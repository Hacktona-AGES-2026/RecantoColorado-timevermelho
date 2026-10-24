# API

Base: `http://localhost:8080/api`. Erros voltam como `{"mensagem": "..."}` em português
(400 campo inválido, 404 não encontrado, 409 conflito, 422 regra de negócio).
Contrato dos tipos espelhado em `frontend/src/lib/api.ts`.

## Fila e pacientes

| Método | Rota | O que faz |
|---|---|---|
| GET | `/status` | Saúde da API e os dois snapshots (operacional e oficial) |
| GET | `/fila` | Fila operacional paginada. Filtros: `classe`, `especialidade`, `busca`, `status`, `oncologico`, `pagina`, `tamanho` |
| GET | `/fila/export.csv` | `snapshot=oficial` (padrão, `fila_priorizada.csv`) ou `snapshot=operacional` com `versao=op-N` opcional (409 se mudou) |
| GET | `/pacientes/{solicitacaoId}` | Detalhe: dados, classificação, score, sinais, eventos, decisões, reavaliações |
| POST | `/eventos` | Registra um evento clínico novo e devolve o impacto na fila |
| POST | `/ajustes` | Decisão do regulador (justificativa obrigatória): `CONFIRMAR_CLASSE` (mantém a vigente), `ALTERAR_CLASSE` (classe menos urgente vira sugestão), `SUGERIR_REBAIXAMENTO`, `CONFIRMAR_REBAIXAMENTO`, `REJEITAR_REBAIXAMENTO`, `CONFIRMAR_SAIDA`, `RECUSAR_SAIDA`. Rebaixar oncológico: 422 |
| POST | `/simulacao` | "E se?" para um paciente, sem salvar |
| POST | `/simulacao/pesos` | Fila com outros pesos de score, sem salvar |
| GET | `/alertas` | Alertas, grupos de reavaliação, saídas sugeridas e `rebaixamentos` (pendentes primeiro) |
| GET | `/indicadores` | Antes × depois |
| POST | `/demo/cenario` | Registra eventos sintéticos de demonstração |
| POST | `/demo/reset` | Volta ao estado do dataset |

## Médicos

| Método | Rota | O que faz |
|---|---|---|
| GET | `/especialidades` | Catálogo com `pacientesCirurgicos` e `pacientesPossivelAvaliacao` de cada especialidade |
| GET | `/medicos` | Médicos cadastrados |
| GET | `/medicos/{id}` | Um médico |
| POST | `/medicos` | Cadastra `{nome, crm, especialidade}` (201; 409 CRM repetido; 422 especialidade inexistente) |
| DELETE | `/medicos/{id}` | Remove (204) |
| POST | `/medicos/{id}/ajustes` | O médico muda a prioridade: `{solicitacaoId, tipo (ALTERAR_CLASSE, CONFIRMAR_CLASSE, SUGERIR_REBAIXAMENTO, FIXAR_POSICAO, LIBERAR_POSICAO), classeNova, posicao, justificativa, informacaoForaDoSistema}`. Paciente fora das listas dele ou tipo do regulador: 422 |
| GET | `/medicos/{id}/ajustes` | Auditoria: o que o médico alterou, do mais recente para o mais antigo |
| GET | `/medicos/{id}/fila` | Lista do médico. `grupo=CIRURGICO` (padrão) ou `POSSIVEL_AVALIACAO`; filtros `busca`, `classe`, `pagina`, `tamanho` |

Cada item da lista do médico traz `posicaoNaLista`, `grupo`, `motivosEspecialidade` (por que está na lista) e
`item`, que é exatamente o item da fila geral.
