package com.example.ui.prayer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.audio.AppAudioManager
import com.example.data.audio.AzanAudioPlayer
import com.example.domain.prayer.AdhanPlaybackService
import com.example.domain.prayer.AdhkarAutoTriggerManager
import com.example.domain.prayer.AdhkarWindow
import com.example.ui.MainViewModel
import com.example.ui.screens.QiblaCompassScreen
import com.example.ui.theme.RafiqTheme

class AdhanFullScreenActivity : ComponentActivity() {

    private var azanPlayer: AzanAudioPlayer? = null

    companion object {
        const val EXTRA_PRAYER_NAME = "EXTRA_PRAYER_NAME"
        const val EXTRA_CITY_NAME = "EXTRA_CITY_NAME"
        const val EXTRA_MUEZZIN = "EXTRA_MUEZZIN"
        const val EXTRA_AUTO_PLAY = "EXTRA_AUTO_PLAY"
        const val EXTRA_SCHEDULED_TIME_MS = "EXTRA_SCHEDULED_TIME_MS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        runCatching { enableEdgeToEdge() }

        // Allow showing over lockscreen & turn screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val scheduledTimeMs = intent.getLongExtra(EXTRA_SCHEDULED_TIME_MS, 0L)
        val now = System.currentTimeMillis()
        if (scheduledTimeMs > 0L && kotlin.math.abs(now - scheduledTimeMs) > 15 * 60 * 1000L) {
            finish()
            return
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الفجر"
        val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "القاهرة"
        val muezzinVoice = intent.getStringExtra(EXTRA_MUEZZIN) ?: "الأذان المصري الأصيل (MBC وإذاعة القرآن)"
        val autoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, true)

        azanPlayer = AzanAudioPlayer(this)
        if (autoPlay && !AdhanPlaybackService.isPlayingAdhan) {
            azanPlayer?.playMuezzin(
                muezzinId = muezzinVoice,
                muezzinName = muezzinVoice,
                useAlarmAttributes = true,
                smoothFadeIn = true
            )
        }

        val stopAllAdhan = {
            AdhanPlaybackService.stop(this)
            azanPlayer?.stopAudio()
            AppAudioManager.stopAllNonAdhanAudio(this)
        }

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            var showQiblaModal by remember { mutableStateOf(false) }

            BackHandler(enabled = showQiblaModal) {
                showQiblaModal = false
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RafiqTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RealisticAnimatedAdhanScreen(
                            prayerName = prayerName,
                            cityName = cityName,
                            muezzinVoice = muezzinVoice,
                            onOpenQibla = {
                                stopAllAdhan()
                                showQiblaModal = true
                            },
                            onOpenNearbyMosques = {
                                stopAllAdhan()
                            },
                            onOpenMorningAdhkar = {
                                stopAllAdhan()
                                val adhkarIntent = Intent(this@AdhanFullScreenActivity, AthkarPopupActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_MORNING)
                                }
                                startActivity(adhkarIntent)
                                finish()
                            },
                            onStopAdhan = {
                                stopAllAdhan()
                                val norm = prayerName.trim().lowercase()
                                val activeWindow = AdhkarAutoTriggerManager.detectCurrentWindowForContext(this@AdhanFullScreenActivity)
                                when {
                                    (norm.contains("فجر") || norm.contains("fajr")) && activeWindow == AdhkarWindow.MORNING -> {
                                        val morningIntent = Intent(this@AdhanFullScreenActivity, AthkarPopupActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_MORNING)
                                        }
                                        startActivity(morningIntent)
                                    }
                                    (norm.contains("عصر") || norm.contains("asr")) && activeWindow == AdhkarWindow.EVENING -> {
                                        val eveningIntent = Intent(this@AdhanFullScreenActivity, AthkarPopupActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_EVENING)
                                        }
                                        startActivity(eveningIntent)
                                    }
                                }
                                finish()
                            }
                        )

                        // Instant In-Place Live Qibla Compass Modal Sheet (Zero SplashScreen / Zero App Restart!)
                        AnimatedVisibility(
                            visible = showQiblaModal,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            QiblaCompassScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = {
                                    showQiblaModal = false
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            AdhanPlaybackService.stop(this)
            azanPlayer?.stopAudio()
            AppAudioManager.stopAllNonAdhanAudio(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        azanPlayer?.stopAudio()
    }
}

fun getHijriMonthName(month: Int): String {
    return when (month) {
        0 -> "محرم"
        1 -> "صفر"
        2 -> "ربيع الأول"
        3 -> "ربيع الآخر"
        4 -> "جمادى الأولى"
        5 -> "جمادى الآخرة"
        6 -> "رجب"
        7 -> "شعبان"
        8 -> "رمضان"
        9 -> "شوال"
        10 -> "ذو القعدة"
        11 -> "ذو الحجة"
        else -> ""
    }
}
