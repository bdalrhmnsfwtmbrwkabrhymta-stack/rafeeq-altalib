package com.example.domain.ai

import com.example.BuildConfig

/**
 * ملف إعدادات فهيم المركزي للمطور (مخفي تماماً عن المستخدمين العاديين).
 * يتم حقن المفاتيح من الكود المصدري أو Secrets / BuildConfig أو من لوحة تحكم المطور السرية (PIN: 2026).
 */
object FahimConfig {
    // ضع مفتاح Gemini API هنا في الكود المصدري أو عبر لوحة المطور السرية (PIN: 2026):
    const val DEFAULT_GEMINI_API_KEY: String = ""
    const val PRIMARY_MODEL: String = "gemini-2.5-flash"
    const val FALLBACK_MODEL: String = "gemini-2.0-flash"
    const val GEMINI_MODEL: String = PRIMARY_MODEL

    // روابط اختيارية لنماذج الصوت مفتوحة المصدر من GitHub (Piper TTS & Coqui TTS):
    const val PIPER_TTS_URL: String = ""
    const val COQUI_TTS_URL: String = ""

    // الرمز السري لفتح لوحة تحكم المطور فقط:
    const val DEVELOPER_CONTROL_PIN: String = "2026"

    fun cleanApiKey(rawKey: String?): String =
        rawKey.orEmpty()
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace("\r", "")
            .replace("\n", "")
            .trim()

    fun resolveGeminiApiKey(savedDeveloperKey: String = ""): String {
        val saved = cleanApiKey(savedDeveloperKey)
        if (saved.isNotBlank() && saved != "MY_GEMINI_API_KEY") return saved

        val buildConfigKey = cleanApiKey(runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault(""))
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") return buildConfigKey

        val embedded = cleanApiKey(DEFAULT_GEMINI_API_KEY)
        if (embedded.isNotBlank() && embedded != "MY_GEMINI_API_KEY") return embedded

        val legacyAiStudio = cleanApiKey(FahimAiConfig.AI_STUDIO_GEMINI_API_KEY)
        if (legacyAiStudio.isNotBlank() && legacyAiStudio != "MY_GEMINI_API_KEY") return legacyAiStudio

        return ""
    }

    fun resolvePiperTtsUrl(savedUrl: String = ""): String =
        savedUrl.trim().ifBlank { PIPER_TTS_URL.trim().ifBlank { FahimAiConfig.VOICE_TTS_ENDPOINT.trim() } }

    fun resolveCoquiTtsUrl(savedUrl: String = ""): String =
        savedUrl.trim().ifBlank { COQUI_TTS_URL.trim() }
}
