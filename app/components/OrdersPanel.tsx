"use client";

import { useMemo, useState } from "react";
import type { CloudOrder, OrderStatus } from "@/lib/whappy-data";

const labels: Record<OrderStatus, string> = {
  pending: "À confirmer",
  confirmed: "Confirmée",
  ready: "Prête",
  completed: "Terminée",
  cancelled: "Annulée",
};

const steps: OrderStatus[] = ["pending", "confirmed", "ready", "completed"];

function money(value: number) {
  return value ? `${new Intl.NumberFormat("fr-FR").format(value)} FCFA` : "À négocier";
}

export function OrdersPanel({ orders, cloud, onClose, onExplore, onContact, onCancel, notify }: {
  orders: CloudOrder[];
  cloud: boolean;
  onClose: () => void;
  onExplore: () => void;
  onContact: (seller: string) => void;
  onCancel: (order: CloudOrder) => Promise<boolean>;
  notify: (text: string) => void;
}) {
  const [selected, setSelected] = useState(orders[0]?.id || "");
  const [filter, setFilter] = useState<"all" | "active" | "completed">("all");
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [busy, setBusy] = useState(false);
  const visible = orders.filter((order) => filter === "all" || (filter === "active" ? !["completed", "cancelled"].includes(order.status) : order.status === "completed"));
  const current = orders.find((order) => order.id === selected) || visible[0] || orders[0];
  const metrics = useMemo(() => ({
    active: orders.filter((order) => !["completed", "cancelled"].includes(order.status)).length,
    completed: orders.filter((order) => order.status === "completed").length,
    value: orders.filter((order) => order.status !== "cancelled").reduce((sum, order) => sum + order.total, 0),
  }), [orders]);

  function downloadReceipt(order: CloudOrder) {
    const lines = [
      "WHAPPY — RÉCAPITULATIF DE COMMANDE",
      `Référence : ${order.reference}`,
      `Statut : ${labels[order.status]}`,
      `Client : ${order.customerName}`,
      `Téléphone : ${order.phone}`,
      `Remise : ${order.address}`,
      `Règlement : ${order.paymentMethod === "mobile" ? "Mobile Money après confirmation" : "Paiement à la livraison"}`,
      "",
      ...order.items.map((item) => `${item.quantity} × ${item.title} — ${item.price} — ${item.seller}`),
      "",
      `TOTAL INDICATIF : ${money(order.total)}`,
      "Aucun débit n’est confirmé par ce document.",
    ];
    const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `${order.reference.toLowerCase()}-recapitulatif.txt`;
    link.click();
    URL.revokeObjectURL(url);
    notify("Le récapitulatif a été téléchargé");
  }

  async function cancel(order: CloudOrder) {
    setBusy(true);
    const cancelled = await onCancel(order);
    setBusy(false);
    if (cancelled) setConfirmCancel(false);
  }

  return <div className="orders-layer">
    <button className="orders-dismiss" onClick={onClose} aria-label="Fermer les commandes" />
    <section className="orders-panel" role="dialog" aria-modal="true" aria-label="Mes commandes">
      <header className="orders-head"><div><span>WHAPPY MARKET</span><h2>Mes commandes</h2><p>Suivez chaque achat de la demande à la remise.</p></div><div className="orders-head-metrics"><span><b>{metrics.active}</b> en cours</span><span><b>{metrics.completed}</b> terminée{metrics.completed > 1 ? "s" : ""}</span><span><b>{money(metrics.value)}</b> valeur suivie</span></div><button onClick={onClose} aria-label="Fermer">×</button></header>
      <div className="orders-body">
        <aside className="orders-list"><div className="orders-tabs">{(["all", "active", "completed"] as const).map((value) => <button key={value} className={filter === value ? "active" : ""} onClick={() => setFilter(value)}>{value === "all" ? "Toutes" : value === "active" ? "En cours" : "Terminées"}</button>)}</div><div className="orders-sync"><i className={cloud ? "cloud" : "local"} />{cloud ? "Synchronisation active" : "Démonstration locale"}</div>{visible.map((order) => <button className={`order-row ${current?.id === order.id ? "active" : ""}`} key={order.id} onClick={() => { setSelected(order.id); setConfirmCancel(false); }}><span>{order.items[0]?.title.split(/\s+/).slice(0,2).map((word) => word[0]).join("").toUpperCase() || "WH"}</span><div><small>{order.reference}</small><strong>{order.items[0]?.title}{order.items.length > 1 ? ` +${order.items.length - 1}` : ""}</strong><p>{money(order.total)} · {order.items.reduce((sum, item) => sum + item.quantity, 0)} article{order.items.length > 1 ? "s" : ""}</p></div><em className={order.status}>{labels[order.status]}</em></button>)}{!visible.length && <div className="orders-empty-list"><span>◇</span><strong>Aucune commande</strong><small>Vos achats apparaîtront ici.</small></div>}</aside>
        <main className="order-detail">{current ? <><div className="order-detail-title"><div><small>COMMANDE {current.reference}</small><h3>{labels[current.status]}</h3><p>{current.createdAt?.toDate?.()?.toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" }) || "Créée aujourd’hui"}</p></div><span className={current.status}>{labels[current.status]}</span></div>{current.status !== "cancelled" ? <div className="order-timeline">{steps.map((step, index) => { const activeIndex = steps.indexOf(current.status); return <div className={index <= activeIndex ? "done" : ""} key={step}><span>{index < activeIndex ? "✓" : index + 1}</span><small>{labels[step]}</small>{index < steps.length - 1 && <i />}</div>; })}</div> : <div className="order-cancelled">Cette commande a été annulée. Aucun paiement n’a été déclenché.</div>}<section className="order-products"><header><strong>Articles</strong><small>{current.items.reduce((sum, item) => sum + item.quantity, 0)} au total</small></header>{current.items.map((item) => <article key={`${current.id}-${item.listingId}`}><span>{item.quantity}</span><div><strong>{item.title}</strong><small>Vendu par {item.seller}</small></div><b>{item.price}</b><button onClick={() => onContact(item.seller)}>Contacter</button></article>)}<footer><span>Total indicatif</span><strong>{money(current.total)}</strong></footer></section><section className="order-delivery"><div><span>⌖</span><p><small>POINT DE REMISE</small><strong>{current.address}</strong></p></div><div><span>◇</span><p><small>RÈGLEMENT</small><strong>{current.paymentMethod === "mobile" ? "Mobile Money après confirmation" : "Paiement à la livraison"}</strong></p></div><div><span>☎</span><p><small>CONTACT</small><strong>{current.phone}</strong></p></div></section><div className="order-actions"><button onClick={() => downloadReceipt(current)}>⇩ Télécharger le récapitulatif</button>{current.status === "pending" && (confirmCancel ? <span><button onClick={() => setConfirmCancel(false)}>Conserver</button><button className="danger" disabled={busy} onClick={() => cancel(current)}>{busy ? "Annulation…" : "Confirmer l’annulation"}</button></span> : <button className="cancel" onClick={() => setConfirmCancel(true)}>Annuler la commande</button>)}</div><p className="order-safety">◆ Une commande reste indicative jusqu’à confirmation du vendeur. Ne transmettez jamais un code Mobile Money dans la conversation.</p></> : <div className="order-empty"><span>◇</span><h3>Aucune commande pour le moment</h3><p>Ajoutez un produit au panier puis préparez votre première commande.</p><button onClick={onExplore}>Explorer le Marketplace</button></div>}</main>
      </div>
    </section>
  </div>;
}
