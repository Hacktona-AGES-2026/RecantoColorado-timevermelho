# FilaVIVA — Plano de execução (Hackatona 2026 · Desafio 1 Fin-x)

> **Pergunta:** quem precisa operar primeiro — e como defendemos essa resposta?
> **Nossa resposta:** uma plataforma de apoio ao **regulador** que classifica cada paciente numa classe clínica (SWALIS adaptado), ordena a fila por risco de estourar o prazo da classe, **explica cada posição**, reage a cada evento novo, permite **simular** mudanças e deixa a **decisão final com o humano**.

Prazos que não mudam:

| Quando | O quê |
|---|---|
| Sex 23:30 | Início do desenvolvimento |
| **Sáb 04:00** | `fila_priorizada.csv` v0 gerado (protótipo Python) — tira o risco do caminho |
| **Sáb 08:00** | Checkpoint café: backend + front ponta a ponta com os dados reais |
| **Sáb 14:00** | **Feature freeze.** `fila_priorizada.csv` final passando no verificador |
| **Sáb 18:00** | Repositório **público** no GitHub, com wiki, README, DECISOES.md |
| Sáb 19:00 | Pitch de **5 minutos** (problema → abordagem → demo com paciente real → decisão difícil) |

---

## 1. O que aprendemos com os dados (usar no pitch)

| Fato | Número |
|---|---|
| Solicitações na fila | **941** (907 pacientes) |
| Oncológicos pelo CID (C*) | **177** — e **34 deles estão com prioridade "baixa"** |
| Flag oncológico errada (não bate com o CID) | **44** |
| Biópsia positiva com CID ainda benigno/incerto | **23** (ex.: `SOL-2026-00351`, CID D44.0, prioridade baixa, laudo "NEOPLASIA MALIGNA") |
| Esperando mais de 1 ano | **353** (máximo: 1.550 dias) |
| Pares paciente+procedimento duplicados | **22** |
| Eventos apontando para solicitações fora da fila | **9** |
| Contatos "desistiu" / "operou em outro serviço" | 13 / 5 (+ observações "operou pelo convênio") |
| Prioridade com grafia suja (`Alta`, `Média`, vazia) | 34 |
| CID sem ponto (`K409`) | 32 |
| Campos vazios: dor / limitação / ASA | 38 / 20 / 26 |

**Pacientes para a demo do pitch:**
- `SOL-2026-00351` — CID D44.0 (benigno/incerto), prioridade **baixa**, biópsia **positiva** em 13/09. Hoje fica no meio da fila; no FilaVIVA vira **A2** e sobe para o topo, com a evidência citada.
- `SOL-2025-00048` — carcinoma de mama (C50.4), prioridade **baixa**, 607 dias esperando, "relata piora por telefone".
- Um "alta" sem urgência (ex.: catarata/varizes com dor 0–2, limitação nenhuma) que **desce**, com justificativa e confirmação do regulador.

---

## 2. Decisões de produto (fechar na 1ª hora, não reabrir)

- **Usuário principal:** o **regulador** da fila cirúrgica.
- **Momento:** a fila é **recalculada a cada evento novo** (biópsia, PS, internação, exame, contato) e o regulador faz uma **revisão diária** da tela de alertas.
- **Método:** regras clínicas explícitas (sem caixa-preta). LLM só como **leitor opcional** de texto livre, sempre propondo com evidência citada para o humano confirmar.
- **Humano com a última palavra:** o sistema **propõe**; o regulador **confirma, ajusta ou recusa**, com justificativa obrigatória e trilha de auditoria. Oncológico **nunca** pode ser rebaixado abaixo de um não urgente (nem manualmente).

### 2.1 Motor de priorização (contrato entre todos)

**Passo 1 — Normalização** (no import): CID maiúsculo sem ponto; prioridade minúscula sem acento; município em *Title Case*; `oncologico = CID começa com C` (ignorar a flag); eventos duplicados (mesmo conteúdo) removidos; eventos órfãos registrados e ignorados; eventos ligados por `paciente_id` também alcançam a outra solicitação do mesmo paciente.

