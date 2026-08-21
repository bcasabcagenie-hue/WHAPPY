"use client";

import { useEffect, useRef, useState } from "react";

type Props = { onComplete: () => void };

export function TwinRecorder({ onComplete }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const playbackRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const timerRef = useRef<number | null>(null);
  const [state, setState] = useState<"closed" | "ready" | "recording" | "review">("closed");
  const [seconds, setSeconds] = useState(0);
  const [videoUrl, setVideoUrl] = useState("");
  const [error, setError] = useState("");

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  useEffect(() => () => {
    stopCamera();
    if (timerRef.current) window.clearInterval(timerRef.current);
  }, []);

  useEffect(() => () => {
    if (videoUrl) URL.revokeObjectURL(videoUrl);
  }, [videoUrl]);

  async function openCamera() {
    setError("");
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setError("L’enregistrement vidéo n’est pas pris en charge par ce navigateur.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } }, audio: true });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setState("ready");
    } catch (reason) {
      setError(reason instanceof DOMException && reason.name === "NotAllowedError" ? "Autorisez la caméra et le micro pour enregistrer votre capsule." : "Impossible d’ouvrir la caméra. Fermez les autres applications vidéo puis réessayez.");
    }
  }

  function startRecording() {
    if (!streamRef.current) return;
    const chunks: BlobPart[] = [];
    const mimeType = MediaRecorder.isTypeSupported("video/webm;codecs=vp9,opus") ? "video/webm;codecs=vp9,opus" : "video/webm";
    const recorder = new MediaRecorder(streamRef.current, { mimeType });
    recorderRef.current = recorder;
    recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
    recorder.onstop = () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
      timerRef.current = null;
      const url = URL.createObjectURL(new Blob(chunks, { type: mimeType }));
      setVideoUrl(url);
      setState("review");
      stopCamera();
    };
    recorder.start(500);
    setSeconds(0);
    setState("recording");
    timerRef.current = window.setInterval(() => setSeconds((value) => {
      if (value >= 89) {
        window.setTimeout(() => recorderRef.current?.state === "recording" && recorderRef.current.stop(), 0);
        return 90;
      }
      return value + 1;
    }), 1000);
  }

  function stopRecording() {
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
  }

  function retry() {
    if (videoUrl) URL.revokeObjectURL(videoUrl);
    setVideoUrl("");
    setSeconds(0);
    setState("closed");
    void openCamera();
  }

  return <div className="twin-recorder">
    <span className="builder-icon record">●</span>
    <h3>Enregistrez votre capsule source</h3>
    <p>Lisez le texte guidé face caméra. La capsule reste sur cet appareil tant que vous ne la transmettez pas au futur service de génération.</p>
    <div className={`recorder-frame ${state}`}>
      {state === "review" ? <video ref={playbackRef} src={videoUrl} controls playsInline><track kind="captions" srcLang="fr" label="Français" /></video> : <video ref={videoRef} muted playsInline />}
      {state === "closed" && <div><span>▣</span><strong>Caméra fermée</strong><small>Préparez un endroit calme et lumineux</small></div>}
      {state === "recording" && <b>● REC · {seconds}s / 90s</b>}
      {state === "ready" && <b>PRÊT · Cadrez votre visage</b>}
    </div>
    <blockquote>« Bonjour, je présente aujourd’hui mes produits Whappy. Je confirme être la personne filmée et autoriser uniquement les usages que je validerai. »</blockquote>
    {error && <p className="recorder-error">! {error}</p>}
    <div className="recorder-actions">
      {state === "closed" && <button onClick={openCamera}>▣ Ouvrir la caméra</button>}
      {state === "ready" && <button onClick={startRecording}>● Commencer l&apos;enregistrement</button>}
      {state === "recording" && <button className="stop" onClick={stopRecording}>■ Arrêter et prévisualiser</button>}
      {state === "review" && <><button className="secondary" onClick={retry}>↻ Refaire</button><a href={videoUrl} download="capsule-double-whappy.webm">↓ Télécharger une copie</a><button onClick={onComplete}>Utiliser cette capsule ↗</button></>}
    </div>
  </div>;
}
