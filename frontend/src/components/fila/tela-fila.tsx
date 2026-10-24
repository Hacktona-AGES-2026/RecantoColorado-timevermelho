"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ChevronLeftIcon, ChevronRightIcon, DownloadIcon, RefreshCwIcon, SearchIcon } from "lucide-react";
import {
  api,
  CLASSES,
  FilaMudouErro,
  INFO_CLASSE,
  type ClasseSwalis,
  type FiltrosFila,
  type ItemFila,
  type Pagina,
  type SnapshotInfo,
  type StatusFila,
} from "@/lib/api";
import { BarraPrazo, Delta, IndicadorRebaixamento, SeloClasse } from "@/components/fila-ui";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { SeloDesejo } from "./desejo-cirurgia";
import { SeloPosicaoEscolhida } from "@/components/medicos/prioridade-medico";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { CampoSelect, Carregando, ErroApi, mensagemErro, rotuloStatus, Vazio } from "./comum";
import { cn } from "@/lib/utils";

const TAMANHO = 50;

const OPCOES_STATUS = [
  { valor: "ATIVA", rotulo: "Fila ativa" },
  { valor: "SAIDA_SUGERIDA", rotulo: "Saídas sugeridas" },
  { valor: "SAIDA_CONFIRMADA", rotulo: "Saídas confirmadas" },
  { valor: "TODOS", rotulo: "Todos" },
];

const OPCOES_CLASSE = [{ valor: "", rotulo: "Todas as classes" }, ...CLASSES.map((c) => ({ valor: c, rotulo: INFO_CLASSE[c].nome }))];

const ROTULO_PRIORIDADE: Record<string, string> = { alta: "Alta", media: "Média", baixa: "Baixa" };

interface Filtros {
  busca: string;
  classe: ClasseSwalis | "";
  especialidade: string;
  oncologico: boolean;
  status: StatusFila | "TODOS";
  pagina: number;
}

function lerFiltros(sp: URLSearchParams): Filtros {
  const classe = sp.get("classe");
  const status = sp.get("status");
  return {
    busca: sp.get("busca") ?? "",
    classe: classe && (CLASSES as string[]).includes(classe) ? (classe as ClasseSwalis) : "",
    especialidade: sp.get("especialidade") ?? "",
    oncologico: sp.get("onco") === "1",
    status: status && OPCOES_STATUS.some((o) => o.valor === status) ? (status as StatusFila | "TODOS") : "ATIVA",
    pagina: Math.max(0, Number(sp.get("p") ?? 0) || 0),
  };
}

function escreverFiltros(f: Filtros): string {
  const p = new URLSearchParams();
  if (f.busca) p.set("busca", f.busca);
  if (f.classe) p.set("classe", f.classe);
  if (f.especialidade) p.set("especialidade", f.especialidade);
  if (f.oncologico) p.set("onco", "1");
  if (f.status !== "ATIVA") p.set("status", f.status);
  if (f.pagina > 0) p.set("p", String(f.pagina));
  return p.toString();
}

type Resultado = { chave: string; dados: Pagina<ItemFila> | null; erro: string | null };

