import { addDoc, collection, onSnapshot, orderBy, query, serverTimestamp } from "firebase/firestore";
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

type NewListing = Omit<CloudListing, "id" | "trust" | "mediaUrl">;
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
  const document = await addDoc(collection(db, "listings"), { ...listing, ownerId: userId, trust: 100, mediaUrl: mediaUrl ?? null, createdAt: serverTimestamp() });
  return { ...listing, id: document.id, trust: 100, mediaUrl } satisfies CloudListing;
}

export async function publishRequest(userId: string, request: NewRequest) {
  const document = await addDoc(collection(db, "requests"), { ...request, ownerId: userId, createdAt: serverTimestamp() });
  return { ...request, id: document.id } satisfies CloudRequest;
}
