# WAPI — fiabilité des formulaires, du catalogue et des photos

Passe du 30 août 2026. Modifications locales uniquement : aucune publication d’APK, de backend, de règles ou de version iOS. Les changements des autres passes ont été conservés.

## Ce qui change

- Android : conservation des champs Business/Ticketbulk, de la route et de l’identifiant d’enregistrement lors d’une recréation d’activité. Seuls les champs et la référence de galerie sont sauvegardés, pas les images en base64 ni le catalogue entier.
- Android : la photo choisie est relue depuis la galerie après rotation. Une référence devenue inaccessible bloque l’enregistrement sans photo et propose de la choisir à nouveau. Reprendre la même image relance bien sa préparation.
- Android : correction d’un plantage découvert pendant cette passe, après sélection d’une photo puis rotation (`Mismatching scroller orientation`). Les champs possèdent désormais des clés de restauration distinctes, indépendantes de l’apparition de l’image.
- Android et iOS : confirmation avant d’abandonner un formulaire modifié, champs non modifiables pendant l’enregistrement, protection contre deux enregistrements simultanés, conservation de la saisie en cas d’erreur.
- iOS : l’initialisation du formulaire n’est exécutée qu’une fois ; un retour de galerie ou du lecteur QR ne réinitialise pas les champs. Le geste de fermeture est bloqué pendant une sauvegarde ou lorsque des modifications doivent être confirmées.
- Marketplace côté serveur : recherche de 40 résultats au maximum à travers jusqu’à 5 lots de 40 candidats. Le curseur correspond au dernier document examiné ; les résultats suivants ne sont ni sautés ni dupliqués. Les boutiques suspendues, absentes ou non publiées sont exclues.
- Photos du catalogue côté serveur : création conditionnelle des objets immuables. Une nouvelle tentative sur la même image réutilise son jeton existant au lieu d’invalider les liens déjà distribués. Une erreur d’accès ou de réseau reste une erreur explicite.

## Vérifications réalisées

| Vérification | Résultat |
| --- | --- |
| Android `assembleDebug` + tests JVM | Réussite ; 62 tests, aucun échec |
| Backend TypeScript + Node | Réussite ; 43 tests, aucun échec |
| iOS Debug, simulateur arm64, sans signature | `BUILD SUCCEEDED` |
| Firestore local isolé `demo-wapi-commerce` | Réussite |
| Formulaire Android : erreur, double appui, rotation, reprise | Réussite sur émulateur |
| Galerie Android : sélection, paysage, retour portrait | Photo restaurée ; aucun nouveau plantage après correction |

### Scénarios supplémentaires côté serveur

Les six nouveaux tests couvrent : recherche après les 40 premiers candidats et curseur intermédiaire exact ; limite de 200 candidats et reprise ; exclusions de visibilité ; huit sauvegardes concurrentes d’une image avec une seule écriture et une seule URL ; préservation de l’ancienne image quand le contenu change ; erreurs 403/503 et objet sans jeton.

Le test sur le véritable émulateur Firestore vérifie aussi 103 boutiques : 62 résultats filtrés répartis sur deux pages de 40 et 22, sans doublon ni saut. Les scénarios précédents restent valides : huit réservations pour trois places, cinq scans pour une entrée, quatre annulations pour une seule place libérée, ancien QR révoqué après réémission, mauvais événement refusé, quatre collections interdites en accès client direct.

### Vérification visuelle Android

Écran de test exclusivement dans `src/debug`, données locales, aucun envoi aux utilisateurs :

1. Saisie de « Menu preserve » et du prix 1250.
2. Double appui : un seul appel au service de test. Une erreur volontaire garde les champs visibles.
3. Rotation paysage puis portrait : valeurs et identifiant de l’article conservés.
4. Retour : confirmation affichée ; « Continuer à modifier » conserve les champs.
5. Nouvelle tentative : même identifiant, succès de test, retour au catalogue.
6. Sélection d’un logo comme image de test depuis la galerie native ; rotation dans les deux sens ; image toujours visible et prête après correction du défaut découvert.

Captures :

- [Saisie après erreur](reliability-form-error-2026-08-30.png)
- [Protection avant abandon](reliability-discard-2026-08-30.png)
- [Image restaurée après rotation](reliability-photo-restored-2026-08-30.png)

## Fichiers modifiés dans cette passe

- `android/app/src/main/java/com/whappy/chat/WapiCommerce.kt`
- `android/app/src/debug/java/com/whappy/chat/CommerceChatVisualTestActivity.kt`
- `ios/Whappy/WapiCommerce.swift`
- `functions/src/commerce.ts`
- `functions/test/commerce.test.js`
- `functions/test/commerce.emulator.cjs`
- Ce rapport et ses trois captures.

## Limites et suite de validation

- La validation iOS est une compilation, pas un test interactif sur iPhone. Les retours galerie et les interruptions réseau doivent encore être essayés sur appareils réels.
- La galerie et la préparation d’image Android ont été testées ; l’écriture Cloud Storage est vérifiée par des tests injectés, pas par un téléversement en production ou un émulateur Storage.
- `rememberSaveable` protège les recréations gérées par Android ; ce n’est pas une sauvegarde cloud des brouillons. Aucune promesse de conservation après effacement des données, arrêt forcé ou désinstallation.
- La recherche est bornée pour limiter les lectures : au-delà de 200 candidats, « Charger la suite » peut être nécessaire. Ce n’est pas encore un index géographique ou une recherche plein texte à grande échelle.
- Aucun nouvel essai des appels entre pays, des paiements ou des jeux dans cette passe. Leur bon fonctionnement ne découle pas de ces tests Commerce.
- Les corrections serveur ne sont pas actives sur la version publique avant un déploiement ultérieur autorisé.

## Références techniques vérifiées

- L’état sauvegardé Android doit rester petit et adapté à la recréation d’activité : [documentation Android — Save UI state](https://developer.android.com/develop/ui/compose/state-saving).
- La précondition `ifGenerationMatch: 0` empêche de remplacer un objet existant : [Cloud Storage — Request preconditions](https://docs.cloud.google.com/storage/docs/request-preconditions).

Ces références justifient les mécanismes choisis ; les résultats ci-dessus proviennent des compilations et tests locaux.
