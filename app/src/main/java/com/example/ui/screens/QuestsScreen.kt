package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.LevelHistoryEntity
import com.example.data.database.QuestEntity
import com.example.data.database.SystemLogEntity
import com.example.data.database.UserCollectionEntity
import com.example.domain.gamification.GamificationEngine
import com.example.domain.localization.AppLanguageManager
import com.example.domain.sound.SoundEffectsManager
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage != "ar"
    val lang = userSettings.appLanguage

    val quests by viewModel.quests.collectAsState()
    val levelHistory by viewModel.levelHistory.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()
    val userCollections by viewModel.userCollections.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var questFilter by remember { mutableStateOf("ALL") } // ALL, CORE, IMPORTANT, NORMAL, COMPLETED

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingQuest by remember { mutableStateOf<QuestEntity?>(null) }
    var showStreakRecoveryDialog by remember { mutableStateOf(false) }
    var showLevelUpCelebration by remember { mutableStateOf<Pair<Int, GamificationEngine.RankTier>?>(null) }
    var showRankUpCelebration by remember { mutableStateOf<GamificationEngine.RankTier?>(null) }

    val levelInfo = remember(userSettings.playerXp) {
        GamificationEngine.calculateLevelInfo(userSettings.playerXp)
    }

    val streakInfo = remember(quests, userSettings.dailyDeadlineHour, userSettings.dailyDeadlineMinute, userSettings.currentGlobalStreak, userSettings.streakShieldsCount) {
        GamificationEngine.evaluateStreakRisk(
            quests = quests,
            deadlineHour = userSettings.dailyDeadlineHour,
            deadlineMinute = userSettings.dailyDeadlineMinute,
            currentStreak = userSettings.currentGlobalStreak,
            bestStreak = userSettings.bestGlobalStreak,
            shieldsCount = userSettings.streakShieldsCount
        )
    }

    val filteredQuests = remember(quests, questFilter) {
        when (questFilter) {
            "CORE" -> quests.filter { it.questType in listOf("CORE", "MAIN") && !it.isCompleted }
            "IMPORTANT" -> quests.filter { it.questType == "IMPORTANT" && !it.isCompleted }
            "NORMAL" -> quests.filter { it.questType == "NORMAL" && !it.isCompleted }
            "COMPLETED" -> quests.filter { it.isCompleted }
            else -> quests
        }
    }

    val completedCount = remember(quests) { quests.count { it.isCompleted } }
    val totalCount = remember(quests) { quests.size }
    val dailyProgress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEnglish) "Adventure Quests & Goals ⚔️" else "مهام المغامرة والأهداف ⚔️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "رجوع"
                        )
                    }
                },
                actions = {
                    // Sound FX Toggle Button
                    IconButton(
                        onClick = {
                            val newSoundState = !userSettings.isSoundEffectsEnabled
                            coroutineScope.launch {
                                viewModel.setSoundEffectsEnabled(newSoundState)
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.BUTTON_CLICK, newSoundState)
                                Toast.makeText(
                                    context,
                                    if (newSoundState) (if (isEnglish) "Sound FX Enabled 🔊" else "تم تفعيل المؤثرات الصوتية 🔊")
                                    else (if (isEnglish) "Sound FX Muted 🔇" else "تم كتم المؤثرات الصوتية 🔇"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (userSettings.isSoundEffectsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Sound FX",
                            tint = if (userSettings.isSoundEffectsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Quest Quick Action
                    IconButton(onClick = {
                        editingQuest = null
                        showCreateDialog = true
                    }) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = if (isEnglish) "New Quest" else "مهمة جديدة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingQuest = null
                        showCreateDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (isEnglish) "New Quest" else "مهمة جديدة ⚔️", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Player Status & Level Card
            PlayerOverviewCard(
                levelInfo = levelInfo,
                streakInfo = streakInfo,
                userSettings = userSettings,
                isEnglish = isEnglish,
                onShieldClick = {
                    if (userSettings.streakShieldsCount > 0) {
                        coroutineScope.launch {
                            viewModel.useStreakShield()
                            SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SHIELD_USED, userSettings.isSoundEffectsEnabled)
                            Toast.makeText(context, if (isEnglish) "Shield activated! Daily streak is protected 🛡️" else "تم تفعيل الدرع! التتابع اليومي محمي بنجاح 🛡️", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, if (isEnglish) "No shields remaining! Complete quests to earn shields 🛡️" else "لا توجد دروع متبقية! أكمل المهام لكسب الدروع 🛡️", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 2. Streak Risk Alert Banner (If risk is active)
            if (streakInfo.isRiskActive) {
                StreakRiskWarningBanner(
                    streakInfo = streakInfo,
                    isEnglish = isEnglish,
                    onShieldClick = {
                        coroutineScope.launch {
                            if (userSettings.streakShieldsCount > 0) {
                                viewModel.useStreakShield()
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SHIELD_USED, userSettings.isSoundEffectsEnabled)
                                Toast.makeText(context, if (isEnglish) "Shield deployed! Streak safe 🛡️" else "تم نشر الدرع لحماية التتابع! 🛡️", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, if (isEnglish) "No shields! Complete your core quests now ⚡" else "لا تملك دروعاً! سارع بإكمال مهامك الأساسية الآن ⚡", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // 3. Navigation Tabs
            val tabs = listOf(
                if (isEnglish) "Quests ⚔️" else "المهام ⚔️",
                if (isEnglish) "Rank & Level 🏆" else "الرتبة والمستوى 🏆",
                if (isEnglish) "Shields 🛡️" else "الدروع والاستعادة 🛡️",
                if (isEnglish) "Collection 👑" else "المقتنيات 👑",
                if (isEnglish) "Stats 📊" else "الإحصائيات 📊",
                if (isEnglish) "Logs 📝" else "السجل 📝"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // 4. Tab Content
            when (selectedTab) {
                0 -> QuestsListTab(
                    quests = filteredQuests,
                    dailyProgress = dailyProgress,
                    completedCount = completedCount,
                    totalCount = totalCount,
                    selectedFilter = questFilter,
                    onFilterChange = { questFilter = it },
                    isEnglish = isEnglish,
                    onCompleteQuest = { quest ->
                        coroutineScope.launch {
                            val xpGained = quest.xpReward
                            val oldLvl = levelInfo.level
                            val oldRank = levelInfo.rank.rankId

                            viewModel.completeQuest(quest)

                            val newXp = userSettings.playerXp + xpGained
                            val newLvlInfo = GamificationEngine.calculateLevelInfo(newXp)

                            if (newLvlInfo.level > oldLvl) {
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.LEVEL_UP, userSettings.isSoundEffectsEnabled)
                                showLevelUpCelebration = Pair(newLvlInfo.level, newLvlInfo.rank)
                            } else if (newLvlInfo.rank.rankId != oldRank) {
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.RANK_UP, userSettings.isSoundEffectsEnabled)
                                showRankUpCelebration = newLvlInfo.rank
                            } else {
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.QUEST_COMPLETED, userSettings.isSoundEffectsEnabled)
                            }

                            Toast.makeText(
                                context,
                                if (isEnglish) "Quest Completed! +$xpGained XP ✨" else "أحسنت! اكتملت المهمة وحصلت على +$xpGained نقطة خبرة ✨",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onEditQuest = { quest ->
                        editingQuest = quest
                        showCreateDialog = true
                    },
                    onDeleteQuest = { quest ->
                        coroutineScope.launch {
                            viewModel.deleteQuest(quest)
                            SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SUCCESS, userSettings.isSoundEffectsEnabled)
                            Toast.makeText(context, if (isEnglish) "Quest deleted" else "تم حذف المهمة", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                1 -> LevelAndRankTab(
                    levelInfo = levelInfo,
                    levelHistory = levelHistory,
                    isEnglish = isEnglish
                )

                2 -> ShieldsAndRecoveryTab(
                    streakInfo = streakInfo,
                    userSettings = userSettings,
                    isEnglish = isEnglish,
                    onUseShield = {
                        coroutineScope.launch {
                            if (userSettings.streakShieldsCount > 0) {
                                viewModel.useStreakShield()
                                SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SHIELD_USED, userSettings.isSoundEffectsEnabled)
                                Toast.makeText(context, if (isEnglish) "Shield Used! 🛡️" else "تم تفعيل درع الحماية 🛡️", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onOpenRecovery = { showStreakRecoveryDialog = true }
                )

                3 -> CollectionsTab(
                    collections = userCollections,
                    userSettings = userSettings,
                    isEnglish = isEnglish,
                    onEquip = { item ->
                        coroutineScope.launch {
                            viewModel.equipCollectionItem(item)
                            SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SUCCESS, userSettings.isSoundEffectsEnabled)
                            Toast.makeText(context, if (isEnglish) "Equipped: ${item.nameEn} ✨" else "تم تجهيز: ${item.nameAr} ✨", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                4 -> StatisticsTab(
                    quests = quests,
                    levelInfo = levelInfo,
                    streakInfo = streakInfo,
                    userSettings = userSettings,
                    isEnglish = isEnglish
                )

                5 -> SystemLogsTab(
                    logs = systemLogs,
                    isEnglish = isEnglish,
                    onClearLogs = {
                        coroutineScope.launch {
                            viewModel.clearSystemLogs()
                            Toast.makeText(context, if (isEnglish) "Logs cleared" else "تم مسح السجل", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateOrEditQuestDialog(
            initialQuest = editingQuest,
            isEnglish = isEnglish,
            onDismiss = { showCreateDialog = false },
            onSave = { quest ->
                coroutineScope.launch {
                    if (editingQuest == null) {
                        viewModel.createQuest(quest)
                        SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SUCCESS, userSettings.isSoundEffectsEnabled)
                        Toast.makeText(context, if (isEnglish) "New Quest Created! ⚔️" else "تم إنشاء المهمة بنجاح ⚔️", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateQuest(quest)
                        SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.SUCCESS, userSettings.isSoundEffectsEnabled)
                        Toast.makeText(context, if (isEnglish) "Quest Updated" else "تم تعديل المهمة بنجاح", Toast.LENGTH_SHORT).show()
                    }
                    showCreateDialog = false
                }
            }
        )
    }

    if (showStreakRecoveryDialog) {
        StreakRecoveryChallengeDialog(
            isEnglish = isEnglish,
            onDismiss = { showStreakRecoveryDialog = false },
            onCompleteChallenge = {
                coroutineScope.launch {
                    viewModel.recoverStreak()
                    SoundEffectsManager.playSound(context, SoundEffectsManager.SoundType.STREAK_MILESTONE, userSettings.isSoundEffectsEnabled)
                    Toast.makeText(context, if (isEnglish) "Streak Recovered! 🔥" else "تمت استعادة الـ Streak بنجاح! 🔥", Toast.LENGTH_LONG).show()
                    showStreakRecoveryDialog = false
                }
            }
        )
    }

    showLevelUpCelebration?.let { (newLevel, rank) ->
        LevelUpCelebrationDialog(
            newLevel = newLevel,
            rank = rank,
            isEnglish = isEnglish,
            onDismiss = { showLevelUpCelebration = null }
        )
    }

    showRankUpCelebration?.let { newRank ->
        RankUpCelebrationDialog(
            rank = newRank,
            isEnglish = isEnglish,
            onDismiss = { showRankUpCelebration = null }
        )
    }
}

// ==========================================
// 1. Player Overview & Header Card
// ==========================================
@Composable
fun PlayerOverviewCard(
    levelInfo: GamificationEngine.LevelInfo,
    streakInfo: GamificationEngine.StreakInfo,
    userSettings: com.example.data.preferences.UserSettings,
    isEnglish: Boolean,
    onShieldClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Avatar + Frame + Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.5.dp,
                                color = try { Color(android.graphics.Color.parseColor(levelInfo.rank.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary },
                                shape = CircleShape
                            )
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userSettings.equippedAvatar.contains("SCHOLAR")) "👨‍🏫" else if (userSettings.equippedAvatar.contains("CHAMPION")) "🦸" else "🧑‍🎓",
                            fontSize = 26.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = userSettings.userName.ifBlank { if (isEnglish) "Hero Student" else "البطل المجتهد" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = userSettings.equippedTitle.ifBlank { if (isEnglish) "Diligent Student" else "طالب مجتهد" },
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Rank Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = try { Color(android.graphics.Color.parseColor(levelInfo.rank.colorHex)).copy(alpha = 0.15f) } catch (_: Exception) { MaterialTheme.colorScheme.primaryContainer }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = levelInfo.rank.icon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) levelInfo.rank.titleEn else levelInfo.rank.titleAr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = try { Color(android.graphics.Color.parseColor(levelInfo.rank.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Level XP Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (isEnglish) "Level" else "المستوى"} ${levelInfo.level}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${levelInfo.currentLevelXp} / ${levelInfo.xpRequiredForNextLevel} XP",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { levelInfo.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = try { Color(android.graphics.Color.parseColor(levelInfo.rank.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Quick Row: Streak & Shields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Streak Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = streakInfo.tierIcon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${streakInfo.currentStreak} ${if (isEnglish) "Days Streak" else "أيام تتابع"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) streakInfo.tierNameEn else streakInfo.tierNameAr,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Shields Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onShieldClick),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🛡️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${userSettings.streakShieldsCount} ${if (isEnglish) "Shields" else "دروع حماية"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) "Tap to Protect" else "اضغط للحماية",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. Streak Risk Warning Banner
// ==========================================
@Composable
fun StreakRiskWarningBanner(
    streakInfo: GamificationEngine.StreakInfo,
    isEnglish: Boolean,
    onShieldClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⚠️", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Streak at Risk! ⏳" else "تنبيه: التتابع اليومي في خطر! ⏳",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (isEnglish) {
                        "Remaining: ~${streakInfo.hoursRemainingToDeadline}h to deadline. Complete core quests or use a shield."
                    } else {
                        "متبقي ~${streakInfo.hoursRemainingToDeadline} ساعة حتى الموعد النهائي. أكمل المهام الأساسية أو استخدم درعاً."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onShieldClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = if (isEnglish) "Shield 🛡️" else "درع 🛡️", fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// 3. Quests List Tab
// ==========================================
@Composable
fun QuestsListTab(
    quests: List<QuestEntity>,
    dailyProgress: Float,
    completedCount: Int,
    totalCount: Int,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    isEnglish: Boolean,
    onCompleteQuest: (QuestEntity) -> Unit,
    onEditQuest: (QuestEntity) -> Unit,
    onDeleteQuest: (QuestEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Daily Progress Bar Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) "Daily Quests Progress" else "إنجاز مهام اليوم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "$completedCount / $totalCount (${(dailyProgress * 100).toInt()}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { dailyProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Filter Chips Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to if (isEnglish) "All (${quests.size})" else "الكل (${quests.size})",
                    "CORE" to if (isEnglish) "Core 💎" else "أساسية 💎",
                    "IMPORTANT" to if (isEnglish) "Important ⭐" else "مهمة ⭐",
                    "NORMAL" to if (isEnglish) "Normal 🎯" else "عادية 🎯",
                    "COMPLETED" to if (isEnglish) "Completed ✅" else "مكتملة ✅"
                )

                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { onFilterChange(key) },
                        label = { Text(label, fontSize = 11.5.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        if (quests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎯", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "No quests found in this category!" else "لا توجد مهام في هذا التصنيف حالياً!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnglish) "Tap '+ New Quest' to create your first mission" else "اضغط على '+ مهمة جديدة' لإنشاء هدفك التالي",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(quests, key = { it.id }) { quest ->
                QuestItemCard(
                    quest = quest,
                    isEnglish = isEnglish,
                    onComplete = { onCompleteQuest(quest) },
                    onEdit = { onEditQuest(quest) },
                    onDelete = { onDeleteQuest(quest) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// ==========================================
// 4. Quest Item Card
// ==========================================
@Composable
fun QuestItemCard(
    quest: QuestEntity,
    isEnglish: Boolean,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeBadgeColor = when (quest.questType) {
        "CORE", "MAIN" -> Color(0xFF9C27B0) // Purple
        "IMPORTANT" -> Color(0xFFE65100) // Orange
        else -> Color(0xFF0288D1) // Blue
    }

    val typeLabel = when (quest.questType) {
        "CORE" -> if (isEnglish) "Core 💎" else "أساسية 💎"
        "MAIN" -> if (isEnglish) "Main Quest ⚔️" else "مهمة رئيسية ⚔️"
        "IMPORTANT" -> if (isEnglish) "Important ⭐" else "مهمة ⭐"
        else -> if (isEnglish) "Normal 🎯" else "عادية 🎯"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quest.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (quest.isCompleted) 0.dp else 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Type & Category Tags
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeBadgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = quest.category,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Streak & Rewards Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (quest.currentStreak > 0) {
                        Text(
                            text = "🔥 ${quest.currentStreak}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFBC02D).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "+${quest.xpReward} XP",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF57F17),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quest Title & Description
            Text(
                text = quest.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (quest.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )

            if (quest.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = quest.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Deadline & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Deadline time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", quest.deadlineHour, quest.deadlineMinute),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }

                    if (!quest.isCompleted) {
                        Button(
                            onClick = onComplete,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isEnglish) "Complete" else "إكمال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "Completed" else "مكتملة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. Level & Rank Tab
// ==========================================
@Composable
fun LevelAndRankTab(
    levelInfo: GamificationEngine.LevelInfo,
    levelHistory: List<LevelHistoryEntity>,
    isEnglish: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Current Rank Highlight
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = levelInfo.rank.icon, fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isEnglish) levelInfo.rank.titleEn else levelInfo.rank.titleAr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = try { Color(android.graphics.Color.parseColor(levelInfo.rank.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                    )
                    Text(
                        text = "${if (isEnglish) "Player Level" else "مستوى الطالب"}: ${levelInfo.level}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${levelInfo.totalXp} ${if (isEnglish) "Total XP Earned" else "إجمالي نقاط الخبرة المكتسبة"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // All Ranks Ladder
        item {
            Text(
                text = if (isEnglish) "🏆 Rank Progression Ladder" else "🏆 سلم الترقية والرتب الشرفية",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        items(GamificationEngine.RankTier.values()) { rank ->
            val isCurrent = rank == levelInfo.rank
            val isUnlocked = levelInfo.level >= rank.minLevel

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = rank.icon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) rank.titleEn else rank.titleAr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${if (isEnglish) "Requires Level" else "يتطلب المستوى"} ${rank.minLevel}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (isEnglish) "Current Rank" else "رتبتك الحالية",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isUnlocked) {
                        Text(text = "✅", fontSize = 16.sp)
                    } else {
                        Text(text = "🔒", fontSize = 16.sp)
                    }
                }
            }
        }

        // Level History Log
        if (levelHistory.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "📜 Level Progression History" else "📜 سجل الترقيات السابقة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            items(levelHistory) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${if (isEnglish) "Level Up" else "ترقية إلى المستوى"} ${entry.newLevel} 🎉",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = entry.reason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(entry.timestampMillis))
                        Text(text = dateStr, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. Shields & Recovery Tab
// ==========================================
@Composable
fun ShieldsAndRecoveryTab(
    streakInfo: GamificationEngine.StreakInfo,
    userSettings: com.example.data.preferences.UserSettings,
    isEnglish: Boolean,
    onUseShield: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Shields Explanation Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛡️", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Streak Protection Shields" else "نظام دروع حماية الـ Streak",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${userSettings.streakShieldsCount} ${if (isEnglish) "Active Shields Available" else "دروع حماية مفعلة ومتاحة"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isEnglish) {
                            "Shields automatically protect your consecutive daily streak from breaking if you fail to complete your Core Quests before the daily deadline (${String.format(Locale.getDefault(), "%02d:%02d", userSettings.dailyDeadlineHour, userSettings.dailyDeadlineMinute)})."
                        } else {
                            "تحميك الدروع تلقائياً من انكسار تتابعك اليومي المتتالي إذا لم تتمكن من إكمال مهامك الأساسية قبل الموعد النهائي المحدد (${String.format(Locale.getDefault(), "%02d:%02d", userSettings.dailyDeadlineHour, userSettings.dailyDeadlineMinute)})."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onUseShield,
                        enabled = userSettings.streakShieldsCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Activate Protection Shield Now 🛡️" else "تفعيل درع الحماية يدوياً 🛡️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // Streak Recovery Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔄", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Streak Recovery Center" else "مركز استعادة الـ Streak المفقود",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isEnglish) "One-chance restoration challenge" else "فرصة استعادة فورية عبر التحدي السريع",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isEnglish) {
                            "Did you miss a day and lose your streak? Complete a quick focus & study challenge to restore your previous streak records!"
                        } else {
                            "هل فاتك يوم وانكسر تتابعك الدراسي؟ يمكنك إكمال تحدي تركيز واستذكار سريع لاسترجاع رقمك القياسي المفقود وحماية تقدمك!"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onOpenRecovery,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Start Streak Recovery Challenge 🚀" else "بدء تحدي استعادة التتابع 🚀",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. Collections & Titles Tab
// ==========================================
@Composable
fun CollectionsTab(
    collections: List<UserCollectionEntity>,
    userSettings: com.example.data.preferences.UserSettings,
    isEnglish: Boolean,
    onEquip: (UserCollectionEntity) -> Unit
) {
    val titles = remember(collections) { collections.filter { it.itemType == "TITLE" } }
    val frames = remember(collections) { collections.filter { it.itemType == "FRAME" } }
    val avatars = remember(collections) { collections.filter { it.itemType == "AVATAR" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Honor Titles
        item {
            Text(
                text = if (isEnglish) "👑 Honor Titles" else "👑 الألقاب الشرفية",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(titles) { item ->
            val isEquipped = userSettings.equippedTitle == item.nameAr || userSettings.equippedTitle == item.nameEn || item.isEquipped
            CollectionItemCard(item = item, isEquipped = isEquipped, isEnglish = isEnglish, onEquip = { onEquip(item) })
        }

        // Section 2: Avatar Frames
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isEnglish) "🖼️ Avatar Frames" else "🖼️ إطارات الأفاتار",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(frames) { item ->
            val isEquipped = userSettings.equippedFrame == item.itemId || item.isEquipped
            CollectionItemCard(item = item, isEquipped = isEquipped, isEnglish = isEnglish, onEquip = { onEquip(item) })
        }

        // Section 3: Avatars
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isEnglish) "🧑‍🎓 Avatars" else "🧑‍🎓 صور وشخصيات الأفاتار",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(avatars) { item ->
            val isEquipped = userSettings.equippedAvatar == item.itemId || item.isEquipped
            CollectionItemCard(item = item, isEquipped = isEquipped, isEnglish = isEnglish, onEquip = { onEquip(item) })
        }
    }
}

@Composable
fun CollectionItemCard(
    item: UserCollectionEntity,
    isEquipped: Boolean,
    isEnglish: Boolean,
    onEquip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEquipped) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = item.iconOrColor.ifBlank { "⭐" }, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEnglish) item.nameEn else item.nameAr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = if (isEnglish) item.descriptionEn else item.descriptionAr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isEquipped) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = if (isEnglish) "Equipped ✅" else "مجهز ✅",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else if (item.isUnlocked) {
                OutlinedButton(
                    onClick = onEquip,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(text = if (isEnglish) "Equip" else "تجهيز", fontSize = 11.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "🔒 ${if (isEnglish) "Lvl" else "مستوى"} ${item.requiredLevel}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 8. Statistics & Analytics Tab
// ==========================================
@Composable
fun StatisticsTab(
    quests: List<QuestEntity>,
    levelInfo: GamificationEngine.LevelInfo,
    streakInfo: GamificationEngine.StreakInfo,
    userSettings: com.example.data.preferences.UserSettings,
    isEnglish: Boolean
) {
    val totalQuests = quests.size
    val completedQuests = quests.count { it.isCompleted }
    val coreCount = quests.count { it.questType in listOf("CORE", "MAIN") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = if (isEnglish) "📊 Overall Performance Analytics" else "📊 تحليلات الأداء والإنجاز العام",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Stats Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMetricCard(
                    title = if (isEnglish) "Total XP" else "إجمالي الخبرة",
                    value = "${levelInfo.totalXp} XP",
                    icon = "⚡",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = if (isEnglish) "Current Level" else "المستوى الحالي",
                    value = "Lv. ${levelInfo.level}",
                    icon = "🎓",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMetricCard(
                    title = if (isEnglish) "Current Streak" else "التتابع الحالي",
                    value = "${streakInfo.currentStreak} ${if (isEnglish) "days" else "يوم"}",
                    icon = "🔥",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = if (isEnglish) "Best Streak" else "أفضل تتابع",
                    value = "${streakInfo.bestStreak} ${if (isEnglish) "days" else "يوم"}",
                    icon = "🌟",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMetricCard(
                    title = if (isEnglish) "Completed Quests" else "المهام المكتملة",
                    value = "$completedQuests / $totalQuests",
                    icon = "✅",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = if (isEnglish) "Core Missions" else "المهام الأساسية",
                    value = "$coreCount",
                    icon = "💎",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ==========================================
// 9. System Logs Tab
// ==========================================
@Composable
fun SystemLogsTab(
    logs: List<SystemLogEntity>,
    isEnglish: Boolean,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEnglish) "📝 System Activity Events" else "📝 سجل أنشطة وأحداث النظام",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearLogs) {
                    Text(if (isEnglish) "Clear" else "مسح السجل", color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isEnglish) "No logs recorded yet" else "لم يتم تسجيل أحداث حتى الآن",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = log.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                if (log.details.isNotBlank()) {
                                    Text(text = log.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestampMillis))
                            Text(text = dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. Create / Edit Quest Dialog
// ==========================================
@Composable
fun CreateOrEditQuestDialog(
    initialQuest: QuestEntity?,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuestEntity) -> Unit
) {
    var title by remember { mutableStateOf(initialQuest?.title ?: "") }
    var description by remember { mutableStateOf(initialQuest?.description ?: "") }
    var category by remember { mutableStateOf(initialQuest?.category ?: "STUDY") }
    var questType by remember { mutableStateOf(initialQuest?.questType ?: "NORMAL") }
    var difficulty by remember { mutableStateOf(initialQuest?.difficulty ?: "MEDIUM") }
    var deadlineHour by remember { mutableIntStateOf(initialQuest?.deadlineHour ?: 22) }
    var deadlineMinute by remember { mutableIntStateOf(initialQuest?.deadlineMinute ?: 0) }

    val xpReward = remember(difficulty, questType) {
        val base = when (difficulty) {
            "EASY" -> 40
            "MEDIUM" -> 70
            "HARD" -> 110
            "EPIC" -> 160
            else -> 60
        }
        val mult = if (questType in listOf("CORE", "MAIN")) 1.5f else if (questType == "IMPORTANT") 1.25f else 1.0f
        (base * mult).toInt()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initialQuest == null) (if (isEnglish) "Create New Quest ⚔️" else "إنشاء مهمة جديدة ⚔️")
                    else (if (isEnglish) "Edit Quest ✏️" else "تعديل المهمة ✏️"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isEnglish) "Quest Title" else "عنوان المهمة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (isEnglish) "Description & Details" else "الوصف والتفاصيل") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quest Type Selection
                Text(text = if (isEnglish) "Type:" else "نوع المهمة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NORMAL" to (if (isEnglish) "Normal" else "عادية"), "IMPORTANT" to (if (isEnglish) "Important" else "مهمة"), "CORE" to (if (isEnglish) "Core 💎" else "أساسية 💎")).forEach { (tKey, tLabel) ->
                        FilterChip(
                            selected = questType == tKey,
                            onClick = { questType = tKey },
                            label = { Text(tLabel, fontSize = 11.sp) }
                        )
                    }
                }

                // Difficulty Selection
                Text(text = if (isEnglish) "Difficulty:" else "مستوى الصعوبة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("EASY" to (if (isEnglish) "Easy" else "سهلة"), "MEDIUM" to (if (isEnglish) "Medium" else "متوسطة"), "HARD" to (if (isEnglish) "Hard" else "صعبة")).forEach { (dKey, dLabel) ->
                        FilterChip(
                            selected = difficulty == dKey,
                            onClick = { difficulty = dKey },
                            label = { Text(dLabel, fontSize = 11.sp) }
                        )
                    }
                }

                Text(
                    text = "${if (isEnglish) "Estimated Reward" else "المكافأة التقديرية"}: +$xpReward XP ✨",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF57F17)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isEnglish) "Cancel" else "إلغاء")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    initialQuest?.copy(
                                        title = title,
                                        description = description,
                                        category = category,
                                        questType = questType,
                                        difficulty = difficulty,
                                        xpReward = xpReward,
                                        deadlineHour = deadlineHour,
                                        deadlineMinute = deadlineMinute
                                    ) ?: QuestEntity(
                                        title = title,
                                        description = description,
                                        category = category,
                                        questType = questType,
                                        difficulty = difficulty,
                                        xpReward = xpReward,
                                        deadlineHour = deadlineHour,
                                        deadlineMinute = deadlineMinute
                                    )
                                )
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(if (isEnglish) "Save Quest" else "حفظ المهمة")
                    }
                }
            }
        }
    }
}

// ==========================================
// 11. Level Up Celebration Dialog
// ==========================================
@Composable
fun LevelUpCelebrationDialog(
    newLevel: Int,
    rank: GamificationEngine.RankTier,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🎉", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "LEVEL UP!" else "ارتقاء في المستوى! 🎉",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${if (isEnglish) "You reached Level" else "لقد وصلت إلى المستوى"} $newLevel!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (isEnglish) "Current Rank" else "رتبتك الشرفية"}: ${if (isEnglish) rank.titleEn else rank.titleAr} ${rank.icon}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEnglish) "Awesome! 🚀" else "رائع، استمر! 🚀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 12. Rank Up Celebration Dialog
// ==========================================
@Composable
fun RankUpCelebrationDialog(
    rank: GamificationEngine.RankTier,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = rank.icon, fontSize = 56.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "RANK UP!" else "ترقية رتبة شرفية! 👑",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = try { Color(android.graphics.Color.parseColor(rank.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnglish) rank.titleEn else rank.titleAr,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEnglish) "Claim Honor! 🏆" else "استلام التكريم! 🏆", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 13. Streak Recovery Challenge Dialog
// ==========================================
@Composable
fun StreakRecoveryChallengeDialog(
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onCompleteChallenge: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "⚡", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnglish) "Streak Recovery Challenge" else "تحدي استعادة التتابع ⚡",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Complete 1 study mission right now or review your flashcards to recover your lost streak and keep progressing!"
                    } else {
                        "قم بإنجاز جلسة مذاكرة سريعة الآن أو مراجعة بطاقاتك لاستعادة التتابع اليومي المفقود ومواصلة رحلتك نحو القمة!"
                    },
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCompleteChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEnglish) "Complete & Restore Streak 🔥" else "إتمام واستعادة التتابع 🔥", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
