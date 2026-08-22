import { addDoc, collection, deleteDoc, doc, limit, onSnapshot, orderBy, query, serverTimestamp, Timestamp, where } from "firebase/firestore";
import { deleteObject, getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

type CloudTimestamp = { toDate?: () => Date; seconds?: number; nanoseconds?: number } | null;

export type WhappyStory = {
  id: string;
  authorId: string;
  authorName: string;
  mediaUrl: string;
  mediaType: "image" | "video";
  caption: string;
  storagePath?: string;
  createdAt?: CloudTimestamp;
  expiresAt?: CloudTimestamp;
};

const STORY_LIFETIME = 24 * 60 * 60 * 1000;

function validStoryFile(file: File) {
  const extension = file.name.toLowerCase().split(".").pop() || "";
  const detectedType = file.type || (extension === "webm" ? "video/webm" : extension === "mp4" ? "video/mp4" : extension === "png" ? "image/png" : extension === "webp" ? "image/webp" : "image/jpeg");
  const image = /^image\/(jpeg|jpg|png|webp)$/.test(detectedType);
  const video = /^video\/(mp4|webm)$/.test(detectedType);
  if (!image && !video) throw new Error("story-format");
  if (image && file.size > 12 * 1024 * 1024) throw new Error("story-image-too-large");
  if (video && file.size > 50 * 1024 * 1024) throw new Error("story-video-too-large");
  return image ? "image" as const : "video" as const;
}

export async function publishStory(userId: string, authorName: string, file: File, caption: string) {
  const mediaType = validStoryFile(file);
  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, "-").slice(-90) || `${mediaType}.bin`;
  const storagePath = `stories/${userId}/${Date.now()}-${safeName}`;
  const mediaRef = ref(storage, storagePath);
  const contentType = file.type || (mediaType === "image" ? "image/jpeg" : "video/mp4");
  await uploadBytes(mediaRef, file, { contentType });
  const mediaUrl = await getDownloadURL(mediaRef);
  const now = Date.now();
  const expiresAt = Timestamp.fromMillis(now + STORY_LIFETIME);
  try {
    const story = await addDoc(collection(db, "stories"), {
      authorId: userId,
      authorName: authorName.trim().slice(0, 80),
      mediaUrl,
      mediaType,
      caption: caption.trim().slice(0, 180),
      storagePath,
      createdAt: serverTimestamp(),
      expiresAt,
    });
    return {
      id: story.id,
      authorId: userId,
      authorName: authorName.trim().slice(0, 80),
      mediaUrl,
      mediaType,
      caption: caption.trim().slice(0, 180),
      storagePath,
      createdAt: Timestamp.fromMillis(now),
      expiresAt,
    } satisfies WhappyStory;
  } catch (error) {
    await deleteObject(mediaRef).catch(() => undefined);
    throw error;
  }
}

export function watchStories(onStories: (stories: WhappyStory[]) => void, onError: () => void) {
  const stories = query(collection(db, "stories"), where("expiresAt", ">", Timestamp.now()), orderBy("expiresAt", "desc"), limit(60));
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
