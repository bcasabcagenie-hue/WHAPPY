const assert = require("node:assert/strict");
const { createHmac } = require("node:crypto");
const test = require("node:test");
const { createTurnIcePayload, selectTurnUrls, turnTcpEndpoint } = require("../lib/webrtcTurn.js");

test("creates one-hour TURN REST credentials for the signed-in WAPI user", () => {
  const now = 1_800_000_000_000;
  const secret = "a".repeat(64);
  const result = createTurnIcePayload(
    "turn:102.129.68.217:3478?transport=udp, turn:102.129.68.217:3478?transport=tcp",
    secret,
    "wapi-user",
    now,
  );
  const username = `${Math.floor(now / 1000) + 3600}:wapi-user`;

  assert.equal(result.turnConfigured, true);
  assert.equal(result.expiresAt, Math.floor(now / 1000) + 3600);
  assert.equal(result.iceServers.length, 6);
  assert.equal(result.iceServers[5].username, username);
  assert.equal(
    result.iceServers[5].credential,
    createHmac("sha1", secret).update(username).digest("base64"),
  );
});
test("does not advertise TURN when its secret is absent or too short", () => {
  const result = createTurnIcePayload("turn:relay.wapi.test:3478", "short", "user", 0);
  assert.equal(result.turnConfigured, false);
  assert.equal(result.iceServers.length, 5);
});

test("rejects non-TURN and malformed relay URLs", () => {
  const result = createTurnIcePayload(
    "https://relay.invalid, stun:relay.invalid, javascript:bad",
    "b".repeat(64),
    "user",
    0,
  );
  assert.equal(result.turnConfigured, false);
  assert.equal(result.iceServers.length, 5);
});

test("prefers a fresh self-hosted relay heartbeat over the bootstrap secret", () => {
  const now = 1_800_000_000_000;
  const selected = selectTurnUrls(
    "turn:old.example:3478",
    {
      active: true,
      urls: ["turn:102.129.81.15:3478?transport=udp", "turn:102.129.81.15:3478?transport=tcp"],
      updatedAtMs: now - 60_000,
    },
    now,
  );
  assert.equal(selected, "turn:102.129.81.15:3478?transport=udp,turn:102.129.81.15:3478?transport=tcp");
});

test("falls back to the static production relay when the dynamic relay is stale or stopped", () => {
  const now = 1_800_000_000_000;
  assert.equal(selectTurnUrls("turn:old.example:3478", {
    active: true,
    urls: ["turn:102.129.81.15:3478"],
    updatedAtMs: now - 13 * 60_000,
  }, now), "turn:old.example:3478");
  assert.equal(selectTurnUrls("turn:old.example:3478", {
    active: false,
    urls: ["turn:102.129.81.15:3478"],
    updatedAtMs: now,
  }, now), "turn:old.example:3478");
});

test("keeps Secret Manager as bootstrap until the first heartbeat exists", () => {
  assert.equal(selectTurnUrls(" turn:bootstrap.wapi:3478 ", null, 0), "turn:bootstrap.wapi:3478");
});

test("extracts only a public TCP TURN fallback for the external health check", () => {
  assert.deepEqual(turnTcpEndpoint(
    "turn:102.129.81.15:3478?transport=udp,turn:102.129.81.15:3478?transport=tcp",
  ), { host: "102.129.81.15", port: 3478 });
  assert.equal(turnTcpEndpoint("turn:192.168.100.62:3478?transport=tcp"), null);
  assert.equal(turnTcpEndpoint("turn:102.129.81.15:99999?transport=tcp"), null);
});
