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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PersonalityType
import com.example.core.model.ReplyLength
import com.example.storage.AppRepository
import com.example.storage.PersonaEntity
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagerScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val personas by repository.personas.collectAsState(initial = emptyList())

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personas & Characters", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                onClick = {
                    editingPersona = null
                    showEditorSheet = true
                },
                containerColor = ElectricIndigo,
                modifier = Modifier.testTag("add_persona_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Persona")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Select an active personality or create a custom roleplay character:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(personas, key = { it.id }) { persona ->
                PersonaCard(
                    persona = persona,
                    onSelect = {
                        scope.launch { repository.selectPersona(persona.id) }
                    },
                    onEdit = {
                        editingPersona = persona
                        showEditorSheet = true
                    },
                    onDelete = {
                        scope.launch { repository.deletePersona(persona) }
                    }
                )
            }
        }

        if (showEditorSheet) {
            PersonaEditorBottomSheet(
                initialPersona = editingPersona,
                onDismiss = { showEditorSheet = false },
                onSave = { updated ->
                    scope.launch {
                        repository.savePersona(updated)
                        showEditorSheet = false
                    }
                }
            )
        }
    }
}

@Composable
fun PersonaCard(
    persona: PersonaEntity,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (persona.isSelected) ElectricIndigo.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = if (persona.isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricIndigo)) else null,
        modifier = Modifier.fillMaxWidth().testTag("persona_card_${persona.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = persona.isSelected,
                    onClick = onSelect,
                    modifier = Modifier.testTag("radio_persona_${persona.id}")
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = persona.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (persona.isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface
                        )
                        if (persona.isBuiltIn) {
                            Spacer(Modifier.width(6.dp))
                            Text("Built-in", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        text = "${persona.personalityType.title} • ${persona.replyLength.title} (Max ${persona.maxCharacters} chars)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Persona", modifier = Modifier.size(18.dp))
                }

                if (!persona.isBuiltIn) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Persona", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = persona.personalityDescription,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (persona.personalityType == PersonalityType.ROLEPLAY) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (persona.background.isNotBlank()) {
                            Text("Background: ${persona.background}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (persona.relationship.isNotBlank()) {
                            Text("Relationship: ${persona.relationship}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (persona.speakingStyle.isNotBlank()) {
                            Text("Style: ${persona.speakingStyle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorBottomSheet(
    initialPersona: PersonaEntity?,
    onDismiss: () -> Unit,
    onSave: (PersonaEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialPersona?.name ?: "New Persona") }
    var type by remember { mutableStateOf(initialPersona?.personalityType ?: PersonalityType.CASUAL) }
    var description by remember { mutableStateOf(initialPersona?.personalityDescription ?: "Natural and friendly texting.") }
    var background by remember { mutableStateOf(initialPersona?.background ?: "") }
    var relationship by remember { mutableStateOf(initialPersona?.relationship ?: "") }
    var speakingStyle by remember { mutableStateOf(initialPersona?.speakingStyle ?: "") }
    var rules by remember { mutableStateOf(initialPersona?.rules ?: "Never break character. Do not mention AI.") }
    var replyLength by remember { mutableStateOf(initialPersona?.replyLength ?: ReplyLength.NORMAL) }
    var maxChars by remember { mutableIntStateOf(initialPersona?.maxCharacters ?: 350) }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    var lengthMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (initialPersona != null) "Edit Persona" else "Create New Persona",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Persona / Character Name") },
                modifier = Modifier.fillMaxWidth().testTag("persona_name_input")
            )

            // Personality Type Dropdown
            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = type.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Personality Archetype") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    PersonalityType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.title) },
                            onClick = {
                                type = t
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Personality Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("persona_desc_input")
            )

            // Roleplay Character Specific Fields
            if (type == PersonalityType.ROLEPLAY || type == PersonalityType.CUSTOM) {
                Text("ROLEPLAY ATTRIBUTES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)

                OutlinedTextField(
                    value = background,
                    onValueChange = { background = it },
                    label = { Text("Background / Lore") },
                    placeholder = { Text("e.g. Rogue nomad who guards emotional walls.") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship to User") },
                    placeholder = { Text("e.g. Close confidant, slow-burn romantic interest.") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = speakingStyle,
                    onValueChange = { speakingStyle = it },
                    label = { Text("Speaking Style / Cadence") },
                    placeholder = { Text("e.g. Poetic dialogue, thoughtful pauses.") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("Character Rules") },
                    placeholder = { Text("e.g. Never break character. Never mention AI.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Reply Length Dropdown
            ExposedDropdownMenuBox(
                expanded = lengthMenuExpanded,
                onExpandedChange = { lengthMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = replyLength.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Reply Length Guideline") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lengthMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = lengthMenuExpanded,
                    onDismissRequest = { lengthMenuExpanded = false }
                ) {
                    ReplyLength.entries.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l.title) },
                            onClick = {
                                replyLength = l
                                lengthMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Max character limit
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Maximum Characters", fontSize = 13.sp)
                    Text("$maxChars chars", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = maxChars.toFloat(),
                    onValueChange = { maxChars = it.toInt() },
                    valueRange = 50f..1000f,
                    steps = 18
                )
            }

            Button(
                onClick = {
                    val toSave = (initialPersona ?: PersonaEntity(
                        name = name,
                        personalityType = type,
                        personalityDescription = description
                    )).copy(
                        name = name,
                        personalityType = type,
                        personalityDescription = description,
                        background = background,
                        relationship = relationship,
                        speakingStyle = speakingStyle,
                        rules = rules,
                        replyLength = replyLength,
                        maxCharacters = maxChars,
                        isBuiltIn = false
                    )
                    onSave(toSave)
                },
                modifier = Modifier.fillMaxWidth().testTag("save_persona_button")
            ) {
                Text("Save Persona")
            }
        }
    }
}
