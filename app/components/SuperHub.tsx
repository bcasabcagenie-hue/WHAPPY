"use client";

import { FormEvent, useMemo, useState } from "react";
import type { CloudGroup } from "@/lib/whappy-data";

type Destination = "live" | "market" | "barter" | "seek" | "twin" | "orbit";

const initialContacts = [
  { name: "Amina M.", mark: "AM", note: "Acheteuse fiable · Poto-Poto", online: true },
  { name: "Junior K.", mark: "JK", note: "Vendeur vérifié · Centre-ville", online: true },
  { name: "Mokabi Store", mark: "MS", note: "Boutique Pro · Mode", online: false },
  { name: "Nadia M.", mark: "NM", note: "Photographe · Moungali", online: true },
  { name: "Maison Noki", mark: "MN", note: "Maison & décoration · Bacongo", online: false },
];

const initialGroups = [
  { id: "design-crew", name: "Design Crew", mark: "DC", note: "12 membres · 3 nouveaux messages" },
  { id: "commercants-brazza", name: "Commerçants Brazza", mark: "CB", note: "48 membres · Marketplace" },
];

export function ContactsSpace({ search, cloud, cloudGroups, onCreateGroup, onMessage, onCall, notify }: {
  search: string;
  cloud: boolean;
  cloudGroups: CloudGroup[];
  onCreateGroup: (name: string, description: string, members: string[]) => Promise<boolean>;
  onMessage: (name: string) => void;
  onCall: (name: string) => void;
  notify: (text: string) => void;
}) {
  const [tab, setTab] = useState<"contacts" | "groups" | "business">("contacts");
  const [contactList, setContactList] = useState(initialContacts);
  const [creator, setCreator] = useState<"contact" | "group" | null>(null);
  const [busy, setBusy] = useState(false);
  const visible = useMemo(() => contactList.filter((contact) => `${contact.name} ${contact.note}`.toLowerCase().includes(search.toLowerCase())), [contactList, search]);
  const groups = useMemo(() => {
    const synced = cloudGroups.map((group) => ({ id: group.id, name: group.name, mark: group.mark, note: `${group.memberNames.length + 1} membre${group.memberNames.length ? "s" : ""} · ${group.description || "Groupe Whappy"}` }));
    return [...synced, ...initialGroups.filter((sample) => !synced.some((group) => group.name === sample.name))];
  }, [cloudGroups]);

  async function createEntry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") || "").trim();
    if (!name) return;
    if (creator === "group") {
      setBusy(true);
      const created = await onCreateGroup(name, String(form.get("description") || ""), String(form.get("members") || "").split(","));
      setBusy(false);
      if (!created) return;
      setTab("groups");
    } else {
      const phone = String(form.get("phone") || "Numéro à confirmer").trim();
      const mark = name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
      setContactList((current) => [{ name, mark, note: `${phone} · Ajouté maintenant`, online: false }, ...current]);
      setTab("contacts");
      notify(`${name} a été ajouté au carnet de démonstration`);
    }
    setCreator(null);
  }

  return <div className="contact-space">
    <section className="contact-summary"><div><span>CARNET WHAPPY</span><h2>Les bonnes personnes,<br/><em>au bon moment.</em></h2><p>Contacts, groupes et professionnels réunis autour de votre numéro.</p></div><div className="contact-stats"><span><b>{128 + contactList.length - initialContacts.length}</b> contacts</span><span><b>{9 + cloudGroups.length}</b> groupes</span><span><b>24</b> pros vérifiés</span></div></section>
    <div className="contact-layout">
      <aside className="contact-tools"><button onClick={() => setCreator("contact")}><span>＋</span><div><strong>Nouveau contact</strong><small>Ajouter avec son numéro</small></div></button><button onClick={() => setCreator("group")}><span>◎</span><div><strong>Nouveau groupe</strong><small>Famille, projet ou communauté</small></div></button><button onClick={() => setCreator("contact")}><span>⌗</span><div><strong>Saisir un Whappy ID</strong><small>Ajouter avec un numéro vérifié</small></div></button><button onClick={() => setTab("business")}><span>✓</span><div><strong>Annuaire vérifié</strong><small>Trouver un professionnel fiable</small></div></button></aside>
      <main className="contact-book"><header><div>{(["contacts", "groups", "business"] as const).map((item) => <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>{item === "contacts" ? "Contacts" : item === "groups" ? "Groupes" : "Professionnels"}</button>)}</div><span className={`contact-cloud ${cloud ? "active" : ""}`}><i />{cloud ? "Compte synchronisé" : "Démonstration locale"}</span></header>
        {tab === "groups" ? <div className="group-grid">{groups.map((group) => <button key={group.id} onClick={() => onMessage(group.name)}><span>{group.mark}</span><strong>{group.name}</strong><small>{group.note}</small></button>)}<button onClick={() => setCreator("group")}><span>＋</span><strong>Créer un groupe</strong><small>Jusqu’à 1 000 membres</small></button></div> : <div className="contact-rows">{visible.filter((contact) => tab === "contacts" || /Store|Maison/.test(contact.name)).map((contact) => <article key={contact.name}><span>{contact.mark}<i className={contact.online ? "online" : ""}/></span><div><strong>{contact.name}{tab === "business" && <b>✓</b>}</strong><small>{contact.note}</small></div><button onClick={() => onCall(contact.name)} aria-label={`Appeler ${contact.name}`}>☎</button><button className="contact-message" onClick={() => onMessage(contact.name)}>Message</button></article>)}{!visible.length && <p className="contact-empty">Aucun contact ne correspond à cette recherche.</p>}</div>}
      </main>
    </div>
    {creator && <div className="contact-create-layer"><button className="contact-create-dismiss" onClick={() => setCreator(null)} aria-label="Fermer"/><form onSubmit={createEntry}><header><div><small>CARNET WHAPPY</small><h3>{creator === "group" ? "Créer un groupe" : "Ajouter un contact"}</h3></div><button type="button" onClick={() => setCreator(null)}>×</button></header><label>{creator === "group" ? "Nom du groupe" : "Nom complet"}<input name="name" required placeholder={creator === "group" ? "Ex. Équipe projet" : "Ex. Grâce M."}/></label>{creator === "contact" ? <label>Numéro ou Whappy ID<input name="phone" required inputMode="tel" placeholder="+242 06…"/></label> : <><label>Description<input name="description" placeholder="Ex. Coordination du projet"/></label><label>Membres à inviter<input name="members" placeholder="Amina, Junior, Nadia…"/></label></>}<p>{creator === "group" ? cloud ? "Le groupe sera synchronisé avec votre compte Whappy." : "Le groupe sera créé dans cette démonstration pour la session." : "Le contact sera ajouté au carnet de démonstration pour cette session."}</p><button className="contact-create-submit" disabled={busy}>{busy ? "Création…" : creator === "group" ? "Créer le groupe →" : "Ajouter le contact →"}</button></form></div>}
  </div>;
}

