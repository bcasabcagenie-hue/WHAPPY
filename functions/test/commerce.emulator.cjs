// Run only against the explicitly isolated demo project; never production.
const assert = require('node:assert/strict');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { handleCommerce } = require('../lib/commerce');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-wapi-commerce');
assert.ok(process.env.FIRESTORE_EMULATOR_HOST?.startsWith('127.0.0.1:'));
initializeApp({ projectId: 'demo-wapi-commerce' });
const db = getFirestore();
async function run() {
  await db.doc('businessPages/cafe').set({ ownerId: 'owner', name: 'Café des Arts', status: 'active', category: 'Restaurant', city: 'Lomé' });
  await handleCommerce(db, 'owner', { action: 'saveStorefront', pageId: 'cafe', published: true, address: '12 avenue des Arts', hours: 'Lun–sam 9h–20h' });
  await handleCommerce(db, 'owner', { action: 'saveProduct', pageId: 'cafe', productId: 'menu', name: 'Menu déjeuner', description: 'Plat et dessert', category: 'Menus', priceMinor: 5500, currency: 'XOF', available: true });
  const directory = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe', city: 'lome' });
  assert.equal(directory.stores.length, 1);
  const store = await handleCommerce(db, 'visitor', { action: 'storefront', pageId: 'cafe' });
  assert.equal(store.products[0].priceMinor, 5500);
  const batch = db.batch();
  for (let n = 0; n < 103; n++) {
    const key = `b${String(n).padStart(3, '0')}`;
    batch.set(db.doc(`businessStorefronts/${key}`), { published: true });
    batch.set(db.doc(`businessPages/${key}`), { ownerId: 'owner', status: 'active', name: n >= 41 ? 'Recherche Café' : 'Atelier', city: 'Lomé' });
  }
  await batch.commit();
  const firstPage = await handleCommerce(db, 'visitor', { action: 'directory', search: 'recherche cafe', city: 'lome' });
  assert.equal(firstPage.stores.length, 40);
  assert.equal(firstPage.stores[0].id, 'b041');
  assert.equal(firstPage.nextCursor, 'b080');
  const secondPage = await handleCommerce(db, 'visitor', { action: 'directory', search: 'recherche cafe', cursor: firstPage.nextCursor });
  assert.equal(secondPage.stores.length, 22);
  assert.equal(new Set([...firstPage.stores, ...secondPage.stores].map(row => row.id)).size, 62);
  assert.equal(secondPage.nextCursor, null);
  await assert.rejects(handleCommerce(db, 'intruder', { action: 'deleteProduct', pageId: 'cafe', productId: 'menu' }), { code: 'permission-denied' });
  await handleCommerce(db, 'owner', { action: 'createEvent', eventId: 'concert', pageId: 'cafe', title: 'Concert acoustique', description: 'Concert de quartier', venue: 'Café des Arts', capacity: 3, startsAt: Date.now() + 86400000 });
  const attempts = await Promise.allSettled(Array.from({length: 8}, (_, i) => handleCommerce(db, `visitor-${i}`, { action: 'reserveTicket', eventId: 'concert' })));
  const issued = attempts.filter(result => result.status === 'fulfilled').map(result => result.value.ticket);
  assert.equal(issued.length, 3);
  assert.equal((await db.doc('ticketbulkEvents/concert').get()).get('reserved'), 3);
  const ticket = issued[0];
  assert.equal((await handleCommerce(db, ticket.userId, { action: 'reserveTicket', eventId: 'concert' })).ticket.code, ticket.code);
  const entries = await Promise.all(Array.from({length: 5}, () => handleCommerce(db, 'owner', { action: 'checkTicket', code: ticket.code })));
  assert.equal(entries.filter(entry => entry.status === 'accepted').length, 1);
  assert.equal((await db.doc('ticketbulkEvents/concert').get()).get('checkedIn'), 1);
  const mine = await handleCommerce(db, ticket.userId, { action: 'myTickets' });
  assert.equal(mine.tickets.length, 1);
  const unused = issued[1];
  await assert.rejects(handleCommerce(db, 'owner', { action: 'checkTicket', eventId: 'other-event', code: unused.code }), { code: 'failed-precondition' });
  await Promise.all(Array.from({length: 4}, () => handleCommerce(db, unused.userId, { action: 'cancelTicket', code: unused.code })));
  assert.equal((await db.doc('ticketbulkEvents/concert').get()).get('reserved'), 2);
  await assert.rejects(handleCommerce(db, 'owner', { action: 'checkTicket', code: unused.code }), { code: 'failed-precondition' });
  const reissued = (await handleCommerce(db, unused.userId, { action: 'reserveTicket', eventId: 'concert' })).ticket;
  assert.notEqual(reissued.code, unused.code);
  assert.equal((await db.doc('ticketbulkEvents/concert').get()).get('reserved'), 3);
  await assert.rejects(handleCommerce(db, unused.userId, { action: 'cancelTicket', code: unused.code }), { code: 'failed-precondition' });
  // Even a signed-in native client cannot bypass the callable to read QR tokens.
  const encoded = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const token = `${encoded({alg:'none',typ:'JWT'})}.${encoded({sub:ticket.userId,user_id:ticket.userId,aud:'demo-wapi-commerce',iss:'https://securetoken.google.com/demo-wapi-commerce',iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,firebase:{sign_in_provider:'custom'}})}.`;
  for (const collection of ['ticketbulkTickets', 'ticketbulkEvents', 'businessCatalog', 'businessStorefronts']) {
    const response = await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-wapi-commerce/databases/(default)/documents/${collection}`, { headers: { Authorization: `Bearer ${token}` } });
    assert.equal(response.status, 403, `Direct client access to ${collection} must be denied`);
  }
  console.log('PASS: real Firestore transactions, filtered directory across 103 businesses / 62 matches without skips, catalog, 8 reservations/3 seats, 5 scans/1 entry, 4 cancellations/1 released seat, reissue revokes old QR, wrong event rejected, 4 rules denials.');
  await db.terminate();
}
run().catch(error => { console.error(error); process.exitCode = 1; });
