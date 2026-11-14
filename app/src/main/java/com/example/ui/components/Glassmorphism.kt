package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 3) Glassmorphism:
 * Translucent white background with light blur-effect style and thin white border.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.20f),
    borderColor: Color = Color.White.copy(alpha = 0.45f),
    borderWidth: Dp = 1.dp,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = (backgroundColor.alpha * 1.25f).coerceAtMost(1f)),
                        backgroundColor.copy(alpha = (backgroundColor.alpha * 0.85f).coerceAtMost(1f))
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = borderColor.alpha * 0.35f)
                    )
                ),
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun GlassCircleButton(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.22f),
    borderColor: Color = Color.White.copy(alpha = 0.50f),
    elevation: Dp = 4.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    GlassBox(
        modifier = modifier,
        shape = CircleShape,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = 1.2.dp,
        elevation = elevation,
        onClick = onClick,
        content = content
    )
}
