package com.example.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.UserSettings
import com.example.data.quran.MushafPagePagination
import com.example.data.quran.SurahInfo
import com.example.ui.MainViewModel
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.quran.MushafPageViewerScreen
import com.example.ui.screens.quran.QuranAudioTabContent
import com.example.ui.screens.quran.QuranCalligraphicScreenHeader
import com.example.ui.screens.quran.QuranFourTabBar
import com.example.ui.screens.quran.QuranJuzTabContent
import com.example.ui.screens.quran.QuranKhatmahTabContent
import com.example.ui.screens.quran.QuranMiniAudioPlayer
import com.example.ui.screens.quran.QuranPalette
import com.example.ui.screens.quran.QuranProgressRing
import com.example.ui.screens.quran.formatNumberLocale
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

enum class QuranTab(
    val titleAr: String,
    val titleEn: String,
    val icon: ImageVector
) {
    SURAHS("فهرس السور (١١٤)", "Surahs (114)", Icons.Default.FormatListNumbered),
    JUZ("الأجزاء (٣٠)", "Juz (30)", Icons.Default.AutoStories),
    AUDIO("التلاوات الصوتية", "Recitations", Icons.AutoMirrored.Filled.VolumeUp),
    KHATMAH("الورد والختمة", "Khatmah", Icons.Default.BookmarkAdded)
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
        nameAr = "الشيخ عبد الباسط عبد الصمد",
        nameEn = "Abdulbasit",
        surahUrlTemplate = { num -> "https://server7.mp3quran.net/basit/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Abdul_Basit_Murattal_192kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    ),
    ReciterOption(
        id = "minshawi",
        nameAr = "الشيخ محمد صديق المنشاوي",
        nameEn = "Al-Minshawi",
        surahUrlTemplate = { num -> "https://server10.mp3quran.net/minsh/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Minshawy_Murattal_128kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    ),
    ReciterOption(
        id = "sudais",
        nameAr = "الشيخ عبد الرحمن السديس",
        nameEn = "Al-Sudais",
        surahUrlTemplate = { num -> "https://server11.mp3quran.net/sds/${String.format(Locale.US, "%03d", num)}.mp3" },
        ayahUrlTemplate = { sNum, aNum -> "https://everyayah.com/data/Abdurrahmaan_As-Sudais_192kbps/${String.format(Locale.US, "%03d%03d", sNum, aNum)}.mp3" }
    )
)

fun formatArabicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    val str = number.toString()
    val builder = StringBuilder(str.length)
    for (ch in str) {
        if (ch in '0'..'9') {
            builder.append(arabicDigits[ch - '0'])
        } else {
            builder.append(ch)
        }
    }
    return builder.toString()
}

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
    onFullScreenViewerChanged: (Boolean) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isEnglish = userSettings.appLanguage == "en"

    val allSurahs by viewModel.allSurahs.collectAsStateWithLifecycle()
    val allJuzList = viewModel.allJuzList
    val allSurahInfoList = viewModel.allSurahInfoList
    val searchQuery by viewModel.quranSearchQuery.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(initialTab) }
    var selectedSurahFilter by remember { mutableStateOf("ALL") } // "ALL", "MECCAN", "MEDINAN"
    var mushafStartPage by remember { mutableStateOf<Int?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }

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

    val mushafPages by produceState<List<com.example.data.quran.MushafPage>>(
        initialValue = emptyList(),
        key1 = viewModel.quranRepo
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val fullSurahs = viewModel.quranRepo.getAllSurahs()
            MushafPagePagination.buildPages(fullSurahs, allJuzList)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadQuranData()
    }

    val isMushafViewerOpen = mushafStartPage != null
    LaunchedEffect(isMushafViewerOpen) {
        onFullScreenViewerChanged(isMushafViewerOpen)
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
        val stopListener: () -> Unit = { stopAudio() }
        com.example.data.audio.AppAudioManager.registerStopListener(stopListener)
        onDispose {
            onFullScreenViewerChanged(false)
            com.example.data.audio.AppAudioManager.unregisterStopListener(stopListener)
            stopAudio()
        }
    }

    fun toggleCurrentPlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                isAudioPlaying = false
            } else {
                player.start()
                isAudioPlaying = true
            }
        } catch (_: Exception) {
        }
    }

    fun playRawAudio(rawResId: Int, title: String, juzNumber: Int? = null) {
        try {
            if (currentPlayingTitle == title && mediaPlayer != null) {
                toggleCurrentPlayPause()
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
        } catch (e: Exception) {
            isAudioLoading = false
            Toast.makeText(context, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    fun playFilePath(filePath: String, title: String, surahNum: Int? = null, juzNum: Int? = null) {
        if (currentPlayingTitle == title && mediaPlayer != null) {
            toggleCurrentPlayPause()
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
            isAudioLoading = false
            Toast.makeText(context, "حدث خطأ أثناء تشغيل الملف", Toast.LENGTH_SHORT).show()
        }
    }

    fun playSurahStream(surahNumber: Int, surahName: String, reciterIndex: Int = selectedReciterIndex) {
        val reciter = AVAILABLE_RECITERS.getOrElse(reciterIndex) { AVAILABLE_RECITERS[0] }
        val title = "سورة $surahName (${reciter.nameAr})"

        if (currentPlayingSurahNum == surahNumber && currentPlayingTitle == title && mediaPlayer != null) {
            toggleCurrentPlayPause()
            return
        }

        val downloadedFile = viewModel.quranAudioManager.getDownloadedSurahFile(reciter.id, surahNumber)
        if (downloadedFile != null) {
            playFilePath(downloadedFile.absolutePath, "$title (أوفلاين)", surahNum = surahNumber)
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
            isAudioLoading = false
            Toast.makeText(context, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    fun playJuz(juzNumber: Int) {
        val juzInfo = allJuzList.firstOrNull { it.number == juzNumber } ?: return
        val rawRes = findRawAudioForJuz(context, juzNumber)
        if (rawRes != null) {
            playRawAudio(rawRes, juzInfo.nameAr, juzNumber)
        } else {
            val startSurah = juzInfo.startSurahNumber
            val surahName = allSurahInfoList.firstOrNull { it.number == startSurah }?.nameAr ?: "القرآن الكريم"
            val reciter = AVAILABLE_RECITERS.getOrElse(selectedReciterIndex) { AVAILABLE_RECITERS[0] }
            val title = "${juzInfo.nameAr} - سورة $surahName"

            if (currentPlayingJuzNum == juzNumber && mediaPlayer != null) {
                toggleCurrentPlayPause()
                return
            }

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
                isAudioLoading = false
                Toast.makeText(context, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openMushafAtBookmark() {
        val page = MushafPagePagination.findPageForSurahAyah(
            surahNumber = userSettings.quranBookmarkSurah,
            ayahNumber = userSettings.quranBookmarkAyah,
            pages = mushafPages,
            fallbackSurahInfo = allSurahInfoList
        )
        mushafStartPage = page
    }

    // Full-screen Sequential Mushaf Page Reader Overlay
    val activeStartPage = mushafStartPage
    if (activeStartPage != null) {
        if (mushafPages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = QuranPalette.RoyalBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تجهيز صفحات المصحف الشريف...",
                        fontFamily = ReadexProFontFamily,
                        color = QuranPalette.SlateMuted
                    )
                }
            }
        } else {
            MushafPageViewerScreen(
                pages = mushafPages,
                allSurahInfoList = allSurahInfoList,
                allJuzList = allJuzList,
                initialPageNumber = activeStartPage,
                bookmarkedSurahNumber = userSettings.quranBookmarkSurah,
                bookmarkedAyahNumber = userSettings.quranBookmarkAyah,
                preferredFontSizeSp = userSettings.quranFontSizeSp,
                isEnglish = isEnglish,
                currentPlayingTitle = currentPlayingTitle,
                isAudioPlaying = isAudioPlaying,
                isAudioLoading = isAudioLoading,
                audioProgressMs = audioProgressMs,
                audioDurationMs = audioDurationMs,
                onPlaySurah = { sNum, sName -> playSurahStream(sNum, sName, selectedReciterIndex) },
                onToggleAudioPlayPause = { toggleCurrentPlayPause() },
                onStopAudio = { stopAudio() },
                onSaveBookmark = { sNum, sName, aNum ->
                    viewModel.saveQuranBookmark(sNum, aNum, sName)
                },
                onMarkPageRead = {
                    viewModel.incrementQuranPagesRead()
                    Toast.makeText(context, "تم احتساب صفحة ضمن ورد اليوم", Toast.LENGTH_SHORT).show()
                },
                onExit = { mushafStartPage = null }
            )
        }
    } else {
        // Main Unified Royal Blue & White Quran Hub
        val headerConfig = when (activeTab) {
            QuranTab.SURAHS -> listOf(
                if (isEnglish) "Holy" else "ٱلْقُرْآنُ",
                if (isEnglish) "Quran" else "ٱلْكَرِيمُ",
                if (isEnglish) "30 Blessed Ajza • 114 Surahs" else "٣٠ جزءاً مباركاً • ١١٤ سورة"
            )
            QuranTab.JUZ -> listOf(
                if (isEnglish) "Quran" else "أَجْزَاءُ",
                if (isEnglish) "Ajza (30)" else "ٱلْقُرْآنِ",
                if (isEnglish) "30 Sequential Ajza • 604 Mushaf Pages" else "٣٠ جزءاً مرتبة بالتسلسل • ٦٠٤ صفحات"
            )
            QuranTab.AUDIO -> listOf(
                if (isEnglish) "Audio" else "التِّلَاوَاتُ",
                if (isEnglish) "Recitations" else "الصَّوْتِيَّةُ",
                if (isEnglish) "Listen & download Surahs and Ajza offline" else "استمع وحمّل السور والأجزاء بدون إنترنت"
            )
            QuranTab.KHATMAH -> listOf(
                if (isEnglish) "Daily" else "الْوِرْدُ",
                if (isEnglish) "Khatmah" else "وَالْخَتْمَةُ",
                if (isEnglish) "Track your daily reading goal and bookmarks" else "نظّم وردك اليومي وتابع ختمة القرآن الكريم"
            )
        }
        val titleFirst = headerConfig[0]
        val titleSecond = headerConfig[1]
        val headerSubtitle = headerConfig[2]
        val firstWordBlue = activeTab == QuranTab.SURAHS || activeTab == QuranTab.AUDIO

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QuranPalette.Background)
            .testTag("quran_hub_root")
    ) {
        QuranCalligraphicScreenHeader(
            firstWord = titleFirst,
            secondWord = titleSecond,
            firstWordBlue = firstWordBlue,
            subtitle = headerSubtitle,
            isEnglish = isEnglish,
            onNavigateBack = onNavigateBack,
            onOpenDownloads = { activeTab = QuranTab.AUDIO },
            onOpenSettings = { showSettingsSheet = true }
        )

        QuranFourTabBar(
            activeTab = activeTab,
            isEnglish = isEnglish,
            onSelectTab = { activeTab = it }
        )

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                QuranTab.SURAHS -> {
                    val bookmarkPage = remember(
                        userSettings.quranBookmarkSurah,
                        userSettings.quranBookmarkAyah,
                        mushafPages,
                        allSurahInfoList
                    ) {
                        MushafPagePagination.findPageForSurahAyah(
                            surahNumber = userSettings.quranBookmarkSurah,
                            ayahNumber = userSettings.quranBookmarkAyah,
                            pages = mushafPages,
                            fallbackSurahInfo = allSurahInfoList
                        )
                    }
                    QuranSurahsHubContent(
                        allSurahInfoList = allSurahInfoList,
                        userSettings = userSettings,
                        bookmarkPage = bookmarkPage,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.quranSearchQuery.value = it },
                        selectedFilter = selectedSurahFilter,
                        onSelectFilter = { selectedSurahFilter = it },
                        isEnglish = isEnglish,
                        currentPlayingSurahNum = currentPlayingSurahNum,
                        isAudioPlaying = isAudioPlaying,
                        onOpenSurahPage = { surah ->
                            val targetPage = MushafPagePagination.findPageForSurah(
                                surahNumber = surah.number,
                                pages = mushafPages,
                                fallbackSurahInfo = allSurahInfoList
                            )
                            mushafStartPage = targetPage
                        },
                        onContinueReading = { openMushafAtBookmark() },
                        onPlaySurahAudio = { surah -> playSurahStream(surah.number, surah.nameAr, selectedReciterIndex) }
                    )
                }

                QuranTab.JUZ -> {
                    QuranJuzTabContent(
                        allJuzList = allJuzList,
                        allSurahInfoList = allSurahInfoList,
                        userSettings = userSettings,
                        isEnglish = isEnglish,
                        currentPlayingJuzNum = currentPlayingJuzNum,
                        isAudioPlaying = isAudioPlaying,
                        onOpenJuzPage = { startPage -> mushafStartPage = startPage },
                        onPlayJuzAudio = { juzNum -> playJuz(juzNum) },
                        onContinueDailyWird = { openMushafAtBookmark() }
                    )
                }

                QuranTab.AUDIO -> {
                    QuranAudioTabContent(
                        viewModel = viewModel,
                        allSurahInfoList = allSurahInfoList,
                        audioJuzList = viewModel.quranRepo.audioJuzList,
                        selectedReciterIndex = selectedReciterIndex,
                        onSelectReciter = { selectedReciterIndex = it },
                        currentPlayingTitle = currentPlayingTitle,
                        currentPlayingJuzNum = currentPlayingJuzNum,
                        currentPlayingSurahNum = currentPlayingSurahNum,
                        isAudioPlaying = isAudioPlaying,
                        isEnglish = isEnglish,
                        onPlayJuz = { playJuz(it) },
                        onPlayRawAudio = { resId, title, juzNum -> playRawAudio(resId, title, juzNum) },
                        onPlaySurah = { sNum, sName, rIdx -> playSurahStream(sNum, sName, rIdx) },
                        onPlayFilePath = { path, title, sNum, jNum -> playFilePath(path, title, sNum, jNum) }
                    )
                }

                QuranTab.KHATMAH -> {
                    QuranKhatmahTabContent(
                        userSettings = userSettings,
                        todayVerse = remember { viewModel.getTodayQuranVerse() },
                        isEnglish = isEnglish,
                        onContinueBookmark = { openMushafAtBookmark() },
                        onIncrementPageRead = { viewModel.incrementQuranPagesRead() },
                        onResetPagesRead = { viewModel.resetQuranPagesRead() }
                    )
                }
            }
        }

        // Persistent Mini Audio Player when playing outside Mushaf viewer
        if (currentPlayingTitle != null || isAudioLoading) {
            QuranMiniAudioPlayer(
                title = currentPlayingTitle ?: "التلاوة الصوتية",
                isPlaying = isAudioPlaying,
                isLoading = isAudioLoading,
                progressMs = audioProgressMs,
                durationMs = audioDurationMs,
                onTogglePlayPause = { toggleCurrentPlayPause() },
                onStop = { stopAudio() }
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "إعدادات المصحف والقراءة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = QuranPalette.NavyInk
                )

                Text(
                    text = "حجم الخط العثماني الأساسي: ${formatArabicDigits(userSettings.quranFontSizeSp)} نقطة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = QuranPalette.SlateBody
                )

                Slider(
                    value = userSettings.quranFontSizeSp.coerceIn(19, 27).toFloat(),
                    onValueChange = { viewModel.setQuranFontSize(it.roundToInt()) },
                    valueRange = 19f..27f,
                    colors = SliderDefaults.colors(
                        thumbColor = QuranPalette.RoyalBlue,
                        activeTrackColor = QuranPalette.RoyalBlue,
                        inactiveTrackColor = QuranPalette.RoyalBlueTint
                    )
                )

                Button(
                    onClick = {
                        showSettingsSheet = false
                        mushafStartPage = 1
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuranPalette.RoyalBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فتح المصحف من الصفحة الأولى",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun QuranSurahsHubContent(
    allSurahInfoList: List<SurahInfo>,
    userSettings: UserSettings,
    bookmarkPage: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    isEnglish: Boolean,
    currentPlayingSurahNum: Int?,
    isAudioPlaying: Boolean,
    onOpenSurahPage: (SurahInfo) -> Unit,
    onContinueReading: () -> Unit,
    onPlaySurahAudio: (SurahInfo) -> Unit
) {
    val dailyGoal = userSettings.quranDailyPagesGoal.coerceAtLeast(1)
    val pagesRead = userSettings.quranPagesReadToday
    val progressFraction = (pagesRead.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100f).roundToInt()

    val filteredSurahs = remember(allSurahInfoList, searchQuery, selectedFilter) {
        allSurahInfoList.filter { info ->
            val matchesFilter = when (selectedFilter) {
                "MECCAN" -> info.revelationType == "مكية"
                "MEDINAN" -> info.revelationType == "مدنية"
                else -> true
            }
            val q = searchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                info.nameAr.contains(q, ignoreCase = true) ||
                info.nameEn.contains(q, ignoreCase = true) ||
                info.number.toString() == q
            matchesFilter && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(QuranPalette.Background)
            .testTag("quran_surahs_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Royal Blue Bookmark & Daily Wird Card matching Mockup 1
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuranPalette.HeroGradient)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Last Bookmark" else "آخر علامة مرجعية",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            AutoSizeText(
                                text = if (isEnglish)
                                    "Surah ${userSettings.quranBookmarkSurahName} (Page $bookmarkPage)"
                                else
                                    "سورة ${userSettings.quranBookmarkSurahName} (صفحة ${formatArabicDigits(bookmarkPage)})",
                                maxFontSize = 18.sp,
                                minFontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ReadexProFontFamily,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        QuranProgressRing(
                            progress = progressFraction,
                            percentText = "$progressPercent%",
                            subLabel = if (isEnglish) "Daily" else "ورد اليوم",
                            ringSize = 78.dp,
                            strokeWidth = 6.dp
                        )
                    }

                    // Full-width white pill CTA button "+ متابعة القراءة"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onContinueReading() }
                            .testTag("quran_continue_reading_button"),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "+ Continue Reading" else "+ متابعة القراءة",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = QuranPalette.RoyalBlue
                            )
                        }
                    }
                }
            }
        }

        // Search Bar & Filter Pills
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = if (isEnglish) "Search surah by name or number..." else "ابحث عن اسم السورة أو رقمها...",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.5.sp,
                            color = QuranPalette.SlateMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = QuranPalette.RoyalBlue
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = QuranPalette.RoyalBlue,
                        unfocusedBorderColor = QuranPalette.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quran_search_field")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to if (isEnglish) "All (114)" else "الكل (١١٤)",
                        "MECCAN" to if (isEnglish) "Meccan" else "مكية",
                        "MEDINAN" to if (isEnglish) "Medinan" else "مدنية"
                    ).forEach { (key, label) ->
                        val active = selectedFilter == key
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectFilter(key) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) QuranPalette.RoyalBlue else QuranPalette.Surface,
                            border = if (active) null else BorderStroke(1.dp, QuranPalette.Border)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    color = if (active) Color.White else QuranPalette.SlateBody
                                )
                            }
                        }
                    }
                }
            }
        }

        // All 114 Surahs clean white cards matching Mockup 1
        items(filteredSurahs, key = { it.number }) { surah ->
            val isThisPlaying = isAudioPlaying && currentPlayingSurahNum == surah.number
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenSurahPage(surah) }
                    .testTag("surah_card_${surah.number}"),
                shape = RoundedCornerShape(18.dp),
                color = QuranPalette.Surface,
                border = BorderStroke(
                    width = if (isThisPlaying) 1.5.dp else 1.dp,
                    color = if (isThisPlaying) QuranPalette.RoyalBlue else QuranPalette.Border
                ),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Royal Blue Rounded-Square Surah Number Badge
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(QuranPalette.RoyalBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatNumberLocale(surah.number, isEnglish),
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AutoSizeText(
                            text = if (isEnglish) "Surah ${surah.nameEn}" else "سورة ${surah.nameAr}",
                            maxFontSize = 16.sp,
                            minFontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ReadexProFontFamily,
                            color = QuranPalette.NavyInk,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val meta = if (isEnglish) {
                            "${surah.numberOfAyahs} Ayahs • ${surah.revelationType} • Page ${surah.page}"
                        } else {
                            "${formatArabicDigits(surah.numberOfAyahs)} آيات • ${surah.revelationType} • صفحة ${formatArabicDigits(surah.page)}"
                        }
                        AutoSizeText(
                            text = meta,
                            maxFontSize = 12.5.sp,
                            minFontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = ReadexProFontFamily,
                            color = QuranPalette.SlateMuted,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onPlaySurahAudio(surah) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("surah_play_button_${surah.number}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isThisPlaying) QuranPalette.RoyalBlue else QuranPalette.RoyalBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isEnglish) "Play Surah" else "تشغيل السورة",
                                tint = if (isThisPlaying) Color.White else QuranPalette.RoyalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
