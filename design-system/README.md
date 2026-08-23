# WAPI Native Design System

`wapi.tokens.json` est la source de vérité visuelle commune. Android et iOS
restent des applications natives et traduisent ces mêmes valeurs dans leurs
types de plateforme (`Color`/`Dp` et `SwiftUI.Color`/`CGFloat`).

Règles :

- interface blanche, lisible et dense, avec le bleu WAPI réservé aux actions ;
- navigation, champs, feuilles et transitions suivent les conventions natives ;
- badge de certification gris, badge Live rouge ;
- aucun composant Web embarqué pour reproduire l’interface principale ;
- les changements de token doivent être appliqués aux deux adaptateurs natifs.
