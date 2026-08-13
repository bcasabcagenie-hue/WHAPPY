import { addDoc, collection, deleteDoc, doc, onSnapshot, orderBy, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

export type CloudListing = {
  id: string;
  title: string;
  price: string;
  place: string;
  seller: string;
  mark: string;
  category: string;
  mode: "vente" | "troc";
  trust: number;
  mediaUrl?: string;
  ownerId?: string;
  status?: "active" | "reserved" | "sold";
};

export type CloudRequest = {
  id: string;
  title: string;
  details: string;
  place: string;
  reward: string;
  urgent: boolean;
  category: "Produits" | "Services" | "Situations";
};

export type CloudMessage = {
  id: string;
  text: string;
  senderId: string;
  createdAt?: { toDate?: () => Date } | null;
};

export type CloudGroup = {
  id: string;
  name: string;
  description: string;
  mark: string;
  ownerId: string;
  memberIds: string[];
  memberNames: string[];
  createdAt?: { toDate?: () => Date } | null;
};

export type CloudGroupMessage = CloudMessage & { senderName: string };

export type OrderStatus = "pending" | "confirmed" | "ready" | "completed" | "cancelled";

export type CloudOrder = {
  id: string;
  reference: string;
  buyerId: string;
  customerName: string;
  phone: string;
  address: string;
  paymentMethod: "delivery" | "mobile";
  total: number;
  status: OrderStatus;
  items: Array<{ listingId: string; title: string; seller: string; price: string; quantity: number }>;
  createdAt?: { toDate?: () => Date } | null;
};

export type NewOrder = Omit<CloudOrder, "id" | "reference" | "buyerId" | "status" | "createdAt">;

type NewListing = Omit<CloudListing, "id" | "trust" | "mediaUrl" | "ownerId" | "status">;
type NewRequest = Omit<CloudRequest, "id">;

export function watchWhappyData(onListings: (items: CloudListing[]) => void, onRequests: (items: CloudRequest[]) => void, onError: () => void) {
  const listingsQuery = query(collection(db, "listings"), orderBy("createdAt", "desc"));
  const requestsQuery = query(collection(db, "requests"), orderBy("createdAt", "desc"));
  const stopListings = onSnapshot(listingsQuery, (snapshot) => onListings(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudListing))), onError);
  const stopRequests = onSnapshot(requestsQuery, (snapshot) => onRequests(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudRequest))), onError);
  return () => { stopListings(); stopRequests(); };
}

export async function publishListing(userId: string, listing: NewListing, media?: File) {
  let mediaUrl: string | undefined;
  if (media) {
    if (media.size > 10 * 1024 * 1024) throw new Error("media-too-large");
    const safeName = media.name.replace(/[^a-zA-Z0-9._-]/g, "-");
    const mediaRef = ref(storage, `users/${userId}/listings/${Date.now()}-${safeName}`);
    await uploadBytes(mediaRef, media, { contentType: media.type });
    mediaUrl = await getDownloadURL(mediaRef);
  }
  const document = await addDoc(collection(db, "listings"), { ...listing, ownerId: userId, status: "active", trust: 100, mediaUrl: mediaUrl ?? null, createdAt: serverTimestamp() });
  return { ...listing, id: document.id, ownerId: userId, status: "active", trust: 100, mediaUrl } satisfies CloudListing;
}

export async function updateListing(listingId: string, changes: Pick<CloudListing, "title" | "price" | "place" | "status">) {
  await updateDoc(doc(db, "listings", listingId), { ...changes, updatedAt: serverTimestamp() });
}

export async function removeListing(listingId: string) {
  await deleteDoc(doc(db, "listings", listingId));
}

export async function publishRequest(userId: string, request: NewRequest) {
  const document = await addDoc(collection(db, "requests"), { ...request, ownerId: userId, createdAt: serverTimestamp() });
  return { ...request, id: document.id } satisfies CloudRequest;
}

function conversationId(userId: string, contactId: string) {
  return `${userId}--${contactId.replace(/[^a-zA-Z0-9_-]/g, "-").toLowerCase()}`;
}

async function ensureConversation(userId: string, contactId: string, contactName: string) {
  const reference = doc(db, "conversations", conversationId(userId, contactId));
  await setDoc(reference, {
    ownerId: userId,
    memberIds: [userId],
    contactId,
    contactName,
    updatedAt: serverTimestamp(),
  }, { merge: true });
  return reference;
}

