package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Centralized Audio Manager ensuring strict priority handling and conflict resolution.
 * Adhan has absolute top priority and immediately halts any ongoing Quran, Athkar, or Salawat playback.
 */
object AppAudioManager {

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    // Listeners for components to be notified to stop when higher-priority audio starts
    private val stopPlaybackListeners = mutableListOf<() -> Unit>()

    fun registerStopListener(listener: () -> Unit) {
        synchronized(stopPlaybackListeners) {
            if (!stopPlaybackListeners.contains(listener)) {
                stopPlaybackListeners.add(listener)
            }
        }
    }

    fun unregisterStopListener(listener: () -> Unit) {
        synchronized(stopPlaybackListeners) {
            stopPlaybackListeners.remove(listener)
        }
    }

    /**
     * Halts active Quran, Athkar, and Salawat audio players immediately.
     * When includeAzkar is true (e.g. Adhan priority), also stops Azkar service.
     */
    fun stopAllNonAdhanAudio(context: Context? = null, includeAzkar: Boolean = true) {
        synchronized(stopPlaybackListeners) {
            val listenersCopy = ArrayList(stopPlaybackListeners)
            for (listener in listenersCopy) {
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Stop Azkar service safely without startService() (safe from background on Android 8-16)
        if (includeAzkar && context != null) {
            try {
                com.example.domain.prayer.AzkarAudioService.isPlayingAzkar = false
                context.stopService(
                    android.content.Intent(context, com.example.domain.prayer.AzkarAudioService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Halts Quran and Salawat playback without stopping Azkar audio service.
     */
    fun stopQuranAndSalawatOnly(context: Context? = null) {
        if (context != null) {
            try {
                com.example.domain.prayer.SalawatPlaybackService.stop(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Request Audio Focus for Adhan or Quran playback
     */
    fun requestAudioFocus(context: Context, isAdhan: Boolean = false): Boolean {
        val am = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager) ?: return false
        audioManager = am

        if (isAdhan) {
            stopAllNonAdhanAudio(context)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusType = if (isAdhan) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(if (isAdhan) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(focusType)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAllNonAdhanAudio(context)
                    }
                }
                .build()
            focusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = am.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAllNonAdhanAudio(context)
                    }
                },
                AudioManager.STREAM_MUSIC,
                if (isAdhan) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        focusRequest = null
    }
}
