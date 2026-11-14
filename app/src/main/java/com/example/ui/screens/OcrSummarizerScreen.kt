package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSummarizerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToFlashcards: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    val isProcessing by viewModel.isOcrProcessing.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val extractedText by viewModel.ocrExtractedText.collectAsState()
    val ocrStats by viewModel.lastOcrResult.collectAsState()
    val geminiSummary by viewModel.ocrGeminiSummary.collectAsState()
    val summaryError by viewModel.ocrSummaryError.collectAsState()
    val selectedStyle by viewModel.selectedSummaryStyle.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Editable OCR Text, 1: AI Summary, 2: Original Image
    var isSpeaking by remember { mutableStateOf(false) }

    // Editable text state inside the app
    var editableLessonText by remember { mutableStateOf("") }
    var lessonTitle by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }

    // Sync extractedText to editableLessonText when ML Kit produces text
    LaunchedEffect(extractedText) {
        if (extractedText.isNotBlank()) {
            editableLessonText = extractedText
            if (lessonTitle.isBlank()) {
                val firstNonEmptyLine = extractedText.lines().firstOrNull { it.isNotBlank() } ?: ""
                lessonTitle = if (firstNonEmptyLine.length > 35) {
                    firstNonEmptyLine.take(35) + "..."
                } else if (firstNonEmptyLine.isNotBlank()) {
                    firstNonEmptyLine
                } else {
                    if (isEnglish) "Lesson Summary - ${SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date())}"
                    else "ملخص درس - ${SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date())}"
                }
            }
        }
    }

    // Init TTS for audio reading
    LaunchedEffect(Unit) {
        AnkiTTS.init(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            AnkiTTS.stop()
        }
    }

    // Process bitmap with ML Kit
    fun processBitmapWithMlKit(bitmap: Bitmap) {
        selectedBitmap = bitmap
        selectedTab = 0
        viewModel.processImageWithMlKit(bitmap) { success, msg ->
            if (success) {
                Toast.makeText(
                    context,
                    if (isEnglish) "Text extracted with ML Kit! You can now edit it ✏️"
                    else "تم استخراج نصوص الملخص بـ ML Kit! يمكنك تعديلها وحفظها الآن ✏️",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            processBitmapWithMlKit(bitmap)
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(
                context,
                if (isEnglish) "Camera permission is required to capture lesson summaries"
                else "إذن الكاميرا مطلوب لتصوير ملخصات الدروس",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun triggerCameraCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Photo picker (Google Play policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    processBitmapWithMlKit(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    if (isEnglish) "Failed to load image" else "تعذر تحميل الصورة",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Format paragraphs helper
    fun autoFormatParagraphs() {
        if (editableLessonText.isBlank()) return
        val formatted = editableLessonText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        editableLessonText = formatted
        viewModel.setOcrExtractedText(formatted)
        Toast.makeText(
            context,
            if (isEnglish) "Paragraphs formatted automatically ✨" else "تم تنسيق الفقرات تلقائياً ✨",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Share via WhatsApp helper
    fun shareViaWhatsApp() {
        val titleText = if (lessonTitle.isNotBlank()) lessonTitle else (if (isEnglish) "Lesson Summary" else "ملخص درس")
        val shareBody = buildString {
            append("📚 *")
            append(titleText)
            append("*\n\n")
            append(editableLessonText.trim())
            append("\n\n─────────────────────\n")
            append(if (isEnglish) "📱 Extracted via Student Planner app (ML Kit OCR)" else "📱 تم استخراج الملخص عبر تطبيق الطالب المجتهد (ML Kit OCR)")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareBody)
                },
                if (isEnglish) "Share Lesson Summary" else "مشاركة ملخص الدرس"
            )
            context.startActivity(chooser)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "Lesson Summaries Scanner" else "ماسح ملخصات الدروس",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnglish) "Google ML Kit OCR • Editable Text Studio" else "استخراج النصوص بـ Google ML Kit • تحرير وحفظ الملخصات",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (editableLessonText.isNotBlank() || selectedBitmap != null) {
                        IconButton(
                            onClick = {
                                selectedBitmap = null
                                editableLessonText = ""
                                lessonTitle = ""
                                viewModel.clearOcrState()
                                AnkiTTS.stop()
                                isSpeaking = false
                            },
                            modifier = Modifier.testTag("clear_ocr_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = if (isEnglish) "Reset" else "إعادة ضبط",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Camera & Gallery Capture Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEnglish) "Capture Lesson Summary" else "تصوير ملخص الدرس أو الكشكول",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (isEnglish) "Converts handwritten or printed notes to editable text" else "تحويل أوراق الدروس والملازم لنصوص فورية قابلة للتعديل",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Capture buttons: Camera + Gallery
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { triggerCameraCapture() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("camera_ocr_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Camera Capture" else "التقاط بالكاميرا 📷",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("gallery_ocr_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "From Gallery" else "اختيار من الاستوديو 🖼️",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Thumbnail preview if image loaded
                        if (selectedBitmap != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = selectedBitmap!!.asImageBitmap(),
                                    contentDescription = "Captured summary",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) "Captured Lesson Image" else "صورة الملخص الحالية",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${selectedBitmap!!.width}×${selectedBitmap!!.height} px",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { triggerCameraCapture() }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEnglish) "Retake" else "إعادة تصوير", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. ML Kit Processing Indicator
            if (isProcessing || isAiLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (isProcessing) {
                                        if (isEnglish) "Extracting text with Google ML Kit..."
                                        else "جاري استخراج وتحويل النصوص بـ Google ML Kit..."
                                    } else {
                                        if (isEnglish) "Generating AI study breakdown..."
                                        else "جاري صياغة التلخيص والأسئلة بـ Gemini..."
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isEnglish) "Analyzing words, lines, and formatting..."
                                    else "قراءة الأسطر والفقرات وتحويلها لنصوص جاهزة للتعديل...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Error Alert if any
            if (summaryError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = summaryError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 4. Main Section: Editable Text Studio & AI Summaries
            if (editableLessonText.isNotBlank() || geminiSummary != null || selectedBitmap != null) {
                // Section Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) "Editable Text ✏️" else "النص القابل للتعديل ✏️",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            modifier = Modifier.testTag("tab_editable_ocr_text")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) "AI Summary ✨" else "التلخيص الذكي ✨",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            modifier = Modifier.testTag("tab_gemini_summary")
                        )
                        if (selectedBitmap != null) {
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isEnglish) "Image 🖼️" else "الصورة 🖼️",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("tab_image_preview")
                            )
                        }
                    }
                }

                // TAB 0: EDITABLE TEXT STUDIO (نصوص قابلة للتعديل داخل التطبيق)
                if (selectedTab == 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                // Title and live metrics
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isEnglish) "Editable Lesson Text (ML Kit)" else "نص الملخص القابل للتعديل",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        val words = if (editableLessonText.isBlank()) 0 else editableLessonText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                                        val lines = if (editableLessonText.isBlank()) 0 else editableLessonText.lines().count { it.isNotBlank() }
                                        Text(
                                            text = if (isEnglish) "$words words • $lines lines • ${editableLessonText.length} chars"
                                            else "$words كلمة • $lines سطر • ${editableLessonText.length} حرف",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Quick action buttons (TTS + Copy + Share)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Read aloud with TTS
                                        IconButton(
                                            onClick = {
                                                if (isSpeaking) {
                                                    AnkiTTS.stop()
                                                    isSpeaking = false
                                                } else {
                                                    if (editableLessonText.isNotBlank()) {
                                                        AnkiTTS.speak(editableLessonText)
                                                        isSpeaking = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("tts_editable_text_button")
                                        ) {
                                            Icon(
                                                if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = "Read Aloud",
                                                tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Copy to clipboard
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(editableLessonText))
                                                Toast.makeText(
                                                    context,
                                                    if (isEnglish) "Text copied to clipboard" else "تم نسخ النص للحافظة بنجاح",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.testTag("copy_editable_text_button")
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy Text",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Share via WhatsApp
                                        IconButton(
                                            onClick = { shareViaWhatsApp() },
                                            modifier = Modifier.testTag("share_whatsapp_button")
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share via WhatsApp",
                                                tint = Color(0xFF25D366)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Lesson Title Field
                                OutlinedTextField(
                                    value = lessonTitle,
                                    onValueChange = { lessonTitle = it },
                                    label = { Text(if (isEnglish) "Lesson / Summary Title" else "عنوان ملخص الدرس") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lesson_title_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = {
                                        Icon(Icons.Default.Title, contentDescription = null)
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Subject Selector Chips
                                if (subjects.isNotEmpty()) {
                                    Text(
                                        text = if (isEnglish) "Assign to Subject (optional):" else "ربط بمادة دراسية (اختياري):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(subjects) { subject ->
                                            FilterChip(
                                                selected = selectedSubjectId == subject.id,
                                                onClick = {
                                                    selectedSubjectId = if (selectedSubjectId == subject.id) null else subject.id
                                                },
                                                label = { Text(subject.name, fontSize = 11.sp) },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // Text Editing Helper Toolbar
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Auto-format paragraphs
                                        AssistChip(
                                            onClick = { autoFormatParagraphs() },
                                            leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(if (isEnglish) "Format Paragraphs" else "تنسيق الفقرات ✨", fontSize = 11.sp) }
                                        )

                                        // Insert bullet point
                                        AssistChip(
                                            onClick = {
                                                editableLessonText = if (editableLessonText.endsWith("\n") || editableLessonText.isEmpty()) {
                                                    editableLessonText + "• "
                                                } else {
                                                    editableLessonText + "\n• "
                                                }
                                                viewModel.setOcrExtractedText(editableLessonText)
                                            },
                                            leadingIcon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(if (isEnglish) "Bullet •" else "نقطة •", fontSize = 11.sp) }
                                        )

                                        // Insert numbering
                                        AssistChip(
                                            onClick = {
                                                editableLessonText = if (editableLessonText.endsWith("\n") || editableLessonText.isEmpty()) {
                                                    editableLessonText + "1. "
                                                } else {
                                                    editableLessonText + "\n1. "
                                                }
                                                viewModel.setOcrExtractedText(editableLessonText)
                                            },
                                            leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(if (isEnglish) "Number 1." else "ترقيم 1.", fontSize = 11.sp) }
                                        )

                                        // Insert divider
                                        AssistChip(
                                            onClick = {
                                                editableLessonText = editableLessonText + "\n─────────────────────\n"
                                                viewModel.setOcrExtractedText(editableLessonText)
                                            },
                                            leadingIcon = { Icon(Icons.Default.HorizontalRule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(if (isEnglish) "Divider" else "خط فاصل ──", fontSize = 11.sp) }
                                        )

                                        // Revert to raw OCR
                                        if (extractedText.isNotBlank() && editableLessonText != extractedText) {
                                            AssistChip(
                                                onClick = {
                                                    editableLessonText = extractedText
                                                    Toast.makeText(
                                                        context,
                                                        if (isEnglish) "Reset to original ML Kit text" else "تمت استعادة النص الأصلي من ML Kit",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                label = { Text(if (isEnglish) "Reset" else "استعادة الأصل", fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Main Editable Text Area
                                OutlinedTextField(
                                    value = editableLessonText,
                                    onValueChange = {
                                        editableLessonText = it
                                        viewModel.setOcrExtractedText(it)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 380.dp)
                                        .testTag("editable_lesson_text"),
                                    shape = RoundedCornerShape(14.dp),
                                    placeholder = {
                                        Text(
                                            if (isEnglish) "Extracted lesson text will appear here. Tap to edit, add headers, format, or organize points..."
                                            else "سيظهر نص الملخص المستخرج هنا. انقر لتعديل أي كلمة، إضافة عناوين، تنظيم الفقرات، أو مسح الزوائد..."
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Save and Action Buttons
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 1. Save to Student Notes (Room Database)
                                    Button(
                                        onClick = {
                                            if (editableLessonText.isBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    if (isEnglish) "Please enter or extract text first" else "يرجى كتابة أو استخراج نص الملخص أولاً",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }
                                            val finalTitle = if (lessonTitle.isNotBlank()) {
                                                lessonTitle
                                            } else {
                                                if (isEnglish) "Lesson Summary - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}"
                                                else "ملخص درس - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}"
                                            }
                                            viewModel.addNote(
                                                title = finalTitle,
                                                content = editableLessonText,
                                                subjectId = selectedSubjectId
                                            )
                                            showSaveSuccessDialog = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("save_lesson_note_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isEnglish) "Save as Lesson Note in App 💾" else "حفظ كملخص درس في مذكراتي 💾",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    // 2. Secondary Row: Add as Study Task + Send to WhatsApp
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (editableLessonText.isBlank()) return@OutlinedButton
                                                val finalTitle = if (lessonTitle.isNotBlank()) "مذاكرة: $lessonTitle" else "مذاكرة ملخص الدرس"
                                                viewModel.addTask(
                                                    title = finalTitle,
                                                    description = editableLessonText.take(250) + if (editableLessonText.length > 250) "..." else "",
                                                    subjectId = selectedSubjectId,
                                                    priority = "MEDIUM",
                                                    dueDateMillis = System.currentTimeMillis() + 86400000L,
                                                    tag = "ملخص دراسي"
                                                )
                                                Toast.makeText(
                                                    context,
                                                    if (isEnglish) "Added to study tasks! 📅" else "تمت إضافة مهمة مذاكرة الملخص بنجاح! 📅",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("add_as_task_button"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isEnglish) "Add Task" else "جدولة مهمة 📅", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { shareViaWhatsApp() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("whatsapp_share_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFF1B8A44)
                                            )
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isEnglish) "WhatsApp" else "واتساب 📲", fontSize = 12.sp)
                                        }
                                    }

                                    // 3. AI Summarize Button: Generates Gemini summary for this edited text
                                    Button(
                                        onClick = {
                                            if (editableLessonText.isNotBlank()) {
                                                viewModel.generateGeminiSummaryForExtractedText(editableLessonText, selectedStyle)
                                                selectedTab = 1
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_summarize_from_edit_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isEnglish) "Generate AI Summary & Q&A from this text ✨" else "تلخيص النص وتوليد أسئلة بالذكاء الاصطناعي ✨",
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: AI SUMMARY & STUDY BREAKDOWN (Gemini 3.5 Flash)
                if (selectedTab == 1) {
                    // Style selection chips
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isEnglish) "Summary Style (Gemini AI):" else "أسلوب التلخيص المطلوب (Gemini AI):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val styles = listOf(
                                    Triple("COMPREHENSIVE", if (isEnglish) "Comprehensive Summary" else "شامل ومفصل 📖", Icons.Default.MenuBook),
                                    Triple("BULLETS", if (isEnglish) "Key Bullet Points" else "نقاط سريعة ومركزة 📌", Icons.Default.FormatListBulleted),
                                    Triple("EXAM_REVISION", if (isEnglish) "Exam Revision & Q&A" else "مراجعة ليلة الامتحان 🎯", Icons.Default.Quiz)
                                )

                                styles.forEach { (styleKey, title, icon) ->
                                    FilterChip(
                                        selected = selectedStyle == styleKey,
                                        onClick = {
                                            viewModel.setSummaryStyle(styleKey)
                                            val textToSummarize = editableLessonText.ifBlank { extractedText }
                                            if (textToSummarize.isNotBlank()) {
                                                viewModel.generateGeminiSummaryForExtractedText(textToSummarize, styleKey)
                                            }
                                        },
                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(title, fontSize = 12.sp, fontWeight = if (selectedStyle == styleKey) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (isEnglish) "AI Educational Summary" else "ملخص المعلم الذكي",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = if (isEnglish) "Powered by Gemini 3.5 Flash" else "تمت الصياغة بواسطة Gemini 3.5 Flash",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Action icons (TTS & Copy)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                val textToRead = geminiSummary ?: editableLessonText
                                                if (isSpeaking) {
                                                    AnkiTTS.stop()
                                                    isSpeaking = false
                                                } else {
                                                    AnkiTTS.speak(textToRead)
                                                    isSpeaking = true
                                                }
                                            },
                                            modifier = Modifier.testTag("tts_summary_button")
                                        ) {
                                            Icon(
                                                if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = "Text to Speech",
                                                tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                geminiSummary?.let {
                                                    clipboardManager.setText(AnnotatedString(it))
                                                    Toast.makeText(context, if (isEnglish) "Summary copied" else "تم نسخ الملخص بنجاح", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.testTag("copy_summary_button")
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy Summary",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))

                                // The Summary Content
                                SelectionContainer {
                                    Text(
                                        text = geminiSummary ?: if (isEnglish) "Generating summary..." else "جاري صياغة التلخيص...",
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Bottom Action Buttons: Save Note, Create Flashcards
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            geminiSummary?.let { sum ->
                                                viewModel.saveSummaryToDocuments(
                                                    title = if (lessonTitle.isNotBlank()) lessonTitle else "ملخص ذكي - ${System.currentTimeMillis() % 10000}",
                                                    summary = sum,
                                                    originalText = editableLessonText.ifBlank { extractedText }
                                                )
                                                Toast.makeText(context, if (isEnglish) "Saved to Documents ✅" else "تم الحفظ في المستندات بنجاح ✅", Toast.LENGTH_SHORT).show()
                                                onNavigateToDocuments()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_summary_doc_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isEnglish) "Save Doc" else "حفظ كمستند", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val textForCards = editableLessonText.ifBlank { extractedText }
                                            if (textForCards.isNotBlank()) {
                                                viewModel.generateFlashcardsFromOcrText(
                                                    extractedText = textForCards,
                                                    topicHint = lessonTitle.ifBlank { "تلخيص صورة ومستند" },
                                                    targetDeckId = null,
                                                    deckTitle = if (lessonTitle.isNotBlank()) "بطاقات $lessonTitle" else "بطاقات من ملخص الدرس"
                                                ) { count, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    onNavigateToFlashcards()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("create_flashcards_from_summary_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isEnglish) "Make Anki Cards" else "توليد فلاش كاردز", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: ORIGINAL IMAGE PREVIEW
                if (selectedTab == 2 && selectedBitmap != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (isEnglish) "Original Document / Lesson Image" else "صورة ملخص الدرس الأصلية",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 250.dp, max = 500.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = selectedBitmap!!.asImageBitmap(),
                                        contentDescription = "Original lesson photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Initial Helpful Guidance / Feature Showcase
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (isEnglish) "Capture Lesson Notes & Convert to Editable Text" else "التقط صور ملخصات الدروس وحوّلها لنصوص قابلة للتعديل",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isEnglish) {
                                    "1. 📸 Capture your textbook, notebook, or blackboard using the camera.\n2. ⚡ Google ML Kit recognizes Arabic and English text instantly on your device.\n3. ✏️ Edit words, reformat paragraphs, and fix notes inside the app.\n4. 💾 Save to your Notes, schedule study tasks, or share via WhatsApp!"
                                } else {
                                    "1. 📸 التقط صورة صفحة الكشكول، الملزمة، أو السبورة بالكاميرا.\n2. ⚡ يتعرف محرك Google ML Kit الذكي على النصوص فورياً وبشكل محلي على جهازك.\n3. ✏️ عدّل الكلمات، نظّم الفقرات، وأضف ملاحظاتك من داخل محرر التطبيق.\n4. 💾 احفظ الملخص في مذكراتك، جدول مهمة لمذاكرته، أو شاركه عبر واتساب بنقرة واحدة!"
                                },
                                fontSize = 13.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { triggerCameraCapture() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEnglish) "Open Camera" else "فتح الكاميرا 📷")
                                }

                                OutlinedButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEnglish) "Pick Photo" else "اختيار صورة 🖼️")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Success Dialog on Saving Lesson Note
    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSuccessDialog = false },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isEnglish) "Lesson Note Saved! 📝" else "تم حفظ ملخص الدرس بنجاح! 📝",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (isEnglish) "Your edited lesson summary has been saved to your Notes. You can review or edit it anytime."
                    else "تم حفظ نص الملخص الذي قمت بتعديله في قسم الملاحظات. يمكنك مراجعته أو الإضافة عليه في أي وقت.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveSuccessDialog = false
                        onNavigateToNotes()
                    }
                ) {
                    Text(if (isEnglish) "Go to Notes" else "الانتقال إلى الملاحظات")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveSuccessDialog = false }
                ) {
                    Text(if (isEnglish) "Stay Here" else "البقاء هنا")
                }
            }
        )
    }
}
