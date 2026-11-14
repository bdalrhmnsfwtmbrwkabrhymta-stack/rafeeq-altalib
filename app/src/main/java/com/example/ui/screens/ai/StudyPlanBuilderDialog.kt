package com.example.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.AiChatStudyHelper
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanBuilderDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRequireNetwork: () -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    val enrolledSubjects by viewModel.subjects.collectAsState()

    var subjectName by remember {
        mutableStateOf(enrolledSubjects.firstOrNull()?.name ?: if (isEnglish) "Mathematics" else "رياضيات")
    }
    var customSubject by remember { mutableStateOf("") }
    var daysAvailable by remember { mutableIntStateOf(7) }
    var hoursPerDay by remember { mutableIntStateOf(4) }
    var selectedStyle by remember { mutableStateOf("POMODORO") } // POMODORO, SPACED_REPETITION, EXAM_CRAM, BALANCED

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isEnglish) "Smart Study Plan Generator" else "مُوَلّد الخطط الدراسية بـ Gemini",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (isEnglish) "Tailored to your exams and schedule" else "خطة وجدول مخصص لظروفك وامتحاناتك",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Subject Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isEnglish) "Target Subject:" else "المادة الدراسية المستهدفة:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    if (enrolledSubjects.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(enrolledSubjects) { sub ->
                                FilterChip(
                                    selected = subjectName == sub.name && customSubject.isBlank(),
                                    onClick = {
                                        subjectName = sub.name
                                        customSubject = ""
                                    },
                                    label = { Text(sub.name, fontSize = 12.sp) },
                                    leadingIcon = if (subjectName == sub.name && customSubject.isBlank()) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = {
                            customSubject = it
                            if (it.isNotBlank()) subjectName = it
                        },
                        placeholder = {
                            Text(
                                if (isEnglish) "Or enter another subject/course..."
                                else "أو اكتب اسماً آخر للمادة أو المنهج..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("study_plan_subject_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Section 2: Timeline / Days to Exam
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Days Available:" else "الأيام المتبقية حتى الإنجاز/الامتحان:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isEnglish) "$daysAvailable days" else "$daysAvailable أيام",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = daysAvailable == days,
                                onClick = { daysAvailable = days },
                                label = { Text(if (isEnglish) "$days d" else "$days أيام", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 3: Daily Available Hours
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Daily Study Hours:" else "ساعات المذاكرة المتاحة يومياً:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isEnglish) "$hoursPerDay hrs/day" else "$hoursPerDay ساعات/يوم",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 4, 6, 8).forEach { hrs ->
                            FilterChip(
                                selected = hoursPerDay == hrs,
                                onClick = { hoursPerDay = hrs },
                                label = { Text(if (isEnglish) "$hrs hrs" else "$hrs س", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 4: Study Technique / Style
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isEnglish) "Study Technique:" else "أسلوب المذاكرة المفضل:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    val styles = listOf(
                        Triple("POMODORO", if (isEnglish) "🍅 Pomodoro (25/5 min blocks)" else "🍅 بومودورو (جلسات تركيز وراحة)", Icons.Default.Timer),
                        Triple("SPACED_REPETITION", if (isEnglish) "🔄 Spaced Repetition" else "🔄 التكرار المتباعد والاسترجاع", Icons.Default.Repeat),
                        Triple("EXAM_CRAM", if (isEnglish) "🚀 High-Yield Exam Cram" else "🚀 مراجعة مكثفة سريعة للامتحان", Icons.Default.Bolt),
                        Triple("BALANCED", if (isEnglish) "⚖️ Balanced & Comprehensive" else "⚖️ متوازن (فهم + تمارين شاملة)", Icons.Default.Balance)
                    )

                    styles.forEach { (id, label, icon) ->
                        val isSelected = selectedStyle == id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStyle = id }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!AiChatStudyHelper.isNetworkAvailable(context)) {
                        onDismiss()
                        onRequireNetwork()
                        return@Button
                    }
                    val finalSubject = if (customSubject.isNotBlank()) customSubject else subjectName
                    viewModel.generateStructuredStudyPlan(
                        subject = finalSubject,
                        daysAvailable = daysAvailable,
                        hoursPerDay = hoursPerDay,
                        focusStyle = selectedStyle
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("generate_study_plan_submit")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEnglish) "Generate Plan" else "إنشاء الخطة الدراسية ✨")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
