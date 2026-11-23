package com.example.download.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.download.data.DownloadSettings
import com.example.download.data.ExtractionResult
import com.example.download.data.MediaQualityOption
import com.example.download.data.PlaylistEntry
import com.example.download.engine.StorageHelper
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelectionBottomSheet(
    video: ExtractionResult.SingleVideo,
    defaultQualityId: String,
    onDismiss: () -> Unit,
    onSelectQuality: (MediaQualityOption) -> Unit
) {
    var showAudioOnly by remember {
        mutableStateOf(defaultQualityId.startsWith("audio"))
    }
    val videoOptions = remember(video) { video.qualities.filter { !it.isAudioOnly } }
    val audioOptions = remember(video) { video.qualities.filter { it.isAudioOnly } }
    val displayed = if (showAudioOnly && audioOptions.isNotEmpty()) audioOptions else videoOptions
    val highestQuality = remember(videoOptions) {
        videoOptions.maxByOrNull { it.resolutionHeight }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 92.dp, height = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = video.platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.durationSeconds > 0) {
                        Text(
                            text = "المدة: ${StorageHelper.formatEta(video.durationSeconds.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (highestQuality != null) {
                Button(
                    onClick = { onSelectQuality(highestQuality) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تحميل بأعلى جودة (${highestQuality.id})")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !showAudioOnly,
                    onClick = { showAudioOnly = false },
                    label = { Text("فيديو (${videoOptions.size})") },
                    leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showAudioOnly,
                    onClick = { showAudioOnly = true },
                    label = { Text("صوت فقط (${audioOptions.size})") },
                    leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayed, key = { it.id }) { option ->
                    val isDefault = option.id == defaultQualityId
                    val sizeText = if (option.estimatedBytes > 0L) {
                        val prefix = if (option.isApproximateSize) "~" else ""
                        "$prefix${StorageHelper.formatBytes(option.estimatedBytes)}"
                    } else {
                        "الحجم يتحدد أثناء التحميل"
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectQuality(option) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDefault) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            width = if (isDefault) 1.5.dp else 1.dp,
                            color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (option.isAudioOnly) Icons.Default.AudioFile else Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = option.labelAr,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = option.subLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isDefault) {
                                    Text(
                                        text = "مقترحة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionBottomSheet(
    playlist: ExtractionResult.Playlist,
    onDismiss: () -> Unit,
    onConfirmBatch: (List<PlaylistEntry>, String) -> Unit
) {
    val entries = remember(playlist) {
        mutableStateListOf<PlaylistEntry>().apply { addAll(playlist.entries) }
    }
    val qualityChoices = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
    var selectedQuality by remember { mutableStateOf("720p") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        for (i in entries.indices) entries[i] = entries[i].copy(selected = true)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("تحديد الكل") }

                OutlinedButton(
                    onClick = {
                        for (i in entries.indices) entries[i] = entries[i].copy(selected = false)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("إلغاء الكل") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                qualityChoices.forEach { q ->
                    FilterChip(
                        selected = selectedQuality == q,
                        onClick = { selectedQuality = q },
                        label = { Text(if (q == "2160p") "4K 2160p" else q) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedQuality == "audio_m4a",
                    onClick = { selectedQuality = "audio_m4a" },
                    label = { Text("صوت M4A") }
                )
                FilterChip(
                    selected = selectedQuality == "audio_mp3",
                    onClick = { selectedQuality = "audio_mp3" },
                    label = { Text("صوت MP3") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries.size) { index ->
                    val item = entries[index]
                    val resolvedEntryThumb = item.thumbnailUrl?.takeIf { it.startsWith("http", true) }
                        ?: Regex("""(?:youtu\.be/|youtube\.com/(?:shorts/|watch\?.*v=))([A-Za-z0-9_-]{6,})""")
                            .find(item.url)?.groupValues?.getOrNull(1)
                            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                entries[index] = item.copy(selected = !item.selected)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = item.selected,
                            onCheckedChange = { checked ->
                                entries[index] = item.copy(selected = checked)
                            }
                        )
                        if (!resolvedEntryThumb.isNullOrBlank()) {
                            AsyncImage(
                                model = resolvedEntryThumb,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 64.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Text(
                            text = "${item.index}. ${item.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val selectedCount = entries.count { it.selected }
            Button(
                onClick = { onConfirmBatch(entries.toList(), selectedQuality) },
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("بدء تحميل ($selectedCount) عنصر")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsBottomSheet(
    settings: DownloadSettings,
    hasSavedCookies: Boolean,
    onDismiss: () -> Unit,
    onUpdateChunkCount: (Int) -> Unit,
    onUpdateWifiOnly: (Boolean) -> Unit,
    onUpdateMaxConcurrent: (Int) -> Unit,
    onUpdateSpeedLimit: (Int) -> Unit,
    onCleanOrphanTemp: () -> Unit,
    onOpenCookieLogin: (String) -> Unit,
    onClearCookies: () -> Unit
) {
    val context = LocalContext.current
    var concurrentSlider by remember(settings.maxConcurrent) { mutableIntStateOf(settings.maxConcurrent) }
    val speedOptions = listOf(0 to "غير محدود", 512 to "512 ك.ب/ث", 1024 to "1 م.ب/ث", 2048 to "2 م.ب/ث", 5120 to "5 م.ب/ث")

    val isIgnoringBattery = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "إعدادات التحميل السريع",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text("عدد أجزاء التسريع المتوازية (Chunks)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12, 16).forEach { count ->
                    FilterChip(
                        selected = settings.chunkCount == count,
                        onClick = { onUpdateChunkCount(count) },
                        label = { Text("$count أجزاء") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("التحميل عبر الواي فاي فقط", fontWeight = FontWeight.SemiBold)
                        Text(
                            "إيقاف مؤقت تلقائي على بيانات الهاتف",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = settings.wifiOnly,
                    onCheckedChange = onUpdateWifiOnly
                )
            }

            Column {
                Text(
                    text = "التنزيلات المتزامنة: $concurrentSlider مهام",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = concurrentSlider.toFloat(),
                    onValueChange = { concurrentSlider = it.roundToInt().coerceIn(1, 5) },
                    onValueChangeFinished = { onUpdateMaxConcurrent(concurrentSlider) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            Text("حد السرعة الأقصى (اختياري)", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speedOptions.take(3).forEach { (kbps, label) ->
                    FilterChip(
                        selected = settings.speedLimitKBps == kbps,
                        onClick = { onUpdateSpeedLimit(kbps) },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speedOptions.drop(3).forEach { (kbps, label) ->
                    FilterChip(
                        selected = settings.speedLimitKBps == kbps,
                        onClick = { onUpdateSpeedLimit(kbps) },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider()

            Text("تسجيل الدخول للمواقع الخاصة (اختياري محلياً)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onOpenCookieLogin("https://m.facebook.com/login") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("فيسبوك")
                }
                OutlinedButton(
                    onClick = { onOpenCookieLogin("https://www.instagram.com/accounts/login/") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إنستغرام")
                }
            }
            if (hasSavedCookies) {
                TextButton(onClick = onClearCookies) {
                    Text("مسح الجلسة المحفوظة محلياً")
                }
            }

            OutlinedButton(
                onClick = onCleanOrphanTemp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تنظيف الملفات المؤقتة التالفة (.part)")
            }

            if (!isIgnoringBattery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("السماح بالتحميل في الخلفية بدون قيود البطارية")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocalCookieLoginDialog(
    loginUrl: String,
    cookiesFile: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 460.dp, max = 640.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تسجيل دخول محلي (محفوظ على جهازك فقط)", fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        exportWebViewCookiesToNetscape(
                            listOf("https://www.facebook.com", "https://m.facebook.com", "https://www.instagram.com"),
                            cookiesFile
                        )
                        onSaved()
                    }) {
                        Text("حفظ وإغلاق")
                    }
                }
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = WebViewClient()
                                loadUrl(loginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogDialog(
    title: String,
    diagnosticText: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل التشخيص — $title") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = diagnosticText.ifBlank { "لا توجد تفاصيل تقنية إضافية." },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                clipboard.setText(AnnotatedString(diagnosticText))
                onDismiss()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("نسخ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

private fun exportWebViewCookiesToNetscape(urls: List<String>, targetFile: File) {
    val cm = CookieManager.getInstance()
    cm.flush()
    val lines = mutableListOf("# Netscape HTTP Cookie File")
    val expiry = (System.currentTimeMillis() / 1000L) + 30L * 24L * 3600L

    urls.forEach { rawUrl ->
        val host = Uri.parse(rawUrl).host ?: return@forEach
        val domain = if (host.startsWith("m.") || host.startsWith("www.")) {
            "." + host.substringAfter('.')
        } else {
            ".$host"
        }
        val cookieStr = cm.getCookie(rawUrl) ?: return@forEach
        cookieStr.split(";").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotBlank()) {
                    lines.add("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
                }
            }
        }
    }
    targetFile.parentFile?.mkdirs()
    targetFile.writeText(lines.joinToString("\n"))
}
