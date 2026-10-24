import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Componente, Simulacao } from "@/lib/api";
import { detalhe, itemFila, pagina } from "@/test/fixtures";

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

vi.mock("@/lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...original,
    api: {
      ...original.api,
      fila: vi.fn(),
      paciente: vi.fn(),
      simular: vi.fn(),
      ajustar: vi.fn(),
      registrarEvento: vi.fn(),
      resetDemo: vi.fn(),
      cenarioDemo: vi.fn(),
    },
  };
});

import { api } from "@/lib/api";
import { SimuladorPaciente } from "./simulador-paciente";

const mocks = vi.mocked(api);

const COMPONENTES: Componente[] = ["diagnostico", "piora", "dor", "limitacao", "espera", "risco"].map((chave) => ({
  chave,
  nome: chave,
  pontos: 5,
  maximo: 10,
  explicacao: `explicação de ${chave}`,
}));

function simulacao(sobrescrever: Partial<Simulacao> = {}): Simulacao {
  return {
    antes: itemFila({ classe: "D", posicao: 80 }),
    depois: itemFila({ classe: "A1", posicao: 3 }),
    sinaisNovos: [
      {
        tipo: "ALARME_GRAVE",
        rotulo: "Sinal de alarme grave no texto",
        categoria: "PIORA",
        descricao: "Hérnia encarcerada",
        evidencia: "episódio de encarceramento",
        fonte: "justificativa_clinica",
        data: null,
      },
    ],
    componentesDepois: COMPONENTES,
    mensagem: "Simulação (nada foi salvo). Subiu da posição 80 para 3, classe D → A1: Hérnia encarcerada",
    ...sobrescrever,
  };
}

function nadaFoiGravado() {
  expect(mocks.ajustar).not.toHaveBeenCalled();
  expect(mocks.registrarEvento).not.toHaveBeenCalled();
  expect(mocks.resetDemo).not.toHaveBeenCalled();
  expect(mocks.cenarioDemo).not.toHaveBeenCalled();
}

beforeEach(() => {
  Object.values(mocks).forEach((m) => typeof m === "function" && "mockReset" in m && (m as ReturnType<typeof vi.fn>).mockReset());
});

