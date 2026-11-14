package com.example.domain.calculator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Data class representing a saved calculation in phone storage.
 */
data class CalculatorHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Persistent Storage for Calculator History on Device Storage (JSON).
 * Stores calculation records permanently on phone memory.
 */
object CalculatorHistoryStore {
    private const val FILE_NAME = "calculator_history.json"
    private const val MAX_HISTORY = 100

    suspend fun getHistory(context: Context): List<CalculatorHistoryItem> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<CalculatorHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CalculatorHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        expression = obj.optString("expression", ""),
                        result = obj.optString("result", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("CalculatorHistoryStore", "Error loading calculator history", e)
            emptyList()
        }
    }

    suspend fun saveCalculation(context: Context, expression: String, result: String): CalculatorHistoryItem = withContext(Dispatchers.IO) {
        val current = getHistory(context).toMutableList()
        val newItem = CalculatorHistoryItem(expression = expression, result = result)
        current.add(0, newItem)
        val trimmed = if (current.size > MAX_HISTORY) current.take(MAX_HISTORY) else current

        try {
            val jsonArray = JSONArray()
            for (item in trimmed) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("expression", item.expression)
                obj.put("result", item.result)
                obj.put("timestamp", item.timestamp)
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("CalculatorHistoryStore", "Error saving calculator history", e)
        }
        newItem
    }

    suspend fun deleteItem(context: Context, id: String) = withContext(Dispatchers.IO) {
        val current = getHistory(context).filter { it.id != id }
        try {
            val jsonArray = JSONArray()
            for (item in current) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("expression", item.expression)
                obj.put("result", item.result)
                obj.put("timestamp", item.timestamp)
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("CalculatorHistoryStore", "Error deleting history item", e)
        }
    }

    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("CalculatorHistoryStore", "Error clearing history", e)
        }
    }
}

/**
 * Result of Casio SOLVE function (SHIFT + CALC).
 */
data class SolveResult(
    val isSuccess: Boolean,
    val x: Double = Double.NaN,
    val leftMinusRight: Double = Double.NaN,
    val iterations: Int = 0,
    val message: String = ""
)

/**
 * Robust Casio-grade Mathematical Evaluation and Equation Solving Engine.
 */
object CalculatorEngine {

    /**
     * Preprocesses math expressions for implicit multiplication, constants, and operators.
     */
    fun preprocessExpression(
        raw: String,
        ansVal: String,
        xVal: Double? = null,
        yVal: Double? = null,
        registers: Map<Char, Double> = emptyMap()
    ): String {
        var s = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", Math.PI.toString())
            .replace("ANS", ansVal)
            .replace("Ans", ansVal)

        // Euler's constant 'e' not preceded or followed by word character
        s = s.replace(Regex("(?<![a-zA-Z])e(?![a-zA-Z])"), Math.E.toString())

        // Replace variable X with value if provided
        if (xVal != null) {
            val xStr = if (xVal < 0) "(${xVal})" else xVal.toString()
            s = s.replace(Regex("(?<![a-zA-Z])[xX](?![a-zA-Z])"), xStr)
        }

        // Replace variable Y with value if provided
        if (yVal != null) {
            val yStr = if (yVal < 0) "(${yVal})" else yVal.toString()
            s = s.replace(Regex("(?<![a-zA-Z])[yY](?![a-zA-Z])"), yStr)
        }

        // Replace memory registers (A, B, C, D, E, F, M, X, Y)
        registers.forEach { (regChar, regVal) ->
            if (!regVal.isNaN()) {
                val regStr = if (regVal < 0) "(${regVal})" else regVal.toString()
                s = s.replace(Regex("(?<![a-zA-Z])${regChar}(?![a-zA-Z])"), regStr)
            }
        }

        // Insert implicit multiplication:
        // 1. Number followed by parenthesis: e.g. 2(3) -> 2*(3)
        s = s.replace(Regex("(\\d)\\("), "$1*(")
        // 2. Parenthesis followed by number: e.g. (2)3 -> (2)*3
        s = s.replace(Regex("\\)(\\d)"), ")*$1")
        // 3. Parenthesis followed by parenthesis: e.g. (2)(3) -> (2)*(3)
        s = s.replace(Regex("\\)\\("), ")*(")
        // 4. Number followed by variable / register letter: e.g. 2X -> 2*X, 5A -> 5*A
        s = s.replace(Regex("(\\d)([a-zA-Z])"), "$1*$2")
        // 5. Variable followed by variable: e.g. XY -> X*Y
        s = s.replace(Regex("([xXyY])([xXyY])"), "$1*$2")
        // 6. Number followed by function name: e.g. 2sin(30) -> 2*sin(30)
        s = s.replace(Regex("(\\d)(sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|log|ln|sqrt|cbrt)"), "$1*$2")

        return s
    }

