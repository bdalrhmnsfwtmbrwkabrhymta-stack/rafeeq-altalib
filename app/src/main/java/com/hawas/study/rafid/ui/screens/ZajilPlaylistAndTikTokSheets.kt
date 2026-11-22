package com.hawas.study.rafid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette

internal enum class ZajilPlaylistQuality(
    val id: String,
    val maxHeight: Int?,
    val isAudioOnly: Boolean,
    val labelAr: String,
    val labelEn: String
) {
    Q1080("pl_1080", 1080, false, "الأفضل حتى 1080p", "Best up to 1080p"),
    Q720("pl_720", 720, false, "الأفضل حتى 720p", "Best up to 720p"),
    Q480("pl_480", 480, false, "الأفضل حتى 480p", "Best up to 480p"),
    AUDIO("pl_audio", null, true, "صوت فقط", "Audio only")
}

internal enum class ZajilAudioPreference(
    val id: String,
    val preferDubbedArabic: Boolean,
    val labelAr: String,
    val labelEn: String
) {
    ORIGINAL("orig", false, "الأصلي", "Original"),
    DUBBED_ARABIC("dub_ar", true, "مدبلج بالعربية إن وُجد", "Arabic dubbed if available")
}

private data class ZajilSelectOption(
    val id: String,
    val label: String
)

private fun playlistItemKey(item: PlaylistEntryItem): String =
    "${item.index}_${item.videoId.ifBlank { item.videoUrl }}"

private fun formatTabTitleClean(option: MediaFormatOption): String {
    return when {
        option.isAudioOnly -> option.containerExt.uppercase()
        else -> option.label
    }
}

private fun prepareTikTokDisplayFormats(
    formats: List<MediaFormatOption>,
    isEnglish: Boolean
): List<MediaFormatOption> {
    val videoFormats = formats.filter { !it.isAudioOnly }
    val audioFormats = formats.filter { it.isAudioOnly }
    if (videoFormats.isEmpty()) return formats

    val sortedVideos = videoFormats.sortedWith(
        compareBy<MediaFormatOption> { opt ->
            val hint = "${opt.id} ${opt.label} ${opt.videoUrl.orEmpty()}".lowercase()
            hint.contains("wmplay") ||
                (hint.contains("watermark") && !hint.contains("no watermark") && !hint.contains("بدون علامة"))
        }
            .thenByDescending { it.estimatedSizeBytes.coerceAtLeast(0L) }
            .thenByDescending { it.resolutionHeight ?: 0 }
            .thenByDescending { it.bitrateKbps ?: 0 }
    )

    val labeledVideos = sortedVideos.mapIndexed { index, opt ->
        val standardLabel = when (index) {
            0 -> if (isEnglish) "1080p HD (No Watermark — Clearer)" else "1080p HD (بدون علامة مائية — الأوضح)"
            1 -> if (isEnglish) "720p (No Watermark)" else "720p (بدون علامة مائية)"
            2 -> "480p"
            else -> if (isEnglish) "360p (Data Saver)" else "360p (توفير البيانات)"
        }
        opt.copy(
            label = standardLabel,
            badgeTagAr = null,
            badgeTagEn = null
        )
    }
    return labeledVideos + audioFormats
}

private fun MediaFormatOption.matchesAudioPreference(pref: ZajilAudioPreference): Boolean {
    return if (pref.preferDubbedArabic) {
        isAutoDubbed ||
            languageCode.startsWith("ar", ignoreCase = true) ||
            languageLabel.contains("مدبلج")
    } else {
        !isAutoDubbed
    }
}

