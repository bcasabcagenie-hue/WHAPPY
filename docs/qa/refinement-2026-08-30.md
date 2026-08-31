# WAPI — seconde passe d’amélioration du 30 août 2026

Travail local, sans publication des applications ni du service Firebase. Complète le rapport `commerce-chat-games-2026-08-30.md`.

## Améliorations

### Messagerie

- Android et iOS : modifier un message envoyé ne remplace plus le brouillon non envoyé. Annuler ou terminer la modification restaure ce brouillon. Passer à une réponse quitte proprement le mode modification.
- iOS : galerie, fichier et emoji sont regroupés dans un panneau ouvert par « + », au lieu de comprimer en permanence le champ de texte. La commande principale alterne micro/envoi ; le bouton Stop reste accessible pendant un enregistrement.
- La conversation conserve les corrections clavier de la passe précédente.

### Design partagé Android

- Les styles typographiques ne forcent plus une couleur sombre : les boutons et contrôles héritent de leur couleur de contenu, notamment le blanc sur bleu.
- Les surfaces Material de dialogue utilisent les blancs et gris WAPI au lieu du violet par défaut.

### Marketplace — Android et iOS

- En-tête de vitrine plus compact, laissant davantage de place au catalogue.
- Recherche dans les noms, rubriques et descriptions d’articles.
- Aperçu de la photo choisie avant enregistrement ; photo actuelle visible lors de la modification.
- Saisie naturelle des prix : `12,50` EUR devient exactement 1250 centimes ; 5500 XAF reste 5500. Pas d’arrondi silencieux ni de notation scientifique. Erreur lisible pour les montants invalides.
- Les chargements obsolètes ne remplacent plus un écran ou une recherche plus récents. Déduplication des résultats paginés.

### Ticketbulk — Android, iOS et serveur

- Distinction entre réservation possible, événement complet, inscriptions fermées et billet déjà obtenu.
- Annulation confirmée d’un billet gratuit avant l’événement. Une transaction libère exactement une place, même si l’utilisateur réessaie.
- Nouvelle réservation possible après annulation si la capacité le permet ; le nouveau QR invalide l’ancien. Une ancienne demande d’annulation ne peut pas annuler ce nouveau billet.
- Un billet utilisé ne peut pas être annulé ; un billet annulé n’est plus accepté au contrôle.
- Le contrôle ouvert depuis un événement transmet son identifiant : un billet d’un autre événement du même organisateur est refusé sans consommer l’entrée.
- Les billets annulés/utilisés ne présentent plus de QR ni d’action de copie utilisable.

### Jeux

- Android : distinction explicite toucher/glissement/zoom/annulation. Revenir au point initial après avoir tourné le plateau ne sélectionne plus un pion. Un zoom à plusieurs doigts ou une interruption ne provoque pas de sélection à la fin du geste.
- Billard Android : commande de puissance bornée, état de traction réinitialisé lorsque le tir est désactivé, callbacks actualisés.
- Billard iOS : état de traction géré par le cycle natif du geste, réinitialisé si celui-ci est annulé.
- Pas de changement de moteur, pas d’annonce de photoréalisme ni de nouvelle validation multijoueur.

## Résultats vérifiés

| Vérification | Résultat |
| --- | --- |
| Android Debug + tests JVM | Compilation réussie, 62 tests, aucun échec |
| Backend TypeScript + Node tests | Compilation réussie, 37 tests, aucun échec |
| iOS Debug arm64 Simulator sans signature | BUILD SUCCEEDED |
| Firestore local — réservations simultanées | 8 demandes / 3 places : 3 billets |
| Firestore local — contrôles simultanés | 5 scans : 1 seule entrée |
| Firestore local — annulations simultanées | 4 demandes : 1 place libérée |
| Firestore local — ancien QR et mauvais événement | Refus confirmés |
| Règles Firestore des quatre nouvelles collections | Accès direct client refusé |
| Android réel dans émulateur — brouillon | Ouverture de modification, saisie, annulation : brouillon initial restauré |
| Android réel dans émulateur — thème | Texte blanc sur boutons bleus confirmé |
| Android réel dans émulateur — Ticketbulk | Réservation, affichage QR, confirmation et retour après annulation exercés |
| git diff --check | Aucun défaut d’espacement |

Les scénarios visuels commerce/billets utilisent des données isolées dans `src/debug`, exclues du binaire Release. La persistance et la concurrence ont été testées séparément dans le véritable émulateur Firestore, projet `demo-wapi-commerce`, jamais en production.

## Captures

- `refinement-catalogue-2026-08-30.png` : vitrine compacte et recherche catalogue.
- `refinement-ticketbulk-2026-08-30.png` : contraste des boutons corrigé.
- `refinement-draft-2026-08-30.png` : brouillon restauré, clavier ouvert et dernier message visible.

## Fichiers de cette passe

- Android : `WapiCommerce.kt`, nouveaux `WapiCommercePolicy.kt` et `WapiTabletopInput.kt`, tests associés, `WhappyUi.kt`, `WapiTabletop3DView.kt`, `WapiDesignSystem.kt`, harnais Debug `CommerceChatVisualTestActivity.kt`.
- iOS : `WapiCommerce.swift`, `ContentView.swift`.
- Serveur : `functions/src/commerce.ts`, `functions/test/commerce.test.js`, `functions/test/commerce.emulator.cjs`.

## Limites conservées

- Ni APK ni service `wapiCommerce` déployés : ces changements ne sont pas disponibles dans l’APK public actuel.
- Les interactions iOS sont compilées mais pas validées sur iPhone. Pas de mesure de performance sur téléphone physique.
- Ticketbulk reste gratuit ; aucune passerelle de paiement ajoutée.
- L’envoi des photos catalogue vers Storage attend toujours une validation bout en bout sur un environnement de recette autorisé.
- Recherche publique paginée, pas encore d’index géographique à grande échelle.
- Cette passe ne certifie pas les appels à distance, les lives, l’ensemble des jeux ou chaque module de WAPI.
