package com.example.domain.prayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Data model for authentic "Salli ala Muhammad" (الصلاة على النبي ﷺ) reciter voices and formulas.
 */
data class SalawatVoiceItem(
    val id: Int,
    val title: String,
    val sheikhName: String,
    val subtitle: String,
    val fullText: String,
    val rawResId: Int = 0,
    val audioUrl: String? = null,
    val isBuiltIn: Boolean = true,
    val sizeText: String = ""
)

object SalawatAudioData {

    private const val TAG = "SalawatAudioData"
    private var activeTts: TextToSpeech? = null

    // أصوات مدمجة ثابتة مع التطبيق بدون إنترنت (أصوات تطبيقات متجر جوجل بلاي الشهيرة)
    val builtInVoices: List<SalawatVoiceItem> = listOf(
        SalawatVoiceItem(
            id = 1,
            title = "صَلِّ عَلَى مُحَمَّد ﷺ (النداء الكلاسيكي)",
            sheikhName = "صوت نداء المتجر الكلاسيكي",
            subtitle = "النداء الشهير في تطبيقات التذكير: «صَلِّ عَلَى مُحَمَّد ﷺ»",
            fullText = "صَلِّ عَلَى مُحَمَّد",
            rawResId = R.raw.salli_ala_muhammad,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        ),
        SalawatVoiceItem(
            id = 2,
            title = "صَلِّ عَلَى النَّبِي ﷺ (الصوت العذب المشهور)",
            sheikhName = "صوت نداء متجر بلاي الشهير",
            subtitle = "نغمة التذكير النبوية الأكثر انتشاراً في المتجر: «صَلِّ عَلَى النَّبِي ﷺ»",
            fullText = "صَلِّ عَلَى النَّبِي",
            rawResId = R.raw.salawat_reminder,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        ),
        SalawatVoiceItem(
            id = 3,
            title = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد (صوت نقي واضح)",
            sheikhName = "نغمة تذكير نبوية",
            subtitle = "نداء التذكير العذب: «اللَّهُمَّ صَلِّ عَلَى مُحَمَّد»",
            fullText = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد",
            rawResId = R.raw.salawat_voice_3,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        ),
        SalawatVoiceItem(
            id = 4,
            title = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد (صوت هادئ وخاشع)",
            sheikhName = "صوت هادئ ومؤثر",
            subtitle = "تذكير وقور وخشوع: «اللَّهُمَّ صَلِّ عَلَى مُحَمَّد»",
            fullText = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد",
            rawResId = R.raw.salawat_voice_4,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        ),
        SalawatVoiceItem(
            id = 5,
            title = "صَلُّوا عَلَى رَسُولِ اللَّه ﷺ",
            sheikhName = "نداء التذكير النبوي",
            subtitle = "نداء تنبيه مبارك: «صَلُّوا عَلَى رَسُولِ اللَّه ﷺ»",
            fullText = "صَلُّوا عَلَى رَسُولِ اللَّه",
            rawResId = R.raw.salawat_voice_5,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        ),
        SalawatVoiceItem(
            id = 6,
            title = "صَلَّى اللَّهُ عَلَى مُحَمَّد ﷺ",
            sheikhName = "صيغة مباركة خفيفة",
            subtitle = "صيغة خفيفة وسريعة: «صَلَّى اللَّهُ عَلَى مُحَمَّد ﷺ»",
            fullText = "صَلَّى اللَّهُ عَلَى مُحَمَّد",
            rawResId = R.raw.salawat_voice_6,
            isBuiltIn = true,
            sizeText = "مدمج (بدون نت)"
        )
    )

