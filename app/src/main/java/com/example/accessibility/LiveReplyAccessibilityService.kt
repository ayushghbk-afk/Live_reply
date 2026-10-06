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

    override fun onServiceConnected() {
        super.onServiceConnected()
        LiveSessionState.setAccessibilityConnected(true)
        repository.log("Accessibility", "Accessibility service connected and active", "SUCCESS")
        startListeningToOverlayActions()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

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
        val root = rootInActiveWindow ?: return@withContext
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

        // Identify the latest incoming message
        val latestIncoming = messages.lastOrNull { it.isIncoming } ?: return@withContext

        // Loop and duplicate prevention check
        if (!loopPrevention.shouldProcessMessage(latestIncoming)) {
            return@withContext
        }

        // We have a confirmed new incoming message!
        loopPrevention.markMessageProcessed(latestIncoming.text)
        LiveSessionState.setNewIncomingMessage(latestIncoming.text, messages)
        repository.log("Detector", "New incoming message in $chatTitle: \"${latestIncoming.text.take(60)}\"", "INFO")

        // Trigger AI Reply Generation
        processIncomingMessage(latestIncoming.text, messages, packageName)
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

        // Find send button and click
        LiveSessionState.updateState(ProcessingState.SENDING)
        val refreshedRoot = rootInActiveWindow ?: root
        val sendBtn = adapter.findSendButton(refreshedRoot)

        if (sendBtn != null) {
            val clicked = sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                loopPrevention.recordSentReply(reply)
                LiveSessionState.markReplySent(reply)
                repository.log("Automation", "Reply sent successfully in $packageName!", "SUCCESS")
            } else {
                LiveSessionState.setError("Found send button but click action failed.")
            }
        } else {
            // Cannot find send button reliably - leave text in input box for user to tap send
            repository.log("Automation", "Inserted text into chat. Please tap send.", "INFO")
            LiveSessionState.markReplySent(reply)
        }
    }

    private suspend fun simulateHumanTyping(
        inputField: AccessibilityNodeInfo,
        text: String,
        cpm: Int
    ): Boolean {
        // Average delay per character based on CPM
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
