package com.example.ui.screens.english

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.english.*

enum class VocabFilterTab(val title: String) {
    IN_PROGRESS("بدأتها"),
    MASTERED("ثابتة"),
    DUE("مستحقة"),
    ALL("كل القاموس")
}

/**
 * 1000-Word Dictionary & Vocabulary Engine matching UI screenshots
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishVocabularyHomeScreen(
    onNavigateBack: () -> Unit,
    speak: (text: String) -> Unit
) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf(EnglishVocabularyRepository.getAllCategories(context)) }
    var selectedTab by remember { mutableStateOf(VocabFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val expandedCategoryIds = remember { mutableStateMapOf<String, Boolean>() }

    // Initially expand first category
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && expandedCategoryIds.isEmpty()) {
            expandedCategoryIds[categories[0].categoryId] = true
        }
    }

    BackHandler {
        onNavigateBack()
    }

    // Filter categories and words based on tab & search query
    val filteredCategories = remember(categories, selectedTab, searchQuery) {
        categories.mapNotNull { cat ->
            val matchingWords = cat.words.filter { word ->
                val matchesTab = when (selectedTab) {
                    VocabFilterTab.ALL -> true
                    VocabFilterTab.IN_PROGRESS -> word.status == WordStatus.IN_PROGRESS
                    VocabFilterTab.MASTERED -> word.status == WordStatus.MASTERED
                    VocabFilterTab.DUE -> word.status == WordStatus.NEW || word.status == WordStatus.IN_PROGRESS
                }
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    val q = searchQuery.trim().lowercase()
                    word.english.lowercase().contains(q) || word.arabic.lowercase().contains(q)
                }
                matchesTab && matchesSearch
            }

            if (matchingWords.isNotEmpty()) {
                cat.copy(words = matchingWords.toMutableList())
            } else null
        }
    }

    // Totals for top statistics
    val totalWordsCount = remember(categories) { categories.sumOf { it.words.size } }
    val startedTotalCount = remember(categories) { categories.sumOf { it.startedCount } }
    val masteredTotalCount = remember(categories) { categories.sumOf { it.masteredCount } }

    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F7FF), Color(0xFFF4F0FF), Color(0xFFFFFFFF))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "قاموس الـ 1000 كلمة 📚",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = Color(0xFF1E1B4B),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "القاموس متاح بدون إنترنت ويتم المزامنة تلقائياً ⚡", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced",
                            tint = Color(0xFF059669)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF9F7FF))
            )
        },
        containerColor = Color(0xFFF9F7FF)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(purpleGradient)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Tab Filters ("بدأتها", "ثابتة", "مستحقة", "كل القاموس")
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VocabFilterTab.values()) { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C5CFC),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF475569)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF7C5CFC) else Color(0xFFE2E8F0)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // 2. Daily Quota Header Card ("حصة الجديد 10 كلمة في اليوم — ابدأ منها")
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFEDE9FE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "حصة الجديد",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "حصة الجديد 10 كلمة في اليوم — ابدأ منها",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "تم بدء $startedTotalCount كلمة · $masteredTotalCount كلمة ثابتة من أصل $totalWordsCount",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "بدأت جلسة حصة الكلمات اليومية (10 كلمات جديدة) 🚀", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("ابدأ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Status Action Badges ("مفتوح / مافيش جديد", "مراجعة / مافيش مراجعة")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مفتوح: متاح للدراسة", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Loop, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("المطابقة: بعد أول كلمات", fontSize = 12.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 4. Real-time Search Field ("دور على كلمة بالإنجليزي أو العربي")
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "دور على كلمة بالإنجليزي أو العربي...",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF7C5CFC)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF7C5CFC),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    singleLine = true
                )
            }

            // 5. Expandable Topic Tiles (Accordion UI)
            if (filteredCategories.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لم يتم العثور على كلمات مطابقة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            } else {
                items(filteredCategories, key = { it.categoryId }) { category ->
                    val isExpanded = expandedCategoryIds[category.categoryId] == true
                    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotate")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFEDE9FE))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Category Accordion Header Tile
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategoryIds[category.categoryId] = !isExpanded
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Expand/Collapse arrow
                                    IconButton(
                                        onClick = {
                                            expandedCategoryIds[category.categoryId] = !isExpanded
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Toggle",
                                            tint = Color(0xFF7C5CFC),
                                            modifier = Modifier.rotate(arrowRotation)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = category.categoryName,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E1B4B)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = Color(0xFFEDE9FE),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = category.level,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF6B21A8),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Mastery stats: "بدأت 0 · ثابتة 0"
                                        Text(
                                            text = "${category.words.size} كلمة · بدأت ${category.startedCount} · ثابتة ${category.masteredCount}",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            // Expanded Word Items List
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFAFAFC))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    category.words.forEach { word ->
                                        WordListItemCard(
                                            word = word,
                                            onToggleStatus = {
                                                val next = EnglishVocabularyRepository.cycleWordStatus(context, word.id)
                                                // Trigger state refresh
                                                categories = EnglishVocabularyRepository.getAllCategories(context).toList()
                                            },
                                            onSpeak = {
                                                speak(word.english)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Word Item List Card matching Screenshot:
 * - Status Tag ("لسه", "بدأت", "ثابتة")
 * - English Word in bold
 * - Audio Speaker Icon (TTS)
 * - Arabic Translation directly underneath
 */
@Composable
fun WordListItemCard(
    word: VocabularyWordModel,
    onToggleStatus: () -> Unit,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Tag on left
            Surface(
                color = when (word.status) {
                    WordStatus.NEW -> Color(0xFFF1F5F9)
                    WordStatus.IN_PROGRESS -> Color(0xFFFEF3C7)
                    WordStatus.MASTERED -> Color(0xFFD1FAE5)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onToggleStatus() }
            ) {
                Text(
                    text = word.status.labelAr,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (word.status) {
                        WordStatus.NEW -> Color(0xFF64748B)
                        WordStatus.IN_PROGRESS -> Color(0xFFB45309)
                        WordStatus.MASTERED -> Color(0xFF047857)
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // English Word & Arabic Meaning
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = word.english,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B),
                    textAlign = TextAlign.End
                )
                if (word.phonetic.isNotEmpty()) {
                    Text(
                        text = word.phonetic,
                        fontSize = 11.sp,
                        color = Color(0xFF7C5CFC),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.arabic,
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Audio Speaker Icon Button (calls native TTS via speak)
            IconButton(
                onClick = onSpeak,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFFF3E8FF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Speak",
                    tint = Color(0xFF7C5CFC),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
