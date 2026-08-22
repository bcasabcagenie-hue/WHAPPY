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

export type WepiIntent =
  | "greeting"
  | "identity"
  | "price"
  | "availability"
  | "messages"
  | "stories"
  | "business"
  | "groups"
  | "games"
  | "calls"
  | "radio"
  | "privacy"
  | "help"
  | "other";

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

function normalizePilotisText(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("fr-FR").trim();
}

function hasAny(text: string, values: string[]) {
  return values.some((value) => new RegExp(`\\b${value}\\b`, "i").test(text));
}

export function classifyWepiMessage(message: Pick<CloudMessage, "text">): WepiIntent {
  const lower = normalizePilotisText(message.text);
  if (hasAny(lower, ["bonjour", "bonsoir", "salut", "hello", "coucou"])) return "greeting";
  if (hasAny(lower, ["qui es tu", "qui es-tu", "pilotis", "wepi", "intelligence artificielle"])) return "identity";
  if (hasAny(lower, ["prix", "tarif", "coute", "cout", "combien", "budget"])) return "price";
  if (hasAny(lower, ["disponible", "disponibilite", "stock", "livraison", "livrer", "rendez vous", "rdv"])) return "availability";
  if (hasAny(lower, ["message", "repondre", "reponds", "transfert", "transferer", "vu", "lu", "conversation"])) return "messages";
  if (hasAny(lower, ["story", "stories", "statut", "photo", "video", "publier"])) return "stories";
  if (hasAny(lower, ["business", "publicite", "pub", "campagne", "booster", "region", "client"])) return "business";
  if (hasAny(lower, ["groupe", "groupes", "communaute", "communaute", "administrateur"])) return "groups";
  if (hasAny(lower, ["jeu", "jeux", "billard", "echec", "dames", "poker", "joueur"])) return "games";
  if (hasAny(lower, ["appel", "audio", "video", "live", "haut parleur", "micro"])) return "calls";
  if (hasAny(lower, ["radio", "podcast", "direct", "emission", "ecouter"])) return "radio";
  if (hasAny(lower, ["stockage", "cache", "donnees", "chiffre", "confidentialite", "securite", "cloud"])) return "privacy";
  if (hasAny(lower, ["aide", "help", "faire", "fonctionnalite", "fonctionnalites"])) return "help";
  return "other";
}

export function buildWepiResponse(message: Pick<CloudMessage, "text">, settings: WepiSettings, customerName = "") {
  const intent = classifyWepiMessage(message);
  const name = customerName.trim() ? ` ${customerName.trim().split(/\s+/)[0]}` : "";
  const business = settings.businessName.trim() || "notre activité";
  const greeting = settings.welcomeMessage.trim() || "Bonjour et merci pour votre message.";
  const tone = settings.tone === "direct" ? "Je vais à l’essentiel." : settings.tone === "expert" ? "Je vous apporte une réponse précise." : "Je suis là pour vous aider avec plaisir.";
  const assistant = settings.assistantName.trim() || "WEPI";
  const configuredInstructions = settings.instructions.trim() || "Je transmets votre demande à l’équipe.";

  const text = (() => {
    switch (intent) {
      case "greeting": return `${greeting}${name} Je suis ${assistant}, le chatbot Pilotis intégré à WAPI pour ${business}. ${tone}`;
      case "identity": return `Je suis ${assistant}, le chatbot Pilotis intégré à WAPI. Je peux vous aider dans les messages, les groupes, les Stories, Business, les appels, la radio et les jeux. Je reste transparent : je n’invente ni prix, ni disponibilité, ni action effectuée.`;
      case "price": return `Merci pour votre question${name}. Je n’invente pas de tarif : aucun catalogue prix n’est configuré pour ${business}. Ajoutez vos offres dans Business ou demandez le relais d’un membre de l’équipe. ${tone}`;
      case "availability": return `Merci${name}. Je peux enregistrer votre demande pour ${business}, mais je ne peux pas confirmer un stock ou une livraison sans donnée connectée. Un membre de l’équipe doit valider la disponibilité. ${tone}`;
      case "messages": return `Je peux vous guider pour répondre, citer un message, le transférer, suivre les vues d’un groupe ou ouvrir la conversation concernée. Dites-moi l’action à faire et le contact visé.`;
      case "stories": return `Pour une Story WAPI : ouvrez votre profil, choisissez Ajouter, puis Image, Vidéo, Texte ou un audio/podcast. Vérifiez l’aperçu et publiez. Les Stories restent rattachées au profil, elles ne sont pas affichées comme un fil public.`;
      case "business": return `Dans WAPI Business, créez une campagne, choisissez la région ciblée, le budget et la durée, puis envoyez-la en validation. Une publicité doit être identifiée comme telle et ne sera diffusée que dans la zone choisie.`;
      case "groups": return `Je peux vous aider à organiser un groupe ou une communauté : rôles administrateur, annonce, sondage, événement, fichier et modération avec validation humaine.`;
      case "games": return `Les jeux WAPI doivent ouvrir une vraie partie séparée : solo contre IA, duel en ligne, tour par tour synchronisé et audio de partie. Choisissez le jeu et le mode pour lancer une salle réelle.`;
      case "calls": return `Pour un appel WAPI, utilisez Audio ou Vidéo puis activez le haut-parleur depuis l’écran d’appel. Les appels de groupe nécessitent une salle média active ; si elle n’est pas disponible, je vous le signale au lieu de simuler des participants.`;
      case "radio": return `La Radio WAPI permet d’écouter un direct, de changer de station et de retrouver les podcasts publiés. Un épisode doit posséder une vraie source audio cloud avant d’être annoncé comme disponible.`;
      case "privacy": return `WAPI garde les messages récents en cache local pour afficher la conversation rapidement, synchronise les données cloud quand la connexion revient et ne présente jamais un cache comme une donnée confirmée. Les messages protégés restent chiffrés côté conversation.`;
      case "help": return `Je suis Pilotis dans WAPI. Essayez : « comment publier une Story ? », « créer une campagne régionale », « ouvrir un jeu en ligne », « lancer un direct radio » ou « gérer mon groupe ».`;
      default: return `${greeting}${name} J’ai reçu votre demande pour ${business}. ${configuredInstructions} Pour une réponse précise, indiquez l’action WAPI, le contact ou le service concerné. ${tone}`;
    }
  })();
  return { text, intent };
}

export function buildWepiReply(message: Pick<CloudMessage, "text">, settings: WepiSettings, customerName = "") {
  return buildWepiResponse(message, settings, customerName).text;
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
