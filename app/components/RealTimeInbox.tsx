"use client";
/* eslint-disable jsx-a11y/media-has-caption -- messages vocaux créés par les utilisateurs sans piste de sous-titres */

import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
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
};

function timestampMillis(value: { toDate?: () => Date } | null | undefined) {
  return value?.toDate?.()?.getTime() || 0;
}

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH";
}

export function RealTimeInbox({ user, onCall, notify }: RealTimeInboxProps) {
  const [open, setOpen] = useState(false);
  const [conversations, setConversations] = useState<CloudConversation[]>([]);
  const [selected, setSelected] = useState("");
  const [items, setItems] = useState<CloudMessage[]>([]);
  const [text, setText] = useState("");
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
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

  const current = conversations.find((item) => item.id === selected) || conversations[0];
  const currentId = current?.id;
  const peer = useMemo(() => current?.members.find((member) => member.uid !== userId) || null, [current, userId]);

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

  async function uploadAttachment(file: File, kind: "image" | "audio", duration = 0, conversationId = currentId) {
    if (!conversationId || !userId || busy) return;
    setBusy(true);
    try {
      await sendDirectAttachment(conversationId, userId, file, kind, duration);
      notify(kind === "image" ? "Photo envoyée" : "Message vocal envoyé");
    } catch {
      notify(kind === "image" ? "La photo n’a pas pu être envoyée" : "Le message vocal n’a pas pu être envoyé");
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
        notify("Aucun compte Whappy trouvé avec ce numéro");
        return;
      }
      if (found.uid === user.uid) {
        notify("C’est votre propre numéro Whappy");
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

  if (!user) return null;

  return <>
    <button className="realtime-launch" onClick={() => setOpen(true)} aria-label="Ouvrir Whappy Direct">
      <span>⚡</span><strong>Messages en temps réel</strong>
      <small>{conversations.length ? `${conversations.length} conversation${conversations.length > 1 ? "s" : ""}` : "Démarrer avec un numéro Whappy"}</small><b>→</b>
    </button>
    {open && <div className="realtime-layer" role="dialog" aria-modal="true" aria-label="Whappy Direct">
      <section className="realtime-panel">
        <aside>
          <header><div><small>WHAPPY DIRECT</small><strong>Temps réel</strong></div><button onClick={() => setAdding(true)} aria-label="Nouvelle conversation">＋</button></header>
          {conversations.map((conversation) => {
            const person = conversation.members.find((member) => member.uid !== user.uid);
            const unread = timestampMillis(conversation.updatedAt) > timestampMillis(conversation.readBy?.[user.uid]);
            return <button className={current?.id === conversation.id ? "active" : ""} key={conversation.id} onClick={() => { stopTyping(); stopRecording(true); setSelected(conversation.id); }}>
              <span>{initials(person?.displayName || "Whappy")}</span>
              <div><strong>{person?.displayName || "Contact Whappy"}</strong><small>{conversation.typingBy?.[person?.uid || ""] ? "écrit…" : conversation.lastMessage || person?.phoneNumber}</small></div>
              <i className={unread ? "unread" : ""}>{unread ? "●" : ""}</i>
            </button>;
          })}
          {!conversations.length && <div className="realtime-empty"><span>⚡</span><strong>Aucune conversation réelle</strong><small>Ajoutez le numéro d’un autre compte Whappy.</small><button onClick={() => setAdding(true)}>＋ Nouveau message</button></div>}
        </aside>
        <main>{current && peer ? <>
          <header>
            <span>{initials(peer.displayName)}</span><div><strong>{peer.displayName}</strong><small>{current.typingBy?.[peer.uid] ? "écrit en ce moment…" : "● Synchronisé en direct"}</small></div>
            <button onClick={() => onCall(peer, false)} aria-label={`Appeler ${peer.displayName}`}>☎</button>
            <button onClick={() => onCall(peer, true)} aria-label={`Appel vidéo avec ${peer.displayName}`}>▣</button>
            <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>
          </header>
          <div className="realtime-messages">
            {!items.length && <div className="message-empty"><span>✦</span><strong>La conversation commence ici</strong><small>Vos messages apparaîtront instantanément sur les deux comptes.</small></div>}
            {items.map((message) => {
              const read = timestampMillis(current.readBy?.[peer.uid]) >= timestampMillis(message.createdAt);
              return <article className={`${message.senderId === user.uid ? "mine" : ""} ${message.kind === "image" ? "image" : ""}`} key={message.id}>
                {message.kind === "image" && message.mediaUrl ? <a className="message-photo" href={message.mediaUrl} target="_blank" rel="noreferrer" style={{ backgroundImage: `url(${message.mediaUrl})` }} aria-label="Ouvrir la photo"/> : message.kind === "audio" && message.mediaUrl ? <div className="message-vocal"><span>▶</span><audio controls preload="metadata" src={message.mediaUrl}/><b>{message.duration || 0}s</b></div> : <p>{message.text}</p>}
                <small>{message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) || "Envoi…"} {message.senderId === user.uid && (read ? "✓✓" : "✓")}</small>
              </article>;
            })}
            <div ref={messagesEnd}/>
          </div>
          {recording && <div className="recording-strip"><i/><strong>Message vocal · {recordingSeconds}s / 90s</strong><button onClick={() => stopRecording()}>Terminer et envoyer</button></div>}
          <form onSubmit={send}>
            <input className="message-file" ref={imageInput} type="file" accept="image/*" onChange={chooseImage}/>
            <button type="button" onClick={() => imageInput.current?.click()} disabled={busy || recording} aria-label="Ajouter une photo">＋</button>
            <button type="button" className={recording ? "recording" : ""} onClick={startRecording} disabled={busy} aria-label={recording ? "Terminer le message vocal" : "Enregistrer un message vocal"}>●</button>
            <input value={text} onChange={(event) => change(event.target.value)} onBlur={() => stopTyping(current.id)} maxLength={4000} autoComplete="off" aria-label={`Message à ${peer.displayName}`} placeholder={`Message à ${peer.displayName}`}/>
            <button disabled={busy || recording || !text.trim()} aria-label="Envoyer le message">➤</button>
          </form>
        </> : <div className="realtime-welcome">
          <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button><span>⚡</span><h3>Whappy Direct</h3><p>Échangez instantanément entre deux comptes identifiés par leur numéro.</p><button onClick={() => setAdding(true)}>Commencer une conversation</button>
        </div>}</main>
      </section>
      {adding && <form className="direct-create" onSubmit={addContact}>
        <header><div><small>NOUVELLE CONVERSATION</small><h3>Entrez son numéro Whappy</h3></div><button type="button" onClick={() => setAdding(false)} aria-label="Fermer">×</button></header>
        <label>Numéro international complet<input name="phone" required inputMode="tel" autoComplete="tel" placeholder="+242 06 000 00 00"/></label>
        <p>Le numéro doit déjà avoir créé un compte Whappy.</p><button disabled={busy}>{busy ? "Recherche…" : "Trouver le compte →"}</button>
      </form>}
    </div>}
  </>;
}
