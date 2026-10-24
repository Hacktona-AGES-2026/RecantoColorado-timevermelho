"use client";

import { useEffect, useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { AssistenteFlutuante } from "@/components/assistente/assistente-flutuante";

const LINKS = [
  { href: "/", rotulo: "Fila" },
  { href: "/medicos", rotulo: "Médicos" },
  { href: "/alertas", rotulo: "Alertas e reavaliação" },
  { href: "/simulador", rotulo: "Simulador" },
  { href: "/indicadores", rotulo: "Antes × depois" },
  { href: "/relatorios", rotulo: "Relatórios" },
  { href: "/assistente", rotulo: "Assistente" },
];

export function Nav() {
  const pathname = usePathname();
  const cabecalho = useRef<HTMLElement>(null);

  // O cabeçalho fica fixo no topo e muda de altura quando o menu quebra linha. Publica a altura em
  // --altura-cabecalho para o que também é fixo (colunas sticky, painel do assistente) não ficar por baixo dele.
  useEffect(() => {
    const el = cabecalho.current;
    if (!el) return;
    const raiz = document.documentElement;
    const medir = () => raiz.style.setProperty("--altura-cabecalho", `${el.offsetHeight}px`);
    medir();
    const observador = new ResizeObserver(medir);
    observador.observe(el);
    return () => observador.disconnect();
  }, []);

  return (
    <header ref={cabecalho} className="bg-background sticky top-0 z-40 border-b">
      <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link href="/" className="flex items-center">
          <Image src="/logo-filaviva.png" alt="FilaVIVA" width={450} height={220} loading="eager" className="h-12 w-auto" />
        </Link>
        {/* Em telas menores: logo e assistente na 1ª linha, menu inteiro (rolando de lado) na 2ª. Em telas largas, uma linha só. */}
        <div className="ml-auto flex items-center gap-3 xl:order-last xl:flex-1 xl:gap-6">
          {/* Em telas largas fica centralizado entre o link "Assistente" do menu e o botão do assistente. */}
          <span className="text-muted-foreground hidden text-xs md:inline xl:flex-1 xl:text-center">Regulador · o sistema propõe, você decide</span>
          <AssistenteFlutuante />
        </div>
        <nav className="-mx-4 flex w-[calc(100%+2rem)] gap-1 overflow-x-auto px-4 text-sm xl:mx-0 xl:w-auto xl:shrink-0 xl:px-0">
          {LINKS.map((l) => {
            const ativo = l.href === "/" ? pathname === "/" || pathname.startsWith("/paciente") : pathname.startsWith(l.href);
            return (
              <Link
                key={l.href}
                href={l.href}
                className={cn(
                  "shrink-0 rounded-md px-3 py-1.5 whitespace-nowrap transition-colors",
                  ativo ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                {l.rotulo}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
