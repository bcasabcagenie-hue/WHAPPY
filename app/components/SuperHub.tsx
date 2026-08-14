"use client";
/* eslint-disable @next/next/no-img-element -- le QR est une image data: générée localement */

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import QRCode from "qrcode";
import { createGroupActivity, ensureGroupInvite, sendGroupMessage, watchGroupActivities, watchGroupMessages, type CloudGroup, type CloudGroupActivity, type CloudGroupMessage } from "@/lib/whappy-data";
import { GroupActivities, type ActivityDraft } from "@/app/components/GroupActivities";
import { GroupInvitePanel } from "@/app/components/GroupInvitePanel";
import { GroupJoinRequests } from "@/app/components/GroupJoinRequests";

type Destination = "live" | "market" | "barter" | "seek" | "twin" | "orbit";

const initialContacts = [
  { name: "Amina M.", mark: "AM", note: "Acheteuse fiable · Poto-Poto", online: true },
  { name: "Junior K.", mark: "JK", note: "Vendeur vérifié · Centre-ville", online: true },
  { name: "Mokabi Store", mark: "MS", note: "Boutique Pro · Mode", online: false },
  { name: "Nadia M.", mark: "NM", note: "Photographe · Moungali", online: true },
  { name: "Maison Noki", mark: "MN", note: "Maison & décoration · Bacongo", online: false },
];

type BookContact = (typeof initialContacts)[number] & { phone?: string };

const initialGroups = [
  { id: "design-crew", name: "Design Crew", mark: "DC", note: "12 membres · 3 nouveaux messages", description: "Création, retours et rendez-vous de l’équipe.", memberNames: ["Amina M.","Junior K.","Nadia M."] },
  { id: "commercants-brazza", name: "Commerçants Brazza", mark: "CB", note: "48 membres · Marketplace", description: "Opportunités, fournisseurs et entraide locale.", memberNames: ["Mokabi Store","Maison Noki","Junior K."] },
];

const initialGroupMessages: Record<string,CloudGroupMessage[]> = {
  "design-crew": [{id:"d1",text:"La nouvelle identité est prête. Je vous envoie la présentation.",senderId:"amina",senderName:"Amina M."},{id:"d2",text:"Parfait. On valide ensemble à 16 h ?",senderId:"junior",senderName:"Junior K."}],
  "commercants-brazza": [{id:"c1",text:"Qui connaît un livreur disponible cet après-midi ?",senderId:"mokabi",senderName:"Mokabi Store"}],
};

const initialActivities: Record<string,CloudGroupActivity[]> = {
  "design-crew": [{id:"a1",type:"poll",title:"Quelle heure pour la validation ?",details:"Choisissez avant 14 h.",options:["16 h","17 h","Demain 9 h"],eventDate:"",creatorId:"amina",creatorName:"Amina M."}],
  "commercants-brazza": [{id:"a2",type:"event",title:"Rencontre vendeurs Whappy",details:"Échange sur la livraison et les paiements.",options:[],eventDate:"2026-08-16T15:00",creatorId:"mokabi",creatorName:"Mokabi Store"}],
};

