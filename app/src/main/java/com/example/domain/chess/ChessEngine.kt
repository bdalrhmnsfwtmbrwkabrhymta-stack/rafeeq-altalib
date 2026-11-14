package com.example.domain.chess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class PieceColor {
    WHITE, BLACK
}

enum class PieceType(val arabicName: String, val value: Int) {
    PAWN("بيدق", 100),
    KNIGHT("حصان", 320),
    BISHOP("فيل", 330),
    ROOK("قلعة", 500),
    QUEEN("وزير", 900),
    KING("ملك", 20000)
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor,
    val hasMoved: Boolean = false
)

data class Position(val row: Int, val col: Int) {
    fun toAlgebraic(): String {
        val file = ('a' + col).toString()
        val rank = (8 - row).toString()
        return "$file$rank"
    }

    companion object {
        fun fromAlgebraic(s: String): Position? {
            if (s.length < 2) return null
            val col = s[0].lowercaseChar() - 'a'
            val rank = s[1].digitToIntOrNull() ?: return null
            val row = 8 - rank
            if (row in 0..7 && col in 0..7) return Position(row, col)
            return null
        }
    }
}

data class ChessMove(
    val from: Position,
    val to: Position,
    val piece: ChessPiece,
    val capturedPiece: ChessPiece? = null,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
    val promotionType: PieceType? = null
) {
    fun toNotation(): String {
        if (isCastling) {
            return if (to.col > from.col) "O-O" else "O-O-O"
        }
        val piecePrefix = when (piece.type) {
            PieceType.PAWN -> if (capturedPiece != null) ('a' + from.col).toString() else ""
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.ROOK -> "R"
            PieceType.QUEEN -> "Q"
            PieceType.KING -> "K"
        }
        val capture = if (capturedPiece != null) "x" else ""
        val target = to.toAlgebraic()
        val promo = if (promotionType != null) "=${when(promotionType) {
            PieceType.QUEEN -> "Q"
            PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"
            PieceType.KNIGHT -> "N"
            else -> "Q"
        }}" else ""
        val check = if (isCheckmate) "#" else if (isCheck) "+" else ""
        return "$piecePrefix$capture$target$promo$check"
    }
}

class ChessBoard {
    val grid = Array<Array<ChessPiece?>>(8) { arrayOfNulls(8) }
    var turn: PieceColor = PieceColor.WHITE
    var moveHistory = mutableListOf<ChessMove>()
    val capturedByWhite = mutableListOf<ChessPiece>()
    val capturedByBlack = mutableListOf<ChessPiece>()
    var enPassantTarget: Position? = null
    var isGameOver = false
    var winner: PieceColor? = null
    var isDraw = false

    init {
        resetBoard()
    }

