# Business commerce, TicketBulk et facturation

## Périmètre livré

- TicketBulk reste un mini-service serveur : événements, capacité atomique, billet QR opaque, annulation et contrôle d'entrée.
- Les boutiques et menus utilisent un catalogue détenu par la page Business ; le client ne peut ni écrire directement dans Firestore ni modifier le prix serveur.
- La facturation est accessible depuis la vitrine du propriétaire Business. Une facture reprend uniquement les articles disponibles du catalogue, avec prix et devise relus côté serveur.
- Les créances sont calculées à partir des factures émises. Une relance est enregistrée comme brouillon et exige une validation humaine avant tout envoi.
- WIA reçoit désormais le catalogue Marketplace actif du compte Business afin de préparer des réponses commerciales factuelles. Il ne confirme jamais un paiement ou une disponibilité non établie.

## Garde-fous

- Aucun paiement, encaissement, crédit ou relance automatique n'est effectué par ce module.
- La bascule d'une créance vers « payée » devra provenir d'un web-hook idempotent d'un prestataire de paiement, avec règles fiscales et rapprochement comptable adaptés au pays.
- `marketplaceInvoices` et `invoiceReminderDrafts` sont verrouillés dans les règles Firestore : seul le backend Admin SDK peut les modifier.

## Vérifications exécutées

- `functions/npm test` : 48 tests passent, dont propriété de la page Business, prix catalogue autoritaire, créances et brouillons de relance.
- `android/./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` : succès.
- `xcodebuild -workspace ios/Whappy.xcworkspace -scheme Whappy -configuration Debug -sdk iphonesimulator -derivedDataPath /private/tmp/wapi-ios-billing-build build CODE_SIGNING_ALLOWED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES -quiet` : succès.

## Mise en service

Le callable `wapiCommerce` doit être déployé avec les fonctions et les règles Firestore avant que TicketBulk, les boutiques et la facturation ne puissent être utilisés sur les téléphones. Aucun déploiement n'a été réalisé dans cette passe.
