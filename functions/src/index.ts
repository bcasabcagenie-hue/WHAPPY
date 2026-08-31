import { getApps, initializeApp } from "firebase-admin/app";
import { AggregateField, FieldValue, Timestamp, getFirestore, type DocumentSnapshot } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { randomUUID } from "node:crypto";
import { connect as connectTcp } from "node:net";
import {
  AccessToken,
  RoomServiceClient,
  WebhookReceiver,
} from "livekit-server-sdk";
import { createTurnIcePayload, selectTurnUrls, turnTcpEndpoint } from "./webrtcTurn";
import { decodeGroupPhotoBase64 } from "./groupPhoto";
import { gameIds, playerCustomization, publicPlayer, poolMatchAward } from "./gamePlayer";
import { callPushState } from "./callState";
import { handleCommerce } from "./commerce";
import {
  WAPI_AD_PRICE_PER_THOUSAND,
  ELEPHANT_ALGORITHM_NAME,
  ELEPHANT_ALGORITHM_VERSION,
  isWapiDeliveryComplete,
  priceWapiAdDelivery,
  rankElephantAds,
  type WapiAdCandidate,
  type WapiAdSignal,
} from "./adRanking";
import {
  initialPoolBalls,
  resolvePoolShot,
  sanitizePoolBalls,
  simulatePoolShot,
  validatePoolShot,
  validPoolCuePlacement,
  type PoolGroup,
} from "./poolGame";

if (!getApps().length) initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 20, memory: "256MiB" });

const db = getFirestore();
export const wapiCommerce = onCall({ timeoutSeconds: 60 }, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connectez-vous à WAPI.");
  return handleCommerce(db, request.auth.uid, request.data || {});
});
const livekitServerUrl = defineString("WAPI_LIVEKIT_URL", { default: "" });
const livekitApiKey = defineString("WAPI_LIVEKIT_API_KEY", { default: "" });
const livekitApiSecret = defineSecret("WAPI_LIVEKIT_API_SECRET");
// Lingwap is a self-hosted relay: speech-to-text -> translation -> optional TTS.
// The mobile client never receives the relay credential or third-party API keys.
const lingwapRelayUrl = defineString("WAPI_LINGWAP_RELAY_URL", { default: "" });
// Optional until the self-hosted Lingwap relay is provisioned. Keeping this as
// an empty server parameter prevents an unrelated missing token from blocking
// deployments of WIA, Live or notifications. No value is sent to clients.
const lingwapRelayToken = defineString("WAPI_LINGWAP_RELAY_TOKEN", { default: "" });
// Optional self-hosted coturn relay. STUN alone cannot cross every carrier NAT;
// production calls become reliable only when these values point to WAPI's own
// TURN service. Credentials are issued to authenticated clients by this
// callable and never embedded in the mobile binaries.
const webRtcTurnUrls = defineSecret("WAPI_TURN_URLS");
const webRtcTurnSharedSecret = defineSecret("WAPI_TURN_SHARED_SECRET");
// WIA is hosted by Pilotis. The historical secret name remains for deployment
// compatibility; the credential never ships in a client.
const wepiApiKey = defineSecret("WAPI_WEPI_API_KEY");
// Endpoint and model are server parameters, not mobile constants. Pilotis can
// evolve the WIA channel without requiring a new Android or iOS binary.
const wiaEndpoint = defineString("WAPI_WIA_ENDPOINT", {
  default: "https://mypilotis.web.app/wepi-api/v1/chat/completions",
});
const wiaModel = defineString("WAPI_WIA_MODEL", { default: "wepi-ai-nova-1" });
// The mobile language catalog and the server policy deliberately match. Add a
// language here only after the self-hosted Lingwap relay supports it.
const lingwapSupportedLanguages = new Set([
  "fr", "en", "zh-CN", "ar", "ru", "es", "pt", "tr",
  "ja", "it", "nl", "de", "ko", "hi", "sw", "ln",
]);

type PushDevice = {
  token: string;
  platform: "android" | "ios";
  remove: () => Promise<unknown>;
};

async function pushDevices(userIds: string[]): Promise<PushDevice[]> {
  const snapshots = await Promise.all(
    [...new Set(userIds.filter(Boolean))].map((userId) =>
      db.collection("users").doc(userId).collection("devices").where("enabled", "==", true).get(),
    ),
  );
  const devices = new Map<string, PushDevice>();
  snapshots.flatMap((snapshot) => snapshot.docs).forEach((document) => {
    const token = String(document.get("token") || "").trim();
    if (!token || devices.has(token)) return;
    const platform = document.get("platform") === "ios" ? "ios" : "android";
    devices.set(token, { token, platform, remove: () => document.ref.delete() });
  });
  return [...devices.values()];
}

function staleRegistration(errorCode?: string) {
  return errorCode === "messaging/registration-token-not-registered" || errorCode === "messaging/invalid-registration-token";
}

function lingwapLanguage(value: unknown, field: string) {
  const language = String(value || "").trim();
  if (!/^(auto|[a-z]{2}(?:-[A-Z]{2})?)$/.test(language)) {
    throw new HttpsError("invalid-argument", `${field} doit être un code langue valide, par exemple fr, en ou ln.`);
  }
  if (language !== "auto" && !lingwapSupportedLanguages.has(language)) {
    throw new HttpsError("invalid-argument", `${field} n’est pas encore pris en charge par Lingwap.`);
  }
  return language;
}

function isGroupDocument(value: Record<string, unknown>, memberIds: string[]) {
  return value.conversationType === "group" ||
    value.kind === "group" ||
    value.isGroup === true ||
    memberIds.length > 2 ||
    Array.isArray(value.adminIds) ||
    Boolean(String(value.title || "").trim()) ||
    Boolean(String(value.groupPhotoUrl || "").trim());
}

/**
 * Secure translation gateway. The relay is deliberately independent from the
 * app and SFU so a live call can inject translated audio without exposing a
 * provider key to either mobile platform. It is unavailable—not simulated—until a relay is
 * provisioned by the WAPI operator.
 */
export const lingwapTranslateText = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const text = String(request.data?.text || "").trim();
  if (text.length < 1 || text.length > 1_200) {
    throw new HttpsError("invalid-argument", "Le texte à traduire doit contenir entre 1 et 1 200 caractères.");
  }
  const sourceLanguage = lingwapLanguage(request.data?.sourceLanguage || "auto", "La langue source");
  const targetLanguage = lingwapLanguage(request.data?.targetLanguage, "La langue cible");
  if (targetLanguage === "auto") throw new HttpsError("invalid-argument", "Choisissez la langue du destinataire.");

  const relayUrl = lingwapRelayUrl.value().trim();
  const relayToken = lingwapRelayToken.value().trim();
  if (!relayUrl || !relayToken) {
    throw new HttpsError("failed-precondition", "Lingwap attend son relais de traduction auto-hébergé. Aucun texte n’a été envoyé à un service tiers.");
  }

  let response: Response;
  try {
    response = await fetch(`${relayUrl.replace(/\/$/, "")}/v1/translate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${relayToken}`,
        "X-WAPI-User": request.auth.uid,
      },
      body: JSON.stringify({ text, sourceLanguage, targetLanguage, mode: "text" }),
      signal: AbortSignal.timeout(8_000),
    });
  } catch (error) {
    logger.warn("Relais Lingwap indisponible", { userId: request.auth.uid, error: String(error) });
    throw new HttpsError("unavailable", "Lingwap est momentanément indisponible. Réessayez dans un instant.");
  }
  if (!response.ok) {
    logger.warn("Relais Lingwap a refusé une traduction", { userId: request.auth.uid, status: response.status });
    throw new HttpsError("unavailable", "Lingwap ne peut pas traduire ce message pour le moment.");
  }
  const payload = await response.json().catch(() => null) as { translation?: unknown; detectedLanguage?: unknown } | null;
  const translation = String(payload?.translation || "").trim();
  if (!translation || translation.length > 2_400) {
    logger.error("Réponse Lingwap invalide", { userId: request.auth.uid });
    throw new HttpsError("internal", "La réponse Lingwap est invalide.");
  }
  return {
    translation,
    detectedLanguage: String(payload?.detectedLanguage || sourceLanguage).slice(0, 12),
    targetLanguage,
  };
});

type WepiHistoryItem = {
  fromUser?: unknown;
  text?: unknown;
};

type WepiPayload = {
  choices?: Array<{ message?: { content?: unknown } }>;
  content?: unknown;
};

function wepiSystemPrompt(
  settings: Record<string, unknown>,
  pages: Array<Record<string, unknown>>,
  deals: Array<Record<string, unknown>>,
  catalog: Array<Record<string, unknown>> = [],
) {
  const storedAssistantName = String(settings.assistantName || "WIA").trim().slice(0, 60) || "WIA";
  const assistantName = /^(wepi|assistant wapi)$/i.test(storedAssistantName) ? "WIA" : storedAssistantName;
  const tone = String(settings.tone || "chaleureux").trim().slice(0, 30) || "chaleureux";
  const businessName = String(settings.businessName || "").trim().slice(0, 100);
  const instructions = String(settings.instructions || "").trim().slice(0, 600);
  const salesAutomation = settings.salesAutomation === true;
  const captureOrders = settings.captureOrderRequests !== false;
  const humanHandoff = settings.humanHandoff !== false;
  const deliveryPolicy = String(settings.deliveryPolicy || "").trim().slice(0, 400);
  const pageContext = pages.slice(0, 5).map((page) =>
    `${String(page.name || "Page WAPI").slice(0, 100)} (${String(page.category || "activité").slice(0, 60)}, ${String(page.city || "zone non précisée").slice(0, 80)})`,
  ).join(" ; ");
  const catalogContext = deals.filter((deal) => String(deal.status || "active") === "active").slice(0, 30).map((deal) => {
    const stock = Math.max(0, Number(deal.stock || 0) - Number(deal.sold || 0));
    return `${String(deal.title || "Produit").slice(0, 120)} — ${Number(deal.dealPrice || 0)} XAF — stock ${stock} — ${String(deal.description || "").slice(0, 180)}`;
  }).join("\n");
  const marketplaceCatalog = catalog.filter((item) => item.available === true).slice(0, 40).map((item) =>
    `${String(item.name || "Article").slice(0, 100)} — ${Number(item.priceMinor || 0)} ${String(item.currency || "XAF").slice(0, 3)} — ${String(item.category || "Autres").slice(0, 60)} — ${String(item.description || "").slice(0, 180)}`,
  ).join("\n");
  return [
    `Tu es ${assistantName}, propulsé par WIA, le même moteur conversationnel que WIA Chat dans Pilotis.`,
    "MODE GÉNÉRAL — ESPACE CHAT.",
    `Réponds avec un ton ${tone}, de façon claire, utile et naturelle, dans la langue de l’utilisateur.`,
    "Commence par la réponse directement utile, puis ajoute seulement le contexte nécessaire et une prochaine action concrète.",
    "Si une information indispensable manque, pose une question ciblée au lieu d’inventer.",
    "Maîtrise les sujets généraux, les langues, le raisonnement, les métiers, le développement logiciel, les données, la sécurité, les tests et le déploiement.",
    "Ne prétends jamais avoir exécuté une action, confirmé un prix ou accédé à des données si ce n’est pas établi.",
    businessName ? `Compte business : ${businessName}.` : "",
    instructions ? `Consignes du propriétaire : ${instructions}` : "",
    pageContext ? `Pages Business vérifiées du compte : ${pageContext}.` : "",
    salesAutomation && catalogContext ? `Catalogue actif et prix confirmés :\n${catalogContext}` : "",
    salesAutomation && marketplaceCatalog ? `Catalogue Marketplace vérifié :\n${marketplaceCatalog}` : "",
    salesAutomation ? "Tu peux recommander uniquement les produits présents dans ce catalogue et vérifier leur stock indiqué." : "Ne mène pas de vente automatisée : le mode vente assistée est désactivé.",
    salesAutomation && captureOrders ? "Pour préparer une demande de commande, recueille le produit, la quantité, le nom, la zone et le mode de livraison. Dis clairement que la commande et le paiement restent à confirmer par le vendeur." : "Ne collecte pas de demande de commande.",
    humanHandoff ? "Propose un transfert humain pour paiement, litige, remise, rupture ou information absente." : "N’annonce pas de transfert humain automatique.",
    deliveryPolicy ? `Politique de livraison : ${deliveryPolicy}` : "",
  ].filter(Boolean).join(" ");
}

/**
 * Secure WIA gateway. Pilotis stays the model provider while the Pilotis
 * bearer credential remains on the WAPI server only.
 */
function safeWepiThreadId(value: unknown) {
  const threadId = String(value || "main").trim();
  return /^[A-Za-z0-9_-]{1,64}$/.test(threadId) ? threadId : "main";
}

function safeWepiMessageId(value: unknown) {
  const messageId = String(value || "").trim();
  return /^[A-Za-z0-9_-]{8,96}$/.test(messageId) ? messageId : "";
}

function wepiMessageFromDocument(document: DocumentSnapshot) {
  if (document.get("status") === "failed") return null;
  const role = document.get("role") === "assistant" ? "assistant" : "user";
  const content = String(document.get("content") || "").trim().slice(0, 12_000);
  return content ? { role, content } : null;
}

/** Restores the account-owned WIA thread on every device. */
export const getWepiHistory = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const threadId = safeWepiThreadId(request.data?.threadId);
  const snapshot = await db.doc(`users/${request.auth.uid}/wepiThreads/${threadId}`)
    .collection("messages")
    .orderBy("createdAt", "desc")
    .limit(100)
    .get();
  return {
    threadId,
    messages: snapshot.docs.reverse().flatMap((document) => {
      const item = wepiMessageFromDocument(document);
      return item ? [{ id: document.id, ...item }] : [];
    }),
  };
});

