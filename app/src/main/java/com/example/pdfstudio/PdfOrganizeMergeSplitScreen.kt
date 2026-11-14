package com.example.pdfstudio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

object PdfOrganizeMergeSplitEngine {
    suspend fun exportOrganizedPdf(
        context: Context,
        sourceFile: File,
        items: List<OrganizePageItem>,
        cropMarginFraction: Float,
        addPageNumbers: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_organized_${System.currentTimeMillis()}.pdf"
            )
            PDDocument.load(sourceFile).use { srcDoc ->
                PDDocument().use { dstDoc ->
                    val totalOut = items.size
                    items.forEachIndexed { idx, item ->
                        val page: PDPage = when (item.sourcePageIndex) {
                            -1 -> PDPage(PDRectangle.A4)
                            -2 -> {
                                val ruledPage = PDPage(PDRectangle.A4)
                                val bmp = PdfEngineHelper.createBlankOrRuledPageBitmap(ruled = true)
                                val pdImg = JPEGFactory.createFromImage(dstDoc, bmp, 0.90f)
                                PDPageContentStream(dstDoc, ruledPage).use { cs ->
                                    cs.drawImage(pdImg, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                                }
                                bmp.recycle()
                                ruledPage
                            }
                            else -> {
                                val original = srcDoc.getPage(item.sourcePageIndex)
                                dstDoc.importPage(original)
                            }
                        }

                        if (item.sourcePageIndex < 0) {
                            dstDoc.addPage(page)
                        }

                        val newRot = ((page.rotation + item.rotationDegrees) % 360 + 360) % 360
                        page.rotation = newRot

                        if (cropMarginFraction > 0.005f) {
                            val box = page.cropBox ?: page.mediaBox
                            val dx = box.width * cropMarginFraction.coerceIn(0f, 0.25f)
                            val dy = box.height * cropMarginFraction.coerceIn(0f, 0.25f)
                            page.cropBox = PDRectangle(
                                box.lowerLeftX + dx,
                                box.lowerLeftY + dy,
                                (box.width - 2f * dx).coerceAtLeast(100f),
                                (box.height - 2f * dy).coerceAtLeast(100f)
                            )
                        }

                        if (addPageNumbers) {
                            val box = page.cropBox ?: page.mediaBox
                            val label = "${idx + 1} / $totalOut"
                            PDPageContentStream(dstDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, 10f)
                                cs.newLineAtOffset(box.lowerLeftX + box.width / 2f - 18f, box.lowerLeftY + 18f)
                                cs.showText(label)
                                cs.endText()
                            }
                        }
                    }
                    dstDoc.save(outFile)
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergePdfs(
        context: Context,
        inputs: List<MergeInputDoc>,
        addBookmarksOutline: Boolean,
        outputBaseName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        val workingFiles = mutableListOf<File>()
        try {
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${outputBaseName.ifBlank { "merged_study" }}_${System.currentTimeMillis()}.pdf"
            )
            val docStarts = mutableListOf<Pair<String, Int>>()
            var runningPages = 0

            val merger = PDFMergerUtility()
            merger.destinationFileName = outFile.absolutePath

            inputs.forEach { input ->
                val f = PdfEngineHelper.copyUriToWorkingFile(context, input.uri.toString(), input.title)
                    ?: throw IllegalStateException("تعذر قراءة الملف: ${input.title}")
                workingFiles.add(f)
                val pages = PdfEngineHelper.getPdfPageCount(f)
                docStarts.add(input.title.removeSuffix(".pdf") to runningPages)
                runningPages += pages
                merger.addSource(f)
            }

            merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())

            if (addBookmarksOutline && docStarts.isNotEmpty()) {
                val tempBookmarksOut = File(
                    PdfEngineHelper.getStudioCacheDir(context),
                    "bm_${System.currentTimeMillis()}.pdf"
                )
                PDDocument.load(outFile).use { mergedDoc ->
                    val outline = PDDocumentOutline()
                    mergedDoc.documentCatalog.documentOutline = outline
                    docStarts.forEach { (title, pageIdx) ->
                        if (pageIdx in 0 until mergedDoc.numberOfPages) {
                            val item = PDOutlineItem().apply {
                                this.title = title
                                destination = PDPageFitDestination().apply {
                                    page = mergedDoc.getPage(pageIdx)
                                }
                            }
                            outline.addLast(item)
                        }
                    }
                    mergedDoc.save(tempBookmarksOut)
                }
                tempBookmarksOut.copyTo(outFile, overwrite = true)
                tempBookmarksOut.delete()
            }

            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            workingFiles.forEach { f ->
                if (f.parentFile?.name == "pdf_studio_work") f.delete()
            }
        }
    }

