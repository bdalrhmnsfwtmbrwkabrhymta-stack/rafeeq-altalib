package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import com.example.data.preferences.UserSettings
import com.example.domain.prayer.CalculationMethod
import com.example.domain.prayer.PrayerDataConstants
import com.example.domain.prayer.SalawatAudioData
import com.example.ui.MainViewModel
import com.example.ui.screens.islamic.AdhanDiagAuraIcon
import com.example.ui.screens.islamic.AdhanSettingsSubScreen
import com.example.ui.screens.islamic.AdhanSpeakerAuraIcon
import com.example.ui.screens.islamic.AzkarDailySubScreen
import com.example.ui.screens.islamic.AzkarSunriseAuraIcon
import com.example.ui.screens.islamic.FullMushafAuraIcon
import com.example.ui.screens.islamic.HeroMosqueAuraIcon
import com.example.ui.screens.islamic.IslamicHubGridCard
import com.example.ui.screens.islamic.IslamicSubSection
import com.example.ui.screens.islamic.IslamicTopBar
import com.example.ui.screens.islamic.KaabaQiblaAuraIcon
import com.example.ui.screens.islamic.PrayerClockAuraIcon
import com.example.ui.screens.islamic.PrayerLogAuraIcon
import com.example.ui.screens.islamic.PrayerTimesDetailSubScreen
import com.example.ui.screens.islamic.PrayerTrackerLogSubScreen
import com.example.ui.screens.islamic.QuranHifzAuraIcon
import com.example.ui.screens.islamic.QuranHifzSubScreen
import com.example.ui.screens.islamic.SalawatStarAuraIcon
import com.example.ui.screens.islamic.SalawatSubScreen
import com.example.ui.screens.islamic.TasbeehBeadsAuraIcon
import com.example.ui.screens.islamic.TasbeehSubScreen
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.DisplayArabicFontFamily
import java.util.Locale

data class AzkarItem(val text: String, val targetCount: Int)

private enum class IslamicCardIconKind {
    CLOCK,
    SPEAKER,
    KAABA,
    SUNRISE,
    STAR,
    SPARKLE,
    BOOK,
    CLIPBOARD,
    WRENCH,
    MUSHAF
}

