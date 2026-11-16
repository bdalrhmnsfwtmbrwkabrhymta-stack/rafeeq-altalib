package com.hawas.study.dofaati.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiBatch
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.models.DofaatiSubject
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiAmiriTitleText
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun DofaatiNavigation(
    onNavigateBackToApp: () -> Unit,
    onFullScreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: DofaatiViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )
    val backStack by viewModel.backStack.collectAsStateWithLifecycle()
    val currentRoute = backStack.lastOrNull() ?: DofaatiRoute.HUB
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    LaunchedEffect(currentRoute) {
        onFullScreenChanged(currentRoute.isFullScreen)
    }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChanged(false) }
    }

    BackHandler(enabled = backStack.size > 1) {
        viewModel.navigateBack()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize().background(DofaatiColors.Background)) {
            when (currentRoute) {
                DofaatiRoute.HUB -> DofaatiHubScreen(
                    viewModel = viewModel,
                    onNavigateBackToApp = onNavigateBackToApp
                )
                DofaatiRoute.CREATE_BATCH -> CreateBatchScreen(viewModel = viewModel)
                DofaatiRoute.CHAT_AND_ADMIN -> BatchChatAndAdminContainerScreen(viewModel = viewModel)
                DofaatiRoute.SUBJECT_DETAILS -> SubjectResourcesScreen(viewModel = viewModel)
                DofaatiRoute.AUDIO_TRANSCRIPT -> AudioTranscriptSummaryScreen(viewModel = viewModel)
                DofaatiRoute.MIND_MAP_FLASHCARDS -> MindMapAndFlashcardsScreen(viewModel = viewModel)
                DofaatiRoute.BULK_AI_GENERATOR -> BulkAiQuestionGeneratorScreen(viewModel = viewModel)
                DofaatiRoute.QUESTION_BANK -> ComprehensiveQuestionBankScreen(viewModel = viewModel)
                DofaatiRoute.ADMIN_EXAM_CREATOR -> BatchAdminExamCreatorScreen(viewModel = viewModel)
                DofaatiRoute.LIVE_CHALLENGE -> LiveQuizChallengeScreen(viewModel = viewModel)
                DofaatiRoute.LEADERBOARD_RESULTS -> QuizLeaderboardResultsScreen(viewModel = viewModel)
                DofaatiRoute.SCHEDULE_COUNTDOWN -> BatchScheduleCountdownScreen(viewModel = viewModel)
            }

            if (statusBanner != null) {
                LaunchedEffect(statusBanner) {
                    kotlinx.coroutines.delay(2800)
                    viewModel.clearStatusBanner()
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.clearStatusBanner() },
                    shape = RoundedCornerShape(20.dp),
                    color = DofaatiColors.NavyInk,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = statusBanner ?: "",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private enum class HubBottomTab(val label: String) {
    SUBJECTS("المواد 4"),
    SCHEDULE("المواعيد"),
    UPDATES("المستجدات"),
    EXAMS("امتحانات")
}

@Composable
fun DofaatiHubScreen(
    viewModel: DofaatiViewModel,
    onNavigateBackToApp: () -> Unit
) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val activeBatch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.first()
    }
    val questionBank by viewModel.questionBankItems.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(HubBottomTab.SUBJECTS) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.44f,
        portraitContent = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DofaatiColors.Background)
                    .testTag("dofaati_hub_root"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    DofaatiHubTopHeader(onNavigateBackToApp = onNavigateBackToApp)
                }
                item {
                    QuickBatchActionsRow(
                        onCreateBatch = { viewModel.navigateTo(DofaatiRoute.CREATE_BATCH) },
                        onEnterCode = { showJoinCodeDialog = true }
                    )
                }
                item {
                    BatchDashboardCard(
                        batch = activeBatch,
                        onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                        onSwitchBatch = { nextId -> viewModel.selectBatch(nextId) },
                        allBatches = batches
                    )
                }
                item {
                    HubSegmentedTabBar(
                        selectedTab = selectedTab,
                        subjectCount = activeBatch.subjects.size,
                        onSelectTab = { selectedTab = it }
                    )
                }
                when (selectedTab) {
                    HubBottomTab.SUBJECTS -> {
                        items(activeBatch.subjects, key = { it.id }) { subject ->
                            SubjectListRowCard(
                                subject = subject,
                                onClick = { viewModel.selectSubject(subject.id) }
                            )
                        }
                    }
                    HubBottomTab.SCHEDULE -> {
                        item {
                            HubSchedulePreviewCard(
                                onOpenFullSchedule = { viewModel.navigateTo(DofaatiRoute.SCHEDULE_COUNTDOWN) }
                            )
                        }
                    }
                    HubBottomTab.UPDATES -> {
                        item {
                            HubUpdatesAndAdminCard(
                                pinnedNotice = activeBatch.pinnedNotice,
                                onOpenAdminRequests = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                                onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                            )
                        }
                    }
                    HubBottomTab.EXAMS -> {
                        item {
                            HubExamsSuiteCard(
                                totalBankCount = questionBank.size.coerceAtLeast(500),
                                onOpenBulkGenerator = { viewModel.navigateTo(DofaatiRoute.BULK_AI_GENERATOR) },
                                onOpenQuestionBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) },
                                onOpenAdminExamCreator = { viewModel.navigateTo(DofaatiRoute.ADMIN_EXAM_CREATOR) },
                                onStartLiveChallenge = { viewModel.startDefaultLiveChallenge() },
                                onOpenLeaderboard = { viewModel.navigateTo(DofaatiRoute.LEADERBOARD_RESULTS) }
                            )
                        }
                    }
                }
            }
        },
        landscapeLeftPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { DofaatiHubTopHeader(onNavigateBackToApp = onNavigateBackToApp) }
                item {
                    QuickBatchActionsRow(
                        onCreateBatch = { viewModel.navigateTo(DofaatiRoute.CREATE_BATCH) },
                        onEnterCode = { showJoinCodeDialog = true }
                    )
                }
                item {
                    BatchDashboardCard(
                        batch = activeBatch,
                        onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                        onSwitchBatch = { viewModel.selectBatch(it) },
                        allBatches = batches
                    )
                }
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    HubSegmentedTabBar(
                        selectedTab = selectedTab,
                        subjectCount = activeBatch.subjects.size,
                        onSelectTab = { selectedTab = it }
                    )
                }
                when (selectedTab) {
                    HubBottomTab.SUBJECTS -> {
                        items(activeBatch.subjects, key = { it.id }) { subject ->
                            SubjectListRowCard(
                                subject = subject,
                                onClick = { viewModel.selectSubject(subject.id) }
                            )
                        }
                    }
                    HubBottomTab.SCHEDULE -> {
                        item {
                            HubSchedulePreviewCard(
                                onOpenFullSchedule = { viewModel.navigateTo(DofaatiRoute.SCHEDULE_COUNTDOWN) }
                            )
                        }
                    }
                    HubBottomTab.UPDATES -> {
                        item {
                            HubUpdatesAndAdminCard(
                                pinnedNotice = activeBatch.pinnedNotice,
                                onOpenAdminRequests = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                                onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                            )
                        }
                    }
                    HubBottomTab.EXAMS -> {
                        item {
                            HubExamsSuiteCard(
                                totalBankCount = questionBank.size.coerceAtLeast(500),
                                onOpenBulkGenerator = { viewModel.navigateTo(DofaatiRoute.BULK_AI_GENERATOR) },
                                onOpenQuestionBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) },
                                onOpenAdminExamCreator = { viewModel.navigateTo(DofaatiRoute.ADMIN_EXAM_CREATOR) },
                                onStartLiveChallenge = { viewModel.startDefaultLiveChallenge() },
                                onOpenLeaderboard = { viewModel.navigateTo(DofaatiRoute.LEADERBOARD_RESULTS) }
                            )
                        }
                    }
                }
            }
        }
    )

    if (showJoinCodeDialog) {
        JoinByCodeDialog(
            previewLookup = { code -> viewModel.previewBatchByCode(code) },
            onRequestJoin = { code ->
                viewModel.requestJoinByCode(code)
                showJoinCodeDialog = false
            },
            onDismiss = { showJoinCodeDialog = false }
        )
    }
}

