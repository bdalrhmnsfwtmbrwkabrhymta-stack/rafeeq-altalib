package com.hawas.study.dofaati.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader
import kotlinx.coroutines.delay

@Composable
fun BatchScheduleCountdownScreen(viewModel: DofaatiViewModel) {
    val events by viewModel.scheduleEvents.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.scheduleRemindersEnabled.collectAsStateWithLifecycle()

    var remainingSecondsInDay by remember { mutableIntStateOf(4 * 3600 + 28 * 60 + 42) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (remainingSecondsInDay > 0) remainingSecondsInDay--
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractScheduleFromImageUri(uri)
        }
    }

    val launchScheduleScanner: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.44f,
        portraitContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DofaatiColors.Background)
                    .testTag("schedule_countdown_root")
            ) {
                DofaatiRoyalBlueHeader(
                    title = "جدول المواعيد والامتحانات القادمة",
                    subtitle = "تنبيهات تلقائية واستخراج ذكي من صور الجداول",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "schedule_header"
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        HeroExamCountdownCard(remainingSeconds = remainingSecondsInDay)
                    }
                    item {
                        OcrScheduleScanButton(onClick = launchScheduleScanner)
                    }
                    items(events, key = { it.id }) { ev ->
                        GlowingScheduleEventCard(event = ev)
                    }
                    item {
                        ScheduleReminderSwitchCard(
                            enabled = remindersEnabled,
                            onToggle = { viewModel.toggleScheduleNotifications(it) }
                        )
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DofaatiRoyalBlueHeader(
                    title = "جدول المواعيد والامتحانات القادمة",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "schedule_header_land"
                )
                HeroExamCountdownCard(remainingSeconds = remainingSecondsInDay)
                OcrScheduleScanButton(onClick = launchScheduleScanner)
                ScheduleReminderSwitchCard(
                    enabled = remindersEnabled,
                    onToggle = { viewModel.toggleScheduleNotifications(it) }
                )
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.id }) { ev ->
                    GlowingScheduleEventCard(event = ev)
                }
            }
        }
    )
}

@Composable
private fun HeroExamCountdownCard(remainingSeconds: Int) {
    val hours = remainingSeconds / 3600
    val mins = (remainingSeconds % 3600) / 60
    val secs = remainingSeconds % 60
    val formattedClock = "02d : %02dh : %02dm : %02ds".format(java.util.Locale.US, hours, mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_countdown_card"),
        shape = RoundedCornerShape(18.dp),
        color = DofaatiColors.RoyalBlue,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "باقي يومان و 4 ساعات على الامتحان الشامل",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = Color.White
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.16f)
            ) {
                DofaatiLtrText(
                    text = formattedClock,
                    fontSize = 17.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun OcrScheduleScanButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("btn_extract_schedule_ocr")
    ) {
        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = DofaatiColors.RoyalBlue)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "استخراج الموعد تلقائياً من صورة الجدول",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = DofaatiColors.RoyalBlue
        )
    }
}

@Composable
private fun GlowingScheduleEventCard(event: DofaatiScheduleEvent) {
    val borderAccent = if (event.isExam) DofaatiColors.PinnedOrange else DofaatiColors.RoyalBlue
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_event_${event.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.6.dp, borderAccent.copy(alpha = 0.65f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(borderAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = borderAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = event.dayAndTime,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = borderAccent
                )
                Text(
                    text = event.title,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = DofaatiColors.NavyInk
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = borderAccent.copy(alpha = 0.12f)
            ) {
                Text(
                    text = event.subjectName,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = borderAccent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduleReminderSwitchCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    DofaatiCard(
        borderColor = DofaatiColors.RoyalBlueTint,
        borderWidth = 1.2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "تفعيل إشعارات التذكير",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = DofaatiColors.NavyInk
                    )
                    Text(
                        text = "تنبيه ذكي قبل المحاضرات والامتحانات بساعة",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.5.sp,
                        color = DofaatiColors.SlateMuted
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = DofaatiColors.RoyalBlue
                ),
                modifier = Modifier.testTag("switch_schedule_notifications")
            )
        }
    }
}
