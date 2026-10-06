package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.ocr.ScreenCaptureService
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.LiveAiReplyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LiveAiReplyApplication
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            LiveAiReplyTheme {
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        app.repository.log("Permission", "Notification permission granted: $granted", "INFO")
                    }
                )

                val screenCaptureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = { result ->
                        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                            val serviceIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                            app.repository.log("LiveVision", "👁 Live on-screen sight granted! Auto-seeing messages.", "SUCCESS")
                        } else {
                            app.repository.log("LiveVision", "Screen capture permission declined.", "WARN")
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                AppNavigation(
                    application = app,
                    onRequestScreenCapture = {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                )
            }
        }
    }
}
