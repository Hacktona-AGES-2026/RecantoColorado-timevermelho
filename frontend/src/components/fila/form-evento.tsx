"use client";

import { useState } from "react";
import { api, TIPOS_EVENTO, type Impacto } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { CampoSelect, CartaoImpacto, mensagemErro } from "./comum";

export function rotuloResultado(r: string): string {
  const t = r.replace(/_/g, " ");
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export function rotuloTipoEvento(tipo: string): string {
  return TIPOS_EVENTO.find((t) => t.tipo === tipo)?.rotulo.replace(/ \(.*\)$/, "") ?? rotuloResultado(tipo);
}

/** Registra um evento clínico novo; a fila é recalculada e mostramos o impacto. */
export function FormEvento({ solicitacaoId, aoRegistrar }: { solicitacaoId: string; aoRegistrar: () => void }) {
  const [tipo, setTipo] = useState(TIPOS_EVENTO[0].tipo);
  const [resultado, setResultado] = useState(TIPOS_EVENTO[0].resultados[0]);
  const [valor, setValor] = useState("");
  const [descricao, setDescricao] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [impacto, setImpacto] = useState<Impacto | null>(null);

  const def = TIPOS_EVENTO.find((t) => t.tipo === tipo) ?? TIPOS_EVENTO[0];
  const valorNum = valor.trim() === "" ? undefined : Number(valor.replace(",", "."));
  const valorInvalido = valorNum !== undefined && Number.isNaN(valorNum);

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    if (valorInvalido) return;
    setEnviando(true);
    setErro(null);
    setImpacto(null);
    try {
      const r = await api.registrarEvento({
        solicitacaoId,
        tipo,
        resultado: resultado || undefined,
        valorNumerico: valorNum,
        descricao: descricao.trim() || undefined,
      });
      setImpacto(r);
      setValor("");
      setDescricao("");
      aoRegistrar();
    } catch (err) {
      setErro(mensagemErro(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Registrar evento clínico</CardTitle>
        <CardDescription>A fila é recalculada na hora com o evento novo.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={enviar} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Tipo</span>
            <CampoSelect
              rotulo="Tipo de evento"
              valor={tipo}
              aoMudar={(v) => {
                const novo = TIPOS_EVENTO.find((t) => t.tipo === v);
                if (!novo) return;
                setTipo(novo.tipo);
                setResultado(novo.resultados[0]);
              }}
              opcoes={TIPOS_EVENTO.map((t) => ({ valor: t.tipo, rotulo: t.rotulo }))}
            />
          </label>
          <div className="grid grid-cols-[1fr_7rem] gap-2">
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium">Resultado</span>
              <CampoSelect
                rotulo="Resultado"
                valor={resultado}
                aoMudar={setResultado}
                opcoes={def.resultados.map((r) => ({ valor: r, rotulo: rotuloResultado(r) }))}
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium">Valor</span>
              <Input
                inputMode="decimal"
                value={valor}
                onChange={(e) => setValor(e.target.value)}
                placeholder="opcional"
                aria-invalid={valorInvalido}
              />
            </label>
          </div>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium">Descrição</span>
            <Textarea
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
              placeholder="Ex.: Anatomopatológico: adenocarcinoma."
              rows={2}
            />
          </label>
          {erro && (
            <p className="bg-destructive/10 text-destructive rounded-lg p-2 text-sm" role="alert">
              {erro}
            </p>
          )}
          <Button type="submit" variant="secondary" disabled={enviando || valorInvalido}>
            {enviando ? "Registrando…" : "Registrar evento"}
          </Button>
          {impacto && <CartaoImpacto impacto={impacto} aoFechar={() => setImpacto(null)} />}
        </form>
      </CardContent>
    </Card>
  );
}
