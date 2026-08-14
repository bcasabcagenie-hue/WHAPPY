"use client";
/* eslint-disable jsx-a11y/media-has-caption -- messages vocaux créés par les utilisateurs sans piste de sous-titres */

import { ChangeEvent, FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { watchCallHistory, type CallSignal } from "@/lib/whappy-calls";
import { translateTextOnDevice, WhappyExpressionHub } from "@/app/components/WhappyExpressionHub";
import {
  ensureDirectConversation,
  findWhappyUserByPhone,
  markDirectConversationRead,
  sendDirectAttachment,
  sendDirectMessage,
  setDirectTyping,
  watchDirectConversations,
  watchDirectMessages,
  type CloudConversation,
  type CloudMessage,
  type DirectMember,
} from "@/lib/whappy-data";

type RealTimeInboxProps = {
  user: DirectMember | null;
  onCall: (peer: DirectMember, video: boolean) => void;
  notify: (text: string) => void;
  embedded?: boolean;
  composeToken?: number;
  search?: string;
};

function timestampMillis(value: { toDate?: () => Date } | null | undefined) {
  return value?.toDate?.()?.getTime() || 0;
}

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH";
}

function callPeer(call: CallSignal, userId: string): DirectMember {
  const outgoing = call.callerId === userId;
  return { uid: outgoing ? call.calleeId : call.callerId, displayName: outgoing ? call.calleeName : call.callerName, phoneNumber: "" };
}

function callLabel(call: CallSignal, userId: string) {
  if (call.status === "ringing") return call.callerId === userId ? "Appel en cours" : "Appel entrant";
  if (call.status === "declined") return "Refusé";
  if (call.status === "accepted" || call.answer) return "Terminé";
  return call.calleeId === userId ? "Manqué" : "Annulé";
}

