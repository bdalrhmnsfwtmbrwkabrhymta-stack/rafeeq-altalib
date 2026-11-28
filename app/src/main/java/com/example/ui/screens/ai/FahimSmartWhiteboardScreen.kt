package com.example.ui.screens.ai

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.ai.FahimDialect
import com.example.domain.ai.FahimVoiceGender
import com.example.domain.ai.OpenMaicBoardSession
import com.example.domain.ai.OpenMaicPdfDocument
import com.example.domain.ai.WhiteboardState
import com.example.ui.MainViewModel
import com.example.ui.theme.ReadexProFontFamily
import kotlin.math.abs
import kotlin.math.sin

private val BoardPageBg = Color(0xFFF8FAFC)
private val BoardBorderBlue = Color(0xFF1E4E8C)
private val BoardRoyalBlue = Color(0xFF1A6FE3)
private val BoardCharcoal = Color(0xFF1E1B18)
private val BoardMuted = Color(0xFF64748B)
private val BoardGridLine = Color(0xFFE2E8F0)
private val BoardAmberGold = Color(0xFFD97706)
private val BoardEmerald = Color(0xFF10B981)

/**
 * سبورة فهيم الذكية (OpenMAIC + Excalidraw):
 * - تبدأ دائماً بحالة [WhiteboardState.Empty] نظيفة (كراسة مربعات بيضاء + مجسم فهيم 3D + زرا رفع PDF وخريطة ذهنية).
 * - في الحالة النشطة [WhiteboardState.Active]: شريط صوتي نظيف بدون اقتطاع (مصري/فصحى فقط + استماع للشرح بموجة حية)،
 *   رسم Excalidraw تدريجي حي، جدول موحد، خطوات معادلات مفصولة الاتجاه (RTL عربي / LTR معادلة)، وتبويبات OpenMAIC.
 */
