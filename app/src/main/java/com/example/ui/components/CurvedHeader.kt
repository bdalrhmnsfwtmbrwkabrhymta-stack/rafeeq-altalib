package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Custom smooth curved bottom shape (curveHeight ≈ 44.dp)
 */
val CurvedBottomShape: (Dp) -> GenericShape = { curveHeight ->
    GenericShape { size, _ ->
        val curvePx = curveHeight.value * 2.8f
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height - curvePx)
        // Cubic bezier or quadratic curve across bottom
        quadraticTo(
            size.width / 2f, size.height,
            0f, size.height - curvePx
        )
        close()
    }
}

/**
 * 1) CurvedHeader:
 * - Vertical gradient according to active screen (Emerald / Blue / Crimson / Teal / Orange / Purple)
 * - Smooth bottom curve (curveHeight ≈ 44.dp)
 * - Top specular highlight layer
 * - Optional hero banner image inside header on Home screen
 * - Glass circle buttons with Material icons & 3D effect
 */
@Composable
fun CurvedHeader(
    title: String,
    subtitle: String? = null,
    gradientColors: List<Color>,
    curveHeight: Dp = 44.dp,
    showHeroBanner: Boolean = false,
    heroImageResId: Int = R.drawable.study_hero_banner,
    onMenuClick: (() -> Unit)? = null,
    onNotificationClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val shape = CurvedBottomShape(curveHeight)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = gradientColors
                )
            )
    ) {
        // Optional Hero Banner illustration layer with blend
        if (showHeroBanner) {
            Image(
                painter = painterResource(id = heroImageResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.22f)
            )
        }

        // Top specular highlight layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Header Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = curveHeight + 10.dp)
        ) {
            // Top action bar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading (Menu / Drawer button)
                if (onMenuClick != null) {
                    GlassCircleButton(
                        size = 42.dp,
                        onClick = onMenuClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "القائمة",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(42.dp))
                }

                // Title in center / start
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // Trailing actions or notifications
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (actions != null) {
                        actions()
                    } else if (onNotificationClick != null) {
                        GlassCircleButton(
                            size = 42.dp,
                            onClick = onNotificationClick
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "الإشعارات",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(42.dp))
                    }
                }
            }

            // Extra Content inside curved header (e.g. stats, search bar, greeting)
            if (extraContent != null) {
                Spacer(modifier = Modifier.height(16.dp))
                extraContent()
            }
        }
    }
}
