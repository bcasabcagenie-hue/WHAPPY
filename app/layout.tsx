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
  title: "WHAPPY 1.5.1 — Android professionnel",
  description: "Téléchargez WHAPPY Android 1.5.1 : live vocal, cadeaux gratuits expérimentaux, groupes, chaînes, messagerie, jeux et Ludo.",
  openGraph: {
    title: "WHAPPY 1.5.1 — Android",
    description: "Live vocal, cadeaux gratuits en test, groupes, chaînes, messagerie, jeux et Ludo.",
    locale: "fr_FR",
    type: "website",
  },
  twitter: {
    card: "summary",
    title: "WHAPPY 1.5.1 — Android",
    description: "Téléchargez la version Android professionnelle de WHAPPY.",
  },
};

export const viewport: Viewport = {
  themeColor: "#111827",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable} spellCheck autoCorrect="on" autoCapitalize="sentences">{children}</body>
    </html>
  );
}
