import { doc, serverTimestamp, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type WhappyProfile = {
  uid: string;
  displayName: string;
  phoneNumber: string;
};

export async function saveWhappyProfile(profile: WhappyProfile) {
  const phoneDigits = profile.phoneNumber.replace(/\D/g, "");
  await setDoc(doc(db, "users", profile.uid), {
    displayName: profile.displayName,
    phoneNumber: profile.phoneNumber,
    phoneLookup: profile.phoneNumber,
    phoneDigits,
    updatedAt: serverTimestamp(),
  }, { merge: true });
}
