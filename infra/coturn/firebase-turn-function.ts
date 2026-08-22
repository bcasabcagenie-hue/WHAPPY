/**
 * À déplacer dans un codebase Firebase dédié lorsque le serveur coturn WAPI
 * est prêt. Il est volontairement isolé des notifications afin qu’aucun secret
 * TURN inexistant ne bloque leur déploiement.
 */
import { createHmac } from "node:crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";

const turnUrls = defineSecret("WAPI_TURN_URLS");
const turnSharedSecret = defineSecret("WAPI_TURN_SHARED_SECRET");

export const getWebRtcIceServers = onCall(
  { secrets: [turnUrls, turnSharedSecret], timeoutSeconds: 15 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
    }
    const urls = turnUrls
      .value()
      .split(",")
      .map((url) => url.trim())
      .filter((url) => /^turns?:/i.test(url));
    const secret = turnSharedSecret.value();
    if (!urls.length || !secret) {
      throw new HttpsError(
        "failed-precondition",
        "Relais d’appel WAPI non configuré.",
      );
    }
    const expiresAt = Math.floor(Date.now() / 1000) + 60 * 60;
    const username = `${expiresAt}:${request.auth.uid}`;
    const credential = createHmac("sha1", secret)
      .update(username)
      .digest("base64");
    return {
      iceServers: [
        { urls: ["stun:stun.l.google.com:19302"] },
        { urls, username, credential },
      ],
      expiresAt,
    };
  },
);
