package com.example.domain.english

data class ReviewDueItem(
    val id: Int, // 1 to 80
    val englishSentence: String,
    val arabicPrompt: String,
    val hintArabic: String,
    val levelCode: String // Pre-A1, A1, A2, B1, B2
)

object EnglishReviewDueData {
    val ITEMS_80: List<ReviewDueItem> = listOf(
        // Item 1 (Matching Screenshot 3)
        ReviewDueItem(1, "I am a teacher", "أنا مدرس", "استخدم I am مع المهن المفردة", "A1"),
        ReviewDueItem(2, "She is a doctor", "هي طبيبة", "استخدم She is مع المهنة المفردة المؤنثة", "A1"),
        ReviewDueItem(3, "He is a student", "هو طالب", "He للمفرد المذكر الغائب", "A1"),
        ReviewDueItem(4, "This is my book", "هذا كتابي", "This للإشارة للقريب المفرد", "A1"),
        ReviewDueItem(5, "That is an apple", "تلك تفاحة", "an قبل الكلمات البادئة بحرف متحرك", "Pre-A1"),
        ReviewDueItem(6, "They are at school", "هم في المدرسة", "They مع الجمع وفعل are", "A1"),
        ReviewDueItem(7, "We have a cat", "نحن لدينا قطة", "have مع ضمائر الجمع وI/We", "A1"),
        ReviewDueItem(8, "The sun is hot", "الشمس حارة", "hot صفة تأتي بعد is", "Pre-A1"),
        ReviewDueItem(9, "I drink water", "أنا أشرب ماء", "مضارع بسيط يعبر عن عادة", "A1"),
        ReviewDueItem(10, "She speaks English", "هي تتحدث الإنجليزية", "إضافة s مع المفرد الغائب", "A1"),
        ReviewDueItem(11, "Good morning to you", "صباح الخير لك", "تحية صباحية شائعة", "Pre-A1"),
        ReviewDueItem(12, "Where is my bag?", "أين حقيبتي؟", "Where للسؤال عن المكان", "A1"),
        ReviewDueItem(13, "Open the door please", "افتح الباب من فضلك", "فعل أمر مهذب في المصدر", "Pre-A1"),
        ReviewDueItem(14, "Close the window", "أغلق النافذة", "صيغة طلب مباشرة", "Pre-A1"),
        ReviewDueItem(15, "I like ice cream", "أنا أحب المثلجات", "like للتعبير عن التفضيل", "A1"),
        ReviewDueItem(16, "He has three pens", "هو يملك ثلاثة أقلام", "has مع المفرد الغائب He", "A1"),
        ReviewDueItem(17, "What time is it?", "كم الساعة الآن؟", "سؤال الوقت الأساسي", "A1"),
        ReviewDueItem(18, "It is ten o'clock", "إنها الساعة العاشرة", "o'clock للساعات المضبوطة", "A1"),
        ReviewDueItem(19, "My father is tall", "والدي طويل القامة", "الصفة تصف الاسم بعد is", "A1"),
        ReviewDueItem(20, "This car is fast", "هذه السيارة سريعة", "fast صفة للسرعة", "A1"),

        // Items 21 to 40 (A2 level)
        ReviewDueItem(21, "Yesterday I went home", "بالأمس ذهبت للمنزل", "went ماضي الفعل go", "A2"),
        ReviewDueItem(22, "She arrived at noon", "هي وصلت وقت الظهيرة", "at مع الأوقات المحددة", "A2"),
        ReviewDueItem(23, "Can you help me?", "هل يمكنك مساعدتي؟", "Can للطلب بأدب", "A2"),
        ReviewDueItem(24, "I did not see him", "أنا لم أره", "did not للنفي في الماضي", "A2"),
        ReviewDueItem(25, "He was very tired", "هو كان متعباً جداً", "was ماضي is للمفرد", "A2"),
        ReviewDueItem(26, "We were at the park", "كنا في الحديقة العامة", "were ماضي are للجمع", "A2"),
        ReviewDueItem(27, "She is sleeping now", "هي نائمة الآن", "مضارع مستمر لحدث حالي", "A2"),
        ReviewDueItem(28, "They are playing tennis", "هم يلعبون التنس", "are + verb + ing", "A2"),
        ReviewDueItem(29, "The weather is cloudy", "الطقس غائم اليوم", "cloudy صفة تعني غائم", "A2"),
        ReviewDueItem(30, "I will call you tomorrow", "سأتصل بك غداً", "will للمستقبل البسيط", "A2"),
        ReviewDueItem(31, "How much does it cost?", "كم يبلغ ثمن هذا؟", "How much للسعر والكمية", "A2"),
        ReviewDueItem(32, "This jacket is cheaper", "هذا المعطف أرخص سعراً", "er للمقارنة بين شيئين", "A2"),
        ReviewDueItem(33, "Cairo is a big city", "القاهرة مدينة كبرى", "اسم موصوف مع a", "A2"),
        ReviewDueItem(34, "He works at a bank", "هو يعمل في بنك", "works مع He في المضارع", "A2"),
        ReviewDueItem(35, "I never eat late", "أنا لا آكل متأخراً أبداً", "never تفيد النفي التام", "A2"),
        ReviewDueItem(36, "She usually drinks tea", "هي عادة تشرب الشاي", "ظرف تكرار قبل الفعل", "A2"),
        ReviewDueItem(37, "Turn left at the bank", "انعطف يساراً عند البنك", "وصف الاتجاهات في الشارع", "A2"),
        ReviewDueItem(38, "Walk straight ahead", "امشِ للأمام مباشرة", "straight تعني للأمام", "A2"),
        ReviewDueItem(39, "I bought a new shirt", "اشتريت قميصاً جديداً", "bought ماضي buy", "A2"),
        ReviewDueItem(40, "Could you repeat that?", "هل بالإمكان إعادة ذلك؟", "طلب مهذب جداً بـ Could", "A2"),

        // Items 41 to 60 (B1 level)
        ReviewDueItem(41, "I have lived here for years", "عشت هنا لسنوات", "مضارع تام يدل على استمرار", "B1"),
        ReviewDueItem(42, "She has already left", "هي غادرت بالفعل", "already تأتي بين has والتصريف الثالث", "B1"),
        ReviewDueItem(43, "If it rains we stay", "إذا أمطرت نبقى بالداخل", "حالة شرطية أولى", "B1"),
        ReviewDueItem(44, "The letter was sent", "الخطاب تم إرساله", "مبني للمجهول في الماضي", "B1"),
        ReviewDueItem(45, "He must wear a helmet", "يجب عليه ارتداء الخوذة", "must تفيد الإلزام الضروري", "B1"),
        ReviewDueItem(46, "You should sleep early", "ينبغي عليك النوم مبكراً", "should لتقديم النصيحة", "B1"),
        ReviewDueItem(47, "Although it was cold we walked", "رغم البرد سرنا", "Although تفيد التناقض", "B1"),
        ReviewDueItem(48, "The man who called is kind", "الرجل الذي اتصل طيب", "who ضمير وصل للعاقل", "B1"),
        ReviewDueItem(49, "I enjoy reading books", "أنا أستمتع بقراءة الكتب", "enjoy يتبعه verb + ing", "B1"),
        ReviewDueItem(50, "She decided to study abroad", "هي قررت الدراسة بالخارج", "decide يتبعه to + infinitive", "B1"),
        ReviewDueItem(51, "We ran out of coffee", "نفد ما لدينا من القهوة", "run out of فعل مركب", "B1"),
        ReviewDueItem(52, "Can you pick me up?", "هل يمكنك أن تقلني بسيارتك؟", "pick up بمعنى يقل أحداً", "B1"),
        ReviewDueItem(53, "The exam was a piece of cake", "كان الامتحان سهلاً للغاية", "تعبير اصطلاحي يدل على السهولة", "B1"),
        ReviewDueItem(54, "I am looking forward to seeing you", "أتطلع شوقاً لرؤيتك", "looking forward to + ing", "B1"),
        ReviewDueItem(55, "He denied stealing the money", "هو أنكر سرقة النقود", "deny يتبعه gerund", "B1"),
        ReviewDueItem(56, "The project is nearly finished", "المشروع شارف على الانتهاء", "nearly تعني تقريباً", "B1"),
        ReviewDueItem(57, "I would rather stay home", "أفضل البقاء في المنزل", "would rather للمفاضلة", "B1"),
        ReviewDueItem(58, "Neither Ali nor Omar came", "لا علي ولا عمر حضر", "Neither ... nor نفي ثنائي", "B1"),
        ReviewDueItem(59, "The flight was delayed", "تأخرت رحلة الطيران", "مبني للمجهول مع delay", "B1"),
        ReviewDueItem(60, "Take your umbrella just in case", "خذ مظلتك تحسباً للظروف", "just in case تحوطاً", "B1"),

        // Items 61 to 80 (B2 level)
        ReviewDueItem(61, "We had to put off the meeting", "اضطررنا لتأجيل الاجتماع", "put off تعني تأجيل", "B2"),
        ReviewDueItem(62, "He came up with a great solution", "هو توصل إلى حل عبقري", "come up with يبتكر فكرة", "B2"),
        ReviewDueItem(63, "If I had known I would have helped", "لو كنت أعلم لساعدتك", "حالة شرطية ثالثة للندم", "B2"),
        ReviewDueItem(64, "Despite working hard he failed", "رغم عمله الشاق لم ينجح", "Despite يتبعها Gerund", "B2"),
        ReviewDueItem(65, "She is reluctant to change her mind", "هي مترددة في تغيير رأيها", "reluctant غير راغب", "B2"),
        ReviewDueItem(66, "I wish I had studied harder", "أتمنى لو كنت اجتهدت أكثر", "wish + past perfect", "B2"),
        ReviewDueItem(67, "Hardly had we arrived when it rained", "ما كدنا نصل حتى أمطرت", "أسلوب توكيد بالتقديم", "B2"),
        ReviewDueItem(68, "You have not seen him have you?", "لم تره أليس كذلك؟", "سؤال مذيل منفي ومثبت", "B2"),
        ReviewDueItem(69, "His explanation was very vague", "كان تفسيره غامضاً وغير دقيق", "vague مبهم وغير واضح", "B2"),
        ReviewDueItem(70, "I appreciate your prompt assistance", "أقدر مساعدتك الفورية السريعة", "أسلوب مخاطبة رسمي", "B2"),
        ReviewDueItem(71, "The decision is subject to approval", "القرار مرهون بالموافقة", "صيغة قانونية وإدارية", "B2"),
        ReviewDueItem(72, "He took the compliments for granted", "اعتبر الإطراء أمراً مفروغاً منه", "take for granted", "B2"),
        ReviewDueItem(73, "It goes without saying that health matters", "غني عن البيان أن الصحة مهمة", "تعبير بلاغي معروف", "B2"),
        ReviewDueItem(74, "She has an eye for detail", "لديها دقة ملاحظة فائقة", "idiom للدقة العالية", "B2"),
        ReviewDueItem(75, "Under no circumstances should you open it", "تحت أي ظرف لا تفتحها", "تقديم النفي للتوكيد", "B2"),
        ReviewDueItem(76, "We reached a mutual agreement", "توصلنا لاتفاق متبادل", "mutual تعني متبادلاً", "B2"),
        ReviewDueItem(77, "The results exceeded all expectations", "فاقت النتائج كل التوقعات", "exceed يتجاوز ويفوق", "B2"),
        ReviewDueItem(78, "He is prone to making hasty choices", "هو عرضة لاتخاذ قرارات متسرعة", "prone to عرضة لـ", "B2"),
        ReviewDueItem(79, "To put it mildly it was a disaster", "دون مبالغة كان الأمر كارثة", "تعبير تلطيف للأمر", "B2"),
        ReviewDueItem(80, "I look forward to our fruitful collaboration", "أتطلع لتعاوننا المثمر", "خاتمة رسمية مهنية", "B2")
    )

    fun calculateSpeechSimilarity(recognized: String, expected: String): Float {
        val cleanRec = recognized.lowercase().replace("[^a-z0-9 ]".toRegex(), "").trim()
        val cleanExp = expected.lowercase().replace("[^a-z0-9 ]".toRegex(), "").trim()
        if (cleanRec == cleanExp) return 1.0f
        if (cleanRec.isEmpty() || cleanExp.isEmpty()) return 0.0f
        
        val recWords = cleanRec.split("\\s+".toRegex()).toSet()
        val expWords = cleanExp.split("\\s+".toRegex()).toSet()
        val intersection = recWords.intersect(expWords).size
        val union = recWords.union(expWords).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
    }
}
