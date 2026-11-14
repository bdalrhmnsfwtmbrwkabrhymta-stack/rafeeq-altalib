package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.RestartAlt
import com.example.data.preferences.UserSettings
import com.example.ui.MainViewModel
import com.example.ui.theme.parseHexColor

data class AppThemeOption(
    val id: String,
    val titleArabic: String,
    val titleEnglish: String,
    val bgColor: Color,
    val appColor: Color,
    val textColor: Color,
    val cardColor: Color
)

val PRESET_THEMES = listOf(
    AppThemeOption("ROYAL_BLUE", "أبيض مع الأزرق الملكي 🔵 (الافتراضي)", "Clean White & Royal Blue 🔵 (Default)", Color(0xFFF8FAFC), Color(0xFF2563EB), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("VIBRANT_PURPLE", "أبيض مع البنفسجي الحيوي 🟣", "Clean White & Vibrant Purple 🟣", Color(0xFFF8FAFC), Color(0xFF6C5CE7), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("EMERALD", "أبيض مع الأخضر الزمردي 🟢", "Clean White & Emerald 🟢", Color(0xFFF8FAFC), Color(0xFF10B981), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("AMBER", "أبيض مع الكهرماني الذهبي 🟡", "Clean White & Amber 🟡", Color(0xFFF8FAFC), Color(0xFFF59E0B), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("CORAL", "أبيض مع المرجاني الدافئ 🔴", "Clean White & Coral 🔴", Color(0xFFF8FAFC), Color(0xFFFF6B6B), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("ORANGE_WHITE", "برتقالي في أبيض 🍊", "Orange & White 🍊", Color(0xFFF8FAFC), Color(0xFFEA580C), Color(0xFF0F172A), Color(0xFFFFFFFF)),
    AppThemeOption("MONOCHROME", "أبيض وأسود أنيق ⚪⚫ (كلاسيك)", "Monochrome ⚪⚫ (Black & White)", Color(0xFFFFFFFF), Color(0xFF0F172A), Color(0xFF000000), Color(0xFFF1F5F9)),
    AppThemeOption("ORANGE_DARK", "برتقالي داكن 🌙 (ليلي)", "Orange Dark 🌙 (Night)", Color(0xFF0F172A), Color(0xFFFB923C), Color(0xFFFFFFFF), Color(0xFF1E293B)),
    AppThemeOption("SPACE", "الفضاء الكوني 🌌", "Space Dark 🌌", Color(0xFF050B14), Color(0xFF38BDF8), Color(0xFFF1F5F9), Color(0xFF0F172A))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppThemeScreen(
    viewModel: MainViewModel,
    onBackClick: (() -> Unit)? = null
) {
    val userSettings by viewModel.userSettings.collectAsState()
    val activePreset = userSettings.appThemePreset
    val isEnglish = userSettings.appLanguage == "en"

    var showCustomColorDialog by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf("") } // "BG", "APP", "TEXT", "CARD"
    var tempHexColor by remember { mutableStateOf("") }

    val customThemeOption = AppThemeOption(
        id = "CUSTOM",
        titleArabic = "مخصص (انقر على أي لون لتغييره)",
        titleEnglish = "Custom Theme (Tap any color to edit)",
        bgColor = parseHexColor(userSettings.customBgColorHex, Color(0xFFF8FAFC)),
        appColor = parseHexColor(userSettings.customAppColorHex, Color(0xFFD95338)),
        textColor = parseHexColor(userSettings.customTextColorHex, Color(0xFF0F172A)),
        cardColor = parseHexColor(userSettings.customCardColorHex, Color(0xFFFFFFFF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar Header
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = if (isEnglish) "App Theme & Appearance 🎨" else "المظهر والثيم الداكن 🎨",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "رجوع"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. Dark Mode Selection Card (M3 Dark Theme)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (userSettings.isDarkMode) {
                                            true -> Icons.Default.DarkMode
                                            false -> Icons.Default.LightMode
                                            null -> Icons.Default.SettingsBrightness
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Dark Mode & Night Theme 🌙" else "السمة الداكنة والوضع الليلي 🌙",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEnglish) "Switch between Light, Dark, or System mode" else "التبديل بين الوضع الفاتح، الداكن، أو تلقائي النظام",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Light Option
                            FilterChip(
                                selected = userSettings.isDarkMode == false,
                                onClick = { viewModel.setDarkMode(false) },
                                label = { Text(if (isEnglish) "Light ☀️" else "فاتح ☀️", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                leadingIcon = if (userSettings.isDarkMode == false) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )

                            // 2. Dark Option
                            FilterChip(
                                selected = userSettings.isDarkMode == true,
                                onClick = { viewModel.setDarkMode(true) },
                                label = { Text(if (isEnglish) "Dark 🌙" else "داكن 🌙", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                leadingIcon = if (userSettings.isDarkMode == true) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )

                            // 3. System Option
                            FilterChip(
                                selected = userSettings.isDarkMode == null,
                                onClick = { viewModel.setDarkMode(null) },
                                label = { Text(if (isEnglish) "Auto ⚙️" else "تلقائي ⚙️", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                leadingIcon = if (userSettings.isDarkMode == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Color Themes Palette 🎨" else "ألوان وثيمات التطبيق 🎨",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(
                        onClick = {
                            viewModel.setAppThemePreset("ROYAL_BLUE")
                            viewModel.setCustomThemeColors("#2563EB", "#F8FAFC", "#FFFFFF", "#0F172A")
                            viewModel.setDarkMode(false)
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEnglish) "Reset to Default 🔵" else "استعادة الافتراضي 🔵", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Presets list
            items(PRESET_THEMES) { theme ->
                val isSelected = activePreset.equals(theme.id, ignoreCase = true) ||
                        (theme.id == "DEFAULT" && (activePreset.isBlank() || activePreset.equals("LIGHT", ignoreCase = true)))
                ThemeCardItem(
                    theme = theme,
                    isEnglish = isEnglish,
                    isSelected = isSelected,
                    onSelect = {
                        viewModel.setAppThemePreset(theme.id)
                    }
                )
            }

            // Custom Theme Option Card
            item {
                ThemeCardItem(
                    theme = customThemeOption,
                    isEnglish = isEnglish,
                    isSelected = activePreset.equals("CUSTOM", ignoreCase = true),
                    onSelect = {
                        viewModel.setAppThemePreset("CUSTOM")
                    },
                    onColorClick = { target ->
                        editingTarget = target
                        tempHexColor = when (target) {
                            "BG" -> userSettings.customBgColorHex
                            "APP" -> userSettings.customAppColorHex
                            "TEXT" -> userSettings.customTextColorHex
                            else -> userSettings.customCardColorHex
                        }
                        showCustomColorDialog = true
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCustomColorDialog) {
        val colorPresets = listOf(
            "#1E3A8A", "#2563EB", "#3B82F6", "#60A5FA", "#0284C7", "#38BDF8",
            "#3B0764", "#7C3AED", "#9333EA", "#C084FC", "#D95338", "#EA580C",
            "#14532D", "#16A34A", "#22C55E", "#4ADE80", "#134E4A", "#0D9488",
            "#0F172A", "#1E293B", "#334155", "#64748B", "#F8FAFC", "#FFFFFF"
        )

        AlertDialog(
            onDismissRequest = { showCustomColorDialog = false },
            title = {
                Text(
                    text = when (editingTarget) {
                        "BG" -> if (isEnglish) "Change Background Color" else "تغيير لون الخلفية"
                        "APP" -> if (isEnglish) "Change Primary Color" else "تغيير لون التطبيق الأساسي"
                        "TEXT" -> if (isEnglish) "Change Text Color" else "تغيير لون النص"
                        else -> if (isEnglish) "Change Card Color" else "تغيير لون البطاقة"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isEnglish) "Pick a quick color or enter Hex code:" else "اختر لوناً سريعاً أو أدخل كود Hex:")
                    colorPresets.chunked(6).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex, Color.Gray))
                                        .clickable { tempHexColor = hex }
                                        .border(
                                            width = if (tempHexColor.equals(hex, ignoreCase = true)) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempHexColor,
                        onValueChange = { tempHexColor = it },
                        label = { Text(if (isEnglish) "Hex code (e.g. #0D9488)" else "كود Hex (مثال: #0D9488)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanHex = tempHexColor.removePrefix("#")
                    val bg = if (editingTarget == "BG") cleanHex else userSettings.customBgColorHex
                    val app = if (editingTarget == "APP") cleanHex else userSettings.customAppColorHex
                    val text = if (editingTarget == "TEXT") cleanHex else userSettings.customTextColorHex
                    val card = if (editingTarget == "CARD") cleanHex else userSettings.customCardColorHex

                    viewModel.setCustomThemeColors(bg, app, text, card)
                    viewModel.setAppThemePreset("CUSTOM")
                    showCustomColorDialog = false
                }) {
                    Text(if (isEnglish) "Save & Apply" else "حفظ وتطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomColorDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}

@Composable
fun ThemeCardItem(
    theme: AppThemeOption,
    isEnglish: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onColorClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) theme.titleEnglish else theme.titleArabic,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Switch(
                    checked = isSelected,
                    onCheckedChange = { onSelect() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color Circles Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorSwatchItem(
                    label = if (isEnglish) "Card" else "البطاقة",
                    color = theme.cardColor,
                    onClick = { if (theme.id == "CUSTOM" && onColorClick != null) onColorClick("CARD") else onSelect() }
                )

                ColorSwatchItem(
                    label = if (isEnglish) "Text" else "النص",
                    color = theme.textColor,
                    onClick = { if (theme.id == "CUSTOM" && onColorClick != null) onColorClick("TEXT") else onSelect() }
                )

                ColorSwatchItem(
                    label = if (isEnglish) "Primary" else "التطبيق",
                    color = theme.appColor,
                    onClick = { if (theme.id == "CUSTOM" && onColorClick != null) onColorClick("APP") else onSelect() }
                )

                ColorSwatchItem(
                    label = if (isEnglish) "Background" else "الخلفية",
                    color = theme.bgColor,
                    onClick = { if (theme.id == "CUSTOM" && onColorClick != null) onColorClick("BG") else onSelect() }
                )
            }
        }
    }
}

@Composable
fun ColorSwatchItem(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
