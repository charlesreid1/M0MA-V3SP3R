package com.vesper.flipper.domain.ralph

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vesper.flipper.domain.executor.CommandExecutor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic sweeper for expired [com.vesper.flipper.domain.model.PendingApproval]
 * entries. [CommandExecutor.clearExpiredApprovals] already runs on every chat
 * execute() call, which is sufficient for the interactive path; but a long-lived
 * autonomous approval (24h expiry) needs to be reaped even when nothing else is
 * running. This worker is scheduled once by [RalphOrchestrator] as a unique
 * periodic job and just calls clearExpiredApprovals().
 */
@HiltWorker
class ApprovalCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val commandExecutor: CommandExecutor,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        commandExecutor.clearExpiredApprovals()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "vesper_approval_cleanup"
    }
}
