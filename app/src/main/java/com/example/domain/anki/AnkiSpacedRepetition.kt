package com.example.domain.anki

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ReviewResult(
    val newRepetitionNumber: Int,
    val newIntervalDays: Int,
    val newEaseFactor: Float,
    val nextReviewTimestampMillis: Long,
    val newLapses: Int,
    val newMasteryPercentage: Int,
    val newState: String
)

object AnkiSpacedRepetition {

    /**
     * Parses Cloze text like "The capital of France is {{c1::Paris}}"
     * Returns a pair of (QuestionWithBlank, RevealedAnswer)
     */
    fun parseClozeCard(clozeText: String): Pair<String, String> {
        val regex = Regex("\\{\\{c\\d+::(.*?)\\}\\}")
        val matches = regex.findAll(clozeText).toList()
        if (matches.isEmpty()) {
            return clozeText to ""
        }
        val answers = matches.map { it.groupValues[1] }.joinToString(", ")
        val maskedText = regex.replace(clozeText, "[...]")
        return maskedText to answers
    }

    /**
     * Returns the full text with the clozed parts revealed.
     */
    fun revealClozeText(clozeText: String): String {
        val regex = Regex("\\{\\{c\\d+::(.*?)\\}\\}")
        return regex.replace(clozeText, "$1")
    }

    /**
     * Rating scale for Adaptive SM-2:
     * 1 = Again: Lapsed / Mistake -> Repeat very soon.
     * 2 = Hard: Recalled with difficulty -> Repeat with small interval increase, ease decreases.
     * 3 = Good: Normal recall -> Standard progression (interval * ease).
     * 4 = Easy: Immediate easy recall -> Faster progression (interval * ease * 1.3), ease increases.
     */
    fun calculateNextReview(
        grade: Int, // 1..4
        repetitionNumber: Int,
        intervalDays: Int,
        easeFactor: Float,
        currentLapses: Int,
        currentMastery: Int
    ): ReviewResult {
        var newRepetition = repetitionNumber
        var newInterval = intervalDays
        var newEase = easeFactor
        var newLapses = currentLapses
        var newState = "REVIEW"

        when (grade) {
            1 -> {
                newLapses += 1
                newRepetition = 0
                newInterval = 1 // Next day
                newEase = max(1.3f, newEase - 0.2f)
                newState = "LEARNING"
            }
            2 -> {
                newRepetition += 1
                newInterval = if (repetitionNumber == 0) {
                    2 
                } else {
                    max(2, (intervalDays * 1.2f).roundToInt())
                }
                newEase = max(1.3f, newEase - 0.15f)
                newState = "LEARNING"
            }
            3 -> {
                newRepetition += 1
                newInterval = when (repetitionNumber) {
                    0 -> 1
                    1 -> 4
                    else -> max((intervalDays * newEase).roundToInt(), intervalDays + 1)
                }
                newState = if (newInterval > 21) "MASTERED" else "REVIEW"
            }
            4 -> {
                newRepetition += 1
                newInterval = when (repetitionNumber) {
                    0 -> 4
                    1 -> 8
                    else -> max((intervalDays * newEase * 1.3f).roundToInt(), intervalDays + 3)
                }
                newEase = min(3.0f, newEase + 0.15f)
                newState = if (newInterval > 14) "MASTERED" else "REVIEW"
            }
            else -> {
                newRepetition += 1
                newInterval = max(1, intervalDays)
            }
        }

        newInterval = max(1, newInterval)
        val nextReviewMillis = System.currentTimeMillis() + (newInterval * 86400000L)

        // Calculate Mastery
        var calculatedMastery = 0f
        calculatedMastery += min(50f, newRepetition * 5f)
        calculatedMastery += min(30f, newInterval * 2f)
        calculatedMastery += min(20f, (newEase - 1.3f) * 11.7f)
        calculatedMastery -= min(calculatedMastery, newLapses * 10f)
        
        val finalMastery = calculatedMastery.roundToInt().coerceIn(0, 100)
        
        if (finalMastery >= 96) newState = "MASTERED"
        else if (finalMastery < 40 && newState == "MASTERED") newState = "REVIEW"

        return ReviewResult(
            newRepetitionNumber = newRepetition,
            newIntervalDays = newInterval,
            newEaseFactor = newEase,
            nextReviewTimestampMillis = nextReviewMillis,
            newLapses = newLapses,
            newMasteryPercentage = finalMastery,
            newState = newState
        )
    }

    /**
     * Preview human-readable interval text for rating buttons
     */
    fun previewIntervalText(
        grade: Int,
        repetitionNumber: Int,
        intervalDays: Int,
        easeFactor: Float,
        currentLapses: Int,
        currentMastery: Int,
        isArabic: Boolean = true
    ): String {
        val res = calculateNextReview(grade, repetitionNumber, intervalDays, easeFactor, currentLapses, currentMastery)
        val days = res.newIntervalDays
        return if (isArabic) {
            when {
                days == 1 -> "1 يوم"
                days == 2 -> "2 يوم"
                days in 3..10 -> "$days أيام"
                days in 11..30 -> "$days يوم"
                days in 31..60 -> "${days / 30} شهر"
                else -> "${days / 30} أشهر"
            }
        } else {
            when {
                days == 1 -> "1 day"
                days < 30 -> "$days days"
                else -> "${days / 30} mo"
            }
        }
    }
}
