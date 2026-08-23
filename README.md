# WAPI — clients mobiles natifs

WAPI est maintenant livré avec deux clients mobiles natifs :

- Android : Kotlin, Jetpack Compose, Firebase et WebRTC/LiveKit ;
- iOS : Swift, SwiftUI, Firebase et WebRTC/LiveKit ;
- design partagé : `design-system/wapi.tokens.json`, adapté aux composants de
  chaque plateforme sans reproduire une interface Web.

Le dossier `wapi_flutter/` est conservé uniquement comme archive de migration.
Il n’entre plus dans la compilation, la signature ni la page de téléchargement
officielle. Il sera supprimé lorsque la parité fonctionnelle aura été validée
sur appareils Android et iOS.

## Architecture

- `android/` : application Android officielle, package `com.whappy.chat` ;
- `ios/` : application iOS officielle, bundle `com.whappy.chat` ;
- `design-system/` : couleurs, espacements, rayons et règles de marque communs ;
- `functions/` : fonctions Firebase, notifications, contrôle d’accès et jetons
  Live éphémères ;
- `infra/livekit/` : SFU WebRTC WAPI auto-hébergé ;
- `firestore.rules` et `storage.rules` : autorisations côté serveur ;
- `app/` et `lib/` : surfaces Web/administration, pas le client mobile.

## Android

Prérequis : JDK 17 et Android SDK 35.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

APK : `android/app/build/outputs/apk/release/app-release.apk`.

## iOS

Prérequis : Xcode 16.3 ou plus récent, Ruby `xcodeproj` et CocoaPods.
LiveKit est résolu avec Swift Package Manager ; Firebase reste actuellement
intégré par CocoaPods.

```bash
cd ios
ruby generate_project.rb
pod install
xcodebuild -workspace Whappy.xcworkspace -scheme Whappy \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

La publication TestFlight nécessite l’équipe Apple Developer du propriétaire.

## Live natif

Les deux clients utilisent les mêmes fonctions sécurisées : création et
visibilité du salon, jetons courts, présence, réactions, modération et fin de
session. La caméra et le micro transitent par le SFU WebRTC ; aucun secret
LiveKit n’est intégré dans les applications.

Le serveur doit être provisionné avec :

- `WAPI_LIVEKIT_URL` ;
- `WAPI_LIVEKIT_API_KEY` ;
- le secret Firebase `WAPI_LIVEKIT_API_SECRET`.

Sans ces paramètres, l’application affiche une erreur explicite et ne simule
pas un faux direct.

## Vérifications

```bash
cd functions && npm run check
cd ../android && ./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Après toute modification visuelle, synchroniser les adaptateurs Android et iOS
avec `design-system/wapi.tokens.json`.
