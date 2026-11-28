package com.app.games

import androidx.compose.ui.graphics.Color

const val TOTAL_GAME_STAGES = 20

enum class GameCharacterId(
    val id: String,
    val titleAr: String,
    val price: Int,
    val perkSummaryAr: String,
    val accentHex: Long,
    val coinBonusMultiplier: Float = 1.0f,
    val comboBonusMultiplier: Float = 1.0f,
    val gateSpeedFactor: Float = 1.0f,
    val jumpDurationMultiplier: Float = 1.0f,
    val startsWithFreeShield: Boolean = false,
    val hasGoldenTrail: Boolean = false
) {
    FAHIM_SMART(
        id = "fahim_smart",
        titleAr = "فهيم الذكي",
        price = 0,
        perkSummaryAr = "توازن ممتاز وسرعة استجابة في التنقل بين الحارات الثلاث",
        accentHex = 0xFF1A6FE3
    ),
    WALKING_BOOK(
        id = "walking_book",
        titleAr = "الكتاب المتحرك",
        price = 250,
        perkSummaryAr = "+10% عملات إضافية عند الإجابة الصحيحة من أول مرة",
        accentHex = 0xFF2563EB,
        coinBonusMultiplier = 1.10f
    ),
    FLYING_BOOK_TOWER(
        id = "flying_book_tower",
        titleAr = "برج الكتب الطائر",
        price = 500,
        perkSummaryAr = "قفزة أطول فوق العوائق و +15% زيادة في نقاط الـ Combo",
        accentHex = 0xFFEA580C,
        comboBonusMultiplier = 1.15f,
        jumpDurationMultiplier = 1.30f
    ),
    GENIUS_LAMP(
        id = "genius_lamp",
        titleAr = "المصباح العبقري",
        price = 850,
        perkSummaryAr = "تهدئة سرعة البوابة بنسبة 10% لقراءة السؤال بتركيز",
        accentHex = 0xFFD97706,
        gateSpeedFactor = 0.90f
    ),
    SCIENCE_ASTRONAUT(
        id = "science_astronaut",
        titleAr = "رائد الفضاء العلمي",
        price = 1300,
        perkSummaryAr = "يبدأ كل مرحلة مع درع حماية مجاني لمرة واحدة",
        accentHex = 0xFF0284C7,
        startsWithFreeShield = true
    ),
    ROYAL_GOLDEN_HERO(
        id = "royal_golden_hero",
        titleAr = "البطل الملكي الذهبي",
        price = 2000,
        perkSummaryAr = "+25% عملات ذهبية دائمة وأثر خطوات ذهبي متوهج",
        accentHex = 0xFFB45309,
        coinBonusMultiplier = 1.25f,
        hasGoldenTrail = true
    );

    companion object {
        fun fromId(raw: String?): GameCharacterId =
            entries.firstOrNull { it.id == raw } ?: FAHIM_SMART
    }
}

enum class PowerUpType(
    val id: String,
    val titleAr: String,
    val basePrice: Int,
    val maxLevel: Int,
    val descriptionAr: String,
    val accentColor: Color
) {
    COIN_MAGNET(
        id = "coin_magnet",
        titleAr = "مغناطيس العملات",
        basePrice = 150,
        maxLevel = 5,
        descriptionAr = "يجذب العملات الذهبية من الحارات الثلاث تلقائياً (12 إلى 25 ثانية)",
        accentColor = Color(0xFFF59E0B)
    ),
    KNOWLEDGE_SHIELD(
        id = "knowledge_shield",
        titleAr = "درع المعرفة",
        basePrice = 200,
        maxLevel = 5,
        descriptionAr = "يحيط اللاعب بهالة ذهبية تحميه من خسارة قلب عند الاصطدام ببوابة خاطئة",
        accentColor = Color(0xFF1A6FE3)
    ),
    GATE_DETECTOR(
        id = "gate_detector",
        titleAr = "كاشف البوابة الصحيحة",
        basePrice = 250,
        maxLevel = 5,
        descriptionAr = "يُومض إطار البوابة الصحيحة بنور ذهبي قبل الوصول إليها لمساعدتك في الأسئلة الصعبة",
        accentColor = Color(0xFF10B981)
    );

    fun upgradePrice(currentLevel: Int): Int = basePrice + (currentLevel - 1).coerceAtLeast(0) * 75

    fun magnetDurationSeconds(level: Int): Float =
        (12f + (level.coerceIn(1, maxLevel) - 1) * 3.25f).coerceAtMost(25f)
}

enum class HighSchoolGrade(val titleAr: String) {
    GRADE_1("الأول الثانوي"),
    GRADE_2("الثاني الثانوي"),
    GRADE_3("الثالث الثانوي")
}

