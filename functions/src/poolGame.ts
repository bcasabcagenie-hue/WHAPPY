export type PoolGroup = "open" | "solids" | "stripes";

export type PoolBall = {
  id: number;
  x: number;
  y: number;
  vx?: number;
  vy?: number;
  pocketed?: boolean;
  sideSpin?: number;
  followSpin?: number;
};

export type PoolShot = {
  angle: number;
  power: number;
  sideSpin: number;
  followSpin: number;
};

export type PoolSimulation = {
  balls: PoolBall[];
  pocketedIds: number[];
  scratched: boolean;
  firstContactBallId: number | null;
  frames: number;
};

export type PoolResolution = {
  shooterGroup: PoolGroup;
  opponentGroup: PoolGroup;
  foul: boolean;
  keepTurn: boolean;
  shooterWon: boolean | null;
  message: string;
};

const WIDTH = 10.2;
const HEIGHT = 5.10;
const RADIUS = 0.145;
const POCKETS: Array<[number, number]> = [
  [.055, .07], [.5, .07], [.945, .07],
  [.055, .93], [.5, .93], [.945, .93],
];

const clamp = (value: number, minimum: number, maximum: number) => Math.min(maximum, Math.max(minimum, value));
const finite = (value: unknown, fallback = 0) => Number.isFinite(Number(value)) ? Number(value) : fallback;

export function initialPoolBalls(): PoolBall[] {
  const balls: PoolBall[] = [{ id: 0, x: .23, y: .5 }];
  let id = 1;
  for (let row = 0; row <= 4; row += 1) {
    for (let column = 0; column <= row; column += 1) {
      balls.push({ id: id++, x: .700 + row * .0295, y: .5 + (column - row / 2) * .0575 });
    }
  }
  return balls;
}

export function sanitizePoolBalls(value: unknown): PoolBall[] {
  if (!Array.isArray(value) || value.length !== 16) throw new Error("invalid-pool-balls");
  const ids = new Set<number>();
  const balls = value.map((raw) => {
    const item = raw && typeof raw === "object" ? raw as Record<string, unknown> : {};
    const id = Number(item.id);
    const x = finite(item.x, -1);
    const y = finite(item.y, -1);
    if (!Number.isInteger(id) || id < 0 || id > 15 || ids.has(id) || x < 0 || x > 1 || y < 0 || y > 1) {
      throw new Error("invalid-pool-ball");
    }
    ids.add(id);
    return { id, x, y, pocketed: item.pocketed === true, vx: 0, vy: 0, sideSpin: 0, followSpin: 0 };
  });
  if (ids.size !== 16) throw new Error("invalid-pool-ids");
  return balls.sort((a, b) => a.id - b.id);
}

export function validatePoolShot(value: unknown): PoolShot {
  const data = value && typeof value === "object" ? value as Record<string, unknown> : {};
  const angle = finite(data.angle, Number.NaN);
  const power = finite(data.power, Number.NaN);
  const sideSpin = finite(data.sideSpin, Number.NaN);
  const followSpin = finite(data.followSpin, Number.NaN);
  if (![angle, power, sideSpin, followSpin].every(Number.isFinite)) throw new Error("invalid-shot");
  if (angle < -Math.PI * 4 || angle > Math.PI * 4 || power < 10 || power > 100 || Math.abs(sideSpin) > 1 || Math.abs(followSpin) > 1) {
    throw new Error("shot-out-of-range");
  }
  return { angle, power: Math.round(power), sideSpin, followSpin };
}

function distance(a: PoolBall, b: PoolBall) {
  return Math.hypot((b.x - a.x) * WIDTH, (b.y - a.y) * HEIGHT);
}

function moving(balls: PoolBall[]) {
  return balls.some((ball) => !ball.pocketed && Math.hypot((ball.vx || 0) * WIDTH, (ball.vy || 0) * HEIGHT) > .09);
}

