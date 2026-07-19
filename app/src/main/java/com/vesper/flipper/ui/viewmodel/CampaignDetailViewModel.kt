package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignFindingEntity
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.ralph.RalphOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [com.vesper.flipper.ui.screen.CampaignDetailScreen]. Loads a single
 * campaign row + its findings and exposes pause/resume/stop/resume-exploit
 * actions that route through [RalphOrchestrator].
 */
@HiltViewModel
class CampaignDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val campaignDao: CampaignDao,
    private val orchestrator: RalphOrchestrator,
) : ViewModel() {

    val campaignId: String = checkNotNull(savedStateHandle["campaignId"]) {
        "CampaignDetailViewModel requires a campaignId nav arg"
    }

    private val _state = MutableStateFlow<CampaignStateEntity?>(null)
    val state: StateFlow<CampaignStateEntity?> = _state.asStateFlow()

    private val _findings = MutableStateFlow<List<CampaignFindingEntity>>(emptyList())
    val findings: StateFlow<List<CampaignFindingEntity>> = _findings.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = campaignDao.getState(campaignId)
            _findings.value = campaignDao.getFindingsForCampaign(campaignId)
        }
    }

    fun pause() = viewModelScope.launch {
        orchestrator.pauseCampaign(campaignId)
        refresh()
    }

    fun resume() = viewModelScope.launch {
        orchestrator.resumeCampaign(campaignId)
        refresh()
    }

    fun stop() = viewModelScope.launch {
        orchestrator.stopCampaign(campaignId)
        refresh()
    }

    fun resumeExploit() = viewModelScope.launch {
        orchestrator.resumeExploitPhase(campaignId)
        refresh()
    }
}
