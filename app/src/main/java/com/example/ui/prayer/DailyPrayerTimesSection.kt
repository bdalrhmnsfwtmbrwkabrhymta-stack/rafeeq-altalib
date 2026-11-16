package com.example.ui.prayer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import com.example.domain.prayer.DailyPrayerSchedule
import com.example.domain.prayer.PrayerTimeItem
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Modern Material 3 Daily Prayer Times display section powered by Batoul Apps Adhan library.
 * Displays daily prayer times based on the user's location with live countdown, next prayer highlight,
 * audio playback, and GPS refresh.
 */
@Composable
fun DailyPrayerTimesSection(
    viewModel: MainViewModel,
    dailySchedule: DailyPrayerSchedule,
    azanAudioPlayer: AzanAudioPlayer,
    isEnglish: Boolean,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsState()

    // Live countdown timer ticking every second
    var liveRemainingMs by remember { mutableLongStateOf(dailySchedule.timeRemainingMs) }
    LaunchedEffect(dailySchedule) {
        liveRemainingMs = dailySchedule.timeRemainingMs
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val diff = (dailySchedule.nextPrayer.timestampMs - now).coerceAtLeast(0L)
            liveRemainingMs = diff
        }
    }

    val hours = (liveRemainingMs / 3600000).toInt()
    val minutes = ((liveRemainingMs % 3600000) / 60000).toInt()
    val seconds = ((liveRemainingMs % 60000) / 1000).toInt()
    val liveRemainingText = if (isEnglish) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        "$hours س و $minutes د و $seconds ث"
    }

    // Permission launcher for GPS detection
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.detectAndApplyGpsLocation(context) { success, msg ->
                Toast.makeText(
                    context,
                    if (success) {
                        if (isEnglish) "Location updated & prayer times calculated via Adhan: $msg"
                        else "تم تحديث الموقع وحساب مواقيت الصلاة عبر مكتبة Adhan: $msg"
                    } else msg,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                context,
                if (isEnglish) "Please grant location permission to calculate precise prayer times"
                else "يرجى منح إذن الموقع لحساب مواقيت الصلاة الدقيقة",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Location & Adhan Engine Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isEnglish) "Location (Adhan Library Engine)" else "موقعك الحالي (مواقيت مكتبة Adhan)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "adhan 1.2.1",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${dailySchedule.cityName}، ${dailySchedule.countryName}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "(${String.format(Locale.US, "%.3f", dailySchedule.latitude)}°, ${String.format(Locale.US, "%.3f", dailySchedule.longitude)}°) • ${dailySchedule.calculationMethodName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        enabled = !isDetectingLocation,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (isDetectingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDetectingLocation) (if (isEnglish) "Detecting..." else "تحديد...") else (if (isEnglish) "GPS Update" else "تحديث GPS"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Date & Qibla summary bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${dailySchedule.dateFormatted} | ${dailySchedule.hijriDateFormatted}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Qibla: ${dailySchedule.qiblaDirectionAngle.toInt()}°" else "القبلة: ${dailySchedule.qiblaDirectionAngle.toInt()}°",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // 2. Next Prayer Hero Card with Countdown & Elapsed Progress
        val nextPrayer = dailySchedule.nextPrayer
        val nextPrayerDisplayName = if (isEnglish) nextPrayer.nameEnglish else nextPrayer.nameArabic
        val nextPrayerIcon = getPrayerIcon(nextPrayer.id)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        nextPrayerIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "NEXT PRAYER" else "الصلاة القادمة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = nextPrayerDisplayName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Prayer Time
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = nextPrayer.timeFormatted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Countdown Display
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Time Remaining:" else "الوقت المتبقي للأذان:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = liveRemainingText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Progress indicator between prayers
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { dailySchedule.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (dailySchedule.currentPrayer != null) {
                                    val curName = if (isEnglish) dailySchedule.currentPrayer.nameEnglish else dailySchedule.currentPrayer.nameArabic
                                    if (isEnglish) "Current: $curName" else "الصلاة السابقة: $curName"
                                } else "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isEnglish) "Next: $nextPrayerDisplayName" else "القادمة: $nextPrayerDisplayName",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Quick Action Listen Adhan button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val adhanRaw = if (nextPrayer.id == "fajr") R.raw.adhan_makkah_hq else R.raw.adhan_egypt_mbc_hq
                                azanAudioPlayer.playRawSoundWithFadeIn(adhanRaw, useAlarmAttributes = false)
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Playing Adhan for $nextPrayerDisplayName 🔊"
                                    else "جاري تشغيل أذان $nextPrayerDisplayName 🔊",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Listen to Adhan" else "استمع للأذان",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Settings" else "الإعدادات",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Core Daily Prayer Times Grid (الفجر، الشروق، الظهر، العصر، المغرب، العشاء)
        Text(
            text = if (isEnglish) "🕌 Daily Prayer Times (Based on Location)" else "🕌 مواقيت الصلوات الخمس اليومية (حسب موقعك)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        val mainPrayers = dailySchedule.prayersList
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mainPrayers.forEach { prayer ->
                PrayerTimeRowCard(
                    prayer = prayer,
                    isEnglish = isEnglish,
                    azanAudioPlayer = azanAudioPlayer,
                    onCopyTime = {
                        val label = if (isEnglish) "${prayer.nameEnglish}: ${prayer.timeFormatted} (${dailySchedule.cityName})"
                        else "أذان ${prayer.nameArabic}: ${prayer.timeFormatted} (${dailySchedule.cityName})"
                        clipboardManager.setText(AnnotatedString(label))
                        Toast.makeText(context, if (isEnglish) "Copied to clipboard" else "تم نسخ موعد الصلاة", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 4. Sunnah & Night Prayers (قيام الليل والثلث الأخير، منتصف الليل)
        Text(
            text = if (isEnglish) "✨ Sunnah & Night Times (Adhan SunnahTimes)" else "✨ أوقات السنن والليل (حسابات SunnahTimes)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SunnahPrayerCard(
                prayer = dailySchedule.tahajjud,
                subtitle = if (isEnglish) "Best time for Dua & Prayer" else "وقت استجابة الدعاء والصلاة",
                icon = Icons.Default.NightsStay,
                isEnglish = isEnglish,
                modifier = Modifier.weight(1f)
            )
            SunnahPrayerCard(
                prayer = dailySchedule.midnight,
                subtitle = if (isEnglish) "End of Isha window" else "نهاية وقت العشاء الاختياري",
                icon = Icons.Default.Bedtime,
                isEnglish = isEnglish,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Single card row for one of the daily prayers (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha)
 */
@Composable
fun PrayerTimeRowCard(
    prayer: PrayerTimeItem,
    isEnglish: Boolean,
    azanAudioPlayer: AzanAudioPlayer,
    onCopyTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayName = if (isEnglish) prayer.nameEnglish else prayer.nameArabic
    val icon = getPrayerIcon(prayer.id)

    val cardBorder = when {
        prayer.isNext -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        prayer.isCurrent -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    val cardBg = when {
        prayer.isNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        prayer.isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        prayer.isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        prayer.isNext -> MaterialTheme.colorScheme.primary
                        prayer.isCurrent -> MaterialTheme.colorScheme.secondary
                        prayer.isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = when {
                                prayer.isNext -> MaterialTheme.colorScheme.onPrimary
                                prayer.isCurrent -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 15.sp,
                            fontWeight = if (prayer.isNext || prayer.isCurrent) FontWeight.Black else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (prayer.isNext) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (isEnglish) "NEXT" else "القادمة",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (prayer.isCurrent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = if (isEnglish) "CURRENT" else "الحالية",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (prayer.isPast) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "✓ Passed" else "✓ مضت",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Text(
                        text = if (prayer.id == "sunrise") {
                            if (isEnglish) "No prayer (sunrise)" else "وقت الشروق وانتهاء الفجر"
                        } else {
                            if (isEnglish) "Daily obligatory prayer" else "صلاة مفروضة"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Time and action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = prayer.timeFormatted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        prayer.isNext -> MaterialTheme.colorScheme.primary
                        prayer.isCurrent -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                if (prayer.id != "sunrise") {
                    IconButton(
                        onClick = {
                            val raw = if (prayer.id == "fajr") R.raw.adhan_makkah_hq else R.raw.adhan_egypt_mbc_hq
                            azanAudioPlayer.playRawSoundWithFadeIn(raw, useAlarmAttributes = false)
                            Toast.makeText(
                                context,
                                if (isEnglish) "Playing Adhan: $displayName 🔊"
                                else "أذان $displayName 🔊",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Listen",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onCopyTime,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Card for Sunnah / Night times (Tahajjud & Islamic Midnight)
 */
@Composable
fun SunnahPrayerCard(
    prayer: PrayerTimeItem,
    subtitle: String,
    icon: ImageVector,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = if (isEnglish) prayer.nameEnglish else prayer.nameArabic

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = prayer.timeFormatted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getPrayerIcon(id: String): ImageVector {
    return when (id) {
        "fajr" -> Icons.Default.WbTwilight
        "sunrise" -> Icons.Default.WbSunny
        "dhuhr" -> Icons.Default.LightMode
        "asr" -> Icons.Default.WbCloudy
        "maghrib" -> Icons.Default.NightsStay
        "isha" -> Icons.Default.Brightness3
        "tahajjud" -> Icons.Default.Star
        "midnight" -> Icons.Default.Bedtime
        else -> Icons.Default.AccessTime
    }
}
