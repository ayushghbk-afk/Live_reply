package com.example

import android.app.Application
import com.example.ai.OpenAiCompatibleProvider
import com.example.core.security.SecureStorage
import com.example.notifications.NotificationHelper
import com.example.storage.AppDatabase
import com.example.storage.AppRepository

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
    }
}
