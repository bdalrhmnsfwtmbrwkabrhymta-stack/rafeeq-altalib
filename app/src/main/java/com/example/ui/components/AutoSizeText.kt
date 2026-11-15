package com.example.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Flicker-free smart auto-sizing text component using [rememberTextMeasurer].
 * Steps font size smoothly from [maxFontSize] down to [minFontSize] so single-line
 * headings and long Arabic student names never truncate with ellipsis (...).
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 17.sp,
    minFontSize: TextUnit = 13.sp,
    stepGranularity: TextUnit = 0.5.sp,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeightRatio: Float = 1.32f,
    overflowToTwoLinesAtMinSize: Boolean = true,
    style: TextStyle = LocalTextStyle.current
) {
    AutoSizeText(
        text = AnnotatedString(text),
        modifier = modifier,
        color = color,
        maxFontSize = maxFontSize,
        minFontSize = minFontSize,
        stepGranularity = stepGranularity,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        lineHeightRatio = lineHeightRatio,
        overflowToTwoLinesAtMinSize = overflowToTwoLinesAtMinSize,
        style = style
    )
}

@Composable
fun AutoSizeText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 17.sp,
    minFontSize: TextUnit = 13.sp,
    stepGranularity: TextUnit = 0.5.sp,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeightRatio: Float = 1.32f,
    overflowToTwoLinesAtMinSize: Boolean = true,
    style: TextStyle = LocalTextStyle.current
) {
    val textMeasurer = rememberTextMeasurer()
    val boxAlignment = when (textAlign) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = boxAlignment
    ) {
        val maxWidthPx = constraints.maxWidth
        val mergedStyle = style.merge(
            TextStyle(
                color = if (color != Color.Unspecified) color else style.color,
                fontWeight = fontWeight ?: style.fontWeight,
                fontFamily = fontFamily ?: style.fontFamily,
                textAlign = textAlign ?: style.textAlign,
                letterSpacing = if (letterSpacing != TextUnit.Unspecified) letterSpacing else style.letterSpacing
            )
        )

        val (resolvedFontSize, overflowsAtMin) = remember(
            text,
            maxWidthPx,
            maxFontSize,
            minFontSize,
            stepGranularity,
            mergedStyle
        ) {
            if (!constraints.hasBoundedWidth || maxWidthPx <= 0 || maxWidthPx == Int.MAX_VALUE) {
                maxFontSize to false
            } else {
                val step = stepGranularity.value.coerceAtLeast(0.25f)
                val floorSp = minFontSize.value.coerceAtMost(maxFontSize.value)
                var currentSp = maxFontSize.value
                var stillOverflows = false

                while (currentSp >= floorSp) {
                    val result = textMeasurer.measure(
                        text = text,
                        style = mergedStyle.copy(
                            fontSize = currentSp.sp,
                            lineHeight = (currentSp * lineHeightRatio).sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        constraints = Constraints(maxWidth = maxWidthPx)
                    )
                    if (!result.didOverflowWidth) {
                        stillOverflows = false
                        break
                    }
                    stillOverflows = true
                    if (currentSp - step < floorSp) {
                        currentSp = floorSp
                        val floorResult = textMeasurer.measure(
                            text = text,
                            style = mergedStyle.copy(
                                fontSize = floorSp.sp,
                                lineHeight = (floorSp * lineHeightRatio).sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            constraints = Constraints(maxWidth = maxWidthPx)
                        )
                        stillOverflows = floorResult.didOverflowWidth
                        break
                    }
                    currentSp -= step
                }

                currentSp.sp to stillOverflows
            }
        }

        val allowTwoLines = overflowsAtMin && overflowToTwoLinesAtMinSize
        Text(
            text = text,
            color = color,
            fontSize = resolvedFontSize,
            lineHeight = (resolvedFontSize.value * lineHeightRatio).sp,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
            letterSpacing = letterSpacing,
            maxLines = if (allowTwoLines) 2 else 1,
            softWrap = allowTwoLines,
            overflow = TextOverflow.Clip,
            style = style
        )
    }
}
