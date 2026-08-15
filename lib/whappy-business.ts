import { addDoc, collection, deleteDoc, doc, limit, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

type CloudDate = { toDate?: () => Date } | null;

export type BusinessPage = {
  id: string;
  ownerId: string;
  name: string;
  handle: string;
  type: "business" | "creator" | "organization";
  category: string;
  bio: string;
  city: string;
  phone: string;
  website: string;
  logoUrl: string;
  coverUrl: string;
  status: "active" | "hidden";
  followers: number;
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type AdCampaign = {
  id: string;
  ownerId: string;
  pageId: string;
  pageName: string;
  objective: "reach" | "messages" | "traffic" | "sales";
  title: string;
  creative: string;
  cta: string;
  audience: string;
  city: string;
  dailyBudget: number;
  days: number;
  totalBudget: number;
  status: "draft" | "active" | "paused" | "completed";
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type AdEvent = {
  id: string;
  campaignId: string;
  ownerId: string;
  userId: string;
  type: "impression" | "click";
  createdAt?: CloudDate;
};

export type BusinessCurrency = "XAF" | "XOF" | "CDF" | "USD" | "EUR";
export const businessCurrencyLabels: Record<BusinessCurrency, string> = {
  XAF: "FCFA · CEMAC",
  XOF: "FCFA · UEMOA",
  CDF: "Franc congolais",
  USD: "Dollar US",
  EUR: "Euro",
};
export type BusinessOfferKind = "product" | "service";
export type BusinessOffer = {
  id: string;
  ownerId: string;
  kind: BusinessOfferKind;
  title: string;
  description: string;
  price: number;
  currency: BusinessCurrency;
  status: "active" | "draft";
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};
export type BusinessSettings = { ownerId: string; currency: BusinessCurrency; defaultKind: BusinessOfferKind; updatedAt?: CloudDate };
export type LeadStage = "new" | "contacted" | "qualified" | "won" | "lost";
export const leadStageLabels: Record<LeadStage, string> = { new: "Nouveau", contacted: "Contacté", qualified: "Qualifié", won: "Client gagné", lost: "À reprendre" };
export type BusinessLead = {
  id: string;
  ownerId: string;
  name: string;
  contact: string;
  need: string;
  source: string;
  stage: LeadStage;
  nextAction: string;
  notes: string;
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type WhappyTeamStatus = "active" | "invited" | "paused";
export type WhappyTeamMember = {
  id: string;
  ownerId: string;
  name: string;
  role: string;
  contact: string;
  area: string;
  status: WhappyTeamStatus;
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type FounderEarningStatus = "pending" | "processing" | "paid";
export type FounderEarning = {
  id: string;
  ownerId: string;
  title: string;
  source: string;
  amount: number;
  currency: BusinessCurrency;
  status: FounderEarningStatus;
  dueLabel: string;
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type ArtistStatus = "prospect" | "active" | "paused" | "archived";

export type Artist = {
  id: string;
  ownerId: string;
  name: string;
  stageName: string;
  discipline: string;
  city: string;
  contact: string;
  email: string;
  status: ArtistStatus;
  isScouted?: boolean;
  nextAction: string;
  monthlyBudget: number;
  notes: string;
  createdAt?: CloudDate;
  updatedAt?: CloudDate;
};

export type NewBusinessPage = Pick<BusinessPage, "name" | "type" | "category" | "bio" | "city" | "phone" | "website">;
export type NewAdCampaign = Pick<AdCampaign, "pageId" | "pageName" | "objective" | "title" | "creative" | "cta" | "audience" | "city" | "dailyBudget" | "days">;
export type NewArtist = Pick<Artist, "name" | "stageName" | "discipline" | "city" | "contact" | "email" | "status" | "nextAction" | "monthlyBudget" | "notes"> & {
  isScouted?: boolean;
};

function slugify(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 42);
}

export function watchBusinessPages(ownerId: string, onPages: (pages: BusinessPage[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "businessPages"), where("ownerId", "==", ownerId)), (snapshot) => {
    const pages = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as BusinessPage));
    pages.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onPages(pages);
  }, onError);
}

export async function createBusinessPage(ownerId: string, page: NewBusinessPage) {
  const reference = doc(collection(db, "businessPages"));
  const name = page.name.trim().slice(0, 80);
  await setDoc(reference, {
    ...page,
    name,
    handle: `${slugify(name)}-${reference.id.slice(0, 5)}`,
    ownerId,
    bio: page.bio.trim().slice(0, 400),
    city: page.city.trim().slice(0, 80),
    phone: page.phone.trim().slice(0, 30),
    website: page.website.trim().slice(0, 180),
    logoUrl: "",
    coverUrl: "",
    status: "active",
    followers: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function updateBusinessPage(pageId: string, changes: Partial<Pick<BusinessPage, "name" | "type" | "category" | "bio" | "city" | "phone" | "website" | "status">>) {
  await updateDoc(doc(db, "businessPages", pageId), { ...changes, updatedAt: serverTimestamp() });
}

export async function updateBusinessBranding(ownerId: string, pageId: string, assets: { logo?: File; cover?: File }) {
  const entries = Object.entries(assets).filter((entry): entry is ["logo" | "cover", File] => Boolean(entry[1]));
  if (!entries.length) throw new Error("missing-brand-asset");

  const uploaded = await Promise.all(entries.map(async ([kind, file]) => {
    const maximum = kind === "logo" ? 5 * 1024 * 1024 : 8 * 1024 * 1024;
    const extensionByType: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };
    if (!extensionByType[file.type]) throw new Error("invalid-brand-asset");
    if (!file.size || file.size > maximum) throw new Error("brand-asset-too-large");
    const extension = extensionByType[file.type];
    const assetRef = ref(storage, `business/${ownerId}/${pageId}/${kind}-${Date.now()}.${extension}`);
    await uploadBytes(assetRef, file, { contentType: file.type, customMetadata: { ownerId, pageId, kind } });
    return [`${kind}Url`, await getDownloadURL(assetRef)] as const;
  }));

  const changes = Object.fromEntries(uploaded) as Partial<Pick<BusinessPage, "logoUrl" | "coverUrl">>;
  await updateDoc(doc(db, "businessPages", pageId), { ...changes, updatedAt: serverTimestamp() });
  return changes;
}

export async function deleteBusinessPage(pageId: string) {
  await deleteDoc(doc(db, "businessPages", pageId));
}

export function watchOwnerCampaigns(ownerId: string, onCampaigns: (campaigns: AdCampaign[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "adCampaigns"), where("ownerId", "==", ownerId)), (snapshot) => {
    const campaigns = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as AdCampaign));
    campaigns.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onCampaigns(campaigns);
  }, onError);
}

export function watchActiveCampaigns(onCampaigns: (campaigns: AdCampaign[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "adCampaigns"), where("status", "==", "active"), limit(20)), (snapshot) => onCampaigns(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as AdCampaign))), onError);
}

