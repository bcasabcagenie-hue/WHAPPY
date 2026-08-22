"use client";
/* eslint-disable @next/next/no-img-element -- les stories utilisent des URLs Firebase et des aperçus Blob dynamiques */
/* eslint-disable jsx-a11y/media-has-caption -- les courtes vidéos de story sont créées par les utilisateurs */

import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { createPortal } from "react-dom";
import { publishStory, removeStory, watchStories, type WhappyStory } from "@/lib/whappy-stories";
import { readWhappyCache, writeWhappyCache } from "@/lib/whappy-local-cache";

type StoryItem = WhappyStory;
type StoryGroup = { authorId: string; authorName: string; stories: StoryItem[] };

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH";
}

function localMediaKind(file: File | null) {
  if (!file) return "text" as const;
  const extension = file.name.toLowerCase().split(".").pop() || "";
  if (file.type.startsWith("audio/") || ["mp3", "m4a", "aac", "ogg", "wav"].includes(extension)) return "audio" as const;
  if (file.type.startsWith("video/") || ["mp4", "webm"].includes(extension)) return "video" as const;
  return "image" as const;
}

function timestampMillis(value: WhappyStory["expiresAt"]) {
  if (value?.toDate) return value.toDate().getTime();
  if (typeof value?.seconds === "number") return value.seconds * 1000 + Math.floor((value.nanoseconds || 0) / 1_000_000);
  return Date.now() + 1;
}

