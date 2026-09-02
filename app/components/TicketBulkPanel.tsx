"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { getFunctions, httpsCallable } from "firebase/functions";
import { firebaseApp } from "@/lib/firebase";

type TicketEvent = { id: string; ownerId: string; title: string; venue: string; description: string; category: string; ticketLabel: string; startsAt: number; capacity: number; reserved: number; checkedIn: number; status: string; myTicketStatus?: string | null };
type Ticket = { id: string; eventId: string; title: string; venue: string; startsAt: number; organizer: string; ticketLabel: string; status: string; code: string };
type Result<T> = { data: T };

const demoEvent: TicketEvent = { id: "demo-ticket-event", ownerId: "demo-user", title: "WAPI Connect · soirée Business", venue: "Brazzaville · Espace La Congolaise", description: "Rencontrez des entrepreneurs et présentez vos offres.", category: "Networking", ticketLabel: "Accès général", startsAt: Date.now() + 86_400_000 * 5, capacity: 120, reserved: 38, checkedIn: 0, status: "published", myTicketStatus: null };

function dateLabel(timestamp: number) { return new Date(timestamp).toLocaleString("fr-FR", { dateStyle: "medium", timeStyle: "short" }); }
function callable() { return httpsCallable<Record<string, unknown>, Record<string, unknown>>(getFunctions(firebaseApp, "europe-west1"), "wapiCommerce"); }