export const askWepi = onCall({ secrets: [wepiApiKey], timeoutSeconds: 60 }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");

  const prompt = String(request.data?.prompt || "").trim();
  if (prompt.length < 1 || prompt.length > 4_000) {
    throw new HttpsError("invalid-argument", "Le message WIA doit contenir entre 1 et 4 000 caractères.");
  }

  const clientHistory = Array.isArray(request.data?.history)
    ? (request.data.history as WepiHistoryItem[]).slice(-6).flatMap((item) => {
      const text = String(item?.text || "").trim().slice(0, 2_000);
      if (!text) return [];
      return [{ role: item?.fromUser ? "user" : "assistant", content: text }];
    })
    : [];
  const threadId = safeWepiThreadId(request.data?.threadId);
  const providerEndpoint = wiaEndpoint.value().trim();
  const providerModel = wiaModel.value().trim() || "wepi-ai-nova-1";
  if (!providerEndpoint.startsWith("https://")) {
    throw new HttpsError("failed-precondition", "Le point d’accès sécurisé WIA n’est pas configuré.");
  }
  const thread = db.doc(`users/${request.auth.uid}/wepiThreads/${threadId}`);
  const [settingsSnapshot, pagesSnapshot, dealsSnapshot, catalogSnapshot, memorySnapshot] = await Promise.all([
    db.doc(`users/${request.auth.uid}/wepi/settings`).get(),
    db.collection("businessPages").where("ownerId", "==", request.auth.uid).limit(10).get(),
    db.collection("businessDeals").where("ownerId", "==", request.auth.uid).limit(40).get(),
    db.collection("businessCatalog").where("ownerId", "==", request.auth.uid).limit(80).get(),
    thread.collection("messages").orderBy("createdAt", "desc").limit(48).get(),
  ]);
  const settings = (settingsSnapshot.data() || {}) as Record<string, unknown>;
  const pages = pagesSnapshot.docs.map((document) => document.data() as Record<string, unknown>);
  const deals = dealsSnapshot.docs.map((document) => document.data() as Record<string, unknown>);
  const catalog = catalogSnapshot.docs.map((document) => document.data() as Record<string, unknown>);
  const persistentHistory = memorySnapshot.docs.reverse().flatMap((document) => {
    const item = wepiMessageFromDocument(document);
    return item ? [item] : [];
  });
  const history = persistentHistory.length ? persistentHistory : clientHistory;
  const requestedMessageId = safeWepiMessageId(request.data?.messageId);
  const userMessage = requestedMessageId
    ? thread.collection("messages").doc(requestedMessageId)
    : thread.collection("messages").doc();
  const assistantMessage = thread.collection("messages").doc(`assistant-${userMessage.id}`);
  await Promise.all([
    userMessage.set({
      role: "user",
      content: prompt,
      status: "pending",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true }),
    thread.set({
      ownerId: request.auth.uid,
      title: prompt.slice(0, 80),
      lastMessage: prompt.slice(0, 240),
      provider: "wia-pilotis",
      model: providerModel,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true }),
  ]);
  const markPromptFailed = async () => {
    await userMessage.set({ status: "failed", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  };
  const apiKey = wepiApiKey.value().trim();
  if (!apiKey) {
    await markPromptFailed();
    throw new HttpsError("failed-precondition", "La connexion sécurisée à WIA n’est pas configurée.");
  }

  let upstream: Response;
  try {
    upstream = await fetch(providerEndpoint, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json; charset=utf-8",
        Accept: "application/json",
      },
      body: JSON.stringify({
        model: providerModel,
        temperature: 0.5,
        max_tokens: 640,
        messages: [
          { role: "system", content: wepiSystemPrompt(settings, pages, deals, catalog) },
          ...history,
          { role: "user", content: prompt },
        ],
      }),
      signal: AbortSignal.timeout(45_000),
    });
  } catch (error) {
    logger.warn("WIA Pilotis indisponible", { userId: request.auth.uid, error: String(error) });
    await markPromptFailed();
    throw new HttpsError("unavailable", "WIA est momentanément indisponible.");
  }

  const raw = await upstream.text();
  let payload: WepiPayload | null = null;
  try {
    payload = JSON.parse(raw) as WepiPayload;
  } catch {
    // Never show an HTML/proxy error as an assistant answer in the mobile app.
  }
  if (!upstream.ok) {
    logger.warn("WIA Pilotis a refusé la requête", { userId: request.auth.uid, status: upstream.status });
    await markPromptFailed();
    if (upstream.status === 401 || upstream.status === 403) {
      throw new HttpsError("failed-precondition", "La liaison sécurisée WIA avec Pilotis doit être renouvelée.");
    }
    throw new HttpsError("unavailable", "WIA ne peut pas répondre pour le moment. Réessayez dans un instant.");
  }
  const answer = String(payload?.choices?.[0]?.message?.content || payload?.content || "").trim();
  if (!answer) {
    logger.error("Réponse WIA Pilotis invalide", { userId: request.auth.uid });
    await markPromptFailed();
    throw new HttpsError("internal", "La réponse WIA est invalide.");
  }
  const batch = db.batch();
  const resolvedModel = String(upstream.headers.get("x-wepi-model") || providerModel).trim().slice(0, 120) || providerModel;
  const providerRevision = String(upstream.headers.get("x-wepi-revision") || "").trim().slice(0, 120);
  batch.set(userMessage, {
    status: "complete",
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  batch.set(assistantMessage, {
    role: "assistant",
    content: answer.slice(0, 12_000),
    provider: "wia-pilotis",
    model: resolvedModel,
    ...(providerRevision ? { providerRevision } : {}),
    createdAt: FieldValue.serverTimestamp(),
  });
  batch.set(thread, {
    ownerId: request.auth.uid,
    title: prompt.slice(0, 80),
    lastMessage: answer.slice(0, 240),
    provider: "wia-pilotis",
    model: resolvedModel,
    ...(providerRevision ? { providerRevision } : {}),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await batch.commit();
  return {
    text: answer,
    provider: "wia-pilotis",
    model: resolvedModel,
    providerRevision,
    assistant: "WIA",
    threadId,
  };
});

async function sendInBatches(devices: PushDevice[], message: Omit<MulticastMessage, "tokens">) {
  const send = async (target: PushDevice[], apple: boolean) => {
    for (let index = 0; index < target.length; index += 500) {
      const batch = target.slice(index, index + 500);
    if (!batch.length) continue;
      const title = String(message.data?.title || "WAPI").slice(0, 120);
      const body = String(message.data?.body || "Nouvelle activité").slice(0, 240);
      const badge = Math.max(0, Math.min(99, Number(message.data?.badgeCount || 0) || 0));
      const silent = message.data?.silent === "true";
      const category = message.data?.type === "direct_call" || message.data?.type === "incoming_call"
        ? "WAPI_DIRECT_CALL"
        : undefined;
      const response = await getMessaging().sendEachForMulticast({
        ...message,
        ...(apple ? {
          ...(silent ? {} : { notification: { title, body } }),
          apns: {
            headers: {
              "apns-priority": silent ? "5" : "10",
              ...(message.data?.callId ? { "apns-collapse-id": `wapi-call-${message.data.callId}`.slice(0, 64) } : {}),
            },
            payload: {
              aps: silent
                ? { contentAvailable: true }
                : { alert: { title, body }, sound: "default", badge, category, contentAvailable: true },
            },
          },
        } : {}),
        tokens: batch.map((device) => device.token),
      });
    const stale = response.responses.flatMap((result, responseIndex) =>
      !result.success && staleRegistration(result.error?.code) ? [batch[responseIndex].remove()] : [],
    );
    if (stale.length) await Promise.allSettled(stale);
    if (response.failureCount) logger.warn("Certaines notifications WAPI n’ont pas été livrées", { failures: response.failureCount, staleTokens: stale.length });
    }
  };
  await send(devices.filter((device) => device.platform === "android"), false);
  await send(devices.filter((device) => device.platform === "ios"), true);
}

function unreadCount(value: unknown) {
  return typeof value === "number" && Number.isFinite(value)
    ? Math.max(0, Math.floor(value))
    : 0;
}

/**
 * The source of truth for the launcher badge is stored per user, not per
 * device. That keeps the counter coherent when the same WAPI account is used
 * on more than one phone and avoids querying every conversation on each push.
 */
async function incrementUnreadMessages(userId: string, conversationId: string) {
  const inbox = db.doc(`users/${userId}/notificationState/inbox`);
  const conversation = inbox.collection("conversations").doc(conversationId);
  return db.runTransaction(async (transaction) => {
    const [inboxSnapshot, conversationSnapshot] = await Promise.all([
      transaction.get(inbox),
      transaction.get(conversation),
    ]);
    const total = Math.min(9999, unreadCount(inboxSnapshot.get("unreadMessages")) + 1);
    const inConversation = Math.min(9999, unreadCount(conversationSnapshot.get("unreadMessages")) + 1);
    transaction.set(inbox, {
      unreadMessages: total,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.set(conversation, {
      unreadMessages: inConversation,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return total;
  });
}

export const notifyNewMessage = onDocumentCreated("conversations/{conversationId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;
  const conversation = await db.collection("conversations").doc(event.params.conversationId).get();
  if (!conversation.exists) return;
  const senderId = String(message.senderId || "");
  const recipients = (conversation.get("memberIds") as string[] | undefined)?.filter((id) => id && id !== senderId) ?? [];
  if (!recipients.length) return;
  const sender = await db.collection("users").doc(senderId).get();
  const senderName = String(sender.get("displayName") || conversation.get("contactName") || "Contact WAPI");
  const senderPhotoUrl = String(sender.get("photoUrl") || "").trim().slice(0, 2_000);
  const kind = String(message.kind || "text");
  const text = String(message.text || "").trim();
  const body = kind === "image" ? "📷 Photo" : kind === "audio" ? "🎙️ Note vocale" : kind === "video" ? "🎥 Vidéo" : text.slice(0, 240);
  const groupTitle = String(conversation.get("title") || "").trim();
  await Promise.all(recipients.map(async (recipientId) => {
    const [devices, unread] = await Promise.all([
      pushDevices([recipientId]),
      incrementUnreadMessages(recipientId, event.params.conversationId),
    ]);
    if (!devices.length) return;
    await sendInBatches(devices, {
      data: {
        type: "message",
        title: groupTitle || senderName,
        body: body || "Nouveau message",
        senderName,
        senderPhotoUrl,
        conversationId: event.params.conversationId,
        messageId: event.params.messageId,
        badgeCount: String(Math.min(unread, 99)),
      },
      // Data-only + high priority wakes the native background handler even
      // when the UI process is not running. Android then creates the visible
      // notification and updates its launcher badge summary.
      android: {
        priority: "high",
        ttl: 86_400_000,
        directBootOk: true,
      },
    });
  }));
});

export const notifyNewGroupMessage = onDocumentCreated("groups/{groupId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;
  const group = await db.collection("groups").doc(event.params.groupId).get();
  if (!group.exists) return;
  const senderId = String(message.senderId || "");
  const recipients = (group.get("memberIds") as string[] | undefined)?.filter((id) => id && id !== senderId) ?? [];
  if (!recipients.length) return;
  const sender = await db.collection("users").doc(senderId).get();
  const senderName = String(message.senderName || sender.get("displayName") || "Membre WAPI");
  const senderPhotoUrl = String(message.senderPhotoUrl || sender.get("photoUrl") || "").trim().slice(0, 2_000);
  const text = String(message.text || "").trim();
  await Promise.all(recipients.map(async (recipientId) => {
    const [devices, unread] = await Promise.all([
      pushDevices([recipientId]),
      incrementUnreadMessages(recipientId, event.params.groupId),
    ]);
    if (!devices.length) return;
    await sendInBatches(devices, {
      data: {
        type: "message",
        title: String(group.get("name") || "Groupe WAPI"),
        body: text.slice(0, 240) || "Nouveau message de groupe",
        senderName,
        senderPhotoUrl,
        conversationId: event.params.groupId,
        messageId: event.params.messageId,
        badgeCount: String(Math.min(unread, 99)),
      },
      android: { priority: "high", ttl: 86_400_000, directBootOk: true },
    });
  }));
});

export const notifyIncomingCall = onDocumentCreated("calls/{callId}", async (event) => {
  const call = event.data?.data();
  if (!call || call.status !== "ringing") return;
  const calleeId = String(call.calleeId || "");
  const devices = await pushDevices([calleeId]);
  if (!devices.length) return;
  const callerName = String(call.callerName || "Contact WAPI");
  const callerPhotoUrl = String(call.callerPhotoUrl || "").trim().slice(0, 2_000);
  const video = call.video === true;
  await sendInBatches(devices, {
    data: {
      type: "incoming_call",
      title: callerName,
      body: video ? "Appel vidéo entrant" : "Appel audio entrant",
      callerName,
      callerPhotoUrl,
      callId: event.params.callId,
      video: String(video),
      deepLink: `whappy://call/${event.params.callId}`,
    },
    android: { priority: "high", ttl: 120_000, collapseKey: `call-${event.params.callId}` },
  });
});

/**
 * A call notification is persistent by design, so creating it is only half of
 * the lifecycle.  When either participant declines or hangs up, this high
 * priority data event tells a sleeping Android/iOS client to remove the
 * ringing notification immediately instead of waiting for its timeout.
 */
export const notifyCallStateChanged = onDocumentUpdated("calls/{callId}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.status !== "ringing" || after.status === "ringing") return;
  const nextStatus = String(after.status || "ended");
  const pushState = callPushState(nextStatus);
  const calleeId = String(after.calleeId || before.calleeId || "");
  const devices = await pushDevices([calleeId]);
  if (!devices.length) return;
  await sendInBatches(devices, {
    data: {
      // Accepting only dismisses the ringing notification. It must not be
      // mistaken for a terminal event by the handset that just answered.
      type: pushState.type,
      title: "WAPI",
      body: pushState.body,
      callId: event.params.callId,
      status: nextStatus,
      silent: "true",
    },
    android: { priority: "high", ttl: 60_000, collapseKey: `call-${event.params.callId}` },
  });
});

let cachedTurnUrls: { value: string; expiresAt: number } | null = null;

async function publiclyReachableTurnUrls(urls: string) {
  if (!urls) return "";
  const endpoint = turnTcpEndpoint(urls);
  if (!endpoint) return "";
  const reachable = await new Promise<boolean>((resolve) => {
    const socket = connectTcp({ host: endpoint.host, port: endpoint.port });
    let completed = false;
    const finish = (result: boolean) => {
      if (completed) return;
      completed = true;
      socket.destroy();
      resolve(result);
    };
    socket.setTimeout(1_500);
    socket.once("connect", () => finish(true));
    socket.once("timeout", () => finish(false));
    socket.once("error", () => finish(false));
  });
  if (!reachable) {
    logger.warn("Relais TURN actif localement mais inaccessible depuis Internet", {
      host: endpoint.host,
      port: endpoint.port,
    });
  }
  return reachable ? urls : "";
}

async function activeWebRtcTurnUrls() {
  const nowMs = Date.now();
  if (cachedTurnUrls && cachedTurnUrls.expiresAt > nowMs) return cachedTurnUrls.value;
  const secretFallback = webRtcTurnUrls.value();
  try {
    const snapshot = await db.collection("systemConfig").doc("webrtcRelay").get();
    const value = snapshot.data();
    const timestamp = value?.updatedAt as { toMillis?: () => number } | undefined;
    const selected = await publiclyReachableTurnUrls(selectTurnUrls(
      secretFallback,
      snapshot.exists ? {
        active: value?.active === true,
        urls: value?.urls,
        updatedAtMs: typeof timestamp?.toMillis === "function" ? timestamp.toMillis() : Number.NaN,
      } : null,
      nowMs,
    ));
    cachedTurnUrls = { value: selected, expiresAt: nowMs + 30_000 };
    return selected;
  } catch (error) {
    logger.warn("Lecture du heartbeat TURN impossible; utilisation du secours Secret Manager", { error });
    return publiclyReachableTurnUrls(secretFallback);
  }
}

export const getWebRtcIceServers = onCall({ secrets: [webRtcTurnUrls, webRtcTurnSharedSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  return createTurnIcePayload(
    await activeWebRtcTurnUrls(),
    webRtcTurnSharedSecret.value(),
    request.auth.uid,
  );
});

type LiveRole = "host" | "viewer" | "speaker";
type LiveReaction = "heart" | "applause" | "fire" | "wow";

const liveReactions = new Set<LiveReaction>(["heart", "applause", "fire", "wow"]);
const liveGiftCatalog = {
  crown: { label: "Couronne", symbol: "👑", wearable: true },
  glasses: { label: "Lunettes", symbol: "😎", wearable: true },
  halo: { label: "Halo", symbol: "✨", wearable: true },
  trophy: { label: "Trophée", symbol: "🏆", wearable: false },
} as const;

function requiredLiveText(value: unknown, label: string, min: number, max: number) {
  const text = String(value || "").trim();
  if (text.length < min || text.length > max) {
    throw new HttpsError("invalid-argument", `${label} doit contenir entre ${min} et ${max} caractères.`);
  }
  return text;
}

function optionalLivekitConfig() {
  const serverUrl = livekitServerUrl.value().trim();
  const apiKey = livekitApiKey.value().trim();
  const apiSecret = livekitApiSecret.value().trim();
  if (!serverUrl.startsWith("wss://") || !apiKey || apiSecret.length < 16) return null;
  return { serverUrl, apiKey, apiSecret };
}

function livekitConfig() {
  const configuration = optionalLivekitConfig();
  if (configuration) return configuration;
  throw new HttpsError(
    "failed-precondition",
    "Le relais média WebRTC WAPI n’est pas encore provisionné. Configurez WAPI_LIVEKIT_URL, WAPI_LIVEKIT_API_KEY et WAPI_LIVEKIT_API_SECRET.",
  );
}

function livekitHttpUrl(serverUrl: string) {
  const url = new URL(serverUrl);
  url.protocol = url.protocol === "wss:" ? "https:" : "http:";
  return url.toString().replace(/\/$/, "");
}

function livekitRoomService() {
  const configuration = livekitConfig();
  return new RoomServiceClient(
    livekitHttpUrl(configuration.serverUrl),
    configuration.apiKey,
    configuration.apiSecret,
  );
}

async function liveAccessToken(options: {
  roomName: string;
  userId: string;
  displayName: string;
  role: LiveRole;
  product?: "wapi-live" | "wapi-group-call" | "wapi-direct-call";
}) {
  const configuration = livekitConfig();
  const canPublish = options.role !== "viewer";
  const product = options.product || "wapi-live";
  const token = new AccessToken(configuration.apiKey, configuration.apiSecret, {
    identity: options.userId,
    name: options.displayName,
    metadata: JSON.stringify({ role: options.role, product }),
    // Short tokens make revoked access effective quickly on a self-hosted SFU.
    ttl: "10m",
  });
  token.addGrant({
    roomJoin: true,
    room: options.roomName,
    canPublish,
    // Reactions pass through a rate-limited callable instead of giving every
    // spectator an unrestricted data channel to the whole room.
    canPublishData: canPublish,
    canSubscribe: true,
    canUpdateOwnMetadata: canPublish,
  });
  return {
    serverUrl: configuration.serverUrl,
    participantToken: await token.toJwt(),
  };
}

async function liveDisplayName(userId: string) {
  const profile = await db.collection("users").doc(userId).get();
  const displayName = String(profile.get("displayName") || profile.get("phoneNumber") || "Membre WAPI").trim();
  return {
    displayName: displayName || "Membre WAPI",
    photoUrl: String(profile.get("photoUrl") || "").trim(),
  };
}

async function canViewContactsLive(hostId: string, viewerId: string) {
  const [host, viewer] = await Promise.all([
    db.collection("users").doc(hostId).get(),
    db.collection("users").doc(viewerId).get(),
  ]);
  const hostContacts = (host.get("contacts") || {}) as Record<string, unknown>;
  const viewerContacts = (viewer.get("contacts") || {}) as Record<string, unknown>;
  if (hostContacts[viewerId] || viewerContacts[hostId]) return true;
  const sharedConversation = await db.collection("conversations")
    .where("memberIds", "array-contains", viewerId)
    .limit(100)
    .get();
  return sharedConversation.docs.some((document) => {
    const memberIds = document.get("memberIds") as string[] | undefined;
    return memberIds?.includes(hostId) === true;
  });
}

async function liveAudienceIds(hostId: string) {
  const profile = await db.collection("users").doc(hostId).get();
  const contacts = (profile.get("contacts") || {}) as Record<string, unknown>;
  const conversations = await db.collection("conversations")
    .where("memberIds", "array-contains", hostId)
    .limit(250)
    .get();
  const audience = new Set<string>(Object.keys(contacts));
  conversations.docs.forEach((document) => {
    const memberIds = document.get("memberIds") as string[] | undefined;
    memberIds?.forEach((memberId) => {
      if (memberId && memberId !== hostId) audience.add(memberId);
    });
  });
  return [...audience].slice(0, 500);
}

/** A deliberate opt-in for alerts when a particular WAPI account starts a live. */
export const setLiveSubscription = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const targetUserId = String(request.data?.targetUserId || "").trim();
  const subscribed = request.data?.subscribed === true;
  if (!targetUserId || targetUserId === request.auth.uid) {
    throw new HttpsError("invalid-argument", "Choisissez un autre compte WAPI.");
  }
  const target = await db.collection("users").doc(targetUserId).get();
  if (!target.exists) throw new HttpsError("not-found", "Ce compte WAPI est introuvable.");
  const subscriber = await liveDisplayName(request.auth.uid);
  const reference = db.doc(`users/${targetUserId}/liveFollowers/${request.auth.uid}`);
  if (subscribed) {
    await reference.set({
      userId: request.auth.uid,
      displayName: subscriber.displayName,
      photoUrl: subscriber.photoUrl,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  } else {
    await reference.delete();
  }
  return { subscribed };
});

async function liveDocument(liveId: string) {
  const snapshot = await db.collection("liveSessions").doc(liveId).get();
  const value = snapshot.data();
  if (!snapshot.exists || !value) {
    throw new HttpsError("not-found", "Ce direct n’existe plus.");
  }
  return { reference: snapshot.ref, value };
}

async function setViewerPresence(liveId: string, userId: string, active: boolean) {
  const live = db.collection("liveSessions").doc(liveId);
  const viewer = live.collection("viewers").doc(userId);
  const identity = active ? await liveDisplayName(userId) : null;
  await db.runTransaction(async (transaction) => {
    const [liveSnapshot, viewerSnapshot] = await Promise.all([
      transaction.get(live),
      transaction.get(viewer),
    ]);
    const liveValue = liveSnapshot.data();
    if (!liveSnapshot.exists || !liveValue || liveValue.hostId === userId) return;
    const viewerValue = viewerSnapshot.data();
    const wasActive = viewerValue?.active === true;
    const hasJoined = viewerValue?.hasJoined === true;
    if (wasActive === active && (hasJoined || !active)) return;
    const currentCount = Math.max(0, Number(liveValue.viewerCount || 0));
    transaction.set(viewer, {
      active,
      hasJoined: hasJoined || active,
      ...(identity ? { displayName: identity.displayName, photoUrl: identity.photoUrl } : {}),
      joinedAt: viewerValue?.joinedAt || (active ? FieldValue.serverTimestamp() : null),
      lastSeenAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.update(live, {
      viewerCount: Math.max(0, currentCount + (active ? 1 : -1)),
      uniqueViewerCount: Math.max(0, Number(liveValue.uniqueViewerCount || 0)) +
        (active && !hasJoined ? 1 : 0),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
}

async function assertLiveAccess(liveId: string, userId: string) {
  const { reference, value } = await liveDocument(liveId);
  if (value.hostId === userId) {
    if (value.status === "ended") throw new HttpsError("failed-precondition", "Ce direct est terminé et ne peut pas être rouvert.");
    return { reference, value, isHost: true };
  }
  if (value.status !== "live") {
    throw new HttpsError("failed-precondition", "Ce direct n’est pas en cours.");
  }
  if (value.visibility === "private") {
    throw new HttpsError("permission-denied", "Ce direct est privé.");
  }
  if (value.visibility === "contacts" &&
      !((value.audienceIds as string[] | undefined)?.includes(userId)) &&
      !(await canViewContactsLive(String(value.hostId || ""), userId))) {
    throw new HttpsError("permission-denied", "Ce direct est réservé aux contacts de l’animateur.");
  }
  const block = await reference.collection("blockedViewers").doc(userId).get();
  if (block.exists) {
    const expiresAt = block.get("expiresAt") as { toMillis?: () => number } | undefined;
    const stillBlocked = !expiresAt?.toMillis || expiresAt.toMillis() > Date.now();
    if (stillBlocked) {
      throw new HttpsError("permission-denied", "L’accès à ce direct vous a été retiré.");
    }
    await block.ref.delete();
  }
  return { reference, value, isHost: false };
}

export const createLiveSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const hostId = request.auth.uid;
  const requestedTitle = String(request.data?.title || "").trim();
  if (requestedTitle.length > 120) {
    throw new HttpsError("invalid-argument", "Le titre automatique du direct est invalide.");
  }
  const category = requiredLiveText(request.data?.category || "Discussion", "La catégorie", 2, 60);
  const visibility = String(request.data?.visibility || "public");
  const hostMode = String(request.data?.hostMode || "personal");
  const audioOnly = request.data?.audioOnly === true;
  const startNow = request.data?.startNow !== false;
  if (!["public", "contacts", "private"].includes(visibility)) {
    throw new HttpsError("invalid-argument", "Visibilité du direct invalide.");
  }
  if (!["personal", "creator", "business"].includes(hostMode)) {
    throw new HttpsError("invalid-argument", "Mode d’animateur invalide.");
  }

  const current = await db.doc(`users/${hostId}/liveState/current`).get();
  if (["scheduled", "live"].includes(String(current.get("status") || ""))) {
    const previousId = String(current.get("liveId") || "");
    const previous = previousId ? await db.collection("liveSessions").doc(previousId).get() : null;
    if (previous?.exists && previous.get("status") !== "ended" && previous.get("streamProvider") === "wapi-webrtc-p2p") {
      throw new HttpsError("already-exists", "Un direct WAPI est déjà ouvert sur ce compte.");
    }
    if (previous?.exists && previous.get("hostId") === hostId) {
      await previous.ref.set({ status: "ended", endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
  }

  const pendingKingQiRoomId = String(current.get("kingQiRoomId") || "");
  let kingQiRoomId = "";
  if (pendingKingQiRoomId) {
    const kingQiRoom = await db.collection("kingQiRooms").doc(pendingKingQiRoomId).get();
    if (kingQiRoom.exists && kingQiRoom.get("hostId") === hostId && ["waiting", "playing"].includes(String(kingQiRoom.get("status") || ""))) {
      kingQiRoomId = pendingKingQiRoomId;
    }
  }

  const [host, audienceIds] = await Promise.all([
    liveDisplayName(hostId),
    visibility === "contacts" ? liveAudienceIds(hostId) : Promise.resolve([]),
  ]);
  // WAPI does not ask creators to name a live. The server keeps a concise
  // internal label for notifications, accessibility and moderation records.
  const title = requestedTitle || (audioOnly ? `Radio de ${host.displayName}` : `En direct avec ${host.displayName}`);
  const live = db.collection("liveSessions").doc();
  const signalingRoomId = `wapi-${hostId.slice(0, 12)}-${live.id}`;
  const batch = db.batch();
    batch.set(live, {
      hostId,
      hostName: host.displayName,
      hostPhotoUrl: host.photoUrl,
      title,
      category,
      productTitle: "",
      hostMode,
      visibility,
      audienceIds,
      // Le statut passe à « live » après l'ouverture effective de la caméra
      // locale, dans setLiveSessionState. Cela évite un direct noir.
      status: "scheduled",
      viewerCount: 0,
      uniqueViewerCount: 0,
      reactionCount: 0,
      reactionTotals: { heart: 0, applause: 0, fire: 0, wow: 0 },
      allowComments: true,
      allowReactions: true,
      allowGiftWearables: false,
      giftCount: 0,
      aiGenerated: false,
      moderationStatus: "clear",
      streamProvider: "wapi-webrtc-p2p",
      streamRoomId: signalingRoomId,
      signalingRoomId,
      mediaStatus: "provisioned",
      maxPeerViewers: 8,
      kingQiRoomId,
      audioOnly,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    batch.set(db.doc(`users/${hostId}/liveState/current`), {
      liveId: live.id,
      status: "scheduled",
      kingQiRoomId,
      updatedAt: FieldValue.serverTimestamp(),
    });
  await batch.commit();
  return {
    liveId: live.id,
    role: "host",
    streamProvider: "wapi-webrtc-p2p",
    mediaStatus: "provisioned",
    startNow,
    signalingRoomId,
  };
});

export const listVisibleLiveSessions = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const snapshot = await db.collection("liveSessions")
    .where("status", "in", ["scheduled", "live"])
    .limit(100)
    .get();
  const lives = snapshot.docs.flatMap((document) => {
    const value = document.data();
    const visible = value.hostId === userId || value.visibility === "public" ||
      (value.visibility === "contacts" &&
        (value.audienceIds as string[] | undefined)?.includes(userId));
    if (!visible || value.moderationStatus === "blocked") return [];
    // Les spectateurs ne voient jamais un brouillon ou une salle préparée :
    // une carte apparaît uniquement après le passage réel à « live ».
    if (value.hostId !== userId && value.status !== "live") return [];
    return [{
      id: document.id,
      hostId: value.hostId,
      hostName: value.hostName,
      hostPhotoUrl: value.hostPhotoUrl,
      title: value.title,
      category: value.category,
      productTitle: value.productTitle,
      hostMode: value.hostMode,
      visibility: value.visibility,
      status: value.status,
      streamProvider: value.streamProvider,
      streamRoomId: value.streamRoomId,
      viewerCount: Number(value.viewerCount || 0),
      reactionCount: Number(value.reactionCount || 0),
      aiGenerated: value.aiGenerated === true,
      audioOnly: value.audioOnly === true,
      allowGiftWearables: value.allowGiftWearables === true,
      giftCount: Number(value.giftCount || 0),
      startedAtMillis: value.startedAt?.toMillis?.() || 0,
      createdAtMillis: value.createdAt?.toMillis?.() || 0,
    }];
  }).sort((left, right) => {
    if (left.status !== right.status) return left.status === "live" ? -1 : 1;
    return right.createdAtMillis - left.createdAtMillis;
  });
  return { lives };
});

export const joinLiveSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  if (!liveId) throw new HttpsError("invalid-argument", "Direct WAPI invalide.");
  const accessControl = await assertLiveAccess(liveId, request.auth.uid);
  const value = accessControl.value;
  const isHost = accessControl.isHost;
  const signalingRoomId = String(value.signalingRoomId || value.streamRoomId || "").trim();
  if (value.streamProvider !== "wapi-webrtc-p2p" || !signalingRoomId) {
    throw new HttpsError("failed-precondition", "Le flux de ce direct n’est pas provisionné.");
  }
  const profile = await liveDisplayName(request.auth.uid);
  if (!isHost) {
    const viewer = accessControl.reference.collection("viewers").doc(request.auth.uid);
    const existingViewer = await viewer.get();
    await viewer.set({
      userId: request.auth.uid,
      displayName: profile.displayName,
      photoUrl: profile.photoUrl,
      authorizedAt: FieldValue.serverTimestamp(),
      lastSeenAt: FieldValue.serverTimestamp(),
      ...(existingViewer.exists ? {} : { active: false, hasJoined: false }),
    }, { merge: true });
  }
  const role: LiveRole = isHost ? "host" : "viewer";
  if (!isHost) {
    const activePeers = await accessControl.reference.collection("peers").get();
    const activeCount = activePeers.docs.filter((document) => !["blocked", "disconnected"].includes(String(document.get("status") || ""))).length;
    if (activeCount >= Math.max(1, Number(value.maxPeerViewers || 8)) && !activePeers.docs.some((document) => document.id === request.auth!.uid)) {
      throw new HttpsError("resource-exhausted", "Ce direct a atteint sa capacité WebRTC actuelle.");
    }
    const peer = accessControl.reference.collection("peers").doc(request.auth.uid);
    await peer.set({
      viewerId: request.auth.uid,
      viewerName: profile.displayName,
      viewerPhotoUrl: profile.photoUrl,
      hostId: String(value.hostId || ""),
      status: "waiting",
      offer: null,
      answer: null,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }
  return { liveId, role, streamProvider: "wapi-webrtc-p2p", signalingRoomId };
});

export const setLivePresence = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const action = String(request.data?.action || "").trim();
  if (!liveId || !["connected", "disconnected"].includes(action)) {
    throw new HttpsError("invalid-argument", "Présence Live invalide.");
  }
  if (action === "connected") {
    await assertLiveAccess(liveId, request.auth.uid);
  } else {
    await liveDocument(liveId);
  }
  await setViewerPresence(liveId, request.auth.uid, action === "connected");
  await db.collection("liveSessions").doc(liveId).collection("peers").doc(request.auth.uid).set({
    status: action === "connected" ? "waiting" : "disconnected",
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { ok: true };
});

export const sendLiveReaction = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const reaction = String(request.data?.reaction || "") as LiveReaction;
  if (!liveId || !liveReactions.has(reaction)) {
    throw new HttpsError("invalid-argument", "Réaction Live invalide.");
  }
  const access = await assertLiveAccess(liveId, request.auth.uid);
  if (access.value.allowReactions === false) {
    throw new HttpsError("failed-precondition", "Les réactions sont désactivées.");
  }
  const participant = access.reference.collection("viewers").doc(request.auth.uid);
  let total = 0;
  await db.runTransaction(async (transaction) => {
    const [liveSnapshot, participantSnapshot] = await Promise.all([
      transaction.get(access.reference),
      transaction.get(participant),
    ]);
    const participantValue = participantSnapshot.data();
    const previous = participantValue?.lastReactionAt as { toMillis?: () => number } | undefined;
    if (!access.isHost && !participantSnapshot.exists) {
      throw new HttpsError("permission-denied", "Rejoignez le direct avant de réagir.");
    }
    if (previous?.toMillis && Date.now() - previous.toMillis() < 800) {
      throw new HttpsError("resource-exhausted", "Réaction envoyée trop rapidement.");
    }
    const liveValue = liveSnapshot.data() || {};
    const totals = { heart: 0, applause: 0, fire: 0, wow: 0,
      ...((liveValue.reactionTotals || {}) as Record<string, number>) };
    totals[reaction] = Number(totals[reaction] || 0) + 1;
    total = Number(liveValue.reactionCount || 0) + 1;
    transaction.update(access.reference, {
      reactionCount: total,
      reactionTotals: totals,
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (!access.isHost) {
      transaction.set(participant, {
        lastReactionAt: FieldValue.serverTimestamp(),
        lastSeenAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    }
  });
  return { ok: true, total };
});

/** Sends a real persisted digital gift. Wearable effects require host opt-in. */
export const sendLiveGift = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const giftId = String(request.data?.giftId || "").trim() as keyof typeof liveGiftCatalog;
  const gift = liveGiftCatalog[giftId];
  if (!liveId || !gift) throw new HttpsError("invalid-argument", "Cadeau Live invalide.");
  const access = await assertLiveAccess(liveId, request.auth.uid);
  if (access.isHost) throw new HttpsError("invalid-argument", "L’animateur ne peut pas s’envoyer un cadeau.");
  const viewer = access.reference.collection("viewers").doc(request.auth.uid);
  const sender = await liveDisplayName(request.auth.uid);
  const event = access.reference.collection("gifts").doc();
  let total = 0;
  let equipped = false;
  await db.runTransaction(async (transaction) => {
    const [liveSnapshot, viewerSnapshot] = await Promise.all([transaction.get(access.reference), transaction.get(viewer)]);
    if (!viewerSnapshot.exists) throw new HttpsError("failed-precondition", "Rejoignez le direct avant d’envoyer un cadeau.");
    const previous = viewerSnapshot.get("lastGiftAt") as { toMillis?: () => number } | undefined;
    if (previous?.toMillis && Date.now() - previous.toMillis() < 3_000) throw new HttpsError("resource-exhausted", "Attendez quelques secondes avant un autre cadeau.");
    equipped = gift.wearable && liveSnapshot.get("allowGiftWearables") === true;
    total = Math.max(0, Number(liveSnapshot.get("giftCount") || 0)) + 1;
    transaction.set(event, {
      giftId,
      label: gift.label,
      symbol: gift.symbol,
      wearable: gift.wearable,
      equipped,
      senderId: request.auth!.uid,
      senderName: sender.displayName,
      recipientId: String(liveSnapshot.get("hostId") || ""),
      createdAt: FieldValue.serverTimestamp(),
    });
    transaction.set(viewer, { lastGiftAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.update(access.reference, { giftCount: total, updatedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true, equipped, total };
});

export const setLiveGiftWearables = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const enabled = request.data?.enabled === true;
  const live = db.collection("liveSessions").doc(liveId);
  const snapshot = await live.get();
  if (!snapshot.exists || snapshot.get("hostId") !== request.auth.uid || snapshot.get("status") === "ended") {
    throw new HttpsError("permission-denied", "Seul l’animateur peut régler les cadeaux portables.");
  }
  await live.update({ allowGiftWearables: enabled, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true, enabled };
});

export const moderateLiveParticipant = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const targetUserId = String(request.data?.targetUserId || "").trim();
  const action = String(request.data?.action || "").trim();
  if (!liveId || !targetUserId || !["remove", "block"].includes(action)) {
    throw new HttpsError("invalid-argument", "Action de modération invalide.");
  }
  const { reference, value } = await liveDocument(liveId);
  if (value.hostId !== request.auth.uid || targetUserId === request.auth.uid) {
    throw new HttpsError("permission-denied", "Seul l’animateur peut modérer ce direct.");
  }
  await reference.collection("blockedViewers").doc(targetUserId).set({
    userId: targetUserId,
    blockedBy: request.auth.uid,
    reason: action,
    blockedAt: FieldValue.serverTimestamp(),
    expiresAt: action === "remove" ? new Date(Date.now() + 10 * 60_000) : null,
  });
  await setViewerPresence(liveId, targetUserId, false);
  await reference.collection("peers").doc(targetUserId).set({
    status: "blocked",
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { ok: true };
});

export const reportLiveSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  const reason = requiredLiveText(request.data?.reason, "Le motif", 3, 80);
  const details = String(request.data?.details || "").trim().slice(0, 600);
  const { value } = await liveDocument(liveId);
  if (value.hostId === request.auth.uid) {
    throw new HttpsError("invalid-argument", "Vous ne pouvez pas signaler votre propre direct.");
  }
  await db.collection("liveReports").doc(`${liveId}_${request.auth.uid}`).set({
    liveId,
    hostId: value.hostId,
    reporterId: request.auth.uid,
    reason,
    details,
    status: "open",
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: false });
  return { ok: true };
});

export const setLiveSessionState = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const hostId = request.auth.uid;
  const liveId = String(request.data?.liveId || "").trim();
  const action = String(request.data?.action || "").trim();
  if (!liveId || !["start", "end"].includes(action)) {
    throw new HttpsError("invalid-argument", "État du direct invalide.");
  }
  const live = db.collection("liveSessions").doc(liveId);
  let startedNow = false;
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(live);
    const value = snapshot.data();
    if (!snapshot.exists || !value || value.hostId !== hostId) {
      throw new HttpsError("permission-denied", "Seul l’animateur peut contrôler ce direct.");
    }
    if (action === "start") {
      if (value.status === "ended") throw new HttpsError("failed-precondition", "Ce direct est terminé.");
      startedNow = value.status !== "live";
      transaction.update(live, {
        status: "live",
        startedAt: value.startedAt || FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      transaction.update(live, {
        status: "ended",
        endedAt: FieldValue.serverTimestamp(),
        viewerCount: 0,
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.set(db.doc(`users/${hostId}/liveState/current`), {
        liveId,
        status: "ended",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  });
  if (action === "start" && startedNow) {
    const [started, followers] = await Promise.all([
      live.get(),
      db.collection("users").doc(hostId).collection("liveFollowers").limit(500).get(),
    ]);
    const followerIds = followers.docs.map((document) => document.id).filter((id) => id && id !== hostId);
    const visibility = String(started.get("visibility") || "public");
    const audienceIds = Array.isArray(started.get("audienceIds")) ? (started.get("audienceIds") as unknown[]).map(String) : [];
    // A subscription does not bypass the host's audience choice: private
    // rooms stay silent, and contacts-only rooms alert contacts only.
    const recipients = visibility === "public"
      ? followerIds
      : visibility === "contacts"
        ? followerIds.filter((id) => audienceIds.includes(id))
        : [];
    const devices = await pushDevices(recipients);
    if (devices.length) {
      const title = String(started.get("title") || "Direct WAPI").trim();
      const audioOnly = started.get("audioOnly") === true;
      await sendInBatches(devices, {
        data: {
          type: "live",
          title: `${String(started.get("hostName") || "Un contact WAPI")} est en direct`,
          body: audioOnly ? `Radio en direct · ${title}` : `Live en direct · ${title}`,
          liveId,
          deepLink: `whappy://live/${liveId}`,
        },
        android: { priority: "high", ttl: 3_600_000, collapseKey: `live-${liveId}` },
      });
    }
  }
  return { ok: true, status: action === "start" ? "live" : "ended" };
});

async function groupCallDocument(callId: string, userId: string) {
  const snapshot = await db.collection("groupCallSessions").doc(callId).get();
  const value = snapshot.data();
  if (!snapshot.exists || !value) throw new HttpsError("not-found", "Cet appel de groupe n’existe plus.");
  const members = Array.isArray(value.memberIds) ? value.memberIds.map(String) : [];
  if (!members.includes(userId)) throw new HttpsError("permission-denied", "Vous n’êtes pas membre de cet appel.");
  const expiresAt = value.expiresAt;
  if (value.status === "ended" || !expiresAt?.toMillis || expiresAt.toMillis() <= Date.now()) {
    throw new HttpsError("failed-precondition", "Cet appel de groupe est terminé.");
  }
  return { reference: snapshot.ref, value, members };
}

async function setGroupCallParticipantPresence(callId: string, userId: string, active: boolean) {
  const reference = db.collection("groupCallSessions").doc(callId);
  const participant = reference.collection("participants").doc(userId);
  await db.runTransaction(async (transaction) => {
    const [callSnapshot, participantSnapshot] = await Promise.all([transaction.get(reference), transaction.get(participant)]);
    if (!callSnapshot.exists || callSnapshot.get("status") === "ended") return;
    const value = participantSnapshot.data();
    const wasActive = value?.active === true;
    if (wasActive === active) return;
    const current = Math.max(0, Number(callSnapshot.get("participantCount") || 0));
    const maximum = Math.max(2, Math.min(6, Number(callSnapshot.get("maxParticipants") || 6)));
    if (active && current >= maximum) throw new HttpsError("resource-exhausted", "Cet appel P2P a atteint sa limite de participants.");
    transaction.set(participant, { userId, active, lastSeenAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.update(reference, { participantCount: Math.max(0, current + (active ? 1 : -1)), updatedAt: FieldValue.serverTimestamp() });
  });
}

/** Creates a small-group native WebRTC mesh; Firestore carries signaling only. */
export const createGroupCallSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const groupId = String(request.data?.groupId || "").trim();
  const source = request.data?.source === "conversations" ? "conversations" : "groups";
  const video = request.data?.video === true;
  if (!groupId) throw new HttpsError("invalid-argument", "Groupe invalide.");
  const group = await db.collection(source).doc(groupId).get();
  const groupValue = group.data();
  if (!group.exists || !groupValue) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  const memberIds = Array.isArray(groupValue.memberIds) ? groupValue.memberIds.map(String) : [];
  if (!memberIds.includes(request.auth.uid)) throw new HttpsError("permission-denied", "Vous n’êtes pas membre de ce groupe.");
  if (memberIds.length < 2) throw new HttpsError("failed-precondition", "Ajoutez au moins un autre membre au groupe.");
  const groupName = String(source === "groups" ? groupValue.name || "Groupe WAPI" : groupValue.title || "Groupe WAPI").trim().slice(0, 80);
  const creator = await liveDisplayName(request.auth.uid);
  const call = db.collection("groupCallSessions").doc();
  const expiresAt = new Date(Date.now() + 6 * 60 * 60_000);
  const batch = db.batch();
  batch.set(call, {
      groupId,
      groupSource: source,
      groupName,
      groupPhotoUrl: String(source === "groups" ? groupValue.photoUrl || "" : groupValue.groupPhotoUrl || ""),
      creatorId: request.auth.uid,
      creatorName: creator.displayName,
      adminIds: [...new Set([String(groupValue.ownerId || ""), ...((groupValue.adminIds as string[] | undefined) || [])])].filter(Boolean),
      memberIds,
      video,
      status: "active",
      participantCount: 1,
      maxParticipants: 6,
      streamProvider: "wapi-webrtc-mesh",
      streamRoomId: call.id,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt,
      updatedAt: FieldValue.serverTimestamp(),
  });
  batch.set(call.collection("participants").doc(request.auth.uid), {
    userId: request.auth.uid,
    displayName: creator.displayName,
    photoUrl: creator.photoUrl,
    active: true,
    role: "host",
    joinedAt: FieldValue.serverTimestamp(),
    lastSeenAt: FieldValue.serverTimestamp(),
  });
  await batch.commit();
  const recipients = memberIds.filter((id: string) => id !== request.auth!.uid);
  const devices = await pushDevices(recipients);
  if (devices.length) {
    await sendInBatches(devices, {
      data: {
        type: "group_call",
        title: groupName,
        body: `${creator.displayName} a lancé un appel ${video ? "vidéo" : "audio"} de groupe`,
        callerName: creator.displayName,
        callId: call.id,
        groupId,
        video: String(video),
        deepLink: `whappy://group-call/${call.id}`,
      },
      android: { priority: "high", ttl: 180_000, collapseKey: `group-call-${call.id}` },
    });
  }
  return { callId: call.id, groupId, groupName, video, role: "host", streamProvider: "wapi-webrtc-mesh", maxParticipants: 6 };
});

export const getGroupCallSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  if (!callId) throw new HttpsError("invalid-argument", "Appel invalide.");
  const { value } = await groupCallDocument(callId, request.auth.uid);
  return {
    callId,
    groupId: String(value.groupId || ""),
    groupName: String(value.groupName || "Groupe WAPI"),
    video: value.video === true,
    participantCount: Number(value.participantCount || 0),
  };
});

export const joinGroupCallSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  if (!callId) throw new HttpsError("invalid-argument", "Appel invalide.");
  const { reference, value } = await groupCallDocument(callId, request.auth.uid);
  const profile = await liveDisplayName(request.auth.uid);
  if (value.streamProvider !== "wapi-webrtc-mesh") throw new HttpsError("failed-precondition", "Le transport WebRTC de cet appel est indisponible.");
  if (Number(value.participantCount || 0) >= 6) throw new HttpsError("resource-exhausted", "Cet appel P2P a atteint sa limite de six participants.");
  await reference.collection("participants").doc(request.auth.uid).set({
    userId: request.auth.uid,
    displayName: profile.displayName,
    photoUrl: profile.photoUrl,
    active: false,
    role: "participant",
    invitedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { callId, groupId: String(value.groupId || ""), groupName: String(value.groupName || "Groupe WAPI"), video: value.video === true, role: "participant", streamProvider: "wapi-webrtc-mesh", maxParticipants: 6 };
});

export const setGroupCallPresence = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  const active = request.data?.active === true;
  await groupCallDocument(callId, request.auth.uid);
  await setGroupCallParticipantPresence(callId, request.auth.uid, active);
  return { ok: true };
});

export const endGroupCallSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  const { reference, value } = await groupCallDocument(callId, request.auth.uid);
  const adminIds = Array.isArray(value.adminIds) ? value.adminIds.map(String) : [];
  if (value.creatorId !== request.auth.uid && !adminIds.includes(request.auth.uid)) {
    throw new HttpsError("permission-denied", "Seuls le créateur et les administrateurs peuvent terminer cet appel.");
  }
  await reference.update({ status: "ended", participantCount: 0, endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  return { ok: true };
});

// MARK: - Appels directs WAPI
//
// Les appels privés ne passent jamais par tel://, FaceTime ni par un secret
// embarqué dans une application. Firebase ne garde que l'invitation et l'état;
// le média WebRTC transite par le SFU WAPI auto-hébergé avec des jetons courts.
async function directCallDocument(callId: string, userId: string) {
  const reference = db.collection("directCallSessions").doc(callId);
  const snapshot = await reference.get();
  const value = snapshot.data();
  if (!snapshot.exists || !value) throw new HttpsError("not-found", "Cet appel WAPI n’existe plus.");
  const callerId = String(value.callerId || "");
  const calleeId = String(value.calleeId || "");
  if (userId !== callerId && userId !== calleeId) {
    throw new HttpsError("permission-denied", "Vous n’êtes pas invité à cet appel.");
  }
  return { reference, value, callerId, calleeId };
}

async function finishDirectCallSession(callId: string, userId: string, status: "declined" | "ended") {
  const { reference, value, callerId, calleeId } = await directCallDocument(callId, userId);
  const currentStatus = String(value.status || "");
  if (!["ringing", "accepted"].includes(currentStatus)) return { ok: true, status: currentStatus };
  if (status === "declined" && userId !== calleeId) {
    throw new HttpsError("permission-denied", "Seul le destinataire peut refuser cet appel.");
  }
  await reference.update({ status, endedBy: userId, endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  const roomName = String(value.streamRoomId || "");
  if (roomName) await livekitRoomService().deleteRoom(roomName).catch((error) => logger.warn("Salle d’appel direct déjà fermée", { callId, error }));
  // Le document reste disponible brièvement pour afficher l'historique des deux
  // interlocuteurs, sans exposer un journal d'appel public.
  return { ok: true, status, peerId: userId === callerId ? calleeId : callerId };
}

/** Crée une salle WebRTC privée à deux participants sur le SFU WAPI. */
export const createDirectCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const calleeId = String(request.data?.calleeId || "").trim();
  const video = request.data?.video === true;
  if (!calleeId || calleeId === request.auth.uid) throw new HttpsError("invalid-argument", "Destinataire WAPI invalide.");
  const configuration = livekitConfig();
  const [caller, calleeProfile] = await Promise.all([liveDisplayName(request.auth.uid), db.collection("users").doc(calleeId).get()]);
  if (!calleeProfile.exists) throw new HttpsError("not-found", "Ce contact n’est plus disponible sur WAPI.");
  const callee = await liveDisplayName(calleeId);

  const call = db.collection("directCallSessions").doc();
  const roomName = `wapi-direct-${call.id}`;
  const roomService = new RoomServiceClient(livekitHttpUrl(configuration.serverUrl), configuration.apiKey, configuration.apiSecret);
  await roomService.createRoom({
    name: roomName,
    emptyTimeout: 90,
    departureTimeout: 20,
    maxParticipants: 2,
    metadata: JSON.stringify({ callId: call.id, product: "wapi-direct-call" }),
  });
  const access = await liveAccessToken({
    roomName,
    userId: request.auth.uid,
    displayName: caller.displayName,
    role: "speaker",
    product: "wapi-direct-call",
  });
  try {
    await call.set({
      callerId: request.auth.uid,
      callerName: caller.displayName,
      callerPhotoUrl: caller.photoUrl,
      calleeId,
      calleeName: callee.displayName,
      calleePhotoUrl: callee.photoUrl,
      memberIds: [request.auth.uid, calleeId],
      video,
      status: "ringing",
      streamProvider: "livekit-self-hosted",
      streamRoomId: roomName,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt: new Date(Date.now() + 2 * 60_000),
      updatedAt: FieldValue.serverTimestamp(),
    });
  } catch (error) {
    await roomService.deleteRoom(roomName).catch(() => undefined);
    throw error;
  }
  const devices = await pushDevices([calleeId]);
  if (devices.length) {
    await sendInBatches(devices, {
      data: {
        type: "direct_call",
        title: caller.displayName,
        body: video ? "Appel vidéo WAPI entrant" : "Appel audio WAPI entrant",
        callerName: caller.displayName,
        callerPhotoUrl: caller.photoUrl,
        callId: call.id,
        video: String(video),
        deepLink: `whappy://call/${call.id}`,
      },
      android: { priority: "high", ttl: 120_000, collapseKey: `direct-call-${call.id}` },
    });
  }
  return { callId: call.id, calleeId, calleeName: callee.displayName, calleePhotoUrl: callee.photoUrl, video, ...access };
});

/** Reads an incoming invitation without joining its media room. */
export const getDirectCallSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  if (!callId) throw new HttpsError("invalid-argument", "Appel WAPI invalide.");
  const { value, callerId, calleeId } = await directCallDocument(callId, request.auth.uid);
  const status = String(value.status || "");
  if (status !== "ringing") throw new HttpsError("failed-precondition", "Cet appel n’est plus disponible.");
  const incoming = request.auth.uid === calleeId;
  return {
    callId,
    incoming,
    peerId: incoming ? callerId : calleeId,
    peerName: incoming ? String(value.callerName || "Contact WAPI") : String(value.calleeName || "Contact WAPI"),
    peerPhotoUrl: incoming ? String(value.callerPhotoUrl || "") : String(value.calleePhotoUrl || ""),
    video: value.video === true,
    status,
  };
});

/** Le destinataire reçoit un jeton personnel seulement après avoir accepté. */
export const joinDirectCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  if (!callId) throw new HttpsError("invalid-argument", "Appel WAPI invalide.");
  const { reference, value, callerId, calleeId } = await directCallDocument(callId, request.auth.uid);
  const status = String(value.status || "");
  const roomName = String(value.streamRoomId || "");
  if (value.streamProvider !== "livekit-self-hosted" || !roomName) {
    throw new HttpsError("failed-precondition", "Le relais WebRTC de cet appel n’est pas disponible.");
  }
  if (!["ringing", "accepted"].includes(status)) throw new HttpsError("failed-precondition", "Cet appel est terminé.");
  if (request.auth.uid === calleeId && status === "ringing") {
    await reference.update({ status: "accepted", acceptedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  }
  const profile = await liveDisplayName(request.auth.uid);
  const access = await liveAccessToken({ roomName, userId: request.auth.uid, displayName: profile.displayName, role: "speaker", product: "wapi-direct-call" });
  return {
    callId,
    peerId: request.auth.uid === callerId ? calleeId : callerId,
    peerName: request.auth.uid === callerId ? String(value.calleeName || "Contact WAPI") : String(value.callerName || "Contact WAPI"),
    peerPhotoUrl: request.auth.uid === callerId ? String(value.calleePhotoUrl || "") : String(value.callerPhotoUrl || ""),
    video: value.video === true,
    status: request.auth.uid === calleeId && status === "ringing" ? "accepted" : status,
    ...access,
  };
});

export const closeDirectCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  const action = String(request.data?.action || "end").trim();
  if (!callId || !["decline", "end"].includes(action)) throw new HttpsError("invalid-argument", "Action d’appel invalide.");
  return finishDirectCallSession(callId, request.auth.uid, action === "decline" ? "declined" : "ended");
});

export const livekitWebhook = onRequest({ secrets: [livekitApiSecret] }, async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).send("Method Not Allowed");
    return;
  }
  try {
    const configuration = livekitConfig();
    const receiver = new WebhookReceiver(configuration.apiKey, configuration.apiSecret);
    const event = await receiver.receive(
      request.rawBody.toString("utf8"),
      request.get("Authorization"),
    );
    const roomName = event.room?.name;
    if (!roomName) {
      response.status(204).send();
      return;
    }
    const sessions = await db.collection("liveSessions")
      .where("streamRoomId", "==", roomName)
      .limit(1)
      .get();
    if (sessions.empty) {
      const groupCalls = await db.collection("groupCallSessions").where("streamRoomId", "==", roomName).limit(1).get();
      if (groupCalls.empty) {
        const directCalls = await db.collection("directCallSessions").where("streamRoomId", "==", roomName).limit(1).get();
        if (directCalls.empty) {
          response.status(204).send();
          return;
        }
        // Une coupure réseau ou une fermeture du SFU ne doit jamais laisser
        // une invitation privée bloquée en état « ringing » ou « accepted ».
        // Le webhook signé est l'autorité serveur pour clôturer la salle.
        if (event.event === "room_finished") {
          const call = directCalls.docs[0];
          const status = String(call.get("status") || "");
          if (["ringing", "accepted"].includes(status)) {
            await call.ref.update({
              status: "ended",
              endedAt: FieldValue.serverTimestamp(),
              updatedAt: FieldValue.serverTimestamp(),
            });
          }
        }
        response.status(204).send();
        return;
      }
      const call = groupCalls.docs[0];
      const participantId = event.participant?.identity;
      if (participantId && event.event === "participant_joined") await setGroupCallParticipantPresence(call.id, participantId, true);
      else if (participantId && ["participant_left", "participant_connection_aborted"].includes(event.event)) await setGroupCallParticipantPresence(call.id, participantId, false);
      if (event.event === "room_finished") {
        await call.ref.update({ status: "ended", participantCount: 0, endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
      }
      response.status(204).send();
      return;
    }
    const live = sessions.docs[0];
    const hostId = String(live.get("hostId") || "");
    const participantId = event.participant?.identity;
    if (participantId && participantId !== hostId) {
      if (event.event === "participant_joined") {
        await setViewerPresence(live.id, participantId, true);
      } else if (["participant_left", "participant_connection_aborted"].includes(event.event)) {
        await setViewerPresence(live.id, participantId, false);
      }
    }
    if (event.event === "room_finished") {
      await live.ref.update({
        status: "ended",
        viewerCount: 0,
        endedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      await db.doc(`users/${hostId}/liveState/current`).set({
        liveId: live.id,
        status: "ended",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    response.status(204).send();
  } catch (error) {
    logger.warn("Webhook LiveKit rejeté", { error });
    response.status(401).send("Unauthorized");
  }
});

/**
 * Firestore rules intentionally protect an expired story with server time.
 * A direct client query cannot prove its local timestamp is newer than the
 * server timestamp, so this callable is the safe, server-authoritative read
 * path for the Actus screen.
 */
async function storyAudienceIds(userId: string): Promise<string[]> {
  const directContacts = await db.collection("conversations")
    .where("memberIds", "array-contains", userId)
    .get();
  const directIds = directContacts.docs
    .filter((document) => document.get("conversationType") === "direct" || (document.get("memberIds") as unknown[] | undefined)?.length === 2)
    .flatMap((document) => Array.isArray(document.get("memberIds")) ? (document.get("memberIds") as unknown[]).map(String) : [])
    .filter(Boolean);
  const profile = await db.collection("users").doc(userId).get();
  const savedIds = profile.exists && profile.get("contacts") && typeof profile.get("contacts") === "object"
    ? Object.keys(profile.get("contacts") as Record<string, unknown>)
    : [];
  const groups = await db.collection("groups").where("memberIds", "array-contains", userId).get();
  const groupIds = groups.docs.flatMap((document) => Array.isArray(document.get("memberIds")) ? (document.get("memberIds") as unknown[]).map(String) : []);
  return [...new Set([...directIds, ...savedIds, ...groupIds, userId])].filter(Boolean).slice(0, 500);
}

/** Publishes a Story through a server-owned audience and 24-hour expiry. */
export const publishStory = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const clientRequestId = String(request.data?.clientRequestId || "").trim();
  if (clientRequestId && !/^[A-Za-z0-9_-]{8,80}$/.test(clientRequestId)) {
    throw new HttpsError("invalid-argument", "Identifiant de publication Story invalide.");
  }
  const story = clientRequestId
    ? db.collection("stories").doc(`${userId}_${clientRequestId}`)
    : db.collection("stories").doc();
  if (clientRequestId) {
    const existing = await story.get();
    if (existing.exists) {
      const value = existing.data() || {};
      const createdAt = Number(value.createdAtMillis || (value.createdAt?.toMillis?.() ?? Date.now()));
      const expiresAt = Number(value.expiresAt?.toMillis?.() ?? createdAt + 24 * 60 * 60 * 1_000);
      return {
        id: existing.id,
        authorId: String(value.authorId || userId),
        authorName: String(value.authorName || "Membre WAPI"),
        authorPhotoUrl: String(value.authorPhotoUrl || ""),
        caption: String(value.caption || ""),
        mediaUrl: String(value.mediaUrl || ""),
        mediaType: String(value.mediaType || "text"),
        createdAtMillis: createdAt,
        expiresAtMillis: expiresAt,
        viewCount: Number(value.viewCount || 0),
      };
    }
  }
  const caption = String(request.data?.caption || "").trim().slice(0, 600);
  const mediaType = String(request.data?.mediaType || "text").trim().toLowerCase();
  let mediaUrl = String(request.data?.mediaUrl || "").trim().slice(0, 2_000);
  let storagePath = String(request.data?.storagePath || "").trim().slice(0, 300);
  const mediaDataBase64 = request.data?.mediaDataBase64;
  if (!["text", "image", "video", "audio"].includes(mediaType)) throw new HttpsError("invalid-argument", "Type de Story invalide.");
  let uploadedFilePath = "";
  if (mediaDataBase64 !== undefined) {
    if (mediaType !== "image" || mediaUrl || storagePath) throw new HttpsError("invalid-argument", "Le média Story est invalide.");
    let uploadedPhoto: ReturnType<typeof decodeGroupPhotoBase64>;
    try {
      uploadedPhoto = decodeGroupPhotoBase64(mediaDataBase64);
    } catch {
      throw new HttpsError("invalid-argument", "Cette image Story est invalide ou trop volumineuse.");
    }
    if (!uploadedPhoto) throw new HttpsError("invalid-argument", "L’image Story est vide.");
    const bucket = getStorage().bucket();
    const downloadToken = randomUUID();
    uploadedFilePath = `stories/${userId}/story-${randomUUID()}.${uploadedPhoto.extension}`;
    await bucket.file(uploadedFilePath).save(uploadedPhoto.bytes, {
      resumable: false,
      metadata: {
        contentType: uploadedPhoto.contentType,
        cacheControl: "public,max-age=86400",
        metadata: { firebaseStorageDownloadTokens: downloadToken },
      },
    });
    storagePath = uploadedFilePath;
    mediaUrl = `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(bucket.name)}/o/${encodeURIComponent(storagePath)}?alt=media&token=${downloadToken}`;
  }
  if (!caption && !mediaUrl) throw new HttpsError("invalid-argument", "Ajoutez un texte ou un média.");
  const isFirebaseMedia = mediaUrl.startsWith("https://firebasestorage.googleapis.com/") || mediaUrl.startsWith("https://storage.googleapis.com/");
  if (mediaType === "text" && (mediaUrl || storagePath)) throw new HttpsError("invalid-argument", "Une Story texte ne peut pas contenir un média externe.");
  if (mediaType !== "text" && (!isFirebaseMedia || !storagePath.startsWith(`stories/${userId}/`))) throw new HttpsError("invalid-argument", "Le média Story n’est pas sécurisé.");
  const profile = await db.collection("users").doc(userId).get();
  const authorName = String(profile.get("displayName") || profile.get("phoneNumber") || "Membre WAPI").trim().slice(0, 80);
  const authorPhotoUrl = String(profile.get("photoUrl") || "").trim().slice(0, 2_000);
  const createdAt = Date.now();
  try {
    await story.set({
      authorId: userId,
      authorName,
      authorPhotoUrl,
      caption,
      mediaUrl,
      mediaType,
      storagePath,
      clientRequestId: clientRequestId || null,
      audienceIds: await storyAudienceIds(userId),
      viewCount: 0,
      // Keep a concrete millisecond value in addition to the server timestamp.
      // A just-created Firestore serverTimestamp can be unresolved in the first
      // response read; the mobile clients use this value to keep the Story in
      // the rail immediately after publication.
      createdAtMillis: createdAt,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt: new Date(createdAt + 24 * 60 * 60 * 1_000),
    });
  } catch (error) {
    if (uploadedFilePath) await getStorage().bucket().file(uploadedFilePath).delete({ ignoreNotFound: true }).catch(() => undefined);
    throw error;
  }
  return { id: story.id, authorId: userId, authorName, authorPhotoUrl, caption, mediaUrl, mediaType, createdAtMillis: createdAt, expiresAtMillis: createdAt + 24 * 60 * 60 * 1_000, viewCount: 0 };
});

export const listVisibleStories = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  }
  const snapshot = await db.collection("stories")
    .where("audienceIds", "array-contains", request.auth.uid)
    .where("expiresAt", ">", new Date())
    .limit(250)
    .get();
  const viewerId = request.auth.uid;
  const missingPhotoAuthorIds = [...new Set(snapshot.docs
    .filter((document) => !String(document.get("authorPhotoUrl") || "").trim())
    .map((document) => String(document.get("authorId") || "").trim())
    .filter(Boolean))];
  const profilePhotos = new Map<string, string>();
  if (missingPhotoAuthorIds.length) {
    const profiles = await db.getAll(...missingPhotoAuthorIds.map((authorId) => db.doc(`users/${authorId}`)));
    profiles.forEach((profile) => {
      profilePhotos.set(profile.id, String(profile.get("photoUrl") || "").trim().slice(0, 2000));
    });
  }
  // Fetching one view document at a time made the Actus rail increasingly
  // slow as a user followed more people.  Read the viewer state in one batch
  // instead: this keeps a newly published Story responsive and avoids a burst
  // of hundreds of callable-function reads on every refresh.
  const viewerSnapshots = snapshot.empty
    ? []
    : await db.getAll(...snapshot.docs.map((document) => document.ref.collection("views").doc(viewerId)));
  const viewedStoryIds = new Set(
    viewerSnapshots
      .filter((view) => view.exists)
      .map((view) => view.ref.parent.parent?.id)
      .filter((storyId): storyId is string => Boolean(storyId)),
  );
  const stories = snapshot.docs.map((document) => {
    const value = document.data();
    const createdAt = value.createdAt;
    return {
      id: document.id,
      authorId: String(value.authorId || ""),
      authorName: String(value.authorName || "Contact WAPI"),
      authorPhotoUrl: String(value.authorPhotoUrl || profilePhotos.get(String(value.authorId || "")) || ""),
      caption: String(value.caption || ""),
      mediaUrl: String(value.mediaUrl || ""),
      mediaType: String(value.mediaType || "text"),
      createdAt: createdAt && typeof createdAt.toDate === "function"
        ? createdAt.toDate().toISOString()
        : null,
      createdAtMillis: Number(value.createdAtMillis || (createdAt && typeof createdAt.toMillis === "function" ? createdAt.toMillis() : 0)),
      expiresAtMillis: value.expiresAt && typeof value.expiresAt.toMillis === "function"
        ? value.expiresAt.toMillis()
        : 0,
      viewCount: Number(value.viewCount || 0),
      viewedByCurrentUser: viewedStoryIds.has(document.id),
    };
  });
  // A server timestamp can be unresolved in the first few milliseconds after
  // publishing.  The concrete millisecond field is therefore the canonical
  // ordering key for every client, including the just-created Story.
  stories.sort((left, right) => right.createdAtMillis - left.createdAtMillis);
  return { stories };
});

/** Records one unique viewer per Story. The author is never counted. */
export const recordStoryView = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const storyId = String(request.data?.storyId || "").trim();
  if (!storyId || storyId.length > 160) throw new HttpsError("invalid-argument", "Story invalide.");
  const viewerId = request.auth.uid;
  const story = db.collection("stories").doc(storyId);
  const view = story.collection("views").doc(viewerId);
  const profile = await db.collection("users").doc(viewerId).get();
  const displayName = String(profile.get("displayName") || profile.get("phoneNumber") || "Contact WAPI").trim().slice(0, 80);
  const photoUrl = String(profile.get("photoUrl") || "").slice(0, 2000);
  const count = await db.runTransaction(async (transaction) => {
    const [storySnapshot, viewSnapshot] = await Promise.all([transaction.get(story), transaction.get(view)]);
    if (!storySnapshot.exists) throw new HttpsError("not-found", "Cette Story n’existe plus.");
    const value = storySnapshot.data() || {};
    const audienceIds = Array.isArray(value.audienceIds) ? value.audienceIds.map(String) : [];
    const expiresAt = value.expiresAt;
    if (!audienceIds.includes(viewerId) || !expiresAt?.toMillis || expiresAt.toMillis() <= Date.now()) {
      throw new HttpsError("permission-denied", "Cette Story n’est plus visible.");
    }
    const currentCount = Math.max(0, Number(value.viewCount || 0));
    if (String(value.authorId || "") === viewerId || viewSnapshot.exists) return currentCount;
    transaction.set(view, {
      userId: viewerId,
      displayName: displayName || "Contact WAPI",
      photoUrl,
      viewedAt: FieldValue.serverTimestamp(),
    });
    transaction.update(story, { viewCount: FieldValue.increment(1) });
    return currentCount + 1;
  });
  return { ok: true, viewCount: count };
});

/** Only the Story author can inspect the identities behind the counter. */
export const listStoryViewers = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const storyId = String(request.data?.storyId || "").trim();
  if (!storyId || storyId.length > 160) throw new HttpsError("invalid-argument", "Story invalide.");
  const story = await db.collection("stories").doc(storyId).get();
  if (!story.exists) throw new HttpsError("not-found", "Cette Story n’existe plus.");
  if (String(story.get("authorId") || "") !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Seul l’auteur peut voir cette liste.");
  }
  const snapshot = await story.ref.collection("views").orderBy("viewedAt", "desc").limit(500).get();
  return {
    viewers: snapshot.docs.map((document) => {
      const value = document.data();
      return {
        userId: document.id,
        displayName: String(value.displayName || "Contact WAPI"),
        photoUrl: String(value.photoUrl || ""),
        viewedAtMillis: value.viewedAt && typeof value.viewedAt.toMillis === "function" ? value.viewedAt.toMillis() : 0,
      };
    }),
  };
});

export const deleteStory = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const storyId = String(request.data?.storyId || "").trim();
  if (!storyId) throw new HttpsError("invalid-argument", "Story invalide.");
  const story = db.collection("stories").doc(storyId);
  const snapshot = await story.get();
  if (!snapshot.exists) return { ok: true };
  if (snapshot.get("authorId") !== request.auth.uid) throw new HttpsError("permission-denied", "Seul l’auteur peut supprimer cette Story.");
  const views = await story.collection("views").limit(500).get();
  const batch = db.batch();
  views.docs.forEach((view) => batch.delete(view.ref));
  batch.delete(story);
  await batch.commit();
  return { ok: true };
});

/** The creator remains owner; only they can delegate administration. */
export const manageGroupAdministration = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const groupId = String(request.data?.groupId || "").trim();
  const source = request.data?.source === "conversations" ? "conversations" : "groups";
  const memberId = String(request.data?.memberId || "").trim();
  const administrator = request.data?.administrator === true;
  if (!groupId || !memberId) throw new HttpsError("invalid-argument", "Groupe ou membre invalide.");
  const group = db.collection(source).doc(groupId);
  let resolvedAdmins: string[] = [];
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(group);
    if (!snapshot.exists) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
    const record = (snapshot.data() || {}) as Record<string, unknown>;
    const recordMemberIds = Array.isArray(record.memberIds) ? record.memberIds.map(String) : [];
    if (source === "conversations" && !isGroupDocument(record, recordMemberIds)) {
      throw new HttpsError("failed-precondition", "Cette conversation n’est pas un groupe.");
    }
    const ownerId = String(snapshot.get("ownerId") || "");
    const memberIds = Array.isArray(snapshot.get("memberIds")) ? (snapshot.get("memberIds") as unknown[]).map(String) : [];
    const currentAdmins = Array.isArray(snapshot.get("adminIds")) ? (snapshot.get("adminIds") as unknown[]).map(String) : [];
    if (ownerId !== request.auth!.uid) throw new HttpsError("permission-denied", "Seul le créateur peut gérer les administrateurs.");
    if (!memberIds.includes(memberId)) throw new HttpsError("failed-precondition", "Ce compte n’est pas membre du groupe.");
    if (memberId === ownerId && !administrator) throw new HttpsError("failed-precondition", "Le créateur reste administrateur.");
    const nextAdmins = [...new Set([ownerId, ...currentAdmins.filter((id) => id !== memberId), ...(administrator ? [memberId] : [])])];
    resolvedAdmins = nextAdmins;
    const eventText = administrator ? "Un nouvel administrateur a été nommé" : "Un administrateur a été retiré";
    transaction.update(group, { adminIds: nextAdmins, lastMessage: eventText, lastSenderId: request.auth!.uid, updatedAt: FieldValue.serverTimestamp() });
    const event = group.collection("messages").doc();
    transaction.set(event, {
      text: eventText,
      senderId: request.auth!.uid,
      senderName: "Administration du groupe",
      kind: "system",
      createdAt: FieldValue.serverTimestamp(),
      deleted: false,
    });
  });
  return { ok: true, adminIds: resolvedAdmins };
});

