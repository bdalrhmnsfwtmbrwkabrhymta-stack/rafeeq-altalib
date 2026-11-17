package com.hawas.study.rafid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.models.RafidLanguageMode
import com.hawas.study.rafid.models.RafidScreenTab
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import com.hawas.study.rafid.ui.theme.rafidStr

@Composable
fun RafidLogoEmblem(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalRafidPalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E88E5), palette.heroBlue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val h = this.size.height
            // Stream / Wing emblem representing "رَافِد" (flowing tributary & swift stream)
            val wing1 = Path().apply {
                moveTo(w * 0.14f, h * 0.24f)
                quadraticTo(w * 0.54f, h * 0.14f, w * 0.88f, h * 0.56f)
                quadraticTo(w * 0.56f, h * 0.52f, w * 0.26f, h * 0.42f)
                close()
            }
            val wing2 = Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                quadraticTo(w * 0.52f, h * 0.46f, w * 0.82f, h * 0.78f)
                quadraticTo(w * 0.48f, h * 0.78f, w * 0.22f, h * 0.68f)
                close()
            }
            val droplet = Path().apply {
                moveTo(w * 0.24f, h * 0.84f)
                quadraticTo(w * 0.48f, h * 0.76f, w * 0.72f, h * 0.90f)
                quadraticTo(w * 0.46f, h * 0.94f, w * 0.24f, h * 0.84f)
                close()
            }
            drawPath(wing1, color = Color.White)
            drawPath(wing2, color = Color.White.copy(alpha = 0.88f))
            drawPath(droplet, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
fun StackedHeroCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalRafidPalette.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Backmost receding layer
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(34.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(palette.heroBackLayer1)
        )
        // Middle receding layer
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .fillMaxWidth(0.92f)
                .height(34.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(palette.heroBackLayer2)
        )
        // Front primary stacked card (#1565C0, 28.dp radius)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.heroBlue,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                content = content
            )
        }
    }
}

