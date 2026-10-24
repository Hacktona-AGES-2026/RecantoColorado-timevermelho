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
      <TabsList className="h-10! w-full sm:w-fit">
        <TabsTrigger value="paciente" className="px-3">
          E se acontecer com este paciente?
        </TabsTrigger>
        <TabsTrigger value="pesos" className="px-3">
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
