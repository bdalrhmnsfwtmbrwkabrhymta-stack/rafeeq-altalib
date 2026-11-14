package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. Task Entity
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val subjectId: Long? = null,
    val dueDateMillis: Long = 0,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, URGENT
    val tag: String = "عام",
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: String = "NONE", // DAILY, WEEKLY, MONTHLY
    val createdAtMillis: Long = System.currentTimeMillis()
)

// 2. Subject Entity
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#3F51B5",
    val iconName: String = "Book",
    val targetWeeklyHours: Int = 10,
    val notes: String = ""
)

// 3. Schedule Session & Plan Entities
@Entity(tableName = "schedule_plans")
data class SchedulePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isDefault: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedule_sessions")
data class ScheduleSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 1,
    val subjectId: Long,
    val dayOfWeek: Int, // 1 = Sat, 2 = Sun, ... 7 = Fri
    val startTimeString: String, // e.g., "09:00"
    val endTimeString: String,   // e.g., "10:30"
    val roomOrLink: String = "",
    val note: String = ""
)

// 4. Exam Countdown Entity
@Entity(tableName = "exam_countdowns")
data class ExamCountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val examTitle: String,
    val examTimestampMillis: Long,
    val location: String = "",
    val totalTopics: Int = 10,
    val studiedTopics: Int = 0
)

// 5. Pomodoro Log & Preset Entities
@Entity(tableName = "pomodoro_presets")
data class PomodoroPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val focusSeconds: Int,
    val shortBreakMinutes: Int,
    val shortBreakSeconds: Int,
    val longBreakMinutes: Int,
    val longBreakSeconds: Int
)

@Entity(tableName = "pomodoro_logs")
data class PomodoroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val durationMinutes: Int,
    val sessionType: String = "FOCUS", // FOCUS, SHORT_BREAK, LONG_BREAK
    val timestampMillis: Long = System.currentTimeMillis()
)

// 6. Note Entity
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val subjectId: Long? = null,
    val isPinned: Boolean = false,
    val isChecklist: Boolean = false,
    val checklistJson: String = "", // JSON list of items if checklist
    val imageUri: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

// 7. Habit Entity
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetFrequencyPerWeek: Int = 7,
    val iconName: String = "CheckCircle",
    val streakCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

// 8. Habit Log Entity
@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dateString: String // YYYY-MM-DD
)

// 9. Flashcard Deck Entity
@Entity(tableName = "flashcard_decks")
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val parentDeckId: Long? = null,
    val subjectId: Long? = null,
    val cardCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

// 10. Flashcard Entity (Anki SM-2 spaced repetition + Adaptive Learning)
@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val cardType: String = "BASIC", // BASIC, REVERSED, CLOZE
    val question: String,
    val answer: String,
    val hint: String = "",
    val clozeText: String = "",
    // SM-2 fields
    val repetitionNumber: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Float = 2.5f,
    val state: String = "NEW", // NEW, LEARNING, REVIEW, MASTERED
    val nextReviewDateMillis: Long = System.currentTimeMillis(),
    
    // New AI Language Learning Fields
    val englishExample: String = "",
    val arabicExample: String = "",
    val contextStr: String = "",
    val partOfSpeech: String = "",
    val difficulty: String = "", 
    val pronunciation: String = "",
    val phoneticTranscription: String = "",
    val synonyms: String = "",
    val antonyms: String = "",
    val notes: String = "",
    val sourcePage: String = "",
    val creationDateMillis: Long = System.currentTimeMillis(),
    val lapses: Int = 0,
    val masteryPercentage: Int = 0,
    val imageUri: String = "",
    val imageOcclusionData: String = "",
    val optionsJson: String = "",
    val correctAnswerIndex: Int = 0
)

// 10.5 Flashcard Review History
@Entity(tableName = "flashcard_review_history")
data class FlashcardReviewHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flashcardId: Long,
    val reviewDateMillis: Long = System.currentTimeMillis(),
    val quality: Int, // 0 = Again, 1 = Hard, 2 = Good, 3 = Easy
    val oldInterval: Int,
    val newInterval: Int,
    val oldEase: Float,
    val newEase: Float,
    val timeTakenMillis: Long = 0
)


// 11. Dictionary Word Entity
@Entity(tableName = "dictionary_words")
data class DictionaryWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordEnglish: String,
    val wordArabic: String,
    val phonetic: String = "",
    val partOfSpeech: String = "", // noun, verb, adj
    val exampleSentence: String = "",
    val emoji: String = "",
    val category: String = "",
    val isFavorite: Boolean = false,
    val lastSearchedMillis: Long = System.currentTimeMillis()
)

