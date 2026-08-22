"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

type RadioMessage = {
  id: string;
  name: string;
  text: string;
  pinned?: boolean;
  hidden?: boolean;
  hearts?: number;
};

type Caller = {
  id: string;
  name: string;
  motif: string;
  isVip?: boolean;
  hearts?: number;
};

export type RadioSession = {
  status: "offline" | "live" | "paused";
  title: string;
  elapsed: number;
  listeners: number;
  micOn: boolean;
};

type Props = {
  active: boolean;
  tab: "live" | "podcasts";
  onTabChange: (tab: "live" | "podcasts") => void;
  hostName: string;
  notify: (message: string) => void;
  onSessionChange: (session: RadioSession) => void;
  onOpenInbox: () => void;
};

type Episode = {
  id: string;
  title: string;
  description: string;
  category: "Actualités" | "Business" | "Culture" | "Conseils" | "Autres";
  duration: string;
  status: "Publié" | "Brouillon";
};

type GiftPack = {
  id: string;
  name: string;
  hearts: number;
  priceLabel: string;
  tone: "soft" | "plus";
};

const maxQueueSize = 10;

const giftCatalog: GiftPack[] = [
  { id: "gift-soft-200", name: "Cœur", hearts: 1, priceLabel: "200 FCFA", tone: "soft" },
  { id: "gift-soft-500", name: "Double-cœur", hearts: 2, priceLabel: "500 FCFA", tone: "soft" },
  { id: "gift-premium-1000", name: "Cœur premium", hearts: 5, priceLabel: "1 000 FCFA", tone: "plus" },
  { id: "gift-premium-2500", name: "Feu d’écoute", hearts: 12, priceLabel: "2 500 FCFA", tone: "plus" },
];

const initialLineup = [
  { id: "slot-1", time: "00:00", title: "Ouverture & salutations", host: "Host", notes: "Accroche + présentation" },
  { id: "slot-2", time: "00:12", title: "Actu locale", host: "Studio", notes: "Focus ville et culture" },
  { id: "slot-3", time: "00:25", title: "Dédicace & coup de cœur", host: "Ouverture aux auditeurs", notes: "Laisser parler les auditeurs" },
];

type LineupItem = {
  id: string;
  time: string;
  title: string;
  host: string;
  notes: string;
};