/**
 * Updates both current `groups` documents and legacy group conversations.
 * Keeping this operation server-authoritative prevents a client from granting
 * itself administration rights and guarantees one visible system event.
 */
export const updateGroupIdentity = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const groupId = String(request.data?.groupId || "").trim();
  const source = request.data?.source === "conversations" ? "conversations" : "groups";
  const name = String(request.data?.name || "").trim();
  const photoUrl = String(request.data?.photoUrl || "").trim();
  const photoDataBase64 = request.data?.photoDataBase64;
  const removePhoto = request.data?.removePhoto === true;
  if (!groupId || name.length < 2 || name.length > 80) {
    throw new HttpsError("invalid-argument", "Le nom du groupe doit contenir entre 2 et 80 caractères.");
  }
  if (photoUrl.length > 2_000 || (photoUrl && !photoUrl.startsWith("https://firebasestorage.googleapis.com/"))) {
    throw new HttpsError("invalid-argument", "La photo du groupe n’est pas un média WAPI valide.");
  }
  let uploadedPhoto: ReturnType<typeof decodeGroupPhotoBase64>;
  try {
    uploadedPhoto = decodeGroupPhotoBase64(photoDataBase64);
  } catch {
    throw new HttpsError("invalid-argument", "La photo du groupe est invalide ou trop volumineuse.");
  }
  if (removePhoto && uploadedPhoto) {
    throw new HttpsError("invalid-argument", "Choisissez soit une nouvelle photo, soit sa suppression.");
  }

  const reference = db.collection(source).doc(groupId);
  const snapshot = await reference.get();
  const value = snapshot.data();
  if (!snapshot.exists || !value) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  const memberIds = Array.isArray(value.memberIds) ? value.memberIds.map(String) : [];
  const ownerId = String(value.ownerId || "");
  const adminIds = Array.isArray(value.adminIds) ? value.adminIds.map(String) : [];
  const memberMayEditInfo = value.editInfoByMembers !== false;
  if (!memberIds.includes(request.auth.uid) || (!memberMayEditInfo && request.auth.uid !== ownerId && !adminIds.includes(request.auth.uid))) {
    throw new HttpsError("permission-denied", "Les administrateurs ont réservé la modification des informations du groupe.");
  }
  if (source === "conversations" && !isGroupDocument(value as Record<string, unknown>, memberIds)) {
    throw new HttpsError("failed-precondition", "Cette conversation n’est pas un groupe.");
  }

  const previousName = String(source === "groups" ? value.name || "" : value.title || "").trim();
  const previousPhoto = String(source === "groups" ? value.photoUrl || "" : value.groupPhotoUrl || "").trim();
  let uploadedFilePath = "";
  let uploadedPhotoUrl = "";
  if (uploadedPhoto) {
    const bucket = getStorage().bucket();
    const downloadToken = randomUUID();
    uploadedFilePath = `${source}/${groupId}/${request.auth.uid}/cover-${randomUUID()}.${uploadedPhoto.extension}`;
    await bucket.file(uploadedFilePath).save(uploadedPhoto.bytes, {
      resumable: false,
      metadata: {
        contentType: uploadedPhoto.contentType,
        cacheControl: "public,max-age=31536000,immutable",
        metadata: { firebaseStorageDownloadTokens: downloadToken },
      },
    });
    uploadedPhotoUrl = `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(bucket.name)}/o/${encodeURIComponent(uploadedFilePath)}?alt=media&token=${downloadToken}`;
  }
  const nextPhoto = removePhoto ? "" : (uploadedPhotoUrl || photoUrl || previousPhoto);
  const changedName = name !== previousName;
  const changedPhoto = nextPhoto !== previousPhoto;
  if (!changedName && !changedPhoto) return { ok: true, name, photoUrl: nextPhoto, unchanged: true };

  const actor = await liveDisplayName(request.auth.uid);
  const action = changedName && changedPhoto
    ? `a modifié le nom et la photo du groupe`
    : changedName
      ? `a renommé le groupe en « ${name} »`
      : nextPhoto
        ? "a changé la photo du groupe"
        : "a supprimé la photo du groupe";
  const eventText = `${actor.displayName} ${action}`.slice(0, 500);
  const event = reference.collection("messages").doc();
  const batch = db.batch();
  batch.update(reference, {
    [source === "groups" ? "name" : "title"]: name,
    [source === "groups" ? "photoUrl" : "groupPhotoUrl"]: nextPhoto,
    ...(source === "conversations" ? { conversationType: "group", kind: "group", isGroup: true } : {}),
    lastMessage: eventText,
    lastSenderId: request.auth.uid,
    updatedAt: FieldValue.serverTimestamp(),
  });
  batch.set(event, {
    text: eventText,
    senderId: request.auth.uid,
    senderName: actor.displayName,
    senderPhotoUrl: actor.photoUrl,
    kind: "system",
    clientMessageId: event.id,
    createdAt: FieldValue.serverTimestamp(),
  });
  try {
    await batch.commit();
  } catch (error) {
    if (uploadedFilePath) await getStorage().bucket().file(uploadedFilePath).delete({ ignoreNotFound: true }).catch(() => undefined);
    throw error;
  }
  return { ok: true, name, photoUrl: nextPhoto, eventText };
});

