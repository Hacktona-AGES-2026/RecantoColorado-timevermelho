import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Relatorio, RelatorioResumo } from "@/lib/agente";

vi.mock("@/lib/agente", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/agente")>();
  return {
    ...original,
    agenteApi: {
      ...original.agenteApi,
      especialidades: vi.fn(),
      procedimentos: vi.fn(),
      relatorios: vi.fn(),
      relatorio: vi.fn(),
      gerarRelatorio: vi.fn(),
    },
  };
});

import { agenteApi, ErroAgente } from "@/lib/agente";
import { TelaRelatorios } from "./tela-relatorios";

const api = vi.mocked(agenteApi);

function relatorio(sobrescrever: Partial<Relatorio> = {}): Relatorio {
  return {
    id: "11111111-aaaa-bbbb-cccc-222222222222",
    tipo: "VISAO_GERAL",
    especialidade: null,
    procedimento: null,
    titulo: "Relatório da fila — visão geral",
    solicitante: "Dra. Ana",
    criadoEm: "2026-10-24T10:00:00",
    texto: "> **Gerado por IA — revisar antes de usar.**\n\n## Resumo\nA fila tem 919 pacientes.",
    dadosUsados: "Pacientes na fila: 919.",
    versaoPrompt: "abc/def",
    geradoPorIA: true,
    ...sobrescrever,
  };
}

function resumo(r: Relatorio): RelatorioResumo {
  return { id: r.id, tipo: r.tipo, especialidade: r.especialidade, procedimento: r.procedimento, titulo: r.titulo, solicitante: r.solicitante, criadoEm: r.criadoEm };
}

beforeEach(() => {
  Object.values(api).forEach((f) => typeof f === "function" && "mockReset" in f && (f as ReturnType<typeof vi.fn>).mockReset());
  api.especialidades.mockResolvedValue({ especialidades: ["Cirurgia Geral", "Urologia"] });
  api.procedimentos.mockResolvedValue({ procedimentos: ["Colectomia", "Histerectomia"] });
  api.relatorios.mockResolvedValue([]);
});

