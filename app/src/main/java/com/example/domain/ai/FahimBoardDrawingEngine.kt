package com.example.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * اللهجات المعتمدة حصراً في محرك صوت «مُتعلِّم»: مصري (ar-EG) وفصحى (ar) فقط.
 */
enum class FahimDialect(
    val labelAr: String,
    val localeLang: String,
    val localeCountry: String,
    val bcp47Tag: String
) {
    EGYPTIAN("مصري", "ar", "EG", "ar-EG"),
    FUSHA("فصحى", "ar", "", "ar");

    companion object {
        fun fromName(name: String): FahimDialect =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: EGYPTIAN
    }
}

enum class WhiteboardGeometryScene {
    SHADED_CIRCLES_RECT,
    RIGHT_TRIANGLE_PYTHAGORAS,
    CIRCLE_PROPERTIES,
    COORDINATE_GRAPH,
    PHYSICS_FORCE_MOTION,
    ELECTRIC_CIRCUIT,
    OPTICS_LENS
}

/**
 * خطوة شرح مفصولة الاتجاه:
 * - [arabicTitle]: الشرح أو العنوان العربي الصافي (يُعرض بـ RTL).
 * - [ltrFormula]: المعادلة الرياضية أو الفيزيائية الصافية (تُعرض بـ LTR منفصل تماماً لمنع التداخل).
 */
data class WhiteboardStep(
    val index: Int,
    val rightGivenText: String = "",
    val leftOperationText: String = "",
    val centerFormulaText: String = "",
    val speechLine: String = "",
    val isFinalResult: Boolean = false,
    val arabicTitle: String = "",
    val ltrFormula: String = ""
) {
    /**
     * العنوان العربي النظيف المعروض في سطر RTL مستقل.
     */
    fun displayArabicTitle(): String {
        if (arabicTitle.isNotBlank()) return arabicTitle
        val candidates = listOf(rightGivenText, centerFormulaText).filter { it.isNotBlank() }
        val arabicCandidate = candidates.firstOrNull { containsArabic(it) && !containsLatinEquation(it) }
        if (arabicCandidate != null) return arabicCandidate
        val stripped = (rightGivenText.ifBlank { centerFormulaText })
            .replace(Regex("[A-Za-z_]+\\s*=\\s*[^\\u0600-\\u06FF]+"), "")
            .replace("←", "")
            .trim(' ', ':', '-', '•')
        return stripped.ifBlank { "الخطوة ${index + 1}" }
    }

    /**
     * المعادلة الإنجليزية/الرقمية الصافية المعروضة في بطاقة LTR مستقلة أسفل العنوان.
     */
    fun displayLtrFormula(): String {
        if (ltrFormula.isNotBlank()) return ltrFormula
        val parts = listOf(centerFormulaText, leftOperationText, rightGivenText)
            .filter { it.isNotBlank() && containsLatinEquation(it) }
        val chosen = parts.firstOrNull() ?: return ""
        // Strip leading Arabic label before ":" so pure LTR math stays clean
        val afterColon = if (chosen.contains(":") && containsArabic(chosen.substringBefore(":"))) {
            chosen.substringAfter(":").trim()
        } else {
            chosen.trim()
        }
        return afterColon.takeIf { it.isNotBlank() && it != displayArabicTitle() }.orEmpty()
    }

    companion object {
        private fun containsArabic(text: String): Boolean =
            text.any { it in '\u0600'..'\u06FF' }

        private fun containsLatinEquation(text: String): Boolean =
            text.any { it in 'A'..'Z' || it in 'a'..'z' || it in setOf('=', '×', '÷', '√', 'π', '²', 'Ω', '∠') }
    }
}

data class FahimWhiteboardLesson(
    val title: String,
    val sceneType: WhiteboardGeometryScene,
    val dimensionLabel: String = "d = 4",
    val radiusLabel: String = "r = 2",
    val secondaryLabel: String = "L = 16",
    val steps: List<WhiteboardStep>,
    val finalSummaryText: String,
    val excalidrawElements: List<ExcalidrawElementData> = emptyList(),
    val structuredTable: StructuredTableData? = null
)

