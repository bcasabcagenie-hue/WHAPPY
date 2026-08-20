const apkUrl = "https://whappy-d97e7.web.app/WHAPPY-Android-1.5.0-native.apk";
const firebaseUrl = "https://whappy-d97e7.web.app";

const highlights = [
  { icon: "🎙️", title: "Live vocal", text: "Le son du live sort clairement, avec ambiance, commentaires et actions rapides." },
  { icon: "🎁", title: "Cadeaux gratuits", text: "Mode expérimental sans paiement : les cadeaux servent à tester l’expérience." },
  { icon: "👥", title: "Groupes & chaînes", text: "Espaces plus propres pour inviter, discuter, publier et organiser une communauté." },
  { icon: "🎲", title: "Jeux + Ludo", text: "Une zone jeux plus réelle, avec Ludo ajouté dans la version Android." },
];

const installSteps = [
  "Télécharge l’APK WHAPPY 1.5.0.",
  "Ouvre le fichier sur ton téléphone Android.",
  "Autorise l’installation si Android le demande.",
  "Lance WHAPPY et teste le live, les cadeaux, groupes, chaînes et jeux.",
];

const stats = [
  ["Version", "1.5.0"],
  ["Taille", "75 Mo"],
  ["Android", "8.0+"],
  ["Paiement", "Pas encore activé"],
];

export const dynamic = "force-static";

