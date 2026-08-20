import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import "./blue-brand.css";
import "./studio.css";
import "./whappy-brand.css";
import "./strict-blue-theme.css";
import "./performance.css";

const geist = Geist({
  variable: "--font-geist",
  subsets: ["latin"],
});

export const dynamic = "force-static";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "https://whappy-chat.docile-mesa-9203.chatgpt.site"),
  manifest: "/manifest.webmanifest",
  title: "Whappy — Tout peut devenir une opportunité",
  description: "Ouvrez WHAPPY sur le Web ou téléchargez WHAPPY Android 1.5.1 avec messagerie fiable, appels, groupes, chaînes, statuts et live.",
  icons: {
    icon: "/whappy-app-icon.png",
    shortcut: "/whappy-app-icon.png",
    apple: "/whappy-app-icon.png",
  },
  openGraph: {
    title: "WHAPPY 1.5.1 — Web et Android",
    description: "Messagerie fiable, appels, groupes, chaînes, statuts et live dans WHAPPY.",
    images: [{
      url: "/whappy-social-card.svg",
      width: 1200,
      height: 630,
      alt: "Whappy — Messages, appels et marketplace",
    }],
    locale: "fr_FR",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "WHAPPY 1.5.1 — Web et Android",
    description: "Utilisez WHAPPY sur le Web ou téléchargez la version Android 1.5.1.",
    images: ["/whappy-social-card.svg"],
  },
};

export const viewport: Viewport = {
  themeColor: "#1C1C74",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable} spellCheck autoCorrect="on" autoCapitalize="sentences">{children}</body>
    </html>
  );
}
