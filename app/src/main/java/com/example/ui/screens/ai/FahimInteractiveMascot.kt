package com.example.ui.screens.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.FahimMoodState
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val FahimRoyalBlue = Color(0xFF1A6FE3)
private val FahimCyanGlow = Color(0xFF38BDF8)
private val FahimGoldAccent = Color(0xFFF59E0B)
private val FahimAnnoyedRed = Color(0xFFEF4444)
private val FahimAnnoyedOrange = Color(0xFFFF5722)
private val FahimInkText = Color(0xFF1E1B18)

private val LaughBubbles = listOf(
    "ههههه! ضحكتني، يلا نبدأ بهمة عالية!",
    "أهلاً بالبطل! طاقتك إيجابية جداً النهاردة!",
    "كده الكلام! يلا نكسر امتحان بكرة سوا!"
)

private val AnnoyedBubbles = listOf(
    "كفى ضغطاً بقى! أنا زهقت، سيبني أركز في المذاكرة!",
    "خلاص بقى دوختني! كفاية تكتكة خلينا نذاكر!"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FahimInteractiveMascot(
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var moodState by remember { mutableStateOf(FahimMoodState.FRIENDLY_IDLE) }
    var laughCount by remember { mutableIntStateOf(0) }
    var annoyTapCount by remember { mutableIntStateOf(0) }
    var rapidTapCounter by remember { mutableIntStateOf(0) }
    var lastTapMillis by remember { mutableLongStateOf(0L) }
    var lastMoodEventMillis by remember { mutableLongStateOf(0L) }
    var laughIndex by remember { mutableIntStateOf(0) }
    var annoyIndex by remember { mutableIntStateOf(0) }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerLaugh() {
        moodState = FahimMoodState.HAPPY_LAUGHING
        laughCount++
        laughIndex = (laughIndex + 1) % LaughBubbles.size
        lastMoodEventMillis = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun triggerAnnoyed() {
        moodState = FahimMoodState.ANNOYED_CRYING
        annoyTapCount++
        annoyIndex = (annoyIndex + 1) % AnnoyedBubbles.size
        lastMoodEventMillis = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val sequence = listOf(-14f, 14f, -11f, 11f, -7f, 7f, -3f, 0f)
            for (target in sequence) {
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 42))
            }
        }
    }

    // Auto reset back to FRIENDLY_IDLE after 3.5 seconds of inactivity
    LaunchedEffect(moodState, lastMoodEventMillis) {
        if (moodState != FahimMoodState.FRIENDLY_IDLE) {
            delay(3500L)
            moodState = FahimMoodState.FRIENDLY_IDLE
            rapidTapCounter = 0
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fahim_idle_motion")
    val idleFloatDp by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_bob"
    )
    val tearFallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tear_fall"
    )
    val sparklePulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_pulse"
    )

    val bounceJumpDp by animateDpAsState(
        targetValue = when (moodState) {
            FahimMoodState.HAPPY_LAUGHING -> (-12).dp
            else -> 0.dp
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 350f),
        label = "laugh_bounce"
    )

    val avatarScale by animateFloatAsState(
        targetValue = when (moodState) {
            FahimMoodState.HAPPY_LAUGHING -> 1.07f
            FahimMoodState.ANNOYED_CRYING -> 0.96f
            FahimMoodState.FRIENDLY_IDLE -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 350f),
        label = "avatar_scale"
    )

    val speechText = when (moodState) {
        FahimMoodState.FRIENDLY_IDLE -> "اضغط عليّ للتفاعل أو اسألني أي سؤال!"
        FahimMoodState.HAPPY_LAUGHING -> LaughBubbles[laughIndex]
        FahimMoodState.ANNOYED_CRYING -> AnnoyedBubbles[annoyIndex]
    }

    val mascotSize = if (compactMode) 118.dp else 156.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dynamic Mood Speech Bubble
        AnimatedContent(
            targetState = Triple(moodState, speechText, laughCount + annoyTapCount),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "speech_bubble"
        ) { (state, text, _) ->
            val bubbleBg = when (state) {
                FahimMoodState.ANNOYED_CRYING -> Color(0xFFFEF2F2)
                FahimMoodState.HAPPY_LAUGHING -> Color(0xFFFFFBEB)
                FahimMoodState.FRIENDLY_IDLE -> Color.White
            }
            val bubbleBorder = when (state) {
                FahimMoodState.ANNOYED_CRYING -> Color(0xFFFCA5A5)
                FahimMoodState.HAPPY_LAUGHING -> Color(0xFFFCD34D)
                FahimMoodState.FRIENDLY_IDLE -> Color(0xFFE2E8F0)
            }
            val bubbleTextColor = when (state) {
                FahimMoodState.ANNOYED_CRYING -> Color(0xFFB91C1C)
                FahimMoodState.HAPPY_LAUGHING -> Color(0xFF92400E)
                FahimMoodState.FRIENDLY_IDLE -> FahimInkText
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = bubbleBg,
                border = BorderStroke(1.2.dp, bubbleBorder),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = text,
                    fontFamily = ReadexProFontFamily,
                    fontSize = if (compactMode) 13.5.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = bubbleTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // 3D-Styled Interactive Hooded Robot Mascot Canvas
        Box(
            modifier = Modifier
                .size(mascotSize)
                .offset {
                    IntOffset(
                        x = shakeOffset.value.roundToInt(),
                        y = (idleFloatDp + bounceJumpDp.value).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapMillis <= 2000L) {
                            rapidTapCounter++
                        } else {
                            rapidTapCounter = 1
                        }
                        lastTapMillis = now

                        if (rapidTapCounter >= 3) {
                            triggerAnnoyed()
                        } else {
                            triggerLaugh()
                        }
                    },
                    onLongClick = {
                        rapidTapCounter = 3
                        lastTapMillis = System.currentTimeMillis()
                        triggerAnnoyed()
                    }
                )
                .testTag("fahim_interactive_mascot"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Outer Atmospheric Mood Aura
                val auraColors = when (moodState) {
                    FahimMoodState.ANNOYED_CRYING -> listOf(
                        FahimAnnoyedRed.copy(alpha = 0.28f),
                        FahimAnnoyedOrange.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                    FahimMoodState.HAPPY_LAUGHING -> listOf(
                        FahimGoldAccent.copy(alpha = 0.28f),
                        FahimCyanGlow.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                    FahimMoodState.FRIENDLY_IDLE -> listOf(
                        FahimRoyalBlue.copy(alpha = 0.22f),
                        FahimCyanGlow.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(colors = auraColors, center = Offset(cx, cy), radius = w * 0.50f),
                    radius = w * 0.50f,
                    center = Offset(cx, cy)
                )

                // Soft Shadow under Mascot
                drawOval(
                    color = Color(0xFF0F172A).copy(alpha = 0.14f),
                    topLeft = Offset(cx - w * 0.24f, h * 0.87f),
                    size = Size(w * 0.48f, h * 0.08f)
                )

                // Hoodie / Shoulders Cloak (Charcoal Navy 3D gradient)
                val bodyPath = Path().apply {
                    moveTo(cx - w * 0.27f, h * 0.88f)
                    cubicTo(cx - w * 0.27f, h * 0.64f, cx - w * 0.18f, h * 0.56f, cx, h * 0.56f)
                    cubicTo(cx + w * 0.18f, h * 0.56f, cx + w * 0.27f, h * 0.64f, cx + w * 0.27f, h * 0.88f)
                    close()
                }
                drawPath(
                    path = bodyPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                        startY = h * 0.56f,
                        endY = h * 0.88f
                    )
                )
                // Royal Blue Hoodie Zipper & Collar Trim
                drawLine(
                    color = if (moodState == FahimMoodState.ANNOYED_CRYING) FahimAnnoyedRed else FahimRoyalBlue,
                    start = Offset(cx, h * 0.65f),
                    end = Offset(cx, h * 0.88f),
                    strokeWidth = w * 0.022f,
                    cap = StrokeCap.Round
                )

                // Headphone Band Arc over the Hood
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.34f, h * 0.11f),
                    size = Size(w * 0.68f, h * 0.56f),
                    style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
                )

                // Left & Right Headphones Ear Cups with Glowing Ring
                val cupGlow = when (moodState) {
                    FahimMoodState.ANNOYED_CRYING -> FahimAnnoyedRed
                    FahimMoodState.HAPPY_LAUGHING -> FahimGoldAccent
                    FahimMoodState.FRIENDLY_IDLE -> FahimCyanGlow
                }
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(cx - w * 0.38f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.22f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
                drawRoundRect(
                    color = cupGlow,
                    topLeft = Offset(cx - w * 0.365f, h * 0.35f),
                    size = Size(w * 0.03f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
                )
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(cx + w * 0.28f, h * 0.31f),
                    size = Size(w * 0.10f, h * 0.22f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
                drawRoundRect(
                    color = cupGlow,
                    topLeft = Offset(cx + w * 0.335f, h * 0.35f),
                    size = Size(w * 0.03f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
                )

                // Outer Hood Silhouette
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF28354E), Color(0xFF131B2E)),
                        startY = h * 0.14f,
                        endY = h * 0.68f
                    ),
                    topLeft = Offset(cx - w * 0.31f, h * 0.14f),
                    size = Size(w * 0.62f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.26f, w * 0.26f)
                )

                // Inner Glowing Hood Trim Ring
                drawRoundRect(
                    color = when (moodState) {
                        FahimMoodState.ANNOYED_CRYING -> FahimAnnoyedOrange
                        FahimMoodState.HAPPY_LAUGHING -> FahimGoldAccent
                        FahimMoodState.FRIENDLY_IDLE -> FahimRoyalBlue
                    },
                    topLeft = Offset(cx - w * 0.26f, h * 0.21f),
                    size = Size(w * 0.52f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
                    style = Stroke(width = w * 0.026f)
                )

                // Matte Dark Robot Visor Screen Face
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF141E30), Color(0xFF090E1A))
                    ),
                    topLeft = Offset(cx - w * 0.24f, h * 0.23f),
                    size = Size(w * 0.48f, h * 0.36f),
                    cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
                )

                // Subtle Top Visor Glass Highlight
                drawOval(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(cx - w * 0.16f, h * 0.255f),
                    size = Size(w * 0.32f, h * 0.06f)
                )

                // Facial Expressions by Mood State
                when (moodState) {
                    FahimMoodState.FRIENDLY_IDLE -> {
                        // Glowing Royal Cyan Rounded Eyes
                        drawRoundRect(
                            color = FahimCyanGlow,
                            topLeft = Offset(cx - w * 0.135f, h * 0.345f),
                            size = Size(w * 0.075f, h * 0.105f),
                            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                        )
                        drawRoundRect(
                            color = FahimCyanGlow,
                            topLeft = Offset(cx + w * 0.06f, h * 0.345f),
                            size = Size(w * 0.075f, h * 0.105f),
                            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                        )
                        // Tiny specular dot in eyes
                        drawCircle(Color.White, radius = w * 0.015f, center = Offset(cx - w * 0.108f, h * 0.375f))
                        drawCircle(Color.White, radius = w * 0.015f, center = Offset(cx + w * 0.087f, h * 0.375f))

                        // Friendly Smile Arc
                        drawArc(
                            color = FahimCyanGlow,
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx - w * 0.075f, h * 0.44f),
                            size = Size(w * 0.15f, h * 0.085f),
                            style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                        )
                    }

                    FahimMoodState.HAPPY_LAUGHING -> {
                        // Golden Happy Arched Laughing Eyes (^ ^)
                        val leftEye = Path().apply {
                            moveTo(cx - w * 0.15f, h * 0.415f)
                            quadraticTo(cx - w * 0.10f, h * 0.335f, cx - w * 0.05f, h * 0.415f)
                        }
                        val rightEye = Path().apply {
                            moveTo(cx + w * 0.05f, h * 0.415f)
                            quadraticTo(cx + w * 0.10f, h * 0.335f, cx + w * 0.15f, h * 0.415f)
                        }
                        drawPath(leftEye, color = FahimGoldAccent, style = Stroke(width = w * 0.028f, cap = StrokeCap.Round))
                        drawPath(rightEye, color = FahimGoldAccent, style = Stroke(width = w * 0.028f, cap = StrokeCap.Round))

                        // Wide Open Cheerful Smile
                        drawArc(
                            color = FahimGoldAccent,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - w * 0.09f, h * 0.44f),
                            size = Size(w * 0.18f, h * 0.10f)
                        )

                        // Scattered Golden Sparkles Around Hood
                        val starOffsets = listOf(
                            Offset(cx - w * 0.37f, h * 0.22f),
                            Offset(cx + w * 0.36f, h * 0.20f),
                            Offset(cx - w * 0.39f, h * 0.56f),
                            Offset(cx + w * 0.38f, h * 0.58f)
                        )
                        starOffsets.forEachIndexed { i, pos ->
                            val r = w * 0.042f * sparklePulse * (if (i % 2 == 0) 1f else 0.85f)
                            val starPath = Path().apply {
                                moveTo(pos.x, pos.y - r)
                                lineTo(pos.x + r * 0.3f, pos.y - r * 0.3f)
                                lineTo(pos.x + r, pos.y)
                                lineTo(pos.x + r * 0.3f, pos.y + r * 0.3f)
                                lineTo(pos.x, pos.y + r)
                                lineTo(pos.x - r * 0.3f, pos.y + r * 0.3f)
                                lineTo(pos.x - r, pos.y)
                                lineTo(pos.x - r * 0.3f, pos.y - r * 0.3f)
                                close()
                            }
                            drawPath(starPath, color = FahimGoldAccent)
                        }
                    }

                    FahimMoodState.ANNOYED_CRYING -> {
                        // Glowing Angry Orange-Red Squinting Eyes
                        drawLine(
                            color = FahimAnnoyedOrange,
                            start = Offset(cx - w * 0.155f, h * 0.34f),
                            end = Offset(cx - w * 0.045f, h * 0.385f),
                            strokeWidth = w * 0.028f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = FahimAnnoyedOrange,
                            start = Offset(cx + w * 0.155f, h * 0.34f),
                            end = Offset(cx + w * 0.045f, h * 0.385f),
                            strokeWidth = w * 0.028f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(FahimAnnoyedOrange, radius = w * 0.036f, center = Offset(cx - w * 0.095f, h * 0.41f))
                        drawCircle(FahimAnnoyedOrange, radius = w * 0.036f, center = Offset(cx + w * 0.095f, h * 0.41f))

                        // Frown Mouth
                        drawArc(
                            color = FahimAnnoyedOrange,
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(cx - w * 0.075f, h * 0.485f),
                            size = Size(w * 0.15f, h * 0.07f),
                            style = Stroke(width = w * 0.024f, cap = StrokeCap.Round)
                        )

                        // Animated Falling Blue Teardrops
                        val dropY1 = h * (0.44f + tearFallProgress * 0.18f)
                        val dropY2 = h * (0.44f + ((tearFallProgress + 0.5f) % 1f) * 0.18f)
                        val drops = listOf(
                            Offset(cx - w * 0.125f, dropY1),
                            Offset(cx + w * 0.125f, dropY2)
                        )
                        drops.forEach { d ->
                            val tearPath = Path().apply {
                                moveTo(d.x, d.y - w * 0.028f)
                                quadraticTo(d.x + w * 0.032f, d.y + w * 0.015f, d.x, d.y + w * 0.035f)
                                quadraticTo(d.x - w * 0.032f, d.y + w * 0.015f, d.x, d.y - w * 0.028f)
                                close()
                            }
                            drawPath(tearPath, color = FahimCyanGlow)
                        }

                        // Annoyance Spark Lines Top Corner
                        for (k in 0..2) {
                            val angle = Math.toRadians((-150 + k * 35).toDouble())
                            val r1 = w * 0.33f
                            val r2 = w * 0.40f
                            drawLine(
                                color = FahimAnnoyedRed,
                                start = Offset(cx + (cos(angle) * r1).toFloat(), cy + (sin(angle) * r1).toFloat()),
                                end = Offset(cx + (cos(angle) * r2).toFloat(), cy + (sin(angle) * r2).toFloat()),
                                strokeWidth = w * 0.02f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Counter Pill (Laughs / Annoyed Counter)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (moodState == FahimMoodState.ANNOYED_CRYING) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "فهيم منزعج! • مرات الضغط: $annoyTapCount",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (laughCount > 0) Color(0xFFFEF3C7) else Color(0xFFEFF6FF),
                    border = BorderStroke(
                        1.dp,
                        if (laughCount > 0) Color(0xFFFCD34D) else Color(0xFFBFDBFE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SentimentVerySatisfied,
                            contentDescription = null,
                            tint = if (laughCount > 0) Color(0xFFD97706) else FahimRoyalBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (laughCount > 0) {
                                "عدد الضحكات: $laughCount"
                            } else {
                                "المس فهيم للتفاعل أو اضغط مطولاً"
                            },
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (laughCount > 0) Color(0xFF92400E) else FahimRoyalBlue
                        )
                    }
                }
            }
        }
    }
}
