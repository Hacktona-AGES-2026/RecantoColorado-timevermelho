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
};

const MIN_JUSTIFICATIVA = 10;

/** Painel onde o regulador dá a última palavra. Toda decisão exige justificativa. */
export function PainelDecisao({ detalhe, aoDecidir }: { detalhe: PacienteDetalhe; aoDecidir: () => void }) {
  const { item, sinalSaida } = detalhe;
  const temSaida = !!sinalSaida || item.status !== "ATIVA";
  const acoes: TipoAjuste[] = ["CONFIRMAR_CLASSE", "ALTERAR_CLASSE"];
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
  const rebaixaOnco =
    acaoValida === "ALTERAR_CLASSE" &&
    item.oncologico &&
    classeNova !== null &&
    CLASSES.indexOf(classeNova) > CLASSES.indexOf(item.classe);

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
                {CLASSES.map((c) => (
                  <button
                    key={c}
                    type="button"
                    disabled={c === item.classe}
                    onClick={() => setClasseNova(c)}
                    title={INFO_CLASSE[c].nome}
                    className={cn(
                      "rounded-md p-0.5 ring-2 ring-transparent transition disabled:cursor-not-allowed disabled:opacity-40",
                      classeNova === c && "ring-primary",
                    )}
                  >
                    <SeloClasse classe={c} />
                  </button>
                ))}
              </div>
              {rebaixaOnco && (
                <p className="text-xs text-amber-700 dark:text-amber-400">
                  Atenção: paciente oncológico. A regra de ouro impede que ele fique abaixo de quem não tem urgência; o
                  sistema pode recusar este rebaixamento.
                </p>
              )}
            </div>
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
            {enviando ? "Registrando…" : ROTULO_ACAO[acaoValida]}
          </Button>

          {impacto && <CartaoImpacto impacto={impacto} aoFechar={() => setImpacto(null)} />}
        </form>
      </CardContent>
    </Card>
  );
}
