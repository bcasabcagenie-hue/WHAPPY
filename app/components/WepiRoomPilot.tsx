"use client";

import { useEffect, useRef, useState } from "react";
import { buildWepiRoomSuggestion, defaultWepiRoomPilot, saveWepiRoomPilot, watchWepiRoomPilot, type WepiRoomPilotSettings } from "@/lib/whappy-wepi";
import type { WhappyRoom, WhappyRoomPost } from "@/lib/whappy-rooms";

type WepiRoomPilotProps = {
  room: WhappyRoom;
  posts: WhappyRoomPost[];
  userId: string;
  cloud: boolean;
  notify: (text: string) => void;
  onUseSuggestion: (text: string) => void;
};

export function WepiRoomPilot({ room, posts, userId, cloud, notify, onUseSuggestion }: WepiRoomPilotProps) {
  const [settings, setSettings] = useState<WepiRoomPilotSettings>(() => defaultWepiRoomPilot(room.id, userId));
  const [suggestion, setSuggestion] = useState("");
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    if (!cloud) {
      let active = true;
      queueMicrotask(() => {
        if (!active) return;
        try {
          const saved = JSON.parse(localStorage.getItem(`whappy-wepi-room-${room.id}`) || "null") as Partial<WepiRoomPilotSettings> | null;
          if (saved) setSettings({ ...defaultWepiRoomPilot(room.id, userId), ...saved, roomId: room.id, ownerId: userId });
        } catch { /* La salle reste pilotable sans stockage local. */ }
      });
      return () => { active = false; };
    }
    return watchWepiRoomPilot(room.id, userId, setSettings, () => notifyRef.current("Le pilotage WEPI de cette salle est momentanément indisponible"));
  }, [cloud, room.id, userId]);
  useEffect(() => {
    if (cloud) return;
    try { localStorage.setItem(`whappy-wepi-room-${room.id}`, JSON.stringify(settings)); } catch { /* Le mode local reste disponible en mémoire. */ }
  }, [cloud, room.id, settings]);

  async function update(key: "enabled" | "autoModeration" | "announcementAssist") {
    const next = { ...settings, [key]: !settings[key] };
    setSettings(next);
    try {
      if (cloud) await saveWepiRoomPilot(next);
      notify(next.enabled ? "WEPI pilote cette salle avec votre validation" : "WEPI est en pause pour cette salle");
    } catch {
      setSettings(settings);
      notify("Le réglage de pilotage WEPI n’a pas pu être enregistré");
    }
  }

  function generateSuggestion() {
    const next = buildWepiRoomSuggestion(room, posts);
    setSuggestion(next);
    notify("WEPI a préparé une idée pour votre salle");
  }

  return <section className="wepi-room-pilot">
    <header><div><span className="wepi-mark">W</span><div><small>WHAPPY INTELLIGENCE · PILOTAGE</small><h3>WEPI accompagne votre salle</h3></div></div><span className={`wepi-status ${settings.enabled ? "on" : "off"}`}><i />{settings.enabled ? "ACTIF" : "EN PAUSE"}</span></header>
    <p>WEPI observe l’activité, prépare des idées et signale les contenus à vérifier. Il ne publie ni ne supprime rien sans votre validation.</p>
    <div className="wepi-room-metrics"><span><b>{room.memberCount.toLocaleString("fr-FR")}</b> membres</span><span><b>{posts.length}</b> publications visibles</span><span><b>{posts.filter((post) => post.pinned).length}</b> épinglées</span></div>
    <div className="wepi-room-switches"><button type="button" className={settings.enabled ? "active" : ""} onClick={() => void update("enabled")}><b>{settings.enabled ? "✓" : "○"}</b><span><strong>Activer le pilotage</strong><small>WEPI vous aide à garder le cap</small></span></button><button type="button" className={settings.autoModeration ? "active" : ""} onClick={() => void update("autoModeration")} disabled={!settings.enabled}><b>{settings.autoModeration ? "✓" : "○"}</b><span><strong>Veille de modération</strong><small>Repérer le hors-sujet à vérifier</small></span></button><button type="button" className={settings.announcementAssist ? "active" : ""} onClick={() => void update("announcementAssist")} disabled={!settings.enabled}><b>{settings.announcementAssist ? "✓" : "○"}</b><span><strong>Aide aux annonces</strong><small>Préparer des publications utiles</small></span></button></div>
    <div className="wepi-room-suggestion"><div><small>COACHING WEPI</small><p>{suggestion || "Demandez à WEPI une idée adaptée au thème et à l’activité de votre communauté."}</p></div><div><button type="button" onClick={generateSuggestion} disabled={!settings.enabled}>Générer une idée ↗</button>{suggestion && <button type="button" className="secondary" onClick={() => { onUseSuggestion(suggestion); notify("L’idée WEPI est prête à être relue avant publication"); }}>Utiliser dans l’éditeur</button>}</div></div>
  </section>;
}
