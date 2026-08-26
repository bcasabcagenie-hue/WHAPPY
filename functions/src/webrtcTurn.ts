import { createHmac } from "node:crypto";

const defaultStunServers = [
  { urls: ["stun:stun.l.google.com:19302"] },
  { urls: ["stun:stun1.l.google.com:19302"] },
  { urls: ["stun:stun2.l.google.com:19302"] },
  { urls: ["stun:stun3.l.google.com:19302"] },
  { urls: ["stun:stun4.l.google.com:19302"] },
];

const runtimeRelayMaxAgeMs = 12 * 60 * 1_000;

export type RuntimeTurnRelay = {
  active: boolean;
  urls: unknown;
  updatedAtMs: number;
};

export type TurnTcpEndpoint = {
  host: string;
  port: number;
};

function isPublicIpv4(host: string) {
  const octets = host.split(".").map(Number);
  if (octets.length !== 4 || octets.some((value) => !Number.isInteger(value) || value < 0 || value > 255)) {
    return false;
  }
  const [first, second] = octets;
  return !(
    first === 0 || first === 10 || first === 127 || first >= 224 ||
    (first === 100 && second >= 64 && second <= 127) ||
    (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && second === 168) ||
    (first === 198 && (second === 18 || second === 19))
  );
}

/** Returns the public TCP fallback advertised by WAPI's relay heartbeat. */
export function turnTcpEndpoint(rawUrls: string): TurnTcpEndpoint | null {
  for (const url of rawUrls.split(",")) {
    const match = /^turn:((?:\d{1,3}\.){3}\d{1,3}):(\d{1,5})\?transport=tcp$/i.exec(url.trim());
    if (!match || !isPublicIpv4(match[1])) continue;
    const port = Number(match[2]);
    if (port < 1 || port > 65_535) continue;
    return { host: match[1], port };
  }
  return null;
}

/**
 * Prefer the endpoint heartbeat published by WAPI's self-hosted relay.
 *
 * When the runtime document exists but is stale, returning an empty value is
 * intentional: advertising an old dynamic IP makes ICE wait on a relay which
 * cannot answer. The Secret Manager value remains a bootstrap fallback until
 * the first runtime heartbeat has been created.
 */
export function selectTurnUrls(
  secretFallback: string,
  runtimeRelay: RuntimeTurnRelay | null,
  nowMs = Date.now(),
) {
  if (!runtimeRelay) return secretFallback.trim();
  const ageMs = nowMs - runtimeRelay.updatedAtMs;
  if (
    runtimeRelay.active !== true ||
    !Number.isFinite(runtimeRelay.updatedAtMs) ||
    ageMs < -60_000 ||
    ageMs > runtimeRelayMaxAgeMs
  ) return "";
  if (!Array.isArray(runtimeRelay.urls)) return "";
  return runtimeRelay.urls
    .map((value) => String(value || "").trim())
    .filter((value) => /^turns?:[^\s]{1,500}$/i.test(value))
    .slice(0, 8)
    .join(",");
}

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
