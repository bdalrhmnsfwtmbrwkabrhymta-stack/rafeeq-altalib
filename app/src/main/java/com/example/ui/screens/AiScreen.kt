package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.AiChatStudyHelper
import com.example.ui.MainViewModel
import com.example.ui.components.AiFormattedContent
import com.example.ui.screens.ai.StudyPlanBuilderDialog
import com.example.ui.screens.ai.ComplexTopicClarifierDialog
import com.example.ui.screens.ai.AiTaskExtractionDialog
import com.example.ui.screens.ai.AiWelcomeHeroCard
import com.example.ui.screens.ai.AiScheduleSuggestionDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Brush
import java.io.ByteArrayOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    val messages by viewModel.aiChatMessages.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()
    val errorMessage by viewModel.aiErrorMessage.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Mode Tabs: 0 -> Study Plan & Academic Chat (Firebase AI SDK), 1 -> Advanced Gemini Tools
    var selectedTab by remember { mutableStateOf(0) }

    // Gemini Feature Modes
    var selectedModel by remember { mutableStateOf("gemini-3.5-flash") }
    var enableThinking by remember { mutableStateOf(false) }
    var enableSearch by remember { mutableStateOf(false) }
    var enableMaps by remember { mutableStateOf(false) }
    var attachStudyPlanContext by remember { mutableStateOf(true) }

    // TTS Voice Assistant & Speaking State
    val ttsManager = remember { AiChatStudyHelper.ArabicTtsManager(context) }
    var speakingMessageIndex by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Network Disconnect Alert Dialog
    var showOfflineAlert by remember { mutableStateOf(false) }

    // Export PDF Status State
    var pdfExportStatus by remember { mutableStateOf<String?>(null) }

    // Media Attachments
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var selectedAudioBase64 by remember { mutableStateOf<String?>(null) }
    var selectedAudioName by remember { mutableStateOf<String?>(null) }

    // Quick Concept Explainer Dialog State
    var showConceptDialog by remember { mutableStateOf(false) }
    var conceptQuery by remember { mutableStateOf("") }
    var selectedSubjectCategory by remember { mutableStateOf("عام") }

    // New AI Feature Dialogs
    var showMusicGeneratorDialog by remember { mutableStateOf(false) }
    var showAudioTranscribeDialog by remember { mutableStateOf(false) }
    var showModelSwitcherDialog by remember { mutableStateOf(false) }
    var showFirebaseSyncDialog by remember { mutableStateOf(false) }

    // Dedicated Study Plan & Topic Clarifier Dialogs
    var showStudyPlanDialog by remember { mutableStateOf(false) }
    var showClarifyTopicDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToAddTitle by remember { mutableStateOf("") }
    var taskToAddSubject by remember { mutableStateOf("") }

    // Schedule Dialog
    var showAiScheduleDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Camera Capture Launcher
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val outputStream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val bytes = outputStream.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                selectedImageBase64 = base64
                selectedImageName = "صورة ملتقطة بالكاميرا 📷 (${bytes.size / 1024} KB)"
                selectedModel = "gemini-3.1-pro-preview"
                Toast.makeText(context, if (isEnglish) "Camera photo captured successfully" else "تم التقاط الصورة بالكاميرا بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraCaptureLauncher.launch(null)
        } else {
            Toast.makeText(context, if (isEnglish) "Camera permission is required to snap questions" else "مطلوب إذن الكاميرا لتصوير الأسئلة والصفحات", Toast.LENGTH_SHORT).show()
        }
    }

    // Speech-to-Text Voice Dictation Launcher
    var isListeningVoice by remember { mutableStateOf(false) }
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = spokenMatches?.firstOrNull() ?: ""
            if (recognizedText.isNotBlank()) {
                inputPrompt = if (inputPrompt.isBlank()) recognizedText else "$inputPrompt $recognizedText"
                Toast.makeText(context, if (isEnglish) "Voice input captured" else "تم التعرف على الصوت بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Audio / Microphone Permission Launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isEnglish) "en-US" else "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isEnglish) "Speak your question or study topic..." else "تحدث بسؤالك أو استفسارك الدراسي...")
            }
            try {
                isListeningVoice = true
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                isListeningVoice = false
                Toast.makeText(context, if (isEnglish) "Speech recognizer not supported on this device" else "التعرف الصوتي غير متاح حالياً على هذا الجهاز", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, if (isEnglish) "Microphone permission is required for voice input" else "مطلوب إذن الميكروفون للإملاء والتسجيل الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bytes: ByteArray? = inputStream?.use { stream -> stream.readBytes() }
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    selectedImageBase64 = base64
                    selectedImageName = "صورة مستند تعليمي (${bytes.size / 1024} KB)"
                    selectedModel = "gemini-3.1-pro-preview"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Audio Picker Launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bytes: ByteArray? = inputStream?.use { stream -> stream.readBytes() }
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    selectedAudioBase64 = base64
                    selectedAudioName = "تسجيل صوتي (${bytes.size / 1024} KB)"
                    selectedModel = "gemini-3.5-flash"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val currentApiKey = remember(userSettings.geminiApiKey) {
        userSettings.geminiApiKey.ifBlank { com.example.BuildConfig.GEMINI_API_KEY }
    }
    val isApiKeyConfigured = currentApiKey.isNotBlank() && currentApiKey != "MY_GEMINI_API_KEY"

    LaunchedEffect(Unit) {
        if (!isApiKeyConfigured) {
            showApiKeyDialog = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(userSettings.geminiApiKey) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            icon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text(if (isEnglish) "Gemini API Key Required 🔑" else "مطلوب مفتاح Gemini API 🔑", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isEnglish) "Please enter your personal Gemini API key to activate the live AI smart tutor and avoid connection errors."
                            else "تنبيه: لتفعيل المساعد الذكي وإجابات الشات المباشرة وتجنب أي أخطاء في الاتصال، يرجى إدخال مفتاح API الخاص بك بـ Gemini:",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(if (isEnglish) "API Key" else "مفتاح API الخاص بك") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGeminiApiKey(keyInput.trim())
                        showApiKeyDialog = false
                        Toast.makeText(context, if (isEnglish) "API Key saved successfully!" else "تم حفظ وتفعيل مفتاح Gemini API بنجاح 💾", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (isEnglish) "Save & Activate 💾" else "حفظ وتفعيل المفتاح 💾")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(if (isEnglish) "Later" else "إلغاء / لاحقاً")
                }
            }
        )
    }

    if (showAiScheduleDialog) {
        AiScheduleSuggestionDialog(
            viewModel = viewModel,
            onDismiss = { showAiScheduleDialog = false }
        )
    }

    if (showStudyPlanDialog) {
        StudyPlanBuilderDialog(
            viewModel = viewModel,
            onDismiss = { showStudyPlanDialog = false },
            onRequireNetwork = { showOfflineAlert = true }
        )
    }

    if (showClarifyTopicDialog) {
        ComplexTopicClarifierDialog(
            viewModel = viewModel,
            onDismiss = { showClarifyTopicDialog = false },
            onRequireNetwork = { showOfflineAlert = true }
        )
    }

    if (showAddTaskDialog) {
        AiTaskExtractionDialog(
            initialTaskTitle = taskToAddTitle,
            initialSubject = taskToAddSubject,
            viewModel = viewModel,
            onDismiss = { showAddTaskDialog = false }
        )
    }

    // Quick Academic Concept Explainer Dialog
    if (showConceptDialog) {
        val academicSubjects = listOf(
            "كيمياء", "فيزياء", "رياضيات", "أحياء", "لغة عربية", "English", "تاريخ", "جغرافيا"
        )
        AlertDialog(
            onDismissRequest = { showConceptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEnglish) "Quick Academic Explanation" else "شرح مفهوم أكاديمي سريع",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (isEnglish) "Choose subject or enter concept:" else "اختر المادة أو اكتب المفهوم المراد توضيحه:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(academicSubjects) { sub ->
                            FilterChip(
                                selected = selectedSubjectCategory == sub,
                                onClick = {
                                    selectedSubjectCategory = sub
                                    if (conceptQuery.isBlank()) {
                                        conceptQuery = "مفهوم أساسي في مادة $sub"
                                    }
                                },
                                label = { Text(sub, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = conceptQuery,
                        onValueChange = { conceptQuery = it },
                        label = { Text(if (isEnglish) "Topic / Concept (e.g. Ohm's Law, Mitosis)" else "المفهوم (مثال: قانون أوم، الانقسام الميتوزي)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("concept_query_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!AiChatStudyHelper.isNetworkAvailable(context)) {
                            showConceptDialog = false
                            showOfflineAlert = true
                            return@Button
                        }

                        if (conceptQuery.isNotBlank()) {
                            viewModel.requestQuickAcademicExplanation(
                                conceptOrTopic = "[$selectedSubjectCategory]: $conceptQuery"
                            )
                            showConceptDialog = false
                            conceptQuery = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_explain_concept_button")
                ) {
                    Text(if (isEnglish) "Explain Now" else "اشرح الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConceptDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }

    // 1. Music Generator Dialog (Lyria AI)
    if (showMusicGeneratorDialog) {
        var musicPrompt by remember { mutableStateOf("Binaural alpha waves with soothing rain sounds for deep focus and studying") }
        var isShortClip by remember { mutableStateOf(true) }
        var isGeneratingMusic by remember { mutableStateOf(false) }
        var generatedMusicStatus by remember { mutableStateOf<String?>(null) }

        val musicPresets = listOf(
            "موسيقى ألفا للتركيز 🧠" to "Binaural alpha beats 40Hz with soft background ambience for memory retention",
            "بيانو هادئ مع المطر 🌧️" to "Peaceful neoclassical piano melody with gentle rain drops",
            "لو-فاي هادئ للمذاكرة 🎧" to "Chill relaxed lofi study hip-hop beats with warm vintage synth",
            "أجواء الطبيعة والسكينة 🌲" to "Calming forest ambient soundscape with peaceful stream flow"
        )

        AlertDialog(
            onDismissRequest = { if (!isGeneratingMusic) showMusicGeneratorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(if (isEnglish) "Generate Focus Music (Lyria)" else "توليد موسيقى التركيز (Lyria AI) 🎵", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("lyria-3-clip-preview / lyria-3-pro-preview", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isEnglish) "Choose a mood or describe your ideal study track:" else "اختر نمطاً أو صف أجواء المذاكرة المطلوبة:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(musicPresets) { (label, presetPrompt) ->
                            SuggestionChip(
                                onClick = { musicPrompt = presetPrompt },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = musicPrompt,
                        onValueChange = { musicPrompt = it },
                        label = { Text("وصف المقطع الموسيقي (Prompt)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المدة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = isShortClip,
                                onClick = { isShortClip = true },
                                label = { Text("30 ثانية (Clip)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = !isShortClip,
                                onClick = { isShortClip = false },
                                label = { Text("كامل (Pro Track)", fontSize = 11.sp) }
                            )
                        }
                    }

                    if (isGeneratingMusic) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                        Text("جاري تأليف المقطع وتوليد الموجات الصوتية بواسطة Lyria...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    generatedMusicStatus?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(status, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGeneratingMusic = true
                        viewModel.generateStudyMusic(musicPrompt, isShortClip) { success, msg ->
                            isGeneratingMusic = false
                            generatedMusicStatus = msg
                            if (success) {
                                Toast.makeText(context, "تم توليد مقطع المذاكرة بنجاح 🎶", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isGeneratingMusic && musicPrompt.isNotBlank()
                ) {
                    Text(if (isEnglish) "Generate Music" else "توليد الموسيقى 🎶")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMusicGeneratorDialog = false }, enabled = !isGeneratingMusic) {
                    Text(if (isEnglish) "Close" else "إغلاق")
                }
            }
        )
    }

    // 2. Audio Transcribe Dialog
    if (showAudioTranscribeDialog) {
        var transcribedText by remember { mutableStateOf("") }
        var isTranscribing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isTranscribing) showAudioTranscribeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE91E63), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(if (isEnglish) "Audio Transcription" else "تفريغ الصوت الذكي 🎙️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("gemini-3.5-transcribe", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isEnglish) "Speak using microphone or select an audio lecture file:" else "تحدث بصوتك مباشرة أو اختر ملف تسجيل صوتي لتفريغه بدقة:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasAudioPermission) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isEnglish) "en-US" else "ar-SA")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث لتفريغ الصوت...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                } else {
                                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسجيل بالمايك")
                        }

                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رفع ملف صوتي")
                        }
                    }

                    if (selectedAudioName != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE91E63).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الملف: $selectedAudioName", fontSize = 11.sp, color = Color(0xFFC2185B))
                                IconButton(onClick = { selectedAudioBase64 = null; selectedAudioName = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (inputPrompt.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("النص المفرغ:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(inputPrompt, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAudioTranscribeDialog = false
                    }
                ) {
                    Text(if (isEnglish) "Done" else "تم واستخدام النص")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAudioTranscribeDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }

    // 4. Model Switcher Dialog
    if (showModelSwitcherDialog) {
        AlertDialog(
            onDismissRequest = { showModelSwitcherDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "Select Gemini Model" else "اختيار نموذج Gemini الذكي", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Gemini 3.5 Flash
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedModel == "gemini-3.5-flash") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (selectedModel == "gemini-3.5-flash") MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedModel = "gemini-3.5-flash"
                            showModelSwitcherDialog = false
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✨ Gemini 3.5 Flash (النموذج القياسي)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("متوازن، سريع، ويدعم بحث جوجل والوسائط المتعددة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 2: Gemini 3.1 Pro Preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedModel == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (selectedModel == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedModel = "gemini-3.1-pro-preview"
                            enableThinking = true
                            showModelSwitcherDialog = false
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🧠 Gemini 3.1 Pro (تفكير عميق وSTEM)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("أعلى دقة استدلال رياضي وحل المسائل العلمية المعقدة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 3: Gemini 3.1 Flash Lite
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedModel == "gemini-3.1-flash-lite") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (selectedModel == "gemini-3.1-flash-lite") MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedModel = "gemini-3.1-flash-lite"
                            showModelSwitcherDialog = false
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚡ Gemini 3.1 Flash Lite (فائق السرعة)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("استجابة لحظية خفيفة للمفاهيم السريعة والترجمة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelSwitcherDialog = false }) {
                    Text(if (isEnglish) "Close" else "إغلاق")
                }
            }
        )
    }

    // Network Disconnect Alert Dialog
    if (showOfflineAlert) {
        AlertDialog(
            onDismissRequest = { showOfflineAlert = false },
            icon = {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isEnglish) "No Internet Connection" else "لا يوجد اتصال بالإنترنت",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "Gemini AI requires an active internet connection to solve questions and analyze images. Please verify your Wi-Fi or mobile data connection and try again."
                    else
                        "مساعد الذكاء الاصطناعي (Gemini API) يتطلب اتصالاً حقيقياً ونشطاً بشبكة الإنترنت لتحليل الصور وحل المسائل العلمية. يرجى التأكد من تشغيل الواي فاي أو بيانات الهاتف والمحاولة مرة أخرى."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showOfflineAlert = false }
                ) {
                    Text(if (isEnglish) "OK" else "حسناً")
                }
            }
        )
    }

    // PDF Export Status Alert
    pdfExportStatus?.let { statusMsg ->
        AlertDialog(
            onDismissRequest = { pdfExportStatus = null },
            icon = {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isEnglish) "PDF Export" else "تصدير ملف PDF",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = statusMsg)
            },
            confirmButton = {
                Button(onClick = { pdfExportStatus = null }) {
                    Text(if (isEnglish) "OK" else "حسناً")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Sleek, Ultra-Compact Top Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    ) {
                        Icon(
                            Icons.Default.TipsAndUpdates,
                            contentDescription = "فهيم",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isEnglish) "Faheem • Study Mentor" else "فهيم • اسأل واشرح",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "TUTOR" else "معك دائماً",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isEnglish) "Ready to explain lessons step by step" else "شرح مبسط للمناهج والمسائل خطوة بخطوة",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // API Key Settings Button
                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("api_key_setting_button")
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = if (isEnglish) "API Key" else "مفتاح API",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Model Switcher Button
                    IconButton(
                        onClick = { showModelSwitcherDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Model Switcher",
                            tint = Color(0xFF9B51E0),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (messages.any { !it.first }) {
                        IconButton(
                            onClick = {
                                val lastAiAnswer = messages.filter { !it.first }.lastOrNull()?.second ?: ""
                                AiChatStudyHelper.exportToPdf(
                                    context = context,
                                    title = "شرح وحل المسألة التعليمية",
                                    content = lastAiAnswer
                                ) { success, msg, uri ->
                                    pdfExportStatus = msg
                                    if (success && uri != null) {
                                        AiChatStudyHelper.sharePdf(context, uri)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("export_pdf_button")
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = if (isEnglish) "Export PDF" else "تصدير PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.clearAiChat() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = if (isEnglish) "Clear Chat" else "مسح المحادثة",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Warning banner if API key is missing or unconfigured
        if (!isApiKeyConfigured) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clickable { showApiKeyDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isEnglish) "Gemini API key missing. Tap to enter key." else "مفتاح API غير متوفر أو يحتاج لتحديث. اضغط لإدخاله الآن 🔑",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        if (isEnglish) "ENTER 🔑" else "إدخال 🔑",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Single Streamlined Shortcut Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { showStudyPlanDialog = true },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) },
                label = { Text(if (isEnglish) "Study Plan 🗓️" else "خطة دراسية 🗓️", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("build_study_plan_chip")
            )
            AssistChip(
                onClick = { showClarifyTopicDialog = true },
                leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary) },
                label = { Text(if (isEnglish) "Clarify Concept 💡" else "شرح مفهوم 💡", fontSize = 11.sp) },
                modifier = Modifier.testTag("clarify_topic_quick_chip")
            )
            AssistChip(
                onClick = { onNavigate("image_ocr_summary") },
                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) },
                label = { Text(if (isEnglish) "OCR & Summary 📸" else "مسح صورة 📸", fontSize = 11.sp) }
            )
            FilterChip(
                selected = enableThinking,
                onClick = {
                    enableThinking = !enableThinking
                    if (enableThinking) selectedModel = "gemini-3.1-pro-preview"
                },
                leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF673AB7)) },
                label = { Text(if (isEnglish) "Deep Thinking 🧠" else "تفكير عميق 🧠", fontSize = 11.sp) }
            )
            AssistChip(
                onClick = { showMusicGeneratorDialog = true },
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFEC4899)) },
                label = { Text(if (isEnglish) "Focus Music 🎵" else "موسيقى التركيز 🎵", fontSize = 11.sp) }
            )
            AssistChip(
                onClick = { showAudioTranscribeDialog = true },
                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFE91E63)) },
                label = { Text(if (isEnglish) "Transcribe 🎙️" else "تفريغ صوت 🎙️", fontSize = 11.sp) }
            )
        }

        // Full-Screen Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    AiWelcomeHeroCard(
                        selectedTab = selectedTab,
                        isEnglish = isEnglish,
                        onOpenStudyPlanner = { showStudyPlanDialog = true },
                        onAnalyzeStudyPlan = { viewModel.analyzeStudyPlanWithAi() },
                        onOpenTopicClarifier = { showClarifyTopicDialog = true },
                        onQuickPrompt = { prompt -> inputPrompt = prompt }
                    )
                }
            }

            items(messages.size) { index ->
                val (isUser, text) = messages[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!isUser) {
                        // AI Radiant Gemini Avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4285F4),
                                            Color(0xFF9B51E0),
                                            Color(0xFFEA4335)
                                        )
                                    )
                                )
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Gemini",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Card(
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.dp else 2.dp),
                        modifier = Modifier.widthIn(max = if (isUser) 310.dp else 340.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Speaker label for clarity and color
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = if (isUser) (if (isEnglish) "You" else "أنت") else (if (isEnglish) "Gemini AI" else "الذكاء الاصطناعي (Gemini)"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF673AB7)
                                )
                            }

                            AiFormattedContent(
                                text = text,
                                isUser = isUser,
                                onCopyMathBlock = { formula ->
                                    viewModel.copyTextToClipboard("معادلة رياضية", formula)
                                }
                            )

                            if (!isUser) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // TTS Listen Button
                                    val isCurrentSpeaking = speakingMessageIndex == index
                                    IconButton(
                                        onClick = {
                                            if (isCurrentSpeaking) {
                                                ttsManager.stop()
                                                speakingMessageIndex = null
                                            } else {
                                                speakingMessageIndex = index
                                                ttsManager.speak(text) { isSpeaking ->
                                                    if (!isSpeaking && speakingMessageIndex == index) {
                                                        speakingMessageIndex = null
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isCurrentSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                            contentDescription = if (isCurrentSpeaking) "إيقاف القراءة" else "قراءة الإجابة صوتياً",
                                            tint = if (isCurrentSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Single Message PDF Export Button
                                    IconButton(
                                        onClick = {
                                            AiChatStudyHelper.exportToPdf(
                                                context = context,
                                                title = "حل المسألة والشرح الأكاديمي",
                                                content = text
                                            ) { success, msg, uri ->
                                                pdfExportStatus = msg
                                                if (success && uri != null) {
                                                    AiChatStudyHelper.sharePdf(context, uri)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = "تصدير هذه الإجابة إلى PDF",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Copy Answer Button
                                    IconButton(
                                        onClick = { viewModel.copyTextToClipboard("إجابة الذكاء الاصطناعي", text) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "نسخ الإجابة",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Add to Study Tasks Button
                                    IconButton(
                                        onClick = {
                                            taskToAddTitle = text.lines()
                                                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                                                ?.replace("*", "")
                                                ?.take(60)
                                                ?: if (isEnglish) "AI Study Task" else "مهمة مذاكرة موصى بها من الذكاء الاصطناعي"
                                            taskToAddSubject = if (isEnglish) "General" else "مذاكرة"
                                            showAddTaskDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AddTask,
                                            contentDescription = if (isEnglish) "Add to Study Tasks" else "إضافة إلى مهام المذاكرة",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // User Avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "User",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                enableThinking -> "جاري التفكير والتسلسل العميق في الحل (High Thinking Mode)..."
                                enableSearch -> "جاري البحث في نتائج جوجل الحية وتجميع الشرح..."
                                selectedImageBase64 != null -> "جاري تحليل الصورة وقراءة المحتوى بـ Gemini Pro..."
                                selectedAudioBase64 != null -> "جاري تفريغ التسجيل الصوتي وتلخيصه..."
                                attachStudyPlanContext -> "جاري مراجعة خطتك الدراسية وصياغة أفضل إرشاد بالذكاء الاصطناعي..."
                                else -> "جاري تحضير الشرح والإجابة..."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Attached Media Previews
        AnimatedVisibility(visible = selectedImageBase64 != null || selectedAudioBase64 != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                selectedImageName?.let { imgName ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(imgName, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    selectedImageBase64 = null
                                    selectedImageName = null
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    )
                }

                selectedAudioName?.let { audName ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(audName, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    selectedAudioBase64 = null
                                    selectedAudioName = null
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    )
                }
            }
        }

        // Quick Suggestion Chips for Students
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    SuggestionChip(
                        onClick = { showStudyPlanDialog = true },
                        icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) },
                        label = { Text(if (isEnglish) "Build Plan 🗓️✨" else "صمّم خطة دراسية 🗓️✨", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "How should I structure my Pomodoro sessions for today's tasks?" else "كيف أنظم جلسات بومودورو وأوقات الراحة لإنجاز مهام اليوم بأعلى تركيز؟"
                        },
                        label = { Text(if (isEnglish) "⏱️ Pomodoro Breakdown" else "⏱️ تقسيم بومودورو لليوم", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { viewModel.analyzeStudyPlanWithAi() },
                        icon = { Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(if (isEnglish) "Analyze Plan 📊" else "تحليل خطتي والمهام 📊", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Create a comprehensive 7-day revision schedule before my upcoming exams:" else "صمم لي جدول مراجعة مكثف لمدة 7 أيام قبل موعد امتحاناتي:"
                        },
                        label = { Text(if (isEnglish) "7-Day Exam Plan ⏳" else "خطة 7 أيام للاختبار ⏳", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "How do I balance daily homework assignments and deep revision without burnout?" else "كيف أوازن بين الواجبات المدرسية والمذاكرة اليومية بدون إرهاق؟"
                        },
                        label = { Text(if (isEnglish) "Study Balance ⚖️" else "الموازنة بين المواد ⚖️", fontSize = 11.sp) }
                    )
                }
                1 -> {
                    SuggestionChip(
                        onClick = { showClarifyTopicDialog = true },
                        icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary) },
                        label = { Text(if (isEnglish) "Clarify Topic 💡✨" else "اشرح وبسّط مفهوماً 💡✨", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Explain step-by-step with formulas and laws: " else "اشرح لي بالتفصيل والخطوات والمعادلات الرياضية أو الفيزيائية مسألة: "
                        },
                        label = { Text(if (isEnglish) "Step-by-Step 📐" else "📐 حل تدريجي بالمعادلات", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Explain this concept using the Feynman technique and an intuitive story: " else "بسّط لي بأسلوب فاينمان وبمثال واقعي سهل جداً مفهوم: "
                        },
                        label = { Text(if (isEnglish) "Feynman Story 🧠" else "🧠 تشبيه فاينمان البسيط", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "What are the top 3 exam traps and student mistakes regarding: " else "ما هي أشهر 3 أخطاء ومصائد يقع فيها الطلاب في الامتحانات بخصوص: "
                        },
                        label = { Text(if (isEnglish) "Exam Traps ⚠️" else "⚠️ مصائد الامتحانات", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Test my understanding with 3 high-yield questions with solutions about: " else "اطرح علي 3 أسئلة ذكية مع الحل النموذجي لاختبار فهمي لـ: "
                        },
                        label = { Text(if (isEnglish) "Quick Quiz 🎯" else "🎯 اختبار فهمي الفوري", fontSize = 11.sp) }
                    )
                }
                else -> {
                    SuggestionChip(
                        onClick = { showAiScheduleDialog = true },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(if (isEnglish) "AI Smart Schedule 🤖" else "جدول ذكي بـ Gemini 🤖", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Analyze and solve the question in this image step-by-step:" else "قم بتحليل وحل المسألة العلمية في هذه الصورة خطوة بخطوة:"
                        },
                        label = { Text(if (isEnglish) "📷 Solve from Photo" else "📷 حل من صورة", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputPrompt = if (isEnglish) "Summarize this lesson into 5 key points with examples:" else "قم بتلخيص هذا الدرس في 5 نقاط أساسية مع أمثلة عملية:"
                        },
                        label = { Text(if (isEnglish) "Summarize Lesson 📝" else "تلخيص درس 📝", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            enableThinking = true
                            selectedModel = "gemini-3.1-pro-preview"
                            inputPrompt = if (isEnglish) "Deep-reason and formulate a comprehensive breakdown for:" else "فكر بعمق وقدّم تحليلاً شاملاً وتفصيلياً لـ:"
                        },
                        label = { Text(if (isEnglish) "🧠 High Thinking Mode" else "🧠 وضع التفكير العميق", fontSize = 11.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Media & Tool Actions Bar above input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Direct Capture Pill
            FilterChip(
                selected = false,
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        cameraCaptureLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF1A73E8)
                    )
                },
                label = { Text(if (isEnglish) "Camera 📸" else "تصوير بالكاميرا 📸", fontSize = 11.sp) },
                modifier = Modifier.testTag("camera_capture_button")
            )

            // Pick Image Pill
            FilterChip(
                selected = selectedImageBase64 != null,
                onClick = { imagePickerLauncher.launch("image/*") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text(if (isEnglish) "Attach Image 🖼️" else "إرفاق صورة 🖼️", fontSize = 11.sp) },
                modifier = Modifier.testTag("pick_image_button")
            )

            // Voice Dictation Pill
            FilterChip(
                selected = isListeningVoice,
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isEnglish) "en-US" else "ar-SA")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isEnglish) "Speak your question or study topic..." else "تحدث بسؤالك أو استفسارك الدراسي...")
                        }
                        try {
                            isListeningVoice = true
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            isListeningVoice = false
                            Toast.makeText(context, if (isEnglish) "Speech recognizer not supported on this device" else "التعرف الصوتي غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                leadingIcon = {
                    Icon(
                        if (isListeningVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isListeningVoice) Color(0xFFD32F2F) else Color(0xFF7B1FA2)
                    )
                },
                label = { Text(if (isEnglish) "Voice Dictation 🎙️" else "إملاء صوتي 🎙️", fontSize = 11.sp) },
                modifier = Modifier.testTag("voice_dictation_button")
            )

            // Pick Audio File Pill
            FilterChip(
                selected = selectedAudioBase64 != null,
                onClick = { audioPickerLauncher.launch("audio/*") },
                leadingIcon = {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFE91E63)
                    )
                },
                label = { Text(if (isEnglish) "Audio File 🎵" else "تسجيل صوتي 🎵", fontSize = 11.sp) },
                modifier = Modifier.testTag("pick_audio_button")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Open, Spacious Input Field & Send Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            when (selectedTab) {
                                0 -> if (isEnglish) "Ask about study planning, Pomodoro, or timetable..."
                                else "اسأل عن تنظيم المذاكرة، الجداول، أو خطة المراجعة..."
                                1 -> if (isEnglish) "Ask to explain any formula, concept, or hard topic..."
                                else "اكتب المفهوم أو المسألة الصعبة لشرحها وتبسيطها..."
                                else -> if (isEnglish) "Ask Gemini anything, snap photo, or dictate..."
                                else "اسأل الذكاء الاصطناعي، اطلب حلاً، أو اكتب سؤالك..."
                            },
                            fontSize = 13.5.sp,
                            maxLines = 2
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input_field"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Radiant Send Button
                IconButton(
                    onClick = {
                        if (!AiChatStudyHelper.isNetworkAvailable(context)) {
                            showOfflineAlert = true
                            return@IconButton
                        }

                        if (!isApiKeyConfigured) {
                            showApiKeyDialog = true
                            Toast.makeText(context, if (isEnglish) "Please enter your Gemini API key first" else "يرجى إدخال مفتاح Gemini API أولاً لتشغيل الإجابات المباشرة 🔑", Toast.LENGTH_LONG).show()
                            return@IconButton
                        }

                        if (inputPrompt.isNotBlank() || selectedImageBase64 != null || selectedAudioBase64 != null) {
                            viewModel.sendAiUserMessage(
                                prompt = inputPrompt,
                                imageBytesBase64 = selectedImageBase64,
                                audioBytesBase64 = selectedAudioBase64,
                                enableSearch = enableSearch,
                                enableMaps = enableMaps,
                                enableThinking = enableThinking,
                                selectedModel = selectedModel,
                                includeStudyPlanContext = attachStudyPlanContext
                            )
                            inputPrompt = ""
                            selectedImageBase64 = null
                            selectedImageName = null
                            selectedAudioBase64 = null
                            selectedAudioName = null
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .testTag("send_ai_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isEnglish) "Send" else "إرسال",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

