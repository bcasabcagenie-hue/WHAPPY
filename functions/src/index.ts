import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

if (!getApps().length) initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 20, memory: "256MiB" });

const db = getFirestore();

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
      // Data-only + high priority starts the Flutter background isolate even
      // when the UI process is not running. The isolate creates the visible
      // Android notification and its launcher badge summary.
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
