package com.example.ui.screens.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.QuranTab
import com.example.ui.screens.formatArabicDigits
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.example.ui.theme.ReadexProFontFamily

object QuranPalette {
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE2E8F0)
    val RoyalBlue = Color(0xFF1A6FE3)
    val RoyalBlueDark = Color(0xFF1356B5)
    val RoyalBlueSoft = Color(0xFFEFF6FF)
    val RoyalBlueTint = Color(0xFFBFDBFE)
    val NavyInk = Color(0xFF0F172A)
    val SlateBody = Color(0xFF475569)
    val SlateMuted = Color(0xFF64748B)
    val GoldAccent = Color(0xFFD97706)
    val GoldSoft = Color(0xFFFEF3C7)
    val GoldBorder = Color(0xFFF59E0B)
    val HeroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A6FE3), Color(0xFF155BC0))
    )
}

@Composable
fun QuranCalligraphicScreenHeader(
    firstWord: String,
    secondWord: String,
    firstWordBlue: Boolean = true,
    subtitle: String,
    isEnglish: Boolean,
    onNavigateBack: (() -> Unit)?,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(QuranPalette.Background)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top row matching Mockups 1, 4 & 5: Back arrow on start (right in RTL), Downloads + Settings on end (left in RTL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quran_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "رجوع",
                        tint = QuranPalette.NavyInk,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenDownloads != null) {
                    IconButton(
                        onClick = onOpenDownloads,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("quran_header_downloads_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = if (isEnglish) "Offline Downloads" else "تنزيل التلاوات بدون إنترنت",
                            tint = QuranPalette.SlateMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quran_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = if (isEnglish) "Quran Settings" else "إعدادات المصحف والقراءة",
                        tint = QuranPalette.RoyalBlue,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }

        // Calligraphic 28.sp Amiri Bold Title (color-only spans to preserve Arabic ligatures)
        val firstColor = if (firstWordBlue) QuranPalette.RoyalBlue else QuranPalette.NavyInk
        val secondColor = if (firstWordBlue) QuranPalette.NavyInk else QuranPalette.RoyalBlue
        val styledTitle = buildAnnotatedString {
            withStyle(SpanStyle(color = firstColor)) {
                append(firstWord)
            }
            append(" ")
            withStyle(SpanStyle(color = secondColor)) {
                append(secondWord)
            }
        }

        Text(
            text = styledTitle,
            fontFamily = HeaderBrandAmiriFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("quran_header_title")
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = QuranPalette.SlateMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuranFourTabBar(
    activeTab: QuranTab,
    isEnglish: Boolean,
    onSelectTab: (QuranTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // FlowRow displays all 4 tabs visibly at once on phones (clean 2x2 grid like Mockup 1) and single row on wide tablets
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("quran_tabs_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuranTab.entries.forEach { tab ->
            val isSelected = tab == activeTab
            val label = if (isEnglish) tab.titleEn else tab.titleAr
            Surface(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelectTab(tab) }
                    .testTag("quran_tab_${tab.name.lowercase()}"),
                shape = RoundedCornerShape(22.dp),
                color = if (isSelected) QuranPalette.RoyalBlue else QuranPalette.Surface,
                border = if (isSelected) null else BorderStroke(1.dp, QuranPalette.Border),
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) Color.White else QuranPalette.SlateBody,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
fun QuranProgressRing(
    progress: Float,
    percentText: String,
    ringSize: Dp = 84.dp,
    strokeWidth: Dp = 7.dp,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    progressColor: Color = Color.White,
    textColor: Color = Color.White,
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (clamped > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textColor
            )
            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun QuranMiniAudioPlayer(
    title: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    progressMs: Int,
    durationMs: Int,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("quran_mini_player"),
        shape = RoundedCornerShape(16.dp),
        color = QuranPalette.Surface,
        border = BorderStroke(1.dp, QuranPalette.RoyalBlueTint),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = QuranPalette.RoyalBlueSoft,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = QuranPalette.RoyalBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = QuranPalette.RoyalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    AutoSizeText(
                        text = title,
                        maxFontSize = 14.sp,
                        minFontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ReadexProFontFamily,
                        color = QuranPalette.NavyInk,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (isLoading) "جاري تحميل التلاوة..." else if (isPlaying) "تعمل الآن" else "متوقف مؤقتاً",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.5.sp,
                        color = QuranPalette.RoyalBlue
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quran_mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = QuranPalette.RoyalBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quran_mini_stop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق المشغل",
                        tint = QuranPalette.SlateMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (durationMs > 0) {
                val fraction = (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = QuranPalette.RoyalBlue,
                    trackColor = QuranPalette.RoyalBlueSoft
                )
            }
        }
    }
}

fun formatNumberLocale(number: Int, isEnglish: Boolean): String =
    if (isEnglish) number.toString() else formatArabicDigits(number)
