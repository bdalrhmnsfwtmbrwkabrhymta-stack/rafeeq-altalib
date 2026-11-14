package com.example.data.quran

import androidx.annotation.RawRes
import com.example.R

data class SurahInfo(
    val number: Int,
    val nameAr: String,
    val nameEn: String,
    val numberOfAyahs: Int,
    val revelationType: String, // "مكية" or "مدنية"
    val juz: Int,
    val page: Int
)

data class Ayah(
    val numberInSurah: Int,
    val text: String,
    val surahNumber: Int
)

data class SurahDetail(
    val info: SurahInfo,
    val ayahs: List<Ayah>
)

data class JuzAudio(
    val juzNumber: Int,
    val titleAr: String,
    val titleEn: String,
    @RawRes val rawResId: Int? = null,
    val surahsDesc: String,
    val audioUrl: String? = null
)

data class QuranBookmark(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class JuzInfo(
    val number: Int,
    val nameAr: String,
    val nameEn: String,
    val startSurahNumber: Int,
    val startAyahNumber: Int,
    val endSurahNumber: Int,
    val endAyahNumber: Int,
    val descriptionAr: String,
    val descriptionEn: String,
    val startPage: Int
)

data class JuzSection(
    val surahInfo: SurahInfo,
    val ayahs: List<Ayah>
)

data class JuzDetail(
    val info: JuzInfo,
    val sections: List<JuzSection>
)

data class DownloadedSurahItem(
    val reciterId: String,
    val surahNumber: Int,
    val surahNameAr: String,
    val reciterNameAr: String,
    val filePath: String,
    val formattedSize: String = "5.2 MB"
)

data class ImportedQuranAudio(
    val id: String,
    val title: String,
    val surahNumber: Int? = null,
    val surahNameAr: String = "",
    val reciterName: String = "",
    val filePath: String,
    val formattedSize: String = "4.0 MB"
)
