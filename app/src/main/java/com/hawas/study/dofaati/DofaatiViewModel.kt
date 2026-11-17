package com.hawas.study.dofaati

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
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
import com.hawas.study.dofaati.models.DofaatiQuizAttempt
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

class DofaatiViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val localStore = DofaatiLocalStore(DofaatiDatabase.getInstance(appContext).dofaatiDao())
    private val prefsRepo = UserPreferencesRepository(appContext)
    val questionRepository = BulkQuestionGeneratorRepository(appContext)

    // Backstack Navigation inside Dofa'ati module
    private val _backStack = MutableStateFlow<List<DofaatiRoute>>(listOf(DofaatiRoute.HUB))
    val backStack: StateFlow<List<DofaatiRoute>> = _backStack.asStateFlow()

    val currentRoute: DofaatiRoute
        get() = _backStack.value.lastOrNull() ?: DofaatiRoute.HUB

    // Room-backed Reactive Streams (Starts 100% Empty — ZERO fake seed rows)
    val batches: StateFlow<List<DofaatiBatch>> = localStore.observeBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionBankItems: StateFlow<List<DofaatiQuestionItem>> = localStore.observeQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flashcards: StateFlow<List<DofaatiFlashcard>> = localStore.observeFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioLessons: StateFlow<List<DofaatiAudioLesson>> = localStore.observeAudioLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studyFiles: StateFlow<List<DofaatiStudyFile>> = localStore.observeStudyFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<DofaatiChatMessage>> = localStore.observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val joinRequests: StateFlow<List<DofaatiJoinRequest>> = localStore.observeJoinRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batchMembers: StateFlow<List<DofaatiBatchMember>> = localStore.observeBatchMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mindMaps: StateFlow<List<DofaatiMindMap>> = localStore.observeMindMaps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleEvents: StateFlow<List<DofaatiScheduleEvent>> = localStore.observeScheduleEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAttempts: StateFlow<List<DofaatiQuizAttempt>> = localStore.observeQuizAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Selection State
    private val _activeBatchId = MutableStateFlow<String?>(null)
    val activeBatchId: StateFlow<String?> = _activeBatchId.asStateFlow()

    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    val selectedSubjectId: StateFlow<String?> = _selectedSubjectId.asStateFlow()

    private val _activeLessonId = MutableStateFlow<String?>(null)
    val activeLessonId: StateFlow<String?> = _activeLessonId.asStateFlow()

    val currentUserName: StateFlow<String>
        get() = _currentUserName
    private val _currentUserName = MutableStateFlow("الطالب")

    // Real Audio Recording & Playback State
    private val _playingAudioId = MutableStateFlow<String?>(null)
    val playingAudioId: StateFlow<String?> = _playingAudioId.asStateFlow()

    private val _audioProgressFraction = MutableStateFlow(0f)
    val audioProgressFraction: StateFlow<Float> = _audioProgressFraction.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var audioProgressJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var recorderAmplitudeJob: Job? = null
    private var recordedFile: File? = null
    private var recordingStartMs: Long = 0L
    private val recordedWaveformSamples = mutableListOf<Float>()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _isRecordingLesson = MutableStateFlow(false)
    val isRecordingLesson: StateFlow<Boolean> = _isRecordingLesson.asStateFlow()

    // Chat Reply State
    private val _replyingToMessage = MutableStateFlow<DofaatiChatMessage?>(null)
    val replyingToMessage: StateFlow<DofaatiChatMessage?> = _replyingToMessage.asStateFlow()

    // Bulk AI, Transcript & MindMap AI States
    private val _bulkProgress = MutableStateFlow<BulkGenerationProgress?>(null)
    val bulkProgress: StateFlow<BulkGenerationProgress?> = _bulkProgress.asStateFlow()

    private val _isGeneratingBulk = MutableStateFlow(false)
    val isGeneratingBulk: StateFlow<Boolean> = _isGeneratingBulk.asStateFlow()

    private val _isTranscribingAudio = MutableStateFlow(false)
    val isTranscribingAudio: StateFlow<Boolean> = _isTranscribingAudio.asStateFlow()

    private val _isConvertingTranscript = MutableStateFlow(false)
    val isConvertingTranscript: StateFlow<Boolean> = _isConvertingTranscript.asStateFlow()

    private val _isGeneratingMindMap = MutableStateFlow(false)
    val isGeneratingMindMap: StateFlow<Boolean> = _isGeneratingMindMap.asStateFlow()

    private val _adminParsedQuestions = MutableStateFlow<List<DofaatiQuestionItem>>(emptyList())
    val adminParsedQuestions: StateFlow<List<DofaatiQuestionItem>> = _adminParsedQuestions.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Live Challenge Quiz State
    private val _liveChallenge = MutableStateFlow(LiveChallengeSessionState())
    val liveChallenge: StateFlow<LiveChallengeSessionState> = _liveChallenge.asStateFlow()

    private var quizTimerJob: Job? = null
    private var quizAdvanceJob: Job? = null

    private val _scheduleRemindersEnabled = MutableStateFlow(true)
    val scheduleRemindersEnabled: StateFlow<Boolean> = _scheduleRemindersEnabled.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val settings = prefsRepo.userSettingsFlow.first()
                if (settings.userName.isNotBlank()) {
                    _currentUserName.value = settings.userName
                }
            }
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
                quizAdvanceJob?.cancel()
            }
            _backStack.value = current.dropLast(1)
            true
        } else {
            false
        }
    }

    fun currentActiveBatch(): DofaatiBatch? =
        batches.value.firstOrNull { it.id == _activeBatchId.value } ?: batches.value.firstOrNull()

    fun selectBatch(batchId: String) {
        _activeBatchId.value = batchId
    }

    fun selectSubject(subjectId: String) {
        _selectedSubjectId.value = subjectId
        navigateTo(DofaatiRoute.SUBJECT_DETAILS)
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun showMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    // Screen 2: Create New Batch (Persisted in Room)
    fun createBatch(
        batchName: String,
        logoColorHex: Long,
        subjectNames: List<String>,
        uploadPermission: UploadPermissionMode,
        requireApproval: Boolean,
        onCreated: (DofaatiBatch) -> Unit = {}
    ) {
        val cleanName = batchName.trim()
        if (cleanName.isEmpty()) {
            _statusBannerMessage.value = "اكتب اسم الدفعة أولاً"
            return
        }
        val cleanSubjects = subjectNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanSubjects.isEmpty()) {
            _statusBannerMessage.value = "أضف مادة دراسية واحدة على الأقل"
            return
        }

        val part1 = Random.nextInt(1000, 9999)
        val part2 = Random.nextInt(1000, 9999)
        val code = "$part1 $part2"
        val batchId = "batch_${System.currentTimeMillis()}"
        val subjects = cleanSubjects.mapIndexed { idx, sName ->
            DofaatiSubject(
                id = "sub_${System.currentTimeMillis()}_${idx}_${Random.nextInt(100, 999)}",
                batchId = batchId,
                name = sName
            )
        }
        val creatorName = _currentUserName.value.ifBlank { "المشرف" }
        val newBatch = DofaatiBatch(
            id = batchId,
            name = cleanName,
            code = code,
            logoColorHex = logoColorHex,
            adminName = creatorName,
            studentsCount = 1,
            subjects = subjects,
            uploadPermission = uploadPermission,
            requireApproval = requireApproval,
            pinnedNotice = ""
        )
        val adminMember = DofaatiBatchMember(
            id = "mem_admin_${System.currentTimeMillis()}",
            batchId = batchId,
            name = creatorName,
            roleLabel = "مشرف الدفعة",
            isAdmin = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            localStore.saveBatchWithInitialSetup(newBatch, adminMember)
            withContext(Dispatchers.Main) {
                _activeBatchId.value = newBatch.id
                _selectedSubjectId.value = subjects.first().id
                onCreated(newBatch)
            }
        }
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteBatch(batchId)
            withContext(Dispatchers.Main) {
                if (_activeBatchId.value == batchId) {
                    _activeBatchId.value = batches.value.firstOrNull { it.id != batchId }?.id
                }
                _statusBannerMessage.value = "تم حذف الدفعة"
                if (currentRoute != DofaatiRoute.HUB) {
                    navigateBack()
                }
            }
        }
    }

    fun addSubjectToActiveBatch(subjectName: String) {
        val batch = currentActiveBatch() ?: return
        val clean = subjectName.trim()
        if (clean.isEmpty()) return
        if (batch.subjects.any { it.name.equals(clean, ignoreCase = true) }) {
            _statusBannerMessage.value = "هذه المادة مضافة بالفعل"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            localStore.addSubject(batch.id, clean)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تمت إضافة مادة «$clean»"
            }
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteSubject(subjectId)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم حذف المادة"
            }
        }
    }

    fun updatePinnedNotice(notice: String) {
        val batch = currentActiveBatch() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            localStore.updateBatchPinnedNotice(batch.id, notice.trim())
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = if (notice.isBlank()) "تم إزالة التنبيه المثبت" else "تم تحديث التنبيه المثبت للدفعة"
            }
        }
    }

    fun previewBatchByCode(rawCode: String): Pair<String, String>? {
        val normalized = rawCode.filter { it.isDigit() }
        val existing = batches.value.firstOrNull { it.code.replace(" ", "") == normalized } ?: return null
        return existing.name to existing.adminName
    }

    fun requestJoinByCode(rawCode: String, note: String = "") {
        val normalized = rawCode.filter { it.isDigit() }
        val existing = batches.value.firstOrNull { it.code.replace(" ", "") == normalized }
        if (existing == null) {
            _statusBannerMessage.value = "لا توجد دفعة مطابقة للكود ($normalized)"
            return
        }
        val studentName = _currentUserName.value.ifBlank { "طالب" }
        val timeLabel = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
        viewModelScope.launch(Dispatchers.IO) {
            if (existing.requireApproval) {
                val req = DofaatiJoinRequest(
                    id = "req_${System.currentTimeMillis()}",
                    batchId = existing.id,
                    studentName = studentName,
                    studentInitial = studentName.take(1).ifBlank { "ط" },
                    requestedAt = timeLabel,
                    note = note.trim().ifBlank { "طلب انضمام عبر الكود" }
                )
                localStore.addJoinRequest(req)
                withContext(Dispatchers.Main) {
                    _statusBannerMessage.value = "تم إرسال طلب انضمامك إلى «${existing.name}» بانتظار موافقة المشرف"
                }
            } else {
                val member = DofaatiBatchMember(
                    id = "mem_${System.currentTimeMillis()}",
                    batchId = existing.id,
                    name = studentName,
                    roleLabel = "عضو",
                    isAdmin = false
                )
                localStore.upsertBatchMember(member)
                localStore.updateBatchStudentsCount(existing.id, existing.studentsCount + 1)
                withContext(Dispatchers.Main) {
                    _activeBatchId.value = existing.id
                    _statusBannerMessage.value = "تم انضمامك إلى «${existing.name}» بنجاح"
                }
            }
        }
    }

    // Screen 3: Real Chat & Admin Actions
    fun sendTextChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val batch = currentActiveBatch() ?: run {
            _statusBannerMessage.value = "أنشئ دفعة أولاً لإرسال الرسائل"
            return
        }
        val sender = _currentUserName.value.ifBlank { "أنت" }
        val replyPreview = _replyingToMessage.value?.text
        val now = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
        val msg = DofaatiChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            batchId = batch.id,
            senderName = sender,
            senderInitial = sender.take(1).ifBlank { "أ" },
            isAdmin = sender == batch.adminName,
            isMe = true,
            isAudio = false,
            text = trimmed,
            timestamp = now,
            replyToPreview = replyPreview
        )
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertChatMessage(msg)
        }
        _replyingToMessage.value = null
    }

    fun toggleVoiceRecording(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            _statusBannerMessage.value = "يرجى السماح بصلاحية الميكروفون للتسجيل الصوتي"
            return
        }
        if (!_isRecordingVoice.value) {
            if (startRealRecorder(prefix = "chat_voice")) {
                _isRecordingVoice.value = true
            }
        } else {
            val result = stopRealRecorder()
            _isRecordingVoice.value = false
            if (result != null) {
                val (file, durationSec, peaks) = result
                val batch = currentActiveBatch() ?: return
                val sender = _currentUserName.value.ifBlank { "أنت" }
                val now = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
                val msg = DofaatiChatMessage(
                    id = "voice_${System.currentTimeMillis()}",
                    batchId = batch.id,
                    senderName = sender,
                    senderInitial = sender.take(1).ifBlank { "أ" },
                    isAdmin = sender == batch.adminName,
                    isMe = true,
                    isAudio = true,
                    text = "رسالة صوتية",
                    audioUri = file.absolutePath,
                    audioDurationSec = durationSec,
                    waveformPeaks = peaks,
                    timestamp = now,
                    replyToPreview = _replyingToMessage.value?.text
                )
                viewModelScope.launch(Dispatchers.IO) {
                    localStore.upsertChatMessage(msg)
                }
                _replyingToMessage.value = null
            }
        }
    }

    fun toggleLessonVoiceRecording(hasMicPermission: Boolean, subjectId: String, titleInput: String = "") {
        if (!hasMicPermission) {
            _statusBannerMessage.value = "يرجى السماح بصلاحية الميكروفون لتسجيل الدرس"
            return
        }
        if (!_isRecordingLesson.value) {
            if (startRealRecorder(prefix = "lesson_rec")) {
                _isRecordingLesson.value = true
            }
        } else {
            val result = stopRealRecorder()
            _isRecordingLesson.value = false
            if (result != null) {
                val (file, durationSec, peaks) = result
                val cleanTitle = titleInput.trim().ifBlank {
                    val timeLabel = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
                    "تسجيل صوتي ($timeLabel)"
                }
                val lesson = DofaatiAudioLesson(
                    id = "lesson_${System.currentTimeMillis()}",
                    subjectId = subjectId,
                    title = cleanTitle,
                    durationSec = durationSec,
                    waveformPeaks = peaks,
                    audioUri = file.absolutePath
                )
                viewModelScope.launch(Dispatchers.IO) {
                    localStore.upsertAudioLesson(lesson)
                    withContext(Dispatchers.Main) {
                        _activeLessonId.value = lesson.id
                        _statusBannerMessage.value = "تم حفظ التسجيل الصوتي بنجاح"
                    }
                }
            }
        }
    }

    fun importAudioLessonFromUri(uri: Uri, subjectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = runCatching {
                val audioDir = File(appContext.filesDir, "dofaati_audio").apply { mkdirs() }
                var displayName = "تسجيل صوتي"
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                }
                val ext = displayName.substringAfterLast('.', "m4a").take(5)
                val destFile = File(audioDir, "imported_${System.currentTimeMillis()}.$ext")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("تعذر فتح الملف الصوتي")

                val retriever = MediaMetadataRetriever()
                val durationSec = try {
                    retriever.setDataSource(destFile.absolutePath)
                    val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    (ms / 1000L).toInt().coerceAtLeast(1)
                } finally {
                    retriever.release()
                }
                val peaks = computeWaveformFromFileBytes(destFile)
                DofaatiAudioLesson(
                    id = "lesson_${System.currentTimeMillis()}",
                    subjectId = subjectId,
                    title = displayName.substringBeforeLast(".").ifBlank { displayName },
                    durationSec = durationSec,
                    waveformPeaks = peaks,
                    audioUri = destFile.absolutePath
                )
            }
            imported.onSuccess { lesson ->
                localStore.upsertAudioLesson(lesson)
                withContext(Dispatchers.Main) {
                    _statusBannerMessage.value = "تمت إضافة التسجيل «${lesson.title}»"
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    _statusBannerMessage.value = err.message ?: "تعذر استيراد الملف الصوتي"
                }
            }
        }
    }

    fun deleteAudioLesson(lessonId: String) {
        if (_playingAudioId.value == lessonId) stopAudioPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteAudioLesson(lessonId)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم حذف التسجيل الصوتي"
            }
        }
    }

    fun importStudyPdfFromUri(uri: Uri, subjectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val info = questionRepository.inspectPdfOrText(uri)
                val docsDir = File(appContext.filesDir, "dofaati_docs").apply { mkdirs() }
                val destFile = File(docsDir, "doc_${System.currentTimeMillis()}.pdf")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                DofaatiStudyFile(
                    id = "file_${System.currentTimeMillis()}",
                    subjectId = subjectId,
                    title = info.title,
                    pageCount = info.pageCount,
                    fileSizeLabel = info.fileSizeLabel,
                    fileUri = destFile.absolutePath,
                    extractedText = info.extractedText.take(12_000)
                )
            }
            result.onSuccess { studyFile ->
                localStore.upsertStudyFile(studyFile)
                withContext(Dispatchers.Main) {
                    _statusBannerMessage.value = "تم رفع «${studyFile.title}» (${studyFile.pageCount} صفحة)"
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _statusBannerMessage.value = "تعذر قراءة ملف PDF المحدد"
                }
            }
        }
    }

    fun deleteStudyFile(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteStudyFile(fileId)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم حذف الملف"
            }
        }
    }

    private fun startRealRecorder(prefix: String): Boolean {
        return try {
            val audioDir = File(appContext.filesDir, "dofaati_audio").apply { mkdirs() }
            val outFile = File(audioDir, "${prefix}_${System.currentTimeMillis()}.m4a")
            recordedFile = outFile
            recordedWaveformSamples.clear()
            recordingStartMs = System.currentTimeMillis()

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
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recorderAmplitudeJob?.cancel()
            recorderAmplitudeJob = viewModelScope.launch {
                while (isActive && mediaRecorder != null) {
                    val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = (amp / 32767f).coerceIn(0.15f, 1.0f)
                    recordedWaveformSamples.add(normalized)
                    delay(140)
                }
            }
            true
        } catch (_: Throwable) {
            recorderAmplitudeJob?.cancel()
            mediaRecorder = null
            recordedFile = null
            _statusBannerMessage.value = "تعذر تشغيل الميكروفون على هذا الجهاز"
            false
        }
    }

    private fun stopRealRecorder(): Triple<File, Int, List<Float>>? {
        recorderAmplitudeJob?.cancel()
        recorderAmplitudeJob = null
        val file = recordedFile
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
        val peaks = downsamplePeaks(recordedWaveformSamples)
        try {
            mediaRecorder?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }
        mediaRecorder = null
        recordedFile = null

        if (file == null || !file.exists() || file.length() == 0L) {
            _statusBannerMessage.value = "لم يتم التقاط صوت صالح"
            return null
        }
        return Triple(file, elapsedSec, peaks)
    }

    private fun downsamplePeaks(raw: List<Float>, targetBars: Int = 16): List<Float> {
        if (raw.isEmpty()) return List(targetBars) { 0.35f }
        if (raw.size <= targetBars) return raw
        val step = raw.size.toFloat() / targetBars.toFloat()
        return List(targetBars) { i ->
            val idx = (i * step).toInt().coerceIn(0, raw.lastIndex)
            raw[idx]
        }
    }

    private fun computeWaveformFromFileBytes(file: File, targetBars: Int = 16): List<Float> {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return List(targetBars) { 0.4f }
        if (bytes.isEmpty()) return List(targetBars) { 0.4f }
        val chunk = (bytes.size / targetBars).coerceAtLeast(1)
        return List(targetBars) { i ->
            val start = i * chunk
            val end = ((i + 1) * chunk).coerceAtMost(bytes.size)
            var sum = 0
            var count = 0
            var idx = start
            while (idx < end) {
                sum += kotlin.math.abs(bytes[idx].toInt())
                count++
                idx += maxOf(1, (end - start) / 32)
            }
            val avg = if (count > 0) sum / count.toFloat() / 128f else 0.35f
            avg.coerceIn(0.2f, 1.0f)
        }
    }

    fun toggleAudioPlayback(audioId: String, audioUri: String = "") {
        if (_playingAudioId.value == audioId) {
            stopAudioPlayback()
            return
        }
        stopAudioPlayback()

        val resolvedUri = audioUri.ifBlank {
            audioLessons.value.firstOrNull { it.id == audioId }?.audioUri
                ?: chatMessages.value.firstOrNull { it.id == audioId }?.audioUri
                ?: ""
        }
        if (resolvedUri.isBlank() || (!resolvedUri.startsWith("content://") && !File(resolvedUri).exists())) {
            _statusBannerMessage.value = "لا يوجد ملف صوتي متاح للتشغيل"
            return
        }

        try {
            val player = MediaPlayer().apply {
                if (resolvedUri.startsWith("content://")) {
                    setDataSource(appContext, Uri.parse(resolvedUri))
                } else {
                    setDataSource(resolvedUri)
                }
                setOnCompletionListener {
                    stopAudioPlayback()
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playingAudioId.value = audioId
            _audioProgressFraction.value = 0f

            audioProgressJob = viewModelScope.launch {
                while (isActive && _playingAudioId.value == audioId) {
                    val p = mediaPlayer ?: break
                    val dur = p.duration.coerceAtLeast(1)
                    val pos = p.currentPosition.coerceAtLeast(0)
                    _audioProgressFraction.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    if (!p.isPlaying) break
                    delay(150)
                }
            }
        } catch (_: Throwable) {
            stopAudioPlayback()
            _statusBannerMessage.value = "تعذر تشغيل الملف الصوتي"
        }
    }

    private fun stopAudioPlayback() {
        audioProgressJob?.cancel()
        audioProgressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        _playingAudioId.value = null
        _audioProgressFraction.value = 0f
    }

    fun setReplyMessage(message: DofaatiChatMessage?) {
        _replyingToMessage.value = message
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.toggleChatMessageReaction(messageId, emoji)
        }
    }

    fun approveJoinRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = localStore.getJoinRequestById(requestId) ?: return@launch
            localStore.deleteJoinRequest(requestId)
            val newMember = DofaatiBatchMember(
                id = "mem_${System.currentTimeMillis()}",
                batchId = req.batchId,
                name = req.studentName,
                roleLabel = "عضو",
                isAdmin = false
            )
            localStore.upsertBatchMember(newMember)
            val batch = batches.value.firstOrNull { it.id == req.batchId }
            if (batch != null) {
                localStore.updateBatchStudentsCount(batch.id, batch.studentsCount + 1)
            }
        }
    }

    fun rejectJoinRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteJoinRequest(requestId)
        }
    }

    fun toggleMuteMember(memberId: String) {
        val member = batchMembers.value.firstOrNull { it.id == memberId } ?: return
        if (member.isAdmin) return
        viewModelScope.launch(Dispatchers.IO) {
            localStore.setMemberMuted(memberId, !member.isMuted)
        }
    }

    fun removeMember(memberId: String) {
        val member = batchMembers.value.firstOrNull { it.id == memberId } ?: return
        if (member.isAdmin) return
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteBatchMember(memberId)
            val batch = batches.value.firstOrNull { it.id == member.batchId }
            if (batch != null) {
                localStore.updateBatchStudentsCount(batch.id, (batch.studentsCount - 1).coerceAtLeast(1))
            }
        }
    }

    // Screen 4 & 5: Audio Transcript, Summarization & Quiz Conversion
    fun openAudioTranscript(lessonId: String) {
        _activeLessonId.value = lessonId
        navigateTo(DofaatiRoute.AUDIO_TRANSCRIPT)
    }

    fun updateLessonTranscriptManual(
        lessonId: String,
        transcriptText: String,
        laws: List<String>,
        examPoints: List<String>
    ) {
        val existing = audioLessons.value.firstOrNull { it.id == lessonId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertAudioLesson(
                existing.copy(
                    transcriptText = transcriptText.trim(),
                    extractedLaws = laws.filter { it.isNotBlank() },
                    importantExamPoints = examPoints.filter { it.isNotBlank() }
                )
            )
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم حفظ التفريغ والملاحظات"
            }
        }
    }

    fun transcribeLessonWithAi(lessonId: String, manualNotes: String) {
        if (_isTranscribingAudio.value) return
        val existing = audioLessons.value.firstOrNull { it.id == lessonId } ?: return
        val batch = currentActiveBatch()
        val subjectName = batch?.subjects?.firstOrNull { it.id == existing.subjectId }?.name ?: "عام"

        _isTranscribingAudio.value = true
        viewModelScope.launch {
            val res = questionRepository.transcribeAndSummarizeAudio(
                audioUri = existing.audioUri,
                existingNotes = manualNotes.ifBlank { existing.transcriptText },
                subjectName = subjectName
            )
            _isTranscribingAudio.value = false
            res.onSuccess { summary ->
                withContext(Dispatchers.IO) {
                    localStore.upsertAudioLesson(
                        existing.copy(
                            transcriptText = summary.transcriptText,
                            extractedLaws = summary.extractedLaws,
                            importantExamPoints = summary.importantExamPoints
                        )
                    )
                }
                _statusBannerMessage.value = "تم تفريغ الدرس واستخراج القوانين ونقاط الامتحان بنجاح"
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "تعذر التفريغ بالذكاء الاصطناعي"
            }
        }
    }

    fun convertCurrentTranscriptToQuestions(
        questionCount: Int = 20,
        onReady: (List<DofaatiQuestionItem>) -> Unit
    ) {
        if (_isConvertingTranscript.value) return
        val lesson = audioLessons.value.firstOrNull { it.id == _activeLessonId.value } ?: audioLessons.value.firstOrNull()
        if (lesson == null) {
            _statusBannerMessage.value = "لا يوجد تسجيل صوتي محدد للتحويل"
            return
        }
        val subjectName = currentActiveBatch()?.subjects?.firstOrNull { it.id == lesson.subjectId }?.name ?: "عام"
        _isConvertingTranscript.value = true
        viewModelScope.launch {
            val res = questionRepository.convertAudioTranscriptToQuestions(
                transcriptId = lesson.id,
                transcriptText = lesson.transcriptText,
                extractedLaws = lesson.extractedLaws,
                examPoints = lesson.importantExamPoints,
                subjectName = subjectName,
                requestedCount = questionCount
            )
            _isConvertingTranscript.value = false
            res.onSuccess { questions ->
                withContext(Dispatchers.IO) {
                    localStore.appendQuestions(questions)
                }
                _statusBannerMessage.value = "تم توليد ${questions.size} سؤالاً حقيقياً وحفظها في البنك"
                onReady(questions)
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "تعذر تحويل التفريغ إلى أسئلة"
            }
        }
    }

    // Screen 6: Mind Maps & Flashcards
    fun createNewMindMap(centerTitle: String, branchTitles: List<String>) {
        val cleanCenter = centerTitle.trim()
        val cleanBranches = branchTitles.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
        if (cleanCenter.isEmpty() || cleanBranches.isEmpty()) {
            _statusBannerMessage.value = "أدخل العنوان المركزي وفرعاً واحداً على الأقل"
            return
        }
        val colors = listOf(0xFF10B981L, 0xFFEA580CL, 0xFFF59E0BL, 0xFF8B5CF6L)
        val icons = listOf("BOLT", "RESISTOR", "OHM", "VOLT")
        val branches = cleanBranches.mapIndexed { idx, label ->
            DofaatiMindMapBranch(
                id = "mm_br_${System.currentTimeMillis()}_$idx",
                title = label,
                subtitle = "فرع ضمن $cleanCenter",
                formulaOrBadge = label.take(12),
                accentHex = colors[idx % colors.size],
                iconKey = icons[idx % icons.size]
            )
        }
        val created = DofaatiMindMap(
            id = "mm_${System.currentTimeMillis()}",
            centerTitle = cleanCenter,
            sourceFileTitle = currentActiveBatch()?.subjects?.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "خريطة يدوية",
            branches = branches
        )
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertMindMap(created)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم إنشاء الخريطة الذهنية"
            }
        }
    }

    fun generateMindMapAndCardsWithAi(topicOrNotes: String, sourceTitle: String) {
        if (_isGeneratingMindMap.value) return
        _isGeneratingMindMap.value = true
        viewModelScope.launch {
            val mapRes = questionRepository.generateMindMapAi(topicOrNotes, sourceTitle)
            val cardsRes = questionRepository.generateFlashcardsAi(topicOrNotes, sourceTitle)
            _isGeneratingMindMap.value = false

            var successAny = false
            mapRes.onSuccess { map ->
                withContext(Dispatchers.IO) { localStore.upsertMindMap(map) }
                successAny = true
            }
            cardsRes.onSuccess { cards ->
                withContext(Dispatchers.IO) { localStore.insertFlashcards(cards) }
                successAny = true
            }

            _statusBannerMessage.value = if (successAny) {
                "تم توليد الخريطة الذهنية وبطاقات الاستذكار بنجاح"
            } else {
                mapRes.exceptionOrNull()?.message ?: cardsRes.exceptionOrNull()?.message ?: "تعذر التوليد الذكي"
            }
        }
    }

    fun deleteMindMap(mapId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteMindMap(mapId)
        }
    }

    fun saveFlashcard(card: DofaatiFlashcard) {
        if (card.frontTitle.isBlank()) {
            _statusBannerMessage.value = "اكتب السؤال أو المفهوم أولاً"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertFlashcard(card)
        }
    }

    fun deleteFlashcard(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteFlashcard(cardId)
        }
    }

    // Screen 7: Real AI Bulk Question Generator
    fun startBulkGeneration(
        totalCount: Int,
        difficulties: Set<DifficultyLevel>,
        types: Set<QuestionType>,
        sourceText: String,
        subjectNameOverride: String? = null
    ) {
        if (_isGeneratingBulk.value) return
        _isGeneratingBulk.value = true
        val subjectName = subjectNameOverride?.trim()?.ifBlank { null }
            ?: currentActiveBatch()?.subjects?.firstOrNull { it.id == _selectedSubjectId.value }?.name
            ?: "عام"

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
                    if (progress.accumulatedQuestions.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            localStore.appendQuestions(progress.accumulatedQuestions)
                        }
                    }
                    _isGeneratingBulk.value = false
                    _statusBannerMessage.value = progress.errorMessage ?: progress.statusText
                }
            }
        }
    }

    // Screen 8: Real Question Bank Manual CRUD + PDF Import
    fun addManualQuestionToBank(item: DofaatiQuestionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.appendQuestions(listOf(item))
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تمت إضافة السؤال إلى البنك"
            }
        }
    }

    fun deleteQuestionFromBank(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteQuestion(questionId)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تم حذف السؤال من البنك"
            }
        }
    }

    fun importQuestionsFromPdfUri(uri: Uri) {
        viewModelScope.launch {
            val subjectName = currentActiveBatch()?.subjects?.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "عام"
            val result = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    questionRepository.importQuestionsFromPdfOrText(stream, subjectName)
                } ?: Result.failure(IllegalStateException("تعذر فتح الملف المحدد"))
            }
            result.onSuccess { imported ->
                withContext(Dispatchers.IO) { localStore.appendQuestions(imported) }
                _statusBannerMessage.value = "تم استيراد ${imported.size} سؤالاً حقيقياً إلى البنك"
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "لم يتم استخراج أسئلة من الملف"
            }
        }
    }

    // Screen 9: Admin Bulk Paste Parser + Editable Preview + Publish
    fun parseAdminBulkPastePreview(rawText: String, allowAiFallback: Boolean = false) {
        viewModelScope.launch {
            val subjectName = currentActiveBatch()?.subjects?.firstOrNull { it.id == _selectedSubjectId.value }?.name ?: "عام"
            val parsed = questionRepository.parsePastedBulkQuestions(rawText, subjectName, allowAiFallback)
            _adminParsedQuestions.value = parsed
        }
    }

    fun updateAdminParsedQuestionCorrectOption(questionId: String, correctIdx: Int) {
        _adminParsedQuestions.update { list ->
            list.map { q ->
                if (q.id == questionId && correctIdx in q.options.indices) {
                    q.copy(
                        correctOptionIndex = correctIdx,
                        detailedExplanation = "الإجابة الصحيحة: ${q.options[correctIdx]}"
                    )
                } else q
            }
        }
    }

    fun removeAdminParsedQuestion(questionId: String) {
        _adminParsedQuestions.update { list -> list.filterNot { it.id == questionId } }
    }

    fun publishBulkExamToBatch(examTitle: String) {
        val questionsToPublish = _adminParsedQuestions.value
        if (questionsToPublish.isEmpty()) {
            _statusBannerMessage.value = "الصق أسئلة صالحة بخيارات وإجابات صحيحة أولاً قبل النشر"
            return
        }
        val cleanTitle = examTitle.trim().ifBlank { "تحدي الدفعة المباشر" }
        viewModelScope.launch(Dispatchers.IO) {
            localStore.appendQuestions(questionsToPublish)
            withContext(Dispatchers.Main) {
                launchQuizFromQuestions(title = cleanTitle, questions = questionsToPublish)
            }
        }
    }

    // Screen 10 & 11: Real Live Challenge + Fixed Advance Guard + Attempt Persistence
    fun launchQuizFromQuestions(title: String, questions: List<DofaatiQuestionItem>) {
        if (questions.isEmpty()) {
            _statusBannerMessage.value = "لا توجد أسئلة جاهزة لبدء التحدي بعد"
            return
        }
        quizTimerJob?.cancel()
        quizAdvanceJob?.cancel()
        _liveChallenge.value = LiveChallengeSessionState(
            quizTitle = title.ifBlank { "تحدي الدفعة" },
            onlineParticipantsCount = 1,
            answeredParticipantsCount = 0,
            currentQuestionIndex = 0,
            remainingSeconds = 15,
            questions = questions,
            selectedAnswers = emptyMap(),
            earnedPoints = 0,
            isFinished = false
        )
        navigateTo(DofaatiRoute.LIVE_CHALLENGE)
        startLiveQuizCountdown()
    }

    fun startQuizFromQuestionBank() {
        val bank = questionBankItems.value
        if (bank.isEmpty()) {
            _statusBannerMessage.value = "بنك الأسئلة فارغ — أضف أو استورد أسئلة أولاً"
            return
        }
        val quizSet = bank.filter { it.questionType != QuestionType.ESSAY && it.options.size >= 2 }.take(20)
        if (quizSet.isEmpty()) {
            _statusBannerMessage.value = "أضف أسئلة اختيار من متعدد أو صح وخطأ في البنك لبدء التحدي"
            return
        }
        launchQuizFromQuestions(title = "تحدي بنك الأسئلة (${quizSet.size} سؤال)", questions = quizSet)
    }

    private fun startLiveQuizCountdown() {
        quizTimerJob?.cancel()
        quizTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val st = _liveChallenge.value
                if (st.isFinished || st.questions.isEmpty()) break
                val currentQ = st.questions.getOrNull(st.currentQuestionIndex) ?: break
                // Pause ticking down if user already answered current question
                if (st.selectedAnswers.containsKey(currentQ.id)) continue

                if (st.remainingSeconds > 1) {
                    _liveChallenge.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
                } else {
                    advanceOrFinishQuiz(expectedQuestionIndex = st.currentQuestionIndex)
                }
            }
        }
    }

    fun submitLiveQuizAnswer(optionIndex: Int) {
        val state = _liveChallenge.value
        val idxSnapshot = state.currentQuestionIndex
        val currentQ = state.questions.getOrNull(idxSnapshot) ?: return
        if (state.selectedAnswers.containsKey(currentQ.id)) return

        val isCorrect = optionIndex == currentQ.correctOptionIndex
        val gained = LiveQuizScoringEngine.calculateQuestionScore(isCorrect, state.remainingSeconds)
        val updatedAnswers = state.selectedAnswers + (currentQ.id to optionIndex)

        _liveChallenge.update {
            it.copy(
                selectedAnswers = updatedAnswers,
                earnedPoints = it.earnedPoints + gained,
                answeredParticipantsCount = 1
            )
        }

        quizAdvanceJob?.cancel()
        quizAdvanceJob = viewModelScope.launch {
            delay(1350)
            advanceOrFinishQuiz(expectedQuestionIndex = idxSnapshot)
        }
    }

    fun advanceLiveQuizNow() {
        quizAdvanceJob?.cancel()
        advanceOrFinishQuiz(expectedQuestionIndex = _liveChallenge.value.currentQuestionIndex)
    }

    private fun advanceOrFinishQuiz(expectedQuestionIndex: Int) {
        val state = _liveChallenge.value
        if (state.isFinished || state.currentQuestionIndex != expectedQuestionIndex) return

        if (state.currentQuestionIndex + 1 < state.questions.size) {
            _liveChallenge.update {
                it.copy(
                    currentQuestionIndex = it.currentQuestionIndex + 1,
                    remainingSeconds = 15,
                    answeredParticipantsCount = 0
                )
            }
        } else {
            quizTimerJob?.cancel()
            quizAdvanceJob?.cancel()
            val total = state.questions.size.coerceAtLeast(1)
            val correctCount = state.questions.count { q ->
                state.selectedAnswers[q.id] == q.correctOptionIndex
            }
            val pct = ((correctCount * 100f) / total.toFloat()).roundToInt().coerceIn(0, 100)
            val attempt = DofaatiQuizAttempt(
                id = "attempt_${System.currentTimeMillis()}",
                quizTitle = state.quizTitle.ifBlank { "اختبار" },
                participantName = _currentUserName.value.ifBlank { "أنت" },
                correctCount = correctCount,
                totalQuestions = total,
                scorePercent = pct,
                earnedPoints = state.earnedPoints,
                timestampMillis = System.currentTimeMillis()
            )
            viewModelScope.launch(Dispatchers.IO) {
                localStore.insertQuizAttempt(attempt)
            }

            val allAttempts = (listOf(attempt) + quizAttempts.value)
            val (top3, avg) = LiveQuizScoringEngine.computeLeaderboardFromAttempts(allAttempts)

            _liveChallenge.update {
                it.copy(
                    isFinished = true,
                    leaderboardTopThree = top3,
                    batchAveragePercentage = avg,
                    totalBatchParticipants = allAttempts.size
                )
            }
            if (currentRoute == DofaatiRoute.LIVE_CHALLENGE) {
                navigateTo(DofaatiRoute.LEADERBOARD_RESULTS)
            }
        }
    }

    // Screen 12: Real Schedule CRUD & Image OCR/Vision Extraction
    fun toggleScheduleNotifications(enabled: Boolean) {
        _scheduleRemindersEnabled.value = enabled
        _statusBannerMessage.value = if (enabled) {
            "تم تفعيل إشعارات التذكير التلقائية للمواعيد"
        } else {
            "تم إيقاف إشعارات التذكير"
        }
    }

    fun addManualScheduleEvent(
        title: String,
        subjectName: String,
        dayAndTimeLabel: String,
        isExam: Boolean,
        hoursFromNow: Int
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _statusBannerMessage.value = "اكتب عنوان الموعد أو الامتحان"
            return
        }
        val targetMs = System.currentTimeMillis() + (hoursFromNow.coerceAtLeast(1) * 3_600_000L)
        val event = DofaatiScheduleEvent(
            id = "ev_${System.currentTimeMillis()}",
            dayAndTime = dayAndTimeLabel.trim().ifBlank { "قريباً" },
            subjectName = subjectName.trim().ifBlank { "عام" },
            title = cleanTitle,
            isExam = isExam,
            targetTimestampMillis = targetMs
        )
        viewModelScope.launch(Dispatchers.IO) {
            localStore.upsertScheduleEvent(event)
            withContext(Dispatchers.Main) {
                _statusBannerMessage.value = "تمت إضافة الموعد إلى الجدول"
            }
        }
    }

    fun deleteScheduleEvent(eventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localStore.deleteScheduleEvent(eventId)
        }
    }

    fun extractScheduleFromImageUri(uri: Uri) {
        viewModelScope.launch {
            val res = questionRepository.extractScheduleFromImage(uri)
            res.onSuccess { extracted ->
                withContext(Dispatchers.IO) { localStore.upsertScheduleEvent(extracted) }
                _statusBannerMessage.value = "تم استخراج الموعد «${extracted.title}» من الصورة وإضافته للجدول"
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "لم يتم العثور على جدول واضح في الصورة"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
        recorderAmplitudeJob?.cancel()
        quizTimerJob?.cancel()
        quizAdvanceJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }
    }
}