export function ContactsSpace({ search, cloud, userId, userName, cloudGroups, onCreateGroup, onMessage, onCall, notify }: {
  search: string;
  cloud: boolean;
  userId: string;
  userName: string;
  cloudGroups: CloudGroup[];
  onCreateGroup: (name: string, description: string, members: string[]) => Promise<boolean>;
  onMessage: (contact: { name: string; phone?: string }) => void;
  onCall: (name: string) => void;
  notify: (text: string) => void;
}) {
  const [tab, setTab] = useState<"contacts" | "groups" | "business">("contacts");
  const [contactList, setContactList] = useState<BookContact[]>(initialContacts);
  const [creator, setCreator] = useState<"contact" | "group" | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedGroup,setSelectedGroup]=useState(initialGroups[0].id);
  const [groupMessages,setGroupMessages]=useState<Record<string,CloudGroupMessage[]>>(initialGroupMessages);
  const [groupText,setGroupText]=useState("");
  const [groupInfo,setGroupInfo]=useState(false);
  const [notificationsEnabled,setNotificationsEnabled]=useState(true);
  const [activities,setActivities]=useState<Record<string,CloudGroupActivity[]>>(initialActivities);
  const [groupView,setGroupView]=useState<"chat"|"activities">("chat");
  const [groupInvite,setGroupInvite]=useState<{name:string;link:string}|null>(null);
  const notifyRef=useRef(notify);
  useEffect(()=>{notifyRef.current=notify;},[notify]);
  const visible = useMemo(() => contactList.filter((contact) => `${contact.name} ${contact.note}`.toLowerCase().includes(search.toLowerCase())), [contactList, search]);
  const groups = useMemo(() => {
    const synced = cloudGroups.map((group) => ({ id: group.id, name: group.name, mark: group.mark, note: `${group.memberNames.length + 1} membre${group.memberNames.length ? "s" : ""} · ${group.description || "Groupe Whappy"}`, description:group.description, memberNames:group.memberNames }));
    return [...synced, ...initialGroups.filter((sample) => !synced.some((group) => group.name === sample.name))];
  }, [cloudGroups]);
  const currentGroup=groups.find((group)=>group.id===selectedGroup)||groups[0];
  useEffect(()=>{if(!userId||!cloudGroups.some((group)=>group.id===selectedGroup))return;return watchGroupMessages(selectedGroup,(items)=>setGroupMessages((current)=>({...current,[selectedGroup]:items})),()=>notifyRef.current("Discussion de groupe momentanément hors ligne"));},[userId,selectedGroup,cloudGroups]);
  useEffect(()=>{if(!userId||!cloudGroups.some((group)=>group.id===selectedGroup))return;return watchGroupActivities(selectedGroup,(items)=>setActivities((current)=>({...current,[selectedGroup]:items})),()=>notifyRef.current("Activités de groupe momentanément hors ligne"));},[userId,selectedGroup,cloudGroups]);

  async function sendToGroup(event:FormEvent){event.preventDefault();const value=groupText.trim();if(!value||!currentGroup||busy)return;setGroupText("");if(!userId||!cloudGroups.some((group)=>group.id===currentGroup.id)){setGroupMessages((current)=>({...current,[currentGroup.id]:[...(current[currentGroup.id]||[]),{id:`local-${Date.now()}`,text:value,senderId:userId||"local",senderName:userName}]}));return;}setBusy(true);try{await sendGroupMessage(currentGroup.id,userId,userName,value);}catch{setGroupText(value);notify("Le message a été conservé : l’envoi a échoué.");}finally{setBusy(false);}}

  async function shareGroup() {
    if (!currentGroup) return;
    try {
      const group = cloudGroups.find((item) => item.id === currentGroup.id);
      if (!group) { notify("Connectez-vous pour créer un lien d’invitation sécurisé."); return; }
      if (group.ownerId !== userId) { notify("Seul l’administrateur peut créer ou renouveler ce lien."); return; }
      setBusy(true);
      const token = await ensureGroupInvite(group.id, group.ownerId, group.inviteToken);
      const link = window.location.origin + "/?group=" + encodeURIComponent(group.id) + "&invite=" + encodeURIComponent(token) + "&owner=" + encodeURIComponent(group.ownerId);
      setGroupInvite({ name: group.name, link });
    } catch {
      notify("Le lien d’invitation n’a pas pu être créé.");
    } finally {
      setBusy(false);
    }
  }

  async function publishActivity(activity:ActivityDraft){if(!currentGroup)return;setBusy(true);try{const created=userId&&cloudGroups.some((group)=>group.id===currentGroup.id)?await createGroupActivity(currentGroup.id,userId,userName,activity):{...activity,id:`local-activity-${Date.now()}`,creatorId:userId||"local",creatorName:userName,createdAt:null};setActivities((current)=>({...current,[currentGroup.id]:[created,...(current[currentGroup.id]||[])]}));setGroupView("activities");notify(activity.type==="poll"?"Sondage publié":activity.type==="event"?"Événement publié":"Annonce publiée");}catch{notify("La publication n’a pas pu être créée.");}finally{setBusy(false);}}

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
      setContactList((current) => [{ name, mark, phone, note: `${phone} · Ajouté maintenant`, online: false }, ...current]);
      setTab("contacts");
      notify(`${name} a été ajouté au carnet de démonstration`);
    }
    setCreator(null);
  }

  return <div className="contact-space">
    <section className="contact-summary"><div><span>CARNET WHAPPY</span><h2>Les bonnes personnes,<br/><em>au bon moment.</em></h2><p>Contacts, groupes et professionnels réunis autour de votre numéro.</p></div><div className="contact-stats"><span><b>{128 + contactList.length - initialContacts.length}</b> contacts</span><span><b>{9 + cloudGroups.length}</b> groupes</span><span><b>24</b> pros vérifiés</span></div></section>
    <div className={`contact-layout ${tab === "groups" ? "groups-active" : ""}`}>
      <aside className="contact-tools"><button onClick={() => setCreator("contact")}><span>＋</span><div><strong>Nouveau contact</strong><small>Ajouter avec son numéro</small></div></button><button onClick={() => setCreator("group")}><span>◎</span><div><strong>Nouveau groupe</strong><small>Famille, projet ou communauté</small></div></button><button onClick={() => setCreator("contact")}><span>⌗</span><div><strong>Saisir un Whappy ID</strong><small>Ajouter avec un numéro vérifié</small></div></button><button onClick={() => setTab("business")}><span>✓</span><div><strong>Annuaire vérifié</strong><small>Trouver un professionnel fiable</small></div></button></aside>
      <main className="contact-book"><header><div>{(["contacts", "groups", "business"] as const).map((item) => <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>{item === "contacts" ? "Contacts" : item === "groups" ? "Groupes" : "Professionnels"}</button>)}</div><span className={`contact-cloud ${cloud ? "active" : ""}`}><i />{cloud ? "Compte synchronisé" : "Démonstration locale"}</span></header>
        {tab === "groups" ? <div className="groups-studio"><aside className="groups-list"><header><div><strong>Vos groupes</strong><small>{groups.length} salons actifs</small></div><button onClick={()=>setCreator("group")}>＋</button></header>{groups.map((group)=><button className={currentGroup?.id===group.id?"active":""} key={group.id} onClick={()=>setSelectedGroup(group.id)}><span>{group.mark}</span><div><strong>{group.name}</strong><small>{group.description||group.note}</small></div><b>{group.memberNames.length+1}</b></button>)}</aside>{currentGroup&&<section className="group-room"><header><span>{currentGroup.mark}</span><div><strong>{currentGroup.name}</strong><small>{currentGroup.memberNames.length+1} membres · Salon sécurisé</small></div><button onClick={()=>onCall(`${currentGroup.name} · appel de groupe`)} aria-label="Appel de groupe">☎</button><button onClick={()=>setGroupInfo(true)} aria-label="Informations du groupe">•••</button></header><div className="group-pin"><span>◆</span><p><small>MESSAGE ÉPINGLÉ</small><strong>{currentGroup.description||"Bienvenue dans votre groupe Whappy."}</strong></p><button onClick={()=>notify("Message épinglé ouvert")}>Voir</button></div><div className="group-thread"><span>AUJOURD’HUI</span>{(groupMessages[currentGroup.id]||[]).map((message)=><article className={message.senderId===userId||message.senderId==="local"?"mine":""} key={message.id}><b>{message.senderName}</b><p>{message.text}</p><small>{message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR",{hour:"2-digit",minute:"2-digit"})||"Maintenant"} ✓✓</small></article>)}{!(groupMessages[currentGroup.id]||[]).length&&<div className="group-welcome"><span>{currentGroup.mark}</span><strong>Le groupe est prêt</strong><small>Envoyez le premier message à vos membres.</small></div>}</div><form onSubmit={sendToGroup}><label className="group-attach" aria-label="Joindre un fichier">＋<input type="file" accept="image/*,video/*,.pdf" onChange={(event)=>{const file=event.target.files?.[0];if(file){setGroupText((value)=>`${value}${value?" ":""}📎 ${file.name}`);notify(`${file.name} ajouté au message`);}}}/></label><input value={groupText} maxLength={4000} onChange={(event)=>setGroupText(event.target.value)} placeholder={`Message à ${currentGroup.name}`}/><button type="button" onClick={()=>setGroupText((value)=>`${value} 😊`)}>☺</button><button className="group-send" disabled={busy}>➤</button></form></section>}{groupInfo&&currentGroup&&<div className="group-info"><button className="group-info-dismiss" onClick={()=>setGroupInfo(false)}/><section><header><span>{currentGroup.mark}</span><div><h3>{currentGroup.name}</h3><p>{currentGroup.description}</p></div><button onClick={()=>setGroupInfo(false)}>×</button></header><div className="group-member-head"><strong>{currentGroup.memberNames.length+1} membres</strong><button onClick={shareGroup}>＋ Inviter</button></div><div className="group-members"><article><span>{userName.split(/\s+/).map((part)=>part[0]).join("").slice(0,2)}</span><div><strong>{userName}</strong><small>Créateur du groupe</small></div><b>ADMIN</b></article>{currentGroup.memberNames.map((name)=><article key={name}><span>{name.split(/\s+/).map((part)=>part[0]).join("").slice(0,2)}</span><div><strong>{name}</strong><small>Membre invité</small></div></article>)}</div><button className="group-setting" onClick={()=>{setNotificationsEnabled((value)=>!value);notify(notificationsEnabled?"Notifications du groupe désactivées":"Notifications du groupe activées")}}>♢ Notifications <b>{notificationsEnabled?"Activées":"Désactivées"}</b></button><button className="group-setting" onClick={()=>notify(`${(groupMessages[currentGroup.id]||[]).filter((message)=>message.text.includes("📎")).length} pièce(s) jointe(s) dans cette discussion`)}>▦ Médias, liens et documents <b>→</b></button></section></div>}</div> : <div className="contact-rows">{visible.filter((contact) => tab === "contacts" || /Store|Maison/.test(contact.name)).map((contact) => <article key={contact.name}><span>{contact.mark}<i className={contact.online ? "online" : ""}/></span><div><strong>{contact.name}{tab === "business" && <b>✓</b>}</strong><small>{contact.note}</small></div><button onClick={() => onCall(contact.name)} aria-label={`Appeler ${contact.name}`}>☎</button><button className="contact-message" onClick={() => onMessage({ name: contact.name, phone: contact.phone })}>Message</button></article>)}{!visible.length && <p className="contact-empty">Aucun contact ne correspond à cette recherche.</p>}</div>}
      </main>
    </div>
    {creator && <div className="contact-create-layer"><button className="contact-create-dismiss" onClick={() => setCreator(null)} aria-label="Fermer"/><form onSubmit={createEntry}><header><div><small>CARNET WHAPPY</small><h3>{creator === "group" ? "Créer un groupe" : "Ajouter un contact"}</h3></div><button type="button" onClick={() => setCreator(null)}>×</button></header><label>{creator === "group" ? "Nom du groupe" : "Nom complet"}<input name="name" required placeholder={creator === "group" ? "Ex. Équipe projet" : "Ex. Grâce M."}/></label>{creator === "contact" ? <label>Numéro ou Whappy ID<input name="phone" required inputMode="tel" placeholder="+242 06…"/></label> : <><label>Description<input name="description" placeholder="Ex. Coordination du projet"/></label><label>Membres à inviter<input name="members" placeholder="Amina, Junior, Nadia…"/></label></>}<p>{creator === "group" ? cloud ? "Le groupe sera synchronisé avec votre compte Whappy." : "Le groupe sera créé dans cette démonstration pour la session." : "Le contact sera ajouté au carnet de démonstration pour cette session."}</p><button className="contact-create-submit" disabled={busy}>{busy ? "Création…" : creator === "group" ? "Créer le groupe →" : "Ajouter le contact →"}</button></form></div>}
    {tab==="groups"&&currentGroup&&<div className={`community-drawer ${groupView==="activities"?"open":""}`}><button className="community-toggle" onClick={()=>setGroupView((view)=>view==="chat"?"activities":"chat")}>{groupView==="chat"?`✦ Communauté · ${(activities[currentGroup.id]||[]).length}`:"× Retour au salon"}</button>{groupView==="activities"&&<GroupActivities groupId={currentGroup.id} userId={userId} cloud={cloudGroups.some((group)=>group.id===currentGroup.id)} items={activities[currentGroup.id]||[]} busy={busy} onCreate={publishActivity} notify={notify}/>}</div>}
    {tab==="groups"&&currentGroup&&<GroupJoinRequests ownerId={userId} groupId={currentGroup.id} groupName={currentGroup.name} enabled={cloudGroups.some((group)=>group.id===currentGroup.id&&group.ownerId===userId)} notify={notify}/>}
    {groupInvite&&<GroupInvitePanel groupName={groupInvite.name} link={groupInvite.link} onClose={()=>setGroupInvite(null)}/>}
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

type HubPanel = "wallet" | "receive" | "rewards" | "service" | null;
type WalletTransaction = { id: string; label: string; amount: number; date: string };
type ServiceKind = "Transport" | "Livraison" | "Santé";
type ServiceRequest = { id: string; kind: ServiceKind; title: string; details: string; status: "confirmée" | "terminée" };

const nearbyServices: Array<{ kind: ServiceKind; icon: string; note: string; options: string[] }> = [
  { kind: "Transport", icon: "⌖", note: "Course locale et estimation", options: ["Moto · 1 500 FCFA", "Voiture · 3 500 FCFA", "Minibus · 6 000 FCFA"] },
  { kind: "Livraison", icon: "→", note: "Colis et retrait boutique", options: ["Express · 2 500 FCFA", "Aujourd’hui · 1 500 FCFA", "Programmé · 1 000 FCFA"] },
  { kind: "Santé", icon: "＋", note: "Professionnels et rendez-vous", options: ["Médecin généraliste", "Pharmacie de garde", "Infirmier à domicile"] },
];

function formatMoney(value: number) {
  return new Intl.NumberFormat("fr-FR").format(value);
}

export function SuperHub({ go, orderCount, onOrders, notify, search = "" }: { go: (destination: Destination) => void; orderCount: number; onOrders: () => void; notify: (text: string) => void; search?: string }) {
  const [panel, setPanel] = useState<HubPanel>(null);
  const [provider, setProvider] = useState("");
  const [balance, setBalance] = useState(0);
  const [transactions, setTransactions] = useState<WalletTransaction[]>([]);
  const [rewardCodes, setRewardCodes] = useState<string[]>([]);
  const [serviceKind, setServiceKind] = useState<ServiceKind>("Transport");
  const [requests, setRequests] = useState<ServiceRequest[]>([]);
  const [receiveAmount, setReceiveAmount] = useState("");
  const [receiveNote, setReceiveNote] = useState("");
  const [qrUrl, setQrUrl] = useState("");
  const [hydrated, setHydrated] = useState(false);
  const query = search.trim().toLowerCase();
  const visibleServices = services.filter((item) => !query || `${item.title} ${item.note}`.toLowerCase().includes(query));
  const visibleNearby = nearbyServices.filter((item) => !query || `${item.kind} ${item.note} ${item.options.join(" ")}`.toLowerCase().includes(query));

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      try {
        const saved = JSON.parse(localStorage.getItem("whappy-services") || "null") as null | { provider?: string; balance?: number; transactions?: WalletTransaction[]; rewards?: string[]; requests?: ServiceRequest[] };
        if (saved) {
          setProvider(saved.provider || "");
          setBalance(Number(saved.balance) || 0);
          setTransactions(Array.isArray(saved.transactions) ? saved.transactions : []);
          setRewardCodes(Array.isArray(saved.rewards) ? saved.rewards : []);
          setRequests(Array.isArray(saved.requests) ? saved.requests : []);
        }
      } catch { /* Le mode privé peut bloquer le stockage local. */ }
      setHydrated(true);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try { localStorage.setItem("whappy-services", JSON.stringify({ provider, balance, transactions, rewards: rewardCodes, requests })); }
    catch { /* L’expérience reste utilisable pendant la session. */ }
  }, [provider, balance, transactions, rewardCodes, requests, hydrated]);

  useEffect(() => {
    if (panel !== "receive") return;
    const payload = `whappy://receive?amount=${encodeURIComponent(receiveAmount || "libre")}&note=${encodeURIComponent(receiveNote || "Paiement Whappy")}`;
    void QRCode.toDataURL(payload, { width: 280, margin: 1, color: { dark: "#08384d", light: "#ffffff" } }).then(setQrUrl).catch(() => setQrUrl(""));
  }, [panel, receiveAmount, receiveNote]);

  function connectWallet(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const nextProvider = String(form.get("provider") || "");
    if (!nextProvider) return;
    setProvider(nextProvider);
    setBalance(25_000);
    setTransactions([{ id: crypto.randomUUID(), label: "Solde de démonstration", amount: 25_000, date: "Maintenant" }]);
    notify(`${nextProvider} est configuré en mode démonstration`);
  }

  function pay(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const recipient = String(form.get("recipient") || "").trim();
    const amount = Number(form.get("amount"));
    if (!recipient || !Number.isFinite(amount) || amount <= 0) return;
    if (amount > balance) { notify("Solde de démonstration insuffisant"); return; }
    setBalance((value) => value - amount);
    setTransactions((current) => [{ id: crypto.randomUUID(), label: `Paiement test · ${recipient}`, amount: -amount, date: "Maintenant" }, ...current]);
    event.currentTarget.reset();
    notify(`Paiement test de ${formatMoney(amount)} FCFA enregistré`);
  }

  function createServiceRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const option = String(form.get("option") || "");
    const details = String(form.get("details") || "").trim();
    const request = { id: crypto.randomUUID(), kind: serviceKind, title: option, details, status: "confirmée" as const };
    setRequests((current) => [request, ...current]);
    notify(`Demande ${serviceKind.toLowerCase()} enregistrée`);
    setPanel(null);
  }

  async function copyReceiveLink() {
    const link = `whappy://receive?amount=${encodeURIComponent(receiveAmount || "libre")}&note=${encodeURIComponent(receiveNote || "Paiement Whappy")}`;
    try { await navigator.clipboard.writeText(link); notify("Lien de paiement copié"); }
    catch { notify("Sélectionnez le lien de paiement pour le copier"); }
  }

  return <div className="super-hub">
    <section className="hub-hero"><div><span>WHAPPY SERVICES</span><h2>Un seul numéro.<br/><em>Toute votre vie numérique.</em></h2><p>Parlez, payez, vendez, trouvez et développez vos activités sans quitter Whappy.</p></div><button className="wallet-card" onClick={() => setPanel("wallet")}><span>W</span><div><small>WHAPPY PAY · DÉMO</small><strong>{provider ? `${formatMoney(balance)} FCFA` : "Configurer le portefeuille"}</strong><p>{provider || "Mobile Money · Carte · QR"}</p></div><b>→</b></button></section>
    <section className="hub-body">
      <div className="hub-shortcuts"><button onClick={() => setPanel("wallet")}><span>⌁</span><strong>Payer</strong><small>{provider ? "Effectuer un paiement test" : "Configurer un moyen test"}</small></button><button onClick={() => setPanel("receive")}><span>⌗</span><strong>Recevoir</strong><small>Créer un QR et un lien</small></button><button onClick={onOrders}><span>▤</span><strong>Commandes</strong><small>{orderCount ? `${orderCount} commande${orderCount > 1 ? "s" : ""}` : "Suivre mes achats"}</small></button><button onClick={() => setPanel("rewards")}><span>％</span><strong>Avantages</strong><small>{rewardCodes.length ? `${rewardCodes.length} offre${rewardCodes.length > 1 ? "s" : ""} activée${rewardCodes.length > 1 ? "s" : ""}` : "Coupons et fidélité"}</small></button></div>
      <header className="hub-section-title"><div><small>MINI-SERVICES WHAPPY</small><h3>{query ? `Résultats pour « ${search} »` : "Tout ce dont vous avez besoin"}</h3></div></header>
      <div className="service-grid">{visibleServices.map((service) => <button className={service.accent ? "accent" : ""} key={service.title} onClick={() => service.destination && go(service.destination)}><span>{service.icon}</span><div><strong>{service.title}</strong><small>{service.note}</small></div><b>→</b></button>)}</div>
      <header className="hub-section-title"><div><small>À PROXIMITÉ</small><h3>Services utiles maintenant</h3></div></header>
      <div className="local-services">{visibleNearby.map((item) => <button key={item.kind} onClick={() => { setServiceKind(item.kind); setPanel("service"); }}><span>{item.icon}</span><strong>{item.kind}</strong><small>{item.note}</small></button>)}</div>
      {!visibleServices.length && !visibleNearby.length && <div className="hub-no-results"><span>⌕</span><strong>Aucun service trouvé</strong><small>Essayez “paiement”, “transport”, “marché” ou “santé”.</small></div>}
      {requests.length > 0 && <section className="service-requests"><header><div><small>SUIVI LOCAL</small><h3>Vos demandes récentes</h3></div><b>{requests.length}</b></header>{requests.slice(0, 3).map((item) => <article key={item.id}><span>{item.kind[0]}</span><div><strong>{item.title}</strong><small>{item.details || item.kind} · {item.status}</small></div><button onClick={() => setRequests((current) => current.map((request) => request.id === item.id ? { ...request, status: "terminée" } : request))}>{item.status === "terminée" ? "✓ Terminée" : "Marquer terminée"}</button></article>)}</section>}
    </section>

    {panel === "wallet" && <div className="wallet-layer" role="dialog" aria-modal="true" aria-label="Whappy Pay"><button className="wallet-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><section className="wallet-panel functional"><header><div><span>WHAPPY PAY · BAC À SABLE</span><h3>Payez sans écran vide.</h3><p>Essayez le parcours complet avec un solde fictif, sans débit réel.</p></div><button onClick={() => setPanel(null)}>×</button></header><div className="wallet-balance"><small>SOLDE DE DÉMONSTRATION</small><strong>{formatMoney(balance)} <span>FCFA</span></strong><p>{provider || "Aucun moyen de test configuré"}</p></div>{provider ? <><form className="wallet-form" onSubmit={pay}><h4>Nouveau paiement test</h4><label>Destinataire<input name="recipient" required placeholder="Nom ou numéro Whappy"/></label><label>Montant<input name="amount" required min="100" max={balance} step="100" type="number" inputMode="numeric" placeholder="5 000"/></label><button disabled={balance < 100}>Confirmer le paiement test →</button></form><div className="wallet-demo-tools"><button onClick={() => { setBalance((value) => value + 10_000); setTransactions((current) => [{ id: crypto.randomUUID(), label: "Recharge de test", amount: 10_000, date: "Maintenant" }, ...current]); }}>＋ Ajouter 10 000 FCFA test</button><button onClick={() => setPanel("receive")}>⌗ Créer un QR de réception</button></div><div className="wallet-history"><h4>Activité</h4>{transactions.map((item) => <p key={item.id}><span><strong>{item.label}</strong><small>{item.date}</small></span><b className={item.amount < 0 ? "negative" : ""}>{item.amount > 0 ? "+" : ""}{formatMoney(item.amount)} FCFA</b></p>)}</div></> : <form className="wallet-form" onSubmit={connectWallet}><h4>Configurer le mode test</h4><label>Moyen de paiement<select name="provider" required defaultValue=""><option value="" disabled>Choisir…</option><option>MTN Mobile Money</option><option>Airtel Money</option><option>Carte bancaire de test</option></select></label><label>Numéro de test<input name="phone" required inputMode="tel" placeholder="+242 06 000 00 00"/></label><button>Activer avec 25 000 FCFA fictifs →</button></form>}<p className="wallet-safety">◆ Démonstration locale uniquement : aucun opérateur, aucune banque et aucun débit réel.</p></section></div>}

    {panel === "receive" && <div className="wallet-layer" role="dialog" aria-modal="true" aria-label="Recevoir un paiement"><button className="wallet-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><section className="wallet-panel receive-panel"><header><div><span>WHAPPY QR</span><h3>Recevoir un paiement test</h3><p>Le QR se met à jour avec le montant et le motif.</p></div><button onClick={() => setPanel(null)}>×</button></header><div className="receive-grid"><div className="receive-qr">{qrUrl ? <img src={qrUrl} alt="QR de paiement Whappy"/> : <span>Création du QR…</span>}</div><div><label>Montant facultatif<input value={receiveAmount} onChange={(event) => setReceiveAmount(event.target.value.replace(/\D/g, ""))} inputMode="numeric" placeholder="10 000 FCFA"/></label><label>Motif<input value={receiveNote} onChange={(event) => setReceiveNote(event.target.value)} maxLength={80} placeholder="Commande, participation…"/></label><button onClick={copyReceiveLink}>Copier le lien de paiement</button>{qrUrl && <a href={qrUrl} download="whappy-qr.png">Télécharger le QR</a>}</div></div><p className="wallet-safety">◆ Ce QR ouvre un parcours Whappy de démonstration et ne collecte aucun paiement réel.</p></section></div>}

    {panel === "rewards" && <div className="wallet-layer" role="dialog" aria-modal="true" aria-label="Avantages Whappy"><button className="wallet-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><section className="wallet-panel rewards-panel"><header><div><span>WHAPPY AVANTAGES</span><h3>Vos offres locales</h3><p>Activez un coupon : il reste mémorisé sur cet appareil.</p></div><button onClick={() => setPanel(null)}>×</button></header>{[{code:"LIVRAISON15",title:"-15 % sur une livraison",note:"Valable sur la prochaine demande test"},{code:"MARKET5",title:"5 000 FCFA de remise",note:"Dès 50 000 FCFA sur le Marketplace"},{code:"LIVEVIP",title:"Accès prioritaire aux directs",note:"Rappel 10 minutes avant le lancement"}].map((reward) => <article key={reward.code}><span>％</span><div><strong>{reward.title}</strong><small>{reward.note} · {reward.code}</small></div><button className={rewardCodes.includes(reward.code) ? "active" : ""} onClick={() => setRewardCodes((current) => current.includes(reward.code) ? current.filter((code) => code !== reward.code) : [...current, reward.code])}>{rewardCodes.includes(reward.code) ? "✓ Activé" : "Activer"}</button></article>)}</section></div>}

    {panel === "service" && <div className="wallet-layer" role="dialog" aria-modal="true" aria-label={`Demande ${serviceKind}`}><button className="wallet-dismiss" onClick={() => setPanel(null)} aria-label="Fermer"/><form className="wallet-panel service-form" onSubmit={createServiceRequest}><header><div><span>SERVICE LOCAL</span><h3>{serviceKind}</h3><p>Préparez et suivez votre demande depuis Whappy.</p></div><button type="button" onClick={() => setPanel(null)}>×</button></header><label>Option<select name="option" required>{nearbyServices.find((item) => item.kind === serviceKind)?.options.map((option) => <option key={option}>{option}</option>)}</select></label><label>{serviceKind === "Santé" ? "Besoin et quartier" : "Départ, destination et détails"}<textarea name="details" required placeholder={serviceKind === "Santé" ? "Ex. consultation générale à Moungali" : "Ex. Poto-Poto vers Bacongo, petit colis"}/></label><button className="service-submit">Enregistrer la demande →</button><p className="wallet-safety">◆ Cette version prépare la demande localement. Confirmez toujours le prix et l’identité du prestataire avant tout déplacement.</p></form></div>}
  </div>;
}
