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
        // Default send strategy: Click dedicated send button if found, or click input field
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
        return findInputField(rootNode) != null
    }

    override fun getChatTitle(rootNode: AccessibilityNodeInfo): String? {
        val outBounds = Rect()
        for (node in getAllNodes(rootNode)) {
            node.getBoundsInScreen(outBounds)
            // Near top of screen (y < 350) and has text
            if (outBounds.top < 350 && outBounds.bottom > 50 && !node.text.isNullOrBlank()) {
                val txt = node.text.toString().trim()
                if (txt.length in 2..40 && !ChatReaderEngine.isSystemNoise(txt)) {
                    return txt
                }
            }
        }
        return null
    }

    override fun extractConversation(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val allNodes = getAllNodes(rootNode)
        val displayWidth = getEstimatedWindowWidth(rootNode)

        // 1. First attempt: Grouped Container Extraction (RecyclerView / ListView message rows)
        val containerMessages = extractFromMessageContainers(allNodes, displayWidth)
        if (containerMessages.isNotEmpty()) {
            return containerMessages.sortedBy { it.boundsTop }
        }

        // 2. Fallback: Flat Node Extraction with deep noise filtering & clustering
        return extractFromFlatNodes(allNodes, displayWidth).sortedBy { it.boundsTop }
    }

    /**
     * Inspects structured message item containers to accurately isolate sender, quote, and body.
     */
    protected open fun extractFromMessageContainers(
        allNodes: List<AccessibilityNodeInfo>,
        displayWidth: Int
    ): List<ChatMessage> {
        val results = mutableListOf<ChatMessage>()
        val nodeBounds = Rect()

        // Find candidate message row containers (ViewGroup, LinearLayout, FrameLayout with multiple children)
        val candidateContainers = allNodes.filter { node ->
            val childCount = node.childCount
            childCount in 1..10 &&
            !node.isEditable &&
            !node.isPassword &&
            node.className?.contains("Button", ignoreCase = true) != true
        }

        for (container in candidateContainers) {
            container.getBoundsInScreen(nodeBounds)

            // Exclude status bar and input bar areas
            if (nodeBounds.top < 100 || nodeBounds.bottom > 2200 || nodeBounds.height() < 30) continue

            val textChildren = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
            for (i in 0 until container.childCount) {
                val child = container.getChild(i) ?: continue
                if (!child.text.isNullOrBlank() && !child.isEditable && !child.isPassword) {
                    val childBounds = Rect()
                    child.getBoundsInScreen(childBounds)
                    val rawText = child.text.toString().trim()
                    if (rawText.isNotBlank() && !ChatReaderEngine.isSystemNoise(rawText)) {
                        textChildren.add(Pair(child, childBounds))
                    }
                }
            }

            if (textChildren.isEmpty()) continue

            // Determine incoming vs outgoing from container placement
            val centerX = nodeBounds.centerX()
            val isIncoming = centerX < (displayWidth * 0.52f)

            var senderName: String? = null
            var quoteText: String? = null
            val bodyParts = mutableListOf<String>()

            // Sort child text by vertical appearance
            textChildren.sortBy { it.second.top }

            for ((child, bounds) in textChildren) {
                val txt = child.text.toString().trim()
                val idName = child.viewIdResourceName?.lowercase() ?: ""
                val desc = child.contentDescription?.toString()?.lowercase() ?: ""

                if (idName.contains("sender") || idName.contains("name") || idName.contains("author") || idName.contains("contact")) {
                    senderName = txt
                } else if (idName.contains("quote") || idName.contains("reply") || desc.contains("reply") || txt.startsWith("Replying to", ignoreCase = true)) {
                    quoteText = txt
                } else {
                    bodyParts.add(txt)
                }
            }

            val finalBody = bodyParts.joinToString(" ").trim()
            if (finalBody.length >= 2) {
                results.add(
                    ChatMessage(
                        id = "${nodeBounds.left}_${nodeBounds.top}_${finalBody.hashCode()}",
                        text = finalBody,
                        isIncoming = isIncoming,
                        senderName = senderName,
                        replyToText = quoteText,
                        timestamp = System.currentTimeMillis(),
                        boundsLeft = nodeBounds.left,
                        boundsTop = nodeBounds.top,
                        boundsRight = nodeBounds.right,
                        boundsBottom = nodeBounds.bottom
                    )
                )
            }
        }

        return results
    }

    /**
     * Fallback extraction directly from text nodes when containers cannot be identified.
     */
    protected open fun extractFromFlatNodes(
        allNodes: List<AccessibilityNodeInfo>,
        displayWidth: Int
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
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

            // Filter out system UI like timestamps only or status icons
            if (ChatReaderEngine.isSystemNoise(text)) continue

            node.getBoundsInScreen(nodeBounds)

            // Exclude status bar and keyboard/input area
            if (nodeBounds.top < 100) continue

            // Determine if incoming or outgoing based on horizontal placement
            val centerX = nodeBounds.centerX()
            val isIncoming = if (displayWidth > 0) {
                centerX < (displayWidth * 0.52f)
            } else {
                nodeBounds.left < 300
            }

            // Check if media indicator
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val mediaType = when {
                desc.contains("voice message") || desc.contains("audio") -> "voice_note"
                desc.contains("photo") || desc.contains("image") -> "photo"
                desc.contains("video") -> "video"
                desc.contains("sticker") -> "sticker"
                else -> null
            }

            messages.add(
                ChatMessage(
                    id = "${nodeBounds.left}_${nodeBounds.top}_${text.hashCode()}",
                    text = text,
                    isIncoming = isIncoming,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis(),
                    boundsLeft = nodeBounds.left,
                    boundsTop = nodeBounds.top,
                    boundsRight = nodeBounds.right,
                    boundsBottom = nodeBounds.bottom
                )
            )
        }

        return messages
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

        // 4. Look for clickable icon near the bottom right (x > 75% of width, y > 800)
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
}
