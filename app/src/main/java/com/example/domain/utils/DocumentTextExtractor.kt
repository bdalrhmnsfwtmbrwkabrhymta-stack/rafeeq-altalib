package com.example.domain.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.domain.ai.BookOcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.regex.Pattern
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

object DocumentTextExtractor {

    suspend fun extractTextFromUri(context: Context, uri: Uri, mimeType: String? = null, fileName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val name = fileName ?: uri.lastPathSegment ?: ""
            val lowerName = name.lowercase()
            val type = mimeType ?: context.contentResolver.getType(uri) ?: ""

            when {
                lowerName.endsWith(".docx") || type.contains("wordprocessingml") -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = extractFromDocxStream(stream)
                        if (text.isNotBlank()) Result.success(text)
                        else Result.failure(Exception("لم نتمكن من قراءة محتوى ملف Word (.docx). تأكد من صحة الملف."))
                    } ?: Result.failure(Exception("تعذر فتح ملف Word."))
                }
                lowerName.endsWith(".txt") || type.contains("text/plain") -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        Result.success(text)
                    } ?: Result.failure(Exception("تعذر فتح الملف النصي."))
                }
                lowerName.endsWith(".pdf") || type.contains("pdf") -> {
                    val text = extractFromPdfUri(context, uri)
                    if (text.isNotBlank()) Result.success(text)
                    else Result.failure(Exception("لم يتم العثور على نصوص في ملف PDF."))
                }
                type.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") -> {
                    val ocrResult = suspendCancellableCoroutine { continuation ->
                        BookOcrHelper.recognizeTextFromUri(
                            context = context,
                            uri = uri,
                            onSuccess = { res -> continuation.resume(Result.success(res.fullText)) },
                            onFailure = { e -> continuation.resume(Result.failure(e)) }
                        )
                    }
                    ocrResult
                }
                else -> {
                    // Try reading as plain text first
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val text = stream.bufferedReader().readText()
                            if (text.isNotBlank() && text.any { it.isLetter() }) {
                                return@withContext Result.success(text)
                            }
                        }
                    } catch (_: Exception) {}
                    Result.failure(Exception("نوع الملف غير مدعوم حالياً. يرجى اختيار ملف PDF، DOCX، TXT، أو صورة."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractFromDocxStream(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sb = StringBuilder()
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zip.bufferedReader().readText()
                val matcher = Pattern.compile("<w:t[^>]*>(.*?)</w:t>").matcher(xml)
                while (matcher.find()) {
                    sb.append(matcher.group(1)).append(" ")
                }
                break
            }
            entry = zip.nextEntry
        }
        return sb.toString().trim()
    }

    private suspend fun extractFromPdfUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount.coerceAtMost(10) // Process up to 10 pages for speed
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = (page.width * 1.5f).toInt()
                    val height = (page.height * 1.5f).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageText = suspendCancellableCoroutine<String> { continuation ->
                        BookOcrHelper.recognizeTextFromBitmap(
                            bitmap = bitmap,
                            onSuccess = { continuation.resume(it.fullText) },
                            onFailure = { continuation.resume("") }
                        )
                    }
                    if (pageText.isNotBlank()) {
                        sb.append("--- صفحة ${i + 1} ---\n").append(pageText).append("\n\n")
                    }
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
        sb.toString().trim()
    }
}
