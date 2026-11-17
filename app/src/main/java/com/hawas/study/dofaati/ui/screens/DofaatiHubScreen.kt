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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
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
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiBatch
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.models.DofaatiSubject
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiAmiriTitleText
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText

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
                    kotlinx.coroutines.delay(3200)
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
    SUBJECTS("المواد"),
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
        batches.firstOrNull { it.id == activeBatchId } ?: batches.firstOrNull()
    }
    val questionBank by viewModel.questionBankItems.collectAsStateWithLifecycle()
    val scheduleEvents by viewModel.scheduleEvents.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(HubBottomTab.SUBJECTS) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }

    val activeJoinRequestsCount = remember(joinRequests, activeBatch) {
        if (activeBatch == null) 0 else joinRequests.count { it.batchId == activeBatch.id }
    }

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
                    if (activeBatch != null) {
                        BatchDashboardCard(
                            batch = activeBatch,
                            allBatches = batches,
                            onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                            onSwitchBatch = { nextId -> viewModel.selectBatch(nextId) }
                        )
                    } else {
                        EmptyBatchWelcomeCard(
                            onCreateBatch = { viewModel.navigateTo(DofaatiRoute.CREATE_BATCH) },
                            onEnterCode = { showJoinCodeDialog = true }
                        )
                    }
                }
                item {
                    HubSegmentedTabBar(
                        selectedTab = selectedTab,
                        subjectCount = activeBatch?.subjects?.size ?: 0,
                        onSelectTab = { selectedTab = it }
                    )
                }
                when (selectedTab) {
                    HubBottomTab.SUBJECTS -> {
                        if (activeBatch == null) {
                            item {
                                HubEmptySectionCard(
                                    title = "لا توجد مواد بعد",
                                    subtitle = "أنشئ دفعتك الأولى أو انضم بكود لتظهر موادك وملفاتك هنا."
                                )
                            }
                        } else if (activeBatch.subjects.isEmpty()) {
                            item {
                                HubEmptySectionCard(
                                    title = "لم تتم إضافة مواد لهذه الدفعة",
                                    subtitle = "أضف مادة الآن لبدء رفع الريكوردات والملفات."
                                )
                            }
                            item {
                                AddSubjectButton(onClick = { showAddSubjectDialog = true })
                            }
                        } else {
                            items(activeBatch.subjects, key = { it.id }) { subject ->
                                SubjectListRowCard(
                                    subject = subject,
                                    onClick = { viewModel.selectSubject(subject.id) },
                                    onDelete = { viewModel.deleteSubject(subject.id) }
                                )
                            }
                            item {
                                AddSubjectButton(onClick = { showAddSubjectDialog = true })
                            }
                        }
                    }
                    HubBottomTab.SCHEDULE -> {
                        item {
                            HubSchedulePreviewCard(
                                events = scheduleEvents,
                                onOpenFullSchedule = { viewModel.navigateTo(DofaatiRoute.SCHEDULE_COUNTDOWN) }
                            )
                        }
                    }
                    HubBottomTab.UPDATES -> {
                        item {
                            HubUpdatesAndAdminCard(
                                batch = activeBatch,
                                pendingRequestsCount = activeJoinRequestsCount,
                                onOpenAdminRequests = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                                onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                            )
                        }
                    }
                    HubBottomTab.EXAMS -> {
                        item {
                            HubExamsSuiteCard(
                                totalBankCount = questionBank.size,
                                onOpenBulkGenerator = { viewModel.navigateTo(DofaatiRoute.BULK_AI_GENERATOR) },
                                onOpenQuestionBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) },
                                onOpenAdminExamCreator = { viewModel.navigateTo(DofaatiRoute.ADMIN_EXAM_CREATOR) },
                                onStartLiveChallenge = { viewModel.startQuizFromQuestionBank() },
                                onOpenLeaderboard = { viewModel.navigateTo(DofaatiRoute.LEADERBOARD_RESULTS) },
                                onOpenMindMaps = { viewModel.navigateTo(DofaatiRoute.MIND_MAP_FLASHCARDS) }
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
                    if (activeBatch != null) {
                        BatchDashboardCard(
                            batch = activeBatch,
                            allBatches = batches,
                            onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                            onSwitchBatch = { viewModel.selectBatch(it) }
                        )
                    } else {
                        EmptyBatchWelcomeCard(
                            onCreateBatch = { viewModel.navigateTo(DofaatiRoute.CREATE_BATCH) },
                            onEnterCode = { showJoinCodeDialog = true }
                        )
                    }
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
                        subjectCount = activeBatch?.subjects?.size ?: 0,
                        onSelectTab = { selectedTab = it }
                    )
                }
                when (selectedTab) {
                    HubBottomTab.SUBJECTS -> {
                        if (activeBatch == null) {
                            item {
                                HubEmptySectionCard(
                                    title = "لا توجد مواد بعد",
                                    subtitle = "أنشئ دفعتك الأولى أو انضم بكود لتظهر موادك وملفاتك هنا."
                                )
                            }
                        } else if (activeBatch.subjects.isEmpty()) {
                            item {
                                HubEmptySectionCard(
                                    title = "لم تتم إضافة مواد لهذه الدفعة",
                                    subtitle = "أضف مادة الآن لبدء رفع الريكوردات والملفات."
                                )
                            }
                            item {
                                AddSubjectButton(onClick = { showAddSubjectDialog = true })
                            }
                        } else {
                            items(activeBatch.subjects, key = { it.id }) { subject ->
                                SubjectListRowCard(
                                    subject = subject,
                                    onClick = { viewModel.selectSubject(subject.id) },
                                    onDelete = { viewModel.deleteSubject(subject.id) }
                                )
                            }
                            item {
                                AddSubjectButton(onClick = { showAddSubjectDialog = true })
                            }
                        }
                    }
                    HubBottomTab.SCHEDULE -> {
                        item {
                            HubSchedulePreviewCard(
                                events = scheduleEvents,
                                onOpenFullSchedule = { viewModel.navigateTo(DofaatiRoute.SCHEDULE_COUNTDOWN) }
                            )
                        }
                    }
                    HubBottomTab.UPDATES -> {
                        item {
                            HubUpdatesAndAdminCard(
                                batch = activeBatch,
                                pendingRequestsCount = activeJoinRequestsCount,
                                onOpenAdminRequests = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) },
                                onOpenChat = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                            )
                        }
                    }
                    HubBottomTab.EXAMS -> {
                        item {
                            HubExamsSuiteCard(
                                totalBankCount = questionBank.size,
                                onOpenBulkGenerator = { viewModel.navigateTo(DofaatiRoute.BULK_AI_GENERATOR) },
                                onOpenQuestionBank = { viewModel.navigateTo(DofaatiRoute.QUESTION_BANK) },
                                onOpenAdminExamCreator = { viewModel.navigateTo(DofaatiRoute.ADMIN_EXAM_CREATOR) },
                                onStartLiveChallenge = { viewModel.startQuizFromQuestionBank() },
                                onOpenLeaderboard = { viewModel.navigateTo(DofaatiRoute.LEADERBOARD_RESULTS) },
                                onOpenMindMaps = { viewModel.navigateTo(DofaatiRoute.MIND_MAP_FLASHCARDS) }
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

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onAdd = { name ->
                viewModel.addSubjectToActiveBatch(name)
                showAddSubjectDialog = false
            },
            onDismiss = { showAddSubjectDialog = false }
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
            text = "مساحة دفعتك النظيفة للشات والملفات والاختبارات",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = DofaatiColors.SlateMuted
        )
    }
}

