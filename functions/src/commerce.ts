import { FieldPath, FieldValue, type Firestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { HttpsError } from "firebase-functions/v2/https";
import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { decodeGroupPhotoBase64, type DecodedGroupPhoto } from "./groupPhoto";

type Input = Record<string, unknown>;
const fail = (message: string): never => { throw new HttpsError("invalid-argument", message); };
export function text(value: unknown, max: number, required = false): string {
  if (typeof value !== "string" || value.length > max) return fail("Vérifiez les champs saisis.");
  const result = value.trim();
  if (required && !result) return fail("Complétez les champs obligatoires.");
  return result;
}
function id(value: unknown): string {
  const result = text(value, 128, true);
  if (!/^[A-Za-z0-9_-]+$/.test(result)) return fail("Référence invalide.");
  return result;
}
export function integer(value: unknown, min: number, max: number): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < min || value > max) return fail("Nombre invalide.");
  return value;
}
export function normalizeSearch(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase().trim();
}
const supportedCurrencies = ["XAF", "XOF", "EUR", "USD", "CDF"] as const;
type SupportedCurrency = typeof supportedCurrencies[number];
function currency(value: unknown): SupportedCurrency {
  const result = text(value ?? "XAF", 3, true) as SupportedCurrency;
  if (!supportedCurrencies.includes(result)) return fail("Devise non prise en charge.");
  return result;
}
function invoiceStatus(invoice: Input, now: number): "issued" | "overdue" | "cancelled" {
  if (invoice.status === "cancelled") return "cancelled";
  return Number(invoice.dueAt) < now ? "overdue" : "issued";
}
function invoiceNumber(invoiceId: string, now: number): string {
  return `WAPI-${new Date(now).getUTCFullYear()}-${invoiceId.replace(/-/g, "").slice(-8).toUpperCase()}`;
}
function invoiceLineInput(value: unknown): { productId: string; quantity: number } {
  if (!value || typeof value !== "object" || Array.isArray(value)) return fail("Ajoutez au moins un article à la facture.");
  const item = value as Input;
  return { productId: id(item.productId), quantity: integer(item.quantity, 1, 999) };
}
function publicInvoice(id: string, invoice: Input, now: number) {
  const status = invoiceStatus(invoice, now);
  return {
    id,
    number: String(invoice.number || "WAPI"),
    pageId: String(invoice.pageId || ""),
    pageName: String(invoice.pageName || ""),
    customerName: String(invoice.customerName || "Client WAPI"),
    customerUserId: String(invoice.customerUserId || ""),
    currency: String(invoice.currency || "XAF"),
    totalMinor: Number(invoice.totalMinor || 0),
    balanceMinor: Number(invoice.balanceMinor || 0),
    dueAt: Number(invoice.dueAt || 0),
    issuedAt: Number(invoice.issuedAt || 0),
    status,
    note: String(invoice.note || ""),
    lineItems: Array.isArray(invoice.lineItems) ? invoice.lineItems : [],
  };
}
export function validateEvent(data: Input, now: number) {
  return { title: text(data.title, 100, true), venue: text(data.venue, 160, true),
    description: text(data.description ?? "", 1200),
    startsAt: integer(data.startsAt, now + 60_000, now + 730 * 86_400_000),
    capacity: integer(data.capacity, 1, 10000), priceMinor: 0, currency: "XAF" };
}
export function reservationID(eventId: string, uid: string): string {
  return createHash("sha256").update(`${eventId}:${uid}`).digest("hex");
}
export function catalogID(pageId: string, productId: string): string {
  // Delimiter concatenation lets (a_b, c) collide with (a, b_c).
  return createHash("sha256").update(JSON.stringify([pageId, productId])).digest("hex");
}
export function canReserve(event: Input, now: number) {
  if (event.status !== "published" || Number(event.startsAt) <= now) throw new HttpsError("failed-precondition", "Les inscriptions sont fermées.");
  if (Number(event.reserved) >= Number(event.capacity)) throw new HttpsError("resource-exhausted", "Cet événement est complet.");
}
export function parseTicketCode(raw: unknown): { ticketId: string; token: string } {
  const value = text(raw, 500, true);
  try {
    const url = new URL(value);
    if (url.protocol !== "wapi:" || url.hostname !== "ticketbulk") return fail("Ce code n’est pas un billet Ticketbulk.");
    const ticketId = id(url.pathname.slice(1));
    const token = text(url.searchParams.get("token"), 64, true);
    if (!/^[a-f0-9-]{36}$/.test(token)) return fail("Code de billet invalide.");
    return { ticketId, token };
  } catch { return fail("Code de billet invalide."); }
}
function publicPage(pageId: string, page: Input, settings: Input = {}) {
  return { id: pageId, name: page.name || "", logoUrl: page.logoUrl || "", category: page.category || "",
    city: page.city || "", bio: page.bio || "", phone: page.phone || "", ownerId: page.ownerId || "",
    handle: page.handle || "", verified: page.verified === true,
    address: settings.address || "", hours: settings.hours || "", published: settings.published === true };
}

