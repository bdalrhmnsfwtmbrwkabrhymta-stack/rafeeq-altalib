package com.example.studytools.inheritance

import kotlin.math.abs

enum class DeceasedGender(val labelAr: String) {
    MALE("راجل"),
    FEMALE("ست")
}

data class InheritanceInput(
    val deceasedGender: DeceasedGender = DeceasedGender.MALE,
    val wivesCount: Int = 0, // 0..4 (only when deceased is MALE)
    val hasHusband: Boolean = false, // only when deceased is FEMALE
    val hasMother: Boolean = false,
    val hasFather: Boolean = false,
    val hasMaternalGrandmother: Boolean = false,
    val hasPaternalGrandfather: Boolean = false,
    val sonsCount: Int = 0,
    val daughtersCount: Int = 1,
    val fullBrothersCount: Int = 0,
    val fullSistersCount: Int = 0,
    val maternalSiblingsCount: Int = 0,
    val estateAmount: Double? = null
)

data class Rational(val num: Long, val den: Long) {
    val reduced: Rational
        get() {
            if (num == 0L) return Rational(0L, 1L)
            val g = gcd(abs(num), abs(den))
            val sign = if (den < 0L) -1L else 1L
            return Rational(sign * num / g, abs(den) / g)
        }

    operator fun plus(other: Rational): Rational {
        val d = lcm(den, other.den)
        return Rational(num * (d / den) + other.num * (d / other.den), d).reduced
    }

    operator fun minus(other: Rational): Rational {
        val d = lcm(den, other.den)
        return Rational(num * (d / den) - other.num * (d / other.den), d).reduced
    }

    operator fun times(other: Rational): Rational =
        Rational(num * other.num, den * other.den).reduced

    operator fun div(other: Rational): Rational =
        Rational(num * other.den, den * other.num).reduced

    fun toDouble(): Double = if (den == 0L) 0.0 else num.toDouble() / den.toDouble()

    fun formatFraction(): String {
        val r = reduced
        return if (r.den == 1L) "${r.num}" else "${r.num}/${r.den}"
    }

    companion object {
        val ZERO = Rational(0L, 1L)
        val ONE = Rational(1L, 1L)

        fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return if (x == 0L) 1L else x
        }

        fun lcm(a: Long, b: Long): Long {
            if (a == 0L || b == 0L) return 1L
            return (a / gcd(a, b)) * b
        }
    }
}

data class HeirShareResult(
    val heirTitle: String,
    val count: Int,
    val fraction: Rational,
    val percentage: Double,
    val moneyPerGroup: Double?,
    val moneyPerPerson: Double?,
    val shareTypeLabel: String,
    val shariaReason: String,
    val isExcluded: Boolean = false
)

data class InheritanceResult(
    val shares: List<HeirShareResult>,
    val excludedHeirs: List<HeirShareResult>,
    val baseAslAlMasala: Long,
    val finalDenominator: Long,
    val isAwl: Boolean,
    val isRadd: Boolean,
    val isUmariyyah: Boolean,
    val summaryNoteAr: String
)

object InheritanceCalculatorEngine {

    private data class DraftShare(
        val id: String,
        val title: String,
        val count: Int,
        var fraction: Rational,
        val isSpouse: Boolean,
        val shareTypeLabel: String,
        val reason: String
    )

