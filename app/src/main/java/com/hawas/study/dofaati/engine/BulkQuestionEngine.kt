package com.hawas.study.dofaati.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hawas.study.dofaati.models.BulkGenerationProgress
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.DofaatiFlashcard
import com.hawas.study.dofaati.models.DofaatiMindMap
import com.hawas.study.dofaati.models.DofaatiMindMapBranch
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.DofaatiQuizAttempt
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.models.PodiumStudent
import com.hawas.study.dofaati.models.QuestionType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PdfInspectionResult(
    val title: String,
    val pageCount: Int,
    val fileSizeLabel: String,
    val extractedText: String
)

data class AudioSummaryResult(
    val transcriptText: String,
    val extractedLaws: List<String>,
    val importantExamPoints: List<String>
)

object DofaatiQuestionValidator {

    fun validateAndNormalize(
        raw: DofaatiQuestionItem
    ): DofaatiQuestionItem? {
        val cleanText = raw.questionText.trim()
        if (cleanText.length < 4) return null

        val cleanedOptions = raw.options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return when (raw.questionType) {
            QuestionType.TRUE_FALSE -> {
                val tfOptions = if (cleanedOptions.size == 2) cleanedOptions else listOf("صح", "خطأ")
                val safeIdx = raw.correctOptionIndex.coerceIn(0, 1)
                raw.copy(
                    questionText = cleanText,
                    options = tfOptions,
                    correctOptionIndex = safeIdx,
                    detailedExplanation = raw.detailedExplanation.trim().ifBlank {
                        "الإجابة الصحيحة: ${tfOptions[safeIdx]}"
                    }
                )
            }
            QuestionType.MCQ -> {
                if (cleanedOptions.size < 2) return null
                val safeIdx = raw.correctOptionIndex.coerceIn(0, cleanedOptions.lastIndex)
                raw.copy(
                    questionText = cleanText,
                    options = cleanedOptions,
                    correctOptionIndex = safeIdx,
                    detailedExplanation = raw.detailedExplanation.trim().ifBlank {
                        "الإجابة الصحيحة هي: ${cleanedOptions[safeIdx]}"
                    }
                )
            }
            QuestionType.ESSAY -> {
                val safeOpts = if (cleanedOptions.size >= 2) cleanedOptions else emptyList()
                val safeIdx = if (safeOpts.isNotEmpty()) raw.correctOptionIndex.coerceIn(0, safeOpts.lastIndex) else 0
                val explanation = raw.detailedExplanation.trim()
                if (safeOpts.isEmpty() && explanation.isBlank()) return null
                raw.copy(
                    questionText = cleanText,
                    options = safeOpts,
                    correctOptionIndex = safeIdx,
                    detailedExplanation = explanation
                )
            }
        }
    }

    fun parseJsonArray(
        rawJson: String,
        startNum: Int,
        subjectName: String,
        sourceTag: String
    ): List<DofaatiQuestionItem> {
        val cleaned = extractJsonArrayString(rawJson) ?: return emptyList()
        val arr = runCatching { JSONArray(cleaned) }.getOrNull() ?: return emptyList()
        val out = ArrayList<DofaatiQuestionItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj: JSONObject = arr.optJSONObject(i) ?: continue
            val qText = obj.optString("questionText").trim()
            if (qText.isBlank()) continue
            val type = runCatching {
                QuestionType.valueOf(obj.optString("questionType", "MCQ").trim().uppercase())
            }.getOrDefault(QuestionType.MCQ)
            val diff = runCatching {
                DifficultyLevel.valueOf(obj.optString("difficulty", "MEDIUM").trim().uppercase())
            }.getOrDefault(DifficultyLevel.MEDIUM)
            val optArr = obj.optJSONArray("options")
            val options = mutableListOf<String>()
            if (optArr != null) {
                for (j in 0 until optArr.length()) {
                    val opt = optArr.optString(j).trim()
                    if (opt.isNotEmpty()) options.add(opt)
                }
            }
            val candidate = DofaatiQuestionItem(
                id = "q_${System.nanoTime()}_$i",
                questionNumber = startNum + out.size,
                subjectName = subjectName.ifBlank { obj.optString("subjectName", "عام") },
                questionText = qText,
                questionType = type,
                difficulty = diff,
                options = options,
                correctOptionIndex = obj.optInt("correctOptionIndex", 0),
                circuitDiagramType = obj.optString("circuitDiagramType").takeIf { it.isNotBlank() && it != "null" },
                detailedExplanation = obj.optString("detailedExplanation", "").trim(),
                sourceTag = sourceTag
            )
            validateAndNormalize(candidate)?.let { out.add(it) }
        }
        return out
    }

    fun extractJsonArrayString(raw: String): String? {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('[')
        val end = stripped.lastIndexOf(']')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }

    fun extractJsonObjectString(raw: String): String? {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }
}

class DofaatiAiClient(private val context: Context) {

    private suspend fun resolveApiKey(): String {
        val savedKey = runCatching {
            UserPreferencesRepository(context).userSettingsFlow.first().geminiApiKey.trim()
        }.getOrDefault("")
        if (savedKey.isNotBlank() && savedKey != "MY_GEMINI_API_KEY") return savedKey
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey
        return ""
    }

