package com.example.studytools.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class StudyTimerMode(
    val titleAr: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val isCountUp: Boolean = false
) {
    POMODORO("بومودورو الكلاسيكي", 25, 5),
    DEEP_WORK("تركيز عميق 50/10", 50, 10),
    QUICK_SPRINT("مراجعة سريعة 15/3", 15, 3),
    ULTRA_FOCUS("ماراثون 90 دقيقة", 90, 15),
    STOPWATCH("ساعة إيقاف حرة", 0, 0, isCountUp = true)
}

enum class StudyTimerPhase(val labelAr: String) {
    FOCUS("جلسة تركيز"),
    BREAK("استراحة قصيرة")
}

data class StudyTimerSnapshot(
    val mode: StudyTimerMode = StudyTimerMode.POMODORO,
    val phase: StudyTimerPhase = StudyTimerPhase.FOCUS,
    val subjectLabel: String = "مذاكرة عامة",
    val totalTargetSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
    val completedCycles: Int = 0,
    val pendingCompletedMinutesToLog: Int = 0
)

object StudyTimerCoordinator {
    private val _snapshot = MutableStateFlow(StudyTimerSnapshot())
    val snapshot: StateFlow<StudyTimerSnapshot> = _snapshot.asStateFlow()

    fun selectMode(mode: StudyTimerMode) {
        val targetSec = if (mode.isCountUp) 0 else mode.focusMinutes * 60
        _snapshot.value = StudyTimerSnapshot(
            mode = mode,
            phase = StudyTimerPhase.FOCUS,
            subjectLabel = _snapshot.value.subjectLabel,
            totalTargetSeconds = targetSec,
            remainingSeconds = targetSec,
            elapsedSeconds = 0,
            isRunning = false,
            completedCycles = _snapshot.value.completedCycles,
            pendingCompletedMinutesToLog = _snapshot.value.pendingCompletedMinutesToLog
        )
    }

    fun setCustomMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        val targetSec = clamped * 60
        _snapshot.update {
            it.copy(
                phase = StudyTimerPhase.FOCUS,
                totalTargetSeconds = targetSec,
                remainingSeconds = targetSec,
                elapsedSeconds = 0,
                isRunning = false
            )
        }
    }

    fun setSubjectLabel(label: String) {
        _snapshot.update { it.copy(subjectLabel = label.ifBlank { "مذاكرة عامة" }) }
    }

    internal fun setRunning(running: Boolean) {
        _snapshot.update { it.copy(isRunning = running) }
    }

    internal fun tickSecond(): Boolean {
        var justFinishedPhase = false
        _snapshot.update { current ->
            if (!current.isRunning) return@update current
            if (current.mode.isCountUp) {
                current.copy(elapsedSeconds = current.elapsedSeconds + 1)
            } else {
                val nextRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                val nextElapsed = current.elapsedSeconds + 1
                if (nextRemaining == 0) {
                    justFinishedPhase = true
                    if (current.phase == StudyTimerPhase.FOCUS) {
                        val loggedMins = (current.totalTargetSeconds / 60).coerceAtLeast(1)
                        val breakSecs = current.mode.breakMinutes.coerceAtLeast(1) * 60
                        current.copy(
                            phase = StudyTimerPhase.BREAK,
                            totalTargetSeconds = breakSecs,
                            remainingSeconds = breakSecs,
                            elapsedSeconds = 0,
                            isRunning = false,
                            completedCycles = current.completedCycles + 1,
                            pendingCompletedMinutesToLog = current.pendingCompletedMinutesToLog + loggedMins
                        )
                    } else {
                        val focusSecs = current.mode.focusMinutes.coerceAtLeast(1) * 60
                        current.copy(
                            phase = StudyTimerPhase.FOCUS,
                            totalTargetSeconds = focusSecs,
                            remainingSeconds = focusSecs,
                            elapsedSeconds = 0,
                            isRunning = false
                        )
                    }
                } else {
                    current.copy(remainingSeconds = nextRemaining, elapsedSeconds = nextElapsed)
                }
            }
        }
        return justFinishedPhase
    }

    fun resetCurrentPhase() {
        _snapshot.update { current ->
            val resetSec = if (current.mode.isCountUp) 0 else current.totalTargetSeconds
            current.copy(
                remainingSeconds = resetSec,
                elapsedSeconds = 0,
                isRunning = false
            )
        }
    }

    fun finishStopwatchEarly(): Int {
        val current = _snapshot.value
        val mins = (current.elapsedSeconds / 60).coerceAtLeast(if (current.elapsedSeconds >= 30) 1 else 0)
        resetCurrentPhase()
        return mins
    }

    fun consumePendingLoggedMinutes(): Int {
        var result = 0
        _snapshot.update { current ->
            result = current.pendingCompletedMinutesToLog
            current.copy(pendingCompletedMinutesToLog = 0)
        }
        return result
    }
}

class StudyTimerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "study_tools_timer_channel"
        private const val NOTIFICATION_ID = 8042

        const val ACTION_START = "com.example.studytools.timer.START"
        const val ACTION_PAUSE = "com.example.studytools.timer.PAUSE"
        const val ACTION_STOP = "com.example.studytools.timer.STOP"

        fun startOrResume(context: Context) {
            val intent = Intent(context, StudyTimerForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, StudyTimerForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StudyTimerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                StudyTimerCoordinator.setRunning(false)
                tickerJob?.cancel()
                updateNotification()
            }
            ACTION_STOP -> {
                StudyTimerCoordinator.setRunning(false)
                tickerJob?.cancel()
                StudyTimerCoordinator.resetCurrentPhase()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                StudyTimerCoordinator.setRunning(true)
                startForegroundWithType(buildNotification())
                startTicker()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (StudyTimerCoordinator.snapshot.value.isRunning) {
                delay(1000L)
                if (!StudyTimerCoordinator.snapshot.value.isRunning) break
                val completedPhase = StudyTimerCoordinator.tickSecond()
                updateNotification()
                if (completedPhase) {
                    playCompletionTone()
                    break
                }
            }
        }
    }

    private fun playCompletionTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (_: Throwable) {
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = StudyTimerCoordinator.snapshot.value
        val displaySecs = if (state.mode.isCountUp) state.elapsedSeconds else state.remainingSeconds
        val mins = displaySecs / 60
        val secs = displaySecs % 60
        val timeText = String.format(Locale.US, "%02d:%02d", mins, secs)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "study_tools")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(this, StudyTimerForegroundService::class.java).apply {
            action = if (state.isRunning) ACTION_PAUSE else ACTION_START
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            102,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StudyTimerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusTitle = if (state.isRunning) {
            "${state.phase.labelAr} • $timeText"
        } else {
            "متوقف مؤقتاً (${state.phase.labelAr}) • $timeText"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(statusTitle)
            .setContentText("${state.subjectLabel} — ${state.mode.titleAr}")
            .setOngoing(state.isRunning)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                if (state.isRunning) "إيقاف مؤقت" else "استكمال",
                togglePendingIntent
            )
            .addAction(0, "إنهاء", stopPendingIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "مؤقت المذاكرة العميقة",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "يعرض العد التنازلي لجلسات التركيز والمراجعة أثناء استخدام التطبيقات الأخرى"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
