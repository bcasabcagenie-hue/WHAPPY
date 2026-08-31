package com.whappy.chat
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WapiPlayableAiTest {
    @Test(timeout = 6000) fun everyStrategyDifficultyRepliesWithLegalMove() {
        val chess = listOf("♜","♞","♝","♛","♚","♝","♞","♜") + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + listOf("♖","♘","♗","♕","♔","♗","♘","♖")
        val checkers = List(100) { i -> if ((i / 10 + i % 10) % 2 == 0) "" else if (i / 10 < 4) "b" else if (i / 10 >= 6) "w" else "" }
        for (difficulty in listOf("easy", "medium", "hard", "ultra")) {
            for (isCheckers in listOf(false, true)) {
                val board = if (isCheckers) checkers else chess
                val move = WapiGameRules.bestMove(board, false, isCheckers, difficulty)!!
                assertNotNull(if (isCheckers) WapiGameRules.checkersMove(board, move.first, move.second, false) else WapiGameRules.chessMove(board, move.first, move.second, false))
            }
        }
    }
    @Test(timeout = 10000) fun poolBreakAndAiReplyBothSettle() {
        fun shot(balls: List<WapiPoolBall>, angle: Float, power: Int): List<WapiPoolBall> {
            val speed = 3.4f + power / 100f * 7.4f
            var next = balls.map { if (it.id == 0) it.copy(vx = cos(angle)*speed/WAPI_POOL_WORLD_WIDTH, vy = sin(angle)*speed/WAPI_POOL_WORLD_HEIGHT) else it }
            repeat(1800) { next = advanceWapiPoolFrame(next, 1f/60).balls }
            assertFalse("Physics never returned control to the player", wapiPoolBallsMoving(next))
            assertTrue(next.all { it.x.isFinite() && it.y.isFinite() })
            return next
        }
        val broken = shot(initialPoolBalls(), 0f, 75)
        val plan = planWapiPoolAiShot(broken, wapiPoolTargetIds(WapiPoolGroup.OPEN, broken))!!
        shot(broken, plan.angle, plan.power)
    }
}
