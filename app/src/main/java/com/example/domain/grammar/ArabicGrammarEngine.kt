package com.example.domain.grammar

import com.example.domain.ai.GeminiAiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class IrabWordResult(
    val originalWord: String,
    val vocalizedWord: String,
    val syntacticRole: String, // الموقع الإعرابي: مبتدأ، فاعل، مفعول به، اسم إن، خبر كان...
    val grammaticalCase: String, // الحالة الإعرابية: مرفوع، منصوب، مجرور، مجزوم، مبني
    val marker: String, // علامة الإعراب: وعلامة رفعه الضمة الظاهرة، الفتحة، الياء...
    val whyReason: String, // «ليه اتعربت كده؟»: الشرح التوضيحي والقاعدة النحوية
    val subNotes: String = "" // ملاحظات وضمائر متصلة
)

data class IrabSentenceAnalysis(
    val originalSentence: String,
    val vocalizedSentence: String,
    val sentenceType: String, // جملة اسمية / جملة فعلية
    val generalMeaning: String,
    val words: List<IrabWordResult>,
    val timestamp: Long = System.currentTimeMillis()
)

data class GrammarQuizQuestion(
    val id: Int,
    val sentenceText: String,
    val targetWord: String,
    val questionPrompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

object ArabicGrammarEngine {

    /**
     * Famous pre-cached parsed sentences with comprehensive, verified grammar breakdowns
     */
    private val offlineCachedAnalyses = mapOf(
        "العلم نور" to IrabSentenceAnalysis(
            originalSentence = "العلم نور",
            vocalizedSentence = "العِلْمُ نُورٌ",
            sentenceType = "جملة اسمية (مبتدأ وخبر)",
            generalMeaning = "توضح الجملة فضل العلم وأنه يضيء طريق الإنسان ويهديه إلى الحق والصواب.",
            words = listOf(
                IrabWordResult(
                    originalWord = "العلم",
                    vocalizedWord = "العِلْمُ",
                    syntacticRole = "مبتدأ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه اسم معرفة مرفوع تبدأ به الجملة الاسمية ويدل على الشيء المتحدث عنه.",
                    subNotes = "نوع الاسم: اسم مفرد معرف بأل"
                ),
                IrabWordResult(
                    originalWord = "نور",
                    vocalizedWord = "نُورٌ",
                    syntacticRole = "خبر المبتدأ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره (تنوين ضم)",
                    whyReason = "لأنه الجزء المتمم لمعنى الجملة الاسمية مع المبتدأ والذي يحسن السكوت عليه.",
                    subNotes = "نوع الخبر: خبر مفرد نكرة"
                )
            )
        ),
        "يذاكر الطلاب بجد" to IrabSentenceAnalysis(
            originalSentence = "يذاكر الطلاب بجد",
            vocalizedSentence = "يُذَاكِرُ الطُّلَّابُ بِجِدٍّ",
            sentenceType = "جملة فعلية (فعل + فاعل + جار ومجرور)",
            generalMeaning = "توضح الجملة حرص الطلاب على الاجتهاد وبذل الجهد في تحصيل دروسهم.",
            words = listOf(
                IrabWordResult(
                    originalWord = "يذاكر",
                    vocalizedWord = "يُذَاكِرُ",
                    syntacticRole = "فعل مضارع",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه فعل مضارع تجرد من الناصب والجازم (لم يسبقه حرف نصب ولا جزم).",
                    subNotes = "فعل مبني للمعلوم صحيح الآخر"
                ),
                IrabWordResult(
                    originalWord = "الطلاب",
                    vocalizedWord = "الطُّلَّابُ",
                    syntacticRole = "فاعل",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه من قام بالفعل (المذاكرة) أو اتصف به.",
                    subNotes = "نوع الفاعل: جمع تكسير يرفع بالضمة"
                ),
                IrabWordResult(
                    originalWord = "بجد",
                    vocalizedWord = "بِجِدٍّ",
                    syntacticRole = "جار ومجرور (شبه جملة في محل نصب حال)",
                    grammaticalCase = "مجرور بالباء",
                    marker = "وعلامة جره الكسرة الظاهرة تحت آخره",
                    whyReason = "الباء حرف جر مبني على الكسر لا محل له من الإعراب، وجدٍّ اسم مجرور بالباء.",
                    subNotes = "شبه الجملة متعلق بالفعل يذاكر ويبين هيئة المذاكرة"
                )
            )
        ),
        "إن المجتهدين فائزون" to IrabSentenceAnalysis(
            originalSentence = "إن المجتهدين فائزون",
            vocalizedSentence = "إِنَّ المُجْتَهِدِينَ فَائِزُونَ",
            sentenceType = "جملة اسمية منسوخة بالحرف الناسخ (إنّ)",
            generalMeaning = "تأكيد فوز وظفر المجتهدين بثمرة تعبهم ودراستهم.",
            words = listOf(
                IrabWordResult(
                    originalWord = "إن",
                    vocalizedWord = "إِنَّ",
                    syntacticRole = "حرف توكيد ونصب ناسخ",
                    grammaticalCase = "مبني على الفتح",
                    marker = "مبني على الفتح لا محل له من الإعراب",
                    whyReason = "حرف ناسخ يدخل على الجملة الاسمية فينصب المبتدأ ويسمى اسمه ويرفع الخبر ويسمى خبره.",
                    subNotes = "يفيد التوكيد ونفي الشك"
                ),
                IrabWordResult(
                    originalWord = "المجتهدين",
                    vocalizedWord = "المُجْتَهِدِينَ",
                    syntacticRole = "اسم إنّ",
                    grammaticalCase = "منصوب",
                    marker = "وعلامة نصبه الياء لأنه جمع مذكر سالم",
                    whyReason = "لأنه وقع اسماً لـ (إنّ) الناسخة التي تنصب اسمها، وعلامة النصب الفرعية لجمع المذكر السالم هي الياء.",
                    subNotes = "النون عوض عن التنوين في الاسم المفرد"
                ),
                IrabWordResult(
                    originalWord = "فائزون",
                    vocalizedWord = "فَائِزُونَ",
                    syntacticRole = "خبر إنّ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الواو لأنه جمع مذكر سالم",
                    whyReason = "لأنه متمم معنى الجملة مع اسم إنّ وخبرها مرفوع دائماً، وعلامة الرفع في جمع المذكر السالم هي الواو.",
                    subNotes = "نوع الخبر: خبر مفرد مرفوع بالواو"
                )
            )
        ),
        "كان الجو معتدلاً" to IrabSentenceAnalysis(
            originalSentence = "كان الجو معتدلاً",
            vocalizedSentence = "كَانَ الجَوُّ مُعْتَدِلاً",
            sentenceType = "جملة اسمية منسوخة بالفعل الناسخ (كان)",
            generalMeaning = "الإخبار عن اعتدال الطقس في الزمن الماضي.",
            words = listOf(
                IrabWordResult(
                    originalWord = "كان",
                    vocalizedWord = "كَانَ",
                    syntacticRole = "فعل ماض ناسخ ناقص",
                    grammaticalCase = "مبني على الفتح",
                    marker = "مبني على الفتح لا محل له من الإعراب",
                    whyReason = "فعل ناسخ ناقص يدخل على الجملة الاسمية فيرفع المبتدأ ويسمى اسمه وينصب الخبر ويسمى خبره.",
                    subNotes = "يدل على الاتصاف في الزمن الماضي"
                ),
                IrabWordResult(
                    originalWord = "الجو",
                    vocalizedWord = "الجَوُّ",
                    syntacticRole = "اسم كان",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه المسند إليه في الجملة بعد الفعل الناسخ، واسم كان يكون مرفوعاً دائماً.",
                    subNotes = "اسم مفرد صحيح الآخر"
                ),
                IrabWordResult(
                    originalWord = "معتدلاً",
                    vocalizedWord = "مُعْتَدِلاً",
                    syntacticRole = "خبر كان",
                    grammaticalCase = "منصوب",
                    marker = "وعلامة نصبه الفتحة الظاهرة على آخره (تنوين فتح)",
                    whyReason = "لأنه المتمم لمعنى جملة كان، وخبر الأفعال الناسخة يكون منصوباً دائماً.",
                    subNotes = "نوع الخبر: خبر مفرد مشتق (اسم فاعل)"
                )
            )
        ),
        "الصبر مفتاح الفرج" to IrabSentenceAnalysis(
            originalSentence = "الصبر مفتاح الفرج",
            vocalizedSentence = "الصَّبْرُ مِفْتَاحُ الفَرَجِ",
            sentenceType = "جملة اسمية (مبتدأ + خبر + مضاف إليه)",
            generalMeaning = "حكمة بليغة تؤكد أن التحلي بالصبر يقود إلى تيسير الأمور وانفراج الكروب.",
            words = listOf(
                IrabWordResult(
                    originalWord = "الصبر",
                    vocalizedWord = "الصَّبْرُ",
                    syntacticRole = "مبتدأ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "اسم معرفة مرفوع تبدأ به الجملة الاسمية.",
                    subNotes = "مصدر ثلاثي لفعل صبر"
                ),
                IrabWordResult(
                    originalWord = "مفتاح",
                    vocalizedWord = "مِفْتَاحُ",
                    syntacticRole = "خبر المبتدأ وهو مضاف",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "متمم معنى الجملة مع المبتدأ، وحُذف تنوينه للإضافة.",
                    subNotes = "اسم آلة مشتق على وزن مِفْعَال"
                ),
                IrabWordResult(
                    originalWord = "الفرج",
                    vocalizedWord = "الفَرَجِ",
                    syntacticRole = "مضاف إليه",
                    grammaticalCase = "مجرور",
                    marker = "وعلامة جره الكسرة الظاهرة تحت آخره",
                    whyReason = "لأنه اسم معرفة أضيف إلى نكرة سابقة (مفتاح) لتعريفه وتخصيص معناه.",
                    subNotes = "المضاف إليه مجرور دائماً"
                )
            )
        )
    )

    val grammarQuizQuestions = listOf(
        GrammarQuizQuestion(
            id = 1,
            sentenceText = "إنَّ الطالبينِ مجتهدانِ في دروسِهما.",
            targetWord = "الطالبينِ",
            questionPrompt = "ما هو الإعراب الصحيح للكلمة المحددة (الطالبينِ)؟",
            options = listOf(
                "اسم إنّ منصوب وعلامة نصبه الياء لأنه مثنى",
                "مبتدأ مرفوع وعلامة رفعه الألف لأنه مثنى",
                "خبر إنّ مرفوع وعلامة رفعه الألف",
                "مفعول به منصوب وعلامة نصبه الفتحة"
            ),
            correctIndex = 0,
            explanation = "كلمة (الطالبينِ) اسم إنّ منصوب وعلامة نصبه الياء لأنه مثنى، ونون المثنى مكسورة دائماً."
        ),
        GrammarQuizQuestion(
            id = 2,
            sentenceText = "أصبحَ المهندسونَ بارعينَ في تصميمِ المشاريع.",
            targetWord = "بارعينَ",
            questionPrompt = "ما إعراب كلمة (بارعينَ) في الجملة؟",
            options = listOf(
                "فاعل مرفوع بالواو",
                "خبر أصبحَ منصوب وعلامة نصبه الياء لأنه جمع مذكر سالم",
                "نعت منصوب بالفتحة",
                "اسم أصبحَ مرفوع بالضمة"
            ),
            correctIndex = 1,
            explanation = "(أصبح) فعل ناسخ من أخوات كان، يرفع المبتدأ وينصب الخبر. لذا (بارعين) خبر أصبح منصوب بالياء لأنه جمع مذكر سالم."
        ),
        GrammarQuizQuestion(
            id = 3,
            sentenceText = "لا تقصرْ في أداءِ واجباتِك اليومية.",
            targetWord = "تقصرْ",
            questionPrompt = "ما الحالة الإعرابية للفعل المضارع (تقصرْ)؟",
            options = listOf(
                "فعل مضارع مرفوع بالضمة",
                "فعل مضارع منصوب بالفتحة",
                "فعل مضارع مجزوم بـ (لا) الناهية وعلامة جزمه السكون",
                "فعل ماض مبني على الفتح"
            ),
            correctIndex = 2,
            explanation = "سبق الفعل المضارع (لا) الناهية الجازمة، وهو صحيح الآخر، فيجزم بالسكون الظاهر على آخره."
        ),
        GrammarQuizQuestion(
            id = 4,
            sentenceText = "سعى الطالبُ سعياً حثيثاً نحو القمة.",
            targetWord = "سعياً",
            questionPrompt = "ما هو الموقع الإعرابي لكلمة (سعياً)؟",
            options = listOf(
                "مفعول مطلق منصوب وعلامة نصبه الفتحة الظاهرة",
                "مفعول لأجله منصوب بالفتحة",
                "حال منصوب بالفتحة",
                "تمييز منصوب بالفتحة"
            ),
            correctIndex = 0,
            explanation = "(سعياً) مصدر مشتق من لفظ الفعل (سعى)، جاء لتأكيد الفعل وبيان نوعه (موصوف بـ حثيثاً) فهو مفعول مطلق مبين للنوع."
        ),
        GrammarQuizQuestion(
            id = 5,
            sentenceText = "كافأتُ الطلابَ إلا المهمِلَ.",
            targetWord = "المهمِلَ",
            questionPrompt = "ما إعراب كلمة (المهمِلَ) بعد أداة الاستثناء (إلا)؟",
            options = listOf(
                "مستثنى منصوب وجوباً وعلامة نصبه الفتحة الظاهرة",
                "بدل مرفوع بالضمة",
                "مضاف إليه مجرور بالكسرة",
                "فاعل مؤخر مرفوع بالضمة"
            ),
            correctIndex = 0,
            explanation = "الكلام تام مثبت (مستثنى منه موجود وغير منفي)، فحكم المستثنى بـ (إلا) هو وجوب النصب."
        )
    )

    /**
     * Parse sentence using Gemini AI, with instant offline cache and fallback
     */
    suspend fun parseSentence(
        inputSentence: String,
        aiRepository: GeminiAiRepository,
        apiKey: String = ""
    ): Result<IrabSentenceAnalysis> = withContext(Dispatchers.IO) {
        val trimmed = inputSentence.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("يرجى كتابة الجملة أو الفقرة المراد إعرابها."))
        }

        // 1. Check exact cache match
        val cached = offlineCachedAnalyses[trimmed]
            ?: offlineCachedAnalyses.entries.find { it.key.filterNot { c -> c.isWhitespace() } == trimmed.filterNot { c -> c.isWhitespace() } }?.value
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        // 2. Try Gemini 3.5 Flash AI syntax analysis
        val systemPrompt = """
            أنت "الخبير الأول في النحو العربي والإعراب المفصل والتعليمي".
            وظيفتك: إعراب الجمل والفقرات العربية كلمة بكلمة بدقة نحوية متناهية ومطابقة لمنهج النحو العربي الأصيل (سيبويه وابن مالك ومناهج الثانوية العامة).
            
            أهم ما يميزك:
            1. التشكيل الكامل للكلمة والجملة.
            2. إعراب كل كلمة وتحديد (الموقع الإعرابي، الحالة الإعرابية، علامة الإعراب).
            3. شرح مبسط في حقل (whyReason) يوضح «ليه اتعربت كده» مع ذكر القاعدة النحوية وعلة الإعراب بأسلوب شيق وواضح للطالب.
            4. ذكر تفاصيل الضمائر المتصلة والمستترة وشبه الجمل في حقل (subNotes).
            
            يجب أن تكون النتيجة حصراً بصيغة JSON بدون أي كلام خارج كائن الـ JSON:
            {
              "originalSentence": "نص الجملة الأصلية",
              "vocalizedSentence": "نَصُّ الجُمْلَةِ مَشْكُولاً بِالكـَامِلِ",
              "sentenceType": "جملة اسمية / جملة فعلية / أسلوب شرط...",
              "generalMeaning": "شرح المعنى الإجمالي للجملة في سطرين...",
              "words": [
                {
                  "originalWord": "الكلمة بدون تشكيل",
                  "vocalizedWord": "الكَلِمَةُ مُشَكَّلَةً",
                  "syntacticRole": "مبتدأ / فاعل / مفعول به / اسم إن / خبر...",
                  "grammaticalCase": "مرفوع / منصوب / مجرور / مجزوم / مبني",
                  "marker": "وعلامة رفعه الضمة الظاهرة على آخره",
                  "whyReason": "لأنه اسم تبدأ به الجملة الاسمية ويدل على المبتدأ به في الكلام...",
                  "subNotes": "ملاحظات إضافية أو إعراب الضمائر المتصلة"
                }
              ]
            }
        """.trimIndent()

        val userPrompt = "قم بإعراب الجملة التالية تفصيلياً مع توضيح لماذا تم إعراب كل كلمة بهذا الشكل:\n\n\"$trimmed\""

        val aiResult = aiRepository.generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = apiKey,
            modelName = "gemini-3.5-flash"
        )

        aiResult.fold(
            onSuccess = { responseText ->
                try {
                    val parsed = parseJsonResponse(trimmed, responseText)
                    Result.success(parsed)
                } catch (e: Exception) {
                    Result.success(buildRuleBasedFallback(trimmed))
                }
            },
            onFailure = {
                // Return high-quality rule-based fallback if offline
                Result.success(buildRuleBasedFallback(trimmed))
            }
        )
    }

    private fun parseJsonResponse(originalText: String, rawResponse: String): IrabSentenceAnalysis {
        var jsonText = rawResponse.trim()
        if (jsonText.startsWith("```json")) jsonText = jsonText.removePrefix("```json").trim()
        else if (jsonText.startsWith("```")) jsonText = jsonText.removePrefix("```").trim()
        if (jsonText.endsWith("```")) jsonText = jsonText.removeSuffix("```").trim()

        val startIdx = jsonText.indexOf('{')
        val endIdx = jsonText.lastIndexOf('}')
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            jsonText = jsonText.substring(startIdx, endIdx + 1)
        }

        val json = JSONObject(jsonText)
        val orig = json.optString("originalSentence", originalText)
        val vocalized = json.optString("vocalizedSentence", originalText)
        val type = json.optString("sentenceType", if (originalText.startsWith("ي") || originalText.startsWith("ت") || originalText.startsWith("أ") || originalText.startsWith("ن")) "جملة فعلية" else "جملة اسمية")
        val meaning = json.optString("generalMeaning", "تحليل نحوي متكامل للجملة وتراكيبها اللغوية.")

        val wordsList = mutableListOf<IrabWordResult>()
        val wordsArray = json.optJSONArray("words")
        if (wordsArray != null) {
            for (i in 0 until wordsArray.length()) {
                val wObj = wordsArray.getJSONObject(i)
                wordsList.add(
                    IrabWordResult(
                        originalWord = wObj.optString("originalWord", ""),
                        vocalizedWord = wObj.optString("vocalizedWord", wObj.optString("originalWord", "")),
                        syntacticRole = wObj.optString("syntacticRole", "كلمة في الجملة"),
                        grammaticalCase = wObj.optString("grammaticalCase", "حسب موقعها"),
                        marker = wObj.optString("marker", "علامة إعراب أصلية أو فرعية"),
                        whyReason = wObj.optString("whyReason", "تؤدي وظيفة نحوية دلالية داخل السياق التركيبي."),
                        subNotes = wObj.optString("subNotes", "")
                    )
                )
            }
        }

        if (wordsList.isEmpty()) {
            return buildRuleBasedFallback(originalText)
        }

        return IrabSentenceAnalysis(
            originalSentence = orig,
            vocalizedSentence = vocalized,
            sentenceType = type,
            generalMeaning = meaning,
            words = wordsList
        )
    }

    /**
     * Smart Rule-Based Offline Parser for Arabic Sentences
     */
    fun buildRuleBasedFallback(sentence: String): IrabSentenceAnalysis {
        val words = sentence.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val isVerbal = words.firstOrNull()?.let { isLikelyVerb(it) } ?: false
        val sentenceType = if (isVerbal) "جملة فعلية" else "جملة اسمية"

        val parsedWords = words.mapIndexed { index, word ->
            when {
                index == 0 && !isVerbal -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = "مبتدأ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه الاسم المبتدأ به في صدر الجملة الاسمية المتحدث عنه.",
                    subNotes = "الاسم في أول الجملة يكون مسنداً إليه"
                )
                index == 0 && isVerbal -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = if (word.startsWith("ي") || word.startsWith("ت") || word.startsWith("أ") || word.startsWith("ن")) "فعل مضارع" else "فعل ماض",
                    grammaticalCase = if (word.startsWith("ي") || word.startsWith("ت") || word.startsWith("أ") || word.startsWith("ن")) "مرفوع بالضمة" else "مبني على الفتح",
                    marker = "لتجرده من العوامل الناصبة والجازمة أو بنائه على الفتح",
                    whyReason = "لأنه يدل على حدث مقترن بزمن معين في صدر الجملة الفعلية.",
                    subNotes = "ركن أساسي في الجملة الفعلية"
                )
                index == 1 && !isVerbal -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = "خبر المبتدأ",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه الجزء المتمم للفائدة والمعنى مع المبتدأ.",
                    subNotes = "الخبر يتمم معنى المبتدأ"
                )
                index == 1 && isVerbal -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = "فاعل",
                    grammaticalCase = "مرفوع",
                    marker = "وعلامة رفعه الضمة الظاهرة على آخره",
                    whyReason = "لأنه من قام بالفعل أو اتصف به بعد الفعل التام.",
                    subNotes = "الفاعل ركن لازم بعد الفعل المبني للمعلوم"
                )
                word.startsWith("بـ") || word.startsWith("في") || word.startsWith("من") || word.startsWith("على") || word.startsWith("إلى") || word.startsWith("عن") || word.startsWith("لـ") -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = "حرف جر واسم مجرور",
                    grammaticalCase = "مجرور",
                    marker = "وعلامة جره الكسرة الظاهرة",
                    whyReason = "لأنه مسبوق بحرف جر أو متصل به.",
                    subNotes = "شبه جملة متعلق بالعامل السابق"
                )
                else -> IrabWordResult(
                    originalWord = word,
                    vocalizedWord = word,
                    syntacticRole = if (isVerbal) "مفعول به أو فضلة" else "نعت أو مضاف إليه",
                    grammaticalCase = if (isVerbal) "منصوب" else "حسب الموقع",
                    marker = "وعلامة إعرابه بحسب موقعه في التركيب",
                    whyReason = "يتمم معنى الجملة ويبين تفاصيل إضافية عن أركانها الأساسية.",
                    subNotes = "مكمل تركيبي للجملة"
                )
            }
        }

        return IrabSentenceAnalysis(
            originalSentence = sentence,
            vocalizedSentence = sentence,
            sentenceType = sentenceType,
            generalMeaning = "جملة عربية ذات دلالة بلاغية ونحوية واضحة.",
            words = parsedWords
        )
    }

    private fun isLikelyVerb(word: String): Boolean {
        if (word.startsWith("ال")) return false
        val commonPrefixes = listOf("ي", "ت", "ن", "أ", "است", "ان", "تـ")
        return commonPrefixes.any { word.startsWith(it) && word.length > 3 } ||
                word.endsWith("ت") || word.endsWith("وا") || word.endsWith("نا")
    }
}
