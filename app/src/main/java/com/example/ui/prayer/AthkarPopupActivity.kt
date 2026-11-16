package com.example.ui.prayer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.domain.prayer.AzkarAudioService
import com.example.domain.prayer.SalawatPlaybackService
import com.example.ui.theme.RafiqTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Interactive Full-Screen Dialog for Morning and Evening Athkar.
 * - Starts reciting audio IMMEDIATELY upon appearance.
 * - Displays smart battery-saving timer (auto-dismisses screen after 2 minutes to conserve battery).
 * - Leaves background audio reciting peacefully while screen sleeps.
 * - Provides prominent Stop and Read controls.
 */
class AthkarPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        // Wake screen and show over lock screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val athkarType = intent.getStringExtra(EXTRA_ATHKAR_TYPE) ?: TYPE_MORNING

        // 1. Immediately launch audio playback automatically
        startAthkarAudio(athkarType)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RafiqTheme {
                    AthkarPopupContent(
                        athkarType = athkarType,
                        onStopAudioAndClose = {
                            stopAthkarAudio(athkarType)
                            dismissScreen()
                        },
                        onDismissScreenOnly = {
                            // Close screen to preserve battery; audio keeps reciting in background
                            dismissScreen()
                        },
                        onMuteAudioAndRead = {
                            stopAthkarAudio(athkarType)
                        },
                        onOpenFullPrayerScreen = {
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("NAVIGATE_TO", "prayer")
                            }
                            startActivity(mainIntent)
                            dismissScreen()
                        }
                    )
                }
            }
        }
    }

    private fun startAthkarAudio(athkarType: String) {
        when (athkarType) {
            TYPE_MORNING -> {
                AzkarAudioService.startMorning(this)
            }
            TYPE_EVENING -> {
                AzkarAudioService.startEvening(this)
            }
            TYPE_SALAWAT -> {
                val appContext = applicationContext
                var voiceId = 1
                var audioMode = "VOICE_FORMULA"
                var customUri = ""
                try {
                    kotlinx.coroutines.runBlocking {
                        val repo = com.example.data.preferences.UserPreferencesRepository(appContext)
                        val settings = repo.userSettingsFlow.first()
                        voiceId = settings.salawatVoiceId
                        audioMode = settings.salawatAudioMode
                        customUri = settings.customSalawatAudioUri
                    }
                } catch (_: Exception) {}
                SalawatPlaybackService.start(this, voiceId, audioMode, customUri)
            }
        }
    }

    private fun stopAthkarAudio(athkarType: String) {
        if (athkarType == TYPE_MORNING || athkarType == TYPE_EVENING) {
            AzkarAudioService.stop(this)
        } else if (athkarType == TYPE_SALAWAT) {
            SalawatPlaybackService.stop(this)
        }
    }

    private fun dismissScreen() {
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        finish()
    }

    companion object {
        const val EXTRA_ATHKAR_TYPE = "extra_athkar_type"
        const val TYPE_MORNING = "morning"
        const val TYPE_EVENING = "evening"
        const val TYPE_SALAWAT = "salawat"
    }
}

