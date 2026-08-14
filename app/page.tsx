"use client";

import Image from "next/image";
import { ConfirmationResult, onAuthStateChanged, RecaptchaVerifier, signInWithPhoneNumber, signOut, updateProfile } from "firebase/auth";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { auth } from "@/lib/firebase";
import { cancelOrder, createGroup, createOrder, publishListing, publishRequest, removeListing, sendConversationMessage, updateListing, watchConversationMessages, watchUserGroups, watchUserOrders, watchWhappyData, type CloudGroup, type CloudMessage, type CloudOrder } from "@/lib/whappy-data";
import { saveWhappyProfile } from "@/lib/whappy-profile";
import { BroadcastStudio, type BroadcastConfig } from "@/app/components/BroadcastStudio";
import { TwinRecorder } from "@/app/components/TwinRecorder";
import { TwinEngineStudio } from "@/app/components/TwinEngineStudio";
import { CallRoom } from "@/app/components/CallRoom";
import { CartPanel, type CartLine, type CheckoutDraft, ProductPanel } from "@/app/components/CommercePanels";
import { SellerDashboard } from "@/app/components/SellerDashboard";
import { OrdersPanel } from "@/app/components/OrdersPanel";
import { ContactsSpace, SuperHub } from "@/app/components/SuperHub";
import { RealTimeInbox } from "@/app/components/RealTimeInbox";
import { BusinessStudio } from "@/app/components/BusinessStudio";
import type { CallSignal } from "@/lib/whappy-calls";
import { watchIncomingCalls } from "@/lib/whappy-calls";
import { recordAdEvent, watchActiveCampaigns, type AdCampaign } from "@/lib/whappy-business";
import type { DirectMember } from "@/lib/whappy-data";

type Space = "orbit" | "live" | "market" | "barter" | "seek" | "inbox" | "contacts" | "services" | "twin" | "business";
type Listing = { id: string | number; title: string; price: string; place: string; seller: string; mark: string; tone: string; category: string; mode: "vente" | "troc"; trust: number; mediaUrl?: string; ownerId?: string; status?: "active" | "reserved" | "sold"; };
type RequestItem = { id: string | number; title: string; details: string; place: string; reward: string; urgent: boolean; category: "Produits" | "Services" | "Situations"; };

const listings: Listing[] = [
  { id: 1, title: "MacBook Air M3 · Comme neuf", price: "750 000 FCFA", place: "Poto-Poto · 1,2 km", seller: "Junior K.", mark: "JK", tone: "lime", category: "Tech", mode: "vente", trust: 98 },
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
  { host: "Mokabi Studio", title: "Nouvelle collection · essayage en direct", viewers: "2,8 k", product: "Veste N'Tela", price: "65 000", tone: "fashion", badge: "LIVE SHOP" },
  { host: "Chef Grâce", title: "Secrets du poulet moambe moderne", viewers: "1,4 k", product: "Masterclass", price: "12 000", tone: "food", badge: "EN DIRECT" },
  { host: "Tech House", title: "Test sans filtre : les meilleurs smartphones", viewers: "963", product: "Galaxy S26", price: "490 000", tone: "tech", badge: "DÉMO LIVE" },
];

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
  { name: "Amina M.", text: "Le troc est accepté pour le canapé ?", time: "Maintenant", mark: "AM", color: "#13d713", unread: 2, mood: "Cherche une belle pièce pour son salon", badge: "ACHETEUSE FIABLE", streak: 12 },
  { name: "Junior K.", text: "Je peux livrer le MacBook cet après-midi.", time: "12:08", mark: "JK", color: "#13d713", unread: 1, mood: "Disponible pour une livraison rapide", badge: "VENDEUR VÉRIFIÉ", streak: 28 },
  { name: "Mokabi Store", text: "Votre commande est prête ✦", time: "11:42", mark: "MS", color: "#13d713", unread: 0, mood: "Collection N'Tela en direct ce soir", badge: "BOUTIQUE PRO", streak: 54 },
  { name: "Design Crew", text: "Nadia : rendez-vous confirmé demain", time: "Hier", mark: "DC", color: "#13d713", unread: 0, mood: "Créateurs disponibles cette semaine", badge: "GROUPE ACTIF", streak: 19 },
];

function Mark({ children, color, small = false }: { children: React.ReactNode; color?: string; small?: boolean }) {
  return <span className={`op-mark ${small ? "small" : ""}`} style={color ? { background: color } : undefined}>{children}</span>;
}

