"use client";
/* eslint-disable @next/next/no-img-element -- les stories utilisent des URLs Firebase et des aperçus Blob dynamiques */
/* eslint-disable jsx-a11y/media-has-caption -- les courtes vidéos de story sont créées par les utilisateurs */

import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { publishStory, removeStory, watchStories, type WhappyStory } from "@/lib/whappy-stories";

type StoryItem = WhappyStory & { tone?: string };

const demoStories: StoryItem[] = [
  { id: "demo-amina", authorId: "amina", authorName: "Amina M.", mediaUrl: "", mediaType: "image", caption: "Nouvelles inspirations à Poto-Poto aujourd’hui.", tone: "sunset" },
  { id: "demo-junior", authorId: "junior", authorName: "Junior K.", mediaUrl: "", mediaType: "image", caption: "Livraisons disponibles cet après-midi.", tone: "city" },
  { id: "demo-mokabi", authorId: "mokabi", authorName: "Mokabi Store", mediaUrl: "", mediaType: "video", caption: "La collection arrive ce soir sur WHAPPY Live.", tone: "fashion" },
];

function initials(name: string) {
  return name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "WH";
}

export function StoryStudio({ userId, userName, cloud, notify }: { userId: string; userName: string; cloud: boolean; notify: (text: string) => void }) {
  const [stories, setStories] = useState<StoryItem[]>(cloud ? [] : demoStories);
  const [creatorOpen, setCreatorOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [caption, setCaption] = useState("");
  const [active, setActive] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [railTarget, setRailTarget] = useState<HTMLElement | null>(null);
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);
  useEffect(() => {
    const timer = window.setTimeout(() => setRailTarget(document.querySelector(".moments-feed > .story-line")), 0);
    return () => window.clearTimeout(timer);
  }, []);
  useEffect(() => {
    if (!cloud) return;
    return watchStories(setStories, () => notifyRef.current("Les Stories sont momentanément indisponibles."));
  }, [cloud]);

  const current = active === null ? null : stories[active] || null;
  const orderedStories = useMemo(() => [...stories].sort((a, b) => Number(b.authorId === userId) - Number(a.authorId === userId)), [stories, userId]);

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const next = event.target.files?.[0] || null;
    if (!next) return;
    if (!next.type.startsWith("image/") && !next.type.startsWith("video/")) {
      notify("Choisissez une photo ou une courte vidéo.");
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
    if (!file || busy) return;
    setBusy(true);
    try {
      if (cloud) {
        await publishStory(userId, userName, file, caption);
        notify("Votre Story est publiée pendant 24 heures.");
      } else {
        const local: StoryItem = { id: `local-story-${Date.now()}`, authorId: userId, authorName: userName, mediaUrl: preview, mediaType: file.type.startsWith("video/") ? "video" : "image", caption, expiresAt: { toDate: () => new Date(Date.now() + 24 * 60 * 60 * 1000) } };
        setStories((items) => [local, ...items]);
        notify("Votre Story est visible dans cette démonstration.");
      }
      resetCreator();
    } catch (error) {
      const code = error instanceof Error ? error.message : "";
      notify(code === "story-image-too-large" ? "La photo doit peser moins de 12 Mo." : code === "story-video-too-large" ? "La vidéo doit peser moins de 50 Mo." : code === "story-format" ? "Format accepté : JPG, PNG, WEBP, MP4 ou WEBM." : "La Story n’a pas pu être publiée.");
    } finally {
      setBusy(false);
    }
  }

  async function deleteCurrent() {
    if (!current || current.authorId !== userId || busy) return;
    setBusy(true);
    try {
      if (cloud) await removeStory(current, userId);
      else setStories((items) => items.filter((item) => item.id !== current.id));
      setActive(null);
      notify("Votre Story a été supprimée.");
    } catch {
      notify("La Story n’a pas pu être supprimée.");
    } finally {
      setBusy(false);
    }
  }

  function openStory(story: StoryItem) {
    setActive(stories.findIndex((item) => item.id === story.id));
  }

  const rail = <section className="story-studio" aria-label="Stories Whappy">
      <header><div><small>STORIES</small><strong>Les moments de vos contacts</strong></div><span>24 H</span></header>
      <div className="story-line real-stories">
        <button className="add-story" onClick={() => setCreatorOpen(true)}><span>＋</span><strong>Votre Story</strong><small>Photo ou vidéo</small></button>
        {orderedStories.map((story) => <button key={story.id} onClick={() => openStory(story)}>
          <span className={`story-cover ${story.tone || ""}`} style={story.mediaUrl ? { backgroundImage: `url(${story.mediaUrl})` } : undefined}><i>{initials(story.authorName)}</i>{story.mediaType === "video" && <b>▶</b>}</span>
          <strong>{story.authorId === userId ? "Votre Story" : story.authorName.split(" ")[0]}</strong><small>{story.authorId === userId ? "Publiée" : "Nouveau"}</small>
        </button>)}
        {!orderedStories.length && <div className="stories-empty"><span>✦</span><strong>Aucune Story active</strong><small>Partagez la première.</small></div>}
      </div>
    </section>;

  return <>
    {railTarget ? createPortal(rail, railTarget) : rail}

    {creatorOpen && <div className="story-create-layer" role="dialog" aria-modal="true" aria-labelledby="story-create-title">
      <button className="story-layer-dismiss" onClick={resetCreator} aria-label="Fermer"/>
      <form onSubmit={publish}>
        <header><div><small>NOUVELLE STORY</small><h3 id="story-create-title">Partagez votre moment</h3></div><button type="button" onClick={resetCreator} aria-label="Fermer">×</button></header>
        <label className={`story-drop ${preview ? "ready" : ""}`}>
          {preview ? file?.type.startsWith("video/") ? <video src={preview} controls playsInline/> : <img src={preview} alt="Aperçu de votre Story"/> : <><span>＋</span><strong>Choisir une photo ou une vidéo</strong><small>JPG, PNG, WEBP, MP4 ou WEBM</small></>}
          <input type="file" accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" onChange={chooseFile}/>
        </label>
        <label className="story-caption">Légende<textarea value={caption} onChange={(event) => setCaption(event.target.value.slice(0, 180))} maxLength={180} spellCheck lang="fr" placeholder="Ajoutez quelques mots…"/><small>{caption.length}/180</small></label>
        <div className="story-publish-note"><span>◷</span><p><strong>Visible pendant 24 heures</strong><small>Vous pourrez la supprimer à tout moment.</small></p></div>
        <button className="story-publish" disabled={!file || busy}>{busy ? "Publication…" : "Publier la Story →"}</button>
      </form>
    </div>}

    {current && <div className="story-viewer" role="dialog" aria-modal="true" aria-label={`Story de ${current.authorName}`}>
      <header><div className="story-progress">{stories.map((story, index) => <i className={index <= (active || 0) ? "seen" : ""} key={story.id}/>)}</div><section><span>{initials(current.authorName)}</span><div><strong>{current.authorName}</strong><small>Story · visible 24 h</small></div>{current.authorId === userId && <button onClick={() => void deleteCurrent()} disabled={busy}>Supprimer</button>}<button onClick={() => setActive(null)} aria-label="Fermer">×</button></section></header>
      <main className={current.tone || ""}>{current.mediaUrl ? current.mediaType === "video" ? <video src={current.mediaUrl} controls autoPlay playsInline/> : <img src={current.mediaUrl} alt={`Story de ${current.authorName}`}/> : <div className="story-demo-visual"><span>{initials(current.authorName)}</span><strong>WHAPPY STORY</strong></div>}{current.caption && <p>{current.caption}</p>}</main>
      <button className="story-previous" onClick={() => setActive((value) => value === null ? null : Math.max(0, value - 1))} disabled={active === 0} aria-label="Story précédente">‹</button>
      <button className="story-next" onClick={() => setActive((value) => value === null ? null : value >= stories.length - 1 ? null : value + 1)} aria-label="Story suivante">›</button>
    </div>}
  </>;
}
