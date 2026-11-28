package com.example.domain.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val EDGE_TAG = "EdgeNeuralTts"

/**
 * محرك الأصوات العربية العصبية الحقيقية مفتوح المصدر (مبني على بروتوكول GitHub: rany2/edge-tts):
 * - اللهجة المصرية (مصري):
 *   • معلم: ar-EG-ShakirNeural
 *   • معلمة: ar-EG-SalmaNeural
 * - اللغة العربية الفصحى (فصحى):
 *   • معلم: ar-SA-HamedNeural
 *   • معلمة: ar-SA-ZariyahNeural
 * مع تخزين مؤقت فوري (Cache) وبديل صوتي عصبي احتياطي لضمان عمل الصوت دائماً بنقاء عالٍ.
 */
class EdgeNeuralTtsClient(
    private val context: Context,
    private val httpClient: OkHttpClient = defaultClient()
) {

    data class NeuralVoiceProfile(
        val shortName: String,
        val displayLabelAr: String
    )

    fun resolveVoice(dialect: FahimDialect, gender: FahimVoiceGender): NeuralVoiceProfile {
        return when (dialect) {
            FahimDialect.EGYPTIAN -> when (gender) {
                FahimVoiceGender.MALE -> NeuralVoiceProfile("ar-EG-ShakirNeural", "صوت مصري طبيعي • شاكر")
                FahimVoiceGender.FEMALE -> NeuralVoiceProfile("ar-EG-SalmaNeural", "صوت مصري طبيعي • سلمى")
            }
            FahimDialect.FUSHA -> when (gender) {
                FahimVoiceGender.MALE -> NeuralVoiceProfile("ar-SA-HamedNeural", "صوت فصحى طبيعي • حامد")
                FahimVoiceGender.FEMALE -> NeuralVoiceProfile("ar-SA-ZariyahNeural", "صوت فصحى طبيعي • زارية")
            }
        }
    }

    suspend fun synthesizeToMp3File(
        text: String,
        dialect: FahimDialect,
        gender: FahimVoiceGender,
        speed: Float = 1.0f
    ): Pair<File, String>? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext null

        val voice = resolveVoice(dialect, gender)
        val cacheKey = sha256Hex("${voice.shortName}|$speed|$cleanText").take(28)
        val cacheDir = File(context.cacheDir, "fahim_neural_tts").apply { mkdirs() }
        val cachedFile = File(cacheDir, "tts_$cacheKey.mp3")
        if (cachedFile.exists() && cachedFile.length() > 512L) {
            return@withContext cachedFile to voice.displayLabelAr
        }

        // 1. Primary: GitHub open-source Edge-TTS Neural WebSocket protocol (ar-EG-ShakirNeural / SalmaNeural / HamedNeural / ZariyahNeural)
        val edgeBytes = synthesizeViaEdgeWebSocket(cleanText, voice.shortName, speed)
        if (edgeBytes != null && edgeBytes.size > 512) {
            cachedFile.writeBytes(edgeBytes)
            return@withContext cachedFile to voice.displayLabelAr
        }

        // 2. Secondary: Network Arabic TTS MP3 fallback (chunked <= 180 chars) so voice stays natural even if WebSocket is blocked
        val fallbackBytes = synthesizeViaNetworkArabicMp3(cleanText, dialect)
        if (fallbackBytes != null && fallbackBytes.size > 512) {
            cachedFile.writeBytes(fallbackBytes)
            return@withContext cachedFile to "صوت عربي شبكي (${dialect.labelAr})"
        }

        null
    }

    private suspend fun synthesizeViaEdgeWebSocket(
        text: String,
        voiceShortName: String,
        speed: Float
    ): ByteArray? = withTimeoutOrNull(14_000L) {
        suspendCancellableCoroutine { cont ->
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val secMsGec = generateSecMsGec()
            val wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION" +
                "&ConnectionId=$connectionId"

            val request = Request.Builder()
                .url(wsUrl)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
                )
                .build()

            val audioBuffer = ByteArrayOutputStream()
            var finished = false

            fun complete(result: ByteArray?) {
                if (!finished) {
                    finished = true
                    if (cont.isActive) cont.resume(result)
                }
            }

            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val timestamp = utcEdgeTimestamp()
                    val configPayload = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                    webSocket.send(configPayload)

                    val reqId = UUID.randomUUID().toString().replace("-", "")
                    val ratePct = (((speed - 1.0f) * 100f).toInt()).coerceIn(-40, 60)
                    val rateStr = if (ratePct >= 0) "+${ratePct}%" else "${ratePct}%"
                    val escapedText = escapeXml(text)
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ar-EG'>" +
                        "<voice name='$voiceShortName'>" +
                        "<prosody pitch='+0Hz' rate='$rateStr' volume='+0%'>$escapedText</prosody>" +
                        "</voice></speak>"

                    val ssmlPayload = "X-RequestId:$reqId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml
                    webSocket.send(ssmlPayload)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        val data = audioBuffer.toByteArray()
                        webSocket.close(1000, "done")
                        complete(if (data.size > 512) data else null)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val raw = bytes.toByteArray()
                    if (raw.size < 2) return
                    val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                    val payloadStart = 2 + headerLen
                    if (payloadStart < raw.size) {
                        val headerStr = String(raw, 2, headerLen.coerceAtMost(raw.size - 2), Charsets.UTF_8)
                        if (headerStr.contains("Path:audio")) {
                            audioBuffer.write(raw, payloadStart, raw.size - payloadStart)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(EDGE_TAG, "Edge Neural TTS websocket failed: ${t.message} (code=${response?.code})")
                    complete(null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val data = audioBuffer.toByteArray()
                    complete(if (data.size > 512) data else null)
                }
            })

            cont.invokeOnCancellation {
                runCatching { ws.cancel() }
            }
        }
    }

    private fun synthesizeViaNetworkArabicMp3(text: String, dialect: FahimDialect): ByteArray? = runCatching {
        val chunks = splitIntoChunks(text, 170)
        val out = ByteArrayOutputStream()
        val langParam = if (dialect == FahimDialect.EGYPTIAN) "ar" else "ar"
        for (chunk in chunks) {
            val encoded = URLEncoder.encode(chunk, "UTF-8")
            val url = "https://translate.googleapis.com/translate_tts?ie=UTF-8&tl=$langParam&client=gtx&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size > 256) out.write(bytes)
            }
        }
        val merged = out.toByteArray()
        if (merged.size > 512) merged else null
    }.getOrNull()

    private fun splitIntoChunks(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val result = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        val current = StringBuilder()
        for (w in words) {
            if (current.isNotEmpty() && current.length + w.length + 1 > maxLen) {
                result += current.toString().trim()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(w)
        }
        if (current.isNotEmpty()) result += current.toString().trim()
        return result
    }

    private fun generateSecMsGec(): String {
        val unixSeconds = System.currentTimeMillis() / 1000L
        var ticks = unixSeconds + 11_644_473_600L
        ticks -= (ticks % 300L)
        ticks *= 10_000_000L
        return sha256Hex("${ticks}$TRUSTED_CLIENT_TOKEN").uppercase(Locale.US)
    }

    private fun utcEdgeTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-130.0.2849.68"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
