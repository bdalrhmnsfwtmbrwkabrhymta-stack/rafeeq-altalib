package com.hawas.study.rafid.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hawas.study.rafid.models.RafidBookmarkEntity
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidExcludedDomainEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface RafidDao {
    // Downloads
    @Query("SELECT * FROM rafid_downloads ORDER BY createdAtMillis DESC")
    fun observeAllDownloads(): Flow<List<RafidDownloadEntity>>

    @Query("SELECT * FROM rafid_downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): RafidDownloadEntity?

    @Query("SELECT * FROM rafid_downloads WHERE status IN ('DOWNLOADING', 'MUXING')")
    suspend fun getActiveDownloads(): List<RafidDownloadEntity>

    @Query("SELECT * FROM rafid_downloads WHERE status = 'QUEUED' ORDER BY createdAtMillis ASC")
    suspend fun getQueuedDownloads(): List<RafidDownloadEntity>

    @Query("SELECT COUNT(*) FROM rafid_downloads WHERE status IN ('DOWNLOADING', 'MUXING')")
    suspend fun countActiveDownloads(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: RafidDownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloads(items: List<RafidDownloadEntity>)

    @Query(
        "UPDATE rafid_downloads SET status = :status, downloadedBytes = :downloadedBytes, " +
            "totalBytes = :totalBytes, speedBytesPerSec = :speedBytesPerSec, etaSeconds = :etaSeconds " +
            "WHERE id = :id"
    )
    suspend fun updateDownloadProgress(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long
    )

    @Query(
        "UPDATE rafid_downloads SET status = :status, speedBytesPerSec = 0, etaSeconds = 0, " +
            "errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun updateDownloadStatus(id: String, status: String, errorMessage: String? = null)

    @Query(
        "UPDATE rafid_downloads SET status = 'QUEUED', engineState = 'QUEUED', speedBytesPerSec = 0, " +
            "etaSeconds = 0, errorMessage = NULL WHERE id = :id AND status IN ('DOWNLOADING', 'MUXING')"
    )
    suspend fun resetOrphanedActiveToQueued(id: String)

    @Query(
        "UPDATE rafid_downloads SET engineState = :engineState, errorCode = :errorCode, " +
            "diagnosticLog = COALESCE(:diagnosticLog, diagnosticLog) WHERE id = :id"
    )
    suspend fun updateEngineState(
        id: String,
        engineState: String,
        errorCode: String? = null,
        diagnosticLog: String? = null
    )

    @Query(
        "UPDATE rafid_downloads SET status = :status, speedBytesPerSec = 0, etaSeconds = 0, " +
            "errorMessage = :errorMessage, engineState = :engineState, errorCode = :errorCode, " +
            "diagnosticLog = :diagnosticLog WHERE id = :id"
    )
    suspend fun updateDownloadFailure(
        id: String,
        status: String,
        errorMessage: String?,
        engineState: String,
        errorCode: String?,
        diagnosticLog: String?
    )

    @Query(
        "UPDATE rafid_downloads SET videoStreamUrl = :videoStreamUrl, audioStreamUrl = :audioStreamUrl, " +
            "headersJson = :headersJson, cookiesHeader = :cookiesHeader WHERE id = :id"
    )
    suspend fun updateRefreshedStreams(
        id: String,
        videoStreamUrl: String,
        audioStreamUrl: String?,
        headersJson: String?,
        cookiesHeader: String?
    )

    @Query(
        "UPDATE rafid_downloads SET videoStreamUrl = :videoStreamUrl, audioStreamUrl = :audioStreamUrl, " +
            "totalBytes = CASE WHEN :totalBytes > 0 THEN :totalBytes ELSE totalBytes END, " +
            "headersJson = :headersJson, cookiesHeader = COALESCE(:cookiesHeader, cookiesHeader), " +
            "formatExpression = COALESCE(:formatExpression, formatExpression), " +
            "qualityLabel = COALESCE(:qualityLabel, qualityLabel), " +
            "isMuxRequired = :isMuxRequired " +
            "WHERE id = :id AND status = 'QUEUED'"
    )
    suspend fun updatePreExtractedStreamsIfQueued(
        id: String,
        videoStreamUrl: String,
        audioStreamUrl: String?,
        totalBytes: Long,
        headersJson: String?,
        cookiesHeader: String?,
        formatExpression: String?,
        qualityLabel: String?,
        isMuxRequired: Boolean
    ): Int

    @Query(
        "UPDATE rafid_downloads SET status = 'COMPLETED', engineState = 'COMPLETED', errorCode = NULL, downloadedBytes = :finalSize, totalBytes = :finalSize, " +
            "speedBytesPerSec = 0, etaSeconds = 0, mediaStoreUri = :mediaStoreUri, " +
            "displayPath = :displayPath, completedAtMillis = :completedAtMillis, errorMessage = NULL WHERE id = :id"
    )
    suspend fun markDownloadCompleted(
        id: String,
        finalSize: Long,
        mediaStoreUri: String?,
        displayPath: String,
        completedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM rafid_downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("DELETE FROM rafid_downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompletedDownloads()

    // Bookmarks (Browser quick platforms)
    @Query("SELECT * FROM rafid_bookmarks ORDER BY sortOrder ASC")
    fun observeBookmarks(): Flow<List<RafidBookmarkEntity>>

    @Query("SELECT COUNT(*) FROM rafid_bookmarks")
    suspend fun countBookmarks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: RafidBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmarksIfAbsent(bookmarks: List<RafidBookmarkEntity>)

    @Query("DELETE FROM rafid_bookmarks WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomBookmark(id: String)

    // Protection Excluded Domains
    @Query("SELECT * FROM rafid_excluded_domains ORDER BY addedAtMillis DESC")
    fun observeExcludedDomains(): Flow<List<RafidExcludedDomainEntity>>

    @Query("SELECT domain FROM rafid_excluded_domains")
    suspend fun getAllExcludedDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addExcludedDomain(entity: RafidExcludedDomainEntity)

    @Query("DELETE FROM rafid_excluded_domains WHERE domain = :domain")
    suspend fun removeExcludedDomain(domain: String)
}

@Database(
    entities = [
        RafidDownloadEntity::class,
        RafidBookmarkEntity::class,
        RafidExcludedDomainEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RafidDatabase : RoomDatabase() {
    abstract fun rafidDao(): RafidDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN headersJson TEXT")
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN formatExpression TEXT")
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN audioLanguage TEXT")
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN engineState TEXT NOT NULL DEFAULT 'QUEUED'")
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN errorCode TEXT")
                db.execSQL("ALTER TABLE rafid_downloads ADD COLUMN diagnosticLog TEXT")
            }
        }
        val DEFAULT_PLATFORM_BOOKMARKS = listOf(
            RafidBookmarkEntity(
                id = "bm_instagram",
                titleAr = "إنستغرام",
                titleEn = "Instagram",
                url = "https://www.instagram.com/",
                platformKey = "INSTAGRAM",
                sortOrder = 0,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_tiktok",
                titleAr = "تيك توك",
                titleEn = "TikTok",
                url = "https://www.tiktok.com/",
                platformKey = "TIKTOK",
                sortOrder = 1,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_pinterest",
                titleAr = "بينتيريست",
                titleEn = "Pinterest",
                url = "https://www.pinterest.com/",
                platformKey = "PINTEREST",
                sortOrder = 2,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_youtube",
                titleAr = "يوتيوب",
                titleEn = "YouTube",
                url = "https://m.youtube.com/",
                platformKey = "YOUTUBE",
                sortOrder = 3,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_study",
                titleAr = "منصات الدراسة",
                titleEn = "Study Sites",
                url = "https://www.khanacademy.org/",
                platformKey = "STUDY",
                sortOrder = 4,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_x",
                titleAr = "X",
                titleEn = "X",
                url = "https://x.com/",
                platformKey = "X",
                sortOrder = 5,
                isDefault = true
            ),
            RafidBookmarkEntity(
                id = "bm_facebook",
                titleAr = "فيسبوك",
                titleEn = "Facebook",
                url = "https://m.facebook.com/",
                platformKey = "FACEBOOK",
                sortOrder = 6,
                isDefault = true
            )
        )

        @Volatile
        private var INSTANCE: RafidDatabase? = null

        fun getInstance(context: Context): RafidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RafidDatabase::class.java,
                    "rafid_media_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching { ensureDefaultsSeeded(getInstance(context).rafidDao()) }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun ensureDefaultsSeeded(dao: RafidDao) {
            runCatching {
                if (dao.countBookmarks() == 0) {
                    dao.insertBookmarksIfAbsent(DEFAULT_PLATFORM_BOOKMARKS)
                }
            }
        }
    }
}
