package com.example.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserSettings
import com.example.domain.prayer.PrayerTimesResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * MODULE 3: Main Application Dashboard Components (الشاشة الرئيسية)
 *
 * 1. Top Header Bar:
 *    - Grid Menu Icon
 *    - Theme Switcher (Light / Dark / Space)
 *    - Verified Status Badge ("رفيق الطالب الذهبي")
 *    - Notification Bell with unread badge counter.
 *
 * 2. Prayer Countdown & Location Bar:
 *    - Dynamic location name display
 *    - Live real-time Countdown Timer (Hours : Minutes : Seconds) to the next prayer.
 *
 * 3. Daily Prayer Timetable & Night Calculations:
 *    - 6 Daily prayer times with custom weather/time icons (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha).
 *    - Night division section: Midnight (منتصف الليل) and Last Third of Night (الثلث الأخير).
 *
 * 4. Interactive Announcements & Status Cards:
 *    - Administrative text box ("مرحبا... نسعى لتقريب المسافات...")
 *    - Account upgrade promo card with close button (X).
 */

@Composable
fun RafeeqTopHeaderBar(
    userSettings: UserSettings,
    unreadNotificationsCount: Int = 2,
    onOpenMenu: () -> Unit = {},
    onToggleTheme: (String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Student Profile & Account Greeting
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenMenu() }
                    .padding(4.dp)
            ) {
                // Profile Avatar Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = userSettings.userName.trim().take(1).ifEmpty { "ط" }
                    Text(
                        text = initials,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "مرحباً بك 👋",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = userSettings.userName.ifBlank { "الطالب المجتهد" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right: Theme Switcher & Notification Bell
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Theme Switcher (Light / Dark / Space)
                val currentTheme = userSettings.appThemePreset.lowercase()
                val nextTheme = when (currentTheme) {
                    "light" -> "dark"
                    "dark" -> "space"
                    else -> "light"
                }
                val themeIcon = when (currentTheme) {
                    "dark" -> Icons.Default.DarkMode
                    "space" -> Icons.Default.AutoAwesome
                    else -> Icons.Default.LightMode
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = { onToggleTheme(nextTheme) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            themeIcon,
                            contentDescription = "تبديل المظهر",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Notification Bell with unread counter badge
                Box(contentAlignment = Alignment.TopEnd) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "الإشعارات",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (unreadNotificationsCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 2.dp)
                                .size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$unreadNotificationsCount",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 2. PRAYER COUNTDOWN & LOCATION BAR
// -----------------------------------------------------------------------------------------

@Composable
fun PrayerCountdownLocationBar(
    cityName: String,
    prayerTimesResult: PrayerTimesResult?,
    onOpenPrayerScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Real-time countdown clock updating every second
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val nextPrayerInfo = remember(currentTimeMs, prayerTimesResult) {
        if (prayerTimesResult != null) {
            calculateNextPrayerInfo(currentTimeMs, prayerTimesResult)
        } else {
            NextPrayerCalc("الظهر", currentTimeMs, "01:15:46")
        }
    }

    val displayCity = if (cityName.isNotBlank() && cityName != "القاهرة") cityName else "عزب بسنتاواي - مصر"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenPrayerScreen() },
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0xFFF1E5DE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Right: Location Info + Pin Icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "الموقع الحالي",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayCity,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E1B4B)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE2574C),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "الموقع",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Left: Real-time Countdown Timer Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "متبقي لأذان ${nextPrayerInfo.prayerNameAr}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2574C)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = nextPrayerInfo.formattedCountdown,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E1B4B)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. DAILY PRAYER TIMETABLE & NIGHT CALCULATIONS
// -----------------------------------------------------------------------------------------

data class SinglePrayerItemSpec(
    val id: String,
    val nameAr: String,
    val timeFormatted: String,
    val iconEmoji: String,
    val isNext: Boolean = false
)

@Composable
fun DailyPrayerTimetableNightCard(
    prayerTimesResult: PrayerTimesResult?,
    onOpenPrayerScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (prayerTimesResult == null) return

    val prayers = remember(prayerTimesResult) {
        listOf(
            SinglePrayerItemSpec("fajr", "الفجر", prayerTimesResult.fajr.timeString, "🌅"),
            SinglePrayerItemSpec("sunrise", "الشروق", prayerTimesResult.sunrise.timeString, "☀️"),
            SinglePrayerItemSpec("dhuhr", "الظهر", prayerTimesResult.dhuhr.timeString, "☀️"),
            SinglePrayerItemSpec("asr", "العصر", prayerTimesResult.asr.timeString, "🌤️"),
            SinglePrayerItemSpec("maghrib", "المغرب", prayerTimesResult.maghrib.timeString, "🌇"),
            SinglePrayerItemSpec("isha", "العشاء", prayerTimesResult.isha.timeString, "🌙")
        )
    }

    // Calculations for Midnight and Last Third of Night
    val nightCalculations = remember(prayerTimesResult) {
        calculateNightDivisions(prayerTimesResult)
    }

    Card(
        onClick = onOpenPrayerScreen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕌", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مواقيت الصلوات والثلث الأخير",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "عرض التفاصيل ←",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6 Daily Prayer Times Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                prayers.forEach { prayer ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(prayer.iconEmoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = prayer.nameAr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = prayer.timeFormatted,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Night Division Section: Midnight & Last Third
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Midnight (منتصف الليل)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌌", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("منتصف الليل", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(nightCalculations.midnightFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Last Third (الثلث الأخير)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌠", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("الثلث الأخير (قيام الليل)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(nightCalculations.lastThirdFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 4. INTERACTIVE ANNOUNCEMENTS & STATUS CARDS
// -----------------------------------------------------------------------------------------

@Composable
fun InteractiveAnnouncementsCard(
    modifier: Modifier = Modifier
) {
    var isPromoVisible by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Administrative announcement box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "إعلان إداري هام 📢",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "مرحباً بكم في منصة رفيق الطالب... نسعى لتقريب المسافات وتوفير كافة أدوات التفوق الأكاديمي والالتزام الديني في تطبيق واحد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // 2. Account Upgrade Promo Card with close (X) button
        AnimatedVisibility(
            visible = isPromoVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✨", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "حساب رفيق الطالب الذهبي المميز",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "احصل على جميع ميزات الذكاء الاصطناعي والمزامنة السحابية مجاناً!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isPromoVisible = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إغلاق الإعلان",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Helper Calculations
// -----------------------------------------------------------------------------------------

private data class NextPrayerCalc(
    val prayerNameAr: String,
    val targetTimeMs: Long,
    val formattedCountdown: String
)

private fun calculateNextPrayerInfo(nowMs: Long, result: PrayerTimesResult): NextPrayerCalc {
    val prayers = listOf(
        Pair("الفجر", result.fajr.timestampMillis),
        Pair("الشروق", result.sunrise.timestampMillis),
        Pair("الظهر", result.dhuhr.timestampMillis),
        Pair("العصر", result.asr.timestampMillis),
        Pair("المغرب", result.maghrib.timestampMillis),
        Pair("العشاء", result.isha.timestampMillis)
    )

    val next = prayers.firstOrNull { it.second > nowMs } ?: Pair("الفجر (غداً)", result.fajr.timestampMillis + 86400000L)
    val diffMs = (next.second - nowMs).coerceAtLeast(0L)

    val hours = diffMs / (1000 * 60 * 60)
    val minutes = (diffMs / (1000 * 60)) % 60
    val seconds = (diffMs / 1000) % 60

    val countdownStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    return NextPrayerCalc(next.first, next.second, countdownStr)
}

private data class NightDivisions(
    val midnightFormatted: String,
    val lastThirdFormatted: String
)

private fun calculateNightDivisions(result: PrayerTimesResult): NightDivisions {
    val maghribMs = result.maghrib.timestampMillis
    var fajrNextMs = result.fajr.timestampMillis
    if (fajrNextMs <= maghribMs) {
        fajrNextMs += 86400000L
    }

    val nightDuration = fajrNextMs - maghribMs
    val midnightMs = maghribMs + (nightDuration / 2)
    val lastThirdMs = maghribMs + (nightDuration * 2 / 3)

    val sdf = SimpleDateFormat("h:mm a", Locale("ar"))
    return NightDivisions(
        midnightFormatted = sdf.format(Date(midnightMs)),
        lastThirdFormatted = sdf.format(Date(lastThirdMs))
    )
}
