package com.example.pdfstudio

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uriString: String,
    documentTitle: String,
    initialPage: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { PdfStudioStorage(context) }
    val renderCache = remember { PdfRenderCache() }

    BackHandler { onBack() }

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var renderFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var requiresPassword by remember { mutableStateOf(false) }
    var activePassword by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // خصائص القراءة والعرض
    var currentPage by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var viewMode by remember { mutableStateOf(PdfReaderViewMode.CONTINUOUS) }
    var eyeCareMode by remember { mutableStateOf(PdfEyeCareMode.DAY) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var cleanReadingMode by remember { mutableStateOf(false) }
    var showThumbnailStrip by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // أدوات البحث والفهرس والعلامات
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<PdfSearchMatch>>(emptyList()) }
    var currentSearchIndex by remember { mutableIntStateOf(0) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<PdfBookmarkItem>>(emptyList()) }
    var outlineNodes by remember { mutableStateOf<List<PdfOutlineNode>>(emptyList()) }
    var bookmarkLabelInput by remember { mutableStateOf("") }

    // أدوات التحرير والشروحات
    var activeTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var activeHighlightColorArgb by remember { mutableStateOf(HIGHLIGHTER_4_COLORS[0].first) }
    var activeInkColorArgb by remember { mutableStateOf(ANNOTATION_INK_COLORS[1]) }
    var activeWidthDp by remember { mutableFloatStateOf(ANNOTATION_STROKE_WIDTHS[1]) }
    var activeStamp by remember { mutableStateOf(StudyStampType.IMPORTANT) }
    val annotationState = remember { AnnotationDocumentState() }

    val activeEffectiveColorArgb = if (activeTool == AnnotationTool.HIGHLIGHTER) {
        activeHighlightColorArgb
    } else {
        activeInkColorArgb
    }

    // حوار إضافة/تعديل ملاحظة لاصقة أو مربع نص (T)
    var pendingNotePage by remember { mutableStateOf<Int?>(null) }
    var pendingNoteCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingTextBoxPage by remember { mutableStateOf<Int?>(null) }
    var pendingTextBoxCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingNote by remember { mutableStateOf<Pair<Int, PdfTextNote>?>(null) }

    // حالة التصدير والحفظ
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportTempFile by remember { mutableStateOf<File?>(null) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val temp = pendingExportTempFile
        if (targetUri != null && temp != null && temp.exists()) {
            scope.launch {
                val ok = PdfEngineHelper.copyFileToUri(context, temp, targetUri)
                Toast.makeText(
                    context,
                    if (ok) "تم حفظ نسخة الـ PDF بنجاح ✅" else "تعذر حفظ الملف في المسار المختار",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingExportTempFile = null
    }

    val listState = rememberLazyListState()
    val thumbListState = rememberLazyListState()

    // إبقاء الشاشة مضاءة أثناء وضع القراءة الصافية
    DisposableEffect(cleanReadingMode) {
        val activity = context as? Activity
        if (cleanReadingMode) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // تحميل الملف وفحص التشفير واسترجاع الصفحة الأخيرة
    LaunchedEffect(uriString) {
        isLoading = true
        loadError = null
        requiresPassword = false
        val copied = PdfEngineHelper.copyUriToWorkingFile(context, uriString, documentTitle)
        if (copied == null) {
            isLoading = false
            loadError = "تعذر الوصول إلى ملف الـ PDF. تأكد من صلاحية الملف على الجهاز."
            return@LaunchedEffect
        }
        sourceFile = copied
        val res = PdfEngineHelper.prepareRenderableFile(context, copied, null)
        if (res.isSuccess) {
            val rf = res.getOrThrow()
            renderFile = rf
            val count = PdfEngineHelper.getPdfPageCount(rf)
            pageCount = count
            bookmarks = storage.getBookmarks(uriString)
            val savedPage = if (initialPage > 0) initialPage else storage.getLastPage(uriString)
            currentPage = savedPage.coerceIn(0, max(0, count - 1))
            storage.recordRecentPdf(
                RecentPdfRecord(
                    uriString = uriString,
                    title = documentTitle,
                    fileSizeBytes = copied.length(),
                    pageCount = max(1, count),
                    lastPageIndex = currentPage,
                    lastOpenedMillis = System.currentTimeMillis()
                )
            )
            outlineNodes = PdfEngineHelper.loadPdfOutline(context, copied, null)
            isLoading = false
            if (currentPage > 0) {
                listState.scrollToItem(currentPage)
            }
        } else {
            val err = res.exceptionOrNull()
            if (err is SecurityException || PdfEngineHelper.isPdfEncrypted(context, copied)) {
                requiresPassword = true
                isLoading = false
            } else {
                loadError = "تعذر فتح المستند: ${err?.localizedMessage ?: "ملف غير مدعوم"}"
                isLoading = false
            }
        }
    }

    val visibleFirstPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(visibleFirstPage, viewMode, pageCount) {
        if (viewMode == PdfReaderViewMode.CONTINUOUS && pageCount > 0) {
            currentPage = visibleFirstPage.coerceIn(0, pageCount - 1)
        }
    }

    LaunchedEffect(currentPage, pageCount) {
        if (pageCount > 0 && renderFile != null) {
            storage.updateLastPage(uriString, currentPage, pageCount)
            thumbListState.animateScrollToItem(currentPage.coerceIn(0, pageCount - 1))
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (pageCount > 0) {
                storage.updateLastPage(uriString, currentPage, pageCount)
            }
            renderCache.clear()
        }
    }

    fun jumpToPage(pageIdx: Int) {
        val target = pageIdx.coerceIn(0, max(0, pageCount - 1))
        currentPage = target
        if (viewMode == PdfReaderViewMode.CONTINUOUS) {
            scope.launch { listState.animateScrollToItem(target) }
        }
    }

    fun runExport(flatten: Boolean, shareAfter: Boolean) {
        val src = sourceFile ?: return
        isExporting = true
        scope.launch {
            val suffix = if (flatten) "flattened" else "annotated"
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${documentTitle.removeSuffix(".pdf")}_${suffix}_${System.currentTimeMillis()}.pdf"
            )
            val res = PdfAnnotationsExporter.exportAnnotatedPdf(
                context = context,
                sourceFile = src,
                password = activePassword,
                annotationsByPage = annotationState.pagesMap,
                flatten = flatten,
                outFile = outFile
            )
            isExporting = false
            if (res.isSuccess) {
                if (shareAfter) {
                    PdfEngineHelper.shareFile(context, outFile, "application/pdf", "مشاركة PDF")
                } else {
                    pendingExportTempFile = outFile
                    saveAsLauncher.launch("${documentTitle.removeSuffix(".pdf")}_$suffix.pdf")
                }
            } else {
                Toast.makeText(
                    context,
                    "فشل التصدير: ${res.exceptionOrNull()?.localizedMessage ?: "خطأ"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val pageColorFilter = remember(eyeCareMode) {
        when (eyeCareMode) {
            PdfEyeCareMode.DAY -> null
            PdfEyeCareMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0f, 0f, 0f, 10f,
                        0f, 0.90f, 0f, 0f, 4f,
                        0f, 0f, 0.78f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfEyeCareMode.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.90f, 0f, 0f, 0f, 232f,
                        0f, -0.90f, 0f, 0f, 236f,
                        0f, 0f, -0.88f, 0f, 242f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val canvasBackground = when (eyeCareMode) {
        PdfEyeCareMode.DAY -> PdfStudioColors.screenBackground()
        PdfEyeCareMode.SEPIA -> Color(0xFFF3EAD8)
        PdfEyeCareMode.NIGHT -> Color(0xFF0E131F)
    }

    val floatingCardBg = when (eyeCareMode) {
        PdfEyeCareMode.NIGHT -> Color(0xFF1E293B)
        else -> PdfStudioColors.cardSurface()
    }
    val floatingTitleColor = when (eyeCareMode) {
        PdfEyeCareMode.NIGHT -> Color.White
        else -> PdfStudioColors.titleText()
    }
    val floatingMutedColor = when (eyeCareMode) {
        PdfEyeCareMode.NIGHT -> Color(0xFF94A3B8)
        else -> PdfStudioColors.subtitleText()
    }

    Scaffold(
        containerColor = canvasBackground,
        topBar = {
            // الشريط العلوي العائم الوحيد مطابق للشكل (2) — ممنوع تكدس الأشرطة
            AnimatedVisibility(visible = !cleanReadingMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = floatingCardBg,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            1.dp,
                            if (eyeCareMode == PdfEyeCareMode.NIGHT) Color(0xFF334155) else PdfStudioColors.cardBorder()
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // يمين: زر رجوع + اسم الملف (16.sp Bold سطر واحد)
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reader_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = floatingTitleColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = documentTitle,
                                    style = PdfTypography.uiTitle(16.sp, FontWeight.Bold),
                                    color = floatingTitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // يسار: 3 أيقونات فقط بمسافات مريحة 14.dp (العين، البحث، الإشارة المرجعية)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        cleanReadingMode = true
                                        activeTool = AnnotationTool.NONE
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("reader_clean_mode_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveRedEye,
                                        contentDescription = "وضع القراءة الصافية",
                                        tint = floatingTitleColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showSearchBar = !showSearchBar },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("reader_search_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث في النص",
                                        tint = if (showSearchBar) PdfStudioColors.RoyalBlue else floatingTitleColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                val isCurrentBookmarked = bookmarks.any { it.pageIndex == currentPage }
                                IconButton(
                                    onClick = { showBookmarksSheet = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("reader_bookmarks_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "العلامات والفهرس وخيارات الملف",
                                        tint = if (isCurrentBookmarked) PdfStudioColors.RoyalBlue else floatingTitleColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }

                    // شريط البحث النصي يظهر فقط عند الضغط على أيقونة البحث
                    AnimatedVisibility(visible = showSearchBar) {
                        Surface(
                            color = floatingCardBg,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("ابحث عن تعريف أو كلمة بالكتاب...", style = PdfTypography.uiBody(13.sp)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reader_search_input")
                                    )
                                    Button(
                                        onClick = {
                                            val sf = sourceFile ?: return@Button
                                            isSearching = true
                                            scope.launch {
                                                val res = PdfEngineHelper.searchTextInPdf(context, sf, searchQuery, activePassword)
                                                searchMatches = res
                                                currentSearchIndex = 0
                                                isSearching = false
                                                if (res.isNotEmpty()) {
                                                    jumpToPage(res.first().pageIndex)
                                                } else {
                                                    Toast.makeText(context, "لم يتم العثور على نتائج مطابقة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = searchQuery.isNotBlank() && !isSearching,
                                        colors = ButtonDefaults.buttonColors(containerColor = PdfStudioColors.RoyalBlue),
                                        modifier = Modifier.testTag("reader_search_go_btn")
                                    ) {
                                        Text("بحث", style = PdfTypography.uiLabel())
                                    }
                                }
                                if (isSearching) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                } else if (searchMatches.isNotEmpty()) {
                                    val match = searchMatches[currentSearchIndex.coerceIn(0, searchMatches.lastIndex)]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "نتيجة ${currentSearchIndex + 1} من ${searchMatches.size} (ص ${match.pageIndex + 1}): ${match.snippet}",
                                            style = PdfTypography.uiLabel(11.sp),
                                            color = PdfStudioColors.RoyalBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val prev = (currentSearchIndex - 1 + searchMatches.size) % searchMatches.size
                                                    currentSearchIndex = prev
                                                    jumpToPage(searchMatches[prev].pageIndex)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "السابق")
                                            }
                                            IconButton(
                                                onClick = {
                                                    val next = (currentSearchIndex + 1) % searchMatches.size
                                                    currentSearchIndex = next
                                                    jumpToPage(searchMatches[next].pageIndex)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "التالي")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !cleanReadingMode && pageCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // شريط فرعي صغير سياقي يظهر فقط عند اختيار أداة رسم/نص/ختم دون إفساد التصميم
                    val showSubToolRow = activeTool in listOf(
                        AnnotationTool.PEN,
                        AnnotationTool.SHAPE_RECT,
                        AnnotationTool.SHAPE_OVAL,
                        AnnotationTool.SHAPE_ARROW,
                        AnnotationTool.REDACT,
                        AnnotationTool.TEXT_BOX,
                        AnnotationTool.TEXT_NOTE,
                        AnnotationTool.STAMP
                    )
                    AnimatedVisibility(visible = showSubToolRow) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = floatingCardBg.copy(alpha = 0.96f),
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (activeTool) {
                                    AnnotationTool.TEXT_BOX, AnnotationTool.TEXT_NOTE -> {
                                        SubOptionPill(
                                            label = "ملاحظة لاصقة",
                                            icon = Icons.Default.NoteAdd,
                                            selected = activeTool == AnnotationTool.TEXT_NOTE,
                                            onClick = { activeTool = AnnotationTool.TEXT_NOTE }
                                        )
                                        SubOptionPill(
                                            label = "نص مباشر T",
                                            icon = Icons.Default.TextFields,
                                            selected = activeTool == AnnotationTool.TEXT_BOX,
                                            onClick = { activeTool = AnnotationTool.TEXT_BOX }
                                        )
                                    }
                                    AnnotationTool.STAMP -> {
                                        StudyStampType.entries.forEach { st ->
                                            val isSel = activeStamp == st
                                            Surface(
                                                onClick = { activeStamp = st },
                                                shape = CircleShape,
                                                color = if (isSel) Color(st.badgeColor).copy(alpha = 0.16f) else Color.Transparent,
                                                border = BorderStroke(1.dp, Color(st.badgeColor))
                                            ) {
                                                Text(
                                                    text = st.textAr,
                                                    style = PdfTypography.uiLabel(11.sp, FontWeight.Bold),
                                                    color = Color(st.badgeColor),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        SubOptionPill(
                                            label = "قلم",
                                            icon = Icons.Default.Draw,
                                            selected = activeTool == AnnotationTool.PEN,
                                            onClick = { activeTool = AnnotationTool.PEN }
                                        )
                                        SubOptionPill(
                                            label = "مربع",
                                            icon = Icons.Default.CropSquare,
                                            selected = activeTool == AnnotationTool.SHAPE_RECT,
                                            onClick = { activeTool = AnnotationTool.SHAPE_RECT }
                                        )
                                        SubOptionPill(
                                            label = "دائرة",
                                            icon = Icons.Default.Circle,
                                            selected = activeTool == AnnotationTool.SHAPE_OVAL,
                                            onClick = { activeTool = AnnotationTool.SHAPE_OVAL }
                                        )
                                        SubOptionPill(
                                            label = "سهم",
                                            icon = Icons.Default.ArrowForward,
                                            selected = activeTool == AnnotationTool.SHAPE_ARROW,
                                            onClick = { activeTool = AnnotationTool.SHAPE_ARROW }
                                        )
                                        SubOptionPill(
                                            label = "طمس",
                                            icon = Icons.Default.Block,
                                            selected = activeTool == AnnotationTool.REDACT,
                                            onClick = { activeTool = AnnotationTool.REDACT }
                                        )
                                        ANNOTATION_INK_COLORS.forEach { argb ->
                                            val sel = activeInkColorArgb == argb
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(argb))
                                                    .border(
                                                        width = if (sel) 2.dp else 1.dp,
                                                        color = if (sel) PdfStudioColors.RoyalBlue else Color.LightGray,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { activeInkColorArgb = argb }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { activeTool = AnnotationTool.NONE },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "إنهاء الرسم", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // الشريط السفلي العائم للتلوين والأدوات: صفّان نظيفان فقط مطابق للشكل (2)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = floatingCardBg,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            1.dp,
                            if (eyeCareMode == PdfEyeCareMode.NIGHT) Color(0xFF334155) else PdfStudioColors.cardBorder()
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // الصف الأول: 4 دوائر ألوان تظليل كبيرة (26.dp) على اليمين + أزرار التراجع والإعادة على اليسار
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HIGHLIGHTER_4_COLORS.forEach { (argb, label) ->
                                        val isColorSelected = activeTool == AnnotationTool.HIGHLIGHTER && activeHighlightColorArgb == argb
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(argb))
                                                .border(
                                                    width = if (isColorSelected) 2.5.dp else 1.dp,
                                                    color = if (isColorSelected) PdfStudioColors.RoyalBlue else Color.Black.copy(alpha = 0.08f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    if (isColorSelected) {
                                                        activeTool = AnnotationTool.NONE
                                                    } else {
                                                        activeHighlightColorArgb = argb
                                                        activeTool = AnnotationTool.HIGHLIGHTER
                                                    }
                                                }
                                                .testTag("highlight_color_$label")
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { annotationState.undo() },
                                        enabled = annotationState.canUndo,
                                        modifier = Modifier.size(34.dp).testTag("annot_undo_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "تراجع",
                                            tint = if (annotationState.canUndo) floatingTitleColor else floatingMutedColor.copy(alpha = 0.45f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { annotationState.redo() },
                                        enabled = annotationState.canRedo,
                                        modifier = Modifier.size(34.dp).testTag("annot_redo_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "إعادة",
                                            tint = if (annotationState.canRedo) floatingTitleColor else floatingMutedColor.copy(alpha = 0.45f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // الصف الثاني: 4 أيقونات أدوات (قلم، ممحاة، نص T، ختم دراسي) على اليمين + مفتاح أوضاع العين (نهاري | ورقي | ليلي) على اليسار
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DockToolIcon(
                                        icon = Icons.Default.Draw,
                                        contentDescription = "قلم وأشكال",
                                        selected = activeTool in listOf(
                                            AnnotationTool.PEN,
                                            AnnotationTool.SHAPE_RECT,
                                            AnnotationTool.SHAPE_OVAL,
                                            AnnotationTool.SHAPE_ARROW,
                                            AnnotationTool.REDACT
                                        ),
                                        tintDefault = floatingTitleColor,
                                        onClick = {
                                            activeTool = if (activeTool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN
                                        },
                                        testTag = "reader_pen_btn"
                                    )
                                    DockToolIcon(
                                        icon = Icons.Default.AutoFixNormal,
                                        contentDescription = "ممحاة",
                                        selected = activeTool == AnnotationTool.ERASER,
                                        tintDefault = floatingTitleColor,
                                        onClick = {
                                            activeTool = if (activeTool == AnnotationTool.ERASER) AnnotationTool.NONE else AnnotationTool.ERASER
                                        },
                                        testTag = "reader_eraser_btn"
                                    )
                                    DockToolIcon(
                                        icon = Icons.Default.TextFields,
                                        contentDescription = "نص T أو ملاحظة لاصقة",
                                        selected = activeTool == AnnotationTool.TEXT_BOX || activeTool == AnnotationTool.TEXT_NOTE,
                                        tintDefault = floatingTitleColor,
                                        onClick = {
                                            activeTool = if (activeTool == AnnotationTool.TEXT_BOX || activeTool == AnnotationTool.TEXT_NOTE) {
                                                AnnotationTool.NONE
                                            } else {
                                                AnnotationTool.TEXT_NOTE
                                            }
                                        },
                                        testTag = "reader_text_btn"
                                    )
                                    DockToolIcon(
                                        icon = Icons.Default.Verified,
                                        contentDescription = "ختم دراسي",
                                        selected = activeTool == AnnotationTool.STAMP,
                                        tintDefault = floatingTitleColor,
                                        onClick = {
                                            activeTool = if (activeTool == AnnotationTool.STAMP) AnnotationTool.NONE else AnnotationTool.STAMP
                                        },
                                        testTag = "reader_stamp_btn"
                                    )
                                }

                                // مفتاح أوضاع العين الثلاثة (نهاري | ورقي | ليلي) بشكل كبسولي مطابق للشكل (2)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (eyeCareMode == PdfEyeCareMode.NIGHT) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (eyeCareMode == PdfEyeCareMode.NIGHT) Color(0xFF334155) else PdfStudioColors.cardBorder()
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        PdfEyeCareMode.entries.forEach { mode ->
                                            val isSel = eyeCareMode == mode
                                            Surface(
                                                onClick = { eyeCareMode = mode },
                                                shape = RoundedCornerShape(50),
                                                color = if (isSel) PdfStudioColors.RoyalBlue else Color.Transparent,
                                                modifier = Modifier.testTag("reader_eyecare_${mode.name}")
                                            ) {
                                                Text(
                                                    text = mode.labelAr,
                                                    style = PdfTypography.uiLabel(11.sp, FontWeight.Bold),
                                                    color = if (isSel) Color.White else floatingMutedColor,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // أسفل الشاشة: نص "صفحة X من Y" وتحته شريط مصغرات الصفحات الأفقي
                    Text(
                        text = "صفحة ${currentPage + 1} من $pageCount",
                        style = PdfTypography.uiLabel(13.sp, FontWeight.Medium),
                        color = floatingMutedColor,
                        textAlign = TextAlign.Center
                    )

                    if (showThumbnailStrip) {
                        val rf = renderFile
                        LazyRow(
                            state = thumbListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pageCount) { pIndex ->
                                val isCurrent = pIndex == currentPage
                                var thumb by remember(rf?.absolutePath, pIndex, rotationDegrees) {
                                    mutableStateOf<Bitmap?>(null)
                                }
                                LaunchedEffect(rf, pIndex, rotationDegrees) {
                                    if (rf != null) {
                                        val key = renderCache.buildKey(rf.absolutePath, pIndex, 160, rotationDegrees)
                                        thumb = renderCache.get(key) ?: PdfEngineHelper.renderPageToBitmap(rf, pIndex, 160, rotationDegrees)?.also {
                                            renderCache.put(key, it)
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = if (isCurrent) PdfStudioColors.RoyalBlue else PdfStudioColors.cardBorder(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { jumpToPage(pIndex) },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    val bmp = thumb
                                    if (bmp != null && !bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "صفحة ${pIndex + 1}",
                                            contentScale = ContentScale.Crop,
                                            colorFilter = pageColorFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PdfStudioColors.RoyalBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("جارٍ تجهيز صفحات المستند...", style = PdfTypography.uiBody(), color = floatingTitleColor)
                    }
                }
                loadError != null -> {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = loadError.orEmpty(),
                                style = PdfTypography.uiBody(),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onBack) { Text("عودة للمكتبة", style = PdfTypography.uiLabel()) }
                        }
                    }
                }
                renderFile != null && pageCount > 0 -> {
                    val activeFile = renderFile!!
                    val pinchPanModifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTool, viewMode) {
                            if (activeTool == AnnotationTool.NONE) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.2f) {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            zoomScale = 2.2f
                                        }
                                    }
                                )
                            }
                        }
                        .pointerInput(activeTool, zoomScale) {
                            if (activeTool == AnnotationTool.NONE) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.count { it.pressed }
                                        if (pointerCount >= 2 || zoomScale > 1.02f) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            if (abs(zoom - 1f) > 0.005f || zoomScale > 1.02f) {
                                                val newZoom = (zoomScale * zoom).coerceIn(1f, 5f)
                                                zoomScale = newZoom
                                                if (newZoom <= 1.02f) {
                                                    panOffset = Offset.Zero
                                                } else {
                                                    val maxPanX = size.width * (newZoom - 1f) * 0.55f
                                                    val maxPanY = size.height * (newZoom - 1f) * 0.55f
                                                    panOffset = Offset(
                                                        x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                        y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                    )
                                                }
                                                if (pointerCount >= 2) {
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        }

                    val targetDpiWidth = when {
                        zoomScale >= 3.0f -> 2200
                        zoomScale >= 1.7f -> 1650
                        else -> 1080
                    }

                    Box(
                        modifier = pinchPanModifier.graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                    ) {
                        if (viewMode == PdfReaderViewMode.CONTINUOUS) {
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = activeTool == AnnotationTool.NONE && zoomScale <= 1.05f,
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(pageCount, key = { it }) { pIdx ->
                                    PdfRenderedPageCard(
                                        file = activeFile,
                                        pageIndex = pIdx,
                                        targetWidthPx = targetDpiWidth,
                                        rotationDegrees = rotationDegrees,
                                        renderCache = renderCache,
                                        colorFilter = pageColorFilter,
                                        pageAnnotations = annotationState.pagesMap[pIdx] ?: PageAnnotations(),
                                        activeTool = activeTool,
                                        activeColorArgb = activeEffectiveColorArgb,
                                        activeWidthDp = activeWidthDp,
                                        activeStamp = activeStamp,
                                        onAddStroke = { annotationState.addStroke(pIdx, it) },
                                        onAddShape = { annotationState.addShape(pIdx, it) },
                                        onTapAddNote = { nx, ny ->
                                            pendingNotePage = pIdx
                                            pendingNoteCoords = nx to ny
                                        },
                                        onTapAddTextBox = { bx, by ->
                                            pendingTextBoxPage = pIdx
                                            pendingTextBoxCoords = bx to by
                                        },
                                        onTapNoteBubble = { note -> editingNote = pIdx to note },
                                        onTapAddStamp = { sx, sy ->
                                            annotationState.addStamp(
                                                pIdx,
                                                PdfStampItem(UUID.randomUUID().toString(), activeStamp, sx, sy)
                                            )
                                        },
                                        onEraseAt = { ex, ey -> annotationState.eraseNear(pIdx, ex, ey) }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PdfRenderedPageCard(
                                    file = activeFile,
                                    pageIndex = currentPage,
                                    targetWidthPx = targetDpiWidth,
                                    rotationDegrees = rotationDegrees,
                                    renderCache = renderCache,
                                    colorFilter = pageColorFilter,
                                    pageAnnotations = annotationState.pagesMap[currentPage] ?: PageAnnotations(),
                                    activeTool = activeTool,
                                    activeColorArgb = activeEffectiveColorArgb,
                                    activeWidthDp = activeWidthDp,
                                    activeStamp = activeStamp,
                                    onAddStroke = { annotationState.addStroke(currentPage, it) },
                                    onAddShape = { annotationState.addShape(currentPage, it) },
                                    onTapAddNote = { nx, ny ->
                                        pendingNotePage = currentPage
                                        pendingNoteCoords = nx to ny
                                    },
                                    onTapAddTextBox = { bx, by ->
                                        pendingTextBoxPage = currentPage
                                        pendingTextBoxCoords = bx to by
                                    },
                                    onTapNoteBubble = { note -> editingNote = currentPage to note },
                                    onTapAddStamp = { sx, sy ->
                                        annotationState.addStamp(
                                            currentPage,
                                            PdfStampItem(UUID.randomUUID().toString(), activeStamp, sx, sy)
                                        )
                                    },
                                    onEraseAt = { ex, ey -> annotationState.eraseNear(currentPage, ex, ey) }
                                )
                            }
                        }
                    }
                }
            }

            if (cleanReadingMode) {
                Surface(
                    onClick = { cleanReadingMode = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .testTag("reader_exit_clean_mode_btn"),
                    shape = CircleShape,
                    color = floatingCardBg.copy(alpha = 0.90f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "إظهار الأدوات", tint = floatingTitleColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إظهار الأدوات (ص ${currentPage + 1}/$pageCount)", style = PdfTypography.uiLabel(12.sp), color = floatingTitleColor)
                    }
                }
            }

            if (isExporting) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    color = floatingCardBg,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = PdfStudioColors.RoyalBlue)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("جارٍ معالجة وحفظ المستند...", style = PdfTypography.uiBody(), color = floatingTitleColor)
                    }
                }
            }
        }
    }

    if (requiresPassword) {
        AlertDialog(
            onDismissRequest = onBack,
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("ملف PDF محمي بكلمة مرور", style = PdfTypography.uiTitle(16.sp)) },
            text = {
                Column {
                    Text(
                        "أدخل كلمة المرور لفتح هذا المستند وعرض صفحاته:",
                        style = PdfTypography.uiBody(13.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("كلمة المرور", style = PdfTypography.uiLabel()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth().testTag("reader_password_input")
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = PdfTypography.uiLabel(12.sp),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val src = sourceFile ?: return@Button
                        scope.launch {
                            val res = PdfEngineHelper.prepareRenderableFile(context, src, passwordInput)
                            if (res.isSuccess) {
                                val rf = res.getOrThrow()
                                activePassword = passwordInput
                                renderFile = rf
                                pageCount = PdfEngineHelper.getPdfPageCount(rf)
                                bookmarks = storage.getBookmarks(uriString)
                                outlineNodes = PdfEngineHelper.loadPdfOutline(context, src, passwordInput)
                                requiresPassword = false
                            } else {
                                passwordError = "كلمة المرور غير صحيحة"
                            }
                        }
                    },
                    modifier = Modifier.testTag("reader_password_unlock_btn")
                ) {
                    Text("فتح الملف", style = PdfTypography.uiLabel())
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("إلغاء", style = PdfTypography.uiLabel()) }
            }
        )
    }

    // حوار إضافة ملاحظة لاصقة
    val addPage = pendingNotePage
    val addCoords = pendingNoteCoords
    if (addPage != null && addCoords != null) {
        TextNoteEditorDialog(
            title = "إضافة ملاحظة لاصقة",
            initialText = "",
            isEditingExisting = false,
            onDismiss = {
                pendingNotePage = null
                pendingNoteCoords = null
            },
            onConfirm = { text ->
                annotationState.addTextNote(
                    addPage,
                    PdfTextNote(
                        id = UUID.randomUUID().toString(),
                        x = addCoords.first,
                        y = addCoords.second,
                        text = text,
                        colorArgb = 0xFFFEF08AL
                    )
                )
                pendingNotePage = null
                pendingNoteCoords = null
            }
        )
    }

    // حوار إدراج مربع نص مباشر (T)
    val tbPage = pendingTextBoxPage
    val tbCoords = pendingTextBoxCoords
    if (tbPage != null && tbCoords != null) {
        TextNoteEditorDialog(
            title = "إدراج نص على الصفحة (T)",
            placeholder = "اكتب النص أو المعادلة لإضافتها على الصفحة...",
            initialText = "",
            isEditingExisting = false,
            onDismiss = {
                pendingTextBoxPage = null
                pendingTextBoxCoords = null
            },
            onConfirm = { text ->
                annotationState.addTextBox(
                    tbPage,
                    PdfTextBoxItem(
                        id = UUID.randomUUID().toString(),
                        x = tbCoords.first,
                        y = tbCoords.second,
                        text = text,
                        colorArgb = activeInkColorArgb
                    )
                )
                pendingTextBoxPage = null
                pendingTextBoxCoords = null
            }
        )
    }

    // تعديل/حذف ملاحظة موجودة
    val editTarget = editingNote
    if (editTarget != null) {
        TextNoteEditorDialog(
            initialText = editTarget.second.text,
            isEditingExisting = true,
            onDismiss = { editingNote = null },
            onConfirm = { newText ->
                annotationState.updateTextNote(editTarget.first, editTarget.second.id, newText)
                editingNote = null
            },
            onDelete = {
                annotationState.removeTextNote(editTarget.first, editTarget.second.id)
                editingNote = null
            }
        )
    }

    // نافذة العلامات المرجعية وفهرس الكتاب وتصدير وخيارات الملف
    if (showBookmarksSheet) {
        var sheetTab by remember { mutableIntStateOf(0) }
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("العلامات والفهرس وخيارات الملف", style = PdfTypography.uiTitle(17.sp))
                Spacer(modifier = Modifier.height(10.dp))

                TabRow(selectedTabIndex = sheetTab) {
                    Tab(
                        selected = sheetTab == 0,
                        onClick = { sheetTab = 0 },
                        text = { Text("العلامات (${bookmarks.size})", style = PdfTypography.uiLabel(12.sp)) }
                    )
                    Tab(
                        selected = sheetTab == 1,
                        onClick = { sheetTab = 1 },
                        text = { Text("الفهرس (${outlineNodes.size})", style = PdfTypography.uiLabel(12.sp)) }
                    )
                    Tab(
                        selected = sheetTab == 2,
                        onClick = { sheetTab = 2 },
                        text = { Text("خيارات وحفظ", style = PdfTypography.uiLabel(12.sp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (sheetTab) {
                    0 -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = bookmarkLabelInput,
                                onValueChange = { bookmarkLabelInput = it },
                                placeholder = { Text("عنوان للعلامة (ص ${currentPage + 1})", style = PdfTypography.uiBody(13.sp)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    bookmarks = storage.toggleBookmark(
                                        uriString,
                                        currentPage,
                                        bookmarkLabelInput.ifBlank { "مراجعة صفحة ${currentPage + 1}" }
                                    )
                                    bookmarkLabelInput = ""
                                }
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (bookmarks.any { it.pageIndex == currentPage }) "إزالة" else "إضافة",
                                    style = PdfTypography.uiLabel()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (bookmarks.isEmpty()) {
                            Text(
                                "لا توجد علامات مرجعية محفوظة لهذا الكتاب بعد.",
                                style = PdfTypography.uiBody(),
                                color = PdfStudioColors.subtitleText(),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(250.dp)) {
                                items(bookmarks, key = { it.id }) { bm ->
                                    Surface(
                                        onClick = {
                                            jumpToPage(bm.pageIndex)
                                            showBookmarksSheet = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = PdfStudioColors.RoyalBlue)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(bm.label, style = PdfTypography.uiBody())
                                            }
                                            Text("ص ${bm.pageIndex + 1}", style = PdfTypography.uiLabel(), color = PdfStudioColors.RoyalBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (outlineNodes.isEmpty()) {
                            Text(
                                "هذا الملف لا يحتوي على فهرس فصول مُضمّن (Outline). يمكنك استخدام العلامات المرجعية الشخصية.",
                                style = PdfTypography.uiBody(),
                                color = PdfStudioColors.subtitleText(),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(270.dp)) {
                                items(outlineNodes) { node ->
                                    Surface(
                                        onClick = {
                                            jumpToPage(node.pageIndex)
                                            showBookmarksSheet = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (node.level * 14).dp, top = 3.dp, bottom = 3.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = node.title,
                                                style = PdfTypography.uiBody(13.sp, if (node.level == 0) FontWeight.Bold else FontWeight.Normal),
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ص ${node.pageIndex + 1}",
                                                style = PdfTypography.uiLabel(12.sp),
                                                color = PdfStudioColors.RoyalBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetActionRow(
                                icon = if (viewMode == PdfReaderViewMode.CONTINUOUS) Icons.Default.ViewCarousel else Icons.Default.ViewAgenda,
                                label = if (viewMode == PdfReaderViewMode.CONTINUOUS) "التبديل لوضع صفحة بصفحة" else "التبديل للتمرير المتصل",
                                onClick = {
                                    viewMode = if (viewMode == PdfReaderViewMode.CONTINUOUS) PdfReaderViewMode.SINGLE_PAGE else PdfReaderViewMode.CONTINUOUS
                                    showBookmarksSheet = false
                                }
                            )
                            SheetActionRow(
                                icon = Icons.Default.Rotate90DegreesCw,
                                label = "تدوير العرض 90° (الحالي: $rotationDegrees°)",
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                }
                            )
                            SheetActionRow(
                                icon = Icons.Default.Visibility,
                                label = if (showThumbnailStrip) "إخفاء شريط المصغرات" else "إظهار شريط المصغرات",
                                onClick = {
                                    showThumbnailStrip = !showThumbnailStrip
                                    showBookmarksSheet = false
                                }
                            )
                            SheetActionRow(
                                icon = Icons.Default.Save,
                                label = "حفظ نسخة جديدة بالشروحات",
                                onClick = {
                                    showBookmarksSheet = false
                                    runExport(flatten = false, shareAfter = false)
                                }
                            )
                            SheetActionRow(
                                icon = Icons.Default.Layers,
                                label = "تسطيح ودمج الشروحات نهائياً (Flatten)",
                                onClick = {
                                    showBookmarksSheet = false
                                    runExport(flatten = true, shareAfter = false)
                                }
                            )
                            SheetActionRow(
                                icon = Icons.Default.Share,
                                label = "مشاركة الملف المشروح",
                                onClick = {
                                    showBookmarksSheet = false
                                    runExport(flatten = false, shareAfter = true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    tintDefault: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) PdfStudioColors.RoyalBlue.copy(alpha = 0.14f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, PdfStudioColors.RoyalBlue.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .size(36.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) PdfStudioColors.RoyalBlue else tintDefault,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SubOptionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) PdfStudioColors.RoyalBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) BorderStroke(1.dp, PdfStudioColors.RoyalBlue) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) PdfStudioColors.RoyalBlue else PdfStudioColors.subtitleText(),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = PdfTypography.uiLabel(11.sp),
                color = if (selected) PdfStudioColors.RoyalBlue else PdfStudioColors.titleText()
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PdfStudioColors.RoyalBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = PdfTypography.uiBody(14.sp, FontWeight.Medium))
        }
    }
}

@Composable
private fun PdfRenderedPageCard(
    file: File,
    pageIndex: Int,
    targetWidthPx: Int,
    rotationDegrees: Int,
    renderCache: PdfRenderCache,
    colorFilter: ColorFilter?,
    pageAnnotations: PageAnnotations,
    activeTool: AnnotationTool,
    activeColorArgb: Long,
    activeWidthDp: Float,
    activeStamp: StudyStampType,
    onAddStroke: (PdfStrokePath) -> Unit,
    onAddShape: (PdfShapeItem) -> Unit,
    onTapAddNote: (Float, Float) -> Unit,
    onTapAddTextBox: (Float, Float) -> Unit,
    onTapNoteBubble: (PdfTextNote) -> Unit,
    onTapAddStamp: (Float, Float) -> Unit,
    onEraseAt: (Float, Float) -> Unit
) {
    var pageBitmap by remember(file.absolutePath, pageIndex, targetWidthPx, rotationDegrees) {
        val key = renderCache.buildKey(file.absolutePath, pageIndex, targetWidthPx, rotationDegrees)
        mutableStateOf(renderCache.get(key))
    }

    LaunchedEffect(file.absolutePath, pageIndex, targetWidthPx, rotationDegrees) {
        val key = renderCache.buildKey(file.absolutePath, pageIndex, targetWidthPx, rotationDegrees)
        if (renderCache.get(key) == null) {
            val rendered = PdfEngineHelper.renderPageToBitmap(file, pageIndex, targetWidthPx, rotationDegrees)
            if (rendered != null) {
                renderCache.put(key, rendered)
                pageBitmap = rendered
            }
        }
    }

    val bmp = pageBitmap
    val aspectRatio = if (bmp != null && bmp.height > 0) {
        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.4f, 2.5f)
    } else {
        0.707f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "صفحة PDF رقم ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PdfStudioColors.RoyalBlue)
                }
            }

            PageAnnotationCanvasOverlay(
                pageIndex = pageIndex,
                annotations = pageAnnotations,
                activeTool = activeTool,
                activeColorArgb = activeColorArgb,
                activeWidthDp = activeWidthDp,
                activeStamp = activeStamp,
                onAddStroke = onAddStroke,
                onAddShape = onAddShape,
                onTapAddNote = onTapAddNote,
                onTapAddTextBox = onTapAddTextBox,
                onTapNoteBubble = onTapNoteBubble,
                onTapAddStamp = onTapAddStamp,
                onEraseAt = onEraseAt,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
