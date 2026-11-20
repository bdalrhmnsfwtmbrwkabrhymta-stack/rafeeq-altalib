package com.example.download.core.model

import com.example.download.data.DownloadStatus
import com.hawas.study.rafid.models.RafidDownloadStatus

/**
 * Explicit formal Finite State Machine (FSM) for the unified Hawas download engine.
 * Every transition across QUEUED, RESOLVING, READY, DOWNLOADING, PAUSING, PAUSED,
 * RETRYING, VERIFYING, MERGING, COMPLETED, FAILED, and CANCELLED is strictly validated.
 */
enum class EngineDownloadState {
    QUEUED,
    RESOLVING,
    READY,
    DOWNLOADING,
    PAUSING,
    PAUSED,
    RETRYING,
    VERIFYING,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED || this == CANCELLED

    fun isActive(): Boolean = this == RESOLVING ||
        this == DOWNLOADING ||
        this == PAUSING ||
        this == RETRYING ||
        this == VERIFYING ||
        this == MERGING

    fun toSmartDownloadStatus(): DownloadStatus = when (this) {
        QUEUED, READY -> DownloadStatus.QUEUED
        RESOLVING -> DownloadStatus.EXTRACTING
        DOWNLOADING, RETRYING -> DownloadStatus.DOWNLOADING
        PAUSING, PAUSED -> DownloadStatus.PAUSED
        VERIFYING, MERGING -> DownloadStatus.MERGING
        COMPLETED -> DownloadStatus.COMPLETED
        FAILED -> DownloadStatus.FAILED
        CANCELLED -> DownloadStatus.CANCELED
    }

    fun toRafidStatus(): RafidDownloadStatus = when (this) {
        QUEUED, RESOLVING, READY -> RafidDownloadStatus.QUEUED
        DOWNLOADING, RETRYING -> RafidDownloadStatus.DOWNLOADING
        PAUSING, PAUSED -> RafidDownloadStatus.PAUSED
        VERIFYING, MERGING -> RafidDownloadStatus.MUXING
        COMPLETED -> RafidDownloadStatus.COMPLETED
        FAILED -> RafidDownloadStatus.FAILED
        CANCELLED -> RafidDownloadStatus.CANCELLED
    }

    companion object {
        fun fromSmartDownloadStatus(status: DownloadStatus): EngineDownloadState = when (status) {
            DownloadStatus.QUEUED -> QUEUED
            DownloadStatus.EXTRACTING -> RESOLVING
            DownloadStatus.DOWNLOADING -> DOWNLOADING
            DownloadStatus.PAUSED -> PAUSED
            DownloadStatus.WAITING_NETWORK -> PAUSED
            DownloadStatus.MERGING -> MERGING
            DownloadStatus.COMPLETED -> COMPLETED
            DownloadStatus.FAILED -> FAILED
            DownloadStatus.CANCELED -> CANCELLED
        }

        fun fromRafidStatus(status: RafidDownloadStatus): EngineDownloadState = when (status) {
            RafidDownloadStatus.QUEUED -> QUEUED
            RafidDownloadStatus.DOWNLOADING -> DOWNLOADING
            RafidDownloadStatus.PAUSED -> PAUSED
            RafidDownloadStatus.MUXING -> MERGING
            RafidDownloadStatus.COMPLETED -> COMPLETED
            RafidDownloadStatus.FAILED -> FAILED
            RafidDownloadStatus.CANCELLED -> CANCELLED
        }
    }
}

object DownloadStateMachine {

    private val allowedTransitions: Map<EngineDownloadState, Set<EngineDownloadState>> = mapOf(
        EngineDownloadState.QUEUED to setOf(
            EngineDownloadState.RESOLVING,
            EngineDownloadState.READY,
            EngineDownloadState.DOWNLOADING,
            EngineDownloadState.PAUSING,
            EngineDownloadState.PAUSED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.RESOLVING to setOf(
            EngineDownloadState.READY,
            EngineDownloadState.DOWNLOADING,
            EngineDownloadState.PAUSING,
            EngineDownloadState.PAUSED,
            EngineDownloadState.RETRYING,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.READY to setOf(
            EngineDownloadState.DOWNLOADING,
            EngineDownloadState.PAUSING,
            EngineDownloadState.PAUSED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.DOWNLOADING to setOf(
            EngineDownloadState.PAUSING,
            EngineDownloadState.PAUSED,
            EngineDownloadState.RETRYING,
            EngineDownloadState.VERIFYING,
            EngineDownloadState.MERGING,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.PAUSING to setOf(
            EngineDownloadState.PAUSED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.PAUSED to setOf(
            EngineDownloadState.QUEUED,
            EngineDownloadState.RESOLVING,
            EngineDownloadState.DOWNLOADING,
            EngineDownloadState.CANCELLED,
            EngineDownloadState.FAILED
        ),
        EngineDownloadState.RETRYING to setOf(
            EngineDownloadState.RESOLVING,
            EngineDownloadState.DOWNLOADING,
            EngineDownloadState.PAUSING,
            EngineDownloadState.PAUSED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.VERIFYING to setOf(
            EngineDownloadState.MERGING,
            EngineDownloadState.COMPLETED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.MERGING to setOf(
            EngineDownloadState.VERIFYING,
            EngineDownloadState.COMPLETED,
            EngineDownloadState.FAILED,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.COMPLETED to emptySet(),
        EngineDownloadState.FAILED to setOf(
            EngineDownloadState.QUEUED,
            EngineDownloadState.RESOLVING,
            EngineDownloadState.RETRYING,
            EngineDownloadState.CANCELLED
        ),
        EngineDownloadState.CANCELLED to setOf(
            EngineDownloadState.QUEUED,
            EngineDownloadState.RESOLVING
        )
    )

    fun canTransition(from: EngineDownloadState, to: EngineDownloadState): Boolean {
        if (from == to) return true
        return allowedTransitions[from]?.contains(to) == true
    }

    fun transition(from: EngineDownloadState, to: EngineDownloadState): EngineDownloadState {
        require(canTransition(from, to)) {
            "Invalid download state transition: $from -> $to"
        }
        return to
    }
}
