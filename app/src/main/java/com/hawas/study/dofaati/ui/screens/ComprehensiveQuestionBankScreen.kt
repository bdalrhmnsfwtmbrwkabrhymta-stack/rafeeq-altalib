package com.hawas.study.dofaati.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader

@Composable
fun ComprehensiveQuestionBankScreen(viewModel: DofaatiViewModel) {
    val questions by viewModel.questionBankItems.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(60) }

    val pdfOrTextPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importQuestionsFromPdfUri(uri)
        }
    }

    val filteredQuestions = remember(questions, searchQuery) {
        if (searchQuery.isBlank()) questions
        else questions.filter {
            it.questionText.contains(searchQuery, ignoreCase = true) ||
                it.subjectName.contains(searchQuery, ignoreCase = true) ||
                it.sourceTag.contains(searchQuery, ignoreCase = true)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            AddQuestionBatchButton(onClick = { viewModel.addQuickQuestionBatchToBank(25) })
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
                            onSearchChange = { searchQuery = it },
                            onImportClick = {
                                pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                            }
                        )
                    }
                    items(displayedQuestions, key = { it.id }) { item ->
                        QuestionBankItemCard(item = item)
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
                    onSearchChange = { searchQuery = it },
                    onImportClick = {
                        pdfOrTextPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                AddQuestionBatchButton(onClick = { viewModel.addQuickQuestionBatchToBank(25) })
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedQuestions, key = { it.id }) { item ->
                    QuestionBankItemCard(item = item)
                }
            }
        }
    )
}

@Composable
private fun BankImportAndSearchHeader(
    totalCountLabel: String,
    searchQuery: String,
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
private fun QuestionBankItemCard(item: DofaatiQuestionItem) {
    var expanded by remember { mutableStateOf(item.questionNumber == 1) }

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
                Text(
                    text = item.sourceTag,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 11.sp,
                    color = DofaatiColors.SlateMuted
                )
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
                                color = DofaatiColors.NavyInk
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

                // Battery on left vertical wire (Vc)
                drawLine(wireColor, Offset(leftX, topY), Offset(leftX, midY - 12.dp.toPx()), strokeWidth)
                drawLine(DofaatiColors.RedAlert, Offset(leftX - 14.dp.toPx(), midY - 12.dp.toPx()), Offset(leftX + 14.dp.toPx(), midY - 12.dp.toPx()), 3.dp.toPx())
                drawLine(wireColor, Offset(leftX - 8.dp.toPx(), midY + 4.dp.toPx()), Offset(leftX + 8.dp.toPx(), midY + 4.dp.toPx()), 3.dp.toPx())
                drawLine(wireColor, Offset(leftX, midY + 4.dp.toPx()), Offset(leftX, bottomY), strokeWidth)

                val vcText = textMeasurer.measure("Vc", labelStyle)
                drawText(vcText, topLeft = Offset(leftX - vcText.size.width - 14.dp.toPx(), midY - vcText.size.height / 2f))

                // Series resistor 2Ω on top left
                drawResistorZigZag(leftX, w * 0.38f, topY, "2Ω", labelAbove = true)

                // Parallel junction nodes
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

                // Right wire & bottom resistor R_B
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
private fun AddQuestionBatchButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("btn_add_question_batch")
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "+ إضافة دفعة أسئلة جديدة",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
