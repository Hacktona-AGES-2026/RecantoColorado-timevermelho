import type { Metadata } from "next";
import { TelaMedicos } from "@/components/medicos/tela-medicos";

export const metadata: Metadata = { title: "Médicos · FilaVIVA" };

export default function MedicosPage() {
  return <TelaMedicos />;
}
