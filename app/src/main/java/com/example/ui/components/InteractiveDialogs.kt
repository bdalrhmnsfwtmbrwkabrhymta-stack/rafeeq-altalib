package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * StudyTimerModalDialog (مؤقت المذاكرة):
 * Modal dialog for student focus sessions, pomodoro countdown, pause/resume.
 */
@Composable
fun StudyTimerModalDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onStartFullTimer: () -> Unit
) {
    if (!isOpen) return

    var totalMinutes by remember { mutableIntStateOf(25) }
    var secondsRemaining by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        } else if (secondsRemaining == 0) {
            isRunning = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مؤقت المذاكرة والتركيز ⏱️",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Circular Progress Display
                val progress = if (totalMinutes > 0) secondsRemaining.toFloat() / (totalMinutes * 60) else 0f
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(EmeraldContainer.copy(alpha = 0.5f))
                        .border(4.dp, EmeraldLight, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formattedTime,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark
                        )
                        Text(
                            text = if (isRunning) "جلسة نشطة 🎯" else "جاهز للبدء",
                            fontSize = 12.sp,
                            color = EmeraldMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Time selector chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSelected = totalMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) EmeraldPrimary else BackgroundLight,
                            modifier = Modifier.clickable {
                                totalMinutes = mins
                                secondsRemaining = mins * 60
                                isRunning = false
                            }
                        ) {
                            Text(
                                text = "$mins دقيقة",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { isRunning = !isRunning },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) PlayRed else EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "إيقاف مؤقت" else "ابدأ الآن")
                    }

                    OutlinedButton(
                        onClick = onStartFullTimer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("الشاشة الكاملة 🖥️", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * QuickTasksBottomSheet (المهام السريعة):
 * Sheet to view, add, and check off daily study tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTasksBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    tasks: List<com.example.data.database.TaskEntity>,
    onToggleTask: (com.example.data.database.TaskEntity) -> Unit,
    onAddTask: (String) -> Unit
) {
    if (!isOpen) return

    var newTaskTitle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المهام السريعة اليومية 📝",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${tasks.count { it.isCompleted }}/${tasks.size} منجز",
                    fontSize = 12.sp,
                    color = EmeraldMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("أضف مهمة مذاكرة جديدة...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(newTaskTitle.trim())
                            newTaskTitle = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            text = "لا توجد مهام حالياً. أضف مهام يومك لتنجز أكثر! 🚀",
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                } else {
                    items(tasks) { task ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (task.isCompleted) PastelMint else BackgroundLight,
                            modifier = Modifier.fillMaxWidth().clickable { onToggleTask(task) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { onToggleTask(task) },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                                    color = if (task.isCompleted) TextMuted else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SmartAssistantBottomSheet (المساعد الذكي):
 * Bottom sheet with quick smart student assistant queries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAssistantBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenAiScreen: () -> Unit
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Premium3DIcon(
                    icon = Icons.Default.AutoAwesome,
                    primaryColor = NavPurple,
                    containerSize = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "المساعد الذكي للدراسة 🤖",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "تلخيص، حل مسائل، شرح قواعد، وتوليد جداول",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            val suggestions = listOf(
                "📝 لخص لي درساً أو نصاً طويلاً",
                "🔍 اشرح لي قاعدة نحوية أو مسألة رياضية",
                "📅 صمم لي جدول مذاكرة لأسبوع الامتحانات",
                "💡 اقترح لي أفكاراً لمشروع أو موضوع تعبير"
            )

            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PastelPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenAiScreen() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NavPurple
                        )
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NavPurple, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenAiScreen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavPurple)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فتح شاشة المساعد الذكي الكاملة ⚡", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * FeatureDetailSheet (تفاصيل الميزة)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureDetailSheet(
    isOpen: Boolean,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onActionClick: () -> Unit
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("فتح الأداة الآن 🚀", fontWeight = FontWeight.Bold)
            }
        }
    }
}
