package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidLanguageMode
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.ui.screens.GreenDownloadingBadgePill
import com.hawas.study.rafid.ui.screens.SmartDownloadSuccessContent
import com.hawas.study.rafid.ui.screens.resolveDownloadItemThumbnail
import com.hawas.study.rafid.ui.theme.RafidTheme
import com.hawas.study.rafid.worker.DownloadQueueManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadUpgradesUiAndQueueTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playlistSheetExposesAllFullAndLowQualityTiersDownTo144pAnd4K() {
        val playlistMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.youtube.com/playlist?list=PL_FULL",
            resolvedUrl = "https://www.youtube.com/playlist?list=PL_FULL",
            title = "سلسلة المراجعة الشاملة",
            authorOrSource = "أكاديمية التفوق",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            playlistItems = listOf(
                PlaylistEntryItem(1, "dQw4w9WgXcQ", "الدرس الأول", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", 420)
            ),
            isPlaylist = true
        )

        var chosenHeight: Int? = null
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                SmartDownloadSuccessContent(
                    metadata = playlistMeta,
                    onStartDownload = { _, fmt, _ -> chosenHeight = fmt.resolutionHeight }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("playlist_quality_picker").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("zajil_option_pl_2160").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_1440").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_1080").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_720").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_480").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_360").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_240").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_144").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_audio").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("zajil_option_pl_audio_mp3").performScrollTo().assertIsDisplayed()

        // Choose 240p low-bandwidth option
        composeTestRule.onNodeWithTag("zajil_option_pl_240").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_start_fast_download").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(240, chosenHeight)
    }

    @Test
    fun greenDownloadingBadgeRendersAnimatedTextAndYouTubeThumbnailFallbackResolves() {
        val sampleEntity = RafidDownloadEntity(
            id = "dl_yt_1",
            originalUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            resolvedUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            videoStreamUrl = "",
            title = "شرح كامل",
            sourceDomain = "youtube.com",
            platformName = "YOUTUBE",
            thumbnailUrl = null,
            qualityLabel = "720p",
            containerExt = "mp4",
            mimeType = "video/mp4",
            categoryName = "فيديو",
            status = RafidDownloadStatus.DOWNLOADING.name
        )

        val thumb = resolveDownloadItemThumbnail(sampleEntity)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", thumb)

        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                GreenDownloadingBadgePill(
                    isEnglish = false,
                    onDarkHero = false,
                    extraSuffix = "45%",
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("جاري التحميل... • 45%").assertIsDisplayed()
    }

    @Test
    fun queueManagerRecoversOrphanedDownloadingRowAndSchedulesQueuedPlaylistItems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                context,
                Configuration.Builder().build()
            )
        }
        val dao = RafidDatabase.getInstance(context).rafidDao()
        val orphanId = "orphan_${System.currentTimeMillis()}"
        val queuedId = "queued_${System.currentTimeMillis()}"

        dao.upsertDownloads(
            listOf(
                RafidDownloadEntity(
                    id = orphanId,
                    originalUrl = "https://www.youtube.com/watch?v=abc12345",
                    resolvedUrl = "https://www.youtube.com/watch?v=abc12345",
                    videoStreamUrl = "",
                    title = "فيديو عالق سابقاً",
                    sourceDomain = "youtube.com",
                    platformName = "YOUTUBE",
                    qualityLabel = "720p",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    categoryName = "فيديو",
                    status = RafidDownloadStatus.DOWNLOADING.name,
                    createdAtMillis = 1000L
                ),
                RafidDownloadEntity(
                    id = queuedId,
                    originalUrl = "https://www.youtube.com/watch?v=def67890",
                    resolvedUrl = "https://www.youtube.com/watch?v=def67890",
                    videoStreamUrl = "",
                    title = "فيديو قائمة التشغيل الأول",
                    sourceDomain = "youtube.com",
                    platformName = "YOUTUBE",
                    qualityLabel = "720p",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    categoryName = "فيديو",
                    status = RafidDownloadStatus.QUEUED.name,
                    isPlaylistItem = true,
                    createdAtMillis = 2000L
                )
            )
        )

        DownloadQueueManager.triggerQueueProcessing(context)

        val orphanAfter = dao.getDownloadById(orphanId)
        assertNotEquals(RafidDownloadStatus.FAILED.name, orphanAfter?.status)
        assertTrue(orphanAfter?.status == RafidDownloadStatus.QUEUED.name)

        // Verify WorkManager now has queued unique work for the recovered orphan item
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(com.hawas.study.rafid.worker.RafidDownloadWorker.uniqueWorkName(orphanId))
            .get()
        assertTrue(workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })

        // Clean up test rows and cancel test work
        WorkManager.getInstance(context).cancelUniqueWork(com.hawas.study.rafid.worker.RafidDownloadWorker.uniqueWorkName(orphanId))
        WorkManager.getInstance(context).cancelUniqueWork(com.hawas.study.rafid.worker.RafidDownloadWorker.uniqueWorkName(queuedId))
        dao.deleteDownloadById(orphanId)
        dao.deleteDownloadById(queuedId)
    }
}
