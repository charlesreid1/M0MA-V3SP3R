@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vesper.flipper.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vesper.flipper.domain.model.*
import com.vesper.flipper.ui.theme.*
import com.vesper.flipper.ui.viewmodel.PayloadLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadLabScreen(
    viewModel: PayloadLabViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("BadUSB", "Evil Portal")

    val error by viewModel.error.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    // Show snackbars
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(saveSuccess) {
        saveSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Payload Lab", fontWeight = FontWeight.Bold)
                        Text(
                            "Generate BadUSB & Evil Portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = VesperOrange
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (index == 0) Icons.Default.Usb else Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(title)
                            }
                        }
                    )
                }
            }

            // Content
            when (selectedTab) {
                0 -> BadUsbTab(viewModel)
                1 -> EvilPortalTab(viewModel)
            }
        }
    }
}

@Composable
private fun BadUsbTab(viewModel: PayloadLabViewModel) {
    val prompt by viewModel.badUsbPrompt.collectAsState()
    val platform by viewModel.badUsbPlatform.collectAsState()
    val generatedScript by viewModel.generatedScript.collectAsState()
    val scriptName by viewModel.scriptName.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Templates Section
        Text(
            "Quick Templates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.badUsbTemplates) { template ->
                TemplateCard(
                    name = template.name,
                    description = template.description,
                    category = template.category.name,
                    onClick = { viewModel.loadBadUsbTemplate(template) }
                )
            }
        }

        Divider()

        // Generator Section
        Text(
            "Generate Custom Script",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Platform selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BadUsbPlatform.entries.forEach { p ->
                FilterChip(
                    selected = platform == p,
                    onClick = { viewModel.updateBadUsbPlatform(p) },
                    label = { Text(p.name.replace("_", " ")) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VesperOrange,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Prompt input
        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.updateBadUsbPrompt(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Describe what you want the script to do") },
            placeholder = { Text("e.g., Open notepad and type a message, or grab WiFi passwords...") },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VesperOrange,
                cursorColor = VesperOrange
            )
        )

        // Generate button
        Button(
            onClick = { viewModel.generateBadUsbScript() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating && prompt.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = VesperOrange)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Generating...")
            } else {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Script")
            }
        }

        // Generated Script Preview
        AnimatedVisibility(visible = generatedScript != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Divider()

                Text(
                    "Generated Script",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Script name input
                OutlinedTextField(
                    value = scriptName,
                    onValueChange = { viewModel.updateScriptName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Script name") },
                    suffix = { Text(".txt") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VesperOrange
                    )
                )

                // Code preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    SelectionContainer {
                        Text(
                            text = generatedScript ?: "",
                            modifier = Modifier
                                .padding(12.dp)
                                .horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF9CDCFE)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearBadUsbGenerated() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear")
                    }

                    Button(
                        onClick = { viewModel.saveBadUsbScript() },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = VesperOrange)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Save to Flipper")
                    }
                }
            }
        }
    }
}

@Composable
private fun EvilPortalTab(viewModel: PayloadLabViewModel) {
    val screenshot by viewModel.portalScreenshot.collectAsState()
    val prompt by viewModel.portalPrompt.collectAsState()
    val generatedHtml by viewModel.generatedHtml.collectAsState()
    val portalName by viewModel.portalName.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val context = LocalContext.current

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setPortalScreenshot(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Templates Section
        Text(
            "Quick Templates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.evilPortalTemplates) { template ->
                TemplateCard(
                    name = template.name,
                    description = template.description,
                    category = template.portalType.name.replace("_", " "),
                    onClick = { viewModel.loadEvilPortalTemplate(template) }
                )
            }
        }

        Divider()

        // Screenshot-to-Portal Section
        Text(
            "Generate from Screenshot",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Upload a screenshot of any login page and AI will recreate it as an evil portal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Screenshot upload area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                2.dp,
                if (screenshot != null) VesperOrange else MaterialTheme.colorScheme.outline
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            if (screenshot != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(screenshot)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Remove button
                    IconButton(
                        onClick = { viewModel.setPortalScreenshot(null) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Remove",
                                modifier = Modifier.padding(4.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap to upload screenshot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "of the login page to clone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Additional prompt
        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.updatePortalPrompt(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Additional instructions (optional)") },
            placeholder = { Text("e.g., Make it look like Facebook login, add company logo...") },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VesperOrange,
                cursorColor = VesperOrange
            )
        )

        // Generate button
        Button(
            onClick = { viewModel.generateEvilPortal() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating && (screenshot != null || prompt.isNotBlank()),
            colors = ButtonDefaults.buttonColors(containerColor = VesperOrange)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Generating...")
            } else {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Portal")
            }
        }

        // Generated HTML Preview
        AnimatedVisibility(visible = generatedHtml != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Divider()

                Text(
                    "Generated Portal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Portal name input
                OutlinedTextField(
                    value = portalName,
                    onValueChange = { viewModel.updatePortalName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Portal name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VesperOrange
                    )
                )

                // HTML preview
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    SelectionContainer {
                        Text(
                            text = generatedHtml ?: "",
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFCE9178)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearPortalGenerated() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear")
                    }

                    Button(
                        onClick = { viewModel.saveEvilPortal() },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = VesperOrange)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Save to Flipper")
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    name: String,
    description: String,
    category: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Category chip
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = VesperOrange.copy(alpha = 0.2f)
            ) {
                Text(
                    category,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = VesperOrange
                )
            }

            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
