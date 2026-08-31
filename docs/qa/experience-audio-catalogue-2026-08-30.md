# WAPI — audio, catalogue et commandes de billard

Passe locale du 30 août 2026, après publication de la 2.1.27. Aucun nouveau déploiement, changement de version, commit ou push. Les modifications préexistantes ont été conservées.

## Améliorations livrées dans les sources

### Notes vocales — Android et iOS

- Une nouvelle note met la précédente en pause. Android réserve aussi la lecture pendant la préparation : une ancienne requête tardive ne reprend pas le son.
- Changer la vitesse d’une note en pause ne la démarre pas ; chercher une position ne lance pas non plus la lecture.
- Android : les ressources sont libérées après échec ou sortie, et le focus audio cède aux autres usages audio. La lecture cesse en arrière-plan.
- iOS : préparation à la demande plutôt que pour chaque note visible ; état de chargement/erreur, interruption audio, débranchement du casque, fin de lecture et reprise pris en compte. Le minuteur n’écrase plus la position choisie pendant un glissement ou une recherche.
- Boutons de lecture de 44 points/dp, durées écoulée/totale, libellés d’accessibilité distincts. Les commandes de vitesse Android ont une zone de toucher agrandie.
- Commencer une note dans la conversation met en pause la note reçue en cours de lecture, pour ne pas la réenregistrer au micro.

### Photos de messages — Android

- Zone de vignette 4:3 réservée avant le chargement et conservée après décodage : l’arrivée de l’image ne modifie plus la hauteur de la bulle.
- Toucher une vignette en échec relance son chargement ; une image chargée ouvre toujours sa consultation complète. Le recadrage ne modifie pas le fichier original.
- iOS conservait déjà une taille fixe pour ces vignettes ; elle n’a pas été modifiée dans cette passe.

### Catalogue Business — Android et iOS

- Catégories sélectionnables, recherche combinée et filtre « Disponibles uniquement ».
- Compteurs issus des articles chargés, pas de nombres inventés ni de statistiques de ventes.
- Les articles sans catégorie restent accessibles sous « Autres ».
- État vide adapté aux filtres et action de réinitialisation ; changement de boutique remet les filtres du catalogue à zéro.
- Le filtre Android possède un libellé pour les lecteurs d’écran.
- Les fenêtres plein écran Android du catalogue et de ses formulaires utilisent des icônes système sombres sur leur fond clair, puis restaurent le réglage précédent à la fermeture.

### Billard — Android et iOS

- Annulation du tir en déplaçant le geste hors de la zone de commande.
- Retour à la puissance précédente lorsqu’un geste est annulé ou trop court.
- Android : déplacement brut conservé, même en dépassant la puissance maximale. Revenir au point de départ après ce dépassement ne provoque plus de tir accidentel. Un geste ne peut valider qu’un seul tir.
- Pas de changement de moteur ou de nouvelles textures dans cette passe ; aucune équivalence avec un jeu commercial n’est annoncée.

## Vérifications

| Contrôle | Résultat |
| --- | --- |
| Android debug + APK de test | Compilation réussie |
| Tests unitaires Android | 73 réussis, dont 11 nouveaux |
| Backend TypeScript + tests Node | 43 réussis ; aucun code serveur modifié |
| iOS Debug, simulateur arm64, sans signature | `BUILD SUCCEEDED` |
| Tests instrumentés Android API 36.1 | 3 réussis, avec véritable `MediaPlayer` et PCM local |
| `git diff --check` | Aucun défaut d’espacement |

### Scénarios sur émulateur

1. Lire puis mettre en pause ; changer la vitesse et chercher une position sans redémarrer le son ; reprendre ; lancer une seconde note qui met la première en pause ; passer en arrière-plan et vérifier l’arrêt audio.
2. Chercher une position avant la première lecture sans lancer le son ; lire depuis cette position ; attendre la fin ; relire la note.
3. Ouvrir le catalogue de deux articles de test, masquer l’article indisponible (compteur 2 → 1), sélectionner sa catégorie vide, puis réinitialiser les filtres (retour à 2).

Le test audio vérifie `AudioManager.isMusicActive` et les commandes accessibles, pas uniquement des booléens simulés. Le catalogue utilise un service injecté dans une activité exclusivement debug : aucune donnée de démonstration n’est ajoutée aux comptes ni à l’APK de distribution.

Capture du catalogue de test : [filtres et disponibilité](catalogue-filters-2026-08-30.png).

La première tentative de compilation iOS a manqué d’espace disque. Le dossier temporaire de produits/intermédiaires `/tmp/wapi-blue-pool-derived/Build` a été supprimé, puis la compilation a réussi avec le cache existant `/tmp/wapi-ios-pool-pro`. Aucun fichier source ou APK publié n’a été supprimé. Les premiers essais instrumentés ont également corrigé le délai de démarrage et le ciblage du contrôle de disponibilité ; les trois scénarios ci-dessus passent sur la compilation finale.

## Fichiers concernés

- `android/app/src/main/java/com/whappy/chat/WhappyUi.kt`
- `android/app/src/main/java/com/whappy/chat/WapiAudioArbiter.kt`
- `android/app/src/main/java/com/whappy/chat/WapiVoiceAudioFocus.kt`
- `android/app/src/main/java/com/whappy/chat/WapiCommerce.kt`
- `android/app/src/main/java/com/whappy/chat/WapiCommercePolicy.kt`
- `android/app/src/main/java/com/whappy/chat/WapiTabletopInput.kt`
- Tests JVM : `WapiAudioArbiterTest`, `WapiCommercePolicyTest`, `WapiTabletopInputTest`.
- Activité debug `CommerceChatVisualTestActivity` et test instrumenté `WapiVoicePlaybackInstrumentedTest`.
- `ios/Whappy/ContentView.swift`
- `ios/Whappy/WapiCommerce.swift`

## Limites à ne pas confondre avec une validation complète

- L’APK public 2.1.27 ne contient pas cette nouvelle passe.
- Le nouveau service `wapiCommerce` reste à déployer : les filtres sont implémentés, mais publier seulement l’APK ne rendra pas les nouveaux catalogues/Ticketbulk opérationnels en production.
- Aucun essai iPhone interactif, appel entre deux pays, paiement, téléversement Cloud Storage, test réseau lent de notes distantes ou benchmark GPU dans cette passe.
- Les sons réels sont joués par le moteur audio de l’émulateur, lancé sans sortie sonore sur le Mac. Cela valide les états de lecture, pas la qualité acoustique sur téléphone.
- Les avertissements préexistants concernant les anciennes cibles iOS des Pods et les API Android dépréciées restent présents. Aucun changement de dépendance opportuniste.

## Références utilisées

- [Android MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) : après préparation, un paramètre de vitesse non nul peut démarrer la lecture ; il est donc appliqué uniquement lorsque la lecture est demandée.
- [Apple AVPlayer — timeControlStatus](https://developer.apple.com/documentation/avfoundation/avplayer/timecontrolstatus-swift.property) : distinction entre pause, lecture et attente de données pour l’état affiché.