export const manageGroupMembers = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  }
  const actorId = request.auth.uid;
  const groupId = String(request.data?.groupId || request.data?.conversationId || "").trim();
  const source = request.data?.source === "conversations" ? "conversations" : "groups";
  const action = String(request.data?.action || "").trim();
  const requestedIds: string[] = Array.isArray(request.data?.memberIds)
    ? [...new Set<string>((request.data.memberIds as unknown[]).map((value) => String(value).trim()).filter(Boolean))]
    : [];
  if (!groupId || !["add", "remove"].includes(action)) {
    throw new HttpsError("invalid-argument", "Action de groupe invalide.");
  }
  if (!requestedIds.length || requestedIds.length > 20) {
    throw new HttpsError("invalid-argument", "Sélectionnez entre 1 et 20 membres.");
  }

  const group = db.collection(source).doc(groupId);
  const initial = await group.get();
  const initialData = initial.data();
  const initialMembers = Array.isArray(initialData?.memberIds) ? initialData.memberIds.map(String) : [];
  if (!initial.exists || !initialData || (source === "conversations" && !isGroupDocument(initialData, initialMembers))) {
    throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  }
  const ownerId = String(initialData.ownerId || "");
  const initialAdmins = Array.isArray(initialData.adminIds) ? initialData.adminIds.map(String) : [];
  if (ownerId !== actorId && !initialAdmins.includes(actorId)) {
    throw new HttpsError("permission-denied", "Seuls les administrateurs peuvent modifier les membres.");
  }
  if (action === "remove" && requestedIds.includes(ownerId)) {
    throw new HttpsError("failed-precondition", "Le créateur ne peut pas être retiré du groupe.");
  }

  const profiles = action === "add"
    ? await db.getAll(...requestedIds.map((uid) => db.collection("users").doc(uid)))
    : [];
  if (action === "add" && profiles.some((profile) => !profile.exists)) {
    throw new HttpsError("not-found", "Un des comptes WAPI sélectionnés n’existe pas.");
  }
  const profilesById = new Map(profiles.map((profile) => [profile.id, profile.data() || {}]));
  const notice = action === "add" ? "Nouveaux membres ajoutés" : "Membres retirés";
  const systemMessage = group.collection("messages").doc();

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(group);
    const value = snapshot.data();
    const liveMembers = Array.isArray(value?.memberIds) ? value.memberIds.map(String) : [];
    const liveAdmins = Array.isArray(value?.adminIds) ? value.adminIds.map(String) : [];
    if (!snapshot.exists || !value || (source === "conversations" && !isGroupDocument(value, liveMembers)) || (String(value.ownerId || "") !== actorId && !liveAdmins.includes(actorId))) {
      throw new HttpsError("permission-denied", "Le groupe a été modifié. Réessayez.");
    }
    const existingMembers = Array.isArray(value.members) ? value.members : [];
    const byId = new Map<string, Record<string, unknown>>();
    existingMembers.forEach((member: Record<string, unknown>) => {
      const uid = String(member.uid || "");
      if (uid) byId.set(uid, member);
    });
    if (action === "add") {
      requestedIds.forEach((uid) => {
        const profile = profilesById.get(uid) || {};
        byId.set(uid, {
          uid,
          displayName: String(profile.displayName || profile.phoneNumber || "Membre WAPI"),
          phoneNumber: String(profile.phoneNumber || ""),
          photoUrl: String(profile.photoUrl || ""),
        });
      });
    } else {
      requestedIds.forEach((uid) => byId.delete(uid));
    }
    if (!byId.has(ownerId) || byId.size < 2 || byId.size > 64) {
      throw new HttpsError("failed-precondition", "Un groupe doit contenir entre 2 et 64 membres.");
    }
    const members = [...byId.values()];
    const memberIds = [...byId.keys()];
    const adminIds = [...new Set([ownerId, ...liveAdmins.filter((id: string) => memberIds.includes(id))])].filter(Boolean);
    transaction.update(group, {
      memberIds,
      memberNames: members.map((member) => String(member.displayName || "Membre WAPI")),
      members,
      adminIds,
      lastMessage: notice,
      lastSenderId: actorId,
      updatedAt: FieldValue.serverTimestamp(),
    });
    transaction.set(systemMessage, {
      text: notice,
      senderId: actorId,
      kind: "system",
      clientMessageId: systemMessage.id,
      createdAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

export const updateGroupSettings = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const groupId = String(request.data?.groupId || "").trim();
  const source = request.data?.source === "conversations" ? "conversations" : "groups";
  const description = String(request.data?.description || "").trim().slice(0, 300);
  const editInfoByMembers = request.data?.editInfoByMembers !== false;
  const onlyAdminsCanSend = request.data?.onlyAdminsCanSend === true;
  if (!groupId) throw new HttpsError("invalid-argument", "Groupe invalide.");
  const reference = db.collection(source).doc(groupId);
  const snapshot = await reference.get();
  const value = snapshot.data();
  if (!snapshot.exists || !value) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  const memberIds = Array.isArray(value.memberIds) ? value.memberIds.map(String) : [];
  if (source === "conversations" && !isGroupDocument(value, memberIds)) throw new HttpsError("failed-precondition", "Cette conversation n’est pas un groupe.");
  const ownerId = String(value.ownerId || "");
  const adminIds = Array.isArray(value.adminIds) ? value.adminIds.map(String) : [];
  if (request.auth.uid !== ownerId && !adminIds.includes(request.auth.uid)) throw new HttpsError("permission-denied", "Seuls les administrateurs peuvent modifier les permissions.");
  const actor = await liveDisplayName(request.auth.uid);
  const eventText = `${actor.displayName} a mis à jour les paramètres du groupe`.slice(0, 300);
  const event = reference.collection("messages").doc();
  const batch = db.batch();
  batch.update(reference, {
    description,
    editInfoByMembers,
    onlyAdminsCanSend,
    lastMessage: eventText,
    lastSenderId: request.auth.uid,
    updatedAt: FieldValue.serverTimestamp(),
  });
  batch.set(event, { text: eventText, senderId: request.auth.uid, senderName: actor.displayName, senderPhotoUrl: actor.photoUrl, kind: "system", clientMessageId: event.id, createdAt: FieldValue.serverTimestamp() });
  await batch.commit();
  return { ok: true, description, editInfoByMembers, onlyAdminsCanSend };
});

