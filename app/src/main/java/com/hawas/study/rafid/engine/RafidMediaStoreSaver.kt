package com.hawas.study.rafid.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hawas.study.rafid.models.RafidMediaCategory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SavedMediaResult(
    val uriString: String,
    val displayPath: String,
    val fileSizeBytes: Long
)

object RafidMediaStoreSaver {

    private const val APP_FOLDER_NAME = "HawasElDirasa"

    fun buildDisplaySubfolder(category: RafidMediaCategory, saveToStudyFolder: Boolean): String {
        return if (saveToStudyFolder) {
            "Download/$APP_FOLDER_NAME/Study"
        } else {
            "Download/$APP_FOLDER_NAME/${category.folderName}"
        }
    }

    fun sanitizeFileName(rawTitle: String, extension: String): String {
        val cleanBase = rawTitle
            .replace(Regex("""[\\/:*?"<>|\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(64)
            .ifBlank { "hawas_media_${System.currentTimeMillis()}" }
        val cleanExt = extension.trim().lowercase().removePrefix(".").ifBlank { "mp4" }
        return if (cleanBase.endsWith(".$cleanExt", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase.$cleanExt"
        }
    }

    @Suppress("DEPRECATION")
    fun saveFileToRafidDownloads(
        context: Context,
        sourceFile: File,
        title: String,
        extension: String,
        mimeType: String,
        category: RafidMediaCategory,
        saveToStudyFolder: Boolean
    ): SavedMediaResult {
        val fileName = sanitizeFileName(title, extension)
        val subFolder = if (saveToStudyFolder) "Study" else category.folderName
        val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER_NAME/$subFolder"
        val displayPath = "Download/$APP_FOLDER_NAME/$subFolder/$fileName"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativeFolder)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val insertedUri = resolver.insert(collection, values)
            if (insertedUri != null) {
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out, bufferSize = 64 * 1024)
                        }
                    } ?: throw IllegalStateException("Failed to open MediaStore output stream")

                    val readyValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, readyValues, null, null)
                    com.example.download.service.DownloadNotificationManager.scanFileIntoGallery(
                        context = context,
                        resolvedFile = null,
                        savedUriString = insertedUri.toString(),
                        displayPath = displayPath,
                        mimeType = mimeType
                    )
                    return SavedMediaResult(
                        uriString = insertedUri.toString(),
                        displayPath = displayPath,
                        fileSizeBytes = sourceFile.length()
                    )
                } catch (e: Exception) {
                    runCatching { resolver.delete(insertedUri, null, null) }
                    throw e
                }
            }
        }

        // Legacy Android 7-9: try public Download/HawasElDirasa/... first, then fallback to app external downloads
        val publicDir = runCatching {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$APP_FOLDER_NAME/$subFolder")
        }.getOrNull()
        val subDir = if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
            publicDir
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            File(baseDir, "$APP_FOLDER_NAME/$subFolder").apply { mkdirs() }
        }

        var outFile = File(subDir, fileName)
        if (outFile.exists()) {
            val stem = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.', extension)
            outFile = File(subDir, "${stem}_${System.currentTimeMillis() % 10000}.$ext")
        }

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        val finalDisplayPath = "Download/$APP_FOLDER_NAME/$subFolder/${outFile.name}"
        com.example.download.service.DownloadNotificationManager.scanFileIntoGallery(
            context = context,
            resolvedFile = outFile,
            savedUriString = null,
            displayPath = finalDisplayPath,
            mimeType = mimeType
        )

        return SavedMediaResult(
            uriString = Uri.fromFile(outFile).toString(),
            displayPath = finalDisplayPath,
            fileSizeBytes = outFile.length()
        )
    }
}
