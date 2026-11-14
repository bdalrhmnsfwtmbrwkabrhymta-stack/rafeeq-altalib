package com.example.domain.english

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Data model for Alphabet Letters (A to Z) with Phonics & IPA
 */
data class AlphabetLetterItem(
    val letter: String,
    val uppercase: String,
    val lowercase: String,
    val ipa: String,
    val arabicExplanation: String,
    val exampleWord: String,
    val arabicMeaning: String,
    val exampleIpa: String = "",
    val soundBlending: String = "" // e.g. "ك + آ + ت"
)

/**
 * Data model for Phonics Blending Words (e.g. cat, bag, cab)
 */
data class PhonicsWordItem(
    val word: String,
    val ipa: String,
    val arabicMeaning: String,
    val soundBreakdownArabic: String, // e.g. "ك + آ + ت"
    val audioEndpoint: String = ""
)

/**
 * Data model for Sentence Lessons (e.g. "I am Ahmed. You are a student.")
 */
data class SentenceLessonItem(
    val sentence: String,
    val translationArabic: String,
    val grammarBreakdown: String, // e.g. "مين + فعل + اسم"
    val noteArabic: String = ""
)

/**
 * Slide types for the Interactive Lesson Player
 */
enum class LessonSlideType {
    EXPLANATION_AND_CARDS,
    INPUT_EXERCISE,
    PRONUNCIATION_SPEAKING
}

data class LessonSlideData(
    val stepIndex: Int,
    val totalSteps: Int,
    val lessonTitle: String,
    val slideType: LessonSlideType,
    val mainRuleTitle: String,
    val ruleBullets: List<String>,
    val pointsReward: Int = 3,
    val letterCards: List<AlphabetLetterItem> = emptyList(),
    val phonicsCards: List<PhonicsWordItem> = emptyList(),
    val sentenceCards: List<SentenceLessonItem> = emptyList(),
    // Exercise fields (matching Screenshot 3)
    val exercisePrompt: String = "",
    val exerciseHint: String = "",
    val expectedAnswer: String = "",
    val explanationOnWrong: String = "",
    // Simpler explanations for "مش فاهم" button
    val simpleExplanation: String = "",
    val realLifeAnalogy: String = ""
)

/**
 * Flashcard Model for Automated Mistake Pipeline
 */
data class FlashcardModel(
    val id: String,
    val frontText: String,
    val ipa: String,
    val arabicExplanation: String,
    val userSpeechLog: String = "",
    val mistakeCount: Int = 1,
    val dateAdded: Long = System.currentTimeMillis()
)

/**
 * Repository and Remote GitHub dataset fetcher with robust offline fallback
 */
object EnglishCurriculumRepository {

    private const val PREFS_NAME = "english_curriculum_prefs"
    private const val KEY_MISTAKES_JSON = "saved_mistakes_json"
    private const val KEY_STUDY_MINUTES = "target_study_minutes"

    // GitHub Raw URL constants (can be pointed to any public repo)
    private const val GITHUB_ALPHABET_URL = "https://raw.githubusercontent.com/datasets/english-curriculum/main/alphabet.json"
    private const val GITHUB_CEFR_URL = "https://raw.githubusercontent.com/datasets/english-curriculum/main/curriculum_cefr.json"

