# WAPI Android 2.1.27 — publication du 30 août 2026

## APK publié

- URL : https://whappy-d97e7.web.app/WAPI-Android-2.1.27-native.apk
- Identifiant Android : `com.whappy.chat`.
- Version affichée : `2.1.27-native` ; version interne : `2104` (précédente : `2103`).
- Taille : 46 676 915 octets, soit environ 44,5 Mio.
- SHA-256 local : `398b105b8183e35bf9289d0868dfd72bca24b190c1b6a2424bbebd488a2e3592`.
- Certificat de signature SHA-256 : `01d828dc518cb4fa2e61b83b253e2508fb37fb08cb7ef4b43ba43a50c7dd8bf2`, identique à celui de la 2.1.26.

## Contrôles

- Compilation Release et 62 tests JVM : réussite.
- Vérification `apksigner` : valide, schémas v1 et v2.
- Manifeste de distribution : aucune activité visuelle de test et aucun attribut `debuggable` activé.
- Bibliothèques natives WebRTC et WAPI présentes pour arm64-v8a, armeabi-v7a, x86 et x86_64.
- HTTP public : `200`, type APK et téléchargement en pièce jointe, cache désactivé pour ce fichier.
- Téléchargement public complet : 46 676 915 octets ; SHA-256 identique au fichier signé local (`398b105b8183e35bf9289d0868dfd72bca24b190c1b6a2424bbebd488a2e3592`). Comparaison octet par octet avec `cmp` : identique.

## Publication limitée à l’APK

La nouvelle version Firebase Hosting contient les 18 fichiers de la version précédente, avec leurs empreintes inchangées, et le nouvel APK. Seul l’en-tête correspondant à ce nouvel APK a été ajouté à la configuration hébergée. Les fichiers web modifiés localement n’ont pas été publiés.

- Version Hosting précédente : `1d55fcfcbbb339bb`.
- Nouvelle version Hosting : `f6c4e2d0dfad2447`.
- Release live : `1788101519662000`.

Le téléversement a nécessité une reprise du même brouillon de version après une erreur locale d’interprétation de la réponse HTTP. Aucun second déploiement et aucune suppression de fichier existant.

Procédure : [API officielle Firebase Hosting](https://firebase.google.com/docs/hosting/api-deploy), avec réutilisation des empreintes des fichiers déjà hébergés et vérification de l’absence de publication concurrente avant activation.

## Périmètre et limites

Cette APK inclut les changements Android locaux, notamment les améliorations de messagerie, de jeux et des formulaires Marketplace/Ticketbulk décrites dans les rapports précédents. Ce constat ne signifie pas que toutes les fonctions de WAPI ont été validées de bout en bout.

**Le service `wapiCommerce` n’est pas déployé** (vérification de la liste Firebase : 64 fonctions, aucune `wapiCommerce`). Les nouveaux écrans Catalogue/Ticketbulk nécessitent donc leur déploiement serveur pour fonctionner en production. Cela a été signalé avant publication. Aucune fonction, règle Firestore, règle Storage, configuration de paiement ou ressource serveur n’a été modifiée dans cette publication d’APK.

Pas de publication iOS/App Store/Play Store et pas de commit ou push Git dans cette opération. Les essais d’installation et d’appels sur téléphones physiques restent nécessaires.
