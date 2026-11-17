package com.hawas.study.dofaati.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.models.DofaatiStudyFile
import com.hawas.study.dofaati.models.DofaatiSubject
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.models.UploadPermissionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private object DofaatiJsonCodec {
    fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun encodeFloatList(list: List<Float>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toDouble()) }
        return arr.toString()
    }

    fun decodeFloatList(raw: String): List<Float> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.optDouble(i, 0.4).toFloat() }
        }.getOrDefault(emptyList())
    }

    fun encodeReactions(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeReactions(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = linkedMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optInt(k, 0)
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun encodeBranches(branches: List<DofaatiMindMapBranch>): String {
        val arr = JSONArray()
        branches.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("subtitle", b.subtitle)
                    put("formulaOrBadge", b.formulaOrBadge)
                    put("accentHex", b.accentHex)
                    put("iconKey", b.iconKey)
                }
            )
        }
        return arr.toString()
    }

    fun decodeBranches(raw: String): List<DofaatiMindMapBranch> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                DofaatiMindMapBranch(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    subtitle = obj.optString("subtitle"),
                    formulaOrBadge = obj.optString("formulaOrBadge"),
                    accentHex = obj.optLong("accentHex", 0xFF1A6FE3L),
                    iconKey = obj.optString("iconKey", "BOLT")
                )
            }
        }.getOrDefault(emptyList())
    }
}

@Entity(tableName = "dofaati_batches")
data class DofaatiBatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val logoColorHex: Long,
    val adminName: String,
    val studentsCount: Int,
    val uploadPermissionName: String,
    val requireApproval: Boolean,
    val pinnedNotice: String,
    val createdAtMillis: Long
) {
    fun toDomain(subjects: List<DofaatiSubject>): DofaatiBatch = DofaatiBatch(
        id = id,
        name = name,
        code = code,
        logoColorHex = logoColorHex,
        adminName = adminName,
        studentsCount = studentsCount,
        subjects = subjects,
        uploadPermission = runCatching { UploadPermissionMode.valueOf(uploadPermissionName) }
            .getOrDefault(UploadPermissionMode.ADMIN_ONLY),
        requireApproval = requireApproval,
        pinnedNotice = pinnedNotice
    )

    companion object {
        fun fromDomain(batch: DofaatiBatch, createdAtMillis: Long = System.currentTimeMillis()): DofaatiBatchEntity =
            DofaatiBatchEntity(
                id = batch.id,
                name = batch.name,
                code = batch.code,
                logoColorHex = batch.logoColorHex,
                adminName = batch.adminName,
                studentsCount = batch.studentsCount,
                uploadPermissionName = batch.uploadPermission.name,
                requireApproval = batch.requireApproval,
                pinnedNotice = batch.pinnedNotice,
                createdAtMillis = createdAtMillis
            )
    }
}

@Entity(tableName = "dofaati_subjects")
data class DofaatiSubjectEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val name: String,
    val createdAtMillis: Long
) {
    fun toDomain(recordingsCount: Int, filesCount: Int): DofaatiSubject = DofaatiSubject(
        id = id,
        batchId = batchId,
        name = name,
        recordingsCount = recordingsCount,
        filesCount = filesCount
    )
}

@Entity(tableName = "dofaati_audio_lessons")
data class DofaatiAudioLessonEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val durationSec: Int,
    val waveformJson: String,
    val audioUri: String,
    val transcriptText: String,
    val extractedLawsJson: String,
    val importantPointsJson: String,
    val createdAtMillis: Long
) {
    fun toDomain(): DofaatiAudioLesson = DofaatiAudioLesson(
        id = id,
        subjectId = subjectId,
        title = title,
        durationSec = durationSec,
        waveformPeaks = DofaatiJsonCodec.decodeFloatList(waveformJson),
        audioUri = audioUri,
        transcriptText = transcriptText,
        extractedLaws = DofaatiJsonCodec.decodeStringList(extractedLawsJson),
        importantExamPoints = DofaatiJsonCodec.decodeStringList(importantPointsJson)
    )

    companion object {
        fun fromDomain(lesson: DofaatiAudioLesson, createdAtMillis: Long = System.currentTimeMillis()): DofaatiAudioLessonEntity =
            DofaatiAudioLessonEntity(
                id = lesson.id,
                subjectId = lesson.subjectId,
                title = lesson.title,
                durationSec = lesson.durationSec,
                waveformJson = DofaatiJsonCodec.encodeFloatList(lesson.waveformPeaks),
                audioUri = lesson.audioUri,
                transcriptText = lesson.transcriptText,
                extractedLawsJson = DofaatiJsonCodec.encodeStringList(lesson.extractedLaws),
                importantPointsJson = DofaatiJsonCodec.encodeStringList(lesson.importantExamPoints),
                createdAtMillis = createdAtMillis
            )
    }
}

