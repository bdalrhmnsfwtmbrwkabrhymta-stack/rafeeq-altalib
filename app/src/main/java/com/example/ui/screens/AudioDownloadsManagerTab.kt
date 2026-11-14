package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.quran.DownloadedSurahItem
import com.example.data.quran.ImportedQuranAudio
import com.example.data.quran.SurahDetail
import com.example.ui.MainViewModel

@Composable
fun AudioDownloadsManagerTab(
    viewModel: MainViewModel,
    allSurahs: List<SurahDetail>,
    onPlayFilePath: (filePath: String, title: String, surahNum: Int?) -> Unit,
    currentPlayingTitle: String?,
    isPlaying: Boolean,
    onStopAudio: () -> Unit,
    isEnglish: Boolean,
    emeraldDark: Color,
    emeraldMedium: Color,
    goldAccent: Color
) {
    val context = LocalContext.current
    val downloadedSurahs by viewModel.downloadedSurahs.collectAsStateWithLifecycle()
    val importedAudios by viewModel.importedQuranAudios.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.quranDownloadProgress.collectAsStateWithLifecycle()

    var sectionTab by remember { mutableStateOf("ALL") } // "ALL", "DOWNLOADED", "IMPORTED", "DOWNLOAD_NEW"
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importFileName by remember { mutableStateOf("") }
    var importCustomTitle by remember { mutableStateOf("") }
    var importSelectedSurahNumber by remember { mutableStateOf<Int?>(null) }
    var importReciterName by remember { mutableStateOf("تلاوة خاصة / ملف من الهاتف") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            importFileName = uri.lastPathSegment ?: "audio_file.mp3"
            importCustomTitle = importFileName.substringBeforeLast(".")
            showImportDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header Card
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
                                Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Audio Downloads & Device Files 📥" else "تنزيل وحفظ الأصوات على التطبيق 📥",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = goldAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEnglish)
                                    "Download Quran recitations from top reciters or import Quran audio files from your device to listen offline anytime."
                                else
                                    "قم بتنزيل السور بأصوات القراء المفضلين أو استيراد أي ملف صوتي من هاتفك وحفظه بالتطبيق للاستماع له في أي وقت بدون إنترنت.",
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
                                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = goldAccent, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons (Import from Device)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch("audio/*")
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = goldAccent,
                                contentColor = Color(0xFF2B1D00)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Import from Phone" else "استيراد صوت من الهاتف 📂",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${downloadedSurahs.size + importedAudios.size} ملفات محفوظة",
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

        // Section Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = sectionTab == "ALL",
                        onClick = { sectionTab = "ALL" },
                        label = { Text("الكل (${downloadedSurahs.size + importedAudios.size})") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sectionTab == "DOWNLOADED",
                        onClick = { sectionTab = "DOWNLOADED" },
                        label = { Text("تنزيلات القراء (${downloadedSurahs.size}) 📥") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sectionTab == "IMPORTED",
                        onClick = { sectionTab = "IMPORTED" },
                        label = { Text("أصوات الهاتف (${importedAudios.size}) 📱") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sectionTab == "DOWNLOAD_NEW",
                        onClick = { sectionTab = "DOWNLOAD_NEW" },
                        label = { Text("تنزيل سور جديدة ⚡") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldMedium,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // TAB CONTENT
        if (sectionTab == "DOWNLOAD_NEW") {
            item {
                DownloadNewSurahsSection(
                    viewModel = viewModel,
                    allSurahs = allSurahs,
                    downloadedSurahs = downloadedSurahs,
                    downloadProgress = downloadProgress,
                    onPlayFilePath = onPlayFilePath,
                    currentPlayingTitle = currentPlayingTitle,
                    isPlaying = isPlaying,
                    emeraldMedium = emeraldMedium,
                    goldAccent = goldAccent,
                    context = context
                )
            }
        } else {
            // SHOW SAVED AND IMPORTED AUDIOS
            val showDownloads = sectionTab == "ALL" || sectionTab == "DOWNLOADED"
            val showImported = sectionTab == "ALL" || sectionTab == "IMPORTED"

            if (showDownloads && downloadedSurahs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = emeraldMedium, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "السور المنزلة من الإنترنت (${downloadedSurahs.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = emeraldMedium
                        )
                    }
                }

                items(downloadedSurahs, key = { "down_${it.reciterId}_${it.surahNumber}" }) { item ->
                    val isPlayingThis = currentPlayingTitle?.contains("سورة ${item.surahNameAr}") == true &&
                            currentPlayingTitle?.contains(item.reciterNameAr) == true && isPlaying

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPlayingThis) goldAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${item.surahNumber}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "سورة ${item.surahNameAr}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "بصوت: ${item.reciterNameAr} • ${item.formattedSize}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingThis) {
                                            onStopAudio()
                                        } else {
                                            onPlayFilePath(
                                                item.filePath,
                                                "سورة ${item.surahNameAr} (${item.reciterNameAr})",
                                                item.surahNumber
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (isPlayingThis) goldAccent else emeraldMedium.copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (isPlayingThis) Color(0xFF2B1D00) else emeraldMedium,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        viewModel.deleteDownloadedSurahAudio(item.reciterId, item.surahNumber)
                                        Toast.makeText(context, "تم حذف سورة ${item.surahNameAr} من التخزين", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (showImported && importedAudios.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = emeraldMedium, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الملفات الصوتية المستوردة من الهاتف (${importedAudios.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = emeraldMedium
                        )
                    }
                }

                items(importedAudios, key = { "imp_${it.id}" }) { item ->
                    val isPlayingThis = currentPlayingTitle == item.title && isPlaying

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) emeraldMedium.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPlayingThis) goldAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = emeraldMedium.copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = emeraldMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${if (item.surahNumber != null) "مرتبط بسورة ${item.surahNameAr ?: item.surahNumber}" else item.reciterName} • ${item.formattedSize}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingThis) {
                                            onStopAudio()
                                        } else {
                                            onPlayFilePath(item.filePath, item.title, item.surahNumber)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (isPlayingThis) goldAccent else emeraldMedium.copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (isPlayingThis) Color(0xFF2B1D00) else emeraldMedium,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        viewModel.deleteImportedQuranAudio(item.id)
                                        Toast.makeText(context, "تم حذف الملف من التطبيق", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (downloadedSurahs.isEmpty() && importedAudios.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = emeraldMedium,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "لا توجد أصوات محفوظة حالياً",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "يمكنك تنزيل سور القرآن الكريم بأصوات القراء، أو إضافة أي تسجيل أو ملف صوتي من هاتفك وحفظه في التطبيق.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { sectionTab = "DOWNLOAD_NEW" },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تنزيل سور الآن")
                            }
                        }
                    }
                }
            }
        }
    }

    // Import Dialog
    if (showImportDialog && selectedFileUri != null) {
        val uri = selectedFileUri!!
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "حفظ الصوت في التطبيق 📥",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "اختر اسماً للملف الصوتي لحفظه في التطبيق وتشغيله أوفلاين في أي وقت:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importCustomTitle,
                        onValueChange = { importCustomTitle = it },
                        label = { Text("عنوان التلاوة / الملف") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = importReciterName,
                        onValueChange = { importReciterName = it },
                        label = { Text("اسم القارئ / صاحب الصوت") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "الملف الأصلي: $importFileName",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = importCustomTitle.ifBlank { importFileName }
                        viewModel.importQuranAudioFromDevice(
                            uri = uri,
                            customTitle = finalTitle,
                            reciterName = importReciterName,
                            surahNumber = importSelectedSurahNumber,
                            surahNameAr = null,
                            onComplete = { success ->
                                if (success) {
                                    Toast.makeText(context, "تم حفظ الملف الصوتي في التطبيق بنجاح ✅", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تعذر استيراد الملف الصوتي", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        showImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium)
                ) {
                    Text("حفظ في التطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DownloadNewSurahsSection(
    viewModel: MainViewModel,
    allSurahs: List<SurahDetail>,
    downloadedSurahs: List<DownloadedSurahItem>,
    downloadProgress: Map<String, Int>,
    onPlayFilePath: (filePath: String, title: String, surahNum: Int?) -> Unit,
    currentPlayingTitle: String?,
    isPlaying: Boolean,
    emeraldMedium: Color,
    goldAccent: Color,
    context: Context
) {
    var selectedReciterIndex by remember { mutableIntStateOf(0) }
    val currentReciter = AVAILABLE_RECITERS.getOrElse(selectedReciterIndex) { AVAILABLE_RECITERS[0] }
    val surahInfoList = if (allSurahs.isNotEmpty()) allSurahs.map { it.info } else viewModel.allSurahInfoList

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Reciter picker
        Text(
            text = "اختر القارئ لتنزيل السور بصوته:",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = emeraldMedium
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AVAILABLE_RECITERS.indices.toList()) { index ->
                val reciter = AVAILABLE_RECITERS[index]
                val isSelected = selectedReciterIndex == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedReciterIndex = index },
                    label = { Text(reciter.nameAr, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = emeraldMedium,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Surahs list to download
        surahInfoList.forEach { sInfo ->
            val isDownloaded = downloadedSurahs.any { it.reciterId == currentReciter.id && it.surahNumber == sInfo.number }
            val downloadKey = "${currentReciter.id}_${sInfo.number}"
            val isDownloading = downloadProgress.containsKey(downloadKey)
            val progressVal = downloadProgress[downloadKey] ?: 0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDownloaded) Color(0xFF2E7D32).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDownloaded) Color(0xFF2E7D32).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDownloaded) Color(0xFF2E7D32).copy(alpha = 0.15f) else emeraldMedium.copy(alpha = 0.12f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sInfo.number}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isDownloaded) Color(0xFF2E7D32) else emeraldMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("سورة ${sInfo.nameAr}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${sInfo.numberOfAyahs} آية • ${sInfo.revelationType}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (isDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = emeraldMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$progressVal%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = emeraldMedium)
                            }
                        } else if (isDownloaded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("جاهزة أوفلاين", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        viewModel.deleteDownloadedSurahAudio(currentReciter.id, sInfo.number)
                                        Toast.makeText(context, "تم حذف الملف الصوتي", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val url = currentReciter.surahUrlTemplate(sInfo.number)
                                    viewModel.downloadSurahAudio(
                                        reciterId = currentReciter.id,
                                        reciterNameAr = currentReciter.nameAr,
                                        surahNumber = sInfo.number,
                                        surahNameAr = sInfo.nameAr,
                                        url = url
                                    )
                                    Toast.makeText(context, "جاري تنزيل سورة ${sInfo.nameAr} 📥", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldMedium),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تنزيل", fontSize = 11.sp)
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
