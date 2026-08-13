"use client";

import Image from "next/image";
import { FormEvent, useMemo, useRef, useState } from "react";

type View = "messages" | "calls" | "groups" | "pulse";

type Message = { id: number; text: string; time: string; mine: boolean; seen?: boolean };
type Person = {
  id: number;
  name: string;
  initials: string;
  color: string;
  status: string;
  preview: string;
  time: string;
  online?: boolean;
  unread?: number;
  messages: Message[];
};

const peopleSeed: Person[] = [
  { id: 1, name: "Sarah M.", initials: "SM", color: "#ff8c69", status: "En ligne maintenant", preview: "On se retrouve à 18h ?", time: "10:42", online: true, unread: 2, messages: [
    { id: 1, text: "Salut Cyril ! Tu as vu la nouvelle campagne ?", time: "10:35", mine: false },
    { id: 2, text: "Oui, le concept Whappy Pulse est incroyable.", time: "10:37", mine: true, seen: true },
    { id: 3, text: "On se retrouve à 18h pour finaliser ?", time: "10:42", mine: false },
  ] },
  { id: 2, name: "Design Crew", initials: "DC", color: "#6856d6", status: "8 membres · 3 en ligne", preview: "Nadia : Le prototype est prêt ✨", time: "09:18", unread: 5, messages: [
    { id: 1, text: "Le nouveau système visuel est prêt à être testé.", time: "09:04", mine: false },
    { id: 2, text: "Super. Je lance une revue collective à midi.", time: "09:11", mine: true, seen: true },
    { id: 3, text: "Le prototype est prêt ✨", time: "09:18", mine: false },
  ] },
  { id: 3, name: "Maman", initials: "MA", color: "#d65b8f", status: "Vue aujourd'hui à 08:12", preview: "N'oublie pas de m'appeler ❤️", time: "Hier", messages: [
    { id: 1, text: "Bonjour mon fils, j'espère que tu vas bien.", time: "18:25", mine: false },
    { id: 2, text: "Très bien maman, je te rappelle ce soir.", time: "18:30", mine: true, seen: true },
    { id: 3, text: "N'oublie pas de m'appeler ❤️", time: "18:34", mine: false },
  ] },
  { id: 4, name: "David K.", initials: "DK", color: "#218e76", status: "Actif il y a 12 min", preview: "📷 Photo", time: "Hier", online: true, messages: [
    { id: 1, text: "Je t'envoie les images de notre sortie.", time: "21:40", mine: false },
    { id: 2, text: "Elles sont magnifiques, merci !", time: "21:45", mine: true, seen: true },
  ] },
  { id: 5, name: "Projet Kivu", initials: "PK", color: "#c88927", status: "12 membres", preview: "Vous : Document reçu, merci.", time: "Lun.", messages: [
    { id: 1, text: "📄 Planning-final.pdf", time: "14:13", mine: false },
    { id: 2, text: "Document reçu, merci.", time: "14:20", mine: true, seen: true },
  ] },
];

const calls = [
  { name: "Sarah M.", initials: "SM", color: "#ff8c69", type: "Vidéo", time: "Aujourd'hui, 11:24", state: "entrant" },
  { name: "Design Crew", initials: "DC", color: "#6856d6", type: "Salon · 38 min", time: "Aujourd'hui, 09:00", state: "sortant" },
  { name: "Maman", initials: "MA", color: "#d65b8f", type: "Audio · 12 min", time: "Hier, 20:16", state: "sortant" },
  { name: "David K.", initials: "DK", color: "#218e76", type: "Appel manqué", time: "Hier, 17:42", state: "manqué" },
];

const groups = [
  { name: "Design Crew", icon: "✦", color: "#6856d6", members: 8, online: 3, topic: "Construire la prochaine expérience Whappy", badge: 5 },
  { name: "Projet Kivu", icon: "◆", color: "#c88927", members: 12, online: 5, topic: "Documents, réunions et suivi terrain", badge: 0 },
  { name: "Famille", icon: "♥", color: "#d65b8f", members: 9, online: 2, topic: "Toujours proches, où que nous soyons", badge: 2 },
  { name: "Founders Congo", icon: "↗", color: "#218e76", members: 48, online: 16, topic: "Idées, entraide et opportunités", badge: 12 },
];

