# WAPI Flutter

Le client Flutter est la cible unique de migration Android et iOS. Il utilise
les mêmes projets Firebase, collections, fonctions callable et règles d'accès
que les clients Kotlin et Swift : aucune donnée n'est copiée ou remplacée.

Les clients natifs restent la référence de production tant que la parité d'un
parcours n'a pas été validée sur Android et iOS. Le détail de cet engagement
est dans MIGRATION_PARITY.md.

## Vérifier le client

    flutter pub get
    flutter analyze
    flutter test

Le module TicketBulk est le premier mini-produit migré : découverte
d'événements, création conditionnée par un compte Business, affiche, capacité,
réservation et QR personnel utilisent directement wapiCommerce.