@Entity(tableName = "dofaati_study_files")
data class DofaatiStudyFileEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val pageCount: Int,
    val fileSizeLabel: String,
    val fileUri: String,
    val extractedText: String,
    val createdAtMillis: Long
) {
    fun toDomain(): DofaatiStudyFile = DofaatiStudyFile(
        id = id,
        subjectId = subjectId,
        title = title,
        pageCount = pageCount,
        fileSizeLabel = fileSizeLabel,
        fileUri = fileUri,
        extractedText = extractedText
    )

    companion object {
        fun fromDomain(file: DofaatiStudyFile, createdAtMillis: Long = System.currentTimeMillis()): DofaatiStudyFileEntity =
            DofaatiStudyFileEntity(
                id = file.id,
                subjectId = file.subjectId,
                title = file.title,
                pageCount = file.pageCount,
                fileSizeLabel = file.fileSizeLabel,
                fileUri = file.fileUri,
                extractedText = file.extractedText,
                createdAtMillis = createdAtMillis
            )
    }
}

@Entity(tableName = "dofaati_chat_messages")
data class DofaatiChatMessageEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val senderName: String,
    val senderInitial: String,
    val isAdmin: Boolean,
    val isMe: Boolean,
    val isAudio: Boolean,
    val text: String,
    val audioUri: String,
    val audioDurationSec: Int,
    val waveformJson: String,
    val timestamp: String,
    val reactionsJson: String,
    val replyToPreview: String?,
    val createdAtMillis: Long
) {
    fun toDomain(): DofaatiChatMessage = DofaatiChatMessage(
        id = id,
        batchId = batchId,
        senderName = senderName,
        senderInitial = senderInitial,
        isAdmin = isAdmin,
        isMe = isMe,
        isAudio = isAudio,
        text = text,
        audioUri = audioUri,
        audioDurationSec = audioDurationSec,
        waveformPeaks = DofaatiJsonCodec.decodeFloatList(waveformJson),
        timestamp = timestamp,
        reactions = DofaatiJsonCodec.decodeReactions(reactionsJson),
        replyToPreview = replyToPreview
    )

    companion object {
        fun fromDomain(msg: DofaatiChatMessage, createdAtMillis: Long = System.currentTimeMillis()): DofaatiChatMessageEntity =
            DofaatiChatMessageEntity(
                id = msg.id,
                batchId = msg.batchId,
                senderName = msg.senderName,
                senderInitial = msg.senderInitial,
                isAdmin = msg.isAdmin,
                isMe = msg.isMe,
                isAudio = msg.isAudio,
                text = msg.text,
                audioUri = msg.audioUri,
                audioDurationSec = msg.audioDurationSec,
                waveformJson = DofaatiJsonCodec.encodeFloatList(msg.waveformPeaks),
                timestamp = msg.timestamp,
                reactionsJson = DofaatiJsonCodec.encodeReactions(msg.reactions),
                replyToPreview = msg.replyToPreview,
                createdAtMillis = createdAtMillis
            )
    }
}

@Entity(tableName = "dofaati_join_requests")
data class DofaatiJoinRequestEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val studentName: String,
    val studentInitial: String,
    val requestedAt: String,
    val note: String
) {
    fun toDomain(): DofaatiJoinRequest = DofaatiJoinRequest(
        id = id,
        batchId = batchId,
        studentName = studentName,
        studentInitial = studentInitial,
        requestedAt = requestedAt,
        note = note
    )

    companion object {
        fun fromDomain(req: DofaatiJoinRequest): DofaatiJoinRequestEntity = DofaatiJoinRequestEntity(
            id = req.id,
            batchId = req.batchId,
            studentName = req.studentName,
            studentInitial = req.studentInitial,
            requestedAt = req.requestedAt,
            note = req.note
        )
    }
}

