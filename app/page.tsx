"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";

type Chat = {
  id: number;
  name: string;
  initials: string;
  status: string;
  preview: string;
  time: string;
  unread?: number;
  accent: string;
  online?: boolean;
  muted?: boolean;
  messages: { id: number; text: string; time: string; mine: boolean; seen?: boolean }[];
};

const starterChats: Chat[] = [
  {
    id: 1,
    name: "Sarah M.",
    initials: "SM",
    status: "en ligne",
    preview: "On se retrouve à 18h ?",
    time: "10:42",
    unread: 2,
    accent: "#f29d72",
    online: true,
    messages: [
      { id: 1, text: "Salut Cyril ! Comment avance le projet Whappy ?", time: "10:35", mine: false },
      { id: 2, text: "Très bien ! L'interface prend forme et le logo rend super bien.", time: "10:37", mine: true, seen: true },
      { id: 3, text: "Excellent 🎉 J'ai hâte de voir le résultat.", time: "10:39", mine: false },
      { id: 4, text: "On se retrouve à 18h ?", time: "10:42", mine: false },
    ],
  },
  {
    id: 2,
    name: "Équipe Whappy",
    initials: "EW",
    status: "7 participants",
    preview: "Junior : La maquette est validée ✅",
    time: "09:18",
    unread: 4,
    accent: "#6e8fd3",
    messages: [
      { id: 1, text: "Bonjour l'équipe ! Le point de ce matin est disponible.", time: "08:50", mine: false },
      { id: 2, text: "Parfait, je regarde ça maintenant.", time: "09:02", mine: true, seen: true },
      { id: 3, text: "La maquette est validée ✅", time: "09:18", mine: false },
    ],
  },
  {
    id: 3,
    name: "Maman",
    initials: "MA",
    status: "vue aujourd'hui à 08:12",
    preview: "N'oublie pas de m'appeler ❤️",
    time: "Hier",
    accent: "#c78ab8",
    messages: [
      { id: 1, text: "Bonjour mon fils, j'espère que tu vas bien.", time: "18:25", mine: false },
      { id: 2, text: "Très bien maman, je te rappelle ce soir.", time: "18:30", mine: true, seen: true },
      { id: 3, text: "N'oublie pas de m'appeler ❤️", time: "18:34", mine: false },
    ],
  },
  {
    id: 4,
    name: "David K.",
    initials: "DK",
    status: "vu hier à 22:46",
    preview: "Photo",
    time: "Hier",
    accent: "#3ea695",
    muted: true,
    messages: [
      { id: 1, text: "Je t'envoie les photos de notre sortie.", time: "21:40", mine: false },
      { id: 2, text: "📷 Photo", time: "21:42", mine: false },
      { id: 3, text: "Elles sont magnifiques, merci !", time: "21:45", mine: true, seen: true },
    ],
  },
  {
    id: 5,
    name: "Bureau — Projet Kivu",
    initials: "PK",
    status: "12 participants",
    preview: "Vous : Document reçu, merci.",
    time: "Lun.",
    accent: "#d3a04a",
    messages: [
      { id: 1, text: "Voici la dernière version du document.", time: "14:12", mine: false },
      { id: 2, text: "📄 Planning-final.pdf", time: "14:13", mine: false },
      { id: 3, text: "Document reçu, merci.", time: "14:20", mine: true, seen: true },
    ],
  },
  {
    id: 6,
    name: "Amina",
    initials: "AM",
    status: "vue lundi à 19:30",
    preview: "À très bientôt !",
    time: "Lun.",
    accent: "#876fc2",
    messages: [
      { id: 1, text: "Merci pour ton aide aujourd'hui.", time: "17:10", mine: false },
      { id: 2, text: "Avec plaisir, à très bientôt !", time: "17:14", mine: true, seen: true },
    ],
  },
];

