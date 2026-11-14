package com.example.ui.prayer

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.audio.AzanAudioPlayer
import com.example.data.prayer.CalculatedPrayerTimes
import com.example.data.prayer.PrayerTimeCalculator
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

enum class CalendarTab(val title: String, val icon: String) {
    ISLAMIYAT("أذكار المسلم", "📿"),
    HEALTH("صحة وتغذية", "❤️"),
    CALENDAR("جدول الصيام", "📅"),
    CULTURE("ثقافة ومعارف", "📚"),
    TOOLS("أدوات", "🛠️")
}

data class DhikrItem(
    val id: Int,
    val text: String,
    val targetCount: Int,
    val currentCount: MutableState<Int> = mutableStateOf(0),
    val reward: String
)

@Composable
fun PrayerCalendarCard(
    locationName: String = "القاهرة",
    latitude: Double? = null,
    longitude: Double? = null,
    dstMode: String = "AUTO",
    onGoToDateClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenZakat: () -> Unit = {},
    onOpenQuran: () -> Unit = {}
) {
    PrayerCalendarScreen(
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        dstMode = dstMode,
        onGoToDateClick = onGoToDateClick,
        onSettingsClick = onSettingsClick,
        onOpenQibla = onOpenQibla,
        onOpenZakat = onOpenZakat,
        onOpenQuran = onOpenQuran
    )
}

