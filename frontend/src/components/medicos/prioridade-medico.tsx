"use client";

// O médico muda a prioridade de um paciente das listas dele (ex.: sabe de algo que ainda não está no sistema).
// Mesmas regras do regulador: subir vale na hora; descer vira rebaixamento sugerido; oncológico nunca perde classe.
import { useEffect, useState } from "react";
import Link from "next/link";
import { History, SlidersHorizontal } from "lucide-react";
import { api, CLASSES, INFO_CLASSE, type AjusteDto, type ClasseSwalis, type Impacto, type ItemFila, type Medico } from "@/lib/api";
import { SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Carregando, ErroApi, mensagemErro, Vazio } from "@/components/fila/comum";
import { cn } from "@/lib/utils";

const MIN_JUSTIFICATIVA = 10;

const ROTULO_TIPO: Record<string, string> = {
  CONFIRMAR_CLASSE: "Confirmou a classe",
  ALTERAR_CLASSE: "Mudou a classe",
  SUGERIR_REBAIXAMENTO: "Sugeriu rebaixamento (aguarda o regulador)",
  CONFIRMAR_REBAIXAMENTO: "Confirmou o rebaixamento",
  REJEITAR_REBAIXAMENTO: "Rejeitou o rebaixamento",
};

/** Botão na linha da lista do médico que abre a mudança de prioridade. */
export function BotaoPrioridade({
  medico,
  item,
  aoAlterar,
}: {
  medico: Medico;
  item: ItemFila;
  aoAlterar: () => void;
}) {
  const [aberto, setAberto] = useState(false);
  return (
    <>
      <Button
        size="xs"
        variant="outline"
        onClick={(e) => {
          e.stopPropagation();
          setAberto(true);
        }}
        aria-label={`Mudar a prioridade de ${item.nome}`}
      >
        <SlidersHorizontal /> Prioridade
      </Button>
      <Dialog open={aberto} onOpenChange={setAberto}>
        <DialogContent className="sm:max-w-md" onClick={(e) => e.stopPropagation()}>
          {aberto && <FormPrioridade medico={medico} item={item} aoAlterar={aoAlterar} />}
        </DialogContent>
      </Dialog>
    </>
  );
}

