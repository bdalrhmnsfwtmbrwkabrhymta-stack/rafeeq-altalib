package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.HabitEntity
import com.example.ui.MainViewModel
import kotlin.math.*

/**
 * Habit Definition Model for Preset Categories
 */
data class HabitPresetItem(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val iconName: String,
    val iconVector: ImageVector,
    val defaultTime: String = "05:00 م",
    val defaultDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri
    val iconBgColor: Color = Color(0xFFF3E8FF),
    val iconTintColor: Color = Color(0xFF7C3AED)
)

data class HabitCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val items: List<HabitPresetItem>
)

/**
 * Days Representation: [ن, ث, ر, خ, ج, س, ح]
 * ن: الاثنين (Mon)
 * ث: الثلاثاء (Tue)
 * ر: الأربعاء (Wed)
 * خ: الخميس (Thu)
 * ج: الجمعة (Fri)
 * س: السبت (Sat)
 * ح: الأحد (Sun)
 */
data class WeekDayOption(
    val dayNumber: Int, // 1 to 7
    val letter: String,
    val nameArabic: String
)

val WEEK_DAY_OPTIONS = listOf(
    WeekDayOption(1, "س", "السبت"),
    WeekDayOption(2, "ح", "الأحد"),
    WeekDayOption(3, "ن", "الاثنين"),
    WeekDayOption(4, "ث", "الثلاثاء"),
    WeekDayOption(5, "ر", "الأربعاء"),
    WeekDayOption(6, "خ", "الخميس"),
    WeekDayOption(7, "ج", "الجمعة")
)

/**
 * Predefined Categories and Habits matching exact specifications
 */
