// Cliente da API do FilaVIVA. Os tipos espelham os DTOs Java em backend/.../api/dto.
// Contrato completo também na seção 3 do PLANO.md.

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type ClasseSwalis = "A1" | "A2" | "B" | "C" | "D";
export type StatusFila = "ATIVA" | "SAIDA_SUGERIDA" | "SAIDA_CONFIRMADA";
export type TipoAjuste =
  | "CONFIRMAR_CLASSE"
  | "ALTERAR_CLASSE"
  | "CONFIRMAR_SAIDA"
  | "RECUSAR_SAIDA"
  | "SUGERIR_REBAIXAMENTO"
  | "CONFIRMAR_REBAIXAMENTO"
  | "REJEITAR_REBAIXAMENTO"
  | "REBAIXAMENTO_BLOQUEADO"; // este último só o sistema registra

/** Situação de rebaixamento de um paciente na fila operacional. */
export type EstadoRebaixamento = "PENDENTE" | "CONFIRMADO" | "REJEITADO" | "BLOQUEADO";

export const CLASSES: ClasseSwalis[] = ["A1", "A2", "B", "C", "D"];

export interface Motivo {
  texto: string; // ex.: "Biópsia positiva em 13/09/2026"
  evidencia: string | null; // trecho do laudo / texto do médico
  fonte: string | null; // coluna ou "evento EVT-000123"
}

export interface ItemFila {
  solicitacaoId: string;
  pacienteId: string;
  nome: string;
  idade: number | null;
  sexo: string | null;
  municipio: string | null;
  procedimento: string;
  especialidade: string;
  cid: string; // formatado, ex.: "D44.0"
  cidDescricao: string | null;
  oncologico: boolean; // CID C* ou biópsia positiva
  posicao: number | null; // null quando está fora da fila ativa
  posicaoManual: number | null; // posição na ordem de hoje (prioridade manual + data de entrada)
  delta: number | null; // posicaoManual - posicao (positivo = subiu)
  classe: ClasseSwalis;
  classeCalculada: ClasseSwalis;
  ajustada: boolean; // classe definida pelo regulador
  prazoDias: number;
  diasEmFila: number;
  percentualPrazo: number; // diasEmFila / prazoDias * 100
  score: number; // 0–100
  prioridadeManual: string | null; // alta | media | baixa | null
  status: StatusFila;
  movidoPelaRegraDeOuro: boolean;
  reavaliacoes: number; // quantos motivos de reavaliação
  motivos: Motivo[]; // até 3 sinais que decidiram a classe
  justificativa: string; // frase completa (a mesma do fila_priorizada.csv)
  rebaixamento: EstadoRebaixamento | null; // null = nenhum rebaixamento registrado
}

export interface Pagina<T> {
  itens: T[];
  total: number;
  pagina: number;
  tamanho: number;
  porClasse: Record<ClasseSwalis, number>; // contagem da fila ativa inteira
  especialidades: string[];
  snapshot?: SnapshotInfo; // de qual fila são estes dados (sempre a operacional nesta rota)
}

/** Identidade de uma fila: a operacional (tela) ou a de referência (entrega oficial). */
export interface SnapshotInfo {
  tipo: "operacional" | "oficial" | "simulacao";
  versao: string; // "op-12" ou "ref-3e37e2b31749"
  calculadoEm: string; // ISO
  eventosRegistrados: number;
  decisoesRegulador: number;
  descricao: string;
}

/** A fila mudou desde que a tela foi carregada (HTTP 409 no download operacional). */
export class FilaMudouErro extends Error {}

export interface SinalDto {
  tipo: string;
  rotulo: string;
  categoria: "DIAGNOSTICO" | "PIORA" | "SINTOMA" | "SAIDA" | "QUALIDADE_DO_DADO";
  descricao: string;
  evidencia: string | null;
  fonte: string | null;
  data: string | null; // AAAA-MM-DD
}

export interface Componente {
  chave: string; // diagnostico | piora | dor | limitacao | espera | risco
  nome: string;
  pontos: number;
  maximo: number;
  explicacao: string;
}

