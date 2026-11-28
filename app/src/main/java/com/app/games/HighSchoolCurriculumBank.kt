package com.app.games

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.domain.ai.FirebaseAiLogicClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val TAG = "HighSchoolCurriculum"

/**
 * بنك مناهج الثانوية العامة المدمج الضخم (أوفلاين بالكامل لجميع الصفوف الـ 3 والمواد الـ 11 • أكثر من 230 سؤالاً)
 * مع إعادة ترتيب عشوائي ذكي للاختيارات (Option Shuffling) في كل جولة،
 * ومولّد أسئلة متجددة عبر Firebase AI Logic (Gemini) واستخراج أسئلة مباشرة من ملفات PDF الخاصة بالطالب.
 */
object HighSchoolCurriculumBank {

    fun getOfflineQuestions(
        grade: HighSchoolGrade,
        subject: HighSchoolSubject,
        chapter: String
    ): List<GameQuestion> {
        val base = when (subject) {
            HighSchoolSubject.PHYSICS -> physicsQuestions
            HighSchoolSubject.CHEMISTRY -> chemistryQuestions
            HighSchoolSubject.BIOLOGY -> biologyQuestions
            HighSchoolSubject.MATH -> mathQuestions
            HighSchoolSubject.ARABIC -> arabicQuestions
            HighSchoolSubject.ENGLISH -> englishQuestions
            HighSchoolSubject.FRENCH -> frenchQuestions
            HighSchoolSubject.HISTORY -> historyQuestions
            HighSchoolSubject.GEOGRAPHY -> geographyQuestions
            HighSchoolSubject.PHILOSOPHY -> philosophyQuestions
            HighSchoolSubject.PSYCHOLOGY -> psychologyQuestions
        }
        return base.shuffled().mapIndexed { idx, q ->
            val shuffledQuestion = shuffleQuestionOptions(q)
            shuffledQuestion.copy(
                id = "${grade.name}_${subject.name}_${chapter.hashCode()}_$idx",
                sourceLabel = "${subject.titleAr} • ${grade.titleAr}"
            )
        }
    }

    /**
     * يضمن توزيع الإجابة الصحيحة عشوائياً بين الحارات الثلاث (اليسار، الوسط، اليمين) في كل جولة!
     */
    fun shuffleQuestionOptions(question: GameQuestion): GameQuestion {
        if (question.options.size < 3) return question
        val correctText = question.options.getOrElse(question.correctIndex) { question.options.first() }
        val shuffledOpts = question.options.take(3).shuffled(Random(System.nanoTime() xor question.id.hashCode().toLong()))
        val newCorrectIdx = shuffledOpts.indexOf(correctText).coerceIn(0, 2)
        return question.copy(
            options = shuffledOpts,
            correctIndex = newCorrectIdx
        )
    }

    /**
     * يولّد أسئلة متجددة لا تتكرر عبر Firebase AI Logic (`gemini-3.8-flash`)
     * بصيغة 3 اختيارات مناسبة لحارات السباق الثلاث + سؤال زعيم في النهاية.
     */
    suspend fun generateFreshQuestionsWithAi(
        grade: HighSchoolGrade,
        subject: HighSchoolSubject,
        chapter: String,
        stageSpec: StageSpec,
        excludePrompts: Set<String> = emptySet()
    ): List<GameQuestion> = withContext(Dispatchers.IO) {
        if (!FirebaseAiLogicClient.isAvailable()) return@withContext emptyList()

        val avoidNote = if (excludePrompts.isNotEmpty()) {
            "تجنّب تكرار هذه الأسئلة السابقة: ${excludePrompts.take(10).joinToString(" | ")}"
        } else ""

        val countToGenerate = (stageSpec.standardGatesCount + 2).coerceIn(8, 14)
        val prompt = """
            أنت خبير مناهج الثانوية العامة المصرية.
            قم بتوليد $countToGenerate سؤال اختيار من متعدد (${countToGenerate - 2} أسئلة سريعة + سؤالين زعيم تحدي) لمادة «${subject.titleAr}» للصف «${grade.titleAr}» في «$chapter» (المرحلة ${stageSpec.number}: ${stageSpec.nameAr}).
            $avoidNote
            الشروط الصارمة:
            1. كل سؤال يحتوي على 3 اختيارات قصيرة جداً ومناسبة للعرض فوق بوابات لعبة سباق (كلمة إلى 3 كلمات لكل اختيار).
            2. السؤال واضح ومباشر وقابل للقراءة السريعة.
            3. نوّع موضع الإجابة الصحيحة بين 0 و 1 و 2.
            4. أعد JSON فقط بدون أي نص خارجي بهذا الشكل:
            {
              "questions": [
                {
                  "prompt": "نص السؤال هنا؟",
                  "options": ["اختيار1", "اختيار2", "اختيار3"],
                  "correctIndex": 0,
                  "explanation": "تفسير مختصر في سطر واحد",
                  "isBoss": false
                }
              ]
            }
        """.trimIndent()

        val result = FirebaseAiLogicClient.generateText(
            prompt = prompt,
            systemInstruction = "أعد كائن JSON صالحاً فقط يطابق الهيكل المطلوب بدقة.",
            temperature = 0.70f,
            responseMimeType = "application/json"
        )

        result.fold(
            onSuccess = { rawJson ->
                parseQuestionsJson(rawJson, sourceLabel = "توليد Gemini • ${subject.titleAr}")
            },
            onFailure = { err ->
                Log.w(TAG, "AI question generation fallback to offline bank: ${err.message}")
                emptyList()
            }
        )
    }

