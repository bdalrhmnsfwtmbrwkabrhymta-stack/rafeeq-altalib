package com.example.domain.english

import android.content.Context
import android.content.SharedPreferences

enum class EnglishLevelCategory(
    val code: Int,
    val cefrCode: String,
    val nameAr: String,
    val nameEn: String,
    val badgeIcon: String,
    val descriptionAr: String,
    val startingPointAr: String
) {
    LETTERS(
        code = 1,
        cefrCode = "Pre-A1",
        nameAr = "الحروف والأصوات التأسيسية",
        nameEn = "Alphabet & Phonics",
        badgeIcon = "🔤",
        descriptionAr = "المستوى التأسيسي من الصفر تماماً: يركز على إتقان أشكال الحروف الإنجليزية الكبيرة والصغيرة (A-Z)، والأصوات الساكنة والمتحركة (Vowels)، وقراءة الكلمات المكونة من 3 حروف.",
        startingPointAr = "البدء من الصفر بتعلم الحروف وكتابتها (درس حرف A والأبجدية)"
    ),
    A1(
        code = 2,
        cefrCode = "A1",
        nameAr = "مستوى مبتدئ (Beginner)",
        nameEn = "Beginner",
        badgeIcon = "🟢",
        descriptionAr = "مستوى المبتدئ: يركز على الكلمات اليومية الشائعة (الأرقام، الألوان، العائلة)، وضمائر الفاعل، وفعل الكينونة (Verb To Be: am/is/are)، وأدوات النكرة، وتكوين الجمل البسيطة.",
        startingPointAr = "البدء من المستوى A1 (المفردات الأساسية وتكوين الجمل البسيطة)"
    ),
    A2(
        code = 3,
        cefrCode = "A2",
        nameAr = "مستوى فوق المبتدئ (Elementary)",
        nameEn = "Elementary",
        badgeIcon = "🟡",
        descriptionAr = "مستوى فوق المبتدئ: يشمل صياغة أزمنة الماضي البسيط (Past Simple)، والسؤال والنفي، والمقارنة والتفضيل، وحروف الجر، والتعبير عن الروتين اليومي.",
        startingPointAr = "البدء من المستوى A2 (المحادثات اليومية والماضي البسيط)"
    ),
    B1(
        code = 4,
        cefrCode = "B1",
        nameAr = "مستوى متوسط (Intermediate)",
        nameEn = "Intermediate",
        badgeIcon = "🔵",
        descriptionAr = "مستوى المتوسط: يمكنك التعبير بطلاقة عن الخبرات، واستخدام المضارع التام (Present Perfect)، والجمل الشرطية (Conditionals)، وأدوات الربط، والمبني للمجهول (Passive).",
        startingPointAr = "البدء من المستوى B1 (القواعد المتقدمة والربط والطلاقة)"
    ),
    B2(
        code = 5,
        cefrCode = "B2",
        nameAr = "مستوى فوق المتوسط (Upper-Intermediate)",
        nameEn = "Upper-Intermediate",
        badgeIcon = "🟣",
        descriptionAr = "مستوى الطلاقة المتقدمة: استيعاب التعبيرات الاصطلاحية (Idioms)، والأفعال المركبة (Phrasal Verbs)، والمحادثات الرسمية والمهنية، والتمييز الدقيق بين المعاني.",
        startingPointAr = "البدء من المستوى B2 (المحادثة الاحترافية والإتقان الشامل)"
    )
}

data class PlacementQuestion(
    val id: Int, // 1 to 40
    val levelCategory: EnglishLevelCategory,
    val questionTitle: String,
    val questionSub: String = "",
    val options: List<String>,
    val correctAnswer: String,
    val explanationArabic: String = ""
)

data class LevelScore(
    val category: EnglishLevelCategory,
    val correctCount: Int,
    val totalCount: Int,
    val percentage: Int,
    val isPassed: Boolean // True if >= 70%
)

