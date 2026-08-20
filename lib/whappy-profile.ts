import { doc, serverTimestamp, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type WhappyProfile = {
  uid: string;
  displayName: string;
  phoneNumber: string;
  accountType?: "personal" | "business";
  verified?: boolean;
};

export async function saveWhappyProfile(profile: WhappyProfile) {
  const phoneDigits = profile.phoneNumber.replace(/\D/g, "");
  await setDoc(doc(db, "users", profile.uid), {
    displayName: profile.displayName,
    phoneNumber: profile.phoneNumber,
    phoneLookup: profile.phoneNumber,
    phoneDigits,
    accountType: profile.accountType || "personal",
    verified: profile.verified ?? true,
    updatedAt: serverTimestamp(),
  }, { merge: true });
}
