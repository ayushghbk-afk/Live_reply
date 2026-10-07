package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.LiveAiReplyApplication
import com.example.core.model.ChatMessage
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import com.example.core.state.LiveSessionState
import com.example.ocr.OcrEngine
import com.example.ocr.ScreenCaptureService
import com.example.storage.ConversationMemoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveReplyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var debounceJob: Job? = null
    private var actionListenerJob: Job? = null

    private val loopPrevention = LoopPreventionManager()
    private val ocrEngine = OcrEngine()

    private val repository by lazy {
        (applicationContext as LiveAiReplyApplication).repository
    }
    private val aiProvider by lazy {
        (applicationContext as LiveAiReplyApplication).aiProvider
    }

    companion object {
        var isServiceRunning: Boolean = false
            private set

        fun isServiceEnabledInSettings(context: android.content.Context): Boolean {
            return try {
                val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                    ?: return false
                val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                enabledServices.any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.notificationTimeout = 120
            serviceInfo = info

            isServiceRunning = true
            LiveSessionState.setAccessibilityConnected(true)
            repository.log("Accessibility", "Accessibility service connected and active", "SUCCESS")
            startListeningToOverlayActions()
        } catch (t: Throwable) {
            // Must never let onServiceConnected fail
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val config = repository.appConfig.value
            if (!config.isMonitoringActive || config.isPaused) {
                return
            }

            val packageName = event.packageName?.toString() ?: return

            // Ignore our own app package to avoid recursion
            if (packageName == applicationContext.packageName) return

            // Human override detection: if user types in the input field, pause auto-sending
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val source = event.source
                if (source != null && source.isEditable && source.isFocused && !source.isPassword) {
                    val entered = event.text?.joinToString("") ?: ""
                    val ourLatestReply = LiveSessionState.latestGeneratedReply.value ?: ""
                    // If the text being typed is not our programmatic insertion
                    if (entered.isNotBlank() && !entered.contains(ourLatestReply)) {
                        LiveSessionState.setManualTyping(true)
                        repository.log("Automation", "Manual typing detected in $packageName. Auto-send paused.", "INFO")
                    }
                }
            }

            // Only process content or window changes
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return
            }

            // Schedule debounced inspection
            scheduleDebouncedInspection(packageName)
        } catch (t: Throwable) {
            // Guarantee no unhandled exception kills the accessibility service
        }
    }

    private fun scheduleDebouncedInspection(packageName: String) {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            val config = repository.appConfig.value
            delay(config.debounceDelayMs)

            // Check if app is enabled
            if (!repository.isAppEnabled(packageName)) {
                return@launch
            }

            inspectActiveChat(packageName)
        }
    }

    private suspend fun inspectActiveChat(packageName: String) = withContext(Dispatchers.Default) {
        try {
            val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return@withContext
            val adapter = ChatAdapterRegistry.getAdapterFor(packageName)

            // Safety filter: never capture password, PIN, or sensitive payment screens
            if (containsSensitiveFields(root)) {
                repository.log("Safety", "Sensitive security/password fields detected in $packageName. Skipping.", "WARN")
                return@withContext
            }

            val chatTitle = adapter.getChatTitle(root) ?: adapter.appName
            LiveSessionState.updateActiveChat(packageName, chatTitle)

            // Check if user paused this specific conversation
            if (repository.isConversationPaused(packageName, chatTitle)) {
                return@withContext
            }

            // Extract messages using accessibility node tree
            var messages = adapter.extractConversation(root)

            // Fallback to local OCR if accessibility yielded no messages and OCR is enabled
            val config = repository.appConfig.value
            if (messages.isEmpty() && config.ocrFallbackEnabled) {
                val captureService = ScreenCaptureService.instance
                val frame = captureService?.captureLatestFrame()
                if (frame != null) {
                    messages = try {
                        ocrEngine.recognizeConversation(frame)
                    } finally {
                        if (!frame.isRecycled) {
                            frame.recycle()
                        }
                    }
                    repository.log("OCR", "Extracted ${messages.size} messages via local OCR and deleted screenshot immediately", "INFO")
                }
            }

            if (messages.isEmpty()) return@withContext

            // Identify the latest incoming message burst (captures all consecutive rapid-fire messages)
            val burst = ChatReaderEngine.extractLatestIncomingBurst(messages) ?: return@withContext
            val latestIncoming = burst.individualMessages.last()

            // Loop and duplicate prevention check
            if (!loopPrevention.shouldProcessMessage(latestIncoming)) {
                return@withContext
            }

            // We have a confirmed new incoming message!
            loopPrevention.markMessageProcessed(latestIncoming.text)
            val textToReplyTo = burst.combinedText
            val displaySender = burst.primarySender ?: chatTitle

            LiveSessionState.setNewIncomingMessage(textToReplyTo, messages)
            repository.log("Detector", "New incoming message from $displaySender in $chatTitle: \"${textToReplyTo.replace("\n", " // ").take(60)}\"", "INFO")

            // Auto-update conversation memory with new message context
            autoLearnConversationMemory(packageName, chatTitle, textToReplyTo)

            // Trigger AI Reply Generation with the full burst context
            processIncomingMessage(textToReplyTo, messages, packageName)
        } catch (t: Throwable) {
            repository.log("Accessibility", "Safe recovery from window change: ${t.message}", "WARN")
        }
    }

    /**
     * Automatically builds and retains long-term memory about contacts,
     * conversation topics, and relationship dynamics.
     */
    private suspend fun autoLearnConversationMemory(
        packageName: String,
        chatTitle: String,
        newIncomingText: String
    ) = withContext(Dispatchers.IO) {
        try {
            val key = "$packageName:$chatTitle"
            val existing = repository.getMemory(key)
            val updated = if (existing == null) {
                ConversationMemoryEntity(
                    conversationKey = key,
                    contactName = chatTitle,
                    packageName = packageName,
                    summary = "Active conversation in $packageName.",
                    facts = "- First observed message: \"${newIncomingText.take(80)}\"",
                    relationshipNote = "Frequent chat contact.",
                    lastInteractedAt = System.currentTimeMillis()
                )
            } else {
                val existingFacts = existing.facts
                val hasFactAlready = existingFacts.contains(newIncomingText.take(40))
                val newFacts = if (!hasFactAlready && existingFacts.length < 500) {
                    "$existingFacts\n- Recent topic: \"${newIncomingText.take(60)}\""
                } else existingFacts

                existing.copy(
                    facts = newFacts,
                    lastInteractedAt = System.currentTimeMillis()
                )
            }
            repository.saveMemory(updated)
        } catch (e: Exception) {
            // Memory storage failure is non-fatal
        }
    }

    private suspend fun processIncomingMessage(
        incomingText: String,
        context: List<ChatMessage>,
        packageName: String
    ) = withContext(Dispatchers.Default) {
        val config = repository.appConfig.value
        if (!config.isMonitoringActive || config.isPaused) return@withContext

        val persona = repository.getSelectedPersona() ?: return@withContext
        LiveSessionState.updateState(ProcessingState.THINKING)

        val result = aiProvider.generateReply(context, incomingText, persona, config)

        result.fold(
            onSuccess = { reply ->
                LiveSessionState.setGeneratedReply(reply)
                handleGeneratedReply(reply, packageName)
            },
            onFailure = { error ->
                val errorMsg = error.localizedMessage ?: "Failed to generate reply"
                LiveSessionState.setError(errorMsg)
                repository.log("AI", "Generation error: $errorMsg", "ERROR")
            }
        )
    }

    private suspend fun handleGeneratedReply(reply: String, packageName: String) {
        val config = repository.appConfig.value

        when (config.operatingMode) {
            OperatingMode.SUGGEST -> {
                // Keep reply in overlay, user can tap Send/Edit/Reject
                repository.log("Mode", "SUGGEST mode: Reply ready in overlay.", "INFO")
            }
            OperatingMode.APPROVE -> {
                // Keep reply in overlay, waits for explicit Send tap
                repository.log("Mode", "APPROVE mode: Waiting for user one-tap approval.", "INFO")
            }
            OperatingMode.AUTO -> {
                // Automatically enter and send after configured natural delay
                repository.log("Mode", "AUTO mode: Waiting ${config.replyDelaySeconds}s delay before auto-reply...", "INFO")
                delay(config.replyDelaySeconds * 1000L)

                // Check again that emergency stop or manual typing didn't trigger
                val currentConfig = repository.appConfig.value
                if (!currentConfig.isMonitoringActive || currentConfig.isPaused) {
                    repository.log("Automation", "Auto-reply aborted: System paused.", "WARN")
                    return
                }

                if (LiveSessionState.manualTypingDetected.value) {
                    repository.log("Automation", "Auto-reply aborted: User is typing manually.", "INFO")
                    LiveSessionState.setManualTyping(false)
                    return
                }

                insertAndSendReply(reply, packageName)
            }
        }
    }

    private suspend fun insertAndSendReply(reply: String, packageName: String) = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow ?: run {
            LiveSessionState.setError("Cannot access chat window to insert reply.")
            return@withContext
        }

        val adapter = ChatAdapterRegistry.getAdapterFor(packageName)
        val inputField = adapter.findInputField(root)

        if (inputField == null) {
            val err = "Could not find chat input field in $packageName."
            LiveSessionState.setError(err)
            repository.log("Automation", err, "WARN")
            return@withContext
        }

        LiveSessionState.updateState(ProcessingState.TYPING)

        val config = repository.appConfig.value
        val success = if (config.simulateTyping) {
            simulateHumanTyping(inputField, reply, config.typingSpeedCpm)
        } else {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
            }
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        if (!success) {
            LiveSessionState.setError("Failed to insert text into input field.")
            return@withContext
        }

        delay(300)

        // Find send button and execute smart dispatch
        LiveSessionState.updateState(ProcessingState.SENDING)
        val refreshedRoot = rootInActiveWindow ?: root
        val sendBtn = adapter.findSendButton(refreshedRoot)

        // Use adapter's specialized send action logic:
        // - WhatsApp supports dedicated button or Enter-to-send
        // - ChatGPT ignores Enter key, requires tapping the dedicated submit/arrow button
        // - Telegram/Instagram/Discord click the respective send button
        val sentSuccessfully = adapter.dispatchSendAction(inputField, sendBtn)

        if (sentSuccessfully) {
            loopPrevention.recordSentReply(reply)
            LiveSessionState.markReplySent(reply)
            repository.log("Automation", "Reply sent successfully in $packageName via ${adapter.appName} adapter!", "SUCCESS")
        } else {
            // Leave text in input box for user to tap send manually if action couldn't be dispatched
            repository.log("Automation", "Inserted text into chat input. Please tap send if auto-tap was resisted.", "INFO")
            LiveSessionState.markReplySent(reply)
        }
    }

    private suspend fun simulateHumanTyping(
        inputField: AccessibilityNodeInfo,
        text: String,
        cpm: Int
    ): Boolean {
        val charDelayMs = ((60000L / cpm.coerceIn(100, 1000))).coerceIn(30L, 200L)
        val builder = StringBuilder()

        for (char in text) {
            builder.append(char)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, builder.toString())
            }
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(charDelayMs)
        }
        return true
    }

    private fun startListeningToOverlayActions() {
        actionListenerJob?.cancel()
        actionListenerJob = serviceScope.launch {
            LiveSessionState.overlayActionEvents.collectLatest { action ->
                when (action) {
                    is LiveSessionState.OverlayAction.Send -> {
                        val reply = LiveSessionState.editedReply.value ?: LiveSessionState.latestGeneratedReply.value
                        val pkg = LiveSessionState.currentPackageName.value
                        if (!reply.isNullOrBlank() && !pkg.isNullOrBlank()) {
                            insertAndSendReply(reply, pkg)
                        }
                    }
                    is LiveSessionState.OverlayAction.Regenerate -> {
                        val incoming = LiveSessionState.latestIncomingMessage.value
                        val context = LiveSessionState.conversationContext.value
                        val pkg = LiveSessionState.currentPackageName.value
                        if (!incoming.isNullOrBlank() && !pkg.isNullOrBlank()) {
                            processIncomingMessage(incoming, context, pkg)
                        }
                    }
                    is LiveSessionState.OverlayAction.Reject -> {
                        LiveSessionState.clearReply()
                        LiveSessionState.updateState(ProcessingState.MONITORING)
                        repository.log("Overlay", "Reply dismissed by user.", "INFO")
                    }
                    is LiveSessionState.OverlayAction.Edit -> {
                        LiveSessionState.updateEditedReply(action.newText)
                    }
                    is LiveSessionState.OverlayAction.EmergencyStop -> {
                        repository.emergencyStopAll()
                        LiveSessionState.updateState(ProcessingState.PAUSED)
                    }
                    is LiveSessionState.OverlayAction.PauseCurrentChat -> {
                        val pkg = LiveSessionState.currentPackageName.value
                        val title = LiveSessionState.currentChatTitle.value
                        if (!pkg.isNullOrBlank() && !title.isNullOrBlank()) {
                            repository.pauseConversation(pkg, title)
                            LiveSessionState.clearReply()
                            LiveSessionState.updateState(ProcessingState.MONITORING)
                        }
                    }
                    is LiveSessionState.OverlayAction.ScanScreenNow -> {
                        val capture = ScreenCaptureService.instance
                        if (capture != null) {
                            capture.scanNow()
                        } else {
                            val pkg = LiveSessionState.currentPackageName.value ?: "com.whatsapp"
                            inspectActiveChat(pkg)
                        }
                    }
                    is LiveSessionState.OverlayAction.ToggleAutoSee -> {
                        LiveSessionState.setAutoSeeActive(action.enabled)
                        repository.log("LiveVision", "Auto-See on screen toggled: ${action.enabled}", "INFO")
                    }
                    is LiveSessionState.OverlayAction.SwitchPersona -> {
                        repository.selectPersona(action.personaId)
                        val p = repository.getSelectedPersona()
                        if (p != null) {
                            LiveSessionState.setActivePersonaName("${p.category.icon} ${p.name}")
                        }
                    }
                    is LiveSessionState.OverlayAction.SetOperatingMode -> {
                        repository.updateOperatingMode(action.mode)
                    }
                    is LiveSessionState.OverlayAction.ToggleTypingSimulation -> {
                        repository.updateSimulateTyping(action.enabled)
                    }
                }
            }
        }
    }

    private fun containsSensitiveFields(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isPassword) return true

        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        val resId = (node.viewIdResourceName?.toString() ?: "").lowercase()

        val sensitiveWords = listOf("password", "pin_code", "cvv", "credit_card", "security_code", "bank_account", "otp")
        if (sensitiveWords.any { desc.contains(it) || text.contains(it) || resId.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsSensitiveFields(child)) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        LiveSessionState.setAccessibilityConnected(false)
        repository.log("Accessibility", "Accessibility service interrupted", "WARN")
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceJob?.cancel()
        actionListenerJob?.cancel()
        LiveSessionState.setAccessibilityConnected(false)
        repository.log("Accessibility", "Accessibility service destroyed", "WARN")
    }
}
