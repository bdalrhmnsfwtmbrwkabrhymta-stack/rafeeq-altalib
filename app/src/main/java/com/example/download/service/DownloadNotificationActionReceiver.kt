package com.example.download.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.download.SmartDownloadManager
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.worker.DownloadQueueManager
import com.hawas.study.rafid.worker.RafidDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID).orEmpty()
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                when (action) {
                    ACTION_PAUSE -> handlePause(appContext, downloadId, source)
                    ACTION_RESUME -> handleResume(appContext, downloadId, source)
                    ACTION_CANCEL -> handleCancel(appContext, downloadId, source)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handlePause(context: Context, downloadId: String, source: String) {
        if (source == DownloadNotificationManager.SOURCE_SMART) {
            val manager = SmartDownloadManager.getInstance(context)
            if (downloadId.isNotBlank()) manager.pause(downloadId) else manager.pauseAllActive()
        } else {
            val dao = RafidDatabase.getInstance(context).rafidDao()
            if (downloadId.isNotBlank()) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.PAUSED.name, null)
                RafidDownloadWorker.cancelDownloadWork(context, downloadId)
            } else {
                RafidPreferencesRepository(context).setQueuePaused(true)
                dao.getActiveDownloads().forEach {
                    dao.updateDownloadStatus(it.id, RafidDownloadStatus.PAUSED.name, null)
                    RafidDownloadWorker.cancelDownloadWork(context, it.id)
                }
            }
        }
        DownloadForegroundService.startOrRefresh(context)
    }

    private suspend fun handleResume(context: Context, downloadId: String, source: String) {
        if (source == DownloadNotificationManager.SOURCE_SMART) {
            val manager = SmartDownloadManager.getInstance(context)
            if (downloadId.isNotBlank()) manager.resume(downloadId) else manager.resumeAllInterrupted()
        } else {
            val dao = RafidDatabase.getInstance(context).rafidDao()
            RafidPreferencesRepository(context).setQueuePaused(false)
            if (downloadId.isNotBlank()) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.QUEUED.name, null)
            } else {
                dao.getQueuedDownloads()
            }
            DownloadQueueManager.triggerQueueProcessing(context)
        }
        DownloadForegroundService.startOrRefresh(context)
    }

    private suspend fun handleCancel(context: Context, downloadId: String, source: String) {
        if (source == DownloadNotificationManager.SOURCE_SMART) {
            val manager = SmartDownloadManager.getInstance(context)
            if (downloadId.isNotBlank()) manager.cancel(downloadId, deleteTemp = true) else manager.cancelAllActive()
        } else {
            val dao = RafidDatabase.getInstance(context).rafidDao()
            if (downloadId.isNotBlank()) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.CANCELLED.name, null)
                RafidDownloadWorker.cancelDownloadWork(context, downloadId)
                dao.deleteDownloadById(downloadId)
                DownloadQueueManager.triggerQueueProcessing(context)
            } else {
                dao.getActiveDownloads().forEach {
                    dao.updateDownloadStatus(it.id, RafidDownloadStatus.CANCELLED.name, null)
                    RafidDownloadWorker.cancelDownloadWork(context, it.id)
                }
            }
        }
        DownloadForegroundService.startOrRefresh(context)
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_PAUSE = "com.example.download.action.NOTIFICATION_PAUSE"
        const val ACTION_RESUME = "com.example.download.action.NOTIFICATION_RESUME"
        const val ACTION_CANCEL = "com.example.download.action.NOTIFICATION_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_SOURCE = "extra_source"
    }
}
