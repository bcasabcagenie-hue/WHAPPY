package com.whappy.chat

data class WapiWepiSettings(
    val ownerId: String,
    val enabled: Boolean = true,
    val autoReply: Boolean = true,
    val assistantName: String = "Assistant WAPI",
    val businessName: String = "",
    val tone: String = "chaleureux",
    val welcomeMessage: String = "Bonjour et merci pour votre message.",
    val instructions: String = "Répondre clairement aux questions commerciales et proposer un échange humain si nécessaire.",
    val salesAutomation: Boolean = false,
    val captureOrderRequests: Boolean = true,
    val humanHandoff: Boolean = true,
    val deliveryPolicy: String = "Confirmer la zone, le délai et les frais avec le client avant toute commande.",
)

enum class WapiPilotisIntent {
    GREETING, IDENTITY, PRICE, AVAILABILITY, MESSAGES, STORIES, BUSINESS,
    GROUPS, GAMES, CALLS, RADIO, PRIVACY, HELP, OTHER,
}

enum class WapiPilotisAction {
    OPEN_MESSAGES, OPEN_BUSINESS,
}

data class WapiPilotisResponse(
    val text: String,
    val intent: WapiPilotisIntent,
    val actions: List<WapiPilotisAction> = emptyList(),
)

/** Internal WAPI AI implementation kept in parity with lib/whappy-wepi.ts. */
object WapiPilotis {
    private fun hasAny(text: String, vararg values: String): Boolean = values.any { value ->
        val words = value.split(' ').filter(String::isNotBlank)
        words.isNotEmpty() && words.all { word -> word in text }
    }

    fun classify(message: String): WapiPilotisIntent {
        val lower = SearchNormalizer.normalize(message)
        return when {
            hasAny(lower, "bonjour", "bonsoir", "salut", "hello", "coucou") -> WapiPilotisIntent.GREETING
            hasAny(lower, "qui es tu", "qui es-tu", "pilotis", "wepi", "intelligence artificielle") -> WapiPilotisIntent.IDENTITY
            hasAny(lower, "prix", "tarif", "coute", "cout", "combien", "budget") -> WapiPilotisIntent.PRICE
            hasAny(lower, "disponible", "disponibilite", "stock", "livraison", "livrer", "rendez vous", "rdv") -> WapiPilotisIntent.AVAILABILITY
            hasAny(lower, "message", "repondre", "reponds", "transfert", "transferer", "vu", "lu", "conversation") -> WapiPilotisIntent.MESSAGES
            hasAny(lower, "story", "stories", "statut", "photo", "video", "publier") -> WapiPilotisIntent.STORIES
            hasAny(lower, "business", "publicite", "pub", "campagne", "booster", "region", "client") -> WapiPilotisIntent.BUSINESS
            hasAny(lower, "groupe", "groupes", "communaute", "administrateur") -> WapiPilotisIntent.GROUPS
            hasAny(lower, "jeu", "jeux", "billard", "echec", "dames", "poker", "joueur") -> WapiPilotisIntent.GAMES
            hasAny(lower, "appel", "audio", "video", "live", "haut parleur", "micro") -> WapiPilotisIntent.CALLS
            hasAny(lower, "radio", "podcast", "direct", "emission", "ecouter") -> WapiPilotisIntent.RADIO
            hasAny(lower, "stockage", "cache", "donnees", "chiffre", "confidentialite", "securite", "cloud") -> WapiPilotisIntent.PRIVACY
            hasAny(lower, "aide", "help", "faire", "fonctionnalite", "fonctionnalites") -> WapiPilotisIntent.HELP
            else -> WapiPilotisIntent.OTHER
        }
    }

    private fun isFollowUp(message: String): Boolean {
        val lower = SearchNormalizer.normalize(message)
        return lower.length <= 48 && (
            lower in setOf("oui", "ok", "daccord", "merci", "comment", "et apres", "et pour ca", "et pour cela") ||
                lower.startsWith("et pour ") || lower.startsWith("et si ") || lower.startsWith("donc ") ||
                lower.startsWith("je veux ") || lower.startsWith("fais ") || lower.startsWith("ouvre ")
            )
    }

