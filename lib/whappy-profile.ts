import { doc, serverTimestamp, setDoc } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

export type WhappyProfile = {
  uid: string;
  displayName: string;
  phoneNumber: string;
  accountType?: "personal" | "business";
  verified?: boolean;
  photoUrl?: string;
};

export async function saveWhappyProfile(profile: WhappyProfile, photo?: File | null) {
  const phoneDigits = profile.phoneNumber.replace(/\D/g, "");
  let photoUrl = profile.photoUrl || "";
  if (photo) {
    if (!/^image\/(jpeg|png|webp)$/.test(photo.type) || photo.size > 8 * 1024 * 1024) throw new Error("invalid-profile-photo");
    const extension = photo.type.split("/")[1].replace("jpeg", "jpg");
    const photoRef = ref(storage, `users/${profile.uid}/profile/avatar-${Date.now()}.${extension}`);
    await uploadBytes(photoRef, photo, { contentType: photo.type });
    photoUrl = await getDownloadURL(photoRef);
  }
  await setDoc(doc(db, "users", profile.uid), {
    displayName: profile.displayName,
    phoneNumber: profile.phoneNumber,
    phoneLookup: profile.phoneNumber,
    phoneDigits,
    accountType: profile.accountType || "personal",
    verified: profile.verified ?? true,
    ...(photoUrl ? { photoUrl } : {}),
    updatedAt: serverTimestamp(),
  }, { merge: true });
  return photoUrl;
}
