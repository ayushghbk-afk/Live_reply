package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState

object NotificationHelper {

    const val CHANNEL_ID = "live_ai_reply_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PAUSE = "com.example.liveaireply.ACTION_PAUSE"
    const val ACTION_RESUME = "com.example.liveaireply.ACTION_RESUME"
    const val ACTION_STOP = "com.example.liveaireply.ACTION_STOP"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildServiceNotification(
        context: Context,
        activeApp: String? = "None",
        mode: OperatingMode = OperatingMode.SUGGEST,
        state: ProcessingState = ProcessingState.MONITORING,
        isPaused: Boolean = false
    ): Notification {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause / Resume
        val pauseResumeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Emergency Stop
        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) "⏸ PAUSED" else state.label
        val content = "App: ${activeApp ?: "Watching"} • Mode: ${mode.displayName} • $statusText"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Live AI Reply")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (isPaused) "▶ Resume" else "⏸ Pause",
                pauseResumePendingIntent
            )
            .addAction(
                0,
                "🛑 Stop AI",
                stopPendingIntent
            )

        return builder.build()
    }
}
