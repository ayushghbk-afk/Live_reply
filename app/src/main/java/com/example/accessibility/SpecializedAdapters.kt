package com.example.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.model.ChatMessage

/**
 * WhatsApp Specialized Adapter with comprehensive chat reading:
 * - Group sender names (conversation_contact_name)
 * - Quoted reply frames (quoted_text, quoted_title)
 * - Voice notes and media messages
 * - Exclusion of date dividers, encryption notices, status checkmarks, and emoji reactions
 */
class WhatsAppAdapter : GenericChatAdapter() {
    override val packageName: String = "com.whatsapp"
    override val appName: String = "WhatsApp"

    override fun matches(pkg: String): Boolean {
        return pkg.equals("com.whatsapp", ignoreCase = true) ||
               pkg.equals("com.whatsapp.w4b", ignoreCase = true) // WhatsApp Business
    }

    override fun getChatTitle(rootNode: AccessibilityNodeInfo): String? {
        val all = getAllNodes(rootNode)
        for (node in all) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if ((resId.contains("conversation_contact_name") || resId.contains("chat_title")) && !node.text.isNullOrBlank()) {
                val title = node.text.toString().trim()
                if (title.length in 2..50 && !ChatReaderEngine.isSystemNoise(title)) {
                    return title
                }
            }
        }
        return super.getChatTitle(rootNode)
    }

    override fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val all = getAllNodes(rootNode)
        val displayWidth = getEstimatedWindowWidth(rootNode)
        val messages = mutableListOf<ChatMessage>()
        val bounds = Rect()

        // 1. Identify WhatsApp message containers
        for (node in all) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            // Skip known system noise views
            if (resId.contains("date_divider") || resId.contains("info_text") || resId.contains("reactions_view") || resId.contains("unread_divider")) {
                continue
            }

            // Check if this node is a WhatsApp message text view
            if (resId.contains("message_text") || resId.contains("caption")) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.length < 2 || ChatReaderEngine.isSystemNoise(text)) continue

                node.getBoundsInScreen(bounds)
                if (bounds.top < 100) continue

                val isIncoming = bounds.centerX() < (displayWidth * 0.52f)

                // Search parent container for group contact name and quoted text
                var senderName: String? = null
                var quoteText: String? = null
                var quoteSender: String? = null

                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        val siblingId = sibling.viewIdResourceName?.lowercase() ?: ""

                        if (siblingId.contains("conversation_contact_name") || siblingId.contains("name")) {
                            val candidateName = sibling.text?.toString()?.trim()
                            if (!candidateName.isNullOrBlank() && !ChatReaderEngine.isSystemNoise(candidateName)) {
                                senderName = candidateName
                            }
                        } else if (siblingId.contains("quoted_text") || siblingId.contains("quoted_message_frame")) {
                            quoteText = sibling.text?.toString()?.trim()
                        } else if (siblingId.contains("quoted_title")) {
                            quoteSender = sibling.text?.toString()?.trim()
                        }
                    }
                }

                messages.add(
                    ChatMessage(
                        id = "wa_${bounds.left}_${bounds.top}_${text.hashCode()}",
                        text = text,
                        isIncoming = isIncoming,
                        senderName = senderName,
                        replyToText = quoteText,
                        replyToSender = quoteSender,
                        timestamp = System.currentTimeMillis(),
                        boundsLeft = bounds.left,
                        boundsTop = bounds.top,
                        boundsRight = bounds.right,
                        boundsBottom = bounds.bottom
                    )
                )
            } else if (resId.contains("voice_note_view") || desc.contains("voice message")) {
                // Audio / Voice note detection
                node.getBoundsInScreen(bounds)
                if (bounds.top >= 100) {
                    val isIncoming = bounds.centerX() < (displayWidth * 0.52f)
                    messages.add(
                        ChatMessage(
                            id = "wa_voice_${bounds.left}_${bounds.top}",
                            text = "[Voice Message]",
                            isIncoming = isIncoming,
                            mediaType = "voice_note",
                            timestamp = System.currentTimeMillis(),
                            boundsLeft = bounds.left,
                            boundsTop = bounds.top,
                            boundsRight = bounds.right,
                            boundsBottom = bounds.bottom
                        )
                    )
                }
            }
        }

        if (messages.isNotEmpty()) {
            return messages.sortedBy { it.boundsTop }
        }

        return super.extractConversation(rootNode)
    }

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull { (it.viewIdResourceName?.contains("entry") == true || it.viewIdResourceName?.contains("input") == true) && it.isEditable }
            ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull { it.viewIdResourceName?.contains("send") == true && it.isClickable }
            ?: super.findSendButton(rootNode)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        if (inputField != null && inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return super.dispatchSendAction(inputField, sendButton)
    }
}

