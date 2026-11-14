package com.example.ui.screens.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Isolated design tokens for «المكتبة الدراسية» (Study Library Container).
 * Inspired strictly by the two reference visuals:
 * 1) Frosted Glassmorphism Folders (Light snow glass + Dark luxury glass with fluid gradient waves)
 * 2) Modern Arabic Geometric Poster (Subtle graph-paper grid background, bold display type, #FACC15 yellow highlights)
 *
 * Completely isolated from the rest of the app's MaterialTheme & Type tokens.
 */
object StudyLibraryColors {
    // Poster Vivid Yellow Accent (#FACC15)
    val AccentYellow = Color(0xFFFACC15)
    val AccentYellowSoft = Color(0xFFFEF08A)
    val AccentYellowInk = Color(0xFF111827)

    // Light Mode (Snowy Frosted Glass & Architectural Paper Grid)
    val LightCanvasBg = Color(0xFFF7F7F4)
    val LightGridLine = Color(0xFFE6E6DF)
    val LightGridDot = Color(0xFFD4D4CB)
    val LightInkPrimary = Color(0xFF0F172A)
    val LightInkSecondary = Color(0xFF475569)
    val LightSurfaceCard = Color(0xFFFFFFFF)
    val LightGlassTop = Color(0xD6FFFFFF)      // ~84% frosty white
    val LightGlassBottom = Color(0xB8F8FAFC)   // ~72% frosty white
    val LightGlassBorder = Color(0x99FFFFFF)
    val LightGlassInnerRim = Color(0xCCFFFFFF)
    val LightBadgeBg = Color(0xFF111827)
    val LightBadgeContent = Color(0xFFFFFFFF)
    val LightPillBadgeBg = Color(0xFFE5E7EB)
    val LightPillBadgeBorder = Color(0xFFD1D5DB)

    // Dark Mode (Luxury Smoked Glass & Deep Studio Grid)
    val DarkCanvasBg = Color(0xFF0A0E17)
    val DarkGridLine = Color(0xFF161F30)
    val DarkGridDot = Color(0xFF223049)
    val DarkInkPrimary = Color(0xFFF8FAFC)
    val DarkInkSecondary = Color(0xFF94A3B8)
    val DarkSurfaceCard = Color(0xFF121826)
    val DarkGlassTop = Color(0xCC1B2230)       // ~80% smoked dark glass
    val DarkGlassBottom = Color(0xE60B0F17)    // ~90% deep smoked glass
    val DarkGlassBorder = Color(0x38FFFFFF)
    val DarkGlassInnerRim = Color(0x4DFFFFFF)
    val DarkBadgeBg = Color(0xFFFACC15)
    val DarkBadgeContent = Color(0xFF111827)
    val DarkPillBadgeBg = Color(0xFF1E293B)
    val DarkPillBadgeBorder = Color(0xFF334155)
}

data class StudyLibraryPalette(
    val isDark: Boolean,
    val canvasBg: Color,
    val gridLine: Color,
    val gridDot: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val surfaceCard: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val glassInnerRim: Color,
    val badgeBg: Color,
    val badgeContent: Color,
    val pillBadgeBg: Color,
    val pillBadgeBorder: Color,
    val accentYellow: Color = StudyLibraryColors.AccentYellow,
    val accentYellowInk: Color = StudyLibraryColors.AccentYellowInk
)

fun studyLibraryPalette(isDark: Boolean): StudyLibraryPalette {
    return if (isDark) {
        StudyLibraryPalette(
            isDark = true,
            canvasBg = StudyLibraryColors.DarkCanvasBg,
            gridLine = StudyLibraryColors.DarkGridLine,
            gridDot = StudyLibraryColors.DarkGridDot,
            inkPrimary = StudyLibraryColors.DarkInkPrimary,
            inkSecondary = StudyLibraryColors.DarkInkSecondary,
            surfaceCard = StudyLibraryColors.DarkSurfaceCard,
            glassTop = StudyLibraryColors.DarkGlassTop,
            glassBottom = StudyLibraryColors.DarkGlassBottom,
            glassBorder = StudyLibraryColors.DarkGlassBorder,
            glassInnerRim = StudyLibraryColors.DarkGlassInnerRim,
            badgeBg = StudyLibraryColors.DarkBadgeBg,
            badgeContent = StudyLibraryColors.DarkBadgeContent,
            pillBadgeBg = StudyLibraryColors.DarkPillBadgeBg,
            pillBadgeBorder = StudyLibraryColors.DarkPillBadgeBorder
        )
    } else {
        StudyLibraryPalette(
            isDark = false,
            canvasBg = StudyLibraryColors.LightCanvasBg,
            gridLine = StudyLibraryColors.LightGridLine,
            gridDot = StudyLibraryColors.LightGridDot,
            inkPrimary = StudyLibraryColors.LightInkPrimary,
            inkSecondary = StudyLibraryColors.LightInkSecondary,
            surfaceCard = StudyLibraryColors.LightSurfaceCard,
            glassTop = StudyLibraryColors.LightGlassTop,
            glassBottom = StudyLibraryColors.LightGlassBottom,
            glassBorder = StudyLibraryColors.LightGlassBorder,
            glassInnerRim = StudyLibraryColors.LightGlassInnerRim,
            badgeBg = StudyLibraryColors.LightBadgeBg,
            badgeContent = StudyLibraryColors.LightBadgeContent,
            pillBadgeBg = StudyLibraryColors.LightPillBadgeBg,
            pillBadgeBorder = StudyLibraryColors.LightPillBadgeBorder
        )
    }
}

val LocalStudyLibraryPalette = staticCompositionLocalOf { studyLibraryPalette(false) }

object StudyLibraryTypography {
    val DisplayFont = FontFamily(
        Font(R.font.changa, FontWeight.Normal),
        Font(R.font.changa, FontWeight.Bold),
        Font(R.font.changa, FontWeight.ExtraBold)
    )

    val BodyFont = FontFamily(
        Font(R.font.readex_pro, FontWeight.Normal),
        Font(R.font.cairo, FontWeight.Medium),
        Font(R.font.cairo, FontWeight.Bold)
    )
}

/**
 * Isolated container implementing `.study-library-container`:
 * Draws the subtle architectural grid paper background from Poster Image 2
 * and provides the scoped palette for all child Study Library composables.
 */
@Composable
fun StudyLibraryContainer(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = studyLibraryPalette(isDark)
    CompositionLocalProvider(LocalStudyLibraryPalette provides palette) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.canvasBg)
        ) {
            // Subtle architectural grid background (inspired by Poster Image 2 & Folder Image 1 dots)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = 24.dp.toPx()
                val lineStroke = 0.8.dp.toPx()

                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = palette.gridLine,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineStroke
                    )
                    x += cellSize
                }

                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = palette.gridLine,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = lineStroke
                    )
                    y += cellSize
                }

                // Subtle intersection micro-dots for tactile depth
                val dotRadius = 1.dp.toPx()
                var dx = 0f
                while (dx <= size.width) {
                    var dy = 0f
                    while (dy <= size.height) {
                        drawCircle(
                            color = palette.gridDot.copy(alpha = 0.45f),
                            radius = dotRadius,
                            center = Offset(dx, dy)
                        )
                        dy += cellSize
                    }
                    dx += cellSize
                }
            }

            content()
        }
    }
}

/**
 * Yellow Highlight Block behind key Arabic words (inspired by Poster Image 2).
 */
@Composable
fun PosterYellowHighlight(
    text: String,
    fontSize: TextUnit = 20.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(StudyLibraryColors.AccentYellow)
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = StudyLibraryTypography.DisplayFont,
                fontWeight = fontWeight,
                fontSize = fontSize,
                color = StudyLibraryColors.AccentYellowInk
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
        )
    }
}
