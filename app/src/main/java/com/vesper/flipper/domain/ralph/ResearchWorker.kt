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
 * Phase 2 — for each target that recon surfaced, pull OSINT and known
 * vulnerabilities. Uses network read actions (github_search, browse_repo,
 * search_resources) plus the LOW-risk vuln_classify to sanity-check severity.
 *
 * All prior-phase context arrives via the campaign_finding rows loaded by
 * [PhaseWorker.readPriorFindings], not through the LLM's own memory.
 */
@HiltWorker
class ResearchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    commandExecutor: CommandExecutor,
    openRouter: OpenRouterClient,
    campaignDao: CampaignDao,
    skillRegistry: SkillRegistry,
    orchestrator: RalphOrchestrator,
    settingsStore: com.vesper.flipper.data.SettingsStore,
) : PhaseWorker(appContext, workerParams, commandExecutor, openRouter, campaignDao, skillRegistry, orchestrator, settingsStore) {

    override val phaseId: CampaignPhase = CampaignPhase.RESEARCH
    override val skillId: String = "campaign"

    override fun phaseSystemPrompt(state: CampaignStateEntity): String = """
        You are the RESEARCH phase of a Ralph campaign. For each target that RECON discovered,
        gather OSINT and known-vulnerability intel. Constraints:

          - No target interaction. Use github_search, browse_repo, search_resources,
            and vuln_classify. Do not enumerate services yet.
          - Prior findings from RECON are attached at the end of this system message.
            Use them as your worklist; produce one research profile per target.
          - When appropriate, LOAD a supporting skill via load_skill (e.g., protocol-analysis
            when you're looking at a proprietary protocol, ble-exploitation when the target
            exposes a GATT server worth mapping).
          - Produce a text summary listing each target with a prioritized attack surface
            assessment. That ends the phase.
    """.trimIndent()
}
