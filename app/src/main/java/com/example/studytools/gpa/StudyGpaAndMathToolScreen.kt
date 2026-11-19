package com.example.studytools.gpa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.util.Locale
import kotlin.math.PI

private data class GpaCourseRow(
    val id: Long,
    val name: String,
    val credits: String,
    val percentageScore: String
)

private enum class UnitCategory(val labelAr: String, val units: List<String>) {
    LENGTH("الطول والمسافة", listOf("متر (m)", "كيلومتر (km)", "سنتيمتر (cm)", "ملليمتر (mm)", "ميل (mi)", "قدم (ft)")),
    MASS("الكتلة والوزن", listOf("كيلوجرام (kg)", "جرام (g)", "طن (t)", "رطل (lb)")),
    TEMP("درجة الحرارة", listOf("مئوية (°C)", "كلفن (K)", "فهرنهايت (°F)")),
    ENERGY("الطاقة والشغل", listOf("جول (J)", "كيلوجول (kJ)", "سعر حراري (cal)", "كيلوواط.ساعة (kWh)")),
    PRESSURE("الضغط", listOf("باسكال (Pa)", "كيلوباسكال (kPa)", "ضغط جوي (atm)", "مليمتر زئبق (mmHg)")),
    ANGLE("الزوايا", listOf("درجة (°)", "راديان (rad)"))
}

