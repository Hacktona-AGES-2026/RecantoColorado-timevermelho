"use client";

// Select simples (Base UI) com rótulos: valor em string, opções {valor, rotulo}.
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";

export interface Opcao {
  valor: string;
  rotulo: string;
}

export function Escolha({
  valor,
  onChange,
  opcoes,
  className,
  ariaLabel,
}: {
  valor: string;
  onChange: (valor: string) => void;
  opcoes: Opcao[];
  className?: string;
  ariaLabel?: string;
}) {
  const rotulos = Object.fromEntries(opcoes.map((o) => [o.valor, o.rotulo]));
  return (
    <Select items={rotulos} value={valor} onValueChange={(v) => v !== null && onChange(String(v))}>
      <SelectTrigger className={cn("w-full", className)} aria-label={ariaLabel}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {opcoes.map((o) => (
          <SelectItem key={o.valor} value={o.valor}>
            {o.rotulo}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
