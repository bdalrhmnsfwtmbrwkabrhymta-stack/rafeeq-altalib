package com.example.ui.dialogs

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
import com.example.data.audio.AzanAudioPlayer
import com.example.domain.prayer.AdhanAudioDownloaderManager
import com.example.domain.prayer.OnlineMuezzinItem
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMuezzinBankDialog(
    viewModel: MainViewModel,
    azanAudioPlayer: AzanAudioPlayer,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings by viewModel.userSettings.collectAsState()

    var downloadingVoiceId by remember { mutableStateOf<String?>(null) }
    val downloadProgressMap = remember { mutableStateMapOf<String, Int>() }
    var previewingVoiceId by remember { mutableStateOf<String?>(null) }

    var filterCategory by remember { mutableStateOf("all") }

    val filteredList = remember(filterCategory) {
        when (filterCategory) {
            "harameyn" -> AdhanAudioDownloaderManager.ONLINE_MUEZZIN_LIST.filter { it.id.contains("makkah") || it.id.contains("madina") || it.id.contains("aqsa") }
            "egypt" -> AdhanAudioDownloaderManager.ONLINE_MUEZZIN_LIST.filter { it.id.contains("cairo") || it.id.contains("abdulbasit") || it.id.contains("minshawi") || it.id.contains("hussary") }
            else -> AdhanAudioDownloaderManager.ONLINE_MUEZZIN_LIST
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            azanAudioPlayer.stopAudio()
        }
    }

    AlertDialog(
        onDismissRequest = {
            azanAudioPlayer.stopAudio()
            onDismiss()
        },
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Adhan Audio Bank ☁️" else "بنك أصوات الأذان السحابي ☁️",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnglish) "HQ MP3 Online Download" else "تنزيل جودة عالية 320kbps",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = {
                    azanAudioPlayer.stopAudio()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isEnglish)
                            "Download high-clarity adhan recitals directly to your device. Plays reliably offline at prayer times!"
                        else
                            "نزّل أصوات أذان الحرم المكي، والمدني، والأقصى، والمقيمين بجودة كريستالية نقية لتشغيلها تلقائياً بدون إنترنت عند كل صلاة.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Filter category chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == "all",
                        onClick = { filterCategory = "all" },
                        label = { Text(if (isEnglish) "All 🌐" else "الكل 🌐", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filterCategory == "harameyn",
                        onClick = { filterCategory = "harameyn" },
                        label = { Text(if (isEnglish) "Haramain 🕋" else "الحرمين والأقصى 🕋", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filterCategory == "egypt",
                        onClick = { filterCategory = "egypt" },
                        label = { Text(if (isEnglish) "Egypt 🇪🇬" else "مصر 🇪🇬", fontSize = 11.sp) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Muezzin items list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredList, key = { it.id }) { voice ->
                        val isDownloaded = AdhanAudioDownloaderManager.isDownloaded(context, voice.id) ||
                                AdhanAudioDownloaderManager.isDownloaded(context, voice.titleArabic)
                        val isSelected = userSettings.muezzinVoice == voice.titleArabic
                        val isDownloading = downloadingVoiceId == voice.id
                        val progress = downloadProgressMap[voice.id] ?: 0
                        val isPreviewing = previewingVoiceId == voice.id

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isEnglish) voice.titleEnglish else voice.titleArabic,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = voice.location,
                                                    fontSize = 9.5.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = voice.quality,
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (isEnglish) "ACTIVE 🎙️" else "المفعل 🎙️",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (isDownloading) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progress / 100f },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                        )
                                        Text(
                                            text = if (isEnglish) "Downloading HQ audio... $progress%" else "جاري التحميل بجودة عالية... $progress%",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Preview button
                                    OutlinedButton(
                                        onClick = {
                                            if (isPreviewing) {
                                                azanAudioPlayer.stopAudio()
                                                previewingVoiceId = null
                                            } else {
                                                azanAudioPlayer.stopAudio()
                                                previewingVoiceId = voice.id
                                                val downloadedFile = AdhanAudioDownloaderManager.getDownloadedVoiceFile(context, voice.id)
                                                if (downloadedFile.exists() && downloadedFile.length() > 1024) {
                                                    azanAudioPlayer.playLocalFile(downloadedFile) {
                                                        previewingVoiceId = null
                                                    }
                                                } else {
                                                    azanAudioPlayer.playAudioUrlOrFile(voice.audioUrl) {
                                                        previewingVoiceId = null
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPreviewing) (if (isEnglish) "Stop" else "إيقاف") else (if (isEnglish) "Preview 🎧" else "استماع 🎧"),
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Action button (Download or Select)
                                    Button(
                                        onClick = {
                                            if (!isDownloaded) {
                                                downloadingVoiceId = voice.id
                                                downloadProgressMap[voice.id] = 0
                                                coroutineScope.launch {
                                                    val success = AdhanAudioDownloaderManager.downloadVoice(
                                                        context = context,
                                                        voice = voice,
                                                        onProgress = { p -> downloadProgressMap[voice.id] = p }
                                                    )
                                                    downloadingVoiceId = null
                                                    if (success) {
                                                        viewModel.updateMuezzinVoice(voice.titleArabic)
                                                        Toast.makeText(context, if (isEnglish) "Downloaded & Activated!" else "تم التنزيل والتفعيل بنجاح! 💾", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, if (isEnglish) "Download failed" else "فشل التنزيل، يرجى الفحص والتحقق من الإنترنت ⚠️", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                viewModel.updateMuezzinVoice(voice.titleArabic)
                                                Toast.makeText(context, if (isEnglish) "Activated!" else "تم اختيار وتفعيل المؤذن بنجاح 🎙️", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isDownloading,
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isSelected -> Icons.Default.CheckCircle
                                                isDownloaded -> Icons.Default.Check
                                                else -> Icons.Default.Download
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when {
                                                isSelected -> if (isEnglish) "Active 🎙️" else "مفعل 🎙️"
                                                isDownloaded -> if (isEnglish) "Select 🎙️" else "تفعيل 🎙️"
                                                else -> if (isEnglish) "Download ⬇️" else "تحميل وتفعيل ⬇️"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    azanAudioPlayer.stopAudio()
                    onDismiss()
                }
            ) {
                Text(if (isEnglish) "Close" else "إغلاق")
            }
        }
    )
}