export function RealTimeInbox({ user, onCall, notify, embedded = false, composeToken = 0, search = "" }: RealTimeInboxProps) {
  const [open, setOpen] = useState(embedded);
  const [view, setView] = useState<"messages" | "calls">("messages");
  const [conversations, setConversations] = useState<CloudConversation[]>([]);
  const [calls, setCalls] = useState<CallSignal[]>([]);
  const [selected, setSelected] = useState("");
  const [items, setItems] = useState<CloudMessage[]>([]);
  const [text, setText] = useState("");
  const [adding, setAdding] = useState(composeToken > 0);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [expressionOpen, setExpressionOpen] = useState(false);
  const [messageTranslations, setMessageTranslations] = useState<Record<string, string>>({});
  const [translatingMessage, setTranslatingMessage] = useState("");
  const typingTimer = useRef<number | null>(null);
  const typingConversation = useRef("");
  const messagesEnd = useRef<HTMLDivElement>(null);
  const imageInput = useRef<HTMLInputElement>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const recorderStream = useRef<MediaStream | null>(null);
  const recordingTimer = useRef<number | null>(null);
  const recordingDuration = useRef(0);
  const discardRecording = useRef(false);
  const notifyRef = useRef(notify);
  const openRef = useRef(open);
  const userId = user?.uid;

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => { openRef.current = open; }, [open]);
  useEffect(() => {
    if (!userId) return;
    return watchDirectConversations(userId, setConversations, () => notifyRef.current("Messagerie directe momentanément hors ligne"));
  }, [userId]);
  useEffect(() => {
    if (!userId) return;
    return watchCallHistory(userId, setCalls, () => notifyRef.current("Historique des appels momentanément indisponible"));
  }, [userId]);

  const current = conversations.find((item) => item.id === selected) || conversations[0];
  const currentId = current?.id;
  const peer = useMemo(() => current?.members.find((member) => member.uid !== userId) || null, [current, userId]);
  const searchValue = search.trim().toLowerCase();
  const visibleConversations = useMemo(() => conversations.filter((conversation) => !searchValue || conversation.members.some((member) => `${member.displayName} ${member.phoneNumber}`.toLowerCase().includes(searchValue))), [conversations, searchValue]);
  const visibleCalls = useMemo(() => calls.filter((call) => !searchValue || `${call.callerName} ${call.calleeName}`.toLowerCase().includes(searchValue)), [calls, searchValue]);

  useEffect(() => {
    if (!currentId || !userId) return;
    return watchDirectMessages(currentId, (messages) => {
      setItems(messages);
      if (openRef.current) void markDirectConversationRead(currentId, userId);
    }, () => notifyRef.current("Messages momentanément hors ligne"));
  }, [currentId, userId]);

  useEffect(() => {
    if (open && currentId && userId) void markDirectConversationRead(currentId, userId);
  }, [open, currentId, userId]);

  useEffect(() => {
    if (open) messagesEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [items, open]);

  useEffect(() => () => {
    if (typingTimer.current) window.clearTimeout(typingTimer.current);
    if (typingConversation.current && userId) void setDirectTyping(typingConversation.current, userId, false);
    discardRecording.current = true;
    if (recordingTimer.current) window.clearInterval(recordingTimer.current);
    if (recorder.current?.state === "recording") recorder.current.stop();
    recorderStream.current?.getTracks().forEach((track) => track.stop());
  }, [userId]);

  function stopTyping(conversationId = typingConversation.current) {
    if (typingTimer.current) window.clearTimeout(typingTimer.current);
    typingTimer.current = null;
    if (conversationId && userId) void setDirectTyping(conversationId, userId, false);
    if (typingConversation.current === conversationId) typingConversation.current = "";
  }

  function closePanel() {
    stopTyping();
    stopRecording(true);
    setAdding(false);
    setOpen(false);
  }

  function stopRecording(discard = false) {
    discardRecording.current = discard;
    if (recordingTimer.current) window.clearInterval(recordingTimer.current);
    recordingTimer.current = null;
    if (recorder.current?.state === "recording") recorder.current.stop();
    recorderStream.current?.getTracks().forEach((track) => track.stop());
    setRecording(false);
  }

  async function uploadAttachment(file: File, kind: "image" | "audio" | "video", duration = 0, conversationId = currentId, effect = "", caption = "") {
    if (!conversationId || !userId || busy) return;
    setBusy(true);
    try {
      await sendDirectAttachment(conversationId, userId, file, kind, duration, effect, caption);
      notify(kind === "image" ? "Image envoyée" : kind === "video" ? "Vidéo WHAPPY envoyée" : "Message vocal envoyé");
    } catch {
      notify(kind === "image" ? "L’image n’a pas pu être envoyée" : kind === "video" ? "La vidéo n’a pas pu être envoyée" : "Le message vocal n’a pas pu être envoyé");
    } finally {
      setBusy(false);
    }
  }

  async function chooseImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file) await uploadAttachment(file, "image");
  }

  async function startRecording() {
    if (!current || !user || busy) return;
    if (recording) {
      stopRecording();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : "";
      const mediaRecorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      const chunks: Blob[] = [];
      const conversationId = current.id;
      recorder.current = mediaRecorder;
      recorderStream.current = stream;
      discardRecording.current = false;
      mediaRecorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
      mediaRecorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        if (discardRecording.current || !chunks.length) return;
        const duration = Math.max(1, recordingDuration.current);
        const blob = new Blob(chunks, { type: mediaRecorder.mimeType || "audio/webm" });
        const extension = blob.type.includes("mp4") ? "m4a" : "webm";
        const file = new File([blob], `message-vocal.${extension}`, { type: blob.type });
        void uploadAttachment(file, "audio", duration, conversationId);
      };
      mediaRecorder.start(500);
      recordingDuration.current = 0;
      setRecordingSeconds(0);
      setRecording(true);
      recordingTimer.current = window.setInterval(() => {
        setRecordingSeconds((value) => {
          recordingDuration.current = value + 1;
          if (value >= 89) {
            window.setTimeout(() => stopRecording(), 0);
            return 90;
          }
          return value + 1;
        });
      }, 1000);
    } catch {
      notify("Autorisez le microphone pour enregistrer un message vocal");
    }
  }

  async function addContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    const form = new FormData(event.currentTarget);
      const phone = String(form.get("phone") || "");
    setBusy(true);
    try {
      const found = await findWhappyUserByPhone(phone);
      if (!found) {
        notify("Aucun compte trouvé avec cet ID ou ce numéro Whappy");
        return;
      }
      if (found.uid === user.uid) {
        notify("C’est votre propre compte Whappy");
        return;
      }
      const id = await ensureDirectConversation(user, found);
      setSelected(id);
      setAdding(false);
      setOpen(true);
      notify(`Conversation en temps réel avec ${found.displayName} ouverte`);
    } catch {
      notify("La recherche du numéro a échoué");
    } finally {
      setBusy(false);
    }
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!current || !user || !text.trim() || busy) return;
    const value = text.trim();
    setText("");
    stopTyping(current.id);
    setBusy(true);
    try {
      await sendDirectMessage(current.id, user.uid, value);
    } catch {
      setText(value);
      notify("Le message n’a pas été envoyé");
    } finally {
      setBusy(false);
    }
  }

  function change(value: string) {
    setText(value);
    if (!current || !user) return;
    if (!value.trim()) {
      stopTyping(current.id);
      return;
    }
    if (typingConversation.current !== current.id) {
      if (typingConversation.current) stopTyping();
      typingConversation.current = current.id;
      void setDirectTyping(current.id, user.uid, true);
    }
    if (typingTimer.current) window.clearTimeout(typingTimer.current);
    typingTimer.current = window.setTimeout(() => stopTyping(current.id), 1200);
  }

  function composerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  async function translateMessage(message: CloudMessage) {
    if (!message.text || translatingMessage) return;
    if (messageTranslations[message.id]) {
      setMessageTranslations((current) => { const next = { ...current }; delete next[message.id]; return next; });
      return;
    }
    setTranslatingMessage(message.id);
    try { const value = await translateTextOnDevice(message.text, "fr"); setMessageTranslations((current) => ({ ...current, [message.id]: value })); }
    catch { notify("La traduction locale de ce message n’est pas disponible sur cet appareil"); }
    finally { setTranslatingMessage(""); }
  }

  if (!user) return null;

  const answeredCalls = calls.filter((call) => Boolean(call.answer)).length;
  const missedCalls = calls.filter((call) => call.calleeId === user.uid && call.status === "ended" && !call.answer).length;

  return <>
    {!embedded && <button className="realtime-launch" onClick={() => setOpen(true)} aria-label="Ouvrir Whappy Direct">
      <span>⚡</span><strong>Messages en temps réel</strong>
      <small>{conversations.length ? `${conversations.length} conversation${conversations.length > 1 ? "s" : ""}` : "Démarrer avec un numéro Whappy"}</small><b>→</b>
    </button>}
    {(embedded || open) && <div className={`realtime-layer ${embedded ? "embedded" : ""}`} role={embedded ? undefined : "dialog"} aria-modal={embedded ? undefined : true} aria-label="Whappy Direct">
      <section className={`realtime-panel ${embedded ? "embedded" : ""}`}>
        <aside>
          <header><div><small>WHAPPY DIRECT</small><strong>Communications</strong></div><button onClick={() => setAdding(true)} aria-label="Nouvelle conversation">＋</button></header>
          <nav className="direct-tabs" aria-label="Sections Whappy Direct">
            <button className={view === "messages" ? "active" : ""} onClick={() => setView("messages")}>◫ Discussions</button>
            <button className={view === "calls" ? "active" : ""} onClick={() => setView("calls")}>☎ Appels <b>{calls.length}</b></button>
          </nav>
          {view === "messages" ? <>
            {visibleConversations.map((conversation) => {
              const person = conversation.members.find((member) => member.uid !== user.uid);
              const unread = timestampMillis(conversation.updatedAt) > timestampMillis(conversation.readBy?.[user.uid]);
              return <button className={current?.id === conversation.id ? "active" : ""} key={conversation.id} onClick={() => { stopTyping(); stopRecording(true); setSelected(conversation.id); }}>
                <span>{initials(person?.displayName || "Whappy")}</span>
                <div><strong>{person?.displayName || "Contact Whappy"}</strong><small>{conversation.typingBy?.[person?.uid || ""] ? "écrit…" : conversation.lastMessage || person?.phoneNumber}</small></div>
                <i className={unread ? "unread" : ""}>{unread ? "●" : ""}</i>
              </button>;
            })}
            {!visibleConversations.length && <div className="realtime-empty"><span>⚡</span><strong>{searchValue ? "Aucun résultat" : "Aucune conversation réelle"}</strong><small>{searchValue ? "Essayez un autre nom ou numéro." : "Ajoutez le numéro d’un autre compte Whappy."}</small>{!searchValue && <button onClick={() => setAdding(true)}>＋ Nouveau message</button>}</div>}
          </> : <div className="call-summary">
            <span>☎</span><strong>{calls.length} appel{calls.length > 1 ? "s" : ""}</strong><small>Historique synchronisé entre vos appareils</small>
            <div><p><b>{answeredCalls}</b> aboutis</p><p><b>{missedCalls}</b> manqués</p></div>
          </div>}
        </aside>
        <main>{view === "calls" ? <>
          <header className="call-history-head"><span>☎</span><div><strong>Historique des appels</strong><small>Audio et vidéo · Synchronisé en direct</small></div>{!embedded && <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>}</header>
          <div className="call-history">
            {visibleCalls.map((call) => {
              const person = callPeer(call, user.uid);
              const outgoing = call.callerId === user.uid;
              const label = callLabel(call, user.uid);
              const date = call.createdAt?.toDate?.();
              return <article className={label === "Manqué" ? "missed" : ""} key={call.id}>
                <span>{initials(person.displayName)}</span><div><strong>{person.displayName}</strong><small>{outgoing ? "↗ Sortant" : "↙ Entrant"} · {label}</small><time>{date ? date.toLocaleString("fr-FR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "À l’instant"}</time></div>
                <i>{call.video ? "VIDÉO" : "AUDIO"}</i><button onClick={() => onCall(person, false)} aria-label={`Rappeler ${person.displayName}`}>☎</button><button onClick={() => onCall(person, true)} aria-label={`Appel vidéo avec ${person.displayName}`}>▣</button>
              </article>;
            })}
            {!visibleCalls.length && <div className="call-history-empty"><span>☎</span><h3>{searchValue ? "Aucun appel trouvé" : "Aucun appel pour le moment"}</h3><p>{searchValue ? "Modifiez votre recherche pour retrouver un contact." : "Ouvrez une discussion et lancez votre premier appel Whappy."}</p><button onClick={() => setView("messages")}>Voir les discussions</button></div>}
          </div>
        </> : current && peer ? <>
          <header>
            <span>{initials(peer.displayName)}</span><div><strong>{peer.displayName}</strong><small>{current.typingBy?.[peer.uid] ? "écrit en ce moment…" : "● Synchronisé en direct"}</small></div>
            <button onClick={() => onCall(peer, false)} aria-label={`Appeler ${peer.displayName}`}>☎</button><button onClick={() => onCall(peer, true)} aria-label={`Appel vidéo avec ${peer.displayName}`}>▣</button>{!embedded && <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>}
          </header>
          <div className="realtime-messages">
            {!items.length && <div className="message-empty"><span>✦</span><strong>La conversation commence ici</strong><small>Vos messages apparaîtront instantanément sur les deux comptes.</small></div>}
            {items.map((message) => {
              const read = timestampMillis(current.readBy?.[peer.uid]) >= timestampMillis(message.createdAt);
              return <article className={`${message.senderId === user.uid ? "mine" : ""} ${message.kind === "image" ? "image" : ""} ${message.kind === "video" ? "video" : ""}`} key={message.id}>
                {message.kind === "image" && message.mediaUrl ? <a className="message-photo" href={message.mediaUrl} target="_blank" rel="noreferrer" style={{ backgroundImage: `url(${message.mediaUrl})` }} aria-label="Ouvrir la photo"/> : message.kind === "video" && message.mediaUrl ? <div className="message-video"><video controls playsInline preload="metadata" src={message.mediaUrl} className={message.effect || "pop"}/>{message.caption && <strong>{message.caption}</strong>}<i>✦ WHAPPY VIDEO</i></div> : message.kind === "audio" && message.mediaUrl ? <div className="message-vocal"><span>▶</span><audio controls preload="metadata" src={message.mediaUrl}/><b>{message.duration || 0}s</b></div> : <><p>{message.text}</p>{messageTranslations[message.id] && <p className="message-translation"><b>FR</b>{messageTranslations[message.id]}</p>}<button className="message-translate" onClick={() => void translateMessage(message)}>{translatingMessage === message.id ? "Traduction…" : messageTranslations[message.id] ? "Masquer" : "文 Traduire"}</button></>}
                <small>{message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) || "Envoi…"} {message.senderId === user.uid && (read ? "✓✓" : "✓")}</small>
              </article>;
            })}<div ref={messagesEnd}/>
          </div>
          {recording && <div className="recording-strip"><i/><strong>Message vocal · {recordingSeconds}s / 90s</strong><button onClick={() => stopRecording()}>Terminer et envoyer</button></div>}
          {expressionOpen && <WhappyExpressionHub draft={text} onDraftChange={change} notify={notify} onClose={() => setExpressionOpen(false)} onSendMedia={(file, kind, effect, caption) => uploadAttachment(file, kind, 0, current.id, effect, caption)}/>}
          <form className="direct-composer" onSubmit={send}>
            <input className="message-file" ref={imageInput} type="file" accept="image/*" onChange={chooseImage}/>
            <button type="button" onClick={() => setExpressionOpen((value) => !value)} className={expressionOpen ? "active" : ""} disabled={busy || recording} aria-label="Emojis, traduction et créations WHAPPY" title="Emojis, traduction et Meme Lab">☺</button>
            <button type="button" onClick={() => imageInput.current?.click()} disabled={busy || recording} aria-label="Ajouter une photo" title="Ajouter une photo">＋</button>
            <button type="button" className={recording ? "recording" : ""} onClick={startRecording} disabled={busy} aria-label={recording ? "Terminer le message vocal" : "Enregistrer un message vocal"} title="Note vocale">●</button>
            <textarea value={text} onChange={(event) => change(event.target.value)} onKeyDown={composerKeyDown} onBlur={() => stopTyping(current.id)} maxLength={4000} rows={1} spellCheck lang="fr" autoComplete="off" aria-label={`Message à ${peer.displayName}`} placeholder={`Message à ${peer.displayName} · Entrée pour envoyer`}/>
            <button disabled={busy || recording || !text.trim()} aria-label="Envoyer le message" title="Envoyer">➤</button>
            <small>Entrée envoie · Maj + Entrée ajoute une ligne · Orthographe activée</small>
          </form>
        </> : <div className="realtime-welcome">{!embedded && <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>}<span>⚡</span><h3>Whappy Direct</h3><p>Échangez instantanément entre deux comptes identifiés par leur numéro.</p><button onClick={() => setAdding(true)}>Commencer une conversation</button></div>}</main>
      </section>
      {adding && <form className="direct-create" onSubmit={addContact}><header><div><small>NOUVELLE CONVERSATION</small><h3>ID ou numéro Whappy</h3></div><button type="button" onClick={() => setAdding(false)} aria-label="Fermer">×</button></header><label>Identifiant du contact<input name="phone" required autoComplete="off" placeholder="WH-12AB34CD ou +242…"/></label><p>Chaque accès direct possède un ID visible dans le profil. Le numéro reste accepté pour les anciens comptes.</p><button disabled={busy}>{busy ? "Recherche…" : "Trouver le compte →"}</button></form>}
    </div>}
  </>;
}
