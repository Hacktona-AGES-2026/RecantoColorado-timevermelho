# Histórico de alterações

Cada mudança de código entra aqui no mesmo commit. Datas do evento (24/10/2026), da mais recente para a mais antiga.

| Commit | Hora | O que mudou |
|---|---|---|
| `6517629` | 14:58 | Responsividade conferida em 320, 375, 768, 1024 e 1440 px (10 telas, nada passa da largura): abas da tela do médico não cobrem mais o texto no celular (duas por linha); diálogos limitados à altura da tela, com rolagem (o de prioridade cortava o título e o botão em celular pequeno) |
| (não commitado) | 24/10 | Textos alinhados: `docs/VALIDACAO-MENTOR.md` vira `docs/VALIDACAO-CLINICA.md` (validação clínica não realizada, perguntas em aberto); README, DECISOES e wiki deixam claro que a fila da tela passa no verificador sobre o dataset e que, com eventos novos, a regra vale pelos mesmos critérios; roteiro da demo com as posições atuais (241, 241 → 57, 241 → 80) |
| (não commitado) | 24/10 | Regra de ouro sem exceção na fila da tela: urgência que o verificador oficial não reconhece (ex.: "tronco 80%" só no texto) fica abaixo dos oncológicos até um dado estruturado confirmar; verificador oficial aprova a fila da tela na fila inicial e depois de 600 ações aleatórias; 681 testes |
| (não commitado) | 24/10 | Deploy: Dockerfiles do backend e do frontend, `docker-compose.prod.yml`, script da EC2 e passo a passo ([Deploy na AWS](Deploy-na-AWS)); DECISOES.md e README atualizados (médicos, desejo, BRAN, aptidão, coluna Saúde, assistente de IA, número de testes) |
| `8f23a93` | 15:10 | Wiki do projeto entra no repositório (20 páginas) e `scripts/publicar_wiki.sh` para a aba Wiki |
| `6517629` | 14:58 | Evento novo sem data fica com a data de hoje (antes herdava a data de referência do dataset, 23/10); data no futuro é recusada; simulação usa a mesma data; a referência 23/10 continua sendo a base dos dias de fila e da fila oficial; 661 testes |
| `6517629` | 14:58 | Auditoria de exames e eventos: quem registrou (médico com nome, CRM e id, ou regulador) entra no histórico do paciente e em Minhas alterações, com o impacto; o médico registra pela lista dele (só pacientes das listas); 659 testes |
| `94fd35d` | 13:45 | Ninguém é chamado de diabético sem registro no CSV: 13 pacientes com HbA1c alta e sem diabetes no cadastro deixam de aparecer como "diabetes descontrolado" na aptidão e nos alertas (passam a "HbA1c alta, sem diabetes registrado"); uma regra só de diabetes para fila, aptidão, BRAN e alertas; conferência de todas as comorbidades contra o CSV, sem divergência; 654 testes |
| `94fd35d` | 13:45 | Coluna Saúde na fila e na lista do médico: diabetes confirmado (comorbidades, flag ou CID secundário), última HbA1c (exame ou cadastro, vermelha acima de 8,5%) e comorbidades; data da HbA1c do cadastro passa a ser importada; 652 testes |
| `94fd35d` | 13:45 | Aptidão cirúrgica: parecer (apto, com ressalvas, otimizar antes) pela saúde do paciente, fatores informados pelo médico, recursos estimados (tempo, internação, UTI, consumo) e decisão humana, sem mudar a fila; 649 testes |
| `94fd35d` | 13:45 | BRAN: rascunho de benefícios, riscos, alternativas e nada por procedimento e paciente, registro da conversa com histórico, decisão do paciente em seguida e versão para o paciente; 641 testes |
| `27016ea` | 12:44 | Médico (e regulador) escolhe a posição do paciente na fila (só para cima; regra de ouro mantida; liberar volta à calculada); selo "escolhida"; 636 testes |
| `27016ea` | 12:44 | Desejo do paciente: registra se quer ser operado (quer, não quer, em dúvida), com quem e quando e histórico; "não quer" e "em dúvida" viram pendência do regulador e selo na fila, sem mudar a ordem; 628 testes |
| `e662be3` | 12:23 | Médico muda a prioridade dos pacientes das listas dele (botão Prioridade), com justificativa, marcação "informação fora do sistema" e auditoria por médico (aba Minhas alterações); mesmas regras do regulador; 620 testes |
| `55768d0` | 12:00 | Equipe (PR #1): logo no cabeçalho, assistente no topo e seções recolhíveis no paciente |
| `ef051b8` | 11:11 | Equipe (Nícolas): agente de IA busca paciente pelo ID e entende solicitações duplicadas |
| `6c3b7da` | 10:34 | Equipe (Leo): corrige o agente de IA que não conseguia buscar um paciente específico |
| `38d218c` | 10:17 | Abas do Simulador quebram linha no celular (sem rolagem lateral); README ganha a seção "Depois de atualizar (git pull)" com os erros comuns e como resolver. Agente de IA testado de verdade com a chave em backend/.env |
| `f272336` | 09:28 | Equipe (Leo): reaplica o merge do agente de IA e junta feat/agente-ia-integrado (mesmo conteúdo de 1ab958e) |
| `d6ecc9e` | 09:11 | Equipe (Leo): reverte temporariamente o merge do agente de IA |
| `1ab958e` | 08:47 | Simulador explica quando nada muda (evento neutro ou de melhora, mesmo valor de hoje, paciente já no máximo), avisa que uma queda de classe seria só sugerida e inclui o evento montado sem "Adicionar"; 86 testes |
| `e966d81` | 08:39 | Equipe (Leo e Dimitri): agente de IA (chat, relatórios, categorização), testes de frontend com vitest, H2 em memória como banco padrão para rodar sem Docker |
| `744e1fe` | 08:06 | Rebaixamentos: classe menos urgente vira sugestão que o regulador confirma ou rejeita em Alertas; rede de segurança no recálculo; oncológico nunca rebaixado (inclui A1→A2); "Confirmar classe" mantém a vigente; alerta "Divergência de prioridade — revisão necessária" passa a cobrir "alta" em C/D; 81 testes |
| `94fd35d` | 13:45 | Cria esta wiki: páginas na pasta `wiki/` do repositório e script `scripts/publicar_wiki.sh` para publicar no GitHub |
| `57a5e9f` | 06:32 | Médicos por especialidade: cadastro, lista de pacientes cirúrgicos, "Possível avaliação da especialidade" e fila completa. Corrige a fila de referência que podia ser fixada vazia (e gravar `fila_priorizada.csv` só com cabeçalho) quando a API recebia pedido durante a subida |
| `63c2849` | 05:49 | Promoção manual só ultrapassa câncer com evidência estruturada (texto não conta) |
| `627e561` | 05:32 | Identifica a fila operacional na tela e separa os botões de exportação oficial e operacional |
| `91ab702` | 05:00 | Estabiliza o motor: duas filas, regra de ouro como restrição e texto livre só como alerta |
| `462f488` | 03:23 | Regra de ouro usa só a evidência do dataset oficial |
| `f824820` | 02:20 | Teste de integração, conferidor do CSV, roteiro da demo e material para o mentor |
| `787f1e9` | 02:20 | Saídas sugeridas ficam na fila; corrige falhas do leitor clínico ("asséptica", tronco coronário) |
| `6f44d61` | 01:46 | Saídas sugeridas no CSV e detalhes da entrega |
| `aa18c4b` | 00:51 | Ordem final dentro da classe (score) no PLANO |
| `49ef453` | 00:51 | Telas do Simulador, Alertas e Antes × depois |
| `0942f43` | 00:48 | Documenta decisões, entregáveis e como rodar a demo |
| `9d676d4` | 00:48 | Telas da Fila e do Paciente com o painel de decisão do regulador |
| `6f581b5` | 00:46 | Alertas, indicadores antes × depois, demo e `fila_priorizada.csv` |
| `f499834` | 00:46 | Serviço da fila em memória, decisões do regulador e simulações |
| `cdf7cac` | 00:46 | Entidades JPA e importação do dataset com limpeza |
| `8ab58c0` | 00:32 | Motor de priorização SWALIS explicável |
| `f083557` | 00:16 | Monta o monorepo do FilaVIVA |