@Composable
private fun DofaatiHubTopHeader(onNavigateBackToApp: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DofaatiAmiriTitleText(
                text = "دفعاتي",
                color = DofaatiColors.NavyInk,
                fontSize = 30.sp,
                modifier = Modifier.testTag("dofaati_hub_title")
            )
            IconButton(
                onClick = onNavigateBackToApp,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("dofaati_hub_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = DofaatiColors.NavyInk,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = "جروب دفعتك: شات وريكوردات وملفات كل مادة",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = DofaatiColors.SlateMuted
        )
    }
}

@Composable
private fun QuickBatchActionsRow(
    onCreateBatch: () -> Unit,
    onEnterCode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DofaatiCard(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_create_batch"),
            onClick = onCreateBatch
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    tint = DofaatiColors.RoyalBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                AutoSizeText(
                    text = "اعمل دفعة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 15.sp,
                    minFontSize = 12.sp,
                    color = DofaatiColors.NavyInk
                )
            }
        }

        DofaatiCard(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_join_with_code"),
            onClick = onEnterCode
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = DofaatiColors.RoyalBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                AutoSizeText(
                    text = "ادخل بكود",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 15.sp,
                    minFontSize = 12.sp,
                    color = DofaatiColors.NavyInk
                )
            }
        }
    }
}

