package com.example.download.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.MainActivity
import com.example.download.extract.LinkExtractor

class DownloadShareReceiverActivity : Activity() {

    companion object {
        const val EXTRA_SMART_DOWNLOAD_URL = "EXTRA_SMART_DOWNLOAD_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }

        val extractedUrl = LinkExtractor.extractFirstHttpUrl(sharedText) ?: sharedText?.trim()
        val targetIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "smart_download")
            if (!extractedUrl.isNullOrBlank()) {
                putExtra(EXTRA_SMART_DOWNLOAD_URL, extractedUrl)
            }
        }
        startActivity(targetIntent)
        finish()
    }
}
