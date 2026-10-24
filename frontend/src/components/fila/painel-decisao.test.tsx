import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Impacto } from "@/lib/api";
import { detalhe, itemFila } from "@/test/fixtures";

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

vi.mock("@/lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api")>();
  return { ...original, api: { ...original.api, ajustar: vi.fn() } };
});

import { api } from "@/lib/api";
import { PainelDecisao } from "./painel-decisao";

const ajustar = vi.mocked(api.ajustar);

const IMPACTO: Impacto = {
  solicitacaoId: "SOL-2026-00001",
  nome: "Maria da Silva",
  posicaoAntes: 10,
  posicaoDepois: 4,
  classeAntes: "B",
  classeDepois: "A2",
  statusAntes: "ATIVA",
  statusDepois: "ATIVA",
  mensagem: "Subiu da posição 10 para 4: regulador definiu a classe A2",
};

const JUSTIFICATIVA_OK = "Laudo confirma progressão.";

beforeEach(() => {
  ajustar.mockReset();
});

/** O texto "Confirmar classe" aparece no seletor de ação e no botão de enviar; este é o de enviar. */
function botaoEnviar() {
  return screen.getAllByRole("button").find((b) => b.getAttribute("type") === "submit")!;
}

/** O botão de escolher a classe nova (o selo dentro dele repete o mesmo title). */
function botaoClasse(classe: string) {
  return screen.getAllByTitle(new RegExp(`^${classe} ·`)).find((el) => el.tagName === "BUTTON")!;
}

async function escreverJustificativa(texto: string) {
  await userEvent.type(screen.getByLabelText(/Justificativa/), texto);
}

describe("PainelDecisao: justificativa obrigatória", () => {
  it("começa com o envio desabilitado", () => {
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={vi.fn()} />);
    expect(botaoEnviar()).toBeDisabled();
    expect(screen.getByText("0/10 caracteres mínimos")).toBeInTheDocument();
  });

  it("9 caracteres não bastam; 10 liberam o envio", async () => {
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={vi.fn()} />);
    await escreverJustificativa("123456789");
    expect(botaoEnviar()).toBeDisabled();
    expect(screen.getByText("9/10 caracteres mínimos")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Justificativa/), "0");
    expect(botaoEnviar()).toBeEnabled();
  });

  it("espaços em volta não contam como caracteres", async () => {
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={vi.fn()} />);
    await escreverJustificativa("     abc     ");
    expect(botaoEnviar()).toBeDisabled();
    expect(screen.getByText("3/10 caracteres mínimos")).toBeInTheDocument();
  });
});

describe("PainelDecisao: confirmar classe", () => {
  it("envia o ajuste, limpa o formulário e avisa a tela", async () => {
    ajustar.mockResolvedValue(IMPACTO);
    const aoDecidir = vi.fn();
    render(<PainelDecisao detalhe={detalhe(itemFila({ solicitacaoId: "SOL-9" }))} aoDecidir={aoDecidir} />);

    await escreverJustificativa(`  ${JUSTIFICATIVA_OK}  `);
    await userEvent.click(botaoEnviar());

    expect(ajustar).toHaveBeenCalledWith({
      solicitacaoId: "SOL-9",
      tipo: "CONFIRMAR_CLASSE",
      classeNova: undefined,
      justificativa: JUSTIFICATIVA_OK,
      autor: "regulador",
    });
    expect(aoDecidir).toHaveBeenCalledOnce();
    expect(await screen.findByText("Impacto na fila")).toBeInTheDocument();
    expect(screen.getByLabelText(/Justificativa/)).toHaveValue("");
  });

  it("usa o autor digitado e volta para 'regulador' se ficar vazio", async () => {
    ajustar.mockResolvedValue(IMPACTO);
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={vi.fn()} />);

    const autor = screen.getByLabelText("Autor");
    await userEvent.clear(autor);
    await userEvent.type(autor, "Dra. Ana");
    await escreverJustificativa(JUSTIFICATIVA_OK);
    await userEvent.click(botaoEnviar());
    expect(ajustar.mock.calls[0][0].autor).toBe("Dra. Ana");

    await userEvent.clear(autor);
    await escreverJustificativa(JUSTIFICATIVA_OK);
    await userEvent.click(botaoEnviar());
    expect(ajustar.mock.calls[1][0].autor).toBe("regulador");
  });

  it("mostra o motivo quando o backend recusa a decisão e mantém o texto digitado", async () => {
    ajustar.mockRejectedValue(new Error("Regra de ouro: paciente oncológico não pode ficar abaixo da classe A2."));
    const aoDecidir = vi.fn();
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={aoDecidir} />);

    await escreverJustificativa(JUSTIFICATIVA_OK);
    await userEvent.click(botaoEnviar());

    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Decisão recusada");
    expect(alerta).toHaveTextContent("Regra de ouro");
    expect(aoDecidir).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Justificativa/)).toHaveValue(JUSTIFICATIVA_OK);
  });
});

