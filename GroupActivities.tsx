"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { saveGroupActivityResponse, watchGroupActivityResponses, type CloudGroupActivity, type CloudGroupActivityResponse, type GroupActivityType } from "@/lib/whappy-data";

export type ActivityDraft = Pick<CloudGroupActivity,"type"|"title"|"details"|"options"|"eventDate">;

export function GroupActivities({ groupId, userId, cloud, items, busy, onCreate, notify }: {
  groupId:string;
  userId:string;
  cloud:boolean;
  items:CloudGroupActivity[];
  busy:boolean;
  onCreate:(draft:ActivityDraft)=>Promise<void>;
  notify:(text:string)=>void;
}) {
  const [type,setType]=useState<GroupActivityType|null>(null);
  const [responses,setResponses]=useState<Record<string,CloudGroupActivityResponse[]>>({});
  const [responding,setResponding]=useState("");
  const notifyRef=useRef(notify);
  useEffect(()=>{notifyRef.current=notify;},[notify]);
  useEffect(()=>{
    if(!cloud||!userId||!groupId)return;
    const stops=items.filter((item)=>item.type!=="announcement").map((item)=>watchGroupActivityResponses(groupId,item.id,(values)=>setResponses((current)=>({...current,[item.id]:values})),()=>notifyRef.current("Les réponses communautaires sont momentanément hors ligne")));
    return ()=>stops.forEach((stop)=>stop());
  },[cloud,userId,groupId,items]);

  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!type)return;
    const form=new FormData(event.currentTarget);
    const options=String(form.get("options")||"").split(";").map((item)=>item.trim()).filter(Boolean);
    if(type==="poll"&&options.length<2){notify("Ajoutez au moins deux choix au sondage");return;}
    await onCreate({type,title:String(form.get("title")||""),details:String(form.get("details")||""),options,eventDate:String(form.get("eventDate")||"")});
    setType(null);
  }

  async function respond(activity:CloudGroupActivity,response:Omit<CloudGroupActivityResponse,"id"|"userId">){
    const memberId=userId||"local";
    setResponding(activity.id);
    try{
      if(cloud&&userId)await saveGroupActivityResponse(groupId,activity.id,userId,response);
      else setResponses((current)=>({...current,[activity.id]:[...(current[activity.id]||[]).filter((item)=>item.userId!==memberId),{...response,id:memberId,userId:memberId}]}));
      notify(response.kind==="poll"?"Votre vote est enregistré":"Votre participation est confirmée");
    }catch{notify("Votre réponse n’a pas pu être enregistrée. Réessayez.");}
    finally{setResponding("");}
  }

  const memberId=userId||"local";
  return <section className="community-board"><header><div><small>COMMUNAUTÉ</small><strong>Organiser le groupe</strong></div><span/><button onClick={()=>setType("poll")}>▥ Sondage</button><button onClick={()=>setType("event")}>◷ Événement</button><button onClick={()=>setType("announcement")}>◆ Annonce</button></header><div className="community-feed">{items.map((activity)=>{
    const activityResponses=responses[activity.id]||[];
    const mine=activityResponses.find((response)=>response.userId===memberId);
    return <article className={activity.type} key={activity.id}><header><span>{activity.type==="poll"?"▥":activity.type==="event"?"◷":"◆"}</span><div><small>{activity.type==="poll"?"SONDAGE":activity.type==="event"?"ÉVÉNEMENT":"ANNONCE ADMIN"}</small><strong>{activity.title}</strong></div></header>{activity.details&&<p>{activity.details}</p>}{activity.type==="poll"&&<div className="poll-options">{activity.options.map((option,index)=>{const count=activityResponses.filter((response)=>response.optionIndex===index).length;return <button disabled={responding===activity.id} className={mine?.optionIndex===index?"selected":""} key={option} onClick={()=>respond(activity,{kind:"poll",optionIndex:index})}><span>{option}</span><b>{count} vote{count>1?"s":""}</b></button>})}</div>}{activity.type==="event"&&<div className="event-date"><span>◷</span><strong>{activity.eventDate?new Date(activity.eventDate).toLocaleString("fr-FR",{dateStyle:"long",timeStyle:"short"}):"Date à confirmer"}</strong><button className={mine?.attending?"confirmed":""} disabled={responding===activity.id} onClick={()=>respond(activity,{kind:"event",attending:true})}>{mine?.attending?`✓ Inscrit · ${activityResponses.filter((response)=>response.attending).length}`:"Je participe"}</button></div>}<footer>Publié par {activity.creatorName}{activity.type!=="announcement"&&` · ${activityResponses.length} réponse${activityResponses.length>1?"s":""}`}</footer></article>
  })}{!items.length&&<div className="community-empty"><span>✦</span><strong>Animez votre communauté</strong><small>Créez un sondage, un événement ou une annonce.</small></div>}</div>{type&&<div className="activity-modal"><button className="activity-dismiss" onClick={()=>setType(null)} aria-label="Fermer"/><form onSubmit={submit}><header><div><small>COMMUNAUTÉ WHAPPY</small><h3>{type==="poll"?"Créer un sondage":type==="event"?"Planifier un événement":"Publier une annonce"}</h3></div><button type="button" onClick={()=>setType(null)}>×</button></header><label>{type==="poll"?"Question":type==="event"?"Nom de l’événement":"Titre de l’annonce"}<input name="title" required minLength={2} maxLength={160} placeholder="Donnez un titre clair"/></label><label>Détails<textarea name="details" maxLength={1000} placeholder="Ajoutez les informations utiles…"/></label>{type==="poll"&&<label>Choix séparés par un point-virgule<input name="options" required placeholder="Option 1 ; Option 2 ; Option 3"/></label>}{type==="event"&&<label>Date et heure<input name="eventDate" required type="datetime-local"/></label>}<button className="activity-submit" disabled={busy}>{busy?"Publication…":"Publier dans le groupe →"}</button></form></div>}</section>;
}
