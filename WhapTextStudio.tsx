"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { publishWhapText, watchWhapTexts, type CloudWhapText, type WhapTextTone } from "@/lib/whappy-data";

const toneLabel: Record<WhapTextTone, string> = {
  hope: "Espoir",
  action: "Action",
  community: "Communauté",
  warning: "Vigilance",
};

const starterPrompts = [
  { tone: "hope" as const, label: "Message positif", text: "Une petite avancée aujourd’hui peut ouvrir une grande opportunité demain." },
  { tone: "community" as const, label: "Entraide locale", text: "Je suis disponible pour aider une personne de ma communauté cette semaine." },
  { tone: "warning" as const, label: "Sensibilisation", text: "Vérifions toujours une information avant de la transmettre à nos proches." },
];

export function WhapTextStudio({ userId, userName, cloud, notify }: { userId: string; userName: string; cloud: boolean; notify: (text: string) => void }) {
  const [items, setItems] = useState<CloudWhapText[]>([]);
  const [draft, setDraft] = useState("");
  const [tone, setTone] = useState<WhapTextTone>("hope");
  const [kind, setKind] = useState<"status" | "whaptext">("whaptext");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(cloud);
  const [offline, setOffline] = useState(false);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);

  useEffect(() => {
    if (!cloud) return;
    return watchWhapTexts((next) => { setItems(next); setLoading(false); setOffline(false); }, () => { setLoading(false); setOffline(true); notifyRef.current("Les WhapText sont momentanément indisponibles."); });
  }, [cloud]);

  async function publish(event: FormEvent) {
    event.preventDefault();
    const value = draft.trim();
    if (value.length < 3 || busy) return;
    if (!cloud) {
      setItems((current) => [{ id: "local-" + Date.now(), authorId: "local", authorName: userName, text: value, tone, kind }, ...current]);
      setDraft("");
      notify("WhapText publié dans la démonstration");
      return;
    }
    setBusy(true);
    try {
      await publishWhapText(userId, userName, value, tone, kind);
      setDraft("");
      notify(kind === "status" ? "Votre statut est publié" : "Votre WhapText est publié");
    } catch {
      notify("La publication n’a pas pu être envoyée.");
    } finally {
      setBusy(false);
    }
  }

  return <section id="whaptext-studio" className="whaptext-studio" aria-labelledby="whaptext-title">
    <header>
      <div><span>WHAPTEXT</span><h2 id="whaptext-title">Des mots qui font avancer.</h2><p>Partagez un statut, une idée ou un message de sensibilisation utile à la communauté.</p></div>
      <b>{items.length} publiés</b>
    </header>
    <form onSubmit={publish}>
      <textarea id="whaptext-composer" aria-label="Votre WhapText" value={draft} onChange={(event) => setDraft(event.target.value.slice(0, 600))} maxLength={600} placeholder="Ex. Vérifions les sources avant de partager une information. Une communauté forte protège les siens." />
      <div className="whaptext-compose-tools">
        <div>{(["whaptext", "status"] as const).map((item) => <button type="button" className={kind === item ? "active" : ""} onClick={() => setKind(item)} key={item}>{item === "status" ? "Statut" : "WhapText"}</button>)}</div>
        <select value={tone} onChange={(event) => setTone(event.target.value as WhapTextTone)} aria-label="Ton du message">
          {Object.entries(toneLabel).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
        </select>
        <small>{draft.length}/600</small>
        <button className="publish" disabled={busy || draft.trim().length < 3}>{busy ? "Publication…" : "Publier"}</button>
      </div>
    </form>
    <div className="whaptext-feed">
      {items.length ? items.map((item) => <article className={item.tone} key={item.id}>
        <header><span>{item.kind === "status" ? "STATUT" : "WHAPTEXT"} · {toneLabel[item.tone]}</span><small>{item.authorName}</small></header>
        <p>{item.text}</p>
      </article>) : <div className="whaptext-starters"><header><div><small>{loading ? "SYNCHRONISATION" : offline ? "MODE HORS LIGNE" : "POUR COMMENCER"}</small><strong>{loading ? "Chargement des publications…" : offline ? "Rédigez maintenant, publiez quand la connexion revient." : "Choisissez une idée et personnalisez-la."}</strong></div><span>✦</span></header><div>{starterPrompts.map((prompt) => <button type="button" key={prompt.label} onClick={() => { setTone(prompt.tone); setDraft(prompt.text); }}><span>{prompt.tone === "hope" ? "☀" : prompt.tone === "community" ? "◎" : "◇"}</span><strong>{prompt.label}</strong><small>{prompt.text}</small><b>Utiliser →</b></button>)}</div></div>}
    </div>
  </section>;
}
