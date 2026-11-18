package com.example.download

import com.example.download.engine.ParallelChunkDownloader
import com.example.download.engine.StorageHelper
import com.example.download.extract.LinkExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDownloadEngineTest {

    @Test
    fun chunkCount_respectsRangeAndMinOneMegabytePerChunk() {
        assertEquals(1, ParallelChunkDownloader.computeOptimalChunkCount(100L * 1024L * 1024L, supportsRange = false, requestedChunks = 16))
        assertEquals(1, ParallelChunkDownloader.computeOptimalChunkCount(1024L * 1024L, supportsRange = true, requestedChunks = 12))
        assertEquals(5, ParallelChunkDownloader.computeOptimalChunkCount(5L * 1024L * 1024L, supportsRange = true, requestedChunks = 12))
        assertEquals(16, ParallelChunkDownloader.computeOptimalChunkCount(200L * 1024L * 1024L, supportsRange = true, requestedChunks = 16))
        assertEquals(8, ParallelChunkDownloader.computeOptimalChunkCount(200L * 1024L * 1024L, supportsRange = true, requestedChunks = 4))
    }

    @Test
    fun buildChunkEntities_coversExactByteRangeWithoutGapsOrOverlap() {
        val total = 25L * 1024L * 1024L + 123L
        val chunks = ParallelChunkDownloader.buildChunkEntities("id1", total, 12, "\"abc\"")
        assertEquals(12, chunks.size)
        assertEquals(0L, chunks.first().startByte)
        assertEquals(total - 1L, chunks.last().endByte)
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].endByte + 1L, chunks[i].startByte)
        }
        val summed = chunks.sumOf { it.endByte - it.startByte + 1L }
        assertEquals(total, summed)
    }

    @Test
    fun sanitizeFileName_removesIllegalCharsAndKeepsArabic() {
        val cleaned = StorageHelper.sanitizeFileName("  درس مهم جداً: كيف تحفظ؟ /\\*|<>  ", "MP4")
        assertTrue(cleaned.endsWith(".mp4"))
        assertFalse(cleaned.contains(":"))
        assertFalse(cleaned.contains("/"))
        assertFalse(cleaned.contains("?"))
        assertTrue(cleaned.contains("درس"))
    }

    @Test
    fun urlExtraction_andPlatformDetection_handleSharedTextCleanly() {
        val shared = "شاهد هذا الفيديو الرائع: https://vm.tiktok.com/ZM8abc123/ 👍"
        val extracted = LinkExtractor.extractFirstHttpUrl(shared)
        assertEquals("https://vm.tiktok.com/ZM8abc123/", extracted)
        assertEquals("تيك توك", LinkExtractor.detectPlatformName(extracted!!))
        assertEquals("يوتيوب", LinkExtractor.detectPlatformName("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("بنترست", LinkExtractor.detectPlatformName("https://pin.it/12345"))
    }
}
