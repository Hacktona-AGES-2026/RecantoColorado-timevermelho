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
  FIXAR_POSICAO: "Escolheu a posição na fila",
  LIBERAR_POSICAO: "Liberou a posição escolhida",
  REGISTRAR_EVENTO: "Registrou exame/evento",
  CONFIRMAR_REBAIXAMENTO: "Confirmou o rebaixamento",
  REJEITAR_REBAIXAMENTO: "Rejeitou o rebaixamento",
};

/** Botão na linha da lista do médico que abre a mudança de prioridade. */
export function BotaoPrioridade({
  medico,
  item,
  aoAlterar,
  primeiroDaLista,
}: {
  medico: Medico;
  item: ItemFila;
  aoAlterar: () => void;
  /** posição na fila geral do primeiro paciente desta lista (atalho "1º desta lista") */
  primeiroDaLista?: number | null;
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
          {aberto && (
            <FormPrioridade medico={medico} item={item} aoAlterar={aoAlterar} primeiroDaLista={primeiroDaLista ?? null} />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

function FormPrioridade({
  medico,
  item: atual,
  aoAlterar,
  primeiroDaLista,
}: {
  medico: Medico;
  item: ItemFila;
  aoAlterar: () => void;
  primeiroDaLista: number | null;
}) {
  // guarda o paciente como estava ao abrir: a lista recarrega depois da mudança e não deve mudar o formulário
  const [item] = useState(atual);
  const [modo, setModo] = useState<"classe" | "posicao">("classe");
  const [posicao, setPosicao] = useState("");
  const [classe, setClasse] = useState<ClasseSwalis>(atual.classe);
  const [justificativa, setJustificativa] = useState("");
  const [foraDoSistema, setForaDoSistema] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [impacto, setImpacto] = useState<Impacto | null>(null);

  const descer = CLASSES.indexOf(classe) > CLASSES.indexOf(item.classe);
  const justOk = justificativa.trim().length >= MIN_JUSTIFICATIVA;
  const alvo = Number(posicao);
  const posicaoOk =
    modo === "classe" || (posicao !== "" && Number.isInteger(alvo) && alvo >= 1 && item.posicao !== null && alvo < item.posicao);

  async function enviar(liberar = false) {
    if (!justOk || (!liberar && !posicaoOk)) return;
    setEnviando(true);
    setErro(null);
    const base = { solicitacaoId: item.solicitacaoId, justificativa: justificativa.trim(), informacaoForaDoSistema: foraDoSistema };
    try {
      const r = await api.ajustarComoMedico(
        medico.id,
        liberar
          ? { ...base, tipo: "LIBERAR_POSICAO" }
          : modo === "posicao"
            ? { ...base, tipo: "FIXAR_POSICAO", posicao: alvo }
            : {
                ...base,
                tipo: classe === item.classe ? "CONFIRMAR_CLASSE" : "ALTERAR_CLASSE",
                classeNova: classe === item.classe ? undefined : classe,
              },
      );
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
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void enviar();
          }}
          className="flex flex-col gap-3"
        >
          <div className="grid grid-cols-2 gap-1.5" role="group" aria-label="O que mudar">
            <Button type="button" size="sm" variant={modo === "classe" ? "default" : "outline"} onClick={() => setModo("classe")}>
              Mudar classe
            </Button>
            <Button type="button" size="sm" variant={modo === "posicao" ? "default" : "outline"} onClick={() => setModo("posicao")}>
              Mudar posição
            </Button>
          </div>

          {modo === "posicao" ? (
            <div className="flex flex-col gap-1.5">
              <label className="flex flex-col gap-1">
                <span className="text-xs font-medium">Nova posição na fila geral (hoje: {item.posicao ?? "—"}º)</span>
                <input
                  type="number"
                  min={1}
                  max={item.posicao ? item.posicao - 1 : undefined}
                  value={posicao}
                  onChange={(e) => setPosicao(e.target.value)}
                  placeholder="Ex.: 1"
                  className="border-input bg-background h-8 rounded-lg border px-2.5 text-sm"
                  aria-label="Nova posição na fila geral"
                />
              </label>
              <div className="flex flex-wrap gap-1.5">
                <Button type="button" size="xs" variant="outline" onClick={() => setPosicao("1")}>
                  1º da fila
                </Button>
                {primeiroDaLista !== null && item.posicao !== null && primeiroDaLista < item.posicao && (
                  <Button type="button" size="xs" variant="outline" onClick={() => setPosicao(String(primeiroDaLista))}>
                    1º desta lista ({primeiroDaLista}º da fila)
                  </Button>
                )}
              </div>
              <p className="text-muted-foreground text-xs">
                O paciente sobe até a posição escolhida e os outros só abrem espaço, na mesma ordem. Só dá para subir: para
                baixar, sugira um rebaixamento de classe. Sem atendimento ou exame de piora registrado, ele não passa à frente
                de quem tem câncer (regra de ouro) e fica logo depois do último paciente oncológico.
              </p>
              {item.posicaoFixada !== null && (
                <div className="rounded-md border border-sky-400/50 bg-sky-500/10 p-2 text-xs">
                  <p>Este paciente já tem posição escolhida ({item.posicaoFixada}º).</p>
                  <Button
                    type="button"
                    size="xs"
                    variant="outline"
                    className="mt-1"
                    disabled={!justOk || enviando}
                    onClick={() => void enviar(true)}
                  >
                    Liberar e voltar para a posição calculada
                  </Button>
                  {!justOk && <span className="ml-1">(escreva a justificativa antes)</span>}
                </div>
              )}
            </div>
          ) : (
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
          )}
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
          <Button type="submit" disabled={!justOk || !posicaoOk || enviando}>
            {enviando
              ? "Registrando…"
              : modo === "posicao"
                ? posicaoOk
                  ? `Colocar na posição ${alvo}`
                  : "Escolha uma posição acima da atual"
                : descer
                  ? "Sugerir rebaixamento"
                  : classe === item.classe
                    ? "Confirmar classe"
                    : "Mudar classe"}
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
          Tudo o que {medico.nome} mudou na prioridade e os exames/eventos que registrou, com justificativa e horário. É a mesma trilha de auditoria que o
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
                  <Link href={`/paciente/${a.solicitacaoId}?medico=${medico.id}`} className="font-mono text-xs hover:underline">
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
                  {a.posicaoFixada !== null && <Badge variant="outline">posição {a.posicaoFixada}</Badge>}
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

/** Selo discreto ao lado da posição: o paciente está numa posição escolhida por um médico ou pelo regulador. */
export function SeloPosicaoEscolhida({ posicao }: { posicao: number | null }) {
  if (posicao === null) return null;
  return (
    <span
      className="block text-[10px] font-medium text-sky-700 dark:text-sky-300"
      title={`Posição escolhida manualmente (${posicao}º). Veja o histórico do paciente.`}
    >
      escolhida
    </span>
  );
}
