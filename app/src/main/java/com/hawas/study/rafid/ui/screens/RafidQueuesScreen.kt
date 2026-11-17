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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.ui.components.StackedHeroCardContainer
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RafidQueuesScreen(
    config: RafidConfigState,
    downloads: List<RafidDownloadEntity>,
    onBack: () -> Unit,
    onAdjustMaxConcurrent: (Int) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetScheduledStart: (enabled: Boolean, hour: Int?, minute: Int?) -> Unit,
    onToggleQueuePaused: () -> Unit,
    onSetBlockAdsAndTrackers: (Boolean) -> Unit,
    onSetSmartEngineEnabled: (Boolean) -> Unit,
    onOpenProtection: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    var showTimePicker by remember { mutableStateOf(false) }

    val queueItems = remember(downloads) {
        downloads.filter {
            it.status in setOf(
                RafidDownloadStatus.DOWNLOADING.name,
                RafidDownloadStatus.MUXING.name,
                RafidDownloadStatus.QUEUED.name,
                RafidDownloadStatus.PAUSED.name
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("rafid_queues_screen")
    ) {
        // Solid #1565C0 Header matching reference screenshot 75ba2802
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
                        .testTag("queues_btn_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "رجوع",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Queues & Protection" else "الطوابير والحماية",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Stacked Main Queue Blue Card
            item {
                StackedHeroCardContainer(modifier = Modifier.testTag("queues_main_card")) {
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
                                text = if (isEnglish) "Main Queue" else "الطابور الرئيسي",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (config.isQueuePaused) Color(0xFFFEF3C7) else Color(0xFFC8F7DC)
                            ) {
                                Text(
                                    text = when {
                                        config.isQueuePaused && isEnglish -> "Paused"
                                        config.isQueuePaused -> "متوقف"
                                        isEnglish -> "Running"
                                        else -> "يعمل"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (config.isQueuePaused) Color(0xFF92400E) else Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isEnglish) "Concurrent Downloads" else "التحميلات المتزامنة",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.90f)
                        )

                        // Stepper Capsule (- [N] +)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = palette.heroBlueDarkPill.copy(alpha = 0.72f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { onAdjustMaxConcurrent(-1) },
                                    enabled = config.maxConcurrentDownloads > 1,
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(54.dp)
                                        .testTag("queues_btn_minus")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Remove,
                                            contentDescription = if (isEnglish) "Decrease concurrent downloads" else "تقليل التحميلات المتزامنة",
                                            tint = if (config.maxConcurrentDownloads > 1) palette.heroBlue else Color.Gray,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = config.maxConcurrentDownloads.toString(),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.testTag("queues_concurrent_count")
                                    )
                                }

                                Surface(
                                    onClick = { onAdjustMaxConcurrent(1) },
                                    enabled = config.maxConcurrentDownloads < 7,
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(54.dp)
                                        .testTag("queues_btn_plus")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = if (isEnglish) "Increase concurrent downloads" else "زيادة التحميلات المتزامنة",
                                            tint = if (config.maxConcurrentDownloads < 7) palette.heroBlue else Color.Gray,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Wi-Fi Only Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetWifiOnly(!config.wifiOnly) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isEnglish) "On Wi-Fi only" else "على الواي فاي فقط",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Switch(
                                checked = config.wifiOnly,
                                onCheckedChange = onSetWifiOnly,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = palette.heroBlue,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.28f),
                                    uncheckedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("queues_switch_wifi")
                            )
                        }

                        // Scheduled Start Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!config.isScheduledStartEnabled) {
                                        showTimePicker = true
                                    } else {
                                        onSetScheduledStart(false, null, null)
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isEnglish) "Scheduled start time" else "موعد البدء المجدول",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (config.isScheduledStartEnabled) {
                                        val timeLabel = String.format(
                                            Locale.US,
                                            "%02d:%02d",
                                            config.scheduledHour,
                                            config.scheduledMinute
                                        )
                                        Text(
                                            text = if (isEnglish) "Starts at $timeLabel (tap to edit)" else "يبدأ الساعة $timeLabel (اضغط للتعديل)",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.clickable { showTimePicker = true }
                                        )
                                    }
                                }
                            }
                            Switch(
                                checked = config.isScheduledStartEnabled,
                                onCheckedChange = { next ->
                                    if (next) {
                                        showTimePicker = true
                                    } else {
                                        onSetScheduledStart(false, null, null)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = palette.heroBlue,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.28f),
                                    uncheckedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("queues_switch_schedule")
                            )
                        }

                        // Pause / Resume Main Queue Full-width White Pill Button
                        Button(
                            onClick = onToggleQueuePaused,
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = palette.heroBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("queues_btn_toggle_pause")
                        ) {
                            Text(
                                text = when {
                                    config.isQueuePaused && isEnglish -> "Resume Queue"
                                    config.isQueuePaused -> "تشغيل الطابور"
                                    isEnglish -> "Pause Queue"
                                    else -> "إيقاف الطابور"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Smart Protection Toggle Card
            item {
                Surface(
                    onClick = onOpenProtection,
                    shape = RoundedCornerShape(22.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queues_card_protection")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = palette.heroBlue.copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = palette.heroBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEnglish) {
                                        "Smart Protection: ${config.totalBlockedRequests} ads & trackers blocked"
                                    } else {
                                        "الحماية الذكية: حُجب ${config.totalBlockedRequests} إعلان ومتتبع"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = palette.textPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEnglish) "Open protection details" else "إدارة قوائم الحماية والاستثناءات",
                                        fontSize = 12.sp,
                                        color = palette.heroBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = palette.heroBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = config.blockAdsAndTrackers,
                            onCheckedChange = onSetBlockAdsAndTrackers,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.heroBlue
                            ),
                            modifier = Modifier.testTag("queues_switch_protection")
                        )
                    }
                }
            }

            // 3. Extraction & Serial Muxer Engine Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) {
                                    "Smart Extraction & Serial Muxing Engine"
                                } else {
                                    "محرك الاستخلاص والدمج المتسلسل: جاهز ومحدّث"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (isEnglish) {
                                    "Resumable multi-stream downloads with lossless MP4/M4A muxing"
                                } else {
                                    "تحليل الروابط واستئناف التحميل ودمج الصوت بالفيديو بسلاسة"
                                },
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = config.smartEngineEnabled,
                            onCheckedChange = onSetSmartEngineEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.heroBlue
                            ),
                            modifier = Modifier.testTag("queues_switch_engine")
                        )
                    }
                }
            }

            // 4. Queued / Active Downloads Section
            item {
                Text(
                    text = if (isEnglish) "Queue Items (${queueItems.size})" else "عناصر الطابور (${queueItems.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
            }

            if (queueItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isEnglish) {
                                "No pending items in the queue right now."
                            } else {
                                "لا توجد تحميلات معلّقة في الطابور حالياً."
                            },
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(queueItems, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.qualityLabel} • ${
                                        RafidFormatUtils.formatFileSize(item.downloadedBytes, isEnglish)
                                    }",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary
                                )
                            }
                            Row {
                                val isActive = item.status == RafidDownloadStatus.DOWNLOADING.name ||
                                    item.status == RafidDownloadStatus.MUXING.name
                                IconButton(
                                    onClick = {
                                        if (isActive) onPauseDownload(item.id) else onResumeDownload(item.id)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isActive) "Pause" else "Resume",
                                        tint = palette.heroBlue
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDownload(item.id) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = palette.dangerTint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = config.scheduledHour,
            initialMinute = config.scheduledMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = palette.surface,
            title = {
                Text(
                    text = if (isEnglish) "Set Scheduled Start" else "تحديد وقت البدء المجدول",
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            selectorColor = palette.heroBlue,
                            clockDialSelectedContentColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetScheduledStart(true, pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue)
                ) {
                    Text(if (isEnglish) "Save" else "حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}