// 12. Document Entity (PDF / Image OCR / AI Books)
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uriString: String = "",
    val fileType: String = "PDF", // PDF, IMAGE, BOOK, TEXT
    val extractedText: String = "",
    val pageCount: Int = 1,
    val subjectId: Long? = null,
    val processedJson: String = "",
    val importantPointsJson: String = "",
    val flashcardDeckId: Long? = null,
    val addedAtMillis: Long = System.currentTimeMillis()
)

// 13. Document Highlight Entity
@Entity(tableName = "document_highlights")
data class DocumentHighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val highlightedText: String,
    val note: String = "",
    val colorHex: String = "#FFEB3B"
)

// 14. Prayer Log Entity
@Entity(tableName = "prayer_logs")
data class PrayerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String, // YYYY-MM-DD
    val fajrCompleted: Boolean = false,
    val dhuhrCompleted: Boolean = false,
    val asrCompleted: Boolean = false,
    val maghribCompleted: Boolean = false,
    val ishaCompleted: Boolean = false,
    val quranPagesRead: Int = 0,
    val tasbeehCount: Int = 0
)

// 15. Clipboard History Entity (Best Copy)
@Entity(tableName = "clipboard_history")
data class ClipboardHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val sourceTitle: String = "نسخ عام",
    val isFavorite: Boolean = false,
    val copiedAtMillis: Long = System.currentTimeMillis()
)

// 16. Study Voice Note Entity (Speech-to-Text for Study Topics)
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val transcribedText: String,
    val subjectId: Long? = null,
    val subjectName: String = "عام",
    val durationSeconds: Int = 0,
    val isPinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

// 17. Idea Vault Entity (مخزن الأفكار)
@Entity(tableName = "idea_vault")
data class IdeaVaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String = "عام", // مشروع, بحث, فكرة دراسية, ابتكار, حل مسألة, كتابة, عام
    val priority: String = "متوسطة", // عاجلة, عالية, متوسطة, منخفضة
    val status: String = "جديدة", // جديدة, قيد التنفيذ, مكتملة, مؤجلة
    val colorHex: String = "#3B82F6", // Default Blue
    val tags: String = "", // Comma-separated tags
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

// 18. Quest Entity (نظام المهام والمغامرة الدراسية)
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "STUDY", // STUDY, MEMORIZATION, FOCUS, HABIT, PRAYER, GENERAL
    val questType: String = "NORMAL", // NORMAL, IMPORTANT, CORE, MAIN
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD, EPIC
    val xpReward: Int = 50,
    val shieldReward: Int = 0,
    val isCompleted: Boolean = false,
    val completionCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val masteryLevel: Int = 0, // 0 to 5 stars
    val deadlineHour: Int = 23,
    val deadlineMinute: Int = 59,
    val isRecurring: Boolean = true,
    val recurrenceFrequency: String = "DAILY", // DAILY, WEEKDAYS, WEEKLY
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastCompletedMillis: Long = 0L
)

// 19. Level & Rank Progression History Entity
@Entity(tableName = "level_history")
data class LevelHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val oldLevel: Int,
    val newLevel: Int,
    val oldRank: String,
    val newRank: String,
    val xpAtLevelUp: Int,
    val reason: String,
    val unlockedTitle: String = "",
    val unlockedFrame: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

// 20. System Activity & Gamification Log Entity (سجل الأحداث)
@Entity(tableName = "system_logs")
data class SystemLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // QUEST_CREATED, QUEST_COMPLETED, LEVEL_UP, RANK_UP, STREAK_MILESTONE, STREAK_RISK, SHIELD_EARNED, SHIELD_USED, STREAK_RECOVERED, STREAK_LOST, SETTINGS_CHANGED, SYSTEM_RESET
    val title: String,
    val details: String,
    val xpGained: Int = 0,
    val shieldsDelta: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
)

// 21. User Collection Item Entity (Frames, Titles, Badges, Avatars)
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: String, // TITLE, FRAME, BADGE, AVATAR
    val itemId: String, // e.g. "title_scholar", "frame_cosmic", "badge_streak_30"
    val nameAr: String,
    val nameEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val iconOrColor: String,
    val isUnlocked: Boolean = false,
    val isEquipped: Boolean = false,
    val requiredLevel: Int = 1,
    val requiredRank: String = "BRONZE",
    val unlockedAtMillis: Long = 0L
)


