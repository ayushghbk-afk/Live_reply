package com.example.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.LiveAiReplyApplication
import com.example.R
import com.example.accessibility.LoopPreventionManager
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import com.example.core.state.LiveSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val ocrEngine = OcrEngine()
    private val loopPrevention = LoopPreventionManager()
    private var autoSeeJob: Job? = null

    private val repository by lazy {
        (applicationContext as LiveAiReplyApplication).repository
    }
    private val aiProvider by lazy {
        (applicationContext as LiveAiReplyApplication).aiProvider
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        startForegroundNotification()

        if (resultCode != 0 && resultData != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
            LiveSessionState.setScreenCaptureActive(true)
            initVirtualDisplay()
            startContinuousScreenVision()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screen_capture_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.screen_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_capture_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Live Screen Vision Active • Auto-seeing messages")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    /**
     * Continuous live screen vision loop like Google Translate live screen mode.
     * Periodically captures the screen in the background, extracts text via ML Kit OCR,
     * spots incoming messages, and triggers AI responses live on screen.
     */
    private fun startContinuousScreenVision() {
        autoSeeJob?.cancel()
        autoSeeJob = scope.launch {
            repository.log("LiveVision", "Continuous on-screen vision started (Google Translate Live Mode)", "SUCCESS")
            while (isActive) {
                val config = repository.appConfig.value
                val isAutoSeeActive = LiveSessionState.isAutoSeeActive.value

                if (config.isMonitoringActive && !config.isPaused && isAutoSeeActive && config.autoSeeOnScreen) {
                    try {
                        scanAndProcessScreen()
                    } catch (e: Exception) {
                        // Keep loop resilient
                    }
                }
                delay(config.scanIntervalMs.coerceIn(1500L, 5000L))
            }
        }
    }

    fun scanNow() {
        scope.launch {
            repository.log("LiveVision", "Manual screen scan triggered by user", "INFO")
            scanAndProcessScreen()
        }
    }

    private suspend fun scanAndProcessScreen() = withContext(Dispatchers.Default) {
        // Skip scanning if currently in the middle of thinking or user is actively typing
        val currentState = LiveSessionState.processingState.value
        if (currentState == ProcessingState.THINKING || currentState == ProcessingState.TYPING || currentState == ProcessingState.SENDING) {
            return@withContext
        }

        val frame = captureLatestFrame() ?: return@withContext
        val detectedMessages = ocrEngine.recognizeConversation(frame)

        if (detectedMessages.isEmpty()) return@withContext

        val latestIncoming = detectedMessages.lastOrNull { it.isIncoming } ?: return@withContext

        // Loop & duplicate prevention check
        if (!loopPrevention.shouldProcessMessage(latestIncoming)) {
            return@withContext
        }

        // Confirmed new incoming message seen live on screen!
        loopPrevention.markMessageProcessed(latestIncoming.text)
        LiveSessionState.setNewIncomingMessage(latestIncoming.text, detectedMessages)
        repository.log("LiveVision", "👁 Spotted live message on screen: \"${latestIncoming.text.take(50)}\"", "INFO")

        // Trigger AI response generation
        val persona = repository.getSelectedPersona() ?: return@withContext
        val config = repository.appConfig.value

        LiveSessionState.updateState(ProcessingState.THINKING)
        val result = aiProvider.generateReply(detectedMessages, latestIncoming.text, persona, config)

        result.fold(
            onSuccess = { reply ->
                LiveSessionState.setGeneratedReply(reply)
                repository.log("LiveVision", "✨ Live on-screen reply ready: \"${reply.take(50)}\"", "SUCCESS")

                // If in AUTO mode, execute send after configured delay
                if (config.operatingMode == OperatingMode.AUTO) {
                    delay(config.replyDelaySeconds * 1000L)
                    if (repository.appConfig.value.isMonitoringActive && !repository.appConfig.value.isPaused && !LiveSessionState.manualTypingDetected.value) {
                        LiveSessionState.triggerAction(LiveSessionState.OverlayAction.Send)
                    }
                }
            },
            onFailure = { err ->
                val errorMsg = err.localizedMessage ?: "Failed to generate reply"
                LiveSessionState.setError(errorMsg)
                repository.log("LiveVision", "Error generating live reply: $errorMsg", "WARN")
            }
        )
    }

    fun captureLatestFrame(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            // Trim row padding if present
            if (rowPadding > 0) {
                val clean = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                clean
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSeeJob?.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        LiveSessionState.setScreenCaptureActive(false)
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 2002

        var instance: ScreenCaptureService? = null
            private set
    }
}
