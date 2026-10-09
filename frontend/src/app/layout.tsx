import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "BIS AI Assistant",
  description:
    "AI-powered assistant for Bureau of Indian Standards — find IS standards, certification guidance, hallmarking info, and more.",
  keywords: ["BIS", "Indian Standards", "IS certification", "ISI mark", "hallmarking"],
  icons: { icon: "/favicon.ico" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body className="bg-gray-50 text-gray-900 antialiased">{children}</body>
    </html>
  );
}
