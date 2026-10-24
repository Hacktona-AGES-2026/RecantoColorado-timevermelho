"use client";

// Barras agrupadas por décimo da fila: ordem de hoje (antes) × FilaVIVA (depois).
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export const COR_ANTES = "#7c8799"; // cinza: recessivo de propósito: a ordem de hoje
export const COR_DEPOIS = "#2563eb"; // blue-600: FilaVIVA

export interface PontoFaixa {
  faixa: string;
  antes: number;
  depois: number;
}

export function GraficoFaixas({ dados, unidade = "pacientes" }: { dados: PontoFaixa[]; unidade?: string }) {
  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 8, right: 8, bottom: 0, left: -8 }} barGap={2} barCategoryGap="20%">
          <CartesianGrid vertical={false} stroke="var(--border)" />
          <XAxis
            dataKey="faixa"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 12, fill: "var(--muted-foreground)" }}
            tickFormatter={(v: string) => v.replace(" décimo", "")}
          />
          <YAxis
            allowDecimals={false}
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 12, fill: "var(--muted-foreground)" }}
          />
          <Tooltip
            cursor={{ fill: "var(--muted)", opacity: 0.6 }}
            contentStyle={{
              background: "var(--popover)",
              border: "1px solid var(--border)",
              borderRadius: 8,
              color: "var(--popover-foreground)",
              fontSize: 13,
            }}
            formatter={(valor, nome) => [`${Number(valor).toLocaleString("pt-BR")} ${unidade}`, nome]}
            labelFormatter={(l) => `${l} da fila`}
          />
          <Legend wrapperStyle={{ fontSize: 13 }} iconType="circle" itemSorter={null} />
          <Bar dataKey="antes" name="Hoje (prioridade manual)" fill={COR_ANTES} radius={[4, 4, 0, 0]} />
          <Bar dataKey="depois" name="FilaVIVA" fill={COR_DEPOIS} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
