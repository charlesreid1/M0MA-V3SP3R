package com.vesper.flipper.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The five phases a Ralph campaign progresses through, matching FlipperAgent's
 * ralph-loop skill. Each phase runs in its own worker with a fresh
 * OpenRouterClient conversation; state passes between phases via the
 * campaign_finding Room table, not via the LLM's context.
 */
@Serializable
enum class CampaignPhase {
    @SerialName("recon")
    RECON,

    @SerialName("research")
    RESEARCH,

    @SerialName("enumerate")
    ENUMERATE,

    @SerialName("exploit")
    EXPLOIT,

    @SerialName("report")
    REPORT;

    /** The phase that runs next in the linear pipeline, or null if this is the terminal phase. */
    fun next(): CampaignPhase? = when (this) {
        RECON -> RESEARCH
        RESEARCH -> ENUMERATE
        ENUMERATE -> EXPLOIT
        EXPLOIT -> REPORT
        REPORT -> null
    }

    /** Phases whose findings this phase consumes as prior context (predecessors, in order). */
    fun predecessors(): List<CampaignPhase> = when (this) {
        RECON -> emptyList()
        RESEARCH -> listOf(RECON)
        ENUMERATE -> listOf(RECON, RESEARCH)
        EXPLOIT -> listOf(RECON, RESEARCH, ENUMERATE)
        REPORT -> listOf(RECON, RESEARCH, ENUMERATE, EXPLOIT)
    }
}

/**
 * Runtime status of a campaign row.
 *
 *  * PENDING            — created but no worker has picked it up yet.
 *  * RUNNING            — a phase worker is actively executing.
 *  * AWAITING_APPROVAL  — paused at a HIGH-risk action inside a phase, or at the
 *                        exploit gate. The orchestrator will not advance until
 *                        a human explicitly resumes.
 *  * PAUSED             — user-initiated pause, or a rate cap was hit (iteration
 *                        limit, wall-clock, tool-call cap in C.3).
 *  * DONE               — REPORT phase completed successfully.
 *  * FAILED             — unrecoverable error; failureReason will be populated.
 */
@Serializable
enum class CampaignStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("running")
    RUNNING,

    @SerialName("awaiting_approval")
    AWAITING_APPROVAL,

    @SerialName("paused")
    PAUSED,

    @SerialName("done")
    DONE,

    @SerialName("failed")
    FAILED,
}

/**
 * Approval behaviour for actions issued by a phase worker. **Neither mode ever
 * auto-executes a HIGH-risk action** — HIGH always pauses for a human. That
 * invariant is enforced at [com.vesper.flipper.domain.executor.CommandExecutor],
 * not here. See MERGE_PLAN §4.3 guardrail 3.
 */
@Serializable
enum class CampaignMode {
    /**
     * MEDIUM actions follow the user's existing `autoApproveMedium` setting;
     * HIGH always pauses. Default for new campaigns.
     */
    @SerialName("autonomous_safe")
    AUTONOMOUS_SAFE,

    /**
     * MEDIUM auto-executes regardless of user setting; HIGH still pauses. Opt-in
     * per campaign at creation time.
     */
    @SerialName("autonomous_trusted")
    AUTONOMOUS_TRUSTED,
}

/**
 * The parameters a new campaign is created from. Scope is authoritative — any
 * target not listed in [scopeTargets] should be refused by the executor's
 * scope enforcement (added in Chunk C.3).
 */
data class CampaignRequest(
    val name: String,
    val scope: String,
    val scopeTargets: List<String>,
    val outOfScope: List<String> = emptyList(),
    val mode: CampaignMode = CampaignMode.AUTONOMOUS_SAFE,
    val maxIterations: Int = 10,
)

/**
 * What a [com.vesper.flipper.domain.ralph.PhaseWorker.runPhase] call returns.
 * Exhaustive by design — every outcome maps to exactly one [CampaignStatus]
 * transition at the orchestrator level.
 */
sealed class PhaseOutcome {
    /** Phase produced [findingCount] new campaign_finding rows and can advance. */
    data class Completed(val findingCount: Int) : PhaseOutcome()

    /**
     * Phase issued a HIGH-risk action that returned `requiresConfirmation=true`.
     * The [pendingApprovalId] is what a human needs to approve/deny to resume.
     */
    data class NeedsApproval(val pendingApprovalId: String) : PhaseOutcome()

    /** Phase deliberately paused (cap hit, Flipper disconnected, etc.). */
    data class Paused(val reason: String) : PhaseOutcome()

    /** Phase hit an unrecoverable error. */
    data class Failed(val reason: String) : PhaseOutcome()
}
