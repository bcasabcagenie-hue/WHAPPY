"use client";
/* eslint-disable @next/next/no-img-element -- aperçu local d’un fichier Blob sélectionné par l’utilisateur */

import { ChangeEvent, useMemo, useRef, useState } from "react";

type HubTab = "emoji" | "whappies" | "language" | "studio";
type MediaEffect = "comic" | "neon" | "ink" | "pop";
type TranslationSession = { translate: (input: string) => Promise<string>; destroy?: () => void };
type TranslatorFactory = {
  availability: (options: { sourceLanguage: string; targetLanguage: string }) => Promise<string>;
  create: (options: { sourceLanguage: string; targetLanguage: string }) => Promise<TranslationSession>;
};
type DetectorFactory = {
  availability: () => Promise<string>;
  create: () => Promise<{ detect: (input: string) => Promise<Array<{ detectedLanguage: string; confidence: number }>>; destroy?: () => void }>;
};
type ProofreaderFactory = {
  availability: (options: { expectedInputLanguages: string[] }) => Promise<string>;
  create: (options: { expectedInputLanguages: string[] }) => Promise<{ proofread: (input: string) => Promise<{ correctedInput: string }>; destroy?: () => void }>;
};
type SpeechRecognitionInstance = {
  lang: string; continuous: boolean; interimResults: boolean;
  onresult: ((event: { results: ArrayLike<{ 0: { transcript: string }; isFinal: boolean }> }) => void) | null;
  onerror: (() => void) | null; onend: (() => void) | null; start: () => void; stop: () => void;
};
type BrowserAI = typeof globalThis & {
  Translator?: TranslatorFactory;
  LanguageDetector?: DetectorFactory;
  Proofreader?: ProofreaderFactory;
  SpeechRecognition?: new () => SpeechRecognitionInstance;
  webkitSpeechRecognition?: new () => SpeechRecognitionInstance;
};

const emojiGroups = [
  { name: "Visages", icon: "😀", items: "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😋 😎 🤩 🥳 😏 😢 😭 😤 😡 🤯 🥶 🫶" },
  { name: "Gestes", icon: "🙌", items: "👍 👎 👌 🤌 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 👋 🤝 👏 🙌 👐 💪 🙏" },
  { name: "Cœurs", icon: "💙", items: "❤️ 🧡 💛 💙 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 ❤️‍🩹 💕 💞 💓 💗 💖 💘 💝 💟 ❣️" },
  { name: "Vie", icon: "🌍", items: "🌍 🌎 🌏 🌞 🌙 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️ 🌧️ 🌊 🌴 🌸 🌹 🍀 🦁 🐆 🦋 🕊️" },
  { name: "Fête", icon: "🎉", items: "🎉 🎊 🎈 🎁 🏆 🥇 ⚽ 🏀 🎵 🎶 🎤 🎧 🎬 📸 🚀 💎 👑 🪩 🥂 🍾 🍰" },
  { name: "Business", icon: "💼", items: "💼 📈 📊 💰 💳 🛍️ 🛒 📦 🚚 🏪 🏢 🤝 ✅ ☑️ 📌 📍 📅 ⏰ 📣 💡 🔒 🛡️" },
];

const symbols = "✓ ✔ ✦ ✧ ★ ☆ ◆ ◇ ● ○ ◉ ◎ ⌁ ≋ ∞ → ← ↑ ↓ ↗ ↘ ⇄ + − × ÷ = ≠ ≤ ≥ @ # % & © ® ™ € $ £ ¥ ₣ ♫ ♪ ☀ ☾ ♡ ♥ ⚡ ☎ ⌘ ⌖".split(" ");
const whappies = [
  { face: "😎", name: "Boss", line: "On passe au niveau supérieur !", colors: ["#169dd6", "#06394f"] },
  { face: "🤩", name: "Star", line: "C’est du lourd ✦", colors: ["#8a40ff", "#ef3cae"] },
  { face: "😂", name: "Mdr", line: "Je ne peux plus respirer !", colors: ["#ffb21b", "#ff5f28"] },
  { face: "🫶", name: "Love", line: "Force et amour à toi", colors: ["#ff477d", "#b51455"] },
  { face: "🤯", name: "Choc", line: "Attends… QUOI ?!", colors: ["#24b9ff", "#2946a5"] },
  { face: "🦁", name: "Fierté", line: "Le courage parle maintenant", colors: ["#e4a116", "#74440b"] },
  { face: "👑", name: "Royal", line: "La classe ne se discute pas", colors: ["#d6af32", "#331b55"] },
  { face: "🚀", name: "Fusée", line: "Direction le sommet", colors: ["#007cf7", "#075173"] },
];

