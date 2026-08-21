"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { approveGroupJoinRequest, declineGroupJoinRequest, watchGroupJoinRequests, type CloudGroupJoinRequest } from "@/lib/whappy-data";

export function GroupJoinRequests({ ownerId, groupId, groupName, enabled, notify }: { ownerId: string; groupId: string; groupName: string; enabled: boolean; notify: (text: string) => void }) {
  const [items, setItems] = useState<CloudGroupJoinRequest[]>([]);
  const [busy, setBusy] = useState("");
  const notifyRef = useRef(notify);

  useEffect(() => { notifyRef.current = notify; }, [notify]);

  useEffect(() => {
    if (!enabled) return;
    return watchGroupJoinRequests(ownerId, setItems, () => notifyRef.current("Les demandes d’adhésion sont momentanément indisponibles."));
  }, [enabled, ownerId]);

  const requests = useMemo(() => items.filter((item) => item.groupId === groupId), [items, groupId]);
  if (!enabled || !requests.length) return null;

  async function decide(request: CloudGroupJoinRequest, approved: boolean) {
    setBusy(request.id);
    try {
      if (approved) {
        await approveGroupJoinRequest(request, ownerId);
        notify(request.userName + " rejoint « " + groupName + " ».");
      } else {
        await declineGroupJoinRequest(request.id);
        notify("Demande refusée.");
      }
    } catch {
      notify("La demande n’a pas pu être mise à jour.");
    } finally {
      setBusy("");
    }
  }

  return <section className="group-join-requests">
    <header><span>DEMANDES D’ADHÉSION</span><b>{requests.length}</b></header>
    {requests.map((request) => <article key={request.id}>
      <span>{request.userName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2)}</span>
      <div><strong>{request.userName}</strong><small>Souhaite rejoindre {groupName}</small></div>
      <button disabled={busy === request.id} onClick={() => void decide(request, false)}>Refuser</button>
      <button className="accept" disabled={busy === request.id} onClick={() => void decide(request, true)}>Accepter</button>
    </article>)}
  </section>;
}
