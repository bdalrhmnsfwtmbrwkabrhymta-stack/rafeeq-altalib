package com.example.studytools

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.dictionary.DictionaryAssetLoader
import com.example.studytools.dictionary.StudyDictionaryToolScreen
import com.example.studytools.files.StudyFileToolsScreen
import com.example.studytools.gpa.StudyGpaAndMathToolScreen
import com.example.studytools.inheritance.InheritanceCalculatorScreen
import com.example.studytools.irab.SmartIrabScreen
import com.example.studytools.scanner.StudyScannerToolScreen
import com.example.studytools.timer.StudyTimerCoordinator
import com.example.studytools.timer.StudyTimerToolScreen
import com.example.studytools.translator.StudyTranslatorToolScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.util.Locale

enum class StudyToolCategory(val labelAr: String) {
    ALL("الكل (٨)"),
    FOCUS("تركيز وإنتاجية"),
    LANGUAGE_SHARIA("لغة وشرع"),
    DOCUMENTS("ملفات وماسح"),
    SCIENCE_MATH("معدل ووحدات")
}

enum class StudyToolKey(
    val routeKey: String,
    val titleAr: String,
    val subtitleAr: String,
    val badgeAr: String,
    val category: StudyToolCategory,
    val accentColor: Color,
    val icon: ImageVector
) {
    TIMER(
        routeKey = "timer",
        titleAr = "مؤقت المذاكرة",
        subtitleAr = "بومودورو، عمل عميق، سبرينت، ماراثون وساعة إيقاف بإشعار خلفي مستمر",
        badgeAr = "إشعار خلفي",
        category = StudyToolCategory.FOCUS,
        accentColor = Color(0xFF059669),
        icon = Icons.Default.Timer
    ),
    INHERITANCE(
        routeKey = "inheritance",
        titleAr = "حاسبة المواريث",
        subtitleAr = "حساب الأنصبة الشرعية بدقة: الفروض، الحجب، التعصيب، العول، الرد والعُمرية",
        badgeAr = "بالشرع",
        category = StudyToolCategory.LANGUAGE_SHARIA,
        accentColor = Color(0xFFD97706),
        icon = Icons.Default.Balance
    ),
    IRAB(
        routeKey = "irab",
        titleAr = "الإعراب الذكي",
        subtitleAr = "إعراب الجمل العربية ملوناً (فعل/اسم/حرف) مع السبب والعلامة + تدريب تفاعلي",
        badgeAr = "إعراب + تدريب",
        category = StudyToolCategory.LANGUAGE_SHARIA,
        accentColor = Color(0xFF0F62FE),
        icon = Icons.Default.AutoAwesome
    ),
    DICTIONARY(
        routeKey = "dictionary",
        titleAr = "القاموس الشامل",
        subtitleAr = "آلاف الكلمات A-Z، الشائعة، المتشابهات، حروف الجر، الأفعال الشاذة ونطق صوتي",
        badgeAr = "بدون نت",
        category = StudyToolCategory.LANGUAGE_SHARIA,
        accentColor = Color(0xFF7C3AED),
        icon = Icons.AutoMirrored.Filled.MenuBook
    ),
    SCANNER(
        routeKey = "scanner",
        titleAr = "الماسح الضوئي الذكي",
        subtitleAr = "التقاط وتعديل الصفحات بالفلاتر، استخراج النصوص (OCR) وتصدير PDF متعدد الصفحات",
        badgeAr = "كاميرا + OCR",
        category = StudyToolCategory.DOCUMENTS,
        accentColor = Color(0xFF0284C7),
        icon = Icons.Default.DocumentScanner
    ),
    FILES(
        routeKey = "files",
        titleAr = "أدوات الملفات وPDF",
        subtitleAr = "دمج ملفات PDF، استخراج النص وحفظه كملاحظة، تقسيم الصفحات وتحويل الصور لـ PDF",
        badgeAr = "PDF احترافي",
        category = StudyToolCategory.DOCUMENTS,
        accentColor = Color(0xFFDC2626),
        icon = Icons.Default.PictureAsPdf
    ),
    TRANSLATOR(
        routeKey = "translator",
        titleAr = "المترجم الدراسي الفوري",
        subtitleAr = "ترجمة ثنائية إنجليزي ↔ عربي أوفلاين بالكلمات والفقرات مع نطق صوتي ونسخ سريع",
        badgeAr = "فوري ثنائي",
        category = StudyToolCategory.LANGUAGE_SHARIA,
        accentColor = Color(0xFF0D9488),
        icon = Icons.Default.Translate
    ),
    GPA_UNITS(
        routeKey = "gpa",
        titleAr = "حاسبة المعدل والوحدات",
        subtitleAr = "حساب المعدل الفصلي GPA والنسبة المئوية + محول وحدات الفيزياء والكيمياء والثوابت",
        badgeAr = "GPA + SI",
        category = StudyToolCategory.SCIENCE_MATH,
        accentColor = Color(0xFFEA580C),
        icon = Icons.Default.Calculate
    );

    companion object {
        fun fromKey(key: String?): StudyToolKey? {
            if (key.isNullOrBlank()) return null
            return entries.firstOrNull { it.routeKey.equals(key.trim(), ignoreCase = true) }
        }
    }
}

