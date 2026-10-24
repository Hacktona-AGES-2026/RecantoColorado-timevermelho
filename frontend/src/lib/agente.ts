// Cliente da API do assistente de IA do FilaVIVA (chat, categorização e relatórios).
// O navegador só fala com /api/agente/*: as chaves das APIs de IA ficam no backend e nunca chegam aqui.
// Contrato: Docs/API_CONTRACT.md e docs/AGENTE-IA.md.

import { API_URL } from "@/lib/api";

// ---------- erros ----------

/** Erro devolvido pela API do assistente (400, 429, 503…), com a mensagem pronta para mostrar ao usuário. */
export class ErroAgente extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
    this.name = "ErroAgente";
  }
}

async function erroDe(resp: Response): Promise<ErroAgente> {
  let mensagem = `Erro ${resp.status}`;
  try {
    const corpo = await resp.json();
    mensagem = corpo.mensagem ?? corpo.message ?? mensagem;
  } catch {
    // corpo sem JSON: fica "Erro <status>"
  }
  return new ErroAgente(mensagem, resp.status);
}

// ---------- chat (fluxo de eventos) ----------

export type EventoChat =
  | { nome: "sessao"; sessaoId: string }
  | { nome: "trecho"; texto: string }
  | { nome: "recusa"; motivo: string; texto: string }
  | { nome: "substituir"; texto: string }
  | { nome: "erro"; texto: string }
  | { nome: "fim"; versaoPrompt: string };

export interface PedidoChat {
  sessaoId?: string | null;
  mensagem: string;
}

/** Converte um evento bruto ("event:" + "data:") no tipo certo; ignora o que não conhece. */
export function lerEvento(nome: string, dados: string): EventoChat | null {
  let corpo: Record<string, unknown>;
  try {
    corpo = JSON.parse(dados);
  } catch {
    return null;
  }
  if (corpo === null || typeof corpo !== "object") return null;
  const texto = (k: string) => (typeof corpo[k] === "string" ? (corpo[k] as string) : "");
  switch (nome) {
    case "sessao":
      return { nome, sessaoId: texto("sessaoId") };
    case "trecho":
      return { nome, texto: texto("texto") };
    case "recusa":
      return { nome, motivo: texto("motivo"), texto: texto("texto") };
    case "substituir":
      return { nome, texto: texto("texto") };
    case "erro":
      return { nome, texto: texto("texto") };
    case "fim":
      return { nome, versaoPrompt: texto("versaoPrompt") };
    default:
      return null;
  }
}

/**
 * Manda uma mensagem e entrega os eventos conforme chegam. Usa fetch + leitura do corpo em partes (a API de eventos
 * pronta do navegador não aceita POST). Termina quando o servidor fecha o fluxo; cancele com o AbortSignal.
 */
