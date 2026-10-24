import type { Metadata } from "next";
import { TelaRelatorios } from "@/components/relatorios/tela-relatorios";

export const metadata: Metadata = { title: "Relatórios · FilaVIVA" };

export default function RelatoriosPage() {
  return (
    <>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Relatórios para médicos</h1>
        <p className="text-muted-foreground mt-1">
          Resumos da fila escritos por inteligência artificial a partir de números agregados da aplicação. No relatório
          por procedimento, a lista de pacientes é montada pelo sistema direto do dataset. Sempre revise antes de usar.
        </p>
      </div>
      <TelaRelatorios />
    </>
  );
}
