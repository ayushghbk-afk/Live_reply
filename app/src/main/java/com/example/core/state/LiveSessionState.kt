package com.example.core.state

import com.example.core.model.ChatMessage
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object LiveSessionState {

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _currentPackageName = MutableStateFlow<String?>(null)
    val currentPackageName: StateFlow<String?> = _currentPackageName.asStateFlow()

    private val _currentChatTitle = MutableStateFlow<String?>("Chat")
    val currentChatTitle: StateFlow<String?> = _currentChatTitle.asStateFlow()

    private val _latestIncomingMessage = MutableStateFlow<String?>(null)
    val latestIncomingMessage: StateFlow<String?> = _latestIncomingMessage.asStateFlow()

    private val _conversationContext = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationContext: StateFlow<List<ChatMessage>> = _conversationContext.asStateFlow()

    private val _latestGeneratedReply = MutableStateFlow<String?>(null)
    val latestGeneratedReply: StateFlow<String?> = _latestGeneratedReply.asStateFlow()

    private val _editedReply = MutableStateFlow<String?>(null)
    val editedReply: StateFlow<String?> = _editedReply.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _isOverlayShowing = MutableStateFlow(false)
    val isOverlayShowing: StateFlow<Boolean> = _isOverlayShowing.asStateFlow()

    private val _isScreenCaptureActive = MutableStateFlow(false)
    val isScreenCaptureActive: StateFlow<Boolean> = _isScreenCaptureActive.asStateFlow()

    private val _isAutoSeeActive = MutableStateFlow(true)
    val isAutoSeeActive: StateFlow<Boolean> = _isAutoSeeActive.asStateFlow()

    private val _manualTypingDetected = MutableStateFlow(false)
    val manualTypingDetected: StateFlow<Boolean> = _manualTypingDetected.asStateFlow()

    // Loop & duplicate prevention hashes
    var lastProcessedMessageHash: Int = 0
    var lastSentReplyHash: Int = 0
    var lastIncomingMessageText: String = ""

    // Action Triggers
    sealed class OverlayAction {
        object Send : OverlayAction()
        object Regenerate : OverlayAction()
        object Reject : OverlayAction()
        data class Edit(val newText: String) : OverlayAction()
        object EmergencyStop : OverlayAction()
        object PauseCurrentChat : OverlayAction()
        object ScanScreenNow : OverlayAction()
        data class ToggleAutoSee(val enabled: Boolean) : OverlayAction()
    }

    private val _overlayActionEvents = MutableSharedFlow<OverlayAction>(extraBufferCapacity = 10)
    val overlayActionEvents: SharedFlow<OverlayAction> = _overlayActionEvents.asSharedFlow()

    fun updateState(state: ProcessingState) {
        _processingState.value = state
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
        if (!connected && _processingState.value != ProcessingState.PAUSED) {
            _processingState.value = ProcessingState.IDLE
        } else if (connected && _processingState.value == ProcessingState.IDLE) {
            _processingState.value = ProcessingState.MONITORING
        }
    }

    fun setOverlayShowing(showing: Boolean) {
        _isOverlayShowing.value = showing
    }

    fun setScreenCaptureActive(active: Boolean) {
        _isScreenCaptureActive.value = active
    }

    fun setAutoSeeActive(active: Boolean) {
        _isAutoSeeActive.value = active
    }

    fun setManualTyping(detected: Boolean) {
        _manualTypingDetected.value = detected
    }

    fun updateActiveChat(packageName: String?, chatTitle: String?) {
        _currentPackageName.value = packageName
        if (!chatTitle.isNullOrBlank()) {
            _currentChatTitle.value = chatTitle
        }
    }

    fun setNewIncomingMessage(text: String, context: List<ChatMessage>) {
        _latestIncomingMessage.value = text
        _conversationContext.value = context
        lastIncomingMessageText = text
        lastProcessedMessageHash = text.trim().hashCode()
    }

    fun setGeneratedReply(reply: String) {
        _latestGeneratedReply.value = reply
        _editedReply.value = reply
        _errorMessage.value = null
        _processingState.value = ProcessingState.REPLY_READY
    }

    fun updateEditedReply(text: String) {
        _editedReply.value = text
    }

    fun setError(message: String) {
        _errorMessage.value = message
        _processingState.value = ProcessingState.ERROR
    }

    fun clearReply() {
        _latestGeneratedReply.value = null
        _editedReply.value = null
        _errorMessage.value = null
    }

    fun triggerAction(action: OverlayAction) {
        _overlayActionEvents.tryEmit(action)
    }

    fun markReplySent(replyText: String) {
        lastSentReplyHash = replyText.trim().hashCode()
        clearReply()
        _processingState.value = ProcessingState.MONITORING
    }
}
