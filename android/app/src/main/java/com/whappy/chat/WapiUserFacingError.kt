package com.whappy.chat

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import java.io.IOException

/** Converts infrastructure failures into short messages that a WAPI user can act on. */
internal fun wapiUserFacingError(error: Throwable, action: String): String {
    val causes = generateSequence(error) { current -> current.cause?.takeIf { it !== current } }.toList()
    val functions = causes.filterIsInstance<FirebaseFunctionsException>().firstOrNull()
    if (functions != null) {
        return when (functions.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> "$action est en cours de mise à jour. Réessayez dans quelques instants."
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Votre session WAPI a expiré. Reconnectez-vous puis réessayez."
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Votre compte n’est pas autorisé à effectuer cette action."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "$action ne peut pas continuer dans son état actuel. Actualisez puis réessayez."
            FirebaseFunctionsException.Code.ALREADY_EXISTS -> "$action est déjà en cours."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Trop de demandes ont été envoyées. Patientez un instant puis réessayez."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "Le réseau WAPI ne répond pas pour le moment. Vérifiez votre connexion puis réessayez."
            else -> "$action n’a pas abouti. Réessayez dans quelques instants."
        }
    }
    val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestore != null) {
        return when (firestore.code) {
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Votre session WAPI a expiré. Reconnectez-vous puis réessayez."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "WAPI ne peut pas accéder à ces données avec ce compte."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "La synchronisation WAPI est momentanément indisponible. Vérifiez votre connexion."
            else -> "$action n’a pas pu être synchronisé. Actualisez puis réessayez."
        }
    }
    val storage = causes.filterIsInstance<StorageException>().firstOrNull()
    if (storage != null) return "$action n’a pas pu transférer le média. Vérifiez votre connexion puis réessayez."
    if (causes.any { it is SecurityException }) return "Autorisez les permissions demandées dans les réglages de WAPI."
    if (causes.any { it is IOException }) return "Connexion internet instable. Vérifiez le réseau puis réessayez."
    return "$action n’a pas abouti. Fermez cet écran puis réessayez."
}
