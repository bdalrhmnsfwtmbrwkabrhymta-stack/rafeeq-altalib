package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Elastic Parabolic Jumping Ball & Trampoline Wave Bottom Navigation Bar
 * Implements 4-stage spring/projectile physics:
 * (A) Parabolic trajectory jump across tabs (360ms FastOutSlowInEasing, 38.dp apex)
 * (B) Cubic Bezier liquid trampoline cradle wave (low-bouncy spring d=0.42f, k=380f)
 * (C) 3D RadialGradient floating sphere (radius=26.dp) with impact squash & stretch (1.14x, 0.86y)
 * (D) Spring icon launch & outlined-to-filled white morph (d=0.5f, k=400f, scale=1.15f)
 */
@Composable
fun ElasticJumpingBottomBar(
    tabs: List<RafiqTab>,
    selectedIndex: Int,
    onTabSelected: (RafiqTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSelectedIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val isDarkSurface = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.45f
    }
    val barBgColor = if (isDarkSurface) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val barBorderColor = if (isDarkSurface) Color(0xFF334155) else Color(0xFFE2E8F0)
    val inactiveIconColor = if (isDarkSurface) Color(0xFFCBD5E1) else Color(0xFF1E1B18)
    val activeLabelColor = if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF1A6FE3)

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // Physics Animatables
    var startIndex by remember { mutableIntStateOf(safeSelectedIndex) }
    var targetIndex by remember { mutableIntStateOf(safeSelectedIndex) }
    val flightProgress = remember { Animatable(1f) }
    val cradleDepthAnim = remember { Animatable(1f) }
    val squashAnim = remember { Animatable(0f) }

    LaunchedEffect(safeSelectedIndex) {
        if (targetIndex == safeSelectedIndex && flightProgress.value == 1f) return@LaunchedEffect

        // Capture visual origin cleanly even if interrupted mid-flight
        val currentVisualFrac = lerp(
            startIndex.toFloat(),
            targetIndex.toFloat(),
            flightProgress.value
        )
        startIndex = kotlin.math.round(currentVisualFrac).toInt().coerceIn(0, tabs.lastIndex)
        targetIndex = safeSelectedIndex

        // Release old trampoline tension smoothly as ball launches
        flightProgress.snapTo(0f)
        squashAnim.snapTo(0f)

        val releaseJob = launch {
            cradleDepthAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
        }

        // (A) Parabolic Projectile Flight across tabs (360ms)
        val flightJob = launch {
            flightProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }

        // Wait until ball reaches landing threshold (flightProgress >= 0.75f ~ 270ms)
        delay(255L)
        releaseJob.cancel()

        // (B) Trampoline Wave Dip + (C) Squash & Stretch Spring
        launch {
            cradleDepthAnim.animateTo(
                targetValue = 1f,
                initialVelocity = 11.5f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = 380f
                )
            )
        }
        launch {
            squashAnim.snapTo(1f)
            squashAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = 380f
                )
            )
        }

        flightJob.join()
    }

    val topOverhangDp = 28.dp
    val barHeightDp = 68.dp
    val totalHeightDp = topOverhangDp + barHeightDp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .height(totalHeightDp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Layer 1: Liquid Trampoline Wave Surface + 3D Floating Jumping Sphere Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
        ) {
            val count = tabs.size.coerceAtLeast(1)
            val totalWidth = size.width
            val slotWidth = totalWidth / count.toFloat()

            fun centerXFor(index: Int): Float {
                val clamped = index.coerceIn(0, count - 1)
                return if (isRtl) {
                    totalWidth - slotWidth * (clamped + 0.5f)
                } else {
                    slotWidth * (clamped + 0.5f)
                }
            }

            val startX = centerXFor(startIndex)
            val targetX = centerXFor(targetIndex)
            val p = flightProgress.value.coerceIn(0f, 1f)

            // (A) Horizontal & Parabolic Jump Arc Position
            val ballX = lerp(startX, targetX, p)
            val maxJumpHeight = 38.dp.toPx()
            val jumpArcY = -4f * maxJumpHeight * p * (1f - p)

            val ballRadius = 26.dp.toPx()
            val barTopY = topOverhangDp.toPx()
            val cornerRadius = 24.dp.toPx()
            val barBottomY = size.height

            // Active cradle smoothly tracks origin while collapsing (<0.55) then target on impact (>=0.55)
            val cradleX = if (p < 0.65f) startX else targetX
            val cradleDepthFactor = cradleDepthAnim.value.coerceAtLeast(0f)
            val cradleMouthHalf = (ballRadius * 2.5f) * 0.72f
            val cradleDepth = (ballRadius * 0.94f) * cradleDepthFactor

            // (B) Draw Liquid Trampoline Wave Bar Path with Cubic Béziers
            val barPath = Path().apply {
                moveTo(cornerRadius, barTopY)

                val leftEdge = (cradleX - cradleMouthHalf).coerceAtLeast(cornerRadius)
                val rightEdge = (cradleX + cradleMouthHalf).coerceAtMost(totalWidth - cornerRadius)

                if (cradleDepth > 1f && rightEdge > leftEdge + 8f) {
                    lineTo(leftEdge, barTopY)
                    val bottomY = barTopY + cradleDepth
                    cubicTo(
                        leftEdge + (cradleX - leftEdge) * 0.48f, barTopY,
                        cradleX - ballRadius * 0.90f, bottomY,
                        cradleX, bottomY
                    )
                    cubicTo(
                        cradleX + ballRadius * 0.90f, bottomY,
                        rightEdge - (rightEdge - cradleX) * 0.48f, barTopY,
                        rightEdge, barTopY
                    )
                }

                lineTo(totalWidth - cornerRadius, barTopY)
                quadraticTo(totalWidth, barTopY, totalWidth, barTopY + cornerRadius)
                lineTo(totalWidth, barBottomY - cornerRadius)
                quadraticTo(totalWidth, barBottomY, totalWidth - cornerRadius, barBottomY)
                lineTo(cornerRadius, barBottomY)
                quadraticTo(0f, barBottomY, 0f, barBottomY - cornerRadius)
                lineTo(0f, barTopY + cornerRadius)
                quadraticTo(0f, barTopY, cornerRadius, barTopY)
                close()
            }

            // Soft drop shadow behind bar
            drawPath(
                path = barPath,
                color = Color.Black.copy(alpha = if (isDarkSurface) 0.28f else 0.08f)
            )
            drawPath(
                path = barPath,
                color = barBgColor
            )
            drawPath(
                path = barPath,
                color = barBorderColor,
                style = Stroke(width = 1.dp.toPx())
            )

            // (C) Resting position nests inside the trampoline cradle
            val restingBallY = barTopY + 2.dp.toPx() + (cradleDepth * 0.12f)
            val ballY = restingBallY + jumpArcY

            // Dynamic Ground Shadow beneath the 3D sphere
            val heightRatio = (1f - (-jumpArcY / maxJumpHeight).coerceIn(0f, 1f))
            val shadowRadiusX = ballRadius * lerp(0.55f, 0.96f, heightRatio)
            val shadowRadiusY = ballRadius * lerp(0.14f, 0.28f, heightRatio)
            drawOval(
                color = Color(0xFF0F172A).copy(alpha = lerp(0.08f, 0.24f, heightRatio)),
                topLeft = Offset(
                    x = ballX - shadowRadiusX,
                    y = restingBallY + ballRadius * 0.85f
                ),
                size = Size(shadowRadiusX * 2f, shadowRadiusY * 2f)
            )

            // Squash & Stretch scaling on impact
            val s = squashAnim.value
            val stretchScaleX = 1f + 0.14f * s
            val stretchScaleY = 1f - 0.14f * s

            withTransform({
                scale(
                    scaleX = stretchScaleX,
                    scaleY = stretchScaleY,
                    pivot = Offset(ballX, ballY)
                )
            }) {
                // 3D Sphere RadialGradient matching Hawas El-Dirasa identity
                val sphereBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8), // Luminous sky blue top highlight
                        Color(0xFF1A6FE3), // Royal brand blue core
                        Color(0xFF0F172A)  // Deep navy rim shading
                    ),
                    center = Offset(ballX - ballRadius * 0.26f, ballY - ballRadius * 0.34f),
                    radius = ballRadius * 1.48f
                )
                drawCircle(
                    brush = sphereBrush,
                    radius = ballRadius,
                    center = Offset(ballX, ballY)
                )
                // Glossy inner rim stroke
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = ballRadius - 1.dp.toPx(),
                    center = Offset(ballX, ballY),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        // Layer 2: Interactive Tab Slots & Spring-Launched Morphing Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp),
            verticalAlignment = Alignment.Bottom
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == safeSelectedIndex

                // (D) Icon launch spring upward into heart of the floating 3D sphere
                val iconOffsetY by animateDpAsState(
                    targetValue = if (isSelected) (-21).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = 400f
                    ),
                    label = "tabIconLift_$index"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = 400f
                    ),
                    label = "tabIconScale_$index"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) Color.White else inactiveIconColor,
                    animationSpec = tween(durationMillis = 220),
                    label = "tabIconColor_$index"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) activeLabelColor else inactiveIconColor.copy(alpha = 0.85f),
                    animationSpec = tween(durationMillis = 220),
                    label = "tabLabelColor_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeightDp)
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        )
                        .testTag("bottom_tab_${tab.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                            contentDescription = tab.titleAr,
                            tint = iconTint,
                            modifier = Modifier
                                .offset(y = iconOffsetY)
                                .scale(iconScale)
                                .size(23.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.titleAr,
                            fontFamily = ReadexProFontFamily,
                            fontSize = if (isSelected) 11.5.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
