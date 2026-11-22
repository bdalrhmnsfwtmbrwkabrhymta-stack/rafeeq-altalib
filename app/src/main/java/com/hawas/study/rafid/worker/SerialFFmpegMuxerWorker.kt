package com.hawas.study.rafid.worker

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.download.core.media.MediaMergeEngine
import com.example.download.core.model.DiagnosticRedactor
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.RafidMediaStoreSaver
import com.hawas.study.rafid.models.RafidDownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Decoupled Serial Muxer Queue Worker:
 * Uses a Mutex lock so strictly 1 FFmpeg/MediaMuxer job runs at a time without OOM,
 * while isolating WorkManager unique work per downloadId so a single item failure
 * never cascades to cancel subsequent playlist items.
 */
class SerialFFmpegMuxerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_SERIAL_MUX_WORK = "rafid_serial_muxer_queue"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VIDEO_FILE = "video_file_path"
        const val KEY_AUDIO_FILE = "audio_file_path"
        const val KEY_AUDIO_ONLY = "is_audio_only"

        private val muxMutex = Mutex()

        fun uniqueMuxWorkName(downloadId: String): String = "rafid_mux_$downloadId"

        suspend fun runSerializedMux(
            context: Context,
            primaryMediaFile: File,
            secondaryAudioFile: File?,
            desiredOutputFile: File,
            desiredExtension: String,
            audioOnly: Boolean
        ): com.example.download.core.media.MergeExecutionResult = withContext(Dispatchers.IO) {
            muxMutex.withLock {
                MediaMergeEngine.muxTracksWithFfmpeg(
                    context = context,
                    primaryMediaFile = primaryMediaFile,
                    secondaryAudioFile = secondaryAudioFile,
                    desiredOutputFile = desiredOutputFile,
                    desiredExtension = desiredExtension,
                    audioOnly = audioOnly
                )
            }
        }

        fun enqueueSerialMux(
            context: Context,
            downloadId: String,
            videoFilePath: String,
            audioFilePath: String?,
            isAudioOnly: Boolean
        ) {
            val data = Data.Builder()
                .putString(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_VIDEO_FILE, videoFilePath)
                .putString(KEY_AUDIO_FILE, audioFilePath)
                .putBoolean(KEY_AUDIO_ONLY, isAudioOnly)
                .build()

            val request = OneTimeWorkRequestBuilder<SerialFFmpegMuxerWorker>()
                .setInputData(data)
                .addTag(UNIQUE_SERIAL_MUX_WORK)
                .addTag("rafid_mux_$downloadId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    uniqueMuxWorkName(downloadId),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        fun muxTracks(
            primaryMediaFile: File,
            secondaryAudioFile: File?,
            outputFile: File,
            audioOnly: Boolean
        ): Boolean {
            if (!primaryMediaFile.exists() || primaryMediaFile.length() == 0L) return false

            if (secondaryAudioFile == null && !audioOnly) {
                primaryMediaFile.copyTo(outputFile, overwrite = true)
                return outputFile.exists() && outputFile.length() > 0L
            }

            var videoExtractor: MediaExtractor? = null
            var audioExtractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            return try {
                val primaryExtractor = MediaExtractor().apply { setDataSource(primaryMediaFile.absolutePath) }
                videoExtractor = primaryExtractor

                val secExtractor = if (secondaryAudioFile != null && secondaryAudioFile.exists() && secondaryAudioFile.length() > 0L) {
                    MediaExtractor().apply { setDataSource(secondaryAudioFile.absolutePath) }.also { audioExtractor = it }
                } else {
                    null
                }

                var selectedVideoTrack = -1
                var selectedAudioTrack = -1
                var audioFromSecondary = false

                if (!audioOnly) {
                    for (i in 0 until primaryExtractor.trackCount) {
                        val fmt = primaryExtractor.getTrackFormat(i)
                        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("video/") && selectedVideoTrack == -1) {
                            selectedVideoTrack = i
                        } else if (secExtractor == null && mime.startsWith("audio/") && selectedAudioTrack == -1) {
                            selectedAudioTrack = i
                        }
                    }
                }

                if (secExtractor != null) {
                    for (i in 0 until secExtractor.trackCount) {
                        val fmt = secExtractor.getTrackFormat(i)
                        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("audio/")) {
                            selectedAudioTrack = i
                            audioFromSecondary = true
                            break
                        }
                    }
                } else if (audioOnly) {
                    for (i in 0 until primaryExtractor.trackCount) {
                        val fmt = primaryExtractor.getTrackFormat(i)
                        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("audio/")) {
                            selectedAudioTrack = i
                            break
                        }
                    }
                }

                if (selectedVideoTrack == -1 && selectedAudioTrack == -1) {
                    return false
                }

                val isWebmTrack = (selectedVideoTrack >= 0 && primaryExtractor.getTrackFormat(selectedVideoTrack)
                    .getString(MediaFormat.KEY_MIME).orEmpty().contains("vp", ignoreCase = true)) ||
                    (selectedAudioTrack >= 0 && (if (audioFromSecondary) secExtractor!! else primaryExtractor)
                        .getTrackFormat(selectedAudioTrack).getString(MediaFormat.KEY_MIME).orEmpty().contains("opus", ignoreCase = true))

                val containerFormat = if (isWebmTrack) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }

                val mediaMuxer = MediaMuxer(outputFile.absolutePath, containerFormat)
                muxer = mediaMuxer

                val muxVideoTrackIndex = if (selectedVideoTrack >= 0) {
                    primaryExtractor.selectTrack(selectedVideoTrack)
                    mediaMuxer.addTrack(primaryExtractor.getTrackFormat(selectedVideoTrack))
                } else -1

                val activeAudioExtractor = if (audioFromSecondary) secExtractor!! else primaryExtractor
                val muxAudioTrackIndex = if (selectedAudioTrack >= 0) {
                    activeAudioExtractor.selectTrack(selectedAudioTrack)
                    mediaMuxer.addTrack(activeAudioExtractor.getTrackFormat(selectedAudioTrack))
                } else -1

                mediaMuxer.start()
                val buffer = ByteBuffer.allocate(512 * 1024)
                val bufferInfo = MediaCodec.BufferInfo()

                if (muxVideoTrackIndex >= 0) {
                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = primaryExtractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break
                        bufferInfo.presentationTimeUs = primaryExtractor.sampleTime
                        bufferInfo.flags = primaryExtractor.sampleFlags
                        mediaMuxer.writeSampleData(muxVideoTrackIndex, buffer, bufferInfo)
                        primaryExtractor.advance()
                    }
                }

                if (muxAudioTrackIndex >= 0) {
                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = activeAudioExtractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break
                        bufferInfo.presentationTimeUs = activeAudioExtractor.sampleTime
                        bufferInfo.flags = activeAudioExtractor.sampleFlags
                        mediaMuxer.writeSampleData(muxAudioTrackIndex, buffer, bufferInfo)
                        activeAudioExtractor.advance()
                    }
                }

                mediaMuxer.stop()
                outputFile.exists() && outputFile.length() > 0L
            } catch (_: Throwable) {
                false
            } finally {
                runCatching { muxer?.release() }
                runCatching { videoExtractor?.release() }
                runCatching { audioExtractor?.release() }
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val videoPath = inputData.getString(KEY_VIDEO_FILE) ?: return@withContext Result.failure()
        val audioPath = inputData.getString(KEY_AUDIO_FILE)
        val isAudioOnly = inputData.getBoolean(KEY_AUDIO_ONLY, false)

        val dao = RafidDatabase.getInstance(applicationContext).rafidDao()
        val record = dao.getDownloadById(downloadId) ?: return@withContext Result.failure()

        dao.updateEngineState(downloadId, "MERGING")
        dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)

        val primaryFile = File(videoPath)
        val secondaryFile = audioPath?.let(::File)
        val tempDir = primaryFile.parentFile ?: File(applicationContext.cacheDir, "rafid_tmp/$downloadId").apply { mkdirs() }

        val targetExt = if (isAudioOnly) {
            if (record.containerExt.equals("mp3", ignoreCase = true)) "mp3" else "m4a"
        } else {
            record.containerExt.ifBlank { "mp4" }
        }
        val muxedOutput = File(tempDir, "muxed_output.$targetExt")
        var completedOk = false

        return@withContext try {
            val mergeResult = runSerializedMux(
                context = applicationContext,
                primaryMediaFile = primaryFile,
                secondaryAudioFile = secondaryFile,
                desiredOutputFile = muxedOutput,
                desiredExtension = targetExt,
                audioOnly = isAudioOnly
            )

            dao.updateEngineState(downloadId, "VERIFYING")
            val category = AdAndTrackerShield.categoryFromMimeOrExt(mergeResult.finalMimeType, mergeResult.finalExtension)
            val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                context = applicationContext,
                sourceFile = mergeResult.outputFile,
                title = record.title,
                extension = mergeResult.finalExtension,
                mimeType = mergeResult.finalMimeType,
                category = category,
                saveToStudyFolder = record.saveToStudyFolder
            )

            dao.markDownloadCompleted(
                id = downloadId,
                finalSize = saved.fileSizeBytes,
                mediaStoreUri = saved.uriString,
                displayPath = saved.displayPath
            )
            completedOk = true
            Result.success()
        } catch (t: Throwable) {
            val mapped = DownloadErrorMapper.fromThrowable(t, record.platformName)
            val finalErr = if (mapped is DownloadError.ExtractionFailed) {
                DownloadError.MergeFailed(t.message ?: "Mux/verify failure")
            } else {
                mapped
            }
            val structuredDiag = DiagnosticRedactor.buildStructuredDiagnostic(
                platform = record.platformName,
                failStage = "MERGING",
                error = finalErr,
                formatId = record.formatExpression ?: record.qualityLabel,
                tempPath = tempDir.absolutePath
            )
            dao.updateDownloadFailure(
                id = downloadId,
                status = RafidDownloadStatus.FAILED.name,
                errorMessage = finalErr.userMessageAr,
                engineState = "FAILED",
                errorCode = finalErr.code,
                diagnosticLog = structuredDiag
            )
            Result.failure()
        } finally {
            // Preserve partial tracks on failure so retries don't wipe downloaded segments
            if (completedOk) {
                runCatching { tempDir.deleteRecursively() }
            }
        }
    }
}
