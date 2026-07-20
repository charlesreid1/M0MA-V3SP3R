@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesper.flipper.domain.model.CampaignMode
import com.vesper.flipper.domain.model.CampaignRequest
import com.vesper.flipper.ui.viewmodel.CampaignsViewModel

@Composable
fun NewCampaignScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CampaignsViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("") }
    var scopeTargetsRaw by remember { mutableStateOf("") }
    var outOfScopeRaw by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CampaignMode.AUTONOMOUS_SAFE) }
    var maxIterations by remember { mutableStateOf(10f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val ralphEnabled by viewModel.ralphEnabled.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New campaign") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Ralph-disabled warning ──────────────────────────────────
            if (!ralphEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        "Ralph is disabled. Enable it in Settings → Experimental, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // ── Error message ──────────────────────────────────────────
            errorMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. smart lock reverse engineer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scope,
                onValueChange = { scope = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("What you're testing and why") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scopeTargetsRaw,
                onValueChange = { scopeTargetsRaw = it },
                label = { Text("In-scope targets (one per line, optional)") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF\n192.168.1.10") },
                supportingText = {
                    Text(
                        "Substring match — \"AA:BB\" matches any MAC with that prefix, " +
                        "\":\" matches any MAC, \".\" matches any IP. " +
                        "Leave empty to allow everything.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = outOfScopeRaw,
                onValueChange = { outOfScopeRaw = it },
                label = { Text("Out-of-scope (one per line, optional)") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF\n192.168.1.100") },
                supportingText = {
                    Text(
                        "Same substring matching as in-scope. " +
                        "Checked first — a target in this list is always blocked.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            ModeChoice(
                selected = mode == CampaignMode.AUTONOMOUS_SAFE,
                label = "Autonomous Safe",
                description = "MEDIUM follows autoApproveMedium; HIGH always pauses.",
                onSelect = { mode = CampaignMode.AUTONOMOUS_SAFE },
            )
            ModeChoice(
                selected = mode == CampaignMode.AUTONOMOUS_TRUSTED,
                label = "Autonomous Trusted",
                description = "MEDIUM auto-executes; HIGH still always pauses.",
                onSelect = { mode = CampaignMode.AUTONOMOUS_TRUSTED },
            )
            Text(
                "Max iterations per phase",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "How many LLM rounds each phase (recon, research, enumerate, exploit, report) runs before advancing. Higher = deeper recon but more API cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = maxIterations,
                onValueChange = { maxIterations = it },
                valueRange = 1f..50f,
                steps = 48,
            )
            Text(
                "${maxIterations.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = {
                    errorMessage = null
                    viewModel.createCampaign(
                        CampaignRequest(
                            name = name.ifBlank { "unnamed" },
                            scope = scope,
                            scopeTargets = scopeTargetsRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
                            outOfScope = outOfScopeRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
                            mode = mode,
                            maxIterations = maxIterations.toInt(),
                        ),
                        onCreated = onCreated,
                        onError = { errorMessage = it },
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeChoice(
    selected: Boolean,
    label: String,
    description: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