@Entity(tableName = "dofaati_batch_members")
data class DofaatiBatchMemberEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val name: String,
    val roleLabel: String,
    val isAdmin: Boolean,
    val isMuted: Boolean
) {
    fun toDomain(): DofaatiBatchMember = DofaatiBatchMember(
        id = id,
        batchId = batchId,
        name = name,
        roleLabel = roleLabel,
        isAdmin = isAdmin,
        isMuted = isMuted
    )

    companion object {
        fun fromDomain(member: DofaatiBatchMember): DofaatiBatchMemberEntity = DofaatiBatchMemberEntity(
            id = member.id,
            batchId = member.batchId,
            name = member.name,
            roleLabel = member.roleLabel,
            isAdmin = member.isAdmin,
            isMuted = member.isMuted
        )
    }
}

@Entity(tableName = "dofaati_mind_maps")
data class DofaatiMindMapEntity(
    @PrimaryKey val id: String,
    val centerTitle: String,
    val sourceFileTitle: String,
    val branchesJson: String,
    val createdAtMillis: Long
) {
    fun toDomain(): DofaatiMindMap = DofaatiMindMap(
        id = id,
        centerTitle = centerTitle,
        sourceFileTitle = sourceFileTitle,
        branches = DofaatiJsonCodec.decodeBranches(branchesJson)
    )

    companion object {
        fun fromDomain(map: DofaatiMindMap, createdAtMillis: Long = System.currentTimeMillis()): DofaatiMindMapEntity =
            DofaatiMindMapEntity(
                id = map.id,
                centerTitle = map.centerTitle,
                sourceFileTitle = map.sourceFileTitle,
                branchesJson = DofaatiJsonCodec.encodeBranches(map.branches),
                createdAtMillis = createdAtMillis
            )
    }
}

@Entity(tableName = "dofaati_schedule_events")
data class DofaatiScheduleEventEntity(
    @PrimaryKey val id: String,
    val dayAndTime: String,
    val subjectName: String,
    val title: String,
    val isExam: Boolean,
    val targetTimestampMillis: Long
) {
    fun toDomain(): DofaatiScheduleEvent = DofaatiScheduleEvent(
        id = id,
        dayAndTime = dayAndTime,
        subjectName = subjectName,
        title = title,
        isExam = isExam,
        targetTimestampMillis = targetTimestampMillis
    )

    companion object {
        fun fromDomain(event: DofaatiScheduleEvent): DofaatiScheduleEventEntity = DofaatiScheduleEventEntity(
            id = event.id,
            dayAndTime = event.dayAndTime,
            subjectName = event.subjectName,
            title = event.title,
            isExam = event.isExam,
            targetTimestampMillis = event.targetTimestampMillis
        )
    }
}

@Entity(tableName = "dofaati_quiz_attempts")
data class DofaatiQuizAttemptEntity(
    @PrimaryKey val id: String,
    val quizTitle: String,
    val participantName: String,
    val correctCount: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val earnedPoints: Int,
    val timestampMillis: Long
) {
    fun toDomain(): DofaatiQuizAttempt = DofaatiQuizAttempt(
        id = id,
        quizTitle = quizTitle,
        participantName = participantName,
        correctCount = correctCount,
        totalQuestions = totalQuestions,
        scorePercent = scorePercent,
        earnedPoints = earnedPoints,
        timestampMillis = timestampMillis
    )

    companion object {
        fun fromDomain(attempt: DofaatiQuizAttempt): DofaatiQuizAttemptEntity = DofaatiQuizAttemptEntity(
            id = attempt.id,
            quizTitle = attempt.quizTitle,
            participantName = attempt.participantName,
            correctCount = attempt.correctCount,
            totalQuestions = attempt.totalQuestions,
            scorePercent = attempt.scorePercent,
            earnedPoints = attempt.earnedPoints,
            timestampMillis = attempt.timestampMillis
        )
    }
}

