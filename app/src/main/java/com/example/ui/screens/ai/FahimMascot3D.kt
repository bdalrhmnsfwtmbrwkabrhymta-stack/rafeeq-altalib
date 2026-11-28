package com.example.ui.screens.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

private val HoodTopSheen = Color(0xFF3E5B8C)
private val HoodMidNavy = Color(0xFF1F3356)
private val HoodDeepNavy = Color(0xFF0D172B)
private val VisorTopDark = Color(0xFF141D2E)
private val VisorBottomBlack = Color(0xFF05080F)
private val EyeGlowCyan = Color(0xFF7DD3FC)
private val EyeCoreWhite = Color(0xFFE0F2FE)
private val StylusCyanAura = Color(0xFF38BDF8)
private val MascotGoldLaugh = Color(0xFFF59E0B)
private val MascotAngryOrange = Color(0xFFFF5722)
private val MascotAngryRed = Color(0xFFEF4444)
private val MascotInkText = Color(0xFF1E1B18)

private val MascotLaughBubbles = listOf(
    "ههههه! ضحكتني، يلا نبدأ بهمة عالية!",
    "أهلاً بالبطل! طاقتك إيجابية جداً النهاردة!",
    "كده الكلام! يلا نكسر امتحان بكرة سوا!"
)

private val MascotAnnoyedBubbles = listOf(
    "كفى ضغطاً بقى! أنا زهقت، سيبني أركز في المذاكرة!",
    "خلاص بقى دوختني! كفاية تكتكة خلينا نذاكر!"
)

