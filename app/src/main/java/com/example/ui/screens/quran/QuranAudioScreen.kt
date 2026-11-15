package com.example.ui.screens.quran

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.quran.JuzAudio
import com.example.data.quran.SurahInfo
import com.example.ui.MainViewModel
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.AVAILABLE_RECITERS
import com.example.ui.screens.findRawAudioForJuz
import com.example.ui.screens.formatArabicDigits
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun QuranAudioTabContent(
    viewModel: MainViewModel,
    allSurahInfoList: List<SurahInfo>,
    audioJuzList: List<JuzAudio>,
    selectedReciterIndex: Int,
    onSelectReciter: (Int) -> Unit,
    currentPlayingTitle: String?,
    currentPlayingJuzNum: Int?,
    currentPlayingSurahNum: Int?,
    isAudioPlaying: Boolean,
    isEnglish: Boolean,
    onPlayJuz: (Int) -> Unit,
    onPlayRawAudio: (rawResId: Int, title: String, juzNumber: Int?) -> Unit,
    onPlaySurah: (surahNumber: Int, surahName: String, reciterIndex: Int) -> Unit,
    onPlayFilePath: (filePath: String, title: String, surahNum: Int?, juzNum: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadedSurahs by viewModel.downloadedSurahs.collectAsStateWithLifecycle()
    val importedAudios by viewModel.importedQuranAudios.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.quranDownloadProgress.collectAsStateWithLifecycle()

    var audioSubFilter by remember { mutableStateOf("JUZ") } // "JUZ", "SURAHS", "OFFLINE"
    var searchQuery by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importFileName by remember { mutableStateOf("") }
    var importCustomTitle by remember { mutableStateOf("") }
    var importReciterName by remember { mutableStateOf("تلاوة محفوظة من الهاتف") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            importFileName = uri.lastPathSegment ?: "quran_audio.mp3"
            importCustomTitle = importFileName.substringBeforeLast(".")
            showImportDialog = true
        }
    }

    val currentReciter = AVAILABLE_RECITERS.getOrElse(selectedReciterIndex) { AVAILABLE_RECITERS[0] }

    val filteredJuzAudios = remember(audioJuzList, searchQuery) {
        if (searchQuery.isBlank()) audioJuzList
        else audioJuzList.filter {
            it.titleAr.contains(searchQuery, ignoreCase = true) ||
                it.titleEn.contains(searchQuery, ignoreCase = true) ||
                it.surahsDesc.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredSurahs = remember(allSurahInfoList, searchQuery) {
        if (searchQuery.isBlank()) allSurahInfoList
        else allSurahInfoList.filter {
            it.nameAr.contains(searchQuery, ignoreCase = true) ||
                it.nameEn.contains(searchQuery, ignoreCase = true) ||
                it.number.toString() == searchQuery.trim()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(QuranPalette.Background)
            .testTag("quran_audio_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Royal Blue Reciter Picker + Offline Downloads Card matching Mockup 5
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuranPalette.HeroGradient)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) "Choose Favorite Reciter" else "اختر القارئ المفضل",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AVAILABLE_RECITERS.forEachIndexed { index, reciter ->
                            val selected = index == selectedReciterIndex
                            Surface(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onSelectReciter(index) }
                                    .testTag("reciter_chip_${reciter.id}"),
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.18f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEnglish) reciter.nameEn else reciter.nameAr.removePrefix("الشيخ ").replace(" (مرتل)", ""),
                                        fontFamily = ReadexProFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        color = if (selected) QuranPalette.RoyalBlue else Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { audioSubFilter = "SURAHS" }
                                .testTag("audio_download_shortcut"),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = QuranPalette.RoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Download Offline" else "تحميل وتنزيل بدون نت",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = QuranPalette.RoyalBlue,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { filePickerLauncher.launch("audio/*") }
                                .testTag("audio_import_device_button"),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Import" else "من الهاتف",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clean White Search Field & Audio Sub-filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (isEnglish) "Search surah or juz..." else "ابحث عن سورة أو جزء...",
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
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("JUZ", "الأجزاء (٣٠)", Icons.Default.Headphones),
                        Triple("SURAHS", "السور (١١٤)", Icons.Default.CloudDownload),
                        Triple("OFFLINE", "بدون إنترنت (${downloadedSurahs.size + importedAudios.size + 3})", Icons.Default.OfflineBolt)
                    ).forEach { (key, label, icon) ->
                        val active = audioSubFilter == key
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { audioSubFilter = key }
                                .testTag("audio_subfilter_$key"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) QuranPalette.RoyalBlue else QuranPalette.Surface,
                            border = if (active) null else BorderStroke(1.dp, QuranPalette.Border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (active) Color.White else QuranPalette.RoyalBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp,
                                    color = if (active) Color.White else QuranPalette.SlateBody,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Content according to active sub-filter
        when (audioSubFilter) {
            "JUZ" -> {
                items(filteredJuzAudios, key = { "juz_audio_${it.juzNumber}" }) { juzAudio ->
                    val hasOfflineRaw = findRawAudioForJuz(context, juzAudio.juzNumber) != null || juzAudio.juzNumber == 0
                    val isPlayingThis = isAudioPlaying && (
                        (juzAudio.juzNumber > 0 && currentPlayingJuzNum == juzAudio.juzNumber) ||
                            (juzAudio.juzNumber == 0 && currentPlayingTitle == juzAudio.titleAr)
                        )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                val rawId = juzAudio.rawResId
                                if (rawId != null) {
                                    onPlayRawAudio(rawId, juzAudio.titleAr, if (juzAudio.juzNumber > 0) juzAudio.juzNumber else null)
                                } else {
                                    onPlayJuz(juzAudio.juzNumber)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = QuranPalette.Surface,
                        border = BorderStroke(
                            width = if (isPlayingThis) 1.5.dp else 1.dp,
                            color = if (isPlayingThis) QuranPalette.RoyalBlue else QuranPalette.Border
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(QuranPalette.RoyalBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (juzAudio.juzNumber == 0) "آ" else formatNumberLocale(juzAudio.juzNumber, isEnglish),
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AutoSizeText(
                                        text = if (isEnglish) juzAudio.titleEn else juzAudio.titleAr,
                                        maxFontSize = 15.sp,
                                        minFontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ReadexProFontFamily,
                                        color = QuranPalette.NavyInk,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (hasOfflineRaw) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = QuranPalette.RoyalBlueSoft
                                        ) {
                                            Text(
                                                text = "بدون نت",
                                                fontFamily = ReadexProFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = QuranPalette.RoyalBlue,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = juzAudio.surahsDesc,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.sp,
                                    color = QuranPalette.SlateMuted,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlayingThis) QuranPalette.RoyalBlue else QuranPalette.RoyalBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingThis) "إيقاف مؤقت" else "تشغيل",
                                    tint = if (isPlayingThis) Color.White else QuranPalette.RoyalBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            "SURAHS" -> {
                items(filteredSurahs, key = { "surah_audio_${it.number}" }) { surah ->
                    val isDownloaded = downloadedSurahs.any {
                        it.reciterId == currentReciter.id && it.surahNumber == surah.number
                    }
                    val dlKey = "${currentReciter.id}_${surah.number}"
                    val progress = downloadProgress[dlKey]
                    val isPlayingThis = isAudioPlaying && currentPlayingSurahNum == surah.number

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlaySurah(surah.number, surah.nameAr, selectedReciterIndex) },
                        shape = RoundedCornerShape(18.dp),
                        color = QuranPalette.Surface,
                        border = BorderStroke(
                            width = if (isPlayingThis) 1.5.dp else 1.dp,
                            color = if (isPlayingThis) QuranPalette.RoyalBlue else QuranPalette.Border
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(QuranPalette.RoyalBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatNumberLocale(surah.number, isEnglish),
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isEnglish) "Surah ${surah.nameEn}" else "سورة ${surah.nameAr}",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = QuranPalette.NavyInk
                                )
                                Text(
                                    text = "${formatArabicDigits(surah.numberOfAyahs)} آية • ${if (isDownloaded) "محملة أوفلاين" else currentReciter.nameAr}",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.sp,
                                    color = if (isDownloaded) QuranPalette.RoyalBlue else QuranPalette.SlateMuted
                                )
                            }

                            if (progress != null && progress in 0..99) {
                                CircularProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = QuranPalette.RoyalBlue
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (isDownloaded) {
                                            viewModel.deleteDownloadedSurahAudio(
                                                reciterId = currentReciter.id,
                                                surahNumber = surah.number,
                                                surahNameAr = surah.nameAr
                                            )
                                            Toast.makeText(context, "تم حذف تحميل سورة ${surah.nameAr}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.downloadSurahAudio(
                                                reciterId = currentReciter.id,
                                                reciterNameAr = currentReciter.nameAr,
                                                surahNumber = surah.number,
                                                surahNameAr = surah.nameAr,
                                                url = currentReciter.surahUrlTemplate(surah.number)
                                            )
                                            Toast.makeText(context, "بدأ تنزيل سورة ${surah.nameAr}...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.CloudDownload,
                                        contentDescription = if (isDownloaded) "محملة" else "تنزيل أوفلاين",
                                        tint = QuranPalette.RoyalBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onPlaySurah(surah.number, surah.nameAr, selectedReciterIndex) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlayingThis) QuranPalette.RoyalBlue else QuranPalette.RoyalBlueSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingThis) "إيقاف" else "تشغيل",
                                        tint = if (isPlayingThis) Color.White else QuranPalette.RoyalBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Offline built-in Juz (1, 29, 30) + Downloaded Surahs + Imported Files
                val builtInOffline = audioJuzList.filter { it.rawResId != null }
                items(builtInOffline, key = { "offline_builtin_${it.juzNumber}" }) { item ->
                    val playing = isAudioPlaying && currentPlayingTitle == item.titleAr
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                item.rawResId?.let { res ->
                                    onPlayRawAudio(res, item.titleAr, if (item.juzNumber > 0) item.juzNumber else null)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = QuranPalette.Surface,
                        border = BorderStroke(1.dp, QuranPalette.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(QuranPalette.RoyalBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = QuranPalette.RoyalBlue)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.titleAr,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = QuranPalette.NavyInk
                                )
                                Text(
                                    text = "مدمجة داخل التطبيق بدون إنترنت",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.sp,
                                    color = QuranPalette.SlateMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (playing) QuranPalette.RoyalBlue else QuranPalette.RoyalBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (playing) Color.White else QuranPalette.RoyalBlue
                                )
                            }
                        }
                    }
                }

                items(downloadedSurahs, key = { "dl_${it.reciterId}_${it.surahNumber}" }) { dl ->
                    val title = "سورة ${dl.surahNameAr} (${dl.reciterNameAr})"
                    val playing = isAudioPlaying && currentPlayingTitle == title
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlayFilePath(dl.filePath, title, dl.surahNumber, null) },
                        shape = RoundedCornerShape(18.dp),
                        color = QuranPalette.Surface,
                        border = BorderStroke(1.dp, QuranPalette.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(QuranPalette.RoyalBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = QuranPalette.NavyInk
                                )
                                Text(
                                    text = "محفوظة أوفلاين • ${dl.formattedSize}",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 11.5.sp,
                                    color = QuranPalette.SlateMuted
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.deleteDownloadedSurahAudio(dl.reciterId, dl.surahNumber, dl.surahNameAr)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = QuranPalette.SlateMuted)
                            }
                        }
                    }
                }

                items(importedAudios, key = { "imp_${it.id}" }) { imp ->
                    val playing = isAudioPlaying && currentPlayingTitle == imp.title
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlayFilePath(imp.filePath, imp.title, imp.surahNumber, null) },
                        shape = RoundedCornerShape(18.dp),
                        color = QuranPalette.Surface,
                        border = BorderStroke(1.dp, QuranPalette.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(QuranPalette.RoyalBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, tint = QuranPalette.RoyalBlue)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = imp.title,
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = QuranPalette.NavyInk
                                )
                                Text(
                                    text = "${imp.reciterName} • ${imp.formattedSize}",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 11.5.sp,
                                    color = QuranPalette.SlateMuted
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteImportedQuranAudio(imp.id) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = QuranPalette.SlateMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog && selectedFileUri != null) {
        val uri = selectedFileUri!!
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = "حفظ تلاوة من الهاتف",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = QuranPalette.NavyInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importCustomTitle,
                        onValueChange = { importCustomTitle = it },
                        label = { Text("عنوان التلاوة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importReciterName,
                        onValueChange = { importReciterName = it },
                        label = { Text("اسم القارئ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importQuranAudioFromDevice(
                            uri = uri,
                            customTitle = importCustomTitle.ifBlank { importFileName },
                            reciterName = importReciterName,
                            surahNumber = null,
                            surahNameAr = null,
                            onComplete = { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) "تم استيراد التلاوة وحفظها بدون نت" else "تعذر استيراد الملف الصوتي",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        showImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuranPalette.RoyalBlue)
                ) {
                    Text("حفظ", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }
}
