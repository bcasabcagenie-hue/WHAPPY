package com.whappy.chat

import kotlin.math.abs
import kotlin.math.sqrt

data class WapiMoveResult(
    val board: List<String>,
    val captured: Boolean,
    val promoted: Boolean = false,
)

/**
 * Real rule switches for the checkers board. They are intentionally explicit
 * so a room can later persist the selected rules instead of silently changing
 * the way a match behaves.
 */
data class WapiCheckersRules(
    val mandatoryCapture: Boolean = true,
    val backwardCapture: Boolean = true,
    val flyingKings: Boolean = false,
)

data class WapiLudoDiceRoll(
    val dieOne: Int,
    val dieTwo: Int,
    val total: Int,
    val mayLeaveHome: Boolean,
    val grantsExtraTurn: Boolean,
)

object WapiGameRules {
    private val whiteChess = setOf("♙", "♖", "♘", "♗", "♕", "♔")
    private val blackChess = setOf("♟", "♜", "♞", "♝", "♛", "♚")

    fun isWhite(piece: String): Boolean = piece in whiteChess || piece in setOf("w", "W")
    fun isBlack(piece: String): Boolean = piece in blackChess || piece in setOf("b", "B")

    /** WAPI two-dice Ludo preset: totals move one pawn, any six opens
     * the house and doubles earn another roll. */
    fun ludoDiceRoll(dieOne: Int, dieTwo: Int): WapiLudoDiceRoll {
        require(dieOne in 1..6 && dieTwo in 1..6)
        val total = dieOne + dieTwo
        return WapiLudoDiceRoll(
            dieOne = dieOne,
            dieTwo = dieTwo,
            total = total,
            mayLeaveHome = dieOne == 6 || dieTwo == 6 || total == 6,
            grantsExtraTurn = dieOne == dieTwo,
        )
    }

    fun chessMove(board: List<String>, from: Int, to: Int, whiteTurn: Boolean): WapiMoveResult? {
        if (board.size != 64 || from !in board.indices || to !in board.indices || from == to) return null
        val piece = board[from]
        val target = board[to]
        if (piece.isBlank() || isWhite(piece) != whiteTurn || (target.isNotBlank() && isWhite(target) == whiteTurn)) return null
        if (target == "♔" || target == "♚") return null
        if (!rawChessMoveLegal(board, from, to, attackOnly = false)) return null
        val destinationRow = to / 8
        val promoted = (piece == "♙" && destinationRow == 0) || (piece == "♟" && destinationRow == 7)
        val movedPiece = when {
            promoted && whiteTurn -> "♕"
            promoted -> "♛"
            else -> piece
        }
        val next = board.toMutableList().also { it[to] = movedPiece; it[from] = "" }
        val king = if (whiteTurn) "♔" else "♚"
        val kingIndex = next.indexOf(king)
        if (kingIndex < 0 || squareAttacked(next, kingIndex, byWhite = !whiteTurn)) return null
        return WapiMoveResult(next, target.isNotBlank(), promoted)
    }

    fun isChessKingInCheck(board: List<String>, whiteTurn: Boolean): Boolean {
        if (board.size != 64) return false
        val kingIndex = board.indexOf(if (whiteTurn) "♔" else "♚")
        return kingIndex >= 0 && squareAttacked(board, kingIndex, byWhite = !whiteTurn)
    }

    fun hasAnyLegalMove(
        board: List<String>,
        whiteTurn: Boolean,
        checkers: Boolean,
        checkersRules: WapiCheckersRules = WapiCheckersRules(),
    ): Boolean = board.indices.any { from ->
        board[from].isNotBlank() && isWhite(board[from]) == whiteTurn && board.indices.any { to ->
            if (checkers) checkersMove(board, from, to, whiteTurn, checkersRules) != null
            else chessMove(board, from, to, whiteTurn) != null
        }
    }

    private fun squareAttacked(board: List<String>, square: Int, byWhite: Boolean): Boolean = board.indices.any { index ->
        val piece = board[index]
        piece.isNotBlank() && isWhite(piece) == byWhite && rawChessMoveLegal(board, index, square, attackOnly = true)
    }

