package com.example.core.model

enum class OperatingMode(val displayName: String, val description: String) {
    SUGGEST(
        displayName = "Suggest",
        description = "Detects messages, generates replies in overlay. You choose to send, edit, or reject."
    ),
    APPROVE(
        displayName = "Approve",
        description = "Detects messages, generates reply, waits for explicit one-tap approval before inserting & sending."
    ),
    AUTO(
        displayName = "Auto",
        description = "Detects messages, generates response, and automatically enters and sends it after configurable delay."
    )
}

enum class ProcessingState(val label: String) {
    IDLE("Idle"),
    MONITORING("● Monitoring"),
    THINKING("⏳ Thinking..."),
    REPLY_READY("✓ Reply Ready"),
    TYPING("⌨ Typing..."),
    SENDING("🚀 Sending..."),
    ERROR("⚠ Error"),
    PAUSED("⏸ Paused")
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isIncoming: Boolean,
    val senderName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0
)

enum class PersonalityType(val title: String) {
    CASUAL("Casual"),
    FRIENDLY("Friendly"),
    FLIRTY("Flirty"),
    ROMANTIC("Romantic"),
    FUNNY("Funny"),
    PROFESSIONAL("Professional"),
    SHORT("Short & Snappy"),
    DETAILED("Detailed"),
    ROLEPLAY("Character Roleplay"),
    CUSTOM("Custom")
}

enum class ReplyLength(val title: String, val instruction: String) {
    VERY_SHORT("Very Short", "Keep responses under 10 words, snappy and ultra-brief."),
    SHORT("Short", "Keep responses between 1-2 concise sentences."),
    NORMAL("Normal", "Balanced natural conversational length, 2-3 sentences."),
    DETAILED("Detailed", "Thoughtful, comprehensive response with depth."),
    CUSTOM("Custom", "Respect custom character limit.")
}

data class AppConfig(
    val operatingMode: OperatingMode = OperatingMode.SUGGEST,
    val debounceDelayMs: Long = 800L,
    val contextMessageCount: Int = 10,
    val replyDelaySeconds: Int = 3,
    val simulateTyping: Boolean = false,
    val typingSpeedCpm: Int = 300, // characters per minute
    val maxReplyCharacters: Int = 400,
    val replyLength: ReplyLength = ReplyLength.NORMAL,
    val autoLanguage: Boolean = true,
    val targetLanguage: String = "Auto",
    val translationModeEnabled: Boolean = false,
    val ocrFallbackEnabled: Boolean = true,
    val autoSeeOnScreen: Boolean = true,
    val scanIntervalMs: Long = 2500L,
    val overlayScale: Float = 1.0f,
    val overlayOpacity: Float = 0.95f,
    val autoShowOverlay: Boolean = true,
    val debugMode: Boolean = false,
    val isPaused: Boolean = false,
    val isMonitoringActive: Boolean = true
)