describe("SimuladorPaciente com paciente vindo da URL", () => {
  beforeEach(() => {
    mocks.paciente.mockResolvedValue(detalhe(itemFila({ solicitacaoId: "SOL-1", nome: "Maria da Silva" })));
  });

  it("carrega o paciente e mostra a situação atual", async () => {
    render(<SimuladorPaciente idInicial="SOL-1" />);
    expect(await screen.findByText("Maria da Silva")).toBeInTheDocument();
    expect(mocks.paciente).toHaveBeenCalledWith("SOL-1");
    expect(screen.getByText("Classe atual")).toBeInTheDocument();
  });

  it("o botão Simular fica desabilitado enquanto nada foi alterado", async () => {
    render(<SimuladorPaciente idInicial="SOL-1" />);
    await screen.findByText("Maria da Silva");
    expect(screen.getByRole("button", { name: /Simular/ })).toBeDisabled();
    expect(screen.getByText("Altere pelo menos um item para simular.")).toBeInTheDocument();
  });

  it("simula com o texto digitado e deixa claro que nada foi salvo", async () => {
    mocks.simular.mockResolvedValue(simulacao());
    render(<SimuladorPaciente idInicial="SOL-1" />);
    await screen.findByText("Maria da Silva");

    await userEvent.type(screen.getByPlaceholderText(/episódio de encarceramento/), "  episódio de encarceramento  ");
    await userEvent.click(screen.getByRole("button", { name: /Simular/ }));

    expect(mocks.simular).toHaveBeenCalledWith({ solicitacaoId: "SOL-1", textoAdicional: "episódio de encarceramento" });
    expect(await screen.findByText("Resultado da simulação")).toBeInTheDocument();
    expect(screen.getByText("simulação · nada foi salvo")).toBeInTheDocument();
    expect(screen.getByText(/Simulação \(nada foi salvo\)\. Subiu da posição 80 para 3/)).toBeInTheDocument();
    expect(screen.getByText("Hoje")).toBeInTheDocument();
    expect(screen.getByText("Se acontecer")).toBeInTheDocument();
    nadaFoiGravado();
  });

  it("sem sinal novo explica que a classificação não muda", async () => {
    mocks.simular.mockResolvedValue(simulacao({ sinaisNovos: [], depois: itemFila({ classe: "D", posicao: 80 }) }));
    render(<SimuladorPaciente idInicial="SOL-1" />);
    await screen.findByText("Maria da Silva");

    await userEvent.type(screen.getByPlaceholderText(/episódio de encarceramento/), "paciente estável");
    await userEvent.click(screen.getByRole("button", { name: /Simular/ }));

    expect(await screen.findByText(/Nenhum sinal novo/)).toBeInTheDocument();
  });

  it("erro da simulação aparece na tela e não grava nada", async () => {
    mocks.simular.mockRejectedValue(new Error("Solicitação SOL-1 não está na fila"));
    render(<SimuladorPaciente idInicial="SOL-1" />);
    await screen.findByText("Maria da Silva");

    await userEvent.type(screen.getByPlaceholderText(/episódio de encarceramento/), "texto");
    await userEvent.click(screen.getByRole("button", { name: /Simular/ }));

    expect(await screen.findByText(/não está na fila/)).toBeInTheDocument();
    expect(screen.queryByText("Resultado da simulação")).not.toBeInTheDocument();
    nadaFoiGravado();
  });

  it("Limpar apaga o resultado e volta a desabilitar o Simular", async () => {
    mocks.simular.mockResolvedValue(simulacao());
    render(<SimuladorPaciente idInicial="SOL-1" />);
    await screen.findByText("Maria da Silva");

    await userEvent.type(screen.getByPlaceholderText(/episódio de encarceramento/), "texto");
    await userEvent.click(screen.getByRole("button", { name: /Simular/ }));
    await screen.findByText("Resultado da simulação");

    await userEvent.click(screen.getByRole("button", { name: "Limpar" }));
    expect(screen.queryByText("Resultado da simulação")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Simular/ })).toBeDisabled();
  });

  it("mostra erro claro quando o paciente da URL não existe", async () => {
    mocks.paciente.mockReset();
    mocks.paciente.mockRejectedValue(new Error("Solicitação SOL-X não encontrada"));
    render(<SimuladorPaciente idInicial="SOL-X" />);
    expect(await screen.findByText(/Não foi possível abrir SOL-X/)).toBeInTheDocument();
  });
});

describe("SimuladorPaciente sem paciente escolhido", () => {
  it("busca só a partir de 2 letras e depois de uma pausa", async () => {
    mocks.fila.mockResolvedValue(pagina([itemFila({ solicitacaoId: "SOL-5", nome: "Joana Prado" })]));
    render(<SimuladorPaciente idInicial={null} />);

    const campo = screen.getByPlaceholderText(/Ex.: Maria/);
    await userEvent.type(campo, "j");
    await new Promise((r) => setTimeout(r, 450));
    expect(mocks.fila).not.toHaveBeenCalled();

    await userEvent.type(campo, "o");
    await waitFor(() => expect(mocks.fila).toHaveBeenCalledWith({ busca: "jo", tamanho: 10 }), { timeout: 2000 });
    expect(await screen.findByText("Joana Prado")).toBeInTheDocument();
  });

  it("escolher um resultado abre o formulário de simulação", async () => {
    mocks.fila.mockResolvedValue(pagina([itemFila({ solicitacaoId: "SOL-5", nome: "Joana Prado" })]));
    mocks.paciente.mockResolvedValue(detalhe(itemFila({ solicitacaoId: "SOL-5", nome: "Joana Prado" })));
    render(<SimuladorPaciente idInicial={null} />);

    await userEvent.type(screen.getByPlaceholderText(/Ex.: Maria/), "joana");
    await userEvent.click(await screen.findByText("Joana Prado", {}, { timeout: 2000 }));

    expect(await screen.findByText("E se…?")).toBeInTheDocument();
    expect(mocks.paciente).toHaveBeenCalledWith("SOL-5");
  });

  it("avisa quando a busca não encontra ninguém", async () => {
    mocks.fila.mockResolvedValue(pagina([], { total: 0 }));
    render(<SimuladorPaciente idInicial={null} />);
    await userEvent.type(screen.getByPlaceholderText(/Ex.: Maria/), "zzz");
    expect(await screen.findByText(/Nenhum paciente encontrado para/, {}, { timeout: 2000 })).toBeInTheDocument();
  });
});