@Composable
fun FahimSmartWhiteboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onSwitchToChat: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsState()
    val whiteboardState by viewModel.whiteboardState.collectAsState()
    val pdfDoc by viewModel.openMaicPdfDoc.collectAsState()
    val isSpeaking by viewModel.fahimVoiceEngine.isSpeaking.collectAsState()
    val activeStepIndex by viewModel.fahimVoiceEngine.activeStepIndex.collectAsState()
    val activeEngineLabel by viewModel.fahimVoiceEngine.activeEngineLabel.collectAsState()
    val voiceGender by viewModel.fahimVoiceEngine.voiceGender.collectAsState()
    val speechRate by viewModel.fahimVoiceEngine.speechRate.collectAsState()

    val selectedDialect = remember(settings.fahimDialect) {
        FahimDialect.fromName(settings.fahimDialect)
    }

    var questionInput by remember { mutableStateOf("") }
    var replayNonce by remember { mutableIntStateOf(0) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showMindMapPromptDialog by remember { mutableStateOf(false) }
    var mindMapTopicInput by remember { mutableStateOf("") }
    var showPdfPreview by remember { mutableStateOf(false) }
    var activeAgentTab by remember { mutableIntStateOf(0) }
    var activeDrawTool by remember { mutableStateOf(ExcalidrawDrawTool.PAN_SCROLL) }
    var selectedInkColorHex by remember { mutableStateOf(0xFF1A6FE3L) }
    var studentInkStrokes by remember { mutableStateOf<List<StudentInkElement>>(emptyList()) }

    val activeSession = (whiteboardState as? WhiteboardState.Active)?.session
    val isLoading = whiteboardState is WhiteboardState.Loading

    // 1. Live Smart Pen Drawing progress animation (stroke-by-stroke with moving stylus cursor)
    val drawProgress = remember { Animatable(0f) }
    // 2. Live Step-by-Step Handwriting reveal progress (0f..steps.size.toFloat())
    val handwritingProgress = remember { Animatable(0f) }

    LaunchedEffect(activeSession?.topicTitle, activeSession?.lesson?.steps?.size, replayNonce) {
        if (activeSession != null) {
            studentInkStrokes = emptyList()
            drawProgress.snapTo(0f)
            handwritingProgress.snapTo(0f)
            drawProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3200, easing = FastOutSlowInEasing)
            )
            val totalSteps = activeSession.lesson.steps.size.coerceAtLeast(1)
            handwritingProgress.animateTo(
                targetValue = totalSteps.toFloat(),
                animationSpec = tween(
                    durationMillis = (totalSteps * 1850).coerceIn(2200, 9500),
                    easing = LinearEasing
                )
            )
        }
    }

    // Keep handwriting ahead of or synced with whichever step the neural voice is speaking
    LaunchedEffect(activeStepIndex, isSpeaking) {
        if (activeSession != null && isSpeaking && activeStepIndex >= 0) {
            val minNeeded = (activeStepIndex + 1).toFloat()
            if (handwritingProgress.value < minNeeded) {
                handwritingProgress.animateTo(
                    targetValue = minNeeded,
                    animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.consumePendingWhiteboardPrompt()
    }

    BackHandler {
        viewModel.fahimVoiceEngine.stop()
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.fahimVoiceEngine.stop() }
    }

    // PDF Picker (OpenDocument for PDF books / handouts)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.loadPdfForWhiteboard(uri = uri, pageIndex = 0, autoExplain = true)
            replayNonce++
        }
    }

    // Voice Speech Recognizer — strictly ar-EG or ar (NEVER ar-SA)
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                questionInput = ""
                viewModel.loadWhiteboardLesson(spoken, selectedDialect, autoSpeak = true)
                replayNonce++
            }
        }
    }

    fun launchVoiceInput(dialect: FahimDialect) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, dialect.bcp47Tag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "اطلب شرح مسألة أو درس على السبورة...")
        }
        runCatching {
            isListeningVoice = true
            speechRecognizerLauncher.launch(intent)
        }.onFailure {
            isListeningVoice = false
            Toast.makeText(context, "تعذر فتح الإدخال الصوتي على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVoiceInput(selectedDialect)
        } else {
            Toast.makeText(context, "يرجى السماح بصلاحية الميكروفون للتحدث مع فهيم", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestVoiceWithDialect(dialect: FahimDialect) {
        if (dialect != selectedDialect) {
            viewModel.updateFahimDialect(dialect)
        }
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            launchVoiceInput(dialect)
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleLessonAudio(session: OpenMaicBoardSession) {
        if (isSpeaking) {
            viewModel.fahimVoiceEngine.stop()
        } else {
            viewModel.fahimVoiceEngine.speakLessonSteps(
                steps = session.lesson.steps,
                dialect = selectedDialect,
                piperUrl = settings.piperTtsUrl,
                coquiUrl = settings.coquiTtsUrl,
                voiceApiKey = settings.voiceTtsApiKey,
                gender = voiceGender,
                speed = speechRate
            )
        }
    }

    if (showMindMapPromptDialog) {
        AlertDialog(
            onDismissRequest = { showMindMapPromptDialog = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = "تلخيص درس بخريطة ذهنية",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BoardCharcoal
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اكتب اسم الدرس أو الموضوع الذي تريد تلخيصه بخريطة ذهنية وقوانين منظمة:",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        color = BoardMuted
                    )
                    OutlinedTextField(
                        value = mindMapTopicInput,
                        onValueChange = { mindMapTopicInput = it },
                        placeholder = {
                            Text(
                                text = "مثال: الدوائر الكهربائية وقانون أوم",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mindmap_topic_dialog_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val topic = mindMapTopicInput.trim().ifBlank { "الدوائر الكهربائية وقانون أوم" }
                        showMindMapPromptDialog = false
                        mindMapTopicInput = ""
                        activeAgentTab = 0
                        viewModel.loadWhiteboardLesson(
                            prompt = topic,
                            dialect = selectedDialect,
                            autoSpeak = true,
                            preferMindMap = true
                        )
                        replayNonce++
                    }
                ) {
                    Text(
                        text = "ابدأ التلخيص",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = BoardRoyalBlue
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMindMapPromptDialog = false }) {
                    Text(
                        text = "إلغاء",
                        fontFamily = ReadexProFontFamily,
                        color = BoardMuted
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("fahim_smart_whiteboard_screen")
    ) {
        // 1. Clean Top Header Bar (Matches Ref Image 1 Left & Ref Image 2)
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        viewModel.fahimVoiceEngine.stop()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("whiteboard_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = BoardCharcoal
                    )
                }

                Text(
                    text = "سبورة فهيم الذكية",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BoardCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                if (activeSession != null) {
                    // Prominent "رفع كتاب PDF" button in active top bar (matches Ref Image 2)
                    Surface(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.testTag("whiteboard_top_upload_pdf_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "رفع كتاب PDF",
                                tint = BoardCharcoal,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "رفع كتاب PDF",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BoardCharcoal,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("whiteboard_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات",
                                tint = BoardCharcoal
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("رفع كتاب PDF", fontFamily = ReadexProFontFamily) },
                                leadingIcon = { Icon(Icons.Default.UploadFile, null, tint = BoardRoyalBlue) },
                                onClick = {
                                    showOverflowMenu = false
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("العودة لمحادثة فهيم", fontFamily = ReadexProFontFamily) },
                                leadingIcon = { Icon(Icons.Default.Forum, null, tint = BoardRoyalBlue) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.fahimVoiceEngine.stop()
                                    onSwitchToChat()
                                }
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = BoardGridLine)

        // 2. Main Body — Switches cleanly between Empty, Loading, and Active states
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = whiteboardState) {
                is WhiteboardState.Empty -> {
                    WhiteboardEmptyCanvasContent(
                        selectedDialect = selectedDialect,
                        onUploadPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        onMindMapClick = { showMindMapPromptDialog = true },
                        onDialectVoiceClick = { dialect -> requestVoiceWithDialect(dialect) }
                    )
                }

                is WhiteboardState.Loading -> {
                    ExcalidrawGridPaperBox(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FahimMascot3D(
                                compactMode = true,
                                showIdleSpeechBubble = false,
                                showStatusBadge = false,
                                mascotSizeDp = 136.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = BoardRoyalBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = state.statusMessage,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BoardCharcoal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is WhiteboardState.Error -> {
                    ExcalidrawGridPaperBox(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FahimMascot3D(
                                compactMode = true,
                                showIdleSpeechBubble = false,
                                showStatusBadge = false,
                                mascotSizeDp = 124.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "تعذّر توليد الشرح الحي على السبورة",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BoardCharcoal,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.sp,
                                color = BoardMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.resetWhiteboardToEmpty() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("رجوع", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    onClick = {
                                        val retry = state.retryPrompt.ifBlank { "اشرح الدائرة الكهربائية وقانون أوم" }
                                        viewModel.loadWhiteboardLesson(retry, selectedDialect, autoSpeak = true)
                                        replayNonce++
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = BoardRoyalBlue,
                                    modifier = Modifier.testTag("whiteboard_retry_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "إعادة المحاولة",
                                            fontFamily = ReadexProFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is WhiteboardState.Active -> {
                    WhiteboardActiveContent(
                        session = state.session,
                        pdfDoc = pdfDoc,
                        selectedDialect = selectedDialect,
                        isSpeaking = isSpeaking,
                        activeStepIndex = activeStepIndex,
                        activeEngineLabel = activeEngineLabel,
                        voiceGender = voiceGender,
                        speechRate = speechRate,
                        drawProgress = drawProgress.value,
                        handwritingProgress = handwritingProgress.value,
                        activeDrawTool = activeDrawTool,
                        selectedInkColorHex = selectedInkColorHex,
                        studentInkStrokes = studentInkStrokes,
                        showPdfPreview = showPdfPreview,
                        activeAgentTab = activeAgentTab,
                        onSelectDialect = { d -> viewModel.updateFahimDialect(d) },
                        onToggleSpeak = { toggleLessonAudio(state.session) },
                        onToggleVoiceGender = {
                            val next = if (voiceGender == FahimVoiceGender.MALE) FahimVoiceGender.FEMALE else FahimVoiceGender.MALE
                            viewModel.toggleFahimVoiceGender(next)
                        },
                        onCycleSpeed = { viewModel.cycleFahimSpeechRate() },
                        onReplayDrawing = { replayNonce++ },
                        onClearBoard = {
                            studentInkStrokes = emptyList()
                            viewModel.resetWhiteboardToEmpty()
                        },
                        onSelectDrawTool = { activeDrawTool = it },
                        onSelectInkColor = { selectedInkColorHex = it },
                        onAddStudentStroke = { stroke -> studentInkStrokes = studentInkStrokes + stroke },
                        onEraseNearPoint = { pt ->
                            studentInkStrokes = studentInkStrokes.filterNot { st ->
                                st.points.any { p -> kotlin.math.hypot(p.x - pt.x, p.y - pt.y) < 38f }
                            }
                        },
                        onUndoStudentInk = {
                            if (studentInkStrokes.isNotEmpty()) {
                                studentInkStrokes = studentInkStrokes.dropLast(1)
                            }
                        },
                        onClearStudentInk = { studentInkStrokes = emptyList() },
                        onTogglePdfPreview = { showPdfPreview = !showPdfPreview },
                        onPrevPdfPage = {
                            val doc = pdfDoc ?: return@WhiteboardActiveContent
                            if (doc.currentPageIndex > 0) {
                                viewModel.selectPdfPageForWhiteboard(doc.currentPageIndex - 1, autoExplain = true)
                                replayNonce++
                            }
                        },
                        onNextPdfPage = {
                            val doc = pdfDoc ?: return@WhiteboardActiveContent
                            if (doc.currentPageIndex < doc.pageCount - 1) {
                                viewModel.selectPdfPageForWhiteboard(doc.currentPageIndex + 1, autoExplain = true)
                                replayNonce++
                            }
                        },
                        onClearPdf = { viewModel.clearPdfFromWhiteboard() },
                        onSelectAgentTab = { activeAgentTab = it },
                        onSaveScheduleToTasks = {
                            viewModel.saveOpenMaicScheduleToTasks { count ->
                                Toast.makeText(
                                    context,
                                    "تمت إضافة $count حصص تلقائياً لقسم مهامي ✓",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }

        // 3. Clean Floating Bottom White Question Capsule (Never overlaps nav bar / IME)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                shadowElevation = 5.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { requestVoiceWithDialect(selectedDialect) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("whiteboard_voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListeningVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "تحدث",
                            tint = if (isListeningVoice) Color(0xFFEF4444) else BoardMuted
                        )
                    }

                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = {
                            Text(
                                text = "اسأل فهيم أو ارفع PDF للشرح...",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.sp,
                                color = BoardMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("whiteboard_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = BoardCharcoal,
                            unfocusedTextColor = BoardCharcoal
                        ),
                        maxLines = 3
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BoardRoyalBlue)
                            .clickable(enabled = !isLoading) {
                                val q = questionInput.trim()
                                if (q.isNotBlank()) {
                                    questionInput = ""
                                    viewModel.loadWhiteboardLesson(q, selectedDialect, autoSpeak = true)
                                    replayNonce++
                                }
                            }
                            .testTag("whiteboard_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسم واشرح",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * الحالة الفارغة النظيفة للسبورة (WhiteboardState.Empty) — مطابقة للصورة المرجعية الأولى يساراً:
 * - كراسة مربعات بيضاء فارغة (#FFFFFF + #E2E8F0)
 * - مجسم فهيم 3D ممسكاً بالقلم الذكي
 * - العنوان: "السبورة جاهزة! اكتب سؤالك أو ارفع كتاب PDF"
 * - الزران الجاهزان: "رفع ملف PDF للشرح" و "تلخيص درس بخريطة ذهنية"
 * - أزرار اللهجة الثنائية فقط: "فصحى" و "مصري"
 */
@Composable
private fun WhiteboardEmptyCanvasContent(
    selectedDialect: FahimDialect,
    onUploadPdfClick: () -> Unit,
    onMindMapClick: () -> Unit,
    onDialectVoiceClick: (FahimDialect) -> Unit
) {
    ExcalidrawGridPaperBox(
        modifier = Modifier
            .fillMaxSize()
            .testTag("whiteboard_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FahimMascot3D(
                compactMode = false,
                showIdleSpeechBubble = false,
                showStatusBadge = false,
                mascotSizeDp = 152.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "السبورة جاهزة!",
                fontFamily = ReadexProFontFamily,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BoardCharcoal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "اكتب سؤالك أو ارفع كتاب PDF",
                fontFamily = ReadexProFontFamily,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Action Button 1: رفع ملف PDF للشرح
            OutlinedButton(
                onClick = onUploadPdfClick,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.6.dp, BoardBorderBlue),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = BoardCharcoal
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("empty_upload_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = BoardRoyalBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "رفع ملف PDF للشرح",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BoardCharcoal
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action Button 2: تلخيص درس بخريطة ذهنية
            OutlinedButton(
                onClick = onMindMapClick,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.6.dp, BoardBorderBlue),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = BoardCharcoal
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("empty_mindmap_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = BoardRoyalBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تلخيص درس بخريطة ذهنية",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BoardCharcoal
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dual Dialect Compact Mic Pills (فصحى / مصري ONLY — zero سعودي)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(FahimDialect.FUSHA, FahimDialect.EGYPTIAN).forEach { dialect ->
                    val isSelected = selectedDialect == dialect
                    Surface(
                        onClick = { onDialectVoiceClick(dialect) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                        border = BorderStroke(1.5.dp, if (isSelected) BoardRoyalBlue else BoardBorderBlue),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("empty_dialect_pill_${dialect.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = dialect.labelAr,
                                tint = BoardRoyalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = dialect.labelAr,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BoardCharcoal
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * الحالة النشطة للسبورة (WhiteboardState.Active) — مطابقة للصورة المرجعية الثانية:
 * - شريط صوتي علوي نظيف: [مصري | فصحى] + زر [استماع للشرح + موجة صوتية] بدون أي اقتطاع.
 * - مخطط Excalidraw على كراسة المربعات مع رسم حي تدريجي.
 * - جدول القوانين/المقارنة المنظم الموحد.
 * - خطوات الشرح المفصولة الاتجاه (عربي RTL / معادلة LTR).
 * - تبويبات OpenMAIC (خريطة ذهنية / جدول المراجعة / ملخص الوكلاء).
 */
@Composable
private fun WhiteboardActiveContent(
    session: OpenMaicBoardSession,
    pdfDoc: OpenMaicPdfDocument?,
    selectedDialect: FahimDialect,
    isSpeaking: Boolean,
    activeStepIndex: Int,
    activeEngineLabel: String,
    voiceGender: FahimVoiceGender,
    speechRate: Float,
    drawProgress: Float,
    handwritingProgress: Float,
    activeDrawTool: ExcalidrawDrawTool,
    selectedInkColorHex: Long,
    studentInkStrokes: List<StudentInkElement>,
    showPdfPreview: Boolean,
    activeAgentTab: Int,
    onSelectDialect: (FahimDialect) -> Unit,
    onToggleSpeak: () -> Unit,
    onToggleVoiceGender: () -> Unit,
    onCycleSpeed: () -> Unit,
    onReplayDrawing: () -> Unit,
    onClearBoard: () -> Unit,
    onSelectDrawTool: (ExcalidrawDrawTool) -> Unit,
    onSelectInkColor: (Long) -> Unit,
    onAddStudentStroke: (StudentInkElement) -> Unit,
    onEraseNearPoint: (androidx.compose.ui.geometry.Offset) -> Unit,
    onUndoStudentInk: () -> Unit,
    onClearStudentInk: () -> Unit,
    onTogglePdfPreview: () -> Unit,
    onPrevPdfPage: () -> Unit,
    onNextPdfPage: () -> Unit,
    onClearPdf: () -> Unit,
    onSelectAgentTab: (Int) -> Unit,
    onSaveScheduleToTasks: () -> Unit
) {
    val lesson = session.lesson

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardPageBg)
            .verticalScroll(
                state = rememberScrollState(),
                enabled = activeDrawTool == ExcalidrawDrawTool.PAN_SCROLL
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Clean Non-Truncated Voice Control Strip (Matches Ref Image 2 exactly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("whiteboard_voice_control_bar"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented Dialect Pill: مصري | فصحى ONLY
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(FahimDialect.EGYPTIAN, FahimDialect.FUSHA).forEach { dialect ->
                        val selected = selectedDialect == dialect
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) BoardRoyalBlue else Color.Transparent)
                                .clickable { onSelectDialect(dialect) }
                                .testTag("dialect_toggle_${dialect.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dialect.labelAr,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else BoardCharcoal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Wide Non-Truncated "استماع للشرح" + Live Audio Waveform Pill
            Surface(
                onClick = onToggleSpeak,
                shape = RoundedCornerShape(50),
                color = if (isSpeaking) Color(0xFFEFF6FF) else Color.White,
                border = BorderStroke(
                    1.3.dp,
                    if (isSpeaking) BoardRoyalBlue else Color(0xFF93C5FD)
                ),
                modifier = Modifier
                    .weight(1.18f)
                    .height(48.dp)
                    .testTag("whiteboard_listen_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isSpeaking) "إيقاف الشرح" else "استماع للشرح",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSpeaking) Color(0xFFDC2626) else BoardCharcoal,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSpeaking) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "إيقاف",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        FahimAnimatedAudioWaveform(isActive = isSpeaking)
                    }
                }
            }
        }

        // Compact secondary controls row (Voice Gender, Speed, Replay, Clear) — never crowds Voice Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactBoardControlChip(
                icon = Icons.Default.RecordVoiceOver,
                label = voiceGender.labelAr,
                onClick = onToggleVoiceGender,
                modifier = Modifier.testTag("whiteboard_gender_chip")
            )
            CompactBoardControlChip(
                icon = Icons.Default.Speed,
                label = "${speechRate}x",
                onClick = onCycleSpeed,
                modifier = Modifier.testTag("whiteboard_speed_chip")
            )
            CompactBoardControlChip(
                icon = Icons.Default.Replay,
                label = "إعادة الكتابة",
                onClick = onReplayDrawing,
                modifier = Modifier.testTag("whiteboard_replay_chip")
            )
            Spacer(modifier = Modifier.weight(1f))
            CompactBoardControlChip(
                icon = Icons.Default.DeleteSweep,
                label = "مسح",
                tint = Color(0xFFDC2626),
                onClick = onClearBoard,
                modifier = Modifier.testTag("whiteboard_clear_chip")
            )
        }

        // Neural Voice Engine Live Indicator
        if (activeEngineLabel.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaking) BoardEmerald else BoardRoyalBlue)
                )
                Text(
                    text = if (isSpeaking) "يتحدث الآن: $activeEngineLabel" else "الصوت الجاهز: $activeEngineLabel",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BoardMuted
                )
            }
        }

        // 2. Active PDF Page Selector Bar (when a book/PDF is loaded)
        if (pdfDoc != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("openmaic_pdf_bar")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pdfDoc.displayName,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BoardCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onTogglePdfPreview, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (showPdfPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "معاينة الصفحة",
                                    tint = BoardRoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onClearPdf, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق الملف",
                                    tint = BoardMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onPrevPdfPage,
                            enabled = pdfDoc.currentPageIndex > 0,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                            Text("السابقة", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                        }
                        Text(
                            text = "صفحة ${pdfDoc.currentPageIndex + 1} من ${pdfDoc.pageCount}",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BoardRoyalBlue
                        )
                        OutlinedButton(
                            onClick = onNextPdfPage,
                            enabled = pdfDoc.currentPageIndex < pdfDoc.pageCount - 1,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("التالية", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                            Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (showPdfPreview && pdfDoc.currentPagePreviewBitmap != null) {
                        Image(
                            bitmap = pdfDoc.currentPagePreviewBitmap.asImageBitmap(),
                            contentDescription = "معاينة صفحة PDF",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                        )
                    }
                }
            }
        }

        // 3. White Grid-Paper Excalidraw Animated & Interactive Diagram Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(
                width = if (activeDrawTool != ExcalidrawDrawTool.PAN_SCROLL) 1.6.dp else 1.dp,
                color = if (activeDrawTool != ExcalidrawDrawTool.PAN_SCROLL) BoardRoyalBlue else Color(0xFFCBD5E1)
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            ExcalidrawGridPaperBox(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lesson.title,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BoardCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        if (drawProgress in 0.01f..0.985f) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, BoardRoyalBlue.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "فهيم يرسم الآن ✎",
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BoardRoyalBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Real Interactive Excalidraw Student Drawing Toolbar
                    InteractiveExcalidrawToolbar(
                        activeTool = activeDrawTool,
                        selectedColorHex = selectedInkColorHex,
                        canUndo = studentInkStrokes.isNotEmpty(),
                        onSelectTool = onSelectDrawTool,
                        onSelectColor = onSelectInkColor,
                        onUndo = onUndoStudentInk,
                        onClearInk = onClearStudentInk
                    )

                    ExcalidrawBoardRenderer(
                        lesson = lesson,
                        progress = drawProgress,
                        activeTool = activeDrawTool,
                        selectedColorHex = selectedInkColorHex,
                        studentStrokes = studentInkStrokes,
                        onAddStudentStroke = onAddStudentStroke,
                        onEraseNearPoint = onEraseNearPoint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(235.dp)
                    )
                }
            }
        }

        // 4. Unified Clean Comparison / Laws Table (Matches Ref Image 2)
        lesson.structuredTable?.let { tbl ->
            ExcalidrawCleanTable(table = tbl)
        }

        // 5. Separated RTL Arabic + LTR Formula Steps with Live Handwriting Reveal (Zero Overlap!)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lesson.steps.forEachIndexed { idx, step ->
                val stepReveal = (handwritingProgress - idx.toFloat()).coerceIn(0f, 1f)
                val isWritingThisStep = stepReveal in 0.01f..0.99f
                // Ensure step 0 begins visibly even while diagram finishes drawing
                val effectiveReveal = if (idx == 0 && drawProgress > 0.6f && stepReveal < 0.05f) {
                    ((drawProgress - 0.6f) / 0.4f).coerceIn(0.05f, 0.35f)
                } else {
                    stepReveal
                }
                SeparatedEquationStepCard(
                    step = step,
                    isActive = isSpeaking && activeStepIndex == idx,
                    revealFraction = effectiveReveal,
                    isCurrentlyWriting = isWritingThisStep
                )
            }
        }

        // 6. OpenMAIC Multi-Agent Companion Tabs (MindMap / Study Table / Agent Summary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                Triple(0, "خريطة ذهنية", Icons.Default.AccountTree),
                Triple(1, "جدول المراجعة", Icons.Default.CalendarMonth),
                Triple(2, "ملخص OpenMAIC", Icons.Default.AutoAwesome)
            )
            tabs.forEach { (tabIndex, label, icon) ->
                val selected = activeAgentTab == tabIndex
                Surface(
                    onClick = { onSelectAgentTab(tabIndex) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) BoardRoyalBlue else Color.White,
                    border = BorderStroke(1.dp, if (selected) BoardRoyalBlue else Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("openmaic_agent_tab_$tabIndex")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) Color.White else BoardRoyalBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else BoardCharcoal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        when (activeAgentTab) {
            0 -> FahimMindMapCanvas(mindMap = session.mindMap)
            1 -> FahimSmartTableCard(
                table = session.studyTable,
                isAddedToSchedule = session.isSavedToTasks,
                onAddToSchedule = onSaveScheduleToTasks
            )
            else -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "خلاصة وكلاء OpenMAIC • ${session.topicTitle}",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BoardRoyalBlue
                        )
                        Text(
                            text = session.agentSummaryText,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.sp,
                            color = BoardCharcoal,
                            lineHeight = 23.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun CompactBoardControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = BoardRoyalBlue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BoardCharcoal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FahimAnimatedAudioWaveform(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        val heights = listOf(0.35f, 0.65f, 1.0f, 0.55f, 0.85f, 0.45f)
        heights.forEachIndexed { i, base ->
            val hFrac = if (isActive) {
                0.28f + 0.72f * abs(sin(phase + i * 0.75f))
            } else {
                base
            }
            Box(
                modifier = Modifier
                    .width(2.6.dp)
                    .height((18f * hFrac).dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive && i in 2..3) BoardAmberGold else BoardRoyalBlue)
            )
        }
    }
}
