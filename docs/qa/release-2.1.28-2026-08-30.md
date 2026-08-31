# WAPI Android 2.1.28 — publication du 30 août 2026

## APK signé

- URL publiée : https://whappy-d97e7.web.app/WAPI-Android-2.1.28-native.apk
- Identifiant Android : `com.whappy.chat`.
- Version affichée : `2.1.28-native` ; version interne : `2105` (précédente : `2104`).
- Taille : 46 686 299 octets, environ 44,5 Mio.
- SHA-256 local : `a2fff2c488a716ad8804d13e0e7cc75b10b70c0704d4f7c02fe5cef1a7146aa7`.
- Certificat SHA-256 : `01d828dc518cb4fa2e61b83b253e2508fb37fb08cb7ef4b43ba43a50c7dd8bf2`, identique à celui de la 2.1.27.

## Contrôles avant publication

- `:app:assembleRelease :app:testDebugUnitTest` : réussite ; 73 tests JVM, aucun échec.
- Signature `apksigner` valide (v1 et v2).
- Manifeste Release sans activité visuelle de test, ni attribut `debuggable` ou `testOnly` activé.
- Bibliothèques WebRTC et WAPI présentes pour arm64-v8a, armeabi-v7a, x86 et x86_64.
- Copie dans `public-hosting` comparée octet par octet à l’APK de compilation : identique.
- Les trois tests instrumentés Android, les 43 tests serveur et la compilation iOS ont réussi pendant la passe précédente, détaillée dans [le rapport audio et catalogue](experience-audio-catalogue-2026-08-30.md). Ils n’ont pas été relancés pour le seul changement de version Android.

## Périmètre

Cette version inclut les corrections Android de la dernière passe : contrôle exclusif des notes vocales, pause/vitesse/recherche/relecture, stabilité des vignettes et reprise de chargement, filtres de catalogue, annulation des gestes de tir au billard.

Publication limitée à l’APK : reprise des empreintes des 19 fichiers de la version Hosting précédente, ajout du nouvel APK et de ses seuls en-têtes HTTP. Aucun déploiement du site modifié localement, des fonctions, des règles Firestore/Storage, des paiements ou d’iOS. Aucun commit ou push Git.

Le service `wapiCommerce` reste à déployer. Les nouveaux écrans Catalogue/Ticketbulk ne sont donc pas annoncés comme opérationnels en production grâce à cette seule APK. Aucun test sur deux téléphones physiques, appel international, paiement ou téléversement de média en production n’est revendiqué.

## État de publication

Publication activée et téléchargement public complet vérifié : HTTP `200`, type APK, pièce jointe, absence de cache. Le fichier téléchargé contient 46 686 299 octets et présente le même SHA-256 que l’APK signé. Comparaison octet par octet avec `cmp` : identique.

- Version précédente : `f6c4e2d0dfad2447`.
- Version Hosting publiée : `a01fee69b2bbb174`.
- Release live : `1788105910478000`.
- Empreinte du contenu gzip hébergé / ETag : `5ad5ffbb223d3779e08819a325bb06c928b73450c3760ded5185e80e6d657883`.
- Vérification avant activation : les 19 fichiers précédents sont conservés avec leurs empreintes inchangées, et seul le nouvel APK est ajouté.
- Une erreur locale du client HTTP avant téléversement a nécessité d’activer explicitement le contrôle des statuts de réponse en mode stream. Reprise du même brouillon, avec vérification de sa configuration et de la version live source avant activation.

## Fichiers modifiés pour cette publication

- `android/app/build.gradle` : numéro de version et code interne.
- `firebase.json` : en-têtes spécifiques du nouvel APK.
- `public-hosting/WAPI-Android-2.1.28-native.apk` : artefact signé.
- Le présent rapport ; toutes les modifications préexistantes ont été conservées.
