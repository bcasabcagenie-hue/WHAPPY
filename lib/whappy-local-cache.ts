const CACHE_VERSION = 2;
const CACHE_PREFIX = "wapi-cache-v2";
const DEFAULT_MAX_AGE = 7 * 24 * 60 * 60 * 1000;
const MAX_CACHE_BYTES = 1_500_000;

type CacheEnvelope<T> = {
  version: number;
  savedAt: number;
  value: T;
};

function storageKey(scope: string, ownerId: string) {
  const safeScope = scope.replace(/[^a-z0-9_-]/gi, "-");
  const safeOwner = ownerId.replace(/[^a-z0-9_-]/gi, "-");
  return `${CACHE_PREFIX}:${safeOwner}:${safeScope}`;
}

/** Device-local snapshot. Cloud listeners remain the source of truth. */
export function readWhappyCache<T>(scope: string, ownerId: string, maxAgeMs = DEFAULT_MAX_AGE): T | null {
  if (typeof window === "undefined" || !ownerId) return null;
  try {
    const raw = window.localStorage.getItem(storageKey(scope, ownerId));
    if (!raw) return null;
    const envelope = JSON.parse(raw) as Partial<CacheEnvelope<T>>;
    const expired = typeof envelope.savedAt !== "number" || Date.now() - envelope.savedAt > maxAgeMs;
    if (envelope.version !== CACHE_VERSION || !("value" in envelope) || expired) {
      window.localStorage.removeItem(storageKey(scope, ownerId));
      return null;
    }
    return envelope.value ?? null;
  } catch {
    return null;
  }
}

export function writeWhappyCache<T>(scope: string, ownerId: string, value: T) {
  if (typeof window === "undefined" || !ownerId) return;
  try {
    const envelope: CacheEnvelope<T> = { version: CACHE_VERSION, savedAt: Date.now(), value };
    const serialized = JSON.stringify(envelope);
    if (serialized.length > MAX_CACHE_BYTES) return;
    window.localStorage.setItem(storageKey(scope, ownerId), serialized);
  } catch {
    // Quota/private-mode failures must never block the cloud experience.
  }
}

export function removeWhappyCache(scope: string, ownerId: string) {
  if (typeof window === "undefined" || !ownerId) return;
  try { window.localStorage.removeItem(storageKey(scope, ownerId)); } catch { /* optional cache */ }
}

export function clearWhappyAccountCache(ownerId: string) {
  if (typeof window === "undefined" || !ownerId) return;
  const safeOwner = ownerId.replace(/[^a-z0-9_-]/gi, "-");
  const prefixes = [`${CACHE_PREFIX}:${safeOwner}:`, `whappy-cache-v1:${safeOwner}:`];
  try {
    for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
      const key = window.localStorage.key(index);
      if (key && prefixes.some((prefix) => key.startsWith(prefix))) window.localStorage.removeItem(key);
    }
  } catch { /* optional cache */ }
}