@Composable
fun PrayerCalendarScreen(
    locationName: String = "القاهرة",
    latitude: Double? = null,
    longitude: Double? = null,
    dstMode: String = "AUTO",
    dayName: String = "",
    hijriYear: String = "",
    gregorianYear: String = "",
    hijriMonth: String = "",
    gregorianMonth: String = "",
    hijriDay: String = "",
    gregorianDay: String = "",
    nextPrayerName: String = "",
    remainingTime: String = "",
    fajr: String = "",
    sunrise: String = "",
    dhuhr: String = "",
    asr: String = "",
    maghrib: String = "",
    isha: String = "",
    onGoToDateClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenZakat: () -> Unit = {},
    onOpenQuran: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableStateOf(CalendarTab.ISLAMIYAT) }

    val cityCoords = remember(locationName, latitude, longitude) {
        if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) {
            Pair(latitude, longitude)
        } else {
            PrayerTimeCalculator.getCoordinatesForCity(locationName)
        }
    }

    // حساب أوقات الصلاة الدقيقة
    val calculatedTimes: CalculatedPrayerTimes = remember(selectedDate, cityCoords, dstMode) {
        PrayerTimeCalculator.calculateForLocalDate(
            localDate = selectedDate,
            latitude = cityCoords.first,
            longitude = cityCoords.second,
            dstMode = dstMode
        )
    }

    // التاريخ الهجري التلقائي الدقيق
    val hijriFormatted = remember(selectedDate) {
        try {
            val hDate = HijrahDate.from(selectedDate)
            val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy هـ", Locale("ar"))
            formatter.format(hDate)
        } catch (e: Exception) {
            "تاريخ هجري مبارك"
        }
    }

    // اختيار تاريخ مخصص
    val showDatePicker = {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val currentDayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))
    val currentGregorianDay = selectedDate.dayOfMonth.toString()
    val currentGregorianMonth = selectedDate.month.getDisplayName(TextStyle.FULL, Locale("ar"))
    val currentGregorianYear = selectedDate.year.toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- كارت النتيجة الحائطية الشامل ومواقيت الصلاة ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .border(2.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // اسم اليوم + أزرار التنقل والتقويم السريعة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBackIos,
                            contentDescription = "اليوم السابق",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showDatePicker() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = locationName,
                                fontSize = 13.sp,
                                color = textColor.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = currentDayName,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryColor
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "اختر تاريخاً",
                                tint = primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                            Icon(
                                imageVector = Icons.Default.ArrowForwardIos,
                                contentDescription = "اليوم التالي",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // التاريخ الهجري والميلادي المعروض
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "التاريخ الهجري",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        Text(
                            text = hijriFormatted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(borderColor.copy(alpha = 0.4f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentGregorianMonth $currentGregorianYear",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        Text(
                            text = currentGregorianDay,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // شريط الصلاة القادمة والمتبقي الحي
                Surface(
                    color = primaryColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "أذان ${calculatedTimes.nextPrayerName}:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        Text(
                            text = remainingTime.ifBlank { calculatedTimes.timeRemainingString }.ifBlank { "مواقيت دقيقة" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // شبكة مواعيد الصلوات الست بدقة
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerCell("الفجر", calculatedTimes.fajr, calculatedTimes.nextPrayerName == "الفجر", primaryColor, textColor, Modifier.weight(1f))
                        PrayerCell("الشروق", calculatedTimes.sunrise, calculatedTimes.nextPrayerName == "الشروق", primaryColor, textColor, Modifier.weight(1f))
                        PrayerCell("الظهر", calculatedTimes.dhuhr, calculatedTimes.nextPrayerName == "الظهر", primaryColor, textColor, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerCell("العصر", calculatedTimes.asr, calculatedTimes.nextPrayerName == "العصر", primaryColor, textColor, Modifier.weight(1f))
                        PrayerCell("المغرب", calculatedTimes.maghrib, calculatedTimes.nextPrayerName == "المغرب", primaryColor, textColor, Modifier.weight(1f))
                        PrayerCell("العشاء", calculatedTimes.isha, calculatedTimes.nextPrayerName == "العشاء", primaryColor, textColor, Modifier.weight(1f))
                    }
                }
            }
        }

        // --- أزرار التبويبات التفاعلية للأقسام ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CalendarTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = tab },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) primaryColor else surfaceColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = tab.icon, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // --- محتوى القسم المختار ---
        TabContentCard(
            selectedTab = selectedTab,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            textColor = textColor,
            onOpenQibla = onOpenQibla,
            onOpenZakat = onOpenZakat,
            onOpenQuran = onOpenQuran
        )
    }
}

@Composable
private fun TabContentCard(
    selectedTab: CalendarTab,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    onOpenQibla: () -> Unit = {},
    onOpenZakat: () -> Unit = {},
    onOpenQuran: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${selectedTab.icon} قسم ${selectedTab.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                CalendarTab.ISLAMIYAT -> AzkarSection(primaryColor, textColor)
                CalendarTab.HEALTH -> RichHealthSection(primaryColor, textColor)
                CalendarTab.CALENDAR -> FastingAndCalendarSection(primaryColor, textColor)
                CalendarTab.CULTURE -> CultureSection(textColor)
                CalendarTab.TOOLS -> ToolsSection(
                    primaryColor = primaryColor,
                    textColor = textColor,
                    onOpenQibla = onOpenQibla,
                    onOpenZakat = onOpenZakat,
                    onOpenQuran = onOpenQuran
                )
            }
        }
    }
}