    // =========================================================================
    // 1. ALL 26 ENGLISH LETTERS (A - Z) WITH IPA & EXAMPLES
    // =========================================================================
    val ALL_26_LETTERS: List<AlphabetLetterItem> = listOf(
        AlphabetLetterItem("A", "A", "a", "/eɪ/", "الحرف ده اسمه «إيه»، ليه شكلين: كبير A وصغير a. الكبير بنستخدمه في أول الجملة وأول الأسماء، والصغير في باقي الكلمة.", "apple", "تفاحة", "/ˈæp.əl/", "أ + ب + ل"),
        AlphabetLetterItem("B", "B", "b", "/biː/", "الحرف ده اسمه «بي»، صوته زي الباء في العربي، وشكل b الصغير دائرته لليمين.", "bag", "شنطة", "/bæɡ/", "ب + آ + ج"),
        AlphabetLetterItem("C", "C", "c", "/siː/", "الحرف ده اسمه «سي». صوته في الغالب زي الكاف /k/ قبل a, o, u، وصوته س /s/ قبل e, i, y.", "cat", "قطة", "/kæt/", "ك + آ + ت"),
        AlphabetLetterItem("D", "D", "d", "/diː/", "الحرف ده اسمه «دي»، صوته دال واللسان على اللثة، وشكل d الصغير دائرته لليسار.", "dog", "كلب", "/dɒɡ/", "د + و + ج"),
        AlphabetLetterItem("E", "E", "e", "/iː/", "الحرف ده اسمه «إي»، وهو حرف متحرك مهم جداً، نطقه القصير فتحة مايلة للكسرة /e/.", "egg", "بيضة", "/eɡ/", "إ + ج"),
        AlphabetLetterItem("F", "F", "f", "/ef/", "الحرف ده اسمه «إف»، صوته فاء واضحة بخروج هواء بين الشفة السفلية والأسنان العليا.", "fish", "سمكة", "/fɪʃ/", "ف + ي + ش"),
        AlphabetLetterItem("G", "G", "g", "/dʒiː/", "الحرف ده اسمه «جي». صوته الشائع هو الجيم المصرية غير المعطشة /ɡ/ زي كلمة go.", "girl", "بنت", "/ɡɜːl/", "ج + ر + ل"),
        AlphabetLetterItem("H", "H", "h", "/eɪtʃ/", "الحرف ده اسمه «إتش»، صوته هاء خفيفة ونقية /h/ بتدفق هواء خفيف من الزور.", "hat", "قبعة", "/hæt/", "هـ + آ + ت"),
        AlphabetLetterItem("I", "I", "i", "/aɪ/", "الحرف ده اسمه «آي»، وهو الضمير أنا «I»، صوته القصير كسرة خفيفة /ɪ/ زي كلمة in.", "ink", "حبر", "/ɪŋk/", "إ + ن + ك"),
        AlphabetLetterItem("J", "J", "j", "/dʒeɪ/", "الحرف ده اسمه «جيه»، صوته دايماً جيم فصحى معطشة بتبدأ بدال خفيفة /dʒ/ زي jam.", "jam", "مربى", "/dʒæm/", "دج + آ + م"),
        AlphabetLetterItem("K", "K", "k", "/keɪ/", "الحرف ده اسمه «كيه»، صوته كاف صريحة بهواء خفيف /k/ زي kite.", "kite", "طائرة ورقية", "/kaɪt/", "ك + آي + ت"),
        AlphabetLetterItem("L", "L", "l", "/el/", "الحرف ده اسمه «إل»، صوته لام خفيفة في الأول وثقيلة في آخر الكلمات.", "leg", "رجل / ساق", "/leɡ/", "ل + ي + ج"),
        AlphabetLetterItem("M", "M", "m", "/em/", "الحرف ده اسمه «إم»، صوته ميم بالشفتين مغلقتين /m/ زي man.", "man", "رجل", "/mæn/", "م + آ + ن"),
        AlphabetLetterItem("N", "N", "n", "/en/", "الحرف ده اسمه «إن»، صوته نون واللسان على اللثة /n/ زي net.", "net", "شبكة", "/net/", "ن + ي + ت"),
        AlphabetLetterItem("O", "O", "o", "/əʊ/", "الحرف ده اسمه «أو»، صوته القصير ضمة مفخمة /ɒ/ زي on و hot.", "orange", "برتقالة", "/ˈɒr.ɪndʒ/", "أ + ر + ن + ج"),
        AlphabetLetterItem("P", "P", "p", "/piː/", "الحرف ده اسمه «پي»، صوته بـ خفيفة باندفاع هواء قوي /p/ لازم تحس بهوا على إيدك.", "pen", "قلم جاف", "/pen/", "پ + ي + ن"),
        AlphabetLetterItem("Q", "Q", "q", "/kjuː/", "الحرف ده اسمه «كيو»، غالباً بعده حرف u وينطقان معاً «كْو» /kw/ زي queen.", "queen", "ملكة", "/kwiːn/", "ك + و + ي + ن"),
        AlphabetLetterItem("R", "R", "r", "/ɑːr/", "الحرف ده اسمه «آر»، الراء الإنجليزية بدون تكرار اللسان لا يلمس سقف الحلق.", "red", "أحمر", "/red/", "ر + ي + د"),
        AlphabetLetterItem("S", "S", "s", "/es/", "الحرف ده اسمه «إس»، صوته سين صافية /s/ بدون اهتزاز في الأحبال الصوتية.", "sun", "شمس", "/sʌn/", "س + أ + ن"),
        AlphabetLetterItem("T", "T", "t", "/tiː/", "الحرف ده اسمه «تي»، صوته تاء واللسان على اللثة مع دفعة هواء خفيفة.", "tea", "شاي", "/tiː/", "ت + ي"),
        AlphabetLetterItem("U", "U", "u", "/juː/", "الحرف ده اسمه «يو»، صوته القصير فتحة سريعة مقفولة /ʌ/ زي cup و sun.", "umbrella", "شمسية", "/ʌmˈbrel.ə/", "أ + م + ب + ر + ل + ا"),
        AlphabetLetterItem("V", "V", "v", "/viː/", "الحرف ده اسمه «ڤي»، صوته فاء برنين واهتزاز الأحبال الصوتية /v/ زي van.", "van", "شاحنة صغيرة", "/væn/", "ڤ + آ + ن"),
        AlphabetLetterItem("W", "W", "w", "/ˈdʌb.əl.juː/", "الحرف ده اسمه «دبليو»، صوته واو ساكنة بالشفتين مستديرتين /w/ زي water.", "water", "ماء", "/ˈwɔː.tər/", "و + آ + ت + ر"),
        AlphabetLetterItem("X", "X", "x", "/eks/", "الحرف ده اسمه «إكس»، صوته كاف مع سين /ks/ زي box و fox.", "box", "صندوق", "/bɒks/", "ب + و + ك + س"),
        AlphabetLetterItem("Y", "Y", "y", "/waɪ/", "الحرف ده اسمه «واي»، صوته ياء ساكنة /j/ في أول الكلمة زي yes.", "yellow", "أصفر", "/ˈjel.əʊ/", "ي + ي + ل + و"),
        AlphabetLetterItem("Z", "Z", "z", "/zed/", "الحرف ده اسمه «زد» (أو زي بالأمريكي)، صوته زاي صافية برنين الأحبال الصوتية /z/.", "zoo", "حديقة حيوان", "/zuː/", "ز + و + و")
    )

