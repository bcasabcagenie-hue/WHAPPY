"use client";

import Image from "next/image";
import dynamic from "next/dynamic";
import { browserLocalPersistence, ConfirmationResult, onAuthStateChanged, RecaptchaVerifier, setPersistence, signInWithPhoneNumber, signOut, updateProfile } from "firebase/auth";
import { DragEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { auth } from "@/lib/firebase";
import { cancelOrder, createGroup, createOrder, findWhappyUserById, publishListing, publishRequest, removeListing, requestGroupJoin, sendConversationMessage, updateListing, watchConversationMessages, watchUserGroups, watchUserOrders, watchWhappyData, watchWhappyUsersById, type CloudGroup, type CloudMessage, type CloudOrder } from "@/lib/whappy-data";
import { callingCountries } from "@/lib/countries";
import { playMediaAddedSound, playOfferSuccessSound } from "@/lib/whappy-sounds";

function messageTime(message: CloudMessage) {
  return message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) || "À l’instant";
}
import { saveWhappyProfile } from "@/lib/whappy-profile";
import { readWhappyCache, writeWhappyCache } from "@/lib/whappy-local-cache";
import type { BroadcastConfig } from "@/app/components/BroadcastStudio";
import type { RadioSession } from "@/app/components/RadioStudio";
import type { CartLine, CheckoutDraft } from "@/app/components/CommercePanels";
import { RealTimeInbox } from "@/app/components/RealTimeInbox";
import { NotificationCenter } from "@/app/components/NotificationCenter";
import { ProfileEditor } from "@/app/components/ProfileEditor";
import { SocialControlDeck } from "@/app/components/SocialControlDeck";
import type { CallSignal } from "@/lib/whappy-calls";
import { watchIncomingCalls } from "@/lib/whappy-calls";
import { recordAdEvent, watchActiveCampaigns, type AdCampaign } from "@/lib/whappy-business";
import type { DirectMember } from "@/lib/whappy-data";

// Feature islands are downloaded only when opened. The inbox stays immediate.
const BroadcastStudio = dynamic(() => import("@/app/components/BroadcastStudio").then((m) => m.BroadcastStudio), { ssr: false });
const RadioStudio = dynamic(() => import("@/app/components/RadioStudio").then((m) => m.RadioStudio), { ssr: false });
const TwinRecorder = dynamic(() => import("@/app/components/TwinRecorder").then((m) => m.TwinRecorder), { ssr: false });
const TwinEngineStudio = dynamic(() => import("@/app/components/TwinEngineStudio").then((m) => m.TwinEngineStudio), { ssr: false });
const CallRoom = dynamic(() => import("@/app/components/CallRoom").then((m) => m.CallRoom), { ssr: false });
const CartPanel = dynamic(() => import("@/app/components/CommercePanels").then((m) => m.CartPanel), { ssr: false });
const ProductPanel = dynamic(() => import("@/app/components/CommercePanels").then((m) => m.ProductPanel), { ssr: false });
const SellerDashboard = dynamic(() => import("@/app/components/SellerDashboard").then((m) => m.SellerDashboard), { ssr: false });
const OrdersPanel = dynamic(() => import("@/app/components/OrdersPanel").then((m) => m.OrdersPanel), { ssr: false });
const ContactsSpace = dynamic(() => import("@/app/components/SuperHub").then((m) => m.ContactsSpace), { ssr: false });
const SuperHub = dynamic(() => import("@/app/components/SuperHub").then((m) => m.SuperHub), { ssr: false });
const BusinessStudio = dynamic(() => import("@/app/components/BusinessStudio").then((m) => m.BusinessStudio), { ssr: false });
const BusinessGrowthTools = dynamic(() => import("@/app/components/BusinessGrowthTools").then((m) => m.BusinessGrowthTools), { ssr: false });
const RoomsSpace = dynamic(() => import("@/app/components/RoomsSpace").then((m) => m.RoomsSpace), { ssr: false });
const HappyFounderDashboard = dynamic(() => import("@/app/components/HappyFounderDashboard").then((m) => m.HappyFounderDashboard), { ssr: false });
const WhappyPulse = dynamic(() => import("@/app/components/WhappyPulse").then((m) => m.WhappyPulse), { ssr: false });
const WhappyNow = dynamic(() => import("@/app/components/WhappyNow").then((m) => m.WhappyNow), { ssr: false });
const WhappyExperience = dynamic(() => import("@/app/components/WhappyExperience").then((m) => m.WhappyExperience), { ssr: false });
const WhappyMotion = dynamic(() => import("@/app/components/WhappyMotion").then((m) => m.WhappyMotion), { ssr: false });
const WhapTextStudio = dynamic(() => import("@/app/components/WhapTextStudio").then((m) => m.WhapTextStudio), { ssr: false });
const StoryStudio = dynamic(() => import("@/app/components/StoryStudio").then((m) => m.StoryStudio), { ssr: false });

type Space = "orbit" | "live" | "market" | "barter" | "seek" | "inbox" | "calls" | "contacts" | "rooms" | "radio" | "services" | "twin" | "business" | "games";
type Listing = { id: string | number; title: string; price: string; place: string; seller: string; mark: string; tone: string; category: string; mode: "vente" | "troc"; trust: number; mediaUrl?: string; ownerId?: string; sellerPhone?: string; status?: "active" | "reserved" | "sold"; };
type RequestItem = { id: string | number; title: string; details: string; place: string; reward: string; urgent: boolean; category: "Produits" | "Services" | "Situations"; };
type DemoOffer = { id: number; text: string; mediaUrl?: string; mediaKind?: "image" | "video"; mediaName?: string };

const ANDROID_APP = {
  url: "/downloads/WHAPPY-Android-1.5.7-native.apk",
  version: "1.5.7 native",
  size: "Universel",
  minimum: "Android 8.0+",
} as const;
const WHAPPY_DOWNLOAD_EVENTS_KEY = "whappy:download-events";

const listings: Listing[] = [
  { id: 1, title: "MacBook Air M3 · Comme neuf", price: "750 000 FCFA", place: "Poto-Poto · 1,2 km", seller: "Junior K.", mark: "JK", tone: "blue", category: "Tech", mode: "vente", trust: 98 },
  { id: 2, title: "Canapé modulable en velours", price: "Échange accepté", place: "Bacongo · 3,4 km", seller: "Maison Noki", mark: "MN", tone: "violet", category: "Maison", mode: "troc", trust: 94 },
  { id: 3, title: "Sneakers édition limitée", price: "85 000 FCFA", place: "Centre-ville · 800 m", seller: "Mokabi Store", mark: "MS", tone: "orange", category: "Mode", mode: "vente", trust: 99 },
  { id: 4, title: "Studio photo — 3 heures", price: "Contre identité visuelle", place: "Moungali · 2,1 km", seller: "Nadia M.", mark: "NM", tone: "blue", category: "Services", mode: "troc", trust: 97 },
];

const requests: RequestItem[] = [
  { id: 1, title: "Je cherche un développeur Flutter", details: "Mission de 3 semaines · Budget disponible", place: "À distance", reward: "450 000 FCFA", urgent: true, category: "Services" },
  { id: 2, title: "Besoin d'un groupe électrogène ce soir", details: "Pour un événement de 18 h à minuit", place: "Talangaï · 6 km", reward: "Location", urgent: true, category: "Situations" },
  { id: 3, title: "Où trouver du tissu wax premium ?", details: "Recherche fournisseur pour 60 mètres", place: "Brazzaville", reward: "Bon plan", urgent: false, category: "Produits" },
  { id: 4, title: "Cours de guitare contre cours d'anglais", details: "Deux séances par semaine", place: "Moungali · 3 km", reward: "Troc", urgent: false, category: "Services" },
];

const lives = [
  { host: "Mokabi Studio", title: "Nouvelle collection · essayage en direct", viewers: "2,8 k", product: "Veste N'Tela", price: "65 000", tone: "fashion", badge: "LIVE SHOP", poster: "/live/mokabi-studio.png", stream: "/live/mokabi-studio.mp4", location: "Poto-Poto · Brazzaville" },
  { host: "Chef Grâce", title: "Secrets du poulet moambe moderne", viewers: "1,4 k", product: "Masterclass", price: "12 000", tone: "food", badge: "EN DIRECT", poster: "/live/chef-grace.png", stream: "/live/chef-grace.mp4", location: "Bacongo · Brazzaville" },
  { host: "Tech House", title: "Test sans filtre : les meilleurs smartphones", viewers: "963", product: "Galaxy S26", price: "490 000", tone: "tech", badge: "DÉMO LIVE", poster: "/live/tech-house.png", stream: "/live/tech-house.mp4", location: "Centre-ville · Brazzaville" },
];

const WHAPPY_FOUNDER_PHONE = "242065465808";
const WHAPPY_FOUNDER_NAME = "Happy";
const WHAPPY_BUSINESS_NAME = "Wapi by BCA";
const WHAPPY_FALLBACK_NAME = "Utilisateur WAPI";

type GiftCarrier = "acheteur" | "offreur";
type LiveGift = {
  id: string;
  icon: string;
  name: string;
  price: number;
  detail: string;
  hearts: number;
};
const liveGifts: LiveGift[] = [
  { id: "spark", icon: "✨", name: "Éclat pro", price: 25000, detail: "Un encouragement premium visible dans le direct", hearts: 8 },
  { id: "bouquet", icon: "💐", name: "Bouquet élite", price: 60000, detail: "Mettez en avant votre soutien avec distinction", hearts: 16 },
  { id: "fire", icon: "🔥", name: "Flamme boost", price: 120000, detail: "Débloquez un focus visible pour votre marque", hearts: 32 },
  { id: "crown", icon: "👑", name: "Couronne signature", price: 240000, detail: "Un cadeau haut de gamme avec mention dédiée", hearts: 64 },
];
const liveGiftTopUps = [20_000, 40_000, 80_000, 160_000] as const;
const LIVE_STAGE_MAX = 10;
type LiveGiftCarrier = { gift: LiveGift; note: string; carrier: GiftCarrier };
type GiftLedger = { count: number; amount: number };
type GiftWalletLedger = Record<GiftCarrier, GiftLedger>;
type ModerationAction = "pin" | "unpin" | "hide" | "restore";
type ModerationEvent = {
  id: number;
  action: ModerationAction;
  commentId: string;
  commentName: string;
  by: string;
  at: number;
};
type LiveComment = { id: string; name: string; text: string; hearts: number; isHost?: boolean; sentAt: number; pinned?: boolean; hidden?: boolean };

const liveStageCandidates = ["Vous", "Amina", "Junior", "Grâce", "Maya", "Patrick", "Nadia", "Samuel", "Léna"];
const LIVE_NOW = Date.now();

function formatLiveMoney(value: number) { return new Intl.NumberFormat("fr-FR").format(value); }

function isHappyFounderPhone(phone?: string | null) {
  return (phone || "").replace(/\D/g, "") === WHAPPY_FOUNDER_PHONE;
}

function withTimeout<T>(promise: Promise<T>, milliseconds: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => reject({ code: "auth/verification-timeout" }), milliseconds);
    promise.then(
      (value) => { window.clearTimeout(timeout); resolve(value); },
      (error) => { window.clearTimeout(timeout); reject(error); },
    );
  });
}

const messages = [
  { name: "Amina M.", text: "Le troc est accepté pour le canapé ?", time: "Maintenant", mark: "AM", color: "#1C1C74", unread: 2, mood: "Cherche une belle pièce pour son salon", badge: "ACHETEUSE FIABLE", streak: 12, verified: true, accountType: "personal" as const },
  { name: "Junior K.", text: "Je peux livrer le MacBook cet après-midi.", time: "12:08", mark: "JK", color: "#1C1C74", unread: 1, mood: "Disponible pour une livraison rapide", badge: "VENDEUR VÉRIFIÉ", streak: 28, verified: true, accountType: "personal" as const },
  { name: "Mokabi Store", text: "Votre commande est prête ✦", time: "11:42", mark: "MS", color: "#1C1C74", unread: 0, mood: "Collection N'Tela en direct ce soir", badge: "BOUTIQUE PRO", streak: 54, verified: true, accountType: "business" as const },
  { name: "Design Crew", text: "Nadia : rendez-vous confirmé demain", time: "Hier", mark: "DC", color: "#1C1C74", unread: 0, mood: "Créateurs disponibles cette semaine", badge: "GROUPE ACTIF", streak: 19, verified: true, accountType: "community" as const },
  { name: "Maison Noki", text: "Canapé disponible pour échange ou vente.", time: "10:26", mark: "MN", color: "#1C1C74", unread: 0, mood: "Maison et décoration à Bacongo", badge: "VENDEUR VÉRIFIÉ", streak: 17, verified: true, accountType: "business" as const },
  { name: "Nadia M.", text: "Studio photo disponible cette semaine.", time: "09:14", mark: "NM", color: "#1C1C74", unread: 0, mood: "Service local · Moungali", badge: "PROFESSIONNELLE VÉRIFIÉE", streak: 21, verified: true, accountType: "personal" as const },
];

function Mark({ children, color, small = false }: { children: React.ReactNode; color?: string; small?: boolean }) {
  return <span className={`op-mark ${small ? "small" : ""}`} style={color ? { background: color } : undefined}>{children}</span>;
}

function PremiumOfferModal({ busy, onClose, onSubmit }: { busy: boolean; onClose: () => void; onSubmit: (event: FormEvent) => void; notify?: (text: string) => void }) {
  return <div className="modal-layer" role="dialog" aria-modal="true" aria-label="Faire une offre"><form className="action-modal" onSubmit={onSubmit}><button type="button" className="modal-close" onClick={onClose} aria-label="Fermer">×</button><span className="modal-icon">⇄</span><small>WAPI ACTION</small><h2>Faire une offre</h2><p>Proposez un prix ou un échange directement dans la conversation.</p><label>Votre proposition<input name="offer" required placeholder="Prix ou échange souhaité" /></label><label>Message<textarea name="message" placeholder="Ajoutez un détail…" /></label><button className="modal-submit" type="submit" disabled={busy}>{busy ? "Envoi sécurisé…" : "Envoyer l’offre ↗"}</button></form></div>;
}

