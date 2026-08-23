import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import {
  AccessToken,
  DataPacket_Kind,
  RoomServiceClient,
  WebhookReceiver,
} from "livekit-server-sdk";

if (!getApps().length) initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 20, memory: "256MiB" });

const db = getFirestore();
const livekitServerUrl = defineString("WAPI_LIVEKIT_URL", { default: "" });
const livekitApiKey = defineString("WAPI_LIVEKIT_API_KEY", { default: "" });
const livekitApiSecret = defineSecret("WAPI_LIVEKIT_API_SECRET");

type PushDevice = {
  token: string;
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
    devices.set(token, { token, remove: () => document.ref.delete() });
  });
  return [...devices.values()];
}

function staleRegistration(errorCode?: string) {
  return errorCode === "messaging/registration-token-not-registered" || errorCode === "messaging/invalid-registration-token";
}

async function sendInBatches(devices: PushDevice[], message: Omit<MulticastMessage, "tokens">) {
  for (let index = 0; index < devices.length; index += 500) {
    const batch = devices.slice(index, index + 500);
    if (!batch.length) continue;
    const response = await getMessaging().sendEachForMulticast({ ...message, tokens: batch.map((device) => device.token) });
    const stale = response.responses.flatMap((result, responseIndex) =>
      !result.success && staleRegistration(result.error?.code) ? [batch[responseIndex].remove()] : [],
    );
    if (stale.length) await Promise.allSettled(stale);
    if (response.failureCount) logger.warn("Certaines notifications WAPI n’ont pas été livrées", { failures: response.failureCount, staleTokens: stale.length });
  }
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
    },
    android: { priority: "high", ttl: 120_000, collapseKey: `call-${event.params.callId}` },
  });
});

export const getWebRtcIceServers = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  // The production TURN relay is intentionally not fabricated here. Clients
  // receive a valid STUN fallback until coturn is provisioned and its secrets
  // are added to Firebase. This keeps calls usable on compatible networks.
  return {
    iceServers: [
      { urls: ["stun:stun.l.google.com:19302"] },
      { urls: ["stun:stun1.l.google.com:19302"] },
    ],
    turnConfigured: false,
  };
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

function livekitConfig() {
  const serverUrl = livekitServerUrl.value().trim();
  const apiKey = livekitApiKey.value().trim();
  const apiSecret = livekitApiSecret.value().trim();
  if (!serverUrl.startsWith("wss://") || !apiKey || apiSecret.length < 16) {
    throw new HttpsError(
      "failed-precondition",
      "Le serveur Live WAPI n’est pas encore provisionné. Configurez WAPI_LIVEKIT_URL, WAPI_LIVEKIT_API_KEY et WAPI_LIVEKIT_API_SECRET.",
    );
  }
  return { serverUrl, apiKey, apiSecret };
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
  product?: "wapi-live" | "wapi-group-call";
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
  if (value.hostId === userId) return { reference, value, isHost: true };
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

export const createLiveSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  // Validate the media service before persisting a room that nobody can open.
  livekitConfig();
  const hostId = request.auth.uid;
  const title = requiredLiveText(request.data?.title, "Le titre", 3, 120);
  const category = requiredLiveText(request.data?.category || "Discussion", "La catégorie", 2, 60);
  const visibility = String(request.data?.visibility || "public");
  const hostMode = String(request.data?.hostMode || "personal");
  const audioOnly = request.data?.audioOnly === true;
  if (!["public", "contacts", "private"].includes(visibility)) {
    throw new HttpsError("invalid-argument", "Visibilité du direct invalide.");
  }
  if (!["personal", "creator", "business"].includes(hostMode)) {
    throw new HttpsError("invalid-argument", "Mode d’animateur invalide.");
  }

  const current = await db.doc(`users/${hostId}/liveState/current`).get();
  if (["scheduled", "live"].includes(String(current.get("status") || ""))) {
    throw new HttpsError("already-exists", "Un direct WAPI est déjà ouvert sur ce compte.");
  }

  const [host, audienceIds] = await Promise.all([
    liveDisplayName(hostId),
    visibility === "contacts" ? liveAudienceIds(hostId) : Promise.resolve([]),
  ]);
  const live = db.collection("liveSessions").doc();
  const roomName = `wapi-${hostId.slice(0, 12)}-${live.id}`;
  const roomService = livekitRoomService();
  await roomService.createRoom({
    name: roomName,
    emptyTimeout: 300,
    departureTimeout: 25,
    maxParticipants: 500,
    metadata: JSON.stringify({ liveId: live.id, hostId, product: "wapi-live" }),
  });
  const access = await liveAccessToken({
    roomName,
    userId: hostId,
    displayName: host.displayName,
    role: "host",
  });
  try {
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
      streamProvider: "livekit-self-hosted",
      streamRoomId: roomName,
      audioOnly,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    batch.set(db.doc(`users/${hostId}/liveState/current`), {
      liveId: live.id,
      status: "scheduled",
      updatedAt: FieldValue.serverTimestamp(),
    });
    await batch.commit();
  } catch (error) {
    await roomService.deleteRoom(roomName).catch(() => undefined);
    throw error;
  }
  return { liveId: live.id, role: "host", ...access };
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

export const joinLiveSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const liveId = String(request.data?.liveId || "").trim();
  if (!liveId) throw new HttpsError("invalid-argument", "Direct WAPI invalide.");
  const accessControl = await assertLiveAccess(liveId, request.auth.uid);
  const value = accessControl.value;
  const hostId = String(value.hostId || "");
  const isHost = accessControl.isHost;
  const roomName = String(value.streamRoomId || "").trim();
  if (value.streamProvider !== "livekit-self-hosted" || !roomName) {
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
  const access = await liveAccessToken({
    roomName,
    userId: request.auth.uid,
    displayName: profile.displayName,
    role,
  });
  return { liveId, role, ...access };
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
  return { ok: true };
});

