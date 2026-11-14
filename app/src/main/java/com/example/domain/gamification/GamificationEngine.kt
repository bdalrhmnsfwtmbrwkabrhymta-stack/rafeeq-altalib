package com.example.domain.gamification

import com.example.data.database.QuestEntity
import com.example.data.database.UserCollectionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object GamificationEngine {

    enum class RankTier(
        val rankId: String,
        val titleAr: String,
        val titleEn: String,
        val colorHex: String,
        val minLevel: Int,
        val icon: String
    ) {
        BRONZE("BRONZE", "مبتدئ برونزي", "Bronze Novice", "#CD7F32", 1, "🥉"),
        SILVER("SILVER", "مكافح فضي", "Silver Striker", "#C0C0C0", 10, "🥈"),
        GOLD("GOLD", "متميز ذهبي", "Gold Achiever", "#FFD700", 25, "🥇"),
        PLATINUM("PLATINUM", "متفوق بلاتيني", "Platinum Master", "#00B4D8", 50, "💎"),
        DIAMOND("DIAMOND", "عبقري ماسي", "Diamond Genius", "#7209B7", 75, "🔮"),
        MASTER("MASTER", "أسطورة دراسية", "Study Legend", "#F72585", 100, "👑"),
        GRANDMASTER("GRANDMASTER", "سيد الالتزام المطلق", "Grandmaster of Focus", "#E63946", 150, "🌌")
    }

    data class LevelInfo(
        val level: Int,
        val totalXp: Int,
        val currentLevelXp: Int,
        val xpRequiredForNextLevel: Int,
        val progressPercentage: Float,
        val rank: RankTier
    )

    data class StreakInfo(
        val currentStreak: Int,
        val bestStreak: Int,
        val tierNameAr: String,
        val tierNameEn: String,
        val tierIcon: String,
        val isRiskActive: Boolean,
        val hoursRemainingToDeadline: Int,
        val shieldsRemaining: Int
    )

    /**
     * Calculates required cumulative XP to reach a specific level.
     */
    fun getXpRequiredForLevel(level: Int): Int {
        if (level <= 1) return 0
        // Exponential scaling: Level 2 = 100, Level 3 = 250, Level 4 = 450, etc.
        var total = 0
        for (lvl in 1 until level) {
            total += (lvl * 100) + (lvl * lvl * 15)
        }
        return total
    }

    /**
     * Calculates the level based on total earned XP.
     */
    fun calculateLevelInfo(totalXp: Int): LevelInfo {
        var currentLevel = 1
        while (getXpRequiredForLevel(currentLevel + 1) <= totalXp) {
            currentLevel++
        }

        val xpAtCurrentLevelStart = getXpRequiredForLevel(currentLevel)
        val xpAtNextLevelStart = getXpRequiredForLevel(currentLevel + 1)
        val xpRequiredForNextLevel = xpAtNextLevelStart - xpAtCurrentLevelStart
        val currentLevelXp = (totalXp - xpAtCurrentLevelStart).coerceAtLeast(0)

        val progressPercentage = if (xpRequiredForNextLevel > 0) {
            (currentLevelXp.toFloat() / xpRequiredForNextLevel.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

        val rank = getRankForLevel(currentLevel)

        return LevelInfo(
            level = currentLevel,
            totalXp = totalXp,
            currentLevelXp = currentLevelXp,
            xpRequiredForNextLevel = xpRequiredForNextLevel,
            progressPercentage = progressPercentage,
            rank = rank
        )
    }

    fun getRankForLevel(level: Int): RankTier {
        return when {
            level >= RankTier.GRANDMASTER.minLevel -> RankTier.GRANDMASTER
            level >= RankTier.MASTER.minLevel -> RankTier.MASTER
            level >= RankTier.DIAMOND.minLevel -> RankTier.DIAMOND
            level >= RankTier.PLATINUM.minLevel -> RankTier.PLATINUM
            level >= RankTier.GOLD.minLevel -> RankTier.GOLD
            level >= RankTier.SILVER.minLevel -> RankTier.SILVER
            else -> RankTier.BRONZE
        }
    }

    fun calculateLevelAndRank(totalXp: Int): Pair<Int, String> {
        val info = calculateLevelInfo(totalXp)
        return Pair(info.level, info.rank.rankId)
    }

    /**
     * Streak Tier definition
     */
    fun getStreakTier(streakDays: Int): Triple<String, String, String> {
        return when {
            streakDays >= 100 -> Triple("أسطورة الطلاب 💎", "Student Legend 💎", "💎")
            streakDays >= 60 -> Triple("تنين الإنجاز 🐉", "Dragon of Achievement 🐉", "🐉")
            streakDays >= 30 -> Triple("تاج الإصرار 👑", "Crown of Persistence 👑", "👑")
            streakDays >= 14 -> Triple("درع الاستمرارية 🛡️", "Shield of Consistency 🛡️", "🛡️")
            streakDays >= 7 -> Triple("لهيب العزيمة 🌟", "Flame of Will 🌟", "🌟")
            streakDays >= 3 -> Triple("تيار الالتزام ⚡", "Lightning Flow ⚡", "⚡")
            streakDays >= 1 -> Triple("شعلة البداية 🔥", "Spark of Beginning 🔥", "🔥")
            else -> Triple("في انتظار البداية ✨", "Ready to Start ✨", "✨")
        }
    }

    /**
     * Checks if current active Core & Main quests are at risk of breaking streak before daily deadline.
     */
    fun evaluateStreakRisk(
        quests: List<QuestEntity>,
        deadlineHour: Int,
        deadlineMinute: Int,
        currentStreak: Int,
        bestStreak: Int,
        shieldsCount: Int
    ): StreakInfo {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val currentTotalMinutes = currentHour * 60 + currentMinute
        val deadlineTotalMinutes = deadlineHour * 60 + deadlineMinute

        val minutesRemaining = deadlineTotalMinutes - currentTotalMinutes
        val hoursRemaining = (minutesRemaining / 60).coerceAtLeast(0)

        // Core or Main quests that are not yet completed
        val pendingCriticalQuests = quests.filter { (it.questType == "CORE" || it.questType == "MAIN") && !it.isCompleted }

        // Risk is active if there are pending critical quests and less than 4 hours remain before deadline
        val isRiskActive = pendingCriticalQuests.isNotEmpty() && minutesRemaining in 1..240

        val (tierAr, tierEn, icon) = getStreakTier(currentStreak)

        return StreakInfo(
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            tierNameAr = tierAr,
            tierNameEn = tierEn,
            tierIcon = icon,
            isRiskActive = isRiskActive,
            hoursRemainingToDeadline = hoursRemaining,
            shieldsRemaining = shieldsCount
        )
    }

    /**
     * Default Collection items to seed and unlock
     */
    fun getDefaultCollections(): List<UserCollectionEntity> {
        return listOf(
            // Titles
            UserCollectionEntity(
                itemType = "TITLE",
                itemId = "title_student",
                nameAr = "طالب مجتهد",
                nameEn = "Diligent Student",
                descriptionAr = "اللقب الأساسي لجميع الطلاب الصاعدين",
                descriptionEn = "Standard title for all aspiring students",
                iconOrColor = "🎓",
                isUnlocked = true,
                isEquipped = true,
                requiredLevel = 1,
                requiredRank = "BRONZE"
            ),
            UserCollectionEntity(
                itemType = "TITLE",
                itemId = "title_focus_master",
                nameAr = "سيد التركيز",
                nameEn = "Master of Focus",
                descriptionAr = "يُمنح للطلاب الملتزمين بجلسات بومودورو والمذاكرة العميقة",
                descriptionEn = "Awarded to masters of deep focus and Pomodoro",
                iconOrColor = "⚡",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 5,
                requiredRank = "BRONZE"
            ),
            UserCollectionEntity(
                itemType = "TITLE",
                itemId = "title_knight_achievement",
                nameAr = "فارس الإنجاز",
                nameEn = "Knight of Achievement",
                descriptionAr = "يُمنح لمن يتجاوز المستوى 10 ويحقق رتبة المكافح الفضي",
                descriptionEn = "Unlocked at Level 10 upon reaching Silver Rank",
                iconOrColor = "⚔️",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 10,
                requiredRank = "SILVER"
            ),
            UserCollectionEntity(
                itemType = "TITLE",
                itemId = "title_academic_genius",
                nameAr = "النابغة الأكاديمي",
                nameEn = "Academic Genius",
                descriptionAr = "يُمنح لأصحاب المستوى 25 ورتبة الذهب",
                descriptionEn = "Unlocked at Level 25 upon reaching Gold Rank",
                iconOrColor = "🌟",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 25,
                requiredRank = "GOLD"
            ),
            UserCollectionEntity(
                itemType = "TITLE",
                itemId = "title_legend_commitment",
                nameAr = "أسطورة الالتزام",
                nameEn = "Legend of Commitment",
                descriptionAr = "أعلى لقب شرفي لمن يصل للمستوى 50 ورتبة البلاتين",
                descriptionEn = "Highest honor unlocked at Level 50 Platinum Rank",
                iconOrColor = "👑",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 50,
                requiredRank = "PLATINUM"
            ),

            // Frames
            UserCollectionEntity(
                itemType = "FRAME",
                itemId = "FRAME_DEFAULT",
                nameAr = "الإطار الكلاسيكي",
                nameEn = "Classic Frame",
                descriptionAr = "إطار بسيط وأنيق",
                descriptionEn = "Clean classic rounded frame",
                iconOrColor = "#94A3B8",
                isUnlocked = true,
                isEquipped = true,
                requiredLevel = 1,
                requiredRank = "BRONZE"
            ),
            UserCollectionEntity(
                itemType = "FRAME",
                itemId = "FRAME_BRONZE_AURA",
                nameAr = "هالة البرونز",
                nameEn = "Bronze Aura",
                descriptionAr = "إطار برونزي لامع للشعلة الأولى",
                descriptionEn = "Gleaming bronze starter frame",
                iconOrColor = "#CD7F32",
                isUnlocked = true,
                isEquipped = false,
                requiredLevel = 3,
                requiredRank = "BRONZE"
            ),
            UserCollectionEntity(
                itemType = "FRAME",
                itemId = "FRAME_SILVER_SHIELD",
                nameAr = "درع الفضة",
                nameEn = "Silver Shield",
                descriptionAr = "إطار فضي مصقول برونق السعي المستمر",
                descriptionEn = "Polished silver shield frame",
                iconOrColor = "#C0C0C0",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 10,
                requiredRank = "SILVER"
            ),
            UserCollectionEntity(
                itemType = "FRAME",
                itemId = "FRAME_GOLD_CROWN",
                nameAr = "تاج الذهب الملكي",
                nameEn = "Royal Gold Crown",
                descriptionAr = "إطار ذهبي متوهج للطلاب المتميزين",
                descriptionEn = "Radiant golden crown frame",
                iconOrColor = "#FFD700",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 25,
                requiredRank = "GOLD"
            ),
            UserCollectionEntity(
                itemType = "FRAME",
                itemId = "FRAME_COSMIC_DIAMOND",
                nameAr = "الماس الكوني",
                nameEn = "Cosmic Diamond",
                descriptionAr = "إطار ماسي بنفسجي سماوي فائق الفخامة",
                descriptionEn = "Ultra-premium celestial diamond frame",
                iconOrColor = "#8B5CF6",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 50,
                requiredRank = "PLATINUM"
            ),

            // Avatars
            UserCollectionEntity(
                itemType = "AVATAR",
                itemId = "AVATAR_STUDENT",
                nameAr = "الطالب الذكي",
                nameEn = "Smart Student",
                descriptionAr = "أيقونة الطالب النشيط",
                descriptionEn = "Active student avatar",
                iconOrColor = "🧑‍🎓",
                isUnlocked = true,
                isEquipped = true,
                requiredLevel = 1,
                requiredRank = "BRONZE"
            ),
            UserCollectionEntity(
                itemType = "AVATAR",
                itemId = "AVATAR_SCHOLAR",
                nameAr = "العالم الباحث",
                nameEn = "Research Scholar",
                descriptionAr = "أيقونة الباحث العلمي المجتهد",
                descriptionEn = "Dedicated scholar avatar",
                iconOrColor = "👨‍🏫",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 15,
                requiredRank = "SILVER"
            ),
            UserCollectionEntity(
                itemType = "AVATAR",
                itemId = "AVATAR_CHAMPION",
                nameAr = "بطل الالتزام",
                nameEn = "Commitment Champion",
                descriptionAr = "أيقونة بطل الإنجازات الأسطورية",
                descriptionEn = "Legendary achiever avatar",
                iconOrColor = "🦸",
                isUnlocked = false,
                isEquipped = false,
                requiredLevel = 30,
                requiredRank = "GOLD"
            )
        )
    }

    /**
     * Today's ISO Date String: YYYY-MM-DD
     */
    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
