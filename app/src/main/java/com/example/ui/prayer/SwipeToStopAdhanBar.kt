package com.example.ui.prayer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DisplayArabicFontFamily
import kotlin.math.roundToInt

/**
 * RTL-native Swipe-Left Bar for dismissing the Adhan screen & stopping playback cleanly.
 *
 * Layout inside RTL:
 * - Thumb (52.dp circle) starts anchored at the far RIGHT (`Alignment.CenterStart` in RTL).
 * - Swiping RIGHT-TO-LEFT (`dragLeftPx` increasing toward `maxTravelPx`) progressively fills
 *   the track in glowing Royal Blue -> Emerald gradient.
 * - Releasing beyond 65% of track width OR performing a long-press triggers HapticFeedback
 *   and invokes `onDismiss()`.
 */
@Composable
fun SwipeToStopAdhanBar(
    label: String = "اسحب لليسار لإيقاف الأذان",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val thumbDiameterDp = 52.dp
    val trackHeightDp = 64.dp
    val innerPaddingDp = 6.dp

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragLeftPx by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    val thumbSizePx = with(density) { thumbDiameterDp.toPx() }
    val paddingPx = with(density) { (innerPaddingDp * 2).toPx() }
    val maxTravelPx = (containerWidthPx - thumbSizePx - paddingPx).coerceAtLeast(160f)

    val animatedDragPx by animateFloatAsState(
        targetValue = if (dismissed) maxTravelPx else dragLeftPx,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "swipe_thumb_offset"
    )
    val swipeFraction = (animatedDragPx / maxTravelPx).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "swipe_chevrons")
    val chevronPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron_wave"
    )
    val nudgeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thumb_breathing"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0x880F172A),
            border = BorderStroke(
                width = 1.2.dp,
                color = Color(0x44FFFFFF)
            ),
            shadowElevation = 10.dp,
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .height(trackHeightDp)
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!dismissed) {
                                dismissed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            }
                        }
                    )
                }
                .testTag("adhan_swipe_dismiss_pill")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
            ) {
                // Active track fill expanding smoothly from RIGHT to LEFT as user drags
                val activeFillFraction = if (containerWidthPx > 0f) {
                    ((thumbSizePx + animatedDragPx + paddingPx) / containerWidthPx).coerceIn(0f, 1f)
                } else {
                    0f
                }

                if (swipeFraction > 0.02f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart) // Right edge in RTL
                            .fillMaxHeight()
                            .fillMaxWidth(activeFillFraction)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF059669).copy(alpha = 0.72f + swipeFraction * 0.26f),
                                        Color(0xFF1D4ED8).copy(alpha = 0.72f + swipeFraction * 0.26f)
                                    )
                                )
                            )
                    )
                }

                // Centered Prompt + 3 Animated Sequential Chevrons <<<
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = thumbDiameterDp + 12.dp, end = 16.dp)
                        .graphicsLayer {
                            alpha = (1f - swipeFraction * 1.15f).coerceIn(0f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // In RTL Row, index 0 is rightmost and index 2 is leftmost -> animates R to L cleanly!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-6).dp)
                    ) {
                        for (idx in 0 until 3) {
                            val wave = ((chevronPhase * 3f - idx * 0.6f) % 3f + 3f) % 3f
                            val chevronAlpha = (1f - (wave / 1.8f)).coerceIn(0.25f, 1f)
                            val shiftX = -wave * 2.5f
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24).copy(alpha = chevronAlpha),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { translationX = shiftX }
                            )
                        }
                    }
                }

                // 52.dp White Draggable Thumb anchored on the RIGHT (Alignment.CenterStart in RTL).
                // Modifier.offset is RTL-aware, so +totalLeftPx shifts cleanly RIGHT -> LEFT.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = innerPaddingDp)
                        .offset {
                            val totalLeftPx = animatedDragPx + if (dragLeftPx == 0f && !dismissed) nudgeOffset else 0f
                            IntOffset(totalLeftPx.roundToInt(), 0)
                        }
                        .size(thumbDiameterDp)
                        .clip(CircleShape)
                        .background(
                            if (swipeFraction >= 0.65f || dismissed) Color(0xFF10B981) else Color.White
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                        .pointerInput(maxTravelPx) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    if (!dismissed) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    if (!dismissed && dragLeftPx >= maxTravelPx * 0.65f) {
                                        dismissed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDismiss()
                                    } else if (!dismissed) {
                                        dragLeftPx = 0f
                                    }
                                },
                                onDragCancel = {
                                    if (!dismissed) dragLeftPx = 0f
                                }
                            ) { change, dragAmount ->
                                if (dismissed) return@detectHorizontalDragGestures
                                change.consume()
                                // Swiping from Right -> Left gives negative `dragAmount` in screen coordinates
                                val nextLeft = dragLeftPx - dragAmount
                                dragLeftPx = nextLeft.coerceIn(0f, maxTravelPx)
                            }
                        }
                        .testTag("adhan_swipe_thumb"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = if (swipeFraction >= 0.65f || dismissed) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = label,
                            tint = if (swipeFraction >= 0.65f || dismissed) Color.White else Color(0xFF1E3A8A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
