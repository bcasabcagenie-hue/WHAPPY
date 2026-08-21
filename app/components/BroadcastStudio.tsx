"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";

export type BroadcastConfig = {
  title: string;
  product: string;
  mode: "human" | "twin";
};

type Props = {
  config: BroadcastConfig;
  twinAuthorized: boolean;
  onClose: () => void;
  onOpenTwin: () => void;
  notify: (message: string) => void;
};

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, "0");
  return `${minutes}:${(seconds % 60).toString().padStart(2, "0")}`;
}

export function BroadcastStudio({ config, twinAuthorized, onClose, onOpenTwin, notify }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [status, setStatus] = useState<"setup" | "ready" | "live" | "ended">("setup");
  const [cameraOn, setCameraOn] = useState(true);
  const [micOn, setMicOn] = useState(true);
  const [hasStream, setHasStream] = useState(false);
  const [mode, setMode] = useState<"human" | "twin">(config.mode);
  const [error, setError] = useState("");
  const [elapsed, setElapsed] = useState(0);
  const [viewers, setViewers] = useState(0);
  const [comment, setComment] = useState("");
  const [comments, setComments] = useState(["Bienvenue dans votre studio Whappy."]);
  const [panel, setPanel] = useState<"conversation" | "stats">("conversation");
  const [productVisible, setProductVisible] = useState(true);
  const autoOpenCamera = useRef(false);

  // The camera preview is real, but there is no SFU/WHAPPY media transport in
  // this workspace yet. Never expose a local preview as a public broadcast or
  // fabricate viewers/interactions.
  const publicStreamingConnected = false;
  const liveModeLabel = "STUDIO LOCAL · NON DIFFUSÉ";

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setHasStream(false);
  }, []);

  const openCamera = useCallback(async () => {
    setError("");
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("Ce navigateur ne permet pas d’ouvrir la caméra.");
      return false;
    }
    try {
      stopStream();
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: true });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setCameraOn(true);
      setMicOn(true);
      setHasStream(true);
      setStatus("ready");
      return true;
    } catch (reason) {
      const name = reason instanceof DOMException ? reason.name : "";
      setError(name === "NotAllowedError" ? "Autorisez la caméra et le micro dans votre navigateur pour continuer." : "La caméra n’est pas disponible. Vérifiez qu’aucune autre application ne l’utilise.");
      return false;
    }
  }, [stopStream]);

  useEffect(() => () => stopStream(), [stopStream]);

  useEffect(() => {
    if (status !== "live") return;
    const timer = window.setInterval(() => {
      setElapsed((value) => value + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [status]);

  useEffect(() => {
    if (autoOpenCamera.current || status !== "setup" || mode !== "human") return;
    autoOpenCamera.current = true;
    void openCamera();
  }, [status, mode, openCamera]);

  async function startLive() {
    if (mode === "twin" && !twinAuthorized) {
      setError("Créez et autorisez votre Double avant de lui passer le direct.");
      return;
    }
    if (mode === "human" && !streamRef.current && !(await openCamera())) return;
    setStatus("live");
    setViewers(0);
    notify("Studio local actif · la diffusion publique nécessite le transport média WAPI");
  }

  function toggleTrack(kind: "audio" | "video") {
    const tracks = kind === "audio" ? streamRef.current?.getAudioTracks() : streamRef.current?.getVideoTracks();
    if (!tracks?.length) {
      setError(`Aucun ${kind === "audio" ? "micro" : "flux caméra"} disponible.`);
      return;
    }
    const enabled = !tracks[0].enabled;
    tracks.forEach((track) => { track.enabled = enabled; });
    if (kind === "audio") setMicOn(enabled);
    else setCameraOn(enabled);
  }

  function endLive() {
    setStatus("ended");
    stopStream();
    notify(`Direct terminé · ${formatDuration(elapsed)}`);
  }

  async function shareLive() {
    const share = { title: config.title, text: `Rejoignez mon direct Whappy : ${config.title}`, url: window.location.href };
    try {
      if (navigator.share) await navigator.share(share);
      else {
        await navigator.clipboard.writeText(`${share.text} — ${share.url}`);
        notify("Lien du direct copié");
      }
    } catch {
      // Closing the native share sheet is not an error the user needs to handle.
    }
  }

  function sendComment(event: FormEvent) {
    event.preventDefault();
    if (!comment.trim()) return;
    setComments((current) => [...current, comment.trim()]);
    setComment("");
  }

  return <div className="broadcast-layer" role="dialog" aria-modal="true" aria-label="Studio de direct Whappy">
    <section className="broadcast-studio">
      <header className="broadcast-head">
        <div><span className={status === "live" ? "is-live" : ""}>{status === "live" ? "● EN DIRECT" : "STUDIO WHAPPY"}</span><h2>{config.title}</h2><p>{status === "live" ? `${formatDuration(elapsed)} · ${viewers} spectateur${viewers > 1 ? "s" : ""}` : liveModeLabel}</p></div>
        <button onClick={status === "live" ? endLive : onClose} aria-label={status === "live" ? "Terminer le direct" : "Fermer le studio"}>{status === "live" ? "Terminer" : "×"}</button>
      </header>

      <div className="broadcast-grid">
        <div className={`broadcast-stage ${mode} ${cameraOn ? "" : "camera-off"}`}>
          <video ref={videoRef} muted playsInline />
          {mode === "twin" && <div className="twin-standin"><span>CB</span><strong>DOUBLE IA</strong><small>Contenu artificiel signalé</small></div>}
          {mode === "human" && !hasStream && <div className="camera-empty"><span>▣</span><strong>Votre caméra est fermée</strong><button onClick={openCamera}>Ouvrir la caméra</button></div>}
          {mode === "human" && hasStream && !cameraOn && <div className="camera-empty"><span>CB</span><strong>Caméra coupée</strong></div>}
          <div className="stage-pills"><span>{mode === "human" ? "CAMÉRA RÉELLE" : "DOUBLE · IA"}</span><span>{micOn ? "Micro actif" : "Micro coupé"}</span></div>
          {productVisible && <div className="pinned-product"><span>◇</span><div><small>PRODUIT ÉPINGLÉ</small><strong>{config.product}</strong></div><button onClick={() => setProductVisible(false)}>Masquer</button></div>}
          {!productVisible && <button className="restore-product" onClick={() => setProductVisible(true)}>◇ Réafficher le produit</button>}
        </div>

        <aside className="studio-panel">
          <div className="studio-tabs"><button className={panel === "conversation" ? "active" : ""} onClick={() => setPanel("conversation")}>Conversation</button><button className={panel === "stats" ? "active" : ""} onClick={() => setPanel("stats")}>Statistiques</button></div>
          {panel === "conversation" ? <><div className="studio-comments">{comments.map((message, index) => <p key={`${message}-${index}`}><b>{index === 0 ? "Whappy" : "Vous"}</b>{message}</p>)}</div><form onSubmit={sendComment}><input value={comment} onChange={(event) => setComment(event.target.value)} placeholder="Écrire dans le direct…"/><button>➤</button></form></> : <div className="studio-live-stats"><article><small>SPECTATEURS</small><strong>{viewers}</strong><span>Pic actuel</span></article><article><small>DURÉE</small><strong>{formatDuration(elapsed)}</strong><span>Session en cours</span></article><article><small>INTERACTIONS</small><strong>{Math.max(0, comments.length - 1)}</strong><span>Messages envoyés</span></article><article><small>PRODUIT</small><strong>{productVisible ? "Visible" : "Masqué"}</strong><span>{config.product}</span></article><p>{status === "live" ? "Les indicateurs se mettent à jour pendant cette session." : status === "ended" ? "Résumé final de votre session locale." : "Démarrez le direct pour alimenter les statistiques."}</p></div>}
        </aside>
      </div>

      {error && <p className="studio-error">! {error}</p>}
      {status === "ended" ? <div className="studio-summary"><div><strong>Direct terminé</strong><span>{formatDuration(elapsed)} · {viewers} spectateur{viewers > 1 ? "s" : ""}</span></div><button onClick={onClose}>Fermer le studio</button></div> : <footer className="broadcast-controls">
        <div>
          <button className={micOn ? "active" : ""} onClick={() => toggleTrack("audio")} disabled={!hasStream}>◉ <span>{micOn ? "Micro" : "Muet"}</span></button>
          <button className={cameraOn ? "active" : ""} onClick={() => toggleTrack("video")} disabled={!hasStream}>▣ <span>{cameraOn ? "Caméra" : "Coupée"}</span></button>
          <button onClick={shareLive}>↗ <span>Inviter</span></button>
          <button onClick={() => {
            if (mode === "human") {
              if (!twinAuthorized) { setError("Votre Double doit être créé et autorisé avant le passage de relais."); return; }
              setMode("twin");
            } else setMode("human");
          }}>⇄ <span>{mode === "human" ? "Passer au Double" : "Reprendre"}</span></button>
        </div>
        {mode === "twin" && !twinAuthorized ? <button className="go-live" onClick={onOpenTwin}>Créer mon Double</button> : status === "live" ? <button className="go-live live" onClick={endLive}>■ Terminer</button> : <button className="go-live" onClick={startLive}>● Démarrer le direct</button>}
      </footer>}
      <small className={publicStreamingConnected ? "broadcast-note connected" : "broadcast-note demo"}>{publicStreamingConnected ? "La diffusion publique est active." : "Caméra et micro réels. Aucun spectateur ni message public n’est simulé : connectez le transport média WAPI pour diffuser."}</small>
    </section>
  </div>;
}
