package com.example.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAdapter : GenericChatAdapter() {
    override val packageName: String = "com.whatsapp"
    override val appName: String = "WhatsApp"

    override fun matches(pkg: String): Boolean {
        return pkg.equals("com.whatsapp", ignoreCase = true) ||
               pkg.equals("com.whatsapp.w4b", ignoreCase = true) // WhatsApp Business
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
        // WhatsApp responds immediately to its dedicated Send button (com.whatsapp:id/send)
        if (sendButton != null && sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        // WhatsApp also supports Enter-to-send when configured in chat settings
        if (inputField != null && inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return super.dispatchSendAction(inputField, sendButton)
    }
}

class TelegramAdapter : GenericChatAdapter() {
    override val packageName: String = "org.telegram.messenger"
    override val appName: String = "Telegram"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("telegram", ignoreCase = true) ||
               pkg.equals("org.telegram.messenger.web", ignoreCase = true)
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
 * Specialized adapter for ChatGPT, Claude, and Gemini native mobile apps.
 * CRITICAL FIX: ChatGPT native app IGNORES Enter key on input field!
 * It specifically requires clicking the dedicated submit button (arrow up / send button).
 */
class ChatGptAdapter : GenericChatAdapter() {
    override val packageName: String = "com.openai.chatgpt"
    override val appName: String = "ChatGPT"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("openai", ignoreCase = true) ||
               pkg.contains("chatgpt", ignoreCase = true)
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
        // Fallback: Click on the button's parent if child isn't directly clickable
        val parent = sendButton?.parent
        if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return false
    }
}

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