function formatarHorario(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", {
    timeZone: "America/Sao_Paulo",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function TelaFila() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filtros = useMemo(() => lerFiltros(new URLSearchParams(searchParams.toString())), [searchParams]);
  const chave = escreverFiltros(filtros);

  const [buscaDigitada, setBuscaDigitada] = useState(filtros.busca);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [recarga, setRecarga] = useState(0);
  // Último resultado bom, para a tabela não "piscar" enquanto a próxima página carrega.
  const [ultimo, setUltimo] = useState<Pagina<ItemFila> | null>(null);
  const [baixando, setBaixando] = useState<"oficial" | "operacional" | null>(null);
  const [avisoDownload, setAvisoDownload] = useState<{ tipo: "ok" | "conflito" | "erro"; texto: string } | null>(null);

  const mudar = useCallback(
    (parcial: Partial<Filtros>) => {
      const novo = { ...filtros, pagina: 0, ...parcial };
      const qs = escreverFiltros(novo);
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [filtros, pathname, router],
  );

  // Busca com debounce.
  useEffect(() => {
    if (buscaDigitada.trim() === filtros.busca) return;
    const t = setTimeout(() => mudar({ busca: buscaDigitada.trim() }), 350);
    return () => clearTimeout(t);
  }, [buscaDigitada, filtros.busca, mudar]);

  useEffect(() => {
    let ativo = true;
    const req: FiltrosFila = {
      busca: filtros.busca || undefined,
      classe: filtros.classe || undefined,
      especialidade: filtros.especialidade || undefined,
      oncologico: filtros.oncologico || undefined,
      status: filtros.status,
      pagina: filtros.pagina,
      tamanho: TAMANHO,
    };
    const chaveReq = `${chave}#${recarga}`;
    api
      .fila(req)
      .then((dados) => {
        if (!ativo) return;
        setResultado({ chave: chaveReq, dados, erro: null });
        setUltimo(dados);
      })
      .catch((e) => {
        if (ativo) setResultado({ chave: chaveReq, dados: null, erro: mensagemErro(e) });
      });
    return () => {
      ativo = false;
    };
  }, [chave, recarga, filtros]);

  const carregando = resultado?.chave !== `${chave}#${recarga}`;
  const pagina = resultado?.dados ?? (carregando ? ultimo : null);
  const erro = !carregando ? resultado?.erro : null;

  const totalAtivos = pagina ? CLASSES.reduce((s, c) => s + (pagina.porClasse?.[c] ?? 0), 0) : null;
  const totalPaginas = pagina ? Math.max(1, Math.ceil(pagina.total / TAMANHO)) : 1;
  const opcoesEsp = [
    { valor: "", rotulo: "Todas as especialidades" },
    ...(pagina?.especialidades ?? []).map((e) => ({ valor: e, rotulo: e })),
  ];
  const filtrosAtivos =
    !!filtros.busca || !!filtros.classe || !!filtros.especialidade || filtros.oncologico || filtros.status !== "ATIVA";
  // a versão da fila que está na tela (vem do backend)
  const snapshot: SnapshotInfo | undefined = pagina?.snapshot;

  async function exportarOficial() {
    setBaixando("oficial");
    setAvisoDownload(null);
    try {
      const r = await api.exportarCsvOficial();
      setAvisoDownload({ tipo: "ok", texto: `Baixado ${r.arquivo} — fila de referência (${r.snapshot.split(";")[1]?.trim()}). Este é o arquivo da banca.` });
    } catch (e) {
      setAvisoDownload({ tipo: "erro", texto: mensagemErro(e) });
    } finally {
      setBaixando(null);
    }
  }

  async function exportarOperacional() {
    setBaixando("operacional");
    setAvisoDownload(null);
    try {
      const r = await api.exportarCsvOperacional(snapshot?.versao);
      setAvisoDownload({
        tipo: "ok",
        texto: `Baixado ${r.arquivo} — fila operacional ${snapshot?.versao ?? ""}. Não é o CSV de entrega para o verificador oficial.`,
      });
    } catch (e) {
      if (e instanceof FilaMudouErro) {
        setAvisoDownload({
          tipo: "conflito",
          texto: `A fila mudou desde que esta tela foi carregada (versão ${snapshot?.versao ?? "?"}). Clique em "Atualizar" e exporte de novo. Detalhe: ${e.message}`,
        });
      } else {
        setAvisoDownload({ tipo: "erro", texto: mensagemErro(e) });
      }
    } finally {
      setBaixando(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Fila cirúrgica priorizada</h1>
          <p className="text-muted-foreground text-sm">
            Ordenada por classe SWALIS e score. Clique num paciente para ver por que ele está nessa posição.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={() => setRecarga((r) => r + 1)} disabled={carregando}>
            <RefreshCwIcon className={cn(carregando && "animate-spin")} />
            Atualizar
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={exportarOperacional}
            disabled={!!baixando || carregando}
            title="Contém eventos registrados após o dataset original. Não utilizar como CSV de entrega para o verificador oficial."
          >
            <DownloadIcon />
            {baixando === "operacional" ? "Exportando…" : "Exportar CSV operacional"}
          </Button>
          <Button
            size="sm"
            onClick={exportarOficial}
            disabled={!!baixando}
            title="fila_priorizada.csv: fila de referência, só o dataset oficial, sem eventos registrados depois. É o arquivo da banca."
          >
            <DownloadIcon />
            {baixando === "oficial" ? "Exportando…" : "Exportar CSV oficial — Banca"}
          </Button>
        </div>
      </div>

      {/* Qual fila está na tela */}
      <div className="flex flex-col gap-1 rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm dark:border-sky-900 dark:bg-sky-950/40">
        <p className="font-medium">Fila operacional — atualizada com novos eventos clínicos</p>
        {snapshot && (
          <p className="text-muted-foreground text-xs">
            Versão <span className="font-mono font-medium text-foreground">{snapshot.versao}</span>
            {" · "}atualizada às {formatarHorario(snapshot.calculadoEm)}
            {" · "}
            {snapshot.eventosRegistrados} evento(s) e {snapshot.decisoesRegulador} decisão(ões) registrados nesta sessão
          </p>
        )}
        <p className="text-xs text-amber-800 dark:text-amber-300">
          As posições podem diferir da fila de referência utilizada na entrega oficial.
        </p>
        <p className="text-muted-foreground text-xs">
          <strong>Exportar CSV operacional:</strong> contém eventos registrados após o dataset original. Não utilizar como
          CSV de entrega para o verificador oficial. <strong>Exportar CSV oficial — Banca:</strong> fila_priorizada.csv,
          só o dataset de referência.
        </p>
      </div>

      {avisoDownload && (
        <div
          role="status"
          className={cn(
            "rounded-lg border px-3 py-2 text-sm",
            avisoDownload.tipo === "ok" && "border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200",
            avisoDownload.tipo === "conflito" && "border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
            avisoDownload.tipo === "erro" && "border-red-200 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200",
          )}
        >
          {avisoDownload.texto}
        </div>
      )}

      {/* Resumo por classe */}
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
        <button
          onClick={() => mudar({ classe: "" })}
          className={cn(
            "bg-card ring-foreground/10 flex flex-col items-start rounded-xl p-3 text-left ring-1 transition-colors hover:bg-muted/50",
            !filtros.classe && "ring-primary ring-2",
          )}
        >
          <span className="text-muted-foreground text-xs">Na fila</span>
          <span className="text-2xl font-semibold tabular-nums">{totalAtivos ?? "—"}</span>
        </button>
        {CLASSES.map((c) => (
          <button
            key={c}
            onClick={() => mudar({ classe: filtros.classe === c ? "" : c })}
            className={cn(
              "bg-card ring-foreground/10 flex flex-col items-start gap-1 rounded-xl p-3 text-left ring-1 transition-colors hover:bg-muted/50",
              filtros.classe === c && "ring-primary ring-2",
            )}
            title={`Filtrar classe ${INFO_CLASSE[c].nome}`}
          >
            <div className="flex items-center gap-2">
              <SeloClasse classe={c} />
              <span className="text-muted-foreground text-xs">até {INFO_CLASSE[c].prazo} d</span>
            </div>
            <span className="text-xl font-semibold tabular-nums">{pagina?.porClasse?.[c] ?? "—"}</span>
          </button>
        ))}
      </div>

      {/* Filtros */}
      <Card size="sm">
        <CardContent className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1.4fr_1fr_auto]">
          <div className="relative">
            <SearchIcon className="text-muted-foreground pointer-events-none absolute top-2 left-2.5 size-4" />
            <Input
              value={buscaDigitada}
              onChange={(e) => setBuscaDigitada(e.target.value)}
              placeholder="Buscar nome, solicitação, CID ou procedimento"
              className="pl-8"
              aria-label="Buscar"
            />
          </div>
          <CampoSelect
            rotulo="Classe"
            valor={filtros.classe}
            aoMudar={(v) => mudar({ classe: v as ClasseSwalis | "" })}
            opcoes={OPCOES_CLASSE}
          />
          <CampoSelect
            rotulo="Especialidade"
            valor={filtros.especialidade}
            aoMudar={(v) => mudar({ especialidade: v })}
            opcoes={opcoesEsp}
          />
          <CampoSelect
            rotulo="Status"
            valor={filtros.status}
            aoMudar={(v) => mudar({ status: v as StatusFila | "TODOS" })}
            opcoes={OPCOES_STATUS}
          />
          <label className="flex h-8 cursor-pointer items-center gap-2 text-sm whitespace-nowrap select-none">
            <input
              type="checkbox"
              className="accent-primary size-4"
              checked={filtros.oncologico}
              onChange={(e) => mudar({ oncologico: e.target.checked })}
            />
            Só oncológicos
          </label>
        </CardContent>
      </Card>

      {/* Tabela */}
      <Card className="py-0">
        {erro ? (
          <ErroApi erro={erro} aoTentar={() => setRecarga((r) => r + 1)} />
        ) : !pagina ? (
          <Carregando texto="Carregando a fila…" />
        ) : pagina.itens.length === 0 ? (
          <div className="flex flex-col items-center">
            <Vazio texto="Nenhum paciente encontrado com esses filtros." />
            {filtrosAtivos && (
              <Button
                variant="outline"
                size="sm"
                className="mb-8"
                onClick={() => {
                  setBuscaDigitada("");
                  router.replace(pathname, { scroll: false });
                }}
              >
                Limpar filtros
              </Button>
            )}
          </div>
        ) : (
          <div className={cn("transition-opacity", carregando && "opacity-60")}>
            <TabelaFila itens={pagina.itens} />
          </div>
        )}
        {pagina && pagina.total > 0 && !erro && (
          <div className="flex flex-wrap items-center justify-between gap-2 border-t px-4 py-2 text-sm">
            <span className="text-muted-foreground">
              {pagina.total} paciente{pagina.total === 1 ? "" : "s"} · página {filtros.pagina + 1} de {totalPaginas}
            </span>
            <div className="flex gap-1">
              <Button
                variant="outline"
                size="sm"
                disabled={filtros.pagina === 0 || carregando}
                onClick={() => mudar({ pagina: filtros.pagina - 1 })}
              >
                <ChevronLeftIcon />
                Anterior
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={filtros.pagina + 1 >= totalPaginas || carregando}
                onClick={() => mudar({ pagina: filtros.pagina + 1 })}
              >
                Próxima
                <ChevronRightIcon />
              </Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}

function TabelaFila({ itens }: { itens: ItemFila[] }) {
  const router = useRouter();
  return (
    <Table className="text-[13px]">
      <TableHeader>
        <TableRow className="bg-muted/40 hover:bg-muted/40">
          <TableHead className="w-12 text-right">#</TableHead>
          <TableHead title="Mudança em relação à ordem de hoje">Δ hoje</TableHead>
          <TableHead>Classe</TableHead>
          <TableHead>Paciente</TableHead>
          <TableHead>Procedimento</TableHead>
          <TableHead>CID</TableHead>
          <TableHead>Espera / prazo</TableHead>
          <TableHead className="text-right">Score</TableHead>
          <TableHead title="Prioridade manual na ordem de hoje">Prior. hoje</TableHead>
          <TableHead>Principal motivo</TableHead>
          <TableHead className="w-8" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {itens.map((it) => {
          const href = `/paciente/${encodeURIComponent(it.solicitacaoId)}`;
          return (
            <TableRow key={it.solicitacaoId} className="cursor-pointer" onClick={() => router.push(href)}>
              <TableCell className="text-right font-medium tabular-nums">
                {it.posicao ?? <span className="text-muted-foreground text-xs">{rotuloStatus(it.status)}</span>}
                <SeloPosicaoEscolhida posicao={it.posicaoFixada} />
              </TableCell>
              <TableCell>
                <Delta delta={it.delta} />
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <SeloClasse classe={it.classe} />
                  {it.ajustada && (
                    <span
                      className="text-muted-foreground text-[10px] font-medium"
                      title={`Classe definida pelo regulador (calculada: ${it.classeCalculada})`}
                    >
                      ✎ reg.
                    </span>
                  )}
                </div>
              </TableCell>
              <TableCell className="max-w-56">
                <Link
                  href={href}
                  className="block truncate font-medium hover:underline"
                  onClick={(e) => e.stopPropagation()}
                >
                  {it.nome}
                </Link>
                <span className="text-muted-foreground text-xs">
                  {it.solicitacaoId}
                  {it.idade !== null && ` · ${it.idade} a`}
                  {it.sexo && ` · ${it.sexo}`}
                </span>
                <SeloDesejo desejo={it.desejoCirurgia} />
                {it.status === "SAIDA_SUGERIDA" && (
                  <span
                    className="mt-0.5 block w-fit rounded bg-amber-100 px-1.5 text-[10px] font-medium text-amber-900"
                    title="Há sinal de desistência ou de cirurgia em outro serviço. Continua na fila até o regulador decidir."
                  >
                    saída sugerida · aguarda regulador
                  </span>
                )}
              </TableCell>
              <TableCell className="max-w-56">
                <span className="block truncate" title={it.procedimento}>
                  {it.procedimento}
                </span>
                <span className="text-muted-foreground block truncate text-xs">{it.especialidade}</span>
              </TableCell>
              <TableCell title={it.cidDescricao ?? undefined}>
                {it.oncologico ? (
                  <Badge variant="destructive" className="font-semibold">
                    {it.cid} · onco
                  </Badge>
                ) : (
                  <span className="font-mono text-xs">{it.cid}</span>
                )}
              </TableCell>
              <TableCell>
                <span className="text-xs tabular-nums">
                  {it.diasEmFila} d <span className="text-muted-foreground">/ {it.prazoDias} d</span>
                </span>
                <BarraPrazo percentual={it.percentualPrazo} />
              </TableCell>
              <TableCell className="text-right font-medium tabular-nums">{it.score.toFixed(1)}</TableCell>
              <TableCell className="text-xs">
                {it.prioridadeManual ? (
                  ROTULO_PRIORIDADE[it.prioridadeManual] ?? it.prioridadeManual
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </TableCell>
              <TableCell className="max-w-72">
                <span className="block truncate text-xs" title={it.justificativa || it.motivos[0]?.texto}>
                  {it.motivos[0]?.texto ?? <span className="text-muted-foreground">—</span>}
                </span>
              </TableCell>
              <TableCell>
                <div className="flex gap-1">
                  {it.movidoPelaRegraDeOuro && (
                    <span
                      className="rounded bg-red-500/10 px-1 text-[10px] font-semibold text-red-700 dark:text-red-400"
                      title="Posição limitada pela regra de ouro: sem urgência pela definição oficial, fica abaixo de todos os oncológicos"
                    >
                      ★ regra
                    </span>
                  )}
                  <IndicadorRebaixamento estado={it.rebaixamento} />
                  {it.reavaliacoes > 0 && (
                    <span
                      className="rounded bg-amber-500/15 px-1 text-[10px] font-semibold text-amber-700 dark:text-amber-400"
                      title={`${it.reavaliacoes} motivo(s) para reavaliar`}
                    >
                      ⚠ {it.reavaliacoes}
                    </span>
                  )}
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
