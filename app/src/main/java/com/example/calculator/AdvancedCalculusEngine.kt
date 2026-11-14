package com.example.calculator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin Numerical & Symbolic Algebra Engine for Advanced Calculus Tools:
 * 1. Composite Simpson's Rule definite integral (n = 1000)
 * 2. High-order central difference derivative
 * 3. Algebraic Equation Solver (Quadratic, Cubic, 2x2/3x3 Linear Systems, Newton-Raphson)
 * 4. Matrix Operations (2x2 & 3x3: +, -, *, det, transpose, inverse)
 * 5. Statistical Analysis (mean, sum, sumSq, s, sigma, median)
 * 6. Complex Arithmetic & Polar Conversion
 */
object AdvancedCalculusEngine {

    sealed class ToolOutcome {
        data class Ok(val primaryResult: String, val details: List<Pair<String, String>> = emptyList()) : ToolOutcome()
        data class Err(val message: String) : ToolOutcome()
    }

    private fun evalFn(expr: String, x: Double, angleUnit: AngleUnit): Double? {
        return when (val res = PureMathEngine.evaluate(expr, angleUnit = angleUnit, xValue = x)) {
            is PureMathEngine.EvalOutcome.Success -> res.value
            is PureMathEngine.EvalOutcome.Failure -> null
        }
    }

    // ----------------------------------------------------------
    // 1. Definite Integral via Composite Simpson's Rule (n = 1000)
    // ----------------------------------------------------------
    fun definiteIntegralSimpson(
        functionExpr: String,
        lowerStr: String,
        upperStr: String,
        angleUnit: AngleUnit,
        decimalPlaces: Int
    ): ToolOutcome {
        if (functionExpr.isBlank()) return ToolOutcome.Err("أدخل الدالة بدلالة x")
        val aVal = (PureMathEngine.evaluate(lowerStr, angleUnit) as? PureMathEngine.EvalOutcome.Success)?.value
            ?: return ToolOutcome.Err("الحد الأدنى غير صالح")
        val bVal = (PureMathEngine.evaluate(upperStr, angleUnit) as? PureMathEngine.EvalOutcome.Success)?.value
            ?: return ToolOutcome.Err("الحد الأعلى غير صالح")

        if (abs(aVal - bVal) < 1e-15) {
            return ToolOutcome.Ok("∫ = 0", listOf("الحدان متساويان" to "0"))
        }

        val sign = if (aVal > bVal) -1.0 else 1.0
        val a = minOf(aVal, bVal)
        val b = maxOf(aVal, bVal)
        val n = 1000 // Must be even for Composite Simpson's rule
        val h = (b - a) / n.toDouble()

        val fa = evalFn(functionExpr, a, angleUnit)
            ?: return ToolOutcome.Err("تعذر تقييم الدالة عند الحد الأدنى")
        val fb = evalFn(functionExpr, b, angleUnit)
            ?: return ToolOutcome.Err("تعذر تقييم الدالة عند الحد الأعلى")

        var sum = fa + fb
        for (i in 1 until n) {
            val xi = a + i * h
            val fi = evalFn(functionExpr, xi, angleUnit)
                ?: return ToolOutcome.Err("الدالة غير معرفة داخل فترة التكامل")
            sum += if (i % 2 == 0) 2.0 * fi else 4.0 * fi
        }

        val integral = PureMathEngine.snapEpsilon(sign * (h / 3.0) * sum)
        val formatted = PureMathEngine.formatNumber(integral, decimalPlaces.coerceAtLeast(8))
        return ToolOutcome.Ok(
            primaryResult = "∫ = $formatted",
            details = listOf(
                "طريقة الحساب" to "سيمبسون المركبة (n = 1000)",
                "الفترة" to "[${PureMathEngine.formatNumber(aVal, 4)}, ${PureMathEngine.formatNumber(bVal, 4)}]"
            )
        )
    }

