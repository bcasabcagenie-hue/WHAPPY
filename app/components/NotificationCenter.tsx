"use client";

import { useEffect, useState } from "react";

type NotificationCenterProps = {
  unread: number;
  onNotify: (text: string) => void;
};

export function NotificationCenter({ unread, onNotify }: NotificationCenterProps) {
  const [permission, setPermission] = useState<NotificationPermission | "unsupported">(() => {
    if (typeof window === "undefined") return "default";
    return "Notification" in window ? window.Notification.permission : "unsupported";
  });

  useEffect(() => {
    if (typeof window === "undefined" || !("Notification" in window)) return;
    if ("serviceWorker" in navigator) void navigator.serviceWorker.register("/whappy-notifications.js").catch(() => undefined);
  }, []);

  useEffect(() => {
    if (permission !== "granted" || unread < 1 || document.visibilityState !== "hidden") return;
    const notice = new Notification("Whappy · messages en attente", {
      body: `${unread} conversation${unread > 1 ? "s" : ""} attend${unread > 1 ? "ent" : ""} votre réponse.`,
      icon: "/whappy-app-icon.png",
      tag: "whappy-unread-messages",
    });
    notice.onclick = () => window.focus();
    return () => notice.close();
  }, [permission, unread]);

  async function enable() {
    if (permission === "unsupported") {
      onNotify("Les notifications système ne sont pas disponibles dans ce navigateur");
      return;
    }
    const result = await Notification.requestPermission();
    setPermission(result);
    onNotify(result === "granted" ? "Notifications Whappy activées, même lorsque l’onglet est en arrière-plan" : "Autorisation des notifications refusée");
  }

  return <button className={`notification-center ${permission === "granted" ? "enabled" : ""}`} onClick={() => void enable()} aria-label={permission === "granted" ? "Notifications Whappy activées" : "Activer les notifications Whappy"} title={permission === "granted" ? "Notifications système activées" : "Activer les notifications système"}>
    <span>♢</span><small>{permission === "granted" ? "Alertes actives" : "Activer les alertes"}</small>{unread > 0 && <b>{unread}</b>}
  </button>;
}
