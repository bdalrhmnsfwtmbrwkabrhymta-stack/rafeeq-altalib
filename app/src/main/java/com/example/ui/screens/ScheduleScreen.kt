package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ScheduleSessionEntity
import com.example.data.database.SubjectEntity
import com.example.domain.ai.DailySyllabusLesson
import com.example.ui.MainViewModel
import com.example.ui.components.ShareStudyPlanWhatsAppDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
    onNavigateToAiSchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val scheduleSessions by viewModel.scheduleSessions.collectAsState()
    val examCountdowns by viewModel.examCountdowns.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    var selectedViewMode by remember { mutableIntStateOf(0) } // 0 = جدولك النهارده, 1 = جدول الحصص الأسبوعي, 2 = إعداد الخطة
    var currentDayNumber by remember { mutableIntStateOf(1) }
    var selectedWeekDay by remember { mutableIntStateOf(1) } // 1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri
    var showAddExamDialog by remember { mutableStateOf(false) }

    // User's custom daily lessons - persisted in DataStore via ViewModel
    val customDailyLessons by viewModel.customDailyLessons.collectAsState()

    // Dialog state for adding subjects / sessions / lessons
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var showAddLessonDialog by remember { mutableStateOf(false) }
    var showAddActionSheet by remember { mutableStateOf(false) }
    var showShareWhatsAppDialog by remember { mutableStateOf(false) }

    // Add Subject Dialog form states
    var newSubjectName by remember { mutableStateOf("") }
    var newSubjectColorHex by remember { mutableStateOf("#3B82F6") }
    var newSubjectWeeklyHours by remember { mutableIntStateOf(6) }
    var newSubjectNotes by remember { mutableStateOf("") }

    // Add Lesson Dialog form states
    var newLessonTitle by remember { mutableStateOf("") }
    var newLessonSubject by remember { mutableStateOf("") }
    var newLessonDuration by remember { mutableIntStateOf(45) }

    // Add Session Dialog form states
    var newSessionSubjectId by remember { mutableStateOf<Long?>(null) }
    var newSessionStartTime by remember { mutableStateOf("08:00") }
    var newSessionEndTime by remember { mutableStateOf("09:30") }
    var newSessionRoom by remember { mutableStateOf("") }
    var newSessionNote by remember { mutableStateOf("") }

    // Schedule Generator State
    var subjectsPerDay by remember { mutableIntStateOf(2) }
    var finishInDays by remember { mutableIntStateOf(45) }
    var offDays by remember { mutableStateOf(setOf("الجمعة")) }

    val daysOfWeek = remember {
        listOf(
            1 to "السبت",
            2 to "الأحد",
            3 to "الإثنين",
            4 to "الثلاثاء",
            5 to "الأربعاء",
            6 to "الخميس",
            7 to "الجمعة"
        )
    }

    val availableColors = listOf(
        "#3B82F6" to "أزرق",
        "#10B981" to "أخضر",
        "#8B5CF6" to "بنفسجي",
        "#F59E0B" to "كهرماني",
        "#EF4444" to "أحمر",
        "#EC4899" to "وردي",
        "#06B6D4" to "سماوي",
        "#F97316" to "برتقالي"
    )

    val popularSubjectSuggestions = listOf(
        "الرياضيات 📐",
        "الفيزياء ⚡",
        "الكيمياء 🧪",
        "الأحياء 🧬",
        "اللغة العربية 📖",
        "اللغة الإنجليزية 🇬🇧",
        "التاريخ 🏛️",
        "الجغرافيا 🌍",
        "الفلسفة والمنطق 🧠",
        "الحاسب الآلي 💻",
        "التربية الدينية 🕌"
    )

    val completedCount = customDailyLessons.count { it.isDone }
    val totalCount = customDailyLessons.size
    val progressPercentage = if (totalCount > 0) ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 0

    val stageLabel = when (userSettings.educationalStage) {
        "PRIMARY" -> "المرحلة الابتدائية"
        "MIDDLE" -> "المرحلة الإعدادية"
        "TECHNICAL" -> "المعهد التقني"
        "UNIVERSITY" -> "المرحلة الجامعية"
        else -> {
            val branchName = when (userSettings.highSchoolBranch) {
                "MATH" -> "علمي رياضة 📐"
                "LITERARY" -> "أدبي 📖"
                else -> "علمي علوم 🧬"
            }
            "الثانوية العامة | $branchName"
        }
    }

    // Filtered sessions for selected week day
    val daySessions = remember(scheduleSessions, selectedWeekDay) {
        scheduleSessions.filter { it.dayOfWeek == selectedWeekDay }
    }

    val subjectMap = remember(subjects) {
        subjects.associateBy({ it.id }, { it })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (subjects.isEmpty()) {
                        // Directly open add subject dialog if no subjects exist yet
                        showAddSubjectDialog = true
                    } else {
                        // If subjects exist, show options to add subject or class session
                        showAddActionSheet = true
                    }
                },
                icon = {
                    Icon(
                        if (subjects.isEmpty()) Icons.Default.Add else Icons.Default.MenuBook,
                        contentDescription = "إضافة مادة"
                    )
                },
                text = {
                    Text(
                        text = if (subjects.isEmpty()) "إضافة أول مادة ➕" else "إضافة مادة / حصة ➕",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("schedule_add_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Navigation Header with Stage Badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "نظمتهالك | الجدول الدراسي 📅",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stageLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showShareWhatsAppDialog = true },
                                modifier = Modifier.testTag("btn_schedule_share_whatsapp")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "مشاركة الخطة عبر واتساب",
                                    tint = Color(0xFF25D366)
                                )
                            }
                            IconButton(onClick = { showAddSubjectDialog = true }) {
                                Icon(Icons.Default.LibraryAdd, contentDescription = "إضافة مادة", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onNavigateToAiSchedule) {
                                Icon(Icons.Default.TipsAndUpdates, contentDescription = "إنشاء جدول مع فهيم", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedViewMode == 0,
                            onClick = { selectedViewMode = 0 },
                            label = { Text("جدولك النهارده", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedViewMode == 1,
                            onClick = { selectedViewMode = 1 },
                            label = { Text("جدول الحصص الأسبوعي", fontSize = 12.sp) },
                            modifier = Modifier.weight(1.2f)
                        )
                        FilterChip(
                            selected = selectedViewMode == 2,
                            onClick = { selectedViewMode = 2 },
                            label = { Text("إعداد الخطة ✨", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bright Colored-Square Interactive Calendar & Upcoming Exams Card
                item {
                    ScheduleColorfulCalendarCard(
                        examCountdowns = examCountdowns,
                        tasks = tasks,
                        onAddExamClick = { showAddExamDialog = true },
                        onDeleteExam = { viewModel.deleteExamCountdown(it) }
                    )
                }

                // Registered Subjects Chips Section
                if (subjects.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "المواد الدراسية المسجلة (${subjects.size}):",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = { showAddSubjectDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مادة جديدة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(subjects, key = { it.id }) { subj ->
                                        val subjColor = try {
                                            Color(android.graphics.Color.parseColor(subj.colorHex))
                                        } catch (_: Exception) {
                                            MaterialTheme.colorScheme.primary
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = subjColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, subjColor.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(subjColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = subj.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "(${subj.targetWeeklyHours}س)",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteSubject(subj)
                                                        Toast.makeText(context, "تم حذف مادة ${subj.name}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color(0xFF94A3B8), modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (selectedViewMode) {
                    0 -> {
                        // MODE 0: "جدولك النهارده" - Starts completely clean without dummy items

                        // 1. Day Navigation Bar
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (currentDayNumber > 1) currentDayNumber-- },
                                        enabled = currentDayNumber > 1
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم السابق")
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "اليوم $currentDayNumber",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "الخطة الدراسية اليومية",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { currentDayNumber++ }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم التالي")
                                    }
                                }
                            }
                        }

                        // 2. Total Progress Card (if user has added lessons)
                        if (customDailyLessons.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "التقدم في دروس اليوم:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "$completedCount / $totalCount درس • $progressPercentage%",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LinearProgressIndicator(
                                            progress = { if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) else 0f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(CircleShape),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Daily Lessons Header & Actions
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "دروس ومهام اليوم (${customDailyLessons.size}):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Row {
                                    Button(
                                        onClick = { showAddLessonDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إضافة درس ➕", fontSize = 12.sp)
                                    }

                                    if (customDailyLessons.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                viewModel.clearCustomDailyLessons()
                                                Toast.makeText(context, "تم تفريغ قائمة دروس اليوم", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Empty State or Lessons List
                        if (customDailyLessons.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.size(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (subjects.isEmpty()) "الجدول والمواد فارغة تماماً 📅" else "الجدول فارغ لهذا اليوم 📅",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (subjects.isEmpty())
                                                "لم تقم بإضافة أي مادة دراسية حتى الآن. اضغط على الزر أدناه لإضافة أول مادة دراسية يدوياً والبدء في تنظيم جدولك!"
                                            else
                                                "لا توجد دروس مضافة لهذا اليوم. أضف دروسك يدوياً أو استخدم مولد الخطط الذكي بالذكاء الاصطناعي.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(18.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (subjects.isEmpty()) {
                                                Button(
                                                    onClick = { showAddSubjectDialog = true },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("إضافة أول مادة ➕", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { showAddLessonDialog = true },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("إضافة درس ➕", fontSize = 13.sp)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = onNavigateToAiSchedule,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.TipsAndUpdates, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("خطة ذكية 💡", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(customDailyLessons) { index, lesson ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.toggleCustomDailyLesson(index)
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (lesson.isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = lesson.isDone,
                                            onCheckedChange = { _ ->
                                                viewModel.toggleCustomDailyLesson(index)
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = lesson.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (lesson.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = lesson.subject,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "• ${lesson.durationMinutes} دقيقة",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.removeCustomDailyLesson(index)
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "حذف", tint = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // MODE 1: "جدول الحصص الأسبوعي" - Real Room Database Sessions

                        // WorkManager Notifications Control Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (userSettings.isLectureNotificationEnabled)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (userSettings.isLectureNotificationEnabled)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (userSettings.isLectureNotificationEnabled)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        if (userSettings.isLectureNotificationEnabled)
                                                            Icons.Default.NotificationsActive
                                                        else
                                                            Icons.Default.NotificationsOff,
                                                        contentDescription = null,
                                                        tint = if (userSettings.isLectureNotificationEnabled)
                                                            MaterialTheme.colorScheme.onPrimary
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column {
                                                Text(
                                                    text = "تنبيهات المحاضرات (WorkManager) 🔔",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = if (userSettings.isLectureNotificationEnabled)
                                                        "مجدولة بالخلفية بدقة ومستمرة أسبوعياً ⚡"
                                                    else
                                                        "التنبيهات متوقفة حالياً",
                                                    fontSize = 10.5.sp,
                                                    color = if (userSettings.isLectureNotificationEnabled)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = userSettings.isLectureNotificationEnabled,
                                            onCheckedChange = { isChecked ->
                                                viewModel.setLectureNotificationEnabled(isChecked)
                                                Toast.makeText(
                                                    context,
                                                    if (isChecked) "تم تفعيل تنبيهات المحاضرات عبر WorkManager 🔔" else "تم إيقاف تنبيهات المحاضرات 🔕",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    }

                                    if (userSettings.isLectureNotificationEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = "موعد التذكير قبل بدء الحصة:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                0 to "في الموعد",
                                                5 to "قبل 5د",
                                                10 to "قبل 10د ⭐",
                                                15 to "قبل 15د",
                                                30 to "قبل 30د"
                                            ).forEach { (minutes, label) ->
                                                val isSelected = userSettings.lectureReminderMinutesBefore == minutes
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        viewModel.setLectureReminderMinutes(minutes)
                                                        Toast.makeText(
                                                            context,
                                                            "تم ضبط التنبيه: $label",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    label = {
                                                        Text(
                                                            label,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedButton(
                                            onClick = {
                                                val testSubjectName = subjects.firstOrNull()?.name ?: "الفيزياء ⚡"
                                                viewModel.triggerTestLectureReminder(testSubjectName)
                                                Toast.makeText(
                                                    context,
                                                    "تم إرسال إشعار تجريبي فوري لـ ($testSubjectName) 🔔",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 10.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Science,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "تجربة وفحص التنبيه فوراً 🧪",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Day Tabs Row
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(daysOfWeek) { (dayId, dayName) ->
                                    val isSelected = selectedWeekDay == dayId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedWeekDay = dayId },
                                        label = { Text(dayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "حصص يوم ${daysOfWeek.find { it.first == selectedWeekDay }?.second} (${daySessions.size}):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Button(
                                    onClick = {
                                        if (subjects.isEmpty()) {
                                            showAddSubjectDialog = true
                                        } else {
                                            newSessionSubjectId = subjects.first().id
                                            showAddSessionDialog = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (subjects.isEmpty()) "إضافة مادة أولاً ➕" else "إضافة حصة ➕",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (daySessions.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.EventBusy,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (subjects.isEmpty()) "لا توجد مواد دراسية بعد" else "لا توجد حصص مسجلة في هذا اليوم",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (subjects.isEmpty())
                                                "أضف موادك الدراسية أولاً ثم قم بجدولة الحصص الأسبوعية."
                                            else
                                                "اضغط على زر «إضافة حصة ➕» لإدراج حصص ومواعيد هذا اليوم.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = {
                                                if (subjects.isEmpty()) {
                                                    showAddSubjectDialog = true
                                                } else {
                                                    newSessionSubjectId = subjects.first().id
                                                    showAddSessionDialog = true
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (subjects.isEmpty()) "إضافة أول مادة دراسية ➕" else "إضافة حصة لهذا اليوم ➕")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(daySessions, key = { it.id }) { session ->
                                val subject = subjectMap[session.subjectId]
                                val subjectName = subject?.name ?: "مادة دراسية"
                                val colorHex = subject?.colorHex ?: "#3B82F6"
                                val accentColor = try {
                                    Color(android.graphics.Color.parseColor(colorHex))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(5.dp)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(accentColor)
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = subjectName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "${session.startTimeString} - ${session.endTimeString}${if (session.roomOrLink.isNotBlank()) " • ${session.roomOrLink}" else ""}${if (session.note.isNotBlank()) " (${session.note})" else ""}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (userSettings.isLectureNotificationEnabled) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.NotificationsActive,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        val offset = userSettings.lectureReminderMinutesBefore
                                                        Text(
                                                            text = if (offset == 0) "تنبيه في الموعد (WorkManager)" else "تنبيه قبلها بـ $offset د (WorkManager)",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        IconButton(onClick = { viewModel.deleteScheduleSession(session) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الحصة", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // MODE 2: "إعداد وتخصيص خطة المذاكرة"
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "إعداد وتخصيص خطة المذاكرة 🎯",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "حدد عدد المواد ومدة الخطة وسيقوم التطبيق بتنظيم دروسك تلقائياً",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question 1: How many subjects per day?
                                    Text(
                                        text = "عايز تذاكر كام مادة في اليوم؟",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(1, 2, 3, 4).forEach { count ->
                                            val isSel = subjectsPerDay == count
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { subjectsPerDay = count },
                                                label = { Text("$count مواد", fontSize = 12.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question 2: How many days to complete curriculum?
                                    Text(
                                        text = "عايز تخلص الخطة في كام يوم؟",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(30, 45, 60, 90).forEach { days ->
                                            val isSel = finishInDays == days
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { finishInDays = days },
                                                label = { Text("$days يوم", fontSize = 12.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question 3: Off days
                                    Text(
                                        text = "أيام الإجازة الأسبوعية:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("الجمعة", "السبت", "الخميس").forEach { day ->
                                            val isOff = offDays.contains(day)
                                            FilterChip(
                                                selected = isOff,
                                                onClick = {
                                                    offDays = if (isOff) offDays - day else offDays + day
                                                },
                                                label = { Text(day, fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(22.dp))

                                    Button(
                                        onClick = {
                                            selectedViewMode = 0
                                            Toast.makeText(context, "تم حفظ إعدادات خطة المذاكرة بنجاح ✨", Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("حفظ الخطة ✨", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Modal Sheet / Dialog to Choose Action when FAB is clicked (if subjects exist)
    if (showAddActionSheet) {
        AlertDialog(
            onDismissRequest = { showAddActionSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ماذا تريد أن تضيف؟ ➕", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddActionSheet = false
                                showAddSubjectDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إضافة مادة دراسية جديدة 📘", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("إضافة مادة مثل الرياضيات، الفيزياء، إلخ...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddActionSheet = false
                                if (subjects.isNotEmpty()) {
                                    newSessionSubjectId = subjects.first().id
                                }
                                showAddSessionDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إضافة حصة دراسية في الجدول ⏰", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("جدولة حصة لمادة معينة في يوم محدد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddActionSheet = false
                                showAddLessonDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إضافة درس أو مهمة لليوم 📝", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("درس محدد تريد إنجازه اليوم", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddActionSheet = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Dialog: Add Subject (إضافة مادة دراسية يدوياً)
    if (showAddSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (subjects.isEmpty()) "إضافة أول مادة دراسية 📘" else "إضافة مادة دراسية جديدة 📘",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("اسم المادة الدراسية") },
                        placeholder = { Text("مثال: الرياضيات، الأحياء، الفيزياء...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick suggestion chips
                    Text(
                        text = "اقتراحات سريعة:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(popularSubjectSuggestions) { suggestion ->
                            val cleanName = suggestion.split(" ").first()
                            SuggestionChip(
                                onClick = { newSubjectName = cleanName },
                                label = { Text(suggestion, fontSize = 10.sp) }
                            )
                        }
                    }

                    // Color Picker
                    Text(
                        text = "لون المادة في الجدول:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { (colorHex, _) ->
                            val isSelected = newSubjectColorHex == colorHex
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .clickable { newSubjectColorHex = colorHex }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Target weekly study hours
                    Text(
                        text = "الساعات الأسبوعية المستهدفة للمذاكرة: $newSubjectWeeklyHours ساعات",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 4, 6, 8, 10, 12).forEach { hours ->
                            val isSelected = newSubjectWeeklyHours == hours
                            FilterChip(
                                selected = isSelected,
                                onClick = { newSubjectWeeklyHours = hours },
                                label = { Text("${hours}س", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Optional Notes
                    OutlinedTextField(
                        value = newSubjectNotes,
                        onValueChange = { newSubjectNotes = it },
                        label = { Text("ملاحظات / اسم المعلم (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newSubjectName.trim()
                        if (cleanName.isNotBlank()) {
                            viewModel.addSubject(
                                name = cleanName,
                                colorHex = newSubjectColorHex,
                                targetHours = newSubjectWeeklyHours,
                                notes = newSubjectNotes.trim()
                            )
                            newSubjectName = ""
                            newSubjectNotes = ""
                            showAddSubjectDialog = false
                            Toast.makeText(context, "تمت إضافة مادة $cleanName بنجاح ✨", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى كتابة اسم المادة أولاً", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ المادة ✨", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog: Add Custom Lesson
    if (showAddLessonDialog) {
        AlertDialog(
            onDismissRequest = { showAddLessonDialog = false },
            title = { Text("إضافة درس جديد 📚", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newLessonTitle,
                        onValueChange = { newLessonTitle = it },
                        label = { Text("عنوان الدرس / الموضوع") },
                        placeholder = { Text("مثال: مراجعة الباب الأول في الأحياء") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (subjects.isNotEmpty()) {
                        Text("اختر المادة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subjects) { subj ->
                                val isSelected = newLessonSubject == subj.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newLessonSubject = subj.name },
                                    label = { Text(subj.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newLessonSubject,
                            onValueChange = { newLessonSubject = it },
                            label = { Text("اسم المادة (مثال: الرياضيات)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = newLessonDuration.toString(),
                        onValueChange = { newLessonDuration = it.toIntOrNull() ?: 45 },
                        label = { Text("المدة بالدقائق") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLessonTitle.isNotBlank()) {
                            val newLesson = DailySyllabusLesson(
                                id = System.currentTimeMillis(),
                                title = newLessonTitle.trim(),
                                subject = if (newLessonSubject.isNotBlank()) newLessonSubject.trim() else "عام",
                                category = "مذاكرة",
                                durationMinutes = newLessonDuration
                            )
                            viewModel.addCustomDailyLesson(newLesson)
                            newLessonTitle = ""
                            newLessonSubject = ""
                            showAddLessonDialog = false
                            Toast.makeText(context, "تمت إضافة الدرس بنجاح ✨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إضافة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLessonDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog: Add Timetable Class Session
    if (showAddSessionDialog) {
        AlertDialog(
            onDismissRequest = { showAddSessionDialog = false },
            title = { Text("إضافة حصة دراسية 📅", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (subjects.isEmpty()) {
                        Text("يرجى إضافة مواد أولاً من قسم المواد", color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = {
                                showAddSessionDialog = false
                                showAddSubjectDialog = true
                            }
                        ) {
                            Text("إضافة مادة الآن ➕")
                        }
                    } else {
                        Text("اختر المادة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subjects) { subj ->
                                val isSelected = newSessionSubjectId == subj.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newSessionSubjectId = subj.id },
                                    label = { Text(subj.name) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSessionStartTime,
                            onValueChange = { newSessionStartTime = it },
                            label = { Text("وقت البدء") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = newSessionEndTime,
                            onValueChange = { newSessionEndTime = it },
                            label = { Text("وقت الانتهاء") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = newSessionRoom,
                        onValueChange = { newSessionRoom = it },
                        label = { Text("القاعة / المكان (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newSessionNote,
                        onValueChange = { newSessionNote = it },
                        label = { Text("ملاحظات إضافية (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (userSettings.isLectureNotificationEnabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (userSettings.isLectureNotificationEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (userSettings.isLectureNotificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (userSettings.isLectureNotificationEnabled) {
                                    val off = userSettings.lectureReminderMinutesBefore
                                    val offText = if (off == 0) "في الموعد تماماً" else "قبل $off دقيقة"
                                    "🔔 سيتم تنبيهك تلقائياً $offText عبر WorkManager"
                                } else {
                                    "🔕 التنبيهات معطلة حالياً من إعدادات الجدول"
                                },
                                fontSize = 11.sp,
                                color = if (userSettings.isLectureNotificationEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sId = newSessionSubjectId ?: subjects.firstOrNull()?.id
                        if (sId != null) {
                            viewModel.addScheduleSession(
                                subjectId = sId,
                                dayOfWeek = selectedWeekDay,
                                startTime = newSessionStartTime,
                                endTime = newSessionEndTime,
                                roomOrLink = newSessionRoom,
                                note = newSessionNote
                            )
                            showAddSessionDialog = false
                            Toast.makeText(context, "تمت إضافة الحصة بنجاح 📅", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = subjects.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ الحصة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSessionDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showShareWhatsAppDialog) {
        ShareStudyPlanWhatsAppDialog(
            userSettings = userSettings,
            sessions = scheduleSessions,
            subjects = subjects,
            tasks = tasks,
            initialDayOfWeek = if (selectedViewMode == 1) selectedWeekDay else null,
            onDismiss = { showShareWhatsAppDialog = false }
        )
    }

    if (showAddExamDialog) {
        AddExamCountdownScheduleDialog(
            onAddExam = { title, millis, location ->
                viewModel.addExamCountdown(title, millis, location)
                Toast.makeText(context, "تمت إضافة موعد الامتحان في التقويم ✅", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddExamDialog = false }
        )
    }
}
