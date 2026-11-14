package com.example.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun AdvancedCalculusToolsScreen(
    activeTab: AdvancedCalcTab,
    angleUnit: AngleUnit,
    decimalPlaces: Int,
    onSelectTab: (AdvancedCalcTab) -> Unit,
    onOpenHistorySettings: () -> Unit,
    onBackToCalculator: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalcColors.CanvasBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar matching Image 2 (→ الحساب | 🕒 ⚙️)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToCalculator,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_tools_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = CalcColors.TextDark
                        )
                    }
                    Text(
                        text = "الحساب",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenHistorySettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_tools_history")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "السجل",
                            tint = CalcColors.TextSlate
                        )
                    }
                    IconButton(
                        onClick = onOpenHistorySettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_tools_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "الإعدادات",
                            tint = CalcColors.TextSlate
                        )
                    }
                }
            }

            // 6 Tool Selector Pills (LTR visual ordering inside pill bar matching Image 2)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdvancedCalcTab.entries.forEach { tab ->
                        val selected = tab == activeTab
                        Surface(
                            onClick = { onSelectTab(tab) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) CalcColors.RoyalBlue else CalcColors.CardWhite,
                            border = BorderStroke(
                                1.dp,
                                if (selected) CalcColors.RoyalBlue else CalcColors.BorderSoft
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("calc_tab_${tab.name}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else CalcColors.TextDark
                                )
                            }
                        }
                    }
                }
            }

            // Active Tool Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (activeTab) {
                    AdvancedCalcTab.INTEGRAL -> IntegralToolContent(angleUnit, decimalPlaces)
                    AdvancedCalcTab.DERIVATIVE -> DerivativeToolContent(angleUnit, decimalPlaces)
                    AdvancedCalcTab.SOLVE -> SolveToolContent(angleUnit, decimalPlaces)
                    AdvancedCalcTab.MATRIX -> MatrixToolContent(decimalPlaces)
                    AdvancedCalcTab.STAT -> StatToolContent(decimalPlaces)
                    AdvancedCalcTab.CMPLX -> ComplexToolContent(angleUnit, decimalPlaces)
                }
            }
        }
    }
}

