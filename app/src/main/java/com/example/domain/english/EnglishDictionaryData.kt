package com.example.domain.english

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EnglishWordItem(
    val english: String,
    val arabicPronunciation: String,
    val arabicMeaning: String,
    val phonetic: String = "",
    val partOfSpeech: String = "noun",
    val exampleEn: String = "",
    val exampleAr: String = "",
    val emoji: String = "📖",
    val category: String = "عام",
    val isFavorite: Boolean = false
)

object EnglishDictionaryRepository {

    private val rawWordList: List<EnglishWordItem> by lazy {
        val list = mutableListOf<EnglishWordItem>()
        list.addAll(generateComprehensiveWordList())
        EnglishPhrasesDatabase.allPhrases.forEach { phrase ->
            list.add(
                EnglishWordItem(
                    english = phrase.english,
                    arabicPronunciation = phrase.pronunciationAr,
                    arabicMeaning = phrase.meaningAr,
                    phonetic = "",
                    partOfSpeech = phrase.subSection,
                    exampleEn = phrase.english,
                    exampleAr = phrase.meaningAr,
                    emoji = "🗣️",
                    category = phrase.section
                )
            )
        }
        list
    }

    fun getEmbeddedWords(): List<EnglishWordItem> {
        return rawWordList
    }

    /**
     * Search online fallback dictionary API
     */
    suspend fun fetchOnlineWord(query: String): EnglishWordItem? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext null

        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArr = JSONArray(responseText)
                if (jsonArr.length() > 0) {
                    val firstEntry = jsonArr.getJSONObject(0)
                    val wordStr = firstEntry.optString("word", cleanQuery)
                    val phonetic = firstEntry.optString("phonetic", "")
                    val meanings = firstEntry.optJSONArray("meanings")
                    var partOfSpeech = "noun"
                    var definition = ""
                    var example = ""

                    if (meanings != null && meanings.length() > 0) {
                        val firstMeaning = meanings.getJSONObject(0)
                        partOfSpeech = firstMeaning.optString("partOfSpeech", "noun")
                        val definitions = firstMeaning.optJSONArray("definitions")
                        if (definitions != null && definitions.length() > 0) {
                            val firstDef = definitions.getJSONObject(0)
                            definition = firstDef.optString("definition", "")
                            example = firstDef.optString("example", "")
                        }
                    }

                    val arabicPron = generatePhoneticArabic(wordStr)
                    val arabicMeaning = if (definition.isNotBlank()) "معنى: $definition" else "كلمة مترجمة أونلاين"

                    return@withContext EnglishWordItem(
                        english = wordStr.replaceFirstChar { it.uppercase() },
                        arabicPronunciation = arabicPron,
                        arabicMeaning = arabicMeaning,
                        phonetic = phonetic,
                        partOfSpeech = partOfSpeech,
                        exampleEn = if (example.isNotBlank()) example else "Example with $wordStr.",
                        exampleAr = "مثال توضيحي للكلمة في سياق الجملة.",
                        emoji = "🌐",
                        category = "بحث مباشر أونلاين"
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback: generate phonetic and default offline item
        val phon = generatePhoneticArabic(cleanQuery)
        return@withContext EnglishWordItem(
            english = cleanQuery.replaceFirstChar { it.uppercase() },
            arabicPronunciation = phon,
            arabicMeaning = "مفردة إنجليزية تم البحث عنها: $cleanQuery",
            phonetic = "/${cleanQuery.lowercase()}/",
            partOfSpeech = "noun",
            exampleEn = "This is the usage of $cleanQuery in a sentence.",
            exampleAr = "هذا مثال على استخدام $cleanQuery في جملة مفيدة.",
            emoji = "🔍",
            category = "قاموس ذكي"
        )
    }

    /**
     * Automatic Arabic Phonetic generator for any English word (e.g. Cat -> كات, School -> سكول)
     */
    fun generatePhoneticArabic(enWord: String): String {
        val lower = enWord.lowercase().trim()
        if (lower.isBlank()) return ""

        val specialMap = mapOf(
            "cat" to "كات", "dog" to "دوج", "book" to "بوك", "school" to "سكول",
            "water" to "ووتر", "apple" to "أبل", "car" to "كار", "house" to "هاوس",
            "friend" to "فريند", "family" to "فاميلي", "teacher" to "تيتشر",
            "student" to "ستيودنت", "university" to "يونيفيرسيتي", "computer" to "كومبيوتر",
            "phone" to "فون", "language" to "لانجوتش", "heart" to "هارت", "sun" to "صن",
            "moon" to "مون", "star" to "ستار", "night" to "نايت", "day" to "داي",
            "time" to "تايم", "life" to "لايف", "world" to "وورلد", "music" to "ميوزيك"
        )
        specialMap[lower]?.let { return it }

        val sb = StringBuilder()
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
            val nextC = if (i + 1 < lower.length) lower[i + 1] else ' '
            val twoChar = "$c$nextC"

            when (twoChar) {
                "th" -> { sb.append("ث"); i += 2; continue }
                "sh" -> { sb.append("ش"); i += 2; continue }
                "ch" -> { sb.append("تش"); i += 2; continue }
                "ph" -> { sb.append("ف"); i += 2; continue }
                "kh" -> { sb.append("خ"); i += 2; continue }
                "gh" -> { sb.append("غ"); i += 2; continue }
                "oo" -> { sb.append("و"); i += 2; continue }
                "ee" -> { sb.append("ي"); i += 2; continue }
                "ea" -> { sb.append("ي"); i += 2; continue }
                "ou" -> { sb.append("او"); i += 2; continue }
                "tion" -> { sb.append("شن"); i += 4; continue }
                "sion" -> { sb.append("جن"); i += 4; continue }
            }

            when (c) {
                'a' -> sb.append(if (i == 0) "أ" else "ا")
                'b' -> sb.append("ب")
                'c' -> sb.append(if (nextC in listOf('e', 'i', 'y')) "س" else "ك")
                'd' -> sb.append("د")
                'e' -> sb.append(if (i == 0) "إ" else if (i == lower.length - 1) "" else "ي")
                'f' -> sb.append("ف")
                'g' -> sb.append(if (nextC in listOf('e', 'i', 'y')) "ج" else "غ")
                'h' -> sb.append("هـ")
                'i' -> sb.append(if (i == 0) "إ" else "ي")
                'j' -> sb.append("ج")
                'k' -> sb.append("ك")
                'l' -> sb.append("ل")
                'm' -> sb.append("م")
                'n' -> sb.append("ن")
                'o' -> sb.append(if (i == 0) "أو" else "و")
                'p' -> sb.append("ب")
                'q' -> sb.append("ك")
                'r' -> sb.append("ر")
                's' -> sb.append("س")
                't' -> sb.append("ت")
                'u' -> sb.append(if (i == 0) "أُ" else "و")
                'v' -> sb.append("ف")
                'w' -> sb.append("و")
                'x' -> sb.append("كس")
                'y' -> sb.append("ي")
                'z' -> sb.append("ز")
                else -> sb.append("")
            }
            i++
        }
        val result = sb.toString().trim()
        return if (result.isNotBlank()) result else enWord
    }

