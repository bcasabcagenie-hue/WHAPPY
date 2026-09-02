"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  businessCurrencyLabels,
  createBusinessLead,
  createBusinessOffer,
  leadStageLabels,
  saveBusinessSettings,
  updateBusinessLeadStage,
  watchBusinessSettings,
  watchOwnerLeads,
  watchOwnerOffers,
  type BusinessCurrency,
  type BusinessLead,
  type BusinessOffer,
  type BusinessOfferKind,
  type BusinessSettings,
  type LeadStage,
} from "@/lib/whappy-business";
import { publishListing } from "@/lib/whappy-data";

const demoOffers: BusinessOffer[] = [
  { id: "demo-offer-1", ownerId: "demo-user", kind: "product", title: "Montre Kongo One", description: "Montre connectée, livraison possible à Brazzaville.", price: 42000, currency: "XAF", status: "active" },
  { id: "demo-offer-2", ownerId: "demo-user", kind: "service", title: "Pack contenu pour boutique", description: "Photos, textes et mini-vidéos pour présenter vos produits.", price: 95000, currency: "XAF", status: "active" },
];

const demoLeads: BusinessLead[] = [
  { id: "demo-lead-1", ownerId: "demo-user", name: "Amina M.", contact: "+242 06 220 11 40", need: "Cherche 20 pièces pour une boutique", source: "Marketplace", stage: "qualified", nextAction: "Envoyer le catalogue demain", notes: "Intéressée par le tarif revendeur." },
  { id: "demo-lead-2", ownerId: "demo-user", name: "Junior K.", contact: "+242 05 410 20 20", need: "Demande un pack photo pour son annonce", source: "Moment Whappy", stage: "new", nextAction: "Répondre aujourd’hui", notes: "Premier contact à qualifier." },
  { id: "demo-lead-3", ownerId: "demo-user", name: "Mokabi Store", contact: "WhatsApp professionnel", need: "Partenariat de distribution", source: "Prospection", stage: "contacted", nextAction: "Relancer vendredi", notes: "A reçu la présentation de l’offre." },
];

const stageOrder: LeadStage[] = ["new", "contacted", "qualified", "won", "lost"];
const currencySymbols: Record<BusinessCurrency, string> = { XAF: "FCFA", XOF: "FCFA", CDF: "CDF", USD: "$", EUR: "€" };

function formatPrice(value: number, currency: BusinessCurrency) {
  return `${new Intl.NumberFormat("fr-FR").format(value)} ${currencySymbols[currency]}`;
}

type MarketplaceOffer = { id: string | number; title: string; price: string; place: string; seller: string; mark: string; tone: string; category: string; mode: "vente"; trust: number; ownerId?: string; status: "active" };

