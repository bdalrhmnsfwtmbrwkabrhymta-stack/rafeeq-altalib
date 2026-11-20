package com.example.domain.ai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.speech.tts.TextToSpeech
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper object providing:
 * 1. Real network connectivity check
 * 2. Arabic/Multilingual Text-to-Speech (TTS) manager
 * 3. Clean PDF exporter for study solutions, scientific explanations, and LaTeX formulas
 */
object AiChatStudyHelper {

    /**
     * Checks if the device has an active internet connection.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (e: Exception) {
            true // Fallback to true if permission or check fails
        }
    }

    /**
     * TextToSpeech manager supporting Arabic and English voice narration.
     */
    class ArabicTtsManager(private val context: Context) {
        private var tts: TextToSpeech? = null
        private var isInitialized = false
        private var isSpeaking = false

        init {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ar"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.getDefault()
                    }
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.95f)
                    isInitialized = true
                }
            }
        }

        fun speak(text: String, onSpeakingChange: (Boolean) -> Unit = {}) {
            if (!isInitialized || tts == null) return
            if (tts?.isSpeaking == true) {
                stop()
                onSpeakingChange(false)
                return
            }

            // Clean text from symbols, LaTeX brackets, and markdown for natural speech
            val cleanForSpeech = cleanTextForSpeech(text)
            val params = android.os.Bundle()
            tts?.speak(cleanForSpeech, TextToSpeech.QUEUE_FLUSH, params, "AiUtteranceId")
            onSpeakingChange(true)
        }

        fun stop() {
            try {
                tts?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun shutdown() {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tts = null
            isInitialized = false
        }

        private fun cleanTextForSpeech(input: String): String {
            return input
                .replace(Regex("""\$\$([^\$]+)\$\$"""), " المعادلة: $1 ")
                .replace(Regex("""\$([^\$]+)\$"""), " $1 ")
                .replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}"""), " $1 على $2 ")
                .replace(Regex("""\\times|\\cdot"""), " ضرب ")
                .replace(Regex("""\\sqrt\{([^}]+)\}"""), " جذر $1 ")
                .replace(Regex("""[\*#_`~>•]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }

    /**
     * Exports the explanation or full conversation into an elegant, formatted PDF file.
     */
    fun exportToPdf(
        context: Context,
        title: String,
        content: String,
        onComplete: (Boolean, String, Uri?) -> Unit
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points
            val margin = 40f
            val contentWidth = pageWidth - (margin * 2)

            val titlePaint = Paint().apply {
                color = Color.rgb(24, 76, 120) // Deep scholarly blue
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val mathBoxPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }

            val mathBorderPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val mathTextPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }

            // Split content into printable lines
            val rawLines = content.split("\n")
            val wrappedLines = mutableListOf<String>()

            for (rawLine in rawLines) {
                if (rawLine.isBlank()) {
                    wrappedLines.add("")
                    continue
                }
                var start = 0
                while (start < rawLine.length) {
                    val count = bodyPaint.breakText(rawLine, start, rawLine.length, true, contentWidth, null)
                    wrappedLines.add(rawLine.substring(start, start + count))
                    start += count
                }
            }

            val lineHeight = 18f
            val usableHeight = pageHeight - (margin * 2)
            var currentLineIndex = 0
            var pageNumber = 1

            while (currentLineIndex < wrappedLines.size || pageNumber == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = margin + 20f

                if (pageNumber == 1) {
                    canvas.drawText("حل وشرح الذكاء الاصطناعي - Gemini AI Tutor", margin, y, titlePaint)
                    y += 20f
                    val dateStr = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")).format(Date())
                    canvas.drawText("العنوان: $title  |  التاريخ: $dateStr", margin, y, metaPaint)
                    y += 15f
                    canvas.drawLine(margin, y, pageWidth - margin, y, mathBorderPaint)
                    y += 20f
                }

                while (currentLineIndex < wrappedLines.size && y + lineHeight < pageHeight - margin) {
                    val line = wrappedLines[currentLineIndex]
                    val isMathFormula = line.trim().startsWith("$$") || line.trim().contains("=") && (line.contains("+") || line.contains("\\") || line.contains("^"))
                    
                    if (isMathFormula) {
                        canvas.drawRect(margin, y - 12f, pageWidth - margin, y + 6f, mathBoxPaint)
                        canvas.drawRect(margin, y - 12f, pageWidth - margin, y + 6f, mathBorderPaint)
                        canvas.drawText(line, margin + 8f, y, mathTextPaint)
                    } else {
                        canvas.drawText(line, margin, y, bodyPaint)
                    }

                    y += lineHeight
                    currentLineIndex++
                }

                // Page number at bottom
                canvas.drawText("صفحة $pageNumber", pageWidth / 2f - 20f, pageHeight - margin / 2f, metaPaint)
                pdfDocument.finishPage(page)
                pageNumber++

                if (currentLineIndex >= wrappedLines.size) break
            }

            // Save to internal cache or external files dir
            val fileName = "Gemini_Study_${System.currentTimeMillis() % 100000}.pdf"
            val outputDir = File(context.cacheDir, "study_pdfs").apply { mkdirs() }
            val outputFile = File(outputDir, fileName)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            onComplete(true, "تم تصدير ملف PDF بنجاح: $fileName", uri)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false, "تعذر تصدير ملف PDF: ${e.message}", null)
        }
    }

    /**
     * Share or open the generated PDF file.
     */
    fun sharePdf(context: Context, uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف الشرح بصيغة PDF"))
    }
}
