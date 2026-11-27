package com.example.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiConnectionTestResult(
    val isSuccess: Boolean,
    val modelUsed: String,
    val messageAr: String,
    val httpCode: Int = 0
)

/**
 * عميل Gemini API الموحد والمصحح لـ «مُتعلِّم»:
 * - ينظف المفتاح تلقائياً عبر .trim() وإزالة الأسطر والمسافات الزائدة.
 * - يستخدم المسار القياسي v1beta مع نموذج gemini-2.5-flash وتبديل تلقائي (Fallback) إلى gemini-2.0-flash عند خطأ 404 أو 429.
 * - مهلة اتصال 60 ثانية لضمان عدم انقطاع الردود الدراسية الطويلة أو تحليل صور PDF.
 * - ينظف ردود JSON من علامات الماركداون (```json و ```) قبل التحليل.
 */
class FahimGeminiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    suspend fun generateContent(
        prompt: String,
        apiKey: String,
        systemInstruction: String = "",
        preferredModel: String = FahimConfig.PRIMARY_MODEL,
        imageBytesBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        audioBytesBase64: String? = null,
        audioMimeType: String = "audio/mp3",
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        temperature: Double = 0.3,
        responseMimeType: String? = null,
        enableGoogleSearch: Boolean = false,
        enableGoogleMaps: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = FahimConfig.cleanApiKey(apiKey)
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("لا يوجد مفتاح Gemini API مُعرّف"))
        }

        val modelsToTry = buildModelAttemptList(preferredModel)
        var lastError: Throwable = IllegalStateException("فشل الاتصال بنموذج Gemini")

        for (i in modelsToTry.indices) {
            val model = modelsToTry[i]
            val outcome = executeSingleModelRequest(
                model = model,
                cleanKey = cleanKey,
                prompt = prompt,
                systemInstruction = systemInstruction,
                imageBytesBase64 = imageBytesBase64,
                imageMimeType = imageMimeType,
                audioBytesBase64 = audioBytesBase64,
                audioMimeType = audioMimeType,
                chatHistory = chatHistory,
                temperature = temperature,
                responseMimeType = responseMimeType,
                enableGoogleSearch = enableGoogleSearch,
                enableGoogleMaps = enableGoogleMaps
            )

            if (outcome.text != null) {
                return@withContext Result.success(outcome.text)
            }

            lastError = outcome.error ?: IllegalStateException("HTTP ${outcome.httpCode}")

            val shouldFallback = (outcome.httpCode == 404 || outcome.httpCode == 429) &&
                i < modelsToTry.lastIndex
            if (!shouldFallback) {
                return@withContext Result.failure(lastError)
            }
        }

        Result.failure(lastError)
    }

    suspend fun testConnection(rawApiKey: String): GeminiConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanKey = FahimConfig.cleanApiKey(rawApiKey)
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiConnectionTestResult(
                isSuccess = false,
                modelUsed = FahimConfig.PRIMARY_MODEL,
                messageAr = "أدخل مفتاح Gemini API أولاً ثم اضغط اختبار الاتصال."
            )
        }

        val modelsToTry = buildModelAttemptList(FahimConfig.PRIMARY_MODEL)
        var lastCode = 0
        var lastErr = "تعذر الاتصال"

        for (i in modelsToTry.indices) {
            val model = modelsToTry[i]
            val outcome = executeSingleModelRequest(
                model = model,
                cleanKey = cleanKey,
                prompt = "أجب بكلمة واحدة فقط: جاهز",
                systemInstruction = "أجب بكلمة جاهز فقط.",
                temperature = 0.0
            )
            if (outcome.text != null) {
                return@withContext GeminiConnectionTestResult(
                    isSuccess = true,
                    modelUsed = model,
                    messageAr = "المفتاح يعمل بنجاح ✅ (النموذج: $model)",
                    httpCode = 200
                )
            }
            lastCode = outcome.httpCode
            lastErr = outcome.error?.message ?: "HTTP $lastCode"
            val shouldFallback = (lastCode == 404 || lastCode == 429) && i < modelsToTry.lastIndex
            if (!shouldFallback) break
        }

        GeminiConnectionTestResult(
            isSuccess = false,
            modelUsed = modelsToTry.first(),
            messageAr = "فشل الاتصال ❌ ($lastErr)",
            httpCode = lastCode
        )
    }

    private data class SingleAttemptOutcome(
        val text: String? = null,
        val httpCode: Int = 0,
        val error: Throwable? = null
    )

    private fun executeSingleModelRequest(
        model: String,
        cleanKey: String,
        prompt: String,
        systemInstruction: String = "",
        imageBytesBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        audioBytesBase64: String? = null,
        audioMimeType: String = "audio/mp3",
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        temperature: Double = 0.3,
        responseMimeType: String? = null,
        enableGoogleSearch: Boolean = false,
        enableGoogleMaps: Boolean = false
    ): SingleAttemptOutcome {
        return try {
            val trimmedBase = baseUrl.trimEnd('/')
            val url = "$trimmedBase/models/$model:generateContent?key=$cleanKey"

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()
                chatHistory.forEach { (isUser, msgText) ->
                    if (msgText.isNotBlank()) {
                        contentsArray.put(JSONObject().apply {
                            put("role", if (isUser) "user" else "model")
                            put("parts", JSONArray().put(JSONObject().put("text", msgText)))
                        })
                    }
                }
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        val effectivePrompt = prompt.ifBlank {
                            when {
                                !imageBytesBase64.isNullOrEmpty() -> "اشرح وحلل هذه الصورة التعليمية بدقة."
                                !audioBytesBase64.isNullOrEmpty() -> "فرغ هذا التسجيل الصوتي ولخصه بدقة."
                                else -> "اشرح هذا الموضوع بوضوح."
                            }
                        }
                        put(JSONObject().put("text", effectivePrompt))
                        if (!imageBytesBase64.isNullOrEmpty()) {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", imageMimeType)
                                    put("data", imageBytesBase64)
                                })
                            })
                        }
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
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                    })
                }

                val toolsArray = JSONArray()
                if (enableGoogleSearch) toolsArray.put(JSONObject().put("googleSearch", JSONObject()))
                if (enableGoogleMaps) toolsArray.put(JSONObject().put("googleMaps", JSONObject()))
                if (toolsArray.length() > 0) put("tools", toolsArray)

                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    if (!responseMimeType.isNullOrBlank()) {
                        put("responseMimeType", responseMimeType)
                    }
                })
            }

            val req = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = parseApiErrorMessage(resp.code, rawBody)
                    return SingleAttemptOutcome(
                        text = null,
                        httpCode = resp.code,
                        error = IllegalStateException(msg)
                    )
                }

                val extracted = extractResponseText(rawBody)
                if (extracted.isNotBlank()) {
                    SingleAttemptOutcome(text = extracted, httpCode = resp.code)
                } else {
                    SingleAttemptOutcome(
                        text = null,
                        httpCode = resp.code,
                        error = IllegalStateException("استجابة فارغة من النموذج $model")
                    )
                }
            }
        } catch (t: Throwable) {
            SingleAttemptOutcome(text = null, httpCode = 0, error = t)
        }
    }

    internal fun buildModelAttemptList(preferredModel: String): List<String> {
        val cleaned = preferredModel.trim().ifBlank { FahimConfig.PRIMARY_MODEL }
        val normalized = when {
            cleaned.equals("gemini-3.5-flash", ignoreCase = true) -> FahimConfig.PRIMARY_MODEL
            else -> cleaned
        }
        return if (normalized == FahimConfig.FALLBACK_MODEL) {
            listOf(FahimConfig.FALLBACK_MODEL)
        } else {
            listOf(normalized, FahimConfig.FALLBACK_MODEL).distinct()
        }
    }

    private fun extractResponseText(rawBody: String): String {
        val jsonResponse = JSONObject(rawBody)
        val candidates = jsonResponse.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val firstCandidate = candidates.getJSONObject(0)
        val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val t = part.optString("text", "")
                if (t.isNotBlank()) sb.append(t)
                val inlineData = part.optJSONObject("inlineData")
                val b64 = inlineData?.optString("data").orEmpty()
                if (b64.isNotBlank()) {
                    sb.append("\n\n[IMAGE_DATA:$b64]")
                }
            }
        }
        return sb.toString().trim()
    }

    private fun parseApiErrorMessage(code: Int, rawBody: String): String {
        val apiMsg = runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            400 -> "HTTP 400: ${apiMsg.ifBlank { "طلب غير صالح" }}"
            401, 403 -> "HTTP $code: المفتاح غير مصرح به أو غير صحيح"
            404 -> "HTTP 404: النموذج غير متوفر (${apiMsg.ifBlank { "fallback" }})"
            429 -> "HTTP 429: تم تجاوز حد الطلبات المؤقت"
            else -> "HTTP $code: ${apiMsg.ifBlank { rawBody.take(140) }}"
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        fun stripMarkdownJsonFences(raw: String): String {
            var cleaned = raw.trim()
            if (cleaned.startsWith("```json", ignoreCase = true)) {
                cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").trim()
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```").trim()
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
            return cleaned
        }
    }
}
