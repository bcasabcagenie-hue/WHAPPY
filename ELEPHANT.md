# ELEPHANT

ELEPHANT est le moteur de diffusion publicitaire de WAPI. Sa mission est de
répartir les campagnes de manière locale, utile, explicable et équitable sans
analyser les messages privés, les contacts ou des caractéristiques sensibles.

## Signaux autorisés

- emplacement publicitaire demandé ;
- pays et ville déclarés dans le profil ;
- catégorie publique de la page Business ;
- fraîcheur de la campagne ;
- impressions, clics, rejets et conversions publicitaires ;
- cadence et quota achetés par l'annonceur ;
- interactions publicitaires de l'utilisateur connecté.

## Garde-fous

- trois impressions maximum par campagne et par utilisateur sur sept jours ;
- campagne masquée pendant sept jours après un rejet ;
- une seule campagne par propriétaire et par page dans un lot de résultats ;
- exclusion de la propre campagne d'un annonceur ;
- arrêt au quota total et au plafond quotidien ;
- pénalité des créations fréquemment masquées ;
- exploration déterministe pour donner une chance aux nouveaux annonceurs ;
- raisons de classement transmises aux applications Android et iOS.

## Version

La version active est `ELEPHANT 1.0`. Toute évolution du score doit conserver
les tests de confidentialité, de fréquence, de cadence, de diversité et de
facturation par diffusion.
