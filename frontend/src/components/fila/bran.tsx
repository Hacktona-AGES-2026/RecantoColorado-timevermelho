"use client";

// BRAN: o que o médico comunica ao paciente antes da decisão. Benefícios, Riscos, Alternativas e Nada (o que acontece
// sem a cirurgia). O sistema monta um rascunho; o médico revisa, conversa e registra. Não muda a fila.
import { useEffect, useState } from "react";
import { Download, MessagesSquare } from "lucide-react";
import { api, type Bran, type BranRascunho, type FormaComunicacao, type RespostaCirurgia } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Carregando, mensagemErro } from "./comum";
import { ROTULO_DESEJO } from "./desejo-cirurgia";
import { cn } from "@/lib/utils";

const MIN = 10;

const ITENS = [
  { chave: "beneficios", letra: "B", titulo: "Benefícios", ajuda: "O que a cirurgia deve melhorar para este paciente." },
  { chave: "riscos", letra: "R", titulo: "Riscos", ajuda: "Os riscos do procedimento e os do próprio paciente." },
  { chave: "alternativas", letra: "A", titulo: "Alternativas", ajuda: "Opções sem cirurgia ou com outros procedimentos." },
  { chave: "nada", letra: "N", titulo: "Nada (não operar)", ajuda: "O que acontece se ele não fizer nada agora." },
] as const;

type Chave = (typeof ITENS)[number]["chave"];

const FORMAS: { valor: FormaComunicacao; rotulo: string }[] = [
  { valor: "PRESENCIAL", rotulo: "Presencial" },
  { valor: "TELEFONE", rotulo: "Telefone" },
  { valor: "TELECONSULTA", rotulo: "Teleconsulta" },
];