**Passo 2 — Sinais extraídos** (cada sinal guarda **a evidência**: coluna, evento ou trecho do texto):
- `ONCO_CID`, `BIOPSIA_POSITIVA`, `PROGRESSAO_IMAGEM`, `PS_RECENTE` / `INTERNACAO_RECENTE` (≤180 dias), `REAVALIACAO_PIORA`, `RELATA_PIORA` (contato/observação), `HBA1C_SUBINDO` / `HBA1C_>8,5`, `CID_INCERTO` (D37–D48, R91), `CID_URGENTE` (I20.0, K40.0, K40.3, K42.0, K43.0, K80.0, N13.2, I83.2), `ALARME_TEXTO` (dicionário de palavras: encarcer, estrangul, colangite, icterícia, sepse, perda ponderal, disfagia, hemoptise, angina em repouso, síncope, crescimento rápido, retenção urinária aguda… — começar pela lista do `verificar_fila_priorizada.py`), `DOR` (EVA), `LIMITACAO`, `ASA`, `RISCO_CIRURGICO`, `DIAS_EM_FILA`.
- Sinais de **saída sugerida**: `DESISTIU`, `OPEROU_OUTRO_SERVICO`, `DUPLICADA`. Nunca removem sozinhos: vão para a fila de confirmação do regulador.

**Passo 3 — Classe SWALIS** (a regra mais alta que bater vence):

| Classe | Prazo | Regra |
|---|---|---|
| **A1** | 15 d | Oncológico (CID C* ou biópsia +) **com** progressão em imagem, PS/internação recente ou alarme no texto; **ou** CID urgente; **ou** alarme grave no texto |
| **A2** | 21 d | CID C* ou biópsia positiva (piso de todo oncológico) |
| **B** | 60 d | Dor ≥ 7, limitação grave, PS/internação recente, reavaliação com piora, CID incerto, relata piora |
| **C** | 180 d | Dor 4–6 ou limitação moderada |
| **D** | 360 d | O resto |

Dado faltando (dor/limitação vazias) **não rebaixa**: marca `DADO_INCOMPLETO` e entra na lista de reavaliação.

**Passo 4 — Ordem da fila:**
1. Classe (A1 > A2 > B > C > D) — **tempo de espera nunca passa por cima de urgência clínica**.
2. Dentro da classe: **% do prazo consumido** (`dias_em_fila / prazo`) — quem está mais perto de estourar (ou já estourou) vai primeiro.
3. Desempate: score de 0–100 (componentes abaixo) e depois data de entrada.

**Score explicável (só para desempate e explicação):** pesos iniciais, ajustáveis na tela do regulador
`diagnóstico 30 · sinais de piora 25 · dor 15 · limitação 10 · tempo de espera relativo 15 · ASA/risco 5`.
Cada componente aparece separado na tela ("+25 biópsia positiva em 13/09").

**Passo 5 — Trava de segurança (regra de ouro):** depois de ordenar, um *guard* percorre a fila e garante que **nenhum CID C\* fique abaixo do primeiro paciente sem urgência clínica** (mesma definição conservadora do verificador). Se algum ajuste manual ou peso quebrar isso, a API recusa com erro claro. Teste unitário obrigatório + o verificador oficial roda antes de cada entrega.

**Passo 6 — Anti-esquecimento:** lista de **reavaliação** com quem estourou o prazo da classe, quem passou >2× o prazo, quem tem flag manual divergente do dado, quem tem dado incompleto, quem não atende contato há meses, e quem a prioridade manual diverge muito da classe calculada.

**Saída `fila_priorizada.csv`:** pacientes ativos; justificativa em uma frase, ex.:
`"A2 · biópsia positiva (13/09/2026) com CID benigno no cadastro · 180 dias de fila (857% do prazo de 21 dias)"`.
Fora do arquivo (explicado no DECISOES.md): duplicatas mescladas (mantém a data de entrada mais antiga) e desistências/cirurgia em outro serviço **não oncológicas**. Oncológico com desistência fica na fila com alerta "confirmar desistência" (escolha segura).

---

## 3. Arquitetura e stack

