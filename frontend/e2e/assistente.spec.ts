import { expect, test, type Page, type Route } from "@playwright/test";

const API = process.env.E2E_API_URL ?? "http://localhost:8080";
const ORIGEM = process.env.E2E_BASE_URL ?? "http://localhost:3000";

const CORS = {
  "access-control-allow-origin": ORIGEM,
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type, accept",
};

/** Responde o pedido de verificação do navegador (CORS) e entrega o resto ao tratador. */
async function comCors(route: Route, tratar: () => Promise<void>) {
  if (route.request().method() === "OPTIONS") {
    await route.fulfill({ status: 204, headers: CORS });
    return;
  }
  await tratar();
}

const STATUS_PRONTO = {
  pronto: true,
  motivoDesligado: null,
  versaoPrompt: "teste1234567",
  moderacaoAtiva: true,
  sessoesAtivas: 0,
  recusasUltimos10Min: 0,
  tokensUltimaHora: 0,
  provedores: [],
};

function sse(...eventos: [string, object][]): string {
  return eventos.map(([nome, dados]) => `event:${nome}\ndata:${JSON.stringify(dados)}\n\n`).join("");
}

/** Assistente "ligado" de mentira: só para ver a tela funcionando sem gastar crédito. */
async function assistenteDeMentira(page: Page, respostas: string[]) {
  const pedidos: unknown[] = [];
  await page.route(`**/api/agente/status`, (route) =>
    comCors(route, () => route.fulfill({ status: 200, headers: CORS, contentType: "application/json", body: JSON.stringify(STATUS_PRONTO) })),
  );
  await page.route(`**/api/agente/chat`, (route) =>
    comCors(route, async () => {
      pedidos.push(route.request().postDataJSON());
      const texto = respostas[Math.min(pedidos.length - 1, respostas.length - 1)];
      await route.fulfill({
        status: 200,
        headers: CORS,
        contentType: "text/event-stream",
        body: sse(
          ["sessao", { sessaoId: "sessao-de-teste" }],
          ["trecho", { texto }],
          ["fim", { versaoPrompt: "teste1234567" }],
        ),
      });
    }),
  );
  return pedidos;
}