    private fun generateComprehensiveWordList(): List<EnglishWordItem> {
        val list = mutableListOf<EnglishWordItem>()

        // 1. Daily Life & Oxford Core
        val coreDaily = listOf(
            Triple("Cat", "كات", "قطة / هرّة أليفة") to ("The cat is sleeping peacefully on the sofa." to "القطة نائمة بسلام على الأريكة."),
            Triple("Dog", "دوج", "كلب") to ("The dog is a loyal companion." to "الكلب رفيق مخلص."),
            Triple("Book", "بوك", "كتاب / مرجع علمي") to ("Reading a book opens new horizons for your mind." to "قراءة كتاب تفتح آفاقاً جديدة لعقلك."),
            Triple("Pen", "بين", "قلم حبر") to ("The pen is mightier than the sword." to "القلم أقوى من السيف."),
            Triple("Pencil", "بينسل", "قلم رصاص") to ("Use a pencil to sketch your initial ideas." to "استخدم قلم رصاص لرسم أفكارك الأولية."),
            Triple("Water", "ووتر", "ماء عذب") to ("Drinking pure water is essential for daily health." to "شرب الماء النقي ضروري للصحة اليومية."),
            Triple("Bread", "بريد", "خبز طازج") to ("They bought fresh bread from the bakery." to "اشتروا خبزاً طازجاً من المخبز."),
            Triple("Apple", "أبل", "تفاحة") to ("An apple a day keeps the doctor away." to "تفاحة في اليوم تبعد الطبيب عنك."),
            Triple("House", "هاوس", "منزل / بيت") to ("They built a cozy house near the mountains." to "بنوا منزلاً دافئاً قرب الجبال."),
            Triple("Family", "فاميلي", "عائلة / أسرة") to ("Family support gives you unlimited strength." to "دعم العائلة يمنحك قوة لا محدودة."),
            Triple("Friend", "فريند", "صديق مخلص") to ("A true friend stands by you in hard times." to "الصديق الحقيقي يقف بجانبك في الأوقات الصعبة."),
            Triple("Time", "تايم", "وقت / زمن") to ("Time management is the secret of success." to "إدارة الوقت هي سر النجاح."),
            Triple("Day", "داي", "يوم / نهار") to ("Every day brings new learning opportunities." to "كل يوم يحمل فرصاً جديدة للتعلم."),
            Triple("Night", "نايت", "ليل / مساء") to ("The night sky is filled with bright stars." to "سماء الليل مليئة بالنجوم الساطعة."),
            Triple("Sun", "صن", "شمس مشرقة") to ("The sun provides light and warmth to Earth." to "الشمس تمد الأرض بالضوء والدفء."),
            Triple("Moon", "مون", "قمر منير") to ("The full moon illuminates the dark desert." to "القمر المكتمل يضيء الصحراء المظلمة."),
            Triple("Door", "دور", "باب / مدخل") to ("Hard work opens every closed door." to "العمل الجاد يفتح كل باب مغلق."),
            Triple("Window", "ويندو", "نافذة / شباك") to ("Fresh morning breeze enters through the window." to "نسيم الصباح العليل يدخل من النافذة."),
            Triple("Car", "كار", "سيارة / مركبة") to ("Electric cars are becoming very popular." to "السيارات الكهربائية أصبحت شائعة جداً."),
            Triple("Road", "رود", "طريق / سبيل") to ("The road to excellence requires persistence." to "طريق التفوق يتطلب الإصرار."),
            Triple("City", "سيتي", "مدينة كبرى") to ("The city never sleeps and is full of life." to "المدينة لا تنام ومليئة بالحياة."),
            Triple("Village", "فيليج", "قرية ريفية") to ("The village is calm and surrounded by nature." to "القرية هادئة ومحاطة بالطبيعة."),
            Triple("River", "ريفر", "نهر جارٍ") to ("The Nile river is the lifeline of Egypt." to "نهر النيل هو شريان الحياة في مصر."),
            Triple("Sea", "سي", "بحر واسع") to ("The blue sea calms the troubled soul." to "البحر الأزرق يهدئ النفس المضطربة."),
            Triple("Mountain", "ماونتن", "جبل شاهق") to ("They climbed the highest mountain peak." to "تسلقوا أعلى قمة جبلية."),
            Triple("Tree", "تري", "شجرة خضراء") to ("Planting a tree benefits future generations." to "زراعة شجرة تفيد الأجيال القادمة."),
            Triple("Flower", "فلاور", "زهرة / وردة") to ("The flower blooms beautifully in spring." to "الزهرة تتفتح بجمال في فصل الربيع."),
            Triple("Sky", "سكاي", "سماء صافية") to ("Birds soar freely across the clear blue sky." to "تحلّق الطيور بحرية عبر السماء الزرقاء الصافية."),
            Triple("Rain", "رين", "مطر / غيث") to ("Gentle rain brings life back to dry soil." to "المطر اللطيف يعيد الحياة إلى التربة الجافة."),
            Triple("Wind", "ويند", "رياح / نسيم") to ("A cool wind blew gently across the fields." to "هبت رياح باردة بلطف عبر الحقول."),
            Triple("Fire", "فاير", "نار / شعلة") to ("They gathered around the warm camp fire." to "تجمعوا حول نار المخيم الدافئة."),
            Triple("Earth", "إيرث", "كوكب الأرض / تربة") to ("We must protect Planet Earth for our children." to "يجب علينا حماية كوكب الأرض لأجل أطفالنا.")
        )
        coreDaily.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "noun",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "🏡",
                    category = "الحياة اليومية"
                )
            )
        }

        // 2. Academic & Education & School
        val academicWords = listOf(
            Triple("Achievement", "أتشيفمنت", "إنجاز / تحقيق هدف عظيم") to ("Graduating with top honors was her proudest achievement." to "كان التخرج بمرتبة الشرف أعلى إنجازاتها فخراً."),
            Triple("Perseverance", "بيرسيفيرانس", "المثابرة والإصرار المستمر") to ("Through perseverance and focus, any challenge can be overcome." to "من خلال المثابرة والتركيز، يمكن التغلب على أي تحدٍ."),
            Triple("Resilience", "ريزيليانس", "المرونة والنهوض بعد الصعاب") to ("Resilience is essential for thriving in school and life." to "المرونة ضرورية للازدهار في الدراسة والحياة."),
            Triple("Knowledge", "نولدج", "المعرفة والعلم المكتسب") to ("Knowledge is power when put into positive action." to "المعرفة قوة عندما تُوضع موضع العمل الإيجابي."),
            Triple("School", "سكول", "مدرسة / صرح تعليمي") to ("School is where curious minds prepare for the future." to "المدرسة هي حيث تستعد العقول الفضولية للمستقبل."),
            Triple("University", "يونيفيرسيتي", "جامعة / مؤسسة أكاديمية") to ("He was accepted into his dream university." to "تم قبوله في الجامعة التي كان يحلم بها."),
            Triple("Education", "إديوكيشن", "التعليم والتربية") to ("Education is the passport to the future." to "التعليم هو جواز السفر إلى المستقبل."),
            Triple("Student", "ستيودنت", "طالب علم وباحث") to ("The dedicated student studies every single day." to "الطالب المخلص يذاكر كل يوم."),
            Triple("Teacher", "تيتشر", "معلم / أستاذ فاضل") to ("A good teacher inspires hope and ignites imagination." to "المعلم الجيد يلهم الأمل ويشعل الخيال."),
            Triple("Professor", "بروفيسور", "أستاذ جامعي متمرس") to ("The professor delivered a fascinating lecture today." to "ألقى الأستاذ الجامعي محاضرة شيقة اليوم."),
            Triple("Curriculum", "كاريكيولم", "المنهج الدراسي المقرر") to ("The new curriculum emphasizes critical thinking." to "المنهج الجديد يركز على التفكير النقدي."),
            Triple("Syllabus", "سيلاباس", "خطة المقرر الدراسي") to ("Check the syllabus for exam dates and homework." to "راجع خطة المقرر لمعرفة مواعيد الامتحانات والواجبات."),
            Triple("Examination", "إجزامينيشن", "امتحان واختبار تقييمي") to ("Prepare thoroughly before your final examination." to "استعد جيداً قبل امتحانك النهائي."),
            Triple("Homework", "هوموورك", "واجب مدرسي منزلي") to ("Completing homework reinforces classroom lessons." to "إنجاز الواجبات المنزلية يعزز دروس الفصل."),
            Triple("Library", "لايبراري", "مكتبة للقراءة والبحث") to ("The library is quiet and ideal for deep concentration." to "المكتبة هادئة ومثالية للتركيز العميق."),
            Triple("Scholarship", "سكولارشيب", "منحة دراسية مجانية") to ("She won a full scholarship for academic excellence." to "حصلت على منحة دراسية كاملة لتفوقها الأكاديمي."),
            Triple("Diploma", "دبلوما", "شهادة دبلوم / وثيقة تخرج") to ("He received his high school diploma proudly." to "استلم شهادة الثانوية العامة بكل فخر."),
            Triple("Bachelor", "باتشيلور", "درجة البكالوريوس / الليسانس") to ("She earned a Bachelor's degree in Computer Science." to "نالت درجة البكالوريوس في علوم الحاسوب."),
            Triple("Master", "ماستر", "درجة الماجستير") to ("He is currently pursuing a Master's degree in Engineering." to "هو يتابع حالياً دراسة الماجستير في الهندسة."),
            Triple("Doctorate", "دوكتوريت", "درجة الدكتوراه (PhD)") to ("Earning a doctorate requires original research." to "الحصول على الدكتوراه يتطلب بحثاً علمياً أصيلاً."),
            Triple("Thesis", "ثيسيس", "أطروحة / رسالة علمية") to ("His graduation thesis solved an important real-world problem." to "حلت أطروحة تخرجه مشكلة هامة في العالم الواقعي."),
            Triple("Assignment", "أساينمنت", "مهمة دراسية / تكليف") to ("Submit your chemistry assignment before the deadline." to "سلّم تكليف الكيمياء قبل الموعد النهائي."),
            Triple("Laboratory", "لابوراتوري", "مختبر علمي تجريبي") to ("Safety goggles are required inside the chemistry laboratory." to "نظارات الأمان إجبارية داخل مختبر الكيمياء."),
            Triple("Experiment", "إكسبيريمنت", "تجربة علمية واستقصاء") to ("The biology experiment confirmed their scientific hypothesis." to "أكدت التجربة الحيوية فرضيتهم العلمية."),
            Triple("Hypothesis", "هايبوثيسيس", "فرضية علمية قابلة للاختبار") to ("Scientists form a hypothesis before testing it." to "يصيغ العلماء فرضية قبل اختبارها."),
            Triple("Analysis", "أناليسيس", "تحليل دقيق واستنتاج") to ("Data analysis reveals hidden patterns in study habits." to "تحليل البيانات يكشف أنماطاً خفية في عادات الاستذكار."),
            Triple("Research", "ريسرش", "بحث واستقصاء أكاديمي") to ("Original research advances human understanding." to "البحث الأصيل يدفع الفهم البشري للأمام."),
            Triple("Summary", "ساماري", "ملخص موجز للنقاط الأساسية") to ("Write a concise summary after reading each chapter." to "اكتب ملخصاً موجزاً بعد قراءة كل فصل."),
            Triple("Revision", "ريفجن", "مراجعة وتثبيت المعلومات") to ("Daily revision prevents forgetting before big exams." to "المراجعة اليومية تمنع النسيان قبل الامتحانات الكبرى."),
            Triple("Concentration", "كونسينتريشن", "التركيز الذهني العميق") to ("Pomodoro technique enhances your focus and concentration." to "تقنية بومودورو تعزز تركيزك الذهني واستيعابك.")
        )
        academicWords.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "noun",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "📚",
                    category = "الأكاديمي والتعليم"
                )
            )
        }

        // 3. Technology, Science, Coding & AI
        val techWords = listOf(
            Triple("Algorithm", "ألجوريزم", "خوارزمية حسابية منظمة") to ("An efficient algorithm solves complex problems in seconds." to "الخوارزمية الفعالة تحل المشكلات المعقدة في ثوانٍ."),
            Triple("Artificial", "أرتيفيشال", "اصطناعي / مصطنع") to ("Artificial intelligence is transforming modern industries." to "الذكاء الاصطناعي يغير الصناعات الحديثة كلياً."),
            Triple("Intelligence", "إنتليجنس", "ذكاء وفطنة وفهم") to ("Emotional intelligence is as important as technical skill." to "الذكاء العاطفي مهم بقدر المهارة التقنية."),
            Triple("Computer", "كومبيوتر", "حاسوب آلي") to ("Computers process millions of calculations per second." to "الحواسيب تعالج ملايين الحسابات في الثانية."),
            Triple("Software", "سوفتوير", "برمجيات وتطبيقات") to ("Open source software powers the modern global internet." to "البرمجيات مفتوحة المصدر تشغل الإنترنت العالمي الحديث."),
            Triple("Hardware", "هاردوير", "عتاد ومكونات مادية") to ("Upgrading the hardware improved computer speed significantly." to "ترقية العتاد حسنت سرعة الحاسوب بشكل ملحوظ."),
            Triple("Database", "داتابيس", "قاعدة بيانات منظمة") to ("The database stores all student records securely." to "قاعدة البيانات تحفظ سجلات جميع الطلاب بأمان."),
            Triple("Network", "نتوورك", "شبكة اتصالات وحواسيب") to ("A stable network connection ensures uninterrupted learning." to "اتصال شبكة مستقر يضمن تعلماً دون انقطاع."),
            Triple("Internet", "إنترنت", "الشبكة العنكبوتية العالمية") to ("The internet connects billions of people worldwide." to "يربط الإنترنت مليارات الأشخاص حول العالم."),
            Triple("Security", "سيكيوريتي", "أمان وحماية إلكترونية") to ("Cyber security protects your privacy and digital identity." to "الأمن السيبراني يحمي خصوصيتك وهويتك الرقمية."),
            Triple("Encryption", "إنكريبشن", "تشفير وحماية البيانات") to ("End-to-end encryption keeps messages private." to "التشفير التام يحافظ على خصوصية الرسائل."),
            Triple("Processor", "بروسيسور", "معالج البيانات الرئيسي CPU") to ("The latest processor handles intense computing tasks with ease." to "المعالج الأحدث يتعامل مع مهام الحوسبة الشاقة بسهولة."),
            Triple("Memory", "ميموري", "ذاكرة تخزين / ذاكرة عقلية") to ("Flashcards help build long-term memory." to "البطاقات التعليمية تساعد على بناء ذاكرة طويلة المدى."),
            Triple("Programming", "بروجرامينج", "برمجة وكتابة الشيفرات") to ("Learning programming sharpens logical thinking." to "تعلم البرمجة يصقل التفكير المنطقي."),
            Triple("Function", "فانكشن", "دالة برمجية / وظيفة") to ("This function calculates the final exam grade automatically." to "هذه الدالة تحسب درجة الامتحان النهائي تلقائياً."),
            Triple("Variable", "فاريابل", "متغير برمجي / رياضي") to ("Define a variable to store the user's score." to "عرّف متغيراً لحفظ نتيجة المستخدم."),
            Triple("Array", "أراي", "مصفوفة بيانات منظمة") to ("An array holds multiple items in a single sequential list." to "المصفوفة تحتوي على عناصر متعددة في قائمة متسلسلة واحدة."),
            Triple("Physics", "فيزيكس", "علم الفيزياء الطبيعية") to ("Physics explains how energy, matter, and gravity interact." to "الفيزياء تشرح كيف تتفاعل الطاقة والمادة والجاذبية."),
            Triple("Chemistry", "كيميستري", "علم الكيمياء والتفاعلات") to ("Chemistry explores atomic bonds and chemical reactions." to "الكيمياء تستكشف الروابط الذرية والتفاعلات الكيميائية."),
            Triple("Biology", "بايولوجي", "علم الأحياء والكائنات الحية") to ("Biology examines living cells, genetics, and ecosystems." to "علم الأحياء يفحص الخلايا الحية والجينات والأنظمة البيئية."),
            Triple("Astronomy", "أسترونومي", "علم الفلك والأجرام السماوية") to ("Astronomy reveals the vast wonders of distant galaxies." to "علم الفلك يكشف عجائب المجرات البعيدة الهائلة."),
            Triple("Molecule", "موليكيول", "جزيء كيميائي مركب") to ("Water is formed of hydrogen and oxygen molecules." to "يتكون الماء من جزيئات الهيدروجين والأكسجين."),
            Triple("Atom", "أتوم", "ذرة عنصر كيميائي") to ("An atom consists of protons, neutrons, and electrons." to "تتكون الذرة من بروتونات ونيوترونات وإلكترونات."),
            Triple("Energy", "إنرجي", "طاقة وقدرة على العمل") to ("Renewable energy from the sun is clean and sustainable." to "الطاقة المتجددة من الشمس نظيفة ومستدامة."),
            Triple("Gravity", "جرافيتي", "الجاذبية الأرضية والكونية") to ("Gravity keeps planets orbiting peacefully around stars." to "الجاذبية تبقي الكواكب تدور بانتظام حول النجوم."),
            Triple("Velocity", "فيلوسيتي", "السرعة المتجهة") to ("Calculate the velocity of the moving object in meters per second." to "احسب السرعة المتجهة للجسم المتحرك بالمتر لكل ثانية."),
            Triple("Equation", "إيكويشن", "معادلة رياضية متوازنة") to ("Solve the quadratic equation step by step." to "حل المعادلة التربيعية خطوة بخطوة."),
            Triple("Theorem", "ثيوريم", "مبرهنة ونظرية مثبتة") to ("Pythagorean theorem is fundamental in Euclidean geometry." to "مبرهنة فيثاغورس أساسية في الهندسة الإقليدية."),
            Triple("Geometry", "جيومتري", "علم الهندسة والأشكال") to ("Geometry explores angles, triangles, and spatial dimensions." to "الهندسة تستكشف الزوايا والمثلثات والأبعاد الفراغية."),
            Triple("Calculus", "كالكولوس", "علم التفاضل والتكامل") to ("Calculus models continuous change in physical phenomena." to "حساب التفاضل والتكامل ينمذج التغير المستمر في الظواهر الفيزيائية.")
        )
        techWords.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "noun",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "💻",
                    category = "العلوم والتقنية"
                )
            )
        }

        // 4. Success, Motivation, Character & Productivity
        val successWords = listOf(
            Triple("Determination", "ديتيرمينيشن", "العزيمة وقوة الإرادة") to ("Unwavering determination turns distant dreams into reality." to "العزيمة الثابتة تحول الأحلام البعيدة إلى حقيقة."),
            Triple("Discipline", "ديسيبلين", "الانضباط الذاتي والالتزام") to ("Self-discipline is doing what needs to be done, even when unmotivated." to "الانضباط الذاتي هو فعل ما يلزم حتى بدون حافز لحظي."),
            Triple("Focus", "فوكس", "التركيز وتوجيه الانتباه") to ("Maintain sharp focus on your most important goals." to "حافظ على تركيز حاد تجاه أهدافك الأكثر أهمية."),
            Triple("Ambition", "أمبيشن", "الطموح وعلو الهمة") to ("Her high ambition drives her to master multiple languages." to "طموحها العالي يدفعها لإتقان لغات متعددة."),
            Triple("Consistency", "كونسيستنسي", "الاستمرارية والمواظبة") to ("Small daily efforts repeated with consistency yield massive results." to "الجهود اليومية الصغيرة المكررة باستمرارية تحقق نتائج هائلة."),
            Triple("Patience", "بيشنس", "الصبر الجميل والتحمل") to ("Patience and hard work are the keys to mastery." to "الصبر والعمل الجاد هما مفاتيح الإتقان والتفوق."),
            Triple("Integrity", "إنتيجريتي", "النزاهة والصدق والمبدأ") to ("Act with integrity even when no one is watching." to "تصرّف بنزاهة حتى حينما لا يراك أحد."),
            Triple("Gratitude", "جراتيتيود", "الشكر والامتنان لله") to ("Practicing gratitude fills your heart with peace and joy." to "ممارسة الامتنان تملأ قلبك سكينة وفرحاً."),
            Triple("Humility", "هيوميليتي", "التواضع ولين الجانب") to ("True greatness is always accompanied by humility." to "العظمة الحقيقية تقترن دائماً بالتواضع."),
            Triple("Generosity", "جينيروسيتي", "الكرم والجود والعطاء") to ("Generosity enriches the giver as much as the receiver." to "الكرم يثري المعطي بقدر ما ينفع الآخذ."),
            Triple("Confidence", "كونفيدنس", "الثقة بالنفس واليقين") to ("Preparation builds unbreakable confidence before speaking." to "الاستعداد المسبق يبني ثقة لا تتزعزع قبل التحدث."),
            Triple("Optimism", "أوبتيميزم", "التفاؤل وحسن الظن") to ("Optimism gives you the courage to try again after failure." to "التفاؤل يمنحك الشجاعة للمحاولة مجدداً بعد العثرة."),
            Triple("Courage", "كوريج", "الشجاعة والإقدام") to ("Courage is not the absence of fear, but triumph over it." to "الشجاعة ليست غياب الخوف، بل الانتصار عليه."),
            Triple("Wisdom", "ويزدوم", "الحكمة وفصل الخطاب") to ("Wisdom comes from learning from past mistakes." to "الحكمة تأتي من استخلاص الدروس من أخطاء الماضي."),
            Triple("Leadership", "ليدرشيب", "القيادة والتأثير الإيجابي") to ("True leadership serves others and empowers them to grow." to "القيادة الحقيقية تخدم الآخرين وتمكنهم من النمو."),
            Triple("Honesty", "أونستي", "الصدق والأمانة") to ("Honesty builds lasting trust in all human relationships." to "الصدق يبني ثقة دائمة في كافة العلاقات الإنسانية.")
        )
        successWords.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "noun",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "🌟",
                    category = "النجاح والتحفيز"
                )
            )
        }

        // 5. Essential Verbs
        val keyVerbs = listOf(
            Triple("Achieve", "أتشيف", "يحقق / ينجز هدفاً") to ("You can achieve anything with dedicated daily practice." to "يمكنك تحقيق أي شيء بالتدريب اليومي المخلص."),
            Triple("Understand", "أندرستاند", "يفهم / يستوعب بعمق") to ("Read carefully to understand the core concept." to "اقرأ بعناية لتفهم المفهوم الجوهري."),
            Triple("Memorize", "ميمورايز", "يحفظ عن ظهر قلب") to ("Spaced repetition helps memorize vocabulary permanently." to "التكرار المتباعد يساعد على حفظ المفردات بشكل دائم."),
            Triple("Improve", "إمبروف", "يحسّن / يطوّر المهارة") to ("Daily listening will improve your English accent." to "الاستماع اليومي سيحسن لهجتك الإنجليزية."),
            Triple("Organize", "أورجانايز", "ينظم / يرتّب الأولويات") to ("Organize your study schedule to balance all subjects." to "نظّم جدول مذاكرتك لتوازن بين جميع المواد."),
            Triple("Overcome", "أوفركام", "يتغلب على / يجتاز الصعاب") to ("Strong willpower helps you overcome any obstacle." to "الإرادة القوية تساعدك على التغلب على أي عقبة."),
            Triple("Discover", "ديسكافر", "يكتشف / يعثر على المعرفة") to ("Curiosity motivates students to discover new science." to "الفضول يحفز الطلاب على اكتشاف علوم جديدة."),
            Triple("Succeed", "سكسيد", "ينجح / يتفوق بجدارة") to ("Those who believe in their potential will succeed." to "أولئك الذين يؤمنون بقدراتهم سينجحون بالتأكيد."),
            Triple("Listen", "ليسن", "يستمع بإنصات وتركيز") to ("Listen attentively during class discussions." to "استمع بإنصات أثناء نقاشات الفصل."),
            Triple("Speak", "سبيك", "يتحدث / ينطق بطلاقة") to ("Practice speaking English with your friends daily." to "مارس التحدث بالإنجليزية مع أصدقائك يومياً."),
            Triple("Write", "رايت", "يكتب / يدون الأفكار") to ("Writing down your goals increases the likelihood of reaching them." to "كتابة أهدافك تزيد من احتمالية تحقيقها."),
            Triple("Create", "كرييت", "يبتكر / ينشئ من العدم") to ("Use your imagination to create wonderful projects." to "استخدم خيالك لابتكار مشاريع رائعة."),
            Triple("Inspire", "إنسباير", "يلهم / يشعل الحماس") to ("Great leaders inspire others to reach their full potential." to "القادة العظماء يلهمون الآخرين للوصول لأقصى طاقاتهم."),
            Triple("Believe", "بيليف", "يؤمن / يثق بالقدرة") to ("Believe in yourself and take decisive action." to "آمن بنفسك واتخذ خطوات حاسمة."),
            Triple("Explore", "إكسبلور", "يستكشف / يتعرف على الجديد") to ("Explore new books and expand your horizons." to "استكشف كتباً جديدة ووسّع آفاق معرفتك.")
        )
        keyVerbs.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "verb",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "⚡",
                    category = "أفعال أساسية"
                )
            )
        }

        // 6. Essential Adjectives
        val keyAdjectives = listOf(
            Triple("Brilliant", "بريليانت", "عبقري / متألق وذكي جداً") to ("She came up with a brilliant solution to the puzzle." to "توصلت إلى حل عبقري ومبتكر للغز."),
            Triple("Diligent", "ديليجنت", "مجتهد ومثابر في عمله") to ("A diligent student always finishes assignments early." to "الطالب المجتهد دائماً ينهي واجباته مبكراً."),
            Triple("Courageous", "كوريجاس", "شجاع ومقدام ولا يهاب") to ("The courageous firefighter saved the trapped family." to "أنقذ رجل الإطفاء الشجاع العائلة المحاصرة."),
            Triple("Magnificent", "ماجنيفيسنت", "رائع / عظيم وفخم") to ("The sunrise over the sea was a magnificent sight." to "كان شروق الشمس فوق البحر مشهداً رائعاً وفخماً."),
            Triple("Crucial", "كروشيال", "حاسم ومصيري وبالغ الأهمية") to ("Sleep is crucial for memory consolidation after studying." to "النوم حاسم لتثبيت الذاكرة بعد المذاكرة."),
            Triple("Generous", "جينيرس", "كريم ومعطاء ونبيل") to ("He is generous with both his wealth and his time." to "هو كريم بماله ووقته على السواء."),
            Triple("Productive", "بروداكتيف", "مُنتج وفعال ومثمر") to ("Early mornings are the most productive hours for study." to "ساعات الصباح الباكر هي الأكثر إنتاجية للدراسة."),
            Triple("Honest", "أونست", "صادق وأمين ومستقيم") to ("Always give an honest answer, even when it is difficult." to "أعطِ دائماً إجابة صادقة حتى عندما يكون الأمر صعباً."),
            Triple("Optimistic", "أوبتيميستيك", "متفائل ومستبشر بالخير") to ("Stay optimistic despite temporary setbacks." to "ابقَ متفائلاً على الرغم من العثرات المؤقتة."),
            Triple("Profound", "بروفاوند", "عميق المعنى / بالغ الأثر") to ("The scholar shared a profound truth about life." to "شارك العالم حقيقة عميقة وذات أثر عن الحياة.")
        )
        keyAdjectives.forEach { (meta, ex) ->
            list.add(
                EnglishWordItem(
                    english = meta.first,
                    arabicPronunciation = meta.second,
                    arabicMeaning = meta.third,
                    phonetic = "/${meta.first.lowercase()}/",
                    partOfSpeech = "adjective",
                    exampleEn = ex.first,
                    exampleAr = ex.second,
                    emoji = "💎",
                    category = "صفات هامة"
                )
            )
        }

        // 7. Expanded Oxford 3000 Vocabulary
        val oxfordPairs = listOf(
            Pair("Ability", Pair("أبيليتي", "قدرة واستطاعة")),
            Pair("Abroad", Pair("أبرود", "في الخارج / خارج البلاد")),
            Pair("Academic", Pair("أكاديميك", "أكاديمي وتعليمي")),
            Pair("Access", Pair("أكسس", "وصول ومدخل")),
            Pair("Accident", Pair("أكسيدنت", "حادث عارض")),
            Pair("Accurate", Pair("أكيوريت", "دقيق ومضبوط")),
            Pair("Achieve", Pair("أتشيف", "ينجز ويحقق")),
            Pair("Action", Pair("أكشن", "فعل وعمل وحركة")),
            Pair("Active", Pair("أكتيف", "نشيط وفعال")),
            Pair("Activity", Pair("أكتيفيتي", "نشاط وفعالية")),
            Pair("Actor", Pair("أكتور", "ممثل مسرحي")),
            Pair("Actual", Pair("أكتشوال", "فعلي وحقيقي")),
            Pair("Adapt", Pair("أدابت", "يتكيف ويتأقلم")),
            Pair("Add", Pair("أد", "يضيف ويزيد")),
            Pair("Addition", Pair("أديشن", "إضافة وعملية جمع")),
            Pair("Address", Pair("أدريس", "عنوان / يوجّه خطاباً")),
            Pair("Adequate", Pair("أديكويت", "كافٍ ومناسب")),
            Pair("Adjust", Pair("أدجاست", "يعدل ويضبط")),
            Pair("Admire", Pair("أدماير", "يعجب بـ ويقدّر")),
            Pair("Admission", Pair("أدميشن", "قبول واعتراف")),
            Pair("Adopt", Pair("أدوبت", "يتبنى فكرة أو نهجاً")),
            Pair("Adult", Pair("أدالت", "بالغ وراشد")),
            Pair("Advance", Pair("أدفانس", "يتقدم ويتطور")),
            Pair("Advantage", Pair("أدفانتج", "ميزة وفائدة")),
            Pair("Adventure", Pair("أدفنتشر", "مغامرة وتجربة")),
            Pair("Advice", Pair("أدفايس", "نصيحة وإرشاد")),
            Pair("Advise", Pair("أدفايز", "ينصح ويوجه")),
            Pair("Affect", Pair("أفكت", "يؤثر على")),
            Pair("Afford", Pair("أفورد", "يتحمل تكلفة")),
            Pair("Afraid", Pair("أفريد", "خائف ووجل")),
            Pair("After", Pair("أفتر", "بعد / عقب")),
            Pair("Afternoon", Pair("أفترنون", "بعد الظهر")),
            Pair("Agency", Pair("إيجنسي", "وكالة وهيئة")),
            Pair("Agenda", Pair("أجيندا", "جدول أعمال")),
            Pair("Agent", Pair("إيجنت", "وكيل وممثل")),
            Pair("Aggressive", Pair("أجريسيف", "هجومي وجريء")),
            Pair("Agree", Pair("أجري", "يوافق ويتفق")),
            Pair("Agreement", Pair("أجريمنت", "اتفاقية ومعاهدة")),
            Pair("Ahead", Pair("أهيد", "في الأمام ومقدماً")),
            Pair("Aid", Pair("إيد", "مساعدة وعون")),
            Pair("Aim", Pair("إيم", "هدف وغاية")),
            Pair("Air", Pair("إير", "هواء وجو")),
            Pair("Aircraft", Pair("إيركرافت", "طائرة جوية")),
            Pair("Airline", Pair("إيرلاين", "شركة طيران")),
            Pair("Airport", Pair("إيربورت", "مطار جوي")),
            Pair("Album", Pair("ألبوم", "ألبوم صور وتسجيلات")),
            Pair("Alert", Pair("أليّرت", "منبه ومتيقظ")),
            Pair("Alien", Pair("إيليان", "أجنبي وغريب")),
            Pair("Align", Pair("ألاين", "يحاذي وينسق")),
            Pair("Alive", Pair("ألايف", "حي ونابض بالحياة")),
            Pair("Allow", Pair("ألاو", "يسمح ويأذن")),
            Pair("Ally", Pair("ألاي", "حليف وصديق")),
            Pair("Almost", Pair("أولموست", "تقريباً وعلى وشك")),
            Pair("Alone", Pair("ألون", "بمفرده ووحيداً")),
            Pair("Already", Pair("أولريدي", "بالفعل ومسبقاً")),
            Pair("Alternative", Pair("ألترناتيف", "بديل وخيار آخر")),
            Pair("Always", Pair("أولويز", "دائماً وعلى الدوام")),
            Pair("Amazing", Pair("أميزنج", "مدهش ومبهر")),
            Pair("Ambassador", Pair("أمباسادور", "سفير ودبلوماسي")),
            Pair("Ambulance", Pair("أمبيولانس", "سيارة إسعاف")),
            Pair("Amount", Pair("أماونت", "كمية ومقدار")),
            Pair("Analyze", Pair("أنالايز", "يحلل ويفحص")),
            Pair("Ancient", Pair("إنشنت", "قديم وأثري")),
            Pair("Anger", Pair("أنجر", "غضب وحنق")),
            Pair("Angle", Pair("أنجل", "زاوية هندسية")),
            Pair("Animal", Pair("أنيمال", "حيوان وكائن حي")),
            Pair("Announce", Pair("أناونس", "يعلن ويصرح")),
            Pair("Annual", Pair("أنيوال", "سنوي ودوري")),
            Pair("Answer", Pair("أنسر", "إجابة وجواب")),
            Pair("Anxious", Pair("أنكشس", "قلق ومتوتر")),
            Pair("Appeal", Pair("أبيل", "نداء وجاذبية واستئناف")),
            Pair("Appear", Pair("أبير", "يظهر ويبدو")),
            Pair("Application", Pair("أبليكيشن", "تطبيق وطلب تقديم")),
            Pair("Apply", Pair("أبلاي", "يطبق ويقدّم طلب")),
            Pair("Appoint", Pair("أبوينت", "يعين ويوظف")),
            Pair("Approach", Pair("أبروتش", "نهج وأسلوب يقترب")),
            Pair("Approve", Pair("أبروف", "يوافق ويعتمد")),
            Pair("Architect", Pair("أركيتكت", "مهندس معماري")),
            Pair("Area", Pair("إيريا", "منطقة ومساحة")),
            Pair("Argue", Pair("أرجيو", "يجادل ويناقش")),
            Pair("Argument", Pair("أرجومنت", "حجة وجدال")),
            Pair("Army", Pair("أرمي", "جيش وقوات مسلحة")),
            Pair("Arrange", Pair("أرينج", "يرتب وينسق")),
            Pair("Arrest", Pair("أريست", "يقبض على ويعتقل")),
            Pair("Arrival", Pair("أرايفال", "وصول وحضور")),
            Pair("Arrive", Pair("أرايف", "يصل ويحط الرحال")),
            Pair("Article", Pair("أرتيكل", "مقال ومادة نظامية")),
            Pair("Artist", Pair("أرتيست", "فنان ومبدع")),
            Pair("Aspire", Pair("أسباير", "يطمح ويتطلع")),
            Pair("Assemble", Pair("أسمبل", "يجمع ويركب")),
            Pair("Assess", Pair("أسيس", "يقيم ويقدّر")),
            Pair("Asset", Pair("أسيت", "أصل وموجودات ثمينة")),
            Pair("Assign", Pair("أساين", "يكلف ويسند مهمة")),
            Pair("Assist", Pair("أسيست", "يساعد ويعاون")),
            Pair("Assume", Pair("أسيوم", "يفترض ويسلم جدلاً")),
            Pair("Assure", Pair("أشور", "يؤكد ويطمئن")),
            Pair("Astonish", Pair("أستونيش", "يذهل ويدهش")),
            Pair("Athlete", Pair("أثليت", "رياضي ولاعب")),
            Pair("Atmosphere", Pair("أتموسفير", "غلاف جوي وبيئة")),
            Pair("Attach", Pair("أتاتش", "يرفق ويوصل")),
            Pair("Attack", Pair("أتاك", "هجوم واعتداء")),
            Pair("Attempt", Pair("أتيمبت", "محاولة وسعي")),
            Pair("Attend", Pair("أتيند", "يحضر ويشارك")),
            Pair("Attention", Pair("أتنشن", "انتباه وتركيز")),
            Pair("Attitude", Pair("أتيتيود", "موقف وسلوك ذهني")),
            Pair("Attract", Pair("أتراكت", "يجذب ويلفت")),
            Pair("Auction", Pair("أوكشن", "مزاد علني")),
            Pair("Audio", Pair("أوديو", "صوت وتسجيل مسموع")),
            Pair("Audit", Pair("أوديت", "تدقيق ومراجعة حسابات")),
            Pair("Audience", Pair("أودينس", "جمهور وحضور")),
            Pair("Author", Pair("أوثر", "مؤلف وكاتب")),
            Pair("Authority", Pair("أوثوريتي", "سلطة وهيئة مسؤولة")),
            Pair("Automate", Pair("أوتوميت", "يؤتمت ويشغل آلياً")),
            Pair("Available", Pair("أفيبلبل", "متاح ومتوفر")),
            Pair("Avenue", Pair("أفينيو", "شارع عريض وطريق")),
            Pair("Average", Pair("أفرج", "معدل ومتوسط")),
            Pair("Avoid", Pair("أفويد", "يتجنب ويتحاشى")),
            Pair("Await", Pair("أويت", "ينتظر ويترقب")),
            Pair("Awake", Pair("أويك", "مستيقظ ومتنبه")),
            Pair("Award", Pair("أوارد", "جائزة ومكافأة")),
            Pair("Aware", Pair("أوير", "واعٍ ومدرك")),
            Pair("Awesome", Pair("أوسم", "رائع ومذهل")),
            Pair("Awful", Pair("أوفل", "سيئ ومريع"))
        )

        oxfordPairs.forEach { item ->
            val word = item.first
            val pron = item.second.first
            val mean = item.second.second
            list.add(
                EnglishWordItem(
                    english = word,
                    arabicPronunciation = pron,
                    arabicMeaning = mean,
                    phonetic = "/${word.lowercase()}/",
                    partOfSpeech = "general",
                    exampleEn = "Learning the word '$word' enriches your vocabulary bank.",
                    exampleAr = "تعلم كلمة '$word' يثري حصيلتك اللغوية اليومية.",
                    emoji = "📖",
                    category = "قاموس أكسفورد الأساسي"
                )
            )
        }

        return list
    }
}
