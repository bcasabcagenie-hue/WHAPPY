"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  businessCurrencyLabels,
  createFounderEarning,
  createWhappyTeamMember,
  updateFounderEarningStatus,
  watchFounderEarnings,
  watchWhappyLiveStats,
  watchWhappyTeam,
  watchWhappyUserCount,
  type BusinessCurrency,
  type FounderEarning,
  type FounderEarningStatus,
  type WhappyTeamMember,
  type WhappyTeamStatus,
} from "@/lib/whappy-business";

const demoTeam: WhappyTeamMember[] = [
  { id: "demo-team-1", ownerId: "happy", name: "Maya L.", role: "Direction produit", contact: "+242 06 700 12 20", area: "Produit & vision", status: "active" },
  { id: "demo-team-2", ownerId: "happy", name: "Amina M.", role: "Communauté", contact: "+242 06 220 11 40", area: "Utilisateurs & retours", status: "active" },
  { id: "demo-team-3", ownerId: "happy", name: "Patrick N.", role: "Opérations", contact: "+242 05 410 20 20", area: "Partenaires & ventes", status: "invited" },
];

const demoEarnings: FounderEarning[] = [
  { id: "demo-earning-1", ownerId: "happy", title: "Abonnements Business", source: "Comptes professionnels", amount: 1250000, currency: "XAF", status: "processing", dueLabel: "Prochaine clôture · 31 août" },
  { id: "demo-earning-2", ownerId: "happy", title: "Campagnes sponsorisées", source: "WHAPPY Ads", amount: 680000, currency: "XAF", status: "pending", dueLabel: "En attente de validation" },
  { id: "demo-earning-3", ownerId: "happy", title: "Commissions Marketplace", source: "Transactions Whappy", amount: 315000, currency: "XAF", status: "paid", dueLabel: "Versé · 12 août" },
];

const earningStatusLabels: Record<FounderEarningStatus, string> = { pending: "À confirmer", processing: "En cours", paid: "Versé" };
const teamStatusLabels: Record<WhappyTeamStatus, string> = { active: "Actif", invited: "Invitation envoyée", paused: "En pause" };
const WHAPPY_DOWNLOAD_EVENTS_KEY = "whappy:download-events";
const WHAPPY_ADMIN_APK = { version: "1.9.4 native", size: "Universel", minimum: "Android 8.0+", url: "https://raw.githubusercontent.com/bcasabcagenie-hue/WHAPPY/codex/sites-deploy-lite/public/downloads/WAPI-Android-1.9.4-native.apk" } as const;
type DownloadEvent = { id: string; at: string; version: string; size: string; source: "button" | "direct"; userAgent?: string };
type StoreMetric = { label: string; value: string; detail: string; trend: string };
type StorePurchase = { id: string; title: string; kind: "Abonnement" | "Achat unique" | "Remboursement"; amount: number; status: "paid" | "pending" | "refunded"; source: string };
type StoreChannel = { name: string; users: number; conversion: string; tone: "organic" | "ads" | "direct" };

const demoStoreMetrics: StoreMetric[] = [
  { label: "INSTALLATIONS PLAY STORE", value: "12 840", detail: "Utilisateurs acquis · 30 jours", trend: "+18,4 %" },
  { label: "OUVERTURES APRÈS INSTALL", value: "9 126", detail: "First opens synchronisés Firebase", trend: "71 %" },
  { label: "DÉSINSTALLATIONS", value: "486", detail: "Perte utilisateur estimée", trend: "-3,7 %" },
  { label: "NOTE PLAY STORE", value: "4,7", detail: "Avis publics et signalements", trend: "328 avis" },
];

