"use client";

import { FormEvent, useMemo, useState } from "react";

export type CommerceItem = {
  id: string | number;
  title: string;
  price: string;
  place: string;
  seller: string;
  mark: string;
  category: string;
  mode: "vente" | "troc";
  trust: number;
  mediaUrl?: string;
};

export type CartLine = { item: CommerceItem; quantity: number };

function numericPrice(price: string) {
  const value = Number(price.replace(/[^0-9]/g, ""));
  return Number.isFinite(value) ? value : 0;
}

function money(value: number) {
  return new Intl.NumberFormat("fr-FR").format(value) + " FCFA";
}

export function ProductPanel({ item, saved, onSave, onClose, onContact, onAdd }: { item:CommerceItem;saved:boolean;onSave:()=>void;onClose:()=>void;onContact:()=>void;onAdd:(quantity:number)=>void }) {
  const [quantity,setQuantity]=useState(1);
  async function share() {
    const content={title:item.title,text:`${item.title} — ${item.price} sur Whappy`,url:window.location.href};
    try { if(navigator.share) await navigator.share(content); else await navigator.clipboard.writeText(`${content.text} ${content.url}`); } catch { /* Share sheet closed. */ }
  }
  return <div className="commerce-layer" role="dialog" aria-modal="true" aria-label={item.title}><button className="commerce-dismiss" onClick={onClose} aria-label="Fermer la fiche"/><section className="product-panel"><button className="panel-close" onClick={onClose} aria-label="Fermer">×</button><div className={`product-media ${item.mediaUrl?"photo":""}`} style={item.mediaUrl?{backgroundImage:`url(${item.mediaUrl})`}:undefined}>{!item.mediaUrl&&<><span>{item.mark}</span><small>{item.category}</small></>}<button className={saved?"saved":""} onClick={onSave} aria-label="Enregistrer l’annonce">{saved?"♥":"♡"}</button></div><div className="product-info"><span className="verified-seller"><i>{item.mark}</i><b>{item.seller}</b><em>✓ Vérifié</em><small>{item.trust}% fiable</small></span><h2>{item.title}</h2><strong>{item.price}</strong><p className="product-location">⌖ {item.place}</p><div className="product-assurance"><span>✓ Identité vérifiée</span><span>◇ Discussion protégée</span><span>↻ Offre négociable</span></div><div className="product-description"><h3>À propos</h3><p>Échangez directement avec le vendeur pour confirmer l’état, la disponibilité, la livraison et les conditions de paiement.</p></div>{item.mode==="vente"?<div className="product-buy"><div className="product-quantity"><button onClick={()=>setQuantity(value=>Math.max(1,value-1))}>−</button><b>{quantity}</b><button onClick={()=>setQuantity(value=>Math.min(9,value+1))}>＋</button></div><button onClick={()=>onAdd(quantity)}>Ajouter au panier</button></div>:<button className="barter-primary" onClick={onContact}>⇄ Proposer un échange</button>}<div className="product-secondary"><button onClick={onContact}>◫ Discuter avec le vendeur</button><button onClick={share}>↗ Partager</button></div><small className="transaction-note">Ne payez jamais en dehors d’un moyen convenu et vérifiable. Inspectez le produit avant validation.</small></div></section></div>;
}

export function CartPanel({ lines, onClose, onQuantity, onRemove, notify }: { lines:CartLine[];onClose:()=>void;onQuantity:(id:CommerceItem["id"],quantity:number)=>void;onRemove:(id:CommerceItem["id"])=>void;notify:(text:string)=>void }) {
  const [checkout,setCheckout]=useState(false); const [confirmed,setConfirmed]=useState(false); const [method,setMethod]=useState("delivery");
  const [reference]=useState(()=>Date.now().toString().slice(-6));
  const total=useMemo(()=>lines.reduce((sum,line)=>sum+numericPrice(line.item.price)*line.quantity,0),[lines]);
  function submit(event:FormEvent){event.preventDefault();setConfirmed(true);notify("Commande préparée avec succès");}
  return <div className="cart-layer"><button className="cart-dismiss" onClick={onClose} aria-label="Fermer le panier"/><aside className="cart-panel" role="dialog" aria-modal="true" aria-label="Mon panier"><header><div><small>WHAPPY MARKET</small><h2>{confirmed?"Commande préparée":"Mon panier"}</h2></div><button onClick={onClose} aria-label="Fermer">×</button></header>{confirmed?<div className="order-success"><span>✓</span><h3>Votre commande est prête</h3><p>Un récapitulatif a été créé. Le vendeur doit maintenant confirmer la disponibilité et le mode de règlement.</p><b>Référence WH-{reference}</b><button onClick={onClose}>Retour à Whappy</button></div>:checkout?<form className="checkout-form" onSubmit={submit}><button className="checkout-back" type="button" onClick={()=>setCheckout(false)}>← Retour au panier</button><h3>Livraison et contact</h3><label>Nom complet<input required autoComplete="name" placeholder="Votre nom"/></label><label>Téléphone<input required inputMode="tel" autoComplete="tel" placeholder="+242 06…"/></label><label>Adresse ou point de rendez-vous<textarea required placeholder="Quartier, rue et repère"/></label><h3>Mode de règlement</h3><div className="payment-choice"><label className={method==="delivery"?"active":""}><input type="radio" name="payment" value="delivery" checked={method==="delivery"} onChange={()=>setMethod("delivery")}/>Paiement à la livraison<small>À confirmer avec le vendeur</small></label><label className={method==="mobile"?"active":""}><input type="radio" name="payment" value="mobile" checked={method==="mobile"} onChange={()=>setMethod("mobile")}/>Mobile Money<small>Demande de paiement après confirmation</small></label></div><div className="checkout-total"><span>Total indicatif</span><strong>{total?money(total):"À négocier"}</strong></div><button className="checkout-submit">Préparer la commande</button><small>Aucun débit n’est effectué à cette étape.</small></form>:<><div className="cart-lines">{lines.map(line=><article key={line.item.id}><span>{line.item.mark}</span><div><strong>{line.item.title}</strong><small>{line.item.seller}</small><b>{line.item.price}</b><div><button onClick={()=>onQuantity(line.item.id,Math.max(1,line.quantity-1))}>−</button><em>{line.quantity}</em><button onClick={()=>onQuantity(line.item.id,line.quantity+1)}>＋</button><button onClick={()=>onRemove(line.item.id)}>Supprimer</button></div></div></article>)}{!lines.length&&<div className="cart-empty"><span>◇</span><h3>Votre panier est vide</h3><p>Ajoutez un produit depuis le Market ou un direct.</p></div>}</div>{lines.length>0&&<footer><div><span>Total indicatif</span><strong>{total?money(total):"À négocier"}</strong></div><button onClick={()=>setCheckout(true)}>Continuer la commande →</button><small>Disponibilité et paiement confirmés ensuite avec chaque vendeur.</small></footer>}</>}</aside></div>;
}