function advanceSubstep(before: PoolBall[], delta: number) {
  const next = before.map((ball) => {
    if (ball.pocketed) return { ...ball };
    const worldVx = (ball.vx || 0) * WIDTH;
    const worldVy = (ball.vy || 0) * HEIGHT;
    const speed = Math.hypot(worldVx, worldVy);
    const curve = ball.id === 0 ? (ball.sideSpin || 0) * clamp(speed / 8, 0, 1) * .0026 : 0;
    const curvedVx = worldVx * Math.cos(curve) - worldVy * Math.sin(curve);
    const curvedVy = worldVx * Math.sin(curve) + worldVy * Math.cos(curve);
    const nextSpeed = Math.max(0, speed - .72 * delta);
    const rollingFactor = speed > .0001 ? nextSpeed / speed : 0;
    const spinFactor = clamp(1 - .74 * delta, 0, 1);
    return {
      ...ball,
      x: ball.x + curvedVx / WIDTH * delta,
      y: ball.y + curvedVy / HEIGHT * delta,
      vx: curvedVx / WIDTH * rollingFactor,
      vy: curvedVy / HEIGHT * rollingFactor,
      sideSpin: (ball.sideSpin || 0) * spinFactor,
      followSpin: (ball.followSpin || 0) * spinFactor,
    };
  });
  let scratched = false;
  let firstContactBallId: number | null = null;
  const pocketedIds: number[] = [];
  for (let index = 0; index < next.length; index += 1) {
    const ball = next[index];
    if (ball.pocketed) continue;
    const nearestPocket = POCKETS.reduce((best, pocket) => {
      const distance = Math.hypot((ball.x - pocket[0]) * WIDTH, (ball.y - pocket[1]) * HEIGHT);
      return distance < best.distance ? { pocket, distance } : best;
    }, { pocket: POCKETS[0], distance: Number.POSITIVE_INFINITY });
    const [pocketX, pocketY] = nearestPocket.pocket;
    const pocketDx = (pocketX - ball.x) * WIDTH;
    const pocketDy = (pocketY - ball.y) * HEIGHT;
    const pocketThreshold = Math.abs(pocketX - .5) < .05 ? .34 : .325;
    const hitPocket = nearestPocket.distance < pocketThreshold;
    if (hitPocket) {
      if (ball.id === 0) {
        scratched = true;
        next[index] = { ...ball, x: .23, y: .5, vx: 0, vy: 0, sideSpin: 0, followSpin: 0 };
      } else {
        pocketedIds.push(ball.id);
        next[index] = { ...ball, vx: 0, vy: 0, sideSpin: 0, followSpin: 0, pocketed: true };
      }
      continue;
    }
    let { x, y } = ball;
    let vx = ball.vx || 0;
    let vy = ball.vy || 0;
    if (nearestPocket.distance < .46 && nearestPocket.distance > .001) {
      const attraction = clamp((.46 - nearestPocket.distance) / .135, 0, 1);
      vx += pocketDx / nearestPocket.distance * attraction * 1.28 * delta / WIDTH;
      vy += pocketDy / nearestPocket.distance * attraction * 1.28 * delta / HEIGHT;
    } else {
      if (x < .064) { x = .064; vy += (ball.sideSpin || 0) * Math.abs(vx) * .035; vx = Math.abs(vx) * .89; }
      if (x > .936) { x = .936; vy -= (ball.sideSpin || 0) * Math.abs(vx) * .035; vx = -Math.abs(vx) * .89; }
      if (y < .093) { y = .093; vx -= (ball.sideSpin || 0) * Math.abs(vy) * .035; vy = Math.abs(vy) * .89; }
      if (y > .907) { y = .907; vx += (ball.sideSpin || 0) * Math.abs(vy) * .035; vy = -Math.abs(vy) * .89; }
    }
    next[index] = { ...ball, x, y, vx, vy };
  }
  for (let first = 0; first < next.length; first += 1) {
    for (let second = first + 1; second < next.length; second += 1) {
      const a = next[first];
      const b = next[second];
      if (a.pocketed || b.pocketed) continue;
      const dx = (b.x - a.x) * WIDTH;
      const dy = (b.y - a.y) * HEIGHT;
      const currentDistance = distance(a, b);
      if (currentDistance <= 0 || currentDistance >= RADIUS * 2) continue;
      const nx = dx / currentDistance;
      const ny = dy / currentDistance;
      const relative = ((b.vx || 0) - (a.vx || 0)) * WIDTH * nx + ((b.vy || 0) - (a.vy || 0)) * HEIGHT * ny;
      const correction = (RADIUS * 2 - currentDistance) * .5 + .001;
      let nextA: PoolBall = { ...a, x: a.x - nx * correction / WIDTH, y: a.y - ny * correction / HEIGHT };
      let nextB: PoolBall = { ...b, x: b.x + nx * correction / WIDTH, y: b.y + ny * correction / HEIGHT };
      if (relative < 0) {
        const impulse = -relative * .97;
        nextA = { ...nextA, vx: (a.vx || 0) - impulse * nx / WIDTH, vy: (a.vy || 0) - impulse * ny / HEIGHT };
        nextB = { ...nextB, vx: (b.vx || 0) + impulse * nx / WIDTH, vy: (b.vy || 0) + impulse * ny / HEIGHT };
        if (a.id === 0 || b.id === 0) {
          firstContactBallId ??= a.id === 0 ? b.id : a.id;
          const cue = a.id === 0 ? a : b;
          const hitX = a.id === 0 ? nx : -nx;
          const hitY = a.id === 0 ? ny : -ny;
          const followX = (cue.followSpin || 0) * impulse * .58 * hitX;
          const followY = (cue.followSpin || 0) * impulse * .58 * hitY;
          const englishX = -(cue.sideSpin || 0) * impulse * .13 * hitY;
          const englishY = (cue.sideSpin || 0) * impulse * .13 * hitX;
          const adjusted = a.id === 0 ? nextA : nextB;
          const spun = {
            ...adjusted,
            vx: (adjusted.vx || 0) + (followX + englishX) / WIDTH,
            vy: (adjusted.vy || 0) + (followY + englishY) / HEIGHT,
            sideSpin: (cue.sideSpin || 0) * .52,
            followSpin: (cue.followSpin || 0) * .34,
          };
          if (a.id === 0) nextA = spun; else nextB = spun;
        }
      }
      next[first] = nextA;
      next[second] = nextB;
    }
  }
  return { balls: next, scratched, firstContactBallId, pocketedIds };
}