@Composable
private fun EmptyBatchWelcomeCard(
    onCreateBatch: () -> Unit,
    onEnterCode: () -> Unit
) {
    DofaatiCard(
        modifier = Modifier.testTag("empty_batch_welcome_card"),
        borderColor = DofaatiColors.RoyalBlueTint,
        borderWidth = 1.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DofaatiColors.RoyalBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = DofaatiColors.RoyalBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "لا توجد دفعات مضافة بعد",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = DofaatiColors.NavyInk,
                textAlign = TextAlign.Center
            )
            Text(
                text = "أنشئ دفعتك الخاصة واختر موادك، أو انضم إلى دفعة باستخدام كود من 8 أرقام.",
                fontFamily = ReadexProFontFamily,
                fontSize = 13.sp,
                color = DofaatiColors.SlateMuted,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCreateBatch,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("إنشاء دفعة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onEnterCode,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("لدي كود", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HubEmptySectionCard(title: String, subtitle: String) {
    DofaatiCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = DofaatiColors.NavyInk,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontFamily = ReadexProFontFamily,
                fontSize = 12.5.sp,
                color = DofaatiColors.SlateMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddSubjectButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("btn_add_subject")
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = DofaatiColors.RoyalBlue)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "إضافة مادة جديدة",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            color = DofaatiColors.RoyalBlue
        )
    }
}