@Composable
fun ZajilPlaylistDownloadContent(
    metadata: ExtractedMediaMetadata,
    onStartDownload: (metadata: ExtractedMediaMetadata, format: MediaFormatOption, saveToStudyFolder: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    val allKeys = remember(metadata) { metadata.playlistItems.map(::playlistItemKey).toSet() }
    var selectedKeys by remember(metadata) { mutableStateOf<Set<String>>(allKeys) }
    var selectedQuality by remember(metadata) { mutableStateOf(ZajilPlaylistQuality.Q1080) }
    var selectedAudio by remember(metadata) { mutableStateOf(ZajilAudioPreference.ORIGINAL) }
    var saveToStudyFolder by remember(metadata) { mutableStateOf(false) }

    var showQualitySheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }

    val selectedItems = remember(metadata, selectedKeys) {
        metadata.playlistItems.filter { playlistItemKey(it) in selectedKeys }
    }
    val selectedCount = selectedItems.size
    val allSelected = selectedKeys.size == allKeys.size && allKeys.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Playlist Title & Author/Count Subtitle
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = metadata.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 27.sp,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("smart_sheet_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEnglish) {
                    "${metadata.authorOrSource}, ${metadata.playlistItems.size} videos"
                } else {
                    "${metadata.authorOrSource}، ${metadata.playlistItems.size} فيديو"
                },
                fontSize = 14.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Selection Counter & Deselect All / Select All Link
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isEnglish) "$selectedCount videos selected" else "حُدّد $selectedCount فيديو",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                modifier = Modifier.testTag("playlist_selected_count")
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        selectedKeys = if (allSelected) emptySet() else allKeys
                    }
                    .padding(horizontal = 8.dp)
                    .testTag("playlist_toggle_select_all")
            ) {
                Text(
                    text = if (allSelected) {
                        if (isEnglish) "Deselect all" else "إلغاء التحديد"
                    } else {
                        if (isEnglish) "Select all" else "تحديد الكل"
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.heroBlue
                )
            }
        }

        // Scrollable Playlist Items Card with Checkbox + Thumbnail + Duration + Title
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 288.dp)
            ) {
                itemsIndexed(
                    items = metadata.playlistItems,
                    key = { _, item -> playlistItemKey(item) }
                ) { idx, item ->
                    val key = playlistItemKey(item)
                    val isChecked = key in selectedKeys
                    val resolvedThumb = item.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: if (metadata.platform == RafidPlatform.YOUTUBE &&
                            item.videoId.isNotBlank() &&
                            !item.videoId.startsWith("item_")
                        ) {
                            "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg"
                        } else {
                            metadata.thumbnailUrl
                        }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 68.dp)
                            .clickable {
                                selectedKeys = if (isChecked) selectedKeys - key else selectedKeys + key
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("playlist_item_${item.index}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.heroBlue,
                                uncheckedColor = palette.textSecondary,
                                checkmarkColor = Color.White
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 98.dp, height = 56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF1E88E5), palette.heroBlueDarkPill)
                                    )
                                ),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Box(
                                modifier = Modifier.matchParentSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            if (!resolvedThumb.isNullOrBlank()) {
                                AsyncImage(
                                    model = resolvedThumb,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            val durText = RafidFormatUtils.formatDuration(item.durationSeconds)
                            if (durText.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = Color.Black.copy(alpha = 0.78f),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text(
                                        text = durText,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.title,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (idx < metadata.playlistItems.lastIndex) {
                        HorizontalDivider(
                            color = palette.borderSubtle,
                            thickness = 0.8.dp
                        )
                    }
                }
            }
        }

        // Quality Selector Row ("الجودة")
        ZajilPickerRow(
            icon = Icons.Outlined.Videocam,
            label = if (isEnglish) "Quality" else "الجودة",
            selectedValue = if (isEnglish) selectedQuality.labelEn else selectedQuality.labelAr,
            testTag = "playlist_quality_picker",
            onClick = { showQualitySheet = true }
        )

        // Audio Track Selector Row ("الصوت")
        ZajilPickerRow(
            icon = Icons.Filled.GraphicEq,
            label = if (isEnglish) "Audio" else "الصوت",
            selectedValue = if (isEnglish) selectedAudio.labelEn else selectedAudio.labelAr,
            testTag = "playlist_audio_picker",
            onClick = { showAudioSheet = true }
        )

        // Study Folder Switch (Preserved Zero-Regression)
        StudyFolderToggleRow(
            checked = saveToStudyFolder,
            onToggle = { saveToStudyFolder = it }
        )

        // Bottom Action Row ("تحميل N فيديو" + "إلغاء")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val filteredMetadata = metadata.copy(playlistItems = selectedItems)
                    val bundleFormat = MediaFormatOption(
                        id = "${selectedQuality.id}_${selectedAudio.id}",
                        label = if (isEnglish) selectedQuality.labelEn else selectedQuality.labelAr,
                        containerExt = if (selectedQuality.isAudioOnly) "m4a" else "mp4",
                        mimeType = if (selectedQuality.isAudioOnly) "audio/mp4" else "video/mp4",
                        resolutionHeight = selectedQuality.maxHeight,
                        bitrateKbps = if (selectedQuality.isAudioOnly) 320 else null,
                        isAudioOnly = selectedQuality.isAudioOnly,
                        isPlaylistBundle = true,
                        isAutoDubbed = selectedAudio.preferDubbedArabic,
                        isOriginalAudio = !selectedAudio.preferDubbedArabic,
                        languageCode = if (selectedAudio.preferDubbedArabic) "ar" else "",
                        languageLabel = if (isEnglish) selectedAudio.labelEn else selectedAudio.labelAr
                    )
                    onStartDownload(filteredMetadata, bundleFormat, saveToStudyFolder)
                },
                enabled = selectedItems.isNotEmpty(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.heroBlue,
                    disabledContainerColor = palette.surfaceVariant,
                    disabledContentColor = palette.textSecondary
                ),
                modifier = Modifier
                    .weight(1.35f)
                    .heightIn(min = 52.dp)
                    .testTag("btn_start_fast_download")
            ) {
                Text(
                    text = if (isEnglish) {
                        "Download Selected ($selectedCount)"
                    } else {
                        "تحميل المحدد ($selectedCount)"
                    },
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedItems.isNotEmpty()) Color.White else palette.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.surfaceVariant,
                    contentColor = palette.textPrimary
                ),
                modifier = Modifier
                    .weight(0.85f)
                    .heightIn(min = 52.dp)
                    .testTag("playlist_btn_cancel")
            ) {
                Text(
                    text = if (isEnglish) "Cancel" else "إلغاء",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }

    if (showQualitySheet) {
        ZajilSelectionModalSheet(
            title = if (isEnglish) "Quality" else "الجودة",
            options = ZajilPlaylistQuality.entries.map {
                ZajilSelectOption(
                    id = it.id,
                    label = if (isEnglish) it.labelEn else it.labelAr
                )
            },
            selectedId = selectedQuality.id,
            onSelect = { chosenId ->
                ZajilPlaylistQuality.entries.firstOrNull { it.id == chosenId }?.let {
                    selectedQuality = it
                }
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false }
        )
    }

    if (showAudioSheet) {
        ZajilSelectionModalSheet(
            title = if (isEnglish) "Audio" else "الصوت",
            options = ZajilAudioPreference.entries.map {
                ZajilSelectOption(
                    id = it.id,
                    label = if (isEnglish) it.labelEn else it.labelAr
                )
            },
            selectedId = selectedAudio.id,
            onSelect = { chosenId ->
                ZajilAudioPreference.entries.firstOrNull { it.id == chosenId }?.let {
                    selectedAudio = it
                }
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false }
        )
    }
}

