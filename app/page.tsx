"use client";

import Image from "next/image";
import { FormEvent, useMemo, useState } from "react";

type Space = "orbit" | "live" | "market" | "barter" | "seek" | "inbox" | "twin";
type Listing = { id: number; title: string; price: string; place: string; seller: string; mark: string; tone: string; category: string; mode: "vente" | "troc"; trust: number; };

const listings: Listing[] = [
  { id: 1, title: "MacBook Air M3 · Comme neuf", price: "750 000 FCFA", place: "Poto-Poto · 1,2 km", seller: "Junior K.", mark: "JK", tone: "lime", category: "Tech", mode: "vente", trust: 98 },
  { id: 2, title: "Canapé modulable en velours", price: "Échange accepté", place: "Bacongo · 3,4 km", seller: "Maison Noki", mark: "MN", tone: "violet", category: "Maison", mode: "troc", trust: 94 },
  { id: 3, title: "Sneakers édition limitée", price: "85 000 FCFA", place: "Centre-ville · 800 m", seller: "Mokabi Store", mark: "MS", tone: "orange", category: "Mode", mode: "vente", trust: 99 },
  { id: 4, title: "Studio photo — 3 heures", price: "Contre identité visuelle", place: "Moungali · 2,1 km", seller: "Nadia M.", mark: "NM", tone: "blue", category: "Services", mode: "troc", trust: 97 },
];

const requests = [
  { title: "Je cherche un développeur Flutter", details: "Mission de 3 semaines · Budget disponible", place: "À distance", reward: "450 000 FCFA", urgent: true },
  { title: "Besoin d'un groupe électrogène ce soir", details: "Pour un événement de 18 h à minuit", place: "Talangaï · 6 km", reward: "Location", urgent: true },
  { title: "Où trouver du tissu wax premium ?", details: "Recherche fournisseur pour 60 mètres", place: "Brazzaville", reward: "Bon plan", urgent: false },
  { title: "Cours de guitare contre cours d'anglais", details: "Deux séances par semaine", place: "Moungali · 3 km", reward: "Troc", urgent: false },
];

const lives = [
  { host: "Mokabi Studio", title: "Nouvelle collection · essayage en direct", viewers: "2,8 k", product: "Veste N'Tela", price: "65 000", tone: "fashion", badge: "LIVE SHOP" },
  { host: "Chef Grâce", title: "Secrets du poulet moambe moderne", viewers: "1,4 k", product: "Masterclass", price: "12 000", tone: "food", badge: "EN DIRECT" },
  { host: "Tech House", title: "Test sans filtre : les meilleurs smartphones", viewers: "963", product: "Galaxy S26", price: "490 000", tone: "tech", badge: "DÉMO LIVE" },
];

const messages = [
  { name: "Amina M.", text: "Le troc est accepté pour le canapé ?", time: "Maintenant", mark: "AM", color: "#13d713", unread: 2 },
  { name: "Junior K.", text: "Je peux livrer le MacBook cet après-midi.", time: "12:08", mark: "JK", color: "#13d713", unread: 1 },
  { name: "Mokabi Store", text: "Votre commande est prête ✦", time: "11:42", mark: "MS", color: "#13d713", unread: 0 },
  { name: "Design Crew", text: "Nadia : rendez-vous confirmé demain", time: "Hier", mark: "DC", color: "#13d713", unread: 0 },
];

function Mark({ children, color, small = false }: { children: React.ReactNode; color?: string; small?: boolean }) {
  return <span className={`op-mark ${small ? "small" : ""}`} style={color ? { background: color } : undefined}>{children}</span>;
}