    // =========================================================================
    // 2. INTERACTIVE LESSONS (MATCHING SCREENSHOTS 2, 3, 4, 5, 6)
    // =========================================================================

    /**
     * Lesson: حرف A (Matching Screenshot 6)
     */
    val LESSON_LETTER_A = listOf(
        LessonSlideData(
            stepIndex = 0,
            totalSteps = 5,
            lessonTitle = "حرف A",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "الحرف ده اسمه «إيه»",
            ruleBullets = listOf(
                "الحرف ده اسمه «إيه».",
                "ليه شكلين: كبير A وصغير a.",
                "الكبير بنستخدمه في أول الجملة وأول الأسماء، والصغير في باقي الكلمة."
            ),
            pointsReward = 3,
            letterCards = listOf(
                AlphabetLetterItem("A", "A", "a", "/eɪ/", "الشكل الكبير", "A", "الشكل الكبير"),
                AlphabetLetterItem("a", "A", "a", "/eɪ/", "الشكل الصغير", "a", "الشكل الصغير"),
                AlphabetLetterItem("apple", "A", "a", "/ˈæp.əl/", "تفاحة", "apple", "كلمة بتبدأ بيه")
            ),
            simpleExplanation = "زي الألف في العربي، حرف A هو البداية. شكله الكبير زي الخيمة A وشكله الصغير دائرة وعصاية صغيرة a.",
            realLifeAnalogy = "تخيل اسم «أحمد» بيبدأ بـ A كابيتال: Ahmed. وتفاحة بتبدأ بـ a سمول: apple."
        ),
        LessonSlideData(
            stepIndex = 1,
            totalSteps = 5,
            lessonTitle = "حرف A",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "تمرين كتابة الحرف الكبير",
            ruleBullets = listOf(
                "اكتب الحرف A الكبير (Capital) في المربع.",
                "لاحظ أن الحرف الكبير يستخدم دائماً في أول الجمل والأسماء."
            ),
            exercisePrompt = "اكتب حرف A الكبير",
            exerciseHint = "حرف A كابيتال",
            expectedAnswer = "A",
            explanationOnWrong = "الحرف الكبير يكتب بالشكل A (Capital)."
        ),
        LessonSlideData(
            stepIndex = 2,
            totalSteps = 5,
            lessonTitle = "حرف A",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "تمرين كتابة الحرف الصغير",
            ruleBullets = listOf("اكتب حرف a الصغير (Small)."),
            exercisePrompt = "اكتب حرف a الصغير",
            exerciseHint = "حرف a سمول",
            expectedAnswer = "a",
            explanationOnWrong = "الحرف الصغير يكتب بالشكل a (Small)."
        ),
        LessonSlideData(
            stepIndex = 3,
            totalSteps = 5,
            lessonTitle = "حرف A",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق كلمة apple",
            ruleBullets = listOf(
                "اضغط على المايك وانطق كلمة apple بوضوح.",
                "احرص على فتح الفم عند نطق صوت /æ/ في أول الكلمة."
            ),
            exercisePrompt = "انطق كلمة apple",
            expectedAnswer = "apple"
        ),
        LessonSlideData(
            stepIndex = 4,
            totalSteps = 5,
            lessonTitle = "حرف A",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "أحسنت! أتقنت حرف A",
            ruleBullets = listOf(
                "عرفت شكل الحرف الكبير A والصغير a.",
                "عرفت صوته وكلمة بتبدأ بيه (apple).",
                "أصبحت جاهزاً للانتقال إلى حرف B وحرف C وقراءة أولى الكلمات!"
            ),
            pointsReward = 5
        )
    )