@Composable
private fun AddSubjectDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("إضافة مادة جديدة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("اكتب اسم المادة (مثل: رياضيات)", fontFamily = ReadexProFontFamily) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("new_subject_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
            ) {
                Text("إضافة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = ReadexProFontFamily)
            }
        }
    )
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
                            text = batch.name.take(1).ifBlank { "د" },
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
                            text = "${batch.studentsCount} عضو · ${batch.subjects.size} مادة",
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
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    text = "${subject.recordingsCount} ريكورد · ${subject.filesCount} ملف",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = DofaatiColors.SlateMuted
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف ${subject.name}",
                    tint = DofaatiColors.SlateMuted,
                    modifier = Modifier.size(20.dp)
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
private fun HubSchedulePreviewCard(
    events: List<DofaatiScheduleEvent>,
    onOpenFullSchedule: () -> Unit
) {
    val upcomingSummary = remember(events) {
        if (events.isEmpty()) {
            "لا توجد مواعيد أو امتحانات مجدولة بعد. أضف موعدك يدوياً أو صوّر الجدول."
        } else {
            events.take(3).joinToString(" • ") { "${it.title} (${it.dayAndTime})" }
        }
    }
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
                text = upcomingSummary,
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
                    text = "فتح الجدول وإضافة المواعيد",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HubUpdatesAndAdminCard(
    batch: DofaatiBatch?,
    pendingRequestsCount: Int,
    onOpenAdminRequests: () -> Unit,
    onOpenChat: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (batch == null) {
            HubEmptySectionCard(
                title = "لا توجد مستجدات",
                subtitle = "أنشئ دفعة أولاً لإرسال الإعلانات المثبتة وإدارة طلبات الانضمام."
            )
            return@Column
        }

        if (batch.pinnedNotice.isNotBlank()) {
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
                        text = batch.pinnedNotice,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
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
                    Text("إدارة الدفعة - طلبات الانضمام ($pendingRequestsCount)", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DofaatiColors.NavyInk)
                    Text("إدارة الأعضاء ومشاركة كود الدفعة ${batch.code}", fontFamily = ReadexProFontFamily, fontSize = 12.sp, color = DofaatiColors.SlateMuted)
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
    onOpenLeaderboard: () -> Unit,
    onOpenMindMaps: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExamActionSuiteRow(
            title = "مولد الأسئلة بالذكاء الاصطناعي",
            subtitle = "توليد أسئلة مخصصة متدرجة من نص منهجك مباشرة",
            icon = Icons.Default.AutoAwesome,
            accent = DofaatiColors.RoyalBlue,
            testTag = "btn_open_bulk_ai_generator",
            onClick = onOpenBulkGenerator
        )
        ExamActionSuiteRow(
            title = "بنك الأسئلة ($totalBankCount سؤال)",
            subtitle = "إضافة أسئلة يدوياً أو استيرادها من PDF مع التفسير",
            icon = Icons.Default.Storage,
            accent = DofaatiColors.RoyalBlueDark,
            testTag = "btn_open_question_bank",
            onClick = onOpenQuestionBank
        )
        ExamActionSuiteRow(
            title = "منشئ الامتحانات باللصق السريع",
            subtitle = "لصق ونشر امتحان مباشر للدفعة مع مراجعة الإجابات",
            icon = Icons.Default.ContentPaste,
            accent = DofaatiColors.AmberGold,
            testTag = "btn_open_admin_paste_creator",
            onClick = onOpenAdminExamCreator
        )
        ExamActionSuiteRow(
            title = "التحدي التفاعلي المباشر",
            subtitle = if (totalBankCount > 0) "اختبر نفسك في $totalBankCount سؤال من بنكك بمؤقت السرعة" else "أضف أسئلة في البنك أو المنشئ لبدء التحدي",
            icon = Icons.Default.Quiz,
            accent = DofaatiColors.EmeraldGreen,
            testTag = "btn_start_live_challenge",
            onClick = onStartLiveChallenge
        )
        ExamActionSuiteRow(
            title = "الخرائط الذهنية وبطاقات الاستذكار",
            subtitle = "خرائط مفاهيم وبطاقات قلب ثلاثية الأبعاد للمراجعة",
            icon = Icons.Default.AccountTree,
            accent = DofaatiColors.PurpleNode,
            testTag = "btn_open_mindmaps_from_hub",
            onClick = onOpenMindMaps
        )
        ExamActionSuiteRow(
            title = "نتائج الاختبارات ولوحة الشرف",
            subtitle = "سجل درجاتك الحقيقية ومراجعة الإجابات والتفسير",
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
    previewLookup: (String) -> Pair<String, String>?,
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
    val matchedBatch = remember(cleanDigits) {
        if (cleanDigits.length == 8) previewLookup(cleanDigits) else null
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
                        placeholder = { Text("1234 5678", fontFamily = ReadexProFontFamily) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input")
                    )
                }

                if (cleanDigits.length == 8) {
                    if (matchedBatch != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DofaatiColors.RoyalBlueSoft,
                            border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("معاينة الدفعة:", fontFamily = ReadexProFontFamily, fontSize = 11.5.sp, color = DofaatiColors.RoyalBlue)
                                Text(matchedBatch.first, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = DofaatiColors.NavyInk)
                                Text("المشرف: ${matchedBatch.second}", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp, color = DofaatiColors.SlateBody)
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DofaatiColors.RedAlert.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, DofaatiColors.RedAlert.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "لا توجد دفعة مسجلة بهذا الكود.",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                color = DofaatiColors.RedAlert,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRequestJoin(cleanDigits) },
                enabled = cleanDigits.length == 8 && matchedBatch != null,
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
