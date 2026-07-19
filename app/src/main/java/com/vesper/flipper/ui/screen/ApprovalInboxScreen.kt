@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.vesper.flipper.domain.model.PendingApproval
import com.vesper.flipper.domain.model.RiskLevel
import com.vesper.flipper.ui.viewmodel.ApprovalInboxViewModel

@Composable
fun ApprovalInboxScreen(
    onBack: () -> Unit,
    viewModel: ApprovalInboxViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approval Inbox") },
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
        containerColor = Color.Transparent,
    ) { inner ->
        if (pending.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No pending approvals.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pending, key = { it.id }) { approval ->
                ApprovalCard(
                    approval = approval,
                    onApprove = { viewModel.approve(approval.id) },
                    onReject = { viewModel.reject(approval.id) },
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val risk = approval.riskAssessment.level
    val riskColor = when (risk) {
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
        RiskLevel.BLOCKED -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = risk.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = riskColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = approval.command.action.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = approval.riskAssessment.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (approval.command.justification.isNotBlank()) {
                Text(
                    text = "Justification: ${approval.command.justification}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (approval.command.expectedEffect.isNotBlank()) {
                Text(
                    text = "Expected: ${approval.command.expectedEffect}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val ageMinutes = ((System.currentTimeMillis() - approval.timestamp) / 60_000).coerceAtLeast(0)
            Text(
                text = "Queued ${ageMinutes}m ago",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("Approve") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) { Text("Deny") }
            }
        }
    }
}
