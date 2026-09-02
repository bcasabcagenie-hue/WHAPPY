"use client";

/* Les images proviennent de fichiers choisis par l’utilisateur et de prévisualisations blob locales. */
/* eslint-disable @next/next/no-img-element */

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createAdCampaign, createArtist, createBusinessPage, deleteAdCampaign, deleteArtist, deleteBusinessPage, updateArtist, updateBusinessBranding, updateBusinessPage, updateCampaignStatus, watchBusinessPages, watchOwnerAdEvents, watchOwnerArtists, watchOwnerCampaigns, type AdCampaign, type AdEvent, type Artist, type ArtistStatus, type BusinessPage } from "@/lib/whappy-business";
import { WepiAssistant } from "@/app/components/WepiAssistant";
import { TicketBulkPanel } from "@/app/components/TicketBulkPanel";

const pageTypes = { business: "Entreprise", creator: "Créateur", organization: "Organisation" } as const;
const objectives = { reach: "Notoriété", messages: "Messages", traffic: "Visites", sales: "Ventes" } as const;
const campaignStatuses = { draft: "Brouillon", active: "En diffusion", paused: "En pause", completed: "Terminée" } as const;
const adPlacements = { profile_story: "Story du profil", inbox: "Découverte Business", market: "Marché", live: "Lives" } as const;
const adDestinations = { message: "Message client", page: "Page Business", call: "Appel direct", website: "Lien externe" } as const;
const adPlaybooks = [
  { title: "Faire écrire les clients", text: "Une Story sponsorisée sur le profil avec bouton message et suivi dans votre inbox.", objective: "messages", placement: "profile_story", destination: "message" },
  { title: "Promouvoir un produit", text: "Montrez une offre dans la Story du profil et le Marché avec un bouton d’achat ou de contact.", objective: "sales", placement: "market", destination: "page" },
  { title: "Remplir un live", text: "Annoncez un direct avant le lancement et envoyez les gens vers le live ou la page.", objective: "reach", placement: "live", destination: "page" },
] as const;
const artistStatuses: Record<ArtistStatus, string> = { prospect: "À suivre", active: "Actif", paused: "En pause", archived: "Archivé" };
const demoArtists: Artist[] = [
  { id: "demo-artist-1", ownerId: "demo-user", name: "Grâce Mpassi", stageName: "Grâce M.", discipline: "Afro-pop", city: "Brazzaville", contact: "+242 06 555 12 12", email: "grace@example.com", status: "active", isScouted: true, nextAction: "Session studio · 24 août", monthlyBudget: 180000, notes: "Priorité : finaliser le single et préparer le visuel.", createdAt: null, updatedAt: null },
  { id: "demo-artist-2", ownerId: "demo-user", name: "Joël Nsimba", stageName: "Jøël", discipline: "Rap / Hip-hop", city: "Pointe-Noire", contact: "+242 05 410 20 20", email: "joel@example.com", status: "prospect", isScouted: false, nextAction: "Relancer la prise de contact · 28 août", monthlyBudget: 0, notes: "Découverte en showcase. Demander le dossier presse.", createdAt: null, updatedAt: null },
  { id: "demo-artist-3", ownerId: "demo-user", name: "Mina Kamba", stageName: "Mina K.", discipline: "R&B", city: "Brazzaville", contact: "+242 06 300 44 55", email: "mina@example.com", status: "paused", isScouted: false, nextAction: "Point stratégie · 02 sept.", monthlyBudget: 120000, notes: "Campagne suspendue jusqu'à la sortie du prochain titre.", createdAt: null, updatedAt: null },
];

