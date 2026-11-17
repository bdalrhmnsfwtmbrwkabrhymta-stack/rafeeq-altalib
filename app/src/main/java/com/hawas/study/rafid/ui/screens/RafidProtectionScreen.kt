package com.hawas.study.rafid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidExcludedDomainEntity
import com.hawas.study.rafid.ui.components.StackedHeroCardContainer
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RafidProtectionScreen(
    config: RafidConfigState,
    excludedDomains: List<RafidExcludedDomainEntity>,
    onBack: () -> Unit,
    onSetBlockAdsAndTrackers: (Boolean) -> Unit,
    onSetBlockMalwareSites: (Boolean) -> Unit,
    onRefreshRulesNow: () -> Unit,
    onAddExcludedDomain: (String) -> Unit,
    onRemoveExcludedDomain: (String) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    var showExcludedDialog by remember { mutableStateOf(false) }

    val formattedLastUpdate = remember(config.lastRulesUpdateMillis, isEnglish) {
        if (config.lastRulesUpdateMillis <= 0L) {
            if (isEnglish) "Just updated" else "محدّثة للتو"
        } else {
            val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            fmt.format(Date(config.lastRulesUpdateMillis))
        }
    }

    val isShieldActive = config.blockAdsAndTrackers || config.blockMalwareSites

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("rafid_protection_screen")
    ) {
        // Solid #1565C0 Header matching reference screenshot 02acbf58
        Surface(
            color = palette.heroBlue,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("protection_btn_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "رجوع",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Smart Protection" else "الحماية الذكية",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 18.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Stacked Hero Protection Card
            item {
                StackedHeroCardContainer(modifier = Modifier.testTag("protection_hero_card")) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEnglish) "Blocked Today" else "حُجب اليوم",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.88f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isShieldActive) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                                )
                                Text(
                                    text = when {
                                        isShieldActive && isEnglish -> "Active"
                                        isShieldActive -> "تعمل"
                                        isEnglish -> "Paused"
                                        else -> "متوقفة"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = if (isEnglish) {
                                "${config.totalBlockedRequests} Requests"
                            } else {
                                "${config.totalBlockedRequests} طلب"
                            },
                            fontFamily = ReadexProFontFamily,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.testTag("protection_total_blocked")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProtectionStatSubBox(
                                count = config.blockedAdsCount,
                                label = if (isEnglish) "Ads" else "إعلانات",
                                modifier = Modifier.weight(1f)
                            )
                            ProtectionStatSubBox(
                                count = config.blockedTrackersCount,
                                label = if (isEnglish) "Trackers" else "متتبعات",
                                modifier = Modifier.weight(1f)
                            )
                            ProtectionStatSubBox(
                                count = config.blockedMalwareCount,
                                label = if (isEnglish) "Malware" else "مواقع ضارة",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. "What's Blocked" Grouped Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isEnglish) "What is Blocked" else "ما يُحجب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Row 1: Ads & Trackers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetBlockAdsAndTrackers(!config.blockAdsAndTrackers) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Ads and Trackers" else "الإعلانات والمتتبعات",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = palette.textPrimary
                                )
                                Switch(
                                    checked = config.blockAdsAndTrackers,
                                    onCheckedChange = onSetBlockAdsAndTrackers,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = palette.heroBlue
                                    ),
                                    modifier = Modifier.testTag("protection_switch_ads")
                                )
                            }

                            HorizontalDivider(
                                color = palette.borderSubtle.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Row 2: Malware Sites
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetBlockMalwareSites(!config.blockMalwareSites) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Malware Sites" else "المواقع الضارة",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = palette.textPrimary
                                )
                                Switch(
                                    checked = config.blockMalwareSites,
                                    onCheckedChange = onSetBlockMalwareSites,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = palette.heroBlue
                                    ),
                                    modifier = Modifier.testTag("protection_switch_malware")
                                )
                            }

                            HorizontalDivider(
                                color = palette.borderSubtle.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Row 3: Phishing Sites (Enabled pill badge)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Phishing Sites" else "مواقع الاحتيال",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = palette.textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.emeraldBg
                                ) {
                                    Text(
                                        text = if (isEnglish) "Enabled" else "مفعّلة",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.emeraldText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. "Protection Lists" Grouped Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isEnglish) "Protection Lists" else "قوائم الحماية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Last Updated" else "آخر تحديث",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = formattedLastUpdate,
                                        fontSize = 12.sp,
                                        color = palette.textSecondary
                                    )
                                }

                                Surface(
                                    onClick = onRefreshRulesNow,
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.heroBlue.copy(alpha = 0.1f),
                                    modifier = Modifier.testTag("protection_btn_update_rules")
                                ) {
                                    Text(
                                        text = if (isEnglish) "Update Now" else "حدّث الآن",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.heroBlue,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = palette.borderSubtle.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showExcludedDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .testTag("protection_row_excluded"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Excluded Sites" else "المواقع المستثناة",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = palette.textPrimary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = excludedDomains.size.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textSecondary
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExcludedDialog) {
        var newDomain by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExcludedDialog = false },
            containerColor = palette.surface,
            title = {
                Text(
                    text = if (isEnglish) "Excluded Sites" else "المواقع المستثناة",
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("excluded_domain_input")
                    )
                    Button(
                        onClick = {
                            if (newDomain.isNotBlank()) {
                                onAddExcludedDomain(newDomain)
                                newDomain = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_add_excluded_domain")
                    ) {
                        Text(if (isEnglish) "Add Domain" else "إضافة نطاق مستثنى")
                    }

                    if (excludedDomains.isEmpty()) {
                        Text(
                            text = if (isEnglish) "No excluded sites added yet." else "لا توجد مواقع مستثناة حالياً.",
                            fontSize = 12.5.sp,
                            color = palette.textSecondary
                        )
                    } else {
                        excludedDomains.forEach { entity ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = entity.domain,
                                    fontSize = 13.5.sp,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { onRemoveExcludedDomain(entity.domain) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove",
                                        tint = palette.dangerTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludedDialog = false }) {
                    Text(if (isEnglish) "Done" else "تم")
                }
            }
        )
    }
}

@Composable
private fun ProtectionStatSubBox(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalRafidPalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.heroBlueDarkPill.copy(alpha = 0.58f),
        modifier = modifier.height(74.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.86f)
            )
        }
    }
}
