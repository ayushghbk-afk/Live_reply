package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.AppRepository
import com.example.storage.SupportedAppEntity
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val apps by repository.supportedApps.collectAsState(initial = emptyList())
    val pausedConversations by repository.pausedConversations.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var newPkg by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supported Apps & Filters", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ElectricIndigo,
                modifier = Modifier.testTag("add_custom_app_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Custom App")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Live AI Reply only monitors chat applications enabled below. All other apps on your device are strictly ignored.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text("ENABLED APPLICATIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
            }

            items(apps, key = { it.packageName }) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("app_card_${app.packageName}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!app.isBuiltIn) {
                                IconButton(onClick = { scope.launch { repository.removeCustomApp(app) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }

                            Switch(
                                checked = app.isEnabled,
                                onCheckedChange = { isChecked ->
                                    scope.launch { repository.setAppEnabled(app.packageName, isChecked) }
                                },
                                modifier = Modifier.testTag("app_switch_${app.packageName}")
                            )
                        }
                    }
                }
            }

            // Paused conversations section
            item {
                Spacer(Modifier.height(8.dp))
                Text("PAUSED CONVERSATIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
            }

            if (pausedConversations.isEmpty()) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No conversations currently paused. Tap 'Pause Chat' in the floating overlay to temporarily silence a specific conversation.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(pausedConversations, key = { it.conversationKey }) { paused ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(paused.chatTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("In ${paused.packageName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(
                                onClick = { scope.launch { repository.resumeConversation(paused.conversationKey) } }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Custom App") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newDisplayName,
                            onValueChange = { newDisplayName = it },
                            label = { Text("App Name (e.g. Signal)") }
                        )
                        OutlinedTextField(
                            value = newPkg,
                            onValueChange = { newPkg = it },
                            label = { Text("Package Name (e.g. org.thoughtcrime.securesms)") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPkg.isNotBlank() && newDisplayName.isNotBlank()) {
                                scope.launch {
                                    repository.addCustomApp(newPkg.trim(), newDisplayName.trim())
                                    newPkg = ""
                                    newDisplayName = ""
                                    showAddDialog = false
                                }
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
