package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageHelper {
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        val filename = "unky_image_${UUID.randomUUID()}.jpg"
        val file = File(context.filesDir, filename)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun createTempImageFile(context: Context): File {
        val filename = "unky_temp_${UUID.randomUUID()}.jpg"
        return File(context.cacheDir, filename)
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
