package com.vesper.flipper.domain.ralph

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vesper.flipper.ai.ChatCompletionResult
import com.vesper.flipper.ai.OpenRouterClient
import com.vesper.flipper.data.SettingsStore
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignFindingEntity
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.executor.CommandExecutor
import com.vesper.flipper.domain.model.CampaignPhase
import com.vesper.flipper.domain.model.ChatMessage
import com.vesper.flipper.domain.model.CommandResult
import com.vesper.flipper.domain.model.ExecuteCommand
import com.vesper.flipper.domain.model.MessageRole
import com.vesper.flipper.domain.model.PhaseOutcome
import com.vesper.flipper.domain.model.Scope
import com.vesper.flipper.domain.model.ToolCall
import com.vesper.flipper.domain.model.ToolResult
import com.vesper.flipper.domain.service.SkillRegistry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Base class for a Ralph phase worker. Concrete subclasses (Recon, Research,
 * Enumerate, Report) supply the phase [phaseId], the skill id to load as the
 * phase's "how to do this" guide, and a phase-specific system-prompt fragment.
 *
 * The base enforces the four invariants Chunk C.1 requires:
 *
 *  1. **Fresh LLM conversation per phase.** A worker opens a brand-new
 *     [OpenRouterClient.chat] loop; prior phases' conversations are never
 *     forwarded. State passes through [CampaignDao] instead.
 *  2. **All tool calls route through [CommandExecutor.execute].** Workers use
 *     the same code path chat does — no shortcut, no bypass. RiskAssessor still
 *     runs; blocked paths stay blocked.
 *  3. **HIGH-risk actions never fire without a human.** If the executor returns
 *     `requiresConfirmation=true` (which it does for HIGH and, per settings, for
 *     MEDIUM), the worker returns [PhaseOutcome.NeedsApproval] and exits.
 *     WorkManager schedules nothing further until a human resolves the pending
 *     approval — that human loop lands in Chunk C.2.
 *  4. **Audit continuity.** Every executor call is logged under
 *     `sessionId = campaignId`. Chunk C.2's UI can filter the audit screen by
 *     campaign id + phase.
 *
 * The worker is bounded by two caps from SettingsStore, both configurable per
 * user preference:
 *   - `ralphMaxToolCallsPerPhase` (default 30) — loop exits with
 *     PhaseOutcome.Paused("tool call cap N hit") when reached.
 *   - `ralphMaxPhaseWallclockMinutes` (default 15) — loop exits with
 *     PhaseOutcome.Paused("wall-clock cap Nm hit") when exceeded, checked
 *     between iterations rather than pre-empting an in-flight call.
 */
