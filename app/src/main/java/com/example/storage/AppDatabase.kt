package com.example.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.model.PersonalityType
import com.example.core.model.ReplyLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PersonaEntity::class,
        DiagnosticLogEntity::class,
        SupportedAppEntity::class,
        PausedConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "live_ai_reply.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default personas and supported apps
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).appDao()
                            seedDefaultData(dao)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultData(dao: AppDao) {
            val defaultPersonas = listOf(
                PersonaEntity(
                    name = "Natural Friendly (Default)",
                    personalityType = PersonalityType.FRIENDLY,
                    personalityDescription = "Warm, enthusiastic, relatable, emotionally intelligent, quick with helpful and kind answers.",
                    speakingStyle = "Casual natural texting style with occasional gentle emojis. Clear and concise.",
                    rules = "Never mention AI. Keep it genuine and human. Do not over-explain.",
                    replyLength = ReplyLength.NORMAL,
                    maxCharacters = 350,
                    isSelected = true,
                    isBuiltIn = true
                ),
                PersonaEntity(
                    name = "Casual & Snappy",
                    personalityType = PersonalityType.CASUAL,
                    personalityDescription = "Laid-back, relaxed, uses lowercase or modern texting conventions, chill vibe.",
                    speakingStyle = "Short conversational replies, quick banter, relaxed phrasing.",
                    rules = "Never sound like an essay or customer service bot. Never say 'As an AI'.",
                    replyLength = ReplyLength.SHORT,
                    maxCharacters = 180,
                    isSelected = false,
                    isBuiltIn = true
                ),
                PersonaEntity(
                    name = "Witty & Funny",
                    personalityType = PersonalityType.FUNNY,
                    personalityDescription = "Playful banter, clever humor, quick-witted comebacks without being rude.",
                    speakingStyle = "Humorous, entertaining, natural conversational wit.",
                    rules = "Keep jokes light and relevant to context. Don't force puns.",
                    replyLength = ReplyLength.NORMAL,
                    maxCharacters = 280,
                    isSelected = false,
                    isBuiltIn = true
                ),
                PersonaEntity(
                    name = "Flirty & Charming",
                    personalityType = PersonalityType.FLIRTY,
                    personalityDescription = "Charming, subtly teasing, confident, magnetic, attentive.",
                    speakingStyle = "Playful, warm, subtle charisma, teasing without being pushy.",
                    rules = "Stay tasteful, charming, and responsive to the other person's energy.",
                    replyLength = ReplyLength.SHORT,
                    maxCharacters = 220,
                    isSelected = false,
                    isBuiltIn = true
                ),
                PersonaEntity(
                    name = "Professional & Crisp",
                    personalityType = PersonalityType.PROFESSIONAL,
                    personalityDescription = "Polite, articulate, constructive, business-friendly, punctual.",
                    speakingStyle = "Clear, professional, polite, grammatically correct and direct.",
                    rules = "No slang or unprofessional jargon. Keep solutions clear and action-oriented.",
                    replyLength = ReplyLength.NORMAL,
                    maxCharacters = 400,
                    isSelected = false,
                    isBuiltIn = true
                ),
                PersonaEntity(
                    name = "Kaelen (Roleplay)",
                    personalityType = PersonalityType.ROLEPLAY,
                    personalityDescription = "Quiet, mysterious, emotionally guarded, deeply perceptive, brooding.",
                    background = "A rogue wanderer who keeps his distance but cares deeply under the surface.",
                    relationship = "Slow-burn romance with a sharp protective instinct.",
                    speakingStyle = "Short poetic dialogue, nuanced cadence, pregnant pauses.",
                    rules = "Never break character. Do not explain the roleplay. Respond naturally.",
                    replyLength = ReplyLength.SHORT,
                    maxCharacters = 250,
                    isSelected = false,
                    isBuiltIn = true
                )
            )
            dao.insertPersonas(defaultPersonas)

            val defaultApps = listOf(
                SupportedAppEntity("com.whatsapp", "WhatsApp", isEnabled = true, isBuiltIn = true),
                SupportedAppEntity("org.telegram.messenger", "Telegram", isEnabled = true, isBuiltIn = true),
                SupportedAppEntity("com.instagram.android", "Instagram", isEnabled = true, isBuiltIn = true),
                SupportedAppEntity("com.discord", "Discord", isEnabled = true, isBuiltIn = true),
                SupportedAppEntity("ai.character.app", "Character.AI", isEnabled = true, isBuiltIn = true),
                SupportedAppEntity("com.android.chrome", "Chrome Browser Chat", isEnabled = true, isBuiltIn = true)
            )
            dao.insertDefaultSupportedApps(defaultApps)
        }
    }
}
