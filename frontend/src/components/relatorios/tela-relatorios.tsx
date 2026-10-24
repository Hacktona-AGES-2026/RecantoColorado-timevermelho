"use client";

import { useCallback, useEffect, useState } from "react";
import { DownloadIcon, FileTextIcon, Loader2Icon } from "lucide-react";
import {
  agenteApi,
  ErroAgente,
  type PedidoRelatorio,
  type Relatorio,
  type RelatorioResumo,
  type TipoRelatorio,
} from "@/lib/agente";
import { formatarData } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

const CLASSE_SELECT =
  "border-input bg-background h-8 w-full rounded-lg border px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

function mensagemDe(e: unknown): string {
  if (e instanceof ErroAgente) return e.message;
  return "Não consegui falar com o servidor. Tente de novo.";
}

/**
 * Pedir, ver, baixar e reabrir relatórios. O texto é sempre mostrado como texto puro (nunca HTML) e vem com o
 * aviso de que foi gerado por IA. Os relatórios ficam salvos no servidor enquanto o backend estiver no ar.
 */
export function TelaRelatorios() {
  const [tipo, setTipo] = useState<TipoRelatorio>("VISAO_GERAL");
  const [especialidades, setEspecialidades] = useState<string[]>([]);
  const [especialidade, setEspecialidade] = useState("");
  const [procedimentos, setProcedimentos] = useState<string[]>([]);
  const [procedimento, setProcedimento] = useState("");
  const [solicitante, setSolicitante] = useState("");
  const [gerando, setGerando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [atual, setAtual] = useState<Relatorio | null>(null);
  const [historico, setHistorico] = useState<RelatorioResumo[]>([]);

  const carregarHistorico = useCallback(() => {
    agenteApi
      .relatorios()
      .then(setHistorico)
      .catch(() => setHistorico([]));
  }, []);

  useEffect(() => {
    let ativo = true;
    agenteApi
      .especialidades()
      .then((r) => {
        if (!ativo) return;
        setEspecialidades(r.especialidades);
        setEspecialidade((atual) => atual || r.especialidades[0] || "");
      })
      .catch(() => undefined);
    agenteApi
      .procedimentos()
      .then((r) => {
        if (!ativo) return;
        setProcedimentos(r.procedimentos);
        setProcedimento((atual) => atual || r.procedimentos[0] || "");
      })
      .catch(() => undefined);
    carregarHistorico();
    return () => {
      ativo = false;
    };
  }, [carregarHistorico]);

  async function gerar() {
    setGerando(true);
    setErro(null);
    const pedido: PedidoRelatorio = {
      tipo,
      especialidade: tipo === "ESPECIALIDADE" ? especialidade : undefined,
      procedimento: tipo === "PROCEDIMENTO" ? procedimento : undefined,
      solicitante: solicitante.trim() || undefined,
    };
    try {
      setAtual(await agenteApi.gerarRelatorio(pedido));
      carregarHistorico();
    } catch (e) {
      setErro(mensagemDe(e));
    } finally {
      setGerando(false);
    }
  }

  async function abrir(id: string) {
    setErro(null);
    try {
      setAtual(await agenteApi.relatorio(id));
    } catch (e) {
      setErro(mensagemDe(e));
    }
  }

  const podeGerar =
    !gerando &&
    (tipo === "VISAO_GERAL" || (tipo === "ESPECIALIDADE" && especialidade !== "") || (tipo === "PROCEDIMENTO" && procedimento !== ""));

  return (
    <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Novo relatório</CardTitle>
            <CardDescription>
              Escolha o que resumir. A IA só vê números agregados; no relatório por procedimento, a lista de pacientes é
              montada pelo sistema direto do dataset.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">Tipo</span>
              <select className={CLASSE_SELECT} value={tipo} onChange={(e) => setTipo(e.target.value as TipoRelatorio)}>
                <option value="VISAO_GERAL">Visão geral da fila</option>
                <option value="ESPECIALIDADE">Por especialidade</option>
                <option value="PROCEDIMENTO">Por procedimento (com a lista de pacientes)</option>
              </select>
            </label>
            {tipo === "ESPECIALIDADE" && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="font-medium">Especialidade</span>
                <select className={CLASSE_SELECT} value={especialidade} onChange={(e) => setEspecialidade(e.target.value)}>
                  {especialidades.map((e) => (
                    <option key={e} value={e}>
                      {e}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {tipo === "PROCEDIMENTO" && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="font-medium">Procedimento</span>
                <select className={CLASSE_SELECT} value={procedimento} onChange={(e) => setProcedimento(e.target.value)}>
                  {procedimentos.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">Seu nome (opcional)</span>
              <Input value={solicitante} onChange={(e) => setSolicitante(e.target.value)} maxLength={60} />
            </label>
            <Button onClick={() => void gerar()} disabled={!podeGerar}>
              {gerando ? <Loader2Icon className="animate-spin" /> : <FileTextIcon />}
              {gerando ? "Gerando…" : "Gerar relatório"}
            </Button>
            {erro && (
              <p className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm" role="alert">
                {erro}
              </p>
            )}
          </CardContent>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>Relatórios gerados</CardTitle>
          </CardHeader>
          <CardContent>
            {historico.length === 0 ? (
              <p className="text-muted-foreground text-sm">Nenhum relatório ainda.</p>
            ) : (
              <ul className="divide-y">
                {historico.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => void abrir(r.id)}
                      className="hover:bg-muted/60 flex w-full flex-col gap-0.5 px-1 py-2 text-left text-sm"
                    >
                      <span className="font-medium">{r.titulo}</span>
                      <span className="text-muted-foreground text-xs">
                        {formatarData(r.criadoEm)} · {r.solicitante}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="min-h-80">
        {atual ? (
          <>
            <CardHeader>
              <CardTitle className="flex flex-wrap items-center gap-2 text-lg">
                {atual.titulo}
                <Badge variant="outline" className="border-amber-500 text-amber-700 dark:text-amber-400">
                  gerado por IA · revisar antes de usar
                </Badge>
              </CardTitle>
              <CardDescription>
                {formatarData(atual.criadoEm)} · pedido por {atual.solicitante}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {/* Texto puro: nada vindo da IA é interpretado como HTML. */}
              <pre className="bg-background rounded-lg border p-3 font-sans text-sm leading-relaxed whitespace-pre-wrap break-words">
                {atual.texto}
              </pre>
              <div>
                <a href={agenteApi.urlDownloadRelatorio(atual.id)} download className={buttonVariants({ variant: "outline", size: "sm" })}>
                  <DownloadIcon /> Baixar em Markdown
                </a>
              </div>
            </CardContent>
          </>
        ) : (
          <CardContent className="text-muted-foreground flex flex-1 items-center justify-center text-sm">
            Gere um relatório ou abra um da lista para ver aqui.
          </CardContent>
        )}
      </Card>
    </div>
  );
}