data class PlacementTestResult(
    val totalQuestions: Int = 40,
    val totalCorrect: Int,
    val overallPercentage: Int,
    val determinedLevel: EnglishLevelCategory,
    val levelBreakdown: List<LevelScore>,
    val unlockedLevels: List<EnglishLevelCategory>,
    val startRecommendation: String,
    val testTimestamp: Long = System.currentTimeMillis()
)

object EnglishPlacementTestData {

    const val PASSING_PERCENTAGE_THRESHOLD = 70 // 70% as specified in the screenshots

    val QUESTIONS_40: List<PlacementQuestion> = listOf(
        // =========================================================================
        // SECTION 1: LETTERS & PHONICS (PRE-A1) - Questions 1 to 8
        // =========================================================================
        PlacementQuestion(
            id = 1,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "أنهي واحد فيهم حرف A الكبير؟",
            questionSub = "",
            options = listOf("A", "a", "H", "V"),
            correctAnswer = "A",
            explanationArabic = "الحرف A هو الحرف الكبير (Capital) للحرف الأول في الأبجدية الإنجليزية."
        ),
        PlacementQuestion(
            id = 2,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "أنهي واحد فيهم حرف b الصغير؟",
            questionSub = "",
            options = listOf("d", "b", "p", "q"),
            correctAnswer = "b",
            explanationArabic = "الحرف b الصغير يتميز بأن دائرته متجهة لليمين، على عكس d المتجه لليسار."
        ),
        PlacementQuestion(
            id = 3,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "ما هو الحرف الأول لكلمة Apple؟",
            questionSub = "___pple",
            options = listOf("A", "E", "O", "B"),
            correctAnswer = "A",
            explanationArabic = "كلمة تفاحة (Apple) تبدأ بحرف A."
        ),
        PlacementQuestion(
            id = 4,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "أي حرف من الحروف التالية هو حرف متحرك (Vowel)؟",
            questionSub = "",
            options = listOf("T", "R", "E", "M"),
            correctAnswer = "E",
            explanationArabic = "الحروف المتحركة في الإنجليزية هي خمسة: A, E, I, O, U."
        ),
        PlacementQuestion(
            id = 5,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "أي من الحروف التالية يمثل حرف C الكبير؟",
            questionSub = "",
            options = listOf("C", "O", "G", "D"),
            correctAnswer = "C",
            explanationArabic = "الحرف C هو الحرف الثالث في الأبجدية الإنجليزية."
        ),
        PlacementQuestion(
            id = 6,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "ما هو الحرف الذي يأتي مباشرة بعد حرف M؟",
            questionSub = "K, L, M, ___",
            options = listOf("N", "L", "O", "P"),
            correctAnswer = "N",
            explanationArabic = "في الترتيب الأبجدي الإنجليزي: L, M, N, O."
        ),
        PlacementQuestion(
            id = 7,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "ما هو الحرف الصغير المقابل لحرف G الكبير؟",
            questionSub = "G -> ___",
            options = listOf("g", "q", "j", "y"),
            correctAnswer = "g",
            explanationArabic = "الحرف الصغير لحرف G هو g."
        ),
        PlacementQuestion(
            id = 8,
            levelCategory = EnglishLevelCategory.LETTERS,
            questionTitle = "أي كلمة من الكلمات الآتية تبدأ بصوت حرف B؟",
            questionSub = "",
            options = listOf("Book", "Cat", "Dog", "Sun"),
            correctAnswer = "Book",
            explanationArabic = "كلمة Book تبدأ بحرف B وتنطق بصوت /b/."
        ),

        // =========================================================================
        // SECTION 2: LEVEL A1 (BEGINNER) - Questions 9 to 16
        // =========================================================================
        PlacementQuestion(
            id = 9,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "اختر الترجمة الصحيحة لكلمة: Book",
            questionSub = "Book",
            options = listOf("كتاب", "قلم", "حقيبة", "مكتب"),
            correctAnswer = "كتاب",
            explanationArabic = "كلمة Book تعني كتاب باللغة العربية."
        ),
        PlacementQuestion(
            id = 10,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "اختر الفعل المناسب للجملة:",
            questionSub = "I ___ a student.",
            options = listOf("am", "is", "are", "be"),
            correctAnswer = "am",
            explanationArabic = "الضمير I يأخذ معه الفعل am في زمن المضارع البسيط."
        ),
        PlacementQuestion(
            id = 11,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "ما هو جمع كلمة: Cat (قطة)؟",
            questionSub = "One cat, two ___",
            options = listOf("Cats", "Caties", "Cates", "Caten"),
            correctAnswer = "Cats",
            explanationArabic = "الجمع المنتظم لمعظم الأسماء في الإنجليزية يكون بإضافة حرف s (Cats)."
        ),
        PlacementQuestion(
            id = 12,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "أحمد وعلي هنا. اختر الضمير المناسب:",
            questionSub = "___ are here.",
            options = listOf("They", "He", "She", "It"),
            correctAnswer = "They",
            explanationArabic = "للإشارة إلى جماعة الغائبين (أحمد وعلي) نستخدم ضمير الجمع They."
        ),
        PlacementQuestion(
            id = 13,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "اختر أداة النكرة المناسبة للكلمة:",
            questionSub = "This is ___ apple.",
            options = listOf("an", "a", "the", "some"),
            correctAnswer = "an",
            explanationArabic = "نستخدم an قبل الكلمات المفردة التي تبدأ بصوت متحرك مثل Apple."
        ),
        PlacementQuestion(
            id = 14,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "ما هو عكس كلمة: Big (كبير)؟",
            questionSub = "Big x ___",
            options = listOf("Small", "Tall", "Fast", "Hot"),
            correctAnswer = "Small",
            explanationArabic = "عكس كلمة Big (كبير) هو Small (صغير)."
        ),
        PlacementQuestion(
            id = 15,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "اختر الجملة المرتبة بشكل صحيح بالإنجليزية:",
            questionSub = "",
            options = listOf("She likes milk.", "Likes she milk.", "Milk she likes.", "She milk likes."),
            correctAnswer = "She likes milk.",
            explanationArabic = "ترتيب الجملة الإنجليزية الأساسي: فاعل (She) + فعل (likes) + مفعول (milk)."
        ),
        PlacementQuestion(
            id = 16,
            levelCategory = EnglishLevelCategory.A1,
            questionTitle = "ما معنى التحية اليومية: Good morning؟",
            questionSub = "Good morning!",
            options = listOf("صباح الخير", "مساء الخير", "تصبح على خير", "أهلاً بك"),
            correctAnswer = "صباح الخير",
            explanationArabic = "Good morning هي التحية الصباحية وتعني صباح الخير."
        ),

        // =========================================================================
        // SECTION 3: LEVEL A2 (ELEMENTARY) - Questions 17 to 24
        // =========================================================================
        PlacementQuestion(
            id = 17,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر الصيغة الصحيحة للماضي البسيط:",
            questionSub = "Yesterday, I ___ to school.",
            options = listOf("went", "go", "going", "goes"),
            correctAnswer = "went",
            explanationArabic = "الفعل الماضي غير المنتظم من go هو went مع الكلمة الدالة Yesterday."
        ),
        PlacementQuestion(
            id = 18,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "أكمل صيغة السؤال في المضارع البسيط:",
            questionSub = "Where ___ you live?",
            options = listOf("do", "does", "is", "are"),
            correctAnswer = "do",
            explanationArabic = "مع الضمير you نستخدم الفعل المساعد do في السؤال."
        ),
        PlacementQuestion(
            id = 19,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر حرف الجر المناسب للوقت والساعات:",
            questionSub = "The train arrives ___ 5 o'clock.",
            options = listOf("at", "on", "in", "to"),
            correctAnswer = "at",
            explanationArabic = "حرف الجر at يستخدم دائماً مع الساعات والأوقات الدقيقة."
        ),
        PlacementQuestion(
            id = 20,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر المقارنة الصحيحة بين صفتين:",
            questionSub = "An elephant is ___ than a cat.",
            options = listOf("bigger", "big", "more big", "biggest"),
            correctAnswer = "bigger",
            explanationArabic = "الصفات القصيرة مثل big تتضاعف وتضاف لها er عند المقارنة (bigger than)."
        ),
        PlacementQuestion(
            id = 21,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر الفعل الصحيح مع النفي:",
            questionSub = "She doesn't ___ coffee in the morning.",
            options = listOf("drink", "drinks", "drank", "drinking"),
            correctAnswer = "drink",
            explanationArabic = "بعد doesn't نرجع الفعل إلى أصله في المصدر (Infinitive) بدون إضافات."
        ),
        PlacementQuestion(
            id = 22,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "ما معنى جملة الطلب بأدب:",
            questionSub = "Can you help me please?",
            options = listOf("هل يمكنك مساعدتي من فضلك؟", "هل تريد مساعدة؟", "أنا أساعدك الآن", "شكراً على المساعدة"),
            correctAnswer = "هل يمكنك مساعدتي من فضلك؟",
            explanationArabic = "Can you help me please هي صيغة مهذبة لطلب المساعدة."
        ),
        PlacementQuestion(
            id = 23,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر الجملة المعبرة عن حدث يحدث الآن (المضارع المستمر):",
            questionSub = "",
            options = listOf("He is sleeping now.", "He sleeps now.", "He slept now.", "He will sleep now."),
            correctAnswer = "He is sleeping now.",
            explanationArabic = "المضارع المستمر يتكون من is/are/am + فعل ينتهي بـ ing للدلالة على حدث حالي."
        ),
        PlacementQuestion(
            id = 24,
            levelCategory = EnglishLevelCategory.A2,
            questionTitle = "اختر الكلمة التي تعني: دائماً (Adverb of frequency):",
            questionSub = "",
            options = listOf("Always", "Never", "Sometimes", "Rarely"),
            correctAnswer = "Always",
            explanationArabic = "كلمة Always تعني دائماً (نسبة 100%)."
        ),

        // =========================================================================
        // SECTION 4: LEVEL B1 (INTERMEDIATE) - Questions 25 to 32
        // =========================================================================
        PlacementQuestion(
            id = 25,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "اختر زمن المضارع التام (Present Perfect):",
            questionSub = "I have ___ in Cairo for three years.",
            options = listOf("lived", "live", "living", "lives"),
            correctAnswer = "lived",
            explanationArabic = "المضارع التام يتكون من have/has + التصريف الثالث للفعل (Past Participle)."
        ),
        PlacementQuestion(
            id = 26,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "اختر ضمير الوصل المناسب للعاقل (Relative Pronoun):",
            questionSub = "The doctor ___ treated me was very kind.",
            options = listOf("who", "which", "whose", "where"),
            correctAnswer = "who",
            explanationArabic = "نستخدم who للإشارة إلى الأسماء والصفات العاقلة (The doctor)."
        ),
        PlacementQuestion(
            id = 27,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "أكمل جملة الشرط الأولى (First Conditional):",
            questionSub = "If it rains tomorrow, we ___ at home.",
            options = listOf("will stay", "stayed", "stay", "have stayed"),
            correctAnswer = "will stay",
            explanationArabic = "قاعدة الحالة الشرطية الأولى: If + مضارع بسيط, فاعل + will + المصدر."
        ),
        PlacementQuestion(
            id = 28,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "اختر المعنى الدقيق لأداة الربط والتناقض: Although",
            questionSub = "Although it was cold, we went outside.",
            options = listOf("على الرغم من", "بسبب ذلك", "لذلك ونتيجة له", "علاوة على ذلك"),
            correctAnswer = "على الرغم من",
            explanationArabic = "أداة الربط Although تعني 'على الرغم من' وتفيد التناقض بين جملتين."
        ),
        PlacementQuestion(
            id = 29,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "اختر الفعل المناسب للإلزام والضرورة القانونية:",
            questionSub = "You ___ wear a seatbelt while driving.",
            options = listOf("must", "might", "can", "would"),
            correctAnswer = "must",
            explanationArabic = "الفعل must يعبر عن الإلزام القوي والضرورة الحتمية (Obligation)."
        ),
        PlacementQuestion(
            id = 30,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "أكمل الجملة بالصيغة المناسبة بعد فعل Avoid:",
            questionSub = "She avoided ___ with him.",
            options = listOf("arguing", "to argue", "argue", "argued"),
            correctAnswer = "arguing",
            explanationArabic = "الفعل avoid يتبعه دائماً اسم الفعل بصيغة Gerund (verb + ing)."
        ),
        PlacementQuestion(
            id = 31,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "اختر الجملة المبنية للمجهول (Passive Voice) السليمة:",
            questionSub = "",
            options = listOf(
                "The car was repaired yesterday.",
                "The car repaired yesterday.",
                "The car was repair yesterday.",
                "The car is repair yesterday."
            ),
            correctAnswer = "The car was repaired yesterday.",
            explanationArabic = "المبني للمجهول في الماضي يتكون من was/were + التصريف الثالث (was repaired)."
        ),
        PlacementQuestion(
            id = 32,
            levelCategory = EnglishLevelCategory.B1,
            questionTitle = "ما معنى التعبير الاصطلاحي الشهير: Piece of cake؟",
            questionSub = "That test was a piece of cake!",
            options = listOf("أمر سهل للغاية", "قطعة كعك شهية", "موقف صعب ومعقد", "وقت الاستراحة"),
            correctAnswer = "أمر سهل للغاية",
            explanationArabic = "تعبير Piece of cake هو Idiom إنجليزي شهير يعني أن الأمر سهل جداً وبسيط."
        ),

        // =========================================================================
        // SECTION 5: LEVEL B2 (UPPER-INTERMEDIATE) - Questions 33 to 40
        // =========================================================================
        PlacementQuestion(
            id = 33,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "اختر الفعل المركب (Phrasal Verb) بمعنى تأجيل:",
            questionSub = "We had to ___ the meeting until next week.",
            options = listOf("put off", "call off", "look up", "give up"),
            correctAnswer = "put off",
            explanationArabic = "الفعل المركب Put off يعني تأجيل (Postpone)، بينما Call off تعني إلغاء."
        ),
        PlacementQuestion(
            id = 34,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "اختر صيغة الشرط الثالثة للتعبير عن الندم في الماضي:",
            questionSub = "If you had studied harder, you ___ the exam.",
            options = listOf("would have passed", "will pass", "would pass", "passed"),
            correctAnswer = "would have passed",
            explanationArabic = "قاعدة الحالة الشرطية الثالثة (Third Conditional): If + had + P.P -> would have + P.P."
        ),
        PlacementQuestion(
            id = 35,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "أكمل الجملة بالصيغة السليمة بعد Despite:",
            questionSub = "Despite ___ hard, he didn't win the race.",
            options = listOf("training", "trained", "train", "he trains"),
            correctAnswer = "training",
            explanationArabic = "كلمة Despite يأتي بعدها اسم أو Gerund (فعل ينتهي بـ ing) وليس جملة كاملة."
        ),
        PlacementQuestion(
            id = 36,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "اختر المرادف الأقرب لكلمة: Accurate (دقيق)",
            questionSub = "Accurate = ___",
            options = listOf("Precise", "Vague", "Superficial", "Careless"),
            correctAnswer = "Precise",
            explanationArabic = "كلمة Accurate تعني دقيق ومضبوط، والمرادف المطابق لها هو Precise."
        ),
        PlacementQuestion(
            id = 37,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "أكمل السؤال المذيل (Tag Question) المناسب:",
            questionSub = "She hardly ever complains, ___?",
            options = listOf("does she?", "doesn't she?", "is she?", "isn't she?"),
            correctAnswer = "does she?",
            explanationArabic = "كلمة Hardly تعطي معنى النفي للجملة، ولذلك يكون السؤال المذيل مثبتاً (does she?)."
        ),
        PlacementQuestion(
            id = 38,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "اختر الصياغة للتعبير عن أمنية حول حدث في الماضي:",
            questionSub = "I wish I ___ that mistake yesterday.",
            options = listOf("hadn't made", "didn't make", "wouldn't make", "haven't made"),
            correctAnswer = "hadn't made",
            explanationArabic = "للتعبير عن الندم أو التمني بخصوص الماضي نستخدم Wish متبوعة بالماضي التام (Past Perfect)."
        ),
        PlacementQuestion(
            id = 39,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "ما معنى كلمة: Reluctant في السياق اللغوي؟",
            questionSub = "He was reluctant to share his secret.",
            options = listOf("متردد أو غير راغب", "متحمس وشغوف", "واثق من قدراته", "سريع الغضب"),
            correctAnswer = "متردد أو غير راغب",
            explanationArabic = "كلمة Reluctant تعني متردداً أو كاره وغير راغب في فعل شيء (Unwilling)."
        ),
        PlacementQuestion(
            id = 40,
            levelCategory = EnglishLevelCategory.B2,
            questionTitle = "اختر الجملة الأكثر بلاغة ورسمية (Formal English):",
            questionSub = "",
            options = listOf(
                "I would appreciate it if you could assist me.",
                "Help me out right now please.",
                "Give me a hand with this stuff.",
                "I wanna some help from you."
            ),
            correctAnswer = "I would appreciate it if you could assist me.",
            explanationArabic = "صيغة 'I would appreciate it if you could assist me' هي الصياغة الرسمية الأكاديمية والمهنية الأنسب."
        )
    )