private data class IslamicHubTileSpec(
    val title: String,
    val subtitle: String,
    val auraPrimary: Color,
    val auraSecondary: Color,
    val testTag: String,
    val iconKind: IslamicCardIconKind,
    val section: IslamicSubSection? = null,
    val externalRoute: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    viewModel: MainViewModel,
    initialSection: IslamicSubSection = IslamicSubSection.HUB,
    onNavigate: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"
    val dailyAdhanSchedule = remember(
        userSettings.latitude,
        userSettings.longitude,
        userSettings.cityLocation,
        userSettings.countryLocation,
        userSettings.calculationMethod,
        userSettings.dstMode,
        isEnglish
    ) {
        com.example.domain.prayer.AdhanPrayerService.calculateDailySchedule(
            latitude = userSettings.latitude,
            longitude = userSettings.longitude,
            cityName = userSettings.cityLocation.ifBlank { "القاهرة" },
            countryName = userSettings.countryLocation.ifBlank { "مصر" },
            methodName = userSettings.calculationMethod,
            dstMode = userSettings.dstMode,
            isEnglish = isEnglish
        )
    }

    var currentSection by remember(initialSection) { mutableStateOf(initialSection) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showQuickSettingsDialog by remember { mutableStateOf(false) }
    var showLocationModalSheet by remember { mutableStateOf(false) }

    val azanAudioPlayer = remember { AzanAudioPlayer(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.detectAndApplyGpsLocation(context) { success, msg ->
                Toast.makeText(
                    context,
                    if (success) "تم تحديد موقعك بدقة وحساب المواقيت: $msg" else msg,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(context, "يرجى منح إذن الموقع الجغرافي لحساب المواقيت", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            azanAudioPlayer.stopAudio()
        }
    }

    val returnFromSubSection: () -> Unit = {
        azanAudioPlayer.stopAudio()
        if (initialSection == IslamicSubSection.HUB) {
            currentSection = IslamicSubSection.HUB
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        if (currentSection != IslamicSubSection.HUB && initialSection == IslamicSubSection.HUB) {
            azanAudioPlayer.stopAudio()
            currentSection = IslamicSubSection.HUB
        } else {
            onNavigateBack()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (currentSection) {
            IslamicSubSection.PRAYER_TIMES -> {
                PrayerTimesDetailSubScreen(
                    viewModel = viewModel,
                    userSettings = userSettings,
                    dailySchedule = dailyAdhanSchedule,
                    azanAudioPlayer = azanAudioPlayer,
                    onOpenLocationSheet = { showLocationModalSheet = true },
                    onOpenAdhanSettings = { currentSection = IslamicSubSection.ADHAN },
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.ADHAN -> {
                AdhanSettingsSubScreen(
                    viewModel = viewModel,
                    userSettings = userSettings,
                    azanAudioPlayer = azanAudioPlayer,
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.QIBLA -> {
                QiblaCompassScreen(
                    viewModel = viewModel,
                    onNavigateBack = returnFromSubSection
                )
            }

            IslamicSubSection.AZKAR -> {
                AzkarDailySubScreen(
                    azanAudioPlayer = azanAudioPlayer,
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.SALAWAT -> {
                SalawatSubScreen(
                    viewModel = viewModel,
                    userSettings = userSettings,
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.TASBEEH -> {
                TasbeehSubScreen(
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.HIFZ -> {
                QuranHifzSubScreen(
                    onOpenFullMushaf = { onNavigate("quran") },
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.PRAYER_LOG -> {
                PrayerTrackerLogSubScreen(
                    onBack = returnFromSubSection
                )
            }

            IslamicSubSection.HUB -> {
                val primaryColor = MaterialTheme.colorScheme.primary
                val isDark = MaterialTheme.colorScheme.surface.let {
                    (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.45f
                }

                // Exact 10-card sequence and layout with clean Arabic copy & soft pastel aura colors ("أطياف")
                val hubTiles = remember(primaryColor) {
                    listOf(
                        IslamicHubTileSpec(
                            title = "مواقيت الصلاة",
                            subtitle = "مواقيت اليوم والصلوات القادمة",
                            auraPrimary = Color(0xFF3B82F6),
                            auraSecondary = Color(0xFF60A5FA),
                            testTag = "islamic_card_prayer_times",
                            iconKind = IslamicCardIconKind.CLOCK,
                            section = IslamicSubSection.PRAYER_TIMES
                        ),
                        IslamicHubTileSpec(
                            title = "الأذان",
                            subtitle = "اختيار الصوت وإعدادات التنبيه",
                            auraPrimary = Color(0xFFF59E0B),
                            auraSecondary = Color(0xFFFBBF24),
                            testTag = "islamic_card_adhan",
                            iconKind = IslamicCardIconKind.SPEAKER,
                            section = IslamicSubSection.ADHAN
                        ),
                        IslamicHubTileSpec(
                            title = "القبلة",
                            subtitle = "بوصلة حية واتجاه الكعبة",
                            auraPrimary = Color(0xFF64748B),
                            auraSecondary = Color(0xFFF59E0B),
                            testTag = "islamic_card_qibla",
                            iconKind = IslamicCardIconKind.KAABA,
                            section = IslamicSubSection.QIBLA
                        ),
                        IslamicHubTileSpec(
                            title = "أذكار الصباح والمساء",
                            subtitle = "ورد يومي بعداد محفوظ",
                            auraPrimary = Color(0xFFFB923C),
                            auraSecondary = Color(0xFF8B5CF6),
                            testTag = "islamic_card_azkar",
                            iconKind = IslamicCardIconKind.SUNRISE,
                            section = IslamicSubSection.AZKAR
                        ),
                        IslamicHubTileSpec(
                            title = "الصلاة على النبي",
                            subtitle = "عداد وتذكيرات على مدار اليوم",
                            auraPrimary = Color(0xFFD97706),
                            auraSecondary = Color(0xFFFDE047),
                            testTag = "islamic_card_salawat",
                            iconKind = IslamicCardIconKind.STAR,
                            section = IslamicSubSection.SALAWAT
                        ),
                        IslamicHubTileSpec(
                            title = "خدمات التسبيح",
                            subtitle = "سبحة تفاعلية ٣D • دورة ١٠٠ (صحيح مسلم ٥٩٧)",
                            auraPrimary = Color(0xFF8B5CF6),
                            auraSecondary = Color(0xFFC084FC),
                            testTag = "islamic_card_tasbeeh",
                            iconKind = IslamicCardIconKind.SPARKLE,
                            section = IslamicSubSection.TASBEEH
                        ),
                        IslamicHubTileSpec(
                            title = "حفظ القرآن",
                            subtitle = "حدد سورة وآيات واختبر حفظك",
                            auraPrimary = Color(0xFF10B981),
                            auraSecondary = Color(0xFF34D399),
                            testTag = "islamic_card_hifz",
                            iconKind = IslamicCardIconKind.BOOK,
                            section = IslamicSubSection.HIFZ
                        ),
                        IslamicHubTileSpec(
                            title = "سجل الصلوات",
                            subtitle = "سجل كل صلاة وتابع سلسلتك",
                            auraPrimary = Color(0xFF14B8A6),
                            auraSecondary = Color(0xFF10B981),
                            testTag = "islamic_card_prayer_log",
                            iconKind = IslamicCardIconKind.CLIPBOARD,
                            section = IslamicSubSection.PRAYER_LOG
                        ),
                        IslamicHubTileSpec(
                            title = "تشخيص الأذان",
                            subtitle = "افحص صلاحيات الإشعارات والصوت",
                            auraPrimary = Color(0xFFEA580C),
                            auraSecondary = Color(0xFF10B981),
                            testTag = "islamic_card_diagnostics",
                            iconKind = IslamicCardIconKind.WRENCH,
                            externalRoute = "notification_reliability_check"
                        ),
                        IslamicHubTileSpec(
                            title = "القرآن الكريم",
                            subtitle = "افتح المصحف والتلاوات",
                            auraPrimary = primaryColor,
                            auraSecondary = Color(0xFFF59E0B),
                            testTag = "islamic_card_quran",
                            iconKind = IslamicCardIconKind.MUSHAF,
                            externalRoute = "quran"
                        )
                    )
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        IslamicTopBar(
                            title = "الإسلاميات",
                            onBack = onNavigateBack,
                            trailingAction = {
                                IconButton(
                                    onClick = { showInfoDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("islamic_info_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "معلومات القسم",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Deep Obsidian-to-Navy Hero Banner with high-contrast white text matching Image 3 & app theme
                        item {
                            val heroBrush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0A1322),
                                    Color(0xFF102447),
                                    primaryColor.copy(alpha = 0.85f)
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(heroBrush)
                                    .padding(horizontal = 18.dp, vertical = 22.dp)
                                    .testTag("islamic_hero_banner")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Open glowing multi-tone mosque blended directly into hero banner (zero square frame)
                                    HeroMosqueAuraIcon(
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "صلاتك ووردك في مكان واحد",
                                            fontFamily = DisplayArabicFontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "المواقيت، الأذان، القبلة، الأذكار، والحفظ",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }

                        // 2-Column 10-Card Grid with multi-tone Aura ("أطياف خفيفة") illustrations
                        items(hubTiles.chunked(2)) { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair.forEach { spec ->
                                    IslamicHubGridCard(
                                        title = spec.title,
                                        subtitle = spec.subtitle,
                                        auraPrimaryColor = spec.auraPrimary,
                                        auraSecondaryColor = spec.auraSecondary,
                                        onClick = {
                                            when {
                                                spec.section != null -> currentSection = spec.section
                                                spec.externalRoute != null -> onNavigate(spec.externalRoute)
                                            }
                                        },
                                        testTag = spec.testTag,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        when (spec.iconKind) {
                                            IslamicCardIconKind.CLOCK -> PrayerClockAuraIcon()
                                            IslamicCardIconKind.SPEAKER -> AdhanSpeakerAuraIcon()
                                            IslamicCardIconKind.KAABA -> KaabaQiblaAuraIcon()
                                            IslamicCardIconKind.SUNRISE -> AzkarSunriseAuraIcon()
                                            IslamicCardIconKind.STAR -> SalawatStarAuraIcon()
                                            IslamicCardIconKind.SPARKLE -> TasbeehBeadsAuraIcon()
                                            IslamicCardIconKind.BOOK -> QuranHifzAuraIcon()
                                            IslamicCardIconKind.CLIPBOARD -> PrayerLogAuraIcon()
                                            IslamicCardIconKind.WRENCH -> AdhanDiagAuraIcon()
                                            IslamicCardIconKind.MUSHAF -> FullMushafAuraIcon()
                                        }
                                    }
                                }
                                if (rowPair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Bottom Hint Footer
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(19.dp)
                                )
                                Text(
                                    text = "كل أدوات الصلاة والحفظ هنا في مكان واحد، وإذا احتجت شرحاً إضافياً اضغط علامة المعلومات بالأعلى.",
                                    fontFamily = CairoFontFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Mosque,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "عن قسم الإسلاميات",
                        fontFamily = DisplayArabicFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "يجمع هذا القسم جميع أدوات العبادة اليومية: مواقيت الصلاة الدقيقة لمدينتك، إعدادات المؤذن وتنبيهات الأذان، بوصلة القبلة ثلاثية الأبعاد، أذكار الصباح والمساء، عداد الصلاة على النبي ﷺ، السبحة الإلكترونية، متابع حفظ القرآن، سجل الصلوات اليومي، وتشخيص الإشعارات.",
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showInfoDialog = false
                            showQuickSettingsDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("الإعدادات المتقدمة", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }

        if (showQuickSettingsDialog) {
            IslamicToolsAndSettingsDialog(
                userSettings = userSettings,
                viewModel = viewModel,
                azanAudioPlayer = azanAudioPlayer,
                isEnglish = isEnglish,
                onDismiss = {
                    showQuickSettingsDialog = false
                    azanAudioPlayer.stopAudio()
                }
            )
        }

        if (showLocationModalSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLocationModalSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 380.dp, max = 560.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "موقع الصلاة والمواقيت",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        FilledTonalButton(
                            onClick = {
                                val hasLocationPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasLocationPerm) {
                                    viewModel.detectAndApplyGpsLocation(context) { success, msg ->
                                        Toast.makeText(
                                            context,
                                            if (success) "تم تحديث الموقع: $msg" else msg,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    showLocationModalSheet = false
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            enabled = !isDetectingLocation,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDetectingLocation) "جارٍ التحديد..." else "موقعي الحالي", fontSize = 12.sp)
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        CityAndLocationTabContent(
                            userSettings = userSettings,
                            viewModel = viewModel,
                            isEnglish = isEnglish,
                            onLocationSelected = { showLocationModalSheet = false }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Islamic Tools & Settings Hub dialog containing all controls:
 * 1. Salawat & Azkar (Interval, Enable/Mute, Custom Times for Morning 9:00 / Evening 5:00, Audio previews)
 * 2. Azan & Muezzin (Per-prayer Muting, Muezzin selection with previews, Fullscreen adhan preview)
 * 3. City & Location (Egyptian cities, Search, Map picker, Calculation Method, DST)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicToolsAndSettingsDialog(
    userSettings: UserSettings,
    viewModel: MainViewModel,
    azanAudioPlayer: AzanAudioPlayer,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewVoiceId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            azanAudioPlayer.stopAudio()
            previewVoiceId = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Adhan & Azkar Settings" else "إعدادات الأذان والتنبيهات ⚙️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // Tab Row (Azan & Muezzin, Salawat & Azkar, City & Location)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(if (isEnglish) "🕌 Azan & Voices" else "🕌 الأذان والمؤذن", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(if (isEnglish) "📿 Salawat & Azkar" else "📿 الصلاة والأذكار", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(if (isEnglish) "🌍 City & Location" else "🌍 المدينة والموقع", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> AzanAndMuezzinTabContent(
                            userSettings = userSettings,
                            viewModel = viewModel,
                            azanAudioPlayer = azanAudioPlayer,
                            previewVoiceId = previewVoiceId,
                            onPreviewVoiceChange = { previewVoiceId = it },
                            isEnglish = isEnglish
                        )
                        1 -> SalawatAndAzkarTabContent(
                            userSettings = userSettings,
                            viewModel = viewModel,
                            azanAudioPlayer = azanAudioPlayer,
                            isEnglish = isEnglish
                        )
                        2 -> CityAndLocationTabContent(
                            userSettings = userSettings,
                            viewModel = viewModel,
                            isEnglish = isEnglish,
                            onLocationSelected = {}
                        )
                    }
                }

                // Footer Done Button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(if (isEnglish) "Done" else "تم وحفظ الإعدادات", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 0: Salawat on the Prophet ﷺ & Morning/Evening Azkar Custom Timings and Mutes
 */
@Composable
fun SalawatAndAzkarTabContent(
    userSettings: UserSettings,
    viewModel: MainViewModel,
    azanAudioPlayer: AzanAudioPlayer,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var salawatEnabled by remember(userSettings.isSalawatEnabled) { mutableStateOf(userSettings.isSalawatEnabled) }
    var selectedInterval by remember(userSettings.salawatIntervalMinutes) { mutableIntStateOf(userSettings.salawatIntervalMinutes) }
    var isCustomMode by remember { mutableStateOf(false) }
    var customUnitIsHours by remember { mutableStateOf(false) }
    var customNumberText by remember { mutableStateOf("") }
    var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playingVoiceId by remember { mutableStateOf<Int?>(null) }
    var isSpeakingTts by remember { mutableStateOf(false) }
    val salawatDownloadProgress by viewModel.salawatDownloadProgress.collectAsState()
    var localVoicesVersion by remember { mutableIntStateOf(0) }

    val salawatFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "ملف صوتي خاص"
            viewModel.updateCustomSalawatAudio(uri.toString(), fileName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SalawatAudioData.stopTts()
            SalawatAudioData.stopPlayer(previewPlayer)
            previewPlayer = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Salawat on the Prophet ﷺ
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Prophet Reminder (Salawat) ﷺ 📿" else "التذكير بالصلاة على النبي ﷺ 📿",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (salawatEnabled) (if (isEnglish) "🟢 Active" else "🟢 شغال ومفعل") else (if (isEnglish) "⚪ Muted" else "⚪ مكتوم ومتوقف"),
                            fontSize = 11.sp,
                            color = if (salawatEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = salawatEnabled,
                        onCheckedChange = {
                            salawatEnabled = it
                            viewModel.updateSalawatSettings(it, selectedInterval)
                        }
                    )
                }

                if (salawatEnabled) {
                    Text(
                        text = if (isEnglish) "Repeat Frequency:" else "توقيت وتكرار التذكير:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val presetList = listOf(
                        1 to if (isEnglish) "Every 1 min" else "كل دقيقة واحدة",
                        2 to if (isEnglish) "Every 2 mins" else "كل دقيقتين",
                        5 to if (isEnglish) "Every 5 mins" else "كل 5 دقائق",
                        10 to if (isEnglish) "Every 10 mins" else "كل 10 دقائق",
                        15 to if (isEnglish) "Every 15 mins" else "كل 15 دقيقة",
                        30 to if (isEnglish) "Every 30 mins" else "كل نصف ساعة",
                        60 to if (isEnglish) "Every 1 Hour" else "كل ساعة كاملة",
                        120 to if (isEnglish) "Every 2 Hours" else "كل ساعتين"
                    )

                    presetList.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { (mins, label) ->
                                val isSelected = !isCustomMode && selectedInterval == mins
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        isCustomMode = false
                                        selectedInterval = mins
                                        viewModel.updateSalawatSettings(salawatEnabled, mins)
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Custom Interval Section
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, if (isCustomMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCustomMode = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = isCustomMode, onClick = { isCustomMode = true })
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Custom Timing (Manual)" else "تحديد توقيت مخصص يدوي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (isCustomMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !customUnitIsHours,
                                        onClick = { customUnitIsHours = false },
                                        label = { Text(if (isEnglish) "In Minutes" else "بالدقائق", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = customUnitIsHours,
                                        onClick = { customUnitIsHours = true },
                                        label = { Text(if (isEnglish) "In Hours" else "بالساعات", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customNumberText,
                                        onValueChange = { customNumberText = it.filter { ch -> ch.isDigit() } },
                                        placeholder = { Text(if (customUnitIsHours) "مثال: 3 ساعات" else "مثال: 20 دقيقة", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val num = customNumberText.toIntOrNull() ?: 1
                                            val calculated = if (customUnitIsHours) num * 60 else num
                                            selectedInterval = calculated.coerceAtLeast(1)
                                            viewModel.updateSalawatSettings(salawatEnabled, selectedInterval)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isEnglish) "Apply" else "تطبيق")
                                    }
                                }
                            }
                        }
                    }

                    // Audio Mode Selection
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish) "Choose Salawat Voice Source: 🎧" else "مصدر صوت الصلاة على النبي ﷺ: 🎧",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = userSettings.salawatAudioMode != "CUSTOM_FILE",
                            onClick = {
                                viewModel.updateSalawatAudioMode("RECORDING")
                            },
                            label = { Text(if (isEnglish) "App & Online Voices 🎙️" else "أصوات التطبيق والسحابية 🎙️", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = userSettings.salawatAudioMode == "CUSTOM_FILE",
                            onClick = {
                                viewModel.updateSalawatAudioMode("CUSTOM_FILE")
                            },
                            label = { Text(if (isEnglish) "Phone MP3 📂" else "صوت من الهاتف 📂", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (userSettings.salawatAudioMode == "CUSTOM_FILE") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "Selected Audio File:" else "الملف الصوتي المختار من هاتفك:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (userSettings.customSalawatAudioName.isNotBlank())
                                        "🎵 ${userSettings.customSalawatAudioName}"
                                    else
                                        if (isEnglish) "No file chosen yet" else "لم يتم اختيار ملف بعد (انقر على الزر لاختيار أي ملف MP3 من هاتفك)",
                                    fontSize = 11.sp,
                                    color = if (userSettings.customSalawatAudioName.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { salawatFilePickerLauncher.launch("audio/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isEnglish) "Choose MP3 File 📂" else "اختيار ملف صوتي من الهاتف 📂", fontSize = 11.sp)
                                    }

                                    if (userSettings.customSalawatAudioUri.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                if (previewPlayer?.isPlaying == true) {
                                                    SalawatAudioData.stopPlayer(previewPlayer)
                                                    previewPlayer = null
                                                } else {
                                                    SalawatAudioData.stopPlayer(previewPlayer)
                                                    previewPlayer = SalawatAudioData.playCustomAudioUri(context, userSettings.customSalawatAudioUri) {
                                                        previewPlayer = null
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (previewPlayer?.isPlaying == true) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = "تشغيل الصوت المخصص",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 1) Fixed Built-in Voices (from popular Play Store apps)
                        Text(
                            text = if (isEnglish) "Built-in App Voices (Always available offline 📱⚡):" else "أصوات مدمجة بالتطبيق (تعمل بدون نت من أشهر تطبيقات المتجر 📱⚡):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SalawatAudioData.builtInVoices.forEach { voiceItem ->
                                val isVoiceSelected = (userSettings.salawatVoiceId == voiceItem.id)
                                val isThisVoicePlaying = (playingVoiceId == voiceItem.id)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isVoiceSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isVoiceSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateSalawatVoiceId(voiceItem.id)
                                            viewModel.updateSalawatAudioMode("RECORDING")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RadioButton(
                                                selected = isVoiceSelected,
                                                onClick = {
                                                    viewModel.updateSalawatVoiceId(voiceItem.id)
                                                    viewModel.updateSalawatAudioMode("RECORDING")
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = voiceItem.title,
                                                        fontWeight = if (isVoiceSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 12.sp,
                                                        color = if (isVoiceSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                    ) {
                                                        Text(
                                                            text = voiceItem.sizeText,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = voiceItem.subtitle,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }

                                        // Play / Stop Preview Button
                                        IconButton(
                                            onClick = {
                                                if (isThisVoicePlaying) {
                                                    SalawatAudioData.stopPlayer(previewPlayer)
                                                    previewPlayer = null
                                                    playingVoiceId = null
                                                } else {
                                                    SalawatAudioData.stopPlayer(previewPlayer)
                                                    playingVoiceId = voiceItem.id
                                                    previewPlayer = SalawatAudioData.playVoiceAudio(context, voiceItem.id) {
                                                        playingVoiceId = null
                                                        previewPlayer = null
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isThisVoicePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = "استماع للصوت",
                                                tint = if (isThisVoicePlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2) Online Downloadable Voices (from Play Store apps)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isEnglish) "Additional Store Voices (Download on-demand ☁️💾):" else "أصوات إضافية من تطبيقات المتجر (تنزيل عند الطلب ☁️💾):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = if (isEnglish) "Download any voice you like. You can delete it anytime to free phone storage." else "حمّل نغمة التذكير التي تعجبك لتوفير مساحة الهاتف، ويمكنك حذفها في أي وقت:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SalawatAudioData.onlineDownloadableVoices.forEach { voiceItem ->
                                val isVoiceSelected = (userSettings.salawatVoiceId == voiceItem.id)
                                val isThisVoicePlaying = (playingVoiceId == voiceItem.id)
                                val isDownloaded = remember(localVoicesVersion) { SalawatAudioData.isVoiceDownloaded(context, voiceItem.id) }
                                val downloadPercent = salawatDownloadProgress[voiceItem.id]
                                val isDownloading = (downloadPercent != null)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isVoiceSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isVoiceSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (isDownloaded) {
                                                    RadioButton(
                                                        selected = isVoiceSelected,
                                                        onClick = {
                                                            viewModel.updateSalawatVoiceId(voiceItem.id)
                                                            viewModel.updateSalawatAudioMode("RECORDING")
                                                        }
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Cloud,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.padding(8.dp).size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = voiceItem.title,
                                                            fontWeight = if (isVoiceSelected) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 12.sp,
                                                            color = if (isVoiceSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isDownloaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                                                        ) {
                                                            Text(
                                                                text = if (isDownloaded) "محمّل ✅ (${voiceItem.sizeText})" else "سحابي ☁️ (${voiceItem.sizeText})",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = voiceItem.subtitle,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2
                                                    )
                                                }
                                            }

                                            // Actions (Preview play / stop)
                                            IconButton(
                                                onClick = {
                                                    if (isThisVoicePlaying) {
                                                        SalawatAudioData.stopPlayer(previewPlayer)
                                                        previewPlayer = null
                                                        playingVoiceId = null
                                                    } else {
                                                        SalawatAudioData.stopPlayer(previewPlayer)
                                                        playingVoiceId = voiceItem.id
                                                        previewPlayer = SalawatAudioData.playVoiceAudio(context, voiceItem.id) {
                                                            playingVoiceId = null
                                                            previewPlayer = null
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isThisVoicePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = "استماع للصوت",
                                                    tint = if (isThisVoicePlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        // Download / Delete Controls
                                        if (isDownloading) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                LinearProgressIndicator(
                                                    progress = { ((downloadPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                                )
                                                Text(
                                                    text = "${downloadPercent ?: 0}%",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else if (!isDownloaded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.downloadSalawatVoice(context, voiceItem.id) { success ->
                                                        localVoicesVersion++
                                                        if (success) {
                                                            Toast.makeText(context, "تم تنزيل صوت ${voiceItem.title} بنجاح واختياره 📿", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "فشل تنزيل الصوت، يرجى التأكد من اتصال الإنترنت", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isEnglish) "Download Voice (${voiceItem.sizeText}) 📥" else "تنزيل الصوت على الهاتف (${voiceItem.sizeText}) 📥",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            // Already downloaded: Show quick Delete option to free space
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        viewModel.deleteSalawatVoice(context, voiceItem.id)
                                                        localVoicesVersion++
                                                        Toast.makeText(context, "تم حذف الصوت لتوفير مساحة الهاتف 🗑️", Toast.LENGTH_SHORT).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isEnglish) "Delete to save space" else "حذف الملف لتوفير المساحة 🗑️",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Test Salawat Button
                    OutlinedButton(
                        onClick = {
                            SalawatAudioData.stopTts()
                            SalawatAudioData.stopPlayer(previewPlayer)
                            previewPlayer = null

                            if (userSettings.salawatAudioMode == "CUSTOM_FILE" && userSettings.customSalawatAudioUri.isNotBlank()) {
                                previewPlayer = SalawatAudioData.playCustomAudioUri(context, userSettings.customSalawatAudioUri) {
                                    previewPlayer = null
                                }
                            } else {
                                playingVoiceId = userSettings.salawatVoiceId
                                previewPlayer = SalawatAudioData.playVoiceAudio(context, userSettings.salawatVoiceId) {
                                    playingVoiceId = null
                                    previewPlayer = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Test Selected Salawat Sound 🔊" else "صَلِّ على محمد بالصوت المختار الآن 🔊",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 2: Morning & Evening Azkar (Timings & Mutes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEnglish) "Daily Azkar Reminders & Times 🌅" else "توقيت وكتم أذكار الصباح والمساء 🌅",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // 2.1 Morning Azkar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isEnglish) "Morning Azkar 🌅" else "أذكار الصباح المباركة 🌅",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val formattedMorningTime = String.format(Locale.US, "%02d:%02d", userSettings.morningAzkarHour, userSettings.morningAzkarMinute)
                                Text(
                                    text = if (userSettings.isMorningAzkarEnabled) "⏰ التوقيت المضبوط: $formattedMorningTime ص (افتراضي 09:00 ص)"
                                    else "🔕 التذكير مكتوم",
                                    fontSize = 11.sp,
                                    color = if (userSettings.isMorningAzkarEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = userSettings.isMorningAzkarEnabled,
                                onCheckedChange = { isEnabled ->
                                    viewModel.updateAzkarSettings(
                                        morningEnabled = isEnabled,
                                        eveningEnabled = userSettings.isEveningAzkarEnabled
                                    )
                                }
                            )
                        }

                        if (userSettings.isMorningAzkarEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val timePicker = TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                viewModel.updateMorningAzkarTime(hourOfDay, minute)
                                            },
                                            userSettings.morningAzkarHour,
                                            userSettings.morningAzkarMinute,
                                            false
                                        )
                                        timePicker.show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEnglish) "Change Time ⏱️" else "تعديل وقت الصباح ⏱️", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { azanAudioPlayer.playMorningAzkar() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEnglish) "Play Audio 🔊" else "استماع للتلاوة 🔊", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 2.2 Evening Azkar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isEnglish) "Evening Azkar 🌇" else "أذكار المساء المباركة 🌇",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val eveningHour12 = if (userSettings.eveningAzkarHour > 12) userSettings.eveningAzkarHour - 12 else userSettings.eveningAzkarHour
                                val formattedEveningTime = String.format(Locale.US, "%02d:%02d", eveningHour12, userSettings.eveningAzkarMinute)
                                Text(
                                    text = if (userSettings.isEveningAzkarEnabled) "⏰ التوقيت المضبوط: $formattedEveningTime م (افتراضي 05:00 م)"
                                    else "🔕 التذكير مكتوم",
                                    fontSize = 11.sp,
                                    color = if (userSettings.isEveningAzkarEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = userSettings.isEveningAzkarEnabled,
                                onCheckedChange = { isEnabled ->
                                    viewModel.updateAzkarSettings(
                                        morningEnabled = userSettings.isMorningAzkarEnabled,
                                        eveningEnabled = isEnabled
                                    )
                                }
                            )
                        }

                        if (userSettings.isEveningAzkarEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val timePicker = TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                viewModel.updateEveningAzkarTime(hourOfDay, minute)
                                            },
                                            userSettings.eveningAzkarHour,
                                            userSettings.eveningAzkarMinute,
                                            false
                                        )
                                        timePicker.show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEnglish) "Change Time ⏱️" else "تعديل وقت المساء ⏱️", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { azanAudioPlayer.playEveningAzkar() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEnglish) "Play Audio 🔊" else "استماع للتلاوة 🔊", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Adhan & Muezzin Voice selection, Prayer Alerts (Sunrise OFF by default), and Fullscreen preview
 */
@Composable
fun AzanAndMuezzinTabContent(
    userSettings: UserSettings,
    viewModel: MainViewModel,
    azanAudioPlayer: AzanAudioPlayer,
    previewVoiceId: String?,
    onPreviewVoiceChange: (String?) -> Unit,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prayerTimesResult by viewModel.prayerTimes.collectAsState()
    var showOnlineBankDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Global Adhan Notification Switch & Full Audio Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "General Adhan Notification" else "التنبيه العام لمواقيت الأذان",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (userSettings.isPrayerNotificationEnabled) (if (isEnglish) "Adhan is active" else "الأذان مفعل بصوت واضح")
                            else (if (isEnglish) "All Adhan notifications muted" else "كافة تنبيهات الأذان مكتومة"),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = userSettings.isPrayerNotificationEnabled,
                        onCheckedChange = { viewModel.updatePrayerNotificationEnabled(it) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Full Muezzin Adhan Audio" else "أذان كامل بصوت المؤذن",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (userSettings.isAdhanFullAudio) (if (isEnglish) "Full Adhan recital plays" else "يتم تشغيل التكبير والأذان كاملاً")
                            else (if (isEnglish) "Short notification chime" else "نغمة تنبيه هادئة ومختصرة"),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = userSettings.isAdhanFullAudio,
                        onCheckedChange = { viewModel.updateAdhanFullAudio(it) }
                    )
                }
            }
        }

        // Per-Prayer Alerts & Manual-Only Sunrise Toggle (Mockup 5)
        com.example.ui.prayer.PrayerAlertsSettingsScreen(
            prayerTimes = prayerTimesResult,
            mutedPrayers = userSettings.mutedPrayers,
            isSunriseAlertEnabled = userSettings.isSunriseAlertEnabled,
            onTogglePrayerMute = { prayerId -> viewModel.toggleMutedPrayer(prayerId) },
            onToggleSunriseAlert = { enabled -> viewModel.updateSunriseAlertEnabled(enabled) },
            showTopBar = false
        )

        // Studio-Grade 3-Voice HD Adhan Selector (Mockup 6)
        com.example.ui.prayer.AdhanAudioSelectorScreen(
            selectedVoiceName = userSettings.muezzinVoice,
            isAutoUpdateHighQualityAudio = userSettings.isAutoUpdateHighQualityAudio,
            playingVoiceId = previewVoiceId,
            azanAudioPlayer = azanAudioPlayer,
            onSelectVoice = { voiceName -> viewModel.updateMuezzinVoice(voiceName) },
            onPreviewVoiceChange = onPreviewVoiceChange,
            onToggleAutoUpdate = { enabled -> viewModel.updateAutoUpdateHighQualityAudio(enabled) },
            embedded = true
        )

        // Optional Cloud HQ Audio Pack Sync + Photorealistic Fullscreen Adhan Previews (Mockups 1-4)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFFDBEAFE))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isEnglish) "Photorealistic Fullscreen Adhan Preview" else "معاينة شاشات الأذان التفاعلية حسب الوقت",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF0F3D99)
                )

                val previewThemes = listOf("الفجر", "الشروق", "العصر", "المغرب")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    previewThemes.forEach { targetPrayer ->
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, com.example.ui.prayer.AdhanFullScreenActivity::class.java).apply {
                                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_PRAYER_NAME, targetPrayer)
                                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_CITY_NAME, userSettings.cityLocation)
                                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_MUEZZIN, userSettings.muezzinVoice)
                                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_AUTO_PLAY, true)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preview_adhan_$targetPrayer"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = targetPrayer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Button(
                    onClick = { showOnlineBankDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "HD Adhan Audio Pack Manager (320 kbps)" else "إدارة ومزامنة باقات الأذان 320 kbps HD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }

    if (showOnlineBankDialog) {
        com.example.ui.dialogs.OnlineMuezzinBankDialog(
            viewModel = viewModel,
            azanAudioPlayer = azanAudioPlayer,
            isEnglish = isEnglish,
            onDismiss = { showOnlineBankDialog = false }
        )
    }
}

/**
 * Tab 2: City & Location Selector (Egyptian Governorates & Cities, Search, Map picker, Calculation Method, DST)
 */
@Composable
fun CityAndLocationTabContent(
    userSettings: UserSettings,
    viewModel: MainViewModel,
    isEnglish: Boolean,
    onLocationSelected: () -> Unit
) {
    val scrollState = rememberScrollState()
    var subTab by remember { mutableIntStateOf(0) } // 0: Egyptian & World Cities, 1: Interactive Map & Calculation
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery) {
        if (searchQuery.isBlank()) PrayerDataConstants.COUNTRIES_CITIES
        else PrayerDataConstants.COUNTRIES_CITIES.filter {
            it.cityArabic.contains(searchQuery) || it.countryArabic.contains(searchQuery) ||
                    it.cityEnglish.contains(searchQuery, ignoreCase = true) || it.countryEnglish.contains(searchQuery, ignoreCase = true)
        }
    }

    var mapLat by remember { mutableDoubleStateOf(userSettings.latitude) }
    var mapLng by remember { mutableDoubleStateOf(userSettings.longitude) }
    var customCityName by remember { mutableStateOf(userSettings.cityLocation) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sub-tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text(if (isEnglish) "🏙️ Cities & Governorates" else "🏙️ المدن والمحافظات", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text(if (isEnglish) "🗺️ Map & Calculation" else "🗺️ الخريطة وطريقة الحساب", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        if (subTab == 0) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isEnglish) "Search city (e.g. Cairo, Damanhur)..." else "ابحث عن مدينة (مثال: دمنهور، القاهرة)...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Current Location Indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المدينة الحالية المضبوطة: ${userSettings.cityLocation} (${userSettings.countryLocation})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredList) { item ->
                    val isSelected = userSettings.cityLocation == item.cityArabic || userSettings.cityLocation == item.cityEnglish
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateLocationAndCountry(
                                    city = item.cityArabic,
                                    country = item.countryArabic,
                                    lat = item.latitude,
                                    lng = item.longitude,
                                    method = item.defaultMethod
                                )
                                onLocationSelected()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isEnglish) "${item.cityEnglish}, ${item.countryEnglish}" else "${item.cityArabic} - ${item.countryArabic}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "📍 ${String.format(Locale.US, "%.2f", item.latitude)}°, ${String.format(Locale.US, "%.2f", item.longitude)}° • ${item.defaultMethod.titleArabic}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            // SubTab 1: Interactive Map & Calculation Settings
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Interactive Map Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    val gridLineColor = Color.White.copy(alpha = 0.15f)
                    val pinColor = Color(0xFFEF4444)
                    val qiblaColor = Color(0xFF38BDF8)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        for (i in 1..5) {
                            drawLine(gridLineColor, Offset(i * w / 6f, 0f), Offset(i * w / 6f, h), strokeWidth = 1f)
                        }
                        for (j in 1..3) {
                            drawLine(gridLineColor, Offset(0f, j * h / 4f), Offset(w, j * h / 4f), strokeWidth = 1f)
                        }

                        val pinX = ((mapLng + 180.0) / 360.0 * w).toFloat().coerceIn(0f, w)
                        val pinY = ((90.0 - mapLat) / 180.0 * h).toFloat().coerceIn(0f, h)

                        val makkahX = ((39.8262 + 180.0) / 360.0 * w).toFloat()
                        val makkahY = ((90.0 - 21.4225) / 180.0 * h).toFloat()

                        drawLine(qiblaColor.copy(alpha = 0.6f), Offset(pinX, pinY), Offset(makkahX, makkahY), strokeWidth = 2f)
                        drawCircle(Color(0xFFF59E0B), radius = 6f, center = Offset(makkahX, makkahY))
                        drawCircle(pinColor, radius = 8f, center = Offset(pinX, pinY))
                        drawCircle(Color.White, radius = 3f, center = Offset(pinX, pinY))
                    }

                    Text(
                        text = "📍 إحداثيات الدبوس: (${String.format(Locale.US, "%.2f", mapLat)}°, ${String.format(Locale.US, "%.2f", mapLng)}°)",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Sliders
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("خط العرض (Lat):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.US, "%.4f", mapLat)}°", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = mapLat.toFloat(), onValueChange = { mapLat = it.toDouble() }, valueRange = -90f..90f, modifier = Modifier.height(26.dp))
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("خط الطول (Lng):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.US, "%.4f", mapLng)}°", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = mapLng.toFloat(), onValueChange = { mapLng = it.toDouble() }, valueRange = -180f..180f, modifier = Modifier.height(26.dp))
                }

                OutlinedTextField(
                    value = customCityName,
                    onValueChange = { customCityName = it },
                    label = { Text("اسم المدينة أو المنطقة", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val method = try { CalculationMethod.valueOf(userSettings.calculationMethod) } catch (e: Exception) { CalculationMethod.EGYPTIAN }
                        viewModel.updateLocationAndCountry(
                            city = if (customCityName.isNotBlank()) customCityName else "موقع مخصص",
                            country = "تحديد يدوي",
                            lat = mapLat,
                            lng = mapLng,
                            method = method
                        )
                        onLocationSelected()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ الموقع الجغرافي المحدد")
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Astronomical Calculation Method Selection
                Text("طريقة الحساب الفلكية للمواقيت:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CalculationMethod.values()) { method ->
                        val isMethodSelected = userSettings.calculationMethod == method.name
                        FilterChip(
                            selected = isMethodSelected,
                            onClick = { viewModel.updatePrayerCalculationMethod(method) },
                            label = { Text(method.titleArabic, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    }
}
