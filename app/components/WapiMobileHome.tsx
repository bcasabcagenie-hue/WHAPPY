"use client";

type MobileSpace = "contacts" | "business" | "live" | "market" | "channels" | "radio" | "twin" | "games";

const shortcuts: Array<{ space: MobileSpace; icon: string; label: string; detail: string; tone: string }> = [
  { space: "contacts", icon: "◎", label: "Contacts", detail: "Trouver et écrire", tone: "blue" },
  { space: "business", icon: "▥", label: "Business", detail: "Pages et offres", tone: "violet" },
  { space: "live", icon: "●", label: "En direct", detail: "Regarder et échanger", tone: "red" },
  { space: "market", icon: "◇", label: "Marché", detail: "Annonces et échanges", tone: "green" },
  { space: "channels", icon: "▤", label: "Chaînes", detail: "Médias et créateurs", tone: "gold" },
  { space: "radio", icon: "◉", label: "Radio", detail: "Émissions et podcasts", tone: "cyan" },
  { space: "twin", icon: "✦", label: "Jumeau numérique", detail: "Votre présence contrôlée", tone: "pink" },
  { space: "games", icon: "♞", label: "Jeux", detail: "Ludo et tournois", tone: "orange" },
];

export function WapiMobileHome({ userName, onNavigate }: { userName: string; onNavigate: (space: MobileSpace) => void }) {
  return <section className="mobile-parity-home" aria-label="Accueil WAPI">
    <div className="mobile-parity-hero">
      <div>
        <span className="mobile-parity-eyebrow">WAPI · EXPÉRIENCE MOBILE SUR LE WEB</span>
        <h2>Votre univers WAPI,<br /><em>sur tous vos écrans.</em></h2>
        <p>Retrouvez sur le web les mêmes accès que dans l’application : discuter, publier, appeler, créer et jouer depuis un seul espace.</p>
        <div className="mobile-parity-status"><i /> Session prête · {userName || "Utilisateur WAPI"}</div>
      </div>
      <div className="mobile-parity-orbit" aria-hidden="true"><span>W</span><i /><b /><em /></div>
    </div>
    <header className="mobile-parity-heading">
      <div><small>ACCÈS RAPIDE</small><h3>Tout ce que vous utilisez dans l’APK</h3></div>
      <span>Synchronisé avec l’expérience WAPI</span>
    </header>
    <div className="mobile-parity-grid">
      {shortcuts.map((item) => <button key={item.space} className={`mobile-parity-card ${item.tone}`} onClick={() => onNavigate(item.space)}>
        <span className="mobile-parity-icon">{item.icon}</span>
        <span><strong>{item.label}</strong><small>{item.detail}</small></span>
        <b>↗</b>
      </button>)}
    </div>
    <div className="mobile-parity-footer">
      <span><i /> Web et APK partagent la même identité WAPI</span>
      <button onClick={() => onNavigate("contacts")}>Commencer par mes contacts →</button>
    </div>
  </section>;
}
