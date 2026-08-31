# Billard WAPI — passe compétition

## Changements vérifiés

- Tapis **bleu compétition** par défaut, avec choix réel entre bleu compétition, bleu nuit et vert tournoi.
- Choix persistant de queue (érable, noyer ou carbone), appliqué au rendu natif Android et iOS.
- Jauge de puissance Android déplacée à gauche ; la visée fine reste séparée à droite.
- Gant de placement et blanche déplaçable sur toute la table après le coup d'ouverture ; les collisions et poches restent bloquantes.
- Animation d'entrée dans une poche en deux temps sur iOS (bord puis chute) et animation de chute existante conservée sur Android.
- Attribution des groupes annoncée pendant la partie : « Vous avez les pleines/rayées ».
- Écran de résultat avec profil, confettis, score, victoires/défaites locales et reprise de partie.
- Coupe IA locale sur trois victoires réelles consécutives, stockée séparément du classement serveur.

## Garanties de produit

- Les points et coupes de cette passe sont locaux et explicitement libellés comme tels.
- Aucun pari, dépôt, retrait ni gain d'argent réel n'est implémenté ou présenté comme disponible.
- Le matchmaking WAPI existant reste le seul mode en ligne réel. Les invitations ciblées, notifications de défi et tournois officiels nécessitent un service serveur anti-triche et ne sont pas affichés comme actifs.

## Vérifications

- Android : `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — succès.
- iOS : `xcodebuild -workspace ios/Whappy.xcworkspace -scheme Whappy -configuration Debug -sdk iphonesimulator -derivedDataPath /private/tmp/wapi-ios-build build CODE_SIGNING_ALLOWED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES` — succès.
