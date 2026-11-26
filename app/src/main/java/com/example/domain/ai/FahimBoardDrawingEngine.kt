package com.example.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class FahimDialect(val labelAr: String, val localeLang: String, val localeCountry: String) {
    EGYPTIAN("مصري", "ar", "EG"),
    FUSHA("فصحى", "ar", ""),
    SAUDI("سعودي", "ar", "SA");

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
    PHYSICS_FORCE_MOTION
}

data class WhiteboardStep(
    val index: Int,
    val rightGivenText: String = "",
    val leftOperationText: String = "",
    val centerFormulaText: String = "",
    val speechLine: String = "",
    val isFinalResult: Boolean = false
)

data class FahimWhiteboardLesson(
    val title: String,
    val sceneType: WhiteboardGeometryScene,
    val dimensionLabel: String = "d = 4",
    val radiusLabel: String = "r = 2",
    val secondaryLabel: String = "L = 16",
    val steps: List<WhiteboardStep>,
    val finalSummaryText: String
)

class FahimBoardDrawingEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun generateWhiteboardLesson(
        prompt: String,
        dialect: FahimDialect,
        customGeminiKey: String = ""
    ): FahimWhiteboardLesson = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank { "اشرح مساحة الجزء المظلل" }
        val apiKey = FahimConfig.resolveGeminiApiKey(customGeminiKey)

        if (apiKey.isNotBlank()) {
            val remoteJson = callGeminiWhiteboardJson(cleanPrompt, dialect, apiKey)
            if (remoteJson != null) {
                val parsed = parseWhiteboardLessonJson(remoteJson, cleanPrompt, dialect)
                if (parsed != null) return@withContext parsed
            }
        }

        buildLocalWhiteboardLesson(cleanPrompt, dialect)
    }

    private fun callGeminiWhiteboardJson(
        prompt: String,
        dialect: FahimDialect,
        apiKey: String
    ): String? = runCatching {
        val dialectHint = when (dialect) {
            FahimDialect.EGYPTIAN -> "اللهجة المصرية الودودة المبسطة كمدرس خصوصي شاطر"
            FahimDialect.FUSHA -> "اللغة العربية الفصحى الواضحة والدقيقة"
            FahimDialect.SAUDI -> "اللهجة السعودية المبسطة بأسلوب اختبارات القدرات والتحصيلي"
        }

        val systemInstruction = """
            أنت محرك «سبورة فهيم الذكية». حوّل سؤال الطالب إلى خطة رسم هندسي ومعادلات ملونة خطوة بخطوة بـ $dialectHint.
            أرجع JSON فقط دون أي نص خارجه بالصيغة التالية:
            {
              "title": "عنوان الدرس",
              "sceneType": "SHADED_CIRCLES_RECT | RIGHT_TRIANGLE_PYTHAGORAS | CIRCLE_PROPERTIES | COORDINATE_GRAPH | PHYSICS_FORCE_MOTION",
              "dimensionLabel": "d = 4",
              "radiusLabel": "r = 2",
              "secondaryLabel": "L = 16",
              "finalSummaryText": "Area_shaded = 16 - (4 × π) ≈ 3.44",
              "steps": [
                {
                  "rightGivenText": "المعطى أو العلاقة",
                  "leftOperationText": "التعويض المقابل",
                  "centerFormulaText": "خطوة الاستنتاج الوسطى",
                  "speechLine": "جملة الشرح المنطوقة باللهجة المختارة",
                  "isFinalResult": false
                }
              ]
            }
        """.trimIndent()

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.25)
                put("responseMimeType", "application/json")
            })
        }

        val model = FahimConfig.GEMINI_MODEL
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val raw = resp.body?.string().orEmpty()
            val root = JSONObject(raw)
            root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    internal fun parseWhiteboardLessonJson(
        rawJson: String,
        fallbackPrompt: String,
        dialect: FahimDialect
    ): FahimWhiteboardLesson? = runCatching {
        val cleaned = rawJson
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val obj = JSONObject(cleaned)
        val title = obj.optString("title").ifBlank { "شرح على سبورة فهيم" }
        val sceneName = obj.optString("sceneType").uppercase()
        val scene = WhiteboardGeometryScene.entries.firstOrNull { it.name == sceneName }
            ?: detectSceneFromPrompt(fallbackPrompt)

        val stepsArr = obj.optJSONArray("steps") ?: return null
        val steps = mutableListOf<WhiteboardStep>()
        for (i in 0 until stepsArr.length()) {
            val sObj = stepsArr.optJSONObject(i) ?: continue
            steps += WhiteboardStep(
                index = i,
                rightGivenText = sObj.optString("rightGivenText"),
                leftOperationText = sObj.optString("leftOperationText"),
                centerFormulaText = sObj.optString("centerFormulaText"),
                speechLine = sObj.optString("speechLine").ifBlank {
                    sObj.optString("centerFormulaText").ifBlank { sObj.optString("rightGivenText") }
                },
                isFinalResult = sObj.optBoolean("isFinalResult", i == stepsArr.length() - 1)
            )
        }
        if (steps.isEmpty()) return null

        val finalSummary = obj.optString("finalSummaryText")
            .ifBlank { steps.lastOrNull()?.centerFormulaText.orEmpty() }

        FahimWhiteboardLesson(
            title = title,
            sceneType = scene,
            dimensionLabel = obj.optString("dimensionLabel", "d = 4"),
            radiusLabel = obj.optString("radiusLabel", "r = 2"),
            secondaryLabel = obj.optString("secondaryLabel", ""),
            steps = steps,
            finalSummaryText = finalSummary
        )
    }.getOrNull()

    fun buildLocalWhiteboardLesson(
        prompt: String,
        dialect: FahimDialect
    ): FahimWhiteboardLesson {
        val scene = detectSceneFromPrompt(prompt)
        return when (scene) {
            WhiteboardGeometryScene.SHADED_CIRCLES_RECT -> buildShadedAreaLesson(dialect)
            WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS -> buildPythagorasLesson(dialect)
            WhiteboardGeometryScene.CIRCLE_PROPERTIES -> buildCircleLesson(dialect)
            WhiteboardGeometryScene.PHYSICS_FORCE_MOTION -> buildPhysicsLesson(dialect, prompt)
            WhiteboardGeometryScene.COORDINATE_GRAPH -> buildGraphLesson(dialect, prompt)
        }
    }

    private fun detectSceneFromPrompt(prompt: String): WhiteboardGeometryScene {
        val p = prompt.lowercase()
        return when {
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
            else -> WhiteboardGeometryScene.SHADED_CIRCLES_RECT
        }
    }

    private fun buildShadedAreaLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val speech1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "بص يا بطل! الأول بنرسم المستطيل وجواه الدوائر المتماسة، ولاحظ إن القطر d بيساوي 4 يعني نصف القطر r بيساوي 2."
            FahimDialect.FUSHA -> "أهلاً بك! نرسم أولاً المستطيل وبداخله الدوائر المتماسة، نلاحظ أن القطر يساوي ٤، إذن نصف القطر r يساوي ٢."
            FahimDialect.SAUDI -> "حياك الله يا بطل القدرات! هنا عندنا مستطيل وبداخله الدوائر متماسة، القطر d يساوي ٤ يعني نصف القطر r يساوي ٢."
        }
        val speech2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "تاني خطوة: نحسب مساحة المستطيل الكبير: الطول في العرض، يعني 4 في 4 بيساوي 16."
            FahimDialect.FUSHA -> "الخطوة الثانية: نحسب مساحة المستطيل، وتساوي الطول ضرب العرض، أي ٤ ضرب ٤ وتساوي ١٦."
            FahimDialect.SAUDI -> "الخطوة الثانية: نحسب مساحة المستطيل كامل وهي الطول ضرب العرض، ٤ ضرب ٤ يساوي ١٦."
        }
        val speech3 = when (dialect) {
            FahimDialect.EGYPTIAN -> "تالت خطوة: نحسب مساحة الدائرة الواحدة: ط نق تربيع، يعني 4 × π."
            FahimDialect.FUSHA -> "الخطوة الثالثة: نحسب مساحة الدائرة الواحدة بالقانون π × r تربيع، فتساوي ٤ ضرب π."
            FahimDialect.SAUDI -> "الحين نطلع مساحة الدائرة الواحدة: ط نق تربيع، وبما إن نق بـ ٢ فالمساحة ٤ × π."
        }
        val speech4 = when (dialect) {
            FahimDialect.EGYPTIAN -> "والنتيجة النهائية للجزء المظلل يا بطل: بنطرح مساحة الدائرة من مساحة المستطيل: 16 ناقص 4π، وتطلع تقريباً 3.44!"
            FahimDialect.FUSHA -> "وأخيراً، لإيجاد مساحة الجزء المظلل نطرح مساحة الدائرة من مساحة المستطيل فتكون النتيجة النهائية تقريباً ٣٫٤٤."
            FahimDialect.SAUDI -> "وبالتالي مساحة الجزء المظلل هي الفرق بين المستطيل والدائرة: ١٦ ناقص ٤π، والناتج النهائي ٣.٤٤ تقريباً!"
        }

        return FahimWhiteboardLesson(
            title = "مساحة الجزء المظلل",
            sceneType = WhiteboardGeometryScene.SHADED_CIRCLES_RECT,
            dimensionLabel = "d = 4",
            radiusLabel = "r = 2",
            secondaryLabel = "4 × 4",
            steps = listOf(
                WhiteboardStep(
                    index = 0,
                    rightGivenText = "القطر d = 4 ← نصف القطر r = 2",
                    leftOperationText = "المحيط = 4 × 4",
                    centerFormulaText = "تحديد الأبعاد الهندسية للشكل",
                    speechLine = speech1
                ),
                WhiteboardStep(
                    index = 1,
                    rightGivenText = "أبعاد المستطيل: 4 × 4",
                    leftOperationText = "π × r² = 4π",
                    centerFormulaText = "مساحة المستطيل : Area_rect = 4 × 4 = 16",
                    speechLine = speech2
                ),
                WhiteboardStep(
                    index = 2,
                    rightGivenText = "مساحة الدائرة: π × 2²",
                    leftOperationText = "Area_circle = 4 × π",
                    centerFormulaText = "مساحة الدائرة = π × r² ≈ 12.56",
                    speechLine = speech3
                ),
                WhiteboardStep(
                    index = 3,
                    rightGivenText = "مساحة الجزء المظلل",
                    leftOperationText = "16 - 12.56",
                    centerFormulaText = "Area_shaded = 16 - (4 × π) ≈ 3.44",
                    speechLine = speech4,
                    isFinalResult = true
                )
            ),
            finalSummaryText = "Area_shaded = 16 - (4 × π) ≈ 3.44"
        )
    }

    private fun buildPythagorasLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "في المثلث القائم ده، الضلع الأول 3 والضلع التاني 4 وعايزين نجيب طول الوتر c."
            FahimDialect.FUSHA -> "في هذا المثلث القائم الزاوية، طولا ضلعي القائمة هما ٣ و ٤، والمطلوب إيجاد طول الوتر c."
            FahimDialect.SAUDI -> "مثلث قائم مشهور يا بطل! ضلعي القائمة ٣ و ٤ والمطلوب طول الوتر c."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "نطبق نظرية فيثاغورس: مربع الوتر بيساوي مجموع مربعي الضلعين، يعني 9 + 16 = 25."
            FahimDialect.FUSHA -> "نطبق مبرهنة فيثاغورس: c² = a² + b²، أي ٩ + ١٦ وتساوي ٢٥."
            FahimDialect.SAUDI -> "على طول نستخدم فيثاغورس: نربع الضلعين ٣ تربيع ٩ و ٤ تربيع ١٦ ومجموعهم ٢٥."
        }
        val s3 = when (dialect) {
            FahimDialect.EGYPTIAN -> "ناخد الجذر التربيعي للطرفين: جذر 25 يساوي 5، وده الوتر المطلوب!"
            FahimDialect.FUSHA -> "بأخذ الجذر التربيعي للطرفين نحصل على c = √25 = 5."
            FahimDialect.SAUDI -> "بأخذ الجذر التربيعي لـ ٢٥ يطلع طول الوتر يساوي ٥ مباشرة!"
        }

        return FahimWhiteboardLesson(
            title = "نظرية فيثاغورس والمثلث القائم",
            sceneType = WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS,
            dimensionLabel = "c = 5",
            radiusLabel = "b = 4",
            secondaryLabel = "a = 3",
            steps = listOf(
                WhiteboardStep(0, "a = 3  ,  b = 4", "∠ = 90°", "المثلث قائم الزاوية", s1),
                WhiteboardStep(1, "c² = a² + b²", "3² + 4² = 9 + 16", "c² = 25", s2),
                WhiteboardStep(2, "أخذ الجذر التربيعي", "c = √25", "c = 5 سم (طول الوتر)", s3, isFinalResult = true)
            ),
            finalSummaryText = "c = √(3² + 4²) = 5"
        )
    }

    private fun buildCircleLesson(dialect: FahimDialect): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "عشان نحسب محيط ومساحة الدائرة، أهم حاجة نحدد نصف القطر r = 7 سم."
            FahimDialect.FUSHA -> "لحساب محيط الدائرة ومساحتها، نحدد أولاً نصف القطر r = ٧ سم."
            FahimDialect.SAUDI -> "في مسائل الدائرة، أول خطوة نثبت نصف القطر r = ٧ سم."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "المحيط بيساوي 2 ط نق، يعني 2 × (22/7) × 7 = 44 سم، والمساحة بتساوي ط نق تربيع = 154 سم مربع."
            FahimDialect.FUSHA -> "المحيط يساوي ٢ × π × r ويساوي ٤٤ سم، والمساحة π × r² وتساوي ١٥٤ سم مربع."
            FahimDialect.SAUDI -> "المحيط ٢ ط نق يطلع ٤٤، والمساحة ط نق تربيع تساوي ١٥٤ سم مربع."
        }
        return FahimWhiteboardLesson(
            title = "قوانين مساحة ومحيط الدائرة",
            sceneType = WhiteboardGeometryScene.CIRCLE_PROPERTIES,
            dimensionLabel = "d = 14",
            radiusLabel = "r = 7",
            steps = listOf(
                WhiteboardStep(0, "r = 7 سم", "π ≈ 22 / 7", "القطر الكامل d = 2r = 14", s1),
                WhiteboardStep(1, "C = 2 × π × r", "2 × (22/7) × 7", "المحيط = 44 سم", s2),
                WhiteboardStep(2, "A = π × r²", "(22/7) × 49", "Area = 154 سم²", s2, isFinalResult = true)
            ),
            finalSummaryText = "Area = π × r² = 154 سم²"
        )
    }

    private fun buildPhysicsLesson(dialect: FahimDialect, prompt: String): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "في الفيزياء، قانون نيوتن التاني بيقول إن القوة F بتساوي الكتلة m مضروبة في العجلة a."
            FahimDialect.FUSHA -> "ينص قانون نيوتن الثاني على أن القوة المحصلة تساوي حاصل ضرب الكتلة في التسارع."
            FahimDialect.SAUDI -> "قانون نيوتن الثاني من أهم قوانين التحصيلي: القوة F تساوي الكتلة في التسارع."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "لو الكتلة 5 كجم والتسارع 4 متر على الثانية تربيع، يبقى القوة بتساوي 5 × 4 = 20 نيوتن!"
            FahimDialect.FUSHA -> "بتعويض m = ٥ كجم و a = ٤ م/ث²، تكون القوة الناتجة ٢٠ نيوتن."
            FahimDialect.SAUDI -> "نعوض مباشرة: ٥ ضرب ٤ يساوي ٢٠ نيوتن."
        }
        return FahimWhiteboardLesson(
            title = "تحليل القوى وقانون نيوتن الثاني",
            sceneType = WhiteboardGeometryScene.PHYSICS_FORCE_MOTION,
            dimensionLabel = "F = 20 N",
            radiusLabel = "m = 5 kg",
            secondaryLabel = "a = 4 m/s²",
            steps = listOf(
                WhiteboardStep(0, "m = 5 kg", "a = 4 m/s²", "القانون: F = m × a", s1),
                WhiteboardStep(1, "توحيد الوحدات", "5 × 4 = 20", "F = 20 Newton (نيوتن)", s2, isFinalResult = true)
            ),
            finalSummaryText = "F = m × a = 20 N"
        )
    }

    private fun buildGraphLesson(dialect: FahimDialect, prompt: String): FahimWhiteboardLesson {
        val s1 = when (dialect) {
            FahimDialect.EGYPTIAN -> "هنا بنمثل الدالة التربيعية y = x² على الشبكة الإحداثية، ورأس المنحنى عند نقطة الأصل (0, 0)."
            FahimDialect.FUSHA -> "نمثل الدالة التربيعية y = x² بيانياً حيث يكون رأس القطع المكافئ عند نقطة الأصل."
            FahimDialect.SAUDI -> "في التمثيل البياني للدالة y = x²، الرأس يكون عند (0, 0) والمنحنى مفتوح للأعلى."
        }
        val s2 = when (dialect) {
            FahimDialect.EGYPTIAN -> "لما x = 2 بتكون y = 4، والمنحنى متماثل حوالين محور الصادات y."
            FahimDialect.FUSHA -> "عند x = ٢ فإن y = ٤، والمنحنى متماثل تماماً حول محور الصادات."
            FahimDialect.SAUDI -> "عند التعويض بـ ٢ تطلع y = ٤، وهو متماثل حول محور الصادات."
        }
        return FahimWhiteboardLesson(
            title = "التمثيل البياني للدالة التربيعية",
            sceneType = WhiteboardGeometryScene.COORDINATE_GRAPH,
            dimensionLabel = "(2, 4)",
            radiusLabel = "(0, 0)",
            steps = listOf(
                WhiteboardStep(0, "رأس المنحنى (0, 0)", "y = x²", "محور التماثل x = 0", s1),
                WhiteboardStep(1, "عند x = ±2", "y = (±2)² = 4", "نقاط المنحنى: (2, 4) و (-2, 4)", s2, isFinalResult = true)
            ),
            finalSummaryText = "f(x) = x²  |  f(2) = 4"
        )
    }
}
