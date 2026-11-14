package com.example.ui.screens.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun StudyLibraryScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
    onOpenPdfUrl: ((title: String, url: String) -> Unit)? = null,
    headerExtraAction: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val systemDark = isSystemInDarkTheme()

    // Respect app dark mode by default, while allowing instant light/dark glass folder toggle
    val defaultDark = userSettings.isDarkMode ?: systemDark
    var isLibraryDarkMode by remember(defaultDark) { mutableStateOf(defaultDark) }

    // Smart Educational Navigation State
    var selectedStage by remember {
        mutableStateOf(
            when (userSettings.educationalStage) {
                "PRIMARY" -> SchoolStage.PRIMARY
                "MIDDLE" -> SchoolStage.PREPARATORY
                else -> SchoolStage.SECONDARY
            }
        )
    }

    val stageGrades = remember(selectedStage) {
        StudyLibraryCatalog.gradesForStage(selectedStage)
    }

    var selectedGradeId by remember(selectedStage) {
        mutableStateOf(stageGrades.firstOrNull()?.id ?: "s1")
    }
    LaunchedEffect(selectedStage) {
        if (stageGrades.none { it.id == selectedGradeId }) {
            selectedGradeId = stageGrades.first().id
        }
    }

    var selectedStudyType by remember { mutableStateOf(StudyType.ARABIC) }
    var selectedBacTrack by remember { mutableStateOf(BaccalaureateTrack.CORE) }
    var selectedSubject by remember { mutableStateOf<SubjectFolderSpec?>(null) }

    // Search & Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedTerm by remember { mutableStateOf("الكل") }
    var selectedBookType by remember { mutableStateOf("الكل") }

    val selectedGrade = remember(selectedGradeId) {
        StudyLibraryCatalog.grades.find { it.id == selectedGradeId } ?: StudyLibraryCatalog.grades.first()
    }

    val subjectFolders = remember(selectedGradeId, selectedStudyType, selectedBacTrack, searchQuery) {
        val all = StudyLibraryCatalog.getSubjectsFor(
            gradeId = selectedGradeId,
            studyType = selectedStudyType,
            bacTrack = selectedBacTrack
        )
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            all
        } else {
            all.filter {
                it.nameAr.contains(q, ignoreCase = true) ||
                    it.nameLanguages.contains(q, ignoreCase = true)
            }
        }
    }

    val visibleBooks = remember(
        selectedGradeId,
        selectedStudyType,
        selectedSubject,
        selectedBacTrack,
        selectedTerm,
        selectedBookType,
        searchQuery
    ) {
        StudyLibraryCatalog.filterBooks(
            gradeId = selectedGradeId,
            studyType = selectedStudyType,
            subjectId = selectedSubject?.id,
            bacTrack = if (selectedStage == SchoolStage.BACCALAUREATE) selectedBacTrack else null,
            term = selectedTerm,
            bookType = selectedBookType,
            query = searchQuery
        )
    }

    // Back handler when inside a Subject Folder
    BackHandler(enabled = selectedSubject != null) {
        selectedSubject = null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        StudyLibraryContainer(isDark = isLibraryDarkMode) {
            val palette = LocalStudyLibraryPalette.current

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnCount = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }
                val gridSpacing = if (maxWidth >= 600.dp) 20.dp else 16.dp

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Poster-Inspired Header with #FACC15 Yellow Highlight + Theme Toggle
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = onOpenDrawer,
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.surfaceCard.copy(alpha = 0.88f),
                                        border = BorderStroke(1.dp, palette.glassBorder),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "القائمة",
                                                tint = palette.inkPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "المكتبة",
                                            fontFamily = StudyLibraryTypography.DisplayFont,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            color = palette.inkPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        PosterYellowHighlight(
                                            text = "الدراسية",
                                            fontSize = 21.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Light / Dark Glass Mode Preview Switcher
                                    Surface(
                                        onClick = { isLibraryDarkMode = !isLibraryDarkMode },
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.surfaceCard.copy(alpha = 0.88f),
                                        border = BorderStroke(1.dp, palette.glassBorder),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .testTag("btn_library_theme_toggle")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isLibraryDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                contentDescription = "تبديل وضع الزجاج",
                                                tint = if (isLibraryDarkMode) StudyLibraryColors.AccentYellow else palette.inkPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (isLibraryDarkMode) "فاتح" else "داكن",
                                                fontFamily = StudyLibraryTypography.BodyFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = palette.inkPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "مناهج وزارة التربية والتعليم المصرية • من 1 ابتدائي حتى 3 ثانوي + البكالوريا المصرية",
                                fontFamily = StudyLibraryTypography.BodyFont,
                                fontSize = 11.5.sp,
                                color = palette.inkSecondary
                            )

                            if (headerExtraAction != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                headerExtraAction()
                            }
                        }
                    }

                    // 2. Stage & Grade Selector Bar (Smart Navigation Step 1)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Stage Tabs including Standalone Baccalaureate Entry
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(SchoolStage.entries) { stage ->
                                    val isSelected = stage == selectedStage
                                    val isBac = stage == SchoolStage.BACCALAUREATE
                                    Surface(
                                        onClick = {
                                            selectedStage = stage
                                            selectedSubject = null
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = when {
                                            isSelected -> StudyLibraryColors.AccentYellow
                                            isBac -> if (palette.isDark) Color(0xFF1E293B) else Color(0xFF111827)
                                            else -> palette.surfaceCard.copy(alpha = 0.85f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) StudyLibraryColors.AccentYellow else palette.glassBorder
                                        ),
                                        shadowElevation = if (isSelected) 4.dp else 1.dp,
                                        modifier = Modifier.testTag("stage_tab_${stage.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isBac) {
                                                Icon(
                                                    imageVector = Icons.Default.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = if (isSelected) StudyLibraryColors.AccentYellowInk else StudyLibraryColors.AccentYellow,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = stage.titleAr,
                                                fontFamily = StudyLibraryTypography.DisplayFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = when {
                                                    isSelected -> StudyLibraryColors.AccentYellowInk
                                                    isBac -> Color.White
                                                    else -> palette.inkPrimary
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Grades Strip (1 Primary -> 3 Secondary, or Baccalaureate)
                            if (stageGrades.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(stageGrades) { grade ->
                                        val isSelected = grade.id == selectedGradeId
                                        Surface(
                                            onClick = {
                                                selectedGradeId = grade.id
                                                selectedSubject = null
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) palette.badgeBg else palette.surfaceCard.copy(alpha = 0.78f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) palette.badgeBg else palette.glassBorder
                                            ),
                                            modifier = Modifier.testTag("grade_chip_${grade.id}")
                                        ) {
                                            Text(
                                                text = grade.labelAr,
                                                fontFamily = StudyLibraryTypography.BodyFont,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (isSelected) palette.badgeContent else palette.inkPrimary,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Study Type Question/Switch (نوع الدراسة: عربي | لغات) + Baccalaureate Track Bar
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = palette.surfaceCard.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, palette.glassBorder),
                            shadowElevation = 3.dp
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "نوع الدراسة:",
                                            fontFamily = StudyLibraryTypography.DisplayFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = palette.inkPrimary
                                        )
                                        Text(
                                            text = "اختر الشعبة لتحديث أسماء ومحتوى مجلدات المواد",
                                            fontFamily = StudyLibraryTypography.BodyFont,
                                            fontSize = 10.5.sp,
                                            color = palette.inkSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Segmented Toggle: عربي | لغات
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(palette.pillBadgeBg.copy(alpha = 0.65f))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        StudyType.entries.forEach { type ->
                                            val active = type == selectedStudyType
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (active) StudyLibraryColors.AccentYellow
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        selectedStudyType = type
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                                    .testTag("study_type_${type.id}")
                                            ) {
                                                Text(
                                                    text = if (type == StudyType.ARABIC) "عربي" else "لغات (Languages)",
                                                    fontFamily = StudyLibraryTypography.BodyFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    color = if (active) StudyLibraryColors.AccentYellowInk else palette.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Additional Baccalaureate Track Selector when Baccalaureate is active
                                AnimatedVisibility(visible = selectedStage == SchoolStage.BACCALAUREATE) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        HorizontalDivider(color = palette.glassBorder)
                                        Text(
                                            text = "اختر المسار الدراسي (البكالوريا المصرية):",
                                            fontFamily = StudyLibraryTypography.DisplayFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = palette.inkPrimary
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(BaccalaureateTrack.entries) { track ->
                                                val activeTrack = track == selectedBacTrack
                                                Surface(
                                                    onClick = {
                                                        selectedBacTrack = track
                                                        selectedSubject = null
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (activeTrack) palette.badgeBg else palette.pillBadgeBg.copy(alpha = 0.6f),
                                                    border = BorderStroke(1.dp, if (activeTrack) palette.badgeBg else palette.pillBadgeBorder)
                                                ) {
                                                    Text(
                                                        text = track.labelAr,
                                                        fontFamily = StudyLibraryTypography.BodyFont,
                                                        fontWeight = if (activeTrack) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.5.sp,
                                                        color = if (activeTrack) palette.badgeContent else palette.inkPrimary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Instant Arabic Search Bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (selectedSubject == null) "ابحث باسم المادة أو الكتاب..." else "ابحث داخل مادة ${selectedSubject?.nameAr}...",
                                    fontFamily = StudyLibraryTypography.BodyFont,
                                    fontSize = 13.sp,
                                    color = palette.inkSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "بحث",
                                    tint = palette.inkSecondary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = palette.inkSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = palette.surfaceCard.copy(alpha = 0.90f),
                                unfocusedContainerColor = palette.surfaceCard.copy(alpha = 0.80f),
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary,
                                focusedBorderColor = StudyLibraryColors.AccentYellow,
                                unfocusedBorderColor = palette.glassBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("study_library_search")
                        )
                    }

                    // 5. Either Subject Folders Grid OR Opened Subject Files View
                    if (selectedSubject == null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "مجلدات المواد — ${selectedGrade.labelAr}",
                                        fontFamily = StudyLibraryTypography.DisplayFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = palette.inkPrimary
                                    )
                                    Text(
                                        text = "اضغط على أي مجلد لفتح كتب الوزارة وملفات المادة",
                                        fontFamily = StudyLibraryTypography.BodyFont,
                                        fontSize = 11.sp,
                                        color = palette.inkSecondary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StudyLibraryColors.AccentYellow
                                ) {
                                    Text(
                                        text = "${subjectFolders.size} مواد",
                                        fontFamily = StudyLibraryTypography.DisplayFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = StudyLibraryColors.AccentYellowInk,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (subjectFolders.isEmpty()) {
                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    GlassEmptyStateCard(
                                        title = "لا توجد مواد مطابقة للبحث",
                                        subtitle = "جرّب البحث باسم مادة آخر، أو تصفح الكتب الرسمية من مكتبة الوزارة.",
                                        officialCatalogUrl = selectedGrade.officialCatalogUrl,
                                        isFilterActive = true,
                                        onOpenOfficialMinistryLibrary = { url -> openExternalUrl(context, url) },
                                        onOpenLocalDocuments = { onNavigate("documents") }
                                    )
                                }
                            }
                        } else {
                            val rows = subjectFolders.chunked(columnCount)
                            items(rows) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                                ) {
                                    for (subject in rowItems) {
                                        val count = StudyLibraryCatalog.filterBooks(
                                            gradeId = selectedGradeId,
                                            studyType = selectedStudyType,
                                            subjectId = subject.id,
                                            bacTrack = if (selectedStage == SchoolStage.BACCALAUREATE) selectedBacTrack else null
                                        ).size
                                        GlassFolderCard(
                                            subject = subject,
                                            studyType = selectedStudyType,
                                            bookCount = count,
                                            onClick = { selectedSubject = subject },
                                            modifier = Modifier
                                                .weight(1f)
                                                .widthIn(min = 155.dp)
                                        )
                                    }
                                    repeat(columnCount - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Opened Subject Detail View (Breadcrumb + Filters + Books / Honest Empty State)
                        val activeSubject = selectedSubject!!
                        val subjectTitle = if (selectedStudyType == StudyType.LANGUAGES) {
                            "${activeSubject.nameLanguages} (${activeSubject.nameAr})"
                        } else {
                            activeSubject.nameAr
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Breadcrumb Back Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { selectedSubject = null },
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.badgeBg,
                                        modifier = Modifier.testTag("btn_back_to_folders")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "رجوع",
                                                tint = palette.badgeContent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "العودة للمجلدات",
                                                fontFamily = StudyLibraryTypography.BodyFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = palette.badgeContent
                                            )
                                        }
                                    }

                                    PosterYellowHighlight(
                                        text = activeSubject.nameAr,
                                        fontSize = 16.sp
                                    )
                                }

                                Text(
                                    text = "$subjectTitle • ${selectedGrade.labelAr} (${selectedStudyType.labelAr})",
                                    fontFamily = StudyLibraryTypography.BodyFont,
                                    fontSize = 12.sp,
                                    color = palette.inkSecondary
                                )

                                // Term Filters
                                val terms = listOf("الكل", "الترم الأول", "الترم الثاني")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(terms) { termItem ->
                                        val isActive = selectedTerm == termItem
                                        Surface(
                                            onClick = { selectedTerm = termItem },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isActive) StudyLibraryColors.AccentYellow else palette.surfaceCard.copy(alpha = 0.85f),
                                            border = BorderStroke(1.dp, if (isActive) StudyLibraryColors.AccentYellow else palette.glassBorder)
                                        ) {
                                            Text(
                                                text = termItem,
                                                fontFamily = StudyLibraryTypography.BodyFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = if (isActive) StudyLibraryColors.AccentYellowInk else palette.inkPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                // Book Type Filters
                                val bookTypes = listOf("الكل", "كتاب الوزارة", "ملخص", "مراجعة", "دليل معلم")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(bookTypes) { bType ->
                                        val isActive = selectedBookType == bType
                                        Surface(
                                            onClick = { selectedBookType = bType },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isActive) palette.badgeBg else palette.surfaceCard.copy(alpha = 0.80f),
                                            border = BorderStroke(1.dp, if (isActive) palette.badgeBg else palette.glassBorder)
                                        ) {
                                            Text(
                                                text = bType,
                                                fontFamily = StudyLibraryTypography.BodyFont,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp,
                                                color = if (isActive) palette.badgeContent else palette.inkPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (visibleBooks.isEmpty()) {
                            item {
                                val hasFilter = searchQuery.isNotBlank() || selectedTerm != "الكل" || selectedBookType != "الكل"
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    GlassEmptyStateCard(
                                        title = if (hasFilter) "لا توجد ملفات مطابقة للفلتر" else "الكتب الرسمية لمادة ${activeSubject.nameAr} قيد الربط",
                                        subtitle = if (hasFilter) {
                                            "جرّب تغيير الفصل الدراسي أو نوع الكتاب أو كلمة البحث."
                                        } else {
                                            "نلتزم بعرض روابط PDF الرسمية الموثقة فقط دون اختلاق أي روابط أو أغلفة وهمية. يمكنك فتح مكتبة وزارة التربية والتعليم المصرية للمرحلة مباشرةً أو استعراض ملفاتك المحلية."
                                        },
                                        officialCatalogUrl = selectedGrade.officialCatalogUrl,
                                        isFilterActive = hasFilter,
                                        onOpenOfficialMinistryLibrary = { url -> openExternalUrl(context, url) },
                                        onOpenLocalDocuments = { onNavigate("documents") }
                                    )
                                }
                            }
                        } else {
                            items(visibleBooks, key = { it.id }) { book ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    GlassBookCard(
                                        book = book,
                                        gradeLabel = selectedGrade.labelAr,
                                        onOpenPdf = {
                                            if (onOpenPdfUrl != null) {
                                                onOpenPdfUrl(book.title, book.officialPdfUrl)
                                            } else {
                                                openExternalUrl(context, book.officialPdfUrl)
                                            }
                                        },
                                        onOpenSource = {
                                            openExternalUrl(context, book.officialPageUrl ?: book.officialPdfUrl)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 6. Official Egyptian Ministry of Education Attribution Banner
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = palette.surfaceCard.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, palette.glassBorder)
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
                                        text = "المصدر الرسمي: وزارة التربية والتعليم المصرية 🇪🇬",
                                        fontFamily = StudyLibraryTypography.DisplayFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = palette.inkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "جميع الكتب الوزارية تُفتح مباشرة من البوابة التعليمية الرسمية دون إعادة رفع.",
                                        fontFamily = StudyLibraryTypography.BodyFont,
                                        fontSize = 11.sp,
                                        color = palette.inkSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = { openExternalUrl(context, StudyLibraryCatalog.MOE_MAIN_BOOKS_URL) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = StudyLibraryColors.AccentYellow
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "مكتبة الوزارة",
                                            tint = StudyLibraryColors.AccentYellowInk,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "بوابة الوزارة",
                                            fontFamily = StudyLibraryTypography.BodyFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = StudyLibraryColors.AccentYellowInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر فتح الرابط: $url", Toast.LENGTH_SHORT).show()
    }
}
