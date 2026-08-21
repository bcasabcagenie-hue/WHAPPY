package com.whappy.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class WapiPilotisTest {
    private val settings = WapiWepiSettings(
        ownerId = "owner",
        enabled = true,
        assistantName = "WEPI",
        businessName = "BCA SA",
        tone = "expert",
    )

    @Test
    fun answersPriceQuestionsWithoutInventingATariff() {
        val reply = WapiPilotis.reply("Quel est le prix ?", settings, "Cyril Bokilo")
        assertTrue(reply.contains("tarif exact"))
        assertTrue(reply.contains("membre de l’équipe"))
    }

    @Test
    fun usesSyncedBusinessIdentityForGreetings() {
        val reply = WapiPilotis.reply("Bonjour", settings, "Cyril Bokilo")
        assertTrue(reply.contains("WEPI"))
        assertTrue(reply.contains("BCA SA"))
    }
}
