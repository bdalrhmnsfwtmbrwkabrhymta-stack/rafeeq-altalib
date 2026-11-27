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

private val MascotRoyalBlueTop = Color(0xFF2B6EF6)
private val MascotDeepNavyMid = Color(0xFF1A27F0)
private val MascotDeepNavyBottom = Color(0xFF1E3A8A)
private val MascotCyanNeon = Color(0xFF38BDF8)
private val MascotGoldLaugh = Color(0xFFF59E0B)
private val MascotAngryRed = Color(0xFFEF4444)
private val MascotAngryOrange = Color(0xFFFF5722)
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
 * مجسم فهيم ثلاثي الأبعاد اللامع (FahimMascot3D):
 * - هودي أزرق ملكي/كحلي لامع بتدرجات 3D وإضاءة حافة سماوية نيون (#38BDF8).
 * - شاشة وجه زجاجية مقوسة بلمعة علوية مع حركة طفو مستمرة وظل أرضي ديناميكي.
 * - TAP_SINGLE: قفزة زنبركية + عيون ذهبية مقوسة ضاحكة + نجوم ذهبية + فقاعة حماسية.
 * - TAP_SPAM: اهتزاز أفقي + عيون حمراء/برتقالية غاضبة + دموع زرقاء متساقطة.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FahimMascot3D(
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
            val sequence = listOf(-16f, 16f, -12f, 12f, -8f, 8f, -4f, 0f)
            for (target in sequence) {
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 38))
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
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_bob_3d"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse_3d"
    )
    val tearFallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tear_fall_3d"
    )
    val sparklePulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_pulse_3d"
    )

    val bounceJumpDp by animateDpAsState(
        targetValue = when (moodState) {
            FahimMoodState.HAPPY_LAUGHING -> (-14).dp
            else -> 0.dp
        },
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 360f),
        label = "laugh_bounce_3d"
    )

    val avatarScale by animateFloatAsState(
        targetValue = when (moodState) {
            FahimMoodState.HAPPY_LAUGHING -> 1.08f
            FahimMoodState.ANNOYED_CRYING -> 0.95f
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

    val mascotSize = if (compactMode) 122.dp else 164.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                val cx = w * 0.5f
                val cy = h * 0.5f

                // Outer Atmospheric 3D Halo Glow (#38BDF8 / Mood color)
                val auraColors = when (moodState) {
                    FahimMoodState.ANNOYED_CRYING -> listOf(
                        MascotAngryRed.copy(alpha = 0.34f),
                        MascotAngryOrange.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                    FahimMoodState.HAPPY_LAUGHING -> listOf(
                        MascotGoldLaugh.copy(alpha = 0.34f),
                        MascotCyanNeon.copy(alpha = 0.16f),
                        Color.Transparent
                    )
                    FahimMoodState.FRIENDLY_IDLE -> listOf(
                        MascotCyanNeon.copy(alpha = 0.30f),
                        MascotDeepNavyMid.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = auraColors,
                        center = Offset(cx, cy * 0.96f),
                        radius = w * 0.49f * haloPulse
                    ),
                    radius = w * 0.50f,
                    center = Offset(cx, cy * 0.96f)
                )

                // Soft Floating Floor Shadow (shrinks slightly as mascot rises)
                val shadowScale = (1f - (idleFloatDp / 28f)).coerceIn(0.75f, 1.1f)
                drawOval(
                    color = Color(0xFF0F172A).copy(alpha = 0.18f),
                    topLeft = Offset(cx - w * 0.23f * shadowScale, h * 0.875f),
                    size = Size(w * 0.46f * shadowScale, h * 0.075f)
                )

                // 3D Glossy Royal/Navy Shoulders & Hoodie Cloak
                val torsoPath = Path().apply {
                    moveTo(cx - w * 0.28f, h * 0.87f)
                    cubicTo(cx - w * 0.29f, h * 0.63f, cx - w * 0.19f, h * 0.55f, cx, h * 0.55f)
                    cubicTo(cx + w * 0.19f, h * 0.55f, cx + w * 0.29f, h * 0.63f, cx + w * 0.28f, h * 0.87f)
                    close()
                }
                drawPath(
                    path = torsoPath,
                    brush = Brush.radialGradient(
                        colors = listOf(MascotRoyalBlueTop, MascotDeepNavyBottom, Color(0xFF0F172A)),
                        center = Offset(cx - w * 0.06f, h * 0.64f),
                        radius = w * 0.34f
                    )
                )
                // Glossy chest rim reflection
                drawLine(
                    color = if (moodState == FahimMoodState.ANNOYED_CRYING) MascotAngryRed else MascotCyanNeon,
                    start = Offset(cx, h * 0.65f),
                    end = Offset(cx, h * 0.87f),
                    strokeWidth = w * 0.022f,
                    cap = StrokeCap.Round
                )

                // Matte Titanium Headphone Band Arch
                drawArc(
                    brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
                    startAngle = 192f,
                    sweepAngle = 156f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.35f, h * 0.10f),
                    size = Size(w * 0.70f, h * 0.56f),
                    style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
                )

                val rimAccent = when (moodState) {
                    FahimMoodState.ANNOYED_CRYING -> MascotAngryRed
                    FahimMoodState.HAPPY_LAUGHING -> MascotGoldLaugh
                    FahimMoodState.FRIENDLY_IDLE -> MascotCyanNeon
                }

                // 3D Beveled Left & Right Headphone Ear Cups
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF090D16))),
                    topLeft = Offset(cx - w * 0.39f, h * 0.30f),
                    size = Size(w * 0.11f, h * 0.24f),
                    cornerRadius = CornerRadius(w * 0.055f, w * 0.055f)
                )
                drawRoundRect(
                    color = rimAccent,
                    topLeft = Offset(cx - w * 0.372f, h * 0.34f),
                    size = Size(w * 0.032f, h * 0.16f),
                    cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF090D16))),
                    topLeft = Offset(cx + w * 0.28f, h * 0.30f),
                    size = Size(w * 0.11f, h * 0.24f),
                    cornerRadius = CornerRadius(w * 0.055f, w * 0.055f)
                )
                drawRoundRect(
                    color = rimAccent,
                    topLeft = Offset(cx + w * 0.34f, h * 0.34f),
                    size = Size(w * 0.032f, h * 0.16f),
                    cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
                )

                // Outer 3D Hood Dome (Deep Glossy Royal-Blue & Navy)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3B82F6), MascotDeepNavyMid, Color(0xFF111C3A)),
                        center = Offset(cx - w * 0.09f, h * 0.22f),
                        radius = w * 0.44f
                    ),
                    topLeft = Offset(cx - w * 0.315f, h * 0.135f),
                    size = Size(w * 0.63f, h * 0.55f),
                    cornerRadius = CornerRadius(w * 0.27f, w * 0.27f)
                )
                // Glossy Specular Crown Highlight on Hood
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    topLeft = Offset(cx - w * 0.19f, h * 0.15f),
                    size = Size(w * 0.34f, h * 0.09f)
                )

                // Luminous Neon Inner Hood Rim
                drawRoundRect(
                    color = rimAccent,
                    topLeft = Offset(cx - w * 0.262f, h * 0.205f),
                    size = Size(w * 0.524f, h * 0.408f),
                    cornerRadius = CornerRadius(w * 0.205f, w * 0.205f),
                    style = Stroke(width = w * 0.026f)
                )

                // Deep Glassy Visor Screen Face
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF132038), Color(0xFF070C17))
                    ),
                    topLeft = Offset(cx - w * 0.242f, h * 0.225f),
                    size = Size(w * 0.484f, h * 0.368f),
                    cornerRadius = CornerRadius(w * 0.185f, w * 0.185f)
                )
                // Curved Glass Specular Reflection Arc
                drawArc(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                    ),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.20f, h * 0.245f),
                    size = Size(w * 0.40f, h * 0.14f),
                    style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
                )

                when (moodState) {
                    FahimMoodState.FRIENDLY_IDLE -> {
                        // Soft Glow Behind Cyan Eyes
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(MascotCyanNeon.copy(alpha = 0.32f), Color.Transparent)
                            ),
                            radius = w * 0.085f,
                            center = Offset(cx - w * 0.098f, h * 0.398f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(MascotCyanNeon.copy(alpha = 0.32f), Color.Transparent)
                            ),
                            radius = w * 0.085f,
                            center = Offset(cx + w * 0.098f, h * 0.398f)
                        )
                        // 3D Rounded Glowing Cyan Eyes
                        drawRoundRect(
                            color = MascotCyanNeon,
                            topLeft = Offset(cx - w * 0.136f, h * 0.344f),
                            size = Size(w * 0.078f, h * 0.108f),
                            cornerRadius = CornerRadius(w * 0.042f, w * 0.042f)
                        )
                        drawRoundRect(
                            color = MascotCyanNeon,
                            topLeft = Offset(cx + w * 0.058f, h * 0.344f),
                            size = Size(w * 0.078f, h * 0.108f),
                            cornerRadius = CornerRadius(w * 0.042f, w * 0.042f)
                        )
                        drawCircle(Color.White, radius = w * 0.016f, center = Offset(cx - w * 0.108f, h * 0.374f))
                        drawCircle(Color.White, radius = w * 0.016f, center = Offset(cx + w * 0.086f, h * 0.374f))

                        // Friendly Glowing Smile
                        drawArc(
                            color = MascotCyanNeon,
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx - w * 0.076f, h * 0.44f),
                            size = Size(w * 0.152f, h * 0.085f),
                            style = Stroke(width = w * 0.023f, cap = StrokeCap.Round)
                        )
                    }

                    FahimMoodState.HAPPY_LAUGHING -> {
                        val leftEye = Path().apply {
                            moveTo(cx - w * 0.15f, h * 0.415f)
                            quadraticTo(cx - w * 0.10f, h * 0.332f, cx - w * 0.05f, h * 0.415f)
                        }
                        val rightEye = Path().apply {
                            moveTo(cx + w * 0.05f, h * 0.415f)
                            quadraticTo(cx + w * 0.10f, h * 0.332f, cx + w * 0.15f, h * 0.415f)
                        }
                        drawPath(leftEye, color = MascotGoldLaugh, style = Stroke(width = w * 0.03f, cap = StrokeCap.Round))
                        drawPath(rightEye, color = MascotGoldLaugh, style = Stroke(width = w * 0.03f, cap = StrokeCap.Round))

                        drawArc(
                            color = MascotGoldLaugh,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - w * 0.09f, h * 0.44f),
                            size = Size(w * 0.18f, h * 0.10f)
                        )

                        // Golden Floating Celebration Stars
                        val starOffsets = listOf(
                            Offset(cx - w * 0.38f, h * 0.21f),
                            Offset(cx + w * 0.37f, h * 0.19f),
                            Offset(cx - w * 0.40f, h * 0.56f),
                            Offset(cx + w * 0.39f, h * 0.58f)
                        )
                        starOffsets.forEachIndexed { i, pos ->
                            val r = w * 0.044f * sparklePulse * (if (i % 2 == 0) 1f else 0.85f)
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
                            start = Offset(cx - w * 0.155f, h * 0.34f),
                            end = Offset(cx - w * 0.045f, h * 0.385f),
                            strokeWidth = w * 0.028f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = MascotAngryOrange,
                            start = Offset(cx + w * 0.155f, h * 0.34f),
                            end = Offset(cx + w * 0.045f, h * 0.385f),
                            strokeWidth = w * 0.028f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(MascotAngryOrange, radius = w * 0.036f, center = Offset(cx - w * 0.095f, h * 0.41f))
                        drawCircle(MascotAngryOrange, radius = w * 0.036f, center = Offset(cx + w * 0.095f, h * 0.41f))

                        drawArc(
                            color = MascotAngryOrange,
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
                        listOf(
                            Offset(cx - w * 0.125f, dropY1),
                            Offset(cx + w * 0.125f, dropY2)
                        ).forEach { d ->
                            val tearPath = Path().apply {
                                moveTo(d.x, d.y - w * 0.028f)
                                quadraticTo(d.x + w * 0.032f, d.y + w * 0.015f, d.x, d.y + w * 0.035f)
                                quadraticTo(d.x - w * 0.032f, d.y + w * 0.015f, d.x, d.y - w * 0.028f)
                                close()
                            }
                            drawPath(tearPath, color = MascotCyanNeon)
                        }

                        for (k in 0..2) {
                            val angle = Math.toRadians((-150 + k * 35).toDouble())
                            val r1 = w * 0.33f
                            val r2 = w * 0.40f
                            drawLine(
                                color = MascotAngryRed,
                                start = Offset(cx + (cos(angle) * r1).toFloat(), cy + (sin(angle) * r1).toFloat()),
                                end = Offset(cx + (cos(angle) * r2).toFloat(), cy + (sin(angle) * r2).toFloat()),
                                strokeWidth = w * 0.022f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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
                            tint = if (laughCount > 0) Color(0xFFD97706) else Color(0xFF1A6FE3),
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
                            color = if (laughCount > 0) Color(0xFF92400E) else Color(0xFF1A6FE3)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FahimTeacherAvatar3D(isDrawing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wb_avatar_3d")
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "wb_bob_3d"
    )
    Canvas(
        modifier = modifier
            .size(64.dp)
            .padding(top = (bob.coerceAtLeast(0f)).dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(MascotCyanNeon.copy(alpha = 0.28f), Color.Transparent)
            ),
            radius = w * 0.48f,
            center = Offset(cx, h * 0.5f)
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3B82F6), MascotDeepNavyMid, Color(0xFF111C3A)),
                center = Offset(cx - w * 0.08f, h * 0.22f),
                radius = w * 0.45f
            ),
            topLeft = Offset(cx - w * 0.30f, h * 0.16f),
            size = Size(w * 0.60f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.25f, w * 0.25f)
        )
        drawRoundRect(
            color = MascotCyanNeon,
            topLeft = Offset(cx - w * 0.25f, h * 0.22f),
            size = Size(w * 0.50f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF070C17),
            topLeft = Offset(cx - w * 0.22f, h * 0.25f),
            size = Size(w * 0.44f, h * 0.36f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )
        val eyeColor = if (isDrawing) MascotCyanNeon else MascotGoldLaugh
        drawCircle(eyeColor, radius = w * 0.048f, center = Offset(cx - w * 0.08f, h * 0.41f))
        drawCircle(eyeColor, radius = w * 0.048f, center = Offset(cx + w * 0.08f, h * 0.41f))
        // Glowing Smart Stylus Pen
        drawLine(
            color = Color(0xFF1A6FE3),
            start = Offset(w * 0.08f, h * 0.78f),
            end = Offset(w * 0.28f, h * 0.56f),
            strokeWidth = 3.4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
