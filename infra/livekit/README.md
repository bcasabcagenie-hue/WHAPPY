# WAPI Live — SFU WebRTC auto-hébergé

Ce dossier fournit l’infrastructure média nécessaire à une diffusion réelle. WAPI utilise LiveKit Server comme **SFU open source auto-hébergé** : Android est natif Kotlin/Compose, iOS est natif Swift/SwiftUI, Firebase gère l’identité et les droits, et le serveur média transporte la caméra et le micro.

## Architecture

1. Le client mobile natif appelle `createLiveSession` ou `joinLiveSession` avec l’identité Firebase.
2. La Cloud Function vérifie l’utilisateur, la visibilité et le rôle.
3. Elle signe un jeton LiveKit valable dix minutes. La clé secrète ne quitte jamais le serveur.
4. L’animateur peut publier caméra et micro. Un spectateur peut uniquement s’abonner au flux.
5. Firestore conserve les métadonnées du direct et les commentaires, pas le média.
6. LiveKit transporte la vidéo via WebRTC et son TURN intégré aide les appareils situés derrière des réseaux mobiles ou des pare-feu.
7. Un webhook signé réconcilie les arrivées, départs et fins de salle avec Firestore ; l'APK envoie aussi une présence de secours.

## Essai local

Docker Desktop doit être démarré.

```bash
cd infra/livekit
docker compose -f docker-compose.dev.yml up -d
curl http://127.0.0.1:7880
```

Le profil local utilise `devkey` / `wapi-local-only-secret-32-characters` et ne doit jamais être exposé sur Internet. Depuis l’émulateur Android, l’hôte macOS est accessible via `10.0.2.2`. Un téléphone physique doit utiliser l’adresse LAN du Mac et être sur le même réseau.

## Production

Un Live professionnel nécessite obligatoirement :

- une VM Linux publique avec IP fixe et bande passante élevée ;
- deux sous-domaines DNS, par exemple `live.example.com` et `turn.example.com` ;
- des certificats TLS émis par une autorité reconnue ;
- les ports `443/tcp`, `80/tcp`, `7881/tcp`, `3478/udp` et `50000-60000/udp` ouverts ;
- Redis pour l’état distribué ;
- métriques, alertes, sauvegarde de configuration et rotation des secrets.

Le script GCE augmente aussi les tampons UDP du noyau à 5 Mo. L'avertissement
peut rester visible dans Docker Desktop/Colima en développement, mais ne doit
pas être ignoré sur la VM de production.

Utiliser le générateur de configuration de production officiel afin d’obtenir Docker Compose, Caddy, Redis et la configuration TURN/TLS adaptée au domaine :

```bash
mkdir -p generated
docker pull livekit/generate
docker run --rm -it -v "$PWD/generated:/output" livekit/generate
```

Déployer les fichiers générés sur la VM puis vérifier le WebSocket, WebRTC, TURN et la reconnexion avant d’ouvrir la fonctionnalité aux utilisateurs.

## Configuration Firebase

Après la mise en ligne du serveur, configurer les paramètres et le secret utilisés par les fonctions :

```bash
firebase functions:secrets:set WAPI_LIVEKIT_API_SECRET --project whappy-d97e7
firebase deploy --only functions --project whappy-d97e7
```

Les paramètres non secrets attendus sont :

```text
WAPI_LIVEKIT_URL=wss://live.example.com
WAPI_LIVEKIT_API_KEY=<clé publique du serveur>
```

La configuration LiveKit de production doit déclarer le webhook signé :

```yaml
webhook:
  api_key: <même valeur que WAPI_LIVEKIT_API_KEY>
  urls:
    - https://europe-west1-whappy-d97e7.cloudfunctions.net/livekitWebhook
```

La version serveur est épinglée à `v1.12.0`, version stable disponible lors de
la mise à jour. Tester toute montée de version en préproduction, notamment les
changements d'authentification TURN.

Ne jamais placer `WAPI_LIVEKIT_API_SECRET` dans un client mobile, dans un fichier suivi par Git ou dans l’APK.

## Validation avant ouverture publique

- animateur Android réel → spectateur Android réel sur deux opérateurs différents ;
- refus caméra/micro puis nouvelle autorisation ;
- bascule caméra avant/arrière et micro muet ;
- perte réseau de 20 secondes puis reconnexion ;
- spectateur interdit de publication ;
- direct `contacts` et `private` refusé aux comptes non autorisés ;
- fermeture animateur, passage Firestore à `ended` et sortie des spectateurs ;
- compteur remis à zéro après fermeture forcée de l'APK ou perte réseau ;
- réactions limitées, signalement, expulsion temporaire et blocage permanent ;
- rejet d'un jeton spectateur tentant de publier une piste audio ou vidéo ;
- charge progressive avec métriques CPU, mémoire, paquets perdus et débit sortant.
