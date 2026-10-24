import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const API = process.env.E2E_API_URL ?? "http://localhost:8080";

interface Item {
  solicitacaoId: string;
  nome: string;
  classe: string;
  posicao: number;
}

async function fila(request: APIRequestContext, query = "tamanho=200"): Promise<{ itens: Item[]; total: number }> {
  const r = await request.get(`${API}/api/fila?${query}`);
  expect(r.ok(), `GET /api/fila?${query} respondeu ${r.status()}`).toBeTruthy();
  return r.json();
}

/** A tela do paciente tem dois formulários (decisão e novo evento); este é o da decisão. */
function painelDecisao(page: Page) {
  return page.locator("form", { has: page.getByLabel(/Justificativa/) });
}

test.beforeAll(async ({ request }) => {
  const r = await request.get(`${API}/api/status`).catch(() => null);
  if (!r?.ok()) throw new Error(`Backend fora do ar em ${API}. Suba o backend antes de rodar os testes e2e.`);
});

test.afterEach(async ({ request }) => {
  await request.post(`${API}/api/demo/reset`);
});

test("a fila abre com o paciente da posição 1 no topo", async ({ page, request }) => {
  const oficial = await fila(request, "tamanho=50");
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Fila cirúrgica priorizada" })).toBeVisible();
  const primeiraLinha = page.locator("tbody tr").first();
  await expect(primeiraLinha).toContainText(oficial.itens[0].nome);
  await expect(primeiraLinha.locator("td").first()).toHaveText("1");
  await expect(page.locator("tbody tr")).toHaveCount(50);
});

test("demo: fila → paciente → simulação → a fila oficial continua igual", async ({ page, request }) => {
  const antes = await fila(request);
  const calmo = (await fila(request, "classe=D&oncologico=false&tamanho=1")).itens[0];

  // 1. abre o paciente a partir da fila
  await page.goto("/");
  await page.locator("tbody tr").first().click();
  await expect(page).toHaveURL(/\/paciente\//);
  await expect(page.getByText("Decisão do regulador")).toBeVisible();

  // 2. simula um agravamento num paciente sem urgência
  await page.goto(`/simulador?id=${encodeURIComponent(calmo.solicitacaoId)}`);
  await expect(page.getByText(calmo.nome).first()).toBeVisible();
  const simular = page.getByRole("button", { name: "Simular", exact: true });
  await expect(simular).toBeDisabled();
  await page.getByPlaceholder(/episódio de encarceramento/).fill("Episódio de encarceramento há 2 dias.");
  await simular.click();
  await expect(page.getByText("Resultado da simulação")).toBeVisible();
  await expect(page.getByText("simulação · nada foi salvo")).toBeVisible();

  // 3. a fila oficial não mudou: nem pela API, nem na tela
  const depois = await fila(request);
  expect(depois).toEqual(antes);
  const alertas = await (await request.get(`${API}/api/alertas`)).json();
  expect(alertas.recentes).toHaveLength(0);

  await page.goto("/");
  await expect(page.locator("tbody tr").first()).toContainText(antes.itens[0].nome);
});

test("o regulador não consegue rebaixar um oncológico abaixo de A2", async ({ page, request }) => {
  const onco = (await fila(request, "oncologico=true&classe=A2&tamanho=1")).itens[0];

  await page.goto(`/paciente/${encodeURIComponent(onco.solicitacaoId)}`);
  await expect(page.getByText("Decisão do regulador")).toBeVisible();

  // na tela: B, C e D já vêm bloqueadas, com o motivo
  await page.getByRole("button", { name: "Mudar classe", exact: true }).click();
  const bloqueadas = painelDecisao(page).locator('button[title^="Bloqueado: paciente oncológico"]');
  await expect(bloqueadas).toHaveCount(3);
  for (const b of await bloqueadas.all()) await expect(b).toBeDisabled();
  await expect(painelDecisao(page).getByText(/pode subir, mas nunca ser reduzida/)).toBeVisible();

  // pela API, direto: o backend também recusa, citando a regra de ouro
  const resp = await request.post(`${API}/api/ajustes`, {
    data: { solicitacaoId: onco.solicitacaoId, tipo: "ALTERAR_CLASSE", classeNova: "D",
      justificativa: "Tentativa de rebaixar o paciente oncológico", autor: "teste e2e" },
  });
  expect(resp.ok()).toBe(false);
  expect(await resp.text()).toContain("Regra de ouro");

  const detalhe = await (await request.get(`${API}/api/pacientes/${onco.solicitacaoId}`)).json();
  expect(detalhe.ajustes).toHaveLength(0);
  expect(detalhe.item.classe).toBe("A2");
});

test("o regulador confirma a classe com justificativa e a decisão fica registrada", async ({ page, request }) => {
  const paciente = (await fila(request, "tamanho=1")).itens[0];

  await page.goto(`/paciente/${encodeURIComponent(paciente.solicitacaoId)}`);
  await expect(page.getByText("Decisão do regulador")).toBeVisible();

  const enviar = painelDecisao(page).locator('button[type="submit"]');
  await expect(enviar).toBeDisabled(); // sem justificativa não envia
  await page.getByLabel(/Justificativa/).fill("Laudo revisado, classe confirmada.");
  await expect(enviar).toBeEnabled();
  await enviar.click();
  await expect(page.getByText("Impacto na fila")).toBeVisible();

  const detalhe = await (await request.get(`${API}/api/pacientes/${paciente.solicitacaoId}`)).json();
  expect(detalhe.ajustes).toHaveLength(1);
  expect(detalhe.ajustes[0].tipo).toBe("CONFIRMAR_CLASSE");
  expect(detalhe.ajustes[0].justificativa).toBe("Laudo revisado, classe confirmada.");
});

test("API fora do ar mostra o aviso e volta ao normal ao tentar de novo", async ({ page }) => {
  await page.route("**/api/fila?*", (route) => route.abort());
  await page.goto("/");
  await expect(page.getByText("API fora do ar")).toBeVisible();

  await page.unroute("**/api/fila?*");
  await page.getByRole("button", { name: "Tentar de novo" }).click();
  await expect(page.locator("tbody tr").first()).toBeVisible();
  await expect(page.getByText("API fora do ar")).toHaveCount(0);
});