type KingQiQuestion = {
  id: string;
  category: string;
  difficulty: "facile" | "moyen" | "expert";
  text: string;
  options: [string, string, string, string];
  correctIndex: number;
};

const kingQiQuestions: KingQiQuestion[] = [
  { id: "geo_01", category: "Géographie", difficulty: "facile", text: "Quelle est la capitale du Japon ?", options: ["Séoul", "Tokyo", "Pékin", "Bangkok"], correctIndex: 1 },
  { id: "sci_01", category: "Sciences", difficulty: "facile", text: "Quelle est la formule chimique de l'eau ?", options: ["CO2", "O2", "H2O", "NaCl"], correctIndex: 2 },
  { id: "math_01", category: "Logique", difficulty: "facile", text: "Quel est le premier nombre premier ?", options: ["0", "1", "2", "3"], correctIndex: 2 },
  { id: "geo_02", category: "Géographie", difficulty: "moyen", text: "Quel est le plus grand océan du monde ?", options: ["Atlantique", "Indien", "Arctique", "Pacifique"], correctIndex: 3 },
  { id: "sci_02", category: "Sciences", difficulty: "moyen", text: "Quel processus permet aux plantes de transformer la lumière en énergie ?", options: ["Respiration", "Photosynthèse", "Fermentation", "Osmose"], correctIndex: 1 },
  { id: "culture_01", category: "Culture", difficulty: "moyen", text: "Combien de continents compte le modèle géographique le plus courant ?", options: ["Cinq", "Six", "Sept", "Huit"], correctIndex: 2 },
  { id: "tech_01", category: "Technologie", difficulty: "moyen", text: "Que signifie l'acronyme GPS ?", options: ["Global Positioning System", "General Public Signal", "Geo Personal Service", "Global Phone Sync"], correctIndex: 0 },
  { id: "history_01", category: "Histoire", difficulty: "moyen", text: "Dans quelle civilisation les pyramides de Gizeh ont-elles été construites ?", options: ["Romaine", "Maya", "Égyptienne", "Perse"], correctIndex: 2 },
  { id: "sci_03", category: "Sciences", difficulty: "expert", text: "Quel élément chimique porte le symbole Fe ?", options: ["Fluor", "Fer", "Francium", "Fermium"], correctIndex: 1 },
  { id: "math_02", category: "Logique", difficulty: "expert", text: "Quelle est la racine carrée de 144 ?", options: ["10", "11", "12", "14"], correctIndex: 2 },
  { id: "geo_03", category: "Géographie", difficulty: "expert", text: "Quel fleuve traverse Brazzaville et Kinshasa ?", options: ["Nil", "Congo", "Niger", "Zambèze"], correctIndex: 1 },
  { id: "tech_02", category: "Technologie", difficulty: "expert", text: "Quel protocole sécurisé protège généralement une page web ?", options: ["FTP", "SMTP", "HTTPS", "POP3"], correctIndex: 2 },
  { id: "geo_04", category: "Géographie", difficulty: "facile", text: "Quelle est la capitale du Brésil ?", options: ["Lima", "Brasília", "Bogota", "Quito"], correctIndex: 1 },
  { id: "culture_02", category: "Culture", difficulty: "facile", text: "Combien de côtés possède un hexagone ?", options: ["Cinq", "Six", "Sept", "Huit"], correctIndex: 1 },
  { id: "sci_04", category: "Sciences", difficulty: "moyen", text: "Quel organe pompe le sang dans le corps humain ?", options: ["Le foie", "Le poumon", "Le cœur", "Le rein"], correctIndex: 2 },
  { id: "history_02", category: "Histoire", difficulty: "moyen", text: "Qui fut le premier humain à marcher sur la Lune ?", options: ["Neil Armstrong", "Youri Gagarine", "Buzz Aldrin", "John Glenn"], correctIndex: 0 },
  { id: "tech_03", category: "Technologie", difficulty: "moyen", text: "Quel langage structure principalement une page web ?", options: ["CSS", "HTML", "SQL", "Kotlin"], correctIndex: 1 },
  { id: "math_03", category: "Logique", difficulty: "moyen", text: "Quelle est la suite : 2, 4, 8, 16, ... ?", options: ["20", "24", "30", "32"], correctIndex: 3 },
  { id: "geo_05", category: "Géographie", difficulty: "expert", text: "Quel est le plus grand désert du monde ?", options: ["Sahara", "Gobi", "Antarctique", "Kalahari"], correctIndex: 2 },
  { id: "sci_05", category: "Sciences", difficulty: "expert", text: "À quelle température l’eau bout-elle au niveau de la mer ?", options: ["50 °C", "80 °C", "90 °C", "100 °C"], correctIndex: 3 },
  { id: "hist_03", category: "Histoire", difficulty: "facile", text: "Quel mur historique se trouve en Chine ?", options: ["Mur d'Hadrien", "Grande Muraille", "Mur des Lamentations", "Mur de Berlin"], correctIndex: 1 },
  { id: "lang_01", category: "Langues", difficulty: "facile", text: "Quel mot signifie « bonjour » en espagnol ?", options: ["Ciao", "Hello", "Hola", "Olá"], correctIndex: 2 },
  { id: "nature_01", category: "Nature", difficulty: "facile", text: "Quel animal est le plus grand mammifère du monde ?", options: ["Éléphant", "Baleine bleue", "Girafe", "Requin-baleine"], correctIndex: 1 },
  { id: "sport_01", category: "Sport", difficulty: "facile", text: "Combien de joueurs une équipe de football aligne-t-elle sur le terrain ?", options: ["9", "10", "11", "12"], correctIndex: 2 },
  { id: "math_04", category: "Logique", difficulty: "moyen", text: "Quel nombre complète la suite : 3, 6, 12, 24, ... ?", options: ["36", "42", "48", "54"], correctIndex: 2 },
  { id: "sci_06", category: "Sciences", difficulty: "moyen", text: "Quelle planète est connue comme la planète rouge ?", options: ["Mars", "Vénus", "Jupiter", "Mercure"], correctIndex: 0 },
  { id: "geo_06", category: "Géographie", difficulty: "moyen", text: "Sur quel continent se trouve le Congo ?", options: ["Asie", "Afrique", "Europe", "Amérique du Sud"], correctIndex: 1 },
  { id: "culture_03", category: "Culture", difficulty: "moyen", text: "Combien de cordes possède une guitare classique ?", options: ["4", "5", "6", "7"], correctIndex: 2 },
  { id: "tech_04", category: "Technologie", difficulty: "moyen", text: "Quel composant stocke temporairement les données d'un téléphone ?", options: ["RAM", "Écran", "Micro", "Haut-parleur"], correctIndex: 0 },
  { id: "lang_02", category: "Langues", difficulty: "moyen", text: "Quelle langue est majoritaire au Brésil ?", options: ["Espagnol", "Portugais", "Français", "Anglais"], correctIndex: 1 },
  { id: "hist_04", category: "Histoire", difficulty: "moyen", text: "Quelle ville était ensevelie par le Vésuve en 79 ?", options: ["Pompéi", "Athènes", "Carthage", "Rome"], correctIndex: 0 },
  { id: "sport_02", category: "Sport", difficulty: "moyen", text: "Combien de cases compte un échiquier ?", options: ["36", "49", "64", "81"], correctIndex: 2 },
  { id: "nature_02", category: "Nature", difficulty: "moyen", text: "Quel gaz les plantes absorbent-elles principalement ?", options: ["Oxygène", "Dioxyde de carbone", "Hélium", "Azote"], correctIndex: 1 },
  { id: "math_05", category: "Logique", difficulty: "expert", text: "Si 5 machines produisent 5 pièces en 5 minutes, combien de minutes faut-il à 100 machines pour produire 100 pièces ?", options: ["5", "20", "100", "500"], correctIndex: 0 },
  { id: "sci_07", category: "Sciences", difficulty: "expert", text: "Quelle unité mesure une fréquence ?", options: ["Watt", "Pascal", "Hertz", "Joule"], correctIndex: 2 },
  { id: "geo_07", category: "Géographie", difficulty: "expert", text: "Quel détroit sépare l'Europe et l'Afrique ?", options: ["Béring", "Gibraltar", "Malacca", "Ormuz"], correctIndex: 1 },
  { id: "tech_05", category: "Technologie", difficulty: "expert", text: "Quelle pratique chiffre des données sans pouvoir les modifier ?", options: ["Hachage", "Compression", "Indexation", "Cache"], correctIndex: 0 },
  { id: "culture_04", category: "Culture", difficulty: "expert", text: "Qui a écrit « Le Petit Prince » ?", options: ["Victor Hugo", "Albert Camus", "Antoine de Saint-Exupéry", "Jules Verne"], correctIndex: 2 },
  { id: "lang_03", category: "Langues", difficulty: "expert", text: "Quelle écriture est utilisée principalement pour le japonais moderne ?", options: ["Cyrillique", "Kanji et kana", "Arabe", "Devanagari"], correctIndex: 1 },
  { id: "africa_01", category: "Afrique", difficulty: "expert", text: "Quel fleuve est le deuxième plus long d'Afrique après le Nil ?", options: ["Congo", "Niger", "Zambèze", "Orange"], correctIndex: 0 },
  { id: "geo_08", category: "Géographie", difficulty: "facile", text: "Quelle est la capitale du Canada ?", options: ["Toronto", "Vancouver", "Ottawa", "Montréal"], correctIndex: 2 },
  { id: "sci_08", category: "Sciences", difficulty: "facile", text: "Quelle est la plus grande planète du système solaire ?", options: ["Mars", "Jupiter", "Saturne", "Neptune"], correctIndex: 1 },
  { id: "math_06", category: "Logique", difficulty: "facile", text: "Combien font 9 multiplié par 8 ?", options: ["64", "70", "72", "81"], correctIndex: 2 },
  { id: "africa_02", category: "Afrique", difficulty: "moyen", text: "Dans quelle ville se trouve le siège de l'Union africaine ?", options: ["Nairobi", "Addis-Abeba", "Le Caire", "Dakar"], correctIndex: 1 },
  { id: "tech_06", category: "Technologie", difficulty: "moyen", text: "Quel est le système de numération fondé sur zéro et un ?", options: ["Décimal", "Binaire", "Hexadécimal", "Romain"], correctIndex: 1 },
  { id: "culture_05", category: "Culture", difficulty: "moyen", text: "Qui a écrit « Les Misérables » ?", options: ["Victor Hugo", "Émile Zola", "Molière", "Alexandre Dumas"], correctIndex: 0 },
  { id: "sci_09", category: "Sciences", difficulty: "moyen", text: "Quel métal porte le symbole chimique Au ?", options: ["Argent", "Aluminium", "Or", "Cuivre"], correctIndex: 2 },
  { id: "sport_03", category: "Sport", difficulty: "moyen", text: "Combien de joueurs d'une équipe de basket sont sur le terrain ?", options: ["Cinq", "Six", "Sept", "Huit"], correctIndex: 0 },
  { id: "sci_10", category: "Sciences", difficulty: "expert", text: "Combien de chromosomes possède normalement une cellule humaine ?", options: ["23", "44", "46", "48"], correctIndex: 2 },
  { id: "tech_07", category: "Technologie", difficulty: "expert", text: "Que signifie HTTP ?", options: ["HyperText Transfer Protocol", "High Transfer Text Process", "Hosted Terminal Transport Program", "Hybrid Text Transmission Port"], correctIndex: 0 },
  { id: "africa_03", category: "Afrique", difficulty: "expert", text: "Quelle militante kényane fut la première Africaine à recevoir le prix Nobel de la paix ?", options: ["Miriam Makeba", "Wangari Maathai", "Ellen Johnson Sirleaf", "Graça Machel"], correctIndex: 1 },
  { id: "math_07", category: "Logique", difficulty: "expert", text: "Quelle valeur approche le mieux le nombre pi ?", options: ["2,14", "2,72", "3,14", "4,13"], correctIndex: 2 },
];

