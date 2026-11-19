package com.example.studytools.irab

import android.content.Context
import com.example.domain.ai.FirebaseStudyChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class ArabicPosCategory(val labelAr: String) {
    VERB("فعل"),
    NOUN("اسم"),
    PARTICLE("حرف")
}

data class IrabWordResult(
    val word: String,
    val category: ArabicPosCategory,
    val irabStatement: String,
    val sign: String,
    val reason: String
)

data class IrabAnalysisResult(
    val sentence: String,
    val tokens: List<IrabWordResult>,
    val isAiPowered: Boolean = false
)

data class IrabTrainingQuestion(
    val id: Int,
    val sentence: String,
    val targetWord: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

object SmartIrabEngine {

    private val prepositions = setOf(
        "في", "من", "إلى", "الى", "على", "عن", "حتى", "منذ", "مذ", "رب", "خلا", "عدا", "حاشا"
    )
    private val nasibParticles = setOf("أن", "لن", "كي", "لكي", "حتى", "إذن")
    private val jazmParticles = setOf("لم", "لما", "لما")
    private val innaSisters = setOf("إن", "ان", "أن", "كأن", "لكن", "ليت", "لعل")
    private val kanaSisters = setOf(
        "كان", "كانت", "كانوا", "يكون", "أصبح", "أصبحت", "أضحى", "ظل", "أمسى", "بات", "صار", "ليس", "ليست"
    )
    private val conjunctions = setOf("و", "ثم", "أو", "او", "بل", "لكن", "أم")
    private val generalParticles = setOf("قد", "سوف", "هل", "بل", "لا", "ما", "نعم", "بلى", "يا", "أيا", "هيا")

    private val pronouns = setOf(
        "أنا", "نحن", "أنت", "أنتِ", "أنتما", "أنتم", "أنتن", "هو", "هي", "هما", "هم", "هن",
        "إياك", "إياه", "إياي"
    )
    private val demonstratives = setOf("هذا", "هذه", "هذان", "هاتان", "هؤلاء", "ذلك", "تلك", "أولئك", "هنا", "هناك")
    private val relatives = setOf("الذي", "التي", "اللذان", "اللتان", "الذين", "اللاتي", "اللائي", "من", "ما")

    private val knownPresentVerbs = setOf(
        "يذاكر", "تذاكر", "أذاكر", "نذاكر",
        "يكتب", "تكتب", "أكتب", "نكتب",
        "يقرأ", "تقرأ", "أقرأ", "نقرأ",
        "يدرس", "تدرس", "أدرس", "ندرس",
        "يجتهد", "تجتهد", "أجتهد", "نجتهد",
        "يفهم", "تفهم", "أفهم", "نفهم",
        "يعمل", "تعمل", "أعمل", "نعمل",
        "ينجح", "تنجح", "أنجح", "ننجح",
        "يسعى", "تسعى", "أسعى", "نسعى",
        "يذهب", "تذهب", "أذهب", "نذهب",
        "يشرح", "تشرح", "أشرح", "نشرح",
        "يحفظ", "تحفظ", "أحفظ", "نحفظ",
        "يتعلم", "تتعلم", "أتعلم", "نتعلم",
        "يحب", "تحب", "أحب", "نحب"
    )

    private val knownPastVerbs = setOf(
        "ذاكر", "ذاكرت", "كتب", "كتبت", "قرأ", "قرأت", "درس", "درست",
        "اجتهد", "اجتهدت", "فهم", "فهمت", "عمل", "عملت", "نجح", "نجحت",
        "ذهب", "ذهبت", "شرح", "شرحت", "حفظ", "حفظت", "تعلم", "تعلمت",
        "نصر", "فتح", "علم", "أكرم", "استغفر", "قال", "قالت", "سأل", "أجاب"
    )

    private val knownImperativeVerbs = setOf(
        "اكتب", "اقرأ", "ادرس", "ذاكرْ", "اجتهد", "افهم", "اعمل", "احفظ", "تعلم", "اسمع", "انظر", "قل"
    )

    val sampleSentences: List<String> = listOf(
        "يُذَاكِرُ الطَّالِبُ فِي الْمَكْتَبَةِ",
        "الطَّالِبُ الْمُجْتَهِدُ نَاجِحٌ",
        "كَتَبَ التِّلْمِيذُ الدَّرْسَ بِاتْقَانٍ",
        "لَنْ يَتَكاسَلَ الْمُتَفَوِّقُ",
        "إِنَّ الْعِلْمَ نُورٌ فِي الْحَيَاةِ",
        "كَانَ الْاِمْتِحَانُ سَهْلاً",
        "لَمْ يَقْصُرْ الطَّالِبُ فِي وَاجِبِهِ"
    )

    val trainingQuestions: List<IrabTrainingQuestion> = listOf(
        IrabTrainingQuestion(
            id = 1,
            sentence = "يُذَاكِرُ الطَّالِبُ فِي الْمَكْتَبَةِ",
            targetWord = "الطَّالِبُ",
            questionText = "ما إعراب كلمة (الطَّالِبُ) في الجملة السابقة؟",
            options = listOf("فاعل مرفوع بالضمة", "مبتدأ مرفوع بالضمة", "مفعول به منصوب بالفتحة", "اسم مجرور بالكسرة"),
            correctIndex = 0,
            explanation = "الطالب هو من قام بفعل المذاكرة، لذا يُعرب فاعلاً مرفوعاً وعلامة رفعه الضمة الظاهرة."
        ),
        IrabTrainingQuestion(
            id = 2,
            sentence = "إِنَّ الْعِلْمَ نُورٌ سَاطِعٌ",
            targetWord = "الْعِلْمَ",
            questionText = "ما موقع كلمة (الْعِلْمَ) من الإعراب؟",
            options = listOf("مبتدأ مرفوع بالضمة", "اسم إنّ منصوب بالفتحة", "خبر إنّ مرفوع بالضمة", "مفعول به منصوب"),
            correctIndex = 1,
            explanation = "(إنّ) حرف ناسخ يدخل على الجملة الاسمية فينصب المبتدأ ويُسمّى اسمها، ويرفع الخبر."
        ),
        IrabTrainingQuestion(
            id = 3,
            sentence = "لَنْ يَنْجَحَ الْكَسُولُ",
            targetWord = "يَنْجَحَ",
            questionText = "لماذا جاء الفعل (يَنْجَحَ) منصوباً؟",
            options = listOf("لأنه فعل ماضٍ", "لسبقه بأداة النصب (لن)", "لأنه فاعل", "لسبقه بأداة جزم"),
            correctIndex = 1,
            explanation = "(لن) من أدوات نصب الفعل المضارع، فتكون علامة نصبه الفتحة الظاهرة."
        ),
        IrabTrainingQuestion(
            id = 4,
            sentence = "كَانَ الطَّقْسُ جَمِيلاً",
            targetWord = "جَمِيلاً",
            questionText = "ما إعراب كلمة (جَمِيلاً)؟",
            options = listOf("خبر كان منصوب بالفتحة", "اسم كان مرفوع", "حال منصوب", "نعت مرفوع"),
            correctIndex = 0,
            explanation = "(كان) فعل ناسخ يرفع الاسم (الطقسُ) وينصب الخبر (جميلاً)."
        ),
        IrabTrainingQuestion(
            id = 5,
            sentence = "قَرَأْتُ كِتَابَ التَّارِيخِ",
            targetWord = "التَّارِيخِ",
            questionText = "ما إعراب كلمة (التَّارِيخِ)؟",
            options = listOf("مفعول به منصوب", "مضاف إليه مجرور بالكسرة", "نعت مجرور", "اسم مجرور بحرف جر"),
            correctIndex = 1,
            explanation = "كلمة (التاريخِ) معرفة جاءت بعد نكرة (كتابَ) لتوضحها وتخصصها، فهي مضاف إليه مجرور بالكسرة."
        ),
        IrabTrainingQuestion(
            id = 6,
            sentence = "لَمْ يَذْهَبْ أَحْمَدُ إِلَى النَّادِي",
            targetWord = "يَذْهَبْ",
            questionText = "ما علامة إعراب الفعل المضارع (يَذْهَبْ)؟",
            options = listOf("مرفوع بالضمة", "منصوب بالفتحة", "مجزوم وعلامة جزمه السكون", "مبني على الفتح"),
            correctIndex = 2,
            explanation = "(لم) حرف جزم ونفي وقلب يجزم الفعل المضارع الصحيح الآخر بالسكون."
        ),
        IrabTrainingQuestion(
            id = 7,
            sentence = "الطَّالِبُونَ مُجْتَهِدُونَ",
            targetWord = "مُجْتَهِدُونَ",
            questionText = "ما علامة رفع الخبر (مُجْتَهِدُونَ)؟",
            options = listOf("الضمة المقدرة", "الألف", "الواو لأنه جمع مذكر سالم", "ثبوت النون"),
            correctIndex = 2,
            explanation = "جمع المذكر السالم يُرفع بالواو نيابةً عن الضمة، ويُنصب ويُجر بالياء."
        ),
        IrabTrainingQuestion(
            id = 8,
            sentence = "الْعِلْمُ يَرْفَعُ بُيُوتاً لَا عِمَادَ لَهَا",
            targetWord = "الْعِلْمُ",
            questionText = "ما إعراب كلمة (الْعِلْمُ)؟",
            options = listOf("فاعل مرفوع", "مبتدأ مرفوع وعلامة رفعه الضمة", "خبر مرفوع", "مفعول به"),
            correctIndex = 1,
            explanation = "اسم معرفة بدأَت به الجملة الاسمية فهو مبتدأ مرفوع بالضمة الظاهرة."
        )
    )

    private fun stripTashkeel(word: String): String {
        return word.replace(Regex("[\u064B-\u065F\u0670]"), "")
    }

    private fun cleanPunctuation(token: String): String {
        return token.trim().trim('،', '؛', '.', '!', '؟', ':', '"', '\'', '(', ')')
    }

    fun analyzeOffline(rawSentence: String): IrabAnalysisResult {
        val rawTokens = rawSentence.trim()
            .split(Regex("\\s+"))
            .map { cleanPunctuation(it) }
            .filter { it.isNotBlank() }

        if (rawTokens.isEmpty()) {
            return IrabAnalysisResult(sentence = rawSentence, tokens = emptyList())
        }

        val results = mutableListOf<IrabWordResult>()

        var isVerbalClause = false
        var verbSeen = false
        var subjectFound = false
        var expectPrepObject = false
        var expectInnaNoun = false
        var expectInnaPred = false
        var expectKanaNoun = false
        var expectKanaPred = false
        var expectPresentSubjunctive = false
        var expectPresentJussive = false
        var prevWasNominalNoun = false

        for ((index, token) in rawTokens.withIndex()) {
            val plain = stripTashkeel(token)
            val norm = plain.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')

            // Attached single-letter prepositions (بـ / كـ / لـ) detection for long nouns
            val hasAttachedPrep = plain.length >= 4 &&
                (plain.startsWith("ب") || plain.startsWith("ك") || plain.startsWith("ل")) &&
                plain.substring(1).startsWith("ال")

            // 1. Check Particles first
            if (plain in prepositions) {
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.PARTICLE,
                        irabStatement = "حرف جر مبني لا محل له من الإعراب",
                        sign = "السكون",
                        reason = "حروف الجر مبنية دائماً وتجر الاسم الذي بعدها"
                    )
                )
                expectPrepObject = true
                prevWasNominalNoun = false
                continue
            }

            if (plain in nasibParticles || plain == "ان" && index > 0) {
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.PARTICLE,
                        irabStatement = "حرف نصب ومصدرية مبني لا محل له من الإعراب",
                        sign = "السكون",
                        reason = "ينصب الفعل المضارع الواقع بعده"
                    )
                )
                expectPresentSubjunctive = true
                continue
            }

            if (plain in jazmParticles) {
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.PARTICLE,
                        irabStatement = "حرف نفي وجزم وقلب مبني على السكون لا محل له من الإعراب",
                        sign = "السكون",
                        reason = "يجزم الفعل المضارع الواقع بعده"
                    )
                )
                expectPresentJussive = true
                continue
            }

            if (plain in innaSisters) {
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.PARTICLE,
                        irabStatement = "حرف توكيد ونصب ناسخ مبني على الفتح لا محل له من الإعراب",
                        sign = "الفتح",
                        reason = "ينصب المبتدأ اسماً له ويرفع الخبر خبراً له"
                    )
                )
                expectInnaNoun = true
                continue
            }

            if (plain in conjunctions || plain in generalParticles) {
                val stmt = if (plain in conjunctions) "حرف عطف مبني لا محل له من الإعراب" else "حرف مبني لا محل له من الإعراب"
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.PARTICLE,
                        irabStatement = stmt,
                        sign = "البناء",
                        reason = "الحروف في العربية مبنية دائماً ولا محل لها من الإعراب"
                    )
                )
                continue
            }

            // 2. Check Kana sisters (الأفعال الناسخة)
            if (plain in kanaSisters) {
                isVerbalClause = true
                verbSeen = true
                expectKanaNoun = true
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.VERB,
                        irabStatement = "فعل ماضٍ ناقص ناسخ مبني على الفتح",
                        sign = "الفتحة",
                        reason = "الأفعال الناسخة ترفع المبتدأ وتنصب الخبر"
                    )
                )
                continue
            }

            // 3. Heuristic Verb Detection
            val endsWithTanwin = token.contains('\u064B') || token.contains('\u064C') || token.contains('\u064D')
            val startsWithAl = plain.startsWith("ال") || plain.startsWith("اال")
            val isExplicitPresentEnding = !startsWithAl && !endsWithTanwin && (
                plain in knownPresentVerbs ||
                    (plain.length >= 4 && (plain.startsWith("ي") || plain.startsWith("ت") || plain.startsWith("ن") || norm.startsWith("ا")) &&
                        (token.endsWith("\u064F") || token.endsWith("\u064E") || token.endsWith("\u0652") ||
                            plain.endsWith("ون") || expectPresentSubjunctive || expectPresentJussive))
                )
            val isExplicitPast = !startsWithAl && !endsWithTanwin && (plain in knownPastVerbs)
            val isExplicitImperative = !startsWithAl && !endsWithTanwin && (plain in knownImperativeVerbs)

            if (isExplicitPresentEnding) {
                isVerbalClause = true
                verbSeen = true
                val (irabLine, signLine, reasonLine) = when {
                    expectPresentSubjunctive -> {
                        expectPresentSubjunctive = false
                        Triple(
                            "فعل مضارع منصوب وعلامة نصبه الفتحة الظاهرة",
                            "الفتحة",
                            "سبقته أداة نصب (لن / أن / كي)"
                        )
                    }
                    expectPresentJussive -> {
                        expectPresentJussive = false
                        Triple(
                            "فعل مضارع مجزوم وعلامة جزمه السكون",
                            "السكون",
                            "سبقته أداة جزم (لم / لا الناهية)"
                        )
                    }
                    plain.endsWith("ون") || plain.endsWith("ان") -> Triple(
                        "فعل مضارع مرفوع وعلامة رفعه ثبوت النون",
                        "ثبوت النون",
                        "لأنه من الأفعال الخمسة ولم يسبقه ناصب ولا جازم"
                    )
                    else -> Triple(
                        "فعل مضارع مرفوع وعلامة رفعه الضمة الظاهرة",
                        "الضمة",
                        "لم يسبقه ناصب ولا جازم"
                    )
                }
                results.add(IrabWordResult(token, ArabicPosCategory.VERB, irabLine, signLine, reasonLine))
                prevWasNominalNoun = false
                continue
            }

            if (isExplicitPast) {
                isVerbalClause = true
                verbSeen = true
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.VERB,
                        irabStatement = "فعل ماضٍ مبني على الفتح الظاهر لا محل له من الإعراب",
                        sign = "الفتح الظاهر",
                        reason = "الفعل الماضي مبني دائماً، وهنا لم يتصل بآخره ضمير رفع"
                    )
                )
                prevWasNominalNoun = false
                continue
            }

            if (isExplicitImperative) {
                isVerbalClause = true
                verbSeen = true
                subjectFound = true // ضمير مستتر تقديره أنت
                results.add(
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.VERB,
                        irabStatement = "فعل أمر مبني على السكون، والفاعل ضمير مستتر تقديره أنت",
                        sign = "السكون",
                        reason = "فعل الأمر الصحيح الآخر يُبنى على السكون"
                    )
                )
                prevWasNominalNoun = false
                continue
            }

            // 4. Noun Syntactic Role Assignment
            val roleResult = when {
                hasAttachedPrep -> {
                    val prepChar = token.first()
                    expectPrepObject = false
                    prevWasNominalNoun = false
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "($prepChar) حرف جر مبني، وما بعدها اسم مجرور وعلامة جره الكسرة الظاهرة",
                        sign = "الكسرة",
                        reason = "دخول حرف الجر المتصل على الاسم المعرّف"
                    )
                }
                expectPrepObject -> {
                    expectPrepObject = false
                    prevWasNominalNoun = false
                    val (sign, detail) = if (plain.endsWith("ين")) {
                        "الياء" to "علامة جر المثنى أو جمع المذكر السالم"
                    } else {
                        "الكسرة الظاهرة" to "دخول حرف الجر عليه"
                    }
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "اسم مجرور وعلامة جره $sign",
                        sign = sign,
                        reason = detail
                    )
                }
                expectInnaNoun -> {
                    expectInnaNoun = false
                    expectInnaPred = true
                    prevWasNominalNoun = true
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "اسم إنّ منصوب وعلامة نصبه الفتحة الظاهرة",
                        sign = "الفتحة",
                        reason = "الحروف الناسخة تنصب المبتدأ ويُسمّى اسمها"
                    )
                }
                expectInnaPred -> {
                    expectInnaPred = false
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "خبر إنّ مرفوع وعلامة رفعه الضمة الظاهرة",
                        sign = "الضمة",
                        reason = "أتمّ معنى الجملة مع اسم إنّ"
                    )
                }
                expectKanaNoun -> {
                    expectKanaNoun = false
                    expectKanaPred = true
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "اسم كان مرفوع وعلامة رفعه الضمة الظاهرة",
                        sign = "الضمة",
                        reason = "الأفعال الناسخة ترفع المبتدأ ويُسمّى اسمها"
                    )
                }
                expectKanaPred -> {
                    expectKanaPred = false
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "خبر كان منصوب وعلامة نصبه الفتحة الظاهرة",
                        sign = "الفتحة",
                        reason = "الأفعال الناسخة تنصب الخبر"
                    )
                }
                plain in pronouns -> {
                    if (!verbSeen) prevWasNominalNoun = true
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = if (!verbSeen) "ضمير منفصل مبني في محل رفع مبتدأ" else "ضمير منفصل مبني في محل رفع",
                        sign = "البناء",
                        reason = "الضمائر المنفصلة أسماء معرفة مبنية"
                    )
                }
                plain in demonstratives -> {
                    if (!verbSeen) prevWasNominalNoun = true
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = if (!verbSeen) "اسم إشارة مبني في محل رفع مبتدأ" else "اسم إشارة مبني",
                        sign = "البناء",
                        reason = "أسماء الإشارة مبنية وتُعرب حسب موقعها"
                    )
                }
                plain in relatives -> {
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "اسم موصول مبني في محل رفع نعت",
                        sign = "البناء",
                        reason = "الأسماء الموصولة مبنية وتوضح الاسم المعرف قبلها"
                    )
                }
                isVerbalClause && !subjectFound -> {
                    subjectFound = true
                    val (sign, why) = if (plain.endsWith("ون")) {
                        "الواو" to "من قام بالفعل (جمع مذكر سالم)"
                    } else if (plain.endsWith("ان") && plain.length > 3) {
                        "الألف" to "من قام بالفعل (مثنى)"
                    } else {
                        "الضمة" to "من قام بالفعل"
                    }
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "فاعل مرفوع وعلامة رفعه $sign الظاهرة",
                        sign = sign,
                        reason = why
                    )
                }
                isVerbalClause && subjectFound -> {
                    val lastIsNominalAdj = startsWithAl && results.lastOrNull()?.irabStatement?.contains("فاعل") == true
                    if (lastIsNominalAdj) {
                        IrabWordResult(
                            word = token,
                            category = ArabicPosCategory.NOUN,
                            irabStatement = "نعت مرفوع وعلامة رفعه الضمة الظاهرة",
                            sign = "الضمة",
                            reason = "النعت يتبع المنعوت (الفاعل) في الرفع والتعريف"
                        )
                    } else if (startsWithAl && results.lastOrNull()?.irabStatement?.contains("مفعول به") == true) {
                        IrabWordResult(
                            word = token,
                            category = ArabicPosCategory.NOUN,
                            irabStatement = "مضاف إليه مجرور وعلامة جره الكسرة الظاهرة",
                            sign = "الكسرة",
                            reason = "معرفة أُضيفت إلى ما قبلها لتوضيحه"
                        )
                    } else {
                        IrabWordResult(
                            word = token,
                            category = ArabicPosCategory.NOUN,
                            irabStatement = "مفعول به منصوب وعلامة نصبه الفتحة الظاهرة",
                            sign = "الفتحة",
                            reason = "وقع عليه فعل الفاعل"
                        )
                    }
                }
                !verbSeen && !prevWasNominalNoun -> {
                    prevWasNominalNoun = true
                    val (sign, why) = if (plain.endsWith("ون")) {
                        "الواو" to "اسم معرفة بدأت به الجملة الاسمية (جمع مذكر سالم)"
                    } else {
                        "الضمة الظاهرة" to "اسم بدأت به الجملة الاسمية"
                    }
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "مبتدأ مرفوع وعلامة رفعه $sign",
                        sign = sign,
                        reason = why
                    )
                }
                !verbSeen && prevWasNominalNoun && startsWithAl && results.size == 1 -> {
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "نعت مرفوع وعلامة رفعه الضمة الظاهرة",
                        sign = "الضمة",
                        reason = "يتبع المبتدأ في الرفع والتعريف بأل"
                    )
                }
                else -> {
                    val (sign, why) = if (plain.endsWith("ون")) {
                        "الواو" to "اسم أتمّ معنى الجملة الاسمية (جمع مذكر سالم)"
                    } else {
                        "الضمة الظاهرة" to "الجزء الذي أتمّ معنى المبتدأ"
                    }
                    IrabWordResult(
                        word = token,
                        category = ArabicPosCategory.NOUN,
                        irabStatement = "خبر مرفوع وعلامة رفعه $sign",
                        sign = sign,
                        reason = why
                    )
                }
            }
            results.add(roleResult)
        }

        return IrabAnalysisResult(
            sentence = rawSentence.trim(),
            tokens = results,
            isAiPowered = false
        )
    }

    suspend fun analyzeWithCloudAiIfAvailable(
        context: Context,
        sentence: String
    ): IrabAnalysisResult = withContext(Dispatchers.IO) {
        val offlineFallback = analyzeOffline(sentence)
        val prompt = """
            أعرب كل كلمة من الجملة العربية التالية إعراباً نحوياً دقيقاً بصيغة JSON Array فقط بدون أي نص خارجي.
            كل عنصر في المصفوفة يجب أن يحتوي على:
            - "word": الكلمة كما هي بالتشكيل
            - "pos": "فعل" أو "اسم" أو "حرف"
            - "irab": الإعراب الكامل
            - "sign": علامة الإعراب
            - "reason": السبب النحوي المختصر

            الجملة: "$sentence"
        """.trimIndent()

        return@withContext try {
            val repo = FirebaseStudyChatRepository(context)
            val result = repo.askStudentAssistant(userMessage = prompt)
            val rawText = result.getOrNull()?.trim().orEmpty()
            val start = rawText.indexOf('[')
            val end = rawText.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val arr = JSONArray(rawText.substring(start, end + 1))
                val parsed = mutableListOf<IrabWordResult>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val word = obj.optString("word").trim()
                    val posRaw = obj.optString("pos").trim()
                    val cat = when {
                        posRaw.contains("فعل") -> ArabicPosCategory.VERB
                        posRaw.contains("حرف") -> ArabicPosCategory.PARTICLE
                        else -> ArabicPosCategory.NOUN
                    }
                    val irab = obj.optString("irab").trim()
                    val sign = obj.optString("sign").trim().ifBlank { "ظاهرة" }
                    val reason = obj.optString("reason").trim().ifBlank { "وفق قواعد النحو العربي" }
                    if (word.isNotBlank() && irab.isNotBlank()) {
                        parsed.add(IrabWordResult(word, cat, irab, sign, reason))
                    }
                }
                if (parsed.isNotEmpty()) {
                    IrabAnalysisResult(sentence = sentence.trim(), tokens = parsed, isAiPowered = true)
                } else {
                    offlineFallback
                }
            } else {
                offlineFallback
            }
        } catch (_: Throwable) {
            offlineFallback
        }
    }
}