    // ----------------------------------------------------------
    // 2. First Derivative via 5-Point Central Difference
    // ----------------------------------------------------------
    fun derivativeAtPoint(
        functionExpr: String,
        pointStr: String,
        angleUnit: AngleUnit,
        decimalPlaces: Int
    ): ToolOutcome {
        if (functionExpr.isBlank()) return ToolOutcome.Err("أدخل الدالة بدلالة x")
        val a = (PureMathEngine.evaluate(pointStr, angleUnit) as? PureMathEngine.EvalOutcome.Success)?.value
            ?: return ToolOutcome.Err("قيمة x غير صالحة")

        val h = 1e-4 * maxOf(1.0, abs(a))
        val fPlus2 = evalFn(functionExpr, a + 2 * h, angleUnit)
        val fPlus1 = evalFn(functionExpr, a + h, angleUnit)
        val fMinus1 = evalFn(functionExpr, a - h, angleUnit)
        val fMinus2 = evalFn(functionExpr, a - 2 * h, angleUnit)

        if (fPlus2 == null || fPlus1 == null || fMinus1 == null || fMinus2 == null) {
            return ToolOutcome.Err("الدالة غير قابلة للاشتقاق أو غير معرفة عند هذه النقطة")
        }

        val deriv = PureMathEngine.snapEpsilon((-fPlus2 + 8.0 * fPlus1 - 8.0 * fMinus1 + fMinus2) / (12.0 * h))
        val fx = evalFn(functionExpr, a, angleUnit)
        val formattedDeriv = PureMathEngine.formatNumber(deriv, decimalPlaces)
        val details = buildList {
            if (fx != null) add("f(${PureMathEngine.formatNumber(a, 4)})" to PureMathEngine.formatNumber(fx, decimalPlaces))
            add("الطريقة" to "الفروق المركزية خماسية النقاط")
        }
        return ToolOutcome.Ok("f'(${PureMathEngine.formatNumber(a, 4)}) = $formattedDeriv", details)
    }

    // ----------------------------------------------------------
    // 3. Equation Solver (Quadratic, Cubic, 2x2, 3x3, Newton-Raphson)
    // ----------------------------------------------------------
    fun solveQuadratic(a: Double, b: Double, c: Double, places: Int): ToolOutcome {
        if (abs(a) < 1e-12) {
            if (abs(b) < 1e-12) {
                return ToolOutcome.Err(if (abs(c) < 1e-12) "عدد لا نهائي من الحلول" else "لا يوجد حل")
            }
            val x = -c / b
            return ToolOutcome.Ok("x = ${PureMathEngine.formatNumber(x, places)}", listOf("النوع" to "معادلة خطية"))
        }
        val disc = PureMathEngine.snapEpsilon(b * b - 4.0 * a * c)
        return when {
            disc > 0.0 -> {
                val sqrtD = sqrt(disc)
                val x1 = (-b + sqrtD) / (2.0 * a)
                val x2 = (-b - sqrtD) / (2.0 * a)
                ToolOutcome.Ok(
                    primaryResult = "x₁ = ${PureMathEngine.formatNumber(x1, places)} , x₂ = ${PureMathEngine.formatNumber(x2, places)}",
                    details = listOf("المميز Δ" to PureMathEngine.formatNumber(disc, places), "نوع الجذور" to "جذران حقيقيان مختلفان")
                )
            }
            disc == 0.0 -> {
                val x = -b / (2.0 * a)
                ToolOutcome.Ok(
                    primaryResult = "x₁ = x₂ = ${PureMathEngine.formatNumber(x, places)}",
                    details = listOf("المميز Δ" to "0", "نوع الجذور" to "جذر حقيقي مكرر")
                )
            }
            else -> {
                val realPart = PureMathEngine.formatNumber(-b / (2.0 * a), places)
                val imagPart = PureMathEngine.formatNumber(sqrt(-disc) / abs(2.0 * a), places)
                ToolOutcome.Ok(
                    primaryResult = "x₁ = $realPart + ${imagPart}i , x₂ = $realPart - ${imagPart}i",
                    details = listOf("المميز Δ" to PureMathEngine.formatNumber(disc, places), "نوع الجذور" to "جذران مركبان مترافقان")
                )
            }
        }
    }

