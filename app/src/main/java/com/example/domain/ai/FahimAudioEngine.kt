package com.example.domain.ai

import android.content.Context
import android.media.AudioAttributes
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class FahimVoiceGender(val labelAr: String, val pitch: Float) {
    MALE("معلم", 0.94f),
    FEMALE("معلمة", 1.15f);

    companion object {
        fun fromName(name: String): FahimVoiceGender =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MALE
    }
}

/**
 * محرك الصوت العصبي الثنائي للسبورة الذكية (FahimAudioEngine):
 * - يقتصر حصراً على لهجتين: «مصري» (ar-EG) و«فصحى» (ar).
 * - يستخدم أصواتاً عصبية طبيعية حقيقية مفتوحة المصدر (Edge-TTS Neural: ShakirNeural / SalmaNeural للمصري
 *   و HamedNeural / ZariyahNeural للفصحى) بالإضافة لدعم Piper / Coqui TTS وبديل Android TTS عند انقطاع الإنترنت.
 * - يزامن رقم الخطوة النشطة (activeStepIndex) وموجة الصوت الحية (isSpeaking) مع الكتابة والرسم الحي على السبورة.
 */
open class FahimAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val neuralTtsClient by lazy { EdgeNeuralTtsClient(context.applicationContext, httpClient) }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(-1)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _activeEngineLabel = MutableStateFlow("صوت عصبي طبيعي (مصري)")
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
                val rawSpeech = step.speechLine.ifBlank { step.displayArabicTitle() }
                val spoken = cleanMathForArabicSpeech(rawSpeech)
                if (spoken.isBlank()) {
                    delay(1000L)
                    continue
                }

                // 1. Optional Custom Piper / Coqui TTS Server (from Developer Panel PIN 2026)
                val endpoint = effectivePiper.ifBlank { effectiveCoqui }
                var played = false
                if (endpoint.isNotBlank()) {
                    _activeEngineLabel.value = if (effectivePiper.isNotBlank()) "Piper TTS" else "Coqui TTS"
                    played = playRemoteTtsAndAwait(
                        endpoint = endpoint,
                        text = spoken,
                        dialect = dialect,
                        gender = gender,
                        speed = _speechRate.value,
                        apiKey = voiceApiKey.ifBlank { FahimAiConfig.VOICE_TTS_API_KEY }
                    )
                }

                // 2. Primary Built-In Open-Source Neural Arabic Voices (ar-EG-ShakirNeural / SalmaNeural / ar-SA-HamedNeural / ZariyahNeural)
                if (!played) {
                    val neuralResult = neuralTtsClient.synthesizeToMp3File(
                        text = spoken,
                        dialect = dialect,
                        gender = gender,
                        speed = _speechRate.value
                    )
                    if (neuralResult != null) {
                        val (audioFile, label) = neuralResult
                        _activeEngineLabel.value = label
                        played = playAudioFileAndAwait(audioFile)
                    }
                }

                // 3. Offline Fallback: Best available Android TextToSpeech Arabic voice
                if (!played) {
                    _activeEngineLabel.value = "صوت الجهاز (${dialect.labelAr} • ${gender.labelAr})"
                    speakViaAndroidTtsAndAwait(spoken, dialect, gender, _speechRate.value, idx)
                }

                delay((320L / _speechRate.value).toLong().coerceIn(180L, 480L))
            }
            _isSpeaking.value = false
        }
    }

    fun speakSingleText(
        text: String,
        dialect: FahimDialect = FahimDialect.EGYPTIAN,
        gender: FahimVoiceGender = _voiceGender.value,
        speed: Float = _speechRate.value
    ) {
        val clean = cleanMathForArabicSpeech(text)
        if (clean.isBlank()) return
        speakLessonSteps(
            steps = listOf(WhiteboardStep(index = 0, speechLine = clean)),
            dialect = dialect,
            gender = gender,
            speed = speed
        )
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

        val ok = playAudioFileAndAwait(tempFile)
        runCatching { tempFile.delete() }
        return ok
    }

    private suspend fun playAudioFileAndAwait(file: File): Boolean = suspendCancellableCoroutine { cont ->
        runCatching {
            releaseMediaPlayer()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    releaseMediaPlayer()
                    if (cont.isActive) cont.resume(true)
                }
                setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    if (cont.isActive) cont.resume(false)
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp
            cont.invokeOnCancellation { releaseMediaPlayer() }
        }.onFailure {
            releaseMediaPlayer()
            if (cont.isActive) cont.resume(false)
        }
    }

    private suspend fun speakViaAndroidTtsAndAwait(
        text: String,
        dialect: FahimDialect,
        gender: FahimVoiceGender,
        speed: Float,
        stepIdx: Int
    ) {
        val engine = tts
        if (engine == null || !isTtsReady) {
            delay(((text.length * 60L) / speed).toLong().coerceIn(1400L, 4200L))
            return
        }

        val targetLocale = if (dialect.localeCountry.isNotBlank()) {
            Locale(dialect.localeLang, dialect.localeCountry)
        } else {
            Locale(dialect.localeLang)
        }

        runCatching {
            val voices = engine.voices.orEmpty()
            val matchingVoice = voices
                .filter { v ->
                    v.locale.language.equals("ar", ignoreCase = true) &&
                        (dialect != FahimDialect.EGYPTIAN || v.locale.country.equals("EG", ignoreCase = true) || true)
                }
                .sortedByDescending { v ->
                    var score = 0
                    if (dialect == FahimDialect.EGYPTIAN && v.locale.country.equals("EG", ignoreCase = true)) score += 10
                    if (!v.isNetworkConnectionRequired) score += 2
                    if (v.quality >= 400) score += 4
                    score
                }
                .firstOrNull()
            if (matchingVoice != null) {
                engine.voice = matchingVoice
            } else {
                val langRes = engine.setLanguage(targetLocale)
                if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale("ar"))
                }
            }
            engine.setSpeechRate(speed.coerceIn(0.75f, 1.5f))
            engine.setPitch(gender.pitch)
        }

        val utteranceId = "fahim_step_${stepIdx}_${System.currentTimeMillis()}"
        suspendCancellableCoroutine<Unit> { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }
            })
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
            val result = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId) }
                .getOrDefault(TextToSpeech.ERROR)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(Unit)
            }
            cont.invokeOnCancellation { runCatching { engine.stop() } }
        }
    }

    fun cleanMathForArabicSpeech(raw: String): String {
        return raw
            .replace("Area_shaded", "المساحة المظللة")
            .replace("Area_rect", "مساحة المستطيل")
            .replace("Area_circle", "مساحة الدائرة")
            .replace("R_eq", "المقاومة المكافئة")
            .replace("π", " باي ")
            .replace("×", " ضرب ")
            .replace("÷", " على ")
            .replace("=", " يساوي ")
            .replace("≈", " يساوي تقريباً ")
            .replace("√", " الجذر التربيعي لـ ")
            .replace("²", " تربيع ")
            .replace("Ω", " أوم ")
            .replace("→", " يؤدي إلى ")
            .replace("←", " إذن ")
            .replace("|", " ، ")
            .replace(Regex("[*_`#]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        releaseMediaPlayer()
        runCatching { tts?.stop() }
        _isSpeaking.value = false
        _activeStepIndex.value = -1
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
