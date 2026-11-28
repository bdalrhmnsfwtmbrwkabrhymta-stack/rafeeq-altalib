package com.example.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class OpenMaicPdfDocument(
    val uriString: String,
    val displayName: String,
    val pageCount: Int,
    val currentPageIndex: Int = 0,
    val currentPagePreviewBitmap: Bitmap? = null,
    val extractedPageText: String = "",
    val chapterOutline: List<String> = emptyList()
)

data class OpenMaicBoardSession(
    val topicTitle: String,
    val sourcePdfName: String? = null,
    val sourcePageNumber: Int? = null,
    val lesson: FahimWhiteboardLesson,
    val mindMap: FahimMindMapData,
    val studyTable: FahimStudyTableData,
    val agentSummaryText: String,
    val isSavedToTasks: Boolean = false
)

/**
 * محرك سبورة OpenMAIC التفاعلي وقارئ ملفات PDF (مستوحى من معمارية THU-MAIC/OpenMAIC - MIT / Apache-2.0):
 * ينسق بين ثلاثة وكلاء متعاونين (Multi-Agent Classroom):
 * 1. وكيل استخلاص PDF عبر Android PdfRenderer + PDFBox + ML Kit OCR.
 * 2. وكيل الشرح والرسم الهندسي/الفيزيائي على لوحة المربعات (بالأزرق الملكي والذهبي والأخضر).
 * 3. وكيل التلخيص والخرائط الذهنية وجدول المراجعة المرتبط تلقائياً بقسم «مهامي».
 */
