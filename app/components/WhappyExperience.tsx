"use client";

import { useEffect, useMemo, useState } from "react";

export type ExperienceSpace = "orbit" | "live" | "market" | "barter" | "seek" | "inbox" | "calls" | "contacts" | "rooms" | "radio" | "services" | "twin" | "business" | "games";
type Panel = "shortcuts" | "faq" | null;

const shortcuts: Array<{ key: string; icon: string; label: string; space: ExperienceSpace }> = [
  { key: "1", icon: "◫", label: "Messages", space: "inbox" },
  { key: "2", icon: "☎", label: "Appels", space: "calls" },
  { key: "3", icon: "◎", label: "Contacts", space: "contacts" },
  { key: "4", icon: "▦", label: "Moments", space: "orbit" },
  { key: "5", icon: "◇", label: "Marketplace", space: "market" },
];

const faqItems = [
  { category: "COMPTE", question: "Comment créer mon compte Whappy ?", answer: "Choisissez votre pays, saisissez votre numéro de téléphone puis confirmez le code reçu par SMS. Un numéro correspond à un seul compte Whappy." },
  { category: "COMPTE", question: "Je ne reçois pas le code SMS. Que faire ?", answer: "Vérifiez l’indicatif du pays et le numéro, puis votre connexion réseau. Attendez quelques instants avant de relancer l’envoi. Si le problème continue, vérifiez que votre opérateur accepte les SMS de vérification." },
  { category: "MESSAGES", question: "Les messages arrivent-ils en temps réel ?", answer: "Oui lorsque votre compte est connecté. L’état Cloud confirme la synchronisation. En mode démonstration, les essais restent sur votre appareil." },
  { category: "GROUPES", question: "Comment créer ou rejoindre un groupe ?", answer: "Ouvrez Contacts, puis la section Groupes. Vous pouvez créer un groupe, inviter des personnes ou utiliser un lien d’invitation reçu d’un administrateur." },
  { category: "APPELS", question: "Comment lancer un appel audio ou vidéo ?", answer: "Ouvrez une discussion ou l’onglet Appels, puis choisissez l’icône téléphone ou caméra. Autorisez le microphone et la caméra lorsque votre appareil le demande." },
  { category: "MARKETPLACE", question: "Comment vendre ou troquer un produit ?", answer: "Dans Marketplace, choisissez Vendre, ajoutez un titre, un prix ou l’échange souhaité, un lieu et un média. Vous pourrez ensuite suivre l’annonce dans votre boutique." },
  { category: "PAIEMENTS", question: "Les paiements sont-ils déjà activés ?", answer: "L’interface de commande est prête, mais un moyen de paiement doit être connecté et validé avant tout encaissement réel. Ne partagez jamais votre code secret dans une conversation." },
  { category: "LIVE", question: "Puis-je vendre pendant un direct ?", answer: "Oui. Préparez le titre, le produit et le mode de diffusion, puis présentez l’article pendant le direct. Les spectateurs peuvent discuter et ajouter le produit à leur panier." },
  { category: "MON DOUBLE", question: "Comment utiliser Mon Double en sécurité ?", answer: "Utilisez uniquement votre propre image et votre propre voix, ou un contenu pour lequel vous avez une autorisation claire. Whappy demande votre consentement avant la création et l’automatisation." },
  { category: "SÉCURITÉ", question: "Comment protéger mon compte et mes échanges ?", answer: "Ne communiquez jamais un code SMS, vérifiez l’identité du contact et gardez les échanges dans Whappy. Utilisez les options de conversation pour bloquer ou signaler un comportement suspect." },
];

