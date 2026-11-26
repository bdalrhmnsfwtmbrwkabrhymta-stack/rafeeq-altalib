package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.ai.AiChatStudyHelper
import com.example.domain.ai.FahimChatMessage
import com.example.domain.ai.FahimConfig
import com.example.domain.ai.FahimStudyTableData
import com.example.domain.ai.FahimStudyTableRow
import com.example.ui.MainViewModel
import com.example.ui.components.AiFormattedContent
import com.example.ui.screens.ai.FahimInteractiveMascot
import com.example.ui.screens.ai.FahimMindMapCanvas
import com.example.ui.screens.ai.FahimSmartTableCard
import com.example.ui.screens.ai.StudyPlanBuilderDialog
import com.example.ui.theme.ReadexProFontFamily
import java.io.ByteArrayOutputStream
import java.io.InputStream

private val DaylightBackground = Color(0xFFF8FAFC)
private val DaylightSurface = Color(0xFFFFFFFF)
private val DaylightBorder = Color(0xFFE2E8F0)
private val DaylightInk = Color(0xFF1E1B18)
private val DaylightMuted = Color(0xFF64748B)
private val FahimBlue = Color(0xFF1A6FE3)
private val FahimAmber = Color(0xFFD97706)
private val FahimEmerald = Color(0xFF10B981)
private val FahimTeal = Color(0xFF0D9488)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val fahimMessages by viewModel.fahimMessages.collectAsState()
    val isFahimLoading by viewModel.isFahimLoading.collectAsState()
    val isSuggestedWeeklyTableAdded by viewModel.isSuggestedWeeklyTableAdded.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showScanChoiceDialog by remember { mutableStateOf(false) }
    var showStudyPlanDialog by remember { mutableStateOf(false) }
    var showComparisonTopicDialog by remember { mutableStateOf(false) }
    var comparisonTopicInput by remember { mutableStateOf("") }
    var showDevPinDialog by remember { mutableStateOf(false) }
    var showDevPanelDialog by remember { mutableStateOf(false) }
    var devPinInput by remember { mutableStateOf("") }
    var devPinError by remember { mutableStateOf(false) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var pdfExportStatus by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val ttsManager = remember { AiChatStudyHelper.ArabicTtsManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    LaunchedEffect(fahimMessages.size, isFahimLoading) {
        if (fahimMessages.isNotEmpty()) {
            listState.animateScrollToItem(fahimMessages.size)
        }
    }

    val suggestedWeeklyTable = remember {
        FahimStudyTableData(
            title = "جدول المذاكرة الأسبوعي المقترح",
            columnHeaders = listOf("اليوم", "المهمة والمادة", "المدة"),
            rows = listOf(
                FahimStudyTableRow("السبت", "مراجعة الفصل الأول وحل أمثلة", "ساعتان", dayOfWeek = 6, startTime = "16:00", endTime = "18:00", tintHex = 0xFFFFFFFFL),
                FahimStudyTableRow("الأحد", "تلخيص القوانين ورسم خريطة ذهنية", "ساعة ونصف", dayOfWeek = 7, startTime = "17:00", endTime = "18:30", tintHex = 0xFFEFF6FFL),
                FahimStudyTableRow("الاثنين", "حل تمارين ومسائل امتحانات سابقة", "ساعتان", dayOfWeek = 1, startTime = "16:30", endTime = "18:30", tintHex = 0xFFECFDF5L),
                FahimStudyTableRow("الثلاثاء", "مراجعة نقاط الصعوبة والأخطاء", "ساعة ونصف", dayOfWeek = 2, startTime = "18:00", endTime = "19:30", tintHex = 0xFFFEF3C7L),
                FahimStudyTableRow("الأربعاء", "اختبار تجريبي شامل ومراجعة سريعة", "ساعة ونصف", dayOfWeek = 3, startTime = "17:00", endTime = "18:30", tintHex = 0xFFF1F5F9L)
            ),
            isComparisonOnly = false
        )
    }

    // Camera capture
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            runCatching {
                val out = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val bytes = out.toByteArray()
                selectedImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                selectedImageName = "صورة مسألة بالكاميرا (${bytes.size / 1024} KB)"
                Toast.makeText(context, "تم التقاط الصورة، اضغط إرسال لحلها مع فهيم", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraCaptureLauncher.launch(null)
        } else {
            Toast.makeText(context, "مطلوب إذن الكاميرا لمسح المسائل", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val stream: InputStream? = context.contentResolver.openInputStream(it)
                val bytes = stream?.use { s -> s.readBytes() }
                if (bytes != null) {
                    selectedImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    selectedImageName = "صورة مرفقة (${bytes.size / 1024} KB)"
                }
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (recognized.isNotBlank()) {
                inputPrompt = if (inputPrompt.isBlank()) recognized else "$inputPrompt $recognized"
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بسؤالك لفهيم...")
            }
            runCatching {
                isListeningVoice = true
                speechRecognizerLauncher.launch(intent)
            }.onFailure {
                isListeningVoice = false
                Toast.makeText(context, "الإملاء الصوتي غير متاح حالياً على هذا الجهاز", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "مطلوب إذن الميكروفون للتحدث مع فهيم", Toast.LENGTH_SHORT).show()
        }
    }

    fun submitFahimPrompt(textToSend: String, withAttachedImage: Boolean = false) {
        val normalized = textToSend.trim()
        if (!withAttachedImage && (normalized.contains("السبورة") || normalized.contains("مساحة الجزء المظلل") || normalized.contains("ارسم المسألة"))) {
            viewModel.requestOpenWhiteboardWithPrompt(normalized)
            onNavigate("fahim_whiteboard")
            return
        }
        val img = if (withAttachedImage) selectedImageBase64 else null
        viewModel.sendFahimMessage(
            prompt = textToSend,
            imageBytesBase64 = img,
            onTableAutoAdded = { count ->
                Toast.makeText(
                    context,
                    "تمت إضافة $count حصص تلقائياً لقسم مهامي وجدولك ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        if (withAttachedImage) {
            selectedImageBase64 = null
            selectedImageName = null
        }
    }

    if (showScanChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showScanChoiceDialog = false },
            containerColor = DaylightSurface,
            title = {
                Text(
                    text = "مسح صورة مسألة أو صفحة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DaylightInk
                )
            },
            text = {
                Text(
                    text = "التقط صورة مباشرة بالكاميرا أو اختر صورة من المعرض ليقوم فهيم بتحليلها وشرحها لك خطوة بخطوة.",
                    fontFamily = ReadexProFontFamily,
                    color = DaylightMuted,
                    fontSize = 14.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showScanChoiceDialog = false
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) cameraCaptureLauncher.launch(null)
                        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FahimBlue)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصوير بالكاميرا", fontFamily = ReadexProFontFamily)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showScanChoiceDialog = false
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("من المعرض", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    if (showComparisonTopicDialog) {
        AlertDialog(
            onDismissRequest = { showComparisonTopicDialog = false },
            containerColor = DaylightSurface,
            title = {
                Text(
                    text = "تصميم جدول مقارنة ذكي",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DaylightInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "اكتب المفهومين المراد المقارنة بينهما ليصمم فهيم جدول مقارنة ملوناً ومنظماً:",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        color = DaylightMuted
                    )
                    OutlinedTextField(
                        value = comparisonTopicInput,
                        onValueChange = { comparisonTopicInput = it },
                        placeholder = { Text("مثال: الانقسام الميتوزي والميوزي") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val topic = comparisonTopicInput.trim().ifBlank { "الخلية النباتية والخلية الحيوانية" }
                        showComparisonTopicDialog = false
                        comparisonTopicInput = ""
                        submitFahimPrompt("صمم لي جدول مقارنة منظم بالألوان بين: $topic")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DaylightInk)
                ) {
                    Text("تصميم الجدول", fontFamily = ReadexProFontFamily, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showComparisonTopicDialog = false }) {
                    Text("إلغاء", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    if (showStudyPlanDialog) {
        StudyPlanBuilderDialog(
            viewModel = viewModel,
            onDismiss = { showStudyPlanDialog = false },
            onRequireNetwork = {
                Toast.makeText(context, "يرجى التأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
            }
        )
    }

    pdfExportStatus?.let { status ->
        AlertDialog(
            onDismissRequest = { pdfExportStatus = null },
            containerColor = DaylightSurface,
            title = { Text("تصدير ملف PDF", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold) },
            text = { Text(status, fontFamily = ReadexProFontFamily) },
            confirmButton = {
                Button(onClick = { pdfExportStatus = null }) {
                    Text("حسناً", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    // Secret Developer Control Panel PIN Gate (PIN: 2026)
    if (showDevPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showDevPinDialog = false
                devPinInput = ""
                devPinError = false
            },
            containerColor = DaylightSurface,
            icon = { Icon(Icons.Default.AdminPanelSettings, null, tint = FahimBlue) },
            title = {
                Text(
                    text = "لوحة المطور السرية",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DaylightInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "أدخل الرمز السري للمطور للتحكم في مفاتيح Gemini وروابط الصوت:",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.5.sp,
                        color = DaylightMuted
                    )
                    OutlinedTextField(
                        value = devPinInput,
                        onValueChange = {
                            devPinInput = it
                            devPinError = false
                        },
                        label = { Text("الرمز السري (PIN)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = devPinError,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("developer_pin_field")
                    )
                    if (devPinError) {
                        Text(
                            text = "الرمز غير صحيح",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.5.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (devPinInput.trim() == FahimConfig.DEVELOPER_CONTROL_PIN) {
                            showDevPinDialog = false
                            devPinInput = ""
                            devPinError = false
                            showDevPanelDialog = true
                        } else {
                            devPinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FahimBlue),
                    modifier = Modifier.testTag("developer_pin_confirm")
                ) {
                    Text("دخول", fontFamily = ReadexProFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDevPinDialog = false }) {
                    Text("إلغاء", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    if (showDevPanelDialog) {
        var devGeminiKey by remember { mutableStateOf(userSettings.geminiApiKey) }
        var devPiperUrl by remember { mutableStateOf(userSettings.piperTtsUrl) }
        var devCoquiUrl by remember { mutableStateOf(userSettings.coquiTtsUrl) }
        var devVoiceKey by remember { mutableStateOf(userSettings.voiceTtsApiKey) }

        AlertDialog(
            onDismissRequest = { showDevPanelDialog = false },
            containerColor = DaylightSurface,
            title = {
                Text(
                    text = "إعدادات المطور (Gemini & TTS)",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DaylightInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = devGeminiKey,
                        onValueChange = { devGeminiKey = it },
                        label = { Text("Gemini API Key (gemini-2.5-flash)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = devPiperUrl,
                        onValueChange = { devPiperUrl = it },
                        label = { Text("Piper TTS URL (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = devCoquiUrl,
                        onValueChange = { devCoquiUrl = it },
                        label = { Text("Coqui TTS URL (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = devVoiceKey,
                        onValueChange = { devVoiceKey = it },
                        label = { Text("Voice TTS API Key (اختياري)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDeveloperFahimSettings(
                            geminiKey = devGeminiKey,
                            piperUrl = devPiperUrl,
                            coquiUrl = devCoquiUrl,
                            voiceKey = devVoiceKey
                        )
                        showDevPanelDialog = false
                        Toast.makeText(context, "تم حفظ إعدادات المطور بأمان", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FahimEmerald)
                ) {
                    Text("حفظ", fontFamily = ReadexProFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDevPanelDialog = false }) {
                    Text("إغلاق", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaylightBackground)
            .statusBarsPadding()
    ) {
        // Pure White Top Header Bar
        Surface(
            color = DaylightSurface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { onNavigate("home") },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fahim_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = DaylightInk
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column {
                            Text(
                                text = "فهيم • المساعد الدراسي الذكي",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = DaylightInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { showDevPinDialog = true }
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(FahimEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "متصل بـ Gemini AI",
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onNavigate("fahim_whiteboard") },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("fahim_open_whiteboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "سبورة فهيم الذكية",
                                tint = FahimBlue
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("fahim_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "خيارات فهيم",
                                    tint = DaylightInk
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                containerColor = DaylightSurface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("سبورة فهيم الذكية 🎨", fontFamily = ReadexProFontFamily) },
                                    leadingIcon = { Icon(Icons.Default.Brush, null, tint = FahimBlue) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("fahim_whiteboard")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("فتح قسم مهامي", fontFamily = ReadexProFontFamily) },
                                    leadingIcon = { Icon(Icons.Default.PlaylistAddCheck, null, tint = FahimEmerald) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("tasks")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("فتح الجدول الدراسي", fontFamily = ReadexProFontFamily) },
                                    leadingIcon = { Icon(Icons.Default.EventNote, null, tint = FahimBlue) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("schedule")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("بناء خطة أسبوعية ذكية", fontFamily = ReadexProFontFamily) },
                                    leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = FahimAmber) },
                                    onClick = {
                                        showMoreMenu = false
                                        showStudyPlanDialog = true
                                    }
                                )
                                if (fahimMessages.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("تصدير المحادثة PDF", fontFamily = ReadexProFontFamily) },
                                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, tint = FahimTeal) },
                                        onClick = {
                                            showMoreMenu = false
                                            val exportBody = fahimMessages.joinToString("\n\n") { m ->
                                                if (m.isUser) "سؤال الطالب: ${m.text}" else "إجابة فهيم:\n${m.text}"
                                            }
                                            AiChatStudyHelper.exportToPdf(
                                                context = context,
                                                title = "ملخص محادثة فهيم الذكي",
                                                content = exportBody
                                            ) { success, msg, uri ->
                                                pdfExportStatus = msg
                                                if (success && uri != null) AiChatStudyHelper.sharePdf(context, uri)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("محادثة جديدة", fontFamily = ReadexProFontFamily) },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFEF4444)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.clearFahimChat()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("إعدادات المطور (PIN)", fontFamily = ReadexProFontFamily, color = DaylightMuted) },
                                    leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null, tint = DaylightMuted) },
                                    onClick = {
                                        showMoreMenu = false
                                        showDevPinDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = DaylightBorder)
            }
        }

        // Scrollable Main Body
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (fahimMessages.isEmpty()) {
                item {
                    FahimInteractiveMascot(compactMode = false)
                }

                item {
                    FahimQuickActionPillsGrid(
                        onTomorrowExamClick = {
                            submitFahimPrompt("نظّم لي جدول مراجعة مكثف لامتحان الغد مع خريطة ذهنية لأهم النقاط")
                        },
                        onMindMapClick = {
                            submitFahimPrompt("ارسم لي خريطة ذهنية ملخصة وملونة لأهم محاور وقوانين المنهج")
                        },
                        onCompareTableClick = {
                            showComparisonTopicDialog = true
                        },
                        onScanQuestionClick = {
                            showScanChoiceDialog = true
                        },
                        onWhiteboardClick = {
                            onNavigate("fahim_whiteboard")
                        }
                    )
                }

                item {
                    FahimSmartTableCard(
                        table = suggestedWeeklyTable,
                        isAddedToSchedule = isSuggestedWeeklyTableAdded,
                        onAddToSchedule = {
                            viewModel.insertFahimTableToApp(
                                table = suggestedWeeklyTable,
                                isSuggestedCard = true
                            ) { count ->
                                Toast.makeText(
                                    context,
                                    "تمت إضافة $count حصص تلقائياً لقسم مهامي ✓",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenTasks = { onNavigate("tasks") }
                    )
                }
            } else {
                item {
                    FahimInteractiveMascot(compactMode = true)
                }

                items(fahimMessages, key = { it.id }) { msg ->
                    FahimMessageBubble(
                        message = msg,
                        isSpeaking = speakingMessageId == msg.id,
                        onCopy = { textToCopy ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Fahim Reply", textToCopy))
                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                        },
                        onSpeakToggle = {
                            if (speakingMessageId == msg.id) {
                                ttsManager.stop()
                                speakingMessageId = null
                            } else {
                                speakingMessageId = msg.id
                                ttsManager.speak(msg.text) { active ->
                                    if (!active) speakingMessageId = null
                                }
                            }
                        },
                        onExportPdf = {
                            AiChatStudyHelper.exportToPdf(
                                context = context,
                                title = "شرح فهيم الدراسي",
                                content = msg.text
                            ) { success, statusMsg, uri ->
                                pdfExportStatus = statusMsg
                                if (success && uri != null) AiChatStudyHelper.sharePdf(context, uri)
                            }
                        },
                        onAddTableToSchedule = { table ->
                            viewModel.insertFahimTableToApp(
                                table = table,
                                messageId = msg.id
                            ) { count ->
                                Toast.makeText(
                                    context,
                                    "تمت إضافة $count حصص لقسم مهامي ✓",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenTasks = { onNavigate("tasks") }
                    )
                }

                if (isFahimLoading) {
                    item {
                        FahimLoadingBubble()
                    }
                }
            }
        }

        // Quick Action Strip during active chat
        if (fahimMessages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallQuickChip("سبورة فهيم 🎨", FahimBlue) {
                    onNavigate("fahim_whiteboard")
                }
                SmallQuickChip("جدول امتحان الغد", DaylightInk) {
                    submitFahimPrompt("نظّم لي جدول مراجعة مكثف لامتحان الغد مع خريطة ذهنية")
                }
                SmallQuickChip("خريطة ذهنية", FahimAmber) {
                    submitFahimPrompt("ارسم لي خريطة ذهنية ملخصة لهذا الموضوع")
                }
                SmallQuickChip("جدول مقارنة +", FahimBlue) {
                    showComparisonTopicDialog = true
                }
                SmallQuickChip("مسح صورة مسألة", FahimTeal) {
                    showScanChoiceDialog = true
                }
            }
        }

        // Floating Pure-White Rounded Input Dock (respects navigation bar & IME cleanly)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaylightBackground)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (selectedImageName != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Image, null, tint = FahimBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedImageName.orEmpty(),
                                fontFamily = ReadexProFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FahimBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedImageBase64 = null
                                selectedImageName = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إزالة الصورة", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = DaylightSurface,
                border = BorderStroke(1.2.dp, DaylightBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showScanChoiceDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("fahim_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "تصوير مسألة",
                            tint = FahimTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val hasAudioPerm = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasAudioPerm) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بسؤالك لفهيم...")
                                }
                                runCatching {
                                    isListeningVoice = true
                                    speechRecognizerLauncher.launch(intent)
                                }.onFailure {
                                    isListeningVoice = false
                                    Toast.makeText(context, "الإملاء الصوتي غير متاح حالياً", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("fahim_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListeningVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "تحدث بالصوت",
                            tint = if (isListeningVoice) Color(0xFFEF4444) else DaylightMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = {
                            Text(
                                text = "اسأل فهيم عن أي درس أو جدول...",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 14.5.sp,
                                color = DaylightMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DaylightInk,
                            unfocusedTextColor = DaylightInk
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(FahimBlue)
                            .clickable(enabled = !isFahimLoading) {
                                if (inputPrompt.isNotBlank() || selectedImageBase64 != null) {
                                    val outgoing = inputPrompt
                                    inputPrompt = ""
                                    submitFahimPrompt(outgoing, withAttachedImage = selectedImageBase64 != null)
                                }
                            }
                            .testTag("send_ai_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FahimQuickActionPillsGrid(
    onTomorrowExamClick: () -> Unit,
    onMindMapClick: () -> Unit,
    onCompareTableClick: () -> Unit,
    onScanQuestionClick: () -> Unit,
    onWhiteboardClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FahimActionPill(
                label = "جدول امتحان الغد",
                icon = Icons.Default.CalendarToday,
                color = DaylightInk,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pill_tomorrow_exam"),
                onClick = onTomorrowExamClick
            )
            FahimActionPill(
                label = "خريطة ذهنية",
                icon = Icons.Default.AccountTree,
                color = FahimAmber,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pill_mind_map"),
                onClick = onMindMapClick
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FahimActionPill(
                label = "تصميم جدول مقارنة +",
                icon = Icons.Default.CompareArrows,
                color = FahimBlue,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pill_comparison_table"),
                onClick = onCompareTableClick
            )
            FahimActionPill(
                label = "مسح صورة مسألة",
                icon = Icons.Default.CenterFocusStrong,
                color = FahimTeal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pill_scan_question"),
                onClick = onScanQuestionClick
            )
        }
        FahimActionPill(
            label = "سبورة فهيم الذكية • رسم حي وشرح صوتي",
            icon = Icons.Default.Brush,
            color = FahimBlue,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pill_smart_whiteboard"),
            onClick = onWhiteboardClick
        )
    }
}

@Composable
private fun FahimActionPill(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        color = color,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                fontFamily = ReadexProFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmallQuickChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            fontFamily = ReadexProFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FahimMessageBubble(
    message: FahimChatMessage,
    isSpeaking: Boolean,
    onCopy: (String) -> Unit,
    onSpeakToggle: () -> Unit,
    onExportPdf: () -> Unit,
    onAddTableToSchedule: (FahimStudyTableData) -> Unit,
    onOpenTasks: () -> Unit
) {
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color = FahimBlue,
                shadowElevation = 2.dp,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.hasAttachedImage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "صورة مرفقة",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    Text(
                        text = message.text,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DaylightSurface,
                border = BorderStroke(1.2.dp, DaylightBorder),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = FahimBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "فهيم الذكي",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FahimBlue
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onSpeakToggle,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "استماع",
                                    tint = if (isSpeaking) Color(0xFFEF4444) else DaylightMuted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopy(message.text) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ",
                                    tint = DaylightMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onExportPdf,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "تصدير PDF",
                                    tint = FahimTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    AiFormattedContent(
                        text = message.text,
                        isUser = false,
                        onCopyMathBlock = onCopy,
                        bodyFontSize = 16.5.sp,
                        bodyLineHeight = 26.sp,
                        bodyColor = DaylightInk,
                        headerColor = FahimBlue
                    )
                }
            }

            message.mindMap?.let { mm ->
                FahimMindMapCanvas(mindMap = mm)
            }

            message.studyTable?.let { tbl ->
                FahimSmartTableCard(
                    table = tbl,
                    isAddedToSchedule = message.isAddedToSchedule,
                    onAddToSchedule = { onAddTableToSchedule(tbl) },
                    onOpenTasks = onOpenTasks
                )
            }
        }
    }
}

@Composable
private fun FahimLoadingBubble() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DaylightSurface,
        border = BorderStroke(1.2.dp, DaylightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = FahimBlue
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "فهيم يفكر ويجهز الشرح والجدول...",
                fontFamily = ReadexProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DaylightInk
            )
        }
    }
}
