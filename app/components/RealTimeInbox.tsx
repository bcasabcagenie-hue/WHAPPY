"use client";
/* eslint-disable @next/next/no-img-element -- aperçu d'une URL Firebase dynamique */
/* eslint-disable jsx-a11y/media-has-caption -- messages vocaux créés par les utilisateurs sans piste de sous-titres */

import { ChangeEvent, FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { watchCallHistory, type CallSignal } from "@/lib/whappy-calls";
import { translateTextOnDevice, WhappyExpressionHub } from "@/app/components/WhappyExpressionHub";
import {
  ensureDirectConversation,
  findWhappyUserById,
  findWhappyUserByPhone,
  markDirectConversationRead,
  markDirectMessageViewed,
  purgeExpiredDirectMessages,
  sendDirectAttachment,
  sendDirectMessage,
  setDirectEphemeralMode,
  setDirectTyping,
  watchDirectConversations,
  watchDirectMessages,
  watchWhappyUsersById,
  type CloudConversation,
  type CloudMessage,
  type DirectMember,
} from "@/lib/whappy-data";
import { buildWepiReply, watchWepiSettings, type WepiSettings } from "@/lib/whappy-wepi";
import { playMessageSound } from "@/lib/whappy-sounds";
import { decryptWhappyText, ensureEncryptionIdentity, getEncryptionPublicKey } from "@/lib/whappy-encryption";

type RealTimeInboxProps = {
  user: DirectMember | null;
  onCall: (peer: DirectMember, video: boolean) => void;
  notify: (text: string) => void;
  embedded?: boolean;
  composeToken?: number;
  composePhone?: string;
  composePeer?: DirectMember | null;
  search?: string;
  initialView?: "messages" | "calls";
  founder?: boolean;
};

function timestampMillis(value: { toDate?: () => Date } | null | undefined) {
  return value?.toDate?.()?.getTime() || 0;
}

function messageTime(value: { toDate?: () => Date } | null | undefined) {
  return value?.toDate?.()?.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) || "À l’instant";
}

function normalizePhone(value = "") {
  return value.replace(/\D/g, "");
}

function isHappyFounderPhone(value?: string | null) {
  return normalizePhone(value || "") === "242065465808";
}

function isVerifiedMember(person: DirectMember | null | undefined) {
  return Boolean(person?.verified || person?.accountType === "business" || isHappyFounderPhone(person?.phoneNumber));
}

function formatLastSeenLabel(value: { toDate?: () => Date } | null | undefined) {
  const time = messageTime(value);
  return time === "À l’instant" ? "" : `à ${time}`;
}

function buildDirectPresenceLine(person: DirectMember | null | undefined, lastSeenAt: { toDate?: () => Date } | null | undefined, isTyping: boolean, isOnline: boolean, isRead: boolean) {
  const seenLabel = formatLastSeenLabel(lastSeenAt);
  if (!person) return "";
  if (isTyping) return "● En ligne · écrit en ce moment…";
  const status = isOnline ? "● En ligne" : "○ Hors ligne";
  if (!seenLabel) return `${status} · ${isRead ? "Vu" : "vu récemment"}`;
  return `${status} · ${isRead ? `Vu ${seenLabel}` : `vu ${seenLabel}`}`;
}

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH";
}

function DirectAvatar({ person }: { person: DirectMember | null | undefined }) {
  const name = person?.displayName || "Whappy";
  return <span className="direct-live-avatar">{initials(name)}{person?.photoUrl ? <img src={person.photoUrl} alt={name} /> : null}</span>;
}

