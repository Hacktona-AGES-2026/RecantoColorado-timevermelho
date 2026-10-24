import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Testes de GUARDA do frontend (T25 e T38): leem o código-fonte e falham se alguém colocar uma chave de API no
 * navegador, abrir uma brecha para HTML vindo da IA ou espalhar chamadas de rede fora dos clientes oficiais.
 */

const RAIZ = join(__dirname, ".."); // frontend/src
const RAIZ_FRONT = join(RAIZ, ".."); // frontend

function arquivos(pasta: string): string[] {
  return readdirSync(pasta).flatMap((nome) => {
    const caminho = join(pasta, nome);
    return statSync(caminho).isDirectory() ? arquivos(caminho) : [caminho];
  });
}

const fontes = arquivos(RAIZ).filter((f) => /\.(ts|tsx)$/.test(f) && !/\.test\.(ts|tsx)$/.test(f) && !f.includes(`${sep}test${sep}`));

function com(padrao: RegExp): string[] {
  return fontes.filter((f) => padrao.test(readFileSync(f, "utf8"))).map((f) => relative(RAIZ, f).split(sep).join("/"));
}

describe("segurança do frontend", () => {
  it("encontra os arquivos de código para conferir", () => {
    expect(fontes.length).toBeGreaterThan(20);
  });

  it("nenhuma variável NEXT_PUBLIC_ guarda segredo (o Next.js as embute no JavaScript do navegador)", () => {
    expect(com(/NEXT_PUBLIC_[A-Z0-9_]*(KEY|SECRET|TOKEN|PASSWORD|SENHA|CHAVE)/i)).toEqual([]);
    const exemplo = readFileSync(join(RAIZ_FRONT, ".env.example"), "utf8");
    const nomes = exemplo.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => l.split("=")[0]);
    expect(nomes).toEqual(["NEXT_PUBLIC_API_URL"]);
  });

  it("nenhuma chave de API, cabeçalho de autorização nem endereço de provedor de IA no código do navegador", () => {
    expect(com(/sk-[A-Za-z0-9_-]{20,}/)).toEqual([]);
    expect(com(/sk-ant-|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}/)).toEqual([]);
    expect(com(/api\.openai\.com|api\.anthropic\.com|generativelanguage\.googleapis\.com/)).toEqual([]);
    expect(com(/IA_PROVEDOR|OPENAI_API_KEY|ANTHROPIC_API_KEY/)).toEqual([]);
    expect(com(/Authorization|Bearer /)).toEqual([]);
  });

  it("nada é montado como HTML ou executado a partir de texto (a resposta da IA só vira texto)", () => {
    expect(com(/dangerouslySetInnerHTML/)).toEqual([]);
    expect(com(/\.innerHTML|\.outerHTML|insertAdjacentHTML|document\.write/)).toEqual([]);
    expect(com(/\beval\(|new Function\(|setTimeout\(\s*["'`]/)).toEqual([]);
  });

  it("só os dois clientes oficiais chamam a rede, e o do assistente só fala com /api/agente/", () => {
    expect(com(/\bfetch\(/).sort()).toEqual(["lib/agente.ts", "lib/api.ts"]);
    expect(com(/XMLHttpRequest|WebSocket|EventSource|sendBeacon/)).toEqual([]);
    const agente = readFileSync(join(RAIZ, "lib/agente.ts"), "utf8");
    // toda rota escrita no cliente do assistente (texto "/api/..." ou template com API_URL)
    const rotas = [...agente.matchAll(/["'`](\/api\/[^"'`$]*)/g)].map((m) => m[1]);
    rotas.push(...[...agente.matchAll(/\$\{API_URL\}(\/[^`"$]*)/g)].map((m) => m[1]));
    expect(rotas.length).toBeGreaterThan(5);
    expect(rotas.filter((r) => !r.startsWith("/api/agente/"))).toEqual([]);
  });

  it("a chave nunca é guardada no navegador, nem a conversa em armazenamento permanente", () => {
    expect(com(/localStorage|sessionStorage|indexedDB|document\.cookie/)).toEqual([]);
  });

  it("links vindos de dados da IA não são montados com href dinâmico no chat e nos relatórios", () => {
    const chat = readFileSync(join(RAIZ, "components/assistente/chat.tsx"), "utf8");
    const relatorios = readFileSync(join(RAIZ, "components/relatorios/tela-relatorios.tsx"), "utf8");
    expect(chat).not.toMatch(/href=/);
    // o único link dos relatórios é o de download, montado pelo cliente a partir do id
    expect([...relatorios.matchAll(/href=\{([^}]*)\}/g)].map((m) => m[1])).toEqual(["agenteApi.urlDownloadRelatorio(atual.id)"]);
  });
});