@Composable
fun ZajilTikTokDownloadContent(
    metadata: ExtractedMediaMetadata,
    onStartDownload: (metadata: ExtractedMediaMetadata, format: MediaFormatOption, saveToStudyFolder: Boolean) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    val normalizedFormats = remember(metadata, isEnglish) {
        prepareTikTokDisplayFormats(metadata.formats, isEnglish)
    }
    val videoFormats = remember(normalizedFormats) { normalizedFormats.filter { !it.isAudioOnly } }
    val audioFormats = remember(normalizedFormats) { normalizedFormats.filter { it.isAudioOnly } }

    var selectedTab by remember(metadata) {
        mutableIntStateOf(if (videoFormats.isNotEmpty()) 0 else 1)
    }
    var selectedAudio by remember(metadata) { mutableStateOf(ZajilAudioPreference.ORIGINAL) }
    var saveToStudyFolder by remember(metadata) { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }

    val tabFormats = if (selectedTab == 0 && videoFormats.isNotEmpty()) videoFormats else audioFormats
    val matchingFormats = remember(tabFormats, selectedAudio) {
        tabFormats.filter { it.matchesAudioPreference(selectedAudio) }
    }

    var selectedFormatId by remember(metadata) {
        mutableStateOf(
            videoFormats.firstOrNull { !it.isAutoDubbed }?.id
                ?: normalizedFormats.firstOrNull()?.id
                ?: ""
        )
    }

    LaunchedEffect(matchingFormats) {
        if (matchingFormats.isNotEmpty() && matchingFormats.none { it.id == selectedFormatId }) {
            selectedFormatId = matchingFormats.first().id
        }
    }

    val activeFormat = matchingFormats.firstOrNull { it.id == selectedFormatId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header preview row matching Zajil screenshot
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.5.sp,
                    lineHeight = 25.sp,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("smart_sheet_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnglish) {
                        "${metadata.sourceDomain}, ${metadata.authorOrSource}"
                    } else {
                        "${metadata.sourceDomain}، ${metadata.authorOrSource}"
                    },
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E88E5), palette.heroBlueDarkPill)
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                if (!metadata.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = metadata.thumbnailUrl,
                        contentDescription = metadata.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
                val durText = RafidFormatUtils.formatDuration(metadata.durationSeconds)
                if (durText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.74f),
                        modifier = Modifier.padding(5.dp)
                    ) {
                        Text(
                            text = durText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Segmented Video / Audio Switcher
        if (videoFormats.isNotEmpty() && audioFormats.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isVideoTab = selectedTab == 0
                    val isAudioTab = selectedTab == 1
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isVideoTab) palette.surface else Color.Transparent,
                        shadowElevation = if (isVideoTab) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("smart_tab_video")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Video" else "فيديو",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                textAlign = TextAlign.Center,
                                color = if (isVideoTab) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAudioTab) palette.surface else Color.Transparent,
                        shadowElevation = if (isAudioTab) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("smart_tab_audio")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Audio Only" else "صوت فقط",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                textAlign = TextAlign.Center,
                                color = if (isAudioTab) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Format Cards matching the Zajil TikTok sheet
        if (matchingFormats.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                matchingFormats.forEach { option ->
                    val isSelected = option.id == selectedFormatId
                    Surface(
                        onClick = { selectedFormatId = option.id },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) palette.heroBlue.copy(alpha = 0.06f) else palette.surface,
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) palette.heroBlue else palette.borderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("format_option_${option.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = palette.heroBlue)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatTabTitleClean(option),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = palette.textPrimary
                                )
                                val badge = if (isEnglish) option.badgeTagEn else option.badgeTagAr
                                if (!badge.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = badge,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.heroBlue
                                    )
                                }
                            }

                            if (option.estimatedSizeBytes > 0L) {
                                Text(
                                    text = RafidFormatUtils.formatFileSize(option.estimatedSizeBytes, isEnglish),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selectable Audio Track Row ("الصوت": "الأصلي" / "مدبلج بالعربية إن وُجد")
        ZajilPickerRow(
            icon = Icons.Filled.GraphicEq,
            label = if (isEnglish) "Audio" else "الصوت",
            selectedValue = if (isEnglish) selectedAudio.labelEn else selectedAudio.labelAr,
            testTag = "tiktok_audio_picker",
            onClick = { showAudioSheet = true }
        )

        // Warning when selected audio variant has no available quality
        if (matchingFormats.isEmpty()) {
            Text(
                text = if (isEnglish) {
                    "This quality is not available with this audio. Choose another."
                } else {
                    "لا توجد هذه الجودة بهذا الصوت. اختر غيرها."
                },
                color = palette.dangerTint,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .testTag("tiktok_audio_unavailable_warning")
            )
        }

        // Study Folder Switch (Preserved Zero-Regression)
        StudyFolderToggleRow(
            checked = saveToStudyFolder,
            onToggle = { saveToStudyFolder = it }
        )

        // Primary CTA button ("تحميل الفيديو" / "تحميل صوت فقط")
        Button(
            onClick = {
                activeFormat?.let { onStartDownload(metadata, it, saveToStudyFolder) }
            },
            enabled = activeFormat != null,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.heroBlue,
                disabledContainerColor = palette.surfaceVariant,
                disabledContentColor = palette.textSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("btn_start_fast_download")
        ) {
            val isAudioAction = selectedTab == 1 || (activeFormat?.isAudioOnly == true)
            val baseText = if (isAudioAction) {
                if (isEnglish) "Download Audio Only" else "تحميل صوت فقط"
            } else {
                if (isEnglish) "Download Video" else "تحميل الفيديو"
            }
            val sizeLabel = if (activeFormat != null && activeFormat.estimatedSizeBytes > 0L) {
                " • ${RafidFormatUtils.formatFileSize(activeFormat.estimatedSizeBytes, isEnglish)}"
            } else {
                ""
            }
            Text(
                text = "$baseText$sizeLabel",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeFormat != null) Color.White else palette.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showAudioSheet) {
        ZajilSelectionModalSheet(
            title = if (isEnglish) "Audio" else "الصوت",
            options = ZajilAudioPreference.entries.map {
                ZajilSelectOption(
                    id = it.id,
                    label = if (isEnglish) it.labelEn else it.labelAr
                )
            },
            selectedId = selectedAudio.id,
            onSelect = { chosenId ->
                ZajilAudioPreference.entries.firstOrNull { it.id == chosenId }?.let {
                    selectedAudio = it
                }
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false }
        )
    }
}

@Composable
private fun ZajilPickerRow(
    icon: ImageVector,
    label: String,
    selectedValue: String,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalRafidPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = palette.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = selectedValue,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = label,
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZajilSelectionModalSheet(
    title: String,
    options: List<ZajilSelectOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val subSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = subSheetState,
        containerColor = palette.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.borderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            options.forEach { option ->
                val isSelected = option.id == selectedId
                Surface(
                    onClick = { onSelect(option.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) palette.heroBlue.copy(alpha = 0.05f) else palette.surface,
                    border = BorderStroke(
                        width = if (isSelected) 1.8.dp else 1.dp,
                        color = if (isSelected) palette.heroBlue else palette.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("zajil_option_${option.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = palette.heroBlue,
                                unselectedColor = palette.textSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyFolderToggleRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = palette.heroBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isEnglish) "Auto-save in Study folder" else "تصنيف تلقائي في مجلد الدراسة",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = palette.heroBlue
                ),
                modifier = Modifier.testTag("switch_save_study_folder")
            )
        }
    }
}
