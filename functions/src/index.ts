import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
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

export const notifyNewMessage = onDocumentCreated("conversations/{conversationId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;
  const conversation = await db.collection("conversations").doc(event.params.conversationId).get();
  if (!conversation.exists) return;
  const senderId = String(message.senderId || "");
  const recipients = (conversation.get("memberIds") as string[] | undefined)?.filter((id) => id && id !== senderId) ?? [];
  if (!recipients.length) return;
  const [sender, devices] = await Promise.all([db.collection("users").doc(senderId).get(), pushDevices(recipients)]);
  if (!devices.length) return;
  const senderName = String(sender.get("displayName") || conversation.get("contactName") || "Contact WHAPPY");
  const kind = String(message.kind || "text");
  const text = String(message.text || "").trim();
  const body = kind === "image" ? "📷 Photo" : kind === "audio" ? "🎙️ Note vocale" : kind === "video" ? "🎥 Vidéo" : text.slice(0, 240);
  const groupTitle = String(conversation.get("title") || "").trim();
  await sendInBatches(devices, {
    data: {
      type: "message",
      title: groupTitle || senderName,
      body: body || "Nouveau message",
      senderName,
      conversationId: event.params.conversationId,
      messageId: event.params.messageId,
    },
    // Data-only + high priority reaches FirebaseMessagingService even when
    // WAPI is not open.  Messages are intentionally not collapsed: each
    // delivery increments the real unread badge on the launcher.
    android: { priority: "high", ttl: 86_400_000 },
  });
});

export const notifyIncomingCall = onDocumentCreated("calls/{callId}", async (event) => {
  const call = event.data?.data();
  if (!call || call.status !== "ringing") return;
  const calleeId = String(call.calleeId || "");
  const devices = await pushDevices([calleeId]);
  if (!devices.length) return;
  const callerName = String(call.callerName || "Contact WHAPPY");
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
