package com.example.domain.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * حالة سبورة فهيم الذكية — الحالة الافتراضية دائماً هي [Empty] بدون أي بيانات وهمية مسبقة.
 */
sealed class WhiteboardState {
    data object Empty : WhiteboardState()
    data class Loading(val statusMessage: String = "فهيم يجهز الشرح والرسم التفاعلي...") : WhiteboardState()
    data class Active(val session: OpenMaicBoardSession) : WhiteboardState()
    data class Error(val message: String, val retryPrompt: String = "") : WhiteboardState()
}

/**
 * أنواع العناصر الرسومية المتوافقة مع محرك Excalidraw المنظم على كراسة المربعات.
 */
enum class ExcalidrawElementType {
    LINE,
    ARROW,
    RECT,
    CIRCLE,
    RESISTOR,
    BATTERY,
    TRIANGLE,
    LABEL,
    NODE;

    companion object {
        fun fromName(raw: String): ExcalidrawElementType =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: LINE
    }
}

/**
 * عنصر رسم متجهي بإحداثيات معيارية (0f..1f) لتكيّف مثالي مع جميع أحجام الشاشات.
 */
data class ExcalidrawElementData(
    val type: ExcalidrawElementType,
    val x1: Float = 0.1f,
    val y1: Float = 0.5f,
    val x2: Float = 0.9f,
    val y2: Float = 0.5f,
    val x3: Float = 0.5f,
    val y3: Float = 0.2f,
    val radius: Float = 0.12f,
    val colorHex: Long = 0xFF1E1B18L,
    val label: String = "",
    val strokeWidthDp: Float = 2.4f
)

/**
 * جدول منظم للمقارنات أو القوانين أو النتائج يُعرض بنظام موحد داخل السبورة.
 */
