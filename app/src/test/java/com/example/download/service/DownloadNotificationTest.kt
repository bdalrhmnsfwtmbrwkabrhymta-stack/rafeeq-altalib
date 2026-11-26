package com.example.download.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadNotificationTest {

    @Test
    fun ensureChannels_createsActiveAndCompleteHighImportanceChannels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DownloadNotificationManager.ensureChannels(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeChannel = nm.getNotificationChannel(DownloadNotificationManager.ACTIVE_CHANNEL_ID)
        val completeChannel = nm.getNotificationChannel(DownloadNotificationManager.COMPLETE_CHANNEL_ID)

        assertNotNull(activeChannel)
        assertNotNull(completeChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, activeChannel.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, completeChannel.importance)
    }

    @Test
    fun buildActiveProgressNotification_hasLiveProgressSpeedActionsAndSilentOngoingFlags() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloadedBytes = (68L * 1024L * 1024L)
        val totalBytes = (100L * 1024L * 1024L)
        val speedBytes = (4.5 * 1024.0 * 1024.0).toLong()

        val notification = DownloadNotificationManager.buildActiveProgressNotification(
            context = context,
            downloadId = "dl_physics_1",
            source = DownloadNotificationManager.SOURCE_RAFID,
            videoTitle = "مراجعة الفيزياء - الباب الأول (1080p HD)",
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytes,
            etaSeconds = 12L,
            isPaused = false,
            hasActive = true
        )

        assertEquals(DownloadNotificationManager.ACTIVE_CHANNEL_ID, notification.channelId)
        assertEquals(DownloadNotificationManager.ACCENT_EMERALD_COLOR, notification.color)
        assertTrue((notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val progressMax = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)

        assertEquals("⬇ جاري التحميل... 68%", title)
        assertTrue(text.contains("م.ب"))
        assertTrue(text.contains("م.ب/ث"))
        assertEquals(68, progress)
        assertEquals(100, progressMax)

        assertEquals(2, notification.actions.size)
        assertEquals("إيقاف مؤقت", notification.actions[0].title.toString())
        assertEquals("إلغاء", notification.actions[1].title.toString())

        val pauseIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(DownloadNotificationActionReceiver.ACTION_PAUSE, pauseIntent.action)
        assertEquals("dl_physics_1", pauseIntent.getStringExtra(DownloadNotificationActionReceiver.EXTRA_DOWNLOAD_ID))

        val cancelIntent = shadowOf(notification.actions[1].actionIntent).savedIntent
        assertEquals(DownloadNotificationActionReceiver.ACTION_CANCEL, cancelIntent.action)
    }

    @Test
    fun buildActiveProgressNotification_whenPaused_showsResumeAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = DownloadNotificationManager.buildActiveProgressNotification(
            context = context,
            downloadId = "dl_paused_1",
            source = DownloadNotificationManager.SOURCE_SMART,
            videoTitle = "محاضرة الكيمياء العضوية",
            downloadedBytes = 25L * 1024L * 1024L,
            totalBytes = 100L * 1024L * 1024L,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            isPaused = true,
            hasActive = false
        )

        assertEquals("استئناف", notification.actions[0].title.toString())
        val resumeIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(DownloadNotificationActionReceiver.ACTION_RESUME, resumeIntent.action)
        assertEquals(DownloadNotificationManager.SOURCE_SMART, resumeIntent.getStringExtra(DownloadNotificationActionReceiver.EXTRA_SOURCE))
    }

    @Test
    fun notifyDownloadCompleted_postsHighPriorityHeadsUpWithStudioAndAppActions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyFile = File(context.filesDir, "physics_review.mp4").apply {
            writeBytes(ByteArray(2048) { 1 })
        }
        try {
            DownloadNotificationManager.notifyDownloadCompleted(
                context = context,
                downloadId = "dl_done_1",
                source = DownloadNotificationManager.SOURCE_RAFID,
                videoTitle = "مراجعة الفيزياء - الباب الأول.mp4",
                totalBytes = 114L * 1024L * 1024L,
                mimeType = "video/mp4",
                savedUriString = dummyFile.toURI().toString(),
                displayPath = dummyFile.absolutePath,
                thumbnailUrl = null
            )

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val posted = shadowOf(nm).allNotifications
                .firstOrNull { it.channelId == DownloadNotificationManager.COMPLETE_CHANNEL_ID }

            assertNotNull(posted)
            val title = posted!!.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            assertEquals("✅ تم اكتمال التحميل بنجاح", title)
            assertTrue(text.contains("محفوظ في الاستوديو"))
            assertTrue((posted.flags and Notification.FLAG_AUTO_CANCEL) != 0)

            assertEquals(2, posted.actions.size)
            assertEquals("تشغيل في الاستوديو", posted.actions[0].title.toString())
            assertEquals("فتح التطبيق", posted.actions[1].title.toString())

            val viewIntent = shadowOf(posted.actions[0].actionIntent).savedIntent
            assertEquals(Intent.ACTION_VIEW, viewIntent.action)
            assertEquals("video/mp4", viewIntent.type)
            assertTrue((viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

            val openAppIntent = shadowOf(posted.actions[1].actionIntent).savedIntent
            assertEquals("rafid", openAppIntent.getStringExtra("NAVIGATE_TO"))
            assertEquals(
                DownloadNotificationManager.TAB_QUEUES,
                openAppIntent.getStringExtra(DownloadNotificationManager.EXTRA_RAFID_INITIAL_TAB)
            )
        } finally {
            dummyFile.delete()
        }
    }
}