    fun solveCubic(a: Double, b: Double, c: Double, d: Double, places: Int): ToolOutcome {
        if (abs(a) < 1e-12) return solveQuadratic(b, c, d, places)
        val b1 = b / a
        val c1 = c / a
        val d1 = d / a

        val p = c1 - (b1 * b1) / 3.0
        val q = (2.0 * b1 * b1 * b1) / 27.0 - (b1 * c1) / 3.0 + d1
        val disc = (q * q) / 4.0 + (p * p * p) / 27.0
        val shift = -b1 / 3.0

        return if (disc > 1e-12) {
            val sqrtD = sqrt(disc)
            val u = cbrt(-q / 2.0 + sqrtD)
            val v = cbrt(-q / 2.0 - sqrtD)
            val x1 = u + v + shift
            val realPart = -(u + v) / 2.0 + shift
            val imagPart = abs((u - v) * sqrt(3.0) / 2.0)
            val r1 = PureMathEngine.formatNumber(x1, places)
            val rp = PureMathEngine.formatNumber(realPart, places)
            val ip = PureMathEngine.formatNumber(imagPart, places)
            ToolOutcome.Ok(
                primaryResult = "x₁ = $r1",
                details = listOf(
                    "x₁ (حقيقي)" to r1,
                    "x₂ (مركب)" to "$rp + ${ip}i",
                    "x₃ (مركب)" to "$rp - ${ip}i"
                )
            )
        } else if (abs(disc) <= 1e-12) {
            val roots = if (abs(q) < 1e-12) {
                listOf(shift, shift, shift)
            } else {
                val u = cbrt(-q / 2.0)
                listOf(2.0 * u + shift, -u + shift, -u + shift)
            }
            ToolOutcome.Ok(
                primaryResult = "x₁ = ${PureMathEngine.formatNumber(roots[0], places)} , x₂ = ${PureMathEngine.formatNumber(roots[1], places)}",
                details = listOf(
                    "x₁" to PureMathEngine.formatNumber(roots[0], places),
                    "x₂" to PureMathEngine.formatNumber(roots[1], places),
                    "x₃" to PureMathEngine.formatNumber(roots[2], places)
                )
            )
        } else {
            val r = sqrt(-(p * p * p) / 27.0)
            val phi = acos((-q / (2.0 * r)).coerceIn(-1.0, 1.0))
            val m = 2.0 * cbrt(r)
            val x1 = m * cos(phi / 3.0) + shift
            val x2 = m * cos((phi + 2.0 * PI) / 3.0) + shift
            val x3 = m * cos((phi + 4.0 * PI) / 3.0) + shift
            ToolOutcome.Ok(
                primaryResult = "x₁=${PureMathEngine.formatNumber(x1, 4)} , x₂=${PureMathEngine.formatNumber(x2, 4)} , x₃=${PureMathEngine.formatNumber(x3, 4)}",
                details = listOf(
                    "x₁" to PureMathEngine.formatNumber(x1, places),
                    "x₂" to PureMathEngine.formatNumber(x2, places),
                    "x₃" to PureMathEngine.formatNumber(x3, places)
                )
            )
        }
    }

    fun solveLinearSystem(augmented: Array<DoubleArray>, places: Int): ToolOutcome {
        val n = augmented.size
        val m = Array(n) { r -> augmented[r].clone() }

        for (col in 0 until n) {
            var pivotRow = col
            for (r in col + 1 until n) {
                if (abs(m[r][col]) > abs(m[pivotRow][col])) pivotRow = r
            }
            if (abs(m[pivotRow][col]) < 1e-12) {
                return ToolOutcome.Err("لا يوجد حل وحيد (المحدد يساوي صفراً)")
            }
            val tmp = m[col]
            m[col] = m[pivotRow]
            m[pivotRow] = tmp

            val pivot = m[col][col]
            for (j in col..n) m[col][j] /= pivot

            for (r in 0 until n) {
                if (r != col) {
                    val factor = m[r][col]
                    for (j in col..n) m[r][j] -= factor * m[col][j]
                }
            }
        }

        val labels = if (n == 2) listOf("x", "y") else listOf("x", "y", "z")
        val pairs = (0 until n).map { i -> labels[i] to PureMathEngine.formatNumber(m[i][n], places) }
        val primary = pairs.joinToString(" , ") { "${it.first} = ${it.second}" }
        return ToolOutcome.Ok(primary, pairs)
    }