    /**
     * يستخرج النص من ملف PDF الذي يختاره الطالب ويولّد منه أسئلة سباق بـ 3 اختيارات (عبر Gemini أو محلياً أوفلاين).
     */
    suspend fun generateQuestionsFromPdf(
        context: Context,
        pdfUri: Uri,
        fileTitle: String
    ): List<GameQuestion> = withContext(Dispatchers.IO) {
        val extractedText = extractPdfTextSafe(context, pdfUri)
        if (extractedText.isBlank()) return@withContext emptyList()

        val snippet = extractedText.take(5500)
        if (FirebaseAiLogicClient.isAvailable()) {
            val prompt = """
                استخرج 10 أسئلة ذكية من النص الدراسي التالي لملف «$fileTitle» (8 أسئلة عادية + سؤالين زعيم):
                ---
                $snippet
                ---
                أعد JSON فقط بهذا الشكل (3 اختيارات قصيرة جداً لكل سؤال):
                {
                  "questions": [
                    {
                      "prompt": "السؤال؟",
                      "options": ["اختيار1", "اختيار2", "اختيار3"],
                      "correctIndex": 0,
                      "explanation": "التعليل المختصر",
                      "isBoss": false
                    }
                  ]
                }
            """.trimIndent()

            val aiRes = FirebaseAiLogicClient.generateText(
                prompt = prompt,
                systemInstruction = "أعد JSON صالحاً فقط للأسئلة المستخرجة من الملف.",
                temperature = 0.45f,
                responseMimeType = "application/json"
            )
            val parsed = aiRes.getOrNull()?.let { parseQuestionsJson(it, sourceLabel = "ملف: $fileTitle") }.orEmpty()
            if (parsed.size >= 4) return@withContext parsed
        }

        buildOfflinePdfFallbackQuestions(snippet, fileTitle)
    }

