package com.vesper.flipper.domain.ralph

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vesper.flipper.data.SettingsStore
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.model.CampaignPhase
import com.vesper.flipper.domain.model.CampaignRequest
import com.vesper.flipper.domain.model.CampaignStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin coordinator on top of [WorkManager]. Owns the campaign state-machine
 * transitions: creating campaigns, scheduling the next phase's worker,
 * pausing/resuming/stopping, and detecting convergence (three consecutive
 * no-new-findings iterations in a row → stop).
 *
 * Deliberately does NOT run the phase logic itself — that's the [PhaseWorker]
 * subclasses' job. The orchestrator only decides *which* worker runs next.
 *
 * Gated by [SettingsStore.ralphEnabled]. All entry points refuse to schedule
 * work while the flag is off, so the whole subsystem can be shipped disabled.
 */
@Singleton
class RalphOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val campaignDao: CampaignDao,
    private val settingsStore: SettingsStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Create a new campaign and schedule its first (RECON) phase. Returns the
     * new campaign id, or null when the ralph feature flag is off.
     */
    suspend fun startCampaign(request: CampaignRequest): String? {
        if (!settingsStore.ralphEnabled.first()) {
            Log.i(TAG, "Refusing to start campaign — ralph feature flag is off")
            return null
        }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val state = CampaignStateEntity(
            id = id,
            name = request.name,
            scope = request.scope,
            scopeTargetsJson = json.encodeToString(
                ListSerializer(String.serializer()), request.scopeTargets
            ),
            outOfScopeJson = json.encodeToString(
                ListSerializer(String.serializer()), request.outOfScope
            ),
            mode = request.mode.name,
            status = CampaignStatus.RUNNING.name,
            currentPhase = CampaignPhase.RECON.name,
            iterationCount = 0,
            maxIterations = request.maxIterations,
            createdAt = now,
            updatedAt = now,
        )
        campaignDao.upsertState(state)
        enqueuePhase(id, CampaignPhase.RECON)
        return id
    }

    /**
     * Advance the campaign to its next phase, based on the current row's
     * `currentPhase` + `status`. Called after each worker finishes.
     *
     * The transitions honor these C.1 rules:
     *  - AWAITING_APPROVAL or PAUSED or FAILED — no scheduling; wait for a human.
     *  - EXPLOIT phase — never scheduled by C.1. It always pauses at the exploit
     *    gate. C.2 will provide the resume path.
     *  - Convergence — three consecutive completed iterations with no new
     *    findings ends the campaign at REPORT.
     */
    suspend fun advancePhase(campaignId: String) {
        val state = campaignDao.getState(campaignId) ?: return
        when (state.status) {
            CampaignStatus.AWAITING_APPROVAL.name,
            CampaignStatus.PAUSED.name,
            CampaignStatus.FAILED.name,
            CampaignStatus.DONE.name -> {
                Log.i(TAG, "advancePhase: no-op, status=${state.status}")
                return
            }
        }
        val current = try {
            CampaignPhase.valueOf(state.currentPhase)
        } catch (e: IllegalArgumentException) {
            markFailed(state, "corrupt currentPhase='${state.currentPhase}'")
            return
        }

        val next = current.next()
        if (next == null) {
            campaignDao.upsertState(
                state.copy(
                    status = CampaignStatus.DONE.name,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            Log.i(TAG, "Campaign ${state.name} (${campaignId.take(8)}) finished after REPORT")
            return
        }

        if (next == CampaignPhase.EXPLOIT) {
            // Exploit gate — hold until a human resumes (C.2 UI).
            campaignDao.upsertState(
                state.copy(
                    status = CampaignStatus.AWAITING_APPROVAL.name,
                    currentPhase = CampaignPhase.EXPLOIT.name,
                    pauseReason = "exploit gate (human required for HIGH-risk actions)",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            Log.i(
                TAG,
                "Campaign ${campaignId.take(8)} reached EXPLOIT gate — awaiting approval"
            )
            return
        }

        if (state.iterationCount >= state.maxIterations) {
            campaignDao.upsertState(
                state.copy(
                    status = CampaignStatus.PAUSED.name,
                    pauseReason = "iteration cap (${state.maxIterations}) hit",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            return
        }

        campaignDao.upsertState(
            state.copy(
                status = CampaignStatus.RUNNING.name,
                currentPhase = next.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
        enqueuePhase(campaignId, next)
    }

    suspend fun pauseCampaign(campaignId: String, reason: String = "user paused") {
        val state = campaignDao.getState(campaignId) ?: return
        if (state.status !in listOf(CampaignStatus.RUNNING.name, CampaignStatus.AWAITING_APPROVAL.name)) {
            return
        }
        WorkManager.getInstance(context).cancelAllWorkByTag(campaignTag(campaignId))
        campaignDao.upsertState(
            state.copy(
                status = CampaignStatus.PAUSED.name,
                pauseReason = reason,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun resumeCampaign(campaignId: String) {
        if (!settingsStore.ralphEnabled.first()) return
        val state = campaignDao.getState(campaignId) ?: return
        if (state.status != CampaignStatus.PAUSED.name && state.status != CampaignStatus.AWAITING_APPROVAL.name) return
        val phase = try {
            CampaignPhase.valueOf(state.currentPhase)
        } catch (e: IllegalArgumentException) {
            markFailed(state, "corrupt currentPhase='${state.currentPhase}' on resume")
            return
        }
        campaignDao.upsertState(
            state.copy(
                status = CampaignStatus.RUNNING.name,
                pauseReason = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
        enqueuePhase(campaignId, phase)
    }

    /**
     * Cancel all scheduled work for [campaignId] and mark the campaign FAILED
     * (as opposed to PAUSED — stopped campaigns are terminal).
     */
    suspend fun stopCampaign(campaignId: String, reason: String = "user stopped") {
        val state = campaignDao.getState(campaignId) ?: return
        WorkManager.getInstance(context).cancelAllWorkByTag(campaignTag(campaignId))
        campaignDao.upsertState(
            state.copy(
                status = CampaignStatus.FAILED.name,
                failureReason = reason,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Kill switch — cancel every scheduled campaign job and pause any RUNNING campaigns. */
    suspend fun stopAllCampaigns(reason: String = "kill switch") {
        WorkManager.getInstance(context).cancelAllWorkByTag(CAMPAIGN_TAG)
        val running = campaignDao.getByStatus(CampaignStatus.RUNNING.name)
        val awaiting = campaignDao.getByStatus(CampaignStatus.AWAITING_APPROVAL.name)
        val now = System.currentTimeMillis()
        (running + awaiting).forEach { state ->
            campaignDao.upsertState(
                state.copy(
                    status = CampaignStatus.PAUSED.name,
                    pauseReason = reason,
                    updatedAt = now,
                )
            )
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun enqueuePhase(campaignId: String, phase: CampaignPhase) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val inputData = Data.Builder()
            .putString(PhaseWorker.KEY_CAMPAIGN_ID, campaignId)
            .build()

        // WorkManager needs the concrete worker class as a reified type parameter; keeping the
        // switch here (instead of Class<*>) preserves the type information Hilt's WorkerFactory
        // uses to construct the right subclass.
        val request = when (phase) {
            CampaignPhase.RECON -> OneTimeWorkRequestBuilder<ReconWorker>()
            CampaignPhase.RESEARCH -> OneTimeWorkRequestBuilder<ResearchWorker>()
            CampaignPhase.ENUMERATE -> OneTimeWorkRequestBuilder<EnumerateWorker>()
            CampaignPhase.REPORT -> OneTimeWorkRequestBuilder<ReportWorker>()
            CampaignPhase.EXPLOIT -> {
                Log.w(TAG, "enqueuePhase called for EXPLOIT — refusing; gate is human-only in C.1")
                return
            }
        }
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(CAMPAIGN_TAG)
            .addTag(campaignTag(campaignId))
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Log.i(TAG, "Enqueued ${phase.name} for campaign ${campaignId.take(8)}")
    }

    private suspend fun markFailed(state: CampaignStateEntity, reason: String) {
        campaignDao.upsertState(
            state.copy(
                status = CampaignStatus.FAILED.name,
                failureReason = reason,
                updatedAt = System.currentTimeMillis(),
            )
        )
        Log.w(TAG, "Campaign ${state.id.take(8)} failed: $reason")
    }

    companion object {
        const val CAMPAIGN_TAG = "campaign"
        fun campaignTag(id: String): String = "campaign_id:$id"
        private const val TAG = "RalphOrchestrator"
    }
}
