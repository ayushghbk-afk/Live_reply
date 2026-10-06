package com.example.accessibility

import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAdapter : GenericChatAdapter() {
    override val packageName: String = "com.whatsapp"
    override val appName: String = "WhatsApp"

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        // Check WhatsApp specific resource ID: com.whatsapp:id/entry
        return all.firstOrNull { it.viewIdResourceName?.contains("entry") == true && it.isEditable }
            ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        // Check WhatsApp specific send button: com.whatsapp:id/send
        return all.firstOrNull { it.viewIdResourceName?.contains("send") == true && it.isClickable }
            ?: super.findSendButton(rootNode)
    }
}

class TelegramAdapter : GenericChatAdapter() {
    override val packageName: String = "org.telegram.messenger"
    override val appName: String = "Telegram"

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            (it.viewIdResourceName?.contains("chat_text_edit") == true || it.isEditable) && !it.isPassword
        } ?: super.findInputField(rootNode)
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        return all.firstOrNull {
            it.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true && it.isClickable
        } ?: super.findSendButton(rootNode)
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
}

class BrowserChatAdapter : GenericChatAdapter() {
    override val packageName: String = "com.android.chrome"
    override val appName: String = "Browser Chat"

    override fun matches(pkg: String): Boolean {
        return pkg.contains("chrome", ignoreCase = true) ||
               pkg.contains("browser", ignoreCase = true) ||
               pkg.contains("firefox", ignoreCase = true)
    }
}

object ChatAdapterRegistry {
    private val generic = GenericChatAdapter()
    private val adapters = listOf(
        WhatsAppAdapter(),
        TelegramAdapter(),
        InstagramAdapter(),
        DiscordAdapter(),
        BrowserChatAdapter()
    )

    fun getAdapterFor(packageName: String): ChatAdapter {
        return adapters.firstOrNull { it.matches(packageName) } ?: generic
    }
}
