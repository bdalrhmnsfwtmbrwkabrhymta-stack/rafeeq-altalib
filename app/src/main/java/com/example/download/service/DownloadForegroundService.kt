package com.example.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.download.SmartDownloadManager
import com.example.download.data.DownloadStatus
import com.example.download.engine.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkMonitor: NetworkAutoResumeMonitor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        networkMonitor = NetworkAutoResumeMonitor(this).also { it.register() }
        startObservingDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> SmartDownloadManager.getInstance(this).pauseAllActive()
            ACTION_RESUME_ALL -> SmartDownloadManager.getInstance(this).resumeAllInterrupted()
            ACTION_CANCEL_ALL -> SmartDownloadManager.getInstance(this).cancelAllActive()
        }

        val initialNotification = buildNotification(
            title = "مدير التحميل الذكي",
            content = "جارٍ تجهيز التنزيلات...",
            progressPercent = 0,
            indeterminate = true,
            hasActive = true
        )
        startForegroundSafely(initialNotification)
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        SmartDownloadManager.getInstance(this).onServiceTimeoutGracefulPause()
        DownloadBootReceiver.enqueueResumeWork(this)
        releaseLocks()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        networkMonitor?.unregister()
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObservingDownloads() {
        val manager = SmartDownloadManager.getInstance(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        observerJob = serviceScope.launch {
            combine(manager.allDownloadsFlow, manager.liveStatsFlow) { list, stats ->
                list to stats
            }.collect { (downloads, stats) ->
                val active = downloads.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.EXTRACTING ||
                        it.status == DownloadStatus.MERGING ||
                        it.status == DownloadStatus.QUEUED
                }
                val waitingNet = downloads.filter { it.status == DownloadStatus.WAITING_NETWORK }

                if (active.isEmpty() && waitingNet.isEmpty()) {
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                if (active.isNotEmpty()) {
                    acquireLocks()
                } else {
                    releaseLocks()
                }

                val primary = active.firstOrNull() ?: waitingNet.first()
                val totalSpeed = stats.values.sumOf { it.speedBytesPerSec }
                val percent = if (primary.totalBytes > 0L) {
                    ((primary.downloadedBytes * 100L) / primary.totalBytes).toInt().coerceIn(0, 100)
                } else 0

                val statusText = when {
                    active.isEmpty() && waitingNet.isNotEmpty() ->
                        "في انتظار عودة الإنترنت (${waitingNet.size}) — سيتم الاستكمال تلقائياً"
                    primary.status == DownloadStatus.EXTRACTING ->
                        "جارٍ تحليل الرابط وتجهيز الأجزاء..."
                    primary.status == DownloadStatus.MERGING ->
                        "جارٍ الدمج والحفظ النهائي..."
                    else -> {
                        val speedStr = if (totalSpeed > 0L) StorageHelper.formatSpeed(totalSpeed) else "جارٍ الاتصال..."
                        val sizePart = if (primary.totalBytes > 0L) {
                            "${StorageHelper.formatBytes(primary.downloadedBytes)} / ${StorageHelper.formatBytes(primary.totalBytes)}"
                        } else {
                            StorageHelper.formatBytes(primary.downloadedBytes)
                        }
                        val countNote = if (active.size > 1) " (+${active.size - 1})" else ""
                        "$percent% • $speedStr • $sizePart$countNote"
                    }
                }

                val notification = buildNotification(
                    title = primary.title,
                    content = statusText,
                    progressPercent = percent,
                    indeterminate = primary.totalBytes <= 0L || primary.status == DownloadStatus.EXTRACTING || primary.status == DownloadStatus.MERGING,
                    hasActive = active.isNotEmpty()
                )
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progressPercent: Int,
        indeterminate: Boolean,
        hasActive: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "smart_download")
        }
        val openPending = PendingIntent.getActivity(
            this,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = if (hasActive) ACTION_PAUSE_ALL else ACTION_RESUME_ALL
        }
        val togglePending = PendingIntent.getService(
            this,
            1002,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPending = PendingIntent.getService(
            this,
            1003,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(hasActive)
            .setContentIntent(openPending)
            .setProgress(100, progressPercent, indeterminate)
            .addAction(0, if (hasActive) "إيقاف مؤقت" else "استئناف", togglePending)
            .addAction(0, "إلغاء", cancelPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "التحميل الذكي السريع",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات متابعة سرعة وتقدم التنزيلات في الخلفية"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HawasElDirasa:DownloadWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(60L * 60L * 1000L)
            }
        }
        runCatching {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "HawasElDirasa:DownloadWifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

    companion object {
        private const val CHANNEL_ID = "smart_downloads_channel"
        private const val NOTIFICATION_ID = 7742
        private const val ACTION_PAUSE_ALL = "com.example.download.ACTION_PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "com.example.download.ACTION_RESUME_ALL"
        private const val ACTION_CANCEL_ALL = "com.example.download.ACTION_CANCEL_ALL"

        fun startOrRefresh(context: Context) {
            val intent = Intent(context.applicationContext, DownloadForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            }
        }
    }
}
