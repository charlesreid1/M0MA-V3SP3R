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
 * Phase 3 — active probing. Connect to each target and map its attack surface.
 * MEDIUM-risk actions (ble_enumerate, ble_read_char, ble_subscribe,
 * subghz_decode_raw) are the primary tools. Whether MEDIUM auto-executes
 * depends on the campaign's mode (see [CampaignMode]) and — for
 * AUTONOMOUS_SAFE — the user's `autoApproveMedium` setting.
 *
 * If any HIGH-risk action fires here it's almost certainly a mistake in the
 * phase instructions; the base [PhaseWorker] will still pause safely.
 */
@HiltWorker
class EnumerateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    commandExecutor: CommandExecutor,
    openRouter: OpenRouterClient,
    campaignDao: CampaignDao,
    skillRegistry: SkillRegistry,
    orchestrator: RalphOrchestrator,
) : PhaseWorker(appContext, workerParams, commandExecutor, openRouter, campaignDao, skillRegistry, orchestrator) {

    override val phaseId: CampaignPhase = CampaignPhase.ENUMERATE
    override val skillId: String = "campaign"

    override fun phaseSystemPrompt(state: CampaignStateEntity): String = """
        You are the ENUMERATE phase of a Ralph campaign. For each researched target, deep-probe to
        map every service, characteristic, entry point, and potential vulnerability. Constraints:

          - You may use MEDIUM-risk actions: ble_enumerate, ble_read_char, ble_subscribe,
            subghz_decode_raw. Vesper will confirm each MEDIUM action per the campaign's mode.
          - Do NOT attempt HIGH-risk actions in this phase. Reserve those for EXPLOIT.
          - Consider load_skill("ble-exploitation") or load_skill("protocol-analysis") when a
            target's shape suggests deeper methodology is needed.
          - For every confirmed weakness, submit a vuln_submit with the appropriate vuln_type
            (writable_ble, writable_characteristic, unencrypted_protocol, etc.). Use vuln_classify
            first if you're unsure about severity.
          - Produce a text summary listing each target's identified vulnerabilities and
            attack-vector shortlist. That ends the phase.
    """.trimIndent()
}