abstract class PhaseWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    protected val commandExecutor: CommandExecutor,
    protected val openRouter: OpenRouterClient,
    protected val campaignDao: CampaignDao,
    protected val skillRegistry: SkillRegistry,
    protected val orchestrator: RalphOrchestrator,
    protected val settingsStore: SettingsStore,
) : CoroutineWorker(appContext, workerParams) {

    abstract val phaseId: CampaignPhase

    /** Skill id (as it appears in `assets/skills/<id>/SKILL.md`) to load as this phase's guide. */
    abstract val skillId: String

    /** Phase-specific fragment appended to the base system prompt. */
    abstract fun phaseSystemPrompt(state: CampaignStateEntity): String

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID)
            ?: return Result.failure()
        val state = campaignDao.getState(campaignId)
            ?: return Result.failure()

        Log.i(TAG, "Phase ${phaseId.name} starting for campaign ${state.name} (${campaignId.take(8)})")
        val outcome = runPhase(state)
        writeOutcome(campaignId, outcome)
        if (outcome is PhaseOutcome.Completed) {
            orchestrator.advancePhase(campaignId)
        }
        return Result.success()
    }

    /**
     * Executes the phase against [state] and returns exactly one [PhaseOutcome].
     * The base implementation drives the LLM loop; subclasses can override
     * for phases with different shapes (e.g. REPORT, which only reads).
     */
    protected open suspend fun runPhase(state: CampaignStateEntity): PhaseOutcome {
        val skillBody = skillRegistry.load(skillId)
        if (skillBody == null) {
            Log.w(TAG, "Skill '$skillId' not found — proceeding without phase guide")
        }
        val priorFindings = readPriorFindings(state.id)
        val campaignScope = buildCampaignScope(state)

        val messages = buildInitialConversation(state, skillBody, priorFindings)
        var findingsWrittenThisRun = 0
        var toolCallsThisRun = 0

        val toolCallCap = settingsStore.ralphMaxToolCallsPerPhase.first()
        val wallclockMinutes = settingsStore.ralphMaxPhaseWallclockMinutes.first()
        val wallclockDeadline = System.currentTimeMillis() + wallclockMinutes * 60_000L

        while (toolCallsThisRun < toolCallCap) {
            if (System.currentTimeMillis() > wallclockDeadline) {
                return PhaseOutcome.Paused("wall-clock cap (${wallclockMinutes}m) hit")
            }
            val chat = openRouter.chat(messages, sessionId = state.id)
            when (chat) {
                is ChatCompletionResult.Error ->
                    return PhaseOutcome.Failed("LLM error: ${chat.message}")

                is ChatCompletionResult.Success -> {
                    val toolCalls = chat.toolCalls.orEmpty()
                    if (toolCalls.isEmpty()) {
                        // Model produced a text turn with no tool call — treat that as the phase
                        // having wrapped up its own reasoning. Persist the closing text as a
                        // phase-summary finding so REPORT can see it.
                        if (chat.content.isNotBlank()) {
                            writeFinding(
                                campaignId = state.id,
                                targetId = null,
                                payloadJson = phaseSummaryJson(chat.content),
                                riskLevel = null,
                            )
                            findingsWrittenThisRun++
                        }
                        return PhaseOutcome.Completed(findingsWrittenThisRun)
                    }

                    val assistantTurn = ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = chat.content,
                        toolCalls = toolCalls,
                    )
                    val nextMessages = messages.toMutableList().apply { add(assistantTurn) }

                    for (call in toolCalls) {
                        toolCallsThisRun++
                        val parsed = parseCommand(call) ?: run {
                            nextMessages += toolResultMessage(call.id, malformedCommandJson())
                            continue
                        }
                        // The model doesn't see the scope field on ExecuteCommand — the worker
                        // attaches it here so RiskAssessor can enforce the campaign's authorized
                        // target list on this call.
                        val command = parsed.copy(scope = campaignScope)
                        val result = commandExecutor.execute(command, sessionId = state.id)
                        if (result.requiresConfirmation && result.pendingApprovalId != null) {
                            return PhaseOutcome.NeedsApproval(result.pendingApprovalId)
                        }
                        recordCommandResultAsFinding(state.id, command, result)
                        findingsWrittenThisRun++
                        nextMessages += toolResultMessage(
                            toolCallId = call.id,
                            resultJson = json.encodeToString(CommandResult.serializer(), result),
                        )
                    }

                    // Swap in the extended history and loop back to the model.
                    messages.clear()
                    messages.addAll(nextMessages)
                }
            }
        }

        return PhaseOutcome.Paused("tool call cap ($toolCallCap) hit")
    }

    // ─── Conversation construction ────────────────────────────────────────────

    private fun buildInitialConversation(
        state: CampaignStateEntity,
        skillBody: String?,
        priorFindings: List<CampaignFindingEntity>,
    ): MutableList<ChatMessage> {
        val header = phaseSystemPrompt(state)
        val block = buildString {
            appendLine(header)
            appendLine()
            appendLine("Campaign: ${state.name}")
            appendLine("Scope: ${state.scope}")
            appendLine("In-scope targets: ${state.scopeTargetsJson}")
            if (state.outOfScopeJson.isNotBlank() && state.outOfScopeJson != "[]") {
                appendLine("Out-of-scope: ${state.outOfScopeJson}")
            }
            appendLine("Mode: ${state.mode}")
            appendLine("Phase: ${phaseId.name}")
            if (skillBody != null) {
                appendLine()
                appendLine("---")
                appendLine("Loaded skill: $skillId")
                appendLine("---")
                append(skillBody)
            }
            if (priorFindings.isNotEmpty()) {
                appendLine()
                appendLine("---")
                appendLine("Prior findings from earlier phases (${priorFindings.size} rows):")
                priorFindings.forEach { f ->
                    appendLine("  [${f.phase}] target=${f.targetId ?: "-"} — ${f.payloadJson}")
                }
            }
        }
        return mutableListOf(
            ChatMessage(role = MessageRole.SYSTEM, content = block)
        )
    }

    private fun toolResultMessage(toolCallId: String, resultJson: String): ChatMessage =
        ChatMessage(
            role = MessageRole.TOOL,
            content = resultJson,
            toolResults = listOf(ToolResult(toolCallId = toolCallId, content = resultJson)),
        )

    // ─── Persistence ──────────────────────────────────────────────────────────

    private suspend fun readPriorFindings(campaignId: String): List<CampaignFindingEntity> {
        val predecessors = phaseId.predecessors().map { it.name }
        if (predecessors.isEmpty()) return emptyList()
        return campaignDao.getFindingsForCampaignAndPhases(campaignId, predecessors)
    }

    private suspend fun writeFinding(
        campaignId: String,
        targetId: String?,
        payloadJson: String,
        riskLevel: String?,
    ) {
        campaignDao.insertFinding(
            CampaignFindingEntity(
                id = UUID.randomUUID().toString(),
                campaignId = campaignId,
                phase = phaseId.name,
                targetId = targetId,
                payloadJson = payloadJson,
                riskLevel = riskLevel,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun recordCommandResultAsFinding(
        campaignId: String,
        command: ExecuteCommand,
        result: CommandResult,
    ) {
        val payload = buildString {
            append("{\"action\":\"")
            append(command.action.name.lowercase())
            append("\",\"success\":")
            append(result.success)
            if (result.data?.message != null) {
                append(",\"message\":")
                append(json.encodeToString(String.serializer(), result.data!!.message!!))
            }
            if (!result.error.isNullOrBlank()) {
                append(",\"error\":")
                append(json.encodeToString(String.serializer(), result.error!!))
            }
            append("}")
        }
        writeFinding(
            campaignId = campaignId,
            targetId = extractTargetId(command),
            payloadJson = payload,
            riskLevel = null,
        )
    }

    private suspend fun writeOutcome(campaignId: String, outcome: PhaseOutcome) {
        val state = campaignDao.getState(campaignId) ?: return
        val updated = when (outcome) {
            is PhaseOutcome.Completed -> state.copy(
                iterationCount = state.iterationCount + 1,
                updatedAt = System.currentTimeMillis(),
            )
            is PhaseOutcome.NeedsApproval -> state.copy(
                status = com.vesper.flipper.domain.model.CampaignStatus.AWAITING_APPROVAL.name,
                pauseReason = "pending approval: ${outcome.pendingApprovalId}",
                updatedAt = System.currentTimeMillis(),
            )
            is PhaseOutcome.Paused -> state.copy(
                status = com.vesper.flipper.domain.model.CampaignStatus.PAUSED.name,
                pauseReason = outcome.reason,
                updatedAt = System.currentTimeMillis(),
            )
            is PhaseOutcome.Failed -> state.copy(
                status = com.vesper.flipper.domain.model.CampaignStatus.FAILED.name,
                failureReason = outcome.reason,
                updatedAt = System.currentTimeMillis(),
            )
        }
        campaignDao.upsertState(updated)
        if (outcome is PhaseOutcome.NeedsApproval) {
            orchestrator.notifyMidPhaseAwaitingApproval(updated)
        }
        Log.i(TAG, "Phase ${phaseId.name} outcome: $outcome (campaign ${campaignId.take(8)})")
    }

    // ─── Small helpers ────────────────────────────────────────────────────────

    private fun parseCommand(call: ToolCall): ExecuteCommand? = try {
        json.decodeFromString(ExecuteCommand.serializer(), call.arguments)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse tool-call arguments for ${call.name}: ${e.message}")
        null
    }

    /**
     * Build the authoritative [Scope] this campaign's phase workers attach to every
     * tool call. The scope lists come out of the campaign_state JSON blobs that
     * NewCampaignScreen writes at campaign creation.
     */
    private fun buildCampaignScope(state: CampaignStateEntity): Scope {
        val listSerializer = ListSerializer(String.serializer())
        val inScope = runCatching {
            json.decodeFromString(listSerializer, state.scopeTargetsJson)
        }.getOrDefault(emptyList())
        val outOfScope = runCatching {
            json.decodeFromString(listSerializer, state.outOfScopeJson)
        }.getOrDefault(emptyList())
        return Scope(
            campaignId = state.id,
            inScope = inScope,
            outOfScope = outOfScope,
        )
    }

    private fun phaseSummaryJson(text: String): String =
        "{\"kind\":\"phase_summary\",\"text\":" +
            json.encodeToString(String.serializer(), text) +
            "}"

    private fun malformedCommandJson(): String =
        """{"success":false,"error":"malformed execute_command arguments"}"""

    private fun extractTargetId(command: ExecuteCommand): String? =
        command.args.address?.takeIf { it.isNotBlank() }
            ?: command.args.target?.takeIf { it.isNotBlank() }
            ?: command.args.path?.takeIf { it.isNotBlank() }

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"
        private const val TAG = "PhaseWorker"
    }
}