export function BusinessGrowthTools({ userId, userName, search, notify, demo = false, onOfferPublished }: { userId: string; userName: string; search: string; notify: (text: string) => void; demo?: boolean; onOfferPublished?: (listing: MarketplaceOffer) => void }) {
  const [tab, setTab] = useState<"catalog" | "prospects" | "settings">("catalog");
  const [offers, setOffers] = useState<BusinessOffer[]>([]);
  const [leads, setLeads] = useState<BusinessLead[]>([]);
  const [settings, setSettings] = useState<BusinessSettings>({ ownerId: userId, currency: "XAF", defaultKind: "product" });
  const [modal, setModal] = useState<"offer" | "lead" | null>(null);
  const [busy, setBusy] = useState(false);
  const query = search.trim().toLowerCase();

  useEffect(() => {
    if (demo) {
      queueMicrotask(() => {
        try {
          const stored = JSON.parse(localStorage.getItem(`whappy-business-growth-${userId}`) || "null") as null | { offers?: BusinessOffer[]; leads?: BusinessLead[]; settings?: BusinessSettings };
          setOffers(stored?.offers?.length ? stored.offers : demoOffers);
          setLeads(stored?.leads?.length ? stored.leads : demoLeads);
          setSettings((current) => stored?.settings ? { ...current, ...stored.settings, ownerId: userId } : { ...current, ownerId: userId, currency: "XAF", defaultKind: "product" });
        } catch {
          setOffers(demoOffers); setLeads(demoLeads); setSettings({ ownerId: userId, currency: "XAF", defaultKind: "product" });
        }
      });
      return;
    }
    return watchBusinessSettings(userId, (value) => { if (value) setSettings(value); }, () => notify("Les paramètres Business sont momentanément indisponibles"));
  }, [demo, notify, userId]);

  useEffect(() => {
    if (demo) return;
    return watchOwnerOffers(userId, setOffers, () => notify("Le catalogue Business est momentanément indisponible"));
  }, [demo, notify, userId]);

  useEffect(() => {
    if (demo) return;
    return watchOwnerLeads(userId, setLeads, () => notify("Les prospects sont momentanément indisponibles"));
  }, [demo, notify, userId]);

  useEffect(() => {
    if (!demo) return;
    try { localStorage.setItem(`whappy-business-growth-${userId}`, JSON.stringify({ offers, leads, settings })); } catch { /* La démo reste utilisable sans stockage local. */ }
  }, [demo, leads, offers, settings, userId]);

  const visibleOffers = useMemo(() => offers.filter((offer) => !query || `${offer.title} ${offer.description} ${offer.kind} ${offer.currency}`.toLowerCase().includes(query)), [offers, query]);
  const visibleLeads = useMemo(() => leads.filter((lead) => !query || `${lead.name} ${lead.contact} ${lead.need} ${lead.source} ${lead.stage}`.toLowerCase().includes(query)), [leads, query]);
  const clients = leads.filter((lead) => lead.stage === "won").length;
  const activeOffers = offers.filter((offer) => offer.status === "active").length;

  async function saveSettingsForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = { currency: String(form.get("currency") || "XAF") as BusinessCurrency, defaultKind: String(form.get("defaultKind") || "product") as BusinessOfferKind };
    setBusy(true);
    try {
      if (demo) setSettings((current) => ({ ...current, ...next }));
      else await saveBusinessSettings(userId, next);
      notify("Paramètres Business enregistrés"); setTab("catalog");
    } catch { notify("Les paramètres n’ont pas pu être enregistrés"); } finally { setBusy(false); }
  }

  async function publishOffer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const offer = { kind: String(form.get("kind") || settings.defaultKind) as BusinessOfferKind, title: String(form.get("title") || "").trim(), description: String(form.get("description") || "").trim(), price: Number(form.get("price") || 0), currency: settings.currency, status: "active" as const };
    if (!offer.title) { notify("Donnez un nom à votre produit ou service"); return; }
    setBusy(true);
    try {
      let offerId = `demo-offer-${Date.now()}`;
      if (demo) setOffers((current) => [{ ...offer, id: offerId, ownerId: userId }, ...current]);
      else offerId = await createBusinessOffer(userId, offer);
      const listing: MarketplaceOffer = {
        id: `business-${offerId}`, title: offer.title,
        price: offer.price ? formatPrice(offer.price, offer.currency) : "Prix à discuter",
        place: "Boutique Business", seller: userName,
        mark: userName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "BU",
        tone: offer.kind === "product" ? "blue" : "violet",
        category: offer.kind === "product" ? "Produits" : "Services", mode: "vente", trust: 100, ownerId: userId, status: "active",
      };
      if (demo) onOfferPublished?.(listing);
      else {
        try {
          const persisted = await publishListing(userId, { title: listing.title, price: listing.price, place: listing.place, seller: listing.seller, mark: listing.mark, category: listing.category, mode: listing.mode, sellerPhone: "" });
          onOfferPublished?.({ ...listing, id: persisted.id });
        } catch { notify("Offre ajoutée au catalogue, mais publication Marketplace à réessayer"); }
      }
      notify(`${offer.kind === "product" ? "Produit" : "Service"} ajouté au catalogue et à la Marketplace`); setModal(null); setTab("catalog");
    } catch { notify("L’offre n’a pas pu être enregistrée"); } finally { setBusy(false); }
  }

  async function createLead(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const lead = { name: String(form.get("name") || "").trim(), contact: String(form.get("contact") || "").trim(), need: String(form.get("need") || "").trim(), source: String(form.get("source") || "Prospection").trim(), stage: String(form.get("stage") || "new") as LeadStage, nextAction: String(form.get("nextAction") || "Relancer cette semaine").trim(), notes: String(form.get("notes") || "").trim() };
    if (!lead.name || !lead.need) { notify("Ajoutez le nom et le besoin du prospect"); return; }
    setBusy(true);
    try {
      if (demo) setLeads((current) => [{ ...lead, id: `demo-lead-${Date.now()}`, ownerId: userId }, ...current]);
      else await createBusinessLead(userId, lead);
      notify("Prospect ajouté à votre pipeline"); setModal(null); setTab("prospects");
    } catch { notify("Le prospect n’a pas pu être enregistré"); } finally { setBusy(false); }
  }

  async function changeStage(lead: BusinessLead, stage: LeadStage) {
    setBusy(true);
    try {
      if (demo) setLeads((current) => current.map((item) => item.id === lead.id ? { ...item, stage } : item));
      else await updateBusinessLeadStage(lead.id, stage);
      notify(`${lead.name} est maintenant « ${leadStageLabels[stage]} »`);
    } catch { notify("Le statut du prospect n’a pas pu être modifié"); } finally { setBusy(false); }
  }

  return <section className="business-growth-tools" lang="fr">
    <header className="growth-header"><div><small>BUSINESS · VENTE ET PROSPECTION · {userName}</small><h2>Trouvez des clients.<br/><em>Transformez vos offres.</em></h2><p>Présentez vos produits ou services, gardez vos prospects à portée de main et relancez au bon moment.</p></div><div className="growth-header-actions"><button onClick={() => setModal("offer")}>＋ Ajouter une offre</button><button className="secondary" onClick={() => setModal("lead")}>⌕ Prospecter un client</button></div></header>
    <div className="growth-stats"><article><small>CATALOGUE ACTIF</small><strong>{activeOffers}</strong><span>produit{activeOffers > 1 ? "s" : ""} ou service{activeOffers > 1 ? "s" : ""}</span></article><article><small>PROSPECTS</small><strong>{leads.length}</strong><span>contacts à suivre</span></article><article><small>CLIENTS GAGNÉS</small><strong>{clients}</strong><span>opportunités converties</span></article><article><small>DEVISE DU COMPTE</small><strong>{settings.currency}</strong><span>{businessCurrencyLabels[settings.currency]}</span></article></div>
    <nav className="growth-tabs"><button className={tab === "catalog" ? "active" : ""} onClick={() => setTab("catalog")}>Produits & services ({visibleOffers.length})</button><button className={tab === "prospects" ? "active" : ""} onClick={() => setTab("prospects")}>Prospection ({visibleLeads.length})</button><button className={tab === "settings" ? "active" : ""} onClick={() => setTab("settings")}>Paramètres Business</button></nav>
    {tab === "catalog" && <section className="growth-catalog"><header><div><small>VOTRE OFFRE</small><h3>Ce que vous proposez</h3></div><button onClick={() => setModal("offer")}>＋ Nouvelle offre</button></header><div className="growth-offer-grid">{visibleOffers.map((offer) => <article key={offer.id}><span className={`growth-kind ${offer.kind}`}>{offer.kind === "product" ? "PRODUIT" : "SERVICE"}</span><h3>{offer.title}</h3><p>{offer.description}</p><strong>{offer.price ? formatPrice(offer.price, offer.currency) : "Prix à discuter"}</strong><footer><small>Visible dans votre présentation Business</small><button onClick={() => notify(`Partagez « ${offer.title} » dans un Moment ou une conversation`)}>↗ Partager</button></footer></article>)}{!visibleOffers.length && <div className="growth-empty"><span>◇</span><h3>Votre catalogue est vide</h3><p>Ajoutez un produit ou un service pour commencer à trouver des clients.</p><button onClick={() => setModal("offer")}>＋ Ajouter la première offre</button></div>}</div></section>}
    {tab === "prospects" && <section className="growth-prospects"><header><div><small>PIPELINE COMMERCIAL</small><h3>Prospecter sans perdre le fil</h3><p>Ajoutez les personnes rencontrées dans Marketplace, les Moments, WhatsApp ou sur le terrain.</p></div><button onClick={() => setModal("lead")}>＋ Nouveau prospect</button></header><div className="lead-board">{stageOrder.map((stage) => <section key={stage}><header><strong>{leadStageLabels[stage]}</strong><span>{visibleLeads.filter((lead) => lead.stage === stage).length}</span></header>{visibleLeads.filter((lead) => lead.stage === stage).map((lead) => <article key={lead.id}><strong>{lead.name}</strong><small>{lead.contact || "Contact à compléter"}</small><p>{lead.need}</p><em>Source : {lead.source}</em><time>Prochaine action : {lead.nextAction || "À planifier"}</time><select value={lead.stage} disabled={busy} onChange={(event) => void changeStage(lead, event.target.value as LeadStage)} aria-label={`Statut de ${lead.name}`}>{stageOrder.map((value) => <option key={value} value={value}>{leadStageLabels[value]}</option>)}</select></article>)}{!visibleLeads.some((lead) => lead.stage === stage) && <div className="lead-empty">Aucun prospect ici</div>}</section>)}</div></section>}
    {tab === "settings" && <section className="growth-settings"><header><div><small>PARAMÈTRES COMMERCIAUX</small><h3>Adaptez votre compte à votre activité</h3><p>La devise choisie sera utilisée pour présenter vos produits, services et budgets de prospection.</p></div></header><form onSubmit={saveSettingsForm}><label>Devise principale<select name="currency" defaultValue={settings.currency}>{Object.entries(businessCurrencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Offre par défaut<select name="defaultKind" defaultValue={settings.defaultKind}><option value="product">Produit</option><option value="service">Service</option></select></label><div className="growth-settings-preview"><span>APERÇU</span><strong>{settings.defaultKind === "product" ? "Produit" : "Service"} · 42 000 {currencySymbols[settings.currency]}</strong><small>Votre compte est préparé pour trouver des clients sur WHAPPY.</small></div><button className="growth-submit" disabled={busy}>{busy ? "Enregistrement…" : "Enregistrer les paramètres"}</button></form></section>}
    {modal && <div className="growth-modal"><button className="growth-modal-dismiss" onClick={() => setModal(null)} aria-label="Fermer"/><form onSubmit={modal === "offer" ? publishOffer : createLead}><header><div><small>{modal === "offer" ? "CATALOGUE BUSINESS" : "PROSPECTION"}</small><h3>{modal === "offer" ? "Ajouter une offre" : "Ajouter un prospect"}</h3></div><button type="button" onClick={() => setModal(null)} aria-label="Fermer">×</button></header>{modal === "offer" ? <><label>Type d’offre<select name="kind" defaultValue={settings.defaultKind}><option value="product">Produit</option><option value="service">Service</option></select></label><label>Nom du produit ou service<input name="title" required maxLength={120} placeholder="Ex. Pack photo pour boutique"/></label><label>Description<textarea name="description" maxLength={600} placeholder="Décrivez clairement la valeur proposée."/></label><label>Prix indicatif ({currencySymbols[settings.currency]})<input name="price" type="number" min={0} step={500} placeholder="0 = à discuter"/></label></> : <><label>Nom du prospect<input name="name" required maxLength={100} placeholder="Ex. Amina M."/></label><label>Téléphone, WhatsApp ou email<input name="contact" placeholder="Comment le joindre ?"/></label><label>Besoin ou opportunité<input name="need" required maxLength={180} placeholder="Ce qu’il cherche ou ce que vous pouvez lui proposer"/></label><div className="growth-form-row"><label>Source<input name="source" defaultValue="Prospection" placeholder="Marketplace, Moment, terrain…"/></label><label>Statut<select name="stage" defaultValue="new">{stageOrder.map((stage) => <option key={stage} value={stage}>{leadStageLabels[stage]}</option>)}</select></label></div><label>Prochaine action<input name="nextAction" defaultValue="Relancer cette semaine" placeholder="Ex. Envoyer le catalogue vendredi"/></label><label>Notes<textarea name="notes" maxLength={600} placeholder="Contexte, budget, objections…"/></label></>}<button className="growth-submit" disabled={busy}>{busy ? "Enregistrement…" : modal === "offer" ? "Ajouter au catalogue" : "Ajouter au pipeline"}</button></form></div>}
  </section>;
}
