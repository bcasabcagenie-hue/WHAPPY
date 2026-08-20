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
  title: "WHAPPY 1.5.0 — Live, cadeaux, groupes, chaînes et Ludo",
  description: "Téléchargez WHAPPY Android 1.5.0 : live vocal, cadeaux gratuits expérimentaux, groupes, chaînes, jeux améliorés et Ludo.",
  icons: {
    icon: "/whappy-app-icon.png",
    shortcut: "/whappy-app-icon.png",
    apple: "/whappy-app-icon.png",
  },
  openGraph: {
    title: "WHAPPY 1.5.0 — Android",
    description: "Live vocal, cadeaux gratuits en test, groupes, chaînes, jeux et Ludo dans WHAPPY.",
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
    title: "WHAPPY 1.5.0 — Android",
    description: "Téléchargez la version Android avec live, cadeaux gratuits expérimentaux, groupes, chaînes et Ludo.",
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
