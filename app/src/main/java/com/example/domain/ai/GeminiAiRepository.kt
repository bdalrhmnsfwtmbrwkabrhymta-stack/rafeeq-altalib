package com.example.domain.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

import com.example.domain.book.BookProcessingResult
import com.example.domain.book.MemoryBookChapter
import com.example.domain.book.MemoryBookData
import com.example.domain.book.MemoryBookItem

data class GeneratedFlashcard(
    val question: String,
    val answer: String
)

data class GeneratedQuizItem(
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String
)

data class EnglishVocabAnkiCard(
    val englishWord: String,
    val partOfSpeech: String = "",
    val pronunciationIpa: String = "",
    val arabicMeaning: String,
    val contextualMeaning: String = "",
    val difficulty: String = "Medium",
    val englishExample: String,
    val arabicExampleTranslation: String,
    val notesOrMnemonic: String = "",
    val frontText: String = "",
    val backText: String = ""
)

data class BookStudyFlashcard(
    val question: String,
    val answer: String,
    val hint: String = "",
    val cardType: String = "BASIC",
    val clozeText: String = "",
    val topic: String = ""
)

data class UniversalAnkiCard(
    val cardType: String = "BASIC", // BASIC, MCQ, TRUE_FALSE, CLOZE
    val question: String,
    val answer: String,
    val hint: String = "",
    val clozeText: String = "",
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int = 0,
    val explanation: String = "",
    val englishExample: String = "",
    val arabicExample: String = "",
    val pronunciation: String = "",
    val topic: String = ""
)

class GeminiAiRepository {

    suspend fun generateText(prompt: String, customApiKey: String = ""): String {
        return generateAiResponse(prompt = prompt, customApiKey = customApiKey).getOrElse { "مرحباً بك، أنا مساعدك الذكي في رفيق الطالب." }
    }

    suspend fun generateStudyResponse(prompt: String, customApiKey: String = ""): String {
        return generateText(prompt, customApiKey)
    }

    suspend fun processBookWithAi(
        title: String,
        rawText: String,
        customApiKey: String
    ): Result<BookProcessingResult> = withContext(Dispatchers.IO) {
        val cleanTitle = if (title.isBlank()) "محتوى دراسي جديد" else title
        val textToProcess = if (rawText.isBlank()) "محتوى الدرس والملخص الدراسي للتطبيق." else rawText

        val systemPrompt = """
            أنت مساعد دراسي خبير ومتخصص في تحويل المقررات والكتب إلى هيكلية "كتاب طرق الحفظ".
            وظيفتك تحويل النص المدخل إلى كائن JSON ملموس بنسبة 100% بنفس الحقول المحددة تماماً بدون أي كلام إضافي أو علامات اقتباس خارجية غير JSON.

            الهيكل المطلوب بالدقة:
            {
              "bookTitle": "عنوان الكتاب أو الدرس",
              "introTitle": "مقدمة الدرس/الكتاب",
              "introBody": "نص المقدمة مع توضيح الهدف من الحفظ والفهم...",
              "chapters": [
                {
                  "id": "ch1",
                  "title": "الفصل الأول: [اسم الموضوع الرئيسي]",
                  "intro": "مقدمة سريعة للفصل...",
                  "items": [
                    {
                      "name": "اسم المفهوم/التقنية/النقطة الرئيسية",
                      "mechanism": "الآلية والشرح العلمي والعملي المبسط",
                      "benefit": "الفائدة التطبيقية وكيف تمنع النسيان",
                      "example": "مثال توضيحي ملموس أو تطبيق عملي"
                    }
                  ],
                  "summary": "خلاصة الفصل في سطرين"
                }
              ],
              "importantPoints": [
                "أهم نقطة مركزية ومستخلصة باختصار شديد",
                "نقطة جوهرية ثانية باختصار"
              ],
              "flashcards": [
                {
                  "question": "سؤال أو مصطلح للمراجعة؟",
                  "answer": "الإجابة النموذجية والمختصرة"
                }
              ],
              "conclusionTitle": "الخاتمة والتثبيت النهائي",
              "conclusionBody": "ملخص عام ونصائح للمذاكرة والتطبيق..."
            }
        """.trimIndent()

        val userPrompt = "قم بمعالجة النص التالي لـ \"$cleanTitle\":\n\n$textToProcess"

        val responseResult = generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey
        )

