package com.whappy.chat

data class WapiWepiSettings(
    val ownerId: String,
    val enabled: Boolean = false,
    val autoReply: Boolean = true,
    val assistantName: String = "WEPI",
    val businessName: String = "",
    val tone: String = "chaleureux",
    val welcomeMessage: String = "Bonjour et merci pour votre message.",
    val instructions: String = "Répondre clairement aux questions commerciales et proposer un échange humain si nécessaire.",
)

/** Android port of the WEPI/Pilotis rules shared by the WAPI web application. */
object WapiPilotis {
    fun reply(message: String, settings: WapiWepiSettings, customerName: String = ""): String {
        val lower = SearchNormalizer.normalize(message)
        val firstName = customerName.trim().substringBefore(' ').takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val business = settings.businessName.trim().ifBlank { "notre activité" }
        val greeting = settings.welcomeMessage.trim().ifBlank { "Bonjour et merci pour votre message." }
        val tone = when (settings.tone) {
            "direct" -> "Je vais à l’essentiel."
            "expert" -> "Je vous apporte une réponse précise."
            else -> "Je suis là pour vous aider avec plaisir."
        }
        return when {
            listOf("bonjour", "bonsoir", "salut", "hello", "coucou").any { it in lower } ->
                "$greeting$firstName Je suis ${settings.assistantName.ifBlank { "WEPI" }}, l’assistant de $business. $tone"
            listOf("prix", "tarif", "coute", "cout", "combien", "budget").any { it in lower } ->
                "Merci pour votre question$firstName. ${settings.assistantName.ifBlank { "WEPI" }} n’a pas encore le tarif exact dans cette conversation. Je vérifie pour vous et un membre de l’équipe peut prendre le relais. $tone"
            listOf("disponible", "disponibilite", "stock", "livraison", "livrer", "rendez vous", "rdv").any { it in lower } ->
                "Merci$firstName, votre demande concernant $business est bien reçue. Je vérifie la disponibilité et nous revenons vers vous rapidement. $tone"
            else -> "$greeting$firstName Votre message est bien reçu par $business. ${settings.instructions.trim().ifBlank { "Je transmets votre demande à l’équipe." }} Un membre de l’équipe peut prendre le relais si une vérification est nécessaire."
        }
    }
}