const marketplaceModes = ["sale", "trade", "auction", "job", "service"] as const;
const marketplaceIntentKinds = ["offer", "trade", "bid", "apply", "message"] as const;
type MarketplaceMode = typeof marketplaceModes[number];
type MarketplaceIntentKind = typeof marketplaceIntentKinds[number];

function marketplaceMode(value: unknown): MarketplaceMode {
  const mode = text(value ?? "sale", 16, true) as MarketplaceMode;
  if (!marketplaceModes.includes(mode)) return fail("Type d’annonce non pris en charge.");
  return mode;
}

function marketplaceIntentKind(value: unknown): MarketplaceIntentKind {
  const kind = text(value, 16, true) as MarketplaceIntentKind;
  if (!marketplaceIntentKinds.includes(kind)) return fail("Action Marketplace non prise en charge.");
  return kind;
}

function listingPhotoPayloads(value: unknown): string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || value.length > 5) return fail("Ajoutez entre 1 et 5 photos valides.");
  return value.map((photo) => text(photo, 8_000_000, true));
}

function publicMarketplaceListing(listingId: string, listing: Input) {
  return {
    id: listingId,
    pageId: String(listing.pageId || ""),
    pageName: String(listing.pageName || "Business WAPI"),
    pagePhotoUrl: String(listing.pagePhotoUrl || ""),
    title: String(listing.title || "Annonce WAPI"),
    description: String(listing.description || ""),
    category: String(listing.category || "Autre"),
    mode: String(listing.mode || "sale"),
    priceText: String(listing.priceText || "Prix à discuter"),
    tradeWish: String(listing.tradeWish || ""),
    place: String(listing.place || ""),
    photoUrls: Array.isArray(listing.photoUrls) ? listing.photoUrls.map((url) => String(url)).filter(Boolean).slice(0, 5) : [],
    acceptsOffers: listing.acceptsOffers === true,
    boostStatus: String(listing.boostStatus || "none"),
    status: String(listing.status || "active"),
    createdAt: Number(listing.createdAt || 0),
  };
}

/** Retries and concurrent saves must keep the URL of an immutable photo valid. */
export async function saveCatalogPhoto(bucket: ReturnType<ReturnType<typeof getStorage>["bucket"]>, path: string, photo: DecodedGroupPhoto): Promise<string> {
  const file = bucket.file(path);
  let token: string = randomUUID();
  try {
    await file.save(photo.bytes, { resumable: false, preconditionOpts: { ifGenerationMatch: 0 },
      metadata: { contentType: photo.contentType, cacheControl: "public,max-age=31536000,immutable", metadata: { firebaseStorageDownloadTokens: token } } });
  } catch (error) {
    if (Number((error as { code?: unknown })?.code) !== 412) throw error;
    const [metadata] = await file.getMetadata();
    const existingToken = String(metadata.metadata?.firebaseStorageDownloadTokens ?? "").split(",").map(value => value.trim()).find(Boolean);
    if (!existingToken) throw new HttpsError("failed-precondition", "La photo existe mais son accès doit être rétabli. Aucune image n’a été remplacée.");
    token = existingToken;
  }
  return `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(path)}?alt=media&token=${encodeURIComponent(token)}`;
}

