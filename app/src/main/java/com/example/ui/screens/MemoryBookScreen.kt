package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DocumentEntity
import com.example.domain.ai.GeneratedFlashcard
import com.example.domain.book.BookJsonSerializer
import com.example.domain.book.MemoryBookChapter
import com.example.domain.book.MemoryBookData
import com.example.domain.book.MemoryBookItem
import com.example.ui.MainViewModel

// ==========================================
// Static Repository for "كتاب كيف تتذكر كل ما تذاكره"
// ==========================================
object MemoryBookRepository {
    val bookData = com.example.domain.book.HowToRememberBook.fullBookData
}

// ==========================================
// Composable UI for Memory Book Screen
// ==========================================
@Composable
fun MemoryBookScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val selectedDocId by viewModel.selectedBookDocumentId.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var showAddBookDialog by remember { mutableStateOf(false) }
    var newBookTitle by remember { mutableStateOf("") }
    var newBookContent by remember { mutableStateOf("") }

    // Resolve Active Document and Active Book Data
    val activeDoc = remember(selectedDocId, documents) {
        documents.find { it.id == selectedDocId }
    }

    val activeBookData: MemoryBookData = remember(activeDoc) {
        if (activeDoc == null) {
            MemoryBookRepository.bookData
        } else {
            val parsed = BookJsonSerializer.deserializeBookData(activeDoc.processedJson)
            parsed ?: MemoryBookData(
                bookTitle = activeDoc.title,
                introTitle = "مقدمة الكتاب",
                introBody = "هذا المستند لم يُعالج بالكامل بعد. اضغط زر المعالجة بالذكاء الاصطناعي بالأسفل.",
                chapters = emptyList(),
                conclusionTitle = "الخاتمة",
                conclusionBody = "يرجى معالجة الكتاب عبر الذكاء الاصطناعي لبناء الفصول والبطاقات."
            )
        }
    }

    // Extracted Points for active book
    val extractedPoints = remember(activeDoc, activeBookData) {
        if (activeDoc != null && activeDoc.importantPointsJson.isNotBlank()) {
            BookJsonSerializer.deserializePointsList(activeDoc.importantPointsJson)
        } else {
            activeBookData.chapters.flatMap { ch ->
                ch.items.map { "📌 ${it.name}: ${it.mechanism}" }
            }
        }
    }

    // Flashcards for active book
    val activeFlashcards = remember(activeDoc, activeBookData) {
        activeBookData.chapters.flatMap { ch ->
            ch.items.map { item ->
                GeneratedFlashcard(
                    question = "ما هي آلية وتأثير (${item.name})؟",
                    answer = "${item.mechanism}\nالفائدة: ${item.benefit}"
                )
            }
        }.ifEmpty {
            listOf(
                GeneratedFlashcard(
                    question = "ما الفكرة الأساسية في هذا الكتاب؟",
                    answer = activeBookData.introBody.take(200)
                )
            )
        }
    }

    var selectedTab by remember { mutableStateOf(1) } // 0: Intro, 1: Chapters, 2: Important Points, 3: Flashcards, 4: AI Tutor, 5: Conclusion, 6: AI Linking, 7: PDF Pages Reader
    var searchQuery by remember { mutableStateOf("") }
    var expandedChapterId by remember { mutableStateOf<String?>("ch1") }
    var favoriteTechniques by remember { mutableStateOf(setOf<String>()) }
    var completedChapters by remember { mutableStateOf(setOf<String>()) }

    val filteredChapters = remember(searchQuery, activeBookData) {
        if (searchQuery.isBlank()) activeBookData.chapters
        else {
            activeBookData.chapters.mapNotNull { ch ->
                val matchTitle = ch.title.contains(searchQuery, ignoreCase = true)
                val matchIntro = ch.intro.contains(searchQuery, ignoreCase = true)
                val matchingItems = ch.items.filter { item ->
                    item.name.contains(searchQuery, ignoreCase = true) ||
                            item.mechanism.contains(searchQuery, ignoreCase = true) ||
                            item.benefit.contains(searchQuery, ignoreCase = true)
                }
                if (matchTitle || matchIntro || matchingItems.isNotEmpty()) {
                    ch.copy(items = if (matchingItems.isNotEmpty()) matchingItems else ch.items)
                } else null
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 1. Book Selector Horizontal Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "مكتبتك الدراسية والمستندات المعالجة 📚",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Default Memory Book Chip
                    item {
                        FilterChip(
                            selected = selectedDocId == null,
                            onClick = { viewModel.selectBookDocument(null) },
                            label = { Text("📖 كتاب طرق الحفظ (الأصلي)") },
                            leadingIcon = {
                                Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    // User Processed Books / Documents Chips
                    items(documents) { doc ->
                        val isSelected = selectedDocId == doc.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectBookDocument(doc.id) },
                            label = { Text(doc.title.take(24)) },
                            leadingIcon = {
                                Icon(
                                    if (doc.processedJson.isNotBlank()) Icons.Default.AutoAwesome else Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (doc.processedJson.isNotBlank()) Color(0xFFFFB800) else MaterialTheme.colorScheme.outline
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            )
                        )
                    }

                    // Add New Book Button Chip
                    item {
                        Button(
                            onClick = { showAddBookDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة كتاب جديد 🤖", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }

        // 2. Hero Active Book Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.AutoStories,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeBookData.bookTitle,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "مُنظّم الفصول والتقنيات بالذكاء الاصطناعي 🤖",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "${activeBookData.chapters.size} فصول",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // If active document exists but has not been AI processed yet
                    if (activeDoc != null && activeDoc.processedJson.isBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "لم يتم معالجة هذا المستند بالذكاء الاصطناعي حتى الآن.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.processDocumentWithAi(activeDoc.id)
                                    },
                                    enabled = !isAiLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isAiLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري معالجة الفصل والبطاقات...")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("معالجة وتحويل لكتاب فصول وبطاقات ذكية 🤖")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Search Box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث عن نقطة أو مفهوم في الكتاب...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        // 3. Navigation Tabs Row
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("المقدمة 📜", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("الفصول (${activeBookData.chapters.size}) 📚", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("النقاط المهمة 📌", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("بطاقات ذكية 🎴", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("المساعد الذكي 🤖", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 },
                    text = { Text("الربط بالذكاء الاصطناعي 🔗", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 7,
                    onClick = { selectedTab = 7 },
                    text = { Text("عرض الـ PDF بالصفحات (43) 📑", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("الخاتمة 🏁", fontWeight = FontWeight.Bold) }
                )
            }
        }

        // 4. Tab Contents
        when (selectedTab) {
            // ==================== TAB 0: INTRO ====================
            0 -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeBookData.introTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = activeBookData.introBody,
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("قراءة فصول الكتاب منظمًا")
                            }
                        }
                    }
                }
            }

            // ==================== TAB 1: CHAPTERS ====================
            1 -> {
                if (filteredChapters.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد فصول متاحة حالياً لـ \"$searchQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredChapters) { chapter ->
                        val isExpanded = expandedChapterId == chapter.id || searchQuery.isNotBlank()
                        val isCompleted = completedChapters.contains(chapter.id)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCompleted)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedChapterId = if (expandedChapterId == chapter.id) null else chapter.id
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                completedChapters = if (isCompleted) {
                                                    completedChapters - chapter.id
                                                } else {
                                                    completedChapters + chapter.id
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                                contentDescription = "إكمال الفصل",
                                                tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        Text(
                                            text = chapter.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "توسيع/طي",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = chapter.intro,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Technique Items
                                        chapter.items.forEachIndexed { index, item ->
                                            val isFav = favoriteTechniques.contains(item.name)

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}. ${item.name}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )

                                                        IconButton(
                                                            onClick = {
                                                                favoriteTechniques = if (isFav) favoriteTechniques - item.name else favoriteTechniques + item.name
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                                contentDescription = "المفضلة",
                                                                tint = if (isFav) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = "⚙️ الآلية: ${item.mechanism}",
                                                        fontSize = 13.sp,
                                                        lineHeight = 19.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = "💡 الفائدة: ${item.benefit}",
                                                        fontSize = 12.sp,
                                                        lineHeight = 18.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    if (!item.example.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = "📝 مثال تطبيقي: ${item.example}",
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Chapter Summary Box
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "🏁 خلاصة الفصل:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = chapter.summary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
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

            // ==================== TAB 2: IMPORTANT POINTS ====================
            2 -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "النقاط المهمة والمركّزة 📌",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(onClick = {
                                    val allPointsText = extractedPoints.joinToString("\n")
                                    viewModel.copyTextToClipboard("النقاط المهمة - ${activeBookData.bookTitle}", allPointsText)
                                    Toast.makeText(context, "تم نسخ كل النقاط المهمة للحافظة 📋", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الكل")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (extractedPoints.isEmpty()) {
                                Text("لا توجد نقاط مهمة مستخرجة بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                extractedPoints.forEachIndexed { idx, point ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("${idx + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(point, fontSize = 13.sp, lineHeight = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== TAB 3: SMART FLASHCARDS REVIEW ====================
            3 -> {
                item {
                    SmartCardsReviewMode(
                        flashcards = activeFlashcards,
                        bookTitle = activeBookData.bookTitle,
                        onOpenAnki = { onNavigate("flashcards") }
                    )
                }
            }

            // ==================== TAB 4: AI TUTOR ====================
            4 -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تطبيقات المساعد الذكي مع الكتاب 🤖",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "اسأل المساعد الذكي أي سؤال يتعلق بكتاب \"${activeBookData.bookTitle}\" لاختبار فهمك أو إنشاء خريطة ذهنية مخصصة!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            val quickPrompts = listOf(
                                "اختبرني في أهم 3 مفاهيم في كتاب \"${activeBookData.bookTitle}\"",
                                "اشرح لي الفصل الأول بأسلوب قصة مشوقة",
                                "كيف أربط بين مفاهيم الكتاب في جدول مراجعة بومودورو؟",
                                "صمم لي خطة مراجعة متباعدة لمذاكرة هذا الكتاب قبل الامتحانات"
                            )

                            quickPrompts.forEach { prompt ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.sendAiUserMessage(prompt)
                                        onNavigate("ai")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(prompt, fontSize = 12.sp, textAlign = TextAlign.Start)
                                }
                            }
                        }
                    }
                }
            }

            // ==================== TAB 5: CONCLUSION ====================
            5 -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeBookData.conclusionTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = activeBookData.conclusionBody,
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==================== TAB 6: AI CONCEPT LINKING LAB ====================
            6 -> {
                item {
                    AiConceptLinkingLabSection(
                        viewModel = viewModel,
                        onOpenAnki = { onNavigate("flashcards") }
                    )
                }
            }

            // ==================== TAB 7: PDF PAGE BY PAGE READER ====================
            7 -> {
                item {
                    PdfPageByPageReaderSection(
                        viewModel = viewModel,
                        onSendToAiLinker = { pageText ->
                            viewModel.generateAiConceptLink(pageText)
                            selectedTab = 6
                        }
                    )
                }
            }
        }
    }

    // Add New Book Dialog
    if (showAddBookDialog) {
        AlertDialog(
            onDismissRequest = { showAddBookDialog = false },
            title = { Text("إضافة كتاب أو محتوى دراسي جديد 🤖", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أدخل عنوان الكتاب أو المادة، والملخص أو النص الكامل ليقوم المساعد الذكي بتقسيمه إلى فصول وبطاقات ذكية:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = newBookTitle,
                        onValueChange = { newBookTitle = it },
                        label = { Text("عنوان الكتاب أو الدرس") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newBookContent,
                        onValueChange = { newBookContent = it },
                        label = { Text("نص الكتاب أو الملخص") },
                        placeholder = { Text("الصق هنا نص المقررات، أو الفصول، أو المحتوى العلمي...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBookContent.isNotBlank()) {
                            viewModel.addAndProcessNewBook(
                                title = newBookTitle.ifBlank { "كتاب دراسي" },
                                rawText = newBookContent
                            ) {
                                showAddBookDialog = false
                                newBookTitle = ""
                                newBookContent = ""
                            }
                        }
                    },
                    enabled = newBookContent.isNotBlank() && !isAiLoading
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري المعالجة...")
                    } else {
                        Text("🤖 تحويل ومعالجة")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// Interactive Smart Cards Review Component
@Composable
fun SmartCardsReviewMode(
    flashcards: List<GeneratedFlashcard>,
    bookTitle: String,
    onOpenAnki: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val activeCard = flashcards.getOrNull(currentIndex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlipToFront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "البطاقات الذكية للمذاكرة 🎴",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} من ${flashcards.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeCard != null) {
                // Interactive Flip Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { isFlipped = !isFlipped },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFlipped)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isFlipped) "💡 الإجابة / الشرح:" else "❓ السؤال / المصطلح:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isFlipped) activeCard.answer else activeCard.question,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "🔄 (انقر هنا لقلب البطاقة)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                isFlipped = false
                            }
                        },
                        enabled = currentIndex > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ArrowRight, contentDescription = "السابق")
                        Text("السابق")
                    }

                    Button(
                        onClick = { isFlipped = !isFlipped },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = "اقلب")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isFlipped) "إظهار السؤال" else "إظهار الجواب")
                    }

                    OutlinedButton(
                        onClick = {
                            if (currentIndex < flashcards.size - 1) {
                                currentIndex++
                                isFlipped = false
                            }
                        },
                        enabled = currentIndex < flashcards.size - 1,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("التالي")
                        Icon(Icons.Default.ArrowLeft, contentDescription = "التالي")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onOpenAnki) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("عرض وتصفح البطاقات المحفوظة في قسم Anki", fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// 🔗 AI Concept Linking & Association Lab (مختبر تقنية الربط الذهني)
// ==========================================
@Composable
fun AiConceptLinkingLabSection(
    viewModel: MainViewModel,
    onOpenAnki: () -> Unit
) {
    val context = LocalContext.current
    val isLinkingLoading by viewModel.isConceptLinkingLoading.collectAsState()
    val lastLinkResult by viewModel.lastConceptLinkResult.collectAsState()

    var conceptsInput by remember { mutableStateOf("") }
    var contextInput by remember { mutableStateOf("") }
    var generatedLinkText by remember { mutableStateOf<String?>(null) }
    var addedToAnkiSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(lastLinkResult) {
        if (lastLinkResult != null) {
            generatedLinkText = lastLinkResult
        }
    }

    val samplePresets = listOf(
        "🧪 فيزياء" to "قانون نيوتن الثاني (القوة = الكتلة × التسارع) + الجاذبية الأرضية",
        "🧬 أحياء" to "إنتاج الطاقة في الميتوكوندريا + جزيئات ATP",
        "📜 تاريخ" to "معركة حطين (1187م) + صلاح الدين الأيوبي وتحرير القدس",
        "🇬🇧 لغات" to "Perseverance (الإصرار) + Achievement (الإنجاز)",
        "📐 رياضيات" to "نظرية فيثاغورس (a² + b² = c²) في المثلث القائم",
        "💡 كيمياء" to "الروابط التساهمية والروابط الأيونية وتوزيع الإلكترونات"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "مختبر تقنية الربط والتداعي الذهني 🔗✨",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اربط أي مفهومين أو معادلتين برابط مستفز وقصة لا تُنسى",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "💡 نماذج وأمثلة جاهزة للربط السريع:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(samplePresets) { (label, presetText) ->
                    SuggestionChip(
                        onClick = {
                            conceptsInput = presetText
                            contextInput = label
                        },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = conceptsInput,
                onValueChange = { conceptsInput = it },
                label = { Text("المفاهيم أو المعلومات المراد ربطها") },
                placeholder = { Text("مثال: قانون أوم + المقاومة الكهربائية، أو تاريخ معركة عين جالوت...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = contextInput,
                onValueChange = { contextInput = it },
                label = { Text("المادة أو السياق الدراسي (اختياري)") },
                placeholder = { Text("مثال: فيزياء تالتة ثانوي / أحياء") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    addedToAnkiSuccess = false
                    viewModel.generateAiConceptLink(
                        conceptsToLink = conceptsInput,
                        subjectContext = contextInput,
                        onResult = { success, result ->
                            if (success) {
                                generatedLinkText = result
                            } else {
                                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                enabled = conceptsInput.isNotBlank() && !isLinkingLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLinkingLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري استحضار الرابط الخارق والقصة...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("توليد الرابط الذهني الخارق بالذكاء الاصطناعي 🔗✨", fontWeight = FontWeight.Bold)
                }
            }

            // Results View
            if (generatedLinkText != null) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🧠 نتيجة الربط الذهني وتقنيات الحفظ:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    viewModel.copyTextToClipboard("الرابط الذهني", generatedLinkText ?: "")
                                    Toast.makeText(context, "تم نسخ الرابط للحافظة بنجاح 📋", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = generatedLinkText ?: "",
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Add to Anki Button
                        Button(
                            onClick = {
                                viewModel.addQuickConceptFlashcard(
                                    question = "ما هو الرابط الذهني لحفظ: $conceptsInput؟",
                                    answer = generatedLinkText ?: ""
                                ) {
                                    addedToAnkiSuccess = true
                                    Toast.makeText(context, "تمت إضافة البطاقة إلى Anki بنجاح! 🃏", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !addedToAnkiSuccess,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (addedToAnkiSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (addedToAnkiSuccess) Icons.Default.CheckCircle else Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (addedToAnkiSuccess) "تمت الإضافة لحزمة أنكي بنجاح ✅" else "حفظ كرابط دائم في بطاقات Anki 🎴",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 📑 PDF Page-by-Page Reader (All 43 Pages Verbatim)
// ==========================================
@Composable
fun PdfPageByPageReaderSection(
    viewModel: MainViewModel,
    onSendToAiLinker: (String) -> Unit
) {
    val context = LocalContext.current
    val pages = com.example.domain.book.HowToRememberPdfPages.all43Pages
    var currentPageIndex by remember { mutableStateOf(0) }
    var pageSearchQuery by remember { mutableStateOf("") }
    var showJumpDialog by remember { mutableStateOf(false) }

    val activePage = pages.getOrNull(currentPageIndex) ?: pages[0]

    val searchMatches = remember(pageSearchQuery) {
        if (pageSearchQuery.isBlank()) emptyList()
        else pages.filter { it.content.contains(pageSearchQuery, ignoreCase = true) || it.title.contains(pageSearchQuery, ignoreCase = true) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFE53935))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "قارئ الكتاب بصيغة PDF الكاملة 📑",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "جميع الـ 43 صفحة محفوظة حرفياً وبدون أي نقصان",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { showJumpDialog = true }
                ) {
                    Text(
                        text = "ص ${activePage.pageNumber} / 43 🔍",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search within pages
            OutlinedTextField(
                value = pageSearchQuery,
                onValueChange = { pageSearchQuery = it },
                placeholder = { Text("بحث في نصوص الـ 43 صفحة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (pageSearchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { pageSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Search matches list
            if (pageSearchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (searchMatches.isEmpty()) {
                    Text("لم يتم العثور على نتائج لـ \"$pageSearchQuery\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    Text("تم العثور على ${searchMatches.size} صفحة تحتوي على الكلمة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(searchMatches) { matchPage ->
                            AssistChip(
                                onClick = {
                                    currentPageIndex = matchPage.pageNumber - 1
                                },
                                label = { Text("صفحة ${matchPage.pageNumber}: ${matchPage.title.take(15)}", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Page Paper View
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFCFDFE),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header of page
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = activePage.chapterTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "الصفحة ${activePage.pageNumber}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activePage.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = activePage.content,
                        fontSize = 14.sp,
                        lineHeight = 26.sp,
                        color = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Page Action Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.copyTextToClipboard("كتاب كيف تتذكر - صفحة ${activePage.pageNumber}", activePage.content)
                                Toast.makeText(context, "تم نسخ نص الصفحة 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                onSendToAiLinker(activePage.content)
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ربط ذكي للصفحة 🔗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Controls (Prev / Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentPageIndex > 0) currentPageIndex--
                    },
                    enabled = currentPageIndex > 0,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ArrowRight, contentDescription = "السابق")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الصفحة السابقة")
                }

                Text(
                    text = "${activePage.pageNumber} / ${pages.size}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                OutlinedButton(
                    onClick = {
                        if (currentPageIndex < pages.size - 1) currentPageIndex++
                    },
                    enabled = currentPageIndex < pages.size - 1,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("الصفحة التالية")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowLeft, contentDescription = "التالي")
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        var targetPageNum by remember { mutableStateOf((currentPageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("الانتقال السريع إلى صفحة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رقم الصفحة من 1 إلى 43:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = targetPageNum,
                        onValueChange = { targetPageNum = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = targetPageNum.toIntOrNull()
                        if (num != null && num in 1..43) {
                            currentPageIndex = num - 1
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("انتقال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

