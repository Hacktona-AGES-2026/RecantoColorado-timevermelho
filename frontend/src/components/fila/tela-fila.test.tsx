import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { itemFila, pagina } from "@/test/fixtures";

// ---- mocks de navegação: a URL é a fonte dos filtros ----
const replace = vi.fn();
const push = vi.fn();
let queryAtual = "";
let paramsEmCache: { query: string; params: URLSearchParams } | null = null;

// Como no Next de verdade, useSearchParams devolve o mesmo objeto enquanto a URL não muda
// (um objeto novo a cada render faria o efeito de busca rodar sem parar).
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push }),
  usePathname: () => "/",
  useSearchParams: () => {
    if (!paramsEmCache || paramsEmCache.query !== queryAtual) {
      paramsEmCache = { query: queryAtual, params: new URLSearchParams(queryAtual) };
    }
    return paramsEmCache.params;
  },
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...resto}>
      {children}
    </a>
  ),
}));

vi.mock("@/lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api")>();
  return { ...original, api: { ...original.api, fila: vi.fn() } };
});

import { api } from "@/lib/api";
import { TelaFila } from "./tela-fila";

const filaMock = vi.mocked(api.fila);

beforeEach(() => {
  replace.mockReset();
  push.mockReset();
  filaMock.mockReset();
  queryAtual = "";
});

