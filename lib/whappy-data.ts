import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, limit, onSnapshot, orderBy, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
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
  kind?: "text" | "image" | "audio";
  mediaUrl?: string;
  mediaName?: string;
  duration?: number;
  createdAt?: { toDate?: () => Date } | null;
};

export type DirectMember = { uid:string; displayName:string; phoneNumber:string };
type CloudTimestamp = { toDate?: () => Date } | null;
export type CloudConversation = {
  id:string;
  ownerId:string;
  memberIds:string[];
  members:DirectMember[];
  lastMessage?:string;
  typingBy?:Record<string,boolean>;
  readBy?:Record<string,CloudTimestamp>;
  updatedAt?:CloudTimestamp;
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

export type GroupActivityType = "poll" | "event" | "announcement";

export type CloudGroupActivity = {
  id: string;
  type: GroupActivityType;
  title: string;
  details: string;
  options: string[];
  eventDate: string;
  creatorId: string;
  creatorName: string;
  createdAt?: { toDate?: () => Date } | null;
};

export type CloudGroupActivityResponse = {
  id: string;
  userId: string;
  kind: "poll" | "event";
  optionIndex?: number;
  attending?: boolean;
};

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

function normalizeWhappyPhone(phoneNumber:string) {
  const raw=phoneNumber.trim();
  const digits=raw.replace(/\D/g,"");
  if(!digits)return "";
  if(raw.startsWith("+"))return `+${digits}`;
  if(digits.startsWith("00"))return `+${digits.slice(2)}`;
  if(digits.length>10)return `+${digits}`;
  return `+242${digits}`;
}

export async function findWhappyUserByPhone(phoneNumber:string) {
  const normalized=normalizeWhappyPhone(phoneNumber);
  if(!normalized)return null;
  const usersQuery=query(collection(db,"users"),where("phoneNumber","==",normalized),limit(1));
  const snapshot=await getDocs(usersQuery);
  const found=snapshot.docs[0];
  return found?({uid:found.id,...found.data()} as DirectMember):null;
}

export async function ensureDirectConversation(current:DirectMember,peer:DirectMember) {
  const id=`direct-${[current.uid,peer.uid].sort().join("-")}`;
  const reference=doc(db,"conversations",id);
  const existing=await getDoc(reference);
  if(!existing.exists())await setDoc(reference,{ownerId:current.uid,memberIds:[current.uid,peer.uid].sort(),members:[current,peer],typingBy:{},readBy:{},updatedAt:serverTimestamp()});
  return id;
}

export function watchDirectConversations(userId:string,onItems:(items:CloudConversation[])=>void,onError:()=>void) {
  const conversationsQuery=query(collection(db,"conversations"),where("memberIds","array-contains",userId));
  return onSnapshot(conversationsQuery,(snapshot)=>{
    const items=snapshot.docs.map((item)=>({id:item.id,...item.data()} as CloudConversation)).filter((item)=>item.memberIds.length===2&&Array.isArray(item.members));
    items.sort((left,right)=>(right.updatedAt?.toDate?.()?.getTime()||0)-(left.updatedAt?.toDate?.()?.getTime()||0));
    onItems(items);
  },onError);
}

export function watchDirectMessages(conversationId:string,onItems:(items:CloudMessage[])=>void,onError:()=>void) {
  const messagesQuery=query(collection(db,"conversations",conversationId,"messages"),orderBy("createdAt","asc"));
  return onSnapshot(messagesQuery,(snapshot)=>onItems(snapshot.docs.map((item)=>({id:item.id,...item.data()} as CloudMessage))),onError);
}

export async function sendDirectMessage(conversationId:string,userId:string,text:string) {
  const value=text.trim();
  if(!value||value.length>4000)throw new Error("invalid-message");
  await addDoc(collection(db,"conversations",conversationId,"messages"),{text:value,senderId:userId,createdAt:serverTimestamp()});
  await updateDoc(doc(db,"conversations",conversationId),{lastMessage:value,updatedAt:serverTimestamp(),[`typingBy.${userId}`]:false});
}

export async function sendDirectAttachment(conversationId:string,userId:string,file:File,kind:"image"|"audio",duration=0) {
  const maximum=kind==="image"?20*1024*1024:12*1024*1024;
  if(!file.size||file.size>maximum)throw new Error("media-too-large");
  if(kind==="image"&&!file.type.startsWith("image/"))throw new Error("invalid-media");
  if(kind==="audio"&&!file.type.startsWith("audio/"))throw new Error("invalid-media");
  const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,"-");
  const mediaRef=ref(storage,`conversations/${conversationId}/${userId}/${Date.now()}-${safeName}`);
  await uploadBytes(mediaRef,file,{contentType:file.type});
  const mediaUrl=await getDownloadURL(mediaRef);
  const label=kind==="image"?"Photo":"Message vocal";
  await addDoc(collection(db,"conversations",conversationId,"messages"),{text:label,senderId:userId,kind,mediaUrl,mediaName:file.name.slice(0,120),duration:Math.max(0,Math.round(duration)),createdAt:serverTimestamp()});
  await updateDoc(doc(db,"conversations",conversationId),{lastMessage:kind==="image"?"📷 Photo":"🎙 Message vocal",updatedAt:serverTimestamp(),[`typingBy.${userId}`]:false});
}

export async function setDirectTyping(conversationId:string,userId:string,typing:boolean) {
  await updateDoc(doc(db,"conversations",conversationId),{[`typingBy.${userId}`]:typing});
}

export async function markDirectConversationRead(conversationId:string,userId:string) {
  await updateDoc(doc(db,"conversations",conversationId),{[`readBy.${userId}`]:serverTimestamp()});
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

export function watchGroupActivities(groupId: string, onActivities: (items: CloudGroupActivity[]) => void, onError: () => void) {
  const activitiesQuery = query(collection(db, "groups", groupId, "activities"), orderBy("createdAt", "desc"));
  return onSnapshot(activitiesQuery, (snapshot) => onActivities(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudGroupActivity))), onError);
}

export async function createGroupActivity(groupId: string, userId: string, creatorName: string, activity: Pick<CloudGroupActivity, "type" | "title" | "details" | "options" | "eventDate">) {
  const title = activity.title.trim();
  if (title.length < 2 || title.length > 160) throw new Error("invalid-activity");
  const payload = {
    ...activity,
    title,
    details: activity.details.trim().slice(0, 1000),
    options: activity.options.map((option) => option.trim()).filter(Boolean).slice(0, 8),
    eventDate: activity.eventDate.slice(0, 40),
    creatorId: userId,
    creatorName: creatorName.trim().slice(0, 80) || "Membre Whappy",
    createdAt: serverTimestamp(),
  };
  const document = await addDoc(collection(db, "groups", groupId, "activities"), payload);
  return { ...payload, id: document.id, createdAt: null } satisfies CloudGroupActivity;
}

export function watchGroupActivityResponses(groupId: string, activityId: string, onResponses: (items: CloudGroupActivityResponse[]) => void, onError: () => void) {
  return onSnapshot(collection(db, "groups", groupId, "activities", activityId, "responses"), (snapshot) => {
    onResponses(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudGroupActivityResponse)));
  }, onError);
}

export async function saveGroupActivityResponse(groupId: string, activityId: string, userId: string, response: Omit<CloudGroupActivityResponse, "id" | "userId">) {
  await setDoc(doc(db, "groups", groupId, "activities", activityId, "responses", userId), {
    ...response,
    userId,
    updatedAt: serverTimestamp(),
  });
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