    suspend fun generateJson(
        prompt: String,
        systemInstruction: String,
        temperature: Float = 0.25f,
        inlineDataBase64: String? = null,
        inlineMimeType: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isNotBlank()) {
            val restResult = callGeminiRestApi(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = systemInstruction,
                temperature = temperature,
                inlineDataBase64 = inlineDataBase64,
                inlineMimeType = inlineMimeType
            )
            if (restResult.isSuccess) return@withContext restResult
            // Only fall through to Firebase if Firebase is initialized; otherwise surface the real REST error
            if (FirebaseApp.getApps(context).isEmpty()) {
                return@withContext restResult
            }
        }

        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return@withContext runCatching {
                val model = Firebase.ai.generativeModel(
                    modelName = "gemini-3.8-flash",
                    generationConfig = generationConfig {
                        this.temperature = temperature
                        responseMimeType = "application/json"
                    },
                    systemInstruction = content { text(systemInstruction) }
                )
                model.generateContent(prompt).text
                    ?: throw IllegalStateException("لم يُرجع الخادم الذكي استجابة صالحة.")
            }
        }

        Result.failure(
            IllegalStateException(
                "يرجى إضافة مفتاح Gemini API في الإعدادات أو تفعيل secrets لتشغيل الذكاء الاصطناعي الحقيقي."
            )
        )
    }

    private fun callGeminiRestApi(
        apiKey: String,
        prompt: String,
        systemInstruction: String,
        temperature: Float,
        inlineDataBase64: String?,
        inlineMimeType: String?
    ): Result<String> {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30_000
                readTimeout = 45_000
                doOutput = true
            }

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                if (!inlineDataBase64.isNullOrBlank() && !inlineMimeType.isNullOrBlank()) {
                    put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject().apply {
                                put("mimeType", inlineMimeType)
                                put("data", inlineDataBase64)
                            }
                        )
                    )
                }
            }

            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", parts)
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", temperature.toDouble())
                        put("responseMimeType", "application/json")
                    }
                )
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val rawResponse = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code !in 200..299) {
                val errMsg = runCatching {
                    JSONObject(rawResponse).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                return Result.failure(
                    IllegalStateException(
                        if (errMsg.isNotBlank()) "خطأ من خدمة الذكاء الاصطناعي ($code): $errMsg"
                        else "تعذر الاتصال بخدمة الذكاء الاصطناعي (رمز $code)."
                    )
                )
            }

            val jsonResp = JSONObject(rawResponse)
            val candidateText = jsonResp
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()

            if (candidateText.isNullOrBlank()) {
                Result.failure(IllegalStateException("لم يُرجع الذكاء الاصطناعي محتوى قابل للقراءة."))
            } else {
                Result.success(candidateText)
            }
        } catch (t: Throwable) {
            Result.failure(
                IllegalStateException("تعذر الاتصال بالإنترنت لتوليد الأسئلة: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            )
        }
    }
}

/**
 * Pathway 1: Real AI Chunked Batch Generation Engine (NO fake deterministic fallback).
 */
class ChunkedBatchGenerationEngine(private val context: Context) {

    private val aiClient = DofaatiAiClient(context)

