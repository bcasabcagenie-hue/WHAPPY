package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WapiGameRulesTest {
    private fun chessBoard() = listOf("♜","♞","♝","♛","♚","♝","♞","♜") + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + listOf("♖","♘","♗","♕","♔","♗","♘","♖")

    @Test fun pawnCanAdvanceButCannotJumpThreeSquares() {
        assertNotNull(WapiGameRules.chessMove(chessBoard(), 52, 36, true))
        assertNull(WapiGameRules.chessMove(chessBoard(), 52, 28, true))
    }

    @Test fun rookCannotCrossItsOwnPawn() {
        assertNull(WapiGameRules.chessMove(chessBoard(), 56, 40, true))
    }

    @Test fun knightCanJump() {
        assertNotNull(WapiGameRules.chessMove(chessBoard(), 57, 42, true))
    }

    @Test fun checkersCaptureRemovesOpponentAndPromotes() {
        val board = MutableList(64) { "" }.also { it[17] = "w"; it[10] = "b" }
        val result = WapiGameRules.checkersMove(board, 17, 3, true)
        assertNotNull(result)
        assertTrue(result!!.captured)
        assertTrue(result.promoted)
        assertFalse(result.board.contains("b"))
    }

    @Test fun mandatoryCaptureBlocksSimpleMove() {
        val board = MutableList(64) { "" }.also { it[42] = "w"; it[33] = "b"; it[46] = "w" }
        assertNull(WapiGameRules.checkersMove(board, 46, 37, true))
    }
}
