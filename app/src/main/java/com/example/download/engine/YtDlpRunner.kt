package com.example.download.engine

import android.content.Context
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadSettingsRepo
import com.example.download.data.LiveProgressSnapshot
import com.example.download.extract.LinkExtractor
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

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
        LinkExtractor.ensureInitialized(context)

        val tempBase = StorageHelper.prepareYtDlpTempBase(context, entity.id)
        val outputTemplate = "${tempBase.absolutePath}.%(ext)s"

        val request = YoutubeDLRequest(entity.originalUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--concurrent-fragments", "16")
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
        var triggeredMerge = false
        var knownTotal = entity.totalBytes

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            val now = System.currentTimeMillis()
            val cleanLine = line ?: ""
            if (!triggeredMerge && (cleanLine.contains("[Merger]", true) ||
                    cleanLine.contains("[ExtractAudio]", true) ||
                    cleanLine.contains("Fixing", true))
            ) {
                triggeredMerge = true
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

            if (now - lastUiEmitMs >= 500L) {
                lastUiEmitMs = now
                onProgress(
                    estDownloaded,
                    knownTotal,
                    LiveProgressSnapshot(
                        speedBytesPerSec = parsedSpeed,
                        etaSeconds = etaInSeconds.coerceAtLeast(-1L),
                        activeThreads = 16
                    )
                )
            }
        }

        if (triggeredMerge) {
            onMerging()
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
            ?: throw IOException("لم يتم العثور على الملف النهائي بعد الدمج")

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