    private fun rawChessMoveLegal(board: List<String>, from: Int, to: Int, attackOnly: Boolean): Boolean {
        val piece = board[from]
        val target = board[to]
        val fr = from / 8; val fc = from % 8; val tr = to / 8; val tc = to % 8
        val dr = tr - fr; val dc = tc - fc; val adr = abs(dr); val adc = abs(dc)
        return when (piece) {
            "♘", "♞" -> (adr == 2 && adc == 1) || (adr == 1 && adc == 2)
            "♖", "♜" -> (dr == 0 || dc == 0) && pathClear(board, from, to)
            "♗", "♝" -> adr == adc && pathClear(board, from, to)
            "♕", "♛" -> (adr == adc || dr == 0 || dc == 0) && pathClear(board, from, to)
            "♔", "♚" -> adr <= 1 && adc <= 1
            "♙", "♟" -> {
                val direction = if (piece == "♙") -1 else 1
                val startRow = if (piece == "♙") 6 else 1
                if (attackOnly) dr == direction && adc == 1
                else if (dc == 0 && target.isBlank()) dr == direction || (fr == startRow && dr == direction * 2 && board[(fr + direction) * 8 + fc].isBlank())
                else dr == direction && adc == 1 && target.isNotBlank()
            }
            else -> false
        }
    }

    private fun pathClear(board: List<String>, from: Int, to: Int): Boolean {
        val fr = from / 8; val fc = from % 8; val tr = to / 8; val tc = to % 8
        val rowStep = (tr - fr).compareTo(0); val colStep = (tc - fc).compareTo(0)
        var row = fr + rowStep; var col = fc + colStep
        while (row != tr || col != tc) {
            if (board[row * 8 + col].isNotBlank()) return false
            row += rowStep; col += colStep
        }
        return true
    }

    fun checkersMove(
        board: List<String>,
        from: Int,
        to: Int,
        whiteTurn: Boolean,
        rules: WapiCheckersRules = WapiCheckersRules(),
    ): WapiMoveResult? {
        return applyCheckersMove(board, from, to, whiteTurn, rules,
            rules.mandatoryCapture && hasCheckersCapture(board, whiteTurn, rules))
    }

    private fun applyCheckersMove(board: List<String>, from: Int, to: Int, whiteTurn: Boolean,
        rules: WapiCheckersRules, mandatoryCapture: Boolean): WapiMoveResult? {
        val dimension = checkersDimension(board) ?: return null
        if (from !in board.indices || to !in board.indices || from == to || board[to].isNotBlank()) return null
        val piece = board[from]
        if (piece.isBlank() || isWhite(piece) != whiteTurn) return null
        val path = checkersPath(board, from, to, whiteTurn, rules) ?: return null
        if (mandatoryCapture && path < 0) return null
        val next = board.toMutableList()
        val captured = path >= 0
        if (captured) next[path] = ""
        val tr = to / dimension
        val promoted = (piece == "w" && tr == 0) || (piece == "b" && tr == dimension - 1)
        next[to] = if (promoted) piece.uppercase() else piece
        next[from] = ""
        return WapiMoveResult(next, captured, promoted)
    }

    fun hasCheckersCapture(
        board: List<String>,
        whiteTurn: Boolean,
        rules: WapiCheckersRules = WapiCheckersRules(),
    ): Boolean = board.indices.any { from ->
        val piece = board[from]
        if (piece.isBlank() || isWhite(piece) != whiteTurn) false
        else {
            board.indices.any { to -> checkersMoveIgnoringMandatory(board, from, to, whiteTurn, rules) }
        }
    }

    fun hasCheckersCaptureFrom(
        board: List<String>,
        from: Int,
        whiteTurn: Boolean,
        rules: WapiCheckersRules = WapiCheckersRules(),
    ): Boolean {
        if (from !in board.indices || board[from].isBlank() || isWhite(board[from]) != whiteTurn) return false
        return board.indices.any { to -> checkersMoveIgnoringMandatory(board, from, to, whiteTurn, rules) }
    }

    fun checkersCaptureTargets(
        board: List<String>,
        from: Int,
        whiteTurn: Boolean,
        rules: WapiCheckersRules = WapiCheckersRules(),
    ): List<Int> {
        if (from !in board.indices || board[from].isBlank() || isWhite(board[from]) != whiteTurn) return emptyList()
        return board.indices.filter { to ->
            checkersPath(board, from, to, whiteTurn, rules)?.let { captured -> captured >= 0 } == true
        }
    }

