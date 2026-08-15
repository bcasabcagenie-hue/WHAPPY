"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  createWhappyRoom,
  deleteRoomPost,
  pinRoomPost,
  publishRoomPost,
  reactToRoomPost,
  roomKindLabels,
  roomOrganizerLabels,
  setRoomSubscription,
  watchRoomPosts,
  watchWhappyRooms,
  type NewWhappyRoom,
  type RoomAccess,
  type RoomKind,
  type RoomOrganizer,
  type WhappyRoom,
  type WhappyRoomPost,
} from "@/lib/whappy-rooms";
import { WepiRoomPilot } from "@/app/components/WepiRoomPilot";

const roomKinds = Object.keys(roomKindLabels) as RoomKind[];
const organizerTypes = Object.keys(roomOrganizerLabels) as RoomOrganizer[];

const demoRooms: WhappyRoom[] = [
  { id: "demo-prayer", name: "Salle Espérance · Église La Grâce", description: "Un espace de prière, d’encouragement et d’annonces pour la communauté de l’église.", category: "prayer", organizerType: "church", access: "approval", guidelines: "Respecter les convictions, protéger les demandes de prière et garder un langage bienveillant.", emoji: "✦", ownerId: "demo-church", ownerName: "Église La Grâce", memberIds: ["demo-user"], memberCount: 248, postCount: 3, lastPost: "Réunion de prière ce soir à 19 h · Salle principale", verified: true, createdAt: null, updatedAt: null },
  { id: "demo-tech", name: "Tech Congo · Construire ensemble", description: "Développeurs, designers et entrepreneurs partagent des outils, des offres et des solutions locales.", category: "technology", organizerType: "organization", access: "public", guidelines: "Partager du concret. Pas de spam, pas de collecte abusive de données, pas de promesses irréalistes.", emoji: "⌘", ownerId: "demo-tech-org", ownerName: "Tech Congo", memberIds: ["demo-user"], memberCount: 1_204, postCount: 12, lastPost: "Appel à projets : les inscriptions ouvrent lundi", verified: true, createdAt: null, updatedAt: null },
  { id: "demo-business", name: "Entreprendre à Brazzaville", description: "Conseils, rencontres et opportunités pour les personnes qui créent une activité.", category: "business", organizerType: "organization", access: "public", guidelines: "Les conseils ne remplacent pas un professionnel. Citer ses sources et annoncer clairement ses intérêts.", emoji: "↗", ownerId: "demo-business-org", ownerName: "Réseau des entrepreneurs", memberIds: [], memberCount: 86, postCount: 5, lastPost: "Petit-déjeuner réseau · samedi 9 h à Moungali", verified: false, createdAt: null, updatedAt: null },
  { id: "demo-study", name: "Révisions & entraide", description: "Un espace calme pour apprendre, demander de l’aide et progresser ensemble.", category: "education", organizerType: "creator", access: "public", guidelines: "Aider sans humilier. Les corrigés et ressources doivent être partagés légalement.", emoji: "◇", ownerId: "demo-study", ownerName: "Maya K.", memberIds: ["demo-user"], memberCount: 432, postCount: 8, lastPost: "Ressource du jour : apprendre JavaScript en 30 minutes", verified: false, createdAt: null, updatedAt: null },
];

const demoPosts: Record<string, WhappyRoomPost[]> = {
  "demo-prayer": [
    { id: "prayer-1", text: "Bienvenue dans cette salle. Les responsables publieront ici les rendez-vous et les mots d’encouragement.", authorId: "demo-church", authorName: "Église La Grâce", createdAt: null, reactions: { "demo-user": "❤️" }, pinned: true, deleted: false },
    { id: "prayer-2", text: "Réunion de prière ce soir à 19 h. Les personnes à distance peuvent envoyer leur intention en privé aux responsables.", authorId: "demo-church", authorName: "Église La Grâce", createdAt: null, reactions: {}, pinned: false, deleted: false },
  ],
  "demo-tech": [{ id: "tech-1", text: "Quel outil utilisez-vous pour prototyper rapidement une application mobile ?", authorId: "demo-tech-org", authorName: "Tech Congo", createdAt: null, reactions: { "demo-user": "💡" }, pinned: true, deleted: false }],
};

function postDate(post: WhappyRoomPost) {
  const date = post.createdAt?.toDate?.();
  return date ? date.toLocaleDateString("fr-FR", { day: "2-digit", month: "short" }) : "À l’instant";
}

