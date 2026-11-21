package com.example.download.core.updater

import android.content.Context
import android.os.Build
import com.example.download.core.media.MediaMergeEngine
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.TimeUnit

data class EngineRuntimeHealthReport(
    val ytDlpVersion: String,
    val ejsPresent: Boolean,
    val quickJsBinaryPresent: Boolean,
    val ffmpegBinaryPresent: Boolean,
    val ffmpegExecutable: Boolean,
    val ffprobeBinaryPresent: Boolean,
    val ffprobeExecutable: Boolean,
    val supportedAbis: List<String>
) {
    val isExtractionHealthy: Boolean
        get() = ytDlpVersion.isNotBlank() && quickJsBinaryPresent
    val isMuxingHealthy: Boolean
        get() = ffmpegBinaryPresent && ffmpegExecutable && ffprobeBinaryPresent && ffprobeExecutable
}

/**
 * Inspects decoupled runtime components separately:
 * 1) yt-dlp Python zip / extractor bundle
 * 2) yt-dlp-ejs challenge solver scripts
 * 3) QuickJS runtime binary (libqjs.so)
 * 4) FFmpeg native binary (libffmpeg.so)
 * 5) FFprobe native binary (libffprobe.so)
 */
object AndroidEngineRuntimeInspector {

    fun inspect(context: Context): EngineRuntimeHealthReport {
        val appCtx = context.applicationContext
        val runtime = MediaMergeEngine.ensureRuntimeReady(appCtx)
        val ytVersion = runCatching { YoutubeDL.getInstance().version(appCtx) }.getOrNull().orEmpty()

        val noBackupRoot = runCatching { File(appCtx.noBackupFilesDir, "youtubedl-android") }.getOrNull()
        val ytDlpZip = noBackupRoot?.let { File(it, "yt-dlp/yt-dlp") }
        val ejsScripts = noBackupRoot?.walkTopDown()?.any {
            it.name.contains("ejs", ignoreCase = true) || it.name.contains("core.min.js")
        } ?: false

        val ffmpegOk = runtime.ffmpegBinary?.let { it.exists() && it.canExecute() } == true
        val ffprobeOk = runtime.ffprobeBinary?.let { it.exists() && it.canExecute() } == true

        return EngineRuntimeHealthReport(
            ytDlpVersion = ytVersion.ifBlank { if (ytDlpZip?.exists() == true) "bundled" else "" },
            ejsPresent = ejsScripts || ytDlpZip?.exists() == true,
            quickJsBinaryPresent = runtime.quickJsBinary?.exists() == true,
            ffmpegBinaryPresent = runtime.ffmpegBinary?.exists() == true,
            ffmpegExecutable = ffmpegOk,
            ffprobeBinaryPresent = runtime.ffprobeBinary?.exists() == true,
            ffprobeExecutable = ffprobeOk,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        )
    }

    fun validateStagedYtDlpArtifact(file: File): Boolean {
        if (!file.exists() || file.length() < 128L) return false
        val header = ByteArray(4)
        file.inputStream().use {
            if (it.read(header) < 2) return false
        }
        // Valid yt-dlp bundle is either Python script (#!) or ZIP archive (PK..)
        val isZip = header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        val isScript = header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()
        return isZip || isScript
    }

    fun probeFfmpegVersion(context: Context): String? {
        val runtime = MediaMergeEngine.ensureRuntimeReady(context)
        val ffmpeg = runtime.ffmpegBinary ?: return null
        if (!ffmpeg.exists() || !ffmpeg.canExecute()) return null
        return runCatching {
            val pb = ProcessBuilder(ffmpeg.absolutePath, "-version")
            if (runtime.ldLibraryPath.isNotBlank()) {
                pb.environment()["LD_LIBRARY_PATH"] = runtime.ldLibraryPath
            }
            val p = pb.start()
            val finished = p.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                return@runCatching null
            }
            p.inputStream.bufferedReader().readLine()?.trim()
        }.getOrNull()
    }
}