/** Deterministic high-frequency integration matching the Android engine.
 * Display frames are split into <= 6 ms physics steps so a break shot cannot
 * tunnel through a ball or skip a pocket on a slower device. */
function advance(before: PoolBall[], delta = .018) {
  const safeDelta = clamp(delta, 0, .05);
  const substeps = clamp(Math.ceil(safeDelta / .006), 1, 9);
  const stepDelta = safeDelta / substeps;
  let balls = before;
  let scratched = false;
  let firstContactBallId: number | null = null;
  const pocketedIds = new Set<number>();
  for (let step = 0; step < substeps; step += 1) {
    const frame = advanceSubstep(balls, stepDelta);
    balls = frame.balls;
    scratched ||= frame.scratched;
    firstContactBallId ??= frame.firstContactBallId;
    frame.pocketedIds.forEach((id) => pocketedIds.add(id));
  }
  return { balls, scratched, firstContactBallId, pocketedIds: [...pocketedIds] };
}

export function simulatePoolShot(source: PoolBall[], rawShot: PoolShot): PoolSimulation {
  const shot = validatePoolShot(rawShot);
  let balls = sanitizePoolBalls(source);
  const cue = balls.find((ball) => ball.id === 0);
  if (!cue) throw new Error("missing-cue-ball");
  const speed = 3.4 + shot.power / 100 * 7.4;
  cue.vx = Math.cos(shot.angle) * speed / WIDTH;
  cue.vy = Math.sin(shot.angle) * speed / HEIGHT;
  cue.sideSpin = shot.sideSpin;
  cue.followSpin = shot.followSpin;
  let scratched = false;
  let firstContactBallId: number | null = null;
  const pocketedIds = new Set<number>();
  let frames = 0;
  for (; frames < 1_800; frames += 1) {
    const frame = advance(balls);
    balls = frame.balls;
    scratched ||= frame.scratched;
    firstContactBallId ??= frame.firstContactBallId;
    frame.pocketedIds.forEach((id) => pocketedIds.add(id));
    if (!moving(balls)) break;
  }
  balls = balls.map((ball) => ({ id: ball.id, x: ball.x, y: ball.y, pocketed: ball.pocketed === true }));
  return { balls, scratched, firstContactBallId, pocketedIds: [...pocketedIds], frames };
}

