package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProvider
import com.example.ai.AiResponseValidator
import com.example.core.model.ChatMessage
import com.example.storage.AppRepository
import com.example.storage.PersonaEntity
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestModeScreen(
    repository: AppRepository,
    aiProvider: AiProvider,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val personas by repository.personas.collectAsState(initial = emptyList())
    var selectedPersona by remember { mutableStateOf<PersonaEntity?>(null) }
    var personaDropdownExpanded by remember { mutableStateOf(false) }

    var incomingText by remember { mutableStateOf("Are you coming tomorrow?") }
    var senderName by remember { mutableStateOf("Hellen") }

    // Simulated multi-turn history
    val history = remember {
        mutableStateListOf(
            ChatMessage("1", "Hey! How's your week going?", isIncoming = true, senderName = "Hellen"),
            ChatMessage("2", "Pretty busy with work, but making progress! You?", isIncoming = false, senderName = "Me")
        )
    }

    var isRunning by remember { mutableStateOf(false) }
    var generatedReply by remember { mutableStateOf("") }
    var executionLatencyMs by remember { mutableLongStateOf(0L) }
    var validationResult by remember { mutableStateOf<AiResponseValidator.ValidationResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Pick first persona if none selected
    if (selectedPersona == null && personas.isNotEmpty()) {
        selectedPersona = personas.firstOrNull { it.isSelected } ?: personas.first()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Simulation Playground", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Simulate live incoming messages without opening third-party chat apps. Test model responses, personas, validation rules, and latency safely.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Persona selector
            ExposedDropdownMenuBox(
                expanded = personaDropdownExpanded,
                onExpandedChange = { personaDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPersona?.name ?: "Select Persona",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Testing Persona") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personaDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = personaDropdownExpanded,
                    onDismissRequest = { personaDropdownExpanded = false }
                ) {
                    personas.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.personalityType.title})") },
                            onClick = {
                                selectedPersona = p
                                personaDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Simulated Conversation Context
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SIMULATED CONTEXT HISTORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                        Text("${history.size} messages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    history.forEachIndexed { index, msg ->
                        Surface(
                            color = if (msg.isIncoming) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (msg.isIncoming) "${msg.senderName ?: "Friend"}:" else "You:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isIncoming) CyberCyan else MaterialTheme.colorScheme.primary
                                    )
                                    Text(msg.text, fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { history.removeAt(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            history.add(ChatMessage("${System.currentTimeMillis()}", "Got it, sounds great!", isIncoming = false, senderName = "Me"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add History Turn", fontSize = 11.sp)
                    }
                }
            }

            // Newest Incoming Message Input
            OutlinedTextField(
                value = incomingText,
                onValueChange = { incomingText = it },
                label = { Text("Newest Incoming Message to Reply To") },
                modifier = Modifier.fillMaxWidth().testTag("simulation_message_input")
            )

            Button(
                onClick = {
                    val p = selectedPersona ?: return@Button
                    scope.launch {
                        isRunning = true
                        errorMessage = null
                        generatedReply = ""
                        val startTime = System.currentTimeMillis()

                        val result = aiProvider.generateReply(
                            contextMessages = history.toList(),
                            incomingMessage = incomingText,
                            persona = p,
                            config = repository.appConfig.value
                        )

                        executionLatencyMs = System.currentTimeMillis() - startTime

                        result.fold(
                            onSuccess = { reply ->
                                generatedReply = reply
                                validationResult = AiResponseValidator.validate(reply, p.maxCharacters)
                            },
                            onFailure = { error ->
                                errorMessage = error.localizedMessage ?: "Failed to generate reply"
                            }
                        )
                        isRunning = false
                    }
                },
                enabled = !isRunning && incomingText.isNotBlank() && selectedPersona != null,
                modifier = Modifier.fillMaxWidth().testTag("generate_simulation_reply_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating Simulation...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate Test Reply", fontWeight = FontWeight.Bold)
                }
            }

            // Simulation Result Card
            if (generatedReply.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().testTag("simulation_result_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SIMULATED AI RESPONSE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                            Text("${executionLatencyMs}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedReply,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // Validation pill
                        when (val v = validationResult) {
                            is AiResponseValidator.ValidationResult.Valid -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Validation Passed: Natural, within bounds", color = NeonEmerald, fontSize = 11.sp)
                                }
                            }
                            is AiResponseValidator.ValidationResult.Invalid -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Validation Issue: ${v.reason}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                            null -> {}
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedReply))
                                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Simulation Error:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(errorMessage!!, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