@Composable
fun RafidFloatingBottomBar(
    currentTab: RafidScreenTab,
    sniffedCount: Int,
    onSelectTab: (RafidScreenTab) -> Unit,
    onCenterFabClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRafidPalette.current
    val hasSniffedMedia = sniffedCount > 0 && currentTab == RafidScreenTab.BROWSER
    val fabBgColor by animateColorAsState(
        targetValue = if (hasSniffedMedia) palette.emeraldTint else palette.heroBlue,
        label = "fabColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Pill bar
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = palette.surface,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(62.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(
                        onClick = { onSelectTab(RafidScreenTab.HOME) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rafid_nav_home")
                    ) {
                        Icon(
                            imageVector = if (currentTab == RafidScreenTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = rafidStr("الرئيسية", "Home"),
                            tint = if (currentTab == RafidScreenTab.HOME) palette.heroBlue else palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSelectTab(RafidScreenTab.BROWSER) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rafid_nav_browser")
                    ) {
                        Icon(
                            imageVector = if (currentTab == RafidScreenTab.BROWSER) Icons.Filled.Public else Icons.Outlined.Public,
                            contentDescription = rafidStr("المتصفح الذكي", "Smart Browser"),
                            tint = if (currentTab == RafidScreenTab.BROWSER) palette.heroBlue else palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Spacer reserved for overlapping center FAB
                    Spacer(modifier = Modifier.width(64.dp))

                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rafid_nav_search")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = rafidStr("بحث", "Search"),
                            tint = palette.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSelectTab(RafidScreenTab.QUEUES) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rafid_nav_queues")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatListBulleted,
                            contentDescription = rafidStr("الطوابير", "Queues"),
                            tint = if (currentTab == RafidScreenTab.QUEUES || currentTab == RafidScreenTab.PROTECTION) {
                                palette.heroBlue
                            } else {
                                palette.textSecondary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Center Elevated Floating Action Button
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    onClick = onCenterFabClick,
                    shape = CircleShape,
                    color = fabBgColor,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(58.dp)
                        .border(3.dp, palette.background, CircleShape)
                        .testTag("rafid_center_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasSniffedMedia) Icons.Filled.Download else Icons.Filled.Add,
                            contentDescription = rafidStr("تحميل جديد", "New Download"),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (hasSniffedMedia) {
                    Surface(
                        shape = CircleShape,
                        color = palette.warningTint,
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = sniffedCount.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RafidLanguageSheet(
    currentMode: RafidLanguageMode,
    onSelectMode: (RafidLanguageMode) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = if (isEnglish) "Language" else "اللغة",
                fontFamily = ReadexProFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            RafidLanguageMode.entries.forEach { mode ->
                val selected = mode == currentMode
                Surface(
                    onClick = {
                        onSelectMode(mode)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface,
                    border = BorderStroke(
                        width = if (selected) 1.8.dp else 1.dp,
                        color = if (selected) palette.heroBlue else palette.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lang_option_${mode.code}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) mode.labelEn else mode.labelAr,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = palette.heroBlue)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDownloadLinkSheet(
    initialUrl: String,
    onPasteFromClipboard: () -> String?,
    onAnalyzeUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    val validUrl = AdAndTrackerShield.extractFirstUrl(urlInput)
    val detectedPlatform = validUrl?.let { AdAndTrackerShield.detectPlatform(it) }
    val detectedDomain = validUrl?.let { AdAndTrackerShield.normalizeDomain(it) } ?: ""

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEnglish) "New Download" else "تحميل جديد",
                fontFamily = ReadexProFontFamily,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { next ->
                    val wasPasted = (next.length - urlInput.length) >= 10
                    urlInput = next
                    if (wasPasted) {
                        AdAndTrackerShield.extractFirstUrl(next)?.let { pastedValid ->
                            onAnalyzeUrl(pastedValid)
                        }
                    }
                },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                ),
                placeholder = {
                    Text(
                        text = if (isEnglish) "Paste video or lecture link here..." else "الصق رابط الفيديو أو المحاضرة هنا...",
                        fontSize = 15.sp,
                        color = palette.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            onPasteFromClipboard()?.let { pasted ->
                                urlInput = pasted
                                AdAndTrackerShield.extractFirstUrl(pasted)?.let { pastedValid ->
                                    onAnalyzeUrl(pastedValid)
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_paste_link")
                    ) {
                        Text(
                            text = if (isEnglish) "Paste" else "لصق",
                            color = palette.heroBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.heroBlue,
                    unfocusedBorderColor = palette.borderSubtle,
                    focusedContainerColor = palette.surfaceVariant,
                    unfocusedContainerColor = palette.surfaceVariant,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (validUrl != null) onAnalyzeUrl(validUrl)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_download_url_field")
            )

            if (validUrl != null && detectedPlatform != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = palette.heroBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = palette.heroBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) "Media Link (${detectedPlatform.labelEn})" else "رابط وسائط (${detectedPlatform.labelAr})",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (isEnglish) {
                                    "From $detectedDomain • Ready for quality selection"
                                } else {
                                    "من $detectedDomain • جاهز لاختيار الجودة"
                                },
                                fontSize = 13.5.sp,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Button(
                    onClick = { onAnalyzeUrl(validUrl) },
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("btn_choose_quality")
                ) {
                    Text(
                        text = if (isEnglish) "Choose Quality" else "اختر الجودة",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.4f)
                            .heightIn(min = 50.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Later" else "لاحقاً",
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { if (validUrl != null) onAnalyzeUrl(validUrl) },
                        enabled = validUrl != null,
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.heroBlue,
                            disabledContainerColor = palette.borderSubtle
                        ),
                        modifier = Modifier
                            .weight(0.6f)
                            .heightIn(min = 50.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Add to Main Queue" else "أضف إلى الطابور الرئيسي",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemInfoDialog(
    item: RafidDownloadEntity,
    onDismiss: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = if (isEnglish) "Download Details" else "تفاصيل التحميل",
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary
                )
                InfoDetailLine(if (isEnglish) "Quality" else "الجودة", item.qualityLabel)
                InfoDetailLine(if (isEnglish) "Source" else "المصدر", item.sourceDomain)
                InfoDetailLine(if (isEnglish) "Save Path" else "مسار الحفظ", item.displayPath)
                InfoDetailLine(
                    if (isEnglish) "Progress" else "التقدم",
                    "${RafidFormatUtils.formatFileSize(item.downloadedBytes, isEnglish)} / ${RafidFormatUtils.formatFileSize(item.totalBytes, isEnglish)}"
                )
                if (!item.errorMessage.isNullOrBlank()) {
                    Text(
                        text = item.errorMessage,
                        fontSize = 12.sp,
                        color = palette.dangerTint
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue)
            ) {
                Text(if (isEnglish) "Close" else "إغلاق")
            }
        }
    )
}

@Composable
private fun InfoDetailLine(label: String, value: String) {
    val palette = LocalRafidPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", fontSize = 12.sp, color = palette.textSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
