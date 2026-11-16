package com.example.ui.prayer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PrayerTimeVisualTheme(
    val accentHex: Long,
    val secondaryHex: Long,
    @DrawableRes val mosqueDrawableRes: Int
) {
    FAJR(
        accentHex = 0xFF0F172A,
        secondaryHex = 0xFF1E3A8A,
        mosqueDrawableRes = R.drawable.img_mosque_fajr
    ),
    SUNRISE(
        accentHex = 0xFFF59E0B,
        secondaryHex = 0xFF38BDF8,
        mosqueDrawableRes = R.drawable.img_mosque_sunrise
    ),
    DHUHR_ASR(
        accentHex = 0xFFD97706,
        secondaryHex = 0xFFFEF3C7,
        mosqueDrawableRes = R.drawable.img_mosque_asr
    ),
    MAGHRIB_ISHA(
        accentHex = 0xFFEA580C,
        secondaryHex = 0xFF7C2D12,
        mosqueDrawableRes = R.drawable.img_mosque_maghrib
    )
}

fun resolvePrayerVisualTheme(prayerName: String): PrayerTimeVisualTheme {
    val normalized = prayerName.trim().lowercase()
    return when {
        normalized.contains("فجر") || normalized.contains("fajr") -> PrayerTimeVisualTheme.FAJR
        normalized.contains("شروق") || normalized.contains("ضحى") || normalized.contains("sunrise") -> PrayerTimeVisualTheme.SUNRISE
        normalized.contains("ظهر") || normalized.contains("dhuhr") || normalized.contains("عصر") || normalized.contains("asr") -> PrayerTimeVisualTheme.DHUHR_ASR
        normalized.contains("مغرب") || normalized.contains("maghrib") || normalized.contains("عشاء") || normalized.contains("isha") -> PrayerTimeVisualTheme.MAGHRIB_ISHA
        else -> PrayerTimeVisualTheme.FAJR
    }
}

/**
 * Ken Burns Pan/Zoom + Subtle Animated Light Shimmer over a photorealistic mosque photograph.
 * Zero cartoon shapes or flat vectors.
 */
@Composable
fun RealisticMosqueMotionView(
    visualTheme: PrayerTimeVisualTheme,
    isIshaNight: Boolean,
    isDhuhrBright: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mosque_ken_burns")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_scale"
    )

    val panX by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_pan_x"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "light_shimmer"
    )

    val bgRes = when {
        isIshaNight -> R.drawable.img_mosque_fajr
        isDhuhrBright -> R.drawable.img_mosque_sunrise
        else -> visualTheme.mosqueDrawableRes
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bgRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panX
                }
        )

        // Subtle dynamic light, sunbeam, starlight & reflection shimmer overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pulse = (sin(shimmerProgress * 2f * PI.toFloat()) + 1f) * 0.5f

            when {
                isIshaNight -> {
                    // Calm night tint + twinkling stars over dome
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC060D21),
                                Color(0x770F172A),
                                Color(0x66060D21)
                            )
                        )
                    )
                    val starPositions = listOf(
                        0.15f to 0.08f, 0.34f to 0.05f, 0.52f to 0.11f, 0.72f to 0.07f,
                        0.86f to 0.14f, 0.22f to 0.17f, 0.64f to 0.18f, 0.88f to 0.23f
                    )
                    starPositions.forEachIndexed { idx, (sx, sy) ->
                        val starAlpha = (0.35f + 0.55f * ((pulse + idx * 0.17f) % 1f))
                        drawCircle(
                            color = Color(0xFFFFF8DC).copy(alpha = starAlpha),
                            radius = if (idx % 2 == 0) 3.5f else 2.4f,
                            center = Offset(w * sx, h * sy)
                        )
                    }
                }
                visualTheme == PrayerTimeVisualTheme.FAJR -> {
                    // Dawn navy atmosphere at top + subtle warm lantern shimmer near arches
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x990F172A),
                                Color(0x221E3A8A),
                                Color(0x660F172A)
                            )
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFBBF24).copy(alpha = 0.12f + pulse * 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.64f),
                            radius = w * 0.45f
                        ),
                        center = Offset(w * 0.5f, h * 0.64f),
                        radius = w * 0.45f
                    )
                }
                visualTheme == PrayerTimeVisualTheme.SUNRISE || isDhuhrBright -> {
                    // Luminous morning/daylight sunbeam shimmer
                    val beamOrigin = Offset(w * (0.15f + shimmerProgress * 0.12f), h * 0.36f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF3C4).copy(alpha = 0.22f + pulse * 0.10f),
                                Color.Transparent
                            ),
                            center = beamOrigin,
                            radius = w * 0.7f
                        ),
                        center = beamOrigin,
                        radius = w * 0.7f
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x660F294A),
                                Color.Transparent,
                                Color(0x550B1626)
                            )
                        )
                    )
                }
                visualTheme == PrayerTimeVisualTheme.DHUHR_ASR -> {
                    // Warm honey-amber afternoon light shimmer
                    val sunX = w * (0.05f + shimmerProgress * 0.10f)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x33D97706).copy(alpha = 0.18f + pulse * 0.08f),
                                Color.Transparent,
                                Color(0x552A1B0E)
                            ),
                            start = Offset(sunX, h * 0.25f),
                            end = Offset(w, h)
                        )
                    )
                }
                visualTheme == PrayerTimeVisualTheme.MAGHRIB_ISHA -> {
                    // Sunset glow reflected softly across the courtyard pool
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEA580C).copy(alpha = 0.16f + pulse * 0.09f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.68f),
                            radius = w * 0.55f
                        ),
                        center = Offset(w * 0.5f, h * 0.68f),
                        radius = w * 0.55f
                    )
                }
            }
        }
    }
}

