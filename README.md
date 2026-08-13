# Whappy

Whappy est un réseau d'opportunités autonome : on peut vendre, troquer, chercher, négocier et présenter ses produits en direct. Le projet est prêt à être développé dans Visual Studio Code et utilise l'identité noire et vert néon du logo officiel.

## Expérience disponible

- **Orbite** : radar visuel des produits, besoins, directs et échanges proches ;
- **Directs** : studio de live shopping, fiche produit, panier et offres en direct ;
- **Marché** : catalogue filtrable pour vendre ou proposer un échange ;
- **Troc** : moteur de correspondance et concept de troc en chaîne ;
- **Chercher** : publication de besoins, services et situations urgentes ;
- **Messages** : négociation contextualisée autour d'une transaction ;
- **Mon Double** : parcours de création d'un présentateur vidéo numérique pour ses propres produits.
- **Connexion téléphone** : inscription sans mot de passe, code SMS et règle « un numéro = un compte » avec Firebase Phone Auth.

Le Double vidéo exige un consentement explicite, reste révocable, affiche son caractère artificiel et ne doit utiliser que l'image ou la voix dont la personne contrôle les droits.

## Ouvrir dans Visual Studio Code

Ouvrez ce dossier :

```text
/Users/cyrilbokilo/Documents/ChatGPT/WHAPPY
```

La configuration **Whappy : lancer en local** du panneau **Exécuter et déboguer** démarre l'application et ouvre sa page.

## Démarrage manuel

Prérequis : Node.js 22.13 ou une version plus récente.

```bash
npm install
npm run dev
```

L'application est disponible sur [http://localhost:3000](http://localhost:3000).

## Vérification

```bash
npm run check
```

Cette commande vérifie le code, produit la version finale et exécute les tests.

## Firebase

Whappy est relié au projet Firebase indépendant `whappy-d97e7`.

- `lib/firebase.ts` initialise Authentication, Firestore et Storage ;
- `.env.local` contient la configuration locale de l'application Web ;
- `firestore.rules` et `storage.rules` fournissent les règles de sécurité ;
- `firestore.indexes.json` contient les index nécessaires.

Les clés Firebase Web identifient l'application ; les autorisations réelles restent contrôlées par Authentication et les règles de sécurité.

## Fichiers principaux

- `app/page.tsx` : expérience interactive et espaces Whappy ;
- `app/globals.css` : design responsive et identité visuelle ;
- `app/layout.tsx` : métadonnées, icône et carte sociale ;
- `public/whappy-logo.svg` : logo officiel ;
- `public/whappy-social.png` : visuel de partage ;
- `.vscode/` : lancement prêt pour Visual Studio Code ;
- `tests/` : tests automatiques.

## Portée de cette version

L'interface et ses interactions constituent un prototype produit complet. Le streaming vidéo réel, la synthèse du Double, les paiements, la modération et la mise en relation en production demanderont ensuite des services backend dédiés et des contrôles de sécurité supplémentaires.
