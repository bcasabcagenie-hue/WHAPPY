# Billard — profils, placement et résultats

## Périmètre du lot

Travail local, sans commit, déploiement Firebase ni publication d’APK. Les modifications antérieures du dépôt ont été conservées.

### Android

- Main 3D liée à la position réelle de la blanche. Placement avant la casse, en entraînement et après une faute lorsque les règles l’autorisent.
- Confirmation explicite « Poser la blanche » ; le lâcher du doigt ne valide plus involontairement un placement. Les tirs sont désactivés pendant cette étape.
- Placement serveur et client alignés sur les mêmes dimensions de table, distances aux poches et autres billes. Coordonnées non finies rejetées.
- Molette de visée fine indépendante de la puissance. Les angles restent normalisés même après plusieurs tours de molette.
- Deux fiches joueurs, photo/icône, joueur actif, pleines/rayées, nombre et numéros des billes restantes. La noire n’est pas comptée parmi les sept billes du groupe.
- Zone réservée au tableau de score pour ne pas masquer les poches. Jauge de puissance et réglage d’effet conservés.
- Profil joueur : pseudo, galerie avec recadrage existant, cinq icônes, cache par utilisateur, résultats en ligne et record local distincts.
- Le match WAPI en ligne lit les profils préparés par le serveur. Une identité de match est un instantané pris à l’entrée dans la table.

### iOS

- Nouveau module Billard plein écran, profils et édition photo/icône utilisant les mêmes API.
- Main de placement, ray-casting tactile sur la table, confirmation, visée fine et jauge physique.
- Pleines/rayées, première collision, fautes, conservation du tour et victoire/défaite sur la noire reliées aux événements SceneKit.
- L’IA cible les billes de son groupe ; les scores d’entraînement restent locaux.
- Impulsion du tir corrigée pour tenir compte de la masse de la bille, au lieu d’appliquer directement une vitesse comme une force impulsionnelle.
- Crash Metal observé puis corrigé : normalisation RGBA8 des textures, notamment la bille noire qui pouvait être produite en niveaux de gris. Le rendu a ensuite démarré dans le simulateur.
- Ajustements supplémentaires du tapis mat et de la subdivision des poches compilés, puis installés et inspectés dans le simulateur. Capture finale : `/tmp/wapi-pool-ios-verified.png`. Cette inspection valide l’ouverture et la disposition, pas un niveau photoréaliste ni la totalité des interactions sur appareil physique.

## Données et sécurité

- `getGameProfile` : lecture des seuls champs publics autorisés ; pas de numéro de téléphone utilisé comme pseudo de secours.
- `saveGameProfile` : propriétaire issu de Firebase Auth, jeu et icône autorisés, pseudo borné, upload d’image validé. Les valeurs de points, de victoires et les URL proposées arbitrairement par le client ne sont jamais copiées dans le profil.
- `gameProfiles/{uid}_billard` : identité personnalisée conservée lors des mises à jour des résultats.
- `submitPoolShot` : le serveur simule le tir, résout le gagnant, écrit les statistiques et le résultat dans la même transaction.
- `poolResults/{roomId}` : un résultat unique par table terminée ; une victoire vaut 100 points, zéro valeur monétaire. Victoires, défaites, parties et meilleure série sont enregistrées.
- Les règles Firestore existantes n’ont pas été assouplies. Les écritures directes des clients sur les résultats, profils joueurs et tables de billard sont refusées.

## Vérifications

- Android : compilation Debug et Kotlin Release réussie ; 83 tests unitaires sans erreur.
- Backend : compilation TypeScript et 47 tests réussis.
- Swift : 8 contrôles exécutables des groupes, fautes et conditions de victoire réussis (`ios/tests/pool-rules/main.swift`).
- Firestore réel en émulateur, exclusivement `demo-wapi-pool` sur `127.0.0.1:8188` : sauvegarde et conservation de l’identité, refus des points falsifiés, confidentialité du numéro, contrôle du propriétaire, placement de la blanche, tir gagnant réel, quatre soumissions concurrentes pour une seule attribution, trois refus d’écriture par les règles.
- Android instrumenté : placement et confirmation, visée fine sans tir involontaire, tir réel puis retour de l’IA, et ouverture/fermeture de Récents réussis. Suite élargie : quatre tests réussis sur cinq. Le test de changement de difficulté réussit son parcours échecs, mais échoue à sélectionner un pion aux dames, également lors des reprises isolées. La cause reste à diagnostiquer ; tous les jeux ne sont donc pas déclarés validés.
- iOS : compilation simulateur arm64 réussie. Le nouveau module a été ouvert et inspecté visuellement après correction du crash Metal. Cela ne remplace pas des tests de jeu sur iPhone physique.

Commandes de reproduction :

```sh
cd android
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:compileReleaseKotlin
cd ../functions
npm test
cd ..
xcrun swiftc ios/Whappy/WapiPoolRules.swift ios/tests/pool-rules/main.swift -o /tmp/wapi-pool-rules-test
/tmp/wapi-pool-rules-test
```

Le test d’intégration `functions/test/poolProfile.emulator.cjs` refuse de fonctionner hors du projet de démonstration et du port local spécifiés. Il peut être lancé avec `firebase emulators:exec --only firestore --project demo-wapi-pool`, Java 21 et une configuration locale chargeant les règles du dépôt.

## Non livré / non activé

- Aucun championnat organisable, tableau éliminatoire, dotation, mise ou retrait d’argent n’est présenté comme opérationnel. Ces mécanismes nécessitent un lot distinct : règlement, éligibilité, arbitrage/anti-collusion, paiements et rapprochement comptable.
- Le multijoueur billard serveur existant est intégré côté Android. Le nouveau module iOS couvre entraînement et IA ; la connexion iOS aux tables multijoueurs reste à implémenter avant de revendiquer une parité complète.
- Les API modifiées et `saveGameProfile` ne sont pas déployées en production. La sauvegarde cloud des nouveaux profils et l’attribution des points nécessitent leur publication ultérieure.
- La galerie/validation des photos a été compilée ; un upload depuis un compte réel en production n’a pas été exécuté.

Principaux fichiers : `WapiPoolPlayers.kt`, `WapiOnlinePool.kt`, `WhappyUi.kt`, `WapiTabletop3DView.kt`, `WapiPoolPlayers.swift`, `WapiPoolRules.swift`, `WapiGameSceneKit.swift`, `ContentView.swift`, `functions/src/gamePlayer.ts`, `functions/src/poolGame.ts`, `functions/src/index.ts` et leurs tests.
