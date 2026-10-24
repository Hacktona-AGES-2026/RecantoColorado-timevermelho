import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { EventoChat, StatusAgente } from "@/lib/agente";

vi.mock("@/lib/agente", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/agente")>();
  return { ...original, agenteApi: { ...original.agenteApi, status: vi.fn(), conversar: vi.fn() } };
});

import { agenteApi, ErroAgente } from "@/lib/agente";
import { Chat, MAX_MENSAGEM } from "./chat";

const statusMock = vi.mocked(agenteApi.status);
const conversarMock = vi.mocked(agenteApi.conversar);

const STATUS_OK: StatusAgente = {
  pronto: true,
  motivoDesligado: null,
  versaoPrompt: "v1",
  moderacaoAtiva: true,
  sessoesAtivas: 0,
  recusasUltimos10Min: 0,
  tokensUltimaHora: 0,
  provedores: [],
};

/** Faz o "servidor" responder com estes eventos. */
function responderCom(...eventos: EventoChat[]) {
  conversarMock.mockImplementation(async (_pedido, aoEvento) => {
    eventos.forEach(aoEvento);
  });
}

async function escrever(texto: string) {
  await userEvent.type(screen.getByLabelText("Sua pergunta"), texto);
}

beforeEach(() => {
  statusMock.mockReset();
  conversarMock.mockReset();
  statusMock.mockResolvedValue(STATUS_OK);
});

