import { Suspense } from "react";
import { TelaFila } from "@/components/fila/tela-fila";
import { Carregando } from "@/components/fila/comum";

// Tela 1: a fila priorizada do regulador. Os filtros ficam na URL (useSearchParams → precisa de Suspense).
export default function Page() {
  return (
    <Suspense fallback={<Carregando texto="Carregando a fila…" />}>
      <TelaFila />
    </Suspense>
  );
}
