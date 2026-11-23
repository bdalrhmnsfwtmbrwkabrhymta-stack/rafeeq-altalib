package com.hawas.study.rafid.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

object DownloadQueueManager {

    private val queueMutex = Mutex()

    suspend fun triggerQueueProcessing(context: Context) {
        val appContext = context.applicationContext
        queueMutex.withLock {
            val prefs = RafidPreferencesRepository(appContext).getSnapshot()
            if (prefs.isQueuePaused) return

            val dao = RafidDatabase.getInstance(appContext).rafidDao()
            val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()

            // Recover any stale DOWNLOADING / MUXING records whose WorkManager job disappeared
            if (workManager != null) {
                val staleActives = dao.getActiveDownloads()
                for (active in staleActives) {
                    val infos = runCatching {
                        workManager.getWorkInfosForUniqueWork(RafidDownloadWorker.uniqueWorkName(active.id)).get()
                    }.getOrDefault(emptyList())
                    val hasLiveWork = infos.any {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED
                    }
                    if (!hasLiveWork) {
                        dao.resetOrphanedActiveToQueued(active.id)
                    }
                }
            }

            val activeCount = dao.countActiveDownloads()
            val freeSlots = (prefs.maxConcurrentDownloads - activeCount).coerceAtLeast(0)
            if (freeSlots == 0) return

            val initialDelay = if (prefs.isScheduledStartEnabled) {
                computeDelayUntilMillis(prefs.scheduledHour, prefs.scheduledMinute)
            } else {
                0L
            }

            val queued = dao.getQueuedDownloads().take(freeSlots)
            for (next in queued) {
                val alreadyScheduled = if (workManager != null) {
                    val infos = runCatching {
                        workManager.getWorkInfosForUniqueWork(RafidDownloadWorker.uniqueWorkName(next.id)).get()
                    }.getOrDefault(emptyList())
                    infos.any {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED
                    }
                } else {
                    false
                }
                if (!alreadyScheduled) {
                    RafidDownloadWorker.enqueueDownload(
                        context = appContext,
                        downloadId = next.id,
                        wifiOnly = prefs.wifiOnly,
                        initialDelayMillis = initialDelay
                    )
                }
            }
        }
    }

    internal fun computeDelayUntilMillis(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            val diff = now.timeInMillis - target.timeInMillis
            if (diff <= 6 * 3600_000L) return 0L
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
