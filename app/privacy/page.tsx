import Link from "next/link";

export const dynamic = "force-static";

export const metadata = {
  title: "Politique de confidentialité — WAPI",
  description: "La politique de confidentialité de l’application WAPI.",
};

export default function PrivacyPage() {
  return (
    <main className="privacy-page">
      <div className="privacy-shell">
        <Link className="privacy-back" href="/">← Retour à WAPI</Link>
        <header className="privacy-hero">
          <span>WAPI · INFORMATIONS LÉGALES</span>
          <h1>Politique de confidentialité</h1>
          <p>Dernière mise à jour : 28 août 2026</p>
        </header>

        <article className="privacy-card">
          <p>WAPI (« nous », « notre application ») respecte votre vie privée. Cette politique explique quelles informations sont utilisées lorsque vous créez un compte, échangez des messages, publiez du contenu ou utilisez les appels et directs WAPI.</p>

          <h2>1. Informations que nous utilisons</h2>
          <p><strong>Compte et profil.</strong> Numéro de téléphone, identifiant de compte, nom, photo et informations que vous choisissez d’ajouter à votre profil.</p>
          <p><strong>Contenus.</strong> Messages, réactions, photos, vidéos, fichiers, annonces, commandes, chaînes et autres contenus que vous envoyez ou publiez.</p>
          <p><strong>Fonctions de l’appareil.</strong> Caméra, microphone, photos et notifications uniquement lorsque vous activez une fonction qui en a besoin. WAPI ne demande pas un accès permanent à ces fonctions.</p>
          <p><strong>Informations techniques.</strong> Type d’appareil, version de l’application, journaux d’erreur et informations nécessaires à la sécurité, au fonctionnement et à l’amélioration du service.</p>

          <h2>2. Pourquoi ces informations sont utilisées</h2>
          <ul>
            <li>Créer et sécuriser votre compte ;</li>
            <li>livrer vos messages, appels, notifications et contenus aux destinataires choisis ;</li>
            <li>permettre les groupes, chaînes, directs, annonces et commandes ;</li>
            <li>prévenir les abus, la fraude et les accès non autorisés ;</li>
            <li>corriger les erreurs et maintenir la fiabilité de l’application.</li>
          </ul>

          <h2>3. Partage et prestataires</h2>
          <p>Nous ne vendons pas vos messages ni vos données personnelles. Certaines données sont traitées par des prestataires techniques nécessaires au service, notamment Firebase pour l’authentification, la base de données, le stockage et les notifications, ainsi que l’infrastructure média WAPI/LiveKit pour les appels et directs. Ces prestataires ne peuvent utiliser ces données que pour fournir leurs services.</p>

          <h2>4. Conservation et suppression</h2>
          <p>Nous conservons les informations aussi longtemps que nécessaire au fonctionnement du compte, à la sécurité et aux obligations légales applicables. Vous pouvez demander la suppression de votre compte et de vos données en nous écrivant à l’adresse ci-dessous. Certains éléments peuvent rester temporairement dans des sauvegardes sécurisées ou être conservés lorsque la loi l’exige.</p>

          <h2>5. Vos choix</h2>
          <p>Vous pouvez gérer les autorisations caméra, microphone, photos et notifications dans les réglages de votre appareil. Vous pouvez modifier votre profil, supprimer vos publications et demander l’accès, la correction ou la suppression de vos données.</p>

          <h2>6. Enfants</h2>
          <p>WAPI n’est pas destiné aux enfants qui n’ont pas l’âge minimum requis dans leur pays. Nous supprimons les comptes dont nous apprenons qu’ils ont été créés en violation de cette règle.</p>

          <h2>7. Contact</h2>
          <p>Pour toute question ou demande relative à la confidentialité : <a href="mailto:contact@whappy.chat">contact@whappy.chat</a>.</p>

          <h2>8. Modifications</h2>
          <p>Nous pouvons mettre cette politique à jour lorsque les fonctions de WAPI évoluent. La date affichée en haut de cette page indiquera la dernière version.</p>
        </article>
      </div>
      <style>{`
        .privacy-page { min-height: 100vh; background: #f4faff; color: #10263a; padding: 32px 20px 80px; }
        .privacy-shell { max-width: 860px; margin: 0 auto; }
        .privacy-back { color: #087fce; font-weight: 700; text-decoration: none; }
        .privacy-hero { padding: 72px 0 34px; }
        .privacy-hero span { color: #087fce; font-size: 12px; font-weight: 800; letter-spacing: .14em; }
        .privacy-hero h1 { margin: 14px 0 10px; color: #082b47; font-size: clamp(38px, 7vw, 66px); line-height: .98; letter-spacing: -.05em; }
        .privacy-hero p { color: #668096; }
        .privacy-card { background: #fff; border: 1px solid #dcecf7; border-radius: 24px; box-shadow: 0 18px 60px #0a5b8712; padding: clamp(24px, 5vw, 56px); line-height: 1.7; }
        .privacy-card h2 { margin: 34px 0 10px; color: #0b5f98; font-size: 22px; line-height: 1.2; }
        .privacy-card h2:first-of-type { margin-top: 0; }
        .privacy-card p { margin: 0 0 14px; }
        .privacy-card ul { margin: 0 0 16px; padding-left: 22px; }
        .privacy-card a { color: #087fce; font-weight: 700; }
      `}</style>
    </main>
  );
}