const kingQiPublicQuestion = (question: KingQiQuestion, index: number) => ({
  id: question.id,
  index,
  category: question.category,
  difficulty: question.difficulty,
  text: question.text,
  options: question.options,
});

const kingQiQuestionById = (id: unknown) => kingQiQuestions.find((question) => question.id === String(id || ""));
const kingQiCreditChoices = new Set([0, 10, 25, 50]);

const adObjectives = new Set(["reach", "messages", "traffic", "sales"]);
const adPlacements = new Set(["profile_story", "inbox", "market", "live"]);
const adDestinations = new Set(["message", "page", "call", "website"]);

/**
 * Creates a Business campaign from trusted page data.
 *
 * Mobile clients only submit the creative and targeting choices. Ownership,
 * public identity, destination details, budget totals and estimated reach are
 * resolved again on the server so a modified APK cannot impersonate another
 * Business page or silently alter the campaign budget.
 */
export const createAdCampaign = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const input = (request.data || {}) as Record<string, unknown>;
  const text = (key: string, max: number) => String(input[key] || "").trim().slice(0, max);
  const pageId = text("pageId", 160);
  const objective = text("objective", 24);
  const placement = text("placement", 24);
  const destination = text("destination", 24);
  const title = text("title", 120);
  const creative = text("creative", 600);
  const cta = text("cta", 40) || (destination === "message" ? "Envoyer un message" : "Découvrir");
  const audience = text("audience", 120) || "Utilisateurs WAPI de la région";
  const city = text("city", 80);
  const countryCode = text("countryCode", 2).toUpperCase();
  const days = Number(input.days);
  const requestedTargetImpressions = Number(input.targetImpressions || 0);
  const deliveryMode = requestedTargetImpressions > 0 ? "impressions" : "budget";
  const legacyDailyBudget = Number(input.dailyBudget);

  if (!pageId) throw new HttpsError("invalid-argument", "Sélectionnez une page Business.");
  if (!adObjectives.has(objective) || !adPlacements.has(placement) || !adDestinations.has(destination)) {
    throw new HttpsError("invalid-argument", "Configuration publicitaire invalide.");
  }
  if (title.length < 2 || creative.length < 2) throw new HttpsError("invalid-argument", "Le titre et le message sont requis.");
  if (deliveryMode === "impressions" && (!Number.isSafeInteger(requestedTargetImpressions) || requestedTargetImpressions < 1_000 || requestedTargetImpressions > 20_000_000)) {
    throw new HttpsError("invalid-argument", "Le volume doit être compris entre 1 000 et 20 000 000 diffusions.");
  }
  if (deliveryMode === "budget" && (!Number.isSafeInteger(legacyDailyBudget) || legacyDailyBudget < 500 || legacyDailyBudget > 50_000_000)) {
    throw new HttpsError("invalid-argument", "Le budget quotidien doit être compris entre 500 et 50 000 000 FCFA.");
  }
  if (!Number.isSafeInteger(days) || days < 1 || days > 90) throw new HttpsError("invalid-argument", "La durée doit être comprise entre 1 et 90 jours.");
  if (countryCode && !/^[A-Z]{2}$/.test(countryCode)) throw new HttpsError("invalid-argument", "Code pays invalide.");

  const page = await db.collection("businessPages").doc(pageId).get();
  if (!page.exists || String(page.get("ownerId") || "") !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Cette page Business ne vous appartient pas.");
  }
  const pageData = page.data() || {};
  const pageName = String(pageData.name || "Business WAPI").trim().slice(0, 120);
  const pageCategory = String(pageData.category || "Business").trim().slice(0, 80);
  const phone = String(pageData.phone || "").trim().slice(0, 40);
  const link = String(pageData.website || "").trim().slice(0, 180);
  if (destination === "website" && !/^https:\/\//i.test(link)) {
    throw new HttpsError("failed-precondition", "Ajoutez un site HTTPS à votre page Business avant de choisir cette destination.");
  }
  if (destination === "call" && !phone) {
    throw new HttpsError("failed-precondition", "Ajoutez un numéro à votre page Business avant de choisir l’appel.");
  }

  const targetImpressions = deliveryMode === "impressions" ? requestedTargetImpressions : 0;
  const totalBudget = deliveryMode === "impressions"
    ? priceWapiAdDelivery(targetImpressions)
    : legacyDailyBudget * days;
  const dailyBudget = Math.max(500, Math.ceil(totalBudget / days));
  const dailyDeliveryCap = deliveryMode === "impressions" ? Math.ceil(targetImpressions / days) : 0;
  const placementFactor = placement === "profile_story" ? 180 : placement === "inbox" ? 150 : 120;
  const estimatedReach = deliveryMode === "impressions"
    ? targetImpressions
    : Math.max(120, Math.floor(dailyBudget / 500) * days * placementFactor);
  const campaign = db.collection("adCampaigns").doc();
  await campaign.create({
    ownerId: request.auth.uid,
    pageId,
    pageName,
    pageCategory,
    objective,
    placement,
    destination,
    title,
    creative,
    cta,
    audience,
    city,
    countryCode,
    phone,
    link,
    estimatedReach,
    deliveryMode,
    targetImpressions,
    pricePerThousand: deliveryMode === "impressions" ? WAPI_AD_PRICE_PER_THOUSAND : 0,
    dailyDeliveryCap,
    deliveryByDay: {},
    dailyBudget,
    days,
    totalBudget,
    status: "pending_payment",
    impressionCount: 0,
    clickCount: 0,
    dismissCount: 0,
    conversionCount: 0,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  return {
    id: campaign.id,
    status: "pending_payment",
    deliveryMode,
    targetImpressions,
    pricePerThousand: deliveryMode === "impressions" ? WAPI_AD_PRICE_PER_THOUSAND : 0,
    dailyDeliveryCap,
    estimatedReach,
    totalBudget,
  };
});

function adMillis(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value && typeof (value as { toMillis?: () => number }).toMillis === "function") {
    return (value as { toMillis: () => number }).toMillis();
  }
  return 0;
}

/**
 * Returns privacy-conscious, frequency-capped and diversified ad inventory.
 * Exact location, contacts, messages and protected characteristics are never
 * read by the WAPI Ads algorithm.
 */
export const getPersonalizedAds = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const placement = String(request.data?.placement || "inbox").trim();
  if (!adPlacements.has(placement)) throw new HttpsError("invalid-argument", "Emplacement publicitaire invalide.");
  const requestedLimit = Number(request.data?.limit || 8);
  const limit = Number.isSafeInteger(requestedLimit) ? Math.max(1, Math.min(requestedLimit, 20)) : 8;
  const [profile, campaignSnapshot, signalSnapshot] = await Promise.all([
    db.collection("users").doc(request.auth.uid).get(),
    db.collection("adCampaigns").where("status", "==", "active").limit(150).get(),
    db.collection("adEvents").where("userId", "==", request.auth.uid).limit(500).get(),
  ]);
  const profileData = profile.data() || {};
  const countryCode = String(profileData.countryCode || request.data?.localeCountry || "").trim().slice(0, 2).toUpperCase();
  const city = String(profileData.city || request.data?.localeCity || "").trim().slice(0, 80);
  const rawCampaigns = new Map<string, Record<string, unknown>>();
  const deliveryDay = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const candidates: WapiAdCandidate[] = campaignSnapshot.docs.flatMap((document) => {
    const value = document.data() as Record<string, unknown>;
    if (!String(value.title || "").trim() || !String(value.creative || "").trim()) return [];
    if (adMillis(value.endAt) > 0 && adMillis(value.endAt) <= Date.now()) return [];
    const targetImpressions = Math.max(0, Number(value.targetImpressions || 0));
    if (isWapiDeliveryComplete(Math.max(0, Number(value.impressionCount || 0)), targetImpressions)) return [];
    rawCampaigns.set(document.id, value);
    return [{
      id: document.id,
      ownerId: String(value.ownerId || ""),
      pageId: String(value.pageId || ""),
      category: String(value.pageCategory || "Business"),
      placement: String(value.placement || "inbox"),
      countryCode: String(value.countryCode || "").toUpperCase(),
      city: String(value.city || ""),
      createdAtMillis: adMillis(value.createdAt) || Date.now(),
      impressions: Math.max(0, Number(value.impressionCount || 0)),
      clicks: Math.max(0, Number(value.clickCount || 0)),
      dismissals: Math.max(0, Number(value.dismissCount || 0)),
      conversions: Math.max(0, Number(value.conversionCount || 0)),
      dailyDelivered: Math.max(0, Number((value.deliveryByDay as Record<string, unknown> | undefined)?.[deliveryDay] || 0)),
      dailyCap: Math.max(0, Number(value.dailyDeliveryCap || 0)),
    }];
  });
  const validSignalTypes = new Set(["impression", "click", "dismiss", "conversion"]);
  const signals: WapiAdSignal[] = signalSnapshot.docs.flatMap((document) => {
    const value = document.data();
    const type = String(value.type || "");
    if (!validSignalTypes.has(type)) return [];
    return [{
      campaignId: String(value.campaignId || ""),
      type: type as WapiAdSignal["type"],
      category: String(value.pageCategory || ""),
      createdAtMillis: adMillis(value.createdAt),
    }];
  });
  const ranked = rankElephantAds({ candidates, signals, userId: request.auth.uid, placement, countryCode, city, nowMillis: Date.now(), limit });
  return {
    placement,
    algorithm: { name: ELEPHANT_ALGORITHM_NAME, version: ELEPHANT_ALGORITHM_VERSION },
    ads: ranked.map((rankedAd) => {
      const value = rawCampaigns.get(rankedAd.id) || {};
      return {
        id: rankedAd.id,
        ownerId: rankedAd.ownerId,
        pageId: rankedAd.pageId,
        pageName: String(value.pageName || "Business WAPI"),
        pageCategory: rankedAd.category,
        objective: String(value.objective || "reach"),
        placement: rankedAd.placement,
        destination: String(value.destination || "page"),
        title: String(value.title || ""),
        creative: String(value.creative || ""),
        cta: String(value.cta || "Découvrir"),
        audience: String(value.audience || "Public local"),
        city: rankedAd.city,
        countryCode: rankedAd.countryCode,
        phone: String(value.phone || ""),
        link: String(value.link || ""),
        estimatedReach: Math.max(0, Number(value.estimatedReach || 0)),
        dailyBudget: Math.max(0, Number(value.dailyBudget || 0)),
        days: Math.max(1, Number(value.days || 1)),
        deliveryMode: String(value.deliveryMode || "budget"),
        targetImpressions: Math.max(0, Number(value.targetImpressions || 0)),
        pricePerThousand: Math.max(0, Number(value.pricePerThousand || 0)),
        dailyDeliveryCap: Math.max(0, Number(value.dailyDeliveryCap || 0)),
        totalBudget: Math.max(0, Number(value.totalBudget || 0)),
        rankScore: Math.round(rankedAd.score * 100) / 100,
        rankReasons: rankedAd.reasons,
        rankingEngine: ELEPHANT_ALGORITHM_NAME,
        rankingVersion: ELEPHANT_ALGORITHM_VERSION,
      };
    }),
    generatedAt: Date.now(),
  };
});

/** Records deduplicated ad behaviour and updates aggregate campaign quality. */
export const recordAdBehavior = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const campaignId = String(request.data?.campaignId || "").trim();
  const type = String(request.data?.type || "").trim();
  if (!/^[A-Za-z0-9_-]{8,160}$/.test(campaignId) || !["impression", "click", "dismiss", "conversion"].includes(type)) {
    throw new HttpsError("invalid-argument", "Événement publicitaire invalide.");
  }
  const day = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const event = db.collection("adEvents").doc(`${campaignId}_${request.auth.uid}_${type}_${day}`);
  const campaign = db.collection("adCampaigns").doc(campaignId);
  const tracked = await db.runTransaction(async (transaction) => {
    const [campaignSnapshot, eventSnapshot] = await Promise.all([transaction.get(campaign), transaction.get(event)]);
    if (eventSnapshot.exists) return false;
    if (!campaignSnapshot.exists || campaignSnapshot.get("status") !== "active") {
      throw new HttpsError("failed-precondition", "Cette campagne n’est plus en diffusion.");
    }
    const ownerId = String(campaignSnapshot.get("ownerId") || "");
    if (!ownerId || ownerId === request.auth!.uid) return false;
    const counter = type === "impression" ? "impressionCount" : type === "click" ? "clickCount" : type === "dismiss" ? "dismissCount" : "conversionCount";
    transaction.create(event, {
      campaignId,
      ownerId,
      userId: request.auth!.uid,
      type,
      pageCategory: String(campaignSnapshot.get("pageCategory") || "Business").slice(0, 80),
      placement: String(campaignSnapshot.get("placement") || "inbox").slice(0, 24),
      createdAt: FieldValue.serverTimestamp(),
    });
    const update: Record<string, unknown> = { [counter]: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() };
    if (type === "impression") {
      update[`deliveryByDay.${day}`] = FieldValue.increment(1);
      const targetImpressions = Math.max(0, Number(campaignSnapshot.get("targetImpressions") || 0));
      const delivered = Math.max(0, Number(campaignSnapshot.get("impressionCount") || 0)) + 1;
      if (isWapiDeliveryComplete(delivered, targetImpressions)) {
        update.status = "completed";
        update.completedReason = "delivery_quota_reached";
        update.completedAt = FieldValue.serverTimestamp();
      }
    }
    transaction.update(campaign, update);
    return true;
  });
  return { ok: true, tracked };
});

