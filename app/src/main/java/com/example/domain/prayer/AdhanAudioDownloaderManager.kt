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
    val quality: String = "320kbps MP3 HQ"
)

object AdhanAudioDownloaderManager {
    private const val TAG = "AdhanAudioDownloader"
    private const val ADHAN_VOICES_DIR = "adhan_voices"

    val ONLINE_MUEZZIN_LIST = listOf(
        OnlineMuezzinItem(
            id = "makkah_mulla",
            titleArabic = "أذان الحرم المكي الشريف (الشيخ علي ملا)",
            titleEnglish = "Makkah Al-Mukarramah - Sheikh Ali Mulla",
            location = "مكة المكرمة 🕋",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ali_Ibn_Ahmad_Mala_1_-_Al_Haram_Al_Maki_(%D8%B9%D9%84%D9%8A_%D8%A8%D9%86_%D8%A3%D8%AD%D9%85%D8%AF_%D9%85%D9%84%D8%A7_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A).mp3"
        ),
        OnlineMuezzinItem(
            id = "madinah_khan",
            titleArabic = "أذان الحرم النبوي الشريف (الشيخ عصام خان)",
            titleEnglish = "Madinah Al-Munawwarah - Sheikh Essam Khan",
            location = "المدينة المنورة 🕌",
            audioUrl = "https://archive.org/download/Azan_Madina_HQ/Azan_Madina_HQ.mp3"
        ),
        OnlineMuezzinItem(
            id = "aqsa_jerusalem",
            titleArabic = "أذان المسجد الأقصى المبارك (القدس الشريف)",
            titleEnglish = "Al-Aqsa Mosque - Jerusalem",
            location = "القدس الشريف 🇵🇸",
            audioUrl = "https://archive.org/download/Azan_Aqsa_Palestine_HQ/Azan_Aqsa_Palestine_HQ.mp3"
        ),
        OnlineMuezzinItem(
            id = "cairo_refaat",
            titleArabic = "أذان القاهرة الأصيل (الشيخ محمد رفعت)",
            titleEnglish = "Cairo Egypt - Sheikh Mohamed Refaat",
            location = "القاهرة 🇪🇬",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Muhammad_Refaat_-_Cairo_(%D9%85%D8%AD%D9%85%D8%AF_%D8%B1%D9%81%D8%B9%D8%AA_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9).mp3"
        ),
        OnlineMuezzinItem(
            id = "afasy_adhan",
            titleArabic = "أذان الشيخ مشاري راشد العفاسي",
            titleEnglish = "Sheikh Mishary Rashid Alafasy",
            location = "الكويت 🇰🇼",
            audioUrl = "https://archive.org/download/Alafasy_Azan_HQ/Alafasy_Azan_HQ.mp3"
        ),
        OnlineMuezzinItem(
            id = "abdulbasit_adhan",
            titleArabic = "أذان الشيخ عبد الباسط عبد الصمد",
            titleEnglish = "Sheikh Abdulbasit Abdulsamad",
            location = "مصر 🇪🇬",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Abdulbasit_Abdusamad_5_-_Cairo_(%D8%B9%D8%A8%D8%AF_%D8%A7%D9%84%D8%A8%D8%A3%D8%B3%D8%B7_%D8%B9%D8%A8%D8%AF_%D8%A7%D9%84%D8%B5%D9%85%D8%AF_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9).mp3"
        ),
        OnlineMuezzinItem(
            id = "minshawi_adhan",
            titleArabic = "أذان الشيخ محمد صديق المنشاوي",
            titleEnglish = "Sheikh Mohamed Siddiq El-Minshawi",
            location = "مصر 🇪🇬",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mohamed_Siddiq_El-Minshawi_-_Egypt_1_(%D9%85%D8%AD%D9%85%D8%AF_%D8%B5%D8%AF%D9%8A%D9%82_%D8%A7%D9%84%D9%85%D9%86%D8%B4%D8%A7%D9%88%D9%8A_-_%D9%85%D8%B5%D8%B1).mp3"
        ),
        OnlineMuezzinItem(
            id = "hussary_adhan",
            titleArabic = "أذان الشيخ محمود خليل الحصري",
            titleEnglish = "Sheikh Mahmoud Khalil Al-Hussary",
            location = "مصر 🇪🇬",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mahmoud_Khalil_Al_Hussary_-_Cairo_(%D9%85%D8%AD%D9%85%D8%AF_%D8%AE%D9%84%D9%8A%D9%84_%D8%A7%D9%84%D8%AD%D8%B5%D8%B1%D9%8A_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9).mp3"
        ),
        OnlineMuezzinItem(
            id = "qatami_adhan",
            titleArabic = "أذان الشيخ ناصر القطامي",
            titleEnglish = "Sheikh Nasser Al-Qatami",
            location = "السعودية 🇸🇦",
            audioUrl = "https://archive.org/download/Nasser_Qatami_Azan_HQ/Nasser_Qatami_Azan_HQ.mp3"
        ),
        OnlineMuezzinItem(
            id = "dosari_adhan",
            titleArabic = "أذان الشيخ ياسر الدوسري",
            titleEnglish = "Sheikh Yasser Al-Dosari",
            location = "السعودية 🇸🇦",
            audioUrl = "https://archive.org/download/Yasser_Dosari_Azan_HQ/Yasser_Dosari_Azan_HQ.mp3"
        ),
        OnlineMuezzinItem(
            id = "morocco_marush",
            titleArabic = "أذان المملكة المغربية (كمال المرعوش)",
            titleEnglish = "Morocco - Kamal Al-Marush",
            location = "المغرب 🇲🇦",
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Kamal_Muhammad_Al_Marush_2_-_Morocco_(%D9%83%D9%85%D8%A7%D9%84_%D9%85%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D9%85%D8%B1%D8%B9%D9%88%D8%B4_-_%D8%A7%D9%84%D9%85%D8%BA%D8%B1%D8%A8).mp3"
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
                // Also save under voice title for name matching
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