    fun solveNewtonRaphson(
        functionExpr: String,
        initialGuess: Double,
        angleUnit: AngleUnit,
        places: Int
    ): ToolOutcome {
        if (functionExpr.isBlank()) return ToolOutcome.Err("أدخل معادلة f(x) = 0")
        var x = initialGuess
        val h = 1e-6

        for (iter in 1..100) {
            val fx = evalFn(functionExpr, x, angleUnit)
                ?: return ToolOutcome.Err("الدالة غير معرفة عند x = ${PureMathEngine.formatNumber(x, 4)}")
            if (abs(fx) < 1e-11) {
                val formatted = PureMathEngine.formatNumber(x, places)
                return ToolOutcome.Ok(
                    primaryResult = "x ≈ $formatted",
                    details = listOf(
                        "f(x)" to PureMathEngine.formatNumber(fx, places),
                        "عدد التكرارات" to "$iter"
                    )
                )
            }
            val fPlus = evalFn(functionExpr, x + h, angleUnit)
            val fMinus = evalFn(functionExpr, x - h, angleUnit)
            if (fPlus == null || fMinus == null) {
                return ToolOutcome.Err("تعذر حساب المشتقة أثناء التقريب")
            }
            val df = (fPlus - fMinus) / (2.0 * h)
            if (abs(df) < 1e-13) {
                x += 0.05 // Small perturbation to escape flat stationary point
                continue
            }
            val nextX = x - fx / df
            if (abs(nextX - x) < 1e-11) {
                val finalFx = evalFn(functionExpr, nextX, angleUnit) ?: fx
                return ToolOutcome.Ok(
                    primaryResult = "x ≈ ${PureMathEngine.formatNumber(nextX, places)}",
                    details = listOf(
                        "f(x)" to PureMathEngine.formatNumber(finalFx, places),
                        "عدد التكرارات" to "$iter"
                    )
                )
            }
            x = nextX
        }
        return ToolOutcome.Err("لم تتقارب الطريقة — جرّب تخميناً ابتدائياً أقرب للجذر")
    }

