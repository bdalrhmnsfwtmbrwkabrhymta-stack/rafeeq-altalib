package com.app.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class StageVisualPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val horizonGlow: Color,
    val trackTop: Color,
    val trackBottom: Color,
    val laneLineColor: Color,
    val railColor: Color,
    val gateFrameColors: List<Color>,
    val gateGlowColors: List<Color>,
    val hudBadgeBg: Color,
    val isDarkEnvironment: Boolean
)

object StageThemeEngine {

    val allStages: List<StageSpec> = listOf(
        StageSpec(
            number = 1,
            nameAr = "مكتبة البدايات",
            environmentSummaryAr = "قاعة المكتبة المشمسة، نوافذ دافئة، كتب ثلاثية الأبعاد عملاقة ومكاتب خشبية ومسار نيون أزرق",
            speedMultiplier = 1.00f,
            coinMultiplier = 1.0f,
            maxComboCap = 4,
            standardGatesCount = 6,
            gateStyle = GatePortalStyle.TECH_ARCH,
            bossChallengeTitleAr = "سؤال أساسي شامل • صندوق البدايات",
            chestNameAr = "صندوق البدايات الذهبي",
            bossRewardCoins = 120
        ),
        StageSpec(
            number = 2,
            nameAr = "معمل العلوم",
            environmentSummaryAr = "أرضية نيون زرقاء وسماوية، سلاسل DNA مزدوجة، ذرات بوهر دوّارة ودوارق كيميائية متوهجة",
            speedMultiplier = 1.12f,
            coinMultiplier = 1.4f,
            maxComboCap = 5,
            standardGatesCount = 6,
            gateStyle = GatePortalStyle.NEON_HOLO_FRAME,
            bossChallengeTitleAr = "سؤال معادلة/تجربة • صندوق المعمل",
            chestNameAr = "صندوق المعمل الفضي",
            bossRewardCoins = 220
        ),
        StageSpec(
            number = 3,
            nameAr = "مرصد الفضاء",
            environmentSummaryAr = "مسار زجاجي شفاف بين كوكب زحل بحلقاته والشمس المشرقة والسدم المجريّة وبوابات كونية مقوسة",
            speedMultiplier = 1.22f,
            coinMultiplier = 1.8f,
            maxComboCap = 6,
            standardGatesCount = 7,
            gateStyle = GatePortalStyle.COSMIC_PORTAL_ARCH,
            bossChallengeTitleAr = "سؤال استنتاجي • صندوق المجرة + مغناطيس",
            chestNameAr = "صندوق المجرة الكوني",
            bossRewardCoins = 320,
            bonusPowerUp = PowerUpType.COIN_MAGNET
        ),
        StageSpec(
            number = 4,
            nameAr = "قلعة العباقرة",
            environmentSummaryAr = "قاعة رخامية بيضاء فخمة، أقواس إسلامية مذهّبة، إسطرلابات معلقة وشلالات مياه جانبية",
            speedMultiplier = 1.32f,
            coinMultiplier = 2.2f,
            maxComboCap = 7,
            standardGatesCount = 7,
            gateStyle = GatePortalStyle.GOLDEN_ISLAMIC_ARCH,
            bossChallengeTitleAr = "سؤال قدرات عليا • صندوق القلعة الملكي + درع",
            chestNameAr = "صندوق القلعة الملكي",
            bossRewardCoins = 450,
            bonusPowerUp = PowerUpType.KNOWLEDGE_SHIELD
        ),
        StageSpec(
            number = 5,
            nameAr = "وادي الحضارات القديمة",
            environmentSummaryAr = "مسار رملي ذهبي وسط المعابد الفرعونية والمسلات والأعمدة الهيروغليفية المضيئة والأهرامات",
            speedMultiplier = 1.42f,
            coinMultiplier = 2.6f,
            maxComboCap = 8,
            standardGatesCount = 7,
            gateStyle = GatePortalStyle.PHARAONIC_PILLAR_GATE,
            bossChallengeTitleAr = "سؤال تاريخي/حضاري • صندوق الحضارات الأثري",
            chestNameAr = "صندوق الحضارات الأثري",
            bossRewardCoins = 600
        ),
        StageSpec(
            number = 6,
            nameAr = "مدينة الذكاء والروبوتات",
            environmentSummaryAr = "جسر إلكتروني متوهج وسط ناطحات سحاب بمعادلات هولوجرامية وقطارات مونوريل معلقة",
            speedMultiplier = 1.52f,
            coinMultiplier = 3.0f,
            maxComboCap = 9,
            standardGatesCount = 8,
            gateStyle = GatePortalStyle.NEON_HOLO_FRAME,
            bossChallengeTitleAr = "سؤال مسألة ذكية • صندوق المستقبل السيبراني",
            chestNameAr = "صندوق المستقبل السيبراني",
            bossRewardCoins = 800
        ),
        StageSpec(
            number = 7,
            nameAr = "قمة الأوائل",
            environmentSummaryAr = "جسر رخامي بدرابزين ذهبي فوق بحر السحاب أمام قمة الجبل الذهبي وكؤوس البطولة الطائرة",
            speedMultiplier = 1.62f,
            coinMultiplier = 3.5f,
            maxComboCap = 10,
            standardGatesCount = 8,
            gateStyle = GatePortalStyle.ROYAL_MARBLE_ARCH,
            bossChallengeTitleAr = "تحدي الزعيم المتقدم • صندوق الكنز الذهبي",
            chestNameAr = "صندوق الكنز الذهبي",
            bossRewardCoins = 1000
        ),
        StageSpec(
            number = 8,
            nameAr = "مفاعل الفيزياء الكمومية",
            environmentSummaryAr = "نفق مسرّع جسيمات بنفسجي وأزرق كهربائي وأنابيب بلازما برق وبوابات معادلات هولوجرامية",
            speedMultiplier = 1.72f,
            coinMultiplier = 4.0f,
            maxComboCap = 10,
            standardGatesCount = 8,
            gateStyle = GatePortalStyle.NEON_HOLO_FRAME,
            bossChallengeTitleAr = "سؤال قوانين ومعادلات • صندوق الكوانتم",
            chestNameAr = "صندوق الكوانتم المضيء",
            bossRewardCoins = 1250
        ),
        StageSpec(
            number = 9,
            nameAr = "غابة الأحياء المضيئة",
            environmentSummaryAr = "جسر خشبي زمردي مضيء وسط غابة استوائية تحلق فيها الخلايا الحية والميتوكوندريا المضيئة",
            speedMultiplier = 1.80f,
            coinMultiplier = 4.5f,
            maxComboCap = 11,
            standardGatesCount = 8,
            gateStyle = GatePortalStyle.COSMIC_PORTAL_ARCH,
            bossChallengeTitleAr = "سؤال أحياء وعلوم بيئية • صندوق الزمرد",
            chestNameAr = "صندوق الزمرد الحيوي",
            bossRewardCoins = 1500
        ),
        StageSpec(
            number = 10,
            nameAr = "أعماق المحيط",
            environmentSummaryAr = "نفق زجاجي تحت الماء تحيط به الشعاب المرجانية والفقاعات المتصاعدة وصناديق اللؤلؤ",
            speedMultiplier = 1.88f,
            coinMultiplier = 5.0f,
            maxComboCap = 11,
            standardGatesCount = 9,
            gateStyle = GatePortalStyle.TECH_ARCH,
            bossChallengeTitleAr = "سؤال جغرافيا وعلوم بحار • صندوق اللؤلؤ",
            chestNameAr = "صندوق اللؤلؤ المرجاني",
            bossRewardCoins = 1800
        ),
        StageSpec(
            number = 11,
            nameAr = "قصر اللغات والأدب",
            environmentSummaryAr = "سجادة ملكية حمراء مذهّبة داخل قاعة الأدب مع ثريات كريستال وريش كتابة وحروف ذهبية",
            speedMultiplier = 1.96f,
            coinMultiplier = 5.5f,
            maxComboCap = 12,
            standardGatesCount = 9,
            gateStyle = GatePortalStyle.GOLDEN_ISLAMIC_ARCH,
            bossChallengeTitleAr = "سؤال نحو وبلاغة ولغات • صندوق البيان الذهبي",
            chestNameAr = "صندوق البيان الذهبي",
            bossRewardCoins = 2100
        ),
        StageSpec(
            number = 12,
            nameAr = "كهف الكريستال والطاقة",
            environmentSummaryAr = "كهف بلوري عميق ببلورات الكوارتز البنفسجية والسماوية وكرات البلازما العائمة",
            speedMultiplier = 2.04f,
            coinMultiplier = 6.0f,
            maxComboCap = 12,
            standardGatesCount = 9,
            gateStyle = GatePortalStyle.COSMIC_PORTAL_ARCH,
            bossChallengeTitleAr = "تحدي زعيم الكريستال • صندوق الكريستال الملكي",
            chestNameAr = "صندوق الكريستال الملكي",
            bossRewardCoins = 2400
        ),
        StageSpec(
            number = 13,
            nameAr = "واحة الرياضيات والهندسة",
            environmentSummaryAr = "ميدان هندسي ثلاثي الأبعاد تحيط به المجسمات البلورية والمنحنيات الجيبية ومناشير الضوء",
            speedMultiplier = 2.10f,
            coinMultiplier = 6.5f,
            maxComboCap = 12,
            standardGatesCount = 9,
            gateStyle = GatePortalStyle.NEON_HOLO_FRAME,
            bossChallengeTitleAr = "تحدي التفاضل والهندسة • صندوق الخوارزمي",
            chestNameAr = "صندوق الخوارزمي الذهبي",
            bossRewardCoins = 2700,
            bonusPowerUp = PowerUpType.GATE_DETECTOR
        ),
        StageSpec(
            number = 14,
            nameAr = "بركان الكيمياء العضوية",
            environmentSummaryAr = "جسر تيتانيوم فوق أنهار الحمم البركانية المضيئة وحلقات البنزين السداسية المتوهجة",
            speedMultiplier = 2.16f,
            coinMultiplier = 7.0f,
            maxComboCap = 13,
            standardGatesCount = 10,
            gateStyle = GatePortalStyle.TECH_ARCH,
            bossChallengeTitleAr = "تحدي المعادلات العضوية • صندوق جابر بن حيان",
            chestNameAr = "صندوق الكيمياء الناري",
            bossRewardCoins = 3000
        ),
        StageSpec(
            number = 15,
            nameAr = "مدينة الإسكندرية القديمة",
            environmentSummaryAr = "جسر حجري ملكي يمتد نحو منارة الإسكندرية التاريخية وأعمدة المكتبة الرخامية على البحر",
            speedMultiplier = 2.22f,
            coinMultiplier = 7.5f,
            maxComboCap = 13,
            standardGatesCount = 10,
            gateStyle = GatePortalStyle.PHARAONIC_PILLAR_GATE,
            bossChallengeTitleAr = "تحدي التاريخ والتراث • صندوق منارة العلم",
            chestNameAr = "صندوق منارة الإسكندرية",
            bossRewardCoins = 3400
        ),
        StageSpec(
            number = 16,
            nameAr = "قبة الفلسفة والمنطق",
            environmentSummaryAr = "قاعة فلكية سماوية بموازين العدالة الذهبية وتروس المنطق والساعات الرملية المضيئة",
            speedMultiplier = 2.28f,
            coinMultiplier = 8.0f,
            maxComboCap = 14,
            standardGatesCount = 10,
            gateStyle = GatePortalStyle.GOLDEN_ISLAMIC_ARCH,
            bossChallengeTitleAr = "تحدي الاستنباط والمنطق • صندوق الحكمة الذهبي",
            chestNameAr = "صندوق الحكمة الفلسفي",
            bossRewardCoins = 3800
        ),
        StageSpec(
            number = 17,
            nameAr = "مختبر الأعصاب وعلم النفس",
            environmentSummaryAr = "شبكة سيالات عصبية مضيئة باللون الوردي والسماوي وموجات دماغية هولوجرامية نابضة",
            speedMultiplier = 2.34f,
            coinMultiplier = 8.5f,
            maxComboCap = 14,
            standardGatesCount = 10,
            gateStyle = GatePortalStyle.NEON_HOLO_FRAME,
            bossChallengeTitleAr = "تحدي الذكاءات المتعددة • صندوق العبقرية",
            chestNameAr = "صندوق العبقرية الذهنية",
            bossRewardCoins = 4200
        ),
        StageSpec(
            number = 18,
            nameAr = "محطة المدار الدولي",
            environmentSummaryAr = "ممر فضائي بألواح طاقة شمسية عملاقة يطل على كوكب الأرض المضيء والأقمار الصناعية",
            speedMultiplier = 2.40f,
            coinMultiplier = 9.0f,
            maxComboCap = 15,
            standardGatesCount = 11,
            gateStyle = GatePortalStyle.COSMIC_PORTAL_ARCH,
            bossChallengeTitleAr = "تحدي الفيزياء الفلكية • صندوق المدار الفضائي",
            chestNameAr = "صندوق المدار الدولي",
            bossRewardCoins = 4600
        ),
        StageSpec(
            number = 19,
            nameAr = "قلعة البلاغة والشعر",
            environmentSummaryAr = "ديوان ملكي بقناديل ذهبية ومخطوطات عربية مضيئة وأقواس مرصعة بالزمرد والياقوت",
            speedMultiplier = 2.46f,
            coinMultiplier = 9.5f,
            maxComboCap = 15,
            standardGatesCount = 11,
            gateStyle = GatePortalStyle.ROYAL_MARBLE_ARCH,
            bossChallengeTitleAr = "تحدي البيان والأدب • صندوق درة الضاد",
            chestNameAr = "صندوق درة الضاد الملكي",
            bossRewardCoins = 5000
        ),
        StageSpec(
            number = 20,
            nameAr = "عرش أسطورة الثانوية (طور لا نهائي)",
            environmentSummaryAr = "ممر الأبطال الذهبي الكوني فوق المجرات مع تيجان التفوق وبوابات الأسطورة اللانهائية",
            speedMultiplier = 2.55f,
            coinMultiplier = 10.0f,
            maxComboCap = 16,
            standardGatesCount = 12,
            gateStyle = GatePortalStyle.ROYAL_MARBLE_ARCH,
            bossChallengeTitleAr = "تحدي الزعيم الأسطوري • تاج أسطورة الثانوية",
            chestNameAr = "تاج «أسطورة الثانوية»",
            bossRewardCoins = 6000,
            isEndlessStage = true
        )
    )