    fun generateInChunks(
        sourceText: String,
        totalCount: Int,
        difficulties: Set<DifficultyLevel>,
        types: Set<QuestionType>,
        subjectName: String = "عام"
    ): Flow<BulkGenerationProgress> = flow {
        val cleanTopic = sourceText.trim()
        if (cleanTopic.isEmpty()) {
            emit(
                BulkGenerationProgress(
                    generatedCount = 0,
                    totalRequested = totalCount,
                    currentBatchIndex = 0,
                    totalBatches = 0,
                    latestBatchQuestions = emptyList(),
                    accumulatedQuestions = emptyList(),
                    isCompleted = true,
                    statusText = "يرجى إدخال موضوع أو نص المنهج أولاً",
                    errorMessage = "يرجى إدخال موضوع أو نص المنهج المطلوب توليد الأسئلة منه."
                )
            )
            return@flow
        }

        val safeTotal = totalCount.coerceIn(1, 100)
        val chunkSize = when {
            safeTotal >= 80 -> 25
            safeTotal >= 40 -> 20
            else -> safeTotal
        }
        val totalBatches = ceil(safeTotal.toDouble() / chunkSize).toInt().coerceAtLeast(1)
        val accumulated = mutableListOf<DofaatiQuestionItem>()
        val usedStems = HashSet<String>()
        val activeDifficulties = difficulties.ifEmpty { setOf(DifficultyLevel.MEDIUM) }.toList()
        val activeTypes = types.ifEmpty { setOf(QuestionType.MCQ) }.toList()

        for (batchIdx in 1..totalBatches) {
            val remaining = safeTotal - accumulated.size
            if (remaining <= 0) break
            val targetBatchCount = remaining.coerceAtMost(chunkSize)

            val prompt = buildString {
                append("ولّد بالضبط $targetBatchCount سؤالاً تعليمياً دقيقاً ومختلفاً في مادة ($subjectName) بناءً على المحتوى التالي.\n")
                append("مستويات الصعوبة المطلوبة: ${activeDifficulties.joinToString { it.labelAr }}.\n")
                append("أنماط الأسئلة المطلوبة: ${activeTypes.joinToString { it.name }}.\n")
                append("المحتوى / الموضوع:\n${cleanTopic.take(3500)}\n")
                if (usedStems.isNotEmpty()) {
                    append("يمنع تكرار أي من الأسئلة التالية:\n${usedStems.take(30).joinToString("\n")}\n")
                }
                append(
                    "أرجع JSON Array فقط. كل عنصر يجب أن يحتوي:\n" +
                        "- questionText (سؤال واضح ومركّز)\n" +
                        "- questionType (MCQ أو TRUE_FALSE أو ESSAY)\n" +
                        "- difficulty (EASY أو MEDIUM أو HIGHER_ORDER)\n" +
                        "- options (مصفوفة من 4 خيارات دقيقة لـ MCQ، أو [\"صح\", \"خطأ\"] لـ TRUE_FALSE)\n" +
                        "- correctOptionIndex (رقم الإجابة الصحيحة بدقة من 0 إلى 3 أو 0..1)\n" +
                        "- detailedExplanation (تفسير علمي دقيق وصحيح للإجابة)"
                )
            }

            val aiResult = aiClient.generateJson(
                prompt = prompt,
                systemInstruction = "أنت خبير تربوي وأكاديمي في صياغة الامتحانات الدقيقة بصيغة JSON فقط بدون أي نص خارجي.",
                temperature = 0.25f
            )

            if (aiResult.isFailure) {
                val errMsg = aiResult.exceptionOrNull()?.message ?: "فشل توليد الأسئلة بالذكاء الاصطناعي"
                emit(
                    BulkGenerationProgress(
                        generatedCount = accumulated.size,
                        totalRequested = safeTotal,
                        currentBatchIndex = batchIdx,
                        totalBatches = totalBatches,
                        latestBatchQuestions = emptyList(),
                        accumulatedQuestions = accumulated.toList(),
                        isCompleted = true,
                        statusText = errMsg,
                        errorMessage = errMsg
                    )
                )
                return@flow
            }

            val rawParsed = DofaatiQuestionValidator.parseJsonArray(
                rawJson = aiResult.getOrThrow(),
                startNum = accumulated.size + 1,
                subjectName = subjectName,
                sourceTag = "توليد ذكي"
            )

            val uniqueBatch = mutableListOf<DofaatiQuestionItem>()
            for (item in rawParsed) {
                val key = item.questionText.trim().lowercase()
                if (usedStems.add(key)) {
                    uniqueBatch.add(item.copy(questionNumber = accumulated.size + uniqueBatch.size + 1))
                    if (uniqueBatch.size >= targetBatchCount) break
                }
            }

            if (uniqueBatch.isEmpty()) {
                val err = "تعذر تحليل أسئلة صالحة من استجابة الذكاء الاصطناعي في الدفعة $batchIdx."
                emit(
                    BulkGenerationProgress(
                        generatedCount = accumulated.size,
                        totalRequested = safeTotal,
                        currentBatchIndex = batchIdx,
                        totalBatches = totalBatches,
                        latestBatchQuestions = emptyList(),
                        accumulatedQuestions = accumulated.toList(),
                        isCompleted = true,
                        statusText = err,
                        errorMessage = err
                    )
                )
                return@flow
            }

            accumulated.addAll(uniqueBatch)
            val isDone = accumulated.size >= safeTotal || batchIdx == totalBatches
            emit(
                BulkGenerationProgress(
                    generatedCount = accumulated.size,
                    totalRequested = safeTotal,
                    currentBatchIndex = batchIdx,
                    totalBatches = totalBatches,
                    latestBatchQuestions = uniqueBatch,
                    accumulatedQuestions = accumulated.toList(),
                    isCompleted = isDone,
                    statusText = if (isDone) "تم توليد ${accumulated.size} سؤالاً بنجاح" else "جاري توليد الأسئلة: ${accumulated.size}/$safeTotal"
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Pathway 2: Real AI Transcript & Audio Summarization + Question Generation (NO fake template fallback).
 */
class TranscriptToQuestionsConverter(private val context: Context) {

    private val aiClient = DofaatiAiClient(context)

    suspend fun transcribeAndSummarizeAudio(
        audioUri: String,
        existingNotes: String,
        subjectName: String
    ): Result<AudioSummaryResult> = withContext(Dispatchers.IO) {
        val fileBytesAndMime = loadAudioBytes(audioUri)
        if (fileBytesAndMime == null && existingNotes.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("أضف تسجيل صوتي صالح أو اكتب نص الدرس لتفريغه وتلخيصه.")
            )
        }

        val prompt = buildString {
            append("قم بتفريغ وتحليل هذا الدرس في مادة ($subjectName) بدقة عالية.\n")
            if (existingNotes.isNotBlank()) {
                append("النص أو الملاحظات المتوفرة:\n${existingNotes.take(4000)}\n")
            }
            append(
                "أرجع JSON Object فقط بهذا الشكل:\n" +
                    "{\n" +
                    "  \"transcriptText\": \"النص المفرغ المنظم بالكامل\",\n" +
                    "  \"extractedLaws\": [\"القانون أو القاعدة 1\", \"القانون 2\"],\n" +
                    "  \"importantExamPoints\": [\"نقطة امتحان مهمة 1\", \"نقطة 2\"]\n" +
                    "}"
            )
        }

        val jsonRes = aiClient.generateJson(
            prompt = prompt,
            systemInstruction = "أنت مساعد تفريغ وتلخيص دروس علمية بصيغة JSON فقط.",
            temperature = 0.2f,
            inlineDataBase64 = fileBytesAndMime?.first,
            inlineMimeType = fileBytesAndMime?.second
        )

        jsonRes.mapCatching { raw ->
            val objStr = DofaatiQuestionValidator.extractJsonObjectString(raw)
                ?: throw IllegalStateException("صيغة التلخيص غير صالحة")
            val obj = JSONObject(objStr)
            val transcript = obj.optString("transcriptText").trim().ifBlank { existingNotes.trim() }
            val lawsArr = obj.optJSONArray("extractedLaws")
            val laws = if (lawsArr != null) {
                List(lawsArr.length()) { lawsArr.optString(it).trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            val ptsArr = obj.optJSONArray("importantExamPoints")
            val pts = if (ptsArr != null) {
                List(ptsArr.length()) { ptsArr.optString(it).trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            AudioSummaryResult(
                transcriptText = transcript,
                extractedLaws = laws,
                importantExamPoints = pts
            )
        }
    }

    suspend fun convertToQuestions(
        transcriptId: String,
        transcriptText: String,
        extractedLaws: List<String>,
        examPoints: List<String>,
        subjectName: String,
        requestedCount: Int = 20
    ): Result<List<DofaatiQuestionItem>> = withContext(Dispatchers.IO) {
        val mergedContent = buildString {
            if (transcriptText.isNotBlank()) append("نص الدرس:\n$transcriptText\n")
            if (extractedLaws.isNotEmpty()) append("القوانين:\n${extractedLaws.joinToString("\n")}\n")
            if (examPoints.isNotEmpty()) append("نقاط الامتحان:\n${examPoints.joinToString("\n")}")
        }.trim()

        if (mergedContent.length < 15) {
            return@withContext Result.failure(
                IllegalStateException("النص المفرغ قصير جداً أو فارغ؛ فرّغ التسجيل أو اكتب نص الدرس أولاً.")
            )
        }

        val safeCount = requestedCount.coerceIn(5, 50)
        val batchSize = 25
        val collected = mutableListOf<DofaatiQuestionItem>()
        val usedStems = HashSet<String>()
        var remaining = safeCount

        while (remaining > 0) {
            val askCount = remaining.coerceAtMost(batchSize)
            val prompt = buildString {
                append("حوّل المحتوى التالي في مادة ($subjectName) إلى $askCount سؤالاً اختبارياً دقيقاً ومتنوعاً.\n")
                append(mergedContent.take(4000))
                if (usedStems.isNotEmpty()) {
                    append("\nتجنب تكرار:\n${usedStems.take(25).joinToString("\n")}")
                }
                append("\nأرجع JSON Array فقط بحقول: questionText, questionType (MCQ|TRUE_FALSE), difficulty (EASY|MEDIUM|HIGHER_ORDER), options (4 strings أو صح/خطأ), correctOptionIndex, detailedExplanation.")
            }

            val res = aiClient.generateJson(
                prompt = prompt,
                systemInstruction = "أنت صانع أسئلة امتحانات احترافية من تفريغ الحصص بصيغة JSON فقط.",
                temperature = 0.25f
            )
            if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)

            val parsed = DofaatiQuestionValidator.parseJsonArray(
                rawJson = res.getOrThrow(),
                startNum = collected.size + 1,
                subjectName = subjectName,
                sourceTag = "تفريغ ريكورد"
            ).filter { usedStems.add(it.questionText.lowercase()) }

            if (parsed.isEmpty()) break
            collected.addAll(parsed.take(askCount))
            remaining -= parsed.take(askCount).size
            if (parsed.size < askCount) break
        }

        if (collected.isEmpty()) {
            Result.failure(IllegalStateException("تعذر توليد أسئلة صالحة من هذا التفريغ."))
        } else {
            val renumbered = collected.mapIndexed { idx, q ->
                q.copy(id = "tr_${transcriptId}_${idx + 1}_${System.nanoTime()}", questionNumber = idx + 1)
            }
            Result.success(renumbered)
        }
    }

    private fun loadAudioBytes(audioUri: String): Pair<String, String>? {
        if (audioUri.isBlank()) return null
        return runCatching {
            val bytes = when {
                audioUri.startsWith("/") -> File(audioUri).takeIf { it.exists() && it.length() in 1..(10 * 1024 * 1024) }?.readBytes()
                audioUri.startsWith("content://") -> context.contentResolver.openInputStream(Uri.parse(audioUri))?.use { it.readBytes() }
                else -> File(audioUri).takeIf { it.exists() }?.readBytes()
            } ?: return null
            if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) return null
            val mime = when {
                audioUri.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
                audioUri.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                else -> "audio/mp4"
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP) to mime
        }.getOrNull()
    }
}

/**
 * Pathway 3: Strict Bulk Paste Parser (NEVER invents fake choices like "الاختيار (أ)").
 */
class SmartBulkPasteParser(private val context: Context) {

    private val aiClient = DofaatiAiClient(context)

    private val questionSplitRegex = Regex("""(?m)^\s*(?:س\s*)?[\d٠-٩]+\s*[-.):：]\s*""")
    private val optionRegex = Regex("""(?m)^\s*(?:[أاإآابجدهـA-Da-d])\s*[-.)/：:]\s*(.+)$""")
    private val answerHintRegex = Regex("""(?i)(?:الإجابة\s*الصحيحة|الاجابة\s*الصحيحة|الإجابة|الاجابة|الحل|الصحيح|Answer|Correct)\s*[:：\-]?\s*(.+)$""")

    suspend fun parseBulkText(
        rawText: String,
        subjectName: String = "عام",
        allowAiFallback: Boolean = false
    ): List<DofaatiQuestionItem> = withContext(Dispatchers.Default) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val regexParsed = parseWithRegex(trimmed, subjectName)
        if (regexParsed.isNotEmpty()) {
            return@withContext regexParsed
        }

        if (allowAiFallback) {
            return@withContext tryStructureViaAi(trimmed, subjectName)
        }
        emptyList()
    }

    fun parseWithRegex(rawText: String, subjectName: String): List<DofaatiQuestionItem> {
        val matches = questionSplitRegex.findAll(rawText).toList()
        val blocks: List<String> = if (matches.isNotEmpty()) {
            matches.indices.map { i ->
                val start = matches[i].range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
                rawText.substring(start, end).trim()
            }.filter { it.isNotEmpty() }
        } else {
            // Also allow blank-line separated blocks when they explicitly carry options or True/False answer
            rawText.split(Regex("""\n\s*\n+""")).map { it.trim() }.filter { it.isNotEmpty() }
        }

        val parsed = ArrayList<DofaatiQuestionItem>(blocks.size)
        for (block in blocks) {
            parseSingleBlock(block, parsed.size + 1, subjectName)?.let { parsed.add(it) }
        }
        return parsed
    }

    private fun parseSingleBlock(
        block: String,
        number: Int,
        subjectName: String
    ): DofaatiQuestionItem? {
        val blockLines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (blockLines.isEmpty()) return null

        val stemLines = mutableListOf<String>()
        val options = mutableListOf<String>()
        var explicitAnswerIdx: Int? = null
        var rawAnswerToken: String? = null
        var explanation = ""

        for (line in blockLines) {
            val ansMatch = answerHintRegex.find(line)
            if (ansMatch != null && !line.matches(optionRegex)) {
                rawAnswerToken = ansMatch.groupValues[1].trim()
                continue
            }
            if (line.startsWith("التفسير") || line.startsWith("الشرح") || line.startsWith("التعليل")) {
                explanation = line.substringAfter(":").substringAfter("：").trim()
                continue
            }
            val optMatch = optionRegex.find(line)
            if (optMatch != null) {
                var optText = optMatch.groupValues[1].trim()
                if (hasCorrectMarker(optText)) {
                    explicitAnswerIdx = options.size
                    optText = stripCorrectMarkers(optText)
                }
                if (optText.isNotEmpty()) options.add(optText)
            } else if (options.isEmpty()) {
                stemLines.add(line)
            }
        }

        // Check inline options on stem e.g. "يقاس الجهد بـ: أ) الأمبير ب) الفولت ج) الأوم د) الكولوم"
        if (options.isEmpty() && stemLines.isNotEmpty()) {
            val joined = stemLines.joinToString(" ")
            val inlineSplit = splitInlineOptions(joined)
            if (inlineSplit != null) {
                stemLines.clear()
                stemLines.add(inlineSplit.first)
                inlineSplit.second.forEachIndexed { idx, rawOpt ->
                    val cleaned = stripCorrectMarkers(rawOpt)
                    if (hasCorrectMarker(rawOpt)) explicitAnswerIdx = idx
                    if (cleaned.isNotEmpty()) options.add(cleaned)
                }
            }
        }

        val finalStem = stemLines.joinToString(" ").trim()
        if (finalStem.length < 4) return null

        // Check True/False implicit pairs if no options listed but answer hint says صح/خطأ
        val resolvedOptions: List<String>
        val isTrueFalse: Boolean
        if (options.size >= 2) {
            isTrueFalse = options.size == 2 &&
                options.any { it.contains("صح") } &&
                options.any { it.contains("خطأ") }
            resolvedOptions = options
        } else if (rawAnswerToken != null && isTrueFalseAnswerToken(rawAnswerToken)) {
            isTrueFalse = true
            resolvedOptions = listOf("صح", "خطأ")
        } else {
            // Do NOT fabricate placeholder options!
            return null
        }

        val finalCorrectIdx = explicitAnswerIdx
            ?: rawAnswerToken?.let { resolveAnswerToken(it, resolvedOptions) }
            ?: 0

        val candidate = DofaatiQuestionItem(
            id = "paste_${number}_${System.nanoTime()}",
            questionNumber = number,
            subjectName = subjectName.ifBlank { "عام" },
            questionText = finalStem,
            questionType = if (isTrueFalse) QuestionType.TRUE_FALSE else QuestionType.MCQ,
            difficulty = DifficultyLevel.MEDIUM,
            options = resolvedOptions,
            correctOptionIndex = finalCorrectIdx.coerceIn(0, resolvedOptions.lastIndex),
            circuitDiagramType = null,
            detailedExplanation = explanation.ifBlank {
                "الإجابة الصحيحة: ${resolvedOptions[finalCorrectIdx.coerceIn(0, resolvedOptions.lastIndex)]}"
            },
            sourceTag = "لصق المشرف"
        )
        return DofaatiQuestionValidator.validateAndNormalize(candidate)
    }

    private fun hasCorrectMarker(text: String): Boolean =
        text.contains("*") || text.contains("✓") || text.contains("✔") ||
            text.contains("(صحيح)") || text.contains("[صح]")

    private fun stripCorrectMarkers(text: String): String =
        text.replace("*", "")
            .replace("✓", "")
            .replace("✔", "")
            .replace("(صحيح)", "")
            .replace("[صح]", "")
            .trim()

    private fun isTrueFalseAnswerToken(token: String): Boolean {
        val norm = token.trim().lowercase()
        return norm.startsWith("صح") || norm.startsWith("صواب") ||
            norm.startsWith("خطأ") || norm.startsWith("غلط") ||
            norm == "true" || norm == "false"
    }

    private fun splitInlineOptions(text: String): Pair<String, List<String>>? {
        val inlineMarker = Regex("""(?:^|\s)([أاإآابجدABCD])\s*[).\-]\s*""")
        val hits = inlineMarker.findAll(text).toList()
        if (hits.size < 2) return null
        val stem = text.substring(0, hits.first().range.first).trim()
        val opts = hits.indices.map { i ->
            val s = hits[i].range.last + 1
            val e = if (i + 1 < hits.size) hits[i + 1].range.first else text.length
            text.substring(s, e).trim()
        }.filter { it.isNotEmpty() }
        return if (stem.isNotEmpty() && opts.size >= 2) stem to opts else null
    }

    private fun resolveAnswerToken(token: String, options: List<String>): Int {
        val clean = token.trim()
        val letterIdx = when (clean.uppercase()) {
            "أ", "ا", "إ", "آ", "A", "1", "١" -> 0
            "ب", "B", "2", "٢" -> 1
            "ج", "C", "3", "٣" -> 2
            "د", "D", "4", "٤" -> 3
            else -> null
        }
        if (letterIdx != null && letterIdx in options.indices) return letterIdx

        if (clean.startsWith("صح") || clean.startsWith("صواب") || clean.equals("true", true)) {
            val idx = options.indexOfFirst { it.contains("صح") || it.contains("صواب") }
            if (idx >= 0) return idx
        }
        if (clean.startsWith("خطأ") || clean.startsWith("غلط") || clean.equals("false", true)) {
            val idx = options.indexOfFirst { it.contains("خطأ") || it.contains("غلط") }
            if (idx >= 0) return idx
        }

        // Exact or partial option text match
        val textMatch = options.indexOfFirst {
            it.equals(clean, ignoreCase = true) || clean.contains(it, ignoreCase = true) || it.contains(clean, ignoreCase = true)
        }
        return if (textMatch >= 0) textMatch else 0
    }

    private suspend fun tryStructureViaAi(rawText: String, subjectName: String): List<DofaatiQuestionItem> {
        val prompt = buildString {
            append("استخرج الأسئلة الموجودة في النص التالي في مادة ($subjectName) وحوّلها لـ JSON Array بدقة.\n")
            append("إذا كان النص شرحاً وليس به أسئلة صريحة، كوّن أسئلة اختبارية دقيقة ومباشرة من النص نفسه مع تحديد الإجابة الصحيحة والتفسير.\n")
            append(rawText.take(4500))
            append("\nالحقول: questionText, questionType (MCQ|TRUE_FALSE), difficulty (EASY|MEDIUM|HIGHER_ORDER), options (4 choices أو صح/خطأ), correctOptionIndex, detailedExplanation.")
        }
        val res = aiClient.generateJson(
            prompt = prompt,
            systemInstruction = "أنت محلل ومحوّل نصوص تعليمية إلى JSON Array فقط.",
            temperature = 0.2f
        )
        val jsonText = res.getOrNull() ?: return emptyList()
        return DofaatiQuestionValidator.parseJsonArray(
            rawJson = jsonText,
            startNum = 1,
            subjectName = subjectName,
            sourceTag = "لصق المشرف"
        )
    }
}

/**
 * Pathway 4: Real PDF / Document Extractor (NO fake synthetic question fallback).
 */
class HighCapacityPdfExtractor(private val context: Context) {

    fun inspectPdfOrText(uri: Uri): PdfInspectionResult {
        val resolver = context.contentResolver
        var displayName = "ملف دراسي.pdf"
        var byteSize = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) c.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) byteSize = c.getLong(sizeIdx)
            }
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        if (byteSize <= 0L) byteSize = bytes.size.toLong()
        val sizeLabel = formatBytesLabel(byteSize)

        var pages = 1
        var extractedText = ""
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            bytes.inputStream().use { stream ->
                PDDocument.load(stream).use { doc ->
                    pages = doc.numberOfPages.coerceAtLeast(1)
                    extractedText = PDFTextStripper().getText(doc).trim()
                }
            }
        } catch (_: Throwable) {
            extractedText = runCatching { String(bytes, StandardCharsets.UTF_8).trim() }.getOrDefault("")
            pages = ((extractedText.length / 1800) + 1).coerceAtLeast(1)
        }

        return PdfInspectionResult(
            title = displayName.substringBeforeLast(".").ifBlank { displayName },
            pageCount = pages,
            fileSizeLabel = sizeLabel,
            extractedText = extractedText
        )
    }

    suspend fun extractQuestionsFromPdfStream(
        inputStream: InputStream,
        subjectName: String,
        parser: SmartBulkPasteParser
    ): Result<List<DofaatiQuestionItem>> = withContext(Dispatchers.IO) {
        val rawBytes = runCatching { inputStream.readBytes() }.getOrElse {
            return@withContext Result.failure(IllegalStateException("تعذر قراءة الملف المحدد."))
        }
        val rawText = try {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(rawBytes.inputStream()).use { doc ->
                PDFTextStripper().getText(doc)
            }
        } catch (_: Throwable) {
            String(rawBytes, StandardCharsets.UTF_8)
        }.trim()

        if (rawText.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("الملف لا يحتوي على نص قابل للاستخراج (قد يكون ممسوحاً ضوئياً كصور).")
            )
        }

        val parsed = parser.parseBulkText(rawText, subjectName, allowAiFallback = true)
        if (parsed.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("لم يتم العثور على أسئلة منسقة داخل الملف، وأضف مفتاح Gemini API لاستخراجها من النص العادي.")
            )
        }
        Result.success(parsed.map { it.copy(sourceTag = "ملزمة PDF") })
    }

    private fun formatBytesLabel(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            "${kb.roundToInt().coerceAtLeast(1)} KB"
        } else {
            String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
        }
    }
}