    /**
     * Calculates the official CEFR level and unlocked stages from the 40 questions.
     * Rule: Each tier has 8 questions. A tier is passed if score >= 70% (at least 6/8 = 75%).
     * If user fails Letters (<70%) -> Level is LETTERS (Starts from alphabet).
     * If user passes Letters and fails A1 -> Level is A1.
     * If user passes A1 and fails A2 -> Level is A2.
     * If user passes A2 and fails B1 -> Level is B1.
     * If user passes B1 and fails B2 -> Level is B2.
     * If user passes B2 -> Level is B2 (Mastery!).
     */
    fun evaluateAssessment(answers: Map<Int, String>): PlacementTestResult {
        var totalCorrect = 0
        val breakdownList = mutableListOf<LevelScore>()

        EnglishLevelCategory.entries.forEach { category ->
            val tierQuestions = QUESTIONS_40.filter { it.levelCategory == category }
            var categoryCorrect = 0
            tierQuestions.forEach { q ->
                val userAns = answers[q.id]
                if (userAns == q.correctAnswer) {
                    categoryCorrect++
                    totalCorrect++
                }
            }
            val percentage = if (tierQuestions.isNotEmpty()) (categoryCorrect * 100) / tierQuestions.size else 0
            val isPassed = percentage >= PASSING_PERCENTAGE_THRESHOLD
            breakdownList.add(
                LevelScore(
                    category = category,
                    correctCount = categoryCorrect,
                    totalCount = tierQuestions.size,
                    percentage = percentage,
                    isPassed = isPassed
                )
            )
        }

        val lettersScore = breakdownList.find { it.category == EnglishLevelCategory.LETTERS }
        val a1Score = breakdownList.find { it.category == EnglishLevelCategory.A1 }
        val a2Score = breakdownList.find { it.category == EnglishLevelCategory.A2 }
        val b1Score = breakdownList.find { it.category == EnglishLevelCategory.B1 }
        val b2Score = breakdownList.find { it.category == EnglishLevelCategory.B2 }

        val determinedLevel: EnglishLevelCategory
        val unlockedLevels = mutableListOf<EnglishLevelCategory>()

        if (lettersScore?.isPassed != true) {
            // Didn't pass Letters (<70%) -> Start from the very beginning (Alphabet)
            determinedLevel = EnglishLevelCategory.LETTERS
            unlockedLevels.add(EnglishLevelCategory.LETTERS)
        } else if (a1Score?.isPassed != true) {
            // Passed Letters, failed A1 -> Start at A1, Letters is cleared!
            determinedLevel = EnglishLevelCategory.A1
            unlockedLevels.add(EnglishLevelCategory.LETTERS)
            unlockedLevels.add(EnglishLevelCategory.A1)
        } else if (a2Score?.isPassed != true) {
            // Passed Letters & A1, failed A2 -> Start at A2, Letters & A1 cleared!
            determinedLevel = EnglishLevelCategory.A2
            unlockedLevels.add(EnglishLevelCategory.LETTERS)
            unlockedLevels.add(EnglishLevelCategory.A1)
            unlockedLevels.add(EnglishLevelCategory.A2)
        } else if (b1Score?.isPassed != true) {
            // Passed up to A2, failed B1 -> Start at B1
            determinedLevel = EnglishLevelCategory.B1
            unlockedLevels.add(EnglishLevelCategory.LETTERS)
            unlockedLevels.add(EnglishLevelCategory.A1)
            unlockedLevels.add(EnglishLevelCategory.A2)
            unlockedLevels.add(EnglishLevelCategory.B1)
        } else {
            // Passed B1 or B2 -> Advanced Level B2
            determinedLevel = EnglishLevelCategory.B2
            unlockedLevels.addAll(EnglishLevelCategory.entries)
        }

        val overallPercentage = (totalCorrect * 100) / QUESTIONS_40.size

        val recommendation = when (determinedLevel) {
            EnglishLevelCategory.LETTERS -> "بناءً على نتيجتك، يوصى بالبدء مباشرة من مرحلة الحروف والأصوات الأبجدية (حرف A والأصوات الأساسية) لتثبيت مخارج الحروف وأشكالها."
            EnglishLevelCategory.A1 -> "أحسنت! أتقنت الحروف بنجاح. ستبدأ الآن من مرحلة A1 لبناء المفردات وتكوين أولى جملك المفيدة."
            EnglishLevelCategory.A2 -> "ممتاز! تم تجاوز مرحلتي الحروف وA1 وفتح المستوى A2 لك لتبدأ المحادثات والروتين اليومي وأزمنة الماضي."
            EnglishLevelCategory.B1 -> "مستوى رائع جداً! تم فتح المستويات التأسيسية بالكامل، ومستواك المعتمد هو B1 المتوسط لبناء الجمل المعقدة والطلاقة."
            EnglishLevelCategory.B2 -> "ما شاء الله! مستواك متقدم (B2)، تم فتح جميع المراحل والوحدات المتقدمة لتطوير المحادثة والطلاقة الشاملة."
        }

        return PlacementTestResult(
            totalQuestions = QUESTIONS_40.size,
            totalCorrect = totalCorrect,
            overallPercentage = overallPercentage,
            determinedLevel = determinedLevel,
            levelBreakdown = breakdownList,
            unlockedLevels = unlockedLevels,
            startRecommendation = recommendation
        )
    }

