"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { BotIcon, DownloadIcon, SendIcon, SquarePenIcon, TriangleAlertIcon, UserIcon } from "lucide-react";
import { agenteApi, ErroAgente, type EventoChat, type StatusAgente, type TabelaPacientes } from "@/lib/agente";
import { baixarCsvPacientes } from "@/lib/csv-pacientes";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

/** Mesmo limite do backend (agente.limites.max-mensagem). */
export const MAX_MENSAGEM = 1000;

const SUGESTOES = [
  "Como uso a tela de Alertas?",
  "O que significa a classe A2?",
  "Quantos pacientes há em cada classe?",
  "Liste os pacientes de Histerectomia",
  "O que é uma consulta de reavaliação?",
];

type Tipo = "normal" | "recusa" | "emergencia" | "erro";

interface Fala {
  id: number;
  autor: "usuario" | "assistente";
  texto: string;
  tipo: Tipo;
  /** Lista de pacientes lida do dataset pelo backend (não vem da IA). */
  tabela?: TabelaPacientes;
}

/**
 * Chat do assistente. O texto das respostas é mostrado SEMPRE como texto puro (nada de HTML nem Markdown):
 * mesmo que a IA devolva marcação, ela aparece escrita na tela e nunca é executada. Tabelas de pacientes vêm num
 * evento próprio, com os dados do dataset, e também são mostradas só como texto.
 */
