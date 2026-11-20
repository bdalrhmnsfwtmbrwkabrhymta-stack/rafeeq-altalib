package com.example.download.core.domain

import com.example.download.core.model.DownloadStateMachine
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.core.updater.EngineUpdateManager
import com.example.download.core.updater.EngineUpdateResult
import com.example.download.data.DownloadEntity
import com.example.download.data.ExtractionResult
import com.example.download.data.MediaQualityOption
import com.example.download.EnqueueOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository contract separating domain UseCases from Room, DataStore, and WorkManager details.
 */
interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadEntity>>
    suspend fun getById(id: String): DownloadEntity?
    suspend fun enqueueSingle(video: ExtractionResult.SingleVideo, quality: MediaQualityOption): EnqueueOutcome
    suspend fun startOrSchedule(id: String)
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun cancel(id: String, deleteTemp: Boolean = true)
    suspend fun retry(id: String)
}

class ResolveMediaUseCase(
    private val resolver: UnifiedMediaResolver
) {
    suspend operator fun invoke(request: ResolveRequest): ResolvedMediaResult =
        resolver.resolve(request)
}

class GetMediaFormatsUseCase(
    private val resolver: UnifiedMediaResolver
) {
    suspend operator fun invoke(request: ResolveRequest): Result<List<ResolvedMediaFormat>> {
        return when (val res = resolver.resolve(request.copy(checkCollection = false))) {
            is ResolvedMediaResult.SingleMedia -> Result.success(res.formats)
            is ResolvedMediaResult.MediaCollection -> Result.success(emptyList())
            is ResolvedMediaResult.Failure -> Result.failure(
                com.example.download.core.model.DownloadEngineException(res.error)
            )
        }
    }
}

class AddDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(
        video: ExtractionResult.SingleVideo,
        quality: MediaQualityOption
    ): EnqueueOutcome = repository.enqueueSingle(video, quality)
}

class StartDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        val current = repository.getById(downloadId)
        if (current != null) {
            val curState = parseEntityState(current)
            if (DownloadStateMachine.canTransition(curState, EngineDownloadState.DOWNLOADING)) {
                repository.startOrSchedule(downloadId)
            }
        }
    }
}

class PauseDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        val current = repository.getById(downloadId)
        if (current != null) {
            val curState = parseEntityState(current)
            if (!curState.isTerminal() && DownloadStateMachine.canTransition(curState, EngineDownloadState.PAUSING)) {
                repository.pause(downloadId)
            }
        }
    }
}

class ResumeDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        val current = repository.getById(downloadId)
        if (current != null) {
            val curState = parseEntityState(current)
            if (curState != EngineDownloadState.COMPLETED &&
                DownloadStateMachine.canTransition(curState, EngineDownloadState.QUEUED)
            ) {
                repository.resume(downloadId)
            }
        }
    }
}

class CancelDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String, deleteTemp: Boolean = true) {
        val current = repository.getById(downloadId)
        if (current != null) {
            val curState = parseEntityState(current)
            if (DownloadStateMachine.canTransition(curState, EngineDownloadState.CANCELLED) || curState.isTerminal()) {
                repository.cancel(downloadId, deleteTemp)
            }
        }
    }
}

class RetryDownloadUseCase(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(downloadId: String) {
        val current = repository.getById(downloadId)
        if (current != null) {
            val curState = parseEntityState(current)
            if (curState == EngineDownloadState.FAILED ||
                curState == EngineDownloadState.PAUSED ||
                curState == EngineDownloadState.CANCELLED ||
                curState == EngineDownloadState.QUEUED
            ) {
                repository.retry(downloadId)
            }
        }
    }
}

class GetDownloadStatusUseCase(
    private val repository: DownloadRepository
) {
    fun observeState(downloadId: String): Flow<EngineDownloadState?> =
        repository.observeAll().map { list ->
            list.firstOrNull { it.id == downloadId }?.let(::parseEntityState)
        }

    suspend fun getState(downloadId: String): EngineDownloadState? =
        repository.getById(downloadId)?.let(::parseEntityState)
}

class UpdateVideoEngineUseCase(
    private val updateManager: EngineUpdateManager
) {
    suspend operator fun invoke(
        manifestUrl: String,
        forceCheck: Boolean = false
    ): EngineUpdateResult = updateManager.checkAndApplyUpdate(
        manifestUrl = manifestUrl,
        forceCheck = forceCheck
    )
}

internal fun parseEntityState(entity: DownloadEntity): EngineDownloadState {
    return runCatching { EngineDownloadState.valueOf(entity.engineState) }
        .getOrElse { EngineDownloadState.fromSmartDownloadStatus(entity.status) }
}
