const assert = require("node:assert/strict");
const test = require("node:test");
const {
  initialPoolBalls,
  planPoolAiShot,
  resolvePoolShot,
  sanitizePoolBalls,
  simulatePoolShot,
  validatePoolShot,
} = require("../lib/poolGame.js");

test("pool server rejects forged shot power and duplicate ball ids", () => {
  assert.throws(() => validatePoolShot({ angle: 0, power: 101, sideSpin: 0, followSpin: 0 }));
  const forged = initialPoolBalls();
  forged[1] = { ...forged[1], id: 0 };
  assert.throws(() => sanitizePoolBalls(forged));
});

test("pool server produces a deterministic break", () => {
  const shot = { angle: 0, power: 92, sideSpin: 0, followSpin: .15 };
  const first = simulatePoolShot(initialPoolBalls(), shot);
  const second = simulatePoolShot(initialPoolBalls(), shot);
  assert.deepEqual(first.balls, second.balls);
  assert.equal(first.firstContactBallId, second.firstContactBallId);
  assert.notEqual(first.firstContactBallId, null, "a full-power break must reach the rack");
  assert.ok(first.frames > 20 && first.frames < 1800);
});

test("pool AI uses regulation table geometry and strikes a legal target", () => {
  const table = initialPoolBalls().map((ball) => ({
    ...ball,
    pocketed: ball.id > 1,
  }));
  table[0] = {id: 0, x: .24, y: .63, pocketed: false};
  table[1] = {id: 1, x: .61, y: .36, pocketed: false};
  const shot = planPoolAiShot(table, "solids");
  assert.ok(shot.angle >= -Math.PI && shot.angle <= Math.PI);
  assert.ok(shot.power >= 10 && shot.power <= 100);
  const result = simulatePoolShot(table, shot);
  assert.equal(result.firstContactBallId, 1);
});

test("authoritative eight-ball rules assign groups and protect the black", () => {
  const table = initialPoolBalls();
  const assignment = resolvePoolShot(table, "open", "open", {
    firstContactBallId: 2,
    scratched: false,
    pocketedIds: [2],
  });
  assert.equal(assignment.shooterGroup, "solids");
  assert.equal(assignment.opponentGroup, "stripes");
  assert.equal(assignment.keepTurn, true);

  const earlyBlack = resolvePoolShot(table, "solids", "stripes", {
    firstContactBallId: 8,
    scratched: false,
    pocketedIds: [8],
  });
  assert.equal(earlyBlack.shooterWon, false);
});