/**
 * Pathway 5: Honest Live Quiz Scoring & Real Attempts Podium Engine (NO fake podium names, NO score inflation).
 */
object LiveQuizScoringEngine {
    fun calculateQuestionScore(isCorrect: Boolean, remainingSeconds: Int): Int {
        if (!isCorrect) return 0
        return 100 + (remainingSeconds.coerceAtLeast(0) * 5)
    }

    fun computeLeaderboardFromAttempts(
        attempts: List<DofaatiQuizAttempt>
    ): Pair<List<PodiumStudent>, Int> {
        if (attempts.isEmpty()) return emptyList<PodiumStudent>() to 0
        val top3 = attempts
            .sortedWith(compareByDescending<DofaatiQuizAttempt> { it.earnedPoints }.thenByDescending { it.scorePercent })
            .take(3)
            .mapIndexed { idx, attempt ->
                PodiumStudent(
                    rank = idx + 1,
                    name = attempt.participantName,
                    scorePercent = attempt.scorePercent.coerceIn(0, 100),
                    points = attempt.earnedPoints
                )
            }
        val avg = attempts.map { it.scorePercent }.average().roundToInt().coerceIn(0, 100)
        return top3 to avg
    }
}

/**
 * Pathway 6: Real AI Mind Map & Flashcard Generator
 */