export default function Home() {
  const [space, setSpace] = useState<Space>("orbit");
  const [search, setSearch] = useState("");
  const [marketFilter, setMarketFilter] = useState("Tout");
  const [saved, setSaved] = useState<Record<number, boolean>>({});
  const [toast, setToast] = useState("");
  const [modal, setModal] = useState<"sell" | "seek" | "live" | "twin" | "message" | null>(null);
  const [liveIndex, setLiveIndex] = useState<number | null>(null);
  const [twinStep, setTwinStep] = useState(1);
  const [consent, setConsent] = useState(false);

  const filtered = useMemo(() => listings.filter((item) => {
    const matchesText = `${item.title} ${item.category} ${item.place}`.toLowerCase().includes(search.toLowerCase());
    const matchesFilter = marketFilter === "Tout" || item.category === marketFilter || (marketFilter === "Troc" && item.mode === "troc");
    return matchesText && matchesFilter;
  }), [search, marketFilter]);

  function go(next: Space) {
    setSpace(next);
    setSearch("");
  }

  function notify(text: string) {
    setToast(text);
    window.setTimeout(() => setToast(""), 2400);
  }

  function submitModal(event: FormEvent) {
    event.preventDefault();
    notify(modal === "seek" ? "Votre recherche est maintenant active" : "Votre annonce est prête à être publiée");
    setModal(null);
  }

  const titles: Record<Space, [string, string]> = {
    orbit: ["Aujourd'hui dans votre monde", "Des opportunités choisies autour de vous"],
    live: ["Whappy Live", "Regardez, échangez et achetez en temps réel"],
    market: ["Marché vivant", "Des produits et services de confiance"],
    barter: ["Troc intelligent", "Échangez de la valeur, sans limite"],
    seek: ["Je cherche", "Publiez un besoin, la communauté répond"],
    inbox: ["Connexions", "Vos conversations, commandes et offres"],
    twin: ["Studio Double", "Votre vendeur numérique, créé avec votre accord"],
  };

  return <main className="nova-shell white-green">
    <aside className="nova-rail">
      <button className="nova-logo" onClick={() => go("orbit")} aria-label="Accueil Whappy"><Image src="/whappy-logo.svg" alt="Icône Whappy" width={50} height={50} priority /></button>
      <nav aria-label="Espaces Whappy">
        <Rail active={space === "orbit"} icon="✦" label="Orbite" onClick={() => go("orbit")} />
        <Rail active={space === "live"} icon="◉" label="Directs" live onClick={() => go("live")} />
        <Rail active={space === "market"} icon="◇" label="Marché" onClick={() => go("market")} />
        <Rail active={space === "barter"} icon="⇄" label="Troquer" onClick={() => go("barter")} />
        <Rail active={space === "seek"} icon="⌖" label="Chercher" onClick={() => go("seek")} />
        <Rail active={space === "inbox"} icon="◫" label="Messages" count={3} onClick={() => go("inbox")} />
      </nav>
      <div className="rail-tools">
        <button className={space === "twin" ? "active" : ""} onClick={() => go("twin")}><span>◎</span><small>Mon Double</small></button>
        <button className="me">CB<i /></button>
      </div>
    </aside>

    <section className="nova-stage">
      <header className="nova-topbar">
        <div className="topbar-identity">
          <button className="mobile-logo" onClick={() => go("orbit")} aria-label="Accueil Whappy">
            <Image src="/whappy-logo.svg" alt="Logo officiel Whappy" width={40} height={40} priority />
          </button>
          <div><span className="kicker">WHAPPY / {space.toUpperCase()}</span><h1>{titles[space][0]}</h1><p>{titles[space][1]}</p></div>
        </div>
        <label className="nova-search"><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Chercher un produit, une compétence, un lieu, une solution…" />{search && <button onClick={() => setSearch("")}>×</button>}</label>
        <div className="top-actions"><button onClick={() => setModal("seek")}><span>⌖</span><small>Je cherche</small></button><button className="sell" onClick={() => setModal("sell")}><span>＋</span><small>Vendre</small></button></div>
      </header>

      {space === "orbit" && <Orbit go={go} setModal={setModal} setLiveIndex={setLiveIndex} notify={notify} saved={saved} setSaved={setSaved} />}
      {space === "live" && <LiveSpace setModal={setModal} setLiveIndex={setLiveIndex} />}
      {space === "market" && <MarketSpace search={search} filter={marketFilter} setFilter={setMarketFilter} items={filtered} saved={saved} setSaved={setSaved} notify={notify} />}
      {space === "barter" && <BarterSpace notify={notify} setModal={setModal} />}
      {space === "seek" && <SeekSpace setModal={setModal} notify={notify} />}
      {space === "inbox" && <InboxSpace setModal={setModal} notify={notify} />}
      {space === "twin" && <TwinSpace step={twinStep} setStep={setTwinStep} consent={consent} setConsent={setConsent} notify={notify} />}
    </section>

    {liveIndex !== null && <LiveViewer live={lives[liveIndex]} onClose={() => setLiveIndex(null)} notify={notify} />}
    {modal && <ActionModal type={modal} onClose={() => setModal(null)} onSubmit={submitModal} consent={consent} setConsent={setConsent} setTwinStep={setTwinStep} go={go} notify={notify} />}
    {toast && <div className="nova-toast">✦ {toast}</div>}
  </main>;
}

