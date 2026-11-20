package com.example.studytools.timer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.notification.NotificationReliabilityManager
import com.example.ui.MainViewModel
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.util.Locale

@Composable
fun StudyTimerToolScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val timerState by StudyTimerCoordinator.snapshot.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val pomodoroLogs by viewModel.pomodoroLogs.collectAsStateWithLifecycle()

    LaunchedEffect(timerState.pendingCompletedMinutesToLog) {
        val pending = timerState.pendingCompletedMinutesToLog
        if (pending > 0) {
            val consumed = StudyTimerCoordinator.consumePendingLoggedMinutes()
            if (consumed > 0) {
                viewModel.logPomodoroSession(
                    durationMinutes = consumed,
                    subjectName = timerState.subjectLabel,
                    sessionType = "FOCUS"
                )
                Toast.makeText(
                    context,
                    "تم تسجيل $consumed دقيقة تركيز وحصولك على ${consumed * 2} نقطة XP!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var customMinutes by remember { mutableFloatStateOf(30f) }
    var focusLockEnabled by remember { mutableStateOf(false) }
    var showRestrictedOverlayDialog by remember { mutableStateOf(false) }
    var pendingEnableFocusLock by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentView = LocalView.current

    DisposableEffect(focusLockEnabled, timerState.isRunning) {
        currentView.keepScreenOn = focusLockEnabled && timerState.isRunning
        onDispose { currentView.keepScreenOn = false }
    }

    DisposableEffect(lifecycleOwner, pendingEnableFocusLock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingEnableFocusLock) {
                if (NotificationReliabilityManager.canDrawOverlays(context)) {
                    focusLockEnabled = true
                    showRestrictedOverlayDialog = false
                    pendingEnableFocusLock = false
                    Toast.makeText(context, "تم تفعيل قفل التركيز بنجاح ✅", Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val displaySeconds = if (timerState.mode.isCountUp) timerState.elapsedSeconds else timerState.remainingSeconds
    val formattedTime = remember(displaySeconds) {
        val hrs = displaySeconds / 3600
        val mins = (displaySeconds % 3600) / 60
        val secs = displaySeconds % 60
        if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    val ringProgress = remember(timerState) {
        if (timerState.mode.isCountUp) {
            ((timerState.elapsedSeconds % 60) / 60f)
        } else {
            val total = timerState.totalTargetSeconds.coerceAtLeast(1)
            (timerState.remainingSeconds.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }

    val todayMinutes = remember(pomodoroLogs) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        pomodoroLogs.filter { it.timestampMillis >= cal && it.sessionType == "FOCUS" }
            .sumOf { it.durationMinutes }
    }

    val accentColor by animateColorAsState(
        targetValue = if (timerState.phase == StudyTimerPhase.BREAK) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "timerAccent"
    )

    val subjectSuggestions = remember(subjects) {
        val base = listOf("مذاكرة عامة", "رياضيات", "فيزياء", "كيمياء", "لغة عربية", "إنجليزي", "أحياء")
        val dbNames = subjects.map { it.name }.filter { it.isNotBlank() }
        (dbNames + base).distinct()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dark Navy Header matching Study Tools aesthetic
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141E30),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("timer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "مُؤَقِّتُ المُذَاكَرَة",
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF5B041)
                                )
                                Text(
                                    text = "تركيز متواصل مع إشعار حي في الخلفية",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "$todayMinutes د",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Mode selector chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudyTimerMode.entries.forEach { mode ->
                        val selected = timerState.mode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                StudyTimerForegroundService.pause(context)
                                StudyTimerCoordinator.selectMode(mode)
                            },
                            label = {
                                Text(
                                    text = mode.titleAr,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("timer_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                // Main Circular Countdown Ring Card
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${timerState.phase.labelAr} • ${timerState.subjectLabel}",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokePx = 16.dp.toPx()
                                val inset = strokePx / 2f
                                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                                drawArc(
                                    color = trackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * ringProgress,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedTime,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("timer_display_text")
                                )
                                Text(
                                    text = "الدورات المكتملة: ${timerState.completedCycles}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Primary Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (timerState.isRunning) {
                                        StudyTimerForegroundService.pause(context)
                                    } else {
                                        StudyTimerForegroundService.startOrResume(context)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (timerState.isRunning) Color(0xFFD97706) else accentColor
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(52.dp)
                                    .testTag("timer_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (timerState.isRunning) "إيقاف مؤقت" else "بدء المؤقت"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (timerState.isRunning) "إيقاف مؤقت" else "ابدأ التركيز",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (timerState.mode.isCountUp && timerState.elapsedSeconds >= 30) {
                                        StudyTimerForegroundService.pause(context)
                                        val mins = StudyTimerCoordinator.finishStopwatchEarly()
                                        if (mins > 0) {
                                            viewModel.logPomodoroSession(
                                                durationMinutes = mins,
                                                subjectName = timerState.subjectLabel,
                                                sessionType = "FOCUS"
                                            )
                                            Toast.makeText(
                                                context,
                                                "تم حفظ $mins دقيقة في سجل المذاكرة",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        StudyTimerForegroundService.stop(context)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("timer_reset_finish_button")
                            ) {
                                Icon(
                                    imageVector = if (timerState.mode.isCountUp) Icons.Default.Check else Icons.Default.Refresh,
                                    contentDescription = if (timerState.mode.isCountUp) "حفظ الجلسة" else "تصفير"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (timerState.mode.isCountUp) "حفظ" else "تصفير",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Custom Duration Slider (when not count-up)
                if (!timerState.mode.isCountUp) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تخصيص مدة الجلسة",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${customMinutes.toInt()} دقيقة",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = customMinutes,
                                onValueChange = { customMinutes = it },
                                onValueChangeFinished = {
                                    StudyTimerForegroundService.pause(context)
                                    StudyTimerCoordinator.setCustomMinutes(customMinutes.toInt())
                                },
                                valueRange = 5f..120f,
                                steps = 22,
                                modifier = Modifier.testTag("timer_custom_slider")
                            )
                        }
                    }
                }

                // Subject Selection Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "المادة الحالية",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subjectSuggestions.forEach { subj ->
                                val isSelected = timerState.subjectLabel == subj
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(50))
                                        .clickable { StudyTimerCoordinator.setSubjectLabel(subj) }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = subj,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // On-demand Focus Lock Card (checks Overlay permission ONLY when toggled ON)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (focusLockEnabled) Color(0xFF0F62FE).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F62FE).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenLockPortrait,
                                    contentDescription = "قفل التركيز",
                                    tint = Color(0xFF0F62FE),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "قفل التركيز (إبقاء الشاشة نشطة)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (focusLockEnabled) {
                                        "مفعّل — حماية من التشتت وإغلاق الشاشة"
                                    } else {
                                        "اختياري — يتطلب إذن الظهور فوق التطبيقات فقط عند التفعيل"
                                    },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = focusLockEnabled,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable) {
                                    focusLockEnabled = false
                                    pendingEnableFocusLock = false
                                } else if (NotificationReliabilityManager.canDrawOverlays(context)) {
                                    focusLockEnabled = true
                                    pendingEnableFocusLock = false
                                } else {
                                    pendingEnableFocusLock = true
                                    showRestrictedOverlayDialog = true
                                }
                            },
                            modifier = Modifier.testTag("timer_focus_lock_switch")
                        )
                    }
                }
            }
        }

        if (showRestrictedOverlayDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRestrictedOverlayDialog = false
                    pendingEnableFocusLock = false
                },
                title = { Text("قفل التركيز وإذن الظهور", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "لتشغيل قفل التركيز فوق التطبيقات على أندرويد 13+:\n" +
                            "١. افتح معلومات التطبيق واختر الثلاث نقاط (⋮) ← السماح بالإعدادات المقيدة.\n" +
                            "٢. فعّل خيار «إظهار فوق التطبيقات الأخرى»."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { NotificationReliabilityManager.openAppSettings(context) }
                    ) {
                        Text("فتح معلومات التطبيق")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRestrictedOverlayDialog = false
                            pendingEnableFocusLock = false
                            focusLockEnabled = false
                        }
                    ) {
                        Text("متابعة بدون قفل الشاشة")
                    }
                }
            )
        }
    }
}
