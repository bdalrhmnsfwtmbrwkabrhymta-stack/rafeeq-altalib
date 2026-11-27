package com.example.ui.prayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.domain.prayer.AdhkarAutoTriggerManager
import com.example.domain.prayer.AdhkarWindow
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily

private val AdhkarRoyalBlue = Color(0xFF0F3D99)
private val AdhkarRoyalDark = Color(0xFF0D2E73)
private val AdhkarGold = Color(0xFFC89B3C)
private val AdhkarGoldLight = Color(0xFFF6D696)
private val AdhkarCreamTop = Color(0xFFFFFBF2)
private val AdhkarParchment = Color(0xFFFFFDF7)
private val AdhkarSkyBadge = Color(0xFF7CC4ED)
private val AdhkarGoldBadge = Color(0xFFD2AC58)

data class OrnamentalDhikrItem(
    val text: String,
    val referenceNote: String,
    val targetCount: Int
)

private val MORNING_ADHKAR_LIST = listOf(
    OrnamentalDhikrItem(
        text = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ",
        referenceNote = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
        referenceNote = "أذكار الصباح الصحيحة",
        targetCount = 1
    ),
    OrnamentalDhikrItem(
        text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
        referenceNote = "حفظ من كل سوء بإذن الله",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",
        referenceNote = "من قالها حين يصبح وحين يمسي كان حقاً على الله أن يرضيه",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
        referenceNote = "كفاه الله ما أهمه من أمر الدنيا والآخرة",
        targetCount = 7
    ),
    OrnamentalDhikrItem(
        text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ",
        referenceNote = "ذكر مضاعف الأجر",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        referenceNote = "حُطّت خطاياه وإن كانت مثل زبد البحر",
        targetCount = 100
    )
)

private val EVENING_ADHKAR_LIST = listOf(
    OrnamentalDhikrItem(
        text = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ",
        referenceNote = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ",
        referenceNote = "أذكار المساء الصحيحة",
        targetCount = 1
    ),
    OrnamentalDhikrItem(
        text = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
        referenceNote = "تحصين المساء",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
        referenceNote = "حفظ من كل ضرر",
        targetCount = 3
    ),
    OrnamentalDhikrItem(
        text = "حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
        referenceNote = "كفاه الله ما أهمه",
        targetCount = 7
    ),
    OrnamentalDhikrItem(
        text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي",
        referenceNote = "سيد الاستغفار",
        targetCount = 1
    ),
    OrnamentalDhikrItem(
        text = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
        referenceNote = "استغفار المساء",
        targetCount = 100
    )
)

/**
 * Ornamental Automatic Morning & Evening Adhkar Screen:
 * - Auto-triggers after Fajr (Morning) and after Asr (Evening)
 * - Custom Islamic blue & gold double-arch frame (`Canvas`)
 * - Vocalized Amiri Bold calligraphy
 * - Circular 1 / 3 interactive tasbeeh counter with radial arc + haptic feedback + auto-advance
 * - Royal Blue horizontal progress bar + full audio recital controls
 */
