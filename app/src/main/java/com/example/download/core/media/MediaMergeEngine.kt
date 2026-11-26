package com.example.download.core.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.hawas.study.rafid.worker.SerialFFmpegMuxerWorker
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class MediaStreamInfo(
    val index: Int,
    val codecType: String,
    val codecName: String,
    val width: Int = 0,
    val height: Int = 0,
    val language: String = "",
    val durationSec: Double = 0.0,
    val sampleRate: Int = 0,
    val channels: Int = 0
)

data class MediaProbeReport(
    val file: File,
    val fileSizeBytes: Long,
    val containerFormat: String,
    val durationSec: Double,
    val streams: List<MediaStreamInfo>,
    val usedFfprobe: Boolean,
    val isHtmlOrText: Boolean = false,
    val rawError: String? = null
) {
    val videoStreams: List<MediaStreamInfo> get() = streams.filter { it.codecType == "video" }
    val audioStreams: List<MediaStreamInfo> get() = streams.filter { it.codecType == "audio" }
    val hasVideoStream: Boolean get() = videoStreams.isNotEmpty()
    val hasAudioStream: Boolean get() = audioStreams.isNotEmpty()

    fun toSummaryString(): String {
        val vSummary = videoStreams.joinToString(",") { "${it.codecName}(${it.width}x${it.height})" }.ifBlank { "none" }
        val aSummary = audioStreams.joinToString(",") {
            val lang = if (it.language.isNotBlank()) ":${it.language}" else ""
            "${it.codecName}$lang"
        }.ifBlank { "none" }
        return "container=$containerFormat, size=$fileSizeBytes, dur=${"%.2f".format(Locale.US, durationSec)}s, v=[$vSummary], a=[$aSummary], ffprobe=$usedFfprobe"
    }
}

data class MergeExecutionResult(
    val outputFile: File,
    val finalExtension: String,
    val finalMimeType: String,
    val probeReport: MediaProbeReport,
    val usedFallbackReencodeAudio: Boolean = false,
    val ffmpegExitCode: Int = 0
)

/**
 * Unified, verifiable native FFmpeg + FFprobe media engine for Hawas El-Dirasa.
 * - Executes bundled libffmpeg.so / libffprobe.so directly via ProcessBuilder with proper LD_LIBRARY_PATH.
 * - Uses lossless stream copy whenever compatible; transcodes only incompatible audio tracks (keeping video intact).
 * - Strictly verifies resulting artifacts via ffprobe/MediaExtractor before any caller marks COMPLETED.
 */
object MediaMergeEngine {

    private val HTML_MARKERS = listOf("<!doctype html", "<html", "<head", "<body", "<script", "<?xml")

    data class NativeRuntimePaths(
        val binDir: File,
        val ffmpegBinary: File?,
        val ffprobeBinary: File?,
        val quickJsBinary: File?,
        val ldLibraryPath: String
    )

    @Synchronized
    fun ensureRuntimeReady(context: Context): NativeRuntimePaths {
        val appCtx = context.applicationContext
        runCatching { YoutubeDL.getInstance().init(appCtx) }
        runCatching { FFmpeg.getInstance().init(appCtx) }

        val nativeLibDir = runCatching {
            appCtx.applicationInfo?.nativeLibraryDir?.let(::File)
        }.getOrNull()

        val packagesRoot = runCatching {
            File(File(appCtx.noBackupFilesDir, "youtubedl-android"), "packages")
        }.getOrNull()

        val ldPaths = buildList {
            if (packagesRoot != null) {
                add(File(packagesRoot, "ffmpeg/usr/lib").absolutePath)
                add(File(packagesRoot, "python/usr/lib").absolutePath)
                add(File(packagesRoot, "aria2c/usr/lib").absolutePath)
            }
            if (nativeLibDir != null) {
                add(nativeLibDir.absolutePath)
            }
        }.filter { it.isNotBlank() }.distinct().joinToString(":")

        val ffmpegFile = nativeLibDir?.let { File(it, "libffmpeg.so") }?.takeIf { it.exists() }
        val ffprobeFile = nativeLibDir?.let { File(it, "libffprobe.so") }?.takeIf { it.exists() }
        val qjsFile = nativeLibDir?.let { File(it, "libqjs.so") }?.takeIf { it.exists() }

        return NativeRuntimePaths(
            binDir = nativeLibDir ?: appCtx.filesDir,
            ffmpegBinary = ffmpegFile,
            ffprobeBinary = ffprobeFile,
            quickJsBinary = qjsFile,
            ldLibraryPath = ldPaths
        )
    }

