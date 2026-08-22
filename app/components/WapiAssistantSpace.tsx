"use client";

import { FormEvent, useState } from "react";

type AssistantSpace = "home" | "contacts" | "orbit" | "inbox" | "calls" | "games" | "business" | "radio";
type AssistantMessage = { from: "wapi" | "you"; text: string };

const suggestions: Array<{ label: string; destination: AssistantSpace; reply: string }> = [
  { label: "Ouvrir mes contacts", destination: "contacts", reply: "J’ouvre vos contacts WAPI." },
  { label: "Voir les Actus", destination: "orbit", reply: "J’ouvre les Stories et les Actus." },
  { label: "Lancer un appel", destination: "calls", reply: "J’ouvre votre espace Appels." },
  { label: "Jouer au Ludo", destination: "games", reply: "J’ouvre l’arène Jeux & Tournois." },
];

export function WapiAssistantSpace({ onNavigate }: { onNavigate: (space: AssistantSpace) => void }) {
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<AssistantMessage[]>([
    { from: "wapi", text: "Bonjour. Je peux vous guider vers les fonctions WAPI du web comme dans l’APK." },
  ]);

  function runCommand(text: string, destination?: AssistantSpace, reply?: string) {
    const value = text.trim();
    if (!value) return;
    const normalized = value.toLowerCase();
    const match = destination ? { destination, reply: reply || "J’ouvre cet espace WAPI." } :
      normalized.includes("contact") ? suggestions[0] :
      normalized.includes("actus") || normalized.includes("story") || normalized.includes("stories") ? suggestions[1] :
      normalized.includes("appel") ? suggestions[2] :
      normalized.includes("ludo") || normalized.includes("jeu") ? suggestions[3] :
      normalized.includes("message") ? { destination: "inbox" as const, reply: "J’ouvre vos messages WAPI." } : null;
    setMessages((current) => [...current, { from: "you", text: value }, { from: "wapi", text: match?.reply || "Je peux ouvrir vos contacts, les Actus, les appels, les messages ou le Ludo." }]);
    setPrompt("");
    if (match) window.setTimeout(() => onNavigate(match.destination), 180);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    runCommand(prompt);
  }

  return <section className="wapi-assistant-space" aria-label="Assistant WAPI">
    <div className="wapi-assistant-hero"><div><span className="mobile-parity-eyebrow">WAPI ASSISTANT · PRIVÉ ET CONTRÔLÉ</span><h2>Un guide simple pour retrouver l’APK sur le web.</h2><p>Utilisez une commande ou choisissez une action. L’assistant ne prétend pas répondre à votre place : il vous conduit vers le bon espace WAPI.</p></div><span className="wapi-assistant-mark">✦</span></div>
    <div className="wapi-assistant-layout">
      <div className="wapi-assistant-chat" aria-live="polite">
        {messages.map((message, index) => <div className={`wapi-assistant-message ${message.from}`} key={`${message.from}-${index}`}><span>{message.from === "wapi" ? "W" : "Vous"}</span><p>{message.text}</p></div>)}
      </div>
      <aside className="wapi-assistant-actions"><small>ACTIONS APK → WEB</small>{suggestions.map((item) => <button key={item.destination} onClick={() => runCommand(item.label, item.destination, item.reply)}><span>{item.destination === "contacts" ? "◎" : item.destination === "orbit" ? "▦" : item.destination === "calls" ? "☎" : "♞"}</span><strong>{item.label}</strong><b>↗</b></button>)}</aside>
    </div>
    <form className="wapi-assistant-compose" onSubmit={submit}><span>✦</span><input value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="Ex. montre-moi mes Stories ou ouvre mes contacts" aria-label="Commande à l’assistant WAPI" /><button disabled={!prompt.trim()}>Envoyer</button></form>
    <p className="wapi-assistant-note">Les réponses d’action restent locales à cette interface tant qu’un service assistant distant validé n’est pas connecté.</p>
  </section>;
}
