import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { initializeApp as initializeClientApp, deleteApp as deleteClientApp } from "firebase/app";
import { connectAuthEmulator, getAuth, signInAnonymously } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { initializeApp as initializeAdminApp, deleteApp as deleteAdminApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "whappy-d97e7";
const storageBucket = `${projectId}.firebasestorage.app`;
const suffix = Date.now().toString(36);
const conversationId = `voice-e2e-${suffix}`;
const messageId = `voice-note-${suffix}`;
const mediaName = `voice-${suffix}.m4a`;

const client = initializeClientApp({
  apiKey: "wapi-local-emulator",
  authDomain: `${projectId}.firebaseapp.com`,
  projectId,
  storageBucket,
});
const admin = initializeAdminApp({ projectId, storageBucket }, `voice-e2e-${suffix}`);

const auth = getAuth(client);
connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
const functions = getFunctions(client, "europe-west1");
connectFunctionsEmulator(functions, "127.0.0.1", 5001);

const firestore = getFirestore(admin);
const bucket = getStorage(admin).bucket();
let canonicalPath = "";

try {
  const credential = await signInAnonymously(auth);
  const uid = credential.user.uid;
  const conversation = firestore.collection("conversations").doc(conversationId);
  await conversation.set({
    conversationType: "direct",
    // Reproduces a direct conversation created by an older WAPI build: the
    // recipient existed in the immutable identity fields but not memberIds.
    memberIds: [uid],
    members: [{ uid }, { uid: "wapi-test-peer" }],
    ownerId: uid,
    contactId: "wapi-test-peer",
    lastMessage: "",
  });

  // Minimal MP4/M4A-like bytes are sufficient here: this test validates the
  // authenticated transport, immutable hash, object creation and message
  // commit. Codec decoding remains an Android instrumentation concern.
  const audio = Buffer.concat([
    Buffer.from([0, 0, 0, 24]),
    Buffer.from("ftypM4A "),
    Buffer.from("WAPI voice integration payload"),
  ]);
  const mediaSha256 = createHash("sha256").update(audio).digest("hex");
  canonicalPath = `conversations/${conversationId}/${uid}/${messageId}-${mediaName}`;
  const commit = httpsCallable(functions, "commitConversationMedia", { timeout: 30_000 });
  const payload = {
    conversationId,
    messageId,
    kind: "audio",
    storagePath: canonicalPath,
    mediaName,
    mediaSha256,
    durationSeconds: 2,
    inlineMediaBase64: audio.toString("base64"),
    inlineContentType: "audio/mp4",
  };

  const first = await commit(payload);
  assert.equal(first.data.delivered, true);
  const message = await conversation.collection("messages").doc(messageId).get();
  assert.equal(message.exists, true);
  assert.equal(message.get("senderId"), uid);
  assert.equal(message.get("kind"), "audio");
  assert.equal(message.get("mediaSha256"), mediaSha256);
  assert.match(message.get("mediaUrl"), /^https:\/\/firebasestorage\.googleapis\.com\//);
  const normalizedConversation = await conversation.get();
  assert.deepEqual(
    [...normalizedConversation.get("memberIds")].sort(),
    [uid, "wapi-test-peer"].sort(),
  );

  const [stored] = await bucket.file(canonicalPath).download();
  assert.equal(createHash("sha256").update(stored).digest("hex"), mediaSha256);

  const duplicate = await commit(payload);
  assert.equal(duplicate.data.delivered, true);
  assert.equal(duplicate.data.duplicate, true);
  process.stdout.write("PASS: note vocale créée, vérifiée, publiée et rejouée sans doublon.\n");
} finally {
  if (canonicalPath) {
    await bucket.file(canonicalPath).delete({ ignoreNotFound: true }).catch(() => undefined);
  }
  await firestore.recursiveDelete(firestore.collection("conversations").doc(conversationId)).catch(() => undefined);
  await auth.currentUser?.delete().catch(() => undefined);
  await deleteClientApp(client);
  await deleteAdminApp(admin);
}
