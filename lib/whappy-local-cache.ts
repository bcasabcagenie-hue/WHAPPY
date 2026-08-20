const CACHE_VERSION = 1;
const CACHE_PREFIX = "whappy-cache-v1";

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
export function readWhappyCache<T>(scope: string, ownerId: string): T | null {
  if (typeof window === "undefined" || !ownerId) return null;
  try {
    const raw = window.localStorage.getItem(storageKey(scope, ownerId));
    if (!raw) return null;
    const envelope = JSON.parse(raw) as Partial<CacheEnvelope<T>>;
    if (envelope.version !== CACHE_VERSION || !("value" in envelope)) return null;
    return envelope.value ?? null;
  } catch {
    return null;
  }
}

export function writeWhappyCache<T>(scope: string, ownerId: string, value: T) {
  if (typeof window === "undefined" || !ownerId) return;
  try {
    const envelope: CacheEnvelope<T> = { version: CACHE_VERSION, savedAt: Date.now(), value };
    window.localStorage.setItem(storageKey(scope, ownerId), JSON.stringify(envelope));
  } catch {
    // Quota/private-mode failures must never block the cloud experience.
  }
}

export function removeWhappyCache(scope: string, ownerId: string) {
  if (typeof window === "undefined" || !ownerId) return;
  try { window.localStorage.removeItem(storageKey(scope, ownerId)); } catch { /* optional cache */ }
}