    fun stageFor(number: Int): StageSpec =
        allStages.firstOrNull { it.number == number } ?: allStages.first()

    /**
     * Maps each stage (1..20) to its high-resolution 3D Pixar environment & track backdrop drawable.
     */
    fun backdropResForStage(stageNumber: Int): Int = when (stageNumber) {
        1 -> com.example.R.drawable.img_stage1_library_track_1795852358508
        2 -> com.example.R.drawable.img_stage2_classroom_track_1795852432544
        3 -> com.example.R.drawable.img_stage3_space_clean_track_1795852459072
        4 -> com.example.R.drawable.img_stage4_citadel_track_1795852372813
        5 -> com.example.R.drawable.img_stage5_ancient_track_1795852399924
        6 -> com.example.R.drawable.img_stage6_cybercity_track_1795852402794
        7 -> com.example.R.drawable.img_stage7_summit_track_1795852405272
        8 -> com.example.R.drawable.img_stage8_quantum_track_1795852405397
        9 -> com.example.R.drawable.img_stage2_sciencelab_track_1795852360837
        10 -> com.example.R.drawable.img_stage2_classroom_track_1795852432544
        11 -> com.example.R.drawable.img_stage4_citadel_track_1795852372813
        12 -> com.example.R.drawable.img_stage8_quantum_track_1795852405397
        13 -> com.example.R.drawable.img_stage6_cybercity_track_1795852402794
        14 -> com.example.R.drawable.img_stage2_sciencelab_track_1795852360837
        15 -> com.example.R.drawable.img_stage5_ancient_track_1795852399924
        16 -> com.example.R.drawable.img_stage4_citadel_track_1795852372813
        17 -> com.example.R.drawable.img_stage8_quantum_track_1795852405397
        18 -> com.example.R.drawable.img_stage3_space_clean_track_1795852459072
        19 -> com.example.R.drawable.img_stage1_library_track_1795852358508
        else -> com.example.R.drawable.img_stage7_summit_track_1795852405272
    }

