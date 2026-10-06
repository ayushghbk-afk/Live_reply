package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.AppRepository
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val config by repository.appConfig.collectAsState()

    val contextLimits = listOf(5, 10, 20, 30, 50)
    val replyDelays = listOf(0, 1, 3, 5, 10)
    val languages = listOf("Auto", "English", "Hindi", "Hinglish", "Spanish", "French", "German", "Custom")

    var langMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Controls", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy Shield Guarantee Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = NeonEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("DEVICE PRIVACY CHARTER", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CyberCyan)
                    }

                    Text("✓ Never monitors password, PIN, or CVV input fields.", fontSize = 12.sp)
                    Text("✓ Never reads bank apps, payment screens, or two-factor auth codes.", fontSize = 12.sp)
                    Text("✓ Local on-device OCR: image frames are processed in RAM and discarded immediately.", fontSize = 12.sp)
                    Text("✓ Only messages within enabled chat apps are evaluated.", fontSize = 12.sp)
                    Text("✓ No telemetry or tracking servers: communicates solely with your configured AI API.", fontSize = 12.sp)
                }
            }

            // Context window size
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CONVERSATION CONTEXT WINDOW", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                    Text("Number of recent messages passed to AI for conversational awareness:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val selectedBtnColors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    val normalBtnColors = ButtonDefaults.outlinedButtonColors()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        contextLimits.forEach { limit ->
                            val isSelected = config.contextMessageCount == limit
                            OutlinedButton(
                                onClick = { repository.updateConfig(config.copy(contextMessageCount = limit)) },
                                modifier = Modifier.weight(1f),
                                colors = if (isSelected) selectedBtnColors else normalBtnColors
                            ) {
                                Text("$limit", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Debounce delay
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("UI Debounce Delay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${config.debounceDelayMs} ms", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Time to wait after a UI update before parsing messages, preventing duplicate triggers.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = config.debounceDelayMs.toFloat(),
                        onValueChange = { repository.updateConfig(config.copy(debounceDelayMs = it.toLong())) },
                        valueRange = 200f..2000f,
                        steps = 17
                    )
                }
            }

            // Reply delay for Auto mode
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AUTO-REPLY SEND DELAY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                    Text("Natural delay before automatically sending in AUTO mode:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val selectedDelayColors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    val normalDelayColors = ButtonDefaults.outlinedButtonColors()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        replyDelays.forEach { delaySec ->
                            val isSelected = config.replyDelaySeconds == delaySec
                            OutlinedButton(
                                onClick = { repository.updateConfig(config.copy(replyDelaySeconds = delaySec)) },
                                modifier = Modifier.weight(1f),
                                colors = if (isSelected) selectedDelayColors else normalDelayColors
                            ) {
                                Text(if (delaySec == 0) "0s" else "${delaySec}s", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Typing simulation
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simulate Human Typing", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Type characters incrementally rather than instant paste.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.simulateTyping,
                            onCheckedChange = { repository.updateConfig(config.copy(simulateTyping = it)) }
                        )
                    }

                    if (config.simulateTyping) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Typing Speed", fontSize = 12.sp)
                                Text("${config.typingSpeedCpm} chars/min", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = config.typingSpeedCpm.toFloat(),
                                onValueChange = { repository.updateConfig(config.copy(typingSpeedCpm = it.toInt())) },
                                valueRange = 100f..600f,
                                steps = 9
                            )
                        }
                    }
                }
            }

            // Language & Translation Mode
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LANGUAGE & TRANSLATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Translation Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Translate incoming messages and reply in selected language.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.translationModeEnabled,
                            onCheckedChange = { repository.updateConfig(config.copy(translationModeEnabled = it)) }
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = langMenuExpanded,
                        onExpandedChange = { langMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = config.targetLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Response Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = langMenuExpanded,
                            onDismissRequest = { langMenuExpanded = false }
                        ) {
                            languages.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l) },
                                    onClick = {
                                        repository.updateConfig(config.copy(targetLanguage = l))
                                        langMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // OCR fallback switch
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On-Device OCR Fallback", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Use local ML Kit text recognition when accessibility tree is obstructed.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.ocrFallbackEnabled,
                        onCheckedChange = { repository.updateConfig(config.copy(ocrFallbackEnabled = it)) }
                    )
                }
            }

            // Debug logging mode
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diagnostic Debug Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Log verbose accessibility and network metrics. Secrets are always redacted.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.debugMode,
                        onCheckedChange = { repository.updateConfig(config.copy(debugMode = it)) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