const posts = [
  { brand: "AIR CONGO", mark: "AC", color: "#147f52", verified: true, label: "Sponsorisé", title: "Brazzaville ↔ Paris. Le monde n'a jamais été aussi proche.", body: "Découvrez nos nouvelles cabines Horizon et profitez de -20% sur votre prochain voyage.", visual: "flight", cta: "Réserver maintenant", likes: "2,4 k", comments: 184 },
  { brand: "MOKABI STUDIO", mark: "MS", color: "#6652d9", verified: true, label: "Tendance à Brazzaville", title: "La créativité congolaise s'habille en lumière.", body: "Une collection pensée ici, portée partout. Édition limitée disponible dès aujourd'hui.", visual: "fashion", cta: "Découvrir la collection", likes: "6,8 k", comments: 392 },
  { brand: "WHAPPY BUSINESS", mark: "WB", color: "#0fbf35", verified: true, label: "Nouveau", title: "Transformez chaque conversation en opportunité.", body: "Catalogue, paiement et support client : tout votre commerce vit maintenant dans Whappy.", visual: "business", cta: "Essayer gratuitement", likes: "9,1 k", comments: 721 },
];

function Avatar({ initials, color, online, size = "normal" }: { initials: string; color: string; online?: boolean; size?: "small" | "normal" | "large" }) {
  return <span className={`avatar avatar-${size}`} style={{ background: color }}>{initials}{online && <i />}</span>;
}

function Glyph({ children }: { children: React.ReactNode }) {
  return <span className="glyph" aria-hidden="true">{children}</span>;
}