    fun response(
        message: String,
        settings: WapiWepiSettings,
        customerName: String = "",
        previousIntent: WapiPilotisIntent? = null,
    ): WapiPilotisResponse {
        val classified = classify(message)
        val intent = if (classified == WapiPilotisIntent.OTHER && previousIntent != null && isFollowUp(message)) previousIntent else classified
        val firstName = customerName.trim().substringBefore(' ').takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val business = settings.businessName.trim().ifBlank { "notre activité" }
        val greeting = settings.welcomeMessage.trim().ifBlank { "Bonjour et merci pour votre message." }
        val assistant = settings.assistantName.trim().ifBlank { "Assistant WAPI" }
        val instructions = settings.instructions.trim().ifBlank { "Je transmets votre demande à l’équipe." }
        val tone = when (settings.tone) {
            "direct" -> "Je vais à l’essentiel."
            "expert" -> "Je vous apporte une réponse précise."
            else -> "Je suis là pour vous aider avec plaisir."
        }
        val text = when (intent) {
            WapiPilotisIntent.GREETING -> "$greeting$firstName Je suis $assistant, l’IA intégrée à WAPI pour $business. $tone"
            WapiPilotisIntent.IDENTITY -> "Je suis $assistant, l’IA intégrée à WAPI. Je peux vous aider dans les messages, les groupes, les Stories, Business, les appels, la radio et les jeux. Je reste transparent : je n’invente ni prix, ni disponibilité, ni action effectuée."
            WapiPilotisIntent.PRICE -> "Merci pour votre question$firstName. Je n’invente pas de tarif : aucun catalogue prix n’est configuré pour $business. Ajoutez vos offres dans Business ou demandez le relais d’un membre de l’équipe. $tone"
            WapiPilotisIntent.AVAILABILITY -> "Merci$firstName. Je peux enregistrer votre demande pour $business, mais je ne peux pas confirmer un stock ou une livraison sans donnée connectée. Un membre de l’équipe doit valider la disponibilité. $tone"
            WapiPilotisIntent.MESSAGES -> "Je peux vous guider pour répondre, citer un message, le transférer, suivre les vues d’un groupe ou ouvrir la conversation concernée. Dites-moi l’action à faire et le contact visé."
            WapiPilotisIntent.STORIES -> "Pour une Story WAPI : ouvrez votre profil, choisissez Ajouter, puis Image, Vidéo, Texte ou un audio/podcast. Vérifiez l’aperçu et publiez. Les Stories restent rattachées au profil, elles ne sont pas affichées comme un fil public."
            WapiPilotisIntent.BUSINESS -> "Dans WAPI Business, créez une campagne, choisissez la région ciblée, le budget et la durée, puis envoyez-la en validation. Une publicité doit être identifiée comme telle et ne sera diffusée que dans la zone choisie."
            WapiPilotisIntent.GROUPS -> "Je peux vous aider à organiser un groupe ou une communauté : rôles administrateur, annonce, sondage, événement, fichier et modération avec validation humaine."
            WapiPilotisIntent.GAMES -> "Les jeux WAPI doivent ouvrir une vraie partie séparée : solo contre IA, duel en ligne, tour par tour synchronisé et audio de partie. Choisissez le jeu et le mode pour lancer une salle réelle."
            WapiPilotisIntent.CALLS -> "Pour un appel WAPI, utilisez Audio ou Vidéo puis activez le haut-parleur depuis l’écran d’appel. Les appels de groupe nécessitent une salle média active ; si elle n’est pas disponible, je vous le signale au lieu de simuler des participants."
            WapiPilotisIntent.RADIO -> "La Radio WAPI permet d’écouter un direct, de changer de station et de retrouver les podcasts publiés. Un épisode doit posséder une vraie source audio cloud avant d’être annoncé comme disponible."
            WapiPilotisIntent.PRIVACY -> "WAPI garde les messages récents en cache local pour afficher la conversation rapidement, synchronise les données cloud quand la connexion revient et ne présente jamais un cache comme une donnée confirmée. Les messages protégés restent chiffrés côté conversation."
            WapiPilotisIntent.HELP -> "Je suis l’IA de WAPI. Essayez : « comment publier une Story ? », « créer une campagne régionale », « ouvrir un jeu en ligne », « lancer un direct radio » ou « gérer mon groupe »."
            WapiPilotisIntent.OTHER -> "$greeting$firstName J’ai reçu votre demande pour $business. $instructions Pour une réponse précise, indiquez l’action WAPI, le contact ou le service concerné. $tone"
        }
        val actions = when (intent) {
            WapiPilotisIntent.MESSAGES -> listOf(WapiPilotisAction.OPEN_MESSAGES)
            WapiPilotisIntent.BUSINESS -> listOf(WapiPilotisAction.OPEN_BUSINESS)
            else -> emptyList()
        }
        return WapiPilotisResponse(text, intent, actions)
    }

    fun reply(message: String, settings: WapiWepiSettings, customerName: String = "", previousIntent: WapiPilotisIntent? = null): String = response(message, settings, customerName, previousIntent).text
}
