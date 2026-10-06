package com.example

import android.app.Application
import com.example.ai.OpenAiCompatibleProvider
import com.example.core.security.SecureStorage
import com.example.core.state.LiveSessionState
import com.example.notifications.NotificationHelper
import com.example.storage.AppDatabase
import com.example.storage.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LiveAiReplyApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var secureStorage: SecureStorage
        private set

    lateinit var repository: AppRepository
        private set

    lateinit var aiProvider: OpenAiCompatibleProvider
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        database = AppDatabase.getInstance(this)
        secureStorage = SecureStorage(this)
        repository = AppRepository(this, database.appDao(), secureStorage)
        aiProvider = OpenAiCompatibleProvider(secureStorage, repository)

        CoroutineScope(Dispatchers.IO).launch {
            repository.selectedPersonaFlow.collectLatest { persona ->
                if (persona != null) {
                    LiveSessionState.setActivePersonaName("${persona.category.icon} ${persona.name}")
                }
            }
        }
    }
}
