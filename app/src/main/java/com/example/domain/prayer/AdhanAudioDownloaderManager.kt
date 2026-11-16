package com.example.domain.prayer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OnlineMuezzinItem(
    val id: String,
    val titleArabic: String,
    val titleEnglish: String,
    val location: String,
    val audioUrl: String,
    val quality: String = "320 kbps HD"
)

object AdhanAudioDownloaderManager {
    private const val TAG = "AdhanAudioDownloader"
    private const val ADHAN_VOICES_DIR = "adhan_voices"

    val ONLINE_MUEZZIN_LIST = listOf(
        OnlineMuezzinItem(
            id = "egypt_mbc_hq",
            titleArabic = "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
            titleEnglish = "Authentic Egyptian Adhan (MBC & Quran Radio)",
            location = "القاهرة 🇪🇬",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Muhammad_Refaat_-_Cairo_(%D9%85%D8%AD%D9%85%D8%AF_%D8%B1%D9%81%D8%B9%D8%AA_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9).mp3",
            quality = "320 kbps HD"
        ),
        OnlineMuezzinItem(
            id = "makkah_hq",
            titleArabic = "أذان الحرم المكي الشريف",
            titleEnglish = "Makkah Al-Haram - Sheikh Ali Mulla",
            location = "مكة المكرمة 🕋",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ali_Ibn_Ahmad_Mala_1_-_Al_Haram_Al_Maki_(%D8%B9%D9%84%D9%8A_%D8%A8%D9%86_%D8%A3%D8%AD%D9%85%D8%AF_%D9%85%D9%84%D8%A7_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A).mp3",
            quality = "320 kbps HD"
        ),
        OnlineMuezzinItem(
            id = "madinah_hq",
            titleArabic = "الأذان الخاشع فائق النقاء",
            titleEnglish = "Serene Pure Adhan - Madinah & Alafasy",
            location = "المدينة المنورة 🕌",
            audioUrl = "https://archive.org/download/Azan_Madina_HQ/Azan_Madina_HQ.mp3",
            quality = "320 kbps HD"
        )
    )

    fun getVoiceDir(context: Context): File {
        val dir = File(context.filesDir, ADHAN_VOICES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadedVoiceFile(context: Context, voiceIdOrName: String): File {
        val safeFileName = voiceIdOrName.replace("[^a-zA-Z0-9_]".toRegex(), "_") + ".mp3"
        return File(getVoiceDir(context), safeFileName)
    }

    fun isDownloaded(context: Context, voiceIdOrName: String): Boolean {
        val file = getDownloadedVoiceFile(context, voiceIdOrName)
        return file.exists() && file.length() > 10 * 1024 // > 10KB
    }

    suspend fun autoSyncHighQualityPacks(context: Context): Int {
        var syncedCount = 0
        for (item in ONLINE_MUEZZIN_LIST) {
            if (isDownloaded(context, item.id)) {
                syncedCount++
            } else {
                if (downloadVoice(context, item) {}) {
                    syncedCount++
                }
            }
        }
        return syncedCount
    }

    suspend fun downloadVoice(
        context: Context,
        voice: OnlineMuezzinItem,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getDownloadedVoiceFile(context, voice.id)
        val tempFile = File(getVoiceDir(context), "${voice.id}_temp.mp3")

        try {
            val url = URL(voice.audioUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode} for ${voice.titleArabic}")
                return@withContext false
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (inputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
                outputStream.write(data, 0, count)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (tempFile.length() > 10 * 1024) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                val titleFile = getDownloadedVoiceFile(context, voice.titleArabic)
                if (titleFile.exists()) titleFile.delete()
                try {
                    targetFile.copyTo(titleFile, overwrite = true)
                } catch (_: Exception) {}

                Log.d(TAG, "Successfully downloaded adhan voice: ${voice.titleArabic}")
                return@withContext true
            } else {
                tempFile.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading adhan voice ${voice.titleArabic}", e)
            try { tempFile.delete() } catch (_: Exception) {}
            return@withContext false
        }
    }

    fun deleteDownloadedVoice(context: Context, voiceIdOrName: String): Boolean {
        return try {
            val file = getDownloadedVoiceFile(context, voiceIdOrName)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }
}
