package com.example.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.preferences.UserSettings
import com.example.domain.notification.NotificationReliabilityManager
import com.example.ui.MainViewModel
import com.example.ui.dialogs.UniversalNotificationPermissionDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        if (isEnglish) "Profile & Account 👤" else "الملف الشخصي والحساب 👤",
        if (isEnglish) "Theme & Colors 🎨" else "المظهر والألوان 🎨",
        if (isEnglish) "Notifications & Protection 🛡️" else "حماية الإشعارات 🛡️",
        if (isEnglish) "Prayer & Azkar 🕌" else "الصلاة والأذكار 🕌"
    )

    // Reliability Status State
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    var exactAlarmGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var batteryOptimizationIgnored by remember { mutableStateOf(false) }
    var dndGranted by remember { mutableStateOf(false) }
    var lastScheduledTime by remember { mutableStateOf(NotificationReliabilityManager.getLastScheduledAlarmTime(context)) }
    var lastReceivedTime by remember { mutableStateOf(NotificationReliabilityManager.getLastNotificationReceivedTime(context)) }
    var showUniversalDialog by remember { mutableStateOf(false) }

    fun refreshDiagnostics() {
        notificationPermissionGranted = NotificationReliabilityManager.isNotificationPermissionGranted(context)
        exactAlarmGranted = NotificationReliabilityManager.canScheduleExactAlarms(context)
        overlayGranted = NotificationReliabilityManager.canDrawOverlays(context)
        batteryOptimizationIgnored = NotificationReliabilityManager.isIgnoringBatteryOptimizations(context)
        dndGranted = NotificationReliabilityManager.isNotificationPolicyAccessGranted(context)
        lastScheduledTime = NotificationReliabilityManager.getLastScheduledAlarmTime(context)
        lastReceivedTime = NotificationReliabilityManager.getLastNotificationReceivedTime(context)
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshDiagnostics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEnglish) "Settings" else "الإعدادات وتنظيم الحساب",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigate("admin_console") }) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Console",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Selector
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Profile & Account Tab (Organized by User)
                    item {
                        UserProfileTabSection(
                            viewModel = viewModel,
                            userSettings = userSettings,
                            isEnglish = isEnglish,
                            onNavigateToLogin = { onNavigate("login") },
                            onNavigateToOnboarding = { onNavigate("onboarding") }
                        )
                    }
                }

                1 -> {
                    // Theme & Appearance Tab
                    item {
                        ThemeAndColorsTabSection(
                            viewModel = viewModel,
                            userSettings = userSettings,
                            isEnglish = isEnglish,
                            onNavigateToTheme = { onNavigate("app_theme") }
                        )
                    }
                }

                2 -> {
                    // Notifications & Reliability Protection Tab
                    item {
                        NotificationProtectionTabSection(
                            context = context,
                            isEnglish = isEnglish,
                            exactAlarmGranted = exactAlarmGranted,
                            overlayGranted = overlayGranted,
                            notificationPermissionGranted = notificationPermissionGranted,
                            batteryOptimizationIgnored = batteryOptimizationIgnored,
                            dndGranted = dndGranted,
                            lastScheduledTime = lastScheduledTime,
                            lastReceivedTime = lastReceivedTime,
                            onOpenUniversalDialog = { showUniversalDialog = true },
                            onRefresh = { refreshDiagnostics() },
                            onRequestNotification = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationReliabilityManager.openNotificationSettings(context)
                                }
                            },
                            onNavigate = onNavigate
                        )
                    }
                }

                3 -> {
                    // Prayer & Azkar Tab
                    item {
                        PrayerAndAzkarTabSection(
                            viewModel = viewModel,
                            userSettings = userSettings,
                            isEnglish = isEnglish
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showUniversalDialog) {
            UniversalNotificationPermissionDialog(
                onDismiss = {
                    showUniversalDialog = false
                    refreshDiagnostics()
                },
                onPermissionGranted = {
                    showUniversalDialog = false
                    refreshDiagnostics()
                }
            )
        }
    }
}

/**
 * 1. User Profile & Account Section
 */
