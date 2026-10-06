package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.AppRepository
import com.example.storage.DiagnosticLogEntity
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonStop
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val logs by repository.recentLogs.collectAsState(initial = emptyList())
    val config by repository.appConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { it.message.contains(searchQuery, ignoreCase = true) || it.tag.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Logs", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { repository.clearLogs() } },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Controls: Search & Debug toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Debug", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = config.debugMode,
                        onCheckedChange = { repository.updateConfig(config.copy(debugMode = it)) }
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diagnostic events recorded yet.\nInteract with a chat app or run a simulation to see live events.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logItem ->
                        LogItemCard(logItem, timeFormatter)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: DiagnosticLogEntity, formatter: SimpleDateFormat) {
    val levelColor = when (log.level) {
        "SUCCESS" -> NeonEmerald
        "WARN" -> AmberAlert
        "ERROR" -> CrimsonStop
        else -> CyberCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(levelColor)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatter.format(Date(log.timestamp))} • [${log.tag}]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                    Text(
                        text = log.level,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = log.message,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
