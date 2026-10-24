"use client";

// "O paciente quer ser operado?": registra a resposta com quem perguntou e quando. Só informa: a posição não muda.
import { useState } from "react";
import { HeartHandshake } from "lucide-react";
import { api, type DesejoCirurgia, type RespostaCirurgia } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { mensagemErro } from "./comum";
import { cn } from "@/lib/utils";

export const ROTULO_DESEJO: Record<RespostaCirurgia, string> = {
  QUER_OPERAR: "Quer ser operado",
  NAO_QUER_OPERAR: "Não quer ser operado",
  EM_DUVIDA: "Em dúvida",
};

const COR: Record<RespostaCirurgia, string> = {
  QUER_OPERAR: "border-emerald-500 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300",
  NAO_QUER_OPERAR: "border-red-500 bg-red-500/10 text-red-800 dark:text-red-300",
  EM_DUVIDA: "border-amber-500 bg-amber-500/10 text-amber-800 dark:text-amber-300",
};

function dataHora(iso: string) {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/** Selo discreto para as tabelas. Mostra só "não quer" e "em dúvida" (o que pede atenção). */
export function SeloDesejo({ desejo }: { desejo: RespostaCirurgia | null }) {
  if (desejo !== "NAO_QUER_OPERAR" && desejo !== "EM_DUVIDA") return null;
  return (
    <span
      className={cn(
        "mt-0.5 block w-fit rounded px-1.5 text-[10px] font-medium",
        desejo === "NAO_QUER_OPERAR" ? "bg-red-100 text-red-900" : "bg-amber-100 text-amber-900",
      )}
      title="Resposta do paciente sobre a cirurgia. Não muda a posição: o regulador conversa e decide."
    >
      {desejo === "NAO_QUER_OPERAR" ? "não quer operar" : "em dúvida sobre a cirurgia"}
    </span>
  );
}

export function CartaoDesejo({
  solicitacaoId,
  atual,
  historico,
  aoRegistrar,
}: {
  solicitacaoId: string;
  atual: DesejoCirurgia | null;
  historico: DesejoCirurgia[];
  aoRegistrar: () => void;
}) {
  const [resposta, setResposta] = useState<RespostaCirurgia | null>(null);
  const [observacao, setObservacao] = useState("");
  const [autor, setAutor] = useState("regulador");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function registrar() {
    if (!resposta) return;
    setEnviando(true);
    setErro(null);
    try {
      await api.registrarDesejo(solicitacaoId, {
        resposta,
        observacao: observacao.trim() || undefined,
        autor: autor.trim() || "regulador",
      });
      setResposta(null);
      setObservacao("");
      aoRegistrar();
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <HeartHandshake className="size-4" /> O paciente quer ser operado?
        </CardTitle>
        <CardDescription>
          Registre a resposta dele. Não muda a posição na fila: “não quer” e “em dúvida” vão para a revisão do regulador.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className={cn("rounded-lg border p-2 text-sm", atual ? COR[atual.resposta] : "border-dashed")}>
          {atual ? (
            <>
              <p className="font-medium">{ROTULO_DESEJO[atual.resposta]}</p>
              <p className="text-xs opacity-80">
                {atual.autor} · {dataHora(atual.criadoEm)}
              </p>
              {atual.observacao && <p className="mt-1 text-xs italic">“{atual.observacao}”</p>}
            </>
          ) : (
            <p className="text-muted-foreground">Não informado: ninguém perguntou ainda.</p>
          )}
        </div>

        <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
          {(Object.keys(ROTULO_DESEJO) as RespostaCirurgia[]).map((r) => (
            <Button
              key={r}
              type="button"
              size="sm"
              variant={resposta === r ? "default" : "outline"}
              onClick={() => setResposta(r)}
              aria-pressed={resposta === r}
              className="h-auto min-h-7 whitespace-normal"
            >
              {ROTULO_DESEJO[r]}
            </Button>
          ))}
        </div>
        {resposta && (
          <>
            <Textarea
              value={observacao}
              onChange={(e) => setObservacao(e.target.value)}
              placeholder="Observação (opcional). Ex.: prefere tentar fisioterapia antes; vai conversar com a família."
              rows={2}
              maxLength={1000}
            />
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium">Quem registrou</span>
              <Input value={autor} onChange={(e) => setAutor(e.target.value)} maxLength={120} />
            </label>
            <Button onClick={registrar} disabled={enviando}>
              {enviando ? "Registrando…" : `Registrar: ${ROTULO_DESEJO[resposta]}`}
            </Button>
          </>
        )}
        {erro && <p className="text-destructive text-sm">{erro}</p>}

        {historico.length > 1 && (
          <div>
            <p className="mb-1 text-xs font-medium">Respostas anteriores</p>
            <ul className="divide-y text-xs">
              {historico.slice(1).map((h) => (
                <li key={h.id} className="flex flex-wrap items-center gap-2 py-1">
                  <Badge variant="outline">{ROTULO_DESEJO[h.resposta]}</Badge>
                  <span className="text-muted-foreground">
                    {h.autor} · {dataHora(h.criadoEm)}
                  </span>
                  {h.observacao && <span className="w-full italic">“{h.observacao}”</span>}
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
