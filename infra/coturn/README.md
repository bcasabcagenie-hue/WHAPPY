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
