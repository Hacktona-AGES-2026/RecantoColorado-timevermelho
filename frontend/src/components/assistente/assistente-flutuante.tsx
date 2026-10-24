"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { BotIcon, XIcon } from "lucide-react";
import { Chat } from "./chat";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Botão no cabeçalho que abre o chat em qualquer página. O painel abre logo abaixo do botão e, como o cabeçalho
 * fica fixo no topo, não cobre o conteúdo enquanto está fechado. Como o cabeçalho está no layout raiz, a conversa
 * continua ao navegar entre as telas. Some na própria página /assistente, que já mostra o chat inteiro.
 */
export function AssistenteFlutuante() {
  const pathname = usePathname();
  const [aberto, setAberto] = useState(false);

  useEffect(() => {
    if (!aberto) return;
    const aoTeclar = (e: KeyboardEvent) => e.key === "Escape" && setAberto(false);
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [aberto]);

  if (pathname.startsWith("/assistente")) return null;

  return (
    <div className="relative">
      {/* Continua ocupando o lugar quando o painel está aberto, para o cabeçalho não pular. */}
      <Button
        size="sm"
        className={cn("rounded-full", aberto && "invisible")}
        onClick={() => setAberto(true)}
        aria-label="Abrir o assistente"
        aria-hidden={aberto || undefined}
        tabIndex={aberto ? -1 : undefined}
      >
        <BotIcon /> Assistente
      </Button>
      {/* Fica montado (só escondido) para a conversa não se perder ao fechar o painel. */}
      <section
        aria-label="Assistente do FilaVIVA"
        hidden={!aberto}
        className="bg-card absolute top-0 right-0 z-50 flex h-[40rem] max-h-[calc(100dvh-var(--altura-cabecalho,4.5rem)-1rem)] w-[min(30rem,calc(100vw-2rem))] flex-col gap-2 rounded-2xl p-3 shadow-xl ring-1 ring-foreground/10"
      >
        <div className="flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <BotIcon className="size-4" /> Assistente do FilaVIVA
          </h2>
          <Button variant="ghost" size="icon-sm" onClick={() => setAberto(false)} aria-label="Fechar o assistente">
            <XIcon />
          </Button>
        </div>
        <Chat compacto className="flex-1" />
      </section>
    </div>
  );
}
