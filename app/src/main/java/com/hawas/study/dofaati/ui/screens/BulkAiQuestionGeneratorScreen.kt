package com.hawas.study.dofaati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.BulkGenerationProgress
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import kotlin.math.roundToInt

@Composable
fun BulkAiQuestionGeneratorScreen(viewModel: DofaatiViewModel) {
    val bulkProgress by viewModel.bulkProgress.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingBulk.collectAsStateWithLifecycle()

    var questionCount by remember { mutableIntStateOf(25) }
    var selectedDifficulties by remember {
        mutableStateOf(setOf(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HIGHER_ORDER))
    }
    var selectedTypes by remember {
        mutableStateOf(setOf(QuestionType.MCQ, QuestionType.TRUE_FALSE))
    }
    var subjectText by remember { mutableStateOf("") }
    var topicText by remember { mutableStateOf("") }

    val onStartGenerate: () -> Unit = {
        viewModel.startBulkGeneration(
            totalCount = questionCount,
            difficulties = selectedDifficulties,
            types = selectedTypes,
            sourceText = topicText,
            subjectNameOverride = subjectText
        )
    }

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.50f,
        portraitContent = {
            Scaffold(
                containerColor = DofaatiColors.Background,
                topBar = {
                    BulkAiWhiteHeader(onNavigateBack = { viewModel.navigateBack() })
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            BulkGenerateCtaButton(
                                isGenerating = isGenerating,
                                questionCount = questionCount,
                                onClick = onStartGenerate
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("bulk_ai_generator_root"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        QuestionCountSliderCard(
                            questionCount = questionCount,
                            onCountChange = { questionCount = it }
                        )
                    }
                    item {
                        DifficultyMultiSelectCard(
                            selected = selectedDifficulties,
                            onToggle = { level ->
                                selectedDifficulties = if (level in selectedDifficulties && selectedDifficulties.size > 1) {
                                    selectedDifficulties - level
                                } else {
                                    selectedDifficulties + level
                                }
                            }
                        )
                    }
                    item {
                        QuestionTypesCheckboxCard(
                            selected = selectedTypes,
                            onToggle = { type ->
                                selectedTypes = if (type in selectedTypes && selectedTypes.size > 1) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                            }
                        )
                    }
                    item {
                        BulkTopicInputCard(
                            subjectText = subjectText,
                            onSubjectChange = { subjectText = it },
                            topicText = topicText,
                            onTopicChange = { topicText = it }
                        )
                    }
                    if (bulkProgress != null) {
                        item {
                            BulkGenerationProgressCard(
                                progress = bulkProgress!!,
                                onStartQuiz = {
                                    viewModel.launchQuizFromQuestions(
                                        title = "تحدي الذكاء الاصطناعي (${bulkProgress!!.accumulatedQuestions.size} سؤال)",
                                        questions = bulkProgress!!.accumulatedQuestions
                                    )
                                },
                                onOpenBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) }
                            )
                        }
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BulkAiWhiteHeader(onNavigateBack = { viewModel.navigateBack() })
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            QuestionCountSliderCard(
                                questionCount = questionCount,
                                onCountChange = { questionCount = it }
                            )
                        }
                        item {
                            DifficultyMultiSelectCard(
                                selected = selectedDifficulties,
                                onToggle = { level ->
                                    selectedDifficulties = if (level in selectedDifficulties && selectedDifficulties.size > 1) {
                                        selectedDifficulties - level
                                    } else {
                                        selectedDifficulties + level
                                    }
                                }
                            )
                        }
                    }
                }
                BulkGenerateCtaButton(
                    isGenerating = isGenerating,
                    questionCount = questionCount,
                    onClick = onStartGenerate
                )
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    QuestionTypesCheckboxCard(
                        selected = selectedTypes,
                        onToggle = { type ->
                            selectedTypes = if (type in selectedTypes && selectedTypes.size > 1) {
                                selectedTypes - type
                            } else {
                                selectedTypes + type
                            }
                        }
                    )
                }
                item {
                    BulkTopicInputCard(
                        subjectText = subjectText,
                        onSubjectChange = { subjectText = it },
                        topicText = topicText,
                        onTopicChange = { topicText = it }
                    )
                }
                if (bulkProgress != null) {
                    item {
                        BulkGenerationProgressCard(
                            progress = bulkProgress!!,
                            onStartQuiz = {
                                viewModel.launchQuizFromQuestions(
                                    title = "تحدي الذكاء الاصطناعي (${bulkProgress!!.accumulatedQuestions.size} سؤال)",
                                    questions = bulkProgress!!.accumulatedQuestions
                                )
                            },
                            onOpenBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BulkTopicInputCard(
    subjectText: String,
    onSubjectChange: (String) -> Unit,
    topicText: String,
    onTopicChange: (String) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "المادة وموضوع الأسئلة:",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = DofaatiColors.NavyInk
            )
            OutlinedTextField(
                value = subjectText,
                onValueChange = onSubjectChange,
                placeholder = { Text("اسم المادة (اختياري)", fontFamily = ReadexProFontFamily) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = topicText,
                onValueChange = onTopicChange,
                placeholder = {
                    Text(
                        text = "اكتب عنوان الدرس أو الصق النص الدراسي لتوليد الأسئلة منه بدقة...",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.5.sp
                    )
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_topic_input")
            )
        }
    }
}

@Composable
private fun BulkAiWhiteHeader(onNavigateBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp).testTag("btn_bulk_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = DofaatiColors.RoyalBlue
                )
            }
            AutoSizeText(
                text = "مولد الأسئلة الضخم بالذكاء الاصطناعي >",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                maxFontSize = 17.5.sp,
                minFontSize = 13.sp,
                color = DofaatiColors.RoyalBlue,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = DofaatiColors.RoyalBlue
            )
        }
    }
}