export default function Home() {
  const [authenticated, setAuthenticated] = useState(false);
  const [authStep, setAuthStep] = useState<"phone" | "code" | "profile">("phone");
  const [countryCode, setCountryCode] = useState("+242");
  const [phone, setPhone] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [profileName, setProfileName] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authStatus, setAuthStatus] = useState("");
  const [authError, setAuthError] = useState("");
  const confirmationRef = useRef<ConfirmationResult | null>(null);
  const recaptchaRef = useRef<RecaptchaVerifier | null>(null);
  const recordedAdsRef = useRef(new Set<string>());
  const [space, setSpace] = useState<Space>("inbox");
  const [search, setSearch] = useState("");
  const [marketFilter, setMarketFilter] = useState("Tout");
  const [saved, setSaved] = useState<Record<string, boolean>>({});
  const [toast, setToast] = useState("");
  const [modal, setModal] = useState<"sell" | "seek" | "live" | "twin" | "message" | null>(null);
  const [liveIndex, setLiveIndex] = useState<number | null>(null);
  const [twinStep, setTwinStep] = useState(1);
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
  const [directCompose, setDirectCompose] = useState(0);
  const [activeAds, setActiveAds] = useState<AdCampaign[]>([]);
  const [call, setCall] = useState<{ contact:string; video:boolean; peer?:DirectMember; incoming?:CallSignal } | null>(null);
  const directUser=useMemo<DirectMember|null>(()=>userId?{uid:userId,displayName:auth.currentUser?.displayName||profileName||"Vous",phoneNumber:auth.currentUser?.phoneNumber||""}:null,[userId,profileName]);

  useEffect(() => onAuthStateChanged(auth, (user) => {
    const hasPhone = Boolean(user?.phoneNumber);
    const hasProfile = Boolean(user?.displayName?.trim());
    if (hasPhone && !hasProfile) setAuthStep("profile");
    setAuthenticated(hasPhone && hasProfile);
    setUserId(user?.uid || "");
    setSyncStatus(user?.uid ? "syncing" : "local");
  }), []);

  useEffect(() => () => recaptchaRef.current?.clear(), []);

  useEffect(() => {
    if (!userId) return;
    return watchWhappyData(
      (items) => { setCustomListings(items.map((item) => ({ ...item, tone: "lime" }))); setSyncStatus("synced"); },
      (items) => { setCustomRequests(items); setSyncStatus("synced"); },
      () => setSyncStatus("offline"),
    );
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return watchUserOrders(userId, setOrders, () => setSyncStatus("offline"));
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return watchUserGroups(userId, setGroups, () => setSyncStatus("offline"));
  }, [userId]);

  useEffect(()=>{if(!userId)return;return watchIncomingCalls(userId,(incoming)=>setCall((current)=>current||{contact:incoming.callerName,video:incoming.video,incoming}));},[userId]);
  useEffect(()=>{if(!userId)return;return watchActiveCampaigns(setActiveAds,()=>setSyncStatus("offline"));},[userId]);
  useEffect(()=>{const campaign=activeAds[0];if(!campaign||!userId||recordedAdsRef.current.has(campaign.id))return;recordedAdsRef.current.add(campaign.id);void recordAdEvent(campaign,userId,"impression").catch(()=>{});},[activeAds,userId]);

  const filtered = useMemo(() => [...customListings, ...listings].filter((item) => {
    if (item.status === "sold") return false;
    const matchesText = `${item.title} ${item.category} ${item.place}`.toLowerCase().includes(search.toLowerCase());
    const matchesFilter = marketFilter === "Tout" || item.category === marketFilter || (marketFilter === "Troc" && item.mode === "troc");
    return matchesText && matchesFilter;
  }), [customListings, search, marketFilter]);
  const shopListings = useMemo(() => userId ? customListings.filter((item) => item.ownerId === userId) : customListings, [customListings, userId]);

  function go(next: Space) {
    setSpace(next);
    setSearch("");
  }

  function notify(text: string) {
    setToast(text);
    window.setTimeout(() => setToast(""), 2400);
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

  async function createTrackedGroup(name: string, description: string, members: string[]) {
    try {
      const created = userId ? await createGroup(userId, name, description, members) : { id: `local-group-${Date.now()}`, name, description, mark: name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase(), ownerId: "local", memberIds: ["local"], memberNames: members.map((member) => member.trim()).filter(Boolean), createdAt: { toDate: () => new Date() } };
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
        title: String(form.get("title") || "Mon direct Whappy"),
        product: String(form.get("product") || "Aucun produit épinglé"),
        mode: String(form.get("liveMode") || "human") as BroadcastConfig["mode"],
      });
      setModal(null);
      return;
    }
    if (modal === "sell") {
      const title = String(form.get("title") || "Nouvelle annonce").trim();
      const mode = String(form.get("mode"));
      const seller = auth.currentUser?.displayName || profileName.trim() || "Vous";
      const listing = { title, price: String(form.get("price") || "Prix à discuter"), place: String(form.get("place") || "Brazzaville"), seller, mark: seller.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "VO", category: String(form.get("category") || "Services"), mode: (mode === "sell" ? "vente" : "troc") as Listing["mode"] };
      setPublishBusy(true);
      try {
        if (userId) {
          const media = form.get("media");
          const persisted = await publishListing(userId, listing, media instanceof File && media.size ? media : undefined);
          setCustomListings((current) => current.some((item) => item.id === persisted.id) ? current : [{ ...persisted, tone: "lime" }, ...current]);
        } else setCustomListings((current) => [{ ...listing, id: Date.now(), tone: "lime", trust: 100, ownerId: "local", status: "active" }, ...current]);
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
    } else if (modal === "message") notify("Votre offre a été ajoutée à la conversation");
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
      auth.settings.appVerificationDisabledForTesting =
        process.env.NODE_ENV !== "production" && e164Phone === "+242060000099";
      recaptchaRef.current?.clear();
      auth.languageCode = "fr";
      const verifier = new RecaptchaVerifier(auth, "whappy-recaptcha", {
        size: "invisible",
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
                : code.includes("captcha-check-failed") || code.includes("invalid-app-credential") ? "La vérification anti-robot a échoué. Rechargez la page puis réessayez."
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
      setAuthStep("profile");
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
      });
      setAuthenticated(true);
    } catch {
      setAuthError("Le profil n'a pas pu être créé. Vérifiez votre connexion puis réessayez.");
    } finally {
      setAuthBusy(false);
    }
  }

  const titles: Record<Space, [string, string]> = {
    orbit: ["Accueil", "Publicités, nouveautés et opportunités du moment"],
    live: ["Whappy Live", "Regardez, échangez et achetez en temps réel"],
    market: ["Whappy Marketplace", "Tout le monde peut vendre, acheter ou négocier"],
    barter: ["Troc intelligent", "Échangez de la valeur, sans limite"],
    seek: ["Je cherche", "Publiez un besoin, la communauté répond"],
    inbox: ["Connexions", "Vos conversations, commandes et offres"],
    contacts: ["Contacts", "Personnes, groupes et professionnels autour de vous"],
    services: ["Services", "Payez, achetez, trouvez et gérez votre quotidien"],
    twin: ["Studio Double", "Votre vendeur numérique, créé avec votre accord"],
    business: ["Business Suite", "Pages professionnelles, publicité et croissance"],
  };
  const searchPlaceholders: Record<Space,string> = {
    inbox: "Rechercher une conversation…",
    contacts: "Rechercher une personne, un groupe ou un professionnel…",
    orbit: "Rechercher dans les Moments…",
    services: "Rechercher un service Whappy…",
    live: "Rechercher un direct…",
    market: "Rechercher un produit ou une boutique…",
    barter: "Rechercher un échange…",
    seek: "Rechercher une solution ou un besoin…",
    twin: "Rechercher dans le Studio Double…",
    business: "Rechercher une page ou une campagne…",
  };

  if (!authenticated) return <PhoneAccess step={authStep} countryCode={countryCode} setCountryCode={setCountryCode} phone={phone} setPhone={setPhone} code={verificationCode} setCode={setVerificationCode} profileName={profileName} setProfileName={setProfileName} busy={authBusy} status={authStatus} error={authError} requestSms={requestSms} verifySms={verifySms} finishProfile={finishProfile} back={()=>{setAuthError("");setAuthStatus("");setAuthStep("phone")}} preview={()=>setAuthenticated(true)} />;

  return <main className="nova-shell white-green">
    <aside className="nova-rail">
      <button className="nova-logo" onClick={() => go("inbox")} aria-label="Messages Whappy"><Image src="/whappy-logo.svg" alt="Icône Whappy" width={50} height={50} priority /></button>
      <nav aria-label="Espaces Whappy">
        <Rail active={space === "inbox"} icon="◫" label="Messages" count={3} onClick={() => go("inbox")} />
        <Rail active={space === "contacts"} icon="◎" label="Contacts" onClick={() => go("contacts")} />
        <Rail active={space === "orbit"} icon="▦" label="Moments" onClick={() => go("orbit")} />
        <Rail active={space === "services"} icon="⌗" label="Services" onClick={() => go("services")} />
        <Rail active={space === "business"} icon="▥" label="Business" onClick={() => go("business")} />
      </nav>
      <div className="rail-tools">
        <button className={space === "twin" ? "active" : ""} onClick={() => go("twin")}><span>◎</span><small>Mon Double</small></button>
        <button className="me" onClick={() => setProfileOpen(true)} aria-label="Ouvrir mon profil">CB<i /></button>
      </div>
    </aside>

    <section className="nova-stage">
      <header className="nova-topbar">
        <div className="topbar-identity">
          <button className="mobile-logo" onClick={() => go("inbox")} aria-label="Messages Whappy">
            <Image src="/whappy-logo.svg" alt="Logo officiel Whappy" width={40} height={40} priority />
          </button>
          <div><span className="kicker">WHAPPY / {space.toUpperCase()}</span><h1>{titles[space][0]}</h1><p>{titles[space][1]}</p></div>
        </div>
        <label className="nova-search"><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={searchPlaceholders[space]} />{search && <button onClick={() => setSearch("")}>×</button>}</label>
        <div className="top-actions"><span className={`sync-badge ${syncStatus}`} title={syncStatus==="synced"?"Données synchronisées":syncStatus==="syncing"?"Synchronisation en cours":syncStatus==="offline"?"Synchronisation indisponible":"Démonstration locale"}><i/>{syncStatus==="synced"?"Cloud":syncStatus==="syncing"?"Sync…":syncStatus==="offline"?"Hors ligne":"Local"}</span>{space === "inbox" ? <><button onClick={() => setOrdersOpen(true)}><span>▤</span><small>Commandes</small>{orders.length>0&&<b className="action-count">{orders.length}</b>}</button><button className="sell" onClick={() => setDirectCompose((value)=>value+1)}><span>＋</span><small>Nouveau</small></button></> : <><button onClick={() => setOrdersOpen(true)}><span>▤</span><small>Commandes</small>{orders.length>0&&<b className="action-count">{orders.length}</b>}</button><button className="cart-action" onClick={()=>setCartOpen(true)}><span>◇</span><small>Panier</small>{cart.length>0&&<b>{cart.reduce((sum,line)=>sum+line.quantity,0)}</b>}</button><button className="sell" onClick={() => setModal("sell")}><span>＋</span><small>Vendre</small></button></>}</div>
      </header>

      {space === "orbit" && <Orbit go={go} setModal={setModal} setLiveIndex={setLiveIndex} notify={notify} saved={saved} setSaved={setSaved} ad={activeAds[0]} onAdClick={(campaign)=>{void recordAdEvent(campaign,userId,"click").catch(()=>{});notify(`Page ${campaign.pageName} ouverte`);}} />}
      {space === "live" && <LiveSpace setModal={setModal} setLiveIndex={setLiveIndex} />}
      {space === "market" && <MarketSpace search={search} filter={marketFilter} setFilter={setMarketFilter} items={filtered} shopCount={shopListings.length} saved={saved} setSaved={setSaved} notify={notify} setModal={setModal} onOpenShop={() => setShopOpen(true)} onOpen={setSelectedProduct} />}
      {space === "barter" && <BarterSpace notify={notify} setModal={setModal} />}
      {space === "seek" && <SeekSpace setModal={setModal} notify={notify} items={[...customRequests, ...requests]} />}
      {space === "inbox" && (userId ? <RealTimeInbox key={directCompose} embedded composeToken={directCompose} search={search} user={directUser} notify={notify} onCall={(peer,video)=>setCall({contact:peer.displayName,video,peer})}/> : <InboxSpace search={search} userId={userId} setModal={setModal} notify={notify} onCall={(contact,video)=>setCall({contact,video})} />)}
      {space === "contacts" && <ContactsSpace search={search} cloud={Boolean(userId)} userId={userId} userName={auth.currentUser?.displayName||profileName||"Vous"} cloudGroups={groups} onCreateGroup={createTrackedGroup} notify={notify} onCall={(contact)=>setCall({contact,video:false})} onMessage={(contact)=>{go("inbox");notify(`Conversation avec ${contact} ouverte`)}} />}
      {space === "services" && <SuperHub go={go} orderCount={orders.length} onOrders={()=>setOrdersOpen(true)} notify={notify} />}
      {space === "twin" && userId && <TwinEngineStudio userId={userId} userName={auth.currentUser?.displayName||profileName||"Vous"} consent={consent} setConsent={setConsent} notify={notify}/>}
      {space === "twin" && !userId && <TwinSpace step={twinStep} setStep={setTwinStep} consent={consent} setConsent={setConsent} notify={notify} />}
      {space === "business" && userId && <BusinessStudio userId={userId} userName={auth.currentUser?.displayName||profileName||"Vous"} search={search} notify={notify}/>}
    </section>

    {liveIndex !== null && <LiveViewer live={lives[liveIndex]} onClose={() => setLiveIndex(null)} notify={notify} onAdd={(live,quantity)=>addToCart({id:`live-${live.product}`,title:live.product,price:`${live.price} FCFA`,place:"Direct Whappy",seller:live.host,mark:live.host.split(" ").map(part=>part[0]).join("").slice(0,2),tone:live.tone,category:"Direct",mode:"vente",trust:98},quantity)} />}
    {broadcast && <BroadcastStudio config={broadcast} twinAuthorized={consent} onClose={() => setBroadcast(null)} onOpenTwin={() => { setBroadcast(null); go("twin"); setTwinStep(1); }} notify={notify} />}
    {modal && <ActionModal type={modal} busy={publishBusy} onClose={() => setModal(null)} onSubmit={submitModal} consent={consent} setConsent={setConsent} setTwinStep={setTwinStep} go={go} notify={notify} />}
    {profileOpen && <ProfilePanel name={auth.currentUser?.displayName || profileName || "Cyril Bokilo"} phone={auth.currentUser?.phoneNumber || `${countryCode} ${phone || "06 000 00 00"}`} onClose={() => setProfileOpen(false)} go={(destination) => { setProfileOpen(false); go(destination); }} onOpenShop={() => { setProfileOpen(false); setShopOpen(true); }} onOpenOrders={() => { setProfileOpen(false); setOrdersOpen(true); }} onSignOut={async () => { if (auth.currentUser) await signOut(auth); setProfileOpen(false); setAuthenticated(false); }} />}
    {shopOpen && <SellerDashboard items={shopListings} cloud={Boolean(userId)} onClose={() => setShopOpen(false)} onCreate={() => { setShopOpen(false); setModal("sell"); }} onUpdate={manageListing} onDelete={deleteShopListing} notify={notify} />}
    {selectedProduct&&<ProductPanel item={selectedProduct} saved={!!saved[String(selectedProduct.id)]} onSave={()=>setSaved(current=>({...current,[selectedProduct.id]:!current[String(selectedProduct.id)]}))} onClose={()=>setSelectedProduct(null)} onContact={()=>{const seller=selectedProduct.seller;setSelectedProduct(null);go("inbox");notify(`Conversation avec ${seller} ouverte`);}} onAdd={(quantity)=>addToCart(selectedProduct,quantity)}/>}
    {cartOpen&&<CartPanel lines={cart} onClose={()=>setCartOpen(false)} onQuantity={(id,quantity)=>setCart(current=>current.map(line=>line.item.id===id?{...line,quantity}:line))} onRemove={(id)=>setCart(current=>current.filter(line=>line.item.id!==id))} onCheckout={checkout} notify={notify}/>}
    {ordersOpen&&<OrdersPanel orders={orders} cloud={Boolean(userId)} onClose={()=>setOrdersOpen(false)} onExplore={()=>{setOrdersOpen(false);go("market")}} onContact={(seller)=>{setOrdersOpen(false);go("inbox");notify(`Conversation avec ${seller} ouverte`)}} onCancel={cancelTrackedOrder} notify={notify}/>}
    {call&&<CallRoom contact={call.contact} video={call.video} currentUser={directUser} peer={call.peer} incoming={call.incoming} onClose={()=>setCall(null)}/>}
    {toast && <div className="nova-toast">✦ {toast}</div>}
  </main>;
}

