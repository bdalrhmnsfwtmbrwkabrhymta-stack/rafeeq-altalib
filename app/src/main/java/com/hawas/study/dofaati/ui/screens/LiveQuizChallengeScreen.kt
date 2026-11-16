package com.hawas.study.dofaati.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.LiveChallengeSessionState
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText

private val QuizPillPalette = listOf(
    Color(0xFFEF4444), // Red
    Color(0xFF1D4ED8), // Royal Blue
    Color(0xFFD97706), // Amber
    Color(0xFF10B981)  // Emerald
)

@Composable
fun LiveQuizChallengeScreen(viewModel: DofaatiViewModel) {
    val state by viewModel.liveChallenge.collectAsStateWithLifecycle()
    val currentQuestion = state.questions.getOrNull(state.currentQuestionIndex)
        ?: DofaatiViewModel.buildDefaultLiveChallengeQuestions().first()
    val selectedOption = state.selectedAnswers[currentQuestion.id]

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DofaatiColors.QuizDarkGradient)
            .testTag("live_quiz_challenge_root")
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 520.dp

        // Subtle scientific chalkboard formulas & orbits in background
        ScientificQuizCanvasBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiveChallengeTopBar(
                state = state,
                onNavigateBack = { viewModel.navigateBack() }
            )

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularQuizCountdownTimer(
                            remainingSeconds = state.remainingSeconds,
                            totalSeconds = 15
                        )
                        LiveQuestionWhiteCard(
                            question = currentQuestion,
                            questionIndex = state.currentQuestionIndex,
                            totalQuestions = state.questions.size.coerceAtLeast(1)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        QuizColoredAnswerPills(
                            options = currentQuestion.options,
                            selectedOptionIndex = selectedOption,
                            correctOptionIndex = currentQuestion.correctOptionIndex,
                            onSelectOption = { idx -> viewModel.submitLiveQuizAnswer(idx) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularQuizCountdownTimer(
                        remainingSeconds = state.remainingSeconds,
                        totalSeconds = 15
                    )
                    LiveQuestionWhiteCard(
                        question = currentQuestion,
                        questionIndex = state.currentQuestionIndex,
                        totalQuestions = state.questions.size.coerceAtLeast(1)
                    )
                    QuizColoredAnswerPills(
                        options = currentQuestion.options,
                        selectedOptionIndex = selectedOption,
                        correctOptionIndex = currentQuestion.correctOptionIndex,
                        onSelectOption = { idx -> viewModel.submitLiveQuizAnswer(idx) }
                    )
                }
            }

            LiveParticipantsBottomBar(
                answeredCount = state.answeredParticipantsCount,
                totalOnline = state.onlineParticipantsCount,
                earnedPoints = state.earnedPoints
            )
        }
    }
}

@Composable
private fun ScientificQuizCanvasBackdrop(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val formulaStyle = TextStyle(
        fontFamily = ReadexProFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.08f)
    )
    Canvas(modifier = modifier) {
        drawCircle(
            color = Color(0xFF3B82F6).copy(alpha = 0.10f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.85f, size.height * 0.16f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF60A5FA).copy(alpha = 0.08f),
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.14f, size.height * 0.78f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        val formulas = listOf(
            "V = I · R" to Offset(size.width * 0.08f, size.height * 0.14f),
            "I = Q / t" to Offset(size.width * 0.68f, size.height * 0.24f),
            "Req = R₁ + R₂" to Offset(size.width * 0.10f, size.height * 0.56f),
            "Pw = V² / R" to Offset(size.width * 0.65f, size.height * 0.72f)
        )
        formulas.forEach { (formula, pos) ->
            drawText(textMeasurer.measure(formula, formulaStyle), topLeft = pos)
        }
    }
}

@Composable
private fun LiveChallengeTopBar(
    state: LiveChallengeSessionState,
    onNavigateBack: () -> Unit
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
                .testTag("btn_live_quiz_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "خروج",
                tint = Color.White
            )
        }

        AutoSizeText(
            text = state.quizTitle,
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            maxFontSize = 15.5.sp,
            minFontSize = 11.5.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DofaatiColors.RedAlert,
            modifier = Modifier.testTag("live_online_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(6.dp))
                DofaatiLtrText(
                    text = "${state.onlineParticipantsCount}",
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun CircularQuizCountdownTimer(
    remainingSeconds: Int,
    totalSeconds: Int
) {
    val targetFraction = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 350),
        label = "quizTimerSweep"
    )
    val ringColor = if (remainingSeconds <= 5) DofaatiColors.RedAlert else DofaatiColors.BrightGold

    Box(
        modifier = Modifier
            .size(84.dp)
            .testTag("live_quiz_countdown_circle"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        DofaatiLtrText(
            text = "$remainingSeconds",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun LiveQuestionWhiteCard(
    question: DofaatiQuestionItem,
    questionIndex: Int,
    totalQuestions: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_quiz_question_card"),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DofaatiColors.RoyalBlueSoft
            ) {
                Text(
                    text = "السؤال ${questionIndex + 1} من $totalQuestions",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = DofaatiColors.RoyalBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Text(
                text = question.questionText,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = DofaatiColors.NavyInk,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuizColoredAnswerPills(
    options: List<String>,
    selectedOptionIndex: Int?,
    correctOptionIndex: Int,
    onSelectOption: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEachIndexed { idx, optionText ->
            val baseColor = QuizPillPalette[idx % QuizPillPalette.size]
            val isSelected = selectedOptionIndex == idx
            val showCorrect = selectedOptionIndex != null && idx == correctOptionIndex

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = selectedOptionIndex == null) { onSelectOption(idx) }
                    .testTag("live_quiz_option_$idx"),
                shape = RoundedCornerShape(28.dp),
                color = baseColor,
                border = if (isSelected || showCorrect) BorderStroke(3.dp, Color.White) else null,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = optionText,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    if (showCorrect) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "إجابة صحيحة",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveParticipantsBottomBar(
    answeredCount: Int,
    totalOnline: Int,
    earnedPoints: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_quiz_bottom_status"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$answeredCount من $totalOnline جاوبوا",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DofaatiColors.BrightGold
            ) {
                Text(
                    text = "$earnedPoints نقطة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = DofaatiColors.NavyInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
