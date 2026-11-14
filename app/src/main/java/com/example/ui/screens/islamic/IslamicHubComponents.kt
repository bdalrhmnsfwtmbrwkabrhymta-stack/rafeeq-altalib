package com.example.ui.screens.islamic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.DisplayArabicFontFamily

enum class IslamicSubSection {
    HUB,
    PRAYER_TIMES,
    ADHAN,
    QIBLA,
    AZKAR,
    SALAWAT,
    TASBEEH,
    HIFZ,
    PRAYER_LOG
}

/**
 * Custom crisp geometric Kaaba line icon preserved for QiblaCompassScreen
 */
@Composable
fun KaabaLineIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(24.dp),
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        val roofRim = Path().apply {
            moveTo(w * 0.24f, h * 0.16f)
            lineTo(w * 0.76f, h * 0.16f)
            lineTo(w * 0.80f, h * 0.26f)
            lineTo(w * 0.20f, h * 0.26f)
            close()
        }
        drawPath(
            path = roofRim,
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, h * 0.26f),
            size = Size(w * 0.60f, h * 0.60f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.41f),
            end = Offset(w * 0.80f, h * 0.41f),
            strokeWidth = sw * 0.9f
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.49f),
            end = Offset(w * 0.80f, h * 0.49f),
            strokeWidth = sw * 0.7f
        )

        val door = Path().apply {
            moveTo(w * 0.42f, h * 0.86f)
            lineTo(w * 0.42f, h * 0.66f)
            cubicTo(w * 0.42f, h * 0.60f, w * 0.58f, h * 0.60f, w * 0.58f, h * 0.66f)
            lineTo(w * 0.58f, h * 0.86f)
        }
        drawPath(door, color = tint, style = Stroke(width = sw * 0.9f, cap = StrokeCap.Round))
    }
}

/**
 * Symmetrical RTL Top Bar matching the clean white header in reference screens
 */
@Composable
fun IslamicTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .testTag("islamic_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = title,
                fontFamily = DisplayArabicFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 56.dp)
            )

            if (trailingAction != null) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    trailingAction()
                }
            }
        }
    }
}

/**
 * Organic Pastel Aura ("أطياف") Islamic Hub Card:
 * Zero rigid square cage around the icon — feathered pastel aura blooms directly inside the card
 * so the colorful multi-tone illustration melts seamlessly into the surface.
 */
@Composable
fun IslamicHubGridCard(
    title: String,
    subtitle: String,
    auraPrimaryColor: Color,
    auraSecondaryColor: Color = auraPrimaryColor,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = (surfaceColor.red * 0.299f + surfaceColor.green * 0.587f + surfaceColor.blue * 0.114f) < 0.45f
    val cardShape = RoundedCornerShape(24.dp)

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = surfaceColor,
        shadowElevation = if (isDark) 3.dp else 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) {
                auraPrimaryColor.copy(alpha = 0.24f)
            } else {
                auraPrimaryColor.copy(alpha = 0.14f)
            }
        ),
        modifier = modifier
            .height(166.dp)
            .shadow(
                elevation = if (isDark) 4.dp else 6.dp,
                shape = cardShape,
                spotColor = auraPrimaryColor.copy(alpha = 0.14f)
            )
            .testTag(testTag)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Soft organic pastel aura ("أطياف خفيفة") woven into the card background — zero boxes!
            Canvas(modifier = Modifier.fillMaxSize()) {
                val auraCenter = Offset(size.width * 0.19f, size.height * 0.24f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraPrimaryColor.copy(alpha = if (isDark) 0.24f else 0.18f),
                            auraSecondaryColor.copy(alpha = if (isDark) 0.10f else 0.08f),
                            Color.Transparent
                        ),
                        center = auraCenter,
                        radius = size.width * 0.56f
                    ),
                    radius = size.width * 0.56f,
                    center = auraCenter
                )
                // Gentle floating secondary pastel orb at bottom opposite corner
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraSecondaryColor.copy(alpha = if (isDark) 0.10f else 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.90f),
                        radius = size.width * 0.34f
                    ),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.88f, size.height * 0.90f)
                )
                // Subtle delicate aura dust sparkles
                drawCircle(
                    color = auraPrimaryColor.copy(alpha = if (isDark) 0.35f else 0.22f),
                    radius = 2.6.dp.toPx(),
                    center = Offset(size.width * 0.39f, size.height * 0.16f)
                )
                drawCircle(
                    color = auraSecondaryColor.copy(alpha = if (isDark) 0.28f else 0.18f),
                    radius = 2.dp.toPx(),
                    center = Offset(size.width * 0.10f, size.height * 0.42f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                // Open floating multi-tone icon directly inside the feathered pastel aura
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = title,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 23.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontFamily = CairoFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