@Composable
fun UserProfileTabSection(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    isEnglish: Boolean,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf(userSettings.userName) }
    var whatsapp by remember { mutableStateOf(userSettings.whatsappNumber) }
    var telegram by remember { mutableStateOf(userSettings.telegramNumber) }
    var goal by remember { mutableStateOf(userSettings.dailyGoal) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // User Header Hero Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userSettings.userName.ifBlank { "الطالب المثالي" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${userSettings.studentGradeYear} • ${userSettings.studentAge} سنة",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = "تعديل البيانات",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            if (!isEditing) {
                // View Mode: Clean organized rows
                ProfileInfoRow(icon = Icons.Default.Phone, title = "رقم الهاتف / الواتساب", value = userSettings.whatsappNumber.ifBlank { "غير مسجل" })
                ProfileInfoRow(icon = Icons.Default.Send, title = "معرف التليجرام", value = userSettings.telegramNumber.ifBlank { "غير مسجل" })
                ProfileInfoRow(icon = Icons.Default.Flag, title = "الهدف اليومي", value = userSettings.dailyGoal.ifBlank { "إتمام المهام الدراسية" })

                Button(
                    onClick = { isEditing = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تعديل بيانات الحساب", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            } else {
                // Edit Mode: Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطالب") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("رقم الهاتف / الواتساب") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = telegram,
                    onValueChange = { telegram = it },
                    label = { Text("معرف التليجرام") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("الهدف اليومي") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إلغاء")
                    }

                    Button(
                        onClick = {
                            viewModel.updateProfile(name = name, whatsapp = whatsapp, telegram = telegram, goal = goal)
                            isEditing = false
                            Toast.makeText(context, "تم حفظ بيانات الملف الشخصي بنجاح ✅", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Account Actions
            Text(
                text = "إدارة وتسجيل الحساب",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Switch / Register Account Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLogin() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("تسجيل الدخول / إنشاء حساب جديد", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("التبديل بين الحسابات أو إنشاء حساب جديد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }

            // Onboarding Tour Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToOnboarding() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("جولة التعريف ومميزات التطبيق 🚀", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("استعراض شرائح الترحيب الـ 4 المنظمة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Re-run Account Setup & Onboarding
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.restartOnboardingSetup()
                        onNavigateToOnboarding()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("تعديل بيانات الحساب والتهيئة (Onboarding) 🎓", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary)
                            Text("إعادة كتابة الاسم، العمر، السنة، رقم التليجرام، وضبط الموقع والإشعارات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 2. Theme & Colors Section
 */
@Composable
fun ThemeAndColorsTabSection(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    isEnglish: Boolean,
    onNavigateToTheme: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "مظهر وألوان التطبيق 🎨",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            // Dark Mode Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = userSettings.isDarkMode == false,
                    onClick = { viewModel.setDarkMode(false) },
                    label = { Text("فاتح ☀️", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = userSettings.isDarkMode == true,
                    onClick = { viewModel.setDarkMode(true) },
                    label = { Text("داكن 🌙", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = userSettings.isDarkMode == null,
                    onClick = { viewModel.setDarkMode(null) },
                    label = { Text("تلقائي ⚙️", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // Quick Theme Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الألوان الأساسية للثيم:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                TextButton(onClick = onNavigateToTheme) {
                    Text("تخصيص كامل 🎨", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            val quickThemes = listOf(
                Triple("ROYAL_BLUE", "أزرق ملكي 🔵", Color(0xFF2563EB)),
                Triple("VIBRANT_PURPLE", "بنفسجي حيوي 🟣", Color(0xFF6C5CE7)),
                Triple("EMERALD", "أخضر زمردي 🟢", Color(0xFF10B981)),
                Triple("AMBER", "كهرماني 🟡", Color(0xFFF59E0B)),
                Triple("CORAL", "مرجاني 🔴", Color(0xFFFF6B6B)),
                Triple("SPACE", "فضاء 🌌", Color(0xFF38BDF8))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickThemes.take(3).forEach { (presetId, title, color) ->
                    val isSelected = userSettings.appThemePreset.equals(presetId, ignoreCase = true)
                    OutlinedButton(
                        onClick = { viewModel.setAppThemePreset(presetId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors(),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickThemes.drop(3).forEach { (presetId, title, color) ->
                    val isSelected = userSettings.appThemePreset.equals(presetId, ignoreCase = true)
                    OutlinedButton(
                        onClick = { viewModel.setAppThemePreset(presetId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors(),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }

            HorizontalDivider()

            // Sound effects
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "المؤثرات الصوتية للأزرار والمهام 🎵", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text(text = "أصوات التحفيز عند إنجاز المهام وترقية المستوى", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = userSettings.isSoundEffectsEnabled,
                    onCheckedChange = { viewModel.updateSoundEffects(it) }
                )
            }

            HorizontalDivider()

            // Language
            Text(text = "لغة التطبيق", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.updateProfile(language = "ar") },
                    modifier = Modifier.weight(1f),
                    colors = if (userSettings.appLanguage == "ar") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("العربية 🇪🇬")
                }
                OutlinedButton(
                    onClick = { viewModel.updateProfile(language = "en") },
                    modifier = Modifier.weight(1f),
                    colors = if (userSettings.appLanguage == "en") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("English 🇬🇧")
                }
            }
        }
    }
}

/**
 * 3. Notifications & Reliability Protection Section (Deduplicated & Clean)
 */
@Composable
fun NotificationProtectionTabSection(
    context: android.content.Context,
    isEnglish: Boolean,
    exactAlarmGranted: Boolean,
    overlayGranted: Boolean,
    notificationPermissionGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    dndGranted: Boolean,
    lastScheduledTime: Long,
    lastReceivedTime: Long,
    onOpenUniversalDialog: () -> Unit,
    onRefresh: () -> Unit,
    onRequestNotification: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero Action Card: Smart Alarm & Protection Dialog
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نظام إنذار ذكي وحماية الإشعارات 🛡️",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "اضبط الصلاحيات والإعدادات الـ 5 لضمان تشغيل الأذان والمنبه والمذكرات في الخلفية دون توقف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { onNavigate("notification_reliability_check") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Full Reliability Report 📋" else "عرض تقرير الموثوقية الكامل 📋",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onOpenUniversalDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح فحص وضبط الأذونات الـ 5 ⚡", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            NotificationReliabilityManager.showInstantTestNotification(context, isEnglish)
                            onRefresh()
                            Toast.makeText(context, "تم إرسال إشعار الاختبار الفوري 🔔", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إشعار فوري 🔔", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            NotificationReliabilityManager.scheduleTestAlarm(context, delayMinutes = 1, isEnglish = isEnglish)
                            onRefresh()
                            Toast.makeText(context, "تمت جدولة تنبيه اختبار بعد دقيقة ⏱️", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("جدولة (دقيقة) ⏱️", fontSize = 12.sp)
                    }
                }
            }
        }

        // Clean Status Checklist Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "حالة الصلاحيات الحيوية (الأذونات الـ 5)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                PermissionStatusRow(
                    title = "إذن جدولة التنبيهات بدقة (Exact Alarm)",
                    isOk = exactAlarmGranted,
                    onClickFix = { NotificationReliabilityManager.openExactAlarmSettings(context) }
                )

                PermissionStatusRow(
                    title = "الظهور فوق التطبيقات وشاشة القفل (Overlay)",
                    isOk = overlayGranted,
                    onClickFix = { NotificationReliabilityManager.openOverlaySettings(context) }
                )

                PermissionStatusRow(
                    title = "استلام الإشعارات (POST_NOTIFICATIONS)",
                    isOk = notificationPermissionGranted,
                    onClickFix = onRequestNotification
                )

                PermissionStatusRow(
                    title = "تجاهل تحسين البطارية (Battery Saver)",
                    isOk = batteryOptimizationIgnored,
                    onClickFix = { NotificationReliabilityManager.openBatteryOptimizationSettings(context) }
                )

                PermissionStatusRow(
                    title = "عدم الإزعاج (Do Not Disturb Bypass)",
                    isOk = dndGranted,
                    onClickFix = { NotificationReliabilityManager.openNotificationPolicySettings(context) }
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isOk: Boolean,
    onClickFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isOk) Color(0xFF10B981).copy(alpha = 0.10f) else Color(0xFFEF4444).copy(alpha = 0.10f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (!isOk) {
            TextButton(
                onClick = onClickFix,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("منح الإذن", fontSize = 11.5.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "مفعل ✅",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981)
            )
        }
    }
}

/**
 * 4. Prayer & Azkar Section
 */
@Composable
fun PrayerAndAzkarTabSection(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    isEnglish: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "خيارات وتنبيهات الصلاة والأذكار 🕌",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("تفعيل إشعارات مواقيت الصلاة", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("إرسال تنبيه في موعد كل صلاة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = userSettings.isPrayerNotificationEnabled,
                    onCheckedChange = { viewModel.updatePrayerSettings(it, userSettings.isAdhanFullAudio, userSettings.muezzinVoice) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("تشغيل الأذان الصوتي الكامل", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("سماع صوت الأذان كاملاً عند دخول الوقت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = userSettings.isAdhanFullAudio,
                    onCheckedChange = { viewModel.updatePrayerSettings(userSettings.isPrayerNotificationEnabled, it, userSettings.muezzinVoice) }
                )
            }

            HorizontalDivider()

            // Azkar Reminders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("أذكار الصباح والمساء", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("تذكير يومي بقراءة الأذكار الحصينة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = userSettings.isMorningAzkarEnabled,
                    onCheckedChange = { viewModel.updateAzkarSettings(morningEnabled = it, eveningEnabled = userSettings.isEveningAzkarEnabled) }
                )
            }
        }
    }
}