/** Authenticated native mini-app API. Clients never write counters or validate their own tickets. */
export async function handleCommerce(db: Firestore, uid: string, data: Input) {
  const action = text(data.action, 40, true);
  const ownPage = async (pageId: string) => {
    const snap = await db.collection("businessPages").doc(pageId).get();
    if (!snap.exists || snap.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Cette page ne vous appartient pas.");
    return snap;
  };
  if (action === "ownPages") {
    const docs = await db.collection("businessPages").where("ownerId", "==", uid).limit(50).get();
    return { pages: docs.docs.map(doc => publicPage(doc.id, doc.data())) };
  }
  if (action === "directory") {
    const search = normalizeSearch(text(data.search ?? "", 100));
    const city = normalizeSearch(text(data.city ?? "", 80));
    const stores: ReturnType<typeof publicPage>[] = [];
    let cursor = data.cursor ? id(data.cursor) : undefined;
    // Bounded scans prevent a filter from returning an empty first page simply
    // because its matches occur after the first 40 documents. Never scan unbounded.
    for (let batch = 0; batch < 5; batch++) {
      let query = db.collection("businessStorefronts").where("published", "==", true).orderBy(FieldPath.documentId()).limit(40);
      if (cursor) query = query.startAfter(cursor);
      const docs = await query.get();
      if (docs.empty) return { stores, nextCursor: null };
      const pages = await db.getAll(...docs.docs.map(doc => db.collection("businessPages").doc(doc.id)));
      for (let index = 0; index < docs.size; index++) {
        const settings = docs.docs[index];
        cursor = settings.id;
        const page = pages[index];
        if (page.exists && page.get("status") === "active") {
          const store = publicPage(page.id, page.data()!, settings.data());
          if (normalizeSearch(String(store.city)).includes(city) && normalizeSearch(`${store.name} ${store.category} ${store.bio}`).includes(search)) stores.push(store);
        }
        // Cursor is the last examined document, not the end of a partly read batch.
        if (stores.length === 40) return { stores, nextCursor: index === docs.size - 1 && docs.size < 40 ? null : cursor };
      }
      if (docs.size < 40) return { stores, nextCursor: null };
    }
    return { stores, nextCursor: cursor };
  }
  if (action === "marketplaceListings") {
    const search = normalizeSearch(text(data.search ?? "", 100));
    const city = normalizeSearch(text(data.city ?? "", 80));
    const docs = await db.collection("marketplaceListings").orderBy("updatedAt", "desc").limit(100).get();
    const listings = docs.docs
      .map((document) => publicMarketplaceListing(document.id, document.data()))
      .filter((listing) => listing.status === "active")
      .filter((listing) => !search || normalizeSearch(`${listing.title} ${listing.description} ${listing.category} ${listing.pageName}`).includes(search))
      .filter((listing) => !city || normalizeSearch(listing.place).includes(city));
    return { listings };
  }
  if (action === "createMarketplaceListing") {
    const pageId = id(data.pageId);
    const page = await ownPage(pageId);
    if (page.get("status") !== "active") throw new HttpsError("failed-precondition", "Terminez l’activation de votre compte Business avant de publier.");
    const listingId = id(data.listingId);
    const mode = marketplaceMode(data.mode);
    const title = text(data.title, 120, true);
    const description = text(data.description ?? "", 1_200);
    const priceText = text(data.priceText ?? "", 120) || "Prix à discuter";
    const tradeWish = text(data.tradeWish ?? "", 180);
    const place = text(data.place ?? "", 120) || String(page.get("city") || "").slice(0, 120);
    const category = text(data.category ?? "Autre", 60, true);
    const photoPayloads = listingPhotoPayloads(data.photoBase64s);
    if (mode === "trade" && !tradeWish) return fail("Indiquez ce que vous acceptez en échange.");
    if (mode === "job" && priceText !== "Prix à discuter") return fail("Une annonce emploi ne doit pas afficher un prix de vente.");
    const reference = db.collection("marketplaceListings").doc(listingId);
    const existing = await reference.get();
    if (existing.exists && existing.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Cette référence d’annonce est déjà utilisée.");
    const photoUrls: string[] = [];
    if (photoPayloads.length) {
      const bucket = getStorage().bucket();
      for (const [index, payload] of photoPayloads.entries()) {
        let photo;
        try { photo = decodeGroupPhotoBase64(payload); } catch { return fail("Choisissez des photos JPEG, PNG ou WebP de moins de 5 Mo."); }
        if (!photo) continue;
        const hash = createHash("sha256").update(photo.bytes).digest("hex");
        photoUrls.push(await saveCatalogPhoto(bucket, `marketplaceListings/${uid}/${pageId}/${listingId}/${index}-${hash}.${photo.extension}`, photo));
      }
    }
    const now = Date.now();
    const previousPhotos = Array.isArray(existing.get("photoUrls")) ? existing.get("photoUrls").map(String).slice(0, 5) : [];
    const document = {
      ownerId: uid, pageId, pageName: String(page.get("name") || "Business WAPI").slice(0, 100), pagePhotoUrl: String(page.get("logoUrl") || "").slice(0, 2_000),
      title, description, category, mode, priceText, tradeWish, place, acceptsOffers: data.acceptsOffers !== false,
      photoUrls: photoUrls.length ? photoUrls : previousPhotos, status: "active", boostStatus: String(existing.get("boostStatus") || "none"),
      createdAt: Number(existing.get("createdAt") || now), updatedAt: now,
    };
    await reference.set(document, { merge: true });
    return { listing: publicMarketplaceListing(reference.id, document) };
  }
  if (action === "respondToMarketplaceListing") {
    const listingId = id(data.listingId);
    const listing = await db.collection("marketplaceListings").doc(listingId).get();
    if (!listing.exists || listing.get("status") !== "active") throw new HttpsError("not-found", "Cette annonce n’est plus disponible.");
    if (listing.get("ownerId") === uid) throw new HttpsError("failed-precondition", "Vous ne pouvez pas répondre à votre propre annonce.");
    const kind = marketplaceIntentKind(data.kind);
    const note = text(data.note ?? "", 800);
    const offerText = text(data.offerText ?? "", 180);
    if (["offer", "trade", "bid", "apply"].includes(kind) && !offerText) return fail("Précisez votre proposition avant de l’envoyer.");
    const reference = db.collection("marketplaceListingIntents").doc();
    await reference.set({ listingId, listingOwnerId: listing.get("ownerId"), pageId: listing.get("pageId"), responderId: uid,
      kind, note, offerText, status: "new", createdAt: FieldValue.serverTimestamp() });
    return { intent: { id: reference.id, status: "new", kind }, contactPageId: String(listing.get("pageId") || "") };
  }
  if (action === "boostMarketplaceListing") {
    const listingId = id(data.listingId);
    const listing = await db.collection("marketplaceListings").doc(listingId).get();
    if (!listing.exists || listing.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Cette annonce ne vous appartient pas.");
    const region = text(data.region ?? "", 80) || "Zone de la page Business";
    const objective = text(data.objective ?? "visibility", 20, true);
    if (!["visibility", "messages", "sales"].includes(objective)) return fail("Objectif de boost invalide.");
    const request = db.collection("marketplaceBoostRequests").doc();
    // Payment is deliberately not initiated here. Regional Mobile Money must
    // be configured and verified before a campaign can be activated.
    await request.set({ listingId, ownerId: uid, pageId: listing.get("pageId"), region, objective,
      status: "awaiting_payment_configuration", createdAt: FieldValue.serverTimestamp() });
    await listing.ref.update({ boostStatus: "awaiting_payment_configuration", updatedAt: Date.now() });
    return { boost: { id: request.id, status: "awaiting_payment_configuration", region, objective },
      notice: "Votre plan de boost est prêt. Activez d’abord un moyen de paiement Mobile Money compatible avec votre région ; aucun paiement n’a été déclenché." };
  }
  if (action === "storefront") {
    const pageId = id(data.pageId);
    const [page, settings] = await Promise.all([db.collection("businessPages").doc(pageId).get(), db.collection("businessStorefronts").doc(pageId).get()]);
    if (!page.exists) throw new HttpsError("not-found", "Établissement introuvable.");
    const owner = page.get("ownerId") === uid;
    if (!owner && (page.get("status") !== "active" || settings.get("published") !== true)) throw new HttpsError("not-found", "Cette vitrine n’est pas publiée.");
    const products = await db.collection("businessCatalog").where("pageId", "==", pageId).limit(200).get();
    return { page: publicPage(pageId, page.data()!, settings.data()), owner,
      products: products.docs.map(doc => ({ ...doc.data(), id: doc.id })).sort((a, b) => String((a as Input).category).localeCompare(String((b as Input).category))) };
  }
  if (action === "saveStorefront") {
    const pageId = id(data.pageId); await ownPage(pageId);
    if (typeof data.published !== "boolean") return fail("Choisissez la visibilité de la vitrine.");
    await db.collection("businessStorefronts").doc(pageId).set({ ownerId: uid, published: data.published,
      address: text(data.address, 160), hours: text(data.hours, 200), updatedAt: FieldValue.serverTimestamp() });
    return { saved: true };
  }
  if (action === "contactStorefront") {
    const pageId = id(data.pageId);
    const page = await db.collection("businessPages").doc(pageId).get();
    const settings = await db.collection("businessStorefronts").doc(pageId).get();
    if (!page.exists || page.get("status") !== "active" || settings.get("published") !== true) throw new HttpsError("not-found", "Cette vitrine n’est pas publiée.");
    const ownerId = String(page.get("ownerId"));
    if (uid === ownerId) throw new HttpsError("failed-precondition", "Cette page Business vous appartient.");
    const profiles = await db.getAll(db.collection("users").doc(uid), db.collection("users").doc(ownerId));
    const ref = db.collection("conversations").doc(`business-${pageId}-${uid}`);
    await db.runTransaction(async tx => {
      if ((await tx.get(ref)).exists) return; // Never reset receipts/history on re-entry.
      tx.create(ref, { ownerId: uid, memberIds: [uid, ownerId].sort(), members: profiles.map(profile => ({
        uid: profile.id, displayName: profile.get("displayName") || "Membre WAPI", phoneNumber: profile.get("phoneNumber") || "", photoUrl: profile.get("photoUrl") || "", verified: profile.get("verified") === true,
      })), profileType: "business", businessPageId: pageId, businessPageName: page.get("name"), businessPagePhotoUrl: page.get("logoUrl") || "", businessOwnerId: ownerId,
      typingBy: {}, readBy: {}, updatedAt: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
    });
    return { conversationId: ref.id };
  }
  if (action === "saveProduct") {
    const pageId = id(data.pageId); const productId = id(data.productId); await ownPage(pageId);
    const ref = db.collection("businessCatalog").doc(catalogID(pageId, productId));
    const old = await ref.get();
    const product = { pageId, productId, ownerId: uid, name: text(data.name, 100, true), description: text(data.description, 1000),
      category: text(data.category, 60, true), priceMinor: integer(data.priceMinor, 0, 100_000_000),
      currency: text(data.currency ?? "XAF", 3), available: data.available === true };
    if (!supportedCurrencies.includes(product.currency as SupportedCurrency)) return fail("Devise non prise en charge.");
    let imageUrl = String(old.get("imageUrl") || "");
    if (data.photoBase64) {
      let photo;
      try { photo = decodeGroupPhotoBase64(data.photoBase64); } catch { return fail("Choisissez une photo JPEG, PNG ou WebP de moins de 5 Mo."); }
      if (photo) {
        const bucket = getStorage().bucket();
        // Content-addressed path: retrying a save does not overwrite a cached image.
        const hash = createHash("sha256").update(photo.bytes).digest("hex");
        const path = `businessCatalog/${uid}/${pageId}/${productId}/${hash}.${photo.extension}`;
        imageUrl = await saveCatalogPhoto(bucket, path, photo);
      }
    }
    await ref.set({ ...product, imageUrl, updatedAt: FieldValue.serverTimestamp() });
    return { saved: true };
  }
  if (action === "deleteProduct") {
    const pageId = id(data.pageId); await ownPage(pageId);
    await db.collection("businessCatalog").doc(catalogID(pageId, id(data.productId))).delete();
    return { saved: true };
  }
  if (action === "createEvent") {
    const pageId = id(data.pageId); const page = await ownPage(pageId);
    const ref = db.collection("ticketbulkEvents").doc(id(data.eventId));
    const event = validateEvent(data, Date.now());
    await db.runTransaction(async tx => {
      const existing = await tx.get(ref);
      if (existing.exists) {
        if (existing.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Référence déjà utilisée.");
        return; // idempotent creation after a lost network response
      }
      tx.create(ref, { ...event, pageId, organizer: page.get("name"), ownerId: uid, status: "published", reserved: 0, checkedIn: 0, createdAt: FieldValue.serverTimestamp() });
    });
    return { eventId: ref.id };
  }
  if (action === "events") {
    let query = db.collection("ticketbulkEvents").orderBy(FieldPath.documentId()).limit(40);
    if (data.cursor) query = query.startAfter(id(data.cursor));
    const docs = await query.get();
    const ownTickets = docs.empty ? [] : await db.getAll(...docs.docs.map(doc => db.collection("ticketbulkTickets").doc(reservationID(doc.id, uid))));
    return { events: docs.docs.filter(doc => doc.get("ownerId") === uid || (doc.get("status") === "published" && doc.get("startsAt") > Date.now()))
      .map(doc => ({ ...doc.data(), id: doc.id, myTicketStatus: ownTickets.find(ticket => ticket.get("eventId") === doc.id)?.get("status") || null }))
      .sort((a, b) => Number((a as Input).startsAt) - Number((b as Input).startsAt)),
      nextCursor: docs.size === 40 ? docs.docs[39].id : null };
  }
  if (action === "closeEvent") {
    const ref = db.collection("ticketbulkEvents").doc(id(data.eventId));
    await db.runTransaction(async tx => {
      const event = await tx.get(ref);
      if (event.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Seul l’organisateur peut fermer les inscriptions.");
      tx.update(ref, { status: "closed" });
    });
    return { saved: true };
  }
  if (action === "reserveTicket") {
    const eventId = id(data.eventId);
    const eventRef = db.collection("ticketbulkEvents").doc(eventId);
    const ref = db.collection("ticketbulkTickets").doc(reservationID(eventId, uid));
    return db.runTransaction(async tx => {
      const [event, existing] = await Promise.all([tx.get(eventRef), tx.get(ref)]);
      if (existing.exists && existing.get("status") !== "cancelled") return { ticket: { ...existing.data(), id: ref.id } };
      if (!event.exists) throw new HttpsError("not-found", "Événement introuvable.");
      canReserve(event.data()!, Date.now());
      const token = randomUUID();
      const ticket = { eventId, userId: uid, ownerId: event.get("ownerId"), title: event.get("title"), venue: event.get("venue"), startsAt: event.get("startsAt"),
        status: "issued", token, code: `wapi://ticketbulk/${ref.id}?token=${token}`, issuedAt: Date.now() };
      if (existing.exists) tx.update(ref, { ...ticket, cancelledAt: null });
      else tx.create(ref, ticket);
      tx.update(eventRef, { reserved: FieldValue.increment(1) });
      return { ticket: { ...ticket, id: ref.id } };
    });
  }
  if (action === "myTickets") {
    const docs = await db.collection("ticketbulkTickets").where("userId", "==", uid).limit(200).get();
    return { tickets: docs.docs.map(doc => ({ ...doc.data(), id: doc.id })).sort((a, b) => Number((b as Input).startsAt) - Number((a as Input).startsAt)) };
  }
  if (action === "cancelTicket") {
    const { ticketId, token } = parseTicketCode(data.code);
    const ref = db.collection("ticketbulkTickets").doc(ticketId);
    return db.runTransaction(async tx => {
      const ticket = await tx.get(ref);
      if (!ticket.exists || ticket.get("userId") !== uid) throw new HttpsError("permission-denied", "Ce billet ne vous appartient pas.");
      // Bind cancellation to this issuance, never a later reservation with the same ID.
      if (ticket.get("token") !== token) throw new HttpsError("failed-precondition", "Ce billet a été remplacé. Actualisez vos billets.");
      if (ticket.get("status") === "cancelled") return { cancelled: true };
      if (ticket.get("status") !== "issued" || Number(ticket.get("startsAt")) <= Date.now()) throw new HttpsError("failed-precondition", "Ce billet ne peut plus être annulé.");
      const eventRef = db.collection("ticketbulkEvents").doc(ticket.get("eventId"));
      const event = await tx.get(eventRef);
      if (!event.exists || Number(event.get("reserved")) < 1) throw new HttpsError("failed-precondition", "Actualisez l’événement avant de réessayer.");
      tx.update(ref, { status: "cancelled", cancelledAt: Date.now() });
      tx.update(eventRef, { reserved: FieldValue.increment(-1) });
      return { cancelled: true };
    });
  }
  if (action === "checkTicket") {
    const { ticketId, token } = parseTicketCode(data.code);
    const ref = db.collection("ticketbulkTickets").doc(ticketId);
    return db.runTransaction(async tx => {
      const ticket = await tx.get(ref);
      if (!ticket.exists || ticket.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Billet invalide ou réservé à un autre organisateur.");
      if (data.eventId && ticket.get("eventId") !== id(data.eventId)) throw new HttpsError("failed-precondition", "Ce billet concerne un autre événement. Aucune entrée validée.");
      const stored = Buffer.from(String(ticket.get("token")));
      const supplied = Buffer.from(token);
      if (stored.length !== supplied.length || !timingSafeEqual(stored, supplied)) throw new HttpsError("permission-denied", "Code de billet invalide.");
      if (ticket.get("status") === "used") return { status: "already-used", title: ticket.get("title") };
      if (ticket.get("status") !== "issued") throw new HttpsError("failed-precondition", "Ce billet a été annulé. Entrée refusée.");
      const eventRef = db.collection("ticketbulkEvents").doc(ticket.get("eventId"));
      const event = await tx.get(eventRef);
      if (!event.exists) throw new HttpsError("not-found", "Événement introuvable.");
      tx.update(ref, { status: "used", checkedInAt: Date.now() });
      tx.update(eventRef, { checkedIn: FieldValue.increment(1) });
      return { status: "accepted", title: ticket.get("title") };
    });
  }
  if (action === "createInvoice") {
    const pageId = id(data.pageId);
    const page = await ownPage(pageId);
    const invoiceId = id(data.invoiceId);
    const customerName = text(data.customerName, 100, true);
    const customerUserId = data.customerUserId ? id(data.customerUserId) : "";
    const note = text(data.note ?? "", 800);
    const now = Date.now();
    const dueAt = integer(data.dueAt, now + 60_000, now + 365 * 86_400_000);
    if (!Array.isArray(data.items) || data.items.length < 1 || data.items.length > 30) return fail("Ajoutez entre 1 et 30 articles à la facture.");
    const requested = data.items.map(invoiceLineInput);
    if (new Set(requested.map(item => item.productId)).size !== requested.length) return fail("Un article ne peut apparaître qu’une fois dans la facture.");
    const references = requested.map(item => db.collection("businessCatalog").doc(catalogID(pageId, item.productId)));
    const products = await db.getAll(...references);
    const lines = requested.map((item, index) => {
      const product = products[index];
      if (!product.exists || product.get("ownerId") !== uid || product.get("available") !== true) throw new HttpsError("failed-precondition", "Un article sélectionné n’est plus disponible dans votre catalogue.");
      return {
        productId: item.productId,
        name: text(product.get("name"), 100, true),
        category: text(product.get("category"), 60, true),
        quantity: item.quantity,
        unitPriceMinor: integer(product.get("priceMinor"), 0, 100_000_000),
        currency: currency(product.get("currency")),
      };
    });
    const invoiceCurrency = lines[0].currency;
    if (lines.some(line => line.currency !== invoiceCurrency)) return fail("Une facture doit utiliser une seule devise.");
    const lineItems = lines.map(line => ({ ...line, totalMinor: line.unitPriceMinor * line.quantity }));
    const totalMinor = lineItems.reduce((total, line) => total + line.totalMinor, 0);
    if (!Number.isSafeInteger(totalMinor) || totalMinor > 100_000_000) return fail("Montant total invalide.");
    const reference = db.collection("marketplaceInvoices").doc(invoiceId);
    await db.runTransaction(async tx => {
      const existing = await tx.get(reference);
      if (existing.exists) {
        if (existing.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Référence de facture déjà utilisée.");
        return; // Idempotent retry after an interrupted mobile connection.
      }
      tx.create(reference, {
        ownerId: uid, pageId, pageName: String(page.get("name") || "Business WAPI").slice(0, 100),
        customerName, customerUserId, note, lineItems, currency: invoiceCurrency,
        totalMinor, balanceMinor: totalMinor, dueAt, issuedAt: now, status: "issued",
        number: invoiceNumber(invoiceId, now), createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
    });
    const created = await reference.get();
    return { invoice: publicInvoice(reference.id, created.data() || {}, now), paymentRequired: true,
      paymentNotice: "Cette facture est émise, mais aucun encaissement n’est effectué par WAPI sans prestataire de paiement et validation comptable." };
  }
  if (action === "billing") {
    const pageId = data.pageId ? id(data.pageId) : "";
    if (pageId) await ownPage(pageId);
    const [docs, productDocs] = await Promise.all([
      db.collection("marketplaceInvoices").where("ownerId", "==", uid).limit(200).get(),
      pageId ? db.collection("businessCatalog").where("pageId", "==", pageId).limit(200).get() : Promise.resolve(null),
    ]);
    const now = Date.now();
    const invoices = docs.docs.map(doc => publicInvoice(doc.id, doc.data(), now))
      .filter(invoice => !pageId || invoice.pageId === pageId)
      .sort((a, b) => b.issuedAt - a.issuedAt);
    const outstanding = invoices.filter(invoice => invoice.status !== "cancelled").reduce((total, invoice) => total + invoice.balanceMinor, 0);
    const overdue = invoices.filter(invoice => invoice.status === "overdue").reduce((total, invoice) => total + invoice.balanceMinor, 0);
    return { invoices, products: productDocs?.docs.map(doc => ({ ...doc.data(), id: doc.id })) || [],
      summary: { outstandingMinor: outstanding, overdueMinor: overdue, invoiceCount: invoices.length } };
  }
  if (action === "prepareInvoiceReminder") {
    const invoiceId = id(data.invoiceId);
    const reference = db.collection("marketplaceInvoices").doc(invoiceId);
    const invoice = await reference.get();
    if (!invoice.exists || invoice.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Facture introuvable.");
    const now = Date.now();
    const current = publicInvoice(invoiceId, invoice.data()!, now);
    if (current.status === "cancelled" || current.balanceMinor < 1) throw new HttpsError("failed-precondition", "Cette facture ne nécessite pas de relance.");
    const requestedTone = text(data.tone ?? "courtois", 20);
    const due = new Date(current.dueAt).toLocaleDateString("fr-FR");
    const amount = `${current.totalMinor} ${current.currency}`;
    const message = `Bonjour ${current.customerName}, ${requestedTone === "ferme" ? "nous vous rappelons" : "petit rappel concernant"} la facture ${current.number} d’un montant de ${amount}, échéance le ${due}. Pouvez-vous nous confirmer la suite à donner ?`;
    const draft = db.collection("invoiceReminderDrafts").doc();
    await draft.set({ invoiceId, ownerId: uid, pageId: current.pageId, customerUserId: current.customerUserId,
      customerName: current.customerName, message, tone: requestedTone, status: "draft", createdAt: FieldValue.serverTimestamp() });
    return { reminder: { id: draft.id, invoiceId, message, status: "draft", requiresHumanApproval: true },
      notice: "Relance préparée. Vérifiez-la puis envoyez-la vous-même dans la discussion concernée : WAPI ne contacte jamais un client ni n’encaisse à votre place." };
  }
  return fail("Action inconnue.");
}