function Rail({ active, icon, label, count, live, onClick }: { active: boolean; icon: string; label: string; count?: number; live?: boolean; onClick: () => void }) {
  return <button className={active ? "active" : ""} onClick={onClick}><span>{icon}</span><small>{label}</small>{count ? <b>{count}</b> : null}{live ? <i /> : null}</button>;
}

function Orbit({ go, setModal, setLiveIndex, notify, saved, setSaved }: { go: (space: Space) => void; setModal: (modal: "sell" | "seek" | "live" | "twin") => void; setLiveIndex: (index: number) => void; notify: (text: string) => void; saved: Record<number, boolean>; setSaved: React.Dispatch<React.SetStateAction<Record<number, boolean>>> }) {
  return <div className="orbit-scroll">
    <section className="orbit-hero">
      <div className="hero-mesh"><i/><i/><i/><i/></div>
      <div className="hero-copy"><span className="signal"><i/> VOTRE VILLE EST ACTIVE</span><h2>Tout peut devenir<br/><em>une opportunité.</em></h2><p>Une idée. Un objet. Un talent. Une urgence. Whappy connecte ce que vous avez à ce dont quelqu&apos;un a besoin — maintenant.</p><div><button onClick={() => setModal("seek")}>⌖ Trouver une solution</button><button onClick={() => setModal("sell")}>＋ Proposer quelque chose</button></div></div>
      <div className="orbit-map"><div className="radar"><span className="radar-core"><Image src="/whappy-logo.svg" alt="" width={44} height={44}/></span><i className="ring r1"/><i className="ring r2"/><i className="ring r3"/><button className="map-node n1" onClick={() => go("market")}><b>◇</b><span>MacBook<br/><small>1,2 km</small></span></button><button className="map-node n2" onClick={() => go("seek")}><b>⌖</b><span>Besoin urgent<br/><small>6 km</small></span></button><button className="map-node n3" onClick={() => go("barter")}><b>⇄</b><span>Troc proposé<br/><small>3,4 km</small></span></button><button className="map-node n4" onClick={() => setLiveIndex(0)}><b>●</b><span>En direct<br/><small>2,8 k vues</small></span></button></div></div>
    </section>
    <div className="orbit-body">
      <section className="action-strip"><button onClick={() => setModal("live")}><Mark>●</Mark><div><strong>Lancer un direct</strong><small>Présentez et vendez en live</small></div><span>↗</span></button><button onClick={() => go("twin")}><Mark>◎</Mark><div><strong>Activer mon Double</strong><small>Votre vendeur vidéo consentant</small></div><span>↗</span></button><button onClick={() => go("barter")}><Mark>⇄</Mark><div><strong>Proposer un troc</strong><small>Échangez ce que vous avez</small></div><span>↗</span></button></section>
      <SectionTitle overline="ÇA SE PASSE MAINTENANT" title="Directs près de vous" action="Explorer les directs" onClick={() => go("live")} />
      <div className="mini-live-grid">{lives.map((live, index) => <button key={live.host} className={`mini-live ${live.tone}`} onClick={() => setLiveIndex(index)}><span className="live-label"><i/> {live.badge}</span><div className="live-person">{live.host.split(" ").map((x) => x[0]).join("").slice(0,2)}</div><div className="live-info"><small>{live.host} · {live.viewers} regardent</small><strong>{live.title}</strong><span>{live.product} <b>{live.price} FCFA</b></span></div></button>)}</div>
      <SectionTitle overline="SÉLECTION POUR VOUS" title="À saisir autour de vous" action="Voir le marché" onClick={() => go("market")} />
      <div className="listing-grid">{listings.slice(0,3).map((item) => <ListingCard key={item.id} item={item} saved={!!saved[item.id]} onSave={() => setSaved((current) => ({...current,[item.id]:!current[item.id]}))} onOpen={() => notify(`${item.title} ouvert`)} />)}</div>
      <section className="need-ribbon"><div><span>⌖</span><div><small>UNE QUESTION À LA COMMUNAUTÉ ?</small><h3>Décrivez ce que vous cherchez.<br/>Whappy trouve qui peut vous aider.</h3></div></div><button onClick={() => setModal("seek")}>Publier une recherche ↗</button></section>
    </div>
  </div>;
}