export function watchConversationMessages(
  userId: string,
  contactId: string,
  contactName: string,
  onMessages: (items: CloudMessage[]) => void,
  onError: () => void,
) {
  let active = true;
  let stop = () => {};

  void ensureConversation(userId, contactId, contactName)
    .then((conversation) => {
      if (!active) return;
      const messagesQuery = query(collection(conversation, "messages"), orderBy("createdAt", "asc"));
      stop = onSnapshot(
        messagesQuery,
        (snapshot) => onMessages(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudMessage))),
        onError,
      );
    })
    .catch(onError);

  return () => {
    active = false;
    stop();
  };
}

export async function sendConversationMessage(userId: string, contactId: string, contactName: string, text: string) {
  const value = text.trim();
  if (!value || value.length > 4000) throw new Error("invalid-message");
  const conversation = await ensureConversation(userId, contactId, contactName);
  const message = await addDoc(collection(conversation, "messages"), {
    text: value,
    senderId: userId,
    createdAt: serverTimestamp(),
  });
  await updateDoc(conversation, { lastMessage: value, updatedAt: serverTimestamp() });
  return message.id;
}

export function watchUserGroups(userId: string, onGroups: (items: CloudGroup[]) => void, onError: () => void) {
  const groupsQuery = query(collection(db, "groups"), where("memberIds", "array-contains", userId));
  return onSnapshot(groupsQuery, (snapshot) => {
    const items = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudGroup));
    items.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onGroups(items);
  }, onError);
}

export async function createGroup(userId: string, name: string, description: string, memberNames: string[]) {
  const cleanName = name.trim();
  if (cleanName.length < 2 || cleanName.length > 80) throw new Error("invalid-group-name");
  const uniqueMembers = [...new Set(memberNames.map((member) => member.trim()).filter(Boolean))].slice(0, 999);
  const mark = cleanName.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase();
  const payload = {
    name: cleanName,
    description: description.trim().slice(0, 240),
    mark: mark || "WG",
    ownerId: userId,
    memberIds: [userId],
    memberNames: uniqueMembers,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  const document = await addDoc(collection(db, "groups"), payload);
  return { ...payload, id: document.id, createdAt: null } satisfies CloudGroup;
}

export function watchGroupMessages(groupId: string, onMessages: (items: CloudGroupMessage[]) => void, onError: () => void) {
  const messagesQuery = query(collection(db, "groups", groupId, "messages"), orderBy("createdAt", "asc"));
  return onSnapshot(messagesQuery, (snapshot) => onMessages(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudGroupMessage))), onError);
}

export async function sendGroupMessage(groupId: string, userId: string, senderName: string, text: string) {
  const value = text.trim();
  if (!value || value.length > 4000) throw new Error("invalid-message");
  const message = await addDoc(collection(db, "groups", groupId, "messages"), {
    text: value,
    senderId: userId,
    senderName: senderName.trim().slice(0, 80) || "Membre Whappy",
    createdAt: serverTimestamp(),
  });
  await updateDoc(doc(db, "groups", groupId), { lastMessage: value, updatedAt: serverTimestamp() });
  return message.id;
}

export function watchUserOrders(userId: string, onOrders: (items: CloudOrder[]) => void, onError: () => void) {
  const ordersQuery = query(collection(db, "orders"), where("buyerId", "==", userId));
  return onSnapshot(ordersQuery, (snapshot) => {
    const items = snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudOrder));
    items.sort((left, right) => (right.createdAt?.toDate?.()?.getTime() || 0) - (left.createdAt?.toDate?.()?.getTime() || 0));
    onOrders(items);
  }, onError);
}

export async function createOrder(userId: string, order: NewOrder) {
  const reference = `WH-${Date.now().toString(36).slice(-6).toUpperCase()}`;
  const document = await addDoc(collection(db, "orders"), {
    ...order,
    buyerId: userId,
    reference,
    status: "pending",
    createdAt: serverTimestamp(),
  });
  return { ...order, id: document.id, buyerId: userId, reference, status: "pending" as const } satisfies CloudOrder;
}

export async function cancelOrder(orderId: string) {
  await updateDoc(doc(db, "orders", orderId), { status: "cancelled", updatedAt: serverTimestamp() });
}