const languages = [
  ["fr", "Français"], ["en", "English"], ["es", "Español"], ["pt", "Português"],
  ["de", "Deutsch"], ["ar", "العربية"], ["zh", "中文"], ["ja", "日本語"], ["ln", "Lingala"],
];

function aiBrowser() { return globalThis as BrowserAI; }

export async function translateTextOnDevice(input: string, targetLanguage: string, sourceLanguage?: string) {
  const browser = aiBrowser();
  let source = sourceLanguage;
  if (!source && browser.LanguageDetector) {
    const availability = await browser.LanguageDetector.availability();
    if (availability !== "unavailable") {
      const detector = await browser.LanguageDetector.create();
      const result = await detector.detect(input);
      source = result.sort((left, right) => right.confidence - left.confidence)[0]?.detectedLanguage;
      detector.destroy?.();
    }
  }
  source ||= targetLanguage === "fr" ? "en" : "fr";
  if (source === targetLanguage) return input;
  if (!browser.Translator) throw new Error("translator-unavailable");
  const availability = await browser.Translator.availability({ sourceLanguage: source, targetLanguage });
  if (availability === "unavailable") throw new Error("language-unavailable");
  const translator = await browser.Translator.create({ sourceLanguage: source, targetLanguage });
  const result = await translator.translate(input);
  translator.destroy?.();
  return result;
}

function polishLocally(input: string) {
  const replacements: Array<[RegExp, string]> = [
    [/\bsa va\b/gi, "ça va"], [/\bca va\b/gi, "ça va"], [/\bj ai\b/gi, "j’ai"], [/\bc est\b/gi, "c’est"],
    [/\bn est\b/gi, "n’est"], [/\bd accord\b/gi, "d’accord"], [/\baujourd hui\b/gi, "aujourd’hui"], [/\btu a\b/gi, "tu as"],
  ];
  let value = input.trim().replace(/\s+([,.;!?])/g, "$1").replace(/([,.;!?])(\S)/g, "$1 $2").replace(/\s{2,}/g, " ");
  replacements.forEach(([pattern, replacement]) => { value = value.replace(pattern, replacement); });
  return value ? value[0].toLocaleUpperCase("fr") + value.slice(1) : value;
}

function wrapText(context: CanvasRenderingContext2D, value: string, maxWidth: number) {
  const words = value.trim().split(/\s+/); const lines: string[] = []; let line = "";
  words.forEach((word) => { const attempt = line ? `${line} ${word}` : word; if (context.measureText(attempt).width > maxWidth && line) { lines.push(line); line = word; } else line = attempt; });
  if (line) lines.push(line); return lines.slice(0, 4);
}

async function stickerFile(face: string, line: string, colors: string[]) {
  const canvas = document.createElement("canvas"); canvas.width = 720; canvas.height = 720;
  const context = canvas.getContext("2d"); if (!context) throw new Error("canvas-unavailable");
  const gradient = context.createLinearGradient(0, 0, 720, 720); gradient.addColorStop(0, colors[0]); gradient.addColorStop(1, colors[1]);
  context.fillStyle = gradient; context.fillRect(0, 0, 720, 720);
  context.fillStyle = "rgba(255,255,255,.13)"; context.beginPath(); context.arc(590, 120, 185, 0, Math.PI * 2); context.fill();
  context.textAlign = "center"; context.font = "240px system-ui"; context.fillText(face, 360, 320);
  context.fillStyle = "white"; context.font = "900 58px Arial"; context.shadowColor = "rgba(0,0,0,.35)"; context.shadowBlur = 12;
  wrapText(context, line.toUpperCase(), 610).forEach((text, index) => context.fillText(text, 360, 465 + index * 62));
  context.shadowBlur = 0; context.font = "900 22px Arial"; context.fillText("WHAPPY • WHAPPIES", 360, 675);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
  if (!blob) throw new Error("canvas-export"); return new File([blob], `whappie-${Date.now()}.png`, { type: "image/png" });
}