val PRESET_HABIT_CATEGORIES = listOf(
    HabitCategory(
        id = "study",
        title = "مذاكرة",
        emoji = "📚",
        items = listOf(
            HabitPresetItem(
                id = "study_hour",
                title = "ذاكر ساعة",
                description = "ساعة كل يوم أنفع من ست ساعات يوم واحد",
                categoryId = "study",
                iconName = "Hourglass",
                iconVector = Icons.Default.HourglassTop,
                defaultTime = "05:00 م",
                iconBgColor = Color(0xFFEDE9FE),
                iconTintColor = Color(0xFF7C3AED)
            ),
            HabitPresetItem(
                id = "review_yesterday",
                title = "راجع اللي ذاكرته إمبارح",
                description = "المراجعة السريعة بتثبت اللي فهمته",
                categoryId = "study",
                iconName = "Repeat",
                iconVector = Icons.Default.Repeat,
                defaultTime = "04:30 م",
                iconBgColor = Color(0xFFE0E7FF),
                iconTintColor = Color(0xFF4F46E5)
            ),
            HabitPresetItem(
                id = "solve_exercises",
                title = "حل تمارين",
                description = "الفهم بيتقاس بالحل مش بالقراية",
                categoryId = "study",
                iconName = "Edit",
                iconVector = Icons.Default.Edit,
                defaultTime = "06:00 م",
                iconBgColor = Color(0xFFEDE9FE),
                iconTintColor = Color(0xFF7C3AED)
            ),
            HabitPresetItem(
                id = "prep_bag",
                title = "جهز شنطة بكرة",
                description = "صباحك يبقى أهدى بكتير",
                categoryId = "study",
                iconName = "Backpack",
                iconVector = Icons.Default.School,
                defaultTime = "09:30 م",
                iconBgColor = Color(0xFFFEF3C7),
                iconTintColor = Color(0xFFD97706)
            ),
            HabitPresetItem(
                id = "no_phone_hour",
                title = "سيب الموبايل ساعة",
                description = "ساعة من غير تشتيت بتساوي ثلاثة معاه",
                categoryId = "study",
                iconName = "PhoneDisabled",
                iconVector = Icons.Default.PhoneDisabled,
                defaultTime = "07:00 م",
                iconBgColor = Color(0xFFFEE2E2),
                iconTintColor = Color(0xFFDC2626)
            )
        )
    ),
    HabitCategory(
        id = "health",
        title = "صحة",
        emoji = "🌱",
        items = listOf(
            HabitPresetItem(
                id = "drink_water",
                title = "اشرب مياه",
                description = "التركيز بيقل أول ما تبتدي تعطش",
                categoryId = "health",
                iconName = "WaterDrop",
                iconVector = Icons.Default.WaterDrop,
                defaultTime = "10:00 ص",
                iconBgColor = Color(0xFFE0F2FE),
                iconTintColor = Color(0xFF0284C7)
            ),
            HabitPresetItem(
                id = "sleep_early",
                title = "نام بدري",
                description = "النوم هو اللى بيثبت اللى ذاكرته",
                categoryId = "health",
                iconName = "Bedtime",
                iconVector = Icons.Default.Bedtime,
                defaultTime = "11:00 م",
                iconBgColor = Color(0xFFF3E8FF),
                iconTintColor = Color(0xFF7C3AED)
            ),
            HabitPresetItem(
                id = "wake_early",
                title = "اصحي بدري",
                description = "أول ساعتين في اليوم أصفى وقت للمذاكرة",
                categoryId = "health",
                iconName = "Alarm",
                iconVector = Icons.Default.Alarm,
                defaultTime = "05:30 ص",
                iconBgColor = Color(0xFFFEF3C7),
                iconTintColor = Color(0xFFD97706)
            ),
            HabitPresetItem(
                id = "move_around",
                title = "التحرك شوية",
                description = "القعدة الطويلة بتجيب الدماغ",
                categoryId = "health",
                iconName = "DirectionsWalk",
                iconVector = Icons.Default.DirectionsWalk,
                defaultTime = "04:00 م",
                iconBgColor = Color(0xFFDCFCE7),
                iconTintColor = Color(0xFF16A34A)
            ),
            HabitPresetItem(
                id = "eat_breakfast",
                title = "كل فطار",
                description = "المذاكرة على معدة فاضية بتجيب بحرية",
                categoryId = "health",
                iconName = "Restaurant",
                iconVector = Icons.Default.Restaurant,
                defaultTime = "08:00 ص",
                iconBgColor = Color(0xFFFFEDD5),
                iconTintColor = Color(0xFFEA580C)
            )
        )
    ),
    HabitCategory(
        id = "religious",
        title = "ديني",
        emoji = "📿",
        items = listOf(
            HabitPresetItem(
                id = "quran_wird",
                title = "ورد القرآن",
                description = "صفحة في اليوم أحسن من جزء كل شهر",
                categoryId = "religious",
                iconName = "AutoStories",
                iconVector = Icons.Default.AutoStories,
                defaultTime = "06:30 ص",
                iconBgColor = Color(0xFFDCFCE7),
                iconTintColor = Color(0xFF059669)
            ),
            HabitPresetItem(
                id = "morning_azkar",
                title = "أذكار الصباح",
                description = "دقايق بتفضل معاك اليوم كله",
                categoryId = "religious",
                iconName = "WbSunny",
                iconVector = Icons.Default.WbSunny,
                defaultTime = "06:00 ص",
                iconBgColor = Color(0xFFFEF9C3),
                iconTintColor = Color(0xFFCA8A04)
            ),
            HabitPresetItem(
                id = "evening_azkar",
                title = "أذكار المساء",
                description = "راحة وطمأنينة لقلبك قبل النوم",
                categoryId = "religious",
                iconName = "Nightlight",
                iconVector = Icons.Default.Nightlight,
                defaultTime = "05:30 م",
                iconBgColor = Color(0xFFE0E7FF),
                iconTintColor = Color(0xFF4338CA)
            )
        )
    ),
    HabitCategory(
        id = "life",
        title = "حياة",
        emoji = "🏠",
        items = listOf(
            HabitPresetItem(
                id = "check_loved_one",
                title = "اطمن على حد بتحبه",
                description = "رسالة قصيرة بتفرق مع ناس أذار حنا فحصل",
                categoryId = "life",
                iconName = "Favorite",
                iconVector = Icons.Default.Favorite,
                defaultTime = "08:30 م",
                iconBgColor = Color(0xFFFFE4E6),
                iconTintColor = Color(0xFFE11D48)
            ),
            HabitPresetItem(
                id = "tidy_desk",
                title = "رتب مكتبتك",
                description = "المكان أضراب عقل الملفات",
                categoryId = "life",
                iconName = "CleaningServices",
                iconVector = Icons.Default.CleaningServices,
                defaultTime = "09:00 م",
                iconBgColor = Color(0xFFEDE9FE),
                iconTintColor = Color(0xFF7C3AED)
            ),
            HabitPresetItem(
                id = "track_expenses",
                title = "سجل مصروف النهارده",
                description = "دقيقة بتخيك عارف فلوسك راحت فين",
                categoryId = "life",
                iconName = "AccountBalanceWallet",
                iconVector = Icons.Default.AccountBalanceWallet,
                defaultTime = "10:00 م",
                iconBgColor = Color(0xFFD1FAE5),
                iconTintColor = Color(0xFF059669)
            ),
            HabitPresetItem(
                id = "self_reflection",
                title = "حاسب نفسك",
                description = "دقيقة في آخر اليوم تبص فرح على يومك",
                categoryId = "life",
                iconName = "Psychology",
                iconVector = Icons.Default.Psychology,
                defaultTime = "10:30 م",
                iconBgColor = Color(0xFFE0E7FF),
                iconTintColor = Color(0xFF4F46E5)
            )
        )
    )
)