class MindMapAndFlashcardAiGenerator(private val context: Context) {
    private val aiClient = DofaatiAiClient(context)

    suspend fun generateMindMap(topicOrNotes: String, sourceTitle: String): Result<DofaatiMindMap> =
        withContext(Dispatchers.IO) {
            if (topicOrNotes.trim().length < 4) {
                return@withContext Result.failure(IllegalStateException("اكتب موضوعاً أو ملخصاً واضحاً لتوليد الخريطة."))
            }
            val prompt = """
                أنشئ خريطة ذهنية دقيقة ومختصرة عن:
                ${topicOrNotes.take(3000)}
                أرجع JSON Object فقط بهذا الشكل:
                {
                  "centerTitle": "عنوان المفهوم المركزي",
                  "branches": [
                    {"title": "الفرع 1", "subtitle": "شرح موجز", "formulaOrBadge": "قانون أو كلمة مفتاحية"},
                    {"title": "الفرع 2", "subtitle": "شرح موجز", "formulaOrBadge": "قانون أو كلمة مفتاحية"},
                    {"title": "الفرع 3", "subtitle": "شرح موجز", "formulaOrBadge": "قانون أو كلمة مفتاحية"},
                    {"title": "الفرع 4", "subtitle": "شرح موجز", "formulaOrBadge": "قانون أو كلمة مفتاحية"}
                  ]
                }
            """.trimIndent()

            aiClient.generateJson(
                prompt = prompt,
                systemInstruction = "أنت مصمم خرائط ذهنية تعليمية بصيغة JSON فقط.",
                temperature = 0.25f
            ).mapCatching { raw ->
                val objStr = DofaatiQuestionValidator.extractJsonObjectString(raw)
                    ?: throw IllegalStateException("تعذر قراءة الخريطة من الذكاء الاصطناعي.")
                val obj = JSONObject(objStr)
                val center = obj.optString("centerTitle").trim().ifBlank { topicOrNotes.trim().take(28) }
                val palette = listOf(0xFF10B981L, 0xFFEA580CL, 0xFFF59E0BL, 0xFF8B5CF6L)
                val icons = listOf("BOLT", "RESISTOR", "OHM", "VOLT")
                val arr = obj.optJSONArray("branches") ?: JSONArray()
                val branches = (0 until arr.length()).take(4).mapNotNull { i ->
                    val bObj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val title = bObj.optString("title").trim()
                    if (title.isBlank()) return@mapNotNull null
                    DofaatiMindMapBranch(
                        id = "mm_br_${System.nanoTime()}_$i",
                        title = title,
                        subtitle = bObj.optString("subtitle").trim(),
                        formulaOrBadge = bObj.optString("formulaOrBadge").trim().ifBlank { "مفهوم ${i + 1}" },
                        accentHex = palette[i % palette.size],
                        iconKey = icons[i % icons.size]
                    )
                }
                if (branches.isEmpty()) throw IllegalStateException("لم يُنتج الذكاء الاصطناعي فروعاً صالحة.")
                DofaatiMindMap(
                    id = "mm_${System.currentTimeMillis()}",
                    centerTitle = center,
                    sourceFileTitle = sourceTitle.ifBlank { "ملاحظات الدراسة" },
                    branches = branches
                )
            }
        }

