import { addDoc, collection, doc, getDoc, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { db } from "@/lib/firebase";

export type CallSignal={
  id:string;callerId:string;calleeId:string;callerName:string;calleeName:string;video:boolean;status:"ringing"|"accepted"|"declined"|"ended";offer?:RTCSessionDescriptionInit;answer?:RTCSessionDescriptionInit;
  createdAt?:{toDate?:()=>Date}|null;
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
export async function addCallCandidate(callId:string,side:"caller"|"callee",candidate:RTCIceCandidateInit){await addDoc(collection(db,"calls",callId,`${side}Candidates`),candidate);}
export function watchCallCandidates(callId:string,side:"caller"|"callee",onCandidate:(candidate:RTCIceCandidateInit)=>void){return onSnapshot(collection(db,"calls",callId,`${side}Candidates`),(snapshot)=>snapshot.docChanges().forEach((change)=>{if(change.type==="added")onCandidate(change.doc.data() as RTCIceCandidateInit);}));}

export async function getCall(callId:string){const snapshot=await getDoc(doc(db,"calls",callId));return snapshot.exists()?({id:snapshot.id,...snapshot.data()} as CallSignal):null;}
