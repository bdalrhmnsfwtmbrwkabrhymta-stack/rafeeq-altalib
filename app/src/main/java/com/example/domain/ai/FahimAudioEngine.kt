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

enum class FahimVoiceGender(val labelAr: String, val pitch: Float) {
    MALE("معلم", 0.94f),
    FEMALE("معلمة", 1.15f);

    companion object {
        fun fromName(name: String): FahimVoiceGender =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MALE
    }
}

/**
 * محرك الصوت الثنائي للسبورة الذكية (FahimAudioEngine):
 * - يقتصر حصراً على لهجتين: «مصري» (ar-EG) و«فصحى» (ar).
 * - يدعم اختيار صوت «معلم» / «معلمة» والتحكم في سرعة النطق (0.75x - 1.5x).
 * - يدعم نقاط نهاية Piper TTS / Coqui TTS مع بديل فوري عبر Android TextToSpeech.
 * - يزامن رقم الخطوة النشطة (activeStepIndex) وموجة الصوت الحية (isSpeaking) مع الرسم الحي.
 */
open class FahimAudioEngine(private val context: Context) {

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

    private val _voiceGender = MutableStateFlow(FahimVoiceGender.MALE)
    val voiceGender: StateFlow<FahimVoiceGender> = _voiceGender.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    init {
        initAndroidTts()
    }

    fun setVoiceGender(gender: FahimVoiceGender) {
        _voiceGender.value = gender
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate.coerceIn(0.75f, 1.5f)
    }

    fun cycleSpeechRate(): Float {
        val next = when {
            _speechRate.value < 0.9f -> 1.0f
            _speechRate.value < 1.15f -> 1.25f
            _speechRate.value < 1.4f -> 1.5f
            else -> 0.75f
        }
        _speechRate.value = next
        return next
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

                        override fun onDone(utteranceId: String?) = Unit

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
        voiceApiKey: String = "",
        gender: FahimVoiceGender = _voiceGender.value,
        speed: Float = _speechRate.value
    ) {
        stop()
        if (steps.isEmpty()) return

        _voiceGender.value = gender
        _speechRate.value = speed.coerceIn(0.75f, 1.5f)

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

                // 1. Try optional Piper or Coqui open-source TTS server if configured
                val endpoint = effectivePiper.ifBlank { effectiveCoqui }
                var playedRemote = false
                if (endpoint.isNotBlank()) {
                    _activeEngineLabel.value = if (effectivePiper.isNotBlank()) "Piper TTS" else "Coqui TTS"
                    playedRemote = playRemoteTtsAndAwait(
                        endpoint = endpoint,
                        text = spoken,
                        dialect = dialect,
                        gender = gender,
                        speed = _speechRate.value,
                        apiKey = voiceApiKey.ifBlank { FahimAiConfig.VOICE_TTS_API_KEY }
                    )
                }

                // 2. Instant fallback to Built-in Android TextToSpeech (مصري ar-EG / فصحى ar)
                if (!playedRemote) {
                    _activeEngineLabel.value = "Android TTS (${dialect.labelAr} • ${gender.labelAr})"
                    speakViaAndroidTtsAndAwait(spoken, dialect, gender, _speechRate.value, idx)
                }

                delay((350L / _speechRate.value).toLong().coerceIn(180L, 500L))
            }
            _isSpeaking.value = false
        }
    }

    private suspend fun playRemoteTtsAndAwait(
        endpoint: String,
        text: String,
        dialect: FahimDialect,
        gender: FahimVoiceGender,
        speed: Float,
        apiKey: String
    ): Boolean {
        val tempFile = withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("text", text)
                    put("language", dialect.bcp47Tag)
                    put("dialect", dialect.name.lowercase())
                    put("gender", gender.name.lowercase())
                    put("speed", speed)
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
        gender: FahimVoiceGender,
        speed: Float,
        stepIndex: Int
    ) {
        if (!isTtsReady || tts == null) {
            delay((2100L / speed).toLong().coerceIn(900L, 3000L))
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
        tts?.setPitch(gender.pitch)
        tts?.setSpeechRate(speed)

        val params = Bundle()
        tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, "step_$stepIndex")

        val baseMinWait = (spoken.length * 46L).coerceIn(1300L, 4200L)
        val scaledWait = (baseMinWait / speed).toLong().coerceIn(900L, 5000L)
        delay(400L)
        var elapsed = 400L
        while (elapsed < scaledWait && _isSpeaking.value) {
            delay(200L)
            elapsed += 200L
            if (elapsed > 1400L && tts?.isSpeaking == false) {
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

    internal fun cleanMathForArabicSpeech(raw: String): String {
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