    suspend fun generateFlashcards(topicOrNotes: String, sourceTitle: String): Result<List<DofaatiFlashcard>> =
        withContext(Dispatchers.IO) {
            if (topicOrNotes.trim().length < 4) {
                return@withContext Result.failure(IllegalStateException("اكتب نص الدرس أو الموضوع لتوليد البطاقات."))
            }
            val prompt = """
                أنشئ 6 بطاقات استذكار (Flashcards) دقيقة ومركزة للمراجعة السريعة حول:
                ${topicOrNotes.take(3000)}
                أرجع JSON Array فقط، كل عنصر يحتوي:
                frontTitle (السؤال أو المفهوم), frontSubtitle (تلميح مختصر), backTitle (الإجابة المختصرة), backFormula (قانون أو صيغة أو كلمة أساسية), backExplanation (تفسير واضح).
            """.trimIndent()

            aiClient.generateJson(
                prompt = prompt,
                systemInstruction = "أنت خبير بطاقات استذكار متباعد بصيغة JSON Array فقط.",
                temperature = 0.25f
            ).mapCatching { raw ->
                val arrStr = DofaatiQuestionValidator.extractJsonArrayString(raw)
                    ?: throw IllegalStateException("تعذر تحليل البطاقات.")
                val arr = JSONArray(arrStr)
                val cards = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val front = o.optString("frontTitle").trim()
                    val exp = o.optString("backExplanation").trim()
                    if (front.isBlank() || exp.isBlank()) return@mapNotNull null
                    DofaatiFlashcard(
                        id = "fc_${System.nanoTime()}_$i",
                        sourceTitle = sourceTitle.ifBlank { "بطاقات ذكية" },
                        frontTitle = front,
                        frontSubtitle = o.optString("frontSubtitle").trim(),
                        backTitle = o.optString("backTitle").trim().ifBlank { "الإجابة النموذجية" },
                        backFormula = o.optString("backFormula").trim(),
                        backExplanation = exp
                    )
                }
                if (cards.isEmpty()) throw IllegalStateException("لم يتم توليد بطاقات صالحة.")
                cards
            }
        }
}

