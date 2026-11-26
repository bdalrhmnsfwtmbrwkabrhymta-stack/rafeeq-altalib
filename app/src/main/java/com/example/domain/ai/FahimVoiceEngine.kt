package com.example.domain.ai

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * محرك النطق الطبيعي للسبورة الذكية:
 * 1. يدعم نقاط نهاية Piper TTS / Coqui TTS المفتوحة المصدر عند ضبط رابطها من المطور (PIN: 2026).
 * 2. يعود تلقائياً وبسرعة إلى Android TextToSpeech المدمج (مصري ar-EG، سعودي ar-SA، فصحى ar) بدون أي أخطاء.
 * 3. يزامن رقم الخطوة النشطة (activeStepIndex) وموجة الصوت الحية (isSpeaking) مع الرسم الحي على السبورة.
 */
class FahimVoiceEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(-1)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _activeEngineLabel = MutableStateFlow("Android TTS")
    val activeEngineLabel: StateFlow<String> = _activeEngineLabel.asStateFlow()

    init {
        initAndroidTts()
    }

    private fun initAndroidTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            val idx = utteranceId?.removePrefix("step_")?.toIntOrNull()
                            if (idx != null) {
                                // Keep UI clean; step queue advances automatically
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
            }
        }
    }

    fun speakLessonSteps(
        steps: List<WhiteboardStep>,
        dialect: FahimDialect,
        piperUrl: String = "",
        coquiUrl: String = "",
        voiceApiKey: String = ""
    ) {
        stop()
        if (steps.isEmpty()) return

        val effectivePiper = FahimConfig.resolvePiperTtsUrl(piperUrl)
        val effectiveCoqui = FahimConfig.resolveCoquiTtsUrl(coquiUrl)

        playbackJob = scope.launch {
            _isSpeaking.value = true
            for ((idx, step) in steps.withIndex()) {
                _activeStepIndex.value = idx
                val spoken = cleanMathForArabicSpeech(step.speechLine)
                if (spoken.isBlank()) {
                    delay(1100L)
                    continue
                }

                // 1. Try Piper or Coqui Open-Source TTS Endpoint if configured by developer
                val endpoint = effectivePiper.ifBlank { effectiveCoqui }
                var playedRemote = false
                if (endpoint.isNotBlank()) {
                    _activeEngineLabel.value = if (effectivePiper.isNotBlank()) "Piper TTS" else "Coqui TTS"
                    playedRemote = playRemoteTtsAndAwait(
                        endpoint = endpoint,
                        text = spoken,
                        dialect = dialect,
                        apiKey = voiceApiKey.ifBlank { FahimAiConfig.VOICE_TTS_API_KEY }
                    )
                }

                // 2. Instant fallback to Built-in Android TextToSpeech with matching dialect locale
                if (!playedRemote) {
                    _activeEngineLabel.value = "Android TTS (${dialect.labelAr})"
                    speakViaAndroidTtsAndAwait(spoken, dialect, idx)
                }

                delay(350L)
            }
            _isSpeaking.value = false
        }
    }

    private suspend fun playRemoteTtsAndAwait(
        endpoint: String,
        text: String,
        dialect: FahimDialect,
        apiKey: String
    ): Boolean {
        val tempFile = withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("text", text)
                    put("language", dialect.localeLang)
                    put("dialect", dialect.name.lowercase())
                }
                val builder = Request.Builder()
                    .url(endpoint)
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (apiKey.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
                httpClient.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    if (bytes.size < 256) return@runCatching null
                    val f = File(context.cacheDir, "fahim_tts_${System.currentTimeMillis()}.wav")
                    f.writeBytes(bytes)
                    f
                }
            }.getOrNull()
        } ?: return false

        return withContext(Dispatchers.Main) {
            runCatching {
                val player = MediaPlayer()
                mediaPlayer = player
                var completed = false
                player.setDataSource(tempFile.absolutePath)
                player.setOnCompletionListener { completed = true }
                player.setOnErrorListener { _, _, _ -> completed = true; true }
                player.prepare()
                player.start()
                while (!completed && _isSpeaking.value) {
                    delay(120L)
                }
                player.release()
                if (mediaPlayer === player) mediaPlayer = null
                tempFile.delete()
                true
            }.getOrElse {
                tempFile.delete()
                false
            }
        }
    }

    private suspend fun speakViaAndroidTtsAndAwait(
        spoken: String,
        dialect: FahimDialect,
        stepIndex: Int
    ) {
        if (!isTtsReady || tts == null) {
            delay(2200L)
            return
        }

        val targetLocale = if (dialect.localeCountry.isNotBlank()) {
            Locale(dialect.localeLang, dialect.localeCountry)
        } else {
            Locale(dialect.localeLang)
        }
        val langStatus = tts?.setLanguage(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale("ar")
        }
        tts?.setPitch(1.03f)
        tts?.setSpeechRate(0.95f)

        val params = Bundle()
        tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, "step_$stepIndex")

        // Estimated natural reading wait + actual speaking poll so step sync works reliably even in headless devices
        val minWait = (spoken.length * 48L).coerceIn(1400L, 4200L)
        delay(450L)
        var elapsed = 450L
        while (elapsed < minWait && _isSpeaking.value) {
            delay(200L)
            elapsed += 200L
            if (elapsed > 1500L && tts?.isSpeaking == false) {
                break
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        runCatching { tts?.stop() }
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        isTtsReady = false
    }

    private fun cleanMathForArabicSpeech(raw: String): String {
        return raw
            .replace("Area_shaded", "المساحة المظللة")
            .replace("Area_rect", "مساحة المستطيل")
            .replace("Area_circle", "مساحة الدائرة")
            .replace("π", " ط ")
            .replace("×", " ضرب ")
            .replace("=", " يساوي ")
            .replace("≈", " تقريباً ")
            .replace("√", " جذر ")
            .trim()
    }
}
