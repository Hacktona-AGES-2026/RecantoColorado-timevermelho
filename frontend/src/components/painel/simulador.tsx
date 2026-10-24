"use client";

import { useSearchParams } from "next/navigation";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SimuladorPaciente } from "./simulador-paciente";
import { SimuladorPesos } from "./simulador-pesos";

/** Precisa estar dentro de <Suspense> (usa useSearchParams). Aceita ?id=SOL-... e ?aba=pesos. */
export function Simulador() {
  const params = useSearchParams();
  const id = params.get("id");
  const aba = params.get("aba") === "pesos" && !id ? "pesos" : "paciente";

  return (
    <Tabs defaultValue={aba} className="gap-4">
      {/* no celular os rótulos quebram linha em vez de empurrar a página para o lado */}
      <TabsList className="h-auto! min-h-10 w-full sm:w-fit">
        <TabsTrigger value="paciente" className="h-auto! min-h-8 px-3 py-1 whitespace-normal sm:whitespace-nowrap">
          E se acontecer com este paciente?
        </TabsTrigger>
        <TabsTrigger value="pesos" className="h-auto! min-h-8 px-3 py-1 whitespace-normal sm:whitespace-nowrap">
          E se mudarmos os pesos?
        </TabsTrigger>
      </TabsList>
      <TabsContent value="paciente">
        <SimuladorPaciente key={id ?? "sem-id"} idInicial={id} />
      </TabsContent>
      <TabsContent value="pesos">
        <SimuladorPesos />
      </TabsContent>
    </Tabs>
  );
}
