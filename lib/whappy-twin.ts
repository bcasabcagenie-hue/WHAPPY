import { addDoc, collection, deleteDoc, doc, onSnapshot, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "@/lib/firebase";

export type TwinAssetKind="video"|"voice"|"movement";
export type TwinGesture="neutral"|"welcome"|"explain"|"point"|"show"|"wave"|"walk";
export type TwinAutomation={id:string;userId:string;name:string;trigger:string;channel:string;action:string;script:string;enabled:boolean;createdAt?:{toDate?:()=>Date}|null};
export type TwinProfile={
  id:string;userId:string;displayName:string;identityConsent:boolean;voiceConsent:boolean;movementConsent:boolean;videoUrl?:string;voiceUrl?:string;movementUrl?:string;voiceStatus:"empty"|"sampled"|"ready";movementStatus:"empty"|"sampled"|"ready";updatedAt?:{toDate?:()=>Date}|null;
};
export type TwinSequenceStep={gesture:TwinGesture;duration:number;label:string};

export function watchTwinProfile(userId:string,onProfile:(profile:TwinProfile|null)=>void,onError:()=>void){
  return onSnapshot(doc(db,"users",userId,"twinProfiles","main"),(snapshot)=>onProfile(snapshot.exists()?({id:snapshot.id,...snapshot.data()} as TwinProfile):null),onError);
}

export async function saveTwinProfile(userId:string,changes:Partial<Omit<TwinProfile,"id"|"userId">>){
  await setDoc(doc(db,"users",userId,"twinProfiles","main"),{userId,...changes,updatedAt:serverTimestamp()},{merge:true});
}

export async function uploadTwinAsset(userId:string,file:File,kind:TwinAssetKind){
  const maximum=kind==="video"||kind==="movement"?80*1024*1024:20*1024*1024;
  if(!file.size||file.size>maximum)throw new Error("asset-too-large");
  if(kind==="voice"&&!file.type.startsWith("audio/"))throw new Error("invalid-asset");
  if(kind!=="voice"&&!file.type.startsWith("video/"))throw new Error("invalid-asset");
  const extension=file.name.split(".").pop()?.replace(/[^a-zA-Z0-9]/g,"")||"webm";
  const assetRef=ref(storage,`twins/${userId}/${kind}-${Date.now()}.${extension}`);
  await uploadBytes(assetRef,file,{contentType:file.type,customMetadata:{ownerId:userId,kind}});
  const url=await getDownloadURL(assetRef);
  await saveTwinProfile(userId,kind==="voice"?{voiceUrl:url,voiceStatus:"sampled"}:kind==="movement"?{movementUrl:url,movementStatus:"sampled"}:{videoUrl:url});
  return url;
}

export function watchTwinAutomations(userId:string,onItems:(items:TwinAutomation[])=>void,onError:()=>void){
  return onSnapshot(collection(db,"users",userId,"twinAutomations"),(snapshot)=>{
    const items=snapshot.docs.map((item)=>({id:item.id,...item.data()} as TwinAutomation));
    items.sort((left,right)=>(right.createdAt?.toDate?.()?.getTime()||0)-(left.createdAt?.toDate?.()?.getTime()||0));onItems(items);
  },onError);
}

export async function createTwinAutomation(userId:string,automation:Omit<TwinAutomation,"id"|"userId"|"createdAt">){
  return addDoc(collection(db,"users",userId,"twinAutomations"),{...automation,userId,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
}
export async function toggleTwinAutomation(userId:string,id:string,enabled:boolean){await updateDoc(doc(db,"users",userId,"twinAutomations",id),{enabled,updatedAt:serverTimestamp()});}
export async function removeTwinAutomation(userId:string,id:string){await deleteDoc(doc(db,"users",userId,"twinAutomations",id));}

export async function createTwinRenderJob(userId:string,payload:{title:string;script:string;language:string;voiceMode:string;sequence:TwinSequenceStep[]}){
  return addDoc(collection(db,"users",userId,"twinRenders"),{...payload,userId,status:"prepared",disclosure:"Créé avec le Double IA de son propriétaire",createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
}