        responseResult.fold(
            onSuccess = { rawResponse ->
                try {
                    val parsed = parseBookJsonResponse(cleanTitle, rawResponse)
                    Result.success(parsed)
                } catch (e: Exception) {
                    // Fallback parse
                    Result.success(generateFallbackBookResult(cleanTitle, textToProcess))
                }
            },
            onFailure = {
                Result.success(generateFallbackBookResult(cleanTitle, textToProcess))
            }
        )
    }

    private fun parseBookJsonResponse(title: String, rawResponse: String): BookProcessingResult {
        var jsonText = rawResponse.trim()
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.removePrefix("```json").trim()
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.removePrefix("```").trim()
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.removeSuffix("```").trim()
        }

        val json = JSONObject(jsonText)
        val bookTitle = json.optString("bookTitle", title)
        val introTitle = json.optString("introTitle", "مقدمة الكتاب")
        val introBody = json.optString("introBody", "هذا الملخص يوضح أهم الأفكار والتقنيات الواردة في هذا المحتوى الدراسي.")
        val conclusionTitle = json.optString("conclusionTitle", "خاتمة وخلاصة")
        val conclusionBody = json.optString("conclusionBody", "استمر في مراجعة هذه المفاهيم وتطبيق التكرار المتباعد للوصول لأعلى درجات الاستيعاب.")

        val chaptersList = mutableListOf<MemoryBookChapter>()
        val jsonChapters = json.optJSONArray("chapters")
        if (jsonChapters != null && jsonChapters.length() > 0) {
            for (i in 0 until jsonChapters.length()) {
                val chObj = jsonChapters.getJSONObject(i)
                val chId = chObj.optString("id", "ch${i + 1}")
                val chTitle = chObj.optString("title", "الفصل ${i + 1}")
                val chIntro = chObj.optString("intro", "مقدمة الفصل")
                val chSummary = chObj.optString("summary", "خلاصة الفصل")

                val itemsList = mutableListOf<MemoryBookItem>()
                val jsonItems = chObj.optJSONArray("items")
                if (jsonItems != null) {
                    for (j in 0 until jsonItems.length()) {
                        val itemObj = jsonItems.getJSONObject(j)
                        itemsList.add(
                            MemoryBookItem(
                                name = itemObj.optString("name", "نقطة مهمة ${j + 1}"),
                                mechanism = itemObj.optString("mechanism", "شرح النقطة والآلية"),
                                benefit = itemObj.optString("benefit", "الفائدة التطبيقية من الفهم"),
                                example = itemObj.optString("example", null)
                            )
                        )
                    }
                }
                chaptersList.add(
                    MemoryBookChapter(
                        id = chId,
                        title = chTitle,
                        intro = chIntro,
                        items = itemsList,
                        summary = chSummary
                    )
                )
            }
        }

        val pointsList = mutableListOf<String>()
        val jsonPoints = json.optJSONArray("importantPoints")
        if (jsonPoints != null) {
            for (i in 0 until jsonPoints.length()) {
                pointsList.add(jsonPoints.getString(i))
            }
        }

        val flashcardsList = mutableListOf<GeneratedFlashcard>()
        val jsonCards = json.optJSONArray("flashcards")
        if (jsonCards != null) {
            for (i in 0 until jsonCards.length()) {
                val cardObj = jsonCards.getJSONObject(i)
                flashcardsList.add(
                    GeneratedFlashcard(
                        question = cardObj.optString("question", "سؤال حول الدرس"),
                        answer = cardObj.optString("answer", "الإجابة النموذجية")
                    )
                )
            }
        }

        if (chaptersList.isEmpty()) {
            return generateFallbackBookResult(title, rawResponse)
        }

        return BookProcessingResult(
            bookData = MemoryBookData(
                bookTitle = bookTitle,
                introTitle = introTitle,
                introBody = introBody,
                chapters = chaptersList,
                conclusionTitle = conclusionTitle,
                conclusionBody = conclusionBody
            ),
            importantPoints = pointsList.ifEmpty { listOf("تركيز المذاكرة على النقاط الرئيسية وفهم الآلية قبل الحفظ.") },
            flashcards = flashcardsList.ifEmpty { listOf(GeneratedFlashcard("ما هو الهدف الأساسي من هذا الدرس؟", "فهم النقاط والآليات وتطبيق المراجعة النشطة.")) }
        )
    }

    fun generateFallbackBookResult(title: String, rawText: String): BookProcessingResult {
        val cleanTitle = title.ifBlank { "محتوى دراسي منظم" }
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        
        val chapters = mutableListOf<MemoryBookChapter>()
        val importantPoints = mutableListOf<String>()
        val flashcards = mutableListOf<GeneratedFlashcard>()

        val chunks = if (lines.size > 6) lines.chunked(lines.size / 2 + 1) else listOf(lines)

        chunks.forEachIndexed { index, chunkLines ->
            val chNum = index + 1
            val chTitle = "الفصل $chNum: ${chunkLines.firstOrNull()?.take(30) ?: "محور المذاكرة $chNum"}"
            val items = mutableListOf<MemoryBookItem>()

            chunkLines.take(4).forEachIndexed { itemIndex, line ->
                val conceptName = if (line.contains(":")) line.substringBefore(":") else "مفهوم رئيسي ${itemIndex + 1}"
                val explanation = if (line.contains(":")) line.substringAfter(":") else line
                
                items.add(
                    MemoryBookItem(
                        name = conceptName.take(40),
                        mechanism = explanation.ifBlank { "شرح وتوضيح آلية عمل المفهوم وكيفية استيعابه." },
                        benefit = "تثبيت الفكرة في الذاكرة طويلة المدى وتسهيل الاسترجاع أثناء الامتحانات.",
                        example = "تطبيق ملموس من الواقع الدراسي لحفظ $conceptName بسهولة."
                    )
                )

                if (importantPoints.size < 6) {
                    importantPoints.add("📌 $conceptName: $explanation")
                }

                if (flashcards.size < 8) {
                    flashcards.add(
                        GeneratedFlashcard(
                            question = "ما المقصود بـ ($conceptName) وما هي أهميته؟",
                            answer = explanation
                        )
                    )
                }
            }

            chapters.add(
                MemoryBookChapter(
                    id = "ch_$chNum",
                    title = chTitle,
                    intro = "يتناول هذا الفصل شرح النقاط الأساسية المتعلقة بـ $cleanTitle بأسلوب مبسط ومنظم.",
                    items = items.ifEmpty {
                        listOf(
                            MemoryBookItem(
                                name = "الفكرة الرئيسية",
                                mechanism = rawText.take(150),
                                benefit = "استيعاب الدروس وتيسير الحفظ السريع.",
                                example = "مراجعة يومية متباعدة"
                            )
                        )
                    },
                    summary = "خلاصة هذا الفصل ترتكز على الفهم العميق وربط الأمثلة بالواقع لمنع النسيان."
                )
            )
        }

        return BookProcessingResult(
            bookData = MemoryBookData(
                bookTitle = cleanTitle,
                introTitle = "مقدمة الكتاب والتلخيص الذكي",
                introBody = "تم تحويل هذا المحتوى الدراسي أوتوماتيكياً بواسطة المساعد الذكي إلى كتاب فصول منظم بطريقة بطاقات الحفظ والتقنيات المثبتة علمياً.",
                chapters = chapters,
                conclusionTitle = "خاتمة وتوجيهات الحفظ",
                conclusionBody = "ينصح بمراجعة البطاقات الذكية المنشأة من هذا الكتاب واستخدام التكرار المتباعد لتثبيت المعلومات."
            ),
            importantPoints = importantPoints.ifEmpty { listOf("الفهم العميق للمفاهيم قبل البدء بالحفظ.", "استخدام بطاقات الأسئلة والأجوبة للمراجعة الذاتية.") },
            flashcards = flashcards.ifEmpty { listOf(GeneratedFlashcard("ما هي الطريقة الفعالة لمذاكرة هذا الدرس؟", "تقسيم الدرس إلى فصول، واستخراج البطاقات الذكية، والمراجعة الفعالة.")) }
        )
    }

    suspend fun generateAiResponse(
        prompt: String,
        systemInstruction: String = """
            أنت "المعلم الخصوصي الذكي والصبور" (Master AI Academic Tutor) للطلاب في كافة المراحل التعليمية.
            أسلوبك ومنهجك في التدريس والإجابة:
             1. **معلم صبور ومفصّل**: تشرح المفاهيم العلمية بخطوات مرتبة وواضحة (خطوة بخطوة Step-by-Step) دون استعجال أو تكرار ممل أو حشو فارغ.
             2. **تحليل الصور والمسائل العلمية (Vision & OCR)**: عند إرسال أي صورة تحتوي على معادلات رياضية، دوائر كهربية، رسوم بيانية، مسائل فيزياء، كيمياء، أو أحياء:
                - قم أولاً بقراءة وقراءة معطيات المسألة بدقة متناهية.
                - اذكر القانون الفيزيائي أو الرياضي المستخدم بوضوح تام.
                - فصّل خطوات التعويض الرياضي خطوة بخطوة مستخدماً صياغة المعادلات الرياضية الواضحة والجميلة (LaTeX أو الصيغ الرياضية المرتبة مثل $$...$$).
                - اذكر الناتج النهائي مع وحدات القياس الفيزيائية الصحيحة.
             3. **شرح وتوضيح الرسم والبيانات**: في حال وجود رسم بياني أو رسم توضيحي، اشرح مدلول كل محور (X, Y) وما تعنيه المنحنيات والقيم الحرجة.
             4. **التنسيق الاحترافي**: استخدم العناوين الفرعية، النقاط المرتبة، المربعات التوضيحية، والنصوص الرياضية الواضحة.
             5. **لغة عربية سليمة**: قدم الإجابة بلغة عربية فصحى مشجعة ومحفزة تنمي ثقة الطالب بنفسه وتساعده على التفوق الدراسي.
        """.trimIndent(),
        customApiKey: String = "",
        modelName: String = "gemini-3.5-flash",
        imageBytesBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        audioBytesBase64: String? = null,
        audioMimeType: String = "audio/mp3",
        enableGoogleSearch: Boolean = false,
        enableGoogleMaps: Boolean = false,
        enableHighThinking: Boolean = false,
        chatHistory: List<Pair<Boolean, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = if (customApiKey.isNotBlank()) customApiKey else BuildConfig.GEMINI_API_KEY

        // Adjust effective model based on feature requirements
        // For vision / image STEM analysis or high-thinking tasks, gemini-3.1-pro-preview provides top-tier reasoning and vision accuracy.
        val effectiveModel = when {
            modelName == "lyria-3-clip-preview" || modelName == "lyria-3-pro-preview" -> modelName
            modelName == "gemini-3.5-transcribe" || (!audioBytesBase64.isNullOrEmpty() && modelName.contains("transcribe")) -> "gemini-3.5-transcribe"
            modelName == "gemini-3.1-flash-image-preview" -> "gemini-3.1-flash-image-preview"
            enableHighThinking -> "gemini-3.1-pro-preview"
            !imageBytesBase64.isNullOrEmpty() && (modelName == "gemini-3.1-pro-preview" || enableHighThinking) -> "gemini-3.1-pro-preview"
            !imageBytesBase64.isNullOrEmpty() -> "gemini-3.5-flash"
            !audioBytesBase64.isNullOrEmpty() || enableGoogleSearch || enableGoogleMaps -> "gemini-3.5-flash"
            modelName == "gemini-3.1-flash-lite" -> "gemini-3.1-flash-lite"
            modelName == "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
            else -> "gemini-3.5-flash"
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val fallback = "⚠️ **ملاحظة حول مفتاح Gemini API**:\nلم يتم تعيين مفتاح API مفعل تلقائياً. يمكنك إضافة مفتاح Gemini API الخاص بك بسهولة وسرية من زر **«🔑 المفتاح»** بوجة الشات ليتم توجيه الأسئلة مباشرة لنماذج Gemini الذكية عبر الإنترنت.\n\n---\n" + generateOfflineAcademicResponse(prompt)
            return@withContext Result.success(fallback)
        }

        try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()

                // Add multi-turn chat history if available
                chatHistory.forEach { (isUser, msgText) ->
                    if (msgText.isNotBlank()) {
                        contentsArray.put(JSONObject().apply {
                            put("role", if (isUser) "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", msgText))
                            })
                        })
                    }
                }

                // Add current turn
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        val currentPrompt = prompt.ifBlank {
                            when {
                                !imageBytesBase64.isNullOrEmpty() -> "Analyze and extract educational flashcards from this image."
                                !audioBytesBase64.isNullOrEmpty() -> "Transcribe and summarize this audio recording accurately."
                                else -> "Explain this topic clearly."
                            }
                        }
                        put(JSONObject().put("text", currentPrompt))

                        // Inline image attachment
                        if (!imageBytesBase64.isNullOrEmpty()) {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", imageMimeType)
                                    put("data", imageBytesBase64)
                                })
                            })
                        }

                        // Inline audio attachment
                        if (!audioBytesBase64.isNullOrEmpty()) {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", audioMimeType)
                                    put("data", audioBytesBase64)
                                })
                            })
                        }
                    })
                })
                put("contents", contentsArray)

                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                }

                // Tools configuration (Search & Maps Grounding)
                val toolsArray = JSONArray()
                if (enableGoogleSearch) {
                    toolsArray.put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }
                if (enableGoogleMaps) {
                    toolsArray.put(JSONObject().apply {
                        put("googleMaps", JSONObject())
                    })
                }
                if (toolsArray.length() > 0) {
                    put("tools", toolsArray)
                }

                // Config object
                val genConfig = JSONObject().apply {
                    put("temperature", 0.3)
                    if (enableHighThinking) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingLevel", "HIGH")
                        })
                    }
                    if (effectiveModel.startsWith("lyria")) {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                    } else if (effectiveModel == "gemini-3.1-flash-image-preview") {
                        put("responseModalities", JSONArray().apply {
                            put("TEXT")
                            put("IMAGE")
                        })
                        put("imageConfig", JSONObject().apply {
                            put("aspectRatio", "1:1")
                            put("imageSize", "1K")
                        })
                    }
                }
                put("generationConfig", genConfig)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val responseString = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(responseString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val responseTextBuilder = StringBuilder()

                    if (parts != null && parts.length() > 0) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val t = part.optString("text", "")
                            if (t.isNotBlank()) {
                                responseTextBuilder.append(t)
                            }
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val b64 = inlineData.optString("data")
                                if (b64.isNotBlank()) {
                                    responseTextBuilder.append("\n\n[IMAGE_DATA:$b64]")
                                }
                            }
                        }
                    }

                    // Check for search & maps grounding metadata if enabled
                    val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
                    val searchQueries = groundingMetadata?.optJSONArray("webSearchQueries")
                    if (searchQueries != null && searchQueries.length() > 0) {
                        responseTextBuilder.append("\n\n🔍 **مصادر بحث جوجل**:")
                        for (q in 0 until searchQueries.length()) {
                            responseTextBuilder.append("\n• ").append(searchQueries.getString(q))
                        }
                    }

                    val groundingChunks = groundingMetadata?.optJSONArray("groundingChunks")
                    if (groundingChunks != null && groundingChunks.length() > 0) {
                        val mapsChunks = mutableListOf<String>()
                        for (c in 0 until groundingChunks.length()) {
                            val chunkObj = groundingChunks.optJSONObject(c)
                            val web = chunkObj?.optJSONObject("web")
                            val title = web?.optString("title")
                            val uri = web?.optString("uri")
                            if (!title.isNullOrBlank() && (title.contains("map", ignoreCase = true) || uri?.contains("google.com/maps") == true)) {
                                mapsChunks.add("📍 $title: $uri")
                            }
                        }
                        if (mapsChunks.isNotEmpty()) {
                            responseTextBuilder.append("\n\n🗺️ **بيانات خرائط جوجل (Google Maps)**:\n")
                            responseTextBuilder.append(mapsChunks.joinToString("\n"))
                        }
                    }

                    val finalOutput = responseTextBuilder.toString()
                    if (finalOutput.isNotBlank()) {
                        return@withContext Result.success(finalOutput)
                    }
                }
                Result.failure(Exception("لم يتم استلام نص استجابة من نموذج الذكاء الاصطناعي."))
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream, "UTF-8"))
                val errorText = errorReader.readText()
                errorReader.close()
                val friendlyError = when (responseCode) {
                    400 -> "طلب غير صالح أو بنية المدخلات غير مقبولة: $errorText"
                    401, 403 -> "مفتاح Gemini API غير صالح أو غير مصرح له. يرجى تعديله في الإعدادات."
                    429 -> "تم استنفاد الحصة المسموحة للطلبات (Quota Exceeded). يرجى الانتظار قليلاً أو تجربة مفتاح آخر."
                    500, 503 -> "خوادم الذكاء الاصطناعي مشغولة حالياً ($responseCode). يرجى إعادة المحاولة."
                    else -> "خطأ في الاتصال بالذكاء الاصطناعي ($responseCode): $errorText"
                }
                Result.failure(Exception(friendlyError))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة وإعادة المحاولة."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("انتهت مهلة الاتصال بخوادم الذكاء الاصطناعي. يرجى إعادة المحاولة."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transcribeAudio(
        audioBytesBase64: String,
        audioMimeType: String = "audio/mp3",
        customApiKey: String = ""
    ): Result<String> {
        return generateAiResponse(
            prompt = "Transcribe the following audio recording accurately into text. Provide the spoken words in Arabic and/or English exactly as heard, with proper punctuation.",
            systemInstruction = "You are an expert audio speech-to-text transcriber powered by gemini-3.5-transcribe. Transcribe clearly and accurately.",
            customApiKey = customApiKey,
            modelName = "gemini-3.5-transcribe",
            audioBytesBase64 = audioBytesBase64,
            audioMimeType = audioMimeType
        )
    }

    suspend fun generateMusic(
        prompt: String,
        isShortClip: Boolean = true,
        customApiKey: String = ""
    ): Result<String> {
        val model = if (isShortClip) "lyria-3-clip-preview" else "lyria-3-pro-preview"
        return generateAiResponse(
            prompt = prompt,
            systemInstruction = "You are an AI music composer specialized in ambient focus, binaural study beats, and peaceful background music.",
            customApiKey = customApiKey,
            modelName = model
        )
    }

    suspend fun searchWithGoogleGrounding(
        query: String,
        customApiKey: String = ""
    ): Result<String> {
        return generateAiResponse(
            prompt = query,
            systemInstruction = "أنت باحث ومساعد أكاديمي دقيق مدعوم ببيانات بحث جوجل المباشرة (Google Search Grounding). قدم إجابات موثقة بأحدث المصادر والأرقام والتواريخ.",
            customApiKey = customApiKey,
            modelName = "gemini-3.5-flash",
            enableGoogleSearch = true
        )
    }

    suspend fun generateOrEditImage(
        prompt: String,
        inputImageBase64: String? = null,
        customApiKey: String = ""
    ): Result<String> {
        return generateAiResponse(
            prompt = prompt,
            systemInstruction = "You are an expert AI visual designer and educational illustrator using gemini-3.1-flash-image-preview. Generate or edit high-quality educational diagrams, visual summaries, or concept illustrations.",
            customApiKey = customApiKey,
            modelName = "gemini-3.1-flash-image-preview",
            imageBytesBase64 = inputImageBase64
        )
    }

    suspend fun testGeminiConnection(customApiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = if (customApiKey.isNotBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("يرجى إدخال مفتاح Gemini API أولاً للاختبار."))
        }
        val res = generateAiResponse(
            prompt = "قل: الاتصال بخدمة Gemini ناجح وجاهز للاستخدام.",
            systemInstruction = "أنت مساعد دراسي لتطبيق رفيق الطالب. أجب بجملة تأكيد قصيرة جداً.",
            customApiKey = apiKey,
            modelName = "gemini-3.5-flash"
        )
        res
    }

    suspend fun summarizeLesson(text: String, customApiKey: String): Result<String> {
        val prompt = "قم بتلخيص النص التعليمي التالي في نقاط واضحة ومحددة مع تظليل المفاهيم الأساسية:\n\n$text"
        return generateAiResponse(prompt = prompt, customApiKey = customApiKey)
    }

    suspend fun summarizeOcrExtractedText(
        extractedText: String,
        style: String = "COMPREHENSIVE",
        customApiKey: String = ""
    ): Result<String> {
        val styleInstruction = when (style) {
            "BULLETS" -> """
                الهدف: تلخيص فوري ومكثف في نقاط سريعة مركزة.
                الهيكل المطلوب:
                1. 📌 **الفكرة الرئيسية**: سطر واحد يحدد جوهر الموضوع.
                2. 📝 **أهم النقاط الجوهرية (5 إلى 7 نقاط)**: بنود واضحة ومباشرة بدون حشو.
                3. 💡 **أهم مصطلح أو قاعدة**: استخلاص القوانين أو المصطلحات الأساسية.
            """.trimIndent()
            "EXAM_REVISION" -> """
                الهدف: ملخص مكثف للمراجعة السريعة قبل الامتحان.
                الهيكل المطلوب:
                1. 🎯 **العناصر الأكثر وروداً في الامتحانات**: النقاط التي تتكرر كأسئلة اختبار.
                2. ❓ **أهم 3 أسئلة متوقعة مع إجاباتها النموذجية المختصرة**.
                3. ⚠️ **أخطاء وملاحظات يجب الانتباه لها (فخاخ شائعة)**.
                4. 🧠 **تقنية للتذكر السريع (قاعدة أو كلمة مفتاحية)**.
            """.trimIndent()
            else -> """
                الهدف: ملخص تعليمي شامل واحترافي ودقيق.
                الهيكل المطلوب:
                1. 📖 **العنوان المقترح ونبذة موجزة**: سطرين يحددان الموضوع والهدف.
                2. 🔍 **الشرح والتحليل المفصل**: تفكيك المحتوى إلى عناوين فرعية ومفاهيم واضحة مع أمثلة.
                3. 📌 **النقاط والتعريفات الأساسية**: إبراز القوانين أو التعريفات بصورة بارزة.
                4. 💡 **الخلاصة والتطبيق العملي**: الفائدة وكيفية ربطها بالمذاكرة.
            """.trimIndent()
        }

        val prompt = """
            أنت مساعد ومعلم دراسي ذكي خبير. تم استخدام تقنية التعرف الضوئي على الحروف (ML Kit Text Recognition) لمسح صورة صفحة دراسية أو مستند تعليمي، والنص المستخرج هو:

            --- بداية النص المستخرج ---
            $extractedText
            --- نهاية النص المستخرج ---

            المطلوب:
            $styleInstruction

            ملاحظات هامة:
            - قم بتصحيح أي أخطاء طباعية طفيفة ناتجة عن المسح الضوئي (OCR typos) بذكاء أثناء التلخيص.
            - استخدم تنسيق Markdown أنيق وواضح (عناوين، نقاط، خط عريض للمصطلحات).
            - اجعل الأسلوب تشجيعياً وسلساً ومناسباً للطلاب.
        """.trimIndent()

        val systemInstruction = "أنت معلم ذكي وخبير في التلخيص الأكاديمي وصياغة المذكرات الدراسية من الصور والمستندات الممسوحة ضوئياً."

        return generateAiResponse(
            prompt = prompt,
            systemInstruction = systemInstruction,
            customApiKey = customApiKey,
            modelName = "gemini-3.5-flash"
        )
    }

    fun generateOfflineSummary(text: String, style: String = "COMPREHENSIVE"): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val previewTitle = lines.firstOrNull()?.take(40) ?: "ملخص المستند الدراسي"
        val keyPoints = lines.filter { it.length > 15 }.take(6)

        val sb = StringBuilder()
        sb.append("📋 **ملخص المستند الممسوح ضوئياً (ML Kit Scanner)**\n\n")
        sb.append("📌 **العنوان المقترح**: $previewTitle\n")
        sb.append("📊 **إحصائيات المسح**: ${words.size} كلمة | ${lines.size} سطور\n\n")

        when (style) {
            "BULLETS" -> {
                sb.append("📝 **أهم النقاط المستخلصة**:\n")
                if (keyPoints.isNotEmpty()) {
                    keyPoints.forEachIndexed { idx, point ->
                        sb.append("• **النقطة ${idx + 1}**: $point\n")
                    }
                } else {
                    sb.append("• ${text.take(250)}...\n")
                }
            }
            "EXAM_REVISION" -> {
                sb.append("🎯 **مراجعة وتثبيت المحتوى**:\n")
                if (keyPoints.isNotEmpty()) {
                    sb.append("1. **المفهوم الأساسي**: ${keyPoints.first()}\n")
                    if (keyPoints.size > 1) sb.append("2. **نقطة جوهرية**: ${keyPoints[1]}\n")
                    if (keyPoints.size > 2) sb.append("3. **سؤال للمراجعة**: ما الفكرة المركزية في: \"${keyPoints[2].take(50)}...\"؟\n")
                }
                sb.append("\n💡 **نصيحة الامتحان**: راجع المصطلحات الواردة وكرر قراءتها لتثبيتها في الذاكرة طويلة المدى.")
            }
            else -> {
                sb.append("🔍 **نظرة عامة على المحتوى**:\n")
                sb.append("تم فحص الصورة واستخراج النصوص التعليمية بنجاح عبر محرك Google ML Kit.\n\n")
                sb.append("📝 **النقاط والمفاهيم الرئيسية**:\n")
                if (keyPoints.isNotEmpty()) {
                    keyPoints.forEachIndexed { idx, point ->
                        sb.append("• $point\n")
                    }
                } else {
                    sb.append("• ${text.take(300)}\n")
                }
                sb.append("\n💡 **الخلاصة**: هذا المستند يحتوي على معلومات دراسية هامة، يُنصح بحفظه ومراجعته دورياً.")
            }
        }
        return sb.toString()
    }

    suspend fun explainConcept(topic: String, customApiKey: String): Result<String> {
        val prompt = "اشرح هذا المفهوم أو الدرس بأسلوب مبسط جدًا كأنك تشرح لطالب مع ذكر أمثلة توضيحية وتطبيقية:\n\n$topic"
        return generateAiResponse(prompt = prompt, customApiKey = customApiKey)
    }

    suspend fun generateFlashcardsFromText(text: String, count: Int = 5, customApiKey: String): Result<List<GeneratedFlashcard>> {
        val prompt = """
            أنت مصمم بطاقات دراسية خبير. قم باستخراج أهم المعلومات من النص التالي وقم بتكوين $count بطاقات سؤال وجواب (Flashcards).
            يجب أن تكون النتيجة بصيغة JSON Array فقط، بدون أي مقدمات أو نصوص أخرى خارج الـ JSON:
            [
              {"question": "السؤال هنا؟", "answer": "الجواب هنا بالتفصيل"}
            ]
            
            النص:
            $text
        """.trimIndent()

        val result = generateAiResponse(
            prompt = prompt,
            systemInstruction = "You are a flashcard generator. Always output pure valid JSON array.",
            customApiKey = customApiKey
        )

        return result.map { responseText ->
            val flashcards = mutableListOf<GeneratedFlashcard>()
            try {
                var jsonText = responseText.trim()
                if (jsonText.startsWith("```json")) jsonText = jsonText.removePrefix("```json").trim()
                else if (jsonText.startsWith("```")) jsonText = jsonText.removePrefix("```").trim()
                if (jsonText.endsWith("```")) jsonText = jsonText.removeSuffix("```").trim()

                val arrayStart = jsonText.indexOf('[')
                val arrayEnd = jsonText.lastIndexOf(']')
                if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                    jsonText = jsonText.substring(arrayStart, arrayEnd + 1)
                }

                val jsonArray = JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val q = obj.optString("question", "")
                    val a = obj.optString("answer", "")
                    if (q.isNotBlank() && a.isNotBlank()) {
                        flashcards.add(GeneratedFlashcard(q, a))
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors, let it fallback to returning empty or generic
            }

            if (flashcards.isEmpty()) {
                flashcards.add(GeneratedFlashcard("ملخص البطاقة", responseText.take(200)))
            }
            flashcards
        }
    }

    suspend fun analyzeTasksAndSuggestSchedule(
        exportedTasksJson: String,
        customApiKey: String
    ): Result<String> {
        val systemPrompt = """
            أنت خبير في التخطيط الأكاديمي وإدارة الوقت وتقنية بومودورو (Pomodoro).
            وظيفتك: قراءة قائمة المهام والبيانات الدراسية الخاصة بالمستخدم، وتحليل أولوياتها وتواريخ استحقاقها والمواد، ثم إنشاء "جدول دراسي يومي محسّن مقسم إلى كتل تركيز (Focused Study Blocks)".

            القواعد والتنظيم المطلوب في الإجابة:
            1. 📊 **تحليل خاطف للمهام**: ابدأ بملخص سريع للمهام الحالية المستوردة من البيانات وأولوياتها.
            2. ⏱️ **جدول كتل التركيز (Focused Time Blocks)**: قسّم اليوم إلى جلسات دراسية محددة بالوقت (مثلاً 25-45 دقيقة لكل كتلة تركيز) متضمنة استراحات قصيرة وصلاة.
            3. 🎯 **ربط كل كتلة بمادة أو مهمة محددة**: حدد أهدافاً واضحة وملموسة لكل كتلة بدقة.
            4. 💡 **نصائح إنتاجية مخصصة**: قدم نصيحتين أو ثلاث نصائح عملية مخصصة بناءً على طبيعة مهام الطالب.

            اكتب الإجابة باللغة العربية بطريقة أنيقة وموزعة في نقاط واضحة بأسلوب الماركداون (Markdown).
        """.trimIndent()

        val userPrompt = """
            قم بتحليل قائمة المهام والبيانات الدراسية التالية واقتراح جدول دراسي يومي متكامل ومحسّن بكتل التركيز:

            $exportedTasksJson
        """.trimIndent()

        return generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey
        )
    }

    /**
     * Gemini API Function to analyze the user's remaining study tasks and suggest a time-blocked daily study schedule.
     */
    suspend fun analyzeRemainingTasksAndSuggestDailySchedule(
        remainingTasks: List<RemainingTaskAnalysisItem>,
        currentTimeContext: String = "",
        customApiKey: String = ""
    ): Result<DailyTaskScheduleSuggestion> = withContext(Dispatchers.IO) {
        if (remainingTasks.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("لا توجد مهام دراسية متبقية حالياً! أضف مهامك لتتمكن من إنشاء جدول زمني ذكي.")
            )
        }

        val tasksSummary = remainingTasks.joinToString("\n") { task ->
            "- مهمة [معرف: ${task.id}]: \"${task.title}\" | المادة: ${task.subjectName} | الأولوية: ${task.priority} | الموعد: ${task.dueDateFormatted.ifBlank { "غير محدد" }} | التصنيف: ${task.tag} ${if (task.description.isNotBlank()) "| تفاصيل: ${task.description}" else ""}"
        }

        val systemPrompt = """
            أنت خبير التخطيط الأكاديمي والجدولة الذكية للطلاب بالذكاء الاصطناعي (Gemini 3.5 Flash).
            وظيفتك: تحليل المهام الدراسية المتبقية للطالب وتوزيعها بدقة وذكاء على جدول مذاكرة يومي مقسم زمنيًا (Time-Blocked Daily Study Schedule).

            القواعد والمنهجية المعتمدة:
            1. ترتيب الأولويات: البدء بالمهام العاجلة (URGENT/HIGH) والمطلوبة اليوم أو غداً في فترات ذروة التركيز، ثم المهام متوسطة وعادية الأولوية.
            2. التوزيع الزمني (Time-Blocking): تقسيم اليوم إلى كتل تركيز محددة بوقت بداية ونهاية (مثلاً جلسات 30-50 دقيقة) وفواصل استراحة وتنبيه لأوقات الصلاة والراحة.
            3. تحديد أهداف دقيقة (Focus Goal): صياغة نتيجة ملموسة وواضحة لكل كتلة (مثل: حل مسائل فصل معين، استيعاب وتلخيص مفهوم محدد).
            4. أسلوب الاستذكار: اقتراح تقنية ملائمة (مثل: بومودورو 25/5، استرجاع نشط Active Recall، بطاقات أنكي، حل نماذج).
            5. يجب إعادة النتيجة حصراً بصيغة JSON محكمة وصحيحة مطابقة للهيكل أدناه، بدون أي نصوص قبل أو بعد علامات الـ JSON.

            الهيكل المطلوب بالدقة:
            {
              "title": "جدول المذاكرة اليومي المقسم زمنياً",
              "executiveSummary": "ملخص خطة اليوم وتوزيع الجهد الذهني...",
              "totalStudyHours": 4.0,
              "urgentTasksNotes": "تنبيه عن المهام العاجلة المطلوب إنجازها اليوم...",
              "timeBlocks": [
                {
                  "timeSlot": "04:30 م - 05:15 م",
                  "startTime": "16:30",
                  "endTime": "17:15",
                  "taskTitle": "عنوان المهمة",
                  "subjectName": "اسم المادة",
                  "durationMinutes": 45,
                  "focusGoal": "الهدف المركز لهذه الجلسة",
                  "priority": "HIGH",
                  "pomodorosCount": 2,
                  "recommendedTechnique": "بومودورو 25/5 دقيقة مع استرجاع نشط",
                  "breakMinutes": 10,
                  "isPrayerOrRestBreak": false
                }
              ],
              "motivationalAdvice": "نصيحة ذكية للتركيز ومنع التسويف",
              "markdownSchedule": "# جدول المذاكرة اليومي المقسم زمنياً\n..."
            }
        """.trimIndent()

        val userPrompt = """
            سياق الوقت والتاريخ الحالي للطالب: ${currentTimeContext.ifBlank { "اليوم" }}

            قائمة المهام الدراسية المتبقية المطلوب تحليلها وتوزيعها زمنياً:
            $tasksSummary

            قم بتحليل المهام وتوليد خطة وجدول المذاكرة اليومي المقسم زمنياً في هيكل الـ JSON الموضح.
        """.trimIndent()

        val responseResult = generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey,
            modelName = "gemini-3.5-flash"
        )

        responseResult.fold(
            onSuccess = { responseText ->
                try {
                    val parsed = parseDailyTaskScheduleJson(responseText, remainingTasks)
                    Result.success(parsed)
                } catch (e: Exception) {
                    val fallback = generateOfflineDailySchedule(remainingTasks, currentTimeContext, responseText)
                    Result.success(fallback)
                }
            },
            onFailure = { error ->
                val fallback = generateOfflineDailySchedule(remainingTasks, currentTimeContext)
                Result.success(fallback)
            }
        )
    }

    private fun parseDailyTaskScheduleJson(
        rawResponse: String,
        remainingTasks: List<RemainingTaskAnalysisItem>
    ): DailyTaskScheduleSuggestion {
        var cleanJson = cleanJsonResponse(rawResponse)
        val firstBrace = cleanJson.indexOf('{')
        val lastBrace = cleanJson.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleanJson = cleanJson.substring(firstBrace, lastBrace + 1)
        }

        val json = JSONObject(cleanJson)
        val title = json.optString("title", "جدول المذاكرة اليومي المقسم زمنياً")
        val summary = json.optString("executiveSummary", "خطة مذاكرة يومية محسنة بالذكاء الاصطناعي وفقاً لمهامك المتبقية.")
        val totalHours = json.optDouble("totalStudyHours", 3.5)
        val urgentNotes = json.optString("urgentTasksNotes", "")
        val motivational = json.optString("motivationalAdvice", "ابدأ بالجلسة الأولى بدون تردد، فالخطوة الأولى هي مفتاح الإنجاز!")

        val blocks = mutableListOf<TimeBlockedStudyBlock>()
        val blocksArray = json.optJSONArray("timeBlocks")
        if (blocksArray != null) {
            for (i in 0 until blocksArray.length()) {
                val blockObj = blocksArray.getJSONObject(i)
                val tTitle = blockObj.optString("taskTitle", "جلسة مذاكرة")
                val sName = blockObj.optString("subjectName", "عام")
                val matchedTask = remainingTasks.firstOrNull { 
                    it.title.contains(tTitle, ignoreCase = true) || tTitle.contains(it.title, ignoreCase = true)
                }
                blocks.add(
                    TimeBlockedStudyBlock(
                        timeSlot = blockObj.optString("timeSlot", "كتلة تركيز ${i + 1}"),
                        startTime = blockObj.optString("startTime", "16:00"),
                        endTime = blockObj.optString("endTime", "16:45"),
                        taskTitle = tTitle,
                        subjectName = sName,
                        subjectId = matchedTask?.subjectId,
                        durationMinutes = blockObj.optInt("durationMinutes", 45),
                        focusGoal = blockObj.optString("focusGoal", "إنجاز جزئية محددة بتركيز تام"),
                        priority = blockObj.optString("priority", matchedTask?.priority ?: "MEDIUM"),
                        pomodorosCount = blockObj.optInt("pomodorosCount", 2),
                        recommendedTechnique = blockObj.optString("recommendedTechnique", "بومودورو 25/5 دقيقة"),
                        breakMinutes = blockObj.optInt("breakMinutes", 10),
                        isPrayerOrRestBreak = blockObj.optBoolean("isPrayerOrRestBreak", false)
                    )
                )
            }
        }

        if (blocks.isEmpty()) {
            throw IllegalStateException("لم يتم العثور على كتل زمنية في استجابة الذكاء الاصطناعي.")
        }

        val markdown = json.optString("markdownSchedule").ifBlank {
            buildMarkdownScheduleFromBlocks(title, summary, totalHours, urgentNotes, blocks, motivational)
        }

        return DailyTaskScheduleSuggestion(
            title = title,
            executiveSummary = summary,
            analyzedTasksCount = remainingTasks.size,
            totalStudyHours = totalHours,
            urgentTasksNotes = urgentNotes,
            timeBlocks = blocks,
            motivationalAdvice = motivational,
            rawMarkdown = markdown
        )
    }

    private fun generateOfflineDailySchedule(
        remainingTasks: List<RemainingTaskAnalysisItem>,
        currentTimeContext: String,
        rawAiHint: String? = null
    ): DailyTaskScheduleSuggestion {
        // Sort remaining tasks: URGENT first, then HIGH, then MEDIUM, then LOW
        val priorityScore = mapOf("URGENT" to 4, "HIGH" to 3, "MEDIUM" to 2, "LOW" to 1)
        val sortedTasks = remainingTasks.sortedWith(
            compareByDescending<RemainingTaskAnalysisItem> { priorityScore[it.priority.uppercase()] ?: 2 }
                .thenBy { if (it.dueDateMillis > 0) it.dueDateMillis else Long.MAX_VALUE }
        )

        val blocks = mutableListOf<TimeBlockedStudyBlock>()
        var currentHour = 16
        var currentMinute = 30
        var totalMinutes = 0

        sortedTasks.take(5).forEachIndexed { index, task ->
            val duration = when (task.priority.uppercase()) {
                "URGENT" -> 50
                "HIGH" -> 45
                else -> 40
            }
            val startStr = String.format("%02d:%02d", currentHour, currentMinute)
            val endMinuteTotal = currentMinute + duration
            val endHour = currentHour + (endMinuteTotal / 60)
            val endMin = endMinuteTotal % 60
            val endStr = String.format("%02d:%02d", endHour % 24, endMin)
            
            val start12 = if (currentHour > 12) "${currentHour - 12}:${String.format("%02d", currentMinute)} م" else "$currentHour:${String.format("%02d", currentMinute)} ص"
            val end12 = if (endHour > 12) "${endHour - 12}:${String.format("%02d", endMin)} م" else "$endHour:${String.format("%02d", endMin)} ص"

            val technique = when (index % 3) {
                0 -> "بومودورو 25/5 دقيقة + استرجاع نشط"
                1 -> "جلسة تركيز عميق 45 دقيقة + حل مسائل"
                else -> "طريقة فاينمان في الشرح والتبسيط"
            }

            blocks.add(
                TimeBlockedStudyBlock(
                    timeSlot = "$start12 - $end12",
                    startTime = startStr,
                    endTime = endStr,
                    taskTitle = task.title,
                    subjectName = task.subjectName,
                    subjectId = task.subjectId,
                    durationMinutes = duration,
                    focusGoal = if (task.description.isNotBlank()) task.description else "إنجاز ${task.title} والتركيز على النقاط الصعبة",
                    priority = task.priority,
                    pomodorosCount = if (duration >= 45) 2 else 1,
                    recommendedTechnique = technique,
                    breakMinutes = 10,
                    isPrayerOrRestBreak = false
                )
            )

            totalMinutes += duration

            // Advance time + 10 mins break
            val afterBreakTotal = endMinuteTotal + 10
            currentHour = (currentHour + (afterBreakTotal / 60)) % 24
            currentMinute = afterBreakTotal % 60
        }

        val totalHours = Math.round((totalMinutes / 60.0) * 10.0) / 10.0
        val urgentCount = sortedTasks.count { it.priority == "URGENT" || it.priority == "HIGH" }
        val urgentNotes = if (urgentCount > 0) {
            "لديك $urgentCount مهام ذات أولوية عالية/حرجة، تم وضعها في مقدمة جدول اليوم لضمان إنجازها في ذروة طاقتك الذهنية."
        } else {
            "تم توزيع مهامك بشكل متوازن مع فترات استراحة كافية لتجنب الإرهاق."
        }

        val summary = "خطة دراسية يومية ذكية مقسمة زمنياً لـ ${sortedTasks.size} مهمة دراسية متبقية."
        val motivational = "قسّم المهام الكبيرة إلى خطوات صغيرة وابدأ الآن، فكل كتلة تركيز تقربك خطوة نحو التفوق!"

        val markdown = if (!rawAiHint.isNullOrBlank() && !rawAiHint.startsWith("{")) {
            rawAiHint
        } else {
            buildMarkdownScheduleFromBlocks("جدول المذاكرة اليومي المقسم زمنياً", summary, totalHours, urgentNotes, blocks, motivational)
        }

        return DailyTaskScheduleSuggestion(
            title = "جدول المذاكرة اليومي المقسم زمنياً ⏱️",
            executiveSummary = summary,
            analyzedTasksCount = sortedTasks.size,
            totalStudyHours = totalHours,
            urgentTasksNotes = urgentNotes,
            timeBlocks = blocks,
            motivationalAdvice = motivational,
            rawMarkdown = markdown
        )
    }

    private fun buildMarkdownScheduleFromBlocks(
        title: String,
        summary: String,
        totalHours: Double,
        urgentNotes: String,
        blocks: List<TimeBlockedStudyBlock>,
        motivational: String
    ): String {
        val sb = StringBuilder()
        sb.append("## 📅 ").append(title).append("\n\n")
        sb.append("💡 **الملخص التنفيذي**: ").append(summary).append("\n")
        sb.append("⏱️ **إجمالي ساعات المذاكرة المقترحة**: ").append(totalHours).append(" ساعة\n\n")
        
        if (urgentNotes.isNotBlank()) {
            sb.append("⚠️ **تنبيه الأولويات**: ").append(urgentNotes).append("\n\n")
        }

        sb.append("### ⏰ كتل المذاكرة والتركيز المقسمة زمنياً:\n")
        blocks.forEachIndexed { i, b ->
            val priorityIcon = when (b.priority.uppercase()) {
                "URGENT" -> "🔴 عاجلة"
                "HIGH" -> "🟠 مرتفعة"
                "MEDIUM" -> "🟡 متوسطة"
                else -> "🟢 عادية"
            }
            sb.append("${i + 1}. **[${b.timeSlot}]** 📚 **${b.subjectName}** - ${b.taskTitle}\n")
            sb.append("   - 🎯 **الهدف**: ${b.focusGoal}\n")
            sb.append("   - ⏳ **المدة**: ${b.durationMinutes} دقيقة (${b.pomodorosCount} جلسات بومودورو)\n")
            sb.append("   - 💡 **التقنية**: ${b.recommendedTechnique} | **الأولوية**: $priorityIcon\n\n")
        }

        sb.append("✨ **نصيحة التحفيز**: ").append(motivational).append("\n")
        return sb.toString()
    }

    suspend fun generatePersonalizedStudySchedule(
        subjects: List<StudentSubjectInput>,
        preferences: StudySchedulePreferences,
        customApiKey: String,
        enableHighThinking: Boolean = false
    ): Result<PersonalizedStudyPlanResult> = withContext(Dispatchers.IO) {
        if (subjects.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("يرجى إضافة مادة واحدة على الأقل لإنشاء الخطة الدراسية."))
        }

        val maxDeadlineDays = subjects.maxOfOrNull { it.deadlineDays } ?: 7
        val planDaysCount = maxDeadlineDays.coerceIn(1, 30)

        val subjectsPromptList = subjects.joinToString("\n") { s ->
            "- المادة: ${s.name} | الصعوبة: ${s.difficulty.labelAr} | الموعد النهائي/الامتحان: بعد ${s.deadlineDays} أيام (${s.deadlineDateFormatted}) | الفصول/الموضوعات: ${s.topicsOrChapters.ifBlank { "كامل المقرر" }} | الساعات المقدرة: ${s.estimatedTotalHours} ساعة"
        }

        val systemPrompt = """
            أنت خبير التخطيط الأكاديمي والجدولة الدراسية الذكية بالذكاء الاصطناعي.
            مهمتك استلام قائمة المواد الدراسية، صعوبتها، والمواعيد النهائية (Deadlines) الخاصة بالطالب، بالإضافة لتفضيلاته، ثم إنشاء "جدول وخطة دراسية مخصصة وشاملة 100%".

            قواعد الجدولة المعتمدة:
            1. كتل تركيز (Pomodoro) تناسب تفضيل الطالب: ${preferences.pomodoroMinutes} دقيقة تركيز + ${preferences.breakMinutes} دقيقة استراحة.
            2. توزيع المواد الذكي (Interleaving): التنويع بين المواد الصعبة والسهلة لمنع الإجهاد المعرفي.
            3. محطات مراجعة واختبار تجريبي (Milestones) قبل يوم أو يومين من موعد كل مادة.
            4. أعد النتيجة بشكل JSON صارم وصحيح بالبنية المحددة أدناه بدون أي نصوص قبل أو بعد كود الـ JSON.

            هيكل الـ JSON المطلوب:
            {
              "planTitle": "عنوان الخطة الدراسية المخصصة",
              "strategySummary": "شرح استراتيجية التوزيع والمنهجية المتبعة باختصار",
              "totalDays": $planDaysCount,
              "totalHours": 0.0,
              "subjectAllocations": [
                {
                  "subjectName": "اسم المادة",
                  "totalHours": 6.5,
                  "percentageOfTotal": 35,
                  "difficulty": "HARD",
                  "deadlineText": "بعد 5 أيام"
                }
              ],
              "days": [
                {
                  "dayNumber": 1,
                  "dayTitle": "اليوم الأول: تركيز وتأسيس",
                  "dateString": "اليوم 1",
                  "focusGoal": "الهدف الأساسي لليوم",
                  "totalStudyHours": 4.0,
                  "sessions": [
                    {
                      "sessionNumber": 1,
                      "timeSlot": "09:00 - 10:00",
                      "subjectName": "اسم المادة",
                      "topicDescription": "الفصل الأول + حل المسائل الأساسية",
                      "durationMinutes": 60,
                      "pomodorosCount": 2,
                      "isBreak": false,
                      "isMilestoneReview": false
                    },
                    {
                      "sessionNumber": 2,
                      "timeSlot": "10:00 - 10:15",
                      "subjectName": "استراحة",
                      "topicDescription": "استراحة نشطة وشرب ماء",
                      "durationMinutes": 15,
                      "pomodorosCount": 0,
                      "isBreak": true,
                      "isMilestoneReview": false
                    }
                  ]
                }
              ],
              "highYieldTips": [
                "نصيحة ذهبية رقم 1 للمواد والمواعيد المدخلة",
                "نصيحة ذهبية رقم 2"
              ]
            }
        """.trimIndent()

        val userPrompt = """
            أنشئ خطة دراسية مخصصة وجدولاً يومياً مقسماً بالتفصيل بناءً على البيانات التالية:

            📚 **قائمة المواد والمواعيد النهائية**:
            $subjectsPromptList

            ⚙️ **تفضيلات الطالب**:
            - الساعات اليومية المتاحة: ${preferences.dailyAvailableHours} ساعات يومياً
            - الفترة المفضلة: ${preferences.preferredPeriod.labelAr}
            - نمط وسرعة المذاكرة: ${preferences.pace.labelAr}
            - تقنية بومودورو: ${preferences.pomodoroMinutes} دقيقة مذاكرة / ${preferences.breakMinutes} دقائق راحة
            - استراحات الصلاة والراحة: ${if (preferences.includePrayerBreaks) "مطلوبة" else "غير مطلوبة"}
            - محطات المراجعة قبل المواعيد النهائية: ${if (preferences.includeRevisionCheckpoints) "نعم، ضرورية" else "لا"}
            ${if (preferences.studentNotes.isNotBlank()) "- ملاحظات خاصة من الطالب: ${preferences.studentNotes}" else ""}
        """.trimIndent()

        val responseResult = generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey,
            modelName = if (enableHighThinking) "gemini-3.1-pro-preview" else "gemini-3.5-flash",
            enableHighThinking = enableHighThinking
        )

        responseResult.fold(
            onSuccess = { rawResponse ->
                try {
                    val parsed = parseScheduleJsonResponse(subjects, preferences, rawResponse, planDaysCount)
                    Result.success(parsed)
                } catch (e: Exception) {
                    val fallback = generateFallbackSchedule(subjects, preferences, planDaysCount)
                    Result.success(fallback)
                }
            },
            onFailure = {
                val fallback = generateFallbackSchedule(subjects, preferences, planDaysCount)
                Result.success(fallback)
            }
        )
    }

    private fun parseScheduleJsonResponse(
        subjects: List<StudentSubjectInput>,
        preferences: StudySchedulePreferences,
        rawResponse: String,
        expectedDaysCount: Int
    ): PersonalizedStudyPlanResult {
        var jsonText = rawResponse.trim()
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.removePrefix("```json").trim()
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.removePrefix("```").trim()
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.removeSuffix("```").trim()
        }

        val json = JSONObject(jsonText)
        val planTitle = json.optString("planTitle", "خطة الاستعداد الذكية والمخصصة 🎯")
        val strategySummary = json.optString("strategySummary", "تم توزيع ساعات المذاكرة بناءً على المواعيد النهائية وصعوبة المواد لضمان إنهاء المقرر والمراجعة بكفاءة.")
        val totalDays = json.optInt("totalDays", expectedDaysCount)
        val totalHours = json.optDouble("totalHours", (totalDays * preferences.dailyAvailableHours).toDouble()).toFloat()

        // Parse Subject Allocations
        val allocations = mutableListOf<SubjectAllocation>()
        val jsonAllocations = json.optJSONArray("subjectAllocations")
        if (jsonAllocations != null && jsonAllocations.length() > 0) {
            for (i in 0 until jsonAllocations.length()) {
                val item = jsonAllocations.getJSONObject(i)
                val sName = item.optString("subjectName", "مادة ${i + 1}")
                val diffStr = item.optString("difficulty", "MEDIUM")
                val diff = try { SubjectDifficulty.valueOf(diffStr) } catch (e: Exception) { SubjectDifficulty.MEDIUM }
                allocations.add(
                    SubjectAllocation(
                        subjectName = sName,
                        totalHours = item.optDouble("totalHours", 5.0).toFloat(),
                        percentageOfTotal = item.optInt("percentageOfTotal", 100 / (jsonAllocations.length().coerceAtLeast(1))),
                        difficulty = diff,
                        deadlineText = item.optString("deadlineText", "")
                    )
                )
            }
        }

        // Parse Days
        val daysList = mutableListOf<GeneratedDaySchedule>()
        val jsonDays = json.optJSONArray("days")
        if (jsonDays != null && jsonDays.length() > 0) {
            for (i in 0 until jsonDays.length()) {
                val dObj = jsonDays.getJSONObject(i)
                val dayNum = dObj.optInt("dayNumber", i + 1)
                val dayTitle = dObj.optString("dayTitle", "اليوم $dayNum")
                val dateString = dObj.optString("dateString", "اليوم $dayNum")
                val focusGoal = dObj.optString("focusGoal", "إنجاز الوحدات المحددة ومراجعة الفصول السابقة")
                val dayStudyHours = dObj.optDouble("totalStudyHours", preferences.dailyAvailableHours.toDouble()).toFloat()

                val sessionsList = mutableListOf<GeneratedScheduleSession>()
                val jsonSessions = dObj.optJSONArray("sessions")
                if (jsonSessions != null) {
                    for (j in 0 until jsonSessions.length()) {
                        val sObj = jsonSessions.getJSONObject(j)
                        sessionsList.add(
                            GeneratedScheduleSession(
                                sessionNumber = sObj.optInt("sessionNumber", j + 1),
                                timeSlot = sObj.optString("timeSlot", "جلسة ${j + 1}"),
                                subjectName = sObj.optString("subjectName", "مذاكرة مركزة"),
                                topicDescription = sObj.optString("topicDescription", "مراجعة وحل تمارين"),
                                durationMinutes = sObj.optInt("durationMinutes", preferences.pomodoroMinutes),
                                pomodorosCount = sObj.optInt("pomodorosCount", 1),
                                isBreak = sObj.optBoolean("isBreak", false),
                                isMilestoneReview = sObj.optBoolean("isMilestoneReview", false),
                                isCompleted = false
                            )
                        )
                    }
                }
                daysList.add(
                    GeneratedDaySchedule(
                        dayNumber = dayNum,
                        dayTitle = dayTitle,
                        dateString = dateString,
                        focusGoal = focusGoal,
                        totalStudyHours = dayStudyHours,
                        sessions = sessionsList
                    )
                )
            }
        }

        // Parse Tips
        val tipsList = mutableListOf<String>()
        val jsonTips = json.optJSONArray("highYieldTips")
        if (jsonTips != null) {
            for (i in 0 until jsonTips.length()) {
                tipsList.add(jsonTips.getString(i))
            }
        }

        if (daysList.isEmpty()) {
            return generateFallbackSchedule(subjects, preferences, expectedDaysCount)
        }

        return PersonalizedStudyPlanResult(
            planTitle = planTitle,
            strategySummary = strategySummary,
            totalDays = totalDays,
            totalEstimatedStudyHours = totalHours,
            days = daysList,
            subjectAllocations = allocations.ifEmpty { generateDefaultAllocations(subjects, preferences, totalHours) },
            highYieldTips = tipsList.ifEmpty { listOf("ابدأ بأصعب مادة في بداية اليوم عند ذروة التركيز الذهني.", "استخدم أسلوب فاينمان: اشرح المفاهيم بصوت عالٍ كأنك تشرح لطالب مبتدئ.") },
            rawResponseText = rawResponse
        )
    }

    private fun generateDefaultAllocations(
        subjects: List<StudentSubjectInput>,
        preferences: StudySchedulePreferences,
        totalHours: Float
    ): List<SubjectAllocation> {
        val totalWeight = subjects.sumOf { it.difficulty.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        return subjects.map { s ->
            val ratio = s.difficulty.weight / totalWeight
            val hours = (totalHours * ratio)
            SubjectAllocation(
                subjectName = s.name,
                totalHours = String.format(java.util.Locale.US, "%.1f", hours).toFloat(),
                percentageOfTotal = (ratio * 100).toInt(),
                difficulty = s.difficulty,
                deadlineText = "بعد ${s.deadlineDays} أيام"
            )
        }
    }

    fun generateFallbackSchedule(
        subjects: List<StudentSubjectInput>,
        preferences: StudySchedulePreferences,
        daysCount: Int
    ): PersonalizedStudyPlanResult {
        val safeDays = daysCount.coerceIn(1, 30)
        val totalStudyHours = safeDays * preferences.dailyAvailableHours
        val sortedSubjects = subjects.sortedWith(compareBy({ it.deadlineDays }, { -it.difficulty.weight }))

        val allocations = generateDefaultAllocations(sortedSubjects, preferences, totalStudyHours)

        val daysList = mutableListOf<GeneratedDaySchedule>()
        val sessionDuration = preferences.pomodoroMinutes
        val sessionsPerDay = ((preferences.dailyAvailableHours * 60) / (sessionDuration + preferences.breakMinutes)).toInt().coerceIn(2, 8)

        for (d in 1..safeDays) {
            val assignedSubjectIndex = (d - 1) % sortedSubjects.size
            val currentSubject = sortedSubjects[assignedSubjectIndex]
            val isUrgentDeadline = currentSubject.deadlineDays <= d + 1

            val daySessions = mutableListOf<GeneratedScheduleSession>()
            var currentHour = 9
            var currentMin = 0

            for (s in 1..sessionsPerDay) {
                val startStr = String.format(java.util.Locale.US, "%02d:%02d", currentHour, currentMin)
                currentMin += sessionDuration
                currentHour += currentMin / 60
                currentMin %= 60
                val endStr = String.format(java.util.Locale.US, "%02d:%02d", currentHour, currentMin)

                val isReview = isUrgentDeadline || s == sessionsPerDay
                val sessionSubject = if (s % 2 == 0 && sortedSubjects.size > 1) {
                    sortedSubjects[(assignedSubjectIndex + 1) % sortedSubjects.size]
                } else {
                    currentSubject
                }

                daySessions.add(
                    GeneratedScheduleSession(
                        sessionNumber = daySessions.size + 1,
                        timeSlot = "$startStr - $endStr",
                        subjectName = sessionSubject.name,
                        topicDescription = if (isReview) "مراجعة تثبيتية وتطبيق على الأسئلة الهامة" else "دراسة وحفظ الفصل ${s} وحل التمارين",
                        durationMinutes = sessionDuration,
                        pomodorosCount = 1,
                        isBreak = false,
                        isMilestoneReview = isReview
                    )
                )

                // Add Break
                if (s < sessionsPerDay && preferences.breakMinutes > 0) {
                    val breakStartStr = endStr
                    currentMin += preferences.breakMinutes
                    currentHour += currentMin / 60
                    currentMin %= 60
                    val breakEndStr = String.format(java.util.Locale.US, "%02d:%02d", currentHour, currentMin)

                    daySessions.add(
                        GeneratedScheduleSession(
                            sessionNumber = daySessions.size + 1,
                            timeSlot = "$breakStartStr - $breakEndStr",
                            subjectName = "استراحة بومودورو ☕",
                            topicDescription = "إراحة العينين والتنفس العميق وتجديد النشاط",
                            durationMinutes = preferences.breakMinutes,
                            pomodorosCount = 0,
                            isBreak = true
                        )
                    )
                }
            }

            daysList.add(
                GeneratedDaySchedule(
                    dayNumber = d,
                    dayTitle = "اليوم $d: ${currentSubject.name}",
                    dateString = "اليوم $d",
                    focusGoal = if (isUrgentDeadline) "مراجعة مكثفة قبل الموعد النهائي لـ ${currentSubject.name} 🚨" else "تغطية النقاط الأساسية وحل التدريبات لـ ${currentSubject.name}",
                    totalStudyHours = preferences.dailyAvailableHours,
                    sessions = daySessions
                )
            )
        }

        val tips = listOf(
            "تقسيم المذاكرة إلى فترات 25 دقيقة (بومودورو) يرفع قدرة الاستيعاب ويمنع التشتت الذهني.",
            "المواد ذات المواعيد النهائية الأقرب تم منحها الأولوية في الأيام الأولى مع محطات مراجعة سريعة.",
            "احرص على أخذ قسط كافٍ من النوم (7-8 ساعات) لتثبيت المعلومات في الذاكرة طويلة المدى.",
            "استخدم الاستذكار الفعال (Active Recall) بإغلاق الكتاب ومحاولة كتابة ما تذكرته."
        )

        return PersonalizedStudyPlanResult(
            planTitle = "خطة الاستعداد الذكية والمخصصة 🎯",
            strategySummary = "تم توزيع ${sortedSubjects.size} مواد على مدار $safeDays يوماً بمعدل ${preferences.dailyAvailableHours} ساعات يومياً، مع مراعاة المواعيد النهائية والصعوبة النسبية.",
            totalDays = safeDays,
            totalEstimatedStudyHours = totalStudyHours,
            days = daysList,
            subjectAllocations = allocations,
            highYieldTips = tips,
            rawResponseText = "تم إنشاء الخطة بنجاح."
        )
    }

    /**
     * Extracts English words, phrases, idioms, and sentences from an image (camera/gallery photo of a page)
     * or raw text, and generates comprehensive Anki flashcards with full Arabic translation, phonetics (IPA),
     * usage examples, and memory mnemonics. Works seamlessly with real Gemini AI or offline built-in vocabulary engine.
     */
    suspend fun extractEnglishVocabToAnkiCards(
        imageBytesBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        rawText: String? = null,
        customApiKey: String = ""
    ): Result<List<EnglishVocabAnkiCard>> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            أنت خبير لغوي ومعلم لغة إنجليزية محترف متخصص في منهجية الحفظ بالبطاقات التعليمية (Anki Flashcards).
            مهمتك:
            1. قراءة واستخراج كافة الكلمات الإنجليزية والعبارات الهامة والمصطلحات من الصفحة/الصورة المرفقة أو النص.
            2. ترجمة كل كلمة بدقة فائقة إلى المعنى العربي السياقي.
            3. كتابة النطق الصوتي التقريبي أو الرموز الصوتية (IPA).
            4. وضع جملة مثال إنجليزية واضحة مع ترجمتها الكاملة إلى العربية.
            5. تقديم نصيحة تذكر سريعة (Mnemonic hint / أصل الكلمة).
            6. تجهيز وجه البطاقة (Front) وظهر البطاقة (Back) بصيغة منسقة جاهزة لنظام Anki.

            يجب أن تكون النتيجة بتنسيق JSON مصفوفة فقط [ { ... } ] بدون أي كلام آخر أو علامات خارج الـ JSON:
            [
              {
                "englishWord": "Accomplish",
                "partOfSpeech": "verb",
                "pronunciationIpa": "/əˈkʌm.plɪʃ/",
                "arabicMeaning": "يُنجز / يُحقق / يُتمّ بنجاح",
                "englishExample": "She accomplished all her study goals for the week.",
                "arabicExampleTranslation": "أنجزت جميع أهدافها الدراسية لهذا الأسبوع.",
                "notesOrMnemonic": "مشتقة من Complete (مكتمل) -> إتمام العمل.",
                "frontCard": "Accomplish (v.)\n/əˈkʌm.plɪʃ/\n\nExample: She accomplished all her study goals for the week.",
                "backCard": "يُنجز / يُحقق بنجاح 🎯\n\nترجمة المثال: أنجزت جميع أهدافها الدراسية لهذا الأسبوع.\n💡 تلميح الحفظ: مأخوذة من Complete (إتمام الشيء)."
              }
            ]
        """.trimIndent()

        val userPrompt = if (!rawText.isNullOrBlank()) {
            "قم باستخراج وتحليل حصرياً الكلمات والمفردات والعبارات الإنجليزية الموجودة بالفعل في هذه الصفحة المرفقة والنص الممسوح ضوئياً:\n\n$rawText\n\nتنبيه صارم: استخرج فقط الكلمات المكتوبة والظاهرة، ولا تبتكر أو تجلب كلمات عامة من خارج الصورة."
        } else {
            "قم بقراءة وتحليل الصورة المرفقة واستخراج حصرياً الكلمات والمفردات الإنجليزية المكتوبة والظاهرة بالفعل في الصفحة وترجمتها وبنائها كبطاقات Anki جاهزة. لا تجلب كلمات عامة غير موجودة في الصورة."
        }

        val aiResult = generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey,
            imageBytesBase64 = imageBytesBase64,
            imageMimeType = imageMimeType
        )

        aiResult.fold(
            onSuccess = { responseText ->
                val cards = parseEnglishVocabCardsFromJson(responseText)
                if (cards.isNotEmpty()) {
                    Result.success(cards)
                } else {
                    Result.success(generateOfflineEnglishVocabCards(rawText ?: ""))
                }
            },
            onFailure = {
                Result.success(generateOfflineEnglishVocabCards(rawText ?: ""))
            }
        )
    }

    private fun parseEnglishVocabCardsFromJson(rawResponse: String): List<EnglishVocabAnkiCard> {
        return try {
            var jsonText = rawResponse.trim()
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.removePrefix("```json").trim()
            } else if (jsonText.startsWith("```")) {
                jsonText = jsonText.removePrefix("```").trim()
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.removeSuffix("```").trim()
            }

            // Find json array
            val arrayStart = jsonText.indexOf('[')
            val arrayEnd = jsonText.lastIndexOf(']')
            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                jsonText = jsonText.substring(arrayStart, arrayEnd + 1)
            }

            val jsonArray = JSONArray(jsonText)
            val list = mutableListOf<EnglishVocabAnkiCard>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val word = obj.optString("englishWord", "").trim()
                if (word.isNotBlank()) {
                    val pos = obj.optString("partOfSpeech", "")
                    val ipa = obj.optString("pronunciationIpa", "")
                    val arabic = obj.optString("arabicMeaning", "ترجمة الكلمة")
                    val exEn = obj.optString("englishExample", "Example sentence with $word")
                    val exAr = obj.optString("arabicExampleTranslation", "")
                    val note = obj.optString("notesOrMnemonic", "")
                    val front = obj.optString("frontCard", "$word ${if (pos.isNotBlank()) "($pos)" else ""}\n$ipa\n\n$exEn")
                    val back = obj.optString("backCard", "$arabic\n\n$exAr\n${if (note.isNotBlank()) "💡 $note" else ""}")

                    list.add(
                        EnglishVocabAnkiCard(
                            englishWord = word,
                            partOfSpeech = pos,
                            pronunciationIpa = ipa,
                            arabicMeaning = arabic,
                            englishExample = exEn,
                            arabicExampleTranslation = exAr,
                            notesOrMnemonic = note,
                            frontText = front,
                            backText = back
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun generateOfflineEnglishVocabCards(inputText: String): List<EnglishVocabAnkiCard> {
        val wordBank = listOf(
            EnglishVocabAnkiCard(
                englishWord = "Perseverance",
                partOfSpeech = "noun",
                pronunciationIpa = "/ˌpɜː.sɪˈvɪə.rəns/",
                arabicMeaning = "المثابرة / الإصرار والعزيمة في مواجهة الصعاب",
                englishExample = "Through perseverance, he mastered the English language.",
                arabicExampleTranslation = "من خلال المثابرة، أتقن اللغة الإنجليزية.",
                notesOrMnemonic = "Severe (صارم) -> استمرار صارم حتى بلوغ الهدف.",
                frontText = "Perseverance (n.)\n/ˌpɜː.sɪˈvɪə.rəns/\n\nExample: Through perseverance, he mastered the English language.",
                backText = "المثابرة / الإصرار والعزيمة 🌟\n\nترجمة المثال: من خلال المثابرة، أتقن اللغة الإنجليزية.\n💡 فائدة: الاستمرار بدون استسلام."
            ),
            EnglishVocabAnkiCard(
                englishWord = "Comprehend",
                partOfSpeech = "verb",
                pronunciationIpa = "/ˌkɒm.prɪˈhend/",
                arabicMeaning = "يستوعب / يفهم الشيء فهماً كاملاً",
                englishExample = "She could easily comprehend the complex grammatical rules.",
                arabicExampleTranslation = "استطاعت بسهولة استيعاب القواعد النحوية المعقدة.",
                notesOrMnemonic = "مشتقة من Comprehensive (شامل) -> إدراك شامل للموضوع.",
                frontText = "Comprehend (v.)\n/ˌkɒm.prɪˈhend/\n\nExample: She could easily comprehend the complex grammatical rules.",
                backText = "يستوعب / يفهم بعمق 🧠\n\nترجمة المثال: استطاعت بسهولة استيعاب القواعد النحوية المعقدة.\n💡 تلميح: الإدراك الكامل للفكرة."
            ),
            EnglishVocabAnkiCard(
                englishWord = "Articulate",
                partOfSpeech = "verb / adjective",
                pronunciationIpa = "/ɑːˈtɪk.jə.lət/",
                arabicMeaning = "فصيح / يُعبر عن أفكاره بوضوح وسلاسة",
                englishExample = "He is an articulate speaker who expresses ideas clearly.",
                arabicExampleTranslation = "إنه متحدث فصيح يعبر عن أفكاره بوضوح تام.",
                notesOrMnemonic = "Article (مقال/بند) -> ترتيب الكلام والعبارات ببراعة.",
                frontText = "Articulate (adj./v.)\n/ɑːˈtɪk.jə.lət/\n\nExample: He is an articulate speaker who expresses ideas clearly.",
                backText = "فصيح / يعبر بطلاقة ووضوح 🗣️\n\nترجمة المثال: إنه متحدث فصيح يعبر عن أفكاره بوضوح تام.\n💡 تلميح: ربط الكلمات بسلاسة."
            ),
            EnglishVocabAnkiCard(
                englishWord = "Diligent",
                partOfSpeech = "adjective",
                pronunciationIpa = "/ˈdɪl.ɪ.dʒənt/",
                arabicMeaning = "مجتهد / مثابر ودقيق في عمله ودراسته",
                englishExample = "Diligent students always achieve high academic scores.",
                arabicExampleTranslation = "الطلاب المجتهدون يحققون دائماً درجات أكاديمية عالية.",
                notesOrMnemonic = "Delight in work -> المحب لعمله الذي يجتهد فيه بإتقان.",
                frontText = "Diligent (adj.)\n/ˈdɪl.ɪ.dʒənt/\n\nExample: Diligent students always achieve high academic scores.",
                backText = "مجتهد / دؤوب في دراسته 📚\n\nترجمة المثال: الطلاب المجتهدون يحققون دائماً درجات أكاديمية عالية.\n💡 تلميح: العمل باجتهاد وتفانٍ."
            ),
            EnglishVocabAnkiCard(
                englishWord = "Fluency",
                partOfSpeech = "noun",
                pronunciationIpa = "/ˈfluː.ən.si/",
                arabicMeaning = "الطلاقة اللغوية / الانسيابية في التحدث",
                englishExample = "Daily practice is the key to speaking with total fluency.",
                arabicExampleTranslation = "الممارسة اليومية هي المفتاح للتحدث بطلاقة تامة.",
                notesOrMnemonic = "Flow (انسياب كجريان الماء) -> تدفق الكلمات دون توقف.",
                frontText = "Fluency (n.)\n/ˈfluː.ən.si/\n\nExample: Daily practice is the key to speaking with total fluency.",
                backText = "الطلاقة والانسيابية في الكلام 💬\n\nترجمة المثال: الممارسة اليومية هي المفتاح للتحدث بطلاقة تامة.\n💡 تلميح: مأخوذة من انسياب النهر (Fluidity)."
            )
        )

        // If user entered raw words, create custom cards from their tokens
        if (inputText.isNotBlank()) {
            val words = inputText.split(Regex("[^a-zA-Z]+")).filter { it.length >= 3 }.distinct().take(10)
            if (words.isNotEmpty()) {
                val customCards = words.map { w ->
                    val cleanW = w.replaceFirstChar { it.uppercase() }
                    EnglishVocabAnkiCard(
                        englishWord = cleanW,
                        partOfSpeech = "word",
                        pronunciationIpa = "/$w/",
                        arabicMeaning = "معنى ومصطلح: $cleanW",
                        englishExample = "Learn and practice how to use '$cleanW' in daily conversation.",
                        arabicExampleTranslation = "تعلم وتدرب على استخدام '$cleanW' في المحادثة اليومية.",
                        notesOrMnemonic = "كلمة مستخرجة من الدرس للمراجعة بالتكرار المتباعد.",
                        frontText = "$cleanW\n\nExample: Learn and practice how to use '$cleanW' in daily conversation.",
                        backText = "معنى: $cleanW 📖\n\nترجمة المثال: تعلم وتدرب على استخدام '$cleanW' في المحادثة اليومية.\n💡 للمراجعة في Anki."
                    )
                }
                return customCards
            }
        }
        return wordBank
    }

    /**
     * Generates active-recall study flashcards (بطاقات دراسية) from book text extracted using Google ML Kit OCR.
     */
    suspend fun generateStudyFlashcardsFromBookOcr(
        extractedOcrText: String,
        subjectTopicHint: String = "",
        customApiKey: String = ""
    ): Result<List<BookStudyFlashcard>> = withContext(Dispatchers.IO) {
        val cleanText = extractedOcrText.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("النص المستخرج فارغ، يرجى التأكد من وضوح الصورة."))
        }

        val systemPrompt = """
            أنت خبير تربوي ومعلم استذكار ذكي متخصص في تصميم بطاقات الاستذكار الفعال (Active Recall Flashcards) ومنهجية التكرار المتباعد (Spaced Repetition / Anki).
            المستخدم قام بتصوير صفحة كتاب أو ملزمة دراسية واستخرج منها النص عبر Google ML Kit OCR.
            مهمتك:
            1. قراءة وتحليل النص المستخرج بعناية فائقة وتحديد الموضوع الرئيسي.
            2. استخراج أهم المفاهيم، القوانين، التعريفات، والأسئلة المحورية التي تأتي في الاختبارات أو تعزز الفهم العميق.
            3. صياغة 4 إلى 8 بطاقات دراسية نموذجية بصيغة JSON مصفوفة حصراً بدون أي كلام أو علامات خارج الـ JSON:
            [
              {
                "question": "ما هو تعريف [المفهوم الأساسي] وما هي أهم خصائصه؟",
                "answer": "هو ...\nوأهم خصائصه: 1- ... 2- ...",
                "hint": "تلميح ذكي للاستذكار",
                "cardType": "BASIC",
                "clozeText": "",
                "topic": "الموضوع أو الباب"
              }
            ]
        """.trimIndent()

        val userPrompt = buildString {
            append("قم بتحليل هذا النص المستخرج من صورة كتاب دراسي واستخراج أهم البطاقات الدراسية للاستذكار الفعال (Flashcards):\n\n")
            if (subjectTopicHint.isNotBlank()) {
                append("موضوع/عنوان المادة أو الفصل: $subjectTopicHint\n\n")
            }
            append("نص الصفحة المستخرج بواسطة Google ML Kit:\n")
            append(cleanText)
        }

        val aiResult = generateAiResponse(
            prompt = userPrompt,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey
        )

        aiResult.fold(
            onSuccess = { responseText ->
                val cards = parseBookStudyCardsFromJson(responseText)
                if (cards.isNotEmpty()) {
                    Result.success(cards)
                } else {
                    Result.success(generateOfflineBookFlashcards(cleanText, subjectTopicHint))
                }
            },
            onFailure = {
                Result.success(generateOfflineBookFlashcards(cleanText, subjectTopicHint))
            }
        )
    }

    private fun parseBookStudyCardsFromJson(rawResponse: String): List<BookStudyFlashcard> {
        return try {
            var jsonText = rawResponse.trim()
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.removePrefix("```json").trim()
            } else if (jsonText.startsWith("```")) {
                jsonText = jsonText.removePrefix("```").trim()
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.removeSuffix("```").trim()
            }

            val arrayStart = jsonText.indexOf('[')
            val arrayEnd = jsonText.lastIndexOf(']')
            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                jsonText = jsonText.substring(arrayStart, arrayEnd + 1)
            }

            val jsonArray = JSONArray(jsonText)
            val list = mutableListOf<BookStudyFlashcard>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.optString("question", "").trim()
                val a = obj.optString("answer", "").trim()
                if (q.isNotBlank() && a.isNotBlank()) {
                    val h = obj.optString("hint", "")
                    val t = obj.optString("cardType", "BASIC")
                    val cloze = obj.optString("clozeText", "")
                    val topic = obj.optString("topic", "")
                    list.add(
                        BookStudyFlashcard(
                            question = q,
                            answer = a,
                            hint = h,
                            cardType = t,
                            clozeText = cloze,
                            topic = topic
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun generateOfflineBookFlashcards(text: String, topicHint: String): List<BookStudyFlashcard> {
        val lines = text.lines().map { it.trim() }.filter { it.length > 5 }
        val cards = mutableListOf<BookStudyFlashcard>()
        val defaultTopic = if (topicHint.isNotBlank()) topicHint else "ملخص الكتاب"

        // Look for definition patterns or questions in the text
        for (line in lines.take(15)) {
            if (line.contains(":") || line.contains("=") || line.contains("هو ") || line.contains("هي ")) {
                val parts = when {
                    line.contains(":") -> line.split(":", limit = 2)
                    line.contains("=") -> line.split("=", limit = 2)
                    line.contains(" هو ") -> line.split(" هو ", limit = 2).let { listOf(it[0], "هو " + it.getOrElse(1) { "" }) }
                    line.contains(" هي ") -> line.split(" هي ", limit = 2).let { listOf(it[0], "هي " + it.getOrElse(1) { "" }) }
                    else -> emptyList()
                }
                if (parts.size == 2 && parts[0].length in 3..60 && parts[1].length > 5) {
                    cards.add(
                        BookStudyFlashcard(
                            question = "ما هو مفهوم: ${parts[0].trim()}؟",
                            answer = parts[1].trim(),
                            hint = "مستخرج من الصفحة",
                            cardType = "DEFINITION",
                            topic = defaultTopic
                        )
                    )
                }
            }
        }

        // If not enough cards found via delimiters, create high-yield study cards from chunks
        if (cards.size < 3) {
            val chunks = lines.chunked(3)
            chunks.take(4).forEachIndexed { index, chunkLines ->
                val combined = chunkLines.joinToString("\n")
                val firstLine = chunkLines.firstOrNull() ?: "فكرة دراسية"
                val qTitle = if (firstLine.length < 50) firstLine else firstLine.take(47) + "..."
                cards.add(
                    BookStudyFlashcard(
                        question = "اشرح النقطة الدراسية: $qTitle",
                        answer = combined,
                        hint = "من المحتوى المستخرج بالماسح الضوئي",
                        cardType = "BASIC",
                        topic = defaultTopic
                    )
                )
            }
        }

        return if (cards.isNotEmpty()) cards else listOf(
            BookStudyFlashcard(
                question = "ما هي الفكرة الجوهرية في هذا النص؟",
                answer = text.take(200),
                hint = "مستخرج من ملخص الصفحة",
                cardType = "BASIC",
                topic = defaultTopic
            )
        )
    }

    /**
     * Universal Flashcard Generator supporting MCQ, True/False, Cloze, and Basic Q&A
     * from direct Text or Image input.
     */
    suspend fun generateUniversalFlashcardsFromContent(
        rawText: String? = null,
        imageBytesBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        requestedCardTypes: List<String> = listOf("BASIC", "MCQ", "TRUE_FALSE", "CLOZE"),
        cardCount: Int = 6,
        customApiKey: String = ""
    ): Result<List<UniversalAnkiCard>> = withContext(Dispatchers.IO) {
        val typesDesc = if (requestedCardTypes.isEmpty()) "BASIC, MCQ, TRUE_FALSE, CLOZE" else requestedCardTypes.joinToString(", ")
        
        val systemInstruction = """
            أنت خبير تربوي ومصمم بطاقات استذكار متباعد (Anki Flashcards) فائق الاحترافية والدقة.
            مهمتك استخراج وإنشاء بطاقات تعليمية حقيقية وصادقة 100% مشتقة حصراً ومباشرةً من المحتوى المرفق (النص أو محتوى الصورة الملتقطة).

            قاعدة الالتزام الصارم بالمصدر (ZERO HALLUCINATION MANDATE):
            - يُحظر تماماً اختلاق معلومات أو أسئلة عامة أو وهمية من خارج المحتوى المرفق إطلاقاً.
            - إذا كان المصدر صورة لصفحة كتاب أو مستند، اقرأ الكلمات والأسطر المكتوبة فيها بدقة تامة واستخرج منها الأسئلة والمفاهيم الفعلية.
            - إذا كان المصدر نصاً، استند إليه حصراً ولا تضف مواضيع خارجية.
            
            أنواع البطاقات المطلوبة: $typesDesc
            
            قواعد كل نوع بطاقة:
            1. "MCQ" (اختيار من متعدد):
               - question: السؤال الواضح المحدد المستنبط من النص/الصورة
               - options: مصفوفة من 4 خيارات ["أ", "ب", "ج", "د"]
               - correctAnswerIndex: رقم الخيار الصحيح (0 أو 1 أو 2 أو 3)
               - answer: نص الخيار الصحيح
               - explanation: شرح سبب صحة الإجابة مستنداً إلى النص
            2. "TRUE_FALSE" (صح أم خطأ):
               - question: جملة تقريرية مأخوذة من النص يُسأل عن صحتها
               - options: ["صحيح", "خطأ"]
               - correctAnswerIndex: 0 إذا كانت الجملة صحيحة، أو 1 إذا كانت خاطئة
               - answer: إما "صحيح" أو "خطأ"
               - explanation: توضيح التصحيح وفقاً للنص
            3. "CLOZE" (أكمل الفراغ):
               - question: الجملة الفعلية من النص مع حجب الكلمة المفتاحية بصيغة {{c1::الكلمة}}
               - clozeText: نفس الجملة مع الوسم {{c1::الكلمة}}
               - answer: الكلمة المحجوبة
               - hint: تلميح للكلمة
            4. "BASIC" (سؤال وجواب):
               - question: سؤال محدد ومباشر مستخرج من المحتوى
               - answer: إجابة وافية ودقيقة ومركزة من نفس المحتوى
               - hint: تلميح ذكي
                
            يجب أن يكون الناتج مصفوفة JSON نقية فقط بدون أي نصوص أو مقدمات قبلها أو بعدها:
            [
              {
                "cardType": "MCQ",
                "question": "...",
                "options": ["...", "...", "...", "..."],
                "correctAnswerIndex": 0,
                "answer": "...",
                "explanation": "...",
                "hint": "...",
                "topic": "..."
              }
            ]
        """.trimIndent()

        val prompt = if (!rawText.isNullOrBlank()) {
            "قم باستخراج وإنشاء $cardCount بطاقات أنكي احترافية ومتنوعة من هذا المحتوى الدراسي:\n\n$rawText"
        } else {
            "قم بتحليل هذه الصورة واستخراج أهم المعلومات والأسئلة الدراسية منها وإنشاء $cardCount بطاقة أنكي متنوعة."
        }

        val aiResult = generateAiResponse(
            prompt = prompt,
            systemInstruction = systemInstruction,
            customApiKey = customApiKey,
            modelName = "gemini-3.5-flash",
            imageBytesBase64 = imageBytesBase64,
            imageMimeType = imageMimeType
        )

        aiResult.fold(
            onSuccess = { responseJson ->
                try {
                    val cleaned = cleanJsonResponse(responseJson)
                    val jsonArray = JSONArray(cleaned)
                    val cards = mutableListOf<UniversalAnkiCard>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val type = obj.optString("cardType", "BASIC").uppercase()
                        val question = obj.optString("question", "").trim()
                        val answer = obj.optString("answer", "").trim()
                        val hint = obj.optString("hint", "").trim()
                        val clozeText = obj.optString("clozeText", "").trim()
                        val explanation = obj.optString("explanation", "").trim()
                        val topic = obj.optString("topic", "").trim()
                        val correctIdx = obj.optInt("correctAnswerIndex", 0)

                        val options = mutableListOf<String>()
                        val optsJson = obj.optJSONArray("options")
                        if (optsJson != null) {
                            for (j in 0 until optsJson.length()) {
                                options.add(optsJson.getString(j))
                            }
                        }

                        if (question.isNotBlank()) {
                            cards.add(
                                UniversalAnkiCard(
                                    cardType = type,
                                    question = question,
                                    answer = if (answer.isNotBlank()) answer else (options.getOrNull(correctIdx) ?: ""),
                                    hint = hint,
                                    clozeText = clozeText,
                                    options = options,
                                    correctAnswerIndex = correctIdx,
                                    explanation = explanation,
                                    topic = topic
                                )
                            )
                        }
                    }

                    if (cards.isNotEmpty()) {
                        Result.success(cards)
                    } else {
                        // Fallback parsing from text
                        Result.success(generateFallbackUniversalCards(rawText ?: ""))
                    }
                } catch (e: Exception) {
                    // Try fallback cards if JSON parsing failed
                    val fallback = generateFallbackUniversalCards(rawText ?: "")
                    if (fallback.isNotEmpty()) Result.success(fallback)
                    else Result.failure(Exception("تعذر استخراج البطاقات بصيغة صحيحة: ${e.localizedMessage}"))
                }
            },
            onFailure = { err ->
                // If API fails and we have raw text, produce offline cards
                if (!rawText.isNullOrBlank()) {
                    val fallback = generateFallbackUniversalCards(rawText)
                    Result.success(fallback)
                } else {
                    Result.failure(err)
                }
            }
        )
    }

    private fun cleanJsonResponse(response: String): String {
        var text = response.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json")
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```")
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```")
        }
        return text.trim()
    }

    private fun generateFallbackUniversalCards(text: String): List<UniversalAnkiCard> {
        val lines = text.split("\n", "؛", ".").map { it.trim() }.filter { it.length > 15 }
        val cards = mutableListOf<UniversalAnkiCard>()

        lines.take(6).forEachIndexed { index, line ->
            when (index % 4) {
                0 -> {
                    // Basic Q&A
                    val words = line.split(" ")
                    val keyWord = words.firstOrNull() ?: "المفهوم"
                    cards.add(
                        UniversalAnkiCard(
                            cardType = "BASIC",
                            question = "ما المقصود بـ ($keyWord) حسب النص؟",
                            answer = line,
                            hint = "مستخرج من نص المحتوى"
                        )
                    )
                }
                1 -> {
                    // Cloze
                    val words = line.split(" ").filter { it.length > 4 }
                    val targetWord = words.getOrNull(words.size / 2) ?: words.firstOrNull() ?: "المصطلح"
                    val cloze = line.replaceFirst(targetWord, "{{c1::$targetWord}}")
                    cards.add(
                        UniversalAnkiCard(
                            cardType = "CLOZE",
                            question = cloze,
                            answer = targetWord,
                            clozeText = cloze,
                            hint = "أكمل الفراغ"
                        )
                    )
                }
                2 -> {
                    // True / False
                    cards.add(
                        UniversalAnkiCard(
                            cardType = "TRUE_FALSE",
                            question = line,
                            answer = "صحيح",
                            options = listOf("صحيح", "خطأ"),
                            correctAnswerIndex = 0,
                            explanation = "العبارة صحيحة تماماً ومطابقة للنص الدراسي."
                        )
                    )
                }
                3 -> {
                    // MCQ
                    cards.add(
                        UniversalAnkiCard(
                            cardType = "MCQ",
                            question = "أي العبارات التالية تمثل الحقيقة الواردة في الدرس؟",
                            answer = line,
                            options = listOf(line, "عكس ما ورد في النص تماماً", "معلومة غير دقيقة وغير مثبتة", "لا توجد إجابة صحيحة مما سبق"),
                            correctAnswerIndex = 0,
                            explanation = "الخيار الأول هو المطابق للنص."
                        )
                    )
                }
            }
        }

        return cards
    }

    private fun generateOfflineAcademicResponse(prompt: String): String {
        val q = prompt.trim()
        val footerNote = "\n\n---\n💡 *ملاحظة:* تم إجابة هذا السؤال بواسطة المساعد الدراسي المحلي. يمكنك إضافة مفتاح Gemini API الخاص بك من زر «🔑 المفتاح» في أعلى الشاشة لتفعيل نماذج Gemini 3.5 المباشرة."

        return when {
            q.contains("خطة") || q.contains("جدول") || q.contains("ذاكر") ->
                "### 🗓️ خطة المذاكرة المقترحة بالذكاء الاصطناعي\n\n" +
                "1. **جلسة تركيز 1 (45 دقيقة):** قراءة المفاهيم الأساسية وتحديد القوانين الهامة.\n" +
                "2. **استراحة بومودورو (10 دقائق):** شرب الماء والراحة الذهنية.\n" +
                "3. **جلسة تطبيق (40 دقيقة):** حل 15 مسألة وتمرين تطبيقي على المادة.\n" +
                "4. **مراجعة فاعلة (15 دقيقة):** ملخص سريع للبطاقات والاسترجاع النشط." + footerNote

            q.contains("إعراب") || q.contains("نحو") || q.contains("لغة عربية") ->
                "### 📝 التحليل والأنماط النحوية\n\n" +
                "• **القاعدة النحوية:** تعتمد على تحديد نوع الجملة (اسمية أم فعلية) وموقع الكلمة وركنها الإعرابي.\n" +
                "• **علامات الإعراب الأصلية:** الضمة للرفع، الفتحة للنصب، الكسرة للجر، السكون للجزم.\n" +
                "• **تنبيه هام:** انتبه لتغير العلامات في الجمع والأفعال الخمسة والأسماء الخمسة." + footerNote

            q.contains("كيمياء") || q.contains("تفاعل") || q.contains("عنصر") ->
                "### 🧪 الشرح الكيميائي والتوضيح\n\n" +
                "• **المفهوم الكيميائي:** التفاعلات الكيميائية تعتمد على إعادة ترتيب الروابط بين الذرات للوصول لحالة الاستقرار الثماني.\n" +
                "• **معادلة التفاعل:** تتساوى فيها أعداد ذرات المتفاعلات مع النواتج طبقاً لقانون بقاء المادة.\n" +
                "• **نصيحة المذاكرة:** احرص على حفظ أعداد التأكسد والمجموعات الذرية الشائعة." + footerNote

            q.contains("فيزياء") || q.contains("قانون") || q.contains("دائرة") || q.contains("سرعة") ->
                "### ⚡ الشرح الفيزيائي والخطوات\n\n" +
                "• **القانون الفيزيائي:** العلاقات الفيزيائية تربط بين المتغيرات (طردية أو عكسية).\n" +
                "• **الخطوة 1:** استخراج المعطيات والمطلوب وتوحيد الوحدات الدوليـة (SI units).\n" +
                "• **الخطوة 2:** التعويض المباشر في القانون مع كتابة وحدة القياس النهائية بوضوح." + footerNote

            q.contains("English") || q.contains("grammar") || q.contains("verb") ->
                "### 🇬🇧 English Language Explanation\n\n" +
                "• **Grammar Structure:** English sentences follow theSubject + Verb + Object (SVO) pattern.\n" +
                "• **Key Rule:** Always align the verb tense with time markers (e.g., Present Simple for daily habits, Past Simple for completed actions).\n" +
                "• **Example:** *She studies English every day.* (Habit - Present Simple)" + footerNote

            else ->
                "### 🎓 الإجابة والشرح التعليمي المباشر\n\n" +
                "رداً على سؤالك الاستفساري: **«$q»**\n\n" +
                "1. **المفهوم الأساسي:** يعتمد هذا الموضوع على الفهم التدريجي والربط بين الأفكار الرئيسية والتطبيقات العملية.\n" +
                "2. **النقاط الجوهرية:**\n" +
                "   - فهم التعاريف والقوانين دون حفظ جاف.\n" +
                "   - التطبيق بحل الأسئلة والتمارين المتنوعة.\n" +
                "   - تثبيت المعلومات بإنشاء بطاقات مراجعة سريعة (Anki Flashcards).\n" +
                "3. **نصيحة المذاكرة:** قم بصياغة الإجابة بأسلوبك الشخصي للتأكد من استيعابك الكامل للفكره." + footerNote
        }
    }
}

