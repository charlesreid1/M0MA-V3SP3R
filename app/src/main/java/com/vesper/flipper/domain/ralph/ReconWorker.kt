package com.vesper.flipper.domain.ralph

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.vesper.flipper.ai.OpenRouterClient
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.executor.CommandExecutor
import com.vesper.flipper.domain.model.CampaignPhase
import com.vesper.flipper.domain.model.CommandAction
import com.vesper.flipper.domain.model.CommandArgs
import com.vesper.flipper.domain.model.ExecuteCommand
import com.vesper.flipper.domain.model.PhaseOutcome
import com.vesper.flipper.domain.service.SkillRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Phase 1 of a Ralph campaign — passive discovery across all RF modalities.
 * See `assets/skills/campaign/SKILL.md` phase 1 for the methodology; the model
 * loads that skill via [skillId] on the first turn.
 *
 * Recon has no predecessors, so [PhaseWorker.readPriorFindings] returns empty
 * and the model has to cast its own net using the LOW-risk scan actions
 * (`ble_scan_targets`, `subghz_receive`, `nfc_detect`, `nfc_field`,
 * `rfid_read`, `ir_receive`) — none of which require user approval.
 */
@HiltWorker
class ReconWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    commandExecutor: CommandExecutor,
    openRouter: OpenRouterClient,
    campaignDao: CampaignDao,
    skillRegistry: SkillRegistry,
    orchestrator: RalphOrchestrator,
    settingsStore: com.vesper.flipper.data.SettingsStore,
) : PhaseWorker(appContext, workerParams, commandExecutor, openRouter, campaignDao, skillRegistry, orchestrator, settingsStore) {

    override val phaseId: CampaignPhase = CampaignPhase.RECON
    override val skillId: String = "campaign"

    override suspend fun runPhase(state: CampaignStateEntity): PhaseOutcome {
        // Pre-flight: verify the Flipper is reachable before starting the LLM loop.
        // Without this, the campaign silently cycles through all phases producing
        // boilerplate "what do you need" responses because every tool call fails.
        val healthCheck = commandExecutor.execute(
            ExecuteCommand(
                action = CommandAction.GET_DEVICE_INFO,
                args = CommandArgs(),
                justification = "Pre-flight connectivity check",
                expectedEffect = "Verify Flipper Zero is connected and responding",
            ),
            sessionId = state.id,
        )
        if (!healthCheck.success) {
            val detail = healthCheck.error
                ?: healthCheck.data?.message
                ?: "no response from device"
            return PhaseOutcome.Failed(
                "Flipper not reachable — connect your Flipper Zero over BLE and retry. ($detail)"
            )
        }
        return super.runPhase(state)
    }

    override fun phaseSystemPrompt(state: CampaignStateEntity): String = """
        You are the RECON phase of a Ralph campaign. Cast a wide net across every RF modality Vesper
        exposes and record what you find. Constraints:

          - Use only LOW-risk actions (ble_scan_targets, subghz_receive, ir_receive, nfc_detect,
            nfc_field, rfid_read). Any MEDIUM or HIGH action ends the phase early.
          - Do NOT connect to specific devices in this phase — that is the ENUMERATE phase's job.
          - Stay strictly inside the declared scope. Ignore anything not in the scope target list.
          - When you have exhausted the passive-discovery surface, produce a text summary of what
            you found (or "no targets discovered"). That summary ends the phase.

        Remember: absence is data. If a modality returns zero targets, that's a finding worth
        recording.
    """.trimIndent()
}
