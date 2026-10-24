"use client";

// Aba "E se mudarmos os pesos?" do simulador.
import { useState } from "react";
import Link from "next/link";
import { FlaskConical, LoaderCircle, RotateCcw, ShieldAlert, ShieldCheck } from "lucide-react";
import { api, PESOS_PADRAO, type Movimento, type Pesos, type SimulacaoPesos } from "@/lib/api";
import { Delta, SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { ErroApi, mensagemErro, Vazio } from "./estados";

const CAMPOS: { chave: keyof Pesos; nome: string; ajuda: string }[] = [
  { chave: "diagnostico", nome: "Diagnóstico", ajuda: "gravidade do CID / biópsia" },
  { chave: "piora", nome: "Sinais de piora", ajuda: "PS, internação, progressão" },
  { chave: "dor", nome: "Dor", ajuda: "escala 0–10" },
  { chave: "limitacao", nome: "Limitação", ajuda: "impacto funcional" },
  { chave: "espera", nome: "Tempo de espera", ajuda: "% do prazo consumido" },
  { chave: "risco", nome: "ASA / risco", ajuda: "risco cirúrgico" },
];

function iguais(a: Pesos, b: Pesos) {
  return CAMPOS.every((c) => a[c.chave] === b[c.chave]);
}

export function SimuladorPesos() {
  const [pesos, setPesos] = useState<Pesos>(PESOS_PADRAO);
  const [resultado, setResultado] = useState<SimulacaoPesos | null>(null);
  const [simulando, setSimulando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const soma = CAMPOS.reduce((s, c) => s + pesos[c.chave], 0);
  const padrao = iguais(pesos, PESOS_PADRAO);

  async function simular() {
    setSimulando(true);
    setErro(null);
    try {
      setResultado(await api.simularPesos(pesos));
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setSimulando(false);
    }
  }

  function voltarPadrao() {
    setPesos(PESOS_PADRAO);
    setResultado(null);
    setErro(null);
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Pesos do score</CardTitle>
          <CardDescription>
            Os pesos só reordenam pacientes <strong>dentro da mesma classe clínica</strong>. A urgência clínica (A1 &gt;
            A2 &gt; B &gt; C &gt; D) vem sempre antes, e um paciente com câncer nunca fica abaixo de alguém sem urgência.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">
          <div className="grid gap-x-8 gap-y-5 sm:grid-cols-2 lg:grid-cols-3">
            {CAMPOS.map((c) => {
              const mudou = pesos[c.chave] !== PESOS_PADRAO[c.chave];
              return (
                <label key={c.chave} className="flex flex-col gap-1.5 text-sm">
                  <span className="flex items-baseline justify-between gap-2">
                    <span>
                      <span className="font-medium">{c.nome}</span>{" "}
                      <span className="text-muted-foreground text-xs">{c.ajuda}</span>
                    </span>
                    <span className={cn("text-lg font-semibold tabular-nums", mudou && "text-primary underline")}>
                      {pesos[c.chave]}
                    </span>
                  </span>
                  <input
                    type="range"
                    min={0}
                    max={50}
                    step={1}
                    value={pesos[c.chave]}
                    onChange={(e) => setPesos({ ...pesos, [c.chave]: Number(e.target.value) })}
                    className="accent-primary w-full cursor-pointer"
                    aria-label={c.nome}
                  />
                  <span className="text-muted-foreground text-xs">padrão: {PESOS_PADRAO[c.chave]}</span>
                </label>
              );
            })}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button size="lg" onClick={simular} disabled={simulando}>
              {simulando ? <LoaderCircle className="animate-spin" /> : <FlaskConical />}
              Simular
            </Button>
            <Button variant="outline" size="lg" onClick={voltarPadrao} disabled={simulando || (padrao && !resultado)}>
              <RotateCcw /> Voltar ao padrão
            </Button>
            <span className="text-muted-foreground ml-auto text-xs">
              Soma dos pesos: {soma} (o score é normalizado para 0–100). Nada é salvo.
            </span>
          </div>
          {erro && <ErroApi mensagem={erro} className="py-6" />}
        </CardContent>
      </Card>

      {resultado && <ResultadoPesos resultado={resultado} />}
    </div>
  );
}

function ResultadoPesos({ resultado }: { resultado: SimulacaoPesos }) {
  const ok = resultado.regraDeOuroRespeitada;
  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardContent className="flex flex-col gap-1">
            <span className="text-muted-foreground text-sm">Pacientes que mudaram de posição</span>
            <span className="text-5xl font-semibold tabular-nums">
              {resultado.pacientesQueMudaram.toLocaleString("pt-BR")}
            </span>
            <span className="text-muted-foreground text-xs">sempre dentro da própria classe clínica</span>
          </CardContent>
        </Card>
        <div
          className={cn(
            "flex items-center gap-4 rounded-xl p-5 text-white",
            ok ? "bg-emerald-600" : "bg-red-600",
          )}
        >
          {ok ? <ShieldCheck className="size-12 shrink-0" /> : <ShieldAlert className="size-12 shrink-0" />}
          <div>
            <p className="text-2xl font-semibold">
              {ok ? "Regra de ouro respeitada" : "Regra de ouro violada"}
            </p>
            <p className="text-sm opacity-90">
              {ok
                ? "Nenhum paciente com câncer ficou abaixo de alguém sem urgência."
                : "Com estes pesos algum paciente oncológico ficaria abaixo de alguém sem urgência."}
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <TabelaMovimentos titulo="Maiores subidas" movimentos={resultado.maioresSubidas} />
        <TabelaMovimentos titulo="Maiores quedas" movimentos={resultado.maioresQuedas} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Topo da fila com os pesos novos</CardTitle>
        </CardHeader>
        <CardContent>
          {resultado.topo.length === 0 ? (
            <Vazio texto="Fila vazia." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-16">Posição</TableHead>
                  <TableHead>Classe</TableHead>
                  <TableHead>Paciente</TableHead>
                  <TableHead className="hidden md:table-cell">Procedimento</TableHead>
                  <TableHead className="text-right">Score</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {resultado.topo.map((i, idx) => (
                  <TableRow key={i.solicitacaoId}>
                    <TableCell className="font-medium tabular-nums">{i.posicao ?? idx + 1}º</TableCell>
                    <TableCell>
                      <SeloClasse classe={i.classe} />
                    </TableCell>
                    <TableCell>
                      <Link href={`/paciente/${i.solicitacaoId}`} className="font-medium hover:underline">
                        {i.nome}
                      </Link>
                      {i.oncologico && (
                        <Badge variant="destructive" className="ml-2">
                          oncológico
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground hidden max-w-72 truncate md:table-cell">
                      {i.procedimento}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{i.score.toFixed(1)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function TabelaMovimentos({ titulo, movimentos }: { titulo: string; movimentos: Movimento[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{titulo}</CardTitle>
      </CardHeader>
      <CardContent>
        {movimentos.length === 0 ? (
          <Vazio texto="Ninguém se moveu." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Paciente</TableHead>
                <TableHead>Classe</TableHead>
                <TableHead className="text-right">Antes → depois</TableHead>
                <TableHead className="text-right">Δ</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {movimentos.map((m) => (
                <TableRow key={m.solicitacaoId}>
                  <TableCell className="max-w-48 truncate">
                    <Link href={`/paciente/${m.solicitacaoId}`} className="font-medium hover:underline">
                      {m.nome}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <SeloClasse classe={m.classe} />
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {m.posicaoAntes}º → {m.posicaoDepois}º
                  </TableCell>
                  <TableCell className="text-right">
                    <Delta delta={m.delta} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
