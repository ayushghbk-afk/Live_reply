package com.example.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // Personas
    @Query("SELECT * FROM personas ORDER BY isSelected DESC, id ASC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPersona(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isSelected = 1 LIMIT 1")
    fun getSelectedPersonaFlow(): Flow<PersonaEntity?>

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun getPersonaCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersona(persona: PersonaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonas(personas: List<PersonaEntity>)

    @Update
    suspend fun updatePersona(persona: PersonaEntity)

    @Delete
    suspend fun deletePersona(persona: PersonaEntity)

    @Transaction
    suspend fun setSelectedPersona(personaId: Long) {
        clearSelectedPersona()
        markPersonaSelected(personaId)
    }

    @Query("UPDATE personas SET isSelected = 0")
    suspend fun clearSelectedPersona()

    @Query("UPDATE personas SET isSelected = 1 WHERE id = :personaId")
    suspend fun markPersonaSelected(personaId: Long)

    // Diagnostic Logs
    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<DiagnosticLogEntity>>

    @Insert
    suspend fun insertLog(log: DiagnosticLogEntity)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearLogs()

    // Supported Apps
    @Query("SELECT * FROM supported_apps ORDER BY displayName ASC")
    fun getAllSupportedApps(): Flow<List<SupportedAppEntity>>

    @Query("SELECT * FROM supported_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getSupportedApp(pkg: String): SupportedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupportedApp(app: SupportedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultSupportedApps(apps: List<SupportedAppEntity>)

    @Query("UPDATE supported_apps SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun updateAppEnabled(pkg: String, enabled: Boolean)

    @Delete
    suspend fun deleteSupportedApp(app: SupportedAppEntity)

    // Paused Conversations
    @Query("SELECT * FROM paused_conversations ORDER BY pausedAt DESC")
    fun getAllPausedConversations(): Flow<List<PausedConversationEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM paused_conversations WHERE conversationKey = :key)")
    suspend fun isConversationPaused(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPausedConversation(paused: PausedConversationEntity)

    @Query("DELETE FROM paused_conversations WHERE conversationKey = :key")
    suspend fun resumeConversation(key: String)

    // Conversation Memories
    @Query("SELECT * FROM conversation_memories ORDER BY lastInteractedAt DESC")
    fun getAllMemories(): Flow<List<ConversationMemoryEntity>>

    @Query("SELECT * FROM conversation_memories WHERE conversationKey = :key LIMIT 1")
    suspend fun getMemory(key: String): ConversationMemoryEntity?

    @Query("SELECT * FROM conversation_memories WHERE conversationKey = :key LIMIT 1")
    fun getMemoryFlow(key: String): Flow<ConversationMemoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: ConversationMemoryEntity)

    @Query("DELETE FROM conversation_memories WHERE conversationKey = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM conversation_memories")
    suspend fun clearAllMemories()
}
