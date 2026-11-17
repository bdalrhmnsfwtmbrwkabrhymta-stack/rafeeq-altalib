package com.hawas.study.rafid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hawas.study.rafid.ExtractionUiState
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDownloadBottomSheet(
    extractionState: ExtractionUiState,
    onRetry: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onStartDownload: (metadata: ExtractedMediaMetadata, format: MediaFormatOption, saveToStudyFolder: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        when (extractionState) {
            is ExtractionUiState.Idle -> Unit
            is ExtractionUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = palette.heroBlue)
                    Text(
                        text = if (isEnglish) "Extracting real media qualities..." else "جاري تحليل الرابط واستخلاص الجودات المتاحة...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = extractionState.url,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            is ExtractionUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.dangerTint.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = palette.dangerTint,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Unable to extract directly" else "تعذر الاستخلاص المباشر",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = extractionState.message,
                                    fontSize = 12.5.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRetry(extractionState.url) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(if (isEnglish) "Retry" else "إعادة المحاولة")
                        }
                        Button(
                            onClick = { onOpenInBrowser(extractionState.url) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("btn_open_in_smart_browser")
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Smart Browser" else "فتح في المتصفح الذكي", fontSize = 13.sp)
                        }
                    }
                }
            }

            is ExtractionUiState.Success -> {
                SmartDownloadSuccessContent(
                    metadata = extractionState.metadata,
                    onStartDownload = onStartDownload
                )
            }
        }
    }
}

@Composable
fun SmartDownloadSuccessContent(
    metadata: ExtractedMediaMetadata,
    onStartDownload: (metadata: ExtractedMediaMetadata, format: MediaFormatOption, saveToStudyFolder: Boolean) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    val videoFormats = remember(metadata) {
        metadata.formats.filter { !it.isAudioOnly || it.isPlaylistBundle }
    }
    val audioFormats = remember(metadata) {
        metadata.formats.filter { it.isAudioOnly }
    }

    var selectedTab by remember(metadata) {
        mutableIntStateOf(if (videoFormats.isNotEmpty()) 0 else 1)
    }
    var selectedFormatId by remember(metadata) {
        mutableStateOf(
            metadata.formats.firstOrNull { !it.isAudioOnly && it.resolutionHeight == 1080 }?.id
                ?: metadata.formats.firstOrNull { !it.isAudioOnly }?.id
                ?: metadata.formats.firstOrNull()?.id
                ?: ""
        )
    }
    var saveToStudyFolder by remember(metadata) { mutableStateOf(false) }

    val displayedFormats = if (selectedTab == 0 && videoFormats.isNotEmpty()) videoFormats else audioFormats
    LaunchedEffect(selectedTab, displayedFormats) {
        if (displayedFormats.isNotEmpty() && displayedFormats.none { it.id == selectedFormatId }) {
            selectedFormatId = displayedFormats.first().id
        }
    }

    val activeFormat = displayedFormats.firstOrNull { it.id == selectedFormatId }
        ?: metadata.formats.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header preview row matching reference screenshot 4c888cc1
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("smart_sheet_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${metadata.sourceDomain} • ${metadata.authorOrSource}",
                    fontSize = 12.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 114.dp, height = 66.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceVariant),
                contentAlignment = Alignment.BottomStart
            ) {
                if (!metadata.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = metadata.thumbnailUrl,
                        contentDescription = metadata.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (metadata.isPlaylist) Icons.Filled.PlaylistPlay else Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = palette.heroBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                val durText = RafidFormatUtils.formatDuration(metadata.durationSeconds)
                if (durText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.padding(5.dp)
                    ) {
                        Text(
                            text = durText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Segmented Video / Audio Switcher matching reference screenshots
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
                            .height(42.dp)
                            .testTag("smart_tab_video")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isEnglish) "Video" else "فيديو",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
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
                            .height(42.dp)
                            .testTag("smart_tab_audio")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isEnglish) "Audio Only" else "صوت فقط",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isAudioTab) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Quality Radio List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 310.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayedFormats.forEach { option ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = palette.textPrimary
                                    )
                                    val badge = if (isEnglish) option.badgeTagEn else option.badgeTagAr
                                    if (!badge.isNullOrBlank()) {
                                        Text(
                                            text = badge,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if ((option.resolutionHeight ?: 0) <= 144 && !option.isAudioOnly && !option.isPlaylistBundle) {
                                                palette.emeraldTint
                                            } else {
                                                palette.heroBlue
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (option.isPlaylistBundle) {
                                "${metadata.playlistItems.size} " + if (isEnglish) "items" else "عنصر"
                            } else {
                                RafidFormatUtils.formatFileSize(option.estimatedSizeBytes, isEnglish)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }

        // Audio Track Indicator Row (as in screenshot 4c888cc1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEnglish) "Audio Track" else "الصوت",
                        fontSize = 11.5.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = activeFormat?.languageLabel ?: if (isEnglish) "Original" else "الأصلي: العربية",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // Auto-organize into Study folder switch (matching screenshot 8a569b80)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { saveToStudyFolder = !saveToStudyFolder }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                }
                Switch(
                    checked = saveToStudyFolder,
                    onCheckedChange = { saveToStudyFolder = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.heroBlue
                    ),
                    modifier = Modifier.testTag("switch_save_study_folder")
                )
            }
        }

        // Primary CTA button
        Button(
            onClick = {
                activeFormat?.let { onStartDownload(metadata, it, saveToStudyFolder) }
            },
            enabled = activeFormat != null,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_start_fast_download")
        ) {
            val sizeLabel = if (activeFormat != null && activeFormat.estimatedSizeBytes > 0L) {
                "، ${RafidFormatUtils.formatFileSize(activeFormat.estimatedSizeBytes, isEnglish)}"
            } else {
                ""
            }
            Text(
                text = if (isEnglish) "Start Fast Download$sizeLabel" else "بدء التحميل السريع$sizeLabel",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