/**
 * Main Habits Screen: "عاداتي"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val habits by viewModel.habits.collectAsState()

    // State for Habit Configuration Bottom Sheet
    var selectedHabitForConfig by remember { mutableStateOf<HabitPresetItem?>(null) }
    var selectedDays by remember { mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7)) }
    var remindersList by remember { mutableStateOf(listOf("05:00 م")) }
    var selectedHabitSound by remember { mutableStateOf("DEFAULT") }

    // State for Time Picker Dialog
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var editingReminderIndex by remember { mutableStateOf<Int?>(null) } // null = adding new

    // Active View Mode: PRESETS / ACTIVE_HABITS
    var selectedTab by remember { mutableStateOf(0) } // 0 = استكشاف العادات, 1 = عاداتي النشطة

    BackHandler {
        onNavigateBack()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFF3F0FA), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color(0xFF4B5563)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "عاداتي",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1E1B4B)
                                    )
                                    Text(
                                        text = "${habits.size} عادات مضافة لـ «يومي»",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }

                            // View active habits switch badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedTab == 1) Color(0xFF7C3AED) else Color(0xFFF3E8FF),
                                modifier = Modifier.clickable {
                                    selectedTab = if (selectedTab == 0) 1 else 0
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Default.CheckCircle else Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = if (selectedTab == 1) Color.White else Color(0xFF7C3AED),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedTab == 1) "استكشاف" else "عاداتي (${habits.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == 1) Color.White else Color(0xFF7C3AED)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFFAF9FD)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFAF9FD),
                                Color(0xFFF5F2FA),
                                Color(0xFFFFFFFF)
                            )
                        )
                    )
            ) {
                if (selectedTab == 0) {
                    // MAIN HABITS DISCOVERY SCREEN
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
                    ) {
                        // 1. TOP SPROUT BANNER
                        item {
                            TopSproutBanner()
                        }

                        // 2. CATEGORIZED SECTIONS
                        PRESET_HABIT_CATEGORIES.forEach { category ->
                            item {
                                HabitCategorySection(
                                    category = category,
                                    onSelectHabit = { habitItem ->
                                        selectedHabitForConfig = habitItem
                                        selectedDays = habitItem.defaultDays
                                        remindersList = listOf(habitItem.defaultTime)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // ACTIVE TRACKED HABITS SCREEN
                    ActiveHabitsView(
                        habits = habits,
                        onToggleToday = { viewModel.toggleHabitToday(it) },
                        onDeleteHabit = { viewModel.deleteHabit(it) },
                        onSwitchToPresets = { selectedTab = 0 }
                    )
                }
            }

            // 3. INTERACTIVE HABIT CONFIGURATION BOTTOM SHEET
            selectedHabitForConfig?.let { habitItem ->
                ModalBottomSheet(
                    onDismissRequest = { selectedHabitForConfig = null },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(Color(0xFFE5E7EB), CircleShape)
                        )
                    }
                ) {
                    HabitConfigurationSheetContent(
                        habit = habitItem,
                        selectedDays = selectedDays,
                        onDaysChanged = { selectedDays = it },
                        reminders = remindersList,
                        onAddReminderClick = {
                            editingReminderIndex = null
                            showTimePickerDialog = true
                        },
                        onEditReminderClick = { index ->
                            editingReminderIndex = index
                            showTimePickerDialog = true
                        },
                        onRemoveReminder = { index ->
                            remindersList = remindersList.toMutableList().apply { removeAt(index) }
                        },
                        selectedSound = selectedHabitSound,
                        onSoundChanged = { selectedHabitSound = it },
                        onConfirmAdd = {
                            viewModel.addConfiguredHabit(
                                title = habitItem.title,
                                description = habitItem.description,
                                selectedDays = selectedDays,
                                reminders = remindersList,
                                iconName = habitItem.iconName,
                                soundType = selectedHabitSound
                            )
                            Toast.makeText(context, "تمت إضافة «${habitItem.title}» وضبط المنبه والإشعار بنجاح! 🚀🔔", Toast.LENGTH_SHORT).show()
                            selectedHabitForConfig = null
                        }
                    )
                }
            }

            // 4. INTERACTIVE ANALOG / DIGITAL TIME PICKER DIALOG
            if (showTimePickerDialog) {
                InteractiveTimePickerDialog(
                    initialTime = if (editingReminderIndex != null && editingReminderIndex!! < remindersList.size) {
                        remindersList[editingReminderIndex!!]
                    } else "05:00 م",
                    onDismiss = { showTimePickerDialog = false },
                    onConfirm = { formattedTime ->
                        if (editingReminderIndex != null && editingReminderIndex!! < remindersList.size) {
                            remindersList = remindersList.toMutableList().apply { set(editingReminderIndex!!, formattedTime) }
                        } else {
                            if (!remindersList.contains(formattedTime)) {
                                remindersList = remindersList + formattedTime
                            }
                        }
                        showTimePickerDialog = false
                    }
                )
            }
        }
    }
}

/**
 * Top Sprout Banner Container with exact green sprout and text
 */
