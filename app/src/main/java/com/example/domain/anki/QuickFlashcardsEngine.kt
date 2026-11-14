package com.example.domain.anki

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.example.data.database.FlashcardDeckEntity
import com.example.data.database.FlashcardEntity
import com.example.domain.ai.GeminiAiRepository
import com.example.domain.utils.DocumentTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class QuickReviewCard(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val question: String,
    val answer: String,
    val keyConcept: String = "",
    val category: String = "عام",
    val formulaOrRule: String = "",
    val mnemonicTip: String = "",
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    var isBookmarked: Boolean = false,
    var masteryScore: Int = 0 // 0 = unrated, 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
)

data class QuickReviewSession(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val sourceName: String,
    val targetCount: Int,
    val cards: List<QuickReviewCard>,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object QuickFlashcardsEngine {

    private val aiRepository = GeminiAiRepository()

    /**
     * Core AI generation engine for "احفظ بسرعة"
     */
    suspend fun generateFlashcards(
        context: Context,
        sourceType: String, // "CAMERA", "FILE", "TEXT", "LIBRARY"
        rawText: String? = null,
        fileUri: Uri? = null,
        bitmapList: List<Bitmap> = emptyList(),
        cardCount: Int = 25,
        customInstructions: String = "",
        deckTitle: String = "مراجعة سريعة",
        customApiKey: String = ""
    ): Result<List<QuickReviewCard>> = withContext(Dispatchers.IO) {
        try {
            var extractedContent = rawText?.trim() ?: ""

            // 1. If fileUri provided, extract text
            if (fileUri != null && extractedContent.isBlank()) {
                val fileExtraction = DocumentTextExtractor.extractTextFromUri(context, fileUri)
                if (fileExtraction.isSuccess) {
                    extractedContent = fileExtraction.getOrDefault("")
                }
            }

            // 2. Prepare images if camera / photos provided
            val base64Images = mutableListOf<String>()
            for (bmp in bitmapList.take(5)) {
                val outputStream = ByteArrayOutputStream()
                val scaled = if (bmp.width > 1280 || bmp.height > 1280) {
                    val ratio = 1280f / maxOf(bmp.width, bmp.height)
                    Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
                } else {
                    bmp
                }
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val b64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                base64Images.add(b64)
            }

            // 3. Build optimized Prompt for Gemini AI
            val systemInstruction = """
                أنت خبير تربوي ومحرك ذكاء اصطناعي فائق الدقة متخصص في إنشاء بطاقات الاستذكار السريع (Flashcards / Active Recall).
                مهمتك تحويل المادة الدراسية (نصوص، صور صفحات، ملخصات، قوانين) إلى حزمة بطاقات سؤال وجواب تفاعلية عالية الجودة.
                
                قواعد هامة:
                1. عدد البطاقات المطلوب بالضبط: $cardCount بطاقة.
                2. ركز على المفاهيم الجوهرية، التعريفات، التعليلات (بم تفسر / علل)، القوانين الرياضية/العلمية، والمقارنات.
                3. اجعل السؤال واضحاً ومحدداً ومحفزاً للتذكر النشط.
                4. اجعل الإجابة نموذجية، دقيقة، ومبسطة مع إبراز الفكرة الرئيسية.
                5. أضف تلميحاً أو وسيلة مساعدة للربط والذاكرة (mnemonicTip) وقانوناً إن وجد.
                
                أرجع الناتج بصيغة JSON Array فقط كالتالي:
                [
                  {
                    "question": "ما هو تعريف ...؟",
                    "answer": "هو عبارة عن ...",
                    "keyConcept": "المفهوم الرئيسي",
                    "category": "فيزياء / أحياء / نحو / تاريخ ...",
                    "formulaOrRule": "القانون إن وجد أو فارغ",
                    "mnemonicTip": "طريقة تذكر ذكية أو رابط ذهني",
                    "difficulty": "EASY أو MEDIUM أو HARD"
                  }
                ]
            """.trimIndent()

            val userPrompt = buildString {
                append("المطلوب: قم بتوليد $cardCount بطاقة استذكار ومراجعة سريعة وشاملة للمحتوى التالي.\n")
                if (customInstructions.isNotBlank()) {
                    append("تعليمات خاصة من الطالب: $customInstructions\n")
                }
                if (extractedContent.isNotBlank()) {
                    append("\n--- المحتوى الدراسي المطلوب تحليله ---\n")
                    append(extractedContent.take(15000))
                } else if (base64Images.isNotEmpty()) {
                    append("\nيرجى استخراج المفاهيم والأسئلة مباشرة من صور الصفحات المرفقة بدقة تامة.\n")
                } else {
                    append("\nيرجى توليد بطاقات دراسية نموذجية لموضوع: $deckTitle\n")
                }
            }

            // 4. Call Gemini AI
            val aiResult = aiRepository.generateAiResponse(
                prompt = userPrompt,
                systemInstruction = systemInstruction,
                customApiKey = customApiKey,
                modelName = "gemini-3.5-flash",
                imageBytesBase64 = base64Images.firstOrNull()
            )

            if (aiResult.isSuccess) {
                val rawAi = aiResult.getOrNull() ?: ""
                val parsedCards = parseCardsFromJson(rawAi)
                if (parsedCards.isNotEmpty()) {
                    return@withContext Result.success(parsedCards)
                }
            }

            // 5. Fallback if AI fails or returns empty/offline
            val fallbackCards = generateFallbackFlashcards(extractedContent, deckTitle, cardCount)
            Result.success(fallbackCards)
        } catch (e: Exception) {
            val fallbackCards = generateFallbackFlashcards(rawText ?: "", deckTitle, cardCount)
            Result.success(fallbackCards)
        }
    }

    /**
     * Parses JSON response into QuickReviewCard list
     */
    fun parseCardsFromJson(rawJson: String): List<QuickReviewCard> {
        var clean = rawJson.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }

        val cardsList = mutableListOf<QuickReviewCard>()
        try {
            val jsonArray = if (clean.startsWith("[")) {
                JSONArray(clean)
            } else {
                val obj = JSONObject(clean)
                obj.optJSONArray("cards") ?: obj.optJSONArray("flashcards") ?: JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val q = item.optString("question", "").trim()
                val a = item.optString("answer", "").trim()
                if (q.isNotBlank() && a.isNotBlank()) {
                    cardsList.add(
                        QuickReviewCard(
                            id = System.currentTimeMillis() + i,
                            question = q,
                            answer = a,
                            keyConcept = item.optString("keyConcept", ""),
                            category = item.optString("category", "مراجعة سريعة"),
                            formulaOrRule = item.optString("formulaOrRule", ""),
                            mnemonicTip = item.optString("mnemonicTip", ""),
                            difficulty = item.optString("difficulty", "MEDIUM").uppercase()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cardsList
    }

    /**
     * Smart local fallback generator for 100% offline & instant support
     */
    fun generateFallbackFlashcards(text: String, title: String, count: Int): List<QuickReviewCard> {
        val lines = text.lines().map { it.trim() }.filter { it.length > 8 }
        val generated = mutableListOf<QuickReviewCard>()

        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains(":") || line.contains("=") || line.contains(" هو ") || line.contains(" هي ")) {
                val parts = when {
                    line.contains(":") -> line.split(":", limit = 2)
                    line.contains("=") -> line.split("=", limit = 2)
                    line.contains(" هو ") -> line.split(" هو ", limit = 2).let { listOf(it[0], "هو " + it.getOrElse(1) { "" }) }
                    line.contains(" هي ") -> line.split(" هي ", limit = 2).let { listOf(it[0], "هي " + it.getOrElse(1) { "" }) }
                    else -> listOf(line, "")
                }
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    generated.add(
                        QuickReviewCard(
                            id = System.currentTimeMillis() + generated.size,
                            question = "ما هو مفهوم: ${parts[0].trim()}؟",
                            answer = parts[1].trim(),
                            keyConcept = parts[0].trim(),
                            category = title.ifBlank { "مراجعة ذكية" },
                            mnemonicTip = "ركز على الكلمات المفتاحية في التعريف.",
                            difficulty = "MEDIUM"
                        )
                    )
                }
            } else if (i + 1 < lines.size && generated.size < count) {
                generated.add(
                    QuickReviewCard(
                        id = System.currentTimeMillis() + generated.size,
                        question = "اشرح / وضح: ${line.take(60)}",
                        answer = lines[i + 1],
                        keyConcept = line.take(30),
                        category = title.ifBlank { "مفاهيم أساسية" },
                        difficulty = "MEDIUM"
                    )
                )
            }
            if (generated.size >= count) break
        }

        // Fill remaining with high-yield study cards if content was short
        val sampleKnowledge = listOf(
            Triple("ما هي تقنية التكرار المتباعد (Spaced Repetition)؟", "طريقة استذكار قائمة على مراجعة المعلومات على فترات متباعدة ومدروسة لتثبيتها في الذاكرة طويلة المدى ومكافحة منحنى النسيان.", "التكرار المتباعد"),
            Triple("ما هو تأثير فاينمان (Feynman Technique) في الفهم؟", "شرح المفهوم بأسلوب بسيط وكأنك تعلمه لشخص مبتدئ لاكتشاف الثغرات في فهمك الذاتي وتصحيحها فوراً.", "تقنية فاينمان"),
            Triple("ما هو مبدأ الاسترجاع النشط (Active Recall)؟", "اختبار النفس واسترجاع المعلومة من الذاكرة ذاتياً قبل النظر للإجابة، وهو أقوى أسلوب لترسيخ الروابط العصبية.", "الاسترجاع النشط"),
            Triple("كيف تنظم جلسات البومودورو للمذاكرة الفعالة؟", "25 دقيقة من التركيز التام الخالي من المشتتات، تليها 5 دقائق استراحة ذهنية، ثم استراحة طويلة بعد 4 دورات.", "تقنية البومودورو"),
            Triple("ما هي قاعدة 80/20 (باريتو) في الاستعداد للامتحانات؟", "التركيز على 20% من المفاهيم والأسئلة الأكثر أهمية وتكراراً والتي تسهم في تحقيق 80% من الدرجات.", "مبدأ باريتو"),
            Triple("ما فائدة الخرائط الذهنية (Mind Maps) في تلخيص المواد؟", "ربط المفاهيم الكبرى بالفرعية بصرياً عبر ألوان ورموز مما يسهل على نصفي الدماغ استيعاب الهيكل العام للمنهج.", "الخرائط الذهنية"),
            Triple("ما هو دور النوم الكافي في ترسيخ المعلومات المذاكرة؟", "أثناء النوم العميق، يقوم الحُصين (Hippocampus) بنقل الذكريات وتثبيتها في القشرة المخية لتحويلها لذاكرة دائمة.", "ترسيخ الذاكرة"),
            Triple("كيف تتعامل مع الأسئلة المعقدة في الامتحانات؟", "تفكيك السؤال لعناصره الأولية، تدوين المعطيات والمطلوب، واسترجاع القانون أو القاعدة الرياضية/النظرية المرتبطة.", "حل المسائل"),
            Triple("ما هي طريقة البطاقات المزدوجة للمصطلحات والرموز؟", "وضع المصطلح في الوجه الأول والمعنى والتطبيق في الوجه الآخر، مع تغيير ترتيب البطاقات عشوائياً.", "بطاقات المصطلحات"),
            Triple("كيف تتغلب على التوتر والقلق الدراسي قبل الامتحانات؟", "التنفس العميق المنتظم، المراجعة السريعة الهادئة للبطاقات، التوكل على الله، وتجنب مراجعة ليلة الامتحان المرهقة.", "إدارة التوتر")
        )

        var sampleIdx = 0
        while (generated.size < count) {
            val sample = sampleKnowledge[sampleIdx % sampleKnowledge.size]
            generated.add(
                QuickReviewCard(
                    id = System.currentTimeMillis() + generated.size,
                    question = sample.first,
                    answer = sample.second,
                    keyConcept = sample.third,
                    category = title.ifBlank { "استذكار فائق" },
                    mnemonicTip = "اربط المفهوم بتطبيق عملي في حياتك اليومية.",
                    difficulty = "EASY"
                )
            )
            sampleIdx++
        }

        return generated.take(count)
    }

    /**
     * Converts QuickReviewCard list to Room FlashcardEntity list for permanent database saving
     */
    fun convertToFlashcardEntities(
        cards: List<QuickReviewCard>,
        deckId: Long
    ): List<FlashcardEntity> {
        return cards.map { card ->
            FlashcardEntity(
                deckId = deckId,
                question = card.question,
                answer = buildString {
                    append(card.answer)
                    if (card.formulaOrRule.isNotBlank()) {
                        append("\n\n📐 **القانون/القاعدة**: ${card.formulaOrRule}")
                    }
                    if (card.mnemonicTip.isNotBlank()) {
                        append("\n💡 **تلميح للذاكرة**: ${card.mnemonicTip}")
                    }
                },
                difficulty = card.difficulty,
                contextStr = card.category,
                partOfSpeech = card.keyConcept,
                state = "NEW",
                repetitionNumber = if (card.masteryScore >= 3) 1 else 0,
                intervalDays = if (card.masteryScore == 4) 4 else 1,
                nextReviewDateMillis = System.currentTimeMillis() + (if (card.masteryScore >= 3) 86400000L else 0L)
            )
        }
    }
}