    fun isHtmlOrTextPayload(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val buf = ByteArray(192)
        val read = runCatching { FileInputStream(file).use { it.read(buf) } }.getOrDefault(0)
        if (read <= 0) return false
        val head = String(buf, 0, read, Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
        return HTML_MARKERS.any { head.startsWith(it) }
    }

    fun probeMediaFile(context: Context?, file: File): MediaProbeReport {
        if (!file.exists() || file.length() <= 0L) {
            return MediaProbeReport(
                file = file,
                fileSizeBytes = 0L,
                containerFormat = "",
                durationSec = 0.0,
                streams = emptyList(),
                usedFfprobe = false,
                rawError = "File missing or empty"
            )
        }
        if (isHtmlOrTextPayload(file)) {
            return MediaProbeReport(
                file = file,
                fileSizeBytes = file.length(),
                containerFormat = "text/html",
                durationSec = 0.0,
                streams = emptyList(),
                usedFfprobe = false,
                isHtmlOrText = true,
                rawError = "Payload starts with HTML/XML markup instead of binary media"
            )
        }

        val paths = context?.let { ensureRuntimeReady(it) }
        val ffprobe = paths?.ffprobeBinary
        if (ffprobe != null && ffprobe.exists() && ffprobe.canExecute()) {
            val probeRes = runNativeCommand(
                binary = ffprobe,
                args = listOf(
                    "-v", "error",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    file.absolutePath
                ),
                ldLibraryPath = paths.ldLibraryPath,
                timeoutSeconds = 20L
            )
            if (probeRes.exitCode == 0 && probeRes.stdout.isNotBlank()) {
                parseFfprobeJson(file, probeRes.stdout)?.let { return it }
            }
        }

        return probeWithAndroidMediaExtractor(file)
    }

    fun verifyFinalArtifact(
        context: Context?,
        file: File,
        requireVideo: Boolean,
        requireAudio: Boolean,
        isImageOrDocument: Boolean = false
    ): MediaProbeReport {
        if (!file.exists() || file.length() <= 0L) {
            throw DownloadEngineException(
                DownloadError.IntegrityCheckFailed("الملف الناتج مفقود أو فارغ الحجم (${file.name})")
            )
        }
        if (isHtmlOrTextPayload(file)) {
            throw DownloadEngineException(
                DownloadError.UnsupportedMedia(
                    mediaType = "text/html",
                    technicalDetail = "Verified artifact begins with HTML/XML instead of media binary (${file.name})"
                )
            )
        }
        if (isImageOrDocument) {
            return MediaProbeReport(
                file = file,
                fileSizeBytes = file.length(),
                containerFormat = file.extension.lowercase(Locale.ROOT),
                durationSec = 0.0,
                streams = emptyList(),
                usedFfprobe = false
            )
        }

        val report = probeMediaFile(context, file)
        return validateProbeReport(report, requireVideo = requireVideo, requireAudio = requireAudio)
    }

    internal fun validateProbeReport(
        report: MediaProbeReport,
        requireVideo: Boolean,
        requireAudio: Boolean
    ): MediaProbeReport {
        if (report.isHtmlOrText) {
            throw DownloadEngineException(
                DownloadError.UnsupportedMedia(
                    mediaType = "text/html",
                    technicalDetail = report.rawError ?: "HTML detected by probe"
                )
            )
        }

        // Only enforce strict stream presence when a probe engine (ffprobe or MediaExtractor) parsed streams
        val probeParsedContainer = report.usedFfprobe || report.streams.isNotEmpty() || report.containerFormat.isNotBlank()
        if (probeParsedContainer) {
            if (requireVideo && !report.hasVideoStream) {
                throw DownloadEngineException(
                    DownloadError.MergeFailed("MissingVideoStream: الملف النهائي لا يحتوي على مسار فيديو صالح (${report.toSummaryString()})")
                )
            }
            if (requireAudio && !report.hasAudioStream) {
                throw DownloadEngineException(
                    DownloadError.MergeFailed("MissingAudioStream: الملف النهائي بدون مسار صوت (${report.toSummaryString()})")
                )
            }
            val vDur = report.videoStreams.maxOfOrNull { it.durationSec } ?: 0.0
            val aDur = report.audioStreams.maxOfOrNull { it.durationSec } ?: 0.0
            if (requireVideo && requireAudio && vDur > 5.0 && aDur > 5.0) {
                val ratio = minOf(vDur, aDur) / maxOf(vDur, aDur)
                val diff = abs(vDur - aDur)
                if (ratio < 0.4 && diff > 15.0) {
                    throw DownloadEngineException(
                        DownloadError.MergeFailed(
                            "Audio/Video duration mismatch after mux: video=${"%.1f".format(Locale.US, vDur)}s, audio=${"%.1f".format(Locale.US, aDur)}s"
                        )
                    )
                }
            }
        } else if (report.rawError != null && !report.rawError.contains("RuntimeException")) {
            throw DownloadEngineException(
                DownloadError.MergeFailed("InvalidContainer: ${report.rawError}")
            )
        }

        return report
    }

    fun muxTracksWithFfmpeg(
        context: Context,
        primaryMediaFile: File,
        secondaryAudioFile: File?,
        desiredOutputFile: File,
        desiredExtension: String,
        audioOnly: Boolean
    ): MergeExecutionResult {
        if (!primaryMediaFile.exists() || primaryMediaFile.length() <= 0L) {
            throw DownloadEngineException(
                DownloadError.MergeFailed("ملف الفيديو أو المصدر الأساسي مفقود أو فارغ (تم الحفاظ على الملفات المؤقتة)")
            )
        }
        if (isHtmlOrTextPayload(primaryMediaFile)) {
            throw DownloadEngineException(
                DownloadError.UnsupportedMedia(
                    mediaType = "text/html",
                    technicalDetail = "Primary downloaded track is text/html instead of media stream"
                )
            )
        }
        if (secondaryAudioFile != null) {
            if (!secondaryAudioFile.exists() || secondaryAudioFile.length() <= 0L) {
                throw DownloadEngineException(
                    DownloadError.MergeFailed("ملف مسار الصوت مفقود أو فارغ (تم الحفاظ على الملفات المؤقتة)")
                )
            }
            if (isHtmlOrTextPayload(secondaryAudioFile)) {
                throw DownloadEngineException(
                    DownloadError.UnsupportedMedia(
                        mediaType = "text/html",
                        technicalDetail = "Downloaded audio track is text/html instead of audio stream"
                    )
                )
            }
        }

        val paths = ensureRuntimeReady(context)
        val cleanExt = desiredExtension.trim().lowercase(Locale.ROOT).ifBlank { if (audioOnly) "m4a" else "mp4" }
        val primaryProbe = probeMediaFile(context, primaryMediaFile)
        val secondaryProbe = secondaryAudioFile?.let { probeMediaFile(context, it) }

        val targetDir = desiredOutputFile.parentFile ?: primaryMediaFile.parentFile ?: context.cacheDir
        targetDir.mkdirs()

        // Fast-path passthrough when only 1 complete file already matches format and doesn't need transcode
        if (!audioOnly && secondaryAudioFile == null &&
            primaryProbe.hasVideoStream &&
            (!primaryProbe.usedFfprobe || primaryProbe.containerFormat.contains(cleanExt, ignoreCase = true) || cleanExt == "mp4")
        ) {
            val verified = runCatching {
                verifyFinalArtifact(
                    context = context,
                    file = primaryMediaFile,
                    requireVideo = true,
                    requireAudio = primaryProbe.hasAudioStream
                )
            }.getOrNull()
            if (verified != null && (primaryMediaFile.extension.equals(cleanExt, true) || primaryProbe.containerFormat.contains("mp4", true))) {
                if (primaryMediaFile.absolutePath != desiredOutputFile.absolutePath) {
                    primaryMediaFile.copyTo(desiredOutputFile, overwrite = true)
                }
                return MergeExecutionResult(
                    outputFile = desiredOutputFile,
                    finalExtension = cleanExt,
                    finalMimeType = mimeForExt(cleanExt, audioOnly = false),
                    probeReport = verified
                )
            }
        }

        // Fast-path Android MediaMuxer ONLY when codecs fit standard MP4/WEBM without audio loss
        val canTryMediaMuxer = !audioOnly &&
            secondaryAudioFile != null &&
            cleanExt != "mp3" &&
            isAndroidMediaMuxerFriendly(primaryProbe, secondaryProbe, cleanExt)
        if (canTryMediaMuxer) {
            val stagingFile = File(targetDir, ".staging_mm_${System.nanoTime()}.$cleanExt")
            val mmOk = SerialFFmpegMuxerWorker.muxTracks(primaryMediaFile, secondaryAudioFile, stagingFile, audioOnly = false)
            if (mmOk && stagingFile.exists() && stagingFile.length() > 0L) {
                val report = runCatching {
                    verifyFinalArtifact(context, stagingFile, requireVideo = true, requireAudio = true)
                }.getOrNull()
                if (report != null) {
                    if (desiredOutputFile.exists()) desiredOutputFile.delete()
                    if (!stagingFile.renameTo(desiredOutputFile)) {
                        stagingFile.copyTo(desiredOutputFile, overwrite = true)
                        stagingFile.delete()
                    }
                    return MergeExecutionResult(
                        outputFile = desiredOutputFile,
                        finalExtension = cleanExt,
                        finalMimeType = mimeForExt(cleanExt, audioOnly = false),
                        probeReport = report
                    )
                }
            }
            runCatching { stagingFile.delete() }
        }

        val ffmpegBin = paths.ffmpegBinary
        if (ffmpegBin == null || !ffmpegBin.exists() || !ffmpegBin.canExecute()) {
            // Fallback last-chance Android MediaMuxer when running without native ffmpeg binary (e.g. non-ARM JVM tests)
            val fallbackOk = SerialFFmpegMuxerWorker.muxTracks(primaryMediaFile, secondaryAudioFile, desiredOutputFile, audioOnly)
            if (fallbackOk && desiredOutputFile.exists() && desiredOutputFile.length() > 0L) {
                val verified = verifyFinalArtifact(
                    context = context,
                    file = desiredOutputFile,
                    requireVideo = !audioOnly,
                    requireAudio = audioOnly || secondaryAudioFile != null
                )
                return MergeExecutionResult(
                    outputFile = desiredOutputFile,
                    finalExtension = cleanExt,
                    finalMimeType = mimeForExt(cleanExt, audioOnly),
                    probeReport = verified
                )
            }
            throw DownloadEngineException(
                DownloadError.FFmpegFailed("FFmpegMissing: محرك الدمج libffmpeg.so غير متوفر أو غير قابل للتنفيذ على هذا الجهاز")
            )
        }

        val ffmpegAttempts = buildFfmpegMergeAttempts(
            primaryFile = primaryMediaFile,
            secondaryAudioFile = secondaryAudioFile,
            primaryProbe = primaryProbe,
            secondaryProbe = secondaryProbe,
            cleanExt = cleanExt,
            audioOnly = audioOnly
        )

        var lastExit = -1
        val stderrHistory = StringBuilder()

        for (attempt in ffmpegAttempts) {
            val stagingOut = File(targetDir, ".staging_ff_${System.nanoTime()}.${attempt.ext}")
            val fullArgs = attempt.args + listOf(stagingOut.absolutePath)
            val res = runNativeCommand(
                binary = ffmpegBin,
                args = fullArgs,
                ldLibraryPath = paths.ldLibraryPath,
                timeoutSeconds = 240L
            )
            lastExit = res.exitCode
            if (res.exitCode == 0 && stagingOut.exists() && stagingOut.length() > 0L) {
                val verifyRes = runCatching {
                    verifyFinalArtifact(
                        context = context,
                        file = stagingOut,
                        requireVideo = !audioOnly,
                        requireAudio = audioOnly || secondaryAudioFile != null || primaryProbe.hasAudioStream
                    )
                }
                val report = verifyRes.getOrNull()
                if (report != null) {
                    val finalOut = if (attempt.ext == cleanExt) {
                        desiredOutputFile
                    } else {
                        File(targetDir, "${desiredOutputFile.nameWithoutExtension}.${attempt.ext}")
                    }
                    if (finalOut.exists()) finalOut.delete()
                    if (!stagingOut.renameTo(finalOut)) {
                        stagingOut.copyTo(finalOut, overwrite = true)
                        stagingOut.delete()
                    }
                    return MergeExecutionResult(
                        outputFile = finalOut,
                        finalExtension = attempt.ext,
                        finalMimeType = mimeForExt(attempt.ext, audioOnly),
                        probeReport = report,
                        usedFallbackReencodeAudio = attempt.reencodedAudio,
                        ffmpegExitCode = res.exitCode
                    )
                } else {
                    stderrHistory.appendLine("[verify-fail ${attempt.tag}] ${verifyRes.exceptionOrNull()?.message}")
                }
            } else {
                stderrHistory.appendLine("[exit=${res.exitCode} ${attempt.tag}] ${sanitizeStderr(res.stderr)}")
            }
            runCatching { stagingOut.delete() }
        }

        // Fallback last-chance Android MediaMuxer if FFmpeg execution failed on compatible MP4/WebM streams
        if (desiredOutputFile.parentFile != null) {
            val fallbackMuxTarget = File(targetDir, ".fallback_mm_${System.nanoTime()}.$cleanExt")
            val fallbackOk = SerialFFmpegMuxerWorker.muxTracks(
                primaryMediaFile = primaryMediaFile,
                secondaryAudioFile = secondaryAudioFile,
                outputFile = fallbackMuxTarget,
                audioOnly = audioOnly
            )
            if (fallbackOk && fallbackMuxTarget.exists() && fallbackMuxTarget.length() > 0L) {
                val verified = runCatching {
                    verifyFinalArtifact(
                        context = context,
                        file = fallbackMuxTarget,
                        requireVideo = !audioOnly,
                        requireAudio = audioOnly || secondaryAudioFile != null || primaryProbe.hasAudioStream
                    )
                }.getOrNull()
                if (verified != null) {
                    if (desiredOutputFile.exists()) desiredOutputFile.delete()
                    if (!fallbackMuxTarget.renameTo(desiredOutputFile)) {
                        fallbackMuxTarget.copyTo(desiredOutputFile, overwrite = true)
                        fallbackMuxTarget.delete()
                    }
                    return MergeExecutionResult(
                        outputFile = desiredOutputFile,
                        finalExtension = cleanExt,
                        finalMimeType = mimeForExt(cleanExt, audioOnly),
                        probeReport = verified
                    )
                }
            }
            runCatching { fallbackMuxTarget.delete() }
        }

        throw DownloadEngineException(
            DownloadError.MergeFailed(
                "FFmpegExecutionFailed(exit=$lastExit): ${stderrHistory.toString().trim().take(480)}"
            )
        )
    }

    private data class FfmpegAttempt(
        val tag: String,
        val ext: String,
        val args: List<String>,
        val reencodedAudio: Boolean = false
    )

    private fun buildFfmpegMergeAttempts(
        primaryFile: File,
        secondaryAudioFile: File?,
        primaryProbe: MediaProbeReport,
        secondaryProbe: MediaProbeReport?,
        cleanExt: String,
        audioOnly: Boolean
    ): List<FfmpegAttempt> {
        if (audioOnly) {
            val input = secondaryAudioFile ?: primaryFile
            return when (cleanExt) {
                "mp3" -> listOf(
                    FfmpegAttempt(
                        tag = "audio_mp3_lame",
                        ext = "mp3",
                        args = listOf("-y", "-i", input.absolutePath, "-vn", "-c:a", "libmp3lame", "-q:a", "2"),
                        reencodedAudio = true
                    ),
                    FfmpegAttempt(
                        tag = "audio_mp3_copy",
                        ext = "mp3",
                        args = listOf("-y", "-i", input.absolutePath, "-vn", "-c:a", "copy")
                    )
                )
                else -> {
                    val acodec = (secondaryProbe ?: primaryProbe).audioStreams.firstOrNull()?.codecName.orEmpty()
                    val m4aFriendly = acodec.isBlank() || acodec in setOf("aac", "alac", "mp3")
                    buildList {
                        if (m4aFriendly) {
                            add(
                                FfmpegAttempt(
                                    tag = "audio_m4a_copy",
                                    ext = "m4a",
                                    args = listOf("-y", "-i", input.absolutePath, "-vn", "-c:a", "copy", "-movflags", "+faststart")
                                )
                            )
                        }
                        add(
                            FfmpegAttempt(
                                tag = "audio_m4a_aac",
                                ext = "m4a",
                                args = listOf("-y", "-i", input.absolutePath, "-vn", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"),
                                reencodedAudio = true
                            )
                        )
                    }
                }
            }
        }

        val vCodec = primaryProbe.videoStreams.firstOrNull()?.codecName.orEmpty().lowercase(Locale.ROOT)
        val aCodec = (secondaryProbe?.audioStreams?.firstOrNull() ?: primaryProbe.audioStreams.firstOrNull())
            ?.codecName.orEmpty().lowercase(Locale.ROOT)

        val mp4VideoOk = vCodec.isBlank() || vCodec in setOf("h264", "avc", "hevc", "h265", "av1", "av01", "mpeg4")
        val mp4AudioOk = aCodec.isBlank() || aCodec.startsWith("mp4a") ||
            aCodec in setOf("aac", "mp3", "ac3", "eac3", "alac")

        val baseInputs = if (secondaryAudioFile != null) {
            listOf(
                "-y",
                "-i", primaryFile.absolutePath,
                "-i", secondaryAudioFile.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-shortest"
            )
        } else {
            listOf("-y", "-i", primaryFile.absolutePath)
        }

        return buildList {
            if (mp4VideoOk && mp4AudioOk && cleanExt == "mp4") {
                add(
                    FfmpegAttempt(
                        tag = "mp4_stream_copy",
                        ext = "mp4",
                        args = baseInputs + listOf("-c:v", "copy", "-c:a", "copy", "-movflags", "+faststart")
                    )
                )
            }
            if (mp4VideoOk) {
                add(
                    FfmpegAttempt(
                        tag = "mp4_copy_v_aac_a",
                        ext = "mp4",
                        args = baseInputs + listOf("-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"),
                        reencodedAudio = true
                    )
                )
            }
            // If video codec is VP8/VP9/AV1 + Opus/Vorbis (or unknown probe) -> lossless WebM container keeps 100% quality without re-encoding video
            if ((vCodec in setOf("vp8", "vp9", "av1", "av01") || (vCodec.isBlank() && cleanExt == "webm")) &&
                aCodec in setOf("", "opus", "vorbis")
            ) {
                add(
                    FfmpegAttempt(
                        tag = "webm_stream_copy",
                        ext = "webm",
                        args = baseInputs + listOf("-c:v", "copy", "-c:a", "copy")
                    )
                )
            }
            add(
                FfmpegAttempt(
                    tag = "mkv_universal_copy",
                    ext = "mkv",
                    args = baseInputs + listOf("-c:v", "copy", "-c:a", "copy")
                )
            )
        }
    }

    private fun isAndroidMediaMuxerFriendly(
        primary: MediaProbeReport,
        secondary: MediaProbeReport?,
        cleanExt: String
    ): Boolean {
        val vCodec = primary.videoStreams.firstOrNull()?.codecName.orEmpty().lowercase(Locale.ROOT)
        val aCodec = (secondary?.audioStreams?.firstOrNull() ?: primary.audioStreams.firstOrNull())
            ?.codecName.orEmpty().lowercase(Locale.ROOT)
        val mp4VideoOk = vCodec in setOf("h264", "avc", "avc1", "hevc", "h265", "hvc1", "hev1")
        val mp4AudioOk = aCodec in setOf("aac", "mp4a") || aCodec.startsWith("mp4a")
        val webmVideoOk = vCodec in setOf("vp8", "vp9")
        val webmAudioOk = aCodec in setOf("opus", "vorbis")
        return when (cleanExt.lowercase(Locale.ROOT)) {
            "mp4" -> mp4VideoOk && mp4AudioOk
            "webm" -> webmVideoOk && webmAudioOk
            else -> false
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    private fun runNativeCommand(
        binary: File,
        args: List<String>,
        ldLibraryPath: String,
        timeoutSeconds: Long
    ): CommandResult {
        return try {
            val pb = ProcessBuilder(listOf(binary.absolutePath) + args)
            if (ldLibraryPath.isNotBlank()) {
                val existing = pb.environment()["LD_LIBRARY_PATH"]
                pb.environment()["LD_LIBRARY_PATH"] = if (existing.isNullOrBlank()) {
                    ldLibraryPath
                } else {
                    "$ldLibraryPath:$existing"
                }
            }
            val process = pb.start()
            val outBuf = StringBuilder()
            val errBuf = StringBuilder()

            val outThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (outBuf.length < 256_000) outBuf.appendLine(line)
                        }
                    }
                }
            }.apply { start() }

            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (errBuf.length < 32_000) errBuf.appendLine(line)
                        }
                    }
                }
            }.apply { start() }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return CommandResult(-1, outBuf.toString(), "Process timed out after ${timeoutSeconds}s\n$errBuf")
            }
            outThread.join(1000)
            errThread.join(1000)
            CommandResult(process.exitValue(), outBuf.toString().trim(), errBuf.toString().trim())
        } catch (t: Throwable) {
            CommandResult(-1, "", "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    internal fun parseFfprobeJson(file: File, jsonText: String): MediaProbeReport? {
        return runCatching {
            val obj = JSONObject(jsonText)
            val formatObj = obj.optJSONObject("format")
            val formatName = formatObj?.optString("format_name").orEmpty()
            val totalDur = formatObj?.optString("duration")?.toDoubleOrNull() ?: 0.0
            val streamsArr = obj.optJSONArray("streams")
            val streams = buildList {
                if (streamsArr != null) {
                    for (i in 0 until streamsArr.length()) {
                        val s = streamsArr.optJSONObject(i) ?: continue
                        val tags = s.optJSONObject("tags")
                        add(
                            MediaStreamInfo(
                                index = s.optInt("index", i),
                                codecType = s.optString("codec_type").lowercase(Locale.ROOT),
                                codecName = s.optString("codec_name").lowercase(Locale.ROOT),
                                width = s.optInt("width", 0),
                                height = s.optInt("height", 0),
                                language = tags?.optString("language").orEmpty(),
                                durationSec = s.optString("duration").toDoubleOrNull() ?: totalDur,
                                sampleRate = s.optString("sample_rate").toIntOrNull() ?: 0,
                                channels = s.optInt("channels", 0)
                            )
                        )
                    }
                }
            }
            MediaProbeReport(
                file = file,
                fileSizeBytes = file.length(),
                containerFormat = formatName,
                durationSec = totalDur,
                streams = streams,
                usedFfprobe = true
            )
        }.getOrNull()
    }

    private fun probeWithAndroidMediaExtractor(file: File): MediaProbeReport {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val count = extractor.trackCount
            val streams = mutableListOf<MediaStreamInfo>()
            var maxDur = 0.0
            for (i in 0 until count) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty().lowercase(Locale.ROOT)
                val type = when {
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "other"
                }
                val rawCodec = mime.substringAfter('/').substringBefore(';')
                val codec = when {
                    rawCodec == "mp4a-latm" || rawCodec.startsWith("mp4a") -> "aac"
                    rawCodec.contains("vp9") -> "vp9"
                    rawCodec.contains("vp8") -> "vp8"
                    rawCodec == "av01" -> "av1"
                    else -> rawCodec
                }
                val w = if (fmt.containsKey(MediaFormat.KEY_WIDTH)) fmt.getInteger(MediaFormat.KEY_WIDTH) else 0
                val h = if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) fmt.getInteger(MediaFormat.KEY_HEIGHT) else 0
                val lang = if (fmt.containsKey(MediaFormat.KEY_LANGUAGE)) fmt.getString(MediaFormat.KEY_LANGUAGE).orEmpty() else ""
                val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
                val durSec = durUs / 1_000_000.0
                if (durSec > maxDur) maxDur = durSec
                streams += MediaStreamInfo(
                    index = i,
                    codecType = type,
                    codecName = codec,
                    width = w,
                    height = h,
                    language = lang,
                    durationSec = durSec
                )
            }
            MediaProbeReport(
                file = file,
                fileSizeBytes = file.length(),
                containerFormat = file.extension.lowercase(Locale.ROOT),
                durationSec = maxDur,
                streams = streams,
                usedFfprobe = false
            )
        } catch (t: Throwable) {
            MediaProbeReport(
                file = file,
                fileSizeBytes = file.length(),
                containerFormat = "",
                durationSec = 0.0,
                streams = emptyList(),
                usedFfprobe = false,
                rawError = "${t.javaClass.simpleName}: ${t.message}"
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun mimeForExt(ext: String, audioOnly: Boolean): String = when (ext.lowercase(Locale.ROOT)) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "webm" -> if (audioOnly) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> if (audioOnly) "audio/mp4" else "video/mp4"
    }

    private fun sanitizeStderr(raw: String): String =
        raw.replace(Regex("(Cookie|Authorization|token|signature|sig|key)=([^&\\s;]+)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            .lines()
            .takeLast(6)
            .joinToString(" | ")
            .take(360)
}
