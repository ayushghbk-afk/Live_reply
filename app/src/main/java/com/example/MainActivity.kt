package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.LiveAiReplyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LiveAiReplyApplication

        setContent {
            LiveAiReplyTheme {
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        app.repository.log("Permission", "Notification permission granted: $granted", "INFO")
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

                AppNavigation(application = app)
            }
        }
    }
}
