const assert = require("node:assert/strict");
const test = require("node:test");
const { callPushState } = require("../lib/callState.js");

test("accepting a call dismisses ringing without terminating WebRTC", () => {
  assert.deepEqual(callPushState("accepted"), {
    type: "call_answered",
    body: "Appel accepté",
    terminal: false,
  });
});

test("declined and ended calls remain terminal", () => {
  assert.equal(callPushState("declined").terminal, true);
  assert.equal(callPushState("ended").type, "call_cancel");
});
