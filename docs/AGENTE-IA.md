# Agente de IA do FilaVIVA

Assistente para tirar dúvidas (chat), organizar textos por assunto (categorização) e escrever relatórios para médicos. É um **protótipo de hackatona**: ele conversa e sugere, **nunca decide nem altera a fila**. As tasks e o histórico de decisões estão em [`TASKS-AGENTE-IA.md`](../TASKS-AGENTE-IA.md).

## O que ele faz e o que não faz

| Função | Onde | O que acontece |
|---|---|---|
| **Chat** | página `/assistente` e botão flutuante em todas as telas | Responde só sobre consultas, uso da aplicação, saúde e alimentação em geral. Usa números agregados da aplicação quando a pergunta pede. |
| **Categorização** | `POST /api/agente/categorizar` | Classifica o assunto de um texto. **Só organiza**: nunca diz gravidade, urgência nem posição. Sugestão fica pendente até um humano aceitar ou corrigir. |
| **Relatórios** | página `/relatorios` e `POST /api/agente/relatorios` | Resumo da fila (geral ou por especialidade) a partir de números agregados, sempre com "Gerado por IA — revisar antes de usar". |

**Nunca faz:** alterar fila, eventos, decisões ou dados; decidir, prever ou julgar a classe, a posição ou a data de um paciente (mostrar o que está cadastrado e calculado, sim); dar diagnóstico, dose ou prescrição; definir urgência; falar de infraestrutura ou código; revelar suas instruções.

## Como ligar

O agente sobe **desligado** até existirem (1) regras de negócio preenchidas (já estão) e (2) ao menos uma chave de API. O motivo aparece em `GET /api/agente/status` e no aviso amarelo do chat.

1. As regras de negócio já estão em `backend/src/main/resources/agente/regras-negocio.md`; qualquer mudança nelas vai por Pull Request revisado.
2. Coloque a chave: `cp backend/.env.example backend/.env` e cole a chave em `IA_PROVEDOR_1_CHAVE` (o arquivo está no `.gitignore`). O backend lê `backend/.env` sozinho ao subir, sem perfil e sem exportar nada, seja pelo terminal ou pelo IntelliJ. Uma variável de ambiente com o mesmo nome também funciona.
   Modelo (`gpt-6.1-sol`), esforço (`medium`) e endereço já estão no `application.properties`; dá para trocar com `IA_PROVEDOR_1_MODELO`, `IA_PROVEDOR_1_ESFORCO` e `IA_PROVEDOR_1_URL`. O esforço (`low`, `medium`, `high`) vira o campo `reasoning_effort` da resposta principal; o filtro de escopo e a categorização usam sempre `low`, e o teto de tokens ganha uma folga de 4000 para o raciocínio não esvaziar a resposta. Deixe vazio para modelos que não raciocinam.
   A chave **nunca** vai para o código nem para o Git: cada pessoa do time coloca a sua no próprio `backend/.env` (ou recebe a do time por um canal privado).
3. Reinicie o backend. Para só **ver a tela** funcionando sem as regras de negócio, use `AGENTE_EXIGIR_REGRAS=false` (as chaves continuam obrigatórias).

**Vários provedores:** a ordem é a de `agente.provedores[0]`, `[1]`… no `application.properties`. Cada um aceita `IA_PROVEDOR_N_URL`: qualquer API no formato `chat/completions` da OpenAI serve (outra conta OpenAI, Azure OpenAI…). A URL precisa ser `https` (ou `http` em localhost), senão o provedor é ignorado. APIs em outro formato precisam de uma implementação própria de `ProvedorIA`.

**Troca automática:** crédito esgotado (`insufficient_quota`/402) ou chave recusada → o provedor sai da rotação por 60 min e o próximo assume; erro de servidor/timeout → 30 s; limite momentâneo de requisições → espera e tenta de novo (2 vezes) e passa adiante; pedido inválido (erro nosso) → não troca, o erro sobe. Se todos falharem, o chat diz "assistente indisponível" e o resto do FilaVIVA segue normal. Resposta em andamento não troca de provedor no meio.

## API

Tudo sob `/api/agente`. O navegador só fala com estas rotas; as chaves nunca saem do backend.