    /**
     * Lesson: حرف C (Matching Screenshot 5)
     */
    val LESSON_LETTER_C = listOf(
        LessonSlideData(
            stepIndex = 0,
            totalSteps = 5,
            lessonTitle = "حرف C",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "الحرف ده اسمه «سي»",
            ruleBullets = listOf(
                "الحرف ده اسمه «سي».",
                "ليه شكلين: كبير C وصغير c.",
                "الكبير بنستخدمه في أول الجملة وأول الأسماء، والصغير في باقي الكلمة."
            ),
            pointsReward = 3,
            letterCards = listOf(
                AlphabetLetterItem("C", "C", "c", "/siː/", "الشكل الكبير", "C", "الشكل الكبير"),
                AlphabetLetterItem("c", "C", "c", "/siː/", "الشكل الصغير", "c", "الشكل الصغير"),
                AlphabetLetterItem("cat", "C", "c", "/kæt/", "قطة", "cat", "كلمة بتبدأ بيه")
            ),
            simpleExplanation = "حرف C شكله زي هلال القمر. اسمه «سي» لكن صوته في أغلب الكلمات بيتنطق كاف /k/ زي قطة cat وكابينة cab.",
            realLifeAnalogy = "تخيل كلمة Cairo (القاهرة) بتبدأ بـ C كابيتال، وكلمة cat بتبدأ بـ c سمول."
        ),
        LessonSlideData(
            stepIndex = 1,
            totalSteps = 5,
            lessonTitle = "حرف C",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "كتابة حرف C الكبير",
            ruleBullets = listOf("اكتب الحرف C الكبير (Capital)."),
            exercisePrompt = "اكتب حرف C الكبير",
            exerciseHint = "حرف C كابيتال",
            expectedAnswer = "C",
            explanationOnWrong = "اكتب الحرف كابيتال C."
        ),
        LessonSlideData(
            stepIndex = 2,
            totalSteps = 5,
            lessonTitle = "حرف C",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "كتابة حرف c الصغير",
            ruleBullets = listOf("اكتب حرف c الصغير (Small)."),
            exercisePrompt = "اكتب حرف c الصغير",
            exerciseHint = "حرف c سمول",
            expectedAnswer = "c",
            explanationOnWrong = "اكتب الحرف سمول c."
        ),
        LessonSlideData(
            stepIndex = 3,
            totalSteps = 5,
            lessonTitle = "حرف C",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق كلمة cat",
            ruleBullets = listOf(
                "اضغط على المايك وانطق كلمة cat بوضوح.",
                "صوت الحرف هنا هو /k/ (كاف صريحة)."
            ),
            exercisePrompt = "انطق كلمة cat",
            expectedAnswer = "cat"
        ),
        LessonSlideData(
            stepIndex = 4,
            totalSteps = 5,
            lessonTitle = "حرف C",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "مبروك! أتقنت حرف C",
            ruleBullets = listOf(
                "الآن يمكنك قراءة كلمات كاملة تجمع A و B و C!",
                "انتقل الآن إلى درس «أول تلات كلمات» لدمج الحروف وقراءتها."
            ),
            pointsReward = 5
        )
    )

