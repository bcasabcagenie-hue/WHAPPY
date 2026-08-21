"use client";

import { FormEvent, useMemo, useState } from "react";

export type SellerListing = {
  id: string | number;
  title: string;
  price: string;
  place: string;
  status?: "active" | "reserved" | "sold";
  trust: number;
};

type ListingChanges = Pick<SellerListing, "title" | "price" | "place" | "status">;

const statusLabels = { active: "En ligne", reserved: "Réservée", sold: "Vendue" } as const;

export function SellerDashboard({ items, cloud, onClose, onCreate, onUpdate, onDelete, notify }: {
  items: SellerListing[];
  cloud: boolean;
  onClose: () => void;
  onCreate: () => void;
  onUpdate: (id: SellerListing["id"], changes: ListingChanges) => Promise<boolean>;
  onDelete: (id: SellerListing["id"]) => Promise<boolean>;
  notify: (text: string) => void;
}) {
  const [filter, setFilter] = useState<"all" | NonNullable<SellerListing["status"]>>("all");
  const [editing, setEditing] = useState<SellerListing["id"] | null>(null);
  const [working, setWorking] = useState<SellerListing["id"] | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<SellerListing["id"] | null>(null);
  const filtered = filter === "all" ? items : items.filter((item) => (item.status || "active") === filter);
  const metrics = useMemo(() => {
    const active = items.filter((item) => (item.status || "active") === "active").length;
    const sold = items.filter((item) => item.status === "sold").length;
    return { active, sold, views: items.length * 37 + active * 11, messages: items.length * 4 + sold * 3 };
  }, [items]);

  async function save(event: FormEvent<HTMLFormElement>, item: SellerListing) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setWorking(item.id);
    const saved = await onUpdate(item.id, {
      title: String(form.get("title") || item.title).trim(),
      price: String(form.get("price") || item.price).trim(),
      place: String(form.get("place") || item.place).trim(),
      status: String(form.get("status") || item.status || "active") as NonNullable<SellerListing["status"]>,
    });
    setWorking(null);
    if (saved) setEditing(null);
  }

  async function changeStatus(item: SellerListing, status: NonNullable<SellerListing["status"]>) {
    setWorking(item.id);
    await onUpdate(item.id, { title: item.title, price: item.price, place: item.place, status });
    setWorking(null);
  }

  async function deleteItem(item: SellerListing) {
    setWorking(item.id);
    const removed = await onDelete(item.id);
    setWorking(null);
    if (removed) setConfirmDelete(null);
  }

  function exportShop() {
    const rows = [["Annonce", "Prix", "Lieu", "Statut", "Confiance"], ...items.map((item) => [item.title, item.price, item.place, statusLabels[item.status || "active"], `${item.trust}%`])];
    const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\n");
    const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "boutique-whappy.csv";
    link.click();
    URL.revokeObjectURL(url);
    notify("Le rapport de votre boutique a été exporté");
  }

  return <div className="seller-layer">
    <button className="seller-dismiss" onClick={onClose} aria-label="Fermer la boutique" />
    <section className="seller-dashboard" role="dialog" aria-modal="true" aria-label="Tableau de bord vendeur">
      <header className="seller-head"><div><span>WHAPPY BUSINESS</span><h2>Ma boutique</h2><p>Publiez, actualisez et suivez vos annonces depuis un seul espace.</p></div><div className="seller-head-actions"><button onClick={exportShop} disabled={!items.length}>⇩ Exporter</button><button className="seller-create" onClick={onCreate}>＋ Nouvelle annonce</button><button className="seller-close" onClick={onClose} aria-label="Fermer">×</button></div></header>
      <div className="seller-sync"><i className={cloud ? "cloud" : "local"} /><div><strong>{cloud ? "Boutique synchronisée" : "Mode démonstration locale"}</strong><small>{cloud ? "Les changements sont enregistrés dans votre compte." : "Connectez votre numéro pour retrouver vos annonces sur tous vos appareils."}</small></div><span>{cloud ? "CLOUD ACTIF" : "LOCAL"}</span></div>
      <section className="seller-metrics">
        <article><span>ANNONCES ACTIVES</span><strong>{metrics.active}</strong><small>Visibles dans Marketplace</small></article>
        <article><span>VUES ESTIMÉES</span><strong>{metrics.views}</strong><small>Depuis la publication</small></article>
        <article><span>MESSAGES</span><strong>{metrics.messages}</strong><small>Conversations commerciales</small></article>
        <article><span>VENTES FINALISÉES</span><strong>{metrics.sold}</strong><small>Annonces marquées vendues</small></article>
      </section>
      <div className="seller-toolbar"><div>{(["all", "active", "reserved", "sold"] as const).map((value) => <button key={value} className={filter === value ? "active" : ""} onClick={() => setFilter(value)}>{value === "all" ? `Toutes (${items.length})` : statusLabels[value]}</button>)}</div><small>{filtered.length} annonce{filtered.length > 1 ? "s" : ""}</small></div>
      <div className="seller-list">
        {!filtered.length && <div className="seller-empty"><span>◇</span><h3>{items.length ? "Aucune annonce dans ce statut" : "Votre boutique est prête"}</h3><p>{items.length ? "Choisissez un autre filtre pour retrouver vos annonces." : "Publiez votre premier produit ou service et commencez à recevoir des messages."}</p>{!items.length && <button onClick={onCreate}>＋ Publier ma première annonce</button>}</div>}
        {filtered.map((item) => <article className="seller-item" key={item.id}>
          <div className="seller-item-mark">{item.title.split(/\s+/).slice(0, 2).map((word) => word[0]).join("").toUpperCase()}<i className={item.status || "active"} /></div>
          {editing === item.id ? <form onSubmit={(event) => save(event, item)} className="seller-edit"><label>Titre<input name="title" defaultValue={item.title} required /></label><div><label>Prix<input name="price" defaultValue={item.price} required /></label><label>Lieu<input name="place" defaultValue={item.place} required /></label><label>Statut<select name="status" defaultValue={item.status || "active"}><option value="active">En ligne</option><option value="reserved">Réservée</option><option value="sold">Vendue</option></select></label></div><footer><button type="button" onClick={() => setEditing(null)}>Annuler</button><button className="primary" disabled={working === item.id}>{working === item.id ? "Enregistrement…" : "Enregistrer"}</button></footer></form> : <>
            <div className="seller-item-copy"><span className={`seller-status ${item.status || "active"}`}>{statusLabels[item.status || "active"]}</span><h3>{item.title}</h3><strong>{item.price}</strong><p>⌖ {item.place} · Confiance {item.trust}%</p></div>
            <div className="seller-item-actions"><button onClick={() => setEditing(item.id)}>✎ Modifier</button><select aria-label={`Modifier le statut de ${item.title}`} value={item.status || "active"} disabled={working === item.id} onChange={(event) => changeStatus(item, event.target.value as NonNullable<SellerListing["status"]>)}><option value="active">En ligne</option><option value="reserved">Réservée</option><option value="sold">Vendue</option></select>{confirmDelete === item.id ? <span><button onClick={() => setConfirmDelete(null)}>Annuler</button><button className="danger" disabled={working === item.id} onClick={() => deleteItem(item)}>Confirmer</button></span> : <button className="delete" onClick={() => setConfirmDelete(item.id)}>Supprimer</button>}</div>
          </>}
        </article>)}
      </div>
    </section>
  </div>;
}
