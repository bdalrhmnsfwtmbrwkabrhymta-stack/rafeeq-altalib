package com.example.domain.ai

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class FahimMoodState {
    FRIENDLY_IDLE,
    HAPPY_LAUGHING,
    ANNOYED_CRYING
}

data class FahimMindMapBranch(
    val title: String,
    val colorHex: Long,
    val items: List<String>
)

data class FahimMindMapData(
    val centerTitle: String,
    val branches: List<FahimMindMapBranch>
)

data class FahimStudyTableRow(
    val timeOrDay: String,
    val taskOrSubject: String,
    val durationOrDetail: String,
    val dayOfWeek: Int = 1,
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val tintHex: Long = 0xFFF8FAFC
)

data class FahimStudyTableData(
    val title: String,
    val columnHeaders: List<String> = listOf("الوقت", "المهمة", "المدة"),
    val rows: List<FahimStudyTableRow>,
    val isComparisonOnly: Boolean = false
)

data class FahimChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val mindMap: FahimMindMapData? = null,
    val studyTable: FahimStudyTableData? = null,
    val isAddedToSchedule: Boolean = false,
    val hasAttachedImage: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

class FahimAiService(
    private val geminiFallback: GeminiAiRepository = GeminiAiRepository()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun askFahim(
        prompt: String,
        history: List<FahimChatMessage> = emptyList(),
        imageBytesBase64: String? = null,
        studentContextSummary: String = "",
        customGeminiKey: String = ""
    ): FahimChatMessage = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank {
            if (!imageBytesBase64.isNullOrBlank()) "اشرح وحل المسألة الموجودة في هذه الصورة خطوة بخطوة" else "مرحباً فهيم"
        }

        val ocrExtractedFromImage = if (!imageBytesBase64.isNullOrBlank()) {
            extractTextFromImageBase64(imageBytesBase64)
        } else {
            ""
        }

        val effectivePrompt = buildString {
            append(cleanPrompt)
            if (ocrExtractedFromImage.isNotBlank()) {
                append("\n\n[نص مستخرج من الصورة المرفقة]:\n")
                append(ocrExtractedFromImage)
            }
        }

        val wantsMindMap = containsAny(
            cleanPrompt,
            "خريطة ذهنية", "خريطه ذهنيه", "تفريع", "mind map", "مفاهيم", "تلخيص شجري"
        )
        val wantsComparisonTable = containsAny(
            cleanPrompt,
            "مقارنة", "قارن", "الفرق بين", "comparison"
        )
        val wantsStudySchedule = !wantsComparisonTable && containsAny(
            cleanPrompt,
            "جدول", "امتحان", "بكرة", "غد", "مراجعة", "خطة", "نظّم", "ظبط لي", "مذاكرة", "schedule"
        )

        // 1. Primary Engine: Gemini API configured via FahimConfig / Developer Panel (PIN 2026) / BuildConfig
        val resolvedGeminiKey = FahimConfig.resolveGeminiApiKey(customGeminiKey)
        if (resolvedGeminiKey.isNotBlank()) {
            val geminiRes = geminiFallback.generateAiResponse(
                prompt = buildSystemAugmentedPrompt(
                    userPrompt = effectivePrompt,
                    wantsMindMap = wantsMindMap,
                    wantsStudySchedule = wantsStudySchedule,
                    wantsComparisonTable = wantsComparisonTable
                ),
                systemInstruction = FAHIM_SYSTEM_PROMPT,
                customApiKey = resolvedGeminiKey,
                modelName = FahimConfig.PRIMARY_MODEL,
                imageBytesBase64 = imageBytesBase64,
                chatHistory = history.takeLast(8).map { it.isUser to it.text }
            )
            val geminiText = geminiRes.getOrNull()
            if (!geminiText.isNullOrBlank() &&
                !geminiText.contains("مفتاح Gemini API") &&
                !geminiText.contains("API key", ignoreCase = true)
            ) {
                return@withContext buildParsedFahimReply(
                    rawText = geminiText,
                    prompt = cleanPrompt,
                    wantsMindMap = wantsMindMap,
                    wantsStudySchedule = wantsStudySchedule,
                    wantsComparisonTable = wantsComparisonTable
                )
            }
        }

        // 2. Secondary DeepSeek Engine (if configured)
        val deepSeekKey = FahimAiConfig.resolveEffectiveDeepSeekKey()
        if (deepSeekKey.isNotBlank()) {
            val deepSeekRaw = callDeepSeekApi(
                apiKey = deepSeekKey,
                userPrompt = effectivePrompt,
                history = history,
                studentContextSummary = studentContextSummary,
                wantsMindMap = wantsMindMap,
                wantsStudySchedule = wantsStudySchedule,
                wantsComparisonTable = wantsComparisonTable
            )
            if (deepSeekRaw.isSuccess) {
                val rawText = deepSeekRaw.getOrThrow()
                return@withContext buildParsedFahimReply(
                    rawText = rawText,
                    prompt = cleanPrompt,
                    wantsMindMap = wantsMindMap,
                    wantsStudySchedule = wantsStudySchedule,
                    wantsComparisonTable = wantsComparisonTable
                )
            }
        }

        // 3. Always-Ready Smart Academic Engine (never blocks UI with key errors)
        buildSmartAcademicStructuredReply(
            prompt = cleanPrompt,
            ocrText = ocrExtractedFromImage,
            wantsMindMap = wantsMindMap,
            wantsStudySchedule = wantsStudySchedule,
            wantsComparisonTable = wantsComparisonTable
        )
    }

    suspend fun callDeepSeekRawText(
        userPrompt: String,
        systemInstruction: String = FAHIM_SYSTEM_PROMPT,
        historyPairs: List<Pair<Boolean, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = FahimAiConfig.resolveEffectiveDeepSeekKey()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("DeepSeek key not configured"))
        }
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
            historyPairs.takeLast(8).forEach { (isUser, msg) ->
                if (msg.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", if (isUser) "user" else "assistant")
                        put("content", msg)
                    })
                }
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }
        executeDeepSeekHttpCall(key, messagesArray)
    }

    private fun callDeepSeekApi(
        apiKey: String,
        userPrompt: String,
        history: List<FahimChatMessage>,
        studentContextSummary: String,
        wantsMindMap: Boolean,
        wantsStudySchedule: Boolean,
        wantsComparisonTable: Boolean
    ): Result<String> {
        val systemContent = buildString {
            append(FAHIM_SYSTEM_PROMPT)
            if (studentContextSummary.isNotBlank()) {
                append("\n\nبيانات الطالب الحالية:\n").append(studentContextSummary)
            }
            if (wantsMindMap || wantsStudySchedule || wantsComparisonTable) {
                append("\n\nتعليمات التنسيق الهيكلي الإلزامية لهذه الإجابة:")
            }
            if (wantsMindMap) {
                append(
                    """
                    
                    أرفق في نهاية ردك كتلة خريطة ذهنية بصيغة JSON داخل الوسمين [MINDMAP_JSON] و [/MINDMAP_JSON]:
                    [MINDMAP_JSON]
                    {"centerTitle":"عنوان الموضوع","branches":[{"title":"الفرع 1","items":["نقطة 1","نقطة 2"]},{"title":"الفرع 2","items":["نقطة 1","نقطة 2"]},{"title":"الفرع 3","items":["نقطة 1","نقطة 2"]},{"title":"الفرع 4","items":["نقطة 1","نقطة 2"]}]}
                    [/MINDMAP_JSON]
                    """.trimIndent()
                )
            }
            if (wantsStudySchedule || wantsComparisonTable) {
                append(
                    """
                    
                    أرفق في ردك جدولاً منظماً بصيغة JSON داخل الوسمين [STUDY_TABLE_JSON] و [/STUDY_TABLE_JSON]:
                    [STUDY_TABLE_JSON]
                    {"title":"عنوان الجدول","columns":["الوقت","المهمة","المدة"],"rows":[{"time":"07:00 - 08:30","task":"اسم المهمة أو المادة","duration":"ساعة ونصف","dayOfWeek":1,"start":"07:00","end":"08:30"}]}
                    [/STUDY_TABLE_JSON]
                    """.trimIndent()
                )
            }
        }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            })
            history.takeLast(8).forEach { msg ->
                if (msg.text.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.text)
                    })
                }
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        return executeDeepSeekHttpCall(apiKey, messagesArray)
    }

    private fun executeDeepSeekHttpCall(apiKey: String, messagesArray: JSONArray): Result<String> {
        return try {
            val bodyJson = JSONObject().apply {
                put("model", FahimAiConfig.DEEPSEEK_MODEL)
                put("messages", messagesArray)
                put("temperature", 0.35)
                put("max_tokens", 2048)
                put("stream", false)
            }

            val request = Request.Builder()
                .url(FahimAiConfig.DEEPSEEK_BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val root = JSONObject(rawBody)
                val choices = root.optJSONArray("choices")
                val content = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()
                if (content.isNotBlank()) {
                    Result.success(content)
                } else {
                    Result.failure(IllegalStateException("Empty DeepSeek response"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal fun buildParsedFahimReply(
        rawText: String,
        prompt: String,
        wantsMindMap: Boolean,
        wantsStudySchedule: Boolean,
        wantsComparisonTable: Boolean
    ): FahimChatMessage {
        var displayText = rawText
        var parsedMindMap: FahimMindMapData? = null
        var parsedTable: FahimStudyTableData? = null

        val mindMapMatch = Regex("""\[MINDMAP_JSON](.*?)\[/MINDMAP_JSON]""", RegexOption.DOT_MATCHES_ALL)
            .find(displayText)
        if (mindMapMatch != null) {
            parsedMindMap = parseMindMapJson(mindMapMatch.groupValues[1])
            displayText = displayText.replace(mindMapMatch.value, "").trim()
        }

        val tableMatch = Regex("""\[STUDY_TABLE_JSON](.*?)\[/STUDY_TABLE_JSON]""", RegexOption.DOT_MATCHES_ALL)
            .find(displayText)
        if (tableMatch != null) {
            parsedTable = parseStudyTableJson(tableMatch.groupValues[1], isComparisonOnly = wantsComparisonTable)
            displayText = displayText.replace(tableMatch.value, "").trim()
        }

        if (wantsMindMap && parsedMindMap == null) {
            parsedMindMap = createContextualMindMap(prompt)
        }
        if ((wantsStudySchedule || wantsComparisonTable) && parsedTable == null) {
            parsedTable = if (wantsComparisonTable) {
                createComparisonTable(prompt)
            } else {
                createExamRevisionTable(prompt)
            }
        }

        val cleanedText = displayText
            .replace(Regex("""```json.*?```""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
            .ifBlank {
                when {
                    parsedMindMap != null && parsedTable != null ->
                        "أبشر! جهّزت لك الخريطة الذهنية الملونة وجدول المراجعة المنظم، وأضفته تلقائياً لقسم مهامك وجدولك لتبدأ فوراً بثقة."
                    parsedMindMap != null ->
                        "تفضل الخريطة الذهنية الملونة المرتبة لأهم محاور الدرس لتسهيل الفهم والحفظ السريع."
                    parsedTable != null ->
                        "إليك الجدول المنظم بالألوان، ويمكنك متابعته في قسم الجداول والمهام."
                    else -> rawText.trim()
                }
            }

        return FahimChatMessage(
            isUser = false,
            text = cleanedText,
            mindMap = parsedMindMap,
            studyTable = parsedTable,
            isAddedToSchedule = false
        )
    }

    private fun parseMindMapJson(rawJson: String): FahimMindMapData? = runCatching {
        val obj = JSONObject(FahimGeminiClient.stripMarkdownJsonFences(rawJson))
        val center = obj.optString("centerTitle").ifBlank { "موضوع الدرس" }
        val arr = obj.optJSONArray("branches") ?: return null
        val paletteHexes = listOf(0xFF1A6FE3L, 0xFFD97706L, 0xFF10B981L, 0xFF0D9488L)
        val branches = mutableListOf<FahimMindMapBranch>()
        for (i in 0 until arr.length()) {
            val bObj = arr.optJSONObject(i) ?: continue
            val bTitle = bObj.optString("title").ifBlank { "محور ${i + 1}" }
            val itemsArr = bObj.optJSONArray("items")
            val items = buildList {
                if (itemsArr != null) {
                    for (j in 0 until itemsArr.length()) {
                        val itemText = itemsArr.optString(j).trim()
                        if (itemText.isNotBlank()) add(itemText)
                    }
                }
            }.ifEmpty { listOf("نقطة رئيسية") }
            branches += FahimMindMapBranch(
                title = bTitle,
                colorHex = paletteHexes[i % paletteHexes.size],
                items = items.take(4)
            )
        }
        if (branches.isEmpty()) null else FahimMindMapData(centerTitle = center, branches = branches)
    }.getOrNull()

    private fun parseStudyTableJson(rawJson: String, isComparisonOnly: Boolean): FahimStudyTableData? = runCatching {
        val obj = JSONObject(FahimGeminiClient.stripMarkdownJsonFences(rawJson))
        val title = obj.optString("title").ifBlank { "خطة المذاكرة المقترحة" }
        val colsArr = obj.optJSONArray("columns")
        val cols = buildList {
            if (colsArr != null) {
                for (i in 0 until colsArr.length()) {
                    val col = colsArr.optString(i).trim()
                    if (col.isNotBlank()) add(col)
                }
            }
        }.ifEmpty { listOf("الوقت", "المهمة", "المدة") }

        val rowsArr = obj.optJSONArray("rows") ?: return null
        val rowTints = listOf(0xFFFFFFFFL, 0xFFF1F5F9L, 0xFFFEF3C7L, 0xFFECFDF5L, 0xFFEFF6FFL)
        val rows = mutableListOf<FahimStudyTableRow>()
        for (i in 0 until rowsArr.length()) {
            val rObj = rowsArr.optJSONObject(i) ?: continue
            val time = rObj.optString("time").ifBlank { "08:00 - 09:30" }
            val task = rObj.optString("task").ifBlank { "مراجعة الدرس" }
            val duration = rObj.optString("duration").ifBlank { "ساعة ونصف" }
            val dayOfWeek = rObj.optInt("dayOfWeek", ((i % 6) + 1)).coerceIn(1, 7)
            val start = rObj.optString("start").ifBlank {
                time.split("-", "–").firstOrNull()?.trim()?.takeIf { it.contains(":") } ?: "08:00"
            }
            val end = rObj.optString("end").ifBlank {
                time.split("-", "–").getOrNull(1)?.trim()?.takeIf { it.contains(":") } ?: "09:30"
            }
            rows += FahimStudyTableRow(
                timeOrDay = time,
                taskOrSubject = task,
                durationOrDetail = duration,
                dayOfWeek = dayOfWeek,
                startTime = start,
                endTime = end,
                tintHex = rowTints[i % rowTints.size]
            )
        }
        if (rows.isEmpty()) null else FahimStudyTableData(
            title = title,
            columnHeaders = cols.take(3),
            rows = rows,
            isComparisonOnly = isComparisonOnly
        )
    }.getOrNull()

    private fun buildSmartAcademicStructuredReply(
        prompt: String,
        ocrText: String,
        wantsMindMap: Boolean,
        wantsStudySchedule: Boolean,
        wantsComparisonTable: Boolean
    ): FahimChatMessage {
        val mindMap = if (wantsMindMap) createContextualMindMap(prompt) else null
        val table = when {
            wantsComparisonTable -> createComparisonTable(prompt)
            wantsStudySchedule -> createExamRevisionTable(prompt)
            else -> null
        }

        val responseText = when {
            ocrText.isNotBlank() -> buildString {
                append("### تحليل وشرح المسألة من الصورة\n\n")
                append("قرأت النص التالي من الصورة المرفقة:\n")
                append("«${ocrText.take(320)}»\n\n")
                append("1. **المعطيات الأساسية:** تحديد القيم والمفاهيم الواردة بدقة.\n")
                append("2. **طريقة الحل:** تطبيق القاعدة المناسبة خطوة بخطوة مع توحيد الوحدات.\n")
                append("3. **المراجعة السريعة:** تأكد من كتابة وحدة القياس النهائية والتحقق من منطقية الناتج.")
            }
            mindMap != null && table != null ->
                "جهّزت لك خطة المراجعة الشاملة لامتحان الغد مع الخريطة الذهنية المتفرعة بالألوان والجدول الزمني المرتب، وتمت إضافته تلقائياً إلى قسم مهامك وجدولك."
            mindMap != null ->
                "تفضل الخريطة الذهنية الملونة التي تلخص الموضوع في 4 محاور أساسية لتثبيت المعلومات بصرياً بسرعة."
            table != null && wantsComparisonTable ->
                "إليك جدول المقارنة المنظم بين العناصر الأساسية مع أبرز الفروقات ونقاط الامتحان المهمة."
            table != null ->
                "تم تصميم جدول المذاكرة المنظم لك خطوة بخطوة بفترات تركيز واستراحة متوازنة، وتم ربطه بقسم المهام والجدول."
            prompt.contains("فيزياء") || prompt.contains("كيمياء") || prompt.contains("رياضيات") ->
                "### الشرح العلمي المبسط خطوة بخطوة\n\n" +
                    "1. **الفكرة الأساسية:** يعتمد هذا الموضوع على فهم العلاقة السببية بين المتغيرات قبل الحفظ.\n" +
                    "2. **خطوات التطبيق:** استخرج المعطيات أولاً، ثم اختر القانون المناسب وعوّض بالوحدات القياسية.\n" +
                    "3. **نصيحة الامتحان:** راجع حالات التحويل الشائعة وحل 3 أمثلة تطبيقية للتأكد من إتقانك الكامل."
            else ->
                "### شرح فهيم المبسط\n\n" +
                    "أهلاً بك يا بطل! بخصوص سؤالك عن «${prompt.take(70)}»:\n\n" +
                    "1. **المفهوم الجوهري:** قسّم الموضوع إلى 3 أفكار صغيرة مترابطة ليسهل استيعابها.\n" +
                    "2. **التطبيق العملي:** اربط كل قاعدة بمثال واقعي وأعد صياغتها بأسلوبك الخاص.\n" +
                    "3. **التثبيت بالاسترجاع:** اختبر نفسك بعد 20 دقيقة بدون النظر إلى الملاحظات.\n\n" +
                    "يمكنك الضغط على «خريطة ذهنية» أو «جدول امتحان الغد» وسأجهزه لك فوراً!"
        }

        return FahimChatMessage(
            isUser = false,
            text = responseText,
            mindMap = mindMap,
            studyTable = table,
            isAddedToSchedule = false
        )
    }

    private fun createContextualMindMap(prompt: String): FahimMindMapData {
        val center = when {
            prompt.contains("فيزياء") -> "مراجعة الفيزياء"
            prompt.contains("كيمياء") -> "مراجعة الكيمياء"
            prompt.contains("رياضيات") -> "ملخص الرياضيات"
            prompt.contains("أحياء") -> "مفاهيم الأحياء"
            prompt.contains("تاريخ") -> "محاور التاريخ"
            prompt.contains("عربي") || prompt.contains("نحو") -> "قواعد النحو"
            else -> "موضوع الامتحان"
        }
        return FahimMindMapData(
            centerTitle = center,
            branches = listOf(
                FahimMindMapBranch(
                    title = "مراجعة",
                    colorHex = 0xFF1A6FE3L,
                    items = listOf("نقاط رئيسية", "تعاريف أساسية", "ملخص الفصل")
                ),
                FahimMindMapBranch(
                    title = "حل أسئلة",
                    colorHex = 0xFFD97706L,
                    items = listOf("نماذج سابقة", "أفكار متوقعة", "تدريب عملي")
                ),
                FahimMindMapBranch(
                    title = "نقاط رئيسية",
                    colorHex = 0xFF10B981L,
                    items = listOf("عرض الشرائح", "مفاهيم مترابطة", "أمثلة محلولة")
                ),
                FahimMindMapBranch(
                    title = "القوانين",
                    colorHex = 0xFF0D9488L,
                    items = listOf("توحيد الوحدات", "الثوابت الهامة", "فخاخ الامتحان")
                )
            )
        )
    }

    private fun createExamRevisionTable(prompt: String): FahimStudyTableData {
        val title = if (prompt.contains("امتحان") || prompt.contains("غد") || prompt.contains("بكرة")) {
            "جدول المراجعة المكثف لامتحان الغد"
        } else {
            "جدول المذاكرة المقترح"
        }
        return FahimStudyTableData(
            title = title,
            columnHeaders = listOf("الوقت", "المهمة", "المدة"),
            rows = listOf(
                FahimStudyTableRow("07:00 - 08:30", "مراجعة المفاهيم الأساسية", "ساعة ونصف", dayOfWeek = 1, startTime = "07:00", endTime = "08:30", tintHex = 0xFFFFFFFFL),
                FahimStudyTableRow("08:45 - 10:15", "مراجعة القوانين والتعاريف", "ساعة ونصف", dayOfWeek = 1, startTime = "08:45", endTime = "10:15", tintHex = 0xFFF1F5F9L),
                FahimStudyTableRow("12:00 - 13:30", "حل نقاط الصعوبة والتمارين", "ساعة ونصف", dayOfWeek = 1, startTime = "12:00", endTime = "13:30", tintHex = 0xFFFEF3C7L),
                FahimStudyTableRow("14:30 - 16:00", "أهم القوانين والتطبيقات", "ساعة ونصف", dayOfWeek = 1, startTime = "14:30", endTime = "16:00", tintHex = 0xFFECFDF5L),
                FahimStudyTableRow("17:00 - 18:30", "حل أسئلة امتحانات شاملة", "ساعة ونصف", dayOfWeek = 1, startTime = "17:00", endTime = "18:30", tintHex = 0xFFEFF6FFL)
            ),
            isComparisonOnly = false
        )
    }

    private fun createComparisonTable(prompt: String): FahimStudyTableData {
        return FahimStudyTableData(
            title = "جدول المقارنة المنظم",
            columnHeaders = listOf("وجه المقارنة", "العنصر الأول", "العنصر الثاني"),
            rows = listOf(
                FahimStudyTableRow("التعريف", "المفهوم الأساسي والآلية الأولى", "المفهوم المقابل والآلية الثانية", tintHex = 0xFFFFFFFFL),
                FahimStudyTableRow("الخصائص", "يمتاز بالثبات والوضوح المباشر", "يمتاز بالتنوع والتطبيق المتدرج", tintHex = 0xFFEFF6FFL),
                FahimStudyTableRow("الأمثلة", "تطبيق مباشر من المنهج", "تطبيق مركب في المسائل", tintHex = 0xFFECFDF5L),
                FahimStudyTableRow("نقطة الامتحان", "يُطلب ذكر السبب العلمي", "يُطلب استنتاج النتيجة", tintHex = 0xFFFEF3C7L)
            ),
            isComparisonOnly = true
        )
    }

    private suspend fun extractTextFromImageBase64(base64: String): String = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text.orEmpty().trim())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("")
                }
        }
    }.getOrDefault("")

    private fun buildSystemAugmentedPrompt(
        userPrompt: String,
        wantsMindMap: Boolean,
        wantsStudySchedule: Boolean,
        wantsComparisonTable: Boolean
    ): String = buildString {
        append(userPrompt)
        if (wantsMindMap) {
            append("\n[أضف في النهاية كتلة JSON للخريطة الذهنية بين [MINDMAP_JSON] و [/MINDMAP_JSON]]")
        }
        if (wantsStudySchedule || wantsComparisonTable) {
            append("\n[أضف في النهاية كتلة JSON للجدول بين [STUDY_TABLE_JSON] و [/STUDY_TABLE_JSON]]")
        }
    }

    private fun containsAny(text: String, vararg terms: String): Boolean {
        val lower = text.lowercase()
        return terms.any { lower.contains(it.lowercase()) }
    }

    companion object {
        private val FAHIM_SYSTEM_PROMPT = """
            أنت «فهيم» — المساعد الدراسي الذكي داخل تطبيق «مُتعلِّم».
            أسلوبك:
            1. لغة عربية واضحة، ودودة، مشجعة، ومباشرة دون حشو.
            2. تنسيق منظم بعناوين واضحة وخطوات مرقمة سهلة القراءة للطلاب.
            3. عندما يطلب الطالب خريطة ذهنية أو جدول مذاكرة أو جدول امتحان غدٍ أو جدول مقارنة، قدّم الشرح الموجز وأرفق الكتلة المهيكلة المطلوبة بدقة.
            4. لا تذكر أي معلومات تقنية عن المفاتيح أو أخطاء الخوادم أمام الطالب أبداً.
        """.trimIndent()
    }
}