test("o menu tem os links do assistente e dos relatórios", async ({ page }) => {
  await page.goto("/");
  const nav = page.getByRole("navigation");
  await expect(nav.getByRole("link", { name: "Assistente" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Relatórios" })).toBeVisible();
});

test("página do assistente: pergunta, resposta em tela e conversa com a mesma sessão", async ({ page }) => {
  const pedidos = await assistenteDeMentira(page, ["A tela de Alertas mostra o que mudou na fila.", "Segunda resposta."]);
  await page.goto("/assistente");

  await expect(page.getByRole("heading", { name: "Assistente" })).toBeVisible();
  await page.getByRole("button", { name: "Como uso a tela de Alertas?" }).click();
  await expect(page.getByText("A tela de Alertas mostra o que mudou na fila.")).toBeVisible();

  await page.getByLabel("Sua pergunta").fill("E o simulador?");
  await page.getByLabel("Sua pergunta").press("Enter");
  await expect(page.getByText("Segunda resposta.")).toBeVisible();

  expect(pedidos).toEqual([
    { mensagem: "Como uso a tela de Alertas?" },
    { sessaoId: "sessao-de-teste", mensagem: "E o simulador?" },
  ]);

  await page.getByRole("button", { name: /Nova conversa/ }).click();
  await expect(page.getByText(/Olá! Posso tirar dúvidas/)).toBeVisible();
});

test("o botão flutuante abre o chat em qualquer tela e a conversa segue ao navegar", async ({ page }) => {
  await assistenteDeMentira(page, ["Resposta que precisa sobreviver à navegação."]);
  await page.goto("/");

  await page.getByRole("button", { name: "Abrir o assistente" }).click();
  const painel = page.getByRole("region", { name: "Assistente do FilaVIVA" });
  await painel.getByLabel("Sua pergunta").fill("oi");
  await painel.getByLabel("Sua pergunta").press("Enter");
  await expect(painel.getByText("Resposta que precisa sobreviver à navegação.")).toBeVisible();

  await page.getByRole("navigation").getByRole("link", { name: "Alertas e reavaliação" }).click();
  await expect(page).toHaveURL(/\/alertas/);
  await expect(painel.getByText("Resposta que precisa sobreviver à navegação.")).toBeVisible();

  await page.getByRole("button", { name: "Fechar o assistente" }).click();
  await expect(painel).toBeHidden();

  // na página do assistente o botão flutuante some (o chat já está inteiro na página)
  await page.getByRole("navigation").getByRole("link", { name: "Assistente" }).click();
  await expect(page.getByRole("button", { name: "Abrir o assistente" })).toHaveCount(0);
});

test("resposta da IA com HTML aparece escrita e nunca é executada", async ({ page }) => {
  let alertou = false;
  page.on("dialog", async (d) => {
    alertou = true;
    await d.dismiss();
  });
  await assistenteDeMentira(page, ['<img src=x onerror="alert(1)"><script>alert(2)</script> texto visível']);
  await page.goto("/assistente");

  await page.getByLabel("Sua pergunta").fill("pergunta");
  await page.getByLabel("Sua pergunta").press("Enter");
  await expect(page.getByText(/texto visível/)).toBeVisible();
  await expect(page.getByText(/<img src=x onerror=/)).toBeVisible(); // escrito na tela, como texto
  await page.waitForTimeout(300);

  expect(alertou).toBe(false);
  await expect(page.locator("main img")).toHaveCount(0);
});

test("recusa e emergência chegam como mensagens do assistente", async ({ page }) => {
  await page.route(`**/api/agente/status`, (route) =>
    comCors(route, () => route.fulfill({ status: 200, headers: CORS, contentType: "application/json", body: JSON.stringify(STATUS_PRONTO) })),
  );
  await page.route(`**/api/agente/chat`, (route) =>
    comCors(route, () =>
      route.fulfill({
        status: 200,
        headers: CORS,
        contentType: "text/event-stream",
        body: sse(
          ["sessao", { sessaoId: "s" }],
          ["recusa", { motivo: "RISCO_A_SAUDE", texto: "Ligue agora para o SAMU (192)." }],
          ["fim", { versaoPrompt: "v" }],
        ),
      }),
    ),
  );
  await page.goto("/assistente");
  await page.getByLabel("Sua pergunta").fill("estou com dor forte no peito");
  await page.getByLabel("Sua pergunta").press("Enter");
  await expect(page.getByRole("alert").filter({ hasText: "SAMU (192)" })).toBeVisible();
});

test("relatórios: pedir, ler com o aviso de IA e baixar", async ({ page }) => {
  const relatorio = {
    id: "11111111-aaaa-bbbb-cccc-222222222222",
    tipo: "VISAO_GERAL",
    especialidade: null,
    titulo: "Relatório da fila — visão geral",
    solicitante: "Dra. Ana",
    criadoEm: "2026-10-24T10:00:00",
    texto: "> **Gerado por IA — revisar antes de usar.**\n\n## Resumo\nA fila tem 919 pacientes.",
    dadosUsados: "Pacientes na fila: 919.",
    versaoPrompt: "abc/def",
    geradoPorIA: true,
  };
  const { texto: _texto, dadosUsados: _dados, versaoPrompt: _versao, geradoPorIA: _ia, ...resumo } = relatorio;
  void [_texto, _dados, _versao, _ia];
  let gerado = false;
  await page.route(`**/api/agente/relatorios/especialidades`, (route) =>
    comCors(route, () => route.fulfill({ status: 200, headers: CORS, contentType: "application/json", body: JSON.stringify({ especialidades: ["Cirurgia Geral", "Urologia"] }) })),
  );
  await page.route(`**/api/agente/relatorios`, (route) =>
    comCors(route, async () => {
      if (route.request().method() === "POST") {
        gerado = true;
        await route.fulfill({ status: 200, headers: CORS, contentType: "application/json", body: JSON.stringify(relatorio) });
      } else {
        await route.fulfill({ status: 200, headers: CORS, contentType: "application/json", body: JSON.stringify(gerado ? [resumo] : []) });
      }
    }),
  );
  await page.goto("/relatorios");

  await expect(page.getByText("Nenhum relatório ainda.")).toBeVisible();
  await page.getByLabel("Seu nome (opcional)").fill("Dra. Ana");
  await page.getByRole("button", { name: /Gerar relatório/ }).click();

  await expect(page.getByText("gerado por IA · revisar antes de usar")).toBeVisible();
  await expect(page.getByText(/A fila tem 919 pacientes/)).toBeVisible();
  await expect(page.getByRole("link", { name: /Baixar em Markdown/ })).toHaveAttribute(
    "href",
    `/api/agente/relatorios/${relatorio.id}/download`, // relativo: o Next repassa para o backend
  );
  await expect(page.getByRole("button", { name: /Relatório da fila — visão geral/ })).toBeVisible();
});

test("com o backend de verdade: as telas abrem e mostram o estado real do assistente", async ({ page, request }) => {
  const status = await (await request.get(`${API}/api/agente/status`)).json();

  await page.goto("/assistente");
  await expect(page.getByLabel("Sua pergunta")).toBeVisible();
  if (status.pronto) {
    await expect(page.getByText(/ainda não está disponível/)).toHaveCount(0);
  } else {
    await expect(page.getByRole("status")).toContainText("ainda não está disponível");
    // desligado: o servidor responde 503 e a tela mostra a mensagem amigável, sem derrubar nada
    await page.getByLabel("Sua pergunta").fill("oi");
    await page.getByLabel("Sua pergunta").press("Enter");
    // (o Next também tem um role=alert escondido, o anúncio de rota: por isso o filtro pelo texto)
    await expect(page.getByRole("alert").filter({ hasText: "indisponível" })).toBeVisible();
  }

  await page.goto("/relatorios");
  await expect(page.getByRole("heading", { name: "Relatórios para médicos" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Gerar relatório/ })).toBeVisible();
});
