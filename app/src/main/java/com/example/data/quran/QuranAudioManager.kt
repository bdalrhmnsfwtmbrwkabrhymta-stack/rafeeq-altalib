package com.example.data.quran

import android.content.Context
import java.io.File

class QuranAudioManager(private val context: Context) {

    fun getDownloadedSurahFile(reciterId: String, surahNumber: Int): File? {
        val dir = File(context.filesDir, "quran_audio/$reciterId")
        if (!dir.exists()) return null
        val file = File(dir, "surah_${String.format("%03d", surahNumber)}.mp3")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun saveDownloadedSurah(reciterId: String, surahNumber: Int, bytes: ByteArray): File {
        val dir = File(context.filesDir, "quran_audio/$reciterId").apply { mkdirs() }
        val file = File(dir, "surah_${String.format("%03d", surahNumber)}.mp3")
        file.writeBytes(bytes)
        return file
    }

    fun deleteDownloadedSurah(reciterId: String, surahNumber: Int): Boolean {
        val file = getDownloadedSurahFile(reciterId, surahNumber) ?: return false
        return file.delete()
    }
}