    fun resetBoard() {
        for (r in 0..7) {
            for (c in 0..7) {
                grid[r][c] = null
            }
        }
        turn = PieceColor.WHITE
        moveHistory.clear()
        capturedByWhite.clear()
        capturedByBlack.clear()
        enPassantTarget = null
        isGameOver = false
        winner = null
        isDraw = false

        // Black pieces (Top: row 0 & 1)
        grid[0][0] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
        grid[0][1] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
        grid[0][2] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
        grid[0][3] = ChessPiece(PieceType.QUEEN, PieceColor.BLACK)
        grid[0][4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
        grid[0][5] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
        grid[0][6] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
        grid[0][7] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
        for (c in 0..7) {
            grid[1][c] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
        }

        // White pieces (Bottom: row 6 & 7)
        for (c in 0..7) {
            grid[6][c] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
        }
        grid[7][0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
        grid[7][1] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
        grid[7][2] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
        grid[7][3] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE)
        grid[7][4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
        grid[7][5] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
        grid[7][6] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
        grid[7][7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
    }

    fun loadFromFenLike(rows: List<String>, currentTurn: PieceColor = PieceColor.WHITE) {
        for (r in 0..7) {
            for (c in 0..7) {
                grid[r][c] = null
            }
        }
        turn = currentTurn
        moveHistory.clear()
        capturedByWhite.clear()
        capturedByBlack.clear()
        enPassantTarget = null
        isGameOver = false
        winner = null
        isDraw = false

        for (r in 0 until min(8, rows.size)) {
            val rowStr = rows[r]
            var c = 0
            for (char in rowStr) {
                if (c >= 8) break
                if (char.isDigit()) {
                    c += char.digitToInt()
                } else {
                    val color = if (char.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (char.lowercaseChar()) {
                        'p' -> PieceType.PAWN
                        'n' -> PieceType.KNIGHT
                        'b' -> PieceType.BISHOP
                        'r' -> PieceType.ROOK
                        'q' -> PieceType.QUEEN
                        'k' -> PieceType.KING
                        else -> PieceType.PAWN
                    }
                    grid[r][c] = ChessPiece(type, color)
                    c++
                }
            }
        }
    }

    fun copy(): ChessBoard {
        val copy = ChessBoard()
        for (r in 0..7) {
            for (c in 0..7) {
                copy.grid[r][c] = grid[r][c]
            }
        }
        copy.turn = turn
        copy.enPassantTarget = enPassantTarget
        copy.isGameOver = isGameOver
        copy.winner = winner
        copy.isDraw = isDraw
        copy.capturedByWhite.addAll(capturedByWhite)
        copy.capturedByBlack.addAll(capturedByBlack)
        copy.moveHistory.addAll(moveHistory)
        return copy
    }

    fun getPiece(pos: Position): ChessPiece? = if (isValidPos(pos)) grid[pos.row][pos.col] else null

    fun isValidPos(pos: Position): Boolean = pos.row in 0..7 && pos.col in 0..7

    fun getValidMovesFor(from: Position): List<Position> {
        val piece = getPiece(from) ?: return emptyList()
        if (piece.color != turn) return emptyList()

        val candidateMoves = getPseudoLegalMoves(from, piece, allowCastling = true)
        val legalMoves = mutableListOf<Position>()

        for (to in candidateMoves) {
            val simBoard = copy()
            simBoard.makeSimulatedMove(from, to)
            if (!simBoard.isKingInCheck(piece.color)) {
                legalMoves.add(to)
            }
        }
        return legalMoves
    }

    fun hasAnyLegalMoves(color: PieceColor): Boolean {
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = grid[r][c]
                if (piece != null && piece.color == color) {
                    val candidateMoves = getPseudoLegalMoves(Position(r, c), piece, allowCastling = false)
                    for (to in candidateMoves) {
                        val simBoard = copy()
                        simBoard.makeSimulatedMove(Position(r, c), to)
                        if (!simBoard.isKingInCheck(color)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun makeSimulatedMove(from: Position, to: Position) {
        val movingPiece = grid[from.row][from.col] ?: return
        grid[to.row][to.col] = movingPiece
        grid[from.row][from.col] = null
        if (movingPiece.type == PieceType.PAWN && to == enPassantTarget) {
            val captureRow = if (movingPiece.color == PieceColor.WHITE) to.row + 1 else to.row - 1
            if (captureRow in 0..7) {
                grid[captureRow][to.col] = null
            }
        }
    }

    fun getAllLegalMoves(color: PieceColor): List<Pair<Position, Position>> {
        val moves = mutableListOf<Pair<Position, Position>>()
        for (r in 0..7) {
            for (c in 0..7) {
                val pos = Position(r, c)
                val piece = grid[r][c]
                if (piece != null && piece.color == color) {
                    val validTos = getValidMovesFor(pos)
                    for (to in validTos) {
                        moves.add(pos to to)
                    }
                }
            }
        }
        return moves
    }

    private fun getPseudoLegalMoves(from: Position, piece: ChessPiece, allowCastling: Boolean = true): List<Position> {
        val moves = mutableListOf<Position>()
        val (r, c) = from

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                val startRow = if (piece.color == PieceColor.WHITE) 6 else 1

                // 1 step forward
                val oneStep = Position(r + dir, c)
                if (isValidPos(oneStep) && grid[oneStep.row][oneStep.col] == null) {
                    moves.add(oneStep)
                    // 2 steps forward
                    if (r == startRow) {
                        val twoStep = Position(r + 2 * dir, c)
                        if (isValidPos(twoStep) && grid[twoStep.row][twoStep.col] == null) {
                            moves.add(twoStep)
                        }
                    }
                }
                // Diagonals capture
                val diagCols = listOf(c - 1, c + 1)
                for (dc in diagCols) {
                    val diagPos = Position(r + dir, dc)
                    if (isValidPos(diagPos)) {
                        val target = grid[diagPos.row][diagPos.col]
                        if (target != null && target.color != piece.color) {
                            moves.add(diagPos)
                        } else if (diagPos == enPassantTarget) {
                            // En Passant
                            moves.add(diagPos)
                        }
                    }
                }
            }
            PieceType.KNIGHT -> {
                val deltas = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((dr, dc) in deltas) {
                    val target = Position(r + dr, c + dc)
                    if (isValidPos(target)) {
                        val p = grid[target.row][target.col]
                        if (p == null || p.color != piece.color) {
                            moves.add(target)
                        }
                    }
                }
            }
            PieceType.BISHOP -> {
                addRayMoves(moves, from, piece.color, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
            }
            PieceType.ROOK -> {
                addRayMoves(moves, from, piece.color, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            }
            PieceType.QUEEN -> {
                addRayMoves(
                    moves, from, piece.color,
                    listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
                )
            }
            PieceType.KING -> {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr != 0 || dc != 0) {
                            val target = Position(r + dr, c + dc)
                            if (isValidPos(target)) {
                                val p = grid[target.row][target.col]
                                if (p == null || p.color != piece.color) {
                                    moves.add(target)
                                }
                            }
                        }
                    }
                }

                // Castling support - only if explicitly allowed to prevent recursion with isKingInCheck
                if (allowCastling && !piece.hasMoved && !isKingInCheck(piece.color)) {
                    val row = if (piece.color == PieceColor.WHITE) 7 else 0
                    // Kingside
                    val kingsideRook = grid[row][7]
                    if (kingsideRook != null && !kingsideRook.hasMoved && kingsideRook.type == PieceType.ROOK) {
                        if (grid[row][5] == null && grid[row][6] == null) {
                            moves.add(Position(row, 6))
                        }
                    }
                    // Queenside
                    val queensideRook = grid[row][0]
                    if (queensideRook != null && !queensideRook.hasMoved && queensideRook.type == PieceType.ROOK) {
                        if (grid[row][1] == null && grid[row][2] == null && grid[row][3] == null) {
                            moves.add(Position(row, 2))
                        }
                    }
                }
            }
        }
        return moves
    }

    private fun addRayMoves(
        moves: MutableList<Position>,
        from: Position,
        color: PieceColor,
        directions: List<Pair<Int, Int>>
    ) {
        for ((dr, dc) in directions) {
            var currR = from.row + dr
            var currC = from.col + dc
            while (currR in 0..7 && currC in 0..7) {
                val target = Position(currR, currC)
                val targetPiece = grid[currR][currC]
                if (targetPiece == null) {
                    moves.add(target)
                } else {
                    if (targetPiece.color != color) {
                        moves.add(target)
                    }
                    break
                }
                currR += dr
                currC += dc
            }
        }
    }

    fun isKingInCheck(color: PieceColor): Boolean {
        var kingPos: Position? = null
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = grid[r][c]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                    kingPos = Position(r, c)
                    break
                }
            }
            if (kingPos != null) break
        }
        if (kingPos == null) return false

        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = grid[r][c]
                if (piece != null && piece.color == opponentColor) {
                    val pseudo = getPseudoLegalMoves(Position(r, c), piece, allowCastling = false)
                    if (kingPos in pseudo) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun executeMove(
        from: Position,
        to: Position,
        promotionPieceType: PieceType = PieceType.QUEEN,
        checkValidation: Boolean = true
    ): ChessMove? {
        val movingPiece = grid[from.row][from.col] ?: return null
        if (checkValidation) {
            val validMoves = getValidMovesFor(from)
            if (to !in validMoves) return null
        }

        var captured = grid[to.row][to.col]
        var isEnPassant = false
        var isCastling = false

        // Handle En Passant capture
        if (movingPiece.type == PieceType.PAWN && to == enPassantTarget && captured == null) {
            val captureRow = if (movingPiece.color == PieceColor.WHITE) to.row + 1 else to.row - 1
            captured = grid[captureRow][to.col]
            grid[captureRow][to.col] = null
            isEnPassant = true
        }

        // Handle Castling Rook movement
        if (movingPiece.type == PieceType.KING && abs(from.col - to.col) == 2) {
            isCastling = true
            val row = from.row
            if (to.col == 6) { // Kingside
                val rook = grid[row][7]
                grid[row][5] = rook?.copy(hasMoved = true)
                grid[row][7] = null
            } else if (to.col == 2) { // Queenside
                val rook = grid[row][0]
                grid[row][3] = rook?.copy(hasMoved = true)
                grid[row][0] = null
            }
        }

        if (captured != null) {
            if (movingPiece.color == PieceColor.WHITE) {
                capturedByWhite.add(captured)
            } else {
                capturedByBlack.add(captured)
            }
        }

        // Set enPassantTarget if 2-step pawn push
        if (movingPiece.type == PieceType.PAWN && abs(from.row - to.row) == 2) {
            val epRow = if (movingPiece.color == PieceColor.WHITE) from.row - 1 else from.row + 1
            enPassantTarget = Position(epRow, from.col)
        } else {
            enPassantTarget = null
        }

        // Handle Pawn Promotion
        var finalPiece = movingPiece.copy(hasMoved = true)
        var promo: PieceType? = null
        if (movingPiece.type == PieceType.PAWN) {
            if ((movingPiece.color == PieceColor.WHITE && to.row == 0) ||
                (movingPiece.color == PieceColor.BLACK && to.row == 7)
            ) {
                finalPiece = ChessPiece(promotionPieceType, movingPiece.color, hasMoved = true)
                promo = promotionPieceType
            }
        }

        grid[to.row][to.col] = finalPiece
        grid[from.row][from.col] = null

        // Switch turn
        turn = if (turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE

        val opponentInCheck = if (checkValidation) isKingInCheck(turn) else false
        val hasMoves = if (checkValidation) hasAnyLegalMoves(turn) else true
        val isCheckmate = checkValidation && opponentInCheck && !hasMoves
        val isStalemate = checkValidation && !opponentInCheck && !hasMoves

        if (isCheckmate) {
            isGameOver = true
            winner = movingPiece.color
        } else if (isStalemate) {
            isGameOver = true
            isDraw = true
        }

        val move = ChessMove(
            from = from,
            to = to,
            piece = movingPiece,
            capturedPiece = captured,
            isCheck = opponentInCheck && !isCheckmate,
            isCheckmate = isCheckmate,
            isCastling = isCastling,
            isEnPassant = isEnPassant,
            promotionType = promo
        )
        moveHistory.add(move)
        return move
    }

    fun getMaterialAdvantage(forColor: PieceColor): Int {
        var whiteSum = 0
        var blackSum = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = grid[r][c] ?: continue
                if (p.color == PieceColor.WHITE) whiteSum += p.type.value
                else blackSum += p.type.value
            }
        }
        return if (forColor == PieceColor.WHITE) (whiteSum - blackSum) / 100 else (blackSum - whiteSum) / 100
    }

    fun getEvaluationScore(): Float {
        if (isGameOver) {
            if (winner == PieceColor.WHITE) return 10.0f
            if (winner == PieceColor.BLACK) return -10.0f
            return 0.0f
        }
        var whiteSum = 0f
        var blackSum = 0f
        for (r in 0..7) {
            for (c in 0..7) {
                val p = grid[r][c] ?: continue
                var valPiece = p.type.value.toFloat()
                if (r in 3..4 && c in 3..4) valPiece += 25f
                if (p.color == PieceColor.WHITE) whiteSum += valPiece else blackSum += valPiece
            }
        }
        val diff = (whiteSum - blackSum) / 100f
        return diff.coerceIn(-10f, 10f)
    }
}

/**
 * Intelligent Chess AI Engine with 4 distinct Persona Bots
 */
data class BotProfile(
    val nameAr: String,
    val nameEn: String,
    val elo: Int,
    val avatar: String,
    val descriptionAr: String,
    val depth: Int,
    val randomness: Float
)

object ChessAiEngine {
    val BOTS = listOf(
        BotProfile("مارتن المبتدئ 👶", "Martin", 400, "👶", "يلعب بعفوية وحماس، مثالي للتدريب والبدايات", 1, 0.65f),
        BotProfile("كريم التكتيكي 🥋", "Karim", 1200, "🥋", "يقتنص الأخطاء التكتيكية والشوكات السريعة", 2, 0.25f),
        BotProfile("الأستاذة نادية 🎓", "Nadia IM", 1800, "🎓", "تخطيط استراتيجي عميق ونهايات دقيقة", 3, 0.08f),
        BotProfile("ماغنوس الخارق 👑", "Magnus GM", 2800, "👑", "أقصى عمق حسابي وقوة محرك خوارزمي عالمي", 4, 0.0f)
    )

    fun findBestMove(board: ChessBoard, botIndex: Int): Pair<Position, Position>? {
        val bot = BOTS.getOrElse(botIndex) { BOTS[1] }
        val legalMoves = board.getAllLegalMoves(board.turn)
        if (legalMoves.isEmpty()) return null

        if (Random.nextFloat() < bot.randomness) {
            val captures = legalMoves.filter { (from, to) -> board.getPiece(to) != null }
            return if (captures.isNotEmpty() && Random.nextBoolean()) captures.random() else legalMoves.random()
        }

        var bestScore = Int.MIN_VALUE
        var bestMove = legalMoves.first()
        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE

        val sortedMoves = legalMoves.sortedByDescending { (from, to) ->
            val victim = board.getPiece(to)?.type?.value ?: 0
            val attacker = board.getPiece(from)?.type?.value ?: 0
            victim * 10 - attacker
        }

        for (move in sortedMoves) {
            val copy = board.copy()
            copy.executeMove(move.first, move.second, checkValidation = false)
            val score = -minimax(copy, bot.depth - 1, -beta, -alpha, copy.turn)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            alpha = max(alpha, bestScore)
            if (alpha >= beta) break
        }
        return bestMove
    }

    private fun minimax(board: ChessBoard, depth: Int, alphaParam: Int, betaParam: Int, currentTurn: PieceColor): Int {
        if (depth == 0 || board.isGameOver) {
            return evaluateBoard(board, currentTurn)
        }

        var alpha = alphaParam
        val legalMoves = board.getAllLegalMoves(board.turn)
        if (legalMoves.isEmpty()) {
            return if (board.isKingInCheck(board.turn)) -100000 + depth else 0
        }

        var maxEval = Int.MIN_VALUE
        for (move in legalMoves) {
            val copy = board.copy()
            copy.executeMove(move.first, move.second, checkValidation = false)
            val evaluation = -minimax(copy, depth - 1, -betaParam, -alpha, copy.turn)
            maxEval = max(maxEval, evaluation)
            alpha = max(alpha, evaluation)
            if (alpha >= betaParam) break
        }
        return maxEval
    }

    private fun evaluateBoard(board: ChessBoard, perspective: PieceColor): Int {
        if (board.isGameOver) {
            if (board.winner == perspective) return 99999
            if (board.winner != null) return -99999
            return 0
        }

        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board.grid[r][c] ?: continue
                var valPiece = p.type.value
                if (r in 3..4 && c in 3..4) valPiece += 30
                else if (r in 2..5 && c in 2..5) valPiece += 15

                if (p.type == PieceType.PAWN) {
                    val adv = if (p.color == PieceColor.WHITE) (6 - r) * 10 else (r - 1) * 10
                    valPiece += adv
                }

                if (p.color == perspective) score += valPiece else score -= valPiece
            }
        }
        return score
    }
}

/**
 * 1,000 Progressive Chess Puzzles Bank
 */
data class ChessPuzzle(
    val levelNumber: Int,
    val titleAr: String,
    val theme: String,
    val ratingElo: Int,
    val story: String,
    val rows: List<String>,
    val playerColor: PieceColor,
    val solutionMoves: List<Pair<Position, Position>>,
    val hintAr: String,
    val explanationAr: String,
    val xpReward: Int = 100
)

object ChessPuzzleRepository {
    private val puzzleThemes = listOf(
        "كش مات في نقلة 🎯",
        "شوكة الفارس الملكية ♞",
        "تضحية الوزير الأسطورية 👑",
        "مات الصف الخلفي 🏰",
        "ربط القطع بالسيخ ♗",
        "هجوم كشف مزدوج ⚡",
        "كش مات خنقي ♟️",
        "تكتيكات نهاية الدور 🏆",
        "تفريغ المربع وإزاحة المدافع ⚔️",
        "هدايا الإغريق والضربات القاتلة 🌟"
    )