export interface ClassificacaoDto {
  classe: ClasseSwalis;
  classeCalculada: ClasseSwalis;
  descricaoClasse: string;
  prazoDias: number;
  regra: string;
  ajustada: boolean;
  justificativaAjuste: string | null;
  determinantes: SinalDto[];
}

export interface EventoDto {
  id: string;
  data: string;
  tipo: string;
  resultado: string | null;
  valor: number | null;
  descricao: string | null;
  origem: string | null;
  sintetico: boolean; // criado na demo
  solicitacaoOriginal: string;
}

export interface AjusteDto {
  id: number;
  tipo: TipoAjuste;
  classeAnterior: ClasseSwalis | null;
  classeNova: ClasseSwalis | null;
  justificativa: string;
  autor: string;
  criadoEm: string; // ISO
}

export interface ReavaliacaoDto {
  tipo: string;
  titulo: string;
  mensagem: string;
}

export interface DadosClinicos {
  sexo: string | null;
  idade: number | null;
  municipio: string | null;
  uf: string | null;
  distanciaKm: number | null;
  procedimentoId: string;
  procedimento: string;
  especialidade: string;
  porte: string | null;
  riscoCirurgico: number | null;
  cid: string;
  cidDescricao: string | null;
  cidSecundario: string | null;
  asa: number | null;
  comorbidades: string | null;
  dor: number | null;
  limitacao: string | null;
  justificativa: string | null;
  observacoes: string | null;
  dataEntrada: string;
  prioridadeManual: string | null;
  dataUltimaRevisao: string | null;
  flagOncologico: string | null;
  flagDiabetico: string | null;
  flagPiora: string | null;
}

export interface PacienteDetalhe {
  item: ItemFila;
  dados: DadosClinicos;
  classificacao: ClassificacaoDto;
  componentes: Componente[];
  sinais: SinalDto[];
  eventos: EventoDto[]; // ordem cronológica
  ajustes: AjusteDto[]; // mais recente primeiro
  reavaliacoes: ReavaliacaoDto[];
  duplicatas: string[]; // solicitações mescladas nesta
  sinalSaida: SinalDto | null;
  rebaixamento: Rebaixamento | null; // último rebaixamento sugerido/decidido/bloqueado
}

export interface NovoEvento {
  solicitacaoId: string;
  tipo: string; // biopsia | pronto_socorro | internacao | exame_imagem | consulta_reavaliacao | contato_telefonico | hba1c | falta
  resultado?: string;
  valorNumerico?: number;
  descricao?: string;
  data?: string;
}

export interface Impacto {
  solicitacaoId: string;
  nome: string;
  posicaoAntes: number | null;
  posicaoDepois: number | null;
  classeAntes: ClasseSwalis;
  classeDepois: ClasseSwalis;
  statusAntes: StatusFila;
  statusDepois: StatusFila;
  mensagem: string;
}

export interface EventoHipotetico {
  tipo: string;
  resultado?: string;
  valorNumerico?: number;
  descricao?: string;
}

export interface SimulacaoRequest {
  solicitacaoId: string;
  dor?: number | null;
  limitacao?: string | null;
  eventos?: EventoHipotetico[];
  textoAdicional?: string; // frase extra, como se o médico tivesse escrito
}

export interface Simulacao {
  antes: ItemFila;
  depois: ItemFila;
  sinaisNovos: SinalDto[];
  componentesDepois: Componente[];
  mensagem: string;
}

export interface Pesos {
  diagnostico: number;
  piora: number;
  dor: number;
  limitacao: number;
  espera: number;
  risco: number;
}

export const PESOS_PADRAO: Pesos = { diagnostico: 30, piora: 25, dor: 15, limitacao: 10, espera: 15, risco: 5 };

export interface Movimento {
  solicitacaoId: string;
  nome: string;
  classe: ClasseSwalis;
  posicaoAntes: number;
  posicaoDepois: number;
  delta: number; // positivo = subiu
}

export interface SimulacaoPesos {
  pesos: Pesos;
  maioresSubidas: Movimento[];
  maioresQuedas: Movimento[];
  topo: ItemFila[]; // 20 primeiros com os pesos novos
  regraDeOuroRespeitada: boolean;
  pacientesQueMudaram: number;
}

