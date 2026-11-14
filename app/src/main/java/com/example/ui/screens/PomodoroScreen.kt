package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POMODORO_CHANNEL_ID = "pomodoro_timer_channel"
private const val NOTIFICATION_ID_FOCUS = 1001
private const val NOTIFICATION_ID_BREAK = 1002

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings by viewModel.userSettings.collectAsState()
    val presets by viewModel.pomodoroPresets.collectAsState()

    // Create Notification Channel for Pomodoro Alerts
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                POMODORO_CHANNEL_ID,
                "تنبيهات مؤقت المذاكرة (بومودورو)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات انتهاء فترات المذاكرة والتركيز والاستراحة في مؤقت بومودورو"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    // Notification permission check on Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    // Active Mode: FOCUS (25 min), SHORT_BREAK (5 min), LONG_BREAK (15 min)
    var currentMode by remember { mutableStateOf("FOCUS") }

    // Session Counter
    var completedSessionsCount by remember { mutableIntStateOf(0) }
    val totalTargetSessions = 4

    // Precise Duration Controls (Default standard Pomodoro: 25 min work, 5 min break)
    var focusMinutes by remember { mutableIntStateOf(25) }
    var focusSeconds by remember { mutableIntStateOf(0) }
    var breakMinutes by remember { mutableIntStateOf(5) }
    var breakSeconds by remember { mutableIntStateOf(0) }
    var longBreakMinutes by remember { mutableIntStateOf(15) }

    val totalDurationSeconds = remember(currentMode, focusMinutes, focusSeconds, breakMinutes, breakSeconds, longBreakMinutes) {
        when (currentMode) {
            "FOCUS" -> (focusMinutes * 60) + focusSeconds
            "LONG_BREAK" -> longBreakMinutes * 60
            else -> (breakMinutes * 60) + breakSeconds
        }
    }

    var timerSeconds by remember { mutableIntStateOf(totalDurationSeconds) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Alarm Playing & Active States
    var isAlarmRinging by remember { mutableStateOf(false) }
    var alarmMessage by remember { mutableStateOf("") }
    var activeRingtone by remember { mutableStateOf<Ringtone?>(null) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var autoStopJob by remember { mutableStateOf<Job?>(null) }

    // Dialog States
    var showPresetDialog by remember { mutableStateOf(false) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }

    // SharedPreferences for Pomodoro Audio Preferences
    val pomodoroSharedPrefs = remember {
        context.getSharedPreferences("pomodoro_audio_prefs", Context.MODE_PRIVATE)
    }

    // Sound selections
    var startSound by remember {
        mutableStateOf(
            pomodoroSharedPrefs.getString("pomodoro_start_sound", null)
                ?: userSettings.pomodoroStartSoundUri.ifBlank { "BUILTIN_CHIME" }
        )
    }
    var finishSound by remember {
        mutableStateOf(
            pomodoroSharedPrefs.getString("pomodoro_finish_sound", null)
                ?: userSettings.pomodoroFinishSoundUri.ifBlank { "DEFAULT_ALARM" }
        )
    }
    var breakSound by remember {
        mutableStateOf(
            pomodoroSharedPrefs.getString("pomodoro_break_sound", null)
                ?: userSettings.pomodoroBreakSoundUri.ifBlank { "BUILTIN_BELL" }
        )
    }
    var customSoundUri by remember {
        mutableStateOf(
            pomodoroSharedPrefs.getString("pomodoro_finish_sound_custom", null)
                ?: userSettings.pomodoroFinishSoundUri
        )
    }
    var customSoundName by remember {
        mutableStateOf(
            pomodoroSharedPrefs.getString("pomodoro_finish_sound_name", null)
                ?: userSettings.pomodoroFinishSoundName.ifBlank { "ملف نغمة مخصص من الهاتف 🎵" }
        )
    }

    // Stop audio & vibration helper
    fun stopAlarmAudio() {
        try {
            activeMediaPlayer?.let { player ->
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
            activeMediaPlayer = null
        } catch (_: Exception) {}
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {}
        autoStopJob?.cancel()
        autoStopJob = null
        isAlarmRinging = false
    }

    // Send System Notification Helper
    fun sendSystemNotification(title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val notifyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "pomodoro")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, POMODORO_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    // Play tone helper with vibration & multi-source support
    fun playAudioAlert(soundKey: String, isAlarm: Boolean = false) {
        stopAlarmAudio()
        try {
            // Trigger vibration feedback
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                    }
                }
            } catch (_: Exception) {}

            var played = false

            // Case 1: Custom audio file URI selected from device
            if (soundKey.startsWith("content://") || soundKey.startsWith("file://")) {
                try {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(if (isAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(context, Uri.parse(soundKey))
                        prepare()
                        start()
                    }
                    activeMediaPlayer = mp
                    played = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Case 2: Built-in notification tones
            if (!played && (soundKey == "BUILTIN_CHIME" || soundKey == "BUILTIN_BELL" || soundKey == "BUILTIN_BEEP")) {
                try {
                    val stream = if (isAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
                    val toneGen = ToneGenerator(stream, 100)
                    val toneType = when (soundKey) {
                        "BUILTIN_BELL" -> ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE
                        "BUILTIN_CHIME" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                        else -> ToneGenerator.TONE_PROP_BEEP2
                    }
                    toneGen.startTone(toneType, if (isAlarm) 3000 else 1200)
                    played = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Case 3: System Ringtone / Alarm / Notification
            if (!played) {
                val ringtoneType = when (soundKey) {
                    "DEFAULT_ALARM" -> RingtoneManager.TYPE_ALARM
                    "DEFAULT_RINGTONE" -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }

                var uri = RingtoneManager.getDefaultUri(ringtoneType)
                if (uri == null) {
                    uri = when (ringtoneType) {
                        RingtoneManager.TYPE_ALARM -> Settings.System.DEFAULT_ALARM_ALERT_URI
                        RingtoneManager.TYPE_RINGTONE -> Settings.System.DEFAULT_RINGTONE_URI
                        else -> Settings.System.DEFAULT_NOTIFICATION_URI
                    }
                }
                if (uri == null) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }

                if (uri != null) {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    if (ringtone != null) {
                        val usage = if (isAlarm || soundKey == "DEFAULT_ALARM") AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        ringtone.play()
                        activeRingtone = ringtone
                        played = true
                    }
                }
            }

            // Fallback to ToneGenerator
            if (!played) {
                try {
                    val stream = if (isAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
                    val toneGen = ToneGenerator(stream, 100)
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
                } catch (_: Exception) {}
            }

            // Auto stop preview / alarm after duration
            autoStopJob = coroutineScope.launch {
                delay(if (isAlarm) 15000L else 3000L)
                stopAlarmAudio()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 1200)
            } catch (_: Exception) {}
        }
    }

    // System Audio Picker Launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var displayName = "ملف نغمة مخصص من الهاتف 🎵"
            try {
                context.contentResolver.query(pickedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
            } catch (_: Exception) {}

            val uriStr = pickedUri.toString()
            customSoundUri = uriStr
            customSoundName = displayName
            finishSound = uriStr

            try {
                pomodoroSharedPrefs.edit()
                    .putString("pomodoro_finish_sound", uriStr)
                    .putString("pomodoro_finish_sound_custom", uriStr)
                    .putString("pomodoro_finish_sound_name", displayName)
                    .apply()
            } catch (_: Exception) {}

            Toast.makeText(context, "تم اختيار النغمة بنجاح: $displayName", Toast.LENGTH_SHORT).show()
            playAudioAlert(uriStr, isAlarm = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAlarmAudio()
        }
    }

    // Bind to Background PomodoroService
    val serviceSeconds by com.example.domain.pomodoro.PomodoroService.secondsRemaining.collectAsState()
    val serviceIsRunning by com.example.domain.pomodoro.PomodoroService.isRunning.collectAsState()
    val serviceMode by com.example.domain.pomodoro.PomodoroService.currentMode.collectAsState()
    val serviceAlarmRinging by com.example.domain.pomodoro.PomodoroService.isAlarmRinging.collectAsState()
    val serviceTotalDuration by com.example.domain.pomodoro.PomodoroService.totalDurationSeconds.collectAsState()

    // Listen for completion events from background service
    LaunchedEffect(Unit) {
        com.example.domain.pomodoro.PomodoroService.sessionCompletedEvent.collect { (completedMode, minutes) ->
            completedSessionsCount++
            if (completedMode == "FOCUS") {
                viewModel.logPomodoroSession(null, if (minutes > 0) minutes else 25)
                alarmMessage = "لقد أتممت جلسة دراسية بنجاح ($minutes دقيقة). حان وقت أخذ استراحة للراحة ☕"
            } else {
                alarmMessage = "انتهت فترة الاستراحة! حان وقت العودة لمواصلة المذاكرة والتركيز 🚀"
            }
        }
    }

    val effectiveSeconds = if (serviceIsRunning || (serviceSeconds in 1 until serviceTotalDuration)) serviceSeconds else totalDurationSeconds
    val effectiveTotalSeconds = if (serviceIsRunning || (serviceSeconds in 1 until serviceTotalDuration)) serviceTotalDuration else totalDurationSeconds
    val isTimerActive = serviceIsRunning

    val displayHours = effectiveSeconds / 3600
    val displayMinutes = (effectiveSeconds % 3600) / 60
    val displaySeconds = effectiveSeconds % 60
    val timeFormatted = if (displayHours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", displayHours, displayMinutes, displaySeconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", displayMinutes, displaySeconds)
    }

    var doNotDisturbEnabled by remember { mutableStateOf(false) }
    var appLockEnabled by remember { mutableStateOf(false) }
    var showDailyMotivationDialog by remember { mutableStateOf(false) }

    val finishTimeStr = remember(effectiveSeconds) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, effectiveSeconds)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "المؤقت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAudioSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                        }
                        IconButton(onClick = { showDailyMotivationDialog = true }) {
                            Icon(Icons.Default.Spa, contentDescription = "تحفيز وتركيز", tint = Color(0xFF10B981))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomTimerDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "تخصيص الوقت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Active Alarm & Finish Alert Banner
            AnimatedVisibility(
                visible = isAlarmRinging,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏰ رنين تنبيه المؤقت!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alarmMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.95f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { stopAlarmAudio() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إيقاف")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Background Persistence Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isTimerActive) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isTimerActive) Color(0xFF10B981) else Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isTimerActive) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isTimerActive) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTimerActive) "المؤقت يعمل في الخلفية بأمان حتى لو أغلقت التطبيق أو الشاشة 🔔" else "ميزة الخلفية مفعلة: استمرار العد والتنبيه بالصوت والاهتزاز حتى مع قفل الهاتف",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Circular Dial Timer matching Image 2
            ClockDialTimer(
                timeText = timeFormatted,
                finishTimeText = finishTimeStr,
                progress = if (effectiveTotalSeconds > 0) 1f - (effectiveSeconds.toFloat() / effectiveTotalSeconds.toFloat()) else 0f
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Explicit Start, Pause, and Stop Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Start / Resume Button (Green / Turquoise)
                Button(
                    onClick = {
                        stopAlarmAudio()
                        com.example.domain.pomodoro.PomodoroService.dismissAlarm(context)
                        if (!isTimerActive && serviceSeconds in 1 until serviceTotalDuration) {
                            com.example.domain.pomodoro.PomodoroService.resumeTimer(context)
                        } else {
                            com.example.domain.pomodoro.PomodoroService.startTimer(
                                context = context,
                                durationSeconds = totalDurationSeconds,
                                mode = currentMode,
                                subject = if (currentMode == "FOCUS") "جلسة تركيز ومذاكرة" else "استراحة بومودورو"
                            )
                        }
                    },
                    enabled = !isTimerActive,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00CBA7),
                        disabledContainerColor = Color(0xFF00CBA7).copy(alpha = 0.35f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (serviceSeconds in 1 until serviceTotalDuration && !isTimerActive) "استئناف ▶" else "تشغيل ▶",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // 2. Pause Button (Amber / Warning)
                Button(
                    onClick = {
                        com.example.domain.pomodoro.PomodoroService.pauseTimer(context)
                    },
                    enabled = isTimerActive,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        disabledContainerColor = Color(0xFFF59E0B).copy(alpha = 0.35f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إيقاف مؤقت ⏸",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // 3. Stop Button (Red / Error)
                Button(
                    onClick = {
                        com.example.domain.pomodoro.PomodoroService.stopTimer(context)
                        stopAlarmAudio()
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إيقاف ⏹",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Focus Mode Cards matching Image 2
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Card 1: Do Not Disturb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = doNotDisturbEnabled,
                            onCheckedChange = {
                                doNotDisturbEnabled = it
                                Toast.makeText(context, if (it) "تم تفعيل كتم الإشعارات 🔕" else "تم إيقاف كتم الإشعارات", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "عدم الإزعاج",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "كتم الإشعارات أثناء الدراسة",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6366F1),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NightlightRound,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Card 2: App Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = {
                                appLockEnabled = it
                                Toast.makeText(context, if (it) "تم تفعيل قفل التطبيقات 🔒" else "تم إلغاء قفل التطبيقات", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "قفل التطبيقات",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "اختر التطبيقات التي تريد قفلها",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Preset / Mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = currentMode == "FOCUS",
                    onClick = {
                        currentMode = "FOCUS"
                        timerSeconds = focusMinutes * 60
                        isTimerRunning = false
                        stopAlarmAudio()
                    },
                    label = { Text("جلسة دراسة (25د)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = currentMode == "SHORT_BREAK",
                    onClick = {
                        currentMode = "SHORT_BREAK"
                        timerSeconds = breakMinutes * 60
                        isTimerRunning = false
                        stopAlarmAudio()
                    },
                    label = { Text("استراحة (5د)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Controls & Sound Settings Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎵 إعدادات التنبيهات المخصصة والأوقات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Row {
                            IconButton(onClick = { showAudioSettingsDialog = true }) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = "تخصيص نغمات التنبيه", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showCustomTimerDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل مدة العمل والراحة", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showPresetDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة قالب جديد", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Standard Presets Row
                    Text(
                        text = "القوالب الدراسية السريعة:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1 Minute quick timer
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 1
                                    focusSeconds = 0
                                    breakMinutes = 1
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 60, currentMode)
                                },
                                label = { Text("⚡ 1 دقيقة") }
                            )
                        }

                        // 3 Minutes quick timer
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 3
                                    focusSeconds = 0
                                    breakMinutes = 1
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 3 * 60, currentMode)
                                },
                                label = { Text("⏱️ 3 دقائق") }
                            )
                        }

                        // 5 Minutes quick timer
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 5
                                    focusSeconds = 0
                                    breakMinutes = 2
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 5 * 60, currentMode)
                                },
                                label = { Text("☕ 5 دقائق") }
                            )
                        }

                        // 6 Minutes quick timer
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 6
                                    focusSeconds = 0
                                    breakMinutes = 2
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 6 * 60, currentMode)
                                },
                                label = { Text("🎯 6 دقائق") }
                            )
                        }

                        // Standard Pomodoro 25/5
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 25
                                    focusSeconds = 0
                                    breakMinutes = 5
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 25 * 60, currentMode)
                                },
                                label = { Text("🍅 بومودورو قياسي (25د / 5د)") },
                                icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        // Intensive Study 50/10
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 50
                                    focusSeconds = 0
                                    breakMinutes = 10
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 50 * 60, currentMode)
                                },
                                label = { Text("⚡ دراسة مكثفة (50د / 10د)") },
                                icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        // 1 Hour
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 60
                                    focusSeconds = 0
                                    breakMinutes = 10
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 60 * 60, currentMode)
                                },
                                label = { Text("🕐 1 ساعة (60د)") }
                            )
                        }

                        // 2 Hours
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 120
                                    focusSeconds = 0
                                    breakMinutes = 15
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 120 * 60, currentMode)
                                },
                                label = { Text("🕑 2 ساعة (120د)") }
                            )
                        }

                        // 3 Hours
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 180
                                    focusSeconds = 0
                                    breakMinutes = 20
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 180 * 60, currentMode)
                                },
                                label = { Text("🕒 3 ساعات (180د)") }
                            )
                        }

                        // 4 Hours
                        item {
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = 240
                                    focusSeconds = 0
                                    breakMinutes = 30
                                    breakSeconds = 0
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, 240 * 60, currentMode)
                                },
                                label = { Text("🕓 4 ساعات (240د)") }
                            )
                        }

                        items(presets) { preset ->
                            SuggestionChip(
                                onClick = {
                                    focusMinutes = preset.focusMinutes
                                    focusSeconds = preset.focusSeconds
                                    breakMinutes = preset.shortBreakMinutes
                                    breakSeconds = preset.shortBreakSeconds
                                    isTimerRunning = false
                                    stopAlarmAudio()
                                    val sec = (preset.focusMinutes * 60) + preset.focusSeconds
                                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, sec, currentMode)
                                },
                                label = { Text(preset.title) },
                                icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Test Alert Buttons (Work Alert & Break Alert)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val msg = "تجربة تنبيه انتهاء فترة العمل والتركيز (25 دقيقة) 🎉"
                        alarmMessage = msg
                        isAlarmRinging = true
                        playAudioAlert(finishSound, isAlarm = true)
                        sendSystemNotification("تجربة تنبيه انتهاء العمل 🍅", msg, NOTIFICATION_ID_FOCUS)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("🔊 تجربة تنبيه العمل", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        val msg = "تجربة تنبيه انتهاء فترة الاستراحة (5 دقائق) ☕"
                        alarmMessage = msg
                        isAlarmRinging = true
                        playAudioAlert(breakSound, isAlarm = true)
                        sendSystemNotification("تجربة تنبيه انتهاء الاستراحة ⚡", msg, NOTIFICATION_ID_BREAK)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("🔊 تجربة تنبيه الراحة", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog: Daily Motivation Popup (Matches Image 8)
    if (showDailyMotivationDialog) {
        AlertDialog(
            onDismissRequest = { showDailyMotivationDialog = false },
            title = {
                Text(
                    text = "📖 رسالة اليوم | نظمتهالك",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Open Red Envelope with Heart popping out (Matches Image 3)
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.daily_letter_heart_1788286613761),
                            contentDescription = "رسالة اليوم",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quote Text matching Image 3
                    Text(
                        text = "لن يخذلك الله وأنت تقول في قلبك إن الله معي",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Red Badge: "🎯 رحلة 40 يوم"
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEF4444)
                    ) {
                        Text(
                            text = "🎯 رحلة 40 يوم",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Green Button: "شارك الرسالة 🔗"
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "📖 رسالة اليوم | نظمتهالك:\n« لن يخذلك الله وأنت تقول في قلبك إن الله معي »\n🎯 رحلة 40 يوم للنجاح والتفوق!"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة رسالة اليوم"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        )
                    ) {
                        Text(
                            text = "شارك الرسالة 🔗",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dark Button: "إغلاق"
                    Button(
                        onClick = { showDailyMotivationDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B)
                        )
                    ) {
                        Text(
                            text = "إغلاق",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle text matching Image 3
                    Text(
                        text = "هيجيلك إشعار كل يوم الساعة 5:37 ص",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // Dialog 1: Custom Time Picker
    if (showCustomTimerDialog) {
        var focusHoursStr by remember { mutableStateOf((focusMinutes / 60).toString()) }
        var focusMinStr by remember { mutableStateOf((focusMinutes % 60).toString()) }
        var focusSecStr by remember { mutableStateOf(focusSeconds.toString()) }
        var breakMinStr by remember { mutableStateOf(breakMinutes.toString()) }
        var breakSecStr by remember { mutableStateOf(breakSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ضبط وتخصيص وقت المؤقت بدقة")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("اختيار سريع للمدة (دقائق وساعات):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            1 to "1 دقيقة",
                            3 to "3 دقائق",
                            5 to "5 دقائق",
                            6 to "6 دقائق",
                            15 to "15 دقيقة",
                            25 to "25 دقيقة",
                            50 to "50 دقيقة",
                            60 to "1 ساعة",
                            120 to "2 ساعة",
                            180 to "3 ساعات",
                            240 to "4 ساعات"
                        ).forEach { (m, label) ->
                            item {
                                FilterChip(
                                    selected = (focusHoursStr.toIntOrNull() ?: 0) * 60 + (focusMinStr.toIntOrNull() ?: 0) == m,
                                    onClick = {
                                        focusHoursStr = (m / 60).toString()
                                        focusMinStr = (m % 60).toString()
                                        focusSecStr = "0"
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("مدة فترة التركيز والعمل (Focus):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = focusHoursStr,
                            onValueChange = { focusHoursStr = it },
                            label = { Text("ساعات (0..12)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = focusMinStr,
                            onValueChange = { focusMinStr = it },
                            label = { Text("دقائق (0..59)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = focusSecStr,
                            onValueChange = { focusSecStr = it },
                            label = { Text("ثواني", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Text("مدة فترة الاستراحة القصيرة (Break):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = breakMinStr,
                            onValueChange = { breakMinStr = it },
                            label = { Text("دقائق (الافتراضي 5)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = breakSecStr,
                            onValueChange = { breakSecStr = it },
                            label = { Text("ثواني") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val hrs = focusHoursStr.toIntOrNull() ?: 0
                    val mins = focusMinStr.toIntOrNull() ?: 0
                    val secs = focusSecStr.toIntOrNull() ?: 0
                    val totalMins = (hrs * 60) + mins
                    focusMinutes = if (totalMins > 0 || secs > 0) totalMins else 25
                    focusSeconds = secs
                    breakMinutes = breakMinStr.toIntOrNull() ?: 5
                    breakSeconds = breakSecStr.toIntOrNull() ?: 0
                    isTimerRunning = false
                    stopAlarmAudio()
                    val newTotalSec = (focusMinutes * 60) + focusSeconds
                    com.example.domain.pomodoro.PomodoroService.setTimerDuration(context, newTotalSec, currentMode)
                    showCustomTimerDialog = false
                }) {
                    Text("حفظ وتطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimerDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dialog 2: Add Preset
    if (showPresetDialog) {
        var presetTitle by remember { mutableStateOf("") }
        var pFocusMin by remember { mutableStateOf("25") }
        var pBreakMin by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("إضافة قالب دراسي جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = presetTitle,
                        onValueChange = { presetTitle = it },
                        label = { Text("اسم القالب (مثال: حفظ قرآن، مراجعة رياضيات)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pFocusMin,
                        onValueChange = { pFocusMin = it },
                        label = { Text("مدة التركيز (دقيقة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pBreakMin,
                        onValueChange = { pBreakMin = it },
                        label = { Text("مدة الاستراحة (دقيقة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (presetTitle.isNotBlank()) {
                        viewModel.addPomodoroPreset(
                            title = presetTitle,
                            focusMin = pFocusMin.toIntOrNull() ?: 25,
                            focusSec = 0,
                            breakMin = pBreakMin.toIntOrNull() ?: 5,
                            breakSec = 0
                        )
                    }
                    showPresetDialog = false
                }) {
                    Text("إضافة القالب")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dialog 3: Dedicated Audio Alerts Customizer (Work finished tone + Break finished tone + Custom Audio)
    if (showAudioSettingsDialog) {
        val soundOptions = listOf(
            "DEFAULT_ALARM" to "⏰ نغمة منبه الموبايل الافتراضية (Alarm)",
            "BUILTIN_CHIME" to "🔔 نغمة الرنين الهادئة المدمجة (Chime)",
            "BUILTIN_BELL" to "🎵 نغمة الجرس الذكي المدمجة (Smart Bell)",
            "DEFAULT_NOTIFICATION" to "💬 نغمة الإشعارات القياسية للنظام",
            "DEFAULT_RINGTONE" to "🎶 نغمة الرنين والاتصال الأساسية"
        )

        AlertDialog(
            onDismissRequest = {
                stopAlarmAudio()
                showAudioSettingsDialog = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("تخصيص نغمات وتنبيهات بومودورو")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "1️⃣ نغمة انتهاء فترة العمل والتركيز (25 دقيقة):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Sound options for Work Finish
                    soundOptions.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    finishSound = key
                                    playAudioAlert(key, isAlarm = true)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = finishSound == key,
                                onClick = {
                                    finishSound = key
                                    playAudioAlert(key, isAlarm = true)
                                }
                            )
                            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            IconButton(onClick = { playAudioAlert(key, isAlarm = true) }) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "معاينة الصوت", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Custom audio file from storage for work finish
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "اختيار ملف صوتي مخصص من الهاتف 📁",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    try {
                                        audioPickerLauncher.launch("audio/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "تعذر فتح منتقي الملفات", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحديد نغمة من الهاتف (MP3/WAV)", fontSize = 12.sp)
                            }

                            if (customSoundUri.isNotBlank() && (customSoundUri.startsWith("content://") || customSoundUri.startsWith("file://"))) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = finishSound == customSoundUri,
                                        onClick = {
                                            finishSound = customSoundUri
                                            playAudioAlert(customSoundUri, isAlarm = true)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customSoundName.ifBlank { "نغمة مخصصة من الهاتف" },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { playAudioAlert(customSoundUri, isAlarm = true) }) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "معاينة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "2️⃣ نغمة انتهاء فترة الاستراحة والعودة للتركيز (5 دقائق):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF10B981)
                    )

                    soundOptions.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    breakSound = key
                                    playAudioAlert(key, isAlarm = true)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = breakSound == key,
                                onClick = {
                                    breakSound = key
                                    playAudioAlert(key, isAlarm = true)
                                }
                            )
                            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            IconButton(onClick = { playAudioAlert(key, isAlarm = true) }) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "معاينة الصوت", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "3️⃣ نغمة بدء الجلسة:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    soundOptions.take(3).forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    startSound = key
                                    playAudioAlert(key, isAlarm = false)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = startSound == key,
                                onClick = {
                                    startSound = key
                                    playAudioAlert(key, isAlarm = false)
                                }
                            )
                            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            IconButton(onClick = { playAudioAlert(key, isAlarm = false) }) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "معاينة الصوت", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    stopAlarmAudio()
                    try {
                        pomodoroSharedPrefs.edit()
                            .putString("pomodoro_start_sound", startSound)
                            .putString("pomodoro_finish_sound", finishSound)
                            .putString("pomodoro_break_sound", breakSound)
                            .putString("pomodoro_finish_sound_name", customSoundName)
                            .apply()
                    } catch (_: Exception) {}

                    viewModel.updatePomodoroSounds(startSound, finishSound, breakSound, customSoundName)
                    Toast.makeText(context, "تم حفظ إعدادات النغمات بنجاح ✅", Toast.LENGTH_SHORT).show()
                    showAudioSettingsDialog = false
                }) {
                    Text("حفظ التنبيهات")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    stopAlarmAudio()
                    showAudioSettingsDialog = false
                }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ClockDialTimer(
    timeText: String,
    finishTimeText: String,
    progress: Float = 0f
) {
    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f - 6.dp.toPx()
            if (radius <= 0f) return@Canvas

            val totalSweepAngle = 270f
            val startAngle = 135f

            // 1. Background Arc Track (inactive part)
            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = startAngle,
                sweepAngle = totalSweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // 2. Dynamic Progress Gradient Arc
            val activeSweep = (totalSweepAngle * progress.coerceIn(0f, 1f))
            if (activeSweep > 0.5f) {
                val arcBrush = androidx.compose.ui.graphics.Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF38BDF8),
                        Color(0xFF6366F1),
                        Color(0xFFA855F7),
                        Color(0xFF38BDF8)
                    ),
                    center = center
                )

                drawArc(
                    brush = arcBrush,
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }

            // 3. Circular Handle Knob at exact current position on Arc
            val knobAngle = Math.toRadians((startAngle + activeSweep).toDouble())
            val knobX = center.x + radius * kotlin.math.cos(knobAngle).toFloat()
            val knobY = center.y + radius * kotlin.math.sin(knobAngle).toFloat()

            // Outer glow / shadow circle
            drawCircle(
                color = Color(0x3338BDF8),
                radius = 15.dp.toPx(),
                center = Offset(knobX, knobY)
            )
            // Outer bright border
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 11.dp.toPx(),
                center = Offset(knobX, knobY),
                style = Stroke(width = 3.5.dp.toPx())
            )
            // Solid White center knob
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(knobX, knobY)
            )
        }

        // Inside Texts matching Image 2
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "وقت الدراسة",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeText,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ستنتهي عند الساعة",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = finishTimeText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }
    }
}