    /**
     * Lesson: أول تلات كلمات (Matching Screenshot 4)
     */
    val LESSON_FIRST_THREE_WORDS = listOf(
        LessonSlideData(
            stepIndex = 0,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "تلات كلمات من الحروف اللي عرفتها",
            ruleBullets = listOf(
                "بحروف a و b و c وحرفين زيادة (t و g) تقدر تقرا تلات كلمات حقيقية.",
                "اقراها صوت صوت، وبعدين وصل الأصوات."
            ),
            pointsReward = 2,
            phonicsCards = listOf(
                PhonicsWordItem("cat", "/kæt/", "قطة", "ك + آ + ت"),
                PhonicsWordItem("bag", "/bæɡ/", "شنطة", "ب + آ + ج"),
                PhonicsWordItem("cab", "/kæb/", "تاكسي / كابينة", "ك + آ + ب")
            ),
            simpleExplanation = "التهجي في الإنجليزي زي العربي: بتنطق الحرف الأول، بعده الحرف الأوسط، بعده الأخير: كْ + آ + تْ = cat.",
            realLifeAnalogy = "زي ما بتقول في العربي «قَ + طَ + ة = قطة»، في الإنجليزي بنقول c (/k/) + a (/æ/) + t (/t/) = cat!"
        ),
        LessonSlideData(
            stepIndex = 1,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "كتابة كلمة قطة",
            ruleBullets = listOf("اكتب كلمة قطة بالإنجليزي (مكونة من حروف c, a, t)."),
            exercisePrompt = "اكتب «قطة» بالإنجليزي",
            exerciseHint = "تبدأ بـ c",
            expectedAnswer = "cat",
            explanationOnWrong = "كلمة قطة تكتب: cat."
        ),
        LessonSlideData(
            stepIndex = 2,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "كتابة كلمة شنطة",
            ruleBullets = listOf("اكتب كلمة شنطة بالإنجليزي (مكونة من b, a, g)."),
            exercisePrompt = "اكتب «شنطة» بالإنجليزي",
            exerciseHint = "تبدأ بـ b",
            expectedAnswer = "bag",
            explanationOnWrong = "كلمة شنطة تكتب: bag."
        ),
        LessonSlideData(
            stepIndex = 3,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق كلمة bag",
            ruleBullets = listOf("اضغط المايك وانطق كلمة bag بدقة."),
            exercisePrompt = "انطق كلمة bag",
            expectedAnswer = "bag"
        ),
        LessonSlideData(
            stepIndex = 4,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "كتابة كلمة كابينة أو تاكسي",
            ruleBullets = listOf("اكتب كلمة cab بالإنجليزي."),
            exercisePrompt = "اكتب «تاكسي / كاب» بالإنجليزي",
            exerciseHint = "c + a + b",
            expectedAnswer = "cab",
            explanationOnWrong = "تكتب: cab."
        ),
        LessonSlideData(
            stepIndex = 5,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق كلمة cab",
            ruleBullets = listOf("اضغط على المايك وانطق كلمة cab."),
            exercisePrompt = "انطق كلمة cab",
            expectedAnswer = "cab"
        ),
        LessonSlideData(
            stepIndex = 6,
            totalSteps = 7,
            lessonTitle = "أول تلات كلمات",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "إنجاز رائع! قرأت أول كلماتك",
            ruleBullets = listOf(
                "أتقنت قراءة cat و bag و cab بالدمج الصوتي.",
                "أنت الآن جاهز لتعلم الضمائر وتكوين أول جملة مفيدة!"
            ),
            pointsReward = 5
        )
    )