```
filaVIVA/
├── backend/            Spring Boot 4.1.2 · Java 25 · Maven (projeto "timevermelho" já gerado)
├── frontend/           Next.js (App Router, TypeScript, Tailwind, shadcn/ui, Recharts)
├── data/               dataset original (CSV) + verificar_fila_priorizada.py
├── prototype/          protótipo Python do motor (referência e plano B)
├── docs/               DECISOES.md, roteiro do pitch, imagens
├── docker-compose.yml  PostgreSQL 16 (+ backend opcional)
├── fila_priorizada.csv entregável comum
└── README.md           como rodar numa máquina limpa
```

**Backend** — pacote `com.hackatona.timevermelho`:
- `importacao/` leitura dos CSVs no startup (`ApplicationRunner`), limpeza, dedup. Adicionar `org.apache.commons:commons-csv` ao `pom.xml`.
- `dominio/` entidades JPA: `Solicitacao`, `Paciente`, `EventoClinico`, `Procedimento`, `Sinal`, `Classificacao`, `AjusteRegulador` (auditoria), `Alerta`.
- `motor/` `ExtratorDeSinais`, `ClassificadorSwalis`, `CalculadoraScore`, `OrdenadorFila`, `GuardRegraDeOuro`, `GeradorJustificativa` — **classes puras, sem Spring**, fáceis de testar.
- `api/` controllers REST + DTOs (`record`).
- Atenção Spring Boot 4: starter é `spring-boot-starter-webmvc`; Jackson 3 (`tools.jackson`); todos precisam de **JDK 25** instalado. Configurar CORS para `http://localhost:3000`.
- `application.properties`: Postgres via variáveis de ambiente; `ddl-auto=create` (recria e reimporta a cada boot — simples e previsível para a demo).

**Contrato da API (fechar até 00:30; o front começa com mocks deste formato):**

| Método | Rota | Uso |
|---|---|---|
| GET | `/api/fila?classe=&especialidade=&busca=&page=` | Fila ordenada: posição, posição antiga (manual), delta, classe, % prazo, score, 3 principais motivos |
| GET | `/api/pacientes/{solicitacaoId}` | Detalhe: dados, sinais com evidência, componentes do score, linha do tempo de eventos, histórico de ajustes |
| POST | `/api/eventos` | Registra evento novo → recalcula → devolve `{posicaoAntes, posicaoDepois, classeAntes, classeDepois, motivo}` e cria alerta |
| POST | `/api/simulacao` | `{solicitacaoId, mudancas:{dor, limitacao, eventoHipotetico...}}` → nova posição e diff **sem salvar** |
| POST | `/api/simulacao/pesos` | Pesos novos → fila resultante e quem mais subiu/desceu, sem salvar |
| POST | `/api/ajustes` | Regulador confirma/ajusta/recusa com justificativa obrigatória (guard bloqueia violação da regra de ouro) |
| GET | `/api/alertas` | Mudanças de classe, eventos novos, reavaliações pendentes, saídas sugeridas |
| GET | `/api/indicadores` | Antes × depois: onco por terço da fila, prazos estourados por classe, divergência manual × calculada, por município/distância |
| GET | `/api/fila/export.csv` | Gera o `fila_priorizada.csv` |

**Frontend (Next.js)** — telas:
1. **Fila** — tabela com badge da classe, barra de % do prazo, seta ↑↓ com quantas posições mudou vs. a ordem atual, filtros.
2. **Paciente** — "por que ele está aqui": classe + regra que bateu, componentes do score, evidências citadas (trecho do texto destacado), linha do tempo dos eventos, botões **Confirmar / Ajustar / Recusar**.
3. **Simulador** — "e se a biópsia vier positiva?" / "e se a dor for 8?": mostra a nova posição antes de qualquer coisa acontecer.
4. **Alertas e reavaliação** — o que mudou desde ontem, quem estourou prazo, quem está sendo esquecido, saídas sugeridas para confirmar.
5. **Painel antes × depois** — números do pitch (onco no 1º terço, prazos estourados, divergências).

---

## 4. Divisão entre os 5 desenvolvedores