function LiveSpace({ setModal, setLiveIndex }: { setModal: (type: "live") => void; setLiveIndex: (index: number) => void }) {
  return <div className="space-scroll live-space">
    <section className="live-command"><div className="live-command-copy"><span className="signal"><i/> STUDIO LIVE NOUVELLE GÉNÉRATION</span><h2>Ne publiez plus.<br/><em>Faites vivre.</em></h2><p>Montrez vos produits, répondez aux questions, négociez et concluez la vente sans quitter la vidéo.</p><button onClick={() => setModal("live")}>● Créer mon direct</button></div><div className="studio-preview"><div className="preview-person">CB</div><span className="preview-live">● LIVE · 00:12:48</span><div className="preview-comments"><span>Ça existe en bleu ?</span><span>Livraison à Pointe-Noire ?</span><span>🔥🔥🔥</span></div><div className="preview-product"><i>◇</i><span><small>PRODUIT ÉPINGLÉ</small><strong>Montre Kongo One</strong><b>42 000 FCFA</b></span><button>Acheter</button></div></div></section>
    <section className="space-content"><SectionTitle overline="EN CE MOMENT" title="Des expériences, pas des publicités" action="Voir le programme" onClick={() => {}}/><div className="big-live-grid">{lives.map((live,index)=><button key={live.host} className={`big-live ${live.tone}`} onClick={() => setLiveIndex(index)}><span><i/> {live.badge}</span><div className="host-face">{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}</div><div><small>{live.host} · {live.viewers} spectateurs</small><h3>{live.title}</h3><p>Produit épinglé : {live.product}</p><b>{live.price} FCFA</b></div></button>)}</div><div className="live-features"><div><span>⚡</span><strong>Achat instantané</strong><p>Le produit reste visible pendant que vous présentez.</p></div><div><span>◌</span><strong>Questions en scène</strong><p>Faites monter un client dans votre direct.</p></div><div><span>◎</span><strong>Relais par votre Double</strong><p>Continuez à vendre après la fin du direct.</p></div></div></section>
  </div>;
}

function MarketSpace({ search, filter, setFilter, items, saved, setSaved, notify }: { search:string; filter:string; setFilter:(v:string)=>void; items:Listing[]; saved:Record<number,boolean>; setSaved:React.Dispatch<React.SetStateAction<Record<number, boolean>>>; notify:(text:string)=>void }) {
  const filters=["Tout","Tech","Mode","Maison","Services","Troc"];
  return <div className="space-scroll market-space"><section className="market-banner"><div><span>WHAPPY MARKET / CONFIANCE LOCALE</span><h2>Achetez à des personnes,<br/>pas à des catalogues.</h2><p>Profils vérifiés, paiement protégé et négociation humaine.</p></div><div className="trust-orbit"><strong>97%</strong><span>indice moyen<br/>de confiance</span></div></section><section className="space-content"><div className="market-toolbar"><div>{filters.map(x=><button className={filter===x?"active":""} key={x} onClick={()=>setFilter(x)}>{x}</button>)}</div><button>⌖ Autour de moi</button><button>≡ Trier</button></div><div className="results-line"><span>{items.length} opportunités {search && `pour « ${search} »`}</span><small>Rayon : 10 km</small></div><div className="listing-grid market-listings">{items.map(item=><ListingCard key={item.id} item={item} saved={!!saved[item.id]} onSave={()=>setSaved(c=>({...c,[item.id]:!c[item.id]}))} onOpen={()=>notify(`Discussion ouverte avec ${item.seller}`)}/>)}</div></section></div>;
}

