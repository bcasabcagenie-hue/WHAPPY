import { addDoc, collection, doc, getDoc, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type CallSignal={
  id:string;callerId:string;calleeId:string;callerName:string;calleeName:string;video:boolean;status:"ringing"|"accepted"|"declined"|"ended";offer?:RTCSessionDescriptionInit;answer?:RTCSessionDescriptionInit;
  createdAt?:{toDate?:()=>Date}|null;
  updatedAt?:{toDate?:()=>Date}|null;
};

export async function startCall(callerId:string,calleeId:string,callerName:string,calleeName:string,video:boolean,offer:RTCSessionDescriptionInit){
  const reference=doc(collection(db,"calls"));
  await setDoc(reference,{callerId,calleeId,callerName,calleeName,video,status:"ringing",offer,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  return reference.id;
}

export async function answerCall(callId:string,answer:RTCSessionDescriptionInit){await updateDoc(doc(db,"calls",callId),{answer,status:"accepted",updatedAt:serverTimestamp()});}
export async function updateCallStatus(callId:string,status:CallSignal["status"]){await updateDoc(doc(db,"calls",callId),{status,updatedAt:serverTimestamp()});}
export function watchCall(callId:string,onCall:(call:CallSignal|null)=>void){return onSnapshot(doc(db,"calls",callId),(snapshot)=>onCall(snapshot.exists()?({id:snapshot.id,...snapshot.data()} as CallSignal):null));}
export function watchIncomingCalls(userId:string,onCall:(call:CallSignal)=>void){const incomingQuery=query(collection(db,"calls"),where("calleeId","==",userId));return onSnapshot(incomingQuery,(snapshot)=>snapshot.docChanges().forEach((change)=>{const call={id:change.doc.id,...change.doc.data()} as CallSignal;const created=call.createdAt?.toDate?.()?.getTime();const recent=!created||Date.now()-created<120_000;if(change.type!=="removed"&&call.status==="ringing"&&call.callerId!==userId&&recent)onCall(call);}));}
export function watchCallHistory(userId:string,onCalls:(calls:CallSignal[])=>void,onError:()=>void){
  let outgoing:CallSignal[]=[];let incoming:CallSignal[]=[];
  const publish=()=>onCalls([...outgoing,...incoming].sort((left,right)=>(right.createdAt?.toDate?.()?.getTime()||0)-(left.createdAt?.toDate?.()?.getTime()||0)));
  const stopOutgoing=onSnapshot(query(collection(db,"calls"),where("callerId","==",userId)),(snapshot)=>{outgoing=snapshot.docs.map((item)=>({id:item.id,...item.data()} as CallSignal));publish();},onError);
  const stopIncoming=onSnapshot(query(collection(db,"calls"),where("calleeId","==",userId)),(snapshot)=>{incoming=snapshot.docs.map((item)=>({id:item.id,...item.data()} as CallSignal));publish();},onError);
  return()=>{stopOutgoing();stopIncoming();};
}
export async function addCallCandidate(callId:string,side:"caller"|"callee",candidate:RTCIceCandidateInit){await addDoc(collection(db,"calls",callId,`${side}Candidates`),candidate);}
export function watchCallCandidates(callId:string,side:"caller"|"callee",onCandidate:(candidate:RTCIceCandidateInit)=>void){return onSnapshot(collection(db,"calls",callId,`${side}Candidates`),(snapshot)=>snapshot.docChanges().forEach((change)=>{if(change.type==="added")onCandidate(change.doc.data() as RTCIceCandidateInit);}));}

export async function getCall(callId:string){const snapshot=await getDoc(doc(db,"calls",callId));return snapshot.exists()?({id:snapshot.id,...snapshot.data()} as CallSignal):null;}
