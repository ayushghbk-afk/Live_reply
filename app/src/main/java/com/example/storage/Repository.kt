package com.example.storage

import android.content.Context
import com.example.core.model.AppConfig
import com.example.core.model.OperatingMode
import com.example.core.model.ReplyLength
import com.example.core.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppRepository(
    private val context: Context,
    private val appDao: AppDao,
    val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _appConfig = MutableStateFlow(loadConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    init {
        // Ensure defaults are populated if DB is fresh
        scope.launch {
            val selected = appDao.getSelectedPersona()
            if (selected == null) {
                AppDatabase.seedDefaultData(appDao)
            }
        }
    }

    // Personas
    val personas: Flow<List<PersonaEntity>> = appDao.getAllPersonas()
    val selectedPersonaFlow: Flow<PersonaEntity?> = appDao.getSelectedPersonaFlow()

    suspend fun getSelectedPersona(): PersonaEntity? = appDao.getSelectedPersona()

    suspend fun selectPersona(id: Long) {
        appDao.setSelectedPersona(id)
        log("Persona", "Switched active persona to ID $id", "INFO")
    }

    suspend fun savePersona(persona: PersonaEntity): Long {
        val id = appDao.insertPersona(persona)
        log("Persona", "Saved persona '${persona.name}'", "INFO")
        return id
    }

    suspend fun deletePersona(persona: PersonaEntity) {
        appDao.deletePersona(persona)
        log("Persona", "Deleted persona '${persona.name}'", "INFO")
    }

    // Supported Apps
    val supportedApps: Flow<List<SupportedAppEntity>> = appDao.getAllSupportedApps()

    suspend fun isAppEnabled(packageName: String): Boolean {
        val app = appDao.getSupportedApp(packageName)
        return app?.isEnabled ?: false
    }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        appDao.updateAppEnabled(packageName, enabled)
        log("Apps", "Updated $packageName enabled: $enabled", "INFO")
    }

    suspend fun addCustomApp(packageName: String, displayName: String) {
        appDao.insertSupportedApp(
            SupportedAppEntity(
                packageName = packageName,
                displayName = displayName,
                isEnabled = true,
                isBuiltIn = false
            )
        )
        log("Apps", "Added custom supported app $displayName ($packageName)", "INFO")
    }

    suspend fun removeCustomApp(app: SupportedAppEntity) {
        appDao.deleteSupportedApp(app)
    }

    // Paused conversations
    val pausedConversations: Flow<List<PausedConversationEntity>> = appDao.getAllPausedConversations()

    suspend fun isConversationPaused(packageName: String, chatTitle: String): Boolean {
        val key = "$packageName:$chatTitle"
        return appDao.isConversationPaused(key)
    }

    suspend fun pauseConversation(packageName: String, chatTitle: String) {
        val key = "$packageName:$chatTitle"
        appDao.insertPausedConversation(
            PausedConversationEntity(
                conversationKey = key,
                packageName = packageName,
                chatTitle = chatTitle
            )
        )
        log("Privacy", "Paused conversation '$chatTitle' in $packageName", "WARN")
    }

    suspend fun resumeConversation(key: String) {
        appDao.resumeConversation(key)
        log("Privacy", "Resumed conversation key $key", "INFO")
    }

    // Conversation Memories
    val memories: Flow<List<ConversationMemoryEntity>> = appDao.getAllMemories()

    suspend fun getMemory(key: String): ConversationMemoryEntity? = appDao.getMemory(key)

    fun getMemoryFlow(key: String): Flow<ConversationMemoryEntity?> = appDao.getMemoryFlow(key)

    suspend fun saveMemory(memory: ConversationMemoryEntity) {
        appDao.insertOrUpdateMemory(memory)
        log("Memory", "Saved persistent memory for '${memory.contactName}'", "SUCCESS")
    }

    suspend fun deleteMemory(key: String) {
        appDao.deleteMemory(key)
        log("Memory", "Removed memory for '$key'", "INFO")
    }

    suspend fun clearAllMemories() {
        appDao.clearAllMemories()
        log("Memory", "Cleared all conversation memories", "WARN")
    }

    // Logs
    val recentLogs: Flow<List<DiagnosticLogEntity>> = appDao.getRecentLogs()

    fun log(tag: String, message: String, level: String = "INFO") {
        scope.launch {
            // Safety scrub: never log API keys or obvious secrets
            val scrubbed = scrubSensitiveInfo(message)
            appDao.insertLog(
                DiagnosticLogEntity(
                    timestamp = System.currentTimeMillis(),
                    tag = tag,
                    message = scrubbed,
                    level = level
                )
            )
        }
    }

    suspend fun clearLogs() {
        appDao.clearLogs()
    }

    // Config updating
    fun updateOperatingMode(mode: OperatingMode) {
        _appConfig.update { it.copy(operatingMode = mode) }
        saveConfigValue("operating_mode", mode.name)
        log("Config", "Operating mode set to ${mode.displayName}", "INFO")
    }

    fun updateCaptureRateMode(mode: com.example.core.model.CaptureRateMode) {
        _appConfig.update { it.copy(captureRateMode = mode) }
        saveConfigValue("capture_rate_mode", mode.name)
        log("Config", "Screen capture rate set to ${mode.title} (${mode.intervalMs}ms)", "INFO")
    }

    fun updateCaptureResolution(mode: com.example.core.model.CaptureResolutionMode) {
        _appConfig.update { it.copy(captureResolution = mode) }
        saveConfigValue("capture_resolution", mode.name)
        log("Config", "Screen capture resolution set to ${mode.title}", "INFO")
    }

    fun updateSmartFrameDiffing(enabled: Boolean) {
        _appConfig.update { it.copy(smartFrameDiffing = enabled) }
        saveConfigValue("smart_frame_diffing", enabled)
        log("Config", "Smart frame change detection set to $enabled", "INFO")
    }

    fun updateSimulateTyping(enabled: Boolean) {
        _appConfig.update { it.copy(simulateTyping = enabled) }
        saveConfigValue("simulate_typing", enabled)
        log("Config", "Simulate human typing set to $enabled", "INFO")
    }

    fun updateMonitoringActive(active: Boolean) {
        _appConfig.update { it.copy(isMonitoringActive = active) }
        saveConfigValue("monitoring_active", active)
        log("Config", "Monitoring set to $active", if (active) "SUCCESS" else "WARN")
    }

    fun emergencyStopAll() {
        _appConfig.update {
            it.copy(
                isMonitoringActive = false,
                isPaused = true
            )
        }
        saveConfigValue("monitoring_active", false)
        saveConfigValue("is_paused", true)
        log("EMERGENCY", "EMERGENCY STOP TRIGGERED: All AI monitoring & automation paused", "ERROR")
    }

    fun resumeAll() {
        _appConfig.update {
            it.copy(
                isMonitoringActive = true,
                isPaused = false
            )
        }
        saveConfigValue("monitoring_active", true)
        saveConfigValue("is_paused", false)
        log("EMERGENCY", "Monitoring and automation resumed", "SUCCESS")
    }

    fun updateConfig(newConfig: AppConfig) {
        _appConfig.value = newConfig
        saveFullConfig(newConfig)
    }

    private fun scrubSensitiveInfo(text: String): String {
        var result = text
        val key = secureStorage.apiKey
        if (key.isNotBlank()) {
            result = result.replace(key, "[PROTECTED_API_KEY]")
        }
        // Scrub bearer tokens
        result = result.replace(Regex("Bearer\\s+[A-Za-z0-9_\\-\\.]+"), "Bearer [PROTECTED_TOKEN]")
        // Scrub 6-digit 2FA codes
        result = result.replace(Regex("\\b\\d{6}\\b"), "[AUTH_CODE]")
        return result
    }

    private fun loadConfig(): AppConfig {
        val sp = context.getSharedPreferences("app_config_prefs", Context.MODE_PRIVATE)
        val modeStr = sp.getString("operating_mode", OperatingMode.SUGGEST.name) ?: OperatingMode.SUGGEST.name
        val mode = try { OperatingMode.valueOf(modeStr) } catch (e: Exception) { OperatingMode.SUGGEST }
        val replyLengthStr = sp.getString("reply_length", ReplyLength.NORMAL.name) ?: ReplyLength.NORMAL.name
        val length = try { ReplyLength.valueOf(replyLengthStr) } catch (e: Exception) { ReplyLength.NORMAL }

        val rateStr = sp.getString("capture_rate_mode", com.example.core.model.CaptureRateMode.BALANCED.name) ?: com.example.core.model.CaptureRateMode.BALANCED.name
        val rate = try { com.example.core.model.CaptureRateMode.valueOf(rateStr) } catch (e: Exception) { com.example.core.model.CaptureRateMode.BALANCED }
        val resStr = sp.getString("capture_resolution", com.example.core.model.CaptureResolutionMode.LOW_RESOURCE.name) ?: com.example.core.model.CaptureResolutionMode.LOW_RESOURCE.name
        val res = try { com.example.core.model.CaptureResolutionMode.valueOf(resStr) } catch (e: Exception) { com.example.core.model.CaptureResolutionMode.LOW_RESOURCE }

        return AppConfig(
            operatingMode = mode,
            debounceDelayMs = sp.getLong("debounce_delay_ms", 800L),
            contextMessageCount = sp.getInt("context_count", 10),
            replyDelaySeconds = sp.getInt("reply_delay_sec", 3),
            simulateTyping = sp.getBoolean("simulate_typing", false),
            typingSpeedCpm = sp.getInt("typing_speed_cpm", 300),
            maxReplyCharacters = sp.getInt("max_reply_chars", 400),
            replyLength = length,
            autoLanguage = sp.getBoolean("auto_language", true),
            targetLanguage = sp.getString("target_language", "Auto") ?: "Auto",
            translationModeEnabled = sp.getBoolean("translation_mode", false),
            ocrFallbackEnabled = sp.getBoolean("ocr_fallback", true),
            autoSeeOnScreen = sp.getBoolean("auto_see_on_screen", true),
            captureRateMode = rate,
            captureResolution = res,
            smartFrameDiffing = sp.getBoolean("smart_frame_diffing", true),
            overlayScale = sp.getFloat("overlay_scale", 1.0f),
            overlayOpacity = sp.getFloat("overlay_opacity", 0.95f),
            autoShowOverlay = sp.getBoolean("auto_show_overlay", true),
            debugMode = sp.getBoolean("debug_mode", false),
            isPaused = sp.getBoolean("is_paused", false),
            isMonitoringActive = sp.getBoolean("monitoring_active", true)
        )
    }

    private fun saveConfigValue(key: String, value: Any) {
        val sp = context.getSharedPreferences("app_config_prefs", Context.MODE_PRIVATE)
        with(sp.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
            }
            apply()
        }
    }

    private fun saveFullConfig(c: AppConfig) {
        val sp = context.getSharedPreferences("app_config_prefs", Context.MODE_PRIVATE)
        sp.edit()
            .putString("operating_mode", c.operatingMode.name)
            .putLong("debounce_delay_ms", c.debounceDelayMs)
            .putInt("context_count", c.contextMessageCount)
            .putInt("reply_delay_sec", c.replyDelaySeconds)
            .putBoolean("simulate_typing", c.simulateTyping)
            .putInt("typing_speed_cpm", c.typingSpeedCpm)
            .putInt("max_reply_chars", c.maxReplyCharacters)
            .putString("reply_length", c.replyLength.name)
            .putBoolean("auto_language", c.autoLanguage)
            .putString("target_language", c.targetLanguage)
            .putBoolean("translation_mode", c.translationModeEnabled)
            .putBoolean("ocr_fallback", c.ocrFallbackEnabled)
            .putBoolean("auto_see_on_screen", c.autoSeeOnScreen)
            .putString("capture_rate_mode", c.captureRateMode.name)
            .putString("capture_resolution", c.captureResolution.name)
            .putBoolean("smart_frame_diffing", c.smartFrameDiffing)
            .putFloat("overlay_scale", c.overlayScale)
            .putFloat("overlay_opacity", c.overlayOpacity)
            .putBoolean("auto_show_overlay", c.autoShowOverlay)
            .putBoolean("debug_mode", c.debugMode)
            .putBoolean("is_paused", c.isPaused)
            .putBoolean("monitoring_active", c.isMonitoringActive)
            .apply()
    }
}
