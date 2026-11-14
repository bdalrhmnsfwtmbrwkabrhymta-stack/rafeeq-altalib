package com.example.domain.copy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object BestCopyManager {

    /**
     * Copies plain or formatted text to system clipboard.
     */
    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val cleanedText = cleanTextForCopy(text)
            val clip = ClipData.newPlainText(label, cleanedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "تم النسخ بنجاح ✨", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "فشل النسخ: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Cleans OCR or PDF extracted text from line split issues and extra spaces.
     */
    fun cleanTextForCopy(rawText: String): String {
        return rawText
            .replace(Regex("(?<=\\w)-\\n(?=\\w)"), "") // rejoin hyphenated split words
            .replace(Regex("[ \\t]+"), " ")            // normalize multi-spaces
            .replace(Regex("\\n{3,}"), "\n\n")         // max 2 consecutive newlines
            .trim()
    }

    /**
     * Shares content using standard Android Share Sheet.
     */
    fun shareContent(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "مشاركة المحتوى عبر:")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
