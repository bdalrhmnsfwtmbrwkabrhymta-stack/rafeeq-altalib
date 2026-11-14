package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 2) Premium3DIcon:
 * - Icon inside circle / rounded rectangle (container 36-44.dp, icon 20-22.dp)
 * - Translucent gradient + colored shadow + specular glint point + optional shimmer light ray
 */
@Composable
fun Premium3DIcon(
    icon: ImageVector,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    containerSize: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    contentDescription: String? = null,
    enablePulse: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "iconEffects")
    val pulseScale by if (enablePulse) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        remember { Animatable(1f) }.asState()
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .scale(pulseScale)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = primaryColor.copy(alpha = 0.35f),
                spotColor = primaryColor.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.85f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.95f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        primaryColor.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            )
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle diagonal 3D shine overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(20f, 20f),
                        radius = 45f
                    )
                )
        )

        // Specular glint dot (top-start corner)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 4) ShimmerRayOverlay:
 * - Moving diagonal light ray sweeping across the element (duration ≈ 2600ms)
 */
@Composable
fun ShimmerRayOverlay(
    modifier: Modifier = Modifier,
    durationMs: Int = 2600,
    rayWidthRatio: Float = 0.35f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerRay")
    val sweepPosition by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "raySweep"
    )

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    (sweepPosition - rayWidthRatio / 2).coerceIn(0f, 1f) to Color.Transparent,
                    sweepPosition.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.45f),
                    (sweepPosition + rayWidthRatio / 2).coerceIn(0f, 1f) to Color.Transparent,
                    1.0f to Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        )
    )
}
