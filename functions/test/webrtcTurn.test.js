const assert = require("node:assert/strict");
const { createHmac } = require("node:crypto");
const test = require("node:test");
const { createTurnIcePayload } = require("../lib/webrtcTurn.js");

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
  assert.equal(result.iceServers.length, 3);
  assert.equal(result.iceServers[2].username, username);
  assert.equal(
    result.iceServers[2].credential,
    createHmac("sha1", secret).update(username).digest("base64"),
  );
});
test("does not advertise TURN when its secret is absent or too short", () => {
  const result = createTurnIcePayload("turn:relay.wapi.test:3478", "short", "user", 0);
  assert.equal(result.turnConfigured, false);
  assert.equal(result.iceServers.length, 2);
});

test("rejects non-TURN and malformed relay URLs", () => {
  const result = createTurnIcePayload(
    "https://relay.invalid, stun:relay.invalid, javascript:bad",
    "b".repeat(64),
    "user",
    0,
  );
  assert.equal(result.turnConfigured, false);
  assert.equal(result.iceServers.length, 2);
});