enum class HighSchoolSubject(
    val titleAr: String,
    val chapters: List<String>,
    val accentColor: Color
) {
    PHYSICS("الفيزياء", listOf("الباب الأول", "الباب الثاني", "الباب الثالث", "الفيزياء الحديثة", "مراجعة شاملة"), Color(0xFF1A6FE3)),
    CHEMISTRY("الكيمياء", listOf("الباب الأول", "الباب الثاني", "الكيمياء العضوية", "الكيمياء الكهربية", "مراجعة شاملة"), Color(0xFF0EA5E9)),
    BIOLOGY("الأحياء", listOf("الدعامة والحركة", "التنسيق الهرموني", "المناعة", "البيولوجيا الجزيئية DNA", "مراجعة شاملة"), Color(0xFF10B981)),
    MATH("الرياضيات", listOf("الجبر والفراغية", "التفاضل والتكامل", "الاستاتيكا والديناميكا", "حساب المثلثات", "مراجعة شاملة"), Color(0xFF6366F1)),
    ARABIC("اللغة العربية", listOf("النحو والإعراب", "البلاغة", "الأدب والنصوص", "القراءة المتحررة", "مراجعة شاملة"), Color(0xFFD97706)),
    ENGLISH("الإنجليزية", listOf("القواعد (Grammar)", "المفردات (Vocab)", "المهارات والترجمة", "القصة والكتابة", "مراجعة شاملة"), Color(0xFF2563EB)),
    FRENCH("الفرنسية", listOf("الباب الأول", "الباب الثاني", "القواعد والمواقف", "الوثائق والإيميل", "مراجعة شاملة"), Color(0xFF7C3AED)),
    HISTORY("التاريخ", listOf("الحملة الفرنسية ومحمد علي", "مصر الحديثة والمعاصرة", "حرب أكتوبر المجيدة", "العرب والقضية الفلسطينية", "مراجعة شاملة"), Color(0xFFB45309)),
    GEOGRAPHY("الجغرافيا", listOf("الجغرافيا السياسية", "المقومات الطبيعية والبشرية", "الحدود السياسية والمشكلات", "التكتلات والعلاقات الدولية", "مراجعة شاملة"), Color(0xFF059669)),
    PHILOSOPHY("الفلسفة والمنطق", listOf("فلسفة البيئة والبيوتيقا", "أخلاقيات المهنة والقيم", "المنطق والاستدلال", "السيبرنطيقا والذكاء الاصطناعي", "مراجعة شاملة"), Color(0xFF9333EA)),
    PSYCHOLOGY("علم النفس والاجتماع", listOf("الذكاءات ونظريات التعلم", "النمو والشخصية", "النظرية والتفاعل الاجتماعي", "البحث العلمي والتطوع", "مراجعة شاملة"), Color(0xFFEC4899))
}

data class GameQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>, // Strictly 3 options for the 3 lanes
    val correctIndex: Int,
    val explanation: String = "",
    val isBossQuestion: Boolean = false,
    val sourceLabel: String = "بنك المنهج"
)

enum class GatePortalStyle {
    TECH_ARCH,
    NEON_HOLO_FRAME,
    COSMIC_PORTAL_ARCH,
    GOLDEN_ISLAMIC_ARCH,
    PHARAONIC_PILLAR_GATE,
    ROYAL_MARBLE_ARCH
}

data class StageSpec(
    val number: Int,
    val nameAr: String,
    val environmentSummaryAr: String,
    val speedMultiplier: Float,
    val coinMultiplier: Float,
    val maxComboCap: Int,
    val standardGatesCount: Int = 7,
    val gateStyle: GatePortalStyle = GatePortalStyle.NEON_HOLO_FRAME,
    val bossChallengeTitleAr: String,
    val chestNameAr: String,
    val bossRewardCoins: Int,
    val bonusPowerUp: PowerUpType? = null,
    val isEndlessStage: Boolean = false
)

data class GameShopState(
    val coins: Int = 350,
    val unlockedStage: Int = 1,
    val completedStages: Set<Int> = emptySet(),
    val ownedCharacters: Set<GameCharacterId> = setOf(GameCharacterId.FAHIM_SMART),
    val equippedCharacter: GameCharacterId = GameCharacterId.FAHIM_SMART,
    val magnetLevel: Int = 1,
    val shieldLevel: Int = 1,
    val detectorLevel: Int = 1,
    val highScore: Int = 0,
    val bestCombo: Int = 1,
    val totalCorrectAnswers: Int = 0,
    val totalBossesDefeated: Int = 0
) {
    fun powerUpLevel(type: PowerUpType): Int = when (type) {
        PowerUpType.COIN_MAGNET -> magnetLevel
        PowerUpType.KNOWLEDGE_SHIELD -> shieldLevel
        PowerUpType.GATE_DETECTOR -> detectorLevel
    }
}

data class RunSummaryResult(
    val stageNumber: Int,
    val victory: Boolean,
    val coinsEarnedFromTrack: Int,
    val bossChestBonus: Int,
    val totalCoinsAdded: Int,
    val score: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val maxCombo: Int,
    val newlyUnlockedStage: Int?,
    val chestNameAr: String,
    val bonusPowerUpGranted: PowerUpType? = null
)
