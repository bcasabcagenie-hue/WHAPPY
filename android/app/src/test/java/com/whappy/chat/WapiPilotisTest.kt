package com.whappy.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class WapiPilotisTest {
    private val settings = WapiWepiSettings(
        ownerId = "owner",
        enabled = true,
        assistantName = "WIA",
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
        assertTrue(reply.contains("WIA"))
        assertTrue(reply.contains("BCA SA"))
        assertTrue(reply.contains("l’IA intégrée à WAPI"))
    }

    @Test
    fun routesWapiRequestsToPilotisCapabilities() {
        val response = WapiPilotis.response("Je veux publier une story avec une image", settings)
        assertTrue(response.intent == WapiPilotisIntent.STORIES)
        assertTrue(response.text.contains("profil"))
        assertTrue(response.text.contains("publiez"))
    }

    @Test
    fun keepsShortFollowUpsInConversationContext() {
        val response = WapiPilotis.response("Et pour ça ?", settings, previousIntent = WapiPilotisIntent.BUSINESS)
        assertTrue(response.intent == WapiPilotisIntent.BUSINESS)
        assertTrue(response.actions.contains(WapiPilotisAction.OPEN_BUSINESS))
    }
}
