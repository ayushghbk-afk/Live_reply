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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProvider
import com.example.core.security.SecureStorage
import com.example.storage.AppRepository
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    secureStorage: SecureStorage,
    repository: AppRepository,
    aiProvider: AiProvider,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var baseUrl by remember { mutableStateOf(secureStorage.apiBaseUrl) }
    var apiKey by remember { mutableStateOf(secureStorage.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var primaryModel by remember { mutableStateOf(secureStorage.primaryModel) }
    var fallbackModelsText by remember { mutableStateOf(secureStorage.fallbackModels.joinToString(", ")) }
    var temperature by remember { mutableFloatStateOf(secureStorage.temperature) }
    var maxTokens by remember { mutableIntStateOf(secureStorage.maxTokens) }
    var timeoutSec by remember { mutableIntStateOf(secureStorage.timeoutSeconds) }
    var retryCount by remember { mutableIntStateOf(secureStorage.retryCount) }
    var customPrompt by remember { mutableStateOf(secureStorage.customSystemPrompt) }

    var isTesting by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Provider Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            secureStorage.apiBaseUrl = baseUrl
                            secureStorage.apiKey = apiKey
                            secureStorage.primaryModel = primaryModel
                            secureStorage.fallbackModels = fallbackModelsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            secureStorage.temperature = temperature
                            secureStorage.maxTokens = maxTokens
                            secureStorage.timeoutSeconds = timeoutSec
                            secureStorage.retryCount = retryCount
                            secureStorage.customSystemPrompt = customPrompt
                            repository.log("AI", "Updated AI Provider settings (Primary: $primaryModel)", "INFO")
                            scope.launch {
                                snackbarHostState.showSnackbar("AI Settings saved securely!")
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("save_ai_settings_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
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
            // Quick preset chips
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("API PROVIDER PRESETS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                baseUrl = "https://openrouter.ai/api/v1"
                                primaryModel = "google/gemini-2.5-flash"
                            }
                        ) {
                            Text("OpenRouter", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                baseUrl = "https://api.openai.com/v1"
                                primaryModel = "gpt-4o-mini"
                            }
                        ) {
                            Text("OpenAI Direct", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Endpoint & Key
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://openrouter.ai/api/v1") },
                modifier = Modifier.fillMaxWidth().testTag("api_base_url_input")
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-or-v1-...") },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle key visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("api_key_input")
            )

            // Primary Model with Auto-Complete Dropdown
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = primaryModel,
                    onValueChange = { primaryModel = it },
                    label = { Text("Primary Model ID") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("primary_model_input")
                )

                if (fetchedModels.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        fetchedModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, fontSize = 13.sp) },
                                onClick = {
                                    primaryModel = m
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Fallback Models Chain
            OutlinedTextField(
                value = fallbackModelsText,
                onValueChange = { fallbackModelsText = it },
                label = { Text("Fallback Models (comma-separated)") },
                supportingText = { Text("Used automatically on rate-limits (429), timeouts, or server errors.") },
                modifier = Modifier.fillMaxWidth().testTag("fallback_models_input")
            )

            // Connection & Model Discovery Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            secureStorage.apiBaseUrl = baseUrl
                            secureStorage.apiKey = apiKey
                            secureStorage.primaryModel = primaryModel
                            val res = aiProvider.testConnection()
                            res.fold(
                                onSuccess = { snackbarHostState.showSnackbar(it) },
                                onFailure = { snackbarHostState.showSnackbar("Failed: ${it.localizedMessage}") }
                            )
                            isTesting = false
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("test_connection_button"),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetchingModels = true
                            secureStorage.apiBaseUrl = baseUrl
                            secureStorage.apiKey = apiKey
                            val res = aiProvider.fetchAvailableModels()
                            res.fold(
                                onSuccess = {
                                    fetchedModels = it
                                    modelDropdownExpanded = true
                                    snackbarHostState.showSnackbar("Found ${it.size} available models")
                                },
                                onFailure = { snackbarHostState.showSnackbar("Failed to fetch models: ${it.localizedMessage}") }
                            )
                            isFetchingModels = false
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("fetch_models_button"),
                    enabled = !isFetchingModels
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fetch Models", fontSize = 12.sp)
                    }
                }
            }

            // Sliders: Temperature, Max Tokens, Timeout, Retries
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("GENERATION HYPERPARAMETERS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Temperature", fontSize = 13.sp)
                            Text(String.format("%.2f", temperature), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.5f,
                            steps = 14
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Maximum Tokens", fontSize = 13.sp)
                            Text("$maxTokens", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = maxTokens.toFloat(),
                            onValueChange = { maxTokens = it.toInt() },
                            valueRange = 50f..1000f,
                            steps = 18
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Timeout Seconds", fontSize = 13.sp)
                            Text("${timeoutSec}s", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = timeoutSec.toFloat(),
                            onValueChange = { timeoutSec = it.toInt() },
                            valueRange = 10f..60f,
                            steps = 9
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Retry Count", fontSize = 13.sp)
                            Text("$retryCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = retryCount.toFloat(),
                            onValueChange = { retryCount = it.toInt() },
                            valueRange = 0f..5f,
                            steps = 4
                        )
                    }
                }
            }

            // Custom Global Prompt Additions
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                label = { Text("Custom Global Instructions") },
                placeholder = { Text("e.g. Always respond in lowercase. Never use exclamation marks.") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("custom_instructions_input")
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
