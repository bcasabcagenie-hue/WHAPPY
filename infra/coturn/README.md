# Relais TURN WAPI auto-hébergé

Ce service rend les appels WebRTC WAPI fiables sur les réseaux mobiles, NAT et Wi-Fi d'entreprise, sans louer de plateforme média.

## Pré-requis

- un serveur Linux WAPI avec une IP publique fixe ;
- un sous-domaine `turn.votre-domaine` pointant vers cette IP ;
- un certificat TLS Let’s Encrypt dans `/etc/letsencrypt/live/<domaine>` ;
- les ports UDP/TCP `3478`, TCP `5349` et UDP `49152-65535` ouverts dans le pare-feu et chez l'hébergeur.

## Déploiement

1. Copier `.env.example` vers `.env` et renseigner le domaine, l'IP et un secret aléatoire d'au moins 64 caractères.
2. Installer Docker Compose puis lancer `docker compose up -d` depuis ce dossier.
3. Enregistrer les secrets Firebase, hors Git :

```sh
firebase functions:secrets:set WAPI_TURN_URLS
# turns:turn.votre-domaine:5349?transport=tcp,turn:turn.votre-domaine:3478?transport=udp
firebase functions:secrets:set WAPI_TURN_SHARED_SECRET
```

4. Déployer ensuite uniquement les fonctions Firebase. Chaque appareil reçoit un identifiant TURN signé, associé à son compte WAPI et valable une heure.

Ne réutilisez pas les identifiants statiques dans l’APK. La fonction `getWebRtcIceServers` calcule le mot de passe TURN via HMAC-SHA1 selon le mécanisme d’authentification partagée coturn.

## Exécution gratuite sur un Mac WAPI

Le fichier `docker-compose.macos.yml` permet d'exécuter le relais sur une machine
WAPI existante, sans abonnement média. Il limite volontairement la plage relais à
`49160-49200/udp` et expose uniquement `3478/tcp`, `3478/udp` et cette plage.

1. Copier `macos.env.example` vers `.env.macos`, un fichier local non versionné.
2. Remplacer l'IP d'exemple par l'IP publique de la connexion.
3. Générer un secret aléatoire d'au moins 64 caractères.
4. Lancer le service avec le fichier macOS.
5. Autoriser explicitement les mêmes ports dans le pare-feu macOS et le routeur.

Cette option exige que le Mac reste allumé et que son IP publique reste stable.
Sans redirection des ports du routeur, le relais fonctionne seulement sur le réseau
local et ne peut pas fiabiliser les appels entre deux réseaux mobiles.

### Service natif recommandé sur ce Mac

Docker/Colima peut publier le port TCP tout en perdant le trafic UDP TURN. Le
service natif évite cette couche et gère aussi les changements d’IP publique :

```sh
chmod +x wapi-turn-supervisor.sh install-macos-relay.sh
./install-macos-relay.sh
```

Le superviseur :

- récupère le secret partagé depuis Firebase Secret Manager sans l’afficher ;
- redémarre coturn lorsque l’adresse LAN ou publique change ;
- publie toutes les quatre minutes un heartbeat privé dans
  `systemConfig/webrtcRelay` ;
- fait expirer automatiquement l’adresse annoncée aux applications après douze
  minutes sans heartbeat ;
- laisse la fonction Firebase tester le port TURN TCP depuis Internet avant de
  remettre le relais aux téléphones, afin d’éviter les attentes sur une adresse
  locale active mais non joignable ;
- limite le relais aux ports UDP `49160-49200` et bloque les destinations
  privées afin d’éviter qu’il serve de proxy vers le réseau local.

Le routeur doit rediriger vers le Mac les ports `3478/TCP`, `3478/UDP` et la
plage `49160-49200/UDP`. Cette opération est indispensable : aucun code WebRTC
ne peut traverser un NAT opérateur si le routeur refuse le trafic entrant.
