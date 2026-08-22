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
  icons: {
    icon: "/favicon.svg",
    apple: "/whappy-app-icon.png",
  },
  title: "Wapi — Tout peut devenir une opportunité",
  description: "Wapi réunit messages privés, communautés, vidéos personnalisées, Business et doubles IA avec contrôle humain.",
  openGraph: {
    title: "Wapi — Tout peut devenir une opportunité",
    description: "Messages, communautés, vidéos et Business dans une même expérience Wapi.",
    locale: "fr_FR",
    type: "website",
    images: ["/whappy-social-card.svg"],
  },
  twitter: {
    card: "summary",
    title: "Wapi — Tout peut devenir une opportunité",
    description: "Messages, communautés, vidéos et Business dans une même expérience Wapi.",
  },
};

export const viewport: Viewport = {
  themeColor: "#0094F0",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable} spellCheck autoCorrect="on" autoCapitalize="sentences">{children}</body>
    </html>
  );
}
