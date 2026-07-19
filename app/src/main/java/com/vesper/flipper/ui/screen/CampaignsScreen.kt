@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesper.flipper.data.database.CampaignStateEntity
import com.vesper.flipper.domain.model.CampaignStatus
import com.vesper.flipper.ui.viewmodel.CampaignsViewModel

@Composable
fun CampaignsScreen(
    onNewCampaign: () -> Unit,
    onOpenCampaign: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CampaignsViewModel = hiltViewModel(),
) {
    val campaigns by viewModel.campaigns.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Campaigns") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewCampaign) {
                Icon(Icons.Filled.Add, contentDescription = "New campaign")
            }
        },
        containerColor = Color.Transparent,
    ) { inner ->
        if (campaigns.isEmpty()) {
            EmptyCampaigns(modifier = Modifier.padding(inner).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(campaigns, key = { it.id }) { campaign ->
                CampaignRow(campaign = campaign, onOpen = { onOpenCampaign(campaign.id) })
            }
        }
    }
}

@Composable
private fun EmptyCampaigns(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "No campaigns yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tap + to start an autonomous campaign.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CampaignRow(campaign: CampaignStateEntity, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    campaign.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusChip(campaign.status)
            }
            Text(
                "Phase: ${campaign.currentPhase}  ·  Iteration ${campaign.iterationCount}/${campaign.maxIterations}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            campaign.pauseReason?.let {
                Text(
                    "paused: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            campaign.failureReason?.let {
                Text(
                    "failed: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun StatusChip(statusName: String) {
    val (label, color) = when (statusName) {
        CampaignStatus.PENDING.name -> "PENDING" to MaterialTheme.colorScheme.secondary
        CampaignStatus.RUNNING.name -> "RUNNING" to MaterialTheme.colorScheme.primary
        CampaignStatus.AWAITING_APPROVAL.name -> "AWAITING APPROVAL" to MaterialTheme.colorScheme.tertiary
        CampaignStatus.PAUSED.name -> "PAUSED" to MaterialTheme.colorScheme.secondary
        CampaignStatus.DONE.name -> "DONE" to MaterialTheme.colorScheme.primary
        CampaignStatus.FAILED.name -> "FAILED" to MaterialTheme.colorScheme.error
        else -> statusName to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}
