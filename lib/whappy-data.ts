import { addDoc, arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, limit, onSnapshot, orderBy, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch } from "firebase/firestore";
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
  sellerPhone?: string;
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
  kind?: "text" | "image" | "audio" | "video" | "link";
  mediaUrl?: string;
  mediaName?: string;
  quality?: "standard" | "hd";
  linkUrl?: string;
  duration?: number;
  effect?: "comic" | "neon" | "ink" | "pop";
  caption?: string;
  expiresAt?: { toDate?: () => Date } | null;
  viewOnce?: boolean;
  viewedBy?: Record<string, CloudTimestamp>;
  createdAt?: { toDate?: () => Date } | null;
};

export type DirectMember = { uid:string; displayName:string; phoneNumber:string; wepiEnabled?: boolean; wepiName?: string; wepiBusinessName?: string };
type CloudTimestamp = { toDate?: () => Date } | null;
export type CloudConversation = {
  id:string;
  ownerId:string;
  memberIds:string[];
  members:DirectMember[];
  lastMessage?:string;
  typingBy?:Record<string,boolean>;
  readBy?:Record<string,CloudTimestamp>;
  presenceBy?:Record<string,CloudTimestamp>;
  updatedAt?:CloudTimestamp;
  ephemeralSeconds?: 0 | 86400 | 604800 | number;
};

export type CloudGroup = {
  id: string;
  name: string;
  description: string;
  mark: string;
  ownerId: string;
  memberIds: string[];
  memberNames: string[];
  inviteToken?: string;
  createdAt?: { toDate?: () => Date } | null;
};

export type CloudGroupJoinRequest = {
  id: string;
  groupId: string;
  ownerId: string;
  userId: string;
  userName: string;
  inviteToken: string;
  status: "pending" | "approved" | "declined";
  createdAt?: { toDate?: () => Date } | null;
  updatedAt?: { toDate?: () => Date } | null;
};

export type WhapTextTone = "hope" | "action" | "community" | "warning";
export type CloudWhapText = {
  id: string;
  authorId: string;
  authorName: string;
  text: string;
  tone: WhapTextTone;
  kind: "status" | "whaptext";
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
  if(digits.startsWith("242") && digits.length > 3)return `+${digits}`;
  if(digits.length>10)return `+${digits}`;
  return `+242${digits}`;
}

/**
 * Phone numbers arrive in several legitimate forms: +242..., 242..., 00 242...,
 * or the national 06... form. Firebase may also canonicalise the national
 * leading zero away. Keep all equivalent representations so an existing
 * Whappy account is found whatever the format used in the search field.
 */
function phoneLookupCandidates(phoneNumber: string) {
  const raw = phoneNumber.trim();
  const digits = raw.replace(/\D/g, "");
  if (!digits) return [];

  const values = new Set<string>();
  const add = (value: string) => {
    const clean = value.replace(/\D/g, "");
    if (!clean) return;
    values.add(clean);
    values.add(`+${clean}`);
  };
  const addCongoForms = (national: string) => {
    const cleanNational = national.replace(/^0+/, "0");
    add(`242${cleanNational}`);
    if (cleanNational.startsWith("0")) add(`242${cleanNational.slice(1)}`);
    else add(`2420${cleanNational}`);
  };

  const withoutInternationalPrefix = digits.startsWith("00") ? digits.slice(2) : digits;
  add(withoutInternationalPrefix);

  if (withoutInternationalPrefix.startsWith("242") && withoutInternationalPrefix.length > 3) {
    addCongoForms(withoutInternationalPrefix.slice(3));
  } else if (!raw.startsWith("+") && !digits.startsWith("00") && digits.length <= 10) {
    addCongoForms(digits);
  }

  // Preserve the direct E.164 representation as the first lookup candidate.
  const normalized = normalizeWhappyPhone(raw);
  if (normalized) {
    values.delete(normalized);
    values.delete(normalized.replace(/^\+/, ""));
    values.add(normalized);
    values.add(normalized.replace(/^\+/, ""));
  }
  return [...values];
}

export async function findWhappyUserByPhone(phoneNumber:string) {
  const candidates = phoneLookupCandidates(phoneNumber);
  if (!candidates.length) return null;
  const fields: Array<"phoneNumber" | "phoneLookup" | "phoneDigits"> = ["phoneNumber", "phoneLookup", "phoneDigits"];
  for (const field of fields) {
    const values = [...new Set(candidates.map((candidate) => field === "phoneDigits" ? candidate.replace(/\D/g, "") : candidate).filter(Boolean))];
    if (!values.length) continue;
    // `in` keeps the lookup fast even when the user entered a national number.
    // Firestore accepts up to 30 values in one `in` clause; our candidate set is
    // deliberately kept below that limit.
    const snapshot = await getDocs(query(collection(db, "users"), where(field, "in", values.slice(0, 30)), limit(1)));
    const found = snapshot.docs[0];
    if (found) return ({ uid: found.id, ...found.data() } as DirectMember);
  }
  return null;
}

