import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import "./white-green.css";
import "./studio.css";

const geist = Geist({
  variable: "--font-geist",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"),
  title: "Whappy — Tout peut devenir une opportunité",
  description: "Trouvez, vendez, troquez et diffusez en direct dans le réseau d'opportunités qui rapproche les besoins des solutions.",
  icons: {
    icon: "/whappy-logo.svg",
    shortcut: "/whappy-logo.svg",
  },
  openGraph: {
    title: "Whappy — Tout peut devenir une opportunité",
    description: "Directs marchands, vente, troc, recherche et Double vidéo consentant.",
    images: ["/whappy-social.png"],
    locale: "fr_FR",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Whappy — Tout peut devenir une opportunité",
    description: "Directs marchands, vente, troc, recherche et Double vidéo consentant.",
    images: ["/whappy-social.png"],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable}>{children}</body>
    </html>
  );
}