function PhoneAccess({ step,countryCode,setCountryCode,phone,setPhone,code,setCode,profileName,setProfileName,busy,status,error,requestSms,verifySms,finishProfile,back,preview }: { step:"phone"|"code"|"profile";countryCode:string;setCountryCode:(value:string)=>void;phone:string;setPhone:(value:string)=>void;code:string;setCode:(value:string)=>void;profileName:string;setProfileName:(value:string)=>void;busy:boolean;status:string;error:string;requestSms:(event:FormEvent)=>void;verifySms:(event:FormEvent)=>void;finishProfile:(event:FormEvent)=>void;back:()=>void;preview:()=>void }) {
  const fullNumber=`${countryCode} ${phone || "—"}`;
  return <main className="phone-access"><section className="access-brand"><div className="access-logo"><Image src="/whappy-logo.svg" alt="Logo Whappy" width={70} height={70} priority/><strong>WHAPPY</strong></div><div className="access-promise"><span>UN NUMÉRO. UN COMPTE.</span><h1>Votre monde,<br/>au bout du <em>numéro.</em></h1><p>Vos messages, vos appels, vos directs et votre boutique vous suivent sur tous vos appareils.</p></div><div className="access-flow"><span className={step==="phone"?"active":"done"}><b>{step==="phone"?"1":"✓"}</b> Numéro</span><i/><span className={step==="code"?"active":step==="profile"?"done":""}><b>{step==="profile"?"✓":"2"}</b> Code SMS</span><i/><span className={step==="profile"?"active":""}><b>3</b> Profil</span></div><small className="access-secure">◆ Chiffrement · Identité téléphonique · Aucun mot de passe</small></section><section className="access-panel"><div className="access-card">{step!=="phone"&&<button className="access-back" onClick={back} aria-label="Modifier le numéro">←</button>}<span className="access-step">ÉTAPE {step==="phone"?"1 SUR 3":step==="code"?"2 SUR 3":"3 SUR 3"}</span>{step==="phone"&&<form onSubmit={requestSms}><h2>Entrez votre numéro</h2><p>Whappy utilise votre numéro pour créer et retrouver votre compte. Un même numéro ne peut appartenir qu&apos;à un seul compte.</p><label>Pays<select value={countryCode} onChange={event=>setCountryCode(event.target.value)}><option value="+242">🇨🇬 Congo (+242)</option><option value="+243">🇨🇩 RD Congo (+243)</option><option value="+33">🇫🇷 France (+33)</option><option value="+225">🇨🇮 Côte d&apos;Ivoire (+225)</option><option value="+221">🇸🇳 Sénégal (+221)</option><option value="+237">🇨🇲 Cameroun (+237)</option></select></label><label>Numéro de téléphone<div className="phone-field"><span>{countryCode}</span><input inputMode="tel" autoComplete="tel-national" value={phone} onChange={event=>setPhone(event.target.value)} placeholder="06 123 45 67"/></div></label><div id="whappy-recaptcha" className="recaptcha-invisible"/>{status&&<p className="sms-status">{status}</p>}<button className="access-primary" disabled={busy}>{busy?"Envoi du SMS…":"Continuer par SMS →"}</button><div className="one-account"><span>1</span><div><strong>Un numéro = un compte Whappy</strong><small>Cette règle protège votre identité, vos contacts et vos transactions.</small></div></div></form>}{step==="code"&&<form onSubmit={verifySms}><span className="access-code-icon">✦</span><h2>Vérifiez votre numéro</h2><p>Nous avons envoyé un code à 6 chiffres au <strong>{fullNumber}</strong>.</p><label>Code reçu par SMS<input className="otp-field" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={event=>setCode(event.target.value.replace(/\D/g,""))} placeholder="— — — — — —"/></label><button className="access-primary" disabled={busy}>{busy?"Vérification…":"Vérifier le code →"}</button><button className="access-link" type="button" onClick={()=>setCode("")}>Renvoyer le code dans 00:42</button></form>}{step==="profile"&&<form onSubmit={finishProfile}><span className="profile-create">＋</span><h2>Créez votre profil</h2><p>Ajoutez le nom que vos contacts verront. Vous pourrez ajouter votre photo ensuite.</p><label>Votre nom<input autoComplete="name" value={profileName} onChange={event=>setProfileName(event.target.value)} placeholder="Ex. Cyril Bokilo"/></label><button className="access-primary" disabled={busy}>{busy?"Création…":"Entrer dans Whappy →"}</button></form>}{error&&<p className="access-error">! {error}</p>}<a className="access-apk" href="/WHAPPY-Android-1.2.0-native.apk" download>⬇ Télécharger WHAPPY Android native 1.2.0</a>{process.env.NODE_ENV!=="production"&&<button className="access-demo" type="button" onClick={preview}>Voir la démonstration locale</button>}<small className="access-legal">En continuant, vous acceptez les conditions Whappy et confirmez être propriétaire de ce numéro.</small></div></section></main>;
}

