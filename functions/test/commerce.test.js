const { test } = require('node:test');
const assert = require('node:assert/strict');
const { handleCommerce, validateEvent, integer, parseTicketCode, reservationID, catalogID, normalizeSearch, saveCatalogPhoto } = require('../lib/commerce');

// Transactional in-memory adapter, never a production Firebase project.
function database(initial) {
  const records = new Map(Object.entries(initial));
  let queue = Promise.resolve();
  const snapshot = path => ({ exists: records.has(path), id: path.split('/').pop(), get: key => records.get(path)?.[key], data: () => records.get(path) });
  const reference = path => ({ path, id: path.split('/').pop(), get: async () => snapshot(path), set: async data => records.set(path, data), delete: async () => records.delete(path) });
  const query = (name, filters = [], limit = 1000, cursor = '') => ({
    doc: key => reference(`${name}/${key}`), where: (key, op, value) => query(name, [...filters, [key, value]], limit, cursor),
    orderBy: () => query(name, filters, limit, cursor), limit: count => query(name, filters, count, cursor), startAfter: id => query(name, filters, limit, id),
    get: async () => { const docs = [...records.keys()].filter(path => path.startsWith(`${name}/`) && path.split('/').pop() > cursor && filters.every(([key, value]) => records.get(path)[key] === value)).sort().slice(0, limit).map(snapshot); return { docs, size: docs.length, empty: docs.length === 0 }; },
  });
  return {
    records, collection: name => query(name), getAll: async (...refs) => refs.map(ref => snapshot(ref.path)),
    runTransaction: work => {
      const run = queue.then(async () => {
        const changes = [];
        const tx = { get: async ref => snapshot(ref.path), create: (ref, data) => changes.push([ref, data, false]), update: (ref, data) => changes.push([ref, data, true]) };
        const result = await work(tx);
        for (const [ref, data, update] of changes) {
          const next = update ? { ...records.get(ref.path) } : {};
          for (const [key, value] of Object.entries(data)) next[key] = value?.constructor?.name === 'NumericIncrementTransform' ? (next[key] || 0) + value.operand : value;
          records.set(ref.path, next);
        }
        return result;
      });
      queue = run.catch(() => {}); return run;
    },
  };
}
const event = { title: 'Rencontre WAPI', venue: 'Brazzaville', ownerId: 'organizer', status: 'published', startsAt: Date.now() + 86400000, capacity: 1, reserved: 0, checkedIn: 0 };
test('strict event validation rejects past dates, fractional capacity and missing fields', () => {
  const now = Date.now();
  assert.equal(validateEvent({ title: ' Concert ', venue: 'Salle', startsAt: now + 86400000, capacity: 50 }, now).title, 'Concert');
  for (const capacity of [0, -1, 1.4, Infinity, '5', 10001]) assert.throws(() => integer(capacity, 1, 10000));
  assert.throws(() => validateEvent({ title: 'Concert', venue: 'Salle', startsAt: now - 1, capacity: 10 }, now));
});
test('directory search ignores case and accents', () => assert.equal(normalizeSearch(' CAFÉ '), 'cafe'));
test('catalog references cannot collide across business pages', () => assert.notEqual(catalogID('a_b', 'c'), catalogID('a', 'b_c')));
test('opaque ticket codes reject arbitrary URLs and paths', () => {
  assert.throws(() => parseTicketCode('https://attacker.test/ticketbulk/abc'));
  assert.throws(() => parseTicketCode('wapi://ticketbulk/../../abc?token=bad'));
  assert.notEqual(reservationID('one', 'a'), reservationID('one', 'b'));
});
test('parallel reservations cannot oversell and retries return the same ticket', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event } });
  const results = await Promise.allSettled(['a', 'b', 'c'].map(uid => handleCommerce(db, uid, { action: 'reserveTicket', eventId: 'e1' })));
  assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(db.records.get('ticketbulkEvents/e1').reserved, 1);
  const first = results[0].value.ticket;
  const retry = (await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' })).ticket;
  assert.equal(first.code, retry.code);
  assert.equal(db.records.get('ticketbulkEvents/e1').reserved, 1);
});
test('only organizer consumes a QR and concurrent rescans count one entry', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event } });
  const { ticket } = await handleCommerce(db, 'visitor', { action: 'reserveTicket', eventId: 'e1' });
  await assert.rejects(handleCommerce(db, 'visitor', { action: 'checkTicket', code: ticket.code }), { code: 'permission-denied' });
  await assert.rejects(handleCommerce(db, 'organizer', { action: 'checkTicket', code: ticket.code.replace(/token=.*/, 'token=00000000-0000-0000-0000-000000000000') }), { code: 'permission-denied' });
  const result = await Promise.all([1, 2].map(() => handleCommerce(db, 'organizer', { action: 'checkTicket', code: ticket.code })));
  assert.deepEqual(result.map(r => r.status), ['accepted', 'already-used']);
  assert.equal(db.records.get('ticketbulkEvents/e1').checkedIn, 1);
});
test('my tickets never expose another visitor token', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event, capacity: 4 } });
  await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' });
  await handleCommerce(db, 'b', { action: 'reserveTicket', eventId: 'e1' });
  const result = await handleCommerce(db, 'a', { action: 'myTickets' });
  assert.equal(result.tickets.length, 1); assert.equal(result.tickets[0].userId, 'a');
});
test('business ownership is required for catalog and event changes', async () => {
  const db = database({ 'businessPages/b1': { ownerId: 'owner', name: 'Café', status: 'active' } });
  for (const action of ['saveStorefront', 'saveProduct', 'createEvent']) {
    await assert.rejects(handleCommerce(db, 'intruder', { action, pageId: 'b1', productId: 'p1', eventId: 'e1' }), { code: 'permission-denied' });
  }
  await assert.rejects(handleCommerce(db, 'intruder', { action: 'storefront', pageId: 'b1' }), { code: 'not-found' });
});
test('only an active Business page can publish a marketplace listing', async () => {
  const db = database({ 'businessPages/b1': { ownerId: 'owner', name: 'Atelier WAPI', city: 'Brazzaville', status: 'active' } });
  await assert.rejects(handleCommerce(db, 'visitor', { action: 'createMarketplaceListing', listingId: 'listing1', pageId: 'b1', title: 'Lampe artisanale', category: 'Maison' }), { code: 'permission-denied' });
  await assert.rejects(handleCommerce(db, 'owner', { action: 'createMarketplaceListing', listingId: 'listing1', pageId: 'b1', title: 'Lampe artisanale', category: 'Maison', mode: 'trade' }), { code: 'invalid-argument' });
  const created = await handleCommerce(db, 'owner', { action: 'createMarketplaceListing', listingId: 'listing1', pageId: 'b1', title: 'Lampe artisanale', category: 'Maison', mode: 'auction', priceText: '20 000 XAF', description: 'Pièce unique', place: 'Poto-Poto' });
  assert.equal(created.listing.pageName, 'Atelier WAPI');
  assert.equal(created.listing.mode, 'auction');
  assert.equal((await handleCommerce(db, 'visitor', { action: 'marketplaceListings' })).listings.length, 1);
});
test('marketplace responses are private, validated and cannot target the author', async () => {
  const db = database({ 'marketplaceListings/l1': { ownerId: 'owner', pageId: 'b1', pageName: 'Atelier', title: 'Canapé', status: 'active' } });
  await assert.rejects(handleCommerce(db, 'owner', { action: 'respondToMarketplaceListing', listingId: 'l1', kind: 'offer', offerText: 'Je propose 100' }), { code: 'failed-precondition' });
  await assert.rejects(handleCommerce(db, 'visitor', { action: 'respondToMarketplaceListing', listingId: 'l1', kind: 'bid' }), { code: 'invalid-argument' });
  const response = await handleCommerce(db, 'visitor', { action: 'respondToMarketplaceListing', listingId: 'l1', kind: 'trade', offerText: 'Vélo + complément' });
  assert.equal(response.intent.kind, 'trade');
  assert.equal([...db.records.keys()].filter(key => key.startsWith('marketplaceListingIntents/')).length, 1);
});
test('published directory resolves current business identity, not a stale copy', async () => {
  const db = database({ 'businessPages/b1': { ownerId: 'owner', name: 'Nouveau Café', city: 'Lomé', category: 'Restaurant', status: 'active' }, 'businessStorefronts/b1': { published: true, name: 'Old name' } });
  const result = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe', city: 'lome' });
  assert.equal(result.stores.length, 1); assert.equal(result.stores[0].name, 'Nouveau Café');
});
test('reopening business contact does not erase receipts', async () => {
  const db = database({ 'businessPages/b1': { ownerId: 'owner', name: 'Café', status: 'active' }, 'businessStorefronts/b1': { published: true }, 'conversations/business-b1-visitor': { readBy: { owner: 900 }, lastMessage: 'Bonjour' } });
  await handleCommerce(db, 'visitor', { action: 'contactStorefront', pageId: 'b1' });
  assert.equal(db.records.get('conversations/business-b1-visitor').readBy.owner, 900);
});
test('cancelling a reservation releases one place once and rejects its QR', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event } });
  const { ticket } = await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' });
  await assert.rejects(handleCommerce(db, 'b', { action: 'cancelTicket', code: ticket.code }), { code: 'permission-denied' });
  await Promise.all([1, 2, 3].map(() => handleCommerce(db, 'a', { action: 'cancelTicket', code: ticket.code })));
  assert.equal(db.records.get('ticketbulkEvents/e1').reserved, 0);
  await assert.rejects(handleCommerce(db, 'organizer', { action: 'checkTicket', code: ticket.code }), { code: 'failed-precondition' });
  const reissued = (await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' })).ticket;
  assert.notEqual(reissued.code, ticket.code);
  await assert.rejects(handleCommerce(db, 'a', { action: 'cancelTicket', code: ticket.code }), { code: 'failed-precondition' });
  assert.equal(db.records.get('ticketbulkEvents/e1').reserved, 1);
});
test('scanner rejects another event from the same organizer without consuming entry', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event } });
  const { ticket } = await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' });
  await assert.rejects(handleCommerce(db, 'organizer', { action: 'checkTicket', eventId: 'e2', code: ticket.code }), { code: 'failed-precondition' });
  assert.equal(db.records.get('ticketbulkEvents/e1').checkedIn, 0);
  await handleCommerce(db, 'organizer', { action: 'checkTicket', eventId: 'e1', code: ticket.code });
  await assert.rejects(handleCommerce(db, 'a', { action: 'cancelTicket', code: ticket.code }), { code: 'failed-precondition' });
});
test('simultaneous cancellation and scan cannot both succeed', async () => {
  for (const cancelFirst of [true, false]) {
    const db = database({ 'ticketbulkEvents/e1': { ...event } });
    const { ticket } = await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' });
    const operations = [() => handleCommerce(db, 'a', { action: 'cancelTicket', code: ticket.code }), () => handleCommerce(db, 'organizer', { action: 'checkTicket', code: ticket.code })];
    const results = await Promise.allSettled((cancelFirst ? operations : operations.reverse()).map(work => work()));
    assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
    assert.equal(db.records.get('ticketbulkEvents/e1').reserved, cancelFirst ? 0 : 1);
  }
});
test('event list exposes only my reservation status, never anyone’s QR', async () => {
  const db = database({ 'ticketbulkEvents/e1': { ...event } });
  await handleCommerce(db, 'a', { action: 'reserveTicket', eventId: 'e1' });
  assert.equal((await handleCommerce(db, 'a', { action: 'events' })).events[0].myTicketStatus, 'issued');
  const other = await handleCommerce(db, 'b', { action: 'events' });
  assert.equal(other.events[0].myTicketStatus, null);
  assert.equal(JSON.stringify(other).includes('token'), false);
});