export default function Home() {
  const [view, setView] = useState<View>("messages");
  const [people, setPeople] = useState(peopleSeed);
  const [activeId, setActiveId] = useState(1);
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");
  const [dark, setDark] = useState(false);
  const [mobileDetail, setMobileDetail] = useState(false);
  const [activeCall, setActiveCall] = useState<string | null>(null);
  const [callVideo, setCallVideo] = useState(false);
  const [muted, setMuted] = useState(false);
  const [liked, setLiked] = useState<Record<number, boolean>>({});
  const [saved, setSaved] = useState<Record<number, boolean>>({});
  const [toast, setToast] = useState("");
  const endRef = useRef<HTMLDivElement>(null);

  const activePerson = people.find((person) => person.id === activeId) ?? people[0];
  const filteredPeople = useMemo(() => people.filter((person) => `${person.name} ${person.preview}`.toLowerCase().includes(query.toLowerCase())), [people, query]);

  function navigate(next: View) {
    setView(next);
    setMobileDetail(false);
    setQuery("");
  }

  function openChat(id: number) {
    setActiveId(id);
    setMobileDetail(true);
    setPeople((current) => current.map((person) => person.id === id ? { ...person, unread: undefined } : person));
  }

  function sendMessage(event: FormEvent) {
    event.preventDefault();
    const text = message.trim();
    if (!text) return;
    const time = new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    setPeople((current) => current.map((person) => person.id === activeId ? {
      ...person,
      preview: `Vous : ${text}`,
      time,
      messages: [...person.messages, { id: Date.now(), text, time, mine: true, seen: true }],
    } : person));
    setMessage("");
    window.setTimeout(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
  }

  function startCall(name: string, video = false) {
    setActiveCall(name);
    setCallVideo(video);
    setMuted(false);
  }

  function notify(text: string) {
    setToast(text);
    window.setTimeout(() => setToast(""), 2300);
  }

  return (
    <main className={`whappy-shell ${dark ? "dark" : ""}`}>
      <aside className="nav-rail">
        <button className="logo-button" onClick={() => navigate("messages")} aria-label="Accueil Whappy">
          <Image src="/whappy-logo.svg" width={48} height={48} alt="Icône Whappy" priority />
        </button>
        <nav aria-label="Navigation principale">
          <button className={view === "messages" ? "active" : ""} onClick={() => navigate("messages")}><Glyph>◫</Glyph><span>Messages</span><b>7</b></button>
          <button className={view === "calls" ? "active" : ""} onClick={() => navigate("calls")}><Glyph>⌕</Glyph><span>Appels</span></button>
          <button className={view === "groups" ? "active" : ""} onClick={() => navigate("groups")}><Glyph>♧</Glyph><span>Groupes</span><b>3</b></button>
          <button className={view === "pulse" ? "active" : ""} onClick={() => navigate("pulse")}><Glyph>◉</Glyph><span>Pulse</span><em>Nouveau</em></button>
        </nav>
        <div className="rail-bottom">
          <button onClick={() => setDark((value) => !value)} aria-label="Changer le thème"><Glyph>{dark ? "☀" : "◐"}</Glyph></button>
          <button onClick={() => notify("Paramètres bientôt disponibles")} aria-label="Paramètres"><Glyph>⚙</Glyph></button>
          <button className="profile-dot" aria-label="Profil de Cyril">CB<i /></button>
        </div>
      </aside>

      {view === "messages" && <>
        <aside className={`context-panel ${mobileDetail ? "mobile-away" : ""}`}>
          <PanelHeader title="Messages" subtitle="7 non lus" action="＋" onAction={() => notify("Nouvelle discussion")} />
          <Search value={query} onChange={setQuery} placeholder="Rechercher une conversation" />
          <div className="story-row">
            <button className="story story-me"><span>＋</span><small>Mon statut</small></button>
            {people.slice(0, 4).map((person) => <button className="story" key={person.id} onClick={() => openChat(person.id)}><span style={{ background: person.color }}>{person.initials}</span><small>{person.name.split(" ")[0]}</small></button>)}
          </div>
          <div className="section-label"><span>CONVERSATIONS</span><button>Tout marquer comme lu</button></div>
          <div className="conversation-list">
            {filteredPeople.map((person) => <button key={person.id} className={`person-row ${activeId === person.id ? "selected" : ""}`} onClick={() => openChat(person.id)}>
              <Avatar initials={person.initials} color={person.color} online={person.online} />
              <span className="person-copy"><span><strong>{person.name}</strong><time>{person.time}</time></span><span><small>{person.preview}</small>{person.unread && <b>{person.unread}</b>}</span></span>
            </button>)}
          </div>
        </aside>
        <section className={`content-stage chat-stage ${mobileDetail ? "mobile-show" : ""}`}>
          <header className="stage-header">
            <button className="mobile-back" onClick={() => setMobileDetail(false)}>‹</button>
            <Avatar initials={activePerson.initials} color={activePerson.color} online={activePerson.online} />
            <div className="stage-title"><strong>{activePerson.name}</strong><span>{activePerson.status}</span></div>
            <div className="stage-actions">
              <button onClick={() => startCall(activePerson.name, true)} aria-label="Appel vidéo"><Glyph>▣</Glyph></button>
              <button onClick={() => startCall(activePerson.name)} aria-label="Appel audio"><Glyph>⌕</Glyph></button>
              <button onClick={() => notify("Recherche dans la conversation")} aria-label="Rechercher"><Glyph>⌕</Glyph></button>
              <button aria-label="Plus d'options"><Glyph>•••</Glyph></button>
            </div>
          </header>
          <div className="chat-canvas">
            <div className="secure-pill">◆ Messages protégés de bout en bout</div>
            <div className="day-pill">AUJOURD&apos;HUI</div>
            <div className="message-stack">
              {activePerson.messages.map((item) => <div key={item.id} className={`message ${item.mine ? "mine" : ""}`}><div><p>{item.text}</p><span>{item.time} {item.mine && <i>✓✓</i>}</span></div></div>)}
              <div ref={endRef} />
            </div>
          </div>
          <form className="message-composer" onSubmit={sendMessage}>
            <button type="button" onClick={() => notify("Choisissez un emoji")}>☺</button>
            <button type="button" onClick={() => notify("Ajoutez une photo ou un document")}>＋</button>
            <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Écrivez quelque chose de génial…" aria-label="Écrire un message" />
            <button type="button" className="voice" onClick={() => notify("Maintenez pour enregistrer")}>●</button>
            <button className="submit" type="submit" disabled={!message.trim()}>➤</button>
          </form>
        </section>
      </>}

      {view === "calls" && <>
        <aside className={`context-panel ${mobileDetail ? "mobile-away" : ""}`}>
          <PanelHeader title="Appels" subtitle="Restez proches" action="＋" onAction={() => startCall("Nouvel appel")} />
          <Search value={query} onChange={setQuery} placeholder="Rechercher un contact" />
          <div className="quick-call">
            <button onClick={() => startCall("Nouveau salon", true)}><span>▣</span><strong>Créer un salon</strong><small>Invitez jusqu&apos;à 50 personnes</small></button>
          </div>
          <div className="section-label"><span>RÉCENTS</span></div>
          <div className="call-list">{calls.map((call) => <button key={call.name} onClick={() => startCall(call.name, call.type.includes("Vidéo"))}>
            <Avatar initials={call.initials} color={call.color} />
            <span><strong>{call.name}</strong><small className={call.state === "manqué" ? "missed" : ""}>{call.state === "entrant" ? "↙" : "↗"} {call.type} · {call.time}</small></span><Glyph>⌕</Glyph>
          </button>)}</div>
        </aside>
        <section className={`content-stage calls-stage ${mobileDetail ? "mobile-show" : ""}`}>
          <div className="calls-hero">
            <div className="orb orb-one" /><div className="orb orb-two" />
            <span className="eyebrow">WHAPPY ROOMS</span>
            <h1>Les appels qui vous<br/><em>rapprochent vraiment.</em></h1>
            <p>Un son spatial, une vidéo limpide et des salons qui accueillent toute votre communauté.</p>
            <div><button className="primary-cta" onClick={() => startCall("Salon instantané", true)}>▣ Démarrer un salon</button><button className="soft-cta" onClick={() => notify("Lien copié")}>◇ Copier mon lien</button></div>
          </div>
          <div className="live-rooms">
            <div className="section-heading"><div><span>EN CE MOMENT</span><h2>Salons à rejoindre</h2></div><button>Voir tout</button></div>
            <div className="room-grid">
              <button onClick={() => startCall("Café des créateurs", true)}><div className="room-visual room-purple"><span>CK</span><span>AM</span><span>JD</span><b>+12</b></div><small>CRÉATIVITÉ</small><strong>Café des créateurs</strong><p>15 personnes discutent maintenant</p></button>
              <button onClick={() => startCall("Tech & Futur", true)}><div className="room-visual room-green"><span>MB</span><span>SK</span><span>YL</span><b>+28</b></div><small>INNOVATION</small><strong>Tech & Futur</strong><p>31 personnes discutent maintenant</p></button>
            </div>
          </div>
        </section>
      </>}

      {view === "groups" && <>
        <aside className={`context-panel ${mobileDetail ? "mobile-away" : ""}`}>
          <PanelHeader title="Groupes" subtitle="Vos communautés" action="＋" onAction={() => notify("Création d'un groupe")} />
          <Search value={query} onChange={setQuery} placeholder="Rechercher un groupe" />
          <button className="create-group" onClick={() => notify("Nouveau groupe prêt à créer")}><span>＋</span><div><strong>Créer un groupe</strong><small>Réunissez votre communauté</small></div></button>
          <div className="section-label"><span>VOS GROUPES</span><button>Gérer</button></div>
          <div className="group-list">{groups.map((group, index) => <button key={group.name} className={index === 0 ? "selected" : ""} onClick={() => setMobileDetail(true)}><span className="group-icon" style={{ background: group.color }}>{group.icon}</span><div><strong>{group.name}</strong><small>{group.members} membres · {group.online} en ligne</small></div>{group.badge > 0 && <b>{group.badge}</b>}</button>)}</div>
        </aside>
        <section className={`content-stage groups-stage ${mobileDetail ? "mobile-show" : ""}`}>
          <header className="group-cover">
            <button className="mobile-back" onClick={() => setMobileDetail(false)}>‹</button>
            <div className="cover-shape shape-a"/><div className="cover-shape shape-b"/>
            <span className="group-big-icon">✦</span>
            <div><span className="eyebrow">GROUPE CRÉATIF</span><h1>Design Crew</h1><p>Construire la prochaine expérience Whappy.</p></div>
            <button className="group-menu">•••</button>
          </header>
          <div className="group-dashboard">
            <div className="group-tabs"><button className="active">Aperçu</button><button>Discussion</button><button>Médias</button><button>Événements</button></div>
            <div className="group-stats"><div><span>8</span><small>Membres</small></div><div><span>3</span><small>En ligne</small></div><div><span>126</span><small>Médias</small></div><button onClick={() => startCall("Design Crew", true)}>▣ Lancer un salon</button></div>
            <div className="group-columns">
              <div className="group-card"><div className="card-title"><h3>Prochain événement</h3><button>Tout voir</button></div><div className="event-card"><span><b>18</b>AOÛT</span><div><small>VISIOCONFÉRENCE</small><strong>Revue du prototype v2</strong><p>12:00 · 45 minutes</p></div><button onClick={() => notify("Participation confirmée")}>Participer</button></div></div>
              <div className="group-card"><div className="card-title"><h3>Membres actifs</h3><button>Inviter</button></div><div className="member-line"><Avatar initials="NM" color="#ff8c69"/><span><strong>Nadia M.</strong><small>Administratrice</small></span><i>●</i></div><div className="member-line"><Avatar initials="JK" color="#218e76"/><span><strong>Junior K.</strong><small>Designer produit</small></span><i>●</i></div><div className="member-line"><Avatar initials="AM" color="#6856d6"/><span><strong>Amina M.</strong><small>Stratégie</small></span></div></div>
            </div>
          </div>
        </section>
      </>}

      {view === "pulse" && <>
        <aside className={`context-panel pulse-side ${mobileDetail ? "mobile-away" : ""}`}>
          <PanelHeader title="Pulse" subtitle="Le monde maintenant" action="✦" onAction={() => notify("Créez votre publication")} />
          <Search value={query} onChange={setQuery} placeholder="Explorer Whappy Pulse" />
          <div className="pulse-intro"><span>◉</span><h3>Votre monde.<br/>Votre rythme.</h3><p>Découvrez les idées, marques et moments qui font vibrer votre communauté.</p><button onClick={() => notify("Créateur Pulse ouvert")}>＋ Créer un Pulse</button></div>
          <div className="trends"><div className="section-label"><span>TENDANCES</span></div>{["#MadeInCongo", "#TechAfrica", "#ModeLocale", "#Entrepreneurs"].map((trend, index) => <button key={trend}><span>{index + 1}</span><div><strong>{trend}</strong><small>{["12,8 k", "8,4 k", "6,1 k", "4,9 k"][index]} publications</small></div><Glyph>›</Glyph></button>)}</div>
        </aside>
        <section className={`content-stage pulse-stage ${mobileDetail ? "mobile-show" : ""}`}>
          <header className="pulse-header"><button className="mobile-back" onClick={() => setMobileDetail(false)}>‹</button><div><h1>Pour vous</h1><p>Sélectionné selon vos passions</p></div><div><button className="active">Pour vous</button><button>Abonnements</button><button>À proximité</button></div></header>
          <div className="pulse-scroll">
            <div className="pulse-stories"><button><span className="add-pulse">＋</span><small>Votre Pulse</small></button>{["Voyage", "Créateurs", "Cuisine", "Business", "Musique"].map((label, index) => <button key={label}><span className={`pulse-ring ring-${index + 1}`}>{["✈", "✦", "♨", "↗", "♫"][index]}</span><small>{label}</small></button>)}</div>
            <div className="feed-layout"><div className="feed">
              {posts.map((post, index) => <article className="post" key={post.brand}>
                <header><Avatar initials={post.mark} color={post.color}/><div><strong>{post.brand} {post.verified && <i>✓</i>}</strong><small>{post.label} · il y a {index + 1} h</small></div><button>•••</button></header>
                <div className={`ad-visual ${post.visual}`}><span className="ad-kicker">{index === 0 ? "PRENEZ DE LA HAUTEUR" : index === 1 ? "LUMIÈRE SUR LE CONGO" : "WHAPPY POUR ENTREPRENDRE"}</span><h2>{post.title}</h2><div className="visual-mark">{post.mark}</div></div>
                <div className="post-copy"><p>{post.body}</p><button className="ad-cta" onClick={() => notify(`${post.brand} ouvert`)}>{post.cta} <span>↗</span></button></div>
                <footer><button className={liked[index] ? "liked" : ""} onClick={() => setLiked((current) => ({ ...current, [index]: !current[index] }))}>{liked[index] ? "♥" : "♡"} {post.likes}</button><button onClick={() => notify(`${post.comments} commentaires`)}>◌ {post.comments}</button><button onClick={() => notify("Publication partagée")}>↗ Partager</button><button className={saved[index] ? "saved" : ""} onClick={() => setSaved((current) => ({ ...current, [index]: !current[index] }))}>◇</button></footer>
              </article>)}
            </div><aside className="pulse-right"><div className="business-box"><span>WHAPPY <b>BUSINESS</b></span><h3>Faites grandir votre marque là où les conversations commencent.</h3><p>Créez des campagnes utiles, humaines et mesurables.</p><button onClick={() => notify("Espace annonceur ouvert")}>Devenir annonceur ↗</button></div><div className="who-follow"><h3>Comptes à suivre</h3>{["Congo Culture", "Africa Tech", "Green Future"].map((name, index) => <div key={name}><Avatar initials={name.split(" ").map((x) => x[0]).join("")} color={["#c88927", "#6856d6", "#218e76"][index]} size="small"/><span><strong>{name}</strong><small>@{name.toLowerCase().replace(" ", "")}</small></span><button onClick={(event) => { event.currentTarget.textContent = "Suivi"; }}>Suivre</button></div>)}</div></aside></div>
          </div>
        </section>
      </>}

      {activeCall && <div className="call-overlay" role="dialog" aria-label={`Appel avec ${activeCall}`}>
        <div className="call-backdrop"><div className="call-glow"/></div>
        <button className="call-close" onClick={() => setActiveCall(null)}>×</button>
        <div className="call-person"><Avatar initials={activeCall.split(" ").map((part) => part[0]).join("").slice(0, 2)} color="#13d713" size="large"/><span>{callVideo ? "APPEL VIDÉO WHAPPY" : "APPEL AUDIO WHAPPY"}</span><h2>{activeCall}</h2><p>Connexion sécurisée en cours…</p></div>
        <div className="call-controls"><button className={muted ? "off" : ""} onClick={() => setMuted((value) => !value)}><Glyph>{muted ? "×" : "●"}</Glyph><span>Micro</span></button><button onClick={() => setCallVideo((value) => !value)} className={!callVideo ? "off" : ""}><Glyph>▣</Glyph><span>Caméra</span></button><button><Glyph>♬</Glyph><span>Son</span></button><button className="hangup" onClick={() => setActiveCall(null)}><Glyph>⌒</Glyph><span>Raccrocher</span></button></div>
      </div>}
      {toast && <div className="toast">✓ {toast}</div>}
    </main>
  );
}

function PanelHeader({ title, subtitle, action, onAction }: { title: string; subtitle: string; action: string; onAction: () => void }) {
  return <header className="panel-header"><div><h1>{title}</h1><span>{subtitle}</span></div><button onClick={onAction}>{action}</button></header>;
}

function Search({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return <label className="global-search"><Glyph>⌕</Glyph><input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder}/>{value && <button onClick={() => onChange("")}>×</button>}</label>;
}