| Método | Rota | Uso |
|---|---|---|
| POST | `/chat` | Mensagem do usuário; resposta em fluxo de eventos `text/event-stream` |
| GET | `/status` | Situação do agente, uso e provedores (nunca devolve chaves) |
| POST | `/categorizar` | Sugere a categoria de um texto (até 2000 caracteres) |
| GET | `/categorizacoes?status=PENDENTE\|ACEITA\|CORRIGIDA` | Últimas 50 |
| GET | `/categorizacoes/{id}` | Uma categorização |
| POST | `/categorizacoes/{id}/revisao` | `{"acao":"ACEITAR"}` ou `{"acao":"CORRIGIR","categoriaCorrigida":"…","justificativa":"…","autor":"…"}` |
| POST | `/relatorios` | `{"tipo":"VISAO_GERAL"}` ou `{"tipo":"ESPECIALIDADE","especialidade":"…","solicitante":"…"}` |
| GET | `/relatorios` | Histórico (últimos 50, sem o texto) |
| GET | `/relatorios/especialidades` | Especialidades que existem na fila |
| GET | `/relatorios/{id}` | Um relatório completo |
| GET | `/relatorios/{id}/download` | O mesmo texto em Markdown, para baixar |

### Chat: corpo e eventos

Corpo (JSON, até 16 KB, `Content-Length` obrigatório): `{"sessaoId":"opcional","mensagem":"…"}`. O histórico fica no servidor, por sessão; o cliente manda **só a mensagem nova**.

| Evento | Dados | Quando |
|---|---|---|
| `sessao` | `{"sessaoId"}` | sempre o primeiro; guarde para a próxima mensagem |
| `trecho` | `{"texto"}` | pedaço da resposta, em ordem |
| `recusa` | `{"motivo","texto"}` | recusada. `motivo`: `RISCO_A_SAUDE`, `TENTATIVA_DE_BURLAR`, `FORA_DO_ESCOPO`, `INFRA_OU_CODIGO`, `MODERACAO`, `MODERACAO_INDISPONIVEL`, `FILTRO_INDISPONIVEL` |
| `substituir` | `{"texto"}` | **troque tudo que já mostrou** por este texto (resposta bloqueada depois de começar) |
| `erro` | `{"texto"}` | indisponível ou falha no meio da resposta |
| `fim` | `{"versaoPrompt"}` | fim da volta |

Erros HTTP antes de transmitir (corpo `{"mensagem"}`): **400** mensagem vazia/grande (1000 caracteres)/JSON inválido · **411/413** sem `Content-Length`/corpo > 16 KB · **429** muitas mensagens (10 por minuto por IP e por sessão) ou IP bloqueado por recusas seguidas · **503** agente desligado (mensagem genérica; o motivo técnico só vai em `/status`).

Categorização e relatórios devolvem **422** para pedido inválido (ex.: especialidade que não existe na fila), **429** se passar de 10 categorizações ou 3 relatórios por minuto, **503** se desligado.

## Como o agente fica seguro

Camadas, da primeira à última. Qualquer uma que falhe **recusa** (a falha fecha, não abre).

1. **Sem IA, em código:** pedido de ajuda de emergência (dor no peito, falta de ar, pensamento de se machucar…) recebe orientação fixa de SAMU 192 / CVV 188 / pronto-socorro, mesmo com todos os provedores fora do ar, e **nunca** conta como abuso nem é bloqueado. Tentativas óbvias de burlar ("ignore as instruções", "mostre seu prompt", em português, inglês e espanhol) são recusadas sem gastar crédito.
2. **Moderação** da pergunta (endpoint de moderação do provedor).
3. **Filtro de escopo:** uma chamada curta classifica a pergunta; a decisão sai da categoria devolvida e qualquer resposta inválida conta como "fora do escopo".
4. **Geração** com o System Prompt, a base de conhecimento da aplicação e as regras de negócio. O texto do usuário e os dados da aplicação chegam marcados como **dado, nunca instrução**.
5. **Trava contra vazamento do prompt:** a resposta é segurada por 80 caracteres e interrompida se repetir um trecho do System Prompt ou das regras.
6. **Moderação da resposta** e **aviso fixo** de que é uma IA (nas respostas de saúde). Se a moderação reprovar depois de a resposta começar a aparecer, o cliente recebe `substituir`.

