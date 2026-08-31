// This file refuses production projects/hosts before importing any backend.
const assert = require('node:assert/strict');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-wapi-pool');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8188');
const api = require('../lib/index');
const {getFirestore} = require('firebase-admin/firestore');
const {initialPoolBalls} = require('../lib/poolGame');
const db = getFirestore();
const call = (name, uid, data = {}) => api[name].run({auth: uid ? {uid, token:{}} : undefined, data});
async function run() {
  await db.doc('users/alice').set({displayName:'Alice', photoUrl:'https://example.test/avatar.jpg'});
  await db.doc('users/bob').set({displayName:'Bob'});
  await db.doc('users/carol').set({phoneNumber:'+15555550123'});
  assert.equal((await call('getGameProfile','alice',{gameId:'billard',uid:'carol'})).profile.displayName,'Joueur WAPI');
  await assert.rejects(call('saveGameProfile', null, {}), {code:'unauthenticated'});
  await call('saveGameProfile', 'alice', {gameId:'billard', displayName:'Alicia', avatarMode:'icon', avatarIcon:'fox', uid:'bob', points:9999, victories:900});
  const profile = (await call('getGameProfile', 'alice', {gameId:'billard'})).profile;
  assert.equal(profile.displayName, 'Alicia'); assert.equal(profile.photoUrl,''); assert.equal(profile.points,0);
  assert.equal((await db.doc('gameProfiles/bob_billard').get()).exists,false);
  const table = await call('createPoolMatch', 'alice');
  await call('joinPoolMatch', 'bob', {roomId:table.roomId});
  const room = db.doc(`gameRooms/${table.roomId}`);
  assert.equal((await room.get()).get('playerProfiles.alice.displayName'), 'Alicia');
  await room.update({ballInHandUid:'alice'});
  await assert.rejects(call('placePoolCueBall','bob',{roomId:table.roomId,x:.2,y:.4}), {code:'permission-denied'});
  await assert.rejects(call('placePoolCueBall','alice',{roomId:table.roomId,x:.7,y:.4}), {code:'invalid-argument'});
  await call('placePoolCueBall','alice',{roomId:table.roomId,x:.2,y:.4});
  assert.equal((await room.get()).get('ballInHandUid'),'');
  // A legal winning eight-ball shot through the real server physics solver.
  const balls = initialPoolBalls().map(ball=>({...ball,pocketed:ball.id!==0&&ball.id!==8,
    ...(ball.id===0?{x:.5,y:.30}:ball.id===8?{x:.5,y:.15}:{})}));
  await room.update({balls, groups:{alice:'solids',bob:'stripes'},turnUid:'alice',turnDeadlineMs:Date.now()+45000});
  const shot = {roomId:table.roomId,angle:-Math.PI/2,power:20,sideSpin:0,followSpin:0};
  await assert.rejects(call('submitPoolShot','intruder',shot),{code:'permission-denied'});
  const attempts = await Promise.allSettled(Array.from({length:4},()=>call('submitPoolShot','alice',shot)));
  assert.equal(attempts.filter(x=>x.status==='fulfilled').length,1);
  const winner = (await call('getGameProfile','alice',{gameId:'billard'})).profile;
  const loser = (await call('getGameProfile','bob',{gameId:'billard'})).profile;
  assert.equal(winner.points,100); assert.equal(winner.victories,1); assert.equal(winner.matchesPlayed,1);
  assert.equal(winner.displayName,'Alicia'); assert.equal(loser.defeats,1); assert.equal(loser.points,0);
  assert.equal((await db.doc(`poolResults/${table.roomId}`).get()).get('monetaryValue'),0);
  const enc = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${enc({alg:'none',typ:'JWT'})}.${enc({sub:'alice',user_id:'alice',aud:'demo-wapi-pool',iss:'https://securetoken.google.com/demo-wapi-pool',iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,firebase:{sign_in_provider:'custom'}})}.`;
  for (const path of ['gameProfiles/alice_billard',`poolResults/${table.roomId}`,`gameRooms/${table.roomId}`]) {
    const res = await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-wapi-pool/databases/(default)/documents/${path}`,{
      method:'PATCH',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({fields:{points:{integerValue:'999999'}}})});
    assert.equal(res.status,403,`Direct client write must be denied: ${path}`);
  }
  console.log('PASS: profile identity, forged points rejected, ownership checks, cue placement, real winning shot, 4 concurrent submissions/1 award, 3 Firestore write denials.');
  await db.terminate();
}
run().catch(error=>{console.error(error);process.exitCode=1});
