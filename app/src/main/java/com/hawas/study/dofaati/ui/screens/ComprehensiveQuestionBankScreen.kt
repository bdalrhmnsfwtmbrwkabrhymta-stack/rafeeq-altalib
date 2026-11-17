package com.hawas.study.dofaati.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader

@Composable
fun ComprehensiveQuestionBankScreen(viewModel: DofaatiViewModel) {
    val questions by viewModel.questionBankItems.collectAsStateWithLifecycle()
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val activeBatch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.firstOrNull()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSubjectFilter by remember { mutableStateOf<String?>(null) }
    var visibleCount by remember { mutableIntStateOf(60) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }

    val pdfOrTextPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importQuestionsFromPdfUri(uri)
        }
    }

    val availableSubjects = remember(questions) {
        questions.map { it.subjectName.trim() }.filter { it.isNotBlank() }.distinct()
    }

    val filteredQuestions = remember(questions, searchQuery, selectedSubjectFilter) {
        questions.filter { q ->
            val matchesSubject = selectedSubjectFilter == null || q.subjectName == selectedSubjectFilter
            val matchesSearch = searchQuery.isBlank() ||
                q.questionText.contains(searchQuery, ignoreCase = true) ||
                q.subjectName.contains(searchQuery, ignoreCase = true) ||
                q.sourceTag.contains(searchQuery, ignoreCase = true)
            matchesSubject && matchesSearch
        }
    }
    val displayedQuestions = remember(filteredQuestions, visibleCount) {
        filteredQuestions.take(visibleCount)
    }
    val totalCountLabel = "إجمالي الأسئلة: ${questions.size} سؤال"

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.38f,
        portraitContent = {
            Scaffold(
                containerColor = DofaatiColors.Background,
                topBar = {
                    DofaatiRoyalBlueHeader(
                        title = "بنك الأسئلة الشامل",
                        subtitle = totalCountLabel,
                        onNavigateBack = { viewModel.navigateBack() },
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.testTag("question_bank_count_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${questions.size}",
                                        fontFamily = ReadexProFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        },
                        testTag = "question_bank_header"
                    )
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AddManualQuestionButton(
                                onClick = { showAddQuestionDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                            if (questions.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.startQuizFromQuestionBank() },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.EmeraldGreen),
                                    modifier = Modifier
                                        .heightIn(min = 50.dp)
                                        .testTag("btn_start_bank_quiz")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اختبار", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("question_bank_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        BankImportAndSearchHeader(
                            totalCountLabel = totalCountLabel,
                            searchQuery = searchQuery,
                            availableSubjects = availableSubjects,
                            selectedSubject = selectedSubjectFilter,
                            onSelectSubject = { selectedSubjectFilter = it },
                            onSearchChange = { searchQuery = it },
                            onImportClick = {
                                pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                            }
                        )
                    }
                    if (displayedQuestions.isEmpty()) {
                        item {
                            EmptyQuestionBankCard(
                                hasSearch = searchQuery.isNotBlank() || selectedSubjectFilter != null,
                                onAddManual = { showAddQuestionDialog = true },
                                onImport = { pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain")) }
                            )
                        }
                    } else {
                        items(displayedQuestions, key = { it.id }) { item ->
                            QuestionBankItemCard(
                                item = item,
                                onDelete = { viewModel.deleteQuestionFromBank(item.id) }
                            )
                        }
                    }
                    if (displayedQuestions.size < filteredQuestions.size) {
                        item {
                            OutlinedButton(
                                onClick = { visibleCount += 100 },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(
                                    text = "عرض المزيد (${filteredQuestions.size - displayedQuestions.size} سؤال متبقي)",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DofaatiRoyalBlueHeader(
                    title = "بنك الأسئلة الشامل",
                    subtitle = totalCountLabel,
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "question_bank_header_land"
                )
                BankImportAndSearchHeader(
                    totalCountLabel = totalCountLabel,
                    searchQuery = searchQuery,
                    availableSubjects = availableSubjects,
                    selectedSubject = selectedSubjectFilter,
                    onSelectSubject = { selectedSubjectFilter = it },
                    onSearchChange = { searchQuery = it },
                    onImportClick = {
                        pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                AddManualQuestionButton(
                    onClick = { showAddQuestionDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
                if (questions.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.startQuizFromQuestionBank() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.EmeraldGreen),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بدء تحدي من البنك", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
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
                if (displayedQuestions.isEmpty()) {
                    item {
                        EmptyQuestionBankCard(
                            hasSearch = searchQuery.isNotBlank() || selectedSubjectFilter != null,
                            onAddManual = { showAddQuestionDialog = true },
                            onImport = { pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain")) }
                        )
                    }
                } else {
                    items(displayedQuestions, key = { it.id }) { item ->
                        QuestionBankItemCard(
                            item = item,
                            onDelete = { viewModel.deleteQuestionFromBank(item.id) }
                        )
                    }
                }
            }
        }
    )

    if (showAddQuestionDialog) {
        ManualQuestionCreatorDialog(
            defaultSubject = activeBatch?.subjects?.firstOrNull()?.name ?: "",
            nextQuestionNumber = questions.size + 1,
            onSave = { newItem ->
                viewModel.addManualQuestionToBank(newItem)
                showAddQuestionDialog = false
            },
            onDismiss = { showAddQuestionDialog = false }
        )
    }
}

@Composable
private fun EmptyQuestionBankCard(
    hasSearch: Boolean,
    onAddManual: () -> Unit,
    onImport: () -> Unit
) {
    DofaatiCard(
        borderColor = DofaatiColors.RoyalBlueTint,
        borderWidth = 1.2.dp,
        modifier = Modifier.testTag("empty_question_bank_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (hasSearch) "لا توجد أسئلة مطابقة للبحث" else "بنك الأسئلة فارغ حالياً",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = DofaatiColors.NavyInk,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSearch) {
                    "جرّب مسح الفلتر أو البحث بكلمات أخرى."
                } else {
                    "أضف أسئلتك يدوياً أو استورد ملف PDF أو ولّد أسئلة بالذكاء الاصطناعي."
                },
                fontFamily = ReadexProFontFamily,
                fontSize = 12.5.sp,
                color = DofaatiColors.SlateMuted,
                textAlign = TextAlign.Center
            )
            if (!hasSearch) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddManual,
                        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إضافة سؤال", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("استيراد PDF", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BankImportAndSearchHeader(
    totalCountLabel: String,
    searchQuery: String,
    availableSubjects: List<String>,
    selectedSubject: String?,
    onSelectSubject: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DofaatiColors.RoyalBlueSoft,
            border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = totalCountLabel,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DofaatiColors.RoyalBlueDark
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onImportClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_import_pdf_questions")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = DofaatiColors.RoyalBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "استيراد من ملف PDF أو نص",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = DofaatiColors.RoyalBlue
            )
        }

        if (availableSubjects.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSubject == null,
                    onClick = { onSelectSubject(null) },
                    label = { Text("الكل", fontFamily = ReadexProFontFamily, fontSize = 12.sp) }
                )
                availableSubjects.forEach { subj ->
                    FilterChip(
                        selected = selectedSubject == subj,
                        onClick = { onSelectSubject(if (selectedSubject == subj) null else subj) },
                        label = { Text(subj, fontFamily = ReadexProFontFamily, fontSize = 12.sp) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("ابحث داخل بنك الأسئلة...", fontFamily = ReadexProFontFamily) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("question_bank_search")
        )
    }
}

@Composable
private fun QuestionBankItemCard(
    item: DofaatiQuestionItem,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    DofaatiCard(modifier = Modifier.testTag("bank_question_${item.questionNumber}")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DofaatiColors.RoyalBlueSoft
                ) {
                    Text(
                        text = "${item.subjectName} • ${item.difficulty.labelAr}",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = DofaatiColors.RoyalBlueDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.sourceTag,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.sp,
                        color = DofaatiColors.SlateMuted
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف السؤال",
                            tint = DofaatiColors.RedAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = "السؤال ${item.questionNumber}: ${item.questionText}",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DofaatiColors.NavyInk
            )

            if (item.circuitDiagramType == "SERIES_PARALLEL_3") {
                DofaatiCircuitDiagramCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .testTag("electric_circuit_diagram")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.options.forEachIndexed { idx, opt ->
                    val isCorrect = idx == item.correctOptionIndex
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCorrect) DofaatiColors.EmeraldGreen.copy(alpha = 0.1f) else DofaatiColors.Background,
                        border = BorderStroke(
                            width = if (isCorrect) 1.5.dp else 1.dp,
                            color = if (isCorrect) DofaatiColors.EmeraldGreen else DofaatiColors.BorderSoft
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = DofaatiColors.NavyInk,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCorrect) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "إجابة صحيحة",
                                    tint = DofaatiColors.EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (item.detailedExplanation.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (expanded) "إخفاء التفسير العلمي" else "عرض التفسير والحل",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = DofaatiColors.RoyalBlue
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = DofaatiColors.RoyalBlue
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DofaatiColors.RoyalBlueSoft
                    ) {
                        Text(
                            text = item.detailedExplanation,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.5.sp,
                            color = DofaatiColors.NavyInk,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualQuestionCreatorDialog(
    defaultSubject: String,
    nextQuestionNumber: Int,
    onSave: (DofaatiQuestionItem) -> Unit,
    onDismiss: () -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var subjectName by remember { mutableStateOf(defaultSubject) }
    var qType by remember { mutableStateOf(QuestionType.MCQ) }
    var difficulty by remember { mutableStateOf(DifficultyLevel.MEDIUM) }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correctIdx by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }

    val options = if (qType == QuestionType.TRUE_FALSE) {
        listOf("صح", "خطأ")
    } else {
        listOf(optA.trim(), optB.trim(), optC.trim(), optD.trim()).filter { it.isNotEmpty() }
    }
    val canSave = questionText.trim().length >= 4 && options.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("إضافة سؤال جديد للبنك", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("المادة الدراسية", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("نص السؤال", fontFamily = ReadexProFontFamily) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(QuestionType.MCQ, QuestionType.TRUE_FALSE).forEach { t ->
                        FilterChip(
                            selected = qType == t,
                            onClick = {
                                qType = t
                                correctIdx = 0
                            },
                            label = { Text(t.labelAr, fontFamily = ReadexProFontFamily, fontSize = 12.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyLevel.entries.forEach { d ->
                        FilterChip(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            label = { Text(d.labelAr, fontFamily = ReadexProFontFamily, fontSize = 11.sp) }
                        )
                    }
                }
                if (qType == QuestionType.TRUE_FALSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("صح", "خطأ").forEachIndexed { idx, label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { correctIdx = idx }
                            ) {
                                RadioButton(selected = correctIdx == idx, onClick = { correctIdx = idx })
                                Text(label, fontFamily = ReadexProFontFamily, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    val fields = listOf(
                        Triple(0, "الاختيار الأول (أ)", optA to { v: String -> optA = v }),
                        Triple(1, "الاختيار الثاني (ب)", optB to { v: String -> optB = v }),
                        Triple(2, "الاختيار الثالث (ج)", optC to { v: String -> optC = v }),
                        Triple(3, "الاختيار الرابع (د)", optD to { v: String -> optD = v })
                    )
                    fields.forEach { (idx, label, pair) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = correctIdx == idx,
                                onClick = { correctIdx = idx }
                            )
                            OutlinedTextField(
                                value = pair.first,
                                onValueChange = pair.second,
                                label = { Text(label, fontFamily = ReadexProFontFamily, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("تفسير الإجابة (اختياري)", fontFamily = ReadexProFontFamily) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val safeIdx = correctIdx.coerceIn(0, options.lastIndex)
                    onSave(
                        DofaatiQuestionItem(
                            id = "manual_q_${System.currentTimeMillis()}",
                            questionNumber = nextQuestionNumber,
                            subjectName = subjectName.trim().ifBlank { "عام" },
                            questionText = questionText.trim(),
                            questionType = qType,
                            difficulty = difficulty,
                            options = options,
                            correctOptionIndex = safeIdx,
                            detailedExplanation = explanation.trim().ifBlank {
                                "الإجابة الصحيحة هي: ${options[safeIdx]}"
                            },
                            sourceTag = "مضاف يدوياً"
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("حفظ السؤال", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}

/**
 * Custom Compose Canvas electric circuit diagram matching Screen 8 (Vc, 2Ω, 3Ω, 4Ω, R_B).
 */
@Composable
fun DofaatiCircuitDiagramCanvas(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = ReadexProFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = DofaatiColors.NavyInk
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DofaatiColors.Border)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val w = size.width
                val h = size.height
                val wireColor = DofaatiColors.NavyInk
                val strokeWidth = 2.4.dp.toPx()

                val leftX = w * 0.12f
                val rightX = w * 0.88f
                val topY = h * 0.28f
                val bottomY = h * 0.80f
                val midY = (topY + bottomY) / 2f

                fun drawResistorZigZag(startX: Float, endX: Float, y: Float, label: String, labelAbove: Boolean = true) {
                    val zigStart = startX + (endX - startX) * 0.15f
                    val zigEnd = endX - (endX - startX) * 0.15f
                    val amp = 7.dp.toPx()
                    val step = (zigEnd - zigStart) / 6f

                    val path = Path().apply {
                        moveTo(startX, y)
                        lineTo(zigStart, y)
                        for (i in 0 until 6) {
                            val px = zigStart + (i + 0.5f) * step
                            val py = if (i % 2 == 0) y - amp else y + amp
                            lineTo(px, py)
                        }
                        lineTo(zigEnd, y)
                        lineTo(endX, y)
                    }
                    drawPath(
                        path = path,
                        color = DofaatiColors.RoyalBlue,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = (startX + endX) / 2f - measured.size.width / 2f,
                            y = if (labelAbove) y - amp - measured.size.height - 2.dp.toPx() else y + amp + 2.dp.toPx()
                        )
                    )
                }

                drawLine(wireColor, Offset(leftX, topY), Offset(leftX, midY - 12.dp.toPx()), strokeWidth)
                drawLine(DofaatiColors.RedAlert, Offset(leftX - 14.dp.toPx(), midY - 12.dp.toPx()), Offset(leftX + 14.dp.toPx(), midY - 12.dp.toPx()), 3.dp.toPx())
                drawLine(wireColor, Offset(leftX - 8.dp.toPx(), midY + 4.dp.toPx()), Offset(leftX + 8.dp.toPx(), midY + 4.dp.toPx()), 3.dp.toPx())
                drawLine(wireColor, Offset(leftX, midY + 4.dp.toPx()), Offset(leftX, bottomY), strokeWidth)

                val vcText = textMeasurer.measure("Vc", labelStyle)
                drawText(vcText, topLeft = Offset(leftX - vcText.size.width - 14.dp.toPx(), midY - vcText.size.height / 2f))

                drawResistorZigZag(leftX, w * 0.38f, topY, "2Ω", labelAbove = true)

                val pStart = w * 0.38f
                val pEnd = w * 0.72f
                val pTop = h * 0.18f
                val pBot = h * 0.44f

                drawLine(wireColor, Offset(pStart, topY), Offset(pStart, pTop), strokeWidth)
                drawLine(wireColor, Offset(pStart, topY), Offset(pStart, pBot), strokeWidth)
                drawCircle(DofaatiColors.RoyalBlue, radius = 4.dp.toPx(), center = Offset(pStart, topY))

                drawResistorZigZag(pStart, pEnd, pTop, "3Ω", labelAbove = true)
                drawResistorZigZag(pStart, pEnd, pBot, "4Ω", labelAbove = false)

                drawLine(wireColor, Offset(pEnd, pTop), Offset(pEnd, topY), strokeWidth)
                drawLine(wireColor, Offset(pEnd, pBot), Offset(pEnd, topY), strokeWidth)
                drawCircle(DofaatiColors.RoyalBlue, radius = 4.dp.toPx(), center = Offset(pEnd, topY))

                drawLine(wireColor, Offset(pEnd, topY), Offset(rightX, topY), strokeWidth)
                drawLine(wireColor, Offset(rightX, topY), Offset(rightX, bottomY), strokeWidth)
                drawLine(wireColor, Offset(rightX, bottomY), Offset(w * 0.65f, bottomY), strokeWidth)
                drawResistorZigZag(w * 0.35f, w * 0.65f, bottomY, "R_B", labelAbove = true)
                drawLine(wireColor, Offset(leftX, bottomY), Offset(w * 0.35f, bottomY), strokeWidth)
            }
        }
    }
}

@Composable
private fun AddManualQuestionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
        modifier = modifier
            .heightIn(min = 50.dp)
            .testTag("btn_add_question_batch")
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "+ إضافة سؤال يدوياً",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp
        )
    }
}
