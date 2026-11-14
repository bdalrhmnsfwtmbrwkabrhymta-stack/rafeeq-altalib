package com.example.ui.screens.chess

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.chess.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChessGameMode(val titleAr: String, val icon: String) {
    VS_BOTS("ضد البوتات والكمبيوتر 🤖", "🤖"),
    PUZZLES_1000("بنك الـ 1000 لغز 🧩", "🧩"),
    PASS_PLAY("شخصان على نفس الجهاز 👥", "👥"),
    ANALYSIS_SELF("التحليل واللعب ضد النفس 🔍", "🔍"),
    LOCAL_P2P("عبر نقطة الاتصال / البلوتوث 📡", "📡")
}

enum class BoardTheme(val titleAr: String, val lightColor: Color, val darkColor: Color) {
    CHESS_COM_GREEN("أخضر البطولة (Chess.com)", Color(0xFFEBECD0), Color(0xFF739552)),
    CLASSIC_WOOD("خشب طبيعي كلاسيكي", Color(0xFFF0D9B5), Color(0xFFB58863)),
    MIDNIGHT_BLUE("أزرق ليلي حديث", Color(0xFFE2E8F0), Color(0xFF475569))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    onNavigateBack: () -> Unit = {}
) {
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Mode & Settings
    var gameMode by remember { mutableStateOf(ChessGameMode.VS_BOTS) }
    var boardTheme by remember { mutableStateOf(BoardTheme.CHESS_COM_GREEN) }
    var isBoardFlipped by remember { mutableStateOf(false) }

    // Bot Selection
    var selectedBotIndex by remember { mutableIntStateOf(1) } // 0: Martin (400), 1: Karim (1200), 2: Nadia (1800), 3: Magnus (2800)
    var isBotThinking by remember { mutableStateOf(false) }

    // 1000 Puzzles Progression
    var currentPuzzleLevel by remember { mutableIntStateOf(1) }
    var puzzleUserRating by remember { mutableIntStateOf(650) }
    var puzzleStreak by remember { mutableIntStateOf(0) }
    var currentPuzzle by remember { mutableStateOf<ChessPuzzle?>(null) }
    var puzzleSolved by remember { mutableStateOf(false) }
    var puzzleFailed by remember { mutableStateOf(false) }
    var showPuzzleHint by remember { mutableStateOf(false) }
    var showLevelBrowserDialog by remember { mutableStateOf(false) }

    // Core Board State
    val board = remember { ChessBoard() }
    var boardVersion by remember { mutableIntStateOf(0) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var validMovesForSelected by remember { mutableStateOf<List<Position>>(emptyList()) }
    var lastMove by remember { mutableStateOf<ChessMove?>(null) }
    var pendingPromotionFromTo by remember { mutableStateOf<Pair<Position, Position>?>(null) }

    // Navigation in Move History (Analysis)
    var viewHistoryIndex by remember { mutableIntStateOf(-1) }

    // Timers for White & Black (in seconds)
    var selectedTimeLimitMinutes by remember { mutableIntStateOf(10) } // 0 = unlimited, 1 = bullet, 3 = blitz, 10 = rapid
    var whiteSecondsRemaining by remember { mutableIntStateOf(600) }
    var blackSecondsRemaining by remember { mutableIntStateOf(600) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // P2P Multiplayer
    val p2pManager = remember { LocalP2PChessManager() }
    var isHostingP2P by remember { mutableStateOf(false) }
    var isJoinedP2P by remember { mutableStateOf(false) }
    var hostIpInput by remember { mutableStateOf("192.168.43.1") }
    var showP2pDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                hostIpInput = LocalP2PChessManager.getLocalIpAddress()
            } catch (_: Throwable) {}
        }
    }

    // Dialogs
    var showGameOverDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBotSelectDialog by remember { mutableStateOf(false) }

    val movesListState = rememberLazyListState()

    fun playSoundEffect(type: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
                when (type) {
                    "move" -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                    "capture" -> toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 90)
                    "check" -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 160)
                    "win" -> toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
                    "error" -> toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 120)
                }
                delay(300)
                toneGen.release()
            } catch (_: Throwable) {}
        }
    }

    fun executeMoveInternal(from: Position, to: Position, promotion: PieceType = PieceType.QUEEN) {
        val movingPiece = board.getPiece(from) ?: return

        // Check if move is pawn promotion
        if (movingPiece.type == PieceType.PAWN && ((movingPiece.color == PieceColor.WHITE && to.row == 0) || (movingPiece.color == PieceColor.BLACK && to.row == 7)) && pendingPromotionFromTo == null) {
            pendingPromotionFromTo = from to to
            return
        }

        val executed = board.executeMove(from, to, promotion)
        if (executed != null) {
            lastMove = executed
            selectedPosition = null
            validMovesForSelected = emptyList()
            pendingPromotionFromTo = null
            boardVersion++
            viewHistoryIndex = board.moveHistory.size - 1

            if (executed.isCheckmate) {
                playSoundEffect("win")
                showGameOverDialog = true
            } else if (executed.isCheck) {
                playSoundEffect("check")
            } else if (executed.capturedPiece != null) {
                playSoundEffect("capture")
            } else {
                playSoundEffect("move")
            }

            // Sync with P2P
            if (p2pManager.isConnected) {
                p2pManager.sendMove("MOVE:${from.toAlgebraic()}-${to.toAlgebraic()}")
            }

            // Handle Puzzle Validation
            if (gameMode == ChessGameMode.PUZZLES_1000 && currentPuzzle != null) {
                val sol = currentPuzzle!!.solutionMoves.firstOrNull()
                if (sol != null && from == sol.first && to == sol.second) {
                    puzzleSolved = true
                    puzzleFailed = false
                    puzzleStreak++
                    puzzleUserRating += 15
                    playSoundEffect("win")
                } else {
                    puzzleFailed = true
                    puzzleStreak = 0
                    puzzleUserRating = max(400, puzzleUserRating - 10)
                    playSoundEffect("error")
                }
            }

            // Trigger Bot Move if in VS_BOTS mode
            if (gameMode == ChessGameMode.VS_BOTS && !board.isGameOver && board.turn == PieceColor.BLACK) {
                isBotThinking = true
                coroutineScope.launch {
                    val bot = ChessAiEngine.BOTS.getOrElse(selectedBotIndex) { ChessAiEngine.BOTS[1] }
                    delay((300 + (bot.depth * 150)).toLong())
                    val best = withContext(Dispatchers.Default) {
                        ChessAiEngine.findBestMove(board, selectedBotIndex)
                    }
                    isBotThinking = false
                    if (best != null) {
                        val botExec = board.executeMove(best.first, best.second)
                        if (botExec != null) {
                            lastMove = botExec
                            boardVersion++
                            viewHistoryIndex = board.moveHistory.size - 1
                            if (botExec.isCheckmate) {
                                playSoundEffect("win")
                                showGameOverDialog = true
                            } else if (botExec.isCheck) {
                                playSoundEffect("check")
                            } else if (botExec.capturedPiece != null) {
                                playSoundEffect("capture")
                            } else {
                                playSoundEffect("move")
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadPuzzleLevel(lvl: Int) {
        val puz = ChessPuzzleRepository.getPuzzleByLevel(lvl)
        currentPuzzle = puz
        board.loadFromFenLike(puz.rows, puz.playerColor)
        boardVersion++
        selectedPosition = null
        validMovesForSelected = emptyList()
        lastMove = null
        puzzleSolved = false
        puzzleFailed = false
        showPuzzleHint = false
    }

    fun startNewGame(mode: ChessGameMode) {
        gameMode = mode
        if (mode == ChessGameMode.PUZZLES_1000) {
            loadPuzzleLevel(currentPuzzleLevel)
        } else {
            board.resetBoard()
            boardVersion++
            selectedPosition = null
            validMovesForSelected = emptyList()
            lastMove = null
            viewHistoryIndex = -1
            whiteSecondsRemaining = if (selectedTimeLimitMinutes > 0) selectedTimeLimitMinutes * 60 else 9999
            blackSecondsRemaining = if (selectedTimeLimitMinutes > 0) selectedTimeLimitMinutes * 60 else 9999
            isTimerRunning = selectedTimeLimitMinutes > 0
        }
    }

    LaunchedEffect(gameMode) {
        startNewGame(gameMode)
    }

    // Timer countdown loop
    LaunchedEffect(isTimerRunning, board.turn, board.isGameOver) {
        while (isTimerRunning && !board.isGameOver && selectedTimeLimitMinutes > 0) {
            delay(1000L)
            if (board.turn == PieceColor.WHITE) {
                if (whiteSecondsRemaining > 0) whiteSecondsRemaining-- else {
                    board.isGameOver = true
                    board.winner = PieceColor.BLACK
                    showGameOverDialog = true
                }
            } else {
                if (blackSecondsRemaining > 0) blackSecondsRemaining-- else {
                    board.isGameOver = true
                    board.winner = PieceColor.WHITE
                    showGameOverDialog = true
                }
            }
        }
    }

    // Auto-scroll moves list
    LaunchedEffect(board.moveHistory.size) {
        if (board.moveHistory.isNotEmpty()) {
            val targetIdx = (board.moveHistory.size - 1) / 2
            try {
                movesListState.animateScrollToItem(targetIdx.coerceAtLeast(0))
            } catch (_: Throwable) {}
        }
    }

    val chessScrollState = androidx.compose.foundation.rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "الشطرنج العالمي الاحترافي ♟️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Text(
                            when (gameMode) {
                                ChessGameMode.VS_BOTS -> {
                                    val bot = ChessAiEngine.BOTS.getOrElse(selectedBotIndex) { ChessAiEngine.BOTS[1] }
                                    "ضد ${bot.nameAr} (${bot.elo} ELO)"
                                }
                                ChessGameMode.PUZZLES_1000 -> "لغز #$currentPuzzleLevel (${currentPuzzle?.ratingElo ?: 600} ELO) • تقييمك: $puzzleUserRating"
                                ChessGameMode.PASS_PLAY -> "شخصان على نفس الجهاز"
                                ChessGameMode.ANALYSIS_SELF -> "لوحة التحليل والتدريب الحر 🔍"
                                ChessGameMode.LOCAL_P2P -> if (p2pManager.isConnected) "متصل بالصديق 🟢" else "نقطة اتصال وبلوتوث"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { isBoardFlipped = !isBoardFlipped }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "قلب الرقعة", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "تغيير مظهر الرقعة")
                    }
                    IconButton(onClick = { startNewGame(gameMode) }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "إعادة اللعبة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(chessScrollState)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Select Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ChessGameMode.entries) { mode ->
                    FilterChip(
                        selected = gameMode == mode,
                        onClick = {
                            gameMode = mode
                            if (mode == ChessGameMode.LOCAL_P2P && !p2pManager.isConnected) {
                                showP2pDialog = true
                            }
                        },
                        label = { Text(mode.titleAr, fontSize = 11.5.sp, fontWeight = if (gameMode == mode) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("mode_${mode.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Black Player Header / Bot Card
            ChessPlayerCard(
                name = if (gameMode == ChessGameMode.VS_BOTS) {
                    val bot = ChessAiEngine.BOTS.getOrElse(selectedBotIndex) { ChessAiEngine.BOTS[1] }
                    bot.nameAr
                } else "الخصم (الأسود)",
                avatar = if (gameMode == ChessGameMode.VS_BOTS) ChessAiEngine.BOTS.getOrElse(selectedBotIndex) { ChessAiEngine.BOTS[1] }.avatar else "👤",
                ratingElo = if (gameMode == ChessGameMode.VS_BOTS) ChessAiEngine.BOTS.getOrElse(selectedBotIndex) { ChessAiEngine.BOTS[1] }.elo else 1500,
                color = PieceColor.BLACK,
                isTurn = board.turn == PieceColor.BLACK,
                isThinking = isBotThinking,
                capturedPieces = board.capturedByBlack,
                materialAdvantage = board.getMaterialAdvantage(PieceColor.BLACK),
                timeRemainingSeconds = if (selectedTimeLimitMinutes > 0) blackSecondsRemaining else null,
                onChangeBotClick = if (gameMode == ChessGameMode.VS_BOTS) { { showBotSelectDialog = true } } else null
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Main Chess Board Area + Evaluation Bar (Chess.com Style)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth - 8.dp
                val boardDim = if (availableWidth < 390.dp) (availableWidth).coerceAtLeast(240.dp) else 390.dp
                Row(
                    modifier = Modifier
                        .width(boardDim)
                        .height(boardDim),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real-time Evaluation Bar
                    ChessEvaluationBar(
                        score = board.getEvaluationScore(),
                        isFlipped = isBoardFlipped,
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight()
                            .padding(end = 4.dp)
                    )

                    // The 8x8 Board Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B2927))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ChessBoardGridPro(
                            board = board,
                            boardVersion = boardVersion,
                            theme = boardTheme,
                            isFlipped = isBoardFlipped,
                            selectedPosition = selectedPosition,
                            validMoves = validMovesForSelected,
                            lastMove = lastMove,
                            onSquareClick = { clickedPos ->
                                val pieceAtClick = board.getPiece(clickedPos)
                                if (selectedPosition == null) {
                                    if (pieceAtClick != null && (gameMode == ChessGameMode.ANALYSIS_SELF || pieceAtClick.color == board.turn)) {
                                        selectedPosition = clickedPos
                                        validMovesForSelected = board.getValidMovesFor(clickedPos)
                                    }
                                } else {
                                    if (clickedPos in validMovesForSelected) {
                                        executeMoveInternal(selectedPosition!!, clickedPos)
                                    } else if (pieceAtClick != null && (gameMode == ChessGameMode.ANALYSIS_SELF || pieceAtClick.color == board.turn)) {
                                        selectedPosition = clickedPos
                                        validMovesForSelected = board.getValidMovesFor(clickedPos)
                                    } else {
                                        selectedPosition = null
                                        validMovesForSelected = emptyList()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // White Player Header (User)
            ChessPlayerCard(
                name = "أنت (اللاعب الأبيض)",
                avatar = "👑",
                ratingElo = if (gameMode == ChessGameMode.PUZZLES_1000) puzzleUserRating else 1500,
                color = PieceColor.WHITE,
                isTurn = board.turn == PieceColor.WHITE,
                isThinking = false,
                capturedPieces = board.capturedByWhite,
                materialAdvantage = board.getMaterialAdvantage(PieceColor.WHITE),
                timeRemainingSeconds = if (selectedTimeLimitMinutes > 0) whiteSecondsRemaining else null
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Contextual Panel (Puzzles, Move Notation, Analysis Controls)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                if (gameMode == ChessGameMode.PUZZLES_1000 && currentPuzzle != null) {
                    // 1000 Puzzles Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentPuzzle!!.titleAr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(onClick = { showLevelBrowserDialog = true }) {
                                Text("اختر لغزاً (1..1000) 📋", fontSize = 11.5.sp)
                            }
                        }

                        Text(
                            currentPuzzle!!.story,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (puzzleSolved) {
                            Text(
                                "🎉 ممتاز جداً! حل صحيح تماماً! ${currentPuzzle!!.explanationAr}",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else if (puzzleFailed) {
                            Text(
                                "❌ نقلة غير موفقة! حاول مجدداً أو استعن بالتلميح الذكي.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        } else if (showPuzzleHint) {
                            Text(
                                "💡 تلميح: ${currentPuzzle!!.hintAr}",
                                color = Color(0xFF1565C0),
                                fontSize = 11.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPuzzleHint = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تلميح", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    currentPuzzleLevel = (currentPuzzleLevel % 1000) + 1
                                    loadPuzzleLevel(currentPuzzleLevel)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("اللغز التالي ➔", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Standard Notation Sheet & Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "سجل النقلات الرسمية (${board.moveHistory.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (board.moveHistory.isNotEmpty()) {
                                            val last = board.moveHistory.last()
                                            board.grid[last.from.row][last.from.col] = last.piece
                                            board.grid[last.to.row][last.to.col] = last.capturedPiece
                                            board.moveHistory.removeAt(board.moveHistory.size - 1)
                                            board.turn = last.piece.color
                                            board.isGameOver = false
                                            board.winner = null
                                            selectedPosition = null
                                            validMovesForSelected = emptyList()
                                            lastMove = board.moveHistory.lastOrNull()
                                            boardVersion++
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "تراجع عن نقلة", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Notation History Flow
                        LazyRow(
                            state = movesListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(board.moveHistory.chunked(2).withIndex().toList()) { (turnIdx, pair) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${turnIdx + 1}.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(pair[0].toNotation(), fontSize = 11.5.sp)
                                        if (pair.size > 1) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(pair[1].toNotation(), fontSize = 11.5.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pawn Promotion Selection Dialog
    if (pendingPromotionFromTo != null) {
        AlertDialog(
            onDismissRequest = { pendingPromotionFromTo = null },
            title = { Text("اختر قطعة الترقية 👑", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        PieceType.QUEEN to "وزير",
                        PieceType.ROOK to "قلعة",
                        PieceType.BISHOP to "فيل",
                        PieceType.KNIGHT to "حصان"
                    ).forEach { (type, name) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    val (from, to) = pendingPromotionFromTo!!
                                    executeMoveInternal(from, to, type)
                                }
                                .padding(8.dp)
                        ) {
                            Box(modifier = Modifier.size(42.dp)) {
                                ChessPieceVector(piece = ChessPiece(type, board.turn))
                            }
                            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Bot Selector Dialog
    if (showBotSelectDialog) {
        AlertDialog(
            onDismissRequest = { showBotSelectDialog = false },
            title = { Text("اختر منافسك من البوتات 🤖", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChessAiEngine.BOTS.forEachIndexed { idx, bot ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBotIndex = idx
                                    showBotSelectDialog = false
                                    startNewGame(ChessGameMode.VS_BOTS)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedBotIndex == idx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (selectedBotIndex == idx) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bot.avatar, fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(bot.nameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text("${bot.elo} ELO", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                    }
                                    Text(bot.descriptionAr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBotSelectDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // 1000 Puzzles Level Browser Dialog
    if (showLevelBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showLevelBrowserDialog = false },
            title = { Text("بنك الـ 1000 لغز شطرنج متدرج 🧩", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.height(360.dp)) {
                    Text("اختر اللغز من المستوى 1 (سهل 400 ELO) إلى المستوى 1000 (أستاذ خارق 2800 ELO):", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items((1..1000).toList()) { lvl ->
                            val puz = ChessPuzzleRepository.getPuzzleByLevel(lvl)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPuzzleLevel = lvl
                                        loadPuzzleLevel(lvl)
                                        showLevelBrowserDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (lvl == currentPuzzleLevel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("لغز #$lvl : ${puz.theme}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${puz.ratingElo} ELO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLevelBrowserDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Board Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("اختر مظهر رقعة الشطرنج 🎨") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    boardTheme = theme
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.darkColor)
                                    .padding(4.dp)
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(theme.lightColor))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(theme.titleAr, fontSize = 13.sp, fontWeight = if (boardTheme == theme) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("تم") }
            }
        )
    }

    // P2P Multiplayer Host/Join Dialog
    if (showP2pDialog) {
        AlertDialog(
            onDismissRequest = { showP2pDialog = false },
            title = { Text("اللعب اللاسلكي (نقطة الاتصال / بلوتوث) 📡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "العب مع صديقك بدون إنترنت! قم بتشغيل نقطة اتصال (Hotspot) أو اتصل بنفس شبكة الواي فاي.",
                        fontSize = 12.5.sp
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isHostingP2P = true
                                showP2pDialog = false
                                Toast.makeText(context, "تم بدء الغرفة! عنوان IP: ${LocalP2PChessManager.getLocalIpAddress()}", Toast.LENGTH_LONG).show()
                                p2pManager.startHost(
                                    onMoveReceived = { line ->
                                        if (line.startsWith("MOVE:")) {
                                            val parts = line.removePrefix("MOVE:").split("-")
                                            if (parts.size == 2) {
                                                val from = Position.fromAlgebraic(parts[0])
                                                val to = Position.fromAlgebraic(parts[1])
                                                if (from != null && to != null) {
                                                    executeMoveInternal(from, to)
                                                }
                                            }
                                        }
                                    },
                                    onClientConnected = {
                                        Toast.makeText(context, "انضم صديقك للعبة بنجاح! ⚔️", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. إنشاء غرفة (استضافة كلاعب أبيض)")
                    }

                    OutlinedTextField(
                        value = hostIpInput,
                        onValueChange = { hostIpInput = it },
                        label = { Text("أدخل عنوان IP للمستضيف") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isJoinedP2P = true
                                isBoardFlipped = true
                                showP2pDialog = false
                                p2pManager.joinHost(
                                    hostIp = hostIpInput.trim(),
                                    onMoveReceived = { line ->
                                        if (line.startsWith("MOVE:")) {
                                            val parts = line.removePrefix("MOVE:").split("-")
                                            if (parts.size == 2) {
                                                val from = Position.fromAlgebraic(parts[0])
                                                val to = Position.fromAlgebraic(parts[1])
                                                if (from != null && to != null) {
                                                    executeMoveInternal(from, to)
                                                }
                                            }
                                        }
                                    },
                                    onConnected = {
                                        Toast.makeText(context, "تم الاتصال بالمستضيف بنجاح! ⚔️", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("2. انضمام للغرفة (كلاعب أسود)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showP2pDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Game Over Dialog
    if (showGameOverDialog) {
        AlertDialog(
            onDismissRequest = { showGameOverDialog = false },
            title = {
                Text(
                    if (board.winner != null) "🏆 كش مات! فوز مستحق!" else "🤝 تعادل (Stalemate)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (board.winner == PieceColor.WHITE) "تهانينا! فاز اللاعب الأبيض بالذكاء والسيطرة 🎖️"
                    else if (board.winner == PieceColor.BLACK) "فاز اللاعب الأسود بالاستراتيجية والتكتيك 👏"
                    else "انتهت المباراة بالتعادل!"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showGameOverDialog = false
                    startNewGame(gameMode)
                }) {
                    Text("لعبة جديدة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGameOverDialog = false }) { Text("مراجعة اللوحة") }
            }
        )
    }
}

@Composable
fun ChessPlayerCard(
    name: String,
    avatar: String,
    ratingElo: Int,
    color: PieceColor,
    isTurn: Boolean,
    isThinking: Boolean,
    capturedPieces: List<ChessPiece>,
    materialAdvantage: Int,
    timeRemainingSeconds: Int? = null,
    onChangeBotClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isTurn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        border = if (isTurn) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(avatar, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isThinking) "$name يفكر... 💭" else name,
                            fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "($ratingElo)",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (onChangeBotClick != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "تغيير ▾",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onChangeBotClick() }
                            )
                        }
                    }

                    // Captured pieces
                    Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
                        capturedPieces.takeLast(6).forEach { p ->
                            Box(modifier = Modifier.size(16.dp)) {
                                ChessPieceVector(piece = p)
                            }
                        }
                        if (materialAdvantage > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+$materialAdvantage", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }
                }
            }

            // Digital Clock Timer
            if (timeRemainingSeconds != null) {
                val mins = timeRemainingSeconds / 60
                val secs = timeRemainingSeconds % 60
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (timeRemainingSeconds < 30) Color(0xFFEF4444) else if (isTurn) Color(0xFF1E293B) else Color(0xFF64748B)
                ) {
                    Text(
                        String.format("%02d:%02d", mins, secs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Real-time Centipawn Evaluation Bar (Chess.com Style)
 */
@Composable
fun ChessEvaluationBar(
    score: Float, // from -10f to +10f
    isFlipped: Boolean,
    modifier: Modifier = Modifier
) {
    // White fraction from 0.0 (all black) to 1.0 (all white)
    val whiteFraction = ((score + 10f) / 20f).coerceIn(0.05f, 0.95f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (isFlipped) 1f - whiteFraction else whiteFraction,
        animationSpec = tween(400), label = "eval"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E293B))
    ) {
        // Black section (Top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight((1f - animatedFraction).coerceAtLeast(0.01f))
                .background(Color(0xFF1E293B))
        )
        // White section (Bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(animatedFraction.coerceAtLeast(0.01f))
                .background(Color(0xFFFFFFFF))
        )
    }
}

/**
 * 8x8 Chess.com Style Grid with High-Resolution Vector Pieces
 */
@Composable
fun ChessBoardGridPro(
    board: ChessBoard,
    boardVersion: Int,
    theme: BoardTheme,
    isFlipped: Boolean,
    selectedPosition: Position?,
    validMoves: List<Position>,
    lastMove: ChessMove?,
    onSquareClick: (Position) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (displayRow in 0..7) {
            val r = if (isFlipped) 7 - displayRow else displayRow
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (displayCol in 0..7) {
                    val c = if (isFlipped) 7 - displayCol else displayCol
                    val currentPos = Position(r, c)
                    val isLightSquare = (r + c) % 2 == 0
                    val isSelected = selectedPosition == currentPos
                    val isValidTarget = currentPos in validMoves
                    val isLastMoveSquare = lastMove != null && (lastMove.from == currentPos || lastMove.to == currentPos)

                    val piece = board.getPiece(currentPos)
                    val isKingInCheck = piece?.type == PieceType.KING && piece.color == board.turn && board.isKingInCheck(piece.color)

                    val squareBgColor = when {
                        isKingInCheck -> Color(0xFFEF4444) // Red alert for check
                        isSelected -> Color(0xFFF7EC59) // Bright yellow
                        isLastMoveSquare -> if (isLightSquare) Color(0xFFF5F682) else Color(0xFFBACA44) // Chess.com move highlight
                        isLightSquare -> theme.lightColor
                        else -> theme.darkColor
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(squareBgColor)
                            .clickable { onSquareClick(currentPos) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Rank coordinate number on the left edge
                        if (displayCol == 0) {
                            Text(
                                text = (8 - r).toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLightSquare) theme.darkColor else theme.lightColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(1.dp)
                            )
                        }
                        // File coordinate letter on the bottom edge
                        if (displayRow == 7) {
                            Text(
                                text = ('a' + c).toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLightSquare) theme.darkColor else theme.lightColor,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(1.dp)
                            )
                        }

                        // Staunton High-Resolution Vector Piece (100% Cross-Free King)
                        if (piece != null) {
                            ChessPieceVector(
                                piece = piece,
                                modifier = Modifier.fillMaxSize(0.92f)
                            )
                        }

                        // Legal Move Destination Indicators (Dots for empty, corner brackets for capture)
                        if (isValidTarget) {
                            if (piece == null) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x55000000))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33EF4444))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
