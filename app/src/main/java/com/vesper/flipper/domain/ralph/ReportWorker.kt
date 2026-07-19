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
 * Phase 5 — write the engagement report. Read-only: only calls vuln_list,
 * audit_query, and load_skill. Never touches hardware.
 *
 * EXPLOIT (phase 4) always pauses at the exploit gate in C.1; the report
 * worker therefore assumes there may be some exploited findings, some
 * enumerated-but-not-exploited findings, and some purely researched findings.
 * It produces the final markdown report as its closing text summary.
 */
@HiltWorker
class ReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    commandExecutor: CommandExecutor,
    openRouter: OpenRouterClient,
    campaignDao: CampaignDao,
    skillRegistry: SkillRegistry,
    orchestrator: RalphOrchestrator,
    settingsStore: com.vesper.flipper.data.SettingsStore,
) : PhaseWorker(appContext, workerParams, commandExecutor, openRouter, campaignDao, skillRegistry, orchestrator, settingsStore) {

    override val phaseId: CampaignPhase = CampaignPhase.REPORT
    override val skillId: String = "pentest-report"

    override fun phaseSystemPrompt(state: CampaignStateEntity): String = """
        You are the REPORT phase of a Ralph campaign. Read the finding set (via vuln_list) and the
        tool-call timeline (via audit_query), then write the engagement report. Constraints:

          - Read-only phase. Do not call any action that touches hardware or writes files (aside
            from vuln_list/audit_query/load_skill).
          - Follow the pentest-report skill structure loaded above (executive summary, methodology,
            findings table with severity + confidence, evidence, attack chains, remediation).
          - Confidence levels — Confirmed (a tool call directly observed the vuln, vuln_validate
            recorded it), Likely (enumeration surfaced it but no exploit was attempted), Possible
            (research only, based on CVE lookup or documentation).
          - Produce the final report as your closing text turn — no tool call. That closing turn
            is what ends the campaign.
    """.trimIndent()
}