// ----------------------------------------------------------
// 1. Definite Integral ∫dx (Composite Simpson n = 1000)
// ----------------------------------------------------------
@Composable
private fun IntegralToolContent(angleUnit: AngleUnit, decimalPlaces: Int) {
    var fnExpr by remember { mutableStateOf("x^2") }
    var lowerStr by remember { mutableStateOf("0") }
    var upperStr by remember { mutableStateOf("2") }
    var outcome by remember {
        mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(
            AdvancedCalculusEngine.definiteIntegralSimpson("x^2", "0", "2", angleUnit, decimalPlaces)
        )
    }

    ToolHeaderTitle(title = "التكامل ∫dx", subtitle = "احسب تكامل بين حدين")

    CalcInputCard(label = "الدالة بدلالة x") {
        LtrOutlinedMathField(
            value = fnExpr,
            onValueChange = { fnExpr = it },
            placeholder = "x^2",
            testTag = "integral_fn_input"
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CalcInputCard(label = "من") {
                LtrOutlinedMathField(
                    value = lowerStr,
                    onValueChange = { lowerStr = it },
                    placeholder = "0",
                    testTag = "integral_lower_input"
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            CalcInputCard(label = "إلى") {
                LtrOutlinedMathField(
                    value = upperStr,
                    onValueChange = { upperStr = it },
                    placeholder = "2",
                    testTag = "integral_upper_input"
                )
            }
        }
    }

    Text(
        text = "الحساب عددي بطريقة سيمبسون (n = 1000) — النتيجة تقريب دقيق حتى 8 أرقام",
        fontFamily = ReadexProFontFamily,
        fontSize = 12.sp,
        color = CalcColors.TextSlate,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    PrimaryCalcActionButton(
        text = "احسب التكامل",
        testTag = "integral_compute_btn",
        onClick = {
            outcome = AdvancedCalculusEngine.definiteIntegralSimpson(
                fnExpr, lowerStr, upperStr, angleUnit, decimalPlaces
            )
        }
    )

    ToolResultCard(outcome)
}

// ----------------------------------------------------------
// 2. First Derivative d/dx (Central Difference)
// ----------------------------------------------------------
@Composable
private fun DerivativeToolContent(angleUnit: AngleUnit, decimalPlaces: Int) {
    var fnExpr by remember { mutableStateOf("x^3 + 2*x") }
    var pointStr by remember { mutableStateOf("2") }
    var outcome by remember { mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(null) }

    ToolHeaderTitle(title = "التفاضل d/dx", subtitle = "المشتقة الأولى عند نقطة x = a")

    CalcInputCard(label = "الدالة f(x)") {
        LtrOutlinedMathField(
            value = fnExpr,
            onValueChange = { fnExpr = it },
            placeholder = "x^3 + 2*x",
            testTag = "deriv_fn_input"
        )
    }

    CalcInputCard(label = "عند النقطة x = a") {
        LtrOutlinedMathField(
            value = pointStr,
            onValueChange = { pointStr = it },
            placeholder = "2",
            testTag = "deriv_point_input"
        )
    }

    PrimaryCalcActionButton(
        text = "احسب المشتقة",
        testTag = "deriv_compute_btn",
        onClick = {
            outcome = AdvancedCalculusEngine.derivativeAtPoint(fnExpr, pointStr, angleUnit, decimalPlaces)
        }
    )

    ToolResultCard(outcome)
}

// ----------------------------------------------------------
// 3. Equation Solver (Quadratic, Cubic, 2x2, 3x3, Newton)
// ----------------------------------------------------------
private enum class SolveSubMode(val titleAr: String) {
    QUADRATIC("تربيعية ax²+bx+c=0"),
    CUBIC("تكعيبية ax³+bx²+cx+d=0"),
    LINEAR_2X2("نظام خطي 2×2"),
    LINEAR_3X3("نظام خطي 3×3"),
    NEWTON("نيوتن-رافسون f(x)=0")
}

@Composable
private fun SolveToolContent(angleUnit: AngleUnit, decimalPlaces: Int) {
    var mode by remember { mutableStateOf(SolveSubMode.QUADRATIC) }
    var outcome by remember { mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(null) }

    // Quadratic & Cubic coeffs
    var aStr by remember { mutableStateOf("1") }
    var bStr by remember { mutableStateOf("-5") }
    var cStr by remember { mutableStateOf("6") }
    var dStr by remember { mutableStateOf("0") }

    // 2x2 / 3x3 linear system grid (up to 3 rows x 4 cols)
    val sysVals = remember {
        mutableStateListOf(
            "2", "1", "5", "0",
            "1", "-1", "1", "0",
            "0", "0", "1", "1"
        )
    }

    // Newton-Raphson
    var newtonFn by remember { mutableStateOf("x^2 - 2") }
    var newtonX0 by remember { mutableStateOf("1") }

    ToolHeaderTitle(title = "حل المعادلات (SOLVE)", subtitle = "المعادلات الجبرية والأنظمة والجذور")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SolveSubMode.entries.forEach { m ->
            val selected = m == mode
            Surface(
                onClick = {
                    mode = m
                    outcome = null
                },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) CalcColors.ToolChipBg else CalcColors.CardWhite,
                border = BorderStroke(1.dp, if (selected) CalcColors.RoyalBlue else CalcColors.BorderSoft)
            ) {
                Text(
                    text = m.titleAr,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) CalcColors.RoyalBlue else CalcColors.TextDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    when (mode) {
        SolveSubMode.QUADRATIC -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { CalcInputCard("a") { LtrNumberField(aStr) { aStr = it } } }
                Box(Modifier.weight(1f)) { CalcInputCard("b") { LtrNumberField(bStr) { bStr = it } } }
                Box(Modifier.weight(1f)) { CalcInputCard("c") { LtrNumberField(cStr) { cStr = it } } }
            }
            PrimaryCalcActionButton("أوجد الجذور", "solve_quad_btn") {
                val a = aStr.toDoubleOrNull()
                val b = bStr.toDoubleOrNull()
                val c = cStr.toDoubleOrNull()
                outcome = if (a == null || b == null || c == null) {
                    AdvancedCalculusEngine.ToolOutcome.Err("أدخل معاملات رقمية صحيحة")
                } else {
                    AdvancedCalculusEngine.solveQuadratic(a, b, c, decimalPlaces)
                }
            }
        }
        SolveSubMode.CUBIC -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { CalcInputCard("a") { LtrNumberField(aStr) { aStr = it } } }
                Box(Modifier.weight(1f)) { CalcInputCard("b") { LtrNumberField(bStr) { bStr = it } } }
                Box(Modifier.weight(1f)) { CalcInputCard("c") { LtrNumberField(cStr) { cStr = it } } }
                Box(Modifier.weight(1f)) { CalcInputCard("d") { LtrNumberField(dStr) { dStr = it } } }
            }
            PrimaryCalcActionButton("أوجد الجذور", "solve_cubic_btn") {
                val a = aStr.toDoubleOrNull()
                val b = bStr.toDoubleOrNull()
                val c = cStr.toDoubleOrNull()
                val d = dStr.toDoubleOrNull()
                outcome = if (a == null || b == null || c == null || d == null) {
                    AdvancedCalculusEngine.ToolOutcome.Err("أدخل معاملات رقمية صحيحة")
                } else {
                    AdvancedCalculusEngine.solveCubic(a, b, c, d, decimalPlaces)
                }
            }
        }
        SolveSubMode.LINEAR_2X2, SolveSubMode.LINEAR_3X3 -> {
            val n = if (mode == SolveSubMode.LINEAR_2X2) 2 else 3
            val colHeaders = if (n == 2) listOf("x", "y", "=") else listOf("x", "y", "z", "=")
            CalcInputCard(label = if (n == 2) "المعاملات: aᵢx + bᵢy = cᵢ" else "المعاملات: aᵢx + bᵢy + cᵢz = dᵢ") {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until n) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0..n) {
                                    val idx = r * 4 + c
                                    OutlinedTextField(
                                        value = sysVals[idx],
                                        onValueChange = { sysVals[idx] = it },
                                        singleLine = true,
                                        label = { Text(colHeaders[c], fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PrimaryCalcActionButton("حل النظام الخطي", "solve_linear_btn") {
                val aug = Array(n) { r ->
                    DoubleArray(n + 1) { c -> sysVals[r * 4 + c].toDoubleOrNull() ?: Double.NaN }
                }
                val invalid = aug.any { row -> row.any { it.isNaN() } }
                outcome = if (invalid) {
                    AdvancedCalculusEngine.ToolOutcome.Err("أكمل جميع الخانات بأرقام صحيحة")
                } else {
                    AdvancedCalculusEngine.solveLinearSystem(aug, decimalPlaces)
                }
            }
        }
        SolveSubMode.NEWTON -> {
            CalcInputCard("المعادلة f(x) = 0") {
                LtrOutlinedMathField(newtonFn, { newtonFn = it }, "x^2 - 2", "solve_newton_fn")
            }
            CalcInputCard("التخمين الابتدائي x₀") {
                LtrNumberField(newtonX0) { newtonX0 = it }
            }
            PrimaryCalcActionButton("إيجاد الجذر (نيوتن-رافسون)", "solve_newton_btn") {
                val x0 = newtonX0.toDoubleOrNull()
                outcome = if (x0 == null) {
                    AdvancedCalculusEngine.ToolOutcome.Err("أدخل قيمة x₀ صالحة")
                } else {
                    AdvancedCalculusEngine.solveNewtonRaphson(newtonFn, x0, angleUnit, decimalPlaces)
                }
            }
        }
    }

    ToolResultCard(outcome)
}

// ----------------------------------------------------------
// 4. Matrix Operations (2x2 & 3x3)
// ----------------------------------------------------------
@Composable
private fun MatrixToolContent(decimalPlaces: Int) {
    var dim by remember { mutableIntStateOf(2) }
    val matA = remember { mutableStateListOf("1", "2", "0", "3", "4", "0", "0", "0", "1") }
    val matB = remember { mutableStateListOf("5", "6", "0", "7", "8", "0", "0", "0", "1") }
    var outcome by remember { mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(null) }

    ToolHeaderTitle(title = "حساب المصفوفات (MATRIX)", subtitle = "العمليات الجبرية والمحدد والمنقول والمعكوس")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(2 to "مصفوفة 2×2", 3 to "مصفوفة 3×3").forEach { (size, label) ->
            val selected = dim == size
            Surface(
                onClick = {
                    dim = size
                    outcome = null
                },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) CalcColors.RoyalBlue else CalcColors.CardWhite,
                border = BorderStroke(1.dp, if (selected) CalcColors.RoyalBlue else CalcColors.BorderSoft),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else CalcColors.TextDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }

    MatrixGridInputCard("المصفوفة A", dim, matA)
    MatrixGridInputCard("المصفوفة B", dim, matB)

    fun readMatrix(list: List<String>): Array<DoubleArray>? {
        val m = Array(dim) { r -> DoubleArray(dim) { c -> list[r * 3 + c].toDoubleOrNull() ?: return null } }
        return m
    }

    fun formatMatrixOutcome(title: String, m: Array<DoubleArray>): AdvancedCalculusEngine.ToolOutcome {
        val rows = m.mapIndexed { r, row ->
            "الصف ${r + 1}" to row.joinToString("   ") { PureMathEngine.formatNumber(it, decimalPlaces.coerceAtMost(6)) }
        }
        val compact = m.joinToString("\n") { row ->
            "[ " + row.joinToString(" , ") { PureMathEngine.formatNumber(it, decimalPlaces.coerceAtMost(4)) } + " ]"
        }
        return AdvancedCalculusEngine.ToolOutcome.Ok("$title:\n$compact", rows)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatrixOpButton("A + B", Modifier.weight(1f)) {
            val a = readMatrix(matA); val b = readMatrix(matB)
            outcome = if (a == null || b == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم المصفوفتين")
            else formatMatrixOutcome("A + B", AdvancedCalculusEngine.matrixAdd(a, b))
        }
        MatrixOpButton("A − B", Modifier.weight(1f)) {
            val a = readMatrix(matA); val b = readMatrix(matB)
            outcome = if (a == null || b == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم المصفوفتين")
            else formatMatrixOutcome("A − B", AdvancedCalculusEngine.matrixSub(a, b))
        }
        MatrixOpButton("A × B", Modifier.weight(1f)) {
            val a = readMatrix(matA); val b = readMatrix(matB)
            outcome = if (a == null || b == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم المصفوفتين")
            else formatMatrixOutcome("A × B", AdvancedCalculusEngine.matrixMul(a, b))
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatrixOpButton("det(A)", Modifier.weight(1f)) {
            val a = readMatrix(matA)
            outcome = if (a == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم A")
            else {
                val det = AdvancedCalculusEngine.matrixDeterminant(a)
                AdvancedCalculusEngine.ToolOutcome.Ok("det(A) = ${PureMathEngine.formatNumber(det, decimalPlaces)}")
            }
        }
        MatrixOpButton("Aᵀ", Modifier.weight(1f)) {
            val a = readMatrix(matA)
            outcome = if (a == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم A")
            else formatMatrixOutcome("منقول Aᵀ", AdvancedCalculusEngine.matrixTranspose(a))
        }
        MatrixOpButton("A⁻¹", Modifier.weight(1f)) {
            val a = readMatrix(matA)
            outcome = if (a == null) AdvancedCalculusEngine.ToolOutcome.Err("تحقق من قيم A")
            else {
                val inv = AdvancedCalculusEngine.matrixInverse(a)
                if (inv == null) AdvancedCalculusEngine.ToolOutcome.Err("المصفوفة منفردة (det(A) = 0) لا معكوس لها")
                else formatMatrixOutcome("معكوس A⁻¹", inv)
            }
        }
    }

    ToolResultCard(outcome)
}

@Composable
private fun MatrixGridInputCard(title: String, dim: Int, values: MutableList<String>) {
    CalcInputCard(label = title) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until dim) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until dim) {
                            val idx = r * 3 + c
                            OutlinedTextField(
                                value = values[idx],
                                onValueChange = { values[idx] = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixOpButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CalcColors.ToolChipBg,
        border = BorderStroke(1.dp, CalcColors.ToolChipBorder),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = CalcColors.RoyalBlue
            )
        }
    }
}

// ----------------------------------------------------------
// 5. Statistics (STAT)
// ----------------------------------------------------------
@Composable
private fun StatToolContent(decimalPlaces: Int) {
    var rawData by remember { mutableStateOf("12, 15, 18, 20, 25") }
    var outcome by remember { mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(null) }

    ToolHeaderTitle(title = "التحليل الإحصائي (STAT)", subtitle = "أدخل الأرقام مفصولة بفاصلة أو مسافة")

    CalcInputCard(label = "قائمة البيانات") {
        LtrOutlinedMathField(
            value = rawData,
            onValueChange = { rawData = it },
            placeholder = "10, 20, 30, 40",
            testTag = "stat_data_input"
        )
    }

    PrimaryCalcActionButton("احسب المؤشرات الإحصائية", "stat_compute_btn") {
        val summary = AdvancedCalculusEngine.computeStats(rawData)
        outcome = if (summary == null) {
            AdvancedCalculusEngine.ToolOutcome.Err("أدخل قيماً رقمية صحيحة مفصولة بفاصلة أو مسافة")
        } else {
            val details = buildList {
                add("العدد (n)" to "${summary.count}")
                add("المتوسط الحسابي (x̄)" to PureMathEngine.formatNumber(summary.mean, decimalPlaces))
                add("المجموع (Σx)" to PureMathEngine.formatNumber(summary.sum, decimalPlaces))
                add("مجموع المربعات (Σx²)" to PureMathEngine.formatNumber(summary.sumSquares, decimalPlaces))
                add("الوسيط (Median)" to PureMathEngine.formatNumber(summary.median, decimalPlaces))
                add("الانحراف المعياري للمجتمع (σ)" to PureMathEngine.formatNumber(summary.populationStdDev, decimalPlaces))
                if (summary.sampleStdDev != null) {
                    add("الانحراف المعياري للعينة (s)" to PureMathEngine.formatNumber(summary.sampleStdDev, decimalPlaces))
                }
            }
            AdvancedCalculusEngine.ToolOutcome.Ok(
                primaryResult = "x̄ = ${PureMathEngine.formatNumber(summary.mean, decimalPlaces)}",
                details = details
            )
        }
    }

    ToolResultCard(outcome)
}

// ----------------------------------------------------------
// 6. Complex Numbers (CMPLX)
// ----------------------------------------------------------
@Composable
private fun ComplexToolContent(angleUnit: AngleUnit, decimalPlaces: Int) {
    var a1 by remember { mutableStateOf("3") }
    var b1 by remember { mutableStateOf("4") }
    var a2 by remember { mutableStateOf("1") }
    var b2 by remember { mutableStateOf("-2") }
    var outcome by remember { mutableStateOf<AdvancedCalculusEngine.ToolOutcome?>(null) }

    ToolHeaderTitle(title = "الأعداد المركبة (CMPLX)", subtitle = "العمليات على a + bi والتحويل للصيغة القطبية r ∠ θ")

    CalcInputCard("العدد المركب z₁ = a + bi") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { LtrNumberField(a1, label = "الجزء الحقيقي (a)") { a1 = it } }
            Box(Modifier.weight(1f)) { LtrNumberField(b1, label = "الجزء التخيلي (b)") { b1 = it } }
        }
    }

    CalcInputCard("العدد المركب z₂ = c + di") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { LtrNumberField(a2, label = "الجزء الحقيقي (c)") { a2 = it } }
            Box(Modifier.weight(1f)) { LtrNumberField(b2, label = "الجزء التخيلي (d)") { b2 = it } }
        }
    }

    fun readZ1(): ComplexNumber? {
        val r = a1.toDoubleOrNull() ?: return null
        val i = b1.toDoubleOrNull() ?: return null
        return ComplexNumber(r, i)
    }

    fun readZ2(): ComplexNumber? {
        val r = a2.toDoubleOrNull() ?: return null
        val i = b2.toDoubleOrNull() ?: return null
        return ComplexNumber(r, i)
    }

    fun showComplexOutcome(label: String, z: ComplexNumber): AdvancedCalculusEngine.ToolOutcome {
        val rect = AdvancedCalculusEngine.formatComplex(z, decimalPlaces)
        val mod = PureMathEngine.formatNumber(AdvancedCalculusEngine.modulus(z), decimalPlaces)
        val arg = PureMathEngine.formatNumber(AdvancedCalculusEngine.argument(z, angleUnit), decimalPlaces)
        val conj = AdvancedCalculusEngine.formatComplex(AdvancedCalculusEngine.conjugate(z), decimalPlaces)
        return AdvancedCalculusEngine.ToolOutcome.Ok(
            primaryResult = "$label = $rect",
            details = listOf(
                "المرافق (z̄)" to conj,
                "المقياس |z|" to mod,
                "السعة θ (${angleUnit.label})" to arg,
                "الصيغة القطبية" to "$mod ∠ $arg"
            )
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatrixOpButton("z₁ + z₂", Modifier.weight(1f)) {
            val z1 = readZ1(); val z2 = readZ2()
            outcome = if (z1 == null || z2 == null) AdvancedCalculusEngine.ToolOutcome.Err("أدخل أرقاماً صالحة")
            else showComplexOutcome("z₁ + z₂", AdvancedCalculusEngine.addComplex(z1, z2))
        }
        MatrixOpButton("z₁ − z₂", Modifier.weight(1f)) {
            val z1 = readZ1(); val z2 = readZ2()
            outcome = if (z1 == null || z2 == null) AdvancedCalculusEngine.ToolOutcome.Err("أدخل أرقاماً صالحة")
            else showComplexOutcome("z₁ − z₂", AdvancedCalculusEngine.subComplex(z1, z2))
        }
        MatrixOpButton("z₁ × z₂", Modifier.weight(1f)) {
            val z1 = readZ1(); val z2 = readZ2()
            outcome = if (z1 == null || z2 == null) AdvancedCalculusEngine.ToolOutcome.Err("أدخل أرقاماً صالحة")
            else showComplexOutcome("z₁ × z₂", AdvancedCalculusEngine.mulComplex(z1, z2))
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatrixOpButton("z₁ ÷ z₂", Modifier.weight(1f)) {
            val z1 = readZ1(); val z2 = readZ2()
            outcome = if (z1 == null || z2 == null) {
                AdvancedCalculusEngine.ToolOutcome.Err("أدخل أرقاماً صالحة")
            } else {
                val res = AdvancedCalculusEngine.divComplex(z1, z2)
                if (res == null) AdvancedCalculusEngine.ToolOutcome.Err("قسمة على صفر مركب")
                else showComplexOutcome("z₁ ÷ z₂", res)
            }
        }
        MatrixOpButton("قطبية z₁", Modifier.weight(1f)) {
            val z1 = readZ1()
            outcome = if (z1 == null) AdvancedCalculusEngine.ToolOutcome.Err("أدخل أرقام z₁")
            else showComplexOutcome("z₁", z1)
        }
    }

    ToolResultCard(outcome)
}

// ----------------------------------------------------------
// Shared Reusable UI Building Blocks
// ----------------------------------------------------------
@Composable
private fun ToolHeaderTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontFamily = ReadexProFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CalcColors.TextDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontFamily = ReadexProFontFamily,
            fontSize = 13.sp,
            color = CalcColors.TextSlate,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalcInputCard(label: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CalcColors.CardWhite,
        border = BorderStroke(1.dp, CalcColors.BorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = CalcColors.TextSlate
            )
            content()
        }
    }
}

@Composable
private fun LtrOutlinedMathField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = CalcColors.TextMuted) },
            textStyle = TextStyle(
                fontFamily = ReadexProFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = CalcColors.TextDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
private fun LtrNumberField(
    value: String,
    label: String? = null,
    onValueChange: (String) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = label?.let { { Text(it, fontSize = 11.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                fontFamily = ReadexProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = CalcColors.TextDark
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PrimaryCalcActionButton(
    text: String,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CalcColors.RoyalBlue,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            fontFamily = ReadexProFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToolResultCard(outcome: AdvancedCalculusEngine.ToolOutcome?) {
    if (outcome == null) return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CalcColors.CardWhite,
        border = BorderStroke(
            1.dp,
            if (outcome is AdvancedCalculusEngine.ToolOutcome.Err) CalcColors.ClearRed.copy(alpha = 0.4f)
            else CalcColors.BorderSoft
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (outcome) {
                is AdvancedCalculusEngine.ToolOutcome.Err -> {
                    Text(
                        text = outcome.message,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.ClearRed,
                        textAlign = TextAlign.Center
                    )
                }
                is AdvancedCalculusEngine.ToolOutcome.Ok -> {
                    Text(
                        text = "النتيجة",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CalcColors.TextSlate
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = outcome.primaryResult,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = CalcColors.RoyalBlue,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (outcome.details.isNotEmpty()) {
                        HorizontalDivider(color = CalcColors.BorderSoft, modifier = Modifier.padding(vertical = 4.dp))
                        outcome.details.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = k,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.5.sp,
                                    color = CalcColors.TextSlate
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = v,
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CalcColors.TextDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
