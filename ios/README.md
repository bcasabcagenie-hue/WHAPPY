# WAPI pour iOS

Application native SwiftUI ciblant iOS 17 et connectée au même backend Firebase
que le client Android Kotlin.

Le Live utilise LiveKit Swift via Swift Package Manager, le SFU WebRTC WAPI
auto-hébergé et des jetons temporaires délivrés par Firebase Functions. Les
salons de démonstration locaux ne sont plus présentés comme des directs réels.

## Compiler pour le simulateur

```bash
ruby generate_project.rb
pod install
xcodebuild -workspace Whappy.xcworkspace -scheme Whappy \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Pour limiter l’espace disque pendant une vérification locale :

```bash
xcodebuild -workspace Whappy.xcworkspace -scheme Whappy \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  ONLY_ACTIVE_ARCH=YES COMPILER_INDEX_STORE_ENABLE=NO \
  CODE_SIGNING_ALLOWED=NO build
```

## Publier

1. Ouvrir `Whappy.xcworkspace` dans Xcode.
2. Sélectionner l’équipe Apple Developer du propriétaire WAPI.
3. Utiliser **Product > Archive** puis **Distribute App**.

Bundle identifier : `com.whappy.chat`.
