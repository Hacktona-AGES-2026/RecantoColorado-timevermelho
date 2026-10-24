"use client";

import { useEffect, useState } from "react";
import { api, API_URL, type Status } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

// Página inicial provisória: confirma que front e back conversam.
// Dev 4 substitui pela tela da Fila.
export default function Home() {
  const [status, setStatus] = useState<Status | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    api.status().then(setStatus).catch((e: Error) => setErro(e.message));
  }, []);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">FilaVIVA</h1>
        <p className="text-muted-foreground mt-1">
          Priorização cirúrgica explicável. Quem precisa operar primeiro, e por quê.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Status da API</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2 text-sm">
          <span className="text-muted-foreground">{API_URL}</span>
          {status && (
            <div className="flex flex-wrap items-center gap-2">
              <Badge>no ar</Badge>
              <span>Data de referência: {status.dataReferencia}</span>
              <Badge variant={status.datasetEncontrado ? "secondary" : "destructive"}>
                {status.datasetEncontrado ? "dataset encontrado" : "dataset não encontrado"}
              </Badge>
            </div>
          )}
          {erro && (
            <div className="flex items-center gap-2">
              <Badge variant="destructive">fora do ar</Badge>
              <span>{erro}. Rode o backend (veja o README).</span>
            </div>
          )}
          {!status && !erro && <span>Consultando…</span>}
        </CardContent>
      </Card>
    </main>
  );
}
