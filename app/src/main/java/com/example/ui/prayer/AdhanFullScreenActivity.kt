package com.example.ui.prayer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
        if (scheduledTimeMs > 0L && Math.abs(now - scheduledTimeMs) > 15 * 60 * 1000L) {
            finish()
            return
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الفجر"
        val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "القاهرة"
        val muezzinVoice = intent.getStringExtra(EXTRA_MUEZZIN) ?: "أذان القاهرة"
        val autoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, true)

        azanPlayer = AzanAudioPlayer(this)
        if (autoPlay && !com.example.domain.prayer.AdhanPlaybackService.isPlayingAdhan) {
            azanPlayer?.playMuezzin(muezzinVoice, muezzinVoice)
        }

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RafiqTheme {
                    AdhanFullScreenContent(
                        prayerName = prayerName,
                        cityName = cityName,
                        muezzinVoice = muezzinVoice,
                        onStopAdhan = {
                            com.example.domain.prayer.AdhanPlaybackService.stop(this)
                            azanPlayer?.stopAudio()
                            com.example.data.audio.AppAudioManager.stopAllNonAdhanAudio(this)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || 
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || 
            keyCode == KeyEvent.KEYCODE_POWER || 
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            
            com.example.domain.prayer.AdhanPlaybackService.stop(this)
            azanPlayer?.stopAudio()
            com.example.data.audio.AppAudioManager.stopAllNonAdhanAudio(this)
            // Just silence the alarm, keep the screen active for user to dismiss
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        azanPlayer?.stopAudio()
    }
}

@Composable
fun AdhanFullScreenContent(
    prayerName: String,
    cityName: String,
    muezzinVoice: String,
    onStopAdhan: () -> Unit
) {
    val currentTime = remember { SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date()) }
    val amPm = remember { SimpleDateFormat("a", Locale.getDefault()).format(Date()) }
    val hijriDate = remember {
        val cal = android.icu.util.IslamicCalendar()
        "${cal.get(android.icu.util.Calendar.DAY_OF_WEEK_IN_MONTH)} ${getHijriMonthName(cal.get(android.icu.util.Calendar.MONTH))}"
    }
    val context = LocalContext.current
    
    val isDayTime = prayerName != "العشاء" && prayerName != "المغرب" && prayerName != "الفجر"
    
    val bgGradient = if (isDayTime) {
        Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFFE0F6FF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
    }
    
    val textColor = if (isDayTime) Color.DarkGray else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Icon(
            Icons.Default.Mosque,
            contentDescription = null,
            tint = if(isDayTime) Color.DarkGray.copy(alpha=0.1f) else Color.White.copy(alpha=0.1f),
            modifier = Modifier.fillMaxWidth().height(250.dp).align(Alignment.BottomCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = currentTime,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                Text(
                    text = amPm,
                    fontSize = 24.sp,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "اليوم، $hijriDate",
                fontSize = 18.sp,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = prayerName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "تم تحديث أوقات الصلاة لمدينة $cityName",
                fontSize = 14.sp,
                color = textColor.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            if (isDayTime) listOf(Color(0xFF64B5F6), Color(0xFFE3F2FD))
                            else listOf(Color(0xFF1976D2), Color(0xFF0D47A1))
                        )
                    )
                    .border(4.dp, textColor.copy(alpha=0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .offset(y = (-30).dp)
                        .clip(CircleShape)
                        .background(if (isDayTime) Color(0xFFFFD54F) else Color(0xFFFFF59D))
                        .background(Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha=0.5f), Color.Transparent)
                        ))
                )
                Icon(
                    Icons.Default.Mosque,
                    contentDescription = null,
                    tint = if(isDayTime) Color(0xFF1976D2) else Color(0xFF0D47A1),
                    modifier = Modifier.size(160.dp).align(Alignment.BottomCenter).offset(y = 20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { 
                    try {
                        val uri = Uri.parse("geo:0,0?q=المساجد")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.setPackage("com.google.android.apps.maps")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val uri = Uri.parse("https://www.google.com/maps/search/المساجد")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                }) {
                    Icon(Icons.Default.LocationCity, contentDescription = null, tint = textColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المساجد القريبة", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { 
                    onStopAdhan()
                }) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = textColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("القبلة", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            val maxDrag = 250f 
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .offset { IntOffset(dragOffsetX.toInt(), 0) }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(2.dp, textColor, CircleShape)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetX > maxDrag || dragOffsetX < -maxDrag) onStopAdhan()
                                    else dragOffsetX = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(textColor))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "اسحب لتعطيل المنبه",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
