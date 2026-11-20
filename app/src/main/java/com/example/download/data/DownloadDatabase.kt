package com.example.download.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class DownloadTypeConverters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.QUEUED)
}

@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DownloadTypeConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE smart_downloads ADD COLUMN engineState TEXT NOT NULL DEFAULT 'QUEUED'"
                )
                db.execSQL(
                    "ALTER TABLE smart_downloads ADD COLUMN errorCode TEXT"
                )
                db.execSQL(
                    "UPDATE smart_downloads SET engineState = CASE status " +
                        "WHEN 'EXTRACTING' THEN 'RESOLVING' " +
                        "WHEN 'WAITING_NETWORK' THEN 'PAUSED' " +
                        "WHEN 'CANCELED' THEN 'CANCELLED' " +
                        "ELSE status END"
                )
            }
        }

        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "hawas_smart_downloads.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
