"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { defaultWepiSettings, saveWepiSettings, watchWepiSettings, type WepiSettings, type WepiTone } from "@/lib/whappy-wepi";

type WepiAssistantProps = {
  userId: string;
  userName: string;
  businessName?: string;
  cloud: boolean;
  notify: (text: string) => void;
};

export function WepiAssistant({ userId, userName, businessName = "", cloud, notify }: WepiAssistantProps) {
  const [settings, setSettings] = useState<WepiSettings>(() => defaultWepiSettings(userId, businessName));
  const [busy, setBusy] = useState(false);
  const notifyRef = useRef(notify);
  const storageReadyRef = useRef(!cloud);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    if (!cloud) {
      let active = true;
      queueMicrotask(() => {
        if (!active) return;
        try {
          const stored = JSON.parse(localStorage.getItem(`whappy-wepi-${userId}`) || "null") as WepiSettings | null;
          if (stored) setSettings({ ...defaultWepiSettings(userId, businessName), ...stored, ownerId: userId });
        } catch { /* La démo reste utilisable sans stockage local. */ }
      });
      return () => { active = false; };
    }
    return watchWepiSettings(userId, (stored) => {
      setSettings(stored ? { ...defaultWepiSettings(userId, businessName), ...stored } : defaultWepiSettings(userId, businessName));
    }, () => notifyRef.current("WEPI est momentanément indisponible"));
  }, [businessName, cloud, userId]);
  useEffect(() => {
    if (cloud || !storageReadyRef.current) return;
    try { localStorage.setItem(`whappy-wepi-${userId}`, JSON.stringify(settings)); } catch { /* Le mode local reste disponible en mémoire. */ }
  }, [cloud, settings, userId]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      const form = new FormData(event.currentTarget);
      const next: WepiSettings = {
        ...settings,
        ownerId: userId,
        assistantName: String(form.get("assistantName") || "WEPI").trim().slice(0, 60) || "WEPI",
        businessName: String(form.get("businessName") || "").trim().slice(0, 100),
        tone: String(form.get("tone") || "chaleureux") as WepiTone,
        welcomeMessage: String(form.get("welcomeMessage") || "").trim().slice(0, 240),
        instructions: String(form.get("instructions") || "").trim().slice(0, 600),
      };
      if (cloud) await saveWepiSettings(userId, next);
      setSettings(next);
      notify(next.enabled && next.autoReply ? "WEPI est activé : il répondra aux nouveaux messages" : next.enabled ? "WEPI est activé en mode validation" : "WEPI est désactivé");
    } catch { notify("Les réglages WEPI n’ont pas pu être enregistrés"); }
    finally { setBusy(false); }
  }

  async function toggle(key: "enabled" | "autoReply") {
    const nextValue = !settings[key];
    const nextSettings = { ...settings, [key]: nextValue };
    setSettings(nextSettings);
    if (!cloud) {
      notify(nextValue ? (key === "enabled" ? "WEPI est activé en mode local" : "WEPI répondra automatiquement") : (key === "enabled" ? "WEPI est désactivé" : "Réponse automatique WEPI en pause"));
      return;
    }
    try {
      await saveWepiSettings(userId, nextSettings);
      notify(nextValue ? (key === "enabled" ? "WEPI est activé" : "Réponse automatique WEPI activée") : (key === "enabled" ? "WEPI est désactivé" : "Réponse automatique WEPI en pause"));
    } catch {
      setSettings((current) => ({ ...current, [key]: !nextValue }));
      notify("Le réglage WEPI n’a pas pu être enregistré");
    }
  }

  return <section className="wepi-assistant">
    <header className="wepi-assistant-head"><div><span className="wepi-mark">W</span><div><small>WHAPPY INTELLIGENCE</small><h3>WEPI, l’IA de WAPI</h3></div></div><span className={`wepi-status ${settings.enabled ? "on" : "off"}`}><i />{settings.enabled ? "ACTIF" : "EN PAUSE"}</span></header>
    <p className="wepi-lead">WEPI comprend les demandes WAPI et répond selon vos informations. Il ne publie rien et ne prend pas d’engagement commercial sans votre accord.</p>
    <div className="wepi-switches"><button type="button" className={settings.enabled ? "active" : ""} onClick={() => void toggle("enabled")}><span>{settings.enabled ? "✓" : "○"}</span><div><strong>Activer WEPI</strong><small>Rendre l’assistant disponible dans Whappy</small></div></button><button type="button" className={settings.autoReply ? "active" : ""} onClick={() => void toggle("autoReply")} disabled={!settings.enabled}><span>{settings.autoReply ? "✓" : "○"}</span><div><strong>Réponse automatique</strong><small>Répondre aux nouveaux messages entrants</small></div></button></div>
    <form onSubmit={save} className="wepi-form">
      <div className="wepi-form-grid"><label>Nom de l’assistant<input name="assistantName" defaultValue={settings.assistantName} key={`${settings.ownerId}-${settings.assistantName}`} maxLength={60}/></label><label>Nom du business<input name="businessName" defaultValue={settings.businessName || businessName} key={`${settings.ownerId}-${settings.businessName}-${businessName}`} maxLength={100} placeholder="Ex. Mokabi Store"/></label><label>Ton<select name="tone" defaultValue={settings.tone} key={`${settings.ownerId}-${settings.tone}`}><option value="chaleureux">Chaleureux</option><option value="expert">Expert</option><option value="direct">Direct</option></select></label></div>
      <label>Message d’accueil<textarea name="welcomeMessage" defaultValue={settings.welcomeMessage} key={`${settings.ownerId}-${settings.welcomeMessage}`} maxLength={240} rows={2}/></label>
      <label>Consigne de réponse<textarea name="instructions" defaultValue={settings.instructions} key={`${settings.ownerId}-${settings.instructions}`} maxLength={600} rows={3}/></label>
      <footer><span>Connecté au compte de {userName}. Les réponses sont identifiées comme générées par l’IA WAPI.</span><button disabled={busy}>{busy ? "Enregistrement…" : "Enregistrer les réglages WEPI ↗"}</button></footer>
    </form>
  </section>;
}
