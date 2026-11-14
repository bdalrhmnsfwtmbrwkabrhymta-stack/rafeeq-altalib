package com.example.ui.screens.english

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.english.EnglishLevelCategory
import com.example.domain.english.EnglishPlacementTestData
import com.example.domain.english.PlacementQuestion
import com.example.domain.english.PlacementTestResult

/**
 * 1. Intro Screen matching Screenshot 1: "إزاي بيشتغل"
 */
@Composable
fun PlacementTestIntroView(
    onStartQuiz: () -> Unit,
    onBack: () -> Unit
) {
    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF8F6FF), Color(0xFFF1EEFF), Color(0xFFFFFFFF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(purpleGradient)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp)) // Balance
            Text(
                text = "تحديد المستوى",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E1B4B)
            )
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color(0xFF1E1B4B)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.4f))

        // Main Instructions Card matching Screenshot 1
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "إزاي بيشتغل",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                val bullets = listOf(
                    "أسئلة من كل مستوى، من غير رد بعد كل سؤال — النتيجة في الآخر.",
                    "لو عديت مستوى (70٪) بيتفتح لك اللي بعده وتبدأ منه.",
                    "مش بيعلم أي حاجة «متقنة»: الإتقان بيتحسب من إجاباتك عبر الأيام، ومستواك الرسمي كمان.",
                    "كل إجابة بتتسجل دليل حقيقي — فمافيش داعي تخمن."
                )

                bullets.forEach { text ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "▪ ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = text,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155),
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big Purple Button matching Screenshot 1
        Button(
            onClick = onStartQuiz,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ابدأ الاختبار",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.6f))
    }
}

/**
 * 2. Questions Screen matching Screenshot 2:
 * 40 questions with top counter "X / 40", linear progress bar, question title, and 4 border cards.
 */
@Composable
fun PlacementTestQuestionView(
    currentQuestion: PlacementQuestion,
    currentIndex: Int,
    totalQuestions: Int = 40,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    onPreviousQuestion: () -> Unit,
    onBackToIntro: () -> Unit
) {
    val progress = (currentIndex + 1) / totalQuestions.toFloat()

    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF8F6FF), Color(0xFFFFFFFF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(purpleGradient)
    ) {
        // Top Navigation Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Progress Counter e.g. "1 / 40"
                Text(
                    text = "${currentIndex + 1} / $totalQuestions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )

                // Center: Title "تحديد المستوى"
                Text(
                    text = "تحديد المستوى",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B)
                )

                // Right: Back Arrow
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            onPreviousQuestion()
                        } else {
                            onBackToIntro()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "السابق",
                        tint = Color(0xFF1E1B4B)
                    )
                }
            }

            // Top Linear Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF6C5CE7),
                trackColor = Color(0xFFEDE9FE)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Level Indicator Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = when (currentQuestion.levelCategory) {
                    EnglishLevelCategory.LETTERS -> Color(0xFFF3E8FF)
                    EnglishLevelCategory.A1 -> Color(0xFFDCFCE7)
                    EnglishLevelCategory.A2 -> Color(0xFFFEF9C3)
                    EnglishLevelCategory.B1 -> Color(0xFFE0F2FE)
                    EnglishLevelCategory.B2 -> Color(0xFFFCE7F3)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${currentQuestion.levelCategory.badgeIcon} ${currentQuestion.levelCategory.nameAr} (${currentQuestion.levelCategory.cefrCode})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "السؤال ${currentIndex + 1} من $totalQuestions",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Title matching Screenshot 2
        Text(
            text = currentQuestion.questionTitle,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1E1B4B),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            textAlign = TextAlign.Start,
            lineHeight = 26.sp
        )

        if (currentQuestion.questionSub.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEEF2FF),
                border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = currentQuestion.questionSub,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4338CA),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4 Option Cards matching Screenshot 2 with crisp dark border
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            currentQuestion.options.forEach { optionText ->
                val isSelected = selectedAnswer == optionText

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onAnswerSelected(optionText) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFEDE9FE) else Color.White
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.2.dp,
                        color = if (isSelected) Color(0xFF6C5CE7) else Color(0xFF1E293B)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = optionText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF4338CA) else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Navigation Helper Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentIndex > 0) {
                OutlinedButton(
                    onClick = onPreviousQuestion,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("السؤال السابق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Text(
                text = "اختر إجابة للمتابعة تلقائياً ⚡",
                fontSize = 11.5.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 3. Complete Result Screen:
 * Calculates official level (Letters, A1, A2, B1, B2) with definitions,
 * success percentage, tier breakdown (70% rule), and unlocked stages!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementTestResultView(
    result: PlacementTestResult,
    onApplyAndStart: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF8F6FF), Color(0xFFFFFFFF))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تقرير تحديد المستوى الرسمي 🎯",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color(0xFF1E1B4B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F6FF))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(purpleGradient)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main Score & Level Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مستواك المعتمد في اللغة الإنجليزية",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${result.determinedLevel.badgeIcon} ${result.determinedLevel.nameAr}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "CEFR: ${result.determinedLevel.cefrCode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFEF08A)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Success Percentage Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "نسبة النجاح العامة:",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = "${result.overallPercentage}% (${result.totalCorrect} من ${result.totalQuestions} سؤال صح)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${result.overallPercentage}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF6C5CE7)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Level Definition Card ("تعريفه")
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تعريف مستواك الحالي (${result.determinedLevel.cefrCode})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                        }

                        Text(
                            text = result.determinedLevel.descriptionAr,
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569),
                            lineHeight = 20.sp
                        )

                        Divider(color = Color(0xFFF1F5F9))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "من أين ستبدأ في التطبيق؟",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        Text(
                            text = result.startRecommendation,
                            fontSize = 12.sp,
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 5 Levels Breakdown List (معيار الـ 70%)
            item {
                Text(
                    text = "تفصيل النتائج عبر مستويات اللغة الـ 5 (معيار 70% للعبور)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(result.levelBreakdown) { score ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(
                        1.dp,
                        if (score.isPassed) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = score.category.badgeIcon,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${score.category.nameAr} (${score.category.cefrCode})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1B4B)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${score.correctCount} من ${score.totalCount} أسئلة صحيحة (${score.percentage}%)",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                score.isPassed -> Color(0xFFDCFCE7)
                                score.category == result.determinedLevel -> Color(0xFFFEF3C7)
                                else -> Color(0xFFF1F5F9)
                            }
                        ) {
                            Text(
                                text = when {
                                    score.isPassed -> "مُجتاز ومفتوح ✅"
                                    score.category == result.determinedLevel -> "مستوى البداية 🎯"
                                    else -> "مغلق 🔒"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    score.isPassed -> Color(0xFF15803D)
                                    score.category == result.determinedLevel -> Color(0xFFB45309)
                                    else -> Color(0xFF64748B)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Unlocked Stages in Learning Path
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المراحل المفتوحة لك الآن في مسار التعلم 🔓:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }

                        val unlockedTitles = result.unlockedLevels.map { "${it.badgeIcon} ${it.nameAr}" }
                        Text(
                            text = unlockedTitles.joinToString(separator = " ➔ "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }

            // Action Buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onApplyAndStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تطبيق مستواي والبدء بالتعلم فوراً 🚀",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onRetakeQuiz,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة خوض الاختبار 🔄", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