/** Founder review is the only manual path from pending payment to delivery. */
export const reviewAdCampaign = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const phone = String(request.auth.token.phone_number || "").replace(/\D/g, "");
  if (phone !== "242065465808" && phone !== "065465808") {
    throw new HttpsError("permission-denied", "Validation réservée au Fondateur WAPI.");
  }
  const campaignId = String(request.data?.campaignId || "").trim();
  const action = String(request.data?.action || "").trim();
  const paymentReference = String(request.data?.paymentReference || "").trim().slice(0, 120);
  const reviewNote = String(request.data?.reviewNote || "").trim().slice(0, 300);
  if (!/^[A-Za-z0-9_-]{8,160}$/.test(campaignId) || !["approve", "reject"].includes(action)) {
    throw new HttpsError("invalid-argument", "Décision publicitaire invalide.");
  }
  if (action === "approve" && paymentReference.length < 6) {
    throw new HttpsError("invalid-argument", "Une référence de paiement vérifiée est obligatoire.");
  }
  const campaign = db.collection("adCampaigns").doc(campaignId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(campaign);
    if (!snapshot.exists || snapshot.get("status") !== "pending_payment") {
      throw new HttpsError("failed-precondition", "Cette campagne a déjà été traitée.");
    }
    if (action === "reject") {
      transaction.update(campaign, {
        status: "rejected",
        reviewNote: reviewNote || "Campagne refusée après contrôle WAPI Ads.",
        reviewedBy: request.auth!.uid,
        reviewedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }
    const days = Math.max(1, Math.min(90, Number(snapshot.get("days") || 1)));
    const targetImpressions = Math.max(0, Number(snapshot.get("targetImpressions") || 0));
    if (targetImpressions > 0 && Number(snapshot.get("impressionCount") || 0) >= targetImpressions) {
      throw new HttpsError("failed-precondition", "Le quota de diffusion est déjà atteint.");
    }
    const startAtMillis = Date.now();
    transaction.update(campaign, {
      status: "active",
      paymentStatus: "verified",
      paymentReference,
      reviewedBy: request.auth!.uid,
      reviewedAt: FieldValue.serverTimestamp(),
      startAt: Timestamp.fromMillis(startAtMillis),
      endAt: Timestamp.fromMillis(startAtMillis + days * 24 * 60 * 60 * 1_000),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true, status: action === "approve" ? "active" : "rejected" };
});

/** Business owners may pause, resume or end delivery, but never approve payment. */
export const updateAdCampaignDelivery = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const campaignId = String(request.data?.campaignId || "").trim();
  const action = String(request.data?.action || "").trim();
  if (!/^[A-Za-z0-9_-]{8,160}$/.test(campaignId) || !["pause", "resume", "complete"].includes(action)) {
    throw new HttpsError("invalid-argument", "Action de diffusion invalide.");
  }
  const campaign = db.collection("adCampaigns").doc(campaignId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(campaign);
    if (!snapshot.exists || snapshot.get("ownerId") !== request.auth!.uid) throw new HttpsError("permission-denied", "Campagne inaccessible.");
    const current = String(snapshot.get("status") || "");
    const targetImpressions = Math.max(0, Number(snapshot.get("targetImpressions") || 0));
    const quotaAvailable = targetImpressions <= 0 || Number(snapshot.get("impressionCount") || 0) < targetImpressions;
    const next = action === "pause" && current === "active" ? "paused"
      : action === "resume" && current === "paused" && adMillis(snapshot.get("endAt")) > Date.now() && quotaAvailable ? "active"
        : action === "complete" && ["active", "paused"].includes(current) ? "completed" : "";
    if (!next) throw new HttpsError("failed-precondition", "Cette transition de campagne n’est pas autorisée.");
    transaction.update(campaign, { status: next, updatedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

/**
 * Founder-only operational dashboard.
 *
 * The mobile client never gets broad Firestore read access. This endpoint
 * performs the aggregates with Admin SDK after checking the authenticated
 * founder phone number, and returns only counters that are actually present
 * in WAPI data. Store downloads are intentionally reported as a separate
 * integration state: Play Console and App Store Connect must be connected
 * before those numbers can be called real.
 */
export const getFounderDashboard = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const phone = String(request.auth.token.phone_number || "").replace(/\D/g, "");
  if (phone !== "242065465808" && phone !== "065465808") {
    throw new HttpsError("permission-denied", "Espace fondateur réservé au compte autorisé.");
  }

  const count = async (collection: string) => (await db.collection(collection).count().get()).data().count;
  const [
    users, pages, stories, channels, liveSessions, radioEpisodes, installations, stores, paymentAggregate, invoiceAggregate,
    campaigns, activeCampaigns, pendingCampaigns, pendingReviewSnapshot, adImpressions, adClicks,
  ] = await Promise.all([
    count("users"),
    count("businessPages"),
    count("stories"),
    count("channels"),
    db.collection("liveSessions").where("status", "==", "live").count().get().then((result) => result.data().count),
    count("radioEpisodes"),
    db.collectionGroup("devices").where("enabled", "==", true).count().get().then((result) => result.data().count),
    db.collection("platformConfig").doc("stores").get(),
    db.collection("paymentNotifications").where("status", "==", "paid").aggregate({
      paidOrders: AggregateField.count(),
      paidRevenue: AggregateField.sum("amount"),
    }).get(),
    // Invoices are intentionally kept apart from paid revenue: an issued
    // invoice is a receivable, not cash that WAPI has collected.
    db.collection("marketplaceInvoices").where("status", "==", "issued").aggregate({
      issuedInvoices: AggregateField.count(),
      outstandingReceivables: AggregateField.sum("balanceMinor"),
    }).get(),
    count("adCampaigns"),
    db.collection("adCampaigns").where("status", "==", "active").count().get().then((result) => result.data().count),
    db.collection("adCampaigns").where("status", "==", "pending_payment").count().get().then((result) => result.data().count),
    db.collection("adCampaigns").where("status", "==", "pending_payment").limit(25).get(),
    db.collection("adEvents").where("type", "==", "impression").count().get().then((result) => result.data().count),
    db.collection("adEvents").where("type", "==", "click").count().get().then((result) => result.data().count),
  ]);

  const paymentData = paymentAggregate.data();
  const invoiceData = invoiceAggregate.data();
  const paidRevenue = Math.max(0, Number(paymentData.paidRevenue || 0));
  const paidOrders = Math.max(0, Number(paymentData.paidOrders || 0));
  const issuedInvoices = Math.max(0, Number(invoiceData.issuedInvoices || 0));
  const outstandingReceivables = Math.max(0, Number(invoiceData.outstandingReceivables || 0));
  const storeData = stores.data() || {};
  const playStoreConnected = storeData.playStore === true;
  const appStoreConnected = storeData.appStore === true;
  const officialDownloadCount = (connected: boolean, value: unknown) => {
    const parsed = Number(value);
    return connected && Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
  };
  const playDownloads = officialDownloadCount(playStoreConnected, storeData.playDownloads);
  const appStoreDownloads = officialDownloadCount(appStoreConnected, storeData.appStoreDownloads);
  return {
    users,
    businessPages: pages,
    stories,
    channels,
    activeLives: liveSessions,
    radioEpisodes,
    activeInstallations: installations,
    paidRevenue,
    paidOrders,
    billing: {
      issuedInvoices,
      outstandingReceivables,
      currency: "XAF",
      note: "Créances émises par les comptes Business : elles ne sont pas comptées dans le CA encaissé.",
    },
    ads: {
      algorithm: { name: ELEPHANT_ALGORITHM_NAME, version: ELEPHANT_ALGORITHM_VERSION },
      campaigns,
      activeCampaigns,
      pendingCampaigns,
      impressions: adImpressions,
      clicks: adClicks,
    },
    pendingAdReviews: pendingReviewSnapshot.docs.map((document) => ({
      id: document.id,
      pageName: String(document.get("pageName") || "Business WAPI").slice(0, 120),
      title: String(document.get("title") || "Campagne WAPI").slice(0, 120),
      creative: String(document.get("creative") || "").slice(0, 240),
      city: String(document.get("city") || "").slice(0, 80),
      countryCode: String(document.get("countryCode") || "").slice(0, 2),
      totalBudget: Math.max(0, Number(document.get("totalBudget") || 0)),
      deliveryMode: String(document.get("deliveryMode") || "budget"),
      targetImpressions: Math.max(0, Number(document.get("targetImpressions") || 0)),
      days: Math.max(1, Number(document.get("days") || 1)),
      createdAt: adMillis(document.get("createdAt")),
    })),
    currency: "XAF",
    storeIntegrations: {
      playStore: playStoreConnected,
      appStore: appStoreConnected,
      playDownloads,
      appStoreDownloads,
      totalDownloads: playDownloads === null && appStoreDownloads === null ? null : (playDownloads || 0) + (appStoreDownloads || 0),
      configured: playStoreConnected || appStoreConnected,
    },
    generatedAt: Date.now(),
  };
});

function kingQiRoomCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 6 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}

async function kingQiIdentity(uid: string) {
  const snapshot = await db.collection("users").doc(uid).get();
  const value = snapshot.data() || {};
  return {
    displayName: String(value.displayName || value.name || value.phoneNumber || "Joueur WAPI").slice(0, 60),
    photoUrl: String(value.photoUrl || "").slice(0, 1_500),
    country: String(value.countryCode || value.country || "CG").slice(0, 8).toUpperCase(),
  };
}

/** Promotional King QI credits are non-withdrawable and have no cash value. */
export const kingQiGetProfile = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const identity = await kingQiIdentity(uid);
  const wallet = db.collection("kingQiWallets").doc(uid);
  const profile = db.collection("kingQiProfiles").doc(uid);
  await db.runTransaction(async (transaction) => {
    const [walletSnapshot, profileSnapshot] = await Promise.all([transaction.get(wallet), transaction.get(profile)]);
    if (!walletSnapshot.exists) transaction.set(wallet, { credits: 100, trophies: 0, promoGranted: true, updatedAt: FieldValue.serverTimestamp() });
    transaction.set(profile, {
      ...identity,
      trophies: Number(profileSnapshot.get("trophies") || 0),
      victories: Number(profileSnapshot.get("victories") || 0),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
  const [walletSnapshot, profileSnapshot, leaders] = await Promise.all([
    wallet.get(), profile.get(), db.collection("kingQiProfiles").orderBy("trophies", "desc").limit(20).get(),
  ]);
  return {
    credits: Number(walletSnapshot.get("credits") || 0),
    trophies: Number(profileSnapshot.get("trophies") || 0),
    victories: Number(profileSnapshot.get("victories") || 0),
    gameId: "king-qi",
    playerProfile: {
      gameId: "king-qi",
      displayName: identity.displayName,
      photoUrl: identity.photoUrl,
      country: identity.country,
      trophies: Number(profileSnapshot.get("trophies") || 0),
      victories: Number(profileSnapshot.get("victories") || 0),
    },
    leaderboard: leaders.docs.map((document, rank) => ({ rank: rank + 1, uid: document.id, ...document.data() })),
  };
});

/** One persistent player card per WAPI game. Stats remain server-owned. */
export const getGameProfile = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const gameId = String(request.data?.gameId || "").trim().toLowerCase();
  if (!gameIds.has(gameId)) throw new HttpsError("invalid-argument", "Jeu WAPI inconnu.");
  const uid = String(request.data?.uid || request.auth.uid);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(uid)) throw new HttpsError("invalid-argument", "Joueur invalide.");
  const [identity, snapshot] = await Promise.all([gamePlayerIdentity(uid), db.collection("gameProfiles").doc(`${uid}_${gameId}`).get()]);
  return { gameId, profile: publicPlayer(uid, gameId, identity, snapshot.data() || {}) };
});

export const saveGameProfile = onCall({ memory: "256MiB", timeoutSeconds: 60 }, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const gameId = String(request.data?.gameId || "");
  if (!gameIds.has(gameId)) throw new HttpsError("invalid-argument", "Jeu WAPI inconnu.");
  let customization;
  try { customization = playerCustomization(request.data || {}); }
  catch { throw new HttpsError("invalid-argument", "Vérifiez votre pseudo et votre avatar."); }
  const ref = db.collection("gameProfiles").doc(`${uid}_${gameId}`);
  const existing = await ref.get();
  let customPhotoUrl = String(existing.get("customPhotoUrl") || "");
  if (request.data?.photoBase64) {
    let photo;
    try { photo = decodeGroupPhotoBase64(request.data.photoBase64); }
    catch { throw new HttpsError("invalid-argument", "Choisissez une photo JPEG, PNG ou WebP de moins de 5 Mo."); }
    if (!photo) throw new HttpsError("invalid-argument", "Photo vide.");
    const bucket = getStorage().bucket();
    const path = `profiles/${uid}/game-${gameId}-${randomUUID()}.${photo.extension}`;
    const token = randomUUID();
    await bucket.file(path).save(photo.bytes, { resumable: false, metadata: { contentType: photo.contentType,
      cacheControl: "public,max-age=31536000,immutable", metadata: { firebaseStorageDownloadTokens: token } } });
    customPhotoUrl = `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(bucket.name)}/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
  }
  if (customization.avatarMode === "photo" && !customPhotoUrl) throw new HttpsError("invalid-argument", "Choisissez une photo.");
  await ref.set({ uid, gameId, ...customization, customPhotoUrl, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  const [identity, saved] = await Promise.all([gamePlayerIdentity(uid), ref.get()]);
  return { profile: publicPlayer(uid, gameId, identity, saved.data() || {}) };
});

async function gamePlayerIdentity(uid: string) {
  const identity = (await db.collection("users").doc(uid).get()).data() || {};
  // A public game profile must never fall back to a private phone number.
  return { displayName: String(identity.displayName || identity.name || "Joueur WAPI").slice(0, 60),
    photoUrl: String(identity.photoUrl || "").slice(0, 1500), country: String(identity.countryCode || identity.country || "").slice(0, 8) };
}

async function poolPlayerIdentity(uid: string) {
  const [identity, snapshot] = await Promise.all([gamePlayerIdentity(uid), db.collection("gameProfiles").doc(`${uid}_billard`).get()]);
  return publicPlayer(uid, "billard", identity, snapshot.data() || {});
}

export const kingQiCreateTournament = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const entryCredits = Number(request.data?.entryCredits || 0);
  const maxPlayers = Number(request.data?.maxPlayers || 4);
  const visibility = request.data?.visibility === "live" ? "live" : "private";
  if (!kingQiCreditChoices.has(entryCredits)) throw new HttpsError("invalid-argument", "Choisissez une mise de crédits autorisée.");
  if (!Number.isInteger(maxPlayers) || maxPlayers < 2 || maxPlayers > 8) throw new HttpsError("invalid-argument", "Un tournoi accepte de 2 à 8 joueurs.");
  const identity = await kingQiIdentity(uid);
  const room = db.collection("kingQiRooms").doc();
  const wallet = db.collection("kingQiWallets").doc(uid);
  const shuffled = [...kingQiQuestions].sort(() => Math.random() - .5).slice(0, 15).map((question) => question.id);
  let code = kingQiRoomCode();
  while (!(await db.collection("kingQiRooms").where("code", "==", code).limit(1).get()).empty) code = kingQiRoomCode();
  await db.runTransaction(async (transaction) => {
    const walletSnapshot = await transaction.get(wallet);
    const credits = walletSnapshot.exists ? Number(walletSnapshot.get("credits") || 0) : 100;
    if (credits < entryCredits) throw new HttpsError("failed-precondition", "Crédits King QI insuffisants.");
    transaction.set(wallet, { credits: credits - entryCredits, trophies: Number(walletSnapshot.get("trophies") || 0), promoGranted: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.set(room, {
      code, hostId: uid, gameId: "king-qi", cupName: "Coupe King QI", status: "waiting", visibility, entryCredits, potCredits: entryCredits,
      maxPlayers, playerIds: [uid], playerNames: { [uid]: identity.displayName }, playerPhotos: { [uid]: identity.photoUrl },
      playerCountries: { [uid]: identity.country }, scores: { [uid]: 0 }, questionIds: shuffled,
      playerProfiles: { [uid]: { displayName: identity.displayName, photoUrl: identity.photoUrl, country: identity.country } },
      currentIndex: -1, answeredIds: [], createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      currencyMode: "PROMO_CREDITS_NON_WITHDRAWABLE", complianceLocked: true,
    });
  });
  return { roomId: room.id, code };
});

export const kingQiJoinTournament = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const code = String(request.data?.code || "").trim().toUpperCase();
  if (!/^[A-Z2-9]{6}$/.test(code)) throw new HttpsError("invalid-argument", "Code King QI invalide.");
  const query = await db.collection("kingQiRooms").where("code", "==", code).limit(1).get();
  if (query.empty) throw new HttpsError("not-found", "Tournoi King QI introuvable.");
  const room = query.docs[0].ref;
  const wallet = db.collection("kingQiWallets").doc(uid);
  const identity = await kingQiIdentity(uid);
  await db.runTransaction(async (transaction) => {
    const [roomSnapshot, walletSnapshot] = await Promise.all([transaction.get(room), transaction.get(wallet)]);
    const value = roomSnapshot.data() || {};
    const playerIds = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    if (playerIds.includes(uid)) return;
    if (value.status !== "waiting") throw new HttpsError("failed-precondition", "Ce tournoi a déjà commencé.");
    if (playerIds.length >= Number(value.maxPlayers || 4)) throw new HttpsError("resource-exhausted", "Ce tournoi est complet.");
    const entryCredits = Number(value.entryCredits || 0);
    const credits = walletSnapshot.exists ? Number(walletSnapshot.get("credits") || 0) : 100;
    if (credits < entryCredits) throw new HttpsError("failed-precondition", "Crédits King QI insuffisants.");
    transaction.set(wallet, { credits: credits - entryCredits, trophies: Number(walletSnapshot.get("trophies") || 0), promoGranted: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.update(room, {
      playerIds: [...playerIds, uid],
      playerNames: { ...(value.playerNames || {}), [uid]: identity.displayName },
      playerPhotos: { ...(value.playerPhotos || {}), [uid]: identity.photoUrl },
      playerCountries: { ...(value.playerCountries || {}), [uid]: identity.country },
      playerProfiles: { ...(value.playerProfiles || {}), [uid]: { displayName: identity.displayName, photoUrl: identity.photoUrl, country: identity.country } },
      scores: { ...(value.scores || {}), [uid]: 0 },
      potCredits: Number(value.potCredits || 0) + entryCredits,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { roomId: room.id };
});

export const kingQiStartTournament = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const roomId = String(request.data?.roomId || "").trim();
  const room = db.collection("kingQiRooms").doc(roomId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    const playerIds = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    if (!snapshot.exists) throw new HttpsError("not-found", "Tournoi introuvable.");
    if (value.hostId !== request.auth!.uid) throw new HttpsError("permission-denied", "Seul l'organisateur peut démarrer.");
    if (value.status !== "waiting") throw new HttpsError("failed-precondition", "Le tournoi a déjà commencé.");
    if (playerIds.length < 2) throw new HttpsError("failed-precondition", "Invitez au moins un autre joueur.");
    const first = kingQiQuestionById((value.questionIds || [])[0]);
    if (!first) throw new HttpsError("internal", "Question King QI indisponible.");
    transaction.update(room, { status: "playing", currentIndex: 0, currentQuestion: kingQiPublicQuestion(first, 0), answeredIds: [], roundStartedAt: FieldValue.serverTimestamp(), roundDeadline: new Date(Date.now() + 20_000), updatedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

/** Arms the creator's next WAPI WebRTC live with a spectator-safe King QI overlay. */
export const kingQiPrepareLive = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const roomId = String(request.data?.roomId || "").trim();
  const room = await db.collection("kingQiRooms").doc(roomId).get();
  if (!room.exists) throw new HttpsError("not-found", "Tournoi King QI introuvable.");
  if (room.get("hostId") !== request.auth.uid) throw new HttpsError("permission-denied", "Seul l'organisateur peut associer ce tournoi au direct.");
  if (!["waiting", "playing"].includes(String(room.get("status") || ""))) throw new HttpsError("failed-precondition", "Ce tournoi est terminé.");
  await db.doc(`users/${request.auth.uid}/liveState/current`).set({ kingQiRoomId: roomId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  return { ok: true, roomId };
});

export const kingQiSubmitAnswer = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const roomId = String(request.data?.roomId || "").trim();
  const optionIndex = Number(request.data?.optionIndex);
  if (!Number.isInteger(optionIndex) || optionIndex < 0 || optionIndex > 3) throw new HttpsError("invalid-argument", "Réponse invalide.");
  const room = db.collection("kingQiRooms").doc(roomId);
  let result = { correct: false, correctIndex: -1, points: 0 };
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    const playerIds = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    if (!snapshot.exists || value.status !== "playing") throw new HttpsError("failed-precondition", "Aucune manche King QI active.");
    if (!playerIds.includes(uid)) throw new HttpsError("permission-denied", "Vous ne participez pas à ce tournoi.");
    const index = Number(value.currentIndex || 0);
    const answer = room.collection("answers").doc(`${index}_${uid}`);
    const answerSnapshot = await transaction.get(answer);
    if (answerSnapshot.exists) throw new HttpsError("already-exists", "Votre réponse est déjà enregistrée.");
    const question = kingQiQuestionById((value.questionIds || [])[index]);
    if (!question) throw new HttpsError("internal", "Question King QI indisponible.");
    const deadline = value.roundDeadline?.toMillis?.() || 0;
    const remaining = Math.max(0, deadline - Date.now());
    const correct = optionIndex === question.correctIndex && remaining > 0;
    const points = correct ? 500 + Math.floor(remaining / 25) : 0;
    const scores = { ...(value.scores || {}) } as Record<string, number>;
    scores[uid] = Number(scores[uid] || 0) + points;
    const answeredIds = [...new Set([...(Array.isArray(value.answeredIds) ? value.answeredIds.map(String) : []), uid])];
    transaction.set(answer, { uid, index, optionIndex, correct, points, answeredAt: FieldValue.serverTimestamp() });
    transaction.update(room, { scores, answeredIds, updatedAt: FieldValue.serverTimestamp() });
    result = { correct, correctIndex: question.correctIndex, points };
  });
  return result;
});

export const kingQiAdvanceTournament = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const uid = request.auth.uid;
  const roomId = String(request.data?.roomId || "").trim();
  const room = db.collection("kingQiRooms").doc(roomId);
  let finished = false;
  let winners: string[] = [];
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    const playerIds = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    if (!snapshot.exists || value.status !== "playing") throw new HttpsError("failed-precondition", "Aucun tournoi actif.");
    if (!playerIds.includes(uid)) throw new HttpsError("permission-denied", "Accès refusé.");
    const answeredIds = Array.isArray(value.answeredIds) ? value.answeredIds.map(String) : [];
    const deadline = value.roundDeadline?.toMillis?.() || 0;
    if (answeredIds.length < playerIds.length && Date.now() < deadline) throw new HttpsError("failed-precondition", "La manche est toujours en cours.");
    const nextIndex = Number(value.currentIndex || 0) + 1;
    const questionIds = Array.isArray(value.questionIds) ? value.questionIds : [];
    if (nextIndex < questionIds.length) {
      const next = kingQiQuestionById(questionIds[nextIndex]);
      if (!next) throw new HttpsError("internal", "Question King QI indisponible.");
      transaction.update(room, { currentIndex: nextIndex, currentQuestion: kingQiPublicQuestion(next, nextIndex), answeredIds: [], roundStartedAt: FieldValue.serverTimestamp(), roundDeadline: new Date(Date.now() + 20_000), updatedAt: FieldValue.serverTimestamp() });
      return;
    }
    const scores = (value.scores || {}) as Record<string, number>;
    const best = Math.max(...playerIds.map((playerId) => Number(scores[playerId] || 0)));
    winners = playerIds.filter((playerId) => Number(scores[playerId] || 0) === best);
    const walletRefs = winners.map((winner) => db.collection("kingQiWallets").doc(winner));
    const profileRefs = winners.map((winner) => db.collection("kingQiProfiles").doc(winner));
    const gameProfileRefs = winners.map((winner) => db.collection("gameProfiles").doc(`${winner}_king-qi`));
    const walletSnapshots: DocumentSnapshot[] = [];
    const profileSnapshots: DocumentSnapshot[] = [];
    const gameProfileSnapshots: DocumentSnapshot[] = [];
    for (const ref of walletRefs) walletSnapshots.push(await transaction.get(ref));
    for (const ref of profileRefs) profileSnapshots.push(await transaction.get(ref));
    for (const ref of gameProfileRefs) gameProfileSnapshots.push(await transaction.get(ref));
    const pot = Number(value.potCredits || 0);
    const prize = winners.length ? Math.floor(pot / winners.length) : 0;
    winners.forEach((winner, position) => {
      const walletSnapshot = walletSnapshots[position];
      const profileSnapshot = profileSnapshots[position];
      const gameProfileSnapshot = gameProfileSnapshots[position];
      transaction.set(walletRefs[position], { credits: Number(walletSnapshot.get("credits") || 0) + prize, trophies: Number(walletSnapshot.get("trophies") || 0) + 1, promoGranted: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      transaction.set(profileRefs[position], { trophies: Number(profileSnapshot.get("trophies") || 0) + 1, victories: Number(profileSnapshot.get("victories") || 0) + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      transaction.set(gameProfileRefs[position], {
        uid: winner,
        gameId: "king-qi",
        displayName: String((value.playerNames || {})[winner] || "Joueur WAPI"),
        photoUrl: String((value.playerPhotos || {})[winner] || ""),
        country: String((value.playerCountries || {})[winner] || "CG"),
        victories: Number(gameProfileSnapshot.get("victories") || 0) + 1,
        defeats: Number(gameProfileSnapshot.get("defeats") || 0),
        trophies: Number(gameProfileSnapshot.get("trophies") || 0) + 1,
        rating: Number(gameProfileSnapshot.get("rating") || 1000) + 25,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });
    transaction.update(room, { status: "finished", winners, prizePerWinner: prize, cupAwarded: true, cupName: "Coupe King QI", cupId: `king-qi-${room.id}`, finishedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
    finished = true;
  });
  return { finished, winners };
});

function businessSaleRoomCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 7 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}

/** Creates a timed social-commerce event. "Private sale" describes the offer,
 * not its discoverability: public rooms are visible to every signed-in WAPI account. */
export const createBusinessSaleRoom = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const ownerId = request.auth.uid;
  const pageId = String(request.data?.pageId || "").trim();
  const rawTitle = String(request.data?.title || "").trim();
  const description = String(request.data?.description || "").trim();
  const visibility = request.data?.visibility === "contacts" ? "contacts" : "public";
  const durationMinutes = Number(request.data?.durationMinutes || 60);
  const startNow = request.data?.startNow !== false;
  const dealIds = Array.isArray(request.data?.dealIds)
    ? [...new Set<string>((request.data.dealIds as unknown[]).map((value) => String(value).trim()).filter(Boolean))]
    : [];
  if (!pageId) throw new HttpsError("invalid-argument", "Sélectionnez une page Business.");
  if (rawTitle.length > 100 || description.length > 500) throw new HttpsError("invalid-argument", "Le titre ou la présentation est trop long.");
  if (!Number.isInteger(durationMinutes) || durationMinutes < 15 || durationMinutes > 10_080) throw new HttpsError("invalid-argument", "La vente doit durer entre 15 minutes et 7 jours.");
  if (dealIds.length < 1 || dealIds.length > 20) throw new HttpsError("invalid-argument", "Ajoutez entre 1 et 20 produits à la vente.");

  const page = await db.collection("businessPages").doc(pageId).get();
  if (!page.exists || page.get("ownerId") !== ownerId) throw new HttpsError("permission-denied", "Cette page Business ne vous appartient pas.");
  const deals = await db.getAll(...dealIds.map((dealId) => db.collection("businessDeals").doc(dealId)));
  if (deals.some((deal) => !deal.exists || deal.get("ownerId") !== ownerId || deal.get("pageId") !== pageId || !["active", "paused"].includes(String(deal.get("status") || "")))) {
    throw new HttpsError("failed-precondition", "Un produit n'est plus disponible dans votre catalogue.");
  }
  const audienceIds = visibility === "contacts" ? await liveAudienceIds(ownerId) : [];
  const room = db.collection("businessSaleRooms").doc();
  const title = rawTitle || `Vente privée de ${String(page.get("name") || "WAPI Business")}`;
  const now = Date.now();
  await room.set({
    ownerId,
    pageId,
    pageName: String(page.get("name") || "WAPI Business"),
    pageCategory: String(page.get("category") || "Commerce"),
    pageCity: String(page.get("city") || ""),
    title,
    description,
    visibility,
    audienceIds,
    dealIds,
    status: startNow ? "live" : "scheduled",
    code: businessSaleRoomCode(),
    viewerCount: 0,
    uniqueViewerCount: 0,
    reservationCount: 0,
    startsAt: new Date(now),
    endsAt: new Date(now + durationMinutes * 60_000),
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  return { roomId: room.id, status: startNow ? "live" : "scheduled" };
});

export const listVisibleBusinessSaleRooms = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const snapshot = await db.collection("businessSaleRooms").where("status", "in", ["scheduled", "live"]).limit(100).get();
  const visible = snapshot.docs.filter((room) => {
    const value = room.data();
    if (value.endsAt?.toMillis?.() <= Date.now()) return false;
    return value.ownerId === userId || value.visibility === "public" || (value.visibility === "contacts" && Array.isArray(value.audienceIds) && value.audienceIds.includes(userId));
  });
  const allDealIds = [...new Set(visible.flatMap((room) => Array.isArray(room.get("dealIds")) ? (room.get("dealIds") as unknown[]).map(String) : []))];
  const dealSnapshots = allDealIds.length ? await db.getAll(...allDealIds.map((dealId) => db.collection("businessDeals").doc(dealId))) : [];
  const deals = new Map(dealSnapshots.filter((deal) => deal.exists).map((deal) => [deal.id, deal.data() || {}]));
  const rooms = visible.map((room) => {
    const value = room.data();
    const products = (Array.isArray(value.dealIds) ? value.dealIds : []).map(String).flatMap((dealId) => {
      const deal = deals.get(dealId);
      if (!deal) return [];
      return [{
        id: dealId,
        title: String(deal.title || "Produit WAPI"),
        description: String(deal.description || ""),
        price: Number(deal.dealPrice || 0),
        originalPrice: Number(deal.originalPrice || 0),
        stock: Number(deal.stock || 0),
        sold: Number(deal.sold || 0),
        status: String(deal.status || "active"),
      }];
    });
    return {
      id: room.id,
      ownerId: value.ownerId,
      pageId: value.pageId,
      pageName: value.pageName,
      pageCategory: value.pageCategory,
      pageCity: value.pageCity,
      title: value.title,
      description: value.description,
      visibility: value.visibility,
      status: value.status,
      code: value.code,
      viewerCount: Number(value.viewerCount || 0),
      reservationCount: Number(value.reservationCount || 0),
      startsAtMillis: value.startsAt?.toMillis?.() || 0,
      endsAtMillis: value.endsAt?.toMillis?.() || 0,
      products,
    };
  }).sort((left, right) => {
    if (left.status !== right.status) return left.status === "live" ? -1 : 1;
    return right.startsAtMillis - left.startsAtMillis;
  });
  return { rooms, worldVisible: true };
});

export const joinBusinessSaleRoom = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const roomId = String(request.data?.roomId || "").trim();
  const room = db.collection("businessSaleRooms").doc(roomId);
  const viewer = room.collection("viewers").doc(userId);
  await db.runTransaction(async (transaction) => {
    const [roomSnapshot, viewerSnapshot] = await Promise.all([transaction.get(room), transaction.get(viewer)]);
    const value = roomSnapshot.data() || {};
    if (!roomSnapshot.exists || value.status !== "live" || value.endsAt?.toMillis?.() <= Date.now()) throw new HttpsError("failed-precondition", "Cette vente n'est plus ouverte.");
    const mayJoin = value.ownerId === userId || value.visibility === "public" || (value.visibility === "contacts" && Array.isArray(value.audienceIds) && value.audienceIds.includes(userId));
    if (!mayJoin) throw new HttpsError("permission-denied", "Cette vente est réservée aux contacts de la boutique.");
    if (!viewerSnapshot.exists) {
      transaction.set(viewer, { userId, joinedAt: FieldValue.serverTimestamp(), lastSeenAt: FieldValue.serverTimestamp() });
      transaction.update(room, { viewerCount: Number(value.viewerCount || 0) + 1, uniqueViewerCount: Number(value.uniqueViewerCount || 0) + 1, updatedAt: FieldValue.serverTimestamp() });
    } else transaction.set(viewer, { lastSeenAt: FieldValue.serverTimestamp() }, { merge: true });
  });
  return { ok: true };
});

export const reserveBusinessSaleProduct = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const buyerId = request.auth.uid;
  const roomId = String(request.data?.roomId || "").trim();
  const dealId = String(request.data?.dealId || "").trim();
  const quantity = Number(request.data?.quantity || 1);
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10) throw new HttpsError("invalid-argument", "Quantité invalide.");
  const buyer = await liveDisplayName(buyerId);
  const room = db.collection("businessSaleRooms").doc(roomId);
  const deal = db.collection("businessDeals").doc(dealId);
  const order = db.collection("businessSaleOrders").doc();
  let saleOwnerId = "";
  let saleProductTitle = "Produit WAPI";
  let saleAmount = 0;
  await db.runTransaction(async (transaction) => {
    const [roomSnapshot, dealSnapshot] = await Promise.all([transaction.get(room), transaction.get(deal)]);
    const roomValue = roomSnapshot.data() || {};
    const dealValue = dealSnapshot.data() || {};
    if (!roomSnapshot.exists || roomValue.status !== "live" || roomValue.endsAt?.toMillis?.() <= Date.now()) throw new HttpsError("failed-precondition", "Cette vente est terminée.");
    if (!(Array.isArray(roomValue.dealIds) && roomValue.dealIds.includes(dealId))) throw new HttpsError("permission-denied", "Ce produit n'appartient pas à cette vente.");
    if (!dealSnapshot.exists || dealValue.status !== "active") throw new HttpsError("failed-precondition", "Ce produit n'est pas disponible.");
    const stock = Number(dealValue.stock || 0);
    const sold = Number(dealValue.sold || 0);
    if (sold + quantity > stock) throw new HttpsError("resource-exhausted", "Stock insuffisant.");
    const amount = Number(dealValue.dealPrice || 0) * quantity;
    saleOwnerId = String(roomValue.ownerId || "");
    saleProductTitle = String(dealValue.title || "Produit WAPI");
    saleAmount = amount;
    transaction.update(deal, { sold: sold + quantity, updatedAt: FieldValue.serverTimestamp() });
    transaction.update(room, { reservationCount: Number(roomValue.reservationCount || 0) + quantity, updatedAt: FieldValue.serverTimestamp() });
    transaction.set(order, {
      roomId,
      dealId,
      pageId: roomValue.pageId,
      ownerId: roomValue.ownerId,
      buyerId,
      buyerName: buyer.displayName,
      productTitle: String(dealValue.title || "Produit WAPI"),
      quantity,
      unitPrice: Number(dealValue.dealPrice || 0),
      amount,
      currency: "XAF",
      status: "reserved",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  const devices = await pushDevices([saleOwnerId]);
  if (devices.length) await sendInBatches(devices, {
    data: {
      type: "business_sale",
      title: "Nouvelle réservation Business",
      body: `${buyer.displayName} réserve ${quantity} × ${saleProductTitle} · ${saleAmount} FCFA`,
      roomId,
      orderId: order.id,
      deepLink: `whappy://business/sales/${roomId}`,
    },
    android: { priority: "high", ttl: 86_400_000, collapseKey: `business-sale-${order.id}` },
  });
  return { ok: true, orderId: order.id };
});