function loadImage(file: File) {
  return new Promise<HTMLImageElement>((resolve, reject) => { const image = new Image(); const url = URL.createObjectURL(file); image.onload = () => { URL.revokeObjectURL(url); resolve(image); }; image.onerror = () => { URL.revokeObjectURL(url); reject(new Error("image-invalid")); }; image.src = url; });
}

async function memeFile(file: File, caption: string, effect: MediaEffect, symbol: string) {
  const image = await loadImage(file); const canvas = document.createElement("canvas"); canvas.width = 1080; canvas.height = 1080;
  const context = canvas.getContext("2d"); if (!context) throw new Error("canvas-unavailable");
  context.filter = effect === "comic" ? "contrast(1.55) saturate(1.7)" : effect === "neon" ? "contrast(1.25) saturate(2.2) hue-rotate(18deg)" : effect === "ink" ? "grayscale(1) contrast(1.8)" : "saturate(1.45) brightness(1.08)";
  const scale = Math.max(1080 / image.width, 1080 / image.height); const width = image.width * scale; const height = image.height * scale;
  context.drawImage(image, (1080 - width) / 2, (1080 - height) / 2, width, height); context.filter = "none";
  const shade = context.createLinearGradient(0, 560, 0, 1080); shade.addColorStop(0, "transparent"); shade.addColorStop(1, "rgba(0,0,0,.88)"); context.fillStyle = shade; context.fillRect(0, 0, 1080, 1080);
  context.strokeStyle = "#07161c"; context.lineWidth = 18; context.fillStyle = "#fff"; context.textAlign = "center"; context.font = "900 76px Arial";
  wrapText(context, caption || "ÇA, C’EST WHAPPY !", 920).forEach((text, index) => { const y = 800 + index * 84; context.strokeText(text.toUpperCase(), 540, y); context.fillText(text.toUpperCase(), 540, y); });
  context.font = "110px system-ui"; context.textAlign = "right"; context.fillText(symbol, 1010, 145); context.textAlign = "left"; context.font = "900 25px Arial"; context.fillStyle = "#55cdff"; context.fillText("WHAPPY MEME LAB", 44, 62);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", .9));
  if (!blob) throw new Error("canvas-export"); return new File([blob], `whappy-meme-${Date.now()}.jpg`, { type: "image/jpeg" });
}

