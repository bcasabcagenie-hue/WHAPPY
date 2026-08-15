import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import "./blue-brand.css";
import "./studio.css";
import "./wepal-brand.css";

const geist = Geist({
  variable: "--font-geist",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"),
  manifest: "/manifest.webmanifest",
  title: "Wepal App — Tout peut devenir une opportunité",
  description: "Wepal App rapproche les personnes, les opportunités, les produits, les services et les conversations.",
  icons: {
    icon: "/wepal-app-icon.svg",
    shortcut: "/wepal-app-icon.svg",
    apple: "/wepal-app-icon.svg",
  },
  openGraph: {
    title: "Wepal App — Tout peut devenir une opportunité",
    description: "Messages, appels, opportunités, ventes et services dans une seule application.",
    images: [{
      url: "/whappy-social-card.svg",
      width: 1200,
      height: 630,
      alt: "Wepal App — Messages, appels et marketplace",
    }],
    locale: "fr_FR",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Wepal App — Tout peut devenir une opportunité",
    description: "Messages, appels, opportunités, ventes et services dans une seule application.",
    images: ["/whappy-social-card.svg"],
  },
};

export const viewport: Viewport = {
  themeColor: "#2D2E83",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable} spellCheck autoCorrect="on" autoCapitalize="sentences">{children}</body>
    </html>
  );
}