/**
 * Telegram Adapter with group sender detection and reply quotes.
 */
class TelegramAdapter : GenericChatAdapter() {
    override val packageName: String = "org.telegram.messenger"
    override val appName: String = "Telegram"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("telegram", ignoreCase = true) ||
               pkg.equals("org.telegram.messenger.web", ignoreCase = true)
    }

    override fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val all = getAllNodes(rootNode)
        val displayWidth = getEstimatedWindowWidth(rootNode)
        val messages = mutableListOf<ChatMessage>()
        val bounds = Rect()

        for (node in all) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if (resId.contains("chat_message_text") || resId.contains("message_text")) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.length < 2 || ChatReaderEngine.isSystemNoise(text)) continue

                node.getBoundsInScreen(bounds)
                if (bounds.top < 100) continue

                val isIncoming = bounds.centerX() < (displayWidth * 0.52f)

                var senderName: String? = null
                var quoteText: String? = null

                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        val sibId = sibling.viewIdResourceName?.lowercase() ?: ""
                        if (sibId.contains("chat_name") || sibId.contains("author_text")) {
                            senderName = sibling.text?.toString()?.trim()
                        } else if (sibId.contains("reply_text")) {
                            quoteText = sibling.text?.toString()?.trim()
                        }
                    }
                }

                messages.add(
                    ChatMessage(
                        id = "tg_${bounds.left}_${bounds.top}_${text.hashCode()}",
                        text = text,
                        isIncoming = isIncoming,
                        senderName = senderName,
                        replyToText = quoteText,
                        timestamp = System.currentTimeMillis(),
                        boundsLeft = bounds.left,
                        boundsTop = bounds.top,
                        boundsRight = bounds.right,
                        boundsBottom = bounds.bottom
                    )
                )
            }
        }

        if (messages.isNotEmpty()) return messages.sortedBy { it.boundsTop }
        return super.extractConversation(rootNode)
    }

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            (it.viewIdResourceName?.contains("chat_text_edit") == true || it.isEditable) && !it.isPassword
        } ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            (it.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
             it.viewIdResourceName?.contains("send_button") == true) && it.isClickable
        } ?: super.findSendButton(rootNode)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        if (inputField != null && inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return super.dispatchSendAction(inputField, sendButton)
    }
}

/**
 * Instagram Direct Message Adapter.
 */
class InstagramAdapter : GenericChatAdapter() {
    override val packageName: String = "com.instagram.android"
    override val appName: String = "Instagram"

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            it.viewIdResourceName?.contains("row_thread_composer_edittext") == true ||
            (it.isEditable && !it.isPassword)
        } ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            it.viewIdResourceName?.contains("row_thread_composer_send_button") == true ||
            (it.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true && it.isClickable)
        } ?: super.findSendButton(rootNode)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return super.dispatchSendAction(inputField, sendButton)
    }
}

/**
 * Discord Adapter with server channel & mentions awareness.
 */
class DiscordAdapter : GenericChatAdapter() {
    override val packageName: String = "com.discord"
    override val appName: String = "Discord"

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            it.viewIdResourceName?.contains("chat_input_edit_text") == true ||
            (it.isEditable && !it.isPassword)
        } ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            (it.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
             it.viewIdResourceName?.contains("send") == true) && it.isClickable
        } ?: super.findSendButton(rootNode)
    }
}

/**
 * Specialized adapter for ChatGPT native mobile app:
 * - Detects user prompt vs ChatGPT model responses for complete conversation history.
 * - Enforces dedicated Send/Submit button click (ChatGPT native ignores Enter key on input).
 */
