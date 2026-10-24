"use client";

// Peças pequenas usadas pelas telas de Fila e Paciente (Dev 4).
import { AlertTriangleIcon, InboxIcon, Loader2Icon } from "lucide-react";
import { API_URL, type Impacto } from "@/lib/api";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SeloClasse } from "@/components/fila-ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** Select simples de valores string (Base UI), com rótulos mostrados no gatilho. */
export function CampoSelect({
  valor,
  aoMudar,
  opcoes,
  className,
  rotulo,
}: {
  valor: string;
  aoMudar: (v: string) => void;
  opcoes: { valor: string; rotulo: string }[];
  className?: string;
  rotulo?: string;
}) {
  const itens = Object.fromEntries(opcoes.map((o) => [o.valor, o.rotulo]));
  return (
    <Select items={itens} value={valor} onValueChange={(v) => aoMudar((v as string | null) ?? "")}>
      <SelectTrigger className={cn("w-full", className)} aria-label={rotulo}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent alignItemWithTrigger={false}>
        {opcoes.map((o) => (
          <SelectItem key={o.valor} value={o.valor}>
            {o.rotulo}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function Carregando({ texto = "Carregando…", className }: { texto?: string; className?: string }) {
  return (
    <div className={cn("text-muted-foreground flex items-center justify-center gap-2 py-12 text-sm", className)}>
      <Loader2Icon className="size-4 animate-spin" />
      {texto}
    </div>
  );
}

/** Erro de API. Falha de rede (fetch rejeitado) vira "API fora do ar". */
export function ErroApi({ erro, aoTentar }: { erro: string; aoTentar?: () => void }) {
  const foraDoAr = /failed to fetch|networkerror|load failed|fetch failed/i.test(erro);
  return (
    <div className="flex flex-col items-center gap-3 py-12 text-center text-sm">
      <AlertTriangleIcon className="text-destructive size-6" />
      <div>
        <p className="font-medium">{foraDoAr ? "API fora do ar" : "Não foi possível carregar"}</p>
        <p className="text-muted-foreground mt-1">
          {foraDoAr ? `Não conseguimos falar com ${API_URL || "o backend (localhost:8080)"}. Verifique se o backend está rodando.` : erro}
        </p>
      </div>
      {aoTentar && (
        <Button variant="outline" size="sm" onClick={aoTentar}>
          Tentar de novo
        </Button>
      )}
    </div>
  );
}

export function Vazio({ texto }: { texto: string }) {
  return (
    <div className="text-muted-foreground flex flex-col items-center gap-2 py-12 text-sm">
      <InboxIcon className="size-6" />
      {texto}
    </div>
  );
}

export function mensagemErro(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

const ROTULO_STATUS: Record<string, string> = {
  ATIVA: "Ativa",
  SAIDA_SUGERIDA: "Saída sugerida",
  SAIDA_CONFIRMADA: "Saída confirmada",
};

export function rotuloStatus(s: string): string {
  return ROTULO_STATUS[s] ?? s;
}

function pos(p: number | null) {
  return p === null ? "fora da fila" : `${p}º`;
}

/** Resultado de um ajuste ou evento: o que mudou na fila. */
export function CartaoImpacto({ impacto, aoFechar }: { impacto: Impacto; aoFechar?: () => void }) {
  const subiu =
    impacto.posicaoAntes !== null && impacto.posicaoDepois !== null && impacto.posicaoDepois < impacto.posicaoAntes;
  const desceu =
    impacto.posicaoAntes !== null && impacto.posicaoDepois !== null && impacto.posicaoDepois > impacto.posicaoAntes;
  return (
    <div className="rounded-lg border border-emerald-600/30 bg-emerald-500/5 p-3 text-sm">
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium">Impacto na fila</p>
        {aoFechar && (
          <button className="text-muted-foreground hover:text-foreground text-xs" onClick={aoFechar}>
            fechar
          </button>
        )}
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-6 gap-y-2">
        <div className="flex items-center gap-2">
          <span className="text-muted-foreground text-xs">Posição</span>
          <span className="tabular-nums">{pos(impacto.posicaoAntes)}</span>
          <span>→</span>
          <span
            className={cn(
              "font-semibold tabular-nums",
              subiu && "text-emerald-600",
              desceu && "text-rose-600",
            )}
          >
            {pos(impacto.posicaoDepois)}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-muted-foreground text-xs">Classe</span>
          <SeloClasse classe={impacto.classeAntes} />
          <span>→</span>
          <SeloClasse classe={impacto.classeDepois} />
        </div>
        {impacto.statusAntes !== impacto.statusDepois && (
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs">Status</span>
            <span>{rotuloStatus(impacto.statusAntes)}</span>
            <span>→</span>
            <span className="font-medium">{rotuloStatus(impacto.statusDepois)}</span>
          </div>
        )}
      </div>
      {impacto.mensagem && <p className="text-muted-foreground mt-2">{impacto.mensagem}</p>}
    </div>
  );
}
