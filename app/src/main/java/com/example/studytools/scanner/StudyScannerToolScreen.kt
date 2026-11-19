package com.example.studytools.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.MainViewModel
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

private enum class ScanFilterMode(val labelAr: String) {
    ORIGINAL("الأصلي"),
    HIGH_CONTRAST("تعزيز الحبر"),
    BLACK_AND_WHITE("أبيض وأسود")
}

private data class ScannedPage(
    val id: Long,
    val originalBitmap: Bitmap,
    val rotationDegrees: Int = 0,
    val filter: ScanFilterMode = ScanFilterMode.HIGH_CONTRAST
) {
    fun renderProcessed(): Bitmap {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = if (rotationDegrees % 360 != 0) {
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } else {
            originalBitmap
        }
        if (filter == ScanFilterMode.ORIGINAL) return rotated

        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = when (filter) {
            ScanFilterMode.HIGH_CONTRAST -> {
                val contrast = 1.45f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScanFilterMode.BLACK_AND_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 1.65f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val boost = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                gray.postConcat(boost)
                gray
            }
            ScanFilterMode.ORIGINAL -> ColorMatrix()
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return result
    }
}

@Composable
fun StudyScannerToolScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val pages = remember { mutableStateListOf<ScannedPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var lastExportedPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun addBitmapFromUri(uri: Uri) {
        coroutineScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeScaledBitmap(context, uri) }
            if (bmp != null) {
                pages.add(ScannedPage(id = System.nanoTime(), originalBitmap = bmp))
                selectedPageIndex = pages.lastIndex
            } else {
                Toast.makeText(context, "تعذّر قراءة الصورة المحددة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        uris.forEach { addBitmapFromUri(it) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            addBitmapFromUri(uri)
        }
    }

    fun launchCameraCapture() {
        try {
            val cacheDir = File(context.cacheDir, "scanner_captures").apply { mkdirs() }
            val file = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (t: Throwable) {
            Toast.makeText(context, "تعذر تشغيل الكاميرا: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "يرجى السماح بصلاحية الكاميرا أو اختيار صورة من المعرض", Toast.LENGTH_LONG).show()
        }
    }

    val currentPage = pages.getOrNull(selectedPageIndex)
    val previewBitmap = remember(currentPage) {
        currentPage?.renderProcessed()
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
                // Header
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
                                .testTag("scanner_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "المَاسِحُ الضَّوْئِيُّ",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF5B041)
                            )
                            Text(
                                text = "تحسين الصفحات • استخراج النص • تصدير PDF",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${pages.size} صفحة",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Capture / Pick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) launchCameraCapture()
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("scanner_camera_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصوير صفحة", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("scanner_gallery_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار صور", fontWeight = FontWeight.Bold)
                    }
                }

                if (pages.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "ابدأ بمسح ملازمك ودفاترك",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "التقط صورة بالكاميرا أو اختر صوراً من المعرض لتحسين الحبر وتجميعها في ملف PDF واحد أو استخراج النص منها.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Thumbnails strip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(pages) { index, page ->
                            val isSelected = index == selectedPageIndex
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable { selectedPageIndex = index }
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Image(
                                        bitmap = page.originalBitmap.asImageBitmap(),
                                        contentDescription = "صفحة ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 8.dp),
                                        color = Color.Black.copy(alpha = 0.65f)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Page Preview + Controls
                    if (previewBitmap != null && currentPage != null) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp, max = 340.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = "معاينة الصفحة",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScanFilterMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = currentPage.filter == mode,
                                            onClick = {
                                                pages[selectedPageIndex] = currentPage.copy(filter = mode)
                                            },
                                            label = { Text(mode.labelAr) }
                                        )
                                    }
                                    AssistChip(
                                        onClick = {
                                            pages[selectedPageIndex] = currentPage.copy(
                                                rotationDegrees = (currentPage.rotationDegrees + 90) % 360
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                                        },
                                        label = { Text("تدوير 90°") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            pages.removeAt(selectedPageIndex)
                                            selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        },
                                        label = { Text("حذف") }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isProcessing = true
                                                val file = withContext(Dispatchers.IO) {
                                                    exportPagesAsPdf(context, pages)
                                                }
                                                isProcessing = false
                                                if (file != null) {
                                                    lastExportedPdfFile = file
                                                    Toast.makeText(
                                                        context,
                                                        "تم إنشاء ملف PDF (${pages.size} صفحة)",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("scanner_export_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تصدير PDF", fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isProcessing = true
                                                val text = runMlKitOcr(previewBitmap)
                                                isProcessing = false
                                                extractedText = text.ifBlank {
                                                    "لم يتم العثور على نص واضح بالصورة. جرّب فلتر تعزيز الحبر."
                                                }
                                            }
                                        },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("scanner_ocr_button")
                                    ) {
                                        Icon(Icons.Default.TextFields, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("استخراج النص", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    lastExportedPdfFile?.let { pdfFile ->
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
                                        text = "تم تجهيز الملف: ${pdfFile.name}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${pdfFile.length() / 1024} كيلوبايت",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Button(
                                    onClick = { shareOrOpenPdfFile(context, pdfFile) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاركة")
                                }
                            }
                        }
                    }

                    if (extractedText.isNotBlank()) {
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
                                                clipboardManager.setText(AnnotatedString(extractedText))
                                                Toast.makeText(context, "تم نسخ النص", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("نسخ")
                                        }
                                        TextButton(
                                            onClick = {
                                                viewModel.addNote(
                                                    title = "مسح ضوئي ${System.currentTimeMillis() / 1000}",
                                                    content = extractedText
                                                )
                                                Toast.makeText(context, "تم الحفظ في الملاحظات", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("حفظ كملاحظة")
                                        }
                                    }
                                }
                                Text(
                                    text = extractedText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        var sampleSize = 1
        val maxDim = 1800
        while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val loadOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, loadOpts)
        }
    } catch (_: Throwable) {
        null
    }
}

private suspend fun runMlKitOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result.text ?: "")
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume("")
            }
    } catch (_: Throwable) {
        if (cont.isActive) cont.resume("")
    }
}

private fun exportPagesAsPdf(context: Context, pages: List<ScannedPage>): File? {
    return try {
        val pdfDir = File(context.filesDir, "study_pdfs").apply { mkdirs() }
        val outFile = File(pdfDir, "scan_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageWidth = 595 // A4 width at 72 dpi
        val pageHeight = 842 // A4 height at 72 dpi

        pages.forEachIndexed { idx, scanned ->
            val bmp = scanned.renderProcessed()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val scale = minOf(
                (pageWidth - 32f) / bmp.width.toFloat(),
                (pageHeight - 32f) / bmp.height.toFloat()
            )
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val left = (pageWidth - drawW) / 2f
            val top = (pageHeight - drawH) / 2f
            val destRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bmp, null, destRect, null)
            doc.finishPage(page)
        }

        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
        outFile
    } catch (_: Throwable) {
        null
    }
}

private fun shareOrOpenPdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف PDF"))
    } catch (_: Throwable) {
        Toast.makeText(context, "تم حفظ الملف في: ${file.name}", Toast.LENGTH_SHORT).show()
    }
}
