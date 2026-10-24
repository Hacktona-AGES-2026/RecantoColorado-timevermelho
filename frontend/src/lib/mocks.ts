import type { ItemFila, Pagina } from "./api";

// Dados de exemplo no formato do contrato, para as telas andarem antes do backend.
export const filaMock: Pagina<ItemFila> = {
  pagina: 0,
  tamanho: 50,
  total: 1,
  itens: [
    {
      solicitacaoId: "SOL-2026-00351",
      pacienteId: "PAC-00000",
      nome: "Paciente exemplo",
      idade: 61,
      procedimento: "Exemplo",
      especialidade: "Cirurgia Oncologica",
      cid: "D44.0",
      oncologico: true,
      posicao: 3,
      posicaoManual: 512,
      delta: 509,
      classe: "A2",
      prazoDias: 21,
      diasEmFila: 180,
      percentualPrazo: 857,
      score: 88,
      prioridadeManual: "baixa",
      motivos: [
        { texto: "Biópsia positiva em 13/09/2026", pontos: 30, evidencia: "LAUDO AP: NEOPLASIA MALIGNA" },
        { texto: "CID benigno no cadastro diverge do laudo", pontos: 0 },
        { texto: "180 dias de fila (857% do prazo de 21 dias)", pontos: 15 },
      ],
    },
  ],
};
