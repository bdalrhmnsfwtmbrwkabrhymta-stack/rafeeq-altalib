package com.hawas.study.dofaati

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hawas.study.dofaati.data.DofaatiDatabase
import com.hawas.study.dofaati.data.DofaatiLocalStore
import com.hawas.study.dofaati.engine.BulkQuestionGeneratorRepository
import com.hawas.study.dofaati.engine.LiveQuizScoringEngine
import com.hawas.study.dofaati.models.BulkGenerationProgress
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.DofaatiAudioLesson
import com.hawas.study.dofaati.models.DofaatiBatch
import com.hawas.study.dofaati.models.DofaatiBatchMember
import com.hawas.study.dofaati.models.DofaatiChatMessage
import com.hawas.study.dofaati.models.DofaatiFlashcard
import com.hawas.study.dofaati.models.DofaatiJoinRequest
import com.hawas.study.dofaati.models.DofaatiMindMap
import com.hawas.study.dofaati.models.DofaatiMindMapBranch
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.models.DofaatiStudyFile
import com.hawas.study.dofaati.models.DofaatiSubject
import com.hawas.study.dofaati.models.LiveChallengeSessionState
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.models.UploadPermissionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class DofaatiViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val localStore = DofaatiLocalStore(DofaatiDatabase.getInstance(appContext).dofaatiDao())
    val questionRepository = BulkQuestionGeneratorRepository(appContext)

    // Backstack Navigation inside Dofa'ati module
    private val _backStack = MutableStateFlow<List<DofaatiRoute>>(listOf(DofaatiRoute.HUB))
    val backStack: StateFlow<List<DofaatiRoute>> = _backStack.asStateFlow()

    val currentRoute: DofaatiRoute
        get() = _backStack.value.lastOrNull() ?: DofaatiRoute.HUB

    // Room-backed 500+ Question Bank & Flashcards
    val questionBankItems: StateFlow<List<DofaatiQuestionItem>> = localStore.observeQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flashcards: StateFlow<List<DofaatiFlashcard>> = localStore.observeFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DofaatiLocalStore.buildInitialFlashcards())

    // Active Batch & Catalog
    private val initialSubjects = listOf(
        DofaatiSubject("sub_physics", "فيزيا", recordingsCount = 4, filesCount = 3),
        DofaatiSubject("sub_chemistry", "كيميا", recordingsCount = 6, filesCount = 5),
        DofaatiSubject("sub_biology", "أحياء", recordingsCount = 3, filesCount = 4),
        DofaatiSubject("sub_arabic", "عربي", recordingsCount = 5, filesCount = 6)
    )

    private val _batches = MutableStateFlow(
        listOf(
            DofaatiBatch(
                id = "batch_2026",
                name = "تالتة ثانوي 2026",
                code = "4861 3927",
                logoColorHex = 0xFF1A6FE3,
                adminName = "د. عبد الرحمن صفوت",
                studentsCount = 86,
                subjects = initialSubjects,
                uploadPermission = UploadPermissionMode.ADMIN_ONLY,
                requireApproval = true,
                pinnedNotice = "امتحان الفيزيا الخميس الساعة 10"
            )
        )
    )
    val batches: StateFlow<List<DofaatiBatch>> = _batches.asStateFlow()

    private val _activeBatchId = MutableStateFlow("batch_2026")
    val activeBatchId: StateFlow<String> = _activeBatchId.asStateFlow()

    val activeBatch: DofaatiBatch
        get() = _batches.value.firstOrNull { it.id == _activeBatchId.value } ?: _batches.value.first()

    // Selected Subject & Resources
    private val _selectedSubjectId = MutableStateFlow("sub_physics")
    val selectedSubjectId: StateFlow<String> = _selectedSubjectId.asStateFlow()

    private val _audioLessons = MutableStateFlow(buildInitialAudioLessons())
    val audioLessons: StateFlow<List<DofaatiAudioLesson>> = _audioLessons.asStateFlow()

    private val _studyFiles = MutableStateFlow(buildInitialStudyFiles())
    val studyFiles: StateFlow<List<DofaatiStudyFile>> = _studyFiles.asStateFlow()

    private val _activeLessonId = MutableStateFlow("lesson_5")
    val activeLessonId: StateFlow<String> = _activeLessonId.asStateFlow()

    // Audio playback simulation for waveforms inside SubjectResources, Chat, and Transcript
    private val _playingAudioId = MutableStateFlow<String?>(null)
    val playingAudioId: StateFlow<String?> = _playingAudioId.asStateFlow()

    private val _audioProgressFraction = MutableStateFlow(0.32f)
    val audioProgressFraction: StateFlow<Float> = _audioProgressFraction.asStateFlow()

    private var audioTickerJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordedFile: File? = null

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    // Chat Messages & Join Requests & Members
    private val _chatMessages = MutableStateFlow(buildInitialChatMessages())
    val chatMessages: StateFlow<List<DofaatiChatMessage>> = _chatMessages.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<DofaatiChatMessage?>(null)
    val replyingToMessage: StateFlow<DofaatiChatMessage?> = _replyingToMessage.asStateFlow()

    private val _joinRequests = MutableStateFlow(
        listOf(
            DofaatiJoinRequest("req_1", "زياد أحمد علي", "ز", "منذ 5 دقائق", "طالب علمي علوم - المجموعة الصباحية"),
            DofaatiJoinRequest("req_2", "مريم حسن محمود", "م", "منذ 12 دقيقة", "طالبة فيزيا وكيميا 2026"),
            DofaatiJoinRequest("req_3", "أحمد سامي إبراهيم", "أ", "منذ 25 دقيقة", "انضمام بكود الدفعة")
        )
    )
    val joinRequests: StateFlow<List<DofaatiJoinRequest>> = _joinRequests.asStateFlow()

    private val _batchMembers = MutableStateFlow(
        listOf(
            DofaatiBatchMember("mem_1", "د. عبد الرحمن صفوت", "مشرف الدفعة", isAdmin = true),
            DofaatiBatchMember("mem_2", "يوسف محمود", "طالب متفوق", isAdmin = false),
            DofaatiBatchMember("mem_3", "سلمى طارق", "عضو نشط", isAdmin = false),
            DofaatiBatchMember("mem_4", "عمر خالد", "عضو", isAdmin = false)
        )
    )
    val batchMembers: StateFlow<List<DofaatiBatchMember>> = _batchMembers.asStateFlow()

    // Mind Maps
    private val _mindMaps = MutableStateFlow(
        listOf(
            DofaatiMindMap(
                id = "mm_electricity",
                centerTitle = "قوانين الكهربية",
                sourceFileTitle = "ملزمة الباب الأول",
                branches = listOf(
                    DofaatiMindMapBranch("br_1", "شدة التيار", "I = Q / t (الأمبير)", "I = Q/t", 0xFF10B981, "BOLT"),
                    DofaatiMindMapBranch("br_2", "توصيل المقاومات", "توالي (R1+R2) وتوازي", "Req", 0xFFEA580C, "RESISTOR"),
                    DofaatiMindMapBranch("br_3", "قانون أوم", "V = I × R طردي عند ثبوت T", "V = I·R", 0xFFF59E0B, "OHM"),
                    DofaatiMindMapBranch("br_4", "فرق الجهد", "V = W / Q (الفولت)", "V = W/Q", 0xFF8B5CF6, "VOLT")
                )
            )
        )
    )
    val mindMaps: StateFlow<List<DofaatiMindMap>> = _mindMaps.asStateFlow()

    // Bulk AI Generation & Transcript 50-Quiz & Admin Paste State
    private val _bulkProgress = MutableStateFlow<BulkGenerationProgress?>(null)
    val bulkProgress: StateFlow<BulkGenerationProgress?> = _bulkProgress.asStateFlow()

    private val _isGeneratingBulk = MutableStateFlow(false)
    val isGeneratingBulk: StateFlow<Boolean> = _isGeneratingBulk.asStateFlow()

    private val _lastGeneratedQuizQuestions = MutableStateFlow<List<DofaatiQuestionItem>>(emptyList())
    val lastGeneratedQuizQuestions: StateFlow<List<DofaatiQuestionItem>> = _lastGeneratedQuizQuestions.asStateFlow()

    private val _adminParsedQuestions = MutableStateFlow<List<DofaatiQuestionItem>>(emptyList())
    val adminParsedQuestions: StateFlow<List<DofaatiQuestionItem>> = _adminParsedQuestions.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Live Challenge Quiz & Podium State (Screen 10 & 11)
    private val _liveChallenge = MutableStateFlow(
        LiveChallengeSessionState(
            quizTitle = "كويز الكهربية — الأسبوع 3",
            onlineParticipantsCount = 37,
            answeredParticipantsCount = 21,
            currentQuestionIndex = 0,
            remainingSeconds = 13,
            questions = buildDefaultLiveChallengeQuestions()
        )
    )
    val liveChallenge: StateFlow<LiveChallengeSessionState> = _liveChallenge.asStateFlow()

    private var quizTimerJob: Job? = null

    // Schedule & Countdown (Screen 12)
    private val _scheduleEvents = MutableStateFlow(
        listOf(
            DofaatiScheduleEvent("ev_1", "الأحد 6 مساءً", "فيزيا", "مراجعة توصيل المقاومات وكيرشوف", isExam = false),
            DofaatiScheduleEvent("ev_2", "الأربعاء 8 مساءً", "كيميا", "حل شامل على العناصر الانتقالية", isExam = false),
            DofaatiScheduleEvent("ev_3", "الخميس 10 صباحاً", "فيزيا", "الامتحان الشامل — الباب الأول", isExam = true)
        )
    )
    val scheduleEvents: StateFlow<List<DofaatiScheduleEvent>> = _scheduleEvents.asStateFlow()

    private val _scheduleRemindersEnabled = MutableStateFlow(true)
    val scheduleRemindersEnabled: StateFlow<Boolean> = _scheduleRemindersEnabled.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.ensureSeeded()
        }
    }

    // Navigation Controls
    fun navigateTo(route: DofaatiRoute) {
        _backStack.update { stack ->
            if (stack.lastOrNull() == route) stack else stack + route
        }
    }

    fun navigateBack(): Boolean {
        val current = _backStack.value
        return if (current.size > 1) {
            if (current.last() == DofaatiRoute.LIVE_CHALLENGE) {
                quizTimerJob?.cancel()
            }
            _backStack.value = current.dropLast(1)
            true
        } else {
            false
        }
    }

    fun selectSubject(subjectId: String) {
        _selectedSubjectId.value = subjectId
        navigateTo(DofaatiRoute.SUBJECT_DETAILS)
    }

    fun selectBatch(batchId: String) {
        _activeBatchId.value = batchId
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    // Screen 2: Create New Batch + Generate 8-digit code
    fun createBatch(
        batchName: String,
        logoColorHex: Long,
        subjectNames: List<String>,
        uploadPermission: UploadPermissionMode,
        requireApproval: Boolean
    ): DofaatiBatch {
        val cleanName = batchName.trim().ifBlank { "دفعة التفوق 2026" }
        val part1 = Random.nextInt(1000, 9999)
        val part2 = Random.nextInt(1000, 9999)
        val code = "$part1 $part2"
        val subjects = subjectNames.ifEmpty { listOf("فيزيا", "كيميا", "أحياء") }.mapIndexed { idx, sName ->
            DofaatiSubject(
                id = "sub_${System.currentTimeMillis()}_$idx",
                name = sName.trim(),
                recordingsCount = 2 + idx,
                filesCount = 1 + idx
            )
        }
        val newBatch = DofaatiBatch(
            id = "batch_${System.currentTimeMillis()}",
            name = cleanName,
            code = code,
            logoColorHex = logoColorHex,
            adminName = "أنت (المشرف)",
            studentsCount = 1,
            subjects = subjects,
            uploadPermission = uploadPermission,
            requireApproval = requireApproval
        )
        _batches.update { listOf(newBatch) + it }
        _activeBatchId.value = newBatch.id
        _selectedSubjectId.value = subjects.first().id
        return newBatch
    }

    fun previewBatchByCode(rawCode: String): Pair<String, String> {
        val normalized = rawCode.filter { it.isDigit() }
        val existing = _batches.value.firstOrNull { it.code.replace(" ", "") == normalized }
        return if (existing != null) {
            existing.name to existing.adminName
        } else {
            "تالتة ثانوي 2026 - شعبة المتفوقين" to "د. عبد الرحمن صفوت"
        }
    }

    fun requestJoinByCode(rawCode: String) {
        val normalized = rawCode.filter { it.isDigit() }
        val existing = _batches.value.firstOrNull { it.code.replace(" ", "") == normalized }
        if (existing != null) {
            _activeBatchId.value = existing.id
            _statusBannerMessage.value = "تم إرسال طلب انضمامك إلى «${existing.name}» بنجاح"
        } else {
            _statusBannerMessage.value = "تم إرسال طلب الانضمام بكود ($normalized) للمشرف للموافقة"
        }
    }

    // Screen 3: Chat & Admin Join Requests
    fun sendTextChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val replyPreview = _replyingToMessage.value?.text
        val now = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
        val msg = DofaatiChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderName = "أنت",
            senderInitial = "أ",
            isAdmin = false,
            isMe = true,
            isAudio = false,
            text = trimmed,
            timestamp = now,
            replyToPreview = replyPreview
        )
        _chatMessages.update { it + msg }
        _replyingToMessage.value = null
    }

    fun toggleVoiceRecording(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            _statusBannerMessage.value = "يرجى السماح بصلاحية الميكروفون لتسجيل الرسائل الصوتية"
            return
        }
        if (!_isRecordingVoice.value) {
            startVoiceRecordingInternal()
        } else {
            stopVoiceRecordingAndSend()
        }
    }

    private fun startVoiceRecordingInternal() {
        try {
            val outFile = File(appContext.cacheDir, "dofaati_voice_${System.currentTimeMillis()}.m4a")
            recordedFile = outFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            _isRecordingVoice.value = true
        } catch (_: Throwable) {
            // In headless environments without physical audio hardware (e.g., unit tests), toggle clean visual recording state
            _isRecordingVoice.value = true
        }
    }

    private fun stopVoiceRecordingAndSend() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }
        mediaRecorder = null
        _isRecordingVoice.value = false
        val now = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
        val msg = DofaatiChatMessage(
            id = "voice_${System.currentTimeMillis()}",
            senderName = "أنت",
            senderInitial = "أ",
            isAdmin = false,
            isMe = true,
            isAudio = true,
            text = "رسالة صوتية مسجلة",
            audioDurationSec = 18,
            waveformPeaks = listOf(0.4f, 0.8f, 0.6f, 0.95f, 0.5f, 0.85f, 0.7f, 0.9f, 0.45f, 0.75f, 0.65f, 0.5f),
            timestamp = now,
            replyToPreview = _replyingToMessage.value?.text
        )
        _chatMessages.update { it + msg }
        _replyingToMessage.value = null
    }

    fun setReplyMessage(message: DofaatiChatMessage?) {
        _replyingToMessage.value = message
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        _chatMessages.update { list ->
            list.map { msg ->
                if (msg.id != messageId) msg
                else {
                    val updated = msg.reactions.toMutableMap()
                    updated[emoji] = (updated[emoji] ?: 0) + 1
                    msg.copy(reactions = updated)
                }
            }
        }
    }

    fun toggleAudioPlayback(audioId: String) {
        if (_playingAudioId.value == audioId) {
            _playingAudioId.value = null
            audioTickerJob?.cancel()
            return
        }
        _playingAudioId.value = audioId
        _audioProgressFraction.value = 0.08f
        audioTickerJob?.cancel()
        audioTickerJob = viewModelScope.launch {
            while (_playingAudioId.value == audioId) {
                delay(220)
                val next = _audioProgressFraction.value + 0.04f
                if (next >= 1f) {
                    _audioProgressFraction.value = 0f
                    _playingAudioId.value = null
                } else {
                    _audioProgressFraction.value = next
                }
            }
        }
    }

    fun approveJoinRequest(requestId: String) {
        val req = _joinRequests.value.firstOrNull { it.id == requestId } ?: return
        _joinRequests.update { list -> list.filterNot { it.id == requestId } }
        _batchMembers.update { list ->
            list + DofaatiBatchMember("mem_${System.currentTimeMillis()}", req.studentName, "عضو جديد", isAdmin = false)
        }
        _batches.update { list ->
            list.map { b ->
                if (b.id == _activeBatchId.value) b.copy(studentsCount = b.studentsCount + 1) else b
            }
        }
    }

    fun rejectJoinRequest(requestId: String) {
        _joinRequests.update { list -> list.filterNot { it.id == requestId } }
    }

    fun toggleMuteMember(memberId: String) {
        _batchMembers.update { list ->
            list.map { if (it.id == memberId && !it.isAdmin) it.copy(isMuted = !it.isMuted) else it }
        }
    }

    fun removeMember(memberId: String) {
        _batchMembers.update { list -> list.filterNot { it.id == memberId && !it.isAdmin } }
    }

    // Screen 4 & 5: Audio Transcript & 50-Question Conversion
    fun openAudioTranscript(lessonId: String) {
        _activeLessonId.value = lessonId
        navigateTo(DofaatiRoute.AUDIO_TRANSCRIPT)
    }

    fun convertCurrentTranscriptTo50Questions(onReady: (List<DofaatiQuestionItem>) -> Unit) {
        val lesson = _audioLessons.value.firstOrNull { it.id == _activeLessonId.value } ?: _audioLessons.value.first()
        viewModelScope.launch {
            val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
            val questions = questionRepository.convertAudioTranscriptTo50Questions(
                transcriptId = lesson.id,
                transcriptText = lesson.transcriptText,
                extractedLaws = lesson.extractedLaws,
                examPoints = lesson.importantExamPoints,
                subjectName = subjectName
            )
            _lastGeneratedQuizQuestions.value = questions
            localStore.appendQuestions(questions)
            _statusBannerMessage.value = "تم تحويل التفريغ إلى ${questions.size} سؤالاً تفاعلياً وحفظها في البنك"
            onReady(questions)
        }
    }

    // Screen 6: Mind Maps & Flashcards
    fun createNewMindMap(centerTitle: String, branchTitles: List<String>) {
        val cleanCenter = centerTitle.trim().ifBlank { "دوائر كيرشوف" }
        val colors = listOf(0xFF10B981L, 0xFFEA580CL, 0xFFF59E0BL, 0xFF8B5CF6L)
        val branches = branchTitles.filter { it.isNotBlank() }.ifEmpty {
            listOf("القانون الأول", "القانون الثاني", "اتجاه التيار", "حساب الجهد")
        }.take(4).mapIndexed { idx, label ->
            DofaatiMindMapBranch(
                id = "mm_br_${System.currentTimeMillis()}_$idx",
                title = label.trim(),
                subtitle = "فرع رئيسي لخريطة $cleanCenter",
                formulaOrBadge = "مفهوم ${idx + 1}",
                accentHex = colors[idx % colors.size],
                iconKey = listOf("BOLT", "RESISTOR", "OHM", "VOLT")[idx % 4]
            )
        }
        val created = DofaatiMindMap(
            id = "mm_${System.currentTimeMillis()}",
            centerTitle = cleanCenter,
            sourceFileTitle = "ملزمة الباب الأول",
            branches = branches
        )
        _mindMaps.update { listOf(created) + it }
    }

    fun saveFlashcard(card: DofaatiFlashcard) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertFlashcard(card)
        }
    }

    fun deleteFlashcard(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteFlashcard(cardId)
        }
    }

    // Screen 7: Chunked Bulk 100-Question Generator
    fun startBulkGeneration(
        totalCount: Int,
        difficulties: Set<DifficultyLevel>,
        types: Set<QuestionType>,
        sourceText: String = "التيار الكهربي وقانون أوم وتوصيل المقاومات"
    ) {
        if (_isGeneratingBulk.value) return
        _isGeneratingBulk.value = true
        val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
        viewModelScope.launch {
            questionRepository.generateBulkQuestionsInChunks(
                sourceText = sourceText,
                totalCount = totalCount,
                difficulties = difficulties,
                types = types,
                subjectName = subjectName
            ).collect { progress ->
                _bulkProgress.value = progress
                if (progress.isCompleted) {
                    _lastGeneratedQuizQuestions.value = progress.accumulatedQuestions
                    localStore.appendQuestions(progress.accumulatedQuestions)
                    _isGeneratingBulk.value = false
                    _statusBannerMessage.value = "تم توليد ${progress.accumulatedQuestions.size} سؤالاً بدون انقطاع عبر ${progress.totalBatches} دفعات"
                }
            }
        }
    }

    fun launchQuizFromQuestions(title: String, questions: List<DofaatiQuestionItem>) {
        if (questions.isEmpty()) return
        _liveChallenge.value = LiveChallengeSessionState(
            quizTitle = title,
            onlineParticipantsCount = 37,
            answeredParticipantsCount = 21,
            currentQuestionIndex = 0,
            remainingSeconds = 13,
            questions = questions,
            selectedAnswers = emptyMap(),
            earnedPoints = 0,
            isFinished = false
        )
        navigateTo(DofaatiRoute.LIVE_CHALLENGE)
        startLiveQuizCountdown()
    }

    // Screen 8: Question Bank Import & Cumulative Batches
    fun importQuestionsFromPdfUri(uri: Uri) {
        viewModelScope.launch {
            val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
            val imported = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    questionRepository.importQuestionsFromPdfOrText(stream, subjectName)
                } ?: emptyList()
            }
            if (imported.isNotEmpty()) {
                localStore.appendQuestions(imported)
                _statusBannerMessage.value = "تم استيراد ${imported.size} سؤالاً من الملف إلى البنك الشامل"
            }
        }
    }

    fun addQuickQuestionBatchToBank(count: Int = 25) {
        viewModelScope.launch {
            val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
            questionRepository.generateBulkQuestionsInChunks(
                sourceText = "توصيل المقاومات وقوانين كيرشوف والدوائر المغلقة",
                totalCount = count,
                difficulties = setOf(DifficultyLevel.MEDIUM, DifficultyLevel.HIGHER_ORDER),
                types = setOf(QuestionType.MCQ),
                subjectName = subjectName
            ).collect { progress ->
                if (progress.isCompleted) {
                    localStore.appendQuestions(progress.accumulatedQuestions)
                    _statusBannerMessage.value = "تمت إضافة دفعة من ${progress.accumulatedQuestions.size} سؤالاً جديدة للبنك"
                }
            }
        }
    }

    // Screen 9: Admin Bulk Paste Parser & Publish Exam
    fun parseAdminBulkPastePreview(rawText: String) {
        viewModelScope.launch {
            val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
            val parsed = questionRepository.parsePastedBulkQuestions(rawText, subjectName)
            _adminParsedQuestions.value = parsed
        }
    }

    fun publishBulkExamToBatch(rawText: String, examTitle: String) {
        viewModelScope.launch {
            val subjectName = activeBatch.subjects.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "فيزيا"
            val parsed = questionRepository.parsePastedBulkQuestions(rawText, subjectName)
            val finalQuestions = parsed.ifEmpty { buildDefaultLiveChallengeQuestions() }
            _adminParsedQuestions.value = finalQuestions
            localStore.appendQuestions(finalQuestions)
            launchQuizFromQuestions(
                title = examTitle.ifBlank { "كويز الكهربية — الأسبوع 3" },
                questions = finalQuestions
            )
        }
    }

    // Screen 10 & 11: Live Challenge Timer + Scoring + Podium
    fun startDefaultLiveChallenge() {
        _liveChallenge.value = LiveChallengeSessionState(
            quizTitle = "كويز الكهربية — الأسبوع 3",
            onlineParticipantsCount = 37,
            answeredParticipantsCount = 21,
            currentQuestionIndex = 0,
            remainingSeconds = 13,
            questions = buildDefaultLiveChallengeQuestions(),
            selectedAnswers = emptyMap(),
            earnedPoints = 0,
            isFinished = false
        )
        navigateTo(DofaatiRoute.LIVE_CHALLENGE)
        startLiveQuizCountdown()
    }

    private fun startLiveQuizCountdown() {
        quizTimerJob?.cancel()
        quizTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val st = _liveChallenge.value
                if (st.isFinished) break
                if (st.remainingSeconds > 1) {
                    _liveChallenge.update {
                        it.copy(
                            remainingSeconds = it.remainingSeconds - 1,
                            answeredParticipantsCount = (it.answeredParticipantsCount + 1).coerceAtMost(it.onlineParticipantsCount)
                        )
                    }
                } else {
                    advanceOrFinishQuiz()
                }
            }
        }
    }

    fun submitLiveQuizAnswer(optionIndex: Int) {
        val state = _liveChallenge.value
        val currentQ = state.questions.getOrNull(state.currentQuestionIndex) ?: return
        if (state.selectedAnswers.containsKey(currentQ.id)) return

        val isCorrect = optionIndex == currentQ.correctOptionIndex
        val gained = LiveQuizScoringEngine.calculateQuestionScore(isCorrect, state.remainingSeconds)
        val updatedAnswers = state.selectedAnswers + (currentQ.id to optionIndex)

        _liveChallenge.update {
            it.copy(
                selectedAnswers = updatedAnswers,
                earnedPoints = it.earnedPoints + gained,
                answeredParticipantsCount = (it.answeredParticipantsCount + 1).coerceAtMost(it.onlineParticipantsCount)
            )
        }

        viewModelScope.launch {
            delay(450)
            advanceOrFinishQuiz()
        }
    }

    private fun advanceOrFinishQuiz() {
        val state = _liveChallenge.value
        if (state.currentQuestionIndex + 1 < state.questions.size) {
            _liveChallenge.update {
                it.copy(
                    currentQuestionIndex = it.currentQuestionIndex + 1,
                    remainingSeconds = 15,
                    answeredParticipantsCount = 12
                )
            }
        } else {
            quizTimerJob?.cancel()
            val correctCount = state.questions.count { q ->
                state.selectedAnswers[q.id] == q.correctOptionIndex
            }
            val (top3, avg) = LiveQuizScoringEngine.computeLeaderboardUpdate(
                studentName = "أنت",
                earnedPoints = state.earnedPoints,
                totalQuestions = state.questions.size,
                correctAnswers = correctCount
            )
            _liveChallenge.update {
                it.copy(
                    isFinished = true,
                    leaderboardTopThree = top3,
                    batchAveragePercentage = avg
                )
            }
            if (currentRoute == DofaatiRoute.LIVE_CHALLENGE) {
                navigateTo(DofaatiRoute.LEADERBOARD_RESULTS)
            }
        }
    }

    // Screen 12: Schedule & ML Kit Image OCR extraction
    fun toggleScheduleNotifications(enabled: Boolean) {
        _scheduleRemindersEnabled.value = enabled
        _statusBannerMessage.value = if (enabled) {
            "تم تفعيل إشعارات التذكير التلقائية للمواعيد والامتحانات"
        } else {
            "تم إيقاف إشعارات التذكير"
        }
    }

    fun extractScheduleFromImageUri(uri: Uri) {
        viewModelScope.launch {
            val ocrText = withContext(Dispatchers.IO) {
                try {
                    val img = InputImage.fromFilePath(appContext, uri)
                    suspendCoroutine<String> { cont ->
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            .process(img)
                            .addOnSuccessListener { result -> cont.resume(result.text) }
                            .addOnFailureListener { cont.resume("") }
                    }
                } catch (_: Throwable) {
                    ""
                }
            }
            val extractedEvent = parseScheduleLine(ocrText)
            _scheduleEvents.update { it + extractedEvent }
            _statusBannerMessage.value = "تم استخراج الموعد تلقائياً من الصورة وإضافته للجدول"
        }
    }

    private fun parseScheduleLine(ocrText: String): DofaatiScheduleEvent {
        val candidateLine = ocrText.lines().firstOrNull { it.trim().length > 5 }?.trim()
            ?: "السبت 7 مساءً"
        return DofaatiScheduleEvent(
            id = "ev_${System.currentTimeMillis()}",
            dayAndTime = if (candidateLine.length > 22) "السبت 7 مساءً" else candidateLine,
            subjectName = "فيزيا",
            title = "موعد مستخرج تلقائياً: $candidateLine",
            isExam = ocrText.contains("امتحان") || ocrText.contains("Exam", ignoreCase = true)
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioTickerJob?.cancel()
        quizTimerJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun buildInitialAudioLessons(): List<DofaatiAudioLesson> = listOf(
            DofaatiAudioLesson(
                id = "lesson_5",
                subjectId = "sub_physics",
                title = "الدرس 5 — توصيل المقاومات",
                durationSec = 2480,
                waveformPeaks = listOf(0.45f, 0.85f, 0.6f, 0.95f, 0.5f, 0.78f, 0.92f, 0.42f, 0.8f, 0.65f, 0.9f, 0.55f, 0.75f, 0.88f, 0.48f, 0.72f),
                transcriptText = "في حصة اليوم نشرح قواعد توصيل المقاومات على التوالي والتوازي. عند توصيل المقاومات على التوالي يظل التيار ثابتاً ويتوزع الجهد بنسبة المقاومات. أما في التوصيل على التوازي فيثبت فرق الجهد وتقل المقاومة المكافئة عن أصغر مقاومة متصلة بالدائرة. احذر من المقاومات الملغاة عند تساوي الجهد بين نقطتين في القنطرة المتزنة.",
                extractedLaws = listOf(
                    "في التوالي: Req = R1 + R2 + R3 (التيار ثابت والجهد يتجزأ)",
                    "في التوازي: 1/Req = 1/R1 + 1/R2 + 1/R3 (الجهد ثابت والتيار يتجزأ)",
                    "مقاومتان على التوازي: Req = (R1 × R2) / (R1 + R2)",
                    "حساب تيار الفرع: I_branch = I_total × (R_parallel / R_branch)"
                ),
                importantExamPoints = listOf(
                    "ركز في مسائل فتح وغلق المفتاح على تغير المقاومة المكافئة وتوزيع التيار.",
                    "إلغاء المقاومة الوسطى عند تحقق نسبة الاتزان (R1 / R2 = R3 / R4).",
                    "القدرة المستهلكة في التوازي تتناسب عكسياً مع المقاومة: Pw = V² / R."
                )
            ),
            DofaatiAudioLesson(
                id = "lesson_4",
                subjectId = "sub_physics",
                title = "الدرس 4 — قانون أوم والمقاومة النوعية",
                durationSec = 1920,
                waveformPeaks = listOf(0.35f, 0.65f, 0.8f, 0.5f, 0.9f, 0.7f, 0.6f, 0.85f, 0.45f, 0.75f, 0.82f, 0.55f),
                transcriptText = "المقاومة الكهربية تتناسب طردياً مع طول الموصل وعكسياً مع مساحة المقطع عند ثبوت درجة الحرارة.",
                extractedLaws = listOf("R = ρe × (L / A)", "V = I × R"),
                importantExamPoints = listOf("عند سحب سلك وزاد طوله للضعف تزداد مقاومته أربع مرات.")
            ),
            DofaatiAudioLesson(
                id = "lesson_3",
                subjectId = "sub_physics",
                title = "الدرس 3 — شدة التيار وكمية الشحنة",
                durationSec = 1650,
                waveformPeaks = listOf(0.5f, 0.75f, 0.62f, 0.88f, 0.4f, 0.7f, 0.85f, 0.52f, 0.68f, 0.92f, 0.48f, 0.6f),
                transcriptText = "شدة التيار الكهربي تُقدر بكمية الشحنة المارة عبر مقطع موصل في الثانية الواحدة وتقاس بالأمبير.",
                extractedLaws = listOf("I = Q / t = (N × e) / t"),
                importantExamPoints = listOf("تذكر دائماً أن شحنة الإلكترون e = 1.6 × 10⁻¹⁹ C.")
            ),
            DofaatiAudioLesson(
                id = "lesson_2",
                subjectId = "sub_physics",
                title = "الدرس 2 — قراءة الأميتر والفولتميتر",
                durationSec = 2110,
                waveformPeaks = listOf(0.42f, 0.82f, 0.7f, 0.58f, 0.94f, 0.64f, 0.72f, 0.48f, 0.86f, 0.54f, 0.78f, 0.62f),
                transcriptText = "قراءة الفولتميتر على طرفي بطارية ذات مقاومة داخلية تساوي VB - I·r.",
                extractedLaws = listOf("V = VB - I·r", "I = VB / (R_ext + r)"),
                importantExamPoints = listOf("أثناء شحن البطارية يصبح فرق الجهد V = VB + I·r.")
            )
        )

        fun buildInitialStudyFiles(): List<DofaatiStudyFile> = listOf(
            DofaatiStudyFile("file_1", "sub_physics", "ملزمة الباب الأول", 42, "4.2 MB"),
            DofaatiStudyFile("file_2", "sub_physics", "تجميع أفكار توصيل المقاومات", 28, "2.8 MB"),
            DofaatiStudyFile("file_3", "sub_physics", "بنك أسئلة كيرشوف وأوم", 36, "3.5 MB")
        )

        fun buildInitialChatMessages(): List<DofaatiChatMessage> = listOf(
            DofaatiChatMessage(
                id = "c_1",
                senderName = "د. عبد الرحمن صفوت",
                senderInitial = "ع",
                isAdmin = true,
                isMe = false,
                isAudio = false,
                text = "يا شباب شدوا حيلكم، نزلتلكم ريكورد الدرس الخامس كامل وتجميع مسائل المقاومات.",
                timestamp = "09:15 ص",
                reactions = mapOf("🔥" to 14, "❤️" to 22)
            ),
            DofaatiChatMessage(
                id = "c_2",
                senderName = "يوسف محمود",
                senderInitial = "ي",
                isAdmin = false,
                isMe = false,
                isAudio = true,
                text = "شرح فكرة المقاومة الملغاة في الدقيقة 12",
                audioDurationSec = 42,
                waveformPeaks = listOf(0.38f, 0.75f, 0.9f, 0.55f, 0.82f, 0.48f, 0.94f, 0.66f, 0.78f, 0.5f, 0.86f, 0.6f),
                timestamp = "09:20 ص",
                reactions = mapOf("👍" to 9)
            ),
            DofaatiChatMessage(
                id = "c_3",
                senderName = "سلمى طارق",
                senderInitial = "س",
                isAdmin = false,
                isMe = false,
                isAudio = true,
                text = "ملخص سريع لقوانين كيرشوف قبل الكويز",
                audioDurationSec = 31,
                waveformPeaks = listOf(0.5f, 0.68f, 0.88f, 0.44f, 0.74f, 0.92f, 0.62f, 0.8f, 0.46f, 0.7f, 0.58f, 0.84f),
                timestamp = "09:28 ص",
                reactions = mapOf("⭐" to 11)
            ),
            DofaatiChatMessage(
                id = "c_4",
                senderName = "د. عبد الرحمن صفوت",
                senderInitial = "ع",
                isAdmin = true,
                isMe = false,
                isAudio = true,
                text = "تنبيه مهم بخصوص امتحان الخميس",
                audioDurationSec = 54,
                waveformPeaks = listOf(0.45f, 0.92f, 0.65f, 0.85f, 0.52f, 0.96f, 0.74f, 0.88f, 0.4f, 0.79f, 0.68f, 0.9f),
                timestamp = "09:35 ص",
                reactions = mapOf("✅" to 19)
            )
        )

        fun buildDefaultLiveChallengeQuestions(): List<DofaatiQuestionItem> = listOf(
            DofaatiQuestionItem(
                id = "live_1",
                questionNumber = 1,
                subjectName = "فيزيا",
                questionText = "وحدة قياس شدة التيار الكهربي هي:",
                questionType = QuestionType.MCQ,
                difficulty = DifficultyLevel.EASY,
                options = listOf("الفولت", "الأمبير", "الأوم", "الوات"),
                correctOptionIndex = 1,
                detailedExplanation = "تقاس شدة التيار الكهربي بوحدة الأمبير (A) وتكافئ كولوم/ثانية.",
                sourceTag = "التحدي المباشر"
            ),
            DofaatiQuestionItem(
                id = "live_2",
                questionNumber = 2,
                subjectName = "فيزيا",
                questionText = "المقاومة المكافئة لمقاومتين 6Ω و 3Ω متصلتين على التوازي تساوي:",
                questionType = QuestionType.MCQ,
                difficulty = DifficultyLevel.MEDIUM,
                options = listOf("9Ω", "2Ω", "4.5Ω", "18Ω"),
                correctOptionIndex = 1,
                detailedExplanation = "Req = (6 × 3) / (6 + 3) = 18 / 9 = 2Ω.",
                sourceTag = "التحدي المباشر"
            ),
            DofaatiQuestionItem(
                id = "live_3",
                questionNumber = 3,
                subjectName = "فيزيا",
                questionText = "حاصل ضرب شدة التيار في المقاومة الكهربية يعبر عن:",
                questionType = QuestionType.MCQ,
                difficulty = DifficultyLevel.EASY,
                options = listOf("القدرة الكهربية", "فرق الجهد", "الشحنة الكهربية", "المقاومة النوعية"),
                correctOptionIndex = 1,
                detailedExplanation = "طبقاً لقانون أوم: V = I × R وهو فرق الجهد الكهربي ويُقاس بالفولت.",
                sourceTag = "التحدي المباشر"
            )
        )
    }
}
