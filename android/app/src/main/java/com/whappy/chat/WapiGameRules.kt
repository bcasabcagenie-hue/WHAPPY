package com.whappy.chat

import kotlin.math.abs

data class WapiMoveResult(
    val board: List<String>,
    val captured: Boolean,
    val promoted: Boolean = false,
)

object WapiGameRules {
    private val whiteChess = setOf("♙", "♖", "♘", "♗", "♕", "♔")
    private val blackChess = setOf("♟", "♜", "♞", "♝", "♛", "♚")

    fun isWhite(piece: String): Boolean = piece in whiteChess || piece in setOf("w", "W")
    fun isBlack(piece: String): Boolean = piece in blackChess || piece in setOf("b", "B")

    fun chessMove(board: List<String>, from: Int, to: Int, whiteTurn: Boolean): WapiMoveResult? {
        if (board.size != 64 || from !in board.indices || to !in board.indices || from == to) return null
        val piece = board[from]
        val target = board[to]
        if (piece.isBlank() || isWhite(piece) != whiteTurn || (target.isNotBlank() && isWhite(target) == whiteTurn)) return null
        if (!rawChessMoveLegal(board, from, to, attackOnly = false)) return null
        val next = board.toMutableList().also { it[to] = piece; it[from] = "" }
        val king = if (whiteTurn) "♔" else "♚"
        val kingIndex = next.indexOf(king)
        if (kingIndex < 0 || squareAttacked(next, kingIndex, byWhite = !whiteTurn)) return null
        return WapiMoveResult(next, target.isNotBlank())
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

    fun checkersMove(board: List<String>, from: Int, to: Int, whiteTurn: Boolean): WapiMoveResult? {
        if (board.size != 64 || from !in board.indices || to !in board.indices || from == to || board[to].isNotBlank()) return null
        val piece = board[from]
        if (piece.isBlank() || isWhite(piece) != whiteTurn) return null
        val fr = from / 8; val fc = from % 8; val tr = to / 8; val tc = to % 8
        val dr = tr - fr; val dc = tc - fc; val king = piece == "W" || piece == "B"
        val directionOkay = king || (piece == "w" && dr < 0) || (piece == "b" && dr > 0)
        if (!directionOkay || abs(dr) != abs(dc) || abs(dr) !in 1..2) return null
        val mandatoryCapture = hasCheckersCapture(board, whiteTurn)
        if (mandatoryCapture && abs(dr) != 2) return null
        var captured = false
        val next = board.toMutableList()
        if (abs(dr) == 2) {
            val middle = ((fr + tr) / 2) * 8 + (fc + tc) / 2
            val middlePiece = board[middle]
            if (middlePiece.isBlank() || isWhite(middlePiece) == whiteTurn) return null
            next[middle] = ""; captured = true
        }
        val promoted = (piece == "w" && tr == 0) || (piece == "b" && tr == 7)
        next[to] = if (promoted) piece.uppercase() else piece
        next[from] = ""
        return WapiMoveResult(next, captured, promoted)
    }

    fun hasCheckersCapture(board: List<String>, whiteTurn: Boolean): Boolean = board.indices.any { from ->
        val piece = board[from]
        if (piece.isBlank() || isWhite(piece) != whiteTurn) false
        else {
            val row = from / 8; val col = from % 8
            listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).any { (dr, dc) ->
                val tr = row + dr; val tc = col + dc
                tr in 0..7 && tc in 0..7 && checkersMoveIgnoringMandatory(board, from, tr * 8 + tc, whiteTurn)
            }
        }
    }

    private fun checkersMoveIgnoringMandatory(board: List<String>, from: Int, to: Int, whiteTurn: Boolean): Boolean {
        val piece = board[from]; val fr = from / 8; val fc = from % 8; val tr = to / 8; val tc = to % 8
        if (board[to].isNotBlank()) return false
        val dr = tr - fr; val king = piece == "W" || piece == "B"
        if (!(king || (piece == "w" && dr < 0) || (piece == "b" && dr > 0))) return false
        val middle = ((fr + tr) / 2) * 8 + (fc + tc) / 2
        return abs(dr) == 2 && abs(tc - fc) == 2 && board[middle].isNotBlank() && isWhite(board[middle]) != whiteTurn
    }
}
