import type { Metadata } from "next";
import { Chat } from "@/components/assistente/chat";

export const metadata: Metadata = { title: "Assistente · FilaVIVA" };

export default function AssistentePage() {
  return (
    <>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Assistente</h1>
        <p className="text-muted-foreground mt-1">
          Tire dúvidas sobre consultas, sobre como usar o FilaVIVA e sobre saúde e alimentação em geral. Ele responde só
          sobre isso, não decide nada pela fila e não mostra dados de nenhum paciente.
        </p>
      </div>
      <Chat className="mx-auto w-full max-w-3xl" />
    </>
  );
}
