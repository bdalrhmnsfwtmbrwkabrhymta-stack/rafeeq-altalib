package com.hawas.study.rafid.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.download.service.DownloadNotificationManager
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.TimeUnit

class RafidDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"

        fun uniqueWorkName(downloadId: String): String = "rafid_dl_$downloadId"

        fun enqueueDownload(
            context: Context,
            downloadId: String,
            wifiOnly: Boolean,
            initialDelayMillis: Long = 0L
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<RafidDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString(KEY_DOWNLOAD_ID, downloadId).build())
                .addTag("rafid_download")
                .addTag(uniqueWorkName(downloadId))

            if (initialDelayMillis > 0L) {
                requestBuilder.setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            } else {
                requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(downloadId),
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        fun cancelDownloadWork(context: Context, downloadId: String) {
            DownloadQueueManager.cancelInProcessJob(downloadId)
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(downloadId)) }
            runCatching { YoutubeDL.getInstance().destroyProcessById("rafid_$downloadId") }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID).orEmpty()
        val notification = DownloadNotificationManager.buildActiveProgressNotification(
            context = applicationContext,
            downloadId = downloadId,
            source = DownloadNotificationManager.SOURCE_RAFID,
            videoTitle = "مدير التحميل الذكي",
            downloadedBytes = 0L,
            totalBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            isPaused = false,
            hasActive = true,
            statusOverrideText = "جارٍ التحميل في الخلفية..."
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationManager.ACTIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DownloadNotificationManager.ACTIVE_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        return when (RafidDownloadExecutor.execute(applicationContext, downloadId) { isStopped }) {
            RafidExecutionOutcome.SUCCESS -> Result.success()
            RafidExecutionOutcome.RETRY -> Result.retry()
            RafidExecutionOutcome.FAILURE -> Result.failure()
        }
    }
}
