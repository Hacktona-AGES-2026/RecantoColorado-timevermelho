# FilaVIVA — frontend

Telas do regulador: Fila, Paciente, Alertas e reavaliação, Simulador e Antes × depois.
Como rodar o projeto inteiro (banco, backend e frontend): veja o [README da raiz](../README.md).

## Rodar só o frontend

Precisa do backend no ar em `http://localhost:8080`. O navegador chama `/api/...` no próprio Next, que repassa para o backend (`next.config.ts`); backend em outro endereço: `BACKEND_URL=http://host:porta npm run dev`.

```bash
npm install
npm run dev      # http://localhost:3000
npm run build    # confere se compila
npm run lint
```

Não precisa de `.env.local`. `NEXT_PUBLIC_API_URL` (em `.env.example`) só serve para o navegador chamar uma API direto, sem o repasse.

## Onde fica cada coisa

| Caminho | O que é |
|---|---|
| `src/lib/api.ts` | Contrato da API: todos os tipos e o cliente. Espelha `backend/.../api/dto/Dtos.java`; se mudar um, mude o outro |
| `src/components/nav.tsx`, `fila-ui.tsx` | Navegação e peças comuns (selo da classe, barra de prazo, mudança de posição) |
| `src/app/page.tsx`, `src/components/fila/` | Telas da Fila e do Paciente |
| `src/app/simulador`, `alertas`, `indicadores`, `src/components/painel/` | Simulador, Alertas e Antes × depois |
| `src/components/ui/` | Componentes shadcn/ui (estilo base-nova, baseados em Base UI, não em Radix) |

## Atenção: Next.js 16

Esta versão tem mudanças em relação às anteriores (leia `AGENTS.md`). Com `cacheComponents` ligado, componentes que usam `usePathname` ou `useSearchParams` precisam estar dentro de `<Suspense>`.