export interface AjusteRequest {
  solicitacaoId: string;
  tipo: TipoAjuste;
  classeNova?: ClasseSwalis; // obrigatório em ALTERAR_CLASSE
  justificativa: string; // mínimo 10 caracteres
  autor?: string;
}

export interface Alerta {
  id: number;
  solicitacaoId: string;
  nome: string;
  tipo: "EVENTO_NOVO" | "MUDANCA_CLASSE" | "AJUSTE_REGULADOR";
  mensagem: string;
  classeAntes: ClasseSwalis | null;
  classeDepois: ClasseSwalis | null;
  posicaoAntes: number | null;
  posicaoDepois: number | null;
  criadoEm: string;
}

export interface ItemReavaliacao {
  solicitacaoId: string;
  nome: string;
  classe: ClasseSwalis;
  posicao: number | null;
  status: StatusFila;
  mensagem: string;
}

export interface GrupoReavaliacao {
  tipo: string;
  titulo: string;
  total: number;
  itens: ItemReavaliacao[]; // até 50, mais urgentes primeiro
}

export interface Saida {
  solicitacaoId: string;
  nome: string;
  cid: string;
  oncologico: boolean;
  status: StatusFila;
  motivo: string;
  evidencia: string | null;
  fonte: string | null;
}

/** Rebaixamento sugerido (manual ou pelo recálculo), com a decisão do regulador quando houver. */
export interface Rebaixamento {
  solicitacaoId: string;
  nome: string | null;
  oncologico: boolean;
  posicao: number | null;
  estado: EstadoRebaixamento;
  classeVigente: ClasseSwalis; // a que valia quando o rebaixamento foi sugerido
  classeProposta: ClasseSwalis;
  classeAtual: ClasseSwalis | null; // a que vale agora
  fatores: string; // por que rebaixar (justificativa de quem sugeriu ou do recálculo)
  evidencias: string | null; // regra e sinais da classe calculada
  automatico: boolean;
  sugeridoPor: string;
  sugeridoEm: string;
  decisao: string | null;
  decididoPor: string | null;
  decididoEm: string | null;
}

export interface Alertas {
  recentes: Alerta[]; // mais recente primeiro
  reavaliacao: GrupoReavaliacao[];
  saidas: Saida[];
  rebaixamentos: Rebaixamento[]; // pendentes primeiro (ordem da fila), depois os últimos decididos/bloqueados
}

export interface Comparacao {
  indicador: string;
  antes: string;
  depois: string;
  descricao: string;
}

export interface Faixa {
  faixa: string; // "1º décimo", ...
  oncoAntes: number;
  oncoDepois: number;
  semUrgenciaAntes: number;
  semUrgenciaDepois: number;
}

export interface LinhaMatriz {
  prioridadeManual: string; // alta | media | baixa | sem prioridade
  porClasse: Record<ClasseSwalis, number>;
}

export interface GrupoPosicao {
  grupo: string;
  pacientes: number;
  posicaoMediaAntes: number;
  posicaoMediaDepois: number;
}

export interface QualidadeDado {
  item: string;
  quantidade: number;
}

export interface Indicadores {
  totalAtivos: number;
  totalForaDaFila: number;
  porClasse: Record<ClasseSwalis, number>;
  comparacoes: Comparacao[];
  faixas: Faixa[];
  matriz: LinhaMatriz[];
  porRegiao: GrupoPosicao[];
  qualidadeDados: QualidadeDado[];
}

export interface Status {
  app: string;
  dataReferencia: string;
  datasetEncontrado: boolean;
}

export interface FiltrosFila {
  classe?: ClasseSwalis;
  especialidade?: string;
  busca?: string; // nome, solicitação, CID ou procedimento
  status?: StatusFila | "TODOS"; // padrão ATIVA
  oncologico?: boolean;
  pagina?: number; // começa em 0
  tamanho?: number; // padrão 50
}

async function lerErro(resp: Response): Promise<string> {
  try {
    const corpo = await resp.json();
    return corpo.mensagem ?? corpo.detail ?? corpo.message ?? `Erro ${resp.status}`;
  } catch {
    return `Erro ${resp.status}`;
  }
}

