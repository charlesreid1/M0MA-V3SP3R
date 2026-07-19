@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesper.flipper.ui.viewmodel.LabsHubViewModel

/**
 * Consolidated hub for Vesper's lab-style features. Landing screen for the "Labs"
 * bottom-nav slot; individual labs are reached through card taps. Making this a
 * hub instead of one bottom-nav slot per lab is what lets us fit Campaigns
 * without dropping any of the existing features.
 */
@Composable
fun LabsHubScreen(
    onOpenAlchemy: () -> Unit,
    onOpenPayloadLab: () -> Unit,
    onOpenCampaigns: () -> Unit,
    viewModel: LabsHubViewModel = hiltViewModel(),
) {
    val ralphEnabled by viewModel.ralphEnabled.collectAsState(initial = false)

    val entries = buildList {
        add(
            LabEntry(
                title = "Alchemy Lab",
                description = "Signal forge — SubGHz, IR, NFC, RFID content generation.",
                icon = Icons.Filled.AutoAwesome,
                onOpen = onOpenAlchemy,
                enabled = true,
            )
        )
        add(
            LabEntry(
                title = "Payload Lab",
                description = "BadUSB and Evil Portal authoring with live validation.",
                icon = Icons.Filled.Code,
                onOpen = onOpenPayloadLab,
                enabled = true,
            )
        )
        add(
            LabEntry(
                title = "Campaigns",
                description = if (ralphEnabled) {
                    "Autonomous multi-phase engagements. Ralph mode."
                } else {
                    "Autonomous multi-phase engagements. Enable Ralph mode in Settings first."
                },
                icon = Icons.Filled.Terminal,
                onOpen = onOpenCampaigns,
                enabled = ralphEnabled,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Labs") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries) { entry ->
                LabCard(entry)
            }
        }
    }
}

private data class LabEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onOpen: () -> Unit,
    val enabled: Boolean,
)

@Composable
private fun LabCard(entry: LabEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.enabled, onClick = entry.onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (entry.enabled) 0.92f else 0.55f,
            ),
        ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (entry.enabled) 0.16f else 0.08f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.title,
                    tint = if (entry.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