class ChatGptAdapter : GenericChatAdapter() {
    override val packageName: String = "com.openai.chatgpt"
    override val appName: String = "ChatGPT"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("openai", ignoreCase = true) ||
               pkg.contains("chatgpt", ignoreCase = true)
    }

    override fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val all = getAllNodes(rootNode)
        val displayWidth = getEstimatedWindowWidth(rootNode)
        val messages = mutableListOf<ChatMessage>()
        val bounds = Rect()

        for (node in all) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val txt = node.text?.toString()?.trim().orEmpty()
            if (txt.length < 2 || ChatReaderEngine.isSystemNoise(txt)) continue

            node.getBoundsInScreen(bounds)
            if (bounds.top < 100 || bounds.bottom > 2200) continue

            // Determine if ChatGPT response (left aligned) or user prompt (right aligned)
            val isUserPrompt = (bounds.right > displayWidth * 0.65f && bounds.left > displayWidth * 0.20f) ||
                               resId.contains("user_prompt") || resId.contains("user_turn")

            messages.add(
                ChatMessage(
                    id = "gpt_${bounds.left}_${bounds.top}_${txt.hashCode()}",
                    text = txt,
                    isIncoming = !isUserPrompt, // ChatGPT answer is incoming, user prompt is outgoing
                    senderName = if (!isUserPrompt) "ChatGPT" else "You",
                    timestamp = System.currentTimeMillis(),
                    boundsLeft = bounds.left,
                    boundsTop = bounds.top,
                    boundsRight = bounds.right,
                    boundsBottom = bounds.bottom
                )
            )
        }

        if (messages.isNotEmpty()) return messages.sortedBy { it.boundsTop }
        return super.extractConversation(rootNode)
    }

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            (it.viewIdResourceName?.contains("prompt_edit_text") == true ||
             it.viewIdResourceName?.contains("chat_input") == true ||
             it.isEditable) && !it.isPassword
        } ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        val sendKeywords = listOf("send", "submit", "paper airplane", "up arrow", "ask", "generate", "arrow")

        // 1. By description or resource ID
        for (node in all) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if (node.isClickable && (sendKeywords.any { desc.contains(it) } || resId.contains("send") || resId.contains("submit") || resId.contains("arrow"))) {
                return node
            }
        }

        // 2. Circular arrow up button next to input near bottom right
        val width = getEstimatedWindowWidth(rootNode)
        val rect = Rect()
        val candidate = all.filter { it.isClickable && !it.isEditable }.firstOrNull {
            it.getBoundsInScreen(rect)
            width > 0 && rect.left > (width * 0.70f) && rect.top > 600
        }
        if (candidate != null) return candidate

        return super.findSendButton(rootNode)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        // ChatGPT does NOT accept Enter key! Only direct click on Send button
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val parent = sendButton?.parent
        if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return false
    }
}

/**
 * Claude AI Native Mobile App Adapter.
 */
class ClaudeAiAdapter : GenericChatAdapter() {
    override val packageName: String = "com.anthropic.claude"
    override val appName: String = "Claude AI"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("anthropic", ignoreCase = true) || pkg.contains("claude", ignoreCase = true)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return false
    }
}

/**
 * Browser Chat Adapter for web versions of WhatsApp, Telegram, Discord, etc.
 */
class BrowserChatAdapter : GenericChatAdapter() {
    override val packageName: String = "com.android.chrome"
    override val appName: String = "Browser Chat"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("chrome", ignoreCase = true) ||
               pkg.contains("browser", ignoreCase = true) ||
               pkg.contains("firefox", ignoreCase = true) ||
               pkg.contains("opera", ignoreCase = true) ||
               pkg.contains("edge", ignoreCase = true)
    }

    override fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        if (inputField != null && inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return super.dispatchSendAction(inputField, sendButton)
    }
}

object ChatAdapterRegistry {
    private val generic = GenericChatAdapter()
    private val adapters = listOf(
        WhatsAppAdapter(),
        TelegramAdapter(),
        InstagramAdapter(),
        DiscordAdapter(),
        ChatGptAdapter(),
        ClaudeAiAdapter(),
        BrowserChatAdapter()
    )

    fun getAdapterFor(packageName: String): ChatAdapter {
        return adapters.firstOrNull { it.matches(packageName) } ?: generic
    }
}
