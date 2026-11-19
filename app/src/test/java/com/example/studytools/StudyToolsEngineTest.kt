package com.example.studytools

import com.example.studytools.inheritance.DeceasedGender
import com.example.studytools.inheritance.InheritanceCalculatorEngine
import com.example.studytools.inheritance.InheritanceInput
import com.example.studytools.inheritance.Rational
import com.example.studytools.irab.ArabicPosCategory
import com.example.studytools.irab.SmartIrabEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyToolsEngineTest {

    @Test
    fun inheritance_wifeSonTwoDaughters_splitsExactShariaShares() {
        val result = InheritanceCalculatorEngine.calculate(
            InheritanceInput(
                deceasedGender = DeceasedGender.MALE,
                wivesCount = 1,
                sonsCount = 1,
                daughtersCount = 2,
                estateAmount = 160_000.0
            )
        )

        assertFalse(result.isAwl)
        assertFalse(result.isRadd)
        val totalFraction = result.shares.fold(Rational.ZERO) { acc, item -> acc + item.fraction }
        assertEquals(Rational.ONE, totalFraction.reduced)
        val wife = result.shares.first { it.heirTitle.contains("الزوجة") }
        assertEquals(Rational(1, 8), wife.fraction.reduced)
        assertEquals(20_000.0, wife.moneyPerGroup ?: 0.0, 0.01)
    }

    @Test
    fun inheritance_awlCase_husbandAndTwoFullSisters() {
        val result = InheritanceCalculatorEngine.calculate(
            InheritanceInput(
                deceasedGender = DeceasedGender.FEMALE,
                hasHusband = true,
                daughtersCount = 0,
                fullSistersCount = 2
            )
        )

        assertTrue(result.isAwl)
        assertEquals(7L, result.finalDenominator)
        val totalFraction = result.shares.fold(Rational.ZERO) { acc, item -> acc + item.fraction }
        assertEquals(Rational.ONE, totalFraction.reduced)
    }

    @Test
    fun inheritance_umariyyahCase_husbandMotherFather() {
        val result = InheritanceCalculatorEngine.calculate(
            InheritanceInput(
                deceasedGender = DeceasedGender.FEMALE,
                hasHusband = true,
                hasMother = true,
                hasFather = true,
                daughtersCount = 0
            )
        )

        assertTrue(result.isUmariyyah)
        val mother = result.shares.first { it.heirTitle.contains("الأم") }
        assertEquals(Rational(1, 6), mother.fraction.reduced)
    }

    @Test
    fun inheritance_hajbCase_fatherBlocksGrandfatherAndFullBrother() {
        val result = InheritanceCalculatorEngine.calculate(
            InheritanceInput(
                deceasedGender = DeceasedGender.MALE,
                hasFather = true,
                hasPaternalGrandfather = true,
                fullBrothersCount = 1,
                daughtersCount = 0
            )
        )

        assertTrue(result.excludedHeirs.any { it.heirTitle.contains("الجد") })
        assertTrue(result.excludedHeirs.any { it.heirTitle.contains("أخ شقيق") })
    }

    @Test
    fun inheritance_raddCase_motherAndSingleDaughter() {
        val result = InheritanceCalculatorEngine.calculate(
            InheritanceInput(
                deceasedGender = DeceasedGender.MALE,
                hasMother = true,
                daughtersCount = 1
            )
        )

        assertTrue(result.isRadd)
        val totalFraction = result.shares.fold(Rational.ZERO) { acc, item -> acc + item.fraction }
        assertEquals(Rational.ONE, totalFraction.reduced)
    }

    @Test
    fun smartIrab_standardVerbalSentence_classifiesPosAndCases() {
        val analysis = SmartIrabEngine.analyzeOffline("يُذَاكِرُ الطَّالِبُ فِي الْمَكْتَبَةِ")
        assertEquals(4, analysis.tokens.size)
        assertEquals(ArabicPosCategory.VERB, analysis.tokens[0].category)
        assertEquals(ArabicPosCategory.NOUN, analysis.tokens[1].category)
        assertEquals(ArabicPosCategory.PARTICLE, analysis.tokens[2].category)
        assertEquals(ArabicPosCategory.NOUN, analysis.tokens[3].category)
        assertTrue(analysis.tokens[3].irabStatement.contains("مجرور"))
    }
}
