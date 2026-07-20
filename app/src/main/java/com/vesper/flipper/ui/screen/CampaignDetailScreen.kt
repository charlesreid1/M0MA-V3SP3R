@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesper.flipper.data.database.CampaignFindingEntity
import com.vesper.flipper.domain.model.CampaignPhase
import com.vesper.flipper.domain.model.CampaignStatus
import com.vesper.flipper.ui.viewmodel.CampaignDetailViewModel

@Composable
fun CampaignDetailScreen(
    onBack: () -> Unit,
    onNavigateToAudit: () -> Unit = {},
    viewModel: CampaignDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val findings by viewModel.findings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.name ?: "Campaign") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAudit) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Audit log",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        val s = state
        if (s == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Loading…") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(s.status)
                            Text(
                                "Phase ${s.currentPhase} · iter ${s.iterationCount}/${s.maxIterations}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Scope: ${s.scope}", style = MaterialTheme.typography.bodyMedium)
                        Text("Targets: ${s.scopeTargetsJson}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        if (s.outOfScopeJson.isNotBlank() && s.outOfScopeJson != "[]") {
                            Text("Out-of-scope: ${s.outOfScopeJson}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        Text("Mode: ${s.mode}", style = MaterialTheme.typography.bodySmall)
                        s.pauseReason?.let {
                            Text("paused: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        s.failureReason?.let {
                            Text("failed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                val awaitingExploit = s.status == CampaignStatus.AWAITING_APPROVAL.name &&
                    s.currentPhase == CampaignPhase.EXPLOIT.name
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (awaitingExploit) {
                        Button(
                            onClick = { viewModel.resumeExploit() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                            ),
                        ) { Text("Release exploit gate") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (s.status) {
                            CampaignStatus.RUNNING.name, CampaignStatus.AWAITING_APPROVAL.name -> {
                                FilledTonalButton(onClick = { viewModel.pause() }, modifier = Modifier.weight(1f)) {
                                    Text("Pause")
                                }
                            }
                            CampaignStatus.PAUSED.name -> {
                                FilledTonalButton(onClick = { viewModel.resume() }, modifier = Modifier.weight(1f)) {
                                    Text("Resume")
                                }
                            }
                        }
                        if (s.status !in listOf(CampaignStatus.DONE.name, CampaignStatus.FAILED.name)) {
                            OutlinedButton(
                                onClick = { viewModel.stop() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Stop") }
                        }
                    }
                }
            }

            item {
                Text(
                    "Findings (${findings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(findings, key = { it.id }) { finding ->
                FindingRow(finding)
            }
        }
    }
}

@Composable
private fun FindingRow(finding: CampaignFindingEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    finding.phase,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                finding.targetId?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
            Text(
                finding.payloadJson.take(300),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
