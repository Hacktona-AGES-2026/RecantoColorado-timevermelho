"use client";

// Área do regulador para rebaixamentos: o sistema (ou alguém) sugere, o regulador confirma ou rejeita.
// Divergência histórica com a prioridade manual NÃO aparece aqui (fica em "Reavaliação").
import { useState } from "react";
import Link from "next/link";
import { ArrowDown, ShieldCheck } from "lucide-react";
import { api, type EstadoRebaixamento, type Rebaixamento } from "@/lib/api";
import { SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { formatarHora, mensagemErro, Vazio } from "./estados";

const MIN_JUSTIFICATIVA = 10;

const ESTADO: Record<EstadoRebaixamento, { rotulo: string; cor: string }> = {
  PENDENTE: { rotulo: "pendente", cor: "bg-violet-600 text-white" },
  CONFIRMADO: { rotulo: "confirmado", cor: "bg-slate-600 text-white" },
  REJEITADO: { rotulo: "rejeitado · classe mantida", cor: "bg-emerald-600 text-white" },
  BLOQUEADO: { rotulo: "bloqueado · oncológico", cor: "bg-red-600 text-white" },
};

export function Rebaixamentos({ itens, aoDecidir }: { itens: Rebaixamento[]; aoDecidir: () => void }) {
  const pendentes = itens.filter((r) => r.estado === "PENDENTE");
  const historico = itens.filter((r) => r.estado !== "PENDENTE");
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ArrowDown className="size-4" /> Rebaixamentos sugeridos
          <Badge variant="secondary" className="tabular-nums">
            {pendentes.length}
          </Badge>
        </CardTitle>
        <CardDescription>
          Nenhuma classe cai sem você. Uma classe menos urgente pedida por alguém, ou que o recálculo daria, fica aqui
          como sugestão, e a classe vigente continua valendo até você confirmar ou rejeitar. Paciente oncológico nunca tem
          a classe reduzida.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {pendentes.length === 0 ? (
          <Vazio texto="Nenhum rebaixamento aguardando decisão." />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {pendentes.map((r) => (
              <Pendente key={`${r.solicitacaoId}-${r.sugeridoEm}`} r={r} aoDecidir={aoDecidir} />
            ))}
          </div>
        )}
        {historico.length > 0 && (
          <div>
            <p className="mb-1 text-xs font-medium">Decididos e bloqueados recentemente</p>
            <ul className="divide-y text-sm">
              {historico.map((r) => (
                <li key={`${r.solicitacaoId}-${r.sugeridoEm}`} className="flex flex-wrap items-center gap-2 py-1.5">
                  <Link href={`/paciente/${r.solicitacaoId}`} className="font-medium hover:underline">
                    {r.nome ?? r.solicitacaoId}
                  </Link>
                  <SeloClasse classe={r.classeVigente} /> → <SeloClasse classe={r.classeProposta} />
                  <span className={cn("rounded px-1.5 text-[10px] font-semibold", ESTADO[r.estado].cor)}>
                    {ESTADO[r.estado].rotulo}
                  </span>
                  <span className="text-muted-foreground ml-auto text-xs">
                    {r.decididoPor} · {formatarHora(r.decididoEm)}
                  </span>
                  {r.decisao && <p className="text-muted-foreground w-full text-xs">“{r.decisao}”</p>}
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Pendente({ r, aoDecidir }: { r: Rebaixamento; aoDecidir: () => void }) {
  const [justificativa, setJustificativa] = useState("");
  const [enviando, setEnviando] = useState<"CONFIRMAR" | "REJEITAR" | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const justOk = justificativa.trim().length >= MIN_JUSTIFICATIVA;

  async function decidir(tipo: "CONFIRMAR_REBAIXAMENTO" | "REJEITAR_REBAIXAMENTO") {
    setEnviando(tipo === "CONFIRMAR_REBAIXAMENTO" ? "CONFIRMAR" : "REJEITAR");
    setErro(null);
    try {
      await api.ajustar({ solicitacaoId: r.solicitacaoId, tipo, justificativa: justificativa.trim(), autor: "regulador" });
      aoDecidir();
    } catch (e) {
      setErro(mensagemErro(e));
    } finally {
      setEnviando(null);
    }
  }

  return (
    <div className={cn("flex flex-col gap-2 rounded-xl border p-4", r.oncologico && "border-red-500")}>
      <div className="flex flex-wrap items-center gap-2">
        <Link href={`/paciente/${r.solicitacaoId}`} className="font-semibold hover:underline">
          {r.nome ?? r.solicitacaoId}
        </Link>
        <span className="text-muted-foreground text-xs">
          {r.solicitacaoId}
          {r.posicao !== null && ` · posição ${r.posicao}`}
        </span>
      </div>
      <p className="flex flex-wrap items-center gap-1.5 text-sm">
        <SeloClasse classe={r.classeVigente} /> → <SeloClasse classe={r.classeProposta} />
        <span className="text-muted-foreground text-xs">
          (vale hoje: {r.classeAtual ?? r.classeVigente}) · {r.automatico ? "recálculo automático" : r.sugeridoPor} ·{" "}
          {formatarHora(r.sugeridoEm)}
        </span>
      </p>
      <p className="text-sm">
        <span className="font-medium">Fatores:</span> {r.fatores}
      </p>
      {r.evidencias && (
        <p className="text-muted-foreground text-xs">
          <span className="font-medium">Evidências:</span> {r.evidencias}
        </p>
      )}
      <Textarea
        value={justificativa}
        onChange={(e) => setJustificativa(e.target.value)}
        placeholder="Justificativa da decisão (obrigatória)"
        rows={2}
        aria-label={`Justificativa para ${r.nome ?? r.solicitacaoId}`}
      />
      <div className="flex flex-wrap gap-2">
        <Button size="sm" disabled={!justOk || enviando !== null} onClick={() => decidir("CONFIRMAR_REBAIXAMENTO")}>
          {enviando === "CONFIRMAR" ? "Registrando…" : "Confirmar rebaixamento"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!justOk || enviando !== null}
          onClick={() => decidir("REJEITAR_REBAIXAMENTO")}
        >
          <ShieldCheck /> {enviando === "REJEITAR" ? "Registrando…" : "Rejeitar e manter a classe"}
        </Button>
      </div>
      {erro && <p className="text-destructive text-xs">{erro}</p>}
    </div>
  );
}
