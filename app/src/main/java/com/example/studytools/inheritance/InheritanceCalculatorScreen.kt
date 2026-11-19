package com.example.studytools.inheritance

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.text.NumberFormat
import java.util.Locale

private val NavyHeaderColor = Color(0xFF141E30)
private val GoldDisplayColor = Color(0xFFD99B38)
private val RoyalBlueAccent = Color(0xFF2855C8)
private val EmeraldAccent = Color(0xFF0A8F5B)

@Composable
fun InheritanceCalculatorScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var gender by remember { mutableStateOf(DeceasedGender.MALE) }
    var wivesCount by remember { mutableIntStateOf(0) }
    var hasHusband by remember { mutableStateOf(false) }

    var hasMother by remember { mutableStateOf(false) }
    var hasFather by remember { mutableStateOf(false) }
    var hasMaternalGrandmother by remember { mutableStateOf(false) }
    var hasPaternalGrandfather by remember { mutableStateOf(false) }

    var sonsCount by remember { mutableIntStateOf(0) }
    var daughtersCount by remember { mutableIntStateOf(1) }

    var showSiblingsSection by remember { mutableStateOf(false) }
    var fullBrothersCount by remember { mutableIntStateOf(0) }
    var fullSistersCount by remember { mutableIntStateOf(0) }
    var maternalSiblingsCount by remember { mutableIntStateOf(0) }

    var estateInputText by remember { mutableStateOf("") }
    var computedResult by remember { mutableStateOf<InheritanceResult?>(null) }
    val scrollState = rememberScrollState()

    val moneyFormatter = remember {
        NumberFormat.getNumberInstance(Locale("ar", "EG")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }

    fun triggerCalculation() {
        val parsedEstate = estateInputText
            .replace(",", "")
            .replace("٬", "")
            .trim()
            .toDoubleOrNull()
        val input = InheritanceInput(
            deceasedGender = gender,
            wivesCount = wivesCount,
            hasHusband = hasHusband,
            hasMother = hasMother,
            hasFather = hasFather,
            hasMaternalGrandmother = hasMaternalGrandmother,
            hasPaternalGrandfather = hasPaternalGrandfather,
            sonsCount = sonsCount,
            daughtersCount = daughtersCount,
            fullBrothersCount = fullBrothersCount,
            fullSistersCount = fullSistersCount,
            maternalSiblingsCount = maternalSiblingsCount,
            estateAmount = parsedEstate?.takeIf { it > 0.0 }
        )
        computedResult = InheritanceCalculatorEngine.calculate(input)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Surface(
                    color = EmeraldAccent,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Button(
                            onClick = { triggerCalculation() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyHeaderColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("inheritance_calculate_button")
                        ) {
                            Text(
                                text = "احسب الميراث",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Signature Royal Blue -> Obsidian Header matching Screenshot 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RoyalBlueAccent)
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = NavyHeaderColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("inheritance_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        gender = DeceasedGender.MALE
                                        wivesCount = 0
                                        hasHusband = false
                                        hasMother = false
                                        hasFather = false
                                        hasMaternalGrandmother = false
                                        hasPaternalGrandfather = false
                                        sonsCount = 0
                                        daughtersCount = 1
                                        fullBrothersCount = 0
                                        fullSistersCount = 0
                                        maternalSiblingsCount = 0
                                        estateInputText = ""
                                        computedResult = null
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("inheritance_reset_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "إعادة ضبط",
                                        tint = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Text(
                                text = "حَاسِبَةُ الْمَوَارِيث",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldDisplayColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "نصيب كل وارث بالشرع",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Deceased Gender Card (المتوفى)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "المتوفى",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DeceasedGender.entries.forEach { item ->
                                        val selected = gender == item
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(50))
                                                .background(if (selected) NavyHeaderColor else Color.Transparent)
                                                .clickable {
                                                    gender = item
                                                    if (item == DeceasedGender.MALE) {
                                                        hasHusband = false
                                                    } else {
                                                        wivesCount = 0
                                                    }
                                                }
                                                .testTag("inheritance_gender_${item.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.labelAr,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Spouses Card (الزوجات أو الزوج)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (gender == DeceasedGender.MALE) "الزوجات" else "الزوج",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (gender == DeceasedGender.MALE) {
                                val wifeOptions = listOf(0 to "مافيش", 1 to "واحدة", 2 to "اتنين", 4 to "أربعة")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    wifeOptions.forEach { (count, label) ->
                                        val selected = wivesCount == count
                                        InheritancePillChip(
                                            label = label,
                                            selected = selected,
                                            activeColor = RoyalBlueAccent,
                                            onClick = { wivesCount = count },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("inheritance_wives_$count")
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    InheritancePillChip(
                                        label = "مافيش",
                                        selected = !hasHusband,
                                        activeColor = RoyalBlueAccent,
                                        onClick = { hasHusband = false },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("inheritance_husband_none")
                                    )
                                    InheritancePillChip(
                                        label = "موجود",
                                        selected = hasHusband,
                                        activeColor = RoyalBlueAccent,
                                        onClick = { hasHusband = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("inheritance_husband_yes")
                                    )
                                }
                            }
                        }
                    }

                    // 3. Ancestors Card (الأصول)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "الأصول",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InheritancePillChip(
                                    label = "+ الأم",
                                    selected = hasMother,
                                    activeColor = EmeraldAccent,
                                    onClick = { hasMother = !hasMother },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inheritance_mother_chip")
                                )
                                InheritancePillChip(
                                    label = "+ الأب",
                                    selected = hasFather,
                                    activeColor = EmeraldAccent,
                                    onClick = { hasFather = !hasFather },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inheritance_father_chip")
                                )
                                InheritancePillChip(
                                    label = "+ أم الأم",
                                    selected = hasMaternalGrandmother,
                                    activeColor = RoyalBlueAccent,
                                    onClick = { hasMaternalGrandmother = !hasMaternalGrandmother },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inheritance_grandmother_chip")
                                )
                                InheritancePillChip(
                                    label = "+ أب الأب",
                                    selected = hasPaternalGrandfather,
                                    activeColor = RoyalBlueAccent,
                                    onClick = { hasPaternalGrandfather = !hasPaternalGrandfather },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inheritance_grandfather_chip")
                                )
                            }
                        }
                    }

                    // 4. Descendants Stepper Card (الأبناء والبنات)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الأبناء والبنات",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = { showSiblingsSection = !showSiblingsSection },
                                    modifier = Modifier.testTag("inheritance_toggle_siblings")
                                ) {
                                    Text(
                                        text = if (showSiblingsSection) "إخفاء الإخوة" else "+ الإخوة والأخوات",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HeirStepperRow(
                                label = "ابن",
                                count = sonsCount,
                                plusColor = EmeraldAccent,
                                minusColor = EmeraldAccent,
                                onDecrement = { if (sonsCount > 0) sonsCount-- },
                                onIncrement = { sonsCount++ },
                                testTagPrefix = "inheritance_sons"
                            )

                            HeirStepperRow(
                                label = "بنت",
                                count = daughtersCount,
                                plusColor = RoyalBlueAccent,
                                minusColor = RoyalBlueAccent,
                                onDecrement = { if (daughtersCount > 0) daughtersCount-- },
                                onIncrement = { daughtersCount++ },
                                testTagPrefix = "inheritance_daughters"
                            )

                            AnimatedVisibility(visible = showSiblingsSection) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HeirStepperRow(
                                        label = "أخ شقيق",
                                        count = fullBrothersCount,
                                        plusColor = NavyHeaderColor,
                                        minusColor = NavyHeaderColor,
                                        onDecrement = { if (fullBrothersCount > 0) fullBrothersCount-- },
                                        onIncrement = { fullBrothersCount++ },
                                        testTagPrefix = "inheritance_full_brothers"
                                    )
                                    HeirStepperRow(
                                        label = "أخت شقيقة",
                                        count = fullSistersCount,
                                        plusColor = GoldDisplayColor,
                                        minusColor = GoldDisplayColor,
                                        onDecrement = { if (fullSistersCount > 0) fullSistersCount-- },
                                        onIncrement = { fullSistersCount++ },
                                        testTagPrefix = "inheritance_full_sisters"
                                    )
                                    HeirStepperRow(
                                        label = "أخ/أخت لأم",
                                        count = maternalSiblingsCount,
                                        plusColor = RoyalBlueAccent,
                                        minusColor = RoyalBlueAccent,
                                        onDecrement = { if (maternalSiblingsCount > 0) maternalSiblingsCount-- },
                                        onIncrement = { maternalSiblingsCount++ },
                                        testTagPrefix = "inheritance_maternal_siblings"
                                    )
                                }
                            }
                        }
                    }

                    // 5. Optional Estate Value Input
                    OutlinedTextField(
                        value = estateInputText,
                        onValueChange = { estateInputText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        placeholder = { Text("مقدار التركة (اختياري)") },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inheritance_estate_input")
                    )

                    // 6. Results Breakdown Section
                    computedResult?.let { result ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.5.dp, EmeraldAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inheritance_results_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "توزيع التركة الشرعي",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                val formatted = formatResultText(result, moneyFormatter)
                                                clipboardManager.setText(AnnotatedString(formatted))
                                                Toast.makeText(context, "تم نسخ تقسيم الميراث", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ النتيجة")
                                        }
                                        IconButton(
                                            onClick = {
                                                val formatted = formatResultText(result, moneyFormatter)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, formatted)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة تقسيم الميراث"))
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "مشاركة النتيجة")
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = RoyalBlueAccent.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = result.summaryNoteAr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RoyalBlueAccent,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                result.shares.forEach { share ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = share.heirTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = EmeraldAccent
                                                ) {
                                                    Text(
                                                        text = "${share.fraction.formatFraction()}  •  ${String.format(Locale.US, "%.2f%%", share.percentage)}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "نوع الإرث: ${share.shareTypeLabel}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RoyalBlueAccent
                                            )
                                            Text(
                                                text = "الدليل والتعليل: ${share.shariaReason}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (share.moneyPerGroup != null) {
                                                val totalTxt = moneyFormatter.format(share.moneyPerGroup)
                                                val eachTxt = share.moneyPerPerson?.let { moneyFormatter.format(it) } ?: totalTxt
                                                Text(
                                                    text = if (share.count > 1) {
                                                        "النصيب المالي: $totalTxt (لكل فرد: $eachTxt)"
                                                    } else {
                                                        "النصيب المالي: $totalTxt"
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = EmeraldAccent
                                                )
                                            }
                                        }
                                    }
                                }

                                if (result.excludedHeirs.isNotEmpty()) {
                                    Text(
                                        text = "الورثة المحجوبون",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    result.excludedHeirs.forEach { ex ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "${ex.heirTitle} — ${ex.shareTypeLabel}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = ex.shariaReason,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun InheritancePillChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) activeColor else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) activeColor else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeirStepperRow(
    label: String,
    count: Int,
    plusColor: Color,
    minusColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = DisplayArabicFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onDecrement,
                shape = RoundedCornerShape(10.dp),
                color = minusColor,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("${testTagPrefix}_minus")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "نقص $label",
                        tint = Color.White
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(min = 56.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Surface(
                onClick = onIncrement,
                shape = RoundedCornerShape(10.dp),
                color = plusColor,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("${testTagPrefix}_plus")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "زيادة $label",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun formatResultText(result: InheritanceResult, fmt: NumberFormat): String {
    val sb = StringBuilder()
    sb.appendLine("📊 نتيجة حاسبة المواريث الشرعية:")
    sb.appendLine(result.summaryNoteAr)
    sb.appendLine()
    result.shares.forEach { s ->
        val money = s.moneyPerGroup?.let { " | القيمة: ${fmt.format(it)}" } ?: ""
        sb.appendLine("• ${s.heirTitle}: ${s.fraction.formatFraction()} (${String.format(Locale.US, "%.2f%%", s.percentage)})$money")
        sb.appendLine("  السبب: ${s.shariaReason}")
    }
    if (result.excludedHeirs.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("المحجوبون:")
        result.excludedHeirs.forEach { ex ->
            sb.appendLine("• ${ex.heirTitle}: ${ex.shariaReason}")
        }
    }
    return sb.toString()
}
