export const gameIds = new Set(["king-qi", "ludo", "checkers", "chess", "billard", "cards", "poker"]);
export const playerIcons = new Set(["cue", "crown", "fox", "lion", "robot"]);
export function playerCustomization(data: Record<string, unknown>) {
  const displayName = String(data.displayName || "").trim();
  const avatarMode = String(data.avatarMode || "account");
  const avatarIcon = String(data.avatarIcon || "cue");
  if (displayName.length < 2 || displayName.length > 40 || /[\u0000-\u001f]/.test(displayName)) throw new Error("invalid-player-name");
  if (!["account", "photo", "icon"].includes(avatarMode) || !playerIcons.has(avatarIcon)) throw new Error("invalid-player-avatar");
  // Never copy scores, balances, trophies or arbitrary URLs from a client.
  return { customDisplayName: displayName, avatarMode, avatarIcon };
}
export function publicPlayer(uid: string, gameId: string, identity: Record<string, unknown>, saved: Record<string, unknown>) {
  const count = (key: string) => Math.max(0, Math.floor(Number(saved[key]) || 0));
  const mode = String(saved.avatarMode || "account");
  return {
    uid, gameId, displayName: String(saved.customDisplayName || identity.displayName || "Joueur WAPI"),
    photoUrl: mode === "icon" ? "" : String((mode === "photo" && saved.customPhotoUrl) || identity.photoUrl || ""),
    avatarIcon: playerIcons.has(String(saved.avatarIcon)) ? String(saved.avatarIcon) : "cue", avatarMode: mode,
    country: String(identity.country || ""), victories: count("victories"), defeats: count("defeats"),
    matchesPlayed: count("matchesPlayed"), points: count("points"), trophies: count("trophies"),
    bestRun: count("bestRun"), rating: Math.max(0, Math.floor(Number(saved.rating) || 1000)),
  };
}

/** Called only after the server resolves a finished match, within its transaction. */
export function poolMatchAward(won: boolean, bestRun: number) {
  return { matchesPlayed: 1, victories: won ? 1 : 0, defeats: won ? 0 : 1,
    points: won ? 100 : 0, bestRun: Math.max(0, Math.min(15, Math.floor(bestRun))) };
}
