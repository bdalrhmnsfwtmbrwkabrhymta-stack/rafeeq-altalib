package com.example.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DownloadNotificationManager {

    const val ACTIVE_CHANNEL_ID = "hawas_download_active_channel"
    const val COMPLETE_CHANNEL_ID = "hawas_download_complete_channel"
    const val ACTIVE_NOTIFICATION_ID = 7742
    const val ACCENT_EMERALD_COLOR = 0xFF10B981.toInt()
    const val ACCENT_ROYAL_BLUE_COLOR = 0xFF1E56F5.toInt()

    const val SOURCE_RAFID = "rafid"
    const val SOURCE_SMART = "smart"

    const val EXTRA_RAFID_INITIAL_TAB = "RAFID_INITIAL_TAB"
    const val TAB_QUEUES = "QUEUES"

    private val thumbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmapCache = LruCache<String, Bitmap>(24)
    private val inFlightThumbs = ConcurrentHashMap.newKeySet<String>()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(ACTIVE_CHANNEL_ID) == null) {
            val activeChannel = NotificationChannel(
                ACTIVE_CHANNEL_ID,
                "التنزيلات النشطة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات متابعة سرعة وتقدم التحميل في الخلفية وشريط الحالة"
                setShowBadge(false)
                enableLights(true)
                lightColor = ACCENT_ROYAL_BLUE_COLOR
            }
            nm.createNotificationChannel(activeChannel)
        }

        if (nm.getNotificationChannel(COMPLETE_CHANNEL_ID) == null) {
            val completeChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "اكتمال التنزيلات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات منبثقة فور اكتمال التحميل والحفظ في الاستوديو"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lightColor = ACCENT_EMERALD_COLOR
            }
            nm.createNotificationChannel(completeChannel)
        }
    }

    fun getCachedOrPrefetchThumbnail(
        thumbnailUrl: String?,
        onLoaded: () -> Unit = {}
    ): Bitmap? {
        if (thumbnailUrl.isNullOrBlank()) return null
        bitmapCache.get(thumbnailUrl)?.let { return it }
        if (inFlightThumbs.add(thumbnailUrl)) {
            thumbScope.launch {
                try {
                    val bmp = downloadScaledBitmap(thumbnailUrl)
                    if (bmp != null) {
                        bitmapCache.put(thumbnailUrl, bmp)
                        onLoaded()
                    }
                } finally {
                    inFlightThumbs.remove(thumbnailUrl)
                }
            }
        }
        return null
    }

    suspend fun getOrFetchThumbnail(thumbnailUrl: String?): Bitmap? {
        if (thumbnailUrl.isNullOrBlank()) return null
        bitmapCache.get(thumbnailUrl)?.let { return it }
        return withTimeoutOrNull(1800L) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                downloadScaledBitmap(thumbnailUrl)?.also { bitmapCache.put(thumbnailUrl, it) }
            }
        }
    }

    private fun downloadScaledBitmap(urlStr: String): Bitmap? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500
            readTimeout = 4500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > 512) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }.getOrNull()

    fun buildActiveProgressNotification(
        context: Context,
        downloadId: String,
        source: String,
        videoTitle: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        isPaused: Boolean,
        hasActive: Boolean,
        extraQueuedCount: Int = 0,
        thumbnailUrl: String? = null,
        statusOverrideText: String? = null,
        onThumbnailReady: () -> Unit = {}
    ): Notification {
        ensureChannels(context)
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else 0
        val indeterminate = totalBytes <= 0L && !isPaused

        val downloadedMb = formatMbNumber(downloadedBytes)
        val totalMb = if (totalBytes > 0L) formatMbNumber(totalBytes) else "--"
        val speedMb = formatSpeedMbNumber(speedBytesPerSec)

        val headerTitle = if (isPaused) {
            "⏸ تم إيقاف التحميل مؤقتاً ($progress%)"
        } else {
            "⬇ جاري التحميل... $progress%"
        }
        val metricsLine = statusOverrideText ?: if (totalBytes > 0L) {
            "$downloadedMb م.ب / $totalMb م.ب • السرعة: $speedMb م.ب/ث"
        } else {
            "$downloadedMb م.ب • السرعة: $speedMb م.ب/ث"
        }
        val countSuffix = if (extraQueuedCount > 0) " (+$extraQueuedCount)" else ""
        val etaPart = if (!isPaused && etaSeconds > 0L) " • متبقي ${formatEtaShort(etaSeconds)}" else ""
        val expandedText = "$videoTitle\n$metricsLine$etaPart$countSuffix"

        val thumbBitmap = getCachedOrPrefetchThumbnail(thumbnailUrl, onThumbnailReady)
        val openAppPending = createOpenDownloadsPendingIntent(context, source, 2001)

        val toggleAction = if (hasActive && !isPaused) {
            DownloadNotificationActionReceiver.ACTION_PAUSE
        } else {
            DownloadNotificationActionReceiver.ACTION_RESUME
        }
        val toggleLabel = if (hasActive && !isPaused) "إيقاف مؤقت" else "استئناف"
        val togglePending = createReceiverPendingIntent(
            context = context,
            action = toggleAction,
            downloadId = downloadId,
            source = source,
            requestCode = 2002
        )
        val cancelPending = createReceiverPendingIntent(
            context = context,
            action = DownloadNotificationActionReceiver.ACTION_CANCEL,
            downloadId = downloadId,
            source = source,
            requestCode = 2003
        )

        val builder = NotificationCompat.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(ACCENT_EMERALD_COLOR)
            .setContentTitle(headerTitle)
            .setContentText("$videoTitle • $metricsLine")
            .setSubText(if (totalBytes > 0L) "$downloadedMb / $totalMb م.ب" else null)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(headerTitle)
                    .bigText(expandedText)
            )
            .setOnlyAlertOnce(true)
            .setOngoing(hasActive)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPending)
            .setProgress(100, progress, indeterminate)
            .addAction(0, toggleLabel, togglePending)
            .addAction(0, "إلغاء", cancelPending)

        if (thumbBitmap != null) {
            builder.setLargeIcon(thumbBitmap)
        }
        return builder.build()
    }

    suspend fun notifyDownloadCompleted(
        context: Context,
        downloadId: String,
        source: String,
        videoTitle: String,
        totalBytes: Long,
        mimeType: String,
        savedUriString: String?,
        displayPath: String,
        thumbnailUrl: String? = null
    ) {
        ensureChannels(context)
        val resolvedFile = resolveSavedFile(context, savedUriString, displayPath)
        scanFileIntoGallery(context, resolvedFile, savedUriString, displayPath, mimeType)

        val thumbBitmap = getOrFetchThumbnail(thumbnailUrl)
        val totalMb = formatMbNumber(totalBytes.coerceAtLeast(resolvedFile?.length() ?: 0L))
        val subtitleText = "$videoTitle\nمحفوظ في الاستوديو ($totalMb م.ب)"

        val openInGalleryPending = createPlayInStudioPendingIntent(
            context = context,
            resolvedFile = resolvedFile,
            savedUriString = savedUriString,
            mimeType = mimeType,
            requestCode = 3100 + (downloadId.hashCode() and 0x0FFF)
        )
        val openAppPending = createOpenDownloadsPendingIntent(
            context = context,
            source = source,
            requestCode = 4100 + (downloadId.hashCode() and 0x0FFF)
        )

        val builder = NotificationCompat.Builder(context, COMPLETE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(ACCENT_EMERALD_COLOR)
            .setContentTitle("✅ تم اكتمال التحميل بنجاح")
            .setContentText("$videoTitle • محفوظ في الاستوديو ($totalMb م.ب)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPending)

        if (thumbBitmap != null) {
            builder.setLargeIcon(thumbBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle("✅ تم اكتمال التحميل بنجاح")
                    .setSummaryText(subtitleText)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("✅ تم اكتمال التحميل بنجاح")
                    .bigText(subtitleText)
            )
        }

        if (openInGalleryPending != null) {
            builder.addAction(0, "تشغيل في الاستوديو", openInGalleryPending)
        }
        builder.addAction(0, "فتح التطبيق", openAppPending)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completionNotifId = 8900 + (downloadId.hashCode() and 0x0FFF)
        nm.notify(completionNotifId, builder.build())
    }

    fun scanFileIntoGallery(
        context: Context,
        resolvedFile: File?,
        savedUriString: String?,
        displayPath: String,
        mimeType: String
    ) {
        val candidatePath = resolvedFile?.absolutePath
            ?: resolveFilePathFromUriOrDisplay(context, savedUriString, displayPath)
        if (!candidatePath.isNullOrBlank()) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(candidatePath),
                    arrayOf(mimeType.ifBlank { "*/*" }),
                    null
                )
            }
        }
    }

    private fun createPlayInStudioPendingIntent(
        context: Context,
        resolvedFile: File?,
        savedUriString: String?,
        mimeType: String,
        requestCode: Int
    ): PendingIntent? {
        val viewUri: Uri? = when {
            resolvedFile != null && resolvedFile.exists() -> {
                runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        resolvedFile
                    )
                }.getOrElse {
                    savedUriString?.let { Uri.parse(it) } ?: Uri.fromFile(resolvedFile)
                }
            }
            !savedUriString.isNullOrBlank() -> Uri.parse(savedUriString)
            else -> null
        }
        viewUri ?: return null

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(viewUri, mimeType.ifBlank { "video/mp4" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createOpenDownloadsPendingIntent(
        context: Context,
        source: String,
        requestCode: Int
    ): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (source == SOURCE_SMART) {
                putExtra("NAVIGATE_TO", "smart_download")
            } else {
                putExtra("NAVIGATE_TO", "rafid")
                putExtra(EXTRA_RAFID_INITIAL_TAB, TAB_QUEUES)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createReceiverPendingIntent(
        context: Context,
        action: String,
        downloadId: String,
        source: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadNotificationActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(DownloadNotificationActionReceiver.EXTRA_SOURCE, source)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolveSavedFile(
        context: Context,
        savedUriString: String?,
        displayPath: String
    ): File? {
        val path = resolveFilePathFromUriOrDisplay(context, savedUriString, displayPath) ?: return null
        return File(path).takeIf { it.exists() && it.length() > 0L }
    }

    private fun resolveFilePathFromUriOrDisplay(
        context: Context,
        savedUriString: String?,
        displayPath: String
    ): String? {
        if (!savedUriString.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(savedUriString) }.getOrNull()
            if (uri != null) {
                if (uri.scheme == ContentResolver.SCHEME_FILE) {
                    uri.path?.let { return it }
                }
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    runCatching {
                        context.contentResolver.query(
                            uri,
                            arrayOf(MediaStore.MediaColumns.DATA),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                if (colIdx >= 0) {
                                    val dataPath = cursor.getString(colIdx)
                                    if (!dataPath.isNullOrBlank()) return dataPath
                                }
                            }
                        }
                    }
                }
            }
        }
        if (displayPath.isNotBlank()) {
            val direct = File(displayPath)
            if (direct.isAbsolute && direct.exists()) return direct.absolutePath
            @Suppress("DEPRECATION")
            val publicRoot = Environment.getExternalStorageDirectory()
            val candidate = File(publicRoot, displayPath.trimStart('/'))
            if (candidate.exists()) return candidate.absolutePath
            return candidate.absolutePath
        }
        return null
    }

    fun formatMbNumber(bytes: Long): String {
        if (bytes <= 0L) return "0.0"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f", mb)
    }

    fun formatSpeedMbNumber(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0.0"
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 0.1) {
            String.format(Locale.US, "%.1f", mb)
        } else {
            String.format(Locale.US, "%.2f", mb)
        }
    }

    private fun formatEtaShort(seconds: Long): String {
        if (seconds <= 0L) return ""
        val mins = seconds / 60
        val secs = seconds % 60
        return when {
            mins >= 60 -> "${mins / 60} س ${mins % 60} د"
            mins > 0 -> "$mins د $secs ث"
            else -> "$secs ثانية"
        }
    }
}