function Rail({ active, icon, label, count, live, onClick }: { active: boolean; icon: string; label: string; count?: number; live?: boolean; onClick: () => void }) {
  return <button className={active ? "active" : ""} onClick={onClick}><span>{icon}</span><small>{label}</small>{count ? <b>{count}</b> : null}{live ? <i /> : null}</button>;
}

function Orbit({ go, setModal, setLiveIndex, notify, saved, setSaved, ad, onAdClick }: { go: (space: Space) => void; setModal: (modal: "sell" | "seek" | "live" | "twin") => void; setLiveIndex: (index: number) => void; notify: (text: string) => void; saved: Record<number, boolean>; setSaved: React.Dispatch<React.SetStateAction<Record<number, boolean>>>; ad?: AdCampaign; onAdClick: (campaign: AdCampaign) => void }) {
  const [liked,setLiked]=useState(false);
  return <div className="orbit-scroll moments-home"><div className="moments-layout"><main className="moments-feed"><section className="moments-intro"><div><span>AUJOURD&apos;HUI · BRAZZAVILLE</span><h2>Ce qui se passe<br/><em>maintenant.</em></h2><p>Des personnes, des idées, des directs et des opportunités — réunis dans un fil vivant.</p></div><button onClick={()=>setModal("live")}>● Passer en direct</button></section><section className="story-line"><button className="add-story"><span>＋</span><strong>Votre moment</strong><small>Partager</small></button>{messages.slice(0,3).map((person,index)=><button key={person.name} onClick={()=>notify(`Moment de ${person.name} ouvert`)}><span>{person.mark}<i>{index===2?"●":""}</i></span><strong>{person.name.split(" ")[0]}</strong><small>{index===2?"EN DIRECT":"Nouveau"}</small></button>)}<button onClick={()=>go("live")}><span className="story-more">→</span><strong>Explorer</strong><small>Tout voir</small></button></section><article className="moment-card sponsored-moment"><header><span className="moment-avatar">{ad?ad.pageName.split(/\s+/).map((word)=>word[0]).join("").slice(0,2):"MS"}<i/></span><div><strong>{ad?.pageName||"Mokabi Studio"} <b>✓</b></strong><small>Publication sponsorisée · WHAPPY ADS</small></div><button>•••</button></header><div className="moment-visual"><span>{ad?"WHAPPY ADS":"WHAPPY LIVE"}</span><div><small>{ad?`${ad.audience} · ${ad.city}`:"COLLECTION N'TELA 2026"}</small><h3>{ad?.title||<>Porter son histoire.<br/>Vivre son style.</>}</h3><button onClick={()=>ad?onAdClick(ad):setLiveIndex(0)}>{ad?.cta||"Rejoindre le direct ●"}</button></div><b>{ad?"Sponsorisé":"2,8 k regardent"}</b></div><p>{ad?.creative||"La nouvelle collection est là. Découvrez chaque pièce en direct, posez vos questions et commandez sans quitter la vidéo."}</p><footer>{ad?<><button className="ad-cta" onClick={()=>onAdClick(ad)}>{ad.cta} →</button><span>Publicité · {ad.pageName}</span></>:<><button className={liked?"liked":""} onClick={()=>setLiked(v=>!v)}>{liked?"♥":"♡"} {liked?"1 205":"1 204"}</button><button onClick={()=>notify("Commentaires ouverts")}>◫ 86 commentaires</button><button onClick={()=>notify("Publication partagée")}>↗ Partager</button></>}</footer></article><article className="moment-card community-moment"><header><span className="moment-avatar">AM<i/></span><div><strong>Amina M. <b>✓</b></strong><small>Poto-Poto · il y a 24 min</small></div><button>•••</button></header><p className="moment-copy">Je transforme mon salon et je cherche une table artisanale locale. Budget raisonnable ou échange possible. Vous connaissez quelqu&apos;un ?</p><div className="moment-request"><span>⌖</span><div><small>RECHERCHE ACTIVE</small><strong>Table artisanale · Brazzaville</strong></div><button onClick={()=>notify("Réponse envoyée à Amina")}>Je peux aider</button></div><footer><button onClick={()=>notify("Vous aimez cette publication")}>♡ 48</button><button onClick={()=>notify("Commentaires ouverts")}>◫ 12 commentaires</button><button onClick={()=>notify("Publication partagée")}>↗ Partager</button></footer></article></main><aside className="moments-side"><section className="quick-publish"><span>CB</span><div><strong>Bonjour Cyril</strong><small>Quoi de neuf aujourd&apos;hui ?</small></div><button onClick={()=>notify("Créateur de publication ouvert")}>＋ Publier</button></section><section className="side-card"><header><div><small>EN DIRECT</small><strong>Ça bouge maintenant</strong></div><button onClick={()=>go("live")}>Tout voir</button></header>{lives.slice(0,2).map((live,index)=><button className="side-live" key={live.host} onClick={()=>setLiveIndex(index)}><span>{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}<i/></span><div><strong>{live.title}</strong><small>{live.viewers} spectateurs</small></div><b>→</b></button>)}</section><section className="side-card"><header><div><small>WHAPPY MARKET</small><strong>Pour vous</strong></div><button onClick={()=>go("market")}>Explorer</button></header>{listings.slice(0,2).map(item=><button className="side-product" key={item.id} onClick={()=>go("market")}><span>{item.mark}</span><div><strong>{item.title}</strong><small>{item.price}</small></div><button className={saved[item.id]?"saved":""} onClick={e=>{e.stopPropagation();setSaved(c=>({...c,[item.id]:!c[item.id]}))}}>♡</button></button>)}</section><button className="home-create-ad" onClick={()=>go("business")}>✦ Promouvoir une publication</button></aside></div></div>;
}

