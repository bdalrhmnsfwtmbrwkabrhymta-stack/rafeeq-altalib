package com.example.pdfstudio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

object PdfEngineHelper {
    private val pdfBoxInitialized = AtomicBoolean(false)
    private val renderMutex = Mutex()

    fun ensurePdfBoxInit(context: Context) {
        if (pdfBoxInitialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    fun getArabicUiTypeface(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    fun getStudioCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "pdf_studio_work")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getStudioExportsDir(context: Context): File {
        val dir = File(context.filesDir, "pdf_studio_exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name.ifBlank { "document.pdf" }
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "مستند.pdf" } ?: "مستند.pdf"
    }

    fun queryFileSizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").length()
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 ك.ب"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f م.ب", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f ك.ب", kb)
            else -> "$bytes بايت"
        }
    }

    private fun getRecentCacheDir(context: Context): File {
        val dir = File(context.filesDir, "pdf_studio_recent")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun copyUriToWorkingFile(
        context: Context,
        uriString: String,
        preferredName: String = "work.pdf"
    ): File? = withContext(Dispatchers.IO) {
        val persistentFallback = File(getRecentCacheDir(context), "doc_${uriString.hashCode()}.pdf")
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val src = File(uri.path ?: return@withContext null)
                if (src.exists() && src.length() > 0L) return@withContext src
            }
            val safeName = preferredName.replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]"), "_")
            val dest = File(getStudioCacheDir(context), "${System.currentTimeMillis()}_$safeName")
            val stream = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            }
            if (stream != null) {
                stream.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                }
                if (dest.exists() && dest.length() > 0L) {
                    try {
                        dest.copyTo(persistentFallback, overwrite = true)
                    } catch (_: Exception) {
                    }
                    return@withContext dest
                }
            }
            if (persistentFallback.exists() && persistentFallback.length() > 0L) {
                persistentFallback.copyTo(dest, overwrite = true)
                return@withContext dest
            }
            null
        } catch (_: Exception) {
            if (persistentFallback.exists() && persistentFallback.length() > 0L) persistentFallback else null
        }
    }

    suspend fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 16 * 1024)
                    }
                } ?: return@withContext false
                true
            } catch (_: Exception) {
                false
            }
        }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareMultipleFiles(context: Context, files: List<File>, mimeType: String, chooserTitle: String) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            shareFile(context, files.first(), mimeType, chooserTitle)
            return
        }
        val uris = ArrayList<Uri>(files.size)
        files.forEach { f ->
            uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    suspend fun isPdfEncrypted(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        ensurePdfBoxInit(context)
        try {
            PDDocument.load(file).use { doc ->
                doc.isEncrypted
            }
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يحضّر نسخة صالحة للعرض عبر PdfRenderer الأصلي. إذا كان الملف محمياً بكلمة مرور
     * يتم فك تشفيره مؤقتاً في ذاكرة التطبيق المؤقتة بعد التحقق من كلمة المرور.
     */
    suspend fun prepareRenderableFile(
        context: Context,
        sourceFile: File,
        password: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        ensurePdfBoxInit(context)
        try {
            if (password.isNullOrEmpty()) {
                val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (sec: SecurityException) {
                    pfd.close()
                    return@withContext Result.failure(sec)
                } catch (e: Exception) {
                    pfd.close()
                    throw e
                }
                renderer.close()
                pfd.close()
                return@withContext Result.success(sourceFile)
            } else {
                PDDocument.load(sourceFile, password).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    val unlocked = File(
                        getStudioCacheDir(context),
                        "unlocked_${sourceFile.nameWithoutExtension}_${System.currentTimeMillis()}.pdf"
                    )
                    doc.save(unlocked)
                    return@withContext Result.success(unlocked)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Exception) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun renderPageToBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int,
        rotationDegrees: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                val rawBitmap: Bitmap
                val page = renderer.openPage(pageIndex)
                try {
                    val safeWidth = targetWidthPx.coerceIn(120, 3200)
                    val aspect = page.height.toFloat() / max(1, page.width).toFloat()
                    val targetHeight = max(1, (safeWidth * aspect).roundToInt()).coerceAtMost(4200)
                    rawBitmap = Bitmap.createBitmap(safeWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    rawBitmap.eraseColor(AndroidColor.WHITE)
                    page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }

                val normRot = ((rotationDegrees % 360) + 360) % 360
                if (normRot == 0) {
                    rawBitmap
                } else {
                    val matrix = Matrix().apply { postRotate(normRot.toFloat()) }
                    val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    if (rotated !== rawBitmap) rawBitmap.recycle()
                    rotated
                }
            } catch (_: OutOfMemoryError) {
                System.gc()
                null
            } catch (_: Exception) {
                null
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }
        }
    }

    suspend fun loadPdfOutline(
        context: Context,
        file: File,
        password: String?
    ): List<PdfOutlineNode> = withContext(Dispatchers.IO) {
        ensurePdfBoxInit(context)
        val result = mutableListOf<PdfOutlineNode>()
        try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pdDoc ->
                val outline = pdDoc.documentCatalog.documentOutline ?: return@withContext emptyList()
                val pagesTree = pdDoc.pages
                fun traverse(item: PDOutlineItem?, level: Int) {
                    var current = item
                    while (current != null && result.size < 250) {
                        val title = current.title?.trim().orEmpty()
                        var pageIdx = -1
                        try {
                            val dest = current.findDestinationPage(pdDoc)
                            if (dest != null) {
                                pageIdx = pagesTree.indexOf(dest)
                            } else if (current.destination is PDPageDestination) {
                                val pdDest = current.destination as PDPageDestination
                                pageIdx = pdDest.retrievePageNumber()
                            }
                        } catch (_: Exception) {
                        }
                        if (title.isNotEmpty() && pageIdx >= 0) {
                            result.add(PdfOutlineNode(title = title, pageIndex = pageIdx, level = level.coerceAtMost(3)))
                        }
                        traverse(current.firstChild, level + 1)
                        current = current.nextSibling
                    }
                }
                traverse(outline.firstChild, 0)
            }
        } catch (_: Exception) {
        }
        result
    }

    suspend fun searchTextInPdf(
        context: Context,
        file: File,
        query: String,
        password: String?
    ): List<PdfSearchMatch> = withContext(Dispatchers.IO) {
        ensurePdfBoxInit(context)
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()
        val matches = mutableListOf<PdfSearchMatch>()
        try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pdDoc ->
                val total = pdDoc.numberOfPages
                val stripper = PDFTextStripper()
                for (p in 1..total) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(pdDoc) ?: continue
                    val idx = pageText.indexOf(cleanQuery, ignoreCase = true)
                    if (idx >= 0) {
                        var count = 0
                        var scan = 0
                        while (true) {
                            val found = pageText.indexOf(cleanQuery, scan, ignoreCase = true)
                            if (found < 0) break
                            count++
                            scan = found + cleanQuery.length
                        }
                        val start = (idx - 35).coerceAtLeast(0)
                        val end = (idx + cleanQuery.length + 55).coerceAtMost(pageText.length)
                        val snippet = pageText.substring(start, end)
                            .replace('\n', ' ')
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        matches.add(
                            PdfSearchMatch(
                                pageIndex = p - 1,
                                snippet = "...$snippet...",
                                matchCountOnPage = count
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        matches
    }

    suspend fun extractTextFromPdf(
        context: Context,
        file: File,
        password: String?,
        startPage1Based: Int? = null,
        endPage1Based: Int? = null
    ): String = withContext(Dispatchers.IO) {
        ensurePdfBoxInit(context)
        try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pdDoc ->
                val total = pdDoc.numberOfPages
                val stripper = PDFTextStripper()
                if (startPage1Based != null && endPage1Based != null) {
                    stripper.startPage = startPage1Based.coerceIn(1, total)
                    stripper.endPage = endPage1Based.coerceIn(stripper.startPage, total)
                }
                stripper.getText(pdDoc).trim()
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun applyBwPrintFilter(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val bwMatrix = ColorMatrix().apply { setSaturation(0f) }
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                1.35f, 0f, 0f, 0f, -25f,
                0f, 1.35f, 0f, 0f, -25f,
                0f, 0f, 1.35f, 0f, -25f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        bwMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(bwMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun createArabicTextOverlayBitmap(
        widthPx: Int,
        heightPx: Int,
        text: String,
        textColor: Int,
        typeface: Typeface,
        textSizePx: Float,
        alpha: Int = 255,
        rotationDegrees: Float = 0f,
        tileGrid: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.alpha = alpha.coerceIn(10, 255)
            textSize = textSizePx
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }

        if (tileGrid) {
            val stepX = (widthPx / 2.2f).coerceAtLeast(260f)
            val stepY = (heightPx / 3.2f).coerceAtLeast(240f)
            var y = stepY * 0.5f
            while (y < heightPx) {
                var x = stepX * 0.5f
                while (x < widthPx) {
                    canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(rotationDegrees)
                    canvas.drawText(text, 0f, 0f, paint)
                    canvas.restore()
                    x += stepX
                }
                y += stepY
            }
        } else {
            canvas.save()
            canvas.translate(widthPx / 2f, heightPx / 2f)
            canvas.rotate(rotationDegrees)
            val layoutWidth = (widthPx * 0.8f).toInt().coerceAtLeast(120)
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.translate(-layoutWidth / 2f, -staticLayout.height / 2f)
            staticLayout.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    fun createBlankOrRuledPageBitmap(widthPx: Int = 1240, heightPx: Int = 1754, ruled: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AndroidColor.WHITE)
        if (ruled) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(90, 59, 130, 246)
                strokeWidth = 2f
            }
            val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(120, 239, 68, 68)
                strokeWidth = 3f
            }
            // هامش عمودي للكتابة العربية من اليمين
            val rightMarginX = widthPx - 140f
            canvas.drawLine(rightMarginX, 90f, rightMarginX, heightPx - 90f, marginPaint)
            var y = 180f
            while (y < heightPx - 100f) {
                canvas.drawLine(80f, y, widthPx - 80f, y, linePaint)
                y += 58f
            }
        }
        return bmp
    }
}

class PdfRenderCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(8 * 1024, 64 * 1024)

    private val lruCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun buildKey(filePath: String, pageIndex: Int, widthPx: Int, rotation: Int): String =
        "$filePath#$pageIndex#$widthPx#$rotation"

    fun get(key: String): Bitmap? {
        val bmp = lruCache.get(key) ?: return null
        return if (bmp.isRecycled) {
            lruCache.remove(key)
            null
        } else {
            bmp
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            lruCache.put(key, bitmap)
        }
    }

    fun clear() {
        lruCache.evictAll()
    }
}

class PdfStudioStorage(context: Context) {
    private val prefs = context.getSharedPreferences("pdf_studio_storage_v1", Context.MODE_PRIVATE)

    fun getRecentPdfs(): List<RecentPdfRecord> {
        val raw = prefs.getString("recent_pdfs", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val list = mutableListOf<RecentPdfRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                RecentPdfRecord(
                    uriString = obj.optString("uriString"),
                    title = obj.optString("title"),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                    pageCount = obj.optInt("pageCount", 1),
                    lastPageIndex = obj.optInt("lastPageIndex", 0),
                    lastOpenedMillis = obj.optLong("lastOpenedMillis", System.currentTimeMillis())
                )
            )
        }
        return list.sortedByDescending { it.lastOpenedMillis }
    }

    fun recordRecentPdf(record: RecentPdfRecord) {
        val existing = getRecentPdfs().filterNot { it.uriString == record.uriString }.toMutableList()
        existing.add(0, record)
        val trimmed = existing.take(15)
        val arr = JSONArray()
        trimmed.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("uriString", item.uriString)
                    put("title", item.title)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("pageCount", item.pageCount)
                    put("lastPageIndex", item.lastPageIndex)
                    put("lastOpenedMillis", item.lastOpenedMillis)
                }
            )
        }
        prefs.edit().putString("recent_pdfs", arr.toString()).apply()
    }

    fun updateLastPage(uriString: String, pageIndex: Int, pageCount: Int) {
        val list = getRecentPdfs().toMutableList()
        val idx = list.indexOfFirst { it.uriString == uriString }
        if (idx >= 0) {
            val updated = list[idx].copy(
                lastPageIndex = pageIndex,
                pageCount = max(1, pageCount),
                lastOpenedMillis = System.currentTimeMillis()
            )
            list[idx] = updated
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("uriString", item.uriString)
                        put("title", item.title)
                        put("fileSizeBytes", item.fileSizeBytes)
                        put("pageCount", item.pageCount)
                        put("lastPageIndex", item.lastPageIndex)
                        put("lastOpenedMillis", item.lastOpenedMillis)
                    }
                )
            }
            prefs.edit().putString("recent_pdfs", arr.toString()).apply()
        }
    }

    fun getLastPage(uriString: String): Int {
        return getRecentPdfs().firstOrNull { it.uriString == uriString }?.lastPageIndex ?: 0
    }

    fun removeRecentPdf(uriString: String) {
        val list = getRecentPdfs().filterNot { it.uriString == uriString }
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("uriString", item.uriString)
                    put("title", item.title)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("pageCount", item.pageCount)
                    put("lastPageIndex", item.lastPageIndex)
                    put("lastOpenedMillis", item.lastOpenedMillis)
                }
            )
        }
        prefs.edit().putString("recent_pdfs", arr.toString()).apply()
    }

    fun getBookmarks(uriString: String): List<PdfBookmarkItem> {
        val key = "bm_${uriString.hashCode()}"
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val list = mutableListOf<PdfBookmarkItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                PdfBookmarkItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    pageIndex = obj.optInt("pageIndex", 0),
                    label = obj.optString("label", "علامة مرجعية"),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                )
            )
        }
        return list.sortedBy { it.pageIndex }
    }

    fun toggleBookmark(uriString: String, pageIndex: Int, label: String): List<PdfBookmarkItem> {
        val key = "bm_${uriString.hashCode()}"
        val current = getBookmarks(uriString).toMutableList()
        val existingIdx = current.indexOfFirst { it.pageIndex == pageIndex }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(
                PdfBookmarkItem(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIndex,
                    label = label.ifBlank { "صفحة ${pageIndex + 1}" },
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
        val arr = JSONArray()
        current.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("id", bm.id)
                    put("pageIndex", bm.pageIndex)
                    put("label", bm.label)
                    put("createdAtMillis", bm.createdAtMillis)
                }
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
        return current.sortedBy { it.pageIndex }
    }
}