    fun parseCustomRanges(expr: String, maxPages: Int): Result<List<IntRange>> {
        val cleaned = expr.trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("أدخل نطاقاً مثل: 1-5, 8, 10-12"))
        val ranges = mutableListOf<IntRange>()
        val parts = cleaned.split(',', '،')
        for (raw in parts) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            if (part.contains('-')) {
                val bounds = part.split('-')
                if (bounds.size != 2) {
                    return Result.failure(IllegalArgumentException("صيغة غير صحيحة عند: $part"))
                }
                val start = bounds[0].trim().toIntOrNull()
                val end = bounds[1].trim().toIntOrNull()
                if (start == null || end == null || start < 1 || end > maxPages || start > end) {
                    return Result.failure(IllegalArgumentException("نطاق خارج الحدود (1-$maxPages): $part"))
                }
                ranges.add(start..end)
            } else {
                val single = part.toIntOrNull()
                if (single == null || single < 1 || single > maxPages) {
                    return Result.failure(IllegalArgumentException("رقم صفحة غير صالح (1-$maxPages): $part"))
                }
                ranges.add(single..single)
            }
        }
        if (ranges.isEmpty()) return Result.failure(IllegalArgumentException("لم يتم العثور على نطاقات صالحة"))
        return Result.success(ranges)
    }

    suspend fun splitPdf(
        context: Context,
        sourceFile: File,
        ranges1Based: List<IntRange>
    ): Result<List<Pair<File, Int>>> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        val outputs = mutableListOf<Pair<File, Int>>()
        try {
            PDDocument.load(sourceFile).use { srcDoc ->
                ranges1Based.forEachIndexed { idx, range ->
                    val outFile = File(
                        PdfEngineHelper.getStudioExportsDir(context),
                        "${sourceFile.nameWithoutExtension}_part${idx + 1}_${range.first}-${range.last}_${System.currentTimeMillis()}.pdf"
                    )
                    PDDocument().use { partDoc ->
                        for (p in range) {
                            val page = srcDoc.getPage(p - 1)
                            partDoc.importPage(page)
                        }
                        partDoc.save(outFile)
                        outputs.add(outFile to partDoc.numberOfPages)
                    }
                }
            }
            Result.success(outputs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun PdfOrganizeMergeSplitScreen(
    initialTab: OrganizeModeTab = OrganizeModeTab.ORGANIZE,
    onOpenInReader: (uriString: String, title: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var activeTab by remember { mutableStateOf(initialTab) }

    Scaffold(
        topBar = {
            Column {
                PdfStudioTopBar(
                    title = "التنظيم والدمج والتقسيم",
                    subtitle = "تحكم كامل في ترتيب وصفحات ملفات الـ PDF",
                    onBack = onBack,
                    testTagBack = "organize_back_btn"
                )
                TabRow(selectedTabIndex = activeTab.ordinal) {
                    OrganizeModeTab.entries.forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            modifier = Modifier.testTag("org_tab_${tab.name}"),
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
                OrganizeModeTab.ORGANIZE -> OrganizePagesPanel(onOpenInReader = onOpenInReader)
                OrganizeModeTab.MERGE -> MergePdfsPanel(onOpenInReader = onOpenInReader)
                OrganizeModeTab.SPLIT -> SplitPdfPanel(onOpenInReader = onOpenInReader)
            }
        }
    }
}

@Composable
private fun OrganizePagesPanel(onOpenInReader: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { PdfRenderCache() }

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<OrganizePageItem>>(emptyList()) }
    var selectedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cropFraction by remember { mutableFloatStateOf(0f) }
    var addPageNumbers by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = PdfEngineHelper.queryDisplayName(context, uri)
                val f = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), name)
                if (f != null) {
                    val count = PdfEngineHelper.getPdfPageCount(f)
                    if (count > 0) {
                        sourceFile = f
                        fileName = name
                        items = (0 until count).map { idx ->
                            OrganizePageItem(UUID.randomUUID().toString(), idx, 0, "ص ${idx + 1}")
                        }
                        selectedUids = emptySet()
                        exportedFile = null
                    } else {
                        Toast.makeText(context, "الملف قد يكون محمياً أو تالفاً", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val out = exportedFile
        if (uri != null && out != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, out, uri)
                Toast.makeText(context, if (ok) "تم حفظ الملف المنظم ✅" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        if (sourceFile == null) {
            StackedDepthCard {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("تنظيم صفحات PDF", style = PdfTypography.uiTitle(17.sp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "اختر ملفاً لإعادة ترتيب صفحاته، تدويرها، حذف غير المهم، أو إضافة ورق مسطّر للملاحظات وقص الهوامش.",
                        style = PdfTypography.uiBody(13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("org_pick_pdf_btn")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار ملف PDF", style = PdfTypography.uiLabel())
                    }
                }
            }
        } else {
            // شريط إجراءات الصفحة السريع
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { pickLauncher.launch(arrayOf("application/pdf")) }) {
                    Text("تغيير الملف", style = PdfTypography.uiLabel(12.sp))
                }
                OutlinedButton(
                    onClick = {
                        val targets = selectedUids
                        items = items.map {
                            if (targets.isEmpty() || it.uid in targets) {
                                it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
                            } else it
                        }
                    }
                ) {
                    Icon(Icons.Default.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (selectedUids.isEmpty()) "تدوير الكل" else "تدوير المحدد", style = PdfTypography.uiLabel(12.sp))
                }
                OutlinedButton(
                    onClick = {
                        items = items + OrganizePageItem(UUID.randomUUID().toString(), -2, 0, "ورقة مسطرة")
                    }
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ ورقة مسطرة", style = PdfTypography.uiLabel(12.sp))
                }
                OutlinedButton(
                    onClick = {
                        items = items + OrganizePageItem(UUID.randomUUID().toString(), -1, 0, "ورقة بيضاء")
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ بيضاء", style = PdfTypography.uiLabel(12.sp))
                }
                if (selectedUids.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val newList = mutableListOf<OrganizePageItem>()
                            items.forEach { item ->
                                newList.add(item)
                                if (item.uid in selectedUids) {
                                    newList.add(item.copy(uid = UUID.randomUUID().toString()))
                                }
                            }
                            items = newList
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تكرار", style = PdfTypography.uiLabel(12.sp))
                    }
                    OutlinedButton(
                        onClick = {
                            val remaining = items.filterNot { it.uid in selectedUids }
                            if (remaining.isEmpty()) {
                                Toast.makeText(context, "يجب بقاء صفحة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                            } else {
                                items = remaining
                                selectedUids = emptySet()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف", style = PdfTypography.uiLabel(12.sp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // خيارات قص الهوامش والترقيم
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("قص الهوامش: ${(cropFraction * 100).roundToInt()}%", style = PdfTypography.uiLabel(11.sp))
                    Slider(
                        value = cropFraction,
                        onValueChange = { cropFraction = it },
                        valueRange = 0f..0.20f,
                        modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { addPageNumbers = !addPageNumbers }
                ) {
                    Checkbox(checked = addPageNumbers, onCheckedChange = { addPageNumbers = it })
                    Text("ترقيم الصفحات", style = PdfTypography.uiLabel(12.sp))
                }
            }

            // شبكة المصغرات التفاعلية
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(items, key = { _, it -> it.uid }) { idx, pageItem ->
                    val isSel = pageItem.uid in selectedUids
                    val sf = sourceFile!!
                    var thumb by remember(pageItem.uid, pageItem.rotationDegrees) { mutableStateOf<Bitmap?>(null) }

                    LaunchedEffect(pageItem.uid, pageItem.rotationDegrees) {
                        thumb = when (pageItem.sourcePageIndex) {
                            -1 -> PdfEngineHelper.createBlankOrRuledPageBitmap(220, 310, ruled = false)
                            -2 -> PdfEngineHelper.createBlankOrRuledPageBitmap(220, 310, ruled = true)
                            else -> {
                                val key = cache.buildKey(sf.absolutePath, pageItem.sourcePageIndex, 220, pageItem.rotationDegrees)
                                cache.get(key) ?: PdfEngineHelper.renderPageToBitmap(
                                    sf,
                                    pageItem.sourcePageIndex,
                                    220,
                                    pageItem.rotationDegrees
                                )?.also { cache.put(key, it) }
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            selectedUids = if (isSel) selectedUids - pageItem.uid else selectedUids + pageItem.uid
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSel) 2.5.dp else 1.dp,
                            color = if (isSel) PdfStudioColors.RoyalBlue else PdfStudioColors.cardBorder()
                        ),
                        color = PdfStudioColors.cardSurface(),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        items = items.map {
                                            if (it.uid == pageItem.uid) {
                                                it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
                                            } else it
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Rotate90DegreesCw,
                                        contentDescription = "تدوير الصفحة",
                                        tint = PdfStudioColors.RoyalBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (items.size > 1) {
                                            items = items.filterNot { it.uid == pageItem.uid }
                                            selectedUids = selectedUids - pageItem.uid
                                        } else {
                                            Toast.makeText(context, "يجب بقاء صفحة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف الصفحة",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            ) {
                                val bmp = thumb
                                if (bmp != null && !bmp.isRecycled) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = pageItem.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (isSel) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = PdfStudioColors.RoyalBlue,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val mut = items.toMutableList()
                                            val moved = mut.removeAt(idx)
                                            mut.add(idx - 1, moved)
                                            items = mut
                                        }
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "تقديم", modifier = Modifier.size(16.dp))
                                }
                                Text("صفحة ${idx + 1}", style = PdfTypography.uiLabel(10.sp))
                                IconButton(
                                    onClick = {
                                        if (idx < items.lastIndex) {
                                            val mut = items.toMutableList()
                                            val moved = mut.removeAt(idx)
                                            mut.add(idx + 1, moved)
                                            items = mut
                                        }
                                    },
                                    enabled = idx < items.lastIndex,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "تأخير", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val sf = sourceFile ?: return@Button
                    isProcessing = true
                    scope.launch {
                        val res = PdfOrganizeMergeSplitEngine.exportOrganizedPdf(
                            context, sf, items, cropFraction, addPageNumbers
                        )
                        isProcessing = false
                        if (res.isSuccess) {
                            exportedFile = res.getOrThrow()
                            Toast.makeText(context, "تم إنشاء النسخة المنظمة بنجاح ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "حدث خطأ: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isProcessing && items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("org_export_btn")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("حفظ وبناء الملف النهائي (${items.size} صفحة)", style = PdfTypography.uiTitle(14.sp))
            }

            exportedFile?.let { out ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenInReader(Uri.fromFile(out).toString(), out.name) },
                        modifier = Modifier.weight(1f)
                    ) { Text("فتح بالقارئ", style = PdfTypography.uiLabel(12.sp)) }
                    OutlinedButton(
                        onClick = { saveLauncher.launch("${fileName.removeSuffix(".pdf")}_organized.pdf") },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ", style = PdfTypography.uiLabel(12.sp)) }
                    OutlinedButton(
                        onClick = { PdfEngineHelper.shareFile(context, out, "application/pdf", "مشاركة PDF") },
                        modifier = Modifier.weight(1f)
                    ) { Text("مشاركة", style = PdfTypography.uiLabel(12.sp)) }
                }
            }
        }
    }
}

@Composable
private fun MergePdfsPanel(onOpenInReader: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mergeList by remember { mutableStateOf<List<MergeInputDoc>>(emptyList()) }
    var addBookmarks by remember { mutableStateOf(true) }
    var outputTitle by remember { mutableStateOf("مذكرة_مدمجة") }
    var isMerging by remember { mutableStateOf(false) }
    var mergedFile by remember { mutableStateOf<File?>(null) }

    val pickMultipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = uris.map { uri ->
                    val title = PdfEngineHelper.queryDisplayName(context, uri)
                    val size = PdfEngineHelper.queryFileSizeBytes(context, uri)
                    val tmp = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), title)
                    val pCount = if (tmp != null) PdfEngineHelper.getPdfPageCount(tmp) else 0
                    MergeInputDoc(UUID.randomUUID().toString(), uri, title, size, pCount)
                }
                mergeList = mergeList + added
                mergedFile = null
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val out = mergedFile
        if (uri != null && out != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, out, uri)
                Toast.makeText(context, if (ok) "تم حفظ الملف المدمج ✅" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("اختر ملفات PDF ورتبها للدمج", style = PdfTypography.uiTitle(16.sp))
            Button(
                onClick = { pickMultipleLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.testTag("merge_add_files_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة ملفات", style = PdfTypography.uiLabel())
            }
        }

        OutlinedTextField(
            value = outputTitle,
            onValueChange = { outputTitle = it },
            label = { Text("اسم الملف الناتج", style = PdfTypography.uiLabel()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { addBookmarks = !addBookmarks }
        ) {
            Checkbox(checked = addBookmarks, onCheckedChange = { addBookmarks = it })
            Text("إنشاء فهرس عناوين (Bookmarks) تلقائياً لكل ملف", style = PdfTypography.uiBody(13.sp))
        }

        if (mergeList.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لم تتم إضافة ملفات للدمج بعد", style = PdfTypography.uiTitle(15.sp))
                    Text("أضف ملفين أو أكثر من جهازك للدمج في كتاب واحد", style = PdfTypography.uiBody(12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(mergeList, key = { _, it -> it.id }) { idx, doc ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${idx + 1}", style = PdfTypography.uiLabel(), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title, style = PdfTypography.uiTitle(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${doc.pageCount} صفحة • ${PdfEngineHelper.formatFileSize(doc.sizeBytes)}",
                                    style = PdfTypography.uiLabel(11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val mut = mergeList.toMutableList()
                                        val item = mut.removeAt(idx)
                                        mut.add(idx - 1, item)
                                        mergeList = mut
                                    }
                                },
                                enabled = idx > 0
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى") }
                            IconButton(
                                onClick = {
                                    if (idx < mergeList.lastIndex) {
                                        val mut = mergeList.toMutableList()
                                        val item = mut.removeAt(idx)
                                        mut.add(idx + 1, item)
                                        mergeList = mut
                                    }
                                },
                                enabled = idx < mergeList.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل") }
                            IconButton(onClick = { mergeList = mergeList.filterNot { it.id == doc.id } }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                isMerging = true
                scope.launch {
                    val res = PdfOrganizeMergeSplitEngine.mergePdfs(context, mergeList, addBookmarks, outputTitle)
                    isMerging = false
                    if (res.isSuccess) {
                        mergedFile = res.getOrThrow()
                        Toast.makeText(context, "تم دمج ${mergeList.size} ملفات بنجاح ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "فشل الدمج: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = mergeList.size >= 2 && !isMerging,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("merge_execute_btn")
        ) {
            if (isMerging) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("دمج ${mergeList.size} ملفات الآن", style = PdfTypography.uiTitle(14.sp))
        }

        mergedFile?.let { out ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenInReader(Uri.fromFile(out).toString(), out.name) },
                    modifier = Modifier.weight(1f)
                ) { Text("فتح", style = PdfTypography.uiLabel()) }
                OutlinedButton(
                    onClick = { saveLauncher.launch("${outputTitle.ifBlank { "merged" }}.pdf") },
                    modifier = Modifier.weight(1f)
                ) { Text("حفظ", style = PdfTypography.uiLabel()) }
                OutlinedButton(
                    onClick = { PdfEngineHelper.shareFile(context, out, "application/pdf", "مشاركة PDF المدمج") },
                    modifier = Modifier.weight(1f)
                ) { Text("مشاركة", style = PdfTypography.uiLabel()) }
            }
        }
    }
}

@Composable
private fun SplitPdfPanel(onOpenInReader: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }
    var totalPages by remember { mutableIntStateOf(0) }

    var useCustomRange by remember { mutableStateOf(false) }
    var everyNPagesText by remember { mutableStateOf("5") }
    var customRangeText by remember { mutableStateOf("1-3, 4-6") }
    var isSplitting by remember { mutableStateOf(false) }
    var splitParts by remember { mutableStateOf<List<Pair<File, Int>>>(emptyList()) }
    var pendingSavePart by remember { mutableStateOf<File?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = PdfEngineHelper.queryDisplayName(context, uri)
                val f = PdfEngineHelper.copyUriToWorkingFile(context, uri.toString(), name)
                if (f != null) {
                    val count = PdfEngineHelper.getPdfPageCount(f)
                    if (count > 0) {
                        sourceFile = f
                        fileName = name
                        totalPages = count
                        splitParts = emptyList()
                        if (count >= 2) {
                            val half = count / 2
                            customRangeText = "1-$half, ${half + 1}-$count"
                        }
                    } else {
                        Toast.makeText(context, "الملف غير مقروء أو محمي بكلمة مرور", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "تعذر قراءة الملف المختار", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val savePartLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pendingSavePart
        if (uri != null && f != null) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, f, uri)
                Toast.makeText(context, if (ok) "تم حفظ الجزء بنجاح ✅" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
        pendingSavePart = null
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
                    Text(
                        text = if (sourceFile == null) "اختر ملف PDF لتقسيمه" else fileName,
                        style = PdfTypography.uiTitle(15.sp)
                    )
                    Text(
                        text = if (sourceFile == null) "تقسيم لملفات أصغر أو استخراج فصول محددة" else "إجمالي الصفحات: $totalPages",
                        style = PdfTypography.uiBody(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.testTag("split_pick_pdf_btn")
                ) {
                    Text(if (sourceFile == null) "اختيار" else "تغيير", style = PdfTypography.uiLabel())
                }
            }
        }

        if (sourceFile != null && totalPages > 0) {
            Surface(
                onClick = { useCustomRange = false },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (!useCustomRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !useCustomRange, onClick = { useCustomRange = false })
                        Text("تقسيم تلقائي كل N صفحات", style = PdfTypography.uiTitle(14.sp))
                    }
                    if (!useCustomRange) {
                        OutlinedTextField(
                            value = everyNPagesText,
                            onValueChange = { if (it.all(Char::isDigit)) everyNPagesText = it },
                            label = { Text("عدد الصفحات في كل جزء", style = PdfTypography.uiLabel()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = { useCustomRange = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (useCustomRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = useCustomRange, onClick = { useCustomRange = true })
                        Text("استخراج نطاقات مخصصة (مثلاً: 1-5, 8, 12-15)", style = PdfTypography.uiTitle(14.sp))
                    }
                    if (useCustomRange) {
                        OutlinedTextField(
                            value = customRangeText,
                            onValueChange = { customRangeText = it },
                            label = { Text("النطاقات (1 إلى $totalPages)", style = PdfTypography.uiLabel()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("split_custom_range_input")
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val sf = sourceFile ?: return@Button
                    val ranges: List<IntRange> = if (useCustomRange) {
                        val parsed = PdfOrganizeMergeSplitEngine.parseCustomRanges(customRangeText, totalPages)
                        if (parsed.isFailure) {
                            Toast.makeText(context, parsed.exceptionOrNull()?.localizedMessage ?: "نطاق غير صالح", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        parsed.getOrThrow()
                    } else {
                        val n = everyNPagesText.toIntOrNull()?.coerceIn(1, totalPages) ?: 1
                        val chunks = mutableListOf<IntRange>()
                        var start = 1
                        while (start <= totalPages) {
                            val end = (start + n - 1).coerceAtMost(totalPages)
                            chunks.add(start..end)
                            start = end + 1
                        }
                        chunks
                    }

                    isSplitting = true
                    scope.launch {
                        val res = PdfOrganizeMergeSplitEngine.splitPdf(context, sf, ranges)
                        isSplitting = false
                        if (res.isSuccess) {
                            splitParts = res.getOrThrow()
                            Toast.makeText(context, "تم التقسيم إلى ${splitParts.size} ملفات ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "تعذر التقسيم: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isSplitting,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("split_execute_btn")
            ) {
                Icon(Icons.Default.CallSplit, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSplitting) "جارٍ التقسيم..." else "بدء التقسيم الفعلي", style = PdfTypography.uiTitle(14.sp))
            }

            if (splitParts.isNotEmpty()) {
                Text("الملفات الناتجة (${splitParts.size})", style = PdfTypography.uiTitle(15.sp))
                splitParts.forEachIndexed { idx, (file, pages) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الجزء ${idx + 1}: ${file.name}", style = PdfTypography.uiTitle(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$pages صفحة • ${PdfEngineHelper.formatFileSize(file.length())}", style = PdfTypography.uiLabel(11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                IconButton(onClick = { onOpenInReader(Uri.fromFile(file).toString(), file.name) }) {
                                    Icon(Icons.Default.MenuBook, contentDescription = "قراءة")
                                }
                                IconButton(onClick = {
                                    pendingSavePart = file
                                    savePartLauncher.launch(file.name)
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "حفظ")
                                }
                                IconButton(onClick = { PdfEngineHelper.shareFile(context, file, "application/pdf", "مشاركة جزء PDF") }) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
