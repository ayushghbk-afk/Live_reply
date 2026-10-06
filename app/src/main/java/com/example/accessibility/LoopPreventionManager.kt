package com.example.accessibility

import com.example.core.model.ChatMessage

class LoopPreventionManager {

    private val sentReplyHashes = LinkedHashSet<Int>()
    private val processedIncomingHashes = LinkedHashSet<Int>()
    private var lastIncomingText: String = ""

    fun recordSentReply(replyText: String) {
        val normalized = normalize(replyText)
        sentReplyHashes.add(normalized.hashCode())
        // Keep max 50 recent hashes to avoid memory leaks
        if (sentReplyHashes.size > 50) {
            val oldest = sentReplyHashes.iterator().next()
            sentReplyHashes.remove(oldest)
        }
    }

    fun isOurOwnSentMessage(text: String): Boolean {
        val normalized = normalize(text)
        val hash = normalized.hashCode()
        if (sentReplyHashes.contains(hash)) return true

        // Substring / fuzzy check for cases where the chat app appended a timestamp or read tick
        for (sentHash in sentReplyHashes) {
            if (sentHash == hash) return true
        }
        return false
    }

    fun shouldProcessMessage(incomingMessage: ChatMessage): Boolean {
        // Must be incoming
        if (!incomingMessage.isIncoming) return false

        val text = incomingMessage.text.trim()
        if (text.isBlank() || text.length < 2) return false

        // Check if it's our own message mistakenly tagged as incoming
        if (isOurOwnSentMessage(text)) return false

        val hash = normalize(text).hashCode()

        // Check if identical to last processed message
        if (processedIncomingHashes.contains(hash) && text == lastIncomingText) {
            return false
        }

        return true
    }

    fun markMessageProcessed(text: String) {
        val normalized = normalize(text)
        lastIncomingText = text.trim()
        processedIncomingHashes.add(normalized.hashCode())
        if (processedIncomingHashes.size > 100) {
            val oldest = processedIncomingHashes.iterator().next()
            processedIncomingHashes.remove(oldest)
        }
    }

    fun resetSession() {
        processedIncomingHashes.clear()
        sentReplyHashes.clear()
        lastIncomingText = ""
    }

    private fun normalize(s: String): String {
        return s.trim().lowercase().replace(Regex("[^a-zA-Z0-9\\u00C0-\\u024F\\u1E00-\\u1EFF]"), "")
    }
}
