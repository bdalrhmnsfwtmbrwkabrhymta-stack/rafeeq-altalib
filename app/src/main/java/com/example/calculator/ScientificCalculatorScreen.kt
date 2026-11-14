package com.example.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun ScientificCalculatorScreen(
    onNavigateBack: () -> Unit,
    calcViewModel: CalculatorViewModel = viewModel()
) {
    val uiState by calcViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        if (uiState.currentSubScreen != CalcSubScreen.MAIN) {
            calcViewModel.navigateToMain()
        } else {
            onNavigateBack()
        }
    }

    when (uiState.currentSubScreen) {
        CalcSubScreen.ADVANCED_TOOLS -> {
            AdvancedCalculusToolsScreen(
                activeTab = uiState.activeAdvancedTab,
                angleUnit = uiState.angleUnit,
                decimalPlaces = uiState.settings.decimalPlaces,
                onSelectTab = calcViewModel::selectAdvancedTab,
                onOpenHistorySettings = calcViewModel::openHistoryAndSettings,
                onBackToCalculator = calcViewModel::navigateToMain
            )
            return
        }
        CalcSubScreen.HISTORY_SETTINGS -> {
            CalculatorHistorySettingsScreen(
                uiState = uiState,
                onSelectHistoryItem = calcViewModel::selectHistoryItem,
                onRestoreHistoryEntry = calcViewModel::restoreHistoryEntry,
                onClearHistory = calcViewModel::clearHistory,
                onUpdateDefaultFormat = calcViewModel::updateDefaultFormat,
                onUpdateDefaultAngleUnit = calcViewModel::updateDefaultAngleUnit,
                onUpdateDecimalPlaces = calcViewModel::updateDecimalPlaces,
                onBack = calcViewModel::navigateToMain
            )
            return
        }
        CalcSubScreen.MAIN -> Unit
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalcColors.CanvasBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // ----------------------------------------------------------
            // Top Bar (RTL): Right [→ الآلة الحاسبة] | Left [DEG | 🕒 | ⚙️]
            // ----------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = CalcColors.TextDark
                        )
                    }
                    Text(
                        text = "الآلة الحاسبة",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextDark
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = calcViewModel::cycleAngleUnit,
                        shape = RoundedCornerShape(10.dp),
                        color = CalcColors.ToolChipBg,
                        border = BorderStroke(1.dp, CalcColors.ToolChipBorder),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("calc_angle_pill")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = uiState.angleUnit.label,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CalcColors.RoyalBlue
                            )
                        }
                    }

                    IconButton(
                        onClick = calcViewModel::openHistoryAndSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "سجل العمليات",
                            tint = CalcColors.TextSlate
                        )
                    }

                    IconButton(
                        onClick = calcViewModel::openHistoryAndSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calc_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "إعدادات الحاسبة",
                            tint = CalcColors.TextSlate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ----------------------------------------------------------
            // LTR MATH LOCK: LCD Display Card + 9-Row Keypad
            // ----------------------------------------------------------
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                CalculatorLcdCard(
                    uiState = uiState,
                    onToggleNotation = calcViewModel::toggleNotationMode
                )

                Spacer(modifier = Modifier.height(10.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val gap = 6.dp
                    val totalGaps = gap * 8
                    val usableHeight = (maxHeight - totalGaps).coerceAtLeast(360.dp)
                    // 5 scientific rows (compact) + 4 numpad rows (slightly taller)
                    val sciRowHeight = (usableHeight * 0.095f).coerceIn(38.dp, 48.dp)
                    val numRowHeight = ((usableHeight - sciRowHeight * 5) / 4f).coerceIn(46.dp, 62.dp)

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        // ROW 1 (Tools): ∫dx | d/dx | SOLVE | MATRIX | STAT | CMPLX
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sciRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ToolRowKey("∫dx", Modifier.weight(1f), "btn_tool_integral") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.INTEGRAL)
                            }
                            ToolRowKey("d/dx", Modifier.weight(1f), "btn_tool_deriv") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.DERIVATIVE)
                            }
                            ToolRowKey("SOLVE", Modifier.weight(1f), "btn_tool_solve") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.SOLVE)
                            }
                            ToolRowKey("MATRIX", Modifier.weight(1f), "btn_tool_matrix") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.MATRIX)
                            }
                            ToolRowKey("STAT", Modifier.weight(1f), "btn_tool_stat") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.STAT)
                            }
                            ToolRowKey("CMPLX", Modifier.weight(1f), "btn_tool_cmplx") {
                                calcViewModel.openAdvancedTool(AdvancedCalcTab.CMPLX)
                            }
                        }

                        // ROW 2 (Control): SHIFT | ALPHA | < | > | MODE | ON
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sciRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SolidColorKey(
                                label = "SHIFT",
                                bgColor = CalcColors.ShiftAmber,
                                isActive = uiState.isShiftActive,
                                fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f),
                                testTag = "btn_shift",
                                onClick = calcViewModel::toggleShift
                            )
                            SolidColorKey(
                                label = "ALPHA",
                                bgColor = CalcColors.AlphaPurple,
                                isActive = uiState.isAlphaActive,
                                fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f),
                                testTag = "btn_alpha",
                                onClick = calcViewModel::toggleAlpha
                            )
                            SciWhiteKey("<", null, Modifier.weight(1f), testTag = "btn_cursor_left") {
                                calcViewModel.moveCursorLeft()
                            }
                            SciWhiteKey(">", null, Modifier.weight(1f), testTag = "btn_cursor_right") {
                                calcViewModel.moveCursorRight()
                            }
                            SciWhiteKey("MODE", null, Modifier.weight(1f), fontSize = 11.sp, testTag = "btn_mode") {
                                calcViewModel.toggleModeSheet(true)
                            }
                            SciWhiteKey("ON", null, Modifier.weight(1f), fontSize = 12.sp, testTag = "btn_on") {
                                calcViewModel.onPowerOnReset()
                            }
                        }

                        // ROW 3 (Powers/Logs): ∫dx(d/dx) | x⁻¹(x!) | x²(x³) | xʸ(ʸ√x) | log(10ˣ) | ln(eˣ)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sciRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SciWhiteKey("∫dx", "d/dx", Modifier.weight(1f), testTag = "btn_calc_int") {
                                val target = if (uiState.isShiftActive) AdvancedCalcTab.DERIVATIVE else AdvancedCalcTab.INTEGRAL
                                calcViewModel.openAdvancedTool(target)
                            }
                            SciWhiteKey("x⁻¹", "x!", Modifier.weight(1f), testTag = "btn_inv_fact") {
                                calcViewModel.onPowerOrFuncKey("⁻¹", "!", isPostfixContinuation = true)
                            }
                            SciWhiteKey("x²", "x³", Modifier.weight(1f), testTag = "btn_sq_cube") {
                                calcViewModel.onPowerOrFuncKey("²", "³", isPostfixContinuation = true)
                            }
                            SciWhiteKey("xʸ", "ʸ√x", Modifier.weight(1f), testTag = "btn_pow_yroot") {
                                calcViewModel.onPowerOrFuncKey("^", "^(1÷", isPostfixContinuation = true)
                            }
                            SciWhiteKey("log", "10ˣ", Modifier.weight(1f), testTag = "btn_log_10x") {
                                calcViewModel.onPowerOrFuncKey("log(", "10^")
                            }
                            SciWhiteKey("ln", "eˣ", Modifier.weight(1f), testTag = "btn_ln_ex") {
                                calcViewModel.onPowerOrFuncKey("ln(", "e^")
                            }
                        }

                        // ROW 4 (Trig/Roots): a b/c(d/c) | √(³√) | hyp(Abs) | sin(sin⁻¹) | cos(cos⁻¹) | tan(tan⁻¹)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sciRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SciWhiteKey("a b/c", "d/c", Modifier.weight(1f), fontSize = 11.sp, testTag = "btn_frac_abc") {
                                calcViewModel.onFractionAbcKey()
                            }
                            SciWhiteKey("√", "³√", Modifier.weight(1f), testTag = "btn_sqrt_cbrt") {
                                calcViewModel.onPowerOrFuncKey("√(", "³√(")
                            }
                            SciWhiteKey(
                                primary = "hyp",
                                secondary = "Abs",
                                modifier = Modifier.weight(1f),
                                isHighlighted = uiState.isHypActive,
                                testTag = "btn_hyp_abs"
                            ) {
                                calcViewModel.toggleHypOrAbs()
                            }
                            SciWhiteKey("sin", "sin⁻¹", Modifier.weight(1f), testTag = "btn_sin") {
                                calcViewModel.onTrigKey("sin")
                            }
                            SciWhiteKey("cos", "cos⁻¹", Modifier.weight(1f), testTag = "btn_cos") {
                                calcViewModel.onTrigKey("cos")
                            }
                            SciWhiteKey("tan", "tan⁻¹", Modifier.weight(1f), testTag = "btn_tan") {
                                calcViewModel.onTrigKey("tan")
                            }
                        }

                        // ROW 5 (Memory & Brackets): RCL(STO) | mod(X) | ( | ) | S↔D | M+(M-)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sciRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SciWhiteKey("RCL", "STO", Modifier.weight(1f), testTag = "btn_rcl_sto") {
                                calcViewModel.onRecallOrStore()
                            }
                            SciWhiteKey(
                                primary = "mod",
                                secondary = "X",
                                secondaryColor = CalcColors.AlphaPurple,
                                modifier = Modifier.weight(1f),
                                testTag = "btn_mod_x"
                            ) {
                                calcViewModel.onModOrVarXKey()
                            }
                            SciWhiteKey("(", null, Modifier.weight(1f), testTag = "btn_lparen") {
                                calcViewModel.appendDigitOrSymbol("(")
                            }
                            SciWhiteKey(")", null, Modifier.weight(1f), testTag = "btn_rparen") {
                                calcViewModel.appendDigitOrSymbol(")")
                            }
                            SciWhiteKey("S↔D", null, Modifier.weight(1f), testTag = "btn_sd") {
                                calcViewModel.onToggleSd()
                            }
                            SciWhiteKey("M+", "M-", Modifier.weight(1f), testTag = "btn_m_plus") {
                                calcViewModel.onMemoryPlusOrMinus()
                            }
                        }

                        // ROW 6 (Numpad): 7 | 8 | 9 | ⌫ | AC
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(numRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NumWhiteKey("7", Modifier.weight(1f), "btn_7") { calcViewModel.appendDigitOrSymbol("7") }
                            NumWhiteKey("8", Modifier.weight(1f), "btn_8") { calcViewModel.appendDigitOrSymbol("8") }
                            NumWhiteKey("9", Modifier.weight(1f), "btn_9") { calcViewModel.appendDigitOrSymbol("9") }
                            DeleteOrangeKey(Modifier.weight(1f)) { calcViewModel.onBackspace() }
                            SolidColorKey(
                                label = "AC",
                                bgColor = CalcColors.ClearRed,
                                fontSize = 17.sp,
                                modifier = Modifier.weight(1f),
                                testTag = "btn_ac",
                                onClick = calcViewModel::onAllClear
                            )
                        }

                        // ROW 7 (Numpad): 4 | 5 | 6 | × | ÷
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(numRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NumWhiteKey("4", Modifier.weight(1f), "btn_4") { calcViewModel.appendDigitOrSymbol("4") }
                            NumWhiteKey("5", Modifier.weight(1f), "btn_5") { calcViewModel.appendDigitOrSymbol("5") }
                            NumWhiteKey("6", Modifier.weight(1f), "btn_6") { calcViewModel.appendDigitOrSymbol("6") }
                            OpWhiteKey("×", Modifier.weight(1f), "btn_mul") { calcViewModel.appendOperator("×") }
                            OpWhiteKey("÷", Modifier.weight(1f), "btn_div") { calcViewModel.appendOperator("÷") }
                        }

                        // ROW 8 (Numpad): 1 | 2 | 3 | + | −
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(numRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NumWhiteKey("1", Modifier.weight(1f), "btn_1") { calcViewModel.appendDigitOrSymbol("1") }
                            NumWhiteKey("2", Modifier.weight(1f), "btn_2") { calcViewModel.appendDigitOrSymbol("2") }
                            NumWhiteKey("3", Modifier.weight(1f), "btn_3") { calcViewModel.appendDigitOrSymbol("3") }
                            OpWhiteKey("+", Modifier.weight(1f), "btn_add") { calcViewModel.appendOperator("+") }
                            OpWhiteKey("−", Modifier.weight(1f), "btn_sub") { calcViewModel.appendOperator("−") }
                        }

                        // ROW 9 (Result Row): 0 | . | ×10ˣ | Ans | =
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(numRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NumWhiteKey("0", Modifier.weight(1f), "btn_0") { calcViewModel.appendDigitOrSymbol("0") }
                            NumWhiteKey(".", Modifier.weight(1f), "btn_dot") { calcViewModel.appendDigitOrSymbol(".") }
                            SciWhiteKey(
                                primary = "×10ˣ",
                                secondary = if (uiState.isAlphaActive) "e" else "π",
                                secondaryColor = if (uiState.isAlphaActive) CalcColors.AlphaPurple else Color(0xFFD97706),
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                testTag = "btn_exp_pi"
                            ) {
                                calcViewModel.onTenPowerKey()
                            }
                            NumWhiteKey("Ans", Modifier.weight(1f), "btn_ans", fontSize = 15.sp) {
                                calcViewModel.appendDigitOrSymbol("Ans")
                            }
                            SolidColorKey(
                                label = "=",
                                bgColor = CalcColors.RoyalBlue,
                                fontSize = 24.sp,
                                modifier = Modifier.weight(1f),
                                testTag = "btn_equals",
                                onClick = calcViewModel::onEquals
                            )
                        }
                    }
                }
            }
        }
    }

    // MODE Quick Picker Dialog (Arabic RTL)
    if (uiState.showModeSheet) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AlertDialog(
                onDismissRequest = { calcViewModel.toggleModeSheet(false) },
                containerColor = CalcColors.CardWhite,
                title = {
                    Text(
                        text = "وضع الآلة الحاسبة (MODE)",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextDark
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeOptionItem("الحساب العلمي الأساسي (COMP)") {
                            calcViewModel.toggleModeSheet(false)
                        }
                        AdvancedCalcTab.entries.forEach { tab ->
                            ModeOptionItem("${tab.arTitle} (${tab.label})") {
                                calcViewModel.openAdvancedTool(tab)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { calcViewModel.toggleModeSheet(false) }) {
                        Text("إغلاق", fontFamily = ReadexProFontFamily, color = CalcColors.RoyalBlue)
                    }
                }
            )
        }
    }
}

