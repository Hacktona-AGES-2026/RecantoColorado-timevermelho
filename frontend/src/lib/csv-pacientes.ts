// CSV da tabela de pacientes que o assistente mostra. Os dados vêm do backend (dataset), não da IA.

import type { LinhaPaciente } from "@/lib/agente";

const COLUNAS: [string, (l: LinhaPaciente) => string | number | null][] = [
  ["posicao", (l) => l.posicao],
  ["solicitacao_id", (l) => l.solicitacaoId],
  ["nome_paciente", (l) => l.nome],
  ["idade", (l) => l.idade],
  ["sexo", (l) => l.sexo],
  ["municipio", (l) => l.municipio],
  ["procedimento", (l) => l.procedimento],
  ["especialidade", (l) => l.especialidade],
  ["cid", (l) => l.cid],
  ["cid_descricao", (l) => l.cidDescricao],
  ["oncologico", (l) => (l.oncologico ? "sim" : "nao")],
  ["classe", (l) => l.classe],
  ["score", (l) => l.score],
  ["dias_em_fila", (l) => l.diasEmFila],
  ["prazo_dias", (l) => l.prazoDias],
  ["status", (l) => l.status],
];

/** Uma célula: aspas escapadas e, contra fórmulas no Excel, "=", "+", "-" e "@" no começo viram texto. */
function celula(valor: string | number | null): string {
  if (valor === null || valor === undefined) return "";
  let t = String(valor);
  if (/^[=+\-@\t\r]/.test(t)) t = `'${t}`;
  return /[",;\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
}

export function csvPacientes(linhas: LinhaPaciente[]): string {
  const cabecalho = COLUNAS.map(([nome]) => nome).join(",");
  const corpo = linhas.map((l) => COLUNAS.map(([, valor]) => celula(valor(l))).join(","));
  return [cabecalho, ...corpo].join("\n") + "\n";
}

/** Baixa o CSV no navegador (com BOM, para o Excel abrir os acentos certos). */
export function baixarCsvPacientes(linhas: LinhaPaciente[], nomeArquivo = "pacientes.csv") {
  const blob = new Blob(["﻿" + csvPacientes(linhas)], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", nomeArquivo);
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
