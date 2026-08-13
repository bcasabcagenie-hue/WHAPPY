"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createGroupActivity, sendGroupMessage, watchGroupActivities, watchGroupMessages, type CloudGroup, type CloudGroupActivity, type CloudGroupMessage } from "@/lib/whappy-data";
import { GroupActivities, type ActivityDraft } from "@/app/components/GroupActivities";

type Destination = "live" | "market" | "barter" | "seek" | "twin" | "orbit";

const initialContacts = [
  { name: "Amina M.", mark: "AM", note: "Acheteuse fiable · Poto-Poto", online: true },
  { name: "Junior K.", mark: "JK", note: "Vendeur vérifié · Centre-ville", online: true },
  { name: "Mokabi Store", mark: "MS", note: "Boutique Pro · Mode", online: false },
  { name: "Nadia M.", mark: "NM", note: "Photographe · Moungali", online: true },
  { name: "Maison Noki", mark: "MN", note: "Maison & décoration · Bacongo", online: false },
];

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
  onMessage: (name: string) => void;
  onCall: (name: string) => void;
  notify: (text: string) => void;
}) {
  const [tab, setTab] = useState<"contacts" | "groups" | "business">("contacts");
  const [contactList, setContactList] = useState(initialContacts);
  const [creator, setCreator] = useState<"contact" | "group" | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedGroup,setSelectedGroup]=useState(initialGroups[0].id);
  const [groupMessages,setGroupMessages]=useState<Record<string,CloudGroupMessage[]>>(initialGroupMessages);
  const [groupText,setGroupText]=useState("");
  const [groupInfo,setGroupInfo]=useState(false);
  const [notificationsEnabled,setNotificationsEnabled]=useState(true);
  const [activities,setActivities]=useState<Record<string,CloudGroupActivity[]>>(initialActivities);
  const [groupView,setGroupView]=useState<"chat"|"activities">("chat");
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
    const invitation = `Rejoignez le groupe « ${currentGroup.name} » sur Whappy.`;
    try {
      if (navigator.share) await navigator.share({ title: currentGroup.name, text: invitation, url: window.location.href });
      else await navigator.clipboard.writeText(`${invitation} ${window.location.href}`);
      notify("Invitation du groupe partagée");
    } catch { /* La feuille de partage a été fermée. */ }
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
      setContactList((current) => [{ name, mark, note: `${phone} · Ajouté maintenant`, online: false }, ...current]);
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
        {tab === "groups" ? <div className="groups-studio"><aside className="groups-list"><header><div><strong>Vos groupes</strong><small>{groups.length} salons actifs</small></div><button onClick={()=>setCreator("group")}>＋</button></header>{groups.map((group)=><button className={currentGroup?.id===group.id?"active":""} key={group.id} onClick={()=>setSelectedGroup(group.id)}><span>{group.mark}</span><div><strong>{group.name}</strong><small>{group.description||group.note}</small></div><b>{group.memberNames.length+1}</b></button>)}</aside>{currentGroup&&<section className="group-room"><header><span>{currentGroup.mark}</span><div><strong>{currentGroup.name}</strong><small>{currentGroup.memberNames.length+1} membres · Salon sécurisé</small></div><button onClick={()=>onCall(`${currentGroup.name} · appel de groupe`)} aria-label="Appel de groupe">☎</button><button onClick={()=>setGroupInfo(true)} aria-label="Informations du groupe">•••</button></header><div className="group-pin"><span>◆</span><p><small>MESSAGE ÉPINGLÉ</small><strong>{currentGroup.description||"Bienvenue dans votre groupe Whappy."}</strong></p><button onClick={()=>notify("Message épinglé ouvert")}>Voir</button></div><div className="group-thread"><span>AUJOURD’HUI</span>{(groupMessages[currentGroup.id]||[]).map((message)=><article className={message.senderId===userId||message.senderId==="local"?"mine":""} key={message.id}><b>{message.senderName}</b><p>{message.text}</p><small>{message.createdAt?.toDate?.()?.toLocaleTimeString("fr-FR",{hour:"2-digit",minute:"2-digit"})||"Maintenant"} ✓✓</small></article>)}{!(groupMessages[currentGroup.id]||[]).length&&<div className="group-welcome"><span>{currentGroup.mark}</span><strong>Le groupe est prêt</strong><small>Envoyez le premier message à vos membres.</small></div>}</div><form onSubmit={sendToGroup}><label className="group-attach" aria-label="Joindre un fichier">＋<input type="file" accept="image/*,video/*,.pdf" onChange={(event)=>{const file=event.target.files?.[0];if(file){setGroupText((value)=>`${value}${value?" ":""}📎 ${file.name}`);notify(`${file.name} ajouté au message`);}}}/></label><input value={groupText} maxLength={4000} onChange={(event)=>setGroupText(event.target.value)} placeholder={`Message à ${currentGroup.name}`}/><button type="button" onClick={()=>setGroupText((value)=>`${value} 😊`)}>☺</button><button className="group-send" disabled={busy}>➤</button></form></section>}{groupInfo&&currentGroup&&<div className="group-info"><button className="group-info-dismiss" onClick={()=>setGroupInfo(false)}/><section><header><span>{currentGroup.mark}</span><div><h3>{currentGroup.name}</h3><p>{currentGroup.description}</p></div><button onClick={()=>setGroupInfo(false)}>×</button></header><div className="group-member-head"><strong>{currentGroup.memberNames.length+1} membres</strong><button onClick={shareGroup}>＋ Inviter</button></div><div className="group-members"><article><span>{userName.split(/\s+/).map((part)=>part[0]).join("").slice(0,2)}</span><div><strong>{userName}</strong><small>Créateur du groupe</small></div><b>ADMIN</b></article>{currentGroup.memberNames.map((name)=><article key={name}><span>{name.split(/\s+/).map((part)=>part[0]).join("").slice(0,2)}</span><div><strong>{name}</strong><small>Membre invité</small></div></article>)}</div><button className="group-setting" onClick={()=>{setNotificationsEnabled((value)=>!value);notify(notificationsEnabled?"Notifications du groupe désactivées":"Notifications du groupe activées")}}>♢ Notifications <b>{notificationsEnabled?"Activées":"Désactivées"}</b></button><button className="group-setting" onClick={()=>notify(`${(groupMessages[currentGroup.id]||[]).filter((message)=>message.text.includes("📎")).length} pièce(s) jointe(s) dans cette discussion`)}>▦ Médias, liens et documents <b>→</b></button></section></div>}</div> : <div className="contact-rows">{visible.filter((contact) => tab === "contacts" || /Store|Maison/.test(contact.name)).map((contact) => <article key={contact.name}><span>{contact.mark}<i className={contact.online ? "online" : ""}/></span><div><strong>{contact.name}{tab === "business" && <b>✓</b>}</strong><small>{contact.note}</small></div><button onClick={() => onCall(contact.name)} aria-label={`Appeler ${contact.name}`}>☎</button><button className="contact-message" onClick={() => onMessage(contact.name)}>Message</button></article>)}{!visible.length && <p className="contact-empty">Aucun contact ne correspond à cette recherche.</p>}</div>}
      </main>
    </div>
    {creator && <div className="contact-create-layer"><button className="contact-create-dismiss" onClick={() => setCreator(null)} aria-label="Fermer"/><form onSubmit={createEntry}><header><div><small>CARNET WHAPPY</small><h3>{creator === "group" ? "Créer un groupe" : "Ajouter un contact"}</h3></div><button type="button" onClick={() => setCreator(null)}>×</button></header><label>{creator === "group" ? "Nom du groupe" : "Nom complet"}<input name="name" required placeholder={creator === "group" ? "Ex. Équipe projet" : "Ex. Grâce M."}/></label>{creator === "contact" ? <label>Numéro ou Whappy ID<input name="phone" required inputMode="tel" placeholder="+242 06…"/></label> : <><label>Description<input name="description" placeholder="Ex. Coordination du projet"/></label><label>Membres à inviter<input name="members" placeholder="Amina, Junior, Nadia…"/></label></>}<p>{creator === "group" ? cloud ? "Le groupe sera synchronisé avec votre compte Whappy." : "Le groupe sera créé dans cette démonstration pour la session." : "Le contact sera ajouté au carnet de démonstration pour cette session."}</p><button className="contact-create-submit" disabled={busy}>{busy ? "Création…" : creator === "group" ? "Créer le groupe →" : "Ajouter le contact →"}</button></form></div>}
    {tab==="groups"&&currentGroup&&<div className={`community-drawer ${groupView==="activities"?"open":""}`}><button className="community-toggle" onClick={()=>setGroupView((view)=>view==="chat"?"activities":"chat")}>{groupView==="chat"?`✦ Communauté · ${(activities[currentGroup.id]||[]).length}`:"× Retour au salon"}</button>{groupView==="activities"&&<GroupActivities groupId={currentGroup.id} userId={userId} cloud={cloudGroups.some((group)=>group.id===currentGroup.id)} items={activities[currentGroup.id]||[]} busy={busy} onCreate={publishActivity} notify={notify}/>}</div>}
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
