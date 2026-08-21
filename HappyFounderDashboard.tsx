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

  const visibleTeam = useMemo(() => team.filter((member) => !query || `${member.name} ${member.role} ${member.area} ${member.contact}`.toLowerCase().includes(query)), [query, team]);
  const visibleEarnings = useMemo(() => earnings.filter((earning) => !query || `${earning.title} ${earning.source} ${earning.status} ${earning.dueLabel}`.toLowerCase().includes(query)), [earnings, query]);
  const activeTeam = team.filter((member) => member.status === "active").length;
  const inProgress = earnings.filter((earning) => earning.status === "pending" || earning.status === "processing").reduce((total, earning) => total + earning.amount, 0);
  const paid = earnings.filter((earning) => earning.status === "paid").reduce((total, earning) => total + earning.amount, 0);

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

  return <section className="happy-founder-dashboard" lang="fr">
    <nav className="founder-quick-actions" aria-label="Actions rapides Business"><button onClick={() => setPanel("team")}><span>◎</span><strong>Gérer l’équipe</strong><small>Inviter un profil</small></button><button onClick={() => setPanel("earning")}><span>◆</span><strong>Ajouter un gain</strong><small>Suivre une recette</small></button><button onClick={() => document.getElementById("business-studio")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>▣</span><strong>Pages & publicités</strong><small>Développer la portée</small></button><button onClick={() => document.getElementById("business-growth")?.scrollIntoView({ behavior: "smooth", block: "start" })}><span>◇</span><strong>Catalogue & prospects</strong><small>Convertir les clients</small></button></nav>
    <header className="founder-dashboard-header"><div><div className="founder-identity-line"><span className="founder-mark">W</span><i className="founder-grey-badge" title="Compte fondateur Whappy by BCA certifié">✓</i><span className="founder-role">FONDATEUR</span></div><small>WHAPPY · COMPTE BUSINESS OFFICIEL</small><h2>Bonjour, <em>{userName}</em>.</h2><p>Le centre de pilotage de {userName} : utilisateurs, directs, équipe et revenus de Whappy en un seul endroit.</p></div><div className="founder-status"><i /> Système opérationnel<small>{demo ? "Données de démonstration" : "Données synchronisées en temps réel"}</small></div></header>
    <div className="founder-metrics"><article><span>UTILISATEURS WHAPPY</span><strong>{userCount.toLocaleString("fr-FR")}</strong><small>Comptes enregistrés</small><b>↗ Communauté totale</b></article><article><span>EN DIRECT MAINTENANT</span><strong>{liveStats.sessions}</strong><small>{liveStats.viewers.toLocaleString("fr-FR")} spectateurs</small><b className="live-metric">● Réseau Live actif</b></article><article><span>GAINS EN COURS</span><strong>{formatFounderMoney(inProgress, "XAF")}</strong><small>À confirmer ou à encaisser</small><b>◆ Suivi financier</b></article><article><span>ÉQUIPE WHAPPY</span><strong>{activeTeam}</strong><small>{team.length} membre{team.length > 1 ? "s" : ""} au total</small><b>◎ Coordination</b></article></div>
    <div className="founder-dashboard-grid"><section className="founder-panel founder-team-panel"><header><div><small>ÉQUIPE WHAPPY</small><h3>Les personnes qui font avancer {userName}</h3><p>Invitez les profils produit, communauté, opérations, technique ou partenaires.</p></div><button onClick={() => setPanel("team")}>＋ Ajouter à l’équipe</button></header><div className="founder-team-list">{visibleTeam.map((member) => <article key={member.id}><span className="founder-person-mark">{member.name.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase()}</span><div><strong>{member.name}</strong><small>{member.role} · {member.area}</small><em>{member.contact || "Contact à renseigner"}</em></div><span className={`founder-member-status ${member.status}`}>{teamStatusLabels[member.status]}</span></article>)}{!visibleTeam.length && <div className="founder-empty">Aucun membre ne correspond à votre recherche.</div>}</div></section><section className="founder-panel founder-earnings-panel"><header><div><small>FINANCES WHAPPY</small><h3>Gains en cours</h3><p>Suivez les recettes par activité et leur état de versement.</p></div><button onClick={() => setPanel("earning")}>＋ Ajouter un gain</button></header><div className="founder-earning-summary"><span><b>{formatFounderMoney(inProgress, "XAF")}</b> en cours</span><span><b>{formatFounderMoney(paid, "XAF")}</b> versés</span></div><div className="founder-earning-list">{visibleEarnings.map((earning) => <article key={earning.id}><div><strong>{earning.title}</strong><small>{earning.source} · {earning.dueLabel}</small></div><b>{formatFounderMoney(earning.amount, earning.currency)}</b><select aria-label={`Modifier le statut de ${earning.title}`} value={earning.status} disabled={busy} onChange={(event) => void changeEarningStatus(earning, event.target.value as FounderEarningStatus)}>{Object.entries(earningStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></article>)}{!visibleEarnings.length && <div className="founder-empty">Aucun gain ne correspond à votre recherche.</div>}</div></section></div>
    <footer className="founder-dashboard-footer"><span>✓ Badge gris réservé au compte officiel {userName}</span><span>⌁ Les statistiques cloud se mettent à jour automatiquement</span><span>◆ Les gains affichés doivent être confirmés avant paiement</span></footer>
    {panel === "team" && <div className="founder-modal"><button className="founder-modal-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><form onSubmit={addTeamMember}><header><div><small>ÉQUIPE WHAPPY</small><h3>Ajouter une personne</h3></div><button type="button" onClick={() => setPanel(null)}>×</button></header><label>Nom<input name="name" required placeholder="Ex. Nadia M."/></label><label>Rôle<input name="role" required placeholder="Ex. Responsable communauté"/></label><div className="founder-form-row"><label>Contact<input name="contact" placeholder="Téléphone ou email"/></label><label>Zone<select name="area"><option>Produit & vision</option><option>Communauté</option><option>Opérations</option><option>Technique</option><option>Partenariats</option></select></label></div><label>Statut<select name="status"><option value="invited">Invitation à envoyer</option><option value="active">Actif</option><option value="paused">En pause</option></select></label><button className="founder-submit" disabled={busy}>{busy ? "Ajout…" : "Ajouter à l’équipe →"}</button></form></div>}
    {panel === "earning" && <div className="founder-modal"><button className="founder-modal-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><form onSubmit={addEarning}><header><div><small>FINANCES WHAPPY</small><h3>Ajouter un gain</h3></div><button type="button" onClick={() => setPanel(null)}>×</button></header><label>Libellé<input name="title" required placeholder="Ex. Abonnements Business"/></label><label>Source<input name="source" placeholder="Ex. WHAPPY Ads"/></label><div className="founder-form-row"><label>Montant<input name="amount" required type="number" min="1" step="1" placeholder="1250000"/></label><label>Devise<select name="currency" defaultValue="XAF">{Object.entries(businessCurrencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div><label>État<select name="status"><option value="pending">À confirmer</option><option value="processing">En cours</option><option value="paid">Versé</option></select></label><label>Échéance ou note<input name="dueLabel" placeholder="Ex. Prochaine clôture · 31 août"/></label><button className="founder-submit" disabled={busy}>{busy ? "Enregistrement…" : "Suivre ce gain →"}</button></form></div>}
  </section>;
}
