import { doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import type { CloudMessage } from "@/lib/whappy-data";

export type WepiTone = "chaleureux" | "expert" | "direct";

export type WepiSettings = {
  ownerId: string;
  enabled: boolean;
  autoReply: boolean;
  assistantName: string;
  businessName: string;
  tone: WepiTone;
  welcomeMessage: string;
  instructions: string;
  updatedAt?: { toDate?: () => Date } | null;
};

export const defaultWepiSettings = (ownerId: string, businessName = "") : WepiSettings => ({
  ownerId,
  enabled: false,
  autoReply: true,
  assistantName: "WEPI",
  businessName,
  tone: "chaleureux",
  welcomeMessage: "Bonjour et merci pour votre message.",
  instructions: "Répondre clairement aux questions commerciales et proposer un échange humain si nécessaire.",
});

export function watchWepiSettings(ownerId: string, onSettings: (settings: WepiSettings | null) => void, onError: () => void) {
  return onSnapshot(doc(db, "users", ownerId, "wepi", "settings"), (snapshot) => {
    onSettings(snapshot.exists() ? ({ ownerId, ...snapshot.data() } as WepiSettings) : null);
  }, onError);
}

export async function saveWepiSettings(ownerId: string, changes: Partial<Omit<WepiSettings, "ownerId" | "updatedAt">>) {
  const sanitized: Partial<WepiSettings> = { ...changes };
  if (sanitized.assistantName !== undefined) sanitized.assistantName = sanitized.assistantName.trim().slice(0, 60) || "WEPI";
  if (sanitized.businessName !== undefined) sanitized.businessName = sanitized.businessName.trim().slice(0, 100);
  if (sanitized.welcomeMessage !== undefined) sanitized.welcomeMessage = sanitized.welcomeMessage.trim().slice(0, 240);
  if (sanitized.instructions !== undefined) sanitized.instructions = sanitized.instructions.trim().slice(0, 600);
  await setDoc(doc(db, "users", ownerId, "wepi", "settings"), { ownerId, ...sanitized, updatedAt: serverTimestamp() }, { merge: true });
}

export function buildWepiReply(message: Pick<CloudMessage, "text">, settings: WepiSettings, customerName = "") {
  const text = message.text.trim();
  const lower = text.toLocaleLowerCase("fr-FR");
  const name = customerName.trim() ? ` ${customerName.trim().split(/\s+/)[0]}` : "";
  const business = settings.businessName.trim() || "notre activité";
  const greeting = settings.welcomeMessage.trim() || "Bonjour et merci pour votre message.";
  const tone = settings.tone === "direct" ? "Je vais à l’essentiel." : settings.tone === "expert" ? "Je vous apporte une réponse précise." : "Je suis là pour vous aider avec plaisir.";

  if (/\b(bonjour|bonsoir|salut|hello|coucou)\b/.test(lower)) {
    return `${greeting}${name} Je suis ${settings.assistantName || "WEPI"}, l’assistant de ${business}. ${tone}`;
  }
  if (/\b(prix|tarif|co[uû]te|co[uû]t|combien|budget)\b/.test(lower)) {
    return `Merci pour votre question${name}. ${settings.assistantName || "WEPI"} n’a pas encore le tarif exact dans cette conversation. Je vérifie pour vous et un membre de l’équipe peut prendre le relais. ${tone}`;
  }
  if (/\b(disponible|disponibilit|stock|livraison|livrer|rendez-vous|rdv)\b/.test(lower)) {
    return `Merci${name}, votre demande concernant ${business} est bien reçue. Je vérifie la disponibilité et nous revenons vers vous rapidement. ${tone}`;
  }
  return `${greeting}${name} Votre message est bien reçu par ${business}. ${settings.instructions.trim() || "Je transmets votre demande à l’équipe."} Un membre de l’équipe peut prendre le relais si votre demande nécessite une vérification.`;
}
