package com.example.studytools.irab

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import kotlinx.coroutines.launch

private val IrabNavyHeader = Color(0xFF141E30)
private val IrabGoldTitle = Color(0xFFD99B38)
private val IrabVerbColor = Color(0xFFD97706)
private val IrabNounColor = Color(0xFF2563EB)
private val IrabParticleColor = Color(0xFF059669)

private fun categoryAccentColor(cat: ArabicPosCategory): Color = when (cat) {
    ArabicPosCategory.VERB -> IrabVerbColor
    ArabicPosCategory.NOUN -> IrabNounColor
    ArabicPosCategory.PARTICLE -> IrabParticleColor
}

@Composable
fun SmartIrabScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = إعراب, 1 = تدريب
    var sampleIndex by remember { mutableIntStateOf(0) }
    var inputSentence by remember { mutableStateOf(SmartIrabEngine.sampleSentences.first()) }
    var analysisResult by remember {
        mutableStateOf(SmartIrabEngine.analyzeOffline(SmartIrabEngine.sampleSentences.first()))
    }
    var isAiLoading by remember { mutableStateOf(false) }
    var showEditField by remember { mutableStateOf(false) }

    val historyList = remember { mutableStateListOf<IrabAnalysisResult>() }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Quiz state for تدريب tab
    val questions = remember { SmartIrabEngine.trainingQuestions }
    var currentQuestionIdx by remember { mutableIntStateOf(0) }
    var selectedAnswerIdx by remember { mutableStateOf<Int?>(null) }
    var quizScore by remember { mutableIntStateOf(0) }
    var quizAnsweredCount by remember { mutableIntStateOf(0) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputSentence = spoken
                val offline = SmartIrabEngine.analyzeOffline(spoken)
                analysisResult = offline
                if (historyList.none { it.sentence == offline.sentence }) {
                    historyList.add(0, offline)
                }
            }
        }
    }

    fun runParsing(deepAi: Boolean) {
        val clean = inputSentence.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "اكتب جملة عربية أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val instant = SmartIrabEngine.analyzeOffline(clean)
        analysisResult = instant
        if (historyList.none { it.sentence == instant.sentence }) {
            historyList.add(0, instant)
        }
        showEditField = false
        if (deepAi) {
            coroutineScope.launch {
                isAiLoading = true
                val refined = SmartIrabEngine.analyzeWithCloudAiIfAvailable(context, clean)
                analysisResult = refined
                isAiLoading = false
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (selectedTab == 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val txt = formatIrabForShare(analysisResult)
                                    clipboardManager.setText(AnnotatedString(txt))
                                    Toast.makeText(context, "تم نسخ الإعراب", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("irab_copy_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    val txt = formatIrabForShare(analysisResult)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, txt)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة الإعراب"))
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("irab_share_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    sampleIndex = (sampleIndex + 1) % SmartIrabEngine.sampleSentences.size
                                    val nextSentence = SmartIrabEngine.sampleSentences[sampleIndex]
                                    inputSentence = nextSentence
                                    analysisResult = SmartIrabEngine.analyzeOffline(nextSentence)
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IrabNavyHeader,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("irab_new_sentence_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جملة جديدة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Signature Royal Blue + Obsidian Header matching Screenshot 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IrabNounColor)
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = IrabNavyHeader,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("irab_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color.White
                                    )
                                }

                                Text(
                                    text = "الإِعْرَابُ الذَّكِيُّ",
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IrabGoldTitle
                                )

                                IconButton(
                                    onClick = { showHistoryDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("irab_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "سجل الجمل",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Two Tabs: إعراب | تدريب
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("إعراب", "تدريب").forEachIndexed { idx, label ->
                                    val selected = selectedTab == idx
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .clickable { selectedTab = idx }
                                            .testTag("irab_tab_$idx"),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = DisplayArabicFontFamily,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .background(if (selected) IrabNounColor else Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Sentence Card with Color-Coded Word Indicators
                        item {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = { showEditField = !showEditField },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("irab_edit_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (showEditField) Icons.Default.Done else Icons.Default.Edit,
                                                contentDescription = "تعديل الجملة",
                                                tint = IrabNounColor
                                            )
                                        }

                                        Text(
                                            text = if (analysisResult.isAiPowered) "تحليل ذكي" else "تحليل فوري",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                        )
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق الجملة العربية للإعراب")
                                                    }
                                                    voiceLauncher.launch(intent)
                                                } catch (_: Throwable) {
                                                    Toast.makeText(
                                                        context,
                                                        "الإملاء الصوتي غير متوفر على هذا الجهاز، يمكنك كتابة الجملة مباشرة",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("irab_mic_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "إدخال صوتي",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Color-coded sentence preview (matching Screenshot 2)
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        analysisResult.tokens.forEach { token ->
                                            val accent = categoryAccentColor(token.category)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = token.word,
                                                    fontFamily = HeaderBrandAmiriFontFamily,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(42.dp)
                                                        .height(3.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(accent)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = token.category.labelAr,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accent
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = showEditField) {
                                        OutlinedTextField(
                                            value = inputSentence,
                                            onValueChange = { inputSentence = it },
                                            label = { Text("اكتب أي جملة عربية للإعراب") },
                                            maxLines = 3,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("irab_sentence_input")
                                        )
                                    }

                                    Button(
                                        onClick = { runParsing(deepAi = true) },
                                        enabled = !isAiLoading,
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = IrabNavyHeader,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("irab_analyze_button")
                                    ) {
                                        if (isAiLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = "أعرب الجملة",
                                            fontFamily = DisplayArabicFontFamily,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Word-by-word Colored I'rab Cards
                        items(analysisResult.tokens) { token ->
                            val accent = categoryAccentColor(token.category)
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.5.dp, accent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${token.word}: ${token.irabStatement}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "العلامة: ${token.sign}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                    Text(
                                        text = "السبب: ${token.reason}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Interactive تدريب Tab
                    val q = questions[currentQuestionIdx]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تحدي الإعراب (${currentQuestionIdx + 1} / ${questions.size})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = IrabParticleColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "النتيجة: $quizScore / $quizAnsweredCount",
                                    fontWeight = FontWeight.Bold,
                                    color = IrabParticleColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = q.sentence,
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = IrabNounColor
                                )
                                Text(
                                    text = q.questionText,
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        q.options.forEachIndexed { idx, opt ->
                            val isAnswered = selectedAnswerIdx != null
                            val isCorrect = idx == q.correctIndex
                            val isChosen = idx == selectedAnswerIdx
                            val cardColor = when {
                                !isAnswered -> MaterialTheme.colorScheme.surface
                                isCorrect -> IrabParticleColor.copy(alpha = 0.16f)
                                isChosen -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val borderColor = when {
                                !isAnswered -> MaterialTheme.colorScheme.outlineVariant
                                isCorrect -> IrabParticleColor
                                isChosen -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Surface(
                                onClick = {
                                    if (selectedAnswerIdx == null) {
                                        selectedAnswerIdx = idx
                                        quizAnsweredCount++
                                        if (idx == q.correctIndex) quizScore++
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = cardColor,
                                border = BorderStroke(1.5.dp, borderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isAnswered && isCorrect) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "صحيح", tint = IrabParticleColor)
                                    } else if (isAnswered && isChosen) {
                                        Icon(Icons.Default.Cancel, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = selectedAnswerIdx != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "القاعدة النحوية:",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = q.explanation,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = {
                                            selectedAnswerIdx = null
                                            currentQuestionIdx = (currentQuestionIdx + 1) % questions.size
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("السؤال التالي", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = { Text("سجل الجمل المعربة", fontWeight = FontWeight.Bold) },
                text = {
                    if (historyList.isEmpty()) {
                        Text("لم تقم بإعراب جمل إضافية في هذه الجلسة بعد.")
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            historyList.forEach { item ->
                                Surface(
                                    onClick = {
                                        inputSentence = item.sentence
                                        analysisResult = item
                                        showHistoryDialog = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.sentence,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }
    }
}

private fun formatIrabForShare(result: IrabAnalysisResult): String {
    val sb = StringBuilder()
    sb.appendLine("📖 الإعراب الذكي — ${result.sentence}")
    sb.appendLine()
    result.tokens.forEach { t ->
        sb.appendLine("• ${t.word}: ${t.irabStatement}")
        sb.appendLine("  العلامة: ${t.sign} | السبب: ${t.reason}")
    }
    return sb.toString()
}