function BarterSpace({ notify, setModal }: { notify:(text:string)=>void; setModal:(type:"sell")=>void }) {
  const [mine,setMine]=useState("Mon appareil photo"); const [want,setWant]=useState("Un ordinateur portable"); const [score,setScore]=useState<number|null>(null);
  return <div className="space-scroll barter-space"><section className="barter-hero"><span className="signal"><i/> WHAPPY MATCH</span><h2>La valeur ne se mesure<br/>pas toujours en argent.</h2><p>Décrivez ce que vous avez et ce que vous voulez. Notre moteur trouve les échanges possibles, même à plusieurs personnes.</p><div className="barter-engine"><label><small>JE PROPOSE</small><input value={mine} onChange={e=>setMine(e.target.value)}/><span>＋ Photo</span></label><button className="swap">⇄</button><label><small>JE RECHERCHE</small><input value={want} onChange={e=>setWant(e.target.value)}/><span>⌖ Zone : 25 km</span></label><button className="match" onClick={()=>setScore(94)}>Trouver un échange ✦</button></div>{score&&<div className="match-result"><span>{score}%</span><div><small>MEILLEURE CORRESPONDANCE</small><strong>Patrick propose un MacBook Pro</strong><p>Il cherche un appareil photo hybride + complément.</p></div><button onClick={()=>notify("Proposition de troc envoyée")}>Proposer le troc ↗</button></div>}</section><section className="space-content"><SectionTitle overline="ÉCHANGES OUVERTS" title="Le troc bouge près de vous" action="Publier un objet" onClick={()=>setModal("sell")}/><div className="barter-cards"><div><span className="barter-art violet">⌁</span><small>PROPOSE</small><strong>Service de photographie</strong><i>contre</i><small>RECHERCHE</small><strong>Création d&apos;un site vitrine</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div><span className="barter-art amber">◆</span><small>PROPOSE</small><strong>Canapé en excellent état</strong><i>contre</i><small>RECHERCHE</small><strong>Table à manger + 4 chaises</strong><button onClick={()=>notify("Détails du troc ouverts")}>Voir l&apos;échange</button></div><div className="chain-card"><span>⇄</span><h3>Troc en chaîne</h3><p>Vous avez A, vous voulez B. Une troisième personne veut A et possède C. Whappy relie les trois.</p><b>18 chaînes possibles aujourd&apos;hui</b></div></div></section></div>;
}

function SeekSpace({ setModal, notify }: { setModal:(type:"seek")=>void; notify:(text:string)=>void }) {
  const [filter,setFilter]=useState("Tous");
  return <div className="space-scroll seek-space"><section className="seek-hero"><div><span className="signal"><i/> INTELLIGENCE COLLECTIVE</span><h2>Demandez.<br/><em>Quelqu&apos;un sait.</em></h2><p>Un produit introuvable, une compétence urgente, une situation à résoudre ? Publiez votre besoin avec le lieu, le délai et votre budget.</p><button onClick={()=>setModal("seek")}>⌖ Publier ce que je cherche</button></div><div className="seek-cloud"><span className="q1">Un plombier maintenant</span><span className="q2">Appartement à louer</span><span className="q3">Pièce Toyota 2017</span><span className="q4">Graphiste disponible</span><span className="q5">Bon restaurant calme</span><b>⌖</b></div></section><section className="space-content"><div className="seek-tabs">{["Tous","Urgent","Produits","Services","Situations"].map(x=><button className={filter===x?"active":""} onClick={()=>setFilter(x)} key={x}>{x}</button>)}</div><div className="request-grid">{requests.filter(x=>filter==="Tous"||(filter==="Urgent"&&x.urgent)||(filter==="Services"&&x.title.includes("développeur"))||(filter==="Situations"&&x.title.includes("groupe"))).map((item,index)=><article key={item.title}><header><span className={item.urgent?"urgent":""}>{item.urgent?"URGENT":"RECHERCHE"}</span><small>Il y a {index*7+3} min</small></header><h3>{item.title}</h3><p>{item.details}</p><div><span>⌖ {item.place}</span><b>{item.reward}</b></div><footer><span>{index*4+7} personnes ont vu</span><button onClick={()=>notify("Votre réponse a été envoyée")}>Je peux aider ↗</button></footer></article>)}</div></section></div>;
}

