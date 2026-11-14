package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. TaskDao
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY isCompleted ASC, dueDateMillis ASC")
    fun getAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isArchived = 0")
    suspend fun getAllActiveTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isArchived = 1 ORDER BY createdAtMillis DESC")
    fun getArchivedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    fun getCompletedTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isArchived = 0")
    fun getTotalActiveTasksCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean)
}

// 2. SubjectDao
@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    suspend fun getAllSubjectsList(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)
}

// 3. ScheduleDao
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_plans ORDER BY createdAtMillis DESC")
    fun getAllPlans(): Flow<List<SchedulePlanEntity>>

    @Query("SELECT * FROM schedule_sessions WHERE planId = :planId ORDER BY dayOfWeek ASC, startTimeString ASC")
    fun getSessionsForPlan(planId: Long): Flow<List<ScheduleSessionEntity>>

    @Query("SELECT * FROM schedule_sessions WHERE planId = :planId")
    suspend fun getSessionsForPlanList(planId: Long): List<ScheduleSessionEntity>

    @Query("SELECT * FROM schedule_sessions ORDER BY dayOfWeek ASC, startTimeString ASC")
    fun getAllSessions(): Flow<List<ScheduleSessionEntity>>

    @Query("SELECT * FROM schedule_sessions")
    suspend fun getAllSessionsList(): List<ScheduleSessionEntity>

    @Query("SELECT * FROM schedule_sessions WHERE planId = :planId AND dayOfWeek = :dayOfWeek ORDER BY startTimeString ASC")
    fun getSessionsForDay(planId: Long, dayOfWeek: Int): Flow<List<ScheduleSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SchedulePlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScheduleSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ScheduleSessionEntity>)

    @Update
    suspend fun updateSession(session: ScheduleSessionEntity)

    @Delete
    suspend fun deleteSession(session: ScheduleSessionEntity)

    @Query("DELETE FROM schedule_sessions WHERE planId = :planId")
    suspend fun deleteSessionsForPlan(planId: Long)

    @Query("DELETE FROM schedule_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("SELECT * FROM exam_countdowns ORDER BY examTimestampMillis ASC")
    fun getAllExams(): Flow<List<ExamCountdownEntity>>

    @Query("SELECT * FROM exam_countdowns")
    suspend fun getAllExamsList(): List<ExamCountdownEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamCountdownEntity): Long

    @Delete
    suspend fun deleteExam(exam: ExamCountdownEntity)
}

// 4. PomodoroDao
@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_presets ORDER BY id ASC")
    fun getAllPresets(): Flow<List<PomodoroPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PomodoroPresetEntity): Long

    @Query("SELECT * FROM pomodoro_logs ORDER BY timestampMillis DESC")
    fun getAllLogs(): Flow<List<PomodoroLogEntity>>

    @Query("SELECT * FROM pomodoro_logs ORDER BY timestampMillis DESC")
    suspend fun getAllLogsList(): List<PomodoroLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PomodoroLogEntity): Long

    @Query("SELECT SUM(durationMinutes) FROM pomodoro_logs WHERE timestampMillis >= :startOfDayMillis")
    fun getTodayFocusMinutes(startOfDayMillis: Long): Flow<Int?>
}

// 5. NoteDao
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAtMillis DESC")
    suspend fun getAllNotesList(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}

// 6. HabitDao
@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAtMillis ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsList(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE dateString = :date")
    fun getLogsForDate(date: String): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitLog(log: HabitLogEntity): Long

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateString = :date")
    suspend fun deleteHabitLog(habitId: Long, date: String)
}

// 7. FlashcardDao
@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcard_decks ORDER BY createdAtMillis DESC")
    fun getAllDecks(): Flow<List<FlashcardDeckEntity>>

    @Query("SELECT * FROM flashcards")
    fun getAllCardsFlow(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE nextReviewDateMillis <= :nowMillis")
    fun getDueCardsFlow(nowMillis: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY nextReviewDateMillis ASC")
    fun getCardsForDeck(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY nextReviewDateMillis ASC")
    fun getCardsForDeckFlow(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND nextReviewDateMillis <= :nowMillis")
    fun getDueCardsForDeck(deckId: Long, nowMillis: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards")
    suspend fun getAllCardsList(): List<FlashcardEntity>

    @Query("SELECT * FROM flashcard_decks")
    suspend fun getAllDecksList(): List<FlashcardDeckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: FlashcardDeckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<FlashcardEntity>)

    @Update
    suspend fun updateCard(card: FlashcardEntity)

    @Delete
    suspend fun deleteCard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteCardsInDeck(deckId: Long)

    @Delete
    suspend fun deleteDeck(deck: FlashcardDeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewHistory(history: FlashcardReviewHistoryEntity): Long
}

// 8. DictionaryDao
@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_words ORDER BY wordEnglish ASC")
    fun getAllWords(): Flow<List<DictionaryWordEntity>>

    @Query("SELECT * FROM dictionary_words WHERE wordEnglish LIKE :query || '%' OR wordArabic LIKE :query || '%' LIMIT 100")
    fun searchWords(query: String): Flow<List<DictionaryWordEntity>>

    @Query("SELECT * FROM dictionary_words WHERE isFavorite = 1 ORDER BY wordEnglish ASC")
    fun getFavoriteWords(): Flow<List<DictionaryWordEntity>>

    @Query("SELECT COUNT(*) FROM dictionary_words")
    suspend fun getWordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: DictionaryWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<DictionaryWordEntity>)

    @Update
    suspend fun updateWord(word: DictionaryWordEntity)
}

// 9. DocumentDao
@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY addedAtMillis DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM document_highlights WHERE documentId = :documentId")
    fun getHighlightsForDocument(documentId: Long): Flow<List<DocumentHighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("DELETE FROM documents WHERE title LIKE '%نموذج%' OR title LIKE '%تجريبي%'")
    suspend fun deleteSampleDocuments()

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: DocumentHighlightEntity): Long

    @Delete
    suspend fun deleteHighlight(highlight: DocumentHighlightEntity)
}

