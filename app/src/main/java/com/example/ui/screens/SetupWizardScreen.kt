package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProvider
import com.example.core.model.ChatMessage
import com.example.core.model.OperatingMode
import com.example.core.state.LiveSessionState
import com.example.storage.AppRepository
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    repository: AppRepository,
    aiProvider: AiProvider,
    onFinishWizard: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 8

    val personas by repository.personas.collectAsState(initial = emptyList())
    val isAccessibilityActive by LiveSessionState.isAccessibilityConnected.collectAsState()

    var apiKeyInput by remember { mutableStateOf(repository.secureStorage.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(repository.secureStorage.primaryModel) }
    var selectedMode by remember { mutableStateOf(repository.appConfig.value.operatingMode) }

    // Test run states for step 8
    var testPrompt by remember { mutableStateOf("Hey, are you free this weekend?") }
    var testReply by remember { mutableStateOf("") }
    var isTestingAi by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live AI Reply Setup", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Step $currentStep of $totalSteps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Text(
                        text = "Skip",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .padding(4.dp)
                            .testTag("skip_wizard_button"),
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.testTag("wizard_prev_button")
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(10.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < totalSteps) {
                                // Save intermediate settings
                                if (currentStep == 4) {
                                    repository.secureStorage.apiKey = apiKeyInput
                                }
                                if (currentStep == 5) {
                                    repository.secureStorage.primaryModel = selectedModel
                                }
                                if (currentStep == 7) {
                                    repository.updateOperatingMode(selectedMode)
                                }
                                currentStep++
                            } else {
                                repository.secureStorage.isFirstRunCompleted = true
                                onFinishWizard()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        modifier = Modifier.testTag("wizard_next_button")
                    ) {
                        Text(if (currentStep == totalSteps) "Get Started" else "Continue")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { currentStep.toFloat() / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberCyan
            )

            when (currentStep) {
                1 -> WizardStepWelcome()
                2 -> WizardStepAccessibility(context, isAccessibilityActive)
                3 -> WizardStepOverlay(context)
                4 -> WizardStepApiKey(apiKeyInput, showApiKey, onApiKeyChange = { apiKeyInput = it }, onToggleShow = { showApiKey = !showApiKey })
                5 -> WizardStepModel(selectedModel, onSelectModel = { selectedModel = it })
                6 -> WizardStepPersona(personas, onSelectPersona = { id -> scope.launch { repository.selectPersona(id) } })
                7 -> WizardStepMode(selectedMode, onSelectMode = { selectedMode = it })
                8 -> WizardStepTest(
                    testPrompt = testPrompt,
                    testReply = testReply,
                    isTestingAi = isTestingAi,
                    testError = testError,
                    onPromptChange = { testPrompt = it },
                    onRunTest = {
                        scope.launch {
                            isTestingAi = true
                            testError = null
                            val persona = repository.getSelectedPersona() ?: personas.firstOrNull()
                            if (persona == null) {
                                testError = "Please select a persona first."
                                isTestingAi = false
                                return@launch
                            }
                            val res = aiProvider.generateReply(
                                contextMessages = listOf(ChatMessage("1", testPrompt, true)),
                                incomingMessage = testPrompt,
                                persona = persona,
                                config = repository.appConfig.value
                            )
                            res.fold(
                                onSuccess = { testReply = it },
                                onFailure = { testError = it.localizedMessage ?: "Failed" }
                            )
                            isTestingAi = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WizardStepWelcome() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(ElectricIndigo.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(32.dp))
        }

        Text("Welcome to Live AI Reply", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Your intelligent real-time conversational co-pilot for WhatsApp, Telegram, Instagram, Discord, and browser chats.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KEY CAPABILITIES:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CyberCyan)
                Text("• Live message detection without full-screen constant video capture", fontSize = 13.sp)
                Text("• Understands multi-turn conversation context up to 50 messages", fontSize = 13.sp)
                Text("• Suggest, Approve, and Auto-reply operating modes", fontSize = 13.sp)
                Text("• Rich personality & roleplay character engines", fontSize = 13.sp)
                Text("• Strict privacy: Passwords, PINs & auth screens are strictly ignored", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun WizardStepAccessibility(context: Context, isEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isEnabled) NeonEmerald.copy(alpha = 0.2f) else ElectricIndigo.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Accessibility,
                contentDescription = null,
                tint = if (isEnabled) NeonEmerald else ElectricIndigo,
                modifier = Modifier.size(32.dp)
            )
        }

        Text("Accessibility Permission", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Live AI Reply uses Android AccessibilityService to detect incoming text bubbles and safely insert replies into the chat input field.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PRIVACY GUARANTEE:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CyberCyan)
                Text("✓ Sensitive input fields (passwords, PINs, bank forms) are blocked at the node level.", fontSize = 12.sp)
                Text("✓ No background surveillance outside of your explicitly enabled chat apps.", fontSize = 12.sp)
            }
        }

        if (isEnabled) {
            Card(colors = CardDefaults.cardColors(containerColor = NeonEmerald.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Accessibility Service is Enabled!", color = NeonEmerald, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().testTag("enable_accessibility_wizard")
            ) {
                Text("Open Accessibility Settings")
            }
            Text(
                "Find 'Live AI Reply' in Installed Apps and toggle it ON.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WizardStepOverlay(context: Context) {
    val canDraw = remember { Settings.canDrawOverlays(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(ElectricIndigo.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Layers, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(32.dp))
        }

        Text("Floating Overlay Permission", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Allows the compact floating AI bubble to appear above WhatsApp or Discord so you can preview, edit, and send replies effortlessly.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (canDraw) {
            Card(colors = CardDefaults.cardColors(containerColor = NeonEmerald.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Floating Overlay Permission Granted!", color = NeonEmerald, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().testTag("enable_overlay_wizard")
            ) {
                Text("Grant Overlay Permission")
            }
        }
    }
}

@Composable
fun WizardStepApiKey(
    apiKeyInput: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleShow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(ElectricIndigo.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(32.dp))
        }

        Text("Configure AI Provider", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Live AI Reply supports OpenRouter and any OpenAI-compatible API endpoint.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = onApiKeyChange,
            label = { Text("OpenRouter / OpenAI API Key") },
            placeholder = { Text("sk-or-v1-...") },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("wizard_api_key_input")
        )

        Text(
            "Get a key at openrouter.ai/keys. Stored safely inside hardware-backed encrypted storage.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WizardStepModel(
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    val models = listOf(
        "google/gemini-2.5-flash" to "Fastest, highly natural conversationalist (Recommended)",
        "meta-llama/llama-3.3-70b-instruct" to "Open-weights powerhouse, expressive writing",
        "openai/gpt-4o-mini" to "Crisp, concise, highly versatile",
        "anthropic/claude-3.5-haiku" to "Nuanced, quick, emotionally intelligent",
        "mistralai/mistral-small-3" to "High-speed reasoning, European privacy standard"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Select AI Model", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Choose the primary model for conversation replies. You can also configure fallbacks later.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        models.forEach { (modelId, desc) ->
            val isSelected = selectedModel == modelId
            Card(
                onClick = { onSelectModel(modelId) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ElectricIndigo.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricIndigo)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(modelId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface)
                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WizardStepPersona(
    personas: List<com.example.storage.PersonaEntity>,
    onSelectPersona: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose Persona", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Sets the personality, texting slang, and tone of replies.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        personas.forEach { persona ->
            val isSelected = persona.isSelected
            Card(
                onClick = { onSelectPersona(persona.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ElectricIndigo.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricIndigo)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(persona.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface)
                    Text(persona.personalityDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WizardStepMode(
    selectedMode: OperatingMode,
    onSelectMode: (OperatingMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose Operating Mode", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("You can change this anytime from the main screen or floating overlay.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OperatingMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            Card(
                onClick = { onSelectMode(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ElectricIndigo.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricIndigo)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(mode.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(mode.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WizardStepTest(
    testPrompt: String,
    testReply: String,
    isTestingAi: Boolean,
    testError: String?,
    onPromptChange: (String) -> Unit,
    onRunTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Test AI Reply Pipeline", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Simulate an incoming message to verify the complete prompt, persona, and API pipeline.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = testPrompt,
            onValueChange = onPromptChange,
            label = { Text("Incoming Test Message") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onRunTest,
            enabled = !isTestingAi && testPrompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("wizard_run_test_button")
        ) {
            if (isTestingAi) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Generating Response...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Test Reply")
            }
        }

        if (testReply.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI REPLY GENERATED:", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                    Text(testReply, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (testError != null) {
            Text(testError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}