export async function findWhappyUserById(userId:string) {
  const snapshot = await getDoc(doc(db, "users", userId));
  return snapshot.exists() ? ({ uid: snapshot.id, ...snapshot.data() } as DirectMember) : null;
}

export async function ensureDirectConversation(current:DirectMember,peer:DirectMember) {
  const id=`direct-${[current.uid,peer.uid].sort().join("-")}`;
  const reference=doc(db,"conversations",id);
  // A missing conversation cannot be read under the privacy rules because it
  // has no member list yet. Merge directly: Firestore treats this as a create
  // for a new conversation and as a safe update for an existing one.
  await setDoc(reference,{ownerId:current.uid,memberIds:[current.uid,peer.uid].sort(),members:[current,peer],typingBy:{},readBy:{},ephemeralSeconds:0,updatedAt:serverTimestamp()},{merge:true});
  return id;
}

export function watchDirectConversations(userId:string,onItems:(items:CloudConversation[])=>void,onError:()=>void) {
  const conversationsQuery=query(collection(db,"conversations"),where("memberIds","array-contains",userId),orderBy("updatedAt","desc"));
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

function expiryTimestamp(ephemeralSeconds:number) {
  return ephemeralSeconds > 0 ? Timestamp.fromMillis(Date.now() + ephemeralSeconds * 1000) : null;
}

export async function sendDirectMessage(conversationId:string,userId:string,text:string,ephemeralSeconds=0,linkUrl="") {
  const value=text.trim();
  if(!value||value.length>4000)throw new Error("invalid-message");
  if(![0,86400,604800].includes(ephemeralSeconds))throw new Error("invalid-expiry");
  const safeLink=linkUrl.trim();
  const payload:Record<string,unknown>={text:value,senderId:userId,expiresAt:expiryTimestamp(ephemeralSeconds),createdAt:serverTimestamp()};
  if(safeLink) { payload.kind="link"; payload.linkUrl=safeLink; }
  await addDoc(collection(db,"conversations",conversationId,"messages"),payload);
  await updateDoc(doc(db,"conversations",conversationId),{lastMessage:value,updatedAt:serverTimestamp(),[`typingBy.${userId}`]:false});
}

export async function sendDirectAttachment(conversationId:string,userId:string,file:File,kind:"image"|"audio"|"video",duration=0,effect="",caption="",ephemeralSeconds=0,viewOnce=false,quality:"standard"|"hd"="hd") {
  const maximum=kind==="image"?20*1024*1024:kind==="video"?60*1024*1024:12*1024*1024;
  if(!file.size||file.size>maximum)throw new Error("media-too-large");
  if(kind==="image"&&!file.type.startsWith("image/"))throw new Error("invalid-media");
  if(kind==="audio"&&!file.type.startsWith("audio/"))throw new Error("invalid-media");
  if(kind==="video"&&!file.type.startsWith("video/"))throw new Error("invalid-media");
  if(![0,86400,604800].includes(ephemeralSeconds))throw new Error("invalid-expiry");
  const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,"-");
  const mediaRef=ref(storage,`conversations/${conversationId}/${userId}/${Date.now()}-${safeName}`);
  await uploadBytes(mediaRef,file,{contentType:file.type,customMetadata:{quality}});
  const mediaUrl=await getDownloadURL(mediaRef);
  const label=kind==="image"?"Image WHAPPY":kind==="video"?"Vidéo WHAPPY":"Message vocal";
  const payload:Record<string,unknown>={text:label,senderId:userId,kind,mediaUrl,mediaName:file.name.slice(0,120),quality:kind === "audio" ? "standard" : quality,duration:Math.max(0,Math.round(duration)),expiresAt:expiryTimestamp(ephemeralSeconds),viewOnce:viewOnce && kind !== "audio",viewedBy:{},createdAt:serverTimestamp()};
  if(kind==="video"){payload.effect=["comic","neon","ink","pop"].includes(effect)?effect:"pop";payload.caption=caption.trim().slice(0,100);}
  await addDoc(collection(db,"conversations",conversationId,"messages"),payload);
  await updateDoc(doc(db,"conversations",conversationId),{lastMessage:kind==="image"?"🎨 Création WHAPPY":kind==="video"?"🎬 Vidéo WHAPPY":"🎙 Message vocal",updatedAt:serverTimestamp(),[`typingBy.${userId}`]:false});
}

