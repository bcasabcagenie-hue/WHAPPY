import { doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import type { WhappyRoom, WhappyRoomPost } from "@/lib/whappy-rooms";
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

export type WepiRoomPilotSettings = {
  roomId: string;
  ownerId: string;
  enabled: boolean;
  autoModeration: boolean;
  announcementAssist: boolean;
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
  await setDoc(doc(db, "users", ownerId), {
    wepiEnabled: sanitized.enabled === true,
    wepiName: sanitized.assistantName || "WEPI",
    wepiBusinessName: sanitized.businessName || "",
  }, { merge: true });
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

export const defaultWepiRoomPilot = (roomId: string, ownerId: string): WepiRoomPilotSettings => ({
  roomId,
  ownerId,
  enabled: false,
  autoModeration: true,
  announcementAssist: true,
});

export function watchWepiRoomPilot(roomId: string, ownerId: string, onSettings: (settings: WepiRoomPilotSettings) => void, onError: () => void) {
  return onSnapshot(doc(db, "channels", roomId, "pilot", "wepi"), (snapshot) => {
    onSettings(snapshot.exists() ? ({ ...defaultWepiRoomPilot(roomId, ownerId), ...snapshot.data(), roomId, ownerId } as WepiRoomPilotSettings) : defaultWepiRoomPilot(roomId, ownerId));
  }, onError);
}

export async function saveWepiRoomPilot(settings: WepiRoomPilotSettings) {
  await setDoc(doc(db, "channels", settings.roomId, "pilot", "wepi"), {
    roomId: settings.roomId,
    ownerId: settings.ownerId,
    enabled: settings.enabled,
    autoModeration: settings.autoModeration,
    announcementAssist: settings.announcementAssist,
    updatedAt: serverTimestamp(),
  }, { merge: true });
}

export function buildWepiRoomSuggestion(room: Pick<WhappyRoom, "category" | "name" | "memberCount">, posts: WhappyRoomPost[]) {
  const pinned = posts.filter((post) => post.pinned).length;
  const ideas: Record<WhappyRoom["category"], string> = {
    prayer: `Préparer un rappel bienveillant pour le prochain temps de prière de « ${room.name} » et inviter les membres à partager leurs intentions en privé.`,
    technology: `Lancer un sujet concret dans « ${room.name} » : demander à la communauté de partager un outil, une ressource ou une solution locale cette semaine.`,
    education: `Publier un mini-défi d’apprentissage dans « ${room.name} » et proposer aux membres de revenir avec leur résultat dans 48 heures.`,
    business: `Annoncer une rencontre ou une opportunité vérifiable dans « ${room.name} », avec une date, un lieu et une action claire pour les membres.`,
    culture: `Mettre en avant un talent de la communauté dans « ${room.name} » et inviter les membres à recommander une création à découvrir.`,
    solidarity: `Identifier un besoin concret dans « ${room.name} », préciser comment aider et rappeler de protéger les informations personnelles.`,
  };
  return `${ideas[room.category]} ${posts.length ? `La salle compte ${posts.length} publication${posts.length > 1 ? "s" : ""} récente${posts.length > 1 ? "s" : ""} et ${pinned} information${pinned > 1 ? "s" : ""} épinglée${pinned > 1 ? "s" : ""}.` : `Elle compte déjà ${room.memberCount} membre${room.memberCount > 1 ? "s" : ""} : c’est le bon moment pour lancer le premier rendez-vous.`}`;
}