data class StructuredTableData(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

/**
 * محرك تلخيص كتب وملزمات PDF بمعمارية OpenMAIC متعددة الوكلاء (OpenMaicPdfSummarizer):
 * - يستخرج نصوص الصفحات عبر Android PdfRenderer + PDFBox + ML Kit OCR.
 * - يستخدم Firebase AI Logic (`gemini-3.8-flash`) مباشرة لتوليد رسم Excalidraw مخصص لكل سؤال
 *   وخطوات شرح مكتوبة حياً باللهجة المختارة (مصري / فصحى) وجدول منظم وخريطة ذهنية.
 */
class OpenMaicPdfSummarizer(
    private val geminiClient: FahimGeminiClient = FahimGeminiClient(),
    private val pdfBoardEngine: OpenMaicPdfBoardEngine = OpenMaicPdfBoardEngine(geminiClient)
) {

    suspend fun inspectPdfPage(
        context: Context,
        uri: Uri,
        pageIndex: Int = 0
    ): OpenMaicPdfDocument? = pdfBoardEngine.inspectAndLoadPdfPage(context, uri, pageIndex)

    suspend fun summarizeAndBuildSession(
        topicOrQuestion: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument? = null,
        customGeminiKey: String = "",
        preferMindMap: Boolean = false
    ): OpenMaicBoardSession = summarizeAndBuildSessionResult(
        topicOrQuestion = topicOrQuestion,
        dialect = dialect,
        pdfDoc = pdfDoc,
        customGeminiKey = customGeminiKey,
        preferMindMap = preferMindMap
    ).getOrElse {
        pdfBoardEngine.buildLocalOpenMaicSession(
            topic = topicOrQuestion.trim().ifBlank { "شرح درس جديد" },
            dialect = dialect,
            pdfDoc = pdfDoc
        )
    }

    suspend fun summarizeAndBuildSessionResult(
        topicOrQuestion: String,
        dialect: FahimDialect,
        pdfDoc: OpenMaicPdfDocument? = null,
        customGeminiKey: String = "",
        preferMindMap: Boolean = false
    ): Result<OpenMaicBoardSession> = withContext(Dispatchers.IO) {
        val cleanPrompt = topicOrQuestion.trim().ifBlank {
            when {
                pdfDoc != null -> "لخّص واشرح صفحة ${pdfDoc.currentPageIndex + 1} من ${pdfDoc.displayName}"
                preferMindMap -> "لخّص الدرس بخريطة ذهنية منظمة وقوانين أساسية"
                else -> "اشرح الدائرة الكهربائية وقانون أوم"
            }
        }

        val apiKey = FahimConfig.resolveGeminiApiKey(customGeminiKey)
        val systemInstruction = buildOpenMaicExcalidrawSystemPrompt(dialect)
        val userPrompt = buildOpenMaicUserPrompt(cleanPrompt, pdfDoc, preferMindMap)
        val pageImageBase64 = if (pdfDoc?.currentPagePreviewBitmap != null && pdfDoc.extractedPageText.length < 100) {
            pdfBoardEngine.encodeBitmapForVision(pdfDoc.currentPagePreviewBitmap)
        } else {
            null
        }

        // 1. Optional Custom Developer Gemini API Key
        if (apiKey.isNotBlank()) {
            val raw = geminiClient.generateContent(
                prompt = userPrompt,
                apiKey = apiKey,
                systemInstruction = systemInstruction,
                preferredModel = FahimConfig.PRIMARY_MODEL,
                imageBytesBase64 = pageImageBase64,
                temperature = 0.25,
                responseMimeType = "application/json"
            ).getOrNull()
            if (raw != null) {
                val parsed = pdfBoardEngine.parseOpenMaicSessionJson(raw, cleanPrompt, dialect, pdfDoc)
                if (parsed != null) return@withContext Result.success(parsed)
            }
        }

        // 2. Primary Live AI Engine: Firebase AI Logic (gemini-3.8-flash, works out of the box)
        if (FirebaseAiLogicClient.isAvailable()) {
            val aiRes = FirebaseAiLogicClient.generateText(
                prompt = userPrompt,
                systemInstruction = systemInstruction,
                imageBytesBase64 = pageImageBase64,
                temperature = 0.3f,
                responseMimeType = "application/json"
            )
            val rawJson = aiRes.getOrNull()
            if (!rawJson.isNullOrBlank()) {
                val parsed = pdfBoardEngine.parseOpenMaicSessionJson(rawJson, cleanPrompt, dialect, pdfDoc)
                if (parsed != null) {
                    return@withContext Result.success(parsed)
                }
            }
            val errMsg = aiRes.exceptionOrNull()?.localizedMessage
                ?: "تعذر تحليل استجابة السبورة الذكية. اضغط إعادة المحاولة."
            return@withContext Result.failure(
                IllegalStateException("تعذر الاتصال بمحرك فهيم الذكي لتوليد الشرح والرسم: $errMsg")
            )
        }

        // 3. Offline JVM/unit-test fallback only when FirebaseApp is not initialized
        Result.success(
            pdfBoardEngine.buildLocalOpenMaicSession(
                topic = cleanPrompt,
                dialect = dialect,
                pdfDoc = pdfDoc
            )
        )
    }

    private fun buildOpenMaicExcalidrawSystemPrompt(dialect: FahimDialect): String {
        val dialectRule = when (dialect) {
            FahimDialect.EGYPTIAN -> "اللهجة المصرية الطبيعية الودودة والواضحة مثل مدرس خصوصي مصري شاطر (استخدم كلمات مثل: بص يا بطل، يعني، عشان، خلينا، كده)"
            FahimDialect.FUSHA -> "اللغة العربية الفصحى العلمية الواضحة والسلسة والدقيقة"
        }

        return """
            أنت محرك OpenMAIC + Excalidraw داخل «سبورة فهيم الذكية» في تطبيق «مُتعلِّم».
            انسق بين 3 وكلاء (وكيل الشرح بالـ $dialectRule، وكيل الرسم المتجهي اليدوي Excalidraw، وكيل الجداول والخرائط الذهنية).
            قواعد إلزامية لا يجوز مخالفتها:
            1. صمم رسماً متجهياً مخصصاً لسؤال الطالب تحديداً في "excalidrawElements" (من 6 إلى 12 عنصراً على الأقل!) بحيث يُرسم خطوة بخطوة بالقلم الحي على السبورة!
               - للأسئلة النظرية أو الأحياء أو التاريخ أو النحو أو أي مفهوم: ارسم مخطط تدفق/علاقات منظم باستخدام NODE و ARROW و RECT و CIRCLE و LABEL مع تسميات قصيرة واضحة من نفس موضوع السؤال!
               - للرياضيات والهندسة والفيزياء والكيمياء: ارسم الشكل الهندسي أو الفيزيائي الدقيق (TRIANGLE / CIRCLE / RECT / RESISTOR / BATTERY / ARROW / LINE / NODE / LABEL) مع الأبعاد والقيم الحقيقية من سؤال الطالب.
               - جميع الإحداثيات x1,y1,x2,y2 معيارية بين 0.08 و 0.92 وموزعة بتناسق دون تداخل النصوص.
               - الألوان المتاحة فقط: "#1E1B18" (فحمي)، "#1A6FE3" (أزرق ملكي)، "#D97706" (ذهبي)، "#10B981" (أخضر زمردي).
            2. افصل الشرح العربي تماماً عن المعادلات/الرموز الإنجليزية في "steps":
               - ضع الشرح العربي المفصل خطوة بخطوة في "arabicTitle" (4 إلى 6 خطوات واضحة).
               - ضع المعادلة أو القاعدة المختصرة في "ltrFormula" (اتركها فارغة "" إن لم تكن هناك معادلة رياضية).
               - اكتب في "speechLine" شرحاً طبيعياً ممتعاً بـ $dialectRule ليقرأه الصوت العصبي الحي.
            أرجع كائن JSON صالحاً فقط بدون أي نص خارجه بالصيغة:
            {
              "topicTitle": "عنوان دقيق لسؤال الطالب",
              "sceneType": "ELECTRIC_CIRCUIT | SHADED_CIRCLES_RECT | RIGHT_TRIANGLE_PYTHAGORAS | CIRCLE_PROPERTIES | COORDINATE_GRAPH | PHYSICS_FORCE_MOTION | OPTICS_LENS",
              "dimensionLabel": "تسمية أساسية",
              "radiusLabel": "تسمية فرعية",
              "secondaryLabel": "تسمية مساعدة",
              "finalSummaryText": "الخلاصة أو الناتج النهائي الدقيق",
              "agentSummaryText": "ملخص مركز وشامل للدرس أو صفحة الـ PDF بـ $dialectRule",
              "steps": [
                {
                  "arabicTitle": "١. شرح الخطوة الأولى بوضوح تام",
                  "ltrFormula": "اختياري للمعادلات فقط أو فارغ",
                  "speechLine": "الشرح المنطوق الطبيعي بـ $dialectRule لهذه الخطوة",
                  "isFinalResult": false
                }
              ],
              "excalidrawElements": [
                {"type":"NODE","x1":0.50,"y1":0.18,"radius":0.16,"colorHex":"#1A6FE3","label":"الفكرة الرئيسية"},
                {"type":"ARROW","x1":0.42,"y1":0.28,"x2":0.25,"y2":0.52,"colorHex":"#1E1B18","label":""},
                {"type":"NODE","x1":0.24,"y1":0.62,"radius":0.15,"colorHex":"#D97706","label":"الفرع الأول"},
                {"type":"ARROW","x1":0.58,"y1":0.28,"x2":0.75,"y2":0.52,"colorHex":"#1E1B18","label":""},
                {"type":"NODE","x1":0.76,"y1":0.62,"radius":0.15,"colorHex":"#10B981","label":"الفرع الثاني"},
                {"type":"ARROW","x1":0.38,"y1":0.64,"x2":0.62,"y2":0.64,"colorHex":"#1A6FE3","label":"علاقة"}
              ],
              "comparisonTable": {
                "title": "جدول ملخص لأهم عناصر الدرس",
                "headers": ["العنصر", "الشرح / القاعدة", "ملاحظة هامة"],
                "rows": [
                  ["عنصر ١", "شرح مختصر", "تطبيق"]
                ]
              },
              "mindMap": {
                "centerTitle": "عنوان الموضوع",
                "branches": [
                  {"title":"المفاهيم","items":["نقطة ١","نقطة ٢"]},
                  {"title":"الخطوات","items":["نقطة ١","نقطة ٢"]},
                  {"title":"أمثلة","items":["مثال ١","مثال ٢"]},
                  {"title":"نقاط الامتحان","items":["نقطة ١","نقطة ٢"]}
                ]
              },
              "studyTable": {
                "title": "خطة مراجعة الدرس",
                "columns": ["الوقت", "المهمة", "المدة"],
                "rows": [
                  {"time":"16:00 - 17:00","task":"مراجعة الدرس وحل تدريبات","duration":"ساعة","dayOfWeek":1,"start":"16:00","end":"17:00"}
                ]
              }
            }
        """.trimIndent()
    }

    private fun buildOpenMaicUserPrompt(
        prompt: String,
        pdfDoc: OpenMaicPdfDocument?,
        preferMindMap: Boolean
    ): String = buildString {
        append("سؤال الطالب / المطلوب شرحه ورسمه حياً على السبورة: ").append(prompt)
        if (preferMindMap) {
            append("\nركز بشكل خاص على إنتاج خريطة ذهنية غنية ومخطط Excalidraw متفرع وجدول مقارنة منظم.")
        }
        if (pdfDoc != null) {
            append("\nالمصدر PDF: ").append(pdfDoc.displayName)
                .append(" — صفحة ").append(pdfDoc.currentPageIndex + 1)
                .append(" من ").append(pdfDoc.pageCount)
            if (pdfDoc.extractedPageText.isNotBlank()) {
                append("\n\nنص الصفحة المستخرج:\n").append(pdfDoc.extractedPageText.take(3000))
            }
        }
    }

    companion object {
        fun parseExcalidrawElements(arr: JSONArray?): List<ExcalidrawElementData> {
            if (arr == null) return emptyList()
            val list = mutableListOf<ExcalidrawElementData>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = ExcalidrawElementType.fromName(obj.optString("type"))
                val color = parseHexColor(obj.optString("colorHex"))
                list += ExcalidrawElementData(
                    type = type,
                    x1 = obj.optDouble("x1", 0.15).toFloat().coerceIn(0.02f, 0.98f),
                    y1 = obj.optDouble("y1", 0.5).toFloat().coerceIn(0.02f, 0.98f),
                    x2 = obj.optDouble("x2", 0.85).toFloat().coerceIn(0.02f, 0.98f),
                    y2 = obj.optDouble("y2", 0.5).toFloat().coerceIn(0.02f, 0.98f),
                    x3 = obj.optDouble("x3", 0.5).toFloat().coerceIn(0.02f, 0.98f),
                    y3 = obj.optDouble("y3", 0.2).toFloat().coerceIn(0.02f, 0.98f),
                    radius = obj.optDouble("radius", 0.14).toFloat().coerceIn(0.03f, 0.45f),
                    colorHex = color,
                    label = obj.optString("label").trim(),
                    strokeWidthDp = obj.optDouble("strokeWidthDp", 2.4).toFloat().coerceIn(1.2f, 5f)
                )
            }
            return list
        }

        fun parseStructuredTable(obj: JSONObject?, fallbackTitle: String): StructuredTableData? {
            if (obj == null) return null
            val title = obj.optString("title").ifBlank { fallbackTitle }
            val headersArr = obj.optJSONArray("headers") ?: obj.optJSONArray("columns") ?: return null
            val headers = buildList {
                for (i in 0 until headersArr.length()) {
                    val h = headersArr.optString(i).trim()
                    if (h.isNotBlank()) add(h)
                }
            }
            if (headers.isEmpty()) return null
            val rowsArr = obj.optJSONArray("rows") ?: return null
            val rows = mutableListOf<List<String>>()
            for (i in 0 until rowsArr.length()) {
                val rArr = rowsArr.optJSONArray(i) ?: continue
                val cells = buildList {
                    for (j in 0 until headers.size) {
                        add(rArr.optString(j).trim().ifBlank { "—" })
                    }
                }
                if (cells.any { it != "—" }) rows += cells
            }
            return if (rows.isEmpty()) null else StructuredTableData(title = title, headers = headers, rows = rows)
        }

        private fun parseHexColor(raw: String): Long {
            val clean = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            val parsed = clean.toLongOrNull(16) ?: return 0xFF1E1B18L
            return if (clean.length <= 6) (0xFF000000L or parsed) else parsed
        }
    }
}