export function Chat({ compacto = false, className }: { compacto?: boolean; className?: string }) {
  const [falas, setFalas] = useState<Fala[]>([]);
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [status, setStatus] = useState<StatusAgente | null>(null);
  const [statusFalhou, setStatusFalhou] = useState(false);
  const sessaoId = useRef<string | null>(null);
  const proximoId = useRef(1);
  const cancelar = useRef<AbortController | null>(null);
  const fim = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let ativo = true;
    agenteApi
      .status()
      .then((s) => ativo && setStatus(s))
      .catch(() => ativo && setStatusFalhou(true));
    return () => {
      ativo = false;
      cancelar.current?.abort();
    };
  }, []);

  useEffect(() => {
    fim.current?.scrollIntoView?.({ block: "end" });
  }, [falas]);

  const atualizarUltima = useCallback((mudar: (f: Fala) => Fala) => {
    setFalas((atual) => (atual.length === 0 ? atual : [...atual.slice(0, -1), mudar(atual[atual.length - 1])]));
  }, []);

  const aoEvento = useCallback(
    (e: EventoChat) => {
      switch (e.nome) {
        case "sessao":
          sessaoId.current = e.sessaoId;
          break;
        case "trecho":
          atualizarUltima((f) => ({ ...f, texto: f.texto + e.texto }));
          break;
        case "recusa":
          atualizarUltima((f) => ({ ...f, texto: e.texto, tipo: e.motivo === "RISCO_A_SAUDE" ? "emergencia" : "recusa" }));
          break;
        case "substituir":
          atualizarUltima((f) => ({ ...f, texto: e.texto, tipo: "recusa" }));
          break;
        case "erro":
          atualizarUltima((f) => ({ ...f, texto: e.texto, tipo: "erro" }));
          break;
        case "tabela":
          atualizarUltima((f) => ({ ...f, tabela: { titulo: e.titulo, filtros: e.filtros, total: e.total, linhas: e.linhas } }));
          break;
        case "fim":
          break;
      }
    },
    [atualizarUltima],
  );

  async function enviar(mensagem: string) {
    const pergunta = mensagem.trim();
    if (!pergunta || pergunta.length > MAX_MENSAGEM || enviando) return;
    const idUsuario = proximoId.current++;
    const idAssistente = proximoId.current++;
    setFalas((atual) => [
      ...atual,
      { id: idUsuario, autor: "usuario", texto: pergunta, tipo: "normal" },
      { id: idAssistente, autor: "assistente", texto: "", tipo: "normal" },
    ]);
    setTexto("");
    setEnviando(true);
    const controle = new AbortController();
    cancelar.current = controle;
    try {
      await agenteApi.conversar({ sessaoId: sessaoId.current, mensagem: pergunta }, aoEvento, controle.signal);
    } catch (e) {
      if (controle.signal.aborted) return;
      const msg =
        e instanceof ErroAgente
          ? e.message
          : "Não consegui falar com o servidor. Verifique sua conexão e tente de novo.";
      atualizarUltima((f) => ({ ...f, texto: msg, tipo: "erro" }));
    } finally {
      if (cancelar.current === controle) {
        cancelar.current = null;
        setEnviando(false);
      }
    }
  }

  function novaConversa() {
    cancelar.current?.abort();
    cancelar.current = null;
    sessaoId.current = null;
    setFalas([]);
    setTexto("");
    setEnviando(false);
  }

  const restantes = MAX_MENSAGEM - texto.length;
  const desligado = status !== null && !status.pronto;
  const ultima = falas[falas.length - 1];
  const aguardando = enviando && ultima?.autor === "assistente" && ultima.texto === "";

  return (
    <div className={cn("flex min-h-0 flex-col gap-3", className)}>
      {desligado && (
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm" role="status">
          <p className="flex items-center gap-2 font-medium">
            <TriangleAlertIcon className="size-4" /> O assistente ainda não está disponível.
          </p>
          <p className="text-muted-foreground mt-1 text-xs">
            Você pode escrever, mas ele só responde quando estiver ligado. Detalhe: {status?.motivoDesligado}
          </p>
        </div>
      )}
      {statusFalhou && (
        <div className="bg-destructive/10 text-destructive rounded-lg p-3 text-sm" role="status">
          Não consegui falar com o servidor para saber se o assistente está disponível.
        </div>
      )}

      <div
        className={cn("bg-background flex-1 space-y-3 overflow-y-auto rounded-xl border p-3", compacto ? "min-h-48" : "min-h-80")}
        aria-live="polite"
        aria-label="Conversa com o assistente"
      >
        {falas.length === 0 ? (
          <div className="text-muted-foreground flex flex-col gap-3 text-sm">
            <p>
              Olá! Posso tirar dúvidas sobre consultas, sobre como usar o FilaVIVA, dar informações gerais de saúde e
              alimentação e consultar números e listas da fila (por procedimento, classe, especialidade…).
            </p>
            <div className="flex flex-wrap gap-2">
              {SUGESTOES.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => void enviar(s)}
                  disabled={enviando}
                  className="hover:bg-muted rounded-full border px-3 py-1 text-xs transition-colors disabled:opacity-50"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          falas.map((f) => <Balao key={f.id} fala={f} aguardando={aguardando && f.id === ultima.id} />)
        )}
        <div ref={fim} />
      </div>

      <form
        className="flex flex-col gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void enviar(texto);
        }}
      >
        <Textarea
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              void enviar(texto);
            }
          }}
          placeholder="Escreva sua pergunta…"
          aria-label="Sua pergunta"
          rows={2}
          maxLength={MAX_MENSAGEM + 200}
          aria-invalid={restantes < 0}
        />
        <div className="flex items-center justify-between gap-2">
          <span className={cn("text-xs tabular-nums", restantes < 0 ? "text-destructive font-medium" : "text-muted-foreground")}>
            {restantes < 0 ? `${-restantes} caracteres a mais que o limite` : `${restantes} caracteres restantes`}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="ghost" size="sm" onClick={novaConversa} disabled={falas.length === 0 && !enviando}>
              <SquarePenIcon /> Nova conversa
            </Button>
            <Button type="submit" size="sm" disabled={enviando || texto.trim() === "" || restantes < 0}>
              <SendIcon /> Enviar
            </Button>
          </div>
        </div>
        <p className="text-muted-foreground text-[11px] leading-snug">
          Assistente de inteligência artificial: pode errar e não substitui um profissional de saúde. Não envie dados
          pessoais como CPF, cartão do SUS, telefone ou endereço.
        </p>
      </form>
    </div>
  );
}