### Dev 1 — Tech lead + Motor de priorização (backend, domínio)
- Escrever o contrato do motor (seção 2.1) em código: `ExtratorDeSinais` (sinais estruturados e de eventos), `ClassificadorSwalis`, `CalculadoraScore`, `OrdenadorFila`, `GuardRegraDeOuro`, `GeradorJustificativa`.
- Testes unitários (JUnit) dos casos-chave: onco com prioridade baixa, biópsia + com CID benigno, dado vazio, regra de ouro com ajuste manual malicioso.
- Comparar a saída Java com o protótipo Python do Dev 3 (mesma ordem nos 50 primeiros).
- Revisar e fazer merge dos PRs do backend; decide disputas técnicas.

### Dev 2 — Backend: dados, persistência e API
- `docker-compose.yml` com Postgres; `application.properties`; CORS.
- Entidades JPA, repositórios, import dos CSVs com toda a limpeza (seção 2.1, passo 1), dedup e eventos órfãos.
- Controllers e DTOs do contrato da API; recálculo ao registrar evento; simulação (sem persistir); ajustes com auditoria; alertas; export CSV.
- Gerador de **eventos sintéticos** para a demo ao vivo (ex.: "chegou biópsia positiva agora").

### Dev 3 — Dados, regras clínicas e texto livre
- **Até 04:00:** protótipo Python (`prototype/priorizar.py`, só biblioteca padrão ou pandas) que gera o `fila_priorizada.csv` v0 e passa no `verificar_fila_priorizada.py`. É o plano B se o Java atrasar.
- Dicionário de alarmes e abreviações do texto livre (`justificativa_clinica` + `observacoes`), padrões de saída (desistiu, convênio, outro serviço) e mapeamento de CIDs urgentes — entregue como `regras.json` lido pelo backend.
- Números do pitch e lista de pacientes-exemplo (seção 1), indicadores antes × depois.
- **Extra se sobrar tempo (após 08:00):** "Agente leitor" com LLM (Claude API) que lê texto + eventos e propõe sinais com o trecho citado; aparece na tela do paciente como *sugestão pendente* que o regulador aceita ou recusa. Nunca altera a fila sozinho.

### Dev 4 — Frontend: base, Fila e Paciente
- Setup Next.js + Tailwind + shadcn/ui, layout, cliente da API com mocks no formato do contrato (troca para API real no checkpoint das 08:00).
- Tela **Fila** (tabela, filtros, badge, barra de prazo, delta de posição).
- Tela **Paciente** (explicação, evidências, componentes do score, linha do tempo, ações do regulador).

### Dev 5 — Frontend: Simulador/Alertas + Pitch e documentação
- Telas **Simulador**, **Alertas/Reavaliação** e **Painel antes × depois** (Recharts).
- A partir das 12:00: `README.md` (rodar em máquina limpa), `DECISOES.md`, **wiki do GitHub** (obrigatória pelas regras), roteiro e slides do pitch, ensaio cronometrado.
- Dono do relógio: lembra os checkpoints e o freeze das 14:00.

> Todos: commits pequenos e frequentes (o histórico do evento é avaliado). Branch por tarefa (`feat/motor-swalis`, `feat/front-fila`…), PR curto, merge na `main` pelo Dev 1 (backend) e Dev 4 (frontend).

---

## 5. Cronograma por blocos