@Composable
fun TopSproutBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF2FBF4),
        border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFDCFCE7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🌱", fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "اختار عادة، ظبط أيامها ومعادها، وهي هتبقى مهمة متكررة في «يومي» — ومعاها تذكير.",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF065F46),
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Categorized Habit Section UI
 */
@Composable
fun HabitCategorySection(
    category: HabitCategory,
    onSelectHabit: (HabitPresetItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = category.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1B4B)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = category.emoji,
                fontSize = 18.sp
            )
        }

        // Section Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            category.items.forEach { item ->
                HabitPresetCard(
                    habit = item,
                    onAddClick = { onSelectHabit(item) }
                )
            }
        }
    }
}

/**
 * Individual Habit Preset Card UI
 */
@Composable
fun HabitPresetCard(
    habit: HabitPresetItem,
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFF1EEF7)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon in soft background
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(habit.iconBgColor, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = habit.iconVector,
                        contentDescription = habit.title,
                        tint = habit.iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = habit.description,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Add button (+)
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF5F3FF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة عادة",
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 2. Interactive Habit Configuration Bottom Sheet
 */
@Composable
fun HabitConfigurationSheetContent(
    habit: HabitPresetItem,
    selectedDays: List<Int>,
    onDaysChanged: (List<Int>) -> Unit,
    reminders: List<String>,
    onAddReminderClick: () -> Unit,
    onEditReminderClick: (Int) -> Unit,
    onRemoveReminder: (Int) -> Unit,
    selectedSound: String = "DEFAULT",
    onSoundChanged: (String) -> Unit = {},
    onConfirmAdd: () -> Unit
) {
    val context = LocalContext.current
    var previewRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewRingtone?.stop()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header with Selected Habit Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(habit.iconBgColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = habit.iconVector,
                    contentDescription = habit.title,
                    tint = habit.iconTintColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = habit.description,
                    fontSize = 12.5.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 17.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFFF3F0FA), thickness = 1.dp)

        // SECTION: Days Selector (أيام التكرار)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أيام التكرار",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )

                Text(
                    text = if (selectedDays.size == 7) "كل الأيام" else "${selectedDays.size} أيام أسبوعياً",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7C3AED)
                )
            }

            // Circular Days Row [ن, ث, ر, خ, ج, س, ح]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WEEK_DAY_OPTIONS.forEach { dayOption ->
                    val isSelected = selectedDays.contains(dayOption.dayNumber)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(if (isSelected) 3.dp else 0.dp, CircleShape)
                            .background(
                                color = if (isSelected) Color(0xFF7C3AED) else Color(0xFFF3F0FA),
                                shape = CircleShape
                            )
                            .border(
                                width = if (isSelected) 0.dp else 1.dp,
                                color = if (isSelected) Color.Transparent else Color(0xFFE5E0F0),
                                shape = CircleShape
                            )
                            .clickable {
                                val newDays = if (isSelected) {
                                    if (selectedDays.size > 1) selectedDays - dayOption.dayNumber else selectedDays
                                } else {
                                    selectedDays + dayOption.dayNumber
                                }
                                onDaysChanged(newDays)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayOption.letter,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF4B5563)
                        )
                    }
                }
            }
        }

        // SECTION: Reminders (التذكير)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التذكير",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )

                if (reminders.isNotEmpty()) {
                    Text(
                        text = "${reminders.size} تذكيرات نشطة",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // Reminder tags list + "+ معاد" button
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(reminders.size) { idx ->
                    val timeString = reminders[idx]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEDE9FE),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier.clickable { onEditReminderClick(idx) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = timeString,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6D28D9)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { onRemoveReminder(idx) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "حذف التذكير",
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Add time button "+ معاد"
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier.clickable(onClick = onAddReminderClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "معاد +",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                        }
                    }
                }
            }
        }

        // SECTION: Notification Tone & Sound Selection (نغمة الإشعار والرنة)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "نغمة ورنة التنبيه",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )
                }

                Text(
                    text = "صوت عالي حتى والتطبيق مغلق",
                    fontSize = 11.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold
                )
            }

            val soundOptions = remember {
                listOf(
                    Triple("DEFAULT", "النغمة الافتراضية للنظام 📱", "نغمة الإشعارات الرسمية للجهاز"),
                    Triple("BELL", "جرس ومنبه واضح 🔔", "صوت منبه قوي وواضح لضمان الانتباه"),
                    Triple("GENTLE", "رنين هادئ ومريح 🍃", "نغمة ناعمة غير مزعجة للتذكير"),
                    Triple("MOTIVATIONAL", "تنبيه إنجاز ونشاط ⚡", "نغمة محفزة تشجعك على الإنجاز"),
                    Triple("TAKBEER", "تكبير وذكر لطيف 🕌", "تنبيه بذكر الله والتكبير")
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(soundOptions.size) { idx ->
                    val (code, label, _) = soundOptions[idx]
                    val isSelected = selectedSound == code
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF7C3AED) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D28D9) else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable {
                            onSoundChanged(code)
                            try {
                                previewRingtone?.stop()
                                val uri = when (code) {
                                    "BELL" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                    else -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                }
                                val r = android.media.RingtoneManager.getRingtone(context, uri)
                                previewRingtone = r
                                r?.play()
                            } catch (_: Exception) {}
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF334155)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Info hint
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEDE9FE).copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "يتم إرسال الإشعار وتشغيل الرنة بدقة عالية فور حلول الوقت المحدد حتى لو كان التطبيق مغلقاً تماماً أو في وضع السكون.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF5B21B6),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Primary Action Button: "ضيفها"
        Button(
            onClick = onConfirmAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C3AED),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ضيفها",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 3. Interactive Time Picker Dialog (Digital + Analog Clock Dial)
 */
