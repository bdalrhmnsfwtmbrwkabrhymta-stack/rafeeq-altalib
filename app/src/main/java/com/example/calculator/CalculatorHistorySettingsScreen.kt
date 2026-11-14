package com.example.calculator

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun CalculatorHistorySettingsScreen(
    uiState: CalculatorUiState,
    onSelectHistoryItem: (String) -> Unit,
    onRestoreHistoryEntry: (CalcHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onUpdateDefaultFormat: (ResultFormat) -> Unit,
    onUpdateDefaultAngleUnit: (AngleUnit) -> Unit,
    onUpdateDecimalPlaces: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val selectedEntry = uiState.history.firstOrNull { it.id == uiState.selectedHistoryId }
        ?: uiState.history.firstOrNull()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalcColors.CanvasBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar matching Image 3 (→ إعدادات وسجل الحاسبة | ✕)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = CalcColors.TextDark
                        )
                    }
                    Text(
                        text = "إعدادات وسجل الحاسبة",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextDark
                    )
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("calc_settings_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = CalcColors.TextSlate
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ======================================================
                // Section 1: السجل (History)
                // ======================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = CalcColors.TextSlate,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "السجل",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CalcColors.TextDark
                        )
                    }

                    if (uiState.history.isNotEmpty()) {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("calc_clear_history_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "مسح السجل",
                                tint = CalcColors.ClearRed,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مسح الكل",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                color = CalcColors.ClearRed
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CalcColors.CardWhite,
                    border = BorderStroke(1.dp, CalcColors.BorderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد عمليات حسابية محفوظة بعد",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.sp,
                                color = CalcColors.TextSlate
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            uiState.history.forEachIndexed { index, entry ->
                                val isSelected = entry.id == selectedEntry?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) CalcColors.SoftBlueTint else Color.Transparent)
                                        .clickable {
                                            onSelectHistoryItem(entry.id)
                                            onRestoreHistoryEntry(entry)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("calc_history_row_$index"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = entry.result,
                                            fontFamily = ReadexProFontFamily,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CalcColors.RoyalBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(0.45f)
                                        )
                                        Text(
                                            text = entry.expression,
                                            fontFamily = ReadexProFontFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CalcColors.TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(0.55f)
                                        )
                                    }
                                }
                                if (index < uiState.history.lastIndex) {
                                    HorizontalDivider(color = CalcColors.BorderSoft)
                                }
                            }
                        }
                    }
                }

                // Royal Blue Copy Result Button matching Image 3
                Button(
                    onClick = {
                        val copyText = selectedEntry?.result ?: uiState.resultText
                        clipboardManager.setText(AnnotatedString(copyText))
                        Toast.makeText(context, "تم نسخ النتيجة: $copyText", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CalcColors.RoyalBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("calc_copy_result_btn")
                ) {
                    Text(
                        text = "نسخ النتيجة",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ======================================================
                // Section 2: إعدادات الآلة الحاسبة (Settings)
                // ======================================================
                Text(
                    text = "إعدادات الآلة الحاسبة",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CalcColors.TextDark,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CalcColors.CardWhite,
                    border = BorderStroke(1.dp, CalcColors.BorderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Setting 1: صيغة الناتج الافتراضية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "صيغة الناتج الافتراضية",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CalcColors.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CalcColors.CanvasBg,
                                border = BorderStroke(1.dp, CalcColors.BorderSoft)
                            ) {
                                Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SegmentedChoicePill(
                                        label = "عشري (DEC)",
                                        selected = uiState.settings.defaultFormat == ResultFormat.DECIMAL,
                                        onClick = { onUpdateDefaultFormat(ResultFormat.DECIMAL) },
                                        testTag = "setting_format_dec"
                                    )
                                    SegmentedChoicePill(
                                        label = "كسر (a/b)",
                                        selected = uiState.settings.defaultFormat == ResultFormat.FRACTION,
                                        onClick = { onUpdateDefaultFormat(ResultFormat.FRACTION) },
                                        testTag = "setting_format_frac"
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = CalcColors.BorderSoft)

                        // Setting 2: وحدة الزاوية الافتراضية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "وحدة الزاوية الافتراضية",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CalcColors.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CalcColors.CanvasBg,
                                border = BorderStroke(1.dp, CalcColors.BorderSoft)
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        AngleUnit.entries.forEach { unit ->
                                            SegmentedChoicePill(
                                                label = unit.label,
                                                selected = uiState.settings.defaultAngleUnit == unit,
                                                onClick = { onUpdateDefaultAngleUnit(unit) },
                                                testTag = "setting_angle_${unit.label}"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = CalcColors.BorderSoft)

                        // Setting 3: عدد الخانات العشرية (0..10 stepper matching Image 3: ∧ 8 ∨)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "عدد الخانات العشرية",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CalcColors.TextDark
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onUpdateDecimalPlaces(uiState.settings.decimalPlaces - 1) },
                                    enabled = uiState.settings.decimalPlaces > 0,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("setting_decimals_dec")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "تقليل الخانات",
                                        tint = if (uiState.settings.decimalPlaces > 0) CalcColors.TextDark else CalcColors.TextMuted
                                    )
                                }
                                Text(
                                    text = "${uiState.settings.decimalPlaces}",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CalcColors.TextDark,
                                    modifier = Modifier.widthIn(min = 24.dp)
                                )
                                IconButton(
                                    onClick = { onUpdateDecimalPlaces(uiState.settings.decimalPlaces + 1) },
                                    enabled = uiState.settings.decimalPlaces < 10,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("setting_decimals_inc")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "زيادة الخانات",
                                        tint = if (uiState.settings.decimalPlaces < 10) CalcColors.TextDark else CalcColors.TextMuted
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

@Composable
private fun SegmentedChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CalcColors.CardWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = ReadexProFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) CalcColors.TextDark else CalcColors.TextSlate
        )
    }
}