class FahimBoardDrawingEngine(
    private val geminiClient: FahimGeminiClient = FahimGeminiClient()
) {

    suspend fun generateWhiteboardLesson(
        prompt: String,
        dialect: FahimDialect,
        customGeminiKey: String = ""
    ): FahimWhiteboardLesson = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank { "اشرح الدائرة الكهربائية وقانون أوم" }
        val apiKey = FahimConfig.resolveGeminiApiKey(customGeminiKey)

        if (apiKey.isNotBlank()) {
            val remoteJson = callGeminiWhiteboardJson(cleanPrompt, dialect, apiKey)
            if (remoteJson != null) {
                val parsed = parseWhiteboardLessonJson(remoteJson, cleanPrompt, dialect)
                if (parsed != null) return@withContext parsed
            }
        }

        if (FirebaseAiLogicClient.isAvailable()) {
            val dialectHint = when (dialect) {
                FahimDialect.EGYPTIAN -> "اللهجة المصرية الودودة المبسطة كمدرس خصوصي شاطر"
                FahimDialect.FUSHA -> "اللغة العربية الفصحى الواضحة والدقيقة"
            }
            val firebaseJson = FirebaseAiLogicClient.generateText(
                prompt = cleanPrompt,
                systemInstruction = "أنت محرك سبورة فهيم الذكية (OpenMAIC + Excalidraw) بـ $dialectHint. أرجع JSON صالحاً يحتوي title, sceneType, finalSummaryText, steps (arabicTitle, ltrFormula, speechLine, isFinalResult), و excalidrawElements (6 إلى 10 عناصر رسم مخصصة للسؤال).",
                temperature = 0.3f,
                responseMimeType = "application/json"
            ).getOrNull()
            if (!firebaseJson.isNullOrBlank()) {
                val parsed = parseWhiteboardLessonJson(firebaseJson, cleanPrompt, dialect)
                if (parsed != null) return@withContext parsed
            }
        }

        buildLocalWhiteboardLesson(cleanPrompt, dialect)
    }

    private suspend fun callGeminiWhiteboardJson(
        prompt: String,
        dialect: FahimDialect,
        apiKey: String
    ): String? {
        val dialectHint = when (dialect) {
            FahimDialect.EGYPTIAN -> "اللهجة المصرية الودودة المبسطة كمدرس خصوصي شاطر"
            FahimDialect.FUSHA -> "اللغة العربية الفصحى الواضحة والدقيقة"
        }

        val systemInstruction = """
            أنت محرك «سبورة فهيم الذكية» (OpenMAIC + Excalidraw). حوّل سؤال الطالب إلى خطة رسم هندسي/فيزيائي ومعادلات مفصولة الاتجاه خطوة بخطوة بـ $dialectHint.
            افصل دائماً الشرح العربي في "arabicTitle" عن المعادلة الصافية في "ltrFormula".
            أرجع JSON فقط دون أي نص خارجه بالصيغة التالية:
            {
              "title": "عنوان الدرس",
              "sceneType": "ELECTRIC_CIRCUIT | SHADED_CIRCLES_RECT | RIGHT_TRIANGLE_PYTHAGORAS | CIRCLE_PROPERTIES | COORDINATE_GRAPH | PHYSICS_FORCE_MOTION | OPTICS_LENS",
              "dimensionLabel": "V = 12 V",
              "radiusLabel": "R1 = 4 Ω",
              "secondaryLabel": "I = 2 A",
              "finalSummaryText": "I = V / R_eq = 2 A",
              "steps": [
                {
                  "arabicTitle": "عنوان الخطوة بالعربية فقط",
                  "ltrFormula": "V = I × R",
                  "speechLine": "جملة الشرح المنطوقة باللهجة المختارة",
                  "isFinalResult": false
                }
              ]
            }
        """.trimIndent()

        return geminiClient.generateContent(
            prompt = prompt,
            apiKey = apiKey,
            systemInstruction = systemInstruction,
            preferredModel = FahimConfig.PRIMARY_MODEL,
            temperature = 0.25,
            responseMimeType = "application/json"
        ).getOrNull()
    }

    internal fun parseWhiteboardLessonJson(
        rawJson: String,
        fallbackPrompt: String,
        dialect: FahimDialect
    ): FahimWhiteboardLesson? = runCatching {
        val cleaned = FahimGeminiClient.stripMarkdownJsonFences(rawJson)
        val obj = JSONObject(cleaned)
        val title = obj.optString("title").ifBlank {
            obj.optString("topicTitle").ifBlank { "شرح على سبورة فهيم" }
        }
        val sceneName = obj.optString("sceneType").uppercase()
        val scene = WhiteboardGeometryScene.entries.firstOrNull { it.name == sceneName }
            ?: detectSceneFromPrompt(fallbackPrompt)

        val stepsArr = obj.optJSONArray("steps") ?: return null
        val steps = mutableListOf<WhiteboardStep>()
        for (i in 0 until stepsArr.length()) {
            val sObj = stepsArr.optJSONObject(i) ?: continue
            val arabicTitle = sObj.optString("arabicTitle").trim()
            val ltrFormula = sObj.optString("ltrFormula").trim()
            val rightGiven = sObj.optString("rightGivenText").ifBlank { arabicTitle }
            val leftOp = sObj.optString("leftOperationText")
            val centerFormula = sObj.optString("centerFormulaText").ifBlank { ltrFormula }
            val speech = sObj.optString("speechLine").ifBlank {
                arabicTitle.ifBlank { centerFormula.ifBlank { rightGiven } }
            }
            steps += WhiteboardStep(
                index = i,
                rightGivenText = rightGiven,
                leftOperationText = leftOp,
                centerFormulaText = centerFormula,
                speechLine = speech,
                isFinalResult = sObj.optBoolean("isFinalResult", i == stepsArr.length() - 1),
                arabicTitle = arabicTitle,
                ltrFormula = ltrFormula
            )
        }
        if (steps.isEmpty()) return null

        val finalSummary = obj.optString("finalSummaryText")
            .ifBlank { steps.lastOrNull()?.displayLtrFormula().orEmpty().ifBlank { steps.lastOrNull()?.centerFormulaText.orEmpty() } }

        val elements = OpenMaicPdfSummarizer.parseExcalidrawElements(obj.optJSONArray("excalidrawElements"))
        val structuredTable = OpenMaicPdfSummarizer.parseStructuredTable(
            obj.optJSONObject("comparisonTable"),
            fallbackTitle = "ملخص القوانين والمقارنة"
        )

        FahimWhiteboardLesson(
            title = title,
            sceneType = scene,
            dimensionLabel = obj.optString("dimensionLabel", ""),
            radiusLabel = obj.optString("radiusLabel", ""),
            secondaryLabel = obj.optString("secondaryLabel", ""),
            steps = steps,
            finalSummaryText = finalSummary,
            excalidrawElements = elements,
            structuredTable = structuredTable
        )
    }.getOrNull()

    fun buildLocalWhiteboardLesson(
        prompt: String,
        dialect: FahimDialect
    ): FahimWhiteboardLesson {
        val scene = detectSceneFromPrompt(prompt)
        return when (scene) {
            WhiteboardGeometryScene.ELECTRIC_CIRCUIT -> buildElectricCircuitLesson(dialect)
            WhiteboardGeometryScene.SHADED_CIRCLES_RECT -> buildShadedAreaLesson(dialect)
            WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS -> buildPythagorasLesson(dialect)
            WhiteboardGeometryScene.CIRCLE_PROPERTIES -> buildCircleLesson(dialect)
            WhiteboardGeometryScene.PHYSICS_FORCE_MOTION -> buildPhysicsLesson(dialect)
            WhiteboardGeometryScene.COORDINATE_GRAPH -> buildGraphLesson(dialect)
            WhiteboardGeometryScene.OPTICS_LENS -> buildOpticsLesson(dialect)
        }
    }

    internal fun detectSceneFromPrompt(prompt: String): WhiteboardGeometryScene {
        val p = prompt.lowercase()
        return when {
            p.contains("كهرب") || p.contains("مقاوم") || p.contains("أوم") || p.contains("اوم") ||
                p.contains("تيار") || p.contains("جهد") || p.contains("دائرة كهرب") || p.contains("كيرشوف") ||
                p.contains("circuit") || p.contains("resistor") || p.contains("ohm") ->
                WhiteboardGeometryScene.ELECTRIC_CIRCUIT
            p.contains("عدس") || p.contains("مرآة") || p.contains("ضوء") || p.contains("انكسار") ||
                p.contains("بؤر") || p.contains("optics") || p.contains("lens") ->
                WhiteboardGeometryScene.OPTICS_LENS
            p.contains("مظلل") || p.contains("مستطيل") || p.contains("shaded") ->
                WhiteboardGeometryScene.SHADED_CIRCLES_RECT
            p.contains("مثلث") || p.contains("فيثاغور") || p.contains("وتر") || p.contains("زاوية") ->
                WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS
            p.contains("دائرة") || p.contains("نصف القطر") || p.contains("محيط") ->
                WhiteboardGeometryScene.CIRCLE_PROPERTIES
            p.contains("فيزياء") || p.contains("سرعة") || p.contains("تسارع") || p.contains("قوة") || p.contains("نيوتن") ->
                WhiteboardGeometryScene.PHYSICS_FORCE_MOTION
            p.contains("دالة") || p.contains("رسم بياني") || p.contains("معادلة") || p.contains("جبر") ->
                WhiteboardGeometryScene.COORDINATE_GRAPH
            else -> WhiteboardGeometryScene.ELECTRIC_CIRCUIT
        }
    }

    private fun buildElectricCircuitLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "بص يا بطل! في الدائرة الكهربائية المرسومة قدامنا عندنا مصدر جهد ٣ فولت، ومعاه ثلاث مقاومات متوصلين على التوالي والتفرع."
            FahimDialect.FUSHA -> "انظر إلى الدائرة الكهربائية المرسومة على السبورة؛ لدينا مصدر جهد ٣ فولت وثلاث مقاومات متصلة في شبكة مغلقة."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "أول حاجة بنجمع المقاومات المتوصلة على التوالي عشان نجيب المقاومة المكافئة الكلية للدائرة."
            FahimDialect.FUSHA -> "الخطوة التالية: نجمع المقاومات المتصلة على التوالي لإيجاد المقاومة المكافئة الكلية للدائرة."
        }
        val s3 = when (dialect) {
            FahimDialect.EGYPTIAN -> "وبعدين نطبق قانون أوم المباشر: شدة التيار بتساوي فرق الجهد مقسوم على المقاومة المكافئة، وتطلع النتيجة النهائية بدقة!"
            FahimDialect.FUSHA -> "أخيراً، نطبق قانون أوم لحساب شدة التيار الكلي بقسمة فرق الجهد على المقاومة المكافئة."
        }

        val table = StructuredTableData(
            title = "جدول عناصر الدائرة وقانون أوم",
            headers = listOf("العنصر", "الرمز / القانون", "القيمة"),
            rows = listOf(
                listOf("فرق الجهد الكلي", "V = I × R_eq", "3 V"),
                listOf("المقاومة الأولى", "R₁ (توالي)", "2 Ω"),
                listOf("المقاومة الثانية", "R₂ (توالي)", "4 Ω"),
                listOf("شدة التيار الكلي", "I = V / R_eq", "0.5 A")
            )
        )

        return FahimWhiteboardLesson(
            title = "تحليل الدائرة الكهربائية وقانون أوم",
            sceneType = WhiteboardGeometryScene.ELECTRIC_CIRCUIT,
            dimensionLabel = "V = 3 V",
            radiusLabel = "R₁ = 2 Ω",
            secondaryLabel = "R₂ = 4 Ω",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "تحديد معطيات مصدر الجهد والمقاومات",
                    leftOperationText = "V = 3 V",
                    centerFormulaText = "V = 3 V  |  R₁ = 2 Ω  |  R₂ = 4 Ω",
                    speechLine = s1,
                    arabicTitle = "١. تحديد معطيات مصدر الجهد والمقاومات على الرسم",
                    ltrFormula = "V = 3 V  |  R₁ = 2 Ω  |  R₂ = 4 Ω"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "حساب المقاومة المكافئة للتوالي",
                    leftOperationText = "2 + 4 = 6 Ω",
                    centerFormulaText = "R_eq = R₁ + R₂ = 6 Ω",
                    speechLine = s2,
                    arabicTitle = "٢. حساب المقاومة المكافئة الكلية للدائرة",
                    ltrFormula = "R_eq = R₁ + R₂ = 2 + 4 = 6 Ω"
                ),
                WhiteboardStep(
                    index = 2,
                    rightGivenText = "تطبيق قانون أوم لإيجاد شدة التيار",
                    leftOperationText = "3 / 6 = 0.5 A",
                    centerFormulaText = "I = V / R_eq = 3 / 6 = 0.5 A",
                    speechLine = s3,
                    isFinalResult = true,
                    arabicTitle = "٣. النتيجة النهائية لشدة التيار المار في الدائرة",
                    ltrFormula = "I = V / R_eq = 3 / 6 = 0.5 A"
                )
            ),
            finalSummaryText = "I = V / R_eq = 0.5 A",
            structuredTable = table
        )
    }

    private fun buildShadedAreaLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val speech1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "بص يا بطل! الأول بنرسم المستطيل وجواه الدوائر المتماسة، ولاحظ إن القطر d بيساوي 4 يعني نصف القطر r بيساوي 2."
            FahimDialect.FUSHA -> "أهلاً بك! نرسم أولاً المستطيل وبداخله الدوائر المتماسة، نلاحظ أن القطر يساوي ٤، إذن نصف القطر r يساوي ٢."
        }
        val speech2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "تاني خطوة: نحسب مساحة المستطيل الكبير: الطول في العرض، يعني 4 في 4 بيساوي 16."
            FahimDialect.FUSHA -> "الخطوة الثانية: نحسب مساحة المستطيل، وتساوي الطول ضرب العرض، أي ٤ ضرب ٤ وتساوي ١٦."
        }
        val speech3 = when (dialect) {
            FahimDialect.EGYPTIAN -> "تالت خطوة: نحسب مساحة الدائرة الواحدة: ط نق تربيع، يعني 4 × π."
            FahimDialect.FUSHA -> "الخطوة الثالثة: نحسب مساحة الدائرة الواحدة بالقانون π × r تربيع، فتساوي ٤ ضرب π."
        }
        val speech4 = when (dialect) {
            FahimDialect.EGYPTIAN -> "والنتيجة النهائية للجزء المظلل يا بطل: بنطرح مساحة الدائرة من مساحة المستطيل: 16 ناقص 4π، وتطلع تقريباً 3.44!"
            FahimDialect.FUSHA -> "وأخيراً، لإيجاد مساحة الجزء المظلل نطرح مساحة الدائرة من مساحة المستطيل فتكون النتيجة النهائية تقريباً ٣٫٤٤."
        }

        val table = StructuredTableData(
            title = "جدول خطوات وقوانين المساحة المظللة",
            headers = listOf("المطلوب", "القانون", "الناتج"),
            rows = listOf(
                listOf("نصف القطر", "r = d / 2", "2"),
                listOf("مساحة الشكل الخارجي", "Area_rect = 4 × 4", "16"),
                listOf("مساحة الدائرة", "Area_circle = π × r²", "12.56"),
                listOf("مساحة الجزء المظلل", "Area_rect − Area_circle", "3.44")
            )
        )

        return FahimWhiteboardLesson(
            title = "مساحة الجزء المظلل",
            sceneType = WhiteboardGeometryScene.SHADED_CIRCLES_RECT,
            dimensionLabel = "d = 4",
            radiusLabel = "r = 2",
            secondaryLabel = "4 × 4",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "تحديد القطر ونصف القطر",
                    leftOperationText = "r = d / 2 = 2",
                    centerFormulaText = "d = 4  →  r = 2",
                    speechLine = speech1,
                    arabicTitle = "١. تحديد الأبعاد الهندسية والقطر ونصف القطر",
                    ltrFormula = "d = 4  →  r = 2"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "حساب مساحة الشكل الخارجي",
                    leftOperationText = "4 × 4 = 16",
                    centerFormulaText = "Area_rect = 4 × 4 = 16",
                    speechLine = speech2,
                    arabicTitle = "٢. حساب مساحة المستطيل / المربع الخارجي",
                    ltrFormula = "Area_rect = 4 × 4 = 16"
                ),
                WhiteboardStep(
                    index = 2,
                    rightGivenText = "حساب مساحة الدائرة",
                    leftOperationText = "π × 2²",
                    centerFormulaText = "Area_circle = π × r² = 4π ≈ 12.56",
                    speechLine = speech3,
                    arabicTitle = "٣. حساب مساحة الدائرة الداخلية",
                    ltrFormula = "Area_circle = π × r² = 4π ≈ 12.56"
                ),
                WhiteboardStep(
                    index = 3,
                    rightGivenText = "مساحة الجزء المظلل",
                    leftOperationText = "16 - 12.56",
                    centerFormulaText = "Area_shaded = 16 - (4 × π) ≈ 3.44",
                    speechLine = speech4,
                    isFinalResult = true,
                    arabicTitle = "٤. النتيجة النهائية لمساحة الجزء المظلل",
                    ltrFormula = "Area_shaded = 16 - (4 × π) ≈ 3.44"
                )
            ),
            finalSummaryText = "Area_shaded = 16 - (4 × π) ≈ 3.44",
            structuredTable = table
        )
    }

    private fun buildPythagorasLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "في المثلث القائم ده، الضلع الأول 3 والضلع التاني 4 وعايزين نجيب طول الوتر c."
            FahimDialect.FUSHA -> "في هذا المثلث القائم الزاوية، طولا ضلعي القائمة هما ٣ و ٤، والمطلوب إيجاد طول الوتر c."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "نطبق نظرية فيثاغورس: مربع الوتر بيساوي مجموع مربعي الضلعين، يعني 9 + 16 = 25."
            FahimDialect.FUSHA -> "نطبق مبرهنة فيثاغورس: c² = a² + b²، أي ٩ + ١٦ وتساوي ٢٥."
        }
        val s3 = when (dialect) {
            FahimDialect.EGYPTIAN -> "ناخد الجذر التربيعي للطرفين: جذر 25 يساوي 5، وده الوتر المطلوب!"
            FahimDialect.FUSHA -> "بأخذ الجذر التربيعي للطرفين نحصل على c = √25 = 5."
        }

        val table = StructuredTableData(
            title = "جدول أضلاع المثلث القائم",
            headers = listOf("الضلع", "الرمز", "القيمة"),
            rows = listOf(
                listOf("ضلع القائمة الأول", "a", "3 cm"),
                listOf("ضلع القائمة الثاني", "b", "4 cm"),
                listOf("طول الوتر", "c = √(a² + b²)", "5 cm")
            )
        )

        return FahimWhiteboardLesson(
            title = "نظرية فيثاغورس والمثلث القائم",
            sceneType = WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS,
            dimensionLabel = "c = 5",
            radiusLabel = "b = 4",
            secondaryLabel = "a = 3",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "معطيات المثلث القائم الزاوية",
                    leftOperationText = "∠ = 90°",
                    centerFormulaText = "a = 3 , b = 4 , ∠B = 90°",
                    speechLine = s1,
                    arabicTitle = "١. تحديد ضلعي القائمة والزاوية القائمة",
                    ltrFormula = "a = 3  ,  b = 4  ,  ∠B = 90°"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "تطبيق نظرية فيثاغورس",
                    leftOperationText = "3² + 4² = 25",
                    centerFormulaText = "c² = a² + b² = 9 + 16 = 25",
                    speechLine = s2,
                    arabicTitle = "٢. التعويض في قانون نظرية فيثاغورس",
                    ltrFormula = "c² = a² + b² = 3² + 4² = 25"
                ),
                WhiteboardStep(
                    index = 2,
                    rightGivenText = "حساب طول الوتر بالجذر التربيعي",
                    leftOperationText = "c = √25",
                    centerFormulaText = "c = √25 = 5 cm",
                    speechLine = s3,
                    isFinalResult = true,
                    arabicTitle = "٣. أخذ الجذر التربيعي لإيجاد طول الوتر",
                    ltrFormula = "c = √25 = 5 cm"
                )
            ),
            finalSummaryText = "c = √(3² + 4²) = 5",
            structuredTable = table
        )
    }

    private fun buildCircleLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "عشان نحسب محيط ومساحة الدائرة، أهم حاجة نحدد نصف القطر r = 7 سم."
            FahimDialect.FUSHA -> "لحساب محيط الدائرة ومساحتها، نحدد أولاً نصف القطر r = ٧ سم."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "المحيط بيساوي 2 ط نق، يعني 2 × (22/7) × 7 = 44 سم، والمساحة بتساوي ط نق تربيع = 154 سم مربع."
            FahimDialect.FUSHA -> "المحيط يساوي ٢ × π × r ويساوي ٤٤ سم، والمساحة π × r² وتساوي ١٥٤ سم مربع."
        }

        val table = StructuredTableData(
            title = "جدول قوانين الدائرة",
            headers = listOf("الكمية", "القانون", "الناتج"),
            rows = listOf(
                listOf("القطر الكامل", "d = 2 × r", "14 cm"),
                listOf("محيط الدائرة", "C = 2 × π × r", "44 cm"),
                listOf("مساحة الدائرة", "A = π × r²", "154 cm²")
            )
        )

        return FahimWhiteboardLesson(
            title = "قوانين مساحة ومحيط الدائرة",
            sceneType = WhiteboardGeometryScene.CIRCLE_PROPERTIES,
            dimensionLabel = "d = 14",
            radiusLabel = "r = 7",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "تحديد نصف القطر والقطر",
                    leftOperationText = "π ≈ 22 / 7",
                    centerFormulaText = "r = 7 cm  →  d = 14 cm",
                    speechLine = s1,
                    arabicTitle = "١. تحديد نصف القطر والقطر الكامل",
                    ltrFormula = "r = 7 cm  →  d = 2r = 14 cm"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "حساب محيط الدائرة",
                    leftOperationText = "2 × (22/7) × 7",
                    centerFormulaText = "C = 2 × π × r = 44 cm",
                    speechLine = s2,
                    arabicTitle = "٢. حساب محيط الدائرة",
                    ltrFormula = "C = 2 × π × r = 44 cm"
                ),
                WhiteboardStep(
                    index = 2,
                    rightGivenText = "حساب مساحة الدائرة",
                    leftOperationText = "(22/7) × 49",
                    centerFormulaText = "Area = π × r² = 154 cm²",
                    speechLine = s2,
                    isFinalResult = true,
                    arabicTitle = "٣. النتيجة النهائية لمساحة الدائرة",
                    ltrFormula = "Area = π × r² = 154 cm²"
                )
            ),
            finalSummaryText = "Area = π × r² = 154 cm²",
            structuredTable = table
        )
    }

    private fun buildPhysicsLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "في الفيزياء، قانون نيوتن التاني بيقول إن القوة F بتساوي الكتلة m مضروبة في العجلة a."
            FahimDialect.FUSHA -> "ينص قانون نيوتن الثاني على أن القوة المحصلة تساوي حاصل ضرب الكتلة في التسارع."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "لو الكتلة 5 كجم والتسارع 4 متر على الثانية تربيع، يبقى القوة بتساوي 5 × 4 = 20 نيوتن!"
            FahimDialect.FUSHA -> "بتعويض m = ٥ كجم و a = ٤ م/ث²، تكون القوة الناتجة ٢٠ نيوتن."
        }

        val table = StructuredTableData(
            title = "جدول كميات قانون نيوتن الثاني",
            headers = listOf("الكمية الفيزيائية", "الرمز", "القيمة والوحدة"),
            rows = listOf(
                listOf("الكتلة", "m", "5 kg"),
                listOf("التسارع / العجلة", "a", "4 m/s²"),
                listOf("القوة المحصلة", "F = m × a", "20 N")
            )
        )

        return FahimWhiteboardLesson(
            title = "تحليل القوى وقانون نيوتن الثاني",
            sceneType = WhiteboardGeometryScene.PHYSICS_FORCE_MOTION,
            dimensionLabel = "F = 20 N",
            radiusLabel = "m = 5 kg",
            secondaryLabel = "a = 4 m/s²",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "صياغة قانون نيوتن الثاني",
                    leftOperationText = "m = 5 kg , a = 4 m/s²",
                    centerFormulaText = "F = m × a",
                    speechLine = s1,
                    arabicTitle = "١. كتابة القانون الأساسي وتحديد المعطيات",
                    ltrFormula = "F = m × a   (m = 5 kg , a = 4 m/s²)"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "التعويض وحساب القوة المحصلة",
                    leftOperationText = "5 × 4 = 20 N",
                    centerFormulaText = "F = 5 × 4 = 20 N",
                    speechLine = s2,
                    isFinalResult = true,
                    arabicTitle = "٢. النتيجة النهائية للقوة المحصلة بالنيوتن",
                    ltrFormula = "F = 5 × 4 = 20 N"
                )
            ),
            finalSummaryText = "F = m × a = 20 N",
            structuredTable = table
        )
    }

    private fun buildGraphLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "هنا بنمثل الدالة التربيعية y = x² على الشبكة الإحداثية، ورأس المنحنى عند نقطة الأصل (0, 0)."
            FahimDialect.FUSHA -> "نمثل الدالة التربيعية y = x² بيانياً حيث يكون رأس القطع المكافئ عند نقطة الأصل."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "لما x = 2 بتكون y = 4، والمنحنى متماثل حوالين محور الصادات y."
            FahimDialect.FUSHA -> "عند x = ٢ فإن y = ٤، والمنحنى متماثل تماماً حول محور الصادات."
        }

        val table = StructuredTableData(
            title = "جدول قيم الدالة التربيعية",
            headers = listOf("قيمة x", "التعويض", "قيمة y"),
            rows = listOf(
                listOf("-2", "(-2)²", "4"),
                listOf("0", "0²", "0"),
                listOf("2", "2²", "4")
            )
        )

        return FahimWhiteboardLesson(
            title = "التمثيل البياني للدالة التربيعية",
            sceneType = WhiteboardGeometryScene.COORDINATE_GRAPH,
            dimensionLabel = "(2, 4)",
            radiusLabel = "(0, 0)",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "تحديد رأس المنحنى ومحور التماثل",
                    leftOperationText = "y = x²",
                    centerFormulaText = "Vertex = (0, 0)  |  x = 0",
                    speechLine = s1,
                    arabicTitle = "١. تحديد رأس القطع المكافئ ومحور التماثل",
                    ltrFormula = "f(x) = x²  |  Vertex = (0, 0)"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "إيجاد نقاط التماثل على المنحنى",
                    leftOperationText = "y = (±2)² = 4",
                    centerFormulaText = "f(2) = 4  ,  f(-2) = 4",
                    speechLine = s2,
                    isFinalResult = true,
                    arabicTitle = "٢. نقاط المنحنى المتماثلة حول محور الصادات",
                    ltrFormula = "f(±2) = (±2)² = 4  →  (2, 4) , (-2, 4)"
                )
            ),
            finalSummaryText = "f(x) = x²  |  f(2) = 4",
            structuredTable = table
        )
    }

    private fun buildOpticsLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "في العدسة المحدبة، الأشعة الموازية للمحور الأصلي بتنكسر وتتجمع كلها في البؤرة F."
            FahimDialect.FUSHA -> "في العدسة المحدبة، تنكسر الأشعة الضوئية الموازية للمحور الرئيسي وتتجمع في البؤرة الأصلية F."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "ونستخدم القانون العام للعدسات عشان نحسب بُعد الصورة بدقة."
            FahimDialect.FUSHA -> "نطبق المعادلة العامة للعدسات لحساب بُعد الصورة المتكونة بدقة."
        }
        val table = StructuredTableData(
            title = "جدول البصريات والعدسات",
            headers = listOf("العنصر", "الرمز", "المعنى الفيزيائي"),
            rows = listOf(
                listOf("البعد البؤري", "f", "المسافة بين المركز البصري والبؤرة"),
                listOf("بُعد الجسم", "u", "المسافة بين الجسم والعدسة"),
                listOf("بُعد الصورة", "v", "المسافة بين الصورة والعدسة")
            )
        )
        return FahimWhiteboardLesson(
            title = "انكسار الضوء في العدسة المحدبة",
            sceneType = WhiteboardGeometryScene.OPTICS_LENS,
            dimensionLabel = "f = 10 cm",
            radiusLabel = "u = 20 cm",
            secondaryLabel = "v = 20 cm",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "تجمع الأشعة المتوازية في البؤرة",
                    leftOperationText = "f = 10 cm",
                    centerFormulaText = "1/f = 1/u + 1/v",
                    speechLine = s1,
                    arabicTitle = "١. مسار الأشعة المنكسرة عبر العدسة المحدبة",
                    ltrFormula = "1/f = 1/u + 1/v"
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "حساب بُعد الصورة المتكونة",
                    leftOperationText = "1/10 - 1/20",
                    centerFormulaText = "v = 20 cm",
                    speechLine = s2,
                    isFinalResult = true,
                    arabicTitle = "٢. النتيجة النهائية لبُعد الصورة الحقيقية المقلوبة",
                    ltrFormula = "1/v = 1/10 - 1/20 = 1/20  →  v = 20 cm"
                )
            ),
            finalSummaryText = "1/f = 1/u + 1/v  →  v = 20 cm",
            structuredTable = table
        )
    }
}
