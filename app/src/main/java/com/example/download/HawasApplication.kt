package com.example.download

import android.app.Application
import com.example.download.data.DownloadSettingsRepo
import com.example.download.extract.LinkExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HawasApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val settingsRepo = DownloadSettingsRepo(this@HawasApplication)
            LinkExtractor.ensureInitialized(this@HawasApplication)
            LinkExtractor.updateYtDlpIfStale(this@HawasApplication, settingsRepo)
            SmartDownloadManager.getInstance(this@HawasApplication).cleanOrphanTempFiles()
        }
    }
}
