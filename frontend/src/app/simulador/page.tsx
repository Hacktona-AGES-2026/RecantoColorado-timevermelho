import { Suspense } from "react";
import type { Metadata } from "next";
import { Carregando } from "@/components/painel/estados";
import { Simulador } from "@/components/painel/simulador";

export const metadata: Metadata = { title: "Simulador · FilaVIVA" };

export default function SimuladorPage() {
  return (
    <>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Simulador</h1>
        <p className="text-muted-foreground mt-1">
          Teste hipóteses antes de decidir. Nada aqui altera a fila: é só “e se?”.
        </p>
      </div>
      <Suspense fallback={<Carregando />}>
        <Simulador />
      </Suspense>
    </>
  );
}