export async function conversar(pedido: PedidoChat, aoEvento: (e: EventoChat) => void, signal?: AbortSignal): Promise<void> {
  const resp = await fetch(`${API_URL}/api/agente/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify({ sessaoId: pedido.sessaoId ?? undefined, mensagem: pedido.mensagem }),
    signal,
  });
  if (!resp.ok) throw await erroDe(resp);
  if (!resp.body) throw new ErroAgente("O navegador não conseguiu ler a resposta.", 0);

  const leitor = resp.body.getReader();
  const decodificador = new TextDecoder();
  let pendente = "";
  for (;;) {
    const { done, value } = await leitor.read();
    if (done) break;
    pendente += decodificador.decode(value, { stream: true }).replace(/\r\n/g, "\n");
    let corte: number;
    while ((corte = pendente.indexOf("\n\n")) >= 0) {
      const bloco = pendente.slice(0, corte);
      pendente = pendente.slice(corte + 2);
      let nome = "";
      const dados: string[] = [];
      for (const linha of bloco.split("\n")) {
        if (linha.startsWith("event:")) nome = linha.slice(6).trim();
        else if (linha.startsWith("data:")) dados.push(linha.slice(5).trimStart());
      }
      const evento = nome ? lerEvento(nome, dados.join("\n")) : null;
      if (evento) aoEvento(evento);
    }
  }
}

// ---------- status ----------

export interface StatusProvedor {
  nome: string;
  situacao: "ATIVO" | "INDISPONIVEL";
  indisponivelAte: string | null;
  motivo: string | null;
  chamadas: number;
  falhas: number;
  tokensEntrada: number;
  tokensSaida: number;
}

export interface StatusAgente {
  pronto: boolean;
  motivoDesligado: string | null;
  versaoPrompt: string;
  moderacaoAtiva: boolean;
  sessoesAtivas: number;
  recusasUltimos10Min: number;
  tokensUltimaHora: number;
  provedores: StatusProvedor[];
}

// ---------- categorização ----------

export type CategoriaDado =
  | "DUVIDA_CONSULTA"
  | "USO_DA_APLICACAO"
  | "SAUDE_GERAL"
  | "ALIMENTACAO"
  | "SINTOMA_RELATADO"
  | "ASSUNTO_ADMINISTRATIVO"
  | "OUTRO";

export interface Categorizacao {
  id: string;
  texto: string;
  categoria: CategoriaDado;
  categoriaRotulo: string;
  subtemas: string[];
  confianca: number;
  justificativa: string;
  origem: "IA" | "REGRA_FIXA";
  atencaoHumana: boolean;
  statusRevisao: "PENDENTE" | "ACEITA" | "CORRIGIDA";
  categoriaFinal: CategoriaDado | null;
  justificativaRevisao: string | null;
  revisadoPor: string | null;
  revisadoEm: string | null;
  criadoEm: string;
  versaoPrompt: string;
  sugestaoDeIA: boolean;
  aviso: string;
}

export interface RevisaoCategorizacao {
  acao: "ACEITAR" | "CORRIGIR";
  categoriaCorrigida?: CategoriaDado;
  justificativa?: string;
  autor?: string;
}

// ---------- relatórios ----------

export type TipoRelatorio = "VISAO_GERAL" | "ESPECIALIDADE";

export interface RelatorioResumo {
  id: string;
  tipo: TipoRelatorio;
  especialidade: string | null;
  titulo: string;
  solicitante: string;
  criadoEm: string;
}

export interface Relatorio extends RelatorioResumo {
  texto: string;
  dadosUsados: string;
  versaoPrompt: string;
  geradoPorIA: boolean;
}

export interface PedidoRelatorio {
  tipo: TipoRelatorio;
  especialidade?: string;
  solicitante?: string;
}

// ---------- chamadas simples ----------

async function get<T>(rota: string): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, { cache: "no-store" });
  if (!resp.ok) throw await erroDe(resp);
  return resp.json() as Promise<T>;
}

async function post<T>(rota: string, corpo: unknown): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  if (!resp.ok) throw await erroDe(resp);
  return resp.json() as Promise<T>;
}

export const agenteApi = {
  status: () => get<StatusAgente>("/api/agente/status"),
  conversar,

  categorizar: (texto: string, solicitante?: string) => post<Categorizacao>("/api/agente/categorizar", { texto, solicitante }),
  categorizacoes: (status?: Categorizacao["statusRevisao"]) =>
    get<Categorizacao[]>(`/api/agente/categorizacoes${status ? `?status=${status}` : ""}`),
  revisarCategorizacao: (id: string, revisao: RevisaoCategorizacao) =>
    post<Categorizacao>(`/api/agente/categorizacoes/${encodeURIComponent(id)}/revisao`, revisao),

  gerarRelatorio: (pedido: PedidoRelatorio) => post<Relatorio>("/api/agente/relatorios", pedido),
  relatorios: () => get<RelatorioResumo[]>("/api/agente/relatorios"),
  relatorio: (id: string) => get<Relatorio>(`/api/agente/relatorios/${encodeURIComponent(id)}`),
  especialidades: () => get<{ especialidades: string[] }>("/api/agente/relatorios/especialidades"),
  urlDownloadRelatorio: (id: string) => `${API_URL}/api/agente/relatorios/${encodeURIComponent(id)}/download`,
};
