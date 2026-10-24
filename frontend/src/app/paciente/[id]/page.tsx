import { Suspense } from "react";
import { TelaPaciente } from "@/components/fila/tela-paciente";
import { Carregando } from "@/components/fila/comum";

// Tela 2: "por que este paciente está nesta posição?". O id vem de useParams (precisa de Suspense).
// Obs.: para o build passar, o layout raiz precisa envolver o <Nav> (usePathname) em <Suspense>.
export default function Page() {
  return (
    <Suspense fallback={<Carregando texto="Carregando paciente…" />}>
      <TelaPaciente />
    </Suspense>
  );
}
