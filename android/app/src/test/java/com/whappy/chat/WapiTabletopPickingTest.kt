package com.whappy.chat

import org.junit.Assert.*
import org.junit.Test

class WapiTabletopPickingTest {
    @Test fun headTapHitsPieceEvenWhenRayWouldLandBehindIt() {
        val head = WapiPickSphere(52, 0f, 1f, 0f, .22f)
        assertEquals(52, pickWapiPiece(floatArrayOf(0f, 5f, 4f), floatArrayOf(0f, -1f, -2f), listOf(head)))
    }
    @Test fun frontPieceWinsAndEmptyBoardDoesNotInventAPiece() {
        val near = floatArrayOf(0f, 1f, 5f); val far = floatArrayOf(0f, 1f, -5f)
        assertEquals(2, pickWapiPiece(near, far, listOf(WapiPickSphere(1, 0f, 1f, 0f, .2f), WapiPickSphere(2, 0f, 1f, 2f, .2f))))
        assertNull(pickWapiPiece(near, far, listOf(WapiPickSphere(1, 2f, 1f, 0f, .2f))))
        assertNull(pickWapiPiece(near, near, emptyList()))
    }
}
