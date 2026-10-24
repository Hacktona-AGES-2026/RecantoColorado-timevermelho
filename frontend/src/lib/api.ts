// Cliente da API do FilaVIVA. Tipos seguem o contrato da seção 3 do PLANO.md.
// Enquanto o backend não tiver a rota, use os dados de src/lib/mocks.ts.

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type ClasseSwalis = "A1" | "A2" | "B" | "C" | "D";

export interface Motivo {
  texto: string; // ex.: "Biópsia positiva em 13/09/2026"
  pontos: number; // contribuição no score
  evidencia?: string; // trecho do texto, id do evento ou nome da coluna
}

export interface ItemFila {
  solicitacaoId: string;
  pacienteId: string;
  nome: string;
  idade: number;
  procedimento: string;
  especialidade: string;
  cid: string;
  oncologico: boolean;
  posicao: number;
  posicaoManual: number; // posição na ordem atual (prioridade manual + data)
  delta: number; // posicaoManual - posicao (positivo = subiu)
  classe: ClasseSwalis;
  prazoDias: number;
  diasEmFila: number;
  percentualPrazo: number; // diasEmFila / prazoDias * 100
  score: number; // 0–100
  prioridadeManual: string | null;
  motivos: Motivo[]; // os 3 principais
}

export interface Pagina<T> {
  itens: T[];
  total: number;
  pagina: number;
  tamanho: number;
}

export interface Status {
  app: string;
  dataReferencia: string;
  datasetEncontrado: boolean;
}

async function get<T>(rota: string): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, { cache: "no-store" });
  if (!resp.ok) throw new Error(`GET ${rota} falhou: ${resp.status}`);
  return resp.json() as Promise<T>;
}

async function post<T>(rota: string, corpo: unknown): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  if (!resp.ok) throw new Error(`POST ${rota} falhou: ${resp.status}`);
  return resp.json() as Promise<T>;
}

export const api = {
  status: () => get<Status>("/api/status"),
  fila: (params: Record<string, string> = {}) =>
    get<Pagina<ItemFila>>(`/api/fila?${new URLSearchParams(params)}`),
  paciente: (solicitacaoId: string) => get<unknown>(`/api/pacientes/${solicitacaoId}`),
  registrarEvento: (evento: unknown) => post<unknown>("/api/eventos", evento),
  simular: (simulacao: unknown) => post<unknown>("/api/simulacao", simulacao),
  ajustar: (ajuste: unknown) => post<unknown>("/api/ajustes", ajuste),
  alertas: () => get<unknown>("/api/alertas"),
  indicadores: () => get<unknown>("/api/indicadores"),
  exportCsvUrl: `${API_URL}/api/fila/export.csv`,
};