    fun calculate(input: InheritanceInput): InheritanceResult {
        val sons = input.sonsCount.coerceAtLeast(0)
        val daughters = input.daughtersCount.coerceAtLeast(0)
        val hasDescendants = (sons + daughters) > 0
        val hasMaleDescendant = sons > 0

        val wives = if (input.deceasedGender == DeceasedGender.MALE) input.wivesCount.coerceIn(0, 4) else 0
        val husband = input.deceasedGender == DeceasedGender.FEMALE && input.hasHusband
        val hasSpouse = wives > 0 || husband

        val totalSiblings = input.fullBrothersCount.coerceAtLeast(0) +
            input.fullSistersCount.coerceAtLeast(0) +
            input.maternalSiblingsCount.coerceAtLeast(0)

        val drafts = mutableListOf<DraftShare>()
        val excluded = mutableListOf<HeirShareResult>()

        // 1. Spouse
        if (husband) {
            val f = if (hasDescendants) Rational(1, 4) else Rational(1, 2)
            val reason = if (hasDescendants) "الربع لوجود الفرع الوارث (الآية ١٢ النساء)" else "النصف لعدم وجود الفرع الوارث (الآية ١٢ النساء)"
            drafts.add(DraftShare("husband", "الزوج", 1, f, true, "فرض (${f.formatFraction()})", reason))
        } else if (wives > 0) {
            val f = if (hasDescendants) Rational(1, 8) else Rational(1, 4)
            val reason = if (hasDescendants) "الثمن يشترك فيه الزوجات لوجود الفرع الوارث" else "الربع يشترك فيه الزوجات لعدم وجود الفرع الوارث"
            val label = if (wives == 1) "الزوجة" else "الزوجات ($wives)"
            drafts.add(DraftShare("wife", label, wives, f, true, "فرض (${f.formatFraction()})", reason))
        }

        // 2. Mother & Maternal Grandmother
        val isUmariyyah = hasSpouse && input.hasMother && input.hasFather && !hasDescendants && totalSiblings < 2
        if (input.hasMother) {
            val motherFrac = when {
                hasDescendants || totalSiblings >= 2 -> Rational(1, 6)
                isUmariyyah -> {
                    val spouseFrac = drafts.first { it.isSpouse }.fraction
                    (Rational.ONE - spouseFrac) * Rational(1, 3)
                }
                else -> Rational(1, 3)
            }
            val reason = when {
                hasDescendants -> "السدس لوجود الفرع الوارث (الآية ١١ النساء)"
                totalSiblings >= 2 -> "السدس لوجود جمع من الإخوة (الآية ١١ النساء)"
                isUmariyyah -> "ثلث الباقي بعد نصيب الزوج/الزوجة (المسألة العمرية)"
                else -> "الثلث لعدم وجود فرع وارث ولا جمع من الإخوة"
            }
            drafts.add(DraftShare("mother", "الأم", 1, motherFrac, false, "فرض (${motherFrac.formatFraction()})", reason))
        }

        if (input.hasMaternalGrandmother) {
            if (input.hasMother) {
                excluded.add(
                    HeirShareResult(
                        heirTitle = "أم الأم (الجدة)",
                        count = 1,
                        fraction = Rational.ZERO,
                        percentage = 0.0,
                        moneyPerGroup = input.estateAmount?.let { 0.0 },
                        moneyPerPerson = input.estateAmount?.let { 0.0 },
                        shareTypeLabel = "محجوبة",
                        shariaReason = "محجوبة حجب حرمان لوجود الأم المباشرة",
                        isExcluded = true
                    )
                )
            } else {
                drafts.add(
                    DraftShare(
                        "m_grandmother",
                        "أم الأم (الجدة)",
                        1,
                        Rational(1, 6),
                        false,
                        "فرض (1/6)",
                        "السدس فرضاً عند عدم وجود الأم"
                    )
                )
            }
        }

        // 3. Father & Paternal Grandfather
        var fatherTakesAsabah = false
        var grandfatherTakesAsabah = false

        if (input.hasFather) {
            when {
                hasMaleDescendant -> {
                    drafts.add(
                        DraftShare(
                            "father",
                            "الأب",
                            1,
                            Rational(1, 6),
                            false,
                            "فرض (1/6)",
                            "السدس فرضاً فقط لوجود الفرع الوارث الذكر"
                        )
                    )
                }
                daughters > 0 -> {
                    fatherTakesAsabah = true
                    drafts.add(
                        DraftShare(
                            "father",
                            "الأب",
                            1,
                            Rational(1, 6),
                            false,
                            "فرض (1/6) + تعصيب",
                            "السدس فرضاً مع الباقي تعصيباً لوجود البنت دون ابن"
                        )
                    )
                }
                else -> {
                    fatherTakesAsabah = true
                    drafts.add(
                        DraftShare(
                            "father",
                            "الأب",
                            1,
                            Rational.ZERO,
                            false,
                            "تعصيب",
                            "يرث الباقي تعصيباً لعدم وجود الفرع الوارث"
                        )
                    )
                }
            }
        }

        if (input.hasPaternalGrandfather) {
            if (input.hasFather) {
                excluded.add(
                    HeirShareResult(
                        heirTitle = "أب الأب (الجد)",
                        count = 1,
                        fraction = Rational.ZERO,
                        percentage = 0.0,
                        moneyPerGroup = input.estateAmount?.let { 0.0 },
                        moneyPerPerson = input.estateAmount?.let { 0.0 },
                        shareTypeLabel = "محجوب",
                        shariaReason = "محجوب حجب حرمان لوجود الأب المباشر",
                        isExcluded = true
                    )
                )
            } else {
                when {
                    hasMaleDescendant -> {
                        drafts.add(
                            DraftShare(
                                "grandfather",
                                "أب الأب (الجد)",
                                1,
                                Rational(1, 6),
                                false,
                                "فرض (1/6)",
                                "يقوم مقام الأب فيأخذ السدس لوجود الابن"
                            )
                        )
                    }
                    daughters > 0 -> {
                        grandfatherTakesAsabah = true
                        drafts.add(
                            DraftShare(
                                "grandfather",
                                "أب الأب (الجد)",
                                1,
                                Rational(1, 6),
                                false,
                                "فرض (1/6) + تعصيب",
                                "السدس فرضاً + الباقي تعصيباً لعدم وجود الأب والابن"
                            )
                        )
                    }
                    else -> {
                        grandfatherTakesAsabah = true
                        drafts.add(
                            DraftShare(
                                "grandfather",
                                "أب الأب (الجد)",
                                1,
                                Rational.ZERO,
                                false,
                                "تعصيب",
                                "يرث تعصيباً عند غياب الأب والفرع الوارث"
                            )
                        )
                    }
                }
            }
        }

        // 4. Daughters (when no sons) & Sons + Daughters Asabah
        if (sons == 0 && daughters > 0) {
            val f = if (daughters == 1) Rational(1, 2) else Rational(2, 3)
            val label = if (daughters == 1) "البنت" else "البنات ($daughters)"
            val reason = if (daughters == 1) "النصف لانفرادها وعدم وجود المعصب (الابن)" else "الثلثان لتعددهن وعدم وجود المعصب"
            drafts.add(DraftShare("daughters", label, daughters, f, false, "فرض (${f.formatFraction()})", reason))
        }

        // 5. Maternal Siblings (excluded by descendants, father, or paternal grandfather)
        val matSibs = input.maternalSiblingsCount.coerceAtLeast(0)
        if (matSibs > 0) {
            if (hasDescendants || input.hasFather || input.hasPaternalGrandfather) {
                excluded.add(
                    HeirShareResult(
                        heirTitle = if (matSibs == 1) "أخ/أخت لأم" else "الإخوة لأم ($matSibs)",
                        count = matSibs,
                        fraction = Rational.ZERO,
                        percentage = 0.0,
                        moneyPerGroup = input.estateAmount?.let { 0.0 },
                        moneyPerPerson = input.estateAmount?.let { 0.0 },
                        shareTypeLabel = "محجوبون",
                        shariaReason = "الإخوة لأم يُحجبون بالفرع الوارث أو الأصل الوارث الذكر (الأب أو الجد)",
                        isExcluded = true
                    )
                )
            } else {
                val f = if (matSibs == 1) Rational(1, 6) else Rational(1, 3)
                val label = if (matSibs == 1) "أخ/أخت لأم" else "الإخوة لأم ($matSibs)"
                val reason = if (matSibs == 1) "السدس لانفراده وعدم وجود فرع أو أصل ذكر (الآية ١٢ النساء)" else "الثلث يتساوى فيه الذكور والإناث (الآية ١٢ النساء)"
                drafts.add(DraftShare("mat_sibs", label, matSibs, f, false, "فرض (${f.formatFraction()})", reason))
            }
        }

        // 6. Full Brothers & Full Sisters (excluded by Son, Father, or Paternal Grandfather per Hanafi/default simplicity)
        val fullBros = input.fullBrothersCount.coerceAtLeast(0)
        val fullSisters = input.fullSistersCount.coerceAtLeast(0)
        var sistersAsabahWithDaughters = false

        if (fullBros + fullSisters > 0) {
            if (hasMaleDescendant || input.hasFather || input.hasPaternalGrandfather) {
                val blocker = when {
                    hasMaleDescendant -> "الابن"
                    input.hasFather -> "الأب"
                    else -> "الجد الصحيح"
                }
                if (fullBros > 0) {
                    excluded.add(
                        HeirShareResult(
                            heirTitle = if (fullBros == 1) "أخ شقيق" else "الإخوة الأشقاء ($fullBros)",
                            count = fullBros,
                            fraction = Rational.ZERO,
                            percentage = 0.0,
                            moneyPerGroup = input.estateAmount?.let { 0.0 },
                            moneyPerPerson = input.estateAmount?.let { 0.0 },
                            shareTypeLabel = "محجوب",
                            shariaReason = "محجوبون لوجود $blocker",
                            isExcluded = true
                        )
                    )
                }
                if (fullSisters > 0) {
                    excluded.add(
                        HeirShareResult(
                            heirTitle = if (fullSisters == 1) "أخت شقيقة" else "الأخوات الشقيقات ($fullSisters)",
                            count = fullSisters,
                            fraction = Rational.ZERO,
                            percentage = 0.0,
                            moneyPerGroup = input.estateAmount?.let { 0.0 },
                            moneyPerPerson = input.estateAmount?.let { 0.0 },
                            shareTypeLabel = "محجوبة",
                            shariaReason = "محجوبات لوجود $blocker",
                            isExcluded = true
                        )
                    )
                }
            } else if (fullBros == 0 && daughters > 0) {
                // العصبة مع الغير: الأخوات الشقيقات مع البنات عصبة
                sistersAsabahWithDaughters = true
            } else if (fullBros == 0 && fullSisters > 0) {
                val f = if (fullSisters == 1) Rational(1, 2) else Rational(2, 3)
                val label = if (fullSisters == 1) "أخت شقيقة" else "الأخوات الشقيقات ($fullSisters)"
                val reason = if (fullSisters == 1) "النصف لانفرادها في الكلالة (الآية ١٧٦ النساء)" else "الثلثان لتعددهن في الكلالة (الآية ١٧٦ النساء)"
                drafts.add(DraftShare("full_sisters", label, fullSisters, f, false, "فرض (${f.formatFraction()})", reason))
            }
        }

        // Compute sum of fixed shares before Asabah
        var fixedSum = Rational.ZERO
        for (d in drafts) {
            fixedSum += d.fraction
        }

        val baseLcm = drafts.map { it.fraction.den }.filter { it > 0L }
            .fold(1L) { acc, den -> Rational.lcm(acc, den) }

        var isAwl = false
        var isRadd = false

        val remainder = if (fixedSum.num < fixedSum.den) Rational.ONE - fixedSum else Rational.ZERO

        // Allocate Ta'sib (Residue)
        var asabahAllocated = false
        if (remainder.num > 0L) {
            when {
                sons > 0 -> {
                    asabahAllocated = true
                    val totalParts = sons * 2L + daughters
                    val sonsFrac = (remainder * Rational(sons * 2L, totalParts)).reduced
                    val daughtersFrac = (remainder * Rational(daughters.toLong(), totalParts)).reduced
                    drafts.add(
                        DraftShare(
                            "sons",
                            if (sons == 1) "الابن" else "الأبناء ($sons)",
                            sons,
                            sonsFrac,
                            false,
                            "تعصيب",
                            if (daughters > 0) "يرث الباقي مع البنات (للذكر مثل حظ الأنثيين)" else "يرث الباقي تعصيباً بالنفس"
                        )
                    )
                    if (daughters > 0) {
                        drafts.add(
                            DraftShare(
                                "daughters_asabah",
                                if (daughters == 1) "البنت" else "البنات ($daughters)",
                                daughters,
                                daughtersFrac,
                                false,
                                "تعصيب بالغير",
                                "ترث الباقي مع الابن (للذكر مثل حظ الأنثيين)"
                            )
                        )
                    }
                }
                fatherTakesAsabah -> {
                    asabahAllocated = true
                    val fatherDraft = drafts.first { it.id == "father" }
                    fatherDraft.fraction = (fatherDraft.fraction + remainder).reduced
                }
                grandfatherTakesAsabah -> {
                    asabahAllocated = true
                    val gDraft = drafts.first { it.id == "grandfather" }
                    gDraft.fraction = (gDraft.fraction + remainder).reduced
                }
                sistersAsabahWithDaughters -> {
                    asabahAllocated = true
                    drafts.add(
                        DraftShare(
                            "full_sisters_asabah",
                            if (fullSisters == 1) "أخت شقيقة" else "الأخوات الشقيقات ($fullSisters)",
                            fullSisters,
                            remainder,
                            false,
                            "عصبة مع الغير",
                            "ترث الباقي بعد فرض البنات (العصبة مع الغير)"
                        )
                    )
                }
                fullBros > 0 && !hasMaleDescendant && !input.hasFather && !input.hasPaternalGrandfather -> {
                    asabahAllocated = true
                    val totalParts = fullBros * 2L + fullSisters
                    val brosFrac = (remainder * Rational(fullBros * 2L, totalParts)).reduced
                    drafts.add(
                        DraftShare(
                            "full_bros",
                            if (fullBros == 1) "أخ شقيق" else "الإخوة الأشقاء ($fullBros)",
                            fullBros,
                            brosFrac,
                            false,
                            "تعصيب",
                            if (fullSisters > 0) "يرث الباقي في الكلالة (للذكر مثل حظ الأنثيين)" else "يرث الباقي تعصيباً بالنفس"
                        )
                    )
                    if (fullSisters > 0) {
                        val sisFrac = (remainder * Rational(fullSisters.toLong(), totalParts)).reduced
                        drafts.add(
                            DraftShare(
                                "full_sisters_asabah",
                                if (fullSisters == 1) "أخت شقيقة" else "الأخوات الشقيقات ($fullSisters)",
                                fullSisters,
                                sisFrac,
                                false,
                                "تعصيب بالغير",
                                "ترث الباقي مع الأخ الشقيق (للذكر مثل حظ الأنثيين)"
                            )
                        )
                    }
                }
            }
        } else {
            // Fixed shares exhausted or exceeded 1: Ta'sib heirs receive 0 residue
            if (sons > 0) {
                drafts.add(DraftShare("sons", if (sons == 1) "الابن" else "الأبناء ($sons)", sons, Rational.ZERO, false, "عصبة (لم يبقَ فاضل)", "استغرقت الفروض التركة"))
            } else if (!fatherTakesAsabah && !grandfatherTakesAsabah && !hasMaleDescendant && !input.hasFather && !input.hasPaternalGrandfather && fullBros > 0) {
                excluded.add(
                    HeirShareResult(
                        heirTitle = if (fullBros == 1) "أخ شقيق" else "الإخوة الأشقاء ($fullBros)",
                        count = fullBros,
                        fraction = Rational.ZERO,
                        percentage = 0.0,
                        moneyPerGroup = input.estateAmount?.let { 0.0 },
                        moneyPerPerson = input.estateAmount?.let { 0.0 },
                        shareTypeLabel = "مستغرقة الفروض",
                        shariaReason = "لم يتبقَّ فاضل بعد أصحاب الفروض",
                        isExcluded = false
                    )
                )
            }
        }

        // Check 'Awl or Radd
        var totalFinalSum = Rational.ZERO
        for (d in drafts) totalFinalSum += d.fraction

        var finalDenom = baseLcm
        if (totalFinalSum.num > totalFinalSum.den && totalFinalSum.den > 0L) {
            // 'Awl (Proportional scaling down)
            isAwl = true
            val scale = totalFinalSum
            drafts.forEach { d ->
                d.fraction = (d.fraction / scale).reduced
            }
            finalDenom = (baseLcm * totalFinalSum.num) / totalFinalSum.den
        } else if (totalFinalSum.num > 0L && totalFinalSum.num < totalFinalSum.den && !asabahAllocated) {
            // Radd onto non-spouse Furud heirs proportionally
            val nonSpouseSum = drafts.filter { !it.isSpouse }.fold(Rational.ZERO) { acc, d -> acc + d.fraction }
            if (nonSpouseSum.num > 0L) {
                isRadd = true
                val spouseSum = drafts.filter { it.isSpouse }.fold(Rational.ZERO) { acc, d -> acc + d.fraction }
                val targetNonSpouseTotal = Rational.ONE - spouseSum
                drafts.filter { !it.isSpouse }.forEach { d ->
                    d.fraction = ((d.fraction / nonSpouseSum) * targetNonSpouseTotal).reduced
                }
            }
        }

        val finalLcm = drafts.map { it.fraction.den }.filter { it > 0L }
            .fold(1L) { acc, den -> Rational.lcm(acc, den) }

        val finalShares = drafts.map { d ->
            val pct = d.fraction.toDouble() * 100.0
            val groupMoney = input.estateAmount?.let { it * d.fraction.toDouble() }
            val perPersonMoney = if (groupMoney != null && d.count > 0) groupMoney / d.count.toDouble() else null
            val adjustedLabel = when {
                isAwl && !d.shareTypeLabel.contains("عول") -> "${d.shareTypeLabel} • عول"
                isRadd && !d.isSpouse -> "${d.shareTypeLabel} + رد"
                else -> d.shareTypeLabel
            }
            HeirShareResult(
                heirTitle = d.title,
                count = d.count,
                fraction = d.fraction,
                percentage = pct,
                moneyPerGroup = groupMoney,
                moneyPerPerson = perPersonMoney,
                shareTypeLabel = adjustedLabel,
                shariaReason = d.reason
            )
        }

        val summaryNote = when {
            finalShares.isEmpty() -> "يرجى اختيار وارث واحد على الأقل لحساب الأنصبة الشرعية."
            isUmariyyah -> "مسألة عمرية: تأخذ الأم ثلث الباقي بعد نصيب أحد الزوجين حتى لا تزيد على الأب."
            isAwl -> "عالت المسألة من أصل $baseLcm إلى $finalLcm لضمان دخول النقص على جميع أصحاب الفروض بالقسط."
            isRadd -> "مسألة رد: بعد أخذ ذوي الفروض فروضهم وبقاء فائض دون عاصب، يُرد الفائض على أصحاب الفروض غير الزوجين بنسبهم."
            else -> "توزيع عادل مطابق لأحكام المواريث في الشريعة الإسلامية (أصل المسألة: $finalLcm)."
        }

        return InheritanceResult(
            shares = finalShares,
            excludedHeirs = excluded,
            baseAslAlMasala = baseLcm,
            finalDenominator = finalLcm,
            isAwl = isAwl,
            isRadd = isRadd,
            isUmariyyah = isUmariyyah,
            summaryNoteAr = summaryNote
        )
    }
}