// 10. PrayerDao
@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_logs WHERE dateString = :date")
    fun getPrayerLogForDate(date: String): Flow<PrayerLogEntity?>

    @Query("SELECT * FROM prayer_logs WHERE dateString = :date")
    suspend fun getPrayerLogForDateSync(date: String): PrayerLogEntity?

    @Query("SELECT * FROM prayer_logs ORDER BY dateString DESC LIMIT 30")
    fun getRecentPrayerLogs(): Flow<List<PrayerLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePrayerLog(log: PrayerLogEntity): Long
}

// 11. ClipboardDao
@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_history ORDER BY isFavorite DESC, copiedAtMillis DESC")
    fun getAllClipboards(): Flow<List<ClipboardHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboard(item: ClipboardHistoryEntity): Long

    @Delete
    suspend fun deleteClipboard(item: ClipboardHistoryEntity)

    @Query("DELETE FROM clipboard_history WHERE isFavorite = 0")
    suspend fun clearUnpinned()
}

// 12. VoiceNoteDao
@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY isPinned DESC, createdAtMillis DESC")
    fun getAllVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity): Long

    @Update
    suspend fun updateVoiceNote(voiceNote: VoiceNoteEntity)

    @Delete
    suspend fun deleteVoiceNote(voiceNote: VoiceNoteEntity)
}

// 13. IdeaVaultDao
@Dao
interface IdeaVaultDao {
    @Query("SELECT * FROM idea_vault ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun getAllIdeas(): Flow<List<IdeaVaultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdea(idea: IdeaVaultEntity): Long

    @Update
    suspend fun updateIdea(idea: IdeaVaultEntity)

    @Delete
    suspend fun deleteIdea(idea: IdeaVaultEntity)
}

// 14. QuestDao
@Dao
interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY isCompleted ASC, questType DESC, createdAtMillis DESC")
    fun getAllQuests(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE isCompleted = 0 ORDER BY questType DESC")
    fun getActiveQuests(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE isCompleted = 1")
    fun getCompletedQuests(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun getQuestById(id: Long): QuestEntity?

    @Query("SELECT COUNT(*) FROM quests")
    suspend fun getQuestCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuest(quest: QuestEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuests(quests: List<QuestEntity>)

    @Update
    suspend fun updateQuest(quest: QuestEntity)

    @Delete
    suspend fun deleteQuest(quest: QuestEntity)

    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun deleteQuestById(id: Long)

    @Query("UPDATE quests SET isCompleted = :completed, completionCount = completionCount + :incrementCount, currentStreak = :newStreak, bestStreak = CASE WHEN :newStreak > bestStreak THEN :newStreak ELSE bestStreak END, lastCompletedMillis = :timestamp WHERE id = :id")
    suspend fun updateQuestCompletion(id: Long, completed: Boolean, incrementCount: Int, newStreak: Int, timestamp: Long)

    @Query("UPDATE quests SET isCompleted = 0 WHERE isRecurring = 1")
    suspend fun resetRecurringQuestsDaily()
}

// 15. LevelHistoryDao
@Dao
interface LevelHistoryDao {
    @Query("SELECT * FROM level_history ORDER BY timestampMillis DESC")
    fun getAllLevelHistory(): Flow<List<LevelHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevelHistory(entry: LevelHistoryEntity): Long
}

// 16. SystemLogDao
@Dao
interface SystemLogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestampMillis DESC LIMIT 200")
    fun getAllSystemLogs(): Flow<List<SystemLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SystemLogEntity): Long

    @Query("DELETE FROM system_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM system_logs")
    suspend fun clearAllLogs()
}

// 17. UserCollectionDao
@Dao
interface UserCollectionDao {
    @Query("SELECT * FROM user_collections ORDER BY isUnlocked DESC, requiredLevel ASC")
    fun getAllCollections(): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM user_collections")
    suspend fun getAllCollectionsList(): List<UserCollectionEntity>

    @Query("SELECT * FROM user_collections WHERE itemType = :type")
    fun getCollectionsByType(type: String): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM user_collections WHERE isEquipped = 1")
    fun getEquippedItems(): Flow<List<UserCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(item: UserCollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(item: UserCollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollections(items: List<UserCollectionEntity>)

    @Update
    suspend fun updateCollection(item: UserCollectionEntity)

    @Query("UPDATE user_collections SET isEquipped = CASE WHEN itemId = :itemId THEN 1 ELSE 0 END WHERE itemType = :type")
    suspend fun equipItem(type: String, itemId: String)

    @Query("UPDATE user_collections SET isUnlocked = 1, unlockedAtMillis = :timestamp WHERE itemId = :itemId")
    suspend fun unlockItem(itemId: String, timestamp: Long)
}

// 18. ExamDao
@Dao
interface ExamDao {
    @Query("SELECT * FROM exam_countdowns ORDER BY examTimestampMillis ASC")
    fun getAllExams(): Flow<List<ExamCountdownEntity>>

    @Query("SELECT * FROM exam_countdowns")
    suspend fun getAllExamsList(): List<ExamCountdownEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamCountdownEntity): Long

    @Delete
    suspend fun deleteExam(exam: ExamCountdownEntity)
}