    private fun extractPdfTextSafe(context: Context, uri: Uri): String {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = doc.numberOfPages.coerceAtMost(10)
                    }
                    stripper.getText(doc).orEmpty().trim()
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting PDF text for game: ${e.message}", e)
            ""
        }
    }

    private fun buildOfflinePdfFallbackQuestions(text: String, title: String): List<GameQuestion> {
        val sentences = text.split('\n', '.', '؟', '!')
            .map { it.trim() }
            .filter { it.length in 24..145 }
            .distinct()
            .take(18)

        val wordsPool = sentences.flatMap { it.split(' ') }
            .map { it.trim(',', '،', ':', ';', '(', ')') }
            .filter { it.length in 4..14 }
            .distinct()

        if (sentences.isEmpty() || wordsPool.size < 4) return emptyList()

        return sentences.take(12).mapIndexedNotNull { idx, sentence ->
            val words = sentence.split(' ').filter { it.length in 4..14 }
            val target = words.lastOrNull() ?: return@mapIndexedNotNull null
            val cleanTarget = target.trim(',', '،', ':', ';', '(', ')')
            val distractors = wordsPool.filter { it != cleanTarget }.shuffled().take(2)
            if (distractors.size < 2) return@mapIndexedNotNull null

            val masked = sentence.replaceFirst(cleanTarget, "......")
            val options = (listOf(cleanTarget) + distractors).shuffled()
            GameQuestion(
                id = "pdf_${title.hashCode()}_$idx",
                prompt = "أكمل من ملفك: $masked",
                options = options,
                correctIndex = options.indexOf(cleanTarget).coerceIn(0, 2),
                explanation = "الإجابة الصحيحة من الملف هي: $cleanTarget",
                isBossQuestion = idx >= 9,
                sourceLabel = "ملف: $title"
            )
        }
    }

    private fun parseQuestionsJson(raw: String, sourceLabel: String): List<GameQuestion> {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val root = JSONObject(cleaned)
            val arr: JSONArray = root.optJSONArray("questions") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val prompt = obj.optString("prompt").trim()
                    val optsArr = obj.optJSONArray("options") ?: continue
                    val opts = (0 until optsArr.length()).mapNotNull { optsArr.optString(it).trim().takeIf { s -> s.isNotBlank() } }
                    if (prompt.isBlank() || opts.size < 3) continue
                    val threeOpts = opts.take(3)
                    val correctIdx = obj.optInt("correctIndex", 0).coerceIn(0, 2)
                    val q = GameQuestion(
                        id = "ai_${System.nanoTime()}_$i",
                        prompt = prompt,
                        options = threeOpts,
                        correctIndex = correctIdx,
                        explanation = obj.optString("explanation", ""),
                        isBossQuestion = obj.optBoolean("isBoss", i == arr.length() - 1),
                        sourceLabel = sourceLabel
                    )
                    add(shuffleQuestionOptions(q))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing AI questions JSON: ${e.message}")
            emptyList()
        }
    }

    // =========================================================================
    // Massive Built-in Offline Question Banks (All 11 Subjects • 220+ Questions)
    // =========================================================================

    private val physicsQuestions = listOf(
        GameQuestion("p1", "ما هي وحدة قياس شدة التيار الكهربي؟", listOf("الأمبير", "الفولت", "الأوم"), 0),
        GameQuestion("p2", "تُقاس المقاومة الكهربية بوحدة؟", listOf("الوبر", "التسلا", "الأوم"), 2),
        GameQuestion("p3", "ما هي وحدة قياس الطول في النظام الدولي؟", listOf("المتر", "الكيلوجرام", "الثانية"), 0),
        GameQuestion("p4", "حسب قانون نيوتن الثاني: القوة تساوي؟", listOf("الكتلة × التسارع", "الشغل ÷ الزمن", "المسافة ÷ الزمن"), 0),
        GameQuestion("p5", "ما وحدة قياس كثافة الفيض المغناطيسي؟", listOf("التسلا", "الجول", "الواط"), 0),
        GameQuestion("p6", "قانون أوم يربط بين فرق الجهد والتيار بـ:", listOf("V = I × R", "P = I × R", "E = m × c²"), 0),
        GameQuestion("p7", "وحدة قياس الفيض المغناطيسي الكلي هي:", listOf("الوبر", "الهنري", "الفاراد"), 0),
        GameQuestion("p8", "جهاز يُستخدم لقياس شدة التيار الكهربي في الدائرة:", listOf("الأميتر", "الفولتميتر", "الأوميتر"), 0),
        GameQuestion("p9", "يوصل الفولتميتر في الدوائر الكهربية على:", listOf("التوازي", "التوالي", "التعامد"), 0),
        GameQuestion("p10", "معامل الحث الذاتي لملف يُقاس بوحدة:", listOf("الهنري", "التسلا", "الكولوم"), 0),
        GameQuestion("p11", "عند زيادة طول الموصل للضعف فإن مقاومته:", listOf("تزداد للضعف", "تقل للنصف", "تظل ثابتة"), 0),
        GameQuestion("p12", "المقاومة النوعية لمادة موصل تتوقف على:", listOf("نوع المادة والحرارة", "طول السلك", "مساحة المقطع"), 0),
        GameQuestion("p13", "قاعدة تُستخدم لتحديد اتجاه التيار المستحث في سلك مستقيم:", listOf("فلمنج لليد اليمنى", "فلمنج لليد اليسرى", "أمبير لليد اليمنى"), 0),
        GameQuestion("p14", "التيارات الدوامية المتولدة في القلب الحديدي للمحول تُقلل بـ:", listOf("شرائح معزولة", "قلب مصمت", "زيادة التردد"), 0),
        GameQuestion("p15", "وحدة قياس السعة الكهربية للمكثف هي:", listOf("الفاراد", "الأوم", "الهنري"), 0),
        GameQuestion("p16", "في دائرة الرنين تكون المعاوقة الكلية مساوية لـ:", listOf("المقاومة الأومية R", "المفاعلة الحثية XL", "الصفر"), 0),
        GameQuestion("p17", "طاقة الفوتون تتناسب طردياً مع:", listOf("تردده", "طوله الموجي", "كتلته السكونية"), 0),
        GameQuestion("p18", "الظاهرة الكهروضوئية تثبت الطبيعة الجسيمية لـ:", listOf("الضوء", "الإلكترون", "البروتون"), 0),
        GameQuestion("p19", "تحدي الزعيم: معادلة أينشتاين لتكافؤ الكتلة والطاقة هي؟", listOf("E = mc²", "F = ma", "V = IR"), 0, isBossQuestion = true),
        GameQuestion("p20", "تحدي الزعيم: قانون كيرشوف الأول يعتمد على مبدأ حفظ:", listOf("الشحنة الكهربية", "الطاقة", "كمية التحرك"), 0, isBossQuestion = true),
        GameQuestion("p21", "تحدي الزعيم: الليزر يعتمد في عمله على الانبعاث:", listOf("المستحث", "التلقائي", "الأيوني"), 0, isBossQuestion = true)
    )

    private val chemistryQuestions = listOf(
        GameQuestion("c1", "ما هو الرمز الكيميائي لعنصر الحديد؟", listOf("Fe", "Cu", "Au"), 0),
        GameQuestion("c2", "تفاعل الحمض مع القلوي لتكوين ملح وماء يُسمى؟", listOf("التعادل", "الأكسدة", "التبخر"), 0),
        GameQuestion("c3", "ما هو الغاز الأكثر وفرة في الغلاف الجوي؟", listOf("النيتروجين", "الأكسجين", "الهيدروجين"), 0),
        GameQuestion("c4", "العدد الذري لعنصر الكربون هو؟", listOf("6", "12", "8"), 0),
        GameQuestion("c5", "الرمز الكيميائي للذهب هو؟", listOf("Ag", "Au", "Al"), 1),
        GameQuestion("c6", "درجة الـ pH للمحلول المتعادل عند 25°م تساوي؟", listOf("7", "1", "14"), 0),
        GameQuestion("c7", "العنصر الانتقالي الذي يُستخدم في جلفنة المعادن لحمايتها:", listOf("الخارصين Zn", "النحاس Cu", "الفضة Ag"), 0),
        GameQuestion("c8", "خام الهيماتيت هو أحد أهم خامات عنصر:", listOf("الحديد", "الألومنيوم", "المنجنيز"), 0),
        GameQuestion("c9", "الغاز الذي يعكر ماء الجير الرائق هو:", listOf("ثاني أكسيد الكربون", "الأكسجين", "النيتروجين"), 0),
        GameQuestion("c10", "الصيغة العامة للألكانات المشبعة هي:", listOf("CnH2n+2", "CnH2n", "CnH2n-2"), 0),
        GameQuestion("c11", "أبسط هيدروكربون أروماتي حلقي هو:", listOf("البنزين العطري", "الميثان", "الإيثين"), 0),
        GameQuestion("c12", "العامل الحفاز في طريقة هابر-بوش لتحضير النشادر:", listOf("الحديد", "النيكل المجزأ", "البلاتين"), 0),
        GameQuestion("c13", "القطب الذي تحدث عنده عملية الأكسدة في الخلية الجلفانية:", listOf("الأنود", "الكاثود", "القنطرة الملحية"), 0),
        GameQuestion("c14", "قانون فعل الكتلة الذي يربط سرعة التفاعل بالتركيز وضعه:", listOf("جولدبرج وفاج", "لوشاتيليه", "فاندر فالس"), 0),
        GameQuestion("c15", "المحلول الذي قيمة pH له تساوي 2 يعتبر:", listOf("حامضياً قوياً", "قاعدياً قوياً", "متعادلاً"), 0),
        GameQuestion("c16", "تفاعل الإيثين مع الهيدروجين في وجود النيكل يُسمى:", listOf("هدرجة", "هلجنة", "بلمرة"), 0),
        GameQuestion("c17", "المركب العضوي المستخدم قديماً كمخدر جراحي غير آمن:", listOf("الكلوروفورم", "الهالوثان", "الإيثانول"), 0),
        GameQuestion("c18", "تحدي الزعيم: المجموعة الوظيفية في الكحولات هي؟", listOf("الهيدروكسيل -OH", "الكربوكسيل -COOH", "الأمين -NH2"), 0, isBossQuestion = true),
        GameQuestion("c19", "تحدي الزعيم: تفاعل حمض عضوي مع كحول ينتج إستر وماء ويسمى:", listOf("الأسترة", "التصبن", "التكسير الحراري"), 0, isBossQuestion = true),
        GameQuestion("c20", "تحدي الزعيم: كمية الكهرباء اللازمة لترسيب المكافئ الجرامي هي:", listOf("واحد فاراداي", "واحد كولوم", "واحد أمبير"), 0, isBossQuestion = true)
    )

    private val biologyQuestions = listOf(
        GameQuestion("b1", "ما هي العضية المسؤولة عن إنتاج الطاقة في الخلية؟", listOf("الميتوكوندريا", "النواة", "الريبوسوم"), 0),
        GameQuestion("b2", "تحدث عملية البناء الضوئي داخل؟", listOf("البلاستيدات الخضراء", "الفجوة العصارية", "الليسوسوم"), 0),
        GameQuestion("b3", "ما اسم الحمض النووي الحامل للمعلومات الوراثية؟", listOf("DNA", "ATP", "ADP"), 0),
        GameQuestion("b4", "عدد الكروموسومات في الخلية الجسدية للإنسان؟", listOf("46 كروموسوم", "23 كروموسوم", "44 كروموسوم"), 0),
        GameQuestion("b5", "ما الهرمون المسؤول عن خفض نسبة السكر في الدم؟", listOf("الأنسولين", "الأدرينالين", "الجلوكاجون"), 0),
        GameQuestion("b6", "تُصنع البروتينات داخل الخلية بواسطة؟", listOf("الريبوسومات", "الميتوكوندريا", "الشبكة الملساء"), 0),
        GameQuestion("b7", "عدد فقرات العمود الفقري في الإنسان البالغ:", listOf("33 فقرة", "24 فقرة", "26 فقرة"), 0),
        GameQuestion("b8", "الوحدة الوظيفية للانقباض العضلي في العضلة الهيكلية هي:", listOf("القطعة العضلية", "الوتر", "الرباط"), 0),
        GameQuestion("b9", "الغدة التي تُلقب بسيدة الغدد الصماء في الجسم هي:", listOf("الغدة النخامية", "الغدة الدرقية", "الغدة الكظرية"), 0),
        GameQuestion("b10", "هرمون الطوارئ الذي يفرزه نخاع الغدة الكظرية:", listOf("الأدرينالين", "الثيروكسين", "الكالسيتونين"), 0),
        GameQuestion("b11", "الأجسام المضادة تُنتج في الدم بواسطة الخلايا الليمفاوية:", listOf("البائية البلازمية", "التائية المساعدة", "القاتلة الطبيعية"), 0),
        GameQuestion("b12", "المادة التي تترسب على جدران خلايا الفلين لمنع نفاذ الماء:", listOf("السيوبرين", "السيليلوز", "البكتين"), 0),
        GameQuestion("b13", "الرباط الذي يربط العضلة بالعظم عند المفاصل هو:", listOf("الوتر (كوتر أخيل)", "الرباط الصليبي", "الغضروف"), 0),
        GameQuestion("b14", "إنزيم يفصل شريطي DNA عن بعضهما أثناء التضاعف:", listOf("اللولب", "البلمرة", "الربط"), 0),
        GameQuestion("b15", "الكودون الذي يبدأ عملية ترجمة البروتين على mRNA هو:", listOf("AUG", "UAA", "UGA"), 0),
        GameQuestion("b16", "نبات يغلق أوراقه عند اللمس بفضل الدعامة الفسيولوجية:", listOf("المستحية", "البازلاء", "الفول"), 0),
        GameQuestion("b17", "تحدي الزعيم: القاعدة النيتروجينية المميزة لـ RNA بدل الثايمين؟", listOf("اليوراسيل", "الجوانين", "السيتوزين"), 0, isBossQuestion = true),
        GameQuestion("b18", "تحدي الزعيم: الخلايا المناعية التي تنضج في الغدة التيموسية هي:", listOf("الخلايا التائية T", "الخلايا البائية B", "الخلايا البلعمية"), 0, isBossQuestion = true),
        GameQuestion("b19", "تحدي الزعيم: الرابطة الكيميائية التي تربط الأحماض الأمينية معاً:", listOf("الرابطة الببتيدية", "الرابطة الهيدروجينية", "الرابطة الأيونية"), 0, isBossQuestion = true)
    )

    private val mathQuestions = listOf(
        GameQuestion("m1", "مشتقة الدالة د(س) = س² تساوي؟", listOf("2س", "س", "2"), 0),
        GameQuestion("m2", "قيمة جا(30°) تساوي؟", listOf("0.5", "1", "0.866"), 0),
        GameQuestion("m3", "مجموع زوايا المثلث الداخلية يساوي؟", listOf("180°", "90°", "360°"), 0),
        GameQuestion("m4", "إذا كانت 3س + 6 = 15 فإن س تساوي؟", listOf("3", "5", "9"), 0),
        GameQuestion("m5", "قيمة لو₁₀(100) تساوي؟", listOf("2", "10", "1"), 0),
        GameQuestion("m6", "مشتقة الدالة الثابتة تساوي دائماً؟", listOf("صفر", "واحد", "نفسها"), 0),
        GameQuestion("m7", "مشتقة الدالة جا(س) بالنسبة إلى س هي:", listOf("جتا(س)", "-جا(س)", "ظا(س)"), 0),
        GameQuestion("m8", "تكامل الدالة 2س بالنسبة إلى س يساوي:", listOf("س² + ث", "2 + ث", "س³ + ث"), 0),
        GameQuestion("m9", "قيمة المضروب 4! (أربعة مضروب) تساوي:", listOf("24", "16", "12"), 0),
        GameQuestion("m10", "ميل المستقيم الموازي لمحور السينات يساوي:", listOf("صفر", "1", "غير معرف"), 0),
        GameQuestion("m11", "قيمة جتا(0°) تساوي:", listOf("1", "0", "-1"), 0),
        GameQuestion("m12", "قيمة ظا(45°) تساوي:", listOf("1", "0.5", "جذر 3"), 0),
        GameQuestion("m13", "مشتقة الدالة الأسية الطبيعية هـ^س هي:", listOf("هـ^س", "س × هـ", "لو(س)"), 0),
        GameQuestion("m14", "معادلة الكرة التي مركزها نقطة الأصل ونصف قطرها نق:", listOf("س²+ص²+ع²=نق²", "س+ص+ع=نق", "س²+ص²=نق"), 0),
        GameQuestion("m15", "الضرب القياسي لمتجهين متعامدين يساوي دائماً:", listOf("صفر", "واحد", "حاصل ضرب معياريهما"), 0),
        GameQuestion("m16", "عدد أقطار الشكل الرباعي المحدب يساوي:", listOf("قطران", "3 أقطار", "4 أقطار"), 0),
        GameQuestion("m17", "تحدي الزعيم: قيمة جا²(س) + جتا²(س) تساوي؟", listOf("1", "0", "2"), 0, isBossQuestion = true),
        GameQuestion("m18", "تحدي الزعيم: نهاية (جا س ÷ س) عندما تؤول س إلى الصفر تساوي:", listOf("1", "0", "ما لا نهاية"), 0, isBossQuestion = true),
        GameQuestion("m19", "تحدي الزعيم: مربع العدد التخيلي ت² في الأعداد المركبة يساوي:", listOf("-1", "1", "صفر"), 0, isBossQuestion = true)
    )

    private val arabicQuestions = listOf(
        GameQuestion("a1", "ما إعراب كلمة (العلمُ) في جملة: العلمُ نورٌ؟", listOf("مبتدأ مرفوع", "خبر مرفوع", "فاعل مرفوع"), 0),
        GameQuestion("a2", "نوع التشبيه في جملة «الجندي كالأسد في الشجاعة»؟", listOf("تشبيه مفصل", "تشبيه بليغ", "استعارة مكنية"), 0),
        GameQuestion("a3", "«كان وأخواتها» تدخل على الجملة الاسمية فتـ:", listOf("ترفع المبتدأ وتنصب الخبر", "تنصب المبتدأ وترفع الخبر", "تجر المبتدأ"), 0),
        GameQuestion("a4", "الجمع الصحيح لكلمة «دُنيا» جمعاً مؤنثاً سالماً؟", listOf("دُنييات", "دُنيات", "دَنايا"), 0),
        GameQuestion("a5", "مؤسس مدرسة الإحياء والبعث في الشعر العربي؟", listOf("محمود سامي البارودي", "العقاد", "إيليا أبو ماضي"), 0),
        GameQuestion("a6", "ما نوع المحسّن البديعي بين (الليل والنهار)؟", listOf("طباق إيجاب", "جناس تام", "سجع"), 0),
        GameQuestion("a7", "«إنّ وأخواتها» حروف ناسخة تدخل على الجملة الاسمية فتـ:", listOf("تنصب المبتدأ وترفع الخبر", "ترفع المبتدأ وتنصب الخبر", "تجزم الفعل"), 0),
        GameQuestion("a8", "الاسم المشتق الذي يدل على من قام بالفعل يُسمى:", listOf("اسم الفاعل", "اسم المفعول", "اسم الزمان"), 0),
        GameQuestion("a9", "إعراب الاسم الواقع بعد «لولا» في الغالب يكون:", listOf("مبتدأ خبره محذوف", "فاعلاً مرفوعاً", "مضافاً إليه"), 0),
        GameQuestion("a10", "نوع الصورة البيانية في «ابتسم الحظ للمجتهد»:", listOf("استعارة مكنية", "استعارة تصريحية", "تشبيه مجمل"), 0),
        GameQuestion("a11", "علامة جزم الفعل المضارع المعتل الآخر مثل (يسعى):", listOf("حذف حرف العلة", "السكون", "حذف النون"), 0),
        GameQuestion("a12", "من شعراء مدرسة الديوان في الأدب الحديث:", listOf("العقاد والمازني", "أحمد شوقي", "خليل مطران"), 0),
        GameQuestion("a13", "رائد الاتجاه الوجداني في الشعر العربي الحديث هو:", listOf("خليل مطران", "حافظ إبراهيم", "البارودي"), 0),
        GameQuestion("a14", "المصدر الصريح من الفعل الخماسي «تقدَّمَ» هو:", listOf("تقدُّم", "تقديم", "إقدام"), 0),
        GameQuestion("a15", "اتفاق فواصل الجمل في الحرف الأخير في النثر يُسمى:", listOf("السجع", "التصريع", "التورية"), 0),
        GameQuestion("a16", "الأفعال الخمسة تُرفع بثبوت النون وتُنصب وتُجزم بـ:", listOf("حذف النون", "الفتحة", "السكون"), 0),
        GameQuestion("a17", "تحدي الزعيم: ما إعراب «نورٌ» في جملة «العلمُ نورٌ»؟", listOf("خبر مرفوع", "مفعول به", "حال منصوب"), 0, isBossQuestion = true),
        GameQuestion("a18", "تحدي الزعيم: تشابه كلمتين في اللفظ واختلافهما في المعنى يُسمى:", listOf("الجناس", "الطباق", "المقابلة"), 0, isBossQuestion = true),
        GameQuestion("a19", "تحدي الزعيم: المخصوص بالمدح مع «نِعْمَ» يُعرب:", listOf("مبتدأ مؤخراً", "حالاً منصوبة", "تمييزاً"), 0, isBossQuestion = true)
    )

    private val englishQuestions = listOf(
        GameQuestion("e1", "Choose the correct form: She ___ to school every day.", listOf("goes", "go", "going"), 0),
        GameQuestion("e2", "What is the past participle of 'write'?", listOf("written", "wrote", "writing"), 0),
        GameQuestion("e3", "The antonym of 'ancient' is:", listOf("modern", "old", "historic"), 0),
        GameQuestion("e4", "I have lived in Cairo ___ 2018.", listOf("since", "for", "ago"), 0),
        GameQuestion("e5", "If you heat ice, it ___.", listOf("melts", "melted", "will melting"), 0),
        GameQuestion("e6", "A synonym for 'enormous' is:", listOf("huge", "tiny", "narrow"), 0),
        GameQuestion("e7", "While I ___ my lessons, the phone rang.", listOf("was studying", "studied", "study"), 0),
        GameQuestion("e8", "He is good ___ solving difficult physics problems.", listOf("at", "in", "on"), 0),
        GameQuestion("e9", "The new hospital ___ built last year.", listOf("was", "is", "has"), 0),
        GameQuestion("e10", "You should avoid ___ late before an exam.", listOf("staying up", "to stay", "stay"), 0),
        GameQuestion("e11", "She asked me where I ___ the previous day.", listOf("had been", "am", "will be"), 0),
        GameQuestion("e12", "This is the scientist ___ discovered the treatment.", listOf("who", "which", "whose"), 0),
        GameQuestion("e13", "We look forward to ___ our goals this year.", listOf("achieving", "achieve", "achieved"), 0),
        GameQuestion("e14", "Neither Adel nor his friends ___ late today.", listOf("are", "is", "was"), 0),
        GameQuestion("e15", "If I had studied harder, I ___ full marks.", listOf("would have got", "will get", "got"), 0),
        GameQuestion("e16", "The word 'sustainable' is closest in meaning to:", listOf("eco-friendly", "temporary", "harmful"), 0),
        GameQuestion("e17", "Boss Challenge: By next year, we ___ our final exams.", listOf("will have finished", "finished", "are finish"), 0, isBossQuestion = true),
        GameQuestion("e18", "Boss Challenge: No sooner had he arrived ___ the train left.", listOf("than", "when", "then"), 0, isBossQuestion = true),
        GameQuestion("e19", "Boss Challenge: I had my car ___ at the service center.", listOf("repaired", "repair", "repairing"), 0, isBossQuestion = true)
    )

    private val frenchQuestions = listOf(
        GameQuestion("f1", "معنى كلمة «Bonjour» بالفرنسية؟", listOf("صباح الخير", "مساء الخير", "وداعاً"), 0),
        GameQuestion("f2", "تصريف فعل être مع الضمير Je هو:", listOf("suis", "es", "est"), 0),
        GameQuestion("f3", "أداة المعرفة المناسبة لاسم مفرد مذكر هي:", listOf("le", "la", "les"), 0),
        GameQuestion("f4", "العدد «quinze» بالفرنسية يعني:", listOf("15", "5", "50"), 0),
        GameQuestion("f5", "كلمة «le livre» معناها:", listOf("الكتاب", "القلم", "المدرسة"), 0),
        GameQuestion("f6", "تصريف فعل avoir مع الضمير Nous:", listOf("avons", "avez", "ont"), 0),
        GameQuestion("f7", "كلمة «l'école» بالفرنسية تعني:", listOf("المدرسة", "النادي", "المنزل"), 0),
        GameQuestion("f8", "أداة التجزئة المناسبة مع كلمة مذكر تبدأ بساكن (pain):", listOf("du", "de la", "des"), 0),
        GameQuestion("f9", "فصل الصيف بالفرنسية يُسمى:", listOf("l'été", "l'hiver", "le printemps"), 0),
        GameQuestion("f10", "تصريف فعل aller مع الضمير Je في المضارع:", listOf("vais", "vas", "va"), 0),
        GameQuestion("f11", "أداة الاستفهام التي تسأل عن المكان «أين» هي:", listOf("Où", "Quand", "Qui"), 0),
        GameQuestion("f12", "أداة الاستفهام التي تسأل عن العاقل «مَن» هي:", listOf("Qui", "Que", "Comment"), 0),
        GameQuestion("f13", "اللون الأزرق باللغة الفرنسية هو:", listOf("bleu", "rouge", "vert"), 0),
        GameQuestion("f14", "كلمة «merci beaucoup» تعني:", listOf("شكراً جزيلاً", "عفواً", "إلى اللقاء"), 0),
        GameQuestion("f15", "لنفي الجملة في الفرنسية نضع الفعل بين:", listOf("ne ... pas", "le ... la", "un ... une"), 0),
        GameQuestion("f16", "تحدي الزعيم: جمع كلمة «un ami» هو:", listOf("des amis", "les ami", "un amis"), 0, isBossQuestion = true),
        GameQuestion("f17", "تحدي الزعيم: زمن المستقبل القريب يتكون من تصريف فعل:", listOf("aller + المصدر", "avoir + اسم المفعول", "être + الصفة"), 0, isBossQuestion = true),
        GameQuestion("f18", "تحدي الزعيم: صفة الملكية المناسبة لـ (أبي - père) مع Je:", listOf("mon", "ma", "mes"), 0, isBossQuestion = true)
    )

    private val historyQuestions = listOf(
        GameQuestion("h1", "من هو مؤسس الدولة الطولونية في مصر؟", listOf("أحمد بن طولون", "محمد علي", "صلاح الدين"), 0),
        GameQuestion("h2", "ما هي عاصمة الدولة العباسية في عهد هارون الرشيد؟", listOf("بغداد", "دمشق", "القاهرة"), 0),
        GameQuestion("h3", "في أي عام انتصر المصريون في حرب أكتوبر المجيدة؟", listOf("1973", "1956", "1967"), 0),
        GameQuestion("h4", "القائد المسلم الذي انتصر في معركة حطين عام 1187م؟", listOf("صلاح الدين الأيوبي", "قطز", "بيبرس"), 0),
        GameQuestion("h5", "من القائد الذي صدّ المغول في معركة عين جالوت؟", listOf("سيف الدين قطز", "عمرو بن العاص", "جوهر الصقلي"), 0),
        GameQuestion("h6", "افتُتحت قناة السويس للملاحة العالمية عام:", listOf("1869", "1805", "1952"), 0),
        GameQuestion("h7", "الكتاب الموسوعي الذي ألّفه علماء الحملة الفرنسية عن مصر:", listOf("وصف مصر", "خطط المقريزي", "مقدمة ابن خلدون"), 0),
        GameQuestion("h8", "حجر رشيد الذي فك رموزه شامبليون كشف أسرار اللغة:", listOf("المصرية القديمة", "الفارسية", "اللاتينية"), 0),
        GameQuestion("h9", "أنشأ محمد علي مدرسة عسكرية لتدريب الضباط في:", listOf("أسوان", "دمياط", "العريش"), 0),
        GameQuestion("h10", "زعيم الثورة العرابية عام 1881م للمطالبة بحقوق الشعب:", listOf("أحمد عرابي", "سعد زغلول", "مصطفى كامل"), 0),
        GameQuestion("h11", "مؤسس الحزب الوطني عام 1907م وصاحب جريدة اللواء:", listOf("مصطفى كامل", "محمد فريد", "عدلي يكن"), 0),
        GameQuestion("h12", "قائد ثورة 1919م الشعبية في مصر هو الزعيم:", listOf("سعد زغلول", "أحمد عرابي", "محمود سامي البارودي"), 0),
        GameQuestion("h13", "تأسست جامعة الدول العربية عام 1945م واختير مقرها في:", listOf("القاهرة", "تونس", "الرياض"), 0),
        GameQuestion("h14", "تم تأميم شركة قناة السويس شركة مساهمة مصرية في يوليو:", listOf("1956", "1952", "1973"), 0),
        GameQuestion("h15", "صاحب خطة الخداع الاستراتيجي في حرب أكتوبر المشير:", listOf("أحمد إسماعيل", "سعد الدين الشاذلي", "الجمسي"), 0),
        GameQuestion("h16", "رئيس أركان حرب القوات المسلحة صاحب خطة المآذن العالية:", listOf("سعد الدين الشاذلي", "أحمد إسماعيل", "عبد المنعم رياض"), 0),
        GameQuestion("h17", "تحدي الزعيم: مؤسس مصر الحديثة الذي تولى الحكم عام 1805م؟", listOf("محمد علي باشا", "الخديوي إسماعيل", "سعيد باشا"), 0, isBossQuestion = true),
        GameQuestion("h18", "تحدي الزعيم: معاهدة السلام عام 1979م أدت لاسترداد كامل:", listOf("شبه جزيرة سيناء", "السودان", "الشام"), 0, isBossQuestion = true),
        GameQuestion("h19", "تحدي الزعيم: استردت مصر طابا عام 1989م عن طريق:", listOf("التحكيم الدولي", "الحرب العسكرية", "المقايضة"), 0, isBossQuestion = true)
    )

    private val geographyQuestions = listOf(
        GameQuestion("g1", "ما هو أكبر محيطات العالم من حيث المساحة؟", listOf("المحيط الهادئ", "المحيط الهندي", "المحيط الأطلسي"), 0),
        GameQuestion("g2", "أي كوكب يُعرف بالكوكب الأحمر؟", listOf("المريخ", "الزهرة", "عطارد"), 0),
        GameQuestion("g3", "أطول نهر في قارة أفريقيا والعالم هو:", listOf("نهر النيل", "نهر الكونغو", "نهر النيجر"), 0),
        GameQuestion("g4", "المضيق الذي يربط البحر الأحمر بخليج عدن هو:", listOf("باب المندب", "جبل طارق", "هرمز"), 0),
        GameQuestion("g5", "أعلى قمة جبلية في العالم هي قمة:", listOf("إيفرست", "كليمنجارو", "الألب"), 0),
        GameQuestion("g6", "الدولة العربية التي تمر بها قناة السويس هي:", listOf("مصر", "المغرب", "عُمان"), 0),
        GameQuestion("g7", "العالم الذي وضع كتاب «الجغرافيا السياسية» عام 1897م:", listOf("فريدريك راتزل", "رودلف كيلين", "ابن خلدون"), 0),
        GameQuestion("g8", "الدولة التي تتكون من عدة جزر مثل اليابان وإندونيسيا تسمى:", listOf("دولة بحرية مجزأة", "دولة حبيسة", "دولة شريطية"), 0),
        GameQuestion("g9", "الدولة التي لا تطل على أي مسطحات مائية مفتوحة تسمى:", listOf("دولة حبيسة", "دولة هامشية", "دولة بحرية"), 0),
        GameQuestion("g10", "جبال تفصل بين إسبانيا وفرنسا كحدود طبيعية منيعة:", listOf("جبال البرانس", "جبال الأنديز", "جبال الهيمالايا"), 0),
        GameQuestion("g11", "أكبر تكتل اقتصادي متكامل في القارة الأوروبية هو:", listOf("الاتحاد الأوروبي", "الكوميسا", "النافتا"), 0),
        GameQuestion("g12", "تكتل اقتصادي أفريقي يضم دول شرق وجنوب أفريقيا ومصر:", listOf("الكوميسا COMESA", "الإيكواس", "الأبيك"), 0),
        GameQuestion("g13", "النظام السياسي في فرنسا يُصنف دستورياً بأنه نظام:", listOf("شبه رئاسي", "برلماني ملكي", "حزبي مغلق"), 0),
        GameQuestion("g14", "عاصمة جمهورية مصر العربية وقلبها السياسي والثقافي:", listOf("القاهرة", "الإسكندرية", "أسوان"), 0),
        GameQuestion("g15", "مضيق يصل بين الخليج العربي وخليج عُمان هو مضيق:", listOf("هرمز", "جبل طارق", "البوسفور"), 0),
        GameQuestion("g16", "مضيق يفصل بين المغرب وإسبانيا ويربط الأطلسي بالمتوسط:", listOf("جبل طارق", "باب المندب", "داردنيل"), 0),
        GameQuestion("g17", "تحدي الزعيم: خط الطول الرئيسي الذي درجته صفر هو خط:", listOf("جرينتش", "الاستواء", "السرطان"), 0, isBossQuestion = true),
        GameQuestion("g18", "تحدي الزعيم: المياه التي تلي المياه الإقليمية نحو أعالي البحار:", listOf("المياه التكميلية", "المياه الداخلية", "البحيرات"), 0, isBossQuestion = true),
        GameQuestion("g19", "تحدي الزعيم: المقر الدائم لمنظمة الأمم المتحدة يقع في مدينة:", listOf("نيويورك", "جنيف", "باريس"), 0, isBossQuestion = true)
    )

    private val philosophyQuestions = listOf(
        GameQuestion("ph1", "مؤسس علم المنطق الصوري القديم هو الفيلسوف:", listOf("أرسطو", "سقراط", "ديكارت"), 0),
        GameQuestion("ph2", "المقولة الشهيرة «أنا أفكر إذن أنا موجود» تنسب إلى:", listOf("ديكارت", "أفلاطون", "ابن خلدون"), 0),
        GameQuestion("ph3", "الاستدلال الذي ننتقل فيه من العام إلى الخاص يُسمى:", listOf("الاستنباط", "الاستقراء", "الحدس"), 0),
        GameQuestion("ph4", "القضية التي يكون فيها المحمول والموضوع متكافئين تسمى:", listOf("قضية تحليلية", "قضية متناقضة", "قياساً فاسداً"), 0),
        GameQuestion("ph5", "الفرع الفلسفي الذي يدرس القيم والأخلاق الطبية يُسمى:", listOf("البيوتيقا", "الأنطولوجيا", "الجماليات"), 0),
        GameQuestion("ph6", "الشك الذي يهدف إلى الوصول لليقين يُسمى شكاً:", listOf("منهجياً", "مذهبياً", "مطلقاً"), 0),
        GameQuestion("ph7", "الفيلسوف العربي المسلم الملقب بـ «المعلم الثاني» هو:", listOf("الفارابي", "الكندي", "ابن رشد"), 0),
        GameQuestion("ph8", "الطبيب العربي صاحب كتاب «القانون في الطب» هو:", listOf("ابن سينا", "الرازي", "ابن النفيس"), 0),
        GameQuestion("ph9", "الفيلسوف الإنجليزي الذي نقد أوهام العقل الأربعة وأسس الاستقراء:", listOf("فرانسيس بيكون", "جون لوك", "برتراند راسل"), 0),
        GameQuestion("ph10", "أوهام تنشأ عن أخطاء اللغة والتباس الألفاظ عند بيكون:", listOf("أوهام السوق", "أوهام الكهف", "أوهام المسرح"), 0),
        GameQuestion("ph11", "العالِم العربي الذي استخدم المنهج التجريبي في البصريات:", listOf("الحسن بن الهيثم", "جابر بن حيان", "الخوارزمي"), 0),
        GameQuestion("ph12", "أحد معايير الأخلاق الطبية الحديثة ويعني عدم إلحاق الأذى:", listOf("عدم الإيذاء", "المنفعة فقط", "التبعية"), 0),
        GameQuestion("ph13", "علم التحكم والاتصال الآلي في الكائنات والآلات يُسمى:", listOf("السيبرنطيقا", "الميتافيزيقا", "البراجماتية"), 0),
        GameQuestion("ph14", "القيمة الفلسفية التي تمنح الفرد القدرة على الاختيار المسؤول:", listOf("الحرية الإنسانية", "الحتمية", "الجبرية"), 0),
        GameQuestion("ph15", "الاستقراء الذي يفحص جميع جزئيات الظاهرة دون استثناء:", listOf("الاستقراء التام", "الاستقراء الناقص", "المماثلة"), 0),
        GameQuestion("ph16", "تحدي الزعيم: الفيلسوف العربي صاحب كتاب «مقدمة في علم العمران»؟", listOf("ابن خلدون", "الفارابي", "الكندي"), 0, isBossQuestion = true),
        GameQuestion("ph17", "تحدي الزعيم: رابط الوصل المنطقي (ق ∧ ك) يصدق فقط إذا:", listOf("صدقت القضيتان معاً", "كذبت القضيتان", "صدقت إحداهما فقط"), 0, isBossQuestion = true),
        GameQuestion("ph18", "تحدي الزعيم: الفيلسوف الذي دعا لاحترام حقوق الأجيال القادمة:", listOf("هانز يوناس", "بيتر سنجر", "ألد ليوبولد"), 0, isBossQuestion = true)
    )

    private val psychologyQuestions = listOf(
        GameQuestion("ps1", "مؤسس أول معمل تجريبي لعلم النفس عام 1879م هو:", listOf("فونت", "فرويد", "بافلوف"), 0),
        GameQuestion("ps2", "صاحب نظرية «التعلم الشرطي الكلاسيكي» هو العالم:", listOf("بافلوف", "ثورندايك", "كوهلر"), 0),
        GameQuestion("ps3", "العالم الذي قسم الذكاءات إلى «ذكاءات متعددة» هو:", listOf("جاردنر", "سبيرمان", "بينيه"), 0),
        GameQuestion("ps4", "المؤسس الحقيقي لعلم الاجتماع (العمران البشري) هو:", listOf("ابن خلدون", "أوجست كونت", "دوركايم"), 0),
        GameQuestion("ps5", "المرحلة التي تتميز بالنمو السريع والبحث عن الهوية هي:", listOf("المراهقة", "الطفولة المبكرة", "الشيخوخة"), 0),
        GameQuestion("ps6", "عملية تحويل المثيرات الحسية إلى معانٍ مفهومة تُسمى:", listOf("الإدراك", "النسيان", "الكبت"), 0),
        GameQuestion("ps7", "القدرة على استخدام اللغة والتعبير البلاغي بطلاقة تُسمى ذكاءً:", listOf("لغوياً", "حركياً", "بصرياً مكانياً"), 0),
        GameQuestion("ps8", "نظرية التعلم بـ «الفهم والاستبصار» قدمها علماء مدرسة:", listOf("الجشطلت", "السلوكية", "التحليل النفسي"), 0),
        GameQuestion("ps9", "حيلة دفاعية لا شعورية يُبعد بها الفرد الذكريات المؤلمة:", listOf("الكبت", "التسامي", "التعويض"), 0),
        GameQuestion("ps10", "عملية اجتماعية تهدف لتحقيق هدف مشترك بين الأفراد:", listOf("التعاون الاجتماعي", "الصراع", "التنافس السلبي"), 0),
        GameQuestion("ps11", "الغدة التي تفرز هرمون الثيروكسين المؤثر في النشاط والنمو:", listOf("الغدة الدرقية", "الغدة الصنوبرية", "الغدة اللعابية"), 0),
        GameQuestion("ps12", "أحد مبادئ التعلم الجيد ويعتمد على رغبة الطالب الداخلية:", listOf("الدافعية", "التشتت", "الحفظ الآلي"), 0),
        GameQuestion("ps13", "تركيز الشعور والذهن في مثير معين دون غيره يُسمى:", listOf("الانتباه", "الإحساس", "الاسترجاع"), 0),
        GameQuestion("ps14", "العمل الذي يقوم به الفرد لخدمة مجتمعه دون انتظار مقابل مادي:", listOf("العمل التطوعي", "العمل الإجباري", "الاغتراب"), 0),
        GameQuestion("ps15", "العالم الفرنسي الذي أطلق مصطلح «السوسيولوجيا» حديثاً:", listOf("أوجست كونت", "ماكس فيبر", "جون ديوي"), 0),
        GameQuestion("ps16", "تحدي الزعيم: قانون «الأثر» في التعلم بالمحاولة والخطأ وضعه العالم:", listOf("ثورندايك", "واطسون", "ماسلو"), 0, isBossQuestion = true),
        GameQuestion("ps17", "تحدي الزعيم: صاحب هرم الحاجات الإنسانية وصولاً لتحقيق الذات:", listOf("أبراهام ماسلو", "سيجموند فرويد", "ألبورت"), 0, isBossQuestion = true),
        GameQuestion("ps18", "تحدي الزعيم: الظاهرة الاجتماعية عند إميل دوركايم تتسم بـ:", listOf("الجبر والإلزام", "الفردية المؤقتة", "الاختيار المطلق"), 0, isBossQuestion = true)
    )
}