/**
 * مجسم فهيم ثلاثي الأبعاد الواقعي (مطابق للصور المرجعية):
 * - هودي كحلي/نيلي لامع بتدرجات حجمية ثلاثية الأبعاد مع أربطة الهودي البيضاء.
 * - شاشة وجه داكنة بلمعة زجاجية وعيون سماوية مضيئة ناعمة.
 * - يحمل القلم الذكي المضيء باللون السماوي.
 * - ضغطة واحدة: يضحك بعيون ذهبية ونجوم ذهبية طائرة وفقاعة حماسية.
 * - ضغطات متكررة أو مطولة: ينزعج بعيون برتقالية ودموع زرقاء واهتزاز أفقي.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FahimMascot3D(
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    showIdleSpeechBubble: Boolean = false,
    showStatusBadge: Boolean = false,
    mascotSizeDp: Dp? = null
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
        laughIndex = (laughIndex + 1) % MascotLaughBubbles.size
        lastMoodEventMillis = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun triggerAnnoyed() {
        moodState = FahimMoodState.ANNOYED_CRYING
        annoyTapCount++
        annoyIndex = (annoyIndex + 1) % MascotAnnoyedBubbles.size
        lastMoodEventMillis = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val sequence = listOf(-14f, 14f, -11f, 11f, -7f, 7f, -3f, 0f)
            for (target in sequence) {
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 36))
            }
        }
    }

    LaunchedEffect(moodState, lastMoodEventMillis) {
        if (moodState != FahimMoodState.FRIENDLY_IDLE) {
            delay(3600L)
            moodState = FahimMoodState.FRIENDLY_IDLE
            rapidTapCounter = 0
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fahim_3d_motion")
    val idleFloatDp by infiniteTransition.animateFloat(
        initialValue = -4.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_bob_3d"
    )
    val stylusPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stylus_pulse"
    )
    val tearFallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tear_fall_3d"
    )
    val sparklePulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 660, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_pulse_3d"
    )

    val bounceJumpDp by animateDpAsState(
        targetValue = if (moodState == FahimMoodState.HAPPY_LAUGHING) (-12).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 360f),
        label = "laugh_bounce_3d"
    )

    val avatarScale by animateFloatAsState(
        targetValue = when (moodState) {
            FahimMoodState.HAPPY_LAUGHING -> 1.07f
            FahimMoodState.ANNOYED_CRYING -> 0.96f
            FahimMoodState.FRIENDLY_IDLE -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 350f),
        label = "avatar_scale_3d"
    )

    val speechText = when (moodState) {
        FahimMoodState.FRIENDLY_IDLE -> "اضغط عليّ للتفاعل أو اسألني أي سؤال!"
        FahimMoodState.HAPPY_LAUGHING -> MascotLaughBubbles[laughIndex]
        FahimMoodState.ANNOYED_CRYING -> MascotAnnoyedBubbles[annoyIndex]
    }

    val mascotSize = mascotSizeDp ?: if (compactMode) 128.dp else 166.dp
    val shouldShowBubble = showIdleSpeechBubble || moodState != FahimMoodState.FRIENDLY_IDLE

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = shouldShowBubble) {
            AnimatedContent(
                targetState = Triple(moodState, speechText, laughCount + annoyTapCount),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "speech_bubble_3d"
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
                    FahimMoodState.FRIENDLY_IDLE -> MascotInkText
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bubbleBg,
                    border = BorderStroke(1.2.dp, bubbleBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = text,
                        fontFamily = ReadexProFontFamily,
                        fontSize = if (compactMode) 13.5.sp else 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = bubbleTextColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

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
                drawRealistic3DFahim(
                    moodState = moodState,
                    stylusPulse = stylusPulse,
                    sparklePulse = sparklePulse,
                    tearFallProgress = tearFallProgress,
                    showStylus = true
                )
            }
        }

        if (showStatusBadge || moodState != FahimMoodState.FRIENDLY_IDLE) {
            Spacer(modifier = Modifier.height(4.dp))
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
                } else if (moodState == FahimMoodState.HAPPY_LAUGHING || showStatusBadge) {
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
                                tint = if (laughCount > 0) Color(0xFFD97706) else Color(0xFF1A6FE3),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (laughCount > 0) {
                                    "عدد الضحكات: $laughCount"
                                } else {
                                    "المس فهيم للتفاعل"
                                },
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (laughCount > 0) Color(0xFF92400E) else Color(0xFF1A6FE3)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRealistic3DFahim(
    moodState: FahimMoodState,
    stylusPulse: Float,
    sparklePulse: Float,
    tearFallProgress: Float,
    showStylus: Boolean
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.54f

    // 1. Soft Ambient Contact Shadow under Hoodie
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0F172A).copy(alpha = 0.22f), Color.Transparent),
            center = Offset(cx, h * 0.94f),
            radius = w * 0.34f
        ),
        topLeft = Offset(cx - w * 0.32f, h * 0.89f),
        size = Size(w * 0.64f, h * 0.09f)
    )

    // 2. Sculpted 3D Dark-Navy Hoodie Torso & Shoulders
    val torsoPath = Path().apply {
        moveTo(cx - w * 0.29f, h * 0.93f)
        cubicTo(cx - w * 0.31f, h * 0.72f, cx - w * 0.22f, h * 0.62f, cx, h * 0.62f)
        cubicTo(cx + w * 0.22f, h * 0.62f, cx + w * 0.31f, h * 0.72f, cx + w * 0.29f, h * 0.93f)
        close()
    }
    drawPath(
        path = torsoPath,
        brush = Brush.radialGradient(
            colors = listOf(HoodTopSheen, HoodMidNavy, HoodDeepNavy),
            center = Offset(cx - w * 0.06f, h * 0.68f),
            radius = w * 0.38f
        )
    )

    // Left Extended Arm holding the Smart Stylus
    if (showStylus) {
        val armPath = Path().apply {
            moveTo(cx - w * 0.20f, h * 0.70f)
            cubicTo(cx - w * 0.31f, h * 0.74f, cx - w * 0.35f, h * 0.80f, cx - w * 0.32f, h * 0.86f)
            cubicTo(cx - w * 0.27f, h * 0.88f, cx - w * 0.18f, h * 0.83f, cx - w * 0.14f, h * 0.77f)
            close()
        }
        drawPath(
            path = armPath,
            brush = Brush.linearGradient(
                colors = listOf(HoodTopSheen, HoodDeepNavy),
                start = Offset(cx - w * 0.34f, h * 0.72f),
                end = Offset(cx - w * 0.14f, h * 0.86f)
            )
        )
    }

    // White Hoodie Drawstrings (matching Ref Image 1)
    drawLine(
        color = Color(0xFFE2E8F0).copy(alpha = 0.92f),
        start = Offset(cx - w * 0.065f, h * 0.68f),
        end = Offset(cx - w * 0.075f, h * 0.83f),
        strokeWidth = w * 0.017f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFE2E8F0).copy(alpha = 0.92f),
        start = Offset(cx + w * 0.065f, h * 0.68f),
        end = Offset(cx + w * 0.075f, h * 0.83f),
        strokeWidth = w * 0.017f,
        cap = StrokeCap.Round
    )
    // Aglets on drawstring tips
    drawCircle(Color(0xFF94A3B8), radius = w * 0.012f, center = Offset(cx - w * 0.075f, h * 0.835f))
    drawCircle(Color(0xFF94A3B8), radius = w * 0.012f, center = Offset(cx + w * 0.075f, h * 0.835f))

    // 3. Outer 3D Rounded Hood Dome (smooth glossy volumetric navy)
    val hoodPath = Path().apply {
        moveTo(cx, h * 0.08f)
        cubicTo(cx + w * 0.27f, h * 0.08f, cx + w * 0.34f, h * 0.28f, cx + w * 0.31f, h * 0.56f)
        cubicTo(cx + w * 0.29f, h * 0.69f, cx + w * 0.14f, h * 0.73f, cx, h * 0.73f)
        cubicTo(cx - w * 0.14f, h * 0.73f, cx - w * 0.29f, h * 0.69f, cx - w * 0.31f, h * 0.56f)
        cubicTo(cx - w * 0.34f, h * 0.28f, cx - w * 0.27f, h * 0.08f, cx, h * 0.08f)
        close()
    }
    drawPath(
        path = hoodPath,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF5173AB), HoodMidNavy, HoodDeepNavy),
            center = Offset(cx - w * 0.07f, h * 0.17f),
            radius = w * 0.48f
        )
    )
    // Top specular soft sheen on hood crown
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
        ),
        topLeft = Offset(cx - w * 0.17f, h * 0.105f),
        size = Size(w * 0.34f, h * 0.10f)
    )

    // Subtle Inner Hood Fold Rim
    val rimColor = when (moodState) {
        FahimMoodState.ANNOYED_CRYING -> MascotAngryOrange.copy(alpha = 0.65f)
        FahimMoodState.HAPPY_LAUGHING -> MascotGoldLaugh.copy(alpha = 0.70f)
        FahimMoodState.FRIENDLY_IDLE -> Color(0xFF47699E).copy(alpha = 0.85f)
    }
    drawOval(
        color = rimColor,
        topLeft = Offset(cx - w * 0.255f, h * 0.20f),
        size = Size(w * 0.51f, h * 0.45f),
        style = Stroke(width = w * 0.022f)
    )

    // 4. Deep Obsidian Glassy Visor Cavity
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(VisorTopDark, VisorBottomBlack)
        ),
        topLeft = Offset(cx - w * 0.235f, h * 0.22f),
        size = Size(w * 0.47f, h * 0.415f)
    )
    // Soft curved glass highlight inside visor
    drawArc(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)),
        startAngle = 205f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.19f, h * 0.24f),
        size = Size(w * 0.38f, h * 0.13f),
        style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
    )

    // 5. Expressive Eyes & Mood Effects
    when (moodState) {
        FahimMoodState.FRIENDLY_IDLE -> {
            val leftEyeCenter = Offset(cx - w * 0.092f, h * 0.425f)
            val rightEyeCenter = Offset(cx + w * 0.092f, h * 0.425f)

            // Soft cyan eye bloom
            drawCircle(
                brush = Brush.radialGradient(listOf(EyeGlowCyan.copy(alpha = 0.42f), Color.Transparent)),
                radius = w * 0.095f,
                center = leftEyeCenter
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(EyeGlowCyan.copy(alpha = 0.42f), Color.Transparent)),
                radius = w * 0.095f,
                center = rightEyeCenter
            )
            // Smooth luminous oval eyes (matching Ref Image 1)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(EyeCoreWhite, EyeGlowCyan)),
                topLeft = Offset(leftEyeCenter.x - w * 0.036f, leftEyeCenter.y - h * 0.056f),
                size = Size(w * 0.072f, h * 0.112f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(EyeCoreWhite, EyeGlowCyan)),
                topLeft = Offset(rightEyeCenter.x - w * 0.036f, rightEyeCenter.y - h * 0.056f),
                size = Size(w * 0.072f, h * 0.112f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }

        FahimMoodState.HAPPY_LAUGHING -> {
            val leftEye = Path().apply {
                moveTo(cx - w * 0.145f, h * 0.44f)
                quadraticTo(cx - w * 0.095f, h * 0.355f, cx - w * 0.045f, h * 0.44f)
            }
            val rightEye = Path().apply {
                moveTo(cx + w * 0.045f, h * 0.44f)
                quadraticTo(cx + w * 0.095f, h * 0.355f, cx + w * 0.145f, h * 0.44f)
            }
            drawPath(leftEye, MascotGoldLaugh, style = Stroke(width = w * 0.03f, cap = StrokeCap.Round))
            drawPath(rightEye, MascotGoldLaugh, style = Stroke(width = w * 0.03f, cap = StrokeCap.Round))

            drawArc(
                color = MascotGoldLaugh,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - w * 0.085f, h * 0.47f),
                size = Size(w * 0.17f, h * 0.095f)
            )

            // Floating Golden Stars
            val starOffsets = listOf(
                Offset(cx - w * 0.38f, h * 0.18f),
                Offset(cx + w * 0.36f, h * 0.16f),
                Offset(cx + w * 0.38f, h * 0.54f)
            )
            starOffsets.forEachIndexed { i, pos ->
                val r = w * 0.045f * sparklePulse * (if (i % 2 == 0) 1f else 0.84f)
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
                drawPath(starPath, color = MascotGoldLaugh)
            }
        }

        FahimMoodState.ANNOYED_CRYING -> {
            drawLine(
                color = MascotAngryOrange,
                start = Offset(cx - w * 0.15f, h * 0.36f),
                end = Offset(cx - w * 0.045f, h * 0.405f),
                strokeWidth = w * 0.028f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = MascotAngryOrange,
                start = Offset(cx + w * 0.15f, h * 0.36f),
                end = Offset(cx + w * 0.045f, h * 0.405f),
                strokeWidth = w * 0.028f,
                cap = StrokeCap.Round
            )
            drawCircle(MascotAngryOrange, radius = w * 0.036f, center = Offset(cx - w * 0.092f, h * 0.435f))
            drawCircle(MascotAngryOrange, radius = w * 0.036f, center = Offset(cx + w * 0.092f, h * 0.435f))
            drawArc(
                color = MascotAngryOrange,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.072f, h * 0.51f),
                size = Size(w * 0.144f, h * 0.07f),
                style = Stroke(width = w * 0.024f, cap = StrokeCap.Round)
            )
            // Falling Blue Teardrops
            val dropY1 = h * (0.46f + tearFallProgress * 0.17f)
            val dropY2 = h * (0.46f + ((tearFallProgress + 0.5f) % 1f) * 0.17f)
            listOf(Offset(cx - w * 0.12f, dropY1), Offset(cx + w * 0.12f, dropY2)).forEach { d ->
                val tearPath = Path().apply {
                    moveTo(d.x, d.y - w * 0.026f)
                    quadraticTo(d.x + w * 0.03f, d.y + w * 0.015f, d.x, d.y + w * 0.034f)
                    quadraticTo(d.x - w * 0.03f, d.y + w * 0.015f, d.x, d.y - w * 0.026f)
                    close()
                }
                drawPath(tearPath, color = StylusCyanAura)
            }
            for (k in 0..2) {
                val angle = Math.toRadians((-145 + k * 35).toDouble())
                val r1 = w * 0.33f
                val r2 = w * 0.39f
                drawLine(
                    color = MascotAngryRed,
                    start = Offset(cx + (cos(angle) * r1).toFloat(), h * 0.42f + (sin(angle) * r1).toFloat()),
                    end = Offset(cx + (cos(angle) * r2).toFloat(), h * 0.42f + (sin(angle) * r2).toFloat()),
                    strokeWidth = w * 0.022f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // 6. Glowing Cyan Smart Stylus Pen in Hand (matching Ref Image 1!)
    if (showStylus) {
        val tip = Offset(w * 0.14f, h * 0.52f)
        val butt = Offset(w * 0.31f, h * 0.87f)
        // Soft luminous cyan aura around stylus tip
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(StylusCyanAura.copy(alpha = 0.55f), StylusCyanAura.copy(alpha = 0.16f), Color.Transparent),
                center = tip,
                radius = w * 0.16f * stylusPulse
            ),
            radius = w * 0.17f * stylusPulse,
            center = tip
        )
        // Stylus barrel
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFBAE6FD), Color(0xFF38BDF8), Color(0xFF1E3A8A)),
                start = tip,
                end = butt
            ),
            start = tip,
            end = butt,
            strokeWidth = w * 0.038f,
            cap = StrokeCap.Round
        )
        // Bright glowing stylus tip core
        drawCircle(Color.White, radius = w * 0.018f, center = tip)

        // Small dark-navy gloved hand gripping the stylus
        drawCircle(
            brush = Brush.radialGradient(listOf(HoodTopSheen, HoodDeepNavy)),
            radius = w * 0.072f,
            center = Offset(w * 0.25f, h * 0.77f)
        )
    }
}

@Composable
fun FahimTeacherAvatar3D(isDrawing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wb_avatar_3d")
    val bob by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
        label = "wb_bob_3d"
    )
    Canvas(
        modifier = modifier
            .size(64.dp)
            .padding(top = bob.coerceAtLeast(0f).dp)
    ) {
        drawRealistic3DFahim(
            moodState = if (isDrawing) FahimMoodState.FRIENDLY_IDLE else FahimMoodState.HAPPY_LAUGHING,
            stylusPulse = if (isDrawing) 1.15f else 0.9f,
            sparklePulse = 1f,
            tearFallProgress = 0f,
            showStylus = true
        )
    }
}
