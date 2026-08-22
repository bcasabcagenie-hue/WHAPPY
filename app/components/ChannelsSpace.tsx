"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  createWhappyRoom,
  deleteRoomPost,
  pinRoomPost,
  publishRoomPost,
  reactToRoomPost,
  roomKindLabels,
  setRoomSubscription,
  watchRoomPosts,
  watchWhappyRooms,
  type RoomKind,
  type WhappyRoom,
  type WhappyRoomPost,
} from "@/lib/whappy-rooms";

const channelKinds = Object.keys(roomKindLabels) as RoomKind[];

function postDate(post: WhappyRoomPost) {
  const date = post.createdAt?.toDate?.();
  return date ? date.toLocaleDateString("fr-FR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "À l’instant";
}

export function ChannelsSpace({ userId, userName, search, cloud, notify, onOpenGroupSpaces }: { userId: string; userName: string; search: string; cloud: boolean; notify: (text: string) => void; onOpenGroupSpaces: () => void }) {
  const [channels, setChannels] = useState<WhappyRoom[]>([]);
  const [selected, setSelected] = useState<WhappyRoom | null>(null);
  const [posts, setPosts] = useState<WhappyRoomPost[]>([]);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(cloud);
  const [postText, setPostText] = useState("");
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    if (!cloud) {
      queueMicrotask(() => { setChannels([]); setLoading(false); });
      return;
    }
    queueMicrotask(() => setLoading(true));
    return watchWhappyRooms((next) => { setChannels(next); setLoading(false); }, () => {
      setLoading(false);
      notifyRef.current("Les chaînes ne peuvent pas être synchronisées pour le moment");
    });
  }, [cloud]);
  useEffect(() => {
    if (!selected || !cloud) {
      queueMicrotask(() => setPosts([]));
      return;
    }
    return watchRoomPosts(selected.id, setPosts, () => notifyRef.current("Le fil de cette chaîne est momentanément indisponible"));
  }, [cloud, selected]);

  const visibleChannels = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return channels.filter((channel) => !query || `${channel.name} ${channel.description} ${channel.ownerName} ${roomKindLabels[channel.category]}`.toLocaleLowerCase("fr").includes(query));
  }, [channels, search]);

  function follows(channel: WhappyRoom) {
    return channel.ownerId === userId || channel.memberIds.includes(userId);
  }

  async function createChannel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!cloud) { notify("Connectez-vous pour créer une chaîne synchronisée."); return; }
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") || "").trim();
    const description = String(form.get("description") || "").trim();
    if (name.length < 3 || description.length < 10) { notify("Ajoutez un nom et une description claire."); return; }
    setBusy(true);
    try {
      await createWhappyRoom(userId, userName, {
        name,
        description,
        category: String(form.get("category") || "culture") as RoomKind,
        organizerType: "creator",
        access: "public",
        guidelines: "Les publications viennent du propriétaire de la chaîne. Les abonnés peuvent réagir dans le respect des règles WAPI.",
        emoji: String(form.get("emoji") || "▤"),
      });
      setCreating(false);
      notify("Votre chaîne est en ligne.");
    } catch { notify("La chaîne n’a pas pu être créée."); }
    finally { setBusy(false); }
  }

  async function toggleFollow(channel: WhappyRoom) {
    if (!cloud) { notify("Connectez-vous pour suivre cette chaîne."); return; }
    const subscribed = follows(channel);
    if (channel.ownerId === userId) return;
    setBusy(true);
    try {
      await setRoomSubscription(channel.id, userId, !subscribed);
      notify(subscribed ? "Chaîne retirée de vos abonnements." : "Chaîne ajoutée à vos abonnements.");
    } catch { notify("L’abonnement n’a pas pu être modifié."); }
    finally { setBusy(false); }
  }

  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || selected.ownerId !== userId || !postText.trim()) return;
    setBusy(true);
    try {
      await publishRoomPost(selected.id, userId, userName, postText);
      setPostText("");
      notify("Publication envoyée à vos abonnés.");
    } catch { notify("La publication n’a pas pu être envoyée."); }
    finally { setBusy(false); }
  }

  async function react(post: WhappyRoomPost, emoji: string) {
    if (!selected || !follows(selected)) { notify("Suivez la chaîne pour réagir."); return; }
    try { await reactToRoomPost(selected.id, post.id, userId, emoji); }
    catch { notify("La réaction n’a pas pu être enregistrée."); }
  }

  async function moderate(post: WhappyRoomPost, action: "pin" | "delete") {
    if (!selected || selected.ownerId !== userId) return;
    try {
      if (action === "pin") await pinRoomPost(selected.id, post.id, !post.pinned);
      else await deleteRoomPost(selected.id, post.id);
    } catch { notify("La publication n’a pas pu être modifiée."); }
  }

  return <div className="space-scroll rooms-space channels-space">
    {!selected ? <>
      <section className="rooms-hero"><div><span className="signal"><i /> WAPI CHAÎNES · PUBLICATIONS OFFICIELLES</span><h2>Suivez les voix<br/><em>qui comptent.</em></h2><p>Une chaîne est un fil éditorial : le propriétaire publie, les abonnés lisent et réagissent. Elle reste distincte des Stories et des groupes de discussion.</p><div className="rooms-hero-actions"><button onClick={() => setCreating(true)}>＋ Créer ma chaîne</button><button type="button" onClick={onOpenGroupSpaces}>Espaces de groupe</button><span>Pas de données de démonstration · synchronisation Firebase</span></div></div><aside><span>CHAÎNES DISPONIBLES</span><strong>{channels.length}</strong><small>médias et créateurs</small><div><b>{channels.filter((item) => follows(item)).length}</b><small>suivies</small><b>{channels.filter((item) => item.ownerId === userId).length}</b><small>à vous</small></div></aside></section>
      <section className="space-content rooms-content"><header className="rooms-heading"><div><small>VOTRE SÉLECTION</small><h3>Chaînes WAPI</h3><p>Des publications organisées, séparées de vos conversations privées.</p></div><button onClick={() => setCreating(true)}>Créer une chaîne ↗</button></header><div className="rooms-grid">
        {visibleChannels.map((channel) => <article className="room-card" key={channel.id}><header><span className={`room-avatar ${channel.category}`}>{channel.emoji || "▤"}</span><div><small>{roomKindLabels[channel.category]} · par {channel.ownerName}</small><h3>{channel.name}</h3></div>{channel.verified && <b className="room-verified">✓</b>}</header><p>{channel.description}</p><div className="room-meta"><span>◎ {channel.memberCount.toLocaleString("fr-FR")} abonné{channel.memberCount > 1 ? "s" : ""}</span><span>▤ {channel.postCount} publication{channel.postCount > 1 ? "s" : ""}</span></div><div className="room-last"><small>DERNIÈRE PUBLICATION</small><span>{channel.lastPost || "Cette chaîne n’a pas encore publié."}</span></div><footer><button onClick={() => setSelected(channel)}>Ouvrir →</button>{channel.ownerId === userId ? <span className="room-owner">Votre chaîne</span> : <button className={follows(channel) ? "following" : ""} disabled={busy} onClick={() => void toggleFollow(channel)}>{follows(channel) ? "✓ Suivie" : "Suivre"}</button>}</footer></article>)}
        {!loading && !visibleChannels.length && <div className="rooms-empty"><span>▤</span><h3>{cloud ? "Aucune chaîne trouvée" : "Connexion nécessaire"}</h3><p>{cloud ? "Créez la première chaîne éditoriale WAPI." : "Les chaînes réelles apparaissent après votre connexion."}</p>{cloud && <button onClick={() => setCreating(true)}>Créer ma chaîne</button>}</div>}
        {loading && <div className="rooms-empty"><span>◌</span><h3>Synchronisation…</h3><p>WAPI récupère les chaînes auxquelles votre compte peut accéder.</p></div>}
      </div></section>
    </> : <section className="space-content room-detail"><button className="room-back" onClick={() => setSelected(null)}>← Toutes les chaînes</button><header className="room-detail-head"><div><span className={`room-avatar ${selected.category}`}>{selected.emoji || "▤"}</span><div><small>CHAÎNE · {roomKindLabels[selected.category]}</small><h2>{selected.name}{selected.verified && <b className="room-verified">✓</b>}</h2><p>Par {selected.ownerName} · {selected.memberCount.toLocaleString("fr-FR")} abonnés</p></div></div>{selected.ownerId !== userId && <button disabled={busy} className={follows(selected) ? "following" : ""} onClick={() => void toggleFollow(selected)}>{follows(selected) ? "✓ Vous suivez" : "Suivre la chaîne"}</button>}</header><div className="room-posts-head"><div><small>FIL OFFICIEL</small><h3>Publications de la chaîne</h3></div><span>{follows(selected) ? "Réactions activées" : "Suivez pour réagir"}</span></div><div className="room-posts">
      {posts.map((post) => <article className={`room-post ${post.pinned ? "pinned" : ""}`} key={post.id}><header><div><strong>{post.authorName}</strong><small>{postDate(post)} · Publication officielle</small></div>{post.pinned && <b>📌 ÉPINGLÉ</b>}</header><p className={post.deleted ? "deleted" : ""}>{post.deleted ? "Publication supprimée" : post.text}</p>{!post.deleted && <footer><div>{["❤️", "👍", "🔥", "👏"].map((emoji) => <button type="button" key={emoji} onClick={() => void react(post, emoji)}>{emoji}</button>)}</div>{selected.ownerId === userId && <div><button type="button" onClick={() => void moderate(post, "pin")}>{post.pinned ? "Désépingler" : "Épingler"}</button><button type="button" onClick={() => void moderate(post, "delete")}>Supprimer</button></div>}</footer>}</article>)}
      {!posts.length && <div className="room-posts-empty"><span>▤</span><strong>Le fil commence ici</strong><small>{selected.ownerId === userId ? "Publiez votre première actualité." : "Les prochaines publications apparaîtront ici."}</small></div>}
    </div>{selected.ownerId === userId && <form className="room-publisher" onSubmit={publish}><textarea value={postText} onChange={(event) => setPostText(event.target.value.slice(0, 4000))} rows={3} placeholder="Publier une actualité à vos abonnés…"/><button disabled={busy || !postText.trim()}>{busy ? "Publication…" : "Publier ↗"}</button></form>}</section>}

    {creating && <div className="room-modal-layer"><form className="room-modal" onSubmit={createChannel}><button type="button" className="room-modal-close" onClick={() => setCreating(false)} aria-label="Fermer">×</button><small>CRÉER UNE CHAÎNE WAPI</small><h2>Votre média, dans WAPI.</h2><p>Cette chaîne sera séparée de vos Stories, groupes et messages privés.</p><label>Nom de la chaîne<input name="name" required maxLength={80} placeholder="Ex. WAPI Tech Congo"/></label><label>Description<textarea name="description" required maxLength={300} rows={3} placeholder="Que publiera cette chaîne ?"/></label><div className="room-form-row"><label>Thème<select name="category" defaultValue="culture">{channelKinds.map((kind) => <option key={kind} value={kind}>{roomKindLabels[kind]}</option>)}</select></label><label>Symbole<input name="emoji" defaultValue="▤" maxLength={4}/></label></div><footer><span>Vous seul publierez. Les abonnés pourront suivre et réagir.</span><button disabled={busy}>{busy ? "Création…" : "Créer la chaîne ↗"}</button></footer></form></div>}
  </div>;
}
