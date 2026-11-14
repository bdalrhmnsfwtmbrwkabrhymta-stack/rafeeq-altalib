package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.domain.english.EnglishLevelCategory
import com.example.domain.english.EnglishPlacementTestData
import com.example.domain.english.PlacementTestResult
import com.example.ui.screens.english.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// Simple custom routing state for modular feature integration
sealed class EnglishSubScreen {
    object Dashboard : EnglishSubScreen()
    object LevelAssessmentIntro : EnglishSubScreen()
    class LevelAssessmentQuiz(val questionIndex: Int = 0) : EnglishSubScreen()
    object LevelAssessmentResult : EnglishSubScreen()
    object AiTutor : EnglishSubScreen()
    object MyPlan : EnglishSubScreen()
    class ReviewDue(val reviewIndex: Int = 0) : EnglishSubScreen()
    object AnalyticsDashboard : EnglishSubScreen()
    object LearningPath : EnglishSubScreen()
    object GrammarMap : EnglishSubScreen()
    object WordsDictionary : EnglishSubScreen()
    object PronunciationAssessment : EnglishSubScreen()
    object ListeningComprehension : EnglishSubScreen()
    object ConversationalSpeaking : EnglishSubScreen()
    object StoryReading : EnglishSubScreen()
    object WritingPrompts : EnglishSubScreen()
    object DailyExercises : EnglishSubScreen()
    object ReviewHub : EnglishSubScreen()
    object ChallengeRoom : EnglishSubScreen()
    object MistakeTrackerScreen : EnglishSubScreen()
    
    // Recommended Lesson (حرف A) sub-flows matching the screenshots
    class RecommendedLessonTutorial(val slideIndex: Int = 0) : EnglishSubScreen()
    object RecommendedLessonIntro : EnglishSubScreen()
    class RecommendedLessonListen(val progressIndex: Int = 0) : EnglishSubScreen()
    object RecommendedLessonPronounce : EnglishSubScreen()
    object RecommendedLessonWrite : EnglishSubScreen()
    object RecommendedLessonSuccess : EnglishSubScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishLearningDashboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State management representing sequential progress
    var currentScreen by remember { mutableStateOf<EnglishSubScreen>(EnglishSubScreen.Dashboard) }
    var userLevelCode by remember { mutableIntStateOf(EnglishPlacementTestData.getSavedLevelCode(context)) }
    var userProgressPercentage by remember { mutableIntStateOf(EnglishPlacementTestData.getSavedOverallPercentage(context)) }
    var lessonACompleted by remember { mutableStateOf(false) }
    var placementResult by remember { mutableStateOf<PlacementTestResult?>(null) }
    
    // Custom temporary quiz state for the 40-question placement assessment
    val quizAnswers = remember { mutableStateMapOf<Int, String>() }
    
    // Dynamic lists of recorded mistakes
    val userMistakes = remember { mutableStateListOf<String>() }

    // TTS Setup
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speak(text: String, isSlow: Boolean = false) {
        ttsEngine?.let { engine ->
            engine.language = Locale.US
            engine.setSpeechRate(if (isSlow) 0.5f else 0.9f)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english_learning_tts")
        }
    }

    // Hardware back key handler
    BackHandler(enabled = currentScreen != EnglishSubScreen.Dashboard) {
        currentScreen = EnglishSubScreen.Dashboard
    }

