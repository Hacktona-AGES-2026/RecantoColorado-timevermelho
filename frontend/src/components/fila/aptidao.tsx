"use client";

// Aptidão cirúrgica: o paciente pode ser operado agora? Parecer com fatores, ações e recursos estimados, mais a
// decisão humana (liberar, aguardar otimização, contraindicar). Não muda a posição na fila.
import { useEffect, useState } from "react";
import { HeartPulse } from "lucide-react";
import {
  api,
  type Aptidao,
  type DecisaoAptidaoTipo,
  type Nivel,
  type VereditoAptidao,
} from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Carregando, mensagemErro } from "./comum";
import { cn } from "@/lib/utils";

const COR_VEREDITO: Record<VereditoAptidao, string> = {
  APTO: "border-emerald-500 bg-emerald-500/10 text-emerald-900 dark:text-emerald-200",
  APTO_COM_RESSALVAS: "border-amber-500 bg-amber-500/10 text-amber-900 dark:text-amber-200",
  OTIMIZAR_ANTES: "border-red-500 bg-red-500/10 text-red-900 dark:text-red-200",
};

const ROTULO_NIVEL: Record<Nivel, string> = { BAIXO: "baixo", MODERADO: "moderado", ALTO: "alto" };
const ROTULO_UTI = { NAO: "não prevista", POSSIVEL: "possível", PROVAVEL: "provável", PREVISTA: "prevista" } as const;

const DECISOES: { valor: DecisaoAptidaoTipo; rotulo: string; ajuda: string }[] = [
  { valor: "LIBERADO", rotulo: "Liberar para agendar", ajuda: "Pode ser operado quando chegar a vez." },
  {
    valor: "AGUARDAR_OTIMIZACAO",
    rotulo: "Aguardar otimização",
    ajuda: "Continua no mesmo lugar da fila, mas não é chamado até melhorar. Descreva o plano.",
  },
  { valor: "CONTRAINDICADO", rotulo: "Contraindicar no momento", ajuda: "A cirurgia não é indicada agora. Converse sobre alternativas (BRAN)." },
];

/** Selo discreto nas tabelas: só o que pede atenção. */
export function SeloAptidao({ aptidao, decisao }: { aptidao: VereditoAptidao | null; decisao: DecisaoAptidaoTipo | null }) {
  let texto: string | null = null;
  let cor = "bg-amber-100 text-amber-900";
  if (decisao === "AGUARDAR_OTIMIZACAO") texto = "não agendar: otimizando a saúde";
  else if (decisao === "CONTRAINDICADO") {
    texto = "cirurgia contraindicada no momento";
    cor = "bg-red-100 text-red-900";
  } else if (decisao === null && aptidao === "OTIMIZAR_ANTES") {
    texto = "otimizar a saúde antes";
    cor = "bg-red-100 text-red-900";
  } else if (decisao === null && aptidao === "APTO_COM_RESSALVAS") texto = "risco cirúrgico alto";
  if (!texto) return null;
  return (
    <span
      className={cn("mt-0.5 block w-fit rounded px-1.5 text-[10px] font-medium", cor)}
      title="Aptidão cirúrgica. Não muda a posição na fila; veja a página do paciente."
    >
      {texto}
    </span>
  );
}

