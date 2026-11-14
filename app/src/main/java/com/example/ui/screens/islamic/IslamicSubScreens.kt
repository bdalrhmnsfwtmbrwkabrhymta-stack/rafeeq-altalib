package com.example.ui.screens.islamic

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import com.example.data.preferences.UserSettings
import com.example.data.quran.QuranRepository
import com.example.data.quran.SurahDetail
import com.example.domain.prayer.DailyPrayerSchedule
import com.example.ui.MainViewModel
import com.example.ui.prayer.DailyPrayerTimesSection
import com.example.ui.screens.AzanAndMuezzinTabContent
import com.example.ui.screens.AzkarItem
import com.example.ui.screens.SalawatAndAzkarTabContent
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.QuranArabicFontFamily
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

@Composable
fun PrayerTimesDetailSubScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    dailySchedule: DailyPrayerSchedule?,
    azanAudioPlayer: AzanAudioPlayer,
    onOpenLocationSheet: () -> Unit,
    onOpenAdhanSettings: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val prayerTimes by viewModel.prayerTimes.collectAsState()
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val liveDiffMs = ((prayerTimes?.nextPrayerTimeMs ?: currentTimeMs) - currentTimeMs).coerceAtLeast(0L)
    val liveHours = (liveDiffMs / 3600000).toInt()
    val liveMinutes = ((liveDiffMs % 3600000) / 60000).toInt()
    val liveSeconds = ((liveDiffMs % 60000) / 1000).toInt()
    val liveRemainingFormatted = String.format(Locale.US, "%02d:%02d:%02d", liveHours, liveMinutes, liveSeconds)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(
                title = "مواقيت الصلاة",
                onBack = onBack,
                trailingAction = {
                    IconButton(onClick = onOpenLocationSheet, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.EditLocationAlt, contentDescription = "تغيير الموقع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "الصلاة القادمة • ${userSettings.cityLocation.ifBlank { "القاهرة" }}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "صلاة ${prayerTimes?.nextPrayerName ?: "الظهر"}",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "متبقي $liveRemainingFormatted",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (dailySchedule != null) {
                item {
                    DailyPrayerTimesSection(
                        viewModel = viewModel,
                        dailySchedule = dailySchedule,
                        azanAudioPlayer = azanAudioPlayer,
                        isEnglish = userSettings.appLanguage == "en",
                        onOpenSettings = onOpenAdhanSettings
                    )
                }
            }

            item {
                val prayersList = listOf(
                    Triple("الفجر", prayerTimes?.fajr?.timeString ?: "04:45 ص", Icons.Default.WbTwilight),
                    Triple("الشروق", prayerTimes?.sunrise?.timeString ?: "06:05 ص", Icons.Default.WbSunny),
                    Triple("الظهر", prayerTimes?.dhuhr?.timeString ?: "12:05 م", Icons.Default.WbSunny),
                    Triple("العصر", prayerTimes?.asr?.timeString ?: "03:20 م", Icons.Default.FilterDrama),
                    Triple("المغرب", prayerTimes?.maghrib?.timeString ?: "06:05 م", Icons.Default.NightsStay),
                    Triple("العشاء", prayerTimes?.isha?.timeString ?: "07:30 م", Icons.Default.NightsStay)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اضغط على أي صلاة لقراءة أذكار ما بعد الصلاة:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    prayersList.forEach { (name, time, icon) ->
                        var showAthkarDialog by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { showAthkarDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = name,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = name,
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = time,
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (showAthkarDialog) {
                            AlertDialog(
                                onDismissRequest = { showAthkarDialog = false },
                                title = {
                                    Text("أذكار ما بعد صلاة $name", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                },
                                text = {
                                    Text(
                                        text = when (name) {
                                            "الفجر" -> "• أستغفر الله (3 مرات)\n• اللهم أنت السلام ومنك السلام تباركت يا ذا الجلال والإكرام\n• لا إله إلا الله وحده لا شريك له له الملك وله الحمد يحيي ويميت وهو على كل شيء قدير (10 مرات)\n• آية الكرسي والمعوذات (3 مرات)"
                                            "المغرب" -> "• أستغفر الله (3 مرات)\n• لا إله إلا الله وحده لا شريك له (10 مرات)\n• آية الكرسي وسور الإخلاص والفلق والناس (3 مرات)"
                                            else -> "• أستغفر الله (3 مرات)\n• اللهم أنت السلام ومنك السلام\n• سبحان الله (33) والحمد لله (33) والله أكبر (33)\n• آية الكرسي"
                                        },
                                        lineHeight = 22.sp,
                                        fontSize = 14.sp
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { showAthkarDialog = false }) {
                                        Text("إغلاق", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdhanSettingsSubScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    azanAudioPlayer: AzanAudioPlayer,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var previewVoiceId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            azanAudioPlayer.stopAudio()
            previewVoiceId = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "الأذان", onBack = onBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AzanAndMuezzinTabContent(
                userSettings = userSettings,
                viewModel = viewModel,
                azanAudioPlayer = azanAudioPlayer,
                previewVoiceId = previewVoiceId,
                onPreviewVoiceChange = { previewVoiceId = it },
                isEnglish = userSettings.appLanguage == "en"
            )
        }
    }
}

@Composable
fun AzkarDailySubScreen(
    azanAudioPlayer: AzanAudioPlayer,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("islamic_azkar_prefs", Context.MODE_PRIVATE) }
    val todayKey = remember { LocalDate.now().toString() }

    var selectedCategory by remember { mutableStateOf("MORNING") }
    var playingAzkarResId by remember { mutableStateOf<Int?>(null) }
    var isAzkarPaused by remember { mutableStateOf(false) }

    LaunchedEffect(azanAudioPlayer) {
        azanAudioPlayer.onCompletionCallback = {
            playingAzkarResId = null
            isAzkarPaused = false
        }
        azanAudioPlayer.onStateChangedCallback = {
            isAzkarPaused = azanAudioPlayer.isPaused
            playingAzkarResId = azanAudioPlayer.currentPlayingResId
        }
    }

    val morningAzkar = remember {
        listOf(
            AzkarItem("أَصْبَحْنَا وَأَصْبَحَ المُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ", 1),
            AzkarItem("اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ", 1),
            AzkarItem("آيَةُ الْكُرْسِيِّ — اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ", 1),
            AzkarItem("بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", 3),
            AzkarItem("رَضِيتُ بِاللَّهِ رَبَّاً، وَبِالإِسْلاَمِ دِينَاً، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيَّاً", 3),
            AzkarItem("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ", 3),
            AzkarItem("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", 100)
        )
    }

    val eveningAzkar = remember {
        listOf(
            AzkarItem("أَمْسَيْنَا وَأَمْسَى المُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ", 1),
            AzkarItem("اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ المَصِيرُ", 1),
            AzkarItem("آيَةُ الْكُرْسِيِّ — اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ", 1),
            AzkarItem("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ", 3),
            AzkarItem("بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", 3),
            AzkarItem("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", 100)
        )
    }

    val isMorning = selectedCategory == "MORNING"
    val targetRes = if (isMorning) R.raw.azkar_sabah else R.raw.azkar_massa
    val isCurrentPlaying = playingAzkarResId == targetRes
    val isPlaying = isCurrentPlaying && !isAzkarPaused
    val isPaused = isCurrentPlaying && isAzkarPaused
    val activeList = if (isMorning) morningAzkar else eveningAzkar

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "أذكار الصباح والمساء", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = isMorning,
                        onClick = { selectedCategory = "MORNING" },
                        label = { Text("أذكار الصباح", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isMorning,
                        onClick = { selectedCategory = "EVENING" },
                        label = { Text("أذكار المساء", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.NightsStay, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isMorning) "الاستماع لأذكار الصباح كاملة" else "الاستماع لأذكار المساء كاملة",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isPaused) azanAudioPlayer.resumeAudio()
                                    else {
                                        if (isMorning) azanAudioPlayer.playMorningAzkar() else azanAudioPlayer.playEveningAzkar()
                                        playingAzkarResId = targetRes
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPlaying) "يعمل الآن" else "تشغيل")
                            }
                            OutlinedButton(
                                onClick = { if (isPlaying) azanAudioPlayer.pauseAudio() },
                                enabled = isPlaying,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إيقاف مؤقت")
                            }
                            OutlinedButton(
                                onClick = {
                                    azanAudioPlayer.stopAudio()
                                    playingAzkarResId = null
                                },
                                enabled = isCurrentPlaying,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إيقاف")
                            }
                        }
                    }
                }
            }

            itemsIndexed(activeList) { idx, azkar ->
                val prefKey = "azkar_${todayKey}_${selectedCategory}_$idx"
                var remaining by remember(prefKey) {
                    mutableIntStateOf(prefs.getInt(prefKey, azkar.targetCount))
                }
                val isDone = remaining <= 0

                Surface(
                    onClick = {
                        if (remaining > 0) {
                            remaining -= 1
                            prefs.edit().putInt(prefKey, remaining).apply()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDone) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isDone) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = azkar.text,
                            fontFamily = QuranArabicFontFamily,
                            fontSize = 18.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDone) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isDone) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isDone) "✓" else "$remaining",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
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
fun SalawatSubScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("islamic_salawat_prefs", Context.MODE_PRIVATE) }
    val todayKey = remember { "salawat_${LocalDate.now()}" }
    var todayCount by remember { mutableIntStateOf(prefs.getInt(todayKey, 0)) }
    var totalCount by remember { mutableIntStateOf(prefs.getInt("salawat_total", 0)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "الصلاة على النبي ﷺ", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                onClick = {
                    todayCount += 1
                    totalCount += 1
                    prefs.edit().putInt(todayKey, todayCount).putInt("salawat_total", totalCount).apply()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ ﷺ",
                        fontFamily = QuranArabicFontFamily,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$todayCount",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "عداد اليوم • الإجمالي: $totalCount • اضغط للتسبيح",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                SalawatAndAzkarTabContent(
                    userSettings = userSettings,
                    viewModel = viewModel,
                    azanAudioPlayer = remember { AzanAudioPlayer(context) },
                    isEnglish = userSettings.appLanguage == "en"
                )
            }
        }
    }
}

@Composable
fun TasbeehSubScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("islamic_tasbeeh_prefs", Context.MODE_PRIVATE) }

    val phrases = remember {
        listOf(
            "سبحان الله",
            "الحمد لله",
            "الله أكبر",
            "لا إله إلا الله",
            "أستغفر الله العظيم",
            "لا حول ولا قوة إلا بالله"
        )
    }
    var selectedPhrase by remember { mutableStateOf(phrases.first()) }
    var target by remember { mutableIntStateOf(33) }
    var count by remember(selectedPhrase) {
        mutableIntStateOf(prefs.getInt("count_$selectedPhrase", 0))
    }
    var grandTotal by remember {
        mutableIntStateOf(prefs.getInt("grand_total", 0))
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val progress = ((count % target).toFloat() / target.toFloat()).let {
        if (count > 0 && count % target == 0) 1f else it
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "السبحة", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(phrases) { phrase ->
                        FilterChip(
                            selected = selectedPhrase == phrase,
                            onClick = { selectedPhrase = phrase },
                            label = { Text(phrase, fontWeight = FontWeight.Bold) }
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
                            selected = target == t,
                            onClick = { target = t },
                            label = { Text("الهدف $t", fontSize = 12.sp) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // Central Interactive Tasbeeh Ring
            Surface(
                onClick = {
                    count += 1
                    grandTotal += 1
                    prefs.edit()
                        .putInt("count_$selectedPhrase", count)
                        .putInt("grand_total", grandTotal)
                        .apply()
                    haptic.performHapticFeedback(
                        if (count % target == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                    )
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(250.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        val stroke = 12.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = selectedPhrase,
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$count",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${count % target} / $target",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي التسبيحات المحفوظ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$grandTotal تسبيحة", fontFamily = DisplayArabicFontFamily, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    OutlinedButton(
                        onClick = {
                            count = 0
                            prefs.edit().putInt("count_$selectedPhrase", 0).apply()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصفير الذكر")
                    }
                }
            }
        }
    }
}

@Composable
fun QuranHifzSubScreen(
    onOpenFullMushaf: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val repo = remember { QuranRepository(context) }
    val prefs = remember { context.getSharedPreferences("islamic_hifz_prefs", Context.MODE_PRIVATE) }

    var selectedSurahNum by remember { mutableIntStateOf(prefs.getInt("hifz_surah", 78)) }
    var surahDetail by remember { mutableStateOf<SurahDetail?>(null) }
    var startAyah by remember { mutableIntStateOf(1) }
    var endAyah by remember { mutableIntStateOf(10) }
    var testRecitationMode by remember { mutableStateOf(false) }
    var revealedAyahs by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showSurahPicker by remember { mutableStateOf(false) }

    val memorizedKey = "memorized_${selectedSurahNum}_${startAyah}_${endAyah}"
    var isRangeMemorized by remember(memorizedKey) {
        mutableStateOf(prefs.getBoolean(memorizedKey, false))
    }

    LaunchedEffect(selectedSurahNum) {
        val loaded = repo.getSurahDetail(selectedSurahNum)
        surahDetail = loaded
        val maxAyah = loaded?.ayahs?.size ?: 7
        startAyah = 1
        endAyah = minOf(10, maxAyah)
        revealedAyahs = emptySet()
    }

    val filteredAyahs = remember(surahDetail, startAyah, endAyah) {
        surahDetail?.ayahs?.filter { it.numberInSurah in startAyah..endAyah } ?: emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "حفظ القرآن", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Surah & Range Selector Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("السورة المختارة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "سورة ${surahDetail?.info?.nameAr ?: "..."}",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Button(
                            onClick = { showSurahPicker = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("اختر سورة", fontWeight = FontWeight.Bold)
                        }
                    }

                    val totalAyahs = surahDetail?.ayahs?.size ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (startAyah > 1) startAyah -= 1 },
                            modifier = Modifier.weight(1f)
                        ) { Text("من آية: $startAyah") }
                        OutlinedButton(
                            onClick = { if (startAyah < endAyah) startAyah += 1 },
                            modifier = Modifier.weight(1f)
                        ) { Text("+ من") }
                        OutlinedButton(
                            onClick = { if (endAyah > startAyah) endAyah -= 1 },
                            modifier = Modifier.weight(1f)
                        ) { Text("- إلى") }
                        OutlinedButton(
                            onClick = { if (endAyah < totalAyahs) endAyah += 1 },
                            modifier = Modifier.weight(1f)
                        ) { Text("إلى: $endAyah") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = testRecitationMode,
                            onClick = {
                                testRecitationMode = !testRecitationMode
                                revealedAyahs = emptySet()
                            },
                            label = {
                                Text(
                                    if (testRecitationMode) "وضع التسميع مفعّل (اضغط على الآية للإظهار)" else "تفعيل وضع التسميع لنفسك",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }

            // Ayahs List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAyahs) { ayah ->
                    val isRevealed = !testRecitationMode || revealedAyahs.contains(ayah.numberInSurah)
                    Surface(
                        onClick = {
                            if (testRecitationMode) {
                                revealedAyahs = if (isRevealed) revealedAyahs - ayah.numberInSurah else revealedAyahs + ayah.numberInSurah
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "آية ${ayah.numberInSurah}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                if (testRecitationMode) {
                                    Text(
                                        text = if (isRevealed) "اضغط للإخفاء" else "سمّع ثم اضغط للكشف",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isRevealed) ayah.text else "﴿ • • • • • • • • • • ﴾",
                                fontFamily = if (isRevealed) QuranArabicFontFamily else DisplayArabicFontFamily,
                                fontSize = 22.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (isRevealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isRangeMemorized = !isRangeMemorized
                        prefs.edit().putBoolean(memorizedKey, isRangeMemorized).apply()
                        Toast.makeText(
                            context,
                            if (isRangeMemorized) "تم حفظ إنجاز الحفظ بنجاح!" else "تم إلغاء علامة الحفظ",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRangeMemorized) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(if (isRangeMemorized) Icons.Default.CheckCircle else Icons.Default.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRangeMemorized) "تم الحفظ ✓" else "تأكيد حفظ الآيات", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenFullMushaf,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("المصحف كامل", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSurahPicker) {
        Dialog(
            onDismissRequest = { showSurahPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.75f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اختر السورة للحفظ", fontFamily = DisplayArabicFontFamily, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(repo.allSurahList) { info ->
                            Surface(
                                onClick = {
                                    selectedSurahNum = info.number
                                    prefs.edit().putInt("hifz_surah", info.number).apply()
                                    showSurahPicker = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (info.number == selectedSurahNum) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${info.number}. سورة ${info.nameAr}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${info.numberOfAyahs} آية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerTrackerLogSubScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("islamic_prayer_log_prefs", Context.MODE_PRIVATE) }
    val today = remember { LocalDate.now() }
    val todayStr = today.toString()

    val obligatoryPrayers = remember {
        listOf(
            "fajr" to "صلاة الفجر",
            "dhuhr" to "صلاة الظهر",
            "asr" to "صلاة العصر",
            "maghrib" to "صلاة المغرب",
            "isha" to "صلاة العشاء"
        )
    }

    var completedToday by remember {
        mutableStateOf(
            obligatoryPrayers.mapNotNull { (id, _) ->
                if (prefs.getBoolean("${todayStr}_$id", false)) id else null
            }.toSet()
        )
    }

    val currentStreak = remember(completedToday) {
        var streak = if (completedToday.size == 5) 1 else 0
        var checkDate = today.minusDays(1)
        while (true) {
            val dayDone = obligatoryPrayers.all { (id, _) ->
                prefs.getBoolean("${checkDate}_$id", false)
            }
            if (dayDone) {
                streak += 1
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        streak
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(title = "سجل الصلوات", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Streak Banner
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "سلسلة المحافظة على الصلاة",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Text(
                            text = "$currentStreak يوم متواصل",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${completedToday.size} / 5 اليوم",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 5 Obligatory Prayers Checklist
            obligatoryPrayers.forEach { (id, label) ->
                val checked = completedToday.contains(id)
                Surface(
                    onClick = {
                        val next = !checked
                        prefs.edit().putBoolean("${todayStr}_$id", next).apply()
                        completedToday = if (next) completedToday + id else completedToday - id
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (checked) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (checked) Color(0xFF10B981).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { next ->
                                    prefs.edit().putBoolean("${todayStr}_$id", next).apply()
                                    completedToday = if (next) completedToday + id else completedToday - id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF059669))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (checked) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (checked) "أُدّيت ✓" else "سجّل الآن",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (checked) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Last 7 days summary card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ملخص آخر 7 أيام",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (6 downTo 0).forEach { daysAgo ->
                            val d = today.minusDays(daysAgo.toLong())
                            val doneCount = if (daysAgo == 0) completedToday.size else obligatoryPrayers.count { (id, _) ->
                                prefs.getBoolean("${d}_$id", false)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        doneCount == 5 -> Color(0xFF10B981)
                                        doneCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$doneCount",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (doneCount == 5) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (daysAgo == 0) "اليوم" else "${d.dayOfMonth}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
