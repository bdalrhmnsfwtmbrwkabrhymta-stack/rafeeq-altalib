package com.example.download.core

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.DownloadStateMachine
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.DirectAndStreamAdapter
import com.example.download.core.resolver.FacebookProviderAdapter
import com.example.download.core.resolver.InstagramProviderAdapter
import com.example.download.core.resolver.PinterestProviderAdapter
import com.example.download.core.resolver.TikTokProviderAdapter
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.core.resolver.XTwitterProviderAdapter
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.data.DownloadDatabase
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformAdaptersAndFsmTest {

    @Test
    fun finiteStateMachine_enforcesAll12StatesAndValidTransitions() {
        assertEquals(12, EngineDownloadState.entries.size)
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.QUEUED, EngineDownloadState.RESOLVING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.RESOLVING, EngineDownloadState.READY))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.READY, EngineDownloadState.DOWNLOADING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.DOWNLOADING, EngineDownloadState.PAUSING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.PAUSING, EngineDownloadState.PAUSED))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.DOWNLOADING, EngineDownloadState.RETRYING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.DOWNLOADING, EngineDownloadState.VERIFYING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.VERIFYING, EngineDownloadState.MERGING))
        assertTrue(DownloadStateMachine.canTransition(EngineDownloadState.MERGING, EngineDownloadState.COMPLETED))

        // Completed is terminal — cannot revert directly to DOWNLOADING
        assertFalse(DownloadStateMachine.canTransition(EngineDownloadState.COMPLETED, EngineDownloadState.DOWNLOADING))
        assertFalse(DownloadStateMachine.canTransition(EngineDownloadState.QUEUED, EngineDownloadState.COMPLETED))

        // Round-trip status mapping sanity
        EngineDownloadState.entries.forEach { state ->
            val legacy = state.toSmartDownloadStatus()
            val rafid = state.toRafidStatus()
            assertTrue(legacy.name.isNotBlank())
            assertTrue(rafid.name.isNotBlank())
        }
    }

    @Test
    fun downloadErrorMapper_coversHttpAndThrowableErrors() {
        assertTrue(DownloadErrorMapper.fromHttpStatus(401) is DownloadError.AuthenticationRequired)
        assertTrue(DownloadErrorMapper.fromHttpStatus(403) is DownloadError.ExpiredMediaURL)
        assertTrue(DownloadErrorMapper.fromHttpStatus(404) is DownloadError.PrivateContent)
        assertTrue(DownloadErrorMapper.fromHttpStatus(416) is DownloadError.InvalidRangeResponse)
        val http400 = DownloadErrorMapper.fromHttpStatus(400)
        assertTrue(http400 is DownloadError.HttpError && http400.suggestLoginOrBrowser)
        assertTrue(DownloadErrorMapper.fromThrowable(java.net.UnknownHostException("dns")) is DownloadError.NetworkUnavailable)
        assertTrue(DownloadErrorMapper.fromThrowable(java.net.SocketTimeoutException("read")) is DownloadError.NetworkTimeout)
    }

    @Test
    fun youtubeAdapter_handlesVideoShortsChannelPlaylistAndErrors() {
        val adapter = YouTubeProviderAdapter(OkHttpClient())
        assertEquals("dQw4w9WgXcQ", adapter.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?si=1"))
        assertTrue(adapter.isChannelUrl("https://www.youtube.com/@OpenCourseWare"))
        assertTrue(adapter.isChannelUrl("https://www.youtube.com/channel/UC12345"))

        val channelJson = JSONObject(
            """
            {
              "metadata": { "channelMetadataRenderer": { "title": "أكاديمية العلوم" } },
              "contents": {
                "tabRenderer": {
                  "content": {
                    "items": [
                      { "gridVideoRenderer": { "videoId": "vidA", "title": { "simpleText": "الدرس 1" }, "lengthSeconds": "300" } },
                      { "reelItemRenderer": { "videoId": "shortB", "headline": { "simpleText": "ملخص سريع" } } }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
        val chRes = adapter.parseChannelInitialData(
            originalUrl = "https://www.youtube.com/@ScienceAcademy",
            canonicalUrl = "https://www.youtube.com/@ScienceAcademy/videos",
            initialData = channelJson
        )
        assertTrue(chRes is ResolvedMediaResult.MediaCollection)
        chRes as ResolvedMediaResult.MediaCollection
        assertEquals(2, chRes.entries.size)

        val loginPlayer = JSONObject("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm"}}""")
        val loginRes = adapter.parsePlayerJsonResult("https://youtu.be/x", "https://youtube.com/watch?v=x", "x", loginPlayer)
        assertTrue((loginRes as ResolvedMediaResult.Failure).error is DownloadError.AuthenticationRequired)

        val paidPlayer = JSONObject("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Join this channel for members-only content"}}""")
        val paidRes = adapter.parsePlayerJsonResult("https://youtu.be/y", "https://youtube.com/watch?v=y", "y", paidPlayer)
        assertTrue((paidRes as ResolvedMediaResult.Failure).error is DownloadError.ProtectedContent)
    }

    @Test
    fun facebookInstagramTikTokXPinterestDirectAdapters_extractRealMediaOrRejectHtmlCleanly() = runBlocking {
        // 1. Facebook streams vs HTTP 400
        val fbAdapter = FacebookProviderAdapter(OkHttpClient())
        val fbHtml = """<script>{"browser_native_hd_url":"https://video.fbcdn.net/hd.mp4","playable_url":"https://video.fbcdn.net/sd.mp4"}</script>"""
        val fbOk = fbAdapter.parseFacebookHtml("https://fb.watch/abc", "https://www.facebook.com/watch/?v=1", fbHtml)
        assertTrue(fbOk is ResolvedMediaResult.SingleMedia)
        assertEquals(1080, (fbOk as ResolvedMediaResult.SingleMedia).formats.first().resolutionHeight)

        val http400Client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("<html>Login or blocked</html>".toResponseBody(null))
                    .build()
            }.build()
        val fb400 = FacebookProviderAdapter(http400Client).resolve(
            "https://www.facebook.com/reel/123",
            ResolveRequest("https://www.facebook.com/reel/123")
        )
        assertTrue(fb400 is ResolvedMediaResult.Failure)
        assertTrue((fb400 as ResolvedMediaResult.Failure).error is DownloadError.HttpError)

        // 2. Instagram video + login wall
        val igAdapter = InstagramProviderAdapter(OkHttpClient())
        val igOk = igAdapter.parseInstagramHtml(
            "https://www.instagram.com/reel/xyz/",
            "https://www.instagram.com/reel/xyz/",
            """<meta property="og:video:secure_url" content="https://scontent.cdninstagram.com/v/clip.mp4"/>"""
        )
        assertTrue(igOk is ResolvedMediaResult.SingleMedia)
        val igWall = igAdapter.parseInstagramHtml(
            "https://www.instagram.com/p/priv/",
            "https://www.instagram.com/p/priv/",
            """<html><script>window._sharedData={"loginAndSignupPage":true};</script></html>"""
        )
        assertTrue((igWall as ResolvedMediaResult.Failure).error is DownloadError.AuthenticationRequired)

        // 3. X (Twitter) syndication JSON with variants + HLS
        val xAdapter = XTwitterProviderAdapter(OkHttpClient())
        val xSyn = xAdapter.parseSyndicationJson(
            "https://x.com/user/status/999",
            "https://x.com/user/status/999",
            """{"text":"درس مهم","user":{"name":"د. أحمد"},"mediaDetails":[{"media_url_https":"https://pbs.twimg.com/thumb.jpg","video_info":{"variants":[{"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/1080.mp4"},{"content_type":"application/x-mpegURL","url":"https://video.twimg.com/pl.m3u8"}]}}]}"""
        )!!
        assertEquals(2, xSyn.formats.size)
        assertTrue(xSyn.formats.any { it.streamUrl.endsWith("1080.mp4") })

        // 4. Pinterest video & image pin
        val pinAdapter = PinterestProviderAdapter(OkHttpClient())
        val pinImg = pinAdapter.parsePinterestHtml(
            "https://pin.it/img1",
            "https://www.pinterest.com/pin/1/",
            """<html><meta property="og:title" content="خريطة ذهنية"/><img src="https://i.pinimg.com/originals/11/22/mindmap.png"/></html>"""
        )
        assertTrue(pinImg is ResolvedMediaResult.SingleMedia)
        assertEquals("png", (pinImg as ResolvedMediaResult.SingleMedia).formats.first().containerExt)

        // 5. Direct adapter rejects HTML page without video URLs
        val resolver = UnifiedMediaResolver(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body("<html><body><p>Just an article</p></body></html>".toResponseBody(null))
                    .build()
            }.build()
        )
        val htmlRes = resolver.resolve(ResolveRequest("https://example.org/article", allowWebViewSniffer = false))
        assertTrue(htmlRes is ResolvedMediaResult.Failure)
        assertTrue((htmlRes as ResolvedMediaResult.Failure).error is DownloadError.UnsupportedMedia)
    }

    @Test
    fun roomMigration1To2_preservesExistingDownloadHistoryRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_downloads_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS smart_downloads (
                            id TEXT NOT NULL PRIMARY KEY,
                            originalUrl TEXT NOT NULL,
                            resolvedUrl TEXT NOT NULL,
                            audioUrl TEXT,
                            title TEXT NOT NULL,
                            thumbnailUrl TEXT,
                            platform TEXT NOT NULL,
                            qualityId TEXT NOT NULL,
                            qualityLabel TEXT NOT NULL,
                            formatId TEXT,
                            isAudioOnly INTEGER NOT NULL,
                            isHlsOrDash INTEGER NOT NULL,
                            needsMux INTEGER NOT NULL,
                            fileName TEXT NOT NULL,
                            mimeType TEXT NOT NULL,
                            tempPartPath TEXT NOT NULL,
                            outputUri TEXT,
                            outputDisplayPath TEXT NOT NULL,
                            totalBytes INTEGER NOT NULL,
                            downloadedBytes INTEGER NOT NULL,
                            activeThreads INTEGER NOT NULL,
                            etag TEXT,
                            lastModified TEXT,
                            referer TEXT,
                            headersJson TEXT,
                            status TEXT NOT NULL,
                            errorFriendlyMessage TEXT,
                            diagnosticLog TEXT,
                            durationSeconds INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            completedAt INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO smart_downloads (
                            id, originalUrl, resolvedUrl, title, platform, qualityId, qualityLabel,
                            isAudioOnly, isHlsOrDash, needsMux, fileName, mimeType, tempPartPath,
                            outputDisplayPath, totalBytes, downloadedBytes, activeThreads, status,
                            durationSeconds, createdAt, updatedAt
                        ) VALUES (
                            'hist_1', 'https://youtu.be/abc', 'https://cdn/v.mp4', 'محاضرة قديمة', 'يوتيوب',
                            '720p', '720p', 0, 0, 0, 'lecture.mp4', 'video/mp4', '/tmp/p.part',
                            'Download/HawasElDirasa/Video/lecture.mp4', 1000, 1000, 0, 'COMPLETED',
                            120, 100, 200
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val dbV1 = helper.writableDatabase
        DownloadDatabase.MIGRATION_1_2.migrate(dbV1)

        dbV1.query("SELECT id, title, status, engineState, errorCode FROM smart_downloads WHERE id = 'hist_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("محاضرة قديمة", c.getString(1))
            assertEquals("COMPLETED", c.getString(2))
            assertEquals("COMPLETED", c.getString(3))
        }
        helper.close()
        context.deleteDatabase(dbName)
    }
}
