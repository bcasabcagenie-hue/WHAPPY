# Marketplace Business — contrôle qualité

## Parcours livré

- Consultation des annonces active pour tout compte WAPI authentifié.
- Publication réservée à une page Business active, contrôlée par la fonction serveur `wapiCommerce`.
- Annonce avec photo optimisée depuis la galerie, description, catégorie, lieu et cinq formats : vente, troc, enchère, emploi et service.
- Réponses privées et validées : offre, proposition de troc, enchère, candidature ou message.
- Étoile/favori local, préparation d’un boost régional et statut explicite `awaiting_payment_configuration`.
- Aucun débit ni promesse de paiement : Mobile Money reste une étape future, séparée et configurée par région.

## Sécurité

- Les clients mobiles ne peuvent plus écrire directement les annonces ni les réponses Marketplace dans Firestore.
- La fonction vérifie le propriétaire de la page Business, son statut actif, les champs, les formats et la taille des photos.
- Une personne ne peut pas répondre à sa propre annonce ; les propositions sont privées.

## Vérifications effectuées

- `functions/npm test` : 50 tests réussis.
- Android : `:app:compileDebugKotlin :app:testDebugUnitTest` réussi.
- iOS : compilation simulateur Debug réussie via `xcodebuild`.

## À configurer avant l’encaissement

Configurer et vérifier les fournisseurs Mobile Money régionaux, la devise, les remboursements, la prévention de fraude et les obligations légales avant d’activer tout paiement ou toute campagne facturée.
