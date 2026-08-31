# Validation — Récents et tableau fondateur

## Récents

- Le geste est limité à la liste Messages, sans recherche active et uniquement au sommet de la liste.
- Android et iOS ouvrent directement une vue plein écran : aucune liste « Récents » partielle n’est insérée dans les conversations.
- Le seuil est franchi pendant le geste, avec retour haptique. L’élan natif du défilement n’est plus absorbé sur Android.
- La fermeture est réversible par un glissement vers le haut ou par le bouton fermer.

## Tableau fondateur

- L’appel `getFounderDashboard` reste réservé au numéro fondateur authentifié.
- Les paiements confirmés sont agrégés côté serveur : aucune limite de lecture locale n’est utilisée pour le CA ou le nombre de commandes.
- Les factures Business sont affichées comme créances ouvertes et restent séparées du CA encaissé.
- Les téléchargements Play/App Store restent libellés « Non relié » jusqu’à la connexion des consoles officielles.

## Vérifications effectuées

- `npm run check` et `npm test` dans `functions/`.
- `:app:compileDebugKotlin` et `:app:testDebugUnitTest` sur Android.
- Compilation simulateur iOS avec `xcodebuild`.

Cette modification ne déploie ni fonction Firebase ni APK.
