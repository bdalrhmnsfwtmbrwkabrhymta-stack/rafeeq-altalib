package com.example.download.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.download.core.downloader.ChunkMetadataStore
import com.example.download.core.downloader.SegmentChunk
import com.example.download.core.model.DownloadError
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.updater.EngineInstallState
import com.example.download.core.updater.EngineUpdateStateStore
import com.example.download.data.ChunkEntity
import com.example.download.data.DownloadDao
import kotlinx.coroutines.flow.first

private val Context.engineUpdaterDataStore by preferencesDataStore(name = "hawas_engine_updater_state")

class RoomChunkMetadataStore(
    private val dao: DownloadDao,
    private val onStateCallback: suspend (downloadId: String, state: EngineDownloadState, error: DownloadError?) -> Unit = { _, _, _ -> }
) : ChunkMetadataStore {

    override suspend fun getChunks(downloadId: String): List<SegmentChunk> {
        return dao.getChunksForDownload(downloadId).map {
            SegmentChunk(
                index = it.chunkIndex,
                startByte = it.startByte,
                endByte = it.endByte,
                downloadedBytes = it.downloadedBytes,
                completed = it.completed,
                etag = it.etag
            )
        }
    }

    override suspend fun saveChunks(downloadId: String, chunks: List<SegmentChunk>) {
        val entities = chunks.map {
            ChunkEntity(
                downloadId = downloadId,
                chunkIndex = it.index,
                startByte = it.startByte,
                endByte = it.endByte,
                downloadedBytes = it.downloadedBytes,
                completed = it.completed,
                etag = it.etag
            )
        }
        dao.replaceChunksForDownload(downloadId, entities)
    }

    override suspend fun clearChunks(downloadId: String) {
        dao.deleteChunksForDownload(downloadId)
    }

    override suspend fun updateProgress(
        downloadId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        activeThreads: Int
    ) {
        dao.updateProgress(downloadId, downloadedBytes, totalBytes, activeThreads)
    }

    override suspend fun updateServerMetadata(
        downloadId: String,
        finalUrl: String,
        etag: String?,
        lastModified: String?
    ) {
        val existing = dao.getDownloadById(downloadId)
        dao.updateResolvedUrl(
            id = downloadId,
            resolvedUrl = finalUrl,
            headersJson = existing?.headersJson,
            etag = etag,
            lastModified = lastModified
        )
    }

    override suspend fun onStateChanged(
        downloadId: String,
        state: EngineDownloadState,
        error: DownloadError?
    ) {
        dao.updateEngineState(downloadId, state.name, error?.code)
        onStateCallback(downloadId, state, error)
    }
}

class DataStoreEngineUpdateStore(
    private val context: Context
) : EngineUpdateStateStore {

    private object Keys {
        val CURRENT_VERSION = stringPreferencesKey("current_version")
        val CURRENT_VERSION_CODE = longPreferencesKey("current_version_code")
        val PREVIOUS_VERSION = stringPreferencesKey("previous_version")
        val PREVIOUS_VERSION_CODE = longPreferencesKey("previous_version_code")
        val MANIFEST_ETAG = stringPreferencesKey("manifest_etag")
        val MANIFEST_LAST_MODIFIED = stringPreferencesKey("manifest_last_modified")
        val LAST_CHECK_MS = longPreferencesKey("last_check_ms")
        val ACTIVE_PATH = stringPreferencesKey("active_artifact_path")
        val BACKUP_PATH = stringPreferencesKey("backup_artifact_path")
    }

    override suspend fun readState(): EngineInstallState {
        val prefs = context.engineUpdaterDataStore.data.first()
        return EngineInstallState(
            currentVersion = prefs[Keys.CURRENT_VERSION] ?: "bundled-0",
            currentVersionCode = prefs[Keys.CURRENT_VERSION_CODE] ?: 0L,
            previousVersion = prefs[Keys.PREVIOUS_VERSION],
            previousVersionCode = prefs[Keys.PREVIOUS_VERSION_CODE] ?: 0L,
            manifestEtag = prefs[Keys.MANIFEST_ETAG],
            manifestLastModified = prefs[Keys.MANIFEST_LAST_MODIFIED],
            lastCheckMs = prefs[Keys.LAST_CHECK_MS] ?: 0L,
            activeArtifactPath = prefs[Keys.ACTIVE_PATH],
            backupArtifactPath = prefs[Keys.BACKUP_PATH]
        )
    }

    override suspend fun saveState(state: EngineInstallState) {
        context.engineUpdaterDataStore.edit { prefs ->
            prefs[Keys.CURRENT_VERSION] = state.currentVersion
            prefs[Keys.CURRENT_VERSION_CODE] = state.currentVersionCode
            if (state.previousVersion != null) prefs[Keys.PREVIOUS_VERSION] = state.previousVersion else prefs.remove(Keys.PREVIOUS_VERSION)
            prefs[Keys.PREVIOUS_VERSION_CODE] = state.previousVersionCode
            if (state.manifestEtag != null) prefs[Keys.MANIFEST_ETAG] = state.manifestEtag else prefs.remove(Keys.MANIFEST_ETAG)
            if (state.manifestLastModified != null) prefs[Keys.MANIFEST_LAST_MODIFIED] = state.manifestLastModified else prefs.remove(Keys.MANIFEST_LAST_MODIFIED)
            prefs[Keys.LAST_CHECK_MS] = state.lastCheckMs
            if (state.activeArtifactPath != null) prefs[Keys.ACTIVE_PATH] = state.activeArtifactPath else prefs.remove(Keys.ACTIVE_PATH)
            if (state.backupArtifactPath != null) prefs[Keys.BACKUP_PATH] = state.backupArtifactPath else prefs.remove(Keys.BACKUP_PATH)
        }
    }
}
