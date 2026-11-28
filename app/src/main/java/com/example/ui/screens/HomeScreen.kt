package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.telegram.DailyTelegramReportHelper
import com.example.ui.MainViewModel
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.ai.SecretDeveloperControlDialogs
import com.example.ui.theme.CURATED_ACCENT_CHOICES
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.example.ui.theme.ReadexProFontFamily
import com.example.ui.theme.VibrantAmber
import com.example.ui.theme.VibrantCoral
import com.example.ui.theme.VibrantEmerald
import com.example.ui.theme.VibrantPurple
import com.example.ui.theme.VibrantRoyalBlue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private data class HomeToolCardSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val backgroundColor: Color
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val pomodoroLogs by viewModel.pomodoroLogs.collectAsState()
    val examCountdowns by viewModel.examCountdowns.collectAsState()
    val prayerTimesResult by viewModel.prayerTimes.collectAsState()

    val completedTasksCount = tasks.count { it.isCompleted }
    val totalTasksCount = tasks.size
    val pendingTasksCount = tasks.count { !it.isCompleted }
    val todayProgressPercent = if (totalTasksCount > 0) {
        ((completedTasksCount.toFloat() / totalTasksCount.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else {
        0
    }

    val startOfDayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayStudyMinutes = remember(pomodoroLogs, startOfDayMillis) {
        pomodoroLogs.filter { it.timestampMillis >= startOfDayMillis && it.sessionType == "FOCUS" }
            .sumOf { it.durationMinutes }
    }
    val streakDays = userSettings.currentGlobalStreak

    val waterCups = userSettings.dailyWaterCups
    val waterGoal = userSettings.dailyWaterGoalCups.coerceAtLeast(8)
    val points = userSettings.studentPoints

    var showAccentPickerBar by remember { mutableStateOf(false) }
    var showExamDialog by remember { mutableStateOf(false) }
    var showQuickFahimCommandDialog by remember { mutableStateOf(false) }
    var showDevPinDialog by remember { mutableStateOf(false) }
    var showDevPanelDialog by remember { mutableStateOf(false) }
    var headerTapCount by remember { mutableIntStateOf(0) }
    var lastHeaderTapMs by remember { mutableLongStateOf(0L) }

    // Live clock ticker for Prayer countdown
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val nextPrayerName = prayerTimesResult?.nextPrayerName ?: "العصر"
    val nextPrayerMs = prayerTimesResult?.nextPrayerTimeMs ?: 0L
    val prayerCountdownText = if (nextPrayerMs > currentTimeMs) {
        val diffSec = (nextPrayerMs - currentTimeMs) / 1000
        val hours = diffSec / 3600
        val mins = (diffSec % 3600) / 60
        val secs = diffSec % 60
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        "00:14:41"
    }

    val upcomingExams = remember(examCountdowns, currentTimeMs) {
        examCountdowns.filter { it.examTimestampMillis >= currentTimeMs - 86_400_000L }
            .sortedBy { it.examTimestampMillis }
    }
    val nearestExam = upcomingExams.firstOrNull()
    val nearestExamDaysLeft = nearestExam?.let {
        max(0L, (it.examTimestampMillis - currentTimeMs) / 86_400_000L)
    }

    val displayGreetingName = remember(userSettings.userName) {
        val raw = userSettings.userName.trim()
        when {
            raw.isBlank() || raw == "طالب زائر" || raw == "الطالب المثالي" -> "ضيف"
            else -> raw
        }
    }

    // Study Tips (clean without dense tashkeel)
    val dailyTips = remember {
        listOf(
            "حل امتحاناً قديماً قبل الامتحان بأسبوع — ده أفضل تدريب",
            "ذاكر 25 دقيقة بتركيز تام وافصل 5 دقائق راحة لتضاعف استيعابك",
            "اشرح الدرس بصوت عالٍ كأنك تدرسه لغيرك — لو قدرت تشرحه يبقى فهمته",
            "ابدأ بأصعب مادة في بداية يومك لما طاقتك الذهنية تكون في قمتها"
        )
    }
    var tipIndex by remember { mutableIntStateOf(0) }

    val dayOfWeek = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    val isThursdayOrFriday = dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.THURSDAY
    val studentTelegram = if (userSettings.telegramNumber.isNotBlank()) userSettings.telegramNumber else userSettings.whatsappNumber

    // Primary 12 Tool Cards in Exact Order (Clean text without dense tashkeel)
    val primaryToolCards = remember {
        listOf(
            // Row 1
            HomeToolCardSpec("المكتبة الدراسية", "كل الكتب والمناهج", Icons.Default.LocalLibrary, "library", VibrantRoyalBlue),
            HomeToolCardSpec("فهيم", "اسأل واشرح", Icons.Default.TipsAndUpdates, "ai", VibrantEmerald),
            // Row 2
            HomeToolCardSpec("منهج الثانوي", "مواد ومسارات", Icons.Default.School, "grade_selection", VibrantPurple),
            HomeToolCardSpec("امتحان", "اختبار بالدرجات", Icons.Default.Quiz, "quests", VibrantAmber),
            // Row 3
            HomeToolCardSpec("تركيز", "جلسات مذاكرة", Icons.Default.Timer, "pomodoro", VibrantEmerald),
            HomeToolCardSpec("بنك الأسئلة", "بنك الأسئلة اللي في مكتبتك", Icons.Default.FolderCopy, "documents", VibrantRoyalBlue),
            // Row 4
            HomeToolCardSpec("الآلة الحاسبة", "علمية متقدمة", Icons.Default.Calculate, "calculator", VibrantCoral),
            HomeToolCardSpec("دليل الإعراب", "قواعد وأمثلة", Icons.Default.MenuBook, "irab", VibrantEmerald),
            // Row 5
            HomeToolCardSpec("استرجاع نشط", "كارت المراجعة", Icons.Default.Style, "flashcards", VibrantPurple),
            HomeToolCardSpec("الخريطة الذهنية", "ارسم أفكارك", Icons.Default.Brush, "whiteboard", VibrantCoral),
            // Row 6
            HomeToolCardSpec("المهام", "واجباتك وتذكيراتك", Icons.Default.Checklist, "tasks", VibrantAmber),
            HomeToolCardSpec("أهداف اليوم", "خطتك النهاردة", Icons.Default.EventNote, "schedule", VibrantPurple)
        )
    }

    // Preserved Additional Existing Features in matching vibrant style
    val extraFeatureCards = remember {
        listOf(
            HomeToolCardSpec("مركز الألعاب التعليمية", "ركض المتفوقين • 12 مرحلة ومتجر", Icons.Default.SportsEsports, "educational_games_hub", VibrantRoyalBlue),
            HomeToolCardSpec("التحميل الذكي السريع", "يوتيوب، تيك توك، بنترست، فيسبوك وإنستغرام", Icons.Default.CloudDownload, "smart_download", VibrantRoyalBlue),
            HomeToolCardSpec("الرسالة • تحميل المحاضرات", "يوتيوب، بينتيريست، تيك توك وPDF", Icons.Default.CloudDownload, "rafid", VibrantRoyalBlue),
            HomeToolCardSpec("القرآن الكريم", "المصحف والتلاوة", Icons.Default.AutoStories, "quran", VibrantEmerald),
            HomeToolCardSpec("الإسلاميات", "صلاتك ووردك في مكان واحد", Icons.Default.Mosque, "prayer", VibrantRoyalBlue),
            HomeToolCardSpec("القاموس واللغات", "ترجمة وحفظ كلمات", Icons.Default.Translate, "dictionary", VibrantPurple),
            HomeToolCardSpec("استوديو الـ PDF", "تصفح ومذاكرة الكتب", Icons.Default.PictureAsPdf, "pdf_studio", VibrantCoral),
            HomeToolCardSpec("تعلم الإنجليزية", "قواعد ومفردات", Icons.Default.Language, "english_learning", VibrantRoyalBlue),
            HomeToolCardSpec("الملاحظات", "تدوين سريع وقوائم", Icons.Default.EditNote, "notes", VibrantAmber),
            HomeToolCardSpec("الماسح الضوئي", "تلخيص من الصور", Icons.Default.DocumentScanner, "image_ocr_summary", VibrantPurple),
            HomeToolCardSpec("بناء العادات", "التزام وتذكير يومي", Icons.Default.Loop, "habits", VibrantEmerald),
            HomeToolCardSpec("ألعاب الذكاء", "تحديات وشطرنج", Icons.Default.SportsEsports, "brain_games", VibrantCoral),
            HomeToolCardSpec("أسماء الله الحسنى", "المعاني والتدبر", Icons.Default.Favorite, "god_names", VibrantEmerald),
            HomeToolCardSpec("مخزن الأفكار", "احفظ أفكارك", Icons.Default.Lightbulb, "idea_vault", VibrantAmber),
            HomeToolCardSpec("الدفعات والزكاة", "حساب الزكاة والميزانية", Icons.Default.AccountBalanceWallet, "zakat", VibrantRoyalBlue)
        )
    }

    val canvasBg = MaterialTheme.colorScheme.background
    val cardSurface = MaterialTheme.colorScheme.surface
    val textMain = MaterialTheme.colorScheme.onSurface
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryAccent = MaterialTheme.colorScheme.primary
    val isDarkTheme = (canvasBg.red * 0.299f + canvasBg.green * 0.587f + canvasBg.blue * 0.114f) < 0.45f
    val zajilHeroBlue = if (userSettings.appThemePreset.equals("ROYAL_BLUE", ignoreCase = true) ||
        userSettings.appThemePreset.isBlank()
    ) Color(0xFF0F62FE) else primaryAccent

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasBg)
        ) {
            val isLandscape = maxWidth > maxHeight
            val toolColumns = when {
                maxWidth >= 840.dp -> 4
                isLandscape -> 3
                else -> 2
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ==========================================================
                // 1. TOP HEADER (Drawer + هوس الدراسة + Theme/Notifications/Settings)
                // ==========================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(canvasBg)
                        .statusBarsPadding()
                        .padding(
                            horizontal = if (isLandscape) 16.dp else 12.dp,
                            vertical = if (isLandscape) 6.dp else 10.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Right (RTL Start): Clean unboxed Drawer button + App Title + Subtitle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 48.dp)
                                    .testTag("home_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "القائمة الجانبية",
                                    tint = textMain,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastHeaderTapMs <= 2500L) {
                                            headerTapCount++
                                        } else {
                                            headerTapCount = 1
                                        }
                                        lastHeaderTapMs = now
                                        if (headerTapCount >= 5) {
                                            headerTapCount = 0
                                            showDevPinDialog = true
                                        }
                                    }
                            ) {
                                val titleDiacriticColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF1E1B18)
                                val styledAppTitle = remember(isDarkTheme) {
                                    buildAnnotatedString {
                                        // RTL visual sweep: Royal Blue '#1A6FE3' -> Sky '#0EA5E9'
                                        withStyle(
                                            SpanStyle(
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color(0xFF1A6FE3), Color(0xFF0EA5E9))
                                                )
                                            )
                                        ) {
                                            append("م\u064Fتعل")
                                        }
                                        // Pure color-only SpanStyle avoids MetricAffectingSpan splitting HarfBuzz diacritic clusters
                                        withStyle(SpanStyle(color = titleDiacriticColor)) {
                                            append("\u0651\u0650")
                                        }
                                        withStyle(
                                            SpanStyle(
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color(0xFF1A6FE3), Color(0xFF0EA5E9))
                                                )
                                            )
                                        ) {
                                            append("م")
                                        }
                                    }
                                }
                                AutoSizeText(
                                    text = styledAppTitle,
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    maxFontSize = 23.sp,
                                    minFontSize = 16.sp,
                                    lineHeightRatio = 1.32f,
                                    fontWeight = FontWeight.Bold,
                                    overflowToTwoLinesAtMinSize = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_header_title")
                                )
                                AutoSizeText(
                                    text = "طريقك نحو مستقبل أفضل",
                                    fontFamily = ReadexProFontFamily,
                                    maxFontSize = 11.5.sp,
                                    minFontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    overflowToTwoLinesAtMinSize = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_header_subtitle")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Left (RTL End): Theme Switcher + Notification Bell (with coral badge) + Settings Gear
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HeaderCircleActionButton(
                                icon = Icons.Outlined.Palette,
                                contentDescription = "تبديل لون التطبيق",
                                tint = primaryAccent,
                                badgeColor = null,
                                surfaceColor = cardSurface,
                                onClick = { showAccentPickerBar = !showAccentPickerBar },
                                testTag = "home_theme_switcher_button"
                            )
                            HeaderCircleActionButton(
                                icon = Icons.Outlined.Notifications,
                                contentDescription = "التنبيهات والموثوقية",
                                tint = textMain,
                                badgeColor = VibrantCoral,
                                surfaceColor = cardSurface,
                                onClick = { onNavigate("notification_reliability_check") },
                                testTag = "home_notifications_button"
                            )
                            HeaderCircleActionButton(
                                icon = Icons.Outlined.Settings,
                                contentDescription = "الإعدادات",
                                tint = textMain,
                                badgeColor = null,
                                surfaceColor = cardSurface,
                                onClick = { onNavigate("settings") },
                                testTag = "home_settings_button"
                            )
                        }
                    }

                // Expandable Theme Accent Switcher Pill Strip
                AnimatedVisibility(visible = showAccentPickerBar) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardSurface,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "لون المظهر:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textMain
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CURATED_ACCENT_CHOICES.forEach { option ->
                                    val selected = userSettings.appThemePreset.equals(option.id, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 30.dp else 26.dp)
                                            .clip(CircleShape)
                                            .background(option.color)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) textMain else Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.setAppThemePreset(option.id)
                                                viewModel.setDarkMode(false)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = option.labelAr,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { onNavigate("app_theme") },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("المزيد", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================================
            // SCROLLABLE BODY (Portrait & Landscape Adaptive Layout)
            // ==========================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val quickAskBar: @Composable () -> Unit = {
                    Surface(
                        onClick = { showQuickFahimCommandDialog = true },
                        shape = RoundedCornerShape(50),
                        color = cardSurface,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_quick_ask_faheem")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TipsAndUpdates,
                                    contentDescription = "فهيم",
                                    tint = zajilHeroBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AutoSizeText(
                                    text = "اسأل فهيم عن أي حاجة...",
                                    fontFamily = DisplayArabicFontFamily,
                                    maxFontSize = 15.sp,
                                    minFontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMain,
                                    overflowToTwoLinesAtMinSize = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(zajilHeroBlue)
                                    .clickable { onNavigate("ai") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "اسأل فهيم",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                val dailyStatsPills: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CleanActivityStatPill(
                                icon = Icons.Outlined.LocalFireDepartment,
                                iconColor = Color(0xFFEA580C),
                                iconBg = Color(0xFFFFEDD5),
                                text = "$streakDays يوم متواصل",
                                surfaceColor = cardSurface,
                                textColor = textMain,
                                onClick = { onNavigate("quests") },
                                modifier = if (isLandscape) Modifier.weight(1f) else Modifier
                            )
                            CleanActivityStatPill(
                                icon = Icons.Outlined.AccessTime,
                                iconColor = zajilHeroBlue,
                                iconBg = Color(0xFFDBEAFE),
                                text = "$todayStudyMinutes دقيقة النهاردة",
                                surfaceColor = cardSurface,
                                textColor = textMain,
                                onClick = { onNavigate("pomodoro") },
                                modifier = if (isLandscape) Modifier.weight(1f) else Modifier
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            CleanActivityStatPill(
                                icon = Icons.Outlined.EventNote,
                                iconColor = Color(0xFF6D28D9),
                                iconBg = Color(0xFFEDE9FE),
                                text = "$pendingTasksCount مهمة مستنية",
                                surfaceColor = cardSurface,
                                textColor = textMain,
                                onClick = { onNavigate("tasks") }
                            )
                        }
                    }
                }

                // ----------------------------------------------------------
                // 2-4. HERO CARD + QUICK QUESTION + DAILY STATS
                // Side-by-side in Landscape, Stacked in Portrait
                // ----------------------------------------------------------
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.05f)) {
                            ZajilStackedHeroCard(
                                heroBlueColor = zajilHeroBlue,
                                studentGreetingName = displayGreetingName,
                                progressPercent = todayProgressPercent,
                                nextPrayerName = nextPrayerName,
                                prayerCountdownText = prayerCountdownText,
                                onPrayerClick = { onNavigate("prayer") },
                                onBookClick = { onNavigate("library") }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            quickAskBar()
                            dailyStatsPills()
                        }
                    }
                } else {
                    ZajilStackedHeroCard(
                        heroBlueColor = zajilHeroBlue,
                        studentGreetingName = displayGreetingName,
                        progressPercent = todayProgressPercent,
                        nextPrayerName = nextPrayerName,
                        prayerCountdownText = prayerCountdownText,
                        onPrayerClick = { onNavigate("prayer") },
                        onBookClick = { onNavigate("library") }
                    )
                    quickAskBar()
                    dailyStatsPills()
                }

                // ----------------------------------------------------------
                // 5. ROW OF 3 ORGANIC AURA CARDS WITH REALISTIC STUDIO IMAGES
                //    فهيم | ملفاتي بالمادة (شنطة حقيقية) | مهامي
                // ----------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OrganicAuraRealisticCard(
                        title = "فهيم",
                        subtitle = "اسأل عن أي حاجة",
                        imageRes = R.drawable.img_card_faheem,
                        auraBgColor = if (isDarkTheme) Color(0xFF281F44) else Color(0xFFF3EEFF),
                        auraAccentColor = Color(0xFF6D28D9),
                        titleColor = if (isDarkTheme) Color.White else Color(0xFF1E1B4B),
                        subtitleColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF4C1D95),
                        onClick = { onNavigate("ai") },
                        isCompactLandscape = isLandscape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_quick_card_faheem")
                    )
                    OrganicAuraRealisticCard(
                        title = "ملفاتي بالمادة",
                        subtitle = "شنطة كل مادة",
                        imageRes = R.drawable.img_card_backpack,
                        auraBgColor = if (isDarkTheme) Color(0xFF133329) else Color(0xFFE7FAF1),
                        auraAccentColor = Color(0xFF059669),
                        titleColor = if (isDarkTheme) Color.White else Color(0xFF064E3B),
                        subtitleColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF047857),
                        onClick = { onNavigate("library") },
                        isCompactLandscape = isLandscape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_quick_card_library")
                    )
                    OrganicAuraRealisticCard(
                        title = "مهامي",
                        subtitle = "رتب مهام يومك",
                        imageRes = R.drawable.img_card_tasks,
                        auraBgColor = if (isDarkTheme) Color(0xFF162A4A) else Color(0xFFEBF3FE),
                        auraAccentColor = Color(0xFF0F62FE),
                        titleColor = if (isDarkTheme) Color.White else Color(0xFF1E3A8A),
                        subtitleColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF1D4ED8),
                        onClick = { onNavigate("tasks") },
                        isCompactLandscape = isLandscape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_quick_card_tasks")
                    )
                }

                // ----------------------------------------------------------
                // 5B. STUDY TOOLS HUB FEATURED CARD («أَدَوَاتُ المُذَاكَرَة» - 8 Tools)
                // ----------------------------------------------------------
                Surface(
                    onClick = { onNavigate("study_tools") },
                    shape = RoundedCornerShape(24.dp),
                    color = if (isDarkTheme) Color(0xFF101B2D) else Color(0xFF0F172A),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFE6B34B).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_study_tools_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Color(0xFFE6B34B).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DashboardCustomize,
                                        contentDescription = "أدوات المذاكرة",
                                        tint = Color(0xFFE6B34B),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "أَدَوَاتُ المُذَاكَرَة",
                                        fontFamily = HeaderBrandAmiriFontFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6B34B)
                                    )
                                    Text(
                                        text = "٨ أدوات بتساعدك وانت بتذاكر",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF0F62FE)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "افتح الكل",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "فتح أدوات المذاكرة",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        val studyToolQuickItems = remember {
                            listOf(
                                Triple("مؤقت المذاكرة", "study_timer", Icons.Default.Timer),
                                Triple("حاسبة المواريث", "study_inheritance", Icons.Default.Balance),
                                Triple("الإعراب الذكي", "study_irab", Icons.Default.AutoAwesome),
                                Triple("القاموس الشامل", "study_dictionary", Icons.Default.AutoStories),
                                Triple("الماسح الذكي", "study_scanner", Icons.Default.DocumentScanner),
                                Triple("ملفات وPDF", "study_files", Icons.Default.PictureAsPdf),
                                Triple("المترجم", "study_translator", Icons.Default.Translate),
                                Triple("المعدل والوحدات", "study_gpa", Icons.Default.Calculate)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            studyToolQuickItems.forEach { (label, route, icon) ->
                                Surface(
                                    onClick = { onNavigate(route) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("home_quick_$route")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = Color(0xFFE6B34B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------
                // 5C. EDUCATIONAL GAMES HUB ENTRY CARD («مركز الألعاب التعليمية»)
                // ----------------------------------------------------------
                Surface(
                    onClick = { onNavigate("educational_games_hub") },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1A6FE3),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_educational_games_hub_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1557B8),
                                        Color(0xFF1A6FE3),
                                        Color(0xFF2563EB)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFFFD54F).copy(alpha = 0.22f))
                                        .border(
                                            1.dp,
                                            Color(0xFFFFD54F).copy(alpha = 0.55f),
                                            RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = "مركز الألعاب التعليمية",
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "مركز الألعاب التعليمية",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ركض المتفوقين • 12 مرحلة ومتجر الشخصيات",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFFFD54F)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "العب الآن",
                                        color = Color(0xFF1E293B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "فتح مركز الألعاب التعليمية",
                                        tint = Color(0xFF1E293B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------
                // 6. DAILY TIP + EXAM COUNTDOWN BANNERS
                // ----------------------------------------------------------
                val tipBg = if (isDarkTheme) Color(0xFF3A162B) else Color(0xFFFDF2F8)
                val tipPink = Color(0xFFEC4899)
                val tipLabelPink = if (isDarkTheme) Color(0xFFF9A8D4) else Color(0xFFDB2777)

                val dailyTipBanner: @Composable (Modifier) -> Unit = { bannerModifier ->
                    Surface(
                        onClick = { tipIndex = (tipIndex + 1) % dailyTips.size },
                        shape = RoundedCornerShape(24.dp),
                        color = tipBg,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, tipPink.copy(alpha = 0.25f)),
                        modifier = bannerModifier.testTag("home_banner_daily_tip")
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawCircle(
                                    color = tipPink.copy(alpha = 0.08f),
                                    radius = size.height * 0.85f,
                                    center = Offset(size.width * 0.12f, size.height * 0.2f)
                                )
                                drawCircle(
                                    color = tipPink.copy(alpha = 0.06f),
                                    radius = size.height * 0.55f,
                                    center = Offset(size.width * 0.88f, size.height * 0.88f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(tipPink),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "نصيحة النهاردة",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "نصيحة النهاردة",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tipLabelPink
                                    )
                                    Text(
                                        text = "من مُتعلِّم ليك",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDarkTheme) Color.White else Color(0xFF831843)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = dailyTips[tipIndex],
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDarkTheme) Color(0xFFFBCFE8) else Color(0xFF9D174D),
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                val examBanner: @Composable (Modifier) -> Unit = { bannerModifier ->
                    Surface(
                        onClick = { showExamDialog = true },
                        shape = RoundedCornerShape(22.dp),
                        color = cardSurface,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, zajilHeroBlue.copy(alpha = 0.25f)),
                        modifier = bannerModifier.testTag("home_banner_exam_countdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(zajilHeroBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = "العد التنازلي للامتحانات",
                                    tint = Color.White,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "أضف عداً تنازلياً لامتحاناتك",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textMain
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (nearestExam != null && nearestExamDaysLeft != null) {
                                        "متبقي $nearestExamDaysLeft يوم على ${nearestExam.examTitle} • اضغط للإدارة"
                                    } else {
                                        "حدد موعد مهمة وإحنا نحسب الأيام الباقية"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textSub
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = zajilHeroBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (nearestExam != null) Icons.Default.EditCalendar else Icons.Default.Add,
                                        contentDescription = "إضافة امتحان",
                                        tint = zajilHeroBlue,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        dailyTipBanner(Modifier.weight(1f))
                        examBanner(Modifier.weight(1f))
                    }
                } else {
                    dailyTipBanner(Modifier.fillMaxWidth())
                    examBanner(Modifier.fillMaxWidth())
                }

                // ----------------------------------------------------------
                // 7. SECTION: «قسم الأدوات» (All 12 Vibrant Cards Preserved)
                // ----------------------------------------------------------
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(22.dp)
                                .clip(CircleShape)
                                .background(zajilHeroBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "قسم الأدوات",
                            fontFamily = com.example.ui.theme.BrandTitleFontFamily,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textMain
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المكتبة الدراسية وكل أدوات مُتعلِّم على بُعد ضغطة واحدة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSub
                    )
                }

                primaryToolCards.chunked(toolColumns).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCards.forEach { card ->
                            VibrantSideNotchCard(
                                spec = card,
                                onClick = { onNavigate(card.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(toolColumns - rowCards.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ----------------------------------------------------------
                // 8. PRESERVED SECONDARY TOOLS & ISLAMIC FEATURES
                // ----------------------------------------------------------
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(22.dp)
                                .clip(CircleShape)
                                .background(VibrantEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "أدوات إضافية وعبادات",
                            fontFamily = com.example.ui.theme.BrandTitleFontFamily,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textMain
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المصحف، القاموس، الملاحظات، الألعاب التعليمية وكل المزايا المحفوظة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSub
                    )
                }

                extraFeatureCards.chunked(toolColumns).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCards.forEach { card ->
                            VibrantSideNotchCard(
                                spec = card,
                                onClick = { onNavigate(card.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(toolColumns - rowCards.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ----------------------------------------------------------
                // 9. PRESERVED TELEGRAM DAILY REPORT CARD
                // ----------------------------------------------------------
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardSurface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0088CC)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "تقرير الإنجاز عبر Telegram",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = textMain
                                    )
                                    Text(
                                        text = if (studentTelegram.isNotBlank()) studentTelegram else "إرسال التنبيهات والتقارير اليومية آلياً",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VibrantRoyalBlue.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "$points نقطة",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VibrantRoyalBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeStatChip(
                                label = "المهام المنجزة",
                                value = "$completedTasksCount من $totalTasksCount",
                                accent = VibrantEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            HomeStatChip(
                                label = "شرب الماء",
                                value = "$waterCups / $waterGoal أكواب",
                                accent = VibrantRoyalBlue,
                                modifier = Modifier.weight(1f)
                            )
                            HomeStatChip(
                                label = "الامتحانات",
                                value = "${upcomingExams.size} قادمة",
                                accent = VibrantPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    DailyTelegramReportHelper.sendCompletedTasksSummaryViaTelegram(
                                        context = context,
                                        userSettings = userSettings,
                                        tasks = tasks
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إرسال التقرير", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { DailyTelegramReportHelper.openTelegramBot(context) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فتح البوت", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ----------------------------------------------------------
                // 10. PRESERVED WATER TRACKER CARD
                // ----------------------------------------------------------
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardSurface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, VibrantRoyalBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = VibrantRoyalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "متابعة شرب الماء",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = textMain
                                )
                            }
                            Text(
                                text = "${waterCups * 250} مل / ${waterGoal * 250} مل",
                                fontWeight = FontWeight.SemiBold,
                                color = VibrantRoyalBlue,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(8) { index ->
                                    Icon(
                                        imageVector = Icons.Default.LocalDrink,
                                        contentDescription = null,
                                        tint = if (index < waterCups) VibrantRoyalBlue else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.decrementWaterCup() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "إنقاص كوب", tint = textSub)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.incrementWaterCup()
                                    Toast.makeText(context, "تم تسجيل كوب ماء بنجاح", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "زيادة كوب", tint = VibrantRoyalBlue)
                            }
                        }
                    }
                }

                // ----------------------------------------------------------
                // 11. PRESERVED FRIDAY SUNNAH CARD
                // ----------------------------------------------------------
                if (isThursdayOrFriday || true) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardSurface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, VibrantEmerald.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mosque,
                                    contentDescription = null,
                                    tint = VibrantEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "سنن يوم الجمعة المبارك",
                                    fontWeight = FontWeight.SemiBold,
                                    color = VibrantEmerald,
                                    fontSize = 13.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setFridaySurahStatus(!userSettings.fridaySurahKahfRead, userSettings.fridaySurahJumuahRead)
                                    }
                            ) {
                                Checkbox(
                                    checked = userSettings.fridaySurahKahfRead,
                                    onCheckedChange = { viewModel.setFridaySurahStatus(it, userSettings.fridaySurahJumuahRead) },
                                    colors = CheckboxDefaults.colors(checkedColor = VibrantEmerald)
                                )
                                Text("1. قراءة سورة الكهف (نور ما بين الجمعتين)", fontSize = 12.sp, color = textMain)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setFridaySurahStatus(userSettings.fridaySurahKahfRead, !userSettings.fridaySurahJumuahRead)
                                    }
                            ) {
                                Checkbox(
                                    checked = userSettings.fridaySurahJumuahRead,
                                    onCheckedChange = { viewModel.setFridaySurahStatus(userSettings.fridaySurahKahfRead, it) },
                                    colors = CheckboxDefaults.colors(checkedColor = VibrantEmerald)
                                )
                                Text("2. قراءة سورة الجمعة والمنافقون والصلاة على النبي ﷺ", fontSize = 12.sp, color = textMain)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }

        if (showExamDialog) {
            ExamCountdownManagerDialog(
                exams = upcomingExams,
                onAddExam = { title, millis, loc ->
                    viewModel.addExamCountdown(title, millis, loc)
                    Toast.makeText(context, "تم إضافة العد التنازلي بنجاح", Toast.LENGTH_SHORT).show()
                },
                onDeleteExam = { viewModel.deleteExamCountdown(it) },
                onOpenSchedule = {
                    showExamDialog = false
                    onNavigate("schedule")
                },
                onDismiss = { showExamDialog = false }
            )
        }

        // Secret Developer Panel triggered by 5 consecutive taps on "مُتعلِّم" (PIN: 2026)
        SecretDeveloperControlDialogs(
            showPinDialog = showDevPinDialog,
            onDismissPinDialog = { showDevPinDialog = false },
            showPanelDialog = showDevPanelDialog,
            onPinVerifiedOpenPanel = {
                showDevPinDialog = false
                showDevPanelDialog = true
            },
            onDismissPanelDialog = { showDevPanelDialog = false },
            userSettings = userSettings,
            viewModel = viewModel
        )

        if (showQuickFahimCommandDialog) {
            var quickQuery by remember { mutableStateOf("") }

            fun dispatchHomeCommand(raw: String) {
                val q = raw.trim()
                showQuickFahimCommandDialog = false
                if (q.isBlank()) {
                    onNavigate("ai")
                    return
                }
                when {
                    q.contains("سبورة") || q.contains("ارسم") || q.contains("مساحة") || q.contains("فيثاغور") || q.contains("مثلث") -> {
                        viewModel.requestOpenWhiteboardWithPrompt(q)
                        onNavigate("fahim_whiteboard")
                    }
                    q.contains("مؤقت") || q.contains("بومودورو") -> onNavigate("pomodoro")
                    q.contains("قاموس") -> onNavigate("dictionary")
                    q.contains("مواريث") -> onNavigate("inheritance")
                    q.contains("تسبيح") || q.contains("أذكار") -> onNavigate("azkar")
                    q.contains("ملفات") || q.contains("pdf", ignoreCase = true) || q.contains("سكنر") -> onNavigate("documents")
                    q.contains("إعراب") || q.contains("اعراب") -> onNavigate("arabic_irab")
                    q.contains("مهامي") && !q.contains("جدول") -> onNavigate("tasks")
                    else -> {
                        viewModel.sendFahimMessage(q) { count ->
                            Toast.makeText(context, "تمت إضافة $count حصص لقسم مهامي ✓", Toast.LENGTH_SHORT).show()
                        }
                        onNavigate("ai")
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showQuickFahimCommandDialog = false },
                containerColor = cardSurface,
                title = {
                    Text(
                        text = "اسأل فهيم أو افتح أي أداة فوراً",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = textMain
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quickQuery,
                            onValueChange = { quickQuery = it },
                            placeholder = { Text("مثال: اشرح لي على السبورة / عندي امتحان بكرة") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_quick_fahim_input"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { dispatchHomeCommand("اشرح لي على السبورة مساحة الجزء المظلل") },
                                label = { Text("سبورة فهيم 🎨", fontSize = 12.sp) }
                            )
                            SuggestionChip(
                                onClick = { dispatchHomeCommand("عندي امتحان بكرة، ظبط لي الجدول واعمل خريطة ذهنية") },
                                label = { Text("جدول امتحان الغد 📅", fontSize = 12.sp) }
                            )
                            SuggestionChip(
                                onClick = { dispatchHomeCommand("مؤقت المذاكرة") },
                                label = { Text("مؤقت ⏱️", fontSize = 12.sp) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { dispatchHomeCommand(quickQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = zajilHeroBlue),
                        modifier = Modifier.testTag("home_quick_fahim_go")
                    ) {
                        Text("انطلق", fontFamily = ReadexProFontFamily, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showQuickFahimCommandDialog = false
                        onNavigate("ai")
                    }) {
                        Text("فتح فهيم", fontFamily = ReadexProFontFamily)
                    }
                }
            )
        }
    }
}

/**
 * Zajil-inspired 3D Stacked Hero Blue Card:
 * Two receding lighter blue layers peeking smoothly behind the top edge of the #1A6FE3 primary card.
 */
@Composable
private fun ZajilStackedHeroCard(
    heroBlueColor: Color,
    studentGreetingName: String,
    progressPercent: Int,
    nextPrayerName: String,
    prayerCountdownText: String,
    onPrayerClick: () -> Unit,
    onBookClick: () -> Unit
) {
    val backLayerColor = heroBlueColor.copy(alpha = 0.25f)
    val midLayerColor = heroBlueColor.copy(alpha = 0.55f)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Layer 1 (Backmost 3D depth slice)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(34.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(backLayerColor)
        )

        // Layer 2 (Middle 3D depth slice)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.92f)
                .height(34.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(midLayerColor)
        )

        // Front Primary Card (#1A6FE3 Royal Blue)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = heroBlueColor,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = heroBlueColor.copy(alpha = 0.35f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Right side: Book badge + Welcome Guest + Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = onBookClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "المكتبة",
                                    tint = Color.White,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            AutoSizeText(
                                text = "مرحباً بك يا $studentGreetingName",
                                fontFamily = com.example.ui.theme.BrandTitleFontFamily,
                                maxFontSize = 17.sp,
                                minFontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                overflowToTwoLinesAtMinSize = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_hero_welcome")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AutoSizeText(
                                text = "كمّل يومك خطوة بخطوة",
                                maxFontSize = 14.sp,
                                minFontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.95f),
                                overflowToTwoLinesAtMinSize = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Left side: Circular Progress Ring
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sweep = (progressPercent.coerceIn(0, 100) / 100f) * 360f
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 6.5.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.25f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            if (sweep > 0f) {
                                drawArc(
                                    color = Color.White,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$progressPercent%",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "تقدمك اليوم",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.94f)
                            )
                        }
                    }
                }

                // Bottom White Capsule for Prayer Countdown inside the Hero Card
                Surface(
                    onClick = onPrayerClick,
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_prayer_strip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NightsStay,
                            contentDescription = "صلاة $nextPrayerName",
                            tint = heroBlueColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "صلاة $nextPrayerName",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "  •  ",
                            fontSize = 14.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "بعد $prayerCountdownText",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = heroBlueColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanActivityStatPill(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    text: String,
    surfaceColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = surfaceColor,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AutoSizeText(
                text = text,
                fontFamily = DisplayArabicFontFamily,
                maxFontSize = 13.sp,
                minFontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                overflowToTwoLinesAtMinSize = false
            )
        }
    }
}

/**
 * Organic Pastel Aura Card (أطياف) with Realistic Studio Product Image & Bold Arabic Typography
 */
@Composable
private fun OrganicAuraRealisticCard(
    title: String,
    subtitle: String,
    @DrawableRes imageRes: Int,
    auraBgColor: Color,
    auraAccentColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactLandscape: Boolean = false
) {
    val cardShape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = auraBgColor,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, auraAccentColor.copy(alpha = 0.16f)),
        modifier = modifier
            .height(if (isCompactLandscape) 158.dp else 186.dp)
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                spotColor = auraAccentColor.copy(alpha = 0.18f)
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Soft organic aura blobs & floating dots ("أطياف") matching Image 3
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = auraAccentColor.copy(alpha = 0.09f),
                    radius = size.width * 0.62f,
                    center = Offset(size.width * 0.5f, size.height * 0.24f)
                )
                drawCircle(
                    color = auraAccentColor.copy(alpha = 0.07f),
                    radius = size.width * 0.52f,
                    center = Offset(size.width * 0.15f, size.height * 0.92f)
                )
                drawCircle(
                    color = auraAccentColor.copy(alpha = 0.35f),
                    radius = 4.dp.toPx(),
                    center = Offset(size.width * 0.16f, size.height * 0.16f)
                )
                drawCircle(
                    color = auraAccentColor.copy(alpha = 0.28f),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * 0.84f, size.height * 0.78f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Realistic 3D/Studio Image inside crisp rounded frame
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier.size(if (isCompactLandscape) 56.dp else 68.dp)
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AutoSizeText(
                        text = title,
                        fontFamily = DisplayArabicFontFamily,
                        maxFontSize = if (isCompactLandscape) 15.sp else 16.sp,
                        minFontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        overflowToTwoLinesAtMinSize = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AutoSizeText(
                        text = subtitle,
                        maxFontSize = if (isCompactLandscape) 11.5.sp else 12.5.sp,
                        minFontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subtitleColor,
                        textAlign = TextAlign.Center,
                        overflowToTwoLinesAtMinSize = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(auraAccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    badgeColor: Color?,
    surfaceColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier.size(width = 42.dp, height = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = surfaceColor,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(40.dp)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (badgeColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun VibrantSideNotchCard(
    spec: HomeToolCardSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = spec.backgroundColor,
        shadowElevation = 6.dp,
        modifier = modifier
            .height(144.dp)
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                spotColor = spec.backgroundColor.copy(alpha = 0.32f)
            )
            .testTag("home_card_${spec.route}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.14f, size.height * 0.18f)
                )
                drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(-size.height * 0.18f, size.height * 0.28f),
                    size = Size(size.height * 0.36f, size.height * 0.44f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.26f,
                    center = Offset(size.width * 0.88f, size.height * 0.88f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = spec.title,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    AutoSizeText(
                        text = spec.title,
                        fontFamily = DisplayArabicFontFamily,
                        maxFontSize = 17.sp,
                        minFontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        overflowToTwoLinesAtMinSize = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    AutoSizeText(
                        text = spec.subtitle,
                        maxFontSize = 13.sp,
                        minFontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        overflowToTwoLinesAtMinSize = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExamCountdownManagerDialog(
    exams: List<com.example.data.database.ExamCountdownEntity>,
    onAddExam: (String, Long, String) -> Unit,
    onDeleteExam: (com.example.data.database.ExamCountdownEntity) -> Unit,
    onOpenSchedule: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var examTitle by remember { mutableStateOf("") }
    var examLocation by remember { mutableStateOf("") }
    val cal = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) } }
    var selectedDateMillis by remember { mutableLongStateOf(cal.timeInMillis) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale("ar")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "العد التنازلي للامتحانات",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = examTitle,
                    onValueChange = { examTitle = it },
                    label = { Text("اسم الامتحان أو المهمة (مثال: امتحان الفيزياء)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = examLocation,
                    onValueChange = { examLocation = it },
                    label = { Text("ملاحظة أو مقر اللجنة (اختياري)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply {
                                    set(y, m, d, 9, 0, 0)
                                }
                                selectedDateMillis = picked.timeInMillis
                            },
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الموعد: ${dateFmt.format(Date(selectedDateMillis))}", fontWeight = FontWeight.SemiBold)
                }

                if (exams.isNotEmpty()) {
                    HorizontalDivider()
                    Text("الامتحانات المسجلة (${exams.size}):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    exams.take(3).forEach { exam ->
                        val daysLeft = max(0L, (exam.examTimestampMillis - System.currentTimeMillis()) / 86_400_000L)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exam.examTitle, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                    Text("متبقي $daysLeft يوم • ${dateFmt.format(Date(exam.examTimestampMillis))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { onDeleteExam(exam) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الامتحان", tint = VibrantCoral, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (examTitle.isNotBlank()) {
                        onAddExam(examTitle, selectedDateMillis, examLocation)
                        examTitle = ""
                        examLocation = ""
                    } else {
                        Toast.makeText(context, "اكتب اسم الامتحان أولاً", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("حفظ الموعد", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenSchedule) {
                    Text("عرض التقويم", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق")
                }
            }
        }
    )
}
