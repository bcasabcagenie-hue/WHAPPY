# WAPI — messagerie, vitrines Business, Ticketbulk et jeux

Validation locale du 30 août 2026. Aucun APK, règle ou service publié pendant ce chantier.

## Changements livrés dans le code

- **Conversation Android/iOS** : la zone de discussion suit la hauteur disponible au-dessus du clavier. Le dernier message reste visible quand on était en bas ; consulter l’historique ne déclenche pas de retour forcé. Android : bulles adaptées au contenu, espaces réduits et suppression du double espacement clavier/navigation.
- **Marketplace et Business** : mini-app native de vitrines publiées, recherche par activité et ville, identité de la page Business, adresse, horaires, catalogue par rubrique, photo depuis la galerie, prix et disponibilité. Le propriétaire peut publier sa vitrine et modifier ses articles. Un visiteur peut ouvrir une conversation avec l’établissement. Ouvrir à nouveau une conversation Business ne remet plus ses accusés de lecture à zéro.
- **Ticketbulk** : mini-app native de création d’événements rattachés à une page Business, date/heure, lieu, capacité, réservations gratuites, billets personnels conservés côté serveur et QR de contrôle. Une réservation par compte/événement ; réessayer après une perte de réponse ne crée pas un second billet. Fermeture des inscriptions et compteur des entrées pour l’organisateur.
- **Sécurité** : propriétaires vérifiés côté serveur, compteurs gérés par transactions, QR validé seulement par l’organisateur. Les clients ne lisent ni n’écrivent directement les quatre nouvelles collections. Les identifiants du catalogue sont dérivés de la paire page/article sans collision de séparateur.
- **Billard** : recul réel de la commande nécessaire pour frapper, possibilité de réduire la puissance, aucun tir sur un simple toucher. Traînées atténuées et ombres de contact rapprochées du tapis. Sur iOS, le tour IA attend l’arrêt réel des boules au lieu d’un délai fixe ; les tirs d’entraînement visent une poche plutôt qu’une direction aléatoire.
- **Échecs** : corps de pièces tournés avec normales lissées, éléments distinctifs, matériaux revus, repères de coups légaux plus discrets. Cadrage selon le format pour ne pas couper le plateau ni les pièces hautes.
- L’ancien panier local est explicitement nommé « sélection locale », sans prétendre avoir envoyé une commande au vendeur.

## Vérifications exécutées

| Contrôle | Résultat |
| --- | --- |
| Android assembleDebug + testDebugUnitTest | Réussi, 56 tests, 0 échec |
| TypeScript build + tests backend | Réussi, 33 tests, 0 échec |
| iOS Debug arm64, SDK Simulator, sans signature | BUILD SUCCEEDED |
| Transactions réelles dans Firestore Emulator | 8 réservations simultanées, capacité 3 : exactement 3 billets |
| Contrôle simultané d’un même QR | 5 tentatives : exactement 1 entrée acceptée |
| Règles sur les 4 nouvelles collections | Lecture directe authentifiée refusée (403) |
| Vitrine/catalogue dans Firestore Emulator | Publication, recherche accentuée, lecture et refus de modification non autorisée vérifiés |
| Android clavier | Dernier message entier au-dessus du clavier ; historique maintenu après saisie |
| Android Marketplace | Répertoire et menu de restaurant contrôlés visuellement dans un harnais Debug |
| Android échecs | Plateau complet visible en portrait après correction du cadrage |
| Android billard | Simple toucher sans tir ; glissement de puissance déclenchant une frappe ; caméra stable |
| git diff --check | Aucun défaut d’espacement |

Les tests Firestore utilisent exclusivement `demo-wapi-commerce` sur `127.0.0.1:8188`. Les exemples du café et des discussions sont dans `src/debug` et ne sont pas compilés dans l’application de production.

### Captures de l’application réellement exécutée

- `chat-keyboard-2026-08-30.png` : clavier et dernier message.
- `chat-history-2026-08-30.png` : consultation de l’historique pendant la saisie.
- `market-storefront-2026-08-30.png` : menu d’un établissement de test.
- `chess-framing-2026-08-30.png` : cadrage du plateau.
- `pool-shot-2026-08-30.png` : après une frappe réelle du moteur existant.

## Fichiers concernés par ce chantier

- Android : `WhappyUi.kt`, `WhappyRepository.kt`, nouveau `WapiCommerce.kt`, `WapiTabletop3DView.kt`, `WapiPoolPresentation.kt`, `WapiPoolPresentationTest.kt`, harnais `CommerceChatVisualTestActivity.kt` et manifeste Debug.
- iOS : `ContentView.swift`, nouveau `WapiCommerce.swift`, `WapiGameSceneKit.swift`, références du projet Xcode.
- Serveur : nouveau `functions/src/commerce.ts`, export `wapiCommerce` dans `functions/src/index.ts`, `firestore.rules`, tests `commerce.test.js` et `commerce.emulator.cjs`.

Les autres modifications déjà présentes dans le dépôt ne font pas partie de ce rapport.

## Limites explicites avant publication

1. Le service `wapiCommerce` et les nouvelles règles doivent être déployés avec les applications pour rendre les mini-apps disponibles aux utilisateurs. Cela n’a pas été fait ici.
2. Ticketbulk couvre les billets **gratuits**. Paiements, remboursements, tarification, transfert de billet et export PDF ne sont pas implémentés ; aucun faux paiement n’est présenté.
3. Le catalogue permet de présenter une activité et de contacter son établissement ; ce n’est pas encore une chaîne complète de commande, paiement et livraison.
4. La recherche est paginée par lots de 40 puis filtrée. Elle n’est pas un index de recherche géographique à grande échelle. Le catalogue est limité à 200 articles par vitrine dans cette version.
5. La sélection/compression d’image et l’enregistrement Storage compilent mais n’ont pas été testés de bout en bout avec Firebase Storage. Prévoir un essai autorisé sur environnement de recette avant diffusion.
6. iOS compile ; les interactions clavier, galerie, scanner et physique doivent encore être vérifiées sur un iPhone. Les captures et gestes exécutés ici sont Android, pas iOS.
7. Les jeux utilisent leurs moteurs natifs existants, pas Unreal Engine. Ces améliorations ne constituent ni une validation photoréaliste, ni une équivalence avec un jeu commercial, ni une validation multijoueur entre deux appareils.
8. Aucune mesure de fluidité sur téléphone physique ou réseau mobile n’a été faite pendant cette validation.

## Reproduire les contrôles

Android : `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`.

Backend : `cd functions && npm test`.

Firestore : lancer `functions/test/commerce.emulator.cjs` avec `firebase emulators:exec --only firestore --project demo-wapi-commerce`, configuration locale pointant vers les règles du dépôt et le port 8188. Le script refuse tout autre projet ou hôte.

iOS : `xcodebuild -workspace ios/Whappy.xcworkspace -scheme Whappy -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build`.
