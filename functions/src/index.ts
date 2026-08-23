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
  const video = call.video === true;
  await sendInBatches(devices, {
    data: {
      type: "incoming_call",
      title: callerName,
      body: video ? "Appel vidéo entrant" : "Appel audio entrant",
      callerName,
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

type LiveRole = "host" | "viewer";
type LiveReaction = "heart" | "applause" | "fire" | "wow";

const liveReactions = new Set<LiveReaction>(["heart", "applause", "fire", "wow"]);

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
}) {
  const configuration = livekitConfig();
  const isHost = options.role === "host";
  const token = new AccessToken(configuration.apiKey, configuration.apiSecret, {
    identity: options.userId,
    name: options.displayName,
    metadata: JSON.stringify({ role: options.role, product: "wapi-live" }),
    // Short tokens make revoked access effective quickly on a self-hosted SFU.
    ttl: "10m",
  });
  token.addGrant({
    roomJoin: true,
    room: options.roomName,
    canPublish: isHost,
    // Reactions pass through a rate-limited callable instead of giving every
    // spectator an unrestricted data channel to the whole room.
    canPublishData: isHost,
    canSubscribe: true,
    canUpdateOwnMetadata: isHost,
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
      aiGenerated: false,
      moderationStatus: "clear",
      streamProvider: "livekit-self-hosted",
      streamRoomId: roomName,
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
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(live);
    const value = snapshot.data();
    if (!snapshot.exists || !value || value.hostId !== hostId) {
      throw new HttpsError("permission-denied", "Seul l’animateur peut contrôler ce direct.");
    }
    if (action === "start") {
      if (value.status === "ended") throw new HttpsError("failed-precondition", "Ce direct est terminé.");
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
  return { ok: true, status: action === "start" ? "live" : "ended" };
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
      authorName: String(value.authorName || "Contact WAPI"),
      caption: String(value.caption || ""),
      mediaUrl: String(value.mediaUrl || ""),
      mediaType: String(value.mediaType || "text"),
      createdAt: createdAt && typeof createdAt.toDate === "function"
        ? createdAt.toDate().toISOString()
        : null,
    };
  });
  stories.sort((left, right) => (right.createdAt || "").localeCompare(left.createdAt || ""));
  return { stories };
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