@Composable
fun StudyToolsHubScreen(
    viewModel: MainViewModel,
    initialToolKey: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val initialTool = remember(initialToolKey) { StudyToolKey.fromKey(initialToolKey) }
    var activeTool by remember(initialToolKey) { mutableStateOf(initialTool) }

    val handleToolBack: () -> Unit = {
        if (initialTool != null) {
            onNavigateBack()
        } else {
            activeTool = null
        }
    }

    BackHandler {
        if (activeTool != null && initialTool == null) {
            activeTool = null
        } else {
            onNavigateBack()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AnimatedContent(
            targetState = activeTool,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "studyToolHubTransition"
        ) { selectedTool ->
            when (selectedTool) {
                StudyToolKey.TIMER -> StudyTimerToolScreen(
                    viewModel = viewModel,
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.INHERITANCE -> InheritanceCalculatorScreen(
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.IRAB -> SmartIrabScreen(
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.DICTIONARY -> StudyDictionaryToolScreen(
                    viewModel = viewModel,
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.SCANNER -> StudyScannerToolScreen(
                    viewModel = viewModel,
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.FILES -> StudyFileToolsScreen(
                    viewModel = viewModel,
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.TRANSLATOR -> StudyTranslatorToolScreen(
                    onNavigateBack = handleToolBack
                )
                StudyToolKey.GPA_UNITS -> StudyGpaAndMathToolScreen(
                    onNavigateBack = handleToolBack
                )
                null -> StudyToolsHubDashboard(
                    context = context,
                    onSelectTool = { activeTool = it },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun StudyToolsHubDashboard(
    context: android.content.Context,
    onSelectTool: (StudyToolKey) -> Unit,
    onNavigateBack: () -> Unit
) {
    val timerSnapshot by StudyTimerCoordinator.snapshot.collectAsState()
    var selectedCategory by remember { mutableStateOf(StudyToolCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var dictionaryTotalCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching {
            DictionaryAssetLoader.loadBundle(context).totalItemCount
        }.onSuccess { count ->
            dictionaryTotalCount = count
        }
    }

    val filteredTools = remember(selectedCategory, searchQuery) {
        StudyToolKey.entries.filter { tool ->
            val matchesCategory = selectedCategory == StudyToolCategory.ALL || tool.category == selectedCategory
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                tool.titleAr.contains(q, ignoreCase = true) ||
                tool.subtitleAr.contains(q, ignoreCase = true) ||
                tool.badgeAr.contains(q, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    val isDark = MaterialTheme.colorScheme.background.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.45f
    }
    val heroNavy = Color(0xFF101B2D)
    val heroRoyalBlue = Color(0xFF1E3A8A)
    val goldAccent = Color(0xFFE6B34B)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val columns = if (maxWidth >= 600.dp) 2 else 1

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Dark Navy & Gold Brand Hero Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(heroRoyalBlue, heroNavy)
                                ),
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .testTag("study_tools_hub_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color.White
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (dictionaryTotalCount > 0) "أوفلاين • +$dictionaryTotalCount مادة لغوية" else "٨ أدوات متكاملة بدون إنترنت",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "أَدَوَاتُ المُذَاكَرَة",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = goldAccent
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "٨ أدوات بتساعدك وانت بتذاكر • سريعة وتعمل بدون نت",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 13.5.sp,
                                color = Color(0xFFCBD5E1)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "ابحث عن أداة (مواريث، إعراب، مؤقت، PDF...)",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = goldAccent
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "مسح البحث",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    focusedBorderColor = goldAccent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                    cursorColor = goldAccent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("study_tools_search_input")
                            )
                        }
                    }
                }

                // 2. Live Running Timer Resume Banner (if active)
                if (timerSnapshot.isRunning || timerSnapshot.elapsedSeconds > 0) {
                    item {
                        val displaySeconds = if (timerSnapshot.mode.isCountUp) {
                            timerSnapshot.elapsedSeconds
                        } else {
                            timerSnapshot.remainingSeconds
                        }
                        val formatted = String.format(
                            Locale.US,
                            "%02d:%02d",
                            displaySeconds / 60,
                            displaySeconds % 60
                        )
                        Surface(
                            onClick = { onSelectTool(StudyToolKey.TIMER) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("study_tools_active_timer_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF059669)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (timerSnapshot.isRunning) Icons.Default.Timer else Icons.Default.Pause,
                                        contentDescription = "المؤقت النشط",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (timerSnapshot.isRunning) "جلسة مذاكرة جارية الآن" else "جلسة مذاكرة متوقفة مؤقتاً",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = if (isDark) Color.White else Color(0xFF064E3B)
                                    )
                                    Text(
                                        text = "${timerSnapshot.subjectLabel} • ${timerSnapshot.mode.titleAr}",
                                        fontSize = 12.sp,
                                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF047857)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF059669)
                                ) {
                                    Text(
                                        text = formatted,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Category Filter Strip
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudyToolCategory.entries.forEach { category ->
                            val selected = selectedCategory == category
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category.labelAr,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0F62FE),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                }

                // 4. Adaptive 1 or 2 Column Tool Cards
                if (filteredTools.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد أداة مطابقة لـ \"$searchQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    val rows = filteredTools.chunked(columns)
                    items(rows.size) { rowIndex ->
                        val rowItems = rows[rowIndex]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { tool ->
                                StudyToolHubCard(
                                    tool = tool,
                                    onClick = { onSelectTool(tool) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyToolHubCard(
    tool: StudyToolKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, tool.accentColor.copy(alpha = 0.24f)),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .testTag("study_tool_card_${tool.routeKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tool.accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.titleAr,
                    tint = tool.accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tool.titleAr,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tool.accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tool.badgeAr,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = tool.accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = tool.subtitleAr,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "فتح ${tool.titleAr}",
                tint = tool.accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
