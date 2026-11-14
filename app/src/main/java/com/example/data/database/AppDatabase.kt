package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Database(
    entities = [
        TaskEntity::class,
        SubjectEntity::class,
        SchedulePlanEntity::class,
        ScheduleSessionEntity::class,
        ExamCountdownEntity::class,
        PomodoroPresetEntity::class,
        PomodoroLogEntity::class,
        NoteEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
        FlashcardReviewHistoryEntity::class,
        DictionaryWordEntity::class,
        DocumentEntity::class,
        DocumentHighlightEntity::class,
        PrayerLogEntity::class,
        ClipboardHistoryEntity::class,
        VoiceNoteEntity::class,
        IdeaVaultEntity::class,
        QuestEntity::class,
        LevelHistoryEntity::class,
        SystemLogEntity::class,
        UserCollectionEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun pomodoroDao(): PomodoroDao
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun documentDao(): DocumentDao
    abstract fun prayerDao(): PrayerDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun ideaVaultDao(): IdeaVaultDao
    abstract fun questDao(): QuestDao
    abstract fun levelHistoryDao(): LevelHistoryDao
    abstract fun systemLogDao(): SystemLogDao
    abstract fun userCollectionDao(): UserCollectionDao
    abstract fun examDao(): ExamDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `targetWeeklyHours` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `schedule_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `stage` TEXT NOT NULL, `branch` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `schedule_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `subjectName` TEXT NOT NULL, `startHour` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `exam_countdowns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subjectName` TEXT NOT NULL, `examDate` INTEGER NOT NULL, `targetScore` INTEGER NOT NULL, `notes` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `pomodoro_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subjectName` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `frequency` TEXT NOT NULL, `targetCount` INTEGER NOT NULL, `streak` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `habit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, `count` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `flashcard_decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `flashcards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL DEFAULT 1, `easeFactor` REAL NOT NULL DEFAULT 2.5, `repetitionNumber` INTEGER NOT NULL DEFAULT 0, `nextReviewDateMillis` INTEGER NOT NULL DEFAULT 0, `isMastered` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new AI Language Learning Fields to flashcards
                database.execSQL("ALTER TABLE flashcards ADD COLUMN englishExample TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN arabicExample TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN contextStr TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN partOfSpeech TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN difficulty TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN pronunciation TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN phoneticTranscription TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN synonyms TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN antonyms TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN sourcePage TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN creationDateMillis INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN masteryPercentage INTEGER NOT NULL DEFAULT 0")

                // Create flashcard_review_history table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flashcard_review_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `flashcardId` INTEGER NOT NULL, 
                        `reviewDateMillis` INTEGER NOT NULL, 
                        `quality` INTEGER NOT NULL, 
                        `oldInterval` INTEGER NOT NULL, 
                        `newInterval` INTEGER NOT NULL, 
                        `oldEase` REAL NOT NULL, 
                        `newEase` REAL NOT NULL, 
                        `timeTakenMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN imageUri TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN imageOcclusionData TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN optionsJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN correctAnswerIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `idea_vault` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'عام',
                        `priority` TEXT NOT NULL DEFAULT 'متوسطة',
                        `status` TEXT NOT NULL DEFAULT 'جديدة',
                        `colorHex` TEXT NOT NULL DEFAULT '#3B82F6',
                        `tags` TEXT NOT NULL DEFAULT '',
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `createdAtMillis` INTEGER NOT NULL DEFAULT 0,
                        `updatedAtMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quests` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `category` TEXT NOT NULL DEFAULT 'STUDY',
                        `questType` TEXT NOT NULL DEFAULT 'NORMAL',
                        `difficulty` TEXT NOT NULL DEFAULT 'MEDIUM',
                        `xpReward` INTEGER NOT NULL DEFAULT 50,
                        `shieldReward` INTEGER NOT NULL DEFAULT 0,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `completionCount` INTEGER NOT NULL DEFAULT 0,
                        `currentStreak` INTEGER NOT NULL DEFAULT 0,
                        `bestStreak` INTEGER NOT NULL DEFAULT 0,
                        `masteryLevel` INTEGER NOT NULL DEFAULT 0,
                        `deadlineHour` INTEGER NOT NULL DEFAULT 23,
                        `deadlineMinute` INTEGER NOT NULL DEFAULT 59,
                        `isRecurring` INTEGER NOT NULL DEFAULT 1,
                        `recurrenceFrequency` TEXT NOT NULL DEFAULT 'DAILY',
                        `createdAtMillis` INTEGER NOT NULL DEFAULT 0,
                        `lastCompletedMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `level_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `oldLevel` INTEGER NOT NULL,
                        `newLevel` INTEGER NOT NULL,
                        `oldRank` TEXT NOT NULL,
                        `newRank` TEXT NOT NULL,
                        `xpAtLevelUp` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `unlockedTitle` TEXT NOT NULL DEFAULT '',
                        `unlockedFrame` TEXT NOT NULL DEFAULT '',
                        `timestampMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `system_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `xpGained` INTEGER NOT NULL DEFAULT 0,
                        `shieldsDelta` INTEGER NOT NULL DEFAULT 0,
                        `timestampMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `nameAr` TEXT NOT NULL,
                        `nameEn` TEXT NOT NULL,
                        `descriptionAr` TEXT NOT NULL,
                        `descriptionEn` TEXT NOT NULL,
                        `iconOrColor` TEXT NOT NULL,
                        `isUnlocked` INTEGER NOT NULL DEFAULT 0,
                        `isEquipped` INTEGER NOT NULL DEFAULT 0,
                        `requiredLevel` INTEGER NOT NULL DEFAULT 1,
                        `requiredRank` TEXT NOT NULL DEFAULT 'BRONZE',
                        `unlockedAtMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rafiq_al_talib.db"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val instance = getInstance(context)
                    seedInitialData(instance)
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val instance = getInstance(context)
                    seedInitialData(instance)
                }
            }

            private suspend fun seedInitialData(db: AppDatabase) {
                try {
                    // Pomodoro Presets
                    if (db.pomodoroDao().getAllPresets().firstOrNull()?.isEmpty() == true) {
                        db.pomodoroDao().insertPreset(
                            PomodoroPresetEntity(title = "تركيز عادي (25د / 5د)", focusMinutes = 25, focusSeconds = 0, shortBreakMinutes = 5, shortBreakSeconds = 0, longBreakMinutes = 15, longBreakSeconds = 0)
                        )
                        db.pomodoroDao().insertPreset(
                            PomodoroPresetEntity(title = "تركيز عميق (50د / 10د)", focusMinutes = 50, focusSeconds = 0, shortBreakMinutes = 10, shortBreakSeconds = 0, longBreakMinutes = 20, longBreakSeconds = 0)
                        )
                        db.pomodoroDao().insertPreset(
                            PomodoroPresetEntity(title = "جلسة مراجعة سريعة (15د / 3د)", focusMinutes = 15, focusSeconds = 0, shortBreakMinutes = 3, shortBreakSeconds = 0, longBreakMinutes = 10, longBreakSeconds = 0)
                        )
                    }

                    // Seed Dictionary
                    if (db.dictionaryDao().getWordCount() == 0) {
                        val dictionaryWords = com.example.domain.dictionary.DictionaryDataGenerator.generateAllWords()
                        db.dictionaryDao().insertWords(dictionaryWords)
                    }

                    // Seed Initial Gamified Quests if none exist
                    if (db.questDao().getQuestCount() == 0) {
                        val initialQuests = listOf(
                            QuestEntity(
                                title = "مذاكرة درس جديد بتركيز 📚",
                                description = "جلسة مذاكرة لمدة 45 دقيقة بدون تشتت مع تلخيص أهم النقاط",
                                category = "STUDY",
                                questType = "CORE",
                                difficulty = "MEDIUM",
                                xpReward = 100,
                                shieldReward = 1,
                                deadlineHour = 21,
                                deadlineMinute = 0
                            ),
                            QuestEntity(
                                title = "مراجعة بطاقات التكرار المتباعد 🧠",
                                description = "إكمال مراجعة جميع بطاقات اليوم لتثبيت المعلومات في الذاكرة طويلة المدى",
                                category = "MEMORIZATION",
                                questType = "MAIN",
                                difficulty = "MEDIUM",
                                xpReward = 80,
                                shieldReward = 0,
                                deadlineHour = 20,
                                deadlineMinute = 0
                            ),
                            QuestEntity(
                                title = "حل تدريبات وتمارين تطبيقية ✍️",
                                description = "حل 10 أسئلة على الأقل واختبار الفهم الحقيقي للدرس",
                                category = "STUDY",
                                questType = "IMPORTANT",
                                difficulty = "HARD",
                                xpReward = 120,
                                shieldReward = 1,
                                deadlineHour = 22,
                                deadlineMinute = 0
                            ),
                            QuestEntity(
                                title = "جلسة بومودورو مركزة ⏱️",
                                description = "إتمام جلسة تركيز كاملة عبر مؤقت بومودورو المدمج",
                                category = "FOCUS",
                                questType = "NORMAL",
                                difficulty = "EASY",
                                xpReward = 50,
                                shieldReward = 0,
                                deadlineHour = 22,
                                deadlineMinute = 30
                            ),
                            QuestEntity(
                                title = "قراءة ورد القرآن وأذكار الصباح والمساء 📖",
                                description = "المحافظة على الورد القرآني اليومي والأذكار المباركة",
                                category = "PRAYER",
                                questType = "CORE",
                                difficulty = "EASY",
                                xpReward = 75,
                                shieldReward = 0,
                                deadlineHour = 21,
                                deadlineMinute = 30
                            )
                        )
                        db.questDao().insertQuests(initialQuests)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