@Composable
private fun QuestionCountSliderCard(
    questionCount: Int,
    onCountChange: (Int) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "عدد الأسئلة: $questionCount سؤال",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DofaatiColors.NavyInk,
                        modifier = Modifier.testTag("bulk_count_label")
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DofaatiColors.RoyalBlueSoft
                ) {
                    Text(
                        text = "${((questionCount + 19) / 20)} دفعات",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = DofaatiColors.RoyalBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Slider(
                value = questionCount.toFloat(),
                onValueChange = { onCountChange(it.roundToInt().coerceIn(5, 100)) },
                valueRange = 5f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = DofaatiColors.RoyalBlue,
                    activeTrackColor = DofaatiColors.RoyalBlue
                ),
                modifier = Modifier.testTag("bulk_count_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 25, 50, 100).forEach { preset ->
                    val active = questionCount == preset
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCountChange(preset) }
                            .testTag("bulk_preset_$preset"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) DofaatiColors.RoyalBlue else DofaatiColors.RoyalBlueSoft,
                        border = BorderStroke(1.dp, if (active) DofaatiColors.RoyalBlue else DofaatiColors.RoyalBlueTint)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "$preset",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (active) Color.White else DofaatiColors.RoyalBlueDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyMultiSelectCard(
    selected: Set<DifficultyLevel>,
    onToggle: (DifficultyLevel) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "مستويات الصعوبة المطلوبة:",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DofaatiColors.NavyInk
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DifficultyLevel.entries.forEach { level ->
                    val isSelected = level in selected
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggle(level) }
                            .testTag("diff_pill_${level.name}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) DofaatiColors.RoyalBlue else Color.White,
                        border = BorderStroke(1.5.dp, if (isSelected) DofaatiColors.RoyalBlue else DofaatiColors.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                            AutoSizeText(
                                text = level.labelAr,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxFontSize = 13.sp,
                                minFontSize = 10.sp,
                                color = if (isSelected) Color.White else DofaatiColors.NavyInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionTypesCheckboxCard(
    selected: Set<QuestionType>,
    onToggle: (QuestionType) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "أنواع الأسئلة المشمولة:",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DofaatiColors.NavyInk
            )
            QuestionType.entries.forEach { type ->
                val checked = type in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(type) }
                        .padding(horizontal = 4.dp)
                        .testTag("type_check_${type.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(type) },
                        colors = CheckboxDefaults.colors(checkedColor = DofaatiColors.RoyalBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = type.labelAr,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.5.sp,
                        color = DofaatiColors.NavyInk
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkGenerationProgressCard(
    progress: BulkGenerationProgress,
    onStartQuiz: () -> Unit,
    onOpenBank: () -> Unit
) {
    val hasError = !progress.errorMessage.isNullOrBlank()
    val fraction = if (progress.totalRequested > 0) {
        (progress.generatedCount.toFloat() / progress.totalRequested.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val borderTint = when {
        hasError -> DofaatiColors.RedAlert
        progress.isCompleted -> DofaatiColors.EmeraldGreen
        else -> DofaatiColors.RoyalBlue
    }

    DofaatiCard(
        borderColor = borderTint,
        borderWidth = 1.5.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        hasError -> Icons.Default.ErrorOutline
                        progress.isCompleted -> Icons.Default.CheckCircle
                        else -> Icons.Default.AutoAwesome
                    },
                    contentDescription = null,
                    tint = borderTint
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = progress.errorMessage ?: progress.statusText,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (hasError) DofaatiColors.RedAlert else DofaatiColors.NavyInk,
                    modifier = Modifier.testTag("bulk_progress_status")
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = borderTint,
                trackColor = DofaatiColors.RoyalBlueTint,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Text(
                text = "الدفعة ${progress.currentBatchIndex} من ${progress.totalBatches} • تم توليد ${progress.generatedCount} سؤال",
                fontFamily = ReadexProFontFamily,
                fontSize = 12.sp,
                color = DofaatiColors.SlateMuted
            )

            if (progress.isCompleted && progress.accumulatedQuestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartQuiz,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.EmeraldGreen),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_launch_generated_quiz")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بدء الكويز", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenBank,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_open_bank_after_gen")
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("عرض البنك", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkGenerateCtaButton(
    isGenerating: Boolean,
    questionCount: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isGenerating,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("btn_generate_bulk_questions")
    ) {
        if (isGenerating) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("جاري التوليد على دفعات...", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "توليد الأسئلة فوراً ($questionCount سؤال)",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
