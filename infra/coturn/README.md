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