/**
 * Pathway 7: Real Schedule Image OCR / Vision Extractor (NO fake "السبت 7 مساءً" fallback).
 */
class ScheduleImageExtractor(private val context: Context) {
    private val aiClient = DofaatiAiClient(context)

    suspend fun extractScheduleEvent(uri: Uri): Result<DofaatiScheduleEvent> = withContext(Dispatchers.IO) {
        val base64Jpeg = encodeBitmapAsJpegBase64(uri)
        if (base64Jpeg != null) {
            val aiRes = aiClient.generateJson(
                prompt = """
                    اقرأ جدول الحصص أو الامتحانات في الصورة واستخرج أقرب موعد مهم في JSON Object فقط:
                    {
                      "dayAndTime": "اليوم والساعة كما في الصورة",
                      "subjectName": "اسم المادة",
                      "title": "عنوان الحصة أو الامتحان",
                      "isExam": true أو false,
                      "hoursFromNow": عدد الساعات المتوقعة من الآن (عدد صحيح موجب، مثلاً 48)
                    }
                    إذا لم تحتوي الصورة على أي موعد واضح أعد {"error": "no_schedule"}.
                """.trimIndent(),
                systemInstruction = "أنت قارئ جداول دراسية من الصور باللغتين العربية والإنجليزية بصيغة JSON فقط.",
                temperature = 0.1f,
                inlineDataBase64 = base64Jpeg,
                inlineMimeType = "image/jpeg"
            )
            if (aiRes.isSuccess) {
                val parsed = runCatching {
                    val objStr = DofaatiQuestionValidator.extractJsonObjectString(aiRes.getOrThrow())
                        ?: throw IllegalStateException()
                    val obj = JSONObject(objStr)
                    if (obj.has("error")) throw IllegalStateException("لم يُعثر على جدول في الصورة.")
                    val dayAndTime = obj.optString("dayAndTime").trim()
                    val title = obj.optString("title").trim()
                    if (dayAndTime.isBlank() || title.isBlank()) throw IllegalStateException()
                    val hours = obj.optInt("hoursFromNow", 24).coerceIn(1, 24 * 60)
                    DofaatiScheduleEvent(
                        id = "ev_${System.currentTimeMillis()}",
                        dayAndTime = dayAndTime,
                        subjectName = obj.optString("subjectName").trim().ifBlank { "عام" },
                        title = title,
                        isExam = obj.optBoolean("isExam", false),
                        targetTimestampMillis = System.currentTimeMillis() + hours * 3_600_000L
                    )
                }.getOrNull()
                if (parsed != null) return@withContext Result.success(parsed)
            }
        }

        // Offline ML Kit OCR fallback (only if real text exists; never fabricates fake schedule)
        val ocrText = runCatching {
            val img = InputImage.fromFilePath(context, uri)
            suspendCoroutine { cont ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(img)
                    .addOnSuccessListener { result -> cont.resume(result.text.trim()) }
                    .addOnFailureListener { cont.resume("") }
            }
        }.getOrDefault("")

        val candidateLine = ocrText.lines().firstOrNull { it.trim().length >= 4 }?.trim()
        if (candidateLine.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("لم يتم العثور على نص واضح لجدول المواعيد في الصورة.")
            )
        }