@Composable
fun RealisticAnimatedAdhanScreen(
    prayerName: String,
    cityName: String,
    muezzinVoice: String,
    formattedClockTime: String? = null,
    formattedAmPm: String? = null,
    onOpenQibla: () -> Unit,
    onOpenNearbyMosques: () -> Unit,
    onOpenMorningAdhkar: () -> Unit,
    onStopAdhan: () -> Unit
) {
    val context = LocalContext.current
    val normalizedPrayer = prayerName.trim().lowercase()
    val visualTheme = remember(prayerName) { resolvePrayerVisualTheme(prayerName) }
    val isSunrise = normalizedPrayer.contains("شروق") || normalizedPrayer.contains("sunrise") || normalizedPrayer.contains("ضحى")
    val isAsrOrDhuhr = visualTheme == PrayerTimeVisualTheme.DHUHR_ASR
    val isDhuhrBright = normalizedPrayer.contains("ظهر") || normalizedPrayer.contains("dhuhr")
    val isIshaNight = normalizedPrayer.contains("عشاء") || normalizedPrayer.contains("isha")
    val isMaghrib = normalizedPrayer.contains("مغرب") || normalizedPrayer.contains("maghrib")

    val currentTimeText = remember(formattedClockTime) {
        formattedClockTime ?: SimpleDateFormat("hh:mm", Locale.US).format(Date())
    }
    val amPmText = remember(formattedAmPm) {
        formattedAmPm ?: run {
            val h = SimpleDateFormat("a", Locale.US).format(Date())
            if (h.equals("AM", ignoreCase = true)) "ص" else "م"
        }
    }
    val hijriDateText = remember {
        try {
            val cal = android.icu.util.IslamicCalendar()
            val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            val month = getHijriMonthName(cal.get(android.icu.util.Calendar.MONTH))
            val year = cal.get(android.icu.util.Calendar.YEAR)
            "$day $month $year هـ"
        } catch (_: Exception) {
            "اليوم المبارك"
        }
    }

    val headerPrayerLabel = remember(prayerName, isSunrise) {
        when {
            isSunrise -> "وقت الشروق والضحى"
            prayerName.startsWith("أذان") || prayerName.startsWith("صلاة") -> prayerName
            else -> "أذان $prayerName"
        }
    }

    val openMapsFallback = {
        try {
            val uri = Uri.parse("geo:0,0?q=المساجد")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/المساجد")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر فتح الخرائط على هذا الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
        onOpenNearbyMosques()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag("realistic_adhan_root")
        ) {
            val isLandscape = maxWidth > maxHeight

            // Layer 1: Live Photorealistic Motion Background
            RealisticMosqueMotionView(
                visualTheme = visualTheme,
                isIshaNight = isIshaNight,
                isDhuhrBright = isDhuhrBright,
                modifier = Modifier.fillMaxSize()
            )

            // Layer 2: Foreground Prayer Chrome & Controls with safe insets
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = if (isLandscape) 24.dp else 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP AREA tailored to prayer time screenshot layout
                when {
                    isAsrOrDhuhr -> {
                        // Screenshot 3: Single crisp frosted glass header pill
                        FrostedTimeHeaderPill(
                            cityName = cityName,
                            prayerTitle = headerPrayerLabel,
                            clockDisplay = "$currentTimeText $amPmText",
                            hijriDate = hijriDateText
                        )
                    }
                    isMaghrib -> {
                        // Screenshot 4: Bright clean rounded header card + quick frosted buttons right below it
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MaghribTopSummaryCard(
                                clockDisplay = "$currentTimeText $amPmText",
                                prayerTitle = headerPrayerLabel,
                                cityName = cityName,
                                hijriDate = hijriDateText
                            )
                            QuickPrayerActionRow(
                                onNearbyMosques = openMapsFallback,
                                onOpenQibla = onOpenQibla,
                                isDarkText = false
                            )
                        }
                    }
                    else -> {
                        // Screenshots 1 & 2 (Fajr, Isha, Sunrise): Hero digital clock + glowing prayer title + city
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (isLandscape) 4.dp else 18.dp)
                        ) {
                            Text(
                                text = "$currentTimeText $amPmText",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = if (isLandscape) 42.sp else 56.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.testTag("adhan_digital_clock")
                            )
                            Text(
                                text = headerPrayerLabel,
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = if (isLandscape) 26.sp else 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    visualTheme == PrayerTimeVisualTheme.FAJR && !isIshaNight -> Color(0xFFF3CF88)
                                    isSunrise -> Color.White
                                    else -> Color(0xFFFDE68A)
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("adhan_prayer_title")
                            )
                            Text(
                                text = "$cityName • $hijriDateText",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = if (isLandscape) 14.sp else 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                            if (!isSunrise) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = Color(0xFFFDE68A),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = muezzinVoice,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // BOTTOM CONTROLS tailored to each prayer screenshot
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSunrise) {
                        // Screenshot 2: Prominent "أذكار الصباح" button at bottom of sunrise screen
                        Surface(
                            onClick = onOpenMorningAdhkar,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFDF8F2),
                            border = BorderStroke(2.5.dp, Color(0xFFD4A373)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("sunrise_morning_adhkar_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = Color(0xFF1E3A8A),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "أذكار الصباح",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                        SwipeToDismissAdhanPill(
                            label = "إيقاف التنبيه",
                            isRedHighlight = false,
                            isWhitePill = false,
                            onDismiss = onStopAdhan
                        )
                    } else {
                        if (!isMaghrib) {
                            QuickPrayerActionRow(
                                onNearbyMosques = openMapsFallback,
                                onOpenQibla = onOpenQibla,
                                isDarkText = false
                            )
                        }
                        SwipeToDismissAdhanPill(
                            label = "إيقاف الأذان",
                            isRedHighlight = (visualTheme == PrayerTimeVisualTheme.FAJR && !isIshaNight),
                            isWhitePill = isMaghrib,
                            onDismiss = onStopAdhan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedTimeHeaderPill(
    cityName: String,
    prayerTitle: String,
    clockDisplay: String,
    hijriDate: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0x706B7280),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cityName,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = prayerTitle,
                    fontFamily = HeaderBrandAmiriFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = clockDisplay,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = hijriDate,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun MaghribTopSummaryCard(
    clockDisplay: String,
    prayerTitle: String,
    cityName: String,
    hijriDate: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF5FFFFFF),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "التاريخ الهجري",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = hijriDate.substringBefore("هـ").trim(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockDisplay,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = prayerTitle,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = cityName,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = cityName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
        }
    }
}

@Composable
private fun QuickPrayerActionRow(
    onNearbyMosques: () -> Unit,
    onOpenQibla: () -> Unit,
    isDarkText: Boolean
) {
    val contentColor = if (isDarkText) Color(0xFF0F172A) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onNearbyMosques,
            shape = RoundedCornerShape(16.dp),
            color = Color(0x8852525B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("adhan_nearby_mosques_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Mosque, contentDescription = "المساجد القريبة", tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "المساجد القريبة",
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        Surface(
            onClick = onOpenQibla,
            shape = RoundedCornerShape(16.dp),
            color = Color(0x8852525B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("adhan_qibla_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Explore, contentDescription = "القبلة", tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "القبلة",
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun SwipeToDismissAdhanPill(
    label: String,
    isRedHighlight: Boolean,
    isWhitePill: Boolean,
    onDismiss: () -> Unit
) {
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { 48.dp.toPx() }
    val maxTravelPx = (containerWidthPx - thumbSizePx - with(density) { 16.dp.toPx() }).coerceAtLeast(120f)

    val containerBg = when {
        isWhitePill -> Color.White
        isRedHighlight -> Color(0xEEFFFFFF)
        else -> Color(0x9957534E)
    }
    val textColor = if (isWhitePill || isRedHighlight) Color(0xFF0F172A) else Color.White
    val thumbBg = when {
        isWhitePill -> Color(0xFF2563EB)
        isRedHighlight -> Color(0xFFFDE68A)
        else -> Color.White
    }
    val thumbIconColor = if (isWhitePill) Color.White else Color(0xFF92400E)

    Surface(
        shape = RoundedCornerShape(50),
        color = containerBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 420.dp)
            .height(60.dp)
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .clickable { onDismiss() }
            .testTag("adhan_swipe_dismiss_pill")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = DisplayArabicFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )

            // Interactive horizontal drag thumb
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(thumbBg)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .pointerInput(maxTravelPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (kotlin.math.abs(dragOffsetPx) >= maxTravelPx * 0.45f) {
                                    onDismiss()
                                } else {
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = { dragOffsetPx = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            val next = dragOffsetPx + dragAmount
                            dragOffsetPx = next.coerceIn(-maxTravelPx, maxTravelPx)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = label,
                    tint = thumbIconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