function Avatar({ chat, small = false }: { chat: Chat; small?: boolean }) {
  return (
    <div className={`avatar ${small ? "avatar-small" : ""}`} style={{ background: chat.accent }} aria-hidden="true">
      {chat.initials}
      {chat.online && <span className="online-dot" />}
    </div>
  );
}

export default function Home() {
  const [chats, setChats] = useState(starterChats);
  const [activeId, setActiveId] = useState(1);
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");
  const [showEmoji, setShowEmoji] = useState(false);
  const [showAttach, setShowAttach] = useState(false);
  const [dark, setDark] = useState(false);
  const [showConversation, setShowConversation] = useState(false);
  const [recording, setRecording] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const savedTheme = localStorage.getItem("whappy-theme");
    setDark(savedTheme === "dark");
  }, []);

  useEffect(() => {
    localStorage.setItem("whappy-theme", dark ? "dark" : "light");
  }, [dark]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [activeId, chats]);

  const activeChat = chats.find((chat) => chat.id === activeId) ?? chats[0];
  const filteredChats = useMemo(
    () => chats.filter((chat) => `${chat.name} ${chat.preview}`.toLowerCase().includes(query.toLowerCase())),
    [chats, query],
  );

  function selectChat(id: number) {
    setActiveId(id);
    setShowConversation(true);
    setChats((current) => current.map((chat) => (chat.id === id ? { ...chat, unread: undefined } : chat)));
  }

  function sendMessage(event: FormEvent) {
    event.preventDefault();
    const clean = message.trim();
    if (!clean) return;
    const now = new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    setChats((current) =>
      current.map((chat) =>
        chat.id === activeId
          ? {
              ...chat,
              preview: `Vous : ${clean}`,
              time: now,
              messages: [...chat.messages, { id: Date.now(), text: clean, time: now, mine: true, seen: true }],
            }
          : chat,
      ),
    );
    setMessage("");
    setShowEmoji(false);
  }

  function addEmoji(emoji: string) {
    setMessage((current) => current + emoji);
  }

  return (
    <main className={dark ? "app-shell dark" : "app-shell"}>
      <aside className={`sidebar ${showConversation ? "mobile-hidden" : ""}`}>
        <header className="sidebar-header">
          <div className="brand">
            <img src="/whappy-logo.svg" alt="Logo Whappy" />
            <div><h1>Whappy</h1><span>Connectés, simplement.</span></div>
          </div>
          <div className="header-actions">
            <button className="icon-button" aria-label="Changer le thème" title="Changer le thème" onClick={() => setDark((value) => !value)}>{dark ? "☀" : "◐"}</button>
            <button className="icon-button new-chat" aria-label="Nouvelle discussion" title="Nouvelle discussion">＋</button>
            <button className="icon-button" aria-label="Menu" title="Menu">⋮</button>
          </div>
        </header>

        <div className="search-wrap">
          <label className="search-box">
            <span aria-hidden="true">⌕</span>
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Rechercher une discussion" aria-label="Rechercher une discussion" />
            {query && <button onClick={() => setQuery("")} aria-label="Effacer la recherche">×</button>}
          </label>
        </div>

        <div className="filter-row" aria-label="Filtres de discussions">
          <button className="filter active">Toutes</button>
          <button className="filter">Non lues</button>
          <button className="filter">Groupes</button>
        </div>

        <div className="chat-list">
          {filteredChats.map((chat) => (
            <button key={chat.id} className={`chat-card ${activeId === chat.id ? "selected" : ""}`} onClick={() => selectChat(chat.id)}>
              <Avatar chat={chat} />
              <span className="chat-copy">
                <span className="chat-topline"><strong>{chat.name}</strong><time>{chat.time}</time></span>
                <span className="chat-bottomline"><span>{chat.preview}</span><span className="chat-meta">{chat.muted && "⌁"}{chat.unread ? <b>{chat.unread}</b> : null}</span></span>
              </span>
            </button>
          ))}
          {filteredChats.length === 0 && <div className="empty-search"><span>⌕</span><p>Aucune discussion trouvée</p></div>}
        </div>

        <footer className="sidebar-footer">
          <button><span>◎</span> Statuts</button>
          <button><span>⚙</span> Paramètres</button>
          <div className="profile-mini"><div className="me-avatar">CB</div><span><strong>Cyril</strong><small>Disponible</small></span></div>
        </footer>
      </aside>

      <section className={`conversation ${showConversation ? "mobile-visible" : ""}`}>
        <header className="conversation-header">
          <button className="back-button" onClick={() => setShowConversation(false)} aria-label="Retour aux discussions">‹</button>
          <Avatar chat={activeChat} small />
          <button className="contact-title" aria-label={`Informations sur ${activeChat.name}`}>
            <strong>{activeChat.name}</strong>
            <span>{activeChat.status}</span>
          </button>
          <div className="conversation-actions">
            <button className="icon-button" aria-label="Appel vidéo" title="Appel vidéo">▣</button>
            <button className="icon-button" aria-label="Appel audio" title="Appel audio">⌕</button>
            <button className="icon-button" aria-label="Rechercher" title="Rechercher">⌕</button>
            <button className="icon-button" aria-label="Plus d'options" title="Plus d'options">⋮</button>
          </div>
        </header>

        <div className="message-area">
          <div className="encryption-note"><span>🔒</span> Vos messages personnels sont chiffrés de bout en bout.</div>
          <div className="date-pill">AUJOURD'HUI</div>
          <div className="messages">
            {activeChat.messages.map((item) => (
              <div key={item.id} className={`message-row ${item.mine ? "mine" : "theirs"}`}>
                <div className="bubble">
                  <p>{item.text}</p>
                  <span className="message-time">{item.time} {item.mine && <i className={item.seen ? "seen" : ""}>✓✓</i>}</span>
                </div>
              </div>
            ))}
            <div ref={bottomRef} />
          </div>
        </div>

        <div className="composer-wrap">
          {showEmoji && (
            <div className="emoji-panel">
              <div className="emoji-title"><span>Emojis récents</span><button onClick={() => setShowEmoji(false)}>×</button></div>
              <div>{["😀", "😂", "🥰", "😍", "🤝", "👍", "🎉", "❤️", "🙏", "🔥", "✅", "🚀"].map((emoji) => <button key={emoji} onClick={() => addEmoji(emoji)}>{emoji}</button>)}</div>
            </div>
          )}
          {showAttach && (
            <div className="attach-panel">
              <button onClick={() => setShowAttach(false)}><span className="attach-icon purple">▧</span>Document</button>
              <button onClick={() => setShowAttach(false)}><span className="attach-icon blue">▦</span>Photos et vidéos</button>
              <button onClick={() => setShowAttach(false)}><span className="attach-icon green">♧</span>Contact</button>
            </div>
          )}
          <form className="composer" onSubmit={sendMessage}>
            <button type="button" className="composer-icon" aria-label="Choisir un emoji" onClick={() => { setShowEmoji((value) => !value); setShowAttach(false); }}>☺</button>
            <button type="button" className="composer-icon" aria-label="Joindre un fichier" onClick={() => { setShowAttach((value) => !value); setShowEmoji(false); }}>⌕</button>
            <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Écrire un message" aria-label="Écrire un message" />
            {message.trim() ? (
              <button type="submit" className="send-button" aria-label="Envoyer le message">➤</button>
            ) : (
              <button type="button" className={`send-button ${recording ? "recording" : ""}`} aria-label="Message vocal" onClick={() => setRecording((value) => !value)}>{recording ? "■" : "●"}</button>
            )}
          </form>
          {recording && <div className="recording-bar"><i /> Enregistrement en cours… <button onClick={() => setRecording(false)}>Annuler</button></div>}
        </div>
      </section>
    </main>
  );
}
