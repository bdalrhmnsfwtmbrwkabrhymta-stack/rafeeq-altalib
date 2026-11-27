package com.example.data.audio

import android.content.Context
import android.util.Log
import com.example.domain.prayer.PrayerDirectBootStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Provides authentic offline-cached Ayat Al-Kursi recitation (Surah Al-Baqarah 2:255)
 * and synthesized gentle morning songbird ambience for Morning Azkar (Muslimuna style),
 * while keeping Evening Azkar strictly serene without birdsong.
 */
object AzkarRecitationEngine {

    private const val TAG = "AzkarRecitationEngine"
    private const val AUDIO_DIR_NAME = "azkar_audio"
    private const val AYAT_AL_KURSI_FILE = "ayat_al_kursi_alafasy.mp3"
    private const val MORNING_BIRDS_FILE = "morning_birds_ambience_v2.wav"
    private const val MIN_VALID_MP3_BYTES = 15_000L

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Authentic high-clarity Ayat Al-Kursi (Surah 2, Ayah 255 / Global Ayah 262) mirrors
    private val AYAT_AL_KURSI_URLS = listOf(
        "https://everyayah.com/data/Alafasy_128kbps/002255.mp3",
        "https://cdn.islamic.network/quran/audio/128/ar.alafasy/262.mp3",
        "https://everyayah.com/data/Minshawy_Murattal_128kbps/002255.mp3"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun audioDir(context: Context): File {
        val baseCtx = PrayerDirectBootStore.getDeviceProtectedContext(context)
        val dir = File(baseCtx.filesDir, AUDIO_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedAyatAlKursiFile(context: Context): File? {
        val primary = File(audioDir(context), AYAT_AL_KURSI_FILE)
        if (primary.exists() && primary.length() >= MIN_VALID_MP3_BYTES) {
            return primary
        }
        val fallback = runCatching {
            File(File(context.applicationContext.filesDir, AUDIO_DIR_NAME), AYAT_AL_KURSI_FILE)
        }.getOrNull()
        if (fallback != null && fallback.exists() && fallback.length() >= MIN_VALID_MP3_BYTES) {
            return fallback
        }
        return null
    }

    /**
     * Silently pre-caches Ayat Al-Kursi MP3 and generates the morning birdsong WAV
     * in the background so both Morning and Evening Azkar work 100% offline.
     */
    fun warmUpAssetsAsync(context: Context) {
        val appCtx = context.applicationContext
        engineScope.launch {
            runCatching { ensureMorningBirdsWavFile(appCtx) }
            runCatching { resolveOrDownloadAyatAlKursi(appCtx) }
        }
    }

    /**
     * Returns the locally cached Ayat Al-Kursi MP3 if available, or downloads and caches it.
     * Returns null gracefully when completely offline and not yet cached.
     */
    suspend fun resolveOrDownloadAyatAlKursi(context: Context): File? = withContext(Dispatchers.IO) {
        getCachedAyatAlKursiFile(context)?.let { return@withContext it }

        val targetFile = File(audioDir(context), AYAT_AL_KURSI_FILE)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        for (url in AYAT_AL_KURSI_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    .header("Accept", "audio/mpeg, audio/*, */*")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body ?: return@use
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() >= MIN_VALID_MP3_BYTES) {
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    Log.i(TAG, "Cached Ayat Al-Kursi audio (${targetFile.length()} bytes) from $url")
                    return@withContext targetFile
                } else {
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ayat Al-Kursi download attempt failed for $url: ${e.message}")
                runCatching { if (tempFile.exists()) tempFile.delete() }
            }
        }
        null
    }

    /**
     * Generates (once) and caches a soothing 18-second dawn songbird & gentle morning breeze WAV.
     * Synthesizes realistic FM songbird warbles, double-chirps, and trills in natural dawn bands
     * (2.4 kHz - 4.7 kHz) so Morning Azkar opens with peaceful birdsong completely offline.
     */
    fun ensureMorningBirdsWavFile(context: Context): File? {
        val file = File(audioDir(context), MORNING_BIRDS_FILE)
        if (file.exists() && file.length() > 100_000L) {
            return file
        }

        return try {
            val sampleRate = 22050
            val durationSec = 18.0
            val totalSamples = (sampleRate * durationSec).toInt()
            val mix = FloatArray(totalSamples)

            // 1. Whisper-soft morning dawn breeze (filtered pink-ish gentle air)
            var b0 = 0.0
            var b1 = 0.0
            var seed = 133791L
            fun nextNoise(): Double {
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                val bits = (seed ushr 33).toInt()
                return (bits.toDouble() / Int.MAX_VALUE.toDouble()) * 2.0 - 1.0
            }
            for (i in 0 until totalSamples) {
                val n = nextNoise()
                b0 = 0.985 * b0 + 0.015 * n
                b1 = 0.960 * b1 + 0.040 * b0
                val t = i.toDouble() / sampleRate
                val swell = 0.65 + 0.35 * sin(2.0 * PI * 0.14 * t)
                mix[i] += (b1 * 0.045 * swell).toFloat()
            }

            // Helper to render a melodic FM songbird syllable with harmonic overtone & natural envelope
            fun addBirdSyllable(
                startSec: Double,
                durSec: Double,
                fStart: Double,
                fPeak: Double,
                fEnd: Double,
                vibHz: Double,
                vibDepthHz: Double,
                gain: Double
            ) {
                val startIdx = (startSec * sampleRate).toInt().coerceIn(0, totalSamples - 1)
                val len = (durSec * sampleRate).toInt().coerceAtLeast(1)
                val endIdx = (startIdx + len).coerceAtMost(totalSamples)
                var phase = 0.0
                var phase2 = 0.0
                for (i in startIdx until endIdx) {
                    val u = (i - startIdx).toDouble() / len.toDouble()
                    // Smooth bell envelope (fast natural attack, soft airy tail)
                    val attack = (u / 0.14).coerceAtMost(1.0)
                    val decay = exp(-3.2 * (u - 0.14).coerceAtLeast(0.0))
                    val env = attack * decay * sin(PI * u).coerceAtLeast(0.0)

                    // Quadratic pitch glide + gentle syrinx vibrato
                    val oneMinus = 1.0 - u
                    val baseFreq = oneMinus * oneMinus * fStart + 2.0 * oneMinus * u * fPeak + u * u * fEnd
                    val freq = baseFreq + vibDepthHz * sin(2.0 * PI * vibHz * (u * durSec))

                    phase += 2.0 * PI * freq / sampleRate
                    phase2 += 2.0 * PI * (freq * 2.0) / sampleRate
                    val sample = (sin(phase) * 0.84 + sin(phase2) * 0.16) * env * gain
                    mix[i] += sample.toFloat()
                }
            }

            // Helper to render a delicate dawn sparrow / nightingale phrase
            fun addMorningPhrase(baseTime: Double, scale: Double = 1.0) {
                // Bright rising double chirp ("chee-weet!")
                addBirdSyllable(baseTime + 0.00, 0.13, 2750.0, 3950.0, 3350.0, 26.0, 90.0, 0.30 * scale)
                addBirdSyllable(baseTime + 0.17, 0.16, 3100.0, 4450.0, 3700.0, 30.0, 110.0, 0.34 * scale)
                // Soft melodic warble trill
                for (k in 0 until 4) {
                    addBirdSyllable(
                        startSec = baseTime + 0.45 + k * 0.10,
                        durSec = 0.085,
                        fStart = 3200.0 + k * 110.0,
                        fPeak = 4050.0 + k * 90.0,
                        fEnd = 2950.0 + k * 80.0,
                        vibHz = 36.0,
                        vibDepthHz = 70.0,
                        gain = (0.24 - k * 0.025) * scale
                    )
                }
            }

            fun addDistantRobinCall(baseTime: Double, scale: Double = 1.0) {
                // Gentle descending flute-like morning call
                addBirdSyllable(baseTime + 0.00, 0.22, 3650.0, 3250.0, 2550.0, 18.0, 65.0, 0.22 * scale)
                addBirdSyllable(baseTime + 0.29, 0.19, 2900.0, 3800.0, 3150.0, 22.0, 75.0, 0.26 * scale)
                addBirdSyllable(baseTime + 0.56, 0.24, 3400.0, 4150.0, 2800.0, 20.0, 85.0, 0.20 * scale)
            }

            fun addSoftFinchChirp(baseTime: Double, scale: Double = 1.0) {
                addBirdSyllable(baseTime + 0.00, 0.11, 3400.0, 4600.0, 3900.0, 32.0, 80.0, 0.22 * scale)
                addBirdSyllable(baseTime + 0.16, 0.12, 3600.0, 4700.0, 3650.0, 28.0, 70.0, 0.19 * scale)
            }

            // Compose a natural 18-second dawn chorus (richer in the first 4s intro, lighter under Ayat Al-Kursi)
            addMorningPhrase(0.25, 1.0)
            addDistantRobinCall(1.35, 0.85)
            addSoftFinchChirp(2.30, 0.95)
            addMorningPhrase(2.90, 0.90)

            // Gentle sparse chirps while Ayat Al-Kursi begins
            addDistantRobinCall(4.80, 0.65)
            addSoftFinchChirp(6.40, 0.55)
            addMorningPhrase(8.20, 0.55)
            addDistantRobinCall(10.60, 0.45)
            addSoftFinchChirp(12.50, 0.40)
            addDistantRobinCall(14.60, 0.30)

            // Simple gentle echo (130ms delay) for open outdoor morning air feel
            val delaySamples = (0.13 * sampleRate).toInt()
            for (i in delaySamples until totalSamples) {
                mix[i] += mix[i - delaySamples] * 0.22f
            }

            // Apply overall fade-in (0.8s) and smooth fade-out (3.0s) + soft limiting
            val fadeInSamples = (0.8 * sampleRate).toInt()
            val fadeOutSamples = (3.0 * sampleRate).toInt()
            val pcm = ByteArray(totalSamples * 2)
            val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until totalSamples) {
                val envIn = if (i < fadeInSamples) i.toDouble() / fadeInSamples else 1.0
                val envOut = if (i > totalSamples - fadeOutSamples) {
                    (totalSamples - i).toDouble() / fadeOutSamples
                } else 1.0
                val shaped = tanh(mix[i].toDouble() * 1.15) * envIn * envOut
                val s16 = (shaped * 30000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                bb.putShort(s16.toShort())
            }

            writeWavFile(file, pcm, sampleRate, channels = 1, bitsPerSample = 16)
            Log.i(TAG, "Synthesized offline morning birdsong WAV (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed generating morning birdsong WAV", e)
            null
        }
    }

    private fun writeWavFile(
        target: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { fos ->
            fos.write(header)
            fos.write(pcmData)
            fos.flush()
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