export function CartaoAptidao({ solicitacaoId, aoRegistrar }: { solicitacaoId: string; aoRegistrar: () => void }) {
  const [a, setA] = useState<Aptidao | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [versao, setVersao] = useState(0);
  const [aberto, setAberto] = useState<"fatores" | "decisao" | null>(null);
  const [verRecursos, setVerRecursos] = useState(false);

  useEffect(() => {
    let ativo = true;
    api
      .aptidao(solicitacaoId)
      .then((x) => ativo && setA(x))
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [solicitacaoId, versao]);

  function depois() {
    setAberto(null);
    setVersao((v) => v + 1);
    aoRegistrar();
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <HeartPulse className="size-4" /> Pode ser operado agora?
        </CardTitle>
        <CardDescription>
          Aptidão pela saúde do paciente, mesmo que ele queira operar. Não muda a posição na fila: quem precisa otimizar
          continua no lugar e não é chamado até melhorar.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        {erro && <p className="text-destructive">{erro}</p>}
        {!a ? (
          <Carregando texto="Avaliando…" className="py-4" />
        ) : (
          <>
            <div className={cn("rounded-lg border p-2", COR_VEREDITO[a.veredito])}>
              <p className="font-semibold">{a.vereditoRotulo}</p>
              <p className="text-xs">
                Risco {ROTULO_NIVEL[a.risco]} ({a.pontos} pontos) · consumo de recursos {ROTULO_NIVEL[a.recursos.consumo]}
              </p>
            </div>
            {a.observacaoPrioridade && (
              <p className="rounded-md border border-sky-400/50 bg-sky-500/10 p-2 text-xs">{a.observacaoPrioridade}</p>
            )}
            {a.decisao && (
              <div className="rounded-md border p-2 text-xs">
                <p className="font-medium">Decisão: {a.decisao.rotulo}</p>
                <p className="text-muted-foreground">
                  {a.decisao.autor} · {new Date(a.decisao.criadoEm).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}
                  {a.decisao.reavaliarEm && ` · reavaliar em ${new Date(a.decisao.reavaliarEm + "T12:00").toLocaleDateString("pt-BR")}`}
                </p>
                <p className="mt-0.5 italic">“{a.decisao.justificativa}”</p>
                {a.decisao.plano && <p className="mt-0.5">Plano: {a.decisao.plano}</p>}
              </div>
            )}

            <div>
              <p className="mb-1 text-xs font-medium">Fatores considerados</p>
              <ul className="flex flex-col gap-1">
                {a.fatores.map((f) => (
                  <li key={f.descricao} className="flex flex-wrap items-center gap-1.5 text-xs">
                    <span className="bg-muted rounded px-1 tabular-nums">+{f.pontos}</span>
                    <span>{f.descricao}</span>
                    {f.bloqueante && <Badge variant="destructive">controlar antes</Badge>}
                    {f.modificavel && !f.bloqueante && <Badge variant="secondary">pode melhorar</Badge>}
                  </li>
                ))}
              </ul>
            </div>
            {a.acoes.length > 0 && (
              <div>
                <p className="mb-1 text-xs font-medium">O que fazer antes</p>
                <ul className="list-disc pl-4 text-xs">
                  {a.acoes.map((x) => (
                    <li key={x}>{x}</li>
                  ))}
                </ul>
              </div>
            )}
            {a.preRequisitos.length > 0 && (
              <p className="text-xs">
                <span className="font-medium">Pré-requisitos pendentes:</span> {a.preRequisitos.join("; ")}.
              </p>
            )}

            <div className="rounded-md border p-2 text-xs">
              <p className="font-medium">Recursos estimados</p>
              <p>
                Cirurgia de ~{a.recursos.tempoCirurgiaMin} min
                {a.recursos.tempoCirurgiaMin !== a.recursos.tempoBaseMin && ` (média do procedimento: ${a.recursos.tempoBaseMin} min)`}
                {" · "}~{a.recursos.permanenciaDias} dia(s) internado · UTI {ROTULO_UTI[a.recursos.uti]}
              </p>
              <button type="button" className="text-muted-foreground mt-0.5 underline" onClick={() => setVerRecursos(!verRecursos)}>
                {verRecursos ? "esconder o cálculo" : "ver como foi calculado"}
              </button>
              {verRecursos && (
                <ul className="text-muted-foreground mt-1 list-disc pl-4">
                  {a.recursos.explicacao.map((x) => (
                    <li key={x}>{x}</li>
                  ))}
                </ul>
              )}
            </div>
            <p className="text-muted-foreground text-[11px]">{a.aviso}</p>

            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => setAberto("fatores")}>
                Informar fatores de saúde
              </Button>
              <Button size="sm" onClick={() => setAberto("decisao")}>
                Registrar decisão
              </Button>
            </div>
          </>
        )}
      </CardContent>

      <Dialog open={aberto !== null} onOpenChange={(v) => !v && setAberto(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
          {aberto === "fatores" && a && <FormFatores a={a} solicitacaoId={solicitacaoId} aoSalvar={depois} />}
          {aberto === "decisao" && a && <FormDecisao a={a} solicitacaoId={solicitacaoId} aoSalvar={depois} />}
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function FormFatores({ a, solicitacaoId, aoSalvar }: { a: Aptidao; solicitacaoId: string; aoSalvar: () => void }) {
  const atual = a.fatoresInformados;
  const [colesterol, setColesterol] = useState(atual?.colesterolAlto ?? false);
  const [pressao, setPressao] = useState(atual?.pressaoDescontrolada ?? false);
  const [fuma, setFuma] = useState(atual?.tabagismoAtual ?? false);
  const [adesao, setAdesao] = useState(atual?.adesaoRuim ?? false);
  const [imc, setImc] = useState(atual?.imc != null ? String(atual.imc) : "");
  const [outros, setOutros] = useState(atual?.outros ?? "");
  const [autor, setAutor] = useState("regulador");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function salvar() {
    setEnviando(true);
    setErro(null);
    const n = Number(imc.replace(",", "."));
    try {
      await api.informarFatoresAptidao(solicitacaoId, {
        colesterolAlto: colesterol,
        pressaoDescontrolada: pressao,
        tabagismoAtual: fuma,
        adesaoRuim: adesao,
        imc: imc.trim() && !Number.isNaN(n) ? n : undefined,
        outros: outros.trim() || undefined,
        autor: autor.trim() || "regulador",
      });
      aoSalvar();
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEnviando(false);
    }
  }

  const caixa = (rotulo: string, v: boolean, set: (b: boolean) => void) => (
    <label className="flex items-start gap-2 text-sm">
      <input type="checkbox" checked={v} onChange={(e) => set(e.target.checked)} className="mt-0.5" />
      <span>{rotulo}</span>
    </label>
  );

  return (
    <>
      <DialogHeader>
        <DialogTitle>Fatores de saúde</DialogTitle>
        <DialogDescription>
          O que o sistema não tem no cadastro. Entra no parecer de aptidão e na estimativa de recursos; não muda a fila.
        </DialogDescription>
      </DialogHeader>
      <div className="flex flex-col gap-2">
        {caixa("Colesterol alto", colesterol, setColesterol)}
        {caixa("Pressão alta descontrolada (precisa controlar antes)", pressao, setPressao)}
        {caixa("Fuma atualmente", fuma, setFuma)}
        {caixa("Não está seguindo o tratamento das doenças crônicas", adesao, setAdesao)}
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">IMC (opcional)</span>
          <Input value={imc} onChange={(e) => setImc(e.target.value)} inputMode="decimal" placeholder="Ex.: 41,5" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">Outros (opcional)</span>
          <Textarea value={outros} onChange={(e) => setOutros(e.target.value)} rows={2} maxLength={1000} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">Quem informou</span>
          <Input value={autor} onChange={(e) => setAutor(e.target.value)} maxLength={120} />
        </label>
        {erro && <p className="text-destructive text-sm">{erro}</p>}
        <Button onClick={salvar} disabled={enviando}>
          {enviando ? "Salvando…" : "Salvar fatores"}
        </Button>
      </div>
    </>
  );
}

function FormDecisao({ a, solicitacaoId, aoSalvar }: { a: Aptidao; solicitacaoId: string; aoSalvar: () => void }) {
  const [decisao, setDecisao] = useState<DecisaoAptidaoTipo>(a.veredito === "OTIMIZAR_ANTES" ? "AGUARDAR_OTIMIZACAO" : "LIBERADO");
  const [justificativa, setJustificativa] = useState("");
  const [plano, setPlano] = useState(a.veredito === "OTIMIZAR_ANTES" ? a.acoes.join("; ") : "");
  const [reavaliar, setReavaliar] = useState("");
  const [autor, setAutor] = useState("regulador");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const ok = justificativa.trim().length >= 10 && (decisao !== "AGUARDAR_OTIMIZACAO" || plano.trim().length > 0);

  async function salvar() {
    setEnviando(true);
    setErro(null);
    try {
      await api.decidirAptidao(solicitacaoId, {
        decisao,
        justificativa: justificativa.trim(),
        plano: plano.trim() || undefined,
        reavaliarEm: reavaliar || undefined,
        autor: autor.trim() || "regulador",
      });
      aoSalvar();
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Decisão sobre a aptidão</DialogTitle>
        <DialogDescription>
          Parecer do sistema: {a.vereditoRotulo.toLowerCase()}. A decisão é sua e fica registrada; a posição na fila não muda.
        </DialogDescription>
      </DialogHeader>
      <div className="flex flex-col gap-2">
        {DECISOES.map((d) => (
          <label key={d.valor} className={cn("flex cursor-pointer items-start gap-2 rounded-md border p-2 text-sm", decisao === d.valor && "border-primary")}>
            <input type="radio" name="decisao" checked={decisao === d.valor} onChange={() => setDecisao(d.valor)} className="mt-0.5" />
            <span>
              <span className="font-medium">{d.rotulo}</span>
              <span className="text-muted-foreground block text-xs">{d.ajuda}</span>
            </span>
          </label>
        ))}
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">Justificativa (obrigatória)</span>
          <Textarea value={justificativa} onChange={(e) => setJustificativa(e.target.value)} rows={2} maxLength={2000} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">
            Plano {decisao === "AGUARDAR_OTIMIZACAO" ? "(obrigatório)" : "(opcional)"}
          </span>
          <Textarea value={plano} onChange={(e) => setPlano(e.target.value)} rows={2} maxLength={2000} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">Reavaliar em (opcional)</span>
          <Input type="date" value={reavaliar} onChange={(e) => setReavaliar(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium">Quem decidiu</span>
          <Input value={autor} onChange={(e) => setAutor(e.target.value)} maxLength={120} />
        </label>
        {erro && <p className="text-destructive text-sm">{erro}</p>}
        <Button onClick={salvar} disabled={!ok || enviando}>
          {enviando ? "Registrando…" : "Registrar decisão"}
        </Button>
      </div>
    </>
  );
}
