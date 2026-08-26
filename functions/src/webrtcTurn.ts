import { createHmac } from "node:crypto";

const defaultStunServers = [
  { urls: ["stun:stun.l.google.com:19302"] },
  { urls: ["stun:stun1.l.google.com:19302"] },
  { urls: ["stun:stun2.l.google.com:19302"] },
  { urls: ["stun:stun3.l.google.com:19302"] },
  { urls: ["stun:stun4.l.google.com:19302"] },
];

export function createTurnIcePayload(
  rawUrls: string,
  rawSecret: string,
  userId: string,
  nowMs = Date.now(),
) {
  const turnUrls = rawUrls
    .split(",")
    .map((value) => value.trim())
    .filter((value) => /^turns?:[^\s]{1,500}$/i.test(value))
    .slice(0, 8);
  const turnSecret = rawSecret.trim();
  const turnConfigured = turnUrls.length > 0 && turnSecret.length >= 32;
  const expiresAt = Math.floor(nowMs / 1_000) + 60 * 60;
  const username = `${expiresAt}:${userId}`;
  const credential = turnConfigured
    ? createHmac("sha1", turnSecret).update(username).digest("base64")
    : "";

  return {
    iceServers: [
      ...defaultStunServers,
      ...(turnConfigured ? [{ urls: turnUrls, username, credential }] : []),
    ],
    turnConfigured,
    expiresAt,
  };
}
