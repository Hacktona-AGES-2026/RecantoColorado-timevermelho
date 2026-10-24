import { Suspense } from "react";
import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Nav } from "@/components/nav";
import { AssistenteFlutuante } from "@/components/assistente/assistente-flutuante";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "FilaVIVA",
  description: "Priorização cirúrgica explicável para o regulador",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="bg-muted/30 flex min-h-full flex-col">
        <Suspense fallback={<div className="bg-background h-14 border-b" />}>
          <Nav />
        </Suspense>
        <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6">{children}</main>
        <Suspense fallback={null}>
          <AssistenteFlutuante />
        </Suspense>
      </body>
    </html>
  );
}