    fun paletteFor(stageNumber: Int): StageVisualPalette = when (stageNumber) {
        1 -> StageVisualPalette(
            skyTop = Color(0xFFFFF4DC),
            skyBottom = Color(0xFFF6D9A8),
            horizonGlow = Color(0xFFFFFBEB),
            trackTop = Color(0xFF1E56B6),
            trackBottom = Color(0xFF1D4ED8),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFF38BDF8),
            gateFrameColors = listOf(Color(0xFF38BDF8), Color(0xFF38BDF8), Color(0xFF38BDF8)),
            gateGlowColors = listOf(Color(0xFF7DD3FC), Color(0xFF7DD3FC), Color(0xFF7DD3FC)),
            hudBadgeBg = Color(0xDD1E3A5F),
            isDarkEnvironment = false
        )
        2 -> StageVisualPalette(
            skyTop = Color(0xFF061530),
            skyBottom = Color(0xFF0D2E5E),
            horizonGlow = Color(0xFF22D3EE),
            trackTop = Color(0xFF132D82),
            trackBottom = Color(0xFF1B3EC4),
            laneLineColor = Color(0xFF22D3EE),
            railColor = Color(0xFF38BDF8),
            gateFrameColors = listOf(Color(0xFF22C55E), Color(0xFF38BDF8), Color(0xFFF59E0B)),
            gateGlowColors = listOf(Color(0xFF4ADE80), Color(0xFF67E8F9), Color(0xFFFCD34D)),
            hudBadgeBg = Color(0xDD0C2446),
            isDarkEnvironment = true
        )
        3 -> StageVisualPalette(
            skyTop = Color(0xFF030718),
            skyBottom = Color(0xFF121038),
            horizonGlow = Color(0xFF6366F1),
            trackTop = Color(0xAA0C4A6E),
            trackBottom = Color(0xCC0369A1),
            laneLineColor = Color(0xFF7DD3FC),
            railColor = Color(0xFF38BDF8),
            gateFrameColors = listOf(Color(0xFF818CF8), Color(0xFF38BDF8), Color(0xFFF59E0B)),
            gateGlowColors = listOf(Color(0xFFA5B4FC), Color(0xFF7DD3FC), Color(0xFFFDE68A)),
            hudBadgeBg = Color(0xDD0F172A),
            isDarkEnvironment = true
        )
        4 -> StageVisualPalette(
            skyTop = Color(0xFF60A5FA),
            skyBottom = Color(0xFFF3E8D2),
            horizonGlow = Color(0xFFFFFBEB),
            trackTop = Color(0xFFE7E2D8),
            trackBottom = Color(0xFFF8F6F2),
            laneLineColor = Color(0xFFD97706),
            railColor = Color(0xFFF59E0B),
            gateFrameColors = listOf(Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFB45309)),
            gateGlowColors = listOf(Color(0xFFFDE68A), Color(0xFFFEF08A), Color(0xFFFCD34D)),
            hudBadgeBg = Color(0xDD1E3A8A),
            isDarkEnvironment = false
        )
        5 -> StageVisualPalette(
            skyTop = Color(0xFF38BDF8),
            skyBottom = Color(0xFFFDE68A),
            horizonGlow = Color(0xFFFFF7ED),
            trackTop = Color(0xFFD6A86E),
            trackBottom = Color(0xFFE9C793),
            laneLineColor = Color(0xFFD97706),
            railColor = Color(0xFFF59E0B),
            gateFrameColors = listOf(Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFB45309)),
            gateGlowColors = listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFFBBF24)),
            hudBadgeBg = Color(0xDD3A2D54),
            isDarkEnvironment = false
        )
        6 -> StageVisualPalette(
            skyTop = Color(0xFF1E3A5F),
            skyBottom = Color(0xFF64748B),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xFF0A1628),
            trackBottom = Color(0xFF0F2342),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFF10B981),
            gateFrameColors = listOf(Color(0xFFA855F7), Color(0xFF22D3EE), Color(0xFFF59E0B)),
            gateGlowColors = listOf(Color(0xFFD8B4FE), Color(0xFF67E8F9), Color(0xFFFCD34D)),
            hudBadgeBg = Color(0xDD0F172A),
            isDarkEnvironment = true
        )
        7 -> StageVisualPalette(
            skyTop = Color(0xFF92400E),
            skyBottom = Color(0xFFFDE68A),
            horizonGlow = Color(0xFFFFFBEB),
            trackTop = Color(0xFFE7E5E4),
            trackBottom = Color(0xFFFAFAF9),
            laneLineColor = Color(0xFFD97706),
            railColor = Color(0xFFF59E0B),
            gateFrameColors = listOf(Color(0xFFC084FC), Color(0xFF38BDF8), Color(0xFFFACC15)),
            gateGlowColors = listOf(Color(0xFFE9D5FF), Color(0xFFBAE6FD), Color(0xFFFEF08A)),
            hudBadgeBg = Color(0xDD451A03),
            isDarkEnvironment = false
        )
        8 -> StageVisualPalette(
            skyTop = Color(0xFF13072E),
            skyBottom = Color(0xFF2E1065),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xFF1E1B4B),
            trackBottom = Color(0xFF312E81),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFFD946EF),
            gateFrameColors = listOf(Color(0xFFA855F7), Color(0xFF22D3EE), Color(0xFFC026D3)),
            gateGlowColors = listOf(Color(0xFFD8B4FE), Color(0xFF67E8F9), Color(0xFFF0ABFC)),
            hudBadgeBg = Color(0xDD1E1B4B),
            isDarkEnvironment = true
        )
        9 -> StageVisualPalette(
            skyTop = Color(0xFF042F2E),
            skyBottom = Color(0xFF0F766E),
            horizonGlow = Color(0xFF34D399),
            trackTop = Color(0xFF5B3A1E),
            trackBottom = Color(0xFF784E28),
            laneLineColor = Color(0xFF34D399),
            railColor = Color(0xFF10B981),
            gateFrameColors = listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF059669)),
            gateGlowColors = listOf(Color(0xFF6EE7B7), Color(0xFFA7F3D0), Color(0xFF34D399)),
            hudBadgeBg = Color(0xDD064E3B),
            isDarkEnvironment = true
        )
        10 -> StageVisualPalette(
            skyTop = Color(0xFF021F36),
            skyBottom = Color(0xFF0284C7),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xBB075985),
            trackBottom = Color(0xDD0369A1),
            laneLineColor = Color(0xFF7DD3FC),
            railColor = Color(0xFF22D3EE),
            gateFrameColors = listOf(Color(0xFF06B6D4), Color(0xFF38BDF8), Color(0xFF0EA5E9)),
            gateGlowColors = listOf(Color(0xFF67E8F9), Color(0xFFBAE6FD), Color(0xFF7DD3FC)),
            hudBadgeBg = Color(0xDD082F49),
            isDarkEnvironment = true
        )
        11, 19 -> StageVisualPalette(
            skyTop = Color(0xFF2D0612),
            skyBottom = Color(0xFF6B1226),
            horizonGlow = Color(0xFFF59E0B),
            trackTop = Color(0xFF7F1D1D),
            trackBottom = Color(0xFFB91C1C),
            laneLineColor = Color(0xFFFBBF24),
            railColor = Color(0xFFD97706),
            gateFrameColors = listOf(Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFB45309)),
            gateGlowColors = listOf(Color(0xFFFDE68A), Color(0xFFFEF08A), Color(0xFFFCD34D)),
            hudBadgeBg = Color(0xDD450A0A),
            isDarkEnvironment = true
        )
        13 -> StageVisualPalette(
            skyTop = Color(0xFF0F172A),
            skyBottom = Color(0xFF1E3A8A),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xFF172554),
            trackBottom = Color(0xFF1E40AF),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFFFACC15),
            gateFrameColors = listOf(Color(0xFF38BDF8), Color(0xFFFACC15), Color(0xFFA855F7)),
            gateGlowColors = listOf(Color(0xFFBAE6FD), Color(0xFFFEF08A), Color(0xFFE9D5FF)),
            hudBadgeBg = Color(0xDD172554),
            isDarkEnvironment = true
        )
        14 -> StageVisualPalette(
            skyTop = Color(0xFF270905),
            skyBottom = Color(0xFF7C2D12),
            horizonGlow = Color(0xFFF97316),
            trackTop = Color(0xFF1C1917),
            trackBottom = Color(0xFF292524),
            laneLineColor = Color(0xFFF97316),
            railColor = Color(0xFFFACC15),
            gateFrameColors = listOf(Color(0xFFF97316), Color(0xFFFACC15), Color(0xFFEF4444)),
            gateGlowColors = listOf(Color(0xFFFED7AA), Color(0xFFFEF08A), Color(0xFFFECACA)),
            hudBadgeBg = Color(0xDD431407),
            isDarkEnvironment = true
        )
        15 -> StageVisualPalette(
            skyTop = Color(0xFF0284C7),
            skyBottom = Color(0xFFFDE68A),
            horizonGlow = Color(0xFFFFFBEB),
            trackTop = Color(0xFFD6C7B2),
            trackBottom = Color(0xFFEDE4D7),
            laneLineColor = Color(0xFF0284C7),
            railColor = Color(0xFFD97706),
            gateFrameColors = listOf(Color(0xFFD97706), Color(0xFF0284C7), Color(0xFFB45309)),
            gateGlowColors = listOf(Color(0xFFFEF08A), Color(0xFFBAE6FD), Color(0xFFFDE68A)),
            hudBadgeBg = Color(0xDD0C4A6E),
            isDarkEnvironment = false
        )
        16 -> StageVisualPalette(
            skyTop = Color(0xFF1E1B4B),
            skyBottom = Color(0xFF4C1D95),
            horizonGlow = Color(0xFFFACC15),
            trackTop = Color(0xFF312E81),
            trackBottom = Color(0xFF1E1B4B),
            laneLineColor = Color(0xFFFACC15),
            railColor = Color(0xFFD97706),
            gateFrameColors = listOf(Color(0xFFFACC15), Color(0xFFC084FC), Color(0xFF38BDF8)),
            gateGlowColors = listOf(Color(0xFFFEF08A), Color(0xFFE9D5FF), Color(0xFFBAE6FD)),
            hudBadgeBg = Color(0xDD312E81),
            isDarkEnvironment = true
        )
        17 -> StageVisualPalette(
            skyTop = Color(0xFF1F0924),
            skyBottom = Color(0xFF500724),
            horizonGlow = Color(0xFFEC4899),
            trackTop = Color(0xFF1E1B4B),
            trackBottom = Color(0xFF3B0764),
            laneLineColor = Color(0xFFF472B6),
            railColor = Color(0xFF38BDF8),
            gateFrameColors = listOf(Color(0xFFEC4899), Color(0xFF38BDF8), Color(0xFFA855F7)),
            gateGlowColors = listOf(Color(0xFFFBCFE8), Color(0xFFBAE6FD), Color(0xFFE9D5FF)),
            hudBadgeBg = Color(0xDD500724),
            isDarkEnvironment = true
        )
        18 -> StageVisualPalette(
            skyTop = Color(0xFF020617),
            skyBottom = Color(0xFF0F172A),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xFF1E293B),
            trackBottom = Color(0xFF334155),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFFFACC15),
            gateFrameColors = listOf(Color(0xFF38BDF8), Color(0xFF22C55E), Color(0xFFFACC15)),
            gateGlowColors = listOf(Color(0xFFBAE6FD), Color(0xFF86EFAC), Color(0xFFFEF08A)),
            hudBadgeBg = Color(0xDD0F172A),
            isDarkEnvironment = true
        )
        20 -> StageVisualPalette(
            skyTop = Color(0xFF1E0B36),
            skyBottom = Color(0xFF78350F),
            horizonGlow = Color(0xFFFACC15),
            trackTop = Color(0xFF311042),
            trackBottom = Color(0xFF1E1B4B),
            laneLineColor = Color(0xFFFACC15),
            railColor = Color(0xFFF59E0B),
            gateFrameColors = listOf(Color(0xFFFACC15), Color(0xFF38BDF8), Color(0xFFC084FC)),
            gateGlowColors = listOf(Color(0xFFFEF08A), Color(0xFFBAE6FD), Color(0xFFE9D5FF)),
            hudBadgeBg = Color(0xDD451A03),
            isDarkEnvironment = true
        )
        else -> StageVisualPalette(
            skyTop = Color(0xFF0E0524),
            skyBottom = Color(0xFF3B0764),
            horizonGlow = Color(0xFF38BDF8),
            trackTop = Color(0xFF1E1B4B),
            trackBottom = Color(0xFF3B0764),
            laneLineColor = Color(0xFF38BDF8),
            railColor = Color(0xFFA855F7),
            gateFrameColors = listOf(Color(0xFFC084FC), Color(0xFF22D3EE), Color(0xFF38BDF8)),
            gateGlowColors = listOf(Color(0xFFE9D5FF), Color(0xFF67E8F9), Color(0xFFBAE6FD)),
            hudBadgeBg = Color(0xDD1E1038),
            isDarkEnvironment = true
        )
    }

    /**
     * Renders the rich, distinctive 3D-perspective background scenery for each of the 20 stages.
     */
    fun DrawScope.drawStageScenery(
        stageNumber: Int,
        palette: StageVisualPalette,
        scrollPhase: Float,
        timeSec: Float,
        horizonY: Float
    ) {
        val w = size.width
        val h = size.height

        // 1. Sky / Backdrop Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.skyTop, palette.skyBottom, palette.skyBottom.copy(alpha = 0.92f)),
                startY = 0f,
                endY = h
            )
        )

        // 2. Horizon Atmospheric Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.horizonGlow.copy(alpha = 0.60f), Color.Transparent),
                center = Offset(w * 0.5f, horizonY),
                radius = w * 0.78f
            ),
            radius = w * 0.78f,
            center = Offset(w * 0.5f, horizonY)
        )

        // 3. Stage-specific 3D architectural & environmental landmarks
        when (stageNumber) {
            1 -> drawLibraryScenery3D(w, h, horizonY, scrollPhase)
            2 -> drawScienceLabScenery3D(w, h, horizonY, scrollPhase, timeSec)
            3, 18 -> drawSpaceObservatoryScenery3D(w, h, horizonY, timeSec, isOrbitalStation = stageNumber == 18)
            4, 16 -> drawScholarsCitadelScenery3D(w, h, horizonY, scrollPhase, timeSec)
            5, 15 -> drawAncientCivilizationsScenery3D(w, h, horizonY, scrollPhase, timeSec, isAlexandria = stageNumber == 15)
            6, 13 -> drawCyberCityScenery3D(w, h, horizonY, scrollPhase, timeSec)
            7, 20 -> drawChampionsSummitScenery3D(w, h, horizonY, timeSec)
            8, 17 -> drawQuantumReactorScenery3D(w, h, horizonY, scrollPhase, timeSec)
            9 -> drawBiologyJungleScenery3D(w, h, horizonY, scrollPhase, timeSec)
            10 -> drawDeepOceanScenery3D(w, h, horizonY, scrollPhase, timeSec)
            11, 19 -> drawLiteraturePalaceScenery3D(w, h, horizonY, scrollPhase, timeSec)
            14 -> drawVolcanoChemistryScenery3D(w, h, horizonY, scrollPhase, timeSec)
            else -> drawCrystalCavernScenery3D(w, h, horizonY, scrollPhase, timeSec)
        }
    }

    /**
     * Stage 1 (Reference 03): Warm sunlit library hall, beamed ceiling, glowing windows, wooden floor planks,
     * giant colorful 3D book volumes with rounded spines & gold bands, and wooden study desks on left & right!
     */
    private fun DrawScope.drawLibraryScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float) {
        // Warm wooden plank floor under the neon track
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFC88A4B), Color(0xFF9C5E28)),
                startY = horizonY,
                endY = h
            ),
            topLeft = Offset(0f, horizonY),
            size = Size(w, h - horizonY)
        )
        // Perspective wooden floor lines
        for (i in -5..5) {
            drawLine(
                color = Color(0xFF78350F).copy(alpha = 0.28f),
                start = Offset(w * 0.5f + i * w * 0.06f, horizonY),
                end = Offset(w * 0.5f + i * w * 0.24f, h),
                strokeWidth = 2f
            )
        }

        // Ceiling wooden beams & sunlit library windows at top left/right
        for (b in 0..3) {
            val by = b * (horizonY * 0.22f)
            drawRect(
                color = Color(0xFF92400E).copy(alpha = 0.35f),
                topLeft = Offset(0f, by),
                size = Size(w, 12f)
            )
        }
        // Sunlit library windows
        for (side in listOf(0.03f, 0.85f)) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFDE68A).copy(alpha = 0.7f))),
                topLeft = Offset(w * side, horizonY * 0.14f),
                size = Size(w * 0.12f, horizonY * 0.55f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
        // Distant bookshelf wall at horizon
        drawRect(
            color = Color(0xFFB47B44),
            topLeft = Offset(w * 0.32f, horizonY - 68f),
            size = Size(w * 0.36f, 68f)
        )
        val shelfBookCols = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFF59E0B))
        for (row in 0..2) {
            val ry = horizonY - 60f + row * 20f
            for (col in 0..11) {
                drawRect(
                    color = shelfBookCols[(row + col) % shelfBookCols.size],
                    topLeft = Offset(w * 0.34f + col * (w * 0.026f), ry),
                    size = Size(w * 0.021f, 15f)
                )
            }
        }

        // Giant 3D Book Volumes & Wooden Desks scrolling along left & right walls (Reference 03)
        val bookColors = listOf(
            Color(0xFF4F46E5), // Purple-Blue
            Color(0xFF16A34A), // Green
            Color(0xFFDC2626), // Red
            Color(0xFFF59E0B), // Amber Gold
            Color(0xFF0284C7)  // Teal Blue
        )
        for (i in 0..4) {
            val z = ((i * 0.20f + scroll) % 1.0f)
            val y = horizonY + z * (h - horizonY) * 0.76f
            val scale = 0.32f + z * 0.90f
            val bookW = w * 0.125f * scale
            val bookH = h * 0.25f * scale

            // Left Giant 3D Book Spine + Gold Stripes
            val leftCol = bookColors[i % bookColors.size]
            val lx = w * 0.01f * (1f - z * 0.5f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(leftCol, leftCol.copy(red = (leftCol.red * 1.15f).coerceAtMost(1f)), leftCol.copy(alpha = 0.85f))
                ),
                topLeft = Offset(lx, y - bookH),
                size = Size(bookW, bookH),
                cornerRadius = CornerRadius(12f * scale, 12f * scale)
            )
            // Golden book spine bands
            drawRect(Color(0xFFFACC15), Offset(lx, y - bookH * 0.82f), Size(bookW, 9f * scale))
            drawRect(Color(0xFFFACC15), Offset(lx, y - bookH * 0.18f), Size(bookW, 7f * scale))

            // Right Giant 3D Book Spine
            val rightCol = bookColors[(i + 2) % bookColors.size]
            val rx = w - bookW - lx
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(rightCol.copy(alpha = 0.85f), rightCol)
                ),
                topLeft = Offset(rx, y - bookH),
                size = Size(bookW, bookH),
                cornerRadius = CornerRadius(12f * scale, 12f * scale)
            )
            drawRect(Color(0xFFFACC15), Offset(rx, y - bookH * 0.80f), Size(bookW, 9f * scale))

            // Wooden Study Desk with open white book in foreground
            if (i % 2 == 0) {
                val deskW = bookW * 1.15f
                val deskH = bookH * 0.44f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFB4692C), Color(0xFF7C3F11))),
                    topLeft = Offset(lx + bookW * 0.15f, y - deskH),
                    size = Size(deskW, deskH),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFB4692C), Color(0xFF7C3F11))),
                    topLeft = Offset(rx - bookW * 0.30f, y - deskH),
                    size = Size(deskW, deskH),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
            }
        }
    }

    /**
     * Stage 2 (Reference 04 & 05): High-tech Science Lab with glowing green & blue 3D DNA double helices on pedestals,
     * 3D orbiting Bohr atom on glowing pad, bubbling Erlenmeyer flasks, and classroom/lab backdrop!
     */
    private fun DrawScope.drawScienceLabScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        // Metallic Lab Wall & Futuristic Lab Doorway at Horizon
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(w * 0.25f, horizonY - 85f),
            size = Size(w * 0.50f, 85f),
            cornerRadius = CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = Color(0xFF38BDF8).copy(alpha = 0.35f),
            topLeft = Offset(w * 0.28f, horizonY - 75f),
            size = Size(w * 0.44f, 65f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )

        // Left (Green) & Right (Cyan-Blue) 3D DNA Double Helices on Circular Lab Pedestals (Reference 05!)
        for (side in listOf(-1f, 1f)) {
            val cx = if (side < 0) w * 0.11f else w * 0.89f
            val primaryCol = if (side < 0) Color(0xFF22C55E) else Color(0xFF38BDF8)
            val secondaryCol = if (side < 0) Color(0xFF86EFAC) else Color(0xFFBAE6FD)
            val baseY = h * 0.56f

            // Circular metallic DNA pedestal base
            drawOval(
                color = Color(0xFF334155),
                topLeft = Offset(cx - w * 0.09f, baseY - 10f),
                size = Size(w * 0.18f, 26f)
            )
            drawOval(
                color = primaryCol,
                topLeft = Offset(cx - w * 0.075f, baseY - 6f),
                size = Size(w * 0.15f, 18f),
                style = Stroke(width = 3f)
            )

            // Double Helix Rungs & Ribbons
            val steps = 18
            val topY = h * 0.18f
            for (s in 0 until steps) {
                val frac = s / steps.toFloat()
                val y = topY + frac * (baseY - topY)
                val phase = frac * 3.0f * PI.toFloat() + time * 2.6f
                val amp = w * 0.056f
                val x1 = cx + sin(phase) * amp
                val x2 = cx - sin(phase) * amp
                // Base pair rung
                drawLine(
                    color = primaryCol.copy(alpha = 0.55f),
                    start = Offset(x1, y),
                    end = Offset(x2, y),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
                // Glowing 3D nodes
                drawCircle(primaryCol, radius = 6f, center = Offset(x1, y))
                drawCircle(secondaryCol, radius = 5.5f, center = Offset(x2, y))
            }
        }

        // Orbiting 3D Bohr Atom on Glowing Hologram Pad (Bottom-Left - Reference 05!)
        val atomPad = Offset(w * 0.14f, h * 0.69f)
        drawOval(
            color = Color(0xFF334155),
            topLeft = Offset(atomPad.x - 42f, atomPad.y + 34f),
            size = Size(84f, 22f)
        )
        drawOval(
            color = Color(0xFF38BDF8),
            topLeft = Offset(atomPad.x - 32f, atomPad.y + 37f),
            size = Size(64f, 16f)
        )
        val atomCenter = Offset(atomPad.x, atomPad.y + sin(time * 2.5f) * 6f)
        for (deg in listOf(-55f, 0f, 55f)) {
            with(drawContext.canvas) {
                save()
                translate(atomCenter.x, atomCenter.y)
                rotate(deg)
                drawOval(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.80f),
                    topLeft = Offset(-42f, -15f),
                    size = Size(84f, 30f),
                    style = Stroke(width = 2.6f)
                )
                val eAngle = time * 3.5f + deg
                val ex = cos(eAngle) * 42f
                val ey = sin(eAngle) * 15f
                drawCircle(Color(0xFF38BDF8), radius = 5.5f, center = Offset(ex, ey))
                restore()
            }
        }
        // Red & Blue Atomic Nucleus
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFCA5A5), Color(0xFFEF4444)), center = atomCenter, radius = 14f),
            radius = 12f,
            center = atomCenter
        )

        // Glowing Bubbling Conical Chemistry Flasks on Left & Right (Reference 05!)
        drawLabFlask(Offset(w * 0.06f, h * 0.56f), Color(0xFFA855F7), time)
        drawLabFlask(Offset(w * 0.87f, h * 0.53f), Color(0xFF22C55E), time + 1.2f)
        drawLabFlask(Offset(w * 0.94f, h * 0.57f), Color(0xFFF97316), time + 2.4f)
    }

    private fun DrawScope.drawLabFlask(center: Offset, liquidColor: Color, time: Float) {
        val flaskPath = Path().apply {
            moveTo(center.x - 8f, center.y - 34f)
            lineTo(center.x + 8f, center.y - 34f)
            lineTo(center.x + 8f, center.y - 16f)
            lineTo(center.x + 26f, center.y + 22f)
            lineTo(center.x - 26f, center.y + 22f)
            lineTo(center.x - 8f, center.y - 16f)
            close()
        }
        val liquidPath = Path().apply {
            moveTo(center.x - 16f, center.y + 2f)
            lineTo(center.x + 16f, center.y + 2f)
            lineTo(center.x + 24f, center.y + 20f)
            lineTo(center.x - 24f, center.y + 20f)
            close()
        }
        drawPath(liquidPath, Brush.verticalGradient(listOf(liquidColor.copy(alpha = 0.85f), liquidColor)))
        drawPath(flaskPath, Color(0xFFBAE6FD).copy(alpha = 0.75f), style = Stroke(width = 2.8f))
        // Rising glowing bubbles
        for (b in 0..2) {
            val by = center.y + 12f - ((time * 22f + b * 14f) % 36f)
            val bx = center.x + sin(time * 3f + b) * 7f
            drawCircle(liquidColor, radius = 3.5f, center = Offset(bx, by))
        }
    }

    /**
     * Stage 3 & 18 (Reference 06): Deep Space Observatory with blazing Sun, 3D Ringed Saturn, colorful Spiral Nebula,
     * glowing planets, and curved cyan cosmic bridge rails!
     */
    private fun DrawScope.drawSpaceObservatoryScenery3D(
        w: Float,
        h: Float,
        horizonY: Float,
        time: Float,
        isOrbitalStation: Boolean = false
    ) {
        // Dense twinkling starfield
        for (i in 0..65) {
            val sx = ((i * 73.1f) % 100f) / 100f * w
            val sy = ((i * 41.7f) % 100f) / 100f * (h * 0.78f)
            val twinkle = 0.35f + 0.65f * sin(time * 2.5f + i).coerceAtLeast(0f)
            drawCircle(Color.White.copy(alpha = twinkle), radius = (1.3f + (i % 3)), center = Offset(sx, sy))
        }

        // Blazing Golden Sun (Top-Left - Reference 06)
        val sunCenter = Offset(w * 0.11f, h * 0.14f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFFEF08A), Color(0xFFF59E0B).copy(alpha = 0.45f), Color.Transparent),
                center = sunCenter,
                radius = w * 0.17f
            ),
            radius = w * 0.17f,
            center = sunCenter
        )
        drawCircle(Color(0xFFFFFBEB), radius = w * 0.055f, center = sunCenter)

        // 3D Ringed Saturn Planet (Upper-Left - Reference 06!)
        val saturn = Offset(w * 0.25f, h * 0.22f + sin(time * 1.2f) * 5f)
        with(drawContext.canvas) {
            save()
            translate(saturn.x, saturn.y)
            rotate(-18f)
            // Outer & Inner Saturn Rings
            drawOval(
                color = Color(0xFFE7C9A9).copy(alpha = 0.75f),
                topLeft = Offset(-w * 0.22f, -w * 0.052f),
                size = Size(w * 0.44f, w * 0.104f),
                style = Stroke(width = 11f)
            )
            drawOval(
                color = Color(0xFFFEF3C7).copy(alpha = 0.45f),
                topLeft = Offset(-w * 0.17f, -w * 0.038f),
                size = Size(w * 0.34f, w * 0.076f),
                style = Stroke(width = 5f)
            )
            restore()
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFC29B6B), Color(0xFF5C4028)),
                center = Offset(saturn.x - w * 0.03f, saturn.y - w * 0.03f),
                radius = w * 0.11f
            ),
            radius = w * 0.095f,
            center = saturn
        )

        // Glowing Cyan-Orange Spiral Galaxy Nebula (Upper-Right - Reference 06!)
        val nebula = Offset(w * 0.76f, h * 0.21f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFF67E8F9).copy(alpha = 0.75f),
                    Color(0xFFF97316).copy(alpha = 0.40f),
                    Color.Transparent
                ),
                center = nebula,
                radius = w * 0.19f
            ),
            radius = w * 0.19f,
            center = nebula
        )

        // Foreground Pink-Purple Planet (Mid-Left) & Emerald-Blue Planet (Mid-Right - Reference 06)
        val pinkPlanet = Offset(w * 0.12f, h * 0.54f + cos(time * 1.6f) * 6f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF472B6), Color(0xFF9333EA), Color(0xFF3B0764)),
                center = Offset(pinkPlanet.x - 12f, pinkPlanet.y - 12f),
                radius = w * 0.09f
            ),
            radius = w * 0.068f,
            center = pinkPlanet
        )
        val greenPlanet = Offset(w * 0.91f, h * 0.43f + sin(time * 1.8f) * 6f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6EE7B7), Color(0xFF0284C7), Color(0xFF0F172A)),
                center = Offset(greenPlanet.x - 10f, greenPlanet.y - 10f),
                radius = w * 0.07f
            ),
            radius = w * 0.052f,
            center = greenPlanet
        )

        if (isOrbitalStation) {
            // Solar panel wings left & right
            for (side in listOf(0.02f, 0.82f)) {
                drawRect(Color(0xFF1D4ED8), Offset(w * side, horizonY - 40f), Size(w * 0.16f, 55f))
                drawRect(Color(0xFFFACC15), Offset(w * side, horizonY - 40f), Size(w * 0.16f, 55f), style = Stroke(2f))
            }
        }
    }

    /**
     * Stage 4 & 16 (Reference 07): Grand Islamic/Classical Marble Scholars' Citadel with soaring marble arches,
     * hanging golden astrolabes, bookshelves built into marble colonnades, waterfalls & cacti/open books!
     */
    private fun DrawScope.drawScholarsCitadelScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        // Soaring Double Marble Archways at Horizon (Reference 07)
        for (layer in 0..1) {
            val inset = layer * w * 0.11f
            val archRectW = w * 0.72f - inset * 2f
            drawArc(
                brush = Brush.verticalGradient(listOf(Color(0xFFFDFBF7), Color(0xFFD6CEBF))),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.5f - archRectW * 0.5f, horizonY - archRectW * 0.55f),
                size = Size(archRectW, archRectW),
                style = Stroke(width = 22f - layer * 6f)
            )
            // Marble pillars supporting arch
            drawRect(
                color = Color(0xFFE7E0D3),
                topLeft = Offset(w * 0.5f - archRectW * 0.5f - 10f, horizonY - 10f),
                size = Size(22f, h * 0.25f)
            )
            drawRect(
                color = Color(0xFFE7E0D3),
                topLeft = Offset(w * 0.5f + archRectW * 0.5f - 12f, horizonY - 10f),
                size = Size(22f, h * 0.25f)
            )
        }

        // Left & Right Marble Colonnade Walls with Waterfalls (Reference 07)
        for (side in listOf(0f, w * 0.86f)) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFFE5DEC9), Color(0xFFCBBFA8))),
                topLeft = Offset(side, 0f),
                size = Size(w * 0.14f, h * 0.62f)
            )
            // Shimmering cyan waterfall cascading into marble basin
            val wfX = if (side == 0f) w * 0.10f else w * 0.88f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFBAE6FD).copy(alpha = 0.80f), Color(0xFF38BDF8).copy(alpha = 0.55f), Color.White)
                ),
                topLeft = Offset(wfX, horizonY * 0.95f),
                size = Size(w * 0.035f, h * 0.24f)
            )
        }

        // Hanging Golden 3D Astrolabes with Glowing Halos (Reference 07!)
        for (cx in listOf(w * 0.19f, w * 0.81f)) {
            val cy = horizonY * 0.92f + sin(time * 1.8f) * 6f
            drawLine(Color(0xFFD97706), Offset(cx, 0f), Offset(cx, cy - 42f), strokeWidth = 3f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x55FEF08A), Color.Transparent), center = Offset(cx, cy), radius = 56f),
                radius = 56f,
                center = Offset(cx, cy)
            )
            drawCircle(Color(0xFFF59E0B), radius = 40f, center = Offset(cx, cy), style = Stroke(width = 6f))
            drawCircle(Color(0xFFFEF08A), radius = 27f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
            drawLine(Color(0xFFF59E0B), Offset(cx - 36f, cy), Offset(cx + 36f, cy), strokeWidth = 3f)
            drawLine(Color(0xFFF59E0B), Offset(cx, cy - 36f), Offset(cx, cy + 36f), strokeWidth = 3f)
            drawLine(Color(0xFFFDE047), Offset(cx - 26f, cy - 26f), Offset(cx + 26f, cy + 26f), strokeWidth = 2.5f)
        }
    }

    /**
     * Stage 5 & 15 (Reference 08): Valley of Ancient Civilizations with Temple Sandstone Walls, Tall Obelisks,
     * Glowing Hieroglyphic Pillars, Arched Horizon Banner, and Foreground 3D Pyramids & Study Books!
     */
    private fun DrawScope.drawAncientCivilizationsScenery3D(
        w: Float,
        h: Float,
        horizonY: Float,
        scroll: Float,
        time: Float,
        isAlexandria: Boolean = false
    ) {
        // Left & Right Monumental Sandstone Temple Walls (Reference 08)
        val leftWall = Path().apply {
            moveTo(0f, horizonY * 0.45f)
            lineTo(w * 0.22f, horizonY + 15f)
            lineTo(w * 0.22f, h * 0.56f)
            lineTo(0f, h * 0.64f)
            close()
        }
        drawPath(leftWall, Brush.horizontalGradient(listOf(Color(0xFFB47B44), Color(0xFF8C5828))))
        val rightWall = Path().apply {
            moveTo(w, horizonY * 0.45f)
            lineTo(w * 0.78f, horizonY + 15f)
            lineTo(w * 0.78f, h * 0.56f)
            lineTo(w, h * 0.64f)
            close()
        }
        drawPath(rightWall, Brush.horizontalGradient(listOf(Color(0xFF8C5828), Color(0xFFB47B44))))

        // Twin Tall Pharaonic Obelisks at Horizon (Reference 08)
        for (ox in listOf(w * 0.26f, w * 0.74f)) {
            val obelisk = Path().apply {
                moveTo(ox - 16f, horizonY + 70f)
                lineTo(ox - 10f, horizonY - 75f)
                lineTo(ox, horizonY - 105f)
                lineTo(ox + 10f, horizonY - 75f)
                lineTo(ox + 16f, horizonY + 70f)
                close()
            }
            drawPath(obelisk, Brush.verticalGradient(listOf(Color(0xFFD99B52), Color(0xFF78350F))))
        }

        // Grand Sandstone Archway Spanning Over the 3 Gates (Reference 08!)
        drawArc(
            brush = Brush.verticalGradient(listOf(Color(0xFFC88A4B), Color(0xFF78350F))),
            startAngle = 185f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = Offset(w * 0.20f, horizonY - w * 0.14f),
            size = Size(w * 0.60f, w * 0.52f),
            style = Stroke(width = 30f, cap = StrokeCap.Round)
        )

        // Foreground Left & Right Pillars with Glowing Golden Hieroglyphs (Reference 08!)
        for (px in listOf(w * 0.08f, w * 0.92f)) {
            val topY = horizonY + 35f
            val botY = h * 0.64f
            // Pillar कैपिटल & Base
            drawRoundRect(Color(0xFF78350F), Offset(px - 28f, topY - 14f), Size(56f, 16f), CornerRadius(4f, 4f))
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF92400E), Color(0xFFB45309), Color(0xFF78350F))),
                topLeft = Offset(px - 20f, topY),
                size = Size(40f, botY - topY)
            )
            drawRoundRect(Color(0xFF78350F), Offset(px - 30f, botY), Size(60f, 16f), CornerRadius(4f, 4f))

            // Glowing Golden Hieroglyphic Symbols
            val glow = 0.65f + 0.35f * sin(time * 3f)
            for (k in 0..5) {
                val gy = topY + 18f + k * ((botY - topY - 24f) / 6f)
                drawCircle(
                    color = Color(0xFFFEF08A).copy(alpha = glow),
                    radius = 5f,
                    center = Offset(px, gy),
                    style = Stroke(width = 2.5f)
                )
                drawLine(
                    color = Color(0xFFFACC15).copy(alpha = glow),
                    start = Offset(px - 9f, gy + 7f),
                    end = Offset(px + 9f, gy + 7f),
                    strokeWidth = 2.5f
                )
            }
        }

        // Foreground Miniature 3D Pyramids & Study Books along the Sandy Track Edges (Reference 08!)
        val pyrRight = Path().apply {
            moveTo(w * 0.72f, h * 0.73f)
            lineTo(w * 0.81f, h * 0.63f)
            lineTo(w * 0.90f, h * 0.73f)
            close()
        }
        drawPath(pyrRight, Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFF92400E))))
        // Stacked study books on left sand bank
        for (b in 0..2) {
            val col = listOf(Color(0xFF2563EB), Color(0xFFDC2626), Color(0xFFFACC15))[b]
            drawRoundRect(
                color = col,
                topLeft = Offset(w * 0.14f, h * 0.71f - b * 9f),
                size = Size(46f, 8.5f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
        if (isAlexandria) {
            // Lighthouse beam at horizon
            drawCircle(Color(0xFFFEF08A), radius = 22f, center = Offset(w * 0.5f, horizonY - 95f))
        }
    }

    /**
     * Stage 6 & 13 (Reference 09): Cyber Quantum City with futuristic glass skyscrapers, glowing cyan holographic math
     * formulas on building facades, and elevated monorail bullet trains on both sides!
     */
    private fun DrawScope.drawCyberCityScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        // Glass Skyscrapers Skyline
        val towers = listOf(
            Triple(0.00f, 0.17f, 0.56f),
            Triple(0.16f, 0.13f, 0.44f),
            Triple(0.71f, 0.13f, 0.46f),
            Triple(0.83f, 0.17f, 0.58f)
        )
        towers.forEachIndexed { idx, (xFrac, wFrac, hFrac) ->
            val topY = horizonY - h * hFrac * 0.52f
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0A192F))),
                topLeft = Offset(w * xFrac, topY),
                size = Size(w * wFrac, h * hFrac)
            )
            // Glass grid & holographic cyan math equations on facades (Reference 09!)
            for (r in 0..8) {
                val wy = topY + 16f + r * 24f
                val alpha = 0.35f + 0.35f * sin(time * 2.2f + r + idx).coerceAtLeast(0f)
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = alpha),
                    start = Offset(w * xFrac + 8f, wy),
                    end = Offset(w * (xFrac + wFrac) - 8f, wy),
                    strokeWidth = if (r % 3 == 0) 3.5f else 1.8f
                )
            }
        }

        // Elevated Monorail Viaducts & Sleek Futuristic White/Cyan Trains on Left & Right (Reference 09!)
        drawLine(Color(0xFF64748B), Offset(0f, horizonY + 55f), Offset(w * 0.42f, horizonY + 5f), strokeWidth = 14f)
        drawLine(Color(0xFF38BDF8), Offset(0f, horizonY + 51f), Offset(w * 0.42f, horizonY + 3f), strokeWidth = 3f)

        drawLine(Color(0xFF64748B), Offset(w, horizonY + 25f), Offset(w * 0.58f, horizonY + 5f), strokeWidth = 14f)
        drawLine(Color(0xFF38BDF8), Offset(w, horizonY + 21f), Offset(w * 0.58f, horizonY + 3f), strokeWidth = 3f)

        // Left Monorail Train Car
        val trainL = Path().apply {
            moveTo(w * 0.11f, horizonY + 28f)
            lineTo(w * 0.31f, horizonY + 8f)
            lineTo(w * 0.31f, horizonY + 24f)
            lineTo(w * 0.11f, horizonY + 46f)
            close()
        }
        drawPath(trainL, Brush.verticalGradient(listOf(Color.White, Color(0xFF93C5FD))))
        drawLine(Color(0xFF0284C7), Offset(w * 0.13f, horizonY + 34f), Offset(w * 0.29f, horizonY + 16f), strokeWidth = 6f)
    }

    /**
     * Stage 7 & 20 (Reference 10): Champions' Summit with golden mountain peak above a sea of clouds
     * and white marble bridge balustrades lined with golden pillars on both sides of the track!
     */
    private fun DrawScope.drawChampionsSummitScenery3D(w: Float, h: Float, horizonY: Float, time: Float) {
        // Majestic Golden Mountain Peak towering behind the gates (Reference 10!)
        val mountain = Path().apply {
            moveTo(w * 0.04f, horizonY + 50f)
            lineTo(w * 0.46f, h * 0.02f)
            lineTo(w * 0.56f, h * 0.03f)
            lineTo(w * 0.96f, horizonY + 50f)
            close()
        }
        drawPath(
            path = mountain,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF78350F), Color(0xFFD97706), Color(0xFFFDE68A)),
                start = Offset(w * 0.2f, horizonY),
                end = Offset(w * 0.8f, 0f)
            )
        )

        // Fluffy Golden-White Cloud Sea below the bridge (Reference 10)
        for (i in 0..8) {
            val cx = (i * 0.14f) * w + sin(time * 0.8f + i) * 8f
            val cy = horizonY + 45f + (i % 3) * 35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFBEB).copy(alpha = 0.90f), Color(0xFFFDE68A).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = w * 0.18f
                ),
                radius = w * 0.18f,
                center = Offset(cx, cy)
            )
        }
    }

    /**
     * Stage 8 & 17 (Reference 11): Quantum Physics Reactor Tunnel with concentric purple/cyan accelerator arches,
     * 3D curved pipe conduits along both walls, and tall cylindrical plasma lightning chambers!
     */
    private fun DrawScope.drawQuantumReactorScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        // Concentric 3D Particle Accelerator Arched Ribs (Reference 11!)
        for (i in 0..6) {
            val z = ((i * 0.15f + scroll) % 1.0f)
            val rx = w * (0.18f + z * 0.68f)
            val ry = h * (0.14f + z * 0.42f)
            val archColor = if (i % 2 == 0) Color(0xFF6366F1) else Color(0xFF38BDF8)
            drawArc(
                color = archColor.copy(alpha = (0.30f + z * 0.55f).coerceAtMost(0.90f)),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.5f - rx, horizonY - ry * 0.82f + z * 55f),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 6f + z * 10f)
            )
        }

        // Tall Cylindrical Plasma Lightning Chambers & Glowing Cyan Pipe Conduits on Left & Right (Reference 11!)
        for (side in listOf(-1f, 1f)) {
            val cx = if (side < 0) w * 0.08f else w * 0.92f
            val topY = horizonY * 0.65f
            val cylH = h * 0.36f
            // Metallic Cap & Base
            drawRoundRect(Color(0xFF312E81), Offset(cx - 26f, topY - 12f), Size(52f, 14f), CornerRadius(5f, 5f))
            drawRoundRect(Color(0xFF38BDF8), Offset(cx - 26f, topY - 12f), Size(52f, 14f), CornerRadius(5f, 5f), style = Stroke(2f))
            // Translucent Violet Plasma Tube
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xAA4C1D95), Color(0xCCD946EF), Color(0xAA4C1D95))
                ),
                topLeft = Offset(cx - 22f, topY),
                size = Size(44f, cylH),
                cornerRadius = CornerRadius(10f, 10f)
            )
            // Crackling Plasma Lightning Core inside tube
            val j1 = sin(time * 11f + side) * 9f
            val j2 = cos(time * 9f - side) * 9f
            drawLine(Color.White, Offset(cx, topY + 8f), Offset(cx + j1, topY + cylH * 0.45f), strokeWidth = 3.5f)
            drawLine(Color(0xFF67E8F9), Offset(cx + j1, topY + cylH * 0.45f), Offset(cx + j2, topY + cylH * 0.92f), strokeWidth = 3.5f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, Color(0xFFF0ABFC), Color.Transparent)),
                radius = 18f,
                center = Offset(cx + j1, topY + cylH * 0.48f)
            )
            // Perspective cyan energy pipes along lower wall
            val pipeStart = Offset(if (side < 0) w * 0.34f else w * 0.66f, horizonY + 12f)
            val pipeEnd = Offset(if (side < 0) 0f else w, h * 0.65f)
            drawLine(Color(0xFF0284C7), pipeStart, pipeEnd, strokeWidth = 14f, cap = StrokeCap.Round)
            drawLine(Color(0xFF67E8F9), pipeStart, pipeEnd, strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.drawBiologyJungleScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        val cells = listOf(
            Triple(w * 0.14f, h * 0.25f, 30f),
            Triple(w * 0.84f, h * 0.22f, 36f),
            Triple(w * 0.12f, h * 0.54f, 26f),
            Triple(w * 0.88f, h * 0.50f, 28f)
        )
        cells.forEachIndexed { idx, (cx, cy, r) ->
            val bob = sin(time * 2f + idx) * 8f
            drawCircle(Color(0xFF34D399).copy(alpha = 0.32f), radius = r, center = Offset(cx, cy + bob))
            drawCircle(Color(0xFF6EE7B7), radius = r, center = Offset(cx, cy + bob), style = Stroke(width = 3.5f))
            drawCircle(Color(0xFFA855F7).copy(alpha = 0.88f), radius = r * 0.38f, center = Offset(cx, cy + bob))
        }
    }

    private fun DrawScope.drawDeepOceanScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        for (i in 0..4) {
            val z = ((i * 0.22f + scroll) % 1.0f)
            val rx = w * (0.24f + z * 0.56f)
            val ry = h * (0.14f + z * 0.36f)
            drawArc(
                color = Color(0xFF7DD3FC).copy(alpha = 0.35f + z * 0.3f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.5f - rx, horizonY - ry * 0.8f + z * 45f),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 5f + z * 5f)
            )
        }
        for (i in 0..18) {
            val bx = ((i * 61.3f) % 100f) / 100f * w
            val by = h - (((time * 52f + i * 73f) % h))
            drawCircle(Color(0xFFBAE6FD).copy(alpha = 0.55f), radius = 3f + (i % 4) * 2.2f, center = Offset(bx, by), style = Stroke(2f))
        }
    }

    private fun DrawScope.drawLiteraturePalaceScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        for (cx in listOf(w * 0.22f, w * 0.50f, w * 0.78f)) {
            val cy = h * 0.13f + sin(time + cx) * 3f
            drawLine(Color(0xFFF59E0B), Offset(cx, 0f), Offset(cx, cy - 16f), strokeWidth = 3f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFFBEB), Color(0xFFF59E0B).copy(alpha = 0.55f), Color.Transparent)),
                radius = 42f,
                center = Offset(cx, cy)
            )
        }
        for (i in 0..12) {
            val sx = ((i * 89f) % 100f) / 100f * w
            val sy = horizonY * 0.45f + sin(time * 1.5f + i) * 24f + (i % 3) * 32f
            drawCircle(Color(0xFFFDE68A).copy(alpha = 0.75f), radius = 4.5f, center = Offset(sx, sy))
        }
    }

    private fun DrawScope.drawVolcanoChemistryScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        // Glowing lava rivers & floating hexagonal benzene rings
        for (side in listOf(w * 0.14f, w * 0.86f)) {
            val y = horizonY * 0.75f + sin(time * 2f + side) * 10f
            drawCircle(
                color = Color(0xFFF97316),
                radius = 30f,
                center = Offset(side, y),
                style = Stroke(width = 4f)
            )
            drawCircle(
                color = Color(0xFFFACC15),
                radius = 18f,
                center = Offset(side, y),
                style = Stroke(width = 2.5f)
            )
        }
    }

    private fun DrawScope.drawCrystalCavernScenery3D(w: Float, h: Float, horizonY: Float, scroll: Float, time: Float) {
        val crystalColors = listOf(Color(0xFFA855F7), Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFF22D3EE))
        for (i in 0..5) {
            val isLeft = i % 2 == 0
            val baseX = if (isLeft) w * (0.04f + (i / 2) * 0.06f) else w * (0.96f - (i / 2) * 0.06f)
            val baseY = horizonY + 90f + (i / 2) * 80f
            val tipX = baseX + (if (isLeft) 42f else -42f)
            val tipY = baseY - 130f - (i % 3) * 30f
            val spire = Path().apply {
                moveTo(baseX - 20f, baseY)
                lineTo(tipX, tipY)
                lineTo(baseX + 20f, baseY)
                close()
            }
            val c = crystalColors[i % crystalColors.size]
            drawPath(spire, c.copy(alpha = 0.78f))
            drawPath(spire, Color.White.copy(alpha = 0.60f), style = Stroke(2.2f))
        }
        for (ox in listOf(w * 0.20f, w * 0.80f)) {
            val oy = horizonY - 22f + cos(time * 2.5f + ox) * 10f
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, Color(0xFF38BDF8), Color(0xFFA855F7).copy(alpha = 0.3f), Color.Transparent)),
                radius = 36f,
                center = Offset(ox, oy)
            )
        }
    }
}
