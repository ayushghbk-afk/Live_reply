package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import com.example.core.state.LiveSessionState
import com.example.overlay.OverlayService
import com.example.storage.AppRepository
import com.example.storage.PersonaEntity
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonStop
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: AppRepository,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToSupportedApps: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTestMode: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val context = LocalContext.current
    val config by repository.appConfig.collectAsState()
    val selectedPersona by repository.selectedPersonaFlow.collectAsState(initial = null)
    val state by LiveSessionState.processingState.collectAsState()
    val isAccessibilityActive by LiveSessionState.isAccessibilityConnected.collectAsState()
    val isOverlayActive by LiveSessionState.isOverlayShowing.collectAsState()
    val activeChatTitle by LiveSessionState.currentChatTitle.collectAsState()
    val incomingMessage by LiveSessionState.latestIncomingMessage.collectAsState()
    val generatedReply by LiveSessionState.latestGeneratedReply.collectAsState()

    val canDrawOverlays = remember { Settings.canDrawOverlays(context) }
    val isApiKeySet = remember { repository.secureStorage.apiKey.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        config.isPaused || !config.isMonitoringActive -> CrimsonStop
                                        state == ProcessingState.THINKING -> AmberAlert
                                        isAccessibilityActive -> NeonEmerald
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Live AI Reply",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenWizard,
                        modifier = Modifier.testTag("help_wizard_button")
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Setup Guide")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Emergency Alert or Live Status Card
            item {
                if (config.isPaused || !config.isMonitoringActive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CrimsonStop.copy(alpha = 0.2f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CrimsonStop)),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_alert_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI MONITORING PAUSED",
                                    color = CrimsonStop,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "All text reading, AI requests, and auto-typing are halted.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { repository.resumeAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                                modifier = Modifier.testTag("resume_ai_button")
                            ) {
                                Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("status_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isAccessibilityActive) "● MONITORING ACTIVE" else "○ WAITING FOR PERMISSION",
                                    color = if (isAccessibilityActive) NeonEmerald else AmberAlert,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Target: ${activeChatTitle ?: "Any Enabled Chat"}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            // Emergency Stop Button
                            Button(
                                onClick = { repository.emergencyStopAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonStop),
                                modifier = Modifier.testTag("emergency_stop_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("STOP AI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. Mode Selector (Suggest, Approve, Auto)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "OPERATING MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().testTag("mode_selector")
                    ) {
                        OperatingMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = config.operatingMode == mode,
                                onClick = { repository.updateOperatingMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = OperatingMode.entries.size)
                            ) {
                                Text(mode.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text(
                        text = config.operatingMode.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Quick Toggles Row (Monitoring, Floating Overlay)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Monitoring", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (config.isMonitoringActive) "Running" else "Off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = config.isMonitoringActive,
                                onCheckedChange = { repository.updateMonitoringActive(it) },
                                modifier = Modifier.testTag("monitoring_switch")
                            )
                        }
                    }

                    OutlinedCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Overlay UI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (isOverlayActive) "Active" else "Hidden", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isOverlayActive,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (Settings.canDrawOverlays(context)) {
                                            context.startService(Intent(context, OverlayService::class.java))
                                        } else {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        context.stopService(Intent(context, OverlayService::class.java))
                                    }
                                },
                                modifier = Modifier.testTag("overlay_switch")
                            )
                        }
                    }
                }
            }

            // 4. Live Message & Generated Reply Preview
            if (!incomingMessage.isNullOrBlank() || !generatedReply.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().testTag("live_reply_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE ACTIVITY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                                Text(
                                    text = state.label,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!incomingMessage.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("INCOMING MESSAGE:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(incomingMessage!!, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            if (!generatedReply.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("AI GENERATED REPLY:", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(generatedReply!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Send) },
                                        modifier = Modifier.weight(1f).testTag("dashboard_send_reply_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Send", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Regenerate) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Regenerate", fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = { LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Reject) }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CrimsonStop)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. System Readiness & Permissions Checklist
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SYSTEM READINESS & PERMISSIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PermissionStatusRow(
                                title = "Accessibility Service",
                                isGranted = isAccessibilityActive,
                                onFix = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )

                            PermissionStatusRow(
                                title = "Floating Overlay Window",
                                isGranted = canDrawOverlays,
                                onFix = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )

                            PermissionStatusRow(
                                title = "AI Provider & API Key",
                                isGranted = isApiKeySet,
                                actionLabel = "Configure",
                                onFix = onNavigateToAiSettings
                            )
                        }
                    }
                }
            }

            // 6. Primary Action Modules
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CONTROLS & CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.PlayArrow,
                        title = "Test AI Simulation",
                        subtitle = "Run live tests with incoming messages without opening chat apps",
                        onClick = onNavigateToTestMode,
                        tag = "nav_test_mode"
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.Psychology,
                        title = "Persona & Character Manager",
                        subtitle = "Active: ${selectedPersona?.name ?: "Default"} • Roleplay characters",
                        onClick = onNavigateToPersonas,
                        tag = "nav_personas"
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.Settings,
                        title = "AI Provider & Model Settings",
                        subtitle = "OpenRouter • ${repository.secureStorage.primaryModel} • Fallbacks",
                        onClick = onNavigateToAiSettings,
                        tag = "nav_ai_settings"
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.Apps,
                        title = "Supported Apps & Filters",
                        subtitle = "WhatsApp, Telegram, Instagram, Discord, Browser",
                        onClick = onNavigateToSupportedApps,
                        tag = "nav_supported_apps"
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.Shield,
                        title = "Privacy & Safety Settings",
                        subtitle = "Passwords, PINs & sensitive screens excluded • Delays & languages",
                        onClick = onNavigateToPrivacy,
                        tag = "nav_privacy"
                    )

                    FeatureNavigationCard(
                        icon = Icons.Default.Terminal,
                        title = "Live Diagnostic Logs",
                        subtitle = "Real-time inspection of detection, network & automation events",
                        onClick = onNavigateToLogs,
                        tag = "nav_logs"
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    actionLabel: String = "Enable",
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) NeonEmerald else AmberAlert,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        if (!isGranted) {
            FilledTonalButton(
                onClick = onFix,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp)
            }
        } else {
            Text("Ready", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FeatureNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