class OpenMaicPdfBoardEngine(
    private val geminiClient: FahimGeminiClient = FahimGeminiClient(),
    private val drawingEngine: FahimBoardDrawingEngine = FahimBoardDrawingEngine(geminiClient)
) {

    suspend fun inspectAndLoadPdfPage(
        context: Context,
        uri: Uri,
        pageIndex: Int = 0
    ): OpenMaicPdfDocument? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val displayName = queryFileName(context, uri) ?: "ملف_دراسي.pdf"

            // Render visual page preview via native Android PdfRenderer
            var pageCount = 1
            var renderedBitmap: Bitmap? = null
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                    val safeIdx = pageIndex.coerceIn(0, pageCount - 1)
                    renderer.openPage(safeIdx).use { page ->
                        val maxSide = 1080
                        val scale = (maxSide.toFloat() / maxOf(page.width, page.height)).coerceIn(0.6f, 2.0f)
                        val targetW = (page.width * scale).toInt().coerceAtLeast(320)
                        val targetH = (page.height * scale).toInt().coerceAtLeast(420)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        renderedBitmap = bmp
                    }
                }
            }

            // Extract embedded text for the selected page using PDFBox, falling back to ML Kit OCR
            val safeIdx = pageIndex.coerceIn(0, pageCount - 1)
            val embeddedText = extractEmbeddedPdfText(context, uri, safeIdx)
            val effectiveText = if (embeddedText.isNotBlank()) {
                embeddedText
            } else {
                renderedBitmap?.let { ocrBitmap(it) }.orEmpty()
            }

            val outline = (1..minOf(pageCount, 8)).map { pNum -> "صفحة / فصل $pNum" }

            OpenMaicPdfDocument(
                uriString = uri.toString(),
                displayName = displayName,
                pageCount = pageCount,
                currentPageIndex = safeIdx,
                currentPagePreviewBitmap = renderedBitmap,
                extractedPageText = effectiveText,
                chapterOutline = outline
            )
        }.getOrNull()
    }

    suspend fun runMultiAgentClassroom(
        topicOrQuestion: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument? = null,
        customGeminiKey: String = ""
    ): OpenMaicBoardSession = withContext(Dispatchers.IO) {
        val cleanKey = FahimConfig.resolveGeminiApiKey(customGeminiKey)
        val cleanTopic = topicOrQuestion.trim().ifBlank {
            pdfDoc?.let { "شرح صفحة ${it.currentPageIndex + 1} من ${it.displayName}" }
                ?: "مساحة الجزء المظلل"
        }

        val pageContextText = pdfDoc?.extractedPageText?.take(2600).orEmpty()
        val pageImageBase64 = if (pdfDoc?.currentPagePreviewBitmap != null && pageContextText.length < 80) {
            encodeBitmapJpegBase64(pdfDoc.currentPagePreviewBitmap)
        } else {
            null
        }

        if (cleanKey.isNotBlank()) {
            val aiSession = requestGeminiOpenMaicSession(
                topic = cleanTopic,
                dialect = dialect,
                pdfDoc = pdfDoc,
                pageContextText = pageContextText,
                pageImageBase64 = pageImageBase64,
                apiKey = cleanKey
            )
            if (aiSession != null) {
                return@withContext aiSession
            }
        }

        if (FirebaseAiLogicClient.isAvailable()) {
            val firebaseRaw = FirebaseAiLogicClient.generateText(
                prompt = buildString {
                    append("الموضوع / المطلوب شرحه: ").append(cleanTopic)
                    if (pdfDoc != null) {
                        append("\nالمصدر المرفوع: ").append(pdfDoc.displayName)
                            .append(" — صفحة ").append(pdfDoc.currentPageIndex + 1)
                    }
                    if (pageContextText.isNotBlank()) {
                        append("\n\nنص صفحة الكتاب:\n").append(pageContextText)
                    }
                },
                systemInstruction = "أنت نظام OpenMAIC + Excalidraw. أرجع JSON صالحاً بالخطوات وعناصر الرسم excalidrawElements.",
                imageBytesBase64 = pageImageBase64,
                temperature = 0.3f,
                responseMimeType = "application/json"
            ).getOrNull()
            if (!firebaseRaw.isNullOrBlank()) {
                val parsed = parseOpenMaicSessionJson(firebaseRaw, cleanTopic, dialect, pdfDoc)
                if (parsed != null) return@withContext parsed
            }
        }

        buildLocalOpenMaicSession(
            topic = cleanTopic,
            dialect = dialect,
            pdfDoc = pdfDoc
        )
    }

    private suspend fun requestGeminiOpenMaicSession(
        topic: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument?,
        pageContextText: String,
        pageImageBase64: String?,
        apiKey: String
    ): OpenMaicBoardSession? {
        val dialectPrompt = when (dialect) {
            FahimDialect.EGYPTIAN -> "اللهجة المصرية المبسطة الواضحة"
            FahimDialect.FUSHA -> "اللغة العربية الفصحى العلمية السلسة"
        }

        val systemInstruction = """
            أنت نظام الفصول التفاعلية متعدد الوكلاء (OpenMAIC Multi-Agent Engine) داخل تطبيق «مُتعلِّم».
            انسق مخرجات 3 وكلاء (وكيل الشرح والرسم الهندسي/الفيزيائي، وكيل الخريطة الذهنية، ووكيل جدول المراجعة) بالـ $dialectPrompt.
            أرجع JSON صالحاً فقط بدون أي نص خارجي أو علامات ```json بالصيغة:
            {
              "topicTitle": "عنوان الدرس المختصر",
              "sceneType": "SHADED_CIRCLES_RECT | RIGHT_TRIANGLE_PYTHAGORAS | CIRCLE_PROPERTIES | COORDINATE_GRAPH | PHYSICS_FORCE_MOTION",
              "dimensionLabel": "d = 4",
              "radiusLabel": "r = 2",
              "secondaryLabel": "4 × 4",
              "finalSummaryText": "Area_shaded = 16 - (4 × π) ≈ 3.44",
              "agentSummaryText": "ملخص واضح ومباشر لأفكار الصفحة أو المسألة",
              "steps": [
                {"rightGivenText":"المعطى","leftOperationText":"العملية","centerFormulaText":"القاعدة أو الخطوة","speechLine":"النص المنطوق باللهجة المطلوبة","isFinalResult":false}
              ],
              "mindMap": {
                "centerTitle": "الفكرة المركزية",
                "branches": [
                  {"title":"محور 1","items":["عنصر 1","عنصر 2"]},
                  {"title":"محور 2","items":["عنصر 1","عنصر 2"]},
                  {"title":"محور 3","items":["عنصر 1","عنصر 2"]},
                  {"title":"محور 4","items":["عنصر 1","عنصر 2"]}
                ]
              },
              "studyTable": {
                "title": "خطة مراجعة الدرس",
                "columns": ["الوقت", "المهمة", "المدة"],
                "rows": [
                  {"time":"16:00 - 17:00","task":"مراجعة مفاهيم الدرس","duration":"ساعة","dayOfWeek":1,"start":"16:00","end":"17:00"}
                ]
              }
            }
        """.trimIndent()

        val userPrompt = buildString {
            append("الموضوع / المطلوب شرحه: ").append(topic)
            if (pdfDoc != null) {
                append("\nالمصدر المرفوع: ").append(pdfDoc.displayName)
                    .append(" — صفحة ").append(pdfDoc.currentPageIndex + 1)
                    .append(" من ").append(pdfDoc.pageCount)
            }
            if (pageContextText.isNotBlank()) {
                append("\n\nنص صفحة الكتاب/الملزمة:\n").append(pageContextText)
            }
        }

        val raw = geminiClient.generateContent(
            prompt = userPrompt,
            apiKey = apiKey,
            systemInstruction = systemInstruction,
            preferredModel = FahimConfig.PRIMARY_MODEL,
            imageBytesBase64 = pageImageBase64,
            temperature = 0.25,
            responseMimeType = "application/json"
        ).getOrNull() ?: return null

        return parseOpenMaicSessionJson(raw, topic, dialect, pdfDoc)
    }

    internal fun parseOpenMaicSessionJson(
        rawJson: String,
        fallbackTopic: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument? = null
    ): OpenMaicBoardSession? = runCatching {
        val cleaned = FahimGeminiClient.stripMarkdownJsonFences(rawJson)
        val obj = JSONObject(cleaned)
        val lesson = drawingEngine.parseWhiteboardLessonJson(cleaned, fallbackTopic, dialect)
            ?: return null

        val topicTitle = obj.optString("topicTitle").ifBlank { lesson.title }
        val agentSummary = obj.optString("agentSummaryText")
            .ifBlank { lesson.steps.joinToString("\n") { "• ${it.speechLine}" } }

        val mindMapObj = obj.optJSONObject("mindMap")
        val mindMap = parseMindMapObject(mindMapObj, topicTitle) ?: buildDefaultMindMap(topicTitle, pdfDoc)

        val tableObj = obj.optJSONObject("studyTable")
        val studyTable = parseStudyTableObject(tableObj, topicTitle) ?: buildDefaultStudyTable(topicTitle)

        OpenMaicBoardSession(
            topicTitle = topicTitle,
            sourcePdfName = pdfDoc?.displayName,
            sourcePageNumber = pdfDoc?.let { it.currentPageIndex + 1 },
            lesson = lesson,
            mindMap = mindMap,
            studyTable = studyTable,
            agentSummaryText = agentSummary
        )
    }.getOrNull()

    fun buildLocalOpenMaicSession(
        topic: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument? = null
    ): OpenMaicBoardSession {
        val combinedPrompt = buildString {
            append(topic)
            if (pdfDoc != null && pdfDoc.extractedPageText.isNotBlank()) {
                append(' ').append(pdfDoc.extractedPageText.take(300))
            }
        }
        val baseLesson = drawingEngine.buildLocalWhiteboardLesson(combinedPrompt, dialect)
        val enrichedLesson = if (pdfDoc != null) {
            val pageNum = pdfDoc.currentPageIndex + 1
            val snippet = pdfDoc.extractedPageText.lines()
                .map { it.trim() }
                .firstOrNull { it.length > 10 }
                ?.take(72)
                ?: "مفاهيم صفحة $pageNum من ${pdfDoc.displayName}"
            val introSpeech = when (dialect) {
                FahimDialect.EGYPTIAN -> "أهلاً بك يا بطل في صفحة $pageNum من ملف ${pdfDoc.displayName}. خلينا نبسط الفكرة الأساسية على السبورة خطوة بخطوة: $snippet"
                FahimDialect.FUSHA -> "مرحباً بك في الصفحة رقم $pageNum من ملف ${pdfDoc.displayName}. سنقوم الآن بشرح المفاهيم الأساسية ورسمها على السبورة: $snippet"
            }
            val pageStep = WhiteboardStep(
                index = 0,
                rightGivenText = "ملخص صفحة $pageNum من الكتاب",
                leftOperationText = "PDF • P.$pageNum",
                centerFormulaText = snippet,
                speechLine = introSpeech,
                arabicTitle = "ملخص صفحة $pageNum — $snippet",
                ltrFormula = "PDF Page $pageNum / ${pdfDoc.pageCount}"
            )
            val reindexed = listOf(pageStep) + baseLesson.steps.mapIndexed { idx, s ->
                s.copy(index = idx + 1)
            }
            baseLesson.copy(
                title = "${pdfDoc.displayName.substringBeforeLast('.')} • ص $pageNum",
                steps = reindexed
            )
        } else {
            baseLesson
        }

        val effectiveTitle = enrichedLesson.title
        val summary = buildString {
            if (pdfDoc != null) {
                append("📄 المصدر: ${pdfDoc.displayName} (صفحة ${pdfDoc.currentPageIndex + 1} من ${pdfDoc.pageCount})\n")
                if (pdfDoc.extractedPageText.isNotBlank()) {
                    append("📝 مقتطف مستخلص: ${pdfDoc.extractedPageText.take(220)}\n")
                }
            }
            append("💡 الخلاصة الذكية (OpenMAIC): ${enrichedLesson.finalSummaryText}")
        }

        return OpenMaicBoardSession(
            topicTitle = effectiveTitle,
            sourcePdfName = pdfDoc?.displayName,
            sourcePageNumber = pdfDoc?.let { it.currentPageIndex + 1 },
            lesson = enrichedLesson,
            mindMap = buildDefaultMindMap(effectiveTitle, pdfDoc),
            studyTable = buildDefaultStudyTable(effectiveTitle),
            agentSummaryText = summary
        )
    }

    private fun parseMindMapObject(obj: JSONObject?, fallbackCenter: String): FahimMindMapData? {
        if (obj == null) return null
        val center = obj.optString("centerTitle").ifBlank { fallbackCenter }
        val arr = obj.optJSONArray("branches") ?: return null
        val palette = listOf(0xFF1A6FE3L, 0xFFD97706L, 0xFF10B981L, 0xFF0D9488L)
        val branches = mutableListOf<FahimMindMapBranch>()
        for (i in 0 until arr.length()) {
            val bObj = arr.optJSONObject(i) ?: continue
            val bTitle = bObj.optString("title").ifBlank { "محور ${i + 1}" }
            val itemsArr = bObj.optJSONArray("items")
            val items = buildList {
                if (itemsArr != null) {
                    for (j in 0 until itemsArr.length()) {
                        val t = itemsArr.optString(j).trim()
                        if (t.isNotBlank()) add(t)
                    }
                }
            }.ifEmpty { listOf("مفهوم رئيسي") }
            branches += FahimMindMapBranch(
                title = bTitle,
                colorHex = palette[i % palette.size],
                items = items.take(4)
            )
        }
        return if (branches.isEmpty()) null else FahimMindMapData(centerTitle = center, branches = branches)
    }

    private fun parseStudyTableObject(obj: JSONObject?, fallbackTitle: String): FahimStudyTableData? {
        if (obj == null) return null
        val title = obj.optString("title").ifBlank { "جدول مراجعة: $fallbackTitle" }
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
        val rowTints = listOf(0xFFFFFFFFL, 0xFFEFF6FFL, 0xFFECFDF5L, 0xFFFEF3C7L, 0xFFF1F5F9L)
        val rows = mutableListOf<FahimStudyTableRow>()
        for (i in 0 until rowsArr.length()) {
            val rObj = rowsArr.optJSONObject(i) ?: continue
            val time = rObj.optString("time").ifBlank { "16:00 - 17:00" }
            val task = rObj.optString("task").ifBlank { "مراجعة $fallbackTitle" }
            val duration = rObj.optString("duration").ifBlank { "ساعة" }
            val dayOfWeek = rObj.optInt("dayOfWeek", (i % 6) + 1).coerceIn(1, 7)
            val start = rObj.optString("start").ifBlank { "16:00" }
            val end = rObj.optString("end").ifBlank { "17:00" }
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
        return if (rows.isEmpty()) null else FahimStudyTableData(
            title = title,
            columnHeaders = cols.take(3),
            rows = rows,
            isComparisonOnly = false
        )
    }

    private fun buildDefaultMindMap(title: String, pdfDoc: OpenMaicPdfDocument?): FahimMindMapData {
        val pageHint = pdfDoc?.let { "صفحة ${it.currentPageIndex + 1}" } ?: "الشرح التفاعلي"
        return FahimMindMapData(
            centerTitle = title.take(26),
            branches = listOf(
                FahimMindMapBranch("المفاهيم الأساسية", 0xFF1A6FE3L, listOf("المعطيات الرئيسية", pageHint, "التعريفات الهامة")),
                FahimMindMapBranch("القوانين والخطوات", 0xFFD97706L, listOf("صياغة القانون", "توحيد الوحدات", "التعويض التدريجي")),
                FahimMindMapBranch("تطبيقات وأمثلة", 0xFF10B981L, listOf("الرسم على الشبكة", "الربط البصري", "التحقق من الناتج")),
                FahimMindMapBranch("مراجعة الامتحان", 0xFF0D9488L, listOf("نقاط التركيز", "أخطاء شائعة", "تدريب سريع"))
            )
        )
    }

    private fun buildDefaultStudyTable(title: String): FahimStudyTableData {
        return FahimStudyTableData(
            title = "جدول تثبيت: ${title.take(28)}",
            columnHeaders = listOf("الوقت", "المهمة", "المدة"),
            rows = listOf(
                FahimStudyTableRow("09:00 - 10:00", "قراءة وفهم: ${title.take(24)}", "ساعة", dayOfWeek = 1, startTime = "09:00", endTime = "10:00", tintHex = 0xFFFFFFFFL),
                FahimStudyTableRow("10:30 - 11:30", "تطبيق الخريطة الذهنية والسبورة", "ساعة", dayOfWeek = 1, startTime = "10:30", endTime = "11:30", tintHex = 0xFFEFF6FFL),
                FahimStudyTableRow("12:00 - 13:00", "حل مسائل وتدريبات الفصل", "ساعة", dayOfWeek = 1, startTime = "12:00", endTime = "13:00", tintHex = 0xFFECFDF5L),
                FahimStudyTableRow("15:30 - 16:30", "مراجعة سريعة قبل التقييم", "ساعة", dayOfWeek = 1, startTime = "15:30", endTime = "16:30", tintHex = 0xFFFEF3C7L)
            ),
            isComparisonOnly = false
        )
    }

    private fun extractEmbeddedPdfText(context: Context, uri: Uri, pageIndex: Int): String {
        return runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val pageNumber = (pageIndex + 1).coerceIn(1, doc.numberOfPages.coerceAtLeast(1))
                    val stripper = PDFTextStripper().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                    }
                    stripper.getText(doc).trim()
                }
            }.orEmpty()
        }.getOrDefault("")
    }

    private suspend fun ocrBitmap(bitmap: Bitmap): String = runCatching {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { res ->
                    if (cont.isActive) cont.resume(res.text.orEmpty().trim())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("")
                }
        }
    }.getOrDefault("")

    private fun queryFileName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()

    internal fun encodeBitmapForVision(bitmap: Bitmap): String? = encodeBitmapJpegBase64(bitmap)

    private fun encodeBitmapJpegBase64(bitmap: Bitmap): String? = runCatching {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
