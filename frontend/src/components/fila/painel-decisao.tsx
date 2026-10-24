"use client";

import { useState } from "react";
import { api, CLASSES, INFO_CLASSE, type ClasseSwalis, type Impacto, type PacienteDetalhe, type TipoAjuste } from "@/lib/api";
import { SeloClasse } from "@/components/fila-ui";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { CartaoImpacto, mensagemErro } from "./comum";
import { cn } from "@/lib/utils";

const ROTULO_ACAO: Record<TipoAjuste, string> = {
  CONFIRMAR_CLASSE: "Confirmar classe",
  ALTERAR_CLASSE: "Mudar classe",
  CONFIRMAR_SAIDA: "Confirmar saída",
  RECUSAR_SAIDA: "Recusar saída",
  SUGERIR_REBAIXAMENTO: "Sugerir rebaixamento",
  CONFIRMAR_REBAIXAMENTO: "Confirmar rebaixamento",
  REJEITAR_REBAIXAMENTO: "Rejeitar rebaixamento",
  REBAIXAMENTO_BLOQUEADO: "Rebaixamento bloqueado",
  FIXAR_POSICAO: "Escolher posição",
  LIBERAR_POSICAO: "Liberar posição",
};

const ROTULO_ESTADO: Record<string, string> = {
  CONFIRMADO: "Rebaixamento confirmado pelo regulador",
  REJEITADO: "Rebaixamento rejeitado: a classe vigente foi mantida",
  BLOQUEADO: "Rebaixamento bloqueado: paciente oncológico não tem a classe reduzida",
};

const MIN_JUSTIFICATIVA = 10;

