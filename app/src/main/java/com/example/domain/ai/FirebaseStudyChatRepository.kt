package com.example.domain.ai

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository integrating Firebase AI SDK for the student chatbot.
 * Allows students to ask questions about their study plan and request academic explanations.
 */
class FirebaseStudyChatRepository(private val context: Context) {

    private val geminiAiRestFallback = GeminiAiRepository()

    /**
     * Initializes a GenerativeModel using Firebase AI SDK if available.
     */
    private fun getFirebaseGenerativeModel(modelName: String = "gemini-2.5-flash", systemPrompt: String): com.google.firebase.ai.GenerativeModel? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val config = generationConfig {
                temperature = 0.4f
                topP = 0.95f
            }
            Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config,
                systemInstruction = content {
                    text(systemPrompt)
                }
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sends a question to the Firebase AI SDK chatbot with contextual study plan / academic guidance.
     */
    suspend fun askStudentAssistant(
        userMessage: String,
        studyPlanContext: String = "",
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        customApiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            أنت "المعلم والمستشار الدراسي الذكي" للطالب.
            دورك الأساسي:
            1. الإجابة بدقة وسهولة على أسئلة الطالب حول خطته الدراسية ومهامه وإدارته للوقت.
            2. تقديم شروحات أكاديمية مبسطة وسريعة لأي مفهوم أو مادة دراسية يطلبها الطالب مع أمثلة عملية.
            3. تشجيع الطالب وتحفيزه، واقتراح فترات راحة بومودورو وتقنيات تكرار متباعد لحفظ المعلومات.
            
            ${if (studyPlanContext.isNotBlank()) "سياق الخطة والمهام الحالية للطالب:\n$studyPlanContext" else ""}
        """.trimIndent()

        // 1. Attempt to generate via Firebase AI SDK
        try {
            val generativeModel = getFirebaseGenerativeModel(modelName = "gemini-2.5-flash", systemPrompt = systemPrompt)
            if (generativeModel != null) {
                val fullPrompt = if (studyPlanContext.isNotBlank()) {
                    "سياق خطتي الدراسية:\n$studyPlanContext\n\nسؤالي هو:\n$userMessage"
                } else {
                    userMessage
                }
                val response = generativeModel.generateContent(fullPrompt)
                val responseText = response.text
                if (!responseText.isNullOrBlank()) {
                    return@withContext Result.success(responseText)
                }
            }
        } catch (e: Throwable) {
            // Log and fallback to direct REST API
            e.printStackTrace()
        }

        // 2. Fallback to Gemini REST API or smart response
        geminiAiRestFallback.generateAiResponse(
            prompt = if (studyPlanContext.isNotBlank()) "سياق خطتي الدراسية:\n$studyPlanContext\n\nسؤالي هو:\n$userMessage" else userMessage,
            systemInstruction = systemPrompt,
            customApiKey = customApiKey,
            chatHistory = chatHistory
        )
    }

    /**
     * Quick explanation for academic concepts.
     */
    suspend fun getQuickAcademicExplanation(
        conceptOrTopic: String,
        customApiKey: String = ""
    ): Result<String> {
        val prompt = "اشرح المفهوم الأكاديمي التالي باختصار ووضوح مع مثال توضيحي عملي:\n$conceptOrTopic"
        return askStudentAssistant(userMessage = prompt, customApiKey = customApiKey)
    }

    /**
     * Study plan analysis and recommendation.
     */
    suspend fun analyzeStudyPlanAndAdvise(
        studyPlanDetails: String,
        customApiKey: String = ""
    ): Result<String> {
        val prompt = "بناءً على خطتي الدراسية ومهامي الحالية، ما هي أفضل خطة عمل وتوزيع زمني لجلسات المذاكرة اليوم؟"
        return askStudentAssistant(
            userMessage = prompt,
            studyPlanContext = studyPlanDetails,
            customApiKey = customApiKey
        )
    }
}
