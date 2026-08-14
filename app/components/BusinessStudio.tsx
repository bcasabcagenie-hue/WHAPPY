"use client";

/* Les images proviennent de fichiers choisis par l’utilisateur et de prévisualisations blob locales. */
/* eslint-disable @next/next/no-img-element */

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createAdCampaign, createBusinessPage, deleteAdCampaign, deleteBusinessPage, updateBusinessBranding, updateBusinessPage, updateCampaignStatus, watchBusinessPages, watchOwnerAdEvents, watchOwnerCampaigns, type AdCampaign, type AdEvent, type BusinessPage } from "@/lib/whappy-business";

const pageTypes = { business: "Entreprise", creator: "Créateur", organization: "Organisation" } as const;
const objectives = { reach: "Notoriété", messages: "Messages", traffic: "Visites", sales: "Ventes" } as const;
const campaignStatuses = { draft: "Brouillon", active: "En diffusion", paused: "En pause", completed: "Terminée" } as const;

export function BusinessStudio({ userId, userName, search, notify, demo = false }: { userId: string; userName: string; search: string; notify: (text: string) => void; demo?: boolean }) {
  const [tab, setTab] = useState<"overview" | "pages" | "ads">("overview");
  const [pages, setPages] = useState<BusinessPage[]>([]);
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
  const [events, setEvents] = useState<AdEvent[]>([]);
  const [creator, setCreator] = useState<"page" | "campaign" | "branding" | null>(null);
  const [selectedPage, setSelectedPage] = useState("");
  const [editing, setEditing] = useState<BusinessPage | null>(null);
  const [branding, setBranding] = useState<BusinessPage | null>(null);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState("");
  const [coverPreview, setCoverPreview] = useState("");
  const [busy, setBusy] = useState(false);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => () => { if (logoPreview.startsWith("blob:")) URL.revokeObjectURL(logoPreview); }, [logoPreview]);
  useEffect(() => () => { if (coverPreview.startsWith("blob:")) URL.revokeObjectURL(coverPreview); }, [coverPreview]);
  useEffect(() => demo ? undefined : watchBusinessPages(userId, setPages, () => notifyRef.current("Vos pages sont momentanément indisponibles")), [demo,userId]);
  useEffect(() => demo ? undefined : watchOwnerCampaigns(userId, setCampaigns, () => notifyRef.current("Vos campagnes sont momentanément indisponibles")), [demo,userId]);
  useEffect(() => demo ? undefined : watchOwnerAdEvents(userId, setEvents, () => notifyRef.current("Les statistiques sont momentanément indisponibles")), [demo,userId]);

  const metrics = useMemo(() => {
    const impressions = events.filter((event) => event.type === "impression").length;
    const clicks = events.filter((event) => event.type === "click").length;
    return { impressions, clicks, ctr: impressions ? (clicks / impressions * 100).toFixed(1) : "0.0", active: campaigns.filter((campaign) => campaign.status === "active").length };
  }, [campaigns, events]);
  const searchValue = search.trim().toLowerCase();
  const visiblePages = useMemo(() => pages.filter((page) => !searchValue || `${page.name} ${page.handle} ${page.category} ${page.city} ${page.phone} ${page.website}`.toLowerCase().includes(searchValue)), [pages, searchValue]);
  const visibleCampaigns = useMemo(() => campaigns.filter((campaign) => !searchValue || `${campaign.title} ${campaign.pageName} ${campaign.audience} ${campaign.city}`.toLowerCase().includes(searchValue)), [campaigns, searchValue]);

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
    setBusy(true);
    try {
      await createAdCampaign(userId, {
        pageId: page.id, pageName: page.name,
        objective: String(form.get("objective") || "reach") as AdCampaign["objective"],
        title: String(form.get("title") || ""), creative: String(form.get("creative") || ""), cta: String(form.get("cta") || "En savoir plus"), audience: String(form.get("audience") || ""), city: String(form.get("city") || page.city), dailyBudget: Number(form.get("dailyBudget") || 500), days: Number(form.get("days") || 7),
      });
      notify("Campagne activée dans le fil WHAPPY"); setCreator(null); setTab("ads");
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

  function openBranding(page: BusinessPage) {
    setBranding(page);
    setLogoFile(null); setCoverFile(null);
    setLogoPreview(page.logoUrl || ""); setCoverPreview(page.coverUrl || "");
    setCreator("branding");
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
    <section className="business-hero"><div><span>WHAPPY BUSINESS SUITE</span><h2>Transformez votre audience<br/><em>en activité durable.</em></h2><p>Créez votre présence professionnelle, diffusez vos campagnes et mesurez uniquement des interactions réelles.</p><div><button onClick={() => { setEditing(null); setCreator("page"); }}>＋ Créer une page</button><button className="secondary" disabled={!pages.length} onClick={() => setCreator("campaign")}>✦ Lancer une publicité</button></div></div><aside><small>PERFORMANCE GLOBALE</small><strong>{metrics.impressions}</strong><span>impressions vérifiées</span><div><p><b>{metrics.clicks}</b> clics</p><p><b>{metrics.ctr}%</b> taux de clic</p></div></aside></section>
    <nav className="business-tabs">{(["overview", "pages", "ads"] as const).map((item) => <button className={tab === item ? "active" : ""} key={item} onClick={() => setTab(item)}>{item === "overview" ? "Vue d’ensemble" : item === "pages" ? `Mes pages (${visiblePages.length})` : `Publicités (${visibleCampaigns.length})`}</button>)}</nav>
    {tab === "overview" && <section className="business-overview"><div className="business-metrics"><article><span>PAGES ACTIVES</span><strong>{pages.filter((page) => page.status === "active").length}</strong><small>Entreprises et créateurs</small></article><article><span>CAMPAGNES ACTIVES</span><strong>{metrics.active}</strong><small>Diffusées dans Moments</small></article><article><span>IMPRESSIONS RÉELLES</span><strong>{metrics.impressions}</strong><small>Une personne par jour</small></article><article><span>CLICS ENREGISTRÉS</span><strong>{metrics.clicks}</strong><small>Actions sur vos publicités</small></article></div><div className="business-onboarding"><header><div><small>PARCOURS PROFESSIONNEL</small><h3>Votre croissance, étape par étape</h3></div><b>{pages.length ? campaigns.length ? "3/3" : "2/3" : "1/3"}</b></header><article className="done"><span>✓</span><div><strong>Compte Whappy vérifié</strong><small>{userName} · propriétaire identifié</small></div></article><article className={pages.length ? "done" : ""}><span>{pages.length ? "✓" : "2"}</span><div><strong>Créer une page professionnelle</strong><small>Identité, catégorie, ville et contact</small></div><button onClick={() => setCreator("page")}>{pages.length ? "Ajouter" : "Commencer"}</button></article><article className={campaigns.length ? "done" : ""}><span>{campaigns.length ? "✓" : "3"}</span><div><strong>Lancer une campagne</strong><small>Budget transparent et statistiques réelles</small></div><button disabled={!pages.length} onClick={() => setCreator("campaign")}>{campaigns.length ? "Nouvelle" : "Configurer"}</button></article></div></section>}
    {tab === "pages" && <section className="business-pages"><header><div><small>VOS IDENTITÉS PUBLIQUES</small><h3>Pages professionnelles</h3></div><button onClick={() => { setEditing(null); setCreator("page"); }}>＋ Nouvelle page</button></header><div>{pages.map((page) => { const missing = [!page.logoUrl, !page.coverUrl, !page.bio, !page.phone && !page.website].filter(Boolean).length; return <article key={page.id}><div className={`business-page-cover${page.coverUrl ? " has-photo" : ""}`} style={page.coverUrl ? { backgroundImage: `linear-gradient(180deg,rgba(3,30,42,.04),rgba(3,30,42,.68)),url(${page.coverUrl})` } : undefined}>{page.logoUrl ? <img src={page.logoUrl} alt={`Logo de ${page.name}`}/> : <span>{page.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2)}</span>}<b>{pageTypes[page.type]}</b><button type="button" onClick={() => openBranding(page)}>✎ Logo & couverture</button></div><div className="business-page-heading"><small>@{page.handle} · {page.category}</small><span className={missing ? "incomplete" : "complete"}>{missing ? `${missing} élément${missing > 1 ? "s" : ""} à compléter` : "Profil complet"}</span></div><h3>{page.name}</h3><p>{page.bio || "Présentez votre activité et ce qui vous rend unique."}</p><div className="business-page-meta"><span>⌖ {page.city || "Zone non renseignée"}</span><span>◎ {page.followers} abonnés</span></div><section className="business-contact-card"><header><span>CONTACT</span>{!page.phone && !page.website && <button type="button" onClick={() => { setEditing(page); setCreator("page"); }}>＋ Ajouter</button>}</header>{page.phone || page.website ? <div>{page.phone && <a href={`tel:${page.phone.replace(/[^+\d]/g, "")}`}><b>☎</b><span><small>Téléphone / WhatsApp</small><strong>{page.phone}</strong></span></a>}{page.website && <a href={page.website} target="_blank" rel="noreferrer"><b>↗</b><span><small>Site ou lien public</small><strong>{page.website.replace(/^https?:\/\//, "")}</strong></span></a>}</div> : <p>Ajoutez un numéro ou un lien afin que les clients puissent vous contacter.</p>}</section><footer><button onClick={() => { setSelectedPage(page.id); setCreator("campaign"); }}>✦ Promouvoir</button><button onClick={() => { setEditing(page); setCreator("page"); }}>Modifier</button><button className="danger" disabled={busy} onClick={() => removePage(page)}>Supprimer</button></footer></article>; })}{!pages.length && <div className="business-empty"><span>▣</span><h3>Créez votre première page</h3><p>Entreprise, créateur ou organisation : présentez votre identité sur WHAPPY.</p><button onClick={() => setCreator("page")}>＋ Créer ma page</button></div>}</div></section>}
    {tab === "ads" && <section className="campaigns-space"><header><div><small>WHAPPY ADS</small><h3>Campagnes publicitaires</h3></div><button disabled={!pages.length} onClick={() => setCreator("campaign")}>✦ Nouvelle campagne</button></header><div className="campaign-table"><div className="campaign-row campaign-labels"><span>Campagne</span><span>Statut</span><span>Budget</span><span>Résultats</span><span>Actions</span></div>{campaigns.map((campaign) => { const campaignEvents = events.filter((event) => event.campaignId === campaign.id); const impressions = campaignEvents.filter((event) => event.type === "impression").length; const clicks = campaignEvents.filter((event) => event.type === "click").length; return <article className="campaign-row" key={campaign.id}><div><small>{campaign.pageName} · {objectives[campaign.objective]}</small><strong>{campaign.title}</strong><p>{campaign.audience} · {campaign.city}</p></div><span className={campaign.status}>{campaignStatuses[campaign.status]}</span><div><strong>{campaign.totalBudget.toLocaleString("fr-FR")} FCFA</strong><small>{campaign.dailyBudget.toLocaleString("fr-FR")} × {campaign.days} jours</small></div><div><strong>{impressions} impressions</strong><small>{clicks} clic{clicks > 1 ? "s" : ""}</small></div><div><button disabled={busy} onClick={() => changeCampaign(campaign)}>{campaign.status === "active" ? "Pause" : "Activer"}</button><button className="danger" disabled={busy} onClick={async () => { setBusy(true); try { await deleteAdCampaign(campaign.id); notify("Campagne supprimée"); } catch { notify("Suppression impossible"); } finally { setBusy(false); } }}>Supprimer</button></div></article>; })}{!campaigns.length && <div className="business-empty"><span>✦</span><h3>Aucune campagne</h3><p>Choisissez une page, une audience et un budget quotidien.</p><button disabled={!pages.length} onClick={() => setCreator("campaign")}>Créer une publicité</button></div>}</div></section>}
    {creator && <div className="business-modal"><button className="business-modal-dismiss" onClick={() => { setCreator(null); setEditing(null); setBranding(null); }} aria-label="Fermer"/>{creator === "page" ? <form onSubmit={publishPage}><header><div><small>PAGE PROFESSIONNELLE</small><h3>{editing ? "Modifier la page" : "Créer votre identité"}</h3></div><button type="button" onClick={() => { setCreator(null); setEditing(null); }}>×</button></header><label>Nom public<input name="name" required minLength={2} maxLength={80} defaultValue={editing?.name} placeholder="Ex. Mokabi Studio"/></label><div className="business-form-row"><label>Type<select name="type" defaultValue={editing?.type || "business"}><option value="business">Entreprise</option><option value="creator">Créateur de contenu</option><option value="organization">Organisation</option></select></label><label>Catégorie<input name="category" required defaultValue={editing?.category} placeholder="Mode, média, restauration…"/></label></div><label>Présentation<textarea name="bio" maxLength={400} defaultValue={editing?.bio} placeholder="Décrivez votre activité, votre univers et votre promesse."/></label><div className="business-form-row"><label>Ville<input name="city" required defaultValue={editing?.city} placeholder="Brazzaville"/></label><label>Téléphone professionnel<input name="phone" inputMode="tel" defaultValue={editing?.phone} placeholder="+242…"/></label></div><label>Site ou lien public<input name="website" type="url" defaultValue={editing?.website} placeholder="https://…"/></label><button className="business-submit" disabled={busy}>{busy ? "Enregistrement…" : editing ? "Enregistrer les changements" : "Publier la page →"}</button></form> : creator === "branding" ? <form className="business-branding-form" onSubmit={saveBranding}><header><div><small>IDENTITÉ VISUELLE</small><h3>Logo et couverture</h3><p>{branding?.name}</p></div><button type="button" onClick={() => { setCreator(null); setBranding(null); }}>×</button></header><div className="business-brand-preview"><div className="business-brand-cover" style={coverPreview ? { backgroundImage: `linear-gradient(180deg,rgba(3,30,42,.02),rgba(3,30,42,.55)),url(${coverPreview})` } : undefined}><label><span>{coverPreview ? "Changer la couverture" : "Ajouter une couverture"}</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("cover", event.target.files?.[0])}/></label><div className="business-brand-logo">{logoPreview ? <img src={logoPreview} alt="Aperçu du logo"/> : <span>{branding?.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2)}</span>}<label aria-label="Changer le logo">✎<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("logo", event.target.files?.[0])}/></label></div></div><strong>{branding?.name}</strong><small>@{branding?.handle}</small></div><div className="business-brand-options"><label><b>Logo du business</b><span>Format carré recommandé · PNG, JPG ou WebP · 5 Mo max.</span><em>{logoFile ? logoFile.name : branding?.logoUrl ? "Logo actuel conservé" : "Aucun logo"}</em><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("logo", event.target.files?.[0])}/></label><label><b>Photo de couverture</b><span>Format paysage recommandé · PNG, JPG ou WebP · 8 Mo max.</span><em>{coverFile ? coverFile.name : branding?.coverUrl ? "Couverture actuelle conservée" : "Aucune couverture"}</em><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => chooseBrandAsset("cover", event.target.files?.[0])}/></label></div><p className="business-brand-note">Les nouvelles images apparaîtront sur votre page professionnelle dès l’enregistrement.</p><button className="business-submit" disabled={busy || (!logoFile && !coverFile)}>{busy ? "Importation…" : "Enregistrer l’identité visuelle →"}</button></form> : <form onSubmit={publishCampaign}><header><div><small>WHAPPY ADS</small><h3>Lancer une campagne</h3></div><button type="button" onClick={() => setCreator(null)}>×</button></header><label>Page à promouvoir<select name="pageId" required value={selectedPage || pages[0]?.id || ""} onChange={(event) => setSelectedPage(event.target.value)}>{pages.map((page) => <option key={page.id} value={page.id}>{page.name}</option>)}</select></label><div className="business-form-row"><label>Objectif<select name="objective"><option value="reach">Notoriété</option><option value="messages">Recevoir des messages</option><option value="traffic">Obtenir des visites</option><option value="sales">Générer des ventes</option></select></label><label>Appel à l’action<select name="cta"><option>En savoir plus</option><option>Envoyer un message</option><option>Acheter maintenant</option><option>Voir la page</option></select></label></div><label>Titre de la publicité<input name="title" required maxLength={120} placeholder="Une promesse claire et utile"/></label><label>Texte publicitaire<textarea name="creative" required maxLength={600} placeholder="Présentez votre offre sans promesse trompeuse."/></label><div className="business-form-row"><label>Audience<input name="audience" required placeholder="Ex. Femmes et hommes 18–45 ans"/></label><label>Zone<input name="city" required defaultValue={pages.find((page) => page.id === selectedPage)?.city || pages[0]?.city} placeholder="Brazzaville"/></label></div><div className="business-form-row"><label>Budget quotidien (FCFA)<input name="dailyBudget" required type="number" min={500} step={500} defaultValue={2000}/></label><label>Durée (jours)<input name="days" required type="number" min={1} max={90} defaultValue={7}/></label></div><p className="campaign-disclaimer">◆ La campagne est diffusée dans WHAPPY. Aucun prélèvement automatique n’est effectué tant qu’un partenaire de paiement n’est pas connecté.</p><button className="business-submit" disabled={busy || !pages.length}>{busy ? "Activation…" : "Activer la campagne →"}</button></form>}</div>}
  </div>;
}
