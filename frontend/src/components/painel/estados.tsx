"use client";

// Estados comuns das telas do painel (carregando / erro da API / vazio) e utilitários.
import { Inbox, LoaderCircle, RefreshCw, WifiOff } from "lucide-react";
import { API_URL, type ClasseSwalis } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** Converte qualquer erro em texto para o regulador. Falha de rede vira "API fora do ar". */
export function mensagemErro(e: unknown): string {
  if (e instanceof TypeError) return "API fora do ar";
  if (e instanceof Error) {
    if (/failed to fetch|networkerror|load failed|fetch failed/i.test(e.message)) return "API fora do ar";
    return e.message;
  }
  return "Erro inesperado";
}

export function Carregando({ texto = "Carregando…", className }: { texto?: string; className?: string }) {
  return (
    <div className={cn("text-muted-foreground flex items-center justify-center gap-2 py-12 text-sm", className)}>
      <LoaderCircle className="size-4 animate-spin" />
      {texto}
    </div>
  );
}

export function ErroApi({
  mensagem,
  onTentar,
  className,
}: {
  mensagem: string;
  onTentar?: () => void;
  className?: string;
}) {
  const foraDoAr = mensagem === "API fora do ar";
  return (
    <div
      className={cn(
        "border-destructive/30 bg-destructive/5 flex flex-col items-center gap-3 rounded-xl border px-4 py-10 text-center",
        className,
      )}
    >
      <WifiOff className="text-destructive size-6" />
      <div>
        <p className="text-destructive font-medium">{mensagem}</p>
        {foraDoAr && (
          <p className="text-muted-foreground mt-1 text-sm">
            Não foi possível falar com {API_URL || "o backend (localhost:8080)"}. Verifique se o backend está rodando.
          </p>
        )}
      </div>
      {onTentar && (
        <Button variant="outline" size="sm" onClick={onTentar}>
          <RefreshCw /> Tentar de novo
        </Button>
      )}
    </div>
  );
}

export function Vazio({ texto, className }: { texto: string; className?: string }) {
  return (
    <div className={cn("text-muted-foreground flex flex-col items-center gap-2 py-8 text-center text-sm", className)}>
      <Inbox className="size-5" />
      {texto}
    </div>
  );
}

export function formatarPosicao(p: number | null | undefined): string {
  return p === null || p === undefined ? "fora da fila" : `${p}º`;
}

export function formatarHora(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

/** Cores hex das classes (equivalentes às de INFO_CLASSE) para gráficos. */
export const COR_CLASSE_HEX: Record<ClasseSwalis, string> = {
  A1: "#dc2626",
  A2: "#f97316",
  B: "#fbbf24",
  C: "#0ea5e9",
  D: "#94a3b8",
};
