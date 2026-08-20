const apkUrl = "https://whappy-d97e7.web.app/WHAPPY-Android-1.5.0-native.apk";

const features = [
  "Live amélioré : cadeaux expérimentaux gratuits, commentaires et voix activée.",
  "Groupes et chaînes plus riches : actions rapides, salons, invitations et filtres.",
  "Jeux plus réels : arcade enrichie et Ludo ajouté dans l’APK Android.",
  "APK Android 1.5.0 publié pour Android 8.0 et versions supérieures.",
];

export const dynamic = "force-static";

export default function Page() {
  return (
    <main className="deploy-page">
      <style>{`
        .deploy-page{min-height:100vh;margin:0;padding:32px;display:grid;place-items:center;background:radial-gradient(circle at top left,#f2f0ff,#ffffff 42%,#eef5ff);color:#1C1C74;font-family:Arial,Helvetica,sans-serif}
        .deploy-card{width:min(920px,100%);padding:34px;border:1px solid rgba(28,28,116,.13);border-radius:34px;background:rgba(255,255,255,.92);box-shadow:0 28px 90px rgba(28,28,116,.13)}
        .deploy-hero{display:grid;grid-template-columns:1.1fr .9fr;gap:28px;align-items:center}
        .deploy-badge{display:inline-flex;align-items:center;gap:8px;padding:9px 13px;border-radius:999px;background:rgba(28,28,116,.08);font-size:12px;font-weight:800;letter-spacing:.08em}
        .deploy-badge i{width:9px;height:9px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 6px rgba(34,197,94,.14)}
        h1{margin:18px 0 12px;font-size:clamp(40px,8vw,82px);line-height:.92;letter-spacing:-.07em}
        p{margin:0;color:rgba(28,28,116,.68);font-size:18px;line-height:1.65}
        .deploy-actions{display:flex;flex-wrap:wrap;gap:12px;margin-top:26px}
        .deploy-actions a{display:inline-flex;align-items:center;justify-content:center;min-height:48px;padding:0 18px;border-radius:16px;text-decoration:none;font-weight:900}
        .primary{background:#1C1C74;color:white;box-shadow:0 16px 36px rgba(28,28,116,.22)}
        .secondary{border:1px solid rgba(28,28,116,.18);color:#1C1C74;background:white}
        .phone{min-height:420px;border-radius:36px;padding:20px;background:#10104d;color:white;box-shadow:inset 0 0 0 8px rgba(255,255,255,.08),0 24px 70px rgba(28,28,116,.26)}
        .screen{height:100%;border-radius:26px;padding:22px;background:linear-gradient(160deg,#ffffff,#ececff);color:#1C1C74;display:flex;flex-direction:column;gap:14px}
        .live{padding:18px;border-radius:24px;background:#1C1C74;color:white}
        .gifts{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
        .gifts span,.mini{padding:12px;border-radius:16px;background:rgba(28,28,116,.08);text-align:center;font-weight:900}
        .features{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:28px}
        .features li{list-style:none;margin:0;padding:16px;border-radius:20px;background:rgba(28,28,116,.06);color:rgba(28,28,116,.78);line-height:1.45}
        .version{margin-top:22px;font-size:13px;color:rgba(28,28,116,.58)}
        @media(max-width:760px){.deploy-hero{grid-template-columns:1fr}.phone{min-height:320px}.features{grid-template-columns:1fr}.deploy-card{padding:24px}.deploy-page{padding:16px}}
      `}</style>
      <section className="deploy-card" aria-label="WHAPPY en ligne">
        <div className="deploy-hero">
          <div>
            <span className="deploy-badge"><i /> WHAPPY EST EN LIGNE</span>
            <h1>WHAPPY 1.5.0</h1>
            <p>
              La nouvelle version Android est publiée : live plus vivant, cadeaux gratuits
              en mode expérimental, voix du live, groupes, chaînes, jeux améliorés et Ludo.
            </p>
            <div className="deploy-actions">
              <a className="primary" href={apkUrl}>Télécharger l’APK Android</a>
              <a className="secondary" href="https://whappy-d97e7.web.app">Ouvrir la page Firebase</a>
            </div>
            <p className="version">APK Android 8.0+ · 75 Mo · Paiement non activé : cadeaux gratuits en test.</p>
          </div>
          <aside className="phone" aria-label="Aperçu WHAPPY">
            <div className="screen">
              <div className="live">
                <strong>LIVE · cadeaux gratuits</strong>
                <p style={{ color: "rgba(255,255,255,.72)", fontSize: 14 }}>Voix sortie haut-parleur · commentaires · salon actif</p>
              </div>
              <div className="gifts"><span>✨</span><span>💐</span><span>🔥</span><span>👑</span></div>
              <div className="mini">Groupes + Chaînes</div>
              <div className="mini">Jeux + Ludo 🎲</div>
            </div>
          </aside>
        </div>
        <ul className="features">
          {features.map((feature) => <li key={feature}>{feature}</li>)}
        </ul>
      </section>
    </main>
  );
}
