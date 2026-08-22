import { addDoc, collection, deleteDoc, doc, getDocs, limit, onSnapshot, orderBy, query, serverTimestamp, Timestamp, where } from "firebase/firestore";
import { deleteObject, getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

type CloudTimestamp = { toDate?: () => Date; seconds?: number; nanoseconds?: number } | null;

export type WhappyStory = {
  id: string;
  authorId: string;
  authorName: string;
  mediaUrl: string;
  mediaType: "text" | "image" | "video" | "audio";
  caption: string;
  storagePath?: string;
  audienceIds?: string[];
  createdAt?: CloudTimestamp;
  expiresAt?: CloudTimestamp;
};

const STORY_LIFETIME = 24 * 60 * 60 * 1000;

function validStoryFile(file: File) {
  const extension = file.name.toLowerCase().split(".").pop() || "";
  const detectedType = file.type || (extension === "webm" ? "video/webm" : extension === "mp4" ? "video/mp4" : extension === "mp3" ? "audio/mpeg" : extension === "m4a" ? "audio/mp4" : extension === "ogg" ? "audio/ogg" : extension === "wav" ? "audio/wav" : extension === "png" ? "image/png" : extension === "webp" ? "image/webp" : "image/jpeg");
  const image = /^image\/(jpeg|jpg|png|webp)$/.test(detectedType);
  const video = /^video\/(mp4|webm)$/.test(detectedType);
  const audio = /^audio\/(mpeg|mp4|x-m4a|aac|ogg|wav|x-wav)$/.test(detectedType);
  if (!image && !video && !audio) throw new Error("story-format");
  if (image && file.size > 12 * 1024 * 1024) throw new Error("story-image-too-large");
  if (video && file.size > 50 * 1024 * 1024) throw new Error("story-video-too-large");
  if (audio && file.size > 25 * 1024 * 1024) throw new Error("story-audio-too-large");
  return image ? "image" as const : video ? "video" as const : "audio" as const;
}

export async function publishStory(userId: string, authorName: string, file: File | null, caption: string) {
  const cleanCaption = caption.trim().slice(0, 180);
  if (!file && !cleanCaption) throw new Error("story-empty");
  const mediaType = file ? validStoryFile(file) : "text" as const;
  const safeName = file?.name.replace(/[^a-zA-Z0-9._-]/g, "-").slice(-90) || "";
  const storagePath = file ? `stories/${userId}/${Date.now()}-${safeName || `${mediaType}.bin`}` : "";
  const mediaRef = storagePath ? ref(storage, storagePath) : null;
  if (file && mediaRef) {
    const contentType = file.type || (mediaType === "image" ? "image/jpeg" : mediaType === "video" ? "video/mp4" : "audio/mpeg");
    await uploadBytes(mediaRef, file, { contentType });
  }
  const mediaUrl = mediaRef ? await getDownloadURL(mediaRef) : "";
  const directSnapshot = await getDocs(query(collection(db, "conversations"), where("memberIds", "array-contains", userId), limit(500)));
  const audienceIds = Array.from(new Set([userId, ...directSnapshot.docs.flatMap((item) => {
    const data = item.data();
    const members = Array.isArray(data.memberIds) ? data.memberIds.filter((value): value is string => typeof value === "string") : [];
    return data.conversationType === "direct" || members.length === 2 ? members : [];
  })])).slice(0, 500);
  const now = Date.now();
  const expiresAt = Timestamp.fromMillis(now + STORY_LIFETIME);
  try {
    const story = await addDoc(collection(db, "stories"), {
      authorId: userId,
      authorName: authorName.trim().slice(0, 80),
      mediaUrl,
      mediaType,
      caption: cleanCaption,
      storagePath,
      audienceIds,
      createdAt: serverTimestamp(),
      expiresAt,
    });
    return {
      id: story.id,
      authorId: userId,
      authorName: authorName.trim().slice(0, 80),
      mediaUrl,
      mediaType,
      caption: cleanCaption,
      storagePath,
      audienceIds,
      createdAt: Timestamp.fromMillis(now),
      expiresAt,
    } satisfies WhappyStory;
  } catch (error) {
    if (mediaRef) await deleteObject(mediaRef).catch(() => undefined);
    throw error;
  }
}

export function watchStories(userId: string, onStories: (stories: WhappyStory[]) => void, onError: () => void) {
  const stories = query(collection(db, "stories"), where("audienceIds", "array-contains", userId), where("expiresAt", ">", Timestamp.now()), orderBy("expiresAt", "desc"), limit(60));
  return onSnapshot(stories, (snapshot) => {
    const now = Date.now();
    onStories(snapshot.docs.map((item) => ({ id: item.id, ...item.data() } as WhappyStory)).filter((item) => (item.expiresAt?.toDate?.()?.getTime() || now + 1) > now));
  }, onError);
}

export async function removeStory(story: WhappyStory, userId: string) {
  if (story.authorId !== userId) throw new Error("story-owner");
  await deleteDoc(doc(db, "stories", story.id));
  if (story.storagePath) await deleteObject(ref(storage, story.storagePath)).catch(() => undefined);
}