function InboxSpace({ setModal, notify }: { setModal:(type:"message")=>void; notify:(text:string)=>void }) {
  const [selected,setSelected]=useState(0); const [text,setText]=useState("");
  function send(e:FormEvent){e.preventDefault();if(!text.trim())return;notify("Message envoyé");setText("");}
  return <div className="inbox-space"><aside className="inbox-list"><div className="inbox-filters"><button className="active">Tout</button><button>Achats</button><button>Ventes</button><button>Trocs</button></div>{messages.map((m,index)=><button className={selected===index?"active":""} onClick={()=>setSelected(index)} key={m.name}><Mark color={m.color}>{m.mark}</Mark><span><strong>{m.name}</strong><small>{m.text}</small></span><i>{m.time}</i>{m.unread>0&&<b>{m.unread}</b>}</button>)}</aside><section className="deal-chat"><header><Mark color={messages[selected].color}>{messages[selected].mark}</Mark><div><strong>{messages[selected].name}</strong><small>Identité vérifiée · Répond rapidement</small></div><button>⌕</button><button>•••</button></header><div className="deal-context"><span className="product-thumb">◇</span><div><small>À PROPOS DE L&apos;ANNONCE</small><strong>{selected===0?"Canapé modulable en velours":"MacBook Air M3 · Comme neuf"}</strong><p>{selected===0?"Échange accepté":"750 000 FCFA"}</p></div><button onClick={()=>notify("Annonce ouverte")}>Voir</button></div><div className="deal-messages"><span className="chat-date">AUJOURD&apos;HUI</span><div className="theirs">Bonjour ! Est-ce que votre annonce est toujours disponible ?<small>12:03</small></div><div className="mine">Oui, absolument. On peut aussi discuter d&apos;un échange.<small>12:05 ✓✓</small></div><div className="theirs">Parfait, je vous envoie ma proposition.<small>12:08</small></div></div><form onSubmit={send}><button type="button">＋</button><input value={text} onChange={e=>setText(e.target.value)} placeholder="Écrire un message ou faire une offre…"/><button type="button" onClick={()=>setModal("message")}>◇ Offre</button><button type="submit">➤</button></form></section></div>;
}

