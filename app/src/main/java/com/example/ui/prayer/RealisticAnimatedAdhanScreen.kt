package com.example.ui.prayer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AdhanDarkGlassSurface = Color(0x770F172A)
private val AdhanDarkGlassBorder = Color(0x44FFFFFF)
private val AdhanWarmGold = Color(0xFFFBBF24)

enum class PrayerTimeVisualTheme(
    val accentHex: Long,
    val secondaryHex: Long,
    @DrawableRes val mosqueDrawableRes: Int
) {
    FAJR(
        accentHex = 0xFF0F172A,
        secondaryHex = 0xFF1E3A8A,
        mosqueDrawableRes = R.drawable.bg_mosque_fajr
    ),
    SUNRISE(
        accentHex = 0xFFF59E0B,
        secondaryHex = 0xFF38BDF8,
        mosqueDrawableRes = R.drawable.bg_mosque_sunrise
    ),
    DHUHR_ASR(
        accentHex = 0xFFD97706,
        secondaryHex = 0xFFFEF3C7,
        mosqueDrawableRes = R.drawable.bg_mosque_asr
    ),
    MAGHRIB_ISHA(
        accentHex = 0xFFEA580C,
        secondaryHex = 0xFF7C2D12,
        mosqueDrawableRes = R.drawable.bg_mosque_maghrib
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
    val isSunrise = normalizedPrayer.contains("شروق") ||
        normalizedPrayer.contains("sunrise") ||
        normalizedPrayer.contains("ضحى")

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

    val cleanPrayerName = remember(prayerName) {
        prayerName.replace(Regex("[\\u064B-\\u065F\\u0670]"), "").trim()
    }

    val (headerTitleLine1, headerTitleLine2) = remember(cleanPrayerName, isSunrise, normalizedPrayer) {
        when {
            isSunrise -> "حان الآن وقت" to "الشروق والضحى"
            normalizedPrayer.contains("فجر") || normalizedPrayer.contains("fajr") -> "حان الآن موعد" to "أذان الفجر"
            normalizedPrayer.contains("ظهر") || normalizedPrayer.contains("dhuhr") -> "حان الآن موعد" to "أذان الظهر"
            normalizedPrayer.contains("عصر") || normalizedPrayer.contains("asr") -> "حان الآن موعد" to "أذان العصر"
            normalizedPrayer.contains("مغرب") || normalizedPrayer.contains("maghrib") -> "حان الآن موعد" to "أذان المغرب"
            normalizedPrayer.contains("عشاء") || normalizedPrayer.contains("isha") -> "حان الآن موعد" to "أذان العشاء"
            else -> "حان الآن موعد" to "أذان $cleanPrayerName"
        }
    }

    val openNearbyMosquesDirectly: () -> Unit = {
        onOpenNearbyMosques()
        try {
            val mapUri = Uri.parse("geo:0,0?q=مسجد")
            val mapsIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapsIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=مسجد")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر فتح الخرائط على هذا الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag("realistic_adhan_root")
        ) {
            val isLandscape = maxWidth > maxHeight

            // Layer 1: Live Video (ExoPlayer) OR Pure Architectural Photo + Ken Burns + Atmospheric Canvas
            RealisticAnimatedMosqueBackground(
                prayerName = prayerName,
                modifier = Modifier.fillMaxSize()
            )

            // Layer 2: 100% Unified Programmatic Compose Dark Glass Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = if (isLandscape) 24.dp else 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // UNIFIED TOP DARK-GLASS HEADER ACROSS ALL 5 PRAYERS
                UnifiedAdhanGlassHeaderCard(
                    clockDisplay = "$currentTimeText $amPmText",
                    cityName = cityName,
                    hijriDate = hijriDateText,
                    titleLine1 = headerTitleLine1,
                    titleLine2 = headerTitleLine2,
                    muezzinVoice = if (isSunrise) null else muezzinVoice,
                    isLandscape = isLandscape
                )

                Spacer(modifier = Modifier.weight(1f))

                // BOTTOM GLASS CONTROLS & SWIPE-LEFT BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSunrise) {
                        Surface(
                            onClick = onOpenMorningAdhkar,
                            shape = RoundedCornerShape(50),
                            color = AdhanDarkGlassSurface,
                            border = BorderStroke(1.2.dp, AdhanWarmGold.copy(alpha = 0.72f)),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("sunrise_morning_adhkar_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = AdhanWarmGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "أذكار الصباح",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    QuickPrayerActionRow(
                        onNearbyMosques = openNearbyMosquesDirectly,
                        onOpenQibla = onOpenQibla
                    )

                    if (!isSunrise) {
                        PostAdhanDuaGlassCard()
                    }

                    Button(
                        onClick = onStopAdhan,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdhanWarmGold,
                            contentColor = Color(0xFF0F172A)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("adhan_stop_primary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "إيقاف الأذان",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSunrise) "تم / إيقاف التنبيه" else "تم / إيقاف الأذان",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    SwipeToStopAdhanBar(
                        label = if (isSunrise) "اسحب لليسار لإيقاف التنبيه" else "اسحب لليسار لإيقاف الأذان",
                        onDismiss = onStopAdhan
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedAdhanGlassHeaderCard(
    clockDisplay: String,
    cityName: String,
    hijriDate: String,
    titleLine1: String,
    titleLine2: String,
    muezzinVoice: String?,
    isLandscape: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AdhanDarkGlassSurface,
        border = BorderStroke(1.dp, AdhanDarkGlassBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = if (isLandscape) 4.dp else 12.dp)
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .testTag("adhan_unified_glass_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = if (isLandscape) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Prayer Time (38.sp Bold White) alongside City & Hijri Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clockDisplay,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = if (isLandscape) 30.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.testTag("adhan_digital_clock")
                )

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = cityName,
                            tint = AdhanWarmGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = cityName,
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = hijriDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            // Row 2: Clean 2-Line Golden Prayer Adhan Title (Zero overlapping tashkeel)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adhan_prayer_title")
            ) {
                Text(
                    text = titleLine1,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = if (isLandscape) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF6C453),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = titleLine2,
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = if (isLandscape) 24.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD460),
                    lineHeight = if (isLandscape) 30.sp else 36.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (!muezzinVoice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 14.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x550F172A),
                    border = BorderStroke(1.dp, AdhanDarkGlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AdhanWarmGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = muezzinVoice,
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.94f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AdhanAudioWaveVisualizer()
            }
        }
    }
}

@Composable
private fun AdhanAudioWaveVisualizer() {
    val transition = rememberInfiniteTransition(label = "adhanSoundWave")
    val fractions = (0 until 11).map { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 480 + (index % 4) * 140,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "waveBar$index"
        )
    }

    Row(
        modifier = Modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        fractions.forEach { state ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((20f * state.value).dp)
                    .background(AdhanWarmGold.copy(alpha = 0.92f), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun PostAdhanDuaGlassCard() {
    val adhanDuaaText = "«اللهم ربَّ هذه الدعوة التامة، والصلاة القائمة،\nآتِ محمدًا الوسيلة والفضيلة،\nوابعثه مقامًا محمودًا الذي وعدته»"
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AdhanDarkGlassSurface,
        border = BorderStroke(1.dp, AdhanWarmGold.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "دعاء ما بعد الأذان",
                fontFamily = DisplayArabicFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AdhanWarmGold
            )
            Text(
                text = adhanDuaaText,
                fontFamily = DisplayArabicFontFamily,
                fontSize = 15.5.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF8FAFC),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickPrayerActionRow(
    onNearbyMosques: () -> Unit,
    onOpenQibla: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onOpenQibla,
            shape = RoundedCornerShape(18.dp),
            color = AdhanDarkGlassSurface,
            border = BorderStroke(1.dp, AdhanDarkGlassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .testTag("adhan_qibla_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "القبلة",
                    tint = AdhanWarmGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "القبلة",
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        Surface(
            onClick = onNearbyMosques,
            shape = RoundedCornerShape(18.dp),
            color = AdhanDarkGlassSurface,
            border = BorderStroke(1.dp, AdhanDarkGlassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .testTag("adhan_nearby_mosques_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mosque,
                    contentDescription = "المساجد القريبة",
                    tint = Color(0xFF93C5FD),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "المساجد القريبة",
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}
