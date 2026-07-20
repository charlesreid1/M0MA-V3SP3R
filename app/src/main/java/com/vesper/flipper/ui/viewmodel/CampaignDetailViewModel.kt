package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignFindingEntity
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.ralph.RalphOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [com.vesper.flipper.ui.screen.CampaignDetailScreen]. Observes a single
 * campaign's state + findings live from Room, so the UI updates in real time as
 * phase workers advance the campaign.
 */
@HiltViewModel
class CampaignDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    campaignDao: CampaignDao,
    private val orchestrator: RalphOrchestrator,
) : ViewModel() {

    val campaignId: String = checkNotNull(savedStateHandle["campaignId"]) {
        "CampaignDetailViewModel requires a campaignId nav arg"
    }

    val state: StateFlow<CampaignStateEntity?> = campaignDao.observeState(campaignId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val findings: StateFlow<List<CampaignFindingEntity>> = campaignDao.observeFindingsForCampaign(campaignId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause() = viewModelScope.launch {
        orchestrator.pauseCampaign(campaignId)
    }

    fun resume() = viewModelScope.launch {
        orchestrator.resumeCampaign(campaignId)
    }

    fun stop() = viewModelScope.launch {
        orchestrator.stopCampaign(campaignId)
    }

    fun resumeExploit() = viewModelScope.launch {
        orchestrator.resumeExploitPhase(campaignId)
    }
}
