package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.LiveAiReplyApplication

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? LiveAiReplyApplication ?: return
        val repo = app.repository

        when (intent?.action) {
            NotificationHelper.ACTION_PAUSE -> {
                repo.emergencyStopAll()
            }
            NotificationHelper.ACTION_RESUME -> {
                repo.resumeAll()
            }
            NotificationHelper.ACTION_STOP -> {
                repo.emergencyStopAll()
            }
        }
    }
}
