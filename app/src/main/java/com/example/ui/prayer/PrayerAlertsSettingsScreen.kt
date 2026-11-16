package com.example.ui.prayer

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.prayer.PrayerTimesResult
import com.example.ui.theme.DisplayArabicFontFamily

private val AlertsRoyalBlue = Color(0xFF1449A8)
private val AlertsBgWhite = Color(0xFFF8FAFC)

private data class PrayerAlertRowModel(
    val id: String,
    val label: String,
    val isSunrise: Boolean,
    val isChecked: Boolean
)

/**
 * Prayer & Sunrise Alerts Settings Screen:
 * - All 5 obligatory prayers (Fajr, Dhuhr, Asr, Maghrib, Isha) enabled by default.
 * - Sunrise alert ("الشروق 06:18 ص — مغلق افتراضياً (فعّله يدوياً)") strictly OFF by default until manually enabled.
 */
@Composable
fun PrayerAlertsSettingsScreen(
    prayerTimes: PrayerTimesResult?,
    mutedPrayers: Set<String>,
    isSunriseAlertEnabled: Boolean,
    onTogglePrayerMute: (String) -> Unit,
    onToggleSunriseAlert: (Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true
) {
    val fajrTime = prayerTimes?.fajr?.timeString ?: "04:52 ص"
    val sunriseTime = prayerTimes?.sunrise?.timeString ?: "06:18 ص"
    val dhuhrTime = prayerTimes?.dhuhr?.timeString ?: "11:48 ص"
    val asrTime = prayerTimes?.asr?.timeString ?: "02:51 م"
    val maghribTime = prayerTimes?.maghrib?.timeString ?: "05:14 م"
    val ishaTime = prayerTimes?.isha?.timeString ?: "06:35 م"

    val rows = listOf(
        PrayerAlertRowModel(
            id = "fajr",
            label = "الفجر $fajrTime",
            isSunrise = false,
            isChecked = !mutedPrayers.contains("fajr")
        ),
        PrayerAlertRowModel(
            id = "sunrise",
            label = "الشروق $sunriseTime — مغلق افتراضياً (فعّله يدوياً)",
            isSunrise = true,
            isChecked = isSunriseAlertEnabled
        ),
        PrayerAlertRowModel(
            id = "dhuhr",
            label = "الظهر $dhuhrTime",
            isSunrise = false,
            isChecked = !mutedPrayers.contains("dhuhr")
        ),
        PrayerAlertRowModel(
            id = "asr",
            label = "العصر $asrTime",
            isSunrise = false,
            isChecked = !mutedPrayers.contains("asr")
        ),
        PrayerAlertRowModel(
            id = "maghrib",
            label = "المغرب $maghribTime",
            isSunrise = false,
            isChecked = !mutedPrayers.contains("maghrib")
        ),
        PrayerAlertRowModel(
            id = "isha",
            label = "العشاء $ishaTime",
            isSunrise = false,
            isChecked = !mutedPrayers.contains("isha")
        )
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val rootModifier = if (showTopBar) {
            Modifier
                .fillMaxSize()
                .background(AlertsBgWhite)
                .safeDrawingPadding()
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = rootModifier) {
            if (showTopBar) {
                Surface(
                    color = AlertsRoyalBlue,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("prayer_alerts_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = "مواقيت الصلاة وتنبيهات الشروق",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prayer_alerts_title")
                        )
                    }
                }
            } else {
                Surface(
                    color = AlertsRoyalBlue,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "مواقيت الصلاة وتنبيهات الشروق",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("prayer_alerts_title")
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showTopBar) {
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        } else {
                            Modifier.padding(top = 12.dp)
                        }
                    )
            ) {
                val isWide = maxWidth >= 600.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 820.dp)
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isWide) {
                        rows.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { item ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        PrayerAlertToggleCard(
                                            item = item,
                                            onCheckedChange = { checked ->
                                                if (item.isSunrise) onToggleSunriseAlert(checked)
                                                else onTogglePrayerMute(item.id)
                                            }
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        rows.forEach { item ->
                            PrayerAlertToggleCard(
                                item = item,
                                onCheckedChange = { checked ->
                                    if (item.isSunrise) onToggleSunriseAlert(checked)
                                    else onTogglePrayerMute(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerAlertToggleCard(
    item: PrayerAlertRowModel,
    onCheckedChange: (Boolean) -> Unit
) {
    val switchTag = if (item.isSunrise) "sunrise_alert_switch" else "prayer_alert_switch_${item.id}"

    Surface(
        onClick = { onCheckedChange(!item.isChecked) },
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isSunrise && !item.isChecked) Color(0xFFE2E8F0) else Color(0xFFDBEAFE)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("prayer_alert_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.label,
                fontFamily = DisplayArabicFontFamily,
                fontSize = if (item.isSunrise) 13.5.sp else 16.sp,
                fontWeight = if (item.isSunrise && !item.isChecked) FontWeight.Medium else FontWeight.Bold,
                color = if (item.isSunrise && !item.isChecked) Color(0xFF64748B) else Color(0xFF0F172A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AlertsRoyalBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCBD5E1),
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.testTag(switchTag)
            )
        }
    }
}
