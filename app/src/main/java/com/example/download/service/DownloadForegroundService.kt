package com.example.download.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.example.download.SmartDownloadManager
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadStatus
import com.example.download.data.LiveProgressSnapshot
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.worker.DownloadQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serviceIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkMonitor: NetworkAutoResumeMonitor? = null

    private val sessionTrackedIds = ConcurrentHashMap.newKeySet<String>()
    private var lastNotifyEmitMs = 0L
    private var lastRafidQueueSignature: String? = null
    private var lastSmartQueueSignature: String? = null

    private data class ActiveNotificationUiModel(
        val id: String,
        val source: String,
        val title: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long,
        val isPaused: Boolean,
        val hasActive: Boolean,
        val extraCount: Int,
        val thumbnailUrl: String?,
        val statusOverrideText: String?
    )

    override fun onCreate() {
        super.onCreate()
        DownloadNotificationManager.ensureChannels(this)
        networkMonitor = NetworkAutoResumeMonitor(this).also { it.register() }
        startObservingDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = DownloadNotificationManager.buildActiveProgressNotification(
            context = this,
            downloadId = "",
            source = DownloadNotificationManager.SOURCE_RAFID,
            videoTitle = "مدير التحميل الذكي",
            downloadedBytes = 0L,
            totalBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            isPaused = false,
            hasActive = true,
            statusOverrideText = "جارٍ تجهيز التنزيلات في الخلفية..."
        )
        startForegroundSafely(initialNotification)

        when (intent?.action) {
            ACTION_PAUSE_ALL -> SmartDownloadManager.getInstance(this).pauseAllActive()
            ACTION_RESUME_ALL -> {
                SmartDownloadManager.getInstance(this).resumeAllInterrupted()
                DownloadQueueManager.processQueueInService(this, serviceIoScope)
            }
            ACTION_CANCEL_ALL -> SmartDownloadManager.getInstance(this).cancelAllActive()
            ACTION_START_QUEUE, null -> {
                val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
                DownloadQueueManager.processQueueInService(this, serviceIoScope, taskId)
                SmartDownloadManager.getInstance(this).ensureQueueRunning()
            }
        }
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
        serviceIoScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObservingDownloads() {
        val manager = SmartDownloadManager.getInstance(this)
        val rafidDao = RafidDatabase.getInstance(this).rafidDao()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        observerJob = serviceScope.launch {
            combine(
                manager.allDownloadsFlow,
                manager.liveStatsFlow,
                rafidDao.observeAllDownloads()
            ) { smartList, smartStats, rafidList ->
                Triple(smartList, smartStats, rafidList)
            }.collect { (smartList, smartStats, rafidList) ->
                val rafidQueuedCount = rafidList.count { it.status == RafidDownloadStatus.QUEUED.name }
                val rafidActiveCount = rafidList.count {
                    it.status == RafidDownloadStatus.DOWNLOADING.name ||
                        it.status == RafidDownloadStatus.MUXING.name
                }
                val rafidSignature = "$rafidActiveCount:$rafidQueuedCount"
                if (rafidQueuedCount > 0 &&
                    (rafidSignature != lastRafidQueueSignature || !DownloadQueueManager.hasActiveInProcessDownloads())
                ) {
                    lastRafidQueueSignature = rafidSignature
                    DownloadQueueManager.processQueueInService(this@DownloadForegroundService, serviceIoScope)
                } else if (rafidQueuedCount == 0) {
                    lastRafidQueueSignature = null
                }

                val smartQueuedCount = smartList.count { it.status == DownloadStatus.QUEUED }
                val smartActiveCount = smartList.count {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.EXTRACTING ||
                        it.status == DownloadStatus.MERGING
                }
                val smartSignature = "$smartActiveCount:$smartQueuedCount"
                if (smartQueuedCount > 0 && smartSignature != lastSmartQueueSignature) {
                    lastSmartQueueSignature = smartSignature
                    manager.ensureQueueRunning()
                } else if (smartQueuedCount == 0) {
                    lastSmartQueueSignature = null
                }

                val model = selectPrimaryNotificationModel(smartList, smartStats, rafidList)
                if (model == null) {
                    val hasPendingOrActiveRafid = rafidQueuedCount > 0 || rafidActiveCount > 0
                    val hasPendingOrActiveSmart = smartQueuedCount > 0 || smartActiveCount > 0
                    if (!hasPendingOrActiveRafid &&
                        !hasPendingOrActiveSmart &&
                        !DownloadQueueManager.hasActiveInProcessDownloads()
                    ) {
                        releaseLocks()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@collect
                }

                if (model.hasActive) {
                    acquireLocks()
                } else {
                    releaseLocks()
                }

                val now = System.currentTimeMillis()
                // Throttle high-frequency progress ticks cleanly to ~1/sec unless state changed
                if (now - lastNotifyEmitMs < 800L && model.downloadedBytes > 0L && model.hasActive && !model.isPaused) {
                    return@collect
                }
                lastNotifyEmitMs = now

                val notification = DownloadNotificationManager.buildActiveProgressNotification(
                    context = this@DownloadForegroundService,
                    downloadId = model.id,
                    source = model.source,
                    videoTitle = model.title,
                    downloadedBytes = model.downloadedBytes,
                    totalBytes = model.totalBytes,
                    speedBytesPerSec = model.speedBytesPerSec,
                    etaSeconds = model.etaSeconds,
                    isPaused = model.isPaused,
                    hasActive = model.hasActive,
                    extraQueuedCount = model.extraCount,
                    thumbnailUrl = model.thumbnailUrl,
                    statusOverrideText = model.statusOverrideText,
                    onThumbnailReady = {
                        lastNotifyEmitMs = 0L
                        nm.notify(
                            DownloadNotificationManager.ACTIVE_NOTIFICATION_ID,
                            DownloadNotificationManager.buildActiveProgressNotification(
                                context = this@DownloadForegroundService,
                                downloadId = model.id,
                                source = model.source,
                                videoTitle = model.title,
                                downloadedBytes = model.downloadedBytes,
                                totalBytes = model.totalBytes,
                                speedBytesPerSec = model.speedBytesPerSec,
                                etaSeconds = model.etaSeconds,
                                isPaused = model.isPaused,
                                hasActive = model.hasActive,
                                extraQueuedCount = model.extraCount,
                                thumbnailUrl = model.thumbnailUrl,
                                statusOverrideText = model.statusOverrideText
                            )
                        )
                    }
                )
                if (model.hasActive) {
                    nm.notify(DownloadNotificationManager.ACTIVE_NOTIFICATION_ID, notification)
                } else {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    nm.notify(DownloadNotificationManager.ACTIVE_NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun selectPrimaryNotificationModel(
        smartList: List<DownloadEntity>,
        smartStats: Map<String, LiveProgressSnapshot>,
        rafidList: List<RafidDownloadEntity>
    ): ActiveNotificationUiModel? {
        val rafidActive = rafidList.filter {
            it.status == RafidDownloadStatus.DOWNLOADING.name ||
                it.status == RafidDownloadStatus.MUXING.name ||
                it.status == RafidDownloadStatus.QUEUED.name
        }
        rafidActive.forEach { sessionTrackedIds.add(it.id) }

        val smartActive = smartList.filter {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.EXTRACTING ||
                it.status == DownloadStatus.MERGING ||
                it.status == DownloadStatus.QUEUED
        }
        smartActive.forEach { sessionTrackedIds.add(it.id) }

        val rafidPausedInSession = rafidList.firstOrNull {
            it.status == RafidDownloadStatus.PAUSED.name && sessionTrackedIds.contains(it.id)
        }
        val smartPausedInSession = smartList.firstOrNull {
            it.status == DownloadStatus.PAUSED && sessionTrackedIds.contains(it.id)
        }
        val smartWaiting = smartList.filter { it.status == DownloadStatus.WAITING_NETWORK }

        val primaryRafid = rafidActive.firstOrNull { it.status == RafidDownloadStatus.DOWNLOADING.name }
            ?: rafidActive.firstOrNull { it.status == RafidDownloadStatus.MUXING.name }
            ?: rafidActive.firstOrNull()

        if (primaryRafid != null) {
            val statusOverride = when {
                primaryRafid.status == RafidDownloadStatus.MUXING.name && primaryRafid.engineState == "VERIFYING" ->
                    "جارٍ فحص وسلامة الملف النهائي..."
                primaryRafid.status == RafidDownloadStatus.MUXING.name ->
                    "جارٍ الدمج والحفظ في الاستوديو..."
                primaryRafid.status == RafidDownloadStatus.QUEUED.name ->
                    "في قائمة الانتظار — جاهز للبدء..."
                else -> null
            }
            val totalSpeed = rafidActive
                .filter { it.status == RafidDownloadStatus.DOWNLOADING.name }
                .sumOf { it.speedBytesPerSec }
                .coerceAtLeast(primaryRafid.speedBytesPerSec)
            return ActiveNotificationUiModel(
                id = primaryRafid.id,
                source = DownloadNotificationManager.SOURCE_RAFID,
                title = primaryRafid.title,
                downloadedBytes = primaryRafid.downloadedBytes,
                totalBytes = primaryRafid.totalBytes,
                speedBytesPerSec = totalSpeed,
                etaSeconds = primaryRafid.etaSeconds,
                isPaused = false,
                hasActive = true,
                extraCount = (rafidActive.size + smartActive.size - 1).coerceAtLeast(0),
                thumbnailUrl = primaryRafid.thumbnailUrl,
                statusOverrideText = statusOverride
            )
        }

        val primarySmart = smartActive.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
            ?: smartActive.firstOrNull { it.status == DownloadStatus.MERGING }
            ?: smartActive.firstOrNull()

        if (primarySmart != null) {
            val totalSpeed = smartStats.values.sumOf { it.speedBytesPerSec }
            val eta = smartStats[primarySmart.id]?.etaSeconds ?: -1L
            val statusOverride = when {
                primarySmart.status == DownloadStatus.EXTRACTING -> "جارٍ تحليل الرابط وتجهيز الأجزاء..."
                primarySmart.status == DownloadStatus.MERGING && primarySmart.engineState == "VERIFYING" ->
                    "جارٍ فحص وسلامة الملف النهائي..."
                primarySmart.status == DownloadStatus.MERGING -> "جارٍ الدمج والحفظ النهائي..."
                else -> null
            }
            return ActiveNotificationUiModel(
                id = primarySmart.id,
                source = DownloadNotificationManager.SOURCE_SMART,
                title = primarySmart.title,
                downloadedBytes = primarySmart.downloadedBytes,
                totalBytes = primarySmart.totalBytes,
                speedBytesPerSec = totalSpeed,
                etaSeconds = eta,
                isPaused = false,
                hasActive = true,
                extraCount = (smartActive.size - 1).coerceAtLeast(0),
                thumbnailUrl = primarySmart.thumbnailUrl,
                statusOverrideText = statusOverride
            )
        }

        if (rafidPausedInSession != null) {
            return ActiveNotificationUiModel(
                id = rafidPausedInSession.id,
                source = DownloadNotificationManager.SOURCE_RAFID,
                title = rafidPausedInSession.title,
                downloadedBytes = rafidPausedInSession.downloadedBytes,
                totalBytes = rafidPausedInSession.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                isPaused = true,
                hasActive = false,
                extraCount = 0,
                thumbnailUrl = rafidPausedInSession.thumbnailUrl,
                statusOverrideText = null
            )
        }

        if (smartPausedInSession != null) {
            return ActiveNotificationUiModel(
                id = smartPausedInSession.id,
                source = DownloadNotificationManager.SOURCE_SMART,
                title = smartPausedInSession.title,
                downloadedBytes = smartPausedInSession.downloadedBytes,
                totalBytes = smartPausedInSession.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                isPaused = true,
                hasActive = false,
                extraCount = 0,
                thumbnailUrl = smartPausedInSession.thumbnailUrl,
                statusOverrideText = null
            )
        }

        if (smartWaiting.isNotEmpty()) {
            val firstWaiting = smartWaiting.first()
            return ActiveNotificationUiModel(
                id = firstWaiting.id,
                source = DownloadNotificationManager.SOURCE_SMART,
                title = firstWaiting.title,
                downloadedBytes = firstWaiting.downloadedBytes,
                totalBytes = firstWaiting.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                isPaused = true,
                hasActive = false,
                extraCount = (smartWaiting.size - 1).coerceAtLeast(0),
                thumbnailUrl = firstWaiting.thumbnailUrl,
                statusOverrideText = "في انتظار عودة الإنترنت (${smartWaiting.size}) — سيتم الاستكمال تلقائياً"
            )
        }

        return null
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                DownloadNotificationManager.ACTIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotificationManager.ACTIVE_NOTIFICATION_ID, notification)
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
        const val ACTION_START_QUEUE = "com.example.download.ACTION_START_QUEUE"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val ACTION_PAUSE_ALL = "com.example.download.ACTION_PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "com.example.download.ACTION_RESUME_ALL"
        private const val ACTION_CANCEL_ALL = "com.example.download.ACTION_CANCEL_ALL"

        fun buildStartQueueIntent(context: Context, taskId: String? = null): Intent {
            val appCtx = context.applicationContext
            return Intent(appCtx, DownloadForegroundService::class.java).apply {
                action = ACTION_START_QUEUE
                if (!taskId.isNullOrBlank()) {
                    putExtra(EXTRA_TASK_ID, taskId)
                }
            }
        }

        fun startQueue(context: Context, taskId: String? = null) {
            val appCtx = context.applicationContext
            DownloadNotificationManager.ensureChannels(appCtx)
            val intent = buildStartQueueIntent(appCtx, taskId)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }
        }

        fun startOrRefresh(context: Context) {
            startQueue(context, null)
        }
    }
}
