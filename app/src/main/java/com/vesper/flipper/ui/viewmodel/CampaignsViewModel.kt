package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesper.flipper.data.database.CampaignDao
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.model.CampaignRequest
import com.vesper.flipper.domain.ralph.RalphOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [com.vesper.flipper.ui.screen.CampaignsScreen]. Observes the
 * campaign_state table and exposes an ordered list (updatedAt desc) for the
 * list UI.
 */
@HiltViewModel
class CampaignsViewModel @Inject constructor(
    campaignDao: CampaignDao,
    private val orchestrator: RalphOrchestrator,
) : ViewModel() {

    val campaigns: StateFlow<List<CampaignStateEntity>> = campaignDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createCampaign(request: CampaignRequest, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = orchestrator.startCampaign(request)
            if (id != null) onCreated(id)
        }
    }
}
