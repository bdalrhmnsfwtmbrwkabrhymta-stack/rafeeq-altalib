package com.example.ui.screens.english

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.english.*
import java.util.Locale

/**
 * Interactive Lesson Player Screen matching Screenshots 2, 3, 4, 5, 6
 */
@Composable
fun EnglishInteractiveLessonScreen(
    unitId: String = "letter_a",
    onNavigateBack: () -> Unit,
    onLessonCompleted: (Int) -> Unit,
    speak: (String, Boolean) -> Unit // (text, isSlow)
) {
    val context = LocalContext.current
    val slides = remember(unitId) { EnglishCurriculumRepository.getLessonSlidesForUnit(unitId) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides.getOrElse(currentSlideIndex) { slides[0] }

    // Exercise state
    var userInputText by remember(currentSlideIndex) { mutableStateOf("") }
    var showHint by remember(currentSlideIndex) { mutableStateOf(false) }
    var exerciseStatus by remember(currentSlideIndex) { mutableStateOf<Boolean?>(null) } // null: not checked, true: correct, false: wrong

    // Dialog for "مش فاهم"
    var showNotUnderstoodDialog by remember { mutableStateOf(false) }

    // Speech-To-Text State
    var isListening by remember { mutableStateOf(false) }
    var recognizedSpokenText by remember { mutableStateOf("") }
    var speechEvaluationResult by remember { mutableStateOf<Boolean?>(null) }

    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F7FF), Color(0xFFF4F0FF), Color(0xFFFFFFFF))
    )

    val progress = (currentSlideIndex + 1) / slides.size.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(purpleGradient)
    ) {
        // ==========================================
        // Top Navigation Bar (Matches Screenshots 2, 4, 5, 6)
        // ==========================================
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Counter: e.g. "١ / ٥" or "١ / ٦" or "٣ / ٤"
                Text(
                    text = "${currentSlideIndex + 1} / ${slides.size}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )

                // Center Title
                Text(
                    text = currentSlide.lessonTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B)
                )

                // Right Back Arrow
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color(0xFF1E1B4B)
                    )
                }
            }

            // Top Linear Indicator (Thin purple line at the top)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp),
                color = Color(0xFF7C5CFC),
                trackColor = Color(0xFFE9D8FD).copy(alpha = 0.5f)
            )
        }

        // ==========================================
        // Main Scrollable Content
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main Rule / Explanation Card (Matches Screenshots 2, 4, 5, 6)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentSlide.mainRuleTitle.isNotBlank()) {
                        Text(
                            text = currentSlide.mainRuleTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )
                    }

                    currentSlide.ruleBullets.forEach { bulletText ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp, end = 8.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7C5CFC))
                            )
                            Text(
                                text = bulletText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E1B4B),
                                lineHeight = 21.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (currentSlide.pointsReward > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentSlide.pointsReward} نقاط",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // ==========================================
            // CONTENT TYPE 1: LETTER CARDS (Screenshots 5 & 6)
            // ==========================================
            if (currentSlide.letterCards.isNotEmpty()) {
                currentSlide.letterCards.forEach { card ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Dual Audio Buttons (اسمعها ببطء & اسمع)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Slow Audio Button (اسمعها ببطء)
                                IconButton(
                                    onClick = { speak(card.exampleWord.ifBlank { card.letter }, true) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SlowMotionVideo,
                                        contentDescription = "اسمعها ببطء",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Normal Audio Button (اسمع)
                                IconButton(
                                    onClick = { speak(card.exampleWord.ifBlank { card.letter }, false) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "اسمع",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Center: Main Big Letter / Word
                            Text(
                                text = card.exampleWord.ifBlank { card.letter },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                textAlign = TextAlign.Start
                            )

                            // Right: Subtext (IPA & Description)
                            Column(horizontalAlignment = Alignment.End) {
                                if (card.ipa.isNotBlank()) {
                                    Text(
                                        text = card.ipa,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1B4B)
                                    )
                                }
                                Text(
                                    text = card.arabicMeaning.ifBlank { card.arabicExplanation },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONTENT TYPE 2: PHONICS CARDS (Screenshot 4: أول تلات كلمات)
            // ==========================================
            if (currentSlide.phonicsCards.isNotEmpty()) {
                currentSlide.phonicsCards.forEach { card ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Dual Audio Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { speak(card.word, true) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SlowMotionVideo,
                                        contentDescription = "اسمعها ببطء",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { speak(card.word, false) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "اسمع",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Center: Word
                            Text(
                                text = card.word,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                            )

                            // Right: Subtext with Phonics Blending (ك + آ + ت)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = card.ipa,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1B4B)
                                )
                                Text(
                                    text = card.arabicMeaning,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Text(
                                    text = card.soundBreakdownArabic,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C5CFC)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONTENT TYPE 3: SENTENCE CARDS (Screenshot 2: أول جملة)
            // ==========================================
            if (currentSlide.sentenceCards.isNotEmpty()) {
                currentSlide.sentenceCards.forEach { card ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Dual Audio Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { speak(card.sentence, true) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SlowMotionVideo,
                                        contentDescription = "اسمعها ببطء",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { speak(card.sentence, false) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "اسمع",
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Center: Full Sentence
                            Text(
                                text = card.sentence,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B),
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                            )

                            // Right: Arabic Translation & Grammar breakdown ("مين + فعل + اسم")
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = card.translationArabic,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1B4B)
                                )
                                Text(
                                    text = card.grammarBreakdown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7C5CFC)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONTENT TYPE 4: INPUT EXERCISE (Screenshot 3: الضمير I)
            // ==========================================
            if (currentSlide.slideType == LessonSlideType.INPUT_EXERCISE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Question Prompt e.g. "اكتب «أنا» بالإنجليزي"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentSlide.exercisePrompt,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )

                        // Hint Bulb (تلميح)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHint = !showHint }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تلميح",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C5CFC)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "تلميح",
                                tint = Color(0xFF7C5CFC),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (showHint && currentSlide.exerciseHint.isNotBlank()) {
                        Surface(
                            color = Color(0xFFF3E8FF),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 تلميح: ${currentSlide.exerciseHint}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6B21A8),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // TextField matching Screenshot 3 ("Type here")
                    OutlinedTextField(
                        value = userInputText,
                        onValueChange = {
                            userInputText = it
                            exerciseStatus = null
                        },
                        placeholder = { Text("Type here", color = Color(0xFF94A3B8), fontSize = 16.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF7C5CFC),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true
                    )

                    // Check Button ("تحقق ✓")
                    Button(
                        onClick = {
                            val isCorrect = userInputText.trim().equals(currentSlide.expectedAnswer.trim(), ignoreCase = true)
                            exerciseStatus = isCorrect
                            if (!isCorrect) {
                                EnglishCurriculumRepository.recordMistake(
                                    context = context,
                                    frontText = currentSlide.expectedAnswer,
                                    arabicExplanation = currentSlide.explanationOnWrong.ifBlank { "إجابة خاطئة في تمرين: ${currentSlide.exercisePrompt}" },
                                    userSpeechLog = userInputText
                                )
                                Toast.makeText(context, "إجابة غير صحيحة، تم تسجيلها في أخطائي للمراجعة ❌", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "إجابة صحيحة وممتازة! 🎉", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("تحقق", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Feedback Banner
                    if (exerciseStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (exerciseStatus == true) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (exerciseStatus == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (exerciseStatus == true) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (exerciseStatus == true) "إجابة صحيحة! أحسنت 🌟" else "الإجابة الصحيحة هي: ${currentSlide.expectedAnswer}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (exerciseStatus == true) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONTENT TYPE 5: REAL STT PRONUNCIATION PRACTICE
            // ==========================================
            if (currentSlide.slideType == LessonSlideType.PRONUNCIATION_SPEAKING) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎤 تقييم النطق المباشر عبر الميكروفون",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B21B6)
                        )
                        Text(
                            text = "المطلوب نطقه: «${currentSlide.expectedAnswer}»",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )

                        // Big Mic Action Button
                        FilledIconButton(
                            onClick = {
                                try {
                                    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                    }
                                    isListening = true
                                    speechRecognizer.setRecognitionListener(object : RecognitionListener {
                                        override fun onReadyForSpeech(params: Bundle?) {}
                                        override fun onBeginningOfSpeech() {}
                                        override fun onRmsChanged(rmsdB: Float) {}
                                        override fun onBufferReceived(buffer: ByteArray?) {}
                                        override fun onEndOfSpeech() { isListening = false }
                                        override fun onError(error: Int) {
                                            isListening = false
                                            Toast.makeText(context, "جرب النطق مرة أخرى بالقرب من المايك", Toast.LENGTH_SHORT).show()
                                        }
                                        override fun onResults(results: Bundle?) {
                                            isListening = false
                                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                            val spoken = matches?.firstOrNull() ?: ""
                                            recognizedSpokenText = spoken
                                            val isMatch = spoken.contains(currentSlide.expectedAnswer, ignoreCase = true)
                                            speechEvaluationResult = isMatch
                                            if (!isMatch) {
                                                EnglishCurriculumRepository.recordMistake(
                                                    context = context,
                                                    frontText = currentSlide.expectedAnswer,
                                                    arabicExplanation = "خطأ في النطق: نطقت «$spoken» بدلاً من «${currentSlide.expectedAnswer}»",
                                                    userSpeechLog = spoken
                                                )
                                            }
                                        }
                                        override fun onPartialResults(partialResults: Bundle?) {}
                                        override fun onEvent(eventType: Int, params: Bundle?) {}
                                    })
                                    speechRecognizer.startListening(intent)
                                } catch (e: Exception) {
                                    isListening = false
                                    Toast.makeText(context, "المايك غير متاح في الوقت الحالي", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isListening) Color(0xFFEF4444) else Color(0xFF7C5CFC)
                            )
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "تحدث",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = if (isListening) "جاري الاستماع لنطقك... تحدث الآن 🔴" else "اضغط على المايك وانطق الكلمة بوضوح",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        if (recognizedSpokenText.isNotBlank()) {
                            Text(
                                text = "ما تم التقاطه: «$recognizedSpokenText»",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                            val isCorrect = speechEvaluationResult == true
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCorrect) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    text = if (isCorrect) "نطق ممتاز وصحيح 100%! 🌟" else "النطق يحتاج تدريب، تم إرسال الكلمة لبطاقات المراجعة 🔄",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // Bottom Navigation Bar (Screenshots 2, 4, 5, 6)
        // [مش فاهم ؟]   [فهمت، كمل ->]
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Secondary button: "مش فاهم ؟"
            OutlinedButton(
                onClick = { showNotUnderstoodDialog = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF8FAFC)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "مش فاهم",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "شرح أبسط",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Primary Purple button: "فهمت، كمل ->"
            Button(
                onClick = {
                    if (currentSlideIndex < slides.size - 1) {
                        currentSlideIndex++
                    } else {
                        // Finished lesson
                        onLessonCompleted(currentSlideIndex)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (currentSlideIndex == slides.size - 1) "إنهاء الدرس 🎓" else "فهمت، كمل",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // ==========================================
    // "مش فاهم" Explanatory Dialog
    // ==========================================
    if (showNotUnderstoodDialog) {
        AlertDialog(
            onDismissRequest = { showNotUnderstoodDialog = false },
            confirmButton = {
                Button(
                    onClick = { showNotUnderstoodDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
                ) {
                    Text("فهمت دلوقتي 👍", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF7C5CFC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("شرح مبسط وتشبيه من الواقع 💡", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = currentSlide.simpleExplanation.ifBlank { "في الإنجليزية نبسط القاعدة خطوة بخطوة حتى تثبت في الذاكرة دون حفظ جاف." },
                        fontSize = 13.5.sp,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    )

                    if (currentSlide.realLifeAnalogy.isNotBlank()) {
                        Divider(color = Color(0xFFE2E8F0))
                        Text(
                            text = "تشبيه من الحياة: ${currentSlide.realLifeAnalogy}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7C5CFC),
                            lineHeight = 19.sp
                        )
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}
