package com.whappy.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSessionPolicyTest {
    @Test
    fun restoresStoredNameImmediately() {
        assertEquals(
            "Cyril Bokilo",
            AccountSessionPolicy.displayName("  Cyril Bokilo  ", "+242061234567", 0L, 1_000L),
        )
    }

    @Test
    fun keepsProfileStepForBrandNewAccount() {
        assertEquals(
            "",
            AccountSessionPolicy.displayName("", "+242061234567", 1_000L, 2_000L),
        )
    }

    @Test
    fun neverBlocksAnOlderAuthenticatedAccount() {
        assertEquals(
            "Membre 4567",
            AccountSessionPolicy.displayName("", "+242061234567", 0L, 600_000L),
        )
    }
}
