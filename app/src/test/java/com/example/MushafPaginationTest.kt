package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.quran.MushafPagePagination
import com.example.data.quran.QuranRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MushafPaginationTest {

    @Test
    fun paginationCoversExact604SequentialPagesAndAll6236Ayahs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = QuranRepository(context)
        val surahs = repo.getAllSurahs()
        assertEquals(114, surahs.size)

        val pages = MushafPagePagination.buildPages(surahs, repo.allJuzList)
        assertEquals(604, pages.size)

        val seenKeys = HashSet<String>(6240)
        var totalAyahs = 0

        pages.forEachIndexed { idx, page ->
            val expectedPageNum = idx + 1
            assertEquals("Page numbering must be sequential", expectedPageNum, page.pageNumber)
            val pageAyahs = page.segments.flatMap { it.ayahs }
            assertTrue("Page $expectedPageNum must not be empty", pageAyahs.isNotEmpty())
            pageAyahs.forEach { ayah ->
                val key = "${ayah.surahNumber}:${ayah.numberInSurah}"
                assertTrue("Duplicate ayah $key on page $expectedPageNum", seenKeys.add(key))
                totalAyahs++
            }
        }

        assertEquals(6236, totalAyahs)

        // Verify Page 1 is exclusively complete Al-Fatihah (7 ayahs, no duplicate Bismillah banner)
        val page1 = pages[0]
        assertEquals(1, page1.segments.size)
        val fatihahSeg = page1.segments[0]
        assertEquals(1, fatihahSeg.surahInfo.number)
        assertTrue(fatihahSeg.startsSurah)
        assertFalse(fatihahSeg.showBismillahHeader)
        assertEquals(7, fatihahSeg.ayahs.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), fatihahSeg.ayahs.map { it.numberInSurah })

        // Verify Page 2 starts Al-Baqarah ayah 1 with Bismillah header
        val page2 = pages[1]
        assertEquals(2, page2.segments.first().surahInfo.number)
        assertTrue(page2.segments.first().startsSurah)
        assertTrue(page2.segments.first().showBismillahHeader)
        assertEquals(1, page2.segments.first().ayahs.first().numberInSurah)

        // Verify every Surah starts on its authentic Madinah page
        repo.allSurahList.forEach { surahInfo ->
            val foundPage = MushafPagePagination.findPageForSurah(surahInfo.number, pages, repo.allSurahList)
            assertEquals("Surah ${surahInfo.number} must start on page ${surahInfo.page}", surahInfo.page, foundPage)
        }
    }
}
