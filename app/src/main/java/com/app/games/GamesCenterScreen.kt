package com.app.games

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val RoyalBlue = Color(0xFF1A6FE3)
private val DeepRoyalBlue = Color(0xFF1252B0)
private val GoldAccent = Color(0xFFD97706)
private val GoldSoftBg = Color(0xFFFEF3C7)
private val EmeraldSoftBg = Color(0xFFD1FAE5)
private val SkySoftBg = Color(0xFFDBEAFE)
private val HubCanvasBg = Color(0xFFF8FAFC)

private enum class GamesHubTab(val titleAr: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Default.Home),
    MAP("الخريطة", Icons.Default.Map),
    CHALLENGES("التحديات", Icons.Default.EmojiEvents),
    STORE("المتجر", Icons.Default.Storefront)
}

@Composable
fun GamesCenterScreen(
    viewModel: GamesHubViewModel,
    onNavigateBackToApp: () -> Unit
) {
    val shopState by viewModel.shopState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(GamesHubTab.HOME) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdfForGame(uri)
        }
    }

    BackHandler {
        if (activeTab != GamesHubTab.HOME) {
            activeTab = GamesHubTab.HOME
        } else {
            onNavigateBackToApp()
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null && !uiState.isGeneratingQuestions) {
            delay(3600)
            viewModel.clearStatusBanner()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = HubCanvasBg,
            topBar = {
                GamesCenterTopBar(
                    coins = shopState.coins,
                    unlockedStage = shopState.unlockedStage,
                    onBack = onNavigateBackToApp,
                    onOpenMap = { activeTab = GamesHubTab.MAP }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    GamesHubTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { activeTab = tab },
                            modifier = Modifier.testTag("games_tab_${tab.name.lowercase()}"),
                            icon = { Icon(tab.icon, contentDescription = tab.titleAr) },
                            label = {
                                Text(
                                    text = tab.titleAr,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RoyalBlue,
                                selectedTextColor = RoyalBlue,
                                indicatorColor = SkySoftBg,
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isGeneratingQuestions) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFACC15),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = uiState.statusBannerMessage.orEmpty(),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearStatusBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                            }
                        }
                    }
                }

                when (activeTab) {
                    GamesHubTab.HOME -> GamesHomeTabContent(
                        shopState = shopState,
                        uiState = uiState,
                        onPlayNow = { viewModel.startStage(uiState.selectedStageNumber) },
                        onSelectStage = viewModel::selectStage,
                        onSelectGrade = viewModel::selectGrade,
                        onSelectSubject = viewModel::selectSubject,
                        onSelectChapter = viewModel::selectChapter,
                        onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
                        onClearPdf = viewModel::clearCustomPdf,
                        onBuyOrEquip = viewModel::buyOrEquipCharacter,
                        onOpenFullStore = { activeTab = GamesHubTab.STORE },
                        onOpenMap = { activeTab = GamesHubTab.MAP }
                    )
                    GamesHubTab.MAP -> StageProgressionMapTab(
                        shopState = shopState,
                        selectedStage = uiState.selectedStageNumber,
                        onPlayStage = { stageNum ->
                            viewModel.selectStage(stageNum)
                            if (stageNum <= shopState.unlockedStage) {
                                viewModel.startStage(stageNum)
                            }
                        }
                    )
                    GamesHubTab.CHALLENGES -> ChallengesAndStatsTab(
                        shopState = shopState,
                        onPlayStage = { stageNum ->
                            if (stageNum <= shopState.unlockedStage) viewModel.startStage(stageNum)
                        }
                    )
                    GamesHubTab.STORE -> FullShopAndPowerUpsTab(
                        shopState = shopState,
                        onBuyOrEquip = viewModel::buyOrEquipCharacter,
                        onUpgradePowerUp = viewModel::upgradePowerUp
                    )
                }
            }
        }
    }
}

