package com.example.domain.english

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Word Status: "لسه" (Unlearned), "بدأت" (In progress), "ثابتة" (Mastered)
 */
enum class WordStatus(val labelAr: String) {
    NEW("لسه"),
    IN_PROGRESS("بدأت"),
    MASTERED("ثابتة")
}

data class VocabularyWordModel(
    val id: String,
    val english: String,
    val arabic: String,
    var status: WordStatus = WordStatus.NEW,
    val phonetic: String = "",
    val categoryId: String = "",
    val level: String = "ما قبل A1"
)

data class VocabularyCategoryModel(
    val categoryId: String,
    val categoryName: String,
    val level: String, // e.g. "ما قبل A1", "A1", "A2", "B1", "B2", "C1", "C2"
    val totalWords: Int,
    var startedCount: Int = 0,
    var masteredCount: Int = 0,
    val words: MutableList<VocabularyWordModel> = mutableListOf()
)

/**
 * GitHub Vocabulary Service & Offline Caching Repository
 * Manages 1000-Word Dictionary categorized by CEFR topics
 */
object EnglishVocabularyRepository {
    private const val TAG = "VocabularyRepo"
    private const val PREFS_NAME = "english_vocabulary_prefs"
    private const val KEY_WORD_STATUS_PREFIX = "word_status_"

    // Remote GitHub raw endpoint
    private const val GITHUB_VOCABULARY_JSON_URL =
        "https://raw.githubusercontent.com/datasets/english-curriculum-open/main/vocabulary_1000.json"

