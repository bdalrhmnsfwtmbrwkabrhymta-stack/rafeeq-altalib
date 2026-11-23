package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hawas.study.rafid.RafidViewModel
import com.hawas.study.rafid.engine.UniversalLinkResolver
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidLanguageMode
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.ui.screens.SmartDownloadSuccessContent
import com.hawas.study.rafid.ui.theme.RafidTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZajilPlaylistAndTikTokUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playlistSheetSelectsVideosQualityDubbedAudioAndStudyFolder() {
        var capturedItemsCount = -1
        var capturedFormat: MediaFormatOption? = null
        var capturedStudyFolder = false
        var dismissed = false

        val playlistMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.youtube.com/playlist?list=PL_AI",
            resolvedUrl = "https://www.youtube.com/playlist?list=PL_AI",
            title = "أدوات الذكاء الاصطناعي - AI tools",
            authorOrSource = "Python Arabic Community",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            playlistItems = listOf(
                PlaylistEntryItem(1, "v1", "8 Free AI Models", "https://www.youtube.com/watch?v=v1", 1356),
                PlaylistEntryItem(2, "v2", "15 times cheaper than Claude", "https://www.youtube.com/watch?v=v2", 723),
                PlaylistEntryItem(3, "v3", "Autonomous AI Software", "https://www.youtube.com/watch?v=v3", 715)
            ),
            isPlaylist = true
        )

        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                SmartDownloadSuccessContent(
                    metadata = playlistMeta,
                    onStartDownload = { meta, fmt, study ->
                        capturedItemsCount = meta.playlistItems.size
                        capturedFormat = fmt
                        capturedStudyFolder = study
                    },
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("smart_sheet_title").assertTextContains("أدوات الذكاء الاصطناعي", substring = true)
        composeTestRule.onNodeWithTag("playlist_selected_count").assertTextContains("حُدّد 3 فيديو")
        composeTestRule.onNodeWithTag("btn_start_fast_download").assertTextContains("تحميل المحدد (3)")

        // Uncheck 1 item -> 2 remaining
        composeTestRule.onNodeWithTag("playlist_item_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlist_selected_count").assertTextContains("حُدّد 2 فيديو")

        // Pick Quality -> 720p
        composeTestRule.onNodeWithTag("playlist_quality_picker").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zajil_option_pl_720").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("الأفضل حتى 720p").assertIsDisplayed()

        // Pick Audio -> Arabic dubbed if available
        composeTestRule.onNodeWithTag("playlist_audio_picker").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zajil_option_dub_ar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("مدبلج بالعربية إن وُجد").assertIsDisplayed()

        // Enable Study folder toggle & click download
        composeTestRule.onNodeWithTag("switch_save_study_folder").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_start_fast_download").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, capturedItemsCount)
        assertEquals(720, capturedFormat?.resolutionHeight)
        assertTrue(capturedFormat?.isAutoDubbed == true)
        assertTrue(capturedStudyFolder)

        // Cancel button triggers onDismiss
        composeTestRule.onNodeWithTag("playlist_btn_cancel").performScrollTo().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun tiktokSheetSupportsVideoAudioTabsAndShowsWarningWhenDubbedAudioUnavailable() {
        var startedFormatId: String? = null
        val tiktokMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.tiktok.com/@creator/video/123",
            resolvedUrl = "https://www.tiktok.com/@creator/video/123",
            title = "شرح سريع لتقنية البرمجة",
            authorOrSource = "creator",
            sourceDomain = "tiktok.com",
            platform = RafidPlatform.TIKTOK,
            durationSeconds = 20,
            formats = listOf(
                MediaFormatOption("v_540", "540p", "mp4", "video/mp4", 540, isAudioOnly = false, isAutoDubbed = false),
                MediaFormatOption("a_m4a", "M4A", "m4a", "audio/mp4", isAudioOnly = true, isAutoDubbed = false)
            )
        )

        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                SmartDownloadSuccessContent(
                    metadata = tiktokMeta,
                    onStartDownload = { _, fmt, _ -> startedFormatId = fmt.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Audio Only tab
        composeTestRule.onNodeWithTag("smart_tab_audio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("format_option_a_m4a").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_start_fast_download").assertIsEnabled()

        // Select dubbed Arabic audio track -> unavailable warning shown & CTA disabled
        composeTestRule.onNodeWithTag("tiktok_audio_picker").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zajil_option_dub_ar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tiktok_audio_unavailable_warning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_start_fast_download").assertIsNotEnabled()

        // Switch back to Original audio & Video tab -> CTA re-enabled & downloads video
        composeTestRule.onNodeWithTag("tiktok_audio_picker").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zajil_option_orig").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("smart_tab_video").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_start_fast_download").assertIsEnabled().performClick()
        assertEquals("v_540", startedFormatId)
    }

    @Test
    fun playlistEntityBuilderHonors480pLimitAndArabicDubbedExpression() = runBlocking {
        val extractor = UniversalMediaExtractor(
            httpClient = OkHttpClient(),
            linkResolver = UniversalLinkResolver(OkHttpClient())
        )
        val playlistMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.youtube.com/playlist?list=PL_TEST",
            resolvedUrl = "https://www.youtube.com/playlist?list=PL_TEST",
            title = "اختبار الجودة",
            authorOrSource = "قناة",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            playlistItems = listOf(
                PlaylistEntryItem(1, "v_fail", "محاضرة 1", "https://127.0.0.1:1/watch?v=v_fail", 100)
            ),
            isPlaylist = true
        )
        val bundle = MediaFormatOption(
            id = "pl_480_dub_ar",
            label = "الأفضل حتى 480p",
            containerExt = "mp4",
            mimeType = "video/mp4",
            resolutionHeight = 480,
            isAudioOnly = false,
            isPlaylistBundle = true,
            isAutoDubbed = true,
            isOriginalAudio = false,
            languageCode = "ar",
            languageLabel = "مدبلج بالعربية إن وُجد"
        )
        val entities = RafidViewModel.buildPlaylistDownloadEntities(
            metadata = playlistMeta,
            selectedFormat = bundle,
            saveToStudyFolder = false,
            extractor = extractor
        )
        assertEquals(1, entities.size)
        assertFalse(entities[0].isAudioOnly)
        assertTrue(entities[0].formatExpression!!.contains("height<=480"))
        assertTrue(entities[0].formatExpression!!.contains("language^=ar"))
        assertEquals("ar", entities[0].audioLanguage)
    }

    @Test
    fun tiktokSheetSortsBySizeAndShowsStandardCleanQualityLabels() {
        var startedFormatId: String? = null
        val tiktokMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.tiktok.com/@creator/video/999",
            resolvedUrl = "https://www.tiktok.com/@creator/video/999",
            title = "مراجعة نهائية",
            authorOrSource = "creator",
            sourceDomain = "tiktok.com",
            platform = RafidPlatform.TIKTOK,
            durationSeconds = 45,
            formats = listOf(
                MediaFormatOption(
                    id = "tt_small",
                    label = "576p MP4",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = 576,
                    estimatedSizeBytes = 3_200_000L,
                    isAudioOnly = false
                ),
                MediaFormatOption(
                    id = "tt_wm",
                    label = "1280p watermark",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = 1280,
                    estimatedSizeBytes = 15_000_000L,
                    videoUrl = "https://cdn.example.com/wmplay.mp4",
                    isAudioOnly = false
                ),
                MediaFormatOption(
                    id = "tt_hdplay",
                    label = "1024p MP4",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = 1024,
                    estimatedSizeBytes = 12_500_000L,
                    isAudioOnly = false
                ),
                MediaFormatOption(
                    id = "tt_play",
                    label = "720p MP4",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = 720,
                    estimatedSizeBytes = 6_800_000L,
                    isAudioOnly = false
                ),
                MediaFormatOption(
                    id = "a_m4a",
                    label = "M4A",
                    containerExt = "m4a",
                    mimeType = "audio/mp4",
                    estimatedSizeBytes = 900_000L,
                    isAudioOnly = true
                )
            )
        )

        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                SmartDownloadSuccessContent(
                    metadata = tiktokMeta,
                    onStartDownload = { _, fmt, _ -> startedFormatId = fmt.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1080p HD (بدون علامة مائية — الأوضح)").assertIsDisplayed()
        composeTestRule.onNodeWithText("720p (بدون علامة مائية)").assertIsDisplayed()
        composeTestRule.onNodeWithText("480p").assertIsDisplayed()
        composeTestRule.onNodeWithText("360p (توفير البيانات)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_start_fast_download").assertIsEnabled().performClick()
        assertEquals("tt_hdplay", startedFormatId)
    }
}