export function groupForBall(id: number): PoolGroup {
  if (id >= 1 && id <= 7) return "solids";
  if (id >= 9 && id <= 15) return "stripes";
  return "open";
}

export function validPoolCuePlacement(x: number, y: number, balls: PoolBall[]) {
  if (!Number.isFinite(x) || !Number.isFinite(y) || x < .085 || x > .445 || y < .115 || y > .885) return false;
  if (POCKETS.some(([px, py]) => Math.hypot((x - px) * WIDTH, (y - py) * HEIGHT) < .40)) return false;
  return !balls.some(b => b.id !== 0 && !b.pocketed && Math.hypot((b.x - x) * WIDTH, (b.y - y) * HEIGHT) < RADIUS * 2.08);
}

function targets(group: PoolGroup, balls: PoolBall[]) {
  const own = balls.filter((ball) => !ball.pocketed && groupForBall(ball.id) === group).map((ball) => ball.id);
  if (group === "open") return balls.filter((ball) => !ball.pocketed && ball.id >= 1 && ball.id <= 15 && ball.id !== 8).map((ball) => ball.id);
  return own.length ? own : [8];
}

function laneIsClear(
  balls: PoolBall[],
  from: PoolBall,
  toX: number,
  toY: number,
  ignoredIds: Set<number>,
) {
  const dx = (toX - from.x) * WIDTH;
  const dy = (toY - from.y) * HEIGHT;
  const lengthSquared = dx * dx + dy * dy;
  if (lengthSquared < .001) return false;
  return !balls.some((ball) => {
    if (ball.pocketed || ignoredIds.has(ball.id)) return false;
    const bx = (ball.x - from.x) * WIDTH;
    const by = (ball.y - from.y) * HEIGHT;
    const projection = clamp((bx * dx + by * dy) / lengthSquared, 0, 1);
    if (projection <= .035 || projection >= .975) return false;
    const nearestX = dx * projection;
    const nearestY = dy * projection;
    return Math.hypot(bx - nearestX, by - nearestY) < RADIUS * 2.08;
  });
}

/**
 * Plans an actual pot rather than aiming at the first visible ball. The AI
 * evaluates every legal ball/pocket pair, computes the ghost-ball contact,
 * rejects obstructed lanes and favours a straight, short shot. Coordinates
 * are converted to the regulation 2:1 table before calculating the angle.
 */
