package com.hawas.study.dofaati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.PodiumStudent
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors

@Composable
fun QuizLeaderboardResultsScreen(viewModel: DofaatiViewModel) {
    val state by viewModel.liveChallenge.collectAsStateWithLifecycle()
    var showAnswerReviewSheet by remember { mutableStateOf(false) }

    val top3 = state.leaderboardTopThree
    val first = top3.firstOrNull { it.rank == 1 } ?: PodiumStudent(1, "يوسف محمود", 98, 980)
    val second = top3.firstOrNull { it.rank == 2 } ?: PodiumStudent(2, "سلمى طارق", 95, 950)
    val third = top3.firstOrNull { it.rank == 3 } ?: PodiumStudent(3, "عمر خالد", 92, 920)

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.52f,
        portraitContent = {
            Scaffold(
                containerColor = DofaatiColors.Background,
                topBar = {
                    GoldFramedLeaderboardHeader(onNavigateBack = { viewModel.navigateBack() })
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            GoldFramedReviewButton(onClick = { showAnswerReviewSheet = true })
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("leaderboard_results_root"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ThreeRankPodiumCard(
                            first = first,
                            second = second,
                            third = third
                        )
                    }
                    item {
                        DoubleGoldStatsCard(
                            batchAverage = state.batchAveragePercentage,
                            participantsCount = state.totalBatchParticipants,
                            yourPoints = state.earnedPoints
                        )
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoldFramedLeaderboardHeader(onNavigateBack = { viewModel.navigateBack() })
                ThreeRankPodiumCard(first = first, second = second, third = third)
            }
        },
        landscapeRightPane = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DoubleGoldStatsCard(
                    batchAverage = state.batchAveragePercentage,
                    participantsCount = state.totalBatchParticipants,
                    yourPoints = state.earnedPoints
                )
                Spacer(modifier = Modifier.weight(1f))
                GoldFramedReviewButton(onClick = { showAnswerReviewSheet = true })
            }
        }
    )

    if (showAnswerReviewSheet) {
        DetailedAnswersReviewDialog(
            questions = state.questions,
            selectedAnswers = state.selectedAnswers,
            onDismiss = { showAnswerReviewSheet = false }
        )
    }
}

@Composable
private fun GoldFramedLeaderboardHeader(onNavigateBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_header"),
        color = DofaatiColors.RoyalBlue,
        border = BorderStroke(2.dp, DofaatiColors.BrightGold),
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val dots = listOf(
                    Offset(size.width * 0.14f, size.height * 0.25f) to DofaatiColors.BrightGold,
                    Offset(size.width * 0.86f, size.height * 0.30f) to Color.White,
                    Offset(size.width * 0.28f, size.height * 0.78f) to DofaatiColors.BrightGold,
                    Offset(size.width * 0.74f, size.height * 0.80f) to DofaatiColors.EmeraldGreen
                )
                dots.forEach { (pt, clr) ->
                    drawCircle(color = clr.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = pt)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp).testTag("btn_leaderboard_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }
                AutoSizeText(
                    text = "نتيجة الاختبار ولوحة الشرف",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 18.sp,
                    minFontSize = 13.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = DofaatiColors.BrightGold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ThreeRankPodiumCard(
    first: PodiumStudent,
    second: PodiumStudent,
    third: PodiumStudent
) {
    DofaatiCard(
        borderColor = DofaatiColors.BrightGold,
        borderWidth = 1.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "أبطال التحدي — منصة التتويج",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = DofaatiColors.NavyInk
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Rank 2 (Silver)
                PodiumColumnPedestal(
                    student = second,
                    rankTitle = "المركز الثاني",
                    pedestalHeight = 112.dp,
                    gradient = Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8))),
                    textColor = DofaatiColors.NavyInk,
                    badgeIcon = Icons.Default.EmojiEvents,
                    badgeTint = Color(0xFF64748B),
                    modifier = Modifier.weight(1f).testTag("podium_rank_2")
                )

                // Rank 1 (Gold - Center Highest)
                PodiumColumnPedestal(
                    student = first,
                    rankTitle = "المركز الأول",
                    pedestalHeight = 148.dp,
                    gradient = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFD97706))),
                    textColor = DofaatiColors.NavyInk,
                    badgeIcon = Icons.Default.Star,
                    badgeTint = Color(0xFFD97706),
                    modifier = Modifier.weight(1.1f).testTag("podium_rank_1")
                )

                // Rank 3 (Bronze)
                PodiumColumnPedestal(
                    student = third,
                    rankTitle = "المركز الثالث",
                    pedestalHeight = 92.dp,
                    gradient = Brush.verticalGradient(listOf(Color(0xFFFDBA74), Color(0xFF9A3412))),
                    textColor = Color.White,
                    badgeIcon = Icons.Default.MilitaryTech,
                    badgeTint = Color(0xFF9A3412),
                    modifier = Modifier.weight(1f).testTag("podium_rank_3")
                )
            }
        }
    }
}

@Composable
private fun PodiumColumnPedestal(
    student: PodiumStudent,
    rankTitle: String,
    pedestalHeight: Dp,
    gradient: Brush,
    textColor: Color,
    badgeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(badgeTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(badgeIcon, contentDescription = rankTitle, tint = badgeTint, modifier = Modifier.size(22.dp))
        }
        AutoSizeText(
            text = student.name,
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            maxFontSize = 12.5.sp,
            minFontSize = 9.5.sp,
            color = DofaatiColors.NavyInk,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                .background(gradient)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = rankTitle,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${student.scorePercent}%",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun DoubleGoldStatsCard(
    batchAverage: Int,
    participantsCount: Int,
    yourPoints: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_stats_card"),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(2.5.dp, DofaatiColors.BrightGold),
        shadowElevation = 4.dp
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFFFBEB),
            border = BorderStroke(1.dp, DofaatiColors.AmberGold.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "متوسط الدفعة: $batchAverage% — عدد المشاركين: $participantsCount",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DofaatiColors.NavyInk,
                    textAlign = TextAlign.Center
                )
                if (yourPoints > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DofaatiColors.RoyalBlueSoft
                    ) {
                        Text(
                            text = "نقاطك المكتسبة في التحدي: $yourPoints نقطة",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DofaatiColors.RoyalBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldFramedReviewButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
        border = BorderStroke(2.dp, DofaatiColors.BrightGold),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("btn_review_detailed_answers")
    ) {
        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "مراجعة الإجابات والتفسير التفصيلي",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )
    }
}

@Composable
private fun DetailedAnswersReviewDialog(
    questions: List<DofaatiQuestionItem>,
    selectedAnswers: Map<String, Int>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مراجعة الإجابات والتفسير",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .testTag("detailed_answers_review_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(questions) { idx, q ->
                    val chosenIdx = selectedAnswers[q.id]
                    val correctText = q.options.getOrNull(q.correctOptionIndex) ?: ""
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DofaatiColors.Background,
                        border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "س${idx + 1}: ${q.questionText}",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = DofaatiColors.NavyInk
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DofaatiColors.EmeraldGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الإجابة الصحيحة: $correctText",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = DofaatiColors.EmeraldGreen
                                )
                            }
                            if (chosenIdx != null && chosenIdx != q.correctOptionIndex) {
                                Text(
                                    text = "إجابتك: ${q.options.getOrNull(chosenIdx) ?: ""}",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.sp,
                                    color = DofaatiColors.RedAlert
                                )
                            }
                            Text(
                                text = q.detailedExplanation,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.sp,
                                color = DofaatiColors.SlateBody
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("تمت المراجعة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    )
}