export function TicketBulkPanel({ userId, notify, demo = false, pages }: { userId: string; notify: (text: string) => void; demo?: boolean; pages: Array<{ id: string; name: string }> }) {
  const [events, setEvents] = useState<TicketEvent[]>(demo ? [demoEvent] : []);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [busy, setBusy] = useState(false);
  const [creator, setCreator] = useState(false);

  const load = useCallback(async () => {
    if (demo) return;
    try {
      const api = callable();
      const [eventResult, ticketResult] = await Promise.all([api({ action: "events" }) as Promise<Result<{ events?: TicketEvent[] }>>, api({ action: "myTickets" }) as Promise<Result<{ tickets?: Ticket[] }>>]);
      setEvents(eventResult.data.events || []);
      setTickets(ticketResult.data.tickets || []);
    } catch { notify("La billetterie est momentanément indisponible"); }
  }, [demo, notify]);
  useEffect(() => { void load(); }, [load]);

  const upcoming = useMemo(() => events.filter((event) => event.startsAt > Date.now() && event.status === "published"), [events]);
  async function createEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pages.length) { notify("Créez d’abord une page Business pour organiser un événement"); return; }
    const form = new FormData(event.currentTarget);
    const startsAt = new Date(String(form.get("startsAt"))).getTime();
    setBusy(true);
    try {
      if (demo) { notify("Connectez-vous pour publier un événement réel"); return; }
      await callable()({ action: "createEvent", eventId: `event-${Date.now()}`, pageId: pages[0].id, title: String(form.get("title") || ""), venue: String(form.get("venue") || ""), description: String(form.get("description") || ""), category: String(form.get("category") || "Événement"), ticketLabel: String(form.get("ticketLabel") || "Accès général"), capacity: Number(form.get("capacity") || 1), startsAt });
      notify("Événement publié : les réservations sont ouvertes"); setCreator(false); await load();
    } catch { notify("L’événement n’a pas pu être publié"); } finally { setBusy(false); }
  }
  async function reserve(event: TicketEvent) {
    setBusy(true);
    try {
      if (demo) { notify("Aperçu démo : la réservation sera confirmée après connexion"); return; }
      await callable()({ action: "reserveTicket", eventId: event.id }); notify("Billet confirmé et ajouté à vos billets"); await load();
    } catch { notify("La réservation n’a pas pu être confirmée"); } finally { setBusy(false); }
  }

  return <section className="ticketbulk-space"><style>{`.ticketbulk-space{display:grid;gap:14px}.ticketbulk-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:18px;border:1px solid rgba(113,135,255,.25);border-radius:20px;background:linear-gradient(125deg,rgba(113,135,255,.16),rgba(255,255,255,.035))}.ticketbulk-header h3{margin:6px 0;color:#fff;font-size:18px}.ticketbulk-header p{margin:0;color:#98a2b3;font-size:8px}.ticketbulk-header small,.ticketbulk-section-title small{color:#aebcff;font-size:6px;font-weight:950;letter-spacing:1px}.ticketbulk-header>button,.ticket-event-card>button{padding:10px 12px;border:0;border-radius:10px;background:#7187ff;color:#0b0d14;font-size:8px;font-weight:950;cursor:pointer}.ticketbulk-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.ticketbulk-metrics article,.ticketbulk-grid>section{padding:13px;border:1px solid rgba(255,255,255,.1);border-radius:15px;background:rgba(255,255,255,.035)}.ticketbulk-metrics article{display:flex;flex-direction:column;gap:3px}.ticketbulk-metrics strong{color:#fff;font-size:19px}.ticketbulk-metrics small,.ticketbulk-note,.ticket-event-card p,.ticket-event-card small,.ticketbulk-my-tickets small,.ticketbulk-my-tickets em{color:#98a2b3;font-size:7px}.ticketbulk-grid{display:grid;grid-template-columns:1.15fr .85fr;gap:14px}.ticketbulk-section-title{display:flex;justify-content:space-between;margin-bottom:11px}.ticketbulk-section-title h4{margin:5px 0;color:#fff;font-size:13px}.ticket-event-card{display:grid;grid-template-columns:34px 1fr auto;gap:10px;padding:12px 0;border-top:1px solid rgba(255,255,255,.08)}.ticket-event-mark{display:grid;place-items:center;width:34px;height:34px;border-radius:12px;background:rgba(113,135,255,.15);color:#aebcff;font-size:18px}.ticket-event-card span{color:#aebcff;font-size:6px}.ticket-event-card h4{margin:4px 0;color:#fff;font-size:10px}.ticket-event-card p{margin:0 0 6px}.ticket-progress{height:4px;margin-top:6px;border-radius:99px;background:rgba(255,255,255,.09)}.ticket-progress i{display:block;height:100%;border-radius:inherit;background:#7187ff}.ticketbulk-my-tickets>article{display:flex;justify-content:space-between;gap:10px;padding:11px 0;border-top:1px solid rgba(255,255,255,.08)}.ticketbulk-my-tickets>article>div{display:flex;min-width:0;flex-direction:column;gap:3px}.ticketbulk-my-tickets strong{color:#fff;font-size:8px}.ticketbulk-my-tickets em{font-style:normal;color:#9daeff}.ticketbulk-my-tickets code{color:#7187ff;font-size:6px}.ticketbulk-form{display:grid;gap:11px;width:min(600px,calc(100vw - 26px))!important}@media(max-width:760px){.ticketbulk-header{flex-direction:column}.ticketbulk-header>button{width:100%}.ticketbulk-grid{grid-template-columns:1fr}.ticketbulk-metrics strong{font-size:16px}.ticket-event-card{grid-template-columns:28px 1fr}.ticket-event-card>button{grid-column:2;justify-self:start}}`}</style>
    <header className="ticketbulk-header"><div><small>WAPI TICKETBULK</small><h3>Billetterie simple et contrôlée</h3><p>Publiez un événement, limitez les places et remettez un billet unique à chaque participant.</p></div><button onClick={() => setCreator(true)}>＋ Nouvel événement</button></header>
    <div className="ticketbulk-metrics"><article><strong>{events.length}</strong><small>événement{events.length > 1 ? "s" : ""}</small></article><article><strong>{events.reduce((sum, event) => sum + event.reserved, 0)}</strong><small>places réservées</small></article><article><strong>{tickets.filter((ticket) => ticket.status === "issued").length}</strong><small>billets actifs</small></article></div>
    <div className="ticketbulk-grid"><section><div className="ticketbulk-section-title"><div><small>À DÉCOUVRIR</small><h4>Événements à venir</h4></div><span>{upcoming.length} ouvert{upcoming.length > 1 ? "s" : ""}</span></div>{upcoming.map((event) => <article className="ticket-event-card" key={event.id}><div className="ticket-event-mark">◎</div><div><span>{event.category} · {event.ticketLabel}</span><h4>{event.title}</h4><p>⌖ {event.venue} · {dateLabel(event.startsAt)}</p><small>{event.reserved} / {event.capacity} places utilisées</small><div className="ticket-progress"><i style={{ width: `${Math.min(100, (event.reserved / Math.max(1, event.capacity)) * 100)}%` }}/></div></div><button disabled={busy || event.myTicketStatus === "issued"} onClick={() => reserve(event)}>{event.myTicketStatus === "issued" ? "Réservé ✓" : "Réserver"}</button></article>)}{!upcoming.length && <div className="business-empty"><span>◎</span><h3>Aucun événement ouvert</h3><p>Créez votre première rencontre Business et gérez les entrées dans WAPI.</p><button onClick={() => setCreator(true)}>Créer un événement</button></div>}</section><section className="ticketbulk-my-tickets"><div className="ticketbulk-section-title"><div><small>MON PORTEFEUILLE</small><h4>Mes billets</h4></div><span>QR sécurisé</span></div>{tickets.length ? tickets.map((ticket) => <article key={ticket.id}><div><strong>{ticket.title}</strong><small>{ticket.organizer} · {dateLabel(ticket.startsAt)}</small><em>{ticket.ticketLabel} · {ticket.status === "issued" ? "Valide" : "Utilisé"}</em></div><code>{ticket.code.replace("wapi://ticketbulk/", "WAPI/").slice(0, 24)}…</code></article>) : <p className="ticketbulk-note">Vos billets réservés apparaîtront ici avec leur code unique. La validation d’entrée est faite par l’organisateur.</p>}</section></div>
    {creator && <div className="business-modal"><button className="business-modal-dismiss" onClick={() => setCreator(false)} aria-label="Fermer"/><form className="ticketbulk-form" onSubmit={createEvent}><header><div><small>WAPI TICKETBULK</small><h3>Créer un événement</h3><p>Les billets sont gratuits pour le moment. Le contrôle des places reste actif.</p></div><button type="button" onClick={() => setCreator(false)}>×</button></header><label>Nom de l’événement<input name="title" required minLength={3} maxLength={100} placeholder="Ex. Salon des entrepreneurs WAPI"/></label><div className="business-form-row"><label>Lieu<input name="venue" required maxLength={160} placeholder="Ex. Espace La Congolaise"/></label><label>Catégorie<input name="category" defaultValue="Événement" maxLength={60}/></label></div><label>Date et heure<input name="startsAt" required type="datetime-local" min={new Date(Date.now() + 60_000).toISOString().slice(0, 16)}/></label><div className="business-form-row"><label>Capacité<input name="capacity" required type="number" min={1} max={10000} defaultValue={50}/></label><label>Type de billet<input name="ticketLabel" defaultValue="Accès général" maxLength={60}/></label></div><label>Description<textarea name="description" maxLength={1200} placeholder="Présentez le programme et les informations importantes."/></label><button className="business-submit" disabled={busy}>{busy ? "Publication…" : "Publier et ouvrir les réservations →"}</button></form></div>}
  </section>;
}
