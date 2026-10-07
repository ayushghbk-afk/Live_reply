package com.example.accessibility

import com.example.core.model.ChatMessage

/**
 * High-performance Chat Reading and Conversation Understanding Engine for AI.
 *
 * Provides:
 * 1. Deep multilingual noise and UI chrome filtering (E2E banners, date dividers, status receipts).
 * 2. Floating overlay UI protection (ensures OCR and accessibility never ingest our own controls).
 * 3. Rapid-fire consecutive message burst aggregation (never replies to only the last 3 words).
 * 4. Rich group chat and thread context formatting for AI models.
 */
object ChatReaderEngine {

    // Multilingual phrases that appear in chat apps but are NOT human messages
    private val SYSTEM_NOISE_PHRASES = listOf(
        // End-to-end encryption notices
        "messages and calls are end-to-end encrypted",
        "end-to-end encrypted",
        "no one outside of this chat",
        "cifrados de extremo a extremo",
        "chiffrés de bout en bout",
        "ende-zu-ende-verschlüsselt",
        "dienkripsi secara end-to-end",
        "mensagens e chamadas são protegidas",
        "crittografati end-to-end",
        "uçtan uca şifrelidir",
        "disappearing messages were turned on",
        "security code changed",

        // Date dividers
        "today", "yesterday",
        "hoy", "ayer",
        "aujourd'hui", "hier",
        "heute", "gestern",
        "hari ini", "kemarin",
        "hoje", "ontem",
        "oggi", "ieri",
        "bugün", "dün",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
        "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche",

        // Group activity events
        "joined the group",
        "left the group",
        "added to the group",
        "removed from the group",
        "changed the group description",
        "changed the group photo",
        "created group",
        "se unió al grupo",
        "salió del grupo",
        "a rejoint le groupe",
        "ist der gruppe beigetreten",
        "pinned a message",
        "unpinned a message",

        // Input chrome / UI placeholders
        "type a message",
        "message...",
        "message chatgpt",
        "ask chatgpt",
        "ask claude",
        "send a message",
        "start a conversation",
        "swipe to reply",
        "online",
        "typing...",
        "last seen",
        "writing...",
        "en línea",
        "escribiendo...",
        "en ligne",
        "tape un message",
        "online",
        "schreibt...",
        "search",
        "buscar",
        "rechercher",
        "unread messages",
        "mensajes no leídos",
        "nouveaux messages",
        "ungelesene nachrichten",

        // Status receipts & markers
        "delivered",
        "read",
        "seen",
        "sent",
        "edited",
        "modifié",
        "editado",
        "bearbeitet",
        "waiting for this message",
        "this message was deleted",
        "este mensaje fue eliminado",
        "ce message a été supprimé",
        "diese nachricht wurde gelöscht",

        // Own Floating Overlay Window Chrome (crucial to avoid reading our own overlay UI!)
        "live ai reply",
        "quick controls",
        "auto-see on screen",
        "switch persona",
        "regenerate",
        "operating mode",
        "typing simulation"
    )

    private val TIMESTAMP_REGEX = Regex("^((\\d{1,2}:\\d{2}(:\\d{2})?(\\s?[AaPp][Mm])?)|(\\d{1,2}:\\d{2}\\s?(am|pm)))$")
    private val REACTION_COUNT_REGEX = Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+\\s?\\d{0,3}$")
    private val DATE_NUMBER_REGEX = Regex("^(\\d{1,2}[/.-]\\d{1,2}([/.-]\\d{2,4})?)$")
    private val STATUS_TICK_REGEX = Regex("^[✓✔\\s·]+$")

    /**
     * Checks if a detected text string is noise, UI chrome, or non-message metadata.
     */
    fun isSystemNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return true

        val lower = trimmed.lowercase()

        // Exact or prefix matching against known system phrases
        if (SYSTEM_NOISE_PHRASES.any { lower == it || lower.startsWith(it) || lower.endsWith(it) }) {
            return true
        }

        // Timestamps (e.g., "10:45", "10:45 AM", "23:59")
        if (TIMESTAMP_REGEX.matches(lower)) return true

        // Isolated numeric dates (e.g. "10/07/2026", "07.10")
        if (DATE_NUMBER_REGEX.matches(lower)) return true

        // Reaction bubbles with count (e.g., "❤️ 1", "👍 2", "🔥")
        if (REACTION_COUNT_REGEX.matches(lower) && lower.length <= 6) return true

