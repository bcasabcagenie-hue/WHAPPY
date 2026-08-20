import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

if (!getApps().length) initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 20, memory: "256MiB" });

const db = getFirestore();

async function deviceTokens(userIds: string[]): Promise<string[]> {
  const snapshots = await Promise.all(
    [...new Set(userIds.filter(Boolean))].map((userId) =>
      db.collection("users").doc(userId).collection("devices").where("enabled", "==", true).get(),
    ),
  );
  return [...new Set(snapshots.flatMap((snapshot) => snapshot.docs.map((document) => String(document.get("token") || "")).filter(Boolean)))];
}

async function sendInBatches(tokens: string[], message: Omit<MulticastMessage, "tokens">) {
  for (let index = 0; index < tokens.length; index += 500) {
    const batch = tokens.slice(index, index + 500);
    if (!batch.length) continue;
    const response = await getMessaging().sendEachForMulticast({ ...message, tokens: batch });
    if (response.failureCount) logger.warn("Certaines notifications WHAPPY n’ont pas été livrées", { failures: response.failureCount });
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
  const [sender, tokens] = await Promise.all([db.collection("users").doc(senderId).get(), deviceTokens(recipients)]);
  if (!tokens.length) return;
  const senderName = String(sender.get("displayName") || conversation.get("contactName") || "Contact WHAPPY");
  const kind = String(message.kind || "text");
  const text = String(message.text || "").trim();
  const body = kind === "image" ? "📷 Photo" : kind === "audio" ? "🎙️ Note vocale" : kind === "video" ? "🎥 Vidéo" : text.slice(0, 240);
  const groupTitle = String(conversation.get("title") || "").trim();
  await sendInBatches(tokens, {
    data: {
      type: "message",
      title: groupTitle || senderName,
      body: body || "Nouveau message",
      senderName,
      conversationId: event.params.conversationId,
    },
    android: { priority: "high", ttl: 86_400_000, collapseKey: `message-${event.params.conversationId}` },
  });
});

export const notifyIncomingCall = onDocumentCreated("calls/{callId}", async (event) => {
  const call = event.data?.data();
  if (!call || call.status !== "ringing") return;
  const calleeId = String(call.calleeId || "");
  const tokens = await deviceTokens([calleeId]);
  if (!tokens.length) return;
  const callerName = String(call.callerName || "Contact WHAPPY");
  const video = call.video === true;
  await sendInBatches(tokens, {
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
