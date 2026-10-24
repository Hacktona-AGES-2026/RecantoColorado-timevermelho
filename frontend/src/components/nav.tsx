"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/", rotulo: "Fila" },
  { href: "/medicos", rotulo: "Médicos" },
  { href: "/alertas", rotulo: "Alertas e reavaliação" },
  { href: "/simulador", rotulo: "Simulador" },
  { href: "/indicadores", rotulo: "Antes × depois" },
];

export function Nav() {
  const pathname = usePathname();
  return (
    <header className="border-b bg-background">
      <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link href="/" className="flex items-baseline gap-2">
          <span className="text-lg font-semibold tracking-tight">FilaVIVA</span>
          <span className="text-muted-foreground hidden text-xs sm:inline">priorização cirúrgica explicável</span>
        </Link>
        <nav className="flex flex-wrap gap-1 text-sm">
          {LINKS.map((l) => {
            const ativo = l.href === "/" ? pathname === "/" || pathname.startsWith("/paciente") : pathname.startsWith(l.href);
            return (
              <Link
                key={l.href}
                href={l.href}
                className={cn(
                  "rounded-md px-3 py-1.5 transition-colors",
                  ativo ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                {l.rotulo}
              </Link>
            );
          })}
        </nav>
        <span className="text-muted-foreground ml-auto text-xs">Regulador · o sistema propõe, você decide</span>
      </div>
    </header>
  );
}