function LiveSpace({ setModal, setLiveIndex }: { setModal: (type: "live") => void; setLiveIndex: (index: number) => void }) {
  const [mode,setMode]=useState<"human"|"twin"|"relay">("relay");
  return <div className="space-scroll live-space">
    <section className="live-command twin-live-command"><div className="live-command-copy"><span className="signal"><i/> WHAPPY LIVE SHIFT™</span><h2>Vous commencez.<br/><em>Votre Double continue.</em></h2><p>Lancez un direct avec votre caméra, avec votre Double vidéo autorisé, ou passez de l&apos;un à l&apos;autre sans couper le live.</p><div className="live-mode-switch"><button className={mode==="human"?"active":""} onClick={()=>setMode("human")}><span>▣</span><strong>Moi, en direct</strong><small>Caméra et voix réelles</small></button><button className={mode==="twin"?"active":""} onClick={()=>setMode("twin")}><span>◎</span><strong>Mon Double</strong><small>Vidéo IA autorisée</small></button><button className={mode==="relay"?"active":""} onClick={()=>setMode("relay")}><span>⇄</span><strong>Passage de relais</strong><small>Humain + Double</small></button></div><button onClick={() => setModal("live")}>● Entrer dans le studio</button><small className="live-safety">✓ Votre image uniquement · Double signalé comme IA · Arrêt immédiat à tout moment</small></div><div className={`studio-preview shift-preview ${mode}`}><span className="preview-live">● LIVE · 00:12:48</span><div className="shift-status"><span>{mode==="human"?"CAMÉRA RÉELLE":mode==="twin"?"DOUBLE IA ACTIF":"PASSAGE DE RELAIS"}</span><b>{mode==="relay"?"Transition dans 00:08":"Contrôle créateur actif"}</b></div><div className="shift-stage"><div className={`shift-person human-feed ${mode==="human"||mode==="relay"?"on":""}`}><span>CB</span><small>VOUS · LIVE</small></div><div className="shift-link">{mode==="relay"?"⇄":"＋"}</div><div className={`shift-person twin-feed ${mode==="twin"||mode==="relay"?"on":""}`}><span>CB</span><small>DOUBLE · IA</small><b>IA</b></div></div><div className="preview-comments"><span>Ça existe en bleu ?</span><span>Le Double parle aussi lingala ?</span></div><div className="preview-product"><i>◇</i><span><small>PRODUIT ÉPINGLÉ</small><strong>Montre Kongo One</strong><b>42 000 FCFA</b></span><button>Acheter</button></div></div></section>
    <section className="space-content"><div className="shift-explainer"><div><span>01</span><strong>Vous créez la confiance</strong><p>Présentez, racontez et répondez vous-même.</p></div><i>→</i><div><span>02</span><strong>Le Double prend le relais</strong><p>Il reprend seulement le script et les produits approuvés.</p></div><i>→</i><div><span>03</span><strong>Vous revenez instantanément</strong><p>Un geste suffit pour reprendre la caméra en direct.</p></div></div><SectionTitle overline="EN CE MOMENT" title="Des directs augmentés, pas automatisés" action="Créer mon direct" onClick={() => setModal("live")}/><div className="big-live-grid">{lives.map((live,index)=><button key={live.host} className={`big-live ${live.tone}`} onClick={() => setLiveIndex(index)}><span><i/> {live.badge}</span><div className="host-face">{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}</div><div><small>{live.host} · {live.viewers} spectateurs</small><h3>{live.title}</h3><p>Produit épinglé : {live.product}</p><b>{live.price} FCFA</b></div></button>)}</div><div className="live-features"><div><span>⇄</span><strong>Live Shift™</strong><p>Passez de votre caméra au Double sans interrompre l&apos;audience.</p></div><div><span>◌</span><strong>Questions filtrées</strong><p>Le Double répond uniquement dans les limites que vous avez approuvées.</p></div><div><span>⏱</span><strong>Vente continue</strong><p>Quittez la scène ; votre boutique reste présentée sous votre contrôle.</p></div></div></section>
  </div>;
}

function MarketSpace({ search, filter, setFilter, items, shopCount, saved, setSaved, notify, setModal, onOpenShop, onOpen }: { search:string; filter:string; setFilter:(v:string)=>void; items:Listing[]; shopCount:number; saved:Record<string,boolean>; setSaved:React.Dispatch<React.SetStateAction<Record<string, boolean>>>; notify:(text:string)=>void; setModal:(type:"sell")=>void; onOpenShop:()=>void; onOpen:(item:Listing)=>void }) {
  const filters=["Tout","Tech","Mode","Maison","Services","Troc"];
  const [sort,setSort]=useState<"near"|"trust">("near");
  const [nearby,setNearby]=useState(false);
  const sortedItems=[...items].sort((a,b)=>sort==="trust"?b.trust-a.trust:String(a.id).localeCompare(String(b.id)));
  return <div className="space-scroll market-space"><section className="market-banner marketplace-banner"><div><span>WHAPPY MARKETPLACE · OUVERT À TOUS</span><h2>Tout le monde peut<br/>ouvrir sa boutique.</h2><p>Vendez un objet, un service ou une création. Discutez avec l&apos;acheteur et préparez un paiement protégé.</p><div className="market-hero-actions"><button onClick={()=>setModal("sell")}>＋ Commencer à vendre</button><button onClick={onOpenShop}>Ma boutique ↗</button></div></div><div className="seller-console"><small>VOTRE BOUTIQUE WHAPPY</small><strong>{shopCount}</strong><span>{shopCount>1?"annonces publiées":"annonce publiée"}</span><div><b>{shopCount*37}</b><small>Vues</small><b>{shopCount*4}</b><small>Messages</small></div><button onClick={shopCount?onOpenShop:()=>setModal("sell")}>{shopCount?"Gérer ma boutique":"Publier mon premier produit"}</button></div></section><section className="space-content"><div className="payment-ready"><div><span>◆</span><div><small>PAIEMENTS À CONNECTER</small><strong>Préparez votre moyen d&apos;encaissement</strong></div></div><div className="payment-methods"><span>Mobile Money</span><span>Carte bancaire</span><span>Whappy Pay</span><span>Paiement à la livraison</span></div><button onClick={()=>notify("Le paiement à la livraison est sélectionné")}>Choisir ↗</button></div><div className="market-toolbar"><div>{filters.map(x=><button className={filter===x?"active":""} key={x} onClick={()=>setFilter(x)}>{x}</button>)}</div><button className={nearby?"active":""} onClick={()=>{setNearby(v=>!v);notify(nearby?"Filtre de proximité retiré":"Produits proches affichés")}}>⌖ {nearby?"À proximité":"Autour de moi"}</button><button onClick={()=>setSort(v=>v==="near"?"trust":"near")}>≡ {sort==="near"?"Trier par confiance":"Trier par proximité"}</button></div><div className="results-line"><span>{sortedItems.length} produits et services {search && `pour « ${search} »`}</span><small>{nearby?"Dans un rayon de 5 km":"Vendeurs particuliers et professionnels"}</small></div><div className="listing-grid market-listings">{sortedItems.map(item=><ListingCard key={item.id} item={item} saved={!!saved[String(item.id)]} onSave={()=>setSaved(c=>({...c,[item.id]:!c[String(item.id)]}))} onOpen={()=>onOpen(item)}/>)}</div>{sortedItems.length===0&&<div className="market-empty"><span>⌕</span><h3>Aucun résultat</h3><p>Essayez une autre catégorie ou publiez votre propre annonce.</p><button onClick={()=>setModal("sell")}>＋ Publier une annonce</button></div>}<button className="market-sell-fab" onClick={()=>setModal("sell")}>＋ Vendre sur Whappy</button></section></div>;
}

