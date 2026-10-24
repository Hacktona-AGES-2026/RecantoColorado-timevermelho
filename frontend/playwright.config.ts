import { defineConfig } from "@playwright/test";

// Testes de ponta a ponta da demo. Precisam do backend no ar (E2E_API_URL, padrão http://localhost:8080).
// O frontend sobe sozinho com `npm run dev` se a porta 3000 estiver livre.
// Usa o Google Chrome instalado; para o Chromium do Playwright, rode com E2E_CHANNEL=chromium
// (depois de `npx playwright install chromium`).
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    channel: process.env.E2E_CHANNEL === "chromium" ? undefined : "chrome",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run dev",
    url: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
