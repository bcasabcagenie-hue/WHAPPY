# WAPI Android 2.1.29 — publication du 30 août 2026

## Artefact vérifié

- Application : `com.whappy.chat` ; version `2.1.29-native`, code `2106` (précédent : `2105`).
- Fichier : `public-hosting/WAPI-Android-2.1.29-native.apk`, 46 758 577 octets.
- SHA-256 : `6f379a8838f175a06ab2521cc62332811e6cb904686f4dbe5bd4283c2ed45cf0`.
- Certificat SHA-256 : `01d828dc518cb4fa2e61b83b253e2508fb37fb08cb7ef4b43ba43a50c7dd8bf2`, identique aux versions précédentes.
- Signature vérifiée v1/v2 ; manifeste sans activité de test visuel, `debuggable` ni `testOnly`.
- Compilation Release et 84 tests JVM réussis. Copie de publication identique à la sortie de compilation (`cmp`).

## Contenu et limites

Inclut les modifications Android présentes au moment de la compilation : main gantée pour la blanche, poches encastrées, matériaux et bandes retravaillés, tableau de joueurs, visée fine et interface du profil joueur. Les trois tests tactiles du billard ont réussi avant le seul changement de numéro de version, voir [le rapport du gant et des poches](pool-glove-pockets-2026-08-30.md).

Publication limitée à l’APK : les 20 fichiers et la configuration Hosting existants sont préservés ; ajout du seul fichier APK et de ses en-têtes. Aucun commit/push, aucun déploiement iOS, fonctions, règles Firestore/Storage ou modifications locales du site.

La sauvegarde cloud du nouveau profil joueur (`saveGameProfile`) et la nouvelle attribution de points serveur restent à déployer. `wapiCommerce` reste également à déployer. Les championnats avec argent et le multijoueur iOS ne sont pas annoncés comme opérationnels. Le problème de sélection aux dames documenté dans la passe précédente n’est pas déclaré corrigé par cette publication.

## Publication

- Source live vérifiée : `a01fee69b2bbb174`.
- Version Hosting activée : `06c6658c361d3974`.
- Release live : `1788123426471000`.
- URL : https://whappy-d97e7.web.app/WAPI-Android-2.1.29-native.apk
- Les 20 fichiers précédents sont conservés avec leurs empreintes inchangées ; seul le nouvel APK est ajouté.
- HTTP `200`, Content-Type APK, Content-Disposition pièce jointe, Cache-Control sans cache vérifiés.
- Empreinte gzip / ETag : `6c39c1222e54e33ab41c210e89f240f60a2aeb140d1edc0a3963010aee682c78`.
- Vérification publique complète réussie : HTTP `200`, 46 758 577 octets après décompression, SHA-256 identique à l’APK signé, comparaison octet par octet (`cmp`) réussie.
- La première requête a expiré après 180 secondes et 34 408 314 octets ; le serveur ne prenant pas en charge la reprise par plage HTTP, une nouvelle requête HTTP/1.1 avec gzip et délai de 600 secondes a terminé le contrôle (45 681 850 octets transférés). Aucune nouvelle publication ni modification de l’APK n’a été nécessaire.

Fichiers de cette publication : `android/app/build.gradle`, `firebase.json` (en-têtes de l’APK), l’APK signé et ce rapport. Toutes les modifications préexistantes sont conservées.
