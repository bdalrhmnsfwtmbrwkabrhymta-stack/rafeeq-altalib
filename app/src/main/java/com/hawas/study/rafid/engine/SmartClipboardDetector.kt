package com.hawas.study.rafid.engine

import android.content.ClipboardManager
import android.content.Context

object SmartClipboardDetector {
    fun readClipboardUrl(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            if (!cm.hasPrimaryClip()) return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val raw = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
            AdAndTrackerShield.extractFirstUrl(raw)
        } catch (_: Exception) {
            null
        }
    }
}
