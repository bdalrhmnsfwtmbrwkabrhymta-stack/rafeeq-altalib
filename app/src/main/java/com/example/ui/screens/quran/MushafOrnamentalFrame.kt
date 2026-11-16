package com.example.ui.screens.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.quran.MushafPagePagination
import com.example.ui.screens.formatArabicDigits
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.example.ui.theme.ReadexProFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-page ornamental Islamic Mushaf frame in Royal Blue (#1A6FE3) and Warm Gold (#D97706)
 * surrounding a crisp white (#FFFFFF) reading page canvas.
 */
@Composable
fun MushafOrnamentalFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val blueBand = QuranPalette.RoyalBlue
    val blueDeep = QuranPalette.RoyalBlueDark
    val gold = QuranPalette.GoldAccent
    val goldBright = QuranPalette.GoldBorder

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Geometric Islamic frame drawn crisply on Canvas behind page content
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val outerBand = 16.dp.toPx()
            val goldLine = 2.dp.toPx()

            // Outer royal blue geometric frame border
            drawRect(
                color = blueBand,
                topLeft = Offset.Zero,
                size = Size(w, h)
            )

            // Crisp white inner reading area bordered by warm gold trim
            val innerLeft = outerBand + goldLine
            val innerTop = outerBand + goldLine
            val innerW = (w - 2f * innerLeft).coerceAtLeast(1f)
            val innerH = (h - 2f * innerTop).coerceAtLeast(1f)

            drawRect(
                color = goldBright,
                topLeft = Offset(outerBand, outerBand),
                size = Size((w - 2f * outerBand).coerceAtLeast(1f), (h - 2f * outerBand).coerceAtLeast(1f))
            )

            drawRect(
                color = Color.White,
                topLeft = Offset(innerLeft, innerTop),
                size = Size(innerW, innerH)
            )

            // Deep blue hairline border around outer frame
            drawRect(
                color = blueDeep,
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size((w - 2.dp.toPx()).coerceAtLeast(1f), (h - 2.dp.toPx()).coerceAtLeast(1f)),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Braided Gold Islamic diamond frieze along top and bottom bands
            val bandMidTop = outerBand * 0.5f
            val bandMidBottom = h - outerBand * 0.5f
            val step = 18.dp.toPx()
            val diamondHalfW = 7.dp.toPx()
            val diamondHalfH = 4.2.dp.toPx()
            val strokeThin = Stroke(width = 1.4.dp.toPx())

            var x = outerBand
            while (x < w - outerBand) {
                val cx = x + step * 0.5f
                if (cx + diamondHalfW <= w - outerBand) {
                    val topPath = Path().apply {
                        moveTo(cx, bandMidTop - diamondHalfH)
                        lineTo(cx + diamondHalfW, bandMidTop)
                        lineTo(cx, bandMidTop + diamondHalfH)
                        lineTo(cx - diamondHalfW, bandMidTop)
                        close()
                    }
                    drawPath(topPath, color = goldBright, style = strokeThin)

                    val botPath = Path().apply {
                        moveTo(cx, bandMidBottom - diamondHalfH)
                        lineTo(cx + diamondHalfW, bandMidBottom)
                        lineTo(cx, bandMidBottom + diamondHalfH)
                        lineTo(cx - diamondHalfW, bandMidBottom)
                        close()
                    }
                    drawPath(botPath, color = goldBright, style = strokeThin)
                }
                x += step
            }

            // Braided Gold Islamic diamond frieze along left and right bands
            val bandMidLeft = outerBand * 0.5f
            val bandMidRight = w - outerBand * 0.5f
            var y = outerBand
            while (y < h - outerBand) {
                val cy = y + step * 0.5f
                if (cy + diamondHalfW <= h - outerBand) {
                    val leftPath = Path().apply {
                        moveTo(bandMidLeft, cy - diamondHalfW)
                        lineTo(bandMidLeft + diamondHalfH, cy)
                        lineTo(bandMidLeft, cy + diamondHalfW)
                        lineTo(bandMidLeft - diamondHalfH, cy)
                        close()
                    }
                    drawPath(leftPath, color = goldBright, style = strokeThin)

                    val rightPath = Path().apply {
                        moveTo(bandMidRight, cy - diamondHalfW)
                        lineTo(bandMidRight + diamondHalfH, cy)
                        lineTo(bandMidRight, cy + diamondHalfW)
                        lineTo(bandMidRight - diamondHalfH, cy)
                        close()
                    }
                    drawPath(rightPath, color = goldBright, style = strokeThin)
                }
                y += step
            }

            // Corner 8-pointed Rub el Hizb stars (overlapping rotated squares)
            val cornerCenters = listOf(
                Offset(outerBand * 0.5f, outerBand * 0.5f),
                Offset(w - outerBand * 0.5f, outerBand * 0.5f),
                Offset(outerBand * 0.5f, h - outerBand * 0.5f),
                Offset(w - outerBand * 0.5f, h - outerBand * 0.5f)
            )
            val starSide = 8.4.dp.toPx()
            cornerCenters.forEach { c ->
                drawCircle(color = blueDeep, radius = starSide * 0.78f, center = c)
                drawRect(
                    color = goldBright,
                    topLeft = Offset(c.x - starSide / 2f, c.y - starSide / 2f),
                    size = Size(starSide, starSide),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                rotate(degrees = 45f, pivot = c) {
                    drawRect(
                        color = goldBright,
                        topLeft = Offset(c.x - starSide / 2f, c.y - starSide / 2f),
                        size = Size(starSide, starSide),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
                drawCircle(color = gold, radius = 1.8.dp.toPx(), center = c)
            }

            // Delicate inner gold corner brackets inside white page
            val insetTick = innerLeft + 5.dp.toPx()
            val tickLen = 10.dp.toPx()
            val tickStroke = 1.2.dp.toPx()
            drawLine(gold, Offset(insetTick, insetTick), Offset(insetTick + tickLen, insetTick), tickStroke)
            drawLine(gold, Offset(insetTick, insetTick), Offset(insetTick, insetTick + tickLen), tickStroke)
            drawLine(gold, Offset(w - insetTick, insetTick), Offset(w - insetTick - tickLen, insetTick), tickStroke)
            drawLine(gold, Offset(w - insetTick, insetTick), Offset(w - insetTick, insetTick + tickLen), tickStroke)
            drawLine(gold, Offset(insetTick, h - insetTick), Offset(insetTick + tickLen, h - insetTick), tickStroke)
            drawLine(gold, Offset(insetTick, h - insetTick), Offset(insetTick, h - insetTick - tickLen), tickStroke)
            drawLine(gold, Offset(w - insetTick, h - insetTick), Offset(w - insetTick - tickLen, h - insetTick), tickStroke)
            drawLine(gold, Offset(w - insetTick, h - insetTick), Offset(w - insetTick, h - insetTick - tickLen), tickStroke)
        }

        // Inner page content bounded neatly inside the golden trim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 21.dp, vertical = 20.dp),
            content = content
        )
    }
}

/**
 * Ornate Royal Blue & Gold Surah Header Cartouche matching Screenshots 1 & 2.
 */
@Composable
fun MushafSurahHeaderBanner(
    surahNameAr: String,
    surahNumber: Int,
    revelationType: String,
    ayahCount: Int,
    modifier: Modifier = Modifier
) {
    val blue = QuranPalette.RoyalBlue
    val blueDark = QuranPalette.RoyalBlueDark
    val gold = QuranPalette.GoldBorder
    val voweledTitle = MushafPagePagination.getCalligraphicSurahBannerTitle(surahNumber, surahNameAr)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(blueDark)
            .border(1.5.dp, gold, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRoundRect(
                color = blue,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size((w - 6.dp.toPx()).coerceAtLeast(1f), (h - 6.dp.toPx()).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
            )

            val sideInset = 18.dp.toPx()
            val cy = h / 2f
            val loopRadius = 11.dp.toPx()
            val stroke = Stroke(width = 1.4.dp.toPx())

            listOf(sideInset, w - sideInset).forEach { sx ->
                drawCircle(color = gold.copy(alpha = 0.25f), radius = loopRadius, center = Offset(sx, cy))
                drawCircle(color = gold, radius = loopRadius, center = Offset(sx, cy), style = stroke)
                drawCircle(color = gold, radius = loopRadius * 0.45f, center = Offset(sx, cy), style = stroke)
            }

            val plaqueW = (w * 0.64f).coerceAtLeast(130.dp.toPx())
            val plaqueLeft = (w - plaqueW) / 2f
            val plaqueTop = 5.dp.toPx()
            val plaqueH = h - 10.dp.toPx()
            drawRoundRect(
                color = blueDark,
                topLeft = Offset(plaqueLeft, plaqueTop),
                size = Size(plaqueW, plaqueH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
            drawRoundRect(
                color = gold,
                topLeft = Offset(plaqueLeft, plaqueTop),
                size = Size(plaqueW, plaqueH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        Text(
            text = voweledTitle,
            fontFamily = HeaderBrandAmiriFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/**
 * Dual-tone Warm Gold (#D97706) & Royal Blue (#1A6FE3) ornamental circular Ayah end medallion
 * (matching PDF Section 2: "فواصل آيات دائرية مزخرفة بالذهبي والأزرق").
 */
@Composable
fun AyahEndMedallion(
    ayahNumber: Int,
    modifier: Modifier = Modifier
) {
    val arabicNum = formatArabicDigits(ayahNumber)
    val gold = QuranPalette.GoldAccent
    val blue = QuranPalette.RoyalBlue
    val goldFill = QuranPalette.GoldSoft
    val fontSize = when {
        ayahNumber >= 100 -> 8.5.sp
        ayahNumber >= 10 -> 10.sp
        else -> 11.sp
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = (size.minDimension / 2f) * 0.90f

            val outerPath = Path()
            val points = 16
            for (i in 0 until points) {
                val angle = (2.0 * PI * i) / points
                val rad = if (i % 2 == 0) r else r * 0.80f
                val px = cx + (rad * cos(angle)).toFloat()
                val py = cy + (rad * sin(angle)).toFloat()
                if (i == 0) outerPath.moveTo(px, py) else outerPath.lineTo(px, py)
            }
            outerPath.close()

            drawPath(outerPath, color = goldFill)
            drawPath(outerPath, color = gold, style = Stroke(width = 1.3.dp.toPx()))
            drawCircle(
                color = blue.copy(alpha = 0.72f),
                radius = r * 0.62f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        Text(
            text = arabicNum,
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = QuranPalette.NavyInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}
