package com.example.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.model.ChatMessage

interface ChatAdapter {
    val packageName: String
    val appName: String

    fun matches(pkg: String): Boolean = packageName.equals(pkg, ignoreCase = true)
    fun isChatScreen(rootNode: AccessibilityNodeInfo): Boolean
    fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage>
    fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo?
    fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo?
    fun getChatTitle(rootNode: AccessibilityNodeInfo): String?
    fun dispatchSendAction(inputField: AccessibilityNodeInfo?, sendButton: AccessibilityNodeInfo?): Boolean {
        // Default send strategy: Click the dedicated send button if found, or click input field action
        if (sendButton != null) {
            val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }
        if (inputField != null) {
            return inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }
}

open class GenericChatAdapter : ChatAdapter {
    override val packageName: String = "*"
    override val appName: String = "Generic Chat"

    override fun matches(pkg: String): Boolean = true

    override fun isChatScreen(rootNode: AccessibilityNodeInfo): Boolean {
        // A chat screen typically has at least one editable input field or message list
        return findInputField(rootNode) != null
    }

    override fun getChatTitle(rootNode: AccessibilityNodeInfo): String? {
        // Search header / actionbar title
        val outBounds = Rect()
        for (node in getAllNodes(rootNode)) {
            node.getBoundsInScreen(outBounds)
            // Near top of screen (y < 350) and has text
            if (outBounds.top < 350 && outBounds.bottom > 50 && !node.text.isNullOrBlank()) {
                val txt = node.text.toString().trim()
                if (txt.length in 2..40 && !txt.contains(":") && !txt.contains("AM") && !txt.contains("PM")) {
                    return txt
                }
            }
        }
        return null
    }

    override fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val allNodes = getAllNodes(rootNode)
        val displayWidth = getEstimatedWindowWidth(rootNode)

        val textNodes = allNodes.filter { node ->
            !node.text.isNullOrBlank() &&
            !node.isEditable &&
            !node.isPassword &&
            node.className?.contains("Button", ignoreCase = true) != true
        }

        val nodeBounds = Rect()
        for (node in textNodes) {
            val text = node.text.toString().trim()
            if (text.length < 2) continue

            // Filter out system UI like timestamps only (e.g. "10:45 AM") or status icons
            if (isTimestampOnly(text)) continue

            node.getBoundsInScreen(nodeBounds)

            // Determine if incoming or outgoing based on horizontal placement:
            // Chat bubbles on the left (center < 48% of screen) are incoming messages
            // Chat bubbles on the right (center > 52% of screen) are outgoing user messages
            val centerX = nodeBounds.centerX()
            val isIncoming = if (displayWidth > 0) {
                centerX < (displayWidth * 0.52f)
            } else {
                nodeBounds.left < 300
            }

            messages.add(
                ChatMessage(
                    id = "${nodeBounds.left}_${nodeBounds.top}_${text.hashCode()}",
                    text = text,
                    isIncoming = isIncoming,
                    timestamp = System.currentTimeMillis(),
                    boundsLeft = nodeBounds.left,
                    boundsRight = nodeBounds.right
                )
            )
        }

        // Sort vertically by top coordinate (older top, newer bottom)
        return messages.sortedBy { it.id.substringAfter("_").substringBefore("_").toIntOrNull() ?: 0 }
    }

    override fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        // 1. Look for focused editable field
        val focused = all.firstOrNull { it.isFocused && it.isEditable && !it.isPassword }
        if (focused != null) return focused

        // 2. Look for any editable field near the bottom of the screen
        val bottomEditable = all.filter { it.isEditable && !it.isPassword }
            .maxByOrNull {
                val r = Rect()
                it.getBoundsInScreen(r)
                r.bottom
            }
        if (bottomEditable != null) return bottomEditable

        // 3. Fallback: by class name
        return all.firstOrNull {
            it.className?.toString()?.contains("EditText", ignoreCase = true) == true && !it.isPassword
        }
    }

    override fun findSendButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = getAllNodes(rootNode)
        val sendKeywords = listOf("send", "enviar", "envoyer", "invia", "kirim", "submit", "paper airplane", "airplane")

        // 1. Look by content description
        for (node in all) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (sendKeywords.any { desc.contains(it) } && node.isClickable) {
                return node
            }
        }

        // 2. Look by resource ID
        for (node in all) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if ((resId.contains("send") || resId.contains("btn_send") || resId.contains("send_button")) && node.isClickable) {
                return node
            }
        }

        // 3. Look by text
        for (node in all) {
            val txt = node.text?.toString()?.lowercase() ?: ""
            if (sendKeywords.any { txt == it } && node.isClickable) {
                return node
            }
        }

        // 4. Look for clickable icon near the bottom right (x > 80% of width, y > 80% of height)
        val width = getEstimatedWindowWidth(rootNode)
        val rect = Rect()
        return all.filter { it.isClickable && !it.isEditable }.firstOrNull {
            it.getBoundsInScreen(rect)
            width > 0 && rect.left > (width * 0.75f) && rect.top > 800
        }
    }

    protected fun getAllNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return list

        fun traverse(node: AccessibilityNodeInfo) {
            list.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverse(child)
                }
            }
        }

        traverse(root)
        return list
    }

    protected fun getEstimatedWindowWidth(rootNode: AccessibilityNodeInfo): Int {
        val rect = Rect()
        rootNode.getBoundsInScreen(rect)
        return if (rect.width() > 0) rect.width() else 1080
    }

    private fun isTimestampOnly(text: String): Boolean {
        val regex = Regex("^\\d{1,2}:\\d{2}(\\s?(AM|PM|am|pm))?$")
        return regex.matches(text.trim())
    }
}
