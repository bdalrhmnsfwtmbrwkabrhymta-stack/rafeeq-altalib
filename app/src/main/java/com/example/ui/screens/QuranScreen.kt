package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.quran.Ayah
import com.example.data.quran.DownloadedSurahItem
import com.example.data.quran.ImportedQuranAudio
import com.example.data.quran.JuzAudio
import com.example.data.quran.JuzDetail
import com.example.data.quran.JuzInfo
import com.example.data.quran.JuzSection
import com.example.data.quran.SurahDetail
import com.example.data.quran.SurahInfo
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class QuranTab(val titleAr: String, val titleEn: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SURAHS("فهرس السور (١١٤)", "Surahs (114)", Icons.Default.FormatListNumbered),
    JUZ("أجزاء القرآن (٣٠)", "Juz (30)", Icons.Default.AutoStories),
    READER("المصحف الشريف 📖", "Mushaf Reader", Icons.Default.MenuBook),
    OFFLINE_AUDIO("الصوتيات بدون نت ⚡", "Offline Audio", Icons.Default.OfflineBolt),
    AUDIO("تلاوات القراء 🎧", "Reciters", Icons.AutoMirrored.Filled.VolumeUp),
    DOWNLOADS("تنزيل وحفظ الأصوات 📥", "Audio Downloads & Device", Icons.Default.Download),
    KHATMAH("الورد والختمة 📿", "Khatmah", Icons.Default.BookmarkAdded)
}

data class ReciterOption(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val surahUrlTemplate: (Int) -> String,
    val ayahUrlTemplate: (Int, Int) -> String
)