@Composable
private fun ModeOptionItem(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CalcColors.CanvasBg,
        border = BorderStroke(1.dp, CalcColors.BorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontFamily = ReadexProFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = CalcColors.TextDark,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

// ----------------------------------------------------------
// LCD Display Card (Exact Image 1 Layout in LTR)
// ----------------------------------------------------------
@Composable
private fun CalculatorLcdCard(
    uiState: CalculatorUiState,
    onToggleNotation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CalcColors.CardWhite,
        border = BorderStroke(1.dp, CalcColors.BorderSoft),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calc_lcd_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Status Row: Active Modifiers (Left) + DEG | NORM | MATH (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uiState.isShiftActive) {
                        StatusBadge("SHIFT", CalcColors.ShiftAmber)
                    }
                    if (uiState.isAlphaActive) {
                        StatusBadge("ALPHA", CalcColors.AlphaPurple)
                    }
                    if (uiState.isHypActive) {
                        StatusBadge("HYP", CalcColors.RoyalBlue)
                    }
                    if (uiState.memoryValue != 0.0) {
                        StatusBadge("M", CalcColors.SuccessGreen)
                    }
                    if (uiState.isFractionDisplayed) {
                        StatusBadge("a/b", CalcColors.TextSlate)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(onClick = onToggleNotation)
                ) {
                    Text(
                        text = uiState.angleUnit.label,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextSlate
                    )
                    Text(
                        text = uiState.notationMode.label,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextSlate
                    )
                    Text(
                        text = "MATH",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalcColors.TextSlate
                    )
                }
            }

            // Expression Row (Left aligned, horizontally scrollable, with cursor indicator)
            val exprWithCursor = buildAnnotatedString {
                val expr = uiState.expression
                if (expr.isEmpty()) {
                    withStyle(SpanStyle(color = CalcColors.TextMuted)) {
                        append("0")
                    }
                } else {
                    val idx = uiState.cursorIndex.coerceIn(0, expr.length)
                    append(expr.substring(0, idx))
                    if (!uiState.isResultCommitted) {
                        withStyle(SpanStyle(color = CalcColors.RoyalBlue, fontWeight = FontWeight.Normal)) {
                            append("|")
                        }
                    }
                    append(expr.substring(idx))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = exprWithCursor,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CalcColors.TextDark,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("calc_expression_text")
                )
            }

            // Result Row (Bottom Right in Royal Blue 28.sp Bold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = uiState.resultText,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isError) CalcColors.ClearRed else CalcColors.RoyalBlue,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calc_result_text")
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontFamily = ReadexProFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ----------------------------------------------------------
// Key Components (Exact Image 1 Styling & Colors)
// ----------------------------------------------------------
@Composable
private fun ToolRowKey(
    label: String,
    modifier: Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = CalcColors.ToolChipBg,
        border = BorderStroke(1.dp, CalcColors.ToolChipBorder),
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CalcColors.RoyalBlue,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun SolidColorKey(
    label: String,
    bgColor: Color,
    isActive: Boolean = false,
    fontSize: TextUnit = 14.sp,
    modifier: Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (isActive) BorderStroke(2.dp, CalcColors.TextDark) else null,
        shadowElevation = if (isActive) 4.dp else 1.dp,
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeleteOrangeKey(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CalcColors.DeleteOrange,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxHeight()
            .testTag("btn_backspace")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "حذف",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SciWhiteKey(
    primary: String,
    secondary: String?,
    modifier: Modifier,
    secondaryColor: Color = Color(0xFFD97706),
    isHighlighted: Boolean = false,
    fontSize: TextUnit = 13.sp,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) CalcColors.ToolChipBg else CalcColors.CardWhite,
        border = BorderStroke(1.dp, if (isHighlighted) CalcColors.RoyalBlue else CalcColors.BorderSoft),
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (secondary != null) {
                Text(
                    text = secondary,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryColor,
                    lineHeight = 9.sp,
                    maxLines = 1
                )
            }
            Text(
                text = primary,
                fontFamily = ReadexProFontFamily,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (isHighlighted) CalcColors.RoyalBlue else CalcColors.TextDark,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NumWhiteKey(
    label: String,
    modifier: Modifier,
    testTag: String,
    fontSize: TextUnit = 19.sp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CalcColors.CardWhite,
        border = BorderStroke(1.dp, CalcColors.BorderSoft),
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = CalcColors.TextDark,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OpWhiteKey(
    label: String,
    modifier: Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CalcColors.CardWhite,
        border = BorderStroke(1.dp, CalcColors.BorderSoft),
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = CalcColors.RoyalBlue,
                maxLines = 1
            )
        }
    }
}
