package com.example.pdfstudio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

private data class HubToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val category: PdfHubTab,
    val onClick: () -> Unit
)

@Composable
fun PdfStudioHubScreen(
    viewModel: MainViewModel,
    initialPdfUri: String? = null,
    initialPdfTitle: String? = null,
    onNavigateBack: () -> Unit,
    onClearIncomingPdf: () -> Unit = {}
) {
    val context = LocalContext.current
    val storage = remember { PdfStudioStorage(context) }

    var activeRoute by remember {
        mutableStateOf<PdfStudioScreenRoute>(
            if (!initialPdfUri.isNullOrBlank()) {
                PdfStudioScreenRoute.Reader(
                    uriString = initialPdfUri,
                    title = initialPdfTitle?.ifBlank { "مستند PDF" } ?: "مستند PDF"
                )
            } else {
                PdfStudioScreenRoute.Hub
            }
        )
    }

    LaunchedEffect(initialPdfUri) {
        if (!initialPdfUri.isNullOrBlank()) {
            activeRoute = PdfStudioScreenRoute.Reader(
                uriString = initialPdfUri,
                title = initialPdfTitle?.ifBlank { "مستند PDF" } ?: "مستند PDF"
            )
            onClearIncomingPdf()
        }
    }

    var selectedTab by remember { mutableStateOf(PdfHubTab.ALL) }
    var showInstantSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var recentPdfs by remember { mutableStateOf(storage.getRecentPdfs()) }
    val savedDocuments by viewModel.documents.collectAsState()

    fun refreshRecents() {
        recentPdfs = storage.getRecentPdfs()
    }

    LaunchedEffect(activeRoute) {
        if (activeRoute is PdfStudioScreenRoute.Hub) {
            refreshRecents()
        }
    }

    fun openUriInReader(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val title = PdfEngineHelper.queryDisplayName(context, uri)
        val uriStr = uri.toString()
        val lastPage = storage.getLastPage(uriStr)
        activeRoute = PdfStudioScreenRoute.Reader(
            uriString = uriStr,
            title = title,
            initialPage = lastPage
        )
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            openUriInReader(uri)
        }
    }

    when (val route = activeRoute) {
        is PdfStudioScreenRoute.Reader -> {
            PdfReaderScreen(
                uriString = route.uriString,
                documentTitle = route.title,
                initialPage = route.initialPage,
                onBack = {
                    onClearIncomingPdf()
                    activeRoute = PdfStudioScreenRoute.Hub
                }
            )
            return
        }
        is PdfStudioScreenRoute.Compress -> {
            PdfCompressionOrganizeScreen(
                initialUriString = route.initialUriString,
                onBack = { activeRoute = PdfStudioScreenRoute.Hub }
            )
            return
        }
        is PdfStudioScreenRoute.OrganizeMergeSplit -> {
            PdfOrganizeMergeSplitScreen(
                initialTab = route.initialTab,
                onOpenInReader = { uriStr, title ->
                    activeRoute = PdfStudioScreenRoute.Reader(uriStr, title)
                },
                onBack = { activeRoute = PdfStudioScreenRoute.Hub }
            )
            return
        }
        is PdfStudioScreenRoute.ConvertOcrSecurity -> {
            PdfConvertOcrScreen(
                initialTab = route.initialTab,
                onOpenInReader = { uriStr, title ->
                    activeRoute = PdfStudioScreenRoute.Reader(uriStr, title)
                },
                onSaveExtractedNote = { title, text ->
                    viewModel.saveSummaryToDocuments(title = title, summary = text)
                },
                onBack = { activeRoute = PdfStudioScreenRoute.Hub }
            )
            return
        }
        PdfStudioScreenRoute.Hub -> {
            BackHandler { onNavigateBack() }
        }
    }

    // البطاقات الست الرئيسية بنفس الترتيب الحرفي من اليمين لليسار كما في الشكل (1)
    val allTools = remember(openPdfLauncher) {
        listOf(
            // الصف الأول: يمين (قارئ الكتب الذكي - بنفسجي) | يسار (ضغط وتصغير الحجم - أخضر نعناعي)
            HubToolItem(
                id = "reader",
                title = "قارئ الكتب الذكي",
                subtitle = "عرض مريح وتظليل",
                icon = Icons.Default.MenuBook,
                accent = PdfStudioColors.AccentPurple,
                category = PdfHubTab.READ_EDIT,
                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
            ),
            HubToolItem(
                id = "compress",
                title = "ضغط وتصغير الحجم",
                subtitle = "تقليل المساحة بنفس الجودة",
                icon = Icons.Default.Compress,
                accent = PdfStudioColors.AccentMint,
                category = PdfHubTab.COMPRESS,
                onClick = { activeRoute = PdfStudioScreenRoute.Compress() }
            ),
            // الصف الثاني: يمين (دمج وتقسيم الصفحات - برتقالي/كهرماني) | يسار (استخراج النص OCR - أزرق سماوي)
            HubToolItem(
                id = "organize",
                title = "دمج وتقسيم الصفحات",
                subtitle = "ترتيب وحذف وتدوير",
                icon = Icons.Default.DashboardCustomize,
                accent = PdfStudioColors.AccentOrange,
                category = PdfHubTab.ORGANIZE,
                onClick = { activeRoute = PdfStudioScreenRoute.OrganizeMergeSplit(OrganizeModeTab.ORGANIZE) }
            ),
            HubToolItem(
                id = "ocr",
                title = "استخراج النص OCR",
                subtitle = "قراءة الكتب المصورة بالعربية",
                icon = Icons.Default.DocumentScanner,
                accent = PdfStudioColors.AccentCyan,
                category = PdfHubTab.READ_EDIT,
                onClick = { activeRoute = PdfStudioScreenRoute.ConvertOcrSecurity(ConvertSecurityTab.OCR_TEXT) }
            ),
            // الصف الثالث: يمين (صور إلى PDF - وردي) | يسار (حماية وعلامة مائية - فيروزي)
            HubToolItem(
                id = "img2pdf",
                title = "صور إلى PDF",
                subtitle = "تحويل الصور لملف واحد",
                icon = Icons.Default.PhotoLibrary,
                accent = PdfStudioColors.AccentPink,
                category = PdfHubTab.ORGANIZE,
                onClick = { activeRoute = PdfStudioScreenRoute.ConvertOcrSecurity(ConvertSecurityTab.IMAGES_TO_PDF) }
            ),
            HubToolItem(
                id = "security",
                title = "حماية وعلامة مائية",
                subtitle = "تشفير وحفظ الحقوق",
                icon = Icons.Default.Lock,
                accent = PdfStudioColors.AccentTeal,
                category = PdfHubTab.COMPRESS,
                onClick = { activeRoute = PdfStudioScreenRoute.ConvertOcrSecurity(ConvertSecurityTab.SECURITY_META) }
            ),
            // أدوات مساندة تظهر عند التصفية أو البحث الفوري
            HubToolItem(
                id = "pdf2img",
                title = "PDF إلى صور وطباعة",
                subtitle = "JPG وPNG وأبيض وأسود",
                icon = Icons.Default.Image,
                accent = PdfStudioColors.AccentIndigo,
                category = PdfHubTab.ORGANIZE,
                onClick = { activeRoute = PdfStudioScreenRoute.ConvertOcrSecurity(ConvertSecurityTab.PDF_TO_IMAGES) }
            ),
            HubToolItem(
                id = "split",
                title = "تقسيم لنطاقات",
                subtitle = "استخراج فصول أو كل N صفحة",
                icon = Icons.Default.CallSplit,
                accent = PdfStudioColors.RoyalBlue,
                category = PdfHubTab.ORGANIZE,
                onClick = { activeRoute = PdfStudioScreenRoute.OrganizeMergeSplit(OrganizeModeTab.SPLIT) }
            )
        )
    }

    val visibleTools = remember(selectedTab, searchQuery) {
        val base = when (selectedTab) {
            PdfHubTab.ALL -> allTools.take(6)
            else -> allTools.filter { it.category == selectedTab }
        }
        if (searchQuery.isBlank()) base
        else allTools.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredRecents = remember(recentPdfs, searchQuery) {
        if (searchQuery.isBlank()) recentPdfs
        else recentPdfs.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val savedPdfEntities = remember(savedDocuments, searchQuery) {
        savedDocuments.filter {
            it.fileType.equals("PDF", ignoreCase = true) &&
                it.uriString.isNotBlank() &&
                (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        containerColor = PdfStudioColors.screenBackground(),
        topBar = {
            Column {
                PdfStudioTopBar(
                    title = "استوديو PDF الشامل",
                    subtitle = "كل أدوات الكتب والملفات في مكان واحد",
                    onBack = onNavigateBack,
                    testTagBack = "pdf_hub_back_btn",
                    transparentBackground = true,
                    actions = {
                        IconButton(
                            onClick = {
                                showInstantSearch = !showInstantSearch
                                if (!showInstantSearch) searchQuery = ""
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hub_search_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (showInstantSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "بحث فوري",
                                tint = if (showInstantSearch) PdfStudioColors.RoyalBlue else PdfStudioColors.titleText()
                            )
                        }
                    }
                )
                AnimatedVisibility(visible = showInstantSearch) {
                    Surface(
                        color = PdfStudioColors.cardSurface(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ابحث باسم المذكرة أو الأداة...", style = PdfTypography.uiBody(13.sp)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("hub_search_input")
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. بطاقة الاستيراد الزرقاء ثلاثية الأبعاد (Stacked 3D Card)
            item {
                BlueStackedImportCard(
                    onImportClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
                )
            }

            // 2. شريط التصنيف الأفقي (LazyRow): الكل | القراءة والتحرير | الضغط والحجم | التنظيم والصفحات
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PdfHubTab.entries) { tab ->
                        val isSel = tab == selectedTab
                        Surface(
                            onClick = { selectedTab = tab },
                            modifier = Modifier.testTag("hub_tab_${tab.name}"),
                            shape = CircleShape,
                            color = if (isSel) PdfStudioColors.RoyalBlue else PdfStudioColors.cardSurface(),
                            border = if (isSel) null else BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                            shadowElevation = if (isSel) 3.dp else 0.dp
                        ) {
                            Text(
                                text = tab.titleAr,
                                style = PdfTypography.uiLabel(13.sp, FontWeight.Bold),
                                color = if (isSel) Color.White else PdfStudioColors.titleText(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // 3. شبكة البطاقات الست (2 في كل صف — بنفس الترتيب الحرفي من اليمين لليسار)
            item {
                val rows = visibleTools.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { tool ->
                                HubGridToolCard(
                                    tool = tool,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 4. قسم «الملفات المفتوحة مؤخراً» في الأسفل مطابق للصورة (1)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(PdfStudioColors.AccentPink, PdfStudioColors.AccentCyan, PdfStudioColors.AccentMint)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الملفات المفتوحة مؤخراً",
                        style = PdfTypography.uiTitle(18.sp, FontWeight.Bold),
                        color = PdfStudioColors.titleText()
                    )
                }
            }

            if (filteredRecents.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PdfStudioColors.cardSurface(),
                        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "لا توجد ملفات مفتوحة مؤخراً بعد",
                                style = PdfTypography.uiTitle(14.sp),
                                color = PdfStudioColors.titleText()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اضغط على «استيراد ملف من الجهاز» لبدء المذاكرة وحفظ صفحتك تلقائياً",
                                style = PdfTypography.uiBody(12.sp),
                                color = PdfStudioColors.subtitleText()
                            )
                        }
                    }
                }
            } else {
                items(filteredRecents, key = { it.uriString }) { rec ->
                    var showItemMenu by remember { mutableStateOf(false) }
                    Surface(
                        onClick = {
                            activeRoute = PdfStudioScreenRoute.Reader(
                                uriString = rec.uriString,
                                title = rec.title,
                                initialPage = rec.lastPageIndex
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = PdfStudioColors.cardSurface(),
                        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "PDF",
                                        style = PdfTypography.uiLabel(11.sp, FontWeight.Bold),
                                        color = PdfStudioColors.subtitleText()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    style = PdfTypography.uiTitle(15.sp, FontWeight.Bold),
                                    color = PdfStudioColors.titleText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "صفحة ${rec.lastPageIndex + 1} من ${rec.pageCount}",
                                    style = PdfTypography.uiLabel(12.sp, FontWeight.Normal),
                                    color = PdfStudioColors.subtitleText()
                                )
                            }
                            Box {
                                IconButton(onClick = { showItemMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "خيارات الملف",
                                        tint = PdfStudioColors.subtitleText()
                                    )
                                }
                                DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("ضغط وتقليص الحجم", style = PdfTypography.uiBody(13.sp)) },
                                        onClick = {
                                            showItemMenu = false
                                            activeRoute = PdfStudioScreenRoute.Compress(rec.uriString)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("إزالة من القائمة", style = PdfTypography.uiBody(13.sp)) },
                                        onClick = {
                                            showItemMenu = false
                                            storage.removeRecentPdf(rec.uriString)
                                            refreshRecents()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (savedPdfEntities.isNotEmpty()) {
                item {
                    Text(
                        text = "مستندات المكتبة المحفوظة",
                        style = PdfTypography.uiTitle(16.sp),
                        color = PdfStudioColors.titleText()
                    )
                }
                items(savedPdfEntities, key = { it.id }) { doc ->
                    Surface(
                        onClick = {
                            val lastPage = storage.getLastPage(doc.uriString)
                            activeRoute = PdfStudioScreenRoute.Reader(
                                uriString = doc.uriString,
                                title = doc.title,
                                initialPage = lastPage
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = PdfStudioColors.cardSurface(),
                        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = PdfStudioColors.RoyalBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title, style = PdfTypography.uiTitle(14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.pageCount} صفحة", style = PdfTypography.uiLabel(12.sp), color = PdfStudioColors.subtitleText())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubGridToolCard(
    tool: HubToolItem,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = tool.onClick,
        modifier = modifier.testTag("hub_tool_${tool.id}"),
        shape = RoundedCornerShape(20.dp),
        color = PdfStudioColors.cardSurface(),
        border = BorderStroke(1.dp, PdfStudioColors.cardBorder()),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tool.accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = tool.accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = PdfTypography.uiTitle(14.5.sp, FontWeight.Bold),
                        color = PdfStudioColors.titleText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tool.subtitle,
                        style = PdfTypography.uiBody(11.sp),
                        color = PdfStudioColors.subtitleText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = CircleShape,
                    color = tool.accent.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = tool.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
