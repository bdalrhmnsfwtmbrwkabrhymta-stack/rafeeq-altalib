package com.example.download.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.download.SmartDownloadManager

class DownloadBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            enqueueResumeWork(context)
        }
    }

    companion object {
        private const val WORK_NAME = "smart_download_auto_resume"

        fun enqueueResumeWork(context: Context) {
            runCatching {
                val work = OneTimeWorkRequestBuilder<DownloadResumeWorker>().build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
            }
        }
    }
}

class DownloadResumeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        SmartDownloadManager.getInstance(applicationContext).resumeInterruptedOnStartup()
        return Result.success()
    }
}