@Composable
fun InteractiveTimePickerDialog(
    initialTime: String = "05:00 م",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Parse initial time
    var isPm by remember { mutableStateOf(!initialTime.contains("ص")) }
    var selectedHour by remember {
        val num = initialTime.replace("ص", "").replace("م", "").trim().split(":").firstOrNull()?.toIntOrNull() ?: 5
        mutableStateOf(if (num in 1..12) num else 5)
    }
    var selectedMinute by remember {
        val parts = initialTime.replace("ص", "").replace("م", "").trim().split(":")
        val min = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        mutableStateOf(min)
    }

    var pickerMode by remember { mutableStateOf("HOUR") } // HOUR or MINUTE
    var isKeyboardInputMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Text(
                        text = "اختيار الوقت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )

                    // Digital Box Display + AM/PM Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hour Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (pickerMode == "HOUR") Color(0xFF7C3AED) else Color(0xFFF3EEFF),
                            modifier = Modifier
                                .width(64.dp)
                                .height(56.dp)
                                .clickable { pickerMode = "HOUR" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format("%02d", selectedHour),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (pickerMode == "HOUR") Color.White else Color(0xFF7C3AED)
                                )
                            }
                        }

                        Text(
                            text = ":",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        // Minute Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (pickerMode == "MINUTE") Color(0xFF7C3AED) else Color(0xFFF3EEFF),
                            modifier = Modifier
                                .width(64.dp)
                                .height(56.dp)
                                .clickable { pickerMode = "MINUTE" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format("%02d", selectedMinute),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (pickerMode == "MINUTE") Color.White else Color(0xFF7C3AED)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // AM / PM Switcher (ص / م)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE5E0F0)),
                            color = Color(0xFFFAF8FF)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 28.dp)
                                        .background(if (!isPm) Color(0xFF7C3AED) else Color.Transparent)
                                        .clickable { isPm = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ص",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isPm) Color.White else Color(0xFF6B7280)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 28.dp)
                                        .background(if (isPm) Color(0xFF7C3AED) else Color.Transparent)
                                        .clickable { isPm = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "م",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPm) Color.White else Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }

                    if (!isKeyboardInputMode) {
                        // Interactive Analog Clock Face
                        AnalogClockDial(
                            mode = pickerMode,
                            selectedValue = if (pickerMode == "HOUR") selectedHour else selectedMinute,
                            onValueSelected = { value ->
                                if (pickerMode == "HOUR") {
                                    selectedHour = value
                                    pickerMode = "MINUTE" // Auto transition to minute
                                } else {
                                    selectedMinute = value
                                }
                            }
                        )
                    } else {
                        // Manual Keyboard Input Mode
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "إدخال يدوي للساعة والدقيقة", fontSize = 13.sp, color = Color(0xFF6B7280))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { selectedHour = if (selectedHour >= 12) 1 else selectedHour + 1 },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EEFF), contentColor = Color(0xFF7C3AED))
                                ) {
                                    Text("+ ساعة")
                                }
                                Button(
                                    onClick = { selectedMinute = (selectedMinute + 15) % 60 },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EEFF), contentColor = Color(0xFF7C3AED))
                                ) {
                                    Text("+ 15 دقيقة")
                                }
                            }
                        }
                    }

                    // Dialog Actions (Confirm, Cancel, Keyboard Toggle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isKeyboardInputMode = !isKeyboardInputMode },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyboardInputMode) Icons.Default.Schedule else Icons.Default.Keyboard,
                                contentDescription = "تبديل الإدخال",
                                tint = Color(0xFF7C3AED)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("الإلغاء", color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val period = if (isPm) "م" else "ص"
                                    val formatted = String.format("%02d:%02d %s", selectedHour, selectedMinute, period)
                                    onConfirm(formatted)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7C3AED),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("حسنًا", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-Precision Analog Clock Face Canvas
 */
@Composable
fun AnalogClockDial(
    mode: String, // "HOUR" or "MINUTE"
    selectedValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val dialRadiusDp = 105.dp
    val primaryColor = Color(0xFF7C3AED)
    val lightDialBg = Color(0xFFF8F5FF)
    val dialBorder = Color(0xFFEDE8F5)

    Box(
        modifier = Modifier
            .size(dialRadiusDp * 2)
            .background(lightDialBg, CircleShape)
            .border(1.dp, dialBorder, CircleShape)
            .pointerInput(mode) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angleDeg < 0) angleDeg += 360f

                    if (mode == "HOUR") {
                        var hour = (round(angleDeg / 30f)).toInt()
                        if (hour == 0) hour = 12
                        if (hour > 12) hour = 12
                        onValueSelected(hour)
                    } else {
                        var minute = (round(angleDeg / 6f)).toInt()
                        if (minute == 60) minute = 0
                        onValueSelected(minute)
                    }
                }
            }
            .pointerInput(mode) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angleDeg < 0) angleDeg += 360f

                    if (mode == "HOUR") {
                        var hour = (round(angleDeg / 30f)).toInt()
                        if (hour == 0) hour = 12
                        if (hour > 12) hour = 12
                        onValueSelected(hour)
                    } else {
                        var minute = (round(angleDeg / 6f)).toInt()
                        if (minute == 60) minute = 0
                        onValueSelected(minute)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 24.dp.toPx()

            // Draw clock center dot
            drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = center)

            // Calculate angle for current selected value
            val currentAngleDeg = if (mode == "HOUR") {
                (selectedValue % 12) * 30f
            } else {
                selectedValue * 6f
            }
            val angleRad = Math.toRadians((currentAngleDeg - 90).toDouble())

            val pointerX = center.x + radius * cos(angleRad).toFloat()
            val pointerY = center.y + radius * sin(angleRad).toFloat()
            val pointerOffset = Offset(pointerX, pointerY)

            // Draw Pointer Line
            drawLine(
                color = primaryColor,
                start = center,
                end = pointerOffset,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw Pointer Selection Circle
            drawCircle(
                color = primaryColor,
                radius = 16.dp.toPx(),
                center = pointerOffset
            )

            // Draw Numbers on Dial
            val count = if (mode == "HOUR") 12 else 12
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1E1B4B")
                textSize = 14.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val selectedTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 14.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            for (i in 1..count) {
                val numValue = if (mode == "HOUR") i else (i * 5) % 60
                val numText = if (mode == "HOUR") "$i" else String.format("%02d", numValue)
                val itemAngleDeg = if (mode == "HOUR") i * 30f else i * 30f
                val itemAngleRad = Math.toRadians((itemAngleDeg - 90).toDouble())

                val numX = center.x + radius * cos(itemAngleRad).toFloat()
                val numY = center.y + radius * sin(itemAngleRad).toFloat() + (textPaint.textSize / 3)

                val isThisSelected = if (mode == "HOUR") selectedValue == i else selectedValue == numValue
                val paintToUse = if (isThisSelected) selectedTextPaint else textPaint

                drawContext.canvas.nativeCanvas.drawText(
                    numText,
                    numX,
                    numY,
                    paintToUse
                )
            }
        }
    }
}