@Composable
fun AthkarPopupContent(
    athkarType: String,
    onStopAudioAndClose: () -> Unit,
    onDismissScreenOnly: () -> Unit,
    onMuteAudioAndRead: () -> Unit,
    onOpenFullPrayerScreen: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isMorning = athkarType == AthkarPopupActivity.TYPE_MORNING
    val isSalawat = athkarType == AthkarPopupActivity.TYPE_SALAWAT

    // Auto-dismiss countdown timer (2 minutes / 120 seconds default as requested)
    var remainingSeconds by remember { mutableStateOf(120) }
    var isAutoDismissActive by remember { mutableStateOf(true) }
    var isAudioPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(isAutoDismissActive) {
        if (isAutoDismissActive) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
            // Auto dismiss screen after 2 minutes to save battery power
            onDismissScreenOnly()
        }
    }

    if (!isSalawat) {
        val popupAudioPlayer = remember { com.example.data.audio.AzanAudioPlayer(context) }
        DisposableEffect(popupAudioPlayer) {
            onDispose { popupAudioPlayer.stopAudio() }
        }
        AutoAdhkarScreen(
            azanAudioPlayer = popupAudioPlayer,
            initialWindow = if (isMorning) {
                com.example.domain.prayer.AdhkarWindow.MORNING
            } else {
                com.example.domain.prayer.AdhkarWindow.EVENING
            },
            onBack = onDismissScreenOnly,
            footerContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F3D99).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF0F3D99).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isAutoDismissActive) {
                                    val m = remainingSeconds / 60
                                    val s = remainingSeconds % 60
                                    "توفير الطاقة • إغلاق تلقائي %02d:%02d".format(m, s)
                                } else {
                                    "وضع القراءة المفتوحة"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F3D99),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { isAutoDismissActive = !isAutoDismissActive }) {
                                Text(
                                    text = if (isAutoDismissActive) "إبقاء الشاشة" else "تفعيل التوفير",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                popupAudioPlayer.stopAudio()
                                onStopAudioAndClose()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("إيقاف وإغلاق", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                isAudioPlaying = false
                                isAutoDismissActive = false
                                popupAudioPlayer.stopAudio()
                                onMuteAudioAndRead()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("كتم والقراءة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        )
        return
    }

    val title = when {
        isSalawat -> "الصلاة على النبي ﷺ 📿"
        isMorning -> "أذكار الصباح المباركة 🌅"
        else -> "أذكار المساء والسكينة 🌇"
    }

    val subtitle = when {
        isSalawat -> "«إِنَّ اللَّهَ وَمَلَائِكَتَهُ يُصَلُّونَ عَلَى النَّبِيِّ يَا أَيُّهَا الَّذِينَ آمَنُوا صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا»"
        isMorning -> "ابدأ يومك بحفظ الله وذكره ونيل البركة والتوفيق"
        else -> "اختم يومك بالاستغفار وشكر النعم وتحصين النفس"
    }

    val sampleDua = when {
        isSalawat -> "«اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ»"
        isMorning -> "«أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ»"
        else -> "«أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا»"
    }

    val gradient = Brush.verticalGradient(
        colors = when {
            isSalawat -> listOf(Color(0xFF042F2E), Color(0xFF064E3B), Color(0xFF021B1A))
            isMorning -> listOf(Color(0xFF0A192F), Color(0xFF132F4C), Color(0xFF172A46))
            else -> listOf(Color(0xFF111827), Color(0xFF1E293B), Color(0xFF0F172A))
        }
    )

    val primaryColor = when {
        isSalawat -> Color(0xFF34D399)
        isMorning -> Color(0xFFFFB74D)
        else -> Color(0xFF81C784)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
            border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Smart Battery Saving Banner with Countdown
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAutoDismissActive) Color(0xFF064E3B).copy(alpha = 0.7f) else Color(0xFF374151).copy(alpha = 0.7f)
                    ),
                    border = BorderStroke(1.dp, if (isAutoDismissActive) Color(0xFF34D399).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isAutoDismissActive) Icons.Default.BatteryChargingFull else Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = if (isAutoDismissActive) Color(0xFF34D399) else Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isAutoDismissActive) "حفظ طاقة البطارية ⚡" else "وضع القراءة المفتوحة 📖",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isAutoDismissActive) {
                                        val minutes = remainingSeconds / 60
                                        val seconds = remainingSeconds % 60
                                        "إغلاق الشاشة بعد %02d:%02d واستمرار الصوت بالخلفية".format(minutes, seconds)
                                    } else {
                                        "تم إلغاء الإغلاق التلقائي للقراءة براحتك"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                isAutoDismissActive = !isAutoDismissActive
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isAutoDismissActive) Color(0xFFFFD54F) else Color(0xFF34D399)
                            )
                        ) {
                            Text(
                                text = if (isAutoDismissActive) "إبقاء الشاشة ⏱️" else "تفعيل التوفير ⚡",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon / Emblem with pulse
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.2f),
                    border = BorderStroke(2.dp, primaryColor),
                    modifier = Modifier
                        .size(68.dp)
                        .scale(if (isAudioPlaying) pulseScale else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSalawat) {
                            Text(
                                text = "ﷺ",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        } else {
                            Icon(
                                if (isMorning) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Audio Status Badge
                AnimatedVisibility(visible = isAudioPlaying) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الصوت يعمل تلقائياً الآن بصوت الشيخ 🎧",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFD1FAE5)
                            )
                        }
                    }
                }

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sample Dua Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sampleDua,
                        fontSize = 13.5.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Action: STOP AUDIO & CLOSE SCREEN (requested explicitly by user)
                Button(
                    onClick = onStopAudioAndClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        Icons.Default.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إيقاف الصوت وإغلاق الشاشة 🛑",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Action: Close screen now to save battery while audio continues in background
                OutlinedButton(
                    onClick = onDismissScreenOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إخفاء الشاشة وتوفير الطاقة (استمرار الصوت) 📱",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Read / Mute / Full App row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mute audio and read
                    OutlinedButton(
                        onClick = {
                            isAudioPlaying = false
                            isAutoDismissActive = false
                            onMuteAudioAndRead()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            Icons.Default.VolumeMute,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("كتم وقراءة 📖", fontSize = 12.sp)
                    }

                    // Open Full Prayer Screen
                    OutlinedButton(
                        onClick = onOpenFullPrayerScreen,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("كل الأذكار 📿", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