    private const val PREFS_NAME = "english_placement_prefs"
    private const val KEY_USER_LEVEL_CODE = "user_level_code"
    private const val KEY_OVERALL_PERCENTAGE = "overall_percentage"
    private const val KEY_TOTAL_CORRECT = "total_correct"
    private const val KEY_TEST_TIMESTAMP = "test_timestamp"
    private const val KEY_IS_TEST_COMPLETED = "is_test_completed"

    fun savePlacementResult(context: Context, result: PlacementTestResult) {
        try {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_USER_LEVEL_CODE, result.determinedLevel.code)
                .putInt(KEY_OVERALL_PERCENTAGE, result.overallPercentage)
                .putInt(KEY_TOTAL_CORRECT, result.totalCorrect)
                .putLong(KEY_TEST_TIMESTAMP, result.testTimestamp)
                .putBoolean(KEY_IS_TEST_COMPLETED, true)
                .apply()
        } catch (_: Exception) {}
    }

    fun getSavedLevelCode(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_USER_LEVEL_CODE, 0)
        } catch (_: Exception) { 0 }
    }

    fun getSavedOverallPercentage(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_OVERALL_PERCENTAGE, 0)
        } catch (_: Exception) { 0 }
    }

    fun isTestCompleted(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_IS_TEST_COMPLETED, false)
        } catch (_: Exception) { false }
    }
}
