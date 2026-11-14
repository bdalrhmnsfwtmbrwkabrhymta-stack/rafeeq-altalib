package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.TaskEntity
import com.example.ui.MainViewModel
import com.example.ui.components.FirebaseAuthFirestoreCard
import com.example.ui.components.WeeklyTasksAchievementChart
import com.example.ui.screens.ai.AiScheduleSuggestionDialog

@Composable
fun TasksScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, TODAY, HIGH_PRIORITY, COMPLETED
    var showAiScheduleDialog by remember { mutableStateOf(false) }
    var showWeeklyChart by remember { mutableStateOf(true) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    val firebaseUser by viewModel.firebaseUser.collectAsState()

    if (showCloudSyncDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSyncDialog = false },
            confirmButton = {
                TextButton(onClick = { showCloudSyncDialog = false }) {
                    Text("إغلاق")
                }
            },
            text = {
                FirebaseAuthFirestoreCard(viewModel = viewModel)
            }
        )
    }

    if (showAiScheduleDialog) {
        AiScheduleSuggestionDialog(
            viewModel = viewModel,
            onDismiss = { showAiScheduleDialog = false }
        )
    }

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            "TODAY" -> tasks.filter { !it.isCompleted }
            "HIGH_PRIORITY" -> tasks.filter { it.priority == "HIGH" || it.priority == "URGENT" }
            "COMPLETED" -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مهمة")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // 1. Header & Search/Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إدارة المهام الدراسية",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Weekly Chart Toggle Button
                        IconButton(
                            onClick = { showWeeklyChart = !showWeeklyChart }
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "عرض الرسم البياني الأسبوعي",
                                tint = if (showWeeklyChart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        // Gemini AI Smart Schedule Button
                        IconButton(
                            onClick = { showAiScheduleDialog = true }
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "اقتراح جدول ذكي بـ Gemini AI",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Cloud Sync Button
                        IconButton(
                            onClick = {
                                if (firebaseUser != null) {
                                    viewModel.syncAllDataToFirestore { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    showCloudSyncDialog = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (firebaseUser != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = "مزامنة السحابة Firestore",
                                tint = if (firebaseUser != null) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.exportLocalBackupToFile(context) { file, _ ->
                                    if (file != null) {
                                        Toast.makeText(context, "تم تصدير المهام والجدول بنجاح إلى: ${file.name} 📁", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "حدث خطأ أثناء حفظ الملف", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "تصدير المهام لملف محلي",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "${tasks.count { !it.isCompleted }} متبقية",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Weekly Tasks Achievement Chart (Recharts / Compose visual chart)
            if (showWeeklyChart) {
                item {
                    WeeklyTasksAchievementChart(
                        tasks = tasks,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Gemini AI Smart Schedule Suggestion Banner
            item {
                Card(
                    onClick = { showAiScheduleDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = "جدول يومي محدد بكتل التركيز (Gemini AI) 🤖",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "حلل مهامك الحالية واحصل على ترتيب زمني موزع للدرس",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "تحليل ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 4. Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("الكل") }
                    )
                    FilterChip(
                        selected = selectedFilter == "HIGH_PRIORITY",
                        onClick = { selectedFilter = "HIGH_PRIORITY" },
                        label = { Text("عالية الأهمية") }
                    )
                    FilterChip(
                        selected = selectedFilter == "COMPLETED",
                        onClick = { selectedFilter = "COMPLETED" },
                        label = { Text("المكتملة") }
                    )
                }
            }

            // 5. Tasks List or Empty State
            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد مهام في هذه القائمة حالياً",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    val subject = subjects.find { it.id == task.subjectId }
                    TaskCardItem(
                        task = task,
                        subjectName = subject?.name,
                        onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            subjects = subjects,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, subjectId, priority, tag ->
                viewModel.addTask(
                    title = title,
                    description = desc,
                    subjectId = subjectId,
                    priority = priority,
                    dueDateMillis = System.currentTimeMillis() + 86400000L,
                    tag = tag
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TaskCardItem(
    task: TaskEntity,
    subjectName: String?,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (subjectName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المادة: $subjectName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    subjects: List<com.example.data.database.SubjectEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var priority by remember { mutableStateOf("MEDIUM") }
    var tag by remember { mutableStateOf("واجب") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مهمة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المهمة") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("الوصف أو التفاصيل (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("التصنيف (مثال: واجب، مراجعة، امتحان)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, desc, selectedSubjectId, priority, tag)
                    }
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
