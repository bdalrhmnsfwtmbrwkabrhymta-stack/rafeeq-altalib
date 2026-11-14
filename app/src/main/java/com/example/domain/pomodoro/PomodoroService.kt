package com.example.domain.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PomodoroService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null

    companion object {
        const val TAG = "PomodoroService"
        const val CHANNEL_ID = "pomodoro_focus_channel"
        const val ALARM_CHANNEL_ID = "pomodoro_alarm_channel"
        // Unique IDs across the entire app namespace (Prayer=2001, AzkarAudio=2002, Salawat=2003, Adhan=2004)
        const val NOTIFICATION_ID = 2101
        const val ALARM_NOTIFICATION_ID = 2102

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SET_DURATION = "ACTION_SET_DURATION"
        const val ACTION_DISMISS_ALARM = "ACTION_DISMISS_ALARM"

        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_SUBJECT_NAME = "EXTRA_SUBJECT_NAME"

        private val _secondsRemaining = MutableStateFlow(25 * 60)
        val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

        private val _totalDurationSeconds = MutableStateFlow(25 * 60)
        val totalDurationSeconds: StateFlow<Int> = _totalDurationSeconds.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentMode = MutableStateFlow("FOCUS") // FOCUS, SHORT_BREAK, LONG_BREAK
        val currentMode: StateFlow<String> = _currentMode.asStateFlow()

        private val _currentSubjectName = MutableStateFlow("مذاكرة عامة")
        val currentSubjectName: StateFlow<String> = _currentSubjectName.asStateFlow()

        private val _isAlarmRinging = MutableStateFlow(false)
        val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

        private val _sessionCompletedEvent = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
        val sessionCompletedEvent: SharedFlow<Pair<String, Int>> = _sessionCompletedEvent.asSharedFlow()

        private val companionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var inProcessTimerJob: Job? = null

        fun setTimerDuration(context: Context, durationSeconds: Int, mode: String = "FOCUS", subject: String = "جلسة تركيز") {
            val validDuration = durationSeconds.coerceAtLeast(1)
            _isRunning.value = false
            _isAlarmRinging.value = false
            inProcessTimerJob?.cancel()
            _totalDurationSeconds.value = validDuration
            _secondsRemaining.value = validDuration
            _currentMode.value = mode
            _currentSubjectName.value = subject

            try {
                val intent = Intent(context, PomodoroService::class.java).apply {
                    action = ACTION_SET_DURATION
                    putExtra(EXTRA_DURATION_SECONDS, validDuration)
                    putExtra(EXTRA_MODE, mode)
                    putExtra(EXTRA_SUBJECT_NAME, subject)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "setTimerDuration error", e)
            }
        }

        fun startTimer(context: Context, durationSeconds: Int, mode: String = "FOCUS", subject: String = "جلسة تركيز") {
            val validDuration = durationSeconds.coerceAtLeast(1)
            _totalDurationSeconds.value = validDuration
            _secondsRemaining.value = validDuration
            _currentMode.value = mode
            _currentSubjectName.value = subject
            _isRunning.value = true
            _isAlarmRinging.value = false

            // Start sound feedback
            playStartSoundFeedback(context)

            // Start in-process countdown immediately so UI ticks without waiting for service IPC
            startInProcessCountdown(context)

            try {
                val intent = Intent(context, PomodoroService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_DURATION_SECONDS, validDuration)
                    putExtra(EXTRA_MODE, mode)
                    putExtra(EXTRA_SUBJECT_NAME, subject)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "startTimer error, attempting normal startService", e)
                try {
                    val intent = Intent(context, PomodoroService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_DURATION_SECONDS, validDuration)
                        putExtra(EXTRA_MODE, mode)
                        putExtra(EXTRA_SUBJECT_NAME, subject)
                    }
                    context.startService(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to start service, continuing in-process", e2)
                }
            }
        }

        fun pauseTimer(context: Context) {
            _isRunning.value = false
            inProcessTimerJob?.cancel()
            try {
                val intent = Intent(context, PomodoroService::class.java).apply {
                    action = ACTION_PAUSE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "pauseTimer error", e)
            }
        }

        fun resumeTimer(context: Context) {
            if (_secondsRemaining.value > 0) {
                _isRunning.value = true
                playStartSoundFeedback(context)
                startInProcessCountdown(context)
                try {
                    val intent = Intent(context, PomodoroService::class.java).apply {
                        action = ACTION_RESUME
                    }
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "resumeTimer error", e)
                    try {
                        val intent = Intent(context, PomodoroService::class.java).apply {
                            action = ACTION_RESUME
                        }
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
            }
        }

        fun stopTimer(context: Context) {
            _isRunning.value = false
            _isAlarmRinging.value = false
            inProcessTimerJob?.cancel()
            _secondsRemaining.value = _totalDurationSeconds.value
            try {
                val intent = Intent(context, PomodoroService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "stopTimer error", e)
            }
        }

        fun dismissAlarm(context: Context) {
            _isAlarmRinging.value = false
            try {
                val intent = Intent(context, PomodoroService::class.java).apply {
                    action = ACTION_DISMISS_ALARM
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "dismissAlarm error", e)
            }
        }

        private fun playStartSoundFeedback(context: Context) {
            companionScope.launch(Dispatchers.IO) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                } catch (_: Exception) {}
            }
        }

        private fun startInProcessCountdown(context: Context) {
            inProcessTimerJob?.cancel()
            inProcessTimerJob = companionScope.launch {
                while (_isRunning.value && _secondsRemaining.value > 0) {
                    delay(1000L)
                    if (!_isRunning.value) break
                    val current = _secondsRemaining.value
                    if (current > 0) {
                        _secondsRemaining.value = current - 1
                    }
                    if (_secondsRemaining.value <= 0) {
                        _isRunning.value = false
                        _isAlarmRinging.value = true
                        _sessionCompletedEvent.tryEmit(_currentMode.value to (_totalDurationSeconds.value / 60))
                        break
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "PomodoroService received action: $action")

        startInForeground()

        when (action) {
            ACTION_SET_DURATION -> {
                stopAlarmAudio()
                timerJob?.cancel()
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60).coerceAtLeast(1)
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "FOCUS"
                val subject = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "جلسة تركيز"

                _totalDurationSeconds.value = duration
                _secondsRemaining.value = duration
                _currentMode.value = mode
                _currentSubjectName.value = subject
                _isRunning.value = false
                _isAlarmRinging.value = false
                updateForegroundNotification()
            }
            ACTION_START -> {
                stopAlarmAudio()
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60).coerceAtLeast(1)
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "FOCUS"
                val subject = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "جلسة تركيز"

                _totalDurationSeconds.value = duration
                _secondsRemaining.value = duration
                _currentMode.value = mode
                _currentSubjectName.value = subject
                _isRunning.value = true
                _isAlarmRinging.value = false

                startCountdownLoop()
            }
            ACTION_PAUSE -> {
                _isRunning.value = false
                timerJob?.cancel()
                updateForegroundNotification()
            }
            ACTION_RESUME -> {
                if (_secondsRemaining.value > 0) {
                    _isRunning.value = true
                    startCountdownLoop()
                }
            }
            ACTION_STOP -> {
                _isRunning.value = false
                _isAlarmRinging.value = false
                timerJob?.cancel()
                stopAlarmAudio()
                _secondsRemaining.value = _totalDurationSeconds.value
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
            }
            ACTION_DISMISS_ALARM -> {
                _isAlarmRinging.value = false
                stopAlarmAudio()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(ALARM_NOTIFICATION_ID)
                if (!_isRunning.value) {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        try {
            val notification = buildOngoingNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e1: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground", e)
        }
    }

    private fun startCountdownLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isRunning.value && _secondsRemaining.value > 0) {
                delay(1000L)
                if (!_isRunning.value) break
                val current = _secondsRemaining.value
                if (current > 0) {
                    val next = current - 1
                    _secondsRemaining.value = next
                    if (next % 5 == 0 || next == 0) {
                        updateForegroundNotification()
                    }
                }
                if (_secondsRemaining.value <= 0) {
                    _isRunning.value = false
                    updateForegroundNotification()
                    onTimerFinished()
                    break
                }
            }
        }
    }

    private fun onTimerFinished() {
        Log.i(TAG, "Pomodoro timer finished for mode ${_currentMode.value}")
        _isAlarmRinging.value = true
        val completedMode = _currentMode.value
        val completedMinutes = _totalDurationSeconds.value / 60
        _sessionCompletedEvent.tryEmit(completedMode to completedMinutes)

        // 1. Play sound
        playAlarmAudio()

        // 2. Vibrate
        vibrateAlarm()

        // 3. Post Completion Notification
        postFinishedNotification()
    }

    private fun playAlarmAudio() {
        stopAlarmAudio()
        try {
            val prefs = getSharedPreferences("pomodoro_audio_prefs", Context.MODE_PRIVATE)
            val soundKey = if (_currentMode.value == "FOCUS") {
                prefs.getString("pomodoro_finish_sound", "DEFAULT_ALARM") ?: "DEFAULT_ALARM"
            } else {
                prefs.getString("pomodoro_break_sound", "BUILTIN_BELL") ?: "BUILTIN_BELL"
            }

            var played = false

            // Case 1: Custom audio URI
            if (soundKey.startsWith("content://") || soundKey.startsWith("file://")) {
                try {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(applicationContext, Uri.parse(soundKey))
                        isLooping = true
                        prepare()
                        start()
                    }
                    activeMediaPlayer = mp
                    played = true
                } catch (e: Exception) {
                    Log.e(TAG, "Custom audio playback failed", e)
                }
            }

            // Case 2: Built-in tone
            if (!played && (soundKey == "BUILTIN_CHIME" || soundKey == "BUILTIN_BELL" || soundKey == "BUILTIN_BEEP")) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    val toneType = when (soundKey) {
                        "BUILTIN_BELL" -> ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE
                        "BUILTIN_CHIME" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                        else -> ToneGenerator.TONE_PROP_BEEP2
                    }
                    toneGen.startTone(toneType, 4000)
                    played = true
                } catch (e: Exception) {
                    Log.e(TAG, "ToneGenerator failed", e)
                }
            }

            // Case 3: System Ringtone
            if (!played) {
                var soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (soundUri == null) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                if (soundUri == null) {
                    soundUri = Settings.System.DEFAULT_ALARM_ALERT_URI
                }
                soundUri?.let { uri ->
                    val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    ringtone?.play()
                    activeRingtone = ringtone
                    played = true
                }
            }

            if (!played) {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone", e)
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
            } catch (_: Exception) {}
        }
    }

    private fun vibrateAlarm() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 600, 300, 600, 300, 600), -1)
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmAudio() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {}
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun updateForegroundNotification() {
        try {
            val notification = buildOngoingNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Ignore non-fatal notification update errors
        }
    }

    private fun formatTime(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) {
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val remaining = _secondsRemaining.value
        val formattedTime = formatTime(remaining)
        val modeTitle = when (_currentMode.value) {
            "FOCUS" -> "🎯 جلسة تركيز ومذاكرة"
            "SHORT_BREAK" -> "☕ استراحة قصيرة"
            "LONG_BREAK" -> "🌟 استراحة طويلة"
            else -> "⏳ مؤقت المذاكرة"
        }
        val subject = _currentSubjectName.value
        val isRunningNow = _isRunning.value

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "pomodoro")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume Action
        val toggleActionIntent = Intent(this, PomodoroService::class.java).apply {
            action = if (isRunningNow) ACTION_PAUSE else ACTION_RESUME
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            202,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleTitle = if (isRunningNow) "⏸️ إيقاف مؤقت" else "▶️ استئناف"

        // Stop Action
        val stopActionIntent = Intent(this, PomodoroService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            203,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isRunningNow) "يعمل الآن" else "موقوف مؤقتاً"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$formattedTime | $modeTitle ($statusText)")
            .setContentText("المادة: $subject | رفيق الطالب والمذاكرة")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isRunningNow)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, toggleTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ إنهاء", stopPendingIntent)
            .build()
    }

    private fun postFinishedNotification() {
        val mode = _currentMode.value
        val (title, text) = if (mode == "FOCUS") {
            "🎉 أحسنت! انتهت جلسة التركيز والمذاكرة" to "لقد أتممت جلستك بنجاح! خذ الآن استراحة لتجديد نشاطك ☕"
        } else {
            "⏰ انتهت الاستراحة! حان وقت العودة للتركيز" to "استعد لبدء جلسة مذاكرة جديدة ومواصلة التفوق 🚀"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "pomodoro")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            204,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, PomodoroService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            205,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_notification_clear_all, "🔕 إيقاف التنبيه", dismissPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(ALARM_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val ongoingChannel = NotificationChannel(
                CHANNEL_ID,
                "مؤقت البومودورو أثناء العمل",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يعرض الوقت المتبقي لجلسة البومودورو النشطة"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(ongoingChannel)

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "تنبيه انتهاء البومودورو 🔔",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "يرن وينبه عند انتهاء فترات المذاكرة أو الاستراحة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            }
            manager?.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        stopAlarmAudio()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
