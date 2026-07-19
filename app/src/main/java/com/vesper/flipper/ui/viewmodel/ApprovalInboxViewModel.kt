package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesper.flipper.domain.executor.CommandExecutor
import com.vesper.flipper.domain.model.PendingApproval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [com.vesper.flipper.ui.screen.ApprovalInboxScreen]. Sorts pending
 * approvals newest-first so long-lived Ralph gates settle at the bottom of the
 * queue and freshly-fired ones surface at the top.
 */
@HiltViewModel
class ApprovalInboxViewModel @Inject constructor(
    private val commandExecutor: CommandExecutor,
) : ViewModel() {

    val pending: StateFlow<List<PendingApproval>> = commandExecutor.allPendingApprovals
        .map { it.values.sortedByDescending { p -> p.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(id: String) {
        viewModelScope.launch { commandExecutor.approve(id, sessionId = SESSION_ID_INBOX) }
    }

    fun reject(id: String) {
        viewModelScope.launch { commandExecutor.reject(id, sessionId = SESSION_ID_INBOX) }
    }

    companion object {
        /** Sentinel session id for approvals resolved from the inbox UI (as opposed to chat). */
        private const val SESSION_ID_INBOX = "approval_inbox"
    }
}
