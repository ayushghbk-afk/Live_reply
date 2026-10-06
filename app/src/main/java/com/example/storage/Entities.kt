package com.example.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.PersonalityType
import com.example.core.model.ReplyLength

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val personalityType: PersonalityType = PersonalityType.CASUAL,
    val personalityDescription: String,
    val background: String = "",
    val relationship: String = "",
    val speakingStyle: String = "",
    val rules: String = "Never break character. Do not mention you are an AI. Respond naturally.",
    val replyLength: ReplyLength = ReplyLength.NORMAL,
    val maxCharacters: Int = 400,
    val isSelected: Boolean = false,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: String = "INFO" // INFO, SUCCESS, WARN, ERROR
)

@Entity(tableName = "supported_apps")
data class SupportedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = true,
    val inputFieldResId: String? = null,
    val sendButtonResId: String? = null
)

@Entity(tableName = "paused_conversations")
data class PausedConversationEntity(
    @PrimaryKey val conversationKey: String, // e.g. "packageName:chatTitle"
    val packageName: String,
    val chatTitle: String,
    val pausedAt: Long = System.currentTimeMillis()
)
