package com.example.domain.ai

import com.example.BuildConfig

/**
 * إعدادات مفاتيح الـ API المركزية المخفية تماماً عن المستخدمين.
 * تتكامل مع FahimConfig ولوحة المطور السرية (PIN: 2026) وبيئة BuildConfig.
 */
object FahimAiConfig {
    // 1. خانة مفتاح Google AI Studio (Gemini API Key) للشرح والسبورة والخرائط الذهنية والجداول:
    const val AI_STUDIO_GEMINI_API_KEY: String = ""
    const val GEMINI_MODEL_NAME: String = "gemini-2.5-flash"

    // 2. خانة مفتاح الـ API الخاص بالصوت (Voice / TTS API Key):
    const val VOICE_TTS_API_KEY: String = ""
    const val VOICE_TTS_ENDPOINT: String = ""

    // 3. خانة مفتاح الـ API الخاص بالرسم وتوليد الصور (Image Generation API Key):
    const val IMAGE_GEN_API_KEY: String = ""
    const val DEFAULT_IMAGE_SIZE: String = "1024x1024"

    // إعدادات DeepSeek الاختيارية الاحتياطية:
    const val DEEPSEEK_API_KEY: String = ""
    const val DEEPSEEK_BASE_URL: String = "https://api.deepseek.com/v1/chat/completions"
    const val DEEPSEEK_MODEL: String = "deepseek-chat"

    fun resolveEffectiveDeepSeekKey(): String {
        val buildConfigKey = runCatching {
            val field = BuildConfig::class.java.getField("DEEPSEEK_API_KEY")
            field.get(null) as? String
        }.getOrNull()?.trim().orEmpty()

        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_DEEPSEEK_API_KEY") {
            return buildConfigKey
        }

        val embedded = DEEPSEEK_API_KEY.trim()
        if (embedded.isNotBlank() && !embedded.startsWith("ضع_مفتاح")) {
            return embedded
        }
        return ""
    }

    fun isDeepSeekConfigured(): Boolean = resolveEffectiveDeepSeekKey().isNotBlank()
}