    private val basePositions = listOf(
        // 1. Mate in 1 (Queen strike)
        listOf("6k1", "5ppp", "8", "8", "8", "5Q2", "8", "8") to (listOf(Position(5, 5) to Position(1, 5)) to "وجه الوزير لمربع f7 لإنهاء المعركة مباشرة!"),
        // 2. Knight fork
        listOf("r3k3", "8", "8", "4N3", "8", "8", "8", "4K3") to (listOf(Position(3, 4) to Position(1, 2)) to "انقل الحصان إلى c7 للهجوم على الملك والقلعة معاً."),
        // 3. Smothered mate
        listOf("6kr", "5Npp", "8", "8", "8", "8", "8", "4K3") to (listOf(Position(1, 5) to Position(0, 7)) to "كش مات خنقي بحصان في الزاوية!"),
        // 4. Back rank
        listOf("3r2k1", "5ppp", "8", "8", "8", "8", "8", "3R2K1") to (listOf(Position(7, 3) to Position(0, 3)) to "اهجم بالقلعة على d8 مستغلاً حبسة الملك خلف بيادقه."),
        // 5. Bishop skewer
        listOf("8", "4k3", "8", "8", "1q6", "8", "8", "B3K3") to (listOf(Position(7, 0) to Position(3, 4)) to "وجه الفيل إلى الوتر المائل e5 لربط الملك والوزير."),
        // 6. Rook lift & mate
        listOf("1r4k1", "5ppp", "8", "6R1", "8", "8", "8", "4K3") to (listOf(Position(3, 6) to Position(0, 6)) to "القلعة في g8 تحسم المباراة فوراً."),
        // 7. Double check
        listOf("r1b2rk1", "pppp1ppp", "8", "3N4", "8", "5Q2", "PPP2PPP", "R1B1K2R") to (listOf(Position(5, 5) to Position(1, 5)) to "ضربة الوزير في f7 تكشف هجوماً كاسحاً."),
        // 8. Queen sacrifice for mate
        listOf("5rk1", "5ppp", "8", "7Q", "8", "8", "8", "4R1K1") to (listOf(Position(3, 7) to Position(1, 7)) to "تضحية الوزير في h7 تقود لمات سريع لا مفر منه."),
        // 9. Pawn promotion breakthrough
        listOf("8", "4P3", "8", "8", "8", "8", "4k3", "4K3") to (listOf(Position(1, 4) to Position(0, 4)) to "ادفع البيدق للمربع الأخير e8 للترقية إلى وزير!"),
        // 10. Anastasia's Mate
        listOf("1r3rk1", "5Npp", "8", "8", "8", "7R", "8", "4K3") to (listOf(Position(5, 7) to Position(0, 7)) to "القلعة في h8 بالتعاون مع الحصان في e7 تشكل مات أناستازيا الشهير.")
    )

