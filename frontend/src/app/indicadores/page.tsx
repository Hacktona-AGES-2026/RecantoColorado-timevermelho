import type { Metadata } from "next";
import { PainelIndicadores } from "@/components/painel/indicadores";

export const metadata: Metadata = { title: "Antes × depois · FilaVIVA" };

export default function IndicadoresPage() {
  return <PainelIndicadores />;
}
