package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PersonaCategory
import com.example.core.model.PersonalityType
import com.example.core.model.ReplyLength
import com.example.storage.AppRepository
import com.example.storage.PersonaEntity
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagerScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val personas by repository.personas.collectAsState(initial = emptyList())

    var selectedCategoryFilter by remember { mutableStateOf<PersonaCategory?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }

    val filteredPersonas = remember(personas, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            personas
        } else {
            personas.filter { it.category == selectedCategoryFilter }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tones, Genres & Cosplay", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                Icon(Icons.Default.Add, contentDescription = "Create Persona / Cosplay")
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
            // Category Filter Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose your conversation tone, cinema genre, or cosplay character:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All (${personas.size})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricIndigo.copy(alpha = 0.25f),
                                    selectedLabelColor = CyberCyan
                                )
                            )
                        }

                        items(PersonaCategory.entries) { cat ->
                            val count = personas.count { it.category == cat }
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text("${cat.icon} ${cat.displayName} ($count)", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricIndigo.copy(alpha = 0.25f),
                                    selectedLabelColor = CyberCyan
                                )
                            )
                        }
                    }
                }
            }

            items(filteredPersonas, key = { it.id }) { persona ->
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("persona_card_${persona.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Category Badge + Built-in tag + Radio button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (persona.category) {
                            PersonaCategory.COSPLAY -> Color(0xFFE040FB).copy(alpha = 0.2f)
                            PersonaCategory.GENRE -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                            PersonaCategory.TONE -> Color(0xFF7C4DFF).copy(alpha = 0.2f)
                            PersonaCategory.CUSTOM -> Color(0xFFFFAB40).copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${persona.category.icon} ${persona.category.displayName.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (persona.category) {
                                PersonaCategory.COSPLAY -> Color(0xFFEA80FC)
                                PersonaCategory.GENRE -> CyberCyan
                                PersonaCategory.TONE -> Color(0xFFB388FF)
                                PersonaCategory.CUSTOM -> Color(0xFFFFD180)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (persona.isBuiltIn) {
                        Spacer(Modifier.width(6.dp))
                        Text("Default", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (persona.isSelected) {
                        Surface(
                            color = NeonEmerald.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonEmerald)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }
                        }
                    }

                    RadioButton(
                        selected = persona.isSelected,
                        onClick = onSelect,
                        modifier = Modifier.testTag("radio_persona_${persona.id}")
                    )
                }
            }

            // Name & Archetype
            Column {
                Text(
                    text = persona.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (persona.isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${persona.personalityType.title} • ${persona.replyLength.title} (Max ${persona.maxCharacters} chars)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Catchphrase / Signature line
            if (persona.catchphrase.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💬 \"${persona.catchphrase}\"",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Description
            Text(
                text = persona.personalityDescription,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Extra roleplay attributes for Cosplay or Custom
            if (persona.category == PersonaCategory.COSPLAY || persona.personalityType == PersonalityType.ROLEPLAY) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (persona.speakingStyle.isNotBlank()) {
                            Text("🎭 Style: ${persona.speakingStyle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (persona.rules.isNotBlank()) {
                            Text("📜 Rules: ${persona.rules}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Action row: Activate button + Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!persona.isSelected) {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Persona", modifier = Modifier.size(18.dp))
                    }

                    if (!persona.isBuiltIn) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Persona", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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

    var name by remember { mutableStateOf(initialPersona?.name ?: "New Character / Cosplay") }
    var category by remember { mutableStateOf(initialPersona?.category ?: PersonaCategory.COSPLAY) }
    var type by remember { mutableStateOf(initialPersona?.personalityType ?: PersonalityType.COSPLAY_CUSTOM) }
    var catchphrase by remember { mutableStateOf(initialPersona?.catchphrase ?: "") }
    var description by remember { mutableStateOf(initialPersona?.personalityDescription ?: "Vibrant and authentic character.") }
    var background by remember { mutableStateOf(initialPersona?.background ?: "") }
    var relationship by remember { mutableStateOf(initialPersona?.relationship ?: "") }
    var speakingStyle by remember { mutableStateOf(initialPersona?.speakingStyle ?: "") }
    var rules by remember { mutableStateOf(initialPersona?.rules ?: "Never break character. Do not mention AI.") }
    var replyLength by remember { mutableStateOf(initialPersona?.replyLength ?: ReplyLength.NORMAL) }
    var maxChars by remember { mutableIntStateOf(initialPersona?.maxCharacters ?: 350) }

    var categoryMenuExpanded by remember { mutableStateOf(false) }
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
                text = if (initialPersona != null) "Edit Persona / Cosplay" else "Create New Cosplay Character",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Category Selection Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${category.icon} ${category.displayName}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    PersonaCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.icon} ${cat.displayName}") },
                            onClick = {
                                category = cat
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Character / Tone Name") },
                placeholder = { Text("e.g. Tsundere Princess, Neon Nomad, Gothic Knight") },
                modifier = Modifier.fillMaxWidth().testTag("persona_name_input")
            )

            OutlinedTextField(
                value = catchphrase,
                onValueChange = { catchphrase = it },
                label = { Text("Signature Catchphrase / Vibe") },
                placeholder = { Text("e.g. B-baka! You took forever to text back!") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Personality & Demeanor") },
                placeholder = { Text("Describe the character's demeanor and core traits.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("persona_desc_input")
            )

            Text("ROLEPLAY & COSPLAY SPECIFICATIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)

            OutlinedTextField(
                value = background,
                onValueChange = { background = it },
                label = { Text("Character Lore & Background") },
                placeholder = { Text("e.g. A rogue bounty hunter roaming the neon rim.") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("Relationship to User") },
                placeholder = { Text("e.g. Loyal companion, slow-burn romantic rival, sworn bodyguard.") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = speakingStyle,
                onValueChange = { speakingStyle = it },
                label = { Text("Speaking Style & Dialect") },
                placeholder = { Text("e.g. Victorian formalisms, cyberpunk slang, flustered stammers.") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                label = { Text("Character Rules") },
                placeholder = { Text("e.g. Never break character. Never mention AI.") },
                modifier = Modifier.fillMaxWidth()
            )

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
                        personalityDescription = description,
                        category = category
                    )).copy(
                        name = name,
                        personalityType = type,
                        category = category,
                        catchphrase = catchphrase,
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