async function get<T>(rota: string): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, { cache: "no-store" });
  if (!resp.ok) throw new Error(await lerErro(resp));
  return resp.json() as Promise<T>;
}

async function post<T>(rota: string, corpo?: unknown): Promise<T> {
  const resp = await fetch(`${API_URL}${rota}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });
  if (!resp.ok) throw new Error(await lerErro(resp));
  return resp.json() as Promise<T>;
}

async function del(rota: string): Promise<void> {
  const resp = await fetch(`${API_URL}${rota}`, { method: "DELETE" });
  if (!resp.ok) throw new Error(await lerErro(resp));
}

// ---------- médicos (backend: medico/MedicoDtos.java) ----------

export interface Especialidade {
  codigo: string; // ex.: "CARDIOLOGIA"
  nome: string;
  descricao: string;
  pacientesCirurgicos: number; // fila operacional: procedimento desta especialidade (atribuídos)
  pacientesPossivelAvaliacao: number; // só CID/comorbidade relacionados (não atribuídos)
}

export interface Medico {
  id: number;
  nome: string;
  crm: string;
  especialidade: string; // código
  especialidadeNome: string;
  criadoEm: string;
}

export interface NovoMedico {
  nome: string;
  crm: string;
  especialidade: string;
}

/** CIRURGICO: procedimento da especialidade (atribuído ao médico). POSSIVEL_AVALIACAO: só CID/comorbidade relacionados. */
export type GrupoMedico = "CIRURGICO" | "POSSIVEL_AVALIACAO";

/** Paciente numa lista do médico. posicaoNaLista: 1 = mais grave do grupo; item é o da fila geral, sem alteração. */
export interface ItemMedico {
  posicaoNaLista: number;
  grupo: GrupoMedico;
  motivosEspecialidade: string[]; // por que está na lista desta especialidade
  item: ItemFila;
}

export interface FilaMedico {
  medico: Medico;
  especialidade: Especialidade;
  grupo: GrupoMedico;
  itens: ItemMedico[];
  total: number; // depois da busca/filtro
  pagina: number;
  tamanho: number;
  totalFilaGeral: number;
  porClasse: Record<ClasseSwalis, number>; // grupo pedido inteiro
  snapshot: SnapshotInfo;
}

export interface FiltrosFilaMedico {
  grupo?: GrupoMedico;
  busca?: string;
  classe?: ClasseSwalis | "";
  pagina?: number;
  tamanho?: number;
}

function query(filtros: FiltrosFila | FiltrosFilaMedico): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(filtros)) {
    if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  }
  return p.toString();
}

/**
 * Baixa um CSV conferindo, antes de salvar, que a API devolveu a fila pedida (cabeçalho X-FilaViva-Snapshot).
 * Assim o botão da banca nunca salva a fila operacional por engano. Devolve o nome do arquivo salvo.
 */
async function baixarCsv(url: string, tipoEsperado: "oficial" | "operacional"): Promise<{ arquivo: string; snapshot: string }> {
  const resp = await fetch(url, { cache: "no-store" });
  if (resp.status === 409) throw new FilaMudouErro(await lerErro(resp));
  if (!resp.ok) throw new Error(await lerErro(resp));
  const snapshot = resp.headers.get("X-FilaViva-Snapshot") ?? "";
  if (!snapshot.startsWith(tipoEsperado)) {
    throw new Error(`Download cancelado: a API devolveu "${snapshot || "fila sem identificação"}" em vez da fila ${tipoEsperado}.`);
  }
  const nome = /filename="([^"]+)"/.exec(resp.headers.get("Content-Disposition") ?? "")?.[1];
  if (!nome || (tipoEsperado === "oficial" && nome !== "fila_priorizada.csv")) {
    throw new Error(`Download cancelado: nome de arquivo inesperado (${nome ?? "ausente"}).`);
  }
  const blob = await resp.blob();
  const href = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = href;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 2000);
  return { arquivo: nome, snapshot };
}

export const api = {
  status: () => get<Status>("/api/status"),
  fila: (filtros: FiltrosFila = {}) => get<Pagina<ItemFila>>(`/api/fila?${query(filtros)}`),
  paciente: (solicitacaoId: string) => get<PacienteDetalhe>(`/api/pacientes/${solicitacaoId}`),
  registrarEvento: (evento: NovoEvento) => post<Impacto>("/api/eventos", evento),
  simular: (simulacao: SimulacaoRequest) => post<Simulacao>("/api/simulacao", simulacao),
  simularPesos: (pesos: Pesos) => post<SimulacaoPesos>("/api/simulacao/pesos", pesos),
  ajustar: (ajuste: AjusteRequest) => post<Impacto>("/api/ajustes", ajuste),
  alertas: () => get<Alertas>("/api/alertas"),
  indicadores: () => get<Indicadores>("/api/indicadores"),
  cenarioDemo: () => post<Impacto[]>("/api/demo/cenario"),
  resetDemo: () => post<{ ok: boolean }>("/api/demo/reset"),
  especialidades: () => get<Especialidade[]>("/api/especialidades"),
  medicos: () => get<Medico[]>("/api/medicos"),
  medico: (id: number | string) => get<Medico>(`/api/medicos/${id}`),
  cadastrarMedico: (novo: NovoMedico) => post<Medico>("/api/medicos", novo),
  removerMedico: (id: number) => del(`/api/medicos/${id}`),
  filaMedico: (id: number | string, filtros: FiltrosFilaMedico = {}) =>
    get<FilaMedico>(`/api/medicos/${id}/fila?${query(filtros)}`),
  /** Fila de referência — entrega oficial (fila_priorizada.csv). Sempre explícito: nunca a operacional. */
  exportCsvUrl: `${API_URL}/api/fila/export.csv?snapshot=oficial`,
  exportarCsvOficial: () => baixarCsv(`${API_URL}/api/fila/export.csv?snapshot=oficial`, "oficial"),
  /** A fila da tela. Com a versão, a API recusa (409) se a fila mudou depois que a tela foi carregada. */
  exportarCsvOperacional: (versao?: string) =>
    baixarCsv(
      `${API_URL}/api/fila/export.csv?snapshot=operacional${versao ? `&versao=${encodeURIComponent(versao)}` : ""}`,
      "operacional",
    ),
};

/** Cores e textos das classes, para todas as telas usarem o mesmo padrão. */
export const INFO_CLASSE: Record<ClasseSwalis, { nome: string; prazo: number; cor: string }> = {
  A1: { nome: "A1 · até 15 dias", prazo: 15, cor: "bg-red-600 text-white" },
  A2: { nome: "A2 · até 21 dias", prazo: 21, cor: "bg-orange-500 text-white" },
  B: { nome: "B · até 60 dias", prazo: 60, cor: "bg-amber-400 text-black" },
  C: { nome: "C · até 180 dias", prazo: 180, cor: "bg-sky-500 text-white" },
  D: { nome: "D · até 360 dias", prazo: 360, cor: "bg-slate-400 text-white" },
};

export const TIPOS_EVENTO: { tipo: string; rotulo: string; resultados: string[] }[] = [
  { tipo: "biopsia", rotulo: "Biópsia", resultados: ["positivo", "negativo", "inconclusivo"] },
  { tipo: "pronto_socorro", rotulo: "Pronto-socorro", resultados: ["atendido_liberado", "observacao"] },
  { tipo: "internacao", rotulo: "Internação (dias no valor)", resultados: ["alta"] },
  { tipo: "exame_imagem", rotulo: "Exame de imagem", resultados: ["estavel", "progressao"] },
  { tipo: "consulta_reavaliacao", rotulo: "Reavaliação ambulatorial", resultados: ["piora", "estavel", "melhora"] },
  {
    tipo: "contato_telefonico",
    rotulo: "Contato telefônico",
    resultados: ["atendeu", "nao_atendeu", "relata_piora", "relata_melhora", "informou_cirurgia_outro_servico", "desistiu"],
  },
  { tipo: "hba1c", rotulo: "HbA1c (% no valor)", resultados: ["resultado"] },
  { tipo: "falta", rotulo: "Falta", resultados: ["consulta", "exame", "avaliacao_pre_anestesica"] },
];
