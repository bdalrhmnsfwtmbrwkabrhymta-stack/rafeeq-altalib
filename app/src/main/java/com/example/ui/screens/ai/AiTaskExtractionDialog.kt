package com.example.ui.screens.ai

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun AiTaskExtractionDialog(
    initialTaskTitle: String,
    initialSubject: String = "",
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var taskTitle by remember { mutableStateOf(initialTaskTitle) }
    var taskSubject by remember { mutableStateOf(initialSubject) }
    var priority by remember { mutableStateOf("High") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AddTask,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEnglish) "Add AI Task to Schedule" else "إضافة مهمة دراسية إلى جدولك",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isEnglish)
                        "Save this recommendation directly into your study tasks list:"
                    else
                        "احفظ هذه التوصية أو جلسة المذاكرة مباشرة في قائمة مهامك:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text(if (isEnglish) "Task Title" else "عنوان المهمة") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_task_title_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = taskSubject,
                    onValueChange = { taskSubject = it },
                    label = { Text(if (isEnglish) "Subject (Optional)" else "المادة (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Priority:" else "الأولوية:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf("High", "Medium", "Low").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = {
                                Text(
                                    when (p) {
                                        "High" -> if (isEnglish) "High" else "عالية"
                                        "Medium" -> if (isEnglish) "Medium" else "متوسطة"
                                        else -> if (isEnglish) "Low" else "عادية"
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        viewModel.addStudyTaskFromAi(
                            title = taskTitle.trim(),
                            subjectName = taskSubject.trim(),
                            priority = priority
                        )
                        Toast.makeText(
                            context,
                            if (isEnglish) "Task added to study schedule ✅" else "تمت إضافة المهمة إلى جدول مهامك الدراسية بنجاح ✅",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    }
                },
                enabled = taskTitle.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_ai_task_button")
            ) {
                Text(if (isEnglish) "Add Task" else "إضافة المهمة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
