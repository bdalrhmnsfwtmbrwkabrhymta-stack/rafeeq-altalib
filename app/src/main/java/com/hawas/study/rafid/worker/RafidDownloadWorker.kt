package com.hawas.study.rafid.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.RafidMediaStoreSaver
import com.hawas.study.rafid.engine.UniversalLinkResolver
import com.hawas.study.rafid.models.RafidDownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(downloadId),
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        fun cancelDownloadWork(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(downloadId))
        }
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val dao = RafidDatabase.getInstance(applicationContext).rafidDao()
        val prefs = RafidPreferencesRepository(applicationContext).getSnapshot()
        val record = dao.getDownloadById(downloadId) ?: return@withContext Result.failure()

        if (record.status == RafidDownloadStatus.PAUSED.name ||
            record.status == RafidDownloadStatus.CANCELLED.name ||
            prefs.isQueuePaused
        ) {
            return@withContext Result.success()
        }

        if (prefs.wifiOnly && !isOnUnmeteredNetwork(applicationContext)) {
            dao.updateDownloadStatus(downloadId, RafidDownloadStatus.QUEUED.name, null)
            return@withContext Result.retry()
        }

        val tempDir = File(applicationContext.cacheDir, "rafid_tmp/$downloadId").apply { mkdirs() }
        val primaryTemp = File(tempDir, "primary.${record.containerExt.ifBlank { "mp4" }}")
        var resolvedStreamUrl = record.videoStreamUrl
        var resolvedAudioUrl = record.audioStreamUrl
        if (resolvedStreamUrl == record.resolvedUrl) {
            val reExtracted = runCatching {
                com.hawas.study.rafid.engine.UniversalMediaExtractor()
                    .extract(record.resolvedUrl, record.cookiesHeader, record.userAgent)
                    .getOrNull()
            }.getOrNull()
            val candidate = reExtracted?.formats?.firstOrNull {
                !it.videoUrl.isNullOrBlank() && it.videoUrl != record.resolvedUrl &&
                    (it.isAudioOnly == record.isAudioOnly)
            }
            if (candidate != null && !candidate.videoUrl.isNullOrBlank()) {
                resolvedStreamUrl = candidate.videoUrl
                if (resolvedAudioUrl.isNullOrBlank()) {
                    resolvedAudioUrl = candidate.audioUrl
                }
            }
        }
        val secondaryAudioTemp = if (!resolvedAudioUrl.isNullOrBlank()) {
            File(tempDir, "audio_track.m4a")
        } else {
            null
        }

        return@withContext try {
            dao.updateDownloadProgress(
                id = downloadId,
                status = RafidDownloadStatus.DOWNLOADING.name,
                downloadedBytes = primaryTemp.length(),
                totalBytes = record.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )

            val okPrimary = downloadStreamWithResume(
                downloadId = downloadId,
                streamUrl = resolvedStreamUrl,
                targetFile = primaryTemp,
                cookiesHeader = record.cookiesHeader,
                userAgent = record.userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT,
                knownTotalBytes = record.totalBytes,
                dao = dao
            )
            if (!okPrimary) {
                val latest = dao.getDownloadById(downloadId)
                if (latest?.status == RafidDownloadStatus.PAUSED.name) {
                    return@withContext Result.success()
                }
                return@withContext Result.failure()
            }

            if (secondaryAudioTemp != null && !resolvedAudioUrl.isNullOrBlank()) {
                val okAudio = downloadStreamWithResume(
                    downloadId = downloadId,
                    streamUrl = resolvedAudioUrl,
                    targetFile = secondaryAudioTemp,
                    cookiesHeader = record.cookiesHeader,
                    userAgent = record.userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT,
                    knownTotalBytes = 0L,
                    dao = dao
                )
                if (!okAudio) {
                    val latest = dao.getDownloadById(downloadId)
                    if (latest?.status == RafidDownloadStatus.PAUSED.name) {
                        return@withContext Result.success()
                    }
                    return@withContext Result.failure()
                }
            }

            // Network phase finished 100%. Hand off muxing/audio conversion to strictly serial WorkManager queue!
            if (record.isMuxRequired || secondaryAudioTemp != null) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)
                SerialFFmpegMuxerWorker.enqueueSerialMux(
                    context = applicationContext,
                    downloadId = downloadId,
                    videoFilePath = primaryTemp.absolutePath,
                    audioFilePath = secondaryAudioTemp?.absolutePath,
                    isAudioOnly = record.isAudioOnly
                )
            } else {
                val category = AdAndTrackerShield.categoryFromMimeOrExt(record.mimeType, record.containerExt)
                val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                    context = applicationContext,
                    sourceFile = primaryTemp,
                    title = record.title,
                    extension = record.containerExt,
                    mimeType = record.mimeType,
                    category = category,
                    saveToStudyFolder = record.saveToStudyFolder
                )
                dao.markDownloadCompleted(
                    id = downloadId,
                    finalSize = saved.fileSizeBytes,
                    mediaStoreUri = saved.uriString,
                    displayPath = saved.displayPath
                )
                runCatching { tempDir.deleteRecursively() }
            }

            Result.success()
        } catch (t: Throwable) {
            val latest = dao.getDownloadById(downloadId)
            if (latest?.status != RafidDownloadStatus.PAUSED.name &&
                latest?.status != RafidDownloadStatus.CANCELLED.name
            ) {
                dao.updateDownloadStatus(
                    id = downloadId,
                    status = RafidDownloadStatus.FAILED.name,
                    errorMessage = t.localizedMessage ?: "فشل الاتصال أثناء التحميل"
                )
            }
            Result.failure()
        }
    }

    private suspend fun downloadStreamWithResume(
        downloadId: String,
        streamUrl: String,
        targetFile: File,
        cookiesHeader: String?,
        userAgent: String,
        knownTotalBytes: Long,
        dao: com.hawas.study.rafid.data.RafidDao
    ): Boolean {
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        val reqBuilder = Request.Builder()
            .url(streamUrl)
            .get()
            .header("User-Agent", userAgent)
            .apply {
                if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
                if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
            }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (response.code == 416 && existingBytes > 0L) {
                return true
            }
            if (!response.isSuccessful) {
                dao.updateDownloadStatus(
                    downloadId,
                    RafidDownloadStatus.FAILED.name,
                    "استجابة الخادم غير صالحة (${response.code})"
                )
                return false
            }

            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) {
                dao.updateDownloadStatus(
                    downloadId,
                    RafidDownloadStatus.FAILED.name,
                    "افتح الرابط في المتصفح الذكي لالتقاط البث المباشر"
                )
                return false
            }

            val isPartial = response.code == 206
            val append = isPartial && existingBytes > 0L
            val contentLen = response.body?.contentLength() ?: -1L
            val totalExpected = when {
                isPartial && contentLen > 0L -> existingBytes + contentLen
                contentLen > 0L -> contentLen
                knownTotalBytes > 0L -> knownTotalBytes
                else -> 0L
            }

            val body = response.body ?: return false
            var currentBytes = if (append) existingBytes else 0L
            val buffer = ByteArray(32 * 1024)
            var lastReportTime = System.currentTimeMillis()
            var bytesSinceLastReport = 0L

            FileOutputStream(targetFile, append).use { fos ->
                body.byteStream().use { input ->
                    while (true) {
                        if (isStopped) {
                            return false
                        }
                        val statusRow = dao.getDownloadById(downloadId)
                        if (statusRow == null ||
                            statusRow.status == RafidDownloadStatus.PAUSED.name ||
                            statusRow.status == RafidDownloadStatus.CANCELLED.name
                        ) {
                            return false
                        }

                        val read = input.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        currentBytes += read
                        bytesSinceLastReport += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastReportTime
                        if (elapsedMs >= 450L) {
                            val speedBps = if (elapsedMs > 0) (bytesSinceLastReport * 1000L) / elapsedMs else 0L
                            val remaining = if (totalExpected > currentBytes) totalExpected - currentBytes else 0L
                            val etaSec = if (speedBps > 0L && remaining > 0L) remaining / speedBps else 0L
                            dao.updateDownloadProgress(
                                id = downloadId,
                                status = RafidDownloadStatus.DOWNLOADING.name,
                                downloadedBytes = currentBytes,
                                totalBytes = totalExpected,
                                speedBytesPerSec = speedBps,
                                etaSeconds = etaSec
                            )
                            lastReportTime = now
                            bytesSinceLastReport = 0L
                        }
                    }
                }
            }

            dao.updateDownloadProgress(
                id = downloadId,
                status = RafidDownloadStatus.DOWNLOADING.name,
                downloadedBytes = currentBytes,
                totalBytes = maxOf(totalExpected, currentBytes),
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )
            return true
        }
    }

    private fun isOnUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