@Composable
fun StudyGpaAndMathToolScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = GPA/Percent, 1 = Units & Constants

    val courses = remember {
        mutableStateListOf(
            GpaCourseRow(1L, "الرياضيات", "3", "92"),
            GpaCourseRow(2L, "الفيزياء", "3", "88"),
            GpaCourseRow(3L, "الكيمياء", "3", "90"),
            GpaCourseRow(4L, "اللغة العربية", "2", "95")
        )
    }
    var nextId by remember { mutableLongStateOf(5L) }

    var unitCategory by remember { mutableStateOf(UnitCategory.LENGTH) }
    var fromUnitIndex by remember { mutableIntStateOf(0) }
    var toUnitIndex by remember { mutableIntStateOf(1) }
    var unitValueInput by remember { mutableStateOf("100") }

    val totalCredits = courses.sumOf { it.credits.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
    val weightedPoints = courses.sumOf { row ->
        val c = row.credits.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val pct = row.percentageScore.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        c * scoreToGpa4(pct)
    }
    val weightedPercentSum = courses.sumOf { row ->
        val c = row.credits.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val pct = row.percentageScore.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        c * pct
    }
    val gpa4 = if (totalCredits > 0) weightedPoints / totalCredits else 0.0
    val overallPct = if (totalCredits > 0) weightedPercentSum / totalCredits else 0.0

    val convertedValue = remember(unitCategory, fromUnitIndex, toUnitIndex, unitValueInput) {
        val raw = unitValueInput.toDoubleOrNull()
        if (raw == null) null else convertUnit(unitCategory, fromUnitIndex, toUnitIndex, raw)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141E30),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("gpa_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "المُعَدَّلُ وَالتَّحْوِيلَاتُ",
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF5B041)
                                )
                                Text(
                                    text = "حساب المعدل التراكمي • تحويل الوحدات والثوابت",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("المعدل والنسبة", "الوحدات والثوابت").forEachIndexed { idx, label ->
                                val selected = activeTab == idx
                                Button(
                                    onClick = { activeTab = idx },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("gpa_tab_$idx")
                                ) {
                                    Text(label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (activeTab == 0) {
                    // Summary Banner
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("المعدل (GPA)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = String.format(Locale.US, "%.2f / 4.00", gpa4),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("النسبة المئوية", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", overallPct),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("التقدير", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = gradeLetterAr(overallPct),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    courses.forEachIndexed { idx, row ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = row.name,
                                        onValueChange = { courses[idx] = row.copy(name = it) },
                                        label = { Text("المادة") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    OutlinedTextField(
                                        value = row.credits,
                                        onValueChange = { courses[idx] = row.copy(credits = it.filter { c -> c.isDigit() || c == '.' }) },
                                        label = { Text("الساعات") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                    OutlinedTextField(
                                        value = row.percentageScore,
                                        onValueChange = { courses[idx] = row.copy(percentageScore = it.filter { c -> c.isDigit() || c == '.' }) },
                                        label = { Text("الدرجة %") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    if (courses.size > 1) {
                                        IconButton(
                                            onClick = { courses.removeAt(idx) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف المادة", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            courses.add(GpaCourseRow(nextId++, "مادة جديدة", "2", "85"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gpa_add_course_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة مادة جديدة", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Scientific Unit Converter + Constants
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnitCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = unitCategory == cat,
                                onClick = {
                                    unitCategory = cat
                                    fromUnitIndex = 0
                                    toUnitIndex = 1.coerceAtMost(cat.units.lastIndex)
                                },
                                label = { Text(cat.labelAr) }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = unitValueInput,
                                onValueChange = { unitValueInput = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                                label = { Text("أدخل القيمة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unit_value_input")
                            )

                            Text("من وحدة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                unitCategory.units.forEachIndexed { idx, name ->
                                    FilterChip(
                                        selected = fromUnitIndex == idx,
                                        onClick = { fromUnitIndex = idx },
                                        label = { Text(name) }
                                    )
                                }
                            }

                            Text("إلى وحدة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                unitCategory.units.forEachIndexed { idx, name ->
                                    FilterChip(
                                        selected = toUnitIndex == idx,
                                        onClick = { toUnitIndex = idx },
                                        label = { Text(name) }
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF059669).copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (convertedValue != null) {
                                        "النتيجة: ${String.format(Locale.US, "%.6g", convertedValue)} ${unitCategory.units[toUnitIndex]}"
                                    } else {
                                        "أدخل رقماً صحيحاً للتحويل"
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Essential Physics & Chemistry Constants Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ثوابت علمية أساسية للمراجعة", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            listOf(
                                "سرعة الضوء في الفراغ (c)" to "2.998 × 10⁸ m/s",
                                "ثابت بلانك (h)" to "6.626 × 10⁻³⁴ J·s",
                                "عدد أفوجادرو (Nₐ)" to "6.022 × 10²³ mol⁻¹",
                                "ثابت الغازات العام (R)" to "8.314 J/(mol·K)",
                                "عجلة الجاذبية الأرضية (g)" to "9.807 m/s²",
                                "شحنة الإلكترون (e)" to "1.602 × 10⁻¹⁹ C"
                            ).forEach { (label, valText) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(valText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun scoreToGpa4(pct: Double): Double = when {
    pct >= 93.0 -> 4.0
    pct >= 90.0 -> 3.7
    pct >= 87.0 -> 3.3
    pct >= 83.0 -> 3.0
    pct >= 80.0 -> 2.7
    pct >= 77.0 -> 2.3
    pct >= 73.0 -> 2.0
    pct >= 70.0 -> 1.7
    pct >= 65.0 -> 1.3
    pct >= 60.0 -> 1.0
    else -> 0.0
}

private fun gradeLetterAr(pct: Double): String = when {
    pct >= 90.0 -> "امتياز"
    pct >= 80.0 -> "جيد جداً"
    pct >= 70.0 -> "جيد"
    pct >= 60.0 -> "مقبول"
    else -> "ضعيف"
}

private fun convertUnit(cat: UnitCategory, from: Int, to: Int, v: Double): Double {
    if (from == to) return v
    return when (cat) {
        UnitCategory.LENGTH -> {
            val toMeters = listOf(1.0, 1000.0, 0.01, 0.001, 1609.344, 0.3048)
            (v * toMeters[from]) / toMeters[to]
        }
        UnitCategory.MASS -> {
            val toKg = listOf(1.0, 0.001, 1000.0, 0.45359237)
            (v * toKg[from]) / toKg[to]
        }
        UnitCategory.TEMP -> {
            val celsius = when (from) {
                0 -> v
                1 -> v - 273.15
                else -> (v - 32.0) * 5.0 / 9.0
            }
            when (to) {
                0 -> celsius
                1 -> celsius + 273.15
                else -> celsius * 9.0 / 5.0 + 32.0
            }
        }
        UnitCategory.ENERGY -> {
            val toJoule = listOf(1.0, 1000.0, 4.184, 3_600_000.0)
            (v * toJoule[from]) / toJoule[to]
        }
        UnitCategory.PRESSURE -> {
            val toPascal = listOf(1.0, 1000.0, 101325.0, 133.322387)
            (v * toPascal[from]) / toPascal[to]
        }
        UnitCategory.ANGLE -> {
            if (from == 0 && to == 1) v * PI / 180.0 else v * 180.0 / PI
        }
    }
}
