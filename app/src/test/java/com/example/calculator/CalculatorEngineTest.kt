package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalculatorEngineTest {

    @Test
    fun `basic order of operations and superscripts match spec`() {
        val res = PureMathEngine.evaluate("2² + 3 × 4", AngleUnit.DEG)
        assertTrue(res is PureMathEngine.EvalOutcome.Success)
        assertEquals(16.0, (res as PureMathEngine.EvalOutcome.Success).value, 1e-12)
    }

    @Test
    fun `implicit multiplication works for pi parentheses and trig`() {
        val r1 = PureMathEngine.evaluate("2π", AngleUnit.RAD) as PureMathEngine.EvalOutcome.Success
        assertEquals(2.0 * Math.PI, r1.value, 1e-10)

        val r2 = PureMathEngine.evaluate("3(4+5)", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(27.0, r2.value, 1e-12)

        val r3 = PureMathEngine.evaluate("2sin(30)", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(1.0, r3.value, 1e-12)
    }

    @Test
    fun `epsilon snapping guarantees exact trig values`() {
        val sin30 = PureMathEngine.evaluate("sin(30)", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(0.5, sin30.value, 0.0)

        val cos90 = PureMathEngine.evaluate("cos(90)", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(0.0, cos90.value, 0.0)
    }

    @Test
    fun `cube root and negative power precedence evaluate accurately`() {
        val cbrt27 = PureMathEngine.evaluate("³√(27)", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(3.0, cbrt27.value, 1e-12)

        val negPow = PureMathEngine.evaluate("10^−3", AngleUnit.DEG) as PureMathEngine.EvalOutcome.Success
        assertEquals(0.001, negPow.value, 1e-12)
    }

    @Test
    fun `continued fraction conversion handles improper and mixed fractions`() {
        val frac1 = PureMathEngine.toFraction(1.5)
        assertNotNull(frac1)
        assertEquals("3/2", frac1!!.toImproperString())
        assertEquals("1 1/2", frac1.toMixedString())

        val frac2 = PureMathEngine.toFraction(0.333333333333)
        assertNotNull(frac2)
        assertEquals("1/3", frac2!!.toImproperString())
    }

    @Test
    fun `simpson composite rule definite integral matches x squared from 0 to 2`() {
        val res = AdvancedCalculusEngine.definiteIntegralSimpson("x^2", "0", "2", AngleUnit.DEG, 8)
        assertTrue(res is AdvancedCalculusEngine.ToolOutcome.Ok)
        val ok = res as AdvancedCalculusEngine.ToolOutcome.Ok
        assertEquals("∫ = 2.66666667", ok.primaryResult)
    }

    @Test
    fun `central difference derivative matches exact slope`() {
        val res = AdvancedCalculusEngine.derivativeAtPoint("x^3 + 2*x", "2", AngleUnit.DEG, 8)
        assertTrue(res is AdvancedCalculusEngine.ToolOutcome.Ok)
        val ok = res as AdvancedCalculusEngine.ToolOutcome.Ok
        assertTrue(ok.primaryResult.endsWith("= 14"))
    }

    @Test
    fun `matrix inverse and linear solver work accurately`() {
        val mat = arrayOf(doubleArrayOf(4.0, 7.0), doubleArrayOf(2.0, 6.0))
        val det = AdvancedCalculusEngine.matrixDeterminant(mat)
        assertEquals(10.0, det, 1e-12)

        val inv = AdvancedCalculusEngine.matrixInverse(mat)
        assertNotNull(inv)
        assertTrue(abs(inv!![0][0] - 0.6) < 1e-12)
        assertTrue(abs(inv[0][1] - (-0.7)) < 1e-12)
    }
}