describe("Chat: tela inicial e estado do assistente", () => {
  it("mostra a saudação, sugestões e o aviso de que é uma IA", async () => {
    render(<Chat />);
    expect(screen.getByText(/Olá! Posso tirar dúvidas/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Como uso a tela de Alertas?" })).toBeInTheDocument();
    expect(screen.getByText(/Assistente de inteligência artificial: pode errar/)).toBeInTheDocument();
    expect(screen.getByText(/Não envie dados pessoais/)).toBeInTheDocument();
    await waitFor(() => expect(statusMock).toHaveBeenCalled());
  });

  it("avisa quando o assistente está desligado e mostra o motivo", async () => {
    statusMock.mockResolvedValue({ ...STATUS_OK, pronto: false, motivoDesligado: "regras de negócio ainda não preenchidas" });
    render(<Chat />);
    const aviso = await screen.findByRole("status");
    expect(aviso).toHaveTextContent("O assistente ainda não está disponível");
    expect(aviso).toHaveTextContent("regras de negócio ainda não preenchidas");
  });

  it("avisa quando nem o status consegue ser lido", async () => {
    statusMock.mockRejectedValue(new TypeError("Failed to fetch"));
    render(<Chat />);
    expect(await screen.findByText(/Não consegui falar com o servidor para saber/)).toBeInTheDocument();
  });

  it("assistente pronto não mostra aviso nenhum", async () => {
    render(<Chat />);
    await waitFor(() => expect(statusMock).toHaveBeenCalled());
    expect(screen.queryByText(/ainda não está disponível/)).not.toBeInTheDocument();
  });
});

describe("Chat: conversa", () => {
  it("mostra a pergunta e a resposta chegando em pedaços, e limpa o campo", async () => {
    responderCom(
      { nome: "sessao", sessaoId: "S1" },
      { nome: "trecho", texto: "A tela de Alertas " },
      { nome: "trecho", texto: "mostra o que mudou." },
      { nome: "fim", versaoPrompt: "v1" },
    );
    render(<Chat />);

    await escrever("O que a tela de alertas mostra?");
    await userEvent.click(screen.getByRole("button", { name: /Enviar/ }));

    expect(await screen.findByText("A tela de Alertas mostra o que mudou.")).toBeInTheDocument();
    expect(screen.getByText("O que a tela de alertas mostra?")).toBeInTheDocument();
    expect(screen.getByLabelText("Sua pergunta")).toHaveValue("");
    expect(conversarMock).toHaveBeenCalledWith(
      { sessaoId: null, mensagem: "O que a tela de alertas mostra?" },
      expect.any(Function),
      expect.any(AbortSignal),
    );
  });

  it("clicar numa sugestão envia a pergunta", async () => {
    responderCom({ nome: "trecho", texto: "Resposta." }, { nome: "fim", versaoPrompt: "v1" });
    render(<Chat />);
    await userEvent.click(screen.getByRole("button", { name: "O que significa a classe A2?" }));
    expect(conversarMock.mock.calls[0][0].mensagem).toBe("O que significa a classe A2?");
    expect(await screen.findByText("Resposta.")).toBeInTheDocument();
  });

  it("Enter envia; Shift+Enter só quebra a linha", async () => {
    responderCom({ nome: "trecho", texto: "ok" });
    render(<Chat />);
    const campo = screen.getByLabelText("Sua pergunta");

    await userEvent.type(campo, "linha um{Shift>}{Enter}{/Shift}linha dois");
    expect(conversarMock).not.toHaveBeenCalled();
    expect(campo).toHaveValue("linha um\nlinha dois");

    await userEvent.type(campo, "{Enter}");
    expect(conversarMock).toHaveBeenCalledOnce();
    expect(conversarMock.mock.calls[0][0].mensagem).toBe("linha um\nlinha dois");
  });

  it("a segunda mensagem reaproveita a sessão que o servidor devolveu", async () => {
    responderCom({ nome: "sessao", sessaoId: "SESSAO-9" }, { nome: "trecho", texto: "um" }, { nome: "fim", versaoPrompt: "v" });
    render(<Chat />);
    await escrever("primeira{Enter}");
    await screen.findByText("um");
    await escrever("segunda{Enter}");
    await waitFor(() => expect(conversarMock).toHaveBeenCalledTimes(2));
    expect(conversarMock.mock.calls[1][0]).toEqual({ sessaoId: "SESSAO-9", mensagem: "segunda" });
  });

  it("não envia texto vazio nem só com espaços", async () => {
    render(<Chat />);
    const enviar = screen.getByRole("button", { name: /Enviar/ });
    expect(enviar).toBeDisabled();
    await escrever("   ");
    expect(enviar).toBeDisabled();
  });

  it("enquanto responde, mostra 'Digitando…' e não deixa enviar de novo", async () => {
    let terminar!: () => void;
    conversarMock.mockImplementation(
      (_p, aoEvento) =>
        new Promise<void>((resolve) => {
          terminar = () => {
            aoEvento({ nome: "trecho", texto: "Pronto." });
            resolve();
          };
        }),
    );
    render(<Chat />);
    await escrever("pergunta{Enter}");

    expect(await screen.findByText("Digitando…")).toBeInTheDocument();
    await escrever("outra");
    expect(screen.getByRole("button", { name: /Enviar/ })).toBeDisabled();

    await act(async () => terminar());
    expect(await screen.findByText("Pronto.")).toBeInTheDocument();
    expect(screen.queryByText("Digitando…")).not.toBeInTheDocument();
  });
});

describe("Chat: recusas, substituição e erros", () => {
  it("mostra a recusa do assistente como resposta", async () => {
    responderCom({ nome: "recusa", motivo: "FORA_DO_ESCOPO", texto: "Só posso ajudar com saúde e com o FilaVIVA." });
    render(<Chat />);
    await escrever("receita de bolo{Enter}");
    expect(await screen.findByText("Só posso ajudar com saúde e com o FilaVIVA.")).toBeInTheDocument();
  });

  it("emergência aparece em destaque, como alerta", async () => {
    responderCom({ nome: "recusa", motivo: "RISCO_A_SAUDE", texto: "Ligue agora para o SAMU (192)." });
    render(<Chat />);
    await escrever("estou com dor no peito{Enter}");
    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Ligue agora para o SAMU (192).");
  });

  it("'substituir' troca o que já tinha sido mostrado", async () => {
    responderCom(
      { nome: "trecho", texto: "Um começo de resposta que depois foi bloqueada." },
      { nome: "substituir", texto: "Não consegui gerar uma resposta adequada." },
      { nome: "fim", versaoPrompt: "v" },
    );
    render(<Chat />);
    await escrever("pergunta{Enter}");
    expect(await screen.findByText("Não consegui gerar uma resposta adequada.")).toBeInTheDocument();
    expect(screen.queryByText(/começo de resposta/)).not.toBeInTheDocument();
  });

  it("evento de erro mostra a mensagem como alerta", async () => {
    responderCom({ nome: "erro", texto: "O assistente está indisponível no momento." });
    render(<Chat />);
    await escrever("pergunta{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent("O assistente está indisponível no momento.");
  });

  it("erro HTTP (429) mostra a mensagem do servidor", async () => {
    conversarMock.mockRejectedValue(new ErroAgente("Muitas mensagens em pouco tempo.", 429));
    render(<Chat />);
    await escrever("pergunta{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas mensagens em pouco tempo.");
  });

  it("falha de rede mostra um aviso genérico e deixa tentar de novo", async () => {
    conversarMock.mockRejectedValue(new TypeError("Failed to fetch"));
    render(<Chat />);
    await escrever("pergunta{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent("Não consegui falar com o servidor");
    await waitFor(() => expect(screen.getByLabelText("Sua pergunta")).toBeEnabled());
    await escrever("de novo");
    expect(screen.getByRole("button", { name: /Enviar/ })).toBeEnabled();
  });
});

describe("Chat: limites e nova conversa", () => {
  it("passando do limite de caracteres bloqueia o envio e diz quanto passou", async () => {
    render(<Chat />);
    const campo = screen.getByLabelText("Sua pergunta");
    await userEvent.click(campo);
    await userEvent.paste("x".repeat(MAX_MENSAGEM + 5));

    expect(screen.getByText("5 caracteres a mais que o limite")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Enviar/ })).toBeDisabled();
    await userEvent.type(campo, "{Enter}");
    expect(conversarMock).not.toHaveBeenCalled();
  });

  it("mostra quantos caracteres ainda restam", async () => {
    render(<Chat />);
    expect(screen.getByText(`${MAX_MENSAGEM} caracteres restantes`)).toBeInTheDocument();
    await escrever("abc");
    expect(screen.getByText(`${MAX_MENSAGEM - 3} caracteres restantes`)).toBeInTheDocument();
  });

  it("nova conversa limpa tudo, esquece a sessão e cancela a resposta em andamento", async () => {
    let sinal: AbortSignal | undefined;
    conversarMock.mockImplementationOnce(
      (_p, aoEvento, s) =>
        new Promise<void>(() => {
          sinal = s;
          aoEvento({ nome: "sessao", sessaoId: "S1" });
          aoEvento({ nome: "trecho", texto: "texto parcial" });
        }),
    );
    render(<Chat />);
    await escrever("primeira{Enter}");
    expect(await screen.findByText("texto parcial")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Nova conversa/ }));

    expect(sinal?.aborted).toBe(true);
    expect(screen.queryByText("texto parcial")).not.toBeInTheDocument();
    expect(screen.getByText(/Olá! Posso tirar dúvidas/)).toBeInTheDocument();

    responderCom({ nome: "trecho", texto: "ok" });
    await escrever("segunda{Enter}");
    await waitFor(() => expect(conversarMock).toHaveBeenCalledTimes(2));
    expect(conversarMock.mock.calls[1][0].sessaoId).toBeNull(); // conversa nova, sessão nova
  });
});

