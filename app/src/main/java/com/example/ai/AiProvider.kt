package com.example.ai

import com.example.core.model.AppConfig
import com.example.core.model.ChatMessage
import com.example.storage.PersonaEntity

interface AiProvider {
    suspend fun generateReply(
        contextMessages: List<ChatMessage>,
        incomingMessage: String,
        persona: PersonaEntity,
        config: AppConfig
    ): Result<String>

    suspend fun testConnection(): Result<String>

    suspend fun fetchAvailableModels(): Result<List<String>>
}
