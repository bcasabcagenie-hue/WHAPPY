import { addDoc, collection, doc, getDocs, increment, limit, onSnapshot, orderBy, query, runTransaction, serverTimestamp, updateDoc, writeBatch } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type RoomKind = "prayer" | "technology" | "education" | "business" | "culture" | "solidarity";
export type RoomOrganizer = "church" | "organization" | "business" | "creator";
export type RoomAccess = "public" | "approval";

export const roomKindLabels: Record<RoomKind, string> = {
  prayer: "Prière & foi",
  technology: "Technologie",
  education: "Éducation",
  business: "Business",
  culture: "Culture & talents",
  solidarity: "Entraide",
};

export const roomOrganizerLabels: Record<RoomOrganizer, string> = {
  church: "Église / communauté",
  organization: "Organisation",
  business: "Business",
  creator: "Créateur",
};

export type WhappyRoom = {
  id: string;
  name: string;
  description: string;
  category: RoomKind;
  organizerType: RoomOrganizer;
  access: RoomAccess;
  guidelines: string;
  emoji: string;
  ownerId: string;
  ownerName: string;
  memberIds: string[];
  memberCount: number;
  postCount: number;
  lastPost: string;
  verified: boolean;
  createdAt?: { toDate?: () => Date } | null;
  updatedAt?: { toDate?: () => Date } | null;
};

export type WhappyRoomPost = {
  id: string;
  text: string;
  authorId: string;
  authorName: string;
  createdAt?: { toDate?: () => Date } | null;
  reactions: Record<string, string>;
  pinned: boolean;
  deleted: boolean;
};

export type NewWhappyRoom = Pick<WhappyRoom, "name" | "description" | "category" | "organizerType" | "access" | "guidelines" | "emoji">;

function mapRoom(id: string, data: Record<string, unknown>): WhappyRoom {
  const category = data.category as RoomKind;
  const organizerType = data.organizerType as RoomOrganizer;
  const access = data.access as RoomAccess;
  return {
    id,
    name: String(data.name || "Salle Whappy"),
    description: String(data.description || ""),
    category: category in roomKindLabels ? category : "technology",
    organizerType: organizerType in roomOrganizerLabels ? organizerType : "organization",
    access: access === "approval" ? "approval" : "public",
    guidelines: String(data.guidelines || "Respect, écoute et informations utiles pour la communauté."),
    emoji: String(data.emoji || "◈").slice(0, 4),
    ownerId: String(data.ownerId || ""),
    ownerName: String(data.ownerName || "Créateur Whappy"),
    memberIds: Array.isArray(data.memberIds) ? data.memberIds.map(String) : [],
    memberCount: Number(data.memberCount || 1),
    postCount: Number(data.postCount || 0),
    lastPost: String(data.lastPost || ""),
    verified: data.verified === true,
    createdAt: data.createdAt as WhappyRoom["createdAt"],
    updatedAt: data.updatedAt as WhappyRoom["updatedAt"],
  };
}

export function watchWhappyRooms(onRooms: (rooms: WhappyRoom[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "channels"), orderBy("updatedAt", "desc"), limit(100)), (snapshot) => {
    onRooms(snapshot.docs.map((item) => mapRoom(item.id, item.data())));
  }, onError);
}

export async function createWhappyRoom(ownerId: string, ownerName: string, room: NewWhappyRoom) {
  const name = room.name.trim().slice(0, 80);
  const description = room.description.trim().slice(0, 300);
  const guidelines = room.guidelines.trim().slice(0, 600);
  if (name.length < 3 || description.length < 10) throw new Error("invalid-room");
  const reference = await addDoc(collection(db, "channels"), {
    name,
    description,
    category: room.category,
    organizerType: room.organizerType,
    access: room.access,
    guidelines,
    emoji: room.emoji.slice(0, 4) || "◈",
    ownerId,
    ownerName: ownerName.trim().slice(0, 80),
    memberIds: [ownerId],
    memberCount: 1,
    postCount: 0,
    lastPost: `Bienvenue dans ${name}`,
    verified: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return reference.id;
}

export async function setRoomSubscription(roomId: string, userId: string, subscribed: boolean) {
  const reference = doc(db, "channels", roomId);
  await runTransaction(db, async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists()) throw new Error("room-not-found");
    const data = snapshot.data();
    const ownerId = String(data.ownerId || "");
    const members = Array.isArray(data.memberIds) ? data.memberIds.map(String) : [];
    if (ownerId === userId && !subscribed) throw new Error("owner-cannot-leave");
    const next = subscribed ? [...new Set([...members, userId])] : members.filter((member) => member !== userId);
    transaction.update(reference, { memberIds: next, memberCount: next.length, updatedAt: serverTimestamp() });
  });
}

function mapPost(id: string, data: Record<string, unknown>): WhappyRoomPost {
  return {
    id,
    text: String(data.text || ""),
    authorId: String(data.authorId || ""),
    authorName: String(data.authorName || "WHAPPY"),
    createdAt: data.createdAt as WhappyRoomPost["createdAt"],
    reactions: (data.reactions && typeof data.reactions === "object" ? data.reactions : {}) as Record<string, string>,
    pinned: data.pinned === true,
    deleted: data.deleted === true,
  };
}

export function watchRoomPosts(roomId: string, onPosts: (posts: WhappyRoomPost[]) => void, onError: () => void) {
  return onSnapshot(query(collection(db, "channels", roomId, "posts"), orderBy("createdAt", "asc"), limit(300)), (snapshot) => {
    onPosts(snapshot.docs.map((item) => mapPost(item.id, item.data())));
  }, onError);
}

export async function publishRoomPost(roomId: string, ownerId: string, authorName: string, text: string) {
  const value = text.trim().slice(0, 4000);
  if (!value) throw new Error("invalid-room-post");
  const room = doc(db, "channels", roomId);
  const post = doc(collection(room, "posts"));
  const batch = writeBatch(db);
  batch.set(post, { text: value, authorId: ownerId, authorName: authorName.trim().slice(0, 80), createdAt: serverTimestamp(), reactions: {}, pinned: false, deleted: false });
  batch.update(room, { lastPost: value.slice(0, 160), postCount: increment(1), updatedAt: serverTimestamp() });
  await batch.commit();
}

export async function reactToRoomPost(roomId: string, postId: string, userId: string, emoji: string) {
  if (!["❤️", "👍", "🔥", "👏", "💡"].includes(emoji)) throw new Error("invalid-room-reaction");
  await updateDoc(doc(db, "channels", roomId, "posts", postId), { [`reactions.${userId}`]: emoji });
}

export async function pinRoomPost(roomId: string, postId: string, pinned: boolean) {
  await updateDoc(doc(db, "channels", roomId, "posts", postId), { pinned });
}

export async function deleteRoomPost(roomId: string, postId: string) {
  await updateDoc(doc(db, "channels", roomId, "posts", postId), { text: "Publication supprimée", deleted: true, pinned: false });
}

export async function readRoomPosts(roomId: string) {
  const snapshot = await getDocs(query(collection(db, "channels", roomId, "posts"), orderBy("createdAt", "asc"), limit(300)));
  return snapshot.docs.map((item) => mapPost(item.id, item.data()));
}