    /**
     * Lesson: الضمير I (Matching Screenshot 3)
     */
    val LESSON_PRONOUN_I = listOf(
        LessonSlideData(
            stepIndex = 0,
            totalSteps = 4,
            lessonTitle = "الضمير I",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "الضمير I يعني «أنا»",
            ruleBullets = listOf(
                "الضمير I يعني «أنا» في اللغة الإنجليزية.",
                "يكتب دائماً بحرف كبير (Capital) في أي مكان في الجملة سواء في الأول أو الوسط.",
                "يأتي دائماً مع فعل الكينونة am: فتقول «I am»."
            ),
            pointsReward = 3,
            sentenceCards = listOf(
                SentenceLessonItem("I", "أنا", "ضمير فاعل مفرد متكلم", "دائماً كابيتال"),
                SentenceLessonItem("I am", "أنا أكون", "فاعل + فعل كينونة", "اختصارها: I'm")
            ),
            simpleExplanation = "في العربي بتقول «أنا طالب». في الإنجليزي بتقول «I am a student» — الـ I هي أنا.",
            realLifeAnalogy = "زي ما بتقول أنا أحمد، بالإنجليزي: I am Ahmed."
        ),
        LessonSlideData(
            stepIndex = 1,
            totalSteps = 4,
            lessonTitle = "الضمير I",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "اكتب «أنا» بالإنجليزي (تطابق الشاشة ٣)",
            ruleBullets = listOf(
                "اكتب الضمير الذي يعني «أنا».",
                "تذكر: يكتب حرفاً كبيراً دائماً."
            ),
            exercisePrompt = "اكتب «أنا» بالإنجليزي",
            exerciseHint = "الضمير I (كابيتال)",
            expectedAnswer = "I",
            explanationOnWrong = "الضمير «أنا» هو حرف I كابيتال."
        ),
        LessonSlideData(
            stepIndex = 2,
            totalSteps = 4,
            lessonTitle = "الضمير I",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق الضمير I",
            ruleBullets = listOf("اضغط المايك وانطق صوت الضمير /aɪ/ (آي)."),
            exercisePrompt = "انطق: I",
            expectedAnswer = "I"
        ),
        LessonSlideData(
            stepIndex = 3,
            totalSteps = 4,
            lessonTitle = "الضمير I",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "أحسنت! أتقنت الضمير I",
            ruleBullets = listOf(
                "الآن ستتعلم كيف تربط I مع am لتكوين أول جملة كاملة: «أول جملة»."
            ),
            pointsReward = 4
        )
    )

