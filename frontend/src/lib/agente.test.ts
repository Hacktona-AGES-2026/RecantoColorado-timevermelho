import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { API_URL } from "./api";
import { agenteApi, conversar, ErroAgente, lerEvento, type EventoChat } from "./agente";

const codificador = new TextEncoder();

/** Resposta em fluxo: cada pedaço do texto chega separado, como numa conexão de verdade. */
function fluxo(pedacos: string[], status = 200): Response {
  const stream = new ReadableStream<Uint8Array>({
    start(controle) {
      for (const p of pedacos) controle.enqueue(codificador.encode(p));
      controle.close();
    },
  });
  return new Response(stream, { status, headers: { "Content-Type": "text/event-stream" } });
}

function json(corpo: unknown, status = 200): Response {
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

async function coletar(pedacos: string[]): Promise<EventoChat[]> {
  fetchFalso.mockResolvedValue(fluxo(pedacos));
  const eventos: EventoChat[] = [];
  await conversar({ mensagem: "oi" }, (e) => eventos.push(e));
  return eventos;
}

describe("conversar: leitura do fluxo de eventos", () => {
  it("entrega os eventos na ordem", async () => {
    const eventos = await coletar([
      'event:sessao\ndata:{"sessaoId":"abc"}\n\n',
      'event:trecho\ndata:{"texto":"Olá"}\n\n',
      'event:trecho\ndata:{"texto":", tudo bem?"}\n\n',
      'event:fim\ndata:{"versaoPrompt":"v1"}\n\n',
    ]);
    expect(eventos).toEqual([
      { nome: "sessao", sessaoId: "abc" },
      { nome: "trecho", texto: "Olá" },
      { nome: "trecho", texto: ", tudo bem?" },
      { nome: "fim", versaoPrompt: "v1" },
    ]);
  });

  it("junta um evento que chegou cortado no meio, inclusive no meio de um caractere acentuado", async () => {
    const bytes = codificador.encode('event:trecho\ndata:{"texto":"ação"}\n\n');
    const meio = bytes.indexOf(0xc3) + 1; // entre os dois bytes do "ç"
    fetchFalso.mockResolvedValue(
      new Response(
        new ReadableStream<Uint8Array>({
          start(c) {
            c.enqueue(bytes.slice(0, meio));
            c.enqueue(bytes.slice(meio));
            c.close();
          },
        }),
      ),
    );
    const eventos: EventoChat[] = [];
    await conversar({ mensagem: "oi" }, (e) => eventos.push(e));
    expect(eventos).toEqual([{ nome: "trecho", texto: "ação" }]);
  });

  it("aceita quebra de linha CRLF e vários eventos no mesmo pedaço", async () => {
    const eventos = await coletar([
      'event:trecho\r\ndata:{"texto":"a"}\r\n\r\nevent:trecho\r\ndata:{"texto":"b"}\r\n\r\n',
    ]);
    expect(eventos.map((e) => (e.nome === "trecho" ? e.texto : ""))).toEqual(["a", "b"]);
  });

  it("entende recusa, substituir e erro", async () => {
    const eventos = await coletar([
      'event:recusa\ndata:{"motivo":"FORA_DO_ESCOPO","texto":"Só posso ajudar com saúde."}\n\n',
      'event:substituir\ndata:{"texto":"Troque tudo."}\n\n',
      'event:erro\ndata:{"texto":"Indisponível."}\n\n',
    ]);
    expect(eventos).toEqual([
      { nome: "recusa", motivo: "FORA_DO_ESCOPO", texto: "Só posso ajudar com saúde." },
      { nome: "substituir", texto: "Troque tudo." },
      { nome: "erro", texto: "Indisponível." },
    ]);
  });

  it("ignora evento desconhecido, JSON quebrado, comentário e evento incompleto no final", async () => {
    const eventos = await coletar([
      ': comentário de keep-alive\n\n',
      'event:coisa_nova\ndata:{"x":1}\n\n',
      "event:trecho\ndata:{isso nao e json}\n\n",
      'event:trecho\ndata:{"texto":"ok"}\n\n',
      'event:trecho\ndata:{"texto":"incompleto"}', // sem a linha em branco: o servidor caiu no meio
    ]);
    expect(eventos).toEqual([{ nome: "trecho", texto: "ok" }]);
  });

  it("campo ausente ou de tipo errado vira texto vazio, nunca quebra", () => {
    expect(lerEvento("trecho", '{"texto":123}')).toEqual({ nome: "trecho", texto: "" });
    expect(lerEvento("sessao", "{}")).toEqual({ nome: "sessao", sessaoId: "" });
    expect(lerEvento("trecho", "null")).toBeNull();
  });
});

describe("conversar: pedido e erros", () => {
  it("manda POST em JSON para /api/agente/chat e omite sessaoId quando não há", async () => {
    fetchFalso.mockResolvedValue(fluxo([]));
    const controle = new AbortController();
    await conversar({ mensagem: "como uso a fila?" }, () => undefined, controle.signal);

    const [url, opcoes] = fetchFalso.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_URL}/api/agente/chat`);
    expect(opcoes.method).toBe("POST");
    expect(opcoes.headers).toMatchObject({ "Content-Type": "application/json", Accept: "text/event-stream" });
    expect(JSON.parse(opcoes.body as string)).toEqual({ mensagem: "como uso a fila?" });
    expect(opcoes.signal).toBe(controle.signal);
  });

  it("manda o sessaoId quando já existe", async () => {
    fetchFalso.mockResolvedValue(fluxo([]));
    await conversar({ sessaoId: "S1", mensagem: "e agora?" }, () => undefined);
    expect(JSON.parse((fetchFalso.mock.calls[0][1] as RequestInit).body as string)).toEqual({ sessaoId: "S1", mensagem: "e agora?" });
  });

  it.each([
    [400, "A mensagem não pode ser vazia."],
    [429, "Muitas mensagens em pouco tempo. Aguarde um minuto e tente de novo."],
    [503, "O assistente está indisponível no momento."],
  ])("HTTP %i vira ErroAgente com a mensagem do servidor", async (status, mensagem) => {
    fetchFalso.mockResolvedValue(json({ mensagem }, status));
    const erro = await conversar({ mensagem: "oi" }, () => undefined).catch((e) => e);
    expect(erro).toBeInstanceOf(ErroAgente);
    expect(erro.message).toBe(mensagem);
    expect(erro.status).toBe(status);
  });

  it("corpo sem JSON no erro vira 'Erro <status>'", async () => {
    fetchFalso.mockResolvedValue(new Response("<html>oops</html>", { status: 502 }));
    await expect(conversar({ mensagem: "oi" }, () => undefined)).rejects.toThrow("Erro 502");
  });

  it("falha de rede e cancelamento sobem sem ser engolidos", async () => {
    fetchFalso.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(conversar({ mensagem: "oi" }, () => undefined)).rejects.toThrow("Failed to fetch");

    fetchFalso.mockRejectedValue(new DOMException("cancelado", "AbortError"));
    await expect(conversar({ mensagem: "oi" }, () => undefined)).rejects.toMatchObject({ name: "AbortError" });
  });
});

describe("agenteApi", () => {
  it("status e relatórios usam as rotas certas, sem cache", async () => {
    fetchFalso.mockImplementation(() => Promise.resolve(json({})));
    await agenteApi.status();
    await agenteApi.relatorios();
    await agenteApi.especialidades();
    await agenteApi.relatorio("a b/c");
    expect(fetchFalso.mock.calls.map((c) => c[0])).toEqual([
      `${API_URL}/api/agente/status`,
      `${API_URL}/api/agente/relatorios`,
      `${API_URL}/api/agente/relatorios/especialidades`,
      `${API_URL}/api/agente/relatorios/a%20b%2Fc`,
    ]);
    expect(fetchFalso.mock.calls[0][1]).toEqual({ cache: "no-store" });
  });

  it("gerar relatório e categorizar mandam JSON", async () => {
    fetchFalso.mockImplementation(() => Promise.resolve(json({})));
    await agenteApi.gerarRelatorio({ tipo: "ESPECIALIDADE", especialidade: "Urologia" });
    await agenteApi.categorizar("texto", "Dra. Ana");
    await agenteApi.revisarCategorizacao("id/1", { acao: "ACEITAR" });
    const [[u1, o1], [u2, o2], [u3, o3]] = fetchFalso.mock.calls as [string, RequestInit][];
    expect(u1).toBe(`${API_URL}/api/agente/relatorios`);
    expect(JSON.parse(o1.body as string)).toEqual({ tipo: "ESPECIALIDADE", especialidade: "Urologia" });
    expect(u2).toBe(`${API_URL}/api/agente/categorizar`);
    expect(JSON.parse(o2.body as string)).toEqual({ texto: "texto", solicitante: "Dra. Ana" });
    expect(u3).toBe(`${API_URL}/api/agente/categorizacoes/id%2F1/revisao`);
    expect(JSON.parse(o3.body as string)).toEqual({ acao: "ACEITAR" });
  });

  it("lista de categorizações aceita filtro de status", async () => {
    fetchFalso.mockImplementation(() => Promise.resolve(json([])));
    await agenteApi.categorizacoes();
    await agenteApi.categorizacoes("PENDENTE");
    expect(fetchFalso.mock.calls.map((c) => c[0])).toEqual([
      `${API_URL}/api/agente/categorizacoes`,
      `${API_URL}/api/agente/categorizacoes?status=PENDENTE`,
    ]);
  });

  it("erros da API viram ErroAgente com a mensagem", async () => {
    fetchFalso.mockResolvedValue(json({ mensagem: "Especialidade desconhecida." }, 422));
    const erro = await agenteApi.gerarRelatorio({ tipo: "ESPECIALIDADE", especialidade: "x" }).catch((e) => e);
    expect(erro).toBeInstanceOf(ErroAgente);
    expect(erro.status).toBe(422);
    expect(erro.message).toBe("Especialidade desconhecida.");
  });

  it("o link de download aponta para o backend e protege o id", () => {
    expect(agenteApi.urlDownloadRelatorio("abc-123")).toBe(`${API_URL}/api/agente/relatorios/abc-123/download`);
    expect(agenteApi.urlDownloadRelatorio("../x")).toBe(`${API_URL}/api/agente/relatorios/..%2Fx/download`);
  });
});