export default function Page() {
  return (
    <main className="whappy-launch">
      <style>{`
        :root{color-scheme:light}
        *{box-sizing:border-box}
        .whappy-launch{min-height:100vh;margin:0;padding:28px;background:
          radial-gradient(circle at 8% 0%,rgba(255,215,102,.38),transparent 28%),
          radial-gradient(circle at 92% 8%,rgba(94,92,230,.24),transparent 28%),
          linear-gradient(135deg,#f8f7ff 0%,#ffffff 44%,#eef6ff 100%);
          color:#171758;font-family:Arial,Helvetica,sans-serif;overflow:hidden}
        .shell{width:min(1180px,100%);margin:0 auto}
        .nav{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:28px}
        .brand{display:flex;align-items:center;gap:12px;font-weight:950;letter-spacing:-.04em}
        .brand-mark{width:44px;height:44px;border-radius:15px;display:grid;place-items:center;background:#1C1C74;color:#fff;box-shadow:0 14px 32px rgba(28,28,116,.2)}
        .pill{display:inline-flex;align-items:center;gap:8px;padding:9px 13px;border-radius:999px;background:rgba(28,28,116,.08);color:#1C1C74;font-size:12px;font-weight:900;text-transform:uppercase;letter-spacing:.08em}
        .pill i{width:9px;height:9px;border-radius:50%;background:#18b66a;box-shadow:0 0 0 6px rgba(24,182,106,.13)}
        .hero{display:grid;grid-template-columns:minmax(0,1.02fr) minmax(330px,.78fr);gap:28px;align-items:stretch}
        .panel{border:1px solid rgba(28,28,116,.12);border-radius:36px;background:rgba(255,255,255,.88);box-shadow:0 30px 90px rgba(28,28,116,.13);backdrop-filter:blur(18px)}
        .copy{padding:42px}
        h1{margin:18px 0 16px;font-size:clamp(48px,8vw,96px);line-height:.86;letter-spacing:-.085em;color:#10104d}
        .lead{max-width:700px;margin:0;color:rgba(23,23,88,.72);font-size:20px;line-height:1.62}
        .actions{display:flex;flex-wrap:wrap;gap:12px;margin-top:28px}
        .button{display:inline-flex;align-items:center;justify-content:center;min-height:54px;padding:0 20px;border-radius:18px;text-decoration:none;font-weight:950}
        .primary{background:#1C1C74;color:#fff;box-shadow:0 18px 42px rgba(28,28,116,.26)}
        .secondary{background:#fff;color:#1C1C74;border:1px solid rgba(28,28,116,.17)}
        .notice{margin-top:20px;padding:14px 16px;border-radius:18px;background:rgba(255,193,7,.16);color:#65500c;font-size:14px;line-height:1.5}
        .stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:26px}
        .stat{padding:14px;border-radius:18px;background:rgba(28,28,116,.06)}
        .stat small{display:block;color:rgba(23,23,88,.55);font-size:11px;font-weight:900;text-transform:uppercase;letter-spacing:.08em}
        .stat strong{display:block;margin-top:5px;color:#10104d;font-size:16px}
        .phone-wrap{position:relative;padding:28px;display:grid;place-items:center;min-height:610px}
        .halo{position:absolute;inset:9%;border-radius:999px;background:linear-gradient(135deg,rgba(28,28,116,.18),rgba(255,215,102,.35));filter:blur(8px)}
        .phone{position:relative;width:min(330px,100%);min-height:560px;border-radius:44px;padding:16px;background:#10104d;color:white;box-shadow:inset 0 0 0 8px rgba(255,255,255,.08),0 34px 90px rgba(16,16,77,.32)}
        .screen{min-height:528px;border-radius:32px;padding:18px;background:linear-gradient(180deg,#fff,#eeeeff);color:#10104d;display:flex;flex-direction:column;gap:12px;overflow:hidden}
        .topbar{display:flex;justify-content:space-between;align-items:center;font-size:12px;font-weight:900;color:rgba(16,16,77,.62)}
        .live-card{padding:18px;border-radius:26px;background:linear-gradient(135deg,#1C1C74,#4037b8);color:white}
        .live-card span{display:inline-flex;margin-bottom:10px;padding:6px 9px;border-radius:999px;background:rgba(255,255,255,.15);font-size:11px;font-weight:900}
        .live-card strong{display:block;font-size:22px;letter-spacing:-.04em}
        .wave{height:56px;margin-top:14px;border-radius:18px;background:repeating-linear-gradient(90deg,rgba(255,255,255,.35) 0 6px,transparent 6px 14px);opacity:.9}
        .gift-row{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
        .gift-row b{display:grid;place-items:center;min-height:54px;border-radius:18px;background:#fff;box-shadow:0 10px 22px rgba(28,28,116,.08);font-size:24px}
        .mini-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
        .mini{padding:14px;border-radius:20px;background:rgba(28,28,116,.08);font-weight:900;min-height:82px}
        .mini small{display:block;margin-top:6px;color:rgba(16,16,77,.58);font-weight:700;line-height:1.35}
        .section{margin-top:26px;padding:28px;border-radius:34px;background:rgba(255,255,255,.82);border:1px solid rgba(28,28,116,.11)}
        .section-head{display:flex;align-items:end;justify-content:space-between;gap:18px;margin-bottom:18px}
        h2{margin:0;color:#10104d;font-size:clamp(28px,4vw,44px);letter-spacing:-.06em}
        .section-head p{max-width:520px;margin:0;color:rgba(23,23,88,.66);line-height:1.55}
        .cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}
        .card{padding:18px;border-radius:24px;background:#fff;border:1px solid rgba(28,28,116,.09);box-shadow:0 14px 34px rgba(28,28,116,.07)}
        .card span{font-size:30px}.card h3{margin:12px 0 8px;color:#10104d;font-size:18px;letter-spacing:-.03em}.card p{margin:0;color:rgba(23,23,88,.65);font-size:14px;line-height:1.5}
        .install{display:grid;grid-template-columns:.88fr 1.12fr;gap:20px;align-items:center}
        .steps{counter-reset:step;display:grid;gap:10px;margin:0;padding:0}
        .steps li{counter-increment:step;list-style:none;padding:15px 16px 15px 54px;position:relative;border-radius:18px;background:rgba(28,28,116,.06);color:rgba(23,23,88,.75);line-height:1.45}
        .steps li:before{content:counter(step);position:absolute;left:14px;top:12px;width:28px;height:28px;border-radius:50%;display:grid;place-items:center;background:#1C1C74;color:#fff;font-size:13px;font-weight:950}
        .final-cta{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin-top:24px;padding:22px;border-radius:26px;background:#10104d;color:white}
        .final-cta strong{font-size:24px;letter-spacing:-.04em}.final-cta small{display:block;margin-top:5px;color:rgba(255,255,255,.68)}
        .final-cta a{background:#fff;color:#10104d}
        @media(max-width:900px){.hero,.install{grid-template-columns:1fr}.phone-wrap{min-height:auto}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}.stats{grid-template-columns:repeat(2,minmax(0,1fr))}}
        @media(max-width:580px){.whappy-launch{padding:16px}.copy,.phone-wrap,.section{padding:20px}.nav{align-items:flex-start;flex-direction:column}.cards{grid-template-columns:1fr}.phone{min-height:auto}.screen{min-height:470px}.lead{font-size:17px}.button{width:100%}.final-cta a{width:100%}}
      `}</style>

      <div className="shell">
        <nav className="nav" aria-label="Navigation WHAPPY">
          <div className="brand"><span className="brand-mark">W</span><span>WHAPPY</span></div>
          <span className="pill"><i /> Version Android publiée</span>
        </nav>

        <section className="hero" aria-label="WHAPPY Android 1.5.0">
          <div className="panel copy">
            <span className="pill"><i /> En ligne maintenant</span>
            <h1>WHAPPY 1.5.0</h1>
            <p className="lead">
              La version Android est prête à tester : live plus vivant, cadeaux gratuits
              en expérimentation, voix du live, groupes, chaînes, jeux plus réels et Ludo.
            </p>
            <div className="actions">
              <a className="button primary" href={apkUrl}>Télécharger l’APK Android</a>
              <a className="button secondary" href={firebaseUrl}>Ouvrir la page miroir</a>
            </div>
            <div className="notice">
              Important : le paiement n’est pas encore activé. Les cadeaux du live sont donc
              gratuits pour tester le comportement réel avant l’intégration paiement.
            </div>
            <div className="stats" aria-label="Informations de version">
              {stats.map(([label, value]) => (
                <div className="stat" key={label}><small>{label}</small><strong>{value}</strong></div>
              ))}
            </div>
          </div>

          <aside className="panel phone-wrap" aria-label="Aperçu de l’application WHAPPY">
            <div className="halo" />
            <div className="phone">
              <div className="screen">
                <div className="topbar"><span>16:51</span><span>WHAPPY LIVE · 54%</span></div>
                <div className="live-card">
                  <span>DIRECT GRATUIT</span>
                  <strong>Live voix + cadeaux</strong>
                  <p style={{ color: "rgba(255,255,255,.72)", fontSize: 14, marginTop: 8 }}>
                    Le public entend, réagit et envoie des cadeaux test.
                  </p>
                  <div className="wave" />
                </div>
                <div className="gift-row"><b>✨</b><b>💐</b><b>🔥</b><b>👑</b></div>
                <div className="mini-grid">
                  <div className="mini">Groupes<small>Salons, invitations, messages</small></div>
                  <div className="mini">Chaînes<small>Posts, filtres, communauté</small></div>
                  <div className="mini">Jeux<small>Plus réalistes</small></div>
                  <div className="mini">Ludo 🎲<small>Ajouté dans Android</small></div>
                </div>
              </div>
            </div>
          </aside>
        </section>

        <section className="section">
          <div className="section-head">
            <h2>Ce qui a été amélioré</h2>
            <p>WHAPPY devient plus vivant : on sent le live, les cadeaux, les communautés et le jeu dans une même expérience.</p>
          </div>
          <div className="cards">
            {highlights.map((item) => (
              <article className="card" key={item.title}>
                <span>{item.icon}</span>
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section install">
          <div>
            <span className="pill"><i /> Installation Android</span>
            <h2 style={{ marginTop: 14 }}>Comment tester</h2>
            <p className="lead" style={{ fontSize: 17 }}>
              L’APK est directement disponible. Sur Android, il peut demander l’autorisation
              d’installer une application venant du navigateur.
            </p>
          </div>
          <ol className="steps">
            {installSteps.map((step) => <li key={step}>{step}</li>)}
          </ol>
        </section>

        <div className="final-cta">
          <div><strong>Prêt à tester WHAPPY ?</strong><small>Android 8.0+ · APK 1.5.0 · cadeaux gratuits en mode expérimental</small></div>
          <a className="button" href={apkUrl}>Télécharger maintenant</a>
        </div>
      </div>
    </main>
  );
}