    /**
     * Lesson: أول جملة (Matching Screenshot 2)
     */
    val LESSON_FIRST_SENTENCE = listOf(
        LessonSlideData(
            stepIndex = 0,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "الجملة الإنجليزية: مين + فعل + إيه",
            ruleBullets = listOf(
                "الجملة الإنجليزية ليها ترتيب ثابت: مين (I / You) ثم الفعل (am / are) ثم الباقي.",
                "وبتبدأ بحرف كبير وبتنتهي بنقطة.",
                "I am Ahmed. You are a student."
            ),
            pointsReward = 4,
            sentenceCards = listOf(
                SentenceLessonItem("I am Ahmed.", "أنا أحمد.", "مين + فعل + اسم", "جملة تقديم النفس"),
                SentenceLessonItem("You are a student.", "إنت طالب.", "a قبل الاسم", "اسم مفرد معدود")
            ),
            simpleExplanation = "في العربي ممكن تقول «أنا أحمد» من غير فعل. الإنجليزي مابيرضاش! لازم تحط الفعل am: يعني «أنا أكون أحمد».",
            realLifeAnalogy = "المعادلة الذهبية للإنجليزي: الفاعل (مين) + الفعل (بيعمل إيه) + المفعول (إيه)!"
        ),
        LessonSlideData(
            stepIndex = 1,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "ترتيب الجملة الأولى",
            ruleBullets = listOf("اكتب جملة «أنا أحمد» بالإنجليزي مع مراعاة النقطة والحرف الكبير."),
            exercisePrompt = "اكتب «I am Ahmed.» بالإنجليزي",
            exerciseHint = "I am Ahmed.",
            expectedAnswer = "I am Ahmed.",
            explanationOnWrong = "تكتب: I am Ahmed. مع حرف كبير ونقطة في النهاية."
        ),
        LessonSlideData(
            stepIndex = 2,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق الجملة الأولى",
            ruleBullets = listOf("اضغط على المايك وتحدث: «I am Ahmed» بطلاقة."),
            exercisePrompt = "انطق: I am Ahmed.",
            expectedAnswer = "I am Ahmed"
        ),
        LessonSlideData(
            stepIndex = 3,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.INPUT_EXERCISE,
            mainRuleTitle = "تكوين جملة You are",
            ruleBullets = listOf("اكتب «You are a student.» بالإنجليزي."),
            exercisePrompt = "اكتب «You are a student.»",
            exerciseHint = "You are a student.",
            expectedAnswer = "You are a student.",
            explanationOnWrong = "تكتب: You are a student."
        ),
        LessonSlideData(
            stepIndex = 4,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.PRONUNCIATION_SPEAKING,
            mainRuleTitle = "نطق جملة You are a student",
            ruleBullets = listOf("اضغط المايك وانطق: «You are a student»."),
            exercisePrompt = "انطق: You are a student.",
            expectedAnswer = "You are a student"
        ),
        LessonSlideData(
            stepIndex = 5,
            totalSteps = 6,
            lessonTitle = "أول جملة",
            slideType = LessonSlideType.EXPLANATION_AND_CARDS,
            mainRuleTitle = "مبارك! كونت أول جملة إنجليزية كاملة 🎓",
            ruleBullets = listOf(
                "أتقنت قاعدة: مين + فعل + إيه.",
                "عرفت استخدام I am و You are وأداة a.",
                "تم فتح الوحدات التالية في مسار التعلم بنجاح!"
            ),
            pointsReward = 6
        )
    )

