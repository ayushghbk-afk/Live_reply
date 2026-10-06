package com.example.ai

import com.example.core.model.AppConfig
import com.example.core.model.ChatMessage
import com.example.core.security.SecureStorage
import com.example.storage.AppRepository
import com.example.storage.PersonaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    private val secureStorage: SecureStorage,
    private val repository: AppRepository
) : AiProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(secureStorage.timeoutSeconds.toLong().coerceAtLeast(10), TimeUnit.SECONDS)
            .readTimeout(secureStorage.timeoutSeconds.toLong().coerceAtLeast(15), TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun generateReply(
        contextMessages: List<ChatMessage>,
        incomingMessage: String,
        persona: PersonaEntity,
        config: AppConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.apiKey.trim()
        if (apiKey.isBlank()) {
            val err = "API Key not configured. Please add your OpenRouter or OpenAI API key in AI Settings."
            repository.log("AI", err, "ERROR")
            return@withContext Result.failure(IllegalStateException(err))
        }

        val baseUrl = secureStorage.apiBaseUrl.trim().removeSuffix("/")
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val modelList = buildList {
            add(secureStorage.primaryModel.trim())
            addAll(secureStorage.fallbackModels.map { it.trim() }.filter { it.isNotEmpty() && it != secureStorage.primaryModel.trim() })
        }

        val systemPrompt = buildSystemPrompt(persona, config)

        var lastError: Throwable? = null

        // Try primary model first, followed by fallbacks on transient/rate-limit failure
        for ((index, model) in modelList.withIndex()) {
            try {
                repository.log("AI", "Querying model '$model' (attempt ${index + 1}/${modelList.size})...", "INFO")

                val payload = buildRequestPayload(model, systemPrompt, contextMessages, incomingMessage, persona, config)
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://liveaireply.aistudio.app")
                    .addHeader("X-Title", "Live AI Reply")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                val responseCode = response.code

                if (response.isSuccessful) {
                    val rawReply = extractReplyText(responseBody)
                    val cleaned = AiResponseValidator.cleanResponse(rawReply)

                    when (val valResult = AiResponseValidator.validate(cleaned, persona.maxCharacters)) {
                        is AiResponseValidator.ValidationResult.Valid -> {
                            repository.log("AI", "Received response from '$model' (${cleaned.length} chars)", "SUCCESS")
                            return@withContext Result.success(cleaned)
                        }
                        is AiResponseValidator.ValidationResult.Invalid -> {
                            val err = "Validation failed for model '$model': ${valResult.reason}"
                            repository.log("AI", err, "WARN")
                            lastError = IllegalStateException(err)
                            // continue to fallback
                        }
                    }
                } else {
                    val errorDetail = parseErrorMessage(responseBody, responseCode)
                    repository.log("AI", "Model '$model' failed [HTTP $responseCode]: $errorDetail", "WARN")

                    // Non-fallback errors: 401 unauthorized shouldn't churn fallbacks
                    if (responseCode == 401 || responseCode == 403) {
                        return@withContext Result.failure(
                            IllegalStateException("Authentication error (HTTP $responseCode): Check your API key.")
                        )
                    }

                    lastError = IOException("HTTP $responseCode from $model: $errorDetail")
                }
            } catch (e: Exception) {
                repository.log("AI", "Network/API error for model '$model': ${e.localizedMessage}", "WARN")
                lastError = e
            }
        }

        val finalError = lastError ?: IOException("All configured AI models failed to generate a reply.")
        repository.log("AI", "All models exhausted: ${finalError.localizedMessage}", "ERROR")
        Result.failure(finalError)
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.apiKey.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key is blank. Please enter an API key."))
        }

        val baseUrl = secureStorage.apiBaseUrl.trim().removeSuffix("/")
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val model = secureStorage.primaryModel.trim()

        try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Reply with only the word 'Connected' if you receive this.")
                })
            }
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 10)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://liveaireply.aistudio.app")
                .addHeader("X-Title", "Live AI Reply")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val reply = extractReplyText(body)
                Result.success("Success! Model '$model' responded: '$reply'")
            } else {
                val errMsg = parseErrorMessage(body, response.code)
                Result.failure(IOException("HTTP ${response.code}: $errMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.apiKey.trim()
        val baseUrl = secureStorage.apiBaseUrl.trim().removeSuffix("/")
        // If baseUrl ends in /chat/completions, trim back to root
        val rootUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl.substringBeforeLast("/chat/completions")
        } else baseUrl

        val modelsUrl = "$rootUrl/models"

        try {
            val reqBuilder = Request.Builder().url(modelsUrl)
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            reqBuilder.addHeader("HTTP-Referer", "https://liveaireply.aistudio.app")
            reqBuilder.addHeader("X-Title", "Live AI Reply")

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                val resultList = mutableListOf<String>()
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("id")
                        if (id.isNotBlank()) {
                            resultList.add(id)
                        }
                    }
                }
                if (resultList.isEmpty()) {
                    Result.success(getDefaultPopularModels())
                } else {
                    Result.success(resultList.sorted())
                }
            } else {
                Result.success(getDefaultPopularModels())
            }
        } catch (e: Exception) {
            Result.success(getDefaultPopularModels())
        }
    }

    private fun buildSystemPrompt(persona: PersonaEntity, config: AppConfig): String {
        val customGlobal = secureStorage.customSystemPrompt.trim()
        val languageInstruction = if (config.translationModeEnabled && config.targetLanguage != "Auto") {
            "Translate and reply in ${config.targetLanguage}."
        } else if (config.autoLanguage) {
            "Match the language, dialect, and texting slang of the incoming message naturally (e.g., Hinglish if user speaks Hinglish, Spanish if Spanish, etc.)."
        } else {
            "Reply strictly in ${config.targetLanguage}."
        }

        return buildString {
            append("You are a real-time conversation reply assistant acting as the user in a live chat. ")
            append("Generate the most natural, human, and contextually aware response to the newest incoming message.\n")
            append("CRITICAL INSTRUCTIONS:\n")
            append("- Never mention that you are an AI, a language model, or a bot.\n")
            append("- Never write prefixes like 'Me:', 'Reply:', or 'Assistant:'.\n")
            append("- Do not describe reasoning, explain prompts, or include meta-commentary.\n")
            append("- Do not repeat the other person's question verbatim.\n")
            append("- Maintain a natural texting rhythm and human tone.\n")
            append("- $languageInstruction\n")
            append("- Reply length guideline: ${persona.replyLength.instruction} (Maximum ${persona.maxCharacters} characters).\n\n")

            append("PERSONA DETAILS:\n")
            append("Name: ${persona.name}\n")
            append("Personality: ${persona.personalityDescription}\n")
            if (persona.background.isNotBlank()) append("Background: ${persona.background}\n")
            if (persona.relationship.isNotBlank()) append("Relationship: ${persona.relationship}\n")
            if (persona.speakingStyle.isNotBlank()) append("Speaking Style: ${persona.speakingStyle}\n")
            if (persona.rules.isNotBlank()) append("Persona Rules: ${persona.rules}\n")

            if (customGlobal.isNotBlank()) {
                append("\nUSER ADDITIONAL PREFERENCES:\n$customGlobal\n")
            }
        }
    }

    private fun buildRequestPayload(
        model: String,
        systemPrompt: String,
        contextMessages: List<ChatMessage>,
        incomingMessage: String,
        persona: PersonaEntity,
        config: AppConfig
    ): JSONObject {
        val messages = JSONArray()

        // 1. System prompt
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // 2. Conversation context (trimmed to user's config limit)
        val limit = config.contextMessageCount.coerceIn(5, 50)
        val recentHistory = contextMessages.takeLast(limit)

        for (msg in recentHistory) {
            val role = if (msg.isIncoming) "user" else "assistant"
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", msg.text)
            })
        }

        // 3. If latest incoming message is not already last in history, append it
        val lastMsgText = recentHistory.lastOrNull()?.text?.trim()
        if (lastMsgText != incomingMessage.trim()) {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", incomingMessage)
            })
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", secureStorage.temperature)
            put("max_tokens", secureStorage.maxTokens)
        }
    }

    private fun extractReplyText(responseBody: String): String {
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            if (message != null) {
                return message.optString("content", "")
            }
            val text = firstChoice.optString("text", "")
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                errorObj.optString("message", "Error code $code")
            } else {
                json.optString("message", "HTTP $code")
            }
        } catch (e: Exception) {
            "HTTP $code error occurred."
        }
    }

    private fun getDefaultPopularModels(): List<String> = listOf(
        "google/gemini-2.5-flash",
        "google/gemini-2.5-pro",
        "meta-llama/llama-3.3-70b-instruct",
        "openai/gpt-4o-mini",
        "openai/gpt-4o",
        "anthropic/claude-3.5-haiku",
        "mistralai/mistral-small-3"
    )
}
