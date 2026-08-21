"use client";

import { useEffect, useMemo, useRef, useState } from "react";

export type PulseSpace = "orbit" | "live" | "market" | "barter" | "seek" | "inbox" | "contacts" | "services" | "twin" | "business" | "games";
type Command = { id: PulseSpace; icon: string; title: string; hint: string; keywords: string; accent?: boolean };

const commands: Command[] = [
  { id: "inbox", icon: "◫", title: "Messages", hint: "Discussions et groupes prioritaires", keywords: "message conversation chat groupe appel", accent: true },
  { id: "contacts", icon: "◎", title: "Contacts", hint: "Personnes, groupes et professionnels", keywords: "contact personne groupe professionnel" },
  { id: "market", icon: "◇", title: "Marketplace", hint: "Acheter, vendre et négocier", keywords: "marketplace marché produit vendre acheter boutique" },
  { id: "live", icon: "●", title: "Whappy Live", hint: "Directs, ventes et démonstrations", keywords: "live direct vidéo vendre" },
  { id: "seek", icon: "⌕", title: "Je cherche", hint: "Trouver une solution ou publier un besoin", keywords: "chercher besoin situation solution" },
  { id: "barter", icon: "⇄", title: "Troc intelligent", hint: "Échanger des objets ou des services", keywords: "troc échange objet service" },
  { id: "services", icon: "⌗", title: "Services", hint: "Paiements et outils du quotidien", keywords: "service paiement pay outil" },
  { id: "business", icon: "▥", title: "Business Suite", hint: "Pages, campagnes et croissance", keywords: "business publicité campagne page entreprise" },
  { id: "games", icon: "♞", title: "Jeux & Tournois", hint: "Échecs, dames, coach et cagnottes", keywords: "jeu jeux échecs echecs dames tournoi compétition coach prix gagner" },
  { id: "twin", icon: "✦", title: "Mon Double", hint: "Voix, mouvements et automatisations", keywords: "double ia voix mouvement automatiser clone" },
  { id: "orbit", icon: "▦", title: "Moments", hint: "Actualités, publicités et opportunités", keywords: "moment accueil fil actualité publicité" },
];

export function WhappyPulse({ open, onClose, onNavigate, unread, orderCount, cartCount, groupCount, syncStatus, userName, founder, demo }: { open: boolean; onClose: () => void; onNavigate: (space: PulseSpace) => void; unread: number; orderCount: number; cartCount: number; groupCount: number; syncStatus: "local" | "syncing" | "synced" | "offline"; userName: string; founder?: boolean; demo: boolean }) {
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return needle ? commands.filter((item) => `${item.title} ${item.hint} ${item.keywords}`.toLowerCase().includes(needle)) : commands;
  }, [query]);

  useEffect(() => { if (open) window.setTimeout(() => inputRef.current?.focus(), 30); }, [open]);
  useEffect(() => {
    if (!open) return;
    function handleKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowDown") { event.preventDefault(); setActive((value) => Math.min(filtered.length - 1, value + 1)); }
      if (event.key === "ArrowUp") { event.preventDefault(); setActive((value) => Math.max(0, value - 1)); }
      if (event.key === "Enter" && filtered[active]) { onNavigate(filtered[active].id); onClose(); }
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [active, filtered, onClose, onNavigate, open]);

  if (!open) return null;
  const syncLabel = syncStatus === "synced" ? "Tout est synchronisé" : syncStatus === "syncing" ? "Synchronisation…" : syncStatus === "offline" ? "Mode hors ligne" : "Aperçu local";
  const openSpace = (space: PulseSpace) => { onNavigate(space); onClose(); };

  return <div className="pulse-layer" role="dialog" aria-modal="true" aria-label="Centre de commandes Whappy">
    <button className="pulse-dismiss" onClick={onClose} aria-label="Fermer le centre de commandes" />
    <section className="pulse-center">
      <header><div className="pulse-brand"><span>W</span><div><small>WHAPPY PULSE</small><strong>Bonjour {userName.split(" ")[0] || "vous"}{founder ? <i className="founder-grey-badge" title="Compte fondateur Whappy by BCA">✓</i> : null}</strong></div></div><div className={`pulse-system ${syncStatus}`}><i />{syncLabel}</div><button onClick={onClose} aria-label="Fermer">×</button></header>
      <div className="pulse-search"><span>⌕</span><input ref={inputRef} value={query} onChange={(event) => { setQuery(event.target.value); setActive(0); }} placeholder="Ouvrir Messages, Marketplace, Mon Double…" /><kbd>⌘ K</kbd></div>
      <main>
        <section className="pulse-priorities"><header><div><small>MAINTENANT</small><h2>Vos priorités</h2></div><span>{unread + orderCount} action(s)</span></header><div className="pulse-metrics"><button onClick={() => openSpace("inbox")}><span>◫</span><strong>{unread}</strong><small>messages à voir</small></button><button onClick={() => openSpace("market")}><span>◇</span><strong>{cartCount}</strong><small>articles au panier</small></button><button onClick={() => openSpace("services")}><span>▤</span><strong>{orderCount}</strong><small>commandes suivies</small></button><button onClick={() => openSpace("contacts")}><span>◎</span><strong>{groupCount}</strong><small>groupes actifs</small></button></div><div className="pulse-feed"><button onClick={() => openSpace("inbox")}><i className="urgent" /><span><small>MESSAGERIE PRIORITAIRE</small><strong>Amina et Junior attendent votre réponse</strong><p>Ouvrir les conversations non lues</p></span><b>→</b></button><button onClick={() => openSpace("twin")}><i /><span><small>AUTOMATISATION</small><strong>Votre Double peut prendre le relais</strong><p>Préparer une présentation vidéo ou une réponse</p></span><b>→</b></button><button onClick={() => openSpace("live")}><i /><span><small>EN DIRECT</small><strong>3 opportunités sont en live maintenant</strong><p>Regarder, discuter ou acheter</p></span><b>→</b></button></div>{demo && <p className="pulse-demo">◎ Mode aperçu : explorez tout le système sans envoyer de données.</p>}</section>
        <section className="pulse-commands"><header><small>{query ? "RÉSULTATS" : "TOUT WHAPPY"}</small><span>↑↓ naviguer · ↵ ouvrir</span></header><div>{filtered.map((item, index) => <button className={`${index === active ? "active" : ""} ${item.accent ? "accent" : ""}`} key={item.id} onMouseEnter={() => setActive(index)} onClick={() => openSpace(item.id)}><span>{item.icon}</span><div><strong>{item.title}</strong><small>{item.hint}</small></div><b>↗</b></button>)}{!filtered.length && <p>Aucun espace trouvé. Essayez « vendre », « appels » ou « Double ».</p>}</div></section>
      </main>
      <footer><span><i /> WHAPPY SYSTEM</span><p>Messages · Commerce · Live · Business · IA</p><b>Un seul endroit. Toutes vos opportunités.</b></footer>
    </section>
  </div>;
}