export function RoomsSpace({ userId, userName, search, cloud, notify }: { userId: string; userName: string; search: string; cloud: boolean; notify: (text: string) => void }) {
  const [rooms, setRooms] = useState<WhappyRoom[]>(cloud ? [] : demoRooms);
  const [selectedRoom, setSelectedRoom] = useState<WhappyRoom | null>(null);
  const [posts, setPosts] = useState<WhappyRoomPost[]>([]);
  const [filter, setFilter] = useState<"all" | RoomKind>("all");
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [postText, setPostText] = useState("");
  const [demoPostState, setDemoPostState] = useState<Record<string, WhappyRoomPost[]>>(demoPosts);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    if (cloud) return watchWhappyRooms(setRooms, () => notifyRef.current("Les salles Whappy sont momentanément indisponibles"));
    let active = true;
    queueMicrotask(() => { if (active) setRooms(demoRooms); });
    return () => { active = false; };
  }, [cloud]);
  useEffect(() => {
    if (!selectedRoom) {
      let active = true;
      queueMicrotask(() => { if (active) setPosts([]); });
      return () => { active = false; };
    }
    if (cloud) return watchRoomPosts(selectedRoom.id, setPosts, () => notifyRef.current("Les publications de cette salle sont momentanément indisponibles"));
    let active = true;
    queueMicrotask(() => { if (active) setPosts(demoPostState[selectedRoom.id] || []); });
    return () => { active = false; };
  }, [cloud, demoPostState, selectedRoom]);

  const visibleRooms = useMemo(() => {
    const query = search.trim().toLowerCase();
    return rooms.filter((room) => (filter === "all" || room.category === filter) && (!query || `${room.name} ${room.description} ${room.ownerName} ${room.category}`.toLowerCase().includes(query)));
  }, [filter, rooms, search]);

  function roomIsMember(room: WhappyRoom) { return room.ownerId === userId || room.memberIds.includes(userId); }

  async function createRoom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const room: NewWhappyRoom = {
      name: String(form.get("name") || ""),
      description: String(form.get("description") || ""),
      category: String(form.get("category") || "technology") as RoomKind,
      organizerType: String(form.get("organizerType") || "organization") as RoomOrganizer,
      access: String(form.get("access") || "public") as RoomAccess,
      guidelines: String(form.get("guidelines") || ""),
      emoji: String(form.get("emoji") || "◈"),
    };
    if (room.name.trim().length < 3 || room.description.trim().length < 10) { notify("Ajoutez un nom et une description claire pour votre salle"); return; }
    setBusy(true);
    try {
      if (cloud) await createWhappyRoom(userId, userName, room);
      else {
        const localRoom: WhappyRoom = { id: `demo-room-${Date.now()}`, ...room, ownerId: userId, ownerName: userName, memberIds: [userId], memberCount: 1, postCount: 0, lastPost: `Bienvenue dans ${room.name.trim()}`, verified: false, createdAt: null, updatedAt: null };
        setRooms((current) => [localRoom, ...current]);
      }
      setCreating(false);
      notify(cloud ? "Salle créée et publiée dans Whappy" : "Salle créée dans la démonstration");
    } catch { notify("La salle n’a pas pu être créée"); }
    finally { setBusy(false); }
  }

  async function toggleSubscription(room: WhappyRoom) {
    const subscribed = roomIsMember(room);
    if (room.ownerId === userId) return;
    setBusy(true);
    try {
      if (cloud) await setRoomSubscription(room.id, userId, !subscribed);
      else setRooms((current) => current.map((item) => item.id === room.id ? { ...item, memberIds: !subscribed ? [...item.memberIds, userId] : item.memberIds.filter((id) => id !== userId), memberCount: Math.max(0, item.memberCount + (!subscribed ? 1 : -1)) } : item));
      notify(!subscribed ? `Vous suivez maintenant « ${room.name} »` : `Vous ne suivez plus « ${room.name} »`);
    } catch { notify("L’abonnement à la salle n’a pas pu être mis à jour"); }
    finally { setBusy(false); }
  }

  async function publishPost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedRoom || selectedRoom.ownerId !== userId || !postText.trim()) return;
    const value = postText.trim();
    setBusy(true);
    try {
      if (cloud) await publishRoomPost(selectedRoom.id, userId, userName, value);
      else {
        const post: WhappyRoomPost = { id: `demo-post-${Date.now()}`, text: value, authorId: userId, authorName: userName, createdAt: null, reactions: {}, pinned: false, deleted: false };
        setDemoPostState((current) => ({ ...current, [selectedRoom.id]: [...(current[selectedRoom.id] || []), post] }));
        setRooms((current) => current.map((room) => room.id === selectedRoom.id ? { ...room, postCount: room.postCount + 1, lastPost: value.slice(0, 160) } : room));
      }
      setPostText("");
      notify("Publication ajoutée dans la salle");
    } catch { notify("La publication n’a pas pu être envoyée"); }
    finally { setBusy(false); }
  }

  async function react(post: WhappyRoomPost, emoji: string) {
    if (!selectedRoom || !roomIsMember(selectedRoom)) { notify("Suivez la salle pour réagir"); return; }
    try {
      if (cloud) await reactToRoomPost(selectedRoom.id, post.id, userId, emoji);
      else setDemoPostState((current) => ({ ...current, [selectedRoom.id]: (current[selectedRoom.id] || []).map((item) => item.id === post.id ? { ...item, reactions: { ...item.reactions, [userId]: emoji } } : item) }));
    } catch { notify("La réaction n’a pas pu être enregistrée"); }
  }

  async function moderate(post: WhappyRoomPost, action: "pin" | "delete") {
    if (!selectedRoom || selectedRoom.ownerId !== userId) return;
    try {
      if (cloud) { if (action === "pin") await pinRoomPost(selectedRoom.id, post.id, !post.pinned); else await deleteRoomPost(selectedRoom.id, post.id); }
      else setDemoPostState((current) => ({ ...current, [selectedRoom.id]: (current[selectedRoom.id] || []).map((item) => item.id === post.id ? action === "pin" ? { ...item, pinned: !item.pinned } : { ...item, text: "Publication supprimée", deleted: true, pinned: false } : item) }));
      notify(action === "pin" ? (post.pinned ? "Publication désépinglée" : "Publication épinglée") : "Publication masquée");
    } catch { notify("La modération n’a pas pu être enregistrée"); }
  }

  return <div className="space-scroll rooms-space">
    {!selectedRoom ? <>
      <section className="rooms-hero"><div><span className="signal"><i /> WHAPPY ROOMS · COMMUNAUTÉS UTILES</span><h2>Des salles pour<br/><em>se retrouver.</em></h2><p>Une église peut créer une salle de prière. Une communauté peut ouvrir une salle de technologie. Une organisation peut transmettre, aider et faire grandir ses membres.</p><div className="rooms-hero-actions"><button onClick={() => setCreating(true)}>＋ Créer une salle</button><span>Publications des responsables · réactions des membres · règles visibles</span></div></div><aside><span>LES SALLES WHAPPY</span><strong>{rooms.length}</strong><small>espaces à découvrir</small><div><b>{rooms.filter((room) => room.category === "prayer").length}</b><small>foi & prière</small><b>{rooms.filter((room) => room.category === "technology").length}</b><small>tech & éducation</small></div></aside></section>
      <section className="space-content rooms-content"><header className="rooms-heading"><div><small>CHOISIR SON ESPACE</small><h3>Les communautés qui font avancer</h3><p>Des salles publiques, claires et centrées sur un objectif.</p></div><button onClick={() => setCreating(true)}>Créer ma salle ↗</button></header><nav className="rooms-filters"><button className={filter === "all" ? "active" : ""} onClick={() => setFilter("all")}>Toutes</button>{roomKinds.map((kind) => <button className={filter === kind ? "active" : ""} key={kind} onClick={() => setFilter(kind)}>{roomKindLabels[kind]}</button>)}</nav><div className="rooms-grid">{visibleRooms.map((room) => <article className="room-card" key={room.id}><header><span className={`room-avatar ${room.category}`}>{room.emoji}</span><div><small>{roomKindLabels[room.category]} · {roomOrganizerLabels[room.organizerType]}</small><h3>{room.name}</h3></div>{room.verified && <b className="room-verified">✓</b>}</header><p>{room.description}</p><div className="room-meta"><span>◉ {room.memberCount.toLocaleString("fr-FR")} membre{room.memberCount > 1 ? "s" : ""}</span><span>▤ {room.postCount} publication{room.postCount > 1 ? "s" : ""}</span><span>{room.access === "approval" ? "◌ Sur demande" : "◎ Ouverte"}</span></div><div className="room-last"><small>DERNIÈRE ACTUALITÉ</small><span>{room.lastPost || "Aucune publication"}</span></div><footer><button onClick={() => setSelectedRoom(room)}>Ouvrir la salle →</button>{room.ownerId === userId ? <span className="room-owner">Votre salle</span> : <button className={roomIsMember(room) ? "following" : ""} disabled={busy} onClick={() => void toggleSubscription(room)}>{roomIsMember(room) ? "✓ Suivie" : "Suivre"}</button>}</footer></article>)}{!visibleRooms.length && <div className="rooms-empty"><span>◈</span><h3>Aucune salle trouvée</h3><p>Créez la première salle autour d’un besoin réel de votre communauté.</p><button onClick={() => setCreating(true)}>Créer une salle</button></div>}</div></section>
    </> : <section className="space-content room-detail"><button className="room-back" onClick={() => setSelectedRoom(null)}>← Toutes les salles</button><header className="room-detail-head"><div><span className={`room-avatar ${selectedRoom.category}`}>{selectedRoom.emoji}</span><div><small>{roomKindLabels[selectedRoom.category]} · {roomOrganizerLabels[selectedRoom.organizerType]}</small><h2>{selectedRoom.name}{selectedRoom.verified && <b className="room-verified">✓</b>}</h2><p>Par {selectedRoom.ownerName} · {selectedRoom.memberCount.toLocaleString("fr-FR")} membres · {selectedRoom.postCount} publications</p></div></div>{selectedRoom.ownerId !== userId && <button disabled={busy} className={roomIsMember(selectedRoom) ? "following" : ""} onClick={() => void toggleSubscription(selectedRoom)}>{roomIsMember(selectedRoom) ? "✓ Vous suivez cette salle" : "Suivre cette salle"}</button>}</header><div className="room-guidelines"><span>◆ RÈGLES DE LA SALLE</span><p>{selectedRoom.guidelines}</p></div>{selectedRoom.ownerId === userId && <WepiRoomPilot room={selectedRoom} posts={posts} userId={userId} cloud={cloud} notify={notify} onUseSuggestion={setPostText}/>}<div className="room-posts-head"><div><small>FIL DE LA SALLE</small><h3>Les publications de la communauté</h3></div><span>{roomIsMember(selectedRoom) ? "Vous pouvez réagir" : "Suivez la salle pour participer"}</span></div><div className="room-posts">{posts.map((post) => <article className={`room-post ${post.pinned ? "pinned" : ""}`} key={post.id}><header><div><strong>{post.authorName}</strong><small>{postDate(post)} · {post.authorId === selectedRoom.ownerId ? "Responsable" : "Membre"}</small></div>{post.pinned && <b>📌 ÉPINGLÉ</b>}</header><p className={post.deleted ? "deleted" : ""}>{post.deleted ? "Publication supprimée" : post.text}</p>{Object.keys(post.reactions).length > 0 && <div className="room-reactions">{Object.values(post.reactions).map((emoji, index) => <span key={`${emoji}-${index}`}>{emoji}</span>)}</div>} {!post.deleted && <footer><div>{["❤️", "👍", "🔥", "💡"].map((emoji) => <button type="button" key={emoji} onClick={() => void react(post, emoji)}>{emoji}</button>)}</div>{selectedRoom.ownerId === userId && <div><button type="button" onClick={() => void moderate(post, "pin")}>{post.pinned ? "Désépingler" : "Épingler"}</button><button type="button" onClick={() => void moderate(post, "delete")}>Masquer</button></div>}</footer>}</article>)}{!posts.length && <div className="room-posts-empty"><span>◇</span><strong>La salle commence ici</strong><small>{selectedRoom.ownerId === userId ? "Publiez la première actualité pour lancer la conversation." : "Les prochaines publications apparaîtront ici."}</small></div>}</div>{selectedRoom.ownerId === userId && <form className="room-publisher" onSubmit={publishPost}><textarea value={postText} onChange={(event) => setPostText(event.target.value.slice(0, 4000))} rows={3} placeholder="Partager une actualité utile à votre salle…"/><button disabled={busy || !postText.trim()}>{busy ? "Publication…" : "Publier dans la salle ↗"}</button></form>}</section>}
    {creating && <div className="room-modal-layer"><form className="room-modal" onSubmit={createRoom}><button type="button" className="room-modal-close" onClick={() => setCreating(false)} aria-label="Fermer">×</button><small>CRÉER UNE SALLE WHAPPY</small><h2>Donnez un lieu à votre communauté.</h2><p>La salle peut être créée par une église, une organisation, un business ou un créateur. Les membres suivent le fil et réagissent aux publications.</p><label>Nom de la salle<input name="name" required maxLength={80} placeholder="Ex. Salle Espérance · Église La Grâce"/></label><label>Description<textarea name="description" required maxLength={300} rows={3} placeholder="À quoi sert cette salle ? Qui peut la rejoindre ?"/></label><div className="room-form-row"><label>Thème<select name="category" defaultValue="technology">{roomKinds.map((kind) => <option key={kind} value={kind}>{roomKindLabels[kind]}</option>)}</select></label><label>Créée par<select name="organizerType" defaultValue="organization">{organizerTypes.map((kind) => <option key={kind} value={kind}>{roomOrganizerLabels[kind]}</option>)}</select></label></div><div className="room-form-row"><label>Accès<select name="access" defaultValue="public"><option value="public">Ouverte à tous</option><option value="approval">Sur demande</option></select></label><label>Symbole<input name="emoji" defaultValue="◈" maxLength={4}/></label></div><label>Règles visibles<textarea name="guidelines" maxLength={600} rows={3} defaultValue="Respect, écoute et informations utiles pour la communauté."/></label><footer><span>Vous pourrez publier les annonces, épingler les informations importantes et masquer les contenus hors sujet.</span><button disabled={busy}>{busy ? "Création…" : "Créer la salle ↗"}</button></footer></form></div>}
  </div>;
}
