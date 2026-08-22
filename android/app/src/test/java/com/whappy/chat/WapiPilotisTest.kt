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
        assertTrue(reply.contains("n’invente pas de tarif"))
        assertTrue(reply.contains("catalogue prix"))
    }

    @Test
    fun usesSyncedBusinessIdentityForGreetings() {
        val reply = WapiPilotis.reply("Bonjour", settings, "Cyril Bokilo")
        assertTrue(reply.contains("WEPI"))
        assertTrue(reply.contains("BCA SA"))
        assertTrue(reply.contains("chatbot Pilotis"))
    }

    @Test
    fun routesWapiRequestsToPilotisCapabilities() {
        val response = WapiPilotis.response("Je veux publier une story avec une image", settings)
        assertTrue(response.intent == WapiPilotisIntent.STORIES)
        assertTrue(response.text.contains("profil"))
        assertTrue(response.text.contains("publiez"))
    }
}