export async function createAdCampaign(ownerId: string, campaign: NewAdCampaign) {
  const dailyBudget = Math.max(500, Math.round(campaign.dailyBudget));
  const days = Math.min(90, Math.max(1, Math.round(campaign.days)));
  const reference = await addDoc(collection(db, "adCampaigns"), {
    ...campaign,
    title: campaign.title.trim().slice(0, 120),
    creative: campaign.creative.trim().slice(0, 600),
    cta: campaign.cta.trim().slice(0, 40),
    audience: campaign.audience.trim().slice(0, 120),
    city: campaign.city.trim().slice(0, 80),
    ownerId,
    dailyBudget,
    days,
    totalBudget: dailyBudget * days,
    status: "active",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function updateCampaignStatus(campaignId: string, status: AdCampaign["status"]) {
  await updateDoc(doc(db, "adCampaigns", campaignId), { status, updatedAt: serverTimestamp() });
}

export async function deleteAdCampaign(campaignId: string) {
  await deleteDoc(doc(db, "adCampaigns", campaignId));
}

export function watchOwnerArtists(ownerId: string, onArtists: (artists: Artist[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "artists"), where("ownerId", "==", ownerId)), (snapshot) => {
    const artists = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as Artist));
    artists.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onArtists(artists);
  }, onError);
}

