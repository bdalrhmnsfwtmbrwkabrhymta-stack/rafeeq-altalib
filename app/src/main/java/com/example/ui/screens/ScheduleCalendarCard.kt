package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ExamCountdownEntity
import com.example.data.database.TaskEntity
import com.example.ui.theme.VibrantAmber
import com.example.ui.theme.VibrantCoral
import com.example.ui.theme.VibrantEmerald
import com.example.ui.theme.VibrantPurple
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Interactive Colorful-Squares Monthly Calendar & Exam Countdowns Card for ScheduleScreen
 */
@Composable
fun ScheduleColorfulCalendarCard(
    examCountdowns: List<ExamCountdownEntity>,
    tasks: List<TaskEntity>,
    onAddExamClick: () -> Unit,
    onDeleteExam: (ExamCountdownEntity) -> Unit
) {
    var displayedMonthOffset by remember { mutableIntStateOf(0) }
    val baseCal = remember(displayedMonthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, displayedMonthOffset)
        }
    }
    val todayCal = remember { Calendar.getInstance() }
    var selectedDayOfMonth by remember(displayedMonthOffset) {
        mutableIntStateOf(
            if (displayedMonthOffset == 0) todayCal.get(Calendar.DAY_OF_MONTH) else 1
        )
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val monthTitle = monthFormat.format(baseCal.time)
    val daysInMonth = baseCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Saturday = 7 in Calendar, we map Sat->0, Sun->1, Mon->2, Tue->3, Wed->4, Thu->5, Fri->6
    val firstDayOffset = when (baseCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        else -> 6
    }

    val displayedYear = baseCal.get(Calendar.YEAR)
    val displayedMonth = baseCal.get(Calendar.MONTH)

    val examsByDay = remember(examCountdowns, displayedYear, displayedMonth) {
        examCountdowns.groupBy { exam ->
            Calendar.getInstance().apply { timeInMillis = exam.examTimestampMillis }.let { c ->
                if (c.get(Calendar.YEAR) == displayedYear && c.get(Calendar.MONTH) == displayedMonth) {
                    c.get(Calendar.DAY_OF_MONTH)
                } else -1
            }
        }
    }

    val tasksByDay = remember(tasks, displayedYear, displayedMonth) {
        tasks.groupBy { task ->
            val due = task.dueDateMillis ?: return@groupBy -1
            Calendar.getInstance().apply { timeInMillis = due }.let { c ->
                if (c.get(Calendar.YEAR) == displayedYear && c.get(Calendar.MONTH) == displayedMonth) {
                    c.get(Calendar.DAY_OF_MONTH)
                } else -1
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale("ar")) }
    val nowMs = System.currentTimeMillis()
    val upcomingExams = remember(examCountdowns, nowMs) {
        examCountdowns.filter { it.examTimestampMillis >= nowMs - 86_400_000L }
            .sortedBy { it.examTimestampMillis }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Month Header + Add Exam Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { displayedMonthOffset-- },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الشهر السابق")
                    }
                    Text(
                        text = monthTitle,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { displayedMonthOffset++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الشهر التالي")
                    }
                }

                FilledTonalButton(
                    onClick = onAddExamClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("امتحان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Weekday Header Strip (Sat -> Fri)
            val weekLabels = listOf("س", "ح", "ن", "ث", "ر", "خ", "ج")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekLabels.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar Days Grid with Bright Saturated Colored Squares
            val totalCells = firstDayOffset + daysInMonth
            val rowsCount = (totalCells + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (rowIdx in 0 until rowsCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (colIdx in 0 until 7) {
                            val cellIndex = rowIdx * 7 + colIdx
                            val dayNum = cellIndex - firstDayOffset + 1
                            if (dayNum < 1 || dayNum > daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f).height(38.dp))
                            } else {
                                val isToday = displayedMonthOffset == 0 && dayNum == todayCal.get(Calendar.DAY_OF_MONTH)
                                val hasExam = !examsByDay[dayNum].isNullOrEmpty()
                                val hasTask = !tasksByDay[dayNum].isNullOrEmpty()
                                val isSelected = dayNum == selectedDayOfMonth

                                val squareBg = when {
                                    hasExam -> VibrantCoral
                                    isToday -> MaterialTheme.colorScheme.primary
                                    hasTask -> VibrantEmerald
                                    isSelected -> VibrantPurple.copy(alpha = 0.18f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }

                                val squareTextColor = when {
                                    hasExam || isToday || hasTask -> Color.White
                                    isSelected -> VibrantPurple
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(squareBg)
                                        .clickable { selectedDayOfMonth = dayNum },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 12.5.sp,
                                            fontWeight = if (hasExam || isToday || hasTask || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = squareTextColor
                                        )
                                        if (hasExam || hasTask) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalendarLegendChip("اليوم", MaterialTheme.colorScheme.primary)
                CalendarLegendChip("امتحان", VibrantCoral)
                CalendarLegendChip("مهام", VibrantEmerald)
            }

            // Selected Day Details or Upcoming Exams List
            val selectedDayExams = examsByDay[selectedDayOfMonth].orEmpty()
            val selectedDayTasks = tasksByDay[selectedDayOfMonth].orEmpty()

            if (selectedDayExams.isNotEmpty() || selectedDayTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "أحداث يوم $selectedDayOfMonth $monthTitle:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                selectedDayExams.forEach { exam ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VibrantCoral.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = VibrantCoral, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(exam.examTitle, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { onDeleteExam(exam) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = VibrantCoral, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                selectedDayTasks.forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VibrantEmerald.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (task.isCompleted) VibrantEmerald else VibrantAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else if (upcomingExams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "الامتحانات القادمة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                upcomingExams.take(2).forEach { exam ->
                    val daysLeft = max(0L, (exam.examTimestampMillis - nowMs) / 86_400_000L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${exam.examTitle} (${dateFormatter.format(Date(exam.examTimestampMillis))})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VibrantCoral
                        ) {
                            Text(
                                text = "متبقي $daysLeft يوم",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AddExamCountdownScheduleDialog(
    onAddExam: (String, Long, String) -> Unit,
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
            Text("إضافة موعد امتحان جديد 📅", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = examTitle,
                    onValueChange = { examTitle = it },
                    label = { Text("اسم المادة / الامتحان") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = examLocation,
                    onValueChange = { examLocation = it },
                    label = { Text("ملاحظة أو رقم اللجنة (اختياري)") },
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
                                val picked = Calendar.getInstance().apply { set(y, m, d, 9, 0, 0) }
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
                    Text("التاريخ: ${dateFmt.format(Date(selectedDateMillis))}", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (examTitle.isNotBlank()) {
                        onAddExam(examTitle, selectedDateMillis, examLocation)
                        onDismiss()
                    } else {
                        Toast.makeText(context, "يرجى إدخال اسم الامتحان", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("حفظ الموعد", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