        Result.success(
            DofaatiScheduleEvent(
                id = "ev_${System.currentTimeMillis()}",
                dayAndTime = candidateLine.take(28),
                subjectName = "جدول مستخرج",
                title = candidateLine,
                isExam = ocrText.contains("امتحان") || ocrText.contains("Exam", ignoreCase = true) || ocrText.contains("Quiz", ignoreCase = true),
                targetTimestampMillis = System.currentTimeMillis() + 24L * 3_600_000L
            )
        )
    }

    private fun encodeBitmapAsJpegBase64(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bmp.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull()
}

/**
 * Unified Repository matching clean architecture across all Dofa'ati features.
 */
class BulkQuestionGeneratorRepository(private val context: Context) {
    private val chunkEngine = ChunkedBatchGenerationEngine(context)
    private val transcriptConverter = TranscriptToQuestionsConverter(context)
    private val pasteParser = SmartBulkPasteParser(context)
    private val pdfExtractor = HighCapacityPdfExtractor(context)
    private val mindMapAi = MindMapAndFlashcardAiGenerator(context)
    private val scheduleExtractor = ScheduleImageExtractor(context)

    fun generateBulkQuestionsInChunks(
        sourceText: String,
        totalCount: Int = 50,
        difficulties: Set<DifficultyLevel>,
        types: Set<QuestionType>,
        subjectName: String = "عام"
    ): Flow<BulkGenerationProgress> = chunkEngine.generateInChunks(
        sourceText = sourceText,
        totalCount = totalCount,
        difficulties = difficulties,
        types = types,
        subjectName = subjectName
    )

    suspend fun transcribeAndSummarizeAudio(
        audioUri: String,
        existingNotes: String,
        subjectName: String
    ): Result<AudioSummaryResult> = transcriptConverter.transcribeAndSummarizeAudio(audioUri, existingNotes, subjectName)

    suspend fun convertAudioTranscriptToQuestions(
        transcriptId: String,
        transcriptText: String,
        extractedLaws: List<String> = emptyList(),
        examPoints: List<String> = emptyList(),
        subjectName: String = "عام",
        requestedCount: Int = 20
    ): Result<List<DofaatiQuestionItem>> = transcriptConverter.convertToQuestions(
        transcriptId = transcriptId,
        transcriptText = transcriptText,
        extractedLaws = extractedLaws,
        examPoints = examPoints,
        subjectName = subjectName,
        requestedCount = requestedCount
    )

    suspend fun parsePastedBulkQuestions(
        rawText: String,
        subjectName: String = "عام",
        allowAiFallback: Boolean = false
    ): List<DofaatiQuestionItem> = pasteParser.parseBulkText(rawText, subjectName, allowAiFallback)

    fun inspectPdfOrText(uri: Uri): PdfInspectionResult = pdfExtractor.inspectPdfOrText(uri)

    suspend fun importQuestionsFromPdfOrText(
        inputStream: InputStream,
        subjectName: String = "عام"
    ): Result<List<DofaatiQuestionItem>> = pdfExtractor.extractQuestionsFromPdfStream(inputStream, subjectName, pasteParser)

    suspend fun generateMindMapAi(topicOrNotes: String, sourceTitle: String): Result<DofaatiMindMap> =
        mindMapAi.generateMindMap(topicOrNotes, sourceTitle)

    suspend fun generateFlashcardsAi(topicOrNotes: String, sourceTitle: String): Result<List<DofaatiFlashcard>> =
        mindMapAi.generateFlashcards(topicOrNotes, sourceTitle)

    suspend fun extractScheduleFromImage(uri: Uri): Result<DofaatiScheduleEvent> =
        scheduleExtractor.extractScheduleEvent(uri)
}