test('only a business owner can issue catalogue-backed invoices and create reviewable reminders', async () => {
  const pageId = 'cafe'; const productId = 'plat'; const invoiceId = 'invoice-1';
  const productPath = `businessCatalog/${catalogID(pageId, productId)}`;
  const db = database({
    [`businessPages/${pageId}`]: { ownerId: 'owner', name: 'Café WAPI', status: 'active' },
    [productPath]: { ownerId: 'owner', pageId, productId, name: 'Menu du jour', category: 'Menu', priceMinor: 5500, currency: 'XAF', available: true },
  });
  const dueAt = Date.now() + 7 * 86400000;
  await assert.rejects(handleCommerce(db, 'intruder', { action: 'createInvoice', pageId, invoiceId, customerName: 'Client', dueAt, items: [{ productId, quantity: 2 }] }), { code: 'permission-denied' });
  const created = await handleCommerce(db, 'owner', { action: 'createInvoice', pageId, invoiceId, customerName: 'Client', note: 'Livraison incluse', dueAt, items: [{ productId, quantity: 2, unitPriceMinor: 1 }] });
  assert.equal(created.invoice.totalMinor, 11000); // Client input can never alter the catalogue price.
  const billing = await handleCommerce(db, 'owner', { action: 'billing', pageId });
  assert.equal(billing.invoices.length, 1);
  assert.equal(billing.summary.outstandingMinor, 11000);
  const reminder = await handleCommerce(db, 'owner', { action: 'prepareInvoiceReminder', invoiceId, tone: 'courtois' });
  assert.equal(reminder.reminder.requiresHumanApproval, true);
  assert.equal(reminder.reminder.status, 'draft');
  assert.equal([...db.records.keys()].some(key => key.startsWith('invoiceReminderDrafts/')), true);
});

