import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";

const geist = Geist({
  variable: "--font-geist",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Whappy — Votre monde, connecté",
  description: "Messages, appels, groupes et découvertes réunis dans une expérience sociale nouvelle génération.",
  icons: {
    icon: "/whappy-logo.svg",
    shortcut: "/whappy-logo.svg",
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body className={geist.variable}>{children}</body>
    </html>
  );
}
