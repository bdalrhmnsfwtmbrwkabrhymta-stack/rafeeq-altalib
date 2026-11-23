package com.example.ui.screens.islamic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.QuranArabicFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private data class TasbeehBeadMaterial(
    val index: Int,
    val nameAr: String,
    val deepColor: Color,
    val midColor: Color,
    val highlightColor: Color,
    val ambientGlow: Color
)

private val TASBEEH_MATERIALS = listOf(
    TasbeehBeadMaterial(1, "اللازورد الأزرق", Color(0xFF1E3A8A), Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFF3B82F6)),
    TasbeehBeadMaterial(2, "العقيق الأحمر", Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFFFCA5A5), Color(0xFFEF4444)),
    TasbeehBeadMaterial(3, "الزمرد الأخضر", Color(0xFF064E3B), Color(0xFF047857), Color(0xFF6EE7B7), Color(0xFF10B981)),
    TasbeehBeadMaterial(4, "الكهرمان الذهبي", Color(0xFF78350F), Color(0xFFD97706), Color(0xFFFDE68A), Color(0xFFF59E0B)),
    TasbeehBeadMaterial(5, "الجمشت البنفسجي", Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFDDD6FE), Color(0xFF8B5CF6)),
    TasbeehBeadMaterial(6, "المرجان السماوي", Color(0xFF164E63), Color(0xFF0891B2), Color(0xFFA5F3FC), Color(0xFF06B6D4)),
    TasbeehBeadMaterial(7, "خشب الصندل", Color(0xFF451A03), Color(0xFF92400E), Color(0xFFFCD34D), Color(0xFFD97706)),
    TasbeehBeadMaterial(8, "الأوبسيديان الليلي", Color(0xFF0F172A), Color(0xFF334155), Color(0xFFCBD5E1), Color(0xFF64748B)),
    TasbeehBeadMaterial(9, "الكريستال النقي", Color(0xFF0369A1), Color(0xFF38BDF8), Color(0xFFF0F9FF), Color(0xFF7DD3FC))
)

private val FREE_DHIKR_PHRASES = listOf(
    "سبحان الله",
    "الحمد لله",
    "الله أكبر",
    "لا إله إلا الله",
    "أستغفر الله العظيم",
    "لا حول ولا قوة إلا بالله"
)

private fun playBeadClickSound() {
    runCatching {
        val sampleRate = 22050
        val count = (sampleRate * 0.028).roundToInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 145.0)
            val wave = sin(2.0 * PI * 980.0 * t) * 0.65 + sin(2.0 * PI * 1960.0 * t) * 0.35
            samples[i] = (wave * env * 14000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        Thread {
            runCatching {
                Thread.sleep(60L)
                track.stop()
                track.release()
            }
        }.start()
    }
}

