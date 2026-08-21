"use client";
/* eslint-disable jsx-a11y/media-has-caption, jsx-a11y/label-has-associated-control -- capture privée et libellé de consentement visuel compact */

import { FormEvent, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import {
  createTwinAutomation,
  createTwinRenderJob,
  removeTwinAutomation,
  saveTwinProfile,
  toggleTwinAutomation,
  uploadTwinAsset,
  watchTwinAutomations,
  watchTwinProfile,
  type TwinAssetKind,
  type TwinAutomation,
  type TwinGesture,
  type TwinProfile,
  type TwinSequenceStep,
} from "@/lib/whappy-twin";
import { WepiAssistant } from "@/app/components/WepiAssistant";

type Tab = "engine" | "capture" | "voice" | "automation" | "produce" | "wepi";
type StageMode = "digital" | "signature";

const gestures: { id: TwinGesture; label: string; icon: string; description: string }[] = [
  { id: "neutral", label: "Présence", icon: "◎", description: "Posture calme et regard caméra" },
  { id: "welcome", label: "Accueil", icon: "◡", description: "Ouverture des bras et sourire" },
  { id: "explain", label: "Expliquer", icon: "≋", description: "Gestes pédagogiques naturels" },
  { id: "point", label: "Pointer", icon: "↗", description: "Dirige l’attention vers un détail" },
  { id: "show", label: "Présenter", icon: "◇", description: "Mise en valeur d’un produit" },
  { id: "wave", label: "Saluer", icon: "⌁", description: "Salut énergique de la main" },
  { id: "walk", label: "Déplacement", icon: "↔", description: "Mouvement latéral fluide" },
];

const presets: Record<"vente" | "direct" | "reponse", TwinSequenceStep[]> = {
  vente: [{ gesture: "welcome", duration: 2, label: "Accroche" }, { gesture: "show", duration: 4, label: "Produit" }, { gesture: "explain", duration: 5, label: "Bénéfices" }, { gesture: "point", duration: 2, label: "Acheter" }],
  direct: [{ gesture: "wave", duration: 2, label: "Salut live" }, { gesture: "welcome", duration: 3, label: "Accueil" }, { gesture: "show", duration: 5, label: "Démonstration" }, { gesture: "explain", duration: 4, label: "Questions" }],
  reponse: [{ gesture: "neutral", duration: 2, label: "Écoute" }, { gesture: "explain", duration: 5, label: "Réponse" }, { gesture: "welcome", duration: 2, label: "Conclusion" }],
};

const automationPresets = [
  { name: "Vendeur 24/7", trigger: "incoming-message", channel: "inbox", action: "reply-video", icon: "◉", text: "Prépare une réponse vidéo à chaque question client." },
  { name: "Annonce en mouvement", trigger: "new-listing", channel: "market", action: "prepare-video", icon: "◇", text: "Transforme chaque nouvelle annonce en présentation." },
  { name: "Relance panier", trigger: "cart-abandoned", channel: "inbox", action: "notify-owner", icon: "↗", text: "Prépare une relance et demande votre validation." },
];

function clock(seconds: number) { return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`; }

export function TwinEngineStudio({ userId, userName, consent, setConsent, notify, cloud = true }: { userId: string; userName: string; consent: boolean; setConsent: (value: boolean) => void; notify: (text: string) => void; cloud?: boolean }) {
  const [tab, setTab] = useState<Tab>("engine");
  const [profile, setProfile] = useState<TwinProfile | null>(null);
  const [automations, setAutomations] = useState<TwinAutomation[]>([]);
  const [gesture, setGesture] = useState<TwinGesture>("welcome");
  const [intensity, setIntensity] = useState(72);
  const [tempo, setTempo] = useState(56);
  const [sequence, setSequence] = useState<TwinSequenceStep[]>(presets.vente);
  const [playing, setPlaying] = useState(false);
  const [activeStep, setActiveStep] = useState(0);
  const [progress, setProgress] = useState(0);
  const [stageMode, setStageMode] = useState<StageMode>("digital");
  const [captureKind, setCaptureKind] = useState<"video" | "movement">("video");
  const [script, setScript] = useState("Bonjour, bienvenue sur Whappy. Aujourd’hui je vous présente une opportunité pensée pour vous. Regardez ce produit : il est disponible maintenant et je peux répondre à vos questions.");
  const [language, setLanguage] = useState("fr-FR");
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState<TwinAssetKind | null>(null);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [previewUrl, setPreviewUrl] = useState("");

  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const startedRef = useRef(0);
  const videoRef = useRef<HTMLVideoElement>(null);
  const timersRef = useRef<number[]>([]);
  const progressRef = useRef(0);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => { if (!cloud) return; return watchTwinProfile(userId, setProfile, () => notifyRef.current("Profil du Double momentanément hors ligne")); }, [cloud, userId]);
  useEffect(() => { if (!cloud) return; return watchTwinAutomations(userId, setAutomations, () => notifyRef.current("Automatisations momentanément hors ligne")); }, [cloud, userId]);
  useEffect(() => {
    if (!recording) return;
    const timer = window.setInterval(() => setRecordSeconds(Math.floor((Date.now() - startedRef.current) / 1000)), 300);
    return () => window.clearInterval(timer);
  }, [recording]);
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);
  useEffect(() => () => { streamRef.current?.getTracks().forEach((track) => track.stop()); timersRef.current.forEach(window.clearTimeout); window.speechSynthesis?.cancel(); }, []);

  const initials = useMemo(() => userName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH", [userName]);
  const totalDuration = sequence.reduce((sum, item) => sum + item.duration, 0);
  const readiness = [consent, Boolean(profile?.videoUrl), Boolean(profile?.voiceUrl), Boolean(profile?.movementUrl), automations.length > 0].filter(Boolean).length;
  const signatureUrl = profile?.movementUrl || profile?.videoUrl;

  function updateLocalProfile(changes: Partial<TwinProfile>) {
    setProfile((current) => ({ id: "local", userId, displayName: userName, identityConsent: consent, voiceConsent: consent, movementConsent: consent, voiceStatus: "empty", movementStatus: "empty", ...current, ...changes }));
  }

  function clearTimers() { timersRef.current.forEach(window.clearTimeout); timersRef.current = []; }
  function stopMotion() { clearTimers(); setPlaying(false); setProgress(0); setGesture("neutral"); window.speechSynthesis?.cancel(); }
  function previewGesture(next: TwinGesture) {
    clearTimers(); setGesture(next); setPlaying(true); setProgress(0);
    timersRef.current.push(window.setTimeout(() => { setPlaying(false); setGesture("neutral"); }, 1500));
  }
  function runSequence() {
    if (!consent) { notify("Validez d’abord l’utilisation de votre propre identité"); setTab("capture"); return; }
    clearTimers(); setPlaying(true); setProgress(0);
    progressRef.current = 0;
    const progressTimer = window.setInterval(() => { progressRef.current = Math.min(100, progressRef.current + 8 / totalDuration); setProgress(progressRef.current); }, 80);
    timersRef.current.push(progressTimer);
    let elapsed = 0;
    sequence.forEach((item, index) => {
      timersRef.current.push(window.setTimeout(() => { setActiveStep(index); setGesture(item.gesture); }, elapsed * 1000));
      elapsed += Math.max(1, item.duration);
    });
    timersRef.current.push(window.setTimeout(() => { clearTimers(); setProgress(100); setPlaying(false); setGesture("neutral"); }, elapsed * 1000));
  }
  function speak() {
    if (!("speechSynthesis" in window)) { notify("La synthèse vocale de prévisualisation n’est pas disponible ici"); return; }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(script);
    utterance.lang = language; utterance.rate = .75 + tempo / 100; utterance.pitch = .9 + intensity / 250;
    utterance.onstart = runSequence; utterance.onend = () => setPlaying(false);
    window.speechSynthesis.speak(utterance);
    notify(profile?.voiceUrl ? "Aperçu animé lancé — la voix système sera remplacée lors du rendu neuronal" : "Aperçu avec une voix système. Enregistrez votre voix pour préparer son clonage.");
  }
  async function persistConsent(value: boolean) {
    setConsent(value);
    if (!cloud) { updateLocalProfile({ identityConsent: value, voiceConsent: value, movementConsent: value }); notify(value ? "Mode aperçu du Double activé" : "Autorisations locales révoquées"); return; }
    try {
      await saveTwinProfile(userId, { displayName: userName, identityConsent: value, voiceConsent: value, movementConsent: value, voiceStatus: profile?.voiceStatus || "empty", movementStatus: profile?.movementStatus || "empty" });
      notify(value ? "Consentement du Double enregistré" : "Autorisations du Double révoquées");
    } catch { notify("Le consentement n’a pas pu être synchronisé"); }
  }
  async function startCapture(kind: TwinAssetKind) {
    if (!consent) { notify("Confirmez d’abord que l’image et la voix sont les vôtres"); return; }
    if (typeof MediaRecorder === "undefined") { notify("Ce navigateur ne prend pas en charge l’enregistrement"); return; }
    try {
      if (cloud) await saveTwinProfile(userId, { displayName: userName, identityConsent: true, voiceConsent: true, movementConsent: true, voiceStatus: profile?.voiceStatus || "empty", movementStatus: profile?.movementStatus || "empty" });
      const stream = await navigator.mediaDevices.getUserMedia(kind === "voice" ? { audio: { echoCancellation: true, noiseSuppression: true } } : { audio: true, video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } } });
      streamRef.current = stream;
      if (kind !== "voice" && videoRef.current) { videoRef.current.srcObject = stream; await videoRef.current.play(); }
      const preferred = kind === "voice" ? (MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : "audio/webm") : (MediaRecorder.isTypeSupported("video/webm;codecs=vp9,opus") ? "video/webm;codecs=vp9,opus" : "video/webm");
      const recorder = new MediaRecorder(stream, { mimeType: preferred });
      recorderRef.current = recorder; chunksRef.current = []; startedRef.current = Date.now(); setRecordSeconds(0); setRecording(kind);
      recorder.ondataavailable = (event) => { if (event.data.size) chunksRef.current.push(event.data); };
      recorder.onstop = async () => {
        const blob = new Blob(chunksRef.current, { type: preferred });
        const file = new File([blob], `${kind}-${Date.now()}.webm`, { type: preferred });
        stream.getTracks().forEach((track) => track.stop()); setRecording(null);
        const localUrl = URL.createObjectURL(blob); setPreviewUrl(localUrl); setBusy(true);
        try {
          if (cloud) await uploadTwinAsset(userId, file, kind);
          else updateLocalProfile(kind === "voice" ? { voiceUrl: localUrl, voiceStatus: "sampled" } : kind === "video" ? { videoUrl: localUrl } : { movementUrl: localUrl, movementStatus: "sampled" });
          notify(kind === "voice" ? (cloud ? "Empreinte vocale sécurisée" : "Voix prête pour l’aperçu local") : kind === "video" ? (cloud ? "Portrait vidéo sécurisé" : "Portrait prêt pour l’aperçu local") : (cloud ? "Signature de mouvements enregistrée" : "Mouvements prêts pour l’aperçu local"));
        } catch (error) { notify(error instanceof Error && error.message === "asset-too-large" ? "La capture dépasse la taille autorisée" : "La capture n’a pas été synchronisée"); }
        finally { setBusy(false); }
      };
      recorder.start(400);
    } catch { notify(`Autorisez ${kind === "voice" ? "le microphone" : "la caméra et le microphone"} pour continuer`); }
  }
  function stopCapture() { if (recorderRef.current?.state === "recording") recorderRef.current.stop(); }
  function addGesture(item: TwinGesture) { const found = gestures.find((candidate) => candidate.id === item); setSequence((current) => [...current, { gesture: item, duration: 3, label: found?.label || "Mouvement" }]); }
  function updateDuration(index: number, delta: number) { setSequence((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, duration: Math.min(10, Math.max(1, item.duration + delta)) } : item)); }
  function loadPreset(name: keyof typeof presets) { stopMotion(); setSequence(presets[name]); notify(`Séquence ${name} chargée`); }
  async function createAutomation(data: { name: string; trigger: string; channel: string; action: string }) {
    if (!consent) { setTab("capture"); notify("Validez votre identité avant d’activer le Double"); return; }
    setBusy(true);
    try {
      if (cloud) await createTwinAutomation(userId, { ...data, script: script.slice(0, 4000), enabled: true });
      else setAutomations((current) => [{ id: `local-${current.length + 1}`, userId, ...data, script: script.slice(0, 4000), enabled: true }, ...current]);
      notify(`${data.name} est activé${cloud ? "" : " en aperçu"}`);
    }
    catch { notify("L’automatisation n’a pas été créée"); }
    finally { setBusy(false); }
  }
  async function addAutomation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    await createAutomation({ name: String(form.get("name") || "Automatisation Double"), trigger: String(form.get("trigger") || "new-listing"), channel: String(form.get("channel") || "market"), action: String(form.get("action") || "prepare-video") });
    event.currentTarget.reset();
  }
  async function prepareRender() {
    if (!consent) { notify("Le consentement est obligatoire avant toute production"); return; }
    setBusy(true);
    try { if (cloud) await createTwinRenderJob(userId, { title: "Séquence commerciale Whappy", script, language, voiceMode: profile?.voiceUrl ? "owner-voice-sample" : "system-preview", sequence }); notify(cloud ? "Production ajoutée à la file sécurisée" : "Aperçu de production prêt — connectez votre compte pour le synchroniser"); }
    catch { notify("Le plan de production n’a pas été enregistré"); }
    finally { setBusy(false); }
  }

  return <div className="twin-engine">
    <header className="twin-engine-hero">
      <div><span><i /> WHAPPY DOUBLE ENGINE / VOTRE IDENTITÉ</span><h2>Votre présence,<br /><em>multipliée.</em></h2><p>Un studio vivant pour chorégraphier votre Double, préparer votre voix et automatiser vos interventions commerciales — toujours sous votre contrôle.</p><div><b>✓ Votre image uniquement</b><b>✓ Voix révocable</b><b>✓ Label IA permanent</b>{!cloud && <b className="local-mode">◎ APERÇU LOCAL IMMÉDIAT</b>}</div></div>
      <aside><div className="twin-ready-ring" style={{ "--ready": `${readiness * 20}%` } as CSSProperties}><strong>{readiness * 20}%</strong></div><div><small>DOUBLE STATUS</small><b>{readiness >= 4 ? "PRÊT À PRODUIRE" : "EN APPRENTISSAGE"}</b><span>{profile?.voiceUrl ? "Voix capturée" : "Voix à enregistrer"} · {profile?.movementUrl ? "Gestuelle capturée" : "Gestuelle à filmer"}</span></div></aside>
    </header>

    <nav className="twin-engine-tabs">{([['engine', 'Mouvements', '⌁'], ['capture', 'Mon image', '▣'], ['voice', 'Ma voix IA', '◖'], ['automation', 'Automatiser', '⌘'], ['produce', 'Studio vidéo', '✦'], ['wepi', 'WEPI IA', 'W']] as [Tab, string, string][]).map(([id, label, icon]) => <button className={tab === id ? "active" : ""} key={id} onClick={() => setTab(id)}><span>{icon}</span>{label}{((id === "voice" && profile?.voiceUrl) || (id === "capture" && profile?.videoUrl)) && <i />}</button>)}</nav>

    {tab === "engine" && <section className="motion-lab enhanced">
      <div className="motion-stage">
        <header className="stage-toolbar"><span><i /> APERÇU TEMPS RÉEL</span><div><button className={stageMode === "digital" ? "active" : ""} onClick={() => setStageMode("digital")}>Double</button><button disabled={!signatureUrl} className={stageMode === "signature" ? "active" : ""} onClick={() => setStageMode("signature")}>Ma signature</button></div></header>
        {stageMode === "signature" && signatureUrl ? <video className="signature-preview" src={signatureUrl} autoPlay loop muted playsInline /> : <div className={`motion-avatar ${gesture} ${playing ? "playing" : ""}`} style={{ '--motion-intensity': intensity / 100, '--motion-tempo': `${1.45 - tempo / 100}s` } as CSSProperties}><div className="motion-aura" /><div className="avatar-head"><span>{initials}</span><i /><i /></div><div className="avatar-body"><span className="arm left" /><span className="torso"><b>IA</b></span><span className="arm right" /><span className="leg left" /><span className="leg right" /></div><small>DOUBLE DE {userName.toUpperCase()}</small></div>}
        <div className="motion-caption"><span>Créé avec mon Double IA</span><strong>{playing ? sequence[activeStep]?.label : "Prêt pour votre scénario"}</strong></div>
        <div className="motion-hud"><span>GESTE ACTIF</span><strong>{gestures.find((item) => item.id === gesture)?.label}</strong><i>{playing ? "MOUVEMENT EN COURS" : "PRÊT"}</i></div><div className="motion-floor" />
        <div className="stage-progress"><i style={{ width: `${progress}%` }} /></div>
      </div>
      <aside className="motion-controls">
        <header><div><small>MOTION CORE 2.0</small><h3>Donnez-lui votre langage corporel</h3></div><button className={playing ? "stop" : ""} onClick={playing ? stopMotion : runSequence}>{playing ? "■ Stop" : "▶ Jouer"}</button></header>
        <div className="sequence-presets"><button onClick={() => loadPreset("vente")}>◇ Vente</button><button onClick={() => loadPreset("direct")}>● Direct</button><button onClick={() => loadPreset("reponse")}>◖ Réponse</button></div>
        <div className="gesture-grid">{gestures.map((item) => <article className={gesture === item.id ? "active" : ""} key={item.id}><button onClick={() => previewGesture(item.id)}><span>{item.icon}</span><div><strong>{item.label}</strong><small>{item.description}</small></div></button><button aria-label={`Ajouter ${item.label} à la séquence`} onClick={() => addGesture(item.id)}>＋</button></article>)}</div>
        <div className="motion-sliders"><label>Énergie <b>{intensity}%</b><input aria-label="Énergie des mouvements" type="range" min="20" max="100" value={intensity} onChange={(event) => setIntensity(Number(event.target.value))} /></label><label>Rythme <b>{tempo}%</b><input aria-label="Rythme des mouvements" type="range" min="20" max="100" value={tempo} onChange={(event) => setTempo(Number(event.target.value))} /></label></div>
      </aside>
    </section>}

    {tab === "capture" && <section className="twin-module capture-module">
      <div className="module-copy"><span>01 / IDENTITÉ SOUVERAINE</span><h3>Apprenez-lui votre visage et vos mouvements.</h3><p>Deux capsules courtes suffisent pour préparer votre présence : votre portrait face caméra, puis votre façon naturelle de bouger et de présenter un objet.</p><label className="sovereign-consent"><input type="checkbox" checked={consent} onChange={(event) => void persistConsent(event.target.checked)} /><span><strong>Je suis la personne enregistrée</strong><small>J’autorise Whappy à utiliser uniquement mon image, ma voix et mes mouvements. Je peux tout révoquer.</small></span></label><div className="capture-selector"><button className={captureKind === "video" ? "active" : ""} onClick={() => setCaptureKind("video")}><b>{profile?.videoUrl ? "✓" : "1"}</b><span>Portrait vidéo<small>Regard, sourire, expressions</small></span></button><button className={captureKind === "movement" ? "active" : ""} onClick={() => setCaptureKind("movement")}><b>{profile?.movementUrl ? "✓" : "2"}</b><span>Mouvements<small>Gestes, posture, présentation</small></span></button></div>
      </div>
      <div className={`capture-stage ${recording || ""}`}><video ref={videoRef} muted playsInline />{!recording && <div><span>{captureKind === "video" ? "◉" : "⌁"}</span><strong>{captureKind === "video" ? (profile?.videoUrl ? "Portrait vidéo enregistré" : "Cadrez votre visage et vos épaules") : (profile?.movementUrl ? "Signature mouvement enregistrée" : "Placez-vous debout, corps visible")}</strong><small>{captureKind === "video" ? "15 à 30 secondes" : "30 à 60 secondes"}</small></div>}{recording && <b>● CAPTURE · {clock(recordSeconds)}</b>}<button disabled={busy} onClick={recording ? stopCapture : () => startCapture(captureKind)}>{recording ? "■ Terminer et sécuriser" : `▣ Enregistrer ${captureKind === "video" ? "mon portrait" : "mes mouvements"}`}</button></div>
    </section>}

    {tab === "voice" && <section className="twin-module voice-module"><div className="voice-orb"><div className={recording === "voice" ? "listening" : ""}>{Array.from({ length: 36 }, (_, index) => <i style={{ '--i': index } as CSSProperties} key={index} />)}</div><span>{recording === "voice" ? clock(recordSeconds) : profile?.voiceUrl ? "✓" : "◖"}</span></div><div className="module-copy"><span>02 / ADN VOCAL</span><h3>Votre ton, même quand vous n’êtes pas devant la caméra.</h3><p>Lisez l’échantillon dans un endroit calme. Whappy prépare votre empreinte vocale ; l’aperçu de travail reste volontairement une voix système jusqu’à la connexion du moteur neuronal.</p><blockquote>« Bonjour, je suis {userName}. Cette voix est la mienne et j’autorise son utilisation uniquement par mon Double Whappy pour les contenus que je valide. »</blockquote><div className="voice-quality"><span className={recordSeconds >= 15 ? "done" : ""}>✓ Environnement calme</span><span className={recordSeconds >= 20 ? "done" : ""}>✓ Échantillon suffisant</span><span className={profile?.voiceUrl ? "done" : ""}>✓ Stockage sécurisé</span></div><button className={recording === "voice" ? "stop" : ""} disabled={busy} onClick={recording === "voice" ? stopCapture : () => startCapture("voice")}>{recording === "voice" ? "■ Terminer et sécuriser" : profile?.voiceUrl ? "↻ Réenregistrer ma voix" : "● Enregistrer mon empreinte vocale"}</button>{previewUrl && <audio src={previewUrl} controls />}<small className="voice-safety">◆ Toute production future est bloquée dès que vous révoquez votre autorisation.</small></div></section>}

    {tab === "automation" && <section className="automation-lab improved"><div className="automation-builder"><span>03 / ORCHESTRATEUR</span><h3>Choisissez une mission pour votre Double.</h3><div className="auto-presets">{automationPresets.map((item) => <article key={item.name}><span>{item.icon}</span><div><strong>{item.name}</strong><p>{item.text}</p></div><button disabled={busy} onClick={() => void createAutomation(item)}>Activer</button></article>)}</div><details><summary>Créer une règle personnalisée</summary><form onSubmit={addAutomation}><div className="auto-grid"><label>Nom<input name="name" required placeholder="Ex. Présenter chaque nouvelle annonce" /></label><label>Déclencheur<select name="trigger"><option value="new-listing">Nouvelle annonce publiée</option><option value="incoming-message">Question client reçue</option><option value="live-start">Démarrage d’un direct</option><option value="schedule">Horaire programmé</option><option value="cart-abandoned">Panier abandonné</option></select></label><label>Destination<select name="channel"><option value="market">Marketplace</option><option value="inbox">Message privé</option><option value="live">Direct Whappy</option><option value="moments">Fil Moments</option></select></label><label>Action<select name="action"><option value="prepare-video">Préparer une vidéo</option><option value="reply-video">Préparer une réponse vidéo</option><option value="publish-moment">Créer un Moment</option><option value="notify-owner">Demander ma validation</option></select></label></div><button disabled={busy || !consent}>＋ Activer cette automatisation</button></form></details></div><div className="automation-list"><header><small>MISSIONS DU DOUBLE</small><strong>{automations.filter((item) => item.enabled).length} mission(s) active(s)</strong></header>{automations.map((item) => <article key={item.id}><span className={item.enabled ? "on" : ""}>⌘</span><div><strong>{item.name}</strong><small>{item.trigger} → {item.channel}</small></div><button onClick={() => cloud ? void toggleTwinAutomation(userId, item.id, !item.enabled) : setAutomations((current) => current.map((candidate) => candidate.id === item.id ? { ...candidate, enabled: !candidate.enabled } : candidate))}>{item.enabled ? "Actif" : "Pause"}</button><button className="danger" aria-label={`Supprimer ${item.name}`} onClick={() => cloud ? void removeTwinAutomation(userId, item.id) : setAutomations((current) => current.filter((candidate) => candidate.id !== item.id))}>×</button></article>)}{!automations.length && <p>Votre Double attend sa première mission.</p>}</div></section>}

    {tab === "wepi" && <section className="twin-module wepi-module"><WepiAssistant userId={userId} userName={userName} cloud={cloud} notify={notify}/></section>}

    {tab === "produce" && <section className="production-lab"><div className="production-script"><span>04 / RÉALISATION</span><h3>Écrivez. Chorégraphiez. Lancez.</h3><label>Langue<select value={language} onChange={(event) => setLanguage(event.target.value)}><option value="fr-FR">Français</option><option value="ln-CD">Lingala</option><option value="en-US">Anglais</option></select></label><label>Scénario <small>{script.length}/4000</small><textarea value={script} onChange={(event) => setScript(event.target.value)} maxLength={4000} /></label><div><button onClick={speak}>▶ Aperçu complet</button><button className="primary" disabled={busy || !consent} onClick={prepareRender}>✦ Préparer la vidéo</button></div><small>Aperçu immédiat avec voix système. Le rendu photoréaliste utilisera votre empreinte seulement après connexion du fournisseur neuronal.</small></div><div className="sequence-board"><header><div><small>CHRONOLOGIE DES MOUVEMENTS</small><strong>{totalDuration} secondes · {sequence.length} gestes</strong></div><button onClick={() => setSequence(presets.vente)}>Réinitialiser</button></header><div className="sequence-track">{sequence.map((item, index) => <article className={playing && activeStep === index ? "active" : ""} style={{ flex: item.duration }} key={`${item.label}-${index}`}><button className="sequence-select" onClick={() => previewGesture(item.gesture)}><span>{gestures.find((candidate) => candidate.id === item.gesture)?.icon}</span><strong>{item.label}</strong></button><div><button aria-label="Réduire la durée" onClick={() => updateDuration(index, -1)}>−</button><small>{item.duration}s</small><button aria-label="Augmenter la durée" onClick={() => updateDuration(index, 1)}>＋</button></div><button className="sequence-remove" aria-label={`Retirer ${item.label}`} onClick={() => setSequence((current) => current.filter((_, itemIndex) => itemIndex !== index))}>×</button></article>)}</div><div className="sequence-library">{gestures.slice(1).map((item) => <button onClick={() => addGesture(item.id)} key={item.id}>＋ {item.label}</button>)}</div><footer><span>DIVULGATION AUTOMATIQUE</span><strong>Créé avec le Double IA de {userName}</strong><i>✓ Toujours activée</i></footer></div></section>}
  </div>;
}
