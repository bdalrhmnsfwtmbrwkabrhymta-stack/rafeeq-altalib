package com.example.domain.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SoundEffectsManager {

    enum class SoundType {
        QUEST_COMPLETED,
        LEVEL_UP,
        RANK_UP,
        SHIELD_EARNED,
        SHIELD_USED,
        SUCCESS,
        ERROR,
        STREAK_MILESTONE,
        BUTTON_CLICK
    }

    private var toneGenerator: ToneGenerator? = null
    private var applicationContext: Context? = null

    private fun getOrCreateToneGenerator(): ToneGenerator? {
        toneGenerator?.let { return it }
        return try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 85).also { toneGenerator = it }
        } catch (_: Throwable) {
            null
        }
    }

    fun getInstance(context: Context? = null): SoundEffectsManager {
        if (context != null) {
            applicationContext = context.applicationContext
        }
        return this
    }

    fun playQuestSuccessSound(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.QUEST_COMPLETED)
    }

    fun playLevelUpSound(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.LEVEL_UP)
    }

    fun playRankUpSound(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.RANK_UP)
    }

    fun playShieldSound(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.SHIELD_EARNED)
    }

    fun playSuccess(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.SUCCESS)
    }

    fun playError(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.ERROR)
    }

    fun playLevelUp(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.LEVEL_UP)
    }

    fun playRankUp(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.RANK_UP)
    }

    fun playButtonClick(context: Context? = null) {
        val ctx = context ?: applicationContext
        if (ctx != null) playSound(ctx, SoundType.BUTTON_CLICK)
    }

    fun playSound(context: Context, type: SoundType, isSoundEnabled: Boolean = true) {
        if (!isSoundEnabled) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (type) {
                    SoundType.QUEST_COMPLETED -> {
                        // Play a bright uplifting chord: C5 -> E5 -> G5 -> C6
                        playSynthesizedTones(listOf(523, 659, 784, 1046), listOf(80, 80, 80, 160))
                    }
                    SoundType.LEVEL_UP -> {
                        // Fanfare: C5 -> G5 -> C6 -> E6 -> G6
                        playSynthesizedTones(listOf(523, 784, 1046, 1318, 1568), listOf(100, 100, 100, 150, 300))
                    }
                    SoundType.RANK_UP -> {
                        // Regal triumphant fanfare
                        playSynthesizedTones(listOf(440, 554, 659, 880, 1108, 1320), listOf(90, 90, 90, 120, 150, 350))
                    }
                    SoundType.SHIELD_EARNED -> {
                        // Shield powerup harmonic
                        playSynthesizedTones(listOf(440, 587, 880), listOf(100, 100, 250))
                    }
                    SoundType.SHIELD_USED -> {
                        // Protective aura pulse
                        playSynthesizedTones(listOf(880, 659, 523), listOf(120, 120, 200))
                    }
                    SoundType.STREAK_MILESTONE -> {
                        // Flame streak victory
                        playSynthesizedTones(listOf(587, 740, 880, 1175), listOf(80, 80, 100, 250))
                    }
                    SoundType.SUCCESS -> {
                        playSynthesizedTones(listOf(659, 880), listOf(100, 180))
                    }
                    SoundType.ERROR -> {
                        playSynthesizedTones(listOf(330, 260), listOf(120, 200))
                    }
                    SoundType.BUTTON_CLICK -> {
                        getOrCreateToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    }
                }
            } catch (e: Throwable) {
                // Fallback to ToneGenerator if audio track generation fails
                try {
                    val tg = getOrCreateToneGenerator()
                    when (type) {
                        SoundType.ERROR -> tg?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
                        SoundType.LEVEL_UP, SoundType.RANK_UP -> tg?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
                        else -> tg?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Synthesizes clean sinusoidal tones in sequence using AudioTrack without any external assets.
     */
    private fun playSynthesizedTones(frequencies: List<Int>, durationsMs: List<Int>) {
        val sampleRate = 44100
        val totalSamples = durationsMs.sumOf { (sampleRate * it) / 1000 }
        val generatedSnd = ByteArray(2 * totalSamples)

        var sampleIdx = 0
        for (i in frequencies.indices) {
            val freq = frequencies[i].toDouble()
            val numSamples = (sampleRate * durationsMs[i]) / 1000
            for (j in 0 until numSamples) {
                // Sine wave synthesis with smooth attack/decay envelope to avoid clicking
                val envelope = when {
                    j < 150 -> j.toDouble() / 150.0
                    j > numSamples - 250 -> (numSamples - j).toDouble() / 250.0
                    else -> 1.0
                }
                val sample = Math.sin(2.0 * Math.PI * j / (sampleRate / freq)) * envelope
                val intSample = (sample * 32767).toInt().toShort()

                val byteIdx = sampleIdx * 2
                generatedSnd[byteIdx] = (intSample.toInt() and 0x00ff).toByte()
                generatedSnd[byteIdx + 1] = ((intSample.toInt() and 0xff00) shr 8).toByte()
                sampleIdx++
            }
        }

        try {
            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(generatedSnd.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    AudioTrack.MODE_STATIC
                )
            }

            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.play()
        } catch (_: Exception) {}
    }
}
