import { doc, getDoc, updateDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type EncryptionIdentity = { encryptionPublicKey: JsonWebKey };

type StoredIdentity = { privateKey: JsonWebKey; publicKey: JsonWebKey };

function storageKey(userId: string) { return `whappy:e2ee:${userId}`; }

function toBase64(value: ArrayBuffer) {
  return btoa(String.fromCharCode(...new Uint8Array(value)));
}

function fromBase64(value: string) {
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0)).buffer as ArrayBuffer;
}

async function localIdentity(userId: string): Promise<StoredIdentity> {
  const saved = window.localStorage.getItem(storageKey(userId));
  if (saved) return JSON.parse(saved) as StoredIdentity;
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveKey"]);
  const identity = {
    privateKey: await crypto.subtle.exportKey("jwk", pair.privateKey),
    publicKey: await crypto.subtle.exportKey("jwk", pair.publicKey),
  };
  window.localStorage.setItem(storageKey(userId), JSON.stringify(identity));
  return identity;
}

export async function ensureEncryptionIdentity(userId: string): Promise<EncryptionIdentity> {
  if (typeof window === "undefined") throw new Error("encryption-browser-only");
  const identity = await localIdentity(userId);
  await updateDoc(doc(db, "users", userId), { encryptionPublicKey: identity.publicKey }).catch(() => undefined);
  return { encryptionPublicKey: identity.publicKey };
}

export async function getEncryptionPublicKey(userId: string) {
  const snapshot = await getDoc(doc(db, "users", userId));
  return snapshot.exists() ? (snapshot.data().encryptionPublicKey as JsonWebKey | undefined) : undefined;
}

async function conversationKey(userId: string, peerPublicKey: JsonWebKey) {
  const identity = await localIdentity(userId);
  const privateKey = await crypto.subtle.importKey("jwk", identity.privateKey, { name: "ECDH", namedCurve: "P-256" }, false, ["deriveKey"]);
  const publicKey = await crypto.subtle.importKey("jwk", peerPublicKey, { name: "ECDH", namedCurve: "P-256" }, false, []);
  return crypto.subtle.deriveKey({ name: "ECDH", public: publicKey }, privateKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}

export async function encryptWhappyText(text: string, userId: string, peerPublicKey: JsonWebKey) {
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, await conversationKey(userId, peerPublicKey), new TextEncoder().encode(text));
  return { text: toBase64(encrypted), encryptionNonce: toBase64(nonce.buffer as ArrayBuffer), encryptionVersion: 1 };
}

export async function decryptWhappyText(text: string, userId: string, peerPublicKey: JsonWebKey, nonce: string) {
  const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64(nonce) }, await conversationKey(userId, peerPublicKey), fromBase64(text));
  return new TextDecoder().decode(decrypted);
}
