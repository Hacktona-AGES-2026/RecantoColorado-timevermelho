// Peças visuais comuns a todas as telas (selo de classe, barra de prazo, mudança de posição).
import { INFO_CLASSE, type ClasseSwalis } from "@/lib/api";
import { cn } from "@/lib/utils";

export function SeloClasse({ classe, className }: { classe: ClasseSwalis; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex h-6 min-w-9 items-center justify-center rounded-md px-1.5 text-xs font-semibold",
        INFO_CLASSE[classe].cor,
        className,
      )}
      title={INFO_CLASSE[classe].nome}
    >
      {classe}
    </span>
  );
}

/** Barra do % do prazo da classe já consumido. Passa de 100% = prazo estourado. */
export function BarraPrazo({ percentual, className }: { percentual: number; className?: string }) {
  const largura = Math.min(percentual, 100);
  const cor = percentual >= 100 ? "bg-red-500" : percentual >= 75 ? "bg-amber-500" : "bg-emerald-500";
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className="bg-muted h-1.5 w-16 overflow-hidden rounded-full">
        <div className={cn("h-full rounded-full", cor)} style={{ width: `${largura}%` }} />
      </div>
      <span className={cn("text-xs tabular-nums", percentual >= 100 && "font-medium text-red-600")}>
        {Math.round(percentual)}%
      </span>
    </div>
  );
}

/** Quantas posições o paciente andou em relação à ordem de hoje. */
export function Delta({ delta }: { delta: number | null }) {
  if (delta === null) return <span className="text-muted-foreground text-xs">—</span>;
  if (delta === 0) return <span className="text-muted-foreground text-xs">=</span>;
  const subiu = delta > 0;
  return (
    <span className={cn("text-xs font-medium tabular-nums", subiu ? "text-emerald-600" : "text-rose-600")}>
      {subiu ? "▲" : "▼"} {Math.abs(delta)}
    </span>
  );
}

export function formatarData(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [a, m, d] = iso.slice(0, 10).split("-");
  return `${d}/${m}/${a}`;
}