export function planPoolAiShot(source: PoolBall[], group: PoolGroup): PoolShot {
  const balls = sanitizePoolBalls(source);
  const cue = balls.find((ball) => ball.id === 0 && !ball.pocketed);
  if (!cue) return { angle: 0, power: 45, sideSpin: 0, followSpin: 0 };
  const legalIds = new Set(targets(group, balls));
  const candidates = balls.filter((ball) => !ball.pocketed && legalIds.has(ball.id));
  let best: { shot: PoolShot; score: number } | null = null;

  for (const target of candidates) {
    for (const [pocketX, pocketY] of POCKETS) {
      const objectX = (pocketX - target.x) * WIDTH;
      const objectY = (pocketY - target.y) * HEIGHT;
      const objectDistance = Math.hypot(objectX, objectY);
      if (objectDistance < .10) continue;
      const unitX = objectX / objectDistance;
      const unitY = objectY / objectDistance;
      const contactDistance = RADIUS * 2.02;
      const ghostX = target.x - unitX * contactDistance / WIDTH;
      const ghostY = target.y - unitY * contactDistance / HEIGHT;
      if (ghostX < .064 || ghostX > .936 || ghostY < .093 || ghostY > .907) continue;

      const cueX = (ghostX - cue.x) * WIDTH;
      const cueY = (ghostY - cue.y) * HEIGHT;
      const cueDistance = Math.hypot(cueX, cueY);
      if (cueDistance < .12) continue;
      const cutQuality = (cueX / cueDistance) * unitX + (cueY / cueDistance) * unitY;
      if (cutQuality < .18) continue;
      if (!laneIsClear(balls, cue, ghostX, ghostY, new Set([0, target.id]))) continue;
      if (!laneIsClear(balls, target, pocketX, pocketY, new Set([target.id]))) continue;

      const score = cutQuality * 5.2 - cueDistance * .18 - objectDistance * .12;
      const power = clamp(30 + cueDistance * 5.1 + objectDistance * 2.8, 34, 86);
      const shot: PoolShot = {
        angle: Math.atan2(cueY, cueX),
        power: Math.round(power),
        sideSpin: 0,
        followSpin: cutQuality > .82 ? .10 : 0,
      };
      if (!best || score > best.score) best = { shot, score };
    }
  }
  if (best) return best.shot;

  const target = candidates.reduce<PoolBall | null>((nearest, ball) => {
    if (!nearest) return ball;
    const current = Math.hypot((ball.x - cue.x) * WIDTH, (ball.y - cue.y) * HEIGHT);
    const previous = Math.hypot((nearest.x - cue.x) * WIDTH, (nearest.y - cue.y) * HEIGHT);
    return current < previous ? ball : nearest;
  }, null);
  if (!target) return { angle: 0, power: 45, sideSpin: 0, followSpin: 0 };
  const dx = (target.x - cue.x) * WIDTH;
  const dy = (target.y - cue.y) * HEIGHT;
  const distance = Math.hypot(dx, dy);
  return {
    angle: Math.atan2(dy, dx),
    power: Math.round(clamp(40 + distance * 5.2, 40, 78)),
    sideSpin: 0,
    followSpin: .08,
  };
}

export function resolvePoolShot(
  ballsBefore: PoolBall[],
  shooterGroupBefore: PoolGroup,
  opponentGroupBefore: PoolGroup,
  simulation: Pick<PoolSimulation, "firstContactBallId" | "scratched" | "pocketedIds">,
): PoolResolution {
  const legalTargets = new Set(targets(shooterGroupBefore, ballsBefore));
  const wrongFirst = simulation.firstContactBallId === null || !legalTargets.has(simulation.firstContactBallId);
  const foul = simulation.scratched || wrongFirst;
  let shooterGroup = shooterGroupBefore;
  let opponentGroup = opponentGroupBefore;
  if (!foul && shooterGroupBefore === "open") {
    const assigned = simulation.pocketedIds.find((id) => (id >= 1 && id <= 7) || (id >= 9 && id <= 15));
    if (assigned !== undefined) {
      shooterGroup = groupForBall(assigned);
      opponentGroup = shooterGroup === "solids" ? "stripes" : "solids";
    }
  }
  if (simulation.pocketedIds.includes(8)) {
    const cleared = shooterGroupBefore !== "open" && !ballsBefore.some((ball) => !ball.pocketed && groupForBall(ball.id) === shooterGroupBefore);
    const won = cleared && !foul && simulation.firstContactBallId === 8;
    return { shooterGroup, opponentGroup, foul, keepTurn: false, shooterWon: won, message: won ? "Noire empochée légalement · victoire." : "Noire empochée trop tôt ou avec faute · défaite." };
  }
  const ownPocket = simulation.pocketedIds.some((id) => id !== 8 && (shooterGroup === "open" || groupForBall(id) === shooterGroup));
  const message = simulation.scratched ? "Faute : blanche empochée · bille en main." :
    simulation.firstContactBallId === null ? "Faute : aucune bille touchée." :
      wrongFirst ? "Faute : mauvaise bille touchée en premier." :
        shooterGroupBefore === "open" && shooterGroup !== "open" ? `Groupe attribué : ${shooterGroup === "solids" ? "billes pleines" : "billes rayées"}.` :
          ownPocket ? "Bille correcte empochée · le joueur continue." : "Tir terminé · changement de joueur.";
  return { shooterGroup, opponentGroup, foul, keepTurn: !foul && ownPocket, shooterWon: null, message };
}
