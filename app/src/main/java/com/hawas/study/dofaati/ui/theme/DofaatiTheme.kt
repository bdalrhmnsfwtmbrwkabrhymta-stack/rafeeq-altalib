package com.hawas.study.dofaati.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.example.ui.theme.ReadexProFontFamily

object DofaatiColors {
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFCBD5E1)
    val BorderSoft = Color(0xFFE2E8F0)
    val RoyalBlue = Color(0xFF1A6FE3)
    val RoyalBlueDark = Color(0xFF1E40AF)
    val RoyalBlueSoft = Color(0xFFEFF6FF)
    val RoyalBlueTint = Color(0xFFDBEAFE)
    val NavyInk = Color(0xFF0F172A)
    val SlateBody = Color(0xFF475569)
    val SlateMuted = Color(0xFF64748B)
    val PinnedOrange = Color(0xFFF97316)
    val AmberGold = Color(0xFFD97706)
    val BrightGold = Color(0xFFF59E0B)
    val EmeraldGreen = Color(0xFF10B981)
    val RedAlert = Color(0xFFEF4444)
    val PurpleNode = Color(0xFF8B5CF6)
    val HeroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A6FE3), Color(0xFF155BC0))
    )
    val QuizDarkGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A1628), Color(0xFF132F66), Color(0xFF0F172A))
    )
}

@Composable
fun DofaatiRoyalBlueHeader(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    testTag: String = "dofaati_royal_header"
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = DofaatiColors.RoyalBlue,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("${testTag}_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    AutoSizeText(
                        text = title,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = 18.sp,
                        minFontSize = 13.5.sp,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (trailingContent != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        content = trailingContent
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
fun DofaatiCard(
    modifier: Modifier = Modifier,
    borderColor: Color = DofaatiColors.Border,
    borderWidth: Dp = 1.dp,
    containerColor: Color = DofaatiColors.Surface,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Enforces strict Left-to-Right layout direction for 8-digit batch codes (e.g. 4861 3927),
 * audio countdown timers, and mathematical formulas inside Arabic RTL layouts.
 */
@Composable
fun DofaatiLtrText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = DofaatiColors.RoyalBlue,
    letterSpacing: TextUnit = 1.sp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            fontFamily = ReadexProFontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            color = color,
            maxLines = 1,
            modifier = modifier
        )
    }
}

/**
 * Interactive Canvas Audio Waveform with progress fill matching Screenshots 3, 4 & 5.
 */
@Composable
fun DofaatiAudioWaveform(
    peaks: List<Float>,
    progressFraction: Float,
    activeColor: Color = DofaatiColors.RoyalBlue,
    inactiveColor: Color = DofaatiColors.RoyalBlueTint,
    modifier: Modifier = Modifier
) {
    val safePeaks = if (peaks.isNotEmpty()) peaks else listOf(0.35f, 0.7f, 0.5f, 0.9f, 0.4f, 0.8f, 0.6f, 0.95f, 0.45f, 0.75f, 0.55f, 0.85f, 0.5f, 0.7f, 0.4f, 0.65f)
    val clampedProgress = progressFraction.coerceIn(0f, 1f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val count = safePeaks.size
            val slotWidth = size.width / count.toFloat()
            val barWidth = (slotWidth * 0.56f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val centerY = size.height / 2f

            safePeaks.forEachIndexed { index, amp ->
                val barFraction = (index + 0.5f) / count.toFloat()
                val color = if (barFraction <= clampedProgress) activeColor else inactiveColor
                val barHeight = (size.height * amp.coerceIn(0.18f, 1f))
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

/**
 * Responsive container wrapping BoxWithConstraints + safeDrawingPadding.
 * Automatically adapts to balanced two-column Row when phone/tablet is rotated to Landscape (maxWidth > maxHeight).
 */
@Composable
fun DofaatiAdaptiveScreenContainer(
    modifier: Modifier = Modifier,
    useSafeDrawing: Boolean = true,
    landscapeLeftWeight: Float = 0.45f,
    portraitContent: @Composable () -> Unit,
    landscapeLeftPane: @Composable () -> Unit,
    landscapeRightPane: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DofaatiColors.Background)
            .then(if (useSafeDrawing) Modifier.safeDrawingPadding() else Modifier)
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 520.dp
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(landscapeLeftWeight)
                        .fillMaxHeight()
                ) {
                    landscapeLeftPane()
                }
                Box(
                    modifier = Modifier
                        .weight(1f - landscapeLeftWeight)
                        .fillMaxHeight()
                ) {
                    landscapeRightPane()
                }
            }
        } else {
            portraitContent()
        }
    }
}

@Composable
fun DofaatiAmiriTitleText(
    text: String,
    color: Color = DofaatiColors.RoyalBlue,
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontFamily = HeaderBrandAmiriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = color,
        modifier = modifier
    )
}

fun formatDurationLtr(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(java.util.Locale.US, mins, secs)
}