    fun getPuzzleByLevel(level: Int): ChessPuzzle {
        val safeLevel = level.coerceIn(1, 1000)
        val baseIndex = (safeLevel - 1) % basePositions.size
        val themeIndex = (safeLevel - 1) % puzzleThemes.size
        val (rows, solPair) = basePositions[baseIndex]
        val (moves, hintText) = solPair

        val elo = 400 + ((safeLevel - 1) * 2.4).toInt() // Scales smoothly from 400 to 2800 ELO
        val theme = puzzleThemes[themeIndex]

        return ChessPuzzle(
            levelNumber = safeLevel,
            titleAr = "اللغز #$safeLevel : $theme",
            theme = theme,
            ratingElo = elo,
            story = "تقييم التكتيك ($elo ELO) - ابحث عن النقلة التكتيكية الأكثر دقة وحسماً في هذا الموقف!",
            rows = rows,
            playerColor = PieceColor.WHITE,
            solutionMoves = moves,
            hintAr = hintText,
            explanationAr = "أحسنت! هذه النقلة تمنحك تفوقاً حاسماً وتقود إلى كسب مادي أو كش مات 🏆",
            xpReward = 50 + (safeLevel / 5)
        )
    }
}

/**
 * Local P2P Hotspot / Wi-Fi Multiplayer Manager
 */
class LocalP2PChessManager {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    var isConnected = false
    var isHost = false