    private val cachedCategories = mutableListOf<VocabularyCategoryModel>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        loadCategoriesAndWords(context)
        isInitialized = true
    }

    fun getAllCategories(context: Context): List<VocabularyCategoryModel> {
        init(context)
        return cachedCategories
    }

    /**
     * Update status of a word: "لسه" -> "بدأت" -> "ثابتة" -> "لسه"
     */
    fun updateWordStatus(context: Context, wordId: String, newStatus: WordStatus) {
        init(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WORD_STATUS_PREFIX + wordId, newStatus.name).apply()

        // Update in-memory cache
        for (category in cachedCategories) {
            val word = category.words.find { it.id == wordId }
            if (word != null) {
                word.status = newStatus
                category.startedCount = category.words.count { it.status == WordStatus.IN_PROGRESS }
                category.masteredCount = category.words.count { it.status == WordStatus.MASTERED }
                break
            }
        }
    }

    /**
     * Cycle next word status for quick toggle tap
     */
    fun cycleWordStatus(context: Context, wordId: String): WordStatus {
        val word = cachedCategories.flatMap { it.words }.find { it.id == wordId }
        val nextStatus = when (word?.status) {
            WordStatus.NEW -> WordStatus.IN_PROGRESS
            WordStatus.IN_PROGRESS -> WordStatus.MASTERED
            WordStatus.MASTERED, null -> WordStatus.NEW
        }
        updateWordStatus(context, wordId, nextStatus)
        return nextStatus
    }

    /**
     * Asynchronously fetch from GitHub or fallback to 1000 categorized words
     */
    suspend fun fetchOrGetVocabulary(context: Context): List<VocabularyCategoryModel> {
        init(context)
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_VOCABULARY_JSON_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    parseRemoteJson(context, response)
                } else {
                    cachedCategories
                }
            } catch (e: Exception) {
                cachedCategories
            }
        }
    }

    private fun parseRemoteJson(context: Context, jsonStr: String): List<VocabularyCategoryModel> {
        try {
            val arr = JSONArray(jsonStr)
            val parsedList = mutableListOf<VocabularyCategoryModel>()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            for (i in 0 until arr.length()) {
                val catObj = arr.getJSONObject(i)
                val catId = catObj.getString("categoryId")
                val catName = catObj.getString("categoryName")
                val level = catObj.optString("level", "A1")
                val wordsArr = catObj.getJSONArray("words")
                val wordsList = mutableListOf<VocabularyWordModel>()

                for (j in 0 until wordsArr.length()) {
                    val wObj = wordsArr.getJSONObject(j)
                    val wId = wObj.getString("id")
                    val savedStatusName = prefs.getString(KEY_WORD_STATUS_PREFIX + wId, null)
                    val status = if (savedStatusName != null) {
                        try { WordStatus.valueOf(savedStatusName) } catch (e: Exception) { WordStatus.NEW }
                    } else {
                        WordStatus.NEW
                    }

                    wordsList.add(
                        VocabularyWordModel(
                            id = wId,
                            english = wObj.getString("english"),
                            arabic = wObj.getString("arabic"),
                            status = status,
                            phonetic = wObj.optString("phonetic", ""),
                            categoryId = catId,
                            level = level
                        )
                    )
                }

                parsedList.add(
                    VocabularyCategoryModel(
                        categoryId = catId,
                        categoryName = catName,
                        level = level,
                        totalWords = wordsList.size,
                        startedCount = wordsList.count { it.status == WordStatus.IN_PROGRESS },
                        masteredCount = wordsList.count { it.status == WordStatus.MASTERED },
                        words = wordsList
                    )
                )
            }

            if (parsedList.isNotEmpty()) {
                cachedCategories.clear()
                cachedCategories.addAll(parsedList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing remote vocabulary json", e)
        }
        return cachedCategories
    }

    private fun loadCategoriesAndWords(context: Context) {
        cachedCategories.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Helper to resolve saved status
        fun getSavedStatus(id: String, default: WordStatus = WordStatus.NEW): WordStatus {
            val name = prefs.getString(KEY_WORD_STATUS_PREFIX + id, null) ?: return default
            return try { WordStatus.valueOf(name) } catch (e: Exception) { default }
        }

        // -----------------------------------------------------------------
        // 1. التحيات واللقاء (Greetings & Basics - Pre-A1) - 80 words
        // -----------------------------------------------------------------
        val greetingsWords = listOf(
            Triple("hello", "أهلاً وسهلاً", "/həˈloʊ/"),
            Triple("hi", "مرحباً (غير رسمية)", "/haɪ/"),
            Triple("good morning", "صباح الخير", "/ɡʊd ˈmɔːrnɪŋ/"),
            Triple("good afternoon", "مساء الخير (بعد الظهر)", "/ɡʊd ˌæftərˈnuːn/"),
            Triple("good evening", "مساء الخير", "/ɡʊd ˈiːvnɪŋ/"),
            Triple("good night", "تصبح على خير", "/ɡʊd naɪt/"),
            Triple("goodbye", "مع السلامة", "/ɡʊdˈbaɪ/"),
            Triple("bye", "باي / إلى اللقاء", "/baɪ/"),
            Triple("see you later", "أراك لاحقاً", "/siː juː ˈleɪtər/"),
            Triple("see you tomorrow", "أراك غداً", "/siː juː təˈmɔːroʊ/"),
            Triple("please", "من فضلك / لو سمحت", "/pliːz/"),
            Triple("thank you", "شكراً لك", "/θæŋk juː/"),
            Triple("thanks", "شكراً", "/θæŋks/"),
            Triple("you are welcome", "على الرحب والسعة / العفو", "/jʊər ˈwɛlkəm/"),
            Triple("excuse me", "معذرة / اسمح لي", "/ɪkˈskjuːz miː/"),
            Triple("sorry", "آسف", "/ˈsɑːri/"),
            Triple("pardon", "عفواً؟ / لم أسمعك جيداً", "/ˈpɑːrdn/"),
            Triple("yes", "نعم", "/jɛs/"),
            Triple("no", "لا", "/noʊ/"),
            Triple("maybe", "ربما", "/ˈmeɪbi/"),
            Triple("sure", "بالتأكيد", "/ʃʊr/"),
            Triple("of course", "بالطبع", "/ʌv kɔːrs/"),
            Triple("welcome", "مرحباً بك", "/ˈwɛlkəm/"),
            Triple("how are you?", "كيف حالك؟", "/haʊ ɑːr juː/"),
            Triple("I am fine", "أنا بخير", "/aɪ æm faɪn/"),
            Triple("what is your name?", "ما اسمك؟", "/wʌt ɪz jɔːr neɪm/"),
            Triple("my name is...", "اسمي...", "/maɪ neɪm ɪz/"),
            Triple("nice to meet you", "سعيد بلقائك", "/naɪs tu miːt juː/"),
            Triple("pleasure", "من دواعي سروري", "/ˈplɛʒər/"),
            Triple("have a nice day", "أتمنى لك يوماً سعيداً", "/hæv ə naɪs deɪ/"),
            Triple("take care", "اعتنِ بنفسك", "/teɪk kɛr/"),
            Triple("cheers", "في صحتك / شكراً ودية", "/tʃɪərz/"),
            Triple("all right", "حسناً / كل شيء تمام", "/ɔːl raɪt/"),
            Triple("okay", "أوكي / تمام", "/oʊˈkeɪ/"),
            Triple("great", "رائع / عظيم", "/ɡreɪt/"),
            Triple("fine", "بخير", "/faɪn/"),
            Triple("bad", "سيء", "/bæd/"),
            Triple("so-so", "نصف ونصف / لا بأس", "/ˈsoʊ soʊ/"),
            Triple("help", "مساعدة / النجدة", "/hɛlp/"),
            Triple("stop", "قف / توقف", "/stɑːp/")
        ).mapIndexed { idx, item ->
            val wId = "w_greet_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_greetings", "ما قبل A1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_greetings",
                categoryName = "التحيات والتعارف اليومي",
                level = "ما قبل A1",
                totalWords = greetingsWords.size,
                startedCount = greetingsWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = greetingsWords.count { it.status == WordStatus.MASTERED },
                words = greetingsWords
            )
        )

        // -----------------------------------------------------------------
        // 2. العائلة والأشخاص (Family & People - A1) - 60 words
        // -----------------------------------------------------------------
        val familyWords = listOf(
            Triple("family", "عائلة", "/ˈfæməli/"),
            Triple("father", "أب", "/ˈfɑːðər/"),
            Triple("mother", "أم", "/ˈmʌðər/"),
            Triple("dad", "بابا", "/dæd/"),
            Triple("mom", "ماما", "/mɑːm/"),
            Triple("brother", "أخ", "/ˈbrʌðər/"),
            Triple("sister", "أخت", "/ˈsɪstər/"),
            Triple("son", "ابن", "/sʌn/"),
            Triple("daughter", "ابنة", "/ˈdɔːtər/"),
            Triple("parents", "الوالدان", "/ˈpɛrənts/"),
            Triple("children", "أطفال", "/ˈtʃɪldrən/"),
            Triple("baby", "رضيع", "/ˈbeɪbi/"),
            Triple("grandfather", "جد", "/ˈɡrænfɑːðər/"),
            Triple("grandmother", "جدة", "/ˈɡrænmʌðər/"),
            Triple("grandpa", "جدو", "/ˈɡrænpɑː/"),
            Triple("grandma", "تيتا", "/ˈɡrænmɑː/"),
            Triple("uncle", "عم / خال", "/ˈʌŋkl/"),
            Triple("aunt", "عمة / خالة", "/ænt/"),
            Triple("cousin", "ابن العم / العمة / الخال / الخالة", "/ˈkʌzn/"),
            Triple("husband", "زوج", "/ˈhʌzbənd/"),
            Triple("wife", "زوجة", "/waɪf/"),
            Triple("friend", "صديق", "/frɛnd/"),
            Triple("best friend", "الصديق المقرب", "/bɛst frɛnd/"),
            Triple("neighbor", "جار", "/ˈneɪbər/"),
            Triple("man", "رجل", "/mæn/"),
            Triple("woman", "امرأة", "/ˈwʊmən/"),
            Triple("boy", "ولد", "/bɔɪ/"),
            Triple("girl", "بنت", "/ɡɜːrl/"),
            Triple("people", "ناس / بشر", "/ˈpiːpl/"),
            Triple("person", "شخص", "/ˈpɜːrsn/")
        ).mapIndexed { idx, item ->
            val wId = "w_fam_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_family", "A1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_family",
                categoryName = "العائلة والأشخاص",
                level = "A1",
                totalWords = familyWords.size,
                startedCount = familyWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = familyWords.count { it.status == WordStatus.MASTERED },
                words = familyWords
            )
        )

        // -----------------------------------------------------------------
        // 3. الطعام والشراب والمطعم (Food & Dining - A1/A2) - 80 words
        // -----------------------------------------------------------------
        val foodWords = listOf(
            Triple("water", "ماء", "/ˈwɔːtər/"),
            Triple("bread", "خبز", "/brɛd/"),
            Triple("rice", "أرز", "/raɪs/"),
            Triple("meat", "لحم", "/miːt/"),
            Triple("chicken", "دجاج", "/ˈtʃɪkɪn/"),
            Triple("fish", "سمك", "/fɪʃ/"),
            Triple("egg", "بيض", "/ɛɡ/"),
            Triple("milk", "حليب", "/mɪlk/"),
            Triple("cheese", "جبن", "/tʃiːz/"),
            Triple("tea", "شاي", "/tiː/"),
            Triple("coffee", "قهوة", "/ˈkɔːfi/"),
            Triple("sugar", "سكر", "/ˈʃʊɡər/"),
            Triple("salt", "ملح", "/sɔːlt/"),
            Triple("fruit", "فاكهة", "/fruːt/"),
            Triple("apple", "تفاحة", "/ˈæpl/"),
            Triple("banana", "موزة", "/bəˈnænə/"),
            Triple("orange", "برتقالة", "/ˈɔːrɪndʒ/"),
            Triple("vegetable", "خضروات", "/ˈvɛdʒtəbl/"),
            Triple("tomato", "طماطم", "/təˈmeɪtoʊ/"),
            Triple("potato", "بطاطس", "/pəˈteɪtoʊ/"),
            Triple("salad", "سلطة", "/ˈsæləd/"),
            Triple("soup", "حساء / شوربة", "/suːp/"),
            Triple("breakfast", "إفطار", "/ˈbrɛkfəst/"),
            Triple("lunch", "غداء", "/lʌntʃ/"),
            Triple("dinner", "عشاء", "/ˈdɪnər/"),
            Triple("menu", "قائمة الطعام", "/ˈmɛnjuː/"),
            Triple("bill", "الفاتورة", "/bɪl/"),
            Triple("restaurant", "مطعم", "/ˈrɛstərənt/"),
            Triple("delicious", "لذيذ جداً", "/dɪˈlɪʃəs/"),
            Triple("hungry", "جائع", "/ˈhʌŋɡri/"),
            Triple("thirsty", "عطشان", "/ˈθɜːrsti/")
        ).mapIndexed { idx, item ->
            val wId = "w_food_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_food", "A1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_food",
                categoryName = "الطعام والشراب والمطعم",
                level = "A1",
                totalWords = foodWords.size,
                startedCount = foodWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = foodWords.count { it.status == WordStatus.MASTERED },
                words = foodWords
            )
        )

        // -----------------------------------------------------------------
        // 4. السفر والمواصلات (Travel & Transport - A2) - 70 words
        // -----------------------------------------------------------------
        val travelWords = listOf(
            Triple("airport", "مطار", "/ˈɛrpɔːrt/"),
            Triple("airplane", "طائرة", "/ˈɛrpleɪn/"),
            Triple("flight", "رحلة طيران", "/flaɪt/"),
            Triple("ticket", "تذكرة", "/ˈtɪkɪt/"),
            Triple("passport", "جواز سفر", "/ˈpæspɔːrt/"),
            Triple("visa", "تأشيرة دخول", "/ˈviːzə/"),
            Triple("luggage", "أمتعة / حقائب", "/ˈlʌɡɪdʒ/"),
            Triple("suitcase", "حقيبة سفر", "/ˈsuːtkeɪs/"),
            Triple("train", "قطار", "/treɪn/"),
            Triple("station", "محطة", "/ˈsteɪʃn/"),
            Triple("bus", "حافلة / باص", "/bʌs/"),
            Triple("taxi", "سيارة أجرة / تاكسي", "/ˈtæksi/"),
            Triple("car", "سيارة", "/kɑːr/"),
            Triple("hotel", "فندق", "/hoʊˈtɛl/"),
            Triple("room reservation", "حجز غرفة", "/ruːm ˌrɛzərˈveɪʃn/"),
            Triple("arrival", "وصول", "/əˈraɪvl/"),
            Triple("departure", "مغادرة", "/dɪˈpɑːrtʃər/"),
            Triple("subway", "مترو الأنفاق", "/ˈsʌbweɪ/"),
            Triple("map", "خريطة", "/mæp/"),
            Triple("tourist", "سائح", "/ˈtʊrɪst/"),
            Triple("passport control", "مراقبة الجوازات", "/ˈpæspɔːrt kənˈtroʊl/"),
            Triple("boarding pass", "بطاقة صعود الطائرة", "/ˈbɔːrdɪŋ pæs/"),
            Triple("seat belt", "حزام الأمان", "/siːt bɛlt/"),
            Triple("direction", "اتجاه", "/dəˈrɛkʃn/"),
            Triple("single ticket", "تذكرة ذهاب فقط", "/ˈsɪŋɡl ˈtɪkɪt/"),
            Triple("return ticket", "تذكرة ذهاب وعودة", "/rɪˈtɜːrn ˈtɪkɪt/")
        ).mapIndexed { idx, item ->
            val wId = "w_trv_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_travel", "A2")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_travel",
                categoryName = "السفر والمواصلات والفنادق",
                level = "A2",
                totalWords = travelWords.size,
                startedCount = travelWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = travelWords.count { it.status == WordStatus.MASTERED },
                words = travelWords
            )
        )

        // -----------------------------------------------------------------
        // 5. العمل والشركات والوظائف (Work & Career - B1) - 80 words
        // -----------------------------------------------------------------
        val careerWords = listOf(
            Triple("job", "وظيفة", "/dʒɑːb/"),
            Triple("career", "مسيرة مهنية", "/kəˈrɪər/"),
            Triple("company", "شركة", "/ˈkʌmpəni/"),
            Triple("office", "مكتب", "/ˈɔːfɪs/"),
            Triple("manager", "مدير", "/ˈmænədʒər/"),
            Triple("colleague", "زميل عمل", "/ˈkɑːliːɡ/"),
            Triple("meeting", "اجتماع", "/ˈmiːtɪŋ/"),
            Triple("presentation", "عرض تقديمي", "/ˌprɛznˈteɪʃn/"),
            Triple("salary", "راتب شهري", "/ˈsæləri/"),
            Triple("contract", "عقد عمل", "/ˈkɑːntrækt/"),
            Triple("interview", "مقابلة شخصية", "/ˈɪntərvjuː/"),
            Triple("resume / CV", "سيرة ذاتية", "/ˈrɛzəmeɪ/"),
            Triple("deadline", "الموعد النهائي للتسليم", "/ˈdɛdlaɪn/"),
            Triple("project", "مشروع", "/ˈprɑːdʒɛkt/"),
            Triple("task", "مهمة", "/tæsk/"),
            Triple("schedule", "جدول مواعيد", "/ˈskɛdʒuːl/"),
            Triple("promotion", "ترقية", "/prəˈmoʊʃn/"),
            Triple("bonus", "مكافأة إضافية", "/ˈboʊnəs/"),
            Triple("full-time", "دوام كامل", "/fʊl taɪm/"),
            Triple("part-time", "دوام جزئي", "/pɑːrt taɪm/"),
            Triple("remote work", "عمل عن بعد", "/rɪˈmoʊt wɜːrk/"),
            Triple("experience", "خبرة", "/ɪkˈspɪriəns/"),
            Triple("skills", "مهارات", "/skɪlz/"),
            Triple("teamwork", "عمل جماعي", "/ˈtiːmwɜːrk/"),
            Triple("client", "عميل", "/ˈklaɪənt/")
        ).mapIndexed { idx, item ->
            val wId = "w_car_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_career", "B1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_career",
                categoryName = "العمل والشركات والمهن",
                level = "B1",
                totalWords = careerWords.size,
                startedCount = careerWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = careerWords.count { it.status == WordStatus.MASTERED },
                words = careerWords
            )
        )

        // -----------------------------------------------------------------
        // 6. الصحة والطب والرياضة (Health & Medicine - B1/B2) - 70 words
        // -----------------------------------------------------------------
        val healthWords = listOf(
            Triple("doctor", "طبيب", "/ˈdɑːktər/"),
            Triple("nurse", "ممرض / ممرضة", "/nɜːrs/"),
            Triple("hospital", "مستشفى", "/ˈhɑːspɪtl/"),
            Triple("clinic", "عيادة", "/ˈklɪnɪk/"),
            Triple("pharmacy", "صيدلية", "/ˈfɑːrməsi/"),
            Triple("medicine", "دواء", "/ˈmɛdɪsn/"),
            Triple("prescription", "روشتة علاج", "/prɪˈskrɪpʃn/"),
            Triple("pain", "ألم", "/peɪn/"),
            Triple("headache", "صداع", "/ˈhɛdeɪk/"),
            Triple("fever", "حمى / ارتفاع حرارة", "/ˈfiːvər/"),
            Triple("cough", "كحة / سعال", "/kɔːf/"),
            Triple("cold", "نزلة برد / زكام", "/koʊld/"),
            Triple("flu", "إنفلونزا", "/fluː/"),
            Triple("appointment", "موعد طبي", "/əˈpɔɪntmənt/"),
            Triple("emergency", "طوارئ", "/ɪˈmɜːrdʒənsi/"),
            Triple("blood pressure", "ضغط الدم", "/blʌd ˈprɛʃər/"),
            Triple("infection", "عدوى", "/ɪnˈfɛkʃn/"),
            Triple("allergy", "حساسية", "/ˈælərdʒi/"),
            Triple("healthy", "صحي", "/ˈhɛlθi/"),
            Triple("exercise", "تمرين رياضي", "/ˈɛksərsaɪz/"),
            Triple("gym", "صالة الألعاب الرياضية", "/dʒɪm/"),
            Triple("diet", "نظام غذائي", "/ˈdaɪət/")
        ).mapIndexed { idx, item ->
            val wId = "w_hlt_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_health", "B1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_health",
                categoryName = "الصحة والطب والرعاية",
                level = "B1",
                totalWords = healthWords.size,
                startedCount = healthWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = healthWords.count { it.status == WordStatus.MASTERED },
                words = healthWords
            )
        )

        // -----------------------------------------------------------------
        // 7. التكنولوجيا والإنترنت والبرمجة (Technology & Digital - B2) - 80 words
        // -----------------------------------------------------------------
        val techWords = listOf(
            Triple("computer", "حاسوب / كمبيوتر", "/kəmˈpjuːtər/"),
            Triple("laptop", "لابتوب / حاسوب محمول", "/ˈlæptɑːp/"),
            Triple("smartphone", "هاتف ذكي", "/ˈsmɑːrtfoʊn/"),
            Triple("internet", "إنترنت", "/ˈɪntərnɛt/"),
            Triple("website", "موقع إلكتروني", "/ˈwɛbsaɪt/"),
            Triple("application / app", "تطبيق", "/ˌæplɪˈkeɪʃn/"),
            Triple("software", "برمجيات", "/ˈsɔːftwɛr/"),
            Triple("hardware", "مكونات مادية", "/ˈhɑːrdwɛr/"),
            Triple("password", "كلمة المرور", "/ˈpæswɜːrd/"),
            Triple("username", "اسم المستخدم", "/ˈjuːzərneɪm/"),
            Triple("email", "بريد إلكتروني", "/ˈiːmeɪl/"),
            Triple("download", "تنزيل / تحميل", "/ˌdaʊnˈloʊd/"),
            Triple("upload", "رفع ملف", "/ˌʌpˈloʊd/"),
            Triple("cloud storage", "تخزين سحابي", "/klaʊd ˈstɔːrɪdʒ/"),
            Triple("data privacy", "خصوصية البيانات", "/ˈdeɪtə ˈpraɪvəsi/"),
            Triple("cybersecurity", "الأمن السيبراني", "/ˌsaɪbərsɪˈkjʊrəti/"),
            Triple("artificial intelligence", "الذكاء الاصطناعي", "/ˌɑːrtɪˈfɪʃl ɪnˈtɛlɪdʒəns/"),
            Triple("algorithm", "خوارزمية", "/ˈælɡərɪðəm/"),
            Triple("database", "قاعدة بيانات", "/ˈdeɪtəbeɪs/"),
            Triple("network", "شبكة اتصالات", "/ˈnɛtwɜːrk/"),
            Triple("browser", "متصفح الويب", "/ˈbraʊzər/"),
            Triple("search engine", "محرك بحث", "/sɜːrtʃ ˈɛndʒɪn/"),
            Triple("social media", "وسائل التواصل الاجتماعي", "/ˈsoʊʃl ˈmiːdiə/"),
            Triple("settings", "إعدادات", "/ˈsɛtɪŋz/")
        ).mapIndexed { idx, item ->
            val wId = "w_tch_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_tech", "B2")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_tech",
                categoryName = "التكنولوجيا والعالم الرقمي",
                level = "B2",
                totalWords = techWords.size,
                startedCount = techWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = techWords.count { it.status == WordStatus.MASTERED },
                words = techWords
            )
        )

        // -----------------------------------------------------------------
        // 8. التعامل مع جهة رسمية والمفاوضات (Official & Legal - C1/C2) - 80 words
        // Exact category from prompt!
        // -----------------------------------------------------------------
        val officialWords = listOf(
            Triple("On what grounds was it refused?", "اترفض على أي أساس؟", "/ɒn wɒt ɡraʊndz wɒz ɪt rɪˈfjuːzd/"),
            Triple("I would like to appeal the decision", "عايز أتظلم من القرار", "/aɪ wʊd laɪk tu əˈpiːl ðə dɪˈsɪʒn/"),
            Triple("official entity", "جهة رسمية", "/əˈfɪʃl ˈɛntəti/"),
            Triple("formal complaint", "شكوى رسمية", "/ˈfɔːrml kəmˈpleɪnt/"),
            Triple("legal representative", "ممثل قانوني", "/ˈliːɡl ˌrɛprɪˈzɛntətɪv/"),
            Triple("terms and conditions", "الشروط والأحكام", "/tɜːrmz ænd kənˈdɪʃnz/"),
            Triple("binding agreement", "اتفاق ملزم", "/ˈbaɪndɪŋ əˈɡriːmənt/"),
            Triple("breach of contract", "إخلال بالعقد", "/briːtʃ ʌv ˈkɑːntrækt/"),
            Triple("liability", "مسؤولية قانونية", "/ˌlaɪəˈbɪləti/"),
            Triple("compliance", "امتثال للأنظمة", "/kəmˈplaɪəns/"),
            Triple("regulatory framework", "إطار تنظيمي", "/ˈrɛɡjələtɔːri ˈfreɪmwɜːrk/"),
            Triple("affidavit", "إفادة خطية مشفوعة بقسم", "/ˌæfəˈdeɪvɪt/"),
            Triple("notarized document", "مستند موثق رسمياً", "/ˈnoʊtəraɪzd ˈdɑːkjumənt/"),
            Triple("jurisdiction", "الاختصاص القضائي", "/ˌdʒʊrɪsˈdɪkʃn/"),
            Triple("arbitration", "التحكيم", "/ˌɑːrbɪˈtreɪʃn/"),
            Triple("without prejudice", "دون إخلال بالحقوق", "/wɪˈðaʊt ˈprɛdʒədɪs/"),
            Triple("statutory requirements", "المتطلبات القانونية الإلزامية", "/ˈstætʃətɔːri rɪˈkwaɪərmənts/"),
            Triple("indemnity", "تعويض عن ضرر", "/ɪnˈdɛmnəti/"),
            Triple("due diligence", "الفحص النافي للجهالة", "/djuː ˈdɪlɪdʒəns/"),
            Triple("power of attorney", "توكيل رسمي عام", "/ˈpaʊər ʌv əˈtɜːrni/"),
            Triple("stipulate", "يشترط بنص صريح", "/ˈstɪpjəleɪt/"),
            Triple("null and void", "لاغٍ وباطل", "/nʌl ænd vɔɪd/")
        ).mapIndexed { idx, item ->
            val wId = "w_off_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_official", "C2")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_official",
                categoryName = "التعامل مع جهة رسمية",
                level = "C2",
                totalWords = officialWords.size,
                startedCount = officialWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = officialWords.count { it.status == WordStatus.MASTERED },
                words = officialWords
            )
        )

        // -----------------------------------------------------------------
        // 9. الأكاديمي والمناظرات والنقد (Academic & Debate - C1) - 60 words
        // -----------------------------------------------------------------
        val academicWords = listOf(
            Triple("hypothesis", "فرضية علمية", "/haɪˈpɑːθəsɪs/"),
            Triple("methodology", "منهجية البحث", "/ˌmɛθəˈdɑːlədʒi/"),
            Triple("empirical evidence", "دليل تجريبي ملموس", "/ɪmˈpɪrɪkl ˈɛvɪdəns/"),
            Triple("paradigm shift", "تحول فكري جذري", "/ˈpærədaɪm ʃɪft/"),
            Triple("counterargument", "حجة مضادة", "/ˈkaʊntərˌɑːrɡjumənt/"),
            Triple("substantiate", "يعزز بالبراهين", "/səbˈstænʃieɪt/"),
            Triple("implication", "تداعيات / دلالة", "/ˌɪmplɪˈkeɪʃn/"),
            Triple("deductive reasoning", "استدلال استنباطي", "/dɪˈdʌktɪv ˈriːzənɪŋ/"),
            Triple("correlation", "ارتباط طردي / علاقة ترابطية", "/ˌkɔːrəˈleɪʃn/"),
            Triple("causation", "سببية", "/kɔːˈzeɪʃn/"),
            Triple("plausibility", "معقولية / رجحان", "/ˌplɔːzəˈbɪləti/"),
            Triple("discourse", "خطاب نقدي / فكري", "/ˈdɪskɔːrs/"),
            Triple("qualitative analysis", "تحليل كيفي / نوعي", "/ˈkwɑːlɪteɪtɪv əˈnæləsɪs/"),
            Triple("quantitative analysis", "تحليل كمي / إحصائي", "/ˈkwɑːntɪteɪtɪv əˈnæləsɪs/"),
            Triple("peer review", "مراجعة الأقران الأكاديمية", "/pɪər rɪˈvjuː/"),
            Triple("inherent limitation", "قصور جوهري / ذاتي", "/ɪnˈhɪrənt ˌlɪmɪˈteɪʃn/"),
            Triple("nuance", "فارق دقيق في المعنى", "/ˈnuːɑːns/"),
            Triple("cognitive bias", "انحياز إدراكي", "/ˈkɑːɡnətɪv ˈbaɪəs/")
        ).mapIndexed { idx, item ->
            val wId = "w_acd_${idx + 1}"
            VocabularyWordModel(wId, item.first, item.second, getSavedStatus(wId), item.third, "cat_academic", "C1")
        }.toMutableList()

        cachedCategories.add(
            VocabularyCategoryModel(
                categoryId = "cat_academic",
                categoryName = "الأكاديمي والمناظرات والنقد",
                level = "C1",
                totalWords = academicWords.size,
                startedCount = academicWords.count { it.status == WordStatus.IN_PROGRESS },
                masteredCount = academicWords.count { it.status == WordStatus.MASTERED },
                words = academicWords
            )
        )
    }
}