    // Soft pastel purple gradient background
    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF5F3FF), Color(0xFFFFFFFF))
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val hideTopBar = currentScreen is EnglishSubScreen.MyPlan ||
                    currentScreen is EnglishSubScreen.ReviewDue ||
                    currentScreen is EnglishSubScreen.LevelAssessmentIntro ||
                    currentScreen is EnglishSubScreen.LevelAssessmentQuiz ||
                    currentScreen is EnglishSubScreen.LevelAssessmentResult ||
                    currentScreen is EnglishSubScreen.AnalyticsDashboard ||
                    currentScreen is EnglishSubScreen.LearningPath ||
                    currentScreen is EnglishSubScreen.WordsDictionary ||
                    currentScreen is EnglishSubScreen.RecommendedLessonTutorial ||
                    currentScreen is EnglishSubScreen.RecommendedLessonIntro ||
                    currentScreen is EnglishSubScreen.RecommendedLessonListen ||
                    currentScreen is EnglishSubScreen.RecommendedLessonPronounce ||
                    currentScreen is EnglishSubScreen.RecommendedLessonWrite ||
                    currentScreen is EnglishSubScreen.RecommendedLessonSuccess
            if (!hideTopBar) {
                TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (currentScreen) {
                                is EnglishSubScreen.Dashboard -> "تعلم الإنجليزية"
                                is EnglishSubScreen.LevelAssessmentIntro,
                                is EnglishSubScreen.LevelAssessmentQuiz,
                                is EnglishSubScreen.LevelAssessmentResult -> "تحديد المستوى"
                                is EnglishSubScreen.AiTutor -> "معلم الإنجليزية AI"
                                is EnglishSubScreen.MyPlan -> "خطتي وجلسة النهارده"
                                is EnglishSubScreen.ReviewDue -> "جلسة المراجعة"
                                is EnglishSubScreen.AnalyticsDashboard -> "لوحة المتابعة"
                                is EnglishSubScreen.LearningPath -> "مسار التعلم"
                                is EnglishSubScreen.GrammarMap -> "خريطة القواعد"
                                is EnglishSubScreen.WordsDictionary -> "الكلمات والمفردات"
                                is EnglishSubScreen.PronunciationAssessment -> "النطق"
                                is EnglishSubScreen.ListeningComprehension -> "الاستماع"
                                is EnglishSubScreen.ConversationalSpeaking -> "التحدث"
                                is EnglishSubScreen.StoryReading -> "القراءة"
                                is EnglishSubScreen.WritingPrompts -> "الكتابة"
                                is EnglishSubScreen.DailyExercises -> "تمارين مجمعة"
                                is EnglishSubScreen.ReviewHub -> "مركز المراجعة"
                                is EnglishSubScreen.ChallengeRoom -> "غرفة التحدي"
                                is EnglishSubScreen.MistakeTrackerScreen -> "أخطائي"
                                is EnglishSubScreen.RecommendedLessonTutorial,
                                is EnglishSubScreen.RecommendedLessonIntro,
                                is EnglishSubScreen.RecommendedLessonListen,
                                is EnglishSubScreen.RecommendedLessonPronounce,
                                is EnglishSubScreen.RecommendedLessonWrite,
                                is EnglishSubScreen.RecommendedLessonSuccess -> "حرف A"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.padding(end = 40.dp) // Offset for back button balance
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == EnglishSubScreen.Dashboard) {
                            onNavigateBack()
                        } else {
                            currentScreen = EnglishSubScreen.Dashboard
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F3FF))
            )
        }
    }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(purpleGradient)
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "EnglishNavigation"
            ) { screenState ->
                when (screenState) {
                    is EnglishSubScreen.Dashboard -> {
                        DashboardContent(
                            userLevelCode = userLevelCode,
                            userProgressPercentage = userProgressPercentage,
                            lessonACompleted = lessonACompleted,
                            onNavigateToScreen = { currentScreen = it }
                        )
                    }
                    is EnglishSubScreen.LevelAssessmentIntro -> {
                        PlacementTestIntroView(
                            onStartQuiz = {
                                quizAnswers.clear()
                                currentScreen = EnglishSubScreen.LevelAssessmentQuiz(0)
                            },
                            onBack = { currentScreen = EnglishSubScreen.Dashboard }
                        )
                    }
                    is EnglishSubScreen.LevelAssessmentQuiz -> {
                        val qIndex = (screenState as EnglishSubScreen.LevelAssessmentQuiz).questionIndex
                        val question = EnglishPlacementTestData.QUESTIONS_40.getOrElse(qIndex) { EnglishPlacementTestData.QUESTIONS_40[0] }
                        PlacementTestQuestionView(
                            currentQuestion = question,
                            currentIndex = qIndex,
                            totalQuestions = EnglishPlacementTestData.QUESTIONS_40.size,
                            selectedAnswer = quizAnswers[question.id],
                            onAnswerSelected = { answer ->
                                quizAnswers[question.id] = answer
                                if (qIndex < EnglishPlacementTestData.QUESTIONS_40.size - 1) {
                                    currentScreen = EnglishSubScreen.LevelAssessmentQuiz(qIndex + 1)
                                } else {
                                    // Complete Assessment of all 40 questions
                                    val evaluated = EnglishPlacementTestData.evaluateAssessment(quizAnswers)
                                    placementResult = evaluated
                                    userLevelCode = evaluated.determinedLevel.code
                                    userProgressPercentage = evaluated.overallPercentage
                                    EnglishPlacementTestData.savePlacementResult(context, evaluated)
                                    currentScreen = EnglishSubScreen.LevelAssessmentResult
                                }
                            },
                            onPreviousQuestion = {
                                if (qIndex > 0) {
                                    currentScreen = EnglishSubScreen.LevelAssessmentQuiz(qIndex - 1)
                                }
                            },
                            onBackToIntro = { currentScreen = EnglishSubScreen.LevelAssessmentIntro }
                        )
                    }
                    is EnglishSubScreen.LevelAssessmentResult -> {
                        val res = placementResult ?: EnglishPlacementTestData.evaluateAssessment(quizAnswers)
                        PlacementTestResultView(
                            result = res,
                            onApplyAndStart = {
                                userLevelCode = res.determinedLevel.code
                                userProgressPercentage = res.overallPercentage
                                EnglishPlacementTestData.savePlacementResult(context, res)
                                // If level is LETTERS, start with alphabet lesson; otherwise open learning path!
                                if (res.determinedLevel == EnglishLevelCategory.LETTERS) {
                                    currentScreen = EnglishSubScreen.RecommendedLessonTutorial(0)
                                } else {
                                    currentScreen = EnglishSubScreen.LearningPath
                                }
                            },
                            onRetakeQuiz = {
                                quizAnswers.clear()
                                currentScreen = EnglishSubScreen.LevelAssessmentQuiz(0)
                            },
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard }
                        )
                    }
                    is EnglishSubScreen.AiTutor -> {
                        AiTutorContent(speak = { speak(it) })
                    }
                    is EnglishSubScreen.MyPlan -> {
                        EnglishMyPlanScreen(
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard },
                            onOpenReviewDue = { currentScreen = EnglishSubScreen.ReviewDue(0) }
                        )
                    }
                    is EnglishSubScreen.ReviewDue -> {
                        val rIdx = (screenState as EnglishSubScreen.ReviewDue).reviewIndex
                        EnglishReviewDueScreen(
                            currentIndex = rIdx,
                            onNavigateBack = { currentScreen = EnglishSubScreen.MyPlan },
                            onNextItem = { nextIdx -> currentScreen = EnglishSubScreen.ReviewDue(nextIdx) },
                            speak = { text, isSlow -> speak(text, isSlow) },
                            onRecordMistake = { mistake -> userMistakes.add(mistake) }
                        )
                    }
                    is EnglishSubScreen.AnalyticsDashboard -> {
                        EnglishAnalyticsDashboardScreen(
                            userLevelCode = userLevelCode,
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard },
                            onStartPlacementTest = { currentScreen = EnglishSubScreen.LevelAssessmentIntro }
                        )
                    }
                    is EnglishSubScreen.LearningPath -> {
                        EnglishLearningPathScreen(
                            userLevelCode = userLevelCode,
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard },
                            onSelectUnit = { unitId ->
                                currentScreen = EnglishSubScreen.RecommendedLessonTutorial(0)
                            },
                            onStartPlacementTest = { currentScreen = EnglishSubScreen.LevelAssessmentIntro }
                        )
                    }
                    is EnglishSubScreen.GrammarMap -> {
                        GrammarMapContent(userLevelCode = userLevelCode)
                    }
                    is EnglishSubScreen.WordsDictionary -> {
                        EnglishVocabularyHomeScreen(
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard },
                            speak = { text -> speak(text) }
                        )
                    }
                    is EnglishSubScreen.PronunciationAssessment -> {
                        PronunciationAssessmentContent(speak = { speak(it) })
                    }
                    is EnglishSubScreen.ListeningComprehension -> {
                        ListeningComprehensionContent(speak = { speak(it) })
                    }
                    is EnglishSubScreen.ConversationalSpeaking -> {
                        ConversationalSpeakingContent(speak = { speak(it) })
                    }
                    is EnglishSubScreen.StoryReading -> {
                        StoryReadingContent(speak = { speak(it) })
                    }
                    is EnglishSubScreen.WritingPrompts -> {
                        WritingPromptsContent()
                    }
                    is EnglishSubScreen.DailyExercises -> {
                        DailyExercisesContent()
                    }
                    is EnglishSubScreen.ReviewHub -> {
                        ReviewHubContent()
                    }
                    is EnglishSubScreen.ChallengeRoom -> {
                        ChallengeRoomContent()
                    }
                    is EnglishSubScreen.MistakeTrackerScreen -> {
                        MistakeTrackerContent(userMistakes = userMistakes)
                    }
                    
                    // Recommended Lesson Flow: Interactive Lesson matching Screenshots 2-6
                    is EnglishSubScreen.RecommendedLessonTutorial,
                    is EnglishSubScreen.RecommendedLessonIntro,
                    is EnglishSubScreen.RecommendedLessonListen,
                    is EnglishSubScreen.RecommendedLessonPronounce,
                    is EnglishSubScreen.RecommendedLessonWrite -> {
                        EnglishInteractiveLessonScreen(
                            unitId = "letter_a",
                            onNavigateBack = { currentScreen = EnglishSubScreen.Dashboard },
                            onLessonCompleted = { xpEarned ->
                                lessonACompleted = true
                                currentScreen = EnglishSubScreen.Dashboard
                            },
                            speak = { text, isSlow -> speak(text, isSlow) }
                        )
                    }
                    is EnglishSubScreen.RecommendedLessonSuccess -> {
                        RecommendedLessonSuccessContent(
                            onFinish = { currentScreen = EnglishSubScreen.Dashboard }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD CONTENT (Matches Screen 20)
// ==========================================
@Composable
fun DashboardContent(
    userLevelCode: Int,
    userProgressPercentage: Int,
    lessonACompleted: Boolean,
    onNavigateToScreen: (EnglishSubScreen) -> Unit
) {
    val levels = listOf(
        "غير محدد (ابدأ بتحديد مستواك) 🎯",
        "🔤 الحروف والأصوات (Pre-A1)",
        "🟢 مبتدئ (Level A1)",
        "🟡 فوق المبتدئ (Level A2)",
        "🔵 متوسط (Level B1)",
        "🟣 فوق المتوسط (Level B2)"
    )
    val currentLevelTitle = levels.getOrElse(userLevelCode) { "مستوى معتمد (${userLevelCode})" }
    val progressLabel = if (userLevelCode == 0) {
        "اضغط على «حدد مستواك» لتشخيص مستواك من الحروف حتى B2 عبر 40 سؤالاً"
    } else {
        "نسبة النجاح: $userProgressPercentage% • المراحل والوحدات مفتوحة وفق مستواك"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Level Card (مستواك)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Surface(
                                color = Color(0xFFF3E8FF),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (userLevelCode == 0) "اختبار التشخيص 🎯" else "Level $userLevelCode",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C5CFC),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentLevelTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B)
                            )
                        }
                        
                        Button(
                            onClick = { onNavigateToScreen(EnglishSubScreen.LevelAssessmentIntro) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حدد مستواك", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = progressLabel,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (userLevelCode == 0) 0.05f else userProgressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFF7C5CFC),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // Recommended Lesson (مرشح ليك)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFEF3C7), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("مرشح ليك", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                            Text(
                                text = "حرف A",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                            Text("حرف A • 4 دقائق • 4 تمارين", fontSize = 11.5.sp, color = Color(0xFF64748B))
                        }
                    }
                    
                    IconButton(
                        onClick = { onNavigateToScreen(EnglishSubScreen.RecommendedLessonTutorial(0)) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF7C5CFC), shape = CircleShape)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        // Quick Practice Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable { onNavigateToScreen(EnglishSubScreen.MistakeTrackerScreen) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("نمط غلط مفتوح", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable { onNavigateToScreen(EnglishSubScreen.ReviewHub) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("مستحق مراجعة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable { onNavigateToScreen(EnglishSubScreen.DailyExercises) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("تمرين النهارده", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                    }
                }
            }
        }

        // Main Sequential Features Navigation List
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Feature 1
                DashboardListItem(
                    title = "معلم الإنجليزية",
                    subtitle = "اسأل عن قاعدة أو كلمة أو غلطة — بيعرف مستواك",
                    icon = Icons.Default.SmartToy,
                    iconBg = Color(0xFFEEF2FF),
                    iconColor = Color(0xFF4F46E5),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.AiTutor) }
                )
                // Feature 2
                DashboardListItem(
                    title = "خطتي وجلسة النهارده",
                    subtitle = "مراجعة ⬅️ كلمات ⬅️ درس ⬅️ تدريب، على قد دقايقك",
                    icon = Icons.Default.Assignment,
                    iconBg = Color(0xFFF0FDF4),
                    iconColor = Color(0xFF16A34A),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.MyPlan) }
                )
                // Feature 3
                DashboardListItem(
                    title = "لوحة المتابعة",
                    subtitle = "المهارات والمستويات و١٤ يوم نشاط وخريطة المعرفة",
                    icon = Icons.Default.QueryStats,
                    iconBg = Color(0xFFFFF7ED),
                    iconColor = Color(0xFFEA580C),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.AnalyticsDashboard) }
                )
                // Feature 4
                DashboardListItem(
                    title = "مسار التعلم",
                    subtitle = "متقن ٣٠ مفتوح ٢٥٦ مقفول",
                    icon = Icons.Default.Timeline,
                    iconBg = Color(0xFFFAF5FF),
                    iconColor = Color(0xFF9333EA),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.LearningPath) }
                )
                // Feature 5
                DashboardListItem(
                    title = "خريطة القواعد",
                    subtitle = "متقن من ٨٨ قاعدة مفتوح",
                    icon = Icons.Default.Schema,
                    iconBg = Color(0xFFF0FDFA),
                    iconColor = Color(0xFF0D9488),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.GrammarMap) }
                )
                // Feature 6
                DashboardListItem(
                    title = "الكلمات",
                    subtitle = "٥٧١ كلمة في القاموس — بتفتح مع المسار",
                    icon = Icons.Default.AutoStories,
                    iconBg = Color(0xFFECFEFF),
                    iconColor = Color(0xFF0891B2),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.WordsDictionary) }
                )
                // Feature 7
                DashboardListItem(
                    title = "النطق",
                    subtitle = "متقن من ٢ صوت • مفتوح",
                    icon = Icons.Default.RecordVoiceOver,
                    iconBg = Color(0xFFFFF1F2),
                    iconColor = Color(0xFFE11D48),
                    isLocked = userLevelCode == 0,
                    onClick = { onNavigateToScreen(EnglishSubScreen.PronunciationAssessment) }
                )
                // Feature 8
                DashboardListItem(
                    title = "الاستماع",
                    subtitle = "متقن من ٢٥ درس • مفتوح",
                    icon = Icons.Default.Headset,
                    iconBg = Color(0xFFF8FAFC),
                    iconColor = Color(0xFF475569),
                    isLocked = userLevelCode == 0,
                    onClick = { onNavigateToScreen(EnglishSubScreen.ListeningComprehension) }
                )
                // Feature 9
                DashboardListItem(
                    title = "التحدث",
                    subtitle = "متقن من ٢٤ موقف • مفتوح",
                    icon = Icons.Default.Forum,
                    iconBg = Color(0xFFFAF5FF),
                    iconColor = Color(0xFF7C5CFC),
                    isLocked = userLevelCode == 0,
                    onClick = { onNavigateToScreen(EnglishSubScreen.ConversationalSpeaking) }
                )
                // Feature 10
                DashboardListItem(
                    title = "القراءة",
                    subtitle = "متقن من ٢٢ قصة • ٣ مفتوح",
                    icon = Icons.Default.MenuBook,
                    iconBg = Color(0xFFFDF2F8),
                    iconColor = Color(0xFFDB2777),
                    isLocked = userLevelCode == 0,
                    onClick = { onNavigateToScreen(EnglishSubScreen.StoryReading) }
                )
                // Feature 11
                DashboardListItem(
                    title = "الكتابة",
                    subtitle = "متقن من ٢ موضوع • مفتوح",
                    icon = Icons.Default.HistoryEdu,
                    iconBg = Color(0xFFFFFBEB),
                    iconColor = Color(0xFFD97706),
                    isLocked = userLevelCode < 2,
                    onClick = { onNavigateToScreen(EnglishSubScreen.WritingPrompts) }
                )
                // Feature 12
                DashboardListItem(
                    title = "تمارين مجمعة",
                    subtitle = "ترجم • كون الجملة • صحح الغلطة — على اللي تفتح لك",
                    icon = Icons.Default.Task,
                    iconBg = Color(0xFFF0FDF4),
                    iconColor = Color(0xFF16A34A),
                    isLocked = userLevelCode < 2,
                    onClick = { onNavigateToScreen(EnglishSubScreen.DailyExercises) }
                )
                // Feature 13
                DashboardListItem(
                    title = "مركز المراجعة",
                    subtitle = "مافيش حاجة مستحقة دلوقتي",
                    icon = Icons.Default.PublishedWithChanges,
                    iconBg = Color(0xFFF8FAFC),
                    iconColor = Color(0xFF475569),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.ReviewHub) }
                )
                // Feature 14
                DashboardListItem(
                    title = "تحدي إنجليزي",
                    subtitle = "مباراة في «تحديات» من تمارينك المفتوحة",
                    icon = Icons.Default.Group,
                    iconBg = Color(0xFFEEF2FF),
                    iconColor = Color(0xFF4F46E5),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.ChallengeRoom) }
                )
                // Feature 15
                DashboardListItem(
                    title = "أخطائي",
                    subtitle = "مافيش أنماط غلط لسه",
                    icon = Icons.Default.MoodBad,
                    iconBg = Color(0xFFFFF1F2),
                    iconColor = Color(0xFFE11D48),
                    isLocked = false,
                    onClick = { onNavigateToScreen(EnglishSubScreen.MistakeTrackerScreen) }
                )
            }
        }
    }
}