function Balao({ fala, aguardando }: { fala: Fala; aguardando: boolean }) {
  const usuario = fala.autor === "usuario";
  return (
    <div className={cn("flex gap-2", usuario && "flex-row-reverse")}>
      <span className={cn("mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full", usuario ? "bg-primary text-primary-foreground" : "bg-muted")}>
        {usuario ? <UserIcon className="size-3.5" /> : <BotIcon className="size-3.5" />}
      </span>
      <div
        className={cn(
          "max-w-[85%] rounded-xl px-3 py-2 text-sm whitespace-pre-wrap break-words",
          usuario && "bg-primary text-primary-foreground",
          !usuario && fala.tipo === "normal" && "bg-muted",
          fala.tipo === "recusa" && "border border-amber-500/40 bg-amber-500/10",
          fala.tipo === "emergencia" && "border-2 border-red-600 bg-red-500/10 font-medium",
          fala.tipo === "erro" && "bg-destructive/10 text-destructive",
        )}
        role={fala.tipo === "emergencia" || fala.tipo === "erro" ? "alert" : undefined}
      >
        {aguardando ? <span className="text-muted-foreground italic">Digitando…</span> : fala.texto}
        {fala.tabela && <TabelaChat tabela={fala.tabela} />}
      </div>
    </div>
  );
}

function TabelaChat({ tabela }: { tabela: TabelaPacientes }) {
  const { linhas, total } = tabela;
  return (
    <div className="bg-background mt-3 rounded-lg border text-xs whitespace-normal" aria-label="Tabela de pacientes">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-2 py-1.5">
        <span className="font-medium">
          {total} paciente(s){linhas.length < total ? ` · mostrando ${linhas.length}` : ""}
        </span>
        <Button type="button" variant="outline" size="sm" onClick={() => baixarCsvPacientes(linhas)}>
          <DownloadIcon /> Baixar CSV
        </Button>
      </div>
      <p className="text-muted-foreground px-2 pt-1.5">{tabela.filtros}</p>
      <div className="max-h-72 overflow-auto">
        <table className="w-full border-collapse">
          <thead className="bg-background sticky top-0">
            <tr className="text-muted-foreground text-left">
              <th className="px-2 py-1 font-medium">#</th>
              <th className="px-2 py-1 font-medium">Paciente</th>
              <th className="px-2 py-1 font-medium">Classe</th>
              <th className="px-2 py-1 font-medium">CID</th>
              <th className="px-2 py-1 font-medium whitespace-nowrap">Espera / prazo</th>
            </tr>
          </thead>
          <tbody>
            {linhas.map((l) => (
              <tr key={l.solicitacaoId} className="border-t align-top">
                <td className="px-2 py-1 tabular-nums">{l.posicao ?? "—"}</td>
                <td className="px-2 py-1">
                  <span className="font-medium">{l.nome}</span>
                  <span className="text-muted-foreground block">
                    {l.solicitacaoId}
                    {l.idade !== null ? ` · ${l.idade} a` : ""}
                    {l.sexo ? ` · ${l.sexo}` : ""}
                    {l.procedimento ? ` · ${l.procedimento}` : ""}
                  </span>
                </td>
                <td className="px-2 py-1 font-medium">{l.classe}</td>
                <td className="px-2 py-1 whitespace-nowrap">
                  {l.cid ?? "—"}
                  {l.oncologico ? " · onco" : ""}
                </td>
                <td className="px-2 py-1 whitespace-nowrap tabular-nums">
                  {l.diasEmFila} d / {l.prazoDias} d
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-muted-foreground border-t px-2 py-1.5">
        Lista lida do dataset pelo sistema, como na tela Fila (não foi escrita pela IA).
      </p>
    </div>
  );
}