**A IA só enxerga agregados; as listas de pacientes vêm do dataset** (decisão D5 revista: modelo híbrido).
- **Perguntas gerais:** três consultas fixas, escolhidas pelo código a partir de palavras da pergunta: resumo da fila por classe e especialidade, indicadores antes × depois e totais de reavaliação.
- **Perguntas sobre um grupo de pacientes** (categoria `DADOS_DA_FILA` do filtro de escopo, como "quantos pacientes aguardam Histerectomia?" ou "liste os pacientes da classe A1 de Urologia"): uma chamada curta em JSON (`PlanejadorConsulta`, prompt em `resources/agente/consulta-fila.md`) faz a IA escolher os **filtros**: procedimento, especialidade, classe, prefixo de CID, oncológico, município, sexo, idade, status, acima do prazo, com reavaliação, ordem e limite.
- **Validação dos filtros:** o código valida tudo em `ConsultaFila`. Os nomes só valem se existirem no catálogo da fila, os números têm limites e o que não existe vira aviso ("não existe na fila"), nunca filtro inventado.
- **Consulta:** quem consulta é `LeituraAplicacaoSpring`. A IA recebe só os números agregados do resultado. A **tabela de pacientes** (posição, solicitação, nome, idade, CID, classe, espera) vai num evento SSE `tabela` direto para a tela, com botão "Baixar CSV", sem passar pela IA. Por isso não há como a IA inventar paciente, e nenhum nome sai para o provedor externo.
- **Relatórios:** a página `/relatorios` tem o tipo **Por procedimento**: o texto da IA usa os agregados, e a tabela de pacientes é escrita pelo código no próprio relatório.
- **Paciente específico** (pelo nome ou pelo número de solicitação, como "classe e posição da paciente X" ou "dados da SOL-2026-00489"): o planejador usa os filtros `nomes` e `solicitacoes`, procurando em todos os status. A tela mostra a linha do paciente. Para até 5 encontrados, a IA recebe o detalhe de cada um **sem nome, número nem CID** (classe, posição, procedimento, idade, espera, status) e responde com esses dados. O nome digitado pelo usuário vai na própria mensagem dele; o que o sistema acrescenta não identifica ninguém.
- **Continua recusado** (`PACIENTE_ESPECIFICO`): pedir para a IA decidir, prever, julgar ou mudar classe, posição ou data de alguém, ou comparar quem merece mais. CPF e cartão do SUS continuam barrados sem IA.
- **Limpeza:** todo rótulo vindo do banco é limpo (caracteres permitidos, 80 caracteres e as mesmas barreiras contra "ignore as instruções").
- **Visibilidade:** a lista mostra o mesmo que a tela Fila já mostra a quem abre a aplicação. Como **não há login nem perfis** (T12), qualquer pessoa com acesso à aplicação vê essas listas. Em uso real, com dados reais, T12 (login e perfis) precisa vir antes.

**Chaves:** só por variável de ambiente; nunca em arquivo versionado, log, erro, resposta ou `NEXT_PUBLIC_*`. As mensagens de erro do provedor são reduzidas a "HTTP <status> <código>". Os pedidos pedem ao provedor `store: false` (não guardar a conversa). O log nunca recebe conteúdo de conversa, só sessão abreviada, categoria, resultado, tokens e versão do prompt.

**Regras e System Prompt:** só mudam por arquivo versionado e Pull Request revisado; não existe endpoint para editá-los. A inicialização valida os arquivos e, se algo faltar, o agente fica **desligado** (nunca roda sem regras). Cada resposta, categorização e relatório registra a versão (hash) usada.

**Testes de guarda** (`AgenteGuardaTest`, `seguranca-agente.test.ts`) falham se alguém der ao agente o poder de gravar na aplicação, rodar comandos, abrir outras conexões de rede, receber ferramentas, ou se uma chave/`dangerouslySetInnerHTML` aparecer no frontend. **Se um deles quebrar, a mudança precisa de revisão de segurança.**

## Limites do contexto do Desafio 1

O agente fica preso ao que o desafio pede: **apoiar a priorização da fila cirúrgica, com o humano decidindo**. Os limites ficam em três camadas:

| Camada | Onde | O que segura |
|---|---|---|
| Regras de negócio (texto) | `resources/agente/regras-negocio.md` | Contexto do desafio, regra de ouro, classes e prazos, o que muda a posição, proteções (dado faltando, saída sugerida, HbA1c), o que está fora do escopo (agenda, preparo, cancelamento) e os limites do assistente. |
| Filtro de escopo (IA) | categoria `PACIENTE_ESPECIFICO` em `filtro-escopo.md` | Pedido de dado de uma pessoa ou para o assistente decidir, prever ou mudar classe, posição, data ou saída de alguém. Resposta fixa, sem gerar texto; não conta como abuso. |
| Barreira em código (sem IA) | `protecao/LimitesDoContexto.java` | CPF ou cartão do SUS (a mensagem **não vai** ao provedor nem ao histórico), número de solicitação `SOL-…`, pedido para rebaixar oncológico e pedido para "furar a fila". Resposta fixa, antes da moderação. |

Casos na bateria de avaliação: `L01`–`L10`, `P02` e `C03`.

## Decisões tomadas por padrão (revisar)

O pedido foi executar as tasks sem as respostas das decisões D1–D12; os padrões abaixo são escolhas minhas, todas reversíveis.

