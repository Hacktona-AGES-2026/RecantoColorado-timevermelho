"use client";

// Coluna "Saúde" das tabelas: diabetes, última HbA1c e comorbidades do cadastro. Só mostra; não muda a fila.
import type { SaudeItem } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatarHba1c(v: number) {
  return v.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

export function CelulaSaude({ saude }: { saude: SaudeItem }) {
  // diabetes vem em selo próprio; o resto da lista vai em texto curto
  const outras = saude.comorbidades.filter((c) => !c.startsWith("Diabetes"));
  const data = saude.dataHba1c ? new Date(saude.dataHba1c + "T12:00").toLocaleDateString("pt-BR") : null;
  return (
    <div className="flex max-w-56 flex-col gap-0.5">
      <div className="flex flex-wrap items-center gap-1">
        {saude.diabetes ? (
          <span
            className="rounded bg-violet-100 px-1.5 text-[10px] font-semibold text-violet-900"
            title={`Diabetes confirmado por: ${saude.fontesDiabetes.join(", ")}${
              saude.divergenciaDiabetes ? ". A flag do cadastro e a lista de comorbidades discordam: confira." : ""
            }`}
          >
            Diabetes{saude.divergenciaDiabetes && " ⚠"}
          </span>
        ) : (
          saude.divergenciaDiabetes && (
            <span className="rounded bg-amber-100 px-1.5 text-[10px] font-medium text-amber-900" title="Flag e comorbidades discordam">
              diabetes? conferir
            </span>
          )
        )}
        {saude.hba1c !== null && (
          <span
            className={cn(
              "rounded px-1.5 text-[10px] font-semibold tabular-nums",
              saude.hba1cAlta ? "bg-red-100 text-red-900" : "bg-slate-100 text-slate-800",
            )}
            title={`Última HbA1c (${saude.origemHba1c === "exame" ? "exame" : "cadastro"}${data ? `, ${data}` : ""})${
              saude.hba1cAlta
                ? saude.diabetes
                  ? ": acima de 8,5%, diabetes descontrolado para cirurgia eletiva"
                  : ": acima de 8,5%. O cadastro não registra diabetes: investigar antes da cirurgia eletiva"
                : ""
            }`}
          >
            HbA1c {formatarHba1c(saude.hba1c)}%
          </span>
        )}
      </div>
      {outras.length > 0 ? (
        <span className="text-muted-foreground block truncate text-[11px]" title={outras.join(", ")}>
          {outras.join(", ")}
        </span>
      ) : (
        !saude.diabetes && saude.hba1c === null && <span className="text-muted-foreground text-[11px]">sem comorbidades</span>
      )}
    </div>
  );
}
