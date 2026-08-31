const test = require('node:test');
const assert = require('node:assert/strict');
const {playerCustomization, publicPlayer, poolMatchAward} = require('../lib/gamePlayer');
const {validPoolCuePlacement, initialPoolBalls} = require('../lib/poolGame');
test('customization cannot set points, ranking or a remote URL', () => {
  assert.deepEqual(playerCustomization({displayName:'  Cyril  ', avatarMode:'icon', avatarIcon:'lion', points:999999, photoUrl:'https://evil.test'}),
    {customDisplayName:'Cyril', avatarMode:'icon', avatarIcon:'lion'});
  assert.throws(() => playerCustomization({displayName:'A', avatarIcon:'unknown'}));
});
test('customized identity survives repeated profile loads; icons hide account photos', () => {
  const p = publicPlayer('u', 'billard', {displayName:'Account', photoUrl:'original'}, {customDisplayName:'Player', avatarMode:'icon', avatarIcon:'fox', points:100});
  assert.equal(p.displayName,'Player'); assert.equal(p.photoUrl,''); assert.equal(p.points,100);
  assert.equal(publicPlayer('u','billard',{},{}).points,0);
});
test('match awards are points only and victories are server resolved', () => {
  assert.deepEqual(poolMatchAward(true,4),{matchesPlayed:1,victories:1,defeats:0,points:100,bestRun:4});
  assert.equal(poolMatchAward(false,3).points,0);
});
test('cue placement matches mobile dimensions and excludes overlaps and invalid floats', () => {
  assert.equal(validPoolCuePlacement(.23,.5, initialPoolBalls()), true);
  assert.equal(validPoolCuePlacement(NaN,.5, initialPoolBalls()), false);
  assert.equal(validPoolCuePlacement(.7,.5, initialPoolBalls()), false);
  assert.equal(validPoolCuePlacement(.23,.5,[{id:1,x:.23,y:.55}]),false);
});
