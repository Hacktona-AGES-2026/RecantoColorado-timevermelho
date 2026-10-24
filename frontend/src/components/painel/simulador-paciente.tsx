"use client";

// Aba "E se acontecer com este paciente?" do simulador.
import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, FlaskConical, LoaderCircle, Plus, Search, X } from "lucide-react";
import {
  api,
  TIPOS_EVENTO,
  type DadosClinicos,
  type EventoHipotetico,
  type ItemFila,
  type Simulacao,
  type SimulacaoRequest,
} from "@/lib/api";
import { Delta, SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ComposicaoScore } from "./composicao-score";
import { Escolha } from "./escolha";
import { Carregando, ErroApi, formatarPosicao, mensagemErro } from "./estados";

const MANTER = "manter";

const OPCOES_DOR = [
  { valor: MANTER, rotulo: "Manter como está" },
  ...Array.from({ length: 11 }, (_, i) => ({ valor: String(i), rotulo: `${i}${i === 0 ? " · sem dor" : i === 10 ? " · máxima" : ""}` })),
];

const OPCOES_LIMITACAO = [
  { valor: MANTER, rotulo: "Manter como está" },
  { valor: "nenhuma", rotulo: "Nenhuma" },
  { valor: "leve", rotulo: "Leve" },
  { valor: "moderada", rotulo: "Moderada" },
  { valor: "grave", rotulo: "Grave" },
];

const OPCOES_TIPO = TIPOS_EVENTO.map((t) => ({ valor: t.tipo, rotulo: t.rotulo }));

function rotuloTipo(tipo: string) {
  return TIPOS_EVENTO.find((t) => t.tipo === tipo)?.rotulo ?? tipo;
}

function rotuloResultado(r: string) {
  return r.replaceAll("_", " ");
}

function usaValor(tipo: string) {
  return tipo === "internacao" || tipo === "hba1c";
}

export function SimuladorPaciente({ idInicial }: { idInicial: string | null }) {
  const [paciente, setPaciente] = useState<ItemFila | null>(null);
  const [dados, setDados] = useState<DadosClinicos | null>(null);
  const [carregandoPaciente, setCarregandoPaciente] = useState(idInicial !== null);
  const [erroPaciente, setErroPaciente] = useState<string | null>(null);

  // Carrega o paciente vindo de ?id=SOL-...
  useEffect(() => {
    if (!idInicial) return;
    let ativo = true;
    api
      .paciente(idInicial)
      .then((d) => {
        if (!ativo) return;
        setPaciente(d.item);
        setDados(d.dados);
      })
      .catch((e) => ativo && setErroPaciente(mensagemErro(e)))
      .finally(() => ativo && setCarregandoPaciente(false));
    return () => {
      ativo = false;
    };
  }, [idInicial]);

  function escolher(item: ItemFila) {
    setPaciente(item);
    setDados(null);
    setErroPaciente(null);
    // dados clínicos atuais (dor, limitação) só para referência; se falhar, segue sem eles
    api
      .paciente(item.solicitacaoId)
      .then((d) => setDados(d.dados))
      .catch(() => undefined);
  }

  return (
    <div className="flex flex-col gap-6">
      {paciente ? (
        <SituacaoAtual paciente={paciente} dados={dados} onTrocar={() => setPaciente(null)} />
      ) : carregandoPaciente ? (
        <Card>
          <CardContent>
            <Carregando texto="Carregando paciente…" />
          </CardContent>
        </Card>
      ) : (
        <>
          {erroPaciente && (
            <ErroApi mensagem={`Não foi possível abrir ${idInicial}: ${erroPaciente}`} className="py-6" />
          )}
          <BuscaPaciente onEscolher={escolher} />
        </>
      )}

      {paciente && <FormularioSimulacao key={paciente.solicitacaoId} paciente={paciente} dados={dados} />}
    </div>
  );
}

