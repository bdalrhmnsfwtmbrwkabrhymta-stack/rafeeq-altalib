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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Locale

@Composable
fun BatchScheduleCountdownScreen(viewModel: DofaatiViewModel) {
    val events by viewModel.scheduleEvents.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.scheduleRemindersEnabled.collectAsStateWithLifecycle()

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    var showAddEventDialog by remember { mutableStateOf(false) }

    val nearestUpcoming = remember(events, nowMillis) {
        events.filter { it.targetTimestampMillis > nowMillis }.minByOrNull { it.targetTimestampMillis }
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
                    subtitle = "مواعيدك الحقيقية واستخراج ذكي من صور الجداول",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "schedule_header"
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        HeroExamCountdownCard(
                            nearestEvent = nearestUpcoming,
                            nowMillis = nowMillis
                        )
                    }
                    item {
                        ScheduleActionButtonsRow(
                            onAddManual = { showAddEventDialog = true },
                            onScanImage = launchScheduleScanner
                        )
                    }
                    if (events.isEmpty()) {
                        item {
                            DofaatiCard {
                                Text(
                                    text = "الجدول فارغ حالياً. أضف موعد محاضرة أو امتحان يدوياً أو امسح صورة جدولك.",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 13.sp,
                                    color = DofaatiColors.SlateMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        items(events, key = { it.id }) { ev ->
                            GlowingScheduleEventCard(
                                event = ev,
                                onDelete = { viewModel.deleteScheduleEvent(ev.id) }
                            )
                        }
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
                HeroExamCountdownCard(nearestEvent = nearestUpcoming, nowMillis = nowMillis)
                ScheduleActionButtonsRow(
                    onAddManual = { showAddEventDialog = true },
                    onScanImage = launchScheduleScanner
                )
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
                if (events.isEmpty()) {
                    item {
                        DofaatiCard {
                            Text(
                                text = "لا توجد مواعيد مضافة بعد.",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.sp,
                                color = DofaatiColors.SlateMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(events, key = { it.id }) { ev ->
                        GlowingScheduleEventCard(
                            event = ev,
                            onDelete = { viewModel.deleteScheduleEvent(ev.id) }
                        )
                    }
                }
            }
        }
    )

    if (showAddEventDialog) {
        AddScheduleEventDialog(
            onAdd = { title, subj, dayLabel, isExam, hours ->
                viewModel.addManualScheduleEvent(
                    title = title,
                    subjectName = subj,
                    dayAndTimeLabel = dayLabel,
                    isExam = isExam,
                    hoursFromNow = hours
                )
                showAddEventDialog = false
            },
            onDismiss = { showAddEventDialog = false }
        )
    }
}

@Composable
private fun HeroExamCountdownCard(
    nearestEvent: DofaatiScheduleEvent?,
    nowMillis: Long
) {
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
            if (nearestEvent == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لا توجد مواعيد أو امتحانات قادمة حالياً",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val totalSecs = ((nearestEvent.targetTimestampMillis - nowMillis) / 1000L).coerceAtLeast(0L)
                val days = totalSecs / 86_400L
                val hours = (totalSecs % 86_400L) / 3_600L
                val mins = (totalSecs % 3_600L) / 60L
                val secs = totalSecs % 60L
                val formattedClock = "%02dd : %02dh : %02dm : %02ds".format(Locale.US, days, hours, mins, secs)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الموعد القادم: ${nearestEvent.title}",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
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
}

@Composable
private fun ScheduleActionButtonsRow(
    onAddManual: () -> Unit,
    onScanImage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAddManual,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_add_schedule_manual")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "+ إضافة موعد أو امتحان",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        OutlinedButton(
            onClick = onScanImage,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_extract_schedule_ocr")
        ) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = DofaatiColors.RoyalBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "استخراج الموعد تلقائياً من صورة الجدول",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = DofaatiColors.RoyalBlue
            )
        }
    }
}

@Composable
private fun GlowingScheduleEventCard(
    event: DofaatiScheduleEvent,
    onDelete: () -> Unit
) {
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
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الموعد", tint = DofaatiColors.RedAlert)
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
                        text = "تنبيه ذكي قبل المحاضرات والامتحانات",
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

@Composable
private fun AddScheduleEventDialog(
    onAdd: (title: String, subject: String, dayAndTime: String, isExam: Boolean, hoursFromNow: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var dayLabel by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("24") }
    var isExam by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("إضافة موعد أو امتحان", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الموعد", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("المادة الدراسية", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dayLabel,
                    onValueChange = { dayLabel = it },
                    label = { Text("اليوم والساعة (مثال: الخميس 5 مساءً)", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("بعد كم ساعة من الآن؟", fontFamily = ReadexProFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isExam, onCheckedChange = { isExam = it })
                    Text("امتحان / اختبار مهم", fontFamily = ReadexProFontFamily, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().isNotEmpty(),
                onClick = {
                    val hrs = hoursText.toIntOrNull()?.coerceIn(1, 24 * 90) ?: 24
                    onAdd(title, subject, dayLabel, isExam, hrs)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("حفظ", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}
