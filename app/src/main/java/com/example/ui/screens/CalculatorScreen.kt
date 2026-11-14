package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.CalculatorEngine
import com.example.domain.calculator.CalculatorHistoryItem
import com.example.domain.calculator.CalculatorHistoryStore
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

enum class CalcMode(val arTitle: String, val enTitle: String, val modeNum: Int) {
    COMP("حاسبة fx-991", "1: COMP", 1),
    CMPLX("الأعداد المركبة", "2: CMPLX", 2),
    STAT("الإحصاء", "3: STAT", 3),
    BASE_N("الأنظمة BASE-N", "4: BASE-N", 4),
    EQN("المعادلات والتفاضل", "5: EQN", 5),
    MATRIX("المصفوفات", "6: MATRIX", 6),
    TABLE("جدول الدوال", "7: TABLE", 7),
    VECTOR("المتجهات", "8: VECTOR", 8)
}

@Composable
fun CalculatorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var currentCalcMode by remember { mutableStateOf(CalcMode.COMP) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Show Mode Selector Header only in secondary modes
        if (currentCalcMode != CalcMode.COMP) {
            Surface(
                color = Color(0xFF1E242D),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentCalcMode = CalcMode.COMP }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to Casio Calculator",
                            tint = Color.White
                        )
                    }
                    ScrollableTabRow(
                        selectedTabIndex = currentCalcMode.ordinal,
                        edgePadding = 4.dp,
                        containerColor = Color(0xFF1E242D),
                        divider = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        CalcMode.values().forEach { mode ->
                            val icon = when (mode) {
                                CalcMode.COMP -> Icons.Default.Calculate
                                CalcMode.CMPLX -> Icons.Default.Hub
                                CalcMode.STAT -> Icons.Default.BarChart
                                CalcMode.BASE_N -> Icons.Default.Code
                                CalcMode.EQN -> Icons.Default.Functions
                                CalcMode.MATRIX -> Icons.Default.GridView
                                CalcMode.TABLE -> Icons.Default.FormatListNumbered
                                CalcMode.VECTOR -> Icons.Default.Navigation
                            }

                            Tab(
                                selected = currentCalcMode == mode,
                                onClick = { currentCalcMode = mode },
                                text = { Text(if (isEnglish) mode.enTitle else mode.arTitle, fontSize = 11.5.sp, fontWeight = if (currentCalcMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = currentCalcMode,
            label = "CalcModeTransition",
            modifier = Modifier.weight(1f)
        ) { mode ->
            when (mode) {
                CalcMode.COMP -> ScientificCalculatorView(
                    viewModel = viewModel,
                    isEnglish = isEnglish,
                    onSelectMode = { currentCalcMode = it },
                    onNavigateBack = onNavigateBack
                )
                CalcMode.CMPLX -> ComplexCalculatorView(isEnglish = isEnglish)
                CalcMode.STAT -> StatsCalculatorView(isEnglish = isEnglish)
                CalcMode.BASE_N -> BaseNCalculatorView(isEnglish = isEnglish)
                CalcMode.EQN -> EquationCalculatorView(isEnglish = isEnglish)
                CalcMode.MATRIX -> MatrixCalculatorView(isEnglish = isEnglish)
                CalcMode.TABLE -> TableCalculatorView(isEnglish = isEnglish)
                CalcMode.VECTOR -> VectorCalculatorView(isEnglish = isEnglish)
            }
        }
    }
}

// ==========================================
// 1. SCIENTIFIC CALCULATOR VIEW (Casio fx-991EX ClassWiz / CalcES Full Interactive Simulator)
// ==========================================
class CasioJsInterface(
    private val context: Context,
    private val onBack: () -> Unit
) {
    @JavascriptInterface
    fun vibrate() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun goBack() {
        onBack()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScientificCalculatorView(
    viewModel: MainViewModel,
    isEnglish: Boolean,
    onSelectMode: (CalcMode) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var showModeDialog by remember { mutableStateOf(false) }
    var showAiExplanationDialog by remember { mutableStateOf(false) }
    var aiExplanationText by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }

    fun solveWithAi(expressionToSolve: String = "7 + 6 = 4x") {
        showAiExplanationDialog = true
        isAiLoading = true
        aiExplanationText = ""

        coroutineScope.launch {
            val prompt = "يرجى حل المسألة الرياضية أو المعادلة التالية مع شرح الخطوات بالتفصيل والتعليل العلمي:\n$expressionToSolve"
            val sysInst = "أنت خبير ومعلم الرياضيات والفيزياء الذكي. قم بشرح خطوات حل المسألة الرياضية باللغة العربية بوضوح ودقة عالية."

            val res = viewModel.aiRepository.generateAiResponse(
                prompt = prompt,
                systemInstruction = sysInst,
                modelName = "gemini-1.5-flash"
            )

            isAiLoading = false
            if (res.isSuccess) {
                aiExplanationText = res.getOrDefault("")
            } else {
                val err = res.exceptionOrNull()
                aiExplanationText = if (isEnglish) "Failed to get AI steps: ${err?.message}" else "حدث خطأ أثناء التواصل مع الذكاء الاصطناعي: ${err?.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        // Top Bar: Back Button, Developer Avatar ("ع"), CASIO fx-991EX branding, AI Solver & Modes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101422))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Developer Avatar & Branding at the very top
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ع",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CASIO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            color = Color(0xFFF1F5F9),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "fx-991EX ClassWiz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    Text(
                        text = if (isEnglish) "Dev: Abd El-Rahman Safwat • CalcES Simulator" else "تطوير: عبد الرحمن صفوت عطا • محاكي كاسيو التفاعلي",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Quick Actions: AI Explainer & Modes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = { solveWithAi() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF38BDF8)
                    )
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("AI 🧠", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { showModeDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF28324D),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isEnglish) "MODE" else "الأوضاع", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Full Interactive Offline WebView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0A0D14"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(CasioJsInterface(ctx, onNavigateBack), "Android")
                    loadUrl("file:///android_asset/casio_simulator.html")
                }
            }
        )
    }

    // AI Step-by-Step Dialog
    if (showAiExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showAiExplanationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "AI Step-by-Step Solution" else "شرح خطوات الحل بالذكاء الاصطناعي")
                }
            },
            text = {
                if (isAiLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(if (isEnglish) "Analyzing expression..." else "جاري تحليل ورسم خطوات المسألة...")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        item {
                            Text(
                                text = aiExplanationText,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiExplanationDialog = false }) {
                    Text(if (isEnglish) "Close" else "إغلاق")
                }
            }
        )
    }

    // Mode Setup Dialog
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnglish) "CASIO fx-991 Mode Setup" else "قائمة أوضاع الآلة الحاسبة (Casio Mode)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcMode.values().forEach { mode ->
                        Surface(
                            onClick = {
                                onSelectMode(mode)
                                showModeDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) mode.enTitle else "${mode.modeNum}: ${mode.arTitle}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text(if (isEnglish) "Close" else "إغلاق")
                }
            }
        )
    }
}


