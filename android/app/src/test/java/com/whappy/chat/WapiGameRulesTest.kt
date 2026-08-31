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

    @Test fun poolPhysicsPocketsObjectBallButRespotsCueBall() {
        val objectFrame = advanceWapiPoolFrame(listOf(WapiPoolBall(3, .055f, .08f)))
        assertTrue(objectFrame.balls.single().pocketed)
        assertEquals(1, objectFrame.newlyPocketed)

        val cueFrame = advanceWapiPoolFrame(listOf(WapiPoolBall(0, .055f, .08f)))
        assertFalse(cueFrame.balls.single().pocketed)
        assertTrue(cueFrame.cueScratch)
        assertTrue(kotlin.math.abs(cueFrame.balls.single().x - .23f) < .0001f)
    }

    @Test fun poolPocketShelfPullsSlowBallTowardTheThroat() {
        val nearCorner = WapiPoolBall(
            id = 4,
            x = .055f + .40f / WAPI_POOL_WORLD_WIDTH,
            y = .07f,
        )
        val frame = advanceWapiPoolFrame(listOf(nearCorner), deltaSeconds = .018f)
        assertTrue(frame.balls.single().vx < 0f)
        assertFalse(frame.balls.single().pocketed)
    }

    @Test fun poolRollingResistanceIsStableAcrossFrameSteps() {
        val moving = WapiPoolBall(0, .50f, .50f, vx = .70f, vy = .20f)
        val oneStep = advanceWapiPoolFrame(listOf(moving), deltaSeconds = .036f).balls.single()
        val firstHalf = advanceWapiPoolFrame(listOf(moving), deltaSeconds = .018f).balls.single()
        val twoSteps = advanceWapiPoolFrame(listOf(firstHalf), deltaSeconds = .018f).balls.single()
        assertEquals(oneStep.vx, twoSteps.vx, .0015f)
        assertEquals(oneStep.vy, twoSteps.vy, .0015f)
    }

    @Test fun poolCollisionTransfersMomentumWithoutOverlapping() {
        val first = WapiPoolBall(0, .40f, .50f, vx = .8f)
        val second = WapiPoolBall(1, .40f + (WAPI_POOL_BALL_RADIUS * 1.8f / WAPI_POOL_WORLD_WIDTH), .50f)
        val frame = advanceWapiPoolFrame(listOf(first, second), deltaSeconds = 0f)
        assertTrue(frame.collisionEnergy > 0f)
        assertTrue(frame.balls[1].vx > 0f)
        assertTrue(wapiPoolDistance(frame.balls[0], frame.balls[1]) >= WAPI_POOL_BALL_RADIUS * 2f - .002f)
    }

    @Test fun poolHighPowerFrameCannotTunnelThroughTargetBall() {
        val cue = WapiPoolBall(0, .25f, .50f, vx = 12f / WAPI_POOL_WORLD_WIDTH)
        val target = WapiPoolBall(5, .25f + .42f / WAPI_POOL_WORLD_WIDTH, .50f)
        val frame = advanceWapiPoolFrame(listOf(cue, target), deltaSeconds = .05f)
        assertEquals(5, frame.cueContactBallId)
        assertTrue(frame.collisionEnergy > 0f)
        assertTrue(frame.balls[1].vx > 0f)
    }

    @Test fun poolFollowSpinChangesCueBallAfterContact() {
        val targetX = .40f + (WAPI_POOL_BALL_RADIUS * 1.8f / WAPI_POOL_WORLD_WIDTH)
        val neutral = advanceWapiPoolFrame(
            listOf(WapiPoolBall(0, .40f, .50f, vx = .8f), WapiPoolBall(1, targetX, .50f)),
            deltaSeconds = 0f,
        )
        val follow = advanceWapiPoolFrame(
            listOf(WapiPoolBall(0, .40f, .50f, vx = .8f, followSpin = 1f), WapiPoolBall(1, targetX, .50f)),
            deltaSeconds = 0f,
        )
        assertEquals(1, follow.cueContactBallId)
        assertTrue(follow.balls.first().vx > neutral.balls.first().vx)
    }

    @Test fun poolBallInHandRejectsOverlapAndAcceptsClearHeadString() {
        val balls = listOf(WapiPoolBall(0, .23f, .50f), WapiPoolBall(1, .30f, .50f))
        assertFalse(isValidWapiCuePlacement(.30f, .50f, balls))
        assertTrue(isValidWapiCuePlacement(.18f, .30f, balls))
        assertFalse(isValidWapiCuePlacement(.60f, .30f, balls))
    }

    @Test fun poolAiPlansARealTargetToPocketRoute() {
        val plan = planWapiPoolAiShot(listOf(WapiPoolBall(0, .22f, .50f), WapiPoolBall(4, .60f, .50f)))
        assertNotNull(plan)
        assertEquals(4, plan!!.targetBallId)
        assertTrue(plan.power in 34..92)
    }

    @Test fun eightBallAssignsSolidsAfterFirstLegalPocket() {
        val before = listOf(WapiPoolBall(0, .22f, .50f), WapiPoolBall(2, .55f, .50f), WapiPoolBall(10, .68f, .60f), WapiPoolBall(8, .70f, .50f))
        val result = resolveWapiPoolShot(before, WapiPoolGroup.OPEN, WapiPoolGroup.OPEN, firstContactBallId = 2, scratched = false, pocketedBallIds = setOf(2))
        assertEquals(WapiPoolGroup.SOLIDS, result.shooterGroup)
        assertEquals(WapiPoolGroup.STRIPES, result.opponentGroup)
        assertTrue(result.keepTurn)
        assertFalse(result.foul)
    }

    @Test fun eightBallWrongFirstGroupIsAFoul() {
        val before = listOf(WapiPoolBall(0, .22f, .50f), WapiPoolBall(3, .55f, .50f), WapiPoolBall(11, .68f, .60f), WapiPoolBall(8, .70f, .50f))
        val result = resolveWapiPoolShot(before, WapiPoolGroup.SOLIDS, WapiPoolGroup.STRIPES, firstContactBallId = 11, scratched = false, pocketedBallIds = emptySet())
        assertTrue(result.foul)
        assertFalse(result.keepTurn)
    }

    @Test fun eightBallEarlyBlackLosesButLegalBlackWins() {
        val early = resolveWapiPoolShot(
            listOf(WapiPoolBall(0, .22f, .50f), WapiPoolBall(3, .55f, .50f), WapiPoolBall(8, .70f, .50f)),
            WapiPoolGroup.SOLIDS, WapiPoolGroup.STRIPES, firstContactBallId = 8, scratched = false, pocketedBallIds = setOf(8),
        )
        assertEquals(false, early.shooterWon)

        val cleared = resolveWapiPoolShot(
            listOf(WapiPoolBall(0, .22f, .50f), WapiPoolBall(3, .55f, .50f, pocketed = true), WapiPoolBall(8, .70f, .50f)),
            WapiPoolGroup.SOLIDS, WapiPoolGroup.STRIPES, firstContactBallId = 8, scratched = false, pocketedBallIds = setOf(8),
        )
        assertEquals(true, cleared.shooterWon)
    }
}