function BarterSpace({ notify, setModal }: { notify:(text:string)=>void; setModal:(type:"sell")=>void }) {
  const [mine,setMine]=useState("Mon appareil photo"); const [want,setWant]=useState("Un ordinateur portable"); const [score,setScore]=useState<number|null>(null);
  return <div className="space-scroll barter-space"><section className="barter-hero"><span className="signal"><i/> WHAPPY MATCH</span><h2>La valeur ne se mesure<br/>pas toujours en argent.</h2><p>Décrivez ce que vous avez et ce que vous voulez. Notre moteur trouve les échanges possibles, même à plusieurs personnes.</p><div className="barter-engine"><label><small>JE PROPOSE</small><input value={mine} onChange={e=>setMine(e.target.value)}/><span>＋ Photo</span></label><button className="swap">⇄</button><label><small>JE RECHERCHE</small><input value={want} onChange={e=>setWant(e.target.value)}/><span>⌖ Zone : 25 km</span></label><button className="match" onClick={()=>setScore(94)}>Trouver un échange ✦</button></div>{score&&<div className="match-result"><span>{score}%</span><div><small>MEILLEURE CORRESPONDANCE</small><strong>Patrick propose un MacBook Pro</strong><p>Il cherche un appareil photo hybride + complément.</p></div><button onClick={()=>notify("Proposition de troc envoyée")}>Proposer le troc ↗</button></div>}</section><section className="space-content"><SectionTitle overline="ÉCHANGES OUVERTS" title="Le troc bouge près de vous" action="Publier un objet" onClick={()=>setModal("sell")}/><div className="barter-cards"><div><span className="barter-art violet">⌁</span><small>PROPOSE</small><strong>Service de photographie</strong><i>contre</i><small>RECHERCHE</small><strong>Création d&apos;un site vitrine</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div><span className="barter-art amber">◆</span><small>PROPOSE</small><strong>Canapé en excellent état</strong><i>contre</i><small>RECHERCHE</small><strong>Table à manger + 4 chaises</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div className="chain-card"><span>⇄</span><h3>Troc en chaîne</h3><p>Vous avez A, vous voulez B. Une troisième personne veut A et possède C. Whappy relie les trois.</p><b>18 chaînes possibles aujourd&apos;hui</b></div></div></section></div>;
}

function SeekSpace({ setModal, notify, items }: { setModal:(type:"seek")=>void; notify:(text:string)=>void; items:RequestItem[] }) {
  const [filter,setFilter]=useState("Tous"); const [answered,setAnswered]=useState<Record<string,boolean>>({});
  const visible=items.filter(item=>filter==="Tous"||(filter==="Urgent"&&item.urgent)||item.category===filter);
  return <div className="space-scroll seek-space"><section className="seek-hero"><div><span className="signal"><i/> INTELLIGENCE COLLECTIVE</span><h2>Demandez.<br/><em>Quelqu&apos;un sait.</em></h2><p>Un produit introuvable, une compétence urgente, une situation à résoudre ? Publiez votre besoin avec le lieu, le délai et votre budget.</p><button onClick={()=>setModal("seek")}>⌖ Publier ce que je cherche</button></div><div className="seek-cloud"><span className="q1">Un plombier maintenant</span><span className="q2">Appartement à louer</span><span className="q3">Pièce Toyota 2017</span><span className="q4">Graphiste disponible</span><span className="q5">Bon restaurant calme</span><b>⌖</b></div></section><section className="space-content"><div className="seek-tabs">{["Tous","Urgent","Produits","Services","Situations"].map(x=><button className={filter===x?"active":""} onClick={()=>setFilter(x)} key={x}>{x}</button>)}</div><div className="request-grid">{visible.map((item,index)=><article key={item.id}><header><span className={item.urgent?"urgent":""}>{item.urgent?"URGENT":item.category.toUpperCase()}</span><small>{item.id>4?"À l'instant":`Il y a ${index*7+3} min`}</small></header><h3>{item.title}</h3><p>{item.details}</p><div><span>⌖ {item.place}</span><b>{item.reward}</b></div><footer><span>{index*4+7} personnes ont vu</span><button className={answered[item.id]?"answered":""} disabled={answered[item.id]} onClick={()=>{setAnswered(current=>({...current,[item.id]:true}));notify("Votre réponse a été envoyée")}}>{answered[item.id]?"✓ Réponse envoyée":"Je peux aider ↗"}</button></footer></article>)}</div>{visible.length===0&&<div className="market-empty"><span>⌖</span><h3>Aucune recherche ici</h3><p>Soyez la première personne à publier un besoin.</p><button onClick={()=>setModal("seek")}>Publier une recherche</button></div>}</section></div>;
}

