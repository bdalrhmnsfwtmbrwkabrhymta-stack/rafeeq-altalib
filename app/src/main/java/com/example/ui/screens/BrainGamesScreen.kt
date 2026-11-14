package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class PuzzleType(val title: String, val icon: String, val themeColor: Color) {
    PHYSICS("فيزياء وميكانيكا ⚙️", "⚙️", Color(0xFF00897B)),
    CIPHERS("فك شفرات وخزائن 🔐", "🔐", Color(0xFFE65100)),
    LOGIC("استنتاج وتفكير منطقي 🧠", "🧠", Color(0xFF1E88E5)),
    EQUATIONS("معادلات ورموز رياضية 📐", "📐", Color(0xFF673AB7)),
    SPATIAL("هندسة ومرايا وفضاء 🔮", "🔮", Color(0xFFC2185B))
}

data class GameStage(
    val levelNumber: Int,
    val type: PuzzleType,
    val title: String,
    val storyDescription: String,
    val visualDiagramLines: List<String>,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val hint: String,
    val xpReward: Int = 50
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainGamesScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChess: () -> Unit = {}
) {
    // Top Section: 0 -> IQ Riddles, 1 -> Chess & Tactics
    var brainGameSection by remember { mutableIntStateOf(0) }

    if (brainGameSection == 1) {
        androidx.activity.compose.BackHandler {
            brainGameSection = 0
        }
        com.example.ui.screens.chess.ChessScreen(
            onNavigateBack = { brainGameSection = 0 }
        )
        return
    }

    // Game Progression States
    var currentLevel by remember { mutableIntStateOf(1) }
    var highestUnlockedLevel by remember { mutableIntStateOf(1) }
    var totalCoins by remember { mutableIntStateOf(100) }
    var totalXp by remember { mutableIntStateOf(0) }
    var hearts by remember { mutableIntStateOf(3) }
    var currentStreak by remember { mutableIntStateOf(0) }
    
    // Level & Question State
    val allStages = remember { getAllGameStages() }
    val currentStage = allStages.getOrElse((currentLevel - 1).coerceIn(0, allStages.size - 1)) { allStages.first() }

    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var isAnswerCorrect by remember { mutableStateOf(false) }
    var showExplanation by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var showStageSelectDialog by remember { mutableStateOf(false) }
    var eliminatedOptions by remember { mutableStateOf(setOf<Int>()) }

    // Timer per stage
    var timerSeconds by remember { mutableIntStateOf(45) }
    var isTimerActive by remember { mutableStateOf(true) }

    // Stage Stars tracker (Level -> Stars 1..3)
    val stageStars = remember { mutableStateMapOf<Int, Int>() }

    // Reset when level changes
    LaunchedEffect(currentLevel) {
        timerSeconds = 45
        isTimerActive = true
        selectedOptionIndex = null
        isAnswered = false
        isAnswerCorrect = false
        showExplanation = false
        showHint = false
        eliminatedOptions = emptySet()

        while (isTimerActive && timerSeconds > 0) {
            delay(1000L)
            if (!isAnswered) {
                timerSeconds--
            }
        }
        if (timerSeconds == 0 && !isAnswered) {
            isAnswered = true
            isAnswerCorrect = false
            currentStreak = 0
            if (hearts > 0) hearts--
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "تحدي ألعاب الذكاء والألغاز 🎮",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // Stage selector button
                    IconButton(onClick = { showStageSelectDialog = true }) {
                        Icon(Icons.Default.GridView, contentDescription = "قائمة المراحل", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Coins badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFB300).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙 $totalCoins", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 30.dp)
        ) {
            // Section Switcher Row: IQ Riddles vs Pro Chess
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = brainGameSection == 0,
                        onClick = { brainGameSection = 0 },
                        label = { Text("🧠 تحدي ألغاز التفكير والفيزياء", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = brainGameSection == 1,
                        onClick = {
                            if (onNavigateToChess != {}) {
                                onNavigateToChess()
                            } else {
                                brainGameSection = 1
                            }
                        },
                        label = { Text("♟️ شطرنج المحترفين والألغاز", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Header: Level Progress Bar + Lives (Hearts) + Streak
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = currentStage.type.themeColor.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, currentStage.type.themeColor.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current Stage Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = currentStage.type.themeColor
                            ) {
                                Text(
                                    text = "المرحلة ${currentStage.levelNumber} / ${allStages.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Hearts
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                for (i in 1..3) {
                                    val hasHeart = i <= hearts
                                    Text(
                                        text = if (hasHeart) "❤️" else "🖤",
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(horizontal = 1.dp)
                                    )
                                }
                            }

                            // Timer Indicator
                            Surface(
                                shape = CircleShape,
                                color = if (timerSeconds < 10) MaterialTheme.colorScheme.errorContainer else currentStage.type.themeColor.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$timerSeconds",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = if (timerSeconds < 10) MaterialTheme.colorScheme.error else currentStage.type.themeColor
                                    )
                                }
                            }

                            // Streak
                            Text(
                                text = "🔥 $currentStreak",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stage progress linear indicator
                        LinearProgressIndicator(
                            progress = { (currentLevel.toFloat() / allStages.size).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = currentStage.type.themeColor,
                            trackColor = currentStage.type.themeColor.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Power-ups / Lifelines Row (وسائل مساعدة)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 50:50 Lifeline
                    OutlinedButton(
                        onClick = {
                            if (!isAnswered && eliminatedOptions.isEmpty() && totalCoins >= 20) {
                                totalCoins -= 20
                                val wrongIndices = currentStage.options.indices.filter { it != currentStage.correctIndex }.shuffled().take(2)
                                eliminatedOptions = wrongIndices.toSet()
                            }
                        },
                        enabled = !isAnswered && eliminatedOptions.isEmpty() && totalCoins >= 20,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("✂️ 50:50 (20🪙)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Smart Hint Lifeline
                    OutlinedButton(
                        onClick = {
                            if (!showHint && totalCoins >= 15) {
                                totalCoins -= 15
                                showHint = true
                            } else {
                                showHint = !showHint
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(if (showHint) "💡 إخفاء" else "💡 تلميح (15🪙)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Add Time (+20s)
                    OutlinedButton(
                        onClick = {
                            if (!isAnswered && totalCoins >= 10) {
                                totalCoins -= 10
                                timerSeconds += 20
                            }
                        },
                        enabled = !isAnswered && totalCoins >= 10,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("⏳ +20ث (10🪙)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Main Puzzle / Stage Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(3.dp),
                    border = BorderStroke(1.5.dp, currentStage.type.themeColor.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Title and Category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentStage.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentStage.type.themeColor
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = currentStage.type.themeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = currentStage.type.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = currentStage.type.themeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Story / Question description
                        Text(
                            text = currentStage.storyDescription,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual Diagram / Puzzle Arena Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentStage.visualDiagramLines.forEach { line ->
                                    Text(
                                        text = line,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "اختر الإجابة لحل اللغز والانتقال للمرحلة التالية:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Options Grid
                        currentStage.options.forEachIndexed { index, option ->
                            val isEliminated = eliminatedOptions.contains(index)
                            val isSelected = selectedOptionIndex == index
                            val isCorrect = index == currentStage.correctIndex

                            val optionBg = when {
                                isAnswered && isCorrect -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                isAnswered && isSelected && !isCorrect -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                isSelected -> currentStage.type.themeColor.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            val optionBorder = when {
                                isAnswered && isCorrect -> Color(0xFF2E7D32)
                                isAnswered && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                isSelected -> currentStage.type.themeColor
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            }

                            if (!isEliminated) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isAnswered) {
                                            selectedOptionIndex = index
                                            isAnswered = true
                                            isTimerActive = false

                                            if (index == currentStage.correctIndex) {
                                                isAnswerCorrect = true
                                                val starsEarned = when {
                                                    timerSeconds > 25 && eliminatedOptions.isEmpty() -> 3
                                                    timerSeconds > 10 -> 2
                                                    else -> 1
                                                }
                                                stageStars[currentStage.levelNumber] = starsEarned
                                                
                                                val coinsEarned = 15 + (currentStreak * 3)
                                                totalCoins += coinsEarned
                                                totalXp += currentStage.xpReward
                                                currentStreak++

                                                // Unlock next stage
                                                if (currentLevel >= highestUnlockedLevel && currentLevel < allStages.size) {
                                                    highestUnlockedLevel = currentLevel + 1
                                                }
                                            } else {
                                                isAnswerCorrect = false
                                                currentStreak = 0
                                                if (hearts > 0) hearts--
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = optionBg,
                                    border = BorderStroke(if (isSelected || (isAnswered && isCorrect)) 2.dp else 1.dp, optionBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected || (isAnswered && isCorrect)) optionBorder else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${('A'.code + index).toChar()}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = if (isSelected || (isAnswered && isCorrect)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = option,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (isAnswered) {
                                            if (isCorrect) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "صحيح", tint = Color(0xFF2E7D32))
                                            } else if (isSelected) {
                                                Icon(Icons.Default.Cancel, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stage Outcome Action: Next Level / Retry
                        if (isAnswered) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAnswerCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                ),
                                border = BorderStroke(1.dp, if (isAnswerCorrect) Color(0xFF81C784) else Color(0xFFE57373))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isAnswerCorrect) "🎉 إجابة عبقرية صحيحة! (+${currentStage.xpReward} XP)" else "❌ إجابة غير صحيحة، حاول مجدداً!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isAnswerCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )

                                    if (isAnswerCorrect) {
                                        val stars = stageStars[currentStage.levelNumber] ?: 1
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            for (s in 1..3) {
                                                Text(
                                                    text = if (s <= stars) "⭐" else "☆",
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        OutlinedButton(
                                            onClick = { showExplanation = !showExplanation },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(if (showExplanation) "إخفاء الشرح" else "📖 شرح اللغز والحل", fontSize = 12.sp)
                                        }

                                        if (isAnswerCorrect && currentLevel < allStages.size) {
                                            Button(
                                                onClick = {
                                                    currentLevel++
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("المرحلة التالية ➔", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        } else if (!isAnswerCorrect) {
                                            Button(
                                                onClick = {
                                                    isAnswered = false
                                                    selectedOptionIndex = null
                                                    timerSeconds = 45
                                                    isTimerActive = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("إعادة المحاولة 🔄", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Hint Card
                        AnimatedVisibility(visible = showHint) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFF9C4),
                                border = BorderStroke(1.dp, Color(0xFFFBC02D))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF57F17))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = currentStage.hint,
                                        fontSize = 12.sp,
                                        color = Color(0xFF424242),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Detailed Explanation Card
                        AnimatedVisibility(visible = showExplanation) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "تفسير اللغز العلمي والمنطقي:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentStage.explanation,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Stats & Player Rank
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "رتبة العبقرية: ${getPlayerRank(totalXp)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "إجمالي نقاط الخبرة (XP): $totalXp نقطة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showStageSelectDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("خريطة المراحل 🗺️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Stage Selection Dialog
    if (showStageSelectDialog) {
        AlertDialog(
            onDismissRequest = { showStageSelectDialog = false },
            title = {
                Text("خريطة المراحل والألغاز 🗺️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "المراحل المفتوحة (حل المرحلة لفتح التالية):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        itemsIndexed(allStages) { idx, stage ->
                            val levelNum = stage.levelNumber
                            val isUnlocked = levelNum <= highestUnlockedLevel
                            val isCurrent = levelNum == currentLevel
                            val stars = stageStars[levelNum] ?: 0

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isCurrent -> stage.type.themeColor
                                    isUnlocked -> stage.type.themeColor.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isUnlocked) stage.type.themeColor else Color.Gray.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = isUnlocked) {
                                        currentLevel = levelNum
                                        showStageSelectDialog = false
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (isUnlocked) {
                                        Text(
                                            text = "$levelNum",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isCurrent) Color.White else stage.type.themeColor
                                        )
                                        if (stars > 0) {
                                            Text(
                                                text = "⭐".repeat(stars),
                                                fontSize = 8.sp
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "مقفل",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStageSelectDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

private fun getPlayerRank(xp: Int): String {
    return when {
        xp >= 500 -> "بروفيسور الألغاز والفيزياء 👑"
        xp >= 300 -> "عالم استنتاج متقدم 🥇"
        xp >= 150 -> "محلل شفرات ومفكر 🥈"
        xp >= 50 -> "باحث ذكي صاعد 🥉"
        else -> "مبتدئ فضولي 🌱"
    }
}

// Built-in offline rich stages spanning Physics, Ciphers, Logic, Geometry, and Math
private fun getAllGameStages(): List<GameStage> {
    return listOf(
        GameStage(
            levelNumber = 1,
            type = PuzzleType.PHYSICS,
            title = "لغز دوران التروس والمسننات المتشابكة",
            storyDescription = "إذا دار الترس (A) في اتجاه عقارب الساعة ↻، في أي اتجاه سيدور الترس النهائي (D)؟",
            visualDiagramLines = listOf(
                "  [ ترس A ↻ ]",
                "       ↓ (متشابك)",
                "  [ ترس B ]",
                "       ↓ (متشابك)",
                "  [ ترس C ]",
                "       ↓ (متشابك)",
                "  [ ترس D = ؟ ]"
            ),
            options = listOf("عكس عقارب الساعة ↺", "في اتجاه عقارب الساعة ↻", "سيتوقف ولن يدور", "يدور في الاتجاهين بالتناوب"),
            correctIndex = 0,
            explanation = "قاعدة التروس المتشابكة: كل ترس يعكس اتجاه دوران الترس الذي يليه!\n• ترس A: يدور مع عقارب الساعة ↻\n• ترس B: يدور عكس عقارب الساعة ↺\n• ترس C: يدور مع عقارب الساعة ↻\n• ترس D: يدور عكس عقارب الساعة ↺!",
            hint = "كلما تشابك ترسان متعاشقان، ينعكس اتجاه الدوران بينهما."
        ),
        GameStage(
            levelNumber = 2,
            type = PuzzleType.CIPHERS,
            title = "فك شفرة الخزنة الرقمية المقفلة",
            storyDescription = "خزنة بنك بها قفل مكون من 3 أرقام [ X - Y - Z ]. تم ترك الملاحظات التالية لفك الرمز:",
            visualDiagramLines = listOf(
                "• [ 6 - 8 - 2 ]: رقم واحد صحيح وفي مكانه الصحيح.",
                "• [ 6 - 1 - 4 ]: رقم واحد صحيح لكن في مكان خاطئ.",
                "• [ 2 - 0 - 6 ]: رقمان صحيحان لكن في مكانين خاطئين.",
                "• [ 7 - 3 - 8 ]: لا يوجد أي رقم صحيح إطلاقاً.",
                "• [ 7 - 8 - 0 ]: رقم واحد صحيح في مكان خاطئ."
            ),
            options = listOf("0 - 4 - 2", "0 - 1 - 2", "6 - 4 - 2", "2 - 4 - 0"),
            correctIndex = 0,
            explanation = "من [7-3-8]: نستبعد 7 و 3 و 8 تماماً.\nمن [6-8-2] بدون 8: إما 6 أو 2 صحيح. إذا كان 6 صحيحاً في الأول، سيتعارض مع [6-1-4]، لذا الرقم 2 هو الصحيح وفي خانة الآحاد (الأخيرة: _ _ 2).\nمن [2-0-6]: الـ 2 صحيحة، والـ 0 صحيحة ومكانها في الخانة الأولى (0 _ 2).\nمن [6-1-4]: الرقم 4 صحيح وفي الخانة الوسطى => الرمز السري هو 0 - 4 - 2!",
            hint = "ابدأ باستبعاد الأرقام الخاطئة تماماً (7, 3, 8)."
        ),
        GameStage(
            levelNumber = 3,
            type = PuzzleType.PHYSICS,
            title = "لغز امتلاء خزانات المياه المتصلة",
            storyDescription = "يتم صب الماء بمعدل ثابت في الخزان الرئيسي (1). ما هو أول خزان سيمتلئ بالكامل حتى نهايته؟",
            visualDiagramLines = listOf(
                "       [ صنبور ماء 🚰 ]",
                "             ↓",
                "         [ خزان 1 ]",
                "          ↙     ↘",
                "     [ خزان 2 ] [ خزان 3 (أنبوبه مسدود من الأسفل) ]",
                "          ↓",
                "     [ خزان 4 ]"
            ),
            options = listOf("الخزان رقم (4)", "الخزان رقم (3)", "الخزان رقم (2)", "الخزان رقم (1)"),
            correctIndex = 0,
            explanation = "بما أن أنبوب الخزان 3 مسدود، سيتدفق الماء كله نحو الخزان 2 ثم ينزل فوراً إلى الخزان 4 في القاع قبل أن يمتلئ 2 أو 1. ولأن الخزان 4 مغلق من الأسفل وليس به تصريف، فسيمتلئ الخزان (4) أولاً بالكامل!",
            hint = "تتبع مسار الجاذبية للأنابيب المفتوحة من القاع."
        ),
        GameStage(
            levelNumber = 4,
            type = PuzzleType.EQUATIONS,
            title = "معادلة رموز الآلات والعمليات الحسابية",
            storyDescription = "احسب الناتج النهائي للمعادلة مع الانتباه للتفاصيل الدقيقة والرموز:",
            visualDiagramLines = listOf(
                "🤖 + 🤖 + 🤖 = 30",
                "🤖 + 🔋🔋 + 🔋🔋 = 18",
                "🔋🔋 - 💡💡 = 2",
                "💡 + 🤖 × 🔋 = ؟"
            ),
            options = listOf("11", "21", "22", "15"),
            correctIndex = 0,
            explanation = "• روبوت (🤖) = 10.\n• من السطر الثاني: 10 + 4 بطاريات = 18 => البطارية الواحدة (🔋) = 2 (البطاريتان = 4).\n• من السطر الثالث: 4 - مصباحان = 2 => المصباح الواحد (💡) = 1 (المصباحان = 2).\n• السطر الأخير: مصباح (1) + روبوت (10) × بطارية واحدة (1 أو 2) = 1 + (10 × 1) = 11 (الضرب أولاً: 10 × 1 = 10 ثم + 1 = 11)!",
            hint = "انتبه لأولويات العمليات: الضرب يسبق الجمع دائماً!"
        ),
        GameStage(
            levelNumber = 5,
            type = PuzzleType.LOGIC,
            title = "لغز جزيرة الفرسان والفرقاء (الصدق والكذب)",
            storyDescription = "قابلت شخصين (زيد وعمرو) على جزيرة حيث الفرسان يقولون الصدق دائماً، والفرقاء يكذبون دائماً. قال زيد: «أحدنا على الأقل كاذب». ماذا يكون زيد وعمرو؟",
            visualDiagramLines = listOf(
                "زيد قال: «أحدنا على الأقل كاذب»",
                "ما هي حقيقة الشخصين؟"
            ),
            options = listOf("زيد صادق، وعمرو كاذب", "كلاهما كاذبان", "كلاهما صادقان", "زيد كاذب، وعمرو صادق"),
            correctIndex = 0,
            explanation = "لو كان زيد كاذباً، لكانت عبارته («أحدنا كاذب») صحيحة، وهذا تناقض لأن الكاذب لا يقول الصدق! إذن زيد صادق (فارس). وبما أن كلامه صدق ويوجد كاذب، فإن عمرو هو الكاذب حتماً!",
            hint = "افترض أن زيد كاذب وانظر هل يحدث تناقض في عبارته."
        ),
        GameStage(
            levelNumber = 6,
            type = PuzzleType.PHYSICS,
            title = "لغز الميزان والكتل المجهولة على القمر",
            storyDescription = "إذا علمت أن الجاذبية على سطح القمر تساوي (1/6) جاذبية الأرض، وقمنا بوزن كتلة مقدارها 60 كجم باستخدام ميزان ذو كفتين (Beam Balance) على القمر، كم كجم من الأثقال نحتاج في الكفة المقابلة لتتزن؟",
            visualDiagramLines = listOf(
                "   [ كتلة 60 كجم ]  ⚖️  [ أثقال الموازنة = ؟ ]",
                "             (على سطح القمر)"
            ),
            options = listOf("60 كجم", "10 كجم", "360 كجم", "0 كجم (انعدام وزن)"),
            correctIndex = 0,
            explanation = "الميزان ذو الكفتين يقارن الكتلة بالكتلة، وتأثير الجاذبية ينخفض على الكفتين بنفس النسبة المتساوية (1/6)! لذلك ستتزن الكتلة مع 60 كجم تماماً كما على الأرض (بينما الميزان الزنبركي هو الذي يقرأ الوزن بالنيوتن)!",
            hint = "الميزان ذو الكفتين يقارن الكتل المتماثلة في نفس مجال الجاذبية."
        ),
        GameStage(
            levelNumber = 7,
            type = PuzzleType.SPATIAL,
            title = "لغز انعكاس الليزر عبر المرايا المتعامدة",
            storyDescription = "شعاع ليزر سقط بزاوية 30° على مرآة أفقية، ثم انعكس ليصطدم بمرآة رأسية عمودية عليها (90°). ما هي زاوية انعكاس الشعاع عن المرآة الرأسية؟",
            visualDiagramLines = listOf(
                "       | المرآة الرأسية",
                "       |     ↖ (شعاع خارج)",
                "       |    ↗ (شعاع منعكس)",
                "  _____|___↗ (زاوية السقوط 30°)",
                "     المرآة الأفقية"
            ),
            options = listOf("60°", "30°", "45°", "90°"),
            correctIndex = 0,
            explanation = "قانون الانعكاس: زاوية السقوط = زاوية الانعكاس.\nسقط الشعاع بزاوية 30° على الأفقية فانعكس بزاوية 30° مع العمود (أي 60° مع سطح المرآة). في المثلث القائم بين المرآتين المتعامدتين، تكون زاوية السقوط على المرآة الرأسية = 90° - 30° = 60°، وبالتالي زاوية الانعكاس عنها هي 60°!",
            hint = "مجموع زوايا المثلث القائم يساوي 180 درجة."
        ),
        GameStage(
            levelNumber = 8,
            type = PuzzleType.LOGIC,
            title = "لغز حبل الإشعال والـ 45 دقيقة",
            storyDescription = "لديك حبلان غير منتظمين، كل حبل يستغرق احتراقه بالكامل 60 دقيقة من طرف لآخر. كيف تقيس 45 دقيقة بدقة باستخدام إشعال الحبال؟",
            visualDiagramLines = listOf(
                "حبل 1: [ 🔥 ------------ 🔥 ] (مشعل من الطرفين)",
                "حبل 2: [ 🔥 ---------------- ] (مشعل من طرف واحد)"
            ),
            options = listOf(
                "إشعال الحبل 1 من طرفيه والحبل 2 من طرف، وعند احتراق 1 نشعل الطرف الآخر لـ 2",
                "إشعال الحبلين معاً من طرف واحد فقط",
                "قطع الحبل الأول إلى نصفين وإشعال النصفين",
                "إشعال الحبل الأول من المنتصف ثم إشعال الثاني"
            ),
            correctIndex = 0,
            explanation = "عند إشعال الحبل 1 من الطرفين، سيحترق في 30 دقيقة. في هذه اللحظة، يكون الحبل 2 قد احترق منه 30 دقيقة وتبقت فيه 30 دقيقة. نقوم فوراً بإشعال الطرف الثاني للحبل 2 ليحترق المتبقي في 15 دقيقة فقط! المجموع: 30 + 15 = 45 دقيقة بدقة متناهية!",
            hint = "إشعال الحبل من كلا الطرفين يقلل زمن احتراقه إلى النصف تماماً."
        ),
        GameStage(
            levelNumber = 9,
            type = PuzzleType.PHYSICS,
            title = "لغز الدائرة الكهربائية والمصابيح المتوازية",
            storyDescription = "في دائرة كهربائية بسيطة، 3 مصابيح متماثلة موصلة على التوالي ببطارية. إذا احترق المصباح الأوسط (B)، ماذا يحدث لإضاءة المصباحين (A) و (C)؟",
            visualDiagramLines = listOf(
                "  + --- [مصباح A] --- [مصباح B 💥] --- [مصباح C] --- -"
            ),
            options = listOf("تنطفئ جميع المصابيح فوراً", "تزداد إضاءة المصباحين A و C", "تظل إضاءة A و C كما هي", "ينفجر المصباح A"),
            correctIndex = 0,
            explanation = "في التوصيل على التوالي، يمر تيار واحد فقط في مسار مغلق متصل. عند احتراق أي مصباح ينفتح المسار الكهربائي بالكامل فينقطع التيار وتنطفئ كل المصابيح في الدائرة!",
            hint = "التوصيل على التوالي يشترط مساراً مغلقاً واحداً لمرور الإلكترونات."
        ),
        GameStage(
            levelNumber = 10,
            type = PuzzleType.EQUATIONS,
            title = "معادلة المصفوفة السحرية والرموز الجبرية العبقرية",
            storyDescription = "استنتج القيمة المفقودة في المعادلة الجبرية التالية:",
            visualDiagramLines = listOf(
                "2 ⊕ 3 = 13  (حيث 2² + 3² = 13)",
                "3 ⊕ 4 = 25  (حيث 3² + 4² = 25)",
                "4 ⊕ 5 = 41  (حيث 4² + 5² = 41)",
                "5 ⊕ 6 = ؟"
            ),
            options = listOf("61", "55", "72", "66"),
            correctIndex = 0,
            explanation = "قاعدة العملية الحسابية الخاصة ⊕ هي جمع مربعي العددين:\n5 ⊕ 6 = 5² + 6² = 25 + 36 = 61!",
            hint = "طبق نفس القاعدة: مربع العدد الأول + مربع العدد الثاني."
        )
    )
}