| Bloco | Dev 1 Motor | Dev 2 API/Dados | Dev 3 Dados/Regras | Dev 4 Front base | Dev 5 Front features/Pitch |
|---|---|---|---|---|---|
| **23:30–00:30** Kickoff (todos juntos) | Ler seção 2, fechar regras e contrato da API | Monorepo, backend gerado em `/backend`, docker-compose, `main` rodando | Copiar dataset para `/data`, rodar exemplos do README | `create-next-app` em `/frontend`, shadcn, mocks | Ler critérios, esboçar telas no papel, criar wiki vazia |
| **00:30–04:00** MVP | Sinais estruturados + classificador + ordenação + testes | Entidades, import e limpeza, `GET /fila` e `GET /pacientes/{id}` | **Protótipo Python + CSV v0 no verificador** | Tela Fila com mocks | Tela Paciente-explicação (componentes) com mocks |
| **04:00–08:00** Integração (2 pessoas dormem ~3h em revezamento: Dev 3 e Dev 5 primeiro) | Score, justificativa, guard da regra de ouro, conferir com o protótipo | `POST /eventos` com recálculo, alertas, export CSV | `regras.json` (alarmes, saídas, CIDs) | Ligar Fila e Paciente na API real | Simulador (UI) com mocks |
| **08:00** Checkpoint café | **Demo interna ponta a ponta com o dataset inteiro.** Corrigir rota se algo estiver vermelho. | | | | |
| **08:00–12:00** Diferenciais | Texto livre no motor (usando `regras.json`), pesos ajustáveis | Simulação, ajustes + auditoria, eventos sintéticos | Indicadores antes × depois; LLM leitor (opcional) | Ações do regulador (confirmar/ajustar/recusar) | Alertas/Reavaliação + Painel antes × depois |
| **12:00** Almoço | | | | | |
| **12:30–14:00** Fechamento | Rodar verificador, corrigir bordas | Export final, seeds da demo | `fila_priorizada.csv` final + números do pitch | Polimento visual | README + DECISOES.md |
| **14:00 FREEZE** | Só correção de bug a partir daqui | | | | |
| **14:00–16:30** | Revisão de código / legibilidade | Script de subida limpa (`docker compose up`) testado do zero | Wiki: modelo de dados e regras | Testar todos os fluxos da demo | Slides + roteiro de 5 min |
| **16:30–18:00** | Repo público, último commit, tag `v1.0` | Gravar vídeo de backup da demo | Ensaio do pitch | Ensaio do pitch | **Ensaio cronometrado (3×)** |
| **18:00** | **Entrega do repositório** | | | | |

---

## 6. Roteiro do pitch (5 min)

1. **0:00–0:40 Problema** — "A fila mede o tempo, não a pessoa": 34 pacientes com câncer marcados como prioridade baixa; 23 com biópsia positiva e CID benigno.
2. **0:40–1:30 Nossa escolha** — usuário = regulador; classe SWALIS + prazo; recálculo a cada evento; humano decide. Regra de ouro garantida por trava + teste.
3. **1:30–3:30 Demo ao vivo** — abrir `SOL-2026-00351`: estava no meio da fila → mostrar o porquê da subida (biópsia citada) → simular um evento novo em outro paciente e ver a fila mudar → regulador ajusta um paciente "alta" sem urgência, com justificativa → mostrar o alerta de reavaliação de quem estava esquecido.
4. **3:30–4:20 Decisão difícil** — "tempo de espera nunca passa por cima da urgência clínica", e como tratamos os esquecidos (prazo da classe + lista de reavaliação). Mencionar: desistência de oncológico não remove, só alerta.
5. **4:20–5:00 Impacto e próximos passos** — painel antes × depois; voz do paciente (questionário) e LLM leitor como evolução.

---

## 7. Riscos e plano B

| Risco | Plano B |
|---|---|
| Java 25 / Spring Boot 4 / Lombok dando problema em alguma máquina | Quem não conseguir compilar trabalha no front ou no protótipo; backend roda na máquina do Dev 1/Dev 2 |
| Backend atrasado | Front lê o `fila_priorizada.csv` + JSON gerados pelo protótipo Python |
| Regra de ouro violada | Guard + teste + verificador oficial antes de cada merge na `main` |
| LLM lento ou sem chave | É extra: o motor de regras funciona sem ele |
| Wi-Fi ruim na hora do pitch | Rodar tudo local; vídeo de backup gravado às 17h |

## 8. Checklist de entrega

- [ ] Repositório público, histórico de commits do evento
- [ ] Wiki do GitHub preenchida
- [ ] `README.md` (rodar em máquina limpa) e `DECISOES.md` (abordagem, usuário, porquê, o que ficou de fora)
- [ ] `fila_priorizada.csv` passando no `verificar_fila_priorizada.py`
- [ ] Solução funcionando sobre os 941 registros
- [ ] Pitch ≤ 5 min ensaiado, com paciente concreto e decisão difícil