    // أصوات إضافية من تطبيقات المتجر يمكن تنزيلها سحابياً لتوفير المساحة
    val onlineDownloadableVoices: List<SalawatVoiceItem> = listOf(
        SalawatVoiceItem(
            id = 101,
            title = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى سَيِّدِنَا مُحَمَّد",
            sheikhName = "الصيغة الكاملة الشجية",
            subtitle = "الصيغة الشاملة بالصوت الشجي الممتد من تطبيقات المتجر",
            fullText = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى سَيِّدِنَا مُحَمَّد",
            rawResId = 0,
            audioUrl = "https://archive.org/download/6_20260613/6.mp3",
            isBuiltIn = false,
            sizeText = "133 KB"
        ),
        SalawatVoiceItem(
            id = 102,
            title = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد (الشيخ سمير مصطفى)",
            sheikhName = "الشيخ سمير مصطفى",
            subtitle = "«اللَّهُمَّ صَلِّ عَلَى مُحَمَّد عَدَدَ مَا ذَكَرَهُ الذَّاكِرُون وَغَفَلَ عَنْ ذِكْرِهِ الْغَافِلُون»",
            fullText = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّد عَدَدَ مَا ذَكَرَهُ الذَّاكِرُون وَغَفَلَ عَنْ ذِكْرِهِ الْغَافِلُون",
            rawResId = 0,
            audioUrl = "https://archive.org/download/mp-3cut.net_202512/%D8%A7%D9%84%D9%84%D9%87%D9%85%20%D8%B5%D9%84%20%D8%B9%D9%84%D9%89%20%D9%85%D8%AD%D9%85%D8%AF%20%D8%B9%D8%AF%D8%AF%20%D9%85%D8%A7%20%D8%B0%D9%83%D8%B1%D9%87%20%D8%A7%D9%84%D8%B0%D8%A7%D9%83%D8%B1%D9%88%D9%86%20%D9%88%D8%BA%D9%81%D9%84%20%D8%B9%D9%86%20%D8%B0%D9%83%D8%B1%D9%87%20%D8%A7%D9%84%D8%BA%D8%A7%D9%81%D9%84%D9%88%D9%86%20-%20%D8%A8%D8%B5%D9%88%D8%AA%20%D8%A7%D9%84%D8%B4%D9%8A%D8%AE%20%D8%B3%D9%85%D9%8A%D8%B1%20%D9%85%D8%B5%D8%B7%D9%81%D9%89%20%28mp3cut.net%29.mp3",
            isBuiltIn = false,
            sizeText = "85 KB"
        ),
        SalawatVoiceItem(
            id = 103,
            title = "صَلِّ عَلَى مُحَمَّد (تنبيه متجر بلاي السريع)",
            sheikhName = "نغمة تنبيه المتجر",
            subtitle = "نغمة تنبيه صوتية قصيرة جداً ومباشرة (2 ثانية)",
            fullText = "صَلِّ عَلَى مُحَمَّد",
            rawResId = 0,
            audioUrl = "https://archive.org/download/6_20260613/1.mp3",
            isBuiltIn = false,
            sizeText = "44 KB"
        ),
        SalawatVoiceItem(
            id = 104,
            title = "صَلِّ عَلَى النَّبِي (تنبيه متجر بلاي الخفيف)",
            sheikhName = "نغمة تنبيه المتجر",
            subtitle = "نغمة تنبيه نبوية خفيفة وسريعة (2 ثانية)",
            fullText = "صَلِّ عَلَى النَّبِي",
            rawResId = 0,
            audioUrl = "https://archive.org/download/6_20260613/2.mp3",
            isBuiltIn = false,
            sizeText = "37 KB"
        )
    )

    val allSalawatVoices: List<SalawatVoiceItem> = builtInVoices + onlineDownloadableVoices

    fun getVoiceById(id: Int): SalawatVoiceItem {
        return allSalawatVoices.firstOrNull { it.id == id } ?: builtInVoices.first()
    }