function TwinSpace({ step, setStep, consent, setConsent, notify }: { step:number; setStep:(n:number)=>void; consent:boolean; setConsent:(v:boolean)=>void; notify:(text:string)=>void }) {
  return <div className="space-scroll twin-space"><section className="twin-hero"><div className="twin-copy"><span className="signal"><i/> STUDIO DOUBLE · VOTRE IMAGE, VOTRE CONTRÔLE</span><h2>Vous créez une fois.<br/><em>Votre Double vend toujours.</em></h2><p>Enregistrez votre propre vidéo. Whappy crée un présentateur numérique qui explique vos produits dans les langues et formats que vous autorisez.</p><div className="safety-chips"><span>✓ Consentement explicite</span><span>✓ Révocable à tout moment</span><span>✓ Marqué comme IA</span></div></div><div className="twin-visual"><div className="scan-lines"/><div className="human"><span>CB</span><small>VOUS</small></div><div className="transfer">··· ✦ ···</div><div className="digital"><span>CB</span><small>DOUBLE IA</small><b>AI</b></div></div></section><section className="twin-builder"><div className="builder-steps">{["Consentement","Enregistrement","Produits","Personnalité","Publication"].map((x,index)=><button className={step===index+1?"active":step>index+1?"done":""} onClick={()=>setStep(index+1)} key={x}><span>{step>index+1?"✓":index+1}</span><small>{x}</small></button>)}</div><div className="builder-card">{step===1&&<><span className="builder-icon">◎</span><h3>Votre identité vous appartient</h3><p>Whappy utilise uniquement les vidéos de vous-même que vous fournissez. Votre Double ne peut pas représenter une autre personne et chaque vidéo générée porte le label « Créé avec un Double IA ».</p><label className="consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/><span>Je confirme créer un Double à partir de ma propre image et j&apos;accepte son utilisation uniquement pour mes contenus Whappy autorisés.</span></label><button disabled={!consent} onClick={()=>setStep(2)}>Continuer vers l&apos;enregistrement ↗</button></>}{step===2&&<><span className="builder-icon record">●</span><h3>Enregistrez votre capsule source</h3><p>Regardez la caméra et lisez le texte guidé pendant 90 secondes. Lumière naturelle, voix claire, aucun filtre.</p><div className="record-frame"><span>Placez votre visage ici</span><i>90 s</i></div><button onClick={()=>{notify("Caméra prête pour votre propre vidéo");setStep(3)}}>▣ Ouvrir la caméra</button></>}{step===3&&<><span className="builder-icon">◇</span><h3>Ajoutez ce que votre Double vendra</h3><p>Choisissez seulement vos produits et services. Fixez prix, stock, conditions et réponses autorisées.</p><div className="product-slot"><span>＋</span><div><strong>Ajouter un produit</strong><small>Photo, vidéo, prix et disponibilité</small></div></div><button onClick={()=>setStep(4)}>Configurer sa personnalité ↗</button></>}{step===4&&<><span className="builder-icon">✦</span><h3>Donnez-lui votre ton</h3><div className="tone-grid"><button className="active">Chaleureux</button><button>Expert</button><button>Énergique</button><button>Élégant</button></div><p>Langues autorisées : Français · Lingala. Réponses commerciales seulement, aucune prise de position personnelle.</p><button onClick={()=>setStep(5)}>Prévisualiser mon Double ↗</button></>}{step===5&&<><span className="builder-icon ready">✓</span><h3>Votre Double est prêt à travailler</h3><p>Il peut présenter vos produits sur votre boutique et prendre le relais après vos directs. Vous approuvez chaque script avant publication.</p><div className="publish-options"><label><input type="checkbox" defaultChecked/> Boutique Whappy</label><label><input type="checkbox" defaultChecked/> Replay des directs</label><label><input type="checkbox"/> Réponses vidéo automatiques</label></div><button onClick={()=>notify("Double enregistré en brouillon pour votre validation")}>Enregistrer en brouillon ✦</button></>}</div></section></div>;
}

function ListingCard({ item, saved, onSave, onOpen }: { item:Listing; saved:boolean; onSave:()=>void; onOpen:()=>void }) {
  return <article className="listing-card"><button className={`save ${saved?"active":""}`} onClick={onSave}>{saved?"♥":"♡"}</button><button className={`listing-art ${item.tone}`} onClick={onOpen}><span>{item.mark}</span><small>{item.category}</small>{item.mode==="troc"&&<b>⇄ TROC</b>}</button><div><span className="seller"><i>{item.mark}</i>{item.seller}<b>✓</b><small>{item.trust}% fiable</small></span><h3>{item.title}</h3><strong>{item.price}</strong><p>⌖ {item.place}</p><button onClick={onOpen}>{item.mode==="troc"?"Proposer un échange":"Discuter"} ↗</button></div></article>;
}

function SectionTitle({ overline,title,action,onClick }: { overline:string;title:string;action:string;onClick:()=>void }) { return <div className="section-title"><div><small>{overline}</small><h3>{title}</h3></div><button onClick={onClick}>{action} ↗</button></div>; }

function LiveViewer({ live,onClose,notify }: { live:(typeof lives)[number];onClose:()=>void;notify:(text:string)=>void }) { const [heart,setHeart]=useState(false);return <div className="live-viewer"><div className={`live-video ${live.tone}`}><button className="viewer-close" onClick={onClose}>×</button><header><span><i/> EN DIRECT</span><b>{live.viewers} spectateurs</b></header><div className="viewer-host">{live.host.split(" ").map(x=>x[0]).join("").slice(0,2)}</div><div className="floating-chat"><span><b>Amina</b> Livraison possible ?</span><span><b>Junior</b> Je prends en bleu 🔥</span><span><b>Grâce</b> Très beau produit !</span></div><div className="viewer-bottom"><div><small>{live.host}</small><h2>{live.title}</h2></div><button onClick={()=>setHeart(v=>!v)} className={heart?"hearted":""}>♥</button></div></div><aside className="live-cart"><span>PRODUIT DU DIRECT</span><div className="cart-product">◇</div><h3>{live.product}</h3><strong>{live.price} FCFA</strong><p>Stock limité · Livraison disponible</p><div className="quantity"><button type="button">−</button><b>1</b><button type="button">＋</button></div><button className="buy" onClick={()=>notify("Produit ajouté au panier sécurisé")}>Acheter maintenant</button><button className="offer" onClick={()=>notify("Votre offre a été envoyée au vendeur")}>Faire une offre</button><small>◆ Paiement protégé par Whappy</small></aside></div>; }

function ActionModal({ type,onClose,onSubmit,consent,setConsent,setTwinStep,go,notify }: { type:"sell"|"seek"|"live"|"twin"|"message";onClose:()=>void;onSubmit:(e:FormEvent)=>void;consent:boolean;setConsent:(v:boolean)=>void;setTwinStep:(v:number)=>void;go:(s:Space)=>void;notify:(t:string)=>void }) {
  if(type==="twin") return null;
  const data={sell:["Vendre ou troquer","Transformez ce que vous avez en opportunité."],seek:["Publier une recherche","Décrivez clairement votre besoin."],live:["Préparer votre direct","Produits, titre et audience en un seul endroit."],message:["Faire une offre","Proposez un prix ou un échange sécurisé."]}[type];
  return <div className="modal-layer" role="dialog"><form className="action-modal" onSubmit={onSubmit}><button type="button" className="modal-close" onClick={onClose}>×</button><span className="modal-icon">{type==="sell"?"◇":type==="seek"?"⌖":type==="live"?"●":"⇄"}</span><small>WHAPPY ACTION</small><h2>{data[0]}</h2><p>{data[1]}</p>{type==="sell"&&<><label>Titre de l&apos;annonce<input required placeholder="Ex. Appareil photo hybride"/></label><div className="modal-row"><label>Mode<select defaultValue="sell"><option value="sell">Vendre</option><option>Troquer</option><option>Vendre ou troquer</option></select></label><label>Prix<input placeholder="FCFA ou échange souhaité"/></label></div><button type="button" className="upload-zone">＋ Ajouter photos ou vidéo</button></>}{type==="seek"&&<><label>Que recherchez-vous ?<textarea required placeholder="Décrivez le produit, service ou la situation…"/></label><div className="modal-row"><label>Zone<input placeholder="Quartier, ville ou à distance"/></label><label>Délai<select><option>Dès que possible</option><option>Aujourd&apos;hui</option><option>Cette semaine</option></select></label></div></>}{type==="live"&&<><label>Titre du direct<input required placeholder="Ex. Découverte de ma nouvelle collection"/></label><label>Produit à présenter<input placeholder="Sélectionner dans ma boutique"/></label><div className="live-mode"><button type="button" className="active">▣ Caméra</button><button type="button">◎ Avec mon Double IA</button></div><label className="mini-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> J&apos;utilise ma propre image ou un Double dont je contrôle les droits.</label></>}{type==="message"&&<><label>Votre proposition<input required placeholder="Votre prix ou ce que vous proposez en échange"/></label><label>Message<textarea placeholder="Ajoutez les détails de votre offre…"/></label></>}<button className="modal-submit" type="submit" onClick={()=>{if(type==="live"&&!consent)notify("Confirmez les droits sur la vidéo avant de continuer")}} disabled={type==="live"&&!consent}>{type==="live"?"Entrer dans le studio":type==="seek"?"Activer ma recherche":type==="message"?"Envoyer l'offre":"Continuer"} ↗</button>{type==="live"&&<button type="button" className="twin-link" onClick={()=>{onClose();go("twin");setTwinStep(1)}}>Créer d&apos;abord mon Double consentant</button>}</form></div>;
}
