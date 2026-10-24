# Mudanças sugeridas no frontend: duas filas

O backend agora tem duas filas (veja "Duas filas" no [DECISOES.md](../DECISOES.md)). **O frontend atual continua funcionando sem mudança nenhuma.** O botão "Baixar fila_priorizada.csv" já entrega o arquivo oficial. Mesmo assim, a tela deveria deixar a diferença visível. Estas são as mudanças, por arquivo e por responsável.

## O que o backend já entrega

| Onde | O quê |
|---|---|
| `GET /api/fila` | Campo novo `snapshot`: `{ tipo: "operacional", versao: "op-12", calculadoEm, eventosRegistrados, decisoesRegulador, descricao }` |
| Toda resposta da API | Cabeçalho `X-FilaViva-Snapshot: operacional; versao=op-12; calculado=…` (liberado no CORS) |
| `GET /api/status` | Campo novo `snapshots`: `{ operacional: {...}, oficial: {...} }` |
| `GET /api/fila/export.csv` | Padrão: fila de referência, arquivo `fila_priorizada.csv` |
| `GET /api/fila/export.csv?snapshot=operacional&versao=op-12` | A fila da tela; `409` se a versão mudou |
| Reavaliação | Grupos novos `REVISAO_URGENTE` ("REVISÃO CLÍNICA URGENTE: possível urgência descrita só em texto", sempre o primeiro; vale destacar em vermelho) e `DIVERGENCIA_VERIFICADOR` ("Urgência que o verificador oficial não reconhece"). A tela de Alertas já mostra grupos novos sem mudança |

## Mudanças por arquivo

### `frontend/src/lib/api.ts` (Dev 3)

1. Adicionar o tipo e o campo:
   ```ts
   export interface SnapshotInfo {
     tipo: "operacional" | "oficial" | "simulacao";
     versao: string;          // "op-12" ou "ref-3e37e2b31749"
     calculadoEm: string;     // ISO
     eventosRegistrados: number;
     decisoesRegulador: number;
     descricao: string;
   }
   ```
   E incluir `snapshot: SnapshotInfo` em `Pagina<T>`, mais `snapshots?: Record<"operacional" | "oficial", SnapshotInfo>` em `Status`.
2. Trocar `exportCsvUrl` por:
   ```ts
   exportOficialUrl: `${API_URL}/api/fila/export.csv?snapshot=oficial`,
   exportOperacionalUrl: (versao: string) => `${API_URL}/api/fila/export.csv?snapshot=operacional&versao=${versao}`,
   ```

### `frontend/src/components/fila/tela-fila.tsx` (Dev 4)

1. Abaixo do título "Fila cirúrgica priorizada", mostrar um selo com `pagina.snapshot`. Exemplo: "Fila operacional · v op-12 · recalculada às 03:21 · 3 eventos novos · 1 decisão", com a frase `snapshot.descricao` em texto pequeno.
2. Trocar o botão de download por dois:
   - **"Fila de referência — entrega oficial"** → `api.exportOficialUrl`, com dica: "fila_priorizada.csv: só o dataset; é o arquivo da banca";
   - **"Baixar esta fila (operacional)"** → `api.exportOperacionalUrl(pagina.snapshot.versao)`, com dica: "inclui eventos e decisões registrados; não é o entregável e pode divergir do verificador oficial".
3. Se o download operacional responder 409, mostrar a mensagem da API e recarregar a fila.

### `frontend/src/components/fila/tela-paciente.tsx` (Dev 4)

O selo "★ Posição limitada pela regra de ouro" continua igual. Sugestão: quando houver a reavaliação `DIVERGENCIA_VERIFICADOR`, mostrar um aviso amarelo no cabeçalho: "Urgência reconhecida pelo FilaVIVA que o verificador oficial não reconhece; no arquivo de entrega este paciente fica abaixo dos oncológicos."

### `frontend/src/components/painel/indicadores.tsx` (Dev 5)

Mostrar no topo de qual fila são os números: "Fila operacional · v op-12". A informação vem do cabeçalho `X-FilaViva-Snapshot` ou de `/api/status`.

## Como conferir

Com tudo no ar: `python scripts/teste_integracao.py` (42 verificações), depois `npm run build` e `npm run lint` em `frontend/`.