@Entity(tableName = "dofaati_questions")
data class DofaatiQuestionEntity(
    @PrimaryKey val id: String,
    val questionNumber: Int,
    val subjectName: String,
    val questionText: String,
    val questionTypeName: String,
    val difficultyName: String,
    val optionsJoined: String,
    val correctOptionIndex: Int,
    val circuitDiagramType: String?,
    val detailedExplanation: String,
    val sourceTag: String
) {
    fun toDomain(): DofaatiQuestionItem = DofaatiQuestionItem(
        id = id,
        questionNumber = questionNumber,
        subjectName = subjectName,
        questionText = questionText,
        questionType = runCatching { QuestionType.valueOf(questionTypeName) }.getOrDefault(QuestionType.MCQ),
        difficulty = runCatching { DifficultyLevel.valueOf(difficultyName) }.getOrDefault(DifficultyLevel.MEDIUM),
        options = if (optionsJoined.startsWith("[")) {
            DofaatiJsonCodec.decodeStringList(optionsJoined)
        } else {
            optionsJoined.split(OPTION_DELIMITER).filter { it.isNotEmpty() }
        },
        correctOptionIndex = correctOptionIndex,
        circuitDiagramType = circuitDiagramType,
        detailedExplanation = detailedExplanation,
        sourceTag = sourceTag
    )

    companion object {
        private const val OPTION_DELIMITER = "\u001F"

        fun fromDomain(item: DofaatiQuestionItem): DofaatiQuestionEntity = DofaatiQuestionEntity(
            id = item.id,
            questionNumber = item.questionNumber,
            subjectName = item.subjectName,
            questionText = item.questionText,
            questionTypeName = item.questionType.name,
            difficultyName = item.difficulty.name,
            optionsJoined = DofaatiJsonCodec.encodeStringList(item.options),
            correctOptionIndex = item.correctOptionIndex,
            circuitDiagramType = item.circuitDiagramType,
            detailedExplanation = item.detailedExplanation,
            sourceTag = item.sourceTag
        )
    }
}

@Entity(tableName = "dofaati_flashcards")
data class DofaatiFlashcardEntity(
    @PrimaryKey val id: String,
    val sourceTitle: String,
    val frontTitle: String,
    val frontSubtitle: String,
    val backTitle: String,
    val backFormula: String,
    val backExplanation: String
) {
    fun toDomain(): DofaatiFlashcard = DofaatiFlashcard(
        id = id,
        sourceTitle = sourceTitle,
        frontTitle = frontTitle,
        frontSubtitle = frontSubtitle,
        backTitle = backTitle,
        backFormula = backFormula,
        backExplanation = backExplanation
    )

    companion object {
        fun fromDomain(card: DofaatiFlashcard): DofaatiFlashcardEntity = DofaatiFlashcardEntity(
            id = card.id,
            sourceTitle = card.sourceTitle,
            frontTitle = card.frontTitle,
            frontSubtitle = card.frontSubtitle,
            backTitle = card.backTitle,
            backFormula = card.backFormula,
            backExplanation = card.backExplanation
        )
    }
}

@Dao
interface DofaatiDao {
    // Questions
    @Query("SELECT * FROM dofaati_questions ORDER BY questionNumber ASC")
    fun observeAllQuestions(): Flow<List<DofaatiQuestionEntity>>

