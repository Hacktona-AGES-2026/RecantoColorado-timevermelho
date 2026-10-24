// Barras com a composição do score (0–100) por componente.
import type { Componente } from "@/lib/api";

export function ComposicaoScore({ componentes }: { componentes: Componente[] }) {
  if (componentes.length === 0) return <p className="text-muted-foreground text-sm">Sem componentes.</p>;
  const total = componentes.reduce((s, c) => s + c.pontos, 0);
  return (
    <div className="flex flex-col gap-3">
      {componentes.map((c) => {
        const pct = c.maximo > 0 ? Math.min(100, (c.pontos / c.maximo) * 100) : 0;
        return (
          <div key={c.chave} className="flex flex-col gap-1">
            <div className="flex items-baseline justify-between gap-2 text-sm">
              <span className="font-medium">{c.nome}</span>
              <span className="tabular-nums">
                {c.pontos.toFixed(1)} <span className="text-muted-foreground">/ {c.maximo}</span>
              </span>
            </div>
            <div className="bg-muted h-2 overflow-hidden rounded-full">
              <div className="bg-primary h-full rounded-full" style={{ width: `${pct}%` }} />
            </div>
            {c.explicacao && <p className="text-muted-foreground text-xs">{c.explicacao}</p>}
          </div>
        );
      })}
      <div className="flex justify-between border-t pt-2 text-sm font-semibold">
        <span>Total</span>
        <span className="tabular-nums">{total.toFixed(1)}</span>
      </div>
    </div>
  );
}