    // ----------------------------------------------------------
    // 4. Matrix Operations (2x2 & 3x3)
    // ----------------------------------------------------------
    fun matrixAdd(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { r -> DoubleArray(a[r].size) { c -> a[r][c] + b[r][c] } }

    fun matrixSub(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { r -> DoubleArray(a[r].size) { c -> a[r][c] - b[r][c] } }

    fun matrixMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val n = a.size
        return Array(n) { r ->
            DoubleArray(n) { c ->
                var s = 0.0
                for (k in 0 until n) s += a[r][k] * b[k][c]
                PureMathEngine.snapEpsilon(s)
            }
        }
    }

    fun matrixTranspose(a: Array<DoubleArray>): Array<DoubleArray> {
        val n = a.size
        return Array(n) { r -> DoubleArray(n) { c -> a[c][r] } }
    }

    fun matrixDeterminant(a: Array<DoubleArray>): Double {
        return if (a.size == 2) {
            PureMathEngine.snapEpsilon(a[0][0] * a[1][1] - a[0][1] * a[1][0])
        } else {
            val d = a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) -
                a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) +
                a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
            PureMathEngine.snapEpsilon(d)
        }
    }

    fun matrixInverse(a: Array<DoubleArray>): Array<DoubleArray>? {
        val n = a.size
        val aug = Array(n) { r ->
            DoubleArray(2 * n) { c ->
                when {
                    c < n -> a[r][c]
                    c - n == r -> 1.0
                    else -> 0.0
                }
            }
        }
        for (col in 0 until n) {
            var pivotRow = col
            for (r in col + 1 until n) {
                if (abs(aug[r][col]) > abs(aug[pivotRow][col])) pivotRow = r
            }
            if (abs(aug[pivotRow][col]) < 1e-12) return null
            val tmp = aug[col]
            aug[col] = aug[pivotRow]
            aug[pivotRow] = tmp

            val pivot = aug[col][col]
            for (j in 0 until 2 * n) aug[col][j] /= pivot
            for (r in 0 until n) {
                if (r != col) {
                    val factor = aug[r][col]
                    for (j in 0 until 2 * n) aug[r][j] -= factor * aug[col][j]
                }
            }
        }
        return Array(n) { r -> DoubleArray(n) { c -> PureMathEngine.snapEpsilon(aug[r][c + n]) } }
    }

    // ----------------------------------------------------------
    // 5. Statistical Analysis
    // ----------------------------------------------------------
    fun computeStats(rawInput: String): StatSummary? {
        val parts = rawInput
            .replace("،", ",")
            .replace(";", ",")
            .split(",", " ", "\n", "\t")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.isEmpty()) return null
        val values = parts.mapNotNull { it.toDoubleOrNull() }
        if (values.size != parts.size || values.isEmpty()) return null

        val n = values.size
        val sum = values.sum()
        val sumSq = values.sumOf { it * it }
        val mean = sum / n.toDouble()
        val sqDiff = values.sumOf { (it - mean).pow(2) }
        val popStd = sqrt(sqDiff / n.toDouble())
        val sampleStd = if (n >= 2) sqrt(sqDiff / (n - 1).toDouble()) else null

        val sorted = values.sorted()
        val median = if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }

        return StatSummary(
            count = n,
            sum = PureMathEngine.snapEpsilon(sum),
            sumSquares = PureMathEngine.snapEpsilon(sumSq),
            mean = PureMathEngine.snapEpsilon(mean),
            sampleStdDev = sampleStd?.let { PureMathEngine.snapEpsilon(it) },
            populationStdDev = PureMathEngine.snapEpsilon(popStd),
            median = PureMathEngine.snapEpsilon(median),
            min = sorted.first(),
            max = sorted.last()
        )
    }

    // ----------------------------------------------------------
    // 6. Complex Arithmetic & Polar Conversion
    // ----------------------------------------------------------
    fun addComplex(z1: ComplexNumber, z2: ComplexNumber) =
        ComplexNumber(PureMathEngine.snapEpsilon(z1.real + z2.real), PureMathEngine.snapEpsilon(z1.imag + z2.imag))

    fun subComplex(z1: ComplexNumber, z2: ComplexNumber) =
        ComplexNumber(PureMathEngine.snapEpsilon(z1.real - z2.real), PureMathEngine.snapEpsilon(z1.imag - z2.imag))

    fun mulComplex(z1: ComplexNumber, z2: ComplexNumber) = ComplexNumber(
        PureMathEngine.snapEpsilon(z1.real * z2.real - z1.imag * z2.imag),
        PureMathEngine.snapEpsilon(z1.real * z2.imag + z1.imag * z2.real)
    )

    fun divComplex(z1: ComplexNumber, z2: ComplexNumber): ComplexNumber? {
        val denom = z2.real * z2.real + z2.imag * z2.imag
        if (denom < 1e-15) return null
        return ComplexNumber(
            PureMathEngine.snapEpsilon((z1.real * z2.real + z1.imag * z2.imag) / denom),
            PureMathEngine.snapEpsilon((z1.imag * z2.real - z1.real * z2.imag) / denom)
        )
    }

    fun conjugate(z: ComplexNumber) = ComplexNumber(z.real, PureMathEngine.snapEpsilon(-z.imag))

    fun modulus(z: ComplexNumber): Double = PureMathEngine.snapEpsilon(sqrt(z.real * z.real + z.imag * z.imag))

    fun argument(z: ComplexNumber, unit: AngleUnit): Double {
        val rad = atan2(z.imag, z.real)
        return PureMathEngine.snapEpsilon(PureMathEngine.fromRadians(rad, unit))
    }

    fun fromPolar(r: Double, theta: Double, unit: AngleUnit): ComplexNumber {
        val rad = PureMathEngine.toRadians(theta, unit)
        return ComplexNumber(
            PureMathEngine.snapEpsilon(r * cos(rad)),
            PureMathEngine.snapEpsilon(r * sin(rad))
        )
    }

    fun formatComplex(z: ComplexNumber, places: Int): String {
        val r = PureMathEngine.snapEpsilon(z.real)
        val im = PureMathEngine.snapEpsilon(z.imag)
        val rStr = PureMathEngine.formatNumber(r, places)
        val absIm = abs(im)
        if (absIm == 0.0) return rStr
        val imStr = if (absIm == 1.0) "" else PureMathEngine.formatNumber(absIm, places)
        if (r == 0.0) {
            return if (im < 0) "-${imStr}i" else "${imStr}i"
        }
        val sign = if (im < 0) " - " else " + "
        return "$rStr$sign${imStr}i"
    }
}
