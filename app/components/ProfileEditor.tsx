"use client";

import { ChangeEvent, useMemo, useState } from "react";

type ProfileSettings = {
  notifications: boolean;
  readReceipts: boolean;
  showOnline: boolean;
  autoDownload: boolean;
};

const defaultSettings: ProfileSettings = { notifications: true, readReceipts: true, showOnline: true, autoDownload: false };

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
}

async function cropSquare(file: File, zoom: number, offsetX: number, offsetY: number) {
  const image = new Image();
  const source = URL.createObjectURL(file);
  image.src = source;
  await new Promise<void>((resolve, reject) => { image.onload = () => resolve(); image.onerror = () => reject(new Error("invalid-image")); });
  const size = 640;
  const scale = Math.max(size / image.naturalWidth, size / image.naturalHeight) * zoom;
  const width = image.naturalWidth * scale;
  const height = image.naturalHeight * scale;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("canvas-unavailable");
  context.fillStyle = "#111";
  context.fillRect(0, 0, size, size);
  const maxX = Math.max(0, (width - size) / 2);
  const maxY = Math.max(0, (height - size) / 2);
  context.drawImage(image, (size - width) / 2 + offsetX * maxX, (size - height) / 2 + offsetY * maxY, width, height);
  URL.revokeObjectURL(source);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", .9));
  if (!blob) throw new Error("crop-failed");
  return new File([blob], "whappy-profile.jpg", { type: "image/jpeg" });
}

export function ProfileEditor({ name, photoUrl, userKey, onSave, notify }: { name: string; photoUrl: string; userKey: string; onSave: (name: string, photo: File | null) => Promise<void>; notify: (message: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [draftName, setDraftName] = useState(name);
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState("");
  const [zoom, setZoom] = useState(1);
  const [offsetX, setOffsetX] = useState(0);
  const [offsetY, setOffsetY] = useState(0);
  const [busy, setBusy] = useState(false);
  const [settings, setSettings] = useState<ProfileSettings>(() => {
    if (typeof window === "undefined") return defaultSettings;
    try {
      const saved = JSON.parse(localStorage.getItem(`whappy-profile-settings-${userKey}`) || "null") as Partial<ProfileSettings> | null;
      return saved ? { ...defaultSettings, ...saved } : defaultSettings;
    } catch {
      return defaultSettings;
    }
  });
  const preview = photoPreview || photoUrl;
  const previewStyle = useMemo(() => ({ transform: `scale(${zoom}) translate(${offsetX * 7}%, ${offsetY * 7}%)` }), [zoom, offsetX, offsetY]);

  function choosePhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) { notify("Choisissez une photo JPG, PNG ou WebP."); return; }
    if (file.size > 8 * 1024 * 1024) { notify("La photo doit peser moins de 8 Mo."); return; }
    setPhotoFile(file);
    setPhotoPreview(URL.createObjectURL(file));
    setZoom(1);
    setOffsetX(0);
    setOffsetY(0);
    setEditing(true);
  }

  function updateSetting(key: keyof ProfileSettings) {
    setSettings((current) => {
      const next = { ...current, [key]: !current[key] };
      try { localStorage.setItem(`whappy-profile-settings-${userKey}`, JSON.stringify(next)); } catch { /* stockage local facultatif */ }
      return next;
    });
  }

  async function save() {
    const cleanName = draftName.trim();
    if (cleanName.length < 2) { notify("Votre nom doit contenir au moins 2 caractères."); return; }
    setBusy(true);
    try {
      const cropped = photoFile ? await cropSquare(photoFile, zoom, offsetX, offsetY) : null;
      await onSave(cleanName, cropped);
      setPhotoFile(null);
      setPhotoPreview("");
      setEditing(false);
      notify("Profil mis à jour");
    } catch (error) {
      notify(error instanceof Error && error.message === "invalid-profile-photo" ? "La photo est invalide ou trop lourde." : "Le profil n’a pas pu être mis à jour.");
    } finally { setBusy(false); }
  }

  return <div className="profile-editor">
    <div className="profile-editor-actions"><button type="button" onClick={() => { setDraftName(name); setEditing((value) => !value); }}><span>✎</span><div><strong>Modifier le profil</strong><small>Nom et photo de profil</small></div><b>→</b></button><button type="button" onClick={() => setSettingsOpen((value) => !value)}><span>⚙</span><div><strong>Paramètres</strong><small>Confidentialité, notifications et médias</small></div><b>→</b></button></div>
    {editing && <section className="profile-edit-card"><header><div><small>IDENTITÉ WHAPPY</small><h3>Modifier votre profil</h3></div><button type="button" onClick={() => setEditing(false)} aria-label="Fermer">×</button></header><label className="profile-crop-picker"><div className="profile-crop-stage">{preview ? <img src={preview} alt="Aperçu du profil" style={previewStyle}/> : <span>{initials(draftName)}</span>}<i>ZONE DE RECADRAGE</i></div><strong>{photoPreview ? "Photo prête à ajuster" : "Choisir une nouvelle photo"}</strong><small>La photo sera recadrée au format carré.</small><input type="file" accept="image/jpeg,image/png,image/webp" onChange={choosePhoto}/></label>{photoPreview && <div className="profile-crop-controls"><label>Zoom <input type="range" min="1" max="3" step=".05" value={zoom} onChange={(event) => setZoom(Number(event.target.value))}/><b>{Math.round(zoom * 100)}%</b></label><label>Horizontal <input type="range" min="-1" max="1" step=".05" value={offsetX} onChange={(event) => setOffsetX(Number(event.target.value))}/></label><label>Vertical <input type="range" min="-1" max="1" step=".05" value={offsetY} onChange={(event) => setOffsetY(Number(event.target.value))}/></label></div>}<label className="profile-name-field">Nom affiché<input value={draftName} maxLength={80} onChange={(event) => setDraftName(event.target.value)} placeholder="Votre nom"/></label><button type="button" className="profile-save-button" onClick={() => void save()} disabled={busy}>{busy ? "Enregistrement…" : "Enregistrer les changements"}</button></section>}
    {settingsOpen && <section className="profile-settings-card"><header><div><small>PRÉFÉRENCES PERSONNELLES</small><h3>Paramètres de votre compte</h3></div><button type="button" onClick={() => setSettingsOpen(false)} aria-label="Fermer">×</button></header>{([ ["notifications", "Notifications", "Recevoir les alertes de messages et d’appels"], ["readReceipts", "Accusés de lecture", "Afficher les messages vus dans les conversations"], ["showOnline", "Présence en ligne", "Permettre à vos contacts de voir votre disponibilité"], ["autoDownload", "Téléchargement automatique", "Charger automatiquement les photos reçues"] ] as Array<[keyof ProfileSettings, string, string]>).map(([key, title, description]) => <button type="button" className="profile-setting-row" key={key} onClick={() => updateSetting(key)}><span><strong>{title}</strong><small>{description}</small></span><i className={settings[key] ? "on" : ""}>{settings[key] ? "ON" : "OFF"}</i></button>)}<p className="profile-settings-note">Ces préférences sont conservées sur cet appareil et s’appliquent à votre expérience WHAPPY.</p></section>}
  </div>;
}
