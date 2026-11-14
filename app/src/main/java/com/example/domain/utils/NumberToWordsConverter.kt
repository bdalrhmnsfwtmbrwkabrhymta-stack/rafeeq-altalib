package com.example.domain.utils

import java.util.Locale

object NumberToWordsConverter {

    private val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )

    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    private val unitsArabic = arrayOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر",
        "سبعة عشر", "ثمانية عشر", "تسعة عشر"
    )

    private val tensArabic = arrayOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"
    )

    fun convertToEnglish(number: Int): String {
        if (number == 0) return "Zero"
        if (number == 1_000_000) return "One Million"
        if (number < 0 || number > 1_000_000) return "Out of range (1 - 1,000,000)"

        return convertLessThanMillion(number).trim()
    }

    private fun convertLessThanMillion(number: Int): String {
        var current = number
        var result = ""

        if (current >= 1000) {
            val thousands = current / 1000
            result += convertLessThanThousand(thousands) + " Thousand "
            current %= 1000
        }

        if (current > 0) {
            result += convertLessThanThousand(current)
        }

        return result
    }

    private fun convertLessThanThousand(number: Int): String {
        var current = number
        var result = ""

        if (current >= 100) {
            result += units[current / 100] + " Hundred "
            current %= 100
        }

        if (current >= 20) {
            result += tens[current / 10]
            if (current % 10 != 0) {
                result += "-" + units[current % 10]
            }
            result += " "
        } else if (current > 0) {
            result += units[current] + " "
        }

        return result.trim()
    }

    fun convertToPhonetic(number: Int): String {
        // Approximate phonetic representation
        val english = convertToEnglish(number)
        return english.lowercase(Locale.ENGLISH)
            .replace("one", "وان")
            .replace("two", "تو")
            .replace("three", "ثري")
            .replace("four", "فور")
            .replace("five", "فايف")
            .replace("six", "سكس")
            .replace("seven", "سفن")
            .replace("eight", "أيت")
            .replace("nine", "ناين")
            .replace("ten", "تن")
            .replace("eleven", "إليفن")
            .replace("twelve", "تويلف")
            .replace("hundred", "هاندرد")
            .replace("thousand", "ثاوزند")
            .replace("million", "مليون")
    }

    fun convertToArabic(number: Int): String {
        if (number == 0) return "صفر"
        if (number == 1_000_000) return "مليون"
        if (number < 0 || number > 1_000_000) return "خارج النطاق"

        return convertArabicLessThanMillion(number).trim()
    }

    private fun convertArabicLessThanMillion(number: Int): String {
        var current = number
        val parts = mutableListOf<String>()

        if (current >= 1000) {
            val thousands = current / 1000
            val thStr = when (thousands) {
                1 -> "ألف"
                2 -> "ألفان"
                in 3..10 -> "${unitsArabic[thousands]} آلاف"
                else -> "${convertArabicLessThanThousand(thousands)} ألفاً"
            }
            parts.add(thStr)
            current %= 1000
        }

        if (current > 0) {
            parts.add(convertArabicLessThanThousand(current))
        }

        return parts.joinToString(" و ")
    }

    private fun convertArabicLessThanThousand(number: Int): String {
        var current = number
        val parts = mutableListOf<String>()

        if (current >= 100) {
            val hundreds = current / 100
            val hStr = when (hundreds) {
                1 -> "مائة"
                2 -> "مائتان"
                3 -> "ثلاثمائة"
                4 -> "أربعمائة"
                5 -> "خمسمائة"
                6 -> "ستمائة"
                7 -> "سبعمائة"
                8 -> "ثمانمائة"
                9 -> "تسعمائة"
                else -> ""
            }
            parts.add(hStr)
            current %= 100
        }

        if (current >= 20) {
            val tensVal = current / 10
            val unitVal = current % 10
            if (unitVal > 0) {
                parts.add("${unitsArabic[unitVal]} و ${tensArabic[tensVal]}")
            } else {
                parts.add(tensArabic[tensVal])
            }
        } else if (current > 0) {
            parts.add(unitsArabic[current])
        }

        return parts.joinToString(" و ")
    }
}