/**
 * Active Tracked Habits Screen (Viewing user's configured habits with streak & completion)
 */
@Composable
fun ActiveHabitsView(
    habits: List<HabitEntity>,
    onToggleToday: (HabitEntity) -> Unit,
    onDeleteHabit: (HabitEntity) -> Unit,
    onSwitchToPresets: () -> Unit
) {
    if (habits.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFF3E8FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🌱", fontSize = 36.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا توجد عادات نشطة بعد",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1B4B)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "اختر من باقة العادات اليومية المقترحة وابدأ في تثبيت روتينك الدراسي والصحي.",
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onSwitchToPresets,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("استكشف واضف عادة جديدة", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFAF5FF),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "معدل التزام العادات",
                                fontSize = 13.sp,
                                color = Color(0xFF6B21A8)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${min(100, habits.size * 25)}%",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF7C3AED)
                            )
                            Text(
                                text = "عاداتك متصلة تلقائياً بمهام «يومي» 🔥",
                                fontSize = 11.5.sp,
                                color = Color(0xFF9333EA)
                            )
                        }

                        Button(
                            onClick = onSwitchToPresets,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة عادة", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "عاداتك اليومية المستمرة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )
            }

            items(habits, key = { it.id }) { habit ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFF1EEF7)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = habit.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E1B4B)
                            )
                            if (habit.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = habit.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "سلسلة الإنجاز: ${habit.streakCount} أيام متتالية",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { onDeleteHabit(habit) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFFEE2E2), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف العادة",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onToggleToday(habit) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF7C3AED), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "إنجاز العادة اليوم",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