/** Painel onde o regulador dá a última palavra. Toda decisão exige justificativa. */
export function PainelDecisao({ detalhe, aoDecidir }: { detalhe: PacienteDetalhe; aoDecidir: () => void }) {
  const { item, sinalSaida, rebaixamento } = detalhe;
  const temSaida = !!sinalSaida || item.status !== "ATIVA";
  const pendente = rebaixamento?.estado === "PENDENTE" ? rebaixamento : null;
  const acoes: TipoAjuste[] = ["CONFIRMAR_CLASSE", "ALTERAR_CLASSE"];
  if (pendente) acoes.push("CONFIRMAR_REBAIXAMENTO", "REJEITAR_REBAIXAMENTO");
  if (temSaida) acoes.push("CONFIRMAR_SAIDA", "RECUSAR_SAIDA");

  const [acao, setAcao] = useState<TipoAjuste>("CONFIRMAR_CLASSE");
  const [classeNova, setClasseNova] = useState<ClasseSwalis | null>(null);
  const [justificativa, setJustificativa] = useState("");
  const [autor, setAutor] = useState("regulador");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [impacto, setImpacto] = useState<Impacto | null>(null);

  const acaoValida = acoes.includes(acao) ? acao : "CONFIRMAR_CLASSE";
  const justOk = justificativa.trim().length >= MIN_JUSTIFICATIVA;
  const classeOk = acaoValida !== "ALTERAR_CLASSE" || (classeNova !== null && classeNova !== item.classe);
  // classe menos urgente que a vigente: vira rebaixamento sugerido (não vale até o regulador confirmar)
  const ehRebaixamento =
    acaoValida === "ALTERAR_CLASSE" && classeNova !== null && CLASSES.indexOf(classeNova) > CLASSES.indexOf(item.classe);
  const rotuloEnviar = ehRebaixamento ? "Sugerir rebaixamento" : ROTULO_ACAO[acaoValida];

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    if (!justOk || !classeOk) return;
    setEnviando(true);
    setErro(null);
    setImpacto(null);
    try {
      const r = await api.ajustar({
        solicitacaoId: item.solicitacaoId,
        tipo: acaoValida,
        classeNova: acaoValida === "ALTERAR_CLASSE" ? (classeNova ?? undefined) : undefined,
        justificativa: justificativa.trim(),
        autor: autor.trim() || "regulador",
      });
      setImpacto(r);
      setJustificativa("");
      setClasseNova(null);
      aoDecidir();
    } catch (err) {
      setErro(mensagemErro(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card className="ring-primary/30">
      <CardHeader>
        <CardTitle>Decisão do regulador</CardTitle>
        <CardDescription>O sistema propõe, você decide. Toda decisão fica registrada com justificativa.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={enviar} className="flex flex-col gap-3">
          {pendente && (
            <div className="rounded-lg border border-violet-500/40 bg-violet-500/10 p-2 text-xs">
              <p className="flex flex-wrap items-center gap-1 font-medium">
                Rebaixamento sugerido, aguardando o regulador: <SeloClasse classe={pendente.classeVigente} /> →{" "}
                <SeloClasse classe={pendente.classeProposta} />
              </p>
              <p className="text-muted-foreground mt-0.5">
                A classe {item.classe} continua valendo até a decisão. Sugerido por {pendente.sugeridoPor}.
              </p>
              <p className="mt-1">
                <span className="font-medium">Fatores:</span> {pendente.fatores}
              </p>
              {pendente.evidencias && (
                <p className="mt-0.5">
                  <span className="font-medium">Evidências:</span> {pendente.evidencias}
                </p>
              )}
            </div>
          )}
          {rebaixamento && !pendente && (
            <p className="text-muted-foreground text-xs">
              {ROTULO_ESTADO[rebaixamento.estado]} ({rebaixamento.classeVigente} → {rebaixamento.classeProposta}
              {rebaixamento.decididoPor ? `, ${rebaixamento.decididoPor}` : ""}).
            </p>
          )}

          {sinalSaida && (
            <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-2 text-xs">
              <p className="font-medium">Saída sugerida: {sinalSaida.rotulo}</p>
              <p className="text-muted-foreground mt-0.5">{sinalSaida.descricao}</p>
              {sinalSaida.evidencia && <p className="mt-1 italic">“{sinalSaida.evidencia}”</p>}
            </div>
          )}

          <div className="grid grid-cols-2 gap-1.5">
            {acoes.map((a) => (
              <Button
                key={a}
                type="button"
                variant={acaoValida === a ? "default" : "outline"}
                size="sm"
                onClick={() => {
                  setAcao(a);
                  setErro(null);
                }}
              >
                {ROTULO_ACAO[a]}
              </Button>
            ))}
          </div>

          {acaoValida === "CONFIRMAR_CLASSE" && (
            <p className="text-muted-foreground flex items-center gap-2 text-xs">
              Confirmar <SeloClasse classe={item.classe} /> como a classe deste paciente.
            </p>
          )}

          {acaoValida === "ALTERAR_CLASSE" && (
            <div className="flex flex-col gap-1.5">
              <span className="text-xs font-medium">Nova classe</span>
              <div className="flex flex-wrap gap-1.5">
                {CLASSES.map((c) => {
                  const bloqueada = item.oncologico && CLASSES.indexOf(c) > CLASSES.indexOf(item.classe);
                  return (
                  <button
                    key={c}
                    type="button"
                    disabled={c === item.classe || bloqueada}
                    onClick={() => setClasseNova(c)}
                    title={bloqueada ? "Bloqueado: paciente oncológico não tem a classe reduzida" : INFO_CLASSE[c].nome}
                    className={cn(
                      "rounded-md p-0.5 ring-2 ring-transparent transition disabled:cursor-not-allowed disabled:opacity-40",
                      classeNova === c && "ring-primary",
                    )}
                  >
                    <SeloClasse classe={c} />
                  </button>
                  );
                })}
              </div>
              {item.oncologico && (
                <p className="text-xs text-amber-700 dark:text-amber-400">
                  Paciente oncológico: a classe {item.classe} pode subir, mas nunca ser reduzida (nem de A1 para A2).
                </p>
              )}
              {ehRebaixamento && (
                <p className="text-xs text-violet-700 dark:text-violet-300">
                  Classe menos urgente que a vigente: isto registra um rebaixamento sugerido. A classe {item.classe} continua
                  valendo até o regulador confirmar ou rejeitar.
                </p>
              )}
            </div>
          )}

          {(acaoValida === "CONFIRMAR_REBAIXAMENTO" || acaoValida === "REJEITAR_REBAIXAMENTO") && pendente && (
            <p className="text-muted-foreground text-xs">
              {acaoValida === "CONFIRMAR_REBAIXAMENTO"
                ? `A classe passa de ${item.classe} para ${pendente.classeProposta}.`
                : `A classe ${item.classe} é mantida; a sugestão fica no histórico.`}
            </p>
          )}

          {(acaoValida === "CONFIRMAR_SAIDA" || acaoValida === "RECUSAR_SAIDA") && (
            <p className="text-muted-foreground text-xs">
              {acaoValida === "CONFIRMAR_SAIDA"
                ? "O paciente sai da fila ativa (a decisão pode ser revista depois)."
                : "O paciente permanece (ou volta) na fila ativa."}
            </p>
          )}

          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Justificativa (obrigatória)</span>
            <Textarea
              value={justificativa}
              onChange={(e) => setJustificativa(e.target.value)}
              placeholder="Ex.: Laudo confirma progressão; mantenho prioridade máxima."
              aria-invalid={justificativa.length > 0 && !justOk}
              rows={3}
            />
            <span className={cn("text-[11px]", justOk ? "text-muted-foreground" : "text-amber-700 dark:text-amber-400")}>
              {justificativa.trim().length}/{MIN_JUSTIFICATIVA} caracteres mínimos
            </span>
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Autor</span>
            <Input value={autor} onChange={(e) => setAutor(e.target.value)} />
          </label>

          {erro && (
            <div className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm" role="alert">
              <p className="font-medium">Decisão recusada</p>
              <p>{erro}</p>
            </div>
          )}

          <Button type="submit" disabled={!justOk || !classeOk || enviando}>
            {enviando ? "Registrando…" : rotuloEnviar}
          </Button>

          {impacto && <CartaoImpacto impacto={impacto} aoFechar={() => setImpacto(null)} />}
        </form>
      </CardContent>
    </Card>
  );
}