export function WhappyExpressionHub({ draft, onDraftChange, onSendMedia, notify, onClose }: {
  draft: string; onDraftChange: (value: string) => void;
  onSendMedia: (file: File, kind: "image" | "video", effect?: MediaEffect, caption?: string) => Promise<void>;
  notify: (text: string) => void; onClose: () => void;
}) {
  const [tab, setTab] = useState<HubTab>("emoji"); const [emojiGroup, setEmojiGroup] = useState(0); const [translated, setTranslated] = useState("");
  const [source, setSource] = useState("fr"); const [target, setTarget] = useState("en"); const [busy, setBusy] = useState(false); const [listening, setListening] = useState(false);
  const [customLine, setCustomLine] = useState(whappies[0].line); const [selectedWhappie, setSelectedWhappie] = useState(0); const [studioFile, setStudioFile] = useState<File | null>(null); const [studioUrl, setStudioUrl] = useState("");
  const [caption, setCaption] = useState(""); const [effect, setEffect] = useState<MediaEffect>("comic"); const [symbol, setSymbol] = useState("⚡"); const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const isVideo = Boolean(studioFile?.type.startsWith("video/"));
  const languageName = useMemo(() => languages.find(([code]) => code === target)?.[1] || target, [target]);

  function insert(value: string) { onDraftChange(`${draft}${draft && !draft.endsWith(" ") ? " " : ""}${value}`); }
  async function correct() {
    if (!draft.trim()) return; setBusy(true);
    try {
      const proofreader = aiBrowser().Proofreader;
      if (proofreader && await proofreader.availability({ expectedInputLanguages: [source] }) !== "unavailable") {
        const session = await proofreader.create({ expectedInputLanguages: [source] }); const result = await session.proofread(draft); session.destroy?.(); onDraftChange(result.correctedInput); notify("Orthographe et grammaire corrigées sur cet appareil");
      } else { onDraftChange(polishLocally(draft)); notify("Ponctuation corrigée. Les fautes restantes sont soulignées par le navigateur."); }
    } catch { onDraftChange(polishLocally(draft)); notify("Correction locale appliquée"); } finally { setBusy(false); }
  }
  async function translate() {
    if (!draft.trim()) return; setBusy(true); setTranslated("");
    try { setTranslated(await translateTextOnDevice(draft, target, source)); notify(`Traduction ${languageName} prête sur cet appareil`); }
    catch { notify("Cette paire de langues n’est pas encore disponible sur cet ordinateur"); } finally { setBusy(false); }
  }
  function speak(value = translated || draft, language = translated ? target : source) {
    if (!value.trim() || !("speechSynthesis" in window)) { notify("Lecture vocale indisponible sur cet appareil"); return; }
    window.speechSynthesis.cancel(); const utterance = new SpeechSynthesisUtterance(value); utterance.lang = language; window.speechSynthesis.speak(utterance);
  }
  function dictate() {
    if (listening) { recognitionRef.current?.stop(); return; }
    const Constructor = aiBrowser().SpeechRecognition || aiBrowser().webkitSpeechRecognition;
    if (!Constructor) { notify("La dictée vocale n’est pas disponible dans ce navigateur"); return; }
    const recognition = new Constructor(); recognition.lang = source === "ln" ? "ln-CD" : source; recognition.continuous = false; recognition.interimResults = true; recognitionRef.current = recognition;
    recognition.onresult = (event) => { const value = Array.from(event.results).map((result) => result[0].transcript).join(" "); onDraftChange(value); };
    recognition.onerror = () => notify("La dictée n’a pas reconnu la voix"); recognition.onend = () => setListening(false); setListening(true); recognition.start();
  }
  async function sendWhappie() {
    setBusy(true); try { const item = whappies[selectedWhappie]; await onSendMedia(await stickerFile(item.face, customLine || item.line, item.colors), "image"); notify("WHAPPIE envoyé"); onClose(); } catch { notify("Le WHAPPIE n’a pas pu être créé"); } finally { setBusy(false); }
  }
  function chooseStudioFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; if (!file) return; if (studioUrl) URL.revokeObjectURL(studioUrl); setStudioFile(file); setStudioUrl(URL.createObjectURL(file));
  }
  async function sendStudio() {
    if (!studioFile) return; setBusy(true);
    try {
      if (isVideo) await onSendMedia(studioFile, "video", effect, caption);
      else await onSendMedia(await memeFile(studioFile, caption, effect, symbol), "image");
      notify(isVideo ? "Vidéo WHAPPY envoyée avec son effet caricature" : "Mème WHAPPY créé et envoyé"); onClose();
    } catch { notify("La création WHAPPY n’a pas pu être envoyée"); } finally { setBusy(false); }
  }

  return <section className="expression-hub" aria-label="Hub d’expression WHAPPY">
    <header><div><small>WHAPPY EXPRESSION HUB</small><strong>Plus qu’un message.</strong></div><button type="button" onClick={onClose} aria-label="Fermer">×</button></header>
    <nav>{([['emoji','Emojis & symboles','☺'],['whappies','WHAPPIES','◉'],['language','Langues & voix','文'],['studio','Meme Lab','✦']] as [HubTab,string,string][]).map(([id,label,icon]) => <button type="button" className={tab === id ? "active" : ""} key={id} onClick={() => setTab(id)}><span>{icon}</span>{label}</button>)}</nav>
    {tab === "emoji" && <div className="emoji-lab"><aside>{emojiGroups.map((group, index) => <button type="button" className={emojiGroup === index ? "active" : ""} onClick={() => setEmojiGroup(index)} key={group.name} title={group.name}>{group.icon}</button>)}</aside><main><small>{emojiGroups[emojiGroup].name.toUpperCase()}</small><div>{emojiGroups[emojiGroup].items.split(" ").map((emoji, index) => <button type="button" onClick={() => insert(emoji)} key={`${emoji}-${index}`}>{emoji}</button>)}</div><small>SYMBOLES WHAPPY</small><div className="symbol-grid">{symbols.map((item, index) => <button type="button" onClick={() => insert(item)} key={`${item}-${index}`}>{item}</button>)}</div></main></div>}
    {tab === "whappies" && <div className="whappies-lab"><div className="whappies-grid">{whappies.map((item, index) => <button type="button" className={selectedWhappie === index ? "active" : ""} style={{ background: `linear-gradient(145deg,${item.colors[0]},${item.colors[1]})` }} onClick={() => { setSelectedWhappie(index); setCustomLine(item.line); }} key={item.name}><span>{item.face}</span><strong>{item.name}</strong></button>)}</div><aside style={{ background: `linear-gradient(145deg,${whappies[selectedWhappie].colors[0]},${whappies[selectedWhappie].colors[1]})` }}><span>{whappies[selectedWhappie].face}</span><strong>{customLine || whappies[selectedWhappie].line}</strong><small>WHAPPY • WHAPPIES</small></aside><div><label>Votre phrase<input value={customLine} maxLength={90} spellCheck onChange={(event) => setCustomLine(event.target.value)}/></label><button type="button" disabled={busy} onClick={sendWhappie}>Créer et envoyer →</button></div></div>}
    {tab === "language" && <div className="language-lab"><div className="language-selects"><label>Langue du message<select value={source} onChange={(event) => setSource(event.target.value)}>{languages.map(([code,name]) => <option value={code} key={code}>{name}</option>)}</select></label><button type="button" onClick={() => { setSource(target); setTarget(source); }}>⇄</button><label>Traduire vers<select value={target} onChange={(event) => setTarget(event.target.value)}>{languages.map(([code,name]) => <option value={code} key={code}>{name}</option>)}</select></label></div><div className="language-actions"><button type="button" className={listening ? "listening" : ""} onClick={dictate}>● {listening ? "J’écoute…" : "Dicter"}</button><button type="button" disabled={busy || !draft.trim()} onClick={correct}>✓ Corriger</button><button type="button" disabled={busy || !draft.trim()} onClick={translate}>文 Traduire</button><button type="button" disabled={!draft.trim()} onClick={() => speak()}>▶ Écouter</button></div>{translated && <article><small>TRADUCTION · {languageName}</small><p>{translated}</p><div><button type="button" onClick={() => speak(translated, target)}>▶ Lire</button><button type="button" onClick={() => { onDraftChange(translated); setTranslated(""); }}>Utiliser cette traduction</button></div></article>}<p>La correction, la détection et la traduction compatibles s’exécutent directement sur l’ordinateur. Sur mobile ou navigateur non compatible, la correction orthographique native reste active.</p></div>}
    {tab === "studio" && <div className="meme-lab"><label className="meme-drop">{studioUrl ? isVideo ? <video src={studioUrl} muted loop autoPlay className={effect}/> : <img src={studioUrl} alt="Aperçu du mème" className={effect}/> : <><span>＋</span><strong>Ajoutez votre photo ou vidéo</strong><small>WHAPPY crée un mème ou applique un style caricature</small></>}<input type="file" accept="image/*,video/*" onChange={chooseStudioFile}/>{studioUrl && <b>Changer le média</b>}</label><aside><label>Légende<input value={caption} maxLength={100} spellCheck onChange={(event) => setCaption(event.target.value)} placeholder="La phrase qui va faire réagir…"/></label><small>STYLE VISUEL</small><div className="effect-grid">{([['comic','BD'],['neon','Néon'],['ink','Encre'],['pop','Pop']] as [MediaEffect,string][]).map(([id,label]) => <button type="button" className={effect === id ? "active" : ""} onClick={() => setEffect(id)} key={id}>{label}</button>)}</div><small>SYMBOLE SIGNATURE</small><div className="meme-symbols">{["⚡","🔥","😂","👑","🚀","💙"].map((item) => <button type="button" className={symbol === item ? "active" : ""} onClick={() => setSymbol(item)} key={item}>{item}</button>)}</div><button type="button" className="meme-send" disabled={!studioFile || busy} onClick={sendStudio}>{busy ? "Création…" : isVideo ? "Envoyer la vidéo stylisée →" : "Créer et envoyer le mème →"}</button><p>Les effets photo sont intégrés au fichier. Pour la vidéo, l’effet est appliqué dans le lecteur WHAPPY et l’original reste intact.</p></aside></div>}
  </section>;
}
