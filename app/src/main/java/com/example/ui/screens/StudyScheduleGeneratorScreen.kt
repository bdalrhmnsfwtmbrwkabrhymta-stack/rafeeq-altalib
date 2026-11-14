package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.*
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScheduleGeneratorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    val subjects by viewModel.studentSubjectsInput.collectAsState()
    val preferences by viewModel.studySchedulePreferences.collectAsState()
    val generatedPlan by viewModel.generatedStudyPlan.collectAsState()
    val isLoading by viewModel.isGeneratingStudyPlan.collectAsState()
    val errorMessage by viewModel.studyPlanErrorMessage.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<StudentSubjectInput?>(null) }
    var enableHighThinking by remember { mutableStateOf(false) }
    var selectedDayViewIndex by remember { mutableIntStateOf(0) }

    // Auto-switch to result tab when plan is generated
    LaunchedEffect(generatedPlan) {
        if (generatedPlan != null) {
            selectedTabIndex = 2
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "AI Study Schedule Generator" else "جدول المذاكرة الذكي بـ Gemini AI",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) "Personalized by subjects & deadlines" else "خطة مخصصة بحسب موادك ومواعيدك النهائية",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = if (isEnglish) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isEnglish) "Back" else "رجوع"
                        )
                    }
                },
                actions = {
                    if (generatedPlan != null) {
                        IconButton(
                            onClick = {
                                val plan = generatedPlan ?: return@IconButton
                                val msg = StringBuilder().apply {
                                    append("📅 *خطة المذاكرة الذكية المقترحة بواسطة الذكاء الاصطناعي* 🎓\n")
                                    val name = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم"
                                    append("👤 *الطالب:* $name\n")
                                    append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
                                    if (plan.strategySummary.isNotBlank()) {
                                        append(plan.strategySummary)
                                        append("\n\n")
                                    }
                                    plan.days.take(7).forEach { day ->
                                        append("📌 *اليوم ${day.dayNumber} (${day.dayTitle}):*\n")
                                        day.sessions.forEach { s ->
                                            append("   • ${s.timeSlot}: *${s.subjectName}* - ${s.topicDescription} (${s.durationMinutes} دقيقة)\n")
                                        }
                                        append("\n")
                                    }
                                    append("💡 *نصيحة للتفوق:* الالتزام والاستمرارية سر النجاح والدرجات العلا دائماً! 🤲\n\n")
                                    append("📲 صادر من تطبيق *رفيق الطالب الذكي* 🎓")
                                }.toString()

                                com.example.domain.telegram.DailyTelegramReportHelper.shareStudyPlanViaIntent(
                                    context = context,
                                    targetPhone = userSettings.telegramNumber.ifBlank { userSettings.whatsappNumber },
                                    studyPlanMessage = msg
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "مشاركة الخطة عبر تليجرام",
                                tint = Color(0xFF0288D1)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.saveGeneratedPlanAsNote(generatedPlan!!) {
                                    Toast.makeText(context, if (isEnglish) "Saved to Notes! 📝" else "تم حفظ الخطة في الملاحظات! 📝", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.NoteAlt, contentDescription = "Save Note", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Tab Header
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Subjects & Deadlines (${subjects.size})" else "المواد والمواعيد (${subjects.size})", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_subjects")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Preferences" else "تفضيلات المذاكرة", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_preferences")
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "AI Plan 🗓️" else "الجدول المولد 🗓️", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_generated_plan")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Content based on Tab
            AnimatedContent(
                targetState = selectedTabIndex,
                label = "tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> SubjectsAndDeadlinesTab(
                        subjects = subjects,
                        isEnglish = isEnglish,
                        onAddClick = {
                            editingSubject = null
                            showAddSubjectDialog = true
                        },
                        onEditClick = { subject ->
                            editingSubject = subject
                            showAddSubjectDialog = true
                        },
                        onDeleteClick = { subject ->
                            viewModel.removeStudentSubject(subject.id)
                        },
                        onGenerateClick = {
                            viewModel.generatePersonalizedStudySchedule(
                                enableHighThinking = enableHighThinking,
                                onComplete = { success ->
                                    if (success) {
                                        selectedTabIndex = 2
                                    }
                                }
                            )
                        },
                        isLoading = isLoading,
                        enableHighThinking = enableHighThinking,
                        onToggleHighThinking = { enableHighThinking = it },
                        errorMessage = errorMessage
                    )

                    1 -> PreferencesTab(
                        preferences = preferences,
                        isEnglish = isEnglish,
                        onPreferencesChange = { viewModel.updateStudySchedulePreferences(it) },
                        onGenerateClick = {
                            viewModel.generatePersonalizedStudySchedule(
                                enableHighThinking = enableHighThinking,
                                onComplete = { success ->
                                    if (success) {
                                        selectedTabIndex = 2
                                    }
                                }
                            )
                        },
                        isLoading = isLoading
                    )

                    2 -> GeneratedPlanTab(
                        plan = generatedPlan,
                        isLoading = isLoading,
                        isEnglish = isEnglish,
                        selectedDayIndex = selectedDayViewIndex,
                        onSelectDay = { selectedDayViewIndex = it },
                        onToggleSession = { dayNum, sessionId ->
                            viewModel.toggleGeneratedSessionCompletion(dayNum, sessionId)
                        },
                        onRegenerate = {
                            viewModel.generatePersonalizedStudySchedule(
                                enableHighThinking = enableHighThinking,
                                onComplete = { }
                            )
                        },
                        onSaveToApp = { plan ->
                            viewModel.exportGeneratedPlanToAppSchedule(plan) { success ->
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Schedule and milestone tasks saved to app! 🚀"
                                        else "تم حفظ الجدول ومهام الامتحانات بنجاح في التطبيق! 🚀",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onSaveAsNote = { plan ->
                            viewModel.saveGeneratedPlanAsNote(plan) {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Full study plan saved to Notes! 📝"
                                    else "تم حفظ تفاصيل الخطة في الملاحظات بنجاح! 📝",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onCopy = { plan ->
                            viewModel.copyTextToClipboard("خطة المذاكرة بـ Gemini AI", plan.rawResponseText)
                            Toast.makeText(context, if (isEnglish) "Schedule copied to clipboard! 📋" else "تم نسخ الجدول للحافظة! 📋", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { plan ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "📅 ${plan.planTitle}\n\n${plan.strategySummary}\n\n" +
                                        plan.days.joinToString("\n\n") { day ->
                                            "📌 ${day.dayTitle} (${day.focusGoal}):\n" +
                                                    day.sessions.joinToString("\n") { s -> "• ${s.timeSlot} | ${s.subjectName} (${s.topicDescription})" }
                                        })
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, if (isEnglish) "Share Study Schedule" else "مشاركة الجدول الدراسي"))
                        },
                        onGoToSubjects = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }

    // Add / Edit Subject Dialog
    if (showAddSubjectDialog) {
        SubjectInputDialog(
            initialSubject = editingSubject,
            isEnglish = isEnglish,
            onDismiss = { showAddSubjectDialog = false },
            onSave = { subject ->
                if (editingSubject != null) {
                    viewModel.updateStudentSubject(subject)
                } else {
                    viewModel.addStudentSubject(subject)
                }
                showAddSubjectDialog = false
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// Tab 1: Subjects & Deadlines
// -----------------------------------------------------------------------------------------
@Composable
fun SubjectsAndDeadlinesTab(
    subjects: List<StudentSubjectInput>,
    isEnglish: Boolean,
    onAddClick: () -> Unit,
    onEditClick: (StudentSubjectInput) -> Unit,
    onDeleteClick: (StudentSubjectInput) -> Unit,
    onGenerateClick: () -> Unit,
    isLoading: Boolean,
    enableHighThinking: Boolean,
    onToggleHighThinking: (Boolean) -> Unit,
    errorMessage: String?
) {
    val quickSubjectSuggestions = listOf(
        "رياضيات", "فيزياء", "كيمياء", "أحياء", "لغة عربية", "English", "تاريخ", "جغرافيا", "حاسب آلي", "فلسفة"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Information Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) "📚 Your Subjects & Deadlines" else "📚 موادك ومواعيد الامتحانات والتسليم",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) "Add your courses, difficulty level, and target deadlines."
                                else "أضف موادك الدراسية وحدد درجة الصعوبة والموعد النهائي لكل مادة.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onAddClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_subject_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEnglish) "Add Subject" else "إضافة مادة", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick Suggestions Row
        item {
            Column {
                Text(
                    text = if (isEnglish) "⚡ Quick Presets:" else "⚡ مواد مقترحة وسريعة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickSubjectSuggestions) { subName ->
                        val alreadyAdded = subjects.any { it.name.contains(subName) }
                        AssistChip(
                            onClick = {
                                onAddClick()
                            },
                            label = { Text(subName, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    if (alreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (alreadyAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }

        // Subject Items List
        if (subjects.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isEnglish) "No subjects added yet" else "لم تقم بإضافة مواد دراسية بعد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isEnglish) "Tap 'Add Subject' to enter your courses and deadlines"
                            else "اضغط على زر 'إضافة مادة' لإدخال المقررات ومواعيد الاختبارات",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(subjects, key = { it.id }) { subject ->
                SubjectCardItem(
                    subject = subject,
                    isEnglish = isEnglish,
                    onEdit = { onEditClick(subject) },
                    onDelete = { onDeleteClick(subject) }
                )
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    }
                }
            }
        }

        // High Thinking & AI Generation Actions
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (enableHighThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Deep Reasoning (Gemini 3.1 Pro)" else "تفكير عميق وجدولة فائقة الذكاء",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isEnglish) "High thinking mode for optimal time balancing"
                                    else "تحليل مكثف لأولويات المذاكرة وتقليل التشتت",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = enableHighThinking,
                            onCheckedChange = onToggleHighThinking
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onGenerateClick,
                        enabled = subjects.isNotEmpty() && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_schedule_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isEnglish) "Gemini is building your schedule..." else "جاري إنشاء جدولك بذكاء Gemini...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Generate Personalized Schedule 🚀" else "توليد الخطة والجدول بـ Gemini AI 🚀",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectCardItem(
    subject: StudentSubjectInput,
    isEnglish: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val difficultyColor = when (subject.difficulty) {
        SubjectDifficulty.HARD -> Color(0xFFE53935)
        SubjectDifficulty.MEDIUM -> Color(0xFFFB8C00)
        SubjectDifficulty.EASY -> Color(0xFF43A047)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(subject.colorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subject.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Difficulty Badge
                    Surface(
                        color = difficultyColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) subject.difficulty.labelEn else subject.difficulty.labelAr,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = difficultyColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (subject.topicsOrChapters.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📖 ${subject.topicsOrChapters}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deadline pill
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Deadline: in ${subject.deadlineDays} days" else "الموعد النهائي: بعد ${subject.deadlineDays} أيام",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Estimated Hours pill
                Text(
                    text = if (isEnglish) "~${subject.estimatedTotalHours} hours" else "~${subject.estimatedTotalHours} ساعات تقديرية",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Tab 2: Preferences & Configuration
// -----------------------------------------------------------------------------------------
@Composable
fun PreferencesTab(
    preferences: StudySchedulePreferences,
    isEnglish: Boolean,
    onPreferencesChange: (StudySchedulePreferences) -> Unit,
    onGenerateClick: () -> Unit,
    isLoading: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Daily Available Hours
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "⏱️ Daily Available Study Hours" else "⏱️ الساعات اليومية المتاحة للمذاكرة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${preferences.dailyAvailableHours.toInt()} ${if (isEnglish) "hrs/day" else "ساعات/يوم"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = preferences.dailyAvailableHours,
                        onValueChange = { onPreferencesChange(preferences.copy(dailyAvailableHours = it)) },
                        valueRange = 2f..12f,
                        steps = 9
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isEnglish) "2 hrs (Light)" else "2 س (خفيف)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (isEnglish) "6 hrs (Balanced)" else "6 س (متوازن)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (isEnglish) "12 hrs (Full-time)" else "12 س (مكثف جداً)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Preferred Study Period
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "☀️ Preferred Study Time of Day" else "☀️ الفترة المفضلة للمذاكرة خلال اليوم",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PreferredStudyPeriod.entries.forEach { period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPreferencesChange(preferences.copy(preferredPeriod = period)) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.preferredPeriod == period,
                                onClick = { onPreferencesChange(preferences.copy(preferredPeriod = period)) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) period.labelEn else period.labelAr,
                                fontSize = 13.sp,
                                fontWeight = if (preferences.preferredPeriod == period) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Study Pace & Style
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "🎯 Study Pace & Style" else "🎯 نمط وسرعة المذاكرة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    StudyPace.entries.forEach { pace ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPreferencesChange(preferences.copy(pace = pace)) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.pace == pace,
                                onClick = { onPreferencesChange(preferences.copy(pace = pace)) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) pace.labelEn else pace.labelAr,
                                fontSize = 13.sp,
                                fontWeight = if (preferences.pace == pace) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Pomodoro Interval & Rest Toggles
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "⏱️ Pomodoro Focus & Break Settings" else "⏱️ إعدادات جلسات بومودورو والاستراحات",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(25 to 5, 45 to 10, 50 to 10).forEach { (focus, breakMin) ->
                            val isSelected = preferences.pomodoroMinutes == focus && preferences.breakMinutes == breakMin
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onPreferencesChange(
                                            preferences.copy(pomodoroMinutes = focus, breakMinutes = breakMin)
                                        )
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$focus min", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("+$breakMin min rest", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Include Prayer & Meal Breaks 🕌" else "تضمين استراحات الصلاة والوجبات 🕌",
                            fontSize = 13.sp
                        )
                        Switch(
                            checked = preferences.includePrayerBreaks,
                            onCheckedChange = { onPreferencesChange(preferences.copy(includePrayerBreaks = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Exam Checkpoint & Mock Test Days ⭐" else "محطات مراجعة واختبارات تجريبية قبل المواعيد ⭐",
                            fontSize = 13.sp
                        )
                        Switch(
                            checked = preferences.includeRevisionCheckpoints,
                            onCheckedChange = { onPreferencesChange(preferences.copy(includeRevisionCheckpoints = it)) }
                        )
                    }
                }
            }
        }

        // Custom Notes
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "📝 Custom Notes for Gemini AI" else "📝 ملاحظات إضافية وتوجيهات للذكاء الاصطناعي",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = preferences.studentNotes,
                        onValueChange = { onPreferencesChange(preferences.copy(studentNotes = it)) },
                        placeholder = {
                            Text(
                                if (isEnglish) "e.g. Focus heavily on Calculus chapters, weak in Organic Chemistry..."
                                else "مثال: ركز على حل مسائل التفاضل، لدي نقطة ضعف في الكيمياء العضوية..."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                }
            }
        }

        // Generate CTA
        item {
            Button(
                onClick = onGenerateClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "Generating Schedule..." else "جاري توليد الجدول...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "Generate AI Schedule 🚀" else "توليد الخطة الذكية 🚀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Tab 3: Generated Plan Result View
// -----------------------------------------------------------------------------------------
@Composable
fun GeneratedPlanTab(
    plan: PersonalizedStudyPlanResult?,
    isLoading: Boolean,
    isEnglish: Boolean,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    onToggleSession: (Int, String) -> Unit,
    onRegenerate: () -> Unit,
    onSaveToApp: (PersonalizedStudyPlanResult) -> Unit,
    onSaveAsNote: (PersonalizedStudyPlanResult) -> Unit,
    onCopy: (PersonalizedStudyPlanResult) -> Unit,
    onShare: (PersonalizedStudyPlanResult) -> Unit,
    onGoToSubjects: () -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(54.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isEnglish) "Gemini AI is crafting your study plan..." else "يقوم Gemini AI الآن بتصميم جدولك الدراسي المخصص...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEnglish) "Balancing cognitive loads, interleaving subjects, and scheduling Pomodoro blocks"
                else "يتم توزيع الفصول حسب الصعوبة، جدولة فترات الراحة، وإعداد محطات المراجعة قبل المواعيد",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    if (plan == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isEnglish) "No Schedule Generated Yet" else "لم يتم إنشاء جدول دراسي حتى الآن",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isEnglish) "Add your subjects and deadlines, then tap generate."
                else "أضف موادك ومواعيد الاختبارات ثم اضغط على زر توليد الخطة بالذكاء الاصطناعي.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoToSubjects,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEnglish) "Go to Subjects" else "الانتقال لقائمة المواد")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Strategy Summary Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plan.planTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${plan.totalDays} ${if (isEnglish) "Days Plan" else "أيام"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = plan.strategySummary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Stat Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (isEnglish) "Total Hours" else "إجمالي الساعات", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${plan.totalEstimatedStudyHours} س", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (isEnglish) "Subjects" else "عدد المواد", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${plan.subjectAllocations.size} مواد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (isEnglish) "Daily Pace" else "المعدل اليومي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val dailyRate = if (plan.totalDays > 0) plan.totalEstimatedStudyHours / plan.totalDays else 4f
                                Text(String.format(java.util.Locale.US, "%.1f س/يوم", dailyRate), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Subject Allocation Distribution
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "📊 Time Allocation per Subject" else "📊 توزيع الساعات ونسب التركيز للمواد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    plan.subjectAllocations.forEach { alloc ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(alloc.subjectName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${alloc.totalHours} س (${alloc.percentageOfTotal}%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (alloc.percentageOfTotal / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        }

        // Day Selector Carousel
        item {
            Column {
                Text(
                    text = if (isEnglish) "🗓️ Day-by-Day Schedule:" else "🗓️ الجدول اليومي المفصل:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(plan.days) { index, day ->
                        val isSelected = index == selectedDayIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectDay(index) },
                            label = { Text("اليوم ${day.dayNumber}", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // Active Selected Day Schedule Card
        val activeDay = plan.days.getOrNull(selectedDayIndex) ?: plan.days.firstOrNull()
        if (activeDay != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeDay.dayTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeDay.totalStudyHours} ساعات",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🎯 ${activeDay.focusGoal}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sessions list inside active day
                        activeDay.sessions.forEach { session ->
                            SessionItemRow(
                                session = session,
                                onToggle = { onToggleSession(activeDay.dayNumber, session.id) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // High-Yield Tips Card
        if (plan.highYieldTips.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "💡 High-Yield Retention & Exam Tips" else "💡 نصائح التفوق وتثبيت الحفظ من Gemini",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        plan.highYieldTips.forEach { tip ->
                            Text(
                                text = "• $tip",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Primary Action: Save to App
                Button(
                    onClick = { onSaveToApp(plan) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_to_app_schedule_button")
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Save to App Schedule & Tasks 🚀" else "إضافة إلى جدول التطبيق والمهام 🚀",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopy(plan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEnglish) "Copy" else "نسخ 📋", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onShare(plan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEnglish) "Share" else "مشاركة 📤", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRegenerate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEnglish) "Redo" else "إعادة 🔄", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItemRow(
    session: GeneratedScheduleSession,
    onToggle: () -> Unit
) {
    val bgColor = when {
        session.isBreak -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        session.isMilestoneReview -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        session.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!session.isBreak) {
                Checkbox(
                    checked = session.isCompleted,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Default.Coffee,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.subjectName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (session.isCompleted) TextDecoration.LineThrough else null,
                        color = if (session.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (session.isMilestoneReview) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "مراجعة هامة ⭐",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = session.topicDescription,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = session.timeSlot,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Add / Edit Subject Dialog
// -----------------------------------------------------------------------------------------
@Composable
fun SubjectInputDialog(
    initialSubject: StudentSubjectInput?,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onSave: (StudentSubjectInput) -> Unit
) {
    var name by remember { mutableStateOf(initialSubject?.name ?: "") }
    var topics by remember { mutableStateOf(initialSubject?.topicsOrChapters ?: "") }
    var difficulty by remember { mutableStateOf(initialSubject?.difficulty ?: SubjectDifficulty.MEDIUM) }
    var deadlineDays by remember { mutableIntStateOf(initialSubject?.deadlineDays ?: 7) }
    var estimatedHours by remember { mutableIntStateOf(initialSubject?.estimatedTotalHours ?: 10) }
    var selectedColorHex by remember { mutableStateOf(initialSubject?.colorHex ?: 0xFF1E88E5) }

    val presetColors = listOf(
        0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00ACC1, 0xFF6D4C41
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSubject != null) (if (isEnglish) "Edit Subject" else "تعديل المادة الدراسية")
                else (if (isEnglish) "Add New Subject" else "إضافة مادة دراسية جديدة"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isEnglish) "Subject Name *" else "اسم المادة *") },
                    placeholder = { Text("مثال: الرياضيات، فيزياء، لغة عربية") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subject_name_field")
                )

                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text(if (isEnglish) "Chapters / Topics to Cover" else "الفصول والموضوعات المطلوبة") },
                    placeholder = { Text("مثال: 5 فصول، حل تمارين، مراجعة النظريات") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Difficulty Chips
                Text(if (isEnglish) "Difficulty Level:" else "درجة الصعوبة والتركيز:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubjectDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(if (isEnglish) diff.labelEn else diff.labelAr, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Deadline Days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isEnglish) "Deadline:" else "الموعد النهائي / موعد الامتحان:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "بعد $deadlineDays أيام",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Slider(
                    value = deadlineDays.toFloat(),
                    onValueChange = { deadlineDays = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28
                )

                // Quick presets for deadline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(3, 7, 14, 21, 30).forEach { days ->
                        AssistChip(
                            onClick = { deadlineDays = days },
                            label = { Text("$days أيام", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Color picker
                Text(if (isEnglish) "Subject Tag Color:" else "لون تمييز المادة:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (selectedColorHex == colorHex) 2.dp else 0.dp,
                                    color = if (selectedColorHex == colorHex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val sub = StudentSubjectInput(
                            id = initialSubject?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            topicsOrChapters = topics.trim(),
                            difficulty = difficulty,
                            deadlineDays = deadlineDays,
                            deadlineDateFormatted = "بعد $deadlineDays أيام",
                            estimatedTotalHours = estimatedHours,
                            colorHex = selectedColorHex
                        )
                        onSave(sub)
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_subject_button")
            ) {
                Text(if (isEnglish) "Save" else "حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