    /** Native opponent with transparent, local difficulty levels. */
    fun bestMove(
        board: List<String>,
        whiteTurn: Boolean,
        checkers: Boolean,
        difficulty: String = "medium",
        checkersRules: WapiCheckersRules = WapiCheckersRules(),
    ): Pair<Int, Int>? {
        val mandatoryCapture = checkers && checkersRules.mandatoryCapture && hasCheckersCapture(board, whiteTurn, checkersRules)
        val moves = buildList {
            board.indices.filter { index ->
                board[index].isNotBlank() && isWhite(board[index]) == whiteTurn
            }.forEach { from ->
                board.indices.forEach { to ->
                    val result = if (checkers) applyCheckersMove(board, from, to, whiteTurn, checkersRules, mandatoryCapture) else chessMove(board, from, to, whiteTurn)
                    if (result != null) add(from to to to result)
                }
            }
        }
        if (moves.isEmpty()) return null
        if (difficulty == "easy") {
            // Stable but deliberately imperfect choice so Easy remains humane.
            return moves[(board.hashCode().toLong() and 0x7fffffffL).rem(moves.size).toInt()].first
        }
        fun tacticalScore(move: Pair<Pair<Int, Int>, WapiMoveResult>) =
            (if (move.second.captured) 80 else 0) + (if (move.second.promoted) 110 else 0) + move.first.second
        if (difficulty == "medium") return moves.maxByOrNull(::tacticalScore)?.first

        fun material(value: List<String>, sideWhite: Boolean): Int = value.sumOf { piece ->
            if (piece.isBlank() || isWhite(piece) != sideWhite) 0
            else when (piece) {
                "♕", "♛" -> 90
                "♖", "♜" -> 50
                "♗", "♝", "♘", "♞" -> 32
                "♙", "♟", "w", "b" -> 10
                "W", "B" -> 28
                "♔", "♚" -> 1000
                else -> 0
            }
        }
        fun positionalScore(result: WapiMoveResult): Int {
            val own = material(result.board, whiteTurn)
            val opponent = material(result.board, !whiteTurn)
            return (own - opponent) * 10 + tacticalScore(Pair(0 to 0, result))
        }
        if (difficulty == "hard") return moves.maxByOrNull { positionalScore(it.second) }?.first

        // Ultra evaluates the opponent's strongest immediate reply as well.
        return moves.maxByOrNull { candidate ->
            val reply = bestMove(candidate.second.board, !whiteTurn, checkers, "hard", checkersRules)
            val afterReply = if (reply == null) candidate.second.board else {
                val next = if (checkers) checkersMove(candidate.second.board, reply.first, reply.second, !whiteTurn, checkersRules)
                else chessMove(candidate.second.board, reply.first, reply.second, !whiteTurn)
                next?.board ?: candidate.second.board
            }
            positionalScore(candidate.second) + (material(afterReply, whiteTurn) - material(afterReply, !whiteTurn)) * 6
        }?.first
    }

    private fun checkersMoveIgnoringMandatory(
        board: List<String>,
        from: Int,
        to: Int,
        whiteTurn: Boolean,
        rules: WapiCheckersRules,
    ): Boolean = checkersPath(board, from, to, whiteTurn, rules)?.let { it >= 0 } == true

    /** Returns -1 for a normal move, the captured square for a capture, null otherwise. */
    private fun checkersPath(
        board: List<String>,
        from: Int,
        to: Int,
        whiteTurn: Boolean,
        rules: WapiCheckersRules,
    ): Int? {
        val dimension = checkersDimension(board) ?: return null
        if (from !in board.indices || to !in board.indices || from == to || board[to].isNotBlank()) return null
        val piece = board[from]
        if (piece.isBlank() || isWhite(piece) != whiteTurn) return null
        val fr = from / dimension; val fc = from % dimension; val tr = to / dimension; val tc = to % dimension
        val dr = tr - fr; val dc = tc - fc; val distance = abs(dr)
        if (distance == 0 || distance != abs(dc)) return null
        val king = piece == "W" || piece == "B"
        val captureMove = distance >= 2
        val forward = (piece == "w" && dr < 0) || (piece == "b" && dr > 0)
        if (!king && !forward && !(captureMove && rules.backwardCapture)) return null
        if (!king && distance > 2) return null
        if (king && distance > 2 && !rules.flyingKings) return null

        val rowStep = dr.compareTo(0); val colStep = dc.compareTo(0)
        var row = fr + rowStep; var col = fc + colStep
        val occupied = mutableListOf<Int>()
        while (row != tr || col != tc) {
            val index = row * dimension + col
            if (board[index].isNotBlank()) occupied += index
            row += rowStep; col += colStep
        }
        if (occupied.isEmpty()) return if (!captureMove || (king && rules.flyingKings)) -1 else null
        if (occupied.size != 1) return null
        val captured = occupied.single()
        return if (isWhite(board[captured]) != whiteTurn && (king || distance == 2)) captured else null
    }

    private fun checkersDimension(board: List<String>): Int? {
        val dimension = sqrt(board.size.toDouble()).toInt()
        return dimension.takeIf { it in setOf(8, 10) && it * it == board.size }
    }
}
