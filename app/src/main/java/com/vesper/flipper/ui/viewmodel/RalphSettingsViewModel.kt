package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesper.flipper.data.SettingsStore
import com.vesper.flipper.domain.ralph.RalphOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedicated to the "Ralph mode" section of Settings — keeps the concern out of
 * the existing [SettingsViewModel] combine() plumbing.
 */
@HiltViewModel
class RalphSettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val orchestrator: RalphOrchestrator,
) : ViewModel() {

    val ralphEnabled: Flow<Boolean> = settingsStore.ralphEnabled

    fun setRalphEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRalphEnabled(enabled) }
    }

    /** Cancels every scheduled campaign job and pauses any running campaign. */
    fun killAllCampaigns() {
        viewModelScope.launch { orchestrator.stopAllCampaigns("user kill switch") }
    }
}