function FormPrioridade({ medico, item: atual, aoAlterar }: { medico: Medico; item: ItemFila; aoAlterar: () => void }) {
  // guarda o paciente como estava ao abrir: a lista recarrega depois da mudança e não deve mudar o formulário
  const [item] = useState(atual);
  const [classe, setClasse] = useState<ClasseSwalis>(atual.classe);
  const [justificativa, setJustificativa] = useState("");
  const [foraDoSistema, setForaDoSistema] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [impacto, setImpacto] = useState<Impacto | null>(null);

  const descer = CLASSES.indexOf(classe) > CLASSES.indexOf(item.classe);
  const justOk = justificativa.trim().length >= MIN_JUSTIFICATIVA;

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    if (!justOk) return;
    setEnviando(true);
    setErro(null);
    try {
      const r = await api.ajustarComoMedico(medico.id, {
        solicitacaoId: item.solicitacaoId,
        tipo: classe === item.classe ? "CONFIRMAR_CLASSE" : "ALTERAR_CLASSE",
        classeNova: classe === item.classe ? undefined : classe,
        justificativa: justificativa.trim(),
        informacaoForaDoSistema: foraDoSistema,
      });
      setImpacto(r);
      aoAlterar();
    } catch (err) {
      setErro(mensagemErro(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Prioridade de {item.nome}</DialogTitle>
        <DialogDescription>
          {item.solicitacaoId} · posição {item.posicao ?? "—"} na fila geral · hoje <strong>{item.classe}</strong>. A mudança
          fica registrada com o seu nome ({medico.nome}).
        </DialogDescription>
      </DialogHeader>
      {impacto ? (
        <div className="flex flex-col gap-3">
          <p className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm">{impacto.mensagem}</p>
          <Button variant="outline" nativeButton={false} render={<Link href={`/paciente/${item.solicitacaoId}`} />}>
            Abrir o paciente
          </Button>
        </div>
      ) : (
        <form onSubmit={enviar} className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-medium">Classe</span>
            <div className="flex flex-wrap gap-1.5">
              {CLASSES.map((c) => {
                const bloqueada = item.oncologico && CLASSES.indexOf(c) > CLASSES.indexOf(item.classe);
                return (
                  <button
                    key={c}
                    type="button"
                    disabled={bloqueada}
                    onClick={() => setClasse(c)}
                    title={bloqueada ? "Bloqueado: paciente oncológico não tem a classe reduzida" : INFO_CLASSE[c].nome}
                    aria-pressed={classe === c}
                    className={cn(
                      "rounded-md p-0.5 ring-2 ring-transparent transition disabled:cursor-not-allowed disabled:opacity-40",
                      classe === c && "ring-primary",
                    )}
                  >
                    <SeloClasse classe={c} />
                  </button>
                );
              })}
            </div>
            <p className="text-muted-foreground text-xs">
              {classe === item.classe
                ? "Mesma classe: registra que você confirmou a classe atual."
                : descer
                  ? "Classe menos urgente: vira rebaixamento sugerido; a classe atual vale até o regulador confirmar."
                  : "Classe mais urgente: vale na hora. Sem atendimento ou exame registrado, o paciente não passa à frente de quem tem câncer (regra de ouro)."}
              {item.oncologico && " Paciente oncológico: a classe pode subir, mas nunca ser reduzida."}
            </p>
          </div>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Justificativa (obrigatória)</span>
            <Textarea
              value={justificativa}
              onChange={(e) => setJustificativa(e.target.value)}
              placeholder="Ex.: paciente relatou no ambulatório piora da dor e perda de força; exame ainda não lançado."
              rows={3}
            />
            <span className={cn("text-[11px]", justOk ? "text-muted-foreground" : "text-amber-700 dark:text-amber-400")}>
              {justificativa.trim().length}/{MIN_JUSTIFICATIVA} caracteres mínimos
            </span>
          </label>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={foraDoSistema}
              onChange={(e) => setForaDoSistema(e.target.checked)}
              className="mt-0.5"
            />
            <span>Sei de algo sobre este paciente que ainda não está registrado no sistema</span>
          </label>
          {erro && (
            <div className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm" role="alert">
              {erro}
            </div>
          )}
          <Button type="submit" disabled={!justOk || enviando}>
            {enviando ? "Registrando…" : descer ? "Sugerir rebaixamento" : classe === item.classe ? "Confirmar classe" : "Mudar classe"}
          </Button>
        </form>
      )}
    </>
  );
}

/** Aba "Minhas alterações": auditoria do que este médico mudou. */
export function MinhasAlteracoes({ medico, versao }: { medico: Medico; versao: number }) {
  const [itens, setItens] = useState<AjusteDto[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .ajustesMedico(medico.id)
      .then((a) => ativo && setItens(a))
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [medico.id, versao]);

  if (erro) return <ErroApi erro={erro} />;
  if (!itens) return <Carregando texto="Carregando alterações…" />;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <History className="size-4" /> Minhas alterações
        </CardTitle>
        <CardDescription>
          Tudo o que {medico.nome} mudou na prioridade, com justificativa e horário. É a mesma trilha de auditoria que o
          regulador vê no histórico de cada paciente.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {itens.length === 0 ? (
          <Vazio texto="Nenhuma alteração feita por este médico ainda." />
        ) : (
          <ul className="divide-y text-sm">
            {itens.map((a) => (
              <li key={a.id} className="flex flex-col gap-0.5 py-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Link href={`/paciente/${a.solicitacaoId}`} className="font-mono text-xs hover:underline">
                    {a.solicitacaoId}
                  </Link>
                  <span className="font-medium">{ROTULO_TIPO[a.tipo] ?? a.tipo}</span>
                  {a.classeAnterior && a.classeNova && a.classeAnterior !== a.classeNova ? (
                    <span className="flex items-center gap-1">
                      <SeloClasse classe={a.classeAnterior} /> → <SeloClasse classe={a.classeNova} />
                    </span>
                  ) : (
                    a.classeNova && <SeloClasse classe={a.classeNova} />
                  )}
                  {a.informacaoForaDoSistema && <Badge variant="secondary">informação fora do sistema</Badge>}
                  <span className="text-muted-foreground ml-auto text-xs">
                    {new Date(a.criadoEm).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })}
                  </span>
                </div>
                <p className="italic">“{a.justificativa}”</p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
