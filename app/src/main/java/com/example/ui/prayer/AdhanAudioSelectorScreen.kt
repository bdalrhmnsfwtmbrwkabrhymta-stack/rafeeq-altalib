package com.example.ui.prayer

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import com.example.domain.prayer.AdhanAudioDownloaderManager
import com.example.domain.prayer.MuezzinVoice
import com.example.domain.prayer.PrayerDataConstants
import com.example.ui.theme.DisplayArabicFontFamily
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val RoyalBlueDeep = Color(0xFF0D47A1)
private val RoyalBlueTitle = Color(0xFF0F3D99)
private val StudioGold = Color(0xFFC9972C)
private val StudioGoldBright = Color(0xFFF59E0B)
private val SoftWhiteBg = Color(0xFFF8FAFC)

/**
 * Studio-Grade Adhan Voice Selector displaying ONLY the 3 core offline 320 kbps HD voices:
 * 1. الأذان المصري الأصيل (MBC وإذاعة القرآن)
 * 2. أذان الحرم المكي الشريف
 * 3. الأذان الخاشع فائق النقاء
 */
@Composable
fun AdhanAudioSelectorScreen(
    selectedVoiceName: String,
    isAutoUpdateHighQualityAudio: Boolean,
    playingVoiceId: String?,
    azanAudioPlayer: AzanAudioPlayer,
    onSelectVoice: (String) -> Unit,
    onPreviewVoiceChange: (String?) -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voices = remember { PrayerDataConstants.MUEZZIN_VOICES }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val outerModifier = if (embedded) {
            Modifier
                .fillMaxWidth()
                .background(SoftWhiteBg, RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp)
        } else {
            Modifier
                .fillMaxSize()
                .background(SoftWhiteBg)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        }

        BoxWithConstraints(modifier = outerModifier) {
            val isLandscapeWide = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                                .testTag("adhan_audio_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = RoyalBlueTitle
                            )
                        }
                    }

                    Text(
                        text = "أصوات الأذان فائقة الجودة HD",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = if (embedded) 22.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RoyalBlueTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = if (onBack != null) 48.dp else 4.dp)
                            .testTag("adhan_audio_selector_title")
                    )
                }

                // 3 Studio Cards ONLY (grid in wide landscape, vertical column in portrait)
                if (isLandscapeWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        voices.forEach { voice ->
                            Box(modifier = Modifier.weight(1f)) {
                                StudioAdhanVoiceCard(
                                    voice = voice,
                                    isSelected = isVoiceSelected(selectedVoiceName, voice),
                                    isPlaying = playingVoiceId == voice.id || playingVoiceId == voice.nameArabic,
                                    onSelect = { onSelectVoice(voice.nameArabic) },
                                    onTogglePreview = {
                                        toggleVoicePreview(
                                            voice = voice,
                                            currentlyPlaying = playingVoiceId == voice.id || playingVoiceId == voice.nameArabic,
                                            azanAudioPlayer = azanAudioPlayer,
                                            onPreviewVoiceChange = onPreviewVoiceChange
                                        )
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        voices.forEach { voice ->
                            StudioAdhanVoiceCard(
                                voice = voice,
                                isSelected = isVoiceSelected(selectedVoiceName, voice),
                                isPlaying = playingVoiceId == voice.id || playingVoiceId == voice.nameArabic,
                                onSelect = { onSelectVoice(voice.nameArabic) },
                                onTogglePreview = {
                                    toggleVoicePreview(
                                        voice = voice,
                                        currentlyPlaying = playingVoiceId == voice.id || playingVoiceId == voice.nameArabic,
                                        azanAudioPlayer = azanAudioPlayer,
                                        onPreviewVoiceChange = onPreviewVoiceChange
                                    )
                                }
                            )
                        }
                    }
                }

                // Bottom Royal Blue Toggle Pill: تحميل وتحديث أعلى جودة صوت تلقائياً
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = RoyalBlueDeep,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable {
                            val next = !isAutoUpdateHighQualityAudio
                            onToggleAutoUpdate(next)
                            if (next) {
                                scope.launch {
                                    AdhanAudioDownloaderManager.autoSyncHighQualityPacks(context)
                                }
                                Toast.makeText(
                                    context,
                                    "تم تفعيل تحديث الأصوات فائقة الجودة 320 kbps تلقائياً",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تحميل وتحديث أعلى جودة صوت تلقائياً",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = isAutoUpdateHighQualityAudio,
                            onCheckedChange = { checked ->
                                onToggleAutoUpdate(checked)
                                if (checked) {
                                    scope.launch {
                                        AdhanAudioDownloaderManager.autoSyncHighQualityPacks(context)
                                    }
                                    Toast.makeText(
                                        context,
                                        "تم تفعيل تحديث الأصوات فائقة الجودة 320 kbps تلقائياً",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RoyalBlueDeep,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.35f),
                                uncheckedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("auto_update_hq_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioAdhanVoiceCard(
    voice: MuezzinVoice,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adhan_voice_card_${voice.id}")
    ) {
        Surface(
            onClick = onSelect,
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = if (isSelected) 8.dp else 4.dp,
            border = if (isSelected) {
                BorderStroke(2.dp, RoyalBlueDeep)
            } else {
                BorderStroke(1.dp, Color(0xFFE2E8F0))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Royal Blue Card Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RoyalBlueDeep)
                        .padding(start = 16.dp, end = 78.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = voice.nameArabic,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "الصوت المختار",
                            tint = Color(0xFFFDE68A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // White Studio Body with Golden Waveform & Preview Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 48dp Play / Stop Preview Button
                    Surface(
                        onClick = onTogglePreview,
                        shape = CircleShape,
                        color = if (isPlaying) Color(0xFFDC2626) else RoyalBlueDeep.copy(alpha = 0.1f),
                        border = BorderStroke(
                            1.5.dp,
                            if (isPlaying) Color(0xFFDC2626) else RoyalBlueDeep.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("play_voice_${voice.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف المعاينة" else "معاينة الصوت",
                                tint = if (isPlaying) Color.White else RoyalBlueDeep,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GoldenStudioWaveform(
                            isAnimating = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )

                        Text(
                            text = voice.subtitleArabic,
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Overlapping Circular 320 kbps HD Badge matching mockup
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp)
                .offset(y = 18.dp)
                .size(58.dp)
                .shadow(5.dp, CircleShape)
                .clip(CircleShape)
                .background(RoyalBlueDeep)
                .border(2.5.dp, Color.White, CircleShape)
                .testTag("bitrate_badge_${voice.id}")
        ) {
            Text(
                text = voice.bitrateLabel,
                fontFamily = DisplayArabicFontFamily,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GoldenStudioWaveform(
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "adhanWaveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 46
        val slotW = size.width / barCount
        val barW = (slotW * 0.56f).coerceAtLeast(2.dp.toPx())
        val midY = size.height / 2f

        for (i in 0 until barCount) {
            val norm = i.toFloat() / (barCount - 1).toFloat() // 0..1
            // Envelope bell curve matching the symmetrical studio waveform
            val bell = sin(norm * PI).toFloat()
            val harmonic = 0.45f + 0.55f * abs(sin(i * 0.72f + if (isAnimating) phase * 2f else 0.4f))
            val amplitudeFactor = (0.14f + 0.86f * bell * harmonic).coerceIn(0.10f, 1.0f)
            val barH = size.height * amplitudeFactor

            val x = i * slotW + (slotW - barW) / 2f
            val y = midY - barH / 2f

            drawRoundRect(
                color = if (isAnimating) StudioGoldBright else StudioGold,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

private fun isVoiceSelected(selectedVoiceName: String, voice: MuezzinVoice): Boolean {
    if (selectedVoiceName == voice.nameArabic || selectedVoiceName == voice.id) return true
    val lower = selectedVoiceName.lowercase()
    return when (voice.id) {
        "egypt_mbc_hq" -> lower.contains("المصري") || lower.contains("mbc") || lower.contains("القاهرة") || lower.contains("رفعت")
        "makkah_hq" -> lower.contains("المكي") || lower.contains("مكة") || lower.contains("makkah") || lower.contains("ملا")
        "madinah_hq" -> lower.contains("الخاشع") || lower.contains("النقاء") || lower.contains("النبوي") || lower.contains("madinah") || lower.contains("العفاسي")
        else -> false
    }
}

private fun resolveRawResIdForVoice(voice: MuezzinVoice): Int {
    return when (voice.id) {
        "makkah_hq" -> R.raw.adhan_makkah_hq
        "madinah_hq" -> R.raw.adhan_madinah_hq
        else -> R.raw.adhan_egypt_mbc_hq
    }
}

private fun toggleVoicePreview(
    voice: MuezzinVoice,
    currentlyPlaying: Boolean,
    azanAudioPlayer: AzanAudioPlayer,
    onPreviewVoiceChange: (String?) -> Unit
) {
    if (currentlyPlaying) {
        azanAudioPlayer.stopAudio()
        onPreviewVoiceChange(null)
    } else {
        onPreviewVoiceChange(voice.id)
        azanAudioPlayer.playRawSoundWithFadeIn(
            rawResId = resolveRawResIdForVoice(voice),
            useAlarmAttributes = false,
            onFinish = { onPreviewVoiceChange(null) }
        )
    }
}
