package com.example.calculator

import androidx.compose.ui.graphics.Color

// ============================================================
// Exact Visual Identity Tokens (Hawas Al-Dirasa Scientific Calc)
// ============================================================
object CalcColors {
    val CanvasBg = Color(0xFFF8FAFC)
    val CardWhite = Color(0xFFFFFFFF)
    val BorderSoft = Color(0xFFE2E8F0)
    val RoyalBlue = Color(0xFF1A6FE3)
    val ToolChipBg = Color(0xFFE0F2FE)
    val ToolChipBorder = Color(0xFFBAE6FD)
    val ShiftAmber = Color(0xFFF59E0B)
    val AlphaPurple = Color(0xFFA855F7)
    val DeleteOrange = Color(0xFFF59E0B)
    val ClearRed = Color(0xFFEF4444)
    val TextDark = Color(0xFF0F172A)
    val TextSlate = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)
    val SoftBlueTint = Color(0xFFEFF6FF)
    val SoftAmberTint = Color(0xFFFEF3C7)
    val SoftPurpleTint = Color(0xFFF3E8FF)
    val SuccessGreen = Color(0xFF059669)
}

enum class AngleUnit(val label: String, val arLabel: String) {
    DEG("DEG", "DEG"),
    RAD("RAD", "RAD"),
    GRAD("GRAD", "GRAD");

    fun next(): AngleUnit = when (this) {
        DEG -> RAD
        RAD -> GRAD
        GRAD -> DEG
    }
}

enum class ResultFormat(val code: String) {
    DECIMAL("DEC"),
    FRACTION("FRAC")
}

enum class CalcNotationMode(val label: String) {
    NORM("NORM"),
    SCI("SCI")
}

enum class AdvancedCalcTab(val label: String, val arTitle: String) {
    INTEGRAL("∫dx", "التكامل ∫dx"),
    DERIVATIVE("d/dx", "التفاضل d/dx"),
    SOLVE("SOLVE", "حل المعادلات"),
    MATRIX("MATRIX", "المصفوفات"),
    STAT("STAT", "الإحصاء"),
    CMPLX("CMPLX", "الأعداد المركبة")
}

enum class CalcSubScreen {
    MAIN,
    ADVANCED_TOOLS,
    HISTORY_SETTINGS
}

data class CalcHistoryEntry(
    val id: String,
    val expression: String,
    val result: String,
    val rawValue: Double? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class CalculatorSettings(
    val defaultFormat: ResultFormat = ResultFormat.DECIMAL,
    val defaultAngleUnit: AngleUnit = AngleUnit.DEG,
    val decimalPlaces: Int = 8
)

data class FractionResult(
    val numerator: Long,
    val denominator: Long
) {
    fun toImproperString(): String = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    fun toMixedString(): String {
        if (denominator == 1L) return "$numerator"
        val sign = if (numerator < 0) "-" else ""
        val absNum = kotlin.math.abs(numerator)
        val whole = absNum / denominator
        val rem = absNum % denominator
        return when {
            whole == 0L -> "$sign$rem/$denominator"
            rem == 0L -> "$sign$whole"
            else -> "$sign$whole $rem/$denominator"
        }
    }
}

data class ComplexNumber(
    val real: Double,
    val imag: Double
)

data class StatSummary(
    val count: Int,
    val sum: Double,
    val sumSquares: Double,
    val mean: Double,
    val sampleStdDev: Double?,
    val populationStdDev: Double,
    val median: Double,
    val min: Double,
    val max: Double
)

data class CalculatorUiState(
    val expression: String = "",
    val cursorIndex: Int = 0,
    val resultText: String = "0",
    val lastNumericResult: Double? = 0.0,
    val isError: Boolean = false,
    val isFractionDisplayed: Boolean = false,
    val useMixedFractionDisplay: Boolean = false,
    val isShiftActive: Boolean = false,
    val isAlphaActive: Boolean = false,
    val isHypActive: Boolean = false,
    val isResultCommitted: Boolean = false,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val notationMode: CalcNotationMode = CalcNotationMode.NORM,
    val ansValue: Double = 0.0,
    val memoryValue: Double = 0.0,
    val xVariableValue: Double = 0.0,
    val settings: CalculatorSettings = CalculatorSettings(),
    val history: List<CalcHistoryEntry> = emptyList(),
    val selectedHistoryId: String? = null,
    val currentSubScreen: CalcSubScreen = CalcSubScreen.MAIN,
    val activeAdvancedTab: AdvancedCalcTab = AdvancedCalcTab.INTEGRAL,
    val showModeSheet: Boolean = false
)
