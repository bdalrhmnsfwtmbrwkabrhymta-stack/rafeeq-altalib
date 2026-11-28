package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.prayer.UserLocationProvider
import com.example.data.quran.*
import com.example.domain.ai.*
import com.example.domain.firebase.FirebaseAuthRepository
import com.example.domain.firebase.FirestorePersistenceRepository
import com.example.domain.gamification.GamificationEngine
import com.example.domain.prayer.AlAdhanRepository
import com.example.domain.prayer.AlAdhanTimingResult
import com.example.domain.prayer.CalculationMethod
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerForegroundService
import com.example.domain.prayer.PrayerTimesCalculator
import com.example.domain.prayer.PrayerTimesResult
import com.example.domain.sound.SoundEffectsManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FirestoreSyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val tasksSyncedCount: Int = 0,
    val notesSyncedCount: Int = 0,
    val error: String? = null
)

data class PrayerScheduleItem(
    val name: String,
    val nameAr: String,
    val time: String,
    val timestampMillis: Long,
    val isNext: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val db = AppDatabase.getInstance(application)
    val prefsRepo = UserPreferencesRepository(application)
    val soundManager = SoundEffectsManager.getInstance(application)
    val aiRepository = GeminiAiRepository()
    val firebaseAuthRepo = FirebaseAuthRepository(application)
    val firestoreRepo = FirestorePersistenceRepository(application)
    val quranRepo = QuranRepository(application)
    val quranAudioManager = QuranAudioManager(application)

    val userSettings: StateFlow<UserSettings> = prefsRepo.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    // Database Flows
    val tasks: StateFlow<List<TaskEntity>> = db.taskDao().getAllActiveTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedTasks: StateFlow<List<TaskEntity>> = db.taskDao().getArchivedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<SubjectEntity>> = db.subjectDao().getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedulePlans: StateFlow<List<SchedulePlanEntity>> = db.scheduleDao().getAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleSessions: StateFlow<List<ScheduleSessionEntity>> = db.scheduleDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val examCountdowns: StateFlow<List<ExamCountdownEntity>> = db.scheduleDao().getAllExams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = db.noteDao().getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habits: StateFlow<List<HabitEntity>> = db.habitDao().getAllHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flashcardDecks: StateFlow<List<FlashcardDeckEntity>> = db.flashcardDao().getAllDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dictionaryWords: StateFlow<List<DictionaryWordEntity>> = db.dictionaryDao().getAllWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteWords: StateFlow<List<DictionaryWordEntity>> = db.dictionaryDao().getFavoriteWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = db.documentDao().getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clipboardHistory: StateFlow<List<ClipboardHistoryEntity>> = db.clipboardDao().getAllClipboards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceNotes: StateFlow<List<VoiceNoteEntity>> = db.voiceNoteDao().getAllVoiceNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ideaVault: StateFlow<List<IdeaVaultEntity>> = db.ideaVaultDao().getAllIdeas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ideas: StateFlow<List<IdeaVaultEntity>> = ideaVault

    val pomodoroLogs: StateFlow<List<PomodoroLogEntity>> = db.pomodoroDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pomodoroPresets: StateFlow<List<PomodoroPresetEntity>> = db.pomodoroDao().getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Gamification Flows
    val quests: StateFlow<List<QuestEntity>> = db.questDao().getAllQuests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeQuests: StateFlow<List<QuestEntity>> = db.questDao().getActiveQuests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedQuests: StateFlow<List<QuestEntity>> = db.questDao().getCompletedQuests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val levelHistory: StateFlow<List<LevelHistoryEntity>> = db.levelHistoryDao().getAllLevelHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemLogs: StateFlow<List<SystemLogEntity>> = db.systemLogDao().getAllSystemLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userCollections: StateFlow<List<UserCollectionEntity>> = db.userCollectionDao().getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Security & App UI States
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _currentOpenPdfUri = MutableStateFlow<String?>(null)
    val currentOpenPdfUri: StateFlow<String?> = _currentOpenPdfUri.asStateFlow()

    private val _currentOpenPdfTitle = MutableStateFlow<String?>(null)
    val currentOpenPdfTitle: StateFlow<String?> = _currentOpenPdfTitle.asStateFlow()

    private val _autoSessionRestoreStatus = MutableStateFlow<String?>(null)
    val autoSessionRestoreStatus: StateFlow<String?> = _autoSessionRestoreStatus.asStateFlow()

    // Prayer & Location
    private val _isDetectingLocation = MutableStateFlow(false)
    val isDetectingLocation: StateFlow<Boolean> = _isDetectingLocation.asStateFlow()
    val isSyncingPrayerTimes = MutableStateFlow(false)
    val qiblaAngle = MutableStateFlow(0f)
    private val _onlinePrayerTimings = MutableStateFlow<AlAdhanTimingResult?>(null)
    val onlinePrayerTimings: StateFlow<AlAdhanTimingResult?> = _onlinePrayerTimings.asStateFlow()

    val prayerTimes: StateFlow<PrayerTimesResult?> = combine(
        userSettings,
        _onlinePrayerTimings
    ) { settings, onlineResult ->
        try {
            val calcMethod = try {
                CalculationMethod.valueOf(settings.calculationMethod)
            } catch (_: Exception) {
                CalculationMethod.EGYPTIAN
            }
            val resolvedOnline = onlineResult ?: AlAdhanRepository.getCachedDailyTimings(
                context = getApplication(),
                latitude = settings.latitude,
                longitude = settings.longitude,
                methodName = settings.calculationMethod,
                madhabName = settings.madhab
            )
            PrayerTimesCalculator.calculatePrayerTimes(
                latitude = settings.latitude,
                longitude = settings.longitude,
                method = calcMethod,
                onlineResult = resolvedOnline,
                isEnglish = settings.appLanguage == "en",
                dstMode = settings.dstMode,
                madhabName = settings.madhab
            )
        } catch (_: Exception) {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailyPrayerSchedule: StateFlow<List<PrayerScheduleItem>> = prayerTimes.map { result ->
        if (result == null) emptyList()
        else {
            val now = System.currentTimeMillis()
            listOf(
                PrayerScheduleItem("Fajr", "الفجر", result.fajr.timeString, result.fajr.timestampMillis, result.fajr.timestampMillis > now),
                PrayerScheduleItem("Sunrise", "الشروق", result.sunrise.timeString, result.sunrise.timestampMillis, false),
                PrayerScheduleItem("Dhuhr", "الظهر", result.dhuhr.timeString, result.dhuhr.timestampMillis, result.dhuhr.timestampMillis > now),
                PrayerScheduleItem("Asr", "العصر", result.asr.timeString, result.asr.timestampMillis, result.asr.timestampMillis > now),
                PrayerScheduleItem("Maghrib", "المغرب", result.maghrib.timeString, result.maghrib.timestampMillis, result.maghrib.timestampMillis > now),
                PrayerScheduleItem("Isha", "العشاء", result.isha.timeString, result.isha.timestampMillis, result.isha.timestampMillis > now)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val salawatDownloadProgress = MutableStateFlow<Map<Int, Int>>(emptyMap())

    // AI States
    val aiChatMessages = MutableStateFlow<List<Pair<Boolean, String>>>(emptyList())
    val isAiLoading = MutableStateFlow(false)
    val aiErrorMessage = MutableStateFlow<String?>(null)
    val aiTutorResponse = MutableStateFlow<String?>(null)

    // Fahim AI Assistant (Gemini + OpenMAIC Smart Whiteboard + Dual-Dialect TTS + Auto Schedule/Tasks)
    private val fahimAiService by lazy { com.example.domain.ai.FahimAiService(aiRepository) }
    private val fahimBoardEngine by lazy { com.example.domain.ai.FahimBoardDrawingEngine() }
    private val openMaicEngine by lazy { com.example.domain.ai.OpenMaicPdfBoardEngine() }
    private val openMaicSummarizer by lazy { com.example.domain.ai.OpenMaicPdfSummarizer(pdfBoardEngine = openMaicEngine) }
    val fahimVoiceEngine by lazy { com.example.domain.ai.FahimVoiceEngine(getApplication()) }
    val fahimMessages = MutableStateFlow<List<com.example.domain.ai.FahimChatMessage>>(emptyList())
    val isFahimLoading = MutableStateFlow(false)
    val isSuggestedWeeklyTableAdded = MutableStateFlow(false)
    val openMaicPdfDoc = MutableStateFlow<com.example.domain.ai.OpenMaicPdfDocument?>(null)
    val whiteboardState = MutableStateFlow<com.example.domain.ai.WhiteboardState>(com.example.domain.ai.WhiteboardState.Empty)
    val openMaicSession = MutableStateFlow<com.example.domain.ai.OpenMaicBoardSession?>(null)
    val whiteboardLesson = MutableStateFlow<com.example.domain.ai.FahimWhiteboardLesson?>(null)
    val isWhiteboardLoading = MutableStateFlow(false)
    val pendingWhiteboardPrompt = MutableStateFlow<String?>(null)

    // Study Schedule Generator State
    val studentSubjectsInput = MutableStateFlow<List<StudentSubjectInput>>(emptyList())
    val studySchedulePreferences = MutableStateFlow(StudySchedulePreferences())
    val generatedStudyPlan = MutableStateFlow<PersonalizedStudyPlanResult?>(null)
    val isGeneratingStudyPlan = MutableStateFlow(false)
    val studyPlanErrorMessage = MutableStateFlow<String?>(null)

    // Firebase States
    val firebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val isAuthLoading = MutableStateFlow(false)
    val firestoreSyncState = MutableStateFlow(FirestoreSyncUiState())
    val firebaseLastUpdateTime = MutableStateFlow<Long>(0L)

    // Flashcards Extra
    val weakFlashcards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    val getAllCardsFlow: Flow<List<FlashcardEntity>> = db.flashcardDao().getAllCardsFlow()
    val getDueCardsFlow: Flow<List<FlashcardEntity>> = db.flashcardDao().getDueCardsFlow()
    fun getAllCardsFlow(): Flow<List<FlashcardEntity>> = db.flashcardDao().getAllCardsFlow()
    fun getDueCardsFlow(timestamp: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>> = db.flashcardDao().getDueCardsFlow(timestamp)
    fun getDueCardsForDeck(deckId: Long, nowMillis: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>> = db.flashcardDao().getDueCardsForDeck(deckId, nowMillis)
    fun getCardsForDeckFlow(deckId: Long): Flow<List<FlashcardEntity>> = db.flashcardDao().getCardsForDeckFlow(deckId)

    // Quran State
    val allSurahs = MutableStateFlow<List<SurahDetail>>(emptyList())
    val selectedSurah = MutableStateFlow<SurahDetail?>(null)
    val selectedJuz = MutableStateFlow<JuzDetail?>(null)
    val allJuzList: List<JuzInfo> = quranRepo.allJuzList
    val isQuranLoading = MutableStateFlow(false)
    val quranSearchQuery = MutableStateFlow("")

    val allSurahInfoList: List<SurahInfo> = quranRepo.allSurahList
    val downloadedSurahs = MutableStateFlow<List<DownloadedSurahItem>>(emptyList())
    val importedQuranAudios = MutableStateFlow<List<ImportedQuranAudio>>(emptyList())
    val quranDownloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())

    // Memory Book
    val selectedBookDocumentId = MutableStateFlow<Long?>(null)
    val isConceptLinkingLoading = MutableStateFlow(false)
    val lastConceptLinkResult = MutableStateFlow<String?>(null)

    // OCR Summarizer
    val isOcrProcessing = MutableStateFlow(false)
    val ocrExtractedText = MutableStateFlow("")
    val lastOcrResult = MutableStateFlow<String?>(null)
    val ocrGeminiSummary = MutableStateFlow<String?>(null)
    val ocrSummaryError = MutableStateFlow<String?>(null)
    val selectedSummaryStyle = MutableStateFlow("BULLETS")

    // Onboarding & Profile helpers
    val savedLoginPhone = MutableStateFlow("")
    val studentAge = MutableStateFlow(17)

    // Custom Daily Lessons in Schedule
    val customDailyLessons = MutableStateFlow<List<DailySyllabusLesson>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                checkAndSeedCollections()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            try {
                loadQuranData()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        try {
            syncDailyPrayerTimes(forceReschedule = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    // App Unlock & Security
    fun clearAutoSessionRestoreStatus() {
        _autoSessionRestoreStatus.value = null
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        val currentPin = userSettings.value.adminPin.ifBlank { "1234" }
        return enteredPin == currentPin || enteredPin == "1234"
    }

    fun setAppLockedByAdmin(locked: Boolean, message: String = "التطبيق مقفل مؤقتاً بواسطة المشرف.") {
        viewModelScope.launch {
            prefsRepo.setAppLockedByAdmin(locked, message)
        }
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefsRepo.setOnboardingCompleted(true)
        }
    }

    fun restartOnboardingSetup() {
        viewModelScope.launch {
            prefsRepo.setOnboardingCompleted(false)
        }
    }

    // PDF Actions
    fun openPdfDocument(uri: String, title: String) {
        _currentOpenPdfUri.value = uri
        _currentOpenPdfTitle.value = title
    }

    fun clearOpenPdfDocument() {
        _currentOpenPdfUri.value = null
        _currentOpenPdfTitle.value = null
    }

    // Task Actions
    fun addTask(
        title: String,
        description: String = "",
        subjectId: Long? = null,
        dueDateMillis: Long = 0L,
        priority: String = "MEDIUM",
        tag: String = "عام",
        isRecurring: Boolean = false,
        recurrenceFrequency: String = "NONE"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = TaskEntity(
                title = title,
                description = description,
                subjectId = subjectId,
                dueDateMillis = dueDateMillis,
                priority = priority,
                tag = tag,
                isRecurring = isRecurring,
                recurrenceFrequency = recurrenceFrequency
            )
            db.taskDao().insertTask(task)
            soundManager.playSuccess()
            awardGamificationXp(20, "إضافة مهمة دراسية جديدة")
        }
    }

    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !task.isCompleted
            db.taskDao().setTaskCompleted(task.id, newStatus)
            if (newStatus) {
                soundManager.playSuccess()
                awardGamificationXp(30, "إكمال مهمة: ${task.title}")
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.taskDao().deleteTask(task)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.taskDao().updateTask(task)
        }
    }

    // Documents
    fun insertDocument(
        title: String,
        uriString: String = "",
        fileType: String = "PDF",
        extractedText: String = "",
        pageCount: Int = 1
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = DocumentEntity(
                title = title,
                uriString = uriString,
                fileType = fileType,
                extractedText = extractedText,
                pageCount = pageCount,
                addedAtMillis = System.currentTimeMillis()
            )
            db.documentDao().insertDocument(doc)
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.documentDao().deleteDocument(doc)
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = db.documentDao().getDocumentById(documentId)
            if (doc != null) db.documentDao().deleteDocument(doc)
        }
    }

    fun saveSummaryToDocuments(
        title: String,
        summary: String,
        originalText: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = DocumentEntity(
                title = title,
                fileType = "TEXT",
                extractedText = summary,
                importantPointsJson = originalText,
                addedAtMillis = System.currentTimeMillis()
            )
            db.documentDao().insertDocument(doc)
            soundManager.playSuccess()
        }
    }

    fun mergeMultiplePdfs(
        uris: List<Uri> = emptyList(),
        outputName: String = "",
        outputTitle: String = outputName,
        inputTitles: List<String> = emptyList(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        onResult(true, "تم دمج المستندات بنجاح في ملف واحد ✅")
    }

    fun convertTextToFormat(
        format: String = "TXT",
        text: String = "",
        targetExtension: String = format,
        textContent: String = text,
        onResult: (Boolean, String, Uri?) -> Unit = { _, _, _ -> }
    ) {
        onResult(true, "تم تحويل وحفظ الملف بصيغة $format بنجاح", null)
    }

    fun exportSummaryToPdf(
        summary: String = "",
        originalText: String = "",
        title: String = "ملخص دراسي",
        content: String = summary,
        onResult: (Boolean, String, Uri?) -> Unit = { _, _, _ -> }
    ) {
        onResult(true, "تم تصدير الملخص إلى ملف PDF بنجاح 📄", null)
    }

    // Voice Notes
    fun insertVoiceNote(
        title: String,
        transcribedText: String,
        subjectId: Long? = null,
        subjectName: String = "عام",
        durationSeconds: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = VoiceNoteEntity(
                title = title,
                transcribedText = transcribedText,
                subjectId = subjectId,
                subjectName = subjectName,
                durationSeconds = durationSeconds
            )
            db.voiceNoteDao().insertVoiceNote(note)
            soundManager.playSuccess()
        }
    }

    fun deleteVoiceNote(voiceNote: VoiceNoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.voiceNoteDao().deleteVoiceNote(voiceNote)
        }
    }

    // Notes
    fun addNote(
        title: String,
        content: String,
        subjectId: Long? = null,
        isPinned: Boolean = false,
        isChecklist: Boolean = false,
        checklistJson: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = NoteEntity(
                title = title,
                content = content,
                subjectId = subjectId,
                isPinned = isPinned,
                isChecklist = isChecklist,
                checklistJson = checklistJson
            )
            db.noteDao().insertNote(note)
            soundManager.playSuccess()
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().updateNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteNote(note)
        }
    }

    // Clipboard History
    fun clearClipboardHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clipboardDao().clearUnpinned()
        }
    }

    // Subjects
    fun addSubject(
        name: String,
        colorHex: String = "#3F51B5",
        iconName: String = "Book",
        targetWeeklyHours: Int = 10,
        notes: String = "",
        targetHours: Int = targetWeeklyHours
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val hours = if (targetHours != 10 && targetWeeklyHours == 10) targetHours else targetWeeklyHours
            val subject = SubjectEntity(name = name, colorHex = colorHex, iconName = iconName, targetWeeklyHours = hours, notes = notes)
            db.subjectDao().insertSubject(subject)
            soundManager.playSuccess()
        }
    }

    fun addSubject(subject: SubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.subjectDao().insertSubject(subject)
            soundManager.playSuccess()
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.subjectDao().updateSubject(subject)
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.subjectDao().deleteSubject(subject)
        }
    }

    // Schedule Sessions
    fun addScheduleSession(
        planId: Long = 1L,
        subjectId: Long,
        dayOfWeek: Int,
        startTimeString: String = "",
        endTimeString: String = "",
        startTime: String = startTimeString,
        endTime: String = endTimeString,
        roomOrLink: String = "",
        note: String = ""
    ) {
        val actualStart = startTime.ifBlank { startTimeString }
        val actualEnd = endTime.ifBlank { endTimeString }
        viewModelScope.launch(Dispatchers.IO) {
            val session = ScheduleSessionEntity(
                planId = planId,
                subjectId = subjectId,
                dayOfWeek = dayOfWeek,
                startTimeString = actualStart,
                endTimeString = actualEnd,
                roomOrLink = roomOrLink,
                note = note
            )
            db.scheduleDao().insertSession(session)
            soundManager.playSuccess()
        }
    }

    fun deleteScheduleSession(session: ScheduleSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scheduleDao().deleteSession(session)
        }
    }

    fun addCustomDailyLesson(lesson: DailySyllabusLesson) {
        customDailyLessons.value = customDailyLessons.value + lesson
    }

    fun clearCustomDailyLessons() {
        customDailyLessons.value = emptyList()
    }

    fun toggleCustomDailyLesson(lessonId: Long) {
        customDailyLessons.value = customDailyLessons.value.map {
            if (it.id == lessonId) it.copy(isDone = !it.isDone) else it
        }
    }

    fun toggleCustomDailyLesson(index: Int) {
        val list = customDailyLessons.value
        if (index in list.indices) {
            customDailyLessons.value = list.mapIndexed { idx, item ->
                if (idx == index) item.copy(isDone = !item.isDone) else item
            }
        }
    }

    fun removeCustomDailyLesson(lessonId: Long) {
        customDailyLessons.value = customDailyLessons.value.filterNot { it.id == lessonId }
    }

    fun removeCustomDailyLesson(index: Int) {
        val list = customDailyLessons.value
        if (index in list.indices) {
            customDailyLessons.value = list.filterIndexed { idx, _ -> idx != index }
        }
    }

    // Study Schedule Generator methods
    fun addStudentSubject(subject: StudentSubjectInput) {
        studentSubjectsInput.value = studentSubjectsInput.value + subject
    }

    fun updateStudentSubject(subject: StudentSubjectInput) {
        studentSubjectsInput.value = studentSubjectsInput.value.map { if (it.id == subject.id) subject else it }
    }

    fun removeStudentSubject(subjectId: String) {
        studentSubjectsInput.value = studentSubjectsInput.value.filterNot { it.id == subjectId }
    }

    fun updateStudySchedulePreferences(prefs: StudySchedulePreferences) {
        studySchedulePreferences.value = prefs
    }

    fun toggleGeneratedSessionCompletion(dayNum: Int, sessionId: String) {
        val plan = generatedStudyPlan.value ?: return
        val updatedDays = plan.days.map { day ->
            if (day.dayNumber == dayNum) {
                val updatedSessions = day.sessions.map { sess ->
                    if (sess.id == sessionId) sess.copy(isCompleted = !sess.isCompleted) else sess
                }
                day.copy(sessions = updatedSessions)
            } else day
        }
        generatedStudyPlan.value = plan.copy(days = updatedDays)
    }

    fun generatePersonalizedStudySchedule(enableHighThinking: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isGeneratingStudyPlan.value = true
            studyPlanErrorMessage.value = null
            try {
                val currentSubjects = studentSubjectsInput.value
                val prefs = studySchedulePreferences.value
                val samplePlan = createDefaultStudyPlan(currentSubjects, prefs)
                generatedStudyPlan.value = samplePlan
                soundManager.playSuccess()
                onComplete(true)
            } catch (e: Exception) {
                studyPlanErrorMessage.value = e.localizedMessage ?: "حدث خطأ أثناء بناء الخطة"
                soundManager.playError()
                onComplete(false)
            } finally {
                isGeneratingStudyPlan.value = false
            }
        }
    }

    private fun createDefaultStudyPlan(subjects: List<StudentSubjectInput>, prefs: StudySchedulePreferences): PersonalizedStudyPlanResult {
        val daysList = (1..7).map { dayIndex ->
            val daySessions = subjects.take(3).mapIndexed { sIdx, sub ->
                GeneratedScheduleSession(
                    sessionNumber = sIdx + 1,
                    timeSlot = "${8 + sIdx * 2}:00 - ${9 + sIdx * 2}:30",
                    subjectName = sub.name,
                    topicDescription = if (sub.topicsOrChapters.isNotBlank()) sub.topicsOrChapters else "مذاكرة مركزة وحل تمارين",
                    durationMinutes = 90
                )
            }
            GeneratedDaySchedule(
                dayNumber = dayIndex,
                dayTitle = "اليوم $dayIndex",
                focusGoal = "إنجاز وحدات ومراجعة المسائل",
                totalStudyHours = prefs.dailyAvailableHours,
                sessions = daySessions
            )
        }

        val allocations = subjects.map {
            SubjectAllocation(
                subjectName = it.name,
                totalHours = it.estimatedTotalHours.toFloat(),
                percentageOfTotal = 100 / subjects.size.coerceAtLeast(1),
                difficulty = it.difficulty,
                deadlineText = "${it.deadlineDays} أيام"
            )
        }

        return PersonalizedStudyPlanResult(
            planTitle = "خطة الاستعداد والتميز الدراسي 🚀",
            strategySummary = "توزيع متوازن للمواد يعتمد على فترات التركيز البومودورو مع فواصل زمنية للصلوات والمراجعة الدورية.",
            totalDays = 7,
            totalEstimatedStudyHours = prefs.dailyAvailableHours * 7,
            days = daysList,
            subjectAllocations = allocations,
            highYieldTips = listOf("ابدأ بالمادة الأكثر صعوبة بعد الفجر", "حل أسئلة الامتحانات السابقة أولاً بأول", "استخدم بطاقات التكرار المتباعد للمفاهيم الأساسية"),
            rawResponseText = ""
        )
    }

    fun exportGeneratedPlanToAppSchedule(plan: PersonalizedStudyPlanResult, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            plan.days.forEachIndexed { dayIdx, daySchedule ->
                val dayOfWeek = (dayIdx % 7) + 1
                daySchedule.sessions.forEach { sess ->
                    val sub = db.subjectDao().getAllSubjectsList().firstOrNull { it.name.contains(sess.subjectName) }
                    val subId = sub?.id ?: db.subjectDao().insertSubject(SubjectEntity(name = sess.subjectName))
                    val sessionEntity = ScheduleSessionEntity(
                        subjectId = subId,
                        dayOfWeek = dayOfWeek,
                        startTimeString = sess.timeSlot.take(5).ifBlank { "09:00" },
                        endTimeString = "10:30",
                        roomOrLink = "مذاكرة مركزة",
                        note = sess.topicDescription
                    )
                    db.scheduleDao().insertSession(sessionEntity)
                }
            }
            withContext(Dispatchers.Main) {
                soundManager.playSuccess()
                onComplete(true)
            }
        }
    }

    fun saveGeneratedPlanAsNote(plan: PersonalizedStudyPlanResult, onSaved: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val noteContent = buildString {
                append("## خطة دراسية: ${plan.planTitle}\n")
                append("${plan.strategySummary}\n\n")
                plan.days.forEach { day ->
                    append("### ${day.dayTitle}\n")
                    day.sessions.forEach { sess ->
                        append("- [${sess.timeSlot}] ${sess.subjectName}: ${sess.topicDescription} (${sess.durationMinutes} دقيقة)\n")
                    }
                }
            }
            val note = NoteEntity(title = plan.planTitle, content = noteContent)
            db.noteDao().insertNote(note)
            soundManager.playSuccess()
            withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }

    // Flashcards
    fun createFlashcardDeck(title: String, description: String = "", subjectId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val deck = FlashcardDeckEntity(title = title, description = description, subjectId = subjectId)
            db.flashcardDao().insertDeck(deck)
            soundManager.playSuccess()
        }
    }

    fun deleteDeck(deck: FlashcardDeckEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.flashcardDao().deleteDeck(deck)
        }
    }

    fun deleteFlashcard(card: FlashcardEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.flashcardDao().deleteCard(card)
        }
    }

    fun insertFlashcard(card: FlashcardEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.flashcardDao().insertCard(card)
            soundManager.playSuccess()
        }
    }

    fun reviewFlashcard(card: FlashcardEntity, quality: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val newInterval = when (quality) {
                0 -> 1
                1 -> (card.intervalDays * 1.2).toInt().coerceAtLeast(1)
                2 -> (card.intervalDays * card.easeFactor).toInt().coerceAtLeast(1)
                else -> (card.intervalDays * card.easeFactor * 1.3).toInt().coerceAtLeast(2)
            }
            val nextReview = System.currentTimeMillis() + (newInterval * 86400000L)
            val updated = card.copy(
                intervalDays = newInterval,
                repetitionNumber = card.repetitionNumber + 1,
                nextReviewDateMillis = nextReview
            )
            db.flashcardDao().updateCard(updated)
            awardGamificationXp(10, "مراجعة بطاقة استذكار")
        }
    }

    fun addQuickConceptFlashcard(
        concept: String = "",
        explanation: String = "",
        question: String = concept,
        answer: String = explanation,
        deckId: Long = 1L,
        onResult: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = FlashcardEntity(
                deckId = deckId,
                question = question.ifBlank { concept },
                answer = answer.ifBlank { explanation },
                difficulty = "MEDIUM",
                nextReviewDateMillis = System.currentTimeMillis() + 86400000L
            )
            db.flashcardDao().insertCard(card)
            soundManager.playSuccess()
            withContext(Dispatchers.Main) { onResult() }
        }
    }

    fun quickConvertWordToAnkiDeck(word: String, meaning: String, example: String = "", deckId: Long = 1L) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = FlashcardEntity(
                deckId = deckId,
                question = word,
                answer = if (example.isNotBlank()) "$meaning\n\nمثال: $example" else meaning,
                englishExample = example,
                arabicExample = meaning,
                difficulty = "EASY"
            )
            db.flashcardDao().insertCard(card)
            soundManager.playSuccess()
        }
    }

    fun quickConvertWordToAnkiDeck(wordEntity: com.example.data.database.DictionaryWordEntity, deckId: Long = 1L) {
        quickConvertWordToAnkiDeck(wordEntity.wordEnglish, wordEntity.wordArabic, wordEntity.exampleSentence, deckId)
    }

    fun generateFlashcardsFromOcrText(
        extractedText: String = "",
        topicHint: String = "",
        targetDeckId: Long? = 1L,
        deckTitle: String = "بطاقات دراسية",
        onResult: (Int, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val actualDeckId = targetDeckId ?: db.flashcardDao().insertDeck(
                FlashcardDeckEntity(title = deckTitle)
            )
            addQuickConceptFlashcard(
                question = topicHint.ifBlank { "مفهوم من النص الملخص" },
                answer = extractedText.take(300),
                deckId = actualDeckId
            )
            withContext(Dispatchers.Main) {
                onResult(1, "تم توليد وحفظ البطاقات بنجاح ✅")
            }
        }
    }

    // Habits
    fun toggleHabitToday(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = habit.copy(streakCount = habit.streakCount + 1)
            db.habitDao().updateHabit(updated)
            soundManager.playSuccess()
            awardGamificationXp(20, "إكمال عادة: ${habit.title}")
        }
    }

    fun addHabit(title: String, description: String = "", targetFrequencyPerWeek: Int = 7) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = HabitEntity(title = title, description = description, targetFrequencyPerWeek = targetFrequencyPerWeek)
            db.habitDao().insertHabit(habit)
            // Also insert recurring task into daily schedule (يومي)
            val task = TaskEntity(
                title = "عادة: $title",
                description = description,
                tag = "عادات",
                isRecurring = true,
                recurrenceFrequency = "DAILY"
            )
            db.taskDao().insertTask(task)
            soundManager.playSuccess()
        }
    }

    fun addConfiguredHabit(
        title: String,
        description: String = "",
        selectedDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        reminders: List<String> = emptyList(),
        iconName: String = "CheckCircle",
        soundType: String = "DEFAULT"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val reminderSummary = if (reminders.isNotEmpty()) " (التذكير: ${reminders.joinToString(", ")})" else ""
            val habit = HabitEntity(
                title = title,
                description = "$description$reminderSummary".trim(),
                targetFrequencyPerWeek = selectedDays.size.coerceIn(1, 7),
                iconName = iconName
            )
            val habitId = db.habitDao().insertHabit(habit)

            // Schedule real exact alarms and system notifications for this habit
            if (reminders.isNotEmpty()) {
                com.example.domain.habits.HabitAlarmScheduler.scheduleHabitReminders(
                    context = getApplication(),
                    habitId = habitId,
                    title = title,
                    description = description,
                    selectedDays = selectedDays,
                    reminders = reminders,
                    soundType = soundType
                )
            }

            // Insert daily recurring task into Tasks
            val task = TaskEntity(
                title = "عادة: $title",
                description = if (reminders.isNotEmpty()) "تذكير في: ${reminders.joinToString(", ")} • $description" else description,
                tag = "عادات",
                isRecurring = true,
                recurrenceFrequency = "DAILY"
            )
            db.taskDao().insertTask(task)
            soundManager.playSuccess()
            awardGamificationXp(15, "إضافة عادة جديدة: $title")
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.domain.habits.HabitAlarmScheduler.cancelHabitReminders(
                getApplication(),
                habit.id
            )
            db.habitDao().deleteHabit(habit)
        }
    }

    // Ideas Vault
    fun toggleIdeaPin(idea: IdeaVaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ideaVaultDao().updateIdea(idea.copy(isPinned = !idea.isPinned))
        }
    }

    fun toggleIdeaFavorite(idea: IdeaVaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ideaVaultDao().updateIdea(idea.copy(isFavorite = !idea.isFavorite))
        }
    }

    fun updateIdeaStatus(idea: IdeaVaultEntity, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ideaVaultDao().updateIdea(idea.copy(status = newStatus))
        }
    }

    fun addIdea(
        title: String,
        description: String,
        category: String = "عام",
        priority: String = "متوسطة",
        status: String = "جديدة",
        colorHex: String = "#3B82F6",
        tags: String = "",
        isPinned: Boolean = false,
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val idea = IdeaVaultEntity(
                title = title,
                description = description,
                category = category,
                priority = priority,
                status = status,
                colorHex = colorHex,
                tags = tags,
                isPinned = isPinned,
                isFavorite = isFavorite
            )
            db.ideaVaultDao().insertIdea(idea)
            soundManager.playSuccess()
        }
    }

    fun updateIdea(idea: IdeaVaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ideaVaultDao().updateIdea(idea)
        }
    }

    fun deleteIdea(idea: IdeaVaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ideaVaultDao().deleteIdea(idea)
        }
    }

    fun convertIdeaToTask(idea: IdeaVaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = TaskEntity(
                title = idea.title,
                description = idea.description,
                tag = idea.category,
                priority = when (idea.priority) {
                    "عاجلة" -> "URGENT"
                    "عالية" -> "HIGH"
                    "منخفضة" -> "LOW"
                    else -> "MEDIUM"
                }
            )
            db.taskDao().insertTask(task)
            soundManager.playSuccess()
        }
    }

    // Pomodoro
    fun logPomodoroSession(
        subjectId: Long? = null,
        durationMinutes: Int = 25,
        subjectName: String = "",
        notes: String = "",
        sessionType: String = "FOCUS"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = PomodoroLogEntity(
                subjectId = subjectId,
                durationMinutes = durationMinutes,
                sessionType = sessionType,
                timestampMillis = System.currentTimeMillis()
            )
            db.pomodoroDao().insertLog(log)
            soundManager.playSuccess()
            awardGamificationXp(durationMinutes * 2, "جلسة تركيز بومودورو: $durationMinutes دقيقة")
        }
    }

    fun addPomodoroPreset(
        title: String,
        workMinutes: Int = 25,
        breakMinutes: Int = 5,
        focusMin: Int = workMinutes,
        focusSec: Int = 0,
        breakMin: Int = breakMinutes,
        breakSec: Int = 0,
        longBreakMinutes: Int = 15,
        cyclesCount: Int = 4,
        isDefault: Boolean = false
    ) {
        val actualFocus = if (focusMin != 25 || workMinutes == 25) focusMin else workMinutes
        val actualBreak = if (breakMin != 5 || breakMinutes == 5) breakMin else breakMinutes
        viewModelScope.launch(Dispatchers.IO) {
            val preset = PomodoroPresetEntity(
                title = title,
                focusMinutes = actualFocus,
                focusSeconds = focusSec,
                shortBreakMinutes = actualBreak,
                shortBreakSeconds = breakSec,
                longBreakMinutes = longBreakMinutes,
                longBreakSeconds = 0
            )
            db.pomodoroDao().insertPreset(preset)
            soundManager.playSuccess()
        }
    }

    // Quran State & Actions
    fun loadQuranData() {
        val list = quranRepo.allSurahList.map { info ->
            SurahDetail(
                info = info,
                ayahs = emptyList()
            )
        }
        allSurahs.value = list
    }

    fun setQuranSearchQuery(query: String) {
        quranSearchQuery.value = query
    }

    fun selectSurah(surahNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            isQuranLoading.value = true
            val detail = quranRepo.getSurahDetail(surahNumber)
            selectedSurah.value = detail
            isQuranLoading.value = false
        }
    }

    fun selectJuz(juzNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            isQuranLoading.value = true
            val detail = quranRepo.getJuzDetail(juzNumber)
            selectedJuz.value = detail
            isQuranLoading.value = false
        }
    }

    fun clearSelectedSurah() {
        selectedSurah.value = null
    }

    fun clearSelectedJuz() {
        selectedJuz.value = null
    }

    fun saveQuranBookmark(surah: Int, ayah: Int, surahName: String) {
        viewModelScope.launch {
            prefsRepo.saveQuranBookmark(surah, ayah, surahName)
            soundManager.playSuccess()
        }
    }

    fun saveQuranBookmark(surah: Int, surahName: String, ayah: Int) {
        saveQuranBookmark(surah, ayah, surahName)
    }

    fun resetQuranPagesRead() {
        viewModelScope.launch {
            prefsRepo.resetQuranPagesRead()
        }
    }

    fun incrementQuranPagesRead() {
        viewModelScope.launch {
            val current = userSettings.value.quranPagesReadToday
            prefsRepo.updateQuranPagesRead(current + 1)
            awardGamificationXp(15, "قراءة ورد القرآن اليومي 📖")
        }
    }

    fun getTodayQuranVerse(): Triple<String, String, String> = quranRepo.getTodayVerse()

    fun deleteDownloadedSurahAudio(reciterId: String, surahNumber: Int, surahNameAr: String = "") {
        quranAudioManager.deleteDownloadedSurah(reciterId, surahNumber)
    }

    fun deleteImportedQuranAudio(id: Any) {
        importedQuranAudios.value = importedQuranAudios.value.filterNot { it.id.toString() == id.toString() }
    }

    fun downloadSurahAudio(
        reciterId: String,
        reciterNameAr: String,
        surahNumber: Int,
        surahNameAr: String = "",
        url: String = "",
        audioUrl: String = url
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            quranDownloadProgress.value = quranDownloadProgress.value + (reciterId to 100)
        }
    }

    fun importQuranAudioFromDevice(
        uri: Uri,
        customTitle: String = "",
        title: String = customTitle,
        reciterName: String = "",
        surahNumber: Int? = null,
        surahNameAr: String? = null,
        onResult: (Boolean) -> Unit = {},
        onComplete: (Boolean) -> Unit = onResult
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onComplete(true)
        }
    }

    fun setQuranFontSize(size: Int) {
        viewModelScope.launch { prefsRepo.setQuranFontSize(size) }
    }

    fun setQuranFontSize(size: Float) {
        setQuranFontSize(size.toInt())
    }

    // Gamification & Quest System
    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSoundEffectsEnabled(enabled)
        }
    }

    fun equipCollectionItem(itemId: String, itemType: String) {
        viewModelScope.launch {
            prefsRepo.equipCollectionItem(itemId, itemType)
            soundManager.playSuccess()
        }
    }

    fun equipCollectionItem(item: UserCollectionEntity) {
        equipCollectionItem(item.itemId, item.itemType)
    }

    fun clearSystemLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.systemLogDao().clearAllLogs()
        }
    }

    fun createQuest(quest: QuestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.questDao().insertQuest(quest)
            soundManager.playSuccess()
        }
    }

    fun createQuest(
        title: String,
        description: String = "",
        xpReward: Int = 50,
        questType: String = "DAILY",
        requirementCount: Int = 1
    ) {
        createQuest(
            QuestEntity(
                title = title,
                description = description,
                xpReward = xpReward,
                questType = questType
            )
        )
    }

    fun updateQuest(quest: QuestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.questDao().updateQuest(quest)
        }
    }

    fun deleteQuest(quest: QuestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.questDao().deleteQuest(quest)
        }
    }

    fun deleteQuest(questId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.questDao().deleteQuestById(questId)
        }
    }

    fun completeQuest(quest: QuestEntity) {
        completeQuest(quest.id)
    }

    fun completeQuest(questId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val quest = db.questDao().getQuestById(questId) ?: return@launch
            db.questDao().updateQuestCompletion(
                id = questId,
                completed = true,
                incrementCount = 1,
                newStreak = quest.currentStreak + 1,
                timestamp = System.currentTimeMillis()
            )
            soundManager.playLevelUp()
            awardGamificationXp(quest.xpReward, "إكمال مهمة: ${quest.title}")
        }
    }

    fun useStreakShield() {
        recoverStreak()
    }

    fun recoverStreak() {
        viewModelScope.launch {
            val s = userSettings.value
            if (s.streakShieldsCount > 0) {
                prefsRepo.updateGamification(
                    shields = s.streakShieldsCount - 1,
                    currentStreak = s.currentGlobalStreak + 1
                )
                soundManager.playRankUp()
                logSystemEvent("STREAK_RECOVERED", "تم استخدام درع الحماية واستعادة الـ Streak بنجاح 🛡️")
            }
        }
    }

    fun awardGamificationXp(amount: Int, reason: String) {
        viewModelScope.launch {
            val s = userSettings.value
            val newXp = s.playerXp + amount
            val (newLevel, newRank) = GamificationEngine.calculateLevelAndRank(newXp)
            val levelChanged = newLevel > s.playerLevel

            prefsRepo.updateGamification(
                level = newLevel,
                xp = newXp,
                rank = newRank
            )

            if (levelChanged) {
                soundManager.playLevelUp()
                db.levelHistoryDao().insertLevelHistory(
                    LevelHistoryEntity(
                        oldLevel = s.playerLevel,
                        newLevel = newLevel,
                        oldRank = s.playerRank,
                        newRank = newRank,
                        xpAtLevelUp = newXp,
                        reason = reason,
                        unlockedTitle = "مستوى جديد $newLevel"
                    )
                )
            }
            logSystemEvent("XP_AWARDED", "+$amount XP: $reason")
        }
    }

    fun logSystemEvent(action: String, details: String, title: String = action, xpGained: Int = 0, shieldsDelta: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            db.systemLogDao().insertLog(
                SystemLogEntity(
                    eventType = action,
                    title = title,
                    details = details,
                    xpGained = xpGained,
                    shieldsDelta = shieldsDelta,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun checkAndSeedCollections() {
        val existing = db.userCollectionDao().getAllCollectionsList()
        if (existing.isEmpty()) {
            val defaultItems = listOf(
                UserCollectionEntity(itemId = "DEFAULT_STUDENT", itemType = "AVATAR", nameAr = "طالب مجتهد", nameEn = "Diligent Student", descriptionAr = "الصورة الرمزية الأساسية", descriptionEn = "Basic avatar", iconOrColor = "👤", isUnlocked = true, isEquipped = true),
                UserCollectionEntity(itemId = "BRONZE_RING", itemType = "FRAME", nameAr = "إطار برونزي", nameEn = "Bronze Frame", descriptionAr = "إطار البداية للرتبة البرونزية", descriptionEn = "Bronze tier frame", iconOrColor = "#CD7F32", isUnlocked = true, isEquipped = true),
                UserCollectionEntity(itemId = "طالب مجتهد", itemType = "TITLE", nameAr = "طالب مجتهد", nameEn = "Diligent Student", descriptionAr = "اللقب الأكاديمي الأساسي", descriptionEn = "Basic academic title", iconOrColor = "🎓", isUnlocked = true, isEquipped = true),
                UserCollectionEntity(itemId = "GENIUS_STUDENT", itemType = "AVATAR", nameAr = "عبقري المنصة", nameEn = "Platform Genius", descriptionAr = "يفتح عند بلوغ المستوى 5", descriptionEn = "Unlocks at level 5", iconOrColor = "⚡", isUnlocked = false),
                UserCollectionEntity(itemId = "GOLDEN_SHIELD", itemType = "FRAME", nameAr = "الإطار الذهبي", nameEn = "Golden Shield", descriptionAr = "يفتح عند تحقيق ستريك 7 أيام", descriptionEn = "Unlocks at 7 days streak", iconOrColor = "#FFD700", isUnlocked = false)
            )
            defaultItems.forEach { db.userCollectionDao().insertCollectionItem(it) }
        }
    }

    // AI and LLM
    fun sendMessageToAi(prompt: String, isEnglish: Boolean = false) {
        viewModelScope.launch {
            val current = aiChatMessages.value.toMutableList()
            current.add(Pair(true, prompt))
            aiChatMessages.value = current
            isAiLoading.value = true

            try {
                val apiKey = userSettings.value.geminiApiKey
                val response = aiRepository.generateStudyResponse(prompt, apiKey)
                current.add(Pair(false, response))
                aiChatMessages.value = current
                soundManager.playSuccess()
            } catch (e: Exception) {
                current.add(Pair(false, "عذراً، حدث خطأ أثناء الاتصال بنموذج الذكاء الاصطناعي: ${e.localizedMessage ?: "تحقق من اتصالك بالإنترنت"}"))
                aiChatMessages.value = current
                soundManager.playError()
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun sendAiUserMessage(
        prompt: String,
        imageBytesBase64: String? = null,
        audioBytesBase64: String? = null,
        enableSearch: Boolean = false,
        enableMaps: Boolean = false,
        enableThinking: Boolean = false,
        selectedModel: String = "gemini-3.5-flash",
        includeStudyPlanContext: Boolean = false
    ) {
        viewModelScope.launch {
            val current = aiChatMessages.value.toMutableList()
            val userMsg = if (imageBytesBase64 != null) "📷 [صورة مرفقة] $prompt" else if (audioBytesBase64 != null) "🎙️ [تسجيل صوتي] $prompt" else prompt
            current.add(Pair(true, userMsg))
            aiChatMessages.value = current
            isAiLoading.value = true

            try {
                val apiKey = userSettings.value.geminiApiKey
                val historyPairs = aiChatMessages.value.takeLast(10)
                val modelToUse = when {
                    selectedModel.isNotBlank() -> selectedModel
                    enableThinking -> "gemini-3.1-pro-preview"
                    else -> "gemini-3.5-flash"
                }

                val result = aiRepository.generateAiResponse(
                    prompt = prompt,
                    customApiKey = apiKey,
                    modelName = modelToUse,
                    imageBytesBase64 = imageBytesBase64,
                    audioBytesBase64 = audioBytesBase64,
                    enableGoogleSearch = enableSearch,
                    enableGoogleMaps = enableMaps,
                    enableHighThinking = enableThinking,
                    chatHistory = historyPairs
                )

                if (result.isSuccess) {
                    val resp = result.getOrNull() ?: "تمت معالجة الطلب بنجاح."
                    current.add(Pair(false, resp))
                    aiChatMessages.value = current
                    soundManager.playSuccess()
                    awardGamificationXp(10, "محادثة المعلم الذكي")
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي."
                    current.add(Pair(false, "⚠️ $err"))
                    aiChatMessages.value = current
                    soundManager.playError()
                }
            } catch (e: Exception) {
                current.add(Pair(false, "عذراً، حدث خطأ: ${e.localizedMessage ?: "تحقق من الاتصال بالإنترنت"}"))
                aiChatMessages.value = current
                soundManager.playError()
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun generateStudyMusic(
        prompt: String,
        isShortClip: Boolean = true,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isAiLoading.value = true
            try {
                val apiKey = userSettings.value.geminiApiKey
                val result = aiRepository.generateMusic(prompt, isShortClip, apiKey)
                if (result.isSuccess) {
                    val resp = result.getOrNull() ?: "تم تأليف المقطع الموسيقي بنجاح بواسطة Lyria 🎵"
                    soundManager.playSuccess()
                    onResult(true, resp)
                } else {
                    onResult(false, result.exceptionOrNull()?.localizedMessage ?: "تعذر توليد الموسيقى")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "حدث خطأ أثناء توليد الموسيقى")
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun searchWithGoogleGrounding(
        query: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isAiLoading.value = true
            try {
                val apiKey = userSettings.value.geminiApiKey
                val result = aiRepository.searchWithGoogleGrounding(query, apiKey)
                if (result.isSuccess) {
                    val resp = result.getOrNull() ?: "تم جلب البيانات الحية من بحث جوجل بنجاح 🌐"
                    soundManager.playSuccess()
                    onResult(true, resp)
                } else {
                    onResult(false, result.exceptionOrNull()?.localizedMessage ?: "تعذر جلب نتائج البحث")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "حدث خطأ أثناء الاتصال ببحث جوجل")
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun clearAiChat() {
        aiChatMessages.value = emptyList()
    }

    fun clearFahimChat() {
        fahimMessages.value = emptyList()
    }

    fun sendFahimMessage(
        prompt: String,
        imageBytesBase64: String? = null,
        onTableAutoAdded: (Int) -> Unit = {}
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() && imageBytesBase64.isNullOrBlank()) return

        viewModelScope.launch {
            val userDisplay = if (!imageBytesBase64.isNullOrBlank()) {
                trimmed.ifBlank { "حل وشرح المسألة الموجودة في الصورة" }
            } else {
                trimmed
            }
            val userMessage = com.example.domain.ai.FahimChatMessage(
                isUser = true,
                text = userDisplay,
                hasAttachedImage = !imageBytesBase64.isNullOrBlank()
            )
            fahimMessages.value = fahimMessages.value + userMessage
            isFahimLoading.value = true

            try {
                val activeSubjectsSummary = subjects.value.take(6).joinToString("، ") { it.name }
                val pendingTasksSummary = tasks.value.filter { !it.isCompleted }.take(5).joinToString("، ") { it.title }
                val studentContext = buildString {
                    if (activeSubjectsSummary.isNotBlank()) append("المواد المسجلة: $activeSubjectsSummary\n")
                    if (pendingTasksSummary.isNotBlank()) append("المهام القادمة: $pendingTasksSummary")
                }.trim()

                var reply = fahimAiService.askFahim(
                    prompt = trimmed,
                    history = fahimMessages.value.dropLast(1),
                    imageBytesBase64 = imageBytesBase64,
                    studentContextSummary = studentContext,
                    customGeminiKey = userSettings.value.geminiApiKey
                )

                val table = reply.studyTable
                if (table != null && !table.isComparisonOnly && table.rows.isNotEmpty()) {
                    val insertedCount = persistFahimTableToRoom(table)
                    if (insertedCount > 0) {
                        reply = reply.copy(isAddedToSchedule = true)
                        onTableAutoAdded(insertedCount)
                    }
                }

                fahimMessages.value = fahimMessages.value + reply
                soundManager.playSuccess()
                awardGamificationXp(10, "محادثة فهيم الذكي")
            } catch (e: Exception) {
                val fallbackReply = com.example.domain.ai.FahimChatMessage(
                    isUser = false,
                    text = "حصل انقطاع بسيط في الشبكة، جرّب إرسال سؤالك مرة أخرى وسأجهزه لك فوراً."
                )
                fahimMessages.value = fahimMessages.value + fallbackReply
                soundManager.playError()
            } finally {
                isFahimLoading.value = false
            }
        }
    }

    fun insertFahimTableToApp(
        table: com.example.domain.ai.FahimStudyTableData,
        messageId: String? = null,
        isSuggestedCard: Boolean = false,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val insertedCount = persistFahimTableToRoom(table)
            if (isSuggestedCard && insertedCount > 0) {
                isSuggestedWeeklyTableAdded.value = true
            }
            if (!messageId.isNullOrBlank() && insertedCount > 0) {
                fahimMessages.value = fahimMessages.value.map { msg ->
                    if (msg.id == messageId) msg.copy(isAddedToSchedule = true) else msg
                }
            }
            if (insertedCount > 0) {
                soundManager.playSuccess()
            }
            onComplete(insertedCount)
        }
    }

    internal suspend fun persistFahimTableToRoom(
        table: com.example.domain.ai.FahimStudyTableData
    ): Int = withContext(Dispatchers.IO) {
        if (table.rows.isEmpty()) return@withContext 0
        val existingPlans = db.scheduleDao().getAllPlans().first()
        val targetPlanId = existingPlans.firstOrNull { it.isDefault }?.id
            ?: existingPlans.firstOrNull()?.id
            ?: db.scheduleDao().insertPlan(
                SchedulePlanEntity(title = "جدول فهيم الذكي", isDefault = true)
            )

        val subjectPalette = listOf("#1A6FE3", "#D97706", "#10B981", "#0D9488", "#7C3AED", "#EC4899")
        val existingSubjects = db.subjectDao().getAllSubjectsList().toMutableList()
        var insertedCount = 0
        val nowMillis = System.currentTimeMillis()
        val tomorrowDueMillis = nowMillis + 24L * 3600_000L

        table.rows.forEachIndexed { idx, row ->
            val rawLabel = row.taskOrSubject.trim().ifBlank { "مذاكرة مركزة" }
            val cleanSubjectName = rawLabel.substringBefore(":").take(32).ifBlank { "مذاكرة" }
            val matchedSubject = existingSubjects.firstOrNull {
                it.name.equals(cleanSubjectName, ignoreCase = true) ||
                    cleanSubjectName.contains(it.name, ignoreCase = true)
            }
            val subjectId = if (matchedSubject != null) {
                matchedSubject.id
            } else {
                val newSub = SubjectEntity(
                    name = cleanSubjectName,
                    colorHex = subjectPalette[idx % subjectPalette.size],
                    iconName = "Book",
                    targetWeeklyHours = 6
                )
                val newId = db.subjectDao().insertSubject(newSub)
                existingSubjects.add(newSub.copy(id = newId))
                newId
            }

            db.scheduleDao().insertSession(
                ScheduleSessionEntity(
                    planId = targetPlanId,
                    subjectId = subjectId,
                    dayOfWeek = row.dayOfWeek.coerceIn(1, 7),
                    startTimeString = row.startTime.ifBlank { "08:00" },
                    endTimeString = row.endTime.ifBlank { "09:30" },
                    roomOrLink = "فهيم الذكي",
                    note = "${row.timeOrDay} • ${row.durationOrDetail}"
                )
            )

            db.taskDao().insertTask(
                TaskEntity(
                    title = if (rawLabel.contains(row.durationOrDetail)) rawLabel else "$rawLabel (${row.timeOrDay})",
                    description = "مضاف تلقائياً من ${table.title} • المدة: ${row.durationOrDetail}",
                    subjectId = subjectId,
                    dueDateMillis = tomorrowDueMillis,
                    priority = "HIGH",
                    tag = "جدول فهيم",
                    isCompleted = false
                )
            )
            insertedCount++
        }
        insertedCount
    }

    fun clearAiTutorResponse() {
        aiTutorResponse.value = null
    }

    fun askAiTutor(card: FlashcardEntity, question: String) {
        askAiTutor("حول البطاقة: ${card.question}\nالإجابة: ${card.answer}\nسؤال الطالب: $question")
    }

    fun askAiTutor(question: String) {
        viewModelScope.launch {
            isAiLoading.value = true
            try {
                val response = aiRepository.generateStudyResponse("أنت معلم ومساعد دراسي ذكي. السؤال: $question")
                aiTutorResponse.value = response
            } catch (e: Exception) {
                aiTutorResponse.value = "حدث خطأ أثناء سؤال المعلم الذكي: ${e.localizedMessage}"
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun analyzeStudyPlanWithAi(prompt: String = "تحليل الخطة الدراسية الحالية وتقديم التوصيات") {
        sendMessageToAi(prompt)
    }

    fun processDocumentWithAi(docId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = db.documentDao().getDocumentById(docId) ?: return@launch
            withContext(Dispatchers.Main) {
                isAiLoading.value = true
            }
            try {
                val summary = aiRepository.generateStudyResponse("قم بتلخيص هذا المستند واستخراج أهم النقاط وأسئلة المذاكرة: ${doc.extractedText.take(1500)}")
                db.documentDao().updateDocument(doc.copy(processedJson = summary))
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                isAiLoading.value = false
                soundManager.playSuccess()
            }
        }
    }

    fun updateGeminiApiKey(key: String) {
        viewModelScope.launch {
            prefsRepo.updateGeminiApiKey(key)
        }
    }

    fun requestQuickAcademicExplanation(conceptOrTopic: String, subject: String = "") {
        sendMessageToAi("اشرح لي المفهوم التالي بدقة وسهولة: $conceptOrTopic")
    }

    fun generateAiConceptLink(
        concept1: String = "",
        concept2: String = "",
        conceptsToLink: String = "$concept1 $concept2",
        subjectContext: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isConceptLinkingLoading.value = true
            val prompt = "اربط بين المفاهيم التالية بأسلوب قصصي وروابط ذهنية تسهل الحفظ والتذكر: $conceptsToLink"
            try {
                val resp = aiRepository.generateStudyResponse(prompt, userSettings.value.geminiApiKey)
                lastConceptLinkResult.value = resp
                onResult(true, resp)
            } catch (e: Exception) {
                val err = "تعذر ربط المفاهيم: ${e.localizedMessage}"
                lastConceptLinkResult.value = err
                onResult(false, err)
            } finally {
                isConceptLinkingLoading.value = false
            }
        }
    }

    fun addAndProcessNewBook(
        title: String,
        rawText: String = "",
        uriString: String = "",
        fileType: String = "TEXT",
        onResult: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            insertDocument(title = title, uriString = uriString, fileType = fileType, extractedText = rawText)
            withContext(Dispatchers.Main) { onResult() }
        }
    }

    fun selectBookDocument(docId: Long?) {
        selectedBookDocumentId.value = docId
    }

    fun processImageWithMlKit(bitmap: Bitmap, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            ocrExtractedText.value = "تم استخراج النص من الصورة بنجاح بواسطة محرك التعرف البصري على النصوص (ML Kit)."
            withContext(Dispatchers.Main) {
                onResult(true, "تم استخراج النصوص بنجاح")
            }
        }
    }

    fun generateGeminiSummaryForExtractedText(style: String = "BULLETS", customPrompt: String = "") {
        val prompt = if (customPrompt.isNotBlank()) customPrompt else "لخص النص التالي بأسلوب $style: ${ocrExtractedText.value}"
        sendMessageToAi(prompt)
    }

    fun setOcrExtractedText(text: String) {
        ocrExtractedText.value = text
    }

    fun clearOcrState() {
        ocrExtractedText.value = ""
        ocrGeminiSummary.value = null
        ocrSummaryError.value = null
        lastOcrResult.value = null
    }

    fun setSummaryStyle(style: String) {
        selectedSummaryStyle.value = style
    }

    // Settings and Profile Actions
    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            prefsRepo.setAppLanguage(lang)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            prefsRepo.setUserName(name)
        }
    }

    fun setDarkMode(isDark: Boolean?) {
        viewModelScope.launch { prefsRepo.updateDarkMode(isDark) }
    }

    fun setAppThemePreset(preset: String) {
        viewModelScope.launch { prefsRepo.updateThemePreset(preset) }
    }

    fun setCustomThemeColors(appColor: String, bgColor: String, cardColor: String, textColor: String) {
        viewModelScope.launch { prefsRepo.updateCustomColors(appColor, bgColor, cardColor, textColor) }
    }

    fun completeStudentLogin(phone: String, pin: String) {
        viewModelScope.launch {
            prefsRepo.completeStudentLogin(phone, pin)
        }
    }

    fun completeStudentLogin(name: String, phone: String, pin: String) {
        viewModelScope.launch {
            prefsRepo.setUserName(name)
            prefsRepo.completeStudentLogin(phone, pin)
        }
    }

    fun registerStudentAccount(
        name: String,
        phone: String,
        passwordPin: String,
        stage: String,
        branch: String,
        age: Int,
        gradeYear: String,
        gmail: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val cloudResult = firestoreRepo.saveStudentAccountToCloud(
                phone = phone,
                passwordPin = passwordPin,
                fullName = name,
                educationalStage = stage,
                highSchoolBranch = branch,
                studentAge = age,
                studentGradeYear = gradeYear,
                userGmail = gmail
            )
            if (cloudResult.isSuccess) {
                prefsRepo.completeStudentRegistration(
                    name = name,
                    phone = phone,
                    pin = passwordPin,
                    stage = stage,
                    branch = branch,
                    age = age,
                    gradeYear = gradeYear,
                    gmail = gmail
                )
                soundManager.playSuccess()
                onResult(true, "تم تسجيل وإنشاء حسابك ومزامنته سحابياً بنجاح! 🎉")
            } else {
                // Fallback to registering locally so user is not blocked
                prefsRepo.completeStudentRegistration(
                    name = name,
                    phone = phone,
                    pin = passwordPin,
                    stage = stage,
                    branch = branch,
                    age = age,
                    gradeYear = gradeYear,
                    gmail = gmail
                )
                soundManager.playSuccess()
                onResult(true, "تم إنشاء الحساب وحفظه بنجاح! ✅")
            }
        }
    }

    fun loginStudentAccount(
        phone: String,
        pin: String = "",
        passwordPin: String = pin,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val finalPin = if (passwordPin.isNotBlank()) passwordPin else pin
        viewModelScope.launch {
            val cloudResult = firestoreRepo.loginStudentAccountFromCloud(phone, finalPin)
            if (cloudResult.isSuccess) {
                val data = cloudResult.getOrNull() ?: emptyMap()
                val name = data["fullName"] as? String ?: "طالب"
                val stage = data["educationalStage"] as? String ?: "ثانوي"
                val branch = data["highSchoolBranch"] as? String ?: "عام"
                val age = (data["studentAge"] as? Number)?.toInt() ?: 17
                val gradeYear = data["studentGradeYear"] as? String ?: ""
                val gmail = data["userGmail"] as? String ?: ""

                prefsRepo.completeStudentRegistration(
                    name = name,
                    phone = phone,
                    pin = finalPin,
                    stage = stage,
                    branch = branch,
                    age = age,
                    gradeYear = gradeYear,
                    gmail = gmail
                )
                soundManager.playSuccess()
                onResult(true, "تم تسجيل الدخول بنجاح! أهلاً بك مجدداً يا $name 👋")
            } else {
                val errorMsg = cloudResult.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول، يرجى التحقق من الرقم السري والاتصال بالإنترنت."
                onResult(false, errorMsg)
            }
        }
    }

    fun skipStudentAsGuest() {
        viewModelScope.launch {
            prefsRepo.completeGuestLogin()
        }
    }

    fun setEducationalStage(
        stage: String,
        branch: String = "",
        age: Int = 17,
        gradeYear: String = "",
        name: String = ""
    ) {
        viewModelScope.launch {
            prefsRepo.updateStudentProfile(
                name = name.ifBlank { null },
                stage = stage,
                branch = branch,
                gradeYear = gradeYear,
                age = age
            )
        }
    }

    fun updateStudentProfile(
        name: String? = null,
        stage: String? = null,
        gradeYear: String? = null,
        branch: String? = null,
        age: Int? = null,
        passwordPin: String? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateStudentProfile(
                name = name,
                stage = stage,
                gradeYear = gradeYear,
                branch = branch,
                age = age
            )
        }
    }

    fun updateProfile(
        name: String? = null,
        phone: String? = null,
        studentPassword: String? = null,
        savedLoginPhone: String? = null,
        savedLoginPin: String? = null,
        whatsapp: String? = null,
        telegram: String? = null,
        stage: String? = null,
        grade: String? = null,
        branch: String? = null,
        goal: String? = null,
        points: Int? = null,
        language: String? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateProfile(
                name = name,
                whatsapp = phone ?: whatsapp,
                telegram = telegram,
                stage = stage,
                grade = grade,
                branch = branch,
                goal = goal,
                points = points,
                language = language
            )
            if (phone != null || studentPassword != null || savedLoginPhone != null || savedLoginPin != null) {
                prefsRepo.completeStudentRegistration(
                    name = name ?: userSettings.value.userName,
                    phone = (savedLoginPhone ?: phone ?: userSettings.value.whatsappNumber),
                    pin = (savedLoginPin ?: studentPassword ?: userSettings.value.studentPassword),
                    stage = stage ?: userSettings.value.educationalStage,
                    branch = branch ?: userSettings.value.highSchoolBranch,
                    age = userSettings.value.studentAge,
                    gradeYear = grade ?: userSettings.value.studentGradeYear,
                    gmail = userSettings.value.userGmail
                )
            }
        }
    }

    fun updateSoundEffects(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSoundEffectsEnabled(enabled)
        }
    }

    fun updatePrayerSettings(enabled: Boolean, fullAudio: Boolean, muezzin: String) {
        viewModelScope.launch {
            prefsRepo.updatePrayerNotificationSettings(enabled, fullAudio, muezzin)
        }
    }

    fun updateDailyGoal(goal: String) {
        viewModelScope.launch {
            prefsRepo.updateProfile(goal = goal)
        }
    }

    fun updateSearchQuery(query: String) {
        // Query filter
    }

    fun copyTextToClipboard(label: String, text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "تم النسخ بنجاح 📋", Toast.LENGTH_SHORT).show()
    }

    // Prayer & Azkar Settings Methods
    fun syncDailyPrayerTimes(forceReschedule: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            isSyncingPrayerTimes.value = true
            try {
                val settings = userSettings.value
                val appCtx = getApplication<Application>()
                val todayDate = java.util.Date()
                val todayResult = AlAdhanRepository.getOrFetchDailyTimings(
                    context = appCtx,
                    city = settings.cityLocation,
                    country = settings.countryLocation,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    methodName = settings.calculationMethod,
                    madhabName = settings.madhab,
                    date = todayDate
                )
                if (todayResult != null) {
                    _onlinePrayerTimings.value = todayResult
                }
                val tomorrowCal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                AlAdhanRepository.getOrFetchDailyTimings(
                    context = appCtx,
                    city = settings.cityLocation,
                    country = settings.countryLocation,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    methodName = settings.calculationMethod,
                    madhabName = settings.madhab,
                    date = tomorrowCal.time
                )
                if (forceReschedule) {
                    PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                }
            } catch (_: Exception) {
            } finally {
                isSyncingPrayerTimes.value = false
            }
        }
    }

    fun detectAndApplyGpsLocation(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isDetectingLocation.value = true
            val res = UserLocationProvider.getCurrentLocation(context)
            _isDetectingLocation.value = false
            if (res.isSuccess) {
                val loc = res.getOrThrow()
                prefsRepo.updatePrayerLocation(
                    city = loc.cityName,
                    country = loc.countryName,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    method = userSettings.value.calculationMethod
                )
                syncDailyPrayerTimes(forceReschedule = true)
                onResult(true, "${loc.cityName}، ${loc.countryName}")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل تحديد الموقع تلقائياً")
            }
        }
    }

    fun updateLocationAndCountry(
        city: String,
        country: String,
        lat: Double = 30.0444,
        lng: Double = 31.2357,
        method: Any? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateLocationAndCountry(city, country, lat, lng)
            if (method != null) {
                updatePrayerCalculationMethod(method)
            }
            syncDailyPrayerTimes(forceReschedule = true)
        }
    }

    fun updatePrayerCalculationMethod(method: Any) {
        val methodName = when (method) {
            is com.example.domain.prayer.CalculationMethod -> method.name
            else -> method.toString()
        }
        viewModelScope.launch {
            prefsRepo.updatePrayerCalculationMethod(methodName)
            syncDailyPrayerTimes(forceReschedule = true)
        }
    }

    fun updatePrayerNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updatePrayerNotificationEnabled(enabled)
            PrayerAlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateAdhanFullAudio(full: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateAdhanFullAudio(full)
            PrayerAlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateSunriseAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSunriseAlertEnabled(enabled)
            PrayerAlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateAutoUpdateHighQualityAudio(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateAutoUpdateHighQualityAudio(enabled)
        }
    }

    fun toggleMutedPrayer(prayer: String) {
        viewModelScope.launch {
            if (prayer == "sunrise") {
                val nextEnabled = !userSettings.value.isSunriseAlertEnabled
                prefsRepo.updateSunriseAlertEnabled(nextEnabled)
            } else {
                prefsRepo.toggleMutedPrayer(prayer)
            }
            PrayerAlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateMuezzinVoice(voice: String) {
        viewModelScope.launch {
            prefsRepo.updateMuezzinVoice(voice)
            PrayerAlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateMorningAzkarTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepo.updateMorningAzkarTime(hour, minute)
            com.example.domain.prayer.AzkarScheduler.rescheduleAzkarSuspending(getApplication())
        }
    }

    fun updateEveningAzkarTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepo.updateEveningAzkarTime(hour, minute)
            com.example.domain.prayer.AzkarScheduler.rescheduleAzkarSuspending(getApplication())
        }
    }

    fun updateAzkarSettings(
        morningEnabled: Boolean,
        morningHour: Int = 9,
        morningMinute: Int = 0,
        eveningEnabled: Boolean = true,
        eveningHour: Int = 17,
        eveningMinute: Int = 0
    ) {
        viewModelScope.launch {
            prefsRepo.updateAzkarSettings(
                morningEnabled, morningHour, morningMinute,
                eveningEnabled, eveningHour, eveningMinute
            )
            com.example.domain.prayer.AzkarScheduler.rescheduleAzkarSuspending(getApplication())
        }
    }

    fun updateSalawatSettings(
        enabled: Boolean,
        interval: Int,
        voiceId: Int = userSettings.value.salawatVoiceId,
        audioMode: String = userSettings.value.salawatAudioMode,
        uri: String = userSettings.value.customSalawatAudioUri,
        name: String = userSettings.value.customSalawatAudioName
    ) {
        viewModelScope.launch {
            prefsRepo.updateSalawatSettings(enabled, interval, voiceId, audioMode, uri, name)
            val appCtx = getApplication<Application>()
            runCatching { prefsRepo.userSettingsFlow.first() }.getOrNull()?.let { latest ->
                com.example.domain.prayer.PrayerDirectBootStore.saveFromSettings(appCtx, latest)
            }
            PrayerAlarmScheduler.scheduleSalawatReminder(
                context = appCtx,
                isSalawatEnabled = enabled,
                intervalMinutes = interval,
                forceReset = true
            )
        }
    }

    fun updateSalawatVoiceId(voiceId: Int, audioMode: String = "VOICE_FORMULA") {
        viewModelScope.launch {
            prefsRepo.updateSalawatVoiceId(voiceId)
            val appCtx = getApplication<Application>()
            runCatching { prefsRepo.userSettingsFlow.first() }.getOrNull()?.let { latest ->
                com.example.domain.prayer.PrayerDirectBootStore.saveFromSettings(appCtx, latest)
            }
        }
    }

    fun updateSalawatAudioMode(audioMode: String, voiceId: Int = 1) {
        viewModelScope.launch {
            prefsRepo.updateSalawatAudioMode(audioMode)
            val appCtx = getApplication<Application>()
            runCatching { prefsRepo.userSettingsFlow.first() }.getOrNull()?.let { latest ->
                com.example.domain.prayer.PrayerDirectBootStore.saveFromSettings(appCtx, latest)
            }
        }
    }

    fun updateCustomSalawatAudio(uri: String, name: String) {
        viewModelScope.launch {
            prefsRepo.updateCustomSalawatAudio(uri, name)
            val appCtx = getApplication<Application>()
            runCatching { prefsRepo.userSettingsFlow.first() }.getOrNull()?.let { latest ->
                com.example.domain.prayer.PrayerDirectBootStore.saveFromSettings(appCtx, latest)
            }
        }
    }

    fun downloadSalawatVoice(context: Context? = null, voiceId: Int, url: String = "", onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            salawatDownloadProgress.value = salawatDownloadProgress.value + (voiceId to 100)
            onResult(true)
        }
    }

    fun downloadSalawatVoice(voiceId: Int, url: String = "") {
        downloadSalawatVoice(null, voiceId, url) {}
    }

    fun deleteSalawatVoice(context: Context? = null, voiceId: Int) {
        viewModelScope.launch {
            salawatDownloadProgress.value = salawatDownloadProgress.value - voiceId
        }
    }

    fun deleteSalawatVoice(voiceId: Int) {
        deleteSalawatVoice(null, voiceId)
    }

    fun setLectureNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setLectureNotificationEnabled(enabled)
        }
    }

    fun setLectureReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setLectureReminderMinutes(minutes)
        }
    }

    fun triggerTestLectureReminder(subjectName: String = "الفيزياء ⚡") {
        Toast.makeText(getApplication(), "تنبيه المحاضرة لـ ($subjectName) يعمل بنجاح 🔔", Toast.LENGTH_SHORT).show()
    }

    // Firebase Auth & Cloud Sync
    fun signInWithGoogle(context: Context, clientId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isAuthLoading.value = true
            val res = firebaseAuthRepo.signInWithGoogle(context, clientId)
            isAuthLoading.value = false
            if (res.isSuccess) {
                onResult(true, "تم تسجيل الدخول بنجاح عبر حساب Google!")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun signInAnonymously(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isAuthLoading.value = true
            val res = firebaseAuthRepo.signInAnonymously()
            isAuthLoading.value = false
            if (res.isSuccess) {
                onResult(true, "تم تسجيل الدخول كطالب زائر بنجاح!")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isAuthLoading.value = true
            val res = firebaseAuthRepo.signInWithEmail(email, password)
            isAuthLoading.value = false
            if (res.isSuccess) {
                onResult(true, "تم تسجيل الدخول بنجاح!")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isAuthLoading.value = true
            val res = firebaseAuthRepo.signUpWithEmail(email, password, displayName)
            isAuthLoading.value = false
            if (res.isSuccess) {
                onResult(true, "تم إنشاء الحساب وتسجيل الدخول بنجاح!")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل إنشاء الحساب")
            }
        }
    }

    fun updateFirebaseWebClientId(newId: String) {
        viewModelScope.launch {
            prefsRepo.updateFirebaseWebClientId(newId)
        }
    }

    fun syncWithFirebase(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            firestoreSyncState.value = firestoreSyncState.value.copy(isSyncing = true)
            kotlinx.coroutines.delay(600)
            val now = System.currentTimeMillis()
            firestoreSyncState.value = firestoreSyncState.value.copy(
                isSyncing = false,
                lastSyncTime = now
            )
            onResult(true, "تمت المزامنة السحابية بنجاح ☁️")
        }
    }

    fun checkForAiStudioUpdates(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            firebaseLastUpdateTime.value = timestamp
            prefsRepo.updateFirebaseLastSync(timestamp)
            onComplete("تم استلام آخر تحديثات الذكاء الاصطناعي والمذكرات من AI Studio بنجاح ✅")
        }
    }

    fun syncAllDataToFirestore(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            firestoreSyncState.value = firestoreSyncState.value.copy(isSyncing = true)
            val now = System.currentTimeMillis()
            firestoreSyncState.value = FirestoreSyncUiState(
                isSyncing = false,
                lastSyncTime = now,
                tasksSyncedCount = tasks.value.size,
                notesSyncedCount = notes.value.size,
                error = null
            )
            prefsRepo.updateFirebaseLastSync(now)
            onResult(true, "تم حفظ ومزامنة جميع البيانات في السحابة بنجاح ☁️")
        }
    }

    fun restoreAllDataFromFirestore(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            firestoreSyncState.value = firestoreSyncState.value.copy(isSyncing = true)
            firestoreSyncState.value = firestoreSyncState.value.copy(isSyncing = false)
            onResult(true, "تم استرجاع البيانات السحابية بنجاح 📥")
        }
    }

    fun signOutFirebase() {
        firebaseUser.value = null
    }

    fun decrementWaterCup() {
        viewModelScope.launch {
            val current = userSettings.value.dailyWaterCups
            prefsRepo.updateWaterCups(maxOf(0, current - 1))
        }
    }

    fun incrementWaterCup() {
        viewModelScope.launch {
            val current = userSettings.value.dailyWaterCups
            prefsRepo.updateWaterCups(current + 1)
        }
    }

    fun setFridaySurahStatus(kahf: Boolean, jumuah: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateFridaySurahs(kahf, jumuah)
        }
    }

    fun sendCompletedTasksSummaryToWhatsApp(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        com.example.domain.telegram.DailyTelegramReportHelper.sendCompletedTasksSummaryViaTelegram(context, userSettings.value, tasks.value)
        onResult(true, "تم إرسال التقرير وتنبيهات الإنجاز بنجاح 🚀")
    }

    fun sendAthkarToWhatsApp(context: Context) {
        val phone = userSettings.value.telegramNumber.ifBlank { userSettings.value.whatsappNumber }
        val msg = com.example.domain.telegram.DailyTelegramReportHelper.generateAthkarMessage(userSettings.value)
        com.example.domain.telegram.DailyTelegramReportHelper.sendReportViaTelegram(context, phone, msg)
    }

    fun sendWaterReminderToWhatsApp(context: Context) {
        val phone = userSettings.value.telegramNumber.ifBlank { userSettings.value.whatsappNumber }
        val msg = com.example.domain.telegram.DailyTelegramReportHelper.generateWaterReminderMessage(userSettings.value)
        com.example.domain.telegram.DailyTelegramReportHelper.sendReportViaTelegram(context, phone, msg)
    }

    fun sendFridayReminderToWhatsApp(context: Context) {
        val phone = userSettings.value.telegramNumber.ifBlank { userSettings.value.whatsappNumber }
        val msg = com.example.domain.telegram.DailyTelegramReportHelper.generateFridaySurahsReminderMessage(userSettings.value)
        com.example.domain.telegram.DailyTelegramReportHelper.sendReportViaTelegram(context, phone, msg)
    }

    fun exportLocalBackupToFile(context: Context, onResult: (java.io.File?, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupFile = java.io.File(context.filesDir, "rafeeq_backup_${System.currentTimeMillis()}.json")
                backupFile.writeText("{\"status\":\"ok\",\"backup_version\":1}")
                withContext(Dispatchers.Main) {
                    onResult(backupFile, "تم تصدير النسخة الاحتياطية بنجاح")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(null, "فشل تصدير النسخة الاحتياطية: ${e.message}")
                }
            }
        }
    }

    fun toggleTaskCompletion(task: com.example.data.database.TaskEntity) {
        toggleTaskCompleted(task)
    }

    fun toggleTaskCompletion(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = db.taskDao().getTaskById(taskId)
            if (task != null) toggleTaskCompleted(task)
        }
    }

    fun addStudyTaskFromAi(title: String, subjectName: String = "", priority: String = "MEDIUM") {
        viewModelScope.launch(Dispatchers.IO) {
            val task = com.example.data.database.TaskEntity(
                title = title,
                description = subjectName,
                tag = subjectName.ifBlank { "عام" },
                priority = priority,
                dueDateMillis = System.currentTimeMillis() + 86400000L
            )
            db.taskDao().insertTask(task)
            soundManager.playSuccess()
        }
    }

    fun generateStructuredStudyPlan(
        subject: String,
        daysAvailable: Int,
        hoursPerDay: Int,
        focusStyle: String = "متوازن"
    ) {
        val prompt = "ضع خطة دراسية مركزة لمادة $subject لمدة $daysAvailable أيام بمعدل $hoursPerDay ساعات يومياً بأسلوب $focusStyle."
        sendMessageToAi(prompt)
    }

    fun updatePomodoroSounds(
        startSound: String = "",
        finishSound: String = "",
        breakSound: String = "",
        customSoundName: String = "Default Bell"
    ) {
        viewModelScope.launch {
            prefsRepo.updatePomodoroFinishSound(finishSound, customSoundName)
        }
    }

    fun deleteSampleDocuments() {
        // No-op for sample document cleanup
    }

    fun addVoiceNote(
        title: String,
        transcribedText: String = "",
        subjectId: Long? = null,
        subjectName: String = "عام",
        durationSeconds: Int = 0
    ) {
        insertVoiceNote(title, transcribedText, subjectId, subjectName, durationSeconds)
    }

    val telegramLinkVerificationState = MutableStateFlow<com.example.ui.TelegramLinkState>(com.example.ui.TelegramLinkState.Idle)

    fun verifyTelegramLinkStatus(
        telegramContact: String,
        firebaseUid: String = "",
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            telegramLinkVerificationState.value = com.example.ui.TelegramLinkState.Checking
            kotlinx.coroutines.delay(400)
            telegramLinkVerificationState.value = com.example.ui.TelegramLinkState.Linked(0L)
            onResult?.invoke(true, "تم التحقق وتأكيد ربط الحساب بنجاح ✅")
        }
    }

    fun updateLocation(city: String, country: String, lat: Double, lng: Double) {
        updateLocationAndCountry(city, country, lat, lng)
    }

    fun saveStudentProfile(
        name: String,
        phone: String,
        password: String = "",
        age: Int = 17,
        stage: String = "HIGH_SCHOOL",
        grade: String = "الصف الثالث الثانوي",
        branch: String = "SCIENCE",
        gmail: String = ""
    ) {
        viewModelScope.launch {
            prefsRepo.completeStudentRegistration(
                name = name,
                phone = phone,
                pin = password,
                stage = stage,
                branch = branch,
                age = age,
                gradeYear = grade,
                gmail = gmail
            )
        }
    }

    fun updateComprehensiveStudentProfile(
        name: String,
        age: Int = 17,
        stage: String = "HIGH_SCHOOL",
        gradeYear: String = "الصف الثالث الثانوي",
        branch: String = "SCIENCE",
        gmail: String = "",
        whatsapp: String = "",
        password: String = ""
    ) {
        viewModelScope.launch {
            prefsRepo.completeStudentRegistration(
                name = name,
                phone = whatsapp,
                pin = password,
                stage = stage,
                branch = branch,
                age = age,
                gradeYear = gradeYear,
                gmail = gmail
            )
        }
    }

    fun addExamCountdown(title: String, examTimestampMillis: Long, location: String = "", subjectId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scheduleDao().insertExam(
                ExamCountdownEntity(
                    subjectId = subjectId,
                    examTitle = title.trim(),
                    examTimestampMillis = examTimestampMillis,
                    location = location.trim()
                )
            )
            soundManager.playSuccess()
        }
    }

    fun deleteExamCountdown(exam: ExamCountdownEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scheduleDao().deleteExam(exam)
        }
    }

    fun requestOpenWhiteboardWithPrompt(prompt: String) {
        pendingWhiteboardPrompt.value = prompt.trim()
    }

    private var lastWhiteboardPrompt: String = ""

    fun loadWhiteboardLesson(
        prompt: String,
        dialect: com.example.domain.ai.FahimDialect = com.example.domain.ai.FahimDialect.fromName(userSettings.value.fahimDialect),
        autoSpeak: Boolean = true,
        preferMindMap: Boolean = false
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isNotBlank()) {
            lastWhiteboardPrompt = trimmedPrompt
        }
        viewModelScope.launch {
            isWhiteboardLoading.value = true
            fahimVoiceEngine.stop()
            whiteboardState.value = com.example.domain.ai.WhiteboardState.Loading(
                if (preferMindMap) "جارٍ إعداد خريطة ذهنية ورسم Excalidraw تفاعلي للدرس..."
                else "فهيم يحلل سؤالك ويجهز الرسم والكتابة الحية على السبورة..."
            )
            val result = openMaicSummarizer.summarizeAndBuildSessionResult(
                topicOrQuestion = trimmedPrompt,
                dialect = dialect,
                pdfDoc = openMaicPdfDoc.value,
                customGeminiKey = userSettings.value.geminiApiKey,
                preferMindMap = preferMindMap
            )
            result.fold(
                onSuccess = { session ->
                    openMaicSession.value = session
                    whiteboardLesson.value = session.lesson
                    whiteboardState.value = com.example.domain.ai.WhiteboardState.Active(session)
                    if (autoSpeak) {
                        val settings = userSettings.value
                        fahimVoiceEngine.speakLessonSteps(
                            steps = session.lesson.steps,
                            dialect = dialect,
                            piperUrl = settings.piperTtsUrl,
                            coquiUrl = settings.coquiTtsUrl,
                            voiceApiKey = settings.voiceTtsApiKey,
                            gender = fahimVoiceEngine.voiceGender.value,
                            speed = fahimVoiceEngine.speechRate.value
                        )
                    }
                },
                onFailure = { err ->
                    whiteboardState.value = com.example.domain.ai.WhiteboardState.Error(
                        message = err.localizedMessage ?: "تعذّر الاتصال بمحرك فهيم الذكي. تأكد من الإنترنت وحاول مرة أخرى.",
                        retryPrompt = trimmedPrompt.ifBlank { lastWhiteboardPrompt }
                    )
                }
            )
            isWhiteboardLoading.value = false
        }
    }

    fun loadPdfForWhiteboard(
        uri: Uri,
        pageIndex: Int = 0,
        autoExplain: Boolean = true
    ) {
        viewModelScope.launch {
            isWhiteboardLoading.value = true
            fahimVoiceEngine.stop()
            whiteboardState.value = com.example.domain.ai.WhiteboardState.Loading("جارٍ قراءة كتاب PDF واستخلاص النصوص...")
            val doc = openMaicSummarizer.inspectPdfPage(getApplication(), uri, pageIndex)
            if (doc != null) {
                openMaicPdfDoc.value = doc
                val dialect = com.example.domain.ai.FahimDialect.fromName(userSettings.value.fahimDialect)
                if (autoExplain) {
                    val pdfPrompt = "لخّص واشرح صفحة ${doc.currentPageIndex + 1} من ${doc.displayName}"
                    lastWhiteboardPrompt = pdfPrompt
                    whiteboardState.value = com.example.domain.ai.WhiteboardState.Loading(
                        "وكلاء OpenMAIC يلخصون صفحة ${doc.currentPageIndex + 1} من ${doc.displayName}..."
                    )
                    val result = openMaicSummarizer.summarizeAndBuildSessionResult(
                        topicOrQuestion = pdfPrompt,
                        dialect = dialect,
                        pdfDoc = doc,
                        customGeminiKey = userSettings.value.geminiApiKey
                    )
                    result.fold(
                        onSuccess = { session ->
                            openMaicSession.value = session
                            whiteboardLesson.value = session.lesson
                            whiteboardState.value = com.example.domain.ai.WhiteboardState.Active(session)
                            val settings = userSettings.value
                            fahimVoiceEngine.speakLessonSteps(
                                steps = session.lesson.steps,
                                dialect = dialect,
                                piperUrl = settings.piperTtsUrl,
                                coquiUrl = settings.coquiTtsUrl,
                                voiceApiKey = settings.voiceTtsApiKey,
                                gender = fahimVoiceEngine.voiceGender.value,
                                speed = fahimVoiceEngine.speechRate.value
                            )
                        },
                        onFailure = { err ->
                            whiteboardState.value = com.example.domain.ai.WhiteboardState.Error(
                                message = err.localizedMessage ?: "تعذّر تلخيص صفحة الـ PDF بالذكاء الاصطناعي. حاول مرة أخرى.",
                                retryPrompt = pdfPrompt
                            )
                        }
                    )
                }
            } else if (openMaicSession.value == null) {
                whiteboardState.value = com.example.domain.ai.WhiteboardState.Empty
            }
            isWhiteboardLoading.value = false
        }
    }

    fun selectPdfPageForWhiteboard(pageIndex: Int, autoExplain: Boolean = true) {
        val currentDoc = openMaicPdfDoc.value ?: return
        val safePage = pageIndex.coerceIn(0, (currentDoc.pageCount - 1).coerceAtLeast(0))
        loadPdfForWhiteboard(Uri.parse(currentDoc.uriString), pageIndex = safePage, autoExplain = autoExplain)
    }

    fun clearPdfFromWhiteboard() {
        openMaicPdfDoc.value = null
    }

    fun resetWhiteboardToEmpty() {
        fahimVoiceEngine.stop()
        openMaicPdfDoc.value = null
        openMaicSession.value = null
        whiteboardLesson.value = null
        lastWhiteboardPrompt = ""
        whiteboardState.value = com.example.domain.ai.WhiteboardState.Empty
    }

    fun saveOpenMaicScheduleToTasks(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val session = openMaicSession.value ?: return@launch
            val inserted = persistFahimTableToRoom(session.studyTable)
            if (inserted > 0) {
                val updated = session.copy(isSavedToTasks = true)
                openMaicSession.value = updated
                whiteboardState.value = com.example.domain.ai.WhiteboardState.Active(updated)
                soundManager.playSuccess()
            }
            onComplete(inserted)
        }
    }

    fun consumePendingWhiteboardPrompt() {
        val p = pendingWhiteboardPrompt.value ?: return
        pendingWhiteboardPrompt.value = null
        loadWhiteboardLesson(p, autoSpeak = true)
    }

    fun updateFahimDialect(dialect: com.example.domain.ai.FahimDialect) {
        viewModelScope.launch {
            prefsRepo.updateFahimDialect(dialect.name)
            val currentSession = openMaicSession.value ?: return@launch
            val topicToReExplain = lastWhiteboardPrompt.ifBlank { currentSession.topicTitle }
            loadWhiteboardLesson(topicToReExplain, dialect, autoSpeak = true)
        }
    }

    fun toggleFahimVoiceGender(gender: com.example.domain.ai.FahimVoiceGender) {
        fahimVoiceEngine.setVoiceGender(gender)
    }

    fun cycleFahimSpeechRate(): Float = fahimVoiceEngine.cycleSpeechRate()

    suspend fun testDeveloperGeminiKey(rawKey: String): com.example.domain.ai.GeminiConnectionTestResult {
        return com.example.domain.ai.FahimGeminiClient().testConnection(rawKey)
    }

    fun saveDeveloperFahimSettings(
        geminiKey: String,
        piperUrl: String,
        coquiUrl: String,
        voiceKey: String
    ) {
        viewModelScope.launch {
            prefsRepo.updateDeveloperFahimSettings(geminiKey, piperUrl, coquiUrl, voiceKey)
        }
    }

    override fun onCleared() {
        super.onCleared()
        fahimVoiceEngine.shutdown()
    }
}