@Composable
fun TasbeehInteractiveScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("islamic_tasbeeh_prefs", Context.MODE_PRIVATE) }

    var isGuidedMode by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(prefs.getBoolean("tasbeeh_muted", false)) }

    // Guided Sahih Muslim 597 100-cycle state (0..100)
    var cycleTotal by remember { mutableIntStateOf(prefs.getInt("guided_cycle_total", 0).coerceIn(0, 100)) }
    // Free dhikr mode state
    var selectedFreePhrase by remember { mutableStateOf(FREE_DHIKR_PHRASES.first()) }
    var freeTarget by remember { mutableIntStateOf(33) }
    var freeCount by remember(selectedFreePhrase) {
        mutableIntStateOf(prefs.getInt("count_$selectedFreePhrase", 0))
    }
    var grandTotal by remember { mutableIntStateOf(prefs.getInt("grand_total", 0)) }

    // Visual animation states
    val slideOffset = remember { Animatable(0f) }
    val dropletProgress = remember { Animatable(0f) }

    val activeMaterialIndex = if (isGuidedMode) {
        (cycleTotal / 11).coerceIn(0, TASBEEH_MATERIALS.lastIndex)
    } else {
        ((freeCount % 99) / 11).coerceIn(0, TASBEEH_MATERIALS.lastIndex)
    }
    val activeMaterial = TASBEEH_MATERIALS[activeMaterialIndex]
    val animatedGlow by animateColorAsState(
        targetValue = activeMaterial.ambientGlow,
        animationSpec = tween(550),
        label = "tasbeeh_glow"
    )
    val animatedMid by animateColorAsState(
        targetValue = activeMaterial.midColor,
        animationSpec = tween(550),
        label = "tasbeeh_mid"
    )
    val animatedHighlight by animateColorAsState(
        targetValue = activeMaterial.highlightColor,
        animationSpec = tween(550),
        label = "tasbeeh_highlight"
    )

    val phaseTitle: String
    val activeDhikrText: String
    val phaseCounterText: String
    val phaseAccentColor: Color

    if (isGuidedMode) {
        when {
            cycleTotal < 33 -> {
                phaseTitle = "التسبيح • ٣٣"
                activeDhikrText = "سُبْحَانَ اللَّهِ"
                phaseCounterText = "$cycleTotal / 33"
                phaseAccentColor = Color(0xFFF59E0B)
            }
            cycleTotal < 66 -> {
                phaseTitle = "التحميد • ٣٣"
                activeDhikrText = "الْحَمْدُ لِلَّهِ"
                phaseCounterText = "${cycleTotal - 33} / 33"
                phaseAccentColor = Color(0xFF10B981)
            }
            cycleTotal < 99 -> {
                phaseTitle = "التكبير • ٣٣"
                activeDhikrText = "اللَّهُ أَكْبَرُ"
                phaseCounterText = "${cycleTotal - 66} / 33"
                phaseAccentColor = Color(0xFF38BDF8)
            }
            cycleTotal == 99 -> {
                phaseTitle = "الختام • تمام المئة"
                activeDhikrText = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ"
                phaseCounterText = "١ / ١"
                phaseAccentColor = Color(0xFFFACC15)
            }
            else -> {
                phaseTitle = "تمت المئة بحمد الله"
                activeDhikrText = "غُفِرَتْ خَطَايَاهُ وَإِنْ كَانَتْ مِثْلَ زَبَدِ الْبَحْرِ"
                phaseCounterText = "33 / 33"
                phaseAccentColor = Color(0xFFFACC15)
            }
        }
    } else {
        phaseTitle = "تسبيح حر • الهدف $freeTarget"
        activeDhikrText = selectedFreePhrase
        val modVal = if (freeCount > 0 && freeCount % freeTarget == 0) freeTarget else freeCount % freeTarget
        phaseCounterText = "$modVal / $freeTarget"
        phaseAccentColor = animatedGlow
    }

    val isCycleComplete = isGuidedMode && cycleTotal >= 100

    fun performTasbeehTap() {
        if (isCycleComplete) return
        if (isGuidedMode) {
            val next = (cycleTotal + 1).coerceAtMost(100)
            cycleTotal = next
            grandTotal += 1
            val keyToInc = when {
                next <= 33 -> "count_سبحان الله"
                next <= 66 -> "count_الحمد لله"
                next <= 99 -> "count_الله أكبر"
                else -> "count_لا إله إلا الله"
            }
            val prevKeyCount = prefs.getInt(keyToInc, 0)
            prefs.edit()
                .putInt("guided_cycle_total", next)
                .putInt("grand_total", grandTotal)
                .putInt(keyToInc, prevKeyCount + 1)
                .apply()

            haptic.performHapticFeedback(
                if (next == 33 || next == 66 || next == 99 || next == 100) {
                    HapticFeedbackType.LongPress
                } else {
                    HapticFeedbackType.TextHandleMove
                }
            )
        } else {
            freeCount += 1
            grandTotal += 1
            prefs.edit()
                .putInt("count_$selectedFreePhrase", freeCount)
                .putInt("grand_total", grandTotal)
                .apply()
            haptic.performHapticFeedback(
                if (freeCount % freeTarget == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }

        if (!isMuted) {
            scope.launch(Dispatchers.Default) { playBeadClickSound() }
        }
        scope.launch {
            slideOffset.snapTo(1f)
            slideOffset.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
        scope.launch {
            dropletProgress.snapTo(0.05f)
            dropletProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            dropletProgress.snapTo(0f)
        }
    }

    Scaffold(
        containerColor = Color(0xFF090F1E),
        topBar = {
            IslamicTopBar(
                title = "خدمات التسبيح",
                onBack = onBack,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val nextMuted = !isMuted
                                isMuted = nextMuted
                                prefs.edit().putBoolean("tasbeeh_muted", nextMuted).apply()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tasbeeh_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "تشغيل صوت الخرز" else "كتم صوت الخرز",
                                tint = if (isMuted) Color(0xFF94A3B8) else animatedGlow
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isGuidedMode) {
                                    cycleTotal = 0
                                    prefs.edit().putInt("guided_cycle_total", 0).apply()
                                } else {
                                    freeCount = 0
                                    prefs.edit().putInt("count_$selectedFreePhrase", 0).apply()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tasbeeh_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تصفير الدورة",
                                tint = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090F1E),
                            Color(0xFF0F172A),
                            Color(0xFF0B1325)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Mode Selector & Sahih Muslim 597 Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "صحيح مسلم ٥٩٧",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCD34D),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("tasbeeh_hadith_badge")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isGuidedMode,
                            onClick = { isGuidedMode = true },
                            label = { Text("الورد النبوي ١٠٠", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.22f),
                                selectedLabelColor = Color(0xFF6EE7B7),
                                labelColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.testTag("tasbeeh_mode_guided")
                        )
                        FilterChip(
                            selected = !isGuidedMode,
                            onClick = { isGuidedMode = false },
                            label = { Text("تسبيح حر", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = animatedGlow.copy(alpha = 0.24f),
                                selectedLabelColor = Color.White,
                                labelColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.testTag("tasbeeh_mode_free")
                        )
                    }
                }

                // Free mode dhikr picker
                AnimatedVisibility(visible = !isGuidedMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(FREE_DHIKR_PHRASES) { phrase ->
                                FilterChip(
                                    selected = selectedFreePhrase == phrase,
                                    onClick = { selectedFreePhrase = phrase },
                                    label = { Text(phrase, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = animatedMid.copy(alpha = 0.32f),
                                        selectedLabelColor = Color.White,
                                        labelColor = Color(0xFFCBD5E1)
                                    )
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(33, 99, 100).forEach { t ->
                                FilterChip(
                                    selected = freeTarget == t,
                                    onClick = { freeTarget = t },
                                    label = { Text("الهدف $t", fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        labelColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Active Dhikr Prompt + Dual Counters + 3-Segment Progress Bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF111C33).copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, phaseAccentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = phaseTitle,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = phaseAccentColor
                            )
                            Text(
                                text = if (isGuidedMode) "$phaseCounterText  •  $cycleTotal / 100" else "$phaseCounterText  •  الإجمالي $freeCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.testTag("tasbeeh_counters")
                            )
                        }

                        Text(
                            text = activeDhikrText,
                            fontFamily = QuranArabicFontFamily,
                            fontSize = if (activeDhikrText.length > 35) 17.sp else 26.sp,
                            lineHeight = if (activeDhikrText.length > 35) 26.sp else 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tasbeeh_active_phrase")
                        )

                        // 3-Segment Progress Bar (Amber Gold -> Emerald Green -> Sky Blue)
                        val p1 = if (isGuidedMode) (cycleTotal.coerceIn(0, 33) / 33f) else ((freeCount % freeTarget).toFloat() / freeTarget)
                        val p2 = if (isGuidedMode) ((cycleTotal - 33).coerceIn(0, 33) / 33f) else 0f
                        val p3 = if (isGuidedMode) ((cycleTotal - 66).coerceIn(0, 34) / 34f) else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SegmentBar(fraction = p1, fillColor = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                            if (isGuidedMode) {
                                SegmentBar(fraction = p2, fillColor = Color(0xFF10B981), modifier = Modifier.weight(1f))
                                SegmentBar(fraction = p3, fillColor = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 3D Interactive Bead Strand + Ambient Bokeh + Bottom Golden Bowl Canvas
                val infiniteBokeh = rememberInfiniteTransition(label = "tasbeeh_bokeh")
                val bokehPulse by infiniteBokeh.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
                    label = "bokeh_pulse"
                )

                val tapInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            enabled = !isCycleComplete
                        ) { performTasbeehTap() }
                        .testTag("tasbeeh_tap_surface"),
                    contentAlignment = Alignment.Center
                ) {
                    val remainingSpheres = if (isGuidedMode) {
                        ((100 - cycleTotal) * 24f / 100f).roundToInt().coerceIn(0, 24)
                    } else {
                        val rem = (freeTarget - (freeCount % freeTarget)).let { if (freeCount > 0 && freeCount % freeTarget == 0) 0 else it }
                        ((rem.toFloat() / freeTarget) * 18f).roundToInt().coerceIn(0, 18)
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // 1. Ambient bokeh celestial lights matching current bead material
                        val bokehCenters = listOf(
                            Triple(w * 0.18f, h * 0.22f, w * 0.22f * bokehPulse),
                            Triple(w * 0.82f, h * 0.26f, w * 0.19f * bokehPulse),
                            Triple(w * 0.50f, h * 0.44f, w * 0.28f * bokehPulse),
                            Triple(w * 0.25f, h * 0.74f, w * 0.14f),
                            Triple(w * 0.76f, h * 0.72f, w * 0.14f)
                        )
                        bokehCenters.forEachIndexed { idx, (bx, by, br) ->
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        animatedGlow.copy(alpha = if (idx == 2) 0.22f else 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(bx, by),
                                    radius = br.coerceAtLeast(10f)
                                ),
                                radius = br.coerceAtLeast(10f),
                                center = Offset(bx, by)
                            )
                        }

                        // 2. Curved, slightly tilted 3D bead strand (11 visible beads along Bezier curve)
                        val p0 = Offset(w * 0.08f, h * 0.24f)
                        val p1 = Offset(w * 0.50f, h * 0.50f)
                        val p2 = Offset(w * 0.92f, h * 0.30f)

                        fun bezierPoint(t: Float): Offset {
                            val u = 1f - t
                            val x = u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x
                            val y = u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y
                            return Offset(x, y)
                        }

                        val cordPath = Path().apply {
                            moveTo(p0.x, p0.y)
                            quadraticTo(p1.x, p1.y, p2.x, p2.y)
                        }
                        drawPath(
                            path = cordPath,
                            color = Color(0xFFD4AF37).copy(alpha = 0.7f),
                            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Hanging knot & silk tassel on right end of strand
                        val tasselStart = bezierPoint(0.95f)
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = tasselStart,
                            end = Offset(tasselStart.x + 4.dp.toPx(), tasselStart.y + 34.dp.toPx()),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        for (tIdx in -2..2) {
                            drawLine(
                                color = Color(0xFFFBBF24).copy(alpha = 0.78f),
                                start = Offset(tasselStart.x + 4.dp.toPx(), tasselStart.y + 20.dp.toPx()),
                                end = Offset(tasselStart.x + (4 + tIdx * 3.2f).dp.toPx(), tasselStart.y + 46.dp.toPx()),
                                strokeWidth = 1.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        val totalVisibleBeads = 9
                        val slideDelta = slideOffset.value
                        val centerBeadIdx = 4
                        var activeBeadCenter = bezierPoint(0.5f)

                        for (i in 0 until totalVisibleBeads) {
                            val baseT = 0.08f + (i.toFloat() / (totalVisibleBeads - 1)) * 0.84f
                            val shiftedT = (baseT - slideDelta * 0.08f).coerceIn(0.05f, 0.95f)
                            val c = bezierPoint(shiftedT)
                            val isCenterFocal = i == centerBeadIdx
                            if (isCenterFocal) activeBeadCenter = c

                            val distFromCenter = kotlin.math.abs(i - centerBeadIdx)
                            val radius = when {
                                isCenterFocal -> 25.dp.toPx()
                                distFromCenter == 1 -> 20.dp.toPx()
                                distFromCenter == 2 -> 17.5.dp.toPx()
                                else -> 15.dp.toPx()
                            }

                            // Soft shadow under bead
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.36f),
                                radius = radius * 1.04f,
                                center = Offset(c.x + 3.dp.toPx(), c.y + 5.dp.toPx())
                            )

                            // Glowing halo on focal active bead
                            if (isCenterFocal) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(animatedGlow.copy(alpha = 0.55f), Color.Transparent),
                                        center = c,
                                        radius = radius * 1.9f
                                    ),
                                    radius = radius * 1.9f,
                                    center = c
                                )
                            }

                            // 3D sphere radial gradient
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        animatedHighlight,
                                        animatedMid,
                                        activeMaterial.deepColor
                                    ),
                                    center = Offset(c.x - radius * 0.34f, c.y - radius * 0.34f),
                                    radius = radius * 1.35f
                                ),
                                radius = radius,
                                center = c
                            )

                            // Specular glass/stone gleam dot
                            drawCircle(
                                color = Color.White.copy(alpha = if (isCenterFocal) 0.72f else 0.5f),
                                radius = radius * 0.22f,
                                center = Offset(c.x - radius * 0.36f, c.y - radius * 0.36f)
                            )
                        }

                        // 3. Bottom Golden Bowl with draining burden spheres
                        val bowlCenterX = w * 0.50f
                        val bowlTopY = h * 0.74f
                        val bowlWidth = (w * 0.44f).coerceAtMost(190.dp.toPx())
                        val bowlHeight = 52.dp.toPx()

                        // Falling glowing droplet animation into the golden bowl
                        val dropP = dropletProgress.value
                        if (dropP > 0f) {
                            val dropY = activeBeadCenter.y + (bowlTopY - activeBeadCenter.y) * dropP
                            val dropRadius = 6.dp.toPx() * (1f - dropP * 0.25f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, animatedGlow, Color.Transparent),
                                    center = Offset(bowlCenterX, dropY),
                                    radius = dropRadius * 2.5f
                                ),
                                radius = dropRadius * 2.5f,
                                center = Offset(bowlCenterX, dropY)
                            )
                            drawCircle(
                                color = Color(0xFFFEF08A),
                                radius = dropRadius,
                                center = Offset(bowlCenterX, dropY)
                            )
                        }

                        // Render dark spheres inside bowl fading out 1-by-1 as counts increase
                        for (sIdx in 0 until remainingSpheres) {
                            val row = sIdx / 8
                            val col = sIdx % 8
                            val spacing = (bowlWidth * 0.74f) / 8f
                            val sx = bowlCenterX - (bowlWidth * 0.33f) + col * spacing + (if (row % 2 == 1) spacing * 0.35f else 0f)
                            val sy = bowlTopY + 14.dp.toPx() - row * 7.5.dp.toPx()
                            val sr = 4.6.dp.toPx()
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF475569), Color(0xFF0F172A)),
                                    center = Offset(sx - 1.5f, sy - 1.5f),
                                    radius = sr * 1.4f
                                ),
                                radius = sr,
                                center = Offset(sx, sy)
                            )
                        }

                        // Golden bowl vessel foreground
                        val bowlRectTopLeft = Offset(bowlCenterX - bowlWidth / 2f, bowlTopY)
                        drawArc(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFDE047), Color(0xFFD97706), Color(0xFF92400E)),
                                startY = bowlTopY,
                                endY = bowlTopY + bowlHeight
                            ),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(bowlRectTopLeft.x, bowlTopY - bowlHeight * 0.5f),
                            size = Size(bowlWidth, bowlHeight * 1.6f)
                        )
                        drawArc(
                            color = Color(0xFFFEF08A),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(bowlRectTopLeft.x, bowlTopY - bowlHeight * 0.5f),
                            size = Size(bowlWidth, bowlHeight * 1.6f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Tap prompt hint near center
                    if (!isCycleComplete) {
                        Text(
                            text = if (isGuidedMode) "المس في أي مكان للتسبيح" else "اضغط للذكر • ${activeMaterial.nameAr}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        )
                    }
                }

                // Footer Material Pill & Persistent Grand Total
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111C33),
                    border = BorderStroke(1.dp, animatedGlow.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(animatedHighlight, animatedMid, activeMaterial.deepColor)
                                        )
                                    )
                            )
                            Text(
                                text = "خامة ${activeMaterial.nameAr} (${activeMaterial.index}/9)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("tasbeeh_material_badge")
                            )
                        }

                        Text(
                            text = "الإجمالي: $grandTotal",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFCD34D),
                            modifier = Modifier.testTag("tasbeeh_grand_total")
                        )
                    }
                }
            }

            // Completion Golden Overlay at 100 (Sahih Muslim 597) centered over full screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isCycleComplete,
                enter = fadeIn(tween(380)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF050A16).copy(alpha = 0.72f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.97f),
                        border = BorderStroke(1.5.dp, Color(0xFFFACC15)),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tasbeeh_completion_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "تَقَبَّلَ اللَّهُ ذِكْرَكَ",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFDE047),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "«غُفِرَتْ خَطَايَاهُ وَإِنْ كَانَتْ مِثْلَ زَبَدِ الْبَحْرِ»",
                                fontFamily = QuranArabicFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "صحيح مسلم ٥٩٧ • الكفّة فارغة نقية",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    cycleTotal = 0
                                    prefs.edit().putInt("guided_cycle_total", 0).apply()
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 50.dp)
                                    .testTag("tasbeeh_restart_cycle_button")
                            ) {
                                Text(
                                    text = "بدء دورة تسبيح جديدة (١٠٠)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentBar(
    fraction: Float,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}
