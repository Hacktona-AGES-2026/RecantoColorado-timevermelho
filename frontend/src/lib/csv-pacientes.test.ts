import { describe, expect, it } from "vitest";
import type { LinhaPaciente } from "@/lib/agente";
import { csvPacientes } from "./csv-pacientes";

const LINHA: LinhaPaciente = {
  posicao: 1, solicitacaoId: "SOL-2026-00489", nome: "Eliane Lopes Ramos", idade: 50, sexo: "F", municipio: "Porto Alegre",
  procedimento: "Histerectomia", especialidade: "Ginecologia", cid: "C54.1", cidDescricao: "Neoplasia, maligna", oncologico: true,
  classe: "A1", score: 86.5, diasEmFila: 138, prazoDias: 15, status: "ATIVA",
};

describe("csvPacientes", () => {
  it("tem cabeçalho e uma linha por paciente, com vírgulas e aspas escapadas", () => {
    const linhas = csvPacientes([LINHA, { ...LINHA, solicitacaoId: "SOL-2", nome: 'Ana "Nina" Souza', idade: null }]).trim().split("\n");
    expect(linhas[0]).toBe("posicao,solicitacao_id,nome_paciente,idade,sexo,municipio,procedimento,especialidade,cid,cid_descricao,oncologico,classe,score,dias_em_fila,prazo_dias,status");
    expect(linhas[1]).toBe('1,SOL-2026-00489,Eliane Lopes Ramos,50,F,Porto Alegre,Histerectomia,Ginecologia,C54.1,"Neoplasia, maligna",sim,A1,86.5,138,15,ATIVA');
    expect(linhas[2]).toContain('"Ana ""Nina"" Souza",,F');
  });

  it("valor que parece fórmula de planilha vira texto", () => {
    expect(csvPacientes([{ ...LINHA, nome: "=HYPERLINK(1)" }])).toContain(",'=HYPERLINK(1),");
  });
});
