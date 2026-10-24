import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

let caminho = "/";
vi.mock("next/navigation", () => ({ usePathname: () => caminho }));

vi.mock("@/lib/agente", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/agente")>();
  return { ...original, agenteApi: { ...original.agenteApi, status: vi.fn(), conversar: vi.fn() } };
});

import { agenteApi } from "@/lib/agente";
import { AssistenteFlutuante } from "./assistente-flutuante";

beforeEach(() => {
  caminho = "/";
  vi.mocked(agenteApi.status).mockReset();
  vi.mocked(agenteApi.conversar).mockReset();
  vi.mocked(agenteApi.status).mockResolvedValue({
    pronto: true,
    motivoDesligado: null,
    versaoPrompt: "v1",
    moderacaoAtiva: true,
    sessoesAtivas: 0,
    recusasUltimos10Min: 0,
    tokensUltimaHora: 0,
    provedores: [],
  });
});

describe("AssistenteFlutuante", () => {
  it("começa fechado, só com o botão no canto da tela", async () => {
    render(<AssistenteFlutuante />);
    expect(screen.getByRole("button", { name: "Abrir o assistente" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Assistente do FilaVIVA" })).not.toBeInTheDocument();
    await waitFor(() => expect(agenteApi.status).toHaveBeenCalled());
  });

  it("abre o painel com o chat e fecha de novo", async () => {
    render(<AssistenteFlutuante />);
    await userEvent.click(screen.getByRole("button", { name: "Abrir o assistente" }));

    expect(screen.getByRole("region", { name: "Assistente do FilaVIVA" })).toBeInTheDocument();
    expect(screen.getByLabelText("Sua pergunta")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Abrir o assistente" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Fechar o assistente" }));
    expect(screen.queryByRole("region", { name: "Assistente do FilaVIVA" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir o assistente" })).toBeInTheDocument();
  });

  it("a conversa continua ao fechar e abrir de novo", async () => {
    vi.mocked(agenteApi.conversar).mockImplementation(async (_p, aoEvento) => {
      aoEvento({ nome: "trecho", texto: "Resposta guardada." });
    });
    render(<AssistenteFlutuante />);
    await userEvent.click(screen.getByRole("button", { name: "Abrir o assistente" }));
    await userEvent.type(screen.getByLabelText("Sua pergunta"), "oi{Enter}");
    await screen.findByText("Resposta guardada.");

    await userEvent.click(screen.getByRole("button", { name: "Fechar o assistente" }));
    await userEvent.click(screen.getByRole("button", { name: "Abrir o assistente" }));

    expect(screen.getByText("Resposta guardada.")).toBeVisible();
    expect(screen.getByText("oi")).toBeVisible();
  });

  it("não aparece na própria página do assistente", () => {
    caminho = "/assistente";
    const { container } = render(<AssistenteFlutuante />);
    expect(container).toBeEmptyDOMElement();
  });

  it("aparece nas outras páginas", () => {
    for (const p of ["/", "/alertas", "/paciente/SOL-1", "/relatorios"]) {
      caminho = p;
      const { unmount } = render(<AssistenteFlutuante />);
      expect(screen.getByRole("button", { name: "Abrir o assistente" })).toBeInTheDocument();
      unmount();
    }
  });
});
