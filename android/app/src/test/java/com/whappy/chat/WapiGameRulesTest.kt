package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test fun pawnPromotesToQueenOnLastRank() {
        val board = MutableList(64) { "" }.also { it[60] = "♔"; it[4] = "♚"; it[8] = "♙" }
        val result = WapiGameRules.chessMove(board, 8, 0, true)
        assertNotNull(result)
        assertTrue(result!!.promoted)
        assertTrue(result.board[0] == "♕")
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

    @Test fun internationalBoardSupportsTenByTenAndChainedCaptures() {
        val board = MutableList(100) { "" }.also {
            it[72] = "w"
            it[63] = "b"
            it[43] = "b"
        }
        val first = WapiGameRules.checkersMove(board, 72, 54, true)
        assertNotNull(first)
        assertTrue(first!!.captured)
        assertTrue(WapiGameRules.hasCheckersCaptureFrom(first.board, 54, true))
        assertTrue(WapiGameRules.checkersCaptureTargets(first.board, 54, true).contains(32))
        val second = WapiGameRules.checkersMove(first.board, 54, 32, true)
        assertNotNull(second)
        assertTrue(second!!.captured)
        assertFalse(second.board.contains("b"))
    }

    @Test fun twoDiceLudoUsesBothDiceAndRewardsDoubles() {
        val regular = WapiGameRules.ludoDiceRoll(2, 5)
        assertEquals(7, regular.total)
        assertFalse(regular.mayLeaveHome)
        assertFalse(regular.grantsExtraTurn)

        val double = WapiGameRules.ludoDiceRoll(6, 6)
        assertEquals(12, double.total)
        assertTrue(double.mayLeaveHome)
        assertTrue(double.grantsExtraTurn)
    }

    @Test fun professionalPoolRackStartsWithoutOverlappingBalls() {
        val balls = initialPoolBalls()
        assertEquals(16, balls.size)
        balls.indices.forEach { first ->
            (first + 1 until balls.size).forEach { second ->
                assertTrue(
                    "balls ${balls[first].id} and ${balls[second].id} overlap",
                    wapiPoolDistance(balls[first], balls[second]) >= WAPI_POOL_BALL_RADIUS * 2f - .015f,
                )
            }
        }
    }
}