@Composable
fun CasioKeyButton(
    key: String,
    shiftLabel: String? = null,
    alphaLabel: String? = null,
    isShiftActive: Boolean = false,
    isAlphaActive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isShiftKey = key == "SHIFT"
    val isAlphaKey = key == "ALPHA"
    val isActionKey = key in listOf("AC", "DEL")
    val isEqualsKey = key == "="
    val isOperator = key in listOf("+", "-", "×", "÷")
    val isNumber = key in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "Ans", "×10^x")
    val isVariableX = key == "X"

    // Strict CASIO fx-991ES PLUS Color Coding:
    // DEL & AC: Distinct Lime Green / Mint Green
    // Scientific Keys: Dark silver / charcoal grey
    // Numbers: Dark slate / light charcoal
    // Equals: Bold Royal Blue
    val containerColor = when {
        isShiftKey -> Color(0xFFEAB308) // Casio Shift Amber Gold
        isAlphaKey -> Color(0xFFF43F5E) // Casio Alpha Magenta / Rose Pink
        isActionKey -> Color(0xFF059669) // Distinct Mint / Lime Green for DEL & AC
        isEqualsKey -> Color(0xFF2563EB) // Electric Royal Blue
        isOperator -> Color(0xFF1E3A8A)  // Deep Indigo / Navy Operator
        isVariableX -> Color(0xFF991B1B) // Deep Red for Variable X
        isNumber -> Color(0xFF1E293B)    // Dark Slate Number Keys
        key in listOf("ON", "MODE") -> Color(0xFF334155) // Control Slate
        else -> Color(0xFF334155)        // Charcoal Grey / Dark Silver for Scientific Keys
    }

    val textColor = when {
        isShiftKey -> Color.Black
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        tonalElevation = 2.dp,
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Secondary Labels (Shift = Amber, Alpha = Pink)
            if (shiftLabel != null || alphaLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = shiftLabel ?: "",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShiftActive) Color(0xFFFACC15) else Color(0xFFEAB308).copy(alpha = 0.9f),
                        maxLines = 1
                    )
                    Text(
                        text = alphaLabel ?: "",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlphaActive) Color(0xFFFB7185) else Color(0xFFF43F5E).copy(alpha = 0.9f),
                        maxLines = 1
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }

            // Main key label
            Text(
                text = key,
                fontSize = when {
                    key.length >= 4 -> 8.5.sp
                    key.length == 3 -> 10.sp
                    key in listOf("DEL", "AC") -> 11.5.sp
                    else -> 12.5.sp
                },
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

// ==========================================
// 2. MATRIX CALCULATOR VIEW
// ==========================================
@Composable
fun MatrixCalculatorView(isEnglish: Boolean) {
    var matrixSize by remember { mutableIntStateOf(2) }
    var a11 by remember { mutableStateOf("1") }
    var a12 by remember { mutableStateOf("2") }
    var a13 by remember { mutableStateOf("0") }
    var a21 by remember { mutableStateOf("3") }
    var a22 by remember { mutableStateOf("4") }
    var a23 by remember { mutableStateOf("0") }
    var a31 by remember { mutableStateOf("0") }
    var a32 by remember { mutableStateOf("0") }
    var a33 by remember { mutableStateOf("1") }

    var matrixResultText by remember { mutableStateOf("") }

    fun calculateDeterminant() {
        try {
            if (matrixSize == 2) {
                val det = (a11.toDouble() * a22.toDouble()) - (a12.toDouble() * a21.toDouble())
                matrixResultText = if (isEnglish) "Determinant det(A) = $det" else "محدد المصروفة det(A) = $det"
            } else {
                val m11 = a11.toDouble(); val m12 = a12.toDouble(); val m13 = a13.toDouble()
                val m21 = a21.toDouble(); val m22 = a22.toDouble(); val m23 = a23.toDouble()
                val m31 = a31.toDouble(); val m32 = a32.toDouble(); val m33 = a33.toDouble()

                val det = m11 * (m22 * m33 - m23 * m32) -
                          m12 * (m21 * m33 - m23 * m31) +
                          m13 * (m21 * m32 - m22 * m31)
                matrixResultText = if (isEnglish) "Determinant det(A) = $det" else "محدد المصروفة det(A) = $det"
            }
        } catch (e: Exception) {
            matrixResultText = if (isEnglish) "Error in matrix values" else "خطأ في قيم إدخال المصفوفة"
        }
    }

    fun calculateInverse() {
        try {
            if (matrixSize == 2) {
                val m11 = a11.toDouble(); val m12 = a12.toDouble()
                val m21 = a21.toDouble(); val m22 = a22.toDouble()
                val det = (m11 * m22) - (m12 * m21)
                if (det == 0.0) {
                    matrixResultText = if (isEnglish) "Matrix has no inverse (det = 0)" else "المصفوفة ليس لها معكوس (المحدد = 0)"
                } else {
                    val inv11 = formatResultNumber(m22 / det)
                    val inv12 = formatResultNumber(-m12 / det)
                    val inv21 = formatResultNumber(-m21 / det)
                    val inv22 = formatResultNumber(m11 / det)
                    matrixResultText = "A⁻¹ =\n[ $inv11 , $inv12 ]\n[ $inv21 , $inv22 ]"
                }
            } else {
                matrixResultText = if (isEnglish) "Inverse calculated for 2x2 matrix" else "حساب المعكوس متاح للمصفوفة 2x2"
            }
        } catch (e: Exception) {
            matrixResultText = if (isEnglish) "Error calculating inverse" else "خطأ أثناء حساب المعكوس"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Matrix Operations (A)" else "حسابات المصفوفات (Matrix A)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = matrixSize == 2, onClick = { matrixSize = 2 }, label = { Text("2 x 2") })
                    FilterChip(selected = matrixSize == 3, onClick = { matrixSize = 3 }, label = { Text("3 x 3") })
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = a11, onValueChange = { a11 = it }, label = { Text("a11") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = a12, onValueChange = { a12 = it }, label = { Text("a12") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (matrixSize == 3) {
                            OutlinedTextField(value = a13, onValueChange = { a13 = it }, label = { Text("a13") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = a21, onValueChange = { a21 = it }, label = { Text("a21") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = a22, onValueChange = { a22 = it }, label = { Text("a22") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (matrixSize == 3) {
                            OutlinedTextField(value = a23, onValueChange = { a23 = it }, label = { Text("a23") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }

                    if (matrixSize == 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = a31, onValueChange = { a31 = it }, label = { Text("a31") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = a32, onValueChange = { a32 = it }, label = { Text("a32") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = a33, onValueChange = { a33 = it }, label = { Text("a33") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = { calculateDeterminant() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(if (isEnglish) "Determinant det(A)" else "المحدد det")
                }
                Button(onClick = { calculateInverse() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(if (isEnglish) "Inverse A⁻¹" else "المعكوس A⁻¹")
                }
            }
        }

        if (matrixResultText.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (isEnglish) "Result:" else "النتيجة:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = matrixResultText, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. VECTOR CALCULATOR VIEW
// ==========================================
@Composable
fun VectorCalculatorView(isEnglish: Boolean) {
    var v1x by remember { mutableStateOf("1") }
    var v1y by remember { mutableStateOf("2") }
    var v1z by remember { mutableStateOf("3") }

    var v2x by remember { mutableStateOf("4") }
    var v2y by remember { mutableStateOf("5") }
    var v2z by remember { mutableStateOf("6") }

    var vectorResultText by remember { mutableStateOf("") }

    fun calculateDotProduct() {
        try {
            val x1 = v1x.toDouble(); val y1 = v1y.toDouble(); val z1 = v1z.toDouble()
            val x2 = v2x.toDouble(); val y2 = v2y.toDouble(); val z2 = v2z.toDouble()

            val dot = (x1 * x2) + (y1 * y2) + (z1 * z2)
            vectorResultText = if (isEnglish) "Dot Product A · B = $dot" else "الضرب القياسي (الداخلي) A · B = $dot"
        } catch (e: Exception) {
            vectorResultText = if (isEnglish) "Invalid vector values" else "خطأ في قيم المتجهات"
        }
    }

    fun calculateCrossProduct() {
        try {
            val x1 = v1x.toDouble(); val y1 = v1y.toDouble(); val z1 = v1z.toDouble()
            val x2 = v2x.toDouble(); val y2 = v2y.toDouble(); val z2 = v2z.toDouble()

            val cx = (y1 * z2) - (z1 * y2)
            val cy = (z1 * x2) - (x1 * z2)
            val cz = (x1 * y2) - (y1 * x2)

            vectorResultText = if (isEnglish) "Cross Product A × B = ($cx, $cy, $cz)" else "الضرب الاتجاهي A × B = ($cx i, $cy j, $cz k)"
        } catch (e: Exception) {
            vectorResultText = if (isEnglish) "Invalid vector values" else "خطأ في قيم المتجهات"
        }
    }

    fun calculateMagnitudes() {
        try {
            val x1 = v1x.toDouble(); val y1 = v1y.toDouble(); val z1 = v1z.toDouble()
            val x2 = v2x.toDouble(); val y2 = v2y.toDouble(); val z2 = v2z.toDouble()

            val mag1 = sqrt(x1 * x1 + y1 * y1 + z1 * z1)
            val mag2 = sqrt(x2 * x2 + y2 * y2 + z2 * z2)

            vectorResultText = "||A|| = ${formatResultNumber(mag1)}\n||B|| = ${formatResultNumber(mag2)}"
        } catch (e: Exception) {
            vectorResultText = if (isEnglish) "Invalid vector values" else "خطأ في قيم المتجهات"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = if (isEnglish) "3D Vector Calculator" else "حاسبة المتجهات ثلاثية الأبعاد (3D Vectors)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Vector A (x, y, z)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = v1x, onValueChange = { v1x = it }, label = { Text("Ax") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = v1y, onValueChange = { v1y = it }, label = { Text("Ay") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = v1z, onValueChange = { v1z = it }, label = { Text("Az") }, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Vector B (x, y, z)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = v2x, onValueChange = { v2x = it }, label = { Text("Bx") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = v2y, onValueChange = { v2y = it }, label = { Text("By") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = v2z, onValueChange = { v2z = it }, label = { Text("Bz") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { calculateDotProduct() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("الضرب القياسي A·B")
                    }
                    Button(onClick = { calculateCrossProduct() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("الضرب الاتجاهي A×B")
                    }
                }
                Button(onClick = { calculateMagnitudes() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text("معيار المتجهات ||A|| , ||B||")
                }
            }
        }

        if (vectorResultText.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "نتيجة المتجهات:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = vectorResultText, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. EQUATIONS & CALCULUS CALCULATOR VIEW
// ==========================================
@Composable
fun EquationCalculatorView(isEnglish: Boolean) {
    var coeffA by remember { mutableStateOf("1") }
    var coeffB by remember { mutableStateOf("-5") }
    var coeffC by remember { mutableStateOf("6") }
    var quadResult by remember { mutableStateOf("") }

    var derivExpr by remember { mutableStateOf("x^2 + 3*x") }
    var xPoint by remember { mutableStateOf("2") }
    var derivResult by remember { mutableStateOf("") }

    fun solveQuadratic() {
        try {
            val a = coeffA.toDouble()
            val b = coeffB.toDouble()
            val c = coeffC.toDouble()

            if (a == 0.0) {
                quadResult = if (isEnglish) "Not a quadratic equation (a = 0)" else "ليست معادلة تربيعية (a = 0)"
                return
            }

            val discriminant = b * b - 4 * a * c
            if (discriminant > 0) {
                val x1 = (-b + sqrt(discriminant)) / (2 * a)
                val x2 = (-b - sqrt(discriminant)) / (2 * a)
                quadResult = "x₁ = ${formatResultNumber(x1)}\nx₂ = ${formatResultNumber(x2)}"
            } else if (discriminant == 0.0) {
                val x = -b / (2 * a)
                quadResult = "x = ${formatResultNumber(x)}"
            } else {
                val real = -b / (2 * a)
                val imag = sqrt(-discriminant) / (2 * a)
                quadResult = "x₁ = ${formatResultNumber(real)} + ${formatResultNumber(imag)}i\nx₂ = ${formatResultNumber(real)} - ${formatResultNumber(imag)}i"
            }
        } catch (e: Exception) {
            quadResult = if (isEnglish) "Invalid coefficients" else "خطأ في المعاملات"
        }
    }

    fun solveDerivative() {
        try {
            val x = xPoint.toDouble()
            val h = 0.00001
            val fxPlus = evaluateMathExpression(derivExpr.replace("x", "(${x + h})"), true, "0")
            val fxMinus = evaluateMathExpression(derivExpr.replace("x", "(${x - h})"), true, "0")
            val slope = (fxPlus - fxMinus) / (2 * h)
            derivResult = "d/dx [$derivExpr] at x = $x\n= ${formatResultNumber(slope)}"
        } catch (e: Exception) {
            derivResult = if (isEnglish) "Error in derivative expression" else "خطأ في تعبير التفاضل"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Quadratic Solver (ax² + bx + c = 0)" else "حل المعادلة التربيعية (ax² + bx + c = 0)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = coeffA, onValueChange = { coeffA = it }, label = { Text("a") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = coeffB, onValueChange = { coeffB = it }, label = { Text("b") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = coeffC, onValueChange = { coeffC = it }, label = { Text("c") }, modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = { solveQuadratic() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Find Roots x₁, x₂" else "إيجاد جذور المعادلة x₁, x₂")
                    }

                    if (quadResult.isNotBlank()) {
                        Text(
                            text = quadResult,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Numerical Derivative d/dx at x = x₀" else "حساب التفاضل العددي d/dx عند نقطة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = derivExpr,
                        onValueChange = { derivExpr = it },
                        label = { Text(if (isEnglish) "Function f(x) e.g., x^2 + 3*x" else "الدالة f(x) مثل: x^2 + 3*x") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = xPoint,
                        onValueChange = { xPoint = it },
                        label = { Text(if (isEnglish) "Value of x₀" else "قيمة النقطة x₀") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { solveDerivative() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Calculate Derivative d/dx" else "احسب الميل / التفاضل d/dx")
                    }

                    if (derivResult.isNotBlank()) {
                        Text(
                            text = derivResult,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. STATISTICS CALCULATOR VIEW
// ==========================================
@Composable
fun StatsCalculatorView(isEnglish: Boolean) {
    var rawNumbers by remember { mutableStateOf("10, 20, 30, 40, 50") }
    var statsResult by remember { mutableStateOf("") }

    fun calculateStats() {
        try {
            val list = rawNumbers.split(",", " ", "\n")
                .mapNotNull { it.trim().toDoubleOrNull() }

            if (list.isEmpty()) {
                statsResult = if (isEnglish) "No valid numbers entered." else "يرجى إدخال أرقام صالحة."
                return
            }

            val count = list.size
            val sum = list.sum()
            val mean = sum / count
            val variance = list.sumOf { (it - mean).pow(2) } / count
            val stdDev = sqrt(variance)
            val minVal = list.minOrNull() ?: 0.0
            val maxVal = list.maxOrNull() ?: 0.0

            statsResult = """
                • ${if (isEnglish) "Count (n)" else "عدد القيم (n)"}: $count
                • ${if (isEnglish) "Sum (Σx)" else "المجموع (Σx)"}: ${formatResultNumber(sum)}
                • ${if (isEnglish) "Mean (x̄)" else "المتوسط الحسابي (x̄)"}: ${formatResultNumber(mean)}
                • ${if (isEnglish) "Std Deviation (σ)" else "الانحراف المعياري (σ)"}: ${formatResultNumber(stdDev)}
                • ${if (isEnglish) "Variance (σ²)" else "التباين (σ²)"}: ${formatResultNumber(variance)}
                • ${if (isEnglish) "Min / Max" else "أدنى وأعلى قيمة"}: ${formatResultNumber(minVal)} / ${formatResultNumber(maxVal)}
            """.trimIndent()
        } catch (e: Exception) {
            statsResult = if (isEnglish) "Error in statistical calculation" else "خطأ في إجراء الحسابات الإحصائية"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Statistical Data Analyzer 📊" else "حاسبة الإحصاء وتحليل البيانات 📊",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = rawNumbers,
                        onValueChange = { rawNumbers = it },
                        label = { Text(if (isEnglish) "Numbers separated by comma or space" else "أدخل القيم مقسومة بفاصلة أو مسافة") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Button(
                        onClick = { calculateStats() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "Calculate Statistics (x̄, σ, Σx)" else "احسب الإحصائيات (x̄, σ, Σx)")
                    }
                }
            }
        }

        if (statsResult.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (isEnglish) "Statistical Output:" else "نتائج التحليل الإحصائي:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statsResult,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. COMPLEX NUMBERS CALCULATOR VIEW (CMPLX)
// ==========================================
@Composable
fun ComplexCalculatorView(isEnglish: Boolean) {
    var real1 by remember { mutableStateOf("3") }
    var imag1 by remember { mutableStateOf("4") }
    var real2 by remember { mutableStateOf("1") }
    var imag2 by remember { mutableStateOf("2") }
    var isDeg by remember { mutableStateOf(true) }

    var calculationResult by remember { mutableStateOf("") }
    var calculationDetails by remember { mutableStateOf("") }

    fun formatComplex(r: Double, i: Double): String {
        val rStr = formatResultNumber(r)
        val absI = abs(i)
        val iStr = formatResultNumber(absI)
        return when {
            abs(i) < 1e-9 -> rStr
            abs(r) < 1e-9 -> if (i < 0) "-${iStr}i" else "${iStr}i"
            i < 0 -> "$rStr - ${iStr}i"
            else -> "$rStr + ${iStr}i"
        }
    }

    fun analyzeZ1() {
        try {
            val a = real1.toDoubleOrNull() ?: 0.0
            val b = imag1.toDoubleOrNull() ?: 0.0
            val mod = sqrt(a * a + b * b)
            val rad = atan2(b, a)
            val deg = Math.toDegrees(rad)
            val angleStr = if (isDeg) "${formatResultNumber(deg)}°" else "${formatResultNumber(rad)} rad"

            calculationResult = "Z₁ = ${formatComplex(a, b)}"
            calculationDetails = """
                • ${if (isEnglish) "Rectangular Form" else "الصيغة الديكارتية"}: ${formatComplex(a, b)}
                • ${if (isEnglish) "Modulus |Z₁|" else "المقياس / المعيار |Z₁|"}: ${formatResultNumber(mod)}
                • ${if (isEnglish) "Argument θ" else "السعة / الزاوية θ"}: $angleStr
                • ${if (isEnglish) "Polar Form (r ∠ θ)" else "الصيغة القطبية (r ∠ θ)"}: ${formatResultNumber(mod)} ∠ $angleStr
                • ${if (isEnglish) "Conjugate (Z̄₁)" else "المرافق (Z̄₁)"}: ${formatComplex(a, -b)}
                • ${if (isEnglish) "Square (Z₁²)" else "المربع (Z₁²)"}: ${formatComplex(a * a - b * b, 2 * a * b)}
                • Re(Z₁) = ${formatResultNumber(a)}, Im(Z₁) = ${formatResultNumber(b)}
            """.trimIndent()
        } catch (e: Exception) {
            calculationResult = if (isEnglish) "Error in input values" else "خطأ في القيم المدخلة"
            calculationDetails = ""
        }
    }

    fun operate(operation: String) {
        try {
            val a = real1.toDoubleOrNull() ?: 0.0
            val b = imag1.toDoubleOrNull() ?: 0.0
            val c = real2.toDoubleOrNull() ?: 0.0
            val d = imag2.toDoubleOrNull() ?: 0.0

            when (operation) {
                "+" -> {
                    val r = a + c
                    val i = b + d
                    calculationResult = "Z₁ + Z₂ = ${formatComplex(r, i)}"
                    calculationDetails = "(${formatComplex(a, b)}) + (${formatComplex(c, d)}) = ${formatComplex(r, i)}"
                }
                "-" -> {
                    val r = a - c
                    val i = b - d
                    calculationResult = "Z₁ - Z₂ = ${formatComplex(r, i)}"
                    calculationDetails = "(${formatComplex(a, b)}) - (${formatComplex(c, d)}) = ${formatComplex(r, i)}"
                }
                "*" -> {
                    val r = a * c - b * d
                    val i = a * d + b * c
                    calculationResult = "Z₁ × Z₂ = ${formatComplex(r, i)}"
                    calculationDetails = "(${formatComplex(a, b)}) × (${formatComplex(c, d)}) = ${formatComplex(r, i)}"
                }
                "/" -> {
                    val denom = c * c + d * d
                    if (denom == 0.0) {
                        calculationResult = if (isEnglish) "Math ERROR: Division by Zero" else "خطأ رياضي: القسمة على صفر"
                        calculationDetails = ""
                    } else {
                        val r = (a * c + b * d) / denom
                        val i = (b * c - a * d) / denom
                        calculationResult = "Z₁ ÷ Z₂ = ${formatComplex(r, i)}"
                        calculationDetails = "(${formatComplex(a, b)}) ÷ (${formatComplex(c, d)}) = ${formatComplex(r, i)}"
                    }
                }
            }
        } catch (e: Exception) {
            calculationResult = if (isEnglish) "Calculation Error" else "خطأ في إجراء العملية"
            calculationDetails = ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Complex Numbers (CMPLX) ⚛️" else "حاسبة الأعداد المركبة (CMPLX) ⚛️",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FilterChip(
                            selected = isDeg,
                            onClick = { isDeg = !isDeg },
                            label = { Text(if (isDeg) "DEG" else "RAD", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Text(
                        text = if (isEnglish) "First Complex Number Z₁ = a + bi:" else "العدد المركب الأول Z₁ = a + bi:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = real1,
                            onValueChange = { real1 = it },
                            label = { Text(if (isEnglish) "Real a" else "الحقيقي a") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = imag1,
                            onValueChange = { imag1 = it },
                            label = { Text(if (isEnglish) "Imag b" else "التخيلي b") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Text(
                        text = if (isEnglish) "Second Complex Number Z₂ = c + di:" else "العدد المركب الثاني Z₂ = c + di:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = real2,
                            onValueChange = { real2 = it },
                            label = { Text(if (isEnglish) "Real c" else "الحقيقي c") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = imag2,
                            onValueChange = { imag2 = it },
                            label = { Text(if (isEnglish) "Imag d" else "التخيلي d") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Text(
                        text = if (isEnglish) "Arithmetic Operations (Z₁ & Z₂):" else "العمليات الحسابية بين Z₁ و Z₂:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { operate("+") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Z₁ + Z₂", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { operate("-") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Z₁ − Z₂", fontSize = 13.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { operate("*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Z₁ × Z₂", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { operate("/") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Z₁ ÷ Z₂", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { analyzeZ1() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Analyze Z₁ (|Z₁|, θ, Polar, Z̄₁)" else "تحليل Z₁ (المقياس |Z₁|، الزاوية θ، القطبية، المرافق)")
                    }
                }
            }
        }

        if (calculationResult.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEnglish) "Result:" else "النتيجة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = calculationResult,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (calculationDetails.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = calculationDetails,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. BASE-N NUMBER SYSTEMS CALCULATOR VIEW
// ==========================================
@Composable
fun BaseNCalculatorView(isEnglish: Boolean) {
    var selectedBase by remember { mutableStateOf("DEC") }
    var inputVal by remember { mutableStateOf("255") }
    var operand2 by remember { mutableStateOf("15") }
    var operationResult by remember { mutableStateOf("") }
    var operationDetails by remember { mutableStateOf("") }

    val bases = listOf("DEC", "HEX", "BIN", "OCT")

    fun parseInputToLong(str: String, baseStr: String): Long? {
        val clean = str.trim().replace(" ", "")
        if (clean.isEmpty()) return 0L
        return try {
            when (baseStr) {
                "HEX" -> clean.toLong(16)
                "BIN" -> clean.toLong(2)
                "OCT" -> clean.toLong(8)
                else -> clean.toLong(10)
            }
        } catch (e: Exception) {
            null
        }
    }

    val currentLong = parseInputToLong(inputVal, selectedBase) ?: 0L

    val decStr = currentLong.toString(10)
    val hexStr = currentLong.toString(16).uppercase(Locale.US)
    val binRaw = currentLong.toString(2)
    val binFormatted = binRaw.padStart(((binRaw.length + 3) / 4) * 4, '0')
        .chunked(4).joinToString(" ")
    val octStr = currentLong.toString(8)

    fun applyBitwise(op: String) {
        val op1 = parseInputToLong(inputVal, selectedBase)
        val op2 = parseInputToLong(operand2, selectedBase)

        if (op1 == null) {
            operationResult = if (isEnglish) "Invalid first value" else "القيمة الأولى غير صالحة"
            return
        }

        val resLong: Long = when (op) {
            "NOT" -> op1.inv()
            "AND" -> if (op2 != null) op1 and op2 else 0L
            "OR" -> if (op2 != null) op1 or op2 else 0L
            "XOR" -> if (op2 != null) op1 xor op2 else 0L
            "NAND" -> if (op2 != null) (op1 and op2).inv() else 0L
            "NOR" -> if (op2 != null) (op1 or op2).inv() else 0L
            "XNOR" -> if (op2 != null) (op1 xor op2).inv() else 0L
            "SHL" -> if (op2 != null) op1 shl (op2.toInt().coerceIn(0, 62)) else op1 shl 1
            "SHR" -> if (op2 != null) op1 ushr (op2.toInt().coerceIn(0, 62)) else op1 ushr 1
            "+" -> if (op2 != null) op1 + op2 else 0L
            "-" -> if (op2 != null) op1 - op2 else 0L
            "*" -> if (op2 != null) op1 * op2 else 0L
            "/" -> if (op2 != null && op2 != 0L) op1 / op2 else 0L
            else -> 0L
        }

        val resInBase = when (selectedBase) {
            "HEX" -> resLong.toString(16).uppercase(Locale.US)
            "BIN" -> resLong.toString(2)
            "OCT" -> resLong.toString(8)
            else -> resLong.toString(10)
        }

        operationResult = "$selectedBase: $resInBase"
        operationDetails = """
            • DEC: ${resLong.toString(10)}
            • HEX: 0x${resLong.toString(16).uppercase(Locale.US)}
            • BIN: ${resLong.toString(2)}
            • OCT: ${resLong.toString(8)}
        """.trimIndent()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "BASE-N Number Systems 💻" else "حاسبة الأنظمة العددية والمنطقية (BASE-N) 💻",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Base Selector Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bases.forEach { b ->
                            FilterChip(
                                selected = selectedBase == b,
                                onClick = {
                                    // Convert current number to the new base
                                    val currentValLong = parseInputToLong(inputVal, selectedBase) ?: 0L
                                    selectedBase = b
                                    inputVal = when (b) {
                                        "HEX" -> currentValLong.toString(16).uppercase(Locale.US)
                                        "BIN" -> currentValLong.toString(2)
                                        "OCT" -> currentValLong.toString(8)
                                        else -> currentValLong.toString(10)
                                    }
                                },
                                label = { Text(b, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it.uppercase(Locale.US) },
                        label = { Text(if (isEnglish) "Value in $selectedBase" else "القيمة في نظام $selectedBase") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (inputVal.isNotEmpty()) {
                                IconButton(onClick = { inputVal = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    // Hex quick keys if HEX selected
                    if (selectedBase == "HEX") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("A", "B", "C", "D", "E", "F").forEach { hexChar ->
                                OutlinedButton(
                                    onClick = { inputVal += hexChar },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(hexChar, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Live Multi-Base Conversion Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Real-Time Base Conversions:" else "التحويل المباشر بين الأنظمة:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("• DEC (العشري): $decStr", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("• HEX (السادس عشري): 0x$hexStr", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("• BIN (الثنائي): $binFormatted", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("• OCT (الثماني): $octStr", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = if (isEnglish) "Second Operand (for 2-value operations):" else "القيمة الثانية (للعمليات الثنائية):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = operand2,
                        onValueChange = { operand2 = it.uppercase(Locale.US) },
                        label = { Text(if (isEnglish) "Second Value ($selectedBase)" else "القيمة الثانية ($selectedBase)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (isEnglish) "Logical & Bitwise Operations:" else "العمليات المنطقية وBITWISE:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(onClick = { applyBitwise("AND") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("AND", fontSize = 11.sp) }
                        Button(onClick = { applyBitwise("OR") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("OR", fontSize = 11.sp) }
                        Button(onClick = { applyBitwise("XOR") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("XOR", fontSize = 11.sp) }
                        Button(onClick = { applyBitwise("NOT") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("NOT", fontSize = 11.sp) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { applyBitwise("NAND") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("NAND", fontSize = 10.sp) }
                        OutlinedButton(onClick = { applyBitwise("NOR") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("NOR", fontSize = 10.sp) }
                        OutlinedButton(onClick = { applyBitwise("SHL") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("<< SHL", fontSize = 10.sp) }
                        OutlinedButton(onClick = { applyBitwise("SHR") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(">> SHR", fontSize = 10.sp) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(onClick = { applyBitwise("+") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("+", fontSize = 14.sp) }
                        FilledTonalButton(onClick = { applyBitwise("-") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("−", fontSize = 14.sp) }
                        FilledTonalButton(onClick = { applyBitwise("*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("×", fontSize = 14.sp) }
                        FilledTonalButton(onClick = { applyBitwise("/") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("÷", fontSize = 14.sp) }
                    }
                }
            }
        }

        if (operationResult.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEnglish) "Operation Output:" else "نتيجة العملية المنطقية:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = operationResult,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (operationDetails.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = operationDetails,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. TABLE CALCULATOR VIEW (Casio fx-991 TABLE Mode)
// ==========================================
data class TableRowData(
    val index: Int,
    val x: Double,
    val fx: Double,
    val gx: Double? = null
)

@Composable
fun TableCalculatorView(isEnglish: Boolean) {
    var fxExpr by remember { mutableStateOf("X^2 - 4") }
    var gxExpr by remember { mutableStateOf("") }
    var startVal by remember { mutableStateOf("-3") }
    var endVal by remember { mutableStateOf("3") }
    var stepVal by remember { mutableStateOf("1") }
    var isDeg by remember { mutableStateOf(true) }

    var tableRows by remember { mutableStateOf<List<TableRowData>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var minFx by remember { mutableStateOf<Double?>(null) }
    var maxFx by remember { mutableStateOf<Double?>(null) }

    fun generateTable() {
        errorMessage = null
        val start = startVal.toDoubleOrNull()
        val end = endVal.toDoubleOrNull()
        val step = stepVal.toDoubleOrNull()

        if (start == null || end == null || step == null) {
            errorMessage = if (isEnglish) "Please enter valid Start, End, and Step numbers." else "يرجى إدخال قيم صالحة للبداية والنهاية والخطوة."
            tableRows = emptyList()
            return
        }

        if (step <= 0) {
            errorMessage = if (isEnglish) "Step must be greater than 0." else "يجب أن تكون قيمة الخطوة (Step) أكبر من 0."
            tableRows = emptyList()
            return
        }

        if (end < start) {
            errorMessage = if (isEnglish) "End value must be greater than or equal to Start value." else "قيمة النهاية يجب أن تكون أكبر من أو تساوي البداية."
            tableRows = emptyList()
            return
        }

        val totalSteps = ((end - start) / step).toInt() + 1
        if (totalSteps > 200) {
            errorMessage = if (isEnglish) "Too many steps (>200). Increase the step size." else "عدد الخطوات كبير جداً (>200). يرجى زيادة قيمة الخطوة."
            tableRows = emptyList()
            return
        }

        try {
            val list = mutableListOf<TableRowData>()
            var currentX = start
            var idx = 1
            val hasG = gxExpr.isNotBlank()

            while (currentX <= end + (step / 10.0) && list.size < totalSteps) {
                val fx = CalculatorEngine.evaluate(fxExpr, isDeg, "0", xVal = currentX)
                val gx = if (hasG) CalculatorEngine.evaluate(gxExpr, isDeg, "0", xVal = currentX) else null
                list.add(TableRowData(index = idx++, x = currentX, fx = fx, gx = gx))
                currentX += step
            }

            tableRows = list
            val validFx = list.map { it.fx }.filter { !it.isNaN() && !it.isInfinite() }
            minFx = validFx.minOrNull()
            maxFx = validFx.maxOrNull()
        } catch (e: Exception) {
            errorMessage = if (isEnglish) "Error calculating table values." else "حدث خطأ أثناء حساب جدول القيم."
            tableRows = emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Function Table (TABLE) 📋" else "جدول قيم الدوال (TABLE) 📋",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FilterChip(
                            selected = isDeg,
                            onClick = { isDeg = !isDeg },
                            label = { Text(if (isDeg) "DEG" else "RAD", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    OutlinedTextField(
                        value = fxExpr,
                        onValueChange = { fxExpr = it },
                        label = { Text("f(X) =") },
                        placeholder = { Text("e.g. X^2 - 4 or 2X + 1") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gxExpr,
                        onValueChange = { gxExpr = it },
                        label = { Text(if (isEnglish) "Optional g(X) =" else "الدالة الثانية الاختيارية g(X) =") },
                        placeholder = { Text("e.g. 2X or sin(X)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("X^2 - 4", "2X + 1", "sin(X)", "X^3 - 3X").forEach { preset ->
                            SuggestionChip(
                                onClick = { fxExpr = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Start, End, Step inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startVal,
                            onValueChange = { startVal = it },
                            label = { Text(if (isEnglish) "Start" else "البداية") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = endVal,
                            onValueChange = { endVal = it },
                            label = { Text(if (isEnglish) "End" else "النهاية") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = stepVal,
                            onValueChange = { stepVal = it },
                            label = { Text(if (isEnglish) "Step" else "الخطوة") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Button(
                        onClick = { generateTable() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Generate Function Table" else "توليد جدول القيم f(X)")
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (tableRows.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEnglish) "Table Results (${tableRows.size} points):" else "جدول القيم (${tableRows.size} نقطة):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (minFx != null && maxFx != null) {
                                Text(
                                    text = "Min: ${formatResultNumber(minFx!!)} | Max: ${formatResultNumber(maxFx!!)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Table Header
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), fontSize = 13.sp)
                                Text("X", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text("f(X)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), fontSize = 13.sp)
                                if (gxExpr.isNotBlank()) {
                                    Text("g(X)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), fontSize = 13.sp)
                                }
                            }
                        }

                        // Table Rows
                        tableRows.forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .padding(vertical = 7.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.index.toString(), modifier = Modifier.weight(0.5f), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                Text(formatResultNumber(row.x), modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text(
                                    formatResultNumber(row.fx),
                                    modifier = Modifier.weight(1.2f),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (row.gx != null) {
                                    Text(
                                        formatResultNumber(row.gx),
                                        modifier = Modifier.weight(1.2f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary
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

// ==========================================
// 6. KOTLIN MATH EXPRESSION EVALUATOR ENGINE
// ==========================================
fun formatResultNumber(valNum: Double): String {
    if (valNum.isNaN()) return "Error"
    if (valNum.isInfinite()) return if (valNum > 0) "∞" else "-∞"
    val intVal = valNum.toLong()
    return if (abs(valNum - intVal) < 1e-9) {
        intVal.toString()
    } else {
        String.format("%.6f", valNum).trimEnd('0').trimEnd('.')
    }
}

fun evaluateMathExpression(rawExpr: String, isDeg: Boolean, ansVal: String): Double {
    return CalculatorEngine.evaluate(rawExpr, isDeg, ansVal)
}
