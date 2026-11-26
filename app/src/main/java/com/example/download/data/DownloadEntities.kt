package com.example.download.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    EXTRACTING,
    DOWNLOADING,
    PAUSED,
    WAITING_NETWORK,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELED
}

@Entity(tableName = "smart_downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val originalUrl: String,
    val resolvedUrl: String = "",
    val audioUrl: String? = null,
    val title: String,
    val thumbnailUrl: String? = null,
    val platform: String = "مباشر",
    val qualityId: String,
    val qualityLabel: String,
    val formatId: String? = null,
    val isAudioOnly: Boolean = false,
    val isHlsOrDash: Boolean = false,
    val needsMux: Boolean = false,
    val fileName: String,
    val mimeType: String = "video/mp4",
    val tempPartPath: String,
    val outputUri: String? = null,
    val outputDisplayPath: String = "",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val activeThreads: Int = 0,
    val etag: String? = null,
    val lastModified: String? = null,
    val referer: String? = null,
    val headersJson: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val engineState: String = "QUEUED",
    val errorCode: String? = null,
    val errorFriendlyMessage: String? = null,
    val diagnosticLog: String? = null,
    val durationSeconds: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "smart_download_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("downloadId")]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: String,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val completed: Boolean = false,
    val etag: String? = null
)

data class MediaQualityOption(
    val id: String,
    val labelAr: String,
    val subLabel: String,
    val resolutionHeight: Int,
    val isAudioOnly: Boolean = false,
    val formatId: String? = null,
    val ext: String = "mp4",
    val directUrl: String = "",
    val audioUrl: String? = null,
    val estimatedBytes: Long = -1L,
    val isApproximateSize: Boolean = false,
    val needsMux: Boolean = false,
    val isHlsOrDash: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val languageLabel: String = "الأصلي",
    val hasAudio: Boolean = true
)

data class PlaylistEntry(
    val index: Int,
    val title: String,
    val url: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val selected: Boolean = true
)

sealed class ExtractionResult {
    data class SingleVideo(
        val originalUrl: String,
        val resolvedUrl: String,
        val title: String,
        val thumbnailUrl: String?,
        val platform: String,
        val durationSeconds: Int,
        val qualities: List<MediaQualityOption>,
        val referer: String? = null,
        val headers: Map<String, String> = emptyMap()
    ) : ExtractionResult()

    data class Playlist(
        val originalUrl: String,
        val title: String,
        val platform: String,
        val entries: List<PlaylistEntry>
    ) : ExtractionResult()

    data class DrmProtected(
        val messageAr: String,
        val diagnostic: String
    ) : ExtractionResult()

    data class Failed(
        val friendlyMessageAr: String,
        val diagnostic: String,
        val suggestCookieLogin: Boolean = false
    ) : ExtractionResult()
}

data class LiveProgressSnapshot(
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val activeThreads: Int = 0
)

data class DownloadSettings(
    val chunkCount: Int = 8,
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = 2,
    val speedLimitKBps: Int = 0,
    val lastSelectedQualityId: String = "1080p"
)
