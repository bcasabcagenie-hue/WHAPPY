package com.whappy.chat

import org.junit.Assert.*
import org.junit.Test

class WapiPoolPlayerTest {
    @Test fun scoreboardOnlyCountsThePlayersGroupAndNotTheEight() {
        val balls = initialPoolBalls().map { if (it.id in setOf(2, 9, 15)) it.copy(pocketed = true) else it }
        assertEquals(listOf(1, 3, 4, 5, 6, 7), poolRemainingIds(WapiPoolGroup.SOLIDS, balls))
        assertEquals(listOf(10, 11, 12, 13, 14), poolRemainingIds(WapiPoolGroup.STRIPES, balls))
        assertTrue(poolRemainingIds(WapiPoolGroup.OPEN, balls).isEmpty())
    }
    @Test fun invalidCueCoordinatesCannotBeConfirmed() {
        val balls = initialPoolBalls()
        assertFalse(isValidWapiCuePlacement(Float.NaN, .4f, balls))
        assertFalse(isValidWapiCuePlacement(.2f, Float.POSITIVE_INFINITY, balls))
        assertTrue(isValidWapiCuePlacement(.2f, .4f, balls))
        assertFalse(isValidWapiCuePlacement(.7f, .4f, balls))
    }
    @Test fun profileUsesVerifiedFieldsAndPreservesCustomizedIdentity() {
        val player = poolPlayerCard(mapOf("displayName" to "Cyril", "avatarMode" to "icon", "avatarIcon" to "lion", "points" to 200L, "victories" to 2L), "Vous")
        assertEquals("Cyril", player.name); assertEquals("lion", player.icon)
        assertEquals(200, player.points); assertEquals(2, player.victories)
    }
}
