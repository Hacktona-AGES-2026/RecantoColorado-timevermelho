# Tarefas por pessoa

Divisão em 5 frentes de tamanho parecido (~14 h de trabalho cada, incluindo testes e revisão).
Cada frente mexe em **arquivos diferentes**, então todos podem trabalhar em paralelo sem conflito.
As regras clínicas que todos seguem estão na seção 2.1 do [PLANO.md](PLANO.md).

| Pessoa | Frente | Pasta principal |
|---|---|---|
| Dev 1 | Motor de priorização | `backend/.../motor/` |
| Dev 2 | Dados e persistência | `backend/.../dominio/`, `backend/.../importacao/` |
| Dev 3 | Texto livre, alertas e indicadores | `backend/.../motor/texto/`, `backend/.../servico/` (alertas, indicadores, export), `regras.json` |
| Dev 4 | API REST + tela Fila e Paciente | `backend/.../api/`, `frontend/src/app/(fila, paciente)` |
| Dev 5 | Telas Simulador, Alertas, Indicadores + pitch | `frontend/src/app/(simulador, alertas, indicadores)`, `docs/` |

---

## Dev 1 — Motor de priorização
- [x] `Sinal`, `TipoSinal`, `ClasseSwalis` (A1…D com prazo) e `Pesos`
- [x] `ExtratorDeSinais`: sinais das colunas (CID, dor, limitação, ASA, risco) e dos eventos (biópsia, PS, internação, imagem, reavaliação, contato, HbA1c)
- [x] `ClassificadorSwalis`: regra mais alta vence; todo oncológico no mínimo A2; dado faltando não rebaixa para D
- [x] `CalculadoraScore`: 6 componentes com pesos ajustáveis, cada um com texto explicativo
- [x] `OrdenadorFila`: classe → % do prazo consumido → score → data de entrada
- [x] `GuardRegraDeOuro`: mesma definição de "sem urgência" do verificador oficial; sobe oncológicos que ficaram abaixo
- [x] Testes unitários dos casos-chave

## Dev 2 — Dados e persistência
- [x] Entidades JPA: `Procedimento`, `Solicitacao`, `EventoClinico`, `AjusteRegulador`, `Alerta`
- [x] Repositórios Spring Data
- [x] `ImportadorDataset` no startup: lê os 4 CSVs com commons-csv
- [x] Limpeza: CID sem ponto e maiúsculo, prioridade sem acento, município em Title Case, números vazios
- [x] Eventos duplicados (mesmo conteúdo) e órfãos (solicitação fora da fila)
- [x] Solicitações duplicadas (mesmo paciente + procedimento): mantém a mais antiga, as outras viram `DUPLICADA` e os eventos passam para a principal
- [x] Gerador de eventos sintéticos para a demo

## Dev 3 — Texto livre, alertas, indicadores e entregável
- [x] `regras.json`: palavras de alarme (graves, suspeita de malignidade, funcionais), CIDs urgentes, padrões de saída e de piora
- [x] `ExtratorDeTexto`: busca termos em `justificativa_clinica` e `observacoes`, ignora negações ("sem", "nega", "ausência de"), guarda o trecho como evidência
- [x] Saídas sugeridas: desistiu / operou em outro serviço / convênio (nunca remove oncológico)
- [x] Lista de reavaliação: prazo estourado, esquecido (> 2× prazo), flag divergente, dado incompleto, sem contato, prioridade manual divergente
- [x] Indicadores antes × depois (ordem atual × FilaVIVA)
- [x] Export `fila_priorizada.csv` + rodar o verificador oficial
- [x] `DECISOES.md`

## Dev 4 — API REST + telas Fila e Paciente
- [x] `FilaService`: mantém a fila calculada em memória e recalcula a cada mudança
- [x] Endpoints: `GET /api/fila`, `GET /api/pacientes/{id}`, `POST /api/eventos`, `POST /api/simulacao`, `POST /api/simulacao/pesos`, `POST /api/ajustes`, `GET /api/alertas`, `GET /api/indicadores`, `GET /api/fila/export.csv`
- [x] Tela **Fila**: tabela com classe, barra de prazo, mudança de posição, filtros e busca
- [x] Tela **Paciente**: por que está nessa posição, evidências, componentes do score, linha do tempo, ações do regulador (confirmar / mudar classe / confirmar ou recusar saída) com justificativa obrigatória

## Dev 5 — Telas Simulador, Alertas, Indicadores + pitch
- [x] Navegação e layout comum
- [x] Tela **Simulador**: "e se?" para um paciente (dor, limitação, evento hipotético) e para os pesos da fila
- [x] Tela **Alertas**: mudanças recentes, reavaliações pendentes, saídas sugeridas
- [x] Tela **Indicadores**: antes × depois com gráficos
- [ ] Wiki do GitHub, slides e roteiro do pitch, ensaio cronometrado (fazer no sábado à tarde)