@Composable
private fun GamesCenterTopBar(
    coins: Int,
    unlockedStage: Int,
    onBack: () -> Unit,
    onOpenMap: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("games_hub_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "العودة لتطبيق متعلم",
                    tint = Color(0xFF0F172A)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مركز الألعاب التعليمية",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ركض المتفوقين • $unlockedStage / $TOTAL_GAME_STAGES مرحلة مفتوحة",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            // Stage Map Quick Button
            Surface(
                onClick = onOpenMap,
                shape = CircleShape,
                color = SkySoftBg,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("games_open_map_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "خريطة المراحل",
                        tint = RoyalBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Golden Coin Balance Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = GoldSoftBg,
                border = BorderStroke(1.dp, Color(0xFFFCD34D))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ع", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$coins عملة",
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GamesHomeTabContent(
    shopState: GameShopState,
    uiState: GamesHubUiState,
    onPlayNow: () -> Unit,
    onSelectStage: (Int) -> Unit,
    onSelectGrade: (HighSchoolGrade) -> Unit,
    onSelectSubject: (HighSchoolSubject) -> Unit,
    onSelectChapter: (String) -> Unit,
    onPickPdf: () -> Unit,
    onClearPdf: () -> Unit,
    onBuyOrEquip: (GameCharacterId) -> Unit,
    onOpenFullStore: () -> Unit,
    onOpenMap: () -> Unit
) {
    val activeStageSpec = StageThemeEngine.stageFor(uiState.selectedStageNumber)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Hero Instant Play Button (#1A6FE3) matching Reference Screen 1
        item {
            Card(
                onClick = onPlayNow,
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("games_instant_play_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(RoyalBlue, DeepRoyalBlue)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "العب فوراً • تحدي سريع",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        val modeLabel = if (uiState.isUsingCustomPdf && uiState.customPdfTitle != null) {
                            "المرحلة ${activeStageSpec.number}: ${activeStageSpec.nameAr} • ملف: ${uiState.customPdfTitle}"
                        } else {
                            "المرحلة ${activeStageSpec.number}: ${activeStageSpec.nameAr} • ${uiState.selectedSubject.titleAr} (${uiState.selectedChapter})"
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = modeLabel,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. High School Curriculum Filter Tree (Grade -> 11 Subjects -> Chapters + Upload PDF)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر المرحلة ($TOTAL_GAME_STAGES مرحلة) ومنهج الأسئلة (٢٢٠+ سؤال)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "خريطة المراحل 🗺️",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenMap)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                // Quick Stage Picker Row (1..20)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StageThemeEngine.allStages) { stage ->
                        val unlocked = stage.number <= shopState.unlockedStage
                        val selected = uiState.selectedStageNumber == stage.number
                        CurriculumFilterPill(
                            label = if (unlocked) "مرحلة ${stage.number}: ${stage.nameAr}" else "🔒 مرحلة ${stage.number}: ${stage.nameAr}",
                            selected = selected,
                            selectedBg = GoldAccent,
                            unselectedBg = if (unlocked) GoldSoftBg else Color(0xFFE2E8F0),
                            selectedText = Color.White,
                            unselectedText = if (unlocked) Color(0xFF92400E) else Color(0xFF64748B),
                            onClick = { onSelectStage(stage.number) }
                        )
                    }
                }

                // Grade Filter Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HighSchoolGrade.entries) { grade ->
                        val selected = !uiState.isUsingCustomPdf && uiState.selectedGrade == grade
                        CurriculumFilterPill(
                            label = grade.titleAr,
                            selected = selected,
                            selectedBg = RoyalBlue,
                            unselectedBg = SkySoftBg,
                            selectedText = Color.White,
                            unselectedText = Color(0xFF1E3A8A),
                            onClick = { onSelectGrade(grade) }
                        )
                    }
                    item {
                        CurriculumFilterPill(
                            label = if (uiState.isUsingCustomPdf) "PDF نشط ✓" else "اللعب من ملفاتي / رفع PDF",
                            selected = uiState.isUsingCustomPdf,
                            selectedBg = Color(0xFFDC2626),
                            unselectedBg = Color(0xFFFEE2E2),
                            selectedText = Color.White,
                            unselectedText = Color(0xFF991B1B),
                            leadingIcon = Icons.Default.PictureAsPdf,
                            onClick = onPickPdf
                        )
                    }
                }

                // 11 High School Subjects Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HighSchoolSubject.entries) { subject ->
                        val selected = !uiState.isUsingCustomPdf && uiState.selectedSubject == subject
                        CurriculumFilterPill(
                            label = subject.titleAr,
                            selected = selected,
                            selectedBg = Color(0xFF059669),
                            unselectedBg = EmeraldSoftBg,
                            selectedText = Color.White,
                            unselectedText = Color(0xFF065F46),
                            onClick = { onSelectSubject(subject) }
                        )
                    }
                }

                // Chapters Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.selectedSubject.chapters) { chapter ->
                        val selected = !uiState.isUsingCustomPdf && uiState.selectedChapter == chapter
                        CurriculumFilterPill(
                            label = chapter,
                            selected = selected,
                            selectedBg = Color(0xFF0F172A),
                            unselectedBg = Color(0xFFE2E8F0),
                            selectedText = Color.White,
                            unselectedText = Color(0xFF334155),
                            onClick = { onSelectChapter(chapter) }
                        )
                    }
                    if (uiState.isUsingCustomPdf) {
                        item {
                            CurriculumFilterPill(
                                label = "العودة للمنهج المدمج",
                                selected = false,
                                selectedBg = RoyalBlue,
                                unselectedBg = Color(0xFFE2E8F0),
                                selectedText = Color.White,
                                unselectedText = Color(0xFF0F172A),
                                onClick = onClearPdf
                            )
                        }
                    }
                }
            }
        }

        // 3. Character Shop Header + 2-Column 6-Character Grid matching Reference Screen 1 & 2
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "متجر الشخصيات",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "القدرات والترقيات ➔",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenFullStore)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        items(GameCharacterId.entries.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { character ->
                    CharacterShopCard(
                        character = character,
                        isOwned = character in shopState.ownedCharacters,
                        isEquipped = shopState.equippedCharacter == character,
                        canAfford = shopState.coins >= character.price,
                        onClick = { onBuyOrEquip(character) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CurriculumFilterPill(
    label: String,
    selected: Boolean,
    selectedBg: Color,
    unselectedBg: Color,
    selectedText: Color,
    unselectedText: Color,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) selectedBg else unselectedBg,
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) selectedText else unselectedText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (selected) selectedText else unselectedText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CharacterShopCard(
    character: GameCharacterId,
    isOwned: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isEquipped -> RoyalBlue
        isOwned -> Color(0xFF10B981)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (isEquipped) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEquipped) 5.dp else 2.dp),
        modifier = modifier.testTag("character_card_${character.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CharacterPortraitCanvas(
                character = character,
                modifier = Modifier.size(112.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = character.titleAr,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = character.perkSummaryAr,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(30.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                isEquipped -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldSoftBg)
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "مُفعَّل",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "مُفعَّل",
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
                isOwned -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SkySoftBg)
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "تفعيل الشخصية",
                            color = RoyalBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (canAfford) GoldSoftBg else Color(0xFFF1F5F9))
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ع", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${character.price} عملة",
                            color = if (canAfford) Color(0xFF92400E) else Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageProgressionMapTab(
    shopState: GameShopState,
    selectedStage: Int,
    onPlayStage: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "خريطة المراحل الـ $TOTAL_GAME_STAGES المتدرجة 🗺️",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "تتغير البيئة البصرية الثلاثية الأبعاد وبوابات الأسئلة والمضاعفات مع كل مرحلة حتى عرش أسطورة الثانوية!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { shopState.unlockedStage / TOTAL_GAME_STAGES.toFloat() },
                        color = Color(0xFFFACC15),
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        items(StageThemeEngine.allStages) { spec ->
            val unlocked = spec.number <= shopState.unlockedStage
            val completed = spec.number in shopState.completedStages
            val palette = StageThemeEngine.paletteFor(spec.number)
            val isCurrent = spec.number == selectedStage

            Card(
                onClick = { onPlayStage(spec.number) },
                shape = RoundedCornerShape(20.dp),
                border = if (isCurrent) BorderStroke(2.dp, GoldAccent) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = if (unlocked) 4.dp else 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stage_map_item_${spec.number}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (unlocked) {
                                    listOf(palette.skyTop, palette.skyBottom)
                                } else {
                                    listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0))
                                }
                            )
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val textColor = when {
                        !unlocked -> Color(0xFF475569)
                        palette.isDarkEnvironment -> Color.White
                        else -> Color(0xFF0F172A)
                    }
                    val subTextColor = when {
                        !unlocked -> Color(0xFF64748B)
                        palette.isDarkEnvironment -> Color(0xFFE2E8F0)
                        else -> Color(0xFF334155)
                    }

                    // Stage Number Emblem
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (unlocked) Color(0xFFF59E0B) else Color(0xFF94A3B8))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (unlocked) {
                            Text(
                                text = "${spec.number}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Icon(Icons.Default.Lock, contentDescription = "مغلقة", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "المرحلة ${spec.number} • ${spec.nameAr}",
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (completed) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "مكتملة",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = spec.environmentSummaryAr,
                            color = subTextColor,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StageStatChip("سرعة ${spec.speedMultiplier}x")
                            StageStatChip("عملات ${spec.coinMultiplier}x")
                            StageStatChip("+${spec.bossRewardCoins} 🪙")
                        }
                    }

                    if (unlocked) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "ابدأ المرحلة",
                                    tint = RoyalBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageStatChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.25f)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ChallengesAndStatsTab(
    shopState: GameShopState,
    onPlayStage: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "إحصائيات المتفوق 🏆",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricBox("أعلى نقاط", "${shopState.highScore}", RoyalBlue, Modifier.weight(1f))
                        MetricBox("أعلى Combo", "x${shopState.bestCombo}", GoldAccent, Modifier.weight(1f))
                        MetricBox("الزعماء", "${shopState.totalBossesDefeated}", Color(0xFF10B981), Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                text = "مصفوفة تحديات الزعماء وصناديق الكنز",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
        }

        items(StageThemeEngine.allStages) { spec ->
            val unlocked = spec.number <= shopState.unlockedStage
            val completed = spec.number in shopState.completedStages
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (completed) Color(0xFF10B981) else Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المرحلة ${spec.number}: ${spec.nameAr}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = spec.bossChallengeTitleAr,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "مكافأة الصندوق: +${spec.bossRewardCoins} عملة ذهبية",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                    }
                    Button(
                        onClick = { onPlayStage(spec.number) },
                        enabled = unlocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (completed) Color(0xFF059669) else RoyalBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                !unlocked -> "مغلق"
                                completed -> "إعادة"
                                else -> "تحدَّ"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(title: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.1f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        Text(title, fontSize = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FullShopAndPowerUpsTab(
    shopState: GameShopState,
    onBuyOrEquip: (GameCharacterId) -> Unit,
    onUpgradePowerUp: (PowerUpType) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "الشخصيات الست القابلة للشراء والتبديل",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
        }

        items(GameCharacterId.entries.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { character ->
                    CharacterShopCard(
                        character = character,
                        isOwned = character in shopState.ownedCharacters,
                        isEquipped = shopState.equippedCharacter == character,
                        canAfford = shopState.coins >= character.price,
                        onClick = { onBuyOrEquip(character) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ترقية القدرات المساعدة (Power-Ups)",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
        }

        items(PowerUpType.entries) { powerUp ->
            val level = shopState.powerUpLevel(powerUp)
            val isMax = level >= powerUp.maxLevel
            val cost = powerUp.upgradePrice(level)
            val icon = when (powerUp) {
                PowerUpType.COIN_MAGNET -> Icons.Default.Bolt
                PowerUpType.KNOWLEDGE_SHIELD -> Icons.Default.Shield
                PowerUpType.GATE_DETECTOR -> Icons.Default.RemoveRedEye
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("powerup_card_${powerUp.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(powerUp.accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = powerUp.titleAr, tint = powerUp.accentColor, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${powerUp.titleAr} • المستوى $level/${powerUp.maxLevel}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = powerUp.descriptionAr,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { level.toFloat() / powerUp.maxLevel.toFloat() },
                            color = powerUp.accentColor,
                            trackColor = Color(0xFFE2E8F0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onUpgradePowerUp(powerUp) },
                        enabled = !isMax,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (shopState.coins >= cost) GoldAccent else Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isMax) "أقصى مستوى" else "$cost 🪙",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
