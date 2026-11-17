package com.hawas.study.dofaati.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiFlashcard
import com.hawas.study.dofaati.models.DofaatiMindMap
import com.hawas.study.dofaati.models.DofaatiMindMapBranch
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader

@Composable
fun MindMapAndFlashcardsScreen(viewModel: DofaatiViewModel) {
    val mindMaps by viewModel.mindMaps.collectAsStateWithLifecycle()
    val flashcards by viewModel.flashcards.collectAsStateWithLifecycle()
    val isGeneratingAi by viewModel.isGeneratingMindMap.collectAsStateWithLifecycle()

    var selectedMapIndex by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf<DofaatiMindMapBranch?>(null) }

    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isCardFlipped by remember { mutableStateOf(false) }

    var showCreateMapDialog by remember { mutableStateOf(false) }
    var showAiGenerateDialog by remember { mutableStateOf(false) }
    var editingFlashcard by remember { mutableStateOf<DofaatiFlashcard?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }

    val activeMap = remember(mindMaps, selectedMapIndex) {
        if (mindMaps.isEmpty()) null else mindMaps[selectedMapIndex.coerceIn(0, mindMaps.lastIndex)]
    }
    val filteredFlashcards = remember(flashcards, searchQuery) {
        if (searchQuery.isBlank()) flashcards
        else flashcards.filter {
            it.frontTitle.contains(searchQuery, ignoreCase = true) ||
                it.backTitle.contains(searchQuery, ignoreCase = true) ||
                it.backFormula.contains(searchQuery, ignoreCase = true) ||
                it.backExplanation.contains(searchQuery, ignoreCase = true)
        }
    }
    val safeCardIndex = if (filteredFlashcards.isEmpty()) 0 else currentCardIndex.coerceIn(0, filteredFlashcards.lastIndex)
    val activeFlashcard = filteredFlashcards.getOrNull(safeCardIndex)

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.52f,
        portraitContent = {
            Column(modifier = Modifier.fillMaxSize().background(DofaatiColors.Background)) {
                DofaatiRoyalBlueHeader(
                    title = "الخريطة الذهنية",
                    subtitle = "الخريطة الذهنية وبنك المعلومات",
                    onNavigateBack = { viewModel.navigateBack() },
                    trailingContent = {
                        IconButton(
                            onClick = { searchExpanded = !searchExpanded },
                            modifier = Modifier.size(48.dp).testTag("btn_mindmap_search")
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "بحث",
                                tint = Color.White
                            )
                        }
                    },
                    testTag = "mind_map_header"
                )

                AnimatedVisibility(visible = searchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في الفروع أو البطاقات...", fontFamily = ReadexProFontFamily) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("mindmap_search_input")
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("mind_map_root"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        MindMapCanvasSection(
                            mindMap = activeMap,
                            allMaps = mindMaps,
                            selectedMapIndex = selectedMapIndex,
                            isGeneratingAi = isGeneratingAi,
                            onSelectMap = {
                                selectedMapIndex = it
                                selectedBranch = null
                            },
                            searchQuery = searchQuery,
                            selectedBranch = selectedBranch,
                            onSelectBranch = { selectedBranch = it },
                            onDeleteMap = { map ->
                                viewModel.deleteMindMap(map.id)
                                selectedBranch = null
                                selectedMapIndex = 0
                            },
                            onOpenCreateDialog = { showCreateMapDialog = true },
                            onOpenAiDialog = { showAiGenerateDialog = true }
                        )
                    }

                    item {
                        FlashcardsSection(
                            card = activeFlashcard,
                            cardPosition = safeCardIndex + 1,
                            totalCards = filteredFlashcards.size,
                            isFlipped = isCardFlipped,
                            onFlip = { isCardFlipped = !isCardFlipped },
                            onPrev = {
                                if (filteredFlashcards.isNotEmpty()) {
                                    currentCardIndex = (safeCardIndex - 1 + filteredFlashcards.size) % filteredFlashcards.size
                                    isCardFlipped = false
                                }
                            },
                            onNext = {
                                if (filteredFlashcards.isNotEmpty()) {
                                    currentCardIndex = (safeCardIndex + 1) % filteredFlashcards.size
                                    isCardFlipped = false
                                }
                            },
                            onEdit = { editingFlashcard = activeFlashcard },
                            onDelete = {
                                activeFlashcard?.let {
                                    viewModel.deleteFlashcard(it.id)
                                    isCardFlipped = false
                                }
                            },
                            onCreateNew = { showCreateCardDialog = true }
                        )
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DofaatiRoyalBlueHeader(
                    title = "الخريطة الذهنية",
                    subtitle = "الخريطة الذهنية وبنك المعلومات",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "mind_map_header_land"
                )
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                        item {
                            MindMapCanvasSection(
                                mindMap = activeMap,
                                allMaps = mindMaps,
                                selectedMapIndex = selectedMapIndex,
                                isGeneratingAi = isGeneratingAi,
                                onSelectMap = {
                                    selectedMapIndex = it
                                    selectedBranch = null
                                },
                                searchQuery = searchQuery,
                                selectedBranch = selectedBranch,
                                onSelectBranch = { selectedBranch = it },
                                onDeleteMap = { map ->
                                    viewModel.deleteMindMap(map.id)
                                    selectedBranch = null
                                    selectedMapIndex = 0
                                },
                                onOpenCreateDialog = { showCreateMapDialog = true },
                                onOpenAiDialog = { showAiGenerateDialog = true }
                            )
                        }
                    }
                }
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FlashcardsSection(
                        card = activeFlashcard,
                        cardPosition = safeCardIndex + 1,
                        totalCards = filteredFlashcards.size,
                        isFlipped = isCardFlipped,
                        onFlip = { isCardFlipped = !isCardFlipped },
                        onPrev = {
                            if (filteredFlashcards.isNotEmpty()) {
                                currentCardIndex = (safeCardIndex - 1 + filteredFlashcards.size) % filteredFlashcards.size
                                isCardFlipped = false
                            }
                        },
                        onNext = {
                            if (filteredFlashcards.isNotEmpty()) {
                                currentCardIndex = (safeCardIndex + 1) % filteredFlashcards.size
                                isCardFlipped = false
                            }
                        },
                        onEdit = { editingFlashcard = activeFlashcard },
                        onDelete = {
                            activeFlashcard?.let {
                                viewModel.deleteFlashcard(it.id)
                                isCardFlipped = false
                            }
                        },
                        onCreateNew = { showCreateCardDialog = true }
                    )
                }
            }
        }
    )

    if (showCreateMapDialog) {
        CreateMindMapDialog(
            onCreate = { center, branches ->
                viewModel.createNewMindMap(center, branches)
                selectedMapIndex = 0
                selectedBranch = null
                showCreateMapDialog = false
            },
            onDismiss = { showCreateMapDialog = false }
        )
    }

    if (showAiGenerateDialog) {
        GenerateMindMapAiDialog(
            onGenerate = { topicOrNotes, sourceTitle ->
                viewModel.generateMindMapAndCardsWithAi(topicOrNotes, sourceTitle)
                showAiGenerateDialog = false
            },
            onDismiss = { showAiGenerateDialog = false }
        )
    }

    if (editingFlashcard != null || showCreateCardDialog) {
        FlashcardEditorDialog(
            initial = editingFlashcard,
            onSave = { saved ->
                viewModel.saveFlashcard(saved)
                editingFlashcard = null
                showCreateCardDialog = false
                isCardFlipped = false
            },
            onDismiss = {
                editingFlashcard = null
                showCreateCardDialog = false
            }
        )
    }
}

