package com.example.overlay

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.LiveAiReplyApplication
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import com.example.core.state.LiveSessionState
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var notificationSyncJob: Job? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val notification = NotificationHelper.buildServiceNotification(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)

        if (Settings.canDrawOverlays(this)) {
            initOverlayView()
        }

        observeSessionState()
    }

    private fun observeSessionState() {
        val repo = (applicationContext as LiveAiReplyApplication).repository
        notificationSyncJob = serviceScope.launch {
            LiveSessionState.processingState.collectLatest { state ->
                val app = LiveSessionState.currentChatTitle.value
                val mode = repo.appConfig.value.operatingMode
                val isPaused = repo.appConfig.value.isPaused || !repo.appConfig.value.isMonitoringActive
                val notif = NotificationHelper.buildServiceNotification(
                    this@OverlayService,
                    app,
                    mode,
                    state,
                    isPaused
                )
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NotificationHelper.NOTIFICATION_ID, notif)
            }
        }
    }

    private fun initOverlayView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF6C8CFF),
                        secondary = Color(0xFF00E5FF),
                        surface = Color(0xFF141824),
                        background = Color(0xFF0D101A)
                    )
                ) {
                    OverlayContent(
                        onDragDelta = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(this@apply, params)
                            } catch (e: Exception) {
                                // Ignore layout race
                            }
                        },
                        onRequestFocus = { needFocus ->
                            if (needFocus) {
                                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                            } else {
                                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            }
                            try {
                                windowManager.updateViewLayout(this@apply, params)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(overlayView, params)
            LiveSessionState.setOverlayShowing(true)
        } catch (e: Exception) {
            LiveSessionState.setOverlayShowing(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationSyncJob?.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        LiveSessionState.setOverlayShowing(false)
    }
}

@Composable
fun OverlayContent(
    onDragDelta: (Float, Float) -> Unit,
    onRequestFocus: (Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    val state by LiveSessionState.processingState.collectAsState()
    val incomingText by LiveSessionState.latestIncomingMessage.collectAsState()
    val generatedReply by LiveSessionState.latestGeneratedReply.collectAsState()
    val editedReply by LiveSessionState.editedReply.collectAsState()
    val errorMsg by LiveSessionState.errorMessage.collectAsState()
    val activeChat by LiveSessionState.currentChatTitle.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val dotColor = when (state) {
        ProcessingState.MONITORING -> Color(0xFF00E676)
        ProcessingState.THINKING -> Color(0xFFFFD600)
        ProcessingState.REPLY_READY -> Color(0xFF00E5FF)
        ProcessingState.TYPING -> Color(0xFFFF9100)
        ProcessingState.SENDING -> Color(0xFF651FFF)
        ProcessingState.ERROR -> Color(0xFFFF5252)
        ProcessingState.PAUSED, ProcessingState.IDLE -> Color(0xFF9E9E9E)
    }

    if (!isExpanded) {
        // Collapsed Floating Mode
        if (generatedReply != null) {
            // Google Translate-Style Live Reply Floating Chip
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF010162A))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Column(
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .clickable { isExpanded = true }
                    ) {
                        Text(
                            text = "✨ " + (editedReply ?: generatedReply!!),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Live Reply Ready • Tap",
                            color = Color(0xFF80D8FF),
                            fontSize = 9.sp
                        )
                    }
                    Button(
                        onClick = {
                            LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Send)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Reject)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            // Collapsed Floating AI Eye Pill
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF161B2E))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Text(
                        text = "AI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { isExpanded = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )

                    IconButton(
                        onClick = {
                            LiveSessionState.triggerAction(LiveSessionState.OverlayAction.ScanScreenNow)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Scan Screen Now",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Expanded Interactive Card
        Card(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF0131726)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live AI Reply",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$activeChat • ${state.label}",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            isExpanded = false
                            onRequestFocus(false)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Minimize",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Live Screen Sight Control Strip (Google Translate Live Mode)
                Surface(
                    color = Color(0xFF1E2842),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Live Screen Sight",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                LiveSessionState.triggerAction(LiveSessionState.OverlayAction.ScanScreenNow)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Scan Now", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Incoming Message Box
                if (!incomingText.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF1E2438),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "LATEST MESSAGE:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = incomingText!!,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 3
                            )
                        }
                    }
                }

                // Reply Box or Editing Field
                if (!generatedReply.isNullOrBlank()) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedReply ?: generatedReply ?: "",
                            onValueChange = {
                                LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Edit(it))
                            },
                            label = { Text("Edit AI Reply", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Surface(
                            color = Color(0xFF1A334B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "SUGGESTED REPLY:",
                                    color = Color(0xFF80D8FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = editedReply ?: generatedReply!!,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Action Buttons Row: Send, Edit, Regenerate, Copy, Reject
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Send)
                                isExpanded = false
                                onRequestFocus(false)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00C853)
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Send", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                isEditing = !isEditing
                                onRequestFocus(isEditing)
                            },
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(if (isEditing) "Done" else "Edit", fontSize = 10.sp)
                        }

                        IconButton(
                            onClick = {
                                LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Regenerate)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                val textToCopy = editedReply ?: generatedReply ?: ""
                                clipboardManager.setText(AnnotatedString(textToCopy))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Reject)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Error Message if any
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp
                    )
                }

                // Secondary controls: Pause Chat and Emergency STOP AI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            LiveSessionState.triggerAction(LiveSessionState.OverlayAction.PauseCurrentChat)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause Chat", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            LiveSessionState.triggerAction(LiveSessionState.OverlayAction.EmergencyStop)
                            isExpanded = false
                            onRequestFocus(false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("STOP AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
