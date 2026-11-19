package com.example.studytools.files

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.MainViewModel
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class FileToolTab(val labelAr: String) {
    MERGE_PDF("دمج PDF"),
    EXTRACT_TEXT("استخراج النص"),
    IMAGES_TO_PDF("صور إلى PDF"),
    SPLIT_PDF("تقسيم الصفحات")
}

@Composable
fun StudyFileToolsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Throwable) {
            }
        }
    }

    var activeTab by remember { mutableStateOf(FileToolTab.MERGE_PDF) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var outputPdfFile by remember { mutableStateOf<File?>(null) }
    var extractedPdfText by remember { mutableStateOf("") }

    // Split states
    var splitSourceUri by remember { mutableStateOf<Uri?>(null) }
    var splitSourceName by remember { mutableStateOf("") }
    var splitPageCount by remember { mutableIntStateOf(0) }
    var splitStartPage by remember { mutableStateOf("1") }
    var splitEndPage by remember { mutableStateOf("1") }

    // Merge PDFs launcher
    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.size < 2) {
            if (uris.isNotEmpty()) {
                Toast.makeText(context, "اختر ملفين PDF على الأقل للدمج", Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            isBusy = true
            statusMessage = null
            outputPdfFile = null
            val merged = withContext(Dispatchers.IO) { mergePdfUris(context, uris) }
            isBusy = false
            if (merged != null) {
                outputPdfFile = merged
                statusMessage = "تم دمج ${uris.size} ملفات بنجاح في ${merged.name}"
            } else {
                statusMessage = "تعذر دمج الملفات. تأكد أن جميعها ملفات PDF صالحة وغير مشفرة."
            }
        }
    }

    // Extract Text PDF launcher
    val extractTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isBusy = true
            statusMessage = null
            extractedPdfText = ""
            val text = withContext(Dispatchers.IO) { extractTextFromPdfUri(context, uri) }
            isBusy = false
            if (text.isNotBlank()) {
                extractedPdfText = text
                statusMessage = "تم استخراج ${text.length} حرفاً من الملف بنجاح"
            } else {
                statusMessage = "لم يُعثر على نص رقمي داخل هذا الملف (جرب الماسح الضوئي إن كان مصوراً)."
            }
        }
    }

    // Images to PDF launcher (Zero-Permission Photo Picker)
    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isBusy = true
            statusMessage = null
            outputPdfFile = null
            val pdfFile = withContext(Dispatchers.IO) { convertImageUrisToPdf(context, uris) }
            isBusy = false
            if (pdfFile != null) {
                outputPdfFile = pdfFile
                statusMessage = "تم تحويل ${uris.size} صور إلى ملف PDF بنجاح"
            } else {
                statusMessage = "تعذر إنشاء ملف PDF من الصور المحددة."
            }
        }
    }

    // Split PDF picker
    val splitPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isBusy = true
            val count = withContext(Dispatchers.IO) { getPdfPageCount(context, uri) }
            isBusy = false
            if (count > 0) {
                splitSourceUri = uri
                splitSourceName = resolveDisplayName(context, uri) ?: "document.pdf"
                splitPageCount = count
                splitStartPage = "1"
                splitEndPage = "$count"
                statusMessage = "تم تحميل الملف ($count صفحة). حدد النطاق ثم اضغط استخراج."
            } else {
                statusMessage = "تعذر قراءة صفحات الملف."
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141E30),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("filetools_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "أَدَوَاتُ المَلَفَّاتِ والـ PDF",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF5B041)
                            )
                            Text(
                                text = "دمج • تقسيم • استخراج النصوص • تحويل الصور",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                // Tool mode grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileToolTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = {
                                activeTab = tab
                                statusMessage = null
                            },
                            label = {
                                Text(tab.labelAr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filetools_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Active Action Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (activeTab) {
                            FileToolTab.MERGE_PDF -> {
                                Text("دمج عدة ملفات PDF في ملزمة واحدة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "اختر ملفين PDF أو أكثر من جهازك ليتم دمجهما بالترتيب في ملف واحد جاهز للمذاكرة والمشاركة.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { mergePdfLauncher.launch(arrayOf("application/pdf")) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("filetools_merge_button")
                                ) {
                                    Icon(Icons.Default.LibraryAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("اختيار ملفات PDF للدمج", fontWeight = FontWeight.Bold)
                                }
                            }

                            FileToolTab.EXTRACT_TEXT -> {
                                Text("استخراج النصوص من ملفات PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "استخرج كل محتوى الكتاب أو المحاضرة كنص قابل للنسخ أو الحفظ مباشرة في ملاحظاتك.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { extractTextLauncher.launch(arrayOf("application/pdf")) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("filetools_extract_button")
                                ) {
                                    Icon(Icons.Default.TextSnippet, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("اختيار ملف PDF للاستخراج", fontWeight = FontWeight.Bold)
                                }
                            }

                            FileToolTab.IMAGES_TO_PDF -> {
                                Text("تحويل الصور إلى كتاب PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "اختر صور السبورة أو الدفاتر من المعرض لتحويلها إلى ملف PDF منسق بمقاس A4.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        imagesToPdfLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("filetools_images_to_pdf_button")
                                ) {
                                    Icon(Icons.Default.Collections, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("اختيار الصور للتحويل", fontWeight = FontWeight.Bold)
                                }
                            }

                            FileToolTab.SPLIT_PDF -> {
                                Text("قص واستخراج صفحات محددة من PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                OutlinedButton(
                                    onClick = { splitPickLauncher.launch(arrayOf("application/pdf")) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (splitSourceName.isBlank()) "اختر ملف PDF للتقسيم" else "$splitSourceName ($splitPageCount صفحة)",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (splitSourceUri != null && splitPageCount > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = splitStartPage,
                                            onValueChange = { splitStartPage = it.filter(Char::isDigit) },
                                            label = { Text("من صفحة") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = splitEndPage,
                                            onValueChange = { splitEndPage = it.filter(Char::isDigit) },
                                            label = { Text("إلى صفحة") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val uri = splitSourceUri ?: return@Button
                                            val start = splitStartPage.toIntOrNull()?.coerceIn(1, splitPageCount) ?: 1
                                            val end = splitEndPage.toIntOrNull()?.coerceIn(start, splitPageCount) ?: splitPageCount
                                            coroutineScope.launch {
                                                isBusy = true
                                                outputPdfFile = null
                                                val splitFile = withContext(Dispatchers.IO) {
                                                    splitPdfRange(context, uri, start, end)
                                                }
                                                isBusy = false
                                                if (splitFile != null) {
                                                    outputPdfFile = splitFile
                                                    statusMessage = "تم استخراج الصفحات $start..$end في ${splitFile.name}"
                                                } else {
                                                    statusMessage = "تعذر تقسيم الصفحات المطلوبة."
                                                }
                                            }
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCut, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("استخراج الصفحات المحددة", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        statusMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                outputPdfFile?.let { file ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${file.length() / 1024} كيلوبايت",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Button(
                                onClick = { sharePdf(context, file) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة")
                            }
                        }
                    }
                }

                if (extractedPdfText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("النص المستخرج", fontWeight = FontWeight.Bold)
                                Row {
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(extractedPdfText))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("نسخ")
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.addNote(
                                                title = "نص PDF مستخرج",
                                                content = extractedPdfText.take(15000)
                                            )
                                            Toast.makeText(context, "تم الحفظ في الملاحظات", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("حفظ كملاحظة")
                                    }
                                }
                            }
                            Text(
                                text = extractedPdfText.take(6000),
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun mergePdfUris(context: Context, uris: List<Uri>): File? {
    return try {
        val outDir = File(context.filesDir, "study_pdfs").apply { mkdirs() }
        val outFile = File(outDir, "merged_${System.currentTimeMillis()}.pdf")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath
        val streams = mutableListOf<java.io.InputStream>()
        uris.forEach { uri ->
            context.contentResolver.openInputStream(uri)?.let { stream ->
                streams.add(stream)
                merger.addSource(stream)
            }
        }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        streams.forEach { runCatching { it.close() } }
        outFile
    } catch (_: Throwable) {
        null
    }
}

private fun extractTextFromPdfUri(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                val stripper = PDFTextStripper()
                stripper.getText(doc).trim()
            }
        } ?: ""
    } catch (_: Throwable) {
        ""
    }
}

private fun getPdfPageCount(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc -> doc.numberOfPages }
        } ?: 0
    } catch (_: Throwable) {
        0
    }
}

private fun splitPdfRange(context: Context, uri: Uri, startPage: Int, endPage: Int): File? {
    return try {
        val outDir = File(context.filesDir, "study_pdfs").apply { mkdirs() }
        val outFile = File(outDir, "split_${startPage}_to_${endPage}_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { src ->
                PDDocument().use { dst ->
                    for (p in (startPage - 1) until endPage.coerceAtMost(src.numberOfPages)) {
                        dst.addPage(src.getPage(p))
                    }
                    dst.save(outFile)
                }
            }
        }
        outFile
    } catch (_: Throwable) {
        null
    }
}

private fun convertImageUrisToPdf(context: Context, uris: List<Uri>): File? {
    return try {
        val outDir = File(context.filesDir, "study_pdfs").apply { mkdirs() }
        val outFile = File(outDir, "images_book_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageIndex = 1
        uris.forEach { uri ->
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (bmp != null) {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex++).create()
                val page = doc.startPage(info)
                val canvas = page.canvas
                val scale = minOf((pageWidth - 32f) / bmp.width, (pageHeight - 32f) / bmp.height)
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (pageWidth - drawW) / 2f
                val top = (pageHeight - drawH) / 2f
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + drawW, top + drawH), null)
                doc.finishPage(page)
            }
        }
        FileOutputStream(outFile).use { fos -> doc.writeTo(fos) }
        doc.close()
        outFile
    } catch (_: Throwable) {
        null
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Throwable) {
        null
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف PDF"))
    } catch (_: Throwable) {
        Toast.makeText(context, "تم الحفظ: ${file.name}", Toast.LENGTH_SHORT).show()
    }
}