@Composable
private fun MindMapCanvasSection(
    mindMap: DofaatiMindMap?,
    allMaps: List<DofaatiMindMap>,
    selectedMapIndex: Int,
    isGeneratingAi: Boolean,
    onSelectMap: (Int) -> Unit,
    searchQuery: String,
    selectedBranch: DofaatiMindMapBranch?,
    onSelectBranch: (DofaatiMindMapBranch) -> Unit,
    onDeleteMap: (DofaatiMindMap) -> Unit,
    onOpenCreateDialog: () -> Unit,
    onOpenAiDialog: () -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الخريطة الذهنية وبنك المعلومات",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = DofaatiColors.NavyInk
                    )
                }
                if (mindMap != null) {
                    IconButton(onClick = { onDeleteMap(mindMap) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الخريطة", tint = DofaatiColors.RedAlert)
                    }
                }
            }

            if (allMaps.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allMaps.take(3).forEachIndexed { idx, map ->
                        FilterChip(
                            selected = idx == selectedMapIndex,
                            onClick = { onSelectMap(idx) },
                            label = {
                                Text(map.centerTitle, fontFamily = ReadexProFontFamily, fontSize = 11.5.sp)
                            }
                        )
                    }
                }
            }

            if (mindMap == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DofaatiColors.Background,
                    border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "لا توجد خريطة ذهنية محفوظة بعد",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = DofaatiColors.NavyInk,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "أنشئ خريطة يدوياً أو اكتب موضوعاً لتوليد خريطة وبطاقات مراجعة بالذكاء الاصطناعي.",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.sp,
                            color = DofaatiColors.SlateMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val branches = mindMap.branches.take(4)
                val topBranches = branches.take(2)
                val bottomBranches = branches.drop(2)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DofaatiColors.Background)
                        .padding(12.dp)
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val anchors = listOf(
                            Offset(size.width * 0.24f, size.height * 0.20f) to Color(branches.getOrNull(0)?.accentHex ?: 0xFF10B981L),
                            Offset(size.width * 0.76f, size.height * 0.20f) to Color(branches.getOrNull(1)?.accentHex ?: 0xFFEA580CL),
                            Offset(size.width * 0.24f, size.height * 0.80f) to Color(branches.getOrNull(2)?.accentHex ?: 0xFFF59E0BL),
                            Offset(size.width * 0.76f, size.height * 0.80f) to Color(branches.getOrNull(3)?.accentHex ?: 0xFF8B5CF6L)
                        )
                        anchors.take(branches.size).forEach { (end, lineColor) ->
                            val path = Path().apply {
                                moveTo(cx, cy)
                                quadraticTo((cx + end.x) / 2f, cy, end.x, end.y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor.copy(alpha = 0.55f),
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            topBranches.forEach { branch ->
                                MindMapBranchNodeCard(
                                    branch = branch,
                                    isSelected = selectedBranch?.id == branch.id,
                                    isHighlighted = searchQuery.isNotBlank() && branch.title.contains(searchQuery, ignoreCase = true),
                                    onClick = { onSelectBranch(branch) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = DofaatiColors.RoyalBlue,
                            shadowElevation = 6.dp,
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier.testTag("mindmap_center_node")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mindMap.centerTitle,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        if (bottomBranches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                bottomBranches.forEach { branch ->
                                    MindMapBranchNodeCard(
                                        branch = branch,
                                        isSelected = selectedBranch?.id == branch.id,
                                        isHighlighted = searchQuery.isNotBlank() && branch.title.contains(searchQuery, ignoreCase = true),
                                        onClick = { onSelectBranch(branch) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedBranch != null) {
                    val accent = Color(selectedBranch.accentHex)
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("selected_branch_detail"),
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedBranch.title,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = DofaatiColors.NavyInk
                                )
                                if (selectedBranch.subtitle.isNotBlank()) {
                                    Text(
                                        text = selectedBranch.subtitle,
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 12.5.sp,
                                        color = DofaatiColors.SlateBody
                                    )
                                }
                            }
                            if (selectedBranch.formulaOrBadge.isNotBlank()) {
                                Surface(shape = RoundedCornerShape(10.dp), color = accent) {
                                    DofaatiLtrText(
                                        text = selectedBranch.formulaOrBadge,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenCreateDialog,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_create_mind_map")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "خريطة يدوية",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = DofaatiColors.RoyalBlue
                    )
                }

                Button(
                    onClick = onOpenAiDialog,
                    enabled = !isGeneratingAi,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_ai_mind_map")
                ) {
                    if (isGeneratingAi) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري التوليد...", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("توليد AI", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MindMapBranchNodeCard(
    branch: DofaatiMindMapBranch,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(branch.accentHex)
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("mindmap_branch_${branch.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected || isHighlighted) accent.copy(alpha = 0.14f) else Color.White,
        border = BorderStroke(if (isSelected) 2.dp else 1.2.dp, accent),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = branch.title,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = DofaatiColors.NavyInk,
                textAlign = TextAlign.Center
            )
            if (branch.formulaOrBadge.isNotBlank()) {
                DofaatiLtrText(
                    text = branch.formulaOrBadge,
                    fontSize = 11.5.sp,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun FlashcardsSection(
    card: DofaatiFlashcard?,
    cardPosition: Int,
    totalCards: Int,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCreateNew: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "flashcardFlip"
    )

    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = card?.sourceTitle ?: "بطاقات الاستذكار",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = DofaatiColors.NavyInk
                    )
                    Text(
                        text = if (totalCards > 0) "بطاقة $cardPosition من $totalCards" else "لا توجد بطاقات بعد",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.sp,
                        color = DofaatiColors.SlateMuted
                    )
                }
                TextButton(onClick = onCreateNew, modifier = Modifier.testTag("btn_add_flashcard")) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة بطاقة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, color = DofaatiColors.RoyalBlue)
                }
            }

            if (card == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DofaatiColors.Background,
                    border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                ) {
                    Text(
                        text = "اضغط «إضافة بطاقة» لإضافة بطاقة مراجعة سريعة أو ولّد بطاقات بالذكاء الاصطناعي.",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.5.sp,
                        color = DofaatiColors.SlateMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 176.dp)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 14f * density
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onFlip)
                        .testTag("flashcard_interactive_surface"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (rotation <= 90f) DofaatiColors.RoyalBlueSoft else DofaatiColors.RoyalBlue,
                    border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
                    shadowElevation = 3.dp
                ) {
                    if (rotation <= 90f) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color.White) {
                                Text(
                                    text = "الوجه الأمامي — اختبر نفسك",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 11.5.sp,
                                    color = DofaatiColors.RoyalBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = card.frontTitle,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = DofaatiColors.NavyInk,
                                textAlign = TextAlign.Center
                            )
                            if (card.frontSubtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = card.frontSubtitle,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 13.5.sp,
                                    color = DofaatiColors.SlateBody,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { rotationY = 180f }
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = card.backTitle,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            if (card.backFormula.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                                    DofaatiLtrText(
                                        text = card.backFormula,
                                        fontSize = 20.sp,
                                        color = DofaatiColors.RoyalBlueDark,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = card.backExplanation,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.94f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(48.dp).testTag("btn_flashcard_prev")
                    ) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = "البطاقة السابقة", tint = DofaatiColors.NavyInk)
                    }
                    Button(
                        onClick = onFlip,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_flip_flashcard")
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اقلب البطاقة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(48.dp).testTag("btn_flashcard_next")
                    ) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "البطاقة التالية", tint = DofaatiColors.NavyInk)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_edit_flashcard")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تعديل", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DofaatiColors.RedAlert),
                        border = BorderStroke(1.dp, DofaatiColors.RedAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_delete_flashcard")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateMindMapDialog(
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var centerTitle by remember { mutableStateOf("") }
    var branchesInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("إنشاء خريطة ذهنية جديدة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = centerTitle,
                    onValueChange = { centerTitle = it },
                    label = { Text("المفهوم المركزي", fontFamily = ReadexProFontFamily) },
                    placeholder = { Text("مثال: المعادلات التفاضلية", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branchesInput,
                    onValueChange = { branchesInput = it },
                    label = { Text("الفروع (مفصولة بفاصلة)", fontFamily = ReadexProFontFamily) },
                    placeholder = { Text("فرع 1، فرع 2، فرع 3", fontFamily = ReadexProFontFamily) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val split = branchesInput.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    onCreate(centerTitle, split)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("إنشاء", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}

@Composable
private fun GenerateMindMapAiDialog(
    onGenerate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceTitle by remember { mutableStateOf("") }
    var topicNotes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("توليد خريطة وبطاقات بالذكاء الاصطناعي", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sourceTitle,
                    onValueChange = { sourceTitle = it },
                    label = { Text("اسم الدرس أو المصدر", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topicNotes,
                    onValueChange = { topicNotes = it },
                    label = { Text("الموضوع أو الملخص", fontFamily = ReadexProFontFamily) },
                    placeholder = { Text("اكتب النقاط أو القوانين المراد تحويلها لخريطة وبطاقات...", fontFamily = ReadexProFontFamily) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = topicNotes.trim().length >= 4,
                onClick = { onGenerate(topicNotes, sourceTitle) },
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("توليد الآن", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}

@Composable
private fun FlashcardEditorDialog(
    initial: DofaatiFlashcard?,
    onSave: (DofaatiFlashcard) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceTitle by remember { mutableStateOf(initial?.sourceTitle ?: "") }
    var front by remember { mutableStateOf(initial?.frontTitle ?: "") }
    var subtitle by remember { mutableStateOf(initial?.frontSubtitle ?: "") }
    var formula by remember { mutableStateOf(initial?.backFormula ?: "") }
    var explanation by remember { mutableStateOf(initial?.backExplanation ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (initial == null) "إضافة بطاقة جديدة" else "تعديل البطاقة",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sourceTitle,
                    onValueChange = { sourceTitle = it },
                    label = { Text("المادة / الدرس (اختياري)", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text("السؤال / الواجهة", fontFamily = ReadexProFontFamily) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("تلميح قصير (اختياري)", fontFamily = ReadexProFontFamily) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formula,
                    onValueChange = { formula = it },
                    label = { Text("الصيغة أو الكلمة المفتاحية (اختياري)", fontFamily = ReadexProFontFamily) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("الإجابة والشرح", fontFamily = ReadexProFontFamily) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = front.trim().isNotEmpty() && (explanation.trim().isNotEmpty() || formula.trim().isNotEmpty()),
                onClick = {
                    val card = DofaatiFlashcard(
                        id = initial?.id ?: "fc_${System.currentTimeMillis()}",
                        sourceTitle = sourceTitle.trim().ifBlank { "بطاقات مخصصة" },
                        frontTitle = front.trim(),
                        frontSubtitle = subtitle.trim(),
                        backTitle = "الإجابة",
                        backFormula = formula.trim(),
                        backExplanation = explanation.trim().ifBlank { formula.trim() }
                    )
                    onSave(card)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("حفظ", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}
