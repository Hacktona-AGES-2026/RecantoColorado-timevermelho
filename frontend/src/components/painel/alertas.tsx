"use client";

// Tela de alertas e reavaliação: a revisão diária do regulador.
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  ChevronDown,
  LoaderCircle,
  LogOut,
  RefreshCw,
  RotateCcw,
  Sparkles,
  TriangleAlert,
} from "lucide-react";
import {
  api,
  type Alerta,
  type Alertas,
  type ClasseSwalis,
  type GrupoReavaliacao,
  type Impacto,
  type Saida,
} from "@/lib/api";
import { SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { Carregando, ErroApi, formatarHora, formatarPosicao, mensagemErro, Vazio } from "./estados";

const ROTULO_TIPO_ALERTA: Record<Alerta["tipo"], { rotulo: string; cor: string }> = {
  EVENTO_NOVO: { rotulo: "Evento novo", cor: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-200" },
  MUDANCA_CLASSE: { rotulo: "Mudança de classe", cor: "bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-200" },
  AJUSTE_REGULADOR: { rotulo: "Ajuste do regulador", cor: "bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-200" },
};

export function PainelAlertas() {
  const [dados, setDados] = useState<Alertas | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [atualizando, setAtualizando] = useState(false);
  const [impactos, setImpactos] = useState<Impacto[] | null>(null);
  const [acaoDemo, setAcaoDemo] = useState<"cenario" | "reset" | null>(null);
  const [erroDemo, setErroDemo] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    try {
      const a = await api.alertas();
      setDados(a);
      setErro(null);
    } catch (e) {
      setErro(mensagemErro(e));
    }
  }, []);

  useEffect(() => {
    let ativo = true;
    api
      .alertas()
      .then((a) => ativo && setDados(a))
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, []);

  async function recarregar() {
    setAtualizando(true);
    await carregar();
    setAtualizando(false);
  }

  async function simularChegada() {
    setAcaoDemo("cenario");
    setErroDemo(null);
    try {
      setImpactos(await api.cenarioDemo());
      await carregar();
    } catch (e) {
      setErroDemo(mensagemErro(e));
    } finally {
      setAcaoDemo(null);
    }
  }

  async function reiniciar() {
    setAcaoDemo("reset");
    setErroDemo(null);
    try {
      await api.resetDemo();
      setImpactos(null);
      await carregar();
    } catch (e) {
      setErroDemo(mensagemErro(e));
    } finally {
      setAcaoDemo(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Alertas e reavaliação</h1>
          <p className="text-muted-foreground mt-1">
            A revisão do dia: o que mudou, quem precisa de um novo olhar e quem provavelmente já pode sair da fila.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="lg" onClick={simularChegada} disabled={acaoDemo !== null}>
            {acaoDemo === "cenario" ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
            Simular chegada de novos eventos
          </Button>
          <Button variant="outline" size="lg" onClick={reiniciar} disabled={acaoDemo !== null}>
            {acaoDemo === "reset" ? <LoaderCircle className="animate-spin" /> : <RotateCcw />}
            Reiniciar demo
          </Button>
          <Button variant="ghost" size="lg" onClick={recarregar} disabled={atualizando} aria-label="Atualizar">
            <RefreshCw className={cn(atualizando && "animate-spin")} />
          </Button>
        </div>
      </div>

      {erroDemo && <ErroApi mensagem={erroDemo} className="py-6" />}
      {impactos && <Impactos impactos={impactos} onFechar={() => setImpactos(null)} />}

      {erro && !dados ? (
        <ErroApi mensagem={erro} onTentar={recarregar} />
      ) : !dados ? (
        <Carregando texto="Carregando alertas…" />
      ) : (
        <>
          {erro && <p className="text-destructive text-sm">Falha ao atualizar: {erro}. Mostrando os últimos dados.</p>}
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
            <Recentes alertas={dados.recentes} />
            <Reavaliacao grupos={dados.reavaliacao} />
          </div>
          <Saidas saidas={dados.saidas} />
        </>
      )}
    </div>
  );
}

function verbo(i: Impacto): string {
  const { posicaoAntes: a, posicaoDepois: d } = i;
  if (d === null && a !== null) return `saiu da fila (estava na posição ${a})`;
  if (a === null && d !== null) return `entrou na fila na posição ${d}`;
  if (a === null || d === null) return "continua fora da fila";
  if (d < a) return `subiu da posição ${a} para ${d}`;
  if (d > a) return `desceu da posição ${a} para ${d}`;
  return `continua na posição ${a}`;
}

/** A mensagem vem como "Subiu da posição X para Y, classe B → A2: <motivo>"; o título já diz o movimento. */
function motivoDoImpacto(mensagem: string): string {
  const i = mensagem.indexOf(": ");
  const motivo = i >= 0 ? mensagem.slice(i + 2) : mensagem;
  return motivo.charAt(0).toLowerCase() + motivo.slice(1);
}

function Impactos({ impactos, onFechar }: { impactos: Impacto[]; onFechar: () => void }) {
  return (
    <Card className="ring-primary/30 ring-2">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Sparkles className="size-5" /> Chegaram {impactos.length} evento{impactos.length === 1 ? "" : "s"} novo
          {impactos.length === 1 ? "" : "s"}
        </CardTitle>
        <CardDescription>A fila foi recalculada na hora. Veja o impacto em cada paciente.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {impactos.length === 0 ? (
          <Vazio texto="Nenhum evento novo gerado." />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {impactos.map((i, idx) => (
              <div key={`${i.solicitacaoId}-${idx}`} className="flex flex-col gap-2 rounded-xl border p-4">
                <p className="text-base leading-snug">
                  <Link href={`/paciente/${i.solicitacaoId}`} className="font-semibold hover:underline">
                    {i.nome}
                  </Link>{" "}
                  {verbo(i)}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <TransicaoClasse antes={i.classeAntes} depois={i.classeDepois} />
                  {i.statusDepois !== i.statusAntes && (
                    <Badge variant="outline">{i.statusDepois.replaceAll("_", " ").toLowerCase()}</Badge>
                  )}
                </div>
                <p className="text-sm">
                  <span className="text-muted-foreground">porque </span>
                  {motivoDoImpacto(i.mensagem)}
                </p>
              </div>
            ))}
          </div>
        )}
        <Button variant="ghost" size="sm" className="self-end" onClick={onFechar}>
          Fechar
        </Button>
      </CardContent>
    </Card>
  );
}

function TransicaoClasse({ antes, depois }: { antes: ClasseSwalis | null; depois: ClasseSwalis | null }) {
  if (!antes && !depois) return null;
  if (antes && depois && antes === depois) return <SeloClasse classe={depois} />;
  return (
    <span className="inline-flex items-center gap-1">
      {antes ? <SeloClasse classe={antes} /> : <span className="text-muted-foreground text-xs">—</span>}
      <ArrowRight className="text-muted-foreground size-3.5" />
      {depois ? <SeloClasse classe={depois} /> : <span className="text-muted-foreground text-xs">—</span>}
    </span>
  );
}

function Recentes({ alertas }: { alertas: Alerta[] }) {
  const [limite, setLimite] = useState(15);
  return (
    <Card>
      <CardHeader>
        <CardTitle>O que mudou</CardTitle>
        <CardDescription>Eventos e decisões mais recentes, do mais novo para o mais antigo.</CardDescription>
      </CardHeader>
      <CardContent>
        {alertas.length === 0 ? (
          <Vazio texto="Nada mudou desde a última revisão." />
        ) : (
          <ol className="flex flex-col">
            {alertas.slice(0, limite).map((a) => {
              const t = ROTULO_TIPO_ALERTA[a.tipo] ?? { rotulo: a.tipo, cor: "bg-muted" };
              return (
                <li key={a.id} className="flex flex-col gap-1.5 border-b py-3 last:border-0">
                  <div className="flex flex-wrap items-center gap-2 text-xs">
                    <span className={cn("rounded px-1.5 py-0.5 font-medium", t.cor)}>{t.rotulo}</span>
                    <span className="text-muted-foreground ml-auto tabular-nums">{formatarHora(a.criadoEm)}</span>
                  </div>
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <Link href={`/paciente/${a.solicitacaoId}`} className="font-medium hover:underline">
                      {a.nome}
                    </Link>
                    <TransicaoClasse antes={a.classeAntes} depois={a.classeDepois} />
                    {(a.posicaoAntes !== null || a.posicaoDepois !== null) && a.posicaoAntes !== a.posicaoDepois && (
                      <span className="text-muted-foreground text-xs tabular-nums">
                        {formatarPosicao(a.posicaoAntes)} → {formatarPosicao(a.posicaoDepois)}
                      </span>
                    )}
                  </div>
                  <p className="text-muted-foreground text-sm">{a.mensagem}</p>
                </li>
              );
            })}
          </ol>
        )}
        {alertas.length > limite && (
          <Button variant="ghost" size="sm" className="mt-2 w-full" onClick={() => setLimite(limite + 15)}>
            Mostrar mais ({alertas.length - limite} restantes)
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

function Reavaliacao({ grupos }: { grupos: GrupoReavaliacao[] }) {
  const total = grupos.reduce((s, g) => s + g.total, 0);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          Precisa de reavaliação
          <Badge variant="secondary" className="tabular-nums">
            {total.toLocaleString("pt-BR")} sinalizações
          </Badge>
        </CardTitle>
        <CardDescription>Pacientes cujo quadro ou dados pedem um novo olhar do regulador.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {grupos.length === 0 ? (
          <Vazio texto="Ninguém precisa de reavaliação agora." />
        ) : (
          grupos.map((g, i) => <BlocoReavaliacao key={g.tipo} grupo={g} abertoInicial={i < 2} />)
        )}
      </CardContent>
    </Card>
  );
}

function BlocoReavaliacao({ grupo, abertoInicial }: { grupo: GrupoReavaliacao; abertoInicial: boolean }) {
  const [aberto, setAberto] = useState(abertoInicial);
  const [limite, setLimite] = useState(5);
  const visiveis = grupo.itens.slice(0, limite);
  return (
    <div className="rounded-xl border">
      <button
        type="button"
        onClick={() => setAberto(!aberto)}
        className="hover:bg-muted/50 flex w-full items-center gap-2 rounded-xl px-4 py-3 text-left"
        aria-expanded={aberto}
      >
        <TriangleAlert className="size-4 shrink-0 text-amber-600" />
        <span className="flex-1 font-medium">{grupo.titulo}</span>
        <Badge variant="outline" className="tabular-nums">
          {grupo.total.toLocaleString("pt-BR")}
        </Badge>
        <ChevronDown className={cn("text-muted-foreground size-4 transition-transform", aberto && "rotate-180")} />
      </button>
      {aberto && (
        <div className="border-t px-4 pb-3">
          {grupo.itens.length === 0 ? (
            <Vazio texto="Sem itens." className="py-4" />
          ) : (
            <ul className="flex flex-col">
              {visiveis.map((it) => (
                <li key={it.solicitacaoId} className="flex items-start gap-3 border-b py-2.5 last:border-0">
                  <SeloClasse classe={it.classe} className="mt-0.5" />
                  <span className="text-muted-foreground w-16 shrink-0 pt-0.5 text-xs tabular-nums">
                    {formatarPosicao(it.posicao)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <Link href={`/paciente/${it.solicitacaoId}`} className="font-medium hover:underline">
                      {it.nome}
                    </Link>
                    <span className="text-muted-foreground block text-sm">{it.mensagem}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap items-center justify-between gap-2 pt-2 text-xs">
            <span className="text-muted-foreground">
              Mostrando {visiveis.length} de {grupo.total.toLocaleString("pt-BR")}
              {grupo.total > grupo.itens.length && ` (lista traz os ${grupo.itens.length} mais urgentes)`}
            </span>
            {grupo.itens.length > limite && (
              <Button variant="ghost" size="xs" onClick={() => setLimite(limite + 10)}>
                Mostrar mais
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function Saidas({ saidas }: { saidas: Saida[] }) {
  const [limite, setLimite] = useState(12);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <LogOut className="size-4" /> Saídas sugeridas
          <Badge variant="secondary" className="tabular-nums">
            {saidas.length}
          </Badge>
        </CardTitle>
        <CardDescription>
          Indícios de que o paciente desistiu ou já operou em outro serviço. O sistema só sugere: confirme ou recuse na
          página do paciente.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {saidas.length === 0 ? (
          <Vazio texto="Nenhuma saída sugerida." />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {saidas.slice(0, limite).map((s) => (
              <div
                key={s.solicitacaoId}
                className={cn(
                  "flex flex-col gap-2 rounded-xl border p-4",
                  s.oncologico && "border-red-500 bg-red-50 dark:bg-red-950/30",
                )}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Link href={`/paciente/${s.solicitacaoId}`} className="font-semibold hover:underline">
                    {s.nome}
                  </Link>
                  <span className="text-muted-foreground text-xs">CID {s.cid}</span>
                  {s.status === "SAIDA_CONFIRMADA" && <Badge variant="secondary">saída confirmada</Badge>}
                </div>
                {s.oncologico && (
                  <div className="flex items-center gap-2 rounded-md bg-red-600 px-2 py-1 text-xs font-semibold text-white">
                    <TriangleAlert className="size-3.5" /> Oncológico: mantido na fila até confirmação
                  </div>
                )}
                <p className="text-sm">{s.motivo}</p>
                {s.evidencia && (
                  <blockquote className="text-muted-foreground border-l-2 pl-2 text-xs italic">“{s.evidencia}”</blockquote>
                )}
                {s.fonte && <p className="text-muted-foreground text-xs">Fonte: {s.fonte}</p>}
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-auto self-start"
                  nativeButton={false}
                  render={<Link href={`/paciente/${s.solicitacaoId}`} />}
                >
                  Confirmar ou recusar <ArrowRight />
                </Button>
              </div>
            ))}
          </div>
        )}
        {saidas.length > limite && (
          <Button variant="ghost" size="sm" className="mt-3 w-full" onClick={() => setLimite(limite + 12)}>
            Mostrar mais ({saidas.length - limite} restantes)
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
