package com.example.pdfstudio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * مدير حزم اللغات لـ Tesseract4Android:
 * يحفظ ملفات .traineddata محلياً داخل filesDir/tessdata ويعرض التقدم الفعلي بالبايتات.
 */
object TessdataManager {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"

    fun getTessdataDir(context: Context): File {
        val dir = File(context.filesDir, "tessdata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isLanguageDownloaded(context: Context, langCode: String): Boolean {
        val f = File(getTessdataDir(context), "$langCode.traineddata")
        return f.exists() && f.length() > 100_000L
    }

    suspend fun downloadLanguage(
        context: Context,
        langCode: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destDir = getTessdataDir(context)
            val destFile = File(destDir, "$langCode.traineddata")
            val tempFile = File(destDir, "$langCode.traineddata.part")

            val req = Request.Builder().url("$BASE_URL$langCode.traineddata").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(IllegalStateException("استجابة فارغة"))
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val r = input.read(buffer)
                            if (r == -1) break
                            output.write(buffer, 0, r)
                            readTotal += r
                            if (body.contentLength() > 0L) {
                                onProgress((readTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            onProgress(1f)
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object PdfConvertOcrEngine {
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        jpegQuality: Float,
        outputName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${outputName.ifBlank { "images_pdf" }}_${System.currentTimeMillis()}.pdf"
            )
            PDDocument().use { doc ->
                for (uri in imageUris) {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: continue
                    val maxDim = 2000f
                    val scale = if (max(bmp.width, bmp.height) > maxDim) {
                        maxDim / max(bmp.width, bmp.height).toFloat()
                    } else 1f
                    val scaled = if (scale < 0.99f) {
                        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
                    } else bmp

                    val pageW = 595f
                    val pageH = (pageW * (scaled.height.toFloat() / max(1, scaled.width).toFloat())).coerceIn(220f, 1200f)
                    val page = PDPage(PDRectangle(pageW, pageH))
                    doc.addPage(page)

                    val pdImg = JPEGFactory.createFromImage(doc, scaled, jpegQuality.coerceIn(0.45f, 0.95f))
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImg, 0f, 0f, pageW, pageH)
                    }
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()
                }
                doc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pdfToImages(
        context: Context,
        sourceFile: File,
        format: PdfExportFormat,
        startPage1Based: Int,
        endPage1Based: Int,
        onProgress: (Float) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val count = PdfEngineHelper.getPdfPageCount(sourceFile)
            if (count <= 0) return@withContext Result.failure(IllegalStateException("ملف PDF غير مقروء أو محمي"))
            val s = startPage1Based.coerceIn(1, count)
            val e = endPage1Based.coerceIn(s, count)
            val outFiles = mutableListOf<File>()
            val total = e - s + 1

            for (p in s..e) {
                val rawBmp = PdfEngineHelper.renderPageToBitmap(sourceFile, p - 1, 1600, 0) ?: continue
                val finalBmp = if (format == PdfExportFormat.BW_PRINT) {
                    val bw = PdfEngineHelper.applyBwPrintFilter(rawBmp)
                    rawBmp.recycle()
                    bw
                } else rawBmp

                val outFile = File(
                    PdfEngineHelper.getStudioExportsDir(context),
                    "${sourceFile.nameWithoutExtension}_p${p}_${System.currentTimeMillis()}.${format.ext}"
                )
                FileOutputStream(outFile).use { fos ->
                    val compressFmt = if (format == PdfExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    finalBmp.compress(compressFmt, 90, fos)
                }
                finalBmp.recycle()
                outFiles.add(outFile)
                onProgress(((p - s + 1).toFloat() / total.toFloat()))
            }
            Result.success(outFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runOfflineTesseractOcr(
        context: Context,
        sourceFile: File,
        langCode: String,
        startPage1Based: Int,
        endPage1Based: Int,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val tess = TessBaseAPI()
        try {
            val dataParent = context.filesDir.absolutePath
            val initialized = tess.init(dataParent, langCode)
            if (!initialized) {
                return@withContext Result.failure(IllegalStateException("تعذر تهيئة محرك التعرف الضوئي للغة: $langCode"))
            }
            val totalDocPages = PdfEngineHelper.getPdfPageCount(sourceFile)
            val s = startPage1Based.coerceIn(1, max(1, totalDocPages))
            val e = endPage1Based.coerceIn(s, max(1, totalDocPages))
            val sb = StringBuilder()

            for (p in s..e) {
                val bmp = PdfEngineHelper.renderPageToBitmap(sourceFile, p - 1, 1650, 0)
                if (bmp != null) {
                    tess.setImage(bmp)
                    val text = tess.utF8Text?.trim().orEmpty()
                    bmp.recycle()
                    if (s != e) {
                        sb.append("─── صفحة ").append(p).append(" ───\n")
                    }
                    sb.append(text).append("\n\n")
                }
                onProgress(((p - s + 1).toFloat() / max(1, e - s + 1).toFloat()))
            }
            Result.success(sb.toString().trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                tess.recycle()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun applySecurityProtection(
        context: Context,
        sourceFile: File,
        userPass: String,
        ownerPass: String,
        perms: SecurityPermissionsConfig
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_protected_${System.currentTimeMillis()}.pdf"
            )
            PDDocument.load(sourceFile).use { doc ->
                val ap = AccessPermission().apply {
                    setCanPrint(perms.allowPrint)
                    setCanExtractContent(perms.allowCopy)
                    setCanModify(perms.allowModify)
                }
                val policy = StandardProtectionPolicy(ownerPass.ifBlank { userPass }, userPass, ap).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
                doc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSecurityProtection(
        context: Context,
        sourceFile: File,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_unlocked_${System.currentTimeMillis()}.pdf"
            )
            PDDocument.load(sourceFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyDiagonalArabicWatermark(
        context: Context,
        sourceFile: File,
        watermarkText: String,
        opacityFraction: Float
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_watermarked_${System.currentTimeMillis()}.pdf"
            )
            val typeface = PdfEngineHelper.getArabicUiTypeface(context)
            PDDocument.load(sourceFile).use { doc ->
                val bmp = PdfEngineHelper.createArabicTextOverlayBitmap(
                    widthPx = 1100,
                    heightPx = 1550,
                    text = watermarkText,
                    textColor = android.graphics.Color.parseColor("#334155"),
                    typeface = typeface,
                    textSizePx = 46f,
                    alpha = (opacityFraction.coerceIn(0.08f, 0.7f) * 255).roundToInt(),
                    rotationDegrees = -35f,
                    tileGrid = true
                )
                val pdWatermark = LosslessFactory.createFromImage(doc, bmp)
                bmp.recycle()
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.drawImage(pdWatermark, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
                    }
                }
                doc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readMetadata(context: Context, sourceFile: File): PdfMetadataInfo = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            PDDocument.load(sourceFile).use { doc ->
                val info = doc.documentInformation
                PdfMetadataInfo(
                    title = info.title.orEmpty(),
                    author = info.author.orEmpty(),
                    subject = info.subject.orEmpty(),
                    keywords = info.keywords.orEmpty(),
                    creator = info.creator.orEmpty()
                )
            }
        } catch (_: Exception) {
            PdfMetadataInfo()
        }
    }

    suspend fun saveOrWipeMetadata(
        context: Context,
        sourceFile: File,
        wipeAll: Boolean,
        info: PdfMetadataInfo
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_${if (wipeAll) "clean_meta" else "meta"}_${System.currentTimeMillis()}.pdf"
            )
            PDDocument.load(sourceFile).use { doc ->
                if (wipeAll) {
                    doc.documentInformation = PDDocumentInformation()
                    doc.documentCatalog.metadata = null
                } else {
                    val pdInfo = doc.documentInformation
                    pdInfo.title = info.title.ifBlank { null }
                    pdInfo.author = info.author.ifBlank { null }
                    pdInfo.subject = info.subject.ifBlank { null }
                    pdInfo.keywords = info.keywords.ifBlank { null }
                    pdInfo.creator = info.creator.ifBlank { null }
                    doc.documentInformation = pdInfo
                }
                doc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun PdfConvertOcrScreen(
    initialTab: ConvertSecurityTab = ConvertSecurityTab.OCR_TEXT,
    onOpenInReader: (String, String) -> Unit,
    onSaveExtractedNote: (title: String, text: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var activeTab by remember { mutableStateOf(initialTab) }

    Scaffold(
        topBar = {
            Column {
                PdfStudioTopBar(
                    title = "التحويل و OCR والأمان",
                    subtitle = "تحويل الصور، استخراج النصوص العربية بدون إنترنت، التشفير والبيانات",
                    onBack = onBack,
                    testTagBack = "convert_ocr_back_btn"
                )
                ScrollableTabRow(selectedTabIndex = activeTab.ordinal, edgePadding = 8.dp) {
                    ConvertSecurityTab.entries.forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            modifier = Modifier.testTag("conv_tab_${tab.name}"),
                            text = { Text(tab.titleAr, style = PdfTypography.uiLabel(13.sp)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (activeTab) {
                ConvertSecurityTab.IMAGES_TO_PDF -> ImagesToPdfPanel(onOpenInReader = onOpenInReader)
                ConvertSecurityTab.PDF_TO_IMAGES -> PdfToImagesPanel()
                ConvertSecurityTab.OCR_TEXT -> OcrAndTextExtractionPanel(onSaveExtractedNote = onSaveExtractedNote)
                ConvertSecurityTab.SECURITY_META -> SecurityWatermarkMetadataPanel(onOpenInReader = onOpenInReader)
            }
        }
    }
}

@Composable
private fun ImagesToPdfPanel(onOpenInReader: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var quality by remember { mutableFloatStateOf(0.85f) }
    var docName by remember { mutableStateOf("صور_المذاكرة") }
    var isBuilding by remember { mutableStateOf(false) }
    var createdPdf by remember { mutableStateOf<File?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = selectedUris + uris
            createdPdf = null
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { targetUri ->
        val f = createdPdf
        if (targetUri != null && f != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, f, targetUri)
                Toast.makeText(context, if (ok) "تم حفظ الـ PDF ✅" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("img2pdf_pick_btn")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("اختيار صور من المعرض (Photo Picker)", style = PdfTypography.uiTitle(14.sp))
        }

        OutlinedTextField(
            value = docName,
            onValueChange = { docName = it },
            label = { Text("اسم ملف PDF الناتج", style = PdfTypography.uiLabel()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("جودة الصور: ${(quality * 100).roundToInt()}%", style = PdfTypography.uiLabel(12.sp))
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 0.5f..0.95f,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
        }

        if (selectedUris.isNotEmpty()) {
            Text("الصور المختارة (${selectedUris.size}) — رتبها قبل الإنشاء:", style = PdfTypography.uiTitle(14.sp))
            selectedUris.forEachIndexed { idx, uri ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}. ${PdfEngineHelper.queryDisplayName(context, uri)}", style = PdfTypography.uiBody(13.sp), modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val mut = selectedUris.toMutableList()
                                    val item = mut.removeAt(idx)
                                    mut.add(idx - 1, item)
                                    selectedUris = mut
                                }
                            },
                            enabled = idx > 0
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى") }
                        IconButton(
                            onClick = {
                                if (idx < selectedUris.lastIndex) {
                                    val mut = selectedUris.toMutableList()
                                    val item = mut.removeAt(idx)
                                    mut.add(idx + 1, item)
                                    selectedUris = mut
                                }
                            },
                            enabled = idx < selectedUris.lastIndex
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل") }
                        IconButton(onClick = { selectedUris = selectedUris.filterIndexed { i, _ -> i != idx } }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                isBuilding = true
                scope.launch {
                    val res = PdfConvertOcrEngine.imagesToPdf(context, selectedUris, quality, docName)
                    isBuilding = false
                    if (res.isSuccess) {
                        createdPdf = res.getOrThrow()
                        Toast.makeText(context, "تم تحويل ${selectedUris.size} صورة إلى PDF ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خطأ: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = selectedUris.isNotEmpty() && !isBuilding,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("img2pdf_convert_btn")
        ) {
            if (isBuilding) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تحويل وبناء PDF الآن", style = PdfTypography.uiTitle(14.sp))
        }

        createdPdf?.let { out ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenInReader(Uri.fromFile(out).toString(), out.name) },
                    modifier = Modifier.weight(1f)
                ) { Text("فتح", style = PdfTypography.uiLabel()) }
                OutlinedButton(
                    onClick = { saveLauncher.launch("${docName.ifBlank { "images" }}.pdf") },
                    modifier = Modifier.weight(1f)
                ) { Text("حفظ", style = PdfTypography.uiLabel()) }
                OutlinedButton(
                    onClick = { PdfEngineHelper.shareFile(context, out, "application/pdf", "مشاركة PDF") },
                    modifier = Modifier.weight(1f)
                ) { Text("مشاركة", style = PdfTypography.uiLabel()) }
            }
        }
    }
}

@Composable
private fun PdfToImagesPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var exportFormat by remember { mutableStateOf(PdfExportFormat.JPG) }
    var startPageText by remember { mutableStateOf("1") }
    var endPageText by remember { mutableStateOf("1") }
    var isExporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var exportedImages by remember { mutableStateOf<List<File>>(emptyList()) }

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = PdfEngineHelper.queryDisplayName(context, uri)
                val f = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), name)
                if (f != null) {
                    val count = PdfEngineHelper.getPdfPageCount(f)
                    if (count > 0) {
                        sourceFile = f
                        fileName = name
                        pageCount = count
                        startPageText = "1"
                        endPageText = count.coerceAtMost(10).coerceAtLeast(1).toString()
                        exportedImages = emptyList()
                    } else {
                        Toast.makeText(context, "الملف قد يكون محمياً بكلمة مرور أو تالفاً", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "تعذر الوصول للملف المختار", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StackedDepthCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (sourceFile == null) "اختر ملف PDF لتحويله لصور" else fileName, style = PdfTypography.uiTitle(15.sp))
                    Text(if (sourceFile == null) "PNG أو JPG أو أبيض وأسود للطباعة الاقتصادية" else "$pageCount صفحة", style = PdfTypography.uiBody(12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                    Text(if (sourceFile == null) "اختيار" else "تغيير", style = PdfTypography.uiLabel())
                }
            }
        }

        if (sourceFile != null && pageCount > 0) {
            Text("صيغة التصدير", style = PdfTypography.uiTitle(14.sp))
            PdfExportFormat.entries.forEach { fmt ->
                Surface(
                    onClick = { exportFormat = fmt },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (exportFormat == fmt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportFormat == fmt, onClick = { exportFormat = fmt })
                        Text(fmt.titleAr, style = PdfTypography.uiBody(13.sp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startPageText,
                    onValueChange = { if (it.all(Char::isDigit)) startPageText = it },
                    label = { Text("من صفحة", style = PdfTypography.uiLabel()) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endPageText,
                    onValueChange = { if (it.all(Char::isDigit)) endPageText = it },
                    label = { Text("إلى صفحة ($pageCount)", style = PdfTypography.uiLabel()) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val sf = sourceFile ?: return@Button
                    val s = startPageText.toIntOrNull() ?: 1
                    val e = endPageText.toIntOrNull() ?: pageCount
                    isExporting = true
                    progress = 0f
                    scope.launch {
                        val res = PdfConvertOcrEngine.pdfToImages(context, sf, exportFormat, s, e) { progress = it }
                        isExporting = false
                        if (res.isSuccess) {
                            exportedImages = res.getOrThrow()
                            Toast.makeText(context, "تم استخراج ${exportedImages.size} صورة ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "خطأ: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isExporting) "جارٍ التحويل..." else "استخراج الصور الآن", style = PdfTypography.uiTitle(14.sp))
            }

            if (isExporting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            if (exportedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الصور الناتجة (${exportedImages.size})", style = PdfTypography.uiTitle(14.sp))
                    OutlinedButton(
                        onClick = {
                            val mime = if (exportFormat == PdfExportFormat.PNG) "image/png" else "image/jpeg"
                            PdfEngineHelper.shareMultipleFiles(context, exportedImages, mime, "مشاركة الصور")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة الكل", style = PdfTypography.uiLabel())
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(exportedImages) { imgFile ->
                        val bmp = remember(imgFile.absolutePath) { BitmapFactory.decodeFile(imgFile.absolutePath) }
                        Surface(
                            onClick = {
                                val mime = if (exportFormat == PdfExportFormat.PNG) "image/png" else "image/jpeg"
                                PdfEngineHelper.shareFile(context, imgFile, mime, "مشاركة الصورة")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = imgFile.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(110.dp, 150.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrAndTextExtractionPanel(onSaveExtractedNote: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }

    var useOcrMode by remember { mutableStateOf(false) }
    var araReady by remember { mutableStateOf(TessdataManager.isLanguageDownloaded(context, "ara")) }
    var engReady by remember { mutableStateOf(TessdataManager.isLanguageDownloaded(context, "eng")) }
    var downloadingLang by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    var selectedOcrLang by remember { mutableStateOf("ara+eng") }
    var pageStartText by remember { mutableStateOf("1") }
    var pageEndText by remember { mutableStateOf("1") }
    var isProcessing by remember { mutableStateOf(false) }
    var ocrProgress by remember { mutableFloatStateOf(0f) }
    var extractedText by remember { mutableStateOf("") }
    var readingFontSp by remember { mutableFloatStateOf(18f) }

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = PdfEngineHelper.queryDisplayName(context, uri)
                val f = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), name)
                if (f != null) {
                    val count = PdfEngineHelper.getPdfPageCount(f)
                    if (count > 0) {
                        sourceFile = f
                        fileName = name
                        pageCount = count
                        pageStartText = "1"
                        pageEndText = count.coerceAtMost(3).coerceAtLeast(1).toString()
                        extractedText = ""
                    } else {
                        Toast.makeText(context, "الملف قد يكون محمياً بكلمة مرور أو تالفاً", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "تعذر الوصول للملف المختار", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StackedDepthCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (sourceFile == null) "اختر ملف PDF لاستخراج نصه" else fileName, style = PdfTypography.uiTitle(15.sp))
                    Text(
                        if (sourceFile == null) "استخراج مباشر أو OCR أوفلاين للكتب المصورة بالعربية" else "$pageCount صفحة جاهزة",
                        style = PdfTypography.uiBody(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.testTag("ocr_pick_pdf_btn")
                ) {
                    Text(if (sourceFile == null) "اختيار" else "تغيير", style = PdfTypography.uiLabel())
                }
            }
        }

        // تبديل الوضع: استخراج مباشر vs OCR أوفلاين
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { useOcrMode = false },
                shape = RoundedCornerShape(14.dp),
                color = if (!useOcrMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (!useOcrMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("استخراج نصي مباشر", style = PdfTypography.uiTitle(13.sp))
                    Text("فوري للملازم النصية", style = PdfTypography.uiLabel(11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                onClick = { useOcrMode = true },
                shape = RoundedCornerShape(14.dp),
                color = if (useOcrMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (useOcrMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).testTag("ocr_mode_tesseract_btn")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("OCR أوفلاين (Tesseract)", style = PdfTypography.uiTitle(13.sp))
                    Text("للصفحات المصورة عربي/إنجليزي", style = PdfTypography.uiLabel(11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (useOcrMode) {
            // إدارة حزم اللغات ara & eng بدون إنترنت بعد التنزيل
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حزم التعرف الضوئي المحلية (Tesseract Tessdata)", style = PdfTypography.uiTitle(13.sp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguagePackTile(
                            title = "العربية (ara)",
                            isReady = araReady,
                            isDownloading = downloadingLang == "ara",
                            onDownload = {
                                downloadingLang = "ara"
                                downloadProgress = 0f
                                scope.launch {
                                    val r = TessdataManager.downloadLanguage(context, "ara") { downloadProgress = it }
                                    downloadingLang = null
                                    araReady = TessdataManager.isLanguageDownloaded(context, "ara")
                                    Toast.makeText(
                                        context,
                                        if (r.isSuccess) "تم تحميل حزمة العربية أوفلاين ✅" else "فشل التحميل: ${r.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        LanguagePackTile(
                            title = "الإنجليزية (eng)",
                            isReady = engReady,
                            isDownloading = downloadingLang == "eng",
                            onDownload = {
                                downloadingLang = "eng"
                                downloadProgress = 0f
                                scope.launch {
                                    val r = TessdataManager.downloadLanguage(context, "eng") { downloadProgress = it }
                                    downloadingLang = null
                                    engReady = TessdataManager.isLanguageDownloaded(context, "eng")
                                    Toast.makeText(
                                        context,
                                        if (r.isSuccess) "تم تحميل حزمة الإنجليزية أوفلاين ✅" else "فشل التحميل: ${r.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (downloadingLang != null) {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text("جارٍ التحميل: ${(downloadProgress * 100).roundToInt()}%", style = PdfTypography.uiLabel(11.sp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ara" to "عربي فقط", "eng" to "إنجليزي فقط", "ara+eng" to "عربي + إنجليزي").forEach { (code, label) ->
                            Surface(
                                onClick = { selectedOcrLang = code },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedOcrLang == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = label,
                                    color = if (selectedOcrLang == code) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = PdfTypography.uiLabel(11.sp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sourceFile != null && pageCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pageStartText,
                    onValueChange = { if (it.all(Char::isDigit)) pageStartText = it },
                    label = { Text("من صفحة", style = PdfTypography.uiLabel()) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pageEndText,
                    onValueChange = { if (it.all(Char::isDigit)) pageEndText = it },
                    label = { Text("إلى صفحة ($pageCount)", style = PdfTypography.uiLabel()) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val sf = sourceFile ?: return@Button
                    val s = pageStartText.toIntOrNull() ?: 1
                    val e = pageEndText.toIntOrNull() ?: pageCount
                    if (useOcrMode) {
                        val needAra = selectedOcrLang.contains("ara") && !araReady
                        val needEng = selectedOcrLang.contains("eng") && !engReady
                        if (needAra || needEng) {
                            Toast.makeText(context, "يرجى تحميل حزمة اللغة المطلوبة أعلاه أولاً", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                    }
                    isProcessing = true
                    ocrProgress = 0f
                    scope.launch {
                        if (useOcrMode) {
                            val res = PdfConvertOcrEngine.runOfflineTesseractOcr(context, sf, selectedOcrLang, s, e) {
                                ocrProgress = it
                            }
                            isProcessing = false
                            if (res.isSuccess) {
                                extractedText = res.getOrThrow().ifBlank { "لم يتم التعرف على نصوص واضحة في النطاق المحدد." }
                            } else {
                                Toast.makeText(context, "فشل OCR: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val txt = PdfEngineHelper.extractTextFromPdf(context, sf, null, s, e)
                            isProcessing = false
                            extractedText = txt.ifBlank { "هذا الملف مصوّر ولا يحتوي على طبقة نصية؛ جرّب وضع OCR أوفلاين أعلاه." }
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ocr_run_btn")
            ) {
                Icon(Icons.Default.TextFields, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isProcessing) "جارٍ الاستخراج..." else "استخراج النص الآن", style = PdfTypography.uiTitle(14.sp))
            }

            if (isProcessing) LinearProgressIndicator(progress = { ocrProgress }, modifier = Modifier.fillMaxWidth())
        }

        // عرض النص المستخرج بخط Noto Naskh Arabic وبسلايدر 16..28 sp
        if (extractedText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("النص المستخرج (${readingFontSp.roundToInt()} sp)", style = PdfTypography.uiTitle(14.sp))
                        Slider(
                            value = readingFontSp,
                            onValueChange = { readingFontSp = it },
                            valueRange = 16f..28f,
                            modifier = Modifier.width(150.dp)
                        )
                    }

                    SelectionContainer {
                        Text(
                            text = extractedText,
                            style = PdfTypography.readingBody(readingFontSp.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("pdf_ocr_text", extractedText))
                                Toast.makeText(context, "تم نسخ النص ✅", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ", style = PdfTypography.uiLabel())
                        }
                        Button(
                            onClick = {
                                onSaveExtractedNote(
                                    "مستخرج: ${fileName.removeSuffix(".pdf")}",
                                    extractedText
                                )
                                Toast.makeText(context, "تم الحفظ في المستندات ✅", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ كمذكرة", style = PdfTypography.uiLabel())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePackTile(
    title: String,
    isReady: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isReady) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = PdfTypography.uiLabel(12.sp))
                Text(
                    if (isReady) "جاهزة أوفلاين" else "غير محمّلة",
                    style = PdfTypography.uiLabel(10.sp),
                    color = if (isReady) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isReady) {
                IconButton(onClick = onDownload, enabled = !isDownloading, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "تحميل")
                }
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = "جاهز", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SecurityWatermarkMetadataPanel(onOpenInReader: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }

    // تشفير / فك تشفير
    var userPass by remember { mutableStateOf("") }
    var ownerPass by remember { mutableStateOf("") }
    var allowPrint by remember { mutableStateOf(true) }
    var allowCopy by remember { mutableStateOf(true) }
    var allowModify by remember { mutableStateOf(false) }
    var unlockPassword by remember { mutableStateOf("") }

    // علامة مائية
    var watermarkText by remember { mutableStateOf("ملزمة دراسية - نسخة خاصة") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.24f) }

    // البيانات الوصفية
    var metaInfo by remember { mutableStateOf(PdfMetadataInfo()) }

    var isBusy by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = PdfEngineHelper.queryDisplayName(context, uri)
                val f = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), name)
                if (f != null) {
                    sourceFile = f
                    fileName = name
                    resultFile = null
                    metaInfo = PdfConvertOcrEngine.readMetadata(context, f)
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { targetUri ->
        val rf = resultFile
        if (targetUri != null && rf != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, rf, targetUri)
                Toast.makeText(context, if (ok) "تم حفظ الملف النهائي ✅" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StackedDepthCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (sourceFile == null) "اختر ملف PDF لتأمينه أو تعديله" else fileName, style = PdfTypography.uiTitle(15.sp))
                    Text("التشفير، العلامة المائية العربية، وتنظيف البيانات الوصفية", style = PdfTypography.uiBody(12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                    Text(if (sourceFile == null) "اختيار" else "تغيير", style = PdfTypography.uiLabel())
                }
            }
        }

        if (sourceFile != null) {
            // 1) التشفير وفك التشفير
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("التشفير وحماية الصلاحيات", style = PdfTypography.uiTitle(14.sp))
                    }
                    OutlinedTextField(
                        value = userPass,
                        onValueChange = { userPass = it },
                        label = { Text("كلمة مرور فتح الملف (User)", style = PdfTypography.uiLabel()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerPass,
                        onValueChange = { ownerPass = it },
                        label = { Text("كلمة مرور المالك (اختياري)", style = PdfTypography.uiLabel()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { allowPrint = !allowPrint }) {
                            Checkbox(checked = allowPrint, onCheckedChange = { allowPrint = it })
                            Text("طباعة", style = PdfTypography.uiLabel(12.sp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { allowCopy = !allowCopy }) {
                            Checkbox(checked = allowCopy, onCheckedChange = { allowCopy = it })
                            Text("نسخ", style = PdfTypography.uiLabel(12.sp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { allowModify = !allowModify }) {
                            Checkbox(checked = allowModify, onCheckedChange = { allowModify = it })
                            Text("تعديل", style = PdfTypography.uiLabel(12.sp))
                        }
                    }
                    Button(
                        onClick = {
                            val sf = sourceFile ?: return@Button
                            isBusy = true
                            scope.launch {
                                val r = PdfConvertOcrEngine.applySecurityProtection(
                                    context, sf, userPass, ownerPass,
                                    SecurityPermissionsConfig(allowPrint, allowCopy, allowModify)
                                )
                                isBusy = false
                                if (r.isSuccess) {
                                    resultFile = r.getOrThrow()
                                    Toast.makeText(context, "تم تشفير الملف بكلمة مرور ✅", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "فشل التشفير: ${r.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = userPass.isNotBlank() && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تطبيق التشفير وحفظ نسخة محمية", style = PdfTypography.uiLabel()) }

                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label = { Text("أو أدخل كلمة السر لفك التشفير نهائياً", style = PdfTypography.uiLabel()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val sf = sourceFile ?: return@OutlinedButton
                            isBusy = true
                            scope.launch {
                                val r = PdfConvertOcrEngine.removeSecurityProtection(context, sf, unlockPassword)
                                isBusy = false
                                if (r.isSuccess) {
                                    resultFile = r.getOrThrow()
                                    Toast.makeText(context, "تمت إزالة التشفير وحفظ نسخة مفتوحة ✅", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "كلمة مرور غير صحيحة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = unlockPassword.isNotBlank() && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فك تشفير الملف", style = PdfTypography.uiLabel())
                    }
                }
            }

            // 2) علامة مائية عربية مائلة
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("علامة مائية عربية مائلة", style = PdfTypography.uiTitle(14.sp))
                    }
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("نص العلامة المائية", style = PdfTypography.uiLabel()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الشفافية: ${(watermarkOpacity * 100).roundToInt()}%", style = PdfTypography.uiLabel(12.sp))
                        Slider(
                            value = watermarkOpacity,
                            onValueChange = { watermarkOpacity = it },
                            valueRange = 0.08f..0.60f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val sf = sourceFile ?: return@Button
                            isBusy = true
                            scope.launch {
                                val r = PdfConvertOcrEngine.applyDiagonalArabicWatermark(context, sf, watermarkText, watermarkOpacity)
                                isBusy = false
                                if (r.isSuccess) {
                                    resultFile = r.getOrThrow()
                                    Toast.makeText(context, "تمت إضافة العلامة المائية لجميع الصفحات ✅", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "خطأ: ${r.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = watermarkText.isNotBlank() && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ختم العلامة المائية على الصفحات", style = PdfTypography.uiLabel()) }
                }
            }

            // 3) البيانات الوصفية وحذفها لحماية الخصوصية
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("البيانات الوصفية والخصوصية (Metadata)", style = PdfTypography.uiTitle(14.sp))
                    }
                    OutlinedTextField(
                        value = metaInfo.title,
                        onValueChange = { metaInfo = metaInfo.copy(title = it) },
                        label = { Text("العنوان (Title)", style = PdfTypography.uiLabel()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = metaInfo.author,
                        onValueChange = { metaInfo = metaInfo.copy(author = it) },
                        label = { Text("المؤلف (Author)", style = PdfTypography.uiLabel()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = metaInfo.subject,
                        onValueChange = { metaInfo = metaInfo.copy(subject = it) },
                        label = { Text("الموضوع (Subject)", style = PdfTypography.uiLabel()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val sf = sourceFile ?: return@Button
                                isBusy = true
                                scope.launch {
                                    val r = PdfConvertOcrEngine.saveOrWipeMetadata(context, sf, wipeAll = false, info = metaInfo)
                                    isBusy = false
                                    if (r.isSuccess) {
                                        resultFile = r.getOrThrow()
                                        Toast.makeText(context, "تم تحديث البيانات الوصفية ✅", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("حفظ البيانات", style = PdfTypography.uiLabel()) }
                        OutlinedButton(
                            onClick = {
                                val sf = sourceFile ?: return@OutlinedButton
                                isBusy = true
                                scope.launch {
                                    val r = PdfConvertOcrEngine.saveOrWipeMetadata(context, sf, wipeAll = true, info = PdfMetadataInfo())
                                    isBusy = false
                                    if (r.isSuccess) {
                                        metaInfo = PdfMetadataInfo()
                                        resultFile = r.getOrThrow()
                                        Toast.makeText(context, "تم مسح جميع البيانات الوصفية لحماية الخصوصية ✅", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح الخصوصية", style = PdfTypography.uiLabel())
                        }
                    }
                }
            }

            resultFile?.let { out ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onOpenInReader(Uri.fromFile(out).toString(), out.name) },
                        modifier = Modifier.weight(1f)
                    ) { Text("فتح", style = PdfTypography.uiLabel()) }
                    OutlinedButton(
                        onClick = { saveLauncher.launch(out.name) },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ", style = PdfTypography.uiLabel()) }
                    OutlinedButton(
                        onClick = { PdfEngineHelper.shareFile(context, out, "application/pdf", "مشاركة PDF") },
                        modifier = Modifier.weight(1f)
                    ) { Text("مشاركة", style = PdfTypography.uiLabel()) }
                }
            }
        }
    }
}