export function WhappyExperience({ current, onNavigate, onPulse }: { current: ExperienceSpace; onNavigate: (space: ExperienceSpace) => void; onPulse: () => void }) {
  const [panel, setPanel] = useState<Panel>(null);
  const [faqQuery, setFaqQuery] = useState("");
  const filteredFaq = useMemo(() => {
    const query = faqQuery.trim().toLowerCase();
    return query ? faqItems.filter((item) => `${item.category} ${item.question} ${item.answer}`.toLowerCase().includes(query)) : faqItems;
  }, [faqQuery]);

  useEffect(() => {
    function handleKeyboard(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing = target?.matches("input, textarea, select, [contenteditable='true']");
      if (event.key === "Escape") setPanel(null);
      if (!typing && event.key === "?") {
        event.preventDefault();
        setPanel((value) => value === "shortcuts" ? null : "shortcuts");
      }
      if (!typing && event.key === "/") {
        event.preventDefault();
        document.querySelector<HTMLInputElement>("#whappy-global-search")?.focus();
      }
      if (!typing && event.altKey) {
        const destination = shortcuts.find((item) => item.key === event.key);
        if (destination) {
          event.preventDefault();
          onNavigate(destination.space);
        }
      }
    }
    window.addEventListener("keydown", handleKeyboard);
    return () => window.removeEventListener("keydown", handleKeyboard);
  }, [onNavigate]);

  return <>
    <div className="experience-tools" aria-label="Aide à la navigation">
      {current !== "inbox" && <button className="experience-back" onClick={() => onNavigate("inbox")}><span>◫</span> Revenir aux messages</button>}
      <button className="experience-faq" onClick={() => setPanel("faq")}>FAQ</button>
      <button className="experience-help" onClick={() => setPanel("shortcuts")} aria-label="Afficher les raccourcis Whappy" title="Raccourcis Whappy (?)">?</button>
    </div>

    {panel && <div className="experience-layer" role="dialog" aria-modal="true" aria-label={panel === "faq" ? "Questions fréquentes Whappy" : "Raccourcis Whappy"}>
      <button className="experience-dismiss" onClick={() => setPanel(null)} aria-label="Fermer" />
      {panel === "shortcuts" ? <section className="experience-panel">
        <header><div><small>NAVIGATION RAPIDE</small><h2>Allez droit à l’essentiel.</h2><p>Utilisez Whappy plus vite, avec la souris ou le clavier.</p></div><button onClick={() => setPanel(null)} aria-label="Fermer">×</button></header>
        <div className="experience-shortcuts">
          {shortcuts.map((item) => <button className={current === item.space ? "active" : ""} key={item.space} onClick={() => { onNavigate(item.space); setPanel(null); }}><span>{item.icon}</span><strong>{item.label}</strong><kbd>Alt {item.key}</kbd></button>)}
        </div>
        <div className="experience-commands">
          <button onClick={() => { document.querySelector<HTMLInputElement>("#whappy-global-search")?.focus(); setPanel(null); }}><span>⌕</span><div><strong>Rechercher partout</strong><small>Produit, contact, message ou service</small></div><kbd>/</kbd></button>
          <button onClick={() => { onPulse(); setPanel(null); }}><span>✦</span><div><strong>Ouvrir Whappy Pulse</strong><small>Toutes vos priorités et tous les espaces</small></div><kbd>⌘ K</kbd></button>
        </div>
        <footer><span>Besoin d’aide ?</span><p>Consultez les réponses aux questions fréquentes.</p><button onClick={() => setPanel("faq")}>Ouvrir la FAQ →</button></footer>
      </section> : <section className="experience-panel faq-panel">
        <header><div><small>CENTRE D’AIDE WHAPPY</small><h2>Comment pouvons-nous vous aider ?</h2><p>Des réponses simples pour utiliser Whappy en toute confiance.</p></div><button onClick={() => setPanel(null)} aria-label="Fermer">×</button></header>
        <label className="faq-search"><span>⌕</span><input value={faqQuery} onChange={(event) => setFaqQuery(event.target.value)} placeholder="Rechercher : SMS, groupe, paiement, Double…" />{faqQuery && <button onClick={() => setFaqQuery("")} aria-label="Effacer">×</button>}</label>
        <div className="faq-results" aria-live="polite">
          {filteredFaq.map((item, index) => <details key={item.question} open={!faqQuery && index === 0}><summary><span>{item.category}</span><strong>{item.question}</strong><i>＋</i></summary><p>{item.answer}</p></details>)}
          {!filteredFaq.length && <div className="faq-empty"><span>⌕</span><strong>Aucune réponse trouvée</strong><p>Essayez un mot plus simple comme « SMS », « appel » ou « vendre ».</p></div>}
        </div>
        <footer><span>Raccourcis</span><p>Découvrez aussi la navigation rapide de Whappy.</p><button onClick={() => setPanel("shortcuts")}>Voir les raccourcis →</button></footer>
      </section>}
    </div>}
  </>;
}
