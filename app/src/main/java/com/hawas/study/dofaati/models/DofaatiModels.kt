package com.hawas.study.dofaati.models

enum class QuestionType(val labelAr: String) {
    MCQ("اختيار من متعدد"),
    TRUE_FALSE("صح وخطأ"),
    ESSAY("مقالي")
}

enum class DifficultyLevel(val labelAr: String) {
    EASY("سهل"),
    MEDIUM("متوسط"),
    HIGHER_ORDER("مستويات عليا")
}

enum class UploadPermissionMode(val labelAr: String) {
    ADMIN_ONLY("أنا بس"),
    ANY_MEMBER("أي عضو")
}

data class DofaatiQuestionItem(
    val id: String,
    val questionNumber: Int,
    val subjectName: String,
    val questionText: String,
    val questionType: QuestionType,
    val difficulty: DifficultyLevel,
    val options: List<String>,
    val correctOptionIndex: Int,
    val circuitDiagramType: String? = null,
    val detailedExplanation: String,
    val sourceTag: String
)

data class BulkGenerationProgress(
    val generatedCount: Int,
    val totalRequested: Int,
    val currentBatchIndex: Int,
    val totalBatches: Int,
    val latestBatchQuestions: List<DofaatiQuestionItem>,
    val accumulatedQuestions: List<DofaatiQuestionItem>,
    val isCompleted: Boolean,
    val statusText: String,
    val errorMessage: String? = null
)

data class PodiumStudent(
    val rank: Int,
    val name: String,
    val scorePercent: Int,
    val points: Int
)

data class DofaatiQuizAttempt(
    val id: String,
    val quizTitle: String,
    val participantName: String,
    val correctCount: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val earnedPoints: Int,
    val timestampMillis: Long
)

data class LiveChallengeSessionState(
    val quizTitle: String = "",
    val onlineParticipantsCount: Int = 1,
    val answeredParticipantsCount: Int = 0,
    val currentQuestionIndex: Int = 0,
    val remainingSeconds: Int = 15,
    val questions: List<DofaatiQuestionItem> = emptyList(),
    val selectedAnswers: Map<String, Int> = emptyMap(),
    val earnedPoints: Int = 0,
    val isFinished: Boolean = false,
    val leaderboardTopThree: List<PodiumStudent> = emptyList(),
    val batchAveragePercentage: Int = 0,
    val totalBatchParticipants: Int = 0
)

data class DofaatiBatch(
    val id: String,
    val name: String,
    val code: String,
    val logoColorHex: Long,
    val adminName: String,
    val studentsCount: Int,
    val subjects: List<DofaatiSubject>,
    val uploadPermission: UploadPermissionMode = UploadPermissionMode.ADMIN_ONLY,
    val requireApproval: Boolean = true,
    val pinnedNotice: String = ""
)

data class DofaatiSubject(
    val id: String,
    val batchId: String = "",
    val name: String,
    val recordingsCount: Int = 0,
    val filesCount: Int = 0
)

data class DofaatiAudioLesson(
    val id: String,
    val subjectId: String,
    val title: String,
    val durationSec: Int,
    val waveformPeaks: List<Float>,
    val audioUri: String = "",
    val transcriptText: String = "",
    val extractedLaws: List<String> = emptyList(),
    val importantExamPoints: List<String> = emptyList()
)

data class DofaatiStudyFile(
    val id: String,
    val subjectId: String,
    val title: String,
    val pageCount: Int,
    val fileSizeLabel: String,
    val fileUri: String = "",
    val extractedText: String = ""
)

data class DofaatiChatMessage(
    val id: String,
    val batchId: String = "",
    val senderName: String,
    val senderInitial: String,
    val isAdmin: Boolean,
    val isMe: Boolean,
    val isAudio: Boolean,
    val text: String,
    val audioUri: String = "",
    val audioDurationSec: Int = 0,
    val waveformPeaks: List<Float> = emptyList(),
    val timestamp: String,
    val reactions: Map<String, Int> = emptyMap(),
    val replyToPreview: String? = null
)

data class DofaatiJoinRequest(
    val id: String,
    val batchId: String = "",
    val studentName: String,
    val studentInitial: String,
    val requestedAt: String,
    val note: String
)

data class DofaatiBatchMember(
    val id: String,
    val batchId: String = "",
    val name: String,
    val roleLabel: String,
    val isAdmin: Boolean,
    val isMuted: Boolean = false
)

data class DofaatiMindMapBranch(
    val id: String,
    val title: String,
    val subtitle: String,
    val formulaOrBadge: String,
    val accentHex: Long,
    val iconKey: String
)

data class DofaatiMindMap(
    val id: String,
    val centerTitle: String,
    val sourceFileTitle: String,
    val branches: List<DofaatiMindMapBranch>
)

data class DofaatiFlashcard(
    val id: String,
    val sourceTitle: String,
    val frontTitle: String,
    val frontSubtitle: String,
    val backTitle: String,
    val backFormula: String,
    val backExplanation: String
)

data class DofaatiScheduleEvent(
    val id: String,
    val dayAndTime: String,
    val subjectName: String,
    val title: String,
    val isExam: Boolean,
    val targetTimestampMillis: Long = 0L
)

enum class DofaatiRoute(val isFullScreen: Boolean) {
    HUB(false),
    CREATE_BATCH(true),
    CHAT_AND_ADMIN(true),
    SUBJECT_DETAILS(false),
    AUDIO_TRANSCRIPT(true),
    MIND_MAP_FLASHCARDS(true),
    BULK_AI_GENERATOR(true),
    QUESTION_BANK(true),
    ADMIN_EXAM_CREATOR(true),
    LIVE_CHALLENGE(true),
    LEADERBOARD_RESULTS(true),
    SCHEDULE_COUNTDOWN(false)
}
