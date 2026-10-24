import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { API_URL, api } from "./api";

function resposta(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } });
}

let fetchFalso: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchFalso = vi.fn();
  vi.stubGlobal("fetch", fetchFalso);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api: GET", () => {
  it("monta a query só com os filtros preenchidos", async () => {
    fetchFalso.mockResolvedValue(resposta({ itens: [] }));
    await api.fila({ classe: "A1", busca: "", especialidade: undefined, status: "ATIVA", oncologico: true, pagina: 0 });
    const url = fetchFalso.mock.calls[0][0] as string;
    expect(url.startsWith(`${API_URL}/api/fila?`)).toBe(true);
    const params = new URL(url, "http://localhost").searchParams; // API_URL vazio = URL relativa
    expect(params.get("classe")).toBe("A1");
    expect(params.get("status")).toBe("ATIVA");
    expect(params.get("oncologico")).toBe("true");
    expect(params.get("pagina")).toBe("0");
    expect(params.has("busca")).toBe(false);
    expect(params.has("especialidade")).toBe(false);
  });

  it("não usa cache nas leituras", async () => {
    fetchFalso.mockResolvedValue(resposta({}));
    await api.indicadores();
    expect(fetchFalso).toHaveBeenCalledWith(`${API_URL}/api/indicadores`, { cache: "no-store" });
  });

  it("devolve o JSON da resposta", async () => {
    fetchFalso.mockResolvedValue(resposta({ app: "filaviva", dataReferencia: "2026-10-23", datasetEncontrado: true }));
    await expect(api.status()).resolves.toEqual({ app: "filaviva", dataReferencia: "2026-10-23", datasetEncontrado: true });
  });

  it("monta a rota do paciente", async () => {
    fetchFalso.mockResolvedValue(resposta({}));
    await api.paciente("SOL-2026-00351");
    expect(fetchFalso.mock.calls[0][0]).toBe(`${API_URL}/api/pacientes/SOL-2026-00351`);
  });
});

describe("api: POST", () => {
  it("envia JSON com Content-Type", async () => {
    fetchFalso.mockResolvedValue(resposta({ ok: true }));
    await api.registrarEvento({ solicitacaoId: "S1", tipo: "biopsia", resultado: "positivo" });
    const [url, opcoes] = fetchFalso.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_URL}/api/eventos`);
    expect(opcoes.method).toBe("POST");
    expect(opcoes.headers).toEqual({ "Content-Type": "application/json" });
    expect(JSON.parse(opcoes.body as string)).toEqual({ solicitacaoId: "S1", tipo: "biopsia", resultado: "positivo" });
  });

  it("reset da demo vai sem corpo", async () => {
    fetchFalso.mockResolvedValue(resposta({ ok: true }));
    await api.resetDemo();
    const [url, opcoes] = fetchFalso.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_URL}/api/demo/reset`);
    expect(opcoes.body).toBeUndefined();
  });

  it("simulação vai para /api/simulacao e a de pesos para /api/simulacao/pesos", async () => {
    fetchFalso.mockImplementation(() => Promise.resolve(resposta({})));
    await api.simular({ solicitacaoId: "S1", textoAdicional: "encarceramento" });
    await api.simularPesos({ diagnostico: 1, piora: 1, dor: 1, limitacao: 1, espera: 1, risco: 1 });
    expect(fetchFalso.mock.calls[0][0]).toBe(`${API_URL}/api/simulacao`);
    expect(fetchFalso.mock.calls[1][0]).toBe(`${API_URL}/api/simulacao/pesos`);
  });
});

describe("api: erros", () => {
  it("usa a mensagem que o backend mandou", async () => {
    fetchFalso.mockResolvedValue(resposta({ mensagem: "Regra de ouro: oncológico não pode ficar abaixo da classe A2." }, 422));
    await expect(
      api.ajustar({ solicitacaoId: "S1", tipo: "ALTERAR_CLASSE", classeNova: "D", justificativa: "tentativa de rebaixar" }),
    ).rejects.toThrow("Regra de ouro: oncológico não pode ficar abaixo da classe A2.");
  });

  it("cai para detail e message de outros formatos de erro", async () => {
    fetchFalso.mockResolvedValueOnce(resposta({ detail: "detalhe do erro" }, 400));
    await expect(api.alertas()).rejects.toThrow("detalhe do erro");
    fetchFalso.mockResolvedValueOnce(resposta({ message: "mensagem em inglês" }, 400));
    await expect(api.alertas()).rejects.toThrow("mensagem em inglês");
  });

  it("sem JSON no corpo, diz só o status", async () => {
    fetchFalso.mockResolvedValue(new Response("<html>erro</html>", { status: 500 }));
    await expect(api.fila()).rejects.toThrow("Erro 500");
  });

  it("corpo JSON sem campo conhecido também vira 'Erro <status>'", async () => {
    fetchFalso.mockResolvedValue(resposta({ outra: "coisa" }, 404));
    await expect(api.paciente("X")).rejects.toThrow("Erro 404");
  });

  it("API fora do ar: o erro de rede sobe sem ser engolido", async () => {
    fetchFalso.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(api.status()).rejects.toThrow("Failed to fetch");
    await expect(api.resetDemo()).rejects.toThrow("Failed to fetch");
  });
});

describe("api: export", () => {
  it("aponta para o CSV oficial do backend", () => {
    expect(api.exportCsvUrl).toBe(`${API_URL}/api/fila/export.csv?snapshot=oficial`);
  });
});