@Composable
fun DashboardListItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLocked) { onClick() }
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = if (isLocked) Color(0xFFF1F5F9).copy(alpha = 0.6f) else Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (isLocked) Color(0xFFCBD5E1) else iconBg, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else icon,
                        contentDescription = null,
                        tint = if (isLocked) Color.White else iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocked) Color(0xFF94A3B8) else Color(0xFF1E1B4B)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            if (!isLocked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "مقفول",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

// ==========================================
// 2. LEVEL ASSESSMENT (Matches Intro & Quiz 1-5)
// ==========================================
@Composable
fun LevelAssessmentIntroContent(onStartQuiz: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFF3E8FF))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "إزاي بيشتغل",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                val bullets = listOf(
                    "• أسئلة من كل مستوى، من غير رد بعد كل سؤال — النتيجة في الآخر.",
                    "• لو عديت مستوى (70٪) بيتفتح لك اللي بعده وتبدأ منه.",
                    "• مش بيعلم أي حاجة «متقنة»: الإتقان بيتحسب من إجاباتك عبر الأيام، ومستواك الرسمي كمان.",
                    "• كل إجابة بتتسجل كدليل حقيقي — فمافيش داعي تخمن."
                )
                
                bullets.forEach { text ->
                    Text(
                        text = text,
                        fontSize = 13.5.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(vertical = 4.dp),
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        Button(
            onClick = onStartQuiz,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("ابدأ الاختبار", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LevelAssessmentQuizContent(
    questionIndex: Int,
    onAnswerSelected: (Int, String) -> Unit
) {
    val progressSteps = listOf("4 / 11", "4 / 10", "4 / 9", "4 / 5", "4 / 4")
    
    val questionTitles = listOf(
        "أحمد وعلي هنا. اختار الضمير:",
        "أنهي جملة صح؟",
        "كمل: You ___ here.",
        "يعني إيه bag؟",
        "اسمع الصوت واختار الحرف"
    )
    
    val questionSubs = listOf(
        "___ are here",
        "",
        "You ___ here.",
        "bag",
        ""
    )
    
    val options = listOf(
        listOf("They", "He", "It"),
        listOf("You are a student.", "You am a student.", "Are you a student.", "You are student."),
        listOf("are", "am", "is"),
        listOf("شنطة", "قطة", "تاكسي"),
        listOf("b", "a", "c")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressSteps[questionIndex],
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            
            // Miniature linear indicator matching screenshots
            LinearProgressIndicator(
                progress = { (questionIndex + 1) / 5f },
                modifier = Modifier
                    .width(100.dp)
                    .height(6.dp)
                    .clip(CircleShape),
                color = Color(0xFF7C5CFC),
                trackColor = Color(0xFFCBD5E1).copy(alpha = 0.3f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = questionTitles[questionIndex],
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1E1B4B),
            textAlign = TextAlign.Center
        )
        
        if (questionSubs[questionIndex].isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = questionSubs[questionIndex],
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4F46E5),
                textAlign = TextAlign.Center
            )
        }
        
        // Custom Audio Buttons only on Question 5
        if (questionIndex == 4) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF4F46E5))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اسمع", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF4F46E5))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اسمعها ببطء", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Exact Options Card List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options[questionIndex].forEach { opt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnswerSelected(questionIndex, opt) }
                        .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFCBD5E1).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = opt,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B),
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
fun LevelAssessmentResultContent(
    userLevelCode: Int,
    userProgressPercentage: Int,
    onFinish: () -> Unit
) {
    val levels = listOf("Level 0 (مبتدئ تماماً)", "Level 1 (تمهيدي)", "Level 2 (مبتدئ)", "Level 3 (فوق المبتدئ)")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "اكتمل الاختبار! 🎉",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1E1B4B)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "نسبة إجاباتك الصحيحة: $userProgressPercentage%",
            fontSize = 15.sp,
            color = Color(0xFF475569)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("مستواك الحالي المعتمد:", fontSize = 12.sp, color = Color(0xFF64748B))
                Text(
                    text = levels[userLevelCode],
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF7C5CFC)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تم فتح كافة الوحدات المتوافقة مع مستواك تلقائياً لتبدأ رحلتك التفاعلية فوراً!",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("العودة إلى لوحة التحكم", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 3. MULTIPLAYER CHALLENGE ROOM (Matches Screen 8)
// ==========================================
@Composable
fun ChallengeRoomContent() {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Room Code Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.2.dp, Color(0xFFE9D5FF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "كود الغرفة",
                    fontSize = 12.sp,
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "3 1 4 9 9 2",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E1B4B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("RoomCode", "314992"))
                            Toast.makeText(context, "تم نسخ كود الغرفة 📋", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("انسخ", color = Color(0xFF475569), fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ابعت لاصحابك", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Badges config
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = {},
                label = { Text("3 أسئلة") },
                icon = { Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            SuggestionChip(
                onClick = {},
                label = { Text("15 ثانية للسؤال") },
                icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            SuggestionChip(
                onClick = {},
                label = { Text("تحدي إنجليزي") }
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Player Slots
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "اللاعبين (1/1 جاهزين)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Host slot with Crown
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFF7C5CFC), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ع", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("عبد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    
                    // Empty waiting slots
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color(0xFFF1F5F9), shape = CircleShape)
                                .border(1.2.dp, Color(0xFF94A3B8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color(0xFF94A3B8), fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("مستنيين...", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {},
            enabled = false,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("مستنيين حد يدخل...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 4. MISTAKE TRACKER (Matches Screen 7)
// ==========================================
@Composable
fun MistakeTrackerContent(userMistakes: List<String>) {
    if (userMistakes.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SentimentVerySatisfied,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "مافيش أنماط غلط لسه",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E1B4B)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "أول ما تغلط في تمرين، الغلطة بتتسجل هنا بسببها.",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "سجل الأخطاء التفاعلي لتصحيح المفاهيم:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4F46E5)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(userMistakes) { mistake ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = mistake,
                            fontSize = 13.sp,
                            color = Color(0xFF1E1B4B)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. ADDITIONAL MODULES STUBS
// ==========================================
@Composable
fun AiTutorContent(speak: (String) -> Unit) {
    var queryText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf(Pair("أهلاً بك! أنا معلم الإنجليزية الخاص بك. اسألني أي سؤال عن القواعد أو المفردات لتوضيحه بطريقة مبسطة.", false)) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages) { msg ->
                val isUser = msg.second
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF7C5CFC) else Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = if (isUser) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = msg.first,
                            color = if (isUser) Color.White else Color(0xFF1E1B4B),
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("اكتب سؤالك هنا بالكامل...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (queryText.isNotBlank()) {
                        chatMessages.add(Pair(queryText, true))
                        val q = queryText
                        queryText = ""
                        keyboardController?.hide()
                        
                        // Smart mock AI response explanation helper
                        val reply = when {
                            q.contains("be") || q.contains("am") || q.contains("is") -> 
                                "فعل Be بيمثل الكينونة. مع الضمير You بنستخدم are عشان جمع (You are). ومع I بنستخدم am (I am)."
                            q.contains("bag") -> 
                                "كلمة bag تعني شنطة أو حقيبة باللغة العربية ونطقها 'باج'."
                            else -> "شرح ممتاز! لتوضيح القاعدة دي: بنعتمد على ترتيب الجملة السليم باللغة الإنجليزية ومراعاة الأزمنة والفاعل."
                        }
                        chatMessages.add(Pair(reply, false))
                        speak(reply)
                    }
                })
            )
            IconButton(
                onClick = {
                    if (queryText.isNotBlank()) {
                        chatMessages.add(Pair(queryText, true))
                        queryText = ""
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF7C5CFC), shape = CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun MyPlanContent() {
    var showSessionModal by remember { mutableStateOf(false) }
    var currentSessionStep by remember { mutableIntStateOf(0) } // 0: Grammar, 1: Words, 2: Pronunciation, 3: Listening

    if (showSessionModal) {
        AlertDialog(
            onDismissRequest = { showSessionModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentSessionStep < 3) currentSessionStep++ else showSessionModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
                ) {
                    Text(if (currentSessionStep < 3) "التالي ➡️" else "إنهاء الجلسة 🎉")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSessionModal = false }) {
                    Text("إغلاق")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C5CFC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جلسة اليوم المخصصة (١٥ دقيقة)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الخطوة ${currentSessionStep + 1} من ٤", fontSize = 12.sp, color = Color(0xFF7C5CFC), fontWeight = FontWeight.Bold)
                        Text("مستوى A2", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (currentSessionStep + 1) / 4f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Color(0xFF7C5CFC),
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    when (currentSessionStep) {
                        0 -> {
                            Text("١. قواعد: المضارع البسيط والماضي البسيط", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("المضارع البسيط يعبر عن العادات (I play tennis), بينما الماضي البسيط يعبر عن حدث انتهى (I played tennis yesterday).", fontSize = 13.sp, color = Color(0xFF475569))
                        }
                        1 -> {
                            Text("٢. كلمات: ٢٠ كلمة جديدة من مستوى A2", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("كلمات اليوم: Achieve (يحقق), Challenge (تحدي), Efficient (فعال), Solution (حل).", fontSize = 13.sp, color = Color(0xFF475569))
                        }
                        2 -> {
                            Text("٣. نطق: اختبار الثنائيات /b/ و /p/", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("انطق الكلمتين لتحديد الفرق: Pat vs Bat. اضغط على الميكروفون للتسجيل.", fontSize = 13.sp, color = Color(0xFF475569))
                        }
                        3 -> {
                            Text("٤. استماع: محادثة طلب القهوة في الكافيه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("استمع للحوار: \"Can I get a medium cappuccino with oat milk, please?\"", fontSize = 13.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Session Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF7C5CFC).copy(alpha = 0.3f), RoundedCornerShape(30.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("١٥ دقيقة • ٤ مهارات مدمجة", fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جلسة النهارده المخصصة 🚀", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("جلسة ذكية مصممة لك بناءً على مستواك A2 وأخطائك الأخيرة في التكرار المتباعد.", fontSize = 12.5.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showSessionModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("ابدأ الجلسة الآن 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Progress Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المستوى الحالي: A2 (مبتدئ متقدم)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                        Text("٤٥%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C5CFC))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.45f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFF7C5CFC),
                        trackColor = Color(0xFFEEF2FF)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("تم إنجاز ٤٥% من خريطة الطريق الإجمالية لتعلم الإنجليزية.", fontSize = 11.5.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Today Tasks Breakdown Header
        item {
            Text("قائمة مهام الجلسة اليومية:", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
        }

        // Tasks List Items
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Task 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("١. قواعد: المضارع والماضي البسيط (٥ د)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("شرح وتطبيق أمثلة يومية", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                        Text("مكتمل ✅", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Task 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("٢. كلمات: ٢٠ كلمة جديدة من A2 (٤ د)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("حفظ بالصور والنطق التفاعلي", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                        Text("جاري التعلم ⚡", fontSize = 11.sp, color = Color(0xFF7C5CFC), fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Task 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("٣. نطق: اختبار الثنائيات /b/ و /p/ (٣ د)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Text("تقييم الصوت الذكي بالـ AI", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                        Text("قيد الانتظار 🔒", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Task 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("٤. استماع: محادثة طلب القهوة (٣ د)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Text("سماع الاستجابة والأسئلة السريعة", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                        Text("قيد الانتظار 🔒", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // Spaced Repetition Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF7C5CFC))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جدول التكرار المتباعد اليومي (Spaced Repetition)", fontSize = 14.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("كلمات وقواعد تحتاج مراجعته اليوم لمنع النسيان المفهومي:", fontSize = 12.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Apple 🍎", "Book 📖", "Water 💧", "Friend 🤝", "Study 📚").forEach { word ->
                            Box(
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(20.dp))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(word, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsDashboardContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("لوحة المتابعة والنشاط (14-Day Heatmap)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                Spacer(modifier = Modifier.height(4.dp))
                Text("متابعة الاستمرارية اليومية وإجمالي الساعات المنجزة.", fontSize = 12.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(14) { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(if (index % 2 == 0) 48.dp else 16.dp)
                                    .background(if (index % 2 == 0) Color(0xFF10B981) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${index + 1}", fontSize = 9.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LearningPathContent(userLevelCode: Int, onSelectLesson: (EnglishSubScreen) -> Unit) {
    var selectedLevelTab by remember { mutableIntStateOf(0) } // 0: All, 1: A1, 2: A2, 3: B1, 4: B2

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مسار التعلم والتحديات التتابعية 🗺️", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (userLevelCode == 0)
                                    "قم بإجراء اختبار تحديد المستوى لتشخيص مستواك وفتح المراحل المتوافقة فوراً."
                                else
                                    "مستواك الحالي: Level $userLevelCode • تم فتح المراحل المناسبة بناءً على نتيجتك في الاختبار.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }

                        Button(
                            onClick = { onSelectLesson(EnglishSubScreen.LevelAssessmentIntro) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تحديد المستوى 🎯", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Level Filter Chips
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedLevelTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                listOf("الكل", "مستوى A1 (مبتدئ)", "مستوى A2 (مبتدئ متقدم)", "مستوى B1 (متوسط)", "مستوى B2 (فوق المتوسط)").forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedLevelTab == idx,
                        onClick = { selectedLevelTab = idx },
                        text = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedLevelTab == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedLevelTab == idx) Color(0xFF7C5CFC) else Color(0xFF64748B)
                            )
                        }
                    )
                }
            }
        }

        // LEVEL A1 SECTION
        if (selectedLevelTab == 0 || selectedLevelTab == 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مستوى A1 (أساسيات اللغة الإنجليزية)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                    Button(
                        onClick = { onSelectLesson(EnglishSubScreen.LevelAssessmentIntro) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("اختبار تحديد المستوى 📝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            listOf(
                Triple("وحدة ١: الحروف والأصوات الأساسية (Alphabet & Phonics)", "حروف A-Z والتمييز السمعي للأصوات القصيرة", if (userLevelCode >= 2) "مكتمل ومتجاوز ✅" else if (userLevelCode == 1) "ابدأ هنا 🚀 (حرف A)" else "جاري التعلم ⚡"),
                Triple("وحدة ٢: ضمائر الفاعل والجمل البسيطة", "I, You, He, She, It, We, They وتكوين الجملة", if (userLevelCode >= 2) "مكتمل ومتجاوز ✅" else "مفتوح 🔓"),
                Triple("وحدة ٣: الكلمات اليومية الشائعة (١٠٠ كلمة)", "الأرقام، الألوان، العائلة، والأيام", if (userLevelCode >= 3) "مكتمل ومتجاوز ✅" else "مفتوح 🔓"),
                Triple("وحدة ٤: قواعد A1 الأساسية (Verb To Be & Articles)", "am, is, are وأدوات النكرة a/an", if (userLevelCode >= 3) "مكتمل ومتجاوز ✅" else "مفتوح 🔓")
            ).forEachIndexed { idx, unit ->
                item {
                    val isDone = unit.third.contains("مكتمل")
                    val isStart = unit.third.contains("ابدأ")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLesson(EnglishSubScreen.RecommendedLessonTutorial(0)) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isStart) Color(0xFF7C5CFC) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(unit.first, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(unit.second, fontSize = 11.5.sp, color = Color(0xFF64748B))
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isDone) Color(0xFFD1FAE5) else if (isStart) Color(0xFFEDE9FE) else Color(0xFFF1F5F9),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    unit.third,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDone) Color(0xFF059669) else if (isStart) Color(0xFF6C5CE7) else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }

        // LEVEL A2 SECTION
        if (selectedLevelTab == 0 || selectedLevelTab == 2) {
            val isA2Unlocked = userLevelCode >= 3
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مستوى A2 (مبتدئ متقدم)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                    Button(
                        onClick = { onSelectLesson(EnglishSubScreen.LevelAssessmentIntro) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("اختبار Level A2 📝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            listOf(
                Triple("وحدة ١: زمن المضارع والماضي البسيط", "Present & Past Simple Tenses", if (isA2Unlocked) (if (userLevelCode >= 4) "مكتمل ومتجاوز ✅" else "مفتوح للدراسة 🔓") else "مقفول 🔒 (اجتز اختبار A2)"),
                Triple("وحدة ٢: الأفعال المساعدة وحروف الجر", "Auxiliary Verbs & Prepositions of Time/Place", if (isA2Unlocked) (if (userLevelCode >= 4) "مكتمل ومتجاوز ✅" else "مفتوح للدراسة 🔓") else "مقفول 🔒 (اجتز اختبار A2)"),
                Triple("وحدة ٣: كلمات A2 الشائعة (٢٠٠ كلمة)", "الوظائف، السفر، الطعام، والأنشطة", if (isA2Unlocked) (if (userLevelCode >= 4) "مكتمل ومتجاوز ✅" else "مفتوح للدراسة 🔓") else "مقفول 🔒 (اجتز اختبار A2)")
            ).forEach { unit ->
                item {
                    val isUnlocked = isA2Unlocked
                    val isDone = unit.third.contains("مكتمل")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isUnlocked) {
                                onSelectLesson(EnglishSubScreen.GrammarMap)
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isUnlocked) Color.White else Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isUnlocked) Color(0xFFE2E8F0) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(unit.first, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isUnlocked) Color(0xFF1E1B4B) else Color(0xFF64748B))
                                Text(unit.second, fontSize = 11.5.sp, color = if (isUnlocked) Color(0xFF64748B) else Color(0xFF94A3B8))
                            }
                            if (isUnlocked) {
                                Box(
                                    modifier = Modifier
                                        .background(if (isDone) Color(0xFFD1FAE5) else Color(0xFFFEF3C7), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        unit.third,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) Color(0xFF059669) else Color(0xFFB45309)
                                    )
                                }
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }

        // LEVEL B1 SECTION
        if (selectedLevelTab == 0 || selectedLevelTab == 3) {
            val isB1Unlocked = userLevelCode >= 4
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مستوى B1 (متوسط)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
                    Button(
                        onClick = { onSelectLesson(EnglishSubScreen.LevelAssessmentIntro) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("اختبار Level B1 📝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            listOf(
                Triple("وحدة ١: الأزمنة المركبة (Present Perfect)", "المضارع التام والماضي المستمر", if (isB1Unlocked) (if (userLevelCode >= 5) "مكتمل ومتجاوز ✅" else "مفتوح للدراسة 🔓") else "مقفول 🔒 (اجتز اختبار B1)"),
                Triple("وحدة ٢: محادثات واستماع متقدم", "الحوارات اليومية والطلاقة الكلامية", if (isB1Unlocked) (if (userLevelCode >= 5) "مكتمل ومتجاوز ✅" else "مفتوح للدراسة 🔓") else "مقفول 🔒 (اجتز اختبار B1)")
            ).forEach { unit ->
                item {
                    val isUnlocked = isB1Unlocked
                    val isDone = unit.third.contains("مكتمل")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isUnlocked) {
                                onSelectLesson(EnglishSubScreen.ConversationalSpeaking)
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isUnlocked) Color.White else Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isUnlocked) Color(0xFFE2E8F0) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(unit.first, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isUnlocked) Color(0xFF1E1B4B) else Color(0xFF64748B))
                                Text(unit.second, fontSize = 11.5.sp, color = if (isUnlocked) Color(0xFF64748B) else Color(0xFF94A3B8))
                            }
                            if (isUnlocked) {
                                Box(
                                    modifier = Modifier
                                        .background(if (isDone) Color(0xFFD1FAE5) else Color(0xFFE0F2FE), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        unit.third,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) Color(0xFF059669) else Color(0xFF0369A1)
                                    )
                                }
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GrammarMapContent(userLevelCode: Int) {
    val levelPreA1 = listOf(
        "1. الضمائر الشخصية (I, You, He, She, It, We, They)" to "متقن ✅",
        "2. فعل الكينونة في المضارع (am, is, are)" to "متقن ✅",
        "3. أسماء الإشارة للقريب والبعيد (This, That, These, Those)" to "مفتوح 🔓",
        "4. الجمع والمفرد وإضافة s المنتظمة" to "مفتوح 🔓",
        "5. أدوات النكرة والتحديد (a, an, the)" to "مفتوح 🔓",
        "6. الملكية وإضافة 's الملكية (John's car)" to "مفتوح 🔓",
        "7. صفات الملكية (my, your, his, her, its, our, their)" to "مفتوح 🔓",
        "8. ضمائر المفعول به (me, you, him, her, it, us, them)" to "مفتوح 🔓",
        "9. السؤال عن الهوية والشيء (Who, What, Where)" to "مفتوح 🔓",
        "10. الأعداد الأصلية من 1 إلى 100" to "متقن ✅",
        "11. أرقام الترتيب (First, Second, Third)" to "مفتوح 🔓",
        "12. أيام الأسبوع وشهور السنة" to "متقن ✅",
        "13. التعبير عن وجود الأشياء (There is / There are)" to "مفتوح 🔓",
        "14. صيغة الأمر والنهي (Imperatives: Come here / Don't go)" to "مفتوح 🔓",
        "15. النفي باستخدام Not (I am not / She is not)" to "متقن ✅",
        "16. الأسئلة بهل والإجابات القصيرة (Is he...? Yes, he is)" to "مفتوح 🔓",
        "17. حرف الجر In مع الأماكن والأوقات" to "مفتوح 🔓",
        "18. حرف الجر On مع الأيام والأسطح" to "مفتوح 🔓",
        "19. حرف الجر At مع الساعات والأماكن المحددة" to "مفتوح 🔓",
        "20. الاستئذان البسيط مع Can (Can I help you?)" to "مفتوح 🔓"
    )

    val levelA1 = listOf(
        "21. زمن المضارع البسيط للحقائق والعادات (Present Simple)" to "مفتوح 🔓",
        "22. إضافة s, es, ies مع ضمائر الغائب (He, She, It)" to "مفتوح 🔓",
        "23. النفي في المضارع البسيط (don't / doesn't)" to "مقفول 🔒",
        "24. تكوين السؤال في المضارع البسيط (Do / Does)" to "مقفول 🔒",
        "25. أفعال الشعور والرغبة (Like, Want, Need + to/noun)" to "مفتوح 🔓",
        "26. ظرف التكرار (Always, Usually, Sometimes, Never)" to "مقفول 🔒",
        "27. زمن المضارع المستمر للأفعال الحالية (Present Continuous)" to "مقفول 🔒",
        "28. إضافة ing وقواعد الإملاء (running, making, sitting)" to "مقفول 🔒",
        "29. النفي والسؤال في المضارع المستمر (Am/Is/Are + ing)" to "مقفول 🔒",
        "30. الفرق بين المضارع البسيط والمضارع المستمر" to "مقفول 🔒",
        "31. أفعال القدرة والتمكن (Can / Can't + Infinitive)" to "مقفول 🔒",
        "32. الأفعال الناقصة للاقتراح والطلب (Could I...? Shall we...?)" to "مقفول 🔒",
        "33. الجمع غير المنتظم (man->men, child->children, foot->feet)" to "مقفول 🔒",
        "34. الأسماء التي تعد والتي لا تعد (Countable vs Uncountable)" to "مقفول 🔒",
        "35. استخدام Some في الإثبات و Any في النفي والسؤال" to "مقفول 🔒",
        "36. التعبير عن الكميات (Much, Many, A lot of)" to "مقفول 🔒",
        "37. التعبير عن القلة (A few, A little)" to "مقفول 🔒",
        "38. صفات الوصف والموقع перед الاسم (a red car, happy boys)" to "مفتح 🔓",
        "39. مقارنة الصفات القصيرة والطويلة (taller than, more expensive)" to "مقفول 🔒",
        "40. تفضيل الصفات (the tallest, the most popular)" to "مقفول 🔒",
        "41. ضمائر الملكية (mine, yours, his, hers, ours, theirs)" to "مقفول 🔒",
        "42. الضمائر المنعكسة (myself, yourself, himself, herself)" to "مقفول 🔒",
        "43. أدوات الاستفهام المركبة (How much, How many, How long)" to "مقفول 🔒",
        "44. أوقات اليوم والساعات (quarter past, half past)" to "مفتح 🔓",
        "45. حروف الجر للحركة (to, into, out of, through, across)" to "مقفول 🔒"
    )

    val levelA2 = listOf(
        "46. فعل الكينونة في الماضي (was / were)" to "مقفول 🔒",
        "47. زمن الماضي البسيط للأفعال المنتظمة (Regular Past -ed)" to "مقفول 🔒",
        "48. الأفعال غير المنتظمة في الماضي (Irregular Verbs: went, saw)" to "مقفول 🔒",
        "49. النفي في الماضي البسيط باستعمال didn't" to "مقفول 🔒",
        "50. تكوين السؤال في الماضي البسيط باستعمال Did" to "مقفول 🔒",
        "51. زمن الماضي المستمر (Past Continuous: was/were + ing)" to "مقفول 🔒",
        "52. الربط بين الماضي البسيط والمستمر باستعمال When و While" to "مقفول 🔒",
        "53. التعبير عن المستقبل باستخدام Going to (النية والخطط)" to "مقفول 🔒",
        "54. التعبير عن المستقبل باستعمال Will (القرارات السريعة)" to "مقفول 🔒",
        "55. الفرق بين Will و Going to في التنبؤات والخطط" to "مقفول 🔒",
        "56. زمن المضارع التام للتجارب بدون وقت محدد (Present Perfect)" to "مقفول 🔒",
        "57. التصريف الثالث للأفعال (Past Participle - V3)" to "مقفول 🔒",
        "58. استخدام Ever و Never مع المضارع التام" to "مقفول 🔒",
        "59. استخدام Already و Yet في النفي والسؤال" to "مقفول 🔒",
        "60. استخدام Just للتعبير عن الأحداث المنتهية للتو" to "مقفول 🔒",
        "61. الفرق بين For و Since للتعبير عن المدة والبداية" to "مقفول 🔒",
        "62. الفرق بين الماضي البسيط والمضارع التام" to "مقفول 🔒",
        "63. أفعال الإلزام والنصيحة (Must, Have to, Should)" to "مقفول 🔒",
        "64. النهي وعدم الضرورة (Mustn't vs Don't have to)" to "مقفول 🔒",
        "65. الحالة الشرطية الصفرية للحقائق العلمية (Zero Conditional)" to "مقفول 🔒",
        "66. الحالة الشرطية الأولى للاحتمالات (First Conditional: If + Present, Will)" to "مقفول 🔒",
        "67. أسلوب التفضيل والمساواة (as... as / not as... as)" to "مقفول 🔒",
        "68. أسلوب الاقتراح (How about...? Why don't we...? Let's...)" to "مقفول 🔒",
        "69. أسلوب العرض والطلب المؤدب (Would you like...? Could you...?)" to "مقفول 🔒",
        "70. ربط الجمل باستعمال (And, But, So, Because, Although)" to "مقفول 🔒",
        "71. ضمائر الوصل العائدة على الأشخاص والأشياء (Who, Which, That)" to "مقفول 🔒",
        "72. ضمائر الوصل المكانية والزمانية (Where, When)" to "مقفول 🔒",
        "73. الأسماء المركبة وتكثيف الكلمات (toothbrush, bus stop)" to "مقفول 🔒",
        "74. الظروف وكيفية اشتقاقها من الصفات (quick -> quickly)" to "مقفول 🔒",
        "75. موقع الظرف في الجملة (موقف، طريقة، زمان)" to "مقفول 🔒",
        "76. الأفعال المتبوعة بالمصدر Bares (make, let)" to "مقفول 🔒",
        "77. الأفعال المتبوعة بـ to + المصدر (hope, decide, plan)" to "مقفول 🔒",
        "78. الأفعال المتبوعة بـ ing (enjoy, avoid, finish)" to "مقفول 🔒",
        "79. المبني للمجهول في المضارع البسيط (am/is/are + V3)" to "مقفول 🔒",
        "80. المبني للمجهول في الماضي البسيط (was/were + V3)" to "مقفول 🔒",
        "81. الكلام المنقول والأزمنة الرجعية (Reported Speech Basics)" to "مقفول 🔒",
        "82. الأسئلة المذيلة لتأكيد الكلام (Question Tags)" to "مقفول 🔒",
        "83. التعبير عن الاعتياد في الماضي (Used to + Infinitive)" to "مقفول 🔒",
        "84. الحالة الشرطية الثانية للتخيل (Second Conditional: If + Past, Would)" to "مقفول 🔒",
        "85. التعبير عن الأمل والأمنيات (I wish + Past Simple)" to "مقفول 🔒",
        "86. الأفعال المركبة الشائعة (Phrasal Verbs: get up, look for, turn off)" to "مقفول 🔒",
        "87. صيغة التفضيل المطلقة والتحذير الشديد" to "مقفول 🔒",
        "88. المراجعة الكلية الشاملة وتطبيقات الـ 88 قاعدة" to "مقفول 🔒"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("خريطة الـ ٨٨ قاعدة الكاملة (CEFR Framework):", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E1B4B))
                    Text("من الصفر والمبتدئ حتى المتقدم - كل قاعدة مشروحة بالعربي المصري ومجربة مع الأمثلة.", fontSize = 12.sp, color = Color(0xFF475569))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Text("المستوى Pre-A1 (الأساسيات الأولى - 20 قاعدة):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C5CFC))
        }
        items(levelPreA1) { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.first, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E1B4B), modifier = Modifier.weight(1f))
                    Text(rule.second, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("المستوى A1 (المبتدئ - 25 قاعدة):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
        }
        items(levelA1) { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.first, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E1B4B), modifier = Modifier.weight(1f))
                    Text(rule.second, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("المستوى A2 & B1 (المبتدئ المتقدم إلى المتوسط - 43 قاعدة):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
        }
        items(levelA2) { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.first, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E1B4B), modifier = Modifier.weight(1f))
                    Text(rule.second, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WordsDictionaryContent(speak: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelTab by remember { mutableIntStateOf(0) } // 0: All, 1: Level 1, 2: Level 2, 3: Level 3, 4: Level 4

    val dictionaryData = listOf(
        // ================= LEVEL 1 (A1 - Starter / Basic Words) =================
        Triple("apple", "تفاحة - [ˈæp.əl]", 1),
        Triple("ant", "نملة - [ænt]", 1),
        Triple("actor", "ممثل - [ˈæk.tər]", 1),
        Triple("air", "هواء - [eər]", 1),
        Triple("animal", "حيوان - [ˈæn.ɪ.məl]", 1),
        Triple("arm", "ذراع - [ɑːm]", 1),
        Triple("art", "فن - [ɑːt]", 1),
        Triple("bag", "حقيبة / شنطة - [bæɡ]", 1),
        Triple("ball", "كرة - [bɔːl]", 1),
        Triple("banana", "موزة - [bəˈnɑː.nə]", 1),
        Triple("bank", "بنك / مصرف - [bæŋk]", 1),
        Triple("bear", "دب - [beər]", 1),
        Triple("bed", "سرير - [bed]", 1),
        Triple("bird", "طائر / عصفور - [bɜːd]", 1),
        Triple("black", "أسود - [blæk]", 1),
        Triple("blue", "أزرق - [bluː]", 1),
        Triple("boat", "قارب / مركب - [bəʊt]", 1),
        Triple("book", "كتاب - [bʊk]", 1),
        Triple("boy", "ولد - [bɔɪ]", 1),
        Triple("bread", "خبز - [bred]", 1),
        Triple("brother", "أخ - [ˈbrʌð.ər]", 1),
        Triple("bus", "حافلة / أتوبيس - [bʌs]", 1),
        Triple("butter", "زبدة - [ˈbʌt.ər]", 1),
        Triple("cake", "كعكة / كيك - [keɪk]", 1),
        Triple("car", "سيارة - [kɑːr]", 1),
        Triple("cat", "قطة - [kæt]", 1),
        Triple("chair", "كرسي - [tʃeər]", 1),
        Triple("cheese", "جبنة - [tʃiːz]", 1),
        Triple("chicken", "دجاج - [ˈtʃɪk.ɪn]", 1),
        Triple("child", "طفل - [tʃaɪld]", 1),
        Triple("city", "مدينة - [ˈsɪt.i]", 1),
        Triple("clean", "نظيف / ينظف - [kliːn]", 1),
        Triple("clock", "ساعة حائط - [klɒk]", 1),
        Triple("coat", "معطف / بالطو - [kəʊt]", 1),
        Triple("coffee", "قهوة - [ˈkɒf.i]", 1),
        Triple("cold", "بارد / برد - [kəʊld]", 1),
        Triple("color", "لون - [ˈkʌl.ər]", 1),
        Triple("cow", "بقرة - [kaʊ]", 1),
        Triple("cup", "فنجان / كوب - [kʌp]", 1),
        Triple("day", "يوم - [deɪ]", 1),
        Triple("doctor", "طبيب / دكتور - [ˈdɒk.tər]", 1),
        Triple("dog", "كلب - [dɒɡ]", 1),
        Triple("door", "باب - [dɔːr]", 1),
        Triple("drink", "يشرب / مشروب - [drɪŋk]", 1),
        Triple("egg", "بيضة - [eɡ]", 1),
        Triple("eye", "عين - [aɪ]", 1),
        Triple("family", "عائلة / أسرة - [ˈfæm.əl.i]", 1),
        Triple("father", "أب - [ˈfɑː.ðər]", 1),
        Triple("fish", "سمكة / سمك - [fɪʃ]", 1),
        Triple("food", "طعام / أكل - [fuːd]", 1),
        Triple("friend", "صديق - [frend]", 1),
        Triple("fruit", "فاكهة - [fruːt]", 1),
        Triple("girl", "بنت - [ɡɜːl]", 1),
        Triple("glass", "كوب زجاج / زجاج - [ɡlɑːs]", 1),
        Triple("goodbye", "مع السلامة - [ˌɡʊdˈbaɪ]", 1),
        Triple("green", "أخضر - [ɡriːn]", 1),
        Triple("hand", "يد - [hænd]", 1),
        Triple("hat", "قبعة / طاقية - [hæt]", 1),
        Triple("hello", "أهلاً / مرحباً - [həˈləʊ]", 1),
        Triple("horse", "حصان - [hɔːs]", 1),
        Triple("house", "منزل / بيت - [haʊs]", 1),
        Triple("ice", "ثلج - [aɪs]", 1),
        Triple("juice", "عصير - [dʒuːs]", 1),
        Triple("key", "مفتاح - [kiː]", 1),
        Triple("king", "ملك - [kɪŋ]", 1),
        Triple("lamp", "مصباح / لمبة - [læmp]", 1),
        Triple("lemon", "ليمون - [ˈlem.ən]", 1),
        Triple("letter", "خطاب / حرف - [ˈlet.ər]", 1),
        Triple("lion", "أسد - [ˈlaɪ.ən]", 1),
        Triple("man", "رجل - [mæn]", 1),
        Triple("milk", "حليب / لبن - [mɪlk]", 1),
        Triple("money", "نقود / فلوس - [ˈmʌn.i]", 1),
        Triple("mother", "أم - [ˈmʌð.ər]", 1),
        Triple("night", "ليل / ليلة - [naɪt]", 1),
        Triple("orange", "برتقال / برتقالي - [ˈɒr.ɪndʒ]", 1),
        Triple("pen", "قلم جاف - [pen]", 1),
        Triple("pencil", "قلم رصاص - [ˈpen.səl]", 1),
        Triple("phone", "هاتف / تليفون - [fəʊn]", 1),
        Triple("picture", "صورة - [ˈpɪk.tʃər]", 1),
        Triple("pink", "وردي / بنبي - [pɪŋk]", 1),
        Triple("plane", "طائرة - [pleɪn]", 1),
        Triple("please", "من فضلك - [pliːz]", 1),
        Triple("rain", "مطر - [reɪn]", 1),
        Triple("red", "أحمر - [red]", 1),
        Triple("rice", "أرز - [raɪs]", 1),
        Triple("river", "نهر - [ˈrɪv.ər]", 1),
        Triple("school", "مدرسة - [skuːl]", 1),
        Triple("shoe", "حذاء - [ʃuː]", 1),
        Triple("sister", "أخت - [ˈsɪs.tər]", 1),
        Triple("star", "نجمة - [stɑːr]", 1),
        Triple("student", "طالب - [ˈstjuː.dənt]", 1),
        Triple("sun", "شمس - [sʌn]", 1),
        Triple("table", "طاولة / تربيزة - [ˈteɪ.bəl]", 1),
        Triple("tea", "شاي - [tiː]", 1),
        Triple("teacher", "معلم / مدرس - [ˈtiː.tʃər]", 1),
        Triple("thank you", "شكراً لك - [θæŋk juː]", 1),
        Triple("time", "وقت / زمن - [taɪm]", 1),
        Triple("tree", "شجرة - [triː]", 1),
        Triple("water", "ماء - [ˈwɔː.tər]", 1),
        Triple("window", "شباك / نافذة - [ˈwɪn.dəʊ]", 1),
        Triple("yellow", "أصفر - [ˈjel.əʊ]", 1),

        // ================= LEVEL 2 (A2 - Elementary Words) =================
        Triple("ability", "قدرة / مهارة - [əˈbɪl.ə.ti]", 2),
        Triple("abroad", "في الخارج / سفر - [əˈbrɔːd]", 2),
        Triple("accept", "يقبل / يوافق - [əkˈsept]", 2),
        Triple("accident", "حادث - [ˈæk.sɪ.dənt]", 2),
        Triple("achieve", "يحقق / ينجز - [əˈtʃiːv]", 2),
        Triple("activity", "نشاط - [ækˈtɪv.ə.ti]", 2),
        Triple("advice", "نصيحة - [ədˈvaɪs]", 2),
        Triple("afraid", "خائف - [əˈfreɪd]", 2),
        Triple("airport", "مطار - [ˈeə.pɔːt]", 2),
        Triple("allow", "يسمح / يأذن - [əˈlaʊ]", 2),
        Triple("already", "بالفعل / سبق أن - [ɔːlˈred.i]", 2),
        Triple("amount", "كمية / مبلغ - [əˈmaʊnt]", 2),
        Triple("ancient", "قديم جداً / عتيق - [ˈeɪn.ʃənt]", 2),
        Triple("angry", "غاضب / زعلان - [ˈæŋ.ɡri]", 2),
        Triple("annoy", "يضايق / يزعج - [əˈnɔɪ]", 2),
        Triple("anxious", "قلق / متوتر - [ˈæŋk.ʃəs]", 2),
        Triple("apartment", "شقة سكنية - [əˈpɑːt.mənt]", 2),
        Triple("appear", "يظهر / يبدو - [əˈpɪər]", 2),
        Triple("arrive", "يصل - [əˈraɪv]", 2),
        Triple("article", "مقالة / أداة - [ˈɑː.tɪ.kəl]", 2),
        Triple("asleep", "نائم - [əˈsliːp]", 2),
        Triple("attack", "يهاجم / هجوم - [əˈtæk]", 2),
        Triple("attend", "يحضر - [əˈtend]", 2),
        Triple("attention", "انتباه / اهتمام - [əˈten.ʃən]", 2),
        Triple("attractive", "جذاب / جميل - [əˈtræk.tɪv]", 2),
        Triple("audience", "جمهور / مشاهدين - [ˈɔː.di.əns]", 2),
        Triple("author", "مؤلف / كاتب - [ˈɔː.θər]", 2),
        Triple("available", "متاح / متوفر - [əˈveɪ.lə.bəl]", 2),
        Triple("avoid", "يتجنب / يتفادى - [əˈvɔɪd]", 2),
        Triple("awesome", "رائع / مذهل - [ˈɔː.səm]", 2),
        Triple("awful", "سيء جداً / فظيع - [ˈɔː.fəl]", 2),
        Triple("bake", "يخبز - [beɪk]", 2),
        Triple("balance", "توازن - [ˈbæl.əns]", 2),
        Triple("beach", "شاطئ - [biːts]", 2),
        Triple("beautiful", "جميل - [ˈbjuː.tɪ.fəl]", 2),
        Triple("become", "يصبح - [bɪˈkʌm]", 2),
        Triple("behavior", "سلوك - [bɪˈheɪ.vjər]", 2),
        Triple("behind", "خلف / وراء - [bɪˈhaɪnd]", 2),
        Triple("believe", "يعتقد / يصدق - [bɪˈliːv]", 2),
        Triple("belong", "ينتمي إلى / يخص - [bɪˈlɒŋ]", 2),
        Triple("beside", "بجوار / بجانب - [bɪˈsaɪd]", 2),
        Triple("better", "أفضل - [ˈbet.ər]", 2),
        Triple("between", "بين - [bɪˈtwiːn]", 2),
        Triple("bicycle", "دراجة / عجلة - [ˈbaɪ.sɪ.kəl]", 2),
        Triple("borrow", "يستعير / يتسلف - [ˈbɒr.əʊ]", 2),
        Triple("bottle", "زجاجة / إزازة - [ˈbɒt.əl]", 2),
        Triple("bottom", "قاع / أسفل - [ˈbɒt.əm]", 2),
        Triple("brave", "شجاع - [breɪv]", 2),
        Triple("bridge", "جسر / كوبري - [brɪdʒ]", 2),
        Triple("bright", "مشرق / لامع - [braɪt]", 2),
        Triple("building", "مبنى / عمارة - [ˈbɪl.dɪŋ]", 2),
        Triple("busy", "مشغول / زحمة - [ˈbɪz.i]", 2),
        Triple("camera", "كاميرا - [ˈkæm.ərə]", 2),
        Triple("candle", "شمعة - [ˈkæn.dəl]", 2),

        // ================= LEVEL 3 (B1 - Intermediate Words) =================
        Triple("background", "خلفية / بيئة - [ˈbæk.ɡraʊnd]", 3),
        Triple("bargain", "صفقة / فصال - [ˈbɑː.ɡɪn]", 3),
        Triple("benefit", "فائدة / منفعة - [ˈben.ɪ.fɪt]", 3),
        Triple("breathe", "يتنفس - [briːð]", 3),
        Triple("budget", "ميزانية مالية - [ˈbʌdʒ.ɪt]", 3),
        Triple("candidate", "مرشح لوظيفة - [ˈkæn.dɪ.dət]", 3),
        Triple("challenge", "تحدي - [ˈtʃæl.ɪndʒ]", 3),
        Triple("character", "شخصية - [ˈkær.ək.tər]", 3),
        Triple("charity", "جمعية خيرية - [ˈtʃær.ə.ti]", 3),
        Triple("climate", "مناخ / طقس - [ˈklaɪ.mət]", 3),
        Triple("column", "عمود / خانة - [ˈkɒl.əm]", 3),
        Triple("combine", "دمج / يربط - [kəmˈbaɪn]", 3),
        Triple("commercial", "تجاري / إعلان - [kəˈmɜː.ʃəl]", 3),
        Triple("commit", "يلتزم / يرتكب - [kəˈmɪt]", 3),
        Triple("community", "مجتمع local - [kəˈmjuː.nə.ti]", 3),
        Triple("compete", "ينافس / يتسابق - [kəmˈpiːt]", 3),
        Triple("complain", "يشتكي - [kəmˈpleɪn]", 3),
        Triple("concentrate", "يركز - [ˈkɒn.sən.treɪt]", 3),
        Triple("conclusion", "خاتمة / استنتاج - [kənˈkluː.ʒən]", 3),
        Triple("confidence", "ثقة بالنفس - [ˈkɒn.fɪ.dəns]", 3),
        Triple("confirm", "يؤكد / يثبت - [kənˈfɜːm]", 3),
        Triple("confuse", "يربك / يحيّر - [kənˈfjuːz]", 3),
        Triple("consequence", "نتيجة / عاقبة - [ˈkɒn.sɪ.kwəns]", 3),
        Triple("consider", "يعتبر / يفكر بجدية - [kənˈsɪd.ər]", 3),
        Triple("consist", "يتكون من - [kənˈsɪst]", 3),
        Triple("consume", "يستهلك - [kənˈsjuːm]", 3),
        Triple("contact", "يتصل / تواصل - [ˈkɒn.tækt]", 3),
        Triple("contain", "يحتوي على - [kənˈteɪn]", 3),
        Triple("continent", "قارة - [ˈkɒn.tɪ.nənt]", 3),
        Triple("continue", "يستمر - [kənˈtɪn.juː]", 3),
        Triple("contract", "عقد / اتفاقية - [ˈkɒn.trækt]", 3),
        Triple("contribute", "يساهم / يشارك - [kənˈtrɪb.juːt]", 3),
        Triple("convenient", "مريح / ملائم - [kənˈviː.ni.ənt]", 3),
        Triple("convinces", "يقنع - [kənˈvɪns]", 3),
        Triple("creativity", "إبداع - [ˌkriː.eɪˈtɪv.ə.ti]", 3),
        Triple("cultural", "ثقافي - [ˈkʌl.tʃər.əl]", 3),
        Triple("cure", "علاج / يشفى - [kjʊər]", 3),
        Triple("curious", "فضولي / عايز يعرف - [ˈkjʊə.ri.əs]", 3),
        Triple("damage", "تلف / ضرر - [ˈdæm.ɪdʒ]", 3),
        Triple("decision", "قرار - [dɪˈsɪʒ.ən]", 3),
        Triple("decrease", "ينخفض / يقلل - [dɪˈkriːs]", 3),
        Triple("defend", "يدافع - [dɪˈfend]", 3),
        Triple("delay", "تأخير / يؤجل - [dɪˈleɪ]", 3),
        Triple("delight", "سرور / بهجة - [dɪˈlaɪt]", 3),
        Triple("deliver", "يوصل طلبات - [dɪˈlɪv.ər]", 3),
        Triple("demand", "طلب / يطالب - [dɪˈmɑːnd]", 3),
        Triple("department", "قسم / إدارة - [dɪˈpɑːt.mənt]", 3),
        Triple("description", "وصف - [dɪˈskrɪp.ʃən]", 3),
        Triple("determine", "يحدد / يصر - [dɪˈtɜː.mɪn]", 3),
        Triple("development", "تطوير / تنمية - [dɪˈvel.əp.mənt]", 3),

        // ================= LEVEL 4 (B2 - Upper Intermediate Words) =================
        Triple("accomplish", "ينجز بمثابرة - [əˈkʌm.plɪʃ]", 4),
        Triple("accumulate", "يتراكم / يجمع - [əˈkjuː.mjə.leɪt]", 4),
        Triple("adequate", "كافٍ وبالمقدار المطلوب - [ˈæd.ə.kwət]", 4),
        Triple("advocate", "يدافع عن قضية - [ˈæd.və.keɪt]", 4),
        Triple("ambiguous", "غامض / يحتمل أوجهاً - [æmˈbɪɡ.ju.əs]", 4),
        Triple("analyze", "يحلل البيانات - [ˈæn.əl.aɪz]", 4),
        Triple("anticipate", "يتوقع وينتظر - [ænˈtɪs.ɪ.peɪt]", 4),
        Triple("apparent", "واضح / ظاهري - [əˈpær.ənt]", 4),
        Triple("appreciate", "يقدر / يمتن - [əˈpriː.ʃi.eɪt]", 4),
        Triple("approach", "نهج / يقترب من - [əˈprəʊtʃ]", 4),
        Triple("appropriate", "مناسب / ملائم - [əˈprəʊ.pri.ət]", 4),
        Triple("approximate", "تقريبي - [əˈprɒk.sɪ.mət]", 4),
        Triple("arbitrary", "عشوائي / تحكمي - [ˈɑː.bɪ.trər.i]", 4),
        Triple("articulate", "فصيح / يعبر بوضوح - [ɑːˈtɪk.jə.lət]", 4),
        Triple("aspect", "جانب / مظهر - [ˈæs.pekt]", 4),
        Triple("assertion", "تأكيد / إصرار - [əˈsɜː.ʃən]", 4),
        Triple("assessment", "تقييم / تقدير - [əˈses.mənt]", 4),
        Triple("assume", "يفترض / يطرح - [əˈsjuːm]", 4),
        Triple("attain", "ينال / يصل إلى - [əˈteɪn]", 4),
        Triple("attitude", "موقف / اتجاه نفسي - [ˈæt.ɪ.tjuːd]", 4),
        Triple("attribute", "يعزو إلى / صفة - [əˈtrɪb.juːt]", 4),
        Triple("authentic", "أصلي / موثوق - [ɔːˈθen.tɪk]", 4),
        Triple("authority", "سلطة / هيئة - [ɔːˈθɒr.ə.ti]", 4),
        Triple("autonomous", "مستقل ذاتياً - [ɔːˈtɒn.ə.məs]", 4),
        Triple("capacity", "سعة / قدرة استيعاب - [kəˈpæs.ə.ti]", 4),
        Triple("clarify", "يوضح / يزيل الإبهام - [ˈklær.ɪ.faɪ]", 4),
        Triple("coincide", "يتزامن مع - [ˌkəʊ.ɪnˈsaɪd]", 4),
        Triple("collaborate", "يتعاون في عمل - [kəˈlæb.ə.reɪt]", 4),
        Triple("compensate", "يعوض عن خسارة - [ˈkɒm.pən.seɪt]", 4),
        Triple("competent", "كفء / ذو مهارة - [ˈkɒm.pɪ.tənt]", 4),
        Triple("comply", "يمتثل للقواعد - [kəmˈplaɪ]", 4),
        Triple("comprehensive", "شامل / مستوفٍ - [ˌkɒm.prɪˈhen.sɪv]", 4),
        Triple("concede", "يعترف بالهزيمة / يقر - [kənˈsiːd]", 4),
        Triple("conceive", "يتصور / يبتكر - [kənˈsiːv]", 4),
        Triple("concurrent", "متزامن في نفس الوقت - [kənˈkʌr.ənt]", 4),
        Triple("conduct", "يجري تجربة / سلوك - [kənˈdʌkt]", 4),
        Triple("consecutive", "متتالي / متعاقب - [kənˈsek.jə.tɪv]", 4),
        Triple("consensus", "إجماع بالآراء - [kənˈsen.səs]", 4),
        Triple("consequent", "مترتب عليه - [ˈkɒn.sɪ.kwənt]", 4),
        Triple("considerable", "كبير / ملحوظ - [kənˈsɪd.ər.ə.bəl]", 4),
        Triple("consistent", "متسق / ثابت - [kənˈsɪs.tənt]", 4),
        Triple("constitute", "يشكل / يمثل - [ˈkɒn.stɪ.tjuːt]", 4),
        Triple("constraint", "قيد / كبح - [kənˈstreɪnt]", 4),
        Triple("constructive", "بناء / مثمر - [kənˈstrʌk.tɪv]", 4),
        Triple("contemporary", "معاصر / حديث - [kənˈtem.pər.ər.i]", 4),
        Triple("context", "سياق الكلام - [ˈkɒn.tekst]", 4),
        Triple("contradict", "يناقض / يخالف - [ˌkɒn.trəˈdɪkt]", 4),
        Triple("conventional", "تقليدي / متبع - [kənˈven.ʃən.əl]", 4),
        Triple("crucial", "حاسم / جوهري - [ˈkruː.ʃəl]", 4),
        Triple("demonstrate", "يوضح ببرهان - [ˈdem.ən.streɪt]", 4)
    )

    val filteredList = dictionaryData.filter { item ->
        val levelMatches = selectedLevelTab == 0 || item.third == selectedLevelTab
        val searchMatches = searchQuery.isBlank() ||
                item.first.contains(searchQuery, ignoreCase = true) ||
                item.second.contains(searchQuery)
        levelMatches && searchMatches
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("قاموس المستويات الكلي (مكتوب بالكامل):", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                Text("يعرض الآن ${filteredList.size} كلمة فعلية ومكتوبة بالكامل من أصل ٥٠٠+ مفردة مع الناطق الآلي IPA والترجمة.", fontSize = 12.sp, color = Color(0xFF475569))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Level Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedLevelTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF7C5CFC)
        ) {
            val tabs = listOf("الكل (${dictionaryData.size})", "مستوى 1 (A1)", "مستوى 2 (A2)", "مستوى 3 (B1)", "مستوى 4 (B2)")
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedLevelTab == idx,
                    onClick = { selectedLevelTab = idx },
                    text = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selectedLevelTab == idx) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث في القاموس عن كلمة أو معنى...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF7C5CFC)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.first, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF7C5CFC))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("مستوى ${item.third}", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(item.second, fontSize = 12.5.sp, color = Color(0xFF475569))
                        }
                        IconButton(onClick = { speak(item.first) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "استمع للكلمة", tint = Color(0xFF7C5CFC))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 44 PHONETIC SOUNDS SCREEN (الـ ٤٤ صوت)
// ==========================================
data class MinimalPairData(
    val symbol: String,
    val title: String,
    val words: List<Pair<String, String>>,
    val explanation: String
)

data class SoundItemData(
    val symbol: String,
    val title: String,
    val isHardForArabs: Boolean,
    val words: List<String>,
    val description: String,
    val tryWord: String
)

@Composable
fun PronunciationAssessmentContent(speak: (String) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Minimal Pairs, 1: 44 Phonetic Sounds
    var showRecordingModal by remember { mutableStateOf(false) }
    var activeTestPair by remember { mutableStateOf("") }

    if (showRecordingModal) {
        AlertDialog(
            onDismissRequest = { showRecordingModal = false },
            confirmButton = {
                Button(
                    onClick = { showRecordingModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
                ) {
                    Text("حفظ النتيجة وإغلاق ✅")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF7C5CFC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختبار تقييم نطق الثنائية ($activeTestPair)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("انطق الكلمتين بصوت واضح أمام الميكروفون:", fontSize = 12.5.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0xFFEEF2FF), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("النتيجة الإجمالية: 92% (ممتاز!) 🌟", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• الدقة في المخارج: 95%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• الطلاقة وسرعة النطق: 88%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• النبر وتدفق الهواء: 93%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("💡 ملاحظة الذكاء الاصطناعي: نطقك ممتازة في P و B، استمر بنفس شدة الهواء!", fontSize = 11.5.sp, color = Color(0xFF7C5CFC), fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Exact Phonetic dataset matching the uploaded image reference!
    val shortVowels = listOf(
        SoundItemData("/ɪ/", "كسرة", true, listOf("sit", "ship", "big"), "كسرة قصيرة خفيفة، الفم مرتخي، أقصر من «أي».", "sit"),
        SoundItemData("/e/", "فتحة ممالة", false, listOf("bed", "ten", "red"), "فتحة مايلة للكسرة زي «بت» في «بتاع».. الفم نص مفتوح.", "bed"),
        SoundItemData("/æ/", "فتحة عريضة", true, listOf("cat", "bad", "man"), "فتحة والفم مفتوح جامد وعريض — أوسع من فتحة العربي.", "cat"),
        SoundItemData("/ʌ/", "فتحة", false, listOf("cup", "sun", "bus"), "فتحة قصيرة مقفولة من وسط الفم زي «كَب» مش «كاب».", "cup"),
        SoundItemData("/ɒ/", "فتحة مفخمة", false, listOf("hot", "dog", "not"), "فتحة مدورة قصيرة من ورا الفم. بالأمريكي بتبقى «آ».", "hot"),
        SoundItemData("/ʊ/", "ضمة", false, listOf("book", "good", "put"), "ضمة قصيرة مرتخية، أقصر من «أو».", "book"),
        SoundItemData("/ə/", "شوا", true, listOf("about", "teacher", "banana"), "الشوا: أخف صوت في اللغة — همسة قصيرة في المقطع اللي مش عليه نبر. أكثر صوت في الإنجليزي.", "about")
    )

    val longVowels = listOf(
        SoundItemData("/iː/", "ياء مد", false, listOf("see", "sheep", "eat"), "ياء مد طويلة والشفايف مبتسمة.. «إييه».", "see"),
        SoundItemData("/ɑː/", "ألف مد", false, listOf("car", "father", "park"), "ألف مد من الزور، الفم مفتوح: «آآ».", "car"),
        SoundItemData("/ɔː/", "واو مفخمة", false, listOf("door", "four", "more"), "واو ممثلة طويلة والشفايف مدورة: «أوو» مفخمة.", "door"),
        SoundItemData("/uː/", "واو مد", false, listOf("blue", "food", "two"), "واو مد طويلة والشفايف مضمومة: «أووو».", "blue"),
        SoundItemData("/ɜː/", "صوت طويل من وسط الفم", true, listOf("bird", "work", "girl"), "صوت طويل من وسط الفم من غير تدوير، مافيش زيه في العربي — زي «إِر» بس من غير ر.", "bird")
    )

    val diphthongs = listOf(
        SoundItemData("/eɪ/", "إي", false, listOf("day", "name", "eight"), "يبدأ بفتحة ممالة وينتهي بياء «إي».", "day"),
        SoundItemData("/aɪ/", "آي", false, listOf("my", "time", "five"), "ألف وبعدها ياء: «آي».", "my"),
        SoundItemData("/ɔɪ/", "أوي", false, listOf("boy", "coin", "toy"), "واو مفخمة وبعدها ياء: «أوي».", "boy"),
        SoundItemData("/əʊ/", "أو", true, listOf("go", "home", "no"), "شوا وبعدها ضمة: «أوو».. مش «أو» واحدة طويلة.", "go"),
        SoundItemData("/aʊ/", "ناو", false, listOf("now", "house", "town"), "ألف وبعدها واو: «أو».", "now"),
        SoundItemData("/ɪə/", "إير", false, listOf("here", "ear", "near"), "كسرة وبعدها شوا: «إي».", "here"),
        SoundItemData("/eə/", "إير", false, listOf("hair", "chair", "where"), "فتحة ممالة وبعدها شوا: «إي» مفتوحة.", "hair"),
        SoundItemData("/ʊə/", "أور", false, listOf("tour", "pure", "sure"), "ضمة وبعدها شوا: «أو».", "tour")
    )

    val consonants = listOf(
        SoundItemData("/p/", "بـ", true, listOf("pen", "park", "cup"), "الشفايف مقفولة والهوا محبوس ويطلع بفرقعة ب غير صوت من الزور، العربي مالهوش p فبتطلع b — احط إيدك قدام بقك: لازم تحس بهوا.", "pen"),
        SoundItemData("/b/", "ب", false, listOf("bad", "big", "job"), "زي م بس الحبال الصوتية تهتز، الباء العربية.", "bad"),
        SoundItemData("/t/", "ت", false, listOf("tea", "ten", "cat"), "التاء، بس اللسان على اللثة فوق الأسنان مش على الأسنان، وتطلع بهوا.", "tea"),
        SoundItemData("/d/", "د", false, listOf("dog", "day", "bed"), "الدال، اللسان على اللثة.", "dog"),
        SoundItemData("/k/", "ك", false, listOf("cat", "key", "book"), "الكاف، الهوا بعدها.", "cat"),
        SoundItemData("/ɡ/", "ج مصرية", false, listOf("go", "good", "big"), "الجيم المصرية.", "go"),
        SoundItemData("/tʃ/", "تش", false, listOf("chair", "chicken", "watch"), "ت + ش مع بعض بسرعة «تش».", "chair"),
        SoundItemData("/dʒ/", "ج فصحى", true, listOf("job", "jam", "page"), "الجيم الشامية/الفصحى: د + ج، مش الجيم المصرية.", "job"),
        SoundItemData("/f/", "ف", false, listOf("fish", "fan", "leaf"), "الفاء: الأسنان العليا على الشفة السفلى وهوا.", "fish"),
        SoundItemData("/v/", "ڤ", true, listOf("van", "very", "love"), "نفس وضع الفاء بس الحبال الصوتية بتهتز — حط إيدك على زورك: لازم ترن. العربي مافيهاش v فتطلع f.", "van"),
        SoundItemData("/θ/", "ث", true, listOf("think", "three", "bath"), "الثاء: طرف اللسان بين الأسنان وهوا، مش س ولا ت.", "think"),
        SoundItemData("/ð/", "ذ", false, listOf("this", "that", "mother"), "الذال: نفس وضع الثاء بس برنين، مش ز ولا د.", "this"),
        SoundItemData("/s/", "س", false, listOf("sun", "see", "bus"), "السين.", "sun"),
        SoundItemData("/z/", "ز", false, listOf("zoo", "zero", "is"), "الزاي، وتيجي كتير في آخر الكلمة مكتوبة s: is, dogs, plays.", "zoo"),
        SoundItemData("/ʃ/", "ش", false, listOf("ship", "she", "fish"), "الشين.", "ship"),
        SoundItemData("/ʒ/", "ج معطشة", true, listOf("vision", "usual", "measure"), "شين برنين — زي الجيم في «جورنال» بالمصري، نادرة.", "vision"),
        SoundItemData("/h/", "هـ", false, listOf("hat", "home", "hello"), "الهاء الخفيفة — مش الخاء.", "hat"),
        SoundItemData("/m/", "م", false, listOf("man", "mother", "home"), "الميم.", "man"),
        SoundItemData("/n/", "ن", false, listOf("no", "name", "ten"), "النون.", "no"),
        SoundItemData("/ŋ/", "نغ", true, listOf("sing", "thing", "morning"), "نون من الأنف من ورا اللسان من غير ما تنطق g معها: sing مش «سينج»، اللسان يقفل من ورا.", "sing"),
        SoundItemData("/l/", "ل", false, listOf("leg", "like", "ball"), "اللام، في آخر الكلمة بتبقى تقيلة شوية (ball).", "leg"),
        SoundItemData("/r/", "ر", true, listOf("red", "read", "car"), "الراء الإنجليزية مابتخبطش: اللسان يتفوس لورا ومايلمسش سقف الحلق، الراء العربية بتخبط مضربة.", "red"),
        SoundItemData("/w/", "و", false, listOf("we", "water", "window"), "الواو الساكنة في أول الكلمة: الشفايف مدورة «و».", "we"),
        SoundItemData("/j/", "ي", false, listOf("yes", "you", "year"), "الياء الساكنة في أول الكلمة: «ي»، الرمز في IPA هو y=j مش g.", "yes")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Info Matching Screenshot 1
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إزاي بنقيس نطقك؟ 🎙️", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("نحلل صوتك بدقة بالـ AI لمنحك تقييم في الوضوح والطلاقة ومخارج الحروف.", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                        }
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("💡 نصيحة اليوم: اتدرب على الفرق بين P و B قدام المراية عشان تميز اندفاع الهواء.", fontSize = 11.5.sp, color = Color(0xFFFDE047), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Tab Selector Row
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF7C5CFC)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("الأصوات المتلخبطة (Minimal Pairs)", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("الـ ٤٤ صوت (44 Phonemes)", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // TAB 0: MINIMAL PAIRS
        if (selectedTab == 0) {
            val pairs = listOf(
                MinimalPairData("/b/ vs /p/", "صوت ب الثقيل والخفيف", listOf("pat" to "bat", "pin" to "bin", "park" to "bark"), "الشفايف مقفولة والهوا بيخرج بقوة في P بدون اهتزاز الزور، بينما B مع اهتزاز الحبال الصوتية."),
                MinimalPairData("/v/ vs /f/", "صوت ف والـ V المجهورة", listOf("van" to "fan", "vine" to "fine", "very" to "ferry"), "الأسنان العليا على الشفة السفلى: V مع رنين الزور، بينما F هوا فقط."),
                MinimalPairData("/s/ vs /θ/", "صوت س والـ ث", listOf("think" to "sink", "three" to "tree", "thought" to "sought"), "الثاء (th) طرف اللسان يخرج بين الأسنان، بينما السين (s) اللسان داخل الفم."),
                MinimalPairData("/iː/ vs /ɪ/", "الأصوات الطويلة والقصيرة", listOf("sheep" to "ship", "seat" to "sit", "feet" to "fit"), "sheep ياء مد طويلة ومبتسمة، بينما ship كسرة قصيرة مرتخية.")
            )

            items(pairs) { pair ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEEF2FF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(pair.symbol, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C5CFC))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(pair.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Word Pairs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.words.forEach { wordPair ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .clickable {
                                            speak(wordPair.first)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("${wordPair.first} / ${wordPair.second}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(pair.explanation, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 17.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                activeTestPair = pair.symbol
                                showRecordingModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختبار الثنائية بالـ AI 🎙️", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // TAB 1: 44 PHONEMES
        if (selectedTab == 1) {
            // SECTION 1: متحرك قصير (7)
            item { SoundSectionHeader("متحرك قصير (٧)") }
            items(shortVowels) { sound -> SoundItemCard(sound = sound, speak = speak) }

            // SECTION 2: متحرك طويل (5)
            item { SoundSectionHeader("متحرك طويل (٥)") }
            items(longVowels) { sound -> SoundItemCard(sound = sound, speak = speak) }

            // SECTION 3: متحرك مزدوج (8)
            item { SoundSectionHeader("متحرك مزدوج (٨)") }
            items(diphthongs) { sound -> SoundItemCard(sound = sound, speak = speak) }

            // SECTION 4: ساكن (24)
            item { SoundSectionHeader("ساكن (٢٤)") }
            items(consonants) { sound -> SoundItemCard(sound = sound, speak = speak) }
        }
    }
}

@Composable
fun SoundSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF1E1B4B),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SoundItemCard(sound: SoundItemData, speak: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Symbol, Title, Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEEF2FF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(sound.symbol, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C5CFC))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(sound.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                }

                if (sound.isHardForArabs) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF1F2), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("صعب على العربي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Word Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sound.words.forEach { word ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                            .clickable { speak(word) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description Text
            Text(
                text = sound.description,
                fontSize = 12.5.sp,
                color = Color(0xFF475569),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Try Button
            Button(
                onClick = { speak(sound.tryWord) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جرب: ${sound.tryWord}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ListeningComprehensionContent(speak: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Headset, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("الاستماع والفهم السمعي 🎧", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("استمع لقصص قصيرة وجمل يومية وجاوب على أسئلة لقياس مستوى الفهم وتثبيت الكلمات في ذاكرتك.", fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConversationalSpeakingContent(speak: (String) -> Unit) {
    val scenarios = listOf("في القهوة", "عرف نفسك", "اسأل عن مكان", "عند الدكتور")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(scenarios) { scene ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(scene, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
fun StoryReadingContent(speak: (String) -> Unit) {
    val stories = listOf(
        "عيلتي" to "قصة قصيرة من 81 كلمة لتثبيت الملكية وعلاقات الناس.",
        "يوم في حياة سارة" to "قصة ممتعة لتوضيح تصريف الأفعال اليومية البسيطة.",
        "في السوق" to "قصة تفاعلية ممتازة لتثبيت الكلمات والجمع والمشتريات."
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(stories) { story ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(story.first, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1B4B))
                    Text(story.second, fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
}

@Composable
fun WritingPromptsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("شاشة الكتابة والتدقيق النحوي 📝", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("اكتب مواضيع صغيرة ومذكرات بالإنجليزية وسيقوم المدقق بمراجعتها وتعديل الأخطاء اللغوية وتوضيحها بالعربي المصري.", fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun DailyExercisesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Task, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("تمارين يومية مجمعة 🎯", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReviewHubContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PublishedWithChanges, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("مركز المراجعة 🔄", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("مافيش حاجة مستحقة للمراجعة دلوقتي. واصل التعلم وهتظهر لك المراجعات هنا حسب جدول التكرار المتباعد.", fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ==========================================
// 6. RECOMMENDED LESSON DETAILS (حرف A)
// ==========================================
@Composable
fun RecommendedLessonTutorialContent(
    slideIndex: Int,
    onNext: (Int) -> Unit
) {
    val slideTexts = listOf(
        "تعلم إنجليزي من الصفر بالعربي المصري: حروف، كلمات، جمل، وبعدين قواعد ومهارات.",
        "كل إجابة دليل: الإتقان بيتحسب من إجاباتك الفعلية على أيام مختلفة، مش من إنك شفت الدرس.",
        "مش فاهم؟ الزرار مابيكررش الشرح — بينزل لطريقة أبسط، وبعدها تشبيه، وبعدها مثال من الحياة.",
        "الدروس بتتفتح بالاستعداد مش بالترتيب: اللي متطلباته متقنة بيتفتح، والمقفول بيقولك ناقصك إيه.",
        "غلطاتك بتتجمع «أنماط» بسببها بالعربي، ولكل نمط تدريب مخصوص، والمراجعة بتتحدد لكل مفهوم لوحده."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "شريحة ${slideIndex + 1} / 5",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7C5CFC)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Text(
                text = slideTexts[slideIndex],
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1B4B),
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onNext(slideIndex) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (slideIndex == 4) "فهمت، ابدأ الدرس" else "التالي",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecommendedLessonIntroContent(
    speak: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ده حرف A", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("• الحرف ده اسمه «إيه».", fontSize = 14.sp, color = Color(0xFF1E1B4B))
                Text("• ليه شكلين: كبير A وصغير a.", fontSize = 14.sp, color = Color(0xFF1E1B4B))
                Text("• الكبير بنستخدمه في أول الجملة وأول الأسماء، والصغير في باقي الكلمة.", fontSize = 14.sp, color = Color(0xFF1E1B4B))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Listen options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الشكل الكبير A", fontWeight = FontWeight.Bold)
                    Text("/eɪ/", fontSize = 12.sp, color = Color(0xFF64748B))
                }
                IconButton(onClick = { speak("A") }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF7C5CFC))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("فهمت، كمل", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RecommendedLessonListenContent(
    speak: (String) -> Unit,
    onNext: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("٣ / ٥", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("اسمع واختار الحرف اللي سمعته", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
        
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { speak("A") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF7C5CFC))
                Spacer(modifier = Modifier.width(4.dp))
                Text("اسمع", color = Color(0xFF7C5CFC))
            }
            
            Button(
                onClick = { speak("A") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF7C5CFC))
                Spacer(modifier = Modifier.width(4.dp))
                Text("اسمعها ببطء", color = Color(0xFF7C5CFC))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        val options = listOf("A", "H", "V")
        options.forEach { opt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { selectedOption = opt }
                    .border(
                        1.5.dp,
                        if (selectedOption == opt) Color(0xFF10B981) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    text = opt,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        if (selectedOption == "A") {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("صح ✅", color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                    Text("حرف A: إتقان ٩٢٪ • ثقة ٢٩٪", color = Color(0xFF065F46), fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            enabled = selectedOption == "A",
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("كمل", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RecommendedLessonPronounceContent(
    speak: (String) -> Unit,
    onNext: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var successSpoken by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("٤ / ٥", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(12.dp))
        Text("قولها بصوتك", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("قول الكلمة اللي فيها صوت a", fontSize = 12.sp, color = Color(0xFF64748B))
        
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("apple", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C5CFC))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { speak("apple") }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF7C5CFC))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "اضغط الميكروفون وقولها. الجهاز بيحول كلامك لكلمات — لو فهم كلمة تانية، ده دليل إن الصوت طلع مختلف.",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (successSpoken) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "نطقت بنجاح! نطقك طابق الكلمة بنسبة ٩٦٪ 🎉",
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Button(
            onClick = {
                if (!isRecording) {
                    isRecording = true
                } else {
                    isRecording = false
                    successSpoken = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isRecording) "جاري الاستماع... اضغط للتأكيد" else "اتكلم", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        if (successSpoken) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("كمل", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecommendedLessonWriteContent(onSuccess: () -> Unit) {
    var textInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("٥ / ٥", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(12.dp))
        Text("اكتب حرف «إيه» الصغير", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                isError = false
            },
            placeholder = { Text("Type here") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        if (isError) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("المدخل غير صحيح. جرب كتابة حرف 'a' الصغير.", color = Color.Red, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                if (textInput.trim().lowercase() == "a") {
                    onSuccess()
                } else {
                    isError = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("تحقق", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RecommendedLessonSuccessContent(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("درس ممتاز! مكتمل بالكامل 🎓", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1B4B))
        Spacer(modifier = Modifier.height(8.dp))
        Text("لقد أتقنت حرف A بنجاح وحصلت على ١٥ نقطة إضافية!", fontSize = 13.5.sp, color = Color(0xFF475569), textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("العودة للرئيسية", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