@Composable
fun BatchDashboardCard(
    batch: DofaatiBatch,
    allBatches: List<DofaatiBatch>,
    onOpenChat: () -> Unit,
    onSwitchBatch: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_batch_card"),
        shape = RoundedCornerShape(18.dp),
        color = DofaatiColors.NavyInk,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(batch.logoColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = batch.name.take(1),
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        AutoSizeText(
                            text = batch.name,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 18.sp,
                            minFontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${batch.studentsCount} طالب · ${batch.subjects.size.coerceAtLeast(6)} مواد",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        DofaatiLtrText(
                            text = batch.code,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                if (allBatches.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allBatches.take(3).forEach { b ->
                            val active = b.id == batch.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSwitchBatch(b.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) DofaatiColors.RoyalBlue else Color.White.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = b.name,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Connected full-width Royal Blue button "شات الدفعة"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable { onOpenChat() }
                    .testTag("btn_batch_chat"),
                color = DofaatiColors.RoyalBlue
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شات الدفعة",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HubSegmentedTabBar(
    selectedTab: HubBottomTab,
    subjectCount: Int,
    onSelectTab: (HubBottomTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dofaati_hub_tab_bar"),
        shape = RoundedCornerShape(16.dp),
        color = DofaatiColors.RoyalBlue,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HubBottomTab.entries.forEach { tab ->
                val active = tab == selectedTab
                val label = if (tab == HubBottomTab.SUBJECTS) "المواد $subjectCount" else tab.label
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTab(tab) }
                        .testTag("hub_tab_${tab.name.lowercase()}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Color.White else Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoSizeText(
                            text = label,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxFontSize = 12.5.sp,
                            minFontSize = 9.5.sp,
                            color = if (active) DofaatiColors.RoyalBlue else Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectListRowCard(
    subject: DofaatiSubject,
    onClick: () -> Unit
) {
    val icon = when {
        subject.name.contains("فيزيا") -> Icons.Default.Bolt
        subject.name.contains("كيميا") -> Icons.Default.Science
        subject.name.contains("أحياء") -> Icons.Default.Biotech
        else -> Icons.Default.MenuBook
    }
    DofaatiCard(
        modifier = Modifier.testTag("subject_card_${subject.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DofaatiColors.RoyalBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = subject.name,
                    tint = DofaatiColors.RoyalBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = subject.name,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DofaatiColors.NavyInk
                )
                Text(
                    text = "${subject.recordingsCount} ريكوردات · ${subject.filesCount} ملفات PDF",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = DofaatiColors.SlateMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DofaatiColors.RoyalBlueTint
            ) {
                Text(
                    text = "فتح",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = DofaatiColors.RoyalBlueDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HubSchedulePreviewCard(onOpenFullSchedule: () -> Unit) {
    DofaatiCard(onClick = onOpenFullSchedule) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "جدول المواعيد والامتحانات القادمة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = DofaatiColors.NavyInk
                )
            }
            Text(
                text = "باقي يومان و 4 ساعات على الامتحان الشامل • الأحد 6 مساءً • الأربعاء 8 مساءً",
                fontFamily = ReadexProFontFamily,
                fontSize = 13.sp,
                color = DofaatiColors.SlateBody
            )
            Button(
                onClick = onOpenFullSchedule,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_open_schedule_screen")
            ) {
                Text(
                    text = "عرض الجدول الذكي واستخراج من صورة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HubUpdatesAndAdminCard(
    pinnedNotice: String,
    onOpenAdminRequests: () -> Unit,
    onOpenChat: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DofaatiColors.PinnedOrange
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = pinnedNotice,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        DofaatiCard(onClick = onOpenAdminRequests) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("إدارة الدفعة - طلبات الانضمام", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DofaatiColors.NavyInk)
                    Text("قبول أو رفض الطلاب الجدد ومشاركة كود 4861 3927", fontFamily = ReadexProFontFamily, fontSize = 12.sp, color = DofaatiColors.SlateMuted)
                }
            }
        }

        Button(
            onClick = onOpenChat,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("الدخول إلى شات الدفعة الصوتي", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HubExamsSuiteCard(
    totalBankCount: Int,
    onOpenBulkGenerator: () -> Unit,
    onOpenQuestionBank: () -> Unit,
    onOpenAdminExamCreator: () -> Unit,
    onStartLiveChallenge: () -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExamActionSuiteRow(
            title = "مولد الأسئلة الضخم بالذكاء الاصطناعي",
            subtitle = "توليد حتى 100 سؤال بدون انقطاع عبر دفعات متتالية",
            icon = Icons.Default.AutoAwesome,
            accent = DofaatiColors.RoyalBlue,
            testTag = "btn_open_bulk_ai_generator",
            onClick = onOpenBulkGenerator
        )
        ExamActionSuiteRow(
            title = "بنك الأسئلة الشامل ($totalBankCount+ سؤال)",
            subtitle = "استيراد ملفات PDF مع رسوم الدوائر الكهربية",
            icon = Icons.Default.Storage,
            accent = DofaatiColors.RoyalBlueDark,
            testTag = "btn_open_question_bank",
            onClick = onOpenQuestionBank
        )
        ExamActionSuiteRow(
            title = "إدارة الدفعة ولصق 100 سؤال دفعة واحدة",
            subtitle = "لصق ونشر تحدي مباشر أو امتحان فوري للدفعة",
            icon = Icons.Default.ContentPaste,
            accent = DofaatiColors.AmberGold,
            testTag = "btn_open_admin_paste_creator",
            onClick = onOpenAdminExamCreator
        )
        ExamActionSuiteRow(
            title = "التحدي المباشر اللحظي للدفعة (Live Quiz)",
            subtitle = "مؤقت دائري حي + 37 متصل الآن + حساب نقاط السرعة",
            icon = Icons.Default.Quiz,
            accent = DofaatiColors.EmeraldGreen,
            testTag = "btn_start_live_challenge",
            onClick = onStartLiveChallenge
        )
        ExamActionSuiteRow(
            title = "نتيجة الاختبار ولوحة الشرف الثلاثية",
            subtitle = "منصة التتويج الثلاثية ومراجعة التفسير التفصيلي",
            icon = Icons.Default.EmojiEvents,
            accent = DofaatiColors.PinnedOrange,
            testTag = "btn_open_leaderboard_results",
            onClick = onOpenLeaderboard
        )
    }
}

@Composable
private fun ExamActionSuiteRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    DofaatiCard(
        modifier = Modifier.testTag(testTag),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = DofaatiColors.NavyInk)
                Text(subtitle, fontFamily = ReadexProFontFamily, fontSize = 12.sp, color = DofaatiColors.SlateMuted)
            }
        }
    }
}

@Composable
private fun JoinByCodeDialog(
    previewLookup: (String) -> Pair<String, String>,
    onRequestJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    val cleanDigits = codeInput.filter { it.isDigit() }.take(8)
    val formattedPreview = if (cleanDigits.length > 4) {
        "${cleanDigits.substring(0, 4)} ${cleanDigits.substring(4)}"
    } else {
        cleanDigits
    }
    val (batchPreviewName, adminPreviewName) = remember(cleanDigits) {
        if (cleanDigits.length == 8) previewLookup(cleanDigits) else ("" to "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ادخل بكود الدفعة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DofaatiColors.NavyInk
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "أدخل الكود المكون من 8 أرقام الذي شاركه مشرف الدفعة:",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 13.sp,
                    color = DofaatiColors.SlateBody
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = formattedPreview,
                        onValueChange = { next -> codeInput = next.filter { it.isDigit() }.take(8) },
                        placeholder = { Text("4861 3927", fontFamily = ReadexProFontFamily) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input")
                    )
                }

                if (cleanDigits.length == 8) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = DofaatiColors.RoyalBlueSoft,
                        border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "معاينة الدفعة:",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 11.5.sp,
                                color = DofaatiColors.RoyalBlue
                            )
                            Text(
                                text = batchPreviewName,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = DofaatiColors.NavyInk
                            )
                            Text(
                                text = "المشرف: $adminPreviewName",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                color = DofaatiColors.SlateBody
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRequestJoin(cleanDigits.ifBlank { "48613927" }) },
                enabled = cleanDigits.length == 8,
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                modifier = Modifier.testTag("btn_confirm_join_request")
            ) {
                Text("اطلب الانضمام", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
}
