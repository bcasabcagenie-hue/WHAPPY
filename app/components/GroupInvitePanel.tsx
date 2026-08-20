"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import QRCode from "qrcode";

export function GroupInvitePanel({ groupName, link, onClose }: { groupName: string; link: string; onClose: () => void }) {
  const [qr, setQr] = useState<{ link: string; value: string; error: string }>({ link: "", value: "", error: "" });
  const [status, setStatus] = useState("");

  useEffect(() => {
    let active = true;
    void QRCode.toDataURL(link, {
      errorCorrectionLevel: "M",
      margin: 1,
      width: 480,
      color: { dark: "#1C1C58", light: "#FFFFFF" },
    }).then((value) => { if (active) setQr({ link, value, error: "" }); }).catch(() => { if (active) setQr({ link, value: "", error: "Le code QR n’a pas pu être généré. Utilisez le lien ci-dessous." }); });
    return () => { active = false; };
  }, [link]);

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) { if (event.key === "Escape") onClose(); }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  async function copyText(value: string) {
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(value);
      else {
        const field = document.createElement("textarea");
        field.value = value;
        field.style.position = "fixed";
        field.style.opacity = "0";
        document.body.appendChild(field);
        field.select();
        const copied = document.execCommand("copy");
        field.remove();
        if (!copied) throw new Error("copy-unavailable");
      }
      setStatus("✓ Lien copié. Vous pouvez maintenant le partager.");
      return true;
    } catch {
      setStatus("Impossible de copier automatiquement. Sélectionnez le lien puis copiez-le manuellement.");
      return false;
    }
  }

  async function share() {
    const text = "Rejoignez le groupe « " + groupName + " » sur WHAPPY. Votre demande sera validée par un administrateur.";
    try {
      if (navigator.share) await navigator.share({ title: "WHAPPY · " + groupName, text, url: link });
      else await copyText(text + "\n" + link);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setStatus("Le partage n’a pas pu s’ouvrir. Copiez le lien manuellement.");
    }
  }

  async function copy() {
    await copyText(link);
  }

  return <div className="whappy-invite-layer" role="dialog" aria-modal="true" aria-label={"Inviter dans " + groupName}>
    <button className="whappy-invite-dismiss" onClick={onClose} aria-label="Fermer l’invitation" />
    <section className="whappy-invite-panel">
      <button className="whappy-invite-close" onClick={onClose} aria-label="Fermer">×</button>
      <span>INVITATION DE GROUPE</span>
      <h2>{groupName}</h2>
      <p>Partagez ce lien ou ce code QR. Les nouveaux membres envoient une demande, puis un administrateur la valide.</p>
      <div className="whappy-qr">{qr.link === link && qr.value ? <Image src={qr.value} alt={"Code QR pour rejoindre " + groupName} width={176} height={176} unoptimized /> : <i>{qr.link === link && qr.error ? qr.error : "Préparation du QR…"}</i>}</div>
      <label>Lien privé d’invitation<input readOnly value={link} onFocus={(event) => event.currentTarget.select()} /></label>
      <div className="whappy-invite-actions">
        <button onClick={() => void copy()}>Copier le lien</button>
        <button className="primary" onClick={() => void share()}>Partager</button>
      </div>
      {status && <small role="status">{status}</small>}
    </section>
  </div>;
}