@Composable
fun AutoAdhkarScreen(
    azanAudioPlayer: AzanAudioPlayer,
    initialWindow: AdhkarWindow? = null,
    onBack: (() -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val resolvedInitial = remember(initialWindow) {
        when (initialWindow) {
            AdhkarWindow.MORNING, AdhkarWindow.EVENING -> initialWindow
            else -> AdhkarAutoTriggerManager.recommendedCategoryForClock()
        }
    }

    var activeCategory by remember(resolvedInitial) { mutableStateOf(resolvedInitial) }
    var currentIndex by remember(activeCategory) { mutableIntStateOf(0) }
    var currentStepCount by remember(activeCategory) { mutableIntStateOf(0) }

    var playingResId by remember { mutableStateOf<Int?>(null) }
    var isAudioPaused by remember { mutableStateOf(false) }
    var currentStageLabel by remember { mutableStateOf<String?>(null) }
    var isAzkarSequenceActive by remember { mutableStateOf(false) }

    LaunchedEffect(azanAudioPlayer) {
        azanAudioPlayer.onCompletionCallback = {
            playingResId = null
            isAudioPaused = false
            currentStageLabel = null
            isAzkarSequenceActive = false
        }
        azanAudioPlayer.onStateChangedCallback = {
            isAudioPaused = azanAudioPlayer.isPaused
            playingResId = azanAudioPlayer.currentPlayingResId
            currentStageLabel = azanAudioPlayer.currentAzkarStageLabel
            isAzkarSequenceActive = azanAudioPlayer.isPlaying() || azanAudioPlayer.isPaused || azanAudioPlayer.currentAzkarStageLabel != null
        }
    }

    val isMorning = activeCategory == AdhkarWindow.MORNING
    val items = if (isMorning) MORNING_ADHKAR_LIST else EVENING_ADHKAR_LIST
    val safeIndex = currentIndex.coerceIn(0, items.lastIndex)
    val currentItem = items[safeIndex]

    val targetRes = if (isMorning) R.raw.azkar_sabah else R.raw.azkar_massa
    val isCurrentPlaying = isAzkarSequenceActive || playingResId == targetRes || playingResId == R.raw.athkar_sabah
    val isPlaying = isCurrentPlaying && !isAudioPaused
    val isPaused = isCurrentPlaying && isAudioPaused

    // Overall progress across all Adhkar in active category
    val stepFraction = currentStepCount.toFloat() / currentItem.targetCount.coerceAtLeast(1).toFloat()
    val overallProgress by animateFloatAsState(
        targetValue = ((safeIndex + stepFraction) / items.size.toFloat()).coerceIn(0.08f, 1f),
        animationSpec = tween(350),
        label = "overallAdhkarProgress"
    )

    // Current active repetition step (e.g., 1 / 3 matching Mockup 7), auto-advancing after targetCount taps
    val displayedStep = (currentStepCount + 1).coerceAtMost(currentItem.targetCount.coerceAtLeast(1))
    val radialSweepFraction by animateFloatAsState(
        targetValue = (displayedStep.toFloat() / currentItem.targetCount.coerceAtLeast(1).toFloat()).coerceIn(0.06f, 1f),
        animationSpec = tween(260),
        label = "tasbeehSweep"
    )

    val handleTasbeehTap: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val nextCount = currentStepCount + 1
        if (nextCount >= currentItem.targetCount) {
            // Completed current dhikr -> auto advance to next
            if (safeIndex < items.lastIndex) {
                currentIndex = safeIndex + 1
                currentStepCount = 0
            } else {
                currentIndex = 0
                currentStepCount = 0
            }
        } else {
            currentStepCount = nextCount
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AdhkarCreamTop, Color(0xFFFDF0D5), Color(0xFFF8DCA4))
                    )
                )
        ) {
            // Subtle bottom-start Royal Blue glow & geometric background accent
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AdhkarRoyalBlue.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.92f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.92f),
                    radius = size.width * 0.7f
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val isLandscapeWide = maxWidth > maxHeight && maxWidth >= 600.dp

                if (isLandscapeWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 980.dp)
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AdhkarHeaderSection(
                                isMorning = isMorning,
                                onSelectMorning = { activeCategory = AdhkarWindow.MORNING },
                                onSelectEvening = { activeCategory = AdhkarWindow.EVENING },
                                onBack = onBack
                            )

                            AdhkarAudioControlsCard(
                                isMorning = isMorning,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                isCurrentPlaying = isCurrentPlaying,
                                stageLabel = currentStageLabel,
                                onPlay = {
                                    if (isPaused) azanAudioPlayer.resumeAudio()
                                    else {
                                        if (isMorning) azanAudioPlayer.playMorningAzkar() else azanAudioPlayer.playEveningAzkar()
                                        playingResId = targetRes
                                        isAzkarSequenceActive = true
                                    }
                                },
                                onPause = { if (isPlaying) azanAudioPlayer.pauseAudio() },
                                onStop = {
                                    azanAudioPlayer.stopAudio()
                                    playingResId = null
                                    currentStageLabel = null
                                    isAzkarSequenceActive = false
                                }
                            )

                            footerContent?.invoke()
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            OrnamentalAdhkarBoardCard(
                                item = currentItem,
                                currentIndex = safeIndex,
                                totalItems = items.size,
                                displayedStep = displayedStep,
                                completedCount = currentStepCount,
                                radialSweepFraction = radialSweepFraction,
                                overallProgress = overallProgress,
                                onTapCounter = handleTasbeehTap,
                                onPrev = {
                                    currentIndex = if (safeIndex > 0) safeIndex - 1 else items.lastIndex
                                    currentStepCount = 0
                                },
                                onNext = {
                                    currentIndex = if (safeIndex < items.lastIndex) safeIndex + 1 else 0
                                    currentStepCount = 0
                                }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AdhkarHeaderSection(
                            isMorning = isMorning,
                            onSelectMorning = { activeCategory = AdhkarWindow.MORNING },
                            onSelectEvening = { activeCategory = AdhkarWindow.EVENING },
                            onBack = onBack
                        )

                        OrnamentalAdhkarBoardCard(
                            item = currentItem,
                            currentIndex = safeIndex,
                            totalItems = items.size,
                            displayedStep = displayedStep,
                            completedCount = currentStepCount,
                            radialSweepFraction = radialSweepFraction,
                            overallProgress = overallProgress,
                            onTapCounter = handleTasbeehTap,
                            onPrev = {
                                currentIndex = if (safeIndex > 0) safeIndex - 1 else items.lastIndex
                                currentStepCount = 0
                            },
                            onNext = {
                                currentIndex = if (safeIndex < items.lastIndex) safeIndex + 1 else 0
                                currentStepCount = 0
                            }
                        )

                        AdhkarAudioControlsCard(
                            isMorning = isMorning,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            isCurrentPlaying = isCurrentPlaying,
                            stageLabel = currentStageLabel,
                            onPlay = {
                                if (isPaused) azanAudioPlayer.resumeAudio()
                                else {
                                    if (isMorning) azanAudioPlayer.playMorningAzkar() else azanAudioPlayer.playEveningAzkar()
                                    playingResId = targetRes
                                    isAzkarSequenceActive = true
                                }
                            },
                            onPause = { if (isPlaying) azanAudioPlayer.pauseAudio() },
                            onStop = {
                                azanAudioPlayer.stopAudio()
                                playingResId = null
                                currentStageLabel = null
                                isAzkarSequenceActive = false
                            }
                        )

                        footerContent?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
private fun AdhkarHeaderSection(
    isMorning: Boolean,
    onSelectMorning: () -> Unit,
    onSelectEvening: () -> Unit,
    onBack: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .testTag("auto_adhkar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = AdhkarRoyalBlue
                    )
                }
            }

            Text(
                text = "أذكار الصباح والمساء",
                fontFamily = DisplayArabicFontFamily,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AdhkarRoyalBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = if (onBack != null) 48.dp else 4.dp)
                    .testTag("auto_adhkar_title")
            )
        }

        // Sky-blue to Gold automatic trigger badge matching Mockup 7
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .shadow(3.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(AdhkarSkyBadge, Color(0xFFA7D4BE), AdhkarGoldBadge)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 9.dp)
                .testTag("auto_adhkar_badge"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "تلقائي عند الصباح والمساء",
                fontFamily = DisplayArabicFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        }

        // Morning / Evening toggle tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onSelectMorning,
                shape = RoundedCornerShape(14.dp),
                color = if (isMorning) AdhkarRoyalBlue else Color.White.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (isMorning) AdhkarRoyalBlue else AdhkarGold.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("tab_morning_adhkar")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (isMorning) Color(0xFFFDE68A) else AdhkarRoyalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "أذكار الصباح",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMorning) Color.White else AdhkarRoyalBlue
                    )
                }
            }

            Surface(
                onClick = onSelectEvening,
                shape = RoundedCornerShape(14.dp),
                color = if (!isMorning) AdhkarRoyalBlue else Color.White.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (!isMorning) AdhkarRoyalBlue else AdhkarGold.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("tab_evening_adhkar")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = if (!isMorning) Color(0xFFFDE68A) else AdhkarRoyalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "أذكار المساء",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isMorning) Color.White else AdhkarRoyalBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun OrnamentalAdhkarBoardCard(
    item: OrnamentalDhikrItem,
    currentIndex: Int,
    totalItems: Int,
    displayedStep: Int,
    completedCount: Int,
    radialSweepFraction: Float,
    overallProgress: Float,
    onTapCounter: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(3.dp, Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF5CF86), Color(0xFFFDF3DD), Color(0xFFF5CF86))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Islamic Ornamental Blue & Gold Arch Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTapCounter),
                contentAlignment = Alignment.Center
            ) {
                // Custom Islamic Blue & Gold Arch Frame Canvas
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    val w = size.width
                    val h = size.height
                    val archTipH = 18.dp.toPx()

                    // Outer Gold Pointed Arch Silhouette
                    val archPath = Path().apply {
                        moveTo(w * 0.5f, 0f)
                        quadraticTo(w * 0.62f, archTipH * 0.9f, w * 0.92f, archTipH)
                        quadraticTo(w, archTipH, w, archTipH + 22.dp.toPx())
                        lineTo(w, h - archTipH - 22.dp.toPx())
                        quadraticTo(w, h - archTipH, w * 0.92f, h - archTipH)
                        quadraticTo(w * 0.62f, h - archTipH * 0.9f, w * 0.5f, h)
                        quadraticTo(w * 0.38f, h - archTipH * 0.9f, w * 0.08f, h - archTipH)
                        quadraticTo(0f, h - archTipH, 0f, h - archTipH - 22.dp.toPx())
                        lineTo(0f, archTipH + 22.dp.toPx())
                        quadraticTo(0f, archTipH, w * 0.08f, archTipH)
                        quadraticTo(w * 0.38f, archTipH * 0.9f, w * 0.5f, 0f)
                        close()
                    }

                    drawPath(path = archPath, color = AdhkarParchment)
                    drawPath(
                        path = archPath,
                        color = AdhkarGold,
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // Deep Royal Blue Ornamental Corner Panels (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
                    val cornerW = w * 0.23f
                    val cornerH = h * 0.24f
                    val inset = 10.dp.toPx()

                    fun drawCornerMedallion(topLeftX: Float, topLeftY: Float, mirrorX: Float, mirrorY: Float) {
                        val cPath = Path().apply {
                            moveTo(topLeftX, topLeftY)
                            lineTo(topLeftX + cornerW * mirrorX, topLeftY)
                            quadraticTo(
                                topLeftX + cornerW * 0.65f * mirrorX,
                                topLeftY + cornerH * 0.65f * mirrorY,
                                topLeftX,
                                topLeftY + cornerH * mirrorY
                            )
                            close()
                        }
                        drawPath(path = cPath, color = AdhkarRoyalBlue)
                        drawPath(
                            path = cPath,
                            color = AdhkarGold,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    }

                    drawCornerMedallion(inset, archTipH + inset * 0.4f, 1f, 1f)
                    drawCornerMedallion(w - inset, archTipH + inset * 0.4f, -1f, 1f)
                    drawCornerMedallion(inset, h - archTipH - inset * 0.4f, 1f, -1f)
                    drawCornerMedallion(w - inset, h - archTipH - inset * 0.4f, -1f, -1f)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.text,
                        fontFamily = HeaderBrandAmiriFontFamily,
                        fontSize = 23.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("adhkar_main_text")
                    )

                    if (item.referenceNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = item.referenceNote,
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Centered Circular 1 / 3 Tasbeeh Counter with Radial Arc
                    Surface(
                        onClick = onTapCounter,
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        shadowElevation = 6.dp,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(78.dp)
                            .testTag("adhkar_tasbeeh_counter")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            ) {
                                val strokePx = 6.dp.toPx()
                                drawArc(
                                    color = Color(0xFFDBEAFE),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(AdhkarRoyalBlue, Color(0xFF3B82F6), AdhkarRoyalBlue)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * radialSweepFraction,
                                    useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }

                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "$displayedStep / ${item.targetCount}",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اضغط للتسبيح ($completedCount من ${item.targetCount})",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 11.sp,
                        color = AdhkarRoyalDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Horizontal Royal Blue Progress Bar matching Mockup 7
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(2.dp)
                        .testTag("adhkar_progress_bar")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(overallProgress)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(AdhkarRoyalDark, Color(0xFF2563EB))
                                )
                            )
                    )
                }

                // Prev / Counter / Next Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onPrev,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AdhkarRoyalBlue.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdhkarRoyalBlue),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "السابق",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("السابق", fontFamily = DisplayArabicFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "الذكر ${currentIndex + 1} من $totalItems",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdhkarRoyalDark
                    )

                    OutlinedButton(
                        onClick = onNext,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AdhkarRoyalBlue.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdhkarRoyalBlue),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("التالي", fontFamily = DisplayArabicFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "التالي",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdhkarAudioControlsCard(
    isMorning: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    isCurrentPlaying: Boolean,
    stageLabel: String? = null,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, AdhkarGold.copy(alpha = 0.55f)),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isMorning) "الاستماع لتلاوة أذكار الصباح كاملة" else "الاستماع لتلاوة أذكار المساء كاملة",
                fontFamily = DisplayArabicFontFamily,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = AdhkarRoyalBlue
            )
            Text(
                text = if (isMorning) {
                    "تبدأ بآية الكرسي مع نسيم الصباح وأصوات العصافير ثم أذكار الصباح كاملة"
                } else {
                    "تبدأ بآية الكرسي بتلاوة مسائية هادئة (بدون أصوات عصافير) ثم أذكار المساء كاملة"
                },
                fontFamily = DisplayArabicFontFamily,
                fontSize = 11.5.sp,
                color = Color(0xFF475569)
            )
            if (!stageLabel.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AdhkarRoyalBlue.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AdhkarRoyalBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stageLabel,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdhkarRoyalBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdhkarRoyalBlue),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("adhkar_audio_play")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isPlaying -> "يعمل الآن"
                            isPaused -> "متابعة"
                            else -> "تشغيل"
                        },
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onPause,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("adhkar_audio_pause")
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إيقاف مؤقت", fontFamily = DisplayArabicFontFamily, fontSize = 11.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onStop,
                    enabled = isCurrentPlaying,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(48.dp)
                        .testTag("adhkar_audio_stop")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إيقاف", fontFamily = DisplayArabicFontFamily, fontSize = 11.5.sp, maxLines = 1)
                }
            }
        }
    }
}
