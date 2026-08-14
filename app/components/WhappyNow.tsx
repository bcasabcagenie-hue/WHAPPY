"use client";

import { useEffect, useMemo, useState } from "react";

type ActivitySpace = "inbox" | "live" | "market" | "twin" | "business";

type WhappyNowProps = {
  unread: number;
  listingCount: number;
  orderCount: number;
  cloud: boolean;
  onNavigate: (space: ActivitySpace) => void;
};

export function WhappyNow({ unread, listingCount, orderCount, cloud, onNavigate }: WhappyNowProps) {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const activities = useMemo(() => [
    {
      icon: "◫",
      eyebrow: "MESSAGES PRIORITAIRES",
      title: `${unread || 1} conversation${unread > 1 ? "s" : ""} à regarder maintenant`,
      detail: "Amina et Junior attendent votre réponse",
      action: "Répondre",
      space: "inbox" as const,
      tone: "message",
    },
    {
      icon: "●",
      eyebrow: "EN DIRECT MAINTENANT",
      title: "3 créateurs présentent leurs nouveautés",
      detail: "Mokabi Studio rassemble déjà 2,8 k personnes",
      action: "Regarder",
      space: "live" as const,
      tone: "live",
    },
    {
      icon: "◇",
      eyebrow: "WHAPPY MARKET",
      title: `${listingCount} opportunités sélectionnées pour vous`,
      detail: orderCount ? `${orderCount} commande${orderCount > 1 ? "s" : ""} à suivre` : "Achetez, vendez ou proposez un échange",
      action: "Explorer",
      space: "market" as const,
      tone: "market",
    },
    {
      icon: "◎",
      eyebrow: "MON DOUBLE",
      title: "Votre prochain vendeur peut travailler sans pause",
      detail: "Préparez une présentation vidéo et automatisez vos réponses",
      action: "Créer",
      space: "twin" as const,
      tone: "double",
    },
    {
      icon: "✦",
      eyebrow: cloud ? "WHAPPY SYNCHRONISÉ" : "ESPACE DE DÉMONSTRATION",
      title: cloud ? "Votre activité est à jour sur le cloud" : "Testez librement toutes les possibilités",
      detail: cloud ? "Messages, groupes et ventes sont connectés" : "Vos essais restent sur cet appareil",
      action: "Piloter",
      space: "business" as const,
      tone: "system",
    },
  ], [cloud, listingCount, orderCount, unread]);

  useEffect(() => {
    if (paused) return;
    const timer = window.setInterval(() => {
      setActive((current) => (current + 1) % activities.length);
    }, 4800);
    return () => window.clearInterval(timer);
  }, [activities.length, paused]);

  const item = activities[active];

  return (
    <section
      className={`whappy-now ${item.tone}`}
      aria-label="Activité Whappy en temps réel"
      aria-live="polite"
      onPointerMove={(event) => {
        const bounds = event.currentTarget.getBoundingClientRect();
        event.currentTarget.style.setProperty("--now-x", `${event.clientX - bounds.left}px`);
        event.currentTarget.style.setProperty("--now-y", `${event.clientY - bounds.top}px`);
      }}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false);
      }}
    >
      <div className="now-label"><i /><span>MAINTENANT</span></div>
      <button className="now-content" onClick={() => onNavigate(item.space)}>
        <span className="now-icon">{item.icon}</span>
        <span className="now-copy" key={active}>
          <small>{item.eyebrow}</small>
          <strong>{item.title}</strong>
          <em>{item.detail}</em>
        </span>
        <b>{item.action} <span>→</span></b>
      </button>
      <div className="now-controls" aria-label="Choisir une activité">
        {activities.map((activity, index) => (
          <button
            key={activity.eyebrow}
            className={index === active ? "active" : ""}
            onClick={() => setActive(index)}
            aria-label={`Afficher : ${activity.eyebrow}`}
            aria-current={index === active ? "true" : undefined}
          />
        ))}
      </div>
      <span className="now-progress" aria-hidden="true"><i key={active} /></span>
    </section>
  );
}
