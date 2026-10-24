"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeftIcon, AlertTriangleIcon, ChevronDownIcon, RefreshCwIcon } from "lucide-react";
import { api, type PacienteDetalhe, type SinalDto } from "@/lib/api";
import { BarraPrazo, Delta, formatarData, SeloClasse } from "@/components/fila-ui";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Carregando, ErroApi, mensagemErro, rotuloStatus, Vazio } from "./comum";
import { PainelDecisao } from "./painel-decisao";
import { FormEvento, rotuloResultado, rotuloTipoEvento } from "./form-evento";
import { cn } from "@/lib/utils";

type Resultado = { chave: string; dados: PacienteDetalhe | null; erro: string | null };

export function TelaPaciente() {
  const params = useParams<{ id: string }>();
  const id = decodeURIComponent(params.id);
  const [recarga, setRecarga] = useState(0);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  const chave = `${id}#${recarga}`;
  useEffect(() => {
    let ativo = true;
    api
      .paciente(id)
      .then((dados) => ativo && setResultado({ chave, dados, erro: null }))
      .catch((e) => ativo && setResultado((prev) => ({ chave, dados: prev?.dados ?? null, erro: mensagemErro(e) })));
    return () => {
      ativo = false;
    };
  }, [id, chave]);

  const carregando = resultado?.chave !== chave;
  // Ao recarregar depois de uma decisão, mantemos os dados antigos na tela (e o impacto visível).
  const mesmoPaciente = resultado?.dados?.item.solicitacaoId === id;
  const detalhe = mesmoPaciente ? resultado?.dados : null;
  const recarregar = () => setRecarga((r) => r + 1);

  const voltar = (
    <Link href="/" className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm">
      <ArrowLeftIcon className="size-4" /> Voltar para a fila
    </Link>
  );

  if (!detalhe) {
    if (carregando) return <Carregando texto="Carregando paciente…" />;
    return (
      <div className="flex flex-col gap-4">
        {voltar}
        <Card>
          <ErroApi erro={resultado?.erro ?? "Paciente não encontrado"} aoTentar={recarregar} />
        </Card>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        {voltar}
        <Button variant="ghost" size="sm" onClick={recarregar} disabled={carregando}>
          <RefreshCwIcon className={cn(carregando && "animate-spin")} /> Atualizar
        </Button>
      </div>
      {!carregando && resultado?.erro && (
        <p className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm">
          Não foi possível atualizar: {resultado.erro}
        </p>
      )}

      <Cabecalho d={detalhe} />

      {detalhe.reavaliacoes.length > 0 && (
        <div className="flex flex-col gap-2">
          {detalhe.reavaliacoes.map((r, i) => (
            <div
              key={`${r.tipo}-${i}`}
              className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm"
            >
              <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-amber-600" />
              <div>
                <p className="font-medium">{r.titulo}</p>
                <p className="text-muted-foreground">{r.mensagem}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex min-w-0 flex-col gap-4">
          <PorQueClasse d={detalhe} />
          <ComposicaoScore d={detalhe} />
          <Sinais sinais={detalhe.sinais} />
          <LinhaDoTempo d={detalhe} />
          <DadosClinicosCard d={detalhe} />
          <Auditoria d={detalhe} />
        </div>
        <div className="flex flex-col gap-4 lg:sticky lg:top-[calc(var(--altura-cabecalho,0px)+1rem)] lg:self-start">
          <PainelDecisao key={detalhe.item.solicitacaoId} detalhe={detalhe} aoDecidir={recarregar} />
          <FormEvento key={`ev-${detalhe.item.solicitacaoId}`} solicitacaoId={detalhe.item.solicitacaoId} aoRegistrar={recarregar} />
        </div>
      </div>
    </div>
  );
}

function Cabecalho({ d }: { d: PacienteDetalhe }) {
  const { item, dados, classificacao } = d;
  const local = [dados.municipio ?? item.municipio, dados.uf].filter(Boolean).join("/");
  const pessoa = [
    item.idade !== null ? `${item.idade} anos` : null,
    item.sexo,
    local || null,
    dados.distanciaKm !== null ? `${Math.round(dados.distanciaKm)} km` : null,
  ]
    .filter(Boolean)
    .join(" · ");
  return (
    <Card>
      <CardContent className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_auto]">
        <div className="flex min-w-0 flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{item.nome}</h1>
            {item.oncologico && <Badge variant="destructive">Oncológico</Badge>}
            {item.status !== "ATIVA" && <Badge variant="outline">{rotuloStatus(item.status)}</Badge>}
            {item.movidoPelaRegraDeOuro && (
              <Badge
                variant="secondary"
                title="Sem urgência pela definição oficial (dor e limitação baixas, sem evento de piora): fica abaixo de todos os oncológicos"
              >
                ★ Posição limitada pela regra de ouro
              </Badge>
            )}
          </div>
          <p className="text-muted-foreground text-sm">
            Solicitação <span className="text-foreground font-mono">{item.solicitacaoId}</span>
            {pessoa && ` · ${pessoa}`}
          </p>
          <div className="text-sm">
            <p className="font-medium">{item.procedimento}</p>
            <p className="text-muted-foreground">
              {item.especialidade}
              {dados.porte && ` · porte ${dados.porte}`}
            </p>
          </div>
          <p className="text-sm">
            <span className={cn("font-mono font-semibold", item.oncologico && "text-destructive")}>{item.cid}</span>
            {item.cidDescricao && <span className="text-muted-foreground"> — {item.cidDescricao}</span>}
            {dados.cidSecundario && <span className="text-muted-foreground"> · secundário {dados.cidSecundario}</span>}
          </p>
        </div>

        <div className="flex flex-wrap items-stretch gap-3 md:flex-nowrap">
          <div className="bg-muted/50 flex min-w-36 flex-col items-center justify-center gap-1 rounded-xl p-3 text-center">
            <span className="text-muted-foreground text-xs">Posição na fila</span>
            <span className="text-4xl font-semibold tabular-nums">{item.posicao ?? "—"}</span>
            <span className="text-muted-foreground text-xs">
              hoje: {item.posicaoManual !== null ? `${item.posicaoManual}º` : "—"}
            </span>
            <Delta delta={item.delta} />
          </div>
          <div className="bg-muted/50 flex min-w-44 flex-col items-center justify-center gap-1.5 rounded-xl p-3 text-center">
            <SeloClasse classe={item.classe} className="h-10 min-w-14 text-xl" />
            <span className="max-w-48 text-xs font-medium">{classificacao.descricaoClasse}</span>
            <span className="text-muted-foreground text-xs">prazo {classificacao.prazoDias} dias</span>
            <span className="text-xs tabular-nums">
              {item.diasEmFila} dias em fila
            </span>
            <BarraPrazo percentual={item.percentualPrazo} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/** Cartão que mostra só o título e o resumo; o conteúdo abre e fecha clicando no cabeçalho. Começa fechado. */
function CardRecolhivel({
  titulo,
  descricao,
  className,
  children,
}: {
  titulo: React.ReactNode;
  descricao?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}) {
  const [aberto, setAberto] = useState(false);
  const idConteudo = useId();
  return (
    <Card className="hover:ring-foreground transition-shadow">
      <CardHeader>
        <button
          type="button"
          aria-expanded={aberto}
          aria-controls={idConteudo}
          onClick={() => setAberto((a) => !a)}
          className="focus-visible:ring-ring/50 -m-1 flex items-start justify-between gap-3 rounded-md p-1 text-left outline-none focus-visible:ring-2"
        >
          <span className="flex min-w-0 flex-col gap-1">
            <span className="font-heading flex items-center gap-2 text-base leading-snug font-medium">{titulo}</span>
            {descricao && <span className="text-muted-foreground text-sm">{descricao}</span>}
          </span>
          <ChevronDownIcon
            aria-hidden
            className={cn("text-muted-foreground mt-0.5 size-5 shrink-0 transition-transform", aberto && "rotate-180")}
          />
        </button>
      </CardHeader>
      <CardContent id={idConteudo} hidden={!aberto} className={className}>
        {children}
      </CardContent>
    </Card>
  );
}

function Citacao({ evidencia, fonte }: { evidencia: string | null; fonte: string | null }) {
  if (!evidencia && !fonte) return null;
  return (
    <div className="mt-1.5">
      {evidencia && (
        <blockquote className="border-primary/40 bg-muted/40 rounded-r-md border-l-2 py-1 pr-2 pl-3 text-sm italic">
          “{evidencia}”
        </blockquote>
      )}
      {fonte && <p className="text-muted-foreground mt-1 text-xs">Fonte: {fonte}</p>}
    </div>
  );
}

function PorQueClasse({ d }: { d: PacienteDetalhe }) {
  const c = d.classificacao;
  return (
    <CardRecolhivel
      titulo={
        <>
          Por que a classe <SeloClasse classe={c.classe} />
        </>
      }
      descricao={c.regra}
      className="flex flex-col gap-3"
    >
      {c.ajustada && (
        <div className="rounded-lg border p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-muted-foreground">Calculada pelo sistema</span>
            <SeloClasse classe={c.classeCalculada} />
            <span>→</span>
            <span className="text-muted-foreground">definida pelo regulador</span>
            <SeloClasse classe={c.classe} />
          </div>
          {c.justificativaAjuste && <p className="mt-2 italic">“{c.justificativaAjuste}”</p>}
        </div>
      )}
      {c.determinantes.length === 0 ? (
        <p className="text-muted-foreground text-sm">Nenhum sinal clínico determinante; classe definida pelo padrão do procedimento.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {c.determinantes.map((s, i) => (
            <li key={`${s.tipo}-${i}`} className="text-sm">
              <div className="flex flex-wrap items-baseline gap-2">
                <span className="font-medium">{s.rotulo}</span>
                {s.data && <span className="text-muted-foreground text-xs">{formatarData(s.data)}</span>}
              </div>
              <p className="text-muted-foreground">{s.descricao}</p>
              <Citacao evidencia={s.evidencia} fonte={s.fonte} />
            </li>
          ))}
        </ul>
      )}
    </CardRecolhivel>
  );
}

function ComposicaoScore({ d }: { d: PacienteDetalhe }) {
  const total = d.componentes.reduce((s, c) => s + c.pontos, 0);
  const max = d.componentes.reduce((s, c) => s + c.maximo, 0);
  return (
    <CardRecolhivel
      titulo="Composição do score"
      descricao={
        <>
          Dentro da mesma classe, quem tem score maior vem antes. Total{" "}
          <span className="text-foreground font-semibold tabular-nums">{d.item.score.toFixed(1)}</span> de 100.
        </>
      }
      className="flex flex-col gap-3"
    >
      {d.componentes.map((c) => {
        const pct = c.maximo > 0 ? Math.min(100, (c.pontos / c.maximo) * 100) : 0;
        return (
          <div key={c.chave} className="text-sm">
            <div className="flex items-baseline justify-between gap-2">
              <span className="font-medium">{c.nome}</span>
              <span className="tabular-nums">
                {c.pontos.toFixed(1)} <span className="text-muted-foreground">/ {c.maximo}</span>
              </span>
            </div>
            <div className="bg-muted mt-1 h-2 overflow-hidden rounded-full">
              <div className="bg-primary h-full rounded-full" style={{ width: `${pct}%` }} />
            </div>
            <p className="text-muted-foreground mt-1 text-xs">{c.explicacao}</p>
          </div>
        );
      })}
      {d.componentes.length > 0 && (
        <div className="flex justify-between border-t pt-2 text-sm font-medium">
          <span>Total</span>
          <span className="tabular-nums">
            {total.toFixed(1)} <span className="text-muted-foreground font-normal">/ {max}</span>
          </span>
        </div>
      )}
    </CardRecolhivel>
  );
}

const CATEGORIAS: { chave: SinalDto["categoria"]; rotulo: string }[] = [
  { chave: "DIAGNOSTICO", rotulo: "Diagnóstico" },
  { chave: "PIORA", rotulo: "Piora" },
  { chave: "SINTOMA", rotulo: "Sintomas" },
  { chave: "SAIDA", rotulo: "Saída" },
  { chave: "QUALIDADE_DO_DADO", rotulo: "Qualidade do dado" },
];

function Sinais({ sinais }: { sinais: SinalDto[] }) {
  const grupos = CATEGORIAS.map((c) => ({ ...c, itens: sinais.filter((s) => s.categoria === c.chave) })).filter(
    (g) => g.itens.length > 0,
  );
  return (
    <CardRecolhivel
      titulo="Todos os sinais encontrados"
      descricao="O que o sistema leu no cadastro, no texto do médico e nos eventos."
      className="flex flex-col gap-4"
    >
      {grupos.length === 0 && <p className="text-muted-foreground text-sm">Nenhum sinal encontrado.</p>}
      {grupos.map((g) => (
        <div key={g.chave}>
          <h3 className="text-muted-foreground mb-2 text-xs font-semibold tracking-wide uppercase">
            {g.rotulo} ({g.itens.length})
          </h3>
          <ul className="flex flex-col gap-2.5">
            {g.itens.map((s, i) => (
              <li key={`${s.tipo}-${i}`} className="text-sm">
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="font-medium">{s.rotulo}</span>
                  {s.data && <span className="text-muted-foreground text-xs">{formatarData(s.data)}</span>}
                </div>
                <p className="text-muted-foreground">{s.descricao}</p>
                <Citacao evidencia={s.evidencia} fonte={s.fonte} />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </CardRecolhivel>
  );
}

function LinhaDoTempo({ d }: { d: PacienteDetalhe }) {
  return (
    <CardRecolhivel
      titulo="Linha do tempo"
      descricao={
        <>
          Entrada na fila em {formatarData(d.dados.dataEntrada)}
          {d.dados.dataUltimaRevisao && ` · última revisão em ${formatarData(d.dados.dataUltimaRevisao)}`}
        </>
      }
    >
      {d.eventos.length === 0 ? (
        <Vazio texto="Nenhum evento clínico registrado." />
      ) : (
        <ol className="border-muted relative ml-1.5 flex flex-col gap-4 border-l pl-4">
          {d.eventos.map((e) => (
            <li key={e.id} className="relative text-sm">
              <span
                className={cn(
                  "bg-background absolute top-1.5 -left-[21px] size-2.5 rounded-full border-2",
                  e.sintetico ? "border-primary" : "border-muted-foreground",
                )}
              />
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-muted-foreground text-xs tabular-nums">{formatarData(e.data)}</span>
                <span className="font-medium">{rotuloTipoEvento(e.tipo)}</span>
                {e.resultado && <Badge variant="outline">{rotuloResultado(e.resultado)}</Badge>}
                {e.valor !== null && <span className="text-xs tabular-nums">valor {e.valor}</span>}
                {e.sintetico && <Badge>demo</Badge>}
              </div>
              {e.descricao && <p className="mt-0.5">{e.descricao}</p>}
              <p className="text-muted-foreground text-xs">
                {[e.origem && `Origem: ${e.origem}`, e.id, e.solicitacaoOriginal !== d.item.solicitacaoId && `da solicitação ${e.solicitacaoOriginal}`]
                  .filter(Boolean)
                  .join(" · ")}
              </p>
            </li>
          ))}
        </ol>
      )}
    </CardRecolhivel>
  );
}

function Campo({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted-foreground text-xs">{rotulo}</dt>
      <dd className="text-sm">{children ?? "—"}</dd>
    </div>
  );
}

function DadosClinicosCard({ d }: { d: PacienteDetalhe }) {
  const x = d.dados;
  const flags = [
    { rotulo: "Oncológico", v: x.flagOncologico },
    { rotulo: "Diabético", v: x.flagDiabetico },
    { rotulo: "Piora", v: x.flagPiora },
  ];
  return (
    <CardRecolhivel titulo="Dados clínicos" descricao="Como vieram do cadastro da solicitação." className="flex flex-col gap-4">
      <div>
        <h3 className="text-muted-foreground mb-1 text-xs font-semibold tracking-wide uppercase">Justificativa do médico</h3>
        <p className="bg-muted/40 rounded-md p-3 text-sm whitespace-pre-wrap">
          {x.justificativa || <span className="text-muted-foreground">Sem justificativa.</span>}
        </p>
      </div>
      <div>
        <h3 className="text-muted-foreground mb-1 text-xs font-semibold tracking-wide uppercase">Observações</h3>
        <p className="bg-muted/40 rounded-md p-3 text-sm whitespace-pre-wrap">
          {x.observacoes || <span className="text-muted-foreground">Sem observações.</span>}
        </p>
      </div>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-4">
        <Campo rotulo="Comorbidades">{x.comorbidades}</Campo>
        <Campo rotulo="Dor (0–10)">{x.dor}</Campo>
        <Campo rotulo="Limitação">{x.limitacao}</Campo>
        <Campo rotulo="ASA">{x.asa}</Campo>
        <Campo rotulo="Risco cirúrgico">{x.riscoCirurgico}</Campo>
        <Campo rotulo="Prioridade manual">{x.prioridadeManual}</Campo>
        <Campo rotulo="Entrada">{formatarData(x.dataEntrada)}</Campo>
        <Campo rotulo="Código do procedimento">{x.procedimentoId}</Campo>
      </dl>
      <div className="flex flex-wrap gap-2">
        {flags.map((f) => (
          <Badge key={f.rotulo} variant={f.v && /^(s|sim|1|true)$/i.test(f.v) ? "secondary" : "outline"}>
            {f.rotulo}: {f.v ?? "—"}
          </Badge>
        ))}
      </div>
    </CardRecolhivel>
  );
}

const ROTULO_AJUSTE: Record<string, string> = {
  CONFIRMAR_CLASSE: "Confirmou a classe",
  ALTERAR_CLASSE: "Mudou a classe",
  CONFIRMAR_SAIDA: "Confirmou a saída",
  RECUSAR_SAIDA: "Recusou a saída",
  SUGERIR_REBAIXAMENTO: "Sugeriu rebaixamento (pendente)",
  CONFIRMAR_REBAIXAMENTO: "Confirmou o rebaixamento",
  REJEITAR_REBAIXAMENTO: "Rejeitou o rebaixamento",
  REBAIXAMENTO_BLOQUEADO: "Rebaixamento bloqueado (oncológico)",
};

function formatarDataHora(iso: string): string {
  const dt = new Date(iso);
  if (Number.isNaN(dt.getTime())) return formatarData(iso);
  return dt.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

function Auditoria({ d }: { d: PacienteDetalhe }) {
  return (
    <CardRecolhivel
      titulo="Histórico de decisões"
      descricao="Auditoria de tudo que o regulador e os médicos decidiram neste paciente: quem, quando e por quê."
      className="flex flex-col gap-4"
    >
      {d.ajustes.length === 0 ? (
        <p className="text-muted-foreground text-sm">Nenhuma decisão registrada ainda.</p>
      ) : (
        <ul className="flex flex-col divide-y">
          {d.ajustes.map((a) => (
            <li key={a.id} className="py-2 text-sm first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">{ROTULO_AJUSTE[a.tipo] ?? a.tipo}</span>
                {a.classeAnterior && a.classeNova && a.classeAnterior !== a.classeNova ? (
                  <span className="flex items-center gap-1">
                    <SeloClasse classe={a.classeAnterior} /> → <SeloClasse classe={a.classeNova} />
                  </span>
                ) : (
                  a.classeNova && <SeloClasse classe={a.classeNova} />
                )}
                {a.medicoId !== null && <Badge variant="outline">médico</Badge>}
                {a.informacaoForaDoSistema && <Badge variant="secondary">informação fora do sistema</Badge>}
                <span className="text-muted-foreground ml-auto text-xs">
                  {a.autor} · {formatarDataHora(a.criadoEm)}
                </span>
              </div>
              <p className="mt-0.5 italic">“{a.justificativa}”</p>
            </li>
          ))}
        </ul>
      )}
      {d.duplicatas.length > 0 && (
        <div className="border-t pt-3 text-sm">
          <p className="font-medium">Solicitações duplicadas mescladas nesta</p>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {d.duplicatas.map((s) => (
              <Badge key={s} variant="outline" className="font-mono">
                {s}
              </Badge>
            ))}
          </div>
        </div>
      )}
    </CardRecolhivel>
  );
}
