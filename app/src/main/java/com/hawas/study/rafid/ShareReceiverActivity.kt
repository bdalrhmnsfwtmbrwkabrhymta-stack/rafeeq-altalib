package com.hawas.study.rafid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.MainActivity
import com.hawas.study.rafid.engine.AdAndTrackerShield

/**
 * Receives external android.intent.action.SEND ("مشاركة") intents from apps like
 * YouTube, Pinterest, TikTok, Instagram, X, and browsers, forwarding the shared URL
 * directly into Rafid's SmartDownloadBottomSheet inside MainActivity.
 */
class ShareReceiverActivity : Activity() {

    companion object {
        const val EXTRA_RAFID_SHARED_URL = "EXTRA_RAFID_SHARED_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }

        val extractedUrl = AdAndTrackerShield.extractFirstUrl(sharedText) ?: sharedText?.trim()
        val targetIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "rafid")
            if (!extractedUrl.isNullOrBlank()) {
                putExtra(EXTRA_RAFID_SHARED_URL, extractedUrl)
            }
        }
        startActivity(targetIntent)
        finish()
    }
}
