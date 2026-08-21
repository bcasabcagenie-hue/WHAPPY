"use client";

type SocialSpace = "orbit" | "live" | "inbox" | "business" | "twin";

const destinations: Array<{ id: SocialSpace; icon: string; label: string; detail: string }> = [
  { id: "orbit", icon: "▦", label: "Fil vidéo", detail: "Pour vous" },
  { id: "inbox", icon: "◫", label: "Messages", detail: "Conversations" },
  { id: "live", icon: "●", label: "Live", detail: "En direct" },
  { id: "business", icon: "▥", label: "Business", detail: "Votre activité" },
  { id: "twin", icon: "◎", label: "Double IA", detail: "Votre présence" },
];

export function SocialControlDeck({ current, onNavigate, onCreate }: { current: SocialSpace; onNavigate: (space: SocialSpace) => void; onCreate: () => void }) {
  const active = destinations.find((item) => item.id === current) || destinations[0];
  return <section className="social-control-deck" aria-label="Navigation sociale Whappy">
    <div className="deck-context"><span className="deck-live-dot"/><div><small>WHAPPY SOCIAL OS</small><strong>{active.label} · {active.detail}</strong></div></div>
    <nav>{destinations.map((item) => <button key={item.id} className={current === item.id ? "active" : ""} onClick={() => onNavigate(item.id)}><span>{item.icon}</span><strong>{item.label}</strong><small>{item.detail}</small></button>)}</nav>
    <button className="deck-create" onClick={onCreate}><span>＋</span><strong>Créer</strong><small>Post, live ou offre</small></button>
  </section>;
}