const demoStorePurchases: StorePurchase[] = [
  { id: "play-1", title: "WHAPPY Business Pro", kind: "Abonnement", amount: 1490000, status: "paid", source: "Google Play Billing" },
  { id: "play-2", title: "Pack cadeaux Live", kind: "Achat unique", amount: 420000, status: "paid", source: "In-app purchases" },
  { id: "play-3", title: "Boost publicité 7 jours", kind: "Achat unique", amount: 185000, status: "pending", source: "Paiement en attente" },
  { id: "play-4", title: "Remboursement campagne", kind: "Remboursement", amount: -65000, status: "refunded", source: "Voided purchase" },
];

const demoStoreChannels: StoreChannel[] = [
  { name: "Recherche Play Store", users: 6240, conversion: "34 %", tone: "organic" },
  { name: "Lien site WHAPPY", users: 2810, conversion: "42 %", tone: "direct" },
  { name: "Campagnes Google Ads", users: 1980, conversion: "21 %", tone: "ads" },
];

function formatFounderMoney(value: number, currency: BusinessCurrency) {
  const suffix = currency === "XAF" || currency === "XOF" ? "FCFA" : currency;
  return `${new Intl.NumberFormat("fr-FR").format(value)} ${suffix}`;
}

export function HappyFounderDashboard({ ownerId, userName, search, notify, demo = false }: { ownerId: string; userName: string; search: string; notify: (text: string) => void; demo?: boolean }) {
  const [userCount, setUserCount] = useState(demo ? 2486 : 0);
  const [liveStats, setLiveStats] = useState(demo ? { sessions: 3, viewers: 5267 } : { sessions: 0, viewers: 0 });
  const [team, setTeam] = useState<WhappyTeamMember[]>(demo ? demoTeam : []);
  const [earnings, setEarnings] = useState<FounderEarning[]>(demo ? demoEarnings : []);
  const [panel, setPanel] = useState<"team" | "earning" | null>(null);
  const [downloadEvents, setDownloadEvents] = useState<DownloadEvent[]>([]);
  const [storeConnected, setStoreConnected] = useState(false);
  const [busy, setBusy] = useState(false);
  const query = search.trim().toLowerCase();

  useEffect(() => {
    if (demo) return;
    const stopUsers = watchWhappyUserCount(setUserCount, () => notify("Le nombre d’utilisateurs est momentanément indisponible"));
    const stopLive = watchWhappyLiveStats(setLiveStats, () => notify("Les statistiques Live sont momentanément indisponibles"));
    return () => { stopUsers(); stopLive(); };
  }, [demo, notify]);

  useEffect(() => {
    if (demo) return;
    return watchWhappyTeam(ownerId, setTeam, () => notify("L’équipe Whappy est momentanément indisponible"));
  }, [demo, notify, ownerId]);

  useEffect(() => {
    if (demo) return;
    return watchFounderEarnings(ownerId, setEarnings, () => notify("Les gains sont momentanément indisponibles"));
  }, [demo, notify, ownerId]);

  useEffect(() => {
    const readDownloads = () => {
      if (typeof window === "undefined") return;
      try { setDownloadEvents(JSON.parse(localStorage.getItem(WHAPPY_DOWNLOAD_EVENTS_KEY) || "[]") as DownloadEvent[]); } catch { setDownloadEvents([]); }
    };
    readDownloads();
    window.addEventListener("storage", readDownloads);
    window.addEventListener("whappy-download-recorded", readDownloads);
    return () => { window.removeEventListener("storage", readDownloads); window.removeEventListener("whappy-download-recorded", readDownloads); };
  }, []);

  const visibleTeam = useMemo(() => team.filter((member) => !query || `${member.name} ${member.role} ${member.area} ${member.contact}`.toLowerCase().includes(query)), [query, team]);
  const visibleEarnings = useMemo(() => earnings.filter((earning) => !query || `${earning.title} ${earning.source} ${earning.status} ${earning.dueLabel}`.toLowerCase().includes(query)), [earnings, query]);
  const activeTeam = team.filter((member) => member.status === "active").length;
  const inProgress = earnings.filter((earning) => earning.status === "pending" || earning.status === "processing").reduce((total, earning) => total + earning.amount, 0);
  const paid = earnings.filter((earning) => earning.status === "paid").reduce((total, earning) => total + earning.amount, 0);
  const todayKey = new Date().toISOString().slice(0, 10);
  const downloadsToday = downloadEvents.filter((event) => event.at.slice(0, 10) === todayKey).length;
  const downloadDevices = new Set(downloadEvents.map((event) => event.userAgent || "inconnu")).size;
  const recentDownloads = downloadEvents.slice(0, 6);
  const storeRevenue = demoStorePurchases.reduce((total, purchase) => total + purchase.amount, 0);
  const paidStoreRevenue = demoStorePurchases.filter((purchase) => purchase.status === "paid").reduce((total, purchase) => total + purchase.amount, 0);

  async function addTeamMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const member = { name: String(form.get("name") || "").trim(), role: String(form.get("role") || "").trim(), contact: String(form.get("contact") || "").trim(), area: String(form.get("area") || "").trim(), status: String(form.get("status") || "invited") as WhappyTeamStatus };
    if (!member.name || !member.role) { notify("Ajoutez le nom et le rôle de la personne"); return; }
    setBusy(true);
    try {
      if (demo) setTeam((current) => [{ ...member, id: `demo-team-${Date.now()}`, ownerId }, ...current]);
      else await createWhappyTeamMember(ownerId, member);
      notify(`${member.name} a été ajouté à l’équipe Whappy`); setPanel(null);
    } catch { notify("La personne n’a pas pu être ajoutée à l’équipe"); } finally { setBusy(false); }
  }

  async function addEarning(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const earning = { title: String(form.get("title") || "").trim(), source: String(form.get("source") || "").trim(), amount: Number(form.get("amount") || 0), currency: String(form.get("currency") || "XAF") as BusinessCurrency, status: String(form.get("status") || "pending") as FounderEarningStatus, dueLabel: String(form.get("dueLabel") || "").trim() };
    if (!earning.title || !earning.amount) { notify("Ajoutez un libellé et un montant pour le gain"); return; }
    setBusy(true);
    try {
      if (demo) setEarnings((current) => [{ ...earning, id: `demo-earning-${Date.now()}`, ownerId }, ...current]);
      else await createFounderEarning(ownerId, earning);
      notify("Le gain a été ajouté au suivi financier"); setPanel(null);
    } catch { notify("Le gain n’a pas pu être enregistré"); } finally { setBusy(false); }
  }

  async function changeEarningStatus(earning: FounderEarning, status: FounderEarningStatus) {
    setBusy(true);
    try {
      if (demo) setEarnings((current) => current.map((item) => item.id === earning.id ? { ...item, status } : item));
      else await updateFounderEarningStatus(earning.id, status);
      notify(`${earning.title} · ${earningStatusLabels[status]}`);
    } catch { notify("Le statut du gain n’a pas pu être modifié"); } finally { setBusy(false); }
  }

  function copyApkLink() {
    if (typeof window === "undefined") return;
    const link = new URL(WHAPPY_ADMIN_APK.url, window.location.origin).toString();
    void navigator.clipboard?.writeText(link);
    notify("Lien APK copié pour partage");
  }

  function exportDownloadCsv() {
    if (typeof window === "undefined") return;
    const lines = ["date,version,source,taille,appareil", ...downloadEvents.map((event) => [event.at, event.version, event.source, event.size, event.userAgent || ""].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))];
    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = `whappy-telechargements-${Date.now()}.csv`; link.click();
    URL.revokeObjectURL(url);
    notify("Export téléchargements préparé");
  }

  function exportPlayCsv() {
    if (typeof window === "undefined") return;
    const lines = ["type,libelle,montant,statut,source", ...demoStorePurchases.map((purchase) => [purchase.kind, purchase.title, purchase.amount, purchase.status, purchase.source].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))];
    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = `whappy-google-play-revenus-${Date.now()}.csv`; link.click();
    URL.revokeObjectURL(url);
    notify("Export Google Play préparé");
  }

  return <section className="happy-founder-dashboard" lang="fr">
    <nav className="founder-quick-actions" aria-label="Actions rapides Business"><button onClick={() => document.getElementById("founder-admin-center")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>▦</span><strong>Admin système</strong><small>APK, accès, logs</small></button><button onClick={() => document.getElementById("founder-play-console")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>▶</span><strong>Play Store</strong><small>Installs & achats</small></button><button onClick={() => setPanel("team")}><span>◎</span><strong>Gérer l’équipe</strong><small>Inviter un profil</small></button><button onClick={() => setPanel("earning")}><span>◆</span><strong>Ajouter un gain</strong><small>Suivre une recette</small></button><button onClick={() => document.getElementById("business-studio")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>▣</span><strong>Pages & publicités</strong><small>Développer la portée</small></button><button onClick={() => document.getElementById("business-growth")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>◇</span><strong>Catalogue & prospects</strong><small>Convertir les clients</small></button></nav>
    <header className="founder-dashboard-header"><div><div className="founder-identity-line"><span className="founder-mark">W</span><i className="founder-grey-badge" title="Compte fondateur Whappy by BCA certifié">✓</i><span className="founder-role">FONDATEUR</span></div><small>WHAPPY · COMPTE BUSINESS OFFICIEL</small><h2>Bonjour, <em>{userName}</em>.</h2><p>Le centre de pilotage de {userName} : utilisateurs, directs, équipe et revenus de Whappy en un seul endroit.</p></div><div className="founder-status"><i /> Système opérationnel<small>{demo ? "Données de démonstration" : "Données synchronisées en temps réel"}</small></div></header>
    <div className="founder-metrics"><article><span>UTILISATEURS WHAPPY</span><strong>{userCount.toLocaleString("fr-FR")}</strong><small>Comptes enregistrés</small><b>↗ Communauté totale</b></article><article><span>EN DIRECT MAINTENANT</span><strong>{liveStats.sessions}</strong><small>{liveStats.viewers.toLocaleString("fr-FR")} spectateurs</small><b className="live-metric">● Réseau Live actif</b></article><article><span>TÉLÉCHARGEMENTS APK</span><strong>{downloadEvents.length}</strong><small>{downloadsToday} aujourd’hui · {downloadDevices} appareil{downloadDevices > 1 ? "s" : ""}</small><b>↓ Version {WHAPPY_ADMIN_APK.version}</b></article><article><span>GAINS EN COURS</span><strong>{formatFounderMoney(inProgress, "XAF")}</strong><small>À confirmer ou à encaisser</small><b>◆ Suivi financier</b></article><article><span>ÉQUIPE WHAPPY</span><strong>{activeTeam}</strong><small>{team.length} membre{team.length > 1 ? "s" : ""} au total</small><b>◎ Coordination</b></article></div>
    <section className="founder-admin-center" id="founder-admin-center" aria-labelledby="founder-admin-title"><header><div><small>ADMIN WHAPPY</small><h3 id="founder-admin-title">Centre interne comme WhatsApp Business / WeChat.</h3><p>Le compte utilisateur reste normal, mais le rôle fondateur donne accès aux versions, téléchargements, équipe, revenus et contrôles système.</p></div><span>Rôle : Fondateur</span></header><div className="founder-admin-layout"><article className="admin-release-card"><small>APK PUBLIÉE</small><strong>WHAPPY {WHAPPY_ADMIN_APK.version}</strong><p>{WHAPPY_ADMIN_APK.size} · {WHAPPY_ADMIN_APK.minimum} · ARM64 optimisé</p><div><a href={WHAPPY_ADMIN_APK.url} download>↓ Télécharger l’APK</a><button onClick={copyApkLink}>Copier le lien</button></div></article><article className="admin-download-card"><small>TÉLÉCHARGEMENTS</small><strong>{downloadEvents.length}</strong><p>{downloadsToday} aujourd’hui sur cet espace. Les stats globales serveur seront branchées ensuite sur analytics/cloud.</p><button onClick={exportDownloadCsv} disabled={!downloadEvents.length}>Exporter CSV</button></article><article className="admin-security-card"><small>MODÈLE ADMIN</small><ul><li>Propriétaire : accès complet</li><li>Admin : support, groupes, chaînes</li><li>Business : pages, pubs, catalogue</li><li>Audit : chaque action sensible doit être journalisée</li></ul></article></div><div className="founder-download-log"><header><strong>Journal récent</strong><small>{recentDownloads.length ? "Derniers clics de téléchargement APK" : "Aucun téléchargement enregistré sur cet appareil"}</small></header>{recentDownloads.map((event) => <div key={event.id}><span>{event.source === "direct" ? "Lien direct" : "Bouton principal"}</span><strong>{new Date(event.at).toLocaleString("fr-FR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })}</strong><small>{event.version}</small></div>)}</div></section>
    <section className="founder-play-console" id="founder-play-console" aria-labelledby="founder-play-title"><header><div><small>GOOGLE PLAY / FIREBASE / BILLING</small><h3 id="founder-play-title">Suivi officiel des téléchargements, achats et abonnements.</h3><p>Ce panneau est prêt pour Play Console, Firebase Analytics et Google Play Billing. Les données affichées ici servent de structure jusqu’à la connexion de ton compte développeur Google.</p></div><button className={storeConnected ? "connected" : ""} onClick={() => { setStoreConnected((value) => !value); notify(storeConnected ? "Connexion Google Play marquée en attente" : "Connexion Google Play marquée prête à brancher"); }}>{storeConnected ? "Connecté en test" : "Préparer connexion"}</button></header><div className="play-status-strip"><span><i /> Play Console : {storeConnected ? "Prêt API" : "À connecter"}</span><span><i /> Firebase Analytics : events app</span><span><i /> Billing RTDN : achats temps réel</span><span><i /> Backend WHAPPY : droits utilisateurs</span></div><div className="play-metric-grid">{demoStoreMetrics.map((metric) => <article key={metric.label}><small>{metric.label}</small><strong>{metric.value}</strong><span>{metric.detail}</span><b>{metric.trend}</b></article>)}</div><div className="play-revenue-grid"><section><header><div><small>REVENUS GOOGLE PLAY</small><h4>{formatFounderMoney(storeRevenue, "XAF")}</h4><p>{formatFounderMoney(paidStoreRevenue, "XAF")} confirmés · le reste attend validation ou remboursement.</p></div><button onClick={exportPlayCsv}>Exporter revenus</button></header><div className="play-purchase-list">{demoStorePurchases.map((purchase) => <article key={purchase.id} className={purchase.status}><div><strong>{purchase.title}</strong><small>{purchase.kind} · {purchase.source}</small></div><b>{formatFounderMoney(purchase.amount, "XAF")}</b><span>{purchase.status === "paid" ? "Payé" : purchase.status === "pending" ? "En attente" : "Remboursé"}</span></article>)}</div></section><aside><small>ACQUISITION</small><h4>Sources d’installation</h4>{demoStoreChannels.map((channel) => <div className={`play-channel ${channel.tone}`} key={channel.name}><span>{channel.name}</span><strong>{channel.users.toLocaleString("fr-FR")}</strong><small>Conversion {channel.conversion}</small></div>)}<div className="play-connection-plan"><strong>À brancher ensuite</strong><ol><li>Compte Google Play Console</li><li>Firebase Analytics + BigQuery</li><li>Google Play Billing + RTDN</li><li>Backend WHAPPY pour activer les droits</li></ol></div></aside></div></section>
    <div className="founder-dashboard-grid"><section className="founder-panel founder-team-panel"><header><div><small>ÉQUIPE WHAPPY</small><h3>Les personnes qui font avancer {userName}</h3><p>Invitez les profils produit, communauté, opérations, technique ou partenaires.</p></div><button onClick={() => setPanel("team")}>＋ Ajouter à l’équipe</button></header><div className="founder-team-list">{visibleTeam.map((member) => <article key={member.id}><span className="founder-person-mark">{member.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase()}</span><div><strong>{member.name}</strong><small>{member.role} · {member.area}</small><em>{member.contact || "Contact à renseigner"}</em></div><span className={`founder-member-status ${member.status}`}>{teamStatusLabels[member.status]}</span></article>)}{!visibleTeam.length && <div className="founder-empty">Aucun membre ne correspond à votre recherche.</div>}</div></section><section className="founder-panel founder-earnings-panel"><header><div><small>FINANCES WHAPPY</small><h3>Gains en cours</h3><p>Suivez les recettes par activité et leur état de versement.</p></div><button onClick={() => setPanel("earning")}>＋ Ajouter un gain</button></header><div className="founder-earning-summary"><span><b>{formatFounderMoney(inProgress, "XAF")}</b> en cours</span><span><b>{formatFounderMoney(paid, "XAF")}</b> versés</span></div><div className="founder-earning-list">{visibleEarnings.map((earning) => <article key={earning.id}><div><strong>{earning.title}</strong><small>{earning.source} · {earning.dueLabel}</small></div><b>{formatFounderMoney(earning.amount, earning.currency)}</b><select aria-label={`Modifier le statut de ${earning.title}`} value={earning.status} disabled={busy} onChange={(event) => void changeEarningStatus(earning, event.target.value as FounderEarningStatus)}>{Object.entries(earningStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></article>)}{!visibleEarnings.length && <div className="founder-empty">Aucun gain ne correspond à votre recherche.</div>}</div></section></div>
    <footer className="founder-dashboard-footer"><span>✓ Badge gris réservé au compte officiel {userName}</span><span>⌁ Les statistiques cloud se mettent à jour automatiquement</span><span>◆ Les gains affichés doivent être confirmés avant paiement</span></footer>
    {panel === "team" && <div className="founder-modal"><button className="founder-modal-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><form onSubmit={addTeamMember}><header><div><small>ÉQUIPE WHAPPY</small><h3>Ajouter une personne</h3></div><button type="button" onClick={() => setPanel(null)}>×</button></header><label>Nom<input name="name" required placeholder="Ex. Nadia M."/></label><label>Rôle<input name="role" required placeholder="Ex. Responsable communauté"/></label><div className="founder-form-row"><label>Contact<input name="contact" placeholder="Téléphone ou email"/></label><label>Zone<select name="area"><option>Produit & vision</option><option>Communauté</option><option>Opérations</option><option>Technique</option><option>Partenariats</option></select></label></div><label>Statut<select name="status"><option value="invited">Invitation à envoyer</option><option value="active">Actif</option><option value="paused">En pause</option></select></label><button className="founder-submit" disabled={busy}>{busy ? "Ajout…" : "Ajouter à l’équipe →"}</button></form></div>}
    {panel === "earning" && <div className="founder-modal"><button className="founder-modal-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><form onSubmit={addEarning}><header><div><small>FINANCES WHAPPY</small><h3>Ajouter un gain</h3></div><button type="button" onClick={() => setPanel(null)}>×</button></header><label>Libellé<input name="title" required placeholder="Ex. Abonnements Business"/></label><label>Source<input name="source" placeholder="Ex. WHAPPY Ads"/></label><div className="founder-form-row"><label>Montant<input name="amount" required type="number" min="1" step="1" placeholder="1250000"/></label><label>Devise<select name="currency" defaultValue="XAF">{Object.entries(businessCurrencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div><label>État<select name="status"><option value="pending">À confirmer</option><option value="processing">En cours</option><option value="paid">Versé</option></select></label><label>Échéance ou note<input name="dueLabel" placeholder="Ex. Prochaine clôture · 31 août"/></label><button className="founder-submit" disabled={busy}>{busy ? "Enregistrement…" : "Suivre ce gain →"}</button></form></div>}
  </section>;
}
