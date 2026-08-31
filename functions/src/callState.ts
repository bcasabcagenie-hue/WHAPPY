export type CallPushState = {
  type: "call_answered" | "call_cancel";
  body: "Appel accepté" | "Appel terminé";
  terminal: boolean;
};

/**
 * Converts a Firestore call status to a push-notification action.
 * Acceptance is deliberately non-terminal: its only purpose is to dismiss
 * the ringing notification on the handset that answered.
 */
export function callPushState(status: unknown): CallPushState {
  const normalized = String(status || "ended").trim().toLowerCase();
  if (normalized === "accepted") {
    return { type: "call_answered", body: "Appel accepté", terminal: false };
  }
  return { type: "call_cancel", body: "Appel terminé", terminal: true };
}
