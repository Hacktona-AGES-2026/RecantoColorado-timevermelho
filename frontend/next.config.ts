import type { NextConfig } from "next";

// O navegador chama /api/... no próprio Next, que repassa para o backend. Assim funciona em qualquer porta
// ou endereço (localhost, 127.0.0.1, IP da rede) sem depender de CORS.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  /* config options here */
  // Em dev o Next só libera os próprios recursos (hidratação, HMR) para localhost. Sem isto, abrir por
  // 127.0.0.1 ou pelo IP da rede carrega a página "morta" (botões não respondem).
  allowedDevOrigins: ["127.0.0.1", "10.*.*.*", "172.*.*.*", "192.168.*.*"],
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
