package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "live_ai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for devices/tests where Keystore MasterKey may throw
        context.getSharedPreferences("live_ai_fallback_prefs", Context.MODE_PRIVATE)
    }

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1"
        set(value) = prefs.edit().putString(KEY_API_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() {
            val stored = prefs.getString(KEY_API_KEY, "") ?: ""
            return if (stored.startsWith("enc_b64:")) {
                try {
                    String(Base64.decode(stored.removePrefix("enc_b64:"), Base64.DEFAULT), StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    ""
                }
            } else {
                stored
            }
        }
        set(value) {
            val toSave = value.trim()
            prefs.edit().putString(KEY_API_KEY, toSave).apply()
        }

    var primaryModel: String
        get() = prefs.getString(KEY_PRIMARY_MODEL, "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash"
        set(value) = prefs.edit().putString(KEY_PRIMARY_MODEL, value.trim()).apply()

    var fallbackModels: List<String>
        get() {
            val raw = prefs.getString(KEY_FALLBACK_MODELS, "meta-llama/llama-3.3-70b-instruct,openai/gpt-4o-mini") ?: ""
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) {
            prefs.edit().putString(KEY_FALLBACK_MODELS, value.joinToString(",")).apply()
        }

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 350)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT_SECONDS, 20)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT_SECONDS, value).apply()

    var retryCount: Int
        get() = prefs.getInt(KEY_RETRY_COUNT, 2)
        set(value) = prefs.edit().putInt(KEY_RETRY_COUNT, value).apply()

    var customSystemPrompt: String
        get() = prefs.getString(KEY_CUSTOM_SYSTEM_PROMPT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_SYSTEM_PROMPT, value).apply()

    var isFirstRunCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, value).apply()

    fun getMaskedApiKey(): String {
        val key = apiKey
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    companion object {
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PRIMARY_MODEL = "primary_model"
        private const val KEY_FALLBACK_MODELS = "fallback_models"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_CUSTOM_SYSTEM_PROMPT = "custom_system_prompt"
        private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
    }
}