export const endBusinessSaleRoom = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const roomId = String(request.data?.roomId || "").trim();
  const room = db.collection("businessSaleRooms").doc(roomId);
  const snapshot = await room.get();
  if (!snapshot.exists) return { ok: true };
  if (snapshot.get("ownerId") !== request.auth.uid) throw new HttpsError("permission-denied", "Seule la boutique peut terminer cette vente.");
  await room.set({ status: "ended", endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  return { ok: true };
});

const poolAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function poolRoomCode() {
  return Array.from({ length: 6 }, () => poolAlphabet[Math.floor(Math.random() * poolAlphabet.length)]).join("");
}

function poolRoomId(value: unknown) {
  const roomId = String(value || "").trim().toUpperCase();
  if (!/^[A-Z2-9]{6}$/.test(roomId)) throw new HttpsError("invalid-argument", "Code de table WAPI invalide.");
  return roomId;
}

/** Creates a server-owned pool table. Mobile clients never author the initial
 * board, turn, groups or anti-cheat revision. */
export const createPoolMatch = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const profile = await poolPlayerIdentity(userId);
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const roomId = poolRoomCode();
    const room = db.collection("gameRooms").doc(roomId);
    if ((await room.get()).exists) continue;
    await room.create({
      game: "pool",
      engineVersion: 2,
      authority: "wapi-server",
      status: "waiting",
      visibility: request.data?.visibility === "private" ? "private" : "wapi",
      hostId: userId,
      playerIds: [userId],
      playerNames: [profile.displayName], playerProfiles: { [userId]: profile },
      groups: { [userId]: "open" },
      turnUid: userId,
      winnerUid: "",
      ballInHandUid: "",
      balls: initialPoolBalls(),
      ballStateRevision: 0,
      shotRevision: 0,
      lastAction: `Table créée · partagez le code ${roomId}.`,
      turnDeadlineMs: Date.now() + 45_000,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { roomId, status: "waiting", reconnected: false };
  }
  throw new HttpsError("resource-exhausted", "Impossible de réserver une table. Réessayez.");
});

export const joinPoolMatch = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const roomId = poolRoomId(request.data?.roomId);
  const profile = await poolPlayerIdentity(userId);
  const room = db.collection("gameRooms").doc(roomId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    if (!snapshot.exists || value.game !== "pool") throw new HttpsError("not-found", "Cette table n’existe plus.");
    const ids = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    const names = Array.isArray(value.playerNames) ? value.playerNames.map(String) : [];
    if (ids.includes(userId)) return;
    if (value.status !== "waiting" || ids.length !== 1) throw new HttpsError("failed-precondition", "Cette table est déjà complète.");
    transaction.update(room, {
      playerIds: [...ids, userId],
      playerNames: [...names, profile.displayName], playerProfiles: { ...(value.playerProfiles || {}), [userId]: profile },
      groups: { ...(value.groups || {}), [userId]: "open" },
      status: "playing",
      turnDeadlineMs: Date.now() + 45_000,
      lastAction: `${profile.displayName} a rejoint la table · casse au premier joueur.`,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { roomId, status: "playing" };
});

/** Finds a real waiting player or creates a public table. A later invocation
 * by another account joins the same room transactionally. */
export const findPoolMatch = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const waiting = await db.collection("gameRooms")
    .where("game", "==", "pool")
    .where("status", "==", "waiting")
    .limit(12)
    .get();
  const candidate = waiting.docs.find((document) => {
    const value = document.data();
    return value.visibility !== "private" && Array.isArray(value.playerIds) && !value.playerIds.includes(userId);
  });
  if (candidate) {
    const profile = await poolPlayerIdentity(userId);
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(candidate.ref);
      const value = snapshot.data() || {};
      const ids = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
      const names = Array.isArray(value.playerNames) ? value.playerNames.map(String) : [];
      if (!snapshot.exists || value.status !== "waiting" || ids.length !== 1 || ids.includes(userId)) {
        throw new HttpsError("aborted", "Cette table vient d’être prise. Relancez la recherche.");
      }
      transaction.update(candidate.ref, {
        playerIds: [...ids, userId], playerNames: [...names, profile.displayName], playerProfiles: { ...(value.playerProfiles || {}), [userId]: profile },
        groups: { ...(value.groups || {}), [userId]: "open" }, status: "playing",
        turnDeadlineMs: Date.now() + 45_000,
        lastAction: `${profile.displayName} a rejoint la table · casse au premier joueur.`,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return { roomId: candidate.id, status: "playing", matched: true };
  }
  // Keep matchmaking callable independent from callable internals: create a
  // public room directly with the same authoritative schema.
  const profile = await poolPlayerIdentity(userId);
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const roomId = poolRoomCode();
    const room = db.collection("gameRooms").doc(roomId);
    if ((await room.get()).exists) continue;
    await room.create({
      game: "pool", engineVersion: 2, authority: "wapi-server", status: "waiting", visibility: "wapi",
      hostId: userId, playerIds: [userId], playerNames: [profile.displayName], playerProfiles: { [userId]: profile }, groups: { [userId]: "open" },
      turnUid: userId, winnerUid: "", ballInHandUid: "", balls: initialPoolBalls(), ballStateRevision: 0,
      shotRevision: 0, lastAction: "Recherche d’un adversaire WAPI…", turnDeadlineMs: Date.now() + 45_000,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    });
    return { roomId, status: "waiting", matched: false };
  }
  throw new HttpsError("resource-exhausted", "Le matchmaking est momentanément saturé.");
});

export const reconnectPoolMatch = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const snapshots = await db.collection("gameRooms").where("playerIds", "array-contains", request.auth.uid).limit(20).get();
  const active = snapshots.docs
    .filter((document) => ["waiting", "playing"].includes(String(document.get("status") || "")))
    .sort((a, b) => Number(b.get("turnDeadlineMs") || 0) - Number(a.get("turnDeadlineMs") || 0))[0];
  return active ? { roomId: active.id, status: active.get("status"), found: true } : { found: false };
});

export const placePoolCueBall = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const roomId = poolRoomId(request.data?.roomId);
  const x = Number(request.data?.x);
  const y = Number(request.data?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    throw new HttpsError("invalid-argument", "Position de la bille blanche invalide.");
  }
  const room = db.collection("gameRooms").doc(roomId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    if (!snapshot.exists || value.status !== "playing") throw new HttpsError("failed-precondition", "Cette partie n’est plus active.");
    if (value.turnUid !== userId || value.ballInHandUid !== userId) throw new HttpsError("permission-denied", "Vous n’avez pas la bille en main.");
    const balls = sanitizePoolBalls(value.balls);
    if (!validPoolCuePlacement(x, y, balls)) throw new HttpsError("invalid-argument", "La bille blanche chevauche une autre bille.");
    const next = balls.map((ball) => ball.id === 0 ? { ...ball, x, y } : ball);
    transaction.update(room, {
      balls: next,
      ballStateRevision: Number(value.ballStateRevision || 0) + 1,
      ballInHandUid: "",
      turnDeadlineMs: Date.now() + 45_000,
      lastAction: "Bille blanche placée · le tir peut commencer.",
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

export const expirePoolTurn = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const roomId = poolRoomId(request.data?.roomId);
  const room = db.collection("gameRooms").doc(roomId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    if (!snapshot.exists || value.status !== "playing") return;
    if (value.turnUid !== userId) throw new HttpsError("permission-denied", "Ce tour ne vous appartient pas.");
    if (Number(value.turnDeadlineMs || 0) > Date.now()) throw new HttpsError("failed-precondition", "Le chronomètre n’est pas terminé.");
    const ids = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    const opponentUid = ids.find((id: string) => id !== userId);
    if (!opponentUid) throw new HttpsError("failed-precondition", "L’adversaire n’est pas connecté.");
    transaction.update(room, {
      turnUid: opponentUid,
      ballInHandUid: opponentUid,
      turnDeadlineMs: Date.now() + 45_000,
      lastAction: "Temps écoulé · bille en main pour l’adversaire.",
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

export const submitPoolShot = onCall({ timeoutSeconds: 30, memory: "512MiB" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const userId = request.auth.uid;
  const roomId = poolRoomId(request.data?.roomId);
  let shot;
  try { shot = validatePoolShot(request.data); }
  catch { throw new HttpsError("invalid-argument", "Paramètres de tir invalides."); }
  const room = db.collection("gameRooms").doc(roomId);
  let response: Record<string, unknown> = {};
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(room);
    const value = snapshot.data() || {};
    if (!snapshot.exists || value.game !== "pool" || value.status !== "playing") throw new HttpsError("failed-precondition", "Cette partie n’est plus active.");
    if (value.turnUid !== userId) throw new HttpsError("permission-denied", "Ce n’est pas votre tour.");
    if (String(value.ballInHandUid || "")) throw new HttpsError("failed-precondition", "Placez d’abord la bille blanche.");
    if (Number(value.turnDeadlineMs || 0) < Date.now() - 1_500) throw new HttpsError("deadline-exceeded", "Le temps du tir est écoulé.");
    const playerIds = Array.isArray(value.playerIds) ? value.playerIds.map(String) : [];
    const opponentUid = playerIds.find((id: string) => id !== userId);
    if (!opponentUid) throw new HttpsError("failed-precondition", "L’adversaire n’a pas encore rejoint la table.");
    const ballsBefore = sanitizePoolBalls(value.balls);
    const simulation = simulatePoolShot(ballsBefore, shot);
    const groups = value.groups && typeof value.groups === "object" ? value.groups as Record<string, PoolGroup> : {};
    const resolution = resolvePoolShot(ballsBefore, groups[userId] || "open", groups[opponentUid] || "open", simulation);
    const winnerUid = resolution.shooterWon === true ? userId : resolution.shooterWon === false ? opponentUid : "";
    const nextTurn = resolution.keepTurn ? userId : opponentUid;
    const revision = Number(value.shotRevision || 0) + 1;
    const legalPockets = resolution.foul ? 0 : simulation.pocketedIds.filter(id => id !== 8 &&
      ((resolution.shooterGroup === "solids" && id >= 1 && id <= 7) || (resolution.shooterGroup === "stripes" && id >= 9 && id <= 15))).length;
    const run = Number(value.runs?.[userId] || 0) + legalPockets;
    const bestRuns = { ...(value.bestRuns || {}), [userId]: Math.max(Number(value.bestRuns?.[userId] || 0), run) };
    if (winnerUid) {
      const refs = playerIds.map((uid: string) => db.collection("gameProfiles").doc(`${uid}_billard`));
      const profiles = await Promise.all(refs.map(ref => transaction.get(ref)));
      playerIds.forEach((uid: string, index: number) => {
        const award = poolMatchAward(uid === winnerUid, Number(bestRuns[uid] || 0));
        transaction.set(refs[index], { uid, gameId: "billard", matchesPlayed: FieldValue.increment(1),
          victories: FieldValue.increment(award.victories), defeats: FieldValue.increment(award.defeats),
          points: FieldValue.increment(award.points), bestRun: Math.max(Number(profiles[index].get("bestRun") || 0), award.bestRun),
          updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      });
      transaction.create(db.collection("poolResults").doc(roomId), {
        roomId, playerIds, winnerUid, shots: revision, bestRuns, pointsAwarded: 100,
        monetaryValue: 0, completedAt: FieldValue.serverTimestamp(),
      });
    }
    transaction.update(room, {
      runs: { ...(value.runs || {}), [userId]: resolution.keepTurn ? run : 0, ...(resolution.keepTurn ? {} : { [opponentUid]: 0 }) },
      bestRuns,
      authority: "wapi-server",
      shotRevision: revision,
      shotBy: userId,
      shotAngle: shot.angle,
      shotPower: shot.power,
      shotSideSpin: shot.sideSpin,
      shotFollowSpin: shot.followSpin,
      shotStartBalls: ballsBefore,
      balls: simulation.balls,
      ballStateRevision: revision,
      groups: { ...groups, [userId]: resolution.shooterGroup, [opponentUid]: resolution.opponentGroup },
      turnUid: nextTurn,
      winnerUid,
      status: winnerUid ? "finished" : "playing",
      ballInHandUid: resolution.foul && !winnerUid ? opponentUid : "",
      turnDeadlineMs: winnerUid ? 0 : Date.now() + 45_000,
      lastAction: resolution.message,
      lastSimulationFrames: simulation.frames,
      updatedAt: FieldValue.serverTimestamp(),
    });
    response = { ok: true, roomId, revision, nextTurn, winnerUid, foul: resolution.foul, message: resolution.message };
  });
  return response;
});
