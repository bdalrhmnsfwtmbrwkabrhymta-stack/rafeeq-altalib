package com.example.calculator

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hawas_scientific_calc_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): CalculatorUiState {
        val angleStr = prefs.getString("angle_unit", AngleUnit.DEG.name) ?: AngleUnit.DEG.name
        val formatStr = prefs.getString("result_format", ResultFormat.DECIMAL.name) ?: ResultFormat.DECIMAL.name
        val decimals = prefs.getInt("decimal_places", 8).coerceIn(0, 10)
        val ans = prefs.getString("ans_value", "0")?.toDoubleOrNull() ?: 0.0
        val mem = prefs.getString("memory_value", "0")?.toDoubleOrNull() ?: 0.0
        val history = loadHistoryFromPrefs()

        val settings = CalculatorSettings(
            defaultFormat = runCatching { ResultFormat.valueOf(formatStr) }.getOrDefault(ResultFormat.DECIMAL),
            defaultAngleUnit = runCatching { AngleUnit.valueOf(angleStr) }.getOrDefault(AngleUnit.DEG),
            decimalPlaces = decimals
        )

        return CalculatorUiState(
            angleUnit = settings.defaultAngleUnit,
            isFractionDisplayed = settings.defaultFormat == ResultFormat.FRACTION,
            settings = settings,
            ansValue = ans,
            memoryValue = mem,
            history = history,
            selectedHistoryId = history.firstOrNull()?.id
        )
    }

    private fun loadHistoryFromPrefs(): List<CalcHistoryEntry> {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CalcHistoryEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            expression = obj.optString("expression", ""),
                            result = obj.optString("result", "0"),
                            rawValue = if (obj.has("rawValue") && !obj.isNull("rawValue")) obj.optDouble("rawValue") else null,
                            timestampMillis = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistoryToPrefs(history: List<CalcHistoryEntry>) {
        val arr = JSONArray()
        history.take(40).forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("expression", entry.expression)
                    put("result", entry.result)
                    if (entry.rawValue != null && entry.rawValue.isFinite()) {
                        put("rawValue", entry.rawValue)
                    }
                    put("timestamp", entry.timestampMillis)
                }
            )
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    // ----------------------------------------------------------
    // Navigation & Sub-screens
    // ----------------------------------------------------------
    fun openAdvancedTool(tab: AdvancedCalcTab) {
        _uiState.update {
            it.copy(
                currentSubScreen = CalcSubScreen.ADVANCED_TOOLS,
                activeAdvancedTab = tab,
                showModeSheet = false,
                isShiftActive = false,
                isAlphaActive = false
            )
        }
    }

    fun selectAdvancedTab(tab: AdvancedCalcTab) {
        _uiState.update { it.copy(activeAdvancedTab = tab) }
    }

    fun openHistoryAndSettings() {
        _uiState.update {
            it.copy(
                currentSubScreen = CalcSubScreen.HISTORY_SETTINGS,
                showModeSheet = false
            )
        }
    }

    fun navigateToMain() {
        _uiState.update { it.copy(currentSubScreen = CalcSubScreen.MAIN) }
    }

    fun toggleModeSheet(show: Boolean) {
        _uiState.update { it.copy(showModeSheet = show) }
    }

    // ----------------------------------------------------------
    // Modifiers & Header Toggles
    // ----------------------------------------------------------
    fun cycleAngleUnit() {
        _uiState.update { state ->
            val next = state.angleUnit.next()
            val updated = state.copy(angleUnit = next)
            reEvaluateLiveIfCommitted(updated)
        }
    }

    fun toggleShift() {
        _uiState.update { it.copy(isShiftActive = !it.isShiftActive, isAlphaActive = false) }
    }

    fun toggleAlpha() {
        _uiState.update { it.copy(isAlphaActive = !it.isAlphaActive, isShiftActive = false) }
    }

    fun toggleHypOrAbs() {
        _uiState.update { state ->
            if (state.isShiftActive) {
                insertToken(state, "Abs(").copy(isShiftActive = false)
            } else {
                state.copy(isHypActive = !state.isHypActive)
            }
        }
    }

    fun toggleNotationMode() {
        _uiState.update { state ->
            val next = if (state.notationMode == CalcNotationMode.NORM) CalcNotationMode.SCI else CalcNotationMode.NORM
            val formatted = state.lastNumericResult?.let {
                formatDisplayedValue(it, state.settings.decimalPlaces, next, state.isFractionDisplayed, state.useMixedFractionDisplay)
            } ?: state.resultText
            state.copy(notationMode = next, resultText = formatted)
        }
    }

    // ----------------------------------------------------------
    // Cursor Navigation & Editing
    // ----------------------------------------------------------
    fun moveCursorLeft() {
        _uiState.update { state ->
            val nextIdx = (state.cursorIndex - 1).coerceAtLeast(0)
            state.copy(cursorIndex = nextIdx, isResultCommitted = false)
        }
    }

    fun moveCursorRight() {
        _uiState.update { state ->
            val nextIdx = (state.cursorIndex + 1).coerceAtMost(state.expression.length)
            state.copy(cursorIndex = nextIdx, isResultCommitted = false)
        }
    }

    fun onBackspace() {
        _uiState.update { state ->
            if (state.expression.isEmpty() || state.cursorIndex <= 0) return@update state
            val idx = state.cursorIndex.coerceIn(0, state.expression.length)
            // Smart multi-char token deletion for readability (sin(, cos(, tan(, Ans, mod, ×10^, etc.)
            val prefixes = listOf(
                "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(",
                "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
                "sinh(", "cosh(", "tanh(",
                "sin(", "cos(", "tan(",
                "cbrt(", "sqrt(", "Abs(", "log(", "ln(",
                "³√(", "√(", "×10^", "Ans", "mod", "⁻¹"
            )
            val before = state.expression.substring(0, idx)
            val matched = prefixes.firstOrNull { before.endsWith(it) }
            val deleteCount = matched?.length ?: 1
            val newExpr = before.dropLast(deleteCount) + state.expression.substring(idx)
            val newCursor = (idx - deleteCount).coerceAtLeast(0)
            state.copy(
                expression = newExpr,
                cursorIndex = newCursor,
                isError = false,
                isResultCommitted = false
            )
        }
    }

    fun onAllClear() {
        _uiState.update {
            it.copy(
                expression = "",
                cursorIndex = 0,
                resultText = "0",
                isError = false,
                isShiftActive = false,
                isAlphaActive = false,
                isHypActive = false,
                isResultCommitted = false
            )
        }
    }

    fun onPowerOnReset() {
        _uiState.update {
            it.copy(
                expression = "",
                cursorIndex = 0,
                resultText = "0",
                lastNumericResult = 0.0,
                isError = false,
                isShiftActive = false,
                isAlphaActive = false,
                isHypActive = false,
                isFractionDisplayed = it.settings.defaultFormat == ResultFormat.FRACTION,
                useMixedFractionDisplay = false,
                isResultCommitted = false
            )
        }
    }

    // ----------------------------------------------------------
    // Token Input & Function Keys
    // ----------------------------------------------------------
    fun appendDigitOrSymbol(text: String) {
        _uiState.update { state ->
            val base = if (state.isResultCommitted) {
                state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
            } else {
                state
            }
            insertToken(base, text).copy(isShiftActive = false, isAlphaActive = false)
        }
    }

    fun appendOperator(opSymbol: String) {
        _uiState.update { state ->
            val base = if (state.isResultCommitted && !state.isError) {
                state.copy(expression = "Ans", cursorIndex = 3, isResultCommitted = false)
            } else if (state.isResultCommitted) {
                state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
            } else {
                state
            }
            insertToken(base, opSymbol).copy(isShiftActive = false, isAlphaActive = false)
        }
    }

    fun onTrigKey(baseName: String) {
        _uiState.update { state ->
            val token = buildString {
                append(baseName)
                if (state.isHypActive) append("h")
                if (state.isShiftActive) append("⁻¹")
                append("(")
            }
            val base = if (state.isResultCommitted) {
                state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
            } else {
                state
            }
            insertToken(base, token).copy(
                isShiftActive = false,
                isAlphaActive = false,
                isHypActive = false
            )
        }
    }

    fun onPowerOrFuncKey(primary: String, shifted: String, isPostfixContinuation: Boolean = false) {
        _uiState.update { state ->
            val chosen = if (state.isShiftActive) shifted else primary
            val base = if (state.isResultCommitted && !state.isError && isPostfixContinuation) {
                state.copy(expression = "Ans", cursorIndex = 3, isResultCommitted = false)
            } else if (state.isResultCommitted) {
                state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
            } else {
                state
            }
            insertToken(base, chosen).copy(isShiftActive = false, isAlphaActive = false)
        }
    }

    fun onTenPowerKey() {
        _uiState.update { state ->
            val token = when {
                state.isShiftActive -> "π"
                state.isAlphaActive -> "e"
                else -> "×10^"
            }
            val base = if (state.isResultCommitted) {
                if (token == "×10^" && !state.isError) {
                    state.copy(expression = "Ans", cursorIndex = 3, isResultCommitted = false)
                } else {
                    state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
                }
            } else {
                state
            }
            insertToken(base, token).copy(isShiftActive = false, isAlphaActive = false)
        }
    }

    fun onModOrVarXKey() {
        _uiState.update { state ->
            if (state.isAlphaActive || state.isShiftActive) {
                val base = if (state.isResultCommitted) {
                    state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
                } else {
                    state
                }
                insertToken(base, "X").copy(isShiftActive = false, isAlphaActive = false)
            } else {
                val base = if (state.isResultCommitted && !state.isError) {
                    state.copy(expression = "Ans", cursorIndex = 3, isResultCommitted = false)
                } else {
                    state
                }
                insertToken(base, " mod ").copy(isShiftActive = false, isAlphaActive = false)
            }
        }
    }

    fun onFractionAbcKey() {
        _uiState.update { state ->
            if (state.isResultCommitted && state.lastNumericResult != null && !state.isError) {
                val mixed = !state.isShiftActive
                val formatted = formatDisplayedValue(
                    state.lastNumericResult,
                    state.settings.decimalPlaces,
                    state.notationMode,
                    asFraction = true,
                    mixed = mixed
                )
                state.copy(
                    isFractionDisplayed = true,
                    useMixedFractionDisplay = mixed,
                    resultText = formatted,
                    isShiftActive = false
                )
            } else {
                val base = if (state.isResultCommitted) {
                    state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
                } else {
                    state
                }
                insertToken(base, "÷").copy(isShiftActive = false)
            }
        }
    }

    fun onToggleSd() {
        _uiState.update { state ->
            val currentVal = state.lastNumericResult
                ?: (PureMathEngine.evaluate(
                    state.expression,
                    state.angleUnit,
                    state.ansValue,
                    state.memoryValue,
                    state.xVariableValue
                ) as? PureMathEngine.EvalOutcome.Success)?.value
                ?: return@update state

            val nextFractionState = !state.isFractionDisplayed
            val formatted = formatDisplayedValue(
                currentVal,
                state.settings.decimalPlaces,
                state.notationMode,
                asFraction = nextFractionState,
                mixed = state.useMixedFractionDisplay
            )
            state.copy(
                isFractionDisplayed = nextFractionState,
                lastNumericResult = currentVal,
                resultText = formatted,
                isError = false
            )
        }
    }

    // ----------------------------------------------------------
    // Memory Keys (M+, M-, RCL, STO)
    // ----------------------------------------------------------
    fun onMemoryPlusOrMinus() {
        _uiState.update { state ->
            val evaluated = if (state.expression.isNotBlank()) {
                when (val outcome = PureMathEngine.evaluate(
                    state.expression,
                    state.angleUnit,
                    state.ansValue,
                    state.memoryValue,
                    state.xVariableValue
                )) {
                    is PureMathEngine.EvalOutcome.Success -> outcome.value
                    is PureMathEngine.EvalOutcome.Failure -> {
                        return@update state.copy(resultText = outcome.messageAr, isError = true, isShiftActive = false)
                    }
                }
            } else {
                state.lastNumericResult ?: 0.0
            }

            val delta = if (state.isShiftActive) -evaluated else evaluated
            val newMem = PureMathEngine.snapEpsilon(state.memoryValue + delta)
            prefs.edit().putString("memory_value", newMem.toString()).apply()
            val formatted = formatDisplayedValue(
                evaluated,
                state.settings.decimalPlaces,
                state.notationMode,
                state.isFractionDisplayed,
                state.useMixedFractionDisplay
            )
            state.copy(
                memoryValue = newMem,
                lastNumericResult = evaluated,
                resultText = formatted,
                isError = false,
                isShiftActive = false,
                isResultCommitted = true
            )
        }
    }

    fun onRecallOrStore() {
        _uiState.update { state ->
            if (state.isShiftActive) {
                val valToStore = if (state.expression.isNotBlank() && !state.isResultCommitted) {
                    (PureMathEngine.evaluate(
                        state.expression,
                        state.angleUnit,
                        state.ansValue,
                        state.memoryValue,
                        state.xVariableValue
                    ) as? PureMathEngine.EvalOutcome.Success)?.value ?: state.lastNumericResult ?: 0.0
                } else {
                    state.lastNumericResult ?: 0.0
                }
                val snapped = PureMathEngine.snapEpsilon(valToStore)
                prefs.edit().putString("memory_value", snapped.toString()).apply()
                state.copy(
                    memoryValue = snapped,
                    isShiftActive = false,
                    resultText = PureMathEngine.formatNumber(snapped, state.settings.decimalPlaces, state.notationMode)
                )
            } else {
                val memStr = PureMathEngine.formatNumber(state.memoryValue, state.settings.decimalPlaces, CalcNotationMode.NORM)
                val base = if (state.isResultCommitted) {
                    state.copy(expression = "", cursorIndex = 0, isResultCommitted = false)
                } else {
                    state
                }
                insertToken(base, memStr).copy(isShiftActive = false)
            }
        }
    }

    // ----------------------------------------------------------
    // Evaluate (=)
    // ----------------------------------------------------------
    fun onEquals() {
        _uiState.update { state ->
            if (state.expression.isBlank()) return@update state
            when (
                val outcome = PureMathEngine.evaluate(
                    rawExpression = state.expression,
                    angleUnit = state.angleUnit,
                    ansValue = state.ansValue,
                    memoryValue = state.memoryValue,
                    xValue = state.xVariableValue
                )
            ) {
                is PureMathEngine.EvalOutcome.Success -> {
                    val valNum = outcome.value
                    val asFraction = state.settings.defaultFormat == ResultFormat.FRACTION
                    val formatted = formatDisplayedValue(
                        valNum,
                        state.settings.decimalPlaces,
                        state.notationMode,
                        asFraction = asFraction,
                        mixed = state.useMixedFractionDisplay
                    )
                    val newEntry = CalcHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        expression = state.expression,
                        result = formatted,
                        rawValue = valNum
                    )
                    val newHistory = (listOf(newEntry) + state.history).take(40)
                    saveHistoryToPrefs(newHistory)
                    prefs.edit().putString("ans_value", valNum.toString()).apply()

                    state.copy(
                        resultText = formatted,
                        lastNumericResult = valNum,
                        ansValue = valNum,
                        isError = false,
                        isFractionDisplayed = asFraction,
                        isResultCommitted = true,
                        isShiftActive = false,
                        isAlphaActive = false,
                        history = newHistory,
                        selectedHistoryId = newEntry.id
                    )
                }
                is PureMathEngine.EvalOutcome.Failure -> {
                    state.copy(
                        resultText = outcome.messageAr,
                        isError = true,
                        isResultCommitted = true,
                        isShiftActive = false,
                        isAlphaActive = false
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------
    // History & Settings Actions
    // ----------------------------------------------------------
    fun selectHistoryItem(id: String) {
        _uiState.update { it.copy(selectedHistoryId = id) }
    }

    fun restoreHistoryEntry(entry: CalcHistoryEntry) {
        _uiState.update {
            it.copy(
                expression = entry.expression,
                cursorIndex = entry.expression.length,
                resultText = entry.result,
                lastNumericResult = entry.rawValue ?: entry.result.toDoubleOrNull(),
                isError = false,
                isResultCommitted = true,
                selectedHistoryId = entry.id,
                currentSubScreen = CalcSubScreen.MAIN
            )
        }
    }

    fun clearHistory() {
        prefs.edit().remove("history_json").apply()
        _uiState.update { it.copy(history = emptyList(), selectedHistoryId = null) }
    }

    fun updateDefaultFormat(format: ResultFormat) {
        prefs.edit().putString("result_format", format.name).apply()
        _uiState.update { state ->
            val newSettings = state.settings.copy(defaultFormat = format)
            val isFrac = format == ResultFormat.FRACTION
            val updatedResult = state.lastNumericResult?.let {
                formatDisplayedValue(it, newSettings.decimalPlaces, state.notationMode, isFrac, state.useMixedFractionDisplay)
            } ?: state.resultText
            state.copy(
                settings = newSettings,
                isFractionDisplayed = isFrac,
                resultText = if (state.isError) state.resultText else updatedResult
            )
        }
    }

    fun updateDefaultAngleUnit(unit: AngleUnit) {
        prefs.edit().putString("angle_unit", unit.name).apply()
        _uiState.update { state ->
            val updated = state.copy(
                settings = state.settings.copy(defaultAngleUnit = unit),
                angleUnit = unit
            )
            reEvaluateLiveIfCommitted(updated)
        }
    }

    fun updateDecimalPlaces(places: Int) {
        val clamped = places.coerceIn(0, 10)
        prefs.edit().putInt("decimal_places", clamped).apply()
        _uiState.update { state ->
            val newSettings = state.settings.copy(decimalPlaces = clamped)
            val updatedResult = state.lastNumericResult?.let {
                formatDisplayedValue(it, clamped, state.notationMode, state.isFractionDisplayed, state.useMixedFractionDisplay)
            } ?: state.resultText
            state.copy(
                settings = newSettings,
                resultText = if (state.isError) state.resultText else updatedResult
            )
        }
    }

    // ----------------------------------------------------------
    // Internal Helpers
    // ----------------------------------------------------------
    private fun insertToken(state: CalculatorUiState, token: String): CalculatorUiState {
        val pos = state.cursorIndex.coerceIn(0, state.expression.length)
        val newExpr = state.expression.substring(0, pos) + token + state.expression.substring(pos)
        return state.copy(
            expression = newExpr,
            cursorIndex = pos + token.length,
            isError = false
        )
    }

    private fun formatDisplayedValue(
        value: Double,
        decimalPlaces: Int,
        notationMode: CalcNotationMode,
        asFraction: Boolean,
        mixed: Boolean
    ): String {
        if (asFraction) {
            val frac = PureMathEngine.toFraction(value)
            if (frac != null) {
                return if (mixed) frac.toMixedString() else frac.toImproperString()
            }
        }
        return PureMathEngine.formatNumber(value, decimalPlaces, notationMode)
    }

    private fun reEvaluateLiveIfCommitted(state: CalculatorUiState): CalculatorUiState {
        if (!state.isResultCommitted || state.expression.isBlank()) return state
        return when (
            val outcome = PureMathEngine.evaluate(
                state.expression,
                state.angleUnit,
                state.ansValue,
                state.memoryValue,
                state.xVariableValue
            )
        ) {
            is PureMathEngine.EvalOutcome.Success -> {
                val formatted = formatDisplayedValue(
                    outcome.value,
                    state.settings.decimalPlaces,
                    state.notationMode,
                    state.isFractionDisplayed,
                    state.useMixedFractionDisplay
                )
                state.copy(lastNumericResult = outcome.value, resultText = formatted, isError = false)
            }
            is PureMathEngine.EvalOutcome.Failure -> state
        }
    }
}
