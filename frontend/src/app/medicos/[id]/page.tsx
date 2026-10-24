import { Suspense } from "react";
import { TelaMedico } from "@/components/medicos/tela-medico";
import { Carregando } from "@/components/fila/comum";

// Lista do médico. useParams e a fila completa (useSearchParams) precisam de Suspense.
export default function Page() {
  return (
    <Suspense fallback={<Carregando texto="Carregando a lista do médico…" />}>
      <TelaMedico />
    </Suspense>
  );
}
