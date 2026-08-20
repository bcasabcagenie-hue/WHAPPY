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
  description: "Whappy réunit messages privés, communautés, vidéos personnalisées, Business et doubles IA avec contrôle humain.",
  openGraph: {
    title: "Whappy — Tout peut devenir une opportunité",
    description: "Messages, communautés, vidéos et Business dans une même expérience Whappy.",
    locale: "fr_FR",
    type: "website",
  },
  twitter: {
    card: "summary",
    title: "Whappy — Tout peut devenir une opportunité",
    description: "Messages, communautés, vidéos et Business dans une même expérience Whappy.",
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