function InboxSpace({ search, userId, setModal, notify, onCall }: { search:string; userId:string; setModal:(type:"message")=>void; notify:(text:string)=>void; onCall:(contact:string,video:boolean)=>void }) {
  const [selected,setSelected]=useState(0); const [text,setText]=useState(""); const [mobileChat,setMobileChat]=useState(false); const [filter,setFilter]=useState("Tout"); const [sent,setSent]=useState<Record<number,CloudMessage[]>>({}); const [sending,setSending]=useState(false);
  const notifyRef=useRef(notify);
  useEffect(()=>{notifyRef.current=notify;},[notify]);
  const visibleMessages = messages.map((message,index)=>({message,index})).filter(({message})=>`${message.name} ${message.text}`.toLowerCase().includes(search.toLowerCase()));
  const filteredMessages=visibleMessages.filter(({message:m})=>filter==="Tout"||(filter==="Non lus"&&m.unread>0)||(filter==="Groupes"&&m.name==="Design Crew")||(filter==="Affaires"&&m.name!=="Design Crew"));
  useEffect(()=>{
    if(!userId)return;
    const contact=messages[selected];
    return watchConversationMessages(userId,contact.mark,contact.name,(items)=>setSent(current=>({...current,[selected]:items})),()=>notifyRef.current("Messages hors ligne — réessayez dans un instant"));
  },[userId,selected]);
  async function send(e:FormEvent){
    e.preventDefault();const value=text.trim();if(!value||sending)return;
    const contact=messages[selected];setText("");
    if(!userId){setSent(current=>({...current,[selected]:[...(current[selected]||[]),{id:`local-${Date.now()}`,text:value,senderId:"local"}]}));notify("Message ajouté à la démonstration locale");return;}
    setSending(true);
    try{await sendConversationMessage(userId,contact.mark,contact.name,value);notify("Message envoyé et synchronisé");}
    catch{setText(value);notify("Échec de l'envoi — votre message a été conservé");}
    finally{setSending(false);}
  }
  return <div className={`inbox-space ${mobileChat?"chat-open":""}`}><aside className="inbox-list"><div className="inbox-title"><div><small>MESSAGERIE PRIORITAIRE</small><strong>Discussions <span>3 nouvelles</span></strong></div><button onClick={()=>{setText("Bonjour, ");notify("Choisissez un contact puis écrivez votre message")}} aria-label="Nouvelle conversation">＋</button></div><div className="inbox-presence"><button onClick={()=>notify("Votre statut est disponible")}><span className="presence-me">CB<i>＋</i></span><small>Mon statut</small></button>{messages.slice(0,3).map(m=><button key={m.name} onClick={()=>notify(`Statut de ${m.name} ouvert`)}><span>{m.mark}<i/></span><small>{m.name.split(" ")[0]}</small></button>)}</div><div className="inbox-filters">{["Tout","Non lus","Groupes","Affaires"].map(item=><button key={item} className={filter===item?"active":""} onClick={()=>setFilter(item)}>{item}</button>)}</div>{filteredMessages.map(({message:m,index})=><button className={selected===index?"active":""} onClick={()=>{setSelected(index);setMobileChat(true)}} key={m.name}><span className="profile-avatar"><Mark color={m.color}>{m.mark}</Mark><i/></span><span><strong>{m.name}<em>{m.badge}</em></strong><small>{m.text}</small></span><i>{m.time}</i>{m.unread>0&&<b>{m.unread}</b>}</button>)}{filteredMessages.length===0&&<p className="empty-messages">Aucune conversation trouvée.</p>}</aside><section className="deal-chat"><header><button className="chat-back" onClick={()=>setMobileChat(false)} aria-label="Retour aux discussions">←</button><span className="profile-avatar large"><Mark color={messages[selected].color}>{messages[selected].mark}</Mark><i/></span><div><strong>{messages[selected].name}</strong><small><i/> En ligne · Identité vérifiée</small></div><button onClick={()=>onCall(messages[selected].name,false)} aria-label="Appel audio">☎</button><button onClick={()=>onCall(messages[selected].name,true)} aria-label="Appel vidéo">▣</button><button onClick={()=>notify("Options de conversation : silencieux, bloquer, signaler")} aria-label="Options">•••</button></header><div className="profile-pulse"><span>✦</span><div><small>{messages[selected].badge} · {messages[selected].streak} ÉCHANGES RÉUSSIS</small><strong>{messages[selected].mood}</strong></div><button onClick={()=>notify(`Profil de ${messages[selected].name} ouvert`)}>Voir le profil ↗</button></div><div className="deal-context"><span className="product-thumb">◇</span><div><small>CONVERSATION LIÉE À UNE OPPORTUNITÉ</small><strong>{selected===0?"Canapé modulable en velours":"MacBook Air M3 · Comme neuf"}</strong><p>{selected===0?"Échange accepté":"750 000 FCFA"}</p></div><button onClick={()=>notify("Annonce ouverte")}>Voir l&apos;annonce ↗</button></div><div className="deal-messages" key={selected}><span className="chat-date">AUJOURD&apos;HUI</span><div className="theirs">Bonjour ! Est-ce que votre annonce est toujours disponible ?<small>12:03</small></div><div className="mine">Oui, absolument. On peut aussi discuter d&apos;un échange.<small>12:05 <b>✓✓</b></small></div><div className="theirs">Parfait, je vous envoie ma proposition.<small>12:08</small></div>{(sent[selected]||[]).map((message)=><div className="mine" key={message.id}>{message.text}<small>{message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR",{hour:"2-digit",minute:"2-digit"})||"Envoi…"} <b>{message.createdAt?"✓✓":"✓"}</b></small></div>)}</div><form onSubmit={send}><label className="chat-upload" aria-label="Ajouter une pièce jointe">＋<input type="file" accept="image/*,video/*,.pdf" onChange={e=>e.target.files?.[0]&&notify(`${e.target.files[0].name} prêt à envoyer`)}/></label><button type="button" onClick={()=>setText(value=>`${value} 😊`)} aria-label="Ajouter un emoji">☺</button><input value={text} onChange={e=>setText(e.target.value)} maxLength={4000} placeholder="Écrire un message…"/><button className="voice-action" type="button" onClick={()=>notify("Enregistrement vocal prêt — appuyez de nouveau pour envoyer")} aria-label="Message vocal">◉</button><button className="offer-action" type="button" onClick={()=>setModal("message")}>◇ Offre</button><button className="send-action" type="submit" disabled={sending} aria-label={sending?"Envoi en cours":"Envoyer"}>{sending?"···":"➤"}</button></form></section></div>;
}

function TwinSpace({ step, setStep, consent, setConsent, notify }: { step:number; setStep:(n:number)=>void; consent:boolean; setConsent:(v:boolean)=>void; notify:(text:string)=>void }) {
  const [product,setProduct]=useState("Montre Kongo One"); const [price,setPrice]=useState("42 000 FCFA"); const [tone,setTone]=useState("Chaleureux"); const [languages,setLanguages]=useState(["Français","Lingala"]);
  function saveDraft(){try{localStorage.setItem("whappy-twin-draft",JSON.stringify({product,price,tone,languages,updatedAt:new Date().toISOString()}));notify("Double enregistré en brouillon sur cet appareil");}catch{notify("Le stockage local est bloqué par votre navigateur");}}
  return <div className="space-scroll twin-space"><section className="twin-hero"><div className="twin-copy"><span className="signal"><i/> STUDIO DOUBLE · VOTRE IMAGE, VOTRE CONTRÔLE</span><h2>Vous créez une fois.<br/><em>Votre Double vend toujours.</em></h2><p>Enregistrez votre propre vidéo. Whappy prépare un présentateur numérique pour les produits, langues et formats que vous autorisez.</p><div className="safety-chips"><span>✓ Consentement explicite</span><span>✓ Révocable à tout moment</span><span>✓ Marqué comme IA</span></div></div><div className="twin-visual"><div className="scan-lines"/><div className="human"><span>CB</span><small>VOUS</small></div><div className="transfer">··· ✦ ···</div><div className="digital"><span>CB</span><small>DOUBLE IA</small><b>IA</b></div></div></section><section className="twin-builder"><div className="builder-steps">{["Consentement","Enregistrement","Produits","Personnalité","Publication"].map((x,index)=><button className={step===index+1?"active":step>index+1?"done":""} onClick={()=>{if(index===0||consent)setStep(index+1);else notify("Validez d&apos;abord votre consentement")}} key={x}><span>{step>index+1?"✓":index+1}</span><small>{x}</small></button>)}</div><div className="builder-card">{step===1&&<><span className="builder-icon">◎</span><h3>Votre identité vous appartient</h3><p>Whappy utilise uniquement les vidéos de vous-même que vous fournissez. Votre Double ne peut pas représenter une autre personne et chaque vidéo générée porte le label « Créé avec un Double IA ».</p><label className="consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/><span>Je confirme créer un Double à partir de ma propre image et j&apos;accepte son utilisation uniquement pour mes contenus Whappy autorisés.</span></label><button disabled={!consent} onClick={()=>setStep(2)}>Continuer vers l&apos;enregistrement ↗</button></>}{step===2&&<TwinRecorder onComplete={()=>{notify("Capsule vidéo validée");setStep(3)}}/>}{step===3&&<><span className="builder-icon">◇</span><h3>Ajoutez ce que votre Double vendra</h3><p>Définissez le produit et le prix que le Double pourra présenter. Vous pourrez toujours les modifier.</p><div className="twin-product-fields"><label>Produit<input value={product} onChange={e=>setProduct(e.target.value)} placeholder="Nom du produit"/></label><label>Prix ou offre<input value={price} onChange={e=>setPrice(e.target.value)} placeholder="Prix en FCFA"/></label></div><button disabled={!product.trim()} onClick={()=>setStep(4)}>Configurer sa personnalité ↗</button></>}{step===4&&<><span className="builder-icon">✦</span><h3>Donnez-lui votre ton</h3><div className="tone-grid">{["Chaleureux","Expert","Énergique","Élégant"].map(item=><button className={tone===item?"active":""} onClick={()=>setTone(item)} key={item}>{item}</button>)}</div><div className="language-grid">{["Français","Lingala","Anglais","Kituba"].map(item=><label key={item}><input type="checkbox" checked={languages.includes(item)} onChange={()=>setLanguages(current=>current.includes(item)?current.filter(x=>x!==item):[...current,item])}/>{item}</label>)}</div><p>Réponses commerciales seulement, aucune prise de position personnelle.</p><button disabled={!languages.length} onClick={()=>setStep(5)}>Prévisualiser mon Double ↗</button></>}{step===5&&<><span className="builder-icon ready">✓</span><h3>Votre brouillon de Double est prêt</h3><p><strong>{tone}</strong>, en {languages.join(" et ")}, il présentera <strong>{product}</strong> à <strong>{price}</strong>. La génération IA finale nécessitera le service vidéo Whappy et votre validation avant publication.</p><div className="publish-options"><label><input type="checkbox" defaultChecked/> Boutique Whappy</label><label><input type="checkbox" defaultChecked/> Replay des directs</label><label><input type="checkbox"/> Réponses vidéo automatiques</label></div><button onClick={saveDraft}>Enregistrer en brouillon ✦</button></>}</div></section></div>;
}

function ListingCard({ item, saved, onSave, onOpen }: { item:Listing; saved:boolean; onSave:()=>void; onOpen:()=>void }) {
  return <article className="listing-card"><button className={`save ${saved?"active":""}`} onClick={onSave}>{saved?"♥":"♡"}</button><button className={`listing-art ${item.tone} ${item.mediaUrl?"has-media":""}`} style={item.mediaUrl?{backgroundImage:`linear-gradient(180deg,transparent 45%,rgba(2,18,7,.72)),url(${item.mediaUrl})`}:undefined} onClick={onOpen}>{!item.mediaUrl&&<span>{item.mark}</span>}<small>{item.category}</small>{item.mode==="troc"&&<b>⇄ TROC</b>}{item.status==="reserved"&&<em className="reserved-badge">RÉSERVÉ</em>}</button><div><span className="seller"><i>{item.mark}</i>{item.seller}<b>✓</b><small>{item.trust}% fiable</small></span><h3>{item.title}</h3><strong>{item.price}</strong><p>⌖ {item.place}</p><button onClick={onOpen}>{item.mode==="troc"?"Proposer un échange":"Discuter"} ↗</button></div></article>;
}

function SectionTitle({ overline,title,action,onClick }: { overline:string;title:string;action:string;onClick:()=>void }) { return <div className="section-title"><div><small>{overline}</small><h3>{title}</h3></div><button onClick={onClick}>{action} ↗</button></div>; }

function LiveViewer({ live,onClose,notify,onAdd }: { live:(typeof lives)[number];onClose:()=>void;notify:(text:string)=>void;onAdd:(live:(typeof lives)[number],quantity:number)=>void }) { const [heart,setHeart]=useState(false);const [quantity,setQuantity]=useState(1);return <div className="live-viewer"><div className={`live-video ${live.tone}`}><button className="viewer-close" onClick={onClose} aria-label="Fermer le direct">×</button><header><span><i/> EN DIRECT</span><b>{live.viewers} spectateurs</b></header><div className="viewer-host">{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}</div><div className="floating-chat"><span><b>Amina</b> Livraison possible ?</span><span><b>Junior</b> Je prends en bleu 🔥</span><span><b>Grâce</b> Très beau produit !</span></div><div className="viewer-bottom"><div><small>{live.host}</small><h2>{live.title}</h2></div><button aria-label="Aimer ce direct" onClick={()=>setHeart(v=>!v)} className={heart?"hearted":""}>♥</button></div></div><aside className="live-cart"><span>PRODUIT DU DIRECT</span><div className="cart-product">◇</div><h3>{live.product}</h3><strong>{live.price} FCFA</strong><p>Stock limité · Livraison disponible</p><div className="quantity"><button type="button" onClick={()=>setQuantity(value=>Math.max(1,value-1))}>−</button><b>{quantity}</b><button type="button" onClick={()=>setQuantity(value=>Math.min(9,value+1))}>＋</button></div><button className="buy" onClick={()=>{onAdd(live,quantity);onClose()}}>Ajouter au panier</button><button className="offer" onClick={()=>notify("Offre préparée — vous pouvez maintenant écrire au vendeur")}>Faire une offre</button><small>◆ Paiement protégé à activer avant encaissement</small></aside></div>; }

function ActionModal({ type,busy,onClose,onSubmit,consent,setConsent,setTwinStep,go,notify }: { type:"sell"|"seek"|"live"|"twin"|"message";busy:boolean;onClose:()=>void;onSubmit:(e:FormEvent)=>void;consent:boolean;setConsent:(v:boolean)=>void;setTwinStep:(v:number)=>void;go:(s:Space)=>void;notify:(t:string)=>void }) {
  const [liveMode,setLiveMode]=useState<"human"|"twin">("human");
  const [fileCount,setFileCount]=useState(0);
  if(type==="twin") return null;
  const data={sell:["Vendre ou troquer","Transformez ce que vous avez en opportunité."],seek:["Publier une recherche","Décrivez clairement votre besoin."],live:["Préparer votre direct","Produits, titre et audience en un seul endroit."],message:["Faire une offre","Proposez un prix ou un échange sécurisé."]}[type];
  return <div className="modal-layer" role="dialog" aria-modal="true" aria-label={data[0]}><form className="action-modal" onSubmit={onSubmit}><button type="button" className="modal-close" onClick={onClose} aria-label="Fermer">×</button><span className="modal-icon">{type==="sell"?"◇":type==="seek"?"⌖":type==="live"?"●":"⇄"}</span><small>WHAPPY ACTION</small><h2>{data[0]}</h2><p>{data[1]}</p>{type==="sell"&&<><label>Titre de l&apos;annonce<input name="title" required placeholder="Ex. Appareil photo hybride"/></label><div className="modal-row"><label>Mode<select name="mode" defaultValue="sell"><option value="sell">Vendre</option><option value="barter">Troquer</option><option value="both">Vendre ou troquer</option></select></label><label>Prix<input name="price" required placeholder="FCFA ou échange souhaité"/></label></div><div className="modal-row"><label>Catégorie<select name="category"><option>Tech</option><option>Mode</option><option>Maison</option><option>Services</option></select></label><label>Lieu<input name="place" required placeholder="Ex. Poto-Poto"/></label></div><label className={`upload-zone ${fileCount?"selected":""}`}>{fileCount?"✓ Média prêt à être téléversé":"＋ Ajouter une photo ou vidéo"}<input name="media" type="file" accept="image/*,video/*" onChange={e=>{const count=e.target.files?.length||0;setFileCount(count);if(count)notify("Média prêt à être téléversé")}}/></label></>}{type==="seek"&&<><label>Que recherchez-vous ?<input name="title" required placeholder="Ex. Un développeur Flutter disponible"/></label><label>Détails<textarea name="details" required placeholder="Décrivez précisément votre besoin…"/></label><div className="modal-row"><label>Catégorie<select name="category"><option>Produits</option><option>Services</option><option>Situations</option></select></label><label>Zone<input name="area" required placeholder="Quartier, ville ou à distance"/></label></div><div className="modal-row"><label>Budget ou échange<input name="reward" placeholder="Ex. 150 000 FCFA"/></label><label className="urgent-check"><input type="checkbox" name="urgent"/> Besoin urgent</label></div></>}{type==="live"&&<><label>Titre du direct<input name="title" required placeholder="Ex. Découverte de ma nouvelle collection"/></label><label>Produit à présenter<input name="product" placeholder="Sélectionner dans ma boutique"/></label><input type="hidden" name="liveMode" value={liveMode}/><div className="live-mode"><button type="button" className={liveMode==="human"?"active":""} onClick={()=>setLiveMode("human")}>▣ Caméra réelle</button><button type="button" className={liveMode==="twin"?"active":""} onClick={()=>setLiveMode("twin")}>◎ Mon Double IA</button></div><label className="mini-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> J&apos;utilise ma propre image ou un Double dont je contrôle les droits.</label></>}{type==="message"&&<><label>Votre proposition<input name="offer" required placeholder="Votre prix ou ce que vous proposez en échange"/></label><label>Message<textarea name="message" placeholder="Ajoutez les détails de votre offre…"/></label></>}<button className="modal-submit" type="submit" onClick={()=>{if(type==="live"&&!consent)notify("Confirmez les droits sur la vidéo avant de continuer")}} disabled={busy||(type==="live"&&!consent)}>{busy?"Synchronisation…":type==="live"?"Entrer dans le studio":type==="seek"?"Activer ma recherche":type==="message"?"Envoyer l'offre":"Publier l'annonce"} ↗</button>{type==="live"&&<button type="button" className="twin-link" onClick={()=>{onClose();go("twin");setTwinStep(1)}}>Créer d&apos;abord mon Double consentant</button>}</form></div>;
}

function ProfilePanel({ name,phone,onClose,go,onOpenShop,onOpenOrders,onSignOut }: { name:string;phone:string;onClose:()=>void;go:(space:Space)=>void;onOpenShop:()=>void;onOpenOrders:()=>void;onSignOut:()=>void }) {
  return <div className="profile-layer"><button className="profile-dismiss" onClick={onClose} aria-label="Fermer le profil"/><aside className="profile-panel" role="dialog" aria-modal="true" aria-label="Mon profil"><header><span>{name.split(/\s+/).map(part=>part[0]).join("").slice(0,2).toUpperCase()}</span><div><small>COMPTE WHAPPY</small><strong>{name}</strong><p>{phone} · Vérifié</p></div><button onClick={onClose} aria-label="Fermer">×</button></header><section><button onClick={()=>go("business")}><span>▥</span><div><strong>Business Suite</strong><small>Pages professionnelles et publicités</small></div><b>→</b></button><button onClick={onOpenShop}><span>◇</span><div><strong>Ma boutique</strong><small>Gérer mes annonces et mes ventes</small></div><b>→</b></button><button onClick={onOpenOrders}><span>▤</span><div><strong>Mes commandes</strong><small>Suivi, reçus et points de remise</small></div><b>→</b></button><button onClick={()=>go("twin")}><span>◎</span><div><strong>Mon Double</strong><small>Capsule, produits et autorisations</small></div><b>→</b></button><button onClick={()=>go("inbox")}><span>◫</span><div><strong>Mes conversations</strong><small>Messages, offres et commandes</small></div><b>→</b></button></section><div className="profile-safety"><span>✓</span><div><strong>Identité protégée</strong><small>Un numéro unique pour votre compte</small></div></div><button className="profile-signout" onClick={onSignOut}>Se déconnecter</button></aside></div>;
}