    // =========================================================================
    // 3. GITHUB REPOSITORY SERVICE & CACHING
    // =========================================================================

    /**
     * Fetches Alphabet dataset from GitHub URL or returns local cache/fallback
     */
    suspend fun fetchAlphabetDataset(context: Context): List<AlphabetLetterItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_ALPHABET_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<AlphabetLetterItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AlphabetLetterItem(
                            letter = obj.optString("letter", ""),
                            uppercase = obj.optString("uppercase", ""),
                            lowercase = obj.optString("lowercase", ""),
                            ipa = obj.optString("ipa", ""),
                            arabicExplanation = obj.optString("arabicExplanation", ""),
                            exampleWord = obj.optString("exampleWord", ""),
                            arabicMeaning = obj.optString("arabicMeaning", "")
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            Log.d("GitHubRepoService", "GitHub raw fetch fallback to local: ${e.message}")
        }
        // Always safe: Return embedded complete 26 letters dataset!
        return@withContext ALL_26_LETTERS
    }

    // =========================================================================
    // 4. AUTOMATED MISTAKE-TO-FLASHCARD PIPELINE ("أخطائي" و "مركز المراجعة")
    // =========================================================================

    fun recordMistake(
        context: Context,
        frontText: String,
        ipa: String = "",
        arabicExplanation: String,
        userSpeechLog: String = ""
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingJson = prefs.getString(KEY_MISTAKES_JSON, "[]") ?: "[]"
            val array = JSONArray(existingJson)

            var found = false
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optString("frontText").equals(frontText, ignoreCase = true)) {
                    item.put("mistakeCount", item.optInt("mistakeCount", 1) + 1)
                    item.put("userSpeechLog", userSpeechLog)
                    found = true
                    break
                }
            }

            if (!found) {
                val newObj = JSONObject().apply {
                    put("id", "mistake_${System.currentTimeMillis()}")
                    put("frontText", frontText)
                    put("ipa", ipa)
                    put("arabicExplanation", arabicExplanation)
                    put("userSpeechLog", userSpeechLog)
                    put("mistakeCount", 1)
                    put("dateAdded", System.currentTimeMillis())
                }
                array.put(newObj)
            }

            prefs.edit().putString(KEY_MISTAKES_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e("MistakePipeline", "Error saving mistake: ${e.message}")
        }
    }

    fun getRecordedMistakes(context: Context): List<FlashcardModel> {
        val list = mutableListOf<FlashcardModel>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_MISTAKES_JSON, "[]") ?: "[]"
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FlashcardModel(
                        id = obj.optString("id", "m_$i"),
                        frontText = obj.optString("frontText", ""),
                        ipa = obj.optString("ipa", ""),
                        arabicExplanation = obj.optString("arabicExplanation", ""),
                        userSpeechLog = obj.optString("userSpeechLog", ""),
                        mistakeCount = obj.optInt("mistakeCount", 1),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getLessonSlidesForUnit(unitId: String): List<LessonSlideData> {
        return when {
            unitId.contains("letter_a", ignoreCase = true) || unitId.contains("حرف a", ignoreCase = true) -> LESSON_LETTER_A
            unitId.contains("letter_c", ignoreCase = true) || unitId.contains("حرف c", ignoreCase = true) -> LESSON_LETTER_C
            unitId.contains("first_words", ignoreCase = true) || unitId.contains("أول تلات كلمات", ignoreCase = true) -> LESSON_FIRST_THREE_WORDS
            unitId.contains("pronoun_i", ignoreCase = true) || unitId.contains("الضمير i", ignoreCase = true) -> LESSON_PRONOUN_I
            unitId.contains("first_sentence", ignoreCase = true) || unitId.contains("أول جملة", ignoreCase = true) -> LESSON_FIRST_SENTENCE
            else -> LESSON_LETTER_A
        }
    }
}
