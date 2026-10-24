"use client";

// Painel "Antes × depois": ordem de hoje (prioridade manual + data de entrada) × FilaVIVA.
import { useEffect, useState } from "react";
import { ArrowRight, Database } from "lucide-react";
import { api, CLASSES, INFO_CLASSE, type ClasseSwalis, type Indicadores } from "@/lib/api";
import { SeloClasse } from "@/components/fila-ui";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { Carregando, ErroApi, mensagemErro, Vazio } from "./estados";
import { GraficoFaixas } from "./grafico-faixas";

const n = (v: number) => v.toLocaleString("pt-BR");

export function PainelIndicadores() {
  const [dados, setDados] = useState<Indicadores | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [tentativa, setTentativa] = useState(0);

  useEffect(() => {
    let ativo = true;
    api
      .indicadores()
      .then((d) => {
        if (!ativo) return;
        setDados(d);
        setErro(null);
      })
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [tentativa]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Antes × depois</h1>
          <p className="text-muted-foreground mt-1">
            A fila de hoje (prioridade manual + data de entrada) comparada à fila do FilaVIVA (classe clínica + score
            explicável).
          </p>
        </div>
        {dados && (
          <div className="flex gap-6 text-right">
            <div>
              <p className="text-3xl font-semibold tabular-nums">{n(dados.totalAtivos)}</p>
              <p className="text-muted-foreground text-xs">pacientes ativos na fila</p>
            </div>
            <div>
              <p className="text-3xl font-semibold tabular-nums">{n(dados.totalForaDaFila)}</p>
              <p className="text-muted-foreground text-xs">fora da fila (saída sugerida/confirmada)</p>
            </div>
          </div>
        )}
      </div>

      {erro && !dados ? (
        <ErroApi mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
      ) : !dados ? (
        <Carregando texto="Calculando indicadores…" />
      ) : (
        <Conteudo dados={dados} />
      )}
    </div>
  );
}

function Conteudo({ dados }: { dados: Indicadores }) {
  const onco = dados.faixas.map((f) => ({ faixa: f.faixa, antes: f.oncoAntes, depois: f.oncoDepois }));
  const semUrg = dados.faixas.map((f) => ({ faixa: f.faixa, antes: f.semUrgenciaAntes, depois: f.semUrgenciaDepois }));

  return (
    <>
      {dados.comparacoes.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {dados.comparacoes.map((c) => (
            <Card key={c.indicador}>
              <CardContent className="flex h-full flex-col gap-2">
                <p className="text-muted-foreground text-sm font-medium">{c.indicador}</p>
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="text-muted-foreground text-xl tabular-nums line-through decoration-1">{c.antes}</span>
                  <ArrowRight className="text-muted-foreground size-4 self-center" />
                  <span className="text-3xl font-semibold tabular-nums text-blue-700 dark:text-blue-400">{c.depois}</span>
                </div>
                <p className="text-muted-foreground mt-auto text-xs">{c.descricao}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Pacientes oncológicos por décimo da fila</CardTitle>
            <CardDescription>
              Hoje os pacientes com câncer estão espalhados pela fila. No FilaVIVA eles se concentram no topo.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {onco.length ? <GraficoFaixas dados={onco} /> : <Vazio texto="Sem dados por faixa." />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Pacientes sem urgência por décimo da fila</CardTitle>
            <CardDescription>
              Hoje casos sem urgência ocupam posições do topo. No FilaVIVA eles vão para o fim, sem perder a vez entre si.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {semUrg.length ? <GraficoFaixas dados={semUrg} /> : <Vazio texto="Sem dados por faixa." />}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <DistribuicaoClasse porClasse={dados.porClasse} />
        <Matriz dados={dados} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <Regioes dados={dados} />
        <QualidadeDados dados={dados} />
      </div>
    </>
  );
}

function DistribuicaoClasse({ porClasse }: { porClasse: Record<ClasseSwalis, number> }) {
  const total = CLASSES.reduce((s, c) => s + (porClasse[c] ?? 0), 0);
  const max = Math.max(1, ...CLASSES.map((c) => porClasse[c] ?? 0));
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Fila por classe clínica</CardTitle>
        <CardDescription>Quantos pacientes ativos em cada classe SWALIS (prazo máximo para operar).</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {CLASSES.map((c) => {
          const v = porClasse[c] ?? 0;
          const pct = total ? (v / total) * 100 : 0;
          return (
            <div key={c} className="grid grid-cols-[3rem_1fr_6.5rem] items-center gap-3" title={INFO_CLASSE[c].nome}>
              <SeloClasse classe={c} />
              <div className="bg-muted h-5 overflow-hidden rounded">
                <div className={cn("h-full rounded", INFO_CLASSE[c].cor)} style={{ width: `${(v / max) * 100}%` }} />
              </div>
              <span className="text-right text-sm tabular-nums">
                <span className="font-semibold">{n(v)}</span>{" "}
                <span className="text-muted-foreground text-xs">({pct.toFixed(1)}%)</span>
              </span>
            </div>
          );
        })}
        <p className="text-muted-foreground text-xs">
          Prazos: {CLASSES.map((c) => `${c} ${INFO_CLASSE[c].prazo} dias`).join(" · ")}
        </p>
      </CardContent>
    </Card>
  );
}

const ROTULO_PRIORIDADE: Record<string, string> = {
  alta: "Alta",
  media: "Média",
  baixa: "Baixa",
  "sem prioridade": "Sem prioridade",
};

function Matriz({ dados }: { dados: Indicadores }) {
  const max = Math.max(1, ...dados.matriz.flatMap((l) => CLASSES.map((c) => l.porClasse[c] ?? 0)));
  const baixa = dados.matriz.find((l) => l.prioridadeManual === "baixa");
  const baixaUrgente = baixa ? (baixa.porClasse.A1 ?? 0) + (baixa.porClasse.A2 ?? 0) : 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Prioridade manual × classe clínica</CardTitle>
        <CardDescription>
          {baixaUrgente > 0 ? (
            <>
              <strong className="text-foreground">{n(baixaUrgente)}</strong> pacientes marcados hoje como prioridade{" "}
              <strong className="text-foreground">baixa</strong> são, pelo quadro clínico, A1 ou A2.
            </>
          ) : (
            "Como a prioridade marcada à mão se distribui pelas classes clínicas."
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {dados.matriz.length === 0 ? (
          <Vazio texto="Sem dados." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Prioridade hoje</TableHead>
                {CLASSES.map((c) => (
                  <TableHead key={c} className="text-center">
                    <SeloClasse classe={c} />
                  </TableHead>
                ))}
                <TableHead className="text-right">Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {dados.matriz.map((l) => {
                const totalLinha = CLASSES.reduce((s, c) => s + (l.porClasse[c] ?? 0), 0);
                return (
                  <TableRow key={l.prioridadeManual} className="hover:bg-transparent">
                    <TableCell className="font-medium">
                      {ROTULO_PRIORIDADE[l.prioridadeManual] ?? l.prioridadeManual}
                    </TableCell>
                    {CLASSES.map((c) => {
                      const v = l.porClasse[c] ?? 0;
                      const alerta = l.prioridadeManual === "baixa" && (c === "A1" || c === "A2") && v > 0;
                      const intensidade = v / max;
                      return (
                        <TableCell
                          key={c}
                          className={cn(
                            "text-center tabular-nums",
                            intensidade > 0.55 && "text-white",
                            alerta && "font-bold ring-2 ring-red-600 ring-inset",
                          )}
                          style={{
                            backgroundColor: v ? `rgb(37 99 235 / ${0.08 + intensidade * 0.8})` : undefined,
                          }}
                          title={`${ROTULO_PRIORIDADE[l.prioridadeManual] ?? l.prioridadeManual} × ${c}: ${n(v)} pacientes`}
                        >
                          {v ? n(v) : "·"}
                        </TableCell>
                      );
                    })}
                    <TableCell className="text-muted-foreground text-right tabular-nums">{n(totalLinha)}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
        <p className="text-muted-foreground mt-3 text-xs">
          Quanto mais escura a célula, mais pacientes. Contorno vermelho: marcados como “baixa” mas clinicamente urgentes.
        </p>
      </CardContent>
    </Card>
  );
}

function Regioes({ dados }: { dados: Indicadores }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Equidade: posição média por grupo</CardTitle>
        <CardDescription>
          Onde cada grupo de pacientes fica, em média, na ordem de hoje e no FilaVIVA. Número menor = mais perto da
          cirurgia.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {dados.porRegiao.length === 0 ? (
          <Vazio texto="Sem dados por grupo." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Grupo</TableHead>
                <TableHead className="text-right">Pacientes</TableHead>
                <TableHead className="text-right">Posição média hoje</TableHead>
                <TableHead className="text-right">No FilaVIVA</TableHead>
                <TableHead className="text-right">Diferença</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {dados.porRegiao.map((g) => {
                const dif = Math.round(g.posicaoMediaAntes - g.posicaoMediaDepois);
                return (
                  <TableRow key={g.grupo}>
                    <TableCell className="font-medium whitespace-normal">{g.grupo}</TableCell>
                    <TableCell className="text-right tabular-nums">{n(g.pacientes)}</TableCell>
                    <TableCell className="text-muted-foreground text-right tabular-nums">
                      {n(Math.round(g.posicaoMediaAntes))}
                    </TableCell>
                    <TableCell className="text-right font-medium tabular-nums">
                      {n(Math.round(g.posicaoMediaDepois))}
                    </TableCell>
                    <TableCell
                      className={cn(
                        "text-right text-xs font-medium tabular-nums",
                        dif > 0 ? "text-emerald-600" : dif < 0 ? "text-rose-600" : "text-muted-foreground",
                      )}
                    >
                      {dif === 0 ? "=" : `${dif > 0 ? "▲" : "▼"} ${n(Math.abs(dif))}`}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function QualidadeDados({ dados }: { dados: Indicadores }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Database className="size-4" /> Qualidade dos dados
        </CardTitle>
        <CardDescription>Defeitos encontrados e tratados antes de priorizar.</CardDescription>
      </CardHeader>
      <CardContent>
        {dados.qualidadeDados.length === 0 ? (
          <Vazio texto="Nenhum defeito registrado." />
        ) : (
          <ul className="flex flex-col">
            {dados.qualidadeDados.map((q) => (
              <li key={q.item} className="flex items-baseline justify-between gap-3 border-b py-2 text-sm last:border-0">
                <span>{q.item}</span>
                <span className="font-semibold tabular-nums">{n(q.quantidade)}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
