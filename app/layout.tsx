import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import "./blue-brand.css";
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
    icon: "/whappy-app-icon.svg",
    shortcut: "/whappy-app-icon.svg",
    apple: "/whappy-app-icon.svg",
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

export const viewport: Viewport = {
  themeColor: "#00A2E6",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable}>{children}</body>
    </html>
  );
}
