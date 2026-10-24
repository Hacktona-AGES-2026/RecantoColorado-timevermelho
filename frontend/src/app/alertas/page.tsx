import type { Metadata } from "next";
import { PainelAlertas } from "@/components/painel/alertas";

export const metadata: Metadata = { title: "Alertas e reavaliação · FilaVIVA" };

export default function AlertasPage() {
  return <PainelAlertas />;
}