describe("Chat: o que vem da IA nunca vira HTML (T38)", () => {
  const ATAQUE =
    '<img src=x onerror="alert(1)"><script>alert(2)</script><a href="javascript:alert(3)">clique</a><iframe src="//x"></iframe> **negrito** [link](http://x)';

  it("marcação na resposta aparece escrita na tela, sem criar elementos", async () => {
    responderCom({ nome: "trecho", texto: ATAQUE });
    const { container } = render(<Chat />);
    await escrever("pergunta{Enter}");

    expect(await screen.findByText(/clique/)).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("iframe")).toBeNull();
    expect(container.querySelector("a")).toBeNull();
    expect(container.querySelector("[onerror]")).toBeNull();
    // aparece como texto, do jeito que veio (sem Markdown também)
    expect(container.textContent).toContain('<img src=x onerror="alert(1)">');
    expect(container.textContent).toContain("**negrito**");
  });

  it("o mesmo vale para recusa, substituição e erro", async () => {
    responderCom(
      { nome: "recusa", motivo: "FORA_DO_ESCOPO", texto: "<b>recusa</b><script>x()</script>" },
      { nome: "erro", texto: "<img src=y onerror=z()>" },
    );
    const { container } = render(<Chat />);
    await escrever("pergunta{Enter}");
    await screen.findByText(/img src=y/);
    expect(container.querySelector("b, script, img")).toBeNull();
  });

  it("a pergunta do próprio usuário também é só texto", async () => {
    responderCom({ nome: "trecho", texto: "ok" });
    const { container } = render(<Chat />);
    await userEvent.click(screen.getByLabelText("Sua pergunta"));
    await userEvent.paste("<script>alert(1)</script>");
    await userEvent.type(screen.getByLabelText("Sua pergunta"), "{Enter}");
    await screen.findByText("ok");
    expect(container.querySelector("script")).toBeNull();
    expect(container.textContent).toContain("<script>alert(1)</script>");
  });
});