function directoryRecords(count, matching) {
  const records = {};
  for (let n = 0; n < count; n++) {
    const key = `b${String(n).padStart(3, '0')}`;
    records[`businessStorefronts/${key}`] = { published: true };
    records[`businessPages/${key}`] = { ownerId: 'owner', status: 'active', name: matching(n) ? 'Café' : 'Atelier', city: 'Lomé' };
  }
  return records;
}
test('directory finds matches beyond the first batch and keeps exact pagination cursor', async () => {
  const db = database(directoryRecords(103, n => n >= 41));
  const first = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe', city: 'lome' });
  assert.equal(first.stores.length, 40);
  assert.equal(first.stores[0].id, 'b041');
  assert.equal(first.nextCursor, 'b080'); // Do not skip the unread tail of batch 3.
  const second = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe', cursor: first.nextCursor });
  assert.equal(second.stores.length, 22);
  assert.equal(second.stores[0].id, 'b081');
  assert.equal(second.nextCursor, null);
  assert.equal(new Set([...first.stores, ...second.stores].map(row => row.id)).size, 62);
});
test('directory scan is bounded to 200 candidates, with a resumable cursor', async () => {
  const db = database(directoryRecords(203, n => n >= 200));
  const first = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe' });
  assert.equal(first.stores.length, 0);
  assert.equal(first.nextCursor, 'b199');
  const second = await handleCommerce(db, 'visitor', { action: 'directory', search: 'cafe', cursor: first.nextCursor });
  assert.equal(second.stores.length, 3);
  assert.equal(second.nextCursor, null);
});
test('directory never returns missing, disabled or unpublished businesses', async () => {
  const initial = directoryRecords(4, () => true);
  initial['businessPages/b000'].status = 'suspended';
  delete initial['businessPages/b001'];
  initial['businessStorefronts/b002'].published = false;
  const result = await handleCommerce(database(initial), 'visitor', { action: 'directory' });
  assert.deepEqual(result.stores.map(row => row.id), ['b003']);
  assert.equal(result.nextCursor, null);
});