export function StoryStudio({ userId, userName, cloud, notify }: { userId: string; userName: string; cloud: boolean; notify: (text: string) => void }) {
  const [stories, setStories] = useState<StoryItem[]>(() => cloud ? [] : [
    { id: "demo-amina", authorId: "demo-amina", authorName: "Amina M.", mediaUrl: "", mediaType: "text", caption: "Une belle journée commence par une bonne idée ✨", createdAt: { seconds: Math.floor(Date.now() / 1000) - 900 }, expiresAt: { seconds: Math.floor(Date.now() / 1000) + 23 * 3600 } },
    { id: "demo-junior", authorId: "demo-junior", authorName: "Junior K.", mediaUrl: "", mediaType: "text", caption: "Disponible pour vos projets aujourd’hui.", createdAt: { seconds: Math.floor(Date.now() / 1000) - 3600 }, expiresAt: { seconds: Math.floor(Date.now() / 1000) + 21 * 3600 } },
  ]);
  const [creatorOpen, setCreatorOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [caption, setCaption] = useState("");
  const [activeAuthorId, setActiveAuthorId] = useState<string | null>(null);
  const [activeItem, setActiveItem] = useState(0);
  const [viewedIds, setViewedIds] = useState<Set<string>>(() => new Set());
  const [muted, setMuted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [offline, setOffline] = useState(false);
  const [loading, setLoading] = useState(cloud);
  const [railTarget, setRailTarget] = useState<HTMLElement | null>(null);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    if (cloud) return;
    try {
      const cached = JSON.parse(localStorage.getItem("whappy-demo-stories") || "null") as StoryItem[] | null;
      if (Array.isArray(cached) && cached.length) setStories(cached.filter((story) => timestampMillis(story.expiresAt) > Date.now()));
    } catch { /* L’aperçu reste utilisable sans stockage local. */ }
  }, [cloud]);
  useEffect(() => {
    if (cloud) return;
    try { localStorage.setItem("whappy-demo-stories", JSON.stringify(stories)); } catch { /* Stockage privé indisponible. */ }
  }, [cloud, stories]);
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(`whappy-story-views-${userId}`) || "[]") as string[];
      if (Array.isArray(saved)) setViewedIds(new Set(saved));
    } catch { /* Les indicateurs de lecture restent optionnels. */ }
  }, [userId]);
  useEffect(() => {
    try { localStorage.setItem(`whappy-story-views-${userId}`, JSON.stringify(Array.from(viewedIds))); } catch { /* Stockage privé indisponible. */ }
  }, [userId, viewedIds]);
  useEffect(() => {
    const timer = window.setTimeout(() => setRailTarget(document.querySelector<HTMLElement>(".moments-feed > .story-line")), 0);
    return () => window.clearTimeout(timer);
  }, []);
  useEffect(() => {
    if (!cloud) return;
    let active = true;
    const cached = readWhappyCache<StoryItem[]>("stories", userId, 25 * 60 * 60 * 1000);
    if (cached?.length) {
      queueMicrotask(() => {
        if (!active) return;
        setStories(cached.filter((story) => timestampMillis(story.expiresAt) > Date.now()));
        setLoading(false);
      });
    }
    const unsubscribe = watchStories(userId, (next) => { setStories(next); setLoading(false); setOffline(false); }, () => {
      setLoading(false);
      setOffline(true);
      // Keep the last confirmed Stories visible. The rail already shows HORS LIGNE.
    });
    return () => { active = false; unsubscribe(); };
  }, [cloud, userId]);

  useEffect(() => {
    if (cloud && !loading) writeWhappyCache("stories", userId, stories);
  }, [cloud, loading, stories, userId]);

  useEffect(() => () => {
    if (preview.startsWith("blob:")) URL.revokeObjectURL(preview);
  }, [preview]);

  const activeStories = useMemo(() => stories.filter((story) => timestampMillis(story.expiresAt) > Date.now()), [stories]);
  const storyGroups = useMemo<StoryGroup[]>(() => {
    const grouped = new Map<string, StoryItem[]>();
    activeStories.forEach((story) => grouped.set(story.authorId, [...(grouped.get(story.authorId) || []), story]));
    return Array.from(grouped.entries()).map(([authorId, items]) => ({
      authorId,
      authorName: items[0]?.authorName || "WAPI",
      stories: items.sort((a, b) => timestampMillis(a.createdAt) - timestampMillis(b.createdAt)),
    })).sort((a, b) => {
      if (a.authorId === userId) return -1;
      if (b.authorId === userId) return 1;
      return timestampMillis(b.stories[b.stories.length - 1]?.createdAt) - timestampMillis(a.stories[a.stories.length - 1]?.createdAt);
    });
  }, [activeStories, userId]);
  const currentGroup = activeAuthorId ? storyGroups.find((group) => group.authorId === activeAuthorId) || null : null;
  const current = currentGroup?.stories[activeItem] || null;
  const previewKind = localMediaKind(file);

  useEffect(() => {
    if (!current || !currentGroup) return;
    if (current.mediaType === "video" || current.mediaType === "audio") return;
    const timer = window.setTimeout(() => {
      if (activeItem < currentGroup.stories.length - 1) setActiveItem((value) => value + 1);
      else setActiveAuthorId(null);
    }, 6000);
    return () => window.clearTimeout(timer);
  }, [activeItem, current, currentGroup]);

  useEffect(() => {
    function closeWithEscape(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      if (creatorOpen) resetCreator();
      else if (activeAuthorId !== null) setActiveAuthorId(null);
    }
    window.addEventListener("keydown", closeWithEscape);
    return () => window.removeEventListener("keydown", closeWithEscape);
  }, [creatorOpen, activeAuthorId]);

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const next = event.target.files?.[0] || null;
    if (!next) return;
    const extension = next.name.toLowerCase().split(".").pop() || "";
    const supported = next.type.startsWith("image/") || next.type.startsWith("video/") || next.type.startsWith("audio/") || ["jpg", "jpeg", "png", "webp", "mp4", "webm", "mp3", "m4a", "aac", "ogg", "wav"].includes(extension);
    if (!supported) {
      notify("Choisissez une photo, une courte vidéo ou un fichier audio.");
      return;
    }
    setFile(next);
    setPreview(URL.createObjectURL(next));
  }

  function resetCreator() {
    setCreatorOpen(false);
    setFile(null);
    setPreview("");
    setCaption("");
  }

  async function publish(event: FormEvent) {
    event.preventDefault();
    if ((!file && !caption.trim()) || busy) return;
    if (!cloud) {
      const now = Math.floor(Date.now() / 1000);
      const localStory: StoryItem = { id: `local-${Date.now()}`, authorId: userId, authorName: userName, mediaUrl: preview, mediaType: file ? previewKind : "text", caption: caption.trim(), createdAt: { seconds: now }, expiresAt: { seconds: now + 24 * 3600 } };
      setStories((current) => [localStory, ...current]);
      setActiveItem(0);
      setActiveAuthorId(userId);
      notify("Votre Story est publiée dans cet aperçu pendant 24 heures.");
      resetCreator();
      return;
    }
    setBusy(true);
    try {
      const created = await publishStory(userId, userName, file, caption);
      setStories((current) => [created, ...current.filter((story) => story.id !== created.id)]);
      // Match the native Story behavior: a successful post immediately opens
      // the owner's Story rather than leaving an empty-looking rail behind.
      setActiveItem(0);
      setActiveAuthorId(userId);
      notify("Votre Story est publiée pendant 24 heures.");
      resetCreator();
    } catch (error) {
      const code = error instanceof Error ? error.message : "";
      notify(code === "story-image-too-large" ? "La photo doit peser moins de 12 Mo." : code === "story-video-too-large" ? "La vidéo doit peser moins de 50 Mo." : code === "story-audio-too-large" ? "L’audio doit peser moins de 25 Mo." : code === "story-format" ? "Format accepté : photo, vidéo MP4/WEBM ou audio MP3/M4A." : "La Story n’a pas pu être publiée.");
    } finally {
      setBusy(false);
    }
  }

  async function deleteCurrent() {
    if (!current || current.authorId !== userId || busy) return;
    setBusy(true);
    try {
      await removeStory(current, userId);
      setStories((items) => items.filter((story) => story.id !== current.id));
      if ((currentGroup?.stories.length || 0) <= 1) setActiveAuthorId(null);
      else if (activeItem >= (currentGroup?.stories.length || 1) - 1) setActiveItem((value) => Math.max(0, value - 1));
      notify("Votre Story a été supprimée.");
    } catch {
      notify("La Story n’a pas pu être supprimée.");
    } finally {
      setBusy(false);
    }
  }

  function openStory(group: StoryGroup) {
    setActiveAuthorId(group.authorId);
    setActiveItem(0);
    setViewedIds((current) => new Set([...current, ...group.stories.map((story) => story.id)]));
  }

  const rail = <section className="story-studio" aria-label="Stories Whappy">
      <header><div><small>STORIES</small><strong>Les moments de vos contacts</strong></div><button className="story-header-action" type="button" onClick={() => setCreatorOpen(true)}>＋ Créer</button><span className={loading ? "loading" : offline ? "offline" : ""}>{loading ? "CHARGEMENT" : offline ? "HORS LIGNE" : "24 H"}</span></header>
      <div className="story-line real-stories">
        <button className="add-story" onClick={() => setCreatorOpen(true)}><span>＋</span><strong>Votre Story</strong><small>Photo ou vidéo</small></button>
        {loading && [1, 2, 3].map((item) => <div className="story-loading-card" key={item}><span/><strong/><small/></div>)}
        {storyGroups.map((group) => { const story = group.stories[group.stories.length - 1]; return <button key={group.authorId} onClick={() => openStory(group)} aria-label={`Voir les Stories de ${group.authorName}`}>
          <span className={`story-cover ${group.stories.every((item) => viewedIds.has(item.id)) ? "seen" : ""}`} style={story.mediaType === "image" && story.mediaUrl ? { backgroundImage: `url(${story.mediaUrl})` } : undefined}><i>{initials(group.authorName)}</i>{story.mediaType === "video" && <b>▶</b>}{story.mediaType === "audio" && <b>◖</b>}</span>
          <strong>{group.authorId === userId ? "Votre Story" : group.authorName.split(" ")[0]}</strong><small>{group.stories.length > 1 ? `${group.stories.length} Stories` : group.authorId === userId ? "Publiée" : "Nouveau"}</small>
        </button>; })}
        {!loading && !storyGroups.length && <button className="stories-empty" onClick={() => setCreatorOpen(true)}><span>✦</span><strong>Aucune Story active</strong><small>Partagez la première →</small></button>}
      </div>
    </section>;

  return <>
    {railTarget ? createPortal(rail, railTarget) : rail}

    {creatorOpen && <div className="story-create-layer" role="dialog" aria-modal="true" aria-labelledby="story-create-title">
      <button className="story-layer-dismiss" onClick={resetCreator} aria-label="Fermer"/>
      <form onSubmit={publish}>
        <header><div><small>NOUVELLE STORY</small><h3 id="story-create-title">Partagez votre moment</h3></div><button type="button" onClick={resetCreator} aria-label="Fermer">×</button></header>
        <div className="story-create-choice" aria-label="Choisir la source de la Story">
          <label><span>⌾</span><strong>Caméra</strong><small>Prendre maintenant</small><input type="file" accept="image/*,video/*" capture="environment" onChange={chooseFile}/></label>
          <label><span>▧</span><strong>Galerie</strong><small>Choisir un média</small><input type="file" accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" onChange={chooseFile}/></label>
          <label><span>◖</span><strong>Audio</strong><small>Podcast ou extrait</small><input type="file" accept="audio/mpeg,audio/mp4,audio/aac,audio/ogg,audio/wav" onChange={chooseFile}/></label>
        </div>
        <label className={`story-drop ${preview ? "ready" : ""}`}>
          {preview ? previewKind === "audio" ? <audio src={preview} controls/> : previewKind === "video" ? <video src={preview} controls playsInline/> : <img src={preview} alt="Aperçu de votre Story"/> : <><span>＋</span><strong>Choisir une photo, une vidéo ou un audio</strong><small>JPG, PNG, WEBP, MP4, WEBM, MP3 ou M4A</small></>}
          <input type="file" accept="image/jpeg,image/png,image/webp,video/mp4,video/webm,audio/mpeg,audio/mp4,audio/aac,audio/ogg,audio/wav" onChange={chooseFile}/>
        </label>
        <label className="story-caption">Légende<textarea value={caption} onChange={(event) => setCaption(event.target.value.slice(0, 180))} maxLength={180} spellCheck lang="fr" placeholder="Ajoutez quelques mots…"/><small>{caption.length}/180</small></label>
        <div className="story-publish-note"><span>◷</span><p><strong>Visible pendant 24 heures</strong><small>Vous pourrez la supprimer à tout moment.</small></p></div>
        <button className="story-publish" disabled={(!file && !caption.trim()) || busy}>{busy ? "Publication…" : "Publier la Story →"}</button>
      </form>
    </div>}

    {current && currentGroup && <div className="story-viewer" role="dialog" aria-modal="true" aria-label={`Story de ${current.authorName}`}>
      <header><div className="story-progress">{currentGroup.stories.map((story, index) => <i className={index < activeItem ? "complete" : index === activeItem ? "active" : ""} style={{ "--story-duration": current.mediaType === "video" ? "10s" : "6s" } as CSSProperties} key={`${story.id}-${index === activeItem ? "active" : "idle"}`}/>)}</div><section><span>{initials(current.authorName)}</span><div><strong>{current.authorName}</strong><small>Story {activeItem + 1}/{currentGroup.stories.length} · visible 24 h</small></div>{current.authorId === userId && <button onClick={() => void deleteCurrent()} disabled={busy}>Supprimer</button>}<button onClick={() => setActiveAuthorId(null)} aria-label="Fermer">×</button></section></header>
      <main>{current.mediaUrl ? current.mediaType === "audio" ? <div className="story-audio-visual"><span>◖</span><strong>{current.authorName}</strong><audio src={current.mediaUrl} controls autoPlay onEnded={() => activeItem >= currentGroup.stories.length - 1 ? setActiveAuthorId(null) : setActiveItem((value) => value + 1)}/></div> : current.mediaType === "video" ? <video src={current.mediaUrl} controls autoPlay muted={muted} playsInline onEnded={() => activeItem >= currentGroup.stories.length - 1 ? setActiveAuthorId(null) : setActiveItem((value) => value + 1)}><track kind="captions" /></video> : <img src={current.mediaUrl} alt={`Story de ${current.authorName}`}/> : <div className="story-text-visual"><span>{initials(current.authorName)}</span><strong>WAPI STORY</strong></div>}{current.caption && <p>{current.caption}</p>}</main>
      {current.mediaType === "video" && <div className="story-actions"><button type="button" onClick={() => setMuted((value) => !value)}>{muted ? "🔇 Activer le son" : "🔊 Couper le son"}</button></div>}
      <button className="story-previous" onClick={() => setActiveItem((value) => Math.max(0, value - 1))} disabled={activeItem === 0} aria-label="Story précédente">‹</button>
      <button className="story-next" onClick={() => activeItem >= currentGroup.stories.length - 1 ? setActiveAuthorId(null) : setActiveItem((value) => value + 1)} aria-label="Story suivante">›</button>
    </div>}
  </>;
}
