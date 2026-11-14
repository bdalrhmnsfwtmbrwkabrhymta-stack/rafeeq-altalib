package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
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
fun AiScheduleSuggestionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var studyGoalInput by remember { mutableStateOf("") }
    var availableHours by remember { mutableIntStateOf(4) }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEnglish) "AI Schedule Optimizer" else "المقترح الذكي لجدول المذاكرة 📅",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isEnglish)
                        "Enter your target exam or study goals to get an optimized daily schedule from Gemini:"
                    else
                        "أدخل أهدافك الدراسية أو مواد الاختبارات ليقوم الذكاء الاصطناعي بتنظيم وتوزيع جلسات المذاكرة والراحة:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = studyGoalInput,
                    onValueChange = { studyGoalInput = it },
                    label = { Text(if (isEnglish) "Target Goal / Exam Focus" else "الهدف أو المواد المراد مراجعتها") },
                    placeholder = { Text(if (isEnglish) "e.g. Finish Physics Chapter 3 & Math calculus" else "مثال: مراجعة الباب الثالث فيزياء وحل مسائل التفاضل") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_schedule_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Available hours today: $availableHours h" else "الساعات المتاحة اليوم: $availableHours ساعات",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row {
                        IconButton(onClick = { if (availableHours > 1) availableHours-- }) {
                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { if (availableHours < 16) availableHours++ }) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (studyGoalInput.isNotBlank()) {
                        isGenerating = true
                        val prompt = if (isEnglish) {
                            "Create an optimized study schedule for $availableHours hours focusing on: $studyGoalInput. Include pomodoro intervals and prayer breaks."
                        } else {
                            "اقترح لي جدول مذاكرة مخصص لمدة $availableHours ساعات مع أوقات راحة بومودورو وأوقات الصلوات، مع التركيز على: $studyGoalInput"
                        }
                        viewModel.sendMessageToAi(prompt)
                        Toast.makeText(context, if (isEnglish) "Generating study schedule..." else "جارٍ إعداد الجدول الدراسي بالذكاء الاصطناعي...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_ai_schedule_button"),
                enabled = studyGoalInput.isNotBlank() && !isGenerating
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEnglish) "Generate Schedule" else "توليد الجدول")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