        // Status ticks (e.g. "✓", "✓✓")
        if (STATUS_TICK_REGEX.matches(lower)) return true

        // Battery / Wifi / Signal artifacts
        if (lower.matches(Regex("^\\d{1,3}%$")) || lower in listOf("5g", "4g", "lte", "wifi", "wi-fi", "volte")) {
            return true
        }

        // Voice note duration controls (e.g. "0:15 / 0:30", "1.5x", "2x")
        if (lower.matches(Regex("^(\\d{1,2}:\\d{2}(\\s?/\\s?\\d{1,2}:\\d{2})?|1x|1\\.5x|2x)$"))) {
            return true
        }

        return false
    }

    /**
     * Clean and normalize raw message text extracted from views or OCR.
     */
    fun cleanMessageText(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isSystemNoise(it) }
            .joinToString("\n")
            .trim()
    }

    data class IncomingBurstResult(
        val combinedText: String,
        val individualMessages: List<ChatMessage>,
        val primarySender: String?,
        val quotedMessageText: String?,
        val quotedSender: String?,
        val hasVoiceNote: Boolean,
        val hasMedia: Boolean
    )

    /**
     * Identifies and groups the complete incoming message burst (all consecutive incoming
     * messages up to the current moment) so the AI reads the FULL thought instead of just
     * the last fragment.
     */
    fun extractLatestIncomingBurst(messages: List<ChatMessage>): IncomingBurstResult? {
        if (messages.isEmpty()) return null

        // Filter out system messages
        val validMessages = messages.filter { !it.isSystemMessage && !isSystemNoise(it.text) }
        if (validMessages.isEmpty()) return null

        // Grab all consecutive incoming messages from the end of the history
        val incomingBurst = validMessages.takeLastWhile { it.isIncoming }
        if (incomingBurst.isEmpty()) return null

        // Combine text preserving order
        val combinedText = incomingBurst
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val primarySender = incomingBurst.lastOrNull { !it.senderName.isNullOrBlank() }?.senderName
            ?: incomingBurst.firstOrNull { !it.senderName.isNullOrBlank() }?.senderName

        val quotedMsg = incomingBurst.lastOrNull { !it.replyToText.isNullOrBlank() }
        val quotedText = quotedMsg?.replyToText
        val quotedSender = quotedMsg?.replyToSender

        val hasVoiceNote = incomingBurst.any { it.mediaType == "voice_note" || it.text.contains("[Voice Message]") }
        val hasMedia = incomingBurst.any { it.mediaType != null || it.text.startsWith("[Photo]") || it.text.startsWith("[Video]") }

        return IncomingBurstResult(
            combinedText = combinedText,
            individualMessages = incomingBurst,
            primarySender = primarySender,
            quotedMessageText = quotedText,
            quotedSender = quotedSender,
            hasVoiceNote = hasVoiceNote,
            hasMedia = hasMedia
        )
    }

    /**
     * Determines whether the conversation is a multi-user group chat.
     */
    fun isGroupChat(messages: List<ChatMessage>, chatTitle: String?): Boolean {
        val senders = messages
            .filter { it.isIncoming && !it.senderName.isNullOrBlank() }
            .mapNotNull { it.senderName }
            .distinct()

        if (senders.size >= 2) return true

        // Fallback: title heuristics
        val title = chatTitle?.lowercase() ?: ""
        if (title.contains("group") || title.contains("team") || title.contains("family") || title.contains("club") || title.contains("squad")) {
            return true
        }

        return false
    }

    /**
     * Reconstructs the conversation into a clean, structured turn-by-turn dialogue
     * designed specifically for LLM system prompts and context injection.
     */
    fun formatTranscriptForAi(
        messages: List<ChatMessage>,
        activeChatTitle: String?,
        limitCount: Int = 20
    ): String {
        val clean = messages
            .filter { !it.isSystemMessage && !isSystemNoise(it.text) }
            .takeLast(limitCount)

        if (clean.isEmpty()) return "(No previous messages)"

        val isGroup = isGroupChat(clean, activeChatTitle)
        val defaultContact = if (isGroup) "Group Member" else (activeChatTitle ?: "Contact")

        return buildString {
            for (msg in clean) {
                append(msg.formatForAi(defaultContact))
                append("\n")
            }
        }.trim()
    }
}