export async function createArtist(ownerId: string, artist: NewArtist) {
  const reference = await addDoc(collection(db, "artists"), {
    ...artist,
    isScouted: artist.isScouted === undefined ? false : Boolean(artist.isScouted),
    ownerId,
    name: artist.name.trim().slice(0, 100),
    stageName: artist.stageName.trim().slice(0, 100),
    discipline: artist.discipline.trim().slice(0, 80),
    city: artist.city.trim().slice(0, 80),
    contact: artist.contact.trim().slice(0, 40),
    email: artist.email.trim().slice(0, 120),
    nextAction: artist.nextAction.trim().slice(0, 120),
    monthlyBudget: Math.max(0, Math.round(artist.monthlyBudget)),
    notes: artist.notes.trim().slice(0, 600),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function updateArtist(artistId: string, changes: Partial<NewArtist>) {
  const sanitized = { ...changes } as Partial<NewArtist>;
  if (sanitized.name !== undefined) sanitized.name = sanitized.name.trim().slice(0, 100);
  if (sanitized.stageName !== undefined) sanitized.stageName = sanitized.stageName.trim().slice(0, 100);
  if (sanitized.discipline !== undefined) sanitized.discipline = sanitized.discipline.trim().slice(0, 80);
  if (sanitized.city !== undefined) sanitized.city = sanitized.city.trim().slice(0, 80);
  if (sanitized.contact !== undefined) sanitized.contact = sanitized.contact.trim().slice(0, 40);
  if (sanitized.email !== undefined) sanitized.email = sanitized.email.trim().slice(0, 120);
  if (sanitized.nextAction !== undefined) sanitized.nextAction = sanitized.nextAction.trim().slice(0, 120);
  if (sanitized.isScouted !== undefined) sanitized.isScouted = Boolean(sanitized.isScouted);
  if (sanitized.monthlyBudget !== undefined) sanitized.monthlyBudget = Math.max(0, Math.round(sanitized.monthlyBudget));
  if (sanitized.notes !== undefined) sanitized.notes = sanitized.notes.trim().slice(0, 600);
  await updateDoc(doc(db, "artists", artistId), { ...sanitized, updatedAt: serverTimestamp() });
}

export async function deleteArtist(artistId: string) {
  await deleteDoc(doc(db, "artists", artistId));
}

export function watchOwnerAdEvents(ownerId: string, onEvents: (events: AdEvent[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "adEvents"), where("ownerId", "==", ownerId)), (snapshot) => onEvents(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as AdEvent))), onError);
}

export async function recordAdEvent(campaign: AdCampaign, userId: string, type: AdEvent["type"]) {
  if (!userId || campaign.ownerId === userId) return;
  if (type === "impression") {
    const day = new Date().toISOString().slice(0, 10);
    await setDoc(doc(db, "adEvents", `${campaign.id}_${userId}_${day}`), { campaignId: campaign.id, ownerId: campaign.ownerId, userId, type, createdAt: serverTimestamp() }, { merge: false });
    return;
  }
  await addDoc(collection(db, "adEvents"), { campaignId: campaign.id, ownerId: campaign.ownerId, userId, type, createdAt: serverTimestamp() });
}

export function watchBusinessSettings(ownerId: string, onSettings: (settings: BusinessSettings | null) => void, onError: () => void) {
  return onSnapshot(doc(db, "businessSettings", ownerId), (snapshot) => onSettings(snapshot.exists() ? ({ ownerId, ...snapshot.data() } as BusinessSettings) : null), onError);
}

export async function saveBusinessSettings(ownerId: string, settings: Pick<BusinessSettings, "currency" | "defaultKind">) {
  await setDoc(doc(db, "businessSettings", ownerId), { ownerId, ...settings, updatedAt: serverTimestamp() }, { merge: true });
}

export function watchOwnerOffers(ownerId: string, onOffers: (offers: BusinessOffer[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "businessOffers"), where("ownerId", "==", ownerId)), (snapshot) => {
    const offers = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as BusinessOffer));
    offers.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onOffers(offers);
  }, onError);
}

