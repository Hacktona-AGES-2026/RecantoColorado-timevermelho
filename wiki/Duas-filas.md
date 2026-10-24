# Duas filas: operacional e de referência

O verificador oficial decide quem é "sem urgência" lendo **só o dataset de referência**. Ele não enxerga eventos
registrados depois, nem urgências que só aparecem no texto do médico. Por isso existem duas filas, sempre
identificadas.

| | **Fila operacional** | **Fila de referência — entrega oficial** |
|---|---|---|
| Para quem | O regulador e os médicos, na tela e na API | A banca: gera o `fila_priorizada.csv` |
| Dados | Dataset + eventos e decisões registrados | Só o dataset, sem eventos novos nem decisões |
| Recalcula | A cada evento ou decisão | Uma vez, depois de importar o dataset; reproduzível |
| Versão | `op-N` | `ref-<hash dos CSVs e do regras.json>` |
| Passa no verificador? | Sim sobre o dataset. Com eventos novos, o verificador não os enxerga; a regra de ouro vale pelos mesmos critérios sobre todos os eventos | **Sim** |

## Como saber de qual fila é um dado

- Cabeçalho `X-FilaViva-Snapshot` em toda resposta (ex.: `operacional; versao=op-12; calculado=…`).
- Campo `snapshot` nas respostas da fila e das listas dos médicos.
- `GET /api/status` traz os dois snapshots.
- A tela da fila mostra o aviso "Fila operacional — atualizada com novos eventos clínicos".

## Downloads

| Botão / rota | Arquivo |
|---|---|
| "Exportar CSV oficial — Banca" · `GET /api/fila/export.csv` | `fila_priorizada.csv` (referência) |
| "Exportar CSV operacional" · `?snapshot=operacional&versao=op-N` | `fila_operacional_op-N_<horário>.csv`; HTTP 409 se a fila mudou desde que a tela carregou |

## Proteção na subida

Se a API receber um pedido antes de terminar de importar o dataset, a fila de referência **não** é fixada vazia.
Ela só é calculada com o dataset carregado; antes disso, o pedido recebe uma mensagem para tentar de novo. Essa
correção impede um `fila_priorizada.csv` gravado só com o cabeçalho.

Simulações não mexem em nenhuma das duas filas.
