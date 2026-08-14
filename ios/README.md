# WHAPPY pour iOS

Application native SwiftUI ciblant iOS 17 et versions suivantes.

Depuis une conversation, les boutons téléphone et vidéo lancent respectivement
l’appel audio dans l’app Téléphone et l’appel vidéo dans FaceTime. Cette fonction
nécessite un iPhone configuré (elle n’est pas disponible dans le simulateur).

## Compiler pour le simulateur

```bash
cd ios
ruby generate_project.rb
xcodebuild -project Whappy.xcodeproj -scheme Whappy -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Installer sur un iPhone ou publier avec TestFlight

1. Ouvrir `Whappy.xcodeproj` dans Xcode.
2. Dans **Signing & Capabilities**, sélectionner l’équipe Apple Developer du propriétaire de WHAPPY.
3. Brancher un iPhone et choisir **Run**, ou utiliser **Product > Archive** puis **Distribute App**.

Le bundle identifier de la version 1.2.1 est `com.whappy.chat`.
