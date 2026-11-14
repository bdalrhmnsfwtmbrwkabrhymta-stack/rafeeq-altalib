package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.grammar.ArabicGrammarEngine
import com.example.domain.grammar.GrammarQuizQuestion
import com.example.domain.grammar.IrabSentenceAnalysis
import com.example.domain.grammar.IrabWordResult
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.util.*

private val PurplePrimary = Color(0xFF635BFF)
private val PurpleDark = Color(0xFF4E45E4)
private val PurpleLight = Color(0xFFF1F0FF)
private val PurpleGradient = Brush.linearGradient(listOf(Color(0xFF7C69FF), Color(0xFF5345E7)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrabGrammarScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val userSettings by viewModel.userSettings.collectAsState()

    var activeTab by remember { mutableStateOf("IRAB") } // "IRAB" or "EXERCISE"
    var inputText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<IrabSentenceAnalysis?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(1) }
    var savedHistoryList by remember { mutableStateOf<List<IrabSentenceAnalysis>>(emptyList()) }

    // Text To Speech
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialize Arabic TTS if available
                ttsInstance?.language = Locale("ar")
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
            }
        }
    }

    fun startVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن بالجملة التي تريد إعرابها...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "التعرف الصوتي غير مدعوم على جهازك حالياً", Toast.LENGTH_SHORT).show()
        }
    }

    fun runIrabAnalysis(sentence: String) {
        val clean = sentence.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "يرجى كتابة أو نطق الجملة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        isAnalyzing = true
        coroutineScope.launch {
            val result = ArabicGrammarEngine.parseSentence(
                inputSentence = clean,
                aiRepository = viewModel.aiRepository,
                apiKey = userSettings.geminiApiKey
            )
            isAnalyzing = false
            result.fold(
                onSuccess = { analysis ->
                    analysisResult = analysis
                    savedHistoryList = listOf(analysis) + savedHistoryList.filter { it.originalSentence != analysis.originalSentence }.take(19)
                    viewModel.awardGamificationXp(15, "إعراب جملة: ${analysis.originalSentence.take(20)}")
                },
                onFailure = { err ->
                    val fallback = ArabicGrammarEngine.buildRuleBasedFallback(clean)
                    analysisResult = fallback
                    savedHistoryList = listOf(fallback) + savedHistoryList.filter { it.originalSentence != fallback.originalSentence }.take(19)
                }
            )
        }
    }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إعراب الجمل والفقرات",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                actions = {
                    // Tutorial Info Button
                    IconButton(onClick = {
                        tutorialStep = 1
                        showTutorialDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "شرح الميزة",
                            tint = PurplePrimary
                        )
                    }
                    // History Button
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "السجل",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF9FAFC)
                )
            )
        },
        containerColor = Color(0xFFF9FAFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDE9FE).copy(alpha = 0.35f),
                            Color(0xFFF9FAFC),
                            Color(0xFFFAF5FF)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Mode Selector Pill matching Screenshot 1 exactly
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Exercise Tab ("🏋️ تمرين")
                        Surface(
                            onClick = { activeTab = "EXERCISE" },
                            shape = RoundedCornerShape(24.dp),
                            color = if (activeTab == "EXERCISE") PurplePrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = if (activeTab == "EXERCISE") Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تمرين",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeTab == "EXERCISE") Color.White else Color(0xFF64748B)
                                )
                            }
                        }

                        // Irab Tab ("إعراب A✓")
                        Surface(
                            onClick = { activeTab = "IRAB" },
                            shape = RoundedCornerShape(24.dp),
                            color = if (activeTab == "IRAB") PurplePrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "إعراب",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeTab == "IRAB") Color.White else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Spellcheck,
                                    contentDescription = null,
                                    tint = if (activeTab == "IRAB") Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (activeTab == "IRAB") {
                    // 2. Center Hero Badge (A✓ with purple radial glow)
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                spotColor = PurplePrimary.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(PurpleGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Spellcheck,
                            contentDescription = "أيقونة الإعراب",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Title & Friendly Subheading
                    Text(
                        text = "أعرب أي جملة أو فقرة ✍️",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E1B4B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "اكتب أو انطق جملة أو فقرة كاملة، وهنعربهالك كلمة كلمة — مش بس الإعراب، لأ وكمان «ليه» اتعربت كده 😉",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 4. Large Text Input Container with Voice Mic & Word Counter
                    val wordCount = if (inputText.isBlank()) 0 else inputText.trim().split("\\s+".toRegex()).size

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, if (inputText.isNotBlank()) PurplePrimary.copy(alpha = 0.6f) else Color(0xFFE2E8F0)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = "اكتب أو انطق الجملة/الفقرة...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 16.sp
                                    )
                                },
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mic Voice Input Button & Clear Button
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = { startVoiceRecognition() },
                                        shape = CircleShape,
                                        color = PurpleLight,
                                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.3f)),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "إدخال صوتي",
                                                tint = PurplePrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    if (inputText.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { inputText = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح النص",
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                // Word Counter Badge: "0 كلمة"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PurpleLight
                                ) {
                                    Text(
                                        text = "$wordCount كلمة",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 5. Big Primary Action Button: "أعرب الجملة ✨"
                    Button(
                        onClick = { runIrabAnalysis(inputText) },
                        enabled = !isAnalyzing && inputText.isNotBlank(),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurplePrimary,
                            disabledContainerColor = PurplePrimary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = PurplePrimary.copy(alpha = 0.5f)
                            )
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "جاري الإعراب بالذكاء الاصطناعي...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "أعرب الجملة ✨",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 6. Quick Suggestion Example Chips matching Screenshot 1
                    Text(
                        text = "أمثلة شائعة وسريعة للإعراب:",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 6.dp)
                    )

                    val quickChips = listOf(
                        "العلم نور",
                        "يذاكر الطلاب بجد",
                        "إن المجتهدين فائزون",
                        "كان الجو معتدلاً",
                        "الصبر مفتاح الفرج"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickChips.take(2).forEach { chip ->
                            SuggestionPillChip(text = chip, modifier = Modifier.weight(1f)) {
                                inputText = chip
                                runIrabAnalysis(chip)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickChips.drop(2).forEach { chip ->
                            SuggestionPillChip(text = chip, modifier = Modifier.weight(1f)) {
                                inputText = chip
                                runIrabAnalysis(chip)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 7. Full Analysis Breakdown Section
                    analysisResult?.let { result ->
                        IrabResultDetailsCard(
                            result = result,
                            onPlayTts = { text ->
                                ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "irab_tts")
                            },
                            onCopyAll = {
                                val fullText = buildString {
                                    appendLine("📖 جملة: ${result.vocalizedSentence}")
                                    appendLine("📌 نوع الجملة: ${result.sentenceType}")
                                    appendLine("💡 المعنى: ${result.generalMeaning}")
                                    appendLine("\n--- الإعراب المفصل ---")
                                    result.words.forEach { w ->
                                        appendLine("• ${w.vocalizedWord} : ${w.syntacticRole} (${w.grammaticalCase}) - ${w.marker}")
                                        appendLine("  «ليه؟»: ${w.whyReason}")
                                        if (w.subNotes.isNotBlank()) appendLine("  ملاحظات: ${w.subNotes}")
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(fullText))
                                Toast.makeText(context, "تم نسخ الإعراب الكامل بنجاح 📋", Toast.LENGTH_SHORT).show()
                            },
                            onShareWhatsApp = {
                                val shareText = buildString {
                                    appendLine("✨ إعراب رفيق الطالب الذكي ✨")
                                    appendLine("الجملة: ${result.vocalizedSentence}")
                                    appendLine("النوع: ${result.sentenceType}")
                                    appendLine("\nالإعراب بالتفصيل:")
                                    result.words.forEach { w ->
                                        appendLine("🔍 ${w.vocalizedWord}: ${w.syntacticRole} ${w.grammaticalCase} (${w.marker})")
                                        appendLine("📌 السبب: ${w.whyReason}")
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "مشاركة الإعراب"))
                            }
                        )
                    }
                } else {
                    // EXERCISE & QUIZ TAB
                    GrammarQuizSection(
                        onRewardXp = { points ->
                            viewModel.awardGamificationXp(points, "تمارين النحو والإعراب")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // Tutorial Dialog (Screenshot 2)
    if (showTutorialDialog) {
        Dialog(onDismissRequest = { showTutorialDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$tutorialStep/2",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "إعراب الجمل",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "abc",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (tutorialStep == 1) "اكتب جملة أو فقرة وهيعربها كلمة كلمة."
                        else "ومش بس الإعراب! كمان بيشرحلك «ليه» اتعربت كده وتقدر تسمع نطقها وتختبر معلوماتك في التمارين التفاعلية.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Next Pill Button + Page dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Page Dots
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = if (tutorialStep == 1) 20.dp else 8.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(if (tutorialStep == 1) PurplePrimary else Color(0xFFCBD5E1))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = if (tutorialStep == 2) 20.dp else 8.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(if (tutorialStep == 2) PurplePrimary else Color(0xFFCBD5E1))
                            )
                        }

                        Button(
                            onClick = {
                                if (tutorialStep == 1) tutorialStep = 2
                                else showTutorialDialog = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (tutorialStep == 1) "التالي" else "فهمت يلا نبدأ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { showTutorialDialog = false }) {
                        Text(
                            text = "تخطي الشرح",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }

    // History Bottom Sheet Dialog
    if (showHistoryDialog) {
        ModalBottomSheet(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل الجمل المعربة 🕒",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )
                    if (savedHistoryList.isNotEmpty()) {
                        TextButton(onClick = { savedHistoryList = emptyList() }) {
                            Text("مسح السجل", color = Color(0xFFEF4444), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (savedHistoryList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لم تقم بإعراب أي جمل بعد. اكتب أي جملة لتضاف هنا تلقائياً!",
                            fontSize = 13.5.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(savedHistoryList) { hist ->
                            Surface(
                                onClick = {
                                    inputText = hist.originalSentence
                                    analysisResult = hist
                                    showHistoryDialog = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = PurpleLight.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hist.vocalizedSentence,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E1B4B)
                                        )
                                        Text(
                                            text = hist.sentenceType,
                                            fontSize = 12.sp,
                                            color = PurplePrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionPillChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IrabResultDetailsCard(
    result: IrabSentenceAnalysis,
    onPlayTts: (String) -> Unit,
    onCopyAll: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Summary Banner Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, PurplePrimary.copy(alpha = 0.35f)),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PurplePrimary
                    ) {
                        Text(
                            text = result.sentenceType,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Action Icons: Pronounce, Copy, Share
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { onPlayTts(result.vocalizedSentence) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "نطق الجملة",
                                tint = PurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCopyAll,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "نسخ الإعراب",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onShareWhatsApp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vocalized Sentence with Large Arabic Typography
                Text(
                    text = result.vocalizedSentence,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E1B4B),
                    lineHeight = 30.sp
                )

                if (result.generalMeaning.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 ${result.generalMeaning}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Section Title: Detailed Word by Word
        Text(
            text = "🔍 الإعراب المفصل كلمة كلمة:",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Word-by-Word Breakdown Cards
        result.words.forEachIndexed { index, word ->
            WordIrabBreakdownCard(index = index + 1, word = word, onPlayTts = onPlayTts)
        }
    }
}

@Composable
fun WordIrabBreakdownCard(
    index: Int,
    word: IrabWordResult,
    onPlayTts: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Word Header Row: Number + Vocalized Word + Role Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PurplePrimary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$index",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = word.vocalizedWord,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E1B4B)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PurpleLight,
                    border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = word.syntacticRole,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // State & Marker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الحالة والعلامة: ",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "${word.grammaticalCase} ${word.marker}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
            }

            // «ليه اتعربت كده؟» (Why Box)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF5F3FF),
                border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "«ليه اتعربت كده؟»",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D28D9)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = word.whyReason,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4C1D95),
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Sub Notes / Attached pronouns
            if (word.subNotes.isNotBlank()) {
                Text(
                    text = "📌 ملاحظات: ${word.subNotes}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun GrammarQuizSection(
    onRewardXp: (Int) -> Unit
) {
    val questions = remember { ArabicGrammarEngine.grammarQuizQuestions }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }

    val currentQ = questions[currentQuestionIndex]

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header info: Score & Question counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PurpleLight
            ) {
                Text(
                    text = "السؤال ${currentQuestionIndex + 1} من ${questions.size}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFECFDF5)
            ) {
                Text(
                    text = "النقاط: $score ⭐",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Question Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = currentQ.sentenceText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E1B4B),
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PurpleLight
                ) {
                    Text(
                        text = currentQ.questionPrompt,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Multiple Choice Options
        currentQ.options.forEachIndexed { optIdx, optionText ->
            val isSelected = selectedOptionIndex == optIdx
            val isCorrect = optIdx == currentQ.correctIndex

            val optionColor = when {
                !hasAnswered && isSelected -> PurplePrimary.copy(alpha = 0.1f)
                hasAnswered && isCorrect -> Color(0xFFDCFCE7)
                hasAnswered && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                else -> Color.White
            }

            val optionBorder = when {
                !hasAnswered && isSelected -> BorderStroke(1.5.dp, PurplePrimary)
                hasAnswered && isCorrect -> BorderStroke(2.dp, Color(0xFF10B981))
                hasAnswered && isSelected && !isCorrect -> BorderStroke(2.dp, Color(0xFFEF4444))
                else -> BorderStroke(1.dp, Color(0xFFE2E8F0))
            }

            Surface(
                onClick = {
                    if (!hasAnswered) {
                        selectedOptionIndex = optIdx
                        hasAnswered = true
                        if (isCorrect) {
                            score += 10
                            onRewardXp(10)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = optionColor,
                border = optionBorder,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${optIdx + 1}.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = optionText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Explanation & Next Button when answered
        if (hasAnswered) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selectedOptionIndex == currentQ.correctIndex) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                border = BorderStroke(1.dp, if (selectedOptionIndex == currentQ.correctIndex) Color(0xFFBBF7D0) else Color(0xFFFECDD3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (selectedOptionIndex == currentQ.correctIndex) "✓ إجابة صحيحة وممتازة! 🎉" else "❌ إجابة غير صحيحة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedOptionIndex == currentQ.correctIndex) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                    Text(
                        text = currentQ.explanation,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                }
            }

            Button(
                onClick = {
                    if (currentQuestionIndex < questions.size - 1) {
                        currentQuestionIndex++
                        selectedOptionIndex = null
                        hasAnswered = false
                    } else {
                        currentQuestionIndex = 0
                        selectedOptionIndex = null
                        hasAnswered = false
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (currentQuestionIndex < questions.size - 1) "السؤال التالي" else "إعادة الاختبار من البداية",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