export function BusinessStudio({ userId, userName, search, notify, demo = false }: { userId: string; userName: string; search: string; notify: (text: string) => void; demo?: boolean }) {
  const [tab, setTab] = useState<"overview" | "pages" | "artists" | "ads" | "tickets" | "wepi">("overview");
  const [pages, setPages] = useState<BusinessPage[]>([]);
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
  const [events, setEvents] = useState<AdEvent[]>([]);
  const [artists, setArtists] = useState<Artist[]>(demo ? demoArtists : []);
  const [artistFilter, setArtistFilter] = useState<"all" | ArtistStatus | "scouted">("all");
  const [adFilter, setAdFilter] = useState<"all" | "active" | "paused">("all");
  const [creator, setCreator] = useState<"page" | "campaign" | "branding" | "artist" | null>(null);
  const [selectedPage, setSelectedPage] = useState("");
  const [editing, setEditing] = useState<BusinessPage | null>(null);
  const [editingArtist, setEditingArtist] = useState<Artist | null>(null);
  const [branding, setBranding] = useState<BusinessPage | null>(null);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState("");
  const [coverPreview, setCoverPreview] = useState("");
  const [busy, setBusy] = useState(false);
  const notifyRef = useRef(notify);
  const artistStorageReadyRef = useRef(!demo);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => () => { if (logoPreview.startsWith("blob:")) URL.revokeObjectURL(logoPreview); }, [logoPreview]);
  useEffect(() => () => { if (coverPreview.startsWith("blob:")) URL.revokeObjectURL(coverPreview); }, [coverPreview]);
  useEffect(() => demo ? undefined : watchBusinessPages(userId, setPages, () => notifyRef.current("Vos pages sont momentanément indisponibles")), [demo,userId]);
  useEffect(() => demo ? undefined : watchOwnerCampaigns(userId, setCampaigns, () => notifyRef.current("Vos campagnes sont momentanément indisponibles")), [demo,userId]);
  useEffect(() => demo ? undefined : watchOwnerAdEvents(userId, setEvents, () => notifyRef.current("Les statistiques sont momentanément indisponibles")), [demo,userId]);
  useEffect(() => {
    if (demo) {
      let active = true;
      artistStorageReadyRef.current = false;
      queueMicrotask(() => {
        if (!active) return;
        try {
          const storageKey = `whappy-artists-${userId}`;
          const stored = JSON.parse(localStorage.getItem(storageKey) || "null") as Artist[] | null;
          const next = Array.isArray(stored) ? stored : demoArtists;
          setArtists(next);
          localStorage.setItem(storageKey, JSON.stringify(next));
        } catch { /* La démo reste utilisable sans stockage local. */ }
        artistStorageReadyRef.current = true;
      });
      return () => { active = false; };
    }
    return watchOwnerArtists(userId, setArtists, () => notifyRef.current("Vos artistes sont momentanément indisponibles"));
  }, [demo, userId]);
  useEffect(() => {
    if (!demo || !artistStorageReadyRef.current) return;
    try { localStorage.setItem(`whappy-artists-${userId}`, JSON.stringify(artists)); } catch { /* La démo reste utilisable sans stockage local. */ }
  }, [artists, demo, userId]);

  const metrics = useMemo(() => {
    const impressions = events.filter((event) => event.type === "impression").length;
    const clicks = events.filter((event) => event.type === "click").length;
    return { impressions, clicks, ctr: impressions ? (clicks / impressions * 100).toFixed(1) : "0.0", active: campaigns.filter((campaign) => campaign.status === "active").length };
  }, [campaigns, events]);
  const searchValue = search.trim().toLowerCase();
  const visiblePages = useMemo(() => pages.filter((page) => !searchValue || `${page.name} ${page.handle} ${page.category} ${page.city} ${page.phone} ${page.website}`.toLowerCase().includes(searchValue)), [pages, searchValue]);
  const visibleCampaigns = useMemo(() => campaigns.filter((campaign) => (adFilter === "all" || campaign.status === adFilter) && (!searchValue || `${campaign.title} ${campaign.pageName} ${campaign.audience} ${campaign.city}`.toLowerCase().includes(searchValue))), [adFilter, campaigns, searchValue]);
  const visibleArtists = useMemo(() => artists.filter((artist) => {
    const matchesStatus = artistFilter === "all" || artist.status === artistFilter || (artistFilter === "scouted" && artist.isScouted);
    const matchesSearch = !searchValue || `${artist.name} ${artist.stageName} ${artist.discipline} ${artist.city} ${artist.status} ${artist.nextAction}`.toLowerCase().includes(searchValue);
    return matchesStatus && matchesSearch;
  }), [artists, artistFilter, searchValue]);

  const artistMetrics = useMemo(() => ({
    active: artists.filter((artist) => artist.status === "active").length,
    nextActions: artists.filter((artist) => artist.nextAction.trim()).length,
    scouted: artists.filter((artist) => artist.isScouted).length,
    budget: artists.reduce((sum, artist) => sum + (artist.monthlyBudget || 0), 0),
  }), [artists]);

  async function publishPage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const values = {
        name: String(form.get("name") || ""),
        type: String(form.get("type") || "business") as BusinessPage["type"],
        category: String(form.get("category") || ""),
        bio: String(form.get("bio") || ""),
        city: String(form.get("city") || ""),
        phone: String(form.get("phone") || ""),
        website: String(form.get("website") || ""),
      };
      if (!values.phone.trim() && !values.website.trim()) { notify("Ajoutez un téléphone ou un lien public pour être contacté"); return; }
      if (editing) await updateBusinessPage(editing.id, values);
      else await createBusinessPage(userId, values);
      notify(editing ? "Page professionnelle actualisée" : "Page professionnelle publiée");
      setEditing(null); setCreator(null); setTab("pages");
    } catch { notify("La page n’a pas pu être enregistrée"); } finally { setBusy(false); }
  }

  async function publishCampaign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const pageId = String(form.get("pageId") || selectedPage || "");
    const page = pages.find((item) => item.id === pageId);
    if (!page) { notify("Créez ou sélectionnez une page professionnelle"); return; }
    const title = String(form.get("title") || "").trim();
    const creative = String(form.get("creative") || "").trim();
    const audience = String(form.get("audience") || "").trim();
    if (title.length < 3 || creative.length < 20 || audience.length < 3) { notify("Ajoutez un titre, un texte d’au moins 20 caractères et une audience précise"); return; }
    setBusy(true);
    try {
      await createAdCampaign(userId, {
        pageId: page.id, pageName: page.name,
        objective: String(form.get("objective") || "reach") as AdCampaign["objective"],
        placement: String(form.get("placement") || "profile_story") as AdCampaign["placement"],
        destination: String(form.get("destination") || "message") as AdCampaign["destination"],
        title, creative, cta: String(form.get("cta") || "Envoyer un message"), audience, city: String(form.get("city") || page.city), phone: String(form.get("phone") || page.phone), link: String(form.get("link") || page.website), dailyBudget: Number(form.get("dailyBudget") || 500), days: Number(form.get("days") || 7),
      });
      notify("Publicité Business activée en mode expérimental"); setCreator(null); setTab("ads");
    } catch { notify("La campagne n’a pas pu être créée"); } finally { setBusy(false); }
  }

  async function changeCampaign(campaign: AdCampaign) {
    const next = campaign.status === "active" ? "paused" : "active";
    setBusy(true); try { await updateCampaignStatus(campaign.id, next); notify(next === "active" ? "Campagne relancée" : "Campagne mise en pause"); } catch { notify("Le statut n’a pas pu être modifié"); } finally { setBusy(false); }
  }

  async function removePage(page: BusinessPage) {
    if (campaigns.some((campaign) => campaign.pageId === page.id)) { notify("Supprimez d’abord les campagnes liées à cette page"); return; }
    setBusy(true); try { await deleteBusinessPage(page.id); notify("Page supprimée"); } catch { notify("La page n’a pas pu être supprimée"); } finally { setBusy(false); }
  }

  async function publishArtist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const values = {
      name: String(form.get("name") || "").trim(),
      stageName: String(form.get("stageName") || "").trim(),
      discipline: String(form.get("discipline") || "").trim(),
      city: String(form.get("city") || "").trim(),
      contact: String(form.get("contact") || "").trim(),
      email: String(form.get("email") || "").trim(),
      status: String(form.get("status") || "prospect") as ArtistStatus,
      isScouted: editingArtist ? editingArtist.isScouted : false,
      nextAction: String(form.get("nextAction") || "").trim(),
      monthlyBudget: Number(form.get("monthlyBudget") || 0),
      notes: String(form.get("notes") || "").trim(),
    };
    if (!values.stageName || !values.discipline) { notify("Ajoutez un nom de scène et une discipline"); return; }
    setBusy(true);
    try {
      if (editingArtist) {
        if (demo) setArtists((current) => current.map((artist) => artist.id === editingArtist.id ? { ...artist, ...values } : artist));
        else await updateArtist(editingArtist.id, values);
        notify("Fiche artiste actualisée");
      } else if (demo) {
        setArtists((current) => [{ ...values, id: `demo-artist-${Date.now()}`, ownerId: userId, createdAt: null, updatedAt: null }, ...current]);
        notify("Artiste ajouté à votre roster");
      } else {
        await createArtist(userId, values);
        notify("Artiste ajouté à votre roster");
      }
      setEditingArtist(null); setCreator(null); setTab("artists");
    } catch { notify("La fiche artiste n’a pas pu être enregistrée"); } finally { setBusy(false); }
  }

  async function changeArtistStatus(artist: Artist, status: ArtistStatus) {
    setBusy(true);
    try {
      if (demo) setArtists((current) => current.map((item) => item.id === artist.id ? { ...item, status } : item));
      else await updateArtist(artist.id, { status });
      notify(`Statut de ${artist.stageName} mis à jour`);
    } catch { notify("Le statut n’a pas pu être modifié"); } finally { setBusy(false); }
  }

  async function toggleScouted(artist: Artist) {
    const nextValue = !artist.isScouted;
    setBusy(true);
    try {
      if (demo) setArtists((current) => current.map((item) => item.id === artist.id ? { ...item, isScouted: nextValue } : item));
      else await updateArtist(artist.id, { isScouted: nextValue });
      notify(`${artist.stageName} ${nextValue ? "repéré" : "désépinglé"} avec succès`);
    } catch {
      notify("Le statut repéré n’a pas pu être modifié");
    } finally { setBusy(false); }
  }

  async function removeArtist(artist: Artist) {
    setBusy(true);
    try {
      if (demo) setArtists((current) => current.filter((item) => item.id !== artist.id));
      else await deleteArtist(artist.id);
      notify(`${artist.stageName} retiré de votre roster`);
    } catch { notify("L’artiste n’a pas pu être supprimé"); } finally { setBusy(false); }
  }

  function exportArtists() {
    const rows = [["Nom de scène", "Nom complet", "Discipline", "Ville", "Contact", "Email", "Repéré", "Statut", "Prochaine action", "Budget mensuel", "Notes"], ...artists.map((artist) => [
      artist.stageName, artist.name, artist.discipline, artist.city, artist.contact, artist.email, artist.isScouted ? "Oui" : "Non",
      artistStatuses[artist.status], artist.nextAction, String(artist.monthlyBudget || 0) + " FCFA", artist.notes,
    ])];
    const csv = rows.map((row) => row.map((cell) => "\"" + String(cell).replaceAll("\"", "\"\"") + "\"").join(",")).join("\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "roster-artistes-whappy.csv";
    link.click();
    URL.revokeObjectURL(url);
    notify("Le roster a été exporté");
  }

  function openBranding(page: BusinessPage) {
    setBranding(page);
    setLogoFile(null); setCoverFile(null);
    setLogoPreview(page.logoUrl || ""); setCoverPreview(page.coverUrl || "");
    setCreator("branding");
  }

  function openCampaignCreator(pageId?: string) {
    if (!pages.length) {
      notify("Ajoutez une page professionnelle avant de lancer une campagne");
      setTab("pages");
      setCreator("page");
      return;
    }
    if (pageId) setSelectedPage(pageId);
    setCreator("campaign");
  }

  function chooseBrandAsset(kind: "logo" | "cover", file?: File) {
    if (!file) return;
    const maximum = kind === "logo" ? 5 * 1024 * 1024 : 8 * 1024 * 1024;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) { notify("Choisissez une image JPG, PNG ou WebP"); return; }
    if (file.size > maximum) { notify(kind === "logo" ? "Le logo doit faire moins de 5 Mo" : "La couverture doit faire moins de 8 Mo"); return; }
    const preview = URL.createObjectURL(file);
    if (kind === "logo") { setLogoFile(file); setLogoPreview(preview); }
    else { setCoverFile(file); setCoverPreview(preview); }
  }

  async function saveBranding(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!branding || (!logoFile && !coverFile)) { notify("Choisissez un logo ou une photo de couverture"); return; }
    if (demo) { notify("Connectez-vous pour enregistrer l’identité de votre business"); return; }
    setBusy(true);
    try {
      await updateBusinessBranding(userId, branding.id, { logo: logoFile || undefined, cover: coverFile || undefined });
      notify("Identité visuelle enregistrée"); setCreator(null); setBranding(null); setLogoFile(null); setCoverFile(null);
    } catch (error) {
      notify(error instanceof Error && error.message === "brand-asset-too-large" ? "Une image dépasse la taille autorisée" : "Les images n’ont pas pu être enregistrées");
    } finally { setBusy(false); }
  }

  return <div className="business-studio" lang="fr" spellCheck autoCorrect="on" autoCapitalize="sentences">
    {tab === "overview" && <article className="business-wepi-card"><header><div><span className="wepi-card-mark">W</span><div><small>WHAPPY INTELLIGENCE</small><h3>WEPI IA pour votre Business</h3></div></div><b>ASSISTANT 24/7</b></header><p>Accueillez les demandes entrantes, répondez selon vos consignes et gardez la main sur chaque conversation.</p><div><span>✓ Réponses adaptées à votre activité</span><span>✓ Validation humaine disponible</span><span>✓ Activation instantanée</span></div><button onClick={() => setTab("wepi")}>Ouvrir l’interface WEPI ↗</button></article>}
    <section className="business-hero"><div><span>WHAPPY BUSINESS SUITE</span><h2>Transformez votre audience<br/><em>en activité durable.</em></h2><p>Créez votre présence professionnelle, diffusez vos campagnes et mesurez uniquement des interactions réelles.</p><div><button onClick={() => { setEditing(null); setCreator("page"); }}>＋ Créer une page</button><button className="secondary" onClick={() => { setEditingArtist(null); setCreator("artist"); }}>♬ Gérer mes artistes</button><button className="secondary" onClick={() => openCampaignCreator()}>✦ Lancer une publicité</button></div></div><aside><small>PERFORMANCE GLOBALE</small><strong>{metrics.impressions}</strong><span>impressions vérifiées</span><div><p><b>{metrics.clicks}</b> clics</p><p><b>{metrics.ctr}%</b> taux de clic</p></div></aside></section>
    <nav className="business-tabs">{(["overview", "pages", "artists", "ads", "tickets", "wepi"] as const).map((item) => <button className={tab === item ? "active" : ""} key={item} onClick={() => setTab(item)}>{item === "overview" ? "Vue d’ensemble" : item === "pages" ? `Mes pages (${visiblePages.length})` : item === "artists" ? `Artistes (${visibleArtists.length})` : item === "ads" ? `Publicités (${visibleCampaigns.length})` : item === "tickets" ? "Billetterie" : "WEPI IA"}</button>)}</nav>
    {tab === "overview" && <section className="business-overview"><div className="business-metrics"><article><span>PAGES ACTIVES</span><strong>{pages.filter((page) => page.status === "active").length}</strong><small>Entreprises et créateurs</small></article><article><span>CAMPAGNES ACTIVES</span><strong>{metrics.active}</strong><small>Diffusées dans Moments</small></article><article><span>IMPRESSIONS RÉELLES</span><strong>{metrics.impressions}</strong><small>Une personne par jour</small></article><article><span>CLICS ENREGISTRÉS</span><strong>{metrics.clicks}</strong><small>Actions sur vos publicités</small></article></div><div className="business-onboarding"><header><div><small>PARCOURS PROFESSIONNEL</small><h3>Votre croissance, étape par étape</h3></div><b>{pages.length ? campaigns.length ? "3/3" : "2/3" : "1/3"}</b></header><article className="done"><span>✓</span><div><strong>Compte Whappy vérifié</strong><small>{userName} · propriétaire identifié</small></div></article><article className={pages.length ? "done" : ""}><span>{pages.length ? "✓" : "2"}</span><div><strong>Créer une page professionnelle</strong><small>Identité, catégorie, ville et contact</small></div><button onClick={() => setCreator("page")}>{pages.length ? "Ajouter" : "Commencer"}</button></article><article className={campaigns.length ? "done" : ""}><span>{campaigns.length ? "✓" : "3"}</span><div><strong>Lancer une campagne</strong><small>Budget transparent et statistiques réelles</small></div><button onClick={() => openCampaignCreator()}>{campaigns.length ? "Nouvelle" : "Configurer"}</button></article></div><div className="artist-summary"><div><small>ROSTER ARTISTIQUE</small><h3>{artists.length ? `${artists.length} artiste${artists.length > 1 ? "s" : ""} accompagné${artists.length > 1 ? "s" : ""}` : "Construisez votre roster"}</h3><p>{artists.length ? `${artistMetrics.active} actif${artistMetrics.active > 1 ? "s" : ""} · ${artistMetrics.nextActions} prochain${artistMetrics.nextActions > 1 ? "s" : ""} rendez-vous` : "Centralisez les contacts et les prochaines actions de vos talents."}</p></div><button onClick={() => setTab("artists")}>{artists.length ? "Ouvrir le roster →" : "Ajouter un artiste →"}</button></div></section>}
    {tab === "pages" && <section className="business-pages"><header><div><small>VOS IDENTITÉS PUBLIQUES</small><h3>Pages professionnelles</h3></div><button onClick={() => { setEditing(null); setCreator("page"); }}>＋ Nouvelle page</button></header><div>{pages.map((page) => { const missing = [!page.logoUrl, !page.coverUrl, !page.bio, !page.phone && !page.website].filter(Boolean).length; return <article key={page.id}><div className={`business-page-cover${page.coverUrl ? " has-photo" : ""}`} style={page.coverUrl ? { backgroundImage: `linear-gradient(180deg,rgba(3,30,42,.04),rgba(3,30,42,.68)),url(${page.coverUrl})` } : undefined}>{page.logoUrl ? <img src={page.logoUrl} alt={`Logo de ${page.name}`}/> : <span>{page.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2)}</span>}<b>{pageTypes[page.type]}</b><button type="button" onClick={() => openBranding(page)}>✎ Logo & couverture</button></div><div className="business-page-heading"><small>@{page.handle} · {page.category}</small><span className={missing ? "incomplete" : "complete"}>{missing ? `${missing} élément${missing > 1 ? "s" : ""} à compléter` : "Profil complet"}</span></div><h3>{page.name}</h3><p>{page.bio || "Présentez votre activité et ce qui vous rend unique."}</p><div className="business-page-meta"><span>⌖ {page.city || "Zone non renseignée"}</span><span>◎ {page.followers} abonnés</span></div><section className="business-contact-card"><header><span>CONTACT</span>{!page.phone && !page.website && <button type="button" onClick={() => { setEditing(page); setCreator("page"); }}>＋ Ajouter</button>}</header>{page.phone || page.website ? <div>{page.phone && <a href={`tel:${page.phone.replace(/[^+\d]/g, "")}`}><b>☎</b><span><small>Téléphone / WhatsApp</small><strong>{page.phone}</strong></span></a>}{page.website && <a href={page.website} target="_blank" rel="noreferrer"><b>↗</b><span><small>Site ou lien public</small><strong>{page.website.replace(/^https?:\/\//, "")}</strong></span></a>}</div> : <p>Ajoutez un numéro ou un lien afin que les clients puissent vous contacter.</p>}</section><footer><button onClick={() => openCampaignCreator(page.id)}>✦ Promouvoir</button><button onClick={() => { setEditing(page); setCreator("page"); }}>Modifier</button><button className="danger" disabled={busy} onClick={() => removePage(page)}>Supprimer</button></footer></article>; })}{!pages.length && <div className="business-empty"><span>▣</span><h3>Créez votre première page</h3><p>Entreprise, créateur ou organisation : présentez votre identité sur WHAPPY.</p><button onClick={() => setCreator("page")}>＋ Créer ma page</button></div>}</div></section>}
    {tab === "artists" && (
      <section className="artists-space">
        <header>
          <div>
            <small>MANAGEMENT ARTISTIQUE</small>
            <h3>Mon roster</h3>
            <p>Suivez vos talents, leurs prochaines actions et les ressources à prévoir.</p>
          </div>
          <div className="artist-header-actions">
            <button className="artist-export" disabled={!artists.length} onClick={exportArtists}>⇩ Exporter</button>
            <button onClick={() => { setEditingArtist(null); setCreator("artist"); }}>＋ Ajouter un artiste</button>
          </div>
        </header>
        <div className="artist-metrics">
          <article><span>ARTISTES</span><strong>{artists.length}</strong><small>Dans votre roster</small></article>
          <article><span>REPÈRES</span><strong>{artistMetrics.scouted}</strong><small>Talents repérés</small></article>
          <article><span>ACTIFS</span><strong>{artistMetrics.active}</strong><small>Accompagnement en cours</small></article>
          <article><span>À SUIVRE</span><strong>{artistMetrics.nextActions}</strong><small>Prochaines actions renseignées</small></article>
          <article><span>BUDGET MENSUEL</span><strong>{artistMetrics.budget.toLocaleString("fr-FR")}</strong><small>FCFA prévus</small></article>
        </div>
        <div className="artist-toolbar">
          <div className="artist-filters">
            {(["all", "active", "prospect", "paused", "archived", "scouted"] as const).map((value) => (
              <button key={value} className={artistFilter === value ? "active" : ""} onClick={() => setArtistFilter(value)}>
                {value === "all" ? "Tous" : value === "scouted" ? "Repérés" : artistStatuses[value]}
              </button>
            ))}
          </div>
          <span>{visibleArtists.length} talent{visibleArtists.length > 1 ? "s" : ""}</span>
          <small>{demo ? "Mode démonstration locale" : "Synchronisé avec votre compte"}</small>
        </div>
        <div className="artist-grid">
          {visibleArtists.map((artist) => (
            <article className="artist-card" key={artist.id}>
              <header>
                <span className={`artist-avatar ${artist.status}`}>{artist.stageName.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase()}</span>
                <div>
                  <h3>{artist.stageName}</h3>
                  <small>{artist.name}{artist.name !== artist.stageName ? " · " : ""}{artist.discipline}</small>
                </div>
                <span className={`artist-status ${artist.status}`}>{artistStatuses[artist.status]}</span>
                <span className={`artist-scouted ${artist.isScouted ? "yes" : "no"}`}>{artist.isScouted ? "Repéré" : "À repérer"}</span>
              </header>
              <div className="artist-details">
                <span>⌖ {artist.city || "Ville non renseignée"}</span>
                <span>☎ {artist.contact || artist.email || "Contact à renseigner"}</span>
                <span>◷ {artist.nextAction || "Aucune action planifiée"}</span>
                <span>◆ {artist.monthlyBudget ? `${artist.monthlyBudget.toLocaleString("fr-FR")} FCFA / mois` : "Budget à définir"}</span>
              </div>
              {artist.notes && <p className="artist-notes">{artist.notes}</p>}
              <footer>
                {artist.contact && <a className="artist-contact" href={"tel:" + artist.contact.replace(/[^+\d]/g, "")}>☎ Appeler</a>}
                {!artist.contact && artist.email && <a className="artist-contact" href={"mailto:" + artist.email}>✉ Email</a>}
                <button onClick={() => { setEditingArtist(artist); setCreator("artist"); }}>✎ Modifier la fiche</button>
                <button className={artist.isScouted ? "artist-unscout" : "artist-scout"} onClick={() => toggleScouted(artist)} disabled={busy}>
                  {artist.isScouted ? "Retirer repère" : "Repérer"}
                </button>
                <select aria-label={`Modifier le statut de ${artist.stageName}`} value={artist.status} disabled={busy} onChange={(event) => changeArtistStatus(artist, event.target.value as ArtistStatus)}>
                  {Object.entries(artistStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
                <button className="danger" disabled={busy} onClick={() => removeArtist(artist)}>Supprimer</button>
              </footer>
            </article>
          ))}
          {!visibleArtists.length && (
            <div className="business-empty">
              <span>♬</span>
              <h3>{artists.length ? "Aucun artiste ne correspond" : "Votre roster est vide"}</h3>
              <p>{artists.length ? "Modifiez votre recherche pour retrouver un talent." : "Ajoutez votre premier talent pour centraliser son suivi."}</p>
              {!artists.length && <button onClick={() => { setEditingArtist(null); setCreator("artist"); }}>＋ Ajouter mon premier artiste</button>}
            </div>
          )}
        </div>
      </section>
    )}
    {tab === "ads" && <section className="campaigns-space">
      <header className="ads-header">
        <div><small>WHAPPY ADS · BUSINESS</small><h3>Publicités comme WhatsApp Business</h3><p>Créez une annonce sponsorisée qui envoie vers un message, une page, un appel ou un lien.</p></div>
        <button onClick={() => openCampaignCreator()}>✦ Nouvelle publicité</button>
      </header>
      <div className="ads-playbooks">{adPlaybooks.map((item) => <article key={item.title}><span>{adPlacements[item.placement]}</span><h4>{item.title}</h4><p>{item.text}</p><button onClick={() => openCampaignCreator()}>Utiliser ce modèle →</button></article>)}</div>
      <div className="campaign-filters">{([["all", "Toutes"], ["active", "En diffusion"], ["paused", "En pause"]] as const).map(([value, label]) => <button key={value} className={adFilter === value ? "active" : ""} onClick={() => setAdFilter(value)}>{label} <b>{value === "all" ? campaigns.length : campaigns.filter((campaign) => campaign.status === value).length}</b></button>)}</div><div className="campaign-table"><div className="campaign-row campaign-labels"><span>Publicité</span><span>Diffusion</span><span>Budget</span><span>Résultats</span><span>Actions</span></div>{visibleCampaigns.map((campaign) => { const campaignEvents = events.filter((event) => event.campaignId === campaign.id); const impressions = campaignEvents.filter((event) => event.type === "impression").length; const clicks = campaignEvents.filter((event) => event.type === "click").length; const placement = campaign.placement || "profile_story"; const destination = campaign.destination || "message"; const estimatedReach = campaign.estimatedReach || Math.max(120, Math.round((campaign.dailyBudget / 500) * campaign.days * 140)); const progress = Math.min(100, Math.round((impressions / Math.max(1, estimatedReach)) * 100)); return <article className="campaign-row" key={campaign.id}><div><small>{campaign.pageName} · {objectives[campaign.objective]}</small><strong>{campaign.title}</strong><p>{campaign.audience} · {campaign.city}</p><em className="campaign-route">{adPlacements[placement]} → {adDestinations[destination]}</em></div><span className={campaign.status}>{campaignStatuses[campaign.status]}</span><div><strong>{campaign.totalBudget.toLocaleString("fr-FR")} FCFA</strong><small>{campaign.dailyBudget.toLocaleString("fr-FR")} × {campaign.days} jours</small></div><div><strong>{impressions} impressions</strong><small>{clicks} clic{clicks > 1 ? "s" : ""} · portée estimée {estimatedReach.toLocaleString("fr-FR")}</small><div className="campaign-progress"><i style={{ width: `${progress}%` }}/></div></div><div><button disabled={busy} onClick={() => changeCampaign(campaign)}>{campaign.status === "active" ? "Pause" : "Activer"}</button><button className="danger" disabled={busy} onClick={async () => { setBusy(true); try { await deleteAdCampaign(campaign.id); notify("Campagne supprimée"); } catch { notify("Suppression impossible"); } finally { setBusy(false); } }}>Supprimer</button></div></article>; })}{!visibleCampaigns.length && <div className="business-empty"><span>✦</span><h3>Aucune publicité dans ce filtre</h3><p>Choisissez une page, un placement, une audience et un budget quotidien.</p><button onClick={() => openCampaignCreator()}>Créer une publicité</button></div>}</div>
    </section>}
    {tab === "tickets" && <TicketBulkPanel userId={userId} notify={notify} demo={demo} pages={pages.map((page) => ({ id: page.id, name: page.name }))}/>} 
    {tab === "wepi" && <section className="business-wepi-space"><header><div><small>WHAPPY BUSINESS · IA</small><h3>Répondez à vos utilisateurs avec WEPI</h3><p>Activez l’assistant pour accueillir les demandes entrantes pendant que vous développez votre activité.</p></div><button onClick={() => setTab("overview")}>← Vue d’ensemble</button></header><WepiAssistant userId={userId} userName={userName} businessName={pages[0]?.name || ""} cloud={!demo} notify={notify}/></section>}
    {creator && <div className="business-modal"><button className="business-modal-dismiss" onClick={() => { setCreator(null); setEditing(null); setEditingArtist(null); setBranding(null); }} aria-label="Fermer"/>{creator === "page" ? <form onSubmit={publishPage}><header><div><small>PAGE PROFESSIONNELLE</small><h3>{editing ? "Modifier la page" : "Créer votre identité"}</h3></div><button type="button" onClick={() => { setCreator(null); setEditing(null); }}>×</button></header><label>Nom public<input name="name" required minLength={2} maxLength={80} defaultValue={editing?.name} placeholder="Ex. Mokabi Studio"/></label><div className="business-form-row"><label>Type<select name="type" defaultValue={editing?.type || "business"}><option value="business">Entreprise</option><option value="creator">Créateur de contenu</option><option value="organization">Organisation</option></select></label><label>Catégorie<input name="category" required defaultValue={editing?.category} placeholder="Mode, média, restauration…"/></label></div><label>Présentation<textarea name="bio" maxLength={400} defaultValue={editing?.bio} placeholder="Décrivez votre activité, votre univers et votre promesse."/></label><div className="business-form-row"><label>Ville<input name="city" required defaultValue={editing?.city} placeholder="Brazzaville"/></label><label>Téléphone professionnel<input name="phone" inputMode="tel" defaultValue={editing?.phone} placeholder="+242…"/></label></div><label>Site ou lien public<input name="website" type="url" defaultValue={editing?.website} placeholder="https://…"/></label><button className="business-submit" disabled={busy}>{busy ? "Enregistrement…" : editing ? "Enregistrer les changements" : "Publier la page →"}</button></form> : creator === "artist" ? <form onSubmit={publishArtist}><header><div><small>MANAGEMENT ARTISTIQUE</small><h3>{editingArtist ? "Modifier la fiche artiste" : "Ajouter un artiste"}</h3><p>Gardez les informations utiles à portée de main.</p></div><button type="button" onClick={() => { setCreator(null); setEditingArtist(null); }}>×</button></header><div className="business-form-row"><label>Nom de scène<input name="stageName" required maxLength={100} defaultValue={editingArtist?.stageName} placeholder="Ex. Grâce M."/></label><label>Nom complet<input name="name" defaultValue={editingArtist?.name} placeholder="Ex. Grâce Mpassi"/></label></div><div className="business-form-row"><label>Discipline / style<input name="discipline" required maxLength={80} defaultValue={editingArtist?.discipline} placeholder="Afro-pop, danse, photo…"/></label><label>Ville<input name="city" defaultValue={editingArtist?.city} placeholder="Brazzaville"/></label></div><div className="business-form-row"><label>Téléphone / WhatsApp<input name="contact" inputMode="tel" defaultValue={editingArtist?.contact} placeholder="+242…"/></label><label>Email<input name="email" type="email" defaultValue={editingArtist?.email} placeholder="artiste@email.com"/></label></div><div className="business-form-row"><label>Statut<select name="status" defaultValue={editingArtist?.status || "prospect"}>{Object.entries(artistStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Prochaine action<input name="nextAction" defaultValue={editingArtist?.nextAction} placeholder="Ex. Session studio · 24 août"/></label></div><div className="business-form-row"><label>Budget mensuel (FCFA)<input name="monthlyBudget" type="number" min={0} step={1000} defaultValue={editingArtist?.monthlyBudget || 0}/></label><label>Notes<textarea name="notes" maxLength={600} defaultValue={editingArtist?.notes} placeholder="Objectifs, priorités, notes de suivi…"/></label></div><button className="business-submit" disabled={busy}>{busy ? "Enregistrement…" : editingArtist ? "Enregistrer la fiche →" : "Ajouter au roster →"}</button></form> : creator === "branding" ? <form className="business-branding-form" onSubmit={saveBranding}><header><div><small>IDENTITÉ VISUELLE</small><h3>Logo et couverture</h3><p>{branding?.name}</p></div><button type="button" onClick={() => { setCreator(null); setBranding(null); }}>×</button></header><div className="business-brand-preview"><div className="business-brand-cover" style={coverPreview ? { backgroundImage: `linear-gradient(180deg,rgba(3,30,42,.02),rgba(3,30,42,.55)),url(${coverPreview})` } : undefined}><label><span>{coverPreview ? "Changer la couverture" : "Ajouter une couverture"}</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("cover", event.target.files?.[0])}/></label><div className="business-brand-logo">{logoPreview ? <img src={logoPreview} alt="Aperçu du logo"/> : <span>{branding?.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2)}</span>}<label aria-label="Changer le logo">✎<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("logo", event.target.files?.[0])}/></label></div></div><strong>{branding?.name}</strong><small>@{branding?.handle}</small></div><div className="business-brand-options"><label><b>Logo du business</b><span>Format carré recommandé · PNG, JPG ou WebP · 5 Mo max.</span><em>{logoFile ? logoFile.name : branding?.logoUrl ? "Logo actuel conservé" : "Aucun logo"}</em><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("logo", event.target.files?.[0])}/></label><label><b>Photo de couverture</b><span>Format paysage recommandé · PNG, JPG ou WebP · 8 Mo max.</span><em>{coverFile ? coverFile.name : branding?.coverUrl ? "Couverture actuelle conservée" : "Aucune couverture"}</em><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("cover", event.target.files?.[0])}/></label></div><p className="business-brand-note">Les nouvelles images apparaîtront sur votre page professionnelle dès l’enregistrement.</p><button className="business-submit" disabled={busy || (!logoFile && !coverFile)}>{busy ? "Importation…" : "Enregistrer l’identité visuelle →"}</button></form> : <AdCampaignCreator pages={pages} selectedPage={selectedPage || pages[0]?.id || ""} busy={busy} onSelectedPage={setSelectedPage} onSubmit={publishCampaign} onClose={() => setCreator(null)} />}</div>}
  </div>;
}

function AdCampaignCreator({ pages, selectedPage, busy, onSelectedPage, onSubmit, onClose }: { pages: BusinessPage[]; selectedPage: string; busy: boolean; onSelectedPage: (pageId: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onClose: () => void }) {
  const page = pages.find((item) => item.id === selectedPage) || pages[0];
  const [objective, setObjective] = useState<AdCampaign["objective"]>("messages");
  const [placement, setPlacement] = useState<AdCampaign["placement"]>("profile_story");
  const [destination, setDestination] = useState<AdCampaign["destination"]>("message");
  const [title, setTitle] = useState(page ? `Découvrez ${page.name}` : "Votre offre Business");
  const [creative, setCreative] = useState("Écrivez-nous pour recevoir le catalogue, les prix et les disponibilités.");
  const [dailyBudget, setDailyBudget] = useState(2000);
  const [days, setDays] = useState(7);
  const estimatedReach = Math.max(120, Math.round((dailyBudget / 500) * days * (placement === "profile_story" ? 180 : placement === "inbox" ? 110 : placement === "live" ? 90 : 140)));
  const cta = destination === "message" ? "Envoyer un message" : destination === "call" ? "Appeler" : destination === "website" ? "Ouvrir le lien" : "Voir la page";

  useEffect(() => {
    if (!page) return;
    const timer = window.setTimeout(() => setTitle((current) => current === "Votre offre Business" || current.startsWith("Découvrez ") ? `Découvrez ${page.name}` : current), 0);
    return () => window.clearTimeout(timer);
  }, [page]);

  return <form className="ad-creator-form" onSubmit={onSubmit}>
    <header><div><small>WHAPPY ADS</small><h3>Créer une publicité Business</h3><p>Un parcours simple : objectif, emplacement, destination, budget et aperçu.</p></div><button type="button" onClick={onClose}>×</button></header>
    <div className="ad-creator-layout">
      <section className="ad-creator-fields">
        <label>Page à promouvoir<select name="pageId" required value={selectedPage} onChange={(event) => onSelectedPage(event.target.value)}>{pages.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <div className="business-form-row"><label>Objectif<select name="objective" value={objective} onChange={(event) => setObjective(event.target.value as AdCampaign["objective"])}><option value="messages">Recevoir des messages</option><option value="sales">Générer des ventes</option><option value="reach">Notoriété</option><option value="traffic">Obtenir des visites</option></select></label><label>Destination<select name="destination" value={destination} onChange={(event) => setDestination(event.target.value as AdCampaign["destination"])}><option value="message">Message client</option><option value="page">Page Business</option><option value="call">Appel direct</option><option value="website">Lien externe</option></select></label></div>
        <label>Placement de la publicité<select name="placement" value={placement} onChange={(event) => setPlacement(event.target.value as AdCampaign["placement"])}><option value="profile_story">Story sur le profil</option><option value="inbox">Découverte Business</option><option value="market">Marché</option><option value="live">Lives</option></select></label>
        <input type="hidden" name="cta" value={cta} />
        <label>Titre de la publicité<input name="title" required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Une promesse claire et utile"/></label>
        <label>Texte publicitaire<textarea name="creative" required maxLength={600} value={creative} onChange={(event) => setCreative(event.target.value)} placeholder="Présentez votre offre sans promesse trompeuse."/></label>
        <div className="business-form-row"><label>Audience<input name="audience" required placeholder="Ex. Clients proches, 18–45 ans, intéressés par vos produits"/></label><label>Zone<input name="city" required defaultValue={page?.city || ""} placeholder="Brazzaville"/></label></div>
        <div className="business-form-row"><label>Téléphone de contact<input name="phone" inputMode="tel" defaultValue={page?.phone || ""} placeholder="+242…"/></label><label>Lien public<input name="link" type="url" defaultValue={page?.website || ""} placeholder="https://…"/></label></div>
        <div className="business-form-row"><label>Budget quotidien (FCFA)<input name="dailyBudget" required type="number" min={500} step={500} value={dailyBudget} onChange={(event) => setDailyBudget(Number(event.target.value || 500))}/></label><label>Durée (jours)<input name="days" required type="number" min={1} max={90} value={days} onChange={(event) => setDays(Number(event.target.value || 1))}/></label></div>
        <p className="campaign-disclaimer">◆ Mode expérimental : aucune facturation automatique tant qu’un paiement réel n’est pas connecté. La publicité est marquée sponsorisée.</p>
        <button className="business-submit" disabled={busy || !pages.length}>{busy ? "Activation…" : "Activer la publicité →"}</button>
      </section>
      <aside className="ad-live-preview" aria-label="Aperçu de la publicité">
        <small>APERÇU SPONSORISÉ</small>
        <article>
          <header><span>{page?.logoUrl ? <img src={page.logoUrl} alt=""/> : (page?.name || "W").slice(0, 2).toUpperCase()}</span><div><strong>{page?.name || "Votre Business"}</strong><em>Publicité · {adPlacements[placement]}</em></div></header>
          <div className="ad-preview-media"><b>{placement === "profile_story" ? "STORY PROFIL" : placement === "live" ? "LIVE" : placement === "market" ? "OFFRE" : "DÉCOUVERTE"}</b></div>
          <h4>{title || "Titre de votre publicité"}</h4>
          <p>{creative || "Votre message publicitaire apparaîtra ici."}</p>
          <button type="button">{cta}</button>
        </article>
        <div className="ad-estimate"><span>Portée estimée</span><strong>{estimatedReach.toLocaleString("fr-FR")} personnes</strong><small>Budget total : {(dailyBudget * days).toLocaleString("fr-FR")} FCFA · {days} jour{days > 1 ? "s" : ""}</small></div>
      </aside>
    </div>
  </form>;
}
