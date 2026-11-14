package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.FlashcardDeckEntity
import com.example.data.database.FlashcardEntity
import com.example.domain.ai.UniversalAnkiCard
import com.example.domain.anki.AnkiSpacedRepetition
import com.example.domain.utils.DocumentTextExtractor
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Base64

/**
 * Modern RTL Review Session Screen with SM-2 Adaptive Spaced Repetition,
 * Multiple Question Types (MCQ, True/False, Cloze, Basic Q&A),
 * dynamic Interval Badges and AI Tutor assistance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiReviewSessionScreen(
    deck: FlashcardDeckEntity,
    viewModel: MainViewModel,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        val dueCardsFlow = if (deck.id == 0L) {
            viewModel.weakFlashcards
        } else {
            viewModel.getDueCardsForDeck(deck.id)
        }
        val dueCards by dueCardsFlow.collectAsState(initial = emptyList())

        var currentCardIndex by remember { mutableIntStateOf(0) }
        var isAnswerRevealed by remember { mutableStateOf(false) }
        var selectedMcqOptionIndex by remember { mutableStateOf<Int?>(null) }
        var selectedTrueFalse by remember { mutableStateOf<Boolean?>(null) }

        // AI Tutor Sheet
        var showAiTutor by remember { mutableStateOf(false) }
        var tutorQuestion by remember { mutableStateOf("") }
        val tutorResponse by viewModel.aiTutorResponse.collectAsState()

        if (dueCards.isEmpty() || currentCardIndex >= dueCards.size) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(deck.title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (isEnglish) "Session Completed!" else "اكتملت جلسة المراجعة!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isEnglish)
                                    "Great job! All due cards in this deck have been reviewed."
                                else
                                    "أحسنت! تمت مراجعة جميع البطاقات المستحقة لهذه الحزمة بنجاح وفق خوارزمية التكرار المتباعد.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (isEnglish) "Return to Decks" else "العودة إلى الحزم", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            return@CompositionLocalProvider
        }

        val currentCard = dueCards[currentCardIndex]
        val totalCards = dueCards.size
        val progress = (currentCardIndex + 1).toFloat() / totalCards.toFloat()

        // Parse MCQ options if available
        val mcqOptions = remember(currentCard.optionsJson) {
            try {
                if (currentCard.optionsJson.isNotBlank()) {
                    val arr = JSONArray(currentCard.optionsJson)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    list
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(deck.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isEnglish) "Card ${currentCardIndex + 1} of $totalCards" else "البطاقة ${currentCardIndex + 1} من $totalCards",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Mastery Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "${currentCard.masteryPercentage}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                            // Ask AI Tutor Action
                            IconButton(onClick = { showAiTutor = true }) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = "AI Tutor",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    if (isAnswerRevealed) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "How easily did you recall this?" else "كيف كان تذكرك لهذه البطاقة؟",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val againInterval = AnkiSpacedRepetition.previewIntervalText(
                                    1, currentCard.repetitionNumber, currentCard.intervalDays,
                                    currentCard.easeFactor, currentCard.lapses, currentCard.masteryPercentage, !isEnglish
                                )
                                val hardInterval = AnkiSpacedRepetition.previewIntervalText(
                                    2, currentCard.repetitionNumber, currentCard.intervalDays,
                                    currentCard.easeFactor, currentCard.lapses, currentCard.masteryPercentage, !isEnglish
                                )
                                val goodInterval = AnkiSpacedRepetition.previewIntervalText(
                                    3, currentCard.repetitionNumber, currentCard.intervalDays,
                                    currentCard.easeFactor, currentCard.lapses, currentCard.masteryPercentage, !isEnglish
                                )
                                val easyInterval = AnkiSpacedRepetition.previewIntervalText(
                                    4, currentCard.repetitionNumber, currentCard.intervalDays,
                                    currentCard.easeFactor, currentCard.lapses, currentCard.masteryPercentage, !isEnglish
                                )

                                SpacedRepetitionButton(
                                    modifier = Modifier.weight(1f),
                                    label = if (isEnglish) "Again" else "مرة أخرى",
                                    interval = againInterval,
                                    buttonColor = Color(0xFFEF4444),
                                    onClick = {
                                        viewModel.reviewFlashcard(currentCard, 1)
                                        isAnswerRevealed = false
                                        selectedMcqOptionIndex = null
                                        selectedTrueFalse = null
                                        currentCardIndex++
                                    }
                                )

                                SpacedRepetitionButton(
                                    modifier = Modifier.weight(1f),
                                    label = if (isEnglish) "Hard" else "صعب",
                                    interval = hardInterval,
                                    buttonColor = Color(0xFFF59E0B),
                                    onClick = {
                                        viewModel.reviewFlashcard(currentCard, 2)
                                        isAnswerRevealed = false
                                        selectedMcqOptionIndex = null
                                        selectedTrueFalse = null
                                        currentCardIndex++
                                    }
                                )

                                SpacedRepetitionButton(
                                    modifier = Modifier.weight(1f),
                                    label = if (isEnglish) "Good" else "جيد",
                                    interval = goodInterval,
                                    buttonColor = Color(0xFF10B981),
                                    onClick = {
                                        viewModel.reviewFlashcard(currentCard, 3)
                                        isAnswerRevealed = false
                                        selectedMcqOptionIndex = null
                                        selectedTrueFalse = null
                                        currentCardIndex++
                                    }
                                )

                                SpacedRepetitionButton(
                                    modifier = Modifier.weight(1f),
                                    label = if (isEnglish) "Easy" else "سهل",
                                    interval = easyInterval,
                                    buttonColor = Color(0xFF0EA5E9),
                                    onClick = {
                                        viewModel.reviewFlashcard(currentCard, 4)
                                        isAnswerRevealed = false
                                        selectedMcqOptionIndex = null
                                        selectedTrueFalse = null
                                        currentCardIndex++
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Button(
                                onClick = { isAnswerRevealed = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Show Answer" else "إظهار الإجابة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Flashcard Container Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Card Type Tag & Hint
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cardTypeLabel = when (currentCard.cardType.uppercase()) {
                                "MCQ" -> if (isEnglish) "Multiple Choice" else "اختيار من متعدد"
                                "TRUE_FALSE" -> if (isEnglish) "True / False" else "صح أم خطأ"
                                "CLOZE" -> if (isEnglish) "Fill in the blank" else "أكمل الفراغ"
                                else -> if (isEnglish) "Question & Answer" else "سؤال وجواب"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = cardTypeLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (currentCard.hint.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            currentCard.hint,
                                            fontSize = 11.sp,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Question Area based on Card Type
                        when (currentCard.cardType.uppercase()) {
                            "CLOZE" -> {
                                val (masked, answers) = AnkiSpacedRepetition.parseClozeCard(
                                    if (currentCard.clozeText.isNotBlank()) currentCard.clozeText else currentCard.question
                                )
                                Text(
                                    text = if (isAnswerRevealed) AnkiSpacedRepetition.revealClozeText(currentCard.clozeText.ifBlank { currentCard.question }) else masked,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 30.sp
                                )
                            }
                            else -> {
                                Text(
                                    text = currentCard.question,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 28.sp
                                )
                            }
                        }

                        if (currentCard.pronunciation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🔊 [ ${currentCard.pronunciation} ]",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Interactive Question Types Section
                        if (currentCard.cardType.uppercase() == "MCQ" && mcqOptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                mcqOptions.forEachIndexed { index, option ->
                                    val isSelected = selectedMcqOptionIndex == index
                                    val isCorrect = index == currentCard.correctAnswerIndex
                                    val optBorderColor = when {
                                        isAnswerRevealed && isCorrect -> Color(0xFF10B981)
                                        isAnswerRevealed && isSelected && !isCorrect -> Color(0xFFEF4444)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    }
                                    val optBgColor = when {
                                        isAnswerRevealed && isCorrect -> Color(0xFF10B981).copy(alpha = 0.15f)
                                        isAnswerRevealed && isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = optBgColor,
                                        border = BorderStroke(1.5.dp, optBorderColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedMcqOptionIndex = index
                                                isAnswerRevealed = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val letter = when (index) {
                                                0 -> if (isEnglish) "A" else "أ"
                                                1 -> if (isEnglish) "B" else "ب"
                                                2 -> if (isEnglish) "C" else "ج"
                                                3 -> if (isEnglish) "D" else "د"
                                                else -> "${index + 1}"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(optBorderColor.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(letter, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = optBorderColor)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = option,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isAnswerRevealed) {
                                                if (isCorrect) {
                                                    Icon(Icons.Default.Check, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                                } else if (isSelected) {
                                                    Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (currentCard.cardType.uppercase() == "TRUE_FALSE") {
                            Spacer(modifier = Modifier.height(16.dp))
                            val isCorrectTrue = currentCard.correctAnswerIndex == 0 || currentCard.answer.contains("صحيح") || currentCard.answer.equals("true", ignoreCase = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // True Button
                                val trueBorder = when {
                                    isAnswerRevealed && isCorrectTrue -> Color(0xFF10B981)
                                    isAnswerRevealed && selectedTrueFalse == true && !isCorrectTrue -> Color(0xFFEF4444)
                                    selectedTrueFalse == true -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = trueBorder.copy(alpha = 0.12f),
                                    border = BorderStroke(1.5.dp, trueBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedTrueFalse = true
                                            isAnswerRevealed = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = trueBorder, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isEnglish) "True" else "صحيح", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // False Button
                                val falseBorder = when {
                                    isAnswerRevealed && !isCorrectTrue -> Color(0xFF10B981)
                                    isAnswerRevealed && selectedTrueFalse == false && isCorrectTrue -> Color(0xFFEF4444)
                                    selectedTrueFalse == false -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = falseBorder.copy(alpha = 0.12f),
                                    border = BorderStroke(1.5.dp, falseBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedTrueFalse = false
                                            isAnswerRevealed = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = falseBorder, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isEnglish) "False" else "خطأ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        // Answer Revelation Section
                        AnimatedVisibility(
                            visible = isAnswerRevealed,
                            enter = fadeIn() + expandVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 20.dp)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    thickness = 1.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (isEnglish) "Answer:" else "الإجابة النموذجية:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = currentCard.answer,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (currentCard.englishExample.isNotBlank() || currentCard.arabicExample.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            if (currentCard.englishExample.isNotBlank()) {
                                                Text(
                                                    text = "💡 ${currentCard.englishExample}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (currentCard.arabicExample.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = currentCard.arabicExample,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (currentCard.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "📌 ${currentCard.notes}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Tutor Bottom Sheet
        if (showAiTutor) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAiTutor = false
                    viewModel.clearAiTutorResponse()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isEnglish) "AI Study Tutor (Gemini)" else "المعلم الذكي (Gemini)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isEnglish) "Ask for explanations or mnemonics" else "اسأل عن توضيح أو طريقة حفظ أو أمثلة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tutorQuestion,
                        onValueChange = { tutorQuestion = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Your question about this card..." else "سؤالك حول هذه البطاقة...") },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.askAiTutor(currentCard, tutorQuestion) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Ask Tutor" else "استشر المعلم الذكي")
                    }

                    if (tutorResponse != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = tutorResponse!!,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SpacedRepetitionButton(
    modifier: Modifier = Modifier,
    label: String,
    interval: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(14.dp),
        color = buttonColor.copy(alpha = 0.15f),
        border = BorderStroke(1.5.dp, buttonColor.copy(alpha = 0.6f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = interval,
                fontSize = 11.sp,
                color = buttonColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Modern AI Scanner & Multi-format Document Card Creator Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerWorkflowScreen(
    viewModel: MainViewModel,
    bitmap: Bitmap?,
    targetDeck: FlashcardDeckEntity?,
    decks: List<FlashcardDeckEntity>,
    isEnglish: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var generatedCards by remember { mutableStateOf<List<UniversalAnkiCard>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    // Selected Deck
    var selectedDeckId by remember { mutableStateOf(targetDeck?.id ?: decks.firstOrNull()?.id ?: 0L) }
    var newDeckTitle by remember { mutableStateOf("") }
    var isCreatingNewDeck by remember { mutableStateOf(decks.isEmpty()) }

    // Card Type Selections
    var includeBasic by remember { mutableStateOf(true) }
    var includeMcq by remember { mutableStateOf(true) }
    var includeTrueFalse by remember { mutableStateOf(true) }
    var includeCloze by remember { mutableStateOf(true) }
    var cardCount by remember { mutableIntStateOf(6) }

    // Direct text / Document text
    var customText by remember { mutableStateOf("") }

    // Current active bitmap (either passed initially or captured in this screen)
    var currentBitmap by remember { mutableStateOf<Bitmap?>(bitmap) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { previewBitmap: Bitmap? ->
        if (previewBitmap != null) {
            currentBitmap = previewBitmap
        } else {
            Toast.makeText(context, if (isEnglish) "No image captured" else "لم يتم التقاط صورة", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists() && tempCameraFile!!.length() > 0) {
            val bmp = BitmapFactory.decodeFile(tempCameraFile!!.absolutePath)
            if (bmp != null) {
                currentBitmap = bmp
            } else {
                cameraPreviewLauncher.launch(null)
            }
        } else {
            cameraPreviewLauncher.launch(null)
        }
    }

    fun launchCamera() {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasCamPermission) {
            try {
                val f = ImageStorageHelper.createTempImageFile(context)
                tempCameraFile = f
                val u = ImageStorageHelper.getUriForFile(context, f)
                cameraLauncher.launch(u)
            } catch (e: Exception) {
                cameraPreviewLauncher.launch(null)
            }
        } else {
            cameraPreviewLauncher.launch(null)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            cameraPreviewLauncher.launch(null)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bmp != null) {
                    currentBitmap = bmp
                }
            } catch (e: Exception) {
                Toast.makeText(context, if (isEnglish) "Failed to load image" else "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun processContent() {
        isProcessing = true
        errorMessage = ""
        coroutineScope.launch {
            try {
                val activeBmp = currentBitmap
                val base64Image = if (activeBmp != null) {
                    val stream = ByteArrayOutputStream()
                    activeBmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                } else null

                val requestedTypes = mutableListOf<String>()
                if (includeBasic) requestedTypes.add("BASIC")
                if (includeMcq) requestedTypes.add("MCQ")
                if (includeTrueFalse) requestedTypes.add("TRUE_FALSE")
                if (includeCloze) requestedTypes.add("CLOZE")
                if (requestedTypes.isEmpty()) requestedTypes.add("BASIC")

                val apiKey = viewModel.userSettings.value.geminiApiKey
                val result = viewModel.aiRepository.generateUniversalFlashcardsFromContent(
                    rawText = customText.ifBlank { null },
                    imageBytesBase64 = base64Image,
                    requestedCardTypes = requestedTypes,
                    cardCount = cardCount,
                    customApiKey = apiKey
                )

                if (result.isSuccess) {
                    generatedCards = result.getOrNull() ?: emptyList()
                    if (generatedCards.isEmpty()) {
                        errorMessage = if (isEnglish) "No cards could be extracted." else "لم يتم العثور على محتوى كافٍ لاستخراج البطاقات."
                    }
                } else {
                    errorMessage = "${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "حدث خطأ غير متوقع."
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveCards() {
        coroutineScope.launch {
            val finalDeckId = if (isCreatingNewDeck || selectedDeckId == 0L) {
                val title = if (newDeckTitle.isNotBlank()) newDeckTitle.trim() else if (isEnglish) "AI Smart Deck ✨" else "حزمة ذكية بالذكاء الاصطناعي ✨"
                viewModel.createFlashcardDeck(title, if (isEnglish) "Created with Gemini AI" else "تم إنشاؤها بالذكاء الاصطناعي")
                // Fetch newly created deck
                viewModel.flashcardDecks.value.firstOrNull { it.title == title }?.id ?: 1L
            } else {
                selectedDeckId
            }

            generatedCards.forEach { card ->
                val optsJson = if (card.options.isNotEmpty()) JSONArray(card.options).toString() else ""
                viewModel.insertFlashcard(
                    FlashcardEntity(
                        deckId = finalDeckId,
                        cardType = card.cardType,
                        question = card.question,
                        answer = card.answer,
                        hint = card.hint,
                        clozeText = card.clozeText,
                        optionsJson = optsJson,
                        correctAnswerIndex = card.correctAnswerIndex,
                        englishExample = card.englishExample,
                        arabicExample = card.arabicExample,
                        pronunciation = card.pronunciation,
                        notes = card.explanation,
                        state = "NEW",
                        intervalDays = 1,
                        repetitionNumber = 0,
                        easeFactor = 2.5f
                    )
                )
            }
            Toast.makeText(context, if (isEnglish) "Saved ${generatedCards.size} cards!" else "تم حفظ ${generatedCards.size} بطاقة بنجاح!", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessing = true
                val res = DocumentTextExtractor.extractTextFromUri(context, uri)
                isProcessing = false
                res.fold(
                    onSuccess = { text ->
                        customText = text
                        Toast.makeText(context, if (isEnglish) "Extracted text from document!" else "تم استخراج النص من الملف بنجاح!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { err ->
                        Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "AI Flashcard Studio" else "استوديو توليد البطاقات بالذكاء الاصطناعي", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (generatedCards.isNotEmpty()) {
                        Button(
                            onClick = { saveCards() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Save All" else "حفظ الكل")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (generatedCards.isEmpty() && !isProcessing) {
                // Input Options Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEnglish) "1. Select Source Content" else "1. اختر مصدر المحتوى الدراسي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (currentBitmap != null) {
                            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                                Image(
                                    bitmap = currentBitmap!!.asImageBitmap(),
                                    contentDescription = "Scanned Content",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                IconButton(
                                    onClick = { currentBitmap = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasCamPermission) {
                                        launchCamera()
                                    } else {
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Camera" else "الكاميرا", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Gallery" else "المعرض", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { docPickerLauncher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Doc" else "ملف", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            label = { Text(if (isEnglish) "Or type / paste study text here..." else "أو اكتب / الصق النص الدراسي هنا...") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card Type Filters
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEnglish) "2. Question Types to Generate" else "2. أنواع الأسئلة المراد توليدها",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeBasic, onCheckedChange = { includeBasic = it })
                            Text(if (isEnglish) "Basic Q&A (سؤال وجواب)" else "سؤال وجواب (Basic)", fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeMcq, onCheckedChange = { includeMcq = it })
                            Text(if (isEnglish) "Multiple Choice (اختيار من متعدد)" else "اختيار من متعدد (MCQ)", fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeTrueFalse, onCheckedChange = { includeTrueFalse = it })
                            Text(if (isEnglish) "True / False (صح أم خطأ)" else "صح أم خطأ (True / False)", fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeCloze, onCheckedChange = { includeCloze = it })
                            Text(if (isEnglish) "Fill in the blank (أكمل الفراغ)" else "أكمل الفراغ (Cloze)", fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isEnglish) "Number of Cards: $cardCount" else "عدد البطاقات: $cardCount", fontWeight = FontWeight.Bold)
                            Slider(
                                value = cardCount.toFloat(),
                                onValueChange = { cardCount = it.toInt() },
                                valueRange = 3f..15f,
                                steps = 11,
                                modifier = Modifier.width(180.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target Deck Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEnglish) "3. Target Flashcard Deck" else "3. الحزمة المستهدفة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (decks.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !isCreatingNewDeck, onClick = { isCreatingNewDeck = false })
                                Text(if (isEnglish) "Existing Deck" else "حزمة موجودة")
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(selected = isCreatingNewDeck, onClick = { isCreatingNewDeck = true })
                                Text(if (isEnglish) "Create New Deck" else "إنشاء حزمة جديدة")
                            }
                        }

                        if (isCreatingNewDeck || decks.isEmpty()) {
                            OutlinedTextField(
                                value = newDeckTitle,
                                onValueChange = { newDeckTitle = it },
                                label = { Text(if (isEnglish) "New Deck Title" else "اسم الحزمة الجديدة") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { processContent() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "Generate Smart Flashcards" else "توليد البطاقات الذكية الآن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (isEnglish) "Gemini AI is crafting your flashcards..." else "الذكاء الاصطناعي (Gemini) يحلل ويصمم البطاقات...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "Applying educational spaced repetition formatting..." else "يتم صياغة أسئلة الاختيار من متعدد، الفراغات، والصح والخطأ...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (generatedCards.isNotEmpty()) {
                Text(
                    text = if (isEnglish) "Generated Cards (${generatedCards.size})" else "البطاقات المولدة (${generatedCards.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                generatedCards.forEachIndexed { index, card ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = card.cardType,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text("#${index + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(card.question, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("✓ ${card.answer}", color = Color(0xFF10B981), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            if (card.explanation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(card.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card Browser Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardBrowserScreen(
    deck: FlashcardDeckEntity,
    viewModel: MainViewModel,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        val cards: List<FlashcardEntity> by viewModel.getCardsForDeckFlow(deck.id).collectAsState(initial = emptyList())
        var searchQuery by remember { mutableStateOf("") }
        var showAddDialog by remember { mutableStateOf(false) }

        val filteredCards = cards.filter {
            it.question.contains(searchQuery, ignoreCase = true) ||
            it.answer.contains(searchQuery, ignoreCase = true) ||
            it.cardType.contains(searchQuery, ignoreCase = true)
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text((if (isEnglish) "Browser: " else "متصفح: ") + deck.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add Card")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isEnglish) "Search cards..." else "البحث في البطاقات...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (filteredCards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isEnglish) "No cards found" else "لا توجد بطاقات مطابقة",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCards) { card ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        ) {
                                            Text(
                                                text = card.cardType,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(card.question, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(card.answer, color = Color(0xFF10B981), fontSize = 14.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${card.masteryPercentage}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        IconButton(onClick = { viewModel.deleteFlashcard(card) }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            var newQ by remember { mutableStateOf("") }
            var newA by remember { mutableStateOf("") }
            var newHint by remember { mutableStateOf("") }
            var newType by remember { mutableStateOf("BASIC") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(if (isEnglish) "Create Card" else "إضافة بطاقة يدوياً") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newQ,
                            onValueChange = { newQ = it },
                            label = { Text(if (isEnglish) "Question / Cloze" else "السؤال أو النص المحجوب") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newA,
                            onValueChange = { newA = it },
                            label = { Text(if (isEnglish) "Answer" else "الإجابة") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newHint,
                            onValueChange = { newHint = it },
                            label = { Text(if (isEnglish) "Hint (optional)" else "تلميح (اختياري)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newQ.isNotBlank() && newA.isNotBlank()) {
                            viewModel.insertFlashcard(
                                FlashcardEntity(
                                    deckId = deck.id,
                                    cardType = newType,
                                    question = newQ.trim(),
                                    answer = newA.trim(),
                                    hint = newHint.trim(),
                                    state = "NEW",
                                    intervalDays = 1
                                )
                            )
                            showAddDialog = false
                        }
                    }) {
                        Text(if (isEnglish) "Add" else "إضافة")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(if (isEnglish) "Cancel" else "إلغاء")
                    }
                }
            )
        }
    }
}
