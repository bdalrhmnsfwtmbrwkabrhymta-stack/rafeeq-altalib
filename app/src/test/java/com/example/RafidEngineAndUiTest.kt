package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hawas.study.rafid.data.BlockCategory
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.RafidMediaStoreSaver
import com.hawas.study.rafid.engine.UniversalLinkResolver
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidLanguageMode
import com.hawas.study.rafid.models.RafidMediaCategory
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.ui.screens.RafidBrowserScreen
import com.hawas.study.rafid.ui.screens.RafidDownloaderHomeScreen
import com.hawas.study.rafid.ui.screens.RafidProtectionScreen
import com.hawas.study.rafid.ui.screens.RafidQueuesScreen
import com.hawas.study.rafid.ui.screens.SmartDownloadSuccessContent
import com.hawas.study.rafid.ui.theme.RafidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RafidEngineAndUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shieldExtractsUrlsClassifiesAdsTrackersMalwareAndRespectsExcludedDomains() {
        val messyShare = "شوف المحاضرة دي جامدة جداً\nhttps://youtu.be/dQw4w9WgXcQ?si=test123! شكراً"
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=test123",
            AdAndTrackerShield.extractFirstUrl(messyShare)
        )
        assertEquals(RafidPlatform.YOUTUBE, AdAndTrackerShield.detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(RafidPlatform.PINTEREST, AdAndTrackerShield.detectPlatform("https://pin.it/abc123"))
        assertEquals(RafidPlatform.TIKTOK, AdAndTrackerShield.detectPlatform("https://vm.tiktok.com/ZM123/"))

        val activeConfig = RafidConfigState(blockAdsAndTrackers = true, blockMalwareSites = true)
        assertEquals(
            BlockCategory.AD,
            AdAndTrackerShield.classifyBlockedRequest(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                "https://www.example.org",
                activeConfig,
                emptySet()
            )
        )
        assertEquals(
            BlockCategory.TRACKER,
            AdAndTrackerShield.classifyBlockedRequest(
                "https://www.google-analytics.com/collect",
                "https://www.example.org",
                activeConfig,
                emptySet()
            )
        )
        assertEquals(
            BlockCategory.MALWARE,
            AdAndTrackerShield.classifyBlockedRequest(
                "https://coinhive.com/lib/coinhive.min.js",
                "https://www.example.org",
                activeConfig,
                emptySet()
            )
        )
        // Excluded site disables blocking cleanly
        assertNull(
            AdAndTrackerShield.classifyBlockedRequest(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                "https://trusted-edu.org",
                activeConfig,
                setOf("trusted-edu.org")
            )
        )
    }

    @Test
    fun linkResolverFollowsHttpRedirectsWithoutExternalNetwork() {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.startsWith("https://pin.it/") -> Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://www.pinterest.com/pin/9988776655/")
                        .body("".toResponseBody(null))
                        .build()
                    else -> Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("<html></html>".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val resolver = UniversalLinkResolver(mockClient)
        val result = resolver.resolve("Check this pin: https://pin.it/xyz999")
        assertEquals("https://pin.it/xyz999", result.originalUrl)
        assertEquals("https://www.pinterest.com/pin/9988776655/", result.resolvedUrl)
        assertEquals(1, result.redirectHops)
    }

    @Test
    fun extractorParsesYouTubeVideoAndPlaylistPinterestAndTikTokAccurately() {
        val extractor = UniversalMediaExtractor(OkHttpClient())

        // 1. YouTube player JSON (adaptive 1080p + 144p + muxed 360p + audio)
        val ytJson = JSONObject(
            """
            {
              "videoDetails": {
                "title": "شرح التفاضل والتكامل - المحاضرة الأولى",
                "author": "أكاديمية الفيزياء",
                "lengthSeconds": "1540"
              },
              "streamingData": {
                "formats": [
                  { "itag": 18, "mimeType": "video/mp4; codecs=\"avc1\"", "height": 360, "contentLength": "15000000", "url": "https://cdn.example.com/360.mp4" }
                ],
                "adaptiveFormats": [
                  { "itag": 137, "mimeType": "video/mp4; codecs=\"avc1\"", "height": 1080, "contentLength": "52000000", "url": "https://cdn.example.com/1080.mp4" },
                  { "itag": 160, "mimeType": "video/mp4; codecs=\"avc1\"", "height": 144, "contentLength": "4000000", "url": "https://cdn.example.com/144.mp4" },
                  { "itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a\"", "bitrate": 128000, "contentLength": "8000000", "url": "https://cdn.example.com/audio.m4a" }
                ]
              }
            }
            """.trimIndent()
        )
        val ytMeta = extractor.parseYouTubePlayerJson(
            originalUrl = "https://youtu.be/abc12345678",
            resolvedUrl = "https://www.youtube.com/watch?v=abc12345678",
            videoId = "abc12345678",
            playerJson = ytJson
        )
        assertEquals("شرح التفاضل والتكامل - المحاضرة الأولى", ytMeta.title)
        val opt1080 = ytMeta.formats.first { it.label == "1080p" }
        assertTrue(opt1080.isMuxRequired)
        assertEquals(60000000L, opt1080.estimatedSizeBytes)
        val opt144 = ytMeta.formats.first { it.label == "144p" }
        assertEquals("أقل استهلاكاً للبيانات", opt144.badgeTagAr)
        assertTrue(ytMeta.formats.any { it.isAudioOnly && it.containerExt == "m4a" })

        // 2. YouTube Playlist JSON
        val playlistJson = JSONObject(
            """
            {
              "metadata": { "playlistMetadataRenderer": { "title": "دورة الكيمياء العضوية كاملة" } },
              "contents": {
                "items": [
                  { "playlistVideoRenderer": { "videoId": "vid1", "title": { "simpleText": "الدرس الأول" }, "lengthSeconds": "600" } },
                  { "playlistVideoRenderer": { "videoId": "vid2", "title": { "simpleText": "الدرس الثاني" }, "lengthSeconds": "720" } }
                ]
              }
            }
            """.trimIndent()
        )
        val playlistMeta = extractor.parseYouTubePlaylistJson(
            originalUrl = "https://www.youtube.com/playlist?list=PL_STUDY",
            resolvedUrl = "https://www.youtube.com/playlist?list=PL_STUDY",
            listId = "PL_STUDY",
            initialData = playlistJson
        )
        assertTrue(playlistMeta.isPlaylist)
        assertEquals(2, playlistMeta.playlistItems.size)
        assertTrue(playlistMeta.formats.all { it.isPlaylistBundle })

        // 3. Pinterest HTML video extraction
        val pinHtml = """
            <html><head>
            <meta property="og:title" content="ملخص قوانين الفيزياء" />
            <meta property="og:image" content="https://i.pinimg.com/originals/aa/bb/cc.jpg" />
            </head><body>
            <script>{"url":"https://v1.pinimg.com/videos/mc/720p/aa/bb/physics.mp4"}</script>
            </body></html>
        """.trimIndent()
        val pinMeta = extractor.parsePinterestHtml(
            originalUrl = "https://pin.it/123",
            resolvedUrl = "https://www.pinterest.com/pin/123/",
            html = pinHtml
        )
        assertEquals(RafidPlatform.PINTEREST, pinMeta.platform)
        assertTrue(pinMeta.formats.first().videoUrl!!.endsWith("physics.mp4"))

        // 4. TikTok watermark-free JSON extraction
        val ttHtml = """
            <html><body>
            <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
            {"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{
              "desc":"تجربة كيميائية مذهلة #دراسة",
              "author":{"nickname":"مختبر العلوم"},
              "video":{"duration":45,"height":1080,"playAddr":"https://v16.tiktokcdn.com/nowm.mp4","cover":"https://p16.tiktokcdn.com/cover.jpg"},
              "music":{"playUrl":"https://sf16.tiktokcdn.com/audio.mp3"}
            }}}}}
            </script></body></html>
        """.trimIndent()
        val ttMeta = extractor.parseTikTokHtml(
            originalUrl = "https://www.tiktok.com/@sci/video/1",
            resolvedUrl = "https://www.tiktok.com/@sci/video/1",
            html = ttHtml
        )
        assertEquals(RafidPlatform.TIKTOK, ttMeta.platform)
        assertTrue(ttMeta.formats.any { !it.isAudioOnly && it.videoUrl == "https://v16.tiktokcdn.com/nowm.mp4" })
        assertTrue(ttMeta.formats.any { it.isAudioOnly && it.videoUrl == "https://sf16.tiktokcdn.com/audio.mp3" })
    }

    @Test
    fun encryptedOrProtectedLinksReturnEightVideoQualitiesAndAudioOptionsWithoutErrors() = runBlocking {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        val extractor = UniversalMediaExtractor(failingClient)

        val ytRes = extractor.extract("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(ytRes.isSuccess)
        val ytMeta = ytRes.getOrThrow()
        assertTrue(ytMeta.title.isNotBlank())
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ytMeta.thumbnailUrl)
        val videoQualities = ytMeta.formats.filter { !it.isAudioOnly }.map { it.label }
        assertEquals(listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p"), videoQualities)
        assertEquals("الأوضح", ytMeta.formats.first { it.label == "2160p" }.badgeTagAr)
        assertEquals("أقل استهلاكاً للبيانات", ytMeta.formats.first { it.label == "144p" }.badgeTagAr)
        assertTrue(ytMeta.formats.any { it.isAudioOnly })

        val pinRes = extractor.extract("https://www.pinterest.com/pin/123456789/")
        assertTrue(pinRes.isSuccess)
        assertEquals(8, pinRes.getOrThrow().formats.count { !it.isAudioOnly })
    }

    @Test
    fun mediaStoreSaverBuildsStudyAndMediaFoldersWithSafeFilenames() {
        assertEquals(
            "Download/Rafid/Study",
            RafidMediaStoreSaver.buildDisplaySubfolder(RafidMediaCategory.VIDEO, saveToStudyFolder = true)
        )
        assertEquals(
            "Download/Rafid/Audio",
            RafidMediaStoreSaver.buildDisplaySubfolder(RafidMediaCategory.AUDIO, saveToStudyFolder = false)
        )
        val sanitized = RafidMediaStoreSaver.sanitizeFileName("شرح: الدرس/الأول*؟<مهم>", "mp4")
        assertFalse(sanitized.contains(':'))
        assertFalse(sanitized.contains('/'))
        assertTrue(sanitized.endsWith(".mp4"))
    }

    @Test
    fun rafidRoomDatabaseStartsWithZeroFakeDownloadsAndSeedsSevenDefaultBookmarks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RafidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rafidDao()
            assertTrue(dao.observeAllDownloads().first().isEmpty())
            RafidDatabase.ensureDefaultsSeeded(dao)
            val bookmarks = dao.observeBookmarks().first()
            assertEquals(7, bookmarks.size)
            assertTrue(bookmarks.any { it.platformKey == "PINTEREST" })
            assertTrue(bookmarks.any { it.platformKey == "YOUTUBE" })
            assertTrue(bookmarks.any { it.platformKey == "TIKTOK" })

            val sampleDownload = RafidDownloadEntity(
                id = "dl_1",
                originalUrl = "https://youtu.be/test",
                resolvedUrl = "https://www.youtube.com/watch?v=test",
                videoStreamUrl = "https://cdn.example.com/video.mp4",
                title = "محاضرة الأحياء 1",
                sourceDomain = "youtube.com",
                platformName = RafidPlatform.YOUTUBE.name,
                qualityLabel = "720p",
                containerExt = "mp4",
                mimeType = "video/mp4",
                categoryName = RafidMediaCategory.VIDEO.name,
                status = RafidDownloadStatus.COMPLETED.name,
                totalBytes = 12_500_000L,
                downloadedBytes = 12_500_000L
            )
            dao.upsertDownload(sampleDownload)
            assertNotNull(dao.getDownloadById("dl_1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun rafidHomeRendersEmptyStateAndTogglesDarkMode() {
        var darkToggled = false
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                Box(modifier = Modifier.size(width = 400.dp, height = 840.dp)) {
                    RafidDownloaderHomeScreen(
                        downloads = emptyList(),
                        config = RafidConfigState(),
                        showSearchBar = false,
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onBackToApp = {},
                        onToggleDarkMode = { darkToggled = true },
                        onOpenLanguageSheet = {},
                        onOpenNewDownloadSheet = {},
                        onOpenClipboardAction = {},
                        onOpenPlaylists = {},
                        onOpenQueues = {},
                        onOpenProtection = {},
                        onOpenBrowser = {},
                        onPauseDownload = {},
                        onResumeDownload = {},
                        onDeleteDownload = {},
                        onOpenCompletedFile = {},
                        onShowItemInfo = {},
                        onClearCompleted = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rafid_brand_title")
            .assertIsDisplayed()
            .assertTextContains("الرسالة")
        composeTestRule.onNodeWithTag("rafid_btn_language").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rafid_welcome_title").assertTextContains("أَهلاً بك، ما يدخل هاتفك يبقى معك")
        composeTestRule.onNodeWithText("لا شيء يُحمَّل الآن").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shortcut_clipboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shortcut_playlists").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shortcut_queues").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shortcut_protection").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rafid_btn_dark_mode").performClick()
        assertTrue(darkToggled)
    }

    @Test
    fun smartSheetSwitchesTabsAndTriggersStudyFolderDownload() {
        var startedFormatId: String? = null
        var studyFlagCaptured = false
        val meta = ExtractedMediaMetadata(
            originalUrl = "https://youtu.be/test",
            resolvedUrl = "https://www.youtube.com/watch?v=test",
            title = "شرح الهندسة الفراغية",
            authorOrSource = "منصة الدرس",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            formats = listOf(
                MediaFormatOption("v_1080", "1080p", "mp4", "video/mp4", 1080, estimatedSizeBytes = 100_000_000L),
                MediaFormatOption("v_144", "144p", "mp4", "video/mp4", 144, estimatedSizeBytes = 8_000_000L, badgeTagAr = "أقل استهلاكاً للبيانات"),
                MediaFormatOption("a_m4a", "M4A", "m4a", "audio/mp4", isAudioOnly = true, estimatedSizeBytes = 6_000_000L)
            )
        )
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                SmartDownloadSuccessContent(
                    metadata = meta,
                    onStartDownload = { _, fmt, study ->
                        startedFormatId = fmt.id
                        studyFlagCaptured = study
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("smart_sheet_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_tab_audio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_save_study_folder").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_start_fast_download").performScrollTo().performClick()
        assertEquals("a_m4a", startedFormatId)
        assertTrue(studyFlagCaptured)
    }

    @Test
    fun queuesScreenUpdatesConcurrencyStepperCleanly() {
        var deltaCaptured = 0
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                Box(modifier = Modifier.size(width = 400.dp, height = 840.dp)) {
                    RafidQueuesScreen(
                        config = RafidConfigState(maxConcurrentDownloads = 5, blockedAdsCount = 12),
                        downloads = emptyList(),
                        onBack = {},
                        onAdjustMaxConcurrent = { deltaCaptured = it },
                        onSetWifiOnly = {},
                        onSetScheduledStart = { _, _, _ -> },
                        onToggleQueuePaused = {},
                        onSetBlockAdsAndTrackers = {},
                        onSetSmartEngineEnabled = {},
                        onOpenProtection = {},
                        onPauseDownload = {},
                        onResumeDownload = {},
                        onDeleteDownload = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queues_concurrent_count").assertTextContains("5")
        composeTestRule.onNodeWithTag("queues_btn_plus").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, deltaCaptured)
    }

    @Test
    fun protectionScreenShowsRealCountsAndTogglesMalwareSwitch() {
        var malwareToggledTo: Boolean? = null
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                Box(modifier = Modifier.size(width = 400.dp, height = 840.dp)) {
                    RafidProtectionScreen(
                        config = RafidConfigState(
                            blockedAdsCount = 10,
                            blockedTrackersCount = 4,
                            blockedMalwareCount = 1
                        ),
                        excludedDomains = emptyList(),
                        onBack = {},
                        onSetBlockAdsAndTrackers = {},
                        onSetBlockMalwareSites = { malwareToggledTo = it },
                        onRefreshRulesNow = {},
                        onAddExcludedDomain = {},
                        onRemoveExcludedDomain = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("protection_total_blocked").assertTextContains("15 طلب")
        composeTestRule.onNodeWithTag("protection_switch_malware").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, malwareToggledTo)
    }

    @Test
    fun browserScreenShowsAllPlatformTilesAndAddButton() {
        composeTestRule.setContent {
            RafidTheme(darkOverride = false, languageMode = RafidLanguageMode.ARABIC) {
                Box(modifier = Modifier.size(width = 400.dp, height = 840.dp)) {
                    RafidBrowserScreen(
                        bookmarks = RafidDatabase.DEFAULT_PLATFORM_BOOKMARKS,
                        config = RafidConfigState(),
                        excludedDomains = emptySet(),
                        isIncognito = false,
                        activePageUrl = null,
                        sniffedMedia = emptyList(),
                        onToggleIncognito = {},
                        onPageChanged = { _, _ -> },
                        onMediaSniffed = {},
                        onRequestExtractCurrentOrSniffed = { _, _ -> },
                        onRecordBlocked = {},
                        onAddCustomBookmark = { _, _ -> },
                        onDeleteCustomBookmark = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_tile_YOUTUBE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_tile_PINTEREST").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_tile_TIKTOK").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_tile_ADD").assertIsDisplayed()
    }
}