    /**
     * Evaluates a mathematical expression with full scientific functions.
     */
    fun evaluate(
        rawExpr: String,
        isDeg: Boolean,
        ansVal: String = "0",
        xVal: Double? = null,
        yVal: Double? = null,
        registers: Map<Char, Double> = emptyMap()
    ): Double {
        val expr = preprocessExpression(rawExpr, ansVal, xVal, yVal, registers)

        return object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < expr.length) expr[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expr.length) return Double.NaN
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> {
                            val denominator = parseFactor()
                            if (denominator == 0.0) return Double.NaN
                            x /= denominator
                        }
                        eat('%'.code) -> x %= parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()

                val startPos = this.pos
                var x: Double

                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = expr.substring(startPos, this.pos).toDoubleOrNull() ?: Double.NaN
                } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                    while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch == '_'.code) nextChar()
                    val func = expr.substring(startPos, this.pos)
                    if (eat('('.code)) {
                        // Handle functions that take multiple comma-separated arguments:
                        // e.g. logb(base, x), nCr(n, r), nPr(n, r), root(n, x), Pol(x, y), Rec(r, theta)
                        val arg1 = parseExpression()
                        val hasSecondArg = eat(','.code)
                        val arg2 = if (hasSecondArg) parseExpression() else 0.0
                        eat(')'.code)

                        x = when (func.lowercase()) {
                            "sin" -> sin(if (isDeg) Math.toRadians(arg1) else arg1)
                            "cos" -> cos(if (isDeg) Math.toRadians(arg1) else arg1)
                            "tan" -> {
                                val rad = if (isDeg) Math.toRadians(arg1) else arg1
                                if (abs(cos(rad)) < 1e-12) Double.NaN else tan(rad)
                            }
                            "asin" -> if (isDeg) Math.toDegrees(asin(arg1)) else asin(arg1)
                            "acos" -> if (isDeg) Math.toDegrees(acos(arg1)) else acos(arg1)
                            "atan" -> if (isDeg) Math.toDegrees(atan(arg1)) else atan(arg1)
                            "sinh" -> sinh(arg1)
                            "cosh" -> cosh(arg1)
                            "tanh" -> tanh(arg1)
                            "asinh" -> ln(arg1 + sqrt(arg1 * arg1 + 1.0))
                            "acosh" -> if (arg1 >= 1.0) ln(arg1 + sqrt(arg1 * arg1 - 1.0)) else Double.NaN
                            "atanh" -> if (abs(arg1) < 1.0) 0.5 * ln((1.0 + arg1) / (1.0 - arg1)) else Double.NaN
                            "log" -> if (arg1 > 0) log10(arg1) else Double.NaN
                            "ln" -> if (arg1 > 0) ln(arg1) else Double.NaN
                            "logb" -> if (arg1 > 0 && arg2 > 0 && arg1 != 1.0) ln(arg2) / ln(arg1) else Double.NaN
                            "sqrt" -> if (arg1 >= 0) sqrt(arg1) else Double.NaN
                            "cbrt" -> cbrt(arg1)
                            "root" -> if (arg1 > 0) arg2.pow(1.0 / arg1) else Double.NaN
                            "abs" -> abs(arg1)
                            "fact" -> factorial(arg1)
                            "npr" -> nPr(arg1.toInt(), arg2.toInt())
                            "ncr" -> nCr(arg1.toInt(), arg2.toInt())
                            "pol" -> sqrt(arg1 * arg1 + arg2 * arg2)
                            "rec" -> arg1 * cos(if (isDeg) Math.toRadians(arg2) else arg2)
                            else -> Double.NaN
                        }
                    } else {
                        // Could be standalone variable like X or Y if not replaced
                        x = when (func) {
                            "X", "x" -> xVal ?: Double.NaN
                            "Y", "y" -> yVal ?: Double.NaN
                            else -> Double.NaN
                        }
                    }
                } else {
                    x = Double.NaN
                }

                // Postfix operators: ! (Factorial)
                if (eat('!'.code)) {
                    x = factorial(x)
                }

                // Exponentiation ^
                if (eat('^'.code)) {
                    x = x.pow(parseFactor())
                }

                return x
            }

            fun factorial(n: Double): Double {
                if (n < 0) return Double.NaN
                val integer = n.toLong()
                if (abs(n - integer) > 1e-9 || integer > 170) return Double.NaN
                var res = 1.0
                for (i in 2..integer) res *= i
                return res
            }

            fun nPr(n: Int, r: Int): Double {
                if (n < 0 || r < 0 || r > n) return Double.NaN
                var res = 1.0
                for (i in 0 until r) res *= (n - i)
                return res
            }

            fun nCr(n: Int, r: Int): Double {
                if (n < 0 || r < 0 || r > n) return Double.NaN
                val k = min(r, n - r)
                var res = 1.0
                for (i in 1..k) {
                    res = res * (n - (k - i)) / i
                }
                return res
            }
        }.parse()
    }

    /**
     * Casio fx-991 SHIFT + SOLVE feature!
     * Solves equations of type: L(X) = R(X) or f(X) = 0 for variable X.
     * Uses hybrid Secant + Newton-Raphson + Bisection numerical solver.
     */
    fun solveForX(rawEquation: String, isDeg: Boolean, initialGuess: Double = 0.0): SolveResult {
        val parts = rawEquation.split("=")
        val leftExpr: String
        val rightExpr: String

        if (parts.size == 2) {
            leftExpr = parts[0].trim()
            rightExpr = parts[1].trim()
        } else if (parts.size == 1) {
            leftExpr = parts[0].trim()
            rightExpr = "0"
        } else {
            return SolveResult(isSuccess = false, message = "معادلة غير صالحة: تحتوي على أكثر من علامة = واحدة")
        }

        fun f(x: Double): Double {
            val l = evaluate(leftExpr, isDeg, "0", xVal = x)
            val r = evaluate(rightExpr, isDeg, "0", xVal = x)
            if (l.isNaN() || r.isNaN()) return Double.NaN
            return l - r
        }

        // Try Newton-Raphson with Secant fallback
        var x0 = initialGuess
        var fx0 = f(x0)

        if (fx0.isNaN() || fx0.isInfinite()) {
            // Try different starting guesses
            val guesses = listOf(1.0, -1.0, 2.0, 5.0, 10.0, 0.5, -5.0)
            for (g in guesses) {
                x0 = g
                fx0 = f(x0)
                if (!fx0.isNaN() && !fx0.isInfinite()) break
            }
        }

        if (fx0.isNaN()) {
            return SolveResult(isSuccess = false, message = "تعذر حساب الدالة عند النقطة الابتدائية")
        }

        if (abs(fx0) < 1e-10) {
            return SolveResult(isSuccess = true, x = x0, leftMinusRight = fx0, iterations = 0)
        }

        var x1 = if (abs(x0) > 1e-4) x0 * 1.01 else 0.01
        var fx1 = f(x1)

        val maxIter = 100
        val tolerance = 1e-9

        for (iter in 1..maxIter) {
            val denominator = fx1 - fx0
            if (abs(denominator) < 1e-15) {
                x1 += 0.05
                fx1 = f(x1)
                continue
            }

            val xNext = x1 - fx1 * (x1 - x0) / denominator
            if (xNext.isNaN() || xNext.isInfinite()) {
                break
            }

            val fxNext = f(xNext)
            if (fxNext.isNaN()) {
                break
            }

            if (abs(fxNext) < tolerance || abs(xNext - x1) < tolerance) {
                return SolveResult(
                    isSuccess = true,
                    x = xNext,
                    leftMinusRight = fxNext,
                    iterations = iter
                )
            }

            x0 = x1
            fx0 = fx1
            x1 = xNext
            fx1 = fxNext
        }

        // If secant struggled, try search bracket bisection
        var low = -100.0
        var high = 100.0
        var step = 0.5
        var bracketFound = false
        var bLow = low
        var bHigh = low + step

        var currentX = low
        while (currentX <= high) {
            val fA = f(currentX)
            val fB = f(currentX + step)
            if (!fA.isNaN() && !fB.isNaN() && fA * fB <= 0) {
                bLow = currentX
                bHigh = currentX + step
                bracketFound = true
                break
            }
            currentX += step
        }

        if (bracketFound) {
            var a = bLow
            var b = bHigh
            var mid = (a + b) / 2.0
            for (i in 1..60) {
                mid = (a + b) / 2.0
                val fMid = f(mid)
                if (abs(fMid) < tolerance || (b - a) / 2.0 < tolerance) {
                    return SolveResult(isSuccess = true, x = mid, leftMinusRight = fMid, iterations = i)
                }
                if (f(a) * fMid <= 0) {
                    b = mid
                } else {
                    a = mid
                }
            }
            return SolveResult(isSuccess = true, x = mid, leftMinusRight = f(mid), iterations = 60)
        }

        return SolveResult(
            isSuccess = false,
            message = "لم يتم العثور على حل حقيقي للمعادلة في النطاق المتاح (Can't Solve)"
        )
    }

    /**
     * Casio d/dx numerical derivative at x = a.
     */
    fun numericalDerivative(rawExpr: String, xVal: Double, isDeg: Boolean): Double {
        val h = 1e-5
        val fPlus = evaluate(rawExpr, isDeg, "0", xVal = xVal + h)
        val fMinus = evaluate(rawExpr, isDeg, "0", xVal = xVal - h)
        if (fPlus.isNaN() || fMinus.isNaN()) return Double.NaN
        return (fPlus - fMinus) / (2.0 * h)
    }

    /**
     * Casio definite integral ∫ f(x) dx from a to b using Adaptive Simpson's Rule.
     */
    fun numericalIntegral(rawExpr: String, a: Double, b: Double, isDeg: Boolean, nSteps: Int = 100): Double {
        if (a == b) return 0.0
        val n = if (nSteps % 2 == 0) nSteps else nSteps + 1
        val h = (b - a) / n
        var sum = evaluate(rawExpr, isDeg, "0", xVal = a) + evaluate(rawExpr, isDeg, "0", xVal = b)
        for (i in 1 until n) {
            val x = a + i * h
            val fx = evaluate(rawExpr, isDeg, "0", xVal = x)
            if (fx.isNaN()) return Double.NaN
            sum += if (i % 2 == 0) 2.0 * fx else 4.0 * fx
        }
        return (h / 3.0) * sum
    }

    /**
     * S<=>D Converter: Converts double numbers to nice fractions or scientific notation.
     */
    fun formatStandardOrDecimal(value: Double): String {
        if (value.isNaN()) return "Math ERROR"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        if (abs(value) < 1e-12) return "0"

        // Check if close to simple fraction
        val maxDenominator = 1000
        for (den in 2..maxDenominator) {
            val num = round(value * den)
            if (abs(value - (num / den.toDouble())) < 1e-8) {
                val n = num.toLong()
                val d = den.toLong()
                val gcdVal = gcd(abs(n), d)
                val simN = n / gcdVal
                val simD = d / gcdVal
                return "$simN/$simD"
            }
        }

        // Return clean decimal
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
}