function dataHora(iso: string) {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/** Texto simples para entregar ao paciente. */
function textoParaPaciente(nome: string, procedimento: string, b: Bran): string {
  return [
    `Informações sobre a sua cirurgia — ${procedimento}`,
    `Paciente: ${nome}`,
    `Conversa em ${dataHora(b.criadoEm)} (${b.forma.toLowerCase()}) com ${b.autor}.`,
    "",
    "BENEFÍCIOS — o que a cirurgia pode melhorar",
    b.beneficios,
    "",
    "RISCOS — o que pode dar errado",
    b.riscos,
    "",
    "ALTERNATIVAS — outras opções",
    b.alternativas,
    "",
    "NADA — o que acontece se você não operar agora",
    b.nada,
    "",
    ...(b.duvidas ? ["Suas dúvidas nesta conversa", b.duvidas, ""] : []),
    "A decisão de operar é sua, junto com a equipe de saúde. Se tiver novas dúvidas ou piorar, procure o seu serviço de saúde.",
    "Em caso de emergência, ligue 192 (SAMU).",
  ].join("\n");
}

export function CartaoBran({
  solicitacaoId,
  nome,
  procedimento,
  aoRegistrar,
}: {
  solicitacaoId: string;
  nome: string;
  procedimento: string;
  aoRegistrar: () => void;
}) {
  const [historico, setHistorico] = useState<Bran[] | null>(null);
  const [aberto, setAberto] = useState(false);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let ativo = true;
    api
      .historicoBran(solicitacaoId)
      .then((h) => ativo && setHistorico(h))
      .catch(() => ativo && setHistorico([]));
    return () => {
      ativo = false;
    };
  }, [solicitacaoId, versao]);

  const atual = historico?.[0] ?? null;

  function baixar(b: Bran) {
    const blob = new Blob([textoParaPaciente(nome, procedimento, b)], { type: "text/plain;charset=utf-8" });
    const href = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = `BRAN_${solicitacaoId}.txt`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(href), 2000);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <MessagesSquare className="size-4" /> Conversa BRAN
        </CardTitle>
        <CardDescription>
          Antes da decisão, comunique ao paciente os Benefícios, os Riscos, as Alternativas e o que acontece se Nada for
          feito. Não muda a posição na fila.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {!historico ? (
          <Carregando texto="Carregando…" className="py-4" />
        ) : atual ? (
          <div className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-2 text-sm">
            <p className="font-medium">Comunicado em {dataHora(atual.criadoEm)}</p>
            <p className="text-xs opacity-80">
              {atual.autor} · {FORMAS.find((f) => f.valor === atual.forma)?.rotulo}
            </p>
            <p className="mt-1 text-xs">
              {atual.compreendeu
                ? "Paciente recebeu as informações e teve as dúvidas respondidas."
                : "Entendimento do paciente ainda não confirmado."}
            </p>
            {atual.duvidas && <p className="mt-1 text-xs italic">Dúvidas: “{atual.duvidas}”</p>}
          </div>
        ) : (
          <p className="text-muted-foreground rounded-lg border border-dashed p-2 text-sm">Ainda não comunicado.</p>
        )}
        <div className="flex flex-wrap gap-2">
          <Button size="sm" onClick={() => setAberto(true)}>
            {atual ? "Registrar nova conversa" : "Registrar conversa BRAN"}
          </Button>
          {atual && (
            <Button size="sm" variant="outline" onClick={() => baixar(atual)}>
              <Download /> Versão para o paciente
            </Button>
          )}
        </div>
        {historico && historico.length > 1 && (
          <p className="text-muted-foreground text-xs">{historico.length - 1} conversa(s) anterior(es) no histórico.</p>
        )}
      </CardContent>

      <Dialog open={aberto} onOpenChange={setAberto}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          {aberto && (
            <FormBran
              solicitacaoId={solicitacaoId}
              nome={nome}
              anterior={atual}
              aoRegistrar={() => {
                setVersao((v) => v + 1);
                aoRegistrar();
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function FormBran({
  solicitacaoId,
  nome,
  anterior,
  aoRegistrar,
}: {
  solicitacaoId: string;
  nome: string;
  anterior: Bran | null;
  aoRegistrar: () => void;
}) {
  const [rascunho, setRascunho] = useState<BranRascunho | null>(null);
  const [textos, setTextos] = useState<Record<Chave, string> | null>(null);
  const [forma, setForma] = useState<FormaComunicacao>("PRESENCIAL");
  const [duvidas, setDuvidas] = useState("");
  const [compreendeu, setCompreendeu] = useState(false);
  const [autor, setAutor] = useState("regulador");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvo, setSalvo] = useState(false);
  const [decisao, setDecisao] = useState<RespostaCirurgia | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .rascunhoBran(solicitacaoId)
      .then((r) => {
        if (!ativo) return;
        setRascunho(r);
        // se já houve conversa, começa dela; senão, do rascunho do sistema
        setTextos(
          anterior
            ? { beneficios: anterior.beneficios, riscos: anterior.riscos, alternativas: anterior.alternativas, nada: anterior.nada }
            : { beneficios: r.beneficios, riscos: r.riscos, alternativas: r.alternativas, nada: r.nada },
        );
      })
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [solicitacaoId, anterior]);

  const ok = textos !== null && ITENS.every((i) => textos[i.chave].trim().length >= MIN);

  async function salvar() {
    if (!textos || !ok) return;
    setEnviando(true);
    setErro(null);
    try {
      await api.registrarBran(solicitacaoId, {
        ...textos,
        forma,
        duvidas: duvidas.trim() || undefined,
        compreendeu,
        autor: autor.trim() || "regulador",
      });
      setSalvo(true);
      aoRegistrar();
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEnviando(false);
    }
  }

  async function registrarDecisao(r: RespostaCirurgia) {
    setErro(null);
    try {
      await api.registrarDesejo(solicitacaoId, { resposta: r, observacao: "Decisão após a conversa BRAN", autor: autor.trim() || "regulador" });
      setDecisao(r);
      aoRegistrar();
    } catch (e) {
      setErro(mensagemErro(e));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Conversa BRAN com {nome}</DialogTitle>
        <DialogDescription>{rascunho ? `${rascunho.procedimento} · ${solicitacaoId}` : solicitacaoId}</DialogDescription>
      </DialogHeader>
      {erro && <p className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm">{erro}</p>}
      {!textos || !rascunho ? (
        <Carregando texto="Montando o rascunho…" />
      ) : salvo ? (
        <div className="flex flex-col gap-3 text-sm">
          <p className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3">
            Conversa registrada. A posição na fila não muda.
          </p>
          <div>
            <p className="mb-1 font-medium">Qual foi a decisão do paciente?</p>
            <div className="flex flex-wrap gap-1.5">
              {(Object.keys(ROTULO_DESEJO) as RespostaCirurgia[]).map((r) => (
                <Button key={r} size="sm" variant={decisao === r ? "default" : "outline"} onClick={() => registrarDecisao(r)}>
                  {ROTULO_DESEJO[r]}
                </Button>
              ))}
            </div>
            {decisao && <p className="text-muted-foreground mt-1 text-xs">Decisão registrada: {ROTULO_DESEJO[decisao]}.</p>}
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-xs">{rascunho.aviso}</p>
          {rascunho.fatoresDeRisco.length > 0 && (
            <div className="flex flex-wrap gap-1">
              <span className="text-xs font-medium">Fatores de risco do paciente:</span>
              {rascunho.fatoresDeRisco.map((f) => (
                <Badge key={f} variant="secondary" className="whitespace-normal">
                  {f}
                </Badge>
              ))}
            </div>
          )}
          {ITENS.map((i) => (
            <label key={i.chave} className="flex flex-col gap-1">
              <span className="text-sm font-medium">
                <span className="bg-primary text-primary-foreground mr-1.5 inline-flex size-5 items-center justify-center rounded text-xs">
                  {i.letra}
                </span>
                {i.titulo} <span className="text-muted-foreground text-xs font-normal">· {i.ajuda}</span>
              </span>
              <Textarea
                value={textos[i.chave]}
                onChange={(e) => setTextos({ ...textos, [i.chave]: e.target.value })}
                rows={3}
                maxLength={4000}
                aria-invalid={textos[i.chave].trim().length < MIN}
              />
            </label>
          ))}
          <div className="flex flex-col gap-1">
            <span className="text-xs font-medium">Como foi a conversa</span>
            <div className="flex flex-wrap gap-1.5">
              {FORMAS.map((f) => (
                <Button
                  key={f.valor}
                  type="button"
                  size="sm"
                  variant={forma === f.valor ? "default" : "outline"}
                  onClick={() => setForma(f.valor)}
                >
                  {f.rotulo}
                </Button>
              ))}
            </div>
          </div>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Dúvidas do paciente ou da família (opcional)</span>
            <Textarea value={duvidas} onChange={(e) => setDuvidas(e.target.value)} rows={2} maxLength={2000} />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Quem comunicou</span>
            <Input value={autor} onChange={(e) => setAutor(e.target.value)} maxLength={120} />
          </label>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={compreendeu} onChange={(e) => setCompreendeu(e.target.checked)} className="mt-0.5" />
            <span>O paciente recebeu as informações e teve as dúvidas respondidas</span>
          </label>
          <Button onClick={salvar} disabled={!ok || enviando} className={cn(!ok && "opacity-70")}>
            {enviando ? "Registrando…" : ok ? "Registrar conversa" : "Preencha os quatro itens (mínimo 10 caracteres cada)"}
          </Button>
        </div>
      )}
    </>
  );
}
