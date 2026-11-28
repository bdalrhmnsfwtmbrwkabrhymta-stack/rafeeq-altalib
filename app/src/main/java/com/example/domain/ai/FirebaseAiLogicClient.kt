package com.example.domain.ai

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseAiLogicClient"

/**
 * عميل الذكاء الاصطناعي المباشر عبر Firebase AI Logic (`Firebase.ai`) بنموذج `gemini-3.8-flash`.
 * يعمل فوراً دون الحاجة لأي مفتاح API يدوي، مع App Check ومهلة 60 ثانية ومعالجة أخطاء شفافة.
 */
object FirebaseAiLogicClient {

    const val DEFAULT_MODEL = "gemini-3.8-flash"

    fun isAvailable(): Boolean = runCatching {
        FirebaseApp.getInstance() != null
    }.getOrDefault(false)

    suspend fun generateText(
        prompt: String,
        systemInstruction: String = "",
        imageBytesBase64: String? = null,
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        temperature: Float = 0.35f,
        responseMimeType: String? = null,
        modelName: String = DEFAULT_MODEL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("FirebaseApp is not initialized"))
        }

        try {
            val config = generationConfig {
                this.temperature = temperature
                if (!responseMimeType.isNullOrBlank()) {
                    this.responseMimeType = responseMimeType
                }
            }

            val sysContent = systemInstruction.takeIf { it.isNotBlank() }?.let {
                content { text(it) }
            }

            val model = Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config,
                systemInstruction = sysContent
            )

            val bitmap = imageBytesBase64
                ?.takeIf { it.isNotBlank() }
                ?.let { b64 ->
                    runCatching {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }

            val responseText = withTimeout(60_000L) {
                if (chatHistory.isNotEmpty() && bitmap == null && responseMimeType.isNullOrBlank()) {
                    val mappedHistory = chatHistory
                        .filter { it.second.isNotBlank() }
                        .map { (isUser, msg) ->
                            content(role = if (isUser) "user" else "model") { text(msg) }
                        }
                    val chat = model.startChat(history = mappedHistory)
                    chat.sendMessage(prompt).text
                } else {
                    val inputContent = content {
                        if (bitmap != null) {
                            image(bitmap)
                        }
                        text(prompt)
                    }
                    model.generateContent(inputContent).text
                }
            }

            if (!responseText.isNullOrBlank()) {
                Result.success(responseText.trim())
            } else {
                Result.failure(IllegalStateException("أعاد النموذج استجابة فارغة"))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out after 60s", e)
            Result.failure(IllegalStateException("انتهت مهلة الاتصال بالذكاء الاصطناعي (60 ثانية). حاول مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun generateTextStream(
        prompt: String,
        systemInstruction: String = "",
        temperature: Float = 0.35f,
        modelName: String = DEFAULT_MODEL
    ): Flow<String> = flow {
        if (!isAvailable()) {
            throw IllegalStateException("FirebaseApp is not initialized")
        }
        val config = generationConfig {
            this.temperature = temperature
        }
        val sysContent = systemInstruction.takeIf { it.isNotBlank() }?.let {
            content { text(it) }
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config,
            systemInstruction = sysContent
        )
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) emit(text)
            }
        }
    }
}