export async function setDirectEphemeralMode(conversationId:string,ephemeralSeconds:0|86400|604800) {
  await updateDoc(doc(db,"conversations",conversationId),{ephemeralSeconds,updatedAt:serverTimestamp()});
}

export async function markDirectMessageViewed(conversationId:string,messageId:string,userId:string) {
  await updateDoc(doc(db,"conversations",conversationId,"messages",messageId),{[`viewedBy.${userId}`]:serverTimestamp()});
}

export async function purgeExpiredDirectMessages(conversationId:string,items:CloudMessage[]) {
  const now=Date.now();
  const expired=items.filter((item)=>item.expiresAt?.toDate?.()?.getTime() !== undefined && (item.expiresAt?.toDate?.()?.getTime() ?? Infinity) <= now);
  await Promise.all(expired.map((item)=>deleteDoc(doc(db,"conversations",conversationId,"messages",item.id)).catch(()=>undefined)));
}

export async function setDirectTyping(conversationId:string,userId:string,typing:boolean) {
  await updateDoc(doc(db,"conversations",conversationId),{[`typingBy.${userId}`]:typing});
}

export async function markDirectPresence(conversationId:string,userId:string) {
  await updateDoc(doc(db,"conversations",conversationId),{[`presenceBy.${userId}`]:serverTimestamp()});
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
    inviteToken: newInviteToken(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  const document = await addDoc(collection(db, "groups"), payload);
  return { ...payload, id: document.id, createdAt: null } satisfies CloudGroup;
}

function newInviteToken() {
  return crypto.randomUUID().replace(/-/g, "");
}

export async function ensureGroupInvite(groupId: string, _ownerId: string, currentToken?: string) {
  if (currentToken) return currentToken;
  const inviteToken = newInviteToken();
  await updateDoc(doc(db, "groups", groupId), { inviteToken, updatedAt: serverTimestamp() });
  return inviteToken;
}

export async function requestGroupJoin(groupId: string, inviteToken: string, ownerId: string, userId: string, userName: string) {
  const token = inviteToken.trim();
  if (!/^[a-f0-9]{32}$/i.test(token)) throw new Error("invalid-invite");
  const requestId = groupId + "-" + userId;
  await setDoc(doc(db, "groupJoinRequests", requestId), {
    groupId,
    inviteToken: token,
    ownerId,
    userId,
    userName: userName.trim().slice(0, 80) || "Membre WHAPPY",
    status: "pending",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }, { merge: true });
  return requestId;
}

export function watchGroupJoinRequests(ownerId: string, onItems: (items: CloudGroupJoinRequest[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "groupJoinRequests"), where("ownerId", "==", ownerId)), (snapshot) => {
    onItems(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudGroupJoinRequest)).filter((item) => item.status === "pending"));
  }, onError);
}

export async function approveGroupJoinRequest(request: CloudGroupJoinRequest, ownerId: string) {
  if (request.ownerId !== ownerId) throw new Error("not-owner");
  const groupRef = doc(db, "groups", request.groupId);
  const requestRef = doc(db, "groupJoinRequests", request.id);
  const batch = writeBatch(db);
  batch.update(groupRef, { memberIds: arrayUnion(request.userId), memberNames: arrayUnion(request.userName), updatedAt: serverTimestamp() });
  batch.update(requestRef, { status: "approved", reviewedAt: serverTimestamp() });
  await batch.commit();
}

export async function declineGroupJoinRequest(requestId: string) {
  await updateDoc(doc(db, "groupJoinRequests", requestId), { status: "declined", reviewedAt: serverTimestamp() });
}

export function watchWhapTexts(onItems: (items: CloudWhapText[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "whaptexts"), orderBy("createdAt", "desc"), limit(40)), (snapshot) => {
    onItems(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as CloudWhapText)));
  }, onError);
}

export async function publishWhapText(authorId: string, authorName: string, text: string, tone: WhapTextTone, kind: CloudWhapText["kind"]) {
  const value = text.trim();
  if (value.length < 3 || value.length > 600) throw new Error("invalid-whaptext");
  const document = await addDoc(collection(db, "whaptexts"), {
    authorId,
    authorName: authorName.trim().slice(0, 80) || "Membre WHAPPY",
    text: value,
    tone,
    kind,
    createdAt: serverTimestamp(),
  });
  return document.id;
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
