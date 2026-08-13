# Whappy

Whappy est une application locale de messagerie moderne, conçue comme un projet autonome pour Visual Studio Code.

## Fonctionnalités disponibles

- liste et recherche des discussions ;
- sélection d'une conversation ;
- envoi local de messages ;
- ajout d'emojis et menu de pièces jointes ;
- simulation d'enregistrement vocal ;
- compteur de messages non lus ;
- mode clair ou sombre mémorisé sur l'appareil ;
- interface adaptée aux ordinateurs, tablettes et téléphones ;
- logo officiel Whappy intégré.

## Ouvrir le projet dans Visual Studio Code

Ouvrez le dossier suivant dans VS Code :

```text
/Users/cyrilbokilo/Documents/ChatGPT/WHAPPY
```

Le panneau **Exécuter et déboguer** contient la configuration **Whappy : lancer en local**. Elle démarre automatiquement l'application et ouvre sa page dans le navigateur.

## Installation manuelle

Prérequis : Node.js 22.13 ou une version plus récente.

```bash
npm install
npm run dev
```

L'application devient disponible à l'adresse [http://localhost:3000](http://localhost:3000).

## Commandes utiles

```bash
npm run dev       # lancer Whappy en développement
npm run build     # produire et vérifier la version finale
npm run lint      # vérifier la qualité du code
npm test          # compiler puis exécuter les tests
npm run check     # exécuter toutes les vérifications
```

## Structure principale

- `app/page.tsx` : logique et interface de la messagerie ;
- `app/globals.css` : identité visuelle et adaptation mobile ;
- `app/layout.tsx` : titre, description et icône de l'application ;
- `public/whappy-logo.svg` : logo officiel ;
- `.vscode/` : configuration prête pour Visual Studio Code ;
- `tests/` : tests automatiques de l'application.

## À propos des données

Cette première version fonctionne entièrement en local. Les discussions de démonstration sont intégrées à l'interface et le thème est mémorisé dans le navigateur. Pour échanger réellement entre plusieurs personnes, une prochaine version devra ajouter des comptes utilisateurs, un serveur temps réel, une base de données et le stockage sécurisé des fichiers.