describe("PainelDecisao: mudar classe", () => {
  async function abrirMudarClasse(item = itemFila({ classe: "B" })) {
    render(<PainelDecisao detalhe={detalhe(item)} aoDecidir={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "Mudar classe" }));
  }

  it("exige escolher uma classe antes de enviar", async () => {
    await abrirMudarClasse();
    await escreverJustificativa(JUSTIFICATIVA_OK);
    expect(botaoEnviar()).toBeDisabled();

    await userEvent.click(botaoClasse("A1"));
    expect(botaoEnviar()).toBeEnabled();
  });

  it("a classe atual não pode ser escolhida como nova", async () => {
    await abrirMudarClasse(itemFila({ classe: "B" }));
    expect(botaoClasse("B")).toBeDisabled();
    expect(botaoClasse("A1")).toBeEnabled();
  });

  it("envia a classe escolhida", async () => {
    ajustar.mockResolvedValue(IMPACTO);
    await abrirMudarClasse(itemFila({ solicitacaoId: "SOL-7", classe: "B" }));
    await userEvent.click(botaoClasse("A2"));
    await escreverJustificativa(JUSTIFICATIVA_OK);
    await userEvent.click(botaoEnviar());

    expect(ajustar).toHaveBeenCalledWith(
      expect.objectContaining({ solicitacaoId: "SOL-7", tipo: "ALTERAR_CLASSE", classeNova: "A2" }),
    );
  });

  it("oncológico: classes menos urgentes ficam bloqueadas, subir continua possível", async () => {
    await abrirMudarClasse(itemFila({ classe: "A2", oncologico: true }));
    expect(screen.getByText(/pode subir, mas nunca ser reduzida/)).toBeInTheDocument();
    // B, C e D: botões desabilitados, com o motivo no título
    const bloqueados = screen.getAllByTitle(/^Bloqueado: paciente oncológico/);
    expect(bloqueados).toHaveLength(3);
    bloqueados.forEach((b) => expect(b).toBeDisabled());
    expect(botaoClasse("A1")).toBeEnabled();
  });

  it("rebaixar não oncológico vira sugestão de rebaixamento, sem o aviso de oncológico", async () => {
    await abrirMudarClasse(itemFila({ classe: "B", oncologico: false }));
    await userEvent.click(botaoClasse("D"));
    expect(screen.queryByText(/pode subir, mas nunca ser reduzida/)).not.toBeInTheDocument();
    expect(screen.getByText(/registra um rebaixamento sugerido/)).toBeInTheDocument();
  });
});

describe("PainelDecisao: saída da fila", () => {
  it("sem sugestão de saída só oferece confirmar e mudar classe", () => {
    render(<PainelDecisao detalhe={detalhe()} aoDecidir={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Confirmar saída" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Recusar saída" })).not.toBeInTheDocument();
  });

  it("com sugestão de saída mostra a evidência e oferece confirmar e recusar", async () => {
    ajustar.mockResolvedValue(IMPACTO);
    const sinalSaida = {
      tipo: "SAIDA_DESISTENCIA",
      rotulo: "Paciente desistiu da cirurgia",
      categoria: "SAIDA" as const,
      descricao: "Desistiu em contato de 05/10/2026",
      evidencia: "Paciente informa que não quer mais operar",
      fonte: "evento EVT-1",
      data: "2026-10-05",
    };
    render(<PainelDecisao detalhe={detalhe(itemFila({ status: "SAIDA_SUGERIDA" }), { sinalSaida })} aoDecidir={vi.fn()} />);

    expect(screen.getByText(/Saída sugerida: Paciente desistiu da cirurgia/)).toBeInTheDocument();
    expect(screen.getByText(/Paciente informa que não quer mais operar/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Recusar saída" }));
    await escreverJustificativa("Paciente confirmou que quer operar.");
    await userEvent.click(botaoEnviar());
    expect(ajustar).toHaveBeenCalledWith(expect.objectContaining({ tipo: "RECUSAR_SAIDA" }));
  });
});