function linkHost(value: string) {
  try { return new URL(value).hostname.replace(/^www\./, ""); }
  catch { return "Lien partagé"; }
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

export function RealTimeInbox({ user, onCall, notify, embedded = false, composeToken = 0, composePhone = "", composePeer = null, search = "", initialView = "messages", founder }: RealTimeInboxProps) {
  const [open, setOpen] = useState(embedded);
  const [view, setView] = useState<"messages" | "calls">(composeToken ? "messages" : initialView);
  const [conversations, setConversations] = useState<CloudConversation[]>([]);
  const [optimisticConversation, setOptimisticConversation] = useState<CloudConversation | null>(null);
  const [calls, setCalls] = useState<CallSignal[]>([]);
  const [selected, setSelected] = useState("");
  const [items, setItems] = useState<CloudMessage[]>([]);
  const [text, setText] = useState("");
  const [adding, setAdding] = useState(composeToken > 0 && !composePhone.trim() && !composePeer);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [expressionOpen, setExpressionOpen] = useState(false);
  const [messageTranslations, setMessageTranslations] = useState<Record<string, string>>({});
  const [translatingMessage, setTranslatingMessage] = useState("");
  const [translationTarget, setTranslationTarget] = useState<"fr" | "en">("fr");
  const [ephemeralSeconds, setEphemeralSeconds] = useState<0 | 86400 | 604800>(0);
  const [ephemeralMenuOpen, setEphemeralMenuOpen] = useState(false);
  const [presenceTick, setPresenceTick] = useState(() => Date.now());
  const [viewOnceMode, setViewOnceMode] = useState(false);
  const [viewedOnceIds, setViewedOnceIds] = useState<Record<string, boolean>>({});
  const [openedViewOnceIds, setOpenedViewOnceIds] = useState<Record<string, boolean>>({});
  const [photoPreview, setPhotoPreview] = useState("");
  const [photoZoom, setPhotoZoom] = useState(1);
  const [photoViewOnceId, setPhotoViewOnceId] = useState("");
  const [videoViewOnce, setVideoViewOnce] = useState<{ id: string; src: string } | null>(null);
  const [voiceViewOnce, setVoiceViewOnce] = useState<{ id: string; src: string; duration?: number } | null>(null);
  const [hdMedia, setHdMedia] = useState(true);
  const typingTimer = useRef<number | null>(null);
  const typingConversation = useRef("");
  const messagesEnd = useRef<HTMLDivElement>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const openPhoneRef = useRef<(phone: string) => Promise<void>>(async () => undefined);
  const openPeerRef = useRef<(peer: DirectMember) => Promise<void>>(async () => undefined);
  const mediaInput = useRef<HTMLInputElement>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const recorderStream = useRef<MediaStream | null>(null);
  const recordingTimer = useRef<number | null>(null);
  const recordingDuration = useRef(0);
  const discardRecording = useRef(false);
  const notifyRef = useRef(notify);
  const openRef = useRef(open);
  const userId = user?.uid;
  const [wepiSettings, setWepiSettings] = useState<WepiSettings | null>(null);
  const [peerProfile, setPeerProfile] = useState<DirectMember | null>(null);
  const [liveProfiles, setLiveProfiles] = useState<Record<string, DirectMember>>({});
  const wepiHandledRef = useRef(new Set<string>());
  const wepiReadyRef = useRef(false);
  const wepiConversationRef = useRef("");
  const itemsConversationRef = useRef("");
  const lastMessageSoundIds = useRef<Record<string, string>>({});
  const pendingMessageRef = useRef<{ conversationId: string; text: string; clientMessageId: string } | null>(null);
  const [replyTarget, setReplyTarget] = useState<CloudMessage | null>(null);
  const [forwardingMessage, setForwardingMessage] = useState<CloudMessage | null>(null);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => { openRef.current = open; }, [open]);
  useEffect(() => {
    if (!userId) return;
    void ensureEncryptionIdentity(userId).catch(() => notifyRef.current("Le chiffrement sécurisé n’a pas pu être initialisé"));
    return watchDirectConversations(userId, setConversations, () => notifyRef.current("Messagerie directe momentanément hors ligne"));
  }, [userId]);
  const profileIds = useMemo(() => [...new Set([userId || "", ...conversations.flatMap((conversation) => conversation.memberIds)].filter(Boolean))].sort(), [conversations, userId]);
  const profileIdsKey = profileIds.join("|");
  useEffect(() => {
    return watchWhappyUsersById(profileIds, setLiveProfiles, () => notifyRef.current("Les photos de profil sont momentanément indisponibles"));
  // The stable key prevents resubscribing when only conversation metadata changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profileIdsKey]);
  useEffect(() => {
    if (!userId) return;
    return watchWepiSettings(userId, setWepiSettings, () => notifyRef.current("WEPI est momentanément indisponible"));
  }, [userId]);
  useEffect(() => {
    if (!userId) return;
    return watchCallHistory(userId, setCalls, () => notifyRef.current("Historique des appels momentanément indisponible"));
  }, [userId]);

  const selectedConversation = conversations.find((item) => item.id === selected);
  const current = selectedConversation || (selected && optimisticConversation?.id === selected ? optimisticConversation : conversations[0]);
  const currentId = current?.id;
  const peer = useMemo(() => current?.members.find((member) => member.uid !== userId) || null, [current, userId]);
  const livePeer = peer?.uid ? liveProfiles[peer.uid] || peer : null;
  const activePeerProfile = livePeer || (peerProfile?.uid === peer?.uid ? peerProfile : null);
  useEffect(() => {
    if (!peer?.uid || peer.uid === userId) return;
    let active = true;
    void findWhappyUserById(peer.uid).then((profile) => {
      if (active) setPeerProfile(profile);
    }).catch(() => {
      if (active) setPeerProfile(null);
    });
    return () => { active = false; };
  }, [peer?.uid, userId]);
  const searchValue = search.trim().toLowerCase();
  const visibleConversations = useMemo(() => {
    const query = conversations
      .filter((conversation) => !searchValue || conversation.members.some((member) => `${member.displayName} ${member.phoneNumber}`.toLowerCase().includes(searchValue)))
      .map((conversation) => {
        const person = conversation.members.find((member) => member.uid !== userId);
        const updatedAt = timestampMillis(conversation.updatedAt);
        return {
          conversation,
          personName: person?.displayName || "",
          unread: updatedAt > timestampMillis(conversation.readBy?.[userId || ""]),
          updatedAt,
        };
      })
      .sort((left, right) => {
        if (left.unread !== right.unread) return left.unread ? -1 : 1;
        if (right.updatedAt !== left.updatedAt) return right.updatedAt - left.updatedAt;
        return left.personName.localeCompare(right.personName, "fr", { sensitivity: "base" });
      });
    return query;
  }, [conversations, searchValue, userId]);
  const visibleCalls = useMemo(() => calls.filter((call) => !searchValue || `${call.callerName} ${call.calleeName}`.toLowerCase().includes(searchValue)), [calls, searchValue]);

  useEffect(() => {
    if (!currentId || !userId) return;
    const subscribedConversationId = currentId;
    return watchDirectMessages(currentId, (messages) => {
      itemsConversationRef.current = subscribedConversationId;
      const now = Date.now();
      const visibleMessages = messages.filter((message) => (message.expiresAt?.toDate?.()?.getTime() || now + 1) > now);
      const latest = visibleMessages[visibleMessages.length - 1];
      const previousLatestId = lastMessageSoundIds.current[subscribedConversationId];
      if (previousLatestId && latest && latest.id !== previousLatestId && latest.senderId !== userId) playMessageSound();
      if (latest) lastMessageSoundIds.current[subscribedConversationId] = latest.id;
      const peerId = visibleMessages.find((message) => message.senderId !== userId)?.senderId || peer?.uid;
      void (async () => {
        const peerPublicKey = peerId ? await getEncryptionPublicKey(peerId) : undefined;
        const decrypted = await Promise.all(visibleMessages.map(async (message) => {
          if (!message.encryptionNonce || !message.encryptionVersion || !peerPublicKey) return message;
          try { return { ...message, text: await decryptWhappyText(message.text, userId, peerPublicKey, message.encryptionNonce) }; }
          catch { return { ...message, text: "🔒 Message protégé — clé indisponible", decryptionFailed: true }; }
        }));
        if (itemsConversationRef.current === subscribedConversationId) setItems(decrypted);
      })();
      void purgeExpiredDirectMessages(currentId, messages);
      if (openRef.current) void markDirectConversationRead(currentId, userId);
    }, () => notifyRef.current("Messages momentanément hors ligne"));
  }, [currentId, peer?.uid, userId]);

  useEffect(() => {
    if (wepiConversationRef.current === currentId) return;
    wepiConversationRef.current = currentId || "";
    wepiReadyRef.current = false;
  }, [currentId]);

  useEffect(() => {
    if (!currentId || itemsConversationRef.current !== currentId || !userId || !wepiSettings?.enabled || !wepiSettings.autoReply || !items.length) return;
    if (!wepiReadyRef.current) {
      items.forEach((message) => wepiHandledRef.current.add(message.id));
      wepiReadyRef.current = true;
      return;
    }
    const latest = items[items.length - 1];
    if (!latest || latest.senderId === userId || wepiHandledRef.current.has(latest.id)) return;
    wepiHandledRef.current.add(latest.id);
    const reply = buildWepiReply(latest, wepiSettings, peer?.displayName);
    void sendDirectMessage(currentId, userId, reply, current?.ephemeralSeconds === 86400 || current?.ephemeralSeconds === 604800 ? current.ephemeralSeconds : 0)
      .catch(() => {
        wepiHandledRef.current.delete(latest.id);
        notifyRef.current("WEPI n’a pas pu envoyer sa réponse");
      });
  }, [current, currentId, items, peer?.displayName, userId, wepiSettings]);

  useEffect(() => {
    if (!currentId || !userId) return;
    const refreshPresence = () => {
      setPresenceTick(Date.now());
    };
    refreshPresence();
    const timer = window.setInterval(refreshPresence, 30_000);
    return () => window.clearInterval(timer);
  }, [currentId, userId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setEphemeralSeconds(current?.ephemeralSeconds === 86400 || current?.ephemeralSeconds === 604800 ? current.ephemeralSeconds : 0);
      setEphemeralMenuOpen(false);
      setMessageTranslations({});
      setViewedOnceIds({});
      setOpenedViewOnceIds({});
      setVideoViewOnce(null);
      setViewOnceMode(false);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [currentId, current?.ephemeralSeconds]);

  useEffect(() => {
    if (open && currentId && userId) void markDirectConversationRead(currentId, userId);
  }, [open, currentId, userId]);

  useEffect(() => {
    if (open) messagesEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [items, open]);

  useEffect(() => {
    if (!currentId || view !== "messages" || adding) return;
    const timer = window.setTimeout(() => composer.current?.focus(), 80);
    return () => window.clearTimeout(timer);
  }, [currentId, view, adding]);

  useEffect(() => {
    if (!composeToken || (!composePhone.trim() && !composePeer)) return;
    const timer = window.setTimeout(() => void (composePeer ? openPeerRef.current(composePeer) : openPhoneRef.current(composePhone)), 0);
    return () => window.clearTimeout(timer);
  }, [composeToken, composePhone, composePeer]);

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

  async function uploadAttachment(file: File, kind: "image" | "audio" | "video", duration = 0, conversationId = currentId, effect = "", caption = "", viewOnceValue = viewOnceMode, quality: "standard" | "hd" = hdMedia ? "hd" : "standard") {
    if (!conversationId || !userId || busy) return;
    setBusy(true);
    try {
      await sendDirectAttachment(conversationId, userId, file, kind, duration, effect, caption, ephemeralSeconds, viewOnceValue, quality);
      notify(kind === "image" ? (viewOnceValue ? "Photo à vue unique envoyée" : "Image envoyée") : kind === "video" ? (viewOnceValue ? "Vidéo à vue unique envoyée" : "Vidéo WHAPPY envoyée") : (viewOnceValue ? "Note vocale à vue unique envoyée" : "Message vocal envoyé"));
    } catch (error) {
      if (error instanceof Error && error.message === "media-too-large") {
        notify(kind === "video" ? "La vidéo doit peser moins de 60 Mo" : "L’image doit peser moins de 20 Mo");
      } else {
        notify(kind === "image" ? "L’image n’a pas pu être envoyée" : kind === "video" ? "La vidéo n’a pas pu être envoyée" : "Le message vocal n’a pas pu être envoyé");
      }
    } finally {
      setBusy(false);
    }
  }

  async function chooseMedia(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (file.type.startsWith("image/")) await uploadAttachment(file, "image");
    else if (file.type.startsWith("video/")) await uploadAttachment(file, "video");
    else notify("Choisissez une image ou une vidéo");
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

  async function openConversationWithPeer(found: DirectMember) {
    if (!user) return;
    if (found.uid === user.uid) {
      notify("C’est votre propre compte Whappy");
      return;
    }
    setBusy(true);
    try {
      const id = `direct-${[user.uid, found.uid].sort().join("-")}`;
      setOptimisticConversation({
        id,
        ownerId: user.uid,
        memberIds: [user.uid, found.uid].sort(),
        members: [user, found],
        typingBy: {},
        readBy: {},
        ephemeralSeconds: 0,
        updatedAt: null,
      });
      setSelected(id);
      setView("messages");
      setAdding(false);
      setOpen(true);
      await ensureDirectConversation(user, found);
      notify(`Conversation en temps réel avec ${found.displayName} ouverte`);
    } finally {
      setBusy(false);
    }
  }

  async function openConversationWithPhone(phone: string) {
    if (!user) return;
    if (!phone.trim()) {
      notify("Entrez le numéro Whappy de votre contact");
      return;
    }
    setBusy(true);
    try {
      const found = await findWhappyUserByPhone(phone);
      if (!found) {
        notify("Aucun compte Whappy trouvé avec ce numéro");
        return;
      }
      await openConversationWithPeer(found);
    } catch {
      notify("Le contact a été trouvé, mais la discussion n’a pas pu s’ouvrir. Réessayez.");
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => { openPhoneRef.current = openConversationWithPhone; openPeerRef.current = openConversationWithPeer; });

  async function addContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await openConversationWithPhone(String(form.get("phone") || ""));
  }

  function closePhotoPreview() {
    setPhotoPreview("");
    setPhotoZoom(1);
    if (photoViewOnceId) {
      setOpenedViewOnceIds((current) => { const next = { ...current }; delete next[photoViewOnceId]; return next; });
      setPhotoViewOnceId("");
    }
  }

  function closeViewOnceVideo(messageId = videoViewOnce?.id) {
    if (!messageId) return;
    setVideoViewOnce((current) => current?.id === messageId ? null : current);
    setOpenedViewOnceIds((current) => {
      const next = { ...current };
      delete next[messageId];
      return next;
    });
  }

  function closeViewOnceVoice(messageId = voiceViewOnce?.id) {
    if (!messageId) return;
    setVoiceViewOnce((current) => current?.id === messageId ? null : current);
    setOpenedViewOnceIds((current) => {
      const next = { ...current };
      delete next[messageId];
      return next;
    });
  }
  void closeViewOnceVoice;

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!current || !user || !text.trim() || busy) return;
    const value = text.trim();
    setText("");
    stopTyping(current.id);
    setBusy(true);
    const pending = pendingMessageRef.current?.conversationId === current.id && pendingMessageRef.current.text === value
      ? pendingMessageRef.current
      : { conversationId: current.id, text: value, clientMessageId: globalThis.crypto.randomUUID() };
    pendingMessageRef.current = pending;
    try {
      await sendDirectMessage(current.id, user.uid, value, ephemeralSeconds, pending.clientMessageId, replyTarget ? { id: replyTarget.id, senderName: replyTarget.senderId === user.uid ? user.displayName : (activePeerProfile?.displayName || peer?.displayName || "Contact"), text: replyTarget.text } : undefined);
      if (pendingMessageRef.current?.clientMessageId === pending.clientMessageId) pendingMessageRef.current = null;
      setReplyTarget(null);
    } catch {
      setText(value);
      notify("Le message n’a pas été envoyé");
    } finally {
      setBusy(false);
    }
  }

  async function forwardMessage(target: CloudConversation, message: CloudMessage) {
    if (!user || !message.text.trim() || target.id === currentId) return;
    const targetPeer = target.members.find((member) => member.uid !== user.uid);
    if (!targetPeer) return;
    try {
      await sendDirectMessage(target.id, user.uid, message.text, 0, undefined, undefined, true);
      setForwardingMessage(null);
      notifyRef.current(`Message transféré à ${targetPeer.displayName}`);
    } catch {
      notifyRef.current("Le transfert n’a pas pu être effectué");
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

  function writeToWepi() {
    if (!activePeerProfile?.wepiEnabled) return;
    setText((value) => value.trim() ? value : `Bonjour ${activePeerProfile.wepiName || "WEPI"}, `);
    window.setTimeout(() => composer.current?.focus(), 0);
    notify(`Vous écrivez à ${activePeerProfile.wepiName || "WEPI"} · envoyez votre message dans cette conversation`);
  }

  async function translateMessage(message: CloudMessage) {
    if (!message.text || translatingMessage) return;
    const key = `${message.id}:${translationTarget}`;
    if (messageTranslations[key]) {
      setMessageTranslations((current) => { const next = { ...current }; delete next[key]; return next; });
      return;
    }
    setTranslatingMessage(message.id);
    try { const value = await translateTextOnDevice(message.text, translationTarget); setMessageTranslations((current) => ({ ...current, [key]: value })); }
    catch { notify("La traduction locale de ce message n’est pas disponible sur cet appareil"); }
    finally { setTranslatingMessage(""); }
  }

  async function openViewOnce(message: CloudMessage) {
    if (!currentId || !userId || !message.viewOnce || message.senderId === userId || message.viewedBy?.[userId] || viewedOnceIds[message.id]) return;
    setViewedOnceIds((current) => ({ ...current, [message.id]: true }));
    setOpenedViewOnceIds((current) => ({ ...current, [message.id]: true }));
    if (message.kind === "image" && message.mediaUrl) {
      setPhotoPreview(message.mediaUrl);
      setPhotoZoom(1);
      setPhotoViewOnceId(message.id);
    } else if (message.kind === "video" && message.mediaUrl) {
      setVideoViewOnce({ id: message.id, src: message.mediaUrl });
    } else if (message.kind === "audio" && message.mediaUrl) {
      setVoiceViewOnce({ id: message.id, src: message.mediaUrl, duration: message.duration });
    }
    try {
      await markDirectMessageViewed(currentId, message.id, userId);
    } catch {
      setViewedOnceIds((current) => { const next = { ...current }; delete next[message.id]; return next; });
      setOpenedViewOnceIds((current) => { const next = { ...current }; delete next[message.id]; return next; });
      setPhotoPreview("");
      setPhotoViewOnceId("");
      closeViewOnceVideo(message.id);
      setVoiceViewOnce((current) => current?.id === message.id ? null : current);
      notify("Ce média à vue unique n’a pas pu être ouvert");
    }
  }

  function ephemeralLabel(seconds: number) {
    return seconds === 86400 ? "24 h" : seconds === 604800 ? "7 jours" : "Désactivés";
  }

  async function changeEphemeralMode(seconds: 0 | 86400 | 604800) {
    if (!currentId) return;
    const previous = ephemeralSeconds;
    setEphemeralSeconds(seconds);
    setEphemeralMenuOpen(false);
    try {
      await setDirectEphemeralMode(currentId, seconds);
      notify(seconds ? `Messages éphémères activés · ${ephemeralLabel(seconds)}` : "Messages permanents activés");
    } catch {
      setEphemeralSeconds(previous);
      notify("Le réglage des messages éphémères a échoué");
    }
  }

  if (!user) return null;

  const answeredCalls = calls.filter((call) => Boolean(call.answer)).length;
  const missedCalls = calls.filter((call) => call.calleeId === user.uid && call.status === "ended" && !call.answer).length;
  const peerOnline = Boolean(peer && timestampMillis(current?.presenceBy?.[peer.uid]) > presenceTick - 90_000);

  return <>
    {!embedded && <button className="realtime-launch" onClick={() => setOpen(true)} aria-label="Ouvrir Whappy Direct">
      <span>⚡</span><strong>Messages en temps réel</strong>
      <small>{conversations.length ? `${conversations.length} conversation${conversations.length > 1 ? "s" : ""}` : "Démarrer avec un numéro Whappy"}</small><b>→</b>
    </button>}
    {(embedded || open) && <div className={`realtime-layer ${embedded ? "embedded" : ""}`} role={embedded ? undefined : "dialog"} aria-modal={embedded ? undefined : true} aria-label="Whappy Direct">
      <section className={`realtime-panel ${embedded ? "embedded" : ""}`}>
        <aside className="realtime-inbox-list realtime-motion-mode-3">
          <header><div><small>WHAPPY DIRECT</small><strong>Communications</strong></div><button onClick={() => setAdding(true)} aria-label="Nouvelle conversation">＋</button></header>
          <nav className="direct-tabs" aria-label="Sections Whappy Direct">
            <button className={view === "messages" ? "active" : ""} onClick={() => setView("messages")}>◫ Discussions</button>
            <button className={view === "calls" ? "active" : ""} onClick={() => setView("calls")}>☎ Appels <b>{calls.length}</b></button>
          </nav>
          {view === "messages" ? <>
            {visibleConversations.map(({ conversation, unread }) => {
              const storedPerson = conversation.members.find((member) => member.uid !== user.uid);
              const person = storedPerson?.uid ? liveProfiles[storedPerson.uid] || storedPerson : storedPerson;
              const personIsFounder = isHappyFounderPhone(person?.phoneNumber);
              const personOnline = Boolean(person && timestampMillis(conversation.presenceBy?.[person.uid]) > presenceTick - 90_000);
              const personSeen = Boolean(person && timestampMillis(conversation.readBy?.[person.uid]) >= timestampMillis(conversation.updatedAt));
              const presenceLine = buildDirectPresenceLine(person, person?.uid ? conversation.presenceBy?.[person.uid] : null, Boolean(person && conversation.typingBy?.[person.uid]), personOnline, personSeen);
              return <button className={current?.id === conversation.id ? "active" : ""} key={conversation.id} onClick={() => { stopTyping(); stopRecording(true); setOptimisticConversation(null); setSelected(conversation.id); }}>
                <DirectAvatar person={person} />
                <div>
                  <strong className="conversation-name">{person?.displayName || "Contact Whappy"}{isVerifiedMember(person) ? <i className="verified-grey-badge" title="Identité Whappy certifiée" aria-label="Identité Whappy certifiée">✓</i> : null}{personIsFounder ? <em className="conversation-founder-role">Fondateur</em> : null}{person?.accountType === "business" ? <em className="business-account-badge">BUSINESS</em> : null}{person?.wepiEnabled ? <em className="wepi-contact-badge">WEPI</em> : null}</strong>
                  <small>{conversation.typingBy?.[person?.uid || ""] ? "écrit…" : conversation.lastMessage || person?.phoneNumber}</small>
                  <small className="conversation-meta">{presenceLine || "• Chargement des infos..."}</small>
                </div>
                <i className={unread ? "unread" : ""}>{unread ? "●" : ""}</i>
              </button>;
            })}
            {!visibleConversations.length && <div className={`realtime-empty ${searchValue ? "search-empty" : "ready-empty"}`}>
              <span>{searchValue ? "⌕" : "◫"}</span>
              <strong>{searchValue ? "Aucun résultat" : "Boîte de réception prête"}</strong>
              <small>{searchValue ? "Essayez un autre nom ou numéro." : "Vos prochains échanges apparaîtront ici, synchronisés sur tous vos appareils."}</small>
              {!searchValue && <><div className="empty-readiness"><i/><span>Compte vérifié</span><i/><span>Cloud actif</span></div><button onClick={() => setAdding(true)}>＋ Écrire un message</button></>}
            </div>}
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
          {(() => {
            const peerIsFounder = isHappyFounderPhone(peer.phoneNumber);
            const peerHasWepi = Boolean(activePeerProfile?.wepiEnabled);
            const peerSeen = Boolean(peer && timestampMillis(current.readBy?.[peer.uid]) >= timestampMillis(current.updatedAt));
            const peerPresence = buildDirectPresenceLine(peer, current.presenceBy?.[peer.uid], Boolean(current.typingBy?.[peer.uid]), peerOnline, peerSeen);
            return <header>
              <DirectAvatar person={activePeerProfile || peer} /><div><strong>{activePeerProfile?.displayName || peer.displayName}{isVerifiedMember(activePeerProfile || peer) ? <i className="verified-grey-badge" title="Identité Whappy certifiée" aria-label="Identité Whappy certifiée">✓</i> : null}{peerHasWepi ? <em className="wepi-contact-badge">WEPI</em> : null}</strong>{peerIsFounder ? <span className="conversation-founder-role">Fondateur</span> : null}{(activePeerProfile || peer).accountType === "business" ? <span className="conversation-business-role">Compte Business</span> : null}<small className="direct-message-subline">{peerHasWepi ? `${peerPresence} · Assistant disponible` : peerPresence}</small></div>
              <span className="encryption-badge" title="Seuls les participants peuvent déchiffrer les messages texte">🔒 Messages chiffrés de bout en bout</span>{peerHasWepi && <button type="button" className="wepi-contact-action" onClick={writeToWepi} aria-label={`Écrire à ${activePeerProfile?.wepiName || "WEPI"}`}>✦ WEPI</button>}
              <button onClick={() => onCall(peer, false)} aria-label={`Appeler ${peer.displayName}`}>☎</button><button onClick={() => onCall(peer, true)} aria-label={`Appel vidéo avec ${peer.displayName}`}>▣</button>{!embedded && <button onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>}
            </header>;
          })()}
          <div className="realtime-messages">
            {!items.length && <div className="message-empty"><span>✦</span><strong>La conversation commence ici</strong><small>Vos messages apparaîtront instantanément sur les deux comptes.</small></div>}
            {items.map((message) => {
              const read = timestampMillis(current.readBy?.[peer.uid]) >= timestampMillis(message.createdAt);
              const translationKey = `${message.id}:${translationTarget}`;
              const translated = messageTranslations[translationKey];
              const viewOnceOpen = Boolean(message.viewOnce && message.senderId !== user.uid && openedViewOnceIds[message.id]);
              const viewOnceConsumed = Boolean(message.viewOnce && message.senderId !== user.uid && (message.viewedBy?.[user.uid] || viewedOnceIds[message.id]) && !viewOnceOpen);
              const viewOncePending = Boolean(message.viewOnce && message.senderId !== user.uid && !viewOnceConsumed && !viewOnceOpen);
              const sentReadTimestamp = peer && message.senderId === user.uid ? messageTime({ toDate: () => new Date(Math.max(timestampMillis(current.readBy?.[peer.uid]), timestampMillis(message.createdAt)) ) }) : "";
              return <article className={`${message.senderId === user.uid ? "mine" : ""} ${message.kind === "image" ? "image" : ""} ${message.kind === "video" ? "video" : ""}`} key={message.id}>
                {message.kind === "link" && message.linkUrl ? <a className="message-link-card" href={message.linkUrl} target="_blank" rel="noreferrer"><span>↗</span><div><small>LIEN PARTAGÉ · {linkHost(message.linkUrl)}</small><strong>{message.text === message.linkUrl ? "Ouvrir le lien partagé" : message.text}</strong><p>{message.linkUrl}</p></div></a> : message.kind === "image" && message.mediaUrl ? viewOncePending ? <button type="button" className="message-view-once" onClick={() => void openViewOnce(message)}><span>1</span><strong>Photo à vue unique</strong><small>Appuyez pour ouvrir</small></button> : viewOnceConsumed ? <div className="message-viewed-once"><span>✓</span><strong>Photo déjà ouverte</strong><small>Ce média ne peut être vu qu’une fois.</small></div> : <button type="button" className="message-photo" onClick={() => { setPhotoPreview(message.mediaUrl || ""); setPhotoZoom(1); }} style={{ backgroundImage: `url(${message.mediaUrl})` }} aria-label="Agrandir la photo"/> : message.kind === "video" && message.mediaUrl ? viewOncePending ? <button type="button" className="message-view-once" onClick={() => void openViewOnce(message)}><span>1</span><strong>Vidéo à vue unique</strong><small>Appuyez pour ouvrir</small></button> : viewOnceOpen ? <div className="message-view-once"><span>1</span><strong>Lecture unique en cours</strong><small>Vue dans l’écran dédié</small></div> : viewOnceConsumed ? <div className="message-viewed-once"><span>✓</span><strong>Vidéo déjà ouverte</strong><small>Ce média ne peut être vu qu’une fois.</small></div> : <div className="message-video"><video controls playsInline preload="metadata" src={message.mediaUrl} className={message.effect || "pop"}/>{message.caption && <strong>{message.caption}</strong>}<i>✦ WHAPPY VIDEO {message.quality === "hd" ? "· HD" : ""}</i></div> : message.kind === "audio" && message.mediaUrl ? viewOncePending ? <button type="button" className="message-view-once" onClick={() => void openViewOnce(message)}><span>1</span><strong>Note vocale à vue unique</strong><small>Appuyez pour écouter · {message.duration || 0}s</small></button> : viewOnceConsumed ? <div className="message-viewed-once"><span>✓</span><strong>Note vocale déjà écoutée</strong><small>Cette note ne peut être écoutée qu’une fois.</small></div> : <div className="message-vocal"><span>▶</span><audio controls preload="metadata" src={message.mediaUrl}/><b>{message.duration || 0}s</b></div> : <><p>{message.text}</p>{translated && <p className="message-translation"><b>{translationTarget.toUpperCase()}</b>{translated}</p>}<div className="message-translation-tools"><button type="button" className={translationTarget === "fr" ? "active" : ""} onClick={() => setTranslationTarget("fr")}>FR</button><button type="button" className={translationTarget === "en" ? "active" : ""} onClick={() => setTranslationTarget("en")}>EN</button><button type="button" className="message-translate" onClick={() => void translateMessage(message)}>{translatingMessage === message.id ? "Traduction…" : translated ? "Masquer" : `文 Traduire en ${translationTarget === "fr" ? "français" : "anglais"}`}</button></div></>}
                {message.forwarded&&<em className="forwarded-badge">↗ TRANSFÉRÉ</em>}{message.viewOnce && <em className="view-once-badge">1× VUE UNIQUE</em>}<div className="message-tools"><button type="button" onClick={()=>setReplyTarget(message)}>↩ Répondre</button><button type="button" onClick={()=>setForwardingMessage(message)}>↗ Transférer</button></div><small><time>{messageTime(message.createdAt)}</time>{message.senderId === user.uid && <><span> · </span><b className={message.pending ? "message-pending" : read ? "message-seen" : "message-sent"}>{message.pending ? "⟳ Envoi…" : read ? `✓✓ Vu ${sentReadTimestamp}` : "✓ Envoyé"}</b></>}</small>
              </article>;
            })}<div ref={messagesEnd}/>
          </div>
          {recording && <div className="recording-strip"><i/><strong>Message vocal · {recordingSeconds}s / 90s</strong><button onClick={() => stopRecording()}>Terminer et envoyer</button></div>}
          {expressionOpen && <WhappyExpressionHub draft={text} onDraftChange={change} notify={notify} onClose={() => setExpressionOpen(false)} onSendMedia={(file, kind, effect, caption) => uploadAttachment(file, kind, 0, current.id, effect, caption)}/>}
          {replyTarget&&<div className="message-reply-banner"><span>Réponse à <b>{replyTarget.senderId===user.uid?"vous":(activePeerProfile?.displayName||peer.displayName)}</b><small>{replyTarget.text}</small></span><button type="button" onClick={()=>setReplyTarget(null)} aria-label="Annuler la réponse">×</button></div>}{forwardingMessage&&<div className="message-forward-layer" role="dialog" aria-label="Transférer le message"><header><strong>Transférer ce message</strong><button type="button" onClick={()=>setForwardingMessage(null)} aria-label="Fermer">×</button></header><p>{forwardingMessage.text}</p><div>{conversations.filter((conversation)=>conversation.id!==currentId).map((conversation)=>{const target=conversation.members.find((member)=>member.uid!==user.uid);return target?<button type="button" key={conversation.id} onClick={()=>void forwardMessage(conversation,forwardingMessage)}><span>{initials(target.displayName)}</span><strong>{target.displayName}</strong><small>Envoyer ici</small></button>:null})}</div></div>}<form className="direct-composer" onSubmit={send}>
            {ephemeralMenuOpen && <div className="ephemeral-menu" role="menu"><strong>Messages éphémères</strong><small>Les nouveaux messages disparaissent automatiquement.</small><button type="button" className={!ephemeralSeconds ? "active" : ""} onClick={() => void changeEphemeralMode(0)}>Désactivés <span>Conserver</span></button><button type="button" className={ephemeralSeconds === 86400 ? "active" : ""} onClick={() => void changeEphemeralMode(86400)}>24 heures <span>Expiration automatique</span></button><button type="button" className={ephemeralSeconds === 604800 ? "active" : ""} onClick={() => void changeEphemeralMode(604800)}>7 jours <span>Expiration automatique</span></button></div>}
            <input className="message-file" ref={mediaInput} type="file" accept="image/*,video/mp4,video/webm,video/quicktime" onChange={chooseMedia}/>
            <button type="button" onClick={() => setExpressionOpen((value) => !value)} className={expressionOpen ? "active" : ""} disabled={busy || recording} aria-label="Emojis, traduction et créations WHAPPY" title="Emojis, traduction et Meme Lab">☺</button>
            <button type="button" onClick={() => mediaInput.current?.click()} disabled={busy || recording} aria-label="Ajouter une image ou une vidéo HD" title="Image ou vidéo HD">▧</button>
            <button type="button" onClick={() => setHdMedia((value) => !value)} className={hdMedia ? "active hd-toggle" : "hd-toggle"} disabled={busy || recording} aria-pressed={hdMedia} aria-label={hdMedia ? "Qualité HD activée" : "Activer la qualité HD"} title={hdMedia ? "Qualité HD activée · fichier original conservé" : "Activer la qualité HD"}>HD</button>
            <button type="button" onClick={() => setText((value) => `${value}${value && !value.endsWith(" ") ? " " : ""}https://`)} disabled={busy || recording} aria-label="Ajouter un lien" title="Ajouter un lien">↗</button>
            <button type="button" onClick={() => setEphemeralMenuOpen((value) => !value)} className={ephemeralSeconds ? "active" : ""} disabled={busy || recording} aria-label="Régler les messages éphémères" title="Messages éphémères">◷</button>
            <button type="button" onClick={() => setViewOnceMode((value) => !value)} className={viewOnceMode ? "active" : ""} disabled={busy || recording} aria-pressed={viewOnceMode} aria-label="Activer le mode vue unique" title="Photo, vidéo ou note vocale à vue unique">1×</button>
            <button type="button" className={recording ? "recording" : ""} onClick={startRecording} disabled={busy} aria-label={recording ? "Terminer le message vocal" : "Enregistrer un message vocal"} title="Note vocale">●</button>
            <textarea ref={composer} value={text} onChange={(event) => change(event.target.value)} onKeyDown={composerKeyDown} onBlur={() => stopTyping(current.id)} maxLength={4000} rows={1} spellCheck lang="fr" autoComplete="off" aria-label={activePeerProfile?.wepiEnabled ? `Message à ${activePeerProfile.wepiName || "WEPI"}` : `Message à ${peer.displayName}`} placeholder={activePeerProfile?.wepiEnabled ? `Écrire à ${activePeerProfile.wepiName || "WEPI"} · Entrée pour envoyer` : `Message à ${peer.displayName} · Entrée pour envoyer`}/>
            <button disabled={busy || recording || !text.trim()} aria-label="Envoyer le message" title="Envoyer">➤</button>
            <small>{viewOnceMode ? "Vue unique activée · Photo, vidéo ou note vocale s’ouvre une seule fois" : "Entrée envoie · Maj + Entrée ajoute une ligne · Orthographe activée"}</small>
          </form>
        </> : <div className="direct-onboarding">
          {!embedded && <button className="direct-onboarding-close" onClick={closePanel} aria-label="Fermer Whappy Direct">×</button>}
          <header className="direct-welcome-head">
            <div><small>VOTRE ESPACE DE COMMUNICATION</small><h2>Bonjour {user.displayName.split(/\s+/)[0]},<br/><em>tout est prêt.</em></h2><p>Lancez votre première conversation avec un numéro Whappy. Messages, médias et appels resteront regroupés ici.</p></div>
            <div className="direct-account-card"><DirectAvatar person={liveProfiles[user.uid] || user} /><div><small>{founder ? "COMPTE FONDATEUR CERTIFIÉ" : "COMPTE WHAPPY VÉRIFIÉ"}</small><strong>{user.displayName}{founder ? <i className="founder-grey-badge" title="Compte fondateur Whappy by BCA">✓</i> : null}</strong><p>{user.phoneNumber ? `${user.phoneNumber.slice(0, 4)} ••• •• ${user.phoneNumber.slice(-2)}` : "Identité téléphonique active"}</p></div><b>✓</b></div>
          </header>
          <section className="direct-overview">
            <article><span>◫</span><div><small>DISCUSSIONS</small><strong>{conversations.length}</strong><p>Synchronisées en direct</p></div></article>
            <article><span>☎</span><div><small>APPELS</small><strong>{calls.length}</strong><p>Audio et vidéo HD</p></div></article>
            <article className="online"><span>◆</span><div><small>DISPONIBILITÉ</small><strong>En ligne</strong><p>Cloud opérationnel</p></div></article>
          </section>
          <section className="direct-start-grid">
            <article className="direct-start-card">
              <div className="start-card-mark">＋</div><small>PREMIÈRE ÉTAPE</small><h3>Démarrez une conversation</h3><p>Entrez le numéro international d’un contact déjà inscrit sur Whappy. La discussion sera créée instantanément.</p>
              <button onClick={() => setAdding(true)}>Nouveau message <span>→</span></button>
              <div className="direct-trust-note"><span>◆</span><p><strong>Identité téléphonique protégée</strong><small>Seuls les comptes Whappy vérifiés peuvent vous contacter.</small></p></div>
            </article>
            <article className="direct-capabilities">
              <header><div><small>INCLUS DANS WHAPPY DIRECT</small><h3>Une conversation complète</h3></div><span>PRÊT</span></header>
              <div><span>01</span><p><strong>Messages instantanés</strong><small>Accusés de lecture et saisie en direct</small></p><b>✓</b></div>
              <div><span>02</span><p><strong>Photos, vidéos et vocaux</strong><small>Partage fluide depuis le même fil</small></p><b>✓</b></div>
              <div><span>03</span><p><strong>Appels audio et vidéo</strong><small>Passez de l’écrit à l’appel en un geste</small></p><b>✓</b></div>
            </article>
          </section>
          <footer className="direct-onboarding-foot"><span><i/> Whappy Direct est opérationnel</span><p>🔒 Messages texte chiffrés de bout en bout. Même Whappy ne peut pas lire leur contenu.</p><button onClick={() => setView("calls")}>Voir les appels →</button></footer>
        </div>}</main>
      </section>
      {adding && <form className="direct-create" onSubmit={addContact}><header><div><small>NOUVELLE CONVERSATION</small><h3>Entrez son numéro Whappy</h3></div><button type="button" onClick={() => setAdding(false)} aria-label="Fermer">×</button></header><label>Numéro international complet<input name="phone" defaultValue={composePhone} required inputMode="tel" autoComplete="tel" placeholder="+242 06 000 00 00"/></label><p>Si le numéro est déjà inscrit, la discussion s’ouvre directement. Aucun écran de contact intermédiaire.</p><button disabled={busy}>{busy ? "Ouverture…" : "Entrer dans la discussion →"}</button></form>}
    </div>}
    {photoPreview && <div className="photo-lightbox" role="dialog" aria-modal="true" aria-label="Photo agrandie">
      <header><span>PHOTO WHAPPY</span><div><button type="button" onClick={() => setPhotoZoom((value) => Math.max(1, value - .5))} disabled={photoZoom <= 1} aria-label="Réduire">−</button><b>{Math.round(photoZoom * 100)}%</b><button type="button" onClick={() => setPhotoZoom((value) => Math.min(3, value + .5))} disabled={photoZoom >= 3} aria-label="Agrandir">＋</button><a href={photoPreview} target="_blank" rel="noreferrer">Original ↗</a><button type="button" onClick={closePhotoPreview} aria-label="Fermer">×</button></div></header>
      <button type="button" className="photo-lightbox-stage" onClick={() => setPhotoZoom((value) => value === 1 ? 2 : 1)} aria-label={photoZoom === 1 ? "Agrandir davantage" : "Revenir à la taille normale"}><img src={photoPreview} alt="Agrandissement partagé" style={{ transform: `scale(${photoZoom})` }}/></button>
    </div>}
    {videoViewOnce && <div className="video-viewonce-lightbox" role="dialog" aria-modal="true" aria-label="Lecture vidéo vue unique">
      <header><span>VIDÉO WHAPPY · VUE UNIQUE</span><button type="button" onClick={() => closeViewOnceVideo(videoViewOnce.id)} aria-label="Fermer la vidéo">×</button></header>
      <main className="video-viewonce-stage"><video src={videoViewOnce.src} controls autoPlay playsInline onEnded={() => closeViewOnceVideo(videoViewOnce.id)} className="video-viewonce-player"/></main>
    </div>}
    {voiceViewOnce && <div className="video-viewonce-lightbox" role="dialog" aria-modal="true" aria-label="Lecture note vocale vue unique">
      <header><span>NOTE VOCALE WHAPPY · VUE UNIQUE</span><button type="button" onClick={() => closeViewOnceVoice(voiceViewOnce.id)} aria-label="Fermer la note vocale">×</button></header>
      <main className="voice-viewonce-stage"><span>◉</span><strong>Lecture unique</strong><small>{voiceViewOnce.duration || 0}s · cette note disparaîtra après écoute</small><audio controls autoPlay src={voiceViewOnce.src} onEnded={() => closeViewOnceVoice(voiceViewOnce.id)} /></main>
    </div>}
  </>;
}
