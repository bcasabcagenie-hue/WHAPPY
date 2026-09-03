# Contrat de migration Flutter

Flutter devient le client commun WAPI pour Android et iOS. Les applications
Kotlin et Swift restent actives pendant la transition : elles ne sont ni
supprimées ni remplacées avant la validation fonctionnelle et visuelle sur les
deux plateformes.

## Règles non négociables

- Même compte Firebase, mêmes collections, mêmes fonctions cloud et mêmes
  règles de sécurité : aucune migration de données n'est nécessaire.
- Les actions sensibles restent côté serveur : accès QR, compteurs,
  attribution de droits, appels, directs, publicité et paiements futurs.
- Un module ne remplace le client actuel qu'après tests Android et iOS sur
  appareil réel, y compris reprise réseau, notifications et retour arrière.
- Les paiements Mobile Money restent désactivés tant que la configuration
  régionale, la conformité et la confirmation utilisateur ne sont pas prêtes.

## État des parcours

| Parcours | Base Flutter existante | Étape de migration |
| --- | --- | --- |
| Connexion, session, profil, présence | Firebase Auth et Firestore déjà actifs | validation et design unifié |
| Messages, médias, notes vocales, groupes | client existant | découpage en modules, fiabilité hors-ligne |
| Stories, chaînes, radio, QR contacts | client existant | parité de publication et aperçu |
| Appels individuels et groupe | client existant | reprise appel, sonnerie, arrière-plan et appareils réels |
| Direct et commentaires | client existant | parité invitations, commentaires et modération |
| Business, catalogue, factures, publicité | tableau Flutter connecté : catalogue, factures, créances et relances validées par le Business | paiements Mobile Money après conformité et configuration régionale |
| Marketplace et boutiques | parcours Flutter Business : annonces, photos, réponses, offres, enchères et boost préparé | paiement et règlement à activer après prestataire validé |
| TicketBulk | module mobile : découverte, création, billet QR, annulation et contrôle organisateur par caméra/code | tests appareil, gestion des équipes d’accès et exports organisateur |
| WIA/Pilotis | point d'entrée existant | raccordement au même historique et aux réglages Pilotis |
| King QI et profils joueurs | salon Flutter relié aux fonctions serveur | écran de match, manches et questions chronométrées |
| Wapi Pool | salon et table Flutter reliés au moteur serveur ; puissance latérale, profils, billes restantes, rayées/pleines, poches et placement de blanche | animations physiques, sons, profils avancés et tournois |
| Ludo WAPI | jeu Flutter local : deux dés, pions, captures, IA et victoire | salons WAPI, synchronisation et tournois |
| Échecs WAPI | jeu Flutter local : échiquier, IA, deux joueurs, coups légaux, roque, prise en passant, promotion et échec | profils, salons WAPI, classement et tournois |
| Dames WAPI | jeu Flutter local : damier 10 × 10, prises obligatoires, enchaînements, dames et IA | profils, salons WAPI, classement et tournois |
| Cartes WAPI | jeu Flutter local : Bataille, 26 manches, IA ou deux joueurs et score | tables WAPI, amis et tournois |
| Poker WAPI | jeu Flutter local : mains de cinq cartes, classement et IA | tables WAPI, profils et parties entre amis |
| Défi du jour | quiz Flutter chronométré : 5 questions, XP et reprise immédiate | score quotidien serveur, classement et récompenses |
| Mots & idées | jeu Flutter local : lettres mélangées, validation et XP | co-opératif, dictionnaire et salons WAPI |
| Services WAPI | demandes Flutter persistantes : transport, livraison, assistance | mise en relation Business et suivi opérationnel |

## Première livraison Flutter : TicketBulk

Le fichier du module TicketBulk est une application mobile interne, sans vue
web :

- découverte et recherche d'événements ;
- réservation via la fonction sécurisée wapiCommerce ;
- billet personnel QR ;
- création guidée de l'identité Business quand elle manque ;
- création d'événement avec affiche, capacité, date, lieu et type de billet ;
- lecture des compteurs organisateur.

Les mêmes endpoints contrôlent les capacités, la génération QR et les
réservations : le client ne peut ni réserver une place inexistante ni créer un
faux billet.

## Centre Jeux Flutter

Le centre Jeux est maintenant une entrée native Flutter dans WAPI :

- profils King QI et Wapi Pool lus depuis les fonctions serveur ;
- création et jonction réelle aux salons King QI ;
- tournoi King QI : attente, démarrage contrôlé par l’organisateur, questions
  chronométrées, réponses, score en direct et remise de coupe ;
- matchmaking Wapi Pool, création de table privée et jonction par code ;
- table Wapi Pool en direct : deux profils, tours, chronomètre, bille en main,
  visée, puissance, tir validé côté serveur et sortie explicite de la partie.

La simulation, les tours, les résultats et les statistiques restent contrôlés
par les fonctions WAPI. Le client Flutter ne calcule jamais le résultat d’un
tir ni l’attribution d’une victoire.

## Ordre de migration

1. Stabiliser le socle Flutter et terminer TicketBulk, Business et Marketplace.
2. Porter la navigation, profils, conversations, médias et réglages avec le
   design mobile WAPI.
3. Valider appels, directs et notifications Android/iOS.
4. Porter WIA/Pilotis et les parcours créateurs.
5. Porter les salons, profils joueurs et interfaces des jeux en conservant les
   règles et fonctions serveur existantes.
6. Comparer chaque écran aux clients actuels, tester sur appareils, puis
   publier Flutter seulement après validation de parité.