function duration(seconds: number) {
  return `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
}

function timeToMinute(value: string) {
  const [hoursPart, minutesPart] = value.split(":");
  const hours = Number.parseInt(hoursPart, 10);
  const minutes = Number.parseInt(minutesPart, 10);
  if (Number.isNaN(hours) || Number.isNaN(minutes)) return 0;
  return hours * 60 + minutes;
}

function isLineupItemActive(item: LineupItem, list: LineupItem[], currentSeconds: number) {
  const nowMinute = Math.floor(currentSeconds / 60);
  const sorted = [...list].sort((a, b) => timeToMinute(a.time) - timeToMinute(b.time));
  const current = sorted.find((entry, index) => {
    const start = timeToMinute(entry.time);
    const next = sorted[index + 1];
    const end = next ? timeToMinute(next.time) : Number.POSITIVE_INFINITY;
    return nowMinute >= start && nowMinute < end;
  });
  return current?.id === item.id;
}

export function RadioStudio({ active, tab, onTabChange, hostName, notify, onSessionChange, onOpenInbox }: Props) {
  const streamRef = useRef<MediaStream | null>(null);
  const [status, setStatus] = useState<RadioSession["status"]>("offline");
  const [title, setTitle] = useState("WAPI Radio · Nouvelle émission");
  const [elapsed, setElapsed] = useState(0);
  const [listeners, setListeners] = useState(0);
  const [micOn, setMicOn] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState<RadioMessage[]>([]);
  const [queuedCallers, setQueuedCallers] = useState<Caller[]>([]);
  const [onAirCaller, setOnAirCaller] = useState<Caller | null>(null);
  const [guestMuted, setGuestMuted] = useState(false);
  const [manualCallerName, setManualCallerName] = useState("");
  const [manualCallerMotif, setManualCallerMotif] = useState("");
  const [manualCallerVip, setManualCallerVip] = useState(false);
  const [episodeTitle, setEpisodeTitle] = useState("");
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [lineup, setLineup] = useState<LineupItem[]>(initialLineup);
  const [lineupTime, setLineupTime] = useState("00:00");
  const [lineupTitle, setLineupTitle] = useState("");
  const [lineupHost, setLineupHost] = useState("");
  const [lineupNotes, setLineupNotes] = useState("");
  const [selectedGiftPackId, setSelectedGiftPackId] = useState(giftCatalog[0].id);
  const [episodeDescription, setEpisodeDescription] = useState("");
  const [episodeCategory, setEpisodeCategory] = useState<Episode["category"]>("Actualités");
  const [isPodcastRecording, setIsPodcastRecording] = useState(false);
  const [podcastSeconds, setPodcastSeconds] = useState(0);

  function stopMicrophone() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  useEffect(() => () => stopMicrophone(), []);

  useEffect(() => {
    onSessionChange({ status, title, elapsed, listeners, micOn });
  }, [elapsed, listeners, micOn, onSessionChange, status, title]);

  useEffect(() => {
    if (status !== "live") return;
    const timer = window.setInterval(() => {
      setElapsed((value) => value + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [status]);

  useEffect(() => {
    if (!isPodcastRecording) return;
    const timer = window.setInterval(() => {
      setPodcastSeconds((value) => value + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [isPodcastRecording]);

  const sortedLineup = [...lineup].sort((a, b) => timeToMinute(a.time) - timeToMinute(b.time));
  const activeLineup = sortedLineup.find((entry, index) => {
    const currentMinutes = Math.floor(elapsed / 60);
    const start = timeToMinute(entry.time);
    const next = sortedLineup[index + 1];
    const end = next ? timeToMinute(next.time) : Number.POSITIVE_INFINITY;
    return currentMinutes >= start && currentMinutes < end;
  }) || sortedLineup[0];
  const selectedGiftPack = giftCatalog.find((pack) => pack.id === selectedGiftPackId) ?? giftCatalog[0];

  async function prepareMicrophone() {
    setError("");
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("Ce navigateur ne permet pas d’utiliser le micro.");
      return false;
    }
    try {
      stopMicrophone();
      streamRef.current = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
      setMicOn(true);
      return true;
    } catch (reason) {
      const name = reason instanceof DOMException ? reason.name : "";
      setError(name === "NotAllowedError" ? "Autorisez le micro pour démarrer votre radio." : "Le micro est indisponible. Vérifiez qu’il n’est pas déjà utilisé.");
      return false;
    }
  }

  async function startRadio() {
    if (!title.trim()) { setError("Donnez un titre à votre émission."); return; }
    if (!streamRef.current && !(await prepareMicrophone())) return;
    setStatus("live");
    setListeners(0);
    notify("Votre radio est en direct. Vous pouvez continuer à utiliser Whappy.");
  }

  function pauseRadio() {
    setStatus("paused");
    streamRef.current?.getAudioTracks().forEach((track) => { track.enabled = false; });
    setMicOn(false);
    notify("Radio mise en pause");
  }

  function resumeRadio() {
    streamRef.current?.getAudioTracks().forEach((track) => { track.enabled = true; });
    setMicOn(true);
    setStatus("live");
    notify("Radio reprise en direct");
  }

  function endRadio() {
    stopMicrophone();
    setMicOn(false);
    setStatus("offline");
    notify(`Radio terminée · ${duration(elapsed)}`);
  }

  function toggleMicrophone() {
    const tracks = streamRef.current?.getAudioTracks();
    if (!tracks?.length) { setError("Démarrez la radio pour utiliser le micro."); return; }
    const next = !tracks[0].enabled;
    tracks.forEach((track) => { track.enabled = next; });
    setMicOn(next);
  }

  function reply(event: FormEvent) {
    event.preventDefault();
    const text = message.trim();
    if (!text) return;
    setMessages((current) => [...current, { id: `m-${Date.now()}`, name: "Vous", text }]);
    setMessage("");
  }

  function sendGiftToMessage(messageId: string) {
    setMessages((current) => current.map((item) => (item.id === messageId ? { ...item, hearts: (item.hearts || 0) + selectedGiftPack.hearts } : item)));
    const recipient = messages.find((item) => item.id === messageId);
    if (!recipient) return;
    notify(`Cadeau ${selectedGiftPack.name} offert à ${recipient.name} (${selectedGiftPack.priceLabel})`);
  }

  function sendGiftToCaller(callerId: string) {
    let targetName = "";
    setQueuedCallers((current) => current.map((item) => {
      if (item.id !== callerId) return item;
      targetName = item.name;
      return { ...item, hearts: (item.hearts || 0) + selectedGiftPack.hearts };
    }));
    if (targetName) {
      notify(`Cadeau ${selectedGiftPack.name} offert à ${targetName} (${selectedGiftPack.priceLabel})`);
    }
  }

  function exportRadioLog() {
    const exportPayload = {
      exportedAt: new Date().toISOString(),
      title,
      elapsedSeconds: elapsed,
      status,
      listeners,
      micOn,
      messages: messages.map(({ id, name, text, pinned, hidden, hearts }) => ({
        id,
        name,
        text,
        pinned: Boolean(pinned),
        hidden: Boolean(hidden),
        hearts,
      })),
      lineUp: lineup,
      queuedCallers,
      onAirCaller,
    };
    const blob = new Blob([JSON.stringify(exportPayload, null, 2)], { type: "application/json;charset=utf-8" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `radio-log-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
    notify("Log d’émission exporté.");
  }

  function addLineup(event: FormEvent) {
    event.preventDefault();
    const titleValue = lineupTitle.trim();
    const hostValue = lineupHost.trim();
    if (!lineupTime || !titleValue || !hostValue) return;
    const next = {
      id: `slot-${Date.now()}`,
      time: lineupTime,
      title: titleValue,
      host: hostValue,
      notes: lineupNotes.trim(),
    };
    setLineup((current) => [...current, next].sort((a, b) => timeToMinute(a.time) - timeToMinute(b.time)));
    setLineupTime("00:00");
    setLineupTitle("");
    setLineupHost("");
    setLineupNotes("");
    notify(`Créneau ajouté : ${next.title}`);
  }

  function removeLineup(id: string) {
    setLineup((current) => current.filter((item) => item.id !== id));
  }

  function replyTo(name: string) {
    setMessage(`@${name} `);
  }

  function pinMessage(messageId: string) {
    setMessages((current) =>
      current.map((item) =>
        item.id === messageId ? { ...item, pinned: !item.pinned, hidden: false } : item,
      ),
    );
  }

  function hideMessage(messageId: string) {
    setMessages((current) =>
      current.map((item) => (item.id === messageId ? { ...item, hidden: true } : item)),
    );
  }

  function removeMessage(messageId: string) {
    setMessages((current) => current.filter((item) => item.id !== messageId));
  }

  function queueCaller(event: FormEvent) {
    event.preventDefault();
    const name = manualCallerName.trim();
    const motif = manualCallerMotif.trim() || "Demande d’antenne";
    if (!name) return;
    if (queuedCallers.length >= maxQueueSize) {
      notify(`File d’attente complète. Maximum autorisé: ${maxQueueSize} auditeurs.`);
      return;
    }
    setQueuedCallers((current) => [{ id: `c-${Date.now()}`, name, motif, isVip: manualCallerVip }, ...current]);
    setManualCallerName("");
    setManualCallerMotif("");
    setManualCallerVip(false);
    notify(`Demande d’antenne ajoutée : ${name}`);
  }

  const orderedQueuedCallers = [...queuedCallers].sort((a, b) => {
    if ((a.isVip ? 1 : 0) === (b.isVip ? 1 : 0)) return 0;
    return a.isVip ? -1 : 1;
  });

  function acceptCaller(id: string) {
    setQueuedCallers((current) => {
      const next = current.find((item) => item.id === id);
      if (!next) return current;
      if (onAirCaller) {
        notify("Terminez l’invité actuel avant d’accepter un nouveau micro.");
        return current;
      }
      setOnAirCaller(next);
      setGuestMuted(false);
      notify(`Invité accepté : ${next.name} est en on air.`);
      return current.filter((item) => item.id !== id);
    });
  }

  function rejectCaller(id: string) {
    setQueuedCallers((current) => current.filter((item) => item.id !== id));
    const caller = queuedCallers.find((item) => item.id === id);
    if (caller) notify(`Demande refusée : ${caller.name}`);
  }

  function endCaller() {
    if (!onAirCaller) return;
    notify(`Le passage de ${onAirCaller.name} est terminé.`);
    setOnAirCaller(null);
    setGuestMuted(false);
  }

  function toggleGuestMic() {
    setGuestMuted((value) => !value);
  }

  function createEpisode(event: FormEvent) {
    event.preventDefault();
    const next = episodeTitle.trim();
    const nextDescription = episodeDescription.trim() || "Épisode prêt pour validation.";
    if (!next) return;
    setEpisodes((current) => [
      {
        id: `episode-${Date.now()}`,
        title: next,
        description: nextDescription,
        category: episodeCategory,
        duration: "À enregistrer",
        status: "Brouillon",
      },
      ...current,
    ]);
    setEpisodeTitle("");
    setEpisodeDescription("");
    setEpisodeCategory("Actualités");
    notify("Épisode podcast créé en brouillon");
  }

  function startRecordingPodcast() {
    if (isPodcastRecording) return;
    setIsPodcastRecording(true);
    setPodcastSeconds(0);
    notify("Enregistrement du podcast en cours. Le contenu direct sera transformé en brouillon.");
  }

  function stopRecordingPodcast() {
    if (!isPodcastRecording) return;
    const draftTitle = episodeTitle.trim() || `${title} · ${duration(podcastSeconds)}`;
    const newEpisode = {
      id: `episode-${Date.now()}`,
      title: draftTitle,
      description: `Enregistrement live de ${hostName}.`,
      category: episodeCategory,
      duration: duration(podcastSeconds),
      status: "Brouillon" as const,
    };
    setEpisodes((current) => [newEpisode, ...current]);
    setIsPodcastRecording(false);
    setEpisodeTitle("");
    setEpisodeDescription("");
    setPodcastSeconds(0);
    setEpisodeCategory("Actualités");
    notify(`Podcast "${newEpisode.title}" sauvegardé (${newEpisode.duration}).`);
  }

  function toggleEpisodePublication(episodeId: string) {
    let nextEpisodeTitle = "";
    let wasPublished = false;
    setEpisodes((current) => current.map((item) => {
      if (item.id !== episodeId) return item;
      nextEpisodeTitle = item.title;
      wasPublished = item.status === "Publié";
      return { ...item, status: item.status === "Publié" ? "Brouillon" : "Publié" };
    }));
    if (nextEpisodeTitle) {
      notify(wasPublished ? `L’épisode "${nextEpisodeTitle}" a été repassé en brouillon.` : `L’épisode "${nextEpisodeTitle}" est maintenant publié.`);
    }
  }

  function removeEpisode(episodeId: string) {
    let removedTitle = "";
    setEpisodes((current) => current.filter((item) => {
      if (item.id !== episodeId) return true;
      removedTitle = item.title;
      return false;
    }));
    if (removedTitle) notify(`Épisode supprimé : ${removedTitle}`);
  }

  return (
    <section className={`radio-space ${active ? "" : "radio-hidden"}`} aria-hidden={!active}>
      <header className="radio-hero">
        <div>
          <span>
            <i className={status === "live" ? "live-dot" : ""} />
            WHAPPY RADIO & PODCAST
          </span>
          <h2>
            Votre voix.
            <br />
            <em>Partout avec vous.</em>
          </h2>
          <p>
            {hostName}, lancez une radio en direct, préparez vos épisodes podcast et répondez à vos auditeurs sans quitter Whappy.
          </p>
        </div>
        <aside>
          <small>{status === "live" ? "EN DIRECT" : status === "paused" ? "EN PAUSE" : "PRÊT À DIFFUSER"}</small>
          <strong>{duration(elapsed)}</strong>
          <span>{listeners} auditeur{listeners > 1 ? "s" : ""}</span>
          <div className={`radio-wave ${status === "live" && micOn ? "active" : ""}`} aria-label={micOn ? "Micro actif" : "Micro coupé"}>
            {Array.from({ length: 18 }, (_, index) => <i key={index} />)}
          </div>
        </aside>
      </header>

      <div className="radio-tabs">
        <button className={tab === "live" ? "active" : ""} onClick={() => onTabChange("live")}>
          Régie direct
        </button>
        <button className={tab === "podcasts" ? "active" : ""} onClick={() => onTabChange("podcasts")}>
          Mes podcasts <span>{episodes.length}</span>
        </button>
      </div>

      {tab === "live" ? (
        <div className="radio-grid">
          <section className="radio-console">
            <header>
              <div>
                <small>ÉMISSION EN COURS</small>
                <h3>{title || "Sans titre"}</h3>
              </div>
              <span className={status}>{status === "live" ? "● LIVE" : status === "paused" ? "Ⅱ PAUSE" : "○ HORS LIGNE"}</span>
            </header>

            <label>
              Nom de l’émission
              <input value={title} onChange={(event) => setTitle(event.target.value.slice(0, 90))} disabled={status === "live"} />
            </label>

            <div className="radio-actions">
              <button className={micOn ? "active" : ""} onClick={toggleMicrophone}>
                ◉ <span>{micOn ? "Micro actif" : "Micro coupé"}</span>
              </button>
              <button onClick={() => onOpenInbox()}>
                ◫ <span>Messages</span>
              </button>
              <button onClick={() => notify("Lien d’invitation prêt à partager")}>
                ↗ <span>Inviter</span>
              </button>
            </div>

            <section className="radio-program" aria-live="polite">
              <small>ENREGISTREMENT PODCAST</small>
              <strong>{isPodcastRecording ? `Enregistrement en cours (${duration(podcastSeconds)})` : "Mode podcast prêt"}</strong>
              <p>Enregistrez votre émission en direct pour créer un épisode brouillon en un clic.</p>
              <div className="radio-lineup-form" style={{ marginTop: "10px" }}>
                {isPodcastRecording ? (
                  <button type="button" onClick={stopRecordingPodcast}>
                    ◉ Arrêter l&apos;enregistrement
                  </button>
                ) : (
                  <button type="button" onClick={startRecordingPodcast}>
                    ⏺️ Démarrer l&apos;enregistrement
                  </button>
                )}
              </div>
            </section>

            <div className="radio-program">
              <small>AU PROGRAMME</small>
              <strong>Conversation, musique dont vous détenez les droits et dédicaces.</strong>
              <p>Le direct continue lorsque vous ouvrez vos messages, vos commandes ou tout autre espace Whappy.</p>
            </div>

            <section className="radio-lineup">
              <header>
                <small>RÉPARTITION DU LIVE</small>
                <h4>Planning d’émission</h4>
              </header>
              <p className="radio-lineup-current">{activeLineup ? `Actuellement : ${activeLineup.time} · ${activeLineup.title}` : "Aucun segment"}</p>
              <form className="radio-lineup-form" onSubmit={addLineup}>
                <input
                  type="time"
                  value={lineupTime}
                  onChange={(event) => setLineupTime(event.target.value)}
                />
                <input
                  value={lineupTitle}
                  onChange={(event) => setLineupTitle(event.target.value.slice(0, 50))}
                  placeholder="Titre du segment"
                />
                <input value={lineupHost} onChange={(event) => setLineupHost(event.target.value.slice(0, 40))} placeholder="Animateur / invité" />
                <input value={lineupNotes} onChange={(event) => setLineupNotes(event.target.value.slice(0, 100))} placeholder="Notes rapides" />
                <button disabled={!lineupTime || !lineupTitle.trim() || !lineupHost.trim()}>Ajouter créneau</button>
              </form>
              <div className="radio-lineup-list">
                {sortedLineup.map((slot) => (
                  <article key={slot.id} className={isLineupItemActive(slot, lineup, elapsed) ? "active" : ""}>
                    <div>
                      <b>{slot.time}</b>
                      <strong>{slot.title}</strong>
                      <small>{slot.host}</small>
                      <span>{slot.notes}</span>
                    </div>
                    <button type="button" className="radio-lineup-delete" onClick={() => removeLineup(slot.id)}>
                      Supprimer
                    </button>
                  </article>
                ))}
              </div>
            </section>

            <section className="radio-callroom">
              <header>
                <small>RÉGIE AUDITEURS</small>
                <h4>Appels en attente</h4>
              </header>
              <p className="radio-call-sub">
                Vous pouvez accepter un invité en on-air, le couper si besoin, et garder la régie propre.
              </p>
              <div className="radio-queue-meta">
                <span>File active : {queuedCallers.length}/{maxQueueSize}</span>
                <label className="radio-gift-selector">
                  <span>Cadeau pro</span>
                  <select value={selectedGiftPackId} onChange={(event) => setSelectedGiftPackId(event.target.value)}>
                    {giftCatalog.map((pack) => <option value={pack.id} key={pack.id}>{pack.name} · {pack.priceLabel}</option>)}
                  </select>
                </label>
              </div>

              <div className="radio-call-status">
                <strong>{onAirCaller ? `On air: ${onAirCaller.name}` : "Aucun invité en on-air"}</strong>
                {onAirCaller && (
                  <div className="radio-call-host">
                    <span className={`badge ${guestMuted ? "off" : "on"}`}>{guestMuted ? "Invité muet" : "Invité actif"}</span>
                    <button onClick={toggleGuestMic}>{guestMuted ? "Autoriser l’invité" : "Couper l’invité"}</button>
                    <button onClick={endCaller}>Terminer l’appel</button>
                  </div>
                )}
              </div>

              <form className="radio-manual-call" onSubmit={queueCaller}>
                <input
                  value={manualCallerName}
                  onChange={(event) => setManualCallerName(event.target.value.slice(0, 80))}
                  placeholder="Nom auditeur"
                />
                <input
                  value={manualCallerMotif}
                  onChange={(event) => setManualCallerMotif(event.target.value.slice(0, 140))}
                  placeholder="Motif (optionnel)"
                />
                <label className="radio-call-vip">
                  <input type="checkbox" checked={manualCallerVip} onChange={(event) => setManualCallerVip(event.target.checked)} />
                  VIP
                </label>
                <button disabled={!manualCallerName.trim() || queuedCallers.length >= maxQueueSize}>Ajouter demande</button>
              </form>

              <div className="radio-queue">
                {queuedCallers.length === 0 ? (
                  <p className="radio-empty">Aucune demande en attente.</p>
                ) : (
                  orderedQueuedCallers.slice(0, maxQueueSize).map((caller) => (
                    <article key={caller.id} className="radio-queue-item">
                      <div>
                        <b>{caller.name}</b>
                        {caller.isVip ? <span className="vip-label">VIP</span> : null}
                        <span>{caller.motif}</span>
                        <small className="radio-caller-hearts">💜 {caller.hearts || 0}</small>
                      </div>
                      <div className="radio-call-controls">
                        <button onClick={() => acceptCaller(caller.id)}>Accepter</button>
                        <button type="button" onClick={() => sendGiftToCaller(caller.id)}>Offrir</button>
                        <button className="ghost" onClick={() => rejectCaller(caller.id)}>
                          Refuser
                        </button>
                      </div>
                    </article>
                  ))
                )}
              </div>
            </section>

            {error && <p className="radio-error">! {error}</p>}

            <footer>
              {status === "offline" ? (
                <button className="radio-live-button" onClick={startRadio}>
                  ● Démarrer ma radio
                </button>
              ) : status === "paused" ? (
                <button className="radio-live-button" onClick={resumeRadio}>
                  ▶ Reprendre le direct
                </button>
              ) : (
                <button className="radio-pause-button" onClick={pauseRadio}>
                  Ⅱ Mettre en pause
                </button>
              )}
              {status !== "offline" && (
                <button className="radio-end-button" onClick={endRadio}>
                  Terminer
                </button>
              )}
            </footer>
          </section>

          <aside className="radio-messages">
              <header>
                <div>
                  <small>MESSAGES AUDITEURS</small>
                  <h3>Répondez en direct</h3>
                </div>
                <span>{messages.length}</span>
                <button className="radio-export-button" onClick={exportRadioLog} title="Exporter le log de modération et planning">
                  Export log
                </button>
              </header>
            <div>
              {messages
                .filter((message) => !message.hidden)
                .map((item) => (
                    <article key={item.id} className={`${item.name === "Vous" ? "mine" : ""} ${item.pinned ? "pinned" : ""}`}>
                      <div>
                        <b>{item.name}</b>
                        <small className="radio-message-hearts">❤ {item.hearts || 0}</small>
                        <p>{item.text}</p>
                      </div>
                      <div className="radio-message-actions">
                        <button type="button" onClick={() => sendGiftToMessage(item.id)} className="radio-gift-button">
                          ❤ {selectedGiftPack.name} (+{selectedGiftPack.hearts})
                        </button>
                        <button type="button" onClick={() => replyTo(item.name)}>
                          Répondre
                        </button>
                        <button type="button" onClick={() => pinMessage(item.id)} className={item.pinned ? "ghost active" : "ghost"}>
                          {item.pinned ? "Désépingler" : "Épingler"}
                      </button>
                      <button type="button" onClick={() => hideMessage(item.id)} className="ghost">
                        Masquer
                      </button>
                      <button type="button" onClick={() => removeMessage(item.id)} className="danger">
                        Supprimer
                      </button>
                      </div>
                    </article>
                  ))}
            </div>
            <form onSubmit={reply}>
              <input
                value={message}
                onChange={(event) => setMessage(event.target.value.slice(0, 280))}
                placeholder="Répondre à un auditeur…"
              />
              <button disabled={!message.trim()} aria-label="Envoyer la réponse">
                ➤
              </button>
            </form>
          </aside>
        </div>
      ) : (
        <div className="podcast-grid">
          <section className="podcast-create">
            <small>NOUVEL ÉPISODE</small>
            <h3>Créez votre podcast</h3>
            <p>Préparez un vrai format pro : titre, thème, notes de publication et mettez en ligne quand vous êtes prêt.</p>
            <form onSubmit={createEpisode}>
              <input value={episodeTitle} onChange={(event) => setEpisodeTitle(event.target.value.slice(0, 100))} placeholder="Titre de l’épisode" />
              <select value={episodeCategory} onChange={(event) => setEpisodeCategory(event.target.value as Episode["category"])}>
                <option value="Actualités">Actualités</option>
                <option value="Business">Business</option>
                <option value="Culture">Culture</option>
                <option value="Conseils">Conseils</option>
                <option value="Autres">Autres</option>
              </select>
              <input value={episodeDescription} onChange={(event) => setEpisodeDescription(event.target.value.slice(0, 180))} placeholder="Résumé (optionnel)" />
              <button disabled={!episodeTitle.trim()}>Créer un brouillon →</button>
            </form>
            <div>
              <span>✓ Votre voix, vos droits</span>
              <span>✓ Replay maîtrisé</span>
              <span>✓ Publication quand vous décidez</span>
            </div>
          </section>

            <section className="podcast-list">
            <header>
              <div>
                <small>BIBLIOTHÈQUE</small>
                <h3>Épisodes</h3>
              </div>
              <button onClick={() => onTabChange("live")}>Aller à la radio</button>
            </header>
            {episodes.map((episode) => (
              <article key={episode.id}>
                <span>◉</span>
                <div>
                  <strong>{episode.title}</strong>
                  <small>{episode.category} · {episode.duration}</small>
                  <span>{episode.description}</span>
                </div>
                <b className={episode.status === "Publié" ? "published" : "draft"}>{episode.status}</b>
                <div className="podcast-item-actions">
                  <button onClick={() => notify(episode.status === "Publié" ? "Lecture du podcast prête" : "Ouvrez le brouillon pour enregistrer votre épisode")}>▶</button>
                  <button onClick={() => toggleEpisodePublication(episode.id)}>{episode.status === "Publié" ? "↺" : "✓"}</button>
                  <button className="danger" onClick={() => removeEpisode(episode.id)}>×</button>
                </div>
              </article>
            ))}
          </section>
        </div>
      )}

      <small className="radio-note">Le micro fonctionne réellement sur cet appareil. La diffusion publique audio et les replays seront branchés au service de streaming Whappy avant publication.</small>
    </section>
  );
}
