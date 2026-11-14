package com.example.data.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.R

class AzanAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val appContext = context.applicationContext

    var onCompletionCallback: (() -> Unit)? = null
    var onStateChangedCallback: (() -> Unit)? = null

    var currentPlayingResId: Int? = null
        private set
    var isPaused: Boolean = false
        private set

    private val stopListener: () -> Unit = {
        stopAudio()
    }

    init {
        AppAudioManager.registerStopListener(stopListener)
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
        .build()

    fun playMakkahAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.azan_makkah_1)
    }

    fun playCairoAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.adhan_cairo)
    }

    fun playMoroccoAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.adhan_morocco)
    }

    fun playAbdulbasitAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.azan_ali_mulla)
    }

    fun playMinshawiAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.adhan_minshawi)
    }

    fun playHussaryAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.adhan_hussary)
    }

    fun playAliMullaAzan() {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        playRawSound(R.raw.azan_makkah_1)
    }

    fun playMuezzin(muezzinId: String, muezzinName: String = "") {
        AppAudioManager.stopAllNonAdhanAudio(appContext)
        val downloadedFile1 = com.example.domain.prayer.AdhanAudioDownloaderManager.getDownloadedVoiceFile(appContext, muezzinId)
        val downloadedFile2 = com.example.domain.prayer.AdhanAudioDownloaderManager.getDownloadedVoiceFile(appContext, muezzinName)

        val fileToPlay = when {
            downloadedFile1.exists() && downloadedFile1.length() > 1024 -> downloadedFile1
            downloadedFile2.exists() && downloadedFile2.length() > 1024 -> downloadedFile2
            else -> null
        }

        if (fileToPlay != null) {
            playLocalFile(fileToPlay)
        } else {
            val resId = getRawResForMuezzin(muezzinId, muezzinName)
            playRawSound(resId)
        }
    }

    fun playLocalFile(file: java.io.File, onFinish: (() -> Unit)? = null) {
        stopAudio()
        ensureAudibleVolume()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(audioAttributes)
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                currentPlayingResId = null
                isPaused = false
                stopAudio()
                onFinish?.invoke()
                onCompletionCallback?.invoke()
                onStateChangedCallback?.invoke()
            }
            mp.setOnErrorListener { player, _, _ ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (mediaPlayer === player) mediaPlayer = null
                true
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            isPaused = false
            onStateChangedCallback?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
        }
    }

    fun playAudioUrlOrFile(urlOrPath: String, onFinish: (() -> Unit)? = null) {
        stopAudio()
        ensureAudibleVolume()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(audioAttributes)
            mp.setDataSource(urlOrPath)
            mp.setOnCompletionListener {
                currentPlayingResId = null
                isPaused = false
                stopAudio()
                onFinish?.invoke()
                onCompletionCallback?.invoke()
                onStateChangedCallback?.invoke()
            }
            mp.setOnErrorListener { player, _, _ ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (mediaPlayer === player) mediaPlayer = null
                true
            }
            mp.prepareAsync()
            mp.setOnPreparedListener {
                it.start()
            }
            mediaPlayer = mp
            isPaused = false
            onStateChangedCallback?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
        }
    }

    fun getRawResForMuezzin(muezzinId: String, muezzinName: String = ""): Int {
        val combined = "$muezzinId $muezzinName".lowercase()
        return when {
            combined.contains("morocco") || combined.contains("المغرب") || combined.contains("مرعوش") -> R.raw.adhan_morocco
            combined.contains("cairo") || combined.contains("القاهرة") || combined.contains("رفعت") -> R.raw.adhan_cairo
            combined.contains("abdulbasit") || combined.contains("عبد الباسط") -> R.raw.azan_ali_mulla
            combined.contains("minshawi") || combined.contains("المنشاوي") -> R.raw.adhan_minshawi
            combined.contains("hussary") || combined.contains("الحصري") -> R.raw.adhan_hussary
            else -> R.raw.azan_makkah_1
        }
    }

    fun playPrayForMuhammad() {
        playRawSound(R.raw.salli_ala_muhammad)
    }

    fun playSalawat() {
        playPrayForMuhammad()
    }

    fun playMorningAzkar(onFinish: (() -> Unit)? = null) {
        AppAudioManager.stopQuranAndSalawatOnly(appContext)
        AppAudioManager.requestAudioFocus(appContext, isAdhan = false)
        stopAudio()
        ensureAudibleVolume()
        playRawSoundWithFallback(R.raw.athkar_sabah, R.raw.azkar_sabah, onFinish)
    }

    fun playMorningAzkarSequence(onFinish: (() -> Unit)? = null) {
        playMorningAzkar(onFinish)
    }

    fun playEveningAzkar(onFinish: (() -> Unit)? = null) {
        AppAudioManager.stopQuranAndSalawatOnly(appContext)
        AppAudioManager.requestAudioFocus(appContext, isAdhan = false)
        stopAudio()
        ensureAudibleVolume()
        playRawSoundWithFallback(R.raw.azkar_massa, R.raw.azkar_massa, onFinish)
    }

    fun ensureAudibleVolume() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < (maxVol * 0.4f).toInt()) {
                    val targetVol = (maxVol * 0.7f).toInt().coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playRawSoundWithFallback(primaryResId: Int, fallbackResId: Int, onFinish: (() -> Unit)? = null) {
        stopAudio()
        ensureAudibleVolume()
        try {
            var player = createPreparedPlayer(primaryResId)
            if (player == null) {
                player = createPreparedPlayer(fallbackResId)
            }
            if (player != null) {
                currentPlayingResId = primaryResId
                isPaused = false
                player.setOnCompletionListener {
                    currentPlayingResId = null
                    isPaused = false
                    stopAudio()
                    onFinish?.invoke()
                    onCompletionCallback?.invoke()
                    onStateChangedCallback?.invoke()
                }
                player.start()
                mediaPlayer = player
                onStateChangedCallback?.invoke()
            } else {
                onFinish?.invoke()
                onCompletionCallback?.invoke()
                onStateChangedCallback?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                playRawSound(fallbackResId, onFinish)
            } catch (ex: Exception) {
                stopAudio()
                onFinish?.invoke()
                onCompletionCallback?.invoke()
                onStateChangedCallback?.invoke()
            }
        }
    }

    fun pauseAudio() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    isPaused = true
                    onStateChangedCallback?.invoke()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeAudio() {
        try {
            mediaPlayer?.let { player ->
                if (isPaused) {
                    player.start()
                    isPaused = false
                    onStateChangedCallback?.invoke()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause(rawResId: Int) {
        if (currentPlayingResId == rawResId) {
            if (isPlaying()) {
                pauseAudio()
            } else if (isPaused) {
                resumeAudio()
            } else {
                playRawSound(rawResId)
            }
        } else {
            playRawSound(rawResId)
        }
    }

    private fun playRawSoundWithNext(firstResId: Int, nextResId: Int, onFinish: (() -> Unit)? = null) {
        stopAudio()
        try {
            val player = createPreparedPlayer(firstResId)
            if (player != null) {
                currentPlayingResId = firstResId
                isPaused = false
                player.setOnCompletionListener {
                    stopAudio()
                    playRawSound(nextResId, onFinish)
                }
                player.start()
                mediaPlayer = player
                onStateChangedCallback?.invoke()
            } else {
                playRawSound(nextResId, onFinish)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
            playRawSound(nextResId, onFinish)
        }
    }

    fun playRawSound(rawResId: Int, onFinish: (() -> Unit)? = null) {
        stopAudio()
        try {
            val player = createPreparedPlayer(rawResId)
            if (player != null) {
                currentPlayingResId = rawResId
                isPaused = false
                player.setOnCompletionListener {
                    currentPlayingResId = null
                    isPaused = false
                    stopAudio()
                    onFinish?.invoke()
                    onCompletionCallback?.invoke()
                    onStateChangedCallback?.invoke()
                }
                player.start()
                mediaPlayer = player
                onStateChangedCallback?.invoke()
            } else {
                onFinish?.invoke()
                onCompletionCallback?.invoke()
                onStateChangedCallback?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
            onFinish?.invoke()
            onCompletionCallback?.invoke()
            onStateChangedCallback?.invoke()
        }
    }

    private fun createPreparedPlayer(rawResId: Int): MediaPlayer? {
        // Try MediaPlayer.create first which properly resolves package raw resources
        try {
            val mp = MediaPlayer.create(appContext, rawResId, audioAttributes, 0)
                ?: MediaPlayer.create(appContext, rawResId)
            if (mp != null) {
                mp.setOnErrorListener { player, what, extra ->
                    try {
                        player.reset()
                        player.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                        currentPlayingResId = null
                        isPaused = false
                    }
                    onStateChangedCallback?.invoke()
                    true
                }
                return mp
            }
        } catch (_: Exception) {}

        // Fallback using AssetFileDescriptor
        var afd: AssetFileDescriptor? = null
        return try {
            afd = appContext.resources.openRawResourceFd(rawResId)
            if (afd != null) {
                val mp = MediaPlayer()
                mp.setAudioAttributes(audioAttributes)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                afd = null

                mp.setOnErrorListener { player, what, extra ->
                    try {
                        player.reset()
                        player.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                        currentPlayingResId = null
                        isPaused = false
                    }
                    onStateChangedCallback?.invoke()
                    true
                }

                mp.prepare()
                mp
            } else {
                null
            }
        } catch (e: Exception) {
            try { afd?.close() } catch (_: Exception) {}
            null
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (ignored: Exception) {}
                try {
                    player.reset()
                } catch (ignored: Exception) {}
                try {
                    player.release()
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentPlayingResId = null
            isPaused = false
            onStateChangedCallback?.invoke()
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
}


