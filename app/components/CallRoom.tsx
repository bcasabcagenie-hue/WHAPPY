"use client";
/* eslint-disable jsx-a11y/media-has-caption -- flux WebRTC en direct sans piste de sous-titres disponible */

import { useEffect, useRef, useState } from "react";
import { addCallCandidate, answerCall, startCall, updateCallStatus, watchCall, watchCallCandidates, type CallSignal } from "@/lib/whappy-calls";
import type { DirectMember } from "@/lib/whappy-data";

const rtcConfiguration: RTCConfiguration = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" },
  ],
};

type CallRoomProps = {
  contact: string;
  video: boolean;
  currentUser: DirectMember | null;
  peer?: DirectMember;
  incoming?: CallSignal;
  onClose: () => void;
};

export function CallRoom({ contact, video, currentUser, peer, incoming, onClose }: CallRoomProps) {
  const localVideo = useRef<HTMLVideoElement>(null);
  const remoteVideo = useRef<HTMLVideoElement>(null);
  const remoteAudio = useRef<HTMLAudioElement>(null);
  const localStream = useRef<MediaStream | null>(null);
  const remoteStream = useRef<MediaStream | null>(null);
  const connection = useRef<RTCPeerConnection | null>(null);
  const callId = useRef(incoming?.id || "");
  const cleanups = useRef<Array<() => void>>([]);
  const [status, setStatus] = useState<"incoming" | "opening" | "ringing" | "connected" | "ended" | "error">(incoming ? "incoming" : "opening");
  const [muted, setMuted] = useState(false);
  const [camera, setCamera] = useState(video);
  const [speaker, setSpeaker] = useState(true);
  const [seconds, setSeconds] = useState(0);
  const [accepted, setAccepted] = useState(!incoming);
  const [mediaReady, setMediaReady] = useState(false);
  const isReal = Boolean(currentUser && (peer || incoming));

  useEffect(() => {
    if (!incoming || accepted) return;
    return watchCall(incoming.id, (signal) => {
      if (!signal || signal.status === "declined" || signal.status === "ended") setStatus("ended");
    });
  }, [accepted, incoming]);

  useEffect(() => {
    if (!accepted) return;
    let active = true;

    async function connect() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video });
        if (!active) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        localStream.current = stream;
        setMediaReady(true);
        if (localVideo.current) {
          localVideo.current.srcObject = stream;
          await localVideo.current.play().catch(() => {});
        }
        if (!isReal) {
          setStatus("connected");
          return;
        }

        const rtc = new RTCPeerConnection(rtcConfiguration);
        const remote = new MediaStream();
        const queuedRemoteCandidates: RTCIceCandidateInit[] = [];
        let applyingRemoteDescription = false;
        connection.current = rtc;
        remoteStream.current = remote;
        stream.getTracks().forEach((track) => rtc.addTrack(track, stream));

        const attachRemoteStream = () => {
          if (remoteVideo.current) {
            remoteVideo.current.srcObject = remote;
            void remoteVideo.current.play().catch(() => {});
          }
          if (remoteAudio.current) {
            remoteAudio.current.srcObject = remote;
            void remoteAudio.current.play().catch(() => {});
          }
        };
        const addRemoteCandidate = async (candidate: RTCIceCandidateInit) => {
          if (!rtc.remoteDescription || applyingRemoteDescription) {
            queuedRemoteCandidates.push(candidate);
            return;
          }
          await rtc.addIceCandidate(candidate).catch(() => {});
        };
        const flushRemoteCandidates = async () => {
          const candidates = queuedRemoteCandidates.splice(0);
          await Promise.all(candidates.map((candidate) => rtc.addIceCandidate(candidate).catch(() => {})));
        };

        rtc.ontrack = (event) => {
          const tracks = event.streams[0]?.getTracks() || [event.track];
          tracks.forEach((track) => {
            if (!remote.getTracks().some((item) => item.id === track.id)) remote.addTrack(track);
          });
          attachRemoteStream();
        };
        rtc.onconnectionstatechange = () => {
          if (rtc.connectionState === "connected") setStatus("connected");
          if (["failed", "closed"].includes(rtc.connectionState)) setStatus("ended");
        };

        if (incoming) {
          setStatus("opening");
          applyingRemoteDescription = true;
          await rtc.setRemoteDescription(incoming.offer!);
          applyingRemoteDescription = false;
          await flushRemoteCandidates();
          rtc.onicecandidate = (event) => event.candidate && void addCallCandidate(incoming.id, "callee", event.candidate.toJSON());
          cleanups.current.push(watchCallCandidates(incoming.id, "caller", (candidate) => void addRemoteCandidate(candidate)));
          cleanups.current.push(watchCall(incoming.id, (signal) => {
            if (!signal || signal.status === "declined" || signal.status === "ended") setStatus("ended");
          }));
          const answer = await rtc.createAnswer();
          await rtc.setLocalDescription(answer);
          await answerCall(incoming.id, answer);
          callId.current = incoming.id;
          return;
        }

        if (peer && currentUser) {
          const pendingLocalCandidates: RTCIceCandidateInit[] = [];
          let answerApplied = false;
          rtc.onicecandidate = (event) => {
            if (!event.candidate) return;
            const candidate = event.candidate.toJSON();
            if (callId.current) void addCallCandidate(callId.current, "caller", candidate);
            else pendingLocalCandidates.push(candidate);
          };
          const offer = await rtc.createOffer();
          await rtc.setLocalDescription(offer);
          const id = await startCall(currentUser.uid, peer.uid, currentUser.displayName, peer.displayName, video, offer);
          callId.current = id;
          await Promise.all(pendingLocalCandidates.map((candidate) => addCallCandidate(id, "caller", candidate)));
          setStatus("ringing");
          cleanups.current.push(watchCallCandidates(id, "callee", (candidate) => void addRemoteCandidate(candidate)));
          cleanups.current.push(watchCall(id, (signal) => {
            if (!signal) {
              setStatus("ended");
              return;
            }
            if (signal.answer && !answerApplied) {
              answerApplied = true;
              void (async () => {
                applyingRemoteDescription = true;
                await rtc.setRemoteDescription(signal.answer!);
                applyingRemoteDescription = false;
                await flushRemoteCandidates();
              })().catch(() => {
                answerApplied = false;
                applyingRemoteDescription = false;
                setStatus("error");
              });
            }
            if (signal.status === "declined" || signal.status === "ended") setStatus("ended");
          }));
        }
      } catch {
        if (active) setStatus("error");
      }
    }

    void connect();
    return () => {
      active = false;
      cleanups.current.forEach((stop) => stop());
      cleanups.current = [];
      localStream.current?.getTracks().forEach((track) => track.stop());
      remoteStream.current?.getTracks().forEach((track) => track.stop());
      connection.current?.close();
    };
  }, [accepted, video, isReal, currentUser, peer, incoming]);

  useEffect(() => {
    if (status !== "connected") return;
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [status]);

  useEffect(() => {
    if (remoteAudio.current) remoteAudio.current.muted = !speaker;
  }, [speaker]);

  function toggle(kind: "audio" | "video") {
    const tracks = kind === "audio" ? localStream.current?.getAudioTracks() : localStream.current?.getVideoTracks();
    if (!tracks?.length) return;
    const enabled = !tracks[0].enabled;
    tracks.forEach((track) => { track.enabled = enabled; });
    if (kind === "audio") setMuted(!enabled);
    else setCamera(enabled);
  }

  async function hangup(next: "ended" | "declined" = "ended") {
    if (callId.current) await updateCallStatus(callId.current, next).catch(() => {});
    onClose();
  }

  const time = `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
  const label = status === "incoming" ? "Appel entrant" : status === "ringing" ? "Sonnerie…" : status === "connected" ? time : status === "ended" ? "Appel terminé" : status === "error" ? "Connexion impossible" : "Connexion sécurisée…";

  return <div className="call-layer" role="dialog" aria-modal="true" aria-label={`Appel avec ${contact}`}>
    <section className={`call-room ${video ? "video" : "audio"} ${status}`}>
      <header><span>WHAPPY CALL · {isReal ? "WEBRTC" : "DÉMONSTRATION"}</span><b>{label}</b></header>
      {video && <><video className="remote-video" ref={remoteVideo} muted autoPlay playsInline/><video className="local-video" ref={localVideo} muted autoPlay playsInline/></>}
      <audio ref={remoteAudio} autoPlay/>
      <div className="call-contact">
        <span>{contact.split(/\s+/).map((part) => part[0]).join("").slice(0, 2)}</span>
        <h2>{contact}</h2>
        <p>{status === "incoming" ? (video ? "Appel vidéo entrant" : "Appel audio entrant") : status === "ringing" ? "Votre contact reçoit l’appel…" : status === "connected" ? (isReal ? "Connexion directe chiffrée entre les appareils" : "Micro et caméra actifs sur cet appareil") : status === "error" ? "Autorisez le micro et la caméra, puis réessayez." : "Préparation de l’appel…"}</p>
      </div>
      {status === "incoming" ? <footer className="incoming-actions">
        <button onClick={() => hangup("declined")} className="hangup" aria-label="Refuser l’appel">×<small>Refuser</small></button>
        <button onClick={() => { setAccepted(true); setStatus("opening"); }} className="accept-call" aria-label="Accepter l’appel">☎<small>Accepter</small></button>
      </footer> : <footer>
        <button className={muted ? "off" : ""} onClick={() => toggle("audio")} disabled={!mediaReady} aria-label={muted ? "Réactiver le micro" : "Couper le micro"}>◉<small>{muted ? "Réactiver" : "Micro"}</small></button>
        {video && <button className={camera ? "" : "off"} onClick={() => toggle("video")} disabled={!mediaReady} aria-label={camera ? "Couper la caméra" : "Réactiver la caméra"}>▣<small>{camera ? "Caméra" : "Réactiver"}</small></button>}
        <button className={speaker ? "" : "off"} onClick={() => setSpeaker((value) => !value)} aria-label="Activer ou désactiver le haut-parleur">◖<small>Haut-parleur</small></button>
        <button onClick={() => hangup()} className="hangup" aria-label="Raccrocher">☎<small>Raccrocher</small></button>
      </footer>}
      <small className="call-note">{isReal ? "WebRTC utilise une connexion directe. Un relais TURN pourra être requis sur certains réseaux mobiles stricts." : "Choisissez un contact Whappy Direct pour appeler un véritable autre compte."}</small>
    </section>
  </div>;
}