describe("TelaFila", () => {
  it("mostra a tabela com os pacientes que a API devolveu", async () => {
    filaMock.mockResolvedValue(
      pagina([
        itemFila({ solicitacaoId: "SOL-1", nome: "Maria da Silva", posicao: 1 }),
        itemFila({ solicitacaoId: "SOL-2", nome: "João Souza", posicao: 2, classe: "A2", oncologico: true, cid: "C50.4" }),
      ]),
    );
    render(<TelaFila />);

    expect(await screen.findByText("Maria da Silva")).toBeInTheDocument();
    expect(screen.getByText("João Souza")).toBeInTheDocument();
    expect(screen.getByText("C50.4 · onco")).toBeInTheDocument();
    expect(screen.getByText("2 pacientes · página 1 de 1")).toBeInTheDocument();
  });

  it("mostra o total da fila ativa somando as classes", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()], { porClasse: { A1: 1, A2: 2, B: 3, C: 4, D: 5 } }));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    const cartaoTotal = screen.getByText("Na fila").closest("button")!;
    expect(within(cartaoTotal).getByText("15")).toBeInTheDocument();
  });

  it("consulta a API com a primeira página da fila ativa", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()]));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(filaMock).toHaveBeenCalledWith(expect.objectContaining({ status: "ATIVA", pagina: 0, tamanho: 50 }));
  });

  it("lê os filtros da URL e manda para a API", async () => {
    queryAtual = "classe=A1&onco=1&busca=maria&p=2&status=TODOS";
    filaMock.mockResolvedValue(pagina([itemFila()], { total: 200 }));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(filaMock).toHaveBeenCalledWith(
      expect.objectContaining({ classe: "A1", oncologico: true, busca: "maria", pagina: 2, status: "TODOS" }),
    );
  });

  it("ignora classe inválida na URL", async () => {
    queryAtual = "classe=Z";
    filaMock.mockResolvedValue(pagina([itemFila()]));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(filaMock.mock.calls[0][0]).toMatchObject({ classe: undefined });
  });

  it("clicar numa classe filtra pela URL", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()]));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");

    await userEvent.click(screen.getByTitle(/Filtrar classe A1/));
    expect(replace).toHaveBeenCalledWith("/?classe=A1", { scroll: false });
  });

  it("o filtro só oncológicos vai para a URL", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()]));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");

    await userEvent.click(screen.getByLabelText("Só oncológicos"));
    expect(replace).toHaveBeenCalledWith("/?onco=1", { scroll: false });
  });

  it("a busca espera o usuário parar de digitar e volta para a página 0", async () => {
    queryAtual = "p=3";
    filaMock.mockResolvedValue(pagina([itemFila()], { total: 400 }));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");

    await userEvent.type(screen.getByLabelText("Buscar"), "maria");
    expect(replace).not.toHaveBeenCalled(); // ainda no debounce
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/?busca=maria", { scroll: false }), { timeout: 2000 });
  });

  it("paginação: avançar e voltar mudam a página e respeitam os limites", async () => {
    queryAtual = "p=1";
    filaMock.mockResolvedValue(pagina([itemFila()], { total: 120, pagina: 1 })); // 3 páginas de 50
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(screen.getByText("120 pacientes · página 2 de 3")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Próxima/ }));
    expect(replace).toHaveBeenLastCalledWith("/?p=2", { scroll: false });
    await userEvent.click(screen.getByRole("button", { name: /Anterior/ }));
    expect(replace).toHaveBeenLastCalledWith("/", { scroll: false });
  });

  it("na primeira página o botão Anterior fica desabilitado", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()], { total: 120 }));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(screen.getByRole("button", { name: /Anterior/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Próxima/ })).toBeEnabled();
  });

  it("clicar na linha abre o paciente", async () => {
    filaMock.mockResolvedValue(pagina([itemFila({ solicitacaoId: "SOL-2026-00351" })]));
    render(<TelaFila />);
    const linha = (await screen.findByText("SOL-2026-00351", { exact: false })).closest("tr")!;
    await userEvent.click(linha);
    expect(push).toHaveBeenCalledWith("/paciente/SOL-2026-00351");
  });

  it("sem resultado mostra a mensagem e o botão de limpar filtros", async () => {
    queryAtual = "busca=zzz";
    filaMock.mockResolvedValue(pagina([], { total: 0 }));
    render(<TelaFila />);
    expect(await screen.findByText("Nenhum paciente encontrado com esses filtros.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(replace).toHaveBeenCalledWith("/", { scroll: false });
  });

  it("sem filtros e sem pacientes não oferece limpar filtros", async () => {
    filaMock.mockResolvedValue(pagina([], { total: 0 }));
    render(<TelaFila />);
    await screen.findByText("Nenhum paciente encontrado com esses filtros.");
    expect(screen.queryByRole("button", { name: "Limpar filtros" })).not.toBeInTheDocument();
  });

  it("API fora do ar mostra o aviso e tenta de novo ao clicar", async () => {
    filaMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    render(<TelaFila />);
    expect(await screen.findByText("API fora do ar")).toBeInTheDocument();

    filaMock.mockResolvedValueOnce(pagina([itemFila()]));
    await userEvent.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Maria da Silva")).toBeInTheDocument();
    expect(screen.queryByText("API fora do ar")).not.toBeInTheDocument();
  });

  it("erro do backend mostra a mensagem dele", async () => {
    filaMock.mockRejectedValueOnce(new Error("Erro 500"));
    render(<TelaFila />);
    expect(await screen.findByText("Não foi possível carregar")).toBeInTheDocument();
    expect(screen.getByText("Erro 500")).toBeInTheDocument();
  });

  it("sinaliza classe ajustada, regra de ouro e reavaliações pendentes", async () => {
    filaMock.mockResolvedValue(
      pagina([itemFila({ ajustada: true, classeCalculada: "D", movidoPelaRegraDeOuro: true, reavaliacoes: 3 })]),
    );
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    expect(screen.getByText("✎ reg.")).toBeInTheDocument();
    expect(screen.getByText("★ regra")).toBeInTheDocument();
    expect(screen.getByText("⚠ 3")).toBeInTheDocument();
  });

  it("o botão de download aponta para o CSV do backend", async () => {
    filaMock.mockResolvedValue(pagina([itemFila()]));
    render(<TelaFila />);
    await screen.findByText("Maria da Silva");
    const link = screen.getByRole("link", { name: /fila_priorizada\.csv/ });
    expect(link).toHaveAttribute("href", api.exportCsvUrl);
  });
});