export async function createBusinessOffer(ownerId: string, offer: Omit<BusinessOffer, "id" | "ownerId" | "createdAt" | "updatedAt">) {
  const reference = await addDoc(collection(db, "businessOffers"), { ...offer, ownerId, title: offer.title.trim().slice(0, 120), description: offer.description.trim().slice(0, 600), price: Math.max(0, Math.round(offer.price)), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
  return reference.id;
}

export function watchOwnerLeads(ownerId: string, onLeads: (leads: BusinessLead[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "businessLeads"), where("ownerId", "==", ownerId)), (snapshot) => {
    const leads = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as BusinessLead));
    leads.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onLeads(leads);
  }, onError);
}

export async function createBusinessLead(ownerId: string, lead: Omit<BusinessLead, "id" | "ownerId" | "createdAt" | "updatedAt">) {
  const reference = await addDoc(collection(db, "businessLeads"), { ...lead, ownerId, name: lead.name.trim().slice(0, 100), contact: lead.contact.trim().slice(0, 80), need: lead.need.trim().slice(0, 180), source: lead.source.trim().slice(0, 80), nextAction: lead.nextAction.trim().slice(0, 120), notes: lead.notes.trim().slice(0, 600), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
  return reference.id;
}

export async function updateBusinessLeadStage(leadId: string, stage: LeadStage) {
  await updateDoc(doc(db, "businessLeads", leadId), { stage, updatedAt: serverTimestamp() });
}

export function watchWhappyUserCount(onCount: (count: number) => void, onError: () => void) {
  return onSnapshot(collection(db, "users"), (snapshot) => onCount(snapshot.size), onError);
}

export function watchWhappyLiveStats(onStats: (stats: { sessions: number; viewers: number }) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "liveSessions"), where("status", "==", "live")), (snapshot) => {
    const viewers = snapshot.docs.reduce((total, item) => total + Math.max(0, Number(item.data().viewerCount || 0)), 0);
    onStats({ sessions: snapshot.size, viewers });
  }, onError);
}

export function watchWhappyTeam(ownerId: string, onMembers: (members: WhappyTeamMember[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "whappyTeam"), where("ownerId", "==", ownerId)), (snapshot) => {
    const members = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as WhappyTeamMember));
    members.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onMembers(members);
  }, onError);
}

export async function createWhappyTeamMember(ownerId: string, member: Omit<WhappyTeamMember, "id" | "ownerId" | "createdAt" | "updatedAt">) {
  const reference = await addDoc(collection(db, "whappyTeam"), {
    ...member,
    ownerId,
    name: member.name.trim().slice(0, 100),
    role: member.role.trim().slice(0, 100),
    contact: member.contact.trim().slice(0, 120),
    area: member.area.trim().slice(0, 100),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function updateWhappyTeamMember(memberId: string, changes: Partial<Pick<WhappyTeamMember, "name" | "role" | "contact" | "area" | "status">>) {
  await updateDoc(doc(db, "whappyTeam", memberId), { ...changes, updatedAt: serverTimestamp() });
}

export function watchFounderEarnings(ownerId: string, onEarnings: (earnings: FounderEarning[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "whappyEarnings"), where("ownerId", "==", ownerId)), (snapshot) => {
    const earnings = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as FounderEarning));
    earnings.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onEarnings(earnings);
  }, onError);
}

export async function createFounderEarning(ownerId: string, earning: Omit<FounderEarning, "id" | "ownerId" | "createdAt" | "updatedAt">) {
  const reference = await addDoc(collection(db, "whappyEarnings"), {
    ...earning,
    ownerId,
    title: earning.title.trim().slice(0, 120),
    source: earning.source.trim().slice(0, 100),
    amount: Math.max(0, Math.round(earning.amount)),
    dueLabel: earning.dueLabel.trim().slice(0, 80),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function updateFounderEarningStatus(earningId: string, status: FounderEarningStatus) {
  await updateDoc(doc(db, "whappyEarnings", earningId), { status, updatedAt: serverTimestamp() });
}
