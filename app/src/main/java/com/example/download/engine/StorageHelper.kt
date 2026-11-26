package com.example.download.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

object StorageHelper {

    private const val APP_FOLDER_NAME = "HawasElDirasa"
    private const val TEMP_DIR_NAME = "smart_download_parts"

    fun sanitizeFileName(rawTitle: String, ext: String, maxLen: Int = 80): String {
        val cleanExt = ext.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "mp4" }
        val base = rawTitle
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .ifBlank { "media_${System.currentTimeMillis()}" }
            .take(maxLen)
            .trim()
        return "$base.$cleanExt"
    }

    fun getTempPartDir(context: Context): File {
        val dir = File(context.filesDir, TEMP_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun prepareTempPartFile(context: Context, downloadId: String, fileName: String): File {
        val dir = getTempPartDir(context)
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF ]"), "_")
        return File(dir, "${downloadId}_$safeName.part")
    }

    fun prepareYtDlpTempBase(context: Context, downloadId: String): File {
        val dir = getTempPartDir(context)
        return File(dir, "yt_$downloadId")
    }

    fun hasEnoughFreeSpace(context: Context, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return true
        val safetyMargin = 20L * 1024L * 1024L
        val needed = requiredBytes + safetyMargin
        return try {
            val internalStat = StatFs(context.filesDir.absolutePath)
            if (internalStat.availableBytes < needed) return false
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val extStat = StatFs(externalDir.absolutePath)
                if (extStat.availableBytes < needed) return false
            }
            true
        } catch (_: Throwable) {
            true
        }
    }

    fun moveTempFileToPublicDownloads(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        mimeType: String,
        isAudioOnly: Boolean
    ): Result<Pair<String, String>> {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return Result.failure(IOException("الملف المؤقت فارغ أو غير موجود"))
        }

        val subFolder = if (isAudioOnly) "Audio" else "Video"
        val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER_NAME/$subFolder"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            moveViaMediaStoreQ(context, sourceFile, desiredFileName, mimeType, relativeFolder)
        } else {
            moveViaLegacyPublicDir(context, sourceFile, desiredFileName, mimeType, subFolder)
        }
    }

    private fun moveViaMediaStoreQ(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        mimeType: String,
        relativeFolder: String
    ): Result<Pair<String, String>> {
        val resolver = context.contentResolver
        val dotIndex = desiredFileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) desiredFileName.substring(0, dotIndex) else desiredFileName
        val ext = if (dotIndex > 0) desiredFileName.substring(dotIndex) else ""

        var attempt = 0
        var finalName = desiredFileName
        var insertedUri: Uri? = null

        return try {
            while (attempt < 50 && insertedUri == null) {
                finalName = if (attempt == 0) desiredFileName else "${baseName}_($attempt)$ext"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                try {
                    insertedUri = resolver.insert(collection, values)
                } catch (_: Throwable) {
                    attempt++
                }
                if (insertedUri == null) attempt++
            }

            val targetUri = insertedUri ?: return Result.failure(
                IOException("تعذر إنشاء ملف الوجهة في مجلد التنزيلات")
            )

            resolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            } ?: return Result.failure(IOException("تعذر فتح مسار الكتابة في التنزيلات"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(targetUri, publishValues, null, null)
            }

            runCatching { sourceFile.delete() }
            val finalDisplay = "$relativeFolder/$finalName"
            com.example.download.service.DownloadNotificationManager.scanFileIntoGallery(
                context = context,
                resolvedFile = null,
                savedUriString = targetUri.toString(),
                displayPath = finalDisplay,
                mimeType = mimeType
            )
            Result.success(targetUri.toString() to finalDisplay)
        } catch (t: Throwable) {
            insertedUri?.let { runCatching { resolver.delete(it, null, null) } }
            Result.failure(t)
        }
    }

    @Suppress("DEPRECATION")
    private fun moveViaLegacyPublicDir(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        mimeType: String,
        subFolder: String
    ): Result<Pair<String, String>> {
        return try {
            val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(File(downloadsRoot, APP_FOLDER_NAME), subFolder)
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return moveViaAppExternalFallback(context, sourceFile, desiredFileName, subFolder)
            }

            val destFile = resolveUniqueFile(targetDir, desiredFileName)
            FileInputStream(sourceFile).use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            runCatching { sourceFile.delete() }
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }
            Result.success(Uri.fromFile(destFile).toString() to "Download/$APP_FOLDER_NAME/$subFolder/${destFile.name}")
        } catch (_: SecurityException) {
            moveViaAppExternalFallback(context, sourceFile, desiredFileName, subFolder)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun moveViaAppExternalFallback(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        subFolder: String
    ): Result<Pair<String, String>> {
        return try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val targetDir = File(File(baseDir, APP_FOLDER_NAME), subFolder)
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = resolveUniqueFile(targetDir, desiredFileName)
            sourceFile.copyTo(destFile, overwrite = false)
            runCatching { sourceFile.delete() }
            Result.success(Uri.fromFile(destFile).toString() to destFile.absolutePath)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun resolveUniqueFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val base = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists() && counter < 500) {
            candidate = File(dir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }

    fun cleanupOrphanPartFiles(context: Context, activeDownloadIds: Set<String>): Int {
        val dir = getTempPartDir(context)
        val files = dir.listFiles() ?: return 0
        var deletedCount = 0
        val now = System.currentTimeMillis()
        for (file in files) {
            val name = file.name
            val belongsToActive = activeDownloadIds.any { id -> name.startsWith("${id}_") || name.startsWith("yt_$id") }
            if (!belongsToActive) {
                val ageMs = now - file.lastModified()
                if (ageMs > 60_000L || !name.endsWith(".part")) {
                    if (file.delete()) deletedCount++
                }
            }
        }
        return deletedCount
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "غير معروف"
        if (bytes < 1024L) return "$bytes ب"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f ك.ب", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f م.ب", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f ج.ب", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 ك.ب/ث"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f ك.ب/ث", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f م.ب/ث", mb)
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "--"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> String.format(Locale.US, "%d س %02d د", hrs, mins)
            mins > 0 -> String.format(Locale.US, "%d د %02d ث", mins, secs)
            else -> String.format(Locale.US, "%d ث", secs)
        }
    }
}
