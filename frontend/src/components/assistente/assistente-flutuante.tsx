"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { BotIcon, XIcon } from "lucide-react";
import { Chat } from "./chat";
import { Button } from "@/components/ui/button";

/**
 * Botão no canto da tela que abre o chat em qualquer página. Como fica no layout raiz, a conversa continua
 * ao navegar entre as telas. Some na própria página /assistente, que já mostra o chat inteiro.
 */
export function AssistenteFlutuante() {
  const pathname = usePathname();
  const [aberto, setAberto] = useState(false);
  if (pathname.startsWith("/assistente")) return null;

  return (
    <div className="fixed right-4 bottom-4 z-40 flex flex-col items-end gap-2">
      {/* Fica montado (só escondido) para a conversa não se perder ao fechar o painel. */}
      <section
        aria-label="Assistente do FilaVIVA"
        hidden={!aberto}
        className="bg-card flex h-[34rem] max-h-[calc(100vh-6rem)] w-[min(26rem,calc(100vw-2rem))] flex-col gap-2 rounded-2xl p-3 shadow-xl ring-1 ring-foreground/10"
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
      {!aberto && (
        <Button size="lg" className="rounded-full shadow-lg" onClick={() => setAberto(true)} aria-label="Abrir o assistente">
          <BotIcon /> Assistente
        </Button>
      )}
    </div>
  );
}
