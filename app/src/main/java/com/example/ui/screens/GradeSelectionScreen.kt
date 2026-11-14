package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel

data class StageButtonSpec(
    val id: String,
    val title: String,
    val defaultGradeYears: List<String>,
    val defaultAge: Int,
    val backgroundColor: Color,
    val borderColor: Color
)

@Composable
fun GradeSelectionScreen(
    viewModel: MainViewModel,
    onStageSelected: () -> Unit
) {
    val userSettings by viewModel.userSettings.collectAsState()
    var selectedStageId by remember {
        mutableStateOf(userSettings.educationalStage.ifBlank { "HIGH_SCHOOL" })
    }
    var selectedBranch by remember { mutableStateOf(userSettings.highSchoolBranch) }
    var studentNameInput by remember { mutableStateOf(userSettings.userName.ifBlank { "طالب متفوق" }) }
    var studentAgeInput by remember { mutableStateOf(userSettings.studentAge.toString()) }
    var selectedGradeYear by remember { mutableStateOf(userSettings.studentGradeYear) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    val stageSpecs = listOf(
        StageButtonSpec(
            "PRIMARY",
            "المدرسة الابتدائية",
            listOf("الصف الأول الابتدائي", "الصف الثاني الابتدائي", "الصف الثالث الابتدائي", "الصف الرابع الابتدائي", "الصف الخامس الابتدائي", "الصف السادس الابتدائي"),
            10,
            Color(0xFFFFECEE),
            Color(0xFFFCA5A5)
        ),
        StageButtonSpec(
            "MIDDLE",
            "المدرسة المتوسطة",
            listOf("الصف الأول الإعدادي/المتوسط", "الصف الثاني الإعدادي/المتوسط", "الصف الثالث الإعدادي/المتوسط"),
            14,
            Color(0xFFE8F8F0),
            Color(0xFF86EFAC)
        ),
        StageButtonSpec(
            "HIGH_SCHOOL",
            "الثانوية",
            listOf("الصف الأول الثانوي", "الصف الثاني الثانوي", "الصف الثالث الثانوي (توجيهي / بكالوريا)"),
            17,
            Color(0xFFE6F0FA),
            Color(0xFF93C5FD)
        ),
        StageButtonSpec(
            "TECHNICAL",
            "المعهد التقني",
            listOf("السنة الأولى دبلوم تقني", "السنة الثانية دبلوم تقني", "السنة الثالثة معهد متقدم"),
            19,
            Color(0xFFFFF7E6),
            Color(0xFFFDE047)
        ),
        StageButtonSpec(
            "UNIVERSITY",
            "الجامعة",
            listOf("السنة الجامعية الأولى (Freshman)", "السنة الجامعية الثانية (Sophomore)", "السنة الجامعية الثالثة (Junior)", "السنة الجامعية الرابعة / التخرج (Senior)", "دراسات عليا / ماجستير"),
            21,
            Color(0xFFF2ECFC),
            Color(0xFFD8B4FE)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Grid notebook background pattern (Matches Image 1)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSize = 24.dp.toPx()
            val gridColor = Color(0xFFE2E8F0)
            val strokeW = 1.dp.toPx()

            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeW
                )
                x += gridSize
            }

            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeW
                )
                y += gridSize
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Main Title matching Image 1
            Text(
                text = "المفكرة المدرسية",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "بالذكاء الاصطناعي",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "اختر مرحلتك وسنتك الدراسية لبدء التخصيص الذكي 🎓",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 5 Grade Buttons matching Image 1
            stageSpecs.forEach { spec ->
                val isSelected = selectedStageId == spec.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedStageId = spec.id
                            if (spec.defaultGradeYears.isNotEmpty() && !spec.defaultGradeYears.contains(selectedGradeYear)) {
                                selectedGradeYear = spec.defaultGradeYears.last()
                            }
                            studentAgeInput = spec.defaultAge.toString()
                            showDetailsSheet = true
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = spec.backgroundColor,
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 2.5.dp else 1.5.dp,
                        if (isSelected) Color(0xFF2563EB) else spec.borderColor
                    ),
                    shadowElevation = if (isSelected) 3.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = spec.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Cartoon Notebook Mascot (Matches Image 1)
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.notebook_mascot_1788286598042),
                    contentDescription = "رفيقك الدراسي الذكي",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Student Profile & Stage Confirmation Dialog
        if (showDetailsSheet) {
            val activeSpec = stageSpecs.firstOrNull { it.id == selectedStageId } ?: stageSpecs[2]

            AlertDialog(
                onDismissRequest = { showDetailsSheet = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بيانات الطالب - ${activeSpec.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Student Name
                        OutlinedTextField(
                            value = studentNameInput,
                            onValueChange = { studentNameInput = it },
                            label = { Text("اسم الطالب") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Student Age
                        OutlinedTextField(
                            value = studentAgeInput,
                            onValueChange = { studentAgeInput = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("العمر / السن (بالسنوات)") },
                            leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Grade Year Selection
                        Text(
                            text = "الصف / السنة الدراسية:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        activeSpec.defaultGradeYears.forEach { gradeYear ->
                            val isGradeSelected = selectedGradeYear == gradeYear
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGradeYear = gradeYear },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isGradeSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isGradeSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isGradeSelected,
                                        onClick = { selectedGradeYear = gradeYear }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = gradeYear,
                                        fontSize = 12.sp,
                                        fontWeight = if (isGradeSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // High School Branch
                        if (selectedStageId == "HIGH_SCHOOL") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الشعبة الدراسية (ثانوية عامة):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val branches = listOf(
                                "SCIENCE" to "علمي علوم 🧬 (أحياء وجيولوجيا)",
                                "MATH" to "علمي رياضة 📐 (تفاضل وجبر وميكانيكا)",
                                "LITERARY" to "أدبي 📖 (تاريخ وجغرافيا وفلسفة)"
                            )

                            branches.forEach { (branchKey, branchLabel) ->
                                val isSelected = selectedBranch == branchKey
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedBranch = branchKey },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedBranch = branchKey }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = branchLabel,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                            val parsedAge = studentAgeInput.toIntOrNull() ?: activeSpec.defaultAge
                            val gradeYearToSave = if (selectedGradeYear.isNotBlank()) selectedGradeYear else activeSpec.defaultGradeYears.firstOrNull() ?: "الصف الدراسي"
                            viewModel.setEducationalStage(
                                stage = selectedStageId,
                                branch = selectedBranch,
                                age = parsedAge,
                                gradeYear = gradeYearToSave,
                                name = studentNameInput.trim()
                            )
                            showDetailsSheet = false
                            onStageSelected()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تأكيد وبدء المذاكرة الذكية ✨", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDetailsSheet = false }) {
                        Text("رجوع")
                    }
                }
            )
        }
    }
}
