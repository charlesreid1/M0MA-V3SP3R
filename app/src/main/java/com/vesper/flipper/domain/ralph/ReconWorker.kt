package com.vesper.flipper.domain.ralph

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.vesper.flipper.ai.OpenRouterClient
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.executor.CommandExecutor
import com.vesper.flipper.domain.model.CampaignPhase
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