function BuscaPaciente({ onEscolher }: { onEscolher: (item: ItemFila) => void }) {
  const [busca, setBusca] = useState("");
  const [resultados, setResultados] = useState<ItemFila[] | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    const termo = busca.trim();
    if (termo.length < 2) return;
    let ativo = true;
    const t = setTimeout(() => {
      setCarregando(true);
      setErro(null);
      api
        .fila({ busca: termo, tamanho: 10 })
        .then((p) => ativo && setResultados(p.itens))
        .catch((e) => ativo && setErro(mensagemErro(e)))
        .finally(() => ativo && setCarregando(false));
    }, 300);
    return () => {
      ativo = false;
      clearTimeout(t);
    };
  }, [busca]);

  const mostrar = busca.trim().length >= 2;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Escolha um paciente</CardTitle>
        <CardDescription>Busque por nome, número da solicitação, CID ou procedimento.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="relative">
          <Search className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Ex.: Maria, SOL-000123, C50, colecistectomia"
            className="h-10 pl-8"
            autoFocus
          />
          {carregando && (
            <LoaderCircle className="text-muted-foreground absolute top-1/2 right-2.5 size-4 -translate-y-1/2 animate-spin" />
          )}
        </div>
        {mostrar && erro && <ErroApi mensagem={erro} className="py-6" />}
        {mostrar && !erro && resultados && resultados.length === 0 && !carregando && (
          <p className="text-muted-foreground py-4 text-center text-sm">Nenhum paciente encontrado para “{busca}”.</p>
        )}
        {mostrar && !erro && resultados && resultados.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {resultados.map((r) => (
              <li key={r.solicitacaoId}>
                <button
                  type="button"
                  onClick={() => onEscolher(r)}
                  className="hover:bg-muted/60 flex w-full items-center gap-3 px-3 py-2 text-left text-sm"
                >
                  <SeloClasse classe={r.classe} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{r.nome}</span>
                    <span className="text-muted-foreground block truncate text-xs">
                      {r.solicitacaoId} · {r.procedimento} · CID {r.cid}
                    </span>
                  </span>
                  <span className="text-muted-foreground text-xs tabular-nums">{formatarPosicao(r.posicao)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function SituacaoAtual({
  paciente,
  dados,
  onTrocar,
}: {
  paciente: ItemFila;
  dados: DadosClinicos | null;
  onTrocar: () => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-lg">
          {paciente.nome}
          {paciente.oncologico && <Badge variant="destructive">oncológico</Badge>}
        </CardTitle>
        <CardDescription>
          {paciente.solicitacaoId} · {paciente.procedimento} · CID {paciente.cid}
          {paciente.cidDescricao ? ` (${paciente.cidDescricao})` : ""}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-x-8 gap-y-4">
        <Indicador rotulo="Classe atual">
          <SeloClasse classe={paciente.classe} className="h-8 min-w-12 text-base" />
        </Indicador>
        <Indicador rotulo="Posição">{formatarPosicao(paciente.posicao)}</Indicador>
        <Indicador rotulo="Score">{paciente.score.toFixed(1)}</Indicador>
        <Indicador rotulo="Dias em fila">{paciente.diasEmFila}</Indicador>
        {dados && (
          <>
            <Indicador rotulo="Dor atual">{dados.dor ?? "—"}</Indicador>
            <Indicador rotulo="Limitação atual">{dados.limitacao ?? "—"}</Indicador>
          </>
        )}
        <div className="ml-auto flex gap-2">
          <Button variant="outline" size="sm" nativeButton={false} render={<Link href={`/paciente/${paciente.solicitacaoId}`} />}>
            Abrir paciente
          </Button>
          <Button variant="ghost" size="sm" onClick={onTrocar}>
            Trocar paciente
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function Indicador({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-muted-foreground text-xs">{rotulo}</span>
      <span className="text-2xl font-semibold tabular-nums">{children}</span>
    </div>
  );
}

function FormularioSimulacao({ paciente, dados }: { paciente: ItemFila; dados: DadosClinicos | null }) {
  const [dor, setDor] = useState(MANTER);
  const [limitacao, setLimitacao] = useState(MANTER);
  const [eventos, setEventos] = useState<EventoHipotetico[]>([]);
  const [texto, setTexto] = useState("");
  const [resultado, setResultado] = useState<Simulacao | null>(null);
  const [simulando, setSimulando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const nadaMudou = dor === MANTER && limitacao === MANTER && eventos.length === 0 && texto.trim() === "";

  async function simular() {
    const req: SimulacaoRequest = { solicitacaoId: paciente.solicitacaoId };
    if (dor !== MANTER) req.dor = Number(dor);
    if (limitacao !== MANTER) req.limitacao = limitacao;
    if (eventos.length) req.eventos = eventos;
    if (texto.trim()) req.textoAdicional = texto.trim();
    setSimulando(true);
    setErro(null);
    try {
      setResultado(await api.simular(req));
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setSimulando(false);
    }
  }

  function limpar() {
    setDor(MANTER);
    setLimitacao(MANTER);
    setEventos([]);
    setTexto("");
    setResultado(null);
    setErro(null);
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>E se…?</CardTitle>
          <CardDescription>Monte uma situação hipotética. Nada é gravado no prontuário nem na fila.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="font-medium">
                Dor (0–10){dados?.dor != null && <span className="text-muted-foreground font-normal"> · hoje: {dados.dor}</span>}
              </span>
              <Escolha valor={dor} onChange={setDor} opcoes={OPCOES_DOR} ariaLabel="Dor" className="h-9" />
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="font-medium">
                Limitação funcional
                {dados?.limitacao && <span className="text-muted-foreground font-normal"> · hoje: {dados.limitacao}</span>}
              </span>
              <Escolha
                valor={limitacao}
                onChange={setLimitacao}
                opcoes={OPCOES_LIMITACAO}
                ariaLabel="Limitação"
                className="h-9"
              />
            </label>
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">Eventos hipotéticos</span>
            {eventos.length > 0 && (
              <ul className="flex flex-wrap gap-2">
                {eventos.map((ev, i) => (
                  <li
                    key={i}
                    className="bg-muted flex items-center gap-2 rounded-lg py-1 pr-1 pl-3 text-sm"
                  >
                    <span>
                      <span className="font-medium">{rotuloTipo(ev.tipo)}</span>
                      {ev.resultado && `: ${rotuloResultado(ev.resultado)}`}
                      {ev.valorNumerico !== undefined && ` (${ev.valorNumerico})`}
                      {ev.descricao && ` — ${ev.descricao}`}
                    </span>
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      aria-label="Remover evento"
                      onClick={() => setEventos(eventos.filter((_, j) => j !== i))}
                    >
                      <X />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            <NovoEventoHipotetico onAdicionar={(ev) => setEventos([...eventos, ev])} />
          </div>

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">Texto adicional do médico</span>
            <Textarea
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Ex.: episódio de encarceramento há 2 dias, dor intensa ao esforço"
              rows={2}
            />
            <span className="text-muted-foreground text-xs">
              Lido como se estivesse no laudo: o motor procura sinais de piora, diagnóstico e saída.
            </span>
          </label>

          <div className="flex flex-wrap items-center gap-2">
            <Button size="lg" onClick={simular} disabled={simulando || nadaMudou}>
              {simulando ? <LoaderCircle className="animate-spin" /> : <FlaskConical />}
              Simular
            </Button>
            <Button variant="ghost" size="lg" onClick={limpar} disabled={simulando}>
              Limpar
            </Button>
            {nadaMudou && <span className="text-muted-foreground text-xs">Altere pelo menos um item para simular.</span>}
          </div>
          {erro && <ErroApi mensagem={erro} className="py-6" />}
        </CardContent>
      </Card>

      {resultado && <ResultadoSimulacao resultado={resultado} />}
    </>
  );
}

function NovoEventoHipotetico({ onAdicionar }: { onAdicionar: (ev: EventoHipotetico) => void }) {
  const [tipo, setTipo] = useState(TIPOS_EVENTO[0].tipo);
  const [resultado, setResultado] = useState(TIPOS_EVENTO[0].resultados[0]);
  const [valor, setValor] = useState("");
  const [descricao, setDescricao] = useState("");

  const def = TIPOS_EVENTO.find((t) => t.tipo === tipo) ?? TIPOS_EVENTO[0];

  function trocarTipo(t: string) {
    setTipo(t);
    setResultado(TIPOS_EVENTO.find((x) => x.tipo === t)?.resultados[0] ?? "");
    setValor("");
  }

  function adicionar() {
    const ev: EventoHipotetico = { tipo };
    if (resultado) ev.resultado = resultado;
    const n = Number(valor.replace(",", "."));
    if (usaValor(tipo) && valor.trim() !== "" && !Number.isNaN(n)) ev.valorNumerico = n;
    if (descricao.trim()) ev.descricao = descricao.trim();
    onAdicionar(ev);
    setDescricao("");
    setValor("");
  }

  return (
    <div className="bg-muted/40 grid gap-2 rounded-lg border border-dashed p-3 sm:grid-cols-[1fr_1fr_auto_1.5fr_auto] sm:items-end">
      <label className="flex flex-col gap-1 text-xs">
        <span className="text-muted-foreground">Tipo</span>
        <Escolha valor={tipo} onChange={trocarTipo} opcoes={OPCOES_TIPO} ariaLabel="Tipo de evento" />
      </label>
      <label className="flex flex-col gap-1 text-xs">
        <span className="text-muted-foreground">Resultado</span>
        <Escolha
          valor={resultado}
          onChange={setResultado}
          opcoes={def.resultados.map((r) => ({ valor: r, rotulo: rotuloResultado(r) }))}
          ariaLabel="Resultado"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs">
        <span className="text-muted-foreground">{tipo === "internacao" ? "Dias" : tipo === "hba1c" ? "HbA1c %" : "Valor"}</span>
        <Input
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          disabled={!usaValor(tipo)}
          inputMode="decimal"
          className="w-full sm:w-20"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs">
        <span className="text-muted-foreground">Descrição (opcional)</span>
        <Input value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder="Ex.: dor abdominal aguda" />
      </label>
      <Button variant="outline" onClick={adicionar}>
        <Plus /> Adicionar
      </Button>
    </div>
  );
}

function ResultadoSimulacao({ resultado }: { resultado: Simulacao }) {
  const { antes, depois } = resultado;
  const delta = antes.posicao !== null && depois.posicao !== null ? antes.posicao - depois.posicao : null;

  return (
    <Card className="ring-primary/30 ring-2">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-lg">
          Resultado da simulação
          <Badge variant="outline" className="border-amber-500 text-amber-700 dark:text-amber-400">
            simulação · nada foi salvo
          </Badge>
        </CardTitle>
        {resultado.mensagem && <CardDescription className="text-base">{resultado.mensagem}</CardDescription>}
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="grid items-center gap-4 md:grid-cols-[1fr_auto_1fr]">
          <Lado titulo="Hoje" item={antes} />
          <div className="flex flex-col items-center gap-1">
            <ArrowRight className="text-muted-foreground hidden size-6 md:block" />
            <span className="text-lg">
              <Delta delta={delta} />
            </span>
            <span className="text-muted-foreground text-xs">posições</span>
          </div>
          <Lado titulo="Se acontecer" item={depois} destaque />
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <div className="flex flex-col gap-2">
            <h3 className="font-medium">Sinais novos detectados</h3>
            {resultado.sinaisNovos.length === 0 ? (
              <p className="text-muted-foreground text-sm">Nenhum sinal novo: a situação hipotética não muda a classificação.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {resultado.sinaisNovos.map((s, i) => (
                  <li key={`${s.tipo}-${i}`} className="rounded-lg border p-3 text-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{s.rotulo}</span>
                      <Badge variant="secondary">{s.categoria.replaceAll("_", " ").toLowerCase()}</Badge>
                    </div>
                    <p className="text-muted-foreground mt-1">{s.descricao}</p>
                    {s.evidencia && <p className="mt-1 text-xs italic">“{s.evidencia}”</p>}
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="flex flex-col gap-2">
            <h3 className="font-medium">Composição do score depois</h3>
            <ComposicaoScore componentes={resultado.componentesDepois} />
          </div>
        </div>

        <div className="bg-muted/50 flex flex-wrap items-center gap-3 rounded-lg p-3 text-sm">
          <span>
            Isto é só uma simulação: a fila e o prontuário <strong>não foram alterados</strong>. Se o evento aconteceu de
            fato, registre na página do paciente.
          </span>
          <Button className="ml-auto" nativeButton={false} render={<Link href={`/paciente/${antes.solicitacaoId}`} />}>
            Registrar de verdade <ArrowRight />
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function Lado({ titulo, item, destaque }: { titulo: string; item: ItemFila; destaque?: boolean }) {
  return (
    <div className={destaque ? "bg-primary/5 rounded-xl border p-4" : "rounded-xl border p-4"}>
      <p className="text-muted-foreground mb-3 text-xs font-medium tracking-wide uppercase">{titulo}</p>
      <div className="flex flex-wrap items-end gap-6">
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground text-xs">Classe</span>
          <SeloClasse classe={item.classe} className="h-9 min-w-14 text-lg" />
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground text-xs">Posição</span>
          <span className="text-3xl font-semibold tabular-nums">{formatarPosicao(item.posicao)}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground text-xs">Score</span>
          <span className="text-3xl font-semibold tabular-nums">{item.score.toFixed(1)}</span>
        </div>
      </div>
      {item.justificativa && <p className="text-muted-foreground mt-3 text-xs">{item.justificativa}</p>}
    </div>
  );
}