| Decisão | Padrão adotado |
|---|---|
| D1 regras de negócio | **Preenchidas** a partir do documento do Desafio 1 e do `DECISOES.md` (`regras-negocio.md`). Falta a revisão por Pull Request (D11). |
| D2/D3 provedores e modelos | Provedor genérico no formato da OpenAI; lista e modelos por variável de ambiente. |
| D4 "dúvida de consulta" | Consulta agendada ou de reavaliação, o que esperar e como o resultado aparece na aplicação. |
| D5 privacidade | Só dados agregados; sem login. |
| D6 saúde | Só informação geral e educativa, com aviso fixo; sem diagnóstico, dose, prescrição nem urgência. |
| D7 categorização | 7 categorias de **assunto** (`CategoriaDado`); sem gravidade, urgência nem prioridade; emergência vira "atenção humana" por regra fixa, sem IA. |
| D8 relatórios | Visão geral e por especialidade, só agregados; texto na tela e download em Markdown; guardados no banco. |
| D9 onde roda | Dentro do backend Spring. |
| D10 dados enviados | Só o dataset sintético do desafio; nenhum dado de paciente vai à IA. |
| D11 revisores das regras | **Pendente** (o time define quem aprova o Pull Request). |
| D12 orçamento dos testes de ataque | **Pendente.** |

## Limitações conhecidas

- **Relatórios e categorizações somem a cada reinício do backend**, porque o `application.properties` do time recria o banco a cada subida (`ddl-auto=create`, de propósito para a demo). Com um banco persistente eles ficam.
- O **bloqueio por abuso, os limites e as sessões** ficam em memória: reiniciar o backend zera tudo, e rodar mais de uma instância não os compartilha.
- A moderação da resposta só roda **no fim** da geração (para permitir streaming): em caso de reprovação o texto já apareceu e é trocado via `substituir`.
- Os textos de emergência, do aviso de saúde e as categorias precisam ser **validados pela equipe**.
- Os padrões de "tentativa de burlar" em código cobrem os casos óbvios; os casos sutis dependem do filtro de escopo (modelo) e só se provam com a bateria contra a IA real (abaixo).

## Verificações e checklists

### Rodar

```bash
cd backend && ./mvnw test                         # tudo sem rede e sem gastar crédito
cd frontend && npm test                           # unitários do frontend
cd frontend && npm run test:e2e                   # precisa do backend no ar e do Chrome
bash scripts/verificar-segredos.sh                # chaves nos arquivos e em todo o histórico
```

**Bateria de avaliação contra a IA de verdade (T45)** — gasta crédito, só roda de propósito:

```bash
export AGENTE_AVALIACAO_REAL=sim IA_PROVEDOR_1_CHAVE=... IA_PROVEDOR_1_MODELO=...
export AGENTE_AVALIACAO_MAX_CHAMADAS=300          # teto de chamadas
cd backend && ./mvnw test -Dtest=AvaliacaoRealTest
```

São ~50 perguntas em `backend/src/test/resources/agente/avaliacao.json` (dentro do escopo, fora, infraestrutura, tentativas de burlar em 3 idiomas e em partes, dados de paciente, emergência, impróprio, clínico). O relatório sai em `backend/target/avaliacao-agente.md`; os casos "revisar à mão" precisam de uma pessoa lendo as respostas. A parte que não precisa de IA (todos os casos pegos por código) roda em todo `./mvnw test`.

### Para uma pessoa fazer (não dá para o código fazer)

**Limite de gasto e chaves nos provedores (T28)**
- [ ] Teto de gasto mensal e alerta de uso configurados no painel de cada provedor.
- [ ] Chaves **separadas** para desenvolvimento e para a demonstração, com o mínimo de permissões.
- [ ] Quem recebe o alerta de gasto está definido: ____________

**Retenção e treino dos dados nos provedores (T32)**
- [ ] Confirmado em cada provedor (painel e termos) que os dados enviados **não** são usados para treinar modelos: ____________
- [ ] Prazo de retenção anotado por provedor: ____________
- [ ] Combinado que só dados sintéticos/autorizados vão à IA (LGPD).
- [ ] Resultado registrado em `DECISOES.md`.

**Revisão de segurança antes de liberar (T46)** — por **outra pessoa** do time, antes do merge na `main` e da demo:
- [ ] Rodou `bash scripts/verificar-segredos.sh` (sem achados).
- [ ] Conferiu que nenhuma chave aparece no build do frontend (`.next`) nem nas respostas da API.
- [ ] Rodou a bateria contra a IA real (T45) e leu os casos "revisar à mão".
- [ ] Tentou à mão: pedir o prompt, mudar as regras pela conversa, pedir dados de paciente, pedir código/infra, mandar HTML na pergunta.
- [ ] Validou os textos de emergência, do aviso de saúde e as categorias.
- [ ] Preencheu `regras-negocio.md` por Pull Request revisado (D1/D11).
- Revisor: ____________ · Data: ____________
