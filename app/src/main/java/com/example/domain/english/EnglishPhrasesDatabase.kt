package com.example.domain.english

data class EnglishPhraseEntry(
    val id: Int,
    val english: String,
    val pronunciationAr: String,
    val meaningAr: String,
    val section: String,
    val subSection: String,
    val category: String,
    val note: String = ""
)

object EnglishPhrasesDatabase {

    val allPhrases: List<EnglishPhraseEntry> by lazy {
        buildComprehensiveList()
    }

    val sectionsList: List<String> by lazy {
        allPhrases.map { it.section }.distinct()
    }

    private fun buildComprehensiveList(): List<EnglishPhraseEntry> {
        val list = mutableListOf<EnglishPhraseEntry>()
        var counter = 1

        fun add(
            english: String,
            pronunciationAr: String,
            meaningAr: String,
            section: String,
            subSection: String,
            note: String = ""
        ) {
            list.add(
                EnglishPhraseEntry(
                    id = counter++,
                    english = english,
                    pronunciationAr = pronunciationAr,
                    meaningAr = meaningAr,
                    section = section,
                    subSection = subSection,
                    category = section,
                    note = note
                )
            )
        }

        // ==========================================
        // 1. قسم: Greetings / التحيات
        // ==========================================
        val sec1 = "1. Greetings / التحيات"

        // Simple Greeting / تحية بسيطة
        add("Hi!", "هاي", "هاي.", sec1, "Simple Greeting / تحية بسيطة")
        add("Hello!", "هالو", "مرحباً.", sec1, "Simple Greeting / تحية بسيطة")
        add("Hello there!", "هالو ذير", "أهلاً بك.", sec1, "Simple Greeting / تحية بسيطة")
        add("Howdy!", "هاودي", "أهلاً.", sec1, "Simple Greeting / تحية بسيطة")
        add("Hey!", "هاي", "هاي.", sec1, "Simple Greeting / تحية بسيطة")
        add("Yo!", "يو", "أهلاً.", sec1, "Simple Greeting / تحية بسيطة")

        // General Greetings & Responses / تحية عامة والردود عليها
        add("How are you?", "هاو آر يو؟", "كيف حالك؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("How's it going?", "هاوز إت جوينج؟", "كيف الحال؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("How's it been?", "هاوز إت بين؟", "كيف كان الحال؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("How is everything?", "هاو إز إفريثينج؟", "كيف تجري الأمور؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("How've you been?", "هاوف يو بين؟", "كيف كان حالك؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("How you been?", "هاو يو بين؟", "كيف كان حالك؟ (غير رسمي)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "غير رسمي")
        add("How's tricks?", "هاوز تريكس؟", "كيف الحال؟ (غير رسمي)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "غير رسمي")
        add("What have you been up to?", "وات هاف يو بين أب تو؟", "ما الذي تفعله الآن؟", sec1, "General Greetings & Responses / تحية عامة والردود عليها")
        add("What's new?", "واتس نيو؟", "ما الجديد؟ (غير رسمي)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "غير رسمي")
        add("What's up?", "واتس أب؟", "ما الجديد؟ (غير رسمي)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "غير رسمي")
        add("What's happening?", "واتس هابنينج؟", "ماذا يحدث؟ (عامية)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "عامية")
        add("What's going on?", "واتس جوينج أون؟", "ماذا يجري؟ (عامية)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "عامية")
        add("Nothing much", "ناثينج ماتش", "لا جديد / ليس كثيراً (رد على What's up)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "رد على What's up")
        add("Not a lot", "نوت أ لوت", "ليس كثيراً (رد على What's up)", sec1, "General Greetings & Responses / تحية عامة والردود عليها", "رد على What's up")

        // Greeting at certain times / تحيات في أوقات محددة
        add("Good morning.", "جود مورنينج", "صباح الخير (تحية الصباح).", sec1, "Greeting at certain times / تحيات في أوقات محددة")
        add("Morning.", "مورنينج", "صباح الخير (تحية الصباح مخففة).", sec1, "Greeting at certain times / تحيات في أوقات محددة")
        add("Good afternoon.", "جود أفترنون", "تحية وقت الظهيرة.", sec1, "Greeting at certain times / تحيات في أوقات محددة")
        add("Good evening.", "جود إيفنينج", "تحية المساء.", sec1, "Greeting at certain times / تحيات في أوقات محددة")
        add("Evening.", "إيفنينج", "تحية المساء (مخففة).", sec1, "Greeting at certain times / تحيات في أوقات محددة")
        add("How are you this bright morning?", "هاو آر يو ذيس برايت مورنينج؟", "كيف حالك في هذا الصباح المشرق؟", sec1, "Greeting at certain times / تحيات في أوقات محددة")

        // ==========================================
        // 2. قسم: Exhaustion & Busyness / التعبير عن التعب والإنشغال
        // ==========================================
        val sec2 = "2. Exhaustion & Busyness / التعبير عن التعب والإنشغال"

        // Ways to Say You're Tired / طرق للتعبير عن الإرهاق
        add("I'm exhausted.", "آيم إجزاوستد", "أنا مرهق.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm dead tired.", "آيم ديد تايرد", "أنا منهك جداً.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm pooped.", "آيم بوبد", "أنا تعبان جداً (عامية).", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق", "عامية")
        add("I'm spent.", "آيم سبينت", "أنا مستهلك / تعبان.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm beat.", "آيم بيت", "أنا مرهق للغاية.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm running on fumes.", "آيم رانينج أون فيومز", "أنا طالعة روحي / أعمل بأقل طاقة.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm running on empty.", "آيم رانينج أون إمبتي", "أنا طالعة روحي / أعمل بأقل طاقة.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I can hardly keep my eyes open.", "آي كان هاردلي كيب ماي آيز أوبن", "لا أستطيع أن أبقي عيني مفتوحة من شدة الإرهاق.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm off to bed.", "آيم أوف تو بيد", "أنا ذاهب للنوم.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("I'm gonna hit the sack.", "آيم جونا هيت ذا ساك", "سأذهب للسرير / للنوم.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")
        add("It's bedtime for me.", "إتس بيدتايم فور مي", "لقد حان وقت النوم بالنسبة لي.", sec2, "Ways to Say You're Tired / طرق للتعبير عن الإرهاق")

        // Telling You Have Been Busy / إخبار الآخرين بأنك مشغول
        add("I'm busy.", "آيم بيزي", "أنا مشغول.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("Keeping myself busy.", "كيبينج مايسيلف بيزي", "أشغل نفسي.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("Been keeping myself busy.", "بين كيبينج مايسيلف بيزي", "أشغل نفسي بالعمل.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("I'm swamped.", "آيم سوامبد", "غارق في العمل.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("As with a swamped boat.", "أز وذ أ سوامبد بوت", "كقارب غارق في البحر.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("Snowed under.", "سنود أندر", "مدفون تحت الثلج (لدي عمل لا ينتهي).", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("I don't have time to breathe.", "آي دونت هاف تايم تو بريث", "ليس لدي وقت لأنفس.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("I don't have time to think.", "آي دونت هاف تايم تو ثينك", "ليس لدي وقت لأفكر.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("There aren't enough hours in the day.", "ذير آرنت إنوف أورز إن ذا داي", "اليوم لا يكفي.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("Not a moment to spare.", "نوت أ مومينت تو سبير", "ليس لدي دقيقة واحدة لأهدرها.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("I've been running around with my head cut off.", "آيف بين رانينج أراوند وذ ماي هيد كات أوف", "إني أدور حول نفسي كالمذبوح.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")
        add("I've been running around like a chicken with its head cut off.", "آيف بين رانينج أراوند لايك أ تشيكن وذ إتس هيد كات أوف", "إني أدور حول نفسي كالدجاجة المذبوحة.", sec2, "Telling You Have Been Busy / إخبار الآخرين بأنك مشغول")

        // ==========================================
        // 3. قسم: Asking How Someone Is / السؤال عن الأحوال
        // ==========================================
        val sec3 = "3. Asking How Someone Is / السؤال عن الأحوال"
        add("How are you?", "هاو آر يو؟", "كيف حالك؟", sec3, "السؤال عن الأحوال")
        add("How is your family?", "هاو إز يور فاميلي؟", "كيف حال عائلتك؟", sec3, "السؤال عن الأحوال")
        add("How's the family?", "هاوز ذا فاميلي؟", "كيف حال عائلتك؟", sec3, "السؤال عن الأحوال")
        add("How are you doing?", "هاو آر يو دوينج؟", "كيف حالك؟", sec3, "السؤال عن الأحوال")
        add("How are you doing today?", "هاو آر يو دوينج توداي؟", "كيف حالك اليوم؟", sec3, "السؤال عن الأحوال")
        add("How you doing?", "هاو يو دوينج؟", "كيف حالك؟", sec3, "السؤال عن الأحوال")
        add("Are you doing Ok?", "آر يو دوينج أوكاي؟", "هل الحال على ما يرام؟", sec3, "السؤال عن الأحوال")
        add("How are you feeling?", "هاو آر يو فيلينج؟", "كيف حالك (كيف تشعر)؟", sec3, "السؤال عن الأحوال")
        add("How you feeling?", "هاو يو فيلينج؟", "كيف حالك (كيف تشعر)؟", sec3, "السؤال عن الأحوال")
        add("Are you feeling better today?", "آر يو فيلينج بيتر توداي؟", "هل تشعر بتحسن اليوم؟", sec3, "السؤال عن الأحوال")
        add("How have you been?", "هاو هاف يو بين؟", "كيف كانت الأحوال؟", sec3, "السؤال عن الأحوال")

        // ==========================================
        // 4. قسم: Introduction & Hobbies / التعارف والهوايات
        // ==========================================
        val sec4 = "4. Introduction & Hobbies / التعارف والهوايات"

        // Introducing Yourself / تقديم نفسك
        add("My name is Adnan and I live in Cairo.", "ماي نيم إز عدنان أند آي ليف إن كايرو", "اسمي عدنان وأعيش في القاهرة.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I was born and raised in... (Cairo, Dokki)", "آي واز بورن أند رايزد إن", "لقد ولدت وتربيت في...", sec4, "Introducing Yourself / تقديم نفسك")
        add("I was born in ... and grew up in...", "آي واز بورن إن ... أند جرو أب إن", "لقد ولدت في... وتربيت في...", sec4, "Introducing Yourself / تقديم نفسك")
        add("I work as an English teacher.", "آي وورك أز أن إنجليش تيتشر", "أنا أعمل كمعلم لغة إنجليزية.", sec4, "Introducing Yourself / تقديم نفسك")
        add("Actually, I'm single now.", "أكتشوالي، آيم سينجل ناو", "في الواقع، أنا أعزب الآن.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I'm married and I have 3 kids.", "آيم ماريد أند آي هاف ثري كيدز", "أنا متزوج ولدي 3 أطفال.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I am 19 years old.", "آي أم ناينتين ييرز أولد", "عمري 19 عاماً.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I am a salesman.", "آي أم أ سيلزمان", "أنا رجل مبيعات.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I am an accountant.", "آي أم أن أكاونتانت", "أنا محاسب.", sec4, "Introducing Yourself / تقديم نفسك")
        add("I am a lawyer.", "آي أم أ لوير", "أنا أعمل كمحامٍ.", sec4, "Introducing Yourself / تقديم نفسك")

        // Talking About Hobbies / التحدث عن الهوايات
        add("My hobbies are...", "ماي هوبيز آر", "هواياتي هي...", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("My hobbies are reading and swimming.", "ماي هوبيز آر ريدينج أند سويمينج", "هواياتي هي السباحة والقراءة.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("My hobbies are watching movies and cooking.", "ماي هوبيز آر واتشينج موفيز أند كوكينج", "هواياتي هي مشاهدة الأفلام والطبخ.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("My hobbies are languages and meeting new friends.", "ماي هوبيز آر لانجودجز أند ميتينج نيو فريندز", "هواياتي هي اللغات ومقابلة أصدقاء جدد.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("I like to go out with friends.", "آي لايك تو جو أوت وذ فريندز", "أحب الذهاب مع الأصدقاء.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("I am good at playing chess.", "آي أم جود أت بلاينج تشيس", "أنا بارع في الشطرنج.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("I enjoy going to the movies.", "آي إنجوي جوينج تو ذا موفيز", "أستمتع بالذهاب إلى السينما.", sec4, "Talking About Hobbies / التحدث عن الهوايات")
        add("I enjoy playing football.", "آي إنجوي بلاينج فوتبول", "أستمتع بلعب كرة القدم.", sec4, "Talking About Hobbies / التحدث عن الهوايات")

        // ==========================================
        // 5. قسم: Introducing Others & Responses / تقديم الآخرين والردود
        // ==========================================
        val sec5 = "5. Introducing Others & Responses / تقديم الآخرين والردود"

        // Introducing Someone to Someone Else / تقديم شخص لآخر
        add("I'd like you to meet my friend Adnan.", "آيد لايك يو تو ميت ماي فريند عدنان", "أود أن تعرف بصديقي عدنان.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("I'd like you to meet Adnan.", "آيد لايك يو تو ميت عدنان", "أود أن تعرف بعدنان.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("This is my friend Mounir.", "ذيس إز ماي فريند منير", "هذا صديقي منير.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Mona, this is Rania, Salwa and Mona.", "منى، ذيس إز رانيا، سلوى أند منى", "منى، أعرفك برانيا وسلوى ومنى.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Malty, have you met Adnan?", "مالكي، هاف يو ميت عدنان؟", "مالكي، هل قابلت عدنان؟", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Asmaa, shake hands with Mariam.", "أسماء، شيك هاندز وذ مريم", "أسماء، صافحي مريم.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Do you two know each other?", "دو يو تو نو إيتش أذر؟", "هل تعرفان بعضكما؟", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Have you met?", "هاف يو ميت؟", "هل تقابلتما؟", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Have you two been introduced?", "هاف يو تو بين إنتروديوسد؟", "هل تعارفتما؟", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Haven't you two been introduced?", "هافنت يو تو بين إنتروديوسد؟", "ألم تعارفا؟", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Oh, I'm sorry; how silly of me. This is Adnan.", "أوه، آيم سوري، هاو سيلي أوف مي. ذيس إز عدنان", "أوه، آسف كم أنا أحمق، هذا عدنان.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("Adnan, Mahmoud is the guy I was telling you about.", "عدنان، محمود إز ذا جاي آي واز تيلينج يو أباوت", "عدنان، هذا محمود، الشاب الذي كنت أحدثك عنه.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")
        add("You two have a lot in common.", "يو تو هاف أ لوت إن كومون", "أنتما الاثنين لديكما أشياء كثيرة مشتركة.", sec5, "Introducing Someone to Someone Else / تقديم شخص لآخر")

        // Responses When Introduced / الردود عند التعارف
        add("Good to meet you.", "جود تو ميت يو", "جميل أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("Nice to meet you.", "نايس تو ميت يو", "لطيف أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("How nice to meet you.", "هاو نايس تو ميت يو", "كم لطيف أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("How (very) nice to meet you.", "هاو فيري نايس تو ميت يو", "كم لطيف (جداً) أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("What a pleasure to meet you.", "وات أ بليجر تو ميت يو", "يالها من سعادة أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("It's pleasure to have finally met you.", "إتس بليجر تو هاف فاينالي ميت يو", "يالها من سعادة أن أقابلك أخيرًا.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("I'm pleased to make your acquaintance.", "آيم بليزد تو ميك يور أكواينتانس", "سعيد بمعرفتك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("I'm glad to meet you.", "آيم جلاد تو ميت يو", "سعيد بلقائك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("I'm happy to meet you.", "آيم هابي تو ميت يو", "سعيد بلقائك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("Charmed.", "تشارمد", "سعيد بمعرفتك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("A pleasure.", "أ بليجر", "سعيد بمعرفتك.", sec5, "Responses When Introduced / الردود عند التعارف")
        add("It's an honor to meet you.", "إتس أن أونر تو ميت يو", "يا له من شرف أن أقابلك.", sec5, "Responses When Introduced / الردود عند التعارف")

        // ==========================================
        // 6. قسم: Starting a Conversation & Getting Attention / البدء في المحادثة وجذب الانتباه
        // ==========================================
        val sec6 = "6. Starting a Conversation & Getting Attention / البدء في المحادثة وجذب الانتباه"

        // Starting an Informal Conversation / البدء في محادثة غير رسمية
        add("Guess what?", "جيس وات؟", "خمن ماذا؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Have you heard the latest?", "هاف يو هيرد ذا لايتست؟", "هل سمعت آخر الأخبار؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Have you heard?", "هاف يو هيرد؟", "هل سمعت؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Did you hear what happened?", "ديد يو هير وات هابند؟", "هل سمعت ماذا حدث؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Did you hear the news?", "ديد يو هير ذا نيو؟", "هل سمعت الأخبار؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Did you get the scoop?", "ديد يو جيت ذا سكوب؟", "هل سمعت آخر الأخبار؟", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("You'll never guess what I heard.", "يول نيفير جيس وات آي هيرد", "لن تخمن أبدًا ما سمعت.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Guess what I just found out.", "جيس وات آي جاست فاوند أوت", "خمن ما اكتشفته توًا.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("You won't believe this.", "يو وونت بليف ذيس", "لن تصدق ذلك.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("You won't believe what Adnan told me.", "يو وونت بليف وات عدنان تولد مي", "لن تصدق ما أخبرني به عدنان.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Get a load of this.", "جيت أ لود أوف ذيس", "خذ عينة من ذلك.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Dig this.", "ديج ذيس", "افهم هذا.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")
        add("Get this.", "جيت ذيس", "افهم ذلك.", sec6, "Starting an Informal Conversation / البدء في محادثة غير رسمية")

        // Getting Someone's Attention / لجذب انتباه شخص
        add("Pardon me.", "باردون مي", "اسمح لي.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Excuse me.", "إكسكيوز مي", "اسمح لي.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Hey!", "هاي", "هاي!", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Hey, you!", "هاي يو", "هاي أنت!", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Yo!", "يو", "أهلاً!", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Look, here!", "لوك هير", "انظر هنا.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Listen up!", "ليسن أب", "اسمع.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Listen here!", "ليسن هير", "اسمع لي.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Now hear this!", "ناو هير ذيس", "الآن، اسمع هذا.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Hear me out.", "هير مي أوت", "أنصت لي.", sec6, "Getting Someone's Attention / لجذب انتباه شخص")
        add("Are you ready for this?", "آر يو ريدي فور ذيس؟", "هل أنت مستعد لما سأقوله؟", sec6, "Getting Someone's Attention / لجذب انتباه شخص")

        // ==========================================
        // 7. قسم: Expressions About Age / تعبيرات عن العمر
        // ==========================================
        val sec7 = "7. Expressions About Age / تعبيرات عن العمر"
        add("She's in her early twenties.", "شيز إن هير إيرلي توينتيز", "إنها في أوائل العشرينات من العمر (20-23 سنة).", sec7, "تعبيرات عن العمر")
        add("He's in his late thirties.", "هيز إن هيز ليت ثيرتيز", "إنه في أواخر الثلاثينات من العمر (37-39 سنة).", sec7, "تعبيرات عن العمر")
        add("She just turned six.", "شي جاست تيرند سكس", "لقد بلغت السادسة للتو.", sec7, "تعبيرات عن العمر")
        add("Act your age!", "آكت يور إيج!", "تصرف بما يليق بعمرك!", sec7, "تعبيرات عن العمر")
        add("I'm not as young as I used to be.", "آيم نوت أز يانج أز آي يوست تو بي", "لست شابًا كما كنت من قبل.", sec7, "تعبيرات عن العمر")
        add("I'm not over the hill yet!", "آيم نوت أوفير ذا هيل ييت!", "لم أصل لسن الشيخوخة بعد.", sec7, "تعبيرات عن العمر")
        add("He's no spring chicken.", "هيز نو سبرينج تشيكن", "إنه ليس شابًا.", sec7, "تعبيرات عن العمر")
        add("She's wise beyond her years.", "شيز وايز بيوند هير ييرز", "إنها شابة ولكنها تتمتع بحكمة شخص أكبر سنًا.", sec7, "تعبيرات عن العمر")
        add("I'm having a senior moment.", "آيم هافينج أ سينير مومينت", "أعاني من لحظات الشيخوخة (أنسى كثيراً).", sec7, "تعبيرات عن العمر")
        add("He lived to a ripe old age.", "هي ليفد تو أ رايب أولد إيج", "لقد عاش عمرًا طويلاً.", sec7, "تعبيرات عن العمر")

        // ==========================================
        // 8. قسم: Business Email / البريد الإلكتروني للأعمال
        // ==========================================
        val sec8 = "8. Business Email / البريد الإلكتروني للأعمال"
        add("Dear Hiring Manager", "دير هايرينج مانجر", "عزيزي مدير التوظيف.", sec8, "مقدمة البريد الإلكتروني للأعمال")
        add("Dear Amazon Innovation Team", "دير أمزون إنوفيشن تيم", "عزيزي فريق أمزون للابتكار.", sec8, "مقدمة البريد الإلكتروني للأعمال")
        add("Dear Sir/Madam", "دير سير / مدام", "عزيزي السيد / السيدة.", sec8, "مقدمة البريد الإلكتروني للأعمال")
        add("To Whom It May Concern", "تو هوم إت ماي كونسيرن", "إلى من يهمه الأمر.", sec8, "مقدمة البريد الإلكتروني للأعمال")
        add("I am writing to enquire about...", "آي أم رايتينج تو إنكواير أباوت", "أنا أكتب للاستفسار عن...", sec8, "جسم البريد الإلكتروني")
        add("I am writing in reference to...", "آي أم رايتينج إن ريفرينس تو", "أنا أكتب بالإشارة إلى...", sec8, "جسم البريد الإلكتروني")
        add("Could you please look into this...", "كود يو بليز لوك إنتو ذيس", "أرجو أن تنظر في هذا...", sec8, "جسم البريد الإلكتروني")
        add("At your earliest convenience...", "أت يور إيرليست كونفينينس", "في أقرب فرصة ملائمة لك...", sec8, "جسم البريد الإلكتروني")
        add("Many thanks in advance for your help", "ماني ثانكس إن أدفانس فور يور هيلب", "وشكراً جزيلاً مقدماً لمساعدتكم.", sec8, "خاتمة البريد الإلكتروني")
        add("Thank you for your help", "ثانك يو فور يور هيلب", "شكراً لكم على مساعدتكم.", sec8, "خاتمة البريد الإلكتروني")
        add("Thank you for your consideration", "ثانك يو فور يور كونسايدريشن", "شكراً لعنايتكم / لاهتمامكم.", sec8, "خاتمة البريد الإلكتروني")

        // ==========================================
        // 9. قسم: Answering the Telephone / الرد على الهاتف
        // ==========================================
        val sec9 = "9. Answering the Telephone / الرد على الهاتف"

        // Mobile Communications / الاتصالات المحمولة
        add("That's my phone.", "ذاتس ماي فون", "هذا هاتفي.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("It's my ring tone.", "إتس ماي رينج تون", "إنها نغمة رنيني.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("I'm being texted.", "آيم بيينج تكستد", "إنني أتلقى رسائل.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("Do you mind if I take this call?", "دو يو مايند إف آي تيك ذيس كول؟", "هل تمانع أن أستقبل هذه المكالمة؟", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("I'll just be a minute.", "آيل جاست بي أ مينيت", "سيستغرق الأمر دقيقة فقط.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("Sorry, do you mind if I take this?", "سوري، دو يو مايند إف آي تيك ذيس؟", "معذرة، هل تمانع أن أستقبل هذه المكالمة؟", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("I need to take this call.", "آي نيد تو تيك ذيس كول", "يجب أن أستقبل هذه المكالمة.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("I just need to send a reply.", "آي جاست نيد تو سيند أ ريبلاي", "يجب أن أرسل رداً فقط.", sec9, "Mobile Communications / الاتصالات المحمولة")
        add("I need to text her back.", "آي نيد تو تكست هير باك", "يجب أن أرسل لها رداً.", sec9, "Mobile Communications / الاتصالات المحمولة")

        // Residential / هاتف المنزلي
        add("Hello.", "هالو", "مرحباً.", sec9, "Residential / هاتف المنزلي")
        add("Adnan residence.", "عدنان ريزيدنس", "منزل عدنان.", sec9, "Residential / هاتف المنزلي")
        add("Hello, Adnan residence.", "هالو، عدنان ريزيدنس", "مرحباً، منزل عدنان.", sec9, "Residential / هاتف المنزلي")
        add("Yo (informal).", "يو", "نعم.", sec9, "Residential / هاتف المنزلي")
        add("Yeah (informal).", "ياه", "نعم.", sec9, "Residential / هاتف المنزلي")
        add("Yes.", "يس", "نعم.", sec9, "Residential / هاتف المنزلي")
        add("Hello, this is Adnan Anter (Speaking).", "هالو، ذيس إز عدنان عنتر سبيكينج", "مرحباً، هذا عدنان عنتر (يتحدث).", sec9, "Residential / هاتف المنزلي")
        add("Adnan Anter, May I help you?", "عدنان عنتر، ماي آي هيلب يو؟", "هذا عدنان عنتر، هل يمكنني مساعدتك؟", sec9, "Residential / هاتف المنزلي")
        add("Adnan.", "عدنان", "هذا عدنان.", sec9, "Residential / هاتف المنزلي")

        // Business Telephone / هاتف العمل التجاري
        add("City hall, what department please?", "سيتي هول، وات ديبارتمينت بليز؟", "جهاز المدينة، أي قسم تريد؟", sec9, "Business Telephone / هاتف العمل التجاري")
        add("Adnan Company.", "عدنان كومباني", "شركة عدنان.", sec9, "Business Telephone / هاتف العمل التجاري")
        add("Adnan company, may I help you?", "عدنان كومباني، ماي آي هيلب يو؟", "شركة عدنان، هل يمكنني مساعدتك؟", sec9, "Business Telephone / هاتف العمل التجاري")

        // ==========================================
        // 10. قسم: At the Bank / في المصرف
        // ==========================================
        val sec10 = "10. At the Bank / في المصرف"

        // Banking Transactions / المعاملات المصرفية
        add("I'd like to withdraw $100, please.", "آيد لايك تو وذدرو ١٠٠ دولار بليز", "أود سحب 100 دولار من فضلك.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("I want to make a withdrawal.", "آي وونت تو ميك أ وذدروال", "أريد أن أسحب بعض النقود.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("How would you like the money?", "هاو وود يو لايك ذا ماني؟", "كيف تود النقود؟", sec10, "Banking Transactions / المعاملات المصرفية")
        add("In tens, please (ten dollar notes).", "إن تينز بليز", "عشرات من فضلك (من فئة العشرة دولار).", sec10, "Banking Transactions / المعاملات المصرفية")
        add("Could you give me some smaller notes?", "كود يو جيف مي سام سمولر نوتس؟", "هلا أعطيتني أوراقاً نقدية من فئات أصغر؟", sec10, "Banking Transactions / المعاملات المصرفية")
        add("I'd like to pay this in, please.", "آيد لايك تو باي ذيس إن بليز", "أود أن أضع هذه (على حسابي) من فضلك.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("I'd like to pay this cheque in, please.", "آيد لايك تو باي ذيس تشيك إن بليز", "أود أن أدفع هذا الشيك (إلى حسابي) من فضلك.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("How many days will it take for the cheque to clear?", "هاو ماني دايز ويل إت تيك فور ذا تشيك تو كلير؟", "كم يوماً سيستغرق هذا الشيك لصرفه؟", sec10, "Banking Transactions / المعاملات المصرفية")
        add("Have you got any...? Identification / ID", "هاف يو جوت إني آيدينتيفيكيشن / آي دي؟", "هل لديك أية وثيقة لإثبات الهوية / بطاقة هوية؟", sec10, "Banking Transactions / المعاملات المصرفية")
        add("I've got my... passport / driving licence / ID card.", "آيف جوت ماي باسبورت / درايفينج لايسنس / آي دي كارد", "معي جواز سفري / رخصة السيارة / بطاقة هويتي.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("Your account's overdrawn.", "يور أكاونتس أوفيردرون", "حسابك فوق حد السحب (سحبت نقوداً أكثر من رصيدك).", sec10, "Banking Transactions / المعاملات المصرفية")
        add("I'd like to transfer some money to this account.", "آيد لايك تو ترانسفير سام ماني تو ذيس أكاونت", "أود تحويل بعض النقود إلى هذا الحساب.", sec10, "Banking Transactions / المعاملات المصرفية")
        add("Could you transfer £1000 from my current account to my deposit account?", "كود يو ترانسفير ١٠٠٠ باوند فروم ماي كارنت أكاونت تو ماي ديبوزيت أكاونت؟", "هلا حولت 1000 جنيه من حسابي الحالي إلى حساب الضمان الخاص بي؟", sec10, "Banking Transactions / المعاملات المصرفية")

        // Other Services / خدمات أخرى
        add("I'd like to open an account.", "آيد لايك تو أوبن أن أكاونت", "أود أن أفتح حساباً.", sec10, "Other Services / خدمات أخرى")
        add("I'd like to open a personal account.", "آيد لايك تو أوبن أ بيرسونال أكاونت", "أود أن أفتح حساباً شخصياً.", sec10, "Other Services / خدمات أخرى")
        add("I'd like to open a business account.", "آيد لايك تو أوبن أ بيزنس أكاونت", "أود أن أفتح حساب عمل.", sec10, "Other Services / خدمات أخرى")
        add("Could you tell me my balance, please?", "كود يو تيل مي ماي بالانس بليز؟", "هل تستطيع أن تخبرني ما ميزانيتي من فضلك؟", sec10, "Other Services / خدمات أخرى")

        // ==========================================
        // 11. قسم: Talking to the Boss / التحدث إلى المدير
        // ==========================================
        val sec11 = "11. Talking to the Boss / التحدث إلى المدير"

        // Talking to the Boss / التحدث إلى الرئيس
        add("Do you want to review my project?", "دو يو وونت تو ريفيو ماي بروجيكت؟", "هل تريد مراجعة مشروعي؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("I completed the assignment.", "آي كومبليتد ذا أساينمنت", "لقد أكملت المهمة للتو.", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("Do you want to review it?", "دو يو وونت تو ريفيو إت؟", "هل أردت مراجعتها؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("This is my first project and I was wondering if you could review it real quick.", "ذيس إز ماي فيرست بروجيكت أند آي واز واندرينج إف يو كود ريفيو إت ريل كويك", "هذا مشروعي الأول وكنت أتساءل إن كان بإمكانك مراجعته بسرعة؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("I think I covered all the bases, but could you do a quick check?", "آي ثينك آي كافرد أول ذا بيسز، بت كود يو دو أ كويك تشيك؟", "أعتقد أنني غطيت كل القواعد / الأساسيات، ولكن هل يمكنك أن تقوم بمراجعة سريعة؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("Adnan, can we set up a time to discuss my progress so far?", "عدنان، كان وي سيت أب أ تايم تو ديسكاس ماي بروجريس سو فار؟", "مرحباً يا عدنان، هل يمكننا تحديد وقت لمناقشة التقدم الذي أحرزته حتى الآن؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("I was wondering if we can meet to discuss how I am doing.", "آي واز واندرينج إف وي كان ميت تو ديسكاس هاو آي أم دوينج", "كنت أتساءل إذا كان بإمكاننا أن نلتقي لمناقشة أدائي بالعمل.", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("Can I get some feedback on my performance?", "كان آي جيت سام فيدباك أون ماي بيرفورمانس؟", "هل يمكنني الحصول على بعض التقييم على أدائي؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("Where do you think I need to improve?", "وير دو يو ثينك آي نيد تو إمبروف؟", "في أي جانب تعتقد أنني بحاجة إلى التحسن؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")
        add("What areas do you think I should work on?", "وات إيرياز دو يو ثينك آي شود وورك أون؟", "ما هي المناطق التي يجب أن أعمل عليها؟", sec11, "Talking to the Boss / التحدث إلى الرئيس")

        // Asking for More Work / طلب المزيد من العمل
        add("Mark, I finished all my weekly duties already. Do you have more work I can do?", "مارك، آي فينيشد أول ماي ويكلي ديوتيز أولريدي. دو يو هاف مور وورك آي كان دو؟", "مرحباً مارك، أنهيت جميع واجباتي الأسبوعية بالفعل. هل لديك المزيد من العمل يمكنني القيام به؟", sec11, "Asking for More Work / طلب المزيد من العمل")
        add("I've been completing my work early on a regular basis. Can I have more responsibilities?", "آيف بين كومبليتينج ماي وورك إيرلي أون أ ريجولار بيسز. كان آي هاف مور ريسبونسبيليتيز؟", "لقد كنت أكمل عملي في وقت مبكر على أساس منتظم. هل يمكنني الحصول على المزيد من المسؤوليات؟", sec11, "Asking for More Work / طلب المزيد من العمل")

        return list
    }
}
