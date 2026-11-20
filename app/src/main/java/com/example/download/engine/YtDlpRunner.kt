package com.example.download.engine

import android.content.Context
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadSettingsRepo
import com.example.download.data.LiveProgressSnapshot
import com.example.download.extract.LinkExtractor
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class YtDlpRunner(
    private val context: Context,
    private val settingsRepo: DownloadSettingsRepo
) {
    suspend fun executeDownload(
        entity: DownloadEntity,
        speedLimitKBps: Int,
        onMerging: suspend () -> Unit,
        onProgress: (downloadedBytes: Long, totalBytes: Long, snapshot: LiveProgressSnapshot) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!LinkExtractor.ensureInitialized(context)) {
            throw DownloadEngineException(
                DownloadError.EngineUpdateFailed("yt-dlp/FFmpeg engine unavailable")
            )
        }

        val tempBase = StorageHelper.prepareYtDlpTempBase(context, entity.id)
        val outputTemplate = "${tempBase.absolutePath}.%(ext)s"

        val request = YoutubeDLRequest(entity.originalUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--concurrent-fragments", "8")
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("-o", outputTemplate)

            if (speedLimitKBps > 0) {
                addOption("--limit-rate", "${speedLimitKBps}K")
            }

            val cookiesFile = settingsRepo.getCookiesFile()
            if (cookiesFile.exists() && cookiesFile.length() > 20L) {
                addOption("--cookies", cookiesFile.absolutePath)
            }

            if (entity.originalUrl.contains("tiktok.com", ignoreCase = true)) {
                addOption("--extractor-args", "tiktok:api_hostname=api22-normal-c-useast2a.tiktokv.com")
            }

            if (entity.isAudioOnly) {
                addOption("-x")
                val audioFmt = if (entity.fileName.endsWith(".mp3", true)) "mp3" else "m4a"
                addOption("--audio-format", audioFmt)
                addOption("--audio-quality", "0")
            } else {
                val fmt = entity.formatId?.takeIf { it.isNotBlank() } ?: "bestvideo+bestaudio/best"
                addOption("-f", fmt)
                addOption("--merge-output-format", "mp4")
            }
        }

        val processId = processIdFor(entity.id)
        var lastUiEmitMs = 0L
        val triggeredMerge = AtomicBoolean(false)
        var knownTotal = entity.totalBytes

        try {
            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val now = System.currentTimeMillis()
                val cleanLine = line ?: ""
                if ((cleanLine.contains("[Merger]", true) ||
                        cleanLine.contains("[ExtractAudio]", true) ||
                        cleanLine.contains("[FixupM3u8]", true) ||
                        cleanLine.contains("Fixing", true)) &&
                    triggeredMerge.compareAndSet(false, true)
                ) {
                    runCatching {
                        runBlocking { onMerging() }
                    }
                }

                val parsedSpeed = parseSpeedBytesPerSec(cleanLine)
                val parsedTotal = parseTotalBytes(cleanLine)?.takeIf { it > 0L } ?: knownTotal
                if (parsedTotal > 0L) knownTotal = parsedTotal

                val clampedPercent = progress.coerceIn(0f, 100f)
                val estDownloaded = if (knownTotal > 0L) {
                    ((clampedPercent / 100f) * knownTotal).toLong()
                } else {
                    (clampedPercent * 1024f).toLong()
                }

                if (now - lastUiEmitMs >= 450L) {
                    lastUiEmitMs = now
                    onProgress(
                        estDownloaded,
                        knownTotal,
                        LiveProgressSnapshot(
                            speedBytesPerSec = parsedSpeed,
                            etaSeconds = etaInSeconds.coerceAtLeast(-1L),
                            activeThreads = if (triggeredMerge.get()) 1 else 8
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            if (triggeredMerge.get()) {
                throw DownloadEngineException(
                    DownloadError.MergeFailed("FFmpeg merge interrupted while keeping partial files intact: ${t.message}"),
                    t
                )
            }
            throw t
        }

        val parentDir = tempBase.parentFile ?: StorageHelper.getTempPartDir(context)
        val produced = parentDir.listFiles()
            ?.filter {
                it.name.startsWith(tempBase.name + ".") &&
                    !it.name.endsWith(".part") &&
                    !it.name.endsWith(".ytdl") &&
                    it.length() > 0L
            }
            ?.maxByOrNull { it.lastModified() }
            ?: throw DownloadEngineException(
                DownloadError.MergeFailed("لم يتم العثور على الملف النهائي بعد الدمج (تم الحفاظ على الأجزاء المؤقتة)")
            )

        produced
    }

    fun cancelOrPauseProcess(downloadId: String) {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(processIdFor(downloadId))
        }
    }

    private fun processIdFor(downloadId: String): String = "dl_$downloadId"

    private fun parseSpeedBytesPerSec(line: String): Long {
        val match = Regex("""at\s+([\d.]+)\s*(KiB|MiB|GiB|KB|MB|GB)/s""", RegexOption.IGNORE_CASE).find(line)
            ?: return 0L
        val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "kib", "kb" -> (num * 1024.0).toLong()
            "mib", "mb" -> (num * 1024.0 * 1024.0).toLong()
            "gib", "gb" -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            else -> 0L
        }
    }

    private fun parseTotalBytes(line: String): Long? {
        val match = Regex("""of\s+~?([\d.]+)\s*(KiB|MiB|GiB|KB|MB|GB)""", RegexOption.IGNORE_CASE).find(line)
            ?: return null
        val num = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "kib", "kb" -> (num * 1024.0).toLong()
            "mib", "mb" -> (num * 1024.0 * 1024.0).toLong()
            "gib", "gb" -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            else -> null
        }
    }
}
