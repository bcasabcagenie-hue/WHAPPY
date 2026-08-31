# WAPI — éditeurs natifs, 30 août 2026

## Périmètre

Les trois captures jointes au dernier message sont introuvables sur le disque. Cette intervention corrige des problèmes identifiés dans le code et vérifiés localement ; elle ne prétend pas résoudre un défaut spécifique visible uniquement sur ces captures.

Aucun déploiement, changement de version, publication de boutique ou modification de règles Firebase.

## Android

- `WapiEditorScreen.kt` : destination plein écran, barre de navigation compacte, une seule liste défilante, marges système/clavier fusionnées sans double compensation, action persistante au-dessus du clavier, largeur de lecture plafonnée sur tablette. Icônes système sombres sur le fond blanc.
- `WhappyUi.kt` : création/modification Business, création de groupe, chaîne, offre, annonce et réglages WIA adoptent cet éditeur. Les confirmations courtes restent des dialogues.
- Groupe : recherche des membres, sélection accessible, limite de 64 respectée, galerie et recadrage existants conservés. Brouillons de groupe/chaîne enregistrés dans l’état de restauration Android.
- Offre : suppression des hauteurs fixes et du formulaire en petite fenêtre ; sections produit/prix/disponibilité, validation prix et durée avant envoi. Aucun changement du calcul des prix ni de la publication serveur.
- `CommerceChatVisualTestActivity.kt` : scénarios de test isolés en source debug, jamais dans l’APK release.
- `WapiNativeEditorInstrumentedTest.kt` : tests du clavier, de la rotation, de la sélection et de la validation sur les vrais composants UI ; sauvegardes remplacées uniquement dans l’activité debug par un callback local.

## iOS

- `WapiDesignSystem.swift` : champs avec libellés persistants et action native dans une safe-area au-dessus du clavier.
- `ContentView.swift` : profil Business restructuré, groupe/chaîne/annonce en plein écran, fermeture interactive du clavier. Les liens et actions existants sont conservés.
- `WhappyStore.swift` : la sauvegarde du profil Business retourne son résultat à l’écran. Une identité non confirmée n’est plus appliquée comme si elle avait été enregistrée. L’écran garde la saisie en cas d’échec et n’affiche le succès qu’après l’acquittement Firestore. La sauvegarde ne redirige plus implicitement vers une autre messagerie.

## Vérification

- Android : `assembleDebug`, `assembleDebugAndroidTest`, `compileReleaseKotlin` réussis ; 80 tests JVM réussis, aucun échec.
- iOS : compilation simulateur arm64 réussie, signature désactivée. Avertissements de cibles minimales des Pods préexistants.
- Trois tests UI Android réussis sur API 36.1 : action Business hors du clavier et brouillon conservé après rotation ; groupe créé avec la personne recherchée ; offre non publiable avec prix/durée invalides.
- Régression finale : **6 scénarios Android réussis en 56,249 s**, comprenant ces trois parcours, le changement de difficulté échecs/dames, le tir de billard suivi de la réponse IA et l’ouverture/fermeture directe de Récents en plein écran.
- Inspection visuelle des écrans Business et groupe, dont contraste des icônes système.
- `git diff --check` sans erreur.

## Limites explicites

- Les appels réseau de publication ne sont pas testés contre la production ; les tests UI n’envoient aucune donnée réelle.
- Aucun test sur iPhone physique dans cette intervention ; la compilation iOS ne remplace pas cette validation.
- Les autres anciens écrans/modales ne sont pas tous refondus. Les parcours serveur existants (y compris ceux qui ferment immédiatement leur éditeur à la soumission sur Android) restent à auditer séparément ; la refonte ne constitue pas une garantie de publication réseau.
- Les changements locaux précédents concernant les jeux et Récents sont conservés. L’APK public 2.1.28 n’est pas mis à jour par cette intervention.