const services: Array<{ icon: string; title: string; note: string; destination?: Destination; accent?: boolean }> = [
  { icon: "◉", title: "Directs", note: "Vendre et regarder en direct", destination: "live", accent: true },
  { icon: "◇", title: "Marketplace", note: "Acheter près de chez vous", destination: "market" },
  { icon: "⇄", title: "Troc", note: "Échanger ce que vous avez", destination: "barter" },
  { icon: "⌖", title: "Je cherche", note: "La communauté trouve pour vous", destination: "seek" },
  { icon: "◎", title: "Mon Double", note: "Votre vendeur vidéo autorisé", destination: "twin", accent: true },
  { icon: "▦", title: "Moments", note: "Voir les actualités locales", destination: "orbit" },
];

export function SuperHub({ go, orderCount, onOrders, notify }: { go: (destination: Destination) => void; orderCount: number; onOrders: () => void; notify: (text: string) => void }) {
  const [walletOpen, setWalletOpen] = useState(false);
  return <div className="super-hub"><section className="hub-hero"><div><span>WHAPPY SERVICES</span><h2>Un seul numéro.<br/><em>Toute votre vie numérique.</em></h2><p>Parlez, payez, vendez, trouvez et développez vos activités sans quitter Whappy.</p></div><button className="wallet-card" onClick={() => setWalletOpen(true)}><span>W</span><div><small>WHAPPY PAY</small><strong>Portefeuille sécurisé</strong><p>Mobile Money · Carte · QR</p></div><b>→</b></button></section><section className="hub-body"><div className="hub-shortcuts"><button onClick={() => setWalletOpen(true)}><span>⌁</span><strong>Payer</strong><small>Voir les connexions possibles</small></button><button onClick={() => notify("Votre QR personnel sera disponible après connexion d’un partenaire de paiement")}><span>⌗</span><strong>Recevoir</strong><small>Préparer mon QR</small></button><button onClick={onOrders}><span>▤</span><strong>Commandes</strong><small>{orderCount ? `${orderCount} commande${orderCount > 1 ? "s" : ""}` : "Suivre mes achats"}</small></button><button onClick={() => notify("Les avantages seront activés avec les boutiques partenaires")}><span>％</span><strong>Avantages</strong><small>Coupons et fidélité</small></button></div><header className="hub-section-title"><div><small>MINI-SERVICES WHAPPY</small><h3>Tout ce dont vous avez besoin</h3></div></header><div className="service-grid">{services.map((service) => <button className={service.accent ? "accent" : ""} key={service.title} onClick={() => service.destination && go(service.destination)}><span>{service.icon}</span><div><strong>{service.title}</strong><small>{service.note}</small></div><b>→</b></button>)}</div><header className="hub-section-title"><div><small>À PROXIMITÉ</small><h3>Services utiles maintenant</h3></div></header><div className="local-services"><button onClick={() => notify("Le partenaire transport n’est pas encore connecté")}><span>⌖</span><strong>Transport</strong><small>Connexion partenaire requise</small></button><button onClick={() => notify("Le partenaire livraison n’est pas encore connecté")}><span>→</span><strong>Livraison</strong><small>Connexion partenaire requise</small></button><button onClick={() => notify("L’annuaire santé vérifié est en préparation")}><span>＋</span><strong>Santé</strong><small>Annuaire vérifié à connecter</small></button></div></section>{walletOpen && <div className="wallet-layer" role="dialog" aria-modal="true" aria-label="Whappy Pay"><button className="wallet-dismiss" onClick={() => setWalletOpen(false)} aria-label="Fermer"/><section className="wallet-panel"><header><div><span>WHAPPY PAY</span><h3>Votre argent, simplement.</h3><p>Le portefeuille est préparé pour connecter Mobile Money et les cartes.</p></div><button onClick={() => setWalletOpen(false)}>×</button></header><div className="wallet-balance"><small>SOLDE DISPONIBLE</small><strong>0 <span>FCFA</span></strong><p>Aucun moyen de paiement connecté</p></div><div className="wallet-actions"><button onClick={() => notify("Connexion Mobile Money : service opérateur requis")}><span>＋</span><strong>Connecter Mobile Money</strong><small>MTN · Airtel · autres opérateurs</small></button><button onClick={() => notify("Connexion bancaire : partenaire de paiement requis")}><span>◇</span><strong>Ajouter une carte</strong><small>Visa ou Mastercard</small></button><button onClick={() => notify("Le scanner sera activé avec le partenaire de paiement")}><span>⌗</span><strong>Scanner pour payer</strong><small>QR marchand Whappy</small></button></div><p className="wallet-safety">◆ Aucun débit réel n’est effectué tant qu’un partenaire de paiement n’est pas connecté et validé.</p></section></div>}</div>;
}