export default function Home() {
  const [authenticated, setAuthenticated] = useState(false);
  const [authStep, setAuthStep] = useState<"phone" | "code" | "profile">("phone");
  const [countryCode, setCountryCode] = useState("+242");
  const [phone, setPhone] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [profileName, setProfileName] = useState("");
  const [profilePhotoUrl, setProfilePhotoUrl] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  // Start with the server-safe access shell; Firebase replaces it silently
  // as soon as its persisted session is observed on the client.
  const [authReady, setAuthReady] = useState(true);
  const [captchaMode, setCaptchaMode] = useState<"invisible" | "visible">("invisible");
  const [authStatus, setAuthStatus] = useState("");
  const [authError, setAuthError] = useState("");
  const confirmationRef = useRef<ConfirmationResult | null>(null);
  const recaptchaRef = useRef<RecaptchaVerifier | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const recordedAdsRef = useRef(new Set<string>());
  const handledGroupInviteRef = useRef(false);
  const [space, setSpace] = useState<Space>("inbox");
  const [accountMode, setAccountMode] = useState<"personal" | "business">("personal");
  const [search, setSearch] = useState("");
  const [marketFilter, setMarketFilter] = useState("Tout");
  const [saved, setSaved] = useState<Record<string, boolean>>({});
  const [toast, setToast] = useState("");
  const [modal, setModal] = useState<"sell" | "seek" | "live" | "message" | null>(null);
  const [liveIndex, setLiveIndex] = useState<number | null>(null);
  const [, setTwinStep] = useState(1);
  const [consent, setConsent] = useState(false);
  const [broadcast, setBroadcast] = useState<BroadcastConfig | null>(null);
  const [customListings, setCustomListings] = useState<Listing[]>([]);
  const [customRequests, setCustomRequests] = useState<RequestItem[]>([]);
  const [profileOpen, setProfileOpen] = useState(false);
  const [shopOpen, setShopOpen] = useState(false);
  const [userId, setUserId] = useState("");
  const [syncStatus, setSyncStatus] = useState<"local" | "syncing" | "synced" | "offline">("local");
  const [publishBusy, setPublishBusy] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Listing | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const [orders, setOrders] = useState<CloudOrder[]>([]);
  const [groups, setGroups] = useState<CloudGroup[]>([]);
  const [ordersOpen, setOrdersOpen] = useState(false);
  const [pulseOpen, setPulseOpen] = useState(false);
  const [directCompose, setDirectCompose] = useState(0);
  const [demoOffer, setDemoOffer] = useState<DemoOffer | null>(null);
  const [directPhone, setDirectPhone] = useState("");
  const [directPeer, setDirectPeer] = useState<DirectMember | null>(null);
  const [demoContactName, setDemoContactName] = useState("");
  const [activeAds, setActiveAds] = useState<AdCampaign[]>([]);
  const [call, setCall] = useState<{ contact:string; video:boolean; peer?:DirectMember; incoming?:CallSignal } | null>(null);
  const [radioSession, setRadioSession] = useState<RadioSession>({ status: "offline", title: "Wapi FM", elapsed: 0, listeners: 0, micOn: false });
  const [demoReady, setDemoReady] = useState(false);
  const isFounderAccount = isHappyFounderPhone(auth.currentUser?.phoneNumber);
  const demoMode = authenticated && !userId;
  const founderProfile = isFounderAccount || demoMode;
  const authDisplayName = (auth.currentUser?.displayName || profileName || "").trim();
  const userDisplayName = authDisplayName || WHAPPY_FALLBACK_NAME;
  const accountName = founderProfile ? WHAPPY_FOUNDER_NAME : userDisplayName;
  const businessName = founderProfile ? WHAPPY_BUSINESS_NAME : accountName;
  const directUser = useMemo<DirectMember | null>(() => userId ? { uid: userId, displayName: userDisplayName, phoneNumber: auth.currentUser?.phoneNumber || "", photoUrl: profilePhotoUrl || undefined, verified: true, accountType: accountMode, businessName: businessName } : null, [userId, profilePhotoUrl, userDisplayName, accountMode, businessName]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      if (!demoMode) { setDemoReady(false); return; }
      try {
        const stored = JSON.parse(localStorage.getItem("whappy-demo-workspace") || "null") as null | { listings?: Listing[]; requests?: RequestItem[]; cart?: CartLine[]; orders?: CloudOrder[]; groups?: CloudGroup[]; saved?: Record<string, boolean> };
        if (stored) {
          if (Array.isArray(stored.listings)) setCustomListings(stored.listings);
          if (Array.isArray(stored.requests)) setCustomRequests(stored.requests);
          if (Array.isArray(stored.cart)) setCart(stored.cart);
          if (Array.isArray(stored.orders)) setOrders(stored.orders);
          if (Array.isArray(stored.groups)) setGroups(stored.groups);
          if (stored.saved && typeof stored.saved === "object") setSaved(stored.saved);
        }
      } catch { /* L’espace reste utilisable pendant la session. */ }
      setDemoReady(true);
    });
    return () => { active = false; };
  }, [demoMode]);

  useEffect(() => {
    if (!demoMode || !demoReady) return;
    try { localStorage.setItem("whappy-demo-workspace", JSON.stringify({ listings: customListings, requests: customRequests, cart, orders, groups, saved })); }
    catch { /* Le navigateur peut bloquer la persistance privée. */ }
  }, [demoMode, demoReady, customListings, customRequests, cart, orders, groups, saved]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      try {
        const savedCountry = localStorage.getItem("whappy-last-country-code");
        const savedPhone = localStorage.getItem("whappy-last-phone");
        if (savedCountry) setCountryCode(savedCountry);
        if (savedPhone) setPhone(savedPhone);
      } catch {
        // Private browsing can disable local storage; Firebase auth still works.
      }
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let disposed = false;
    // Subscribe first: Firebase can restore an existing browser session while
    // persistence is being configured, avoiding a needless logout screen.
    const unsubscribe = onAuthStateChanged(auth, (user) => {
        setAuthReady(true);
        if (!user) {
          setProfileName("");
          setProfilePhotoUrl("");
          setUserId("");
          setSyncStatus("local");
          setAuthenticated(false);
          setAuthStep("phone");
          return;
        }
        const hasPhone = Boolean(user.phoneNumber);
        const hasProfile = Boolean(user.displayName?.trim());
        const accountReady = hasPhone && hasProfile;
        setProfileName(user?.displayName?.trim() || "");
        setProfilePhotoUrl(user?.photoURL || "");
        if (user?.phoneNumber) {
          try { localStorage.setItem("whappy-last-phone-e164", user.phoneNumber); } catch { /* optional convenience only */ }
        }
        setUserId(user?.uid || "");
        setSyncStatus(user?.uid ? "syncing" : "local");
        if (!hasPhone) {
          setAuthenticated(false);
          setAuthStep("phone");
          return;
        }
        if (accountReady) {
          setAuthenticated(true);
          return;
        }
        // Existing Wapi accounts created before Firebase display names were
        // enabled can still have their profile in Firestore. Restore it once,
        // so a returning web user is not incorrectly asked to create a profile.
        setAuthenticated(false);
        setAuthStep("profile");
        void (user?.uid ? findWhappyUserById(user.uid) : Promise.resolve(null)).then((profile) => {
          if (disposed || !profile?.displayName?.trim()) return;
          const restoredName = profile.displayName.trim();
          setProfileName(restoredName);
          setProfilePhotoUrl(profile.photoUrl || user.photoURL || "");
          void updateProfile(user, { displayName: restoredName }).catch(() => {});
          setAuthenticated(true);
        }).catch(() => {});
    });
    void setPersistence(auth, browserLocalPersistence).catch(() => {
      // Some privacy-focused browsers block IndexedDB/local persistence. Firebase
      // still keeps the active tab authenticated, so the flow remains usable.
    });
    return () => {
      disposed = true;
      unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!userId) return;
    return watchWhappyUsersById([userId], (profiles) => {
      const liveProfile = profiles[userId];
      if (!liveProfile) return;
      if (liveProfile.displayName?.trim()) setProfileName(liveProfile.displayName.trim());
      setProfilePhotoUrl(liveProfile.photoUrl || auth.currentUser?.photoURL || "");
    }, () => {});
  }, [userId]);

  useEffect(() => () => {
    recaptchaRef.current?.clear();
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
  }, []);

  useEffect(() => {
    function openPulse(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setPulseOpen(true); }
    }
    window.addEventListener("keydown", openPulse);
    return () => window.removeEventListener("keydown", openPulse);
  }, []);

  useEffect(() => {
    const updateConnectivity = () => {
      if (!navigator.onLine) setSyncStatus("offline");
      else if (userId) setSyncStatus("syncing");
    };
    window.addEventListener("online", updateConnectivity);
    window.addEventListener("offline", updateConnectivity);
    return () => {
      window.removeEventListener("online", updateConnectivity);
      window.removeEventListener("offline", updateConnectivity);
    };
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    let active = true;
    const cachedListings = readWhappyCache<Listing[]>("listings", userId);
    const cachedRequests = readWhappyCache<RequestItem[]>("requests", userId);
    const cachedOrders = readWhappyCache<CloudOrder[]>("orders", userId);
    const cachedGroups = readWhappyCache<CloudGroup[]>("groups", userId);
    queueMicrotask(() => {
      if (!active) return;
      if (cachedListings) setCustomListings(cachedListings);
      if (cachedRequests) setCustomRequests(cachedRequests);
      if (cachedOrders) setOrders(cachedOrders);
      if (cachedGroups) setGroups(cachedGroups);
      if (!navigator.onLine) setSyncStatus("offline");
    });
    return () => { active = false; };
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return watchWhappyData(
      (items) => {
        const next = items.map((item) => ({ ...item, tone: "blue" as const }));
        setCustomListings(next);
        writeWhappyCache("listings", userId, next);
        setSyncStatus("synced");
      },
      (items) => { setCustomRequests(items); writeWhappyCache("requests", userId, items); setSyncStatus("synced"); },
      () => setSyncStatus("offline"),
    );
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return watchUserOrders(userId, (items) => { setOrders(items); writeWhappyCache("orders", userId, items); setSyncStatus("synced"); }, () => setSyncStatus("offline"));
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return watchUserGroups(userId, (items) => { setGroups(items); writeWhappyCache("groups", userId, items); setSyncStatus("synced"); }, () => setSyncStatus("offline"));
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    writeWhappyCache("listings", userId, customListings);
  }, [customListings, userId]);

  useEffect(() => {
    if (!userId) return;
    writeWhappyCache("requests", userId, customRequests);
  }, [customRequests, userId]);

  useEffect(() => {
    if (!userId) return;
    writeWhappyCache("orders", userId, orders);
  }, [orders, userId]);

  useEffect(() => {
    if (!userId) return;
    writeWhappyCache("groups", userId, groups);
  }, [groups, userId]);

  useEffect(() => {
    if (!userId || handledGroupInviteRef.current) return;
    const parameters = new URLSearchParams(window.location.search);
    const groupId = parameters.get("group") || "";
    const inviteToken = parameters.get("invite") || "";
    const ownerId = parameters.get("owner") || "";
    if (!groupId || !inviteToken || !ownerId) return;
    handledGroupInviteRef.current = true;
    void requestGroupJoin(groupId, inviteToken, ownerId, userId, auth.currentUser?.displayName || profileName || "Membre WAPI")
      .then(() => {
        notify("Votre demande a été envoyée à l’administrateur du groupe.");
        window.history.replaceState({}, "", window.location.pathname);
      })
      .catch(() => notify("Ce lien d’invitation est invalide, expiré ou indisponible."));
  }, [userId, profileName]);

  useEffect(()=>{if(!userId)return;return watchIncomingCalls(userId,(incoming)=>setCall((current)=>current||{contact:incoming.callerName,video:incoming.video,incoming}));},[userId]);
  useEffect(()=>{if(!userId)return;return watchActiveCampaigns(setActiveAds,()=>setSyncStatus("offline"));},[userId]);
  useEffect(()=>{const campaign=activeAds[0];if(!campaign||!userId||recordedAdsRef.current.has(campaign.id))return;recordedAdsRef.current.add(campaign.id);void recordAdEvent(campaign,userId,"impression").catch(()=>{});},[activeAds,userId]);

  const filtered = useMemo(() => [...customListings, ...listings].filter((item) => {
    if (item.status === "sold") return false;
    const matchesText = `${item.title} ${item.category} ${item.place} ${item.seller} ${item.mode}`.toLowerCase().includes(search.toLowerCase());
    const matchesFilter = marketFilter === "Tout" || item.category === marketFilter || (marketFilter === "Troc" && item.mode === "troc");
    return matchesText && matchesFilter;
  }), [customListings, search, marketFilter]);
  const shopListings = useMemo(() => userId ? customListings.filter((item) => item.ownerId === userId) : customListings, [customListings, userId]);

  function go(next: Space) {
    setSpace(next);
    if (next === "business") setAccountMode("business");
    else if (next !== "inbox" || accountMode === "business") setAccountMode("personal");
    setSearch("");
  }

  function notify(text: string) {
    setToast(text);
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => {
      setToast("");
      toastTimerRef.current = null;
    }, 2400);
  }

  async function manageListing(id: Listing["id"], changes: Pick<Listing, "title" | "price" | "place" | "status">) {
    try {
      if (userId && typeof id === "string") await updateListing(id, changes);
      setCustomListings((current) => current.map((item) => item.id === id ? { ...item, ...changes } : item));
      notify(userId ? "Annonce mise à jour et synchronisée" : "Annonce mise à jour dans la démonstration");
      return true;
    } catch {
      setSyncStatus("offline");
      notify("La modification n’a pas pu être synchronisée. Réessayez.");
      return false;
    }
  }

  async function deleteShopListing(id: Listing["id"]) {
    try {
      if (userId && typeof id === "string") await removeListing(id);
      setCustomListings((current) => current.filter((item) => item.id !== id));
      notify(userId ? "Annonce supprimée de votre boutique" : "Annonce retirée de la démonstration");
      return true;
    } catch {
      setSyncStatus("offline");
      notify("La suppression n’a pas pu être synchronisée. Réessayez.");
      return false;
    }
  }

  function addToCart(item: Listing, quantity: number) {
    setCart((current) => {
      const existing=current.find((line)=>line.item.id===item.id);
      return existing?current.map((line)=>line.item.id===item.id?{...line,quantity:Math.min(9,line.quantity+quantity)}:line):[...current,{item,quantity}];
    });
    setSelectedProduct(null);
    setCartOpen(true);
    notify("Produit ajouté au panier");
  }

  async function contactListing(item: { seller: string; ownerId?: string; sellerPhone?: string }) {
    setSelectedProduct(null);
    setDemoContactName(item.seller);
    if (userId && item.ownerId && item.ownerId !== userId) {
      try {
        const seller = await findWhappyUserById(item.ownerId);
        if (seller) {
          setDirectPeer(seller);
          setDirectPhone("");
          setDirectCompose((value) => value + 1);
          go("inbox");
          notify(`Conversation avec ${seller.displayName} ouverte, sans ajout aux contacts`);
          return;
        }
      } catch {
        notify("Le vendeur est momentanément introuvable");
      }
    }
    setDirectPeer(null);
    setDirectPhone(item.sellerPhone || "");
    setDirectCompose((value) => value + 1);
    go("inbox");
    notify(`Vous pouvez contacter ${item.seller} depuis cette opportunité`);
  }

  async function checkout(draft: CheckoutDraft) {
    const payload = {
      customerName: draft.customerName.trim(),
      phone: draft.phone.trim(),
      address: draft.address.trim(),
      paymentMethod: draft.paymentMethod,
      total: draft.total,
      items: draft.lines.map((line) => ({ listingId: String(line.item.id), title: line.item.title, seller: line.item.seller, price: line.item.price, quantity: line.quantity })),
    };
    try {
      const created = userId ? await createOrder(userId, payload) : { ...payload, id: `local-${Date.now()}`, buyerId: "local", reference: `WH-${Date.now().toString(36).slice(-6).toUpperCase()}`, status: "pending" as const, createdAt: { toDate: () => new Date() } };
      setOrders((current) => current.some((order) => order.id === created.id) ? current : [created, ...current]);
      setCart([]);
      return created.reference;
    } catch {
      setSyncStatus("offline");
      notify("La commande n’a pas pu être enregistrée. Vos articles restent dans le panier.");
      return null;
    }
  }

  async function cancelTrackedOrder(order: CloudOrder) {
    try {
      if (userId && !order.id.startsWith("local-")) await cancelOrder(order.id);
      setOrders((current) => current.map((item) => item.id === order.id ? { ...item, status: "cancelled" } : item));
      notify("Commande annulée. Aucun paiement n’a été déclenché.");
      return true;
    } catch {
      setSyncStatus("offline");
      notify("L’annulation n’a pas pu être synchronisée. Réessayez.");
      return false;
    }
  }

  async function createTrackedGroup(name: string, description: string, members: string[], kind: "community" | "business" = "community", photo?: File | null) {
    try {
      const created = userId ? await createGroup(userId, name, description, members, kind, photo) : { id: `local-group-${Date.now()}`, name, description, kind, photoUrl: photo ? URL.createObjectURL(photo) : undefined, mark: name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase(), ownerId: "local", memberIds: ["local"], memberNames: members.map((member) => member.trim()).filter(Boolean), createdAt: { toDate: () => new Date() } };
      setGroups((current) => current.some((group) => group.id === created.id) ? current : [created, ...current]);
      notify(userId ? `Le groupe « ${name} » est synchronisé` : `Le groupe « ${name} » est prêt dans la démonstration`);
      return true;
    } catch {
      setSyncStatus("offline");
      notify("Le groupe n’a pas pu être créé. Vérifiez les informations puis réessayez.");
      return false;
    }
  }

  async function submitModal(event: FormEvent) {
    event.preventDefault();
    const form = new FormData(event.currentTarget as HTMLFormElement);
    if (modal === "live") {
      setBroadcast({
        title: String(form.get("title") || "Mon direct Wapi"),
        product: String(form.get("product") || "Aucun produit épinglé"),
        mode: String(form.get("liveMode") || "human") as BroadcastConfig["mode"],
      });
      setModal(null);
      return;
    }
    if (modal === "sell") {
      const title = String(form.get("title") || "Nouvelle annonce").trim();
      const mode = String(form.get("mode"));
      const seller = userDisplayName;
      const listing = { title, price: String(form.get("price") || "Prix à discuter"), place: String(form.get("place") || "Brazzaville"), seller, sellerPhone: auth.currentUser?.phoneNumber || "", mark: seller.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "VO", category: String(form.get("category") || "Services"), mode: (mode === "sell" ? "vente" : "troc") as Listing["mode"] };
      setPublishBusy(true);
      try {
        if (userId) {
          const media = form.get("media");
          const persisted = await publishListing(userId, listing, media instanceof File && media.size ? media : undefined);
          setCustomListings((current) => current.some((item) => item.id === persisted.id) ? current : [{ ...persisted, tone: "blue" }, ...current]);
        } else setCustomListings((current) => [{ ...listing, id: Date.now(), tone: "blue", trust: 100, ownerId: "local", status: "active" }, ...current]);
      } catch (error) {
        notify(error instanceof Error && error.message === "media-too-large" ? "Le média doit peser moins de 10 Mo" : "Synchronisation impossible. Vérifiez votre connexion puis réessayez.");
        setSyncStatus("offline");
        setPublishBusy(false);
        return;
      }
      setMarketFilter("Tout");
      go("market");
      notify(userId ? `« ${title} » est publié et synchronisé` : `« ${title} » est visible dans cette démonstration`);
    } else if (modal === "seek") {
      const title = String(form.get("title") || "Nouvelle recherche").trim();
      const request = { title, details: String(form.get("details") || "Réponse rapide souhaitée"), place: String(form.get("area") || "Brazzaville"), reward: String(form.get("reward") || "À discuter"), urgent: form.get("urgent") === "on", category: String(form.get("category") || "Situations") as RequestItem["category"] };
      setPublishBusy(true);
      try {
        if (userId) {
          const persisted = await publishRequest(userId, request);
          setCustomRequests((current) => current.some((item) => item.id === persisted.id) ? current : [persisted, ...current]);
        } else setCustomRequests((current) => [{ ...request, id: Date.now() }, ...current]);
      } catch {
        notify("Synchronisation impossible. Vérifiez votre connexion puis réessayez.");
        setSyncStatus("offline");
        setPublishBusy(false);
        return;
      }
      go("seek");
      notify(userId ? `Votre recherche « ${title} » est synchronisée` : `Votre recherche « ${title} » est active dans la démonstration`);
    } else if (modal === "message") {
      const offer = String(form.get("offer") || "").trim();
      const details = String(form.get("message") || "").trim();
      const mediaEntry = form.get("offerMedia");
      const media = mediaEntry instanceof File && mediaEntry.size ? mediaEntry : null;
      setDemoOffer({
        id: Date.now(),
        text: `◇ Offre : ${offer}${details ? ` — ${details}` : ""}`,
        mediaUrl: media ? URL.createObjectURL(media) : undefined,
        mediaKind: media?.type.startsWith("video/") ? "video" : media ? "image" : undefined,
        mediaName: media?.name,
      });
      playOfferSuccessSound();
      go("inbox");
      notify(media ? `Offre envoyée avec ${media.type.startsWith("video/") ? "une vidéo" : "une image"}` : "Votre offre a été ajoutée à la conversation");
    }
    setPublishBusy(false);
    setModal(null);
  }

  async function requestSms(event: FormEvent) {
    event.preventDefault();
    const digits = phone.replace(/\D/g, "");
    if (digits.length < 8) {
      setAuthError("Entrez un numéro de téléphone complet.");
      return;
    }
    setAuthBusy(true);
    setAuthError("");
    setAuthStatus("Préparation de l’envoi sécurisé…");
    try {
      const e164Phone = `${countryCode}${digits}`;
      try {
        localStorage.setItem("whappy-last-country-code", countryCode);
        localStorage.setItem("whappy-last-phone", phone);
      } catch { /* optional convenience only */ }
      auth.settings.appVerificationDisabledForTesting =
        process.env.NODE_ENV !== "production" && e164Phone === "+242060000099";
      recaptchaRef.current?.clear();
      const recaptchaContainer = document.getElementById("whappy-recaptcha");
      if (recaptchaContainer) recaptchaContainer.replaceChildren();
      auth.languageCode = "fr";
      const verifier = new RecaptchaVerifier(auth, "whappy-recaptcha", {
        // Keep the normal flow interruption-free. Firebase can still promote
        // this to a visible challenge when its risk engine needs more proof.
        size: captchaMode,
        theme: "light",
        tabindex: 0,
        callback: () => setAuthStatus("Vérification réussie. Envoi du SMS…"),
        "expired-callback": () => setAuthStatus("La vérification a expiré. Relancez l’envoi."),
      });
      recaptchaRef.current = verifier;
      await verifier.render();
      setAuthStatus("Envoi du code SMS en cours…");
      confirmationRef.current = await withTimeout(
        signInWithPhoneNumber(auth, e164Phone, verifier),
        90_000,
      );
      setAuthStatus("");
      setAuthStep("code");
    } catch (error) {
      const code = typeof error === "object" && error && "code" in error ? String(error.code) : "";
      setAuthError(
        code.includes("operation-not-allowed") ? "La connexion par téléphone doit être activée dans Firebase Authentication."
          : code.includes("billing") || code.includes("payment-required") ? "Les SMS réels nécessitent le forfait Firebase Blaze avec un compte de facturation."
          : code.includes("too-many-requests") || code.includes("quota-exceeded") ? "Trop de tentatives ou quota SMS atteint. Patientez quelques minutes."
            : code.includes("invalid-phone-number") ? "Ce numéro n'est pas reconnu. Vérifiez le pays et les chiffres saisis."
              : code.includes("unauthorized-domain") || code.includes("app-not-authorized") ? "Ce domaine n'est pas autorisé dans Firebase Authentication."
                  : code.includes("captcha-check-failed") || code.includes("invalid-app-credential") ? (setCaptchaMode("visible"), "La vérification automatique n’a pas abouti. Un contrôle renforcé sera affiché au prochain essai.")
                  : code.includes("network-request-failed") ? "Connexion internet interrompue. Vérifiez votre réseau puis réessayez."
                    : code.includes("verification-timeout") ? "La vérification a pris trop de temps. Relancez l’envoi du SMS."
                    : `Le SMS n'a pas pu être envoyé${code ? ` (${code.replace("auth/", "")})` : ""}. Réessayez.`,
      );
      setAuthStatus("");
      recaptchaRef.current?.clear();
      recaptchaRef.current = null;
    } finally {
      setAuthBusy(false);
    }
  }

  async function verifySms(event: FormEvent) {
    event.preventDefault();
    if (verificationCode.replace(/\D/g, "").length !== 6 || !confirmationRef.current) {
      setAuthError("Entrez le code à 6 chiffres reçu par SMS.");
      return;
    }
    setAuthBusy(true);
    setAuthError("");
    try {
      await confirmationRef.current.confirm(verificationCode);
      recaptchaRef.current?.clear();
      recaptchaRef.current = null;
      if (auth.currentUser?.displayName?.trim()) setAuthenticated(true);
      else setAuthStep("profile");
    } catch {
      setAuthError("Ce code est incorrect ou a expiré.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function finishProfile(event: FormEvent) {
    event.preventDefault();
    if (profileName.trim().length < 2) {
      setAuthError("Indiquez le nom qui sera visible par vos contacts.");
      return;
    }
    setAuthBusy(true);
    setAuthError("");
    try {
      if (!auth.currentUser) throw new Error("missing-user");
      await updateProfile(auth.currentUser, { displayName: profileName.trim() });
      await saveWhappyProfile({
        uid: auth.currentUser.uid,
        displayName: profileName.trim(),
        phoneNumber: auth.currentUser.phoneNumber || `${countryCode}${phone.replace(/\D/g, "")}`,
      }).catch(() => {
        // Authentication is already valid. Keep the user connected if a
        // transient Firestore/network issue delays profile synchronization.
        setAuthStatus("Profil enregistré sur cet appareil. Synchronisation en cours…");
      });
      setAuthenticated(true);
    } catch {
      setAuthError("Le profil n'a pas pu être créé. Vérifiez votre connexion puis réessayez.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function saveProfileChanges(name: string, photo: File | null) {
    if (!auth.currentUser) throw new Error("missing-user");
    await updateProfile(auth.currentUser, { displayName: name });
    const photoUrl = await saveWhappyProfile({
      uid: auth.currentUser.uid,
      displayName: name,
      phoneNumber: auth.currentUser.phoneNumber || `${countryCode}${phone.replace(/\D/g, "")}`,
      photoUrl: profilePhotoUrl,
    }, photo);
    setProfileName(name);
    if (photoUrl) {
      setProfilePhotoUrl(photoUrl);
      await updateProfile(auth.currentUser, { photoURL: photoUrl });
    }
  }

  const titles: Record<Space, [string, string]> = {
    orbit: ["Accueil", "Publicités, nouveautés et opportunités du moment"],
    live: ["Wapi Live", "Regardez, échangez et achetez en temps réel"],
    market: ["Wapi Marketplace", "Tout le monde peut vendre, acheter ou négocier"],
    barter: ["Troc intelligent", "Échangez de la valeur, sans limite"],
    seek: ["Je cherche", "Publiez un besoin, la communauté répond"],
    inbox: ["Connexions", "Vos conversations, commandes et offres"],
    calls: ["Appels", "Historique audio et vidéo de vos contacts Wapi"],
    contacts: ["Contacts", "Personnes, groupes et professionnels autour de vous"],
    rooms: ["Salles Wapi", "Des communautés de foi, de technologie, d’apprentissage et d’entraide"],
    radio: ["Radio & Podcasts", "Diffusez votre voix, créez vos podcasts et gardez le lien avec vos auditeurs"],
    services: ["Services", "Payez, achetez, trouvez et gérez votre quotidien"],
    twin: ["Studio Double", "Votre vendeur numérique, créé avec votre accord"],
    business: ["Business Suite", "Pages professionnelles, publicité et croissance"],
    games: ["Jeux & Tournois", "Jouez sérieusement, progressez avec un coach et gagnez"],
  };
  const searchPlaceholders: Record<Space,string> = {
    inbox: "Rechercher une conversation…",
    calls: "Rechercher un appel ou un contact…",
    contacts: "Rechercher une personne, un groupe ou un professionnel…",
    rooms: "Rechercher une salle, une communauté ou un thème…",
    radio: "Rechercher un podcast, une émission ou un auditeur…",
    orbit: "Rechercher dans les Moments…",
    services: "Rechercher un service Wapi…",
    live: "Rechercher un direct…",
    market: "Rechercher un produit ou une boutique…",
    barter: "Rechercher un échange…",
    seek: "Rechercher une solution ou un besoin…",
    twin: "Rechercher dans le Studio Double…",
    business: "Rechercher une page ou une campagne…",
    games: "Rechercher un joueur, un tournoi ou une leçon…",
  };

  if (!authReady) return <ReconnectScreen />;
  if (!authenticated) return <PhoneAccess step={authStep} countryCode={countryCode} setCountryCode={setCountryCode} phone={phone} setPhone={setPhone} code={verificationCode} setCode={setVerificationCode} profileName={profileName} setProfileName={setProfileName} busy={authBusy} status={authStatus} error={authError} requestSms={requestSms} verifySms={verifySms} finishProfile={finishProfile} back={()=>{setAuthError("");setAuthStatus("");setAuthStep("phone")}} />;

  return <main className="nova-shell whappy-blue">
    <aside className="nova-rail">
      <button className="nova-logo" onClick={() => go("inbox")} aria-label="Accueil Wapi"><Image src="/whappy-app-icon.png" alt="Logo Wapi" width={42} height={42} priority /><span>WAPI</span></button>
      <nav aria-label="Espaces Wapi App">
        <Rail active={space === "inbox"} icon="◫" label="Messages" count={3} onClick={() => go("inbox")} />
        <Rail active={space === "calls"} icon="☎" label="Appels" onClick={() => go("calls")} />
        <Rail active={space === "contacts"} icon="◎" label="Contacts" onClick={() => go("contacts")} />
        <Rail active={space === "orbit"} icon="▦" label="Moments" onClick={() => go("orbit")} />
        <Rail active={space === "games"} icon="♞" label="Jeux" onClick={() => go("games")} />
        <Rail active={space === "rooms"} icon="◈" label="Salles" onClick={() => go("rooms")} />
        <Rail active={space === "radio"} icon="◉" label="Radio" live={radioSession.status === "live"} onClick={() => go("radio")} />
        <Rail active={space === "services"} icon="⌗" label="Services" onClick={() => go("services")} />
        <Rail active={space === "business"} icon="▥" label="Business" onClick={() => go("business")} />
      </nav>
      <div className="rail-tools">
        <button className="pulse-rail" onClick={() => setPulseOpen(true)}><span>✦</span><small>Pulse</small><b>{messages.reduce((sum,item)=>sum+item.unread,0)}</b></button>
        <button className={space === "twin" ? "active" : ""} onClick={() => go("twin")}><span>◎</span><small>Mon Double</small></button>
        <button className={`me ${profilePhotoUrl ? "has-photo" : ""}`} onClick={() => setProfileOpen(true)} aria-label="Ouvrir mon profil"><span style={profilePhotoUrl ? { backgroundImage: `url(${profilePhotoUrl})` } : undefined}>{profilePhotoUrl ? "" : accountName.split(/\s+/).map((part)=>part[0]).join("").slice(0,2).toUpperCase()}</span><i /></button>
      </div>
    </aside>

    <section className="nova-stage">
      <header className="nova-topbar">
        <div className="topbar-identity">
          <button className="mobile-logo" onClick={() => go("inbox")} aria-label="Messages Wapi">
            <Image src="/whappy-app-icon.png" alt="Logo officiel Wapi" width={40} height={40} priority />
          </button>
          <div key={space} className="topbar-copy"><span className="kicker">WAPI APP / {space.toUpperCase()}</span><h1>{titles[space][0]} {founderProfile && <i className="founder-grey-badge" title="Compte Wapi App by BCA certifié">✓</i>}</h1><p>{founderProfile ? `${businessName} · Fondateur · Compte officiel certifié` : titles[space][1]}</p></div>
        </div>
        <label className="nova-search"><span>⌕</span><input id="whappy-global-search" aria-label="Rechercher dans l’espace actuel" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={searchPlaceholders[space]} />{search && <button onClick={() => setSearch("")} aria-label="Effacer la recherche">×</button>}</label>
      <div className="top-actions">{demoMode&&<button className="demo-exit" onClick={()=>{setAuthenticated(false);setSpace("inbox");}}><span>×</span><small>Quitter la démo</small></button>}<span className={`account-mode ${accountMode}`}><i/>{accountMode === "business" ? "Compte Business" : "Compte personnel"}</span><NotificationCenter unread={messages.reduce((sum,item)=>sum+item.unread,0)} onNotify={notify}/><span className={`sync-badge ${syncStatus}`} title={syncStatus==="synced"?"Données synchronisées":syncStatus==="syncing"?"Synchronisation en cours":syncStatus==="offline"?"Synchronisation indisponible":"Connexion sécurisée"}><i/>{syncStatus==="synced"?"Cloud":syncStatus==="syncing"?"Sync…":syncStatus==="offline"?"Hors ligne":"Prêt"}</span>{space === "inbox" ? <><button onClick={() => setOrdersOpen(true)}><span>▤</span><small>Commandes</small>{orders.length>0&&<b className="action-count">{orders.length}</b>}</button><button className="sell" onClick={() => { setDirectPeer(null); setDirectPhone(""); setDirectCompose((value)=>value+1); }}><span>＋</span><small>Nouveau</small></button></> : space === "calls" ? <><button onClick={() => go("contacts")}><span>◎</span><small>Contacts</small></button><button className="sell" onClick={() => { setDirectPeer(null); setDirectPhone(""); go("inbox"); setDirectCompose((value)=>value+1); }}><span>＋</span><small>Nouveau</small></button></> : space === "radio" ? <><button onClick={() => { setDirectPeer(null); setDirectPhone(""); go("inbox"); setDirectCompose((value)=>value+1); }}><span>◫</span><small>Messages</small></button><button onClick={() => setOrdersOpen(true)}><span>▤</span><small>Commandes</small>{orders.length>0&&<b className="action-count">{orders.length}</b>}</button><button className="cart-action" onClick={()=>setCartOpen(true)}><span>◇</span><small>Panier</small>{cart.length>0&&<b>{cart.reduce((sum,line)=>sum+line.quantity,0)}</b>}</button><button className="sell" onClick={() => setModal("sell")}><span>＋</span><small>Vendre</small></button></> : <><button onClick={() => setOrdersOpen(true)}><span>▤</span><small>Commandes</small>{orders.length>0&&<b className="action-count">{orders.length}</b>}</button><button className="cart-action" onClick={()=>setCartOpen(true)}><span>◇</span><small>Panier</small>{cart.length>0&&<b>{cart.reduce((sum,line)=>sum+line.quantity,0)}</b>}</button><button className="sell" onClick={() => setModal("sell")}><span>＋</span><small>Vendre</small></button></>}</div>
      </header>

      <AccountSwitcher mode={accountMode} onToggle={() => go(accountMode === "business" ? "inbox" : "business")} />

      <WhappyNow
        unread={messages.reduce((sum,item)=>sum+item.unread,0)}
        listingCount={customListings.length+listings.length}
        orderCount={orders.length}
        cloud={Boolean(userId)}
        onNavigate={go}
      />

      {(space === "orbit" || space === "inbox" || space === "business" || space === "twin") && <SocialControlDeck current={space} onNavigate={go} onCreate={() => {
        if (space === "business") go("business");
        else if (space === "twin") go("twin");
        else setModal(space === "inbox" ? "message" : space === "orbit" ? "live" : "live");
      }} />}

      {space === "orbit" && <Orbit go={go} setModal={setModal} setLiveIndex={setLiveIndex} notify={notify} saved={saved} setSaved={setSaved} ad={activeAds[0]} onAdClick={(campaign)=>{void recordAdEvent(campaign,userId,"click").catch(()=>{});notify(`Page ${campaign.pageName} ouverte`);}} userName={accountName} founder={founderProfile} footer={<><StoryStudio userId={userId||"local-preview"} userName={auth.currentUser?.displayName||profileName||"Vous"} cloud={Boolean(userId)} notify={notify}/><WhapTextStudio userId={userId||"local-preview"} userName={auth.currentUser?.displayName||profileName||"Vous"} cloud={Boolean(userId)} notify={notify}/></>} />}
      {space === "live" && <LiveSpace setModal={setModal} setLiveIndex={setLiveIndex} />}
      {space === "market" && <MarketSpace search={search} filter={marketFilter} setFilter={setMarketFilter} items={filtered} shopCount={shopListings.length} saved={saved} setSaved={setSaved} notify={notify} setModal={setModal} onOpenShop={() => setShopOpen(true)} onOpen={setSelectedProduct} />}
      {space === "barter" && <BarterSpace notify={notify} setModal={setModal} />}
      {space === "seek" && <SeekSpace setModal={setModal} notify={notify} items={[...customRequests, ...requests]} />}
      {space === "inbox" && (userId ? <RealTimeInbox key={directCompose} embedded composeToken={directCompose} composePhone={directPhone} composePeer={directPeer} search={search} user={directUser} founder={founderProfile} notify={notify} onCall={(peer,video)=>setCall({contact:peer.displayName,video,peer})}/> : <InboxSpace search={search} userId={userId} offer={demoOffer} initialContact={demoContactName} setModal={setModal} notify={notify} onCall={(contact,video)=>setCall({contact,video})} />)}
      {space === "calls" && (userId ? <RealTimeInbox key={`calls-${directCompose}`} embedded initialView="calls" search={search} user={directUser} founder={founderProfile} notify={notify} onCall={(peer,video)=>setCall({contact:peer.displayName,video,peer})}/> : <CallsPreviewSpace search={search} onCall={(contact,video)=>setCall({contact,video})} onMessages={()=>go("inbox")}/>)}
      {space === "contacts" && <ContactsSpace search={search} cloud={Boolean(userId)} userId={userId} userName={auth.currentUser?.displayName||profileName||"Vous"} cloudGroups={groups} onCreateGroup={createTrackedGroup} notify={notify} onCall={(contact)=>setCall({contact,video:false})} onMessage={(contact)=>{setDirectPeer(null);setDirectPhone(contact.phone||"");setDirectCompose((value)=>value+1);go("inbox");notify(contact.phone?`Ouverture de la conversation avec ${contact.name}`:`Entrez le numéro Wapi de ${contact.name}`)}} />}
      {space === "rooms" && <RoomsSpace userId={userId||"demo-user"} userName={accountName} search={search} cloud={Boolean(userId)} notify={notify} />}
      <RadioStudio
        active={space === "radio"}
        hostName={accountName}
        notify={notify}
        onSessionChange={setRadioSession}
        onOpenInbox={() => {
          setDirectPeer(null);
          setDirectPhone("");
          setDirectCompose((value) => value + 1);
          go("inbox");
        }}
      />
      {space === "services" && <SuperHub search={search} go={go} orderCount={orders.length} onOrders={()=>setOrdersOpen(true)} notify={notify} />}
      {space === "twin" && <TwinEngineStudio userId={userId||"local-preview"} userName={auth.currentUser?.displayName||profileName||"Vous"} consent={consent} setConsent={setConsent} notify={notify} cloud={Boolean(userId)}/>}
      {space === "business" && <>{(founderProfile || demoMode) && <HappyFounderDashboard ownerId={userId||"happy-demo"} userName={businessName} search={search} notify={notify} demo={demoMode}/>}<div id="business-studio"><BusinessStudio userId={userId||"demo-user"} userName={businessName} search={search} notify={notify} demo={demoMode}/></div><div id="business-growth"><BusinessGrowthTools userId={userId||"demo-user"} userName={businessName} search={search} notify={notify} demo={demoMode}/></div></>}
      {space === "games" && <GamesSpace notify={notify} />}
    </section>

    {liveIndex !== null && <LiveViewerPro live={lives[liveIndex]} onClose={() => setLiveIndex(null)} notify={notify} onAdd={(live,quantity)=>addToCart({id:`live-${live.product}`,title:live.product,price:`${live.price} FCFA`,place:"Direct Wapi",seller:live.host,mark:live.host.split(" ").map(part=>part[0]).join("").slice(0,2),tone:live.tone,category:"Direct",mode:"vente",trust:98},quantity)} />}
    {broadcast && <BroadcastStudio config={broadcast} twinAuthorized={consent} onClose={() => setBroadcast(null)} onOpenTwin={() => { setBroadcast(null); go("twin"); setTwinStep(1); }} notify={notify} />}
    {modal === "message" ? <PremiumOfferModal busy={publishBusy} onClose={() => setModal(null)} onSubmit={submitModal} notify={notify} /> : modal && <ActionModal type={modal} busy={publishBusy} onClose={() => setModal(null)} onSubmit={submitModal} consent={consent} setConsent={setConsent} setTwinStep={setTwinStep} go={go} notify={notify} />}
    {profileOpen && <ProfilePanel name={accountName} phone={auth.currentUser?.phoneNumber || `${countryCode} ${phone || "06 000 00 00"}`} photoUrl={profilePhotoUrl} founder={founderProfile} onSaveProfile={saveProfileChanges} notify={notify} onClose={() => setProfileOpen(false)} go={(destination) => { setProfileOpen(false); go(destination); }} onOpenShop={() => { setProfileOpen(false); setShopOpen(true); }} onOpenOrders={() => { setProfileOpen(false); setOrdersOpen(true); }} onSignOut={async () => { if (auth.currentUser) await signOut(auth); setProfileOpen(false); setAuthenticated(false); }} />}
    {shopOpen && <SellerDashboard items={shopListings} cloud={Boolean(userId)} onClose={() => setShopOpen(false)} onCreate={() => { setShopOpen(false); setModal("sell"); }} onUpdate={manageListing} onDelete={deleteShopListing} notify={notify} />}
    {selectedProduct&&<ProductPanel item={selectedProduct} saved={!!saved[String(selectedProduct.id)]} onSave={()=>setSaved(current=>({...current,[selectedProduct.id]:!current[String(selectedProduct.id)]}))} onClose={()=>setSelectedProduct(null)} onContact={(item)=>void contactListing(item)} onAdd={(quantity)=>addToCart(selectedProduct,quantity)}/>}
    {cartOpen&&<CartPanel lines={cart} onClose={()=>setCartOpen(false)} onQuantity={(id,quantity)=>setCart(current=>current.map(line=>line.item.id===id?{...line,quantity}:line))} onRemove={(id)=>setCart(current=>current.filter(line=>line.item.id!==id))} onCheckout={checkout} notify={notify}/>}
    {ordersOpen&&<OrdersPanel orders={orders} cloud={Boolean(userId)} onClose={()=>setOrdersOpen(false)} onExplore={()=>{setOrdersOpen(false);go("market")}} onContact={(seller)=>{setOrdersOpen(false);go("inbox");notify(`Conversation avec ${seller} ouverte`)}} onCancel={cancelTrackedOrder} notify={notify}/>}
    {call&&<CallRoom contact={call.contact} video={call.video} currentUser={directUser} peer={call.peer} incoming={call.incoming} onClose={()=>setCall(null)}/>}
    {space !== "radio" && <RadioDock session={radioSession} onOpen={() => go("radio")} onMessages={() => go("inbox")}/>}
    <WhappyPulse open={pulseOpen} onClose={()=>setPulseOpen(false)} onNavigate={go} unread={messages.reduce((sum,item)=>sum+item.unread,0)} orderCount={orders.length} cartCount={cart.reduce((sum,line)=>sum+line.quantity,0)} groupCount={groups.length} syncStatus={syncStatus} userName={auth.currentUser?.displayName||profileName||"Vous"} founder={founderProfile} demo={demoMode}/>
    <WhappyExperience current={space} onNavigate={go} onPulse={()=>setPulseOpen(true)}/>
    <WhappyMotion />
    {toast && <div className="nova-toast" role="status" aria-live="polite">✦ {toast}</div>}
  </main>;
}

function ReconnectScreen() {
  return <main className="whappy-reconnect" aria-live="polite">
    <div className="whappy-reconnect-card">
      <Image className="whappy-reconnect-logo" src="/whappy-app-icon.png" alt="Logo Wapi" width={64} height={64} priority />
      <strong className="whappy-reconnect-brand">WAPI</strong>
      <strong>Reconnexion sécurisée</strong>
      <p>Wapi restaure votre session et prépare vos données locales avant de contacter le cloud.</p>
      <i aria-hidden="true" />
      <small>Vos messages restent protégés pendant la reprise.</small>
    </div>
  </main>;
}

function PhoneAccess({ step,countryCode,setCountryCode,phone,setPhone,code,setCode,profileName,setProfileName,busy,status,error,requestSms,verifySms,finishProfile,back }: { step:"phone"|"code"|"profile";countryCode:string;setCountryCode:(value:string)=>void;phone:string;setPhone:(value:string)=>void;code:string;setCode:(value:string)=>void;profileName:string;setProfileName:(value:string)=>void;busy:boolean;status:string;error:string;requestSms:(event:FormEvent)=>void;verifySms:(event:FormEvent)=>void;finishProfile:(event:FormEvent)=>void;back:()=>void }) {
  const fullNumber=`${countryCode} ${phone || "—"}`;
  return <main className="phone-access"><section className="access-brand"><div className="access-logo"><Image src="/whappy-app-icon.png" alt="Logo Wapi App" width={70} height={70} priority/><strong>WAPI APP</strong></div><div className="access-promise"><span>UN NUMÉRO. UN COMPTE.</span><h1>Votre monde,<br/>au bout du <em>fil.</em></h1><p>Vos messages, vos appels, vos directs et votre boutique vous suivent sur tous vos appareils.</p><div className="access-highlights"><span>◫ Messages privés</span><span>☎ Appels HD</span><span>◇ Marketplace</span></div></div><div className="access-flow"><span className={step==="phone"?"active":"done"}><b>{step==="phone"?"1":"✓"}</b> Numéro</span><i/><span className={step==="code"?"active":step==="profile"?"done":""}><b>{step==="profile"?"✓":"2"}</b> Code SMS</span><i/><span className={step==="profile"?"active":""}><b>3</b> Profil</span></div><small className="access-secure">◆ Chiffrement · Identité téléphonique · Aucun mot de passe</small></section><section className="access-panel"><div className="access-card">{step!=="phone"&&<button className="access-back" onClick={back} aria-label="Modifier le numéro">←</button>}<span className="access-step">ÉTAPE {step==="phone"?"1 SUR 3":step==="code"?"2 SUR 3":"3 SUR 3"}</span>{step==="phone"&&<form onSubmit={requestSms}><h2>Entrez votre numéro</h2><p>Wapi App utilise votre numéro pour créer et retrouver votre compte. Un même numéro ne peut appartenir qu&apos;à un seul compte.</p><label>Pays<select value={countryCode} onChange={event=>setCountryCode(event.target.value)} aria-label="Pays et indicatif téléphonique">{callingCountries.map((country)=><option key={country.iso} value={country.dialCode}>{`${country.name} (${country.dialCode})`}</option>)}</select><small className="country-count">{callingCountries.length} pays disponibles</small></label><label>Numéro de téléphone<div className="phone-field"><span>{countryCode}</span><input inputMode="tel" autoComplete="tel-national" value={phone} onChange={event=>setPhone(event.target.value)} placeholder="06 123 45 67"/></div></label><div id="whappy-recaptcha" className="recaptcha-box"/>{status&&<p className="sms-status">{status}</p>}<button className="access-primary" disabled={busy}>{busy?"Envoi du SMS…":phone.trim()?"Continuer avec ce numéro →":"Continuer par SMS →"}</button><div className="one-account"><span>1</span><div><strong>Un numéro = un compte Wapi App</strong><small>Cette règle protège votre identité, vos contacts et vos transactions.</small></div></div></form>}{step==="code"&&<form onSubmit={verifySms}><span className="access-code-icon">✦</span><h2>Vérifiez votre numéro</h2><p>Nous avons envoyé un code à 6 chiffres au <strong>{fullNumber}</strong>.</p><label>Code reçu par SMS<input className="otp-field" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={event=>setCode(event.target.value.replace(/\D/g,""))} placeholder="— — — — — —"/></label><button className="access-primary" disabled={busy}>{busy?"Vérification…":"Vérifier le code →"}</button><button className="access-link" type="button" onClick={()=>setCode("")}>Saisir un nouveau code</button></form>}{step==="profile"&&<form onSubmit={finishProfile}><span className="profile-create">＋</span><h2>Créez votre profil</h2><p>Ajoutez le nom que vos contacts verront. Votre photo Firestore apparaîtra automatiquement sur tous vos appareils.</p><label>Votre nom<input autoComplete="name" value={profileName} onChange={event=>setProfileName(event.target.value)} placeholder="Ex. Happy"/></label><button className="access-primary" disabled={busy}>{busy?"Création…":"Entrer dans Wapi App →"}</button></form>}{error&&<p className="access-error">! {error}</p>}<div className="access-divider"><span>ou</span></div><AndroidDownload/><small className="access-legal">La connexion sécurisée protège vos messages, vos contacts et vos transactions. En continuant, vous acceptez les conditions Wapi App et confirmez être propriétaire de ce numéro.</small></div></section></main>;
}

function AndroidDownload() {
  const [started, setStarted] = useState(false);
  const recordDownload = (source: "button" | "direct") => {
    setStarted(true);
    if (typeof window === "undefined") return;
    const event = { id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, at: new Date().toISOString(), version: ANDROID_APP.version, size: ANDROID_APP.size, source, userAgent: navigator.userAgent };
    try {
      const current = JSON.parse(localStorage.getItem(WHAPPY_DOWNLOAD_EVENTS_KEY) || "[]") as unknown[];
      localStorage.setItem(WHAPPY_DOWNLOAD_EVENTS_KEY, JSON.stringify([event, ...current].slice(0, 250)));
      window.dispatchEvent(new CustomEvent("whappy-download-recorded"));
    } catch {}
  };
  return <section className="android-download" aria-labelledby="android-download-title"><Image src="/whappy-app-icon.png" alt="" width={54} height={54}/><div className="android-download-copy"><small>APPLICATION ANDROID</small><strong id="android-download-title">Wapi App {ANDROID_APP.version}</strong><span>{ANDROID_APP.size} · {ANDROID_APP.minimum}</span></div><a className="access-apk" href={ANDROID_APP.url} download onClick={()=>recordDownload("button")}>↓ Télécharger l&apos;application</a>{started&&<p className="download-status" role="status">✓ Téléchargement lancé. Ouvrez ensuite le fichier APK.</p>}<details><summary>Le téléchargement ne démarre pas ?</summary><p>Appuyez sur le lien direct, puis autorisez le téléchargement dans votre navigateur Android.</p><a href={ANDROID_APP.url} target="_blank" rel="noreferrer" onClick={()=>recordDownload("direct")}>Ouvrir le lien direct de l&apos;APK ↗</a></details></section>;
}

function Rail({ active, icon, label, count, live, onClick }: { active: boolean; icon: string; label: string; count?: number; live?: boolean; onClick: () => void }) {
  return <button className={active ? "active" : ""} onClick={onClick} title={label} aria-current={active ? "page" : undefined}><span>{icon}</span><small>{label}</small>{count ? <b>{count}</b> : null}{live ? <i /> : null}</button>;
}

function RadioDock({ session, onOpen, onMessages }: { session: RadioSession; onOpen:()=>void; onMessages:()=>void }) {
  if (session.status === "offline") return null;
  return <aside className={`radio-dock ${session.status}`} aria-label="Radio en cours"><button className="radio-dock-main" onClick={onOpen}><span className="radio-dock-mark">◉</span><div><small>{session.status === "live" ? "● RADIO EN DIRECT" : "Ⅱ RADIO EN PAUSE"}</small><strong>{session.title}</strong><em>{session.listeners} auditeur{session.listeners > 1 ? "s" : ""} · {Math.floor(session.elapsed/60).toString().padStart(2,"0")}:{(session.elapsed%60).toString().padStart(2,"0")}</em></div><b>↗</b></button><button className="radio-dock-message" onClick={onMessages} aria-label="Ouvrir mes messages">◫<span>Répondre</span></button></aside>;
}

type GameKind = "chess" | "checkers" | "ludo" | "sudoku";
type GamesView = "play" | "tournaments" | "coach" | "community" | "world";
type DifficultyId = "beginner" | "club" | "expert" | "master" | "grandmaster";
type GameTeam = { id: number; name: string; game: GameKind; members: number; tier: string; mark: string; color: string };
type GameTier = { name: string; game: GameKind | "all"; minimum: number; color: string };

const gameProfiles: Record<GameKind, { name: string; mark: string; level: string; rating: string; detail: string; color: string }> = {
  chess: { name: "Échecs", mark: "♞", level: "Grand niveau", rating: "1 842", detail: "Parties classées, puzzles et analyse moteur", color: "blue" },
  checkers: { name: "Dames", mark: "●", level: "Compétition", rating: "1 564", detail: "Dames internationales · parties rapides", color: "violet" },
  ludo: { name: "Ludo", mark: "✦", level: "Stratégie", rating: "1 206", detail: "Course tactique · 2 à 4 joueurs", color: "red" },
  sudoku: { name: "Sudoku", mark: "▦", level: "Logique", rating: "1 688", detail: "Défis quotidiens · vitesse et précision", color: "gold" },
};

const tournamentRows = [
  { name: "Kongo Masters", game: "Échecs · 10+5", date: "Sam. 24 août · 18:00", players: "32 / 64", prize: "250 000 FCFA", tag: "PREMIUM", tone: "gold" },
  { name: "Brazzaville Blitz", game: "Échecs · 3+2", date: "Dim. 25 août · 16:00", players: "48 / 128", prize: "100 000 FCFA", tag: "OUVERT", tone: "blue" },
  { name: "Dames du Congo", game: "Dames · 10 min", date: "Mer. 28 août · 19:30", players: "16 / 32", prize: "75 000 FCFA", tag: "NOUVEAU", tone: "violet" },
  { name: "Ludo Four Nations", game: "Ludo · 4 joueurs", date: "Ven. 30 août · 20:00", players: "64 / 128", prize: "50 000 FCFA", tag: "OUVERT", tone: "red" },
];

const defaultGameTeams: GameTeam[] = [
  { id: 1, name: "Kongo Knights", game: "chess", members: 18, tier: "Or national", mark: "KK", color: "blue" },
  { id: 2, name: "Les Pions de Bacongo", game: "checkers", members: 12, tier: "Argent régional", mark: "PB", color: "violet" },
  { id: 3, name: "Moungali Rollers", game: "ludo", members: 9, tier: "Bronze local", mark: "MR", color: "red" },
];

const defaultGameTiers: GameTier[] = [
  { name: "Bronze local", game: "all", minimum: 0, color: "bronze" },
  { name: "Argent régional", game: "all", minimum: 1200, color: "silver" },
  { name: "Or national", game: "all", minimum: 1500, color: "gold" },
  { name: "Diamant élite", game: "all", minimum: 1800, color: "diamond" },
  { name: "Champion WAPI", game: "all", minimum: 2100, color: "champion" },
];

const difficultyProfiles: Record<DifficultyId, { name: string; rating: number; detail: string; gain: number; delay: number; color: string }> = {
  beginner: { name: "Débutant", rating: 800, detail: "L’ordinateur vous laisse le temps d’apprendre", gain: 8, delay: 520, color: "green" },
  club: { name: "Club", rating: 1200, detail: "Des coups solides pour progresser régulièrement", gain: 12, delay: 700, color: "blue" },
  expert: { name: "Expert", rating: 1600, detail: "Tactiques, pièges et réponses rapides", gain: 18, delay: 860, color: "violet" },
  master: { name: "Maître", rating: 2000, detail: "Une opposition exigeante, proche du tournoi", gain: 24, delay: 1020, color: "gold" },
  grandmaster: { name: "Grand maître", rating: 2400, detail: "Le défi ultime pour viser le sommet", gain: 32, delay: 1200, color: "red" },
};

const worldCupEvents = [
  { game: "chess" as GameKind, title: "Coupe du Monde WAPI · Échecs", date: "Qualifications · 07 septembre", stage: "QUALIFICATIONS OUVERTES", prize: "1 000 000 FCFA", players: "2 048 places", tone: "blue" },
  { game: "checkers" as GameKind, title: "Coupe du Monde WAPI · Dames", date: "Qualifications · 14 septembre", stage: "INSCRIPTIONS BIENTÔT", prize: "500 000 FCFA", players: "1 024 places", tone: "violet" },
  { game: "sudoku" as GameKind, title: "Coupe du Monde WAPI · Sudoku", date: "Défi mondial · 21 septembre", stage: "DÉFI CHRONOMÉTRÉ", prize: "350 000 FCFA", players: "5 000 places", tone: "gold" },
  { game: "ludo" as GameKind, title: "Coupe du Monde WAPI · Ludo", date: "Équipes · 28 septembre", stage: "FORMAT PAR ÉQUIPES", prize: "300 000 FCFA", players: "512 équipes", tone: "red" },
];

function GamesSpace({ notify }: { notify: (text: string) => void }) {
  const [game, setGame] = useState<GameKind>("chess");
  const [view, setView] = useState<GamesView>("play");
  const [selectedSquare, setSelectedSquare] = useState<number | null>(null);
  const [moveCount, setMoveCount] = useState(18);
  const [registeredTournament, setRegisteredTournament] = useState<string | null>(null);
  const [lessonStarted, setLessonStarted] = useState(false);
  const [teams, setTeams] = useState<GameTeam[]>(defaultGameTeams);
  const [tiers, setTiers] = useState<GameTier[]>(defaultGameTiers);
  const [showTeamForm, setShowTeamForm] = useState(false);
  const [showTierForm, setShowTierForm] = useState(false);
  const [teamName, setTeamName] = useState("");
  const [teamGame, setTeamGame] = useState<GameKind>("chess");
  const [teamTier, setTeamTier] = useState("Bronze local");
  const [tierName, setTierName] = useState("");
  const [tierGame, setTierGame] = useState<GameKind | "all">("all");
  const [tierMinimum, setTierMinimum] = useState("1200");
  const [registeredWorld, setRegisteredWorld] = useState<Record<GameKind, boolean>>({ chess: false, checkers: false, ludo: false, sudoku: false });
  const [matchSeconds, setMatchSeconds] = useState(9 * 60 + 42);
  const [matchStarted, setMatchStarted] = useState(true);
  const [moveHistory, setMoveHistory] = useState(["e4", "e5", "♘f3", "♞c6", "♗b5"]);
  const [opponentMode, setOpponentMode] = useState<"computer" | "human">("computer");
  const [difficulty, setDifficulty] = useState<DifficultyId>("expert");
  const [computerThinking, setComputerThinking] = useState(false);
  const [matchResult, setMatchResult] = useState<"win" | "loss" | null>(null);
  const [ratings, setRatings] = useState<Record<GameKind, number>>({ chess: 1842, checkers: 1564, ludo: 1206, sudoku: 1688 });
  const [wins, setWins] = useState(14);
  const [losses, setLosses] = useState(4);
  const [streak, setStreak] = useState(3);
  const profile = gameProfiles[game];
  const currentRating = ratings[game];
  const difficultyProfile = difficultyProfiles[difficulty];

  useEffect(() => {
    if (!matchStarted) return;
    const timer = window.setInterval(() => setMatchSeconds((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [matchStarted]);

  const chessPieces = [
    "♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜",
    "♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟",
    "", "", "", "", "", "", "", "",
    "", "", "", "", "", "", "", "",
    "", "", "", "", "", "", "", "",
    "", "", "", "", "", "", "", "",
    "♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙",
    "♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖",
  ];
  const sudokuBoard = [
    "5", "3", "", "", "7", "", "", "", "", "6", "", "", "1", "9", "5", "", "", "", "", "9", "8", "", "", "", "", "6", "", "8", "", "", "6", "", "", "", "3", "4", "", "", "8", "", "3", "", "", "1", "7", "", "", "", "2", "", "", "", "6", "", "6", "", "", "", "", "", "", "2", "8", "", "", "4", "1", "9", "", "", "5", "", "", "", "8", "", "", "", "7", "9",
  ];

  function squareLabel(index: number) {
    if (game === "sudoku") return `ligne ${Math.floor(index / 9) + 1}, colonne ${(index % 9) + 1}`;
    return `${String.fromCharCode(97 + (index % 8))}${8 - Math.floor(index / 8)}`;
  }

  function pieceAt(index: number) {
    if (game === "chess") return chessPieces[index];
    if (game === "sudoku") return sudokuBoard[index] || "";
    if (game === "ludo") {
      const ludoTokens: Record<number, string> = { 0: "●", 1: "●", 7: "●", 8: "●", 55: "●", 56: "●", 62: "●", 63: "●" };
      return ludoTokens[index] || "";
    }
    const row = Math.floor(index / 8);
    const column = index % 8;
    if ((row <= 2 || row >= 5) && (row + column) % 2 === 1) return row <= 2 ? "●" : "○";
    return "";
  }

  function chooseSquare(index: number) {
    if (matchResult || computerThinking) return;
    if (!pieceAt(index) && selectedSquare === null) return;
    if (selectedSquare !== null && selectedSquare !== index) {
      setMoveCount((count) => count + 1);
      setMoveHistory((current) => [...current, squareLabel(index)].slice(-8));
      setMatchStarted(true);
      notify(`Coup joué vers ${squareLabel(index)} · l’instructeur analyse la position`);
      setSelectedSquare(null);
      if (opponentMode === "computer") {
        setComputerThinking(true);
        window.setTimeout(() => {
          setMoveCount((count) => count + 1);
          setMoveHistory((current) => [...current, difficulty === "grandmaster" ? "… tactique" : "… réponse"].slice(-8));
          setComputerThinking(false);
          notify(`${difficultyProfile.name} répond · nouvelle position à calculer`);
        }, difficultyProfile.delay);
      }
      return;
    }
    setSelectedSquare(selectedSquare === index ? null : index);
  }

  function resetMatch() {
    setSelectedSquare(null);
    setMoveCount(1);
    setMoveHistory([]);
    setMatchSeconds(10 * 60);
    setMatchStarted(true);
    setComputerThinking(false);
    setMatchResult(null);
    notify(`Nouvelle partie ${profile.name} prête`);
  }

  function formatClock(totalSeconds: number) {
    return `${String(Math.floor(totalSeconds / 60)).padStart(2, "0")}:${String(totalSeconds % 60).padStart(2, "0")}`;
  }

  function formatRating(rating: number) {
    return new Intl.NumberFormat("fr-FR").format(rating);
  }

  function tierForRating(rating: number) {
    return [...tiers].filter((tier) => tier.game === "all" || tier.game === game).sort((a, b) => b.minimum - a.minimum).find((tier) => rating >= tier.minimum) || tiers[0];
  }

  function nextTierForRating(rating: number) {
    return [...tiers].filter((tier) => (tier.game === "all" || tier.game === game) && tier.minimum > rating).sort((a, b) => a.minimum - b.minimum)[0] || null;
  }

  const currentTier = tierForRating(currentRating);
  const nextTier = nextTierForRating(currentRating);
  const rankProgress = nextTier ? Math.min(100, Math.max(4, ((currentRating - currentTier.minimum) / (nextTier.minimum - currentTier.minimum)) * 100)) : 100;

  function finishMatch(result: "win" | "loss") {
    if (matchResult) return;
    const delta = result === "win" ? difficultyProfile.gain : -Math.max(6, Math.round(difficultyProfile.gain / 2));
    const nextRating = Math.max(0, currentRating + delta);
    setRatings((current) => ({ ...current, [game]: nextRating }));
    setMatchResult(result);
    setMatchStarted(false);
    setComputerThinking(false);
    if (result === "win") {
      setWins((count) => count + 1);
      setStreak((count) => count + 1);
      notify(`Victoire confirmée · +${delta} rating · ${formatRating(nextRating)} points`);
    } else {
      setLosses((count) => count + 1);
      setStreak(0);
      notify(`Partie terminée · ${delta} rating · ${formatRating(nextRating)} points`);
    }
  }

  function registerTournament(name: string) {
    const next = registeredTournament === name ? null : name;
    setRegisteredTournament(next);
    notify(next ? `Inscription confirmée pour ${name}` : `Inscription annulée pour ${name}`);
  }

  function addTeam(event: FormEvent) {
    event.preventDefault();
    const cleanName = teamName.trim();
    if (!cleanName) return;
    setTeams((current) => [...current, { id: Date.now(), name: cleanName, game: teamGame, members: 1, tier: teamTier, mark: cleanName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase(), color: gameProfiles[teamGame].color }]);
    setTeamName("");
    setShowTeamForm(false);
    notify(`L’équipe « ${cleanName} » a été créée`);
  }

  function addTier(event: FormEvent) {
    event.preventDefault();
    const cleanName = tierName.trim();
    if (!cleanName) return;
    setTiers((current) => [...current, { name: cleanName, game: tierGame, minimum: Number(tierMinimum) || 0, color: "custom" }]);
    setTierName("");
    setShowTierForm(false);
    notify(`L’échelon « ${cleanName} » est disponible`);
  }

  function registerWorldCup(kind: GameKind) {
    const next = !registeredWorld[kind];
    setRegisteredWorld((current) => ({ ...current, [kind]: next }));
    notify(next ? `Pré-inscription confirmée pour la Coupe WAPI ${gameProfiles[kind].name}` : `Pré-inscription annulée pour la Coupe WAPI ${gameProfiles[kind].name}`);
  }

  return <div className="space-scroll games-space">
    <section className="games-hero">
      <div className="games-hero-copy">
        <span className="games-eyebrow"><i /> WAPI ARENA · SAISON 01</span>
        <h2>Jouez votre<br /><em>meilleur coup.</em></h2>
        <p>Des parties exigeantes, un accompagnement précis et des tournois où chaque progression compte.</p>
        <div className="games-hero-actions">
        <button className="games-primary" onClick={() => { setView("play"); notify("Votre espace de jeu est prêt"); }}>▶ Lancer une partie</button>
          <button onClick={() => setView("world")}>Compétition mondiale <span>↗</span></button>
        </div>
        <div className="games-proof"><span><strong>2 480</strong> joueurs actifs</span><span><strong>18</strong> tournois ce mois</span><span><strong>425 k</strong> FCFA à gagner</span></div>
      </div>
        <div className="games-hero-orbit" aria-hidden="true"><div className="orbit-ring ring-one" /><div className="orbit-ring ring-two" /><div className="games-piece">{profile.mark}</div><span className="orbit-label label-top">RATING {formatRating(currentRating)}</span><span className="orbit-label label-right">COACH IA + FIDE</span><span className="orbit-label label-bottom">LIVE · 126</span></div>
    </section>

    <section className="space-content games-content">
      <nav className="games-tabs" aria-label="Navigation de l’arène">
        <button className={view === "play" ? "active" : ""} onClick={() => setView("play")}>Jouer <small>12 en ligne</small></button>
        <button className={view === "world" ? "active" : ""} onClick={() => setView("world")}>Compétition mondiale <small>4 disciplines</small></button>
        <button className={view === "tournaments" ? "active" : ""} onClick={() => setView("tournaments")}>Tournois <small>4 ouverts</small></button>
        <button className={view === "community" ? "active" : ""} onClick={() => setView("community")}>Équipes & échelons <small>{teams.length} équipes</small></button>
        <button className={view === "coach" ? "active" : ""} onClick={() => setView("coach")}>Mon accompagnement <small>4 leçons</small></button>
      </nav>

      {view === "world" && <section className="world-view"><header className="world-cup-hero"><div><span className="world-kicker"><i /> COMPÉTITION MONDIALE WAPI</span><h3>Une arène.<br /><em>Le monde entier.</em></h3><p>Les meilleurs joueurs, équipes et esprits logiques se retrouvent dans une même saison.</p><button onClick={() => notify("Calendrier de la Coupe du Monde WAPI ouvert")}>Voir le calendrier officiel ↗</button></div><div className="world-cup-badge"><span>WAPI</span><strong>WORLD<br />CUP</strong><small>SAISON 01 · 2026</small></div></header><div className="world-timeline"><span className="current"><b>01</b><strong>Qualifications</strong><small>Septembre</small></span><i /><span><b>02</b><strong>Phases régionales</strong><small>Octobre</small></span><i /><span><b>03</b><strong>Grande finale</strong><small>Novembre · Brazzaville</small></span></div><div className="world-section-heading"><div><small>4 DISCIPLINES · 1 TITRE MONDIAL</small><h3>Choisissez votre terrain</h3></div><span>Inscription ouverte aux joueurs et aux équipes</span></div><div className="world-event-grid">{worldCupEvents.map((event) => <article className={`world-event-card ${event.tone}`} key={event.game}><header><span className="world-game-mark">{gameProfiles[event.game].mark}</span><span>{event.stage}</span></header><h4>{event.title}</h4><p>{event.date} <i /> {event.players}</p><div className="world-event-footer"><div><small>DOTATION</small><strong>{event.prize}</strong></div><button className={registeredWorld[event.game] ? "registered" : ""} onClick={() => registerWorldCup(event.game)}>{registeredWorld[event.game] ? "✓ Pré-inscrit" : "Se pré-inscrire"}</button></div></article>)}</div><section className="world-ranking"><header><div><small>CLASSEMENT PROVISOIRE</small><h3>Les joueurs à suivre</h3></div><button onClick={() => setView("community")}>Voir les équipes →</button></header><div>{[{ name: "Cyril B.", game: "Échecs", score: "1 842", mark: "CB" }, { name: "Amina M.", game: "Dames", score: "1 776", mark: "AM" }, { name: "Junior K.", game: "Sudoku", score: "1 688", mark: "JK" }].map((player, index) => <article key={player.name}><b>0{index + 1}</b><span>{player.mark}</span><div><strong>{player.name}</strong><small>{player.game}</small></div><strong>{player.score}</strong></article>)}</div></section></section>}

      {view === "community" && <section className="community-view"><header className="games-section-heading"><div><small>COMMUNAUTÉ COMPÉTITIVE</small><h3>Jouez ensemble, montez ensemble.</h3><p>Créez une équipe, choisissez votre discipline et faites progresser votre échelon.</p></div><button className="create-tournament" onClick={() => setShowTeamForm((current) => !current)}>＋ Créer une équipe</button></header><div className="community-layout"><section className="teams-panel"><header><div><small>MES ÉQUIPES</small><h4>Équipes actives</h4></div><span>{teams.length} équipes</span></header>{showTeamForm && <form className="arena-form" onSubmit={addTeam}><label>Nom de l’équipe<input value={teamName} onChange={(event) => setTeamName(event.target.value)} required placeholder="Ex. Les Cavaliers du Congo" /></label><div><label>Jeu<select value={teamGame} onChange={(event) => setTeamGame(event.target.value as GameKind)}>{(Object.keys(gameProfiles) as GameKind[]).map((kind) => <option key={kind} value={kind}>{gameProfiles[kind].name}</option>)}</select></label><label>Échelon de départ<select value={teamTier} onChange={(event) => setTeamTier(event.target.value)}>{tiers.map((tier) => <option key={tier.name} value={tier.name}>{tier.name}</option>)}</select></label></div><button type="submit">Créer l’équipe →</button></form>}<div className="team-grid">{teams.map((team) => <article className="team-card" key={team.id}><header><span className={`team-mark ${team.color}`}>{team.mark}</span><div><strong>{team.name}</strong><small>{gameProfiles[team.game].name} · {team.members} membres</small></div><b>●</b></header><div className="team-meta"><span><small>ÉCHELON</small><strong>{team.tier}</strong></span><span><small>OBJECTIF</small><strong>Coupe WAPI</strong></span></div><button onClick={() => notify(`Invitation à rejoindre ${team.name} envoyée`)}>Inviter des joueurs ↗</button></article>)}</div></section><section className="tiers-panel"><header><div><small>PROGRESSION</small><h4>Échelons WAPI</h4></div><button onClick={() => setShowTierForm((current) => !current)}>＋ Créer</button></header>{showTierForm && <form className="arena-form tier-form" onSubmit={addTier}><label>Nom de l’échelon<input value={tierName} onChange={(event) => setTierName(event.target.value)} required placeholder="Ex. Maître de Brazzaville" /></label><div><label>Jeu<select value={tierGame} onChange={(event) => setTierGame(event.target.value as GameKind | "all")}><option value="all">Tous les jeux</option>{(Object.keys(gameProfiles) as GameKind[]).map((kind) => <option key={kind} value={kind}>{gameProfiles[kind].name}</option>)}</select></label><label>Rating minimum<input type="number" min="0" step="50" value={tierMinimum} onChange={(event) => setTierMinimum(event.target.value)} /></label></div><button type="submit">Ajouter l’échelon →</button></form>}<div className="tier-list">{tiers.map((tier, index) => <article className={`tier-row ${tier.color}`} key={`${tier.name}-${index}`}><span>{String(index + 1).padStart(2, "0")}</span><div><strong>{tier.name}</strong><small>{tier.game === "all" ? "Tous les jeux" : gameProfiles[tier.game].name} · Rating {tier.minimum}+</small></div><b>{index === tiers.length - 1 ? "★" : "→"}</b></article>)}</div></section></div></section>}

      {view === "play" && <>
        <div className="games-section-heading"><div><small>PARTIE CLASSÉE · PROGRESSION ACTIVE</small><h3>À vous de jouer</h3><p>Affrontez l’ordinateur à votre niveau, gagnez du rating et gravissez les échelons.</p></div><div className="game-switcher" role="group" aria-label="Choisir un jeu">{(Object.keys(gameProfiles) as GameKind[]).map((kind) => <button key={kind} className={game === kind ? "active" : ""} onClick={() => { setGame(kind); setSelectedSquare(null); setMatchResult(null); }}>{gameProfiles[kind].mark} {gameProfiles[kind].name}</button>)}</div></div>
        <div className="arena-grid">
          <section className={`board-panel ${game}`}>
            <header><div><span className="live-pill"><i /> {matchResult ? "PARTIE TERMINÉE" : "MATCH EN DIRECT"}</span><strong>{profile.name} · {opponentMode === "computer" ? `Ordinateur ${difficultyProfile.name}` : "Adversaire en ligne"}</strong></div><div className="match-clock"><button onClick={() => setMatchStarted((current) => !current)} aria-label={matchStarted ? "Mettre la partie en pause" : "Reprendre la partie"} disabled={Boolean(matchResult)}>{matchStarted ? "Ⅱ" : "▶"}</button><span className="board-clock">{formatClock(matchSeconds)}</span></div></header>
            <div className="board-frame">{game !== "sudoku" && <div className="board-coordinates board-files">{["a", "b", "c", "d", "e", "f", "g", "h"].map((file) => <span key={file}>{file}</span>)}</div>}<div className={`game-board ${game === "sudoku" ? "sudoku-board" : ""} ${matchResult ? "game-finished" : ""}`} role="grid" aria-label={`Plateau de ${profile.name}`}>{Array.from({ length: game === "sudoku" ? 81 : 64 }, (_, index) => { const boardSize = game === "sudoku" ? 9 : 8; const row = Math.floor(index / boardSize); const column = index % boardSize; const dark = game === "sudoku" ? (Math.floor(row / 3) + Math.floor(column / 3)) % 2 === 1 : (row + column) % 2 === 1; const piece = pieceAt(index); const selected = selectedSquare === index; return <button key={index} className={`game-square ${dark ? "dark" : "light"} ${selected ? "selected" : ""} ${piece ? "has-piece" : ""}`} onClick={() => chooseSquare(index)} aria-label={`${squareLabel(index)}${piece ? `, ${piece}` : ""}`} disabled={Boolean(matchResult)}><span>{piece}</span></button>; })}</div>{computerThinking && <div className="computer-thinking"><i /> L’ordinateur calcule sa réponse…</div>}</div>
            <footer><div><small>TOUR DE JEU</small><strong>{moveCount}</strong></div><div><small>FORMAT</small><strong>{game === "sudoku" ? "9×9" : game === "ludo" ? "4 joueurs" : "10+5"}</strong></div><button onClick={() => game === "ludo" ? notify("Dé lancé : vous avancez de 6 cases") : game === "sudoku" ? notify("Grille vérifiée : 2 erreurs à corriger") : notify("Recherche d’un adversaire de niveau similaire…")}>{game === "ludo" ? "🎲 Lancer le dé" : game === "sudoku" ? "✓ Vérifier la grille" : "⚡ Trouver un adversaire"}</button></footer>
          </section>
          <aside className="games-side-column">
            <section className="ai-lab-card"><header><div><small>ADVERSAIRE ASSISTÉ PAR IA</small><strong>Choisissez votre défi</strong></div><span className="ai-status"><i /> PRÊT</span></header><div className="opponent-mode-switch"><button className={opponentMode === "computer" ? "active" : ""} onClick={() => { setOpponentMode("computer"); setMatchResult(null); notify("Mode ordinateur activé"); }}>🤖 Ordinateur</button><button className={opponentMode === "human" ? "active" : ""} onClick={() => { setOpponentMode("human"); setComputerThinking(false); setMatchResult(null); notify("Mode joueur en ligne activé"); }}>♟ Joueur</button></div>{opponentMode === "computer" && <><div className="difficulty-heading"><span>NIVEAU DE DIFFICULTÉ</span><b>{difficultyProfile.rating} Elo</b></div><div className="difficulty-grid">{(Object.keys(difficultyProfiles) as DifficultyId[]).map((level) => <button key={level} className={`${difficulty === level ? "active" : ""} ${difficultyProfiles[level].color}`} onClick={() => { setDifficulty(level); setMatchResult(null); }}><span>{difficultyProfiles[level].rating}</span><strong>{difficultyProfiles[level].name}</strong></button>)}</div><p className="difficulty-detail">{difficultyProfile.detail}. <b>+{difficultyProfile.gain} points de classement</b> en cas de victoire.</p></>}</section>
            <section className="coach-card"><header><span className="coach-avatar">MK</span><div><small>INSTRUCTEUR · EN LIGNE</small><strong>Maître Kévin</strong><span>FIDE 2 146 · 12 ans d’expérience</span></div><b>●</b></header><div className="coach-lesson"><small>CONSEIL SUR LA POSITION</small><strong>Développez votre cavalier avant de pousser le pion.</strong><p>Votre meilleur coup ici : <b>{game === "chess" ? "♞ f3" : "● c5"}</b></p></div><button className="coach-cta" onClick={() => { setView("coach"); setLessonStarted(true); }}>Ouvrir l’analyse avec l’instructeur ↗</button></section>
            <section className="quick-match"><header><div><small>FORMAT EXPRESS</small><strong>Partie rapide</strong></div><span>+ 126 joueurs</span></header><div className="quick-match-options"><button onClick={() => notify("Match 3+2 recherché")}>3+2 <small>Blitz</small></button><button className="selected" onClick={() => notify("Match 10+5 recherché")}>10+5 <small>Rapide</small></button><button onClick={() => notify("Match 15+10 recherché")}>15+10 <small>Classique</small></button></div><button className="quick-start" onClick={() => notify("Votre adversaire est en cours de recherche…")}>Lancer le matchmaking <span>→</span></button></section>
          <section className="rank-progress-card"><header><div><small>ESCALADE DES ÉCHELONS</small><strong>{currentTier.name}</strong></div><span className={`rank-badge ${currentTier.color}`}>★</span></header><div className="rank-rating-line"><b>{formatRating(currentRating)}</b><span>{nextTier ? `${formatRating(nextTier.minimum)} pour ${nextTier.name}` : "Sommet atteint"}</span></div><div className="rank-track"><i style={{ width: `${rankProgress}%` }} /></div><footer><span>{nextTier ? `${Math.max(0, nextTier.minimum - currentRating)} points avant la promotion` : "Vous êtes au sommet"}</span><button onClick={() => setView("community")}>Voir les échelons →</button></footer><div className="rank-stats"><span><b>{wins}</b><small>victoires</small></span><span><b>{losses}</b><small>défaites</small></span><span><b>{streak}</b><small>série actuelle</small></span></div></section>
          <section className="match-insights"><header><div><small>ANALYSE DE LA PARTIE</small><strong>Vue professionnelle</strong></div><span className="analysis-live">● {computerThinking ? "CALCUL" : matchResult ? "FINIE" : "LIVE"}</span></header><div className="analysis-tabs"><button className="active" onClick={() => notify("Analyse moteur activée")}>Analyse</button><button onClick={() => notify("Historique des coups ouvert")}>Coups</button><button onClick={() => notify("Chat de la partie ouvert")}>Chat</button></div><div className="move-history">{moveHistory.length ? moveHistory.map((move, index) => <span key={move + index}><small>{index + 1}</small>{move}</span>) : <em>Les coups joués apparaîtront ici</em>}</div><div className="match-result-actions"><button className="win" disabled={Boolean(matchResult) || computerThinking} onClick={() => finishMatch("win")}>✓ Victoire</button><button className="loss" disabled={Boolean(matchResult) || computerThinking} onClick={() => finishMatch("loss")}>Défaite</button></div><footer><button onClick={resetMatch}>↺ Nouvelle partie</button><button onClick={() => { setMatchStarted(false); setMatchResult("loss"); setComputerThinking(false); notify("Partie abandonnée · échelon conservé"); }}>Abandonner</button></footer></section>
          </aside>
        </div>
      </>}

      {view === "tournaments" && <section className="tournaments-view"><header className="games-section-heading"><div><small>PRIX & CLASSEMENT</small><h3>Les prochains tournois</h3><p>Inscrivez-vous gratuitement ou rejoignez une compétition premium avec cagnotte.</p></div><button className="create-tournament" onClick={() => notify("Le créateur de tournoi sera bientôt disponible pour votre communauté")}>＋ Créer un tournoi</button></header><div className="prize-notice"><span>◆</span><div><strong>Cagnotte sécurisée Wapi</strong><p>Les sommes sont affichées avant l’inscription. Le versement au gagnant intervient après validation du classement.</p></div><b>FCFA</b></div><div className="tournament-list">{tournamentRows.map((tournament) => <article className="tournament-card" key={tournament.name}><div className={`tournament-mark ${tournament.tone}`}>{tournament.game.startsWith("Dames") ? "●" : "♞"}</div><div className="tournament-main"><div className="tournament-title"><span>{tournament.tag}</span><h4>{tournament.name}</h4></div><p>{tournament.game} <i /> {tournament.date}</p><div className="tournament-capacity"><span><i style={{ width: `${(Number(tournament.players.split(" /")[0]) / Number(tournament.players.split(" /")[1])) * 100}%` }} /></span><small>{tournament.players} joueurs</small></div></div><div className="tournament-prize"><small>À GAGNER</small><strong>{tournament.prize}</strong><button className={registeredTournament === tournament.name ? "registered" : ""} onClick={() => registerTournament(tournament.name)}>{registeredTournament === tournament.name ? "✓ Inscrit" : "S’inscrire"}</button></div></article>)}</div></section>}

      {view === "coach" && <section className="coach-view"><header className="coach-view-header"><div><small>ACADÉMIE WAPI</small><h3>Votre instructeur, coup après coup.</h3><p>Comprenez vos décisions, corrigez vos automatismes et montez en classement.</p></div><div className="coach-rating"><span>RATING ACTUEL</span><strong>{profile.rating}</strong><small>+84 ce mois-ci</small></div></header><div className="coach-dashboard"><section className="lesson-feature"><div className="lesson-badge">LEÇON DU JOUR · 12 MIN</div><h4>Construire une attaque<br /><em>sans se précipiter.</em></h4><p>Une séquence guidée par Maître Kévin avec trois positions issues de vos dernières parties.</p><div className="lesson-progress"><span><i style={{ width: lessonStarted ? "68%" : "32%" }} /></span><small>{lessonStarted ? "8 / 12 min" : "3 / 12 min"}</small></div><button onClick={() => { setLessonStarted(true); notify("Leçon lancée avec Maître Kévin"); }}>{lessonStarted ? "Continuer la leçon" : "Démarrer la leçon"} <span>→</span></button></section><section className="coach-profile"><header><span className="coach-avatar large">MK</span><div><small>VOTRE COACH PRINCIPAL</small><h4>Maître Kévin</h4><p>FIDE 2 146 · Échecs & stratégie</p></div></header><div className="coach-stats"><span><strong>4</strong><small>leçons suivies</small></span><span><strong>78%</strong><small>précision moyenne</small></span><span><strong>+84</strong><small>rating gagné</small></span></div><button onClick={() => notify("Demande envoyée à Maître Kévin")}>Poser une question au coach ↗</button></section></div><div className="coach-modules"><header><div><small>PARCOURS PERSONNALISÉ</small><h4>Progresser cette semaine</h4></div><span>4 modules</span></header><div>{["Ouvertures solides", "Tactiques de fourchette", "Finales de pions", "Gérer son temps"].map((module, index) => <button key={module} onClick={() => notify(`${module} · module ouvert`)}><span>{index < 2 ? "✓" : String(index + 1).padStart(2, "0")}</span><div><strong>{module}</strong><small>{index < 2 ? "Terminé" : `${8 + index * 3} min · Niveau avancé`}</small></div><b>→</b></button>)}</div></div></section>}
    </section>
  </div>;
}

function Orbit({ go, setModal, setLiveIndex, notify, saved, setSaved, ad, onAdClick, footer, userName, founder }: { go: (space: Space) => void; setModal: (modal: "sell" | "seek" | "live") => void; setLiveIndex: (index: number) => void; notify: (text: string) => void; saved: Record<string, boolean>; setSaved: React.Dispatch<React.SetStateAction<Record<string, boolean>>>; ad?: AdCampaign; onAdClick: (campaign: AdCampaign) => void; footer?: React.ReactNode; userName: string; founder?: boolean }) {
  const [liked,setLiked]=useState(false);
  return <div className="orbit-scroll moments-home"><div className="moments-layout"><main className="moments-feed"><section className="moments-intro"><div><span>AUJOURD&apos;HUI · BRAZZAVILLE</span><h2>Ce qui se passe<br/><em>maintenant.</em></h2><p>Des personnes, des idées, des directs et des opportunités — réunis dans un fil vivant.</p></div><button onClick={()=>setModal("live")}>● Passer en direct</button></section><section className="story-line"><button className="add-story"><span>＋</span><strong>Votre moment</strong><small>Partager</small></button>{messages.slice(0,3).map((person,index)=><button key={person.name} onClick={()=>notify(`Moment de ${person.name} ouvert`)}><span>{person.mark}<i>{index===2?"●":""}</i></span><strong>{person.name.split(" ")[0]}</strong><small>{index===2?"EN DIRECT":"Nouveau"}</small></button>)}<button onClick={()=>go("live")}><span className="story-more">→</span><strong>Explorer</strong><small>Tout voir</small></button></section><article className="moment-card sponsored-moment"><header><span className="moment-avatar">{ad?ad.pageName.split(/\s+/).map((word)=>word[0]).join("").slice(0,2):"MS"}<i/></span><div><strong>{ad?.pageName||"Mokabi Studio"} <b>✓</b></strong><small>Publication sponsorisée · WAPI ADS</small></div><button>•••</button></header><div className="moment-visual"><span>{ad?"WAPI ADS":"WAPI LIVE"}</span><div><small>{ad?`${ad.audience} · ${ad.city}`:"COLLECTION N'TELA 2026"}</small><h3>{ad?.title||<>Porter son histoire.<br/>Vivre son style.</>}</h3><button onClick={()=>ad?onAdClick(ad):setLiveIndex(0)}>{ad?.cta||"Rejoindre le direct ●"}</button></div><b>{ad?"Sponsorisé":"2,8 k regardent"}</b></div><p>{ad?.creative||"La nouvelle collection est là. Découvrez chaque pièce en direct, posez vos questions et commandez sans quitter la vidéo."}</p><footer>{ad?<><button className="ad-cta" onClick={()=>onAdClick(ad)}>{ad.cta} →</button><span>Publicité · {ad.pageName}</span></>:<><button className={liked?"liked":""} onClick={()=>setLiked(v=>!v)}>{liked?"♥":"♡"} {liked?"1 205":"1 204"}</button><button onClick={()=>notify("Commentaires ouverts")}>◫ 86 commentaires</button><button onClick={()=>notify("Publication partagée")}>↗ Partager</button></>}</footer></article><article className="moment-card community-moment"><header><span className="moment-avatar">AM<i/></span><div><strong>Amina M. <b>✓</b></strong><small>Poto-Poto · il y a 24 min</small></div><button>•••</button></header><p className="moment-copy">Je transforme mon salon et je cherche une table artisanale locale. Budget raisonnable ou échange possible. Vous connaissez quelqu&apos;un ?</p><div className="moment-request"><span>⌖</span><div><small>RECHERCHE ACTIVE</small><strong>Table artisanale · Brazzaville</strong></div><button onClick={()=>notify("Réponse envoyée à Amina")}>Je peux aider</button></div><footer><button onClick={()=>notify("Vous aimez cette publication")}>♡ 48</button><button onClick={()=>notify("Commentaires ouverts")}>◫ 12 commentaires</button><button onClick={()=>notify("Publication partagée")}>↗ Partager</button></footer></article></main><aside className="moments-side"><section className="quick-publish"><span>CB</span><div><strong>Bonjour {userName}</strong>{founder ? <i className="founder-grey-badge" title="Compte fondateur Wapi by BCA">✓</i> : null}<small>Quoi de neuf aujourd&apos;hui ?</small></div><button onClick={()=>notify("Créateur de publication ouvert")}>＋ Publier</button></section><section className="side-card"><header><div><small>EN DIRECT</small><strong>Ça bouge maintenant</strong></div><button onClick={()=>go("live")}>Tout voir</button></header>{lives.slice(0,2).map((live,index)=><button className="side-live" key={live.host} onClick={()=>setLiveIndex(index)}><span>{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}<i/></span><div><strong>{live.title}</strong><small>{live.viewers} spectateurs</small></div><b>→</b></button>)}</section><section className="side-card"><header><div><small>WAPI MARKET</small><strong>Pour vous</strong></div><button onClick={()=>go("market")}>Explorer</button></header>{listings.slice(0,2).map(item=><div className="side-product" role="button" tabIndex={0} key={item.id} onClick={()=>go("market")} onKeyDown={(event)=>{if(event.key==="Enter"||event.key===" "){event.preventDefault();go("market");}}}><span>{item.mark}</span><div><strong>{item.title}</strong><small>{item.price}</small></div><button className={saved[String(item.id)]?"saved":""} aria-label={`Enregistrer ${item.title}`} onClick={event=>{event.stopPropagation();setSaved(current=>({...current,[String(item.id)]:!current[String(item.id)]}))}}>♡</button></div>)}</section><button className="home-create-ad" onClick={()=>go("business")}>✦ Promouvoir une publication</button></aside></div>{footer}</div>;
}

function LiveSpace({ setModal, setLiveIndex }: { setModal: (type: "live") => void; setLiveIndex: (index: number) => void }) {
  const [mode,setMode]=useState<"human"|"twin"|"relay">("relay");
  return <div className="space-scroll live-space">
    <section className="live-command twin-live-command"><div className="live-command-copy"><span className="signal"><i/> WAPI LIVE SHIFT™</span><h2>Vous commencez.<br/><em>Votre Double continue.</em></h2><p>Lancez un direct avec votre caméra, avec votre Double vidéo autorisé, ou passez de l&apos;un à l&apos;autre sans couper le live.</p><div className="live-mode-switch"><button className={mode==="human"?"active":""} onClick={()=>setMode("human")}><span>▣</span><strong>Moi, en direct</strong><small>Caméra et voix réelles</small></button><button className={mode==="twin"?"active":""} onClick={()=>setMode("twin")}><span>◎</span><strong>Mon Double</strong><small>Vidéo IA autorisée</small></button><button className={mode==="relay"?"active":""} onClick={()=>setMode("relay")}><span>⇄</span><strong>Passage de relais</strong><small>Humain + Double</small></button></div><button onClick={() => setModal("live")}>● Entrer dans le studio</button><small className="live-safety">✓ Votre image uniquement · Double signalé comme IA · Arrêt immédiat à tout moment</small></div><div className={`studio-preview shift-preview ${mode}`}><span className="preview-live">● LIVE · 00:12:48</span><div className="shift-status"><span>{mode==="human"?"CAMÉRA RÉELLE":mode==="twin"?"DOUBLE IA ACTIF":"PASSAGE DE RELAIS"}</span><b>{mode==="relay"?"Transition dans 00:08":"Contrôle créateur actif"}</b></div><div className="shift-stage"><div className={`shift-person human-feed ${mode==="human"||mode==="relay"?"on":""}`}><span>CB</span><small>VOUS · LIVE</small></div><div className="shift-link">{mode==="relay"?"⇄":"＋"}</div><div className={`shift-person twin-feed ${mode==="twin"||mode==="relay"?"on":""}`}><span>CB</span><small>DOUBLE · IA</small><b>IA</b></div></div><div className="preview-comments"><span>Ça existe en bleu ?</span><span>Le Double parle aussi lingala ?</span></div><div className="preview-product"><i>◇</i><span><small>PRODUIT ÉPINGLÉ</small><strong>Montre Kongo One</strong><b>42 000 FCFA</b></span><button>Acheter</button></div></div></section>
    <section className="space-content"><div className="shift-explainer"><div><span>01</span><strong>Vous créez la confiance</strong><p>Présentez, racontez et répondez vous-même.</p></div><i>→</i><div><span>02</span><strong>Le Double prend le relais</strong><p>Il reprend seulement le script et les produits approuvés.</p></div><i>→</i><div><span>03</span><strong>Vous revenez instantanément</strong><p>Un geste suffit pour reprendre la caméra en direct.</p></div></div><SectionTitle overline="EN CE MOMENT" title="Des directs augmentés, pas automatisés" action="Créer mon direct" onClick={() => setModal("live")}/><div className="big-live-grid">{lives.map((live,index)=><button key={live.host} className={`big-live ${live.tone} has-stream`} style={{backgroundImage:`linear-gradient(180deg,rgba(3,8,11,.04) 20%,rgba(3,8,11,.84) 100%),url(${live.poster})`}} onClick={() => setLiveIndex(index)}><span><i/> {live.badge}</span><div className="host-face">{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}</div><div><small>{live.host} · {live.viewers} spectateurs</small><h3>{live.title}</h3><p>Produit épinglé : {live.product}</p><b>{live.price} FCFA</b><em>▶ Ouvrir le direct</em></div></button>)}</div><div className="live-features"><div><span>⇄</span><strong>Live Shift™</strong><p>Passez de votre caméra au Double sans interrompre l&apos;audience.</p></div><div><span>◌</span><strong>Questions filtrées</strong><p>Le Double répond uniquement dans les limites que vous avez approuvées.</p></div><div><span>⏱</span><strong>Vente continue</strong><p>Quittez la scène ; votre boutique reste présentée sous votre contrôle.</p></div></div></section>
  </div>;
}

function MarketSpace({ search, filter, setFilter, items, shopCount, saved, setSaved, notify, setModal, onOpenShop, onOpen }: { search:string; filter:string; setFilter:(v:string)=>void; items:Listing[]; shopCount:number; saved:Record<string,boolean>; setSaved:React.Dispatch<React.SetStateAction<Record<string, boolean>>>; notify:(text:string)=>void; setModal:(type:"sell")=>void; onOpenShop:()=>void; onOpen:(item:Listing)=>void }) {
  const filters=["Tout","Tech","Mode","Maison","Services","Troc"];
  const [sort,setSort]=useState<"near"|"trust">("near");
  const [nearby,setNearby]=useState(false);
  const distance = (place: string) => {
    const match = place.match(/(\d+(?:[.,]\d+)?)\s*km/i);
    return match ? Number(match[1].replace(",", ".")) : Number.POSITIVE_INFINITY;
  };
  const nearbyItems = nearby ? items.filter((item) => distance(item.place) <= 5) : items;
  const sortedItems=[...nearbyItems].sort((a,b)=>sort==="trust"?b.trust-a.trust:distance(a.place)-distance(b.place));
  const intentCards = [
    { icon:"⌖", eyebrow:"DANS VOTRE QUARTIER", title:"Trouver près de moi", detail:"Les offres les plus proches, maintenant", action:()=>{ setNearby(true); notify("Les offres proches sont affichées"); } },
    { icon:"⇄", eyebrow:"VALEUR SANS FRONTIÈRE", title:"Faire un échange", detail:"Objets, services et accords gagnant-gagnant", action:()=>setFilter("Troc") },
    { icon:"◇", eyebrow:"OPPORTUNITÉ OUVERTE", title:"Publier une opportunité", detail:"Être trouvé par toute la communauté, sans réseau préalable", action:()=>setModal("sell") },
  ];
  const proofPoints = [
    { value:"98%", label:"vendeurs bien notés" },
    { value:"< 5 min", label:"pour publier une annonce" },
    { value:"24/7", label:"messages entre acheteurs et vendeurs" },
  ];
  return <div className="space-scroll market-space"><section className="market-banner marketplace-banner"><div><span>WAPI MARKETPLACE · OUVERT À TOUS</span><h2>Tout le monde peut<br/>ouvrir sa boutique.</h2><p>Vendez un objet, un service ou une création. Discutez avec l&apos;acheteur et préparez un paiement protégé.</p><div className="market-hero-actions"><button onClick={()=>setModal("sell")}>＋ Commencer à vendre</button><button onClick={onOpenShop}>Ma boutique ↗</button></div><div className="market-hero-note"><i/> Une marketplace pensée pour les vraies conversations.</div></div><div className="seller-console"><small>VOTRE BOUTIQUE WAPI</small><strong>{shopCount}</strong><span>{shopCount>1?"annonces publiées":"annonce publiée"}</span><div><b>{shopCount*37}</b><small>Vues</small><b>{shopCount*4}</b><small>Messages</small></div><button onClick={shopCount?onOpenShop:()=>setModal("sell")}>{shopCount?"Gérer ma boutique":"Publier mon premier produit"}</button></div></section><section className="space-content"><section className="market-intent-rail" aria-labelledby="market-intent-title"><div className="market-section-heading"><div><small>CHOISIR SON PARCOURS</small><h3 id="market-intent-title">Votre prochaine bonne affaire commence ici.</h3></div><span><i/> Marketplace en mouvement</span></div><div className="market-intents">{intentCards.map(card=><button key={card.title} onClick={card.action}><span className="intent-icon">{card.icon}</span><span><small>{card.eyebrow}</small><strong>{card.title}</strong><em>{card.detail}</em></span><b>↗</b></button>)}</div></section><div className="market-proof-grid" aria-label="Les indicateurs Wapi Marketplace">{proofPoints.map(point=><div key={point.label}><strong>{point.value}</strong><span>{point.label}</span></div>)}<div className="proof-live"><i/> <span>Transactions<br/><b>conversationnelles</b></span></div></div><section className="market-pulse" aria-labelledby="market-pulse-title"><header><div><small>EN CE MOMENT À BRAZZAVILLE</small><h3 id="market-pulse-title">Le marché bouge autour de vous.</h3></div><button onClick={()=>{setNearby(true);setSort("near");notify("Les nouveautés proches sont affichées")}}>Explorer les nouveautés ↗</button></header><div className="market-pulse-stats"><span><b>12</b><small>nouvelles annonces aujourd&apos;hui</small></span><span><b>5</b><small>services disponibles à moins de 5 km</small></span><span><b>3</b><small>échanges ouverts maintenant</small></span><span><b>2</b><small>boutiques répondent en direct</small></span></div></section><div className="payment-ready"><div><span>◆</span><div><small>PAIEMENTS À CONNECTER</small><strong>Préparez votre moyen d&apos;encaissement</strong></div></div><div className="payment-methods"><span>Mobile Money</span><span>Carte bancaire</span><span>Wapi Pay</span><span>Paiement à la livraison</span></div><button onClick={()=>notify("Le paiement à la livraison est sélectionné")}>Choisir ↗</button></div><div className="market-toolbar"><div>{filters.map(x=><button aria-pressed={filter===x} className={filter===x?"active":""} key={x} onClick={()=>setFilter(x)}>{x}</button>)}</div><button aria-pressed={nearby} className={nearby?"active":""} onClick={()=>{setNearby(v=>!v);notify(nearby?"Filtre de proximité retiré":"Produits proches affichés")}}>⌖ {nearby?"À proximité":"Autour de moi"}</button><button onClick={()=>setSort(v=>v==="near"?"trust":"near")}>≡ {sort==="near"?"Trier par confiance":"Trier par proximité"}</button></div><div className="results-line"><span>{sortedItems.length} produits et services {search && `pour « ${search} »`}</span><small>{nearby?"Dans un rayon de 5 km":"Vendeurs particuliers et professionnels"}</small></div><div className="listing-grid market-listings">{sortedItems.map(item=><ListingCard key={item.id} item={item} saved={!!saved[String(item.id)]} onSave={()=>setSaved(c=>({...c,[item.id]:!c[String(item.id)]}))} onOpen={()=>onOpen(item)}/>)}</div>{sortedItems.length===0&&<div className="market-empty"><span>⌕</span><h3>Aucun résultat</h3><p>Essayez une autre catégorie ou publiez votre propre annonce.</p><button onClick={()=>setModal("sell")}>＋ Publier une annonce</button></div>}<button className="market-sell-fab" onClick={()=>setModal("sell")}>＋ Vendre sur Wapi</button></section></div>;
}

function BarterSpace({ notify, setModal }: { notify:(text:string)=>void; setModal:(type:"sell")=>void }) {
  const [mine,setMine]=useState("Mon appareil photo"); const [want,setWant]=useState("Un ordinateur portable"); const [score,setScore]=useState<number|null>(null);
  return <div className="space-scroll barter-space"><section className="barter-hero"><span className="signal"><i/> WAPI MATCH</span><h2>La valeur ne se mesure<br/>pas toujours en argent.</h2><p>Décrivez ce que vous avez et ce que vous voulez. Notre moteur trouve les échanges possibles, même à plusieurs personnes.</p><div className="barter-engine"><label><small>JE PROPOSE</small><input value={mine} onChange={e=>setMine(e.target.value)}/><span>＋ Photo</span></label><button className="swap">⇄</button><label><small>JE RECHERCHE</small><input value={want} onChange={e=>setWant(e.target.value)}/><span>⌖ Zone : 25 km</span></label><button className="match" onClick={()=>setScore(94)}>Trouver un échange ✦</button></div>{score&&<div className="match-result"><span>{score}%</span><div><small>MEILLEURE CORRESPONDANCE</small><strong>Patrick propose un MacBook Pro</strong><p>Il cherche un appareil photo hybride + complément.</p></div><button onClick={()=>notify("Proposition de troc envoyée")}>Proposer le troc ↗</button></div>}</section><section className="space-content"><SectionTitle overline="ÉCHANGES OUVERTS" title="Le troc bouge près de vous" action="Publier un objet" onClick={()=>setModal("sell")}/><div className="barter-cards"><div><span className="barter-art violet">⌁</span><small>PROPOSE</small><strong>Service de photographie</strong><i>contre</i><small>RECHERCHE</small><strong>Création d&apos;un site vitrine</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div><span className="barter-art amber">◆</span><small>PROPOSE</small><strong>Canapé en excellent état</strong><i>contre</i><small>RECHERCHE</small><strong>Table à manger + 4 chaises</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div className="chain-card"><span>⇄</span><h3>Troc en chaîne</h3><p>Vous avez A, vous voulez B. Une troisième personne veut A et possède C. Wapi relie les trois.</p><b>18 chaînes possibles aujourd&apos;hui</b></div></div></section></div>;
}

function SeekSpace({ setModal, notify, items }: { setModal:(type:"seek")=>void; notify:(text:string)=>void; items:RequestItem[] }) {
  const [filter,setFilter]=useState("Tous"); const [answered,setAnswered]=useState<Record<string,boolean>>({});
  const visible=items.filter(item=>filter==="Tous"||(filter==="Urgent"&&item.urgent)||item.category===filter);
  return <div className="space-scroll seek-space"><section className="seek-hero"><div><span className="signal"><i/> INTELLIGENCE COLLECTIVE</span><h2>Demandez.<br/><em>Quelqu&apos;un sait.</em></h2><p>Un produit introuvable, une compétence urgente, une situation à résoudre ? Publiez votre besoin avec le lieu, le délai et votre budget.</p><button onClick={()=>setModal("seek")}>⌖ Publier ce que je cherche</button></div><div className="seek-cloud"><span className="q1">Un plombier maintenant</span><span className="q2">Appartement à louer</span><span className="q3">Pièce Toyota 2017</span><span className="q4">Graphiste disponible</span><span className="q5">Bon restaurant calme</span><b>⌖</b></div></section><section className="space-content"><div className="seek-tabs">{["Tous","Urgent","Produits","Services","Situations"].map(x=><button className={filter===x?"active":""} onClick={()=>setFilter(x)} key={x}>{x}</button>)}</div><div className="request-grid">{visible.map((item,index)=><article key={item.id}><header><span className={item.urgent?"urgent":""}>{item.urgent?"URGENT":item.category.toUpperCase()}</span><small>{typeof item.id==="number"&&item.id>4?"À l'instant":`Il y a ${index*7+3} min`}</small></header><h3>{item.title}</h3><p>{item.details}</p><div><span>⌖ {item.place}</span><b>{item.reward}</b></div><footer><span>{index*4+7} personnes ont vu</span><button className={answered[item.id]?"answered":""} disabled={answered[item.id]} onClick={()=>{setAnswered(current=>({...current,[item.id]:true}));notify("Votre réponse a été envoyée")}}>{answered[item.id]?"✓ Réponse envoyée":"Je peux aider ↗"}</button></footer></article>)}</div>{visible.length===0&&<div className="market-empty"><span>⌖</span><h3>Aucune recherche ici</h3><p>Soyez la première personne à publier un besoin.</p><button onClick={()=>setModal("seek")}>Publier une recherche</button></div>}</section></div>;
}

const previewCalls = [
  { name: "Amina M.", mark: "AM", direction: "Entrant", status: "Abouti", time: "Aujourd’hui · 14:32", video: false },
  { name: "Junior K.", mark: "JK", direction: "Sortant", status: "Abouti", time: "Aujourd’hui · 11:08", video: true },
  { name: "Mokabi Store", mark: "MS", direction: "Entrant", status: "Manqué", time: "Hier · 18:46", video: false },
  { name: "Nadia M.", mark: "NM", direction: "Sortant", status: "Annulé", time: "Lundi · 09:15", video: true },
] as const;

function CallsPreviewSpace({ search, onCall, onMessages }: { search:string; onCall:(contact:string,video:boolean)=>void; onMessages:()=>void }) {
  const visible = previewCalls.filter((item) => `${item.name} ${item.direction} ${item.status}`.toLowerCase().includes(search.toLowerCase()));
  const missed = previewCalls.filter((item) => item.status === "Manqué").length;
  return <div className="calls-preview-space"><section className="calls-preview-hero"><div><span>WAPI CALLS</span><h2>Vos appels,<br/><em>au même endroit.</em></h2><p>Retrouvez vos communications audio et vidéo, rappelez vos contacts en un geste et repérez immédiatement les appels manqués.</p><button onClick={onMessages}>◫ Ouvrir les discussions</button></div><aside><small>ACTIVITÉ RÉCENTE</small><strong>{previewCalls.length}</strong><span>appels enregistrés</span><div><p><b>{previewCalls.length - missed}</b> aboutis</p><p><b>{missed}</b> manqué</p></div></aside></section><section className="calls-preview-body"><header><div><small>HISTORIQUE DES APPELS</small><h3>Audio et vidéo</h3></div><span>Synchronisé sur vos appareils</span></header><div className="call-history">{visible.map((item) => <article className={item.status === "Manqué" ? "missed" : ""} key={`${item.name}-${item.time}`}><span>{item.mark}</span><div><strong>{item.name}</strong><small>{item.direction === "Sortant" ? "↗" : "↙"} {item.direction} · {item.status}</small><time>{item.time}</time></div><i>{item.video ? "VIDÉO" : "AUDIO"}</i><button onClick={() => onCall(item.name, false)} aria-label={`Appeler ${item.name}`}>☎</button><button onClick={() => onCall(item.name, true)} aria-label={`Appel vidéo avec ${item.name}`}>▣</button></article>)}{!visible.length && <div className="call-history-empty"><span>⌕</span><h3>Aucun appel trouvé</h3><p>Essayez le nom d’un autre contact.</p><button onClick={onMessages}>Voir les discussions</button></div>}</div></section></div>;
}

function InboxSpace({ search, userId, offer, initialContact = "", setModal, notify, onCall }: { search:string; userId:string; offer:DemoOffer|null; initialContact?:string; setModal:(type:"message")=>void; notify:(text:string)=>void; onCall:(contact:string,video:boolean)=>void }) {
  const [selected,setSelected]=useState(0); const [text,setText]=useState(""); const [mobileChat,setMobileChat]=useState(false); const [filter,setFilter]=useState("Tout"); const [sent,setSent]=useState<Record<number,CloudMessage[]>>({}); const [sending,setSending]=useState(false);
  const handledOffer=useRef(0);
  const notifyRef=useRef(notify);
  useEffect(()=>{notifyRef.current=notify;},[notify]);
  useEffect(()=>{if(!initialContact)return;const index=messages.findIndex((message)=>message.name===initialContact);if(index<0)return;queueMicrotask(()=>{setSelected(index);setMobileChat(true);});},[initialContact]);
  const visibleMessages = messages.map((message,index)=>({message,index})).filter(({message})=>`${message.name} ${message.text}`.toLowerCase().includes(search.toLowerCase()));
  const filteredMessages=visibleMessages.filter(({message:m})=>filter==="Tout"||(filter==="Non lus"&&m.unread>0)||(filter==="Groupes"&&m.name==="Design Crew")||(filter==="Affaires"&&m.name!=="Design Crew"));
  useEffect(()=>{
    if(!userId)return;
    const contact=messages[selected];
    return watchConversationMessages(userId,contact.mark,contact.name,(items)=>setSent(current=>({...current,[selected]:items})),()=>notifyRef.current("Messages hors ligne — réessayez dans un instant"));
  },[userId,selected]);
  useEffect(()=>{if(!offer||handledOffer.current===offer.id)return;handledOffer.current=offer.id;setSent((current)=>({...current,[selected]:[...(current[selected]||[]),{id:`offer-${offer.id}`,text:offer.text,senderId:"local",kind:offer.mediaKind,mediaUrl:offer.mediaUrl,mediaName:offer.mediaName,createdAt:{toDate:()=>new Date()}}]}));},[offer,selected]);
  async function send(e:FormEvent){
    e.preventDefault();const value=text.trim();if(!value||sending)return;
    const contact=messages[selected];setText("");
    if(!userId){setSent(current=>({...current,[selected]:[...(current[selected]||[]),{id:`local-${Date.now()}`,text:value,senderId:"local",createdAt:{toDate:()=>new Date()}}]}));notify("Message ajouté à la démonstration locale");return;}
    setSending(true);
    try{await sendConversationMessage(userId,contact.mark,contact.name,value);notify("Message envoyé et synchronisé");}
    catch{setText(value);notify("Échec de l'envoi — votre message a été conservé");}
    finally{setSending(false);}
  }
  return <div className={`inbox-space ${mobileChat?"chat-open":""}`}><aside className="inbox-list"><div className="inbox-title"><div><small>MESSAGERIE PRIORITAIRE</small><strong>Discussions <span>3 nouvelles</span></strong></div><button onClick={()=>{setText("Bonjour, ");notify("Choisissez un contact puis écrivez votre message")}} aria-label="Nouvelle conversation">＋</button></div><div className="inbox-filters">{["Tout","Non lus","Groupes","Affaires"].map(item=><button key={item} className={filter===item?"active":""} onClick={()=>setFilter(item)}>{item}</button>)}</div>{filteredMessages.map(({message:m,index})=><button className={selected===index?"active":""} onClick={()=>{setSelected(index);setMobileChat(true)}} key={m.name}><span className="profile-avatar"><Mark color={m.color}>{m.mark}</Mark><i/></span><span><strong>{m.name}<em>{m.badge}</em></strong><small>{m.text}</small></span><i>{m.time}</i>{m.unread>0&&<b>{m.unread}</b>}</button>)}{filteredMessages.length===0&&<p className="empty-messages">Aucune conversation trouvée.</p>}</aside><section className="deal-chat"><header><button className="chat-back" onClick={()=>setMobileChat(false)} aria-label="Retour aux discussions">←</button><span className="profile-avatar large"><Mark color={messages[selected].color}>{messages[selected].mark}</Mark><i/></span><div><strong>{messages[selected].name}</strong><small><i/> En ligne · Identité vérifiée</small></div><button onClick={()=>onCall(messages[selected].name,false)} aria-label="Appel audio">☎</button><button onClick={()=>onCall(messages[selected].name,true)} aria-label="Appel vidéo">▣</button><button onClick={()=>notify("Options de conversation : silencieux, bloquer, signaler")} aria-label="Options">•••</button></header><div className="profile-pulse"><span>✦</span><div><small>{messages[selected].badge} · {messages[selected].streak} ÉCHANGES RÉUSSIS</small><strong>{messages[selected].mood}</strong></div><button onClick={()=>notify(`Profil de ${messages[selected].name} ouvert`)}>Voir le profil ↗</button></div><div className="deal-context"><span className="product-thumb">◇</span><div><small>CONVERSATION LIÉE À UNE OPPORTUNITÉ</small><strong>{selected===0?"Canapé modulable en velours":"MacBook Air M3 · Comme neuf"}</strong><p>{selected===0?"Échange accepté":"750 000 FCFA"}</p></div><button onClick={()=>notify("Annonce ouverte")}>Voir l&apos;annonce ↗</button></div><div className="deal-messages" key={selected}><span className="chat-date">AUJOURD&apos;HUI</span><div className="theirs">Bonjour ! Est-ce que votre annonce est toujours disponible ?<small>12:03</small></div><div className="mine">Oui, absolument. On peut aussi discuter d&apos;un échange.<small>12:05 · <b className="message-seen">✓✓ Vu</b></small></div><div className="theirs">Parfait, je vous envoie ma proposition.<small>12:08</small></div>{(sent[selected]||[]).map((message)=><div className="mine" key={message.id}>{message.text}<small><time>{messageTime(message)}</time> · <b className="message-seen">✓✓ Vu</b></small></div>)}</div><div className="smart-replies" aria-label="Réponses rapides">{["Oui, c’est disponible","Je vous appelle ?","Faire une offre"].map(reply=><button key={reply} onClick={()=>setText(reply)}>{reply}<span>＋</span></button>)}</div><form onSubmit={send}><label className="chat-upload" aria-label="Ajouter une pièce jointe">＋<input type="file" accept="image/*,video/*,.pdf" onChange={e=>e.target.files?.[0]&&notify(`${e.target.files[0].name} prêt à envoyer`)}/></label><button type="button" onClick={()=>setText(value=>`${value} 😊`)} aria-label="Ajouter un emoji">☺</button><input value={text} onChange={e=>setText(e.target.value)} maxLength={4000} placeholder="Écrire un message…"/><button className="voice-action" type="button" onClick={()=>notify("Enregistrement vocal prêt — appuyez de nouveau pour envoyer")} aria-label="Message vocal">◉</button><button className="offer-action" type="button" onClick={()=>setModal("message")}>◇ Offre</button><button className="send-action" type="submit" disabled={sending} aria-label={sending?"Envoi en cours":"Envoyer"}>{sending?"···":"➤"}</button></form></section></div>;
}

function TwinSpace({ step, setStep, consent, setConsent, notify }: { step:number; setStep:(n:number)=>void; consent:boolean; setConsent:(v:boolean)=>void; notify:(text:string)=>void }) {
  const [product,setProduct]=useState("Montre Kongo One"); const [price,setPrice]=useState("42 000 FCFA"); const [tone,setTone]=useState("Chaleureux"); const [languages,setLanguages]=useState(["Français","Lingala"]);
  function saveDraft(){try{localStorage.setItem("whappy-twin-draft",JSON.stringify({product,price,tone,languages,updatedAt:new Date().toISOString()}));notify("Double enregistré en brouillon sur cet appareil");}catch{notify("Le stockage local est bloqué par votre navigateur");}}
  return <div className="space-scroll twin-space"><section className="twin-hero"><div className="twin-copy"><span className="signal"><i/> STUDIO DOUBLE · VOTRE IMAGE, VOTRE CONTRÔLE</span><h2>Vous créez une fois.<br/><em>Votre Double continue de vendre.</em></h2><p>Enregistrez votre propre vidéo. Wapi prépare un présentateur numérique pour les produits, langues et formats que vous autorisez.</p><div className="safety-chips"><span>✓ Consentement explicite</span><span>✓ Révocable à tout moment</span><span>✓ Marqué comme IA</span></div></div><div className="twin-visual"><div className="scan-lines"/><div className="human"><span>CB</span><small>VOUS</small></div><div className="transfer">··· ✦ ···</div><div className="digital"><span>CB</span><small>DOUBLE IA</small><b>IA</b></div></div></section><section className="twin-builder"><div className="builder-steps">{["Consentement","Enregistrement","Produits","Personnalité","Publication"].map((x,index)=><button className={step===index+1?"active":step>index+1?"done":""} onClick={()=>{if(index===0||consent)setStep(index+1);else notify("Validez d’abord votre consentement")}} key={x}><span>{step>index+1?"✓":index+1}</span><small>{x}</small></button>)}</div><div className="builder-card">{step===1&&<><span className="builder-icon">◎</span><h3>Votre identité vous appartient</h3><p>Wapi utilise uniquement les vidéos de vous-même que vous fournissez. Votre Double ne peut pas représenter une autre personne et chaque vidéo générée porte le label « Créé avec un Double IA ».</p><label className="consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/><span>Je confirme créer un Double à partir de ma propre image et j&apos;accepte son utilisation uniquement pour mes contenus Wapi autorisés.</span></label><button disabled={!consent} onClick={()=>setStep(2)}>Continuer vers l&apos;enregistrement ↗</button></>}{step===2&&<TwinRecorder onComplete={()=>{notify("Capsule vidéo validée");setStep(3)}}/>}{step===3&&<><span className="builder-icon">◇</span><h3>Ajoutez ce que votre Double vendra</h3><p>Définissez le produit et le prix que le Double pourra présenter. Vous pourrez toujours les modifier.</p><div className="twin-product-fields"><label>Produit<input value={product} onChange={e=>setProduct(e.target.value)} placeholder="Nom du produit"/></label><label>Prix ou offre<input value={price} onChange={e=>setPrice(e.target.value)} placeholder="Prix en FCFA"/></label></div><button disabled={!product.trim()} onClick={()=>setStep(4)}>Configurer sa personnalité ↗</button></>}{step===4&&<><span className="builder-icon">✦</span><h3>Donnez-lui votre ton</h3><div className="tone-grid">{["Chaleureux","Expert","Énergique","Élégant"].map(item=><button className={tone===item?"active":""} onClick={()=>setTone(item)} key={item}>{item}</button>)}</div><div className="language-grid">{["Français","Lingala","Anglais","Kituba"].map(item=><label key={item}><input type="checkbox" checked={languages.includes(item)} onChange={()=>setLanguages(current=>current.includes(item)?current.filter(x=>x!==item):[...current,item])}/>{item}</label>)}</div><p>Réponses commerciales seulement, aucune prise de position personnelle.</p><button disabled={!languages.length} onClick={()=>setStep(5)}>Prévisualiser mon Double ↗</button></>}{step===5&&<><span className="builder-icon ready">✓</span><h3>Votre brouillon de Double est prêt</h3><p><strong>{tone}</strong>, en {languages.join(" et ")}, il présentera <strong>{product}</strong> à <strong>{price}</strong>. La génération IA finale nécessitera le service vidéo Wapi et votre validation avant publication.</p><div className="publish-options"><label><input type="checkbox" defaultChecked/> Boutique Wapi</label><label><input type="checkbox" defaultChecked/> Replay des directs</label><label><input type="checkbox"/> Réponses vidéo automatiques</label></div><button onClick={saveDraft}>Enregistrer en brouillon ✦</button></>}</div></section></div>;
}
void TwinSpace;

function ListingCard({ item, saved, onSave, onOpen }: { item:Listing; saved:boolean; onSave:()=>void; onOpen:()=>void }) {
  return <article className="listing-card"><button type="button" aria-label={`${saved?"Retirer des favoris":"Enregistrer"} : ${item.title}`} aria-pressed={saved} className={`save ${saved?"active":""}`} onClick={onSave}>{saved?"♥":"♡"}</button><button type="button" aria-label={`Ouvrir l’annonce : ${item.title}`} className={`listing-art ${item.tone} ${item.mediaUrl?"has-media":""}`} style={item.mediaUrl?{backgroundImage:`linear-gradient(180deg,transparent 45%,rgba(2,13,18,.72)),url(${item.mediaUrl})`}:undefined} onClick={onOpen}>{!item.mediaUrl&&<span>{item.mark}</span>}<small>{item.category}</small>{item.mode==="troc"&&<b>⇄ TROC</b>}{item.status==="reserved"&&<em className="reserved-badge">RÉSERVÉ</em>}</button><div><span className="seller"><i>{item.mark}</i>{item.seller}<b>✓</b><small>{item.trust}% fiable</small></span><h3>{item.title}</h3><strong>{item.price}</strong><p>⌖ {item.place}</p><small className="listing-reach">◉ Ouvert à toute la communauté · pas besoin d’être ami</small><button type="button" onClick={onOpen}>{item.mode==="troc"?"Proposer un échange":"Contacter le vendeur"} ↗</button></div></article>;
}

function SectionTitle({ overline,title,action,onClick }: { overline:string;title:string;action:string;onClick:()=>void }) { return <div className="section-title"><div><small>{overline}</small><h3>{title}</h3></div><button onClick={onClick}>{action} ↗</button></div>; }

function LiveViewerPro({ live,onClose,notify,onAdd }: { live:(typeof lives)[number];onClose:()=>void;notify:(text:string)=>void;onAdd:(live:(typeof lives)[number],quantity:number)=>void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [heart,setHeart] = useState(false);
  const [heartCount,setHeartCount] = useState(1284);
  const [heartBurst,setHeartBurst] = useState(0);
  const [following,setFollowing] = useState(false);
  const [quantity,setQuantity] = useState(1);
  const [playing,setPlaying] = useState(true);
  const [elapsed,setElapsed] = useState(0);
  const [connection,setConnection] = useState<"Excellente"|"Stable">("Excellente");
  const [captions,setCaptions] = useState(true);
  const [message,setMessage] = useState("");
  const [replyTarget,setReplyTarget] = useState<{ id:string; name:string }|null>(null);

  const [comments,setComments] = useState<LiveComment[]>([
    { id:"l-com-1",name:"Amina",text:"Livraison possible ?",hearts:2,sentAt:LIVE_NOW-420000 },
    { id:"l-com-2",name:"Junior",text:"Je prends en bleu 🔥",hearts:4,sentAt:LIVE_NOW-290000 },
    { id:"l-com-3",name:"Grâce",text:"Très beau produit !",hearts:5,sentAt:LIVE_NOW-180000 },
  ]);

  const [giftWallets,setGiftWallets] = useState<Record<GiftCarrier, number>>({ acheteur: 125000,offreur: 80000 });
  const [giftLedger,setGiftLedger] = useState<GiftWalletLedger>({
    acheteur: { count: 0, amount: 0 },
    offreur: { count: 0, amount: 0 },
  });
  const [giftTopUpLedger,setGiftTopUpLedger] = useState<GiftWalletLedger>({
    acheteur: { count: 0, amount: 0 },
    offreur: { count: 0, amount: 0 },
  });
  const [giftCarrier,setGiftCarrier] = useState<GiftCarrier>("acheteur");
  const [selectedGift,setSelectedGift] = useState<LiveGift|null>(null);
  const [giftNote,setGiftNote] = useState("");
  const [giftReveal,setGiftReveal] = useState<LiveGiftCarrier|null>(null);
  const [featuredGift,setFeaturedGift] = useState<LiveGiftCarrier|null>(null);
  const [giftTopUp,setGiftTopUp] = useState<number>(liveGiftTopUps[1]);

  const [stageGuests,setStageGuests] = useState<string[]>([]);
  const [stageOpen,setStageOpen] = useState(false);
  const [mutedGuests,setMutedGuests] = useState<string[]>([]);
  const [spotlight,setSpotlight] = useState<string|null>(null);
  const [moderationLog,setModerationLog] = useState<ModerationEvent[]>([]);
  const [showHiddenComments,setShowHiddenComments] = useState(false);

  const hostInitials = live.host.split(" ").map((x)=>x[0]).join("").slice(0,2);
  const giftBalance = giftWallets[giftCarrier];
  const selectedCarrierLedger = giftLedger[giftCarrier];
  const selectedCarrierTopUpLedger = giftTopUpLedger[giftCarrier];

  const orderedComments = useMemo(() => {
    return [...comments].sort((left,right)=> (left.pinned === right.pinned ? right.sentAt - left.sentAt : left.pinned ? -1 : 1));
  }, [comments]);
  const visibleComments = useMemo(() => orderedComments.filter((comment)=>!comment.hidden), [orderedComments]);
  const hiddenComments = useMemo(() => orderedComments.filter((comment)=>comment.hidden), [orderedComments]);
  const discussionComments = showHiddenComments ? orderedComments : visibleComments;

  const canAfford = true;
  const canBuyFromCarrier = (_gift:LiveGift) => true;
  const stageGuestsMax = LIVE_STAGE_MAX - 1;
  const stageCount = Math.min(stageGuests.length + 1, LIVE_STAGE_MAX);
  const stageSlotsRemaining = Math.max(0, stageGuestsMax - stageGuests.length);
  const previewSlots = Math.min(stageSlotsRemaining, 4);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (!playing) return;
      setElapsed((value) => value + 1);
      setConnection(Math.random() > .16 ? "Excellente" : "Stable");
    }, 1000);
    return () => window.clearInterval(timer);
  }, [playing]);

  useEffect(() => {
    const player = videoRef.current;
    if (!player) return;
    if (playing) void player.play().catch(() => setPlaying(false));
    else player.pause();
  }, [playing]);

  useEffect(() => {
    if (!giftReveal) return;
    const timer = window.setTimeout(() => setGiftReveal(null), 2400);
    return () => window.clearTimeout(timer);
  }, [giftReveal]);

  const clock = `${Math.floor(elapsed/60).toString().padStart(2,"0")}:${(elapsed%60).toString().padStart(2,"0")}`;

  function fullscreen(){
    const request = videoRef.current?.closest(".live-video")?.requestFullscreen?.();
    if (!request) { notify("Le plein écran n’est pas disponible dans ce navigateur"); return; }
    void request.catch(() => notify("Le plein écran n’est pas disponible dans ce navigateur"));
  }

  function cancelReply() {
    setReplyTarget(null);
    setMessage("");
  }

  function sendComment(event:FormEvent){
    event.preventDefault();
    const text = message.trim();
    if(!text) return;
    const formattedText = replyTarget ? `@${replyTarget.name} ${text}` : text;
    setComments(current => [...current,{id:`comment-${Date.now()}`,name:"Vous",text:formattedText,hearts:0,sentAt:Date.now()}]);
    setMessage("");
    setReplyTarget(null);
    notify("Votre message est visible dans le direct");
  }

  function sendGiftTopUp() {
    if (giftTopUp <= 0) return;
    setGiftWallets(current => ({ ...current, [giftCarrier]: current[giftCarrier] + giftTopUp }));
    setGiftTopUpLedger(current => ({
      ...current,
      [giftCarrier]: {
        ...current[giftCarrier],
        count: current[giftCarrier].count + 1,
        amount: current[giftCarrier].amount + giftTopUp,
      },
    }));
    notify(`Crédit ajouté : +${formatLiveMoney(giftTopUp)} FCFA au porteur ${giftCarrier}`);
  }

  function sendGift() {
    if(!selectedGift) return;
    const payload = { gift: selectedGift, note: giftNote.trim(), carrier: giftCarrier };
    const carrierLabel = giftCarrier === "acheteur" ? "Acheteur" : "Offreur";

    setGiftLedger(current=>({
      ...current,
      [giftCarrier]: {
        ...current[giftCarrier],
        count: current[giftCarrier].count + 1,
        amount: current[giftCarrier].amount + selectedGift.price,
      },
    }));
    setComments(current=>[
      ...current,
      {
        id:`gift-${Date.now()}`,
        name:"Vous",
        text:`${selectedGift.icon} ${selectedGift.name} (${carrierLabel})${payload.note ? ` · ${payload.note}` : ""}`,
        hearts:selectedGift.hearts,
        sentAt:Date.now(),
      },
    ]);

    setFeaturedGift(payload);
    setGiftReveal(payload);
    notify(`Cadeau gratuit ${selectedGift.name} envoyé à ${live.host} par ${carrierLabel}`);
    setSelectedGift(null);
    setGiftNote("");
  }

  function sendLiveHeart() {
    setHeart(true);
    setHeartCount((value) => value + 1);
    setHeartBurst((value) => value + 1);
  }

  function sendCommentHeart(commentId:string) {
    setComments(current => current.map((comment) => comment.id === commentId ? { ...comment, hearts: comment.hearts + 1 } : comment));
    sendLiveHeart();
  }

  function logModeration(action: ModerationAction, comment: LiveComment) {
    setModerationLog(current => [{
      id: Date.now(),
      action,
      commentId: comment.id,
      commentName: comment.name,
      by: "Animateur",
      at: Date.now(),
    }, ...current].slice(0, 8));
  }

  function replyToComment(comment:LiveComment) {
    setReplyTarget({ id: comment.id, name: comment.name });
    notify(`Réponse préparée pour ${comment.name}`);
  }

  function pinComment(commentId:string) {
    const comment = comments.find((item)=>item.id===commentId);
    if(!comment) return;
    const nextPinned = !comment.pinned;
    setComments(current => current.map((item)=> item.id === commentId ? {
      ...item,
      pinned: nextPinned,
      hidden: nextPinned ? false : item.hidden,
    } : item));
    logModeration(nextPinned ? "pin" : "unpin", comment);
    notify(nextPinned ? `Commentaire de ${comment.name} épinglé` : `Épinglage retiré pour ${comment.name}`);
  }

  function hideComment(commentId:string) {
    const comment = comments.find((item)=>item.id===commentId);
    if(!comment) return;
    setComments(current => current.map((item)=> item.id === commentId ? { ...item, hidden: true } : item));
    logModeration("hide", comment);
    notify(`Commentaire de ${comment.name} masqué`);
  }

  function restoreComment(commentId:string) {
    const comment = comments.find((item)=>item.id===commentId);
    if(!comment) return;
    setComments(current => current.map((item)=> item.id === commentId ? { ...item, hidden: false } : item));
    logModeration("restore", comment);
    notify(`Commentaire de ${comment.name} restauré`);
  }

  function moderationLabel(action: ModerationAction) {
    if (action === "pin") return "épinglé";
    if (action === "unpin") return "désépinglé";
    if (action === "restore") return "restauré";
    return "masqué";
  }

  function toggleStageGuest(name:string) {
    const isOnStage = stageGuests.includes(name);
    if (isOnStage) {
      setStageGuests(current => current.filter((guest) => guest !== name));
      setMutedGuests(current => current.filter((guest) => guest !== name));
      setSpotlight(current => current === name ? null : current);
      notify(`${name} est retiré de la scène`);
      return;
    }
    if (stageGuests.length >= stageGuestsMax) {
      notify(`La scène est complète : ${LIVE_STAGE_MAX} personnes maximum avec l’hôte`);
      return;
    }
    setStageGuests(current => [...current,name]);
    notify(name === "Vous" ? "Vous êtes monté sur le live" : `Invité ajouté à la scène du live`);
  }

  function toggleGuestMute(name:string) {
    setMutedGuests(current => current.includes(name) ? current.filter(guest => guest !== name) : [...current, name]);
  }

  async function shareLive() {
    const share = { title: live.title, text: `Rejoignez ${live.host} sur Wapi Live`, url: window.location.href };
    try {
      if (navigator.share) await navigator.share(share);
      else {
        await navigator.clipboard.writeText(share.url);
        notify("Lien du direct copié");
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) notify("Le partage n’est pas disponible actuellement");
    }
  }

  const caption = live.tone === "food"
    ? "Je vous montre chaque étape, puis vous pourrez réserver la masterclass."
    : live.tone === "tech"
      ? "On compare l’écran, la caméra et l’autonomie en conditions réelles."
      : "La veste N’Tela est disponible en plusieurs tailles et coloris.";

  return <div className="live-viewer">
    <div className={`live-video ${live.tone}`}>
      <video ref={videoRef} className="viewer-stream" src={live.stream} poster={live.poster} autoPlay loop muted playsInline preload="metadata" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} />
      <div className="viewer-scrim" />
      <button className="viewer-close" onClick={onClose} aria-label="Fermer le direct">×</button>
      <header>
        <span><i/> EN DIRECT</span>
        <b><i className="connection-dot"/> {live.viewers} spectateurs</b>
      </header>
      <div className="viewer-meta">
        <span>{clock}</span>
        <span>HD · 1080p</span>
        <span>{connection}</span>
      </div>

      {featuredGift && (
        <aside className="featured-gift" aria-live="polite">
          <span className="gift-bearer">VOUS <i>{featuredGift.gift.icon}</i></span>
          <div>
            <small>CADEAU PORTÉ PAR {featuredGift.carrier === "acheteur" ? "L’ACHETEUR" : "L’OFFREUR"}</small>
            <strong>{featuredGift.gift.name} pour {live.host}</strong>
            {featuredGift.note && <p>“{featuredGift.note}”</p>}
          </div>
          <button onClick={sendLiveHeart} aria-label="Envoyer un cœur au cadeau">♥</button>
        </aside>
      )}

      {stageGuests.length > 0 && (
        <div className="live-stage-strip" aria-label={`${stageCount} personnes sur la scène`}>
          <span className="stage-host">{hostInitials}</span>
          {stageGuests.map((guest) => <span key={guest}>{guest.slice(0,2).toUpperCase()}</span>)}
          <small>{stageCount}/{LIVE_STAGE_MAX} sur scène</small>
        </div>
      )}

      {stageGuests.length > 0 && (
        <section className="live-stage-grid" aria-label="Intervenants du live">
          {stageGuests.map((guest) => (
            <article className={spotlight === guest ? "spotlight" : ""} key={guest}>
              <button className="stage-focus" onClick={() => setSpotlight((current) => current === guest ? null : guest)} aria-label={`Mettre ${guest} en avant`}>
                <span>{guest.slice(0,2).toUpperCase()}</span>
                <strong>{guest}</strong>
                <small>{spotlight === guest ? "À l’écran" : "Intervenant"}</small>
              </button>
              <button className={`stage-mic ${mutedGuests.includes(guest) ? "muted" : ""}`} onClick={() => toggleGuestMute(guest)} aria-label={mutedGuests.includes(guest) ? `Activer le micro de ${guest}` : `Couper le micro de ${guest}`}>
                {mutedGuests.includes(guest) ? "⌁" : "◉"}
              </button>
            </article>
          ))}
        </section>
      )}

      {captions && <p className="live-captions" aria-live="polite">{caption}</p>}
      <div className="floating-chat" aria-live="polite">
        {visibleComments.slice(-3).map((comment) => (
          <article key={comment.id} className={`floating-comment ${comment.pinned ? "floating-comment-pinned" : ""}`}>
            <b>{comment.name}</b>
            {comment.text}
            <button type="button" onClick={() => sendCommentHeart(comment.id)} aria-label={`Envoyer un cœur au commentaire de ${comment.name}`}>
              ❤ {comment.hearts}
            </button>
          </article>
        ))}
      </div>

      <div className="viewer-controls">
        <button onClick={() => setPlaying((value) => !value)} aria-label={playing ? "Mettre en pause" : "Lire le direct"}>{playing ? "Ⅱ" : "▶"}</button>
        <div>
          <i className="live-progress" />
          <span>Lecture en direct</span>
        </div>
        <button onClick={() => setCaptions((value) => !value)} className={captions ? "active" : ""} aria-label={captions ? "Masquer les sous-titres" : "Afficher les sous-titres"}>CC</button>
        <button onClick={fullscreen} aria-label="Afficher le direct en plein écran">⛶</button>
      </div>

      <div className="viewer-bottom">
        <div>
          <small>{live.host} · {live.location}</small>
          <h2>{live.title}</h2>
        </div>
        <button aria-label="Envoyer un cœur au direct" onClick={sendLiveHeart} className={heart ? "hearted" : ""}>
          ♥
          <small>{formatLiveMoney(heartCount)}</small>
          {heartBurst > 0 && <span className="heart-burst" key={heartBurst}>♥ ♥ ♥</span>}
        </button>
      </div>
    </div>

    <aside className="live-cart">
      <header className="live-seller">
        <span>{hostInitials}</span>
        <div>
          <strong>{live.host}</strong>
          <small>Créateur vérifié · répond en direct</small>
        </div>
        <button onClick={() => setFollowing((value) => !value)} className={following ? "following" : ""}>
          {following ? "✓ Suivi" : "Suivre"}
        </button>
      </header>

      <section className="live-stage-manager">
        <header>
          <div>
            <small>SCÈNE MULTI-INVITÉS</small>
            <strong>{stageCount} / {LIVE_STAGE_MAX} personnes</strong>
          </div>
          <button type="button" onClick={() => toggleStageGuest("Vous")}>
            {stageGuests.includes("Vous") ? "Descendre" : "Monter sur le live"}
          </button>
        </header>
        <div className="stage-preview">
          <span className="stage-host">{hostInitials}</span>
          {stageGuests.map((guest) => <span key={guest}>{guest.slice(0,2).toUpperCase()}</span>)}
          {Array.from({ length: previewSlots }, (_, index) => <i key={index}>＋</i>)}
        </div>
        <button className="stage-manage" type="button" onClick={() => setStageOpen((value) => !value)}>
          {stageOpen ? "Fermer la scène" : "Gérer les intervenants"}
        </button>
        {stageOpen && (
          <div className="stage-candidates">
            {liveStageCandidates.map((guest) => (
              <button type="button" key={guest} onClick={() => toggleStageGuest(guest)} className={stageGuests.includes(guest) ? "on-stage" : ""}>
                <span>{guest.slice(0,2).toUpperCase()}</span>
                <strong>{guest}</strong>
                <small>{stageGuests.includes(guest) ? "Retirer" : "Faire monter"}</small>
              </button>
            ))}
          </div>
        )}
      </section>

      <span>PRODUIT DU DIRECT</span>
      <div className="cart-product" style={{ backgroundImage: `linear-gradient(145deg,rgba(7,20,27,.2),rgba(7,20,27,.76)),url(${live.poster})` }}>◇</div>
      <h3>{live.product}</h3>
      <strong>{live.price} FCFA</strong>
      <p>Stock limité · Livraison disponible</p>
      <div className="quantity">
        <button type="button" onClick={() => setQuantity((value) => Math.max(1, value - 1))}>−</button>
        <b>{quantity}</b>
        <button type="button" onClick={() => setQuantity((value) => Math.min(9, value + 1))}>＋</button>
      </div>
      <button className="buy" onClick={() => { onAdd(live, quantity); onClose(); }}>Ajouter {quantity} au panier</button>
      <button className="offer" onClick={() => notify("Offre préparée — vous pouvez maintenant écrire au vendeur")}>Faire une offre</button>
      <button className="share-live" onClick={() => void shareLive()}>↗ Partager ce direct</button>

      <section className="live-gifts" aria-labelledby="live-gifts-title">
        <header>
          <div>
            <small>CADEAUX GRATUITS · EN ATTENDANT LES PAIEMENTS</small>
            <strong id="live-gifts-title">Soutenir ce direct</strong>
          </div>
          <div className="live-gift-ledger">
            <span><b>🎁</b> Tous les cadeaux sont gratuits</span>
            <span>Porteur : <b>{giftCarrier}</b></span>
          </div>
        </header>

        <div className="live-gift-free-note"><span>✦</span><div><strong>Envoyez sans payer</strong><small>Les cadeaux sont gratuits pendant la phase de lancement. Les paiements seront activés plus tard.</small></div></div>

        <div className="gift-summary">
              <span><b>Acheteur</b> {giftLedger.acheteur.count} cadeau{giftLedger.acheteur.count > 1 ? "x" : ""} · {formatLiveMoney(giftLedger.acheteur.amount)} FCFA</span>
              <span><b>Offreur</b> {giftLedger.offreur.count} cadeau{giftLedger.offreur.count > 1 ? "x" : ""} · {formatLiveMoney(giftLedger.offreur.amount)} FCFA</span>
              <span><b>Mode actuel</b> Cadeaux gratuits · aucun paiement</span>
            </div>

        <div className="live-gift-carriers">
          <div className="gift-carrier-selector">
            <label>
              <input type="radio" value="acheteur" checked={giftCarrier === "acheteur"} onChange={() => setGiftCarrier("acheteur")} />Acheteur
            </label>
            <label>
              <input type="radio" value="offreur" checked={giftCarrier === "offreur"} onChange={() => setGiftCarrier("offreur")} />Offreur
            </label>
          </div>
          <p>Le montant est engagé avant validation.</p>
        </div>

        <div className="gift-grid">
          {liveGifts.map((gift) => (
            <button
              type="button"
              key={gift.id}
              onClick={() => setSelectedGift(gift)}
              disabled={!canBuyFromCarrier(gift)}
              className={gift.price > giftBalance ? "gift-disabled" : ""}
            >
              <i>{gift.icon}</i>
              <strong>{gift.name}</strong>
              <small>{formatLiveMoney(gift.price)} FCFA · {gift.hearts} ❤</small>
            </button>
          ))}
        </div>

        {selectedGift && (
          <div className="gift-confirm" role="status">
                <div>
                  <i>{selectedGift.icon}</i>
                  <span>
                    <strong>{selectedGift.name}</strong>
                    <small>{selectedGift.detail} · {formatLiveMoney(selectedGift.price)} FCFA</small>
                  </span>
                  <button type="button" onClick={() => { setSelectedGift(null); setGiftNote(""); }} aria-label="Annuler le cadeau">×</button>
                </div>
              <small className="gift-carry-note">Porteur : {giftCarrier === "acheteur" ? "Acheteur" : "Offreur"} · Cadeau gratuit{selectedCarrierLedger.count > 0 ? ` · Total ${selectedCarrierLedger.count} cadeau${selectedCarrierLedger.count > 1 ? "x" : ""}` : ""}</small>
              <input value={giftNote} onChange={(event) => setGiftNote(event.target.value.slice(0, 100))} placeholder="Ajouter un mot (facultatif)" aria-label="Message avec le cadeau" />
              <button type="button" onClick={sendGift} disabled={!canAfford}>
                Envoyer gratuitement · {selectedGift.icon}
              </button>
            </div>
          )}
      </section>

      <section className="live-discussion">
        <header>
          <strong>Discussion</strong>
          <span>{showHiddenComments ? orderedComments.length : visibleComments.length} messages {hiddenComments.length ? `(${hiddenComments.length} masqués)` : ""}</span>
        </header>

        {replyTarget && (
          <div className="live-reply-indicator">
            <small>Réponse à <b>{replyTarget.name}</b></small>
            <button type="button" onClick={cancelReply}>Annuler</button>
          </div>
        )}

        {hiddenComments.length > 0 && (
          <label className="live-show-hidden">
            <input type="checkbox" checked={showHiddenComments} onChange={event => setShowHiddenComments(event.target.checked)} />
            Afficher les messages masqués ({hiddenComments.length})
          </label>
        )}

        <div>
          {discussionComments.slice(-4).map((comment) => (
            <article key={comment.id} className={`live-comment ${comment.pinned ? "pinned" : ""} ${comment.hidden ? "hidden" : ""}`}>
              <div>
                <b>{comment.name}</b>
                {comment.text}
              </div>
              <div className="live-comment-actions">
                <button className="live-comment-heart" type="button" onClick={() => sendCommentHeart(comment.id)} aria-label={`Envoyer un cœur à ${comment.name}`}>
                  ❤ {comment.hearts}
                </button>
                <button type="button" onClick={() => replyToComment(comment)} aria-label={`Répondre à ${comment.name}`}>↩</button>
                <button type="button" onClick={() => pinComment(comment.id)} aria-label={comment.pinned ? `Retirer l’épinglage de ${comment.name}` : `Épingler le commentaire de ${comment.name}`}>
                  {comment.pinned ? "📌" : "📍"}
                </button>
                <button type="button" onClick={() => comment.hidden ? restoreComment(comment.id) : hideComment(comment.id)} aria-label={comment.hidden ? `Restaurer le commentaire de ${comment.name}` : `Masquer le commentaire de ${comment.name}`}>
                  {comment.hidden ? "↺" : "✖"}
                </button>
              </div>
            </article>
          ))}
        </div>

        {moderationLog.length > 0 && (
          <details className="live-moderation-log">
            <summary>Journal modération ({moderationLog.length})</summary>
            <ul>
              {moderationLog.map((entry) => (
                <li key={entry.id}>
                  <span>{new Date(entry.at).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}</span>
                  <strong> {entry.by}</strong> {moderationLabel(entry.action)} un message de <b>{entry.commentName}</b>
                  <small>#{entry.commentId}</small>
                </li>
              ))}
            </ul>
          </details>
        )}

        <form onSubmit={sendComment}>
          <input value={message} onChange={(event) => setMessage(event.target.value.slice(0, 240))} placeholder="Écrire dans le direct…" aria-label="Écrire dans le direct" />
          <button disabled={!message.trim()} aria-label="Envoyer le message">➤</button>
        </form>
      </section>

      <small>◆ Cadeaux gratuits pour le moment · les paiements réels seront activés avec un prestataire sécurisé</small>
    </aside>

    {giftReveal && (
      <div className="gift-reveal" role="status" aria-live="assertive">
        <div className="gift-cinematic">
          <small>CADEAU ENVOYÉ PAR {giftReveal.carrier === "acheteur" ? "L’ACHETEUR" : "L’OFFREUR"}</small>
          <div className="gift-box-cinematic" aria-hidden="true">
            <i className="gift-box-lid" />
            <i className="gift-box-base" />
            <b>{giftReveal.gift.icon}</b>
          </div>
          <strong>{giftReveal.gift.name} pour {live.host}</strong>
          <p>Votre cadeau arrive sur le direct…</p>
        </div>
      </div>
    )}
  </div>;
}
function PremiumOfferModalLegacy({ busy,onClose,onSubmit,notify }: { busy:boolean;onClose:()=>void;onSubmit:(e:FormEvent)=>void;notify:(t:string)=>void }) {
  const [offer,setOffer]=useState("");
  const [details,setDetails]=useState("");
  const [media,setMedia]=useState<{file:File;url:string;kind:"image"|"video"}|null>(null);
  const [dragging,setDragging]=useState(false);
  const [submitting,setSubmitting]=useState(false);
  const inputRef=useRef<HTMLInputElement>(null);

  useEffect(()=>()=>{if(media?.url)URL.revokeObjectURL(media.url);},[media?.url]);

  function chooseMedia(file?:File){
    if(!file)return;
    const kind=file.type.startsWith("image/")?"image":file.type.startsWith("video/")?"video":null;
    if(!kind){notify("Choisissez une image ou une vidéo compatible");return;}
    const limit=kind==="image"?20*1024*1024:60*1024*1024;
    if(file.size>limit){notify(kind==="image"?"L’image doit peser moins de 20 Mo":"La vidéo doit peser moins de 60 Mo");return;}
    if(media?.url)URL.revokeObjectURL(media.url);
    setMedia({file,url:URL.createObjectURL(file),kind});
    playMediaAddedSound();
    notify(`${kind==="image"?"Image":"Vidéo"} ajoutée à votre offre`);
  }

  function removeMedia(){
    if(media?.url)URL.revokeObjectURL(media.url);
    setMedia(null);
    if(inputRef.current)inputRef.current.value="";
    notify("Média retiré de l’offre");
  }

  function dropMedia(event:DragEvent<HTMLLabelElement>){
    event.preventDefault();setDragging(false);
    const file=event.dataTransfer.files?.[0];
    if(!file)return;
    chooseMedia(file);
    if(inputRef.current){const transfer=new DataTransfer();transfer.items.add(file);inputRef.current.files=transfer.files;}
  }

  const progress=offer.trim()?details.trim()||media?100:68:32;
  return <div className="modal-layer offer-premium-layer" role="dialog" aria-modal="true" aria-label="Faire une offre">
    <form className={`action-modal offer-premium ${submitting?"is-sending":""}`} onSubmit={event=>{setSubmitting(true);onSubmit(event)}}>
      <div className="offer-aurora" aria-hidden="true"><i/><i/><i/></div>
      <div className="offer-progress" aria-label={`Offre complétée à ${progress}%`}><span style={{width:`${progress}%`}}/></div>
      <button type="button" className="modal-close offer-close" onClick={onClose} aria-label="Fermer">×</button>
      <header className="offer-head"><span className="modal-icon">⇄</span><div><small>WAPI ACTION · OFFRE SÉCURISÉE</small><h2>Faire une offre</h2><p>Proposez un prix ou un échange. Ajoutez une image ou une vidéo pour rendre votre proposition plus claire.</p></div></header>
      <div className="offer-step-row"><span className={offer?"done":"active"}><b>{offer?"✓":"1"}</b> Proposition</span><i/><span className={media||details?"done":""}><b>{media||details?"✓":"2"}</b> Détails</span><i/><span><b>3</b> Envoi</span></div>
      <label className="offer-field"><span>Votre proposition <b>OBLIGATOIRE</b></span><div className="offer-input-wrap"><i>◇</i><input name="offer" value={offer} onChange={event=>setOffer(event.target.value)} required maxLength={160} placeholder="Votre prix ou ce que vous proposez en échange"/></div></label>
      <label className="offer-field"><span>Message <em>FACULTATIF</em></span><textarea name="message" value={details} onChange={event=>setDetails(event.target.value)} maxLength={1000} placeholder="Expliquez les détails, la disponibilité ou les conditions de votre offre…"/><small>{details.length}/1000</small></label>
      <label className={`offer-media-drop ${dragging?"dragging":""} ${media?"has-media":""}`} onDragOver={event=>{event.preventDefault();setDragging(true)}} onDragLeave={()=>setDragging(false)} onDrop={dropMedia}>
        <input ref={inputRef} name="offerMedia" type="file" accept="image/*,video/*" onChange={event=>chooseMedia(event.target.files?.[0])}/>
        {media?<div className="offer-media-preview">{media.kind==="image"?<Image src={media.url} alt="Aperçu du média de l’offre" width={210} height={152} unoptimized/>:<video src={media.url} controls muted playsInline preload="metadata"/>}<div><span>{media.kind==="image"?"IMAGE AJOUTÉE":"VIDÉO AJOUTÉE"}</span><strong>{media.file.name}</strong><small>{(media.file.size/1024/1024).toFixed(1)} Mo · Prêt à envoyer</small></div><button type="button" onClick={event=>{event.preventDefault();removeMedia()}} aria-label="Retirer le média">×</button></div>:<div className="offer-media-empty"><span>＋</span><div><strong>Ajouter une image ou une vidéo</strong><small>Glissez ici ou appuyez pour parcourir · JPG, PNG, WEBP, MP4, MOV</small></div><b>PARCOURIR</b></div>}
      </label>
      <div className="offer-assurance"><span>✓</span><div><strong>Votre offre reste dans cette conversation</strong><small>Vous pourrez encore discuter avant toute validation.</small></div><i>🔊 Son activé</i></div>
      <button className="modal-submit offer-submit" type="submit" disabled={busy||submitting||!offer.trim()}><span>{busy||submitting?"Envoi sécurisé en cours…":"Envoyer l’offre"}</span><b>{busy||submitting?"···":"↗"}</b></button>
    </form>
  </div>;
}

void PremiumOfferModalLegacy;
function ActionModal({ type,busy,onClose,onSubmit,consent,setConsent,setTwinStep,go,notify }: { type:"sell"|"seek"|"live"|"message";busy:boolean;onClose:()=>void;onSubmit:(e:FormEvent)=>void;consent:boolean;setConsent:(v:boolean)=>void;setTwinStep:(v:number)=>void;go:(s:Space)=>void;notify:(t:string)=>void }) {
  const [liveMode,setLiveMode]=useState<"human"|"twin">("human");
  const [fileCount,setFileCount]=useState(0);
  const data={sell:["Publier une opportunité","Décrivez ce que vous vendez, échangez ou proposez. Elle sera visible par les acheteurs concernés, même sans lien d’amitié."],seek:["Publier une recherche","Décrivez clairement votre besoin."],live:["Préparer votre direct","Produits, titre et audience en un seul endroit."],message:["Faire une offre","Proposez un prix ou un échange sécurisé."]}[type];
  return <div className="modal-layer" role="dialog" aria-modal="true" aria-label={data[0]}><form className="action-modal" onSubmit={onSubmit}><button type="button" className="modal-close" onClick={onClose} aria-label="Fermer">×</button><span className="modal-icon">{type==="sell"?"◇":type==="seek"?"⌖":type==="live"?"●":"⇄"}</span><small>WAPI ACTION</small><h2>{data[0]}</h2><p>{data[1]}</p>{type==="sell"&&<><label>Titre de l&apos;annonce<input name="title" required placeholder="Ex. Appareil photo hybride"/></label><div className="modal-row"><label>Mode<select name="mode" defaultValue="sell"><option value="sell">Vendre</option><option value="barter">Troquer</option><option value="both">Vendre ou troquer</option></select></label><label>Prix<input name="price" required placeholder="FCFA ou échange souhaité"/></label></div><div className="modal-row"><label>Catégorie<select name="category"><option>Tech</option><option>Mode</option><option>Maison</option><option>Services</option></select></label><label>Lieu<input name="place" required placeholder="Ex. Poto-Poto"/></label></div><label className={`upload-zone ${fileCount?"selected":""}`}>{fileCount?"✓ Média prêt à être téléversé":"＋ Ajouter une photo ou vidéo"}<input name="media" type="file" accept="image/*,video/*" onChange={e=>{const count=e.target.files?.length||0;setFileCount(count);if(count)notify("Média prêt à être téléversé")}}/></label></>}{type==="seek"&&<><label>Que recherchez-vous ?<input name="title" required placeholder="Ex. Un développeur Flutter disponible"/></label><label>Détails<textarea name="details" required placeholder="Décrivez précisément votre besoin…"/></label><div className="modal-row"><label>Catégorie<select name="category"><option>Produits</option><option>Services</option><option>Situations</option></select></label><label>Zone<input name="area" required placeholder="Quartier, ville ou à distance"/></label></div><div className="modal-row"><label>Budget ou échange<input name="reward" placeholder="Ex. 150 000 FCFA"/></label><label className="urgent-check"><input type="checkbox" name="urgent"/> Besoin urgent</label></div></>}{type==="live"&&<><label>Titre du direct<input name="title" required placeholder="Ex. Découverte de ma nouvelle collection"/></label><label>Produit à présenter<input name="product" placeholder="Sélectionner dans ma boutique"/></label><input type="hidden" name="liveMode" value={liveMode}/><div className="live-mode"><button type="button" className={liveMode==="human"?"active":""} onClick={()=>setLiveMode("human")}>▣ Caméra réelle</button><button type="button" className={liveMode==="twin"?"active":""} onClick={()=>setLiveMode("twin")}>◎ Mon Double IA</button></div><label className="mini-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> J&apos;utilise ma propre image ou un Double dont je contrôle les droits.</label></>}{type==="message"&&<><label>Votre proposition<input name="offer" required placeholder="Votre prix ou ce que vous proposez en échange"/></label><label>Message<textarea name="message" placeholder="Ajoutez les détails de votre offre…"/></label></>}<button className="modal-submit" type="submit" onClick={()=>{if(type==="live"&&!consent)notify("Confirmez les droits sur la vidéo avant de continuer")}} disabled={busy||(type==="live"&&!consent)}>{busy?"Synchronisation…":type==="live"?"Entrer dans le studio":type==="seek"?"Activer ma recherche":type==="message"?"Envoyer l'offre":"Publier l'annonce"} ↗</button>{type==="live"&&<button type="button" className="twin-link" onClick={()=>{onClose();go("twin");setTwinStep(1)}}>Créer d&apos;abord mon Double consentant</button>}</form></div>;
}

function AccountSwitcher({ mode, onToggle }: { mode: "personal" | "business"; onToggle: () => void }) {
  const business = mode === "business";
  return <button className={`account-switcher ${business ? "business" : "personal"}`} onClick={onToggle} aria-label={business ? "Revenir au compte personnel" : "Ouvrir le compte Business"}>
    <span className="account-switcher-icon">{business ? "▥" : "◎"}</span>
    <span><small>ESPACE ACTIF</small><strong>{business ? "Compte Business" : "Compte personnel"}</strong></span>
    <b>↔</b>
  </button>;
}

function ProfilePanel({ name,phone,photoUrl,founder,onSaveProfile,notify,onClose,go,onOpenShop,onOpenOrders,onSignOut }: { name:string;phone:string;photoUrl:string;founder:boolean;onSaveProfile:(name:string,photo:File|null)=>Promise<void>;notify:(message:string)=>void;onClose:()=>void;go:(space:Space)=>void;onOpenShop:()=>void;onOpenOrders:()=>void;onSignOut:()=>void }) {
  const [photoExpanded,setPhotoExpanded]=useState(false);
  const initials=name.split(/\s+/).map(part=>part[0]).join("").slice(0,2).toUpperCase();
return <div className="profile-layer"><button className="profile-dismiss" onClick={onClose} aria-label="Fermer le profil"/><aside className="profile-panel" role="dialog" aria-modal="true" aria-label="Mon profil"><header><button type="button" className={`profile-photo-button ${photoUrl?"has-photo":""}`} onClick={()=>photoUrl&&setPhotoExpanded(true)} disabled={!photoUrl} aria-label={photoUrl?"Agrandir ma photo de profil":"Aucune photo de profil"}><span style={photoUrl?{backgroundImage:`url(${photoUrl})`}:undefined}>{photoUrl?"":initials}</span>{photoUrl&&<small>AGRANDIR</small>}</button><div><small>{founder ? "COMPTE OFFICIEL WAPI" : "COMPTE WAPI"}</small><strong>{name} {founder && <i className="founder-grey-badge" title="Compte certifié">✓</i>}</strong>{founder && <em className="founder-title">Fondateur</em>}<p>{phone} · Vérifié</p></div><button onClick={onClose} aria-label="Fermer">×</button></header><section><button onClick={()=>go("business")}><span>▥</span><div><strong>{founder ? "Dashboard Wapi by BCA" : "Business Suite"}</strong><small>{founder ? "Pilotage de Wapi by BCA et de l’équipe" : "Pages professionnelles et publicités"}</small></div><b>→</b></button><button onClick={onOpenShop}><span>◇</span><div><strong>Ma boutique</strong><small>Gérer mes annonces et mes ventes</small></div><b>→</b></button><button onClick={onOpenOrders}><span>▤</span><div><strong>Mes commandes</strong><small>Suivi, reçus et points de remise</small></div><b>→</b></button><button onClick={()=>go("twin")}><span>◎</span><div><strong>Mon Double</strong><small>Capsule, produits et autorisations</small></div><b>→</b></button><button onClick={()=>go("inbox")}><span>◫</span><div><strong>Mes conversations</strong><small>Messages, offres et commandes</small></div><b>→</b></button></section><ProfileEditor name={name} photoUrl={photoUrl} userKey={phone} onSave={onSaveProfile} notify={notify}/><div className="profile-safety"><span>✓</span><div><strong>{founder ? "Compte fondateur certifié" : "Identité protégée"}</strong><small>{founder ? "Badge gris officiel · Wapi by BCA" : "Un numéro unique pour votre compte"}</small></div></div><button className="profile-signout" onClick={onSignOut}>Se déconnecter</button></aside>{photoExpanded&&<div className="profile-photo-viewer" role="dialog" aria-modal="true" aria-label="Photo de profil agrandie"><button className="profile-photo-dismiss" onClick={()=>setPhotoExpanded(false)} aria-label="Fermer la photo"/><section><button onClick={()=>setPhotoExpanded(false)} aria-label="Fermer">×</button><div style={{backgroundImage:`url(${photoUrl})`}} role="img" aria-label={`Photo de profil de ${name}`}/><strong>{name}</strong><small>Photo synchronisée avec votre compte WAPI</small></section></div>}</div>;
}