@Composable
private fun RichHealthSection(
    primaryColor: Color,
    textColor: Color
) {
    var selectedHealthSubTab by remember { mutableStateOf("WATER") }
    
    // Water tracker state
    var waterCups by remember { mutableIntStateOf(4) }
    val dailyWaterTargetCups = 8

    // BMI Calculator state
    var heightInput by remember { mutableStateOf("175") }
    var weightInput by remember { mutableStateOf("70") }
    var calculatedBmi by remember { mutableStateOf<Double?>(null) }
    var bmiCategory by remember { mutableStateOf("") }
    var idealWeightRange by remember { mutableStateOf("") }

    // Live tips repository (works 100% offline and can refresh from live tips pool)
    var currentTipIndex by remember { mutableIntStateOf(0) }
    val healthTips = remember {
        listOf(
            "🍎 قاعدة ثلث لطعامك: تنظيم الوجبات وتجنب التخمة يرفع كفاءة التركيز الذهني بنسبة 35% ويحمي من الخمول بعد الأكل.",
            "💧 شرب كوبين من الماء عند الاستيقاظ يفعّل الدورة الدموية ويعيد ترطيب الأعضاء الحيوية بعد ساعات النوم.",
            "🚶‍♂️ المشي 20 دقيقة يومياً بعد الوجبات يضبط مستويات السكر في الدم ويعزز صحة القلب والأوعية الدموية.",
            "🌙 دورات النوم: النوم في مواعيد ثابتة لمدة 7-8 ساعات يدعم إفراز هرمون النمو وتجديد الذاكرة العصبية.",
            "🍯 عسل النحل والحبة السوداء: غنيان بمضادات الأكسدة الطبيعية التي تقوي الجهاز المناعي ضد الالتهابات.",
            "🥗 الألياف والخضار الورقية: تمد الجسم بالمغنيسيوم والبوتاسيوم الضروريين لراحة العضلات وصفاء الذهن."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Sub-tabs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedHealthSubTab == "WATER",
                onClick = { selectedHealthSubTab = "WATER" },
                label = { Text("متتبع الماء 💧", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedHealthSubTab == "BMI",
                onClick = { selectedHealthSubTab = "BMI" },
                label = { Text("حاسبة الوزن BMI ⚖️", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedHealthSubTab == "SLEEP",
                onClick = { selectedHealthSubTab = "SLEEP" },
                label = { Text("دليل النوم 🌙", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
        }

        when (selectedHealthSubTab) {
            "WATER" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💧 متتبع شرب الماء اليومي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0288D1)
                            )
                            Text(
                                text = "${waterCups * 250} مل / ${dailyWaterTargetCups * 250} مل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { (waterCups.toFloat() / dailyWaterTargetCups).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Color(0xFF0288D1),
                            trackColor = Color(0xFFE1F5FE)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cup icons grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (i in 1..dailyWaterTargetCups) {
                                val isFilled = i <= waterCups
                                IconButton(
                                    onClick = {
                                        waterCups = if (waterCups == i) i - 1 else i
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Text(
                                        text = if (isFilled) "🥛" else "🫙",
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { if (waterCups > 0) waterCups-- },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("- كوب (250 مل)", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { if (waterCups < 16) waterCups++ },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                            ) {
                                Text("+ كوب ماء 🥛", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "BMI" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "⚖️ مؤشر كتلة الجسم والوزن الصحي (BMI):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = primaryColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { heightInput = it },
                                label = { Text("الطول (سم)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { weightInput = it },
                                label = { Text("الوزن (كجم)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Button(
                            onClick = {
                                val h = heightInput.toDoubleOrNull() ?: 0.0
                                val w = weightInput.toDoubleOrNull() ?: 0.0
                                if (h > 50 && w > 20) {
                                    val heightInMeters = h / 100.0
                                    val bmi = w / (heightInMeters * heightInMeters)
                                    calculatedBmi = bmi
                                    val minIdeal = (18.5 * heightInMeters * heightInMeters).toInt()
                                    val maxIdeal = (24.9 * heightInMeters * heightInMeters).toInt()
                                    idealWeightRange = "$minIdeal - $maxIdeal كجم"
                                    bmiCategory = when {
                                        bmi < 18.5 -> "نحافة (أقل من الطبيعي)"
                                        bmi < 25.0 -> "وزن مثالي ورائع ✓"
                                        bmi < 30.0 -> "زيادة طفيفة في الوزن"
                                        else -> "سمنة تتطلب حمية ونشاط"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("احسب المؤشر والوزن المثالي", fontWeight = FontWeight.Bold)
                        }

                        calculatedBmi?.let { bmi ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "المؤشر: ${String.format(Locale.US, "%.1f", bmi)} - الحالة: $bmiCategory",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "نطاق الوزن المثالي لطولك: $idealWeightRange",
                                        fontSize = 12.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "SLEEP" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌙 جدول النوم الصحي ودورات الاستيقاظ:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = primaryColor
                        )
                        Text(
                            text = "• دورة النوم تكتمل كل 90 دقيقة (5 دورات = 7.5 ساعات نوم مثالية).",
                            fontSize = 12.sp,
                            color = textColor
                        )
                        Text(
                            text = "• أفضل وقت للنوم لصلاة الفجر بنشاط: بين 10:00 م و 10:30 م.",
                            fontSize = 12.sp,
                            color = textColor
                        )
                        Text(
                            text = "• القيلولة النبوية (20-30 دقيقة قبل أو بعد صلاة الظهر) تجدد النشاط الذهني دون التأثير على نوم الليل.",
                            fontSize = 12.sp,
                            color = textColor
                        )
                    }
                }
            }
        }

        // Live/Offline Updated Daily Medical Advice Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نصيحة طبية دقيقة ومحدثة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentTipIndex = (currentTipIndex + 1) % healthTips.size
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث النصيحة", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = healthTips[currentTipIndex],
                    fontSize = 12.sp,
                    color = textColor,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun FastingAndCalendarSection(
    primaryColor: Color,
    textColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🌟 جدول صيام السنن والفوائد الطبية:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
                Text("• صيام الاثنين والخميس: ترفع فيهما الأعمال إلى الله وتمنح الجهاز الهضمي راحة دورية.", fontSize = 12.sp, color = textColor)
                Text("• الأيام البيض (13، 14، 15 من كل شهر هجري): تعدل صيام الدهر كُله وتهدئ الدورة البيولوجية للجسم.", fontSize = 12.sp, color = textColor)
                Text("• الفوائد الطبية المثبتة: تحفيز التهام الخلايا التالفة (Autophagy) وخفض مستويات الأنسولين ومقاومة الالتهابات.", fontSize = 12.sp, color = textColor.copy(alpha = 0.85f))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📅 المناسبات والشهور الإسلامية:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
                Text("• المولد النبوي الشريف: 12 ربيع الأول", fontSize = 12.sp, color = textColor)
                Text("• ليلة الإسراء والمعراج: 27 رجب", fontSize = 12.sp, color = textColor)
                Text("• بداية شهر رمضان المبارك: 1 رمضان", fontSize = 12.sp, color = textColor)
                Text("• عيد الفطر المبارك: 1 شوال", fontSize = 12.sp, color = textColor)
                Text("• يوم عرفة وعيد الأضحى: 9 و 10 ذو الحجة", fontSize = 12.sp, color = textColor)
            }
        }
    }
}

@Composable
private fun AzkarSection(
    primaryColor: Color,
    textColor: Color
) {
    var selectedCategory by remember { mutableStateOf("الصباح") }
    val categories = listOf("الصباح", "المساء", "النوم", "الاستغفار")

    val morningAzkar = remember {
        listOf(
            DhikrItem(101, "أَصْبَحْنَا وَأَصْبَحَ المُلْكُ لِلَّهِ، وَالحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ.", 1, reward = "من قالها كُفي شر هذا اليوم وخيره"),
            DhikrItem(102, "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ.", 1, reward = "افتتاح اليوم بحفظ الله ورعايته"),
            DhikrItem(103, "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.", 3, reward = "تعدل أذكاراً كثيرة في الوزن والأجر"),
            DhikrItem(104, "آيَةُ الكُرْسِيّ: اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الحَيُّ القَيُّومُ...", 1, reward = "حفظ وملاذ من الشياطين حتى يمسي")
        )
    }

    val eveningAzkar = remember {
        listOf(
            DhikrItem(201, "أَمْسَيْنَا وَأَمْسَى المُلْكُ لِلَّهِ، وَالحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ.", 1, reward = "حصن الليل وحفظه حتى يصبح"),
            DhikrItem(202, "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, reward = "لم يضره شيء في تلك الليلة"),
            DhikrItem(203, "اللَّهُمَّ بكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ المَصِيرُ.", 1, reward = "تجديد التوكل في المساء")
        )
    }

    val sleepAzkar = remember {
        listOf(
            DhikrItem(301, "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي، وَبِكَ أَرْفَعُهُ، فَإِنْ أَمْسَكْتَ نَفْسِي فَارْحَمْهَا.", 1, reward = "حفظ النفس أثناء النوم"),
            DhikrItem(302, "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ.", 3, reward = "كان النبي ﷺ يقولها إذا أراد أن ينام")
        )
    }

    val istighfarAzkar = remember {
        listOf(
            DhikrItem(401, "أَسْتَغْفِرُ اللَّهَ العَظِيمَ الَّذِي لاَ إِلَهَ إِلاَّ هُوَ الحَيُّ القَيُّومُ وَأَتُوبُ إِلَيْهِ.", 100, reward = "غُفرت ذنوبه وإن كان فرّ من الزحف"),
            DhikrItem(402, "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ العَظِيمِ.", 100, reward = "كلمتان خفيفتان على اللسان ثقيلتان في الميزان"),
            DhikrItem(403, "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى سَيِّدِنَا وَنَبِيِّنَا مُحَمَّدٍ.", 10, reward = "من صلى عليّ واحدة صلى الله عليه بها عشراً")
        )
    }

    val currentAzkarList = when (selectedCategory) {
        "الصباح" -> morningAzkar
        "المساء" -> eveningAzkar
        "النوم" -> sleepAzkar
        else -> istighfarAzkar
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("أذكار اليوم والمسبحة التفاعلية:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSel = selectedCategory == cat
                FilterChip(
                    selected = isSel,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        currentAzkarList.forEach { dhikr ->
            val count by dhikr.currentCount
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = dhikr.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dhikr.reward,
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { dhikr.currentCount.value = 0 }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة العداد", tint = textColor.copy(alpha = 0.5f))
                        }

                        Button(
                            onClick = {
                                if (dhikr.currentCount.value < dhikr.targetCount) {
                                    dhikr.currentCount.value++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (count >= dhikr.targetCount) Color(0xFF10B981) else primaryColor
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (count >= dhikr.targetCount) "تم بحمد الله ✓" else "التكرار: $count / ${dhikr.targetCount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CultureSection(textColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📜 ثقافة ومعلومات إسلامية تاريخية:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("• يُعتبر التقويم الهجري قد اعتمد رسمياً في عهد الخليفة عمر بن الخطاب رضي الله عنه بدءاً من هجرة النبي ﷺ.", fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
        Text("• أول مسجد بني في الإسلام هو مسجد قباء بالمدينة المنورة.", fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
        Text("• أول من جمع القرآن الكريم في مصحف واحد هو الخليفة أبو بكر الصديق رضي الله عنه بإشارة من عمر بن الخطاب.", fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
    }
}

@Composable
private fun ToolsSection(
    primaryColor: Color,
    textColor: Color,
    onOpenQibla: () -> Unit = {},
    onOpenZakat: () -> Unit = {},
    onOpenQuran: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🛠️ أدوات نافعة متكاملة (اضغط لفتح الأداة مباشرة):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenQibla() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("بوصلة اتجاه القبلة الذكية 🧭", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("تحديد دقيق لاتجاه الكعبة المشرفة بمكة المكرمة", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenZakat() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Paid, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("حاسبة الزكاة الشرعية 🪙", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("حساب نصاب 85 جم ذهب والأموال وعروض التجارة", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenQuran() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF047857).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("المصحف الشريف المرتل 📖", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("قراءة وتلاوة السور والأجزاء مع الحفظ المرجعي", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun RowScope.PrayerCell(
    title: String,
    time: String,
    isNext: Boolean,
    primaryColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isNext) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isNext) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "bgColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isNext) MaterialTheme.colorScheme.onPrimary else textColor,
        animationSpec = tween(300),
        label = "contentColor"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNext) 6.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                color = if (isNext) contentColor else contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = time,
                fontSize = 15.sp,
                fontWeight = if (isNext) FontWeight.Black else FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

