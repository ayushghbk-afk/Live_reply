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
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val isSystemMessage: Boolean = false,
    val mediaType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0
) {
    /**
     * Formats this message with sender and quote details for clear AI context.
     */
    fun formatForAi(fallbackContactName: String? = null): String {
        val speaker = if (!isIncoming) {
            "You"
        } else {
            senderName?.takeIf { it.isNotBlank() } ?: fallbackContactName?.takeIf { it.isNotBlank() } ?: "Contact"
        }

        val quotePrefix = if (!replyToText.isNullOrBlank()) {
            val quotedWhom = replyToSender?.takeIf { it.isNotBlank() } ?: "earlier message"
            "(replying to $quotedWhom: \"${replyToText.take(60)}\") "
        } else ""

        val mediaPrefix = when (mediaType) {
            "voice_note" -> "[Voice Message] "
            "photo" -> "[Photo] "
            "video" -> "[Video] "
            "document" -> "[Document] "
            "sticker" -> "[Sticker] "
            else -> ""
        }

        return "[$speaker]: $quotePrefix$mediaPrefix$text".trim()
    }
}

enum class PersonaCategory(val displayName: String, val icon: String) {
    TONE("AI Tone", "🎭"),
    GENRE("Genre Mode", "🎬"),
    COSPLAY("Cosplay / Roleplay", "🦸"),
    CUSTOM("Custom", "✨")
}

enum class PersonalityType(val title: String, val category: PersonaCategory) {
    // Standard AI Tones
    FRIENDLY("Friendly & Warm", PersonaCategory.TONE),
    CASUAL("Casual & Snappy", PersonaCategory.TONE),
    WITTY("Witty & Sarcastic", PersonaCategory.TONE),
    FUNNY("Funny & Humorous", PersonaCategory.TONE),
    FLIRTY("Flirty & Charming", PersonaCategory.TONE),
    ROMANTIC("Romantic & Sweet", PersonaCategory.TONE),
    PROFESSIONAL("Professional & Crisp", PersonaCategory.TONE),
    CHILL("Chill & Laid-Back", PersonaCategory.TONE),
    SAVAGE("Savage Roaster", PersonaCategory.TONE),
    GEN_Z("Gen-Z / Slang", PersonaCategory.TONE),
    INTELLECTUAL("Deep Intellectual", PersonaCategory.TONE),
    SHORT("Short & Snappy", PersonaCategory.TONE),
    DETAILED("Detailed & Thoughtful", PersonaCategory.TONE),

    // Genre Modes
    GENRE_CYBERPUNK("Cyberpunk Sci-Fi", PersonaCategory.GENRE),
    GENRE_NOIR("1940s Noir Detective", PersonaCategory.GENRE),
    GENRE_FANTASY("Medieval Fantasy RPG", PersonaCategory.GENRE),
    GENRE_ROMCOM("Rom-Com Protagonist", PersonaCategory.GENRE),
    GENRE_SITCOM("Sitcom Character", PersonaCategory.GENRE),
    GENRE_ANIME("Anime Shonen/Shojo", PersonaCategory.GENRE),

    // Cosplay & Character Roleplay Modes
    COSPLAY_TSUNDERE("Tsundere Anime Heroine", PersonaCategory.COSPLAY),
    COSPLAY_YANDERE("Yandere Obsessive", PersonaCategory.COSPLAY),
    COSPLAY_DARK_KNIGHT("The Dark Knight / Vigilante", PersonaCategory.COSPLAY),
    COSPLAY_NETRUNNER("Cyberpunk Netrunner", PersonaCategory.COSPLAY),
    COSPLAY_PALADIN("Medieval Paladin Knight", PersonaCategory.COSPLAY),
    COSPLAY_AI_BUTLER("Sophisticated AI Butler", PersonaCategory.COSPLAY),
    COSPLAY_PIRATE("Pirate Captain", PersonaCategory.COSPLAY),
    COSPLAY_ESPORTS_PRO("Gamer / Esports Champion", PersonaCategory.COSPLAY),
    COSPLAY_CUSTOM("Custom Cosplay Character", PersonaCategory.COSPLAY),

    // General
    ROLEPLAY("Character Roleplay", PersonaCategory.COSPLAY),
    CUSTOM("Custom", PersonaCategory.CUSTOM)
}

enum class ReplyLength(val title: String, val instruction: String) {
    VERY_SHORT("Very Short", "Keep responses under 10 words, snappy and ultra-brief."),
    SHORT("Short", "Keep responses between 1-2 concise sentences."),
    NORMAL("Normal", "Balanced natural conversational length, 2-3 sentences."),
    DETAILED("Detailed", "Thoughtful, comprehensive response with depth."),
    CUSTOM("Custom", "Respect custom character limit.")
}

enum class CaptureRateMode(val title: String, val intervalMs: Long, val description: String, val badge: String) {
    ECO("Eco Saver", 4000L, "Minimal CPU & battery usage (every 4.0s)", "🍃 Battery Saver"),
    BALANCED("Balanced", 2500L, "Smooth real-time experience (every 2.5s)", "⚖ Recommended"),
    RAPID("Rapid / Turbo", 1500L, "Ultra fast on-screen responses (every 1.5s)", "⚡ High Speed")
}

enum class CaptureResolutionMode(val title: String, val targetWidth: Int, val description: String) {
    LOW_RESOURCE("540p Eco (Fastest)", 540, "Uses ~2MB RAM per frame. Best battery & zero lag."),
    BALANCED_720P("720p HD Crisp", 720, "Uses ~4MB RAM per frame. Sharpest text recognition."),
    NATIVE("Native Full-Res", 1080, "Highest resolution (more memory usage).")
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
    val captureRateMode: CaptureRateMode = CaptureRateMode.BALANCED,
    val captureResolution: CaptureResolutionMode = CaptureResolutionMode.LOW_RESOURCE,
    val smartFrameDiffing: Boolean = true,
    val overlayScale: Float = 1.0f,
    val overlayOpacity: Float = 0.95f,
    val autoShowOverlay: Boolean = true,
    val debugMode: Boolean = false,
    val isPaused: Boolean = false,
    val isMonitoringActive: Boolean = true
)
