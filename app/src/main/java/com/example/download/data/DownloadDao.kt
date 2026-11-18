package com.example.download.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM smart_downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM smart_downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query(
        "SELECT * FROM smart_downloads WHERE originalUrl = :url AND qualityId = :qualityId " +
            "AND status IN ('QUEUED', 'EXTRACTING', 'DOWNLOADING', 'PAUSED', 'WAITING_NETWORK', 'MERGING', 'COMPLETED') " +
            "LIMIT 1"
    )
    suspend fun findDuplicate(url: String, qualityId: String): DownloadEntity?

    @Query(
        "SELECT * FROM smart_downloads WHERE status IN ('QUEUED', 'EXTRACTING', 'DOWNLOADING', 'WAITING_NETWORK', 'MERGING') " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getResumableDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM smart_downloads WHERE status IN ('DOWNLOADING', 'EXTRACTING', 'MERGING')")
    suspend fun getCurrentlyActiveDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM smart_downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getQueuedDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM smart_downloads WHERE status = 'WAITING_NETWORK' ORDER BY createdAt ASC")
    suspend fun getWaitingNetworkDownloads(): List<DownloadEntity>

    @Query("SELECT id FROM smart_downloads WHERE status NOT IN ('COMPLETED', 'CANCELED')")
    suspend fun getNonTerminalDownloadIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: DownloadEntity)

    @Query(
        "UPDATE smart_downloads SET status = :status, activeThreads = :threads, " +
            "errorFriendlyMessage = :errorMsg, diagnosticLog = :diagnostic, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        threads: Int = 0,
        errorMsg: String? = null,
        diagnostic: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE smart_downloads SET downloadedBytes = :downloaded, totalBytes = :total, " +
            "activeThreads = :threads, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        downloaded: Long,
        total: Long,
        threads: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE smart_downloads SET resolvedUrl = :resolvedUrl, headersJson = :headersJson, " +
            "etag = :etag, lastModified = :lastModified, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateResolvedUrl(
        id: String,
        resolvedUrl: String,
        headersJson: String?,
        etag: String?,
        lastModified: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE smart_downloads SET status = 'COMPLETED', downloadedBytes = :finalBytes, totalBytes = :finalBytes, " +
            "outputUri = :outputUri, outputDisplayPath = :displayPath, activeThreads = 0, " +
            "errorFriendlyMessage = NULL, completedAt = :completedAt, updatedAt = :completedAt WHERE id = :id"
    )
    suspend fun markCompleted(
        id: String,
        finalBytes: Long,
        outputUri: String,
        displayPath: String,
        completedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM smart_downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("SELECT * FROM smart_download_chunks WHERE downloadId = :downloadId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDownload(downloadId: String): List<ChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Query("DELETE FROM smart_download_chunks WHERE downloadId = :downloadId")
    suspend fun deleteChunksForDownload(downloadId: String)

    @Transaction
    suspend fun replaceChunksForDownload(downloadId: String, chunks: List<ChunkEntity>) {
        deleteChunksForDownload(downloadId)
        insertChunks(chunks)
    }
}