    /**
     * File handling for downloaded voices
     */
    fun getDownloadedVoiceFile(context: Context, voiceId: Int): java.io.File {
        val dir = java.io.File(context.filesDir, "salawat_voices")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, "salawat_voice_$voiceId.mp3")
    }

    fun isVoiceDownloaded(context: Context, voiceId: Int): Boolean {
        val voice = getVoiceById(voiceId)
        if (voice.isBuiltIn) return true
        val file = getDownloadedVoiceFile(context, voiceId)
        return file.exists() && file.length() > 1024
    }

    fun deleteDownloadedVoice(context: Context, voiceId: Int): Boolean {
        val file = getDownloadedVoiceFile(context, voiceId)
        return if (file.exists()) file.delete() else false
    }

    suspend fun downloadVoice(
        context: Context,
        voiceId: Int,
        onProgress: (Int) -> Unit = {}
    ): Result<java.io.File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val voice = getVoiceById(voiceId)
        val urlStr = voice.audioUrl ?: return@withContext Result.failure(Exception("لا يوجد رابط تحميل لهذا الصوت"))
        val targetFile = getDownloadedVoiceFile(context, voiceId)
        val parentDir = targetFile.parentFile ?: context.filesDir
        if (!parentDir.exists()) parentDir.mkdirs()
        val tempFile = java.io.File(parentDir, "${targetFile.name}.tmp")

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل الخادم: رمز ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("استجابة الخادم فارغة"))
            val totalLength = body.contentLength()

            body.byteStream().use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val percent = ((totalRead * 100) / totalLength).toInt()
                            onProgress(percent.coerceIn(0, 100))
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 1024) {
                if (targetFile.exists()) targetFile.delete()
                if (tempFile.renameTo(targetFile)) {
                    Result.success(targetFile)
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    Result.success(targetFile)
                }
            } else {
                if (tempFile.exists()) tempFile.delete()
                Result.failure(Exception("الملف المنزّل غير مكتمل أو تالف"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for voice $voiceId", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Stop any running TTS instance
     */
    fun stopTts() {
        try {
            activeTts?.stop()
            activeTts?.shutdown()
            activeTts = null
        } catch (_: Exception) {}
    }

    /**
     * Safely stops, resets and releases a MediaPlayer instance avoiding native state errors.
     * Prevents calling stop() when player is preparing, prepared (state 4), or completed.
     */
    fun stopPlayer(player: MediaPlayer?) {
        if (player == null) return
        try {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
        } catch (_: Exception) {}
        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: Exception) {}
        try {
            player.reset()
        } catch (_: Exception) {}
        try {
            player.release()
        } catch (_: Exception) {}
    }

    /**
     * Speaks the formula aloud using Android TextToSpeech in natural Arabic voice.
     */
    fun speakFormulaTts(
        context: Context,
        voiceId: Int,
        onCompletion: (() -> Unit)? = null
    ) {
        stopTts()
        val voice = getVoiceById(voiceId)
        val appContext = context.applicationContext

        try {
            activeTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = activeTts
                    if (tts != null) {
                        val arabicLocale = Locale("ar")
                        val langResult = tts.setLanguage(arabicLocale)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.setLanguage(Locale.getDefault())
                        }
                        tts.setPitch(1.0f)
                        tts.setSpeechRate(0.92f)

                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                onCompletion?.invoke()
                            }
                            override fun onError(utteranceId: String?) {
                                onCompletion?.invoke()
                            }
                        })

                        val params = android.os.Bundle().apply {
                            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                        }
                        tts.speak(voice.fullText, TextToSpeech.QUEUE_FLUSH, params, "salawat_${voice.id}")
                    } else {
                        onCompletion?.invoke()
                    }
                } else {
                    // Fallback to real recorded audio if TTS is unavailable
                    playVoiceAudio(context, voiceId, onCompletion)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS for Salawat", e)
            playVoiceAudio(context, voiceId, onCompletion)
        }
    }

    /**
     * Plays the specified Salawat voice item using high quality media player with guaranteed single playback.
     */
    fun playVoiceAudio(
        context: Context,
        voiceId: Int,
        onCompletion: (() -> Unit)? = null
    ): MediaPlayer? {
        val voice = getVoiceById(voiceId)
        val appContext = context.applicationContext

        return try {
            if (voice.isBuiltIn) {
                val rawId = if (voice.rawResId != 0) voice.rawResId else R.raw.salli_ala_muhammad
                val mp = MediaPlayer.create(appContext, rawId)
                if (mp != null) {
                    mp.isLooping = false
                    mp.setOnCompletionListener { player ->
                        stopPlayer(player)
                        onCompletion?.invoke()
                    }
                    mp.setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "Salawat audio error: $what, $extra")
                        stopPlayer(player)
                        onCompletion?.invoke()
                        true
                    }
                    mp.start()
                    mp
                } else {
                    onCompletion?.invoke()
                    null
                }
            } else {
                val downloadedFile = getDownloadedVoiceFile(appContext, voiceId)
                if (downloadedFile.exists() && downloadedFile.length() > 1024) {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(downloadedFile.absolutePath)
                        setOnPreparedListener { player ->
                            try {
                                player.start()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting downloaded audio", e)
                                stopPlayer(player)
                                onCompletion?.invoke()
                            }
                        }
                        setOnCompletionListener { player ->
                            stopPlayer(player)
                            onCompletion?.invoke()
                        }
                        setOnErrorListener { player, what, extra ->
                            Log.e(TAG, "Salawat downloaded audio error: $what, $extra")
                            stopPlayer(player)
                            onCompletion?.invoke()
                            true
                        }
                        prepareAsync()
                    }
                    mp
                } else if (!voice.audioUrl.isNullOrBlank()) {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                        setDataSource(appContext, Uri.parse(voice.audioUrl), headers)
                        setOnPreparedListener { player ->
                            try {
                                player.start()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting streaming audio", e)
                                stopPlayer(player)
                                onCompletion?.invoke()
                            }
                        }
                        setOnCompletionListener { player ->
                            stopPlayer(player)
                            onCompletion?.invoke()
                        }
                        setOnErrorListener { player, what, extra ->
                            Log.e(TAG, "Salawat stream error: $what, $extra")
                            stopPlayer(player)
                            onCompletion?.invoke()
                            true
                        }
                        prepareAsync()
                    }
                    mp
                } else {
                    val mp = MediaPlayer.create(appContext, R.raw.salli_ala_muhammad)
                    mp?.setOnCompletionListener { player ->
                        stopPlayer(player)
                        onCompletion?.invoke()
                    }
                    mp?.setOnErrorListener { player, _, _ ->
                        stopPlayer(player)
                        onCompletion?.invoke()
                        true
                    }
                    mp?.start()
                    mp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing Salawat voice audio", e)
            onCompletion?.invoke()
            null
        }
    }

    /**
     * Plays a custom audio URI selected by the user.
     */
    fun playCustomAudioUri(
        context: Context,
        uriString: String,
        onCompletion: (() -> Unit)? = null
    ): MediaPlayer? {
        if (uriString.isBlank()) return null
        val appContext = context.applicationContext

        return try {
            val uri = Uri.parse(uriString)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, uri)
                setOnPreparedListener { player ->
                    try {
                        player.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting prepared custom audio", e)
                        stopPlayer(player)
                        onCompletion?.invoke()
                    }
                }
                setOnCompletionListener { player ->
                    stopPlayer(player)
                    onCompletion?.invoke()
                }
                setOnErrorListener { player, what, extra ->
                    Log.e(TAG, "Custom Salawat audio error: $what, $extra")
                    stopPlayer(player)
                    onCompletion?.invoke()
                    true
                }
                prepareAsync()
            }
            mp
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom Salawat audio URI", e)
            onCompletion?.invoke()
            null
        }
    }
}