    @Query("SELECT COUNT(*) FROM dofaati_questions")
    suspend fun countQuestions(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(items: List<DofaatiQuestionEntity>)

    @Query("DELETE FROM dofaati_questions WHERE id = :id")
    suspend fun deleteQuestion(id: String)

    // Flashcards
    @Query("SELECT * FROM dofaati_flashcards")
    fun observeAllFlashcards(): Flow<List<DofaatiFlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<DofaatiFlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlashcard(card: DofaatiFlashcardEntity)

    @Query("DELETE FROM dofaati_flashcards WHERE id = :id")
    suspend fun deleteFlashcard(id: String)

    // Batches & Subjects
    @Query("SELECT * FROM dofaati_batches ORDER BY createdAtMillis DESC")
    fun observeAllBatches(): Flow<List<DofaatiBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(batch: DofaatiBatchEntity)

    @Query("UPDATE dofaati_batches SET pinnedNotice = :notice WHERE id = :batchId")
    suspend fun updateBatchPinnedNotice(batchId: String, notice: String)

    @Query("UPDATE dofaati_batches SET studentsCount = :count WHERE id = :batchId")
    suspend fun updateBatchStudentsCount(batchId: String, count: Int)

    @Query("DELETE FROM dofaati_batches WHERE id = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("SELECT * FROM dofaati_subjects ORDER BY createdAtMillis ASC")
    fun observeAllSubjects(): Flow<List<DofaatiSubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<DofaatiSubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubject(subject: DofaatiSubjectEntity)

    @Query("DELETE FROM dofaati_subjects WHERE id = :subjectId")
    suspend fun deleteSubject(subjectId: String)

    @Query("DELETE FROM dofaati_subjects WHERE batchId = :batchId")
    suspend fun deleteSubjectsForBatch(batchId: String)

    // Audio Lessons & Study Files
    @Query("SELECT * FROM dofaati_audio_lessons ORDER BY createdAtMillis DESC")
    fun observeAllAudioLessons(): Flow<List<DofaatiAudioLessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudioLesson(lesson: DofaatiAudioLessonEntity)

    @Query("DELETE FROM dofaati_audio_lessons WHERE id = :id")
    suspend fun deleteAudioLesson(id: String)

    @Query("SELECT * FROM dofaati_study_files ORDER BY createdAtMillis DESC")
    fun observeAllStudyFiles(): Flow<List<DofaatiStudyFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyFile(file: DofaatiStudyFileEntity)

    @Query("DELETE FROM dofaati_study_files WHERE id = :id")
    suspend fun deleteStudyFile(id: String)

    // Chat Messages
    @Query("SELECT * FROM dofaati_chat_messages ORDER BY createdAtMillis ASC")
    fun observeAllChatMessages(): Flow<List<DofaatiChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(message: DofaatiChatMessageEntity)

    @Query("SELECT * FROM dofaati_chat_messages WHERE id = :id LIMIT 1")
    suspend fun getChatMessageById(id: String): DofaatiChatMessageEntity?

    // Join Requests & Members
    @Query("SELECT * FROM dofaati_join_requests")
    fun observeAllJoinRequests(): Flow<List<DofaatiJoinRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJoinRequest(req: DofaatiJoinRequestEntity)

    @Query("SELECT * FROM dofaati_join_requests WHERE id = :id LIMIT 1")
    suspend fun getJoinRequestById(id: String): DofaatiJoinRequestEntity?

    @Query("DELETE FROM dofaati_join_requests WHERE id = :id")
    suspend fun deleteJoinRequest(id: String)

    @Query("SELECT * FROM dofaati_batch_members")
    fun observeAllBatchMembers(): Flow<List<DofaatiBatchMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatchMember(member: DofaatiBatchMemberEntity)

    @Query("UPDATE dofaati_batch_members SET isMuted = :muted WHERE id = :id")
    suspend fun setMemberMuted(id: String, muted: Boolean)

    @Query("DELETE FROM dofaati_batch_members WHERE id = :id")
    suspend fun deleteBatchMember(id: String)

    // Mind Maps
    @Query("SELECT * FROM dofaati_mind_maps ORDER BY createdAtMillis DESC")
    fun observeAllMindMaps(): Flow<List<DofaatiMindMapEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMindMap(map: DofaatiMindMapEntity)

    @Query("DELETE FROM dofaati_mind_maps WHERE id = :id")
    suspend fun deleteMindMap(id: String)

    // Schedule Events
    @Query("SELECT * FROM dofaati_schedule_events ORDER BY targetTimestampMillis ASC")
    fun observeAllScheduleEvents(): Flow<List<DofaatiScheduleEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleEvent(event: DofaatiScheduleEventEntity)

    @Query("DELETE FROM dofaati_schedule_events WHERE id = :id")
    suspend fun deleteScheduleEvent(id: String)

    // Quiz Attempts
    @Query("SELECT * FROM dofaati_quiz_attempts ORDER BY timestampMillis DESC")
    fun observeAllQuizAttempts(): Flow<List<DofaatiQuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: DofaatiQuizAttemptEntity)
}

@Database(
    entities = [
        DofaatiQuestionEntity::class,
        DofaatiFlashcardEntity::class,
        DofaatiBatchEntity::class,
        DofaatiSubjectEntity::class,
        DofaatiAudioLessonEntity::class,
        DofaatiStudyFileEntity::class,
        DofaatiChatMessageEntity::class,
        DofaatiJoinRequestEntity::class,
        DofaatiBatchMemberEntity::class,
        DofaatiMindMapEntity::class,
        DofaatiScheduleEventEntity::class,
        DofaatiQuizAttemptEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DofaatiDatabase : RoomDatabase() {
    abstract fun dofaatiDao(): DofaatiDao

    companion object {
        @Volatile
        private var INSTANCE: DofaatiDatabase? = null

        fun getInstance(context: Context): DofaatiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DofaatiDatabase::class.java,
                    "dofaati_isolated_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class DofaatiLocalStore(private val dao: DofaatiDao) {

    fun observeQuestions(): Flow<List<DofaatiQuestionItem>> =
        dao.observeAllQuestions().map { list -> list.map { it.toDomain() } }

    fun observeFlashcards(): Flow<List<DofaatiFlashcard>> =
        dao.observeAllFlashcards().map { list -> list.map { it.toDomain() } }

    fun observeBatches(): Flow<List<DofaatiBatch>> =
        combine(
            dao.observeAllBatches(),
            dao.observeAllSubjects(),
            dao.observeAllAudioLessons(),
            dao.observeAllStudyFiles()
        ) { batchEntities, subjectEntities, lessonEntities, fileEntities ->
            val lessonsBySubject = lessonEntities.groupingBy { it.subjectId }.eachCount()
            val filesBySubject = fileEntities.groupingBy { it.subjectId }.eachCount()
            val subjectsByBatch = subjectEntities.groupBy { it.batchId }

            batchEntities.map { batch ->
                val subjects = (subjectsByBatch[batch.id] ?: emptyList()).map { s ->
                    s.toDomain(
                        recordingsCount = lessonsBySubject[s.id] ?: 0,
                        filesCount = filesBySubject[s.id] ?: 0
                    )
                }
                batch.toDomain(subjects)
            }
        }

    fun observeAudioLessons(): Flow<List<DofaatiAudioLesson>> =
        dao.observeAllAudioLessons().map { list -> list.map { it.toDomain() } }

    fun observeStudyFiles(): Flow<List<DofaatiStudyFile>> =
        dao.observeAllStudyFiles().map { list -> list.map { it.toDomain() } }

    fun observeChatMessages(): Flow<List<DofaatiChatMessage>> =
        dao.observeAllChatMessages().map { list -> list.map { it.toDomain() } }

    fun observeJoinRequests(): Flow<List<DofaatiJoinRequest>> =
        dao.observeAllJoinRequests().map { list -> list.map { it.toDomain() } }

    fun observeBatchMembers(): Flow<List<DofaatiBatchMember>> =
        dao.observeAllBatchMembers().map { list -> list.map { it.toDomain() } }

    fun observeMindMaps(): Flow<List<DofaatiMindMap>> =
        dao.observeAllMindMaps().map { list -> list.map { it.toDomain() } }

    fun observeScheduleEvents(): Flow<List<DofaatiScheduleEvent>> =
        dao.observeAllScheduleEvents().map { list -> list.map { it.toDomain() } }

    fun observeQuizAttempts(): Flow<List<DofaatiQuizAttempt>> =
        dao.observeAllQuizAttempts().map { list -> list.map { it.toDomain() } }

    suspend fun appendQuestions(newItems: List<DofaatiQuestionItem>) {
        if (newItems.isEmpty()) return
        val existingCount = dao.countQuestions()
        val renumbered = newItems.mapIndexed { idx, item ->
            item.copy(
                id = if (item.id.isBlank()) "q_${existingCount + idx + 1}_${System.nanoTime()}" else item.id,
                questionNumber = existingCount + idx + 1
            )
        }
        dao.insertQuestions(renumbered.map { DofaatiQuestionEntity.fromDomain(it) })
    }

    suspend fun upsertQuestion(item: DofaatiQuestionItem) {
        val number = if (item.questionNumber > 0) item.questionNumber else dao.countQuestions() + 1
        dao.insertQuestions(listOf(DofaatiQuestionEntity.fromDomain(item.copy(questionNumber = number))))
    }

    suspend fun deleteQuestion(id: String) {
        dao.deleteQuestion(id)
    }

    suspend fun upsertFlashcard(card: DofaatiFlashcard) {
        dao.upsertFlashcard(DofaatiFlashcardEntity.fromDomain(card))
    }

    suspend fun insertFlashcards(cards: List<DofaatiFlashcard>) {
        if (cards.isNotEmpty()) {
            dao.insertFlashcards(cards.map { DofaatiFlashcardEntity.fromDomain(it) })
        }
    }

    suspend fun deleteFlashcard(id: String) {
        dao.deleteFlashcard(id)
    }

    suspend fun saveBatchWithInitialSetup(
        batch: DofaatiBatch,
        adminMember: DofaatiBatchMember
    ) {
        val now = System.currentTimeMillis()
        dao.upsertBatch(DofaatiBatchEntity.fromDomain(batch, now))
        dao.insertSubjects(
            batch.subjects.mapIndexed { idx, s ->
                DofaatiSubjectEntity(
                    id = s.id,
                    batchId = batch.id,
                    name = s.name,
                    createdAtMillis = now + idx
                )
            }
        )
        dao.upsertBatchMember(DofaatiBatchMemberEntity.fromDomain(adminMember))
    }

    suspend fun updateBatchPinnedNotice(batchId: String, notice: String) {
        dao.updateBatchPinnedNotice(batchId, notice)
    }

    suspend fun updateBatchStudentsCount(batchId: String, count: Int) {
        dao.updateBatchStudentsCount(batchId, count)
    }

    suspend fun deleteBatch(batchId: String) {
        dao.deleteSubjectsForBatch(batchId)
        dao.deleteBatch(batchId)
    }

    suspend fun addSubject(batchId: String, subjectName: String): DofaatiSubject {
        val sub = DofaatiSubject(
            id = "sub_${System.currentTimeMillis()}_${System.nanoTime()}",
            batchId = batchId,
            name = subjectName.trim()
        )
        dao.upsertSubject(
            DofaatiSubjectEntity(
                id = sub.id,
                batchId = batchId,
                name = sub.name,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return sub
    }

    suspend fun deleteSubject(subjectId: String) {
        dao.deleteSubject(subjectId)
    }

    suspend fun upsertAudioLesson(lesson: DofaatiAudioLesson) {
        dao.upsertAudioLesson(DofaatiAudioLessonEntity.fromDomain(lesson))
    }

    suspend fun deleteAudioLesson(id: String) {
        dao.deleteAudioLesson(id)
    }

    suspend fun upsertStudyFile(file: DofaatiStudyFile) {
        dao.upsertStudyFile(DofaatiStudyFileEntity.fromDomain(file))
    }

    suspend fun deleteStudyFile(id: String) {
        dao.deleteStudyFile(id)
    }

    suspend fun upsertChatMessage(message: DofaatiChatMessage) {
        dao.upsertChatMessage(DofaatiChatMessageEntity.fromDomain(message))
    }

    suspend fun toggleChatMessageReaction(messageId: String, emoji: String) {
        val existing = dao.getChatMessageById(messageId)?.toDomain() ?: return
        val updated = existing.reactions.toMutableMap()
        updated[emoji] = (updated[emoji] ?: 0) + 1
        dao.upsertChatMessage(DofaatiChatMessageEntity.fromDomain(existing.copy(reactions = updated)))
    }

    suspend fun addJoinRequest(req: DofaatiJoinRequest) {
        dao.upsertJoinRequest(DofaatiJoinRequestEntity.fromDomain(req))
    }

    suspend fun getJoinRequestById(id: String): DofaatiJoinRequest? =
        dao.getJoinRequestById(id)?.toDomain()

    suspend fun deleteJoinRequest(id: String) {
        dao.deleteJoinRequest(id)
    }

    suspend fun upsertBatchMember(member: DofaatiBatchMember) {
        dao.upsertBatchMember(DofaatiBatchMemberEntity.fromDomain(member))
    }

    suspend fun setMemberMuted(id: String, muted: Boolean) {
        dao.setMemberMuted(id, muted)
    }

    suspend fun deleteBatchMember(id: String) {
        dao.deleteBatchMember(id)
    }

    suspend fun upsertMindMap(map: DofaatiMindMap) {
        dao.upsertMindMap(DofaatiMindMapEntity.fromDomain(map))
    }

    suspend fun deleteMindMap(id: String) {
        dao.deleteMindMap(id)
    }

    suspend fun upsertScheduleEvent(event: DofaatiScheduleEvent) {
        dao.upsertScheduleEvent(DofaatiScheduleEventEntity.fromDomain(event))
    }

    suspend fun deleteScheduleEvent(id: String) {
        dao.deleteScheduleEvent(id)
    }

    suspend fun insertQuizAttempt(attempt: DofaatiQuizAttempt) {
        dao.insertQuizAttempt(DofaatiQuizAttemptEntity.fromDomain(attempt))
    }
}