describe("TelaRelatorios", () => {
  it("começa sem relatórios e com o formulário de pedido", async () => {
    render(<TelaRelatorios />);
    expect(await screen.findByText("Nenhum relatório ainda.")).toBeInTheDocument();
    expect(screen.getByText("Gere um relatório ou abra um da lista para ver aqui.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Gerar relatório/ })).toBeEnabled();
  });

  it("gera a visão geral e mostra o texto com o aviso de IA", async () => {
    const r = relatorio();
    api.gerarRelatorio.mockResolvedValue(r);
    api.relatorios.mockResolvedValueOnce([]).mockResolvedValue([resumo(r)]);
    render(<TelaRelatorios />);
    await screen.findByText("Nenhum relatório ainda.");

    await userEvent.type(screen.getByLabelText("Seu nome (opcional)"), "  Dra. Ana  ");
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    expect(await screen.findByText("gerado por IA · revisar antes de usar")).toBeInTheDocument();
    expect(screen.getByText(/A fila tem 919 pacientes/)).toBeInTheDocument();
    expect(api.gerarRelatorio).toHaveBeenCalledWith({ tipo: "VISAO_GERAL", especialidade: undefined, solicitante: "Dra. Ana" });
    // a lista do histórico foi atualizada
    await waitFor(() => expect(screen.getAllByText("Relatório da fila — visão geral").length).toBeGreaterThanOrEqual(2));
  });

  it("por especialidade, mostra as especialidades da fila e manda a escolhida", async () => {
    api.gerarRelatorio.mockResolvedValue(relatorio({ tipo: "ESPECIALIDADE", especialidade: "Urologia", titulo: "Relatório da fila — Urologia" }));
    render(<TelaRelatorios />);
    await userEvent.selectOptions(screen.getByLabelText("Tipo"), "ESPECIALIDADE");

    const select = await screen.findByLabelText("Especialidade");
    expect(Array.from((select as HTMLSelectElement).options).map((o) => o.value)).toEqual(["Cirurgia Geral", "Urologia"]);
    await userEvent.selectOptions(select, "Urologia");
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    await waitFor(() =>
      expect(api.gerarRelatorio).toHaveBeenCalledWith({ tipo: "ESPECIALIDADE", especialidade: "Urologia", solicitante: undefined }),
    );
  });

  it("por procedimento, mostra os procedimentos da fila e manda o escolhido", async () => {
    api.gerarRelatorio.mockResolvedValue(
      relatorio({ tipo: "PROCEDIMENTO", procedimento: "Histerectomia", titulo: "Relatório da fila — Histerectomia" }),
    );
    render(<TelaRelatorios />);
    await userEvent.selectOptions(screen.getByLabelText("Tipo"), "PROCEDIMENTO");

    const select = await screen.findByLabelText("Procedimento");
    expect(Array.from((select as HTMLSelectElement).options).map((o) => o.value)).toEqual(["Colectomia", "Histerectomia"]);
    await userEvent.selectOptions(select, "Histerectomia");
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    await waitFor(() =>
      expect(api.gerarRelatorio).toHaveBeenCalledWith({ tipo: "PROCEDIMENTO", procedimento: "Histerectomia", solicitante: undefined }),
    );
    expect(await screen.findByText("Relatório da fila — Histerectomia")).toBeInTheDocument();
  });

  it("o link de download aponta para o backend e baixa o arquivo", async () => {
    const r = relatorio();
    api.gerarRelatorio.mockResolvedValue(r);
    render(<TelaRelatorios />);
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    const link = await screen.findByRole("link", { name: /Baixar em Markdown/ });
    expect(link).toHaveAttribute("href", agenteApi.urlDownloadRelatorio(r.id));
    expect(link).toHaveAttribute("download");
  });

  it("enquanto gera, o botão fica desabilitado", async () => {
    let terminar!: (r: Relatorio) => void;
    api.gerarRelatorio.mockImplementation(() => new Promise<Relatorio>((resolve) => (terminar = resolve)));
    render(<TelaRelatorios />);
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    expect(screen.getByRole("button", { name: /Gerando/ })).toBeDisabled();
    terminar(relatorio());
    expect(await screen.findByRole("button", { name: /Gerar relatório/ })).toBeEnabled();
  });

  it.each([
    [503, "O assistente está indisponível no momento."],
    [429, "Muitas mensagens em pouco tempo. Aguarde um minuto e tente de novo."],
    [422, "Especialidade desconhecida. Escolha uma das que aparecem na fila."],
  ])("erro %i do servidor aparece como alerta", async (status, mensagem) => {
    api.gerarRelatorio.mockRejectedValue(new ErroAgente(mensagem, status));
    render(<TelaRelatorios />);
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent(mensagem);
    expect(screen.getByRole("button", { name: /Gerar relatório/ })).toBeEnabled();
  });

  it("falha de rede mostra um aviso genérico", async () => {
    api.gerarRelatorio.mockRejectedValue(new TypeError("Failed to fetch"));
    render(<TelaRelatorios />);
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não consegui falar com o servidor");
  });

  it("abre um relatório antigo da lista", async () => {
    const antigo = relatorio({ id: "99999999-aaaa", titulo: "Relatório da fila — Urologia", texto: "Texto do relatório antigo." });
    api.relatorios.mockResolvedValue([resumo(antigo)]);
    api.relatorio.mockResolvedValue(antigo);
    render(<TelaRelatorios />);

    await userEvent.click(await screen.findByRole("button", { name: /Relatório da fila — Urologia/ }));

    expect(api.relatorio).toHaveBeenCalledWith("99999999-aaaa");
    expect(await screen.findByText("Texto do relatório antigo.")).toBeInTheDocument();
  });

  it("o texto do relatório é só texto: marcação vinda da IA não vira elemento", async () => {
    api.gerarRelatorio.mockResolvedValue(
      relatorio({ texto: '<script>alert(1)</script><img src=x onerror="alert(2)"><a href="javascript:alert(3)">x</a> texto seguro' }),
    );
    const { container } = render(<TelaRelatorios />);
    await userEvent.click(screen.getByRole("button", { name: /Gerar relatório/ }));

    await screen.findByText(/texto seguro/);
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("[onerror]")).toBeNull();
    expect(container.querySelector("a[href^='javascript']")).toBeNull();
    expect(container.textContent).toContain("<script>alert(1)</script>");
  });

  it("sem especialidades carregadas não deixa gerar por especialidade", async () => {
    api.especialidades.mockResolvedValue({ especialidades: [] });
    render(<TelaRelatorios />);
    await userEvent.selectOptions(screen.getByLabelText("Tipo"), "ESPECIALIDADE");
    expect(screen.getByRole("button", { name: /Gerar relatório/ })).toBeDisabled();
  });
});