    suspend fun startHost(port: Int = 8989, onMoveReceived: (String) -> Unit, onClientConnected: () -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                serverSocket = ServerSocket(port)
                isHost = true
                val socket = serverSocket!!.accept()
                clientSocket = socket
                writer = PrintWriter(socket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                isConnected = true
                withContext(Dispatchers.Main) {
                    onClientConnected()
                }

                while (isConnected && !socket.isClosed) {
                    val line = reader?.readLine() ?: break
                    withContext(Dispatchers.Main) {
                        onMoveReceived(line)
                    }
                }
            } catch (_: Exception) {
                isConnected = false
            }
        }

    suspend fun joinHost(hostIp: String, port: Int = 8989, onMoveReceived: (String) -> Unit, onConnected: () -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                val socket = Socket(hostIp, port)
                clientSocket = socket
                writer = PrintWriter(socket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                isConnected = true
                isHost = false
                withContext(Dispatchers.Main) {
                    onConnected()
                }

                while (isConnected && !socket.isClosed) {
                    val line = reader?.readLine() ?: break
                    withContext(Dispatchers.Main) {
                        onMoveReceived(line)
                    }
                }
            } catch (_: Exception) {
                isConnected = false
            }
        }

    fun sendMove(moveMsg: String) {
        Thread {
            try {
                writer?.println(moveMsg)
            } catch (_: Exception) {}
        }.start()
    }

    fun disconnect() {
        try {
            isConnected = false
            writer?.close()
            reader?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val netInterfaces = NetworkInterface.getNetworkInterfaces() ?: return "192.168.43.1"
                val interfaces = Collections.list(netInterfaces)
                for (intf in interfaces) {
                    val inetAddrs = intf?.inetAddresses ?: continue
                    val addrs = Collections.list(inetAddrs)
                    for (addr in addrs) {
                        if (addr != null && !addr.isLoopbackAddress && addr is InetAddress) {
                            val sAddr = addr.hostAddress ?: ""
                            if (sAddr.isNotEmpty() && sAddr.indexOf(':') < 0) {
                                return sAddr
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
            return "192.168.43.1"
        }
    }
}