export const sendLiveReaction = onCall({ secrets: [livekitApiSecret] }, async (request) => {
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
  const payload = Buffer.from(JSON.stringify({
    type: "reaction",
    reaction,
    senderId: request.auth.uid,
    total,
  }));
  await livekitRoomService().sendData(
    String(access.value.streamRoomId || ""),
    payload,
    DataPacket_Kind.LOSSY,
    { topic: "wapi.reaction" },
  );
  return { ok: true, total };
});

/** Sends a real persisted digital gift. Wearable effects require host opt-in. */
export const sendLiveGift = onCall({ secrets: [livekitApiSecret] }, async (request) => {
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
  const payload = Buffer.from(JSON.stringify({ type: "gift", giftId, ...gift, equipped, senderName: sender.displayName, total }));
  await livekitRoomService().sendData(String(access.value.streamRoomId || ""), payload, DataPacket_Kind.RELIABLE, { topic: "wapi.gift" });
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

export const moderateLiveParticipant = onCall({ secrets: [livekitApiSecret] }, async (request) => {
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
  await livekitRoomService().removeParticipant(
    String(value.streamRoomId || ""),
    targetUserId,
    { revokeTokenTs: BigInt(Math.floor(Date.now() / 1000)) },
  ).catch((error) => logger.warn("Spectateur déjà déconnecté", { liveId, targetUserId, error }));
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

export const setLiveSessionState = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const hostId = request.auth.uid;
  const liveId = String(request.data?.liveId || "").trim();
  const action = String(request.data?.action || "").trim();
  if (!liveId || !["start", "end"].includes(action)) {
    throw new HttpsError("invalid-argument", "État du direct invalide.");
  }
  const live = db.collection("liveSessions").doc(liveId);
  if (action === "start") {
    const pending = await live.get();
    const pendingValue = pending.data();
    if (!pending.exists || !pendingValue || pendingValue.hostId !== hostId) {
      throw new HttpsError("permission-denied", "Seul l’animateur peut contrôler ce direct.");
    }
    try {
      await livekitRoomService().getParticipant(
        String(pendingValue.streamRoomId || ""),
        hostId,
      );
    } catch (_) {
      throw new HttpsError(
        "failed-precondition",
        "La caméra n’est pas encore connectée au serveur Live.",
      );
    }
  }
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
  if (action === "end") {
    const ended = await live.get();
    const roomName = String(ended.get("streamRoomId") || "");
    if (roomName) {
      await livekitRoomService().deleteRoom(roomName)
        .catch((error) => logger.warn("Salle Live déjà fermée", { liveId, error }));
    }
  }
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
    transaction.set(participant, { userId, active, lastSeenAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.update(reference, { participantCount: Math.max(0, current + (active ? 1 : -1)), updatedAt: FieldValue.serverTimestamp() });
  });
}

/** Creates a real multi-party WebRTC room on WAPI's self-hosted SFU. */
export const createGroupCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const groupId = String(request.data?.groupId || "").trim();
  const video = request.data?.video === true;
  if (!groupId) throw new HttpsError("invalid-argument", "Groupe invalide.");
  // Validate media configuration before persisting or notifying anyone.
  livekitConfig();
  const group = await db.collection("groups").doc(groupId).get();
  const groupValue = group.data();
  if (!group.exists || !groupValue) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  const memberIds = Array.isArray(groupValue.memberIds) ? groupValue.memberIds.map(String) : [];
  if (!memberIds.includes(request.auth.uid)) throw new HttpsError("permission-denied", "Vous n’êtes pas membre de ce groupe.");
  if (memberIds.length < 2) throw new HttpsError("failed-precondition", "Ajoutez au moins un autre membre au groupe.");
  const groupName = String(groupValue.name || "Groupe WAPI").trim().slice(0, 80);
  const creator = await liveDisplayName(request.auth.uid);
  const call = db.collection("groupCallSessions").doc();
  const roomName = `wapi-call-${groupId.slice(0, 10)}-${call.id}`;
  const roomService = livekitRoomService();
  await roomService.createRoom({
    name: roomName,
    emptyTimeout: 180,
    departureTimeout: 30,
    maxParticipants: Math.min(32, Math.max(2, memberIds.length)),
    metadata: JSON.stringify({ callId: call.id, groupId, product: "wapi-group-call" }),
  });
  const access = await liveAccessToken({
    roomName,
    userId: request.auth.uid,
    displayName: creator.displayName,
    role: "speaker",
    product: "wapi-group-call",
  });
  const expiresAt = new Date(Date.now() + 6 * 60 * 60_000);
  try {
    await call.set({
      groupId,
      groupName,
      groupPhotoUrl: String(groupValue.photoUrl || ""),
      creatorId: request.auth.uid,
      creatorName: creator.displayName,
      adminIds: [...new Set([String(groupValue.ownerId || ""), ...((groupValue.adminIds as string[] | undefined) || [])])].filter(Boolean),
      memberIds,
      video,
      status: "active",
      participantCount: 0,
      streamProvider: "livekit-self-hosted",
      streamRoomId: roomName,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt,
      updatedAt: FieldValue.serverTimestamp(),
    });
  } catch (error) {
    await roomService.deleteRoom(roomName).catch(() => undefined);
    throw error;
  }
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
  return { callId: call.id, groupId, groupName, video, role: "speaker", ...access };
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

export const joinGroupCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  if (!callId) throw new HttpsError("invalid-argument", "Appel invalide.");
  const { reference, value } = await groupCallDocument(callId, request.auth.uid);
  const profile = await liveDisplayName(request.auth.uid);
  const roomName = String(value.streamRoomId || "");
  if (value.streamProvider !== "livekit-self-hosted" || !roomName) throw new HttpsError("failed-precondition", "Le transport WebRTC de cet appel est indisponible.");
  await reference.collection("participants").doc(request.auth.uid).set({
    userId: request.auth.uid,
    displayName: profile.displayName,
    photoUrl: profile.photoUrl,
    active: false,
    invitedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  const access = await liveAccessToken({
    roomName,
    userId: request.auth.uid,
    displayName: profile.displayName,
    role: "speaker",
    product: "wapi-group-call",
  });
  return { callId, groupId: String(value.groupId || ""), groupName: String(value.groupName || "Groupe WAPI"), video: value.video === true, role: "speaker", ...access };
});

export const setGroupCallPresence = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  const active = request.data?.active === true;
  await groupCallDocument(callId, request.auth.uid);
  await setGroupCallParticipantPresence(callId, request.auth.uid, active);
  return { ok: true };
});

export const endGroupCallSession = onCall({ secrets: [livekitApiSecret] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  const callId = String(request.data?.callId || "").trim();
  const { reference, value } = await groupCallDocument(callId, request.auth.uid);
  const adminIds = Array.isArray(value.adminIds) ? value.adminIds.map(String) : [];
  if (value.creatorId !== request.auth.uid && !adminIds.includes(request.auth.uid)) {
    throw new HttpsError("permission-denied", "Seuls le créateur et les administrateurs peuvent terminer cet appel.");
  }
  await reference.update({ status: "ended", participantCount: 0, endedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  await livekitRoomService().deleteRoom(String(value.streamRoomId || "")).catch(() => undefined);
  return { ok: true };
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
export const listVisibleStories = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  }
  const snapshot = await db.collection("stories")
    .where("audienceIds", "array-contains", request.auth.uid)
    .where("expiresAt", ">", new Date())
    .limit(250)
    .get();
  const stories = snapshot.docs.map((document) => {
    const value = document.data();
    const createdAt = value.createdAt;
    return {
      id: document.id,
      authorId: String(value.authorId || ""),
      authorName: String(value.authorName || "Contact WAPI"),
      caption: String(value.caption || ""),
      mediaUrl: String(value.mediaUrl || ""),
      mediaType: String(value.mediaType || "text"),
      createdAt: createdAt && typeof createdAt.toDate === "function"
        ? createdAt.toDate().toISOString()
        : null,
      expiresAtMillis: value.expiresAt && typeof value.expiresAt.toMillis === "function"
        ? value.expiresAt.toMillis()
        : 0,
      viewCount: Number(value.viewCount || 0),
    };
  });
  stories.sort((left, right) => (right.createdAt || "").localeCompare(left.createdAt || ""));
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
  const memberId = String(request.data?.memberId || "").trim();
  const administrator = request.data?.administrator === true;
  if (!groupId || !memberId) throw new HttpsError("invalid-argument", "Groupe ou membre invalide.");
  const group = db.collection("groups").doc(groupId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(group);
    if (!snapshot.exists) throw new HttpsError("not-found", "Ce groupe n’existe plus.");
    const ownerId = String(snapshot.get("ownerId") || "");
    const memberIds = Array.isArray(snapshot.get("memberIds")) ? (snapshot.get("memberIds") as unknown[]).map(String) : [];
    const currentAdmins = Array.isArray(snapshot.get("adminIds")) ? (snapshot.get("adminIds") as unknown[]).map(String) : [];
    if (ownerId !== request.auth!.uid) throw new HttpsError("permission-denied", "Seul le créateur peut gérer les administrateurs.");
    if (!memberIds.includes(memberId)) throw new HttpsError("failed-precondition", "Ce compte n’est pas membre du groupe.");
    if (memberId === ownerId && !administrator) throw new HttpsError("failed-precondition", "Le créateur reste administrateur.");
    const nextAdmins = [...new Set([ownerId, ...currentAdmins.filter((id) => id !== memberId), ...(administrator ? [memberId] : [])])];
    transaction.update(group, { adminIds: nextAdmins, updatedAt: FieldValue.serverTimestamp() });
    const event = group.collection("messages").doc();
    transaction.set(event, {
      text: administrator ? "Un nouvel administrateur a été nommé" : "Un administrateur a été retiré",
      senderId: request.auth!.uid,
      senderName: "Administration du groupe",
      kind: "system",
      createdAt: FieldValue.serverTimestamp(),
      deleted: false,
    });
  });
  return { ok: true };
});

export const manageGroupMembers = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Connexion WAPI requise.");
  }
  const actorId = request.auth.uid;
  const conversationId = String(request.data?.conversationId || "").trim();
  const action = String(request.data?.action || "").trim();
  const requestedIds: string[] = Array.isArray(request.data?.memberIds)
    ? [...new Set<string>((request.data.memberIds as unknown[]).map((value) => String(value).trim()).filter(Boolean))]
    : [];
  if (!conversationId || !["add", "remove"].includes(action)) {
    throw new HttpsError("invalid-argument", "Action de groupe invalide.");
  }
  if (!requestedIds.length || requestedIds.length > 20) {
    throw new HttpsError("invalid-argument", "Sélectionnez entre 1 et 20 membres.");
  }

  const conversation = db.collection("conversations").doc(conversationId);
  const initial = await conversation.get();
  const initialData = initial.data();
  if (!initial.exists || initialData?.conversationType !== "group") {
    throw new HttpsError("not-found", "Ce groupe n’existe plus.");
  }
  if (initialData.ownerId !== actorId) {
    throw new HttpsError("permission-denied", "Seul le propriétaire peut modifier les membres.");
  }
  if (action === "remove" && requestedIds.includes(actorId)) {
    throw new HttpsError("failed-precondition", "Le propriétaire ne peut pas se retirer du groupe.");
  }

  const profiles = action === "add"
    ? await db.getAll(...requestedIds.map((uid) => db.collection("users").doc(uid)))
    : [];
  if (action === "add" && profiles.some((profile) => !profile.exists)) {
    throw new HttpsError("not-found", "Un des comptes WAPI sélectionnés n’existe pas.");
  }
  const profilesById = new Map(profiles.map((profile) => [profile.id, profile.data() || {}]));
  const notice = action === "add" ? "Nouveaux membres ajoutés" : "Membres retirés";
  const systemMessage = conversation.collection("messages").doc();

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(conversation);
    const value = snapshot.data();
    if (!snapshot.exists || value?.ownerId !== actorId || value?.conversationType !== "group") {
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
    if (!byId.has(actorId) || byId.size < 2 || byId.size > 64) {
      throw new HttpsError("failed-precondition", "Un groupe doit contenir entre 2 et 64 membres.");
    }
    const members = [...byId.values()];
    transaction.update(conversation, {
      memberIds: [...byId.keys()],
      members,
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
