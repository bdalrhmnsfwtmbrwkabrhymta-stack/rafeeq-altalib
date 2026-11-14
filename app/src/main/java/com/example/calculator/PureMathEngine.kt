package com.example.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Pure Kotlin Shunting-Yard Scientific Math Engine.
 * Supports:
 * - Implicit multiplication (e.g. 2π, 3(4+5), 2sin(30), 2²(3))
 * - Epsilon snapping at 1e-12 for trigonometric exactness (sin(30°)=0.5, cos(90°)=0)
 * - Continued fraction converter (S<->D & a b/c <-> d/c)
 * - DEG / RAD / GRAD angle modes
 */
object PureMathEngine {

    private const val EPSILON = 1e-12

    sealed class EvalOutcome {
        data class Success(val value: Double) : EvalOutcome()
        data class Failure(val messageAr: String) : EvalOutcome()
    }

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Var(val name: String) : Token()
        data class Func(val name: String) : Token()
        data class Op(val symbol: String) : Token()
        object LParen : Token()
        object RParen : Token()
        object PostfixFact : Token()
    }

    fun evaluate(
        rawExpression: String,
        angleUnit: AngleUnit = AngleUnit.DEG,
        ansValue: Double = 0.0,
        memoryValue: Double = 0.0,
        xValue: Double = 0.0
    ): EvalOutcome {
        val cleaned = normalizeExpression(rawExpression)
        if (cleaned.isBlank()) return EvalOutcome.Success(0.0)

        return try {
            val rawTokens = tokenize(cleaned)
            val withImplicit = insertImplicitMultiplication(rawTokens)
            val resolvedUnary = resolveUnaryOperators(withImplicit)
            val rpn = toRpn(resolvedUnary)
            val result = evaluateRpn(
                rpn = rpn,
                angleUnit = angleUnit,
                ansValue = ansValue,
                memoryValue = memoryValue,
                xValue = xValue
            )
            if (!result.isFinite()) {
                EvalOutcome.Failure("خطأ رياضي")
            } else {
                EvalOutcome.Success(snapEpsilon(result))
            }
        } catch (e: CalcSyntaxException) {
            EvalOutcome.Failure(e.message ?: "خطأ في الصيغة")
        } catch (e: CalcDomainException) {
            EvalOutcome.Failure(e.message ?: "خطأ رياضي")
        } catch (_: Exception) {
            EvalOutcome.Failure("خطأ في الصيغة")
        }
    }

    private class CalcSyntaxException(msg: String) : RuntimeException(msg)
    private class CalcDomainException(msg: String) : RuntimeException(msg)

    private fun normalizeExpression(input: String): String {
        return input
            .trim()
            .replace("×10^", "*10^")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("–", "-")
            .replace("³√", "cbrt")
            .replace("√", "sqrt")
            .replace("²", "^2")
            .replace("³", "^3")
            .replace("⁻¹", "^(-1)")
            .replace("sin⁻¹", "asin")
            .replace("cos⁻¹", "acos")
            .replace("tan⁻¹", "atan")
            .replace("sinh⁻¹", "asinh")
            .replace("cosh⁻¹", "acosh")
            .replace("tanh⁻¹", "atanh")
            .replace("Abs", "abs")
            .replace("π", "pi")
            .replace(",", "")
            .replace(" ", "")
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    var seenDot = (c == '.')
                    i++
                    while (i < expr.length) {
                        val ch = expr[i]
                        if (ch.isDigit()) {
                            i++
                        } else if (ch == '.' && !seenDot) {
                            seenDot = true
                            i++
                        } else if ((ch == 'E' || ch == 'e') && i + 1 < expr.length &&
                            (expr[i + 1].isDigit() || expr[i + 1] == '+' || expr[i + 1] == '-')
                        ) {
                            // Only treat 'e' as scientific exponent if followed by +/-digit or digit,
                            // NOT standalone Euler constant 'e' before operator or parens.
                            val next = expr[i + 1]
                            if (next == '+' || next == '-') {
                                if (i + 2 < expr.length && expr[i + 2].isDigit()) {
                                    i += 2
                                    while (i < expr.length && expr[i].isDigit()) i++
                                } else {
                                    break
                                }
                            } else {
                                i++
                                while (i < expr.length && expr[i].isDigit()) i++
                            }
                            break
                        } else {
                            break
                        }
                    }
                    val numStr = expr.substring(start, i)
                    val parsed = numStr.toDoubleOrNull()
                        ?: throw CalcSyntaxException("رقم غير صالح")
                    tokens.add(Token.Num(parsed))
                }

                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetter()) i++
                    val word = expr.substring(start, i)
                    val lower = word.lowercase(Locale.US)
                    when {
                        lower == "mod" -> tokens.add(Token.Op("mod"))
                        lower == "ans" -> tokens.add(Token.Var("ans"))
                        lower == "pi" -> tokens.add(Token.Var("pi"))
                        lower == "e" -> tokens.add(Token.Var("e"))
                        lower == "x" -> tokens.add(Token.Var("x"))
                        lower == "m" -> tokens.add(Token.Var("m"))
                        lower in KNOWN_FUNCTIONS -> tokens.add(Token.Func(lower))
                        else -> {
                            // Handle chained single-letter tokens like "2xe" -> "2*x*e" or "xpi"
                            var pos = 0
                            while (pos < lower.length) {
                                val rem = lower.substring(pos)
                                val matchedFunc = KNOWN_FUNCTIONS
                                    .sortedByDescending { it.length }
                                    .firstOrNull { rem.startsWith(it) }
                                when {
                                    matchedFunc != null -> {
                                        tokens.add(Token.Func(matchedFunc))
                                        pos += matchedFunc.length
                                    }
                                    rem.startsWith("ans") -> {
                                        tokens.add(Token.Var("ans"))
                                        pos += 3
                                    }
                                    rem.startsWith("pi") -> {
                                        tokens.add(Token.Var("pi"))
                                        pos += 2
                                    }
                                    rem.startsWith("mod") -> {
                                        tokens.add(Token.Op("mod"))
                                        pos += 3
                                    }
                                    rem[0] == 'x' || rem[0] == 'e' || rem[0] == 'm' -> {
                                        tokens.add(Token.Var(rem[0].toString()))
                                        pos += 1
                                    }
                                    else -> throw CalcSyntaxException("دالة غير معروفة: $word")
                                }
                            }
                        }
                    }
                }

                c == '(' -> {
                    tokens.add(Token.LParen)
                    i++
                }

                c == ')' -> {
                    tokens.add(Token.RParen)
                    i++
                }

                c == '!' -> {
                    tokens.add(Token.PostfixFact)
                    i++
                }

                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%' -> {
                    val opSymbol = if (c == '%') "mod" else c.toString()
                    tokens.add(Token.Op(opSymbol))
                    i++
                }

                else -> throw CalcSyntaxException("رمز غير مدعوم")
            }
        }
        return tokens
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens
        val out = ArrayList<Token>(tokens.size + 4)
        for (idx in tokens.indices) {
            val curr = tokens[idx]
            if (idx > 0) {
                val prev = tokens[idx - 1]
                val prevEndsValue = prev is Token.Num ||
                    prev is Token.Var ||
                    prev is Token.RParen ||
                    prev is Token.PostfixFact
                val currStartsValue = curr is Token.Num ||
                    curr is Token.Var ||
                    curr is Token.Func ||
                    curr is Token.LParen

                if (prevEndsValue && currStartsValue) {
                    out.add(Token.Op("*"))
                }
            }
            out.add(curr)
        }
        return out
    }

    private fun resolveUnaryOperators(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        for (idx in tokens.indices) {
            val tok = tokens[idx]
            if (tok is Token.Op && (tok.symbol == "-" || tok.symbol == "+")) {
                val prev = out.lastOrNull()
                val isUnary = prev == null || prev is Token.Op || prev is Token.LParen || prev is Token.Func
                if (isUnary) {
                    if (tok.symbol == "-") {
                        out.add(Token.Op("u-"))
                    }
                    // Unary '+' is a no-op
                    continue
                }
            }
            out.add(tok)
        }
        return out
    }

    private fun precedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/", "mod" -> 2
        "u-" -> 3
        "^" -> 4
        "!" -> 5
        else -> 0
    }

    private fun isRightAssociative(op: String): Boolean = (op == "^" || op == "u-")

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Num, is Token.Var -> output.add(token)
                is Token.Func -> stack.addLast(token)
                is Token.PostfixFact -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.last()
                        if (top is Token.Op && precedence(top.symbol) > precedence("!")) {
                            output.add(stack.removeLast())
                        } else {
                            break
                        }
                    }
                    output.add(token)
                }
                is Token.Op -> {
                    if (token.symbol == "u-") {
                        // Unary prefix negation binds to its right operand and must not pop preceding '^' (e.g. 10^-3)
                        stack.addLast(token)
                    } else {
                        val p1 = precedence(token.symbol)
                        while (stack.isNotEmpty()) {
                            val top = stack.last()
                            if (top is Token.Op) {
                                val p2 = precedence(top.symbol)
                                if ((!isRightAssociative(token.symbol) && p1 <= p2) ||
                                    (isRightAssociative(token.symbol) && p1 < p2)
                                ) {
                                    output.add(stack.removeLast())
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }
                        stack.addLast(token)
                    }
                }
                is Token.LParen -> stack.addLast(token)
                is Token.RParen -> {
                    var foundLParen = false
                    while (stack.isNotEmpty()) {
                        val top = stack.removeLast()
                        if (top is Token.LParen) {
                            foundLParen = true
                            break
                        } else {
                            output.add(top)
                        }
                    }
                    if (!foundLParen) {
                        throw CalcSyntaxException("الأقواس غير متطابقة")
                    }
                    if (stack.isNotEmpty() && stack.last() is Token.Func) {
                        output.add(stack.removeLast())
                    }
                }
            }
        }

        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            if (top is Token.LParen || top is Token.RParen) {
                throw CalcSyntaxException("الأقواس غير متطابقة")
            }
            output.add(top)
        }
        return output
    }

    private fun evaluateRpn(
        rpn: List<Token>,
        angleUnit: AngleUnit,
        ansValue: Double,
        memoryValue: Double,
        xValue: Double
    ): Double {
        val stack = ArrayDeque<Double>()

        for (token in rpn) {
            when (token) {
                is Token.Num -> stack.addLast(token.value)
                is Token.Var -> {
                    val v = when (token.name) {
                        "pi" -> PI
                        "e" -> kotlin.math.E
                        "ans" -> ansValue
                        "m" -> memoryValue
                        "x" -> xValue
                        else -> throw CalcSyntaxException("متغير غير معروف")
                    }
                    stack.addLast(v)
                }
                is Token.PostfixFact -> {
                    if (stack.isEmpty()) throw CalcSyntaxException("خطأ في الصيغة")
                    val a = stack.removeLast()
                    stack.addLast(factorial(a))
                }
                is Token.Op -> {
                    if (token.symbol == "u-") {
                        if (stack.isEmpty()) throw CalcSyntaxException("خطأ في الصيغة")
                        stack.addLast(-stack.removeLast())
                    } else {
                        if (stack.size < 2) throw CalcSyntaxException("خطأ في الصيغة")
                        val b = stack.removeLast()
                        val a = stack.removeLast()
                        val res = when (token.symbol) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> {
                                if (abs(b) == 0.0) throw CalcDomainException("خطأ: قسمة على صفر")
                                a / b
                            }
                            "mod" -> {
                                if (abs(b) == 0.0) throw CalcDomainException("خطأ: قسمة على صفر")
                                a % b
                            }
                            "^" -> {
                                val p = a.pow(b)
                                if (p.isNaN()) throw CalcDomainException("خطأ رياضي")
                                p
                            }
                            else -> throw CalcSyntaxException("عملية غير مدعومة")
                        }
                        stack.addLast(res)
                    }
                }
                is Token.Func -> {
                    if (stack.isEmpty()) throw CalcSyntaxException("خطأ في المعامل")
                    val arg = stack.removeLast()
                    stack.addLast(applyFunction(token.name, arg, angleUnit))
                }
                else -> Unit
            }
        }

        if (stack.size != 1) throw CalcSyntaxException("خطأ في الصيغة")
        return stack.last()
    }

    private fun applyFunction(name: String, arg: Double, angleUnit: AngleUnit): Double {
        return when (name) {
            "sin" -> snapEpsilon(sin(toRadians(arg, angleUnit)))
            "cos" -> snapEpsilon(cos(toRadians(arg, angleUnit)))
            "tan" -> {
                val rad = toRadians(arg, angleUnit)
                val c = cos(rad)
                if (abs(c) < EPSILON) throw CalcDomainException("خطأ رياضي: غير معرّف")
                snapEpsilon(tan(rad))
            }
            "asin" -> {
                val clamped = if (abs(arg) > 1.0 && abs(arg) <= 1.0 + EPSILON) arg.coerceIn(-1.0, 1.0) else arg
                if (clamped < -1.0 || clamped > 1.0) throw CalcDomainException("خطأ رياضي: خارج المجال")
                snapEpsilon(fromRadians(asin(clamped), angleUnit))
            }
            "acos" -> {
                val clamped = if (abs(arg) > 1.0 && abs(arg) <= 1.0 + EPSILON) arg.coerceIn(-1.0, 1.0) else arg
                if (clamped < -1.0 || clamped > 1.0) throw CalcDomainException("خطأ رياضي: خارج المجال")
                snapEpsilon(fromRadians(acos(clamped), angleUnit))
            }
            "atan" -> snapEpsilon(fromRadians(atan(arg), angleUnit))
            "sinh" -> snapEpsilon(sinh(arg))
            "cosh" -> snapEpsilon(cosh(arg))
            "tanh" -> snapEpsilon(tanh(arg))
            "asinh" -> snapEpsilon(asinh(arg))
            "acosh" -> {
                if (arg < 1.0) throw CalcDomainException("خطأ رياضي: خارج المجال")
                snapEpsilon(acosh(arg))
            }
            "atanh" -> {
                if (arg <= -1.0 || arg >= 1.0) throw CalcDomainException("خطأ رياضي: خارج المجال")
                snapEpsilon(atanh(arg))
            }
            "log" -> {
                if (arg <= 0.0) throw CalcDomainException("خطأ رياضي: لوغاريتم غير موجب")
                snapEpsilon(log10(arg))
            }
            "ln" -> {
                if (arg <= 0.0) throw CalcDomainException("خطأ رياضي: لوغاريتم غير موجب")
                snapEpsilon(ln(arg))
            }
            "sqrt" -> {
                val s = if (arg < 0.0 && arg > -EPSILON) 0.0 else arg
                if (s < 0.0) throw CalcDomainException("خطأ رياضي: جذر سالب")
                snapEpsilon(sqrt(s))
            }
            "cbrt" -> snapEpsilon(cbrt(arg))
            "abs" -> snapEpsilon(abs(arg))
            "exp" -> snapEpsilon(exp(arg))
            "floor" -> floor(arg)
            "ceil" -> ceil(arg)
            else -> throw CalcSyntaxException("دالة غير معروفة")
        }
    }

    private fun factorial(n: Double): Double {
        val snapped = snapEpsilon(n)
        if (snapped < 0.0 || snapped != floor(snapped) || snapped > 170.0) {
            throw CalcDomainException("خطأ رياضي: مضروب غير صالح")
        }
        var acc = 1.0
        val limit = snapped.toInt()
        for (k in 2..limit) {
            acc *= k.toDouble()
        }
        return acc
    }

    fun toRadians(value: Double, unit: AngleUnit): Double = when (unit) {
        AngleUnit.DEG -> value * PI / 180.0
        AngleUnit.RAD -> value
        AngleUnit.GRAD -> value * PI / 200.0
    }

    fun fromRadians(rad: Double, unit: AngleUnit): Double = when (unit) {
        AngleUnit.DEG -> rad * 180.0 / PI
        AngleUnit.RAD -> rad
        AngleUnit.GRAD -> rad * 200.0 / PI
    }

    fun snapEpsilon(value: Double): Double {
        if (!value.isFinite()) return value
        if (abs(value) < EPSILON) return 0.0
        val nearestInt = round(value)
        if (abs(value - nearestInt) < EPSILON) return nearestInt
        if (abs(value) < 1e9) {
            val clean12 = BigDecimal.valueOf(value)
                .setScale(12, RoundingMode.HALF_UP)
                .toDouble()
            if (abs(value - clean12) < 1e-14) return clean12
        }
        return value
    }

    /**
     * Continued-fraction rational approximation (S<->D & a b/c).
     */
    fun toFraction(value: Double, maxDenominator: Long = 1_000_000L): FractionResult? {
        if (!value.isFinite()) return null
        val snapped = snapEpsilon(value)
        if (abs(snapped) > 1e9) return null

        val sign = if (snapped < 0) -1L else 1L
        val x = abs(snapped)
        val nearest = round(x)
        if (abs(x - nearest) < 1e-10) {
            return FractionResult(sign * nearest.toLong(), 1L)
        }

        var h1 = 1L
        var h2 = 0L
        var k1 = 0L
        var k2 = 1L
        var b = x

        for (iter in 0 until 32) {
            val a = floor(b).toLong()
            val auxH = h1
            val nextH = a * h1 + h2
            h2 = auxH

            val auxK = k1
            val nextK = a * k1 + k2
            k2 = auxK

            if (nextK > maxDenominator) break
            h1 = nextH
            k1 = nextK

            val frac = b - a
            if (frac < 1e-10) break
            val approx = h1.toDouble() / k1.toDouble()
            if (abs(x - approx) < 1e-10) break
            b = 1.0 / frac
        }

        if (k1 <= 0L) return null
        val approx = h1.toDouble() / k1.toDouble()
        if (abs(x - approx) > 1e-7) return null

        val g = gcd(h1, k1)
        return FractionResult(sign * (h1 / g), k1 / g)
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0L) 1L else x
    }

    fun formatNumber(
        value: Double,
        decimalPlaces: Int = 8,
        notationMode: CalcNotationMode = CalcNotationMode.NORM
    ): String {
        if (!value.isFinite()) return "خطأ رياضي"
        val snapped = snapEpsilon(value)
        if (snapped == 0.0) return "0"

        val places = decimalPlaces.coerceIn(0, 10)
        val absVal = abs(snapped)

        if (notationMode == CalcNotationMode.SCI || absVal >= 1e12 || (absVal > 0.0 && absVal < 1e-9)) {
            val pattern = if (places == 0) "0E0" else "0.${"#".repeat(places)}E0"
            val df = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols(Locale.US))
            return df.format(snapped).replace("E", "×10^")
        }

        val bd = BigDecimal.valueOf(snapped)
            .setScale(places, RoundingMode.HALF_UP)
            .stripTrailingZeros()

        val plain = bd.toPlainString()
        return if (plain == "-0") "0" else plain
    }

    private val KNOWN_FUNCTIONS = setOf(
        "asinh", "acosh", "atanh",
        "asin", "acos", "atan",
        "sinh", "cosh", "tanh",
        "sqrt", "cbrt", "floor", "ceil",
        "sin", "cos", "tan", "log", "ln", "abs", "exp"
    )
}
