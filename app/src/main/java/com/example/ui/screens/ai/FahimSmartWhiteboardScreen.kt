package com.example.ui.screens.ai

import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.ai.FahimDialect
import com.example.domain.ai.FahimWhiteboardLesson
import com.example.domain.ai.WhiteboardGeometryScene
import com.example.domain.ai.WhiteboardStep
import com.example.ui.MainViewModel
import com.example.ui.theme.ReadexProFontFamily
import kotlin.math.abs
import kotlin.math.sin

private val BoardCharcoal = Color(0xFF1E1B18)
private val BoardRoyalBlue = Color(0xFF1A6FE3)
private val BoardAmberGold = Color(0xFFD97706)
private val BoardEmerald = Color(0xFF10B981)
private val BoardGridLine = Color(0xFFE2E8F0)
private val BoardPageBg = Color(0xFFF8FAFC)

@Composable
fun FahimSmartWhiteboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onSwitchToChat: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsState()
    val lesson by viewModel.whiteboardLesson.collectAsState()
    val isLoading by viewModel.isWhiteboardLoading.collectAsState()
    val isSpeaking by viewModel.fahimVoiceEngine.isSpeaking.collectAsState()
    val activeStepIndex by viewModel.fahimVoiceEngine.activeStepIndex.collectAsState()

    val selectedDialect = remember(settings.fahimDialect) {
        FahimDialect.fromName(settings.fahimDialect)
    }

    var questionInput by remember { mutableStateOf("") }
    var replayNonce by remember { mutableIntStateOf(0) }
    var isListeningVoice by remember { mutableStateOf(false) }

    val drawProgress = remember { Animatable(0f) }

    BackHandler {
        viewModel.fahimVoiceEngine.stop()
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.fahimVoiceEngine.stop()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.consumePendingWhiteboardPrompt()
    }

    // Animate progressive geometry stroke drawing whenever lesson or replayNonce updates
    LaunchedEffect(lesson.title, lesson.sceneType, replayNonce) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
        )
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        isListeningVoice = false
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                questionInput = spoken
                viewModel.loadWhiteboardLesson(spoken, selectedDialect, autoSpeak = true)
                replayNonce++
                questionInput = ""
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "اطلب شرح أي مسألة على السبورة...")
            }
            runCatching {
                isListeningVoice = true
                speechRecognizerLauncher.launch(intent)
            }.onFailure {
                isListeningVoice = false
                Toast.makeText(context, "الإملاء الصوتي غير متاح حالياً", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "مطلوب إذن الميكروفون للتحدث مع السبورة", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardPageBg)
            .statusBarsPadding()
    ) {
        // 1. Royal Blue Header matching Screen 1
        Surface(
            color = BoardRoyalBlue,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        viewModel.fahimVoiceEngine.stop()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("whiteboard_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }

                Text(
                    text = "سبورة فهيم الذكية",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        viewModel.fahimVoiceEngine.stop()
                        onSwitchToChat()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("whiteboard_switch_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "العودة لمحادثة فهيم",
                        tint = Color.White
                    )
                }
            }
        }

        // Scrollable Whiteboard + Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2. Main Interactive Grid-Paper Whiteboard Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fahim_whiteboard_card"),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.3.dp, BoardGridLine),
                shadowElevation = 3.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Geometric Grid Paper Background
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val stepPx = 22.dp.toPx()
                        var x = 0f
                        while (x <= size.width) {
                            drawLine(
                                color = BoardGridLine.copy(alpha = 0.65f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 0.9f
                            )
                            x += stepPx
                        }
                        var y = 0f
                        while (y <= size.height) {
                            drawLine(
                                color = BoardGridLine.copy(alpha = 0.65f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 0.9f
                            )
                            y += stepPx
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Lesson Title + 3D Teacher Mascot Holding Stylus
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.width(64.dp))

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = lesson.title,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BoardCharcoal,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(BoardRoyalBlue)
                                )
                            }

                            FahimWhiteboardTeacherAvatar(isDrawing = drawProgress.value < 1f || isSpeaking)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 24.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = BoardRoyalBlue,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "فهيم يرسم المسألة على السبورة...",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BoardRoyalBlue
                                )
                            }
                        } else {
                            // Live Animated Geometry Canvas (PathMeasure + Hatching + Labels)
                            FahimWhiteboardCanvas(
                                lesson = lesson,
                                progress = drawProgress.value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                            )

                            // Dimension Legend Badges right under diagram
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                BoardLegendChip(label = lesson.dimensionLabel, color = BoardEmerald)
                                BoardLegendChip(label = lesson.radiusLabel, color = BoardAmberGold)
                                if (lesson.secondaryLabel.isNotBlank()) {
                                    BoardLegendChip(label = lesson.secondaryLabel, color = BoardRoyalBlue)
                                }
                            }

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = BoardGridLine,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            // Progressive Colored Step-by-Step Equations
                            val visibleStepLimit = when {
                                isSpeaking && activeStepIndex >= 0 -> activeStepIndex + 1
                                drawProgress.value >= 0.92f -> lesson.steps.size
                                else -> ((drawProgress.value * lesson.steps.size).toInt() + 1).coerceAtMost(lesson.steps.size)
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                lesson.steps.take(visibleStepLimit).forEach { step ->
                                    WhiteboardEquationStepRow(
                                        step = step,
                                        isActive = isSpeaking && step.index == activeStepIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bottom Dialect Pills & Live Voice Waveform Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice & Dialects Control Card
                Surface(
                    modifier = Modifier.weight(1.75f),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, BoardGridLine),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FahimDialect.entries.forEach { dialect ->
                                val isSelected = dialect == selectedDialect
                                Surface(
                                    onClick = { viewModel.updateFahimDialect(dialect) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("dialect_pill_${dialect.name}"),
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) BoardRoyalBlue else Color(0xFFF1F5F9),
                                    border = if (isSelected) null else BorderStroke(1.dp, BoardGridLine)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isSelected) "${dialect.labelAr} ✓" else dialect.labelAr,
                                            fontFamily = ReadexProFontFamily,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else BoardCharcoal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Animated Waveform + Play/Stop Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .clickable {
                                    if (isSpeaking) {
                                        viewModel.fahimVoiceEngine.stop()
                                    } else {
                                        viewModel.fahimVoiceEngine.speakLessonSteps(
                                            steps = lesson.steps,
                                            dialect = selectedDialect,
                                            piperUrl = settings.piperTtsUrl,
                                            coquiUrl = settings.coquiTtsUrl,
                                            voiceApiKey = settings.voiceTtsApiKey
                                        )
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("whiteboard_voice_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "تشغيل صوت الشرح",
                                    tint = if (isSpeaking) Color(0xFFEF4444) else BoardRoyalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSpeaking) "صوت الشرح يعمل الآن" else "اضغط لسماع الشرح",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSpeaking) BoardRoyalBlue else BoardCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FahimAnimatedAudioWaveform(isActive = isSpeaking)
                        }
                    }
                }

                // Compact Replay Step-by-Step Card
                Surface(
                    onClick = {
                        replayNonce++
                        viewModel.fahimVoiceEngine.speakLessonSteps(
                            steps = lesson.steps,
                            dialect = selectedDialect,
                            piperUrl = settings.piperTtsUrl,
                            coquiUrl = settings.coquiTtsUrl,
                            voiceApiKey = settings.voiceTtsApiKey
                        )
                    },
                    modifier = Modifier
                        .weight(0.95f)
                        .height(106.dp)
                        .testTag("whiteboard_replay_button"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, BoardGridLine),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Replay else Icons.Default.PlayArrow,
                                contentDescription = "إعادة الشرح",
                                tint = BoardRoyalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "إعادة الرسم\nوالشرح",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BoardCharcoal,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 4. Floating Bottom White Question Capsule (Never overlaps keyboard/nav bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BoardPageBg)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                border = BorderStroke(1.2.dp, BoardGridLine),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val hasAudio = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasAudio) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "اطلب شرح مسألة على السبورة...")
                                }
                                runCatching {
                                    isListeningVoice = true
                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    speechRecognizerLauncher.launch(intent)
                                }.onFailure { isListeningVoice = false }
                            } else {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isListeningVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "تحدث",
                            tint = if (isListeningVoice) Color(0xFFEF4444) else Color(0xFF64748B)
                        )
                    }

                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = {
                            Text(
                                text = "اسأل فهيم أو اطلب شرح درس جديد...",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("whiteboard_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = BoardCharcoal,
                            unfocusedTextColor = BoardCharcoal
                        ),
                        maxLines = 3
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BoardRoyalBlue)
                            .clickable(enabled = !isLoading) {
                                val q = questionInput.trim()
                                if (q.isNotBlank()) {
                                    questionInput = ""
                                    viewModel.loadWhiteboardLesson(q, selectedDialect, autoSpeak = true)
                                    replayNonce++
                                }
                            }
                            .testTag("whiteboard_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسم واشرح",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FahimWhiteboardCanvas(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokePx = 2.5.dp.toPx()

        when (lesson.sceneType) {
            WhiteboardGeometryScene.SHADED_CIRCLES_RECT -> {
                val rectW = w * 0.72f
                val circleCount = 3
                val radius = rectW / (circleCount * 2f)
                val rectH = radius * 2f
                val left = (w - rectW) / 2f
                val top = (h - rectH) / 2f - 4.dp.toPx()

                // 1. Progressive Rectangle & Tangent Circles Path via PathMeasure
                val fullShapePath = Path().apply {
                    addRect(Rect(left, top, left + rectW, top + rectH))
                    for (i in 0 until circleCount) {
                        val cx = left + radius + i * radius * 2f
                        val cy = top + radius
                        addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                    }
                }

                // Hatching shaded corner pockets (Royal Blue on left, Amber Gold on right)
                if (progress > 0.35f) {
                    val hatchProgress = ((progress - 0.35f) / 0.4f).coerceIn(0f, 1f)
                    drawCornerHatching(left, top, rectW, rectH, radius, circleCount, hatchProgress)
                }

                drawMeasuredPath(
                    fullPath = fullShapePath,
                    fraction = (progress / 0.65f).coerceIn(0f, 1f),
                    color = BoardCharcoal,
                    strokeWidth = strokePx
                )

                // Radius arrows + Green overall dimension bracket d = 4
                if (progress > 0.55f) {
                    val labelFraction = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val cy = top + radius
                    val cRightX = left + rectW - radius
                    drawLine(
                        color = BoardRoyalBlue,
                        start = Offset(cRightX, cy),
                        end = Offset(cRightX + radius * labelFraction, cy),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = BoardAmberGold,
                        start = Offset(left + radius, cy),
                        end = Offset(left + radius, cy - radius * labelFraction),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Emerald bottom dimension line (d = 4)
                    val dimY = top + rectH + 14.dp.toPx()
                    val endX = left + rectW * labelFraction
                    drawLine(
                        color = BoardEmerald,
                        start = Offset(left, dimY),
                        end = Offset(endX, dimY),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(color = BoardEmerald, start = Offset(left, dimY - 5.dp.toPx()), end = Offset(left, dimY + 5.dp.toPx()), strokeWidth = 2.dp.toPx())
                    if (labelFraction > 0.9f) {
                        drawLine(color = BoardEmerald, start = Offset(left + rectW, dimY - 5.dp.toPx()), end = Offset(left + rectW, dimY + 5.dp.toPx()), strokeWidth = 2.dp.toPx())
                    }
                }
            }

            WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS -> {
                val ax = w * 0.28f
                val ay = h * 0.16f
                val bx = w * 0.28f
                val by = h * 0.80f
                val cx = w * 0.74f
                val cy = h * 0.80f

                val triPath = Path().apply {
                    moveTo(ax, ay)
                    lineTo(bx, by)
                    lineTo(cx, cy)
                    close()
                }
                if (progress > 0.4f) {
                    drawRect(
                        color = BoardRoyalBlue.copy(alpha = 0.20f),
                        topLeft = Offset(bx, by - 20.dp.toPx()),
                        size = Size(20.dp.toPx(), 20.dp.toPx())
                    )
                    drawRect(
                        color = BoardRoyalBlue,
                        topLeft = Offset(bx, by - 20.dp.toPx()),
                        size = Size(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
                drawMeasuredPath(triPath, (progress / 0.7f).coerceIn(0f, 1f), BoardCharcoal, strokePx)
                if (progress > 0.65f) {
                    drawLine(BoardEmerald, Offset(ax, ay), Offset(cx, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            WhiteboardGeometryScene.CIRCLE_PROPERTIES -> {
                val r = minOf(w, h) * 0.36f
                val cx = w * 0.5f
                val cy = h * 0.48f
                val circlePath = Path().apply {
                    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                }
                if (progress > 0.4f) {
                    drawCircle(color = BoardRoyalBlue.copy(alpha = 0.12f * ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)), radius = r, center = Offset(cx, cy))
                }
                drawMeasuredPath(circlePath, (progress / 0.7f).coerceIn(0f, 1f), BoardCharcoal, strokePx)
                if (progress > 0.6f) {
                    val f = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    drawLine(BoardAmberGold, Offset(cx, cy), Offset(cx + r * f, cy), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(BoardRoyalBlue, radius = 4.dp.toPx(), center = Offset(cx, cy))
                }
            }

            WhiteboardGeometryScene.PHYSICS_FORCE_MOTION -> {
                val boxW = w * 0.26f
                val boxH = h * 0.36f
                val left = w * 0.32f
                val top = h * 0.38f
                val floorY = top + boxH
                val boxPath = Path().apply {
                    addRect(Rect(left, top, left + boxW, floorY))
                }
                drawLine(BoardCharcoal.copy(alpha = 0.5f), Offset(w * 0.12f, floorY), Offset(w * 0.88f, floorY), strokeWidth = 2.dp.toPx())
                drawMeasuredPath(boxPath, (progress / 0.6f).coerceIn(0f, 1f), BoardCharcoal, strokePx)
                if (progress > 0.5f) {
                    val f = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val arrowStartX = left + boxW
                    val arrowEndX = arrowStartX + w * 0.24f * f
                    val midY = top + boxH * 0.5f
                    drawLine(BoardEmerald, Offset(arrowStartX, midY), Offset(arrowEndX, midY), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    if (f > 0.85f) {
                        drawLine(BoardEmerald, Offset(arrowEndX - 10.dp.toPx(), midY - 8.dp.toPx()), Offset(arrowEndX, midY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(BoardEmerald, Offset(arrowEndX - 10.dp.toPx(), midY + 8.dp.toPx()), Offset(arrowEndX, midY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }

            WhiteboardGeometryScene.COORDINATE_GRAPH -> {
                val cx = w * 0.5f
                val cy = h * 0.72f
                drawLine(BoardCharcoal.copy(alpha = 0.6f), Offset(w * 0.15f, cy), Offset(w * 0.85f, cy), strokeWidth = 2.dp.toPx())
                drawLine(BoardCharcoal.copy(alpha = 0.6f), Offset(cx, h * 0.10f), Offset(cx, h * 0.88f), strokeWidth = 2.dp.toPx())
                val curvePath = Path().apply {
                    moveTo(w * 0.25f, h * 0.18f)
                    quadraticTo(cx, cy + h * 0.40f, w * 0.75f, h * 0.18f)
                }
                drawMeasuredPath(curvePath, (progress / 0.85f).coerceIn(0f, 1f), BoardRoyalBlue, 3.dp.toPx())
                if (progress > 0.8f) {
                    drawCircle(BoardAmberGold, radius = 5.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
    }
}

private fun DrawScope.drawCornerHatching(
    left: Float,
    top: Float,
    rectW: Float,
    rectH: Float,
    radius: Float,
    circleCount: Int,
    fraction: Float
) {
    val rectPath = Path().apply { addRect(Rect(left, top, left + rectW, top + rectH)) }
    val circlesPath = Path().apply {
        for (i in 0 until circleCount) {
            val cx = left + radius + i * radius * 2f
            val cy = top + radius
            addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        }
    }
    val shadedRegion = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, rectPath, circlesPath)
    val step = 6.dp.toPx()
    val maxReach = rectW * fraction

    clipRect(left = left, top = top, right = left + maxReach, bottom = top + rectH) {
        // Fill soft blue & amber oblique hatching inside shaded corners
        var x = left - rectH
        while (x < left + rectW + rectH) {
            val strokeColor = if (x < left + rectW * 0.5f) {
                BoardRoyalBlue.copy(alpha = 0.42f)
            } else {
                BoardAmberGold.copy(alpha = 0.42f)
            }
            val lineStart = Offset(x, top + rectH)
            val lineEnd = Offset(x + rectH, top)
            drawPath(
                path = shadedRegion,
                color = Color.Transparent
            )
            drawLine(
                color = strokeColor,
                start = lineStart,
                end = lineEnd,
                strokeWidth = 1.6.dp.toPx()
            )
            x += step
        }
    }

    // Repaint interior of circles white so only corner pockets remain hatched cleanly
    for (i in 0 until circleCount) {
        val cx = left + radius + i * radius * 2f
        val cy = top + radius
        drawCircle(color = Color.White, radius = radius - 1.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawMeasuredPath(
    fullPath: Path,
    fraction: Float,
    color: Color,
    strokeWidth: Float
) {
    if (fraction <= 0f) return
    val measure = android.graphics.PathMeasure(fullPath.asAndroidPath(), false)
    val partial = android.graphics.Path()
    do {
        val len = measure.length
        if (len > 0f) {
            measure.getSegment(0f, len * fraction, partial, true)
        }
    } while (measure.nextContour())

    drawPath(
        path = partial.asComposePath(),
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun WhiteboardEquationStepRow(
    step: WhiteboardStep,
    isActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            step.isFinalResult -> Color(0xFFECFDF5)
            isActive -> Color(0xFFEFF6FF)
            else -> Color.Transparent
        },
        border = when {
            step.isFinalResult -> BorderStroke(1.2.dp, BoardEmerald.copy(alpha = 0.55f))
            isActive -> BorderStroke(1.dp, BoardRoyalBlue.copy(alpha = 0.45f))
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (step.rightGivenText.isNotBlank() || step.leftOperationText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.rightGivenText,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (step.isFinalResult) BoardEmerald else BoardAmberGold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = step.leftOperationText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BoardRoyalBlue,
                        textAlign = TextAlign.End
                    )
                }
            }

            if (step.centerFormulaText.isNotBlank()) {
                Text(
                    text = step.centerFormulaText,
                    fontFamily = if (step.isFinalResult) FontFamily.Monospace else ReadexProFontFamily,
                    fontSize = if (step.isFinalResult) 16.5.sp else 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (step.isFinalResult) BoardEmerald else BoardCharcoal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BoardLegendChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FahimWhiteboardTeacherAvatar(isDrawing: Boolean) {
    val transition = rememberInfiniteTransition(label = "wb_avatar")
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "wb_bob"
    )
    Canvas(
        modifier = Modifier
            .size(62.dp)
            .padding(top = (bob.coerceAtLeast(0f)).dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(BoardRoyalBlue.copy(alpha = 0.25f), Color.Transparent)
            ),
            radius = w * 0.48f,
            center = Offset(cx, h * 0.5f)
        )
        // Hood & Body
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(cx - w * 0.30f, h * 0.16f),
            size = Size(w * 0.60f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.25f, w * 0.25f)
        )
        drawRoundRect(
            color = BoardRoyalBlue,
            topLeft = Offset(cx - w * 0.25f, h * 0.22f),
            size = Size(w * 0.50f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF090E1A),
            topLeft = Offset(cx - w * 0.22f, h * 0.25f),
            size = Size(w * 0.44f, h * 0.36f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )
        // Happy Eyes
        val eyeColor = if (isDrawing) Color(0xFF38BDF8) else BoardAmberGold
        drawCircle(eyeColor, radius = w * 0.045f, center = Offset(cx - w * 0.08f, h * 0.41f))
        drawCircle(eyeColor, radius = w * 0.045f, center = Offset(cx + w * 0.08f, h * 0.41f))
        // Stylus Pen held by mascot
        drawLine(
            color = BoardRoyalBlue,
            start = Offset(w * 0.08f, h * 0.78f),
            end = Offset(w * 0.28f, h * 0.56f),
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FahimAnimatedAudioWaveform(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(22.dp)
    ) {
        val barCount = 11
        for (i in 0 until barCount) {
            val hFrac = if (isActive) {
                0.28f + 0.72f * abs(sin(phase + i * 0.65f))
            } else {
                0.25f + (i % 3) * 0.12f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((20f * hFrac).dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i in 4..6 && isActive) BoardAmberGold else BoardRoyalBlue
                    )
            )
        }
    }
}