val AVAILABLE_RECITERS = listOf(
    ReciterOption(
        id = "alafasy",
        nameAr = "الشيخ مشاري العفاسي",
        nameEn = "Mishari Alafasy",
        surahUrlTemplate = { num -> "https://server8.mp3quran.net/afs/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Alafasy_128kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    ),
    ReciterOption(
        id = "abdulbasit",
        nameAr = "الشيخ عبد الباسط عبد الصمد (مرتل)",
        nameEn = "Abdulbasit Abdulsamad",
        surahUrlTemplate = { num -> "https://server7.mp3quran.net/basit/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Abdul_Basit_Murattal_192kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    ),
    ReciterOption(
        id = "minshawi",
        nameAr = "الشيخ محمد صديق المنشاوي",
        nameEn = "Mohamed Al-Minshawi",
        surahUrlTemplate = { num -> "https://server10.mp3quran.net/minsh/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Minshawy_Murattal_128kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    ),
    ReciterOption(
        id = "sudais",
        nameAr = "الشيخ عبد الرحمن السديس",
        nameEn = "Abdul Rahman Al-Sudais",
        surahUrlTemplate = { num -> "https://server11.mp3quran.net/sds/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Abdurrahmaan_As-Sudais_192kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    )
)

fun formatArabicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    val str = number.toString()
    val builder = java.lang.StringBuilder()
    for (ch in str) {
        if (ch in '0'..'9') {
            builder.append(arabicDigits[ch - '0'])
        } else {
            builder.append(ch)
        }
    }
    return builder.toString()
}

/**
 * Resolves local raw MP3 resource ID for a given Juz number (1..30).
 * Offline files are mapped for Juz 1 (Al-Fatihah), Juz 29 (Tabarak), and Juz 30 (Amma).
 */
fun findRawAudioForJuz(context: Context, juzNumber: Int): Int? {
    return when (juzNumber) {
        1 -> R.raw.juz_01
        29 -> R.raw.juz_29
        30 -> R.raw.juz_30
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    viewModel: MainViewModel,
    initialTab: QuranTab = QuranTab.SURAHS,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    val allSurahs by viewModel.allSurahs.collectAsState()
    val selectedSurah by viewModel.selectedSurah.collectAsState()
    val selectedJuz by viewModel.selectedJuz.collectAsState()
    val allJuzList = viewModel.allJuzList
    val isQuranLoading by viewModel.isQuranLoading.collectAsState()
    val searchQuery by viewModel.quranSearchQuery.collectAsState()

    var activeTab by remember { mutableStateOf(initialTab) }
    var readerMode by remember { mutableStateOf("SURAH") } // "SURAH" or "JUZ"
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Audio Playback State
    var currentPlayingTitle by remember { mutableStateOf<String?>(null) }
    var currentPlayingJuzNum by remember { mutableStateOf<Int?>(null) }
    var currentPlayingSurahNum by remember { mutableStateOf<Int?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var isAudioLoading by remember { mutableStateOf(false) }
    var audioProgressMs by remember { mutableIntStateOf(0) }
    var audioDurationMs by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var selectedReciterIndex by remember { mutableIntStateOf(0) }

    val emeraldDark = Color(0xFF0D5C3A)
    val emeraldMedium = Color(0xFF1B8A5A)
    val goldAccent = Color(0xFFFFD54F)

    LaunchedEffect(Unit) {
        viewModel.loadQuranData()
    }

    LaunchedEffect(isAudioPlaying) {
        while (isAudioPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    audioProgressMs = player.currentPosition
                    audioDurationMs = player.duration
                }
            }
            delay(500)
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.let { player ->
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        isAudioPlaying = false
        isAudioLoading = false
        currentPlayingTitle = null
        currentPlayingJuzNum = null
        currentPlayingSurahNum = null
        audioProgressMs = 0
    }

    DisposableEffect(Unit) {
        val stopListener: () -> Unit = {
            stopAudio()
        }
        com.example.data.audio.AppAudioManager.registerStopListener(stopListener)
        onDispose {
            com.example.data.audio.AppAudioManager.unregisterStopListener(stopListener)
            stopAudio()
        }
    }

    fun playRawAudio(rawResId: Int, title: String, juzNumber: Int? = null) {
        try {
            if (currentPlayingTitle == title && mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    isAudioPlaying = false
                } else {
                    mediaPlayer?.start()
                    isAudioPlaying = true
                }
                return
            }

            stopAudio()
            isAudioLoading = true
            val player = MediaPlayer.create(context, rawResId).apply {
                setOnCompletionListener {
                    isAudioPlaying = false
                    audioProgressMs = 0
                }
                start()
            }
            mediaPlayer = player
            currentPlayingTitle = title
            currentPlayingJuzNum = juzNumber
            currentPlayingSurahNum = null
            isAudioPlaying = true
            isAudioLoading = false
            audioDurationMs = player.duration
            Toast.makeText(context, "جاري تشغيل: $title 🎧", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            isAudioLoading = false
            Toast.makeText(context, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    fun playJuz(juzNumber: Int) {
        val juzInfo = allJuzList.firstOrNull { it.number == juzNumber } ?: return
        val rawRes = findRawAudioForJuz(context, juzNumber)

        if (rawRes != null) {
            playRawAudio(rawRes, "${juzInfo.nameAr}", juzNumber)
        } else {
            // Stream the first surah of that juz or reciter stream
            val startSurah = juzInfo.startSurahNumber
            val surahName = allSurahs.firstOrNull { it.info.number == startSurah }?.info?.nameAr
                ?: viewModel.allSurahInfoList.firstOrNull { it.number == startSurah }?.nameAr
                ?: "القرآن الكريم"
            val reciter = AVAILABLE_RECITERS.getOrElse(selectedReciterIndex) { AVAILABLE_RECITERS[0] }
            val title = "${juzInfo.nameAr} - سورة $surahName (${reciter.nameAr})"

            stopAudio()
            isAudioLoading = true
            currentPlayingTitle = title
            currentPlayingJuzNum = juzNumber

            val url = reciter.surahUrlTemplate(startSurah)
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener { mp ->
                        isAudioLoading = false
                        isAudioPlaying = true
                        audioDurationMs = mp.duration
                        mp.start()
                        Toast.makeText(context, "جاري الاستماع لـ $title 🎧", Toast.LENGTH_SHORT).show()
                    }
                    setOnCompletionListener {
                        isAudioPlaying = false
                        audioProgressMs = 0
                    }
                    setOnErrorListener { _, _, _ ->
                        isAudioLoading = false
                        isAudioPlaying = false
                        Toast.makeText(context, "تأكد من الاتصال بالإنترنت لتشغيل التلاوة المباشرة", Toast.LENGTH_LONG).show()
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
                isAudioLoading = false
                Toast.makeText(context, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playFilePath(filePath: String, title: String, surahNum: Int? = null, juzNum: Int? = null) {
        if (currentPlayingTitle == title && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isAudioPlaying = false
            } else {
                mediaPlayer?.start()
                isAudioPlaying = true
            }
            return
        }

        stopAudio()
        isAudioLoading = true
        currentPlayingTitle = title
        currentPlayingSurahNum = surahNum
        currentPlayingJuzNum = juzNum

        try {
            val file = File(filePath)
            if (!file.exists()) {
                isAudioLoading = false
                Toast.makeText(context, "الملف الصوتي غير موجود", Toast.LENGTH_SHORT).show()
                return
            }
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    isAudioLoading = false
                    isAudioPlaying = true
                    audioDurationMs = mp.duration
                    mp.start()
                    Toast.makeText(context, "جاري تشغيل: $title 🎧", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    isAudioPlaying = false
                    audioProgressMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    isAudioLoading = false
                    isAudioPlaying = false
                    Toast.makeText(context, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            isAudioLoading = false
            Toast.makeText(context, "حدث خطأ أثناء تشغيل الملف", Toast.LENGTH_SHORT).show()
        }
    }

    fun playSurahStream(surahNumber: Int, surahName: String, reciterIndex: Int = selectedReciterIndex) {
        val reciter = AVAILABLE_RECITERS.getOrElse(reciterIndex) { AVAILABLE_RECITERS[0] }
        val title = "سورة $surahName (${reciter.nameAr})"

        if (currentPlayingSurahNum == surahNumber && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isAudioPlaying = false
            } else {
                mediaPlayer?.start()
                isAudioPlaying = true
            }
            return
        }

        // Check if user has downloaded this surah for this reciter
        val downloadedFile = viewModel.quranAudioManager.getDownloadedSurahFile(reciter.id, surahNumber)
        if (downloadedFile != null) {
            playFilePath(downloadedFile.absolutePath, "$title (أوفلاين ⚡)", surahNum = surahNumber)
            return
        }

        stopAudio()
        isAudioLoading = true
        currentPlayingTitle = title
        currentPlayingSurahNum = surahNumber

        val url = reciter.surahUrlTemplate(surahNumber)
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    isAudioLoading = false
                    isAudioPlaying = true
                    audioDurationMs = mp.duration
                    mp.start()
                    Toast.makeText(context, "جاري الاستماع لـ $title 🎧", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    isAudioPlaying = false
                    audioProgressMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    isAudioLoading = false
                    isAudioPlaying = false
                    Toast.makeText(context, "تأكد من الاتصال بالإنترنت لتشغيل التلاوة المباشرة", Toast.LENGTH_LONG).show()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            isAudioLoading = false
            Toast.makeText(context, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    fun playAyahStream(surahNum: Int, ayahNum: Int, reciterIndex: Int = selectedReciterIndex) {
        val reciter = AVAILABLE_RECITERS.getOrElse(reciterIndex) { AVAILABLE_RECITERS[0] }
        val title = "آية $ayahNum [سورة $surahNum]"
        val url = reciter.ayahUrlTemplate(surahNum, ayahNum)

        stopAudio()
        isAudioLoading = true
        currentPlayingTitle = title

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    isAudioLoading = false
                    isAudioPlaying = true
                    audioDurationMs = mp.duration
                    mp.start()
                }
                setOnCompletionListener {
                    isAudioPlaying = false
                    audioProgressMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    isAudioLoading = false
                    isAudioPlaying = false
                    Toast.makeText(context, "تأكد من الاتصال بالإنترنت لتشغيل صوت الآية", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            isAudioLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "The Holy Quran (30 Juz)" else "المصحف الشريف (٣٠ جزءاً كاملاً)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEnglish) "30 Juz • Audio Recitations • 114 Surahs" else "٣٠ جزءاً مباركاً • تلاوات مدمجة ومباشرة • ١١٤ سورة",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    } else {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = emeraldDark,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrollable Tab Header
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = emeraldDark,
                contentColor = Color.White,
                edgePadding = 8.dp,
                divider = {}
            ) {
                QuranTab.values().forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (activeTab == tab) goldAccent else Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) tab.titleEn else tab.titleAr,
                                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == tab) goldAccent else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    )
                }
            }

            // Global Mini Player if Audio is Active
            if (currentPlayingTitle != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF032216),
                    border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f)),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = goldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = currentPlayingTitle ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (isAudioLoading) {
                                        Text(
                                            text = "جاري التحميل والتجهيز...",
                                            color = goldAccent,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        mediaPlayer?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                                isAudioPlaying = false
                                            } else {
                                                it.start()
                                                isAudioPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = goldAccent
                                    )
                                }

                                IconButton(
                                    onClick = { stopAudio() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop", tint = Color.White)
                                }
                            }
                        }

                        if (audioDurationMs > 0) {
                            val maxSlider = audioDurationMs.coerceAtLeast(1).toFloat()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatAudioTime(audioProgressMs),
                                    fontSize = 11.sp,
                                    color = goldAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = audioProgressMs.toFloat().coerceIn(0f, maxSlider),
                                    onValueChange = {
                                        val target = it.toInt()
                                        audioProgressMs = target
                                        try {
                                            mediaPlayer?.seekTo(target)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    valueRange = 0f..maxSlider,
                                    colors = SliderDefaults.colors(
                                        thumbColor = goldAccent,
                                        activeTrackColor = goldAccent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatAudioTime(audioDurationMs),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val current = mediaPlayer?.currentPosition ?: 0
                                            val newPos = (current - 10000).coerceAtLeast(0)
                                            mediaPlayer?.seekTo(newPos)
                                            audioProgressMs = newPos
                                        } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = goldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                IconButton(
                                    onClick = {
                                        mediaPlayer?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                                isAudioPlaying = false
                                            } else {
                                                it.start()
                                                isAudioPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(goldAccent, CircleShape)
                                ) {
                                    Icon(
                                        if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color(0xFF2B1D00),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                IconButton(
                                    onClick = {
                                        try {
                                            val current = mediaPlayer?.currentPosition ?: 0
                                            val total = mediaPlayer?.duration ?: audioDurationMs
                                            val newPos = (current + 10000).coerceAtMost(total)
                                            mediaPlayer?.seekTo(newPos)
                                            audioProgressMs = newPos
                                        } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = goldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (isQuranLoading && selectedJuz == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = emeraldMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isEnglish) "Loading the Holy Quran..." else "جاري تحميل أجزاء المصحف الشريف...",
                                fontSize = 14.sp,
                                color = emeraldMedium
                            )
                        }
                    }
                } else {
                    when (activeTab) {
                        QuranTab.OFFLINE_AUDIO -> OfflineRecitationsTab(
                            viewModel = viewModel,
                            allJuzList = allJuzList,
                            allSurahs = allSurahs,
                            onSelectJuzForReading = { juzNum ->
                                viewModel.selectJuz(juzNum)
                                activeTab = QuranTab.READER
                            },
                            onPlayJuz = { juzNum -> playJuz(juzNum) },
                            onPlayRawAudio = { resId, title -> playRawAudio(resId, title) },
                            currentPlayingJuzNum = currentPlayingJuzNum,
                            currentPlayingTitle = currentPlayingTitle,
                            isAudioPlaying = isAudioPlaying,
                            onStopAudio = { stopAudio() },
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent,
                            context = context
                        )
                        QuranTab.JUZ -> JuzGridListTab(
                            allJuzList = allJuzList,
                            onSelectJuz = { juzNum ->
                                viewModel.selectJuz(juzNum)
                                activeTab = QuranTab.READER
                            },
                            onPlayJuzAudio = { juzNum ->
                                playJuz(juzNum)
                            },
                            currentPlayingJuzNum = currentPlayingJuzNum,
                            isAudioPlaying = isAudioPlaying,
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent,
                            context = context
                        )
                        QuranTab.READER -> MushafUnifiedReaderTab(
                            viewModel = viewModel,
                            readerMode = readerMode,
                            onReaderModeChange = { readerMode = it },
                            selectedSurah = selectedSurah,
                            selectedJuz = selectedJuz,
                            allSurahs = allSurahs,
                            allJuzList = allJuzList,
                            userSettings = userSettings,
                            selectedReciterIndex = selectedReciterIndex,
                            onReciterChange = { selectedReciterIndex = it },
                            onPlayJuz = { juzNum ->
                                playJuz(juzNum)
                            },
                            onPlaySurah = { sNum, sName ->
                                playSurahStream(sNum, sName, selectedReciterIndex)
                            },
                            onPlayAyah = { sNum, aNum ->
                                playAyahStream(sNum, aNum, selectedReciterIndex)
                            },
                            isCurrentAudioPlaying = isAudioPlaying,
                            currentPlayingJuzNum = currentPlayingJuzNum,
                            currentPlayingSurahNum = currentPlayingSurahNum,
                            onSelectSurah = { surahNum ->
                                viewModel.selectSurah(surahNum)
                            },
                            onSelectJuz = { juzNum ->
                                viewModel.selectJuz(juzNum)
                            },
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent,
                            context = context
                        )
                        QuranTab.SURAHS -> SurahsListTab(
                            viewModel = viewModel,
                            allSurahs = allSurahs,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onFilterChange = { selectedFilter = it },
                            onSelectSurah = { info ->
                                viewModel.selectSurah(info.number)
                                viewModel.selectJuz(info.juz)
                                readerMode = "SURAH"
                                activeTab = QuranTab.READER
                            },
                            onPlaySurah = { info ->
                                playSurahStream(info.number, info.nameAr, selectedReciterIndex)
                            },
                            currentlyPlayingSurahNum = currentPlayingSurahNum,
                            isAudioPlaying = isAudioPlaying,
                            isEnglish = isEnglish,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent
                        )
                        QuranTab.AUDIO -> AudioRecitationsTab(
                            viewModel = viewModel,
                            allSurahs = allSurahs,
                            allJuzList = allJuzList,
                            selectedReciterIndex = selectedReciterIndex,
                            onReciterChange = { selectedReciterIndex = it },
                            onPlayJuz = { jNum -> playJuz(jNum) },
                            onPlayRawAudio = { resId, title -> playRawAudio(resId, title) },
                            onPlaySurahStream = { sNum, sName -> playSurahStream(sNum, sName, selectedReciterIndex) },
                            currentPlayingTitle = currentPlayingTitle,
                            isPlaying = isAudioPlaying,
                            onStop = { stopAudio() },
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent,
                            context = context
                        )
                        QuranTab.DOWNLOADS -> AudioDownloadsManagerTab(
                            viewModel = viewModel,
                            allSurahs = allSurahs,
                            onPlayFilePath = { filePath, title, surahNum ->
                                playFilePath(filePath, title, surahNum = surahNum)
                            },
                            currentPlayingTitle = currentPlayingTitle,
                            isPlaying = isAudioPlaying,
                            onStopAudio = { stopAudio() },
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent
                        )
                        QuranTab.KHATMAH -> KhatmahTab(
                            viewModel = viewModel,
                            userSettings = userSettings,
                            onNavigateToJuz = { jNum ->
                                viewModel.selectJuz(jNum)
                                activeTab = QuranTab.READER
                            },
                            isEnglish = isEnglish,
                            emeraldDark = emeraldDark,
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Primary View of 30 Juz cards.
 */
@Composable
fun JuzGridListTab(
    allJuzList: List<JuzInfo>,
    onSelectJuz: (Int) -> Unit,
    onPlayJuzAudio: (Int) -> Unit,
    currentPlayingJuzNum: Int?,
    isAudioPlaying: Boolean,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Hero Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = emeraldDark),
            border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.4f))
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
                        text = if (isEnglish) "Holy Quran by 30 Juz" else "أجزاء القرآن الكريم (٣٠ جزءاً)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = goldAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish) "Select any Juz to read and listen to the recitation." else "اختر أي جزء للقراءة والاستماع للتلاوة الصوتية الخاصة به.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = goldAccent.copy(alpha = 0.2f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "٣٠",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = goldAccent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 30 Juz List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(allJuzList, key = { it.number }) { juzInfo ->
                val isPlayingThis = currentPlayingJuzNum == juzInfo.number && isAudioPlaying
                val hasLocalRaw = findRawAudioForJuz(context, juzInfo.number) != null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectJuz(juzInfo.number) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isPlayingThis) emeraldMedium else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPlayingThis) emeraldMedium else emeraldMedium.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp),
                                border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${juzInfo.number}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isPlayingThis) Color.White else emeraldMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEnglish) juzInfo.nameEn else juzInfo.nameAr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isPlayingThis) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🔊", fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEnglish) juzInfo.descriptionEn else juzInfo.descriptionAr,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEnglish) "Starts Page ${juzInfo.startPage}" else "يبدأ من صفحة ${juzInfo.startPage}",
                                        fontSize = 11.sp,
                                        color = emeraldMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (hasLocalRaw) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = goldAccent.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "صوت مدمج 🎧",
                                                fontSize = 9.sp,
                                                color = Color(0xFF704E00),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Audio button & Read button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onPlayJuzAudio(juzInfo.number) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(emeraldMedium.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Audio",
                                    tint = emeraldMedium,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { onSelectJuz(juzInfo.number) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Read Juz",
                                    tint = emeraldMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unified Mushaf Reader: Supports reading by Surah (114 Surahs) or by Juz (30 Juz) offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafUnifiedReaderTab(
    viewModel: MainViewModel,
    readerMode: String, // "SURAH" or "JUZ"
    onReaderModeChange: (String) -> Unit,
    selectedSurah: SurahDetail?,
    selectedJuz: JuzDetail?,
    allSurahs: List<SurahDetail>,
    allJuzList: List<JuzInfo>,
    userSettings: com.example.data.preferences.UserSettings,
    selectedReciterIndex: Int,
    onReciterChange: (Int) -> Unit,
    onPlayJuz: (Int) -> Unit,
    onPlaySurah: (Int, String) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    isCurrentAudioPlaying: Boolean,
    currentPlayingJuzNum: Int?,
    currentPlayingSurahNum: Int?,
    onSelectSurah: (Int) -> Unit,
    onSelectJuz: (Int) -> Unit,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color,
    context: Context
) {
    var fontSizeSp by remember { mutableFloatStateOf(userSettings.quranFontSizeSp.toFloat()) }
    var showSurahPicker by remember { mutableStateOf(false) }
    var showJuzPicker by remember { mutableStateOf(false) }

    val effectiveSurah = selectedSurah ?: allSurahs.firstOrNull()
    val effectiveJuz = selectedJuz ?: allJuzList.firstOrNull()?.let { info ->
        allSurahs.firstOrNull()?.let { s ->
            JuzDetail(info, listOf(JuzSection(s.info, s.ayahs)))
        }
    }

    if (effectiveSurah == null && readerMode == "SURAH") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = emeraldMedium)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Mode Switcher Bar (Surahs vs Juz)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = readerMode == "SURAH",
                        onClick = { onReaderModeChange("SURAH") },
                        label = { Text("قراءة بالسورة (١١٤)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = readerMode == "JUZ",
                        onClick = { onReaderModeChange("JUZ") },
                        label = { Text("قراءة بالجزء (٣٠)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (fontSizeSp > 16f) {
                                fontSizeSp -= 2f
                                viewModel.setQuranFontSize(fontSizeSp)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Text("${fontSizeSp.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold)

                    IconButton(
                        onClick = {
                            if (fontSizeSp < 38f) {
                                fontSizeSp += 2f
                                viewModel.setQuranFontSize(fontSizeSp)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = emeraldMedium)
                    }
                }
            }
        }

        // Sub Navigation Bar for Selected Surah / Juz
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            if (readerMode == "SURAH" && effectiveSurah != null) {
                val sInfo = effectiveSurah.info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (sInfo.number > 1) onSelectSurah(sInfo.number - 1)
                        },
                        enabled = sInfo.number > 1,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldMedium.copy(alpha = 0.15f),
                            contentColor = emeraldMedium,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isEnglish) "Prev" else "السابقة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedCard(
                        onClick = { showSurahPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = emeraldMedium.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "سورة ${sInfo.nameAr}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = emeraldMedium
                                )
                                Text(
                                    text = "سورة ${sInfo.number} • ${sInfo.revelationType} • ${sInfo.numberOfAyahs} آية • جزء ${sInfo.juz}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = emeraldMedium)
                        }
                    }

                    Button(
                        onClick = {
                            if (sInfo.number < 114) onSelectSurah(sInfo.number + 1)
                        },
                        enabled = sInfo.number < 114,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldMedium.copy(alpha = 0.15f),
                            contentColor = emeraldMedium,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = if (isEnglish) "Next" else "التالية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            } else if (effectiveJuz != null) {
                val jInfo = effectiveJuz.info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (jInfo.number > 1) onSelectJuz(jInfo.number - 1)
                        },
                        enabled = jInfo.number > 1,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldMedium.copy(alpha = 0.15f),
                            contentColor = emeraldMedium,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isEnglish) "Prev Juz" else "الجزء السابق", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedCard(
                        onClick = { showJuzPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = emeraldMedium.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = jInfo.nameAr,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = emeraldMedium
                                )
                                Text(
                                    text = "الجزء ${jInfo.number} من ٣٠ • ص ${jInfo.startPage}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = emeraldMedium)
                        }
                    }

                    Button(
                        onClick = {
                            if (jInfo.number < 30) onSelectJuz(jInfo.number + 1)
                        },
                        enabled = jInfo.number < 30,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldMedium.copy(alpha = 0.15f),
                            contentColor = emeraldMedium,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = if (isEnglish) "Next Juz" else "الجزء التالي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Surah Picker Dialog
        if (showSurahPicker) {
            AlertDialog(
                onDismissRequest = { showSurahPicker = false },
                title = { Text("اختر سورة للقراءة (١١٤ سورة) 📖", fontWeight = FontWeight.Bold, color = emeraldMedium) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(allSurahs, key = { "picker_surah_${it.info.number}" }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectSurah(item.info.number)
                                        showSurahPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = emeraldDark,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${item.info.number}", color = goldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("سورة ${item.info.nameAr}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("${item.info.numberOfAyahs} آية • جزء ${item.info.juz}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSurahPicker = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }

        // Juz Picker Dialog
        if (showJuzPicker) {
            AlertDialog(
                onDismissRequest = { showJuzPicker = false },
                title = { Text("اختر جزءاً للقراءة (٣٠ جزءاً) 📑", fontWeight = FontWeight.Bold, color = emeraldMedium) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(allJuzList, key = { "picker_juz_${it.number}" }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectJuz(item.number)
                                        showJuzPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = emeraldDark,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${item.number}", color = goldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(item.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("ص ${item.startPage}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showJuzPicker = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }

        // Main Content Area
        if (readerMode == "SURAH" && effectiveSurah != null) {
            val sInfo = effectiveSurah.info
            val isPlayingThisSurah = currentPlayingSurahNum == sInfo.number && isCurrentAudioPlaying

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Surah Decorative Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = emeraldDark),
                        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "سورة ${sInfo.nameAr}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = goldAccent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "سورة رقم ${sInfo.number} • ${sInfo.revelationType} • آياتها: ${sInfo.numberOfAyahs} • الجزء: ${sInfo.juz} • الصفحة: ${sInfo.page}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Play Audio Action
                            Button(
                                onClick = { onPlaySurah(sInfo.number, sInfo.nameAr) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = goldAccent,
                                    contentColor = Color(0xFF2B1D00)
                                ),
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Icon(
                                    if (isPlayingThisSurah) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlayingThisSurah) "إيقاف التلاوة ⏸️" else "استمع لتلاوة سورة ${sInfo.nameAr} 🎧",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Bismillah Banner (except At-Tawbah 9, and for Al-Fatihah 1 verse 1 is Bismillah)
                if (sInfo.number != 9 && sInfo.number != 1) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = emeraldMedium.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.2f))
                        ) {
                            Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldDark
                                )
                            }
                        }
                    }
                }

                // Ayahs of Surah
                items(effectiveSurah.ayahs, key = { "ayah_${sInfo.number}_${it.numberInSurah}" }) { ayah ->
                    AyahCard(
                        ayah = ayah,
                        surahInfo = sInfo,
                        fontSizeSp = fontSizeSp,
                        onPlayAyahAudio = { onPlayAyah(sInfo.number, ayah.numberInSurah) },
                        onBookmark = {
                            viewModel.saveQuranBookmark(sInfo.number, sInfo.nameAr, ayah.numberInSurah)
                            Toast.makeText(context, "تم حفظ العلامة عند الآية ${ayah.numberInSurah} [سورة ${sInfo.nameAr}] 🔖", Toast.LENGTH_SHORT).show()
                        },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val numStr = formatArabicDigits(ayah.numberInSurah)
                            val clip = ClipData.newPlainText("Ayah", "${ayah.text} ($numStr) [سورة ${sInfo.nameAr}]")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ الآية الكريمة ✅", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val numStr = formatArabicDigits(ayah.numberInSurah)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${ayah.text} ($numStr)\n[سورة ${sInfo.nameAr} - آية ${ayah.numberInSurah}]")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الآية"))
                        },
                        emeraldMedium = emeraldMedium,
                        goldAccent = goldAccent
                    )
                }

                // Footer for completing Surah
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (sInfo.number > 1) {
                                OutlinedButton(
                                    onClick = { onSelectSurah(sInfo.number - 1) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("السورة السابقة")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    viewModel.incrementQuranPagesRead()
                                    Toast.makeText(context, "تم تسجيل قراءة سورة ${sInfo.nameAr} بنجاح (+5 نقاط) 🌟", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium)
                            ) {
                                Text("أتممت قراءة السورة ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (sInfo.number < 114) {
                                OutlinedButton(
                                    onClick = { onSelectSurah(sInfo.number + 1) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("السورة التالية")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }
        } else if (effectiveJuz != null) {
            val jInfo = effectiveJuz.info
            val isPlayingThisJuz = currentPlayingJuzNum == jInfo.number && isCurrentAudioPlaying

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Juz Header Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = emeraldDark),
                        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isEnglish) jInfo.nameEn else jInfo.nameAr,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = goldAccent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEnglish) jInfo.descriptionEn else jInfo.descriptionAr,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Play Audio Action
                            Button(
                                onClick = { onPlayJuz(jInfo.number) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = goldAccent,
                                    contentColor = Color(0xFF2B1D00)
                                ),
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Icon(
                                    if (isPlayingThisJuz) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlayingThisJuz) "إيقاف التلاوة ⏸️" else "استمع لتلاوة الجزء كاملاً 🎧",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Juz Sections (Surahs in this Juz)
                effectiveJuz.sections.forEach { section ->
                    val sInfo = section.surahInfo

                    // Surah Section Title Banner
                    item(key = "juz_section_header_${sInfo.number}_${jInfo.number}") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = emeraldMedium.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = emeraldMedium,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${sInfo.number}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "سورة ${sInfo.nameAr}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = emeraldDark
                                    )
                                }
                                Text(
                                    text = "${sInfo.revelationType} • ${sInfo.numberOfAyahs} آية",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Bismillah if section starts at ayah 1 (and not At-Tawbah or Al-Fatihah)
                    if (section.ayahs.firstOrNull()?.numberInSurah == 1 && sInfo.number != 9 && sInfo.number != 1) {
                        item(key = "bismillah_${sInfo.number}_${jInfo.number}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldDark
                                )
                            }
                        }
                    }

                    // Ayahs
                    items(section.ayahs, key = { "ayah_${sInfo.number}_${it.numberInSurah}_juz_${jInfo.number}" }) { ayah ->
                        AyahCard(
                            ayah = ayah,
                            surahInfo = sInfo,
                            fontSizeSp = fontSizeSp,
                            onPlayAyahAudio = { onPlayAyah(sInfo.number, ayah.numberInSurah) },
                            onBookmark = {
                                viewModel.saveQuranBookmark(sInfo.number, sInfo.nameAr, ayah.numberInSurah)
                                Toast.makeText(context, "تم حفظ العلامة عند الآية ${ayah.numberInSurah} [سورة ${sInfo.nameAr}] 🔖", Toast.LENGTH_SHORT).show()
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val numStr = formatArabicDigits(ayah.numberInSurah)
                                val clip = ClipData.newPlainText("Ayah", "${ayah.text} ($numStr) [سورة ${sInfo.nameAr}]")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الآية الكريمة ✅", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                val numStr = formatArabicDigits(ayah.numberInSurah)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${ayah.text} ($numStr)\n[سورة ${sInfo.nameAr} - آية ${ayah.numberInSurah}]")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الآية"))
                            },
                            emeraldMedium = emeraldMedium,
                            goldAccent = goldAccent
                        )
                    }
                }

                // Bottom Navigation Footer (To Next Juz)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (jInfo.number > 1) {
                                OutlinedButton(
                                    onClick = { onSelectJuz(jInfo.number - 1) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الجزء ${jInfo.number - 1}")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    viewModel.incrementQuranPagesRead()
                                    Toast.makeText(context, "تم تسجيل قراءة الورد اليومي بنجاح (+10 نقاط) 🌟", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium)
                            ) {
                                Text("أتممت قراءة هذا الجزء ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (jInfo.number < 30) {
                                OutlinedButton(
                                    onClick = { onSelectJuz(jInfo.number + 1) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("الجزء ${jInfo.number + 1}")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AyahCard(
    ayah: Ayah,
    surahInfo: SurahInfo,
    fontSizeSp: Float,
    onPlayAyahAudio: () -> Unit,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    emeraldMedium: Color,
    goldAccent: Color
) {
    val arabicAyahNum = remember(ayah.numberInSurah) { formatArabicDigits(ayah.numberInSurah) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Verse text with formatted arabic numeral
            Text(
                text = "${ayah.text}  ($arabicAyahNum)",
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.85f).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Ayah actions bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ayah location badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = emeraldMedium.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "آية $arabicAyahNum",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = emeraldMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayAyahAudio, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen", tint = emeraldMedium, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onBookmark, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark", tint = emeraldMedium, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = emeraldMedium, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = emeraldMedium, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Tab 3: Surahs List (114 Surahs)
 */
@Composable
fun SurahsListTab(
    viewModel: MainViewModel,
    allSurahs: List<SurahDetail>,
    searchQuery: String,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onSelectSurah: (SurahInfo) -> Unit,
    onPlaySurah: (SurahInfo) -> Unit,
    currentlyPlayingSurahNum: Int?,
    isAudioPlaying: Boolean,
    isEnglish: Boolean,
    emeraldMedium: Color,
    goldAccent: Color
) {
    val surahInfoList = remember(allSurahs) {
        if (allSurahs.isNotEmpty()) allSurahs.map { it.info } else viewModel.allSurahInfoList
    }

    val filteredList = remember(surahInfoList, searchQuery, selectedFilter) {
        surahInfoList.filter { info ->
            val matchesSearch = searchQuery.isBlank() ||
                    info.nameAr.contains(searchQuery.trim(), ignoreCase = true) ||
                    info.nameEn.contains(searchQuery.trim(), ignoreCase = true) ||
                    info.number.toString() == searchQuery.trim()

            val matchesFilter = when (selectedFilter) {
                "MAKKI" -> info.revelationType == "مكية"
                "MADANI" -> info.revelationType == "مدنية"
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setQuranSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(if (isEnglish) "Search Surah name or number..." else "ابحث عن اسم السورة أو رقمها...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = emeraldMedium) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuranSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = emeraldMedium,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { onFilterChange("ALL") },
                label = { Text(if (isEnglish) "All (114)" else "الكل (١١٤)") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "MAKKI",
                onClick = { onFilterChange("MAKKI") },
                label = { Text(if (isEnglish) "Makki 🕋" else "مكية 🕋") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "MADANI",
                onClick = { onFilterChange("MADANI") },
                label = { Text(if (isEnglish) "Madani 🕌" else "مدنية 🕌") },
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Surahs List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(filteredList, key = { it.number }) { info ->
                val isPlayingThis = currentlyPlayingSurahNum == info.number && isAudioPlaying
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSurah(info) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isPlayingThis) emeraldMedium else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Surah Number Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = emeraldMedium.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp),
                                border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${info.number}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = emeraldMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEnglish) "Surah ${info.nameEn}" else "سورة ${info.nameAr}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isPlayingThis) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🔊", fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${info.numberOfAyahs} آية • ${info.revelationType}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "الجزء ${info.juz} • ص ${info.page}",
                                        fontSize = 11.sp,
                                        color = emeraldMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Audio button & Calligraphy Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onPlaySurah(info) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(emeraldMedium.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Surah",
                                    tint = emeraldMedium,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = info.nameAr,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = emeraldMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 4: Audio Recitations Tab (30 Juz audio + Surahs audio + Reciter selection)
 */
@Composable
fun AudioRecitationsTab(
    viewModel: MainViewModel,
    allSurahs: List<SurahDetail>,
    allJuzList: List<JuzInfo>,
    selectedReciterIndex: Int,
    onReciterChange: (Int) -> Unit,
    onPlayJuz: (Int) -> Unit,
    onPlayRawAudio: (Int, String) -> Unit,
    onPlaySurahStream: (Int, String) -> Unit,
    currentPlayingTitle: String?,
    isPlaying: Boolean,
    onStop: () -> Unit,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color,
    context: Context
) {
    var audioSubTab by remember { mutableStateOf("JUZ") } // "JUZ" or "SURAHS"
    val downloadedSurahs by viewModel.downloadedSurahs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.quranDownloadProgress.collectAsStateWithLifecycle()
    val currentReciter = AVAILABLE_RECITERS.getOrElse(selectedReciterIndex) { AVAILABLE_RECITERS[0] }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Reciters Carousel Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldDark),
                border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "Choose Reciter 🎙️" else "اختر القارئ المفضل 🎙️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = goldAccent
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AVAILABLE_RECITERS.indices.toList()) { index ->
                            val reciter = AVAILABLE_RECITERS[index]
                            val isSelected = selectedReciterIndex == index
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) goldAccent else Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onReciterChange(index) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF2B1D00) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) reciter.nameEn else reciter.nameAr,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF2B1D00) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub tab toggle (30 Juz vs 114 Surahs)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = audioSubTab == "JUZ",
                    onClick = { audioSubTab = "JUZ" },
                    label = { Text("تلاوة الأجزاء (٣٠ جزءاً) 🎧") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = audioSubTab == "SURAHS",
                    onClick = { audioSubTab = "SURAHS" },
                    label = { Text("تلاوة السور (١١٤ سورة) 📖") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (audioSubTab == "JUZ") {
            items(allJuzList, key = { "audio_juz_${it.number}" }) { juzInfo ->
                val isPlayingThis = currentPlayingTitle?.contains(juzInfo.nameAr) == true && isPlaying
                val hasLocalRaw = findRawAudioForJuz(context, juzInfo.number) != null

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isPlayingThis) emeraldMedium else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = emeraldMedium.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${juzInfo.number}", fontWeight = FontWeight.Bold, color = emeraldMedium)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = juzInfo.nameAr,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (hasLocalRaw) "صوت مدمج عالي النقاء 🎧" else juzInfo.descriptionAr,
                                    fontSize = 11.sp,
                                    color = if (hasLocalRaw) emeraldMedium else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { onPlayJuz(juzInfo.number) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingThis) goldAccent else emeraldMedium,
                                contentColor = if (isPlayingThis) Color(0xFF2B1D00) else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlayingThis) "إيقاف" else "استمع", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            val surahInfoList = if (allSurahs.isNotEmpty()) allSurahs.map { it.info } else viewModel.allSurahInfoList
            items(surahInfoList, key = { "audio_surah_${it.number}" }) { sInfo ->
                val isPlayingThis = currentPlayingTitle?.contains("سورة ${sInfo.nameAr}") == true && isPlaying
                val isDownloaded = downloadedSurahs.any { it.reciterId == currentReciter.id && it.surahNumber == sInfo.number }
                val downloadKey = "${currentReciter.id}_${sInfo.number}"
                val isDownloading = downloadProgress.containsKey(downloadKey)
                val progressVal = downloadProgress[downloadKey] ?: 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isPlayingThis) emeraldMedium else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDownloaded) Color(0xFF2E7D32).copy(alpha = 0.15f) else emeraldMedium.copy(alpha = 0.12f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${sInfo.number}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDownloaded) Color(0xFF2E7D32) else emeraldMedium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("سورة ${sInfo.nameAr}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (isDownloaded) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "محفوظة 💾",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF2E7D32),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text("${sInfo.numberOfAyahs} آية • ${sInfo.revelationType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Download / Saved Button
                                if (isDownloading) {
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = emeraldMedium,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else if (isDownloaded) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteDownloadedSurahAudio(currentReciter.id, sInfo.number)
                                            Toast.makeText(context, "تم حذف الملف الصوتي من التطبيق", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Downloaded",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val url = currentReciter.surahUrlTemplate(sInfo.number)
                                            viewModel.downloadSurahAudio(
                                                reciterId = currentReciter.id,
                                                reciterNameAr = currentReciter.nameAr,
                                                surahNumber = sInfo.number,
                                                surahNameAr = sInfo.nameAr,
                                                url = url
                                            )
                                            Toast.makeText(context, "جاري تنزيل سورة ${sInfo.nameAr} بصوت ${currentReciter.nameAr} 📥", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download Surah",
                                            tint = emeraldMedium,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Play Button
                                Button(
                                    onClick = { onPlaySurahStream(sInfo.number, sInfo.nameAr) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPlayingThis) goldAccent else emeraldMedium,
                                        contentColor = if (isPlayingThis) Color(0xFF2B1D00) else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlayingThis) "إيقاف" else "استمع", fontSize = 11.sp)
                                }
                            }
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (progressVal / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = goldAccent,
                                trackColor = emeraldMedium.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 5: Khatmah & Daily Quran Goal Tab
 */
@Composable
fun KhatmahTab(
    viewModel: MainViewModel,
    userSettings: com.example.data.preferences.UserSettings,
    onNavigateToJuz: (Int) -> Unit,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color
) {
    val context = LocalContext.current
    val dailyGoal = userSettings.quranDailyPagesGoal
    val pagesToday = userSettings.quranPagesReadToday
    val progress = (pagesToday.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily Reading Goal Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = emeraldDark),
            border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEnglish) "Daily Quran Goal 🌟" else "ورد القراءة والختمة القرآنية 🌟",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = goldAccent
                        )
                        Text(
                            text = if (isEnglish) "$pagesToday of $dailyGoal pages read today" else "قرأت $pagesToday من هدف $dailyGoal صفحات اليوم",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = goldAccent
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = goldAccent,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.incrementQuranPagesRead()
                            Toast.makeText(context, "بارك الله فيك! تم تسجيل قراءة صفحة (+10 نقاط) 🌟", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color(0xFF2B1D00)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ قراءة صفحة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.resetQuranPagesRead()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Text("إعادة ضبط", fontSize = 12.sp)
                    }
                }
            }
        }

        // Bookmark Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, emeraldMedium.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = emeraldMedium.copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = emeraldMedium)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("آخر علامة مرجعية 🔖", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "سورة ${userSettings.quranBookmarkSurahName} (آية ${userSettings.quranBookmarkAyah})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        val surahNum = userSettings.quranBookmarkSurah
                        val sInfo = viewModel.allSurahInfoList.firstOrNull { it.number == surahNum }
                        onNavigateToJuz(sInfo?.juz ?: 1)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium)
                ) {
                    Text("متابعة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 30 Days Khatmah Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "خطة ختم القرآن الكريم في شهر (٣٠ يوماً) 📅",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = emeraldMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "بقراءة جزء واحد يومياً (حوالي ٢٠ صفحة، بمعدل ٤ صفحات بعد كل صلاة مكتوبة)، تختم القرآن الكريم كاملاً كل شهر بإذن الله وتكسب أجراً عظيماً.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dedicated Tab for Offline Quran Recitations (30 Juz & Surahs playback directly from local storage)
 */
@Composable
fun OfflineRecitationsTab(
    viewModel: MainViewModel,
    allJuzList: List<JuzInfo>,
    allSurahs: List<SurahDetail>,
    onSelectJuzForReading: (Int) -> Unit,
    onPlayJuz: (Int) -> Unit,
    onPlayRawAudio: (Int, String) -> Unit,
    currentPlayingJuzNum: Int?,
    currentPlayingTitle: String?,
    isAudioPlaying: Boolean,
    onStopAudio: () -> Unit,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color,
    context: Context
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "AVAILABLE", "FIRST_HALF", "SECOND_HALF"

    val filteredList = remember(searchQuery, selectedFilter, allJuzList) {
        allJuzList.filter { juz ->
            val hasRaw = findRawAudioForJuz(context, juz.number) != null
            val matchesFilter = when (selectedFilter) {
                "AVAILABLE" -> hasRaw
                "FIRST_HALF" -> juz.number in 1..15
                "SECOND_HALF" -> juz.number in 16..30
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                juz.number.toString().contains(q) ||
                    juz.nameAr.contains(q) ||
                    juz.nameEn.lowercase(Locale.ROOT).contains(q) ||
                    juz.descriptionAr.contains(q) ||
                    juz.descriptionEn.lowercase(Locale.ROOT).contains(q)
            }

            matchesFilter && matchesSearch
        }
    }

    val availableCount = remember(allJuzList) {
        allJuzList.count { findRawAudioForJuz(context, it.number) != null }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldDark),
                border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = goldAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Offline Quran Audio (30 Juz)" else "التلاوة الصوتية بدون إنترنت ⚡",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = goldAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEnglish)
                                    "High-clarity local recordings embedded in the app. Play 30 Juz smoothly offline without consuming mobile data."
                                else
                                    "تلاوة صوتية عطرة للأجزاء المدمجة بالهاتف. استمع للأجزاء الـ ٣٠ فورياً وبأعلى نقاء بدون استهلاك لباقة الإنترنت.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 17.sp
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = goldAccent.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Headphones, contentDescription = null, tint = goldAccent, modifier = Modifier.size(26.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "$availableCount / 30 Ready Offline" else "$availableCount / ٣٠ جزء جاهز أوفلاين",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = goldAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "Instant 0s Buffering" else "تشغيل فوري ٠ ثانية",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isEnglish) "Search by Juz number or Surah name..." else "ابحث برقم الجزء أو اسم السورة (مثل: جزء ١، البقرة، يس)...",
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = emeraldMedium)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = emeraldMedium,
                    unfocusedBorderColor = emeraldMedium.copy(alpha = 0.3f)
                )
            )
        }

        // Filter Chips Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("جميع الأجزاء (٣٠)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "AVAILABLE",
                        onClick = { selectedFilter = "AVAILABLE" },
                        label = { Text("المتوفر في الجهاز أوفلاين ⚡") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "FIRST_HALF",
                        onClick = { selectedFilter = "FIRST_HALF" },
                        label = { Text("النصف الأول (١ - ١٥)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "SECOND_HALF",
                        onClick = { selectedFilter = "SECOND_HALF" },
                        label = { Text("النصف الثاني (١٦ - ٣٠)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // 30 Juz Items
        items(filteredList, key = { "offline_juz_${it.number}" }) { juzInfo ->
            val hasRaw = findRawAudioForJuz(context, juzInfo.number) != null
            val isPlayingThis = currentPlayingJuzNum == juzInfo.number && isAudioPlaying

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isPlayingThis) goldAccent else if (hasRaw) emeraldMedium.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isPlayingThis) 3.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Juz Number Badge & Titles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPlayingThis) goldAccent else emeraldDark,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${juzInfo.number}",
                                        color = if (isPlayingThis) Color(0xFF2B1D00) else goldAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = juzInfo.nameAr,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = emeraldMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (hasRaw) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("أوفلاين ⚡", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (isEnglish) juzInfo.descriptionEn else juzInfo.descriptionAr,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // Play/Pause Action Button
                        IconButton(
                            onClick = {
                                if (isPlayingThis) {
                                    onStopAudio()
                                } else {
                                    onPlayJuz(juzInfo.number)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (isPlayingThis) goldAccent else emeraldMedium.copy(alpha = 0.12f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingThis) "إيقاف" else "تشغيل",
                                tint = if (isPlayingThis) Color(0xFF2B1D00) else emeraldMedium,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Action Row (Read Juz / Status)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (hasRaw) "⚡ تلاوة صوتية مدمجة جاهزة" else "🌐 تلاوة صوتية مباشرة",
                            fontSize = 11.sp,
                            color = if (hasRaw) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = { onSelectJuzForReading(juzInfo.number) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = emeraldMedium, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Read Juz" else "قراءة هذا الجزء 📖",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = emeraldMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAudioTime(millis: Int): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