function imageBucket() {
  const objects = new Map();
  let writes = 0;
  return { name: 'local-test', objects, get writes() { return writes; }, file: path => ({
    save: async (bytes, options) => {
      assert.equal(options.preconditionOpts.ifGenerationMatch, 0);
      if (objects.has(path)) throw Object.assign(new Error('Precondition failed'), { code: 412 });
      writes++; objects.set(path, { bytes, ...options.metadata });
    },
    getMetadata: async () => [objects.get(path)],
  }) };
}
const catalogPhoto = { bytes: Buffer.from('test-image'), contentType: 'image/jpeg', extension: 'jpg' };
test('parallel image saves and retries preserve one object and one download URL', async () => {
  const bucket = imageBucket();
  const urls = await Promise.all(Array.from({ length: 8 }, () => saveCatalogPhoto(bucket, 'businessCatalog/owner/p1/a/hash.jpg', catalogPhoto)));
  assert.equal(bucket.writes, 1);
  assert.equal(new Set(urls).size, 1);
  assert.equal(await saveCatalogPhoto(bucket, 'businessCatalog/owner/p1/a/hash.jpg', catalogPhoto), urls[0]);
  assert.equal(bucket.writes, 1);
});
test('new image path keeps the previous photo untouched', async () => {
  const bucket = imageBucket();
  const first = await saveCatalogPhoto(bucket, 'image-1.jpg', catalogPhoto);
  const oldObject = bucket.objects.get('image-1.jpg');
  const second = await saveCatalogPhoto(bucket, 'image-2.jpg', catalogPhoto);
  assert.notEqual(first, second);
  assert.strictEqual(bucket.objects.get('image-1.jpg'), oldObject);
});
test('image save errors do not masquerade as success or overwrite inaccessible objects', async () => {
  for (const code of [403, 503]) {
    let reads = 0;
    const bucket = { name: 'test', file: () => ({ save: async () => { throw Object.assign(new Error('failed'), { code }); }, getMetadata: async () => { reads++; return []; } }) };
    await assert.rejects(saveCatalogPhoto(bucket, 'p.jpg', catalogPhoto), { code });
    assert.equal(reads, 0);
  }
  const bucket = imageBucket();
  bucket.objects.set('p.jpg', { metadata: {} });
  await assert.rejects(saveCatalogPhoto(bucket, 'p.jpg', catalogPhoto), { code: 'failed-precondition' });
  assert.equal(bucket.writes, 0);
});
