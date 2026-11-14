package com.example.pdfstudio

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * الواجهة الثالثة: شاشة الضغط الذكي وتنظيم الصفحات المطابقة للشكل (3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompressionOrganizeScreen(
    initialUriString: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbCache = remember { PdfRenderCache() }

    BackHandler { onBack() }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var originalBytes by remember { mutableLongStateOf(0L) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageItems by remember { mutableStateOf<List<OrganizePageItem>>(emptyList()) }

    var selectedLevel by remember { mutableStateOf(CompressionLevel.BALANCED) }
    var stripMetadataAndUnusedObjects by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var isCompressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<CompressionResult?>(null) }

    suspend fun loadUri(uriStr: String) {
        val uri = Uri.parse(uriStr)
        val name = PdfEngineHelper.queryDisplayName(context, uri)
        val copied = PdfEngineHelper.copyUriToWorkingFile(context, uriStr, name)
        if (copied != null) {
            val count = PdfEngineHelper.getPdfPageCount(copied)
            selectedFile = copied
            selectedFileName = name
            originalBytes = copied.length()
            pageCount = count
            pageItems = (0 until count).map { idx ->
                OrganizePageItem(UUID.randomUUID().toString(), idx, 0, "صفحة ${idx + 1}")
            }
            result = null
        } else {
            Toast.makeText(context, "تعذر قراءة الملف المختار", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialUriString) {
        if (!initialUriString.isNullOrBlank()) {
            loadUri(initialUriString)
        }
    }

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch { loadUri(uri.toString()) }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val outPath = result?.outputFilePath
        if (targetUri != null && outPath != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, File(outPath), targetUri)
                Toast.makeText(
                    context,
                    if (ok) "تم حفظ الملف المضغوط بنجاح ✅" else "تعذر الحفظ",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        containerColor = PdfStudioColors.screenBackground(),
        topBar = {
            PdfStudioTopBar(
                title = "ضغط وتنظيم الملف",
                onBack = onBack,
                testTagBack = "compress_back_btn",
                transparentBackground = true,
                centerTitle = true,
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("compress_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "إعدادات الضغط والملف",
                            tint = PdfStudioColors.titleText()
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = PdfStudioColors.screenBackground(),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        val sf = selectedFile
                        if (sf == null) {
                            pickPdfLauncher.launch(arrayOf("application/pdf"))
                            return@Button
                        }
                        isCompressing = true
                        progress = 0.05f
                        result = null
                        scope.launch {
                            val hasOrganizeEdits = pageItems.size != pageCount || pageItems.anyPageEdited()
                            val res = PdfCompressionEngine.compressPdf(
                                context = context,
                                sourceFile = sf,
                                level = selectedLevel,
                                organizedPages = if (hasOrganizeEdits) pageItems else null,
                                stripMetadata = stripMetadataAndUnusedObjects
                            ) { p -> progress = p }
                            isCompressing = false
                            if (res.isSuccess) {
                                result = res.getOrThrow()
                            } else {
                                Toast.makeText(
                                    context,
                                    "تعذر ضغط الملف: ${res.exceptionOrNull()?.localizedMessage ?: "تأكد أنه غير محمي بكلمة سر"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isCompressing,
                    colors = ButtonDefaults.buttonColors(containerColor = PdfStudioColors.RoyalBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(54.dp)
                        .testTag("compress_start_btn")
                ) {
                    Text(
                        text = when {
                            isCompressing -> "جارٍ الضغط والحفظ..."
                            selectedFile == null -> "اختر ملف PDF للضغط"
                            else -> "حفظ الملف المضغوط الآن"
                        },
                        style = PdfTypography.uiTitle(16.sp, FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. بطاقة المعاينة ونسبة التوفير مطابقة للشكل (3)
            item(span = { GridItemSpan(3) }) {
                Surface(
                    onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                    shape = RoundedCornerShape(20.dp),
                    color = PdfStudioColors.cardSurface(),
                    border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compress_pick_pdf_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // أيقونة PDF حمراء مطابقة للشكل (3)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEF4444),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "PDF",
                                        style = PdfTypography.uiLabel(12.sp, FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedFile == null) "اضغط لاختيار ملف PDF" else selectedFileName,
                                    style = PdfTypography.uiTitle(15.sp, FontWeight.Bold),
                                    color = PdfStudioColors.titleText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                if (selectedFile != null) {
                                    val displayTargetBytes = result?.compressedSizeBytes
                                        ?: (originalBytes * selectedLevel.estimatedRatio).toLong()
                                    Text(
                                        text = "${PdfEngineHelper.formatFileSize(originalBytes)} → ${PdfEngineHelper.formatFileSize(displayTargetBytes)}",
                                        style = PdfTypography.uiLabel(12.sp, FontWeight.Medium),
                                        color = PdfStudioColors.subtitleText()
                                    )
                                } else {
                                    Text(
                                        text = "ضغط ذكي مع تنظيم وتدوير وحذف الصفحات",
                                        style = PdfTypography.uiBody(12.sp),
                                        color = PdfStudioColors.subtitleText()
                                    )
                                }
                            }
                        }

                        if (selectedFile != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val displaySavings = result?.savedPercent
                                ?: ((1f - selectedLevel.estimatedRatio) * 100f).roundToInt()
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = PdfStudioColors.AccentMint
                            ) {
                                Text(
                                    text = "توفير $displaySavings% من الحجم",
                                    style = PdfTypography.uiLabel(12.sp, FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. بطاقات مستويات الضغط الثلاث (رأسياً بمسافة 12.dp عبر LazyVerticalGrid verticalArrangement)
            CompressionLevel.entries.forEach { level ->
                item(span = { GridItemSpan(3) }) {
                    val isSelected = level == selectedLevel
                    Surface(
                        onClick = { if (!isCompressing) selectedLevel = level },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) PdfStudioColors.RoyalBlue.copy(alpha = 0.08f) else PdfStudioColors.cardSurface(),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) PdfStudioColors.RoyalBlue else PdfStudioColors.cardBorder()
                        ),
                        shadowElevation = if (isSelected) 2.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compress_level_${level.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = level.titleAr,
                                style = PdfTypography.uiTitle(15.sp, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                color = if (isSelected) PdfStudioColors.titleText() else PdfStudioColors.titleText(),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "محدد",
                                    tint = PdfStudioColors.RoyalBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // شريط التقدم أو نتيجة الضغط إن وُجدت
            if (isCompressing) {
                item(span = { GridItemSpan(3) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = PdfStudioColors.RoyalBlue
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "نسبة الإنجاز: ${(progress * 100).roundToInt()}%",
                            style = PdfTypography.uiLabel(12.sp),
                            color = PdfStudioColors.RoyalBlue
                        )
                    }
                }
            }

            result?.let { res ->
                item(span = { GridItemSpan(3) }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PdfStudioColors.AccentMint.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PdfStudioColors.AccentMint.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PdfStudioColors.AccentMint)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (res.wasKeptOriginal) {
                                        "الملف مضغوط بالفعل؛ تم الاحتفاظ بالنسخة الأصلية لحماية الجودة"
                                    } else {
                                        "تم ضغط الملف بنجاح (${PdfEngineHelper.formatFileSize(res.originalSizeBytes)} → ${PdfEngineHelper.formatFileSize(res.compressedSizeBytes)})"
                                    },
                                    style = PdfTypography.uiTitle(14.sp),
                                    color = PdfStudioColors.titleText()
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        saveLauncher.launch("${selectedFileName.removeSuffix(".pdf")}_compressed.pdf")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PdfStudioColors.RoyalBlue),
                                    modifier = Modifier.weight(1f).testTag("compress_save_btn")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("حفظ بالجهاز", style = PdfTypography.uiLabel())
                                }
                                OutlinedButton(
                                    onClick = {
                                        PdfEngineHelper.shareFile(
                                            context,
                                            File(res.outputFilePath),
                                            "application/pdf",
                                            "مشاركة الملف المضغوط"
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("compress_share_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة", style = PdfTypography.uiLabel())
                                }
                            }
                        }
                    }
                }
            }

            // 3. عنوان وشبكة «تنظيم وترتيب الصفحات» من 3 أعمدة مطابقة للشكل (3)
            if (selectedFile != null && pageItems.isNotEmpty()) {
                val sf = selectedFile!!
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(PdfStudioColors.RoyalBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تنظيم وترتيب الصفحات",
                            style = PdfTypography.uiTitle(16.sp, FontWeight.Bold),
                            color = PdfStudioColors.titleText()
                        )
                    }
                }

                itemsIndexed(pageItems, key = { _, it -> it.uid }) { idx, pageItem ->
                    var thumb by remember(pageItem.uid, pageItem.rotationDegrees) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(pageItem.uid, pageItem.rotationDegrees) {
                        val key = thumbCache.buildKey(sf.absolutePath, pageItem.sourcePageIndex, 220, pageItem.rotationDegrees)
                        thumb = thumbCache.get(key) ?: PdfEngineHelper.renderPageToBitmap(
                            sf,
                            pageItem.sourcePageIndex,
                            220,
                            pageItem.rotationDegrees
                        )?.also { thumbCache.put(key, it) }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PdfStudioColors.cardSurface(),
                        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // زر تدوير (↻) وزر حذف (🗑️) داخل دوائر رمادية ناعمة مطابقة للصورة (3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    onClick = {
                                        pageItems = pageItems.map {
                                            if (it.uid == pageItem.uid) {
                                                it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
                                            } else it
                                        }
                                    },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Rotate90DegreesCw,
                                            contentDescription = "تدوير",
                                            tint = PdfStudioColors.subtitleText(),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = {
                                        if (pageItems.size > 1) {
                                            pageItems = pageItems.filterNot { it.uid == pageItem.uid }
                                        } else {
                                            Toast.makeText(context, "يجب بقاء صفحة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = PdfStudioColors.subtitleText(),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.76f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                            ) {
                                val bmp = thumb
                                if (bmp != null && !bmp.isRecycled) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "صفحة ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val mut = pageItems.toMutableList()
                                            val moved = mut.removeAt(idx)
                                            mut.add(idx - 1, moved)
                                            pageItems = mut
                                        }
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "تقديم",
                                        tint = if (idx > 0) PdfStudioColors.RoyalBlue else Color.LightGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = "صفحة ${idx + 1}",
                                    style = PdfTypography.uiLabel(12.sp, FontWeight.SemiBold),
                                    color = PdfStudioColors.titleText()
                                )
                                IconButton(
                                    onClick = {
                                        if (idx < pageItems.lastIndex) {
                                            val mut = pageItems.toMutableList()
                                            val moved = mut.removeAt(idx)
                                            mut.add(idx + 1, moved)
                                            pageItems = mut
                                        }
                                    },
                                    enabled = idx < pageItems.lastIndex,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "تأخير",
                                        tint = if (idx < pageItems.lastIndex) PdfStudioColors.RoyalBlue else Color.LightGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("إعدادات الضغط والتنظيم", style = PdfTypography.uiTitle(18.sp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                            Text("تنظيف البيانات الوصفية والخطوط غير المستخدمة", style = PdfTypography.uiTitle(14.sp))
                            Text("تقليل إضافي للمساحة بدون التأثير على جودة العرض", style = PdfTypography.uiBody(12.sp), color = PdfStudioColors.subtitleText())
                        }
                        Switch(
                            checked = stripMetadataAndUnusedObjects,
                            onCheckedChange = { stripMetadataAndUnusedObjects = it }
                        )
                    }
                }
                Button(
                    onClick = {
                        showSettingsSheet = false
                        pickPdfLauncher.launch(arrayOf("application/pdf"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfStudioColors.RoyalBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختيار أو تغيير ملف الـ PDF", style = PdfTypography.uiLabel(14.sp, FontWeight.Bold))
                }
            }
        }
    }
}

private fun List<OrganizePageItem>.anyPageEdited(): Boolean {
    for (i in indices) {
        if (this[i].sourcePageIndex != i || this[i].rotationDegrees != 0) return true
    }
    return false
}
