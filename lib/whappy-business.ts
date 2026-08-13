import { addDoc, collection, deleteDoc, doc, limit, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { db } from "@/lib/firebase";

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

export type NewBusinessPage = Pick<BusinessPage, "name" | "type" | "category" | "bio" | "city" | "phone" | "website">;
export type NewAdCampaign = Pick<AdCampaign, "pageId" | "pageName" | "objective" | "title" | "creative" | "cta" | "audience" | "city" | "dailyBudget" | "days">;

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
